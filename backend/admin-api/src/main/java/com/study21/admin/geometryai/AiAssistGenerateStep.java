package com.study21.admin.geometryai;

import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.geometryai.dto.AiResponseFormatPrompt;
import com.study21.admin.geometryai.dto.BatC52ResultDto;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * batC52「AI画図助手 生成」の実体（**AI を呼ぶ**・外部・従量課金）。
 *
 * <p>利用者の指示で、画図助手は user-api の同期呼び出しから**バッチ batC52**に変わった。
 * 「依頼 → その場で即時実行（待たない）」が原則で、user-api が作った `生成状態=PENDING` の行を
 * 拾って AI を呼び、READY / FAILED に遷移させる。他のバッチと競合するロックや同時実行制限は
 * **持たない**（将来 `GEOMETRY_AI_MAX_CONCURRENCY` のような枠を入れても、助手はその枠の外に置く）。</p>
 *
 * <ol>
 *   <li>対象を決める（`要求内容` の assistId、無ければ PENDING/GENERATING の最古の 1 件）</li>
 *   <li>設定から LLM スロット（`GEOMETRY_AI_ASSIST_PROVIDER`）を解決し、`AI_MODEL` ページの
 *       同スロットのモデル・URL・API Key を読む</li>
 *   <li>指示前 XML からオブジェクト名を抜いてプロンプトを組む</li>
 *   <li>呼び出しの前に `GENERATING` を確定してから AI を呼ぶ</li>
 *   <li>呼び出しごとに `BAT_AI呼出履歴情報` に 1 行残す（バッチコード='batC52'）</li>
 *   <li>コマンドを検証して READY（生成コマンド・説明・呼出履歴ID）または FAILED（理由・再試行回数 +1）</li>
 * </ol>
 */
@Component
public class AiAssistGenerateStep {

    private static final Logger log = LoggerFactory.getLogger(AiAssistGenerateStep.class);

    /** batC52 の必須設定（この工程だけの宣言。バッチ一覧の missingSettings に出る）。 */
    public static final List<SettingRequirement> REQUIRED_SETTINGS = List.of(
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_ENABLED"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_ASSIST_PROVIDER"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_ASSIST_SYSTEM_PROMPT"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_ASSIST_USER_PROMPT"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_ASSIST_TIMEOUT_SECONDS"),
            new SettingRequirement("GEOMETRY_AI", "GEOMETRY_AI_ASSIST_MAX_COMMANDS"));

    /** 呼出履歴のレスポンスに残す上限。 */
    private static final int AI_BODY_LIMIT = 200_000;
    /** 呼出履歴のプロンプトに残す上限。 */
    private static final int PROMPT_LIMIT = 20_000;
    /** 助手の Temperature（設定に専用キーが無いので低めで固定）。 */
    private static final double TEMPERATURE = 0.2;
    /** 助手の出力上限。 */
    private static final int MAX_TOKENS = 2000;

    private final GeometryAiAssistMapper assistMapper;
    private final GeometryAiAssistRecorder recorder;
    private final GeometryAiConnectionResolver connectionResolver;
    private final GeometryAiClient aiClient;
    private final SettingsService settingsService;
    /** 出力形式（DTO から生成した JSON Schema）をシステムプロンプトへ足す。 */
    private final AiResponseFormatPrompt responseFormatPrompt;

    public AiAssistGenerateStep(GeometryAiAssistMapper assistMapper,
                                GeometryAiAssistRecorder recorder,
                                GeometryAiConnectionResolver connectionResolver,
                                GeometryAiClient aiClient,
                                SettingsService settingsService,
                                AiResponseFormatPrompt responseFormatPrompt) {
        this.assistMapper = assistMapper;
        this.recorder = recorder;
        this.connectionResolver = connectionResolver;
        this.aiClient = aiClient;
        this.settingsService = settingsService;
        this.responseFormatPrompt = responseFormatPrompt;
    }

    /** 1 件の依頼を生成する。 */
    public Map<String, Object> run(BatchExecutionEntity execution) {
        Long requestedId = AiAssistPayload.assistId(execution);
        GeometryAiAssistEntity entity = requestedId == null
                ? assistMapper.findPendingTarget()
                : assistMapper.findById(requestedId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity == null) {
            result.put("skipped", true);
            result.put("message", "対象がありませんでした（AI 画図助手の依頼はありません）。");
            return result;
        }
        result.put("assistId", entity.getAssistId());

        if (!isTarget(entity)) {
            result.put("skipped", true);
            result.put("message", "既に生成済みのためスキップしました。（依頼ID " + entity.getAssistId() + "）");
            return result;
        }

        Map<String, String> values = settingsService.requireSettings("batC52", REQUIRED_SETTINGS);
        int maxCommands = intValue(values.get("GEOMETRY_AI_ASSIST_MAX_COMMANDS"), 20);
        int timeoutSeconds = intValue(values.get("GEOMETRY_AI_ASSIST_TIMEOUT_SECONDS"), 60);
        GeometryAiConnectionResolver.AiConnection connection =
                connectionResolver.resolve("batC52", values.get("GEOMETRY_AI_ASSIST_PROVIDER"));

        // 出力形式（JSON Schema）は DTO から生成してシステムプロンプトへ足す（プロンプトに手書きしない）
        // コマンドの書き方（引数の数・条件の満たし方）はコード側の早見表を足す（両方の AI 経路で同じ）
        String systemPrompt = responseFormatPrompt.appendTo(
                GeometryCommandSignatureCard.appendTo(values.get("GEOMETRY_AI_ASSIST_SYSTEM_PROMPT")), "batC52");
        String userPrompt = GeometryAiAssistPromptBuilder.buildUserPrompt(
                values.get("GEOMETRY_AI_ASSIST_USER_PROMPT"), entity.getInstruction(), entity.getBeforeXml(),
                entity.getObjectsSummary(), entity.getFailureDetail(), maxCommands);

        recorder.markGenerating(entity, execution.getExecutionId());

        String promptSummary = userPrompt.length() > PROMPT_LIMIT
                ? userPrompt.substring(0, PROMPT_LIMIT) : userPrompt;
        Timestamp startedAt = Timestamp.valueOf(LocalDateTime.now());
        long startedMs = System.currentTimeMillis();
        GeometryAiClient.AiResponse response = aiClient.call(new GeometryAiClient.AiRequest(
                connection.provider(), connection.model(), connection.url(), connection.apiKey(),
                systemPrompt, userPrompt, null, null, timeoutSeconds, TEMPERATURE, MAX_TOKENS, false));
        int durationMs = (int) (System.currentTimeMillis() - startedMs);

        GeometryAiAssistResponseParser.Parsed parsed = response.isSuccess()
                ? GeometryAiAssistResponseParser.parse(response.body())
                : GeometryAiAssistResponseParser.Parsed.failure(response.errorCode(), response.errorMessage());
        BatC52ResultDto dto = parsed.result();

        List<String> commands = List.of();
        String errorCode = parsed.errorCode();
        String errorMessage = parsed.errorMessage();
        if (errorCode == null) {
            try {
                commands = GeometryAiAssistCommandPolicy.requireAllowed(
                        GeometryAiAssistResponseParser.commandsOf(dto), maxCommands);
            } catch (ValidationException cause) {
                errorCode = "COMMAND_NOT_ALLOWED";
                errorMessage = cause.getMessage();
            }
        }

        Long callId = recordCall(entity, connection, promptSummary,
                response.isSuccess() ? response.body() : null,
                errorCode == null ? "SUCCESS" : "FAILURE",
                response.httpStatus(), errorCode, errorMessage, startedAt, durationMs);

        if (errorCode != null) {
            fail(entity, errorCode, errorMessage);
        }

        entity.setCommands(String.join("\n", commands));
        entity.setCommandCount(commands.size());
        entity.setDescription(dto == null ? null : dto.getDescription());
        entity.setAiCallId(callId);
        recorder.updateReady(entity);

        result.put("success", true);
        result.put("commandCount", commands.size());
        result.put("callId", callId);
        result.put("message", "AI が変更案を " + commands.size() + " 件生成しました。（依頼ID "
                + entity.getAssistId() + "）");
        log.info("batC52 finished. assistId={} commands={} callId={}",
                entity.getAssistId(), commands.size(), callId);
        return result;
    }

    private static boolean isTarget(GeometryAiAssistEntity entity) {
        return "PENDING".equals(entity.getStatus()) || "GENERATING".equals(entity.getStatus());
    }

    private Long recordCall(GeometryAiAssistEntity entity,
                            GeometryAiConnectionResolver.AiConnection connection,
                            String prompt, String responseBody, String result,
                            int httpStatus, String errorCode, String errorMessage,
                            Timestamp startedAt, int durationMs) {
        AiCallLogEntity call = new AiCallLogEntity();
        call.setBatchCode("batC52");
        call.setProcessKey("assist-" + entity.getAssistId());
        call.setLanguage("ja");
        call.setAiType(connection.provider());
        call.setModelName(limit(connection.model(), 100));
        call.setCallUrl(connection.url());
        call.setHttpStatus(httpStatus > 0 ? httpStatus : null);
        call.setStartTime(startedAt);
        call.setEndTime(Timestamp.valueOf(LocalDateTime.now()));
        call.setDurationMs(durationMs);
        call.setResult(result);
        call.setErrorCode(limit(errorCode, 100));
        call.setErrorMessage(errorMessage);
        call.setPrompt(prompt);
        call.setResponse(limit(responseBody, AI_BODY_LIMIT));
        call.setCreatedBy(entity.getCreatedBy());
        call.setSourceCode("BATCH");
        return recorder.recordCall(call);
    }

    private void fail(GeometryAiAssistEntity entity, String errorCode, String message) {
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(message);
        recorder.updateFailed(entity);
        log.warn("batC52 failed. assistId={} code={} message={}", entity.getAssistId(), errorCode, message);
        throw new AiAssistStepException(message == null
                ? "AI の変更案を作れませんでした。指示を変えてもう一度お試しください。" : message);
    }

    private static int intValue(String value, int fallback) {
        try {
            return value == null ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException cause) {
            return fallback;
        }
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 工程の失敗（`BatchServiceImpl` が FAILED として履歴に残す）。 */
    public static class AiAssistStepException extends RuntimeException {
        public AiAssistStepException(String message) {
            super(message);
        }
    }
}

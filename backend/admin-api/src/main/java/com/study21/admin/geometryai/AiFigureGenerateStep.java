package com.study21.admin.geometryai;

import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.geometryai.dto.AiResponseFormatPrompt;
import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.admin.geometryai.dto.FigureOutcome;
import com.study21.admin.geometryai.dto.FigureOutputDto;
import com.study21.admin.geometryai.dto.FigureOutputType;
import com.study21.admin.geometryai.dto.FigureParts;
import com.study21.admin.geometryai.processor.FigureProcessor;
import com.study21.admin.geometryai.processor.FigureProcessorRegistry;
import com.study21.admin.setting.SettingsValidationException;
import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiFigureSupplements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * AI 生図の**AI 生成工程**（モード A〜D で共通の実装）。
 *
 * <p>バッチは**モードごとに 1 つ**（{@code batC51-A}〜{@code batC51-D}）。どのモードでも
 * このクラスが実体で、**プロセッサ（プロンプト・出力 DTO）と設定だけがモードで変わる**
 * （画像の取込・モデル呼び出し・検証・記録は共通の部品）。</p>
 *
 * <ol>
 *   <li>対象を決める（`要求内容` の aiRequestId。無ければ**そのモードの**生成待ちの最古の 1 件）</li>
 *   <li>モードの設定を解決する（モード別 → 無ければ共通。モデルパラメータも同じ規則）</li>
 *   <li>プロンプトを作る（モードの役割・結果種別・補充 → 出力形式は DTO から生成）</li>
 *   <li>**呼び出しの前に `GENERATING` を確定**してから AI を呼ぶ</li>
 *   <li>呼び出しごとに `BAT_AI呼出履歴情報` に 1 行残す（成功・失敗のどちらも）</li>
 *   <li>AI の判定・コマンド・作図の構造・設定スナップショットを保存して `GENERATED` にする</li>
 * </ol>
 *
 * <p><strong>冪等ではない</strong>（毎回トークンを消費する）ので、既に `GENERATED` 以降なら何もせず
 * 成功で終わる。再試行は利用者の【もう一度生成】だけ。</p>
 *
 * <p>再試行: タイムアウト / 429 / 5xx は `GEOMETRY_AI_RETRY_LIMIT` 回まで指数バックオフ（2s, 4s）。
 * 4xx（キー・モデル不正）は再試行しない。**JSON 破損・空応答は 1 回だけ再質問する**が、
 * NEEDS_INPUT / UNSUPPORTED（コマンドが空で正常）は再質問しない。</p>
 *
 * <p>{@code 作図モード} が NULL の時代の要求は**モード A として扱う**（`FigureMode.of(null)` → 空 →
 * `orElse(A)`）。結果種別も当時の分類から読み替える。</p>
 */
@Component
public class AiFigureGenerateStep {

    private static final Logger log = LoggerFactory.getLogger(AiFigureGenerateStep.class);

    /** 必須設定はモード共通の分を宣言する（モード別の項目は「未設定なら共通を継承」する）。 */
    public static final List<com.study21.admin.setting.SettingRequirement> REQUIRED_SETTINGS =
            FigureProcessorSettings.requiredSettings();

    /** 呼出履歴のレスポンスに残す上限（1 行が 200KB 超になるため）。 */
    private static final int AI_BODY_LIMIT = 200_000;
    /** 再質問のときに足す前置き（設計 §6.3）。 */
    private static final String REPAIR_PREFIX =
            "前回の出力は検証に失敗しました: {reason}。修正した {format} だけを返してください。\n";

    private final GeometryAiRequestMapper requestMapper;
    private final GeometryAiRequestRecorder recorder;
    private final GeometryAiImageStorage storage;
    private final GeometryAiConnectionResolver connectionResolver;
    private final FigureProcessorRegistry processorRegistry;
    private final FigureProcessorSettings processorSettings;
    private final FigurePromptBuilder promptBuilder;
    private final GeometryAiClient aiClient;
    /** 出力形式（DTO から生成した JSON Schema）をシステムプロンプトへ足す。 */
    private final AiResponseFormatPrompt responseFormatPrompt;

    public AiFigureGenerateStep(GeometryAiRequestMapper requestMapper,
                                GeometryAiRequestRecorder recorder,
                                GeometryAiImageStorage storage,
                                GeometryAiConnectionResolver connectionResolver,
                                FigureProcessorRegistry processorRegistry,
                                FigureProcessorSettings processorSettings,
                                FigurePromptBuilder promptBuilder,
                                GeometryAiClient aiClient,
                                AiResponseFormatPrompt responseFormatPrompt) {
        this.requestMapper = requestMapper;
        this.recorder = recorder;
        this.storage = storage;
        this.connectionResolver = connectionResolver;
        this.processorRegistry = processorRegistry;
        this.processorSettings = processorSettings;
        this.promptBuilder = promptBuilder;
        this.aiClient = aiClient;
        this.responseFormatPrompt = responseFormatPrompt;
    }

    /**
     * 1 件を生成する。
     *
     * @param pinnedMode モード別バッチ（batC51-A〜D）から呼ぶときのモード。**必ず指定する**
     *                   （モードが無い時代の裸の batC51 の入口は 2026-09-19 に削除した）
     */
    public Map<String, Object> run(BatchExecutionEntity execution, FigureMode pinnedMode) {
        Long requestedId = AiFigurePayload.requestId(execution);
        GeometryAiRequestEntity entity = requestedId == null
                ? requestMapper.findGenerateTarget(pinnedMode.name())
                : requestMapper.findById(requestedId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity == null) {
            result.put("skipped", true);
            result.put("message", "対象がありませんでした（AI 生成待ちの AI 生図はありません）。");
            return result;
        }
        result.put("aiRequestId", entity.getRequestId());
        result.put("requestNo", entity.getRequestNo());

        FigureMode mode = FigureMode.of(entity.getMode()).orElse(FigureMode.A);
        result.put("mode", mode.name());
        if (pinnedMode != mode) {
            // モード別バッチが別モードの要求を拾った（要求行が正。取り違えたら実行しない）
            result.put("skipped", true);
            result.put("message", "この要求の作図モードは " + mode.name() + " です（"
                    + pinnedMode.taskCode() + " では処理しません）。");
            return result;
        }

        if (!isGenerateTarget(entity)) {
            result.put("skipped", true);
            result.put("message", "既に生成済みのためスキップしました。（" + entity.getRequestNo() + "）");
            return result;
        }

        FigureProcessor processor = processorRegistry.of(mode);
        FigureOutputType requested = requestedOutputTypeOf(entity, processor);

        // 設定の解決・プロンプトの組み立て・接続の解決。**どれも失敗しうる**ので、
        // 失敗したら要求行へ理由を書いてから止める（処理中のまま残して拾い直され続けないように）
        AiFigureConfig config;
        String systemPrompt;
        String userPrompt;
        GeometryAiConnectionResolver.AiConnection connection;
        try {
            config = processorSettings.resolve(processor, entity.getSettingsSnapshotJson());
            // 出力形式（JSON Schema）は DTO から生成する。テンプレートが {outputSchema} を使っていれば
            // その変数へ入れ、使っていなければ system プロンプトの末尾へ足す（二重にしない）
            String schemaSection = responseFormatPrompt.schemaSectionOf(processor.taskCode());
            String built = promptBuilder.buildSystemPrompt(processor, config, requested, schemaSection);
            if (!usesOutputSchema(config)) {
                built = responseFormatPrompt.appendTo(built, processor.taskCode());
            }
            systemPrompt = GeometryCommandSignatureCard.appendTo(built);
            userPrompt = promptBuilder.buildUserPrompt(processor, config,
                    new FigurePromptBuilder.PromptInput(mode, requested, entity.getNote(),
                            FigureSupplements.rows(entity.getSupplementsJson()), keepLabelsOf(entity),
                            entity.getUserKind(), entity.getUserSubKind(), entity.getFigureType(),
                            config.maxCommands(), config.allowedCommands(), config.outputFormat()));
            connection = connectionResolver.resolve(processor.taskCode(), config.provider());
        } catch (SettingsValidationException cause) {
            fail(entity, "CONFIG", "AI の設定が不足しています。" + cause.getMessage());
            return result;
        } catch (ValidationException cause) {
            fail(entity, "PROMPT", cause.getMessage());
            return result;
        } catch (Exception cause) {
            fail(entity, "CONFIG", "AI の設定・プロンプトを準備できませんでした（"
                    + cause.getClass().getSimpleName() + "）: " + messageOf(cause));
            return result;
        }

        byte[] image = storage.read(entity.getCroppedPath(), entity.getCroppedName());
        // MIME は**保存した切り抜き画像**の拡張子から決める（元画像の MIME と一致しないことがある）
        String imageMime = entity.getCroppedName() != null
                && entity.getCroppedName().toLowerCase(Locale.ROOT).endsWith(".jpg")
                ? "image/jpeg" : "image/png";
        if (image == null) {
            // 前処理が先に必要（前処理はバッチではなく通常コードで行う）。置き場のずれも分かるように絶対パスを出す
            fail(entity, "NO_IMAGE", "AI へ送る切り抜き画像がありません。"
                    + storage.missingImageMessage(entity.getCroppedPath(), entity.getCroppedName())
                    + " 前処理からやり直してください。");
        }

        // 呼び出しの前に「生成中」を確定する（この後 JVM が落ちても分かるように）。
        entity.setAiExecutionId(execution.getExecutionId());
        recorder.markGenerating(entity);

        // プロンプトは画像を要約に置き換えて保存する（base64 を DB に入れない）
        String promptSummary = userPrompt + "\n" + GeometryAiPromptBuilder.imageSummary(entity.getCroppedName(),
                entity.getCroppedWidth() == null ? 0 : entity.getCroppedWidth(),
                entity.getCroppedHeight() == null ? 0 : entity.getCroppedHeight());

        int attempts = Math.max(0, config.retryLimit()) + 1;
        String outputFormat = config.outputFormat();
        String repairReason = null;
        FigureResponseParser.ParseResult parsed = null;
        Long callId = null;
        for (int attempt = 0; attempt < attempts; attempt += 1) {
            String prompt = userPrompt;
            if (repairReason != null) {
                prompt = REPAIR_PREFIX.replace("{reason}", repairReason)
                        .replace("{format}", outputFormat == null ? "JSON" : outputFormat) + userPrompt;
            }
            Timestamp startedAt = Timestamp.valueOf(LocalDateTime.now());
            long startedMs = System.currentTimeMillis();
            GeometryAiClient.AiResponse response = aiClient.call(new GeometryAiClient.AiRequest(
                    connection.provider(), connection.model(), connection.url(), connection.apiKey(),
                    systemPrompt, prompt, image, imageMime, config.requestTimeoutSeconds(),
                    config.temperature(), config.maxCompletionTokens(),
                    outputFormat == null || "JSON".equalsIgnoreCase(outputFormat)));
            int durationMs = (int) (System.currentTimeMillis() - startedMs);

            parsed = response.isSuccess()
                    ? FigureResponseParser.parse(response.body(), processor.dtoClass(), outputFormat)
                    : FigureResponseParser.ParseResult.failure(response.errorCode(), response.errorMessage());

            callId = recordCall(entity, processor, connection, promptSummary,
                    response.isSuccess() ? response.body() : null,
                    parsed.isSuccess() ? "SUCCESS" : "FAILURE",
                    response.httpStatus(),
                    parsed.isSuccess() ? null : parsed.errorCode(),
                    parsed.isSuccess() ? null : parsed.errorMessage(),
                    startedAt, durationMs);

            if (parsed.isSuccess()) {
                break;
            }
            String code = parsed.errorCode() == null ? "EMPTY_RESPONSE" : parsed.errorCode();
            if (!response.isSuccess() && response.isFatal()) {
                // 4xx（キー・モデル不正）は再試行しない（設計 §6.4）
                fail(entity, code, parsed.errorMessage());
            }
            // 通信の失敗（タイムアウト / 429 / 5xx）は再試行する。
            // JSON 破損・空応答は「再質問」で直せる可能性がある。
            // 方針違反（禁止コマンド・多すぎる）は再質問しない（無駄な課金を避ける）。
            // なお NEEDS_INPUT / UNSUPPORTED（コマンドが空で正常）はここへ来ない＝再質問しない。
            boolean transportFailure = !response.isSuccess();
            boolean repairable = "INVALID_JSON".equals(code) || "EMPTY_RESPONSE".equals(code);
            boolean retryable = transportFailure ? !response.isFatal() : repairable;
            if (retryable && attempt + 1 < attempts) {
                if (!transportFailure) {
                    repairReason = parsed.errorMessage();
                }
                sleepBackoff(attempt);
                continue;
            }
            fail(entity, code, parsed.errorMessage());
        }
        if (parsed == null || !parsed.isSuccess()) {
            fail(entity, "EMPTY_RESPONSE", "AI から作図の内容が返りませんでした。");
        }

        // 出力データ構造はモードの DTO（唯一の定義）。ここでは DTO の値をそのまま使う
        FigureOutputDto output = parsed.output();
        FigureOutcome outcome = output.getOutcome() == null ? FigureOutcome.GENERATABLE : output.getOutcome();
        FigureOutputType claimed = output.getResolvedOutputType() != null && output.getResolvedOutputType().isResolved()
                ? output.getResolvedOutputType() : null;
        FigureOutputType resolvedType = processor.mode().fixedOutputType().orElse(claimed);
        List<String> commands = FigureResponseParser.commandsOf(output);

        entity.setMode(mode.name());
        entity.setRequestedOutputType(requested.name());
        entity.setResolvedOutputType(resolvedType == null ? null : resolvedType.name());
        entity.setOutcome(outcome.name());
        entity.setAiKind(aiKindOf(resolvedType));
        entity.setFigureType(figureTypeOf(resolvedType, entity));
        entity.setPrompt(promptSummary);
        entity.setCommands(String.join("\n", commands));
        entity.setCommandCount(commands.size());
        entity.setProposalJson(AiResponseDtoParser.toJson(output));
        entity.setQuestionsJson(AiResponseDtoParser.toJson(questionsOf(output)));
        entity.setSettingsSnapshotJson(processorSettings.snapshot(processor, config,
                entity.getSettingsSnapshotJson(), requested, resolvedType, connection.model()));
        entity.setAiCallId(callId);
        entity.setValidationError(null);
        recorder.updateGenerated(entity);

        result.put("commandCount", commands.size());
        result.put("aiKind", entity.getAiKind());
        result.put("outcome", outcome.name());
        result.put("requestedOutputType", requested.name());
        result.put("resolvedOutputType", entity.getResolvedOutputType());
        result.put("callId", callId);
        result.put("message", messageOf(outcome, commands.size(), entity.getRequestNo()));
        log.info("{} generated. requestId={} requestNo={} mode={} outcome={} commands={} callId={}",
                processor.taskCode(), entity.getRequestId(), entity.getRequestNo(), mode.name(), outcome.name(),
                commands.size(), callId);
        return result;
    }

    /** 結果の要約（画面・履歴にそのまま出す）。 */
    private static String messageOf(FigureOutcome outcome, int commandCount, String requestNo) {
        return switch (outcome) {
            case GENERATABLE -> "AI がコマンドを " + commandCount + " 件生成しました。（" + requestNo + "）";
            case NEEDS_INPUT -> "AI が情報不足のため確認を求めています。（" + requestNo + "）";
            case UNSUPPORTED -> "AI はこの内容には対応できないと判断しました。（" + requestNo + "）";
        };
    }

    /** 質問（NEEDS_INPUT のとき画面が復元する）。 */
    private static List<FigureParts.Question> questionsOf(FigureOutputDto output) {
        return output.getQuestions() == null ? List.of() : output.getQuestions();
    }

    /**
     * 利用者が指定した結果種別。
     *
     * <p>要求行に無い（歴史的な要求）ときは、当時の分類（利用者区分）から読み替える
     * （FIGURE → GEOMETRY / FUNCTION → GRAPH / MIXED → MIXED）。</p>
     */
    static FigureOutputType requestedOutputTypeOf(GeometryAiRequestEntity entity, FigureProcessor processor) {
        FigureOutputType fromMode = processor.mode().fixedOutputType().orElse(null);
        if (fromMode != null) {
            return fromMode;
        }
        FigureOutputType stored = FigureOutputType.of(entity.getRequestedOutputType()).orElse(null);
        if (stored != null) {
            return stored;
        }
        if (entity.getUserKind() == null) {
            return FigureOutputType.defaultType();
        }
        return switch (entity.getUserKind().toUpperCase(Locale.ROOT)) {
            case "FIGURE" -> FigureOutputType.GEOMETRY;
            case "FUNCTION" -> FigureOutputType.GRAPH;
            case "MIXED" -> FigureOutputType.MIXED;
            default -> FigureOutputType.defaultType();
        };
    }

    /** 元の名前・ラベルを残すか（補充に「元の名前とラベル」があるときだけ false になり得る）。 */
    private static Boolean keepLabelsOf(GeometryAiRequestEntity entity) {
        for (String row : FigureSupplements.rows(entity.getSupplementsJson())) {
            if (row.startsWith(AiFigureSupplements.LABEL_KEEP_LABELS + ":")) {
                return !row.contains(AiFigureSupplements.LABELS_NO);
            }
        }
        return null;
    }

    /**
     * 設定の System Prompt が {@code {outputSchema}} を使っているか。
     *
     * <p>使っているときは本文をその変数へ入れ、末尾への自動追加はしない（同じ Schema を 2 回
     * 送らない）。</p>
     */
    private static boolean usesOutputSchema(AiFigureConfig config) {
        return FigurePromptTemplate.placeholders(config.systemPromptCommon()).contains("outputSchema")
                || FigurePromptTemplate.placeholders(config.systemPromptMode()).contains("outputSchema");
    }

    /** 例外の理由を読める 1 文にする（画面と要求行にそのまま出す）。 */
    private static String messageOf(Throwable cause) {
        if (cause == null) {
            return "原因が分かりません。";
        }
        String message = cause.getMessage();
        if (message != null && !message.isBlank()) {
            return message.strip();
        }
        Throwable root = cause.getCause();
        return root != null && root.getMessage() != null && !root.getMessage().isBlank()
                ? root.getMessage().strip() : cause.getClass().getSimpleName();
    }

    /** 作図種別（DB の CHECK に合わせる。決まらないときは今の値を保つ）。 */
    private static String figureTypeOf(FigureOutputType resolvedType, GeometryAiRequestEntity entity) {
        if (resolvedType == null) {
            return entity.getFigureType() == null ? "geometry" : entity.getFigureType();
        }
        return resolvedType.figureType();
    }

    /** 結果種別から、歴史的な AI区分 列（FIGURE / FUNCTION / MIXED）を埋める。 */
    private static String aiKindOf(FigureOutputType resolvedType) {
        if (resolvedType == null) {
            return null;
        }
        return switch (resolvedType) {
            case GEOMETRY -> "FIGURE";
            case GRAPH -> "FUNCTION";
            case MIXED -> "MIXED";
            case AUTO -> null;
        };
    }

    /** 生成の対象か（PREPROCESSED / GENERATING / FAILED(GENERATE)）。 */
    private static boolean isGenerateTarget(GeometryAiRequestEntity entity) {
        String status = entity.getStatusCode();
        return "PREPROCESSED".equals(status) || "GENERATING".equals(status)
                || ("FAILED".equals(status) && "GENERATE".equals(entity.getFailedStage()));
    }

    /** 呼出履歴に 1 行残す（**画像の base64 は入れない**）。 */
    private Long recordCall(GeometryAiRequestEntity entity,
                            FigureProcessor processor,
                            GeometryAiConnectionResolver.AiConnection connection,
                            String prompt, String responseBody, String result,
                            int httpStatus, String errorCode, String errorMessage,
                            Timestamp startedAt, int durationMs) {
        AiCallLogEntity call = new AiCallLogEntity();
        call.setBatchCode(processor.taskCode());
        call.setExecutionId(entity.getAiExecutionId());
        call.setProcessKey(entity.getRequestNo());
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
        // 誰の要求かが追えるように、要求行の登録者を残す（設計 §3.4）
        call.setCreatedBy(entity.getCreatedBy());
        call.setSourceCode("BATCH");
        return recorder.recordCall(call);
    }

    private void fail(GeometryAiRequestEntity entity, String errorCode, String message) {
        entity.setFailedStage("GENERATE");
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(message);
        recorder.updateFailed(entity);
        log.warn("AI figure generate failed. requestId={} code={} message={}",
                entity.getRequestId(), errorCode, message);
        throw new AiFigurePreprocessStep.GeometryAiStepException(message == null
                ? "AI の生成に失敗しました。" : message);
    }

    /** 指数バックオフ（2s, 4s, 8s…最大 8s）。 */
    private static void sleepBackoff(int attempt) {
        long waitMs = Math.min(8000L, 2000L * (1L << Math.min(attempt, 2)));
        try {
            Thread.sleep(waitMs);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
        }
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

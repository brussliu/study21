package com.study21.admin.classroomai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * batC61「授業ノート フェーズ分析」と batC62「授業ノート 最終まとめ」の共通実体（**AI を呼ぶ**・外部・従量課金）。
 *
 * <ol>
 *   <li>対象を決める（`要求内容` の noteId、無ければ PENDING/GENERATING の最古の 1 件）</li>
 *   <li>設定から LLM スロット（`CLASSROOM_AI_NOTE_PROVIDER`）を解決し、`AI_MODEL` ページの
 *       同スロットのモデル・URL・API Key を読む</li>
 *   <li>対象範囲の転写セグメントを連結してプロンプトを作る</li>
 *   <li>呼び出しの前に `GENERATING` を確定してから AI を呼ぶ</li>
 *   <li>呼び出しごとに `BAT_AI呼出履歴情報` に 1 行残す（sourceCode=BATCH）</li>
 *   <li>ノート JSON を書いて READY にする（FINAL は記録の最終まとめ + COMPLETED も書く）</li>
 * </ol>
 */
@Component
public class ClassroomAiNoteStep {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAiNoteStep.class);

    /** batC61（フェーズ分析）の必須設定。 */
    public static final List<SettingRequirement> PHASE_REQUIRED_SETTINGS = List.of(
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_ENABLED"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_NOTE_PROVIDER"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_NOTE_SYSTEM_PROMPT"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_NOTE_USER_PROMPT"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_NOTE_TIMEOUT_SECONDS"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_NOTE_MAX_COMPLETION_TOKENS"));

    /** batC62（最終まとめ）の必須設定。 */
    public static final List<SettingRequirement> SUMMARY_REQUIRED_SETTINGS = List.of(
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_ENABLED"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_NOTE_PROVIDER"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_SUMMARY_SYSTEM_PROMPT"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_SUMMARY_USER_PROMPT"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_NOTE_TIMEOUT_SECONDS"),
            new SettingRequirement("CLASSROOM_AI", "CLASSROOM_AI_NOTE_MAX_COMPLETION_TOKENS"));

    /** 呼出履歴のレスポンスに残す上限。 */
    private static final int AI_BODY_LIMIT = 200_000;
    /** ノートの Temperature（設定に専用キーが無いので低めで固定し、出力を安定させる）。 */
    private static final double TEMPERATURE = 0.2;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ClassroomNoteMapper noteMapper;
    private final ClassroomSegmentMapper segmentMapper;
    private final ClassroomRecordMapper recordMapper;
    private final ClassroomAiNoteRecorder recorder;
    private final ClassroomAiConnectionResolver connectionResolver;
    private final ClassroomAiClient aiClient;
    private final SettingsService settingsService;

    public ClassroomAiNoteStep(ClassroomNoteMapper noteMapper,
                               ClassroomSegmentMapper segmentMapper,
                               ClassroomRecordMapper recordMapper,
                               ClassroomAiNoteRecorder recorder,
                               ClassroomAiConnectionResolver connectionResolver,
                               ClassroomAiClient aiClient,
                               SettingsService settingsService) {
        this.noteMapper = noteMapper;
        this.segmentMapper = segmentMapper;
        this.recordMapper = recordMapper;
        this.recorder = recorder;
        this.connectionResolver = connectionResolver;
        this.aiClient = aiClient;
        this.settingsService = settingsService;
    }

    /** 1 件のノートを生成する（kind = PHASE / FINAL）。 */
    public Map<String, Object> run(BatchExecutionEntity execution, String kind) {
        String batchCode = "PHASE".equals(kind) ? "batC61" : "batC62";
        Long requestedId = noteId(execution);
        ClassroomNoteEntity entity = requestedId == null ? findTarget(kind) : noteMapper.findById(requestedId);
        Map<String, Object> result = new LinkedHashMap<>();
        if (entity == null) {
            result.put("skipped", true);
            result.put("message", "対象がありませんでした（" + batchCode + " 待ちのノートはありません）。");
            return result;
        }
        result.put("noteId", entity.getNoteId());
        result.put("recordId", entity.getRecordId());
        /*
         * **この試行のトークン**で状態更新を照合する（古い試行が遅れて返っても、
         * 新しい試行や既にできた結果を上書きしない）。
         *
         * <p>要求内容にトークンが載っていればそれを使う（起動の受理が付けた値）。載っていなければ
         * 記録のいまの値を使う（スケジューラ・手動実行の回）。どちらも無ければ照合しない
         * （旧い経路の互換）。</p>
         */
        String token = tokenOf(execution, entity);
        entity.setGenerationToken(token);

        if (!isTarget(entity)) {
            result.put("skipped", true);
            result.put("message", "既に生成済みのためスキップしました。（ノートID " + entity.getNoteId() + "）");
            return result;
        }

        List<SettingRequirement> required = "PHASE".equals(kind)
                ? PHASE_REQUIRED_SETTINGS : SUMMARY_REQUIRED_SETTINGS;
        Map<String, String> values = settingsService.requireSettings(batchCode, required);
        String systemPrompt = "PHASE".equals(kind)
                ? values.get("CLASSROOM_AI_NOTE_SYSTEM_PROMPT")
                : values.get("CLASSROOM_AI_SUMMARY_SYSTEM_PROMPT");
        String userTemplate = "PHASE".equals(kind)
                ? values.get("CLASSROOM_AI_NOTE_USER_PROMPT")
                : values.get("CLASSROOM_AI_SUMMARY_USER_PROMPT");
        int timeoutSeconds = intValue(values.get("CLASSROOM_AI_NOTE_TIMEOUT_SECONDS"), 120);
        int maxTokens = intValue(values.get("CLASSROOM_AI_NOTE_MAX_COMPLETION_TOKENS"), 2048);

        ClassroomAiConnectionResolver.AiConnection connection =
                connectionResolver.resolve(batchCode, values.get("CLASSROOM_AI_NOTE_PROVIDER"));

        int startSeq = entity.getStartSeq() == null ? 1 : entity.getStartSeq();
        int endSeq = entity.getEndSeq() == null ? startSeq : entity.getEndSeq();
        String transcript = transcriptOf(entity.getRecordId(), startSeq, endSeq);
        String userPrompt = (userTemplate == null ? "{transcript}" : userTemplate)
                .replace("{transcript}", transcript);

        recorder.markGenerating(entity, execution.getExecutionId());

        String promptSummary = userPrompt.length() > AI_BODY_LIMIT
                ? userPrompt.substring(0, AI_BODY_LIMIT) : userPrompt;
        Timestamp startedAt = Timestamp.valueOf(LocalDateTime.now());
        long startedMs = System.currentTimeMillis();
        ClassroomAiClient.AiResponse response = aiClient.call(new ClassroomAiClient.AiRequest(
                connection.provider(), connection.model(), connection.url(), connection.apiKey(),
                systemPrompt, userPrompt, timeoutSeconds, TEMPERATURE, maxTokens));
        int durationMs = (int) (System.currentTimeMillis() - startedMs);

        ClassroomNoteResponseParser.ParseResult parsed = response.isSuccess()
                ? ClassroomNoteResponseParser.parse(response.body())
                : ClassroomNoteResponseParser.ParseResult.failure(response.errorCode(), response.errorMessage());

        Long callId = recordCall(entity, connection, promptSummary,
                response.isSuccess() ? response.body() : null,
                parsed.isSuccess() ? "SUCCESS" : "FAILURE",
                response.httpStatus(),
                parsed.isSuccess() ? null : parsed.errorCode(),
                parsed.isSuccess() ? null : parsed.errorMessage(),
                startedAt, durationMs);

        if (!parsed.isSuccess()) {
            fail(entity, parsed.errorCode(), parsed.errorMessage());
        }

        entity.setNoteJson(parsed.noteJson());
        entity.setAiCallId(callId);
        recorder.updateReady(entity);

        result.put("callId", callId);
        result.put("message", "AI が授業ノートを生成しました。（ノートID " + entity.getNoteId() + "）");
        log.info("{} finished. noteId={} recordId={} callId={}", batchCode, entity.getNoteId(),
                entity.getRecordId(), callId);
        return result;
    }

    private ClassroomNoteEntity findTarget(String kind) {
        return "PHASE".equals(kind) ? noteMapper.findPhaseTarget() : noteMapper.findFinalTarget();
    }

    private static boolean isTarget(ClassroomNoteEntity entity) {
        return "PENDING".equals(entity.getStatus()) || "GENERATING".equals(entity.getStatus());
    }

    /** 対象範囲の転写を連番順に連結する。 */
    private String transcriptOf(long recordId, int startSeq, int endSeq) {
        StringBuilder builder = new StringBuilder();
        for (ClassroomSegmentEntity segment : segmentMapper.findTextsByRange(recordId, startSeq, endSeq)) {
            if (segment.getText() == null || segment.getText().isBlank()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(segment.getText());
        }
        return builder.toString();
    }

    /**
     * この実行の**試行のトークン**（状態更新の照合に使う）。
     *
     * <p>起動の受理が付けた値を要求内容から読み、無ければ記録のいまの値を使う。</p>
     */
    static String tokenOf(BatchExecutionEntity execution, ClassroomNoteEntity entity) {
        if (execution != null) {
            String payload = execution.getRequestPayload();
            if (payload != null && !payload.isBlank()) {
                try {
                    JsonNode node = MAPPER.readTree(payload).path("token");
                    if ((node.isTextual() || node.isNumber()) && !node.asText().isBlank()) {
                        return node.asText();
                    }
                } catch (IOException ignored) {
                    // 読めない要求内容は「トークン無し」として扱う（記録の値を使う）
                }
            }
        }
        return entity == null ? null : entity.getGenerationToken();
    }

    /** 要求内容の JSON から noteId を読む。 */
    static Long noteId(BatchExecutionEntity execution) {
        if (execution == null) {
            return null;
        }
        String payload = execution.getRequestPayload();
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode node = MAPPER.readTree(payload).path("noteId");
            return node.isNumber() || node.isTextual() ? node.asLong() : null;
        } catch (IOException cause) {
            return null;
        }
    }

    private Long recordCall(ClassroomNoteEntity entity,
                            ClassroomAiConnectionResolver.AiConnection connection,
                            String prompt, String responseBody, String result,
                            int httpStatus, String errorCode, String errorMessage,
                            Timestamp startedAt, int durationMs) {
        AiCallLogEntity call = new AiCallLogEntity();
        call.setBatchCode("PHASE".equals(entity.getKind()) ? "batC61" : "batC62");
        call.setProcessKey("note-" + entity.getNoteId());
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

    private void fail(ClassroomNoteEntity entity, String errorCode, String message) {
        entity.setErrorCode(errorCode);
        entity.setErrorMessage(message);
        recorder.updateFailed(entity);
        log.warn("classroom note generation failed. noteId={} code={} message={}", entity.getNoteId(),
                errorCode, message);
        throw new ClassroomAiNoteStepException(message == null
                ? "AI のノート生成に失敗しました。" : message);
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

    /** 工程が失敗したことを履歴へ残すための例外。 */
    public static class ClassroomAiNoteStepException extends RuntimeException {
        public ClassroomAiNoteStepException(String message) {
            super(message);
        }
    }
}

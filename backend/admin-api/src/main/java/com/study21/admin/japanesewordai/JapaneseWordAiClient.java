package com.study21.admin.japanesewordai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.batch.AiCallLogEntity;
import com.study21.admin.batch.AiCallLogMapper;
import com.study21.admin.geometryai.GeometryAiClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 日本語単語の AI 呼び出し（batC41〜batC44 で共通）。
 *
 * <p><strong>HTTP は自分で書かない。</strong>OpenAI 互換の呼び出しは既存の
 * {@link GeometryAiClient}（JSON 出力に対応した接縫）をそのまま使う。ここが受け持つのは:</p>
 * <ol>
 *   <li>1 語ぶんのリクエストを組み立てて呼ぶ（画像は無し・JSON 出力）</li>
 *   <li>応答の封筒（{@code choices[0].message.content}）から中身の JSON を取り出す。
 *       Markdown のコードフェンスは剥がし、<b>出力上限で切れた応答は失敗にする</b>
 *       （切れた JSON を DB に入れない）</li>
 *   <li>{@code BAT_AI呼出履歴情報} へ <b>1 回の呼び出し = 1 行</b>（成功でも失敗でも）</li>
 * </ol>
 *
 * <p>トークン数は接縫の応答（OpenAI 互換の {@code usage}）にあれば写す。無ければ null のまま
 * （既存のバッチと同じ扱い）。</p>
 */
@Component
public class JapaneseWordAiClient {

    private static final Logger log = LoggerFactory.getLogger(JapaneseWordAiClient.class);

    /** {@code BAT_AI呼出履歴情報} に残す本文の上限（既存のバッチと同じ 200KB）。 */
    private static final int AI_BODY_LIMIT = 200_000;

    /** 呼出履歴の言語区分（日本語 → 中国語の内容を作る）。 */
    private static final String LANGUAGE = "ja-zh";

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final GeometryAiClient geometryAiClient;
    private final AiCallLogMapper aiCallLogMapper;

    public JapaneseWordAiClient(GeometryAiClient geometryAiClient, AiCallLogMapper aiCallLogMapper) {
        this.geometryAiClient = geometryAiClient;
        this.aiCallLogMapper = aiCallLogMapper;
    }

    /** 1 回の呼び出しに必要な入力。 */
    public record AiCallRequest(
            /** 呼出履歴の バッチコード（{@code batC41} など）。 */
            String batchCode,
            /** 実行ID（{@code BAT_バッチ実行履歴情報.実行ID}。無ければ null）。 */
            Long executionId,
            /** 呼出履歴の 処理キー（{@code japanese-word/<単語ID>/<区分>/attempt-<n>}）。 */
            String processKey,
            /** AI に渡す入力語の見出し（人が履歴を追うときの目印）。 */
            String wordLabel,
            String provider,
            String model,
            String url,
            String apiKey,
            String systemPrompt,
            String userPrompt,
            int timeoutSeconds,
            double temperature,
            int maxCompletionTokens) {
    }

    /** 呼び出しの結果。 */
    public record CallResult(String content, String errorCode, String errorMessage, boolean fatal,
                             /** 呼出履歴の ID（成功・失敗どちらでも入る）。 */
                             Long callLogId) {

        static CallResult success(String content) {
            return new CallResult(content, null, null, false, null);
        }

        static CallResult failure(String errorCode, String errorMessage) {
            // 4xx は待っても直らないので再試行しない（既存のバッチと同じ判定）
            return new CallResult(null, errorCode, errorMessage, "HTTP_4XX".equals(errorCode), null);
        }

        CallResult withCallLogId(Long callLogId) {
            return new CallResult(content, errorCode, errorMessage, fatal, callLogId);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }

        /** 再試行しても直らない失敗（4xx）。 */
        public boolean isFatal() {
            return fatal;
        }
    }

    /**
     * 1 語ぶんを AI に渡す。
     *
     * <p>通信の失敗も、応答が読めない場合も、<b>必ず呼出履歴に 1 行残す</b>
     * （何を送って何が返ったかを人が追えるように）。</p>
     *
     * <p><strong>返す結果には採番された {@code 呼出履歴ID} が入る</strong>
     * （{@code JPN_AI生成履歴情報.呼出履歴ID} に入れるため。切片3 で直した不具合:
     * 以前は常に null を渡していた）。</p>
     */
    public CallResult call(AiCallRequest request) {
        String endpoint = endpointUrl(request.url());
        String prompt = "[system]\n" + request.systemPrompt() + "\n\n[user]\n" + request.userPrompt();
        Timestamp startedAt = Timestamp.valueOf(LocalDateTime.now());
        long startedMs = System.currentTimeMillis();

        GeometryAiClient.AiResponse response = geometryAiClient.call(new GeometryAiClient.AiRequest(
                request.provider(), request.model(), endpoint, request.apiKey(),
                request.systemPrompt(), request.userPrompt(),
                null, null,
                request.timeoutSeconds(), request.temperature(), request.maxCompletionTokens(),
                true));
        int durationMs = (int) (System.currentTimeMillis() - startedMs);

        CallResult result = response.isSuccess()
                ? contentOf(response.body())
                : CallResult.failure(
                        response.errorCode() == null ? "AI_ERROR" : response.errorCode(),
                        response.errorMessage() == null ? "AI の呼び出しに失敗しました。" : response.errorMessage());

        Long callLogId = recordCall(request, endpoint, prompt, response, result, startedAt, durationMs);
        // 採番された 呼出履歴ID を返す（呼び出し側が 生成履歴 へ渡す）
        return result.withCallLogId(callLogId);
    }

    /**
     * 設定の URL を実際に叩く URL にする（既存のバッチと同じ規則）。
     *
     * <p>末尾が {@code /v1} か {@code /compatible-mode} のときは {@code /chat/completions} を補う。</p>
     */
    static String endpointUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (value.endsWith("/v1") || value.endsWith("/compatible-mode")) {
            return value + "/chat/completions";
        }
        return value;
    }

    /**
     * 応答の本文から中身の JSON を取り出す。
     *
     * <ul>
     *   <li>封筒（{@code choices[0].message.content}）なら中身を取る。配列の content にも対応する</li>
     *   <li>{@code finish_reason} が {@code length} なら {@code MAX_TOKENS} で失敗
     *       （途中で切れた JSON はパーサに渡さない）</li>
     *   <li>封筒でない本文（スタブなど）は、そのまま中身として扱う</li>
     *   <li>空なら {@code EMPTY_RESPONSE}、JSON として読めなければ {@code INVALID_RESPONSE}</li>
     * </ul>
     */
    static CallResult contentOf(String body) {
        if (body == null || body.isBlank()) {
            return CallResult.failure("EMPTY_RESPONSE", "AI の応答が空です。");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception cause) {
            return CallResult.failure("INVALID_RESPONSE", "AI の応答が JSON ではありません。");
        }
        JsonNode choices = root.path("choices");
        if (!choices.isArray() || choices.isEmpty()) {
            // 封筒ではない本文は、そのまま中身として扱う（スタブ・切替用の実装）
            return CallResult.success(body);
        }
        JsonNode choice = choices.path(0);
        String content = textOf(choice.path("message").path("content"));
        if (content.isBlank()) {
            return CallResult.failure("EMPTY_RESPONSE", "AI の応答が空です。");
        }
        String stripped = stripFence(content);
        if ("length".equals(choice.path("finish_reason").asText(""))) {
            return CallResult.failure("MAX_TOKENS",
                    "出力が最大トークン数に達して途中で切れました。最大出力Token数を増やしてください。");
        }
        return CallResult.success(stripped);
    }

    /** content は文字列のことも、{@code [{"type":"text","text":"..."}]} のこともある。 */
    private static String textOf(JsonNode content) {
        if (content.isTextual()) {
            return content.asText();
        }
        if (content.isArray()) {
            StringBuilder builder = new StringBuilder();
            for (JsonNode part : content) {
                builder.append(part.path("text").asText(""));
            }
            return builder.toString();
        }
        return "";
    }

    /** Markdown のコードフェンスを剥がす（2.0 と同じ）。 */
    static String stripFence(String value) {
        return value.replaceAll("^```(?:json)?\\s*|\\s*```$", "").trim();
    }

    /** 呼び出し 1 回 = 1 行（成功・失敗のどちらでも残す）。戻り値は採番された 呼出履歴ID。 */
    private Long recordCall(AiCallRequest request, String endpoint, String prompt,
                            GeometryAiClient.AiResponse response, CallResult result,
                            Timestamp startedAt, int durationMs) {
        AiCallLogEntity call = new AiCallLogEntity();
        call.setExecutionId(request.executionId());
        call.setBatchCode(request.batchCode());
        call.setProcessKey(request.processKey());
        call.setLanguage(LANGUAGE);
        call.setAiType(request.provider());
        call.setModelName(limit(request.model(), 100));
        call.setCallUrl(endpoint);
        call.setHttpStatus(response.httpStatus() > 0 ? response.httpStatus() : null);
        call.setStartTime(startedAt);
        call.setEndTime(Timestamp.valueOf(LocalDateTime.now()));
        call.setDurationMs(durationMs);
        call.setResult(result.isSuccess() ? "SUCCESS" : "FAILURE");
        call.setErrorCode(limit(result.errorCode(), 100));
        call.setErrorMessage(limit(result.errorMessage(), AI_BODY_LIMIT));
        call.setPrompt(limit(prompt, AI_BODY_LIMIT));
        // 応答は成功のときだけ本文を残す。失敗のときは**プロバイダーが返した理由**が
        // エラーメッセージに入っている（`AiErrorMessages.clientError`。2026-09-25 に追加）
        call.setResponse(limit(response.isSuccess() ? response.body() : null, AI_BODY_LIMIT));
        // バッチは人が操作しないので アカウントID は null（記録元は 登録元コード で表す）
        call.setCreatedBy(null);
        call.setSourceCode("BATCH");
        aiCallLogMapper.insert(call);
        log.info("japanese word ai call recorded. batchCode={} processKey={} result={} httpStatus={} durationMs={}",
                request.batchCode(), request.processKey(), call.getResult(), call.getHttpStatus(), durationMs);
        // MyBatis が採番した 呼出履歴ID（useGeneratedKeys）を呼び出し側へ渡す
        return call.getCallId();
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}

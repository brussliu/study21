package com.study21.admin.studymonitor;

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
import java.util.Map;

/**
 * 学習モニターのスナップショット 1 枚を AI で判定する接縫（batL03）。
 *
 * <p><strong>HTTP は自分で書かない。</strong>OpenAI 互換の呼び出しは既存の {@link GeometryAiClient}
 * （画像と JSON 出力に対応した接縫。実装は {@code GeometryAiClientConfiguration} が 1 つだけ決める）を
 * そのまま使う。2.0 の {@code BatL03Task#callVision} が組み立てていたリクエスト
 * （{@code messages[0]=system} / {@code messages[1].content=[text, image_url]} /
 * {@code temperature 0.1} / {@code response_format=json_object}）と同じ形になる。</p>
 *
 * <p>ここが受け持つのは 3 つ:</p>
 * <ol>
 *   <li>応答の検証（{@code status} / {@code confidence} / {@code reason}。**状態は 6 つだけ**許可）</li>
 *   <li>日本語の状態 → 2.1 のコード（{@code STUDY_NO_PC} など）への変換</li>
 *   <li>{@code BAT_AI呼出履歴情報} へ 1 回の呼び出し = 1 行の記録（既存の {@link AiCallLogMapper#insert}）</li>
 * </ol>
 *
 * <p>テストでは {@link GeometryAiClient} をモックする（実装の選択はこのクラスの関知するところではない）。</p>
 */
@Component
public class StudyMonitorAiClient {

    private static final Logger log = LoggerFactory.getLogger(StudyMonitorAiClient.class);

    /** 2.0 と同じ温度（判定を安定させるため低く固定する。設定にキーは無い）。 */
    private static final double TEMPERATURE = 0.1;

    /**
     * 応答の最大トークン数。
     *
     * <p>2.0 は {@code max_tokens} を送っていなかったが、接縫（{@code AiRequest}）は値が必須なので、
     * 判定 JSON（status / confidence / reason）に十分な小さめの値を送る。</p>
     */
    private static final int MAX_COMPLETION_TOKENS = 1024;

    /** {@code BAT_AI呼出履歴情報} に残す本文の上限（既存のバッチと同じ 200KB）。 */
    private static final int AI_BODY_LIMIT = 200_000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * AI が返す日本語の状態 → 2.1 のコード。
     *
     * <p>2.0 は日本語の自由文字列のまま DB に入れていた（CHECK に日本語を並べていた）。
     * 2.1 の DB は**コード**で持ち、表示名は画面側が持つ（{@code docs/} の設計と同じ）。
     * プロンプト（{@code STUDY_MONITOR_FIRST_SYSTEM_PROMPT}）は 2.0 のままなので、
     * AI は日本語で返す。ここで必ずコードへ寄せる。</p>
     */
    private static final Map<String, String> STATE_CODES = Map.of(
            "学習中（PC不使用・読書または筆記）", "STUDY_NO_PC",
            "学習中（PC使用）", "STUDY_PC",
            "離席中", "AWAY",
            "PC使用中（非学習）", "PC_NON_STUDY",
            "その他", "OTHER",
            "判断不可", "UNKNOWN");

    private final GeometryAiClient aiClient;
    private final AiCallLogMapper aiCallLogMapper;

    public StudyMonitorAiClient(GeometryAiClient aiClient, AiCallLogMapper aiCallLogMapper) {
        this.aiClient = aiClient;
        this.aiCallLogMapper = aiCallLogMapper;
    }

    /** 1 回の判定に必要な入力。 */
    public record AnalyzeRequest(
            /** 実行ID（{@code BAT_AI呼出履歴情報.実行ID} に入れる。無ければ null）。 */
            Long executionId,
            /** 呼出履歴の 処理キー（{@code study-monitor/snapshot/<ID>/first}）。 */
            String processingKey,
            String provider,
            String model,
            String url,
            String apiKey,
            String systemPrompt,
            String userPrompt,
            /** AI へ渡す画像（**縮小済み**。ログにも DB にも入れない）。 */
            byte[] image,
            String imageMime,
            int timeoutSeconds) {
    }

    /**
     * 判定の結果。
     *
     * @param stateCode   2.1 のコード（{@code STUDY_NO_PC} など）
     * @param confidence  信頼度（0〜1）
     * @param reason      判定理由（空のこともある）
     * @param rawResponse AI の生応答（JSON 本文。そのまま {@code 一次応答JSON} に入れる）
     */
    public record AnalyzeResult(String stateCode, double confidence, String reason, String rawResponse) {
    }

    /**
     * 1 枚を判定する。
     *
     * <p>AI の呼び出しに失敗したとき・応答が読めないときは {@link AnalyzeException} を投げる。
     * どちらの場合も**呼出履歴には 1 行残る**（何を送って何が返ったかを人が追えるように）。</p>
     */
    public AnalyzeResult analyze(AnalyzeRequest request) {
        String endpoint = endpointUrl(request.url());
        String prompt = promptOf(request);
        Timestamp startedAt = Timestamp.valueOf(LocalDateTime.now());
        long startedMs = System.currentTimeMillis();
        GeometryAiClient.AiResponse response = aiClient.call(new GeometryAiClient.AiRequest(
                request.provider(), request.model(), endpoint, request.apiKey(),
                request.systemPrompt(), request.userPrompt(), request.image(), request.imageMime(),
                request.timeoutSeconds(), TEMPERATURE, MAX_COMPLETION_TOKENS, true));
        int durationMs = (int) (System.currentTimeMillis() - startedMs);
        recordCall(request, endpoint, prompt, response, startedAt, durationMs);

        if (!response.isSuccess()) {
            String message = response.errorMessage() == null
                    ? "AI の呼び出しに失敗しました。" : response.errorMessage();
            throw new AnalyzeException(message);
        }
        return validate(replyOf(response.body()));
    }

    /**
     * 設定の URL を実際に叩く URL にする。
     *
     * <p>2.0 と同じ規則: 末尾が {@code /v1} か {@code /compatible-mode} のときは
     * {@code /chat/completions} を補う（LangChain4j の実装は baseUrl から
     * {@code /chat/completions} を外して付け直すので結果は同じだが、切り戻し用の
     * {@code java.net.http} の実装は URL をそのまま叩くため、ここで補う必要がある）。</p>
     */
    static String endpointUrl(String url) {
        String value = url == null ? "" : url.trim();
        if (value.endsWith("/v1") || value.endsWith("/compatible-mode")) {
            return value + "/chat/completions";
        }
        return value;
    }

    /** 呼出履歴に残すプロンプト（2.0 と同じ形）。 */
    private static String promptOf(AnalyzeRequest request) {
        return "[system]\n" + request.systemPrompt() + "\n\n[user]\n" + request.userPrompt();
    }

    /**
     * 応答の本文から AI の返した JSON を取り出す。
     *
     * <p>接縫は OpenAI 互換の封筒（{@code choices[0].message.content}）を返す。
     * 封筒でない本文（スタブなど）は、そのまま判定 JSON として読む。</p>
     */
    private static String replyOf(String body) {
        if (body == null || body.isBlank()) {
            throw new AnalyzeException("AI の応答が空です。");
        }
        JsonNode root;
        try {
            root = MAPPER.readTree(body);
        } catch (Exception cause) {
            throw new AnalyzeException("AI の応答が JSON ではありません。");
        }
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        String reply = content.isTextual() ? content.asText() : (root.has("choices") ? "" : body);
        if (reply.isBlank()) {
            throw new AnalyzeException("AI の応答が空です。");
        }
        // モデルが Markdown のコードフェンスを付けることがある（2.0 と同じく剥がす）
        return reply.replaceAll("^```(?:json)?\\s*|\\s*```$", "").trim();
    }

    /** AI の応答 JSON を検証し、コードへ変換する。 */
    private static AnalyzeResult validate(String rawJson) {
        JsonNode json;
        try {
            json = MAPPER.readTree(rawJson);
        } catch (Exception cause) {
            throw new AnalyzeException("AI の応答が JSON ではありません。");
        }
        if (!json.isObject()) {
            throw new AnalyzeException("AI の応答が JSON ではありません。", rawJson);
        }
        String status = json.path("status").asText("").trim();
        String stateCode = STATE_CODES.get(status);
        if (stateCode == null) {
            // 何が返ったか分かるように生応答も渡す（ERROR 行の 一次応答JSON に残る）
            throw new AnalyzeException("AI の状態値が不正です: " + status, rawJson);
        }
        // 2.0 と同じ判定（数値として読める文字列 "0.9" も受ける）
        double confidence = json.path("confidence").asDouble(-1);
        if (confidence < 0 || confidence > 1) {
            throw new AnalyzeException("AI の confidence が不正です。", rawJson);
        }
        // 理由は空でも許す（2.0 と同じ。列は NULL 可）
        String reason = json.path("reason").asText("").trim();
        return new AnalyzeResult(stateCode, confidence, reason, rawJson);
    }

    /** 呼び出し 1 回 = 1 行（成功・失敗のどちらでも残す）。 */
    private void recordCall(AnalyzeRequest request, String endpoint, String prompt,
                           GeometryAiClient.AiResponse response, Timestamp startedAt, int durationMs) {
        AiCallLogEntity call = new AiCallLogEntity();
        call.setExecutionId(request.executionId());
        call.setBatchCode(StudyMonitorAnalyzeHandler.TASK_CODE);
        call.setProcessKey(request.processingKey());
        call.setLanguage("ja");
        call.setAiType(request.provider());
        call.setModelName(limit(request.model(), 100));
        call.setCallUrl(endpoint);
        call.setHttpStatus(response.httpStatus() > 0 ? response.httpStatus() : null);
        call.setStartTime(startedAt);
        call.setEndTime(Timestamp.valueOf(LocalDateTime.now()));
        call.setDurationMs(durationMs);
        call.setResult(response.isSuccess() ? "SUCCESS" : "FAILURE");
        call.setErrorCode(limit(response.errorCode(), 100));
        call.setErrorMessage(limit(response.errorMessage(), AI_BODY_LIMIT));
        call.setPrompt(limit(prompt, AI_BODY_LIMIT));
        call.setResponse(limit(response.body(), AI_BODY_LIMIT));
        // バッチは人が操作しないので アカウントID は null（記録元は 登録元コード で表す）
        call.setCreatedBy(null);
        call.setSourceCode("BATCH");
        aiCallLogMapper.insert(call);
        log.info("study monitor ai call recorded. processKey={} result={} httpStatus={} durationMs={}",
                request.processingKey(), call.getResult(), call.getHttpStatus(), durationMs);
    }

    private static String limit(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    /** 呼び出し・応答の検証に失敗したことを示す（日本語の理由をそのまま画面と ERROR 行に残す）。 */
    public static class AnalyzeException extends RuntimeException {

        /** AI の生応答（**JSON として読めたときだけ**。ERROR 行の 一次応答JSON に残す）。 */
        private final String rawResponse;

        public AnalyzeException(String message) {
            this(message, null);
        }

        public AnalyzeException(String message, String rawResponse) {
            super(message);
            this.rawResponse = rawResponse;
        }

        public String rawResponse() {
            return rawResponse;
        }
    }
}

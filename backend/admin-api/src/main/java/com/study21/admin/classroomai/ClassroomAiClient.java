package com.study21.admin.classroomai;

/**
 * AI（LLM）を呼ぶ接縫（授業ノート・最終まとめ）。
 *
 * <p>画像添付が無い分、{@code GeometryAiClient} よりシンプル（テキストだけの chat/completions）。
 * インタフェースにしてあるので、テストと E2E は固定の応答を返すスタブ
 * （{@link ClassroomAiStubClient}）を差し込める。</p>
 */
public interface ClassroomAiClient {

    /** 1 回の呼び出し（OpenAI 互換の chat/completions）。 */
    record AiRequest(
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

    /** 1 回の応答。`errorCode` が null なら成功（本文は AI の生応答）。 */
    record AiResponse(int httpStatus, String body, String errorCode, String errorMessage) {

        public static AiResponse success(int status, String body) {
            return new AiResponse(status, body, null, null);
        }

        public static AiResponse failure(int status, String code, String message) {
            return new AiResponse(status, null, code, message);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }

        /** 再試行しても意味が無い失敗か（キー不正・モデル不正などの 4xx）。 */
        public boolean isFatal() {
            return "HTTP_4XX".equals(errorCode);
        }
    }

    AiResponse call(AiRequest request);
}

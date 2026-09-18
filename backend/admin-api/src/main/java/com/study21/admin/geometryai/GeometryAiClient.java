package com.study21.admin.geometryai;

/**
 * AI（LLM）を呼ぶ接縫（AI 生図）。
 *
 * <p>インタフェースにしてあるので、テストと E2E は固定の応答を返すスタブ
 * （{@link GeometryAiStubClient}）を差し込める（`ReadingDictionaryClient` と同じ作法。
 * 設計 §9.4「AI を実呼び出ししない工夫」）。</p>
 *
 * <p>実装は `java.net.http.HttpClient` を使う（`RestClient` / `WebClient` / `RestTemplate` は
 * このリポジトリに依存ごと存在しない）。</p>
 */
public interface GeometryAiClient {

    /** 1 回の呼び出し（OpenAI 互換の chat/completions を想定）。 */
    record AiRequest(
            String provider,
            String model,
            String url,
            String apiKey,
            String systemPrompt,
            String userPrompt,
            /** 添付画像（AI 生図は切り抜き・縮小した PNG/JPEG）。**ログにも DB にも入れない** */
            byte[] image,
            String imageMime,
            int timeoutSeconds,
            double temperature,
            int maxCompletionTokens,
            /** 出力形式が JSON のときは response_format を付ける（対応プロバイダのみ） */
            boolean jsonResponse) {
    }

    /** 1 回の応答。`errorCode` が null なら成功（本文は AI の生応答）。 */
    record AiResponse(
            int httpStatus,
            String body,
            String errorCode,
            String errorMessage) {

        public static AiResponse success(int status, String body) {
            return new AiResponse(status, body, null, null);
        }

        public static AiResponse failure(int status, String code, String message) {
            return new AiResponse(status, null, code, message);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }

        /** 再試行しても意味が無い失敗（キー・モデル不正などの 4xx。設計 §6.4）。 */
        public boolean isFatal() {
            return "HTTP_4XX".equals(errorCode);
        }
    }

    AiResponse call(AiRequest request);
}

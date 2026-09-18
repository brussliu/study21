package com.study21.user.classroom;

import java.util.List;

/**
 * 音声の書き起こし（STT）を呼ぶ接縫。
 *
 * <p>インタフェースにしてあるので、テストと E2E は固定の応答を返すスタブ
 * （{@link ClassroomSttStubClient}）を差し込める（{@code GeometryAiClient} と同じ作法）。
 * 実装は {@code java.net.http.HttpClient} を使う（OpenAI 互換の {@code /audio/transcriptions} を想定）。</p>
 */
public interface ClassroomSttClient {

    /** 1 回の書き起こし（分塊 1 個）。 */
    record SttRequest(
            String provider,
            String model,
            String url,
            String apiKey,
            /** STT へ渡す主言語コード（BCP-47。例 ja-JP）。OpenAI 互換では language にそのまま使う */
            String languageCode,
            /** 混在モードの副言語コード（例 en-US）。無ければ null。Google の alternativeLanguageCodes に使う */
            String alternativeLanguageCode,
            byte[] audio,
            String mime,
            int timeoutSeconds) {
    }

    /** 取り出したセグメント（話者分離が無いときは speaker=null で、表示側が「講義」に丸める）。 */
    record Segment(String text, String speaker, String language, Double start, Double end) {
    }

    /** 1 回の応答。`errorCode` が null なら成功。 */
    record SttResponse(int httpStatus, List<Segment> segments, String errorCode, String errorMessage) {

        public static SttResponse success(int status, List<Segment> segments) {
            return new SttResponse(status, segments == null ? List.of() : segments, null, null);
        }

        public static SttResponse failure(int status, String code, String message) {
            return new SttResponse(status, List.of(), code, message);
        }

        public boolean isSuccess() {
            return errorCode == null;
        }

        /** 再試行しても意味が無い失敗か（キー不正・モデル不正などの 4xx）。 */
        public boolean isFatal() {
            return "HTTP_4XX".equals(errorCode);
        }
    }

    SttResponse transcribe(SttRequest request);
}

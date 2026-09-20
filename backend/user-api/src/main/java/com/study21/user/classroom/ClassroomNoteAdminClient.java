package com.study21.user.classroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * 授業まとめの**内部入口**（admin-api）を呼ぶクライアント。
 *
 * <p><b>なぜ user-api を通すか</b>: 画面から admin-api を直接叩かせると、noteId を差し替えるだけで
 * **他人のまとめを起動・回復**できてしまう。利用者の権限（ログイン・授業の所有権・noteId の帰属）は
 * user-api で確かめ、**そのうえで**このクライアントがサービス間の合言葉を付けて admin-api を呼ぶ。</p>
 *
 * <p>合言葉は環境変数 {@code STUDY21_INTERNAL_TOKEN} から読む。**未設定なら呼ばずに失敗**する
 * （匿名で叩きに行かない）。前端・URL・ログには出さない。タイムアウトを置き、応答を失っても
 * 「成功」とは言わない（呼び側が失敗として扱えるように例外を投げる）。</p>
 */
@Component
public class ClassroomNoteAdminClient {

    private static final Logger log = LoggerFactory.getLogger(ClassroomNoteAdminClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** admin-api の置き場（配備では compose のサービス名＋ポート）。 */
    private final String baseUrl;
    /** サービス間の合言葉（未設定なら内部入口は呼べない）。 */
    private final String internalToken;
    /** 1 回の呼び出しの上限（**AI の完了は待たない**受付 API なので短くてよい）。 */
    private final Duration timeout;

    private final HttpClient http;

    public ClassroomNoteAdminClient(
            @Value("${study21.classroom.admin-api-url:http://admin-api:8081}") String baseUrl,
            @Value("${study21.internal.token:}") String internalToken,
            @Value("${study21.classroom.admin-api-timeout-seconds:15}") int timeoutSeconds) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://admin-api:8081" : baseUrl.trim();
        this.internalToken = internalToken == null || internalToken.isBlank() ? null : internalToken.trim();
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    /** 内部入口が使えるか（合言葉が設定されているか）。 */
    public boolean configured() {
        return internalToken != null;
    }

    /**
     * まとめの**起動を受理**してもらう（`POST /api/admin/batch/classroom/notes/{noteId}/run`）。
     *
     * @return 受理の応答（そのまま画面へ返す）
     * @throws ClassroomNoteCallException 呼べなかった・拒否された・応答が読めなかった
     */
    public JsonNode accept(long noteId, String operator) {
        return post("/api/admin/batch/classroom/notes/" + noteId + "/run",
                "{\"operator\":\"" + escape(operator) + "\"}");
    }

    /**
     * まとめの**状態を確かめ、失联していれば回復**してもらう
     * （`POST /api/admin/batch/classroom/notes/{noteId}/recover`）。
     */
    public JsonNode recover(long noteId) {
        return post("/api/admin/batch/classroom/notes/" + noteId + "/recover", "{}");
    }

    private JsonNode post(String path, String body) {
        if (internalToken == null) {
            /*
             * **合言葉が無い＝呼べない**。匿名で叩きに行かない（設定漏れを「動いているように」
             * 見せない）。画面には「開始できませんでした」として伝わる。
             */
            throw new ClassroomNoteCallException("サービス間の認証が設定されていません"
                    + "（STUDY21_INTERNAL_TOKEN）。", false);
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", internalToken)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            // **合言葉はログに出さない**（パスと結果だけ）
            log.info("classroom note internal call. path={} status={}", path, response.statusCode());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                // 権限（合言葉）の問題。**利用者の認証切れとは別**なので区別できる形で投げる
                throw new ClassroomNoteCallException("まとめの操作が許可されていません（権限）。", false);
            }
            if (response.statusCode() >= 400) {
                throw new ClassroomNoteCallException("まとめの操作に失敗しました（"
                        + response.statusCode() + "）。", true);
            }
            JsonNode root = MAPPER.readTree(response.body());
            // 統一の応答形式（success + data）。success=false は**成功ではない**
            if (root.hasNonNull("success") && !root.get("success").asBoolean()) {
                throw new ClassroomNoteCallException(messageOf(root, "まとめの操作に失敗しました。"), true);
            }
            return root.has("data") ? root.get("data") : root;
        } catch (ClassroomNoteCallException cause) {
            throw cause;
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new ClassroomNoteCallException("まとめの操作が中断されました。", true);
        } catch (Exception cause) {
            // 応答を失った・繋がらない: **成功とは言わない**（呼び側が失敗として扱う）
            log.warn("classroom note internal call failed. path={} cause={}", path,
                    cause.getClass().getSimpleName());
            throw new ClassroomNoteCallException("まとめの操作の応答を確認できませんでした。", true);
        }
    }

    private static String messageOf(JsonNode root, String fallback) {
        JsonNode message = root.get("message");
        return message != null && message.isTextual() && !message.asText().isBlank()
                ? message.asText() : fallback;
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /** 内部入口を呼べなかった（理由つき）。 */
    public static class ClassroomNoteCallException extends RuntimeException {

        private final boolean retryable;

        public ClassroomNoteCallException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        /** もう一度試せば直る可能性があるか（応答を失った等）。 */
        public boolean retryable() {
            return retryable;
        }
    }
}

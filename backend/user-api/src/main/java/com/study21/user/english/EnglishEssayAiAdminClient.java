package com.study21.user.english;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

/**
 * 英作文の AI の**内部入口**（admin-api の `/api/admin/batch/english-essay/...`）を呼ぶクライアント。
 *
 * <p><b>なぜ user-api を通すか</b>: 画面から admin-api を直接叩かせると、`essayId` を差し替えるだけで
 * **他人の作文に添削を積めて、AI の費用を使わせられる**（2026-09-27 の審査で見つかった穴）。
 * 同期 OCR も同じで、**URL を知っていれば誰でも AI を呼べた**。利用者の権限（ログイン・作文の
 * 所有権）は user-api で確かめ、**そのうえで**このクライアントがサービス間の合言葉を付けて
 * admin-api を呼ぶ。作りは {@link com.study21.user.classroom.ClassroomNoteAdminClient} と同じ。</p>
 *
 * <p>合言葉は {@code STUDY21_INTERNAL_TOKEN}（設定 `study21.internal.token`）から読む。
 * **未設定なら呼ばずに失敗**する（匿名で叩きに行かない）。前端・URL・ログには出さない。
 * タイムアウトを置き、応答を失っても「成功」とは言わない（呼び側が失敗として扱えるように例外を投げる）。</p>
 *
 * <p><b>次に何回目を積むかは admin-api が決める**。こちらは `round` を指定されたときだけ送る
 * （`回数` の採番の規則を 2 か所に置かない）。</p>
 */
@Component
public class EnglishEssayAiAdminClient {

    private static final Logger log = LoggerFactory.getLogger(EnglishEssayAiAdminClient.class);

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 添削の受付の内部入口。 */
    private static final String GRADINGS_PATH = "/api/admin/batch/english-essay/gradings";
    /** 同期 OCR の内部入口。 */
    private static final String OCR_PATH = "/api/admin/batch/english-essay/ocr";

    /** 失敗の理由に使う呼び名（何をしようとして失敗したか）。 */
    private static final String GRADING_LABEL = "英作文の添削の受付";
    private static final String OCR_LABEL = "英作文の画像の文字認識";

    /** admin-api の置き場（配備では compose のサービス名＋ポート）。 */
    private final String baseUrl;
    /** サービス間の合言葉（未設定なら内部入口は呼べない）。 */
    private final String internalToken;
    /** 1 回の呼び出しの上限（**AI の完了は待たない**受付 API なので短くてよい）。 */
    private final Duration timeout;
    /** OCR の上限。**AI の完了を待つ同期 API** なので長くする（画像 8 枚で分単位）。 */
    private final Duration ocrTimeout;

    private final HttpClient http;

    public EnglishEssayAiAdminClient(
            @Value("${study21.english-essay.admin-api-url:http://admin-api:8081}") String baseUrl,
            @Value("${study21.internal.token:}") String internalToken,
            @Value("${study21.english-essay.admin-api-timeout-seconds:15}") int timeoutSeconds,
            @Value("${study21.english-essay.admin-api-ocr-timeout-seconds:180}") int ocrTimeoutSeconds) {
        this.baseUrl = baseUrl == null || baseUrl.isBlank() ? "http://admin-api:8081" : baseUrl.trim();
        this.internalToken = internalToken == null || internalToken.isBlank() ? null : internalToken.trim();
        this.timeout = Duration.ofSeconds(Math.max(1, timeoutSeconds));
        this.ocrTimeout = Duration.ofSeconds(Math.max(1, ocrTimeoutSeconds));
        this.http = HttpClient.newBuilder().connectTimeout(this.timeout).build();
    }

    /** 受付の結果（画面へそのまま返す形）。 */
    public record Accepted(long gradingId, int round, String message) {
    }

    /** OCR に渡す画像 1 枚（**ファイルの実体**と、その名前と種類）。 */
    public record OcrImage(String fileName, String mime, byte[] bytes) {
    }

    /**
     * 添削の**受付を依頼する**（`POST /api/admin/batch/english-essay/gradings`）。
     *
     * @param essayId 英作文ID（**呼ぶ前に user-api が所有者を確かめていること**）
     * @param round   何回目か（null なら admin-api が次の回を決める）
     * @throws EnglishEssayCallException 呼べなかった・拒否された・応答が読めなかった（日本語の理由つき）
     */
    public Accepted acceptGrading(long essayId, Integer round) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("essayId", essayId);
        if (round != null) {
            // 指定されたときだけ送る（省くと admin-api が「今までの最大 + 1」を入れる）
            body.put("round", round);
        }
        JsonNode data = post(GRADINGS_PATH, body.toString());
        if (data == null || !data.isObject() || !data.hasNonNull("gradingId")) {
            // 受付を確認できない応答は**成功と見なさない**
            throw new EnglishEssayCallException("英作文の添削の受付の応答を確認できませんでした。"
                    + "少し待ってから、もう一度お試しください。", true);
        }
        long gradingId = data.get("gradingId").asLong();
        int acceptedRound = data.hasNonNull("round") ? data.get("round").asInt() : (round == null ? 0 : round);
        String message = data.hasNonNull("message") && !data.get("message").asText().isBlank()
                ? data.get("message").asText()
                : "英検基準AI添削を受付けました（第 " + acceptedRound + " 回）。バックグラウンドで処理します。";
        return new Accepted(gradingId, acceptedRound, message);
    }

    /**
     * 画像を**文字にする**（`POST /api/admin/batch/english-essay/ocr`。同期）。
     *
     * <p>パラメータ名（`level` / `categories` / `files`）は admin-api の入口と**同じ**にする
     * （ここで名前を変えると、転送先で読めなくなる）。`categories` は**ファイルと同じ順**の
     * カンマ区切りで、空なら送らない（既定は admin-api が決める）。</p>
     *
     * @return 応答の `data`（`questionText` / `essayText` / `pages` / 信頼度）
     * @throws EnglishEssayCallException 呼べなかった・拒否された・応答が読めなかった（日本語の理由つき）
     */
    public JsonNode recognize(String level, String categories, List<OcrImage> images) {
        String boundary = "----study21" + UUID.randomUUID().toString().replace("-", "");
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + OCR_PATH))
                // **AI の完了を待つ**ので、受付 API より長い上限を使う
                .timeout(ocrTimeout)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("X-Internal-Token", requireToken())
                .POST(HttpRequest.BodyPublishers.ofByteArray(multipartBody(boundary, level, categories, images)))
                .build();
        return send(request, OCR_LABEL);
    }

    /** 内部入口を 1 回呼ぶ（JSON。合言葉つき。応答の `data` を返す）。 */
    private JsonNode post(String path, String body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(timeout)
                .header("Content-Type", "application/json")
                .header("X-Internal-Token", requireToken())
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return send(request, GRADING_LABEL);
    }

    /**
     * 合言葉を取り出す。
     *
     * <p>**合言葉が無い＝呼べない**。匿名で叩きに行かない（設定漏れを「動いているように」
     * 見せない）。画面には「受付けられませんでした」として伝わる。</p>
     */
    private String requireToken() {
        if (internalToken == null) {
            throw new EnglishEssayCallException("サービス間の認証が設定されていません"
                    + "（STUDY21_INTERNAL_TOKEN）。", false);
        }
        return internalToken;
    }

    /** 内部入口を 1 回呼び、統一の応答形式（`success` + `data`）を解く。 */
    private JsonNode send(HttpRequest request, String label) {
        try {
            HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
            // **合言葉はログに出さない**（パスと結果だけ）
            log.info("english essay internal call. path={} status={}",
                    request.uri().getPath(), response.statusCode());
            if (response.statusCode() == 401 || response.statusCode() == 403) {
                // 権限（合言葉）の問題。**利用者の認証切れとは別**なので区別できる形で投げる
                throw new EnglishEssayCallException(label + "が許可されていません"
                        + "（内部入口の設定を確認してください）。", false);
            }
            if (response.statusCode() >= 400) {
                String reason = messageOf(parseOrNull(response.body()));
                throw new EnglishEssayCallException(reason != null ? reason
                        : label + "に失敗しました（" + response.statusCode() + "）。", false);
            }
            JsonNode root = MAPPER.readTree(response.body());
            // 統一の応答形式（success + data）。success=false は**成功ではない**
            if (root.hasNonNull("success") && !root.get("success").asBoolean()) {
                String reason = messageOf(root);
                throw new EnglishEssayCallException(reason != null ? reason
                        : label + "に失敗しました。", true);
            }
            return root.has("data") ? root.get("data") : root;
        } catch (EnglishEssayCallException cause) {
            throw cause;
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            throw new EnglishEssayCallException(label + "が中断されました。", true);
        } catch (Exception cause) {
            // 応答を失った・繋がらない: **成功とは言わない**（呼び側が失敗として扱う）
            log.warn("english essay internal call failed. path={} cause={}", request.uri().getPath(),
                    cause.getClass().getSimpleName());
            throw new EnglishEssayCallException(label + "の応答を確認できませんでした。"
                    + "少し待ってから、もう一度お試しください。", true);
        }
    }

    /**
     * multipart/form-data の本文を組む（`level` / `categories` / `files`）。
     *
     * <p>`java.net.http` には multipart が無いので自前で組む。**admin-api の入口と
     * 同じパラメータ名**にすること（Spring は名前で束縛する）。</p>
     */
    private static byte[] multipartBody(String boundary, String level, String categories, List<OcrImage> images) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            if (level != null && !level.isBlank()) {
                writeTextField(out, boundary, "level", level.trim());
            }
            if (categories != null && !categories.isBlank()) {
                writeTextField(out, boundary, "categories", categories.trim());
            }
            if (images != null) {
                for (OcrImage image : images) {
                    writeFilePart(out, boundary, image);
                }
            }
            writeText(out, "--" + boundary + "--\r\n");
        } catch (IOException cause) {
            // ByteArrayOutputStream では起きない（起きたら**成功と言わない**）
            throw new EnglishEssayCallException("英作文の画像の文字認識の要求を組み立てられませんでした。", false);
        }
        return out.toByteArray();
    }

    private static void writeTextField(ByteArrayOutputStream out, String boundary, String name, String value)
            throws IOException {
        writeText(out, "--" + boundary + "\r\n");
        writeText(out, "Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n");
        writeText(out, value + "\r\n");
    }

    private static void writeFilePart(ByteArrayOutputStream out, String boundary, OcrImage image) throws IOException {
        writeText(out, "--" + boundary + "\r\n");
        writeText(out, "Content-Disposition: form-data; name=\"files\"; filename=\""
                + headerSafe(image.fileName(), "image") + "\"\r\n");
        writeText(out, "Content-Type: " + headerSafe(image.mime(), "application/octet-stream") + "\r\n\r\n");
        out.write(image.bytes() == null ? new byte[0] : image.bytes());
        writeText(out, "\r\n");
    }

    private static void writeText(ByteArrayOutputStream out, String text) throws IOException {
        out.write(text.getBytes(StandardCharsets.UTF_8));
    }

    /** ヘッダに埋め込む値を安全にする（改行・引用符は multipart の区切りを壊す）。 */
    private static String headerSafe(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        return value.replace("\r", " ").replace("\n", " ").replace("\"", "'").trim();
    }

    /** 統一の応答形式（`message`）から日本語の理由を取り出す（無ければ null）。 */
    private static String messageOf(JsonNode root) {
        if (root == null) {
            return null;
        }
        JsonNode message = root.get("message");
        return message != null && message.isTextual() && !message.asText().isBlank()
                ? message.asText() : null;
    }

    /** 応答を JSON として読む（読めなければ null。例外の中身を画面へ漏らさない）。 */
    private static JsonNode parseOrNull(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readTree(body);
        } catch (Exception cause) {
            return null;
        }
    }

    /** 内部入口を呼べなかった（日本語の理由つき）。 */
    public static class EnglishEssayCallException extends RuntimeException {

        private final boolean retryable;

        public EnglishEssayCallException(String message, boolean retryable) {
            super(message);
            this.retryable = retryable;
        }

        /** もう一度試せば直る可能性があるか（応答を失った等）。 */
        public boolean retryable() {
            return retryable;
        }
    }
}

package com.study21.user.english;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link EnglishEssayAiAdminClient} の**同期 OCR の契約**（送り先・multipart の中身・合言葉・
 * 失敗の伝え方）の検証。
 *
 * <p>本物の HTTP（その場で立てた小さなサーバー）で確かめる。Mockito で HttpClient を差し替えると
 * **ヘッダの付け忘れ**（＝合言葉が無いまま匿名で叩く）と **multipart の組み立て間違い**
 * （画像が 1 枚も載っていない・区分がずれる）を見つけられない。</p>
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>送り先は `POST /api/admin/batch/english-essay/ocr`、本文は multipart
 *       （`level` / `categories` / `files`。**admin-api の入口と同じパラメータ名**）</li>
 *   <li>`X-Internal-Token` が付く（未設定なら**呼ばずに**失敗。匿名で叩きに行かない）</li>
 *   <li>応答の `data` をそのまま返す（`questionText` / `essayText` / `pages` / 信頼度）</li>
 *   <li>401/403・4xx/5xx・`success=false`・繋がらない は**日本語の理由**つきで失敗</li>
 * </ol>
 */
class EnglishEssayOcrAdminClientTest {

    private static final String PATH = "/api/admin/batch/english-essay/ocr";

    private HttpServer server;
    private final List<String> receivedTokens = new ArrayList<>();
    private final List<String> receivedContentTypes = new ArrayList<>();
    private final List<String> receivedBodies = new ArrayList<>();
    private int status = 200;
    private String responseBody = "{\"success\":true,\"data\":{"
            + "\"questionText\":\"Do you agree?\",\"essayText\":\"I think so.\","
            + "\"pages\":[{\"category\":\"question\",\"text\":\"Do you agree?\",\"confidence\":93}],"
            + "\"questionConfidence\":93,\"essayConfidence\":88}}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PATH, exchange -> {
            receivedTokens.add(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            receivedContentTypes.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            receivedBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private EnglishEssayAiAdminClient client(String token) {
        return new EnglishEssayAiAdminClient("http://127.0.0.1:" + server.getAddress().getPort(), token, 5, 5);
    }

    private static EnglishEssayAiAdminClient.OcrImage image(String fileName, String mime, String content) {
        return new EnglishEssayAiAdminClient.OcrImage(fileName, mime, content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("OCR: 決められた送り先へ multipart（level / categories / files）を送り、合言葉をヘッダに付ける")
    void postsMultipartWithToken() {
        JsonNode data = client("it-internal-token").recognize("PRE1", "question,answer",
                List.of(image("q.png", "image/png", "question-bytes"),
                        image("a.png", "image/jpeg", "answer-bytes")));

        assertThat(receivedTokens).containsExactly("it-internal-token");
        assertThat(receivedContentTypes).hasSize(1);
        assertThat(receivedContentTypes.get(0)).startsWith("multipart/form-data; boundary=");
        assertThat(receivedBodies).hasSize(1);
        String body = receivedBodies.get(0);
        // 区切り（boundary）は**ヘッダの宣言と本文で同じ**で、本文は仕様どおり CRLF で組んである
        // （Spring の multipart 解析はここが崩れると**どの部品も読めない**）
        String boundary = receivedContentTypes.get(0).substring("multipart/form-data; boundary=".length());
        assertThat(body).startsWith("--" + boundary + "\r\n");
        assertThat(body).endsWith("--" + boundary + "--\r\n");
        assertThat(body).contains("\r\n\r\n");
        // **admin-api の入口と同じパラメータ名**（level / categories / files）
        assertThat(body).contains("name=\"level\"").contains("PRE1");
        assertThat(body).contains("name=\"categories\"").contains("question,answer");
        assertThat(body).contains("name=\"files\"").contains("filename=\"q.png\"");
        assertThat(body).contains("filename=\"a.png\"");
        // 画像の**実体**が載っている（選択しただけで送っていない、を防ぐ）
        assertThat(body).contains("question-bytes").contains("answer-bytes");
        assertThat(body).contains("image/png").contains("image/jpeg");

        // 応答の data をそのまま返す
        assertThat(data.get("questionText").asText()).isEqualTo("Do you agree?");
        assertThat(data.get("essayText").asText()).isEqualTo("I think so.");
        assertThat(data.get("pages").get(0).get("category").asText()).isEqualTo("question");
        assertThat(data.get("pages").get(0).get("confidence").asInt()).isEqualTo(93);
        assertThat(data.get("questionConfidence").asInt()).isEqualTo(93);
        assertThat(data.get("essayConfidence").asInt()).isEqualTo(88);
        // 合言葉そのものは応答に載せない
        assertThat(data.toString()).doesNotContain("it-internal-token");
    }

    @Test
    @DisplayName("OCR: categories が無いときは区分を送らない（既定は admin-api が決める）")
    void omitsCategoriesWhenBlank() {
        client("it-internal-token").recognize("PRE1", null, List.of(image("q.png", "image/png", "q")));

        assertThat(receivedBodies.get(0)).contains("name=\"level\"").doesNotContain("name=\"categories\"");
    }

    @Test
    @DisplayName("OCR: 合言葉が未設定なら呼ばずに失敗する（匿名で叩きに行かない）")
    void doesNotCallWithoutToken() {
        assertThatThrownBy(() -> client("").recognize("PRE1", null, List.of(image("q.png", "image/png", "q"))))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessageContaining("STUDY21_INTERNAL_TOKEN");
        assertThatThrownBy(() -> client(null).recognize("PRE1", null, List.of(image("q.png", "image/png", "q"))))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class);

        assertThat(receivedTokens).as("HTTP を出していない").isEmpty();
    }

    @Test
    @DisplayName("OCR: 401/403 は「許可されていない」として日本語で失敗する")
    void reportsForbidden() {
        for (int forbidden : new int[] { 401, 403 }) {
            status = forbidden;
            assertThatThrownBy(() -> client("it-internal-token").recognize("PRE1", null,
                    List.of(image("q.png", "image/png", "q"))))
                    .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                    .hasMessageContaining("許可されていません");
        }
    }

    @Test
    @DisplayName("OCR: 400 のときは admin-api の日本語の理由をそのまま伝える（でっち上げない）")
    void keepsUpstreamReason() {
        status = 400;
        responseBody = "{\"success\":false,\"message\":\"画像の区分は question / answer のいずれかです: memo\"}";

        assertThatThrownBy(() -> client("it-internal-token").recognize("PRE1", "memo",
                List.of(image("q.png", "image/png", "q"))))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessage("画像の区分は question / answer のいずれかです: memo");
    }

    @Test
    @DisplayName("OCR: 200 でも success=false は成功と見なさない（理由があれば日本語で伝える）")
    void treatsFailureResponseAsFailure() {
        responseBody = "{\"success\":false,\"message\":\"英作文AI添削が無効になっています。\"}";

        assertThatThrownBy(() -> client("it-internal-token").recognize("PRE1", null,
                List.of(image("q.png", "image/png", "q"))))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessage("英作文AI添削が無効になっています。");
    }

    @Test
    @DisplayName("OCR: 応答が読めないときは「確認できませんでした」と言い、成功と言わない")
    void reportsUnreadableResponse() {
        responseBody = "not-json";

        assertThatThrownBy(() -> client("it-internal-token").recognize("PRE1", null,
                List.of(image("q.png", "image/png", "q"))))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessageContaining("確認できませんでした");
    }
}

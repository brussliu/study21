package com.study21.user.english;

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
 * {@link EnglishEssayAiAdminClient} の**添削の受付の契約**（送り先・ボディ・合言葉・失敗の伝え方）の検証。
 * （同期 OCR は {@link EnglishEssayOcrAdminClientTest} で確かめる。）
 *
 * <p>本物の HTTP（その場で立てた小さなサーバー）で確かめる。Mockito で HttpClient を差し替えると
 * **ヘッダの付け忘れ**（＝合言葉が無いまま匿名で叩く）を見つけられない。</p>
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>送り先は `POST /api/admin/batch/english-essay/gradings`、ボディは `{essayId, round?}`</li>
 *   <li>`X-Internal-Token` が付く（未設定なら**呼ばずに**失敗。匿名で叩きに行かない）</li>
 *   <li>`round` を省略したら**送らない**（次の回は admin-api が決める。採番の規則を 2 か所に置かない）</li>
 *   <li>401/403・4xx/5xx・`success=false`・繋がらない・形が違う は**日本語の理由**つきで失敗</li>
 * </ol>
 */
class EnglishEssayAiAdminClientTest {

    private static final String PATH = "/api/admin/batch/english-essay/gradings";

    private HttpServer server;
    private final List<String> receivedTokens = new ArrayList<>();
    private final List<String> receivedBodies = new ArrayList<>();
    private int status = 200;
    private String responseBody = "{\"success\":true,\"data\":{\"gradingId\":501,\"round\":2,"
            + "\"message\":\"英検基準AI添削を受付けました（第 2 回）。バックグラウンドで処理します。\"}}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(PATH, exchange -> {
            receivedTokens.add(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
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

    @Test
    @DisplayName("受付: 決められた送り先へ、essayId と round を送り、合言葉をヘッダに付ける")
    void postsToInternalEntranceWithToken() {
        EnglishEssayAiAdminClient.Accepted accepted = client("it-internal-token").acceptGrading(900001L, 2);

        assertThat(receivedTokens).containsExactly("it-internal-token");
        assertThat(receivedBodies).hasSize(1);
        assertThat(receivedBodies.get(0)).contains("\"essayId\":900001").contains("\"round\":2");
        assertThat(accepted.gradingId()).isEqualTo(501L);
        assertThat(accepted.round()).isEqualTo(2);
        assertThat(accepted.message()).contains("受付けました");
        // 合言葉そのものは応答に載せない（この返り値には入りようがない）
        assertThat(accepted.toString()).doesNotContain("it-internal-token");
    }

    @Test
    @DisplayName("受付: round を省略したら送らない（次の回は admin-api が決める）")
    void omitsRoundWhenNotSpecified() {
        responseBody = "{\"success\":true,\"data\":{\"gradingId\":502,\"round\":3,"
                + "\"message\":\"英検基準AI添削を受付けました（第 3 回）。\"}}";

        EnglishEssayAiAdminClient.Accepted accepted = client("it-internal-token").acceptGrading(900001L, null);

        assertThat(receivedBodies.get(0)).contains("\"essayId\":900001").doesNotContain("round");
        // 何回目かは admin-api の応答から読む（こちらで番号を作らない）
        assertThat(accepted.round()).isEqualTo(3);
    }

    @Test
    @DisplayName("合言葉が未設定なら呼ばずに失敗する（匿名で叩きに行かない）")
    void doesNotCallWithoutToken() {
        assertThatThrownBy(() -> client("").acceptGrading(1L, null))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessageContaining("STUDY21_INTERNAL_TOKEN");
        assertThatThrownBy(() -> client(null).acceptGrading(1L, null))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class);

        assertThat(receivedTokens).as("HTTP を出していない").isEmpty();
    }

    @Test
    @DisplayName("401/403 は「許可されていない」として日本語で失敗する")
    void reportsForbidden() {
        for (int forbidden : new int[] { 401, 403 }) {
            status = forbidden;
            assertThatThrownBy(() -> client("it-internal-token").acceptGrading(1L, null))
                    .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                    .hasMessageContaining("許可されていません");
        }
    }

    @Test
    @DisplayName("400 のときは admin-api の日本語の理由をそのまま伝える（でっち上げない）")
    void keepsUpstreamReason() {
        status = 400;
        responseBody = "{\"success\":false,\"message\":\"設問文と作文本文を先に確定してください。\"}";

        assertThatThrownBy(() -> client("it-internal-token").acceptGrading(1L, null))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessage("設問文と作文本文を先に確定してください。");
    }

    @Test
    @DisplayName("200 でも success=false は成功と見なさない（理由があれば日本語で伝える）")
    void treatsFailureResponseAsFailure() {
        responseBody = "{\"success\":false,\"message\":\"前の添削が終わっていません。\"}";

        assertThatThrownBy(() -> client("it-internal-token").acceptGrading(1L, null))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessage("前の添削が終わっていません。");
    }

    @Test
    @DisplayName("繋がらない・応答が読めないときは「確認できませんでした」と言い、成功と言わない")
    void reportsUnreachable() {
        // 壊れた JSON（例外の中身を画面へ漏らさない）
        responseBody = "not-json";
        assertThatThrownBy(() -> client("token").acceptGrading(1L, null))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessageContaining("確認できませんでした");

        // 繋がらない
        int port = server.getAddress().getPort();
        EnglishEssayAiAdminClient broken = new EnglishEssayAiAdminClient("http://127.0.0.1:" + port, "token", 1, 1);
        server.stop(0);

        assertThatThrownBy(() -> broken.acceptGrading(1L, null))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessageContaining("確認できませんでした");
    }

    @Test
    @DisplayName("gradingId が無い応答は受付を確認できない（成功と言わない）")
    void rejectsResponseWithoutGradingId() {
        responseBody = "{\"success\":true,\"data\":{\"round\":2}}";

        assertThatThrownBy(() -> client("token").acceptGrading(1L, null))
                .isInstanceOf(EnglishEssayAiAdminClient.EnglishEssayCallException.class)
                .hasMessageContaining("確認できませんでした");
    }
}

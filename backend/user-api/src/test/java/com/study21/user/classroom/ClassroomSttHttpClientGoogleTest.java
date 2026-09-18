package com.study21.user.classroom;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ClassroomSttHttpClient} の Google（provider=google）分岐の検証（**外部ネットワークに出ない**）。
 *
 * <p>エラー経路（URL 無し / API Key 無し / 非対応エンコード / 4xx / 5xx / タイムアウト）と
 * 成功経路を、ループバックの {@code com.sun.net.httpserver.HttpServer} で固定する。</p>
 */
class ClassroomSttHttpClientGoogleTest {

    private final ClassroomSttHttpClient client = new ClassroomSttHttpClient(10);

    private static ClassroomSttClient.SttRequest googleRequest(String url, String apiKey, String mime,
                                                               int timeoutSeconds) {
        return new ClassroomSttClient.SttRequest(
                "google", "latest_long", url, apiKey, "ja-JP", null, new byte[]{1, 2, 3}, mime, timeoutSeconds);
    }

    private static HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/speech:recognize", handler);
        server.start();
        return server;
    }

    private static String urlOf(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/speech:recognize";
    }

    private static HttpHandler jsonHandler(int status, String body) {
        return exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        };
    }

    // ------------------------------------------------------------ 設定エラー

    @Test
    void failsWithoutUrl() {
        ClassroomSttClient.SttResponse response =
                client.transcribe(googleRequest(null, "key", "audio/webm", 60));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
        assertThat(response.errorMessage()).contains("URL");
    }

    @Test
    void failsWithoutApiKey() {
        ClassroomSttClient.SttResponse response =
                client.transcribe(googleRequest("https://speech.googleapis.com/v1/speech:recognize", null,
                        "audio/webm", 60));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
        assertThat(response.errorMessage()).contains("API Key");
    }

    @Test
    void failsForUnsupportedEncoding() {
        ClassroomSttClient.SttResponse response =
                client.transcribe(googleRequest("https://speech.googleapis.com/v1/speech:recognize", "key",
                        "audio/mp4", 60));
        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
        assertThat(response.errorMessage()).contains("Google Speech-to-Text");
    }

    // ------------------------------------------------------------ HTTP ステータス

    @Test
    void maps4xxToCheckApiKeyMessage() throws Exception {
        HttpServer server = startServer(jsonHandler(400, "{\"error\":{\"message\":\"bad key\"}}"));
        try {
            ClassroomSttClient.SttResponse response =
                    client.transcribe(googleRequest(urlOf(server), "key", "audio/webm", 60));
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
            assertThat(response.errorMessage()).contains("API Key とモデル名");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void maps5xxToFailure() throws Exception {
        HttpServer server = startServer(jsonHandler(500, "{}"));
        try {
            ClassroomSttClient.SttResponse response =
                    client.transcribe(googleRequest(urlOf(server), "key", "audio/webm", 60));
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.errorCode()).isEqualTo("HTTP_5XX");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void parsesGoogleSuccessResponse() throws Exception {
        HttpServer server = startServer(jsonHandler(200,
                "{\"results\":[{\"alternatives\":[{\"transcript\":\"こんにちは\",\"confidence\":0.99}]}]}"));
        try {
            ClassroomSttClient.SttResponse response =
                    client.transcribe(googleRequest(urlOf(server), "key", "audio/webm", 60));
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.segments()).hasSize(1);
            assertThat(response.segments().get(0).text()).isEqualTo("こんにちは");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void mapsTimeoutToJapaneseMessage() throws Exception {
        // 応答しないハンドラ（クライアントは 5 秒でタイムアウトする）
        HttpServer server = startServer(exchange -> {
            try {
                Thread.sleep(15_000);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }
        });
        try {
            ClassroomSttClient.SttResponse response =
                    client.transcribe(googleRequest(urlOf(server), "key", "audio/webm", 1));
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.errorCode()).isEqualTo("TIMEOUT");
            assertThat(response.errorMessage()).contains("時間内に返りませんでした");
        } finally {
            server.stop(0);
        }
    }
}

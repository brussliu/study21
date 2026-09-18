package com.study21.admin.ai;

import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.stt.DashScopeAsrClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 音声認識（STT）の【接続テスト】の検証（**外部ネットワークに出ない**）。
 *
 * <p>Google はループバックの HTTP サーバー、Alibaba は偽の WebSocket（{@link DashScopeAsrClient.Connector}）で
 * 「無音を 1 回送る」経路と、エラー時の日本語メッセージ・設定不備の 400 を固定する。</p>
 */
class SttConnectionTesterTest {

    private static HttpServer startServer(HttpHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v1/speech:recognize", handler);
        server.start();
        return server;
    }

    private static String urlOf(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1/speech:recognize";
    }

    private static HttpHandler jsonHandler(int status, String body, AtomicReference<String> captured) {
        return (HttpExchange exchange) -> {
            captured.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        };
    }

    /** Alibaba 用の偽 DashScope（成功／失敗を決める）。 */
    private static SttConnectionTester alibabaTester(DashScopeAsrClient.Result result,
                                                     AtomicReference<DashScopeAsrClient.Request> captured) {
        return new SttConnectionTester(new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            throw new UnsupportedOperationException("このテストでは接続しない");
        }) {
            @Override
            public Result transcribe(Request request) {
                captured.set(request);
                return result;
            }
        });
    }

    @Test
    void Googleは無音をLINEAR16で送り成功を返す() throws Exception {
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = startServer(jsonHandler(200, "{}", body));
        try {
            SttConnectionTester tester = new SttConnectionTester();
            Map<String, Object> result = tester.test("google", "latest_long", urlOf(server), "google-key");

            assertThat(result.get("ok")).isEqualTo(true);
            assertThat(result.get("provider")).isEqualTo("google");
            assertThat(result.get("model")).isEqualTo("latest_long");
            assertThat((String) result.get("message")).contains("接続できました");
            // 送る中身: LINEAR16 の無音（ヘッダが無いので sampleRateHertz が要る）
            assertThat(body.get()).contains("\"encoding\":\"LINEAR16\"");
            assertThat(body.get()).contains("\"sampleRateHertz\":16000");
            assertThat(body.get()).contains("\"model\":\"latest_long\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void Googleの4xxは理由つきで失敗にする() throws Exception {
        HttpServer server = startServer(jsonHandler(400,
                "{\"error\":{\"code\":400,\"message\":\"API key not valid. Please pass a valid API key.\"}}",
                new AtomicReference<>()));
        try {
            Map<String, Object> result =
                    new SttConnectionTester().test("google", "latest_long", urlOf(server), "bad-key");

            assertThat(result.get("ok")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("API key not valid");
            assertThat((String) result.get("message")).contains("HTTP 400");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void Googleの5xxは再試行の余地がある失敗として返す() throws Exception {
        HttpServer server = startServer(jsonHandler(503, "{}", new AtomicReference<>()));
        try {
            Map<String, Object> result =
                    new SttConnectionTester().test("google", "latest_long", urlOf(server), "key");

            assertThat(result.get("ok")).isEqualTo(false);
            assertThat((String) result.get("message")).contains("HTTP 503");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void Alibabaは無音をpcmで送り成功を返す() {
        AtomicReference<DashScopeAsrClient.Request> sent = new AtomicReference<>();
        SttConnectionTester tester = alibabaTester(DashScopeAsrClient.Result.success(""), sent);

        Map<String, Object> result = tester.test("alibaba", "paraformer-realtime-v2",
                "wss://dashscope.aliyuncs.com/api-ws/v1/inference", "dashscope-key");

        assertThat(result.get("ok")).isEqualTo(true);
        assertThat(result.get("model")).isEqualTo("paraformer-realtime-v2");
        // 認識と同じ経路（WebSocket）へ、pcm 16kHz の無音を送る
        assertThat(sent.get().format()).isEqualTo("pcm");
        assertThat(sent.get().sampleRate()).isEqualTo(16000);
        assertThat(sent.get().apiKey()).isEqualTo("dashscope-key");
        assertThat(sent.get().audio()).hasSize(6400);
    }

    @Test
    void Alibabaの失敗は理由つきで返す() {
        SttConnectionTester tester = alibabaTester(
                DashScopeAsrClient.Result.failure("TASK_FAILED", "音声認識が失敗しました（InvalidApiKey）: Invalid API-key provided。"),
                new AtomicReference<>());

        Map<String, Object> result = tester.test("alibaba", "paraformer-realtime-v2",
                "wss://dashscope.aliyuncs.com/api-ws/v1/inference", "bad-key");

        assertThat(result.get("ok")).isEqualTo(false);
        assertThat((String) result.get("message")).contains("Invalid API-key provided");
    }

    @Test
    void モデルとURLが空なら既定値を使う() {
        AtomicReference<DashScopeAsrClient.Request> sent = new AtomicReference<>();
        SttConnectionTester tester = alibabaTester(DashScopeAsrClient.Result.success(""), sent);

        Map<String, Object> result = tester.test("alibaba", "  ", null, "key");

        assertThat(result.get("model")).isEqualTo("paraformer-realtime-v2");
        assertThat(sent.get().url()).isEqualTo("wss://dashscope.aliyuncs.com/api-ws/v1/inference");
    }

    @Test
    void 未対応のプロバイダーとAPIキー無しは400() {
        SttConnectionTester tester = new SttConnectionTester();

        assertThatThrownBy(() -> tester.test("whisper", "whisper-1", "https://example.com", "key"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("接続テストに対応していません");
        assertThatThrownBy(() -> tester.test("google", "latest_long", "https://example.com", "  "))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("API Key");
    }
}

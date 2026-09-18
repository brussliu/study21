package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LangChain4j 実装（{@link GeometryAiLangChain4jClient}）の接縫。
 *
 * <p>外へは出ない。**JDK 内蔵の {@code com.sun.net.httpserver.HttpServer} で OpenAI 互換の
 * 偽エンドポイントを立て**、送信内容（URL・ヘッダ・本文）と、返ってくる
 * {@link GeometryAiClient.AiResponse} の形だけを確かめる（設計 §9.4 の「AI を実呼び出ししない工夫」）。
 * 偽サーバーの応答は本番と同じ OpenAI 互換の本文にして、下流の抽出経路もそのまま通す。</p>
 *
 * <p>確かめること:</p>
 * <ul>
 *   <li>設定の URL（{@code .../v1/chat/completions}）から baseUrl を割り出して
 *       {@code /chat/completions} を 1 回だけ POST する（Bearer・model・temperature・max_tokens）</li>
 *   <li>返す本文は既存の抽出（{@code choices[0].message.content}）がそのまま読める</li>
 *   <li>既存の {@code GeometryAiHttpClient} と同じエラーコードへ落とす
 *       （4xx / 429 / 5xx / タイムアウト / 接続不可）</li>
 * </ul>
 */
class GeometryAiLangChain4jClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private HttpServer server;
    private String baseUrl;
    private final List<Recorded> requests = new ArrayList<>();

    /** 偽サーバーが返す HTTP ステータス。 */
    private volatile int status = 200;
    /** 偽サーバーが返す本文。 */
    private volatile String body = openAiBody("説明: 垂線を引きます。\nD = (0, 0)\nSegment(C, D)");
    /** 応答までの待ち（タイムアウトの試験用）。 */
    private volatile long delayMs = 0;

    private GeometryAiLangChain4jClient client;

    /** 試験中に偽サーバーを止めたか（@AfterEach の二重 stop を避ける）。 */
    private boolean stopped;

    @BeforeEach
    void startFakeOpenAi() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", this::handle);
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        client = new GeometryAiLangChain4jClient();
    }

    @AfterEach
    void stopFakeOpenAi() {
        if (!stopped) {
            server.stop(0);
        }
    }

    @Test
    void postsToChatCompletionsAndReturnsTheBodyTheParsersCanRead() {
        GeometryAiClient.AiResponse response = client.call(textRequest(60));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.httpStatus()).isEqualTo(200);
        // 既存の抽出経路（batC52 の応答解析）がそのまま読めること
        assertThat(AiResponseDtoParser.assistantContent(response.body()))
                .contains("D = (0, 0)");

        assertThat(requests).hasSize(1);
        Recorded sent = requests.get(0);
        assertThat(sent.method()).isEqualTo("POST");
        assertThat(sent.path()).isEqualTo("/v1/chat/completions");
        assertThat(sent.authorization()).isEqualTo("Bearer secret-key");
        assertThat(sent.contentType()).startsWith("application/json");

        JsonNode payload = readTree(sent.body());
        assertThat(payload.path("model").asText()).isEqualTo("qwen3-vl-plus");
        assertThat(payload.path("temperature").asDouble()).isEqualTo(0.2);
        assertThat(payload.path("max_tokens").asInt()).isEqualTo(2000);
        assertThat(payload.path("messages").size()).isEqualTo(2);
        assertThat(payload.path("messages").path(0).path("role").asText()).isEqualTo("system");
        assertThat(payload.path("messages").path(0).path("content").asText())
                .isEqualTo("あなたは作図アシスタントです。");
        assertThat(payload.path("messages").path(1).path("role").asText()).isEqualTo("user");
        assertThat(payload.path("messages").path(1).path("content").asText())
                .isEqualTo("指示: 垂線を引いて");
    }

    @Test
    void sendsImageAsTheSameContentArrayAsBefore() {
        byte[] image = {1, 2, 3, 4};
        GeometryAiClient.AiResponse response = client.call(new GeometryAiClient.AiRequest(
                "qwen", "qwen3-vl-plus", baseUrl + "/v1/chat/completions", "secret-key",
                "あなたは作図アシスタントです。", "画像から作図して",
                image, "image/png", 60, 0.2, 2000, false));

        assertThat(response.isSuccess()).isTrue();
        JsonNode content = readTree(requests.get(0).body()).path("messages").path(1).path("content");
        assertThat(content.isArray()).isTrue();
        assertThat(content.path(0).path("type").asText()).isEqualTo("text");
        assertThat(content.path(0).path("text").asText()).isEqualTo("画像から作図して");
        assertThat(content.path(1).path("type").asText()).isEqualTo("image_url");
        assertThat(content.path(1).path("image_url").path("url").asText())
                .isEqualTo("data:image/png;base64," + Base64.getEncoder().encodeToString(image));
    }

    @Test
    void mapsBadRequestToHttp4xx() {
        status = 401;
        body = errorBody("Invalid API key provided", "invalid_api_key");

        GeometryAiClient.AiResponse response = client.call(textRequest(60));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
        assertThat(response.httpStatus()).isEqualTo(401);
        assertThat(response.errorMessage()).contains("HTTP 401").contains("API Key");
    }

    @Test
    void mapsRateLimitToHttp429() {
        status = 429;
        body = errorBody("Rate limit reached", "rate_limit_exceeded");

        GeometryAiClient.AiResponse response = client.call(textRequest(60));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_429");
        assertThat(response.httpStatus()).isEqualTo(429);
    }

    @Test
    void mapsServerErrorToHttp5xx() {
        status = 500;
        body = errorBody("internal error", "server_error");

        GeometryAiClient.AiResponse response = client.call(textRequest(60));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_5XX");
        assertThat(response.httpStatus()).isEqualTo(500);
    }

    @Test
    void mapsTimeoutToTimeout() {
        delayMs = 3000;

        GeometryAiClient.AiResponse response = client.call(textRequest(1));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("TIMEOUT");
        assertThat(response.errorMessage()).contains("1 秒");
    }

    @Test
    void mapsUnreachableServerToHttp5xx() {
        int port = server.getAddress().getPort();
        server.stop(0);
        stopped = true;

        GeometryAiClient.AiResponse response = client.call(new GeometryAiClient.AiRequest(
                "qwen", "qwen3-vl-plus", "http://127.0.0.1:" + port + "/v1/chat/completions",
                "secret-key", "あなたは作図アシスタントです。", "指示: 垂線を引いて",
                null, null, 60, 0.2, 2000, false));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_5XX");
        assertThat(response.errorMessage()).contains("接続");
    }

    @Test
    void failsWithoutCallingAiWhenApiKeyIsMissing() {
        GeometryAiClient.AiResponse response = client.call(new GeometryAiClient.AiRequest(
                "qwen", "qwen3-vl-plus", baseUrl + "/v1/chat/completions", "  ",
                "あなたは作図アシスタントです。", "指示: 垂線を引いて",
                null, null, 60, 0.2, 2000, false));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
        assertThat(response.errorMessage()).contains("API Key");
        assertThat(requests).isEmpty();
    }

    @Test
    void failsWithoutCallingAiWhenUrlIsMissing() {
        GeometryAiClient.AiResponse response = client.call(new GeometryAiClient.AiRequest(
                "qwen", "qwen3-vl-plus", "   ", "secret-key",
                "あなたは作図アシスタントです。", "指示: 垂線を引いて",
                null, null, 60, 0.2, 2000, false));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
        assertThat(response.errorMessage()).contains("URL");
        assertThat(requests).isEmpty();
    }

    @Test
    void returnsUsageInTheOpenAiCompatibleBody() {
        GeometryAiClient.AiResponse response = client.call(textRequest(60));

        JsonNode usage = readTree(response.body()).path("usage");
        assertThat(usage.path("prompt_tokens").asInt()).isEqualTo(120);
        assertThat(usage.path("completion_tokens").asInt()).isEqualTo(40);
        assertThat(usage.path("total_tokens").asInt()).isEqualTo(160);
    }

    @Test
    void stripsTheChatCompletionsSuffixFromTheConfiguredUrl() {
        // 設定に入っている実際の URL（4 プロバイダ）
        assertThat(GeometryAiLangChain4jClient.baseUrlOf(
                "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"))
                .isEqualTo("https://dashscope.aliyuncs.com/compatible-mode/v1");
        assertThat(GeometryAiLangChain4jClient.baseUrlOf(
                "https://ark.cn-beijing.volces.com/api/v3/chat/completions"))
                .isEqualTo("https://ark.cn-beijing.volces.com/api/v3");
        assertThat(GeometryAiLangChain4jClient.baseUrlOf("https://api.deepseek.com/v1/chat/completions"))
                .isEqualTo("https://api.deepseek.com/v1");
        assertThat(GeometryAiLangChain4jClient.baseUrlOf("https://api.openai.com/v1/chat/completions/"))
                .isEqualTo("https://api.openai.com/v1");
        // baseUrl を直接入れた場合はそのまま
        assertThat(GeometryAiLangChain4jClient.baseUrlOf(" https://api.openai.com/v1 "))
                .isEqualTo("https://api.openai.com/v1");
    }

    @Test
    void mapsInterruptToInterrupted() {
        Thread.currentThread().interrupt();
        try {
            GeometryAiClient.AiResponse response = client.call(textRequest(60));

            assertThat(response.isSuccess()).isFalse();
            assertThat(response.errorCode()).isEqualTo("INTERRUPTED");
        } finally {
            // 割り込みフラグを後続のテストへ持ち越さない
            Thread.interrupted();
        }
    }

    @Test
    void asksForJsonOutputOnlyWhenTheCallerWantsIt() {
        // batC52（画図助手）は JSON を要求しない
        client.call(textRequest(60));
        assertThat(readTree(requests.get(0).body()).has("response_format")).isFalse();

        // batC51（AI 生図）は GEOMETRY_AI_OUTPUT_FORMAT=JSON のとき response_format を付ける
        GeometryAiClient.AiResponse response = client.call(new GeometryAiClient.AiRequest(
                "qwen", "qwen3-vl-plus", baseUrl + "/v1/chat/completions", "secret-key",
                "あなたは作図アシスタントです。", "画像から作図して",
                null, null, 60, 0.2, 2000, true));

        assertThat(response.isSuccess()).isTrue();
        assertThat(readTree(requests.get(1).body()).path("response_format").path("type").asText())
                .isEqualTo("json_object");
    }

    @Test
    void sendsAnEmptyTextWhenThePromptIsMissing() {
        GeometryAiClient.AiResponse response = client.call(new GeometryAiClient.AiRequest(
                "qwen", "qwen3-vl-plus", baseUrl + "/v1/chat/completions", "secret-key",
                null, null, null, null, 60, 0.2, 2000, false));

        assertThat(response.isSuccess()).isTrue();
        JsonNode payload = readTree(requests.get(0).body());
        // system プロンプトが無いときは user だけを送る
        assertThat(payload.path("messages").size()).isEqualTo(1);
        assertThat(payload.path("messages").path(0).path("role").asText()).isEqualTo("user");
        assertThat(payload.path("messages").path(0).path("content").asText()).isEmpty();
    }

    @Test
    void mapsAnInvalidUrlToHttp4xxWithoutCallingAi() {
        GeometryAiClient.AiResponse response = client.call(new GeometryAiClient.AiRequest(
                "qwen", "qwen3-vl-plus", "これは URL ではない/chat/completions", "secret-key",
                "あなたは作図アシスタントです。", "指示: 垂線を引いて",
                null, null, 60, 0.2, 2000, false));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
        assertThat(response.errorMessage()).contains("URL");
        assertThat(requests).isEmpty();
    }

    /** 偽サーバー（OpenAI 互換）。送信内容を記録してから、決めた応答を返す。 */
    private void handle(HttpExchange exchange) throws IOException {
        try {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            requests.add(new Recorded(
                    exchange.getRequestMethod(),
                    exchange.getRequestURI().getPath(),
                    exchange.getRequestHeaders().getFirst("Authorization"),
                    exchange.getRequestHeaders().getFirst("Content-Type"),
                    requestBody));
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
        } catch (IOException cause) {
            // クライアントがタイムアウトで切った後（想定内）
        } finally {
            exchange.close();
        }
    }

    /** batC52 と同じ形の要求（テキストだけ・画像なし）。 */
    private GeometryAiClient.AiRequest textRequest(int timeoutSeconds) {
        return new GeometryAiClient.AiRequest(
                "qwen", "qwen3-vl-plus", baseUrl + "/v1/chat/completions", "secret-key",
                "あなたは作図アシスタントです。", "指示: 垂線を引いて",
                null, null, timeoutSeconds, 0.2, 2000, false);
    }

    private static String openAiBody(String content) {
        return "{\"choices\":[{\"message\":{\"content\":" + quote(content) + "}}],"
                + "\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":40,\"total_tokens\":160}}";
    }

    /** OpenAI 互換のエラー本文（4xx / 5xx のとき）。 */
    private static String errorBody(String message, String code) {
        return "{\"error\":{\"message\":" + quote(message) + ",\"type\":\"invalid_request_error\","
                + "\"code\":" + quote(code) + "}}";
    }

    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                default -> builder.append(c);
            }
        }
        return builder.append('"').toString();
    }

    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (IOException cause) {
            throw new AssertionError("JSON として読めません: " + json, cause);
        }
    }

    /** 偽サーバーが受け取った 1 リクエスト。 */
    private record Recorded(String method, String path, String authorization, String contentType, String body) {
    }
}

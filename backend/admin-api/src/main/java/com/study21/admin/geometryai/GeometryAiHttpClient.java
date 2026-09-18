package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;

/**
 * OpenAI 互換の `chat/completions` を `java.net.http.HttpClient` で叩く実装（AI 生図）。
 *
 * <p>既存の 4 プロバイダ（qwen / doubao / deepseek / chatgpt）はすべて同じ形の URL が設定済みなので
 * 1 実装で足りる。画像は `content` の配列で送る
 * （`[{"type":"text",...},{"type":"image_url","image_url":{"url":"data:...;base64,..."}}]`）。</p>
 *
 * <p>**画像の base64 をアプリログにも `BAT_AI呼出履歴情報.プロンプト` にも入れない**
 * （呼出履歴は既に 405MB ある。設計 §7）。</p>
 *
 * <p>**いまは既定ではない**（切り戻し用）。AI 呼び出しの既定は LangChain4j の
 * {@link GeometryAiLangChain4jClient} で、`study21.geometry-ai.client=http` を設定したときだけ
 * こちらが使われる（Bean の生成は {@link GeometryAiClientConfiguration}）。</p>
 *
 * <p>`study21.geometry-ai.stub=true` のときはどちらの実呼び出し実装も作らず、固定応答の
 * {@link GeometryAiStubClient} を使う（E2E が外部ネットワークに依存しないため）。</p>
 */
public class GeometryAiHttpClient implements GeometryAiClient {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiHttpClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 接続タイムアウトは {@link GeometryAiClientConfiguration} が
     *  `study21.geometry-ai.connect-timeout-seconds` から渡す。 */
    public GeometryAiHttpClient(int connectTimeoutSeconds) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public AiResponse call(AiRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return AiResponse.failure(0, "HTTP_4XX", "AI の URL が設定されていません（AIモデル設定）。");
        }
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            return AiResponse.failure(0, "HTTP_4XX", "AI の API Key が設定されていません（AIモデル設定）。");
        }

        String payload;
        try {
            payload = buildPayload(request);
        } catch (IOException cause) {
            return AiResponse.failure(0, "INVALID_REQUEST", "AI への送信内容を作れませんでした。");
        }

        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(URI.create(request.url()))
                    .timeout(Duration.ofSeconds(Math.max(5, request.timeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + request.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException cause) {
            log.warn("geometry ai url is invalid. provider={}", request.provider());
            return AiResponse.failure(0, "HTTP_4XX", "AI の URL が正しくありません（AIモデル設定）。");
        }

        long startedAt = System.currentTimeMillis();
        try {
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            log.info("geometry ai call finished. provider={} model={} status={} durationMs={}",
                    request.provider(), request.model(), status, System.currentTimeMillis() - startedAt);
            if (status >= 200 && status < 300) {
                return AiResponse.success(status, response.body());
            }
            if (status >= 400 && status < 500) {
                return AiResponse.failure(status, "HTTP_4XX",
                        "AI がリクエストを受け付けませんでした（HTTP " + status + "）。API Key とモデル名を確認してください。");
            }
            return AiResponse.failure(status, status == 429 ? "HTTP_429" : "HTTP_5XX",
                    "AI の呼び出しに失敗しました（HTTP " + status + "）。");
        } catch (java.net.http.HttpTimeoutException cause) {
            log.warn("geometry ai call timed out. provider={} timeoutSeconds={}",
                    request.provider(), request.timeoutSeconds());
            return AiResponse.failure(0, "TIMEOUT",
                    "AI の応答が時間内に返りませんでした（" + request.timeoutSeconds() + " 秒）。");
        } catch (IOException cause) {
            log.warn("geometry ai call failed. provider={} message={}", request.provider(), cause.getMessage());
            return AiResponse.failure(0, "HTTP_5XX", "AI へ接続できませんでした。");
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            return AiResponse.failure(0, "INTERRUPTED", "AI の呼び出しが中断されました。");
        }
    }

    private String buildPayload(AiRequest request) throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("model", request.model());
        root.put("temperature", request.temperature());
        root.put("max_tokens", request.maxCompletionTokens());
        if (request.jsonResponse()) {
            // JSON を返せるモデルにだけ付ける（非対応でも 400 にならないものが多い）
            ObjectNode format = root.putObject("response_format");
            format.put("type", "json_object");
        }

        ArrayNode messages = root.putArray("messages");
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", request.systemPrompt());
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        if (request.image() == null || request.image().length == 0) {
            user.put("content", request.userPrompt());
        } else {
            ArrayNode content = user.putArray("content");
            ObjectNode text = content.addObject();
            text.put("type", "text");
            text.put("text", request.userPrompt());
            ObjectNode image = content.addObject();
            image.put("type", "image_url");
            ObjectNode imageUrl = image.putObject("image_url");
            imageUrl.put("url", "data:" + request.imageMime() + ";base64,"
                    + Base64.getEncoder().encodeToString(request.image()));
        }
        return objectMapper.writeValueAsString(root);
    }

    /** 応答から `choices[0].message.content` を取り出す（使うのは呼び出し側）。 */
    public String contentOf(String body) {
        if (body == null || body.isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode content = root.path("choices").path(0).path("message").path("content");
            return content.isMissingNode() || content.isNull() ? null : content.asText();
        } catch (IOException cause) {
            return null;
        }
    }
}

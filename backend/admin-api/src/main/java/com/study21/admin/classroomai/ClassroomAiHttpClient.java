package com.study21.admin.classroomai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * OpenAI 互換の {@code chat/completions} を {@code java.net.http.HttpClient} で叩く実装（授業ノート）。
 *
 * <p>画像は無く、テキストだけを送る。`study21.classroom-ai.stub=true` のときは**この Bean を作らず**、
 * 固定応答の {@link ClassroomAiStubClient} を使う（E2E が外部ネットワークに依存しないため）。</p>
 */
@Component
@ConditionalOnProperty(name = "study21.classroom-ai.stub", havingValue = "false", matchIfMissing = true)
public class ClassroomAiHttpClient implements ClassroomAiClient {

    private static final Logger log = LoggerFactory.getLogger(ClassroomAiHttpClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public ClassroomAiHttpClient(
            @Value("${study21.classroom-ai.connect-timeout-seconds:10}") int connectTimeoutSeconds) {
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
            log.warn("classroom ai url is invalid. provider={}", request.provider());
            return AiResponse.failure(0, "HTTP_4XX", "AI の URL が正しくありません（AIモデル設定）。");
        }

        long startedAt = System.currentTimeMillis();
        try {
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            log.info("classroom ai call finished. provider={} model={} status={} durationMs={}",
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
            return AiResponse.failure(0, "TIMEOUT",
                    "AI の応答が時間内に返りませんでした（" + request.timeoutSeconds() + " 秒）。");
        } catch (IOException cause) {
            log.warn("classroom ai call failed. provider={} message={}", request.provider(), cause.getMessage());
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
        ObjectNode format = root.putObject("response_format");
        format.put("type", "json_object");

        ArrayNode messages = root.putArray("messages");
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            ObjectNode system = messages.addObject();
            system.put("role", "system");
            system.put("content", request.systemPrompt());
        }
        ObjectNode user = messages.addObject();
        user.put("role", "user");
        user.put("content", request.userPrompt());
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

package com.study21.user.classroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.study21.common.core.stt.DashScopeAsrClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/**
 * STT を {@code java.net.http.HttpClient} で叩く実装。
 *
 * <p>provider で実装を分岐する:</p>
 * <ul>
 *   <li>{@code whisper} / {@code azure} / {@code other} … OpenAI 互換の {@code /audio/transcriptions}。
 *       multipart/form-data で音声（{@code file}）と {@code model}・{@code language} を送り、
 *       {@code Authorization: Bearer} を付ける。応答は {@code {"text"}} か {@code {"segments"}}。</li>
 *   <li>{@code google} … Google Cloud Speech-to-Text v1 の {@code speech:recognize}。JSON 本文で
 *       音声を base64 にして送り、API Key は {@code ?key=} に付ける（{@link GoogleSttRequestBuilder} /
 *       {@link GoogleSttResponseParser}）。</li>
 *   <li>{@code alibaba} … 阿里巴巴 DashScope の Paraformer-Realtime-V2。**HTTP ではなく WebSocket**
 *       （`wss://…/api-ws/v1/inference`）なので {@link ClassroomSttAlibabaClient} へ渡す
 *       （プロトコルは common-core の {@code DashScopeAsrClient}）。</li>
 * </ul>
 *
 * <p>`study21.classroom-ai.stub=true` のときは**この Bean を作らず**、固定応答を返す
 * {@link ClassroomSttStubClient} を使う（E2E が外部ネットワークに依存しないため）。</p>
 */
@Component
@ConditionalOnProperty(name = "study21.classroom-ai.stub", havingValue = "false", matchIfMissing = true)
public class ClassroomSttHttpClient implements ClassroomSttClient {

    private static final Logger log = LoggerFactory.getLogger(ClassroomSttHttpClient.class);

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 阿里巴巴（DashScope）の分塊認識。プロトコルは common-core の `DashScopeAsrClient` が持つ。 */
    private final ClassroomSttAlibabaClient alibabaClient;

    @Autowired
    public ClassroomSttHttpClient(
            @Value("${study21.classroom-ai.connect-timeout-seconds:10}") int connectTimeoutSeconds) {
        this(connectTimeoutSeconds, new ClassroomSttAlibabaClient(new DashScopeAsrClient()));
    }

    /** テスト用（Alibaba の呼び出しを差し替える）。 */
    ClassroomSttHttpClient(int connectTimeoutSeconds, ClassroomSttAlibabaClient alibabaClient) {
        this.alibabaClient = alibabaClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(1, connectTimeoutSeconds)))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public SttResponse transcribe(SttRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return SttResponse.failure(0, "HTTP_4XX", "STT の URL が設定されていません。");
        }
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            return SttResponse.failure(0, "HTTP_4XX", "STT の API Key が設定されていません。");
        }
        if ("google".equals(request.provider())) {
            return transcribeGoogle(request);
        }
        if ("alibaba".equals(request.provider())) {
            // WebSocket（run-task → 音声 → finish-task）。URL は wss://… を設定する
            return alibabaClient.transcribe(request);
        }
        return transcribeOpenAiCompatible(request);
    }

    // ------------------------------------------------------------ OpenAI 互換

    private SttResponse transcribeOpenAiCompatible(SttRequest request) {
        byte[] payload;
        try {
            payload = buildMultipart(request);
        } catch (IOException cause) {
            return SttResponse.failure(0, "INVALID_REQUEST", "STT への送信内容を作れませんでした。");
        }

        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(URI.create(request.url()))
                    .timeout(Duration.ofSeconds(Math.max(5, request.timeoutSeconds())))
                    .header("Content-Type", "multipart/form-data; boundary=" + BOUNDARY)
                    .header("Accept", "application/json")
                    .header("Authorization", "Bearer " + request.apiKey())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(payload))
                    .build();
        } catch (IllegalArgumentException cause) {
            log.warn("classroom stt url is invalid. provider={}", request.provider());
            return SttResponse.failure(0, "HTTP_4XX", "STT の URL が正しくありません。設定を確認してください。");
        }
        return send(httpRequest, request, this::parseSegments);
    }

    // ------------------------------------------------------------ Google v1

    private SttResponse transcribeGoogle(SttRequest request) {
        if (GoogleSttRequestBuilder.encodingOf(request.mime()) == null) {
            return SttResponse.failure(0, "HTTP_4XX",
                    GoogleSttRequestBuilder.unsupportedEncodingMessage(request.mime()));
        }
        String payload = GoogleSttRequestBuilder.buildBody(request);

        HttpRequest httpRequest;
        try {
            httpRequest = HttpRequest.newBuilder(GoogleSttRequestBuilder.buildUri(request.url(), request.apiKey()))
                    .timeout(Duration.ofSeconds(Math.max(5, request.timeoutSeconds())))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload, StandardCharsets.UTF_8))
                    .build();
        } catch (IllegalArgumentException cause) {
            log.warn("classroom stt url is invalid. provider={}", request.provider());
            return SttResponse.failure(0, "HTTP_4XX", "STT の URL が正しくありません。設定を確認してください。");
        }
        return send(httpRequest, request, GoogleSttResponseParser::parse);
    }

    // ------------------------------------------------------------ 共通送信

    /** 送信してステータスをマップし、応答本文を parser でセグメントへ変換する。 */
    private SttResponse send(HttpRequest httpRequest, SttRequest request,
                             Function<String, List<Segment>> parser) {
        long startedAt = System.currentTimeMillis();
        try {
            HttpResponse<String> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            log.info("classroom stt call finished. provider={} status={} durationMs={}",
                    request.provider(), status, System.currentTimeMillis() - startedAt);
            if (status >= 200 && status < 300) {
                return SttResponse.success(status, parser.apply(response.body()));
            }
            if (status >= 400 && status < 500) {
                return SttResponse.failure(status, "HTTP_4XX",
                        "STT がリクエストを受け付けませんでした（HTTP " + status + "）。API Key とモデル名を確認してください。");
            }
            return SttResponse.failure(status, status == 429 ? "HTTP_429" : "HTTP_5XX",
                    "STT の呼び出しに失敗しました（HTTP " + status + "）。");
        } catch (java.net.http.HttpTimeoutException cause) {
            return SttResponse.failure(0, "TIMEOUT",
                    "STT の応答が時間内に返りませんでした（" + request.timeoutSeconds() + " 秒）。");
        } catch (IOException cause) {
            log.warn("classroom stt call failed. provider={} message={}", request.provider(), cause.getMessage());
            return SttResponse.failure(0, "HTTP_5XX", "STT へ接続できませんでした。");
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            return SttResponse.failure(0, "INTERRUPTED", "STT の呼び出しが中断されました。");
        }
    }

    // -------------------------------------------------------------------- 内部

    private static final String BOUNDARY = "study21-stt-" + UUID.randomUUID().toString().replace("-", "");

    /** multipart/form-data の本文を組み立てる（OpenAI 互換）。 */
    private byte[] buildMultipart(SttRequest request) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        // file（音声）
        writeFieldHeader(out, "file", "audio." + extensionOf(request.mime()), request.mime());
        out.write(request.audio() == null ? new byte[0] : request.audio());
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
        // model
        writeTextField(out, "model", request.model() == null ? "" : request.model());
        // language（auto / 空なら送らない）
        if (request.languageCode() != null && !request.languageCode().isBlank()
                && !"auto".equals(request.languageCode())) {
            writeTextField(out, "language", request.languageCode());
        }
        out.write(("--" + BOUNDARY + "--\r\n").getBytes(StandardCharsets.UTF_8));
        return out.toByteArray();
    }

    private static void writeTextField(ByteArrayOutputStream out, String name, String value) throws IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFieldHeader(ByteArrayOutputStream out, String name, String fileName, String mime)
            throws IOException {
        out.write(("--" + BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Disposition: form-data; name=\"" + name + "\"; filename=\"" + fileName + "\"\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(("Content-Type: " + (mime == null || mime.isBlank() ? "application/octet-stream" : mime) + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
    }

    private static String extensionOf(String mime) {
        if (mime == null) {
            return "webm";
        }
        return switch (mime.toLowerCase(java.util.Locale.ROOT)) {
            case "audio/ogg", "application/ogg" -> "ogg";
            case "audio/mp4", "video/mp4", "audio/mp3", "audio/mpeg" -> "mp4";
            case "audio/wav", "audio/x-wav" -> "wav";
            default -> "webm";
        };
    }

    /** OpenAI 互換の応答 JSON からセグメントを取り出す。 */
    List<Segment> parseSegments(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(body);
            JsonNode segments = root.path("segments");
            if (segments.isArray()) {
                List<Segment> list = new ArrayList<>();
                for (JsonNode node : segments) {
                    String text = node.path("text").isTextual() ? node.path("text").asText().trim() : null;
                    if (text == null || text.isEmpty()) {
                        continue;
                    }
                    list.add(new Segment(text,
                            node.path("speaker").isTextual() ? node.path("speaker").asText() : null,
                            node.path("language").isTextual() ? node.path("language").asText() : null,
                            node.path("start").isNumber() ? node.path("start").asDouble() : null,
                            node.path("end").isNumber() ? node.path("end").asDouble() : null));
                }
                if (!list.isEmpty()) {
                    return list;
                }
            }
            JsonNode text = root.path("text");
            if (text.isTextual() && !text.asText().isBlank()) {
                return List.of(new Segment(text.asText().trim(), null,
                        root.path("language").isTextual() ? root.path("language").asText() : null, null, null));
            }
            return List.of();
        } catch (IOException cause) {
            log.warn("classroom stt response could not be parsed.");
            return List.of();
        }
    }
}

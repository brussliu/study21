package com.study21.admin.geometryai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.exception.AuthenticationException;
import dev.langchain4j.exception.HttpException;
import dev.langchain4j.exception.InternalServerException;
import dev.langchain4j.exception.InvalidRequestException;
import dev.langchain4j.exception.ModelNotFoundException;
import dev.langchain4j.exception.RateLimitException;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.TokenUsage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * AI（LLM）を **LangChain4j** で呼ぶ実装（{@link GeometryAiClient} の既定）。
 *
 * <p>qwen / doubao / deepseek / chatgpt はすべて OpenAI 互換の {@code chat/completions} なので、
 * {@code langchain4j-open-ai} の {@link OpenAiChatModel} 1 つで足りる。接続先は設定
 * （`AI_MODEL` ページの {@code AI_*_URL} / {@code AI_*_API_KEY} / {@code AI_*_MODEL_n}）から
 * {@link GeometryAiConnectionResolver} が解決した値をそのまま使う（DB の値なので Bean には
 * 固定できない）。モデルは呼び出しごとに組み立てる（設定を変えたら再起動なしで効く）。</p>
 *
 * <p>**返す本文は今までと同じ OpenAI 互換の形**（{@code choices[0].message.content} と
 * {@code usage}）に組み直す。こうすると下流の抽出（{@link GeometryAiAssistResponseParser} など）と
 * `BAT_AI呼出履歴情報.レスポンス` の形が変わらず、移行の影響が AI 呼び出しの中だけに収まる。</p>
 *
 * <p>失敗は従来の実装と同じ**エラーコード**（{@code HTTP_4XX} / {@code HTTP_429} /
 * {@code HTTP_5XX} / {@code TIMEOUT}）へ落とす。再試行の判断（429・5xx は再試行、
 * 4xx は再試行しない＝設計 §6.4）はこのコードを見るバッチ側が持つので、
 * ここでは LangChain4j の再試行（{@code maxRetries}）を切っている。</p>
 *
 * <p>Bean の生成は {@link GeometryAiClientConfiguration} が行う
 * （`study21.geometry-ai.client=langchain4j`（既定） / `http` で従来実装と切り替える）。</p>
 */
public class GeometryAiLangChain4jClient implements GeometryAiClient {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiLangChain4jClient.class);

    /** OpenAI 互換 URL の末尾（これを取り除いた部分が LangChain4j の baseUrl）。 */
    private static final String CHAT_COMPLETIONS_SUFFIX = "/chat/completions";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public AiResponse call(AiRequest request) {
        if (request.url() == null || request.url().isBlank()) {
            return AiResponse.failure(0, "HTTP_4XX", "AI の URL が設定されていません（AIモデル設定）。");
        }
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            return AiResponse.failure(0, "HTTP_4XX", "AI の API Key が設定されていません（AIモデル設定）。");
        }
        if (!isValidUrl(baseUrlOf(request.url()))) {
            // ここで弾かないと、モデルを組み立てる時に例外になって工程ごと落ちる
            log.warn("geometry ai url is invalid. provider={}", request.provider());
            return AiResponse.failure(0, "HTTP_4XX", "AI の URL が正しくありません（AIモデル設定）。");
        }

        ChatModel model = chatModel(request);
        long startedAt = System.currentTimeMillis();
        try {
            ChatResponse response = model.chat(messagesOf(request));
            log.info("geometry ai (langchain4j) call finished. provider={} model={} durationMs={}",
                    request.provider(), request.model(), System.currentTimeMillis() - startedAt);
            return AiResponse.success(200, bodyOf(response));
        } catch (HttpException cause) {
            // ステータス番号をそのまま持つ唯一の例外（未対応の 4xx/5xx はここへ来る）
            return failureOfStatus(cause.statusCode(), request, cause);
        } catch (AuthenticationException cause) {
            // 401 / 403。キー不正は再試行しても直らない
            return clientError(401, request, cause);
        } catch (InvalidRequestException cause) {
            // 400（ContentFilteredException もここに入る）
            return clientError(400, request, cause);
        } catch (ModelNotFoundException cause) {
            // 404。モデル名の指定違い
            return clientError(404, request, cause);
        } catch (RateLimitException cause) {
            return failureOfStatus(429, request, cause);
        } catch (InternalServerException cause) {
            return failureOfStatus(500, request, cause);
        } catch (dev.langchain4j.exception.TimeoutException cause) {
            log.warn("geometry ai call timed out (langchain4j). provider={} timeoutSeconds={}",
                    request.provider(), request.timeoutSeconds());
            return AiResponse.failure(0, "TIMEOUT",
                    "AI の応答が時間内に返りませんでした（" + request.timeoutSeconds() + " 秒）。");
        } catch (RuntimeException cause) {
            if (containsInterrupt(cause)) {
                Thread.currentThread().interrupt();
                return AiResponse.failure(0, "INTERRUPTED", "AI の呼び出しが中断されました。");
            }
            // 接続不可（ConnectException など）。LangChain4j は RuntimeException で包む
            log.warn("geometry ai call failed (langchain4j). provider={} message={}",
                    request.provider(), cause.getMessage());
            return AiResponse.failure(0, "HTTP_5XX", "AI へ接続できませんでした。");
        }
    }

    /** 1 回の呼び出しに使う LangChain4j のモデル（設定値だけで組み立てる）。 */
    private ChatModel chatModel(AiRequest request) {
        OpenAiChatModel.OpenAiChatModelBuilder builder = OpenAiChatModel.builder()
                .baseUrl(baseUrlOf(request.url()))
                .apiKey(request.apiKey())
                .modelName(request.model())
                .temperature(request.temperature())
                .maxTokens(request.maxCompletionTokens())
                .timeout(Duration.ofSeconds(Math.max(1, request.timeoutSeconds())))
                // 再試行はバッチ側（履歴・再試行回数）が持つので、ここでは重ねない
                .maxRetries(0)
                // プロンプトと応答は BAT_AI呼出履歴情報 に残すので、ライブラリのログには出さない
                .logRequests(false)
                .logResponses(false);
        if (request.jsonResponse()) {
            // 従来実装と同じ response_format（JSON を返せるモデルにだけ付ける）
            builder.responseFormat("json_object");
        }
        return builder.build();
    }

    /** system / user のメッセージ列（画像は user の content の配列で送る）。 */
    private List<ChatMessage> messagesOf(AiRequest request) {
        List<ChatMessage> messages = new ArrayList<>(2);
        if (request.systemPrompt() != null && !request.systemPrompt().isBlank()) {
            messages.add(SystemMessage.from(request.systemPrompt()));
        }
        // 本文が無いときは空文字にする（null を渡すとライブラリ側で例外になる）
        String prompt = request.userPrompt() == null ? "" : request.userPrompt();
        if (request.image() == null || request.image().length == 0) {
            messages.add(UserMessage.from(prompt));
        } else {
            // 従来実装と同じ data URL（data:image/png;base64,...）になる
            messages.add(UserMessage.from(
                    new TextContent(prompt),
                    ImageContent.from(Base64.getEncoder().encodeToString(request.image()),
                            request.imageMime())));
        }
        return messages;
    }

    /** 応答を OpenAI 互換の本文へ組み直す（下流の抽出・呼出履歴の形を変えないため）。 */
    private String bodyOf(ChatResponse response) {
        ObjectNode root = objectMapper.createObjectNode();
        ObjectNode message = root.putArray("choices").addObject().putObject("message");
        message.put("role", "assistant");
        message.put("content", response.aiMessage() == null ? null : response.aiMessage().text());
        TokenUsage usage = response.tokenUsage();
        if (usage != null) {
            ObjectNode node = root.putObject("usage");
            putToken(node, "prompt_tokens", usage.inputTokenCount());
            putToken(node, "completion_tokens", usage.outputTokenCount());
            putToken(node, "total_tokens", usage.totalTokenCount());
        }
        return root.toString();
    }

    private static void putToken(ObjectNode node, String name, Integer value) {
        if (value != null) {
            node.put(name, value);
        }
    }

    /**
     * 失敗をエラーコードへ落とす。
     *
     * <p>LangChain4j の例外（{@code AuthenticationException} など）は**ステータス番号を持たない**ので、
     * 呼び出し側が付けた番号をそのまま使う（{@code HttpException} だけは {@code statusCode()} を持つ）。</p>
     */
    private AiResponse failureOfStatus(int status, AiRequest request, RuntimeException cause) {
        if (status >= 400 && status < 500 && status != 429) {
            return clientError(status, request, cause);
        }
        log.warn("geometry ai call failed (langchain4j). provider={} model={} status={} message={}",
                request.provider(), request.model(), status, cause.getMessage());
        return AiResponse.failure(status, status == 429 ? "HTTP_429" : "HTTP_5XX",
                "AI の呼び出しに失敗しました（HTTP " + status + "）。");
    }

    /** 4xx（キー・モデル不正）。再試行しても意味が無いので即 FAILED にする（設計 §6.4）。 */
    private AiResponse clientError(int status, AiRequest request, RuntimeException cause) {
        log.warn("geometry ai call rejected (langchain4j). provider={} model={} status={} message={}",
                request.provider(), request.model(), status, cause.getMessage());
        return AiResponse.failure(status, "HTTP_4XX",
                "AI がリクエストを受け付けませんでした（HTTP " + status
                        + "）。API Key とモデル名を確認してください。");
    }

    /** LangChain4j（JDK の HTTP クライアント）は割り込みを RuntimeException で包むので、原因をたどる。 */
    private static boolean containsInterrupt(Throwable cause) {
        for (Throwable current = cause; current != null; current = current.getCause()) {
            if (current instanceof InterruptedException) {
                return true;
            }
        }
        return false;
    }

    /**
     * 設定の URL から LangChain4j の baseUrl を割り出す。
     *
     * <p>設定は {@code https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions} のような
     * **エンドポイントそのもの**。LangChain4j は baseUrl に {@code /chat/completions} を足すので、
     * 末尾の {@code /chat/completions} を外す。末尾が違う URL（baseUrl を直接入れた場合）はそのまま使う。</p>
     */
    static String baseUrlOf(String url) {
        String value = url.trim();
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.regionMatches(true, value.length() - CHAT_COMPLETIONS_SUFFIX.length(),
                CHAT_COMPLETIONS_SUFFIX, 0, CHAT_COMPLETIONS_SUFFIX.length())) {
            value = value.substring(0, value.length() - CHAT_COMPLETIONS_SUFFIX.length());
        }
        return value;
    }

    /** 設定の URL が http(s) の URL として読めるか（従来実装と同じく、不正なら 4xx で返す）。 */
    private static boolean isValidUrl(String url) {
        try {
            URI parsed = URI.create(url);
            return parsed.getScheme() != null && parsed.getHost() != null;
        } catch (IllegalArgumentException cause) {
            return false;
        }
    }
}

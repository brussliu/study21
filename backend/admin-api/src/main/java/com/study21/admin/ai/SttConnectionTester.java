package com.study21.admin.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.stt.DashScopeAsrClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AIモデルページの**音声認識（STT）の【接続テスト】**（モデル・URL・API Key で本当に認識できるかを 1 回だけ確かめる）。
 *
 * <p>チャットの【接続テスト】（{@link AiConnectionTester}）と同じ作法で、**無音をごく短く送る**だけにする
 * （課金と待ち時間を最小にする）。確かめられるのは設定の正しさ:</p>
 * <ul>
 *   <li>{@code google} … Cloud Speech-to-Text v1 の {@code speech:recognize} へ LINEAR16 の無音を送る。
 *       API Key は URL の {@code key} に付ける（本番と同じ）。</li>
 *   <li>{@code alibaba} … DashScope の Paraformer-Realtime-V2 へ WebSocket で無音を送る
 *       （common-core の {@link DashScopeAsrClient}。本番の認識と同じ経路）。</li>
 * </ul>
 *
 * <p>戻り値は {@code ok} で成否を表す。接続できなかった場合は {@code ok=false} と日本語の理由を返し、
 * **設定の不備**（未対応のプロバイダー・値の欠落）は 400 にして画面へ理由をそのまま出す。</p>
 */
@Component
public class SttConnectionTester {

    private static final Logger log = LoggerFactory.getLogger(SttConnectionTester.class);

    /** 接続テストに対応するプロバイダー（画面の STT プロバイダーと同じ値）。 */
    private static final Set<String> SUPPORTED = Set.of("google", "alibaba");

    /** 接続テストのタイムアウト（秒）。 */
    private static final int TIMEOUT_SECONDS = 30;
    /** 送る無音の長さ（秒）。認識結果は空でよい（設定が通るかだけを見る）。 */
    private static final double SILENCE_SECONDS = 0.2;
    private static final int SAMPLE_RATE = 16000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final DashScopeAsrClient dashScopeClient;
    private final HttpClient httpClient;

    public SttConnectionTester() {
        this(new DashScopeAsrClient());
    }

    /** テスト用（Alibaba の呼び出しを差し替える）。 */
    SttConnectionTester(DashScopeAsrClient dashScopeClient) {
        this.dashScopeClient = dashScopeClient;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /** 1 回だけ呼んで接続を確かめる。 */
    public Map<String, Object> test(String provider, String model, String url, String apiKey) {
        String name = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED.contains(name)) {
            throw new ValidationException("この音声認識は接続テストに対応していません（" + provider
                    + "）。接続テストは Google Speech-to-Text と Alibaba Paraformer-Realtime-V2 だけです。");
        }
        String modelValue = model == null || model.isBlank() ? defaultModel(name) : model.trim();
        String urlValue = url == null || url.isBlank() ? defaultUrl(name) : url.trim();
        if (apiKey == null || apiKey.isBlank()) {
            throw new ValidationException("接続テストには API Key が必要です（【AIモデル】ページの"
                    + (name.equals("google") ? "「Google Speech-to-Text」" : "「Alibaba Paraformer-Realtime-V2」")
                    + "タブで設定してください）。");
        }

        byte[] silence = new byte[(int) (SAMPLE_RATE * SILENCE_SECONDS) * 2];
        long startedAt = System.currentTimeMillis();
        String failure = name.equals("google")
                ? probeGoogle(modelValue, urlValue, apiKey.trim(), silence)
                : probeAlibaba(modelValue, urlValue, apiKey.trim(), silence);
        int latencyMs = (int) (System.currentTimeMillis() - startedAt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", name);
        result.put("model", modelValue);
        result.put("latencyMs", latencyMs);
        if (failure == null) {
            log.info("stt connection test succeeded. provider={} model={} latencyMs={}", name, modelValue, latencyMs);
            result.put("ok", true);
            result.put("message", "接続できました（無音を送って確認。"
                    + latencyMs + "ms）。実際の書き起こしは授業録音の画面で確かめてください。");
        } else {
            log.warn("stt connection test failed. provider={} model={} message={}", name, modelValue, failure);
            result.put("ok", false);
            result.put("message", failure);
        }
        return result;
    }

    private static String defaultModel(String provider) {
        return provider.equals("google") ? "latest_long" : "paraformer-realtime-v2";
    }

    private static String defaultUrl(String provider) {
        return provider.equals("google")
                ? "https://speech.googleapis.com/v1/speech:recognize"
                : "wss://dashscope.aliyuncs.com/api-ws/v1/inference";
    }

    /** Google v1 の {@code speech:recognize} へ無音を 1 回送る。成功なら null、失敗なら理由。 */
    private String probeGoogle(String model, String url, String apiKey, byte[] silence) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode config = root.putObject("config");
        config.put("encoding", "LINEAR16");
        config.put("sampleRateHertz", SAMPLE_RATE);
        config.put("languageCode", "ja-JP");
        config.put("model", model);
        root.putObject("audio").put("content", Base64.getEncoder().encodeToString(silence));

        String separator = url.contains("?") ? "&" : "?";
        URI uri;
        try {
            uri = URI.create(url.contains("key=") ? url : url + separator + "key=" + apiKey);
        } catch (IllegalArgumentException cause) {
            throw new ValidationException("URL が正しくありません（" + url + "）。");
        }
        HttpRequest request = HttpRequest.newBuilder(uri)
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(root.toString(), StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> response =
                    httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return null;
            }
            String detail = googleErrorMessage(response.body());
            return "接続できませんでした（HTTP " + status + "）" + (detail.isEmpty() ? "" : ": " + detail)
                    + "。API Key・モデル名・URL を確認してください。";
        } catch (java.net.http.HttpTimeoutException cause) {
            return "接続できませんでした（" + TIMEOUT_SECONDS + " 秒で応答がありません）。";
        } catch (java.io.IOException cause) {
            return "接続できませんでした（" + cause.getClass().getSimpleName()
                    + (cause.getMessage() == null ? "" : ": " + cause.getMessage()) + "）。";
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            return "接続テストが中断されました。";
        }
    }

    /** Google のエラー本文（`error.message`）を取り出す（取れなければ空）。 */
    private String googleErrorMessage(String body) {
        if (body == null || body.isBlank()) {
            return "";
        }
        try {
            return MAPPER.readTree(body).path("error").path("message").asText("");
        } catch (Exception cause) {
            return "";
        }
    }

    /** DashScope のリアルタイム認識へ無音を 1 回送る。成功なら null、失敗なら理由。 */
    private String probeAlibaba(String model, String url, String apiKey, byte[] silence) {
        DashScopeAsrClient.Result result = dashScopeClient.transcribe(new DashScopeAsrClient.Request(
                url, apiKey, model, "pcm", SAMPLE_RATE, null, silence, TIMEOUT_SECONDS));
        if (result.ok()) {
            return null;
        }
        return (result.errorMessage() == null || result.errorMessage().isBlank()
                ? "接続できませんでした。" : result.errorMessage())
                + "（API Key・モデル名・URL を確認してください）";
    }
}

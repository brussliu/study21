package com.study21.admin.ai;

import com.study21.admin.geometryai.GeometryAiClient;
import com.study21.common.core.exception.ValidationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * AIモデルページの【接続テスト】（URL・API Key・モデル名で本当に呼べるかを 1 回だけ確かめる）。
 *
 * <p>対象は **OpenAI 互換の {@code chat/completions} を使う 4 プロバイダー**
 * （千問 / 豆包 / DeepSeek / OpenAI）。どれも同じ形なので、共通の
 * {@link GeometryAiClient}（LangChain4j 実装）で**小さなチャットを 1 回**送るだけで足りる。</p>
 *
 * <p>BigModel / 智譜 OCR は画像を送る別形式（{@code layout_parsing}）なので対象外。
 * 画面でもそのタブには【接続テスト】ボタンを出さない。</p>
 *
 * <p>戻り値（200 の本文）は {@code ok} で成否を表す。接続できなかった場合は例外にせず
 * {@code ok=false} と日本語の理由を返し、**設定の不備**（未対応プロバイダー・キーや URL の欠落）は
 * 400 にして画面へ理由をそのまま出す。</p>
 */
@Component
public class AiConnectionTester {

    private static final Logger log = LoggerFactory.getLogger(AiConnectionTester.class);

    /** 接続テストに対応するプロバイダー（OpenAI 互換の chat/completions）。 */
    private static final Set<String> SUPPORTED = Set.of("qwen", "doubao", "deepseek", "openai", "chatgpt");

    /** 接続テストのタイムアウト（秒）。待たせすぎない。 */
    private static final int TIMEOUT_SECONDS = 30;
    /** 出力はごく短くてよい（課金を最小にする）。 */
    private static final int MAX_TOKENS = 16;

    private final GeometryAiClient aiClient;

    public AiConnectionTester(GeometryAiClient aiClient) {
        this.aiClient = aiClient;
    }

    /**
     * 1 回だけ呼んで接続を確かめる。
     *
     * @param provider 画面が送るプロバイダー名（{@code qwen} / {@code doubao} / {@code deepseek} / {@code openai}）
     */
    public Map<String, Object> test(String provider, String model, String url, String apiKey) {
        String name = provider == null ? "" : provider.trim().toLowerCase(Locale.ROOT);
        if (!SUPPORTED.contains(name)) {
            throw new ValidationException("この AI は接続テストに対応していません（" + provider
                    + "）。接続テストは OpenAI 互換のチャット API を使う 4 つの AI（千問 / 豆包 / DeepSeek / OpenAI）だけです。");
        }
        if (model == null || model.isBlank()) {
            throw new ValidationException("接続テストにはモデル名が必要です（モデル1 を設定してください）。");
        }
        if (url == null || url.isBlank()) {
            throw new ValidationException("接続テストには URL が必要です。");
        }
        if (apiKey == null || apiKey.isBlank()) {
            throw new ValidationException("接続テストには API Key が必要です。");
        }

        long startedAt = System.currentTimeMillis();
        GeometryAiClient.AiResponse response = aiClient.call(new GeometryAiClient.AiRequest(
                name, model.trim(), url.trim(), apiKey.trim(),
                "接続テストです。", "「pong」とだけ返してください。",
                null, null, TIMEOUT_SECONDS, 0.0, MAX_TOKENS, false));
        int latencyMs = (int) (System.currentTimeMillis() - startedAt);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("provider", name);
        result.put("model", model.trim());
        result.put("latencyMs", latencyMs);
        if (response.isSuccess()) {
            log.info("AI connection test succeeded. provider={} model={} latencyMs={}",
                    name, model, latencyMs);
            result.put("ok", true);
            result.put("message", "接続できました（" + latencyMs + "ms）");
        } else {
            log.warn("AI connection test failed. provider={} code={} message={}",
                    name, response.errorCode(), response.errorMessage());
            result.put("ok", false);
            result.put("message", response.errorMessage() == null
                    ? "接続できませんでした。" : response.errorMessage());
        }
        return result;
    }
}

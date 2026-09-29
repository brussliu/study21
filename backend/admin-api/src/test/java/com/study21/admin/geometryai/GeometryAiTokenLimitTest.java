package com.study21.admin.geometryai;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 出力の上限の指定方法（{@code max_tokens} / {@code max_completion_tokens}）。
 *
 * <p>2026-09-27 の実機確認で、設定のモデル {@code gpt-5.4-mini} に {@code max_tokens} を送ると
 * <b>HTTP 400</b>「Unsupported parameter: 'max_tokens'」で失敗することが分かった。モデル名で
 * 判断して切り替える（この判定が壊れると全ての AI 機能が 400 で止まる）。</p>
 */
class GeometryAiTokenLimitTest {

    @Test
    @DisplayName("GPT-5 系・o 系は max_completion_tokens（max_tokens を送ると 400 になる）")
    void requiresCompletionTokensForNewOpenAiModels() {
        assertThat(GeometryAiClient.requiresCompletionTokens("gpt-5.4-mini")).isTrue();
        assertThat(GeometryAiClient.requiresCompletionTokens("gpt-5")).isTrue();
        assertThat(GeometryAiClient.requiresCompletionTokens("GPT-5.4-MINI")).isTrue();
        assertThat(GeometryAiClient.requiresCompletionTokens("gpt-6-preview")).isTrue();
        assertThat(GeometryAiClient.requiresCompletionTokens("o3-mini")).isTrue();
        assertThat(GeometryAiClient.requiresCompletionTokens("o1")).isTrue();
    }

    @Test
    @DisplayName("従来のモデルは max_tokens のまま（qwen / doubao / deepseek / gpt-4o）")
    void keepsMaxTokensForLegacyModels() {
        assertThat(GeometryAiClient.requiresCompletionTokens("gpt-4o")).isFalse();
        assertThat(GeometryAiClient.requiresCompletionTokens("gpt-4o-mini")).isFalse();
        assertThat(GeometryAiClient.requiresCompletionTokens("qwen-max")).isFalse();
        assertThat(GeometryAiClient.requiresCompletionTokens("doubao-pro-32k")).isFalse();
        assertThat(GeometryAiClient.requiresCompletionTokens("deepseek-chat")).isFalse();
        assertThat(GeometryAiClient.requiresCompletionTokens("glm-4")).isFalse();
    }

    @Test
    @DisplayName("モデル名が無いときは従来どおり（設定不備で落とさない）")
    void defaultsToMaxTokens() {
        assertThat(GeometryAiClient.requiresCompletionTokens(null)).isFalse();
        assertThat(GeometryAiClient.requiresCompletionTokens("")).isFalse();
        assertThat(GeometryAiClient.requiresCompletionTokens("   ")).isFalse();
    }
}

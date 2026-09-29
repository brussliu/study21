package com.study21.admin.japanesewordai;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * batC41〜batC44 が必要とする設定（{@code BAT_C4x_*}）の解決。
 *
 * <p>確かめる接縫はこの 1 点: <b>設定値のマップから、型の付いた設定を作る</b>
 * （{@code SettingsService#requireSettings} の戻り値を受ける純関数）。</p>
 *
 * <p>2.1 の規約では<b>既定値もフォールバックも持たない</b>。2.0 は
 * 「threads の既定 3」「timeout は DETAIL 300 / 他 120」などをコードに持っていたが、
 * 2.1 は設定の欠落・型不正・範囲外を日本語の理由で例外にする。</p>
 */
class JapaneseWordAiSettingsTest {

    private static Map<String, String> values(String... overrides) {
        Map<String, String> map = new LinkedHashMap<>();
        map.put("BAT_C41_AI_PROVIDER", "qwen:1");
        map.put("BAT_C41_BATCH_MAX", "20");
        map.put("BAT_C41_THREADS", "3");
        map.put("BAT_C41_REQUEST_TIMEOUT_SECONDS", "300");
        map.put("BAT_C41_MAX_COMPLETION_TOKENS", "11776");
        map.put("BAT_C41_TEMPERATURE", "0.2");
        map.put("BAT_C41_SYSTEM_PROMPT", "system");
        map.put("BAT_C41_USER_PROMPT", "取得区分: {{kind}}\n{{word_json}}");
        map.put("BAT_C41_RETRY_LIMIT", "1");
        for (int index = 0; index + 1 < overrides.length; index += 2) {
            map.put(overrides[index], overrides[index + 1]);
        }
        return map;
    }

    @Test
    @DisplayName("設定値を型付きで読む（provider / batchMax / threads / timeout / tokens / 温度 / プロンプト / 再試行）")
    void readsValues() {
        JapaneseWordAiSettings settings = JapaneseWordAiSettings.of(values());

        assertThat(settings.provider()).isEqualTo("qwen:1");
        assertThat(settings.batchMax()).isEqualTo(20);
        assertThat(settings.threads()).isEqualTo(3);
        assertThat(settings.timeoutSeconds()).isEqualTo(300);
        assertThat(settings.maxCompletionTokens()).isEqualTo(11776);
        assertThat(settings.temperature()).isEqualTo(0.2);
        assertThat(settings.retryLimit()).isEqualTo(1);
        assertThat(settings.systemPrompt()).isEqualTo("system");
        assertThat(settings.userPrompt()).contains("{{word_json}}");
    }

    @Test
    @DisplayName("温度は小数で読む（0.0〜2.0 の範囲外は例外）")
    void readsTemperature() {
        assertThat(JapaneseWordAiSettings.of(values("BAT_C41_TEMPERATURE", "0.9")).temperature())
                .isEqualTo(0.9);
        assertThatThrownBy(() -> JapaneseWordAiSettings.of(values("BAT_C41_TEMPERATURE", "2.5")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BAT_C41_TEMPERATURE");
    }

    @Test
    @DisplayName("数値でない・範囲外・欠落は日本語の理由で例外にする")
    void rejectsBrokenValues() {
        assertThatThrownBy(() -> JapaneseWordAiSettings.of(values("BAT_C41_THREADS", "たくさん")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BAT_C41_THREADS");

        assertThatThrownBy(() -> JapaneseWordAiSettings.of(values("BAT_C41_BATCH_MAX", "0")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BAT_C41_BATCH_MAX");

        Map<String, String> missing = values();
        missing.remove("BAT_C41_SYSTEM_PROMPT");
        assertThatThrownBy(() -> JapaneseWordAiSettings.of(missing))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("BAT_C41_SYSTEM_PROMPT");
    }

    @Test
    @DisplayName("内容種別コードは取得区分から決まる（DETAIL→A_DETAIL、C→C1_READING/C2_KANJI）")
    void resolvesContentTypes() {
        assertThat(JapaneseWordAiSettings.contentTypesOf("DETAIL")).containsExactly("A_DETAIL");
        assertThat(JapaneseWordAiSettings.contentTypesOf("C"))
                .containsExactly("C1_READING", "C2_KANJI");
        assertThat(JapaneseWordAiSettings.contentTypesOf("D")).containsExactly("D_CONTEXT_MEANING");
        assertThat(JapaneseWordAiSettings.contentTypesOf("E")).containsExactly("E_KANJI_USAGE");
        assertThatThrownBy(() -> JapaneseWordAiSettings.contentTypesOf("X"))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    @DisplayName("並列数の上限は対象の語数で抑える（1 語しか無いのに 3 並列にしない）")
    void capsThreadsByWordCount() {
        JapaneseWordAiSettings settings = JapaneseWordAiSettings.of(values());

        assertThat(settings.threadsFor(10)).isEqualTo(3);
        assertThat(settings.threadsFor(1)).isEqualTo(1);
        assertThat(settings.threadsFor(0)).isEqualTo(1);
    }
}

package com.study21.common.core.geometryai;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 有効な設定の解決（共通 → モード別の継承）とスナップショットの往復。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>**共通の System Prompt だけ**で解決できる（共通の User Prompt は空でよい）</li>
 *   <li>モード別の System Prompt は共通を**上書きせず連結**する</li>
 *   <li>モード別の User Prompt があれば、共通の User Prompt が空でもそれを使う</li>
 *   <li>モデルパラメータはモード別 → 共通 → 既定値の順で採る</li>
 *   <li>スナップショットは本文をそのまま持ち、秘密（API Key・URL）を持たない</li>
 *   <li>追跡用の情報を足しても、固定した設定は消えない</li>
 * </ol>
 */
class AiFigureConfigTest {

    /** 共通の設定（User Prompt は空＝利用者が書いていない状態）。 */
    private static Map<String, String> common() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AiFigureSettingKeys.ENABLED, "true");
        values.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        values.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "共通のルール。");
        values.put(AiFigureSettingKeys.INSTRUCTION_TEMPLATE, "");
        values.put(AiFigureSettingKeys.TEMPERATURE, "0.2");
        values.put(AiFigureSettingKeys.MAX_COMPLETION_TOKENS, "4096");
        values.put(AiFigureSettingKeys.REQUEST_TIMEOUT_SECONDS, "120");
        values.put(AiFigureSettingKeys.RETRY_LIMIT, "1");
        values.put(AiFigureSettingKeys.MAX_COMMANDS, "80");
        values.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment,Polygon");
        return values;
    }

    @Test
    @DisplayName("共通の System Prompt だけで解決できる（共通の User Prompt は空でよい）")
    void resolvesWithoutCommonUserPrompt() {
        AiFigureConfig config = AiFigureConfig.resolve("A", "batC51-A", common(), "2026-09-19T00:00:00");

        assertThat(config.systemPromptCommon()).isEqualTo("共通のルール。");
        assertThat(config.systemPromptMode()).isEmpty();
        assertThat(config.systemFromMode()).isFalse();
        // テンプレートは無い（テンプレート無しでも実行できる）
        assertThat(config.taskTemplate()).isEmpty();
        assertThat(config.taskTemplateFrom()).isEqualTo(AiFigureConfig.TaskTemplateFrom.NONE);
        assertThat(config.hasTaskTemplate()).isFalse();
        assertThat(config.maxCommands()).isEqualTo(80);
        assertThat(config.allowedCommands()).isEqualTo("Point,Segment,Polygon");
    }

    @Test
    @DisplayName("モード別の User Prompt があれば、共通の User Prompt が空でもそれを使う")
    void usesModeTemplateWhenCommonIsEmpty() {
        Map<String, String> values = common();
        values.put(AiFigureSettingKeys.taskTemplateKey("B"), "式を読んでグラフを作ってください。");

        AiFigureConfig config = AiFigureConfig.resolve("B", "batC51-B", values, null);

        assertThat(config.taskTemplate()).isEqualTo("式を読んでグラフを作ってください。");
        assertThat(config.taskTemplateFrom()).isEqualTo(AiFigureConfig.TaskTemplateFrom.MODE);
        assertThat(config.taskFromMode()).isTrue();
    }

    @Test
    @DisplayName("モード別の System Prompt は共通を上書きせず、両方を持つ")
    void keepsCommonSystemPromptAlongsideMode() {
        Map<String, String> values = common();
        values.put(AiFigureSettingKeys.systemPromptKey("C"), "C の担当は文章からの作図です。");

        AiFigureConfig config = AiFigureConfig.resolve("C", "batC51-C", values, null);

        assertThat(config.systemPromptCommon()).isEqualTo("共通のルール。");
        assertThat(config.systemPromptMode()).isEqualTo("C の担当は文章からの作図です。");
        assertThat(config.systemFromMode()).isTrue();
    }

    @Test
    @DisplayName("共通の User Prompt がありモード別が空なら共通を使う")
    void fallsBackToCommonTemplate() {
        Map<String, String> values = common();
        values.put(AiFigureSettingKeys.INSTRUCTION_TEMPLATE, "共通のタスクです。");

        AiFigureConfig config = AiFigureConfig.resolve("D", "batC51-D", values, null);

        assertThat(config.taskTemplate()).isEqualTo("共通のタスクです。");
        assertThat(config.taskTemplateFrom()).isEqualTo(AiFigureConfig.TaskTemplateFrom.COMMON);
        assertThat(config.taskFromMode()).isFalse();
    }

    @Test
    @DisplayName("モデルパラメータはモード別が優先で、無ければ共通、どちらも無ければ既定値")
    void inheritsModelParameters() {
        Map<String, String> values = common();
        AiFigureConfig inherited = AiFigureConfig.resolve("A", "batC51-A", values, null);
        assertThat(inherited.provider()).isEqualTo("qwen:4");
        assertThat(inherited.temperature()).isEqualTo(0.2);
        assertThat(inherited.maxCompletionTokens()).isEqualTo(4096);
        assertThat(inherited.requestTimeoutSeconds()).isEqualTo(120);
        assertThat(inherited.retryLimit()).isEqualTo(1);

        values.put(AiFigureSettingKeys.modeKey("A", AiFigureSettingKeys.SUFFIX_PROVIDER), "qwen:2");
        values.put(AiFigureSettingKeys.modeKey("A", AiFigureSettingKeys.SUFFIX_TEMPERATURE), "0.9");
        values.put(AiFigureSettingKeys.modeKey("A", AiFigureSettingKeys.SUFFIX_MAX_COMPLETION_TOKENS), "8192");
        values.put(AiFigureSettingKeys.modeKey("A", AiFigureSettingKeys.SUFFIX_REQUEST_TIMEOUT_SECONDS), "300");
        values.put(AiFigureSettingKeys.modeKey("A", AiFigureSettingKeys.SUFFIX_RETRY_LIMIT), "0");
        AiFigureConfig overridden = AiFigureConfig.resolve("A", "batC51-A", values, null);
        assertThat(overridden.provider()).isEqualTo("qwen:2");
        assertThat(overridden.temperature()).isEqualTo(0.9);
        assertThat(overridden.maxCompletionTokens()).isEqualTo(8192);
        assertThat(overridden.requestTimeoutSeconds()).isEqualTo(300);
        assertThat(overridden.retryLimit()).isZero();
        // 触っていないモードは共通のまま
        assertThat(AiFigureConfig.resolve("B", "batC51-B", values, null).provider()).isEqualTo("qwen:4");
    }

    @Test
    @DisplayName("数値が無い（未設定）ときは既定値を使う")
    void usesDefaultsWhenNumbersAreMissing() {
        Map<String, String> values = common();
        values.remove(AiFigureSettingKeys.TEMPERATURE);
        values.remove(AiFigureSettingKeys.MAX_COMPLETION_TOKENS);
        values.remove(AiFigureSettingKeys.REQUEST_TIMEOUT_SECONDS);
        values.remove(AiFigureSettingKeys.RETRY_LIMIT);
        values.remove(AiFigureSettingKeys.MAX_COMMANDS);
        values.remove(AiFigureSettingKeys.OUTPUT_FORMAT);

        AiFigureConfig config = AiFigureConfig.resolve("A", "batC51-A", values, null);

        assertThat(config.temperature()).isEqualTo(0.2);
        assertThat(config.maxCompletionTokens()).isEqualTo(4096);
        assertThat(config.requestTimeoutSeconds()).isEqualTo(120);
        assertThat(config.retryLimit()).isZero();
        assertThat(config.maxCommands()).isEqualTo(80);
        assertThat(config.outputFormat()).isEqualTo("JSON");
    }

    @Test
    @DisplayName("共通の System Prompt が空なら理由の分かる例外にする")
    void rejectsBlankCommonSystemPrompt() {
        Map<String, String> values = common();
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "   ");

        assertThatThrownBy(() -> AiFigureConfig.resolve("A", "batC51-A", values, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(AiFigureSettingKeys.SYSTEM_PROMPT)
                .hasMessageContaining("図形管理");
    }

    @Test
    @DisplayName("スナップショットは本文を持ち、秘密（API Key・URL）を持たない")
    void snapshotKeepsPromptBodiesWithoutSecrets() {
        Map<String, String> values = common();
        values.put(AiFigureSettingKeys.systemPromptKey("A"), "A のルール。");
        values.put(AiFigureSettingKeys.taskTemplateKey("A"), "{note} を反映してください。");
        AiFigureConfig config = AiFigureConfig.resolve("A", "batC51-A", values, "2026-09-19T00:00:00");

        String json = config.toSnapshotJson(null);

        assertThat(json)
                .contains("\"mode\":\"A\"")
                .contains("\"taskCode\":\"batC51-A\"")
                .contains("\"systemPromptCommon\":\"共通のルール。\"")
                .contains("\"systemPromptMode\":\"A のルール。\"")
                .contains("\"taskTemplate\":\"{note} を反映してください。\"")
                .contains("\"taskTemplateFrom\":\"MODE\"")
                .contains("\"capturedAt\":\"2026-09-19T00:00:00\"");
        // 秘密は入れない（API Key・URL はスナップショットの対象外）
        assertThat(json).doesNotContain("apiKey").doesNotContain("secret").doesNotContain("http");
    }

    @Test
    @DisplayName("スナップショットから同じ設定へ戻せる（本文がそのまま復元できる）")
    void roundTripsThroughJson() {
        Map<String, String> values = common();
        values.put(AiFigureSettingKeys.systemPromptKey("D"), "D のルール。");
        values.put(AiFigureSettingKeys.taskTemplateKey("D"), "本文と図を合わせて作図してください。");
        AiFigureConfig config = AiFigureConfig.resolve("D", "batC51-D", values, "2026-09-19T01:02:03");

        AiFigureConfig restored = AiFigureConfig.fromSnapshotJson(config.toSnapshotJson(null)).orElseThrow();

        assertThat(restored).isEqualTo(config);
    }

    @Test
    @DisplayName("追跡用の情報を足しても、固定した設定は消えない")
    void withTraceKeepsPinnedConfig() {
        Map<String, String> values = common();
        AiFigureConfig config = AiFigureConfig.resolve("A", "batC51-A", values, "2026-09-19T00:00:00");
        String pinned = config.toSnapshotJson(null);

        com.fasterxml.jackson.databind.node.ObjectNode trace =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        trace.put("model", "qwen-vl-max");
        trace.put("resolvedOutputType", "GEOMETRY");
        String merged = AiFigureConfig.withTrace(pinned, trace);

        assertThat(merged).contains("\"systemPromptCommon\":\"共通のルール。\"");
        assertThat(merged).contains("\"model\":\"qwen-vl-max\"");
        assertThat(AiFigureConfig.fromSnapshotJson(merged).orElseThrow()).isEqualTo(config);
    }

    @Test
    @DisplayName("形の違うスナップショットは読まない（今の設定で解決し直す）")
    void ignoresUnknownSnapshotShape() {
        assertThat(AiFigureConfig.fromSnapshotJson(null)).isEmpty();
        assertThat(AiFigureConfig.fromSnapshotJson("{}")).isEmpty();
        // 古い形（ハッシュだけ）は本文が無いので読まない
        assertThat(AiFigureConfig.fromSnapshotJson("{\"systemPromptHash\":\"abcd\"}")).isEmpty();
        assertThat(AiFigureConfig.fromSnapshotJson("壊れた JSON")).isEmpty();
    }
}

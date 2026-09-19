package com.study21.common.core.geometryai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * スナップショットの読み取り（**無い / 使える / 壊れている**の区別）と版の扱い。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>空・未設定は {@code ABSENT}（いまの設定から作り直してよい）</li>
 *   <li>完全なものは {@code VALID}（本文もモデル名も実行版も戻る）</li>
 *   <li>足りない・壊れている・版が違うものは {@code BROKEN}（**黙っていまの設定へ切り替えない**）</li>
 *   <li>追跡用のハッシュだけの古い形も {@code BROKEN}（本文が復元できない）</li>
 *   <li>前の版が書いた {@code model}／{@code revision} の無いものは読める（互換の規則）</li>
 *   <li>{@link AiFigureConfig#repin} は本文を差し替え、実行の記録（trace）は残す</li>
 * </ol>
 */
class AiFigureConfigSnapshotTest {

    private static Map<String, String> settings() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "共通のルール。");
        values.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        values.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment");
        values.put(AiFigureSettingKeys.MAX_COMMANDS, "40");
        values.put(AiFigureSettingKeys.TEMPERATURE, "0.3");
        values.put(AiFigureSettingKeys.OUTPUT_FORMAT, "JSON");
        return values;
    }

    private static AiFigureConfig config() {
        return AiFigureConfig.resolve("A", "batC51-A", settings(), "2026-09-19T00:00:00", "qwen-vl-max", 3);
    }

    @Test
    @DisplayName("空・未設定は ABSENT（いまの設定から作り直してよい）")
    void emptyIsAbsent() {
        assertThat(AiFigureConfig.parseSnapshot(null).isAbsent()).isTrue();
        assertThat(AiFigureConfig.parseSnapshot("").isAbsent()).isTrue();
        assertThat(AiFigureConfig.parseSnapshot("   ").isAbsent()).isTrue();
    }

    @Test
    @DisplayName("完全なスナップショットは VALID（本文・モデル名・実行版が戻る）")
    void completeSnapshotIsValid() {
        AiFigureConfig.SnapshotState state = AiFigureConfig.parseSnapshot(config().toSnapshotJson(null));

        assertThat(state.isValid()).isTrue();
        AiFigureConfig restored = state.config();
        assertThat(restored.systemPromptCommon()).isEqualTo("共通のルール。");
        assertThat(restored.provider()).isEqualTo("qwen:4");
        assertThat(restored.model()).isEqualTo("qwen-vl-max");
        assertThat(restored.temperature()).isEqualTo(0.3);
        assertThat(restored.maxCommands()).isEqualTo(40);
        assertThat(restored.revision()).isEqualTo(3);
    }

    @Test
    @DisplayName("壊れている・足りない・版が違うものは BROKEN（理由が読める）")
    void brokenSnapshotsAreReported() {
        assertThat(AiFigureConfig.parseSnapshot("壊れた JSON").isBroken()).isTrue();
        assertThat(AiFigureConfig.parseSnapshot("[]").isBroken()).isTrue();
        assertThat(AiFigureConfig.parseSnapshot("{}").problem()).contains("config");

        // 本文（systemPromptCommon）が無い
        assertThat(AiFigureConfig.parseSnapshot("{\"version\":1,\"config\":{\"version\":1,\"mode\":\"A\"}}")
                .problem()).contains("systemPromptCommon");

        // 版が違う
        assertThat(AiFigureConfig.parseSnapshot(
                "{\"version\":9,\"config\":{\"version\":9,\"mode\":\"A\",\"systemPromptCommon\":\"x\"}}")
                .problem()).contains("版");

        // 追跡用のハッシュだけの古い形（本文が復元できない）
        assertThat(AiFigureConfig.parseSnapshot(
                "{\"taskCode\":\"batC51-A\",\"systemPromptHash\":\"abcd\",\"taskTemplateHash\":\"efgh\"}")
                .problem()).contains("古い形式");
    }

    @Test
    @DisplayName("前の版が書いた model / revision の無いスナップショットも読める（互換の規則）")
    void readsSnapshotsFromThePreviousVersion() {
        // 本文はあるが model と revision が無い（2026-09-19 の改修前の形）
        String legacy = """
                {"version":1,"config":{"version":1,"mode":"A","taskCode":"batC51-A","provider":"qwen:4",
                 "temperature":0.8,"maxCompletionTokens":2048,"requestTimeoutSeconds":60,"retryLimit":1,
                 "maxCommands":20,"allowedCommands":"Point","outputFormat":"JSON",
                 "systemPromptCommon":"前の版のルール。","systemPromptMode":"","taskTemplate":"",
                 "taskTemplateFrom":"NONE","capturedAt":"2026-09-18T00:00:00"}}
                """;

        AiFigureConfig.SnapshotState state = AiFigureConfig.parseSnapshot(legacy);

        assertThat(state.isValid()).isTrue();
        assertThat(state.config().systemPromptCommon()).isEqualTo("前の版のルール。");
        // モデルは「固定できていない」＝実行の直前に読み直して固定する
        assertThat(state.config().hasPinnedModel()).isFalse();
        assertThat(state.config().revision()).isEqualTo(1);
    }

    @Test
    @DisplayName("repin は本文を差し替え、実行の記録（trace）は残す")
    void repinKeepsTheTrace() {
        com.fasterxml.jackson.databind.node.ObjectNode trace =
                new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        trace.put("model", "qwen-vl-max");
        String withTrace = AiFigureConfig.withTrace(config().toSnapshotJson(null), trace);

        AiFigureConfig repinned = config().withModel("qwen3-vl-plus").withRevision(4);
        String updated = AiFigureConfig.repin(withTrace, repinned);

        AiFigureConfig.SnapshotState state = AiFigureConfig.parseSnapshot(updated);
        assertThat(state.isValid()).isTrue();
        assertThat(state.config().model()).isEqualTo("qwen3-vl-plus");
        assertThat(state.config().revision()).isEqualTo(4);
        assertThat(updated).contains("\"model\":\"qwen-vl-max\"");   // trace は残る
        assertThat(AiFigureConfig.fromSnapshotJson(updated)).isPresent();
    }

    @Test
    @DisplayName("スロットの指定から AI_MODEL のキーを組み立てる（1 番だけ接尾辞なし）")
    void resolvesModelSlotKeys() {
        AiModelSlot first = AiModelSlot.require("qwen:1");
        assertThat(first.provider()).isEqualTo("qwen");
        assertThat(first.modelKey()).isEqualTo("AI_QWEN_MODEL");
        assertThat(first.urlKey()).isEqualTo("AI_QWEN_URL");
        assertThat(first.apiKeyKey()).isEqualTo("AI_QWEN_API_KEY");

        assertThat(AiModelSlot.require("qwen:4").modelKey()).isEqualTo("AI_QWEN_MODEL_4");
        assertThat(AiModelSlot.require("deepseek:2").modelKey()).isEqualTo("AI_DEEPSEEK_MODEL_2");
        assertThat(AiModelSlot.require("doubao:3").modelKey()).isEqualTo("AI_DOUBAO_MODEL_3");
        // openai は chatgpt と同じ設定を指す
        assertThat(AiModelSlot.require("openai:2").modelKey()).isEqualTo("AI_CHATGPT_MODEL_2");
        assertThat(AiModelSlot.require(" QWEN:2 ").modelKey()).isEqualTo("AI_QWEN_MODEL_2");

        assertThat(AiModelSlot.of("gemini:1")).isEmpty();
        assertThat(AiModelSlot.of("qwen")).isEmpty();
        assertThat(AiModelSlot.of("qwen:")).isEmpty();
        assertThat(AiModelSlot.of(null)).isEmpty();
        assertThat(org.assertj.core.api.Assertions.catchThrowable(() -> AiModelSlot.require("qwen:")))
                .isInstanceOf(com.study21.common.core.exception.ValidationException.class)
                .hasMessageContaining("qwen:");
    }
}

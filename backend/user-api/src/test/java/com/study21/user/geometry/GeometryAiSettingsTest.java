package com.study21.user.geometry;

import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.geometryai.AiFigureConfig;
import com.study21.common.core.geometryai.AiFigureSettingKeys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 要求の受付時に固定する「有効な設定」（要求行の設定スナップショット）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>共通＋モード別の継承を適用した結果を固定する（モード別が優先）</li>
 *   <li>**共用の User Prompt が空でも固定できる**（モード別に書いてあればそれを使う）</li>
 *   <li>共通の System Prompt が無ければ、受付の時点で理由の分かる例外にする</li>
 *   <li>復元できる本文が入り、**秘密（API Key・URL）は入らない**</li>
 * </ol>
 */
class GeometryAiSettingsTest {

    private GeometryAiSettingMapper settingMapper;
    private GeometryAiSettings settings;

    @BeforeEach
    void setUp() {
        settingMapper = mock(GeometryAiSettingMapper.class);
        settings = new GeometryAiSettings(settingMapper);
    }

    /** 設定値を返すスタブを作る（呼ばれたキーに応じて返す）。 */
    private void storeAll(java.util.Map<String, String> values) {
        when(settingMapper.findByKeys(eq(AiFigureSettingKeys.PAGE), any()))
                .thenAnswer(invocation -> {
                    List<String> keys = invocation.getArgument(1);
                    java.util.List<GeometryAiSettingEntity> rows = new java.util.ArrayList<>();
                    for (String key : keys) {
                        if (values.containsKey(key)) {
                            GeometryAiSettingEntity entity = new GeometryAiSettingEntity();
                            entity.setSettingKey(key);
                            entity.setSettingValue(values.get(key));
                            rows.add(entity);
                        }
                    }
                    return rows;
                });
    }

    @Test
    @DisplayName("共通＋モード別の継承を適用して固定する（モード別が優先）")
    void pinsTheEffectiveConfig() {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "共通のルール。");
        values.put(AiFigureSettingKeys.INSTRUCTION_TEMPLATE, "");
        values.put(AiFigureSettingKeys.PROVIDER, "qwen:4");
        values.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point,Segment");
        values.put(AiFigureSettingKeys.MAX_COMMANDS, "80");
        values.put(AiFigureSettingKeys.systemPromptKey("B"), "B のルール。");
        values.put(AiFigureSettingKeys.taskTemplateKey("B"), "{modeLabel} として作図してください。");
        values.put(AiFigureSettingKeys.modeKey("B", AiFigureSettingKeys.SUFFIX_PROVIDER), "qwen:2");
        storeAll(values);

        AiFigureConfig pinned = AiFigureConfig.fromSnapshotJson(settings.pinnedConfigJson("B")).orElseThrow();

        assertThat(pinned.mode()).isEqualTo("B");
        // System Prompt は共通＋モード別（モード別で上書きしない）
        assertThat(pinned.systemPromptCommon()).isEqualTo("共通のルール。");
        assertThat(pinned.systemPromptMode()).isEqualTo("B のルール。");
        // 共用の User Prompt は空でも、モード別があればそれを使う
        assertThat(pinned.taskTemplate()).isEqualTo("{modeLabel} として作図してください。");
        assertThat(pinned.taskTemplateFrom()).isEqualTo(AiFigureConfig.TaskTemplateFrom.MODE);
        assertThat(pinned.provider()).isEqualTo("qwen:2");
        assertThat(pinned.capturedAt()).isNotBlank();
    }

    @Test
    @DisplayName("作図モードが無い（歴史的な画面）ときは A として固定する")
    void pinsModeAForLegacyRequests() {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "共通のルール。");
        values.put(AiFigureSettingKeys.systemPromptKey("A"), "A のルール。");
        storeAll(values);

        AiFigureConfig pinned = AiFigureConfig.fromSnapshotJson(settings.pinnedConfigJson(null)).orElseThrow();

        assertThat(pinned.mode()).isEqualTo("A");
        assertThat(pinned.systemPromptMode()).isEqualTo("A のルール。");
    }

    @Test
    @DisplayName("共通の System Prompt が無ければ受付の時点で理由を返す")
    void rejectsWhenCommonSystemPromptIsMissing() {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "   ");
        storeAll(values);

        assertThatThrownBy(() -> settings.pinnedConfigJson("A"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining(AiFigureSettingKeys.SYSTEM_PROMPT);
    }

    @Test
    @DisplayName("復元できる本文が入り、秘密（API Key・URL）は入らない")
    void snapshotHasNoSecrets() {
        java.util.Map<String, String> values = new java.util.LinkedHashMap<>();
        values.put(AiFigureSettingKeys.SYSTEM_PROMPT, "共通のルール。");
        values.put(AiFigureSettingKeys.INSTRUCTION_TEMPLATE, "作図してください。");
        values.put(AiFigureSettingKeys.ALLOWED_COMMANDS, "Point");
        storeAll(values);

        String json = settings.pinnedConfigJson("A");

        assertThat(json)
                .contains("\"systemPromptCommon\":\"共通のルール。\"")
                .contains("\"taskTemplate\":\"作図してください。\"")
                .contains("\"taskTemplateFrom\":\"COMMON\"");
        // 秘密は要求行へ書かない（AI_MODEL ページの設定は含めない）
        assertThat(json).doesNotContain("apiKey").doesNotContain("secret")
                .doesNotContain("http").doesNotContain("GEOMETRY_AI_ENABLED");
    }
}

package com.study21.admin.batch;

import com.study21.admin.geometryai.dto.FigureMode;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link BatchTaskRegistry} の登録内容を固定する。
 *
 * <p>利用者の指示で、AI 生図の流水線は「前処理（通常コード）→ batC51（AI 生成）→ 検証（通常コード）」
 * になり、AI 画図助手は batC52 として実行する。AI 生成は作図モードごとに 1 バッチ
 * （batC51-A〜D）で、**モードが無い時代の裸の batC51 は登録しない**（利用者の指示。2026-09-19）:</p>
 * <ul>
 *   <li>batC51-A〜D = AI 生図 AI 生成（モードごと。必須設定は共通の分）</li>
 *   <li>batC51（裸）は登録しない（実行の入口が無くなったため）</li>
 *   <li>batC52 = AI 画図助手（GEOMETRY_AI_ASSIST_*）</li>
 *   <li>batC53 は登録しない</li>
 * </ul>
 */
class BatchTaskRegistryTest {

    private final BatchTaskRegistry registry = new BatchTaskRegistry();

    @Test
    void batC51ModesAreTheAiFigureGenerateSteps() {
        for (FigureMode mode : FigureMode.values()) {
            BatchTaskDefinition definition = registry.findByCode(mode.taskCode());
            assertThat(definition).as(mode.taskCode()).isNotNull();
            assertThat(definition.description()).contains("AI生図");
            // 必須設定は「AI 生成」のもの（GEOMETRY_AI_SYSTEM_PROMPT など）で、前処理のものではない
            List<String> keys = definition.requiredSettings().stream()
                    .map(com.study21.admin.setting.SettingRequirement::settingKey).toList();
            assertThat(keys).contains("GEOMETRY_AI_SYSTEM_PROMPT", "GEOMETRY_AI_PROVIDER");
            assertThat(keys).doesNotContain("GEOMETRY_AI_MAX_IMAGE_MB", "GEOMETRY_AI_DEFAULT_CROP");
        }
    }

    @Test
    void legacyBatC51IsNotRegisteredAnymore() {
        // モードが無い時代の裸の batC51 は、実行の入口（画面の【再実行】）を外した時点で
        // 誰も起動できなくなったので登録しない（利用者の指示）
        assertThat(registry.findByCode("batC51")).isNull();
        // 作図モード別の 4 バッチは残る
        assertThat(registry.findAll().stream().map(BatchTaskDefinition::taskCode))
                .contains("batC51-A", "batC51-B", "batC51-C", "batC51-D")
                .doesNotContain("batC51");
    }

    @Test
    void batC52IsTheAiAssistGenerateStep() {
        BatchTaskDefinition batC52 = registry.findByCode("batC52");
        assertThat(batC52).isNotNull();
        assertThat(batC52.description()).contains("AI画図助手");
        List<String> keys = batC52.requiredSettings().stream()
                .map(com.study21.admin.setting.SettingRequirement::settingKey).toList();
        assertThat(keys).contains("GEOMETRY_AI_ASSIST_PROVIDER", "GEOMETRY_AI_ASSIST_SYSTEM_PROMPT");
    }

    @Test
    void batC53IsNotRegistered() {
        assertThat(registry.findByCode("batC53")).isNull();
    }

    @Test
    void preprocessAndValidateAreNotBatches() {
        // 前処理・検証はバッチではないので、対応するタスクコードが無い
        assertThat(registry.findByCode("batC52")).isNotNull(); // 助手（番号を再利用）
        assertThat(registry.findAll().stream()
                .filter(t -> t.description().contains("前処理") || t.description().contains("検証・確定")))
                .isEmpty();
    }
}

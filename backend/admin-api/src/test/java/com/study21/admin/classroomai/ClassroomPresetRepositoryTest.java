package com.study21.admin.classroomai;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 実 DB（PostgreSQL）に対する前置詞プリセット管理の検証。
 *
 * <p>seed 3 件（通常の授業 / 数学の授業 / 英語の授業）を壊さないこと、追加・更新・削除が
 * 正しく反映されることを確かめる。テストは {@code @Transactional} でロールバックするので
 * seed や追加した行は DB に残らない（作った行も消える）。</p>
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class ClassroomPresetRepositoryTest {

    @Autowired
    private ClassroomPresetService presetService;

    @Test
    void seedPresetsAreListed() {
        assertThat(presetService.list())
                .extracting(ClassroomPresetModels.PresetView::name)
                .contains("通常の授業", "数学の授業", "英語の授業");
        // すべて GLOBAL スコープ
        assertThat(presetService.list())
                .allSatisfy(view -> assertThat(view.scope()).isEqualTo("GLOBAL"));
    }

    @Test
    void createUpdateDeleteRoundTrip() {
        ClassroomPresetModels.PresetView created = presetService.create(
                new ClassroomPresetModels.SaveRequest("検証用プリセット", "検証用の説明", 999));
        long id = created.presetId();
        assertThat(created.scope()).isEqualTo("GLOBAL");
        assertThat(presetService.list())
                .extracting(ClassroomPresetModels.PresetView::name)
                .contains("検証用プリセット");

        ClassroomPresetModels.PresetView updated = presetService.update(id,
                new ClassroomPresetModels.SaveRequest("検証用プリセット（更新）", "説明2", 998));
        assertThat(updated.name()).isEqualTo("検証用プリセット（更新）");
        assertThat(updated.text()).isEqualTo("説明2");

        presetService.delete(id);
        assertThat(presetService.list())
                .extracting(ClassroomPresetModels.PresetView::name)
                .doesNotContain("検証用プリセット（更新）");
    }
}

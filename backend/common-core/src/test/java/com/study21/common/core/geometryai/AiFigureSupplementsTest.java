package com.study21.common.core.geometryai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 補充項目の項目名とプロンプト変数の対応（画面・受付・プロンプトで同じ名前を使う）。
 *
 * <p>確かめる接縫: **すべての補充項目に変数があること**と、名前が重複しないこと。
 * ここが崩れると「画面で選んだのに AI へ渡らない」という静かな不具合になる。</p>
 */
class AiFigureSupplementsTest {

    /** 画面が出す補充項目（`GeometryAiSupplements` の全項目）。 */
    private static final Set<String> EXPECTED_LABELS = Set.of(
            "元の名前とラベル", "再現の重点", "情報が足りないとき", "既知の値（式・点・寸法・角）",
            "座標の範囲・目盛", "式の訂正", "パラメータの値", "定義域", "表示範囲", "補助的な対象",
            "作図の目標", "問題文の訂正", "残す対象", "追加・変更する対象", "文字・式・ラベルの訂正");

    @Test
    @DisplayName("画面の補充項目にはすべてプロンプト変数がある")
    void everyLabelHasAVariable() {
        Set<String> labels = new HashSet<>();
        for (AiFigureSupplements.Item item : AiFigureSupplements.all()) {
            assertThat(item.variable()).isNotBlank();
            assertThat(AiFigureSupplements.variableOf(item.label()))
                    .as(item.label())
                    .contains(item.variable());
            labels.add(item.label());
        }
        assertThat(labels).containsExactlyInAnyOrderElementsOf(EXPECTED_LABELS);
    }

    @Test
    @DisplayName("変数名は重複しない（同じ変数へ 2 つの項目を入れない）")
    void variablesAreUnique() {
        Set<String> variables = new HashSet<>();
        for (AiFigureSupplements.Item item : AiFigureSupplements.all()) {
            assertThat(variables.add(item.variable())).as(item.variable()).isTrue();
        }
    }

    @Test
    @DisplayName("知らない項目名には変数を割り当てない")
    void unknownLabelHasNoVariable() {
        assertThat(AiFigureSupplements.variableOf("知らない項目")).isEmpty();
        assertThat(AiFigureSupplements.variableOf(null)).isEmpty();
    }

    @Test
    @DisplayName("モード別の設定キーは接頭辞つきで組み立てる（共通のキーと衝突しない）")
    void modeKeysDoNotCollideWithCommonKeys() {
        for (String mode : new String[]{"A", "B", "C", "D"}) {
            assertThat(AiFigureSettingKeys.systemPromptKey(mode)).isEqualTo("GEOMETRY_AI_" + mode + "_SYSTEM_PROMPT");
            assertThat(AiFigureSettingKeys.taskTemplateKey(mode)).isEqualTo("GEOMETRY_AI_" + mode + "_TASK_TEMPLATE");
            assertThat(AiFigureSettingKeys.keysFor(mode)).containsAll(AiFigureSettingKeys.commonKeys());
            assertThat(AiFigureSettingKeys.modeKeys(mode)).doesNotContain(AiFigureSettingKeys.SYSTEM_PROMPT);
        }
    }
}

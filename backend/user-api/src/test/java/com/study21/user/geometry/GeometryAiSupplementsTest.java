package com.study21.user.geometry;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 補充パラメータの適用と検証（作図モードと結果種別で絞る唯一の場所）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>モードと種類に**当てはまる項目だけ**を保存する（隠れた・切り替え前の項目を AI へ渡さない）</li>
 *   <li>グラフ系の項目は GRAPH / MIXED のときだけ通る（AUTO では出さない＝送らない）</li>
 *   <li>既定（共通）の項目はいちばん先に入る。元の名前は「残す」が既定で、いいえのときだけ保存する</li>
 *   <li>知らない選択肢・長すぎる文章は拒否する（画面とサーバーのずれを黙って通さない）</li>
 * </ol>
 */
class GeometryAiSupplementsTest {

    private static GeometryAiModels.SupplementInput input(
            String reproduceFocus, String whenInsufficient, String knownValues, String coordinateRange,
            String formulaCorrection, String parameters, String domain, String viewRange, Boolean showAuxiliary,
            String goal, String problemCorrection, String keepObjects, String changeObjects, String textCorrection,
            Boolean keepLabels) {
        return new GeometryAiModels.SupplementInput(reproduceFocus, whenInsufficient, knownValues, coordinateRange,
                formulaCorrection, parameters, domain, viewRange, showAuxiliary, goal, problemCorrection,
                keepObjects, changeObjects, textCorrection, keepLabels);
    }

    /** すべての項目を埋めた入力（当てはまらない項目が落ちることを見る）。 */
    private static GeometryAiModels.SupplementInput everything() {
        return input("MATH_FIRST", "ASK_FIRST", "AB=5, ∠B=90°", "x: -5..5, 刻み 1",
                "y = x^2（訂正後）", "a = 2", "すべての実数", "x: -10..10", false,
                "GIVEN_ONLY", "問題文の訂正", "円 c", "垂線を追加", "点の名前は A,B,C",
                Boolean.FALSE);
    }

    /** 目標だけを差し替えた入力（目標の値はモードで違う）。 */
    private static GeometryAiModels.SupplementInput withGoal(String goal) {
        GeometryAiModels.SupplementInput base = everything();
        return new GeometryAiModels.SupplementInput(base.reproduceFocus(), base.whenInsufficient(),
                base.knownValues(), base.coordinateRange(), base.formulaCorrection(), base.parameters(),
                base.domain(), base.viewRange(), base.showAuxiliary(), goal, base.problemCorrection(),
                base.keepObjects(), base.changeObjects(), base.textCorrection(), base.keepLabels());
    }

    private static Map<String, String> rows(String json) {
        return GeometryAiSupplements.rows(json);
    }

    @Test
    @DisplayName("A（幾何図形）では A の項目だけを保存し、グラフの項目は落とす")
    void modeAFiltersByOutputType() {
        Map<String, String> geometry = rows(GeometryAiSupplements.toJson("A", "GEOMETRY", everything()));
        assertThat(geometry).containsOnlyKeys(
                GeometryAiSupplements.LABEL_KEEP_LABELS,
                GeometryAiSupplements.LABEL_REPRODUCE_FOCUS,
                GeometryAiSupplements.LABEL_WHEN_INSUFFICIENT,
                GeometryAiSupplements.LABEL_KNOWN_VALUES);
        assertThat(geometry.get(GeometryAiSupplements.LABEL_REPRODUCE_FOCUS)).isEqualTo("数学的な関係を優先");
        assertThat(geometry.get(GeometryAiSupplements.LABEL_WHEN_INSUFFICIENT)).isEqualTo("確認してから進める");
        assertThat(geometry.get(GeometryAiSupplements.LABEL_KEEP_LABELS)).contains("いいえ");
        // B・C・D の項目は入らない
        assertThat(geometry).doesNotContainKeys(
                GeometryAiSupplements.LABEL_FORMULA_CORRECTION,
                GeometryAiSupplements.LABEL_PROBLEM_CORRECTION,
                GeometryAiSupplements.LABEL_KEEP_OBJECTS);

        Map<String, String> graph = rows(GeometryAiSupplements.toJson("A", "GRAPH", everything()));
        assertThat(graph).containsKey(GeometryAiSupplements.LABEL_COORDINATE_RANGE);
        assertThat(graph.get(GeometryAiSupplements.LABEL_COORDINATE_RANGE)).isEqualTo("x: -5..5, 刻み 1");
    }

    @Test
    @DisplayName("AUTO（自動判定）では種類で変わる項目を出さない（隠れた項目を送らない）")
    void autoDoesNotCarryTypeSpecificItems() {
        Map<String, String> rows = rows(GeometryAiSupplements.toJson("A", "AUTO", everything()));
        assertThat(rows).doesNotContainKey(GeometryAiSupplements.LABEL_COORDINATE_RANGE);
        assertThat(rows).containsKey(GeometryAiSupplements.LABEL_REPRODUCE_FOCUS);
    }

    @Test
    @DisplayName("B（数式からグラフ）では式・パラメータ・定義域・表示範囲と補助の指定を保存する")
    void modeBKeepsItsOwnItems() {
        Map<String, String> rows = rows(GeometryAiSupplements.toJson("B", "GRAPH", everything()));
        assertThat(rows).containsOnlyKeys(
                GeometryAiSupplements.LABEL_KEEP_LABELS,
                GeometryAiSupplements.LABEL_FORMULA_CORRECTION,
                GeometryAiSupplements.LABEL_PARAMETERS,
                GeometryAiSupplements.LABEL_DOMAIN,
                GeometryAiSupplements.LABEL_VIEW_RANGE,
                GeometryAiSupplements.LABEL_AUXILIARY);
        assertThat(rows.get(GeometryAiSupplements.LABEL_DOMAIN)).isEqualTo("すべての実数");
        assertThat(rows.get(GeometryAiSupplements.LABEL_AUXILIARY)).isEqualTo("追加しない（答えの性質を持つものは足さない）");
    }

    @Test
    @DisplayName("C（文章の条件）では目標・問題文の訂正と、グラフのときだけパラメータ・定義域・表示範囲を保存する")
    void modeCKeepsItsOwnItems() {
        Map<String, String> mixed = rows(GeometryAiSupplements.toJson("C", "MIXED", everything()));
        assertThat(mixed).containsKeys(
                GeometryAiSupplements.LABEL_GOAL,
                GeometryAiSupplements.LABEL_PROBLEM_CORRECTION,
                GeometryAiSupplements.LABEL_WHEN_INSUFFICIENT,
                GeometryAiSupplements.LABEL_PARAMETERS,
                GeometryAiSupplements.LABEL_DOMAIN,
                GeometryAiSupplements.LABEL_VIEW_RANGE);
        assertThat(mixed.get(GeometryAiSupplements.LABEL_GOAL)).isEqualTo("与えられた条件だけを図にする");

        Map<String, String> geometry = rows(GeometryAiSupplements.toJson("C", "GEOMETRY", everything()));
        assertThat(geometry).doesNotContainKeys(
                GeometryAiSupplements.LABEL_PARAMETERS,
                GeometryAiSupplements.LABEL_DOMAIN,
                GeometryAiSupplements.LABEL_VIEW_RANGE);
    }

    @Test
    @DisplayName("D（文章と図）では目標・残す対象・追加変更・訂正を保存する")
    void modeDKeepsItsOwnItems() {
        Map<String, String> rows = rows(GeometryAiSupplements.toJson("D", "GEOMETRY", withGoal("TRANSFORM")));
        assertThat(rows).containsKeys(
                GeometryAiSupplements.LABEL_GOAL,
                GeometryAiSupplements.LABEL_KEEP_OBJECTS,
                GeometryAiSupplements.LABEL_CHANGE_OBJECTS,
                GeometryAiSupplements.LABEL_TEXT_CORRECTION);
        assertThat(rows.get(GeometryAiSupplements.LABEL_GOAL)).isEqualTo("文字の条件に合わせて補完・変換する");
        assertThat(rows.get(GeometryAiSupplements.LABEL_KEEP_OBJECTS)).isEqualTo("円 c");
    }

    @Test
    @DisplayName("既定では元の名前とラベルを残すので、何も保存しない（＝指定なし）")
    void keepsDefaultsEmpty() {
        GeometryAiModels.SupplementInput onlyDefault = input(null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, Boolean.TRUE);
        assertThat(GeometryAiSupplements.toJson("A", "GEOMETRY", onlyDefault)).isNull();
        assertThat(GeometryAiSupplements.toJson("A", "GEOMETRY", null)).isNull();
        assertThat(GeometryAiSupplements.toJson(null, "AUTO", everything())).isNull();
    }

    @Test
    @DisplayName("知らない選択肢は拒否する（画面とサーバーのずれを黙って通さない）")
    void rejectsUnknownChoice() {
        GeometryAiModels.SupplementInput bad = input("FASTEST", null, null, null, null, null, null, null,
                null, null, null, null, null, null, null);
        assertThatThrownBy(() -> GeometryAiSupplements.toJson("A", "GEOMETRY", bad))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("再現の重点");
    }

    @Test
    @DisplayName("長すぎる文章は拒否する")
    void rejectsTooLongText() {
        GeometryAiModels.SupplementInput long_ = input(null, null, "あ".repeat(GeometryAiSupplements.TEXT_MAX + 1),
                null, null, null, null, null, null, null, null, null, null, null, null);
        assertThatThrownBy(() -> GeometryAiSupplements.toJson("A", "GEOMETRY", long_))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("文字以内");
    }

    @Test
    @DisplayName("保存した JSON は画面で読み直せる（項目名は画面の見出しと同じ）")
    void rowsRoundTrip() {
        String json = GeometryAiSupplements.toJson("D", "MIXED", withGoal("REPRODUCE"));
        Map<String, String> rows = rows(json);
        assertThat(rows).isNotEmpty();
        assertThat(rows.keySet()).allMatch(key -> key.contains("作図") || key.contains("対象") || key.contains("名前")
                || key.contains("パラメータ") || key.contains("定義域") || key.contains("表示範囲")
                || key.contains("訂正") || key.contains("情報"));
        // 引用符・改行を含む文章でも壊れない
        GeometryAiModels.SupplementInput quoted = input(null, null, "∠B = 90° と \"AB\" = 5\n改行あり",
                null, null, null, null, null, null, null, null, null, null, null, null);
        Map<String, String> reread = rows(GeometryAiSupplements.toJson("A", "GEOMETRY", quoted));
        assertThat(reread.get(GeometryAiSupplements.LABEL_KNOWN_VALUES)).contains("改行あり").contains("\"AB\"");
    }
}

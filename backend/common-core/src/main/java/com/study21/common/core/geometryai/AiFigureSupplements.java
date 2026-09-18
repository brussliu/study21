package com.study21.common.core.geometryai;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 補充パラメータ（画面の任意項目）の**項目名とプロンプト変数の唯一の定義**。
 *
 * <p>要求行には**日本語の項目名 → 値**の JSON で保存する（例
 * <code>{"再現の重点":"数学的な関係を優先"}</code>）。項目名は画面の見出しと同じなので、
 * AI へのプロンプトと画面の確認が食い違わない。</p>
 *
 * <p>保存するのは user-api（{@code GeometryAiSupplements}）、プロンプトへ展開するのは
 * admin-api（{@code FigurePromptBuilder}）で、**別のモジュール**になる。項目名がずれると
 * 「画面で選んだのに AI へ渡らない」という静かな不具合になるため、名前と変数の対応を
 * ここ（common-core）で 1 回だけ決めて両方から使う。</p>
 *
 * <p>変数は**モードごとに意味が同じものは同じ名前**にしてある（{@code parameters} /
 * {@code domain} / {@code viewRange} は B・C・D で共通、{@code whenInsufficient} は A・C で共通）。</p>
 */
public final class AiFigureSupplements {

    /** 1 項目（日本語の項目名と、プロンプトの変数名）。 */
    public record Item(String label, String variable) {
    }

    // ---------------------------------------------------------------- 項目名

    public static final String LABEL_KEEP_LABELS = "元の名前とラベル";
    public static final String LABEL_REPRODUCE_FOCUS = "再現の重点";
    public static final String LABEL_WHEN_INSUFFICIENT = "情報が足りないとき";
    public static final String LABEL_KNOWN_VALUES = "既知の値（式・点・寸法・角）";
    public static final String LABEL_COORDINATE_RANGE = "座標の範囲・目盛";
    public static final String LABEL_FORMULA_CORRECTION = "式の訂正";
    public static final String LABEL_PARAMETERS = "パラメータの値";
    public static final String LABEL_DOMAIN = "定義域";
    public static final String LABEL_VIEW_RANGE = "表示範囲";
    public static final String LABEL_AUXILIARY = "補助的な対象";
    public static final String LABEL_GOAL = "作図の目標";
    public static final String LABEL_PROBLEM_CORRECTION = "問題文の訂正";
    public static final String LABEL_KEEP_OBJECTS = "残す対象";
    public static final String LABEL_CHANGE_OBJECTS = "追加・変更する対象";
    public static final String LABEL_TEXT_CORRECTION = "文字・式・ラベルの訂正";

    // ---------------------------------------------------------------- 変数名

    public static final String VAR_KEEP_LABELS = "keepLabels";
    public static final String VAR_REPRODUCE_FOCUS = "reproduceFocus";
    public static final String VAR_WHEN_INSUFFICIENT = "whenInsufficient";
    public static final String VAR_KNOWN_VALUES = "knownValues";
    public static final String VAR_COORDINATE_RANGE = "coordinateRange";
    public static final String VAR_FORMULA_CORRECTION = "formulaCorrection";
    public static final String VAR_PARAMETERS = "parameters";
    public static final String VAR_DOMAIN = "domain";
    public static final String VAR_VIEW_RANGE = "viewRange";
    public static final String VAR_AUXILIARY_OBJECTS = "auxiliaryObjects";
    public static final String VAR_GOAL = "goal";
    public static final String VAR_PROBLEM_CORRECTION = "problemCorrection";
    public static final String VAR_KEEP_OBJECTS = "keepObjects";
    public static final String VAR_CHANGE_OBJECTS = "changeObjects";
    public static final String VAR_TEXT_CORRECTION = "textCorrection";

    /** 項目名 → 変数（登録順。設定ページの案内にもこの順で出す）。 */
    private static final List<Item> ITEMS = List.of(
            new Item(LABEL_KEEP_LABELS, VAR_KEEP_LABELS),
            new Item(LABEL_REPRODUCE_FOCUS, VAR_REPRODUCE_FOCUS),
            new Item(LABEL_WHEN_INSUFFICIENT, VAR_WHEN_INSUFFICIENT),
            new Item(LABEL_KNOWN_VALUES, VAR_KNOWN_VALUES),
            new Item(LABEL_COORDINATE_RANGE, VAR_COORDINATE_RANGE),
            new Item(LABEL_FORMULA_CORRECTION, VAR_FORMULA_CORRECTION),
            new Item(LABEL_PARAMETERS, VAR_PARAMETERS),
            new Item(LABEL_DOMAIN, VAR_DOMAIN),
            new Item(LABEL_VIEW_RANGE, VAR_VIEW_RANGE),
            new Item(LABEL_AUXILIARY, VAR_AUXILIARY_OBJECTS),
            new Item(LABEL_GOAL, VAR_GOAL),
            new Item(LABEL_PROBLEM_CORRECTION, VAR_PROBLEM_CORRECTION),
            new Item(LABEL_KEEP_OBJECTS, VAR_KEEP_OBJECTS),
            new Item(LABEL_CHANGE_OBJECTS, VAR_CHANGE_OBJECTS),
            new Item(LABEL_TEXT_CORRECTION, VAR_TEXT_CORRECTION));

    private static final Map<String, String> VARIABLE_BY_LABEL = new LinkedHashMap<>();

    static {
        for (Item item : ITEMS) {
            VARIABLE_BY_LABEL.put(item.label(), item.variable());
        }
    }

    private AiFigureSupplements() {
    }

    /** すべての項目（登録順）。 */
    public static List<Item> all() {
        return ITEMS;
    }

    /** 項目名に対応する変数（知らない項目名は空。**勝手に変数を作らない**）。 */
    public static Optional<String> variableOf(String label) {
        if (label == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(VARIABLE_BY_LABEL.get(label.trim()));
    }

    /** 「元の名前とラベル」で「いいえ」を表す値（`keepLabels` の判定に使う）。 */
    public static final String LABELS_NO = "いいえ";
}

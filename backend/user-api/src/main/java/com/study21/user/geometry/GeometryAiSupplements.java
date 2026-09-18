package com.study21.user.geometry;

import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.geometryai.AiFigureSupplements;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 「作図方法（A〜D）」と「作成する図の種類」に応じた**補充パラメータの適用と検証**（唯一の定義）。
 *
 * <p>画面はモードと種類で項目を出し分けるが、**サーバー側でも同じ規則で絞る**。
 * 当てはまらない項目は保存しない（＝AI へ渡さない。設計 §2「隠れた不要なパラメータを送らない」）。</p>
 *
 * <p>保存する形は**日本語の項目名 → 値**の JSON（例 {@code {"再現の重点": "数学的な関係を優先"}}）。
 * 画面に出ている見出しと同じ名前を使うので、AI へのプロンプトと画面の確認が食い違わない。</p>
 *
 * <p>空の項目は保存しない（既定の意味は「指定なし」）。{@code 元の名前とラベル} だけは
 * 既定が「はい」なので、明示的に「いいえ」のときだけ保存する。</p>
 */
public final class GeometryAiSupplements {

    // ---------------------------------------------------------------- コード値

    /** A: 再現の重点。 */
    public static final String FOCUS_MATH_FIRST = "MATH_FIRST";
    public static final String FOCUS_APPEARANCE_FIRST = "APPEARANCE_FIRST";
    /** A・C: 情報が足りないときの扱い。 */
    public static final String INSUFFICIENT_ASK_FIRST = "ASK_FIRST";
    public static final String INSUFFICIENT_ALLOW_APPROXIMATE = "ALLOW_APPROXIMATE";
    /** C: 作図の目標。 */
    public static final String GOAL_GIVEN_ONLY = "GIVEN_ONLY";
    public static final String GOAL_COMPLETE_CONSTRUCTION = "COMPLETE_CONSTRUCTION";
    /** D: 作図の目標。 */
    public static final String GOAL_REPRODUCE = "REPRODUCE";
    public static final String GOAL_TRANSFORM = "TRANSFORM";

    // ---------------------------------------------------------------- 項目名

    // 項目名は **common-core の 1 か所**（AiFigureSupplements）で決める。admin-api の
    // FigurePromptBuilder が同じ定義で「項目名 → プロンプトの変数」を引くので、
    // 片方だけ直して「画面で選んだのに AI へ渡らない」となることがない。
    public static final String LABEL_KEEP_LABELS = AiFigureSupplements.LABEL_KEEP_LABELS;
    public static final String LABEL_REPRODUCE_FOCUS = AiFigureSupplements.LABEL_REPRODUCE_FOCUS;
    public static final String LABEL_WHEN_INSUFFICIENT = AiFigureSupplements.LABEL_WHEN_INSUFFICIENT;
    public static final String LABEL_KNOWN_VALUES = AiFigureSupplements.LABEL_KNOWN_VALUES;
    public static final String LABEL_COORDINATE_RANGE = AiFigureSupplements.LABEL_COORDINATE_RANGE;
    public static final String LABEL_FORMULA_CORRECTION = AiFigureSupplements.LABEL_FORMULA_CORRECTION;
    public static final String LABEL_PARAMETERS = AiFigureSupplements.LABEL_PARAMETERS;
    public static final String LABEL_DOMAIN = AiFigureSupplements.LABEL_DOMAIN;
    public static final String LABEL_VIEW_RANGE = AiFigureSupplements.LABEL_VIEW_RANGE;
    public static final String LABEL_AUXILIARY = AiFigureSupplements.LABEL_AUXILIARY;
    public static final String LABEL_GOAL = AiFigureSupplements.LABEL_GOAL;
    public static final String LABEL_PROBLEM_CORRECTION = AiFigureSupplements.LABEL_PROBLEM_CORRECTION;
    public static final String LABEL_KEEP_OBJECTS = AiFigureSupplements.LABEL_KEEP_OBJECTS;
    public static final String LABEL_CHANGE_OBJECTS = AiFigureSupplements.LABEL_CHANGE_OBJECTS;
    public static final String LABEL_TEXT_CORRECTION = AiFigureSupplements.LABEL_TEXT_CORRECTION;

    /** 文字入力の上限（画面と同じ値。長すぎるプロンプトを防ぐ）。 */
    public static final int TEXT_MAX = 500;

    private GeometryAiSupplements() {
    }

    /** 適用する項目（1 つ分）。 */
    private record Item(String label, String value) {
    }

    /**
     * モードと結果種別に当てはまる項目だけを取り出して、**保存する JSON** を作る。
     *
     * @param mode          A / B / C / D（大文字）
     * @param resultType    AUTO / GEOMETRY / GRAPH / MIXED
     * @param input         画面が送ってきた補充（当てはまらない項目は無視する）
     */
    public static String toJson(String mode, String resultType, GeometryAiModels.SupplementInput input) {
        List<Item> items = applicable(mode, resultType, input);
        if (items.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder("{");
        for (int index = 0; index < items.size(); index += 1) {
            if (index > 0) {
                builder.append(',');
            }
            builder.append(quote(items.get(index).label())).append(':').append(quote(items.get(index).value()));
        }
        return builder.append('}').toString();
    }

    /** 適用する項目の一覧（項目名と値）。 */
    static List<Item> applicable(String mode, String resultType, GeometryAiModels.SupplementInput input) {
        List<Item> items = new ArrayList<>();
        if (input == null) {
            return items;
        }
        String normalizedMode = mode == null ? "" : mode.trim().toUpperCase(Locale.ROOT);
        String type = resultType == null || resultType.isBlank()
                ? "AUTO" : resultType.trim().toUpperCase(Locale.ROOT);

        // モードが無い（歴史的な要求）ときは補充を取らない
        if (!"A".equals(normalizedMode) && !"B".equals(normalizedMode)
                && !"C".equals(normalizedMode) && !"D".equals(normalizedMode)) {
            return items;
        }

        // 公共: 元の名前とラベル（既定は「はい」なので、いいえのときだけ送る）
        if (Boolean.FALSE.equals(input.keepLabels())) {
            items.add(new Item(LABEL_KEEP_LABELS, "いいえ（元の名前・ラベルにこだわらない）"));
        }

        boolean graphLike = "GRAPH".equals(type) || "MIXED".equals(type);
        switch (normalizedMode) {
            case "A" -> {
                addChoice(items, LABEL_REPRODUCE_FOCUS, input.reproduceFocus(), Map.of(
                        FOCUS_MATH_FIRST, "数学的な関係を優先",
                        FOCUS_APPEARANCE_FIRST, "外観・配置を優先"));
                addChoice(items, LABEL_WHEN_INSUFFICIENT, input.whenInsufficient(), Map.of(
                        INSUFFICIENT_ASK_FIRST, "確認してから進める",
                        INSUFFICIENT_ALLOW_APPROXIMATE, "近似（明記する）で進めてよい"));
                addText(items, LABEL_KNOWN_VALUES, input.knownValues());
                if (graphLike) {
                    addText(items, LABEL_COORDINATE_RANGE, input.coordinateRange());
                }
            }
            case "B" -> {
                addText(items, LABEL_FORMULA_CORRECTION, input.formulaCorrection());
                addText(items, LABEL_PARAMETERS, input.parameters());
                addText(items, LABEL_DOMAIN, input.domain());
                addText(items, LABEL_VIEW_RANGE, input.viewRange());
                addBoolean(items, LABEL_AUXILIARY, input.showAuxiliary(),
                        "追加する", "追加しない（答えの性質を持つものは足さない）");
            }
            case "C" -> {
                addChoice(items, LABEL_GOAL, input.goal(), Map.of(
                        GOAL_GIVEN_ONLY, "与えられた条件だけを図にする",
                        GOAL_COMPLETE_CONSTRUCTION, "問題が求める作図を完成させる"));
                addText(items, LABEL_PROBLEM_CORRECTION, input.problemCorrection());
                addChoice(items, LABEL_WHEN_INSUFFICIENT, input.whenInsufficient(), Map.of(
                        INSUFFICIENT_ASK_FIRST, "確認してから進める",
                        INSUFFICIENT_ALLOW_APPROXIMATE, "近似（明記する）で進めてよい"));
                if (graphLike) {
                    addText(items, LABEL_PARAMETERS, input.parameters());
                    addText(items, LABEL_DOMAIN, input.domain());
                    addText(items, LABEL_VIEW_RANGE, input.viewRange());
                }
            }
            case "D" -> {
                addChoice(items, LABEL_GOAL, input.goal(), Map.of(
                        GOAL_REPRODUCE, "元の図をそのまま再現する",
                        GOAL_TRANSFORM, "文字の条件に合わせて補完・変換する"));
                addText(items, LABEL_KEEP_OBJECTS, input.keepObjects());
                addText(items, LABEL_CHANGE_OBJECTS, input.changeObjects());
                addText(items, LABEL_TEXT_CORRECTION, input.textCorrection());
                if (graphLike) {
                    addText(items, LABEL_PARAMETERS, input.parameters());
                    addText(items, LABEL_DOMAIN, input.domain());
                    addText(items, LABEL_VIEW_RANGE, input.viewRange());
                }
            }
            default -> {
                // モードが無い（歴史的な要求）ときは補充を取らない
            }
        }
        return items;
    }

    /** 選択肢の値。知らない値は拒否する（画面とサーバーのずれを黙って通さない）。 */
    private static void addChoice(List<Item> items, String label, String value, Map<String, String> labels) {
        if (value == null || value.isBlank()) {
            return;
        }
        String text = labels.get(value.trim().toUpperCase(Locale.ROOT));
        if (text == null) {
            throw new ValidationException("「" + label + "」の指定が正しくありません: " + value);
        }
        items.add(new Item(label, text));
    }

    /** はい・いいえの項目。既定と同じ値は保存しない（指定なしと同じ意味にする）。 */
    private static void addBoolean(List<Item> items, String label, Boolean value, String whenTrue, String whenFalse) {
        if (value == null) {
            return;
        }
        items.add(new Item(label, value ? whenTrue : whenFalse));
    }

    /** 自由記述の項目。 */
    private static void addText(List<Item> items, String label, String value) {
        if (value == null || value.isBlank()) {
            return;
        }
        String text = value.trim();
        if (text.length() > TEXT_MAX) {
            throw new ValidationException("「" + label + "」は " + TEXT_MAX + " 文字以内で入力してください。");
        }
        items.add(new Item(label, text));
    }

    /** JSON の文字列を組み立てる（キーは日本語なので必ず引用する）。 */
    private static String quote(String value) {
        StringBuilder builder = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> builder.append("\\\"");
                case '\\' -> builder.append("\\\\");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> {
                    if (c < 0x20) {
                        builder.append(String.format("\\u%04x", (int) c));
                    } else {
                        builder.append(c);
                    }
                }
            }
        }
        return builder.append('"').toString();
    }

    /** 「情報が足りないとき」の既定（画面とサーバーで同じ: 確認してから進める）。 */
    public static String defaultWhenInsufficient() {
        return INSUFFICIENT_ASK_FIRST;
    }

    /** 保存済みの補充 JSON を行の一覧にする（画面の確認表示と詳細 API が使う）。 */
    public static Map<String, String> rows(String json) {
        Map<String, String> rows = new LinkedHashMap<>();
        if (json == null || json.isBlank()) {
            return rows;
        }
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var root = mapper.readTree(json);
            if (root != null && root.isObject()) {
                root.fields().forEachRemaining(field -> {
                    if (field.getValue() != null && !field.getValue().isNull()) {
                        rows.put(field.getKey(), field.getValue().asText());
                    }
                });
            }
        } catch (Exception cause) {
            // 読めない JSON は空として扱う（画面は「指定なし」を出す）
            return rows;
        }
        return rows;
    }
}

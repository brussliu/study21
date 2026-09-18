package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A〜D の出力 DTO が**共有する数学・作図の構造**（DTO が唯一の定義）。
 *
 * <p>ここに置く型は「幾何図形だけ」のものではない。関数・方程式・定義域・表示範囲も同じ語彙で持つ
 * （A・C・D を幾何だけに閉じないため。設計 §7）。</p>
 *
 * <p>値の意味は 3 つを必ず区別する（設計 §8）:</p>
 * <ul>
 *   <li>数学的な関係（{@link Relation}）</li>
 *   <li>関数・方程式と定義域（{@link Expression} / {@link Domain}）</li>
 *   <li>画面の表示範囲（{@link ViewWindow}）</li>
 *   <li>原図から読み取った情報・文字の条件・作図のための導出・近似の仮定
 *       （{@link Source} で区別する）</li>
 * </ul>
 *
 * <p>レコードを使うのは、値が「読むだけの構造」であり、DTO から JSON Schema を生成して
 * プロンプトへ渡す（＝定義を 2 か所に書かない）ため。日本語のキーはプロンプトの日本語と揃える。</p>
 */
public final class FigureParts {

    private FigureParts() {
    }

    /* ------------------------------------------------------------ 語彙（列挙） */

    /** 値の出どころ。**原図の情報と、作図のために導いた値と、近似の仮定を混ぜない。** */
    public enum Source {

        /** 画像から読み取った情報。 */
        FROM_IMAGE,
        /** 文章（問題文・式・条件）に書かれている情報。 */
        FROM_TEXT,
        /** 作図のために計算・導出した値。 */
        DERIVED,
        /** 情報が足りず置いた近似・仮定（{@code 近似仮定} と対応させる）。 */
        ASSUMED
    }

    /** その値が厳密か近似か。 */
    public enum Precision {

        /** 厳密（与えられた条件・式から定まる）。 */
        EXACT,
        /** 近似（見た目からの推定・仮定）。 */
        APPROXIMATE
    }

    /** 作図オブジェクトの種類（幾何に関数・曲線も含む）。 */
    public enum ObjectKind {

        POINT, SEGMENT, LINE, RAY, VECTOR, POLYGON, CIRCLE, ARC, CONIC,
        /** 関数（{@code f(x) = …}）。 */
        FUNCTION,
        /** 媒介変数・陰関数などの曲線。 */
        CURVE,
        ANGLE, TEXT,
        /** 長さ・面積・角の大きさなどの測定量。 */
        MEASURE,
        OTHER
    }

    /** 条件の状態。 */
    public enum ConditionState {

        /** 与えられた条件（問題文・画像・利用者の入力にある）。 */
        GIVEN,
        /** 作図のために導いた条件。 */
        DERIVED,
        /** 作図の中で確かめられる条件（検証の対象）。 */
        VERIFIED,
        /** 仮に置いた条件（近似）。 */
        ASSUMED,
        /** ほかの条件と両立しない。 */
        CONTRADICTED
    }

    /** 幾何的な関係の種類。 */
    public enum RelationKind {

        PARALLEL, PERPENDICULAR, EQUAL_LENGTH, EQUAL_ANGLE, MIDPOINT,
        TANGENT, ON_CURVE, COLLINEAR, CONGRUENT, SIMILAR, INTERSECT, OTHER
    }

    /** 曲線・軸・目盛の種類。 */
    public enum CurveKind {

        FUNCTION, IMPLICIT, PARAMETRIC, AXIS, GRID, OTHER
    }

    /** 原図から読み取った既知の値の種類。 */
    public enum KnownValueKind {

        FORMULA, POINT, LENGTH, ANGLE, AREA, COORDINATE, OTHER
    }

    /** 式に出てくる記号の種類。 */
    public enum SymbolKind {

        VARIABLE, PARAMETER, CONSTANT
    }

    /** 式の種類。 */
    public enum ExpressionKind {

        FUNCTION, EQUATION, INEQUALITY, PARAMETRIC, OTHER
    }

    /** 利用者への質問の答えの形。 */
    public enum QuestionKind {

        /** 自由記述。 */
        TEXT,
        /** 数値。 */
        NUMBER,
        /** 選択肢から 1 つ。 */
        CHOICE,
        /** はい・いいえ（続けてよいかの確認）。 */
        CONFIRM
    }

    /** 検証の対象。 */
    public enum VerificationKind {

        /** オブジェクトが作られたか。 */
        OBJECT_CREATED,
        /** 数値が条件を満たすか。 */
        NUMERIC,
        /** 幾何的な関係が成り立つか。 */
        GEOMETRIC,
        /** 関数と定義域が正しいか。 */
        DOMAIN,
        /** 表示範囲がキーになる対象を覆っているか。 */
        VIEW,
        /** 保存して開き直しても同じか。 */
        REOPEN
    }

    /** 元の図に対する変更の種類。 */
    public enum ChangeKind {

        ADD, MODIFY, KEEP, REMOVE
    }

    /* ------------------------------------------------------------ 共通の小さな構造 */

    /** 明確な条件（与えられた条件・導いた条件）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "明確な条件。数値・関係・定義域などを 1 つずつ")
    public record Condition(
            @Schema(description = "条件の内容（例: AB = 5, ∠B = 90°, f の定義域は x > 0）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("内容") String content,
            @Schema(description = "値の出どころ", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("出典") Source source,
            @Schema(description = "条件の状態", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("状態") ConditionState state,
            @Schema(description = "なぜそう言えるか（原図のどこ・どの式から）")
            @JsonProperty("根拠") String basis) {
    }

    /** 値つきの項目（導出結果・既知の値など）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "値つきの項目")
    public record Value(
            @Schema(description = "何の値か（例: 頂点の座標、辺の長さ）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("内容") String content,
            @Schema(description = "値（数値・座標・式）")
            @JsonProperty("値") String value,
            @Schema(description = "単位（長さなら cm など。無ければ空）")
            @JsonProperty("単位") String unit,
            @Schema(description = "値の出どころ", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("出典") Source source,
            @Schema(description = "厳密か近似か", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("精度") Precision precision,
            @Schema(description = "根拠（計算式・使った条件）")
            @JsonProperty("根拠") String basis) {
    }

    /**
     * 近似の仮定。
     *
     * <p><strong>近似は「明確な数値・数学的な条件」を上書きできない</strong>（設計 §3）。
     * 近似で置き換えた値はここに必ず書き、利用者が確認できるようにする。</p>
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "近似の仮定（情報が足りないときに置いた見た目・位置の仮定）")
    public record Approximation(
            @Schema(description = "何を近似したか", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("内容") String content,
            @Schema(description = "近似した理由（何が読み取れなかったか）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("理由") String reason,
            @Schema(description = "作図への影響（どのオブジェクトが変わるか）")
            @JsonProperty("影響") String impact) {
    }

    /** 利用者への質問（情報が足りないとき）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "利用者への質問（判定が NEEDS_INPUT のときに必ず 1 つ以上）")
    public record Question(
            @Schema(description = "質問の識別子（q1, q2 …）。利用者の回答と対応させる",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("ID") String id,
            @Schema(description = "日本語の質問文", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("質問") String question,
            @Schema(description = "選択肢（回答の形が CHOICE のとき）")
            @JsonProperty("選択肢") List<String> options,
            @Schema(description = "答えの形", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("回答の形") QuestionKind answerKind) {
    }

    /** 検証の目標（サーバーが作図を確かめるときの期待値）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "検証の目標（作図が条件を満たしているかを確かめる対象）")
    public record VerificationTarget(
            @Schema(description = "対象（オブジェクト名・条件）", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("対象") String target,
            @Schema(description = "何を確かめるか", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("種類") VerificationKind kind,
            @Schema(description = "期待する値・関係（例: 5, 平行, すべての実数）")
            @JsonProperty("期待") String expected,
            @Schema(description = "許容する誤差（数値のとき。無ければ空）")
            @JsonProperty("許容") String tolerance) {
    }

    /* ------------------------------------------------------------ 作図の構造 */

    /** 作図オブジェクト（**A〜D が共有する作図の構造**。ここへ写せないものは作図できない）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "作図オブジェクト（点・線・円・多角形・関数・曲線など）")
    public record MathObject(
            @Schema(description = "名前（英数字。画面とコマンドで同じ名前を使う）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("名前") String name,
            @Schema(description = "種類", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("種類") ObjectKind kind,
            @Schema(description = "定義（コマンドと同じ内容。例: Circle(A, B), f(x) = x^2）")
            @JsonProperty("定義") String definition,
            @Schema(description = "値の出どころ", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("出典") Source source,
            @Schema(description = "厳密か近似か", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("精度") Precision precision,
            @Schema(description = "根拠（原図のどこ・どの条件から）")
            @JsonProperty("根拠") String basis,
            @Schema(description = "依存するオブジェクトの名前（先に作るもの）")
            @JsonProperty("依存") List<String> dependsOn) {
    }

    /** 画像から読み取ったオブジェクトと、その見た目・配置（A・D 用）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "画像から読み取ったオブジェクト（見た目と配置）")
    public record ImageObject(
            @Schema(description = "名前（作図オブジェクトと同じ名前）", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("名前") String name,
            @Schema(description = "種類", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("種類") ObjectKind kind,
            @Schema(description = "見た目（線の種類・色・太さなど、作図に必要な範囲）")
            @JsonProperty("見た目") String appearance,
            @Schema(description = "配置（ほかのオブジェクトとの位置関係。座標が読めないときに使う）")
            @JsonProperty("配置") String position,
            @Schema(description = "原図のラベル（A, B, x など）")
            @JsonProperty("ラベル") String label,
            @Schema(description = "厳密か近似か", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("精度") Precision precision,
            @Schema(description = "根拠（原図のどこに見えるか）")
            @JsonProperty("根拠") String basis) {
    }

    /** 名前とラベルの対応（**元の名前を残す**ための情報。既定は「残す」）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "原図の名前・ラベルと、作図での表示")
    public record Label(
            @Schema(description = "作図での名前", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("名前") String name,
            @Schema(description = "表示する文字（原図のラベル。例: A, B, x, ∠ABC）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("表示文字") String text,
            @Schema(description = "表示位置（原図での位置）")
            @JsonProperty("位置") String position,
            @Schema(description = "備考（元と変えた場合の理由）")
            @JsonProperty("備考") String note) {
    }

    /** 幾何的な関係（条件そのもの。**目分量の座標でごまかさない**）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "幾何的な関係（平行・垂直・等しい・中点・接する など）")
    public record Relation(
            @Schema(description = "関係の種類", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("種類") RelationKind kind,
            @Schema(description = "関係するオブジェクトの名前（2 つ以上）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("対象") List<String> objects,
            @Schema(description = "値（等しい長さ・角の大きさなど。無ければ空）")
            @JsonProperty("値") String value,
            @Schema(description = "単位")
            @JsonProperty("単位") String unit,
            @Schema(description = "厳密か近似か", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("精度") Precision precision,
            @Schema(description = "根拠（原図の情報・文章の条件・導出）")
            @JsonProperty("根拠") String basis) {
    }

    /** 曲線・座標軸・目盛（**画面の表示範囲とは別**）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "曲線・座標軸・目盛")
    public record Curve(
            @Schema(description = "種類", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("種類") CurveKind kind,
            @Schema(description = "式（例: y = x^2, x^2 + y^2 = 4）。軸・目盛では空")
            @JsonProperty("式") String expression,
            @Schema(description = "どの範囲を描くか（曲線の描画範囲。定義域とは別）")
            @JsonProperty("範囲") String range,
            @Schema(description = "目盛の刻み（軸・目盛のとき）")
            @JsonProperty("刻み") String step,
            @Schema(description = "根拠（原図のどこ・どの条件から）")
            @JsonProperty("根拠") String basis) {
    }

    /** 原図から読み取った既知の式・点・長さ・角。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "原図から読み取った既知の式・点・長さ・角")
    public record KnownValue(
            @Schema(description = "種類", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("種類") KnownValueKind kind,
            @Schema(description = "内容（例: BC = 4, ∠B = 90°, y = 2x + 1）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("内容") String content,
            @Schema(description = "値（数値・座標）")
            @JsonProperty("値") String value,
            @Schema(description = "値の出どころ", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("出典") Source source,
            @Schema(description = "厳密か近似か", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("精度") Precision precision,
            @Schema(description = "根拠（原図のどこに書いてあるか）")
            @JsonProperty("根拠") String basis) {
    }

    /** 式（元の書き方と正規化した形）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "式（元の書き方と、作図に使う正規化した形）")
    public record Expression(
            @Schema(description = "元の式（利用者・原図の書き方のまま）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("元の式") String raw,
            @Schema(description = "正規化した式（コマンドに使う形。例: f(x) = x^2 - 2*x）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("正規化") String normalized,
            @Schema(description = "式の種類", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("種類") ExpressionKind kind,
            @Schema(description = "変数（例: [\"x\"]）")
            @JsonProperty("変数") List<String> variables,
            @Schema(description = "根拠（正規化の理由）")
            @JsonProperty("根拠") String basis) {
    }

    /** 変数・パラメータ・定数。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "変数・パラメータ・定数")
    public record Symbol(
            @Schema(description = "名前（x, a, k など）", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("名前") String name,
            @Schema(description = "種類", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("種類") SymbolKind kind,
            @Schema(description = "値（パラメータのとき。指定が無ければ空）")
            @JsonProperty("値") String value,
            @Schema(description = "制約（例: a > 0）")
            @JsonProperty("制約") String constraint) {
    }

    /** 定義域（**表示範囲と混ぜない**）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "定義域（その式が意味を持つ範囲）")
    public record Domain(
            @Schema(description = "対象（オブジェクト名・式）", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("対象") String target,
            @Schema(description = "下限（無いときは空。例: 0, -inf）")
            @JsonProperty("下限") String min,
            @Schema(description = "上限（無いときは空。例: 2*pi, inf）")
            @JsonProperty("上限") String max,
            @Schema(description = "条件（例: x ≠ 0, x > 0）")
            @JsonProperty("条件") String condition,
            @Schema(description = "備考（定義域をどう決めたか）")
            @JsonProperty("備考") String note) {
    }

    /** 画面の表示範囲（**定義域とは別**。見え方を決めるだけ）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "画面の表示範囲（見え方。定義域ではない）")
    public record ViewWindow(
            @Schema(description = "X の最小") @JsonProperty("X最小") String xMin,
            @Schema(description = "X の最大") @JsonProperty("X最大") String xMax,
            @Schema(description = "Y の最小") @JsonProperty("Y最小") String yMin,
            @Schema(description = "Y の最大") @JsonProperty("Y最大") String yMax,
            @Schema(description = "軸の目盛の刻み") @JsonProperty("軸の刻み") String axisStep,
            @Schema(description = "グリッドの刻み") @JsonProperty("グリッドの刻み") String gridStep,
            @Schema(description = "自動で決めたか（キーになる対象が全部見えること）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("自動") Boolean automatic) {
    }

    /** 式の曖昧さ（読み方が 1 つに決まらない箇所）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "式・表記の曖昧さ（読み方が 1 つに決まらない箇所）")
    public record Ambiguity(
            @Schema(description = "該当箇所（式・文字）", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("箇所") String location,
            @Schema(description = "考えられる読み方", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("解釈") List<String> options,
            @Schema(description = "採用した読み方（決められないときは空にして質問する）")
            @JsonProperty("採用") String adopted,
            @Schema(description = "採用の理由")
            @JsonProperty("理由") String reason) {
    }

    /** 作図の手順（1 歩）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "作図の手順（1 歩 = 1 つの操作）")
    public record Step(
            @Schema(description = "順番（1 から）", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("順序") Integer order,
            @Schema(description = "何をするか（日本語。例: 点 A を中心に点 B を通る円をかく）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("内容") String action,
            @Schema(description = "関係するオブジェクトの名前")
            @JsonProperty("対象") List<String> objects,
            @Schema(description = "根拠（どの条件から）")
            @JsonProperty("根拠") String basis) {
    }

    /** 文字の条件と図の対象の対応（D 用）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "文字の条件・式と、図のオブジェクトの対応")
    public record Mapping(
            @Schema(description = "文字側の参照（条件・式・記号）", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("文字") String textRef,
            @Schema(description = "図側のオブジェクト名（対応が決められないときは空）")
            @JsonProperty("図の対象") String objectName,
            @Schema(description = "対応の根拠")
            @JsonProperty("根拠") String basis,
            @Schema(description = "対応の確かさ（確実・たぶん・不明）")
            @JsonProperty("確度") String confidence) {
    }

    /** 元の図に対する追加・変更（D 用）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "元の図に対する追加・変更・維持・削除")
    public record Change(
            @Schema(description = "対象のオブジェクト名", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("対象") String objectName,
            @Schema(description = "どうするか", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("変更") ChangeKind change,
            @Schema(description = "理由（利用者の指示・文字の条件のどれによるか）",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("理由") String reason,
            @Schema(description = "根拠")
            @JsonProperty("根拠") String basis) {
    }

    /** 文字と図の食い違い（D 用。**黙って片方を採用しない**）。 */
    @JsonIgnoreProperties(ignoreUnknown = true)
    @Schema(description = "文字の条件と図の内容の食い違い")
    public record Conflict(
            @Schema(description = "食い違っている内容", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("内容") String content,
            @Schema(description = "図から読み取った内容", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("図の読み") String imageReading,
            @Schema(description = "文字の条件", requiredMode = Schema.RequiredMode.REQUIRED)
            @JsonProperty("文字の条件") String textReading,
            @Schema(description = "どう扱うか（確認を求めるなら質問も出す）")
            @JsonProperty("扱い") String resolution) {
    }
}

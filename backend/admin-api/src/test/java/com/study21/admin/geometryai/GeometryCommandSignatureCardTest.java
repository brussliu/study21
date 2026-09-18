package com.study21.admin.geometryai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * コマンドの書き方（早見表）の検証。
 *
 * <p>実測した失敗（`Polygon(A, B, C, 3)` が引数の数の間違いで拒否された）を繰り返さないための資料なので、
 * **紛らわしいコマンドの形と、守るべき規則**が入っていることを固定する。</p>
 */
class GeometryCommandSignatureCardTest {

    @Test
    @DisplayName("引数の数が紛らわしいコマンドの形が載っている（Polygon は 3 つの形）")
    void containsOverloadedCommandForms() {
        String text = GeometryCommandSignatureCard.text();

        // Polygon は「3 点以上」「点のリスト」「2 点 + 頂点数」。混ぜられないことも書く
        assertThat(text).contains("Polygon(A, B, C)");
        assertThat(text).contains("Polygon({A, B, C})");
        assertThat(text).contains("Polygon(A, B, n)");
        assertThat(text).contains("点 3 つ + 頂点数は書かない");
        // 他の紛らわしいものも載せる
        assertThat(text).contains("Circle((x, y), r)");
        assertThat(text).contains("Circle(A, B, C)");
        assertThat(text).contains("Rotate(obj, 120°, O)");
        assertThat(text).contains("Intersect(f, g, A)");
        // Dilate は引数の順が「倍率 → 中心」（実機で確認）
        assertThat(text).contains("Dilate(obj, 倍率, 中心)");
    }

    @Test
    @DisplayName("この版の GeoGebra に無いコマンドを「使えない」として明記する（実機で確認したもの）")
    void listsUnavailableCommands() {
        String text = GeometryCommandSignatureCard.text();

        // 実測（GeoGebra 5.4.920.0）: これらは実行すると false。AI が選ぶと必ず失敗する
        assertThat(text).contains("使えない: Triangle / Square / RegularPolygon");
        assertThat(text).contains("使えない: CircleWithCenterRadius");
        assertThat(text).contains("使えない: Zeroes（→ Roots を使う）");
        assertThat(text).contains("使えない: SetColor / SetLineThickness");
        // 代わりに使う形（実機で動くもの）
        assertThat(text).contains("Polygon(A, B, 4)");
        assertThat(text).contains("Line(P, f)");
        assertThat(text).contains("ParallelLine は無い");
        // 「一覧にある形だけを使う」ことを最初に言う
        assertThat(text).contains("下の一覧にある形だけを使う");
    }

    @Test
    @DisplayName("守るべき規則（定義してから使う・条件を本当に成り立たせる）が載っている")
    void containsRules() {
        String text = GeometryCommandSignatureCard.text();

        assertThat(text).contains("新しいオブジェクトは**使う前に定義**する");
        assertThat(text).contains("コマンドで本当に成り立たせる");
        assertThat(text).contains("目分量の座標でごまかさない");
    }

    @Test
    @DisplayName("system プロンプトの末尾へ足す（既に入っていれば二重にしない）")
    void appendsToSystemPrompt() {
        String appended = GeometryCommandSignatureCard.appendTo("作図アシスタントです。");

        assertThat(appended).startsWith("作図アシスタントです。");
        assertThat(appended).contains("GeoGebra コマンドの書き方");
        // もう一度足しても増えない（設定に混ざっていても壊れない）
        assertThat(GeometryCommandSignatureCard.appendTo(appended)).isEqualTo(appended);
        // 空のプロンプトでも早見表だけになる
        assertThat(GeometryCommandSignatureCard.appendTo(null)).contains("Polygon(A, B, C)");
        assertThat(GeometryCommandSignatureCard.appendTo("   ")).contains("Polygon(A, B, C)");
    }
}

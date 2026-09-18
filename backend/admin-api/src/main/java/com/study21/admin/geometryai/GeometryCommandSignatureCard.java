package com.study21.admin.geometryai;

/**
 * AI へ渡す**GeoGebra コマンドの書き方（引数の数と条件の満たし方）**の早見表。
 *
 * <p>AI が返すコマンドは作図画面の applet が実行するまで正しさが分からない（サーバーに GeoGebra が無い）。
 * 実測では「引数の数の間違い」と「条件を満たしていない作図」が失敗の主な原因だった:</p>
 *
 * <ul>
 *   <li>実例: 「再画一个正三角形，三个顶点，落在这个圆上。」に対して
 *       {@code Polygon(A, B, C, 3)} を返し、GeoGebra が「Polygon では引数『数量』は使えない」と拒否した。
 *       {@code Polygon} には「3 点以上」の形と「2 点 + 頂点数」の形があり、混ぜると存在しない形になる。</li>
 *   <li>実例: {@code A/B/C = Point(c)} のように円上の**任意**の点を 3 つ取るだけでは正三角形にならない
 *       （条件をコマンドで成り立たせる必要がある）。</li>
 * </ul>
 *
 * <p>そこで**許可しているコマンドのうち引数の形が紛らわしいもの**と、守るべき 3 つの規則を
 * 1 枚のテキストにして、AI の system プロンプトへ足す（batC51 = AI 生図、batC52 = AI 画図助手の両方）。
 * プロンプト（DB の設定）ではなくコードが持つのは、GeoGebra の文法に追従させる必要がある**機械的な資料**
 * だから（利用者が文言を変える対象ではない）。</p>
 */
public final class GeometryCommandSignatureCard {

    /** 見出しと規則。 */
    private static final String HEAD = """
            【GeoGebra コマンドの書き方（この版で使える形だけ）】
            1. 下の一覧にある形だけを使う。一覧に無い引数の数は書かない。
            2. 新しいオブジェクトは**使う前に定義**する（未定義の名前を参照しない）。
            3. 指示の条件（正三角形・円上・接線・平行・中点など）は**コマンドで本当に成り立たせる**
               （回転・正多角形・曲線上の点を使う。目分量の座標でごまかさない）。
            4. この版の GeoGebra に**無い**コマンドがある（下の「使えない」を参照）。使うと必ず失敗する。
            """;

    /**
     * コマンドの形（**実機の applet で 1 つずつ実行して確かめたもの**だけを載せる）。
     *
     * <p>実測（2026-09-17・GeoGebra 5.4.920.0）: `Triangle` / `Square` / `RegularPolygon` /
     * `ParallelLine` / `Zeroes` / `CircleWithCenterRadius` / `CircleWithCenterThroughPoint` /
     * `CircleThrough` / `SetColor` / `SetLineThickness` / `ShowLabel` / `SetVisibleInView` は
     * **この版には無い**（実行すると false）。`Dilate` は引数の順が「倍率 → 中心」。</p>
     */
    private static final String SIGNATURES = """
            点・線:
              A = (1, 2)                    点を座標で作る（「名前 = 座標」）
              Point(c)                      曲線・直線の上に点を取る（c は円・直線など）
              Segment(A, B) / Line(A, B) / Ray(A, B) / Vector(A, B)
              Line(P, f)                    点 P を通り f に**平行**な直線（ParallelLine は無い）
              PerpendicularLine(P, f)       点 P を通り f に垂直な直線
              PerpendicularBisector(A, B) / PerpendicularBisector(s) / AngleBisector(A, B, C)
              Midpoint(A, B) / Midpoint(s)  線分 s の中点
            多角形（**引数の数がいちばん間違いやすい**）:
              Polygon(A, B, C)              3 点以上を順に並べる（点は 3 つ以上、いくつでも）
              Polygon({A, B, C})            点のリストでもよい
              Polygon(A, B, n)              2 点と頂点数 n（正 n 角形。**点 3 つ + 頂点数は書かない**）
              使えない: Triangle / Square / RegularPolygon（→ Polygon(A, B, C) や Polygon(A, B, 4) を使う）
            円・弧:
              Circle((x, y), r)             中心と半径
              Circle(A, B)                  中心 A・通過点 B
              Circle(A, B, C)               3 点を通る円
              Semicircle(A, B) / Arc(c, A, B)
              使えない: CircleWithCenterRadius / CircleWithCenterThroughPoint / CircleThrough
            角・回転・移動:
              Angle(A, B, C)                頂点 B の角（A-B-C）／ Angle(f, g) は 2 直線の角
              Rotate(obj, 120°, O)          点 O を中心に 120° 回転（obj は点・線分・図形など）
              Reflect(obj, f) / Reflect(obj, O) / Translate(obj, v)
              Dilate(obj, 倍率, 中心)        **この順**（倍率が先。Dilate(A, 2, (0, 0))）
            交差・接線:
              Intersect(f, g)               交点（複数あるときは Intersect(f, g, A) で A に近い方）
              Tangent(A, c) / Tangent(f, c)
            長さ・面積・関数:
              Distance(A, B) / Length(s) / Area(obj) / Slope(f)
              f(x) = x^2 / Function(f, a, b) / Curve(x(t), y(t), t, a, b)
              Derivative(f) / Derivative(f, 2) / Integral(f) / Integral(f, a, b)
              Root(f) / Root(f, a, b) / Roots(f) / Extremum(f) / Extremum(f, a, b)
              使えない: Zeroes（→ Roots を使う）
              Sequence(式, t, 1, n) / Sequence(式, t, 1, n, 増分) / If(条件, 真) / If(条件, 真, 偽)
              Text("文章", (x, y)) / Polyline(A, B, C)
            表示:
              使えない: SetColor / SetLineThickness / SetLabelMode / ShowLabel / SetVisibleInView
              （この版ではコマンドから色・太さ・ラベルを変えられない。見た目の指定は書かない）
            """;

    /** 早見表の全文。 */
    public static String text() {
        return HEAD + SIGNATURES;
    }

    /**
     * system プロンプトの末尾へ早見表を足す（既に入っているときは足さない＝二重にならない）。
     *
     * @param systemPrompt 設定の system プロンプト（null 可）
     */
    public static String appendTo(String systemPrompt) {
        String base = systemPrompt == null ? "" : systemPrompt.strip();
        if (base.contains(HEAD.strip().lines().findFirst().orElse(""))) {
            return base;
        }
        if (base.isEmpty()) {
            return text().strip();
        }
        return base + "\n\n" + text().strip();
    }

    private GeometryCommandSignatureCard() {
    }
}

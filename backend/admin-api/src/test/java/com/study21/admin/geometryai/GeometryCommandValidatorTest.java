package com.study21.admin.geometryai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 生成コマンドのポリシー検証（batC53 / 設計 §6.3）。
 *
 * サーバーには GeoGebra が無いので**意味の検証はできない**（最終判定は作図画面の applet）。
 * ここで弾くのは「危険・巨大・方針違反」だけであることを固定する。
 */
class GeometryCommandValidatorTest {

    private static final String ALLOWED =
            "Point,Segment,Line,Polygon,Circle,Text,Angle,PerpendicularLine,Midpoint";
    private static final int MAX = 80;

    private final GeometryCommandValidator validator = new GeometryCommandValidator();

    @Test
    void acceptsDefinitionAndCommandForms() {
        GeometryCommandValidator.Result result = validator.validate(List.of(
                "A = (0, 0)",
                "B = (5, 0)",
                "C = (0, 4)",
                "Polygon(A, B, C)",
                "Text(\"三角形ABC\", (2, -1))",
                "# コメントは実行されないので検証しない"), ALLOWED, MAX);

        assertThat(result.isSuccess()).isTrue();
        // コメント行は落とす（実行されない）
        assertThat(result.commands()).containsExactly(
                "A = (0, 0)", "B = (5, 0)", "C = (0, 4)", "Polygon(A, B, C)", "Text(\"三角形ABC\", (2, -1))");
    }

    @Test
    void rejectsCommandsOutsideTheWhitelist() {
        GeometryCommandValidator.Result result = validator.validate(
                List.of("A = (0, 0)", "Sequence(k, k, 1, 5)"), ALLOWED, MAX);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("COMMAND_NOT_ALLOWED");
        assertThat(result.message()).contains("2 行目").contains("Sequence");
    }

    @Test
    void checksTheRightHandSideOfDefinitions() {
        GeometryCommandValidator.Result result = validator.validate(
                List.of("c = Sequence(k, k, 1, 5)"), ALLOWED, MAX);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("COMMAND_NOT_ALLOWED");
        assertThat(result.message()).contains("Sequence");
    }

    @Test
    void rejectsForbiddenCommandsEvenWhenTheyAreAllowed() {
        // 設定に混ざっていても禁止コマンドは通さない
        GeometryCommandValidator.Result result = validator.validate(
                List.of("Delete(A)"), ALLOWED + ",Delete", MAX);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("COMMAND_FORBIDDEN");
    }

    @Test
    void rejectsUnsafeCharactersAndScripts() {
        assertThat(validator.validate(List.of("Text(\"<img src=x>\", (0, 0))"), ALLOWED, MAX).errorCode())
                .isEqualTo("COMMAND_UNSAFE");
        assertThat(validator.validate(List.of("A = (0, 0) // javascript:alert(1)"), ALLOWED, MAX).errorCode())
                .isEqualTo("COMMAND_UNSAFE");
    }

    @Test
    void rejectsBrokenSyntaxNonFiniteNumbersAndPlainText() {
        assertThat(validator.validate(List.of("Polygon(A, B, C"), ALLOWED, MAX).errorCode())
                .isEqualTo("COMMAND_SYNTAX");
        assertThat(validator.validate(List.of("A = (NaN, 0)"), ALLOWED, MAX).errorCode())
                .isEqualTo("COMMAND_SYNTAX");
        // 説明文が混ざったときは通さない（そのまま evalCommand へ流さない）
        assertThat(validator.validate(List.of("三角形 ABC を作図しました。"), ALLOWED, MAX).errorCode())
                .isEqualTo("COMMAND_SYNTAX");
        // 数値と座標だけの式は許す
        assertThat(validator.validate(List.of("(1, 2)"), ALLOWED, MAX).isSuccess()).isTrue();
    }

    @Test
    void rejectsTooManyOrNoCommands() {
        GeometryCommandValidator.Result tooMany = validator.validate(
                List.of("Point((0, 0))", "Point((1, 1))", "Point((2, 2))"), ALLOWED, 2);
        assertThat(tooMany.errorCode()).isEqualTo("TOO_MANY_COMMANDS");

        GeometryCommandValidator.Result none = validator.validate(List.of(), ALLOWED, MAX);
        assertThat(none.errorCode()).isEqualTo("NO_COMMANDS");
    }

    @Test
    void rejectsOverlyLongLines() {
        String longLine = "Text(\"" + "あ".repeat(600) + "\", (0, 0))";
        GeometryCommandValidator.Result result = validator.validate(List.of(longLine), ALLOWED, MAX);

        assertThat(result.errorCode()).isEqualTo("COMMAND_TOO_LONG");
    }

    // ------------------------------------------------------------------ 関数と数式の定義

    @Test
    void acceptsFunctionDefinitionsWithoutTreatingTheNameAsACommand() {
        // 「合法な関数名」を許可リストのコマンドと取り違えない（利用者の指摘。設計 §8）
        GeometryCommandValidator.Result result = validator.validate(List.of(
                "f(x) = x^2",
                "g(x) = sin(x) + 1",
                "h(x) = f(x) - g(x)",
                "k(x, y) = x + y"), ALLOWED, MAX);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.commands()).containsExactly(
                "f(x) = x^2", "g(x) = sin(x) + 1", "h(x) = f(x) - g(x)", "k(x, y) = x + y");
    }

    @Test
    void acceptsEquationsAndInequalitiesWithTheRightCharset() {
        assertThat(validator.validate(List.of("eq1 = x^2 + y^2 = 4"), ALLOWED, MAX).isSuccess()).isTrue();
        assertThat(validator.validate(List.of("y > x^2 - 1"), ALLOWED, MAX).isSuccess()).isTrue();
        // 定義域つきの関数（Function は許可リストのコマンド）
        assertThat(validator.validate(List.of("Function(f, -2, 2)"), ALLOWED + ",Function", MAX).isSuccess())
                .isTrue();
    }

    @Test
    void rejectsUnknownFunctionsAndCapitalizedCommandsInDefinitions() {
        // 知らない小文字の関数は通さない（任意の呼び出しを許さない）
        GeometryCommandValidator.Result unknown = validator.validate(List.of("f(x) = unknownfn(x)"), ALLOWED, MAX);
        assertThat(unknown.isSuccess()).isFalse();
        assertThat(unknown.errorCode()).isIn("COMMAND_SYNTAX", "COMMAND_NOT_ALLOWED");
        assertThat(unknown.message()).contains("unknownfn");

        // 大文字で始まる呼び出しは GeoGebra のコマンド扱い＝許可リストで見る
        GeometryCommandValidator.Result command = validator.validate(List.of("f(x) = Sequence(k, k, 1, 5)"), ALLOWED, MAX);
        assertThat(command.errorCode()).isEqualTo("COMMAND_NOT_ALLOWED");
        assertThat(command.message()).contains("Sequence");

        // 定義の中に危険な文字・スクリプトは入れられない
        assertThat(validator.validate(List.of("f(x) = x^2 // javascript:alert(1)"), ALLOWED, MAX).errorCode())
                .isEqualTo("COMMAND_UNSAFE");
        assertThat(validator.validate(List.of("f(x) = Execute(\"x\")"), ALLOWED, MAX).errorCode())
                .isEqualTo("COMMAND_FORBIDDEN");
    }

    @Test
    void rejectsBrokenFunctionDefinitions() {
        // 変数名が数値だけ・長すぎる・重複している定義は通さない
        assertThat(validator.validate(List.of("f(1) = x"), ALLOWED, MAX).errorCode()).isEqualTo("COMMAND_SYNTAX");
        assertThat(validator.validate(List.of("f(x, x) = x"), ALLOWED, MAX).errorCode()).isEqualTo("COMMAND_SYNTAX");
        assertThat(validator.validate(List.of("f(abcdefghijkl) = x"), ALLOWED, MAX).errorCode())
                .isEqualTo("COMMAND_SYNTAX");
        // 名前が長すぎる（8 文字まで）
        assertThat(validator.validate(List.of("abcdefghi(x) = x"), ALLOWED, MAX).errorCode())
                .isEqualTo("COMMAND_SYNTAX");
    }

    @Test
    void objectNamesMustBeAscii() {
        GeometryCommandValidator.Result result = validator.validate(List.of("三角形 = (0, 0)"), ALLOWED, MAX);

        assertThat(result.isSuccess()).isFalse();
        // 日本語の名前は弾く（日本語のラベルは Text で作る）
        assertThat(result.errorCode()).isIn("COMMAND_NOT_ALLOWED", "COMMAND_SYNTAX");
    }

    // ------------------------------------------------------------ Text の位置の言葉

    /**
     * `Text("文章", A, "left")` の**位置の言葉**はこの版の GeoGebra の引数に無い（実測で必ず false）。
     * 意味を変えずに外せるので、**落とさずに直して通す**（そのままにすると作図が 1 行も入らない）。
     */
    @Test
    @DisplayName("Text の 3 番目の位置の言葉は外して通す（実機で必ず失敗する形）")
    void dropsTextPositionArgument() {
        GeometryCommandValidator.Result result = validator.validate(
                List.of("A = (0, 0)", "Text(\"A\", A, \"left\")"), "Point,Text", 80);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.commands()).containsExactly("A = (0, 0)", "Text(\"A\", A)");
    }

    @Test
    @DisplayName("Text の位置の言葉（座標のあと）も外す")
    void dropsTextPositionArgumentAfterCoordinate() {
        GeometryCommandValidator.Result result = validator.validate(
                List.of("Text(\"CD ⊥ AB\", (2, -0.5), \"center\")"), "Point,Text", 80);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.commands()).containsExactly("Text(\"CD ⊥ AB\", (2, -0.5))");
    }

    @Test
    @DisplayName("Text の 3 番目が true / false のときは触らない（正しい形）")
    void keepsTextBooleanArgument() {
        GeometryCommandValidator.Result result = validator.validate(
                List.of("A = (0, 0)", "Text(\"A\", A, true)", "Text(\"B\", (1, 1), false)"),
                "Point,Text", 80);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.commands())
                .containsExactly("A = (0, 0)", "Text(\"A\", A, true)", "Text(\"B\", (1, 1), false)");
    }

    @Test
    @DisplayName("定義の形（t = Text(...)）でも位置の言葉は外れる（変換そのものの確認）")
    void dropsTextPositionArgumentInDefinition() {
        // 定義の右辺は式として扱われるので文字列は通らないが、変換の規則としては同じ形を直す
        assertThat(GeometryCommandValidator.dropTextPositionArgument("t = Text(\"あ\", A, \"right\")"))
                .isEqualTo("t = Text(\"あ\", A)");
    }

    @Test
    @DisplayName("形が違うもの（引数の順が違う・Text の外に続きがある）は触らない")
    void keepsUnknownTextShapes() {
        assertThat(GeometryCommandValidator.dropTextPositionArgument("Text(\"A\", \"left\", A)"))
                .isEqualTo("Text(\"A\", \"left\", A)");
        assertThat(GeometryCommandValidator.dropTextPositionArgument("f(x) = Text(\"A\", A, \"left\") + 1"))
                .isEqualTo("f(x) = Text(\"A\", A, \"left\") + 1");
        assertThat(GeometryCommandValidator.dropTextPositionArgument("Text(\"A\", A)"))
                .isEqualTo("Text(\"A\", A)");
        assertThat(GeometryCommandValidator.dropTextPositionArgument("Segment(A, B)"))
                .isEqualTo("Segment(A, B)");
    }
}

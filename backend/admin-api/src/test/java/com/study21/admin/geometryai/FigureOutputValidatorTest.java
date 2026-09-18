package com.study21.admin.geometryai;

import com.study21.admin.geometryai.dto.BatC51AResultDto;
import com.study21.admin.geometryai.dto.FigureOutcome;
import com.study21.admin.geometryai.dto.FigureOutputType;
import com.study21.admin.geometryai.dto.FigureParts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI の出力を実行へ進めてよいかの判定（結果種別の尊重・種類ごとの必要項目・コマンドの検証）。
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>利用者が指定した種類は**黙って切り替えない**（食い違ったら質問して確認する）</li>
 *   <li>AUTO のときだけ AI の判定を使う（判定できないときは null のまま＝設計 §7 が許す）</li>
 *   <li>NEEDS_INPUT / UNSUPPORTED はコマンドが空でも異常にしない</li>
 *   <li>コマンドのポリシー検証に落ちたら検証失敗として返す（追加入力待ちにしない）</li>
 *   <li>モードごとの DTO を共用の作図構造へ写す（名前の重複・欠落は警告に残す）</li>
 * </ol>
 */
class FigureOutputValidatorTest {

    private static final String ALLOWED = "Point,Segment,Line,Polygon,Circle,Text";
    private static final int MAX_COMMANDS = 80;

    private FigureOutputValidator validator;

    @BeforeEach
    void setUp() {
        validator = new FigureOutputValidator(new GeometryCommandValidator());
    }

    /** 作図オブジェクト 1 つ。 */
    private static FigureParts.MathObject object(String name, FigureParts.ObjectKind kind) {
        return new FigureParts.MathObject(name, kind, name + " の定義", FigureParts.Source.FROM_TEXT,
                FigureParts.Precision.EXACT, "問題文", List.of());
    }

    private static BatC51AResultDto dto(FigureOutcome outcome, FigureOutputType resolved,
                                        List<FigureParts.MathObject> objects, String... commands) {
        BatC51AResultDto dto = new BatC51AResultDto();
        dto.setOutcome(outcome);
        dto.setResolvedOutputType(resolved);
        dto.setObjects(objects);
        dto.setCommands(List.of(commands));
        return dto;
    }

    private FigureOutputValidator.Verdict validate(BatC51AResultDto dto, FigureOutputType requested) {
        return validator.validate(dto, requested, ALLOWED, MAX_COMMANDS);
    }

    // ------------------------------------------------------------------ 種類の判定

    @Test
    @DisplayName("AUTO は作図の中身から種類を決める（幾何だけ／グラフだけ／両方）")
    void autoResolvesFromObjects() {
        FigureOutputValidator.Verdict geometry = validate(dto(FigureOutcome.GENERATABLE, null,
                List.of(object("A", FigureParts.ObjectKind.POINT), object("poly", FigureParts.ObjectKind.POLYGON)),
                "A = (0, 0)", "poly = Polygon(A, A, A)"), FigureOutputType.AUTO);
        assertThat(geometry.resolvedOutputType()).isEqualTo(FigureOutputType.GEOMETRY);
        assertThat(geometry.figureType()).isEqualTo("geometry");

        FigureOutputValidator.Verdict graph = validate(dto(FigureOutcome.GENERATABLE, null,
                List.of(object("f", FigureParts.ObjectKind.FUNCTION)), "f(x) = x^2"), FigureOutputType.AUTO);
        assertThat(graph.resolvedOutputType()).isEqualTo(FigureOutputType.GRAPH);
        assertThat(graph.figureType()).isEqualTo("function");

        FigureOutputValidator.Verdict mixed = validate(dto(FigureOutcome.GENERATABLE, null,
                List.of(object("A", FigureParts.ObjectKind.POINT), object("f", FigureParts.ObjectKind.FUNCTION)),
                "A = (0, 0)", "f(x) = x^2"), FigureOutputType.AUTO);
        assertThat(mixed.resolvedOutputType()).isEqualTo(FigureOutputType.MIXED);
        // 混在は Graphing（関数も図形も描ける）
        assertThat(mixed.figureType()).isEqualTo("function");
    }

    @Test
    @DisplayName("利用者が指定した種類に AI が従っていればそのまま通る")
    void keepsUserSpecifiedType() {
        FigureOutputValidator.Verdict verdict = validate(dto(FigureOutcome.GENERATABLE, FigureOutputType.GRAPH,
                List.of(object("f", FigureParts.ObjectKind.FUNCTION)), "f(x) = x^2"), FigureOutputType.GRAPH);
        assertThat(verdict.isGeneratable()).isTrue();
        assertThat(verdict.resolvedOutputType()).isEqualTo(FigureOutputType.GRAPH);
        assertThat(verdict.commands()).containsExactly("f(x) = x^2");
    }

    @Test
    @DisplayName("指定と内容が食い違ったら黙って切り替えず、確認の質問を返す")
    void doesNotSilentlySwitchType() {
        // 利用者は幾何図形を指定したのに、AI は関数だけを作ろうとした
        FigureOutputValidator.Verdict verdict = validate(dto(FigureOutcome.GENERATABLE, FigureOutputType.GRAPH,
                List.of(object("f", FigureParts.ObjectKind.FUNCTION)), "f(x) = x^2"), FigureOutputType.GEOMETRY);

        assertThat(verdict.needsInput()).isTrue();
        // 種類は指定のまま（GRAPH に切り替わっていない）
        assertThat(verdict.resolvedOutputType()).isEqualTo(FigureOutputType.GEOMETRY);
        assertThat(verdict.commands()).isEmpty();
        assertThat(verdict.questions()).hasSize(1);
        assertThat(verdict.questions().get(0).question()).contains("一致しません");
        assertThat(verdict.questions().get(0).question()).contains("幾何図形");
    }

    @Test
    @DisplayName("組み合わせを指定したのに片方しか無いときも確認する")
    void mixedRequiresBoth() {
        FigureOutputValidator.Verdict verdict = validate(dto(FigureOutcome.GENERATABLE, FigureOutputType.GEOMETRY,
                List.of(object("A", FigureParts.ObjectKind.POINT)), "A = (0, 0)"), FigureOutputType.MIXED);
        assertThat(verdict.needsInput()).isTrue();
        assertThat(verdict.resolvedOutputType()).isEqualTo(FigureOutputType.MIXED);
    }

    @Test
    @DisplayName("種類の判定に使う作図が無いときは質問する（空の作図を作らない）")
    void asksWhenNothingToDraw() {
        FigureOutputValidator.Verdict verdict = validate(dto(FigureOutcome.GENERATABLE, null,
                List.of(object("t", FigureParts.ObjectKind.TEXT)), "Text(\"説明\", (0, 0))"),
                FigureOutputType.AUTO);
        assertThat(verdict.needsInput()).isTrue();
        assertThat(verdict.questions()).isNotEmpty();
    }

    // ------------------------------------------------------------------ 判定の扱い

    @Test
    @DisplayName("NEEDS_INPUT はコマンドが空でも異常にしない（形式の直しを要求しない）")
    void acceptsNeedsInputWithoutCommands() {
        BatC51AResultDto dto = dto(FigureOutcome.NEEDS_INPUT, null, List.of());
        dto.setQuestions(List.of(new FigureParts.Question("q1", "辺 AB の長さは？", List.of(),
                FigureParts.QuestionKind.NUMBER)));
        dto.setDescription("長さが読み取れませんでした。");

        FigureOutputValidator.Verdict verdict = validator.validate(dto, FigureOutputType.AUTO, ALLOWED, MAX_COMMANDS);

        assertThat(verdict.needsInput()).isTrue();
        assertThat(verdict.errorCode()).isNull();
        assertThat(verdict.questions()).hasSize(1);
        assertThat(verdict.questions().get(0).question()).isEqualTo("辺 AB の長さは？");
    }

    @Test
    @DisplayName("質問が無い NEEDS_INPUT でも、回答できる質問を必ず 1 つ作る")
    void fillsMissingQuestion() {
        BatC51AResultDto dto = dto(FigureOutcome.NEEDS_INPUT, null, List.of());
        dto.setDescription("条件が足りません。");
        FigureOutputValidator.Verdict verdict = validator.validate(dto, FigureOutputType.AUTO, ALLOWED, MAX_COMMANDS);
        assertThat(verdict.questions()).hasSize(1);
        assertThat(verdict.questions().get(0).question()).contains("条件が足りません");
    }

    @Test
    @DisplayName("UNSUPPORTED は理由つきで返す（コマンドは求めない）")
    void reportsUnsupported() {
        BatC51AResultDto dto = dto(FigureOutcome.UNSUPPORTED, null, List.of());
        dto.setDescription("3D の作図には対応していません。");
        FigureOutputValidator.Verdict verdict = validator.validate(dto, FigureOutputType.AUTO, ALLOWED, MAX_COMMANDS);
        assertThat(verdict.isUnsupported()).isTrue();
        assertThat(verdict.reason()).contains("3D");
        assertThat(verdict.errorCode()).isEqualTo("UNSUPPORTED");
    }

    @Test
    @DisplayName("判定が GENERATABLE なのにコマンドが無いときは質問にする（検証失敗にしない）")
    void asksWhenCommandsAreMissing() {
        FigureOutputValidator.Verdict verdict = validate(dto(FigureOutcome.GENERATABLE, null,
                List.of(object("A", FigureParts.ObjectKind.POINT))), FigureOutputType.AUTO);
        assertThat(verdict.needsInput()).isTrue();
        assertThat(verdict.questions()).isNotEmpty();
    }

    // ------------------------------------------------------------------ コマンドの検証

    @Test
    @DisplayName("許可リストに無いコマンドは検証失敗として返す（追加入力待ちにしない）")
    void rejectsNotAllowedCommand() {
        FigureOutputValidator.Verdict verdict = validate(dto(FigureOutcome.GENERATABLE, FigureOutputType.GEOMETRY,
                List.of(object("A", FigureParts.ObjectKind.POINT)), "A = (0, 0)", "Integral(f, 0, 1)"),
                FigureOutputType.GEOMETRY);
        assertThat(verdict.isGeneratable()).isTrue();
        assertThat(verdict.errorCode()).isEqualTo("COMMAND_NOT_ALLOWED");
        assertThat(verdict.commands()).isEmpty();
    }

    // ------------------------------------------------------------------ 共用の構造への写像

    @Test
    @DisplayName("コマンド行だけの応答は名前と定義を拾って共用の構造にする（種類は断定しない）")
    void mapsCommandsToObjects() {
        FigureOutputValidator.Verdict verdict = validate(dto(FigureOutcome.GENERATABLE, null, List.of(),
                "A = (0, 0)", "f(x) = x^2"), FigureOutputType.AUTO);

        assertThat(verdict.isGeneratable()).isTrue();
        // 種類を断定できないので resolvedOutputType は決めない（設計 §7 が許す）
        assertThat(verdict.resolvedOutputType()).isNull();
        assertThat(verdict.objects()).extracting(FigureParts.MathObject::name).containsExactly("A", "f");
        assertThat(verdict.warnings()).anyMatch(warning -> warning.contains("コマンドから名前と定義"));
    }

    @Test
    @DisplayName("名前の無い・重複した作図オブジェクトは落として警告に残す")
    void warnsAboutBrokenObjects() {
        List<FigureParts.MathObject> objects = new ArrayList<>();
        objects.add(object("A", FigureParts.ObjectKind.POINT));
        objects.add(object("A", FigureParts.ObjectKind.POINT));
        objects.add(object("  ", FigureParts.ObjectKind.CIRCLE));

        FigureOutputValidator.Verdict verdict = validate(dto(FigureOutcome.GENERATABLE, FigureOutputType.GEOMETRY,
                objects, "A = (0, 0)"), FigureOutputType.GEOMETRY);

        assertThat(verdict.objects()).hasSize(1);
        assertThat(verdict.warnings()).anyMatch(warning -> warning.contains("重複"));
        assertThat(verdict.warnings()).anyMatch(warning -> warning.contains("名前の無い"));
    }

    @Test
    @DisplayName("近似の仮定と AI の警告は画面へ出す警告に残す")
    void surfacesApproximations() {
        BatC51AResultDto dto = dto(FigureOutcome.GENERATABLE, FigureOutputType.GEOMETRY,
                List.of(object("A", FigureParts.ObjectKind.POINT)), "A = (0, 0)");
        dto.setApproximations(List.of(new FigureParts.Approximation("点 B の位置", "座標が読めない",
                "三角形の形が変わる")));
        dto.setWarnings(List.of("補助線は読み取れませんでした。"));

        FigureOutputValidator.Verdict verdict = validate(dto, FigureOutputType.GEOMETRY);

        assertThat(verdict.warnings()).anyMatch(warning -> warning.contains("近似: 点 B の位置"));
        assertThat(verdict.warnings()).contains("補助線は読み取れませんでした。");
    }
}

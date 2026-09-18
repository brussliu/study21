package com.study21.admin.geometryai;

import com.study21.admin.geometryai.dto.FigureOutcome;
import com.study21.admin.geometryai.dto.FigureOutputDto;
import com.study21.admin.geometryai.dto.FigureOutputType;
import com.study21.admin.geometryai.dto.FigureParts;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * AI の出力を**実行へ進めてよいか**判定し、結果種別ごとの必要項目を確かめる。
 *
 * <p>確かめること（設計 §7・§8）:</p>
 * <ol>
 *   <li><b>結果種別</b> … 利用者の指定があれば必ずそれに従う。AI の判定と食い違ったら
 *       **黙って切り替えず**、確認の質問を返す（＝追加入力待ち）。AUTO のときだけ AI の判定を使う。</li>
 *   <li><b>種類ごとの必要項目</b> … 幾何なら図形のオブジェクト、グラフなら関数・曲線・方程式。
 *       足りなければ質問を返す（空の作図を作らない）。</li>
 *   <li><b>コマンドのポリシー検証</b> … 許可リスト・長さ・個数・文字種（{@link GeometryCommandValidator}）。
 *       ここで落ちたものは「検証で失敗」として再生成の対象にする。</li>
 *   <li><b>共用の作図構造への写像</b>（{@link FigureObjectDtoMapper}）… モードごとの DTO を
 *       同じ構造（名前・種類・定義・出典・精度）に写す。写せないものは警告として残す。</li>
 * </ol>
 *
 * <p><strong>AI の判定（GENERATABLE）は「実行できた・保存できた」ではない。</strong>ここを通っても
 * 実際の実行・保存は別の工程（サーバーの実行と検証）が行う。</p>
 */
@Component
public class FigureOutputValidator {

    private final GeometryCommandValidator commandValidator;

    public FigureOutputValidator(GeometryCommandValidator commandValidator) {
        this.commandValidator = commandValidator;
    }

    /** 判定の結果。 */
    public record Verdict(
            FigureOutcome outcome,
            FigureOutputType resolvedOutputType,
            /** geometry / function（GeoGebra の appName）。決まらないときは null */
            String figureType,
            /** 検証に通ったコマンド（outcome が GENERATABLE のときだけ中身が入る） */
            List<String> commands,
            /** 共用の作図構造（名前・種類・定義・出典・精度） */
            List<FigureParts.MathObject> objects,
            /** 画面に出す注意（近似・警告・食い違いなど） */
            List<String> warnings,
            /** 追加入力待ちのときに画面が復元する質問 */
            List<FigureParts.Question> questions,
            /** 「対応できない」ときの理由（日本語） */
            String reason,
            /** 検証で落ちたときのエラーコード（COMMAND_* など） */
            String errorCode) {

        public boolean isGeneratable() {
            return outcome == FigureOutcome.GENERATABLE;
        }

        public boolean needsInput() {
            return outcome == FigureOutcome.NEEDS_INPUT;
        }

        public boolean isUnsupported() {
            return outcome == FigureOutcome.UNSUPPORTED;
        }
    }

    /**
     * AI の出力を判定する。
     *
     * @param output          モードの出力 DTO（AI の判定を含む）
     * @param requested       利用者が指定した結果種別（B は GRAPH に固定済み）
     * @param allowedCommands 設定 `GEOMETRY_AI_ALLOWED_COMMANDS`
     * @param maxCommands     設定 `GEOMETRY_AI_MAX_COMMANDS`
     */
    public Verdict validate(FigureOutputDto output, FigureOutputType requested,
                            String allowedCommands, int maxCommands) {
        FigureOutputType want = requested == null ? FigureOutputType.defaultType() : requested;
        List<String> warnings = new ArrayList<>(safe(output.getWarnings()));
        List<FigureParts.MathObject> objects = FigureObjectMapper.normalize(output.getObjects(), warnings);
        // AI が作図オブジェクトを書いていないときは、コマンドから名前と定義だけを拾う
        // （種類は断定できないので、種類の判定には使わない）
        boolean declared = !objects.isEmpty();
        List<String> commands0 = FigureResponseParser.commandsOf(output);
        if (!declared && !commands0.isEmpty()) {
            objects = FigureObjectMapper.fromCommands(commands0);
            warnings.add("作図オブジェクトの一覧が無かったため、コマンドから名前と定義を読み取りました。");
        }
        FigureOutcome outcome = output.getOutcome() == null ? FigureOutcome.GENERATABLE : output.getOutcome();
        List<FigureParts.Question> questions = new ArrayList<>(
                output.getQuestions() == null ? List.of() : output.getQuestions());
        for (FigureParts.Approximation approximation : safe(output.getApproximations())) {
            warnings.add("近似: " + approximation.content()
                    + (approximation.reason() == null ? "" : "（理由: " + approximation.reason() + "）"));
        }

        // 対応できないと AI が判断した（理由をそのまま画面へ出す）
        if (outcome == FigureOutcome.UNSUPPORTED) {
            return new Verdict(FigureOutcome.UNSUPPORTED, null, null, List.of(), objects, warnings, questions,
                    textOr(output.getDescription(), "この内容には対応できません。"), "UNSUPPORTED");
        }

        // 種類の判定（利用者の指定を勝手に変えない）
        FigureOutputType aiType = output.getResolvedOutputType();
        FigureOutputType resolved = want.isResolved() ? want : (aiType != null && aiType.isResolved() ? aiType : null);

        // 追加入力待ち（AI が質問を返した）
        if (outcome == FigureOutcome.NEEDS_INPUT) {
            if (questions.isEmpty()) {
                questions.add(new FigureParts.Question("q1",
                        textOr(output.getDescription(), "作図に必要な情報が足りません。分かる範囲で教えてください。"),
                        List.of(), FigureParts.QuestionKind.TEXT));
            }
            return new Verdict(FigureOutcome.NEEDS_INPUT, resolved, figureTypeOf(resolved, null),
                    List.of(), objects, warnings, questions, null, null);
        }

        // コマンドのポリシー検証（許可リスト・長さ・個数・文字種・名前）
        List<String> commands = FigureResponseParser.commandsOf(output);
        if (commands.isEmpty()) {
            // 判定は GENERATABLE なのにコマンドが無い（契約違反）。質問にして先へ進めない
            questions.add(new FigureParts.Question("q1",
                    "作図のコマンドがありませんでした。作りたい図をもう少し具体的に教えてください。",
                    List.of(), FigureParts.QuestionKind.TEXT));
            return new Verdict(FigureOutcome.NEEDS_INPUT, resolved, figureTypeOf(resolved, objects),
                    List.of(), objects, warnings, questions, null, null);
        }
        GeometryCommandValidator.Result checked = commandValidator.validate(commands, allowedCommands, maxCommands);
        if (!checked.isSuccess()) {
            return new Verdict(FigureOutcome.GENERATABLE, resolved, figureTypeOf(resolved, objects),
                    List.of(), objects, warnings, questions, checked.message(), checked.errorCode());
        }

        // 種類ごとの必要項目（足りないときは黙って切り替えず、確認を求める）。
        // AI が作図オブジェクトを宣言していないとき（コマンド行だけの応答）は種類を断定できないので、
        // 指定をそのまま使う（指定が AUTO なら「決められない」＝ null のまま。設計 §7 が許している）
        if (!declared) {
            return new Verdict(FigureOutcome.GENERATABLE, resolved, figureTypeOf(resolved, null),
                    checked.commands(), objects, warnings, questions, null, null);
        }
        FigureOutputType actual = inferOutputType(objects);
        if (resolved == null) {
            if (actual == null) {
                questions.add(new FigureParts.Question("q1",
                        "作図の種類（幾何図形 / 関数・方程式のグラフ / その組み合わせ）を教えてください。",
                        List.of(FigureOutputType.GEOMETRY.label(), FigureOutputType.GRAPH.label(),
                                FigureOutputType.MIXED.label()),
                        FigureParts.QuestionKind.CHOICE));
                return new Verdict(FigureOutcome.NEEDS_INPUT, null, null, List.of(), objects, warnings, questions,
                        null, null);
            }
            resolved = actual;
        } else if (actual == null) {
            questions.add(new FigureParts.Question("q1",
                    "作図する対象（点・線・円・関数など）が読み取れませんでした。何を作図しますか？",
                    List.of(), FigureParts.QuestionKind.TEXT));
            return new Verdict(FigureOutcome.NEEDS_INPUT, resolved, figureTypeOf(resolved, null),
                    List.of(), objects, warnings, questions, null, null);
        } else if (!isCompatible(resolved, actual)) {
            questions.add(new FigureParts.Question("q1",
                    "指定された種類（" + resolved.label() + "）と、AI が読み取った内容（" + actual.label()
                            + "）が一致しません。どちらで作図しますか？",
                    List.of("指定どおり " + resolved.label() + " で作る", "内容に合わせて " + actual.label() + " で作る"),
                    FigureParts.QuestionKind.CHOICE));
            // **種類は指定のまま**（黙って切り替えない）
            return new Verdict(FigureOutcome.NEEDS_INPUT, resolved, figureTypeOf(resolved, null),
                    List.of(), objects, warnings, questions, null, null);
        }

        return new Verdict(FigureOutcome.GENERATABLE, resolved, figureTypeOf(resolved, objects),
                checked.commands(), objects, warnings, questions, null, null);
    }

    /** 結果種別から作図タイプを決める（種類が決まらないときは作図の中身から決める）。 */
    static String figureTypeOf(FigureOutputType resolved, List<FigureParts.MathObject> objects) {
        if (resolved != null) {
            return resolved.figureType();
        }
        FigureOutputType inferred = inferOutputType(objects);
        return inferred == null ? "geometry" : inferred.figureType();
    }

    /**
     * 作図の中身から種類を見分ける（AUTO のとき・種類の食い違いを見るとき）。
     *
     * <p>関数・曲線・円錐曲線があれば GRAPH、図形だけなら GEOMETRY、両方なら MIXED。
     * 何も無ければ null（判定できない）。</p>
     */
    static FigureOutputType inferOutputType(List<FigureParts.MathObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return null;
        }
        boolean graph = false;
        boolean geometry = false;
        for (FigureParts.MathObject object : objects) {
            FigureParts.ObjectKind kind = object.kind();
            if (kind == null) {
                continue;
            }
            switch (kind) {
                case FUNCTION, CURVE, CONIC -> graph = true;
                case POINT, SEGMENT, LINE, RAY, VECTOR, POLYGON, CIRCLE, ARC, ANGLE, MEASURE -> geometry = true;
                case TEXT, OTHER -> {
                    // 文章・その他は種類の判定に使わない
                }
            }
        }
        if (graph && geometry) {
            return FigureOutputType.MIXED;
        }
        if (graph) {
            return FigureOutputType.GRAPH;
        }
        if (geometry) {
            return FigureOutputType.GEOMETRY;
        }
        return null;
    }

    /** 指定の種類と、実際の中身が両立するか（MIXED は両方を要求する）。 */
    static boolean isCompatible(FigureOutputType resolved, FigureOutputType actual) {
        if (actual == null) {
            return false;
        }
        if (resolved == FigureOutputType.MIXED) {
            return actual == FigureOutputType.MIXED;
        }
        if (resolved == FigureOutputType.GEOMETRY) {
            // 図形だけを指定したのにグラフが混ざっていたら食い違い
            return actual == FigureOutputType.GEOMETRY;
        }
        // グラフだけを指定したのに図形も含む場合は、グラフが主なので許す
        return actual == FigureOutputType.GRAPH || actual == FigureOutputType.MIXED;
    }

    /** 値が 1 つも無いときの説明文（画面にそのまま出す）。 */
    private static String textOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static <T> List<T> safe(List<T> values) {
        if (values == null) {
            return List.of();
        }
        List<T> kept = new ArrayList<>();
        for (T value : values) {
            if (value != null) {
                kept.add(value);
            }
        }
        return kept;
    }

    /** 検証目標を「対象 → 期待」の表にする（画面と検証工程が使う）。 */
    public static Map<String, FigureParts.VerificationTarget> indexTargets(FigureOutputDto output) {
        Map<String, FigureParts.VerificationTarget> map = new LinkedHashMap<>();
        for (FigureParts.VerificationTarget target : safe(output.getVerificationTargets())) {
            if (target.target() != null && !target.target().isBlank()) {
                map.putIfAbsent(target.target().trim(), target);
            }
        }
        return map;
    }

    /** 作図オブジェクトの名前（重複を落とす）。 */
    public static Set<String> namesOf(List<FigureParts.MathObject> objects) {
        Set<String> names = new LinkedHashSet<>();
        for (FigureParts.MathObject object : objects == null ? List.<FigureParts.MathObject>of() : objects) {
            if (object.name() != null && !object.name().isBlank()) {
                names.add(object.name().trim());
            }
        }
        return names;
    }
}

package com.study21.admin.geometryai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI が返したコマンドの**ポリシー検証**（batC53。設計 §6.3）。
 *
 * <p>サーバーには GeoGebra が無いので**意味の検証はできない**（最終判定は作図画面の applet ＝
 * 既存の `runCommands()` が `evalCommand` の戻り値を見て失敗したら巻き戻す）。ここで弾くのは
 * 「危険・巨大・方針違反」だけにする。</p>
 *
 * <table>
 *   <tr><td>1</td><td>行数 ≤ MAX_COMMANDS</td><td>TOO_MANY_COMMANDS</td></tr>
 *   <tr><td>2</td><td>1 行 ≤ 500 / 全体 ≤ 20,000</td><td>COMMAND_TOO_LONG</td></tr>
 *   <tr><td>3</td><td>先頭トークンが許可リストにある</td><td>COMMAND_NOT_ALLOWED</td></tr>
 *   <tr><td>4</td><td>禁止コマンドを含まない</td><td>COMMAND_FORBIDDEN</td></tr>
 *   <tr><td>5</td><td>危険な文字を含まない</td><td>COMMAND_UNSAFE</td></tr>
 *   <tr><td>6</td><td>括弧の対応・オブジェクト名・数値</td><td>COMMAND_SYNTAX</td></tr>
 *   <tr><td>7</td><td>コマンドが 1 個以上ある</td><td>NO_COMMANDS</td></tr>
 * </table>
 */
@Component
public class GeometryCommandValidator {

    private static final Logger log = LoggerFactory.getLogger(GeometryCommandValidator.class);

    /** 1 行の長さの上限。 */
    public static final int LINE_MAX = 500;
    /** 全体の長さの上限。 */
    public static final int TOTAL_MAX = 20_000;

    /** 禁止コマンド（スクリプト・外部入出力・削除など）。 */
    private static final List<String> FORBIDDEN = List.of(
            "Delete", "Execute", "SetValue", "Import", "Export", "File", "Button", "Checkbox",
            "InputBox", "SetActiveView", "RunClickScript", "StartAnimation", "PlaySound",
            "SetAxesVisible", "SetPerspective", "SetCoordSystem", "Paste", "Copy", "SelectObjects");

    /**
     * 危険な文字列（スクリプト・外部読み込み）。
     *
     * <p>{@code <} / {@code >} はここに入れない（不等号・不等式は**数学の式として必要**。
     * 例: {@code y > x^2}）。タグの形（{@code <名前} / {@code </} / {@code <!}）は
     * {@link #TAG_LIKE} で別に弾く。</p>
     */
    private static final List<String> UNSAFE = List.of(
            "\\", "javascript:", "http://", "https://", "../", "`", "$(", "eval(");

    /** HTML・XML のタグの形（スクリプトの混入を防ぐ）。 */
    private static final Pattern TAG_LIKE = Pattern.compile("<\\s*[/!?A-Za-z]");

    /** オブジェクト名（ASCII のみ。日本語は Text で作る）。 */
    private static final Pattern OBJECT_NAME = Pattern.compile("^[A-Za-z][A-Za-z0-9_]{0,7}$");
    /** `名前 = 式` の定義行。 */
    private static final Pattern DEFINITION = Pattern.compile("^([A-Za-z][A-Za-z0-9_]{0,7})\\s*=\\s*(.+)$");
    /** 先頭トークン（コマンド名）。 */
    private static final Pattern COMMAND_TOKEN = Pattern.compile("^([A-Za-z][A-Za-z0-9_]*)\\s*\\(");
    /** 数値リテラル。 */
    private static final Pattern NUMBER = Pattern.compile("[-+]?(\\d+\\.?\\d*|\\.\\d+)([eE][-+]?\\d+)?");
    /** 非有限の数値。 */
    private static final List<String> NON_FINITE = List.of("NaN", "Infinity", "∞");

    /**
     * 数式の中で使ってよい**数学の関数名**（小文字）。
     *
     * <p>これは許可リスト（GeoGebra のコマンド）とは**別物**。{@code f(x) = sin(x)} の {@code sin} は
     * コマンドではなく数学の関数なので、許可リストに無くても通す。逆に**許可リストにあるコマンド名を
     * 関数名として使うこと**は許さない（大文字で始まる呼び出しはコマンドとして許可リストで見る）。</p>
     */
    private static final List<String> MATH_FUNCTIONS = List.of(
            "sin", "cos", "tan", "sec", "csc", "cot",
            "asin", "acos", "atan", "asinh", "acosh", "atanh",
            "sinh", "cosh", "tanh", "sqrt", "cbrt", "abs", "exp", "ln", "log", "lg", "floor", "ceil",
            "round", "sgn", "sign", "min", "max", "mod", "atan2", "nroot", "real", "imaginary", "arg");

    /**
     * 予約語（**関数の変数名に使わせない**名前）。
     *
     * <p>x / y / z は入れない（関数の変数として普通に使う。例: {@code k(x, y) = x + y}）。</p>
     */
    private static final List<String> RESERVED_WORDS = List.of(
            "e", "pi", "true", "false", "infinity", "nan");

    /** `名前(変数, 変数) = 式` の定義（関数の定義）。 */
    private static final Pattern FUNCTION_DEFINITION = Pattern.compile(
            "^([A-Za-z][A-Za-z0-9_]*)\\s*\\(([^()]*)\\)\\s*=\\s*(.+)$");
    /** 数式の中で呼ぶ形（`sin(x)`）。 */
    private static final Pattern EXPRESSION_CALL = Pattern.compile("([A-Za-z_][A-Za-z0-9_]*)\\s*\\(");
    /** 変数名（1〜3 文字の英字）。 */
    private static final Pattern VARIABLE_NAME = Pattern.compile("^[A-Za-z]{1,3}$");
    /** 数式に使ってよい文字（数字・演算子・括弧・カンマ・英字・空白・度・不等号）。 */
    private static final Pattern EXPRESSION_CHARS = Pattern.compile("^[A-Za-z0-9_+\\-*/^().,\\s°=<>&|!\\[\\]{}]*$");

    /** 数式・定義の検査で見つかった問題（エラーコードと日本語の理由）。 */
    /** 引数の区切り（`Text` の 3 番目を外すのに使う）。 */
    private static final Pattern TEXT_COMMAND = Pattern.compile("\\bText\\s*\\(");

    /**
     * `Text("文章", A, "left")` の**位置の言葉**（3 番目）を外す。
     *
     * <p>この版の GeoGebra の `Text` は `Text(文章, 点)` / `Text(文章, 点, true|false)` だけで、
     * **`"left"` のような位置の言葉は引数に存在しない**（実測: 必ず false を返す）。AI が
     * 「点 A の左」を素直に引数へ写してしまうことがあり、そのままだと**作図が 1 行も入らない**
     * （画面は保存を止める）。意味を変えずに外せるので、ここで直して通す。</p>
     *
     * <p>外すのは「最後の引数が文字列」のときだけ。ほかの形（引数の順が違う・式の中など）は
     * 触らない（勝手に意味を変えない）。</p>
     */
    static String dropTextPositionArgument(String line) {
        Matcher command = TEXT_COMMAND.matcher(line);
        if (!command.find()) {
            return line;
        }
        int open = line.indexOf('(', command.start());
        int close = matchingParen(line, open);
        if (close < 0 || close != line.stripTrailing().length() - 1) {
            // `Text(` の外に続きがある行（式の一部など）は触らない
            return line;
        }
        List<String> args = splitArguments(line.substring(open + 1, close));
        if (args.size() != 3) {
            return line;
        }
        String last = args.get(2).trim();
        if (last.length() < 2 || !last.startsWith("\"") || !last.endsWith("\"")) {
            return line;
        }
        return line.substring(0, open + 1) + args.get(0).strip() + ", " + args.get(1).strip() + ")"
                + line.substring(close + 1);
    }

    /** 開き括弧に対応する閉じ括弧（引用符の中は数えない）。無ければ -1。 */
    private static int matchingParen(String line, int open) {
        int depth = 0;
        boolean quoted = false;
        for (int index = open; index < line.length(); index += 1) {
            char c = line.charAt(index);
            if (c == '"') {
                quoted = !quoted;
                continue;
            }
            if (quoted) {
                continue;
            }
            if (c == '(') {
                depth += 1;
            } else if (c == ')') {
                depth -= 1;
                if (depth == 0) {
                    return index;
                }
            }
        }
        return -1;
    }

    /** 括弧の中を**一番外側のカンマ**で分ける（引用符の中と入れ子は分けない）。 */
    private static List<String> splitArguments(String inside) {
        List<String> parts = new ArrayList<>();
        int depth = 0;
        boolean quoted = false;
        StringBuilder current = new StringBuilder();
        for (char c : inside.toCharArray()) {
            if (c == '"') {
                quoted = !quoted;
            } else if (!quoted && (c == '(' || c == '{' || c == '[')) {
                depth += 1;
            } else if (!quoted && (c == ')' || c == '}' || c == ']')) {
                depth -= 1;
            }
            if (c == ',' && !quoted && depth == 0) {
                parts.add(current.toString());
                current.setLength(0);
                continue;
            }
            current.append(c);
        }
        parts.add(current.toString());
        return parts;
    }

    private record Problem(String code, String message) {
    }

    /** 検証の結果。`errorCode` が null なら合格（`commands` は正規化後）。 */
    public record Result(List<String> commands, String errorCode, String message) {

        public boolean isSuccess() {
            return errorCode == null;
        }

        static Result ok(List<String> commands) {
            return new Result(commands, null, null);
        }

        static Result failure(String code, int lineNo, String detail) {
            String prefix = lineNo > 0 ? (lineNo + " 行目: ") : "";
            return new Result(List.of(), code, "生成されたコマンドを確認してください（" + prefix + detail + "）");
        }
    }

    /**
     * 検証して、通れば正規化したコマンドを返す。
     *
     * @param allowedCommands 設定 `GEOMETRY_AI_ALLOWED_COMMANDS`（カンマ区切り）
     * @param maxCommands     設定 `GEOMETRY_AI_MAX_COMMANDS`
     */
    public Result validate(List<String> commands, String allowedCommands, int maxCommands) {
        Set<String> allowed = split(allowedCommands);
        List<String> lines = new ArrayList<>();
        int total = 0;
        int lineNo = 0;
        for (String command : commands == null ? List.<String>of() : commands) {
            lineNo += 1;
            String line = command == null ? "" : command.trim();
            if (line.isEmpty()) {
                continue;
            }
            if (line.length() > LINE_MAX) {
                return Result.failure("COMMAND_TOO_LONG", lineNo, "1 行が長すぎます");
            }
            total += line.length();
            if (total > TOTAL_MAX) {
                return Result.failure("COMMAND_TOO_LONG", lineNo, "コマンド全体が長すぎます");
            }
            if (line.startsWith("#")) {
                // コメント行は実行されないので検証しない（設計: コメントは # で始める行だけ）
                continue;
            }
            for (String unsafe : UNSAFE) {
                if (line.contains(unsafe)) {
                    return Result.failure("COMMAND_UNSAFE", lineNo, "使えない文字が含まれています（" + unsafe + "）");
                }
            }
            if (TAG_LIKE.matcher(line).find()) {
                return Result.failure("COMMAND_UNSAFE", lineNo, "使えない文字が含まれています（<）");
            }
            for (String forbidden : FORBIDDEN) {
                if (containsCommand(line, forbidden)) {
                    return Result.failure("COMMAND_FORBIDDEN", lineNo,
                            "禁止されているコマンドです（" + forbidden + "）");
                }
            }
            for (String nonFinite : NON_FINITE) {
                if (line.contains(nonFinite)) {
                    return Result.failure("COMMAND_SYNTAX", lineNo, "数値が有限ではありません（" + nonFinite + "）");
                }
            }
            if (!balanced(line)) {
                return Result.failure("COMMAND_SYNTAX", lineNo, "括弧が閉じていません");
            }
            // 関数の定義（`f(x) = x^2`）は**コマンドではない**。先に見て、名前を許可リストと
            // 取り違えないようにする（利用者の指摘。`f` が許可リストに無いだけで弾かれていた）
            Matcher function = FUNCTION_DEFINITION.matcher(line);
            if (function.matches()) {
                Problem problem = checkFunctionDefinition(function.group(1), function.group(2),
                        function.group(3), allowed, lines);
                if (problem != null) {
                    return Result.failure(problem.code(), lineNo, problem.message());
                }
                lines.add(line);
                continue;
            }

            String name = firstToken(line);
            if (name != null && !allowed.contains(name)) {
                // コマンド形（名前(…)）のときだけ許可リストを見る
                return Result.failure("COMMAND_NOT_ALLOWED", lineNo,
                        "許可されていないコマンドです（" + name + "）");
            }
            Matcher definition = DEFINITION.matcher(line);
            if (definition.matches()) {
                String objectName = definition.group(1);
                if (!OBJECT_NAME.matcher(objectName).matches()) {
                    return Result.failure("COMMAND_SYNTAX", lineNo,
                            "オブジェクト名は英字で始まる 8 文字以内にしてください（" + objectName + "）");
                }
                // 右辺を数式として見る（小文字の呼び出しは数学の関数、大文字はコマンド）
                Problem problem = checkExpression(definition.group(2).trim(), allowed, lines);
                if (problem != null) {
                    return Result.failure(problem.code(), lineNo, problem.message());
                }
            } else if (name == null) {
                // コマンド形でも `名前 = 式` でもない行。**数式（不等式・陰関数）だけ**を許す
                // （説明文や単なる単語をそのまま evalCommand へ流さない）
                Problem problem = checkStandaloneExpression(line, allowed, lines);
                if (problem != null) {
                    return Result.failure(problem.code(), lineNo, problem.message());
                }
            }
            // 位置の言葉（"left" など）は Text の引数にできない（実機で必ず false になる）。
            // **機械的に直せる**ので落とさず、3 番目を外して通す（直したことはログに残す）
            String fixedLine = dropTextPositionArgument(line);
            if (!fixedLine.equals(line)) {
                log.warn("AI の Text コマンドから位置の指定を外しました。before={} after={}", line, fixedLine);
                line = fixedLine;
            }
            lines.add(line);
        }
        if (lines.isEmpty()) {
            return Result.failure("NO_COMMANDS", 0, "コマンドが 1 つもありません");
        }
        if (lines.size() > maxCommands) {
            return Result.failure("TOO_MANY_COMMANDS", 0,
                    "コマンドが多すぎます（" + lines.size() + " 件。上限は " + maxCommands + " 件）");
        }
        return Result.ok(lines);
    }

    /**
     * 関数の定義（{@code f(x) = x^2}）を確かめる。問題があれば日本語の理由、無ければ null。
     *
     * <p>確かめること: 名前が ASCII 8 文字以内で許可リストのコマンドと衝突しない・変数名が
     * 1〜3 文字の英字で重複しない・右辺が安全な数式である。</p>
     */
    private static Problem checkFunctionDefinition(String name, String parameters, String expression,
                                                   Set<String> allowed, List<String> defined) {
        Problem problem = syntax("関数の名前は英字で始まる 8 文字以内にしてください（" + name + "）");
        if (!OBJECT_NAME.matcher(name).matches()) {
            return problem;
        }
        if (allowed.contains(name)) {
            return syntax("関数の名前がコマンド名と同じです（" + name + "）。別の名前にしてください");
        }
        if (RESERVED_WORDS.contains(name.toLowerCase(Locale.ROOT))) {
            return syntax("関数の名前によく使う記号は使えません（" + name + "）");
        }
        List<String> variables = new ArrayList<>();
        for (String part : parameters.split(",")) {
            String variable = part.trim();
            if (variable.isEmpty()) {
                continue;
            }
            if (!VARIABLE_NAME.matcher(variable).matches()) {
                return syntax("変数は 1〜3 文字の英字にしてください（" + variable + "）");
            }
            if (RESERVED_WORDS.contains(variable.toLowerCase(Locale.ROOT))) {
                return syntax("変数に使えない名前です（" + variable + "）");
            }
            if (variables.contains(variable)) {
                return syntax("変数が重複しています（" + variable + "）");
            }
            variables.add(variable);
        }
        if (variables.isEmpty()) {
            return syntax("関数の変数がありません（例: f(x) = x^2）");
        }
        return checkExpression(expression, allowed, defined);
    }

    /**
     * 単独の数式（不等式・陰関数。例 {@code y > x^2 - 1}, {@code x^2 + y^2 = 4}）かどうか。
     *
     * <p>数値と演算子だけで書ける式は今までどおり許し、それ以外は**数式として読める形**のときだけ許す。
     * 説明文や単なる単語は通さない。</p>
     */
    private static Problem checkStandaloneExpression(String line, Set<String> allowed, List<String> defined) {
        if (isLiteralOrExpression(line)) {
            return null;
        }
        if (!EXPRESSION_CHARS.matcher(line).matches()) {
            return syntax("コマンドの形になっていません");
        }
        // 少なくとも数字か演算子・関係子が要る（英単語だけの行を通さない）
        if (!line.matches(".*[0-9+\\-*/^=<>].*")) {
            return syntax("コマンドの形になっていません");
        }
        Problem problem = checkExpression(line, allowed, defined);
        if (problem != null) {
            return problem;
        }
        return null;
    }

    /** 構文エラー（数式・定義の書き方の問題）。 */
    private static Problem syntax(String message) {
        return new Problem("COMMAND_SYNTAX", message);
    }

    /**
     * 数式として安全か（問題があれば日本語の理由、無ければ null）。
     *
     * <p>小文字の呼び出しは**数学の関数**（{@link #MATH_FUNCTIONS}）か、同じ応答の中で定義済みの
     * 関数名だけを許す。大文字で始まる呼び出しは GeoGebra のコマンドなので許可リストで見る。</p>
     */
    private static Problem checkExpression(String expression, Set<String> allowed, List<String> defined) {
        String text = expression.trim();
        if (text.isEmpty()) {
            return syntax("式がありません");
        }
        if (!EXPRESSION_CHARS.matcher(text).matches()) {
            return syntax("式に使えない文字が含まれています");
        }
        Set<String> definedNames = new LinkedHashSet<>();
        for (String line : defined) {
            Matcher definition = DEFINITION.matcher(line);
            if (definition.matches()) {
                definedNames.add(definition.group(1));
            }
            Matcher function = FUNCTION_DEFINITION.matcher(line);
            if (function.matches()) {
                definedNames.add(function.group(1));
            }
        }
        Matcher call = EXPRESSION_CALL.matcher(text);
        while (call.find()) {
            String name = call.group(1);
            if (Character.isUpperCase(name.charAt(0))) {
                if (!allowed.contains(name)) {
                    return new Problem("COMMAND_NOT_ALLOWED", "許可されていないコマンドです（" + name + "）");
                }
                continue;
            }
            if (MATH_FUNCTIONS.contains(name.toLowerCase(Locale.ROOT)) || definedNames.contains(name)) {
                continue;
            }
            return syntax("知らない関数です（" + name + "）。使える数学の関数: "
                    + String.join(", ", MATH_FUNCTIONS));
        }
        return null;
    }

    /** カンマ区切りの設定値を集合にする。 */
    public static Set<String> split(String allowedCommands) {
        Set<String> set = new LinkedHashSet<>();
        if (allowedCommands == null) {
            return set;
        }
        for (String part : allowedCommands.split(",")) {
            String value = part.trim();
            if (!value.isEmpty()) {
                set.add(value);
            }
        }
        return set;
    }

    /** 行の先頭が `名前(` の形なら、その名前を返す。 */
    private static String firstToken(String line) {
        Matcher matcher = COMMAND_TOKEN.matcher(line);
        return matcher.find() ? matcher.group(1) : null;
    }

    /** 行の中にコマンドとして（＝ `名前(` の形で）その名前が現れるか。 */
    private static boolean containsCommand(String line, String command) {
        String lower = line.toLowerCase(Locale.ROOT);
        String target = command.toLowerCase(Locale.ROOT);
        int from = 0;
        while (true) {
            int index = lower.indexOf(target, from);
            if (index < 0) {
                return false;
            }
            int after = index + target.length();
            boolean boundaryBefore = index == 0 || !Character.isLetterOrDigit(lower.charAt(index - 1));
            boolean commandAfter = after < lower.length() && lower.charAt(after) == '(';
            if (boundaryBefore && commandAfter) {
                return true;
            }
            from = after;
        }
    }

    /** 括弧の対応（丸括弧と角括弧。文字列リテラルの中は数えない）。 */
    private static boolean balanced(String line) {
        int round = 0;
        int square = 0;
        boolean inString = false;
        for (int index = 0; index < line.length(); index += 1) {
            char c = line.charAt(index);
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) {
                continue;
            }
            switch (c) {
                case '(' -> round += 1;
                case ')' -> round -= 1;
                case '[' -> square += 1;
                case ']' -> square -= 1;
                default -> {
                }
            }
            if (round < 0 || square < 0) {
                return false;
            }
        }
        return round == 0 && square == 0 && !inString;
    }

    /**
     * コマンド形でも定義行でもない行が許される形か。
     *
     * <p>許すのは数値と座標だけの式（例 `(1, 2)`、`2 + 3`）。**単なる単語や文章は許さない**
     * （AI が説明文を混ぜたときに、そのまま evalCommand へ流れないようにする）。</p>
     */
    private static boolean isLiteralOrExpression(String line) {
        return line.matches("^\\(?[-+0-9\\s.,*/^()]+$") && NUMBER.matcher(line).find();
    }
}

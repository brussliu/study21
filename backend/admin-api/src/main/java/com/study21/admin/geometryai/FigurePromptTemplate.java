package com.study21.admin.geometryai;

import com.study21.common.core.exception.ValidationException;
import com.study21.common.core.geometryai.AiFigureSupplements;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * プロンプトテンプレートの**変数の登録所と展開**（AI 生図の全モード共通）。
 *
 * <p>画面（設定ページ）とサーバーで同じ 1 重括弧の変数を使う。**知らない変数は展開せずに
 * 例外にする**（未展開の <code>{...}</code> をモデルへ送らない）。値が空の任意項目は
 * **明示的な既定値**（「なし」「指定なし」など）を入れる。</p>
 *
 * <p>変数の名前は**ここが唯一の定義**。設定ページの入力欄の説明・保存時の検証・プロンプトの
 * 展開がすべてこの一覧を使うので、説明と実際に使える変数が食い違わない。</p>
 *
 * <p>補充パラメータ（画面の任意項目）は、**項目ごとに専用の変数**を持つ
 * （{@link AiFigureSupplements} が項目名と変数の対応を持つ）。まとめた一覧は
 * {@code {supplements}} でも渡すので、テンプレート側は好きな方を使える。</p>
 */
public final class FigurePromptTemplate {

    /** テンプレート中の変数。 */
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");

    /** 任意項目が未指定のときに入れる値（未展開の {} をモデルへ送らない）。 */
    static final String DEFAULT_NONE = "なし";
    static final String DEFAULT_UNSPECIFIED = "指定なし";
    /** 「元の名前とラベル」の既定（画面のチェックと同じ）。 */
    static final String DEFAULT_KEEP_LABELS = "はい（元の図・問題文の名前とラベルをそのまま残す）";
    static final String DEFAULT_DROP_LABELS = "いいえ（元の名前・ラベルにこだわらず、分かりやすい名前にしてよい）";

    /** 変数の説明（設定ページに出す文言の元）。 */
    public record Variable(String name, String description, String defaultValue) {
    }

    /** 登録済みの変数（登録順に表示する）。 */
    private static final Map<String, Variable> VARIABLES = new LinkedHashMap<>();

    private static void register(String name, String description, String defaultValue) {
        VARIABLES.put(name, new Variable(name, description, defaultValue));
    }

    static {
        register("mode", "作図モード（A / B / C / D）", "");
        register("modeLabel", "モードの名前（例: 画像をもとに再現）", "");
        register("modeGuide", "モードの役割（サーバーが入れます。テンプレートに書かなくても system へ付きます）", "");
        register("resultType", "作成する図の種類（AUTO / GEOMETRY / GRAPH / MIXED）", "AUTO");
        register("resultTypeLabel", "作成する図の種類の名前（例: 関数・方程式のグラフ）", "自動判定");
        register("resultTypeRule", "種類の守り方（サーバーが入れます）", "");
        register("taskCode", "バッチコード（例: batC51-A）", "");
        register("note", "利用者の補足要求（自由記述）", DEFAULT_NONE);
        register("supplements", "モードと種類ごとの補充項目（サーバーが「項目名: 値」の行で並べます）", DEFAULT_UNSPECIFIED);
        register("keepLabels", "元の名前・ラベルを残すか", DEFAULT_KEEP_LABELS);
        register("maxCommands", "コマンドの最大数", "80");
        register("allowedCommands", "使えるコマンド（許可リスト）", "");
        register("outputFormat", "出力形式（JSON / COMMAND）", "JSON");
        register("outputSchema", "出力 Schema（サーバーが DTO から生成して入れます。テンプレートに手書きしない）", "");

        // 補充項目ごとの変数（画面の項目名と同じ並び。{supplements} の代わりに個別に置ける）
        for (AiFigureSupplements.Item item : AiFigureSupplements.all()) {
            register(item.variable(), "補充項目「" + item.label() + "」（未指定なら「指定なし」）", DEFAULT_UNSPECIFIED);
        }

        // 歴史的なテンプレート（モードが無い時代）の変数。今も展開できるように残す
        register("kind", "（旧）分類の日本語", "図形");
        register("subKind", "（旧）図形の種類のコード", "");
        register("figureType", "作図タイプ（幾何図形 / 関数グラフ）", "幾何図形");
    }

    private FigurePromptTemplate() {
    }

    /** 登録済みの変数（説明つき）。 */
    public static Map<String, Variable> variables() {
        return Map.copyOf(VARIABLES);
    }

    /** 画面に出す「使える変数」の一覧（登録順）。 */
    public static String describeVariables() {
        return VARIABLES.values().stream()
                .map(variable -> "{" + variable.name() + "} … " + variable.description())
                .reduce((left, right) -> left + "\n" + right)
                .orElse("");
    }

    /** テンプレートに書かれている変数名（重複は 1 つにまとめる・書かれた順）。 */
    public static Set<String> placeholders(String template) {
        Set<String> found = new LinkedHashSet<>();
        if (template == null) {
            return found;
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            found.add(matcher.group(1));
        }
        return found;
    }

    /** 知らない変数（テンプレートを保存・実行する前に見つける）。 */
    public static Set<String> unknownVariables(String template) {
        Set<String> unknown = new LinkedHashSet<>();
        for (String name : placeholders(template)) {
            if (!VARIABLES.containsKey(name)) {
                unknown.add(name);
            }
        }
        return unknown;
    }

    /**
     * テンプレートを展開する（**空は許さない**。System Prompt のように必須のもの）。
     *
     * @param template 設定のテンプレート（null / 空は例外）
     * @param values   変数の値（null / 空の項目は登録した既定値で埋める）
     * @param what     例外メッセージに出す設定の名前（例: GEOMETRY_AI_A_SYSTEM_PROMPT）
     */
    public static String render(String template, Map<String, String> values, String what) {
        if (template == null || template.isBlank()) {
            throw new ValidationException("AI のプロンプトが設定されていません（" + what + "）。"
                    + "システム設定の「図形管理」を確認してください。");
        }
        return expand(template, values, what);
    }

    /**
     * テンプレートを展開する（**空なら null**）。
     *
     * <p>共用の User Prompt（タスクテンプレート）は必須ではない。モード別に書いてあれば
     * 共用が空でも動くし、どちらも空なら「テンプレート無し」で実行する。</p>
     */
    public static String renderOptional(String template, Map<String, String> values, String what) {
        if (template == null || template.isBlank()) {
            return null;
        }
        return expand(template, values, what);
    }

    private static String expand(String template, Map<String, String> values, String what) {
        Set<String> used = placeholders(template);
        Set<String> unknown = unknownVariables(template);
        if (!unknown.isEmpty()) {
            throw new ValidationException("AI のプロンプトに知らない変数があります（" + what + "）: "
                    + String.join(", ", unknown.stream().map(v -> "{" + v + "}").toList()) + "。"
                    + "使える変数: " + String.join(", ", VARIABLES.keySet().stream().map(v -> "{" + v + "}").toList()));
        }
        String rendered = template;
        for (String name : used) {
            String value = values == null ? null : values.get(name);
            if (value == null || value.isBlank()) {
                value = VARIABLES.get(name).defaultValue();
            }
            rendered = rendered.replace("{" + name + "}", value);
        }
        // 展開し残しがあってはならない（あるとモデルへそのまま渡ってしまう）
        Set<String> left = placeholders(rendered);
        if (!left.isEmpty()) {
            throw new ValidationException("AI のプロンプトの変数を展開できませんでした（" + what + "）: "
                    + String.join(", ", left.stream().map(v -> "{" + v + "}").toList()));
        }
        return rendered;
    }
}

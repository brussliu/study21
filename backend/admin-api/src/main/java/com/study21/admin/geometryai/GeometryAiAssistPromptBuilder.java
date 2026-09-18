package com.study21.admin.geometryai;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 画図助手（batC52）のプロンプトを組み立てる。
 *
 * <p>user-api の同期実装から移植したもの。User Prompt のプレースホルダ
 * {@code {objects}} / {@code {instruction}} / {@code {maxCommands}} / {@code {xml}} を展開する。
 * 作図のオブジェクト名（label）だけを渡し、**XML そのものは渡さない**（プロンプトを太らせない）。</p>
 */
public final class GeometryAiAssistPromptBuilder {

    /** GeoGebra XML からオブジェクト名を取り出す（label="A" / label="a_1"）。 */
    private static final Pattern XML_LABEL = Pattern.compile("label=\"([^\"]{1,40})\"");
    private static final int OBJECT_NAME_MAX = 200;

    private GeometryAiAssistPromptBuilder() {
    }

    /** User Prompt テンプレートを展開する（オブジェクト一覧は XML の名前から作る）。 */
    public static String buildUserPrompt(String template, String instruction, String construction, int maxCommands) {
        return buildUserPrompt(template, instruction, construction, null, null, maxCommands);
    }

    /**
     * User Prompt テンプレートを展開する。
     *
     * @param objectsSummary 画面が作ったオブジェクト一覧（「名前 = 定義（型）」の改行区切り）。
     *                       null・空なら XML のラベルだけを使う（古い画面との互換）。
     * @param failureDetail  前の案が実行できなかった内容（実行できなかった行と理由）。null なら失敗の節を足さない。
     */
    public static String buildUserPrompt(String template, String instruction, String construction,
                                         String objectsSummary, String failureDetail, int maxCommands) {
        String objects = objectsText(construction, objectsSummary);
        String base = template == null || template.isBlank()
                ? "いまの作図のオブジェクト: {objects}\n指示: {instruction}\nコマンドは最大 {maxCommands} 個。"
                : template;
        String prompt = base
                .replace("{objects}", objects)
                .replace("{instruction}", instruction == null ? "" : instruction.trim())
                .replace("{maxCommands}", String.valueOf(maxCommands))
                .replace("{failure}", failureSection(failureDetail))
                .replace("{xml}", "");
        // テンプレートが {failure} を持たないときは、失敗の内容を末尾に足す（画面は自動修正で再依頼する）
        if (!base.contains("{failure}") && failureDetail != null && !failureDetail.isBlank()) {
            prompt = prompt + "\n" + failureSection(failureDetail);
        }
        return prompt;
    }

    /**
     * 前の案が実行できなかった内容（**自動修正**の材料）。
     *
     * <p>画面は実行に失敗すると、同じ指示をこの内容つきでもう一度送る（1 回だけ）。
     * AI は「自分が何を書いて、どこで拒否されたか」を見て直せる（実測: 引数の数の間違いなど）。</p>
     */
    public static String failureSection(String failureDetail) {
        if (failureDetail == null || failureDetail.isBlank()) {
            return "";
        }
        return "前の案は実行できませんでした（同じ間違いを繰り返さないこと）:\n" + failureDetail.trim();
    }

    /** プロンプトへ渡すオブジェクトの一覧（画面が作った一覧を優先し、無ければ XML の名前）。 */
    private static String objectsText(String construction, String objectsSummary) {
        if (objectsSummary != null && !objectsSummary.isBlank()) {
            return objectsSummary.trim();
        }
        List<String> objects = objectNamesOf(construction);
        return objects.isEmpty() ? "（まだありません）" : String.join(", ", objects);
    }

    /** GeoGebra XML からオブジェクト名（label）を取り出す。 */
    public static List<String> objectNamesOf(String construction) {
        if (construction == null || construction.isBlank()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        Matcher matcher = XML_LABEL.matcher(construction);
        while (matcher.find() && names.size() < OBJECT_NAME_MAX) {
            String name = matcher.group(1).trim();
            if (!name.isEmpty() && !names.contains(name)) {
                names.add(name);
            }
        }
        return names;
    }
}

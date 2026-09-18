package com.study21.admin.geometryai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GeometryAiAssistPromptBuilder} の検証。
 *
 * <p>AI が間違えやすい 2 つの材料を確かめる:</p>
 * <ul>
 *   <li>作図のオブジェクト一覧 … 画面が送った「名前 = 定義（型）」を使う（XML の名前だけでは
 *       円の中心・半径が分からない）。送られてこなければ今までどおり XML のラベルで代用する。</li>
 *   <li>前の失敗 … 実行できなかった行と理由を渡す（AI に自動で直させるため）。</li>
 * </ul>
 */
class GeometryAiAssistPromptBuilderTest {

    private static final String TEMPLATE = "いまの作図のオブジェクト: {objects}\n指示: {instruction}";

    @Test
    @DisplayName("画面が送ったオブジェクト一覧（名前 = 定義）をそのまま使う")
    void usesObjectsSummaryFromScreen() {
        String prompt = GeometryAiAssistPromptBuilder.buildUserPrompt(TEMPLATE, "正三角形を描いて",
                "<construction><element label=\"c\"/></construction>",
                "c = Circle((0, 0), 3)（円）", null, 20);

        assertThat(prompt).contains("c = Circle((0, 0), 3)（円）");
        assertThat(prompt).contains("指示: 正三角形を描いて");
        // XML のラベルだけの一覧は使わない（定義の方が情報が多い）
        assertThat(prompt).doesNotContain("いまの作図のオブジェクト: c\n");
    }

    @Test
    @DisplayName("一覧が無ければ XML のラベルで代用する（古い画面との互換）")
    void fallsBackToXmlLabels() {
        String prompt = GeometryAiAssistPromptBuilder.buildUserPrompt(TEMPLATE, "垂線を引いて",
                "<construction><element label=\"A\"/><element label=\"B\"/></construction>", null, null, 20);

        assertThat(prompt).contains("いまの作図のオブジェクト: A, B");
    }

    @Test
    @DisplayName("作図が空のときは「（まだありません）」と書く")
    void writesEmptyNote() {
        String prompt = GeometryAiAssistPromptBuilder.buildUserPrompt(TEMPLATE, "円を描いて", null, null, null, 20);

        assertThat(prompt).contains("（まだありません）");
    }

    @Test
    @DisplayName("前の失敗を渡すと、同じ間違いを繰り返さないよう促す節が入る")
    void appendsFailureSection() {
        String failure = "実行できなかったコマンド:\n  Polygon(A, B, C, 3)";
        String prompt = GeometryAiAssistPromptBuilder.buildUserPrompt(TEMPLATE, "正三角形を描いて",
                null, "c = Circle((0, 0), 3)", failure, 20);

        assertThat(prompt).contains("前の案は実行できませんでした");
        assertThat(prompt).contains("Polygon(A, B, C, 3)");
    }

    @Test
    @DisplayName("テンプレートに {failure} があれば、そこへ差し込む（末尾に二重に出さない）")
    void honoursFailurePlaceholder() {
        String template = "指示: {instruction}\n{failure}";
        String prompt = GeometryAiAssistPromptBuilder.buildUserPrompt(template, "正三角形を描いて",
                null, null, "実行できなかったコマンド:\n  Polygon(A, B, C, 3)", 20);

        assertThat(prompt).contains("前の案は実行できませんでした");
        // 節は 1 回だけ
        assertThat(prompt.split("前の案は実行できませんでした", -1).length - 1).isEqualTo(1);
    }

    @Test
    @DisplayName("失敗が無いときは失敗の節を足さない")
    void omitsFailureSectionWhenAbsent() {
        String prompt = GeometryAiAssistPromptBuilder.buildUserPrompt(TEMPLATE, "円を描いて", null, null, "  ", 20);

        assertThat(prompt).doesNotContain("前の案は実行できませんでした");
    }

    @Test
    @DisplayName("XML からオブジェクト名を取り出す（順序はそのまま・重複しない）")
    void extractsObjectNames() {
        List<String> names = GeometryAiAssistPromptBuilder.objectNamesOf(
                "<construction><element label=\"A\"/><element label=\"c\"/><element label=\"A\"/></construction>");

        assertThat(names).containsExactly("A", "c");
    }
}

package com.study21.admin.japanesewordai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI へ送るプロンプトの組み立て（batC41〜batC44 で共通）。
 *
 * <p>確かめる接縫はこの 1 点: <b>設定のテンプレートと入力語から、system / user の 2 本を作る</b>
 * （純関数。AI も DB も使わない）。</p>
 *
 * <p>2.0 と同じ約束:</p>
 * <ul>
 *   <li>テンプレートの {@code {{kind}}} を取得区分、{@code {{word_json}}} を入力語の JSON に置き換える</li>
 *   <li>入力語の JSON は「見出し語・読み・品詞・JLPT・収録（書籍・分類）・既存の詳細」を含む</li>
 *   <li>E だけは、テンプレートが {@code {{word_json}}} を落としていても対象語が必ず入るよう追記する</li>
 * </ul>
 */
class JapaneseWordAiPromptTest {

    private static final JapaneseWordAiPrompt.WordInput WORD = new JapaneseWordAiPrompt.WordInput(
            101L,
            "愛",
            "あい",
            "名詞",
            "N3",
            List.of(new JapaneseWordAiPrompt.Collection("日本語単語帳①", "Unit001", 12, "名詞", "爱")),
            null);

    /** 設定に入っている形のテンプレート。 */
    private static final String SYSTEM = "日本語の専門家です。出力JSONのキーは usageNotes です。";
    private static final String USER = "取得区分: {{kind}}\n入力:\n{{word_json}}";

    @Test
    @DisplayName("テンプレートの {{kind}} と {{word_json}} を置き換える")
    void substitutesPlaceholders() {
        JapaneseWordAiPrompt.Prompt prompt =
                JapaneseWordAiPrompt.of(SYSTEM, USER, "C", WORD);

        assertThat(prompt.system()).isEqualTo(SYSTEM);
        assertThat(prompt.user()).startsWith("取得区分: C");
        assertThat(prompt.user()).doesNotContain("{{kind}}");
        assertThat(prompt.user()).doesNotContain("{{word_json}}");
        // 入力語がそのまま入る（AI が対象語を読み取れる）
        assertThat(prompt.user()).contains("\"heading\":\"愛\"");
        assertThat(prompt.user()).contains("\"reading\":\"あい\"");
        assertThat(prompt.user()).contains("\"partOfSpeech\":\"名詞\"");
        assertThat(prompt.user()).contains("\"jlptLevel\":\"N3\"");
    }

    @Test
    @DisplayName("入力語の JSON は、詳細がまだ無ければ既存詳細を null にする")
    void omitsMissingDetail() {
        JapaneseWordAiPrompt.Prompt prompt =
                JapaneseWordAiPrompt.of(SYSTEM, USER, "DETAIL", WORD);

        assertThat(prompt.user()).contains("\"detail\":null");
        assertThat(prompt.user()).contains("\"book\":\"日本語単語帳①\"");
        assertThat(prompt.user()).contains("\"category\":\"Unit001\"");
    }

    @Test
    @DisplayName("E は、テンプレートが {{word_json}} を落としていても対象語を必ず入れる")
    void keepsTargetForE() {
        JapaneseWordAiPrompt.Prompt prompt =
                JapaneseWordAiPrompt.of(SYSTEM, "以下の単語を出題してください。", "E", WORD);

        assertThat(prompt.user()).contains("愛");
        assertThat(prompt.user()).contains("あい");
        // 2.0 と同じ「固定値」の断り書きを付ける（参考例と混同させない）
        assertThat(prompt.user()).contains("固定値");
    }

    @Test
    @DisplayName("取得区分から内容種別コードを決める（A_DETAIL / C1_READING / C2_KANJI / D / E）")
    void mapsContentTypes() {
        assertThat(JapaneseWordAiPrompt.contentTypesOf("DETAIL"))
                .containsExactly("A_DETAIL");
        // C は 2 つの問題を作るので、記録も 2 行に分かれる
        assertThat(JapaneseWordAiPrompt.contentTypesOf("C"))
                .containsExactly("C1_READING", "C2_KANJI");
        assertThat(JapaneseWordAiPrompt.contentTypesOf("D")).containsExactly("D_CONTEXT_MEANING");
        assertThat(JapaneseWordAiPrompt.contentTypesOf("E")).containsExactly("E_KANJI_USAGE");
    }

    /** 入力語の中身が、収録や詳細も含めて渡ることを確かめる（詳細ありの場合）。 */
    @Test
    @DisplayName("既存の詳細があるときは入力語の JSON に載せる")
    void includesExistingDetail() {
        Map<String, Object> detail = Map.of("chineseMeaning", "爱");
        JapaneseWordAiPrompt.WordInput withDetail = new JapaneseWordAiPrompt.WordInput(
                101L, "愛", "あい", "名詞", "N3", WORD.collections(), detail);

        JapaneseWordAiPrompt.Prompt prompt =
                JapaneseWordAiPrompt.of(SYSTEM, USER, "DETAIL", withDetail);

        assertThat(prompt.user()).contains("\"detail\":{\"chineseMeaning\":\"爱\"}");
    }
}

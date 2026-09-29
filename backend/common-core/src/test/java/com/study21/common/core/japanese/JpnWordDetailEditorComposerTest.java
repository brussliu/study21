package com.study21.common.core.japanese;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 画面の編集（詳細の Map）→ 新しい版（ヘッダ ＋ 11 の子テーブルの行）の組み立て（切片4）。
 *
 * <p>確かめる接縫は 1 つ（純関数）:
 * {@link JpnWordDetailEditorComposer#compose(Map, JpnWordDetailEntity, JpnWordDetailChildren.Rows, long, long)}。
 * 実 DB も Spring も使わない。</p>
 *
 * <p>このクラスの要は<b>行の出所の判定</b>:
 * 元の版と内容がまったく同じ行は元の出所（AI の行は BATCH のまま）、
 * 変わった行・足した行は APP（人が触った行）、元にあって要求に無い行は入れない。</p>
 */
class JpnWordDetailEditorComposerTest {

    private static final long WORD_ID = 101L;
    private static final long ACCOUNT_ID = 2L;

    /** 今の有効版のヘッダ（AI が作った版）。 */
    private static JpnWordDetailEntity originHeader() {
        JpnWordDetailEntity header = new JpnWordDetailEntity();
        header.setDetailId(900L);
        header.setWordId(WORD_ID);
        header.setContentVersion(3);
        header.setStateCode("ACTIVE");
        header.setGenerationId(77L);
        header.setAiProvider("qwen");
        header.setAiModel("qwen3.7-plus");
        header.setManualCorrected(false);
        header.setCoreMeaning("AI の核心");
        header.setDescriptionJa("AI の説明。");
        header.setDescriptionZh("AI 的说明。");
        header.setPartOfSpeech("名詞");
        header.setJlptLevel("N3");
        header.setConjugation("なし");
        header.setTransitivity("NONE");
        header.setImportance(5);
        header.setMemoryHint("AI のヒント");
        header.setMemoryHintBasis("AI の根拠");
        header.setPronunciationJson("{\"reading\":\"あい\"}");
        header.setConjugationsJson("[{\"form\":\"て形\",\"value\":\"愛して\"}]");
        header.setTransitivityPairJson("{\"transitive\":\"愛する\"}");
        header.setStructuredJson("{\"detail\":{\"jlpt\":\"N3\"}}");
        return header;
    }

    private static JpnWordDetailChildren.Example example(int orderNo, String japanese, String chinese,
                                                         String sourceCode) {
        JpnWordDetailChildren.Example row = new JpnWordDetailChildren.Example();
        row.setOrderNo(orderNo);
        row.setJapanese(japanese);
        row.setChinese(chinese);
        row.setSourceCode(sourceCode);
        row.setManualCorrected("APP".equals(sourceCode));
        return row;
    }

    private static JpnWordDetailChildren.Sense sense(String japanese, String sourceCode) {
        JpnWordDetailChildren.Sense row = new JpnWordDetailChildren.Sense();
        row.setOrderNo(1);
        row.setSenseNumber(1);
        row.setJapanese(japanese);
        row.setSourceCode(sourceCode);
        row.setManualCorrected("APP".equals(sourceCode));
        return row;
    }

    /** 元の版の段落（例文 2 件は AI・語義 1 件は AI）。 */
    private static JpnWordDetailChildren.Rows originRows() {
        return new JpnWordDetailChildren.Rows(
                List.of(sense("AI の語義。", "BATCH")),
                List.of(example(1, "AI の例文1。", "AI 例句1。", "BATCH"),
                        example(2, "AI の例文2。", "AI 例句2。", "BATCH")),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private static Map<String, Object> exampleRow(String japanese, String chinese) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("japanese", japanese);
        row.put("chinese", chinese);
        return row;
    }

    @Test
    @DisplayName("要求の段落で版を作り直す。内容が同じ行は元の出所・変わった行は APP・無い行は入れない")
    void resolvesRowSourceByContent() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("descriptionJa", "人が直した説明。");
        detail.put("senses", List.of(Map.of("number", 1, "japanese", "AI の語義。")));
        detail.put("examples", List.of(
                exampleRow("AI の例文1。", "AI 例句1。"),        // まったく同じ → BATCH のまま
                exampleRow("AI の例文2。", "人が直した例句2。"))); // 変わった → APP

        JpnWordDetailEditorComposer.NewVersion created = JpnWordDetailEditorComposer.compose(
                detail, originHeader(), originRows(), WORD_ID, ACCOUNT_ID);

        assertThat(created.manual()).isTrue();
        // 版のヘッダ: 元詳細ID・生成ID・出所・AI
        JpnWordDetailEntity header = created.header();
        assertThat(header.getWordId()).isEqualTo(WORD_ID);
        assertThat(header.getStateCode()).isEqualTo("ACTIVE");
        assertThat(header.getOriginDetailId()).isEqualTo(900L);
        assertThat(header.getGenerationId()).isNull();
        assertThat(header.getManualCorrected()).isTrue();
        assertThat(header.getCreatedBy()).isEqualTo(ACCOUNT_ID);
        assertThat(header.getAiProvider()).isEqualTo("qwen");
        assertThat(header.getAiModel()).isEqualTo("qwen3.7-plus");
        // AI の生の応答は引き継ぐ（どの応答から生まれた版か）
        assertThat(header.getStructuredJson()).isEqualTo("{\"detail\":{\"jlpt\":\"N3\"}}");
        // 語レベルの値は要求が優先。無いキーは元の版
        assertThat(header.getDescriptionJa()).isEqualTo("人が直した説明。");
        assertThat(header.getDescriptionZh()).isEqualTo("AI 的说明。");
        assertThat(header.getCoreMeaning()).isEqualTo("AI の核心");
        // 要求に無い小構造（発音・活用形・自他対応）も元の版を持ち越す
        assertThat(header.getPronunciationJson()).isEqualTo("{\"reading\":\"あい\"}");
        assertThat(header.getConjugationsJson()).contains("て形");
        assertThat(header.getTransitivityPairJson()).contains("愛する");

        // 語義は内容が同じ → 元の出所（BATCH）を引き継ぐ
        assertThat(created.children().senses()).hasSize(1);
        assertThat(created.children().senses().get(0).getSourceCode()).isEqualTo("BATCH");
        assertThat(created.children().senses().get(0).getManualCorrected()).isFalse();
        assertThat(created.children().senses().get(0).getOrderNo()).isEqualTo(1);

        // 例文は 1 行目 BATCH・2 行目 APP。表示順は 1 から振り直す
        assertThat(created.children().examples()).hasSize(2);
        assertThat(created.children().examples().get(0).getSourceCode()).isEqualTo("BATCH");
        assertThat(created.children().examples().get(0).getManualCorrected()).isFalse();
        assertThat(created.children().examples().get(1).getSourceCode()).isEqualTo("APP");
        assertThat(created.children().examples().get(1).getManualCorrected()).isTrue();
        assertThat(created.children().examples().get(1).getChinese()).isEqualTo("人が直した例句2。");
        assertThat(created.children().examples()).extracting(JpnWordDetailChildren.Example::getOrderNo)
                .containsExactly(1, 2);
        // 要求に無い段落（文型・会話…）は入れない＝その版から消える
        assertThat(created.children().patterns()).isEmpty();
        assertThat(created.children().dialogs()).isEmpty();
    }

    @Test
    @DisplayName("要求に段落が無ければ、その版の段落はすべて空（画面の内容が版そのもの）")
    void dropsSectionsMissingFromRequest() {
        Map<String, Object> detail = Map.of("descriptionJa", "人が直した説明。");

        JpnWordDetailEditorComposer.NewVersion created = JpnWordDetailEditorComposer.compose(
                detail, originHeader(), originRows(), WORD_ID, ACCOUNT_ID);

        assertThat(created.children().senses()).isEmpty();
        assertThat(created.children().examples()).isEmpty();
        assertThat(created.manual()).isFalse();
        assertThat(created.header().getManualCorrected()).isFalse();
    }

    @Test
    @DisplayName("新しく足した行は APP（人が作った行）")
    void marksAddedRowsAsHuman() {
        Map<String, Object> detail = Map.of(
                "examples", List.of(exampleRow("新しく足した例文。", "新加的例句。")));

        JpnWordDetailEditorComposer.NewVersion created = JpnWordDetailEditorComposer.compose(
                detail, originHeader(), originRows(), WORD_ID, ACCOUNT_ID);

        assertThat(created.children().examples()).hasSize(1);
        assertThat(created.children().examples().get(0).getSourceCode()).isEqualTo("APP");
        assertThat(created.children().examples().get(0).getManualCorrected()).isTrue();
    }

    @Test
    @DisplayName("詳細がまだ無い語（元の版が null）でも最初の版を作れる。元詳細ID は null")
    void composesFirstVersion() {
        Map<String, Object> detail = Map.of("descriptionJa", "最初の説明。");

        JpnWordDetailEditorComposer.NewVersion created = JpnWordDetailEditorComposer.compose(
                detail, null, null, WORD_ID, ACCOUNT_ID);

        assertThat(created.header().getOriginDetailId()).isNull();
        assertThat(created.header().getGenerationId()).isNull();
        assertThat(created.header().getDescriptionJa()).isEqualTo("最初の説明。");
        assertThat(created.header().getWordId()).isEqualTo(WORD_ID);
        assertThat(created.children().senses()).isEmpty();
    }

    @Test
    @DisplayName("会話は 2 層。変わっていない会話と発言は出所を引き継ぎ、直した発言は APP にする")
    void resolvesDialogSourcePerLine() {
        JpnWordDetailChildren.DialogLine line1 = new JpnWordDetailChildren.DialogLine();
        line1.setOrderNo(1);
        line1.setSpeaker("A");
        line1.setJapanese("いらっしゃい。");
        line1.setChinese("欢迎。");
        line1.setSourceCode("BATCH");
        line1.setManualCorrected(false);
        JpnWordDetailChildren.DialogLine line2 = new JpnWordDetailChildren.DialogLine();
        line2.setOrderNo(2);
        line2.setSpeaker("B");
        line2.setJapanese("これください。");
        line2.setChinese("请给我这个。");
        line2.setSourceCode("BATCH");
        line2.setManualCorrected(false);
        JpnWordDetailChildren.Dialog dialog = new JpnWordDetailChildren.Dialog();
        dialog.setOrderNo(1);
        dialog.setScene("店");
        dialog.setSourceCode("BATCH");
        dialog.setManualCorrected(false);
        dialog.setLines(List.of(line1, line2));
        JpnWordDetailChildren.Rows origin = new JpnWordDetailChildren.Rows(
                List.of(), List.of(), List.of(), List.of(dialog), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());

        Map<String, Object> detail = Map.of("dialogs", List.of(Map.of(
                "scene", "店",
                "lines", List.of(
                        Map.of("speaker", "A", "japanese", "いらっしゃい。", "chinese", "欢迎。"),
                        Map.of("speaker", "B", "japanese", "これください。", "chinese", "请给我这个。")))));

        JpnWordDetailEditorComposer.NewVersion created = JpnWordDetailEditorComposer.compose(
                detail, originHeader(), origin, WORD_ID, ACCOUNT_ID);

        assertThat(created.children().dialogs()).hasSize(1);
        assertThat(created.children().dialogs().get(0).getSourceCode()).isEqualTo("BATCH");
        assertThat(created.children().dialogs().get(0).getLines()).extracting(
                        JpnWordDetailChildren.DialogLine::getSourceCode)
                .containsExactly("BATCH", "BATCH");
        assertThat(created.children().dialogs().get(0).getLines()).extracting(
                        JpnWordDetailChildren.DialogLine::getOrderNo)
                .containsExactly(1, 2);

        // 発言を 1 つ直すと会話ごと APP になる（人が直した会話は AI に上書きされない）
        Map<String, Object> changed = Map.of("dialogs", List.of(Map.of(
                "scene", "店",
                "lines", List.of(
                        Map.of("speaker", "A", "japanese", "いらっしゃい。", "chinese", "欢迎。"),
                        Map.of("speaker", "B", "japanese", "これください。", "chinese", "我要这个。")))));
        JpnWordDetailEditorComposer.NewVersion edited = JpnWordDetailEditorComposer.compose(
                changed, originHeader(), origin, WORD_ID, ACCOUNT_ID);

        assertThat(edited.children().dialogs().get(0).getSourceCode()).isEqualTo("APP");
        assertThat(edited.children().dialogs().get(0).getManualCorrected()).isTrue();
    }

    @Test
    @DisplayName("列の CHECK に無い値は入れない（例文のレベル・注意の区分・関連語の関係・練習の種別）")
    void dropsValuesThatBreakColumnChecks() {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("examples", List.of(Map.of("japanese", "例文。", "level", "UNKNOWN")));
        detail.put("cautions", List.of(Map.of("kind", "OTHER", "title", "見出し")));
        detail.put("relatedWords", List.of(Map.of("relation", "その他", "heading", "関連")));
        detail.put("practices", List.of(Map.of("kind", "OTHER", "question", "問題。")));

        JpnWordDetailEditorComposer.NewVersion created = JpnWordDetailEditorComposer.compose(
                detail, originHeader(), originRows(), WORD_ID, ACCOUNT_ID);

        assertThat(created.children().examples().get(0).getLevel()).isNull();
        assertThat(created.children().cautions().get(0).getKind()).isNull();
        assertThat(created.children().relatedWords().get(0).getRelation()).isNull();
        assertThat(created.children().practices().get(0).getKind()).isNull();
    }

    @Test
    @DisplayName("練習の選択肢は文字列の配列（JSONB の列に入れる形）")
    void keepsPracticeChoicesAsJson() {
        Map<String, Object> detail = Map.of("practices", List.of(Map.of(
                "kind", "PARTICLE", "question", "問題。", "choices", List.of("に", "を"), "answer", "を")));

        JpnWordDetailEditorComposer.NewVersion created = JpnWordDetailEditorComposer.compose(
                detail, originHeader(), originRows(), WORD_ID, ACCOUNT_ID);

        assertThat(created.children().practices().get(0).getChoicesJson()).isEqualTo("[\"に\",\"を\"]");
        assertThat(created.children().practices().get(0).getKind()).isEqualTo("PARTICLE");
    }
}

package com.study21.common.core.japanese;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 詳細の「版」＋ 11 の子テーブル → 旧 {@code 詳細JSON} と同じ detail オブジェクトの組み立て。
 *
 * <p>確かめる接縫は 1 つ（純関数）:
 * {@link JpnWordDetailAssembler#assemble(JpnWordDetailEntity, JpnWordDetailChildren.Rows)}。
 * 実 DB も Spring も使わない。</p>
 *
 * <p>期待値は「文字列に日本語が含まれるか」ではなく、<b>キーを 1 つずつ</b>確かめる
 * （キー名の取り違えを contains では見逃すため）。基準は admin-api の
 * {@code JapaneseWordAiDtoMapper.toDetailJson} が出すキー。</p>
 */
class JpnWordDetailAssemblerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 版の行（語レベルの内容はすべて埋める）。 */
    private static JpnWordDetailEntity header() {
        JpnWordDetailEntity header = new JpnWordDetailEntity();
        header.setDetailId(900L);
        header.setWordId(101L);
        header.setContentVersion(3);
        header.setStateCode("ACTIVE");
        header.setOriginDetailId(800L);
        header.setGenerationId(77L);
        header.setAiProvider("qwen");
        header.setAiModel("qwen3.7-plus");
        header.setManualCorrected(false);
        header.setCoreMeaning("爱；喜爱");
        header.setDescriptionJa("人や物を大切に思う気持ち。");
        header.setDescriptionZh("对人或物怀有珍视的感情。");
        header.setPartOfSpeech("名詞");
        header.setJlptLevel("N3");
        header.setConjugation("なし");
        header.setTransitivity("NONE");
        header.setImportance(5);
        header.setMemoryHint("「心」が真ん中にある字。");
        header.setMemoryHintBasis("漢字の形");
        header.setPronunciationJson("""
                {"reading":"あい","accentType":1,"accentNotation":"あꜜい",
                 "hint":"「あ」を高く。","hasAudioSample":false}
                """);
        header.setConjugationsJson("""
                [{"form":"て形","value":"愛して","example":"家族を愛している。"}]
                """);
        header.setTransitivityPairJson("""
                {"intransitive":"愛される","transitive":"愛する","particleNote":"～を／～に",
                 "intransitiveExample":"彼は愛されている。","transitiveExample":"彼は家族を愛している。"}
                """);
        header.setStructuredJson("""
                {"detail":{"jlpt":"N3","senses":[{"number":1},{"number":2}]}}
                """);
        return header;
    }

    private static JpnWordDetailChildren.Sense sense() {
        JpnWordDetailChildren.Sense row = new JpnWordDetailChildren.Sense();
        row.setOrderNo(1);
        row.setSenseNumber(1);
        row.setJapanese("大切に思う気持ち。");
        row.setChinese("爱；喜爱");
        row.setContext("家族・友人");
        row.setStyle("NEUTRAL");
        row.setNoteJapanese("対象が広い。");
        row.setNoteChinese("对象很广。");
        row.setManualCorrected(false);
        row.setSourceCode("BATCH");
        return row;
    }

    private static JpnWordDetailChildren.Example example() {
        JpnWordDetailChildren.Example row = new JpnWordDetailChildren.Example();
        row.setOrderNo(1);
        row.setJapanese("親の愛は無条件だ。");
        row.setReading("おやのあいはむじょうけんだ。");
        row.setChinese("父母的爱是无条件的。");
        row.setSenseNumber(1);
        row.setSource("家族");
        row.setLevel("BASIC");
        row.setManualCorrected(false);
        row.setSourceCode("BATCH");
        return row;
    }

    private static JpnWordDetailChildren.DialogLine line() {
        JpnWordDetailChildren.DialogLine line = new JpnWordDetailChildren.DialogLine();
        line.setOrderNo(1);
        line.setSpeaker("先生");
        line.setJapanese("家族を愛していますか。");
        line.setChinese("你爱家人吗。");
        line.setManualCorrected(false);
        return line;
    }

    private static JpnWordDetailChildren.Dialog dialog() {
        JpnWordDetailChildren.Dialog row = new JpnWordDetailChildren.Dialog();
        row.setDialogId(31L);
        row.setOrderNo(1);
        row.setScene("職員室で先生に相談する");
        row.setManualCorrected(false);
        row.setLines(List.of(line()));
        return row;
    }

    private static JpnWordDetailChildren.Practice practice() {
        JpnWordDetailChildren.Practice row = new JpnWordDetailChildren.Practice();
        row.setOrderNo(1);
        row.setKind("PARTICLE");
        row.setQuestion("音楽（ ）愛する。");
        row.setQuestionChinese("热爱音乐。");
        row.setChoicesJson("[\"に\",\"を\"]");
        row.setFreeWriting(false);
        row.setAnswer("を");
        row.setExplanation("対象は「を」。");
        row.setManualCorrected(false);
        return row;
    }

    /** 11 の段落すべてに 1 件ずつ入れた版。 */
    private static JpnWordDetailChildren.Rows allSections() {
        JpnWordDetailChildren.Pattern pattern = new JpnWordDetailChildren.Pattern();
        pattern.setOrderNo(1);
        pattern.setPattern("**を**愛する");
        pattern.setReading("をあいする");
        pattern.setChinese("爱…");
        pattern.setExample("音楽を愛する。");
        pattern.setExampleChinese("热爱音乐。");

        JpnWordDetailChildren.Synonym synonym = new JpnWordDetailChildren.Synonym();
        synonym.setOrderNo(1);
        synonym.setHeading("恋");
        synonym.setReading("こい");
        synonym.setChinese("恋爱");
        synonym.setShared("大切に思う");
        synonym.setDifference("「恋」は恋愛感情。");
        synonym.setScene("恋愛の場面");

        JpnWordDetailChildren.Caution caution = new JpnWordDetailChildren.Caution();
        caution.setOrderNo(1);
        caution.setKind("MEANING");
        caution.setTitle("「恋」との違い");
        caution.setWrong("文化を恋する。");
        caution.setCorrect("文化を愛する。");
        caution.setReason("「恋」は恋愛に限る。");

        JpnWordDetailChildren.Collocation collocation = new JpnWordDetailChildren.Collocation();
        collocation.setOrderNo(1);
        collocation.setExpression("愛を込める");
        collocation.setReading("あいをこめる");
        collocation.setChinese("倾注爱意");
        collocation.setUsage("手紙で");
        collocation.setExampleJapanese("心を込めて愛を伝える。");
        collocation.setExampleChinese("用心传达爱意。");

        JpnWordDetailChildren.RelatedWord related = new JpnWordDetailChildren.RelatedWord();
        related.setOrderNo(1);
        related.setRelation("類義語");
        related.setHeading("恋");
        related.setReading("こい");
        related.setChinese("恋爱");

        JpnWordDetailChildren.UsageNote note = new JpnWordDetailChildren.UsageNote();
        note.setOrderNo(1);
        note.setSenseNumber(1);
        note.setRegister("どちらも");
        note.setPoliteness("普通");
        note.setAudience("家族に");
        note.setNote("書き言葉でも使う。");

        return new JpnWordDetailChildren.Rows(List.of(sense()), List.of(example()), List.of(pattern),
                List.of(dialog()), List.of(synonym), List.of(caution), List.of(collocation),
                List.of(related), List.of(note), List.of(practice()));
    }

    private static JsonNode json(Map<String, Object> detail) {
        return MAPPER.valueToTree(detail);
    }

    private static JsonNode assembleAll() {
        return json(JpnWordDetailAssembler.assemble(header(), allSections()));
    }

    @Test
    @DisplayName("ルートのキーは書き側（toDetailJson）と同じ 26 個で、順番も同じ")
    void keepsRootKeys() {
        JsonNode json = assembleAll();

        assertThat(json.fieldNames()).toIterable().containsExactly(
                "coreMeaning", "chineseMeaning", "descriptionJa", "descriptionZh", "partOfSpeech",
                "jlptLevel", "conjugation", "transitivity", "importance", "manuallyCorrected",
                "senses", "examples", "patterns", "dialogs", "synonyms", "cautions", "conjugations",
                "transitivityPair", "pronunciation", "pronunciations", "collocations", "relatedWords",
                "usageNotes", "memoryHint", "practices", "structured");
    }

    @Test
    @DisplayName("語レベルの値は版の行から取る（chineseMeaning は coreMeaning と同じ値）")
    void mapsHeaderScalars() {
        JsonNode json = assembleAll();

        assertThat(json.path("coreMeaning").asText()).isEqualTo("爱；喜爱");
        assertThat(json.path("chineseMeaning").asText()).isEqualTo("爱；喜爱");
        assertThat(json.path("descriptionJa").asText()).isEqualTo("人や物を大切に思う気持ち。");
        assertThat(json.path("descriptionZh").asText()).isEqualTo("对人或物怀有珍视的感情。");
        assertThat(json.path("partOfSpeech").asText()).isEqualTo("名詞");
        assertThat(json.path("jlptLevel").asText()).isEqualTo("N3");
        assertThat(json.path("conjugation").asText()).isEqualTo("なし");
        assertThat(json.path("transitivity").asText()).isEqualTo("NONE");
        assertThat(json.path("importance").asInt()).isEqualTo(5);
        assertThat(json.path("manuallyCorrected").asBoolean()).isFalse();
        assertThat(json.path("memoryHint").path("hint").asText()).isEqualTo("「心」が真ん中にある字。");
        assertThat(json.path("memoryHint").path("basis").asText()).isEqualTo("漢字の形");
    }

    @Test
    @DisplayName("段落は子テーブルの行から取る（語義・例文・文型・類義語・注意・コロケーション・関連語・使用場面）")
    void mapsParagraphs() {
        JsonNode json = assembleAll();

        assertThat(json.path("senses").path(0).path("number").asInt()).isEqualTo(1);
        assertThat(json.path("senses").path(0).path("japanese").asText()).isEqualTo("大切に思う気持ち。");
        assertThat(json.path("senses").path(0).path("noteJapanese").asText()).isEqualTo("対象が広い。");

        assertThat(json.path("examples").path(0).path("reading").asText())
                .isEqualTo("おやのあいはむじょうけんだ。");
        assertThat(json.path("examples").path(0).path("senseNumber").asInt()).isEqualTo(1);
        assertThat(json.path("examples").path(0).path("level").asText()).isEqualTo("BASIC");

        assertThat(json.path("patterns").path(0).path("pattern").asText()).isEqualTo("**を**愛する");
        assertThat(json.path("patterns").path(0).path("exampleChinese").asText()).isEqualTo("热爱音乐。");

        assertThat(json.path("synonyms").path(0).path("difference").asText()).isEqualTo("「恋」は恋愛感情。");
        assertThat(json.path("cautions").path(0).path("kind").asText()).isEqualTo("MEANING");
        assertThat(json.path("cautions").path(0).path("wrong").asText()).isEqualTo("文化を恋する。");
        assertThat(json.path("collocations").path(0).path("usage").asText()).isEqualTo("手紙で");
        assertThat(json.path("relatedWords").path(0).path("relation").asText()).isEqualTo("類義語");
        assertThat(json.path("usageNotes").path(0).path("register").asText()).isEqualTo("どちらも");
        assertThat(json.path("usageNotes").path(0).path("senseNumber").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("会話は dialogs[].lines[] の 2 層に戻す（会話行は 会話ID で親にぶら下がる）")
    void mapsDialogs() {
        JsonNode json = assembleAll();

        assertThat(json.path("dialogs")).hasSize(1);
        assertThat(json.path("dialogs").path(0).path("scene").asText()).isEqualTo("職員室で先生に相談する");
        assertThat(json.path("dialogs").path(0).path("lines")).hasSize(1);
        assertThat(json.path("dialogs").path(0).path("lines").path(0).path("speaker").asText()).isEqualTo("先生");
        assertThat(json.path("dialogs").path(0).path("lines").path(0).path("japanese").asText())
                .isEqualTo("家族を愛していますか。");
        assertThat(json.path("dialogs").path(0).path("lines").path(0).path("chinese").asText())
                .isEqualTo("你爱家人吗。");
    }

    @Test
    @DisplayName("練習の choices は文字列の配列にする（選択肢JSON をそのまま出さない）")
    void mapsPractices() {
        JsonNode json = assembleAll();

        assertThat(json.path("practices")).hasSize(1);
        assertThat(json.path("practices").path(0).path("kind").asText()).isEqualTo("PARTICLE");
        assertThat(json.path("practices").path(0).path("choices").isArray()).isTrue();
        assertThat(json.path("practices").path(0).path("choices").path(0).asText()).isEqualTo("に");
        assertThat(json.path("practices").path(0).path("choices").path(1).asText()).isEqualTo("を");
        assertThat(json.path("practices").path(0).path("answer").asText()).isEqualTo("を");
        assertThat(json.path("practices").path(0).path("freeWriting").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("小構造は版の JSONB の列から取る（活用形・自他対応・発音・structured）")
    void mapsHeaderJsonColumns() {
        JsonNode json = assembleAll();

        assertThat(json.path("conjugations").path(0).path("form").asText()).isEqualTo("て形");
        assertThat(json.path("conjugations").path(0).path("value").asText()).isEqualTo("愛して");

        assertThat(json.path("transitivityPair").path("intransitive").asText()).isEqualTo("愛される");
        assertThat(json.path("transitivityPair").path("particleNote").asText()).isEqualTo("～を／～に");

        assertThat(json.path("pronunciation").path("reading").asText()).isEqualTo("あい");
        assertThat(json.path("pronunciation").path("accentType").asInt()).isEqualTo(1);
        assertThat(json.path("pronunciation").path("accentNotation").asText()).isEqualTo("あꜜい");
        assertThat(json.path("pronunciation").path("hint").asText()).isEqualTo("「あ」を高く。");
        assertThat(json.path("pronunciation").path("hasAudioSample").asBoolean()).isFalse();

        // structured は AI の生の応答（元レスポンスJSON）
        assertThat(json.path("structured").path("detail").path("jlpt").asText()).isEqualTo("N3");
        assertThat(json.path("structured").path("detail").path("senses")).hasSize(2);
    }

    @Test
    @DisplayName("pronunciations は発音から作る 0/1 件の配列（moraCount は読みの文字数、音声は null）")
    void derivesPronunciations() {
        JsonNode json = assembleAll();

        assertThat(json.path("pronunciations")).hasSize(1);
        assertThat(json.path("pronunciations").path(0).path("reading").asText()).isEqualTo("あい");
        assertThat(json.path("pronunciations").path(0).path("accentNotation").asText()).isEqualTo("あꜜい");
        assertThat(json.path("pronunciations").path(0).path("accentType").asInt()).isEqualTo(1);
        // 2 文字（「あ」「い」）。DTO に盛り数が無いので読みの文字数で埋める
        assertThat(json.path("pronunciations").path(0).path("moraCount").asInt()).isEqualTo(2);
        assertThat(json.path("pronunciations").path(0).path("audioUrl").isNull()).isTrue();
        assertThat(json.path("pronunciations").path(0).path("audioProvider").isNull()).isTrue();
    }

    @Test
    @DisplayName("発音が空の版は pronunciation が null・pronunciations が空（空オブジェクトを出さない）")
    void keepsEmptyPronunciationNull() {
        JpnWordDetailEntity header = header();
        header.setPronunciationJson("{}");
        header.setConjugationsJson("[]");
        header.setMemoryHint(null);
        header.setMemoryHintBasis(null);
        header.setTransitivityPairJson(null);

        JsonNode json = json(JpnWordDetailAssembler.assemble(header, JpnWordDetailChildren.Rows.empty()));

        assertThat(json.path("pronunciation").isNull()).isTrue();
        assertThat(json.path("pronunciations")).isEmpty();
        assertThat(json.path("transitivityPair").isNull()).isTrue();
        assertThat(json.path("memoryHint").isNull()).isTrue();
        // 段落は空配列（キーは消さない。画面は空配列で耐える）
        for (String key : List.of("senses", "examples", "patterns", "dialogs", "synonyms",
                "cautions", "conjugations", "collocations", "relatedWords", "usageNotes", "practices")) {
            assertThat(json.path(key)).as("%s", key).isEmpty();
        }
    }

    @Test
    @DisplayName("詳細が無い語は null（画面は詳細の節を出さない）")
    void returnsNullWithoutHeader() {
        assertThat(JpnWordDetailAssembler.assemble(null, JpnWordDetailChildren.Rows.empty())).isNull();
    }

    @Test
    @DisplayName("子の行が人が直したものなら manuallyCorrected は true（版のフラグが false でも）")
    void marksManuallyCorrectedByChildRow() {
        JpnWordDetailChildren.Example example = example();
        example.setManualCorrected(true);
        example.setSourceCode("APP");
        JpnWordDetailChildren.Rows rows = new JpnWordDetailChildren.Rows(List.of(), List.of(example),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());

        JsonNode json = json(JpnWordDetailAssembler.assemble(header(), rows));

        assertThat(json.path("manuallyCorrected").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("壊れた JSONB でも落ちない（空として扱う）")
    void survivesBrokenJsonColumns() {
        JpnWordDetailEntity header = header();
        header.setPronunciationJson("これは JSON ではありません");
        header.setConjugationsJson("{\"form\":\"て形\"}");
        header.setStructuredJson("");

        JpnWordDetailChildren.Practice practice = practice();
        practice.setChoicesJson("[\"に\", 3]");

        JsonNode json = json(JpnWordDetailAssembler.assemble(header,
                new JpnWordDetailChildren.Rows(List.of(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(practice))));

        assertThat(json.path("pronunciation").isNull()).isTrue();
        assertThat(json.path("conjugations")).isEmpty();
        assertThat(json.path("structured").isObject()).isTrue();
        assertThat(json.path("structured").isEmpty()).isTrue();
        // 文字列でない要素は落とす
        assertThat(json.path("practices").path(0).path("choices")).hasSize(1);
    }

    @Test
    @DisplayName("語義番号が無ければ 1 から振る（例文と使用場面が番号で結び付くように）")
    void numbersSensesWithoutNumber() {
        JpnWordDetailChildren.Sense first = sense();
        first.setSenseNumber(null);
        JpnWordDetailChildren.Sense second = sense();
        second.setSenseNumber(0);

        JsonNode json = json(JpnWordDetailAssembler.assemble(header(),
                new JpnWordDetailChildren.Rows(List.of(first, second), List.of(), List.of(), List.of(),
                        List.of(), List.of(), List.of(), List.of(), List.of(), List.of())));

        assertThat(json.path("senses").path(0).path("number").asInt()).isEqualTo(1);
        assertThat(json.path("senses").path(1).path("number").asInt()).isEqualTo(2);
    }
}

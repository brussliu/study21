package com.study21.admin.japanesewordai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.admin.geometryai.GeometryAiConnectionResolver;
import com.study21.admin.japanesewordai.dto.JapaneseWordAiDtos;
import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI の応答（詳細の JSON）→ <b>新しい版</b>（版のヘッダ ＋ 11 の子テーブルの行）へ移す純関数。
 *
 * <p>確かめる接縫は 1 つ（AI も DB も触らない）:
 * {@link JapaneseWordAiDetailComposer#compose(String, JpnWordDetailEntity, Long, String, String)}。</p>
 *
 * <p>キー名の基準は {@link JapaneseWordAiDtoMapper#parseDetail(String)} が出す詳細
 * （＝旧 {@code 詳細JSON} とまったく同じ形）。ここはそれを表の列へ崩す。</p>
 *
 * <p>人が作った行を AI が消さない仕組みは 2 段構え:
 * <b>①ここで読む前に既存の版から人の行を複製する</b>（{@link JapaneseWordAiDetailWriter}）＋
 * <b>②AI の新しい行は複製した行の後ろに付ける</b>（{@link JapaneseWordAiDetailComposer}）。
 * ここでは ②を確かめる（①は Writer のテスト）。</p>
 */
class JapaneseWordAiDetailComposerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static String detailJson() {
        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail("""
                {"detail":{
                 "coreMeaning":"爱；喜爱","descriptionJa":"大切に思う気持ち。","descriptionZh":"珍视的感情。",
                 "partOfSpeech":"名詞","jlpt":"N3","conjugation":"なし","transitivity":"NONE","importance":5,
                 "senses":[{"number":1,"japanese":"大切に思う気持ち。","chinese":"爱；喜爱",
                            "context":"家族・友人","style":"NEUTRAL","noteJapanese":"対象が広い。","noteChinese":"对象很广。"}],
                 "examples":[{"japanese":"親の愛は無条件だ。","reading":"おやのあいはむじょうけんだ。",
                              "chinese":"父母的爱是无条件的。","senseNumber":1,"source":"家族","level":"BASIC"},
                             {"japanese":"彼は植物を愛している。","reading":"かれはしょくぶつをあいしている。",
                              "chinese":"他热爱植物。","senseNumber":1,"level":"APPLIED"}],
                 "patterns":[{"pattern":"**を**愛する","reading":"をあいする","chinese":"爱…",
                              "example":"音楽を愛する。","exampleChinese":"热爱音乐。"}],
                 "dialogs":[{"scene":"職員室で先生に相談する",
                             "lines":[{"speaker":"先生","japanese":"家族を愛していますか。","chinese":"你爱家人吗。"}]}],
                 "synonyms":[{"heading":"恋","reading":"こい","chinese":"恋爱","shared":"大切に思う",
                              "difference":"「恋」は恋愛感情。","scene":"恋愛の場面"}],
                 "cautions":[{"kind":"MEANING","title":"「恋」との違い","wrong":"文化を恋する。",
                              "correct":"文化を愛する。","reason":"「恋」は恋愛に限る。"}],
                 "conjugations":[{"form":"て形","value":"愛して","example":"家族を愛している。"}],
                 "transitivityPair":{"intransitive":"愛される","transitive":"愛する","particleNote":"～を／～に",
                                      "intransitiveExample":"彼は愛されている。",
                                      "transitiveExample":"彼は家族を愛している。"},
                 "pronunciation":{"reading":"あい","accentType":1,"accentNotation":"あꜜい",
                                  "hint":"「あ」を高く。","hasAudioSample":false},
                 "collocations":[{"expression":"愛を込める","reading":"あいをこめる","chinese":"倾注爱意",
                                  "usage":"手紙で","exampleJapanese":"心を込めて愛を伝える。",
                                  "exampleChinese":"用心传达爱意。"},
                                 {"expression":"愛が深い","chinese":"爱意深厚"}],
                 "relatedWords":[{"relation":"類義語","heading":"恋","reading":"こい","chinese":"恋爱"}],
                 "usageNotes":[{"register":"どちらも","politeness":"普通","audience":"家族に",
                                "note":"書き言葉でも使う。","senseNumber":1}],
                 "memoryHint":{"hint":"「心」が真ん中にある字。","basis":"漢字の形"},
                 "practices":[{"kind":"PARTICLE","question":"音楽（ ）愛する。","questionChinese":"热爱音乐。",
                               "choices":["に","を"],"answer":"を","explanation":"対象は「を」。",
                               "freeWriting":false}]}}
                """);
        assertThat(result.isSuccess()).isTrue();
        return result.detailJson();
    }

    /** 基にする既存の版（内容版数 3。人の行が 5 行ある想定）。 */
    private static JpnWordDetailEntity originVersion() {
        JpnWordDetailEntity origin = new JpnWordDetailEntity();
        origin.setDetailId(900L);
        origin.setWordId(101L);
        origin.setContentVersion(3);
        origin.setStateCode("ACTIVE");
        return origin;
    }

    private static JapaneseWordAiDetailComposer.NewVersion compose(int maxKeptOrderNo) {
        return JapaneseWordAiDetailComposer.compose(detailJson(), originVersion(),
                77L, "qwen", "qwen3.7-plus", maxKeptOrderNo);
    }

    @Test
    @DisplayName("版のヘッダは語レベルの内容と小構造（JSONB）を持つ。AI 生成の情報も入る")
    void composesVersionHeader() {
        JapaneseWordAiDetailComposer.NewVersion version = compose(0);
        JpnWordDetailEntity header = version.header();

        assertThat(header.getWordId()).isEqualTo(101L);
        assertThat(header.getStateCode()).isEqualTo("ACTIVE");
        assertThat(header.getGenerationId()).isEqualTo(77L);
        assertThat(header.getOriginDetailId()).isEqualTo(900L);
        assertThat(header.getAiProvider()).isEqualTo("qwen");
        assertThat(header.getAiModel()).isEqualTo("qwen3.7-plus");
        assertThat(header.getManualCorrected()).isFalse();
        // 語レベルの内容
        assertThat(header.getCoreMeaning()).isEqualTo("爱；喜爱");
        assertThat(header.getDescriptionJa()).isEqualTo("大切に思う気持ち。");
        assertThat(header.getDescriptionZh()).isEqualTo("珍视的感情。");
        assertThat(header.getPartOfSpeech()).isEqualTo("名詞");
        assertThat(header.getJlptLevel()).isEqualTo("N3");
        assertThat(header.getConjugation()).isEqualTo("なし");
        assertThat(header.getTransitivity()).isEqualTo("NONE");
        assertThat(header.getImportance()).isEqualTo(5);
        assertThat(header.getMemoryHint()).isEqualTo("「心」が真ん中にある字。");
        assertThat(header.getMemoryHintBasis()).isEqualTo("漢字の形");
        // 小構造は JSONB の文字列として持つ
        assertThat(read(header.getPronunciationJson()).path("reading").asText()).isEqualTo("あい");
        assertThat(read(header.getConjugationsJson())).hasSize(1);
        assertThat(read(header.getTransitivityPairJson()).path("transitive").asText()).isEqualTo("愛する");
        assertThat(read(header.getStructuredJson()).path("detail").path("jlpt").asText()).isEqualTo("N3");
    }

    @Test
    @DisplayName("11 の段落を子テーブルの行へ崩す（1 件ずつ）")
    void composesAllSections() {
        JapaneseWordAiDetailComposer.NewVersion version = compose(0);
        JpnWordDetailChildren.Rows rows = version.children();

        assertThat(rows.senses()).hasSize(1);
        assertThat(rows.senses().get(0).getSenseNumber()).isEqualTo(1);
        assertThat(rows.senses().get(0).getJapanese()).isEqualTo("大切に思う気持ち。");
        assertThat(rows.senses().get(0).getContext()).isEqualTo("家族・友人");
        assertThat(rows.senses().get(0).getSourceCode()).isEqualTo("BATCH");
        assertThat(rows.senses().get(0).getManualCorrected()).isFalse();

        assertThat(rows.examples()).hasSize(2);
        assertThat(rows.examples().get(0).getReading()).isEqualTo("おやのあいはむじょうけんだ。");
        assertThat(rows.examples().get(0).getSenseNumber()).isEqualTo(1);
        assertThat(rows.examples().get(0).getLevel()).isEqualTo("BASIC");
        assertThat(rows.examples().get(1).getLevel()).isEqualTo("APPLIED");

        assertThat(rows.patterns()).hasSize(1);
        assertThat(rows.patterns().get(0).getPattern()).isEqualTo("**を**愛する");
        assertThat(rows.patterns().get(0).getExampleChinese()).isEqualTo("热爱音乐。");

        assertThat(rows.dialogs()).hasSize(1);
        assertThat(rows.dialogs().get(0).getScene()).isEqualTo("職員室で先生に相談する");
        assertThat(rows.dialogs().get(0).getLines()).hasSize(1);
        assertThat(rows.dialogs().get(0).getLines().get(0).getSpeaker()).isEqualTo("先生");

        assertThat(rows.synonyms()).hasSize(1);
        assertThat(rows.synonyms().get(0).getHeading()).isEqualTo("恋");
        assertThat(rows.cautions()).hasSize(1);
        assertThat(rows.cautions().get(0).getKind()).isEqualTo("MEANING");
        assertThat(rows.cautions().get(0).getWrong()).isEqualTo("文化を恋する。");
        assertThat(rows.collocations()).hasSize(2);
        assertThat(rows.collocations().get(0).getUsage()).isEqualTo("手紙で");
        assertThat(rows.relatedWords()).hasSize(1);
        assertThat(rows.relatedWords().get(0).getRelation()).isEqualTo("類義語");
        assertThat(rows.usageNotes()).hasSize(1);
        assertThat(rows.usageNotes().get(0).getRegister()).isEqualTo("どちらも");
        assertThat(rows.usageNotes().get(0).getSenseNumber()).isEqualTo(1);
        assertThat(rows.practices()).hasSize(1);
        assertThat(rows.practices().get(0).getKind()).isEqualTo("PARTICLE");
        assertThat(rows.practices().get(0).getChoicesJson()).isEqualTo("[\"に\",\"を\"]");
        assertThat(rows.practices().get(0).getFreeWriting()).isFalse();
    }

    @Test
    @DisplayName("AI の新しい行は、複製した行の後ろに付ける（表示順は今の版の最大 + 1 から）")
    void appendsNewRowsAfterKeptRows() {
        // 今の版に 5 行ある（人が直した行を含む）。AI の行は 6 から
        JapaneseWordAiDetailComposer.NewVersion version = compose(5);

        assertThat(version.children().senses().get(0).getOrderNo()).isEqualTo(6);
        assertThat(version.children().examples().get(0).getOrderNo()).isEqualTo(6);
        assertThat(version.children().examples().get(1).getOrderNo()).isEqualTo(7);
        assertThat(version.children().dialogs().get(0).getOrderNo()).isEqualTo(6);
        assertThat(version.children().collocations().get(0).getOrderNo()).isEqualTo(6);
        assertThat(version.children().collocations().get(1).getOrderNo()).isEqualTo(7);
        // 会話の発言も同じ規則（1 から振り直さない）
        assertThat(version.children().dialogs().get(0).getLines().get(0).getOrderNo()).isEqualTo(6);
    }

    @Test
    @DisplayName("人が直した印は AI の行には付けない（AI が作った行は BATCH・手修正フラグ false）")
    void marksAiRowsAsBatch() {
        JapaneseWordAiDetailComposer.NewVersion version = compose(0);

        assertThat(version.children().senses()).allSatisfy(row -> {
            assertThat(row.getSourceCode()).isEqualTo("BATCH");
            assertThat(row.getManualCorrected()).isFalse();
        });
        assertThat(version.children().examples()).allSatisfy(row -> {
            assertThat(row.getSourceCode()).isEqualTo("BATCH");
            assertThat(row.getManualCorrected()).isFalse();
        });
        assertThat(version.children().practices()).allSatisfy(row -> {
            assertThat(row.getSourceCode()).isEqualTo("BATCH");
            assertThat(row.getManualCorrected()).isFalse();
        });
    }

    @Test
    @DisplayName("人が入れた行を複製した版は、ヘッダの 手修正フラグ も true（この版に人の編集が含まれる）")
    void marksHeaderManualWhenManualRowsAreKept() {
        // 複製した人が入れた行が無ければ false のまま（AI だけの版）
        assertThat(compose(0).header().getManualCorrected()).isFalse();
        // 人が入れた行（表示順の最大が 5）を複製した版には人の編集が含まれる
        assertThat(compose(5).header().getManualCorrected()).isTrue();
    }

    @Test
    @DisplayName("詳細の JSON が壊れていても落ちない（空の版を作る）")
    void survivesBrokenJson() {
        JapaneseWordAiDetailComposer.NewVersion version = JapaneseWordAiDetailComposer.compose(
                "これは JSON ではありません", originVersion(), 77L, "qwen", "qwen3.7-plus", 0);

        assertThat(version.header().getWordId()).isEqualTo(101L);
        assertThat(version.children().senses()).isEmpty();
        assertThat(version.children().examples()).isEmpty();
    }

    @Test
    @DisplayName("読みを持たずに登録された語のために、AI の読みを組み立て結果から取り出せる")
    void exposesPronunciationReading() {
        JapaneseWordAiDetailComposer.NewVersion version = compose(0);

        assertThat(JapaneseWordAiDetailComposer.pronunciationReading(version.header().getPronunciationJson()))
                .isEqualTo("あい");
        assertThat(JapaneseWordAiDetailComposer.pronunciationReading("{}")).isNull();
        assertThat(JapaneseWordAiDetailComposer.pronunciationReading(null)).isNull();
    }

    @Test
    @DisplayName("AI の応答が DTO のキー名でなくても、詳細として読めなければ空の版にする")
    void keepsHeaderWhenDetailIsMissing() {
        JapaneseWordAiDetailComposer.NewVersion version = JapaneseWordAiDetailComposer.compose(
                "{\"detail\":null}", originVersion(), 1L, "qwen", "m", 0);

        assertThat(version.header().getCoreMeaning()).isNull();
        assertThat(version.children().senses()).isEmpty();
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    /** DTO のキー名が変わっていないことの目印（コンパイル時に気づけるように）。 */
    @Test
    @DisplayName("AI の DTO は詳細のキーを持ち続ける")
    void dtoStillHasDetailKeys() {
        JapaneseWordAiDtos.Detail detail = new JapaneseWordAiDtos.Detail();
        detail.setSenses(List.of());
        assertThat(detail.getSenses()).isEmpty();
    }
}

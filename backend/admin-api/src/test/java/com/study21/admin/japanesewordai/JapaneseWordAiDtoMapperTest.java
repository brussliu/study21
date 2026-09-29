package com.study21.admin.japanesewordai;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI の応答（DTO のキー名で返る JSON）→ DTO → DB の形へ移す純関数。
 *
 * <p>確かめる接縫は 2 つだけ（AI も DB も使わない純関数）:</p>
 * <ol>
 *   <li>{@link JapaneseWordAiDtoMapper#parseDetail(String)} …
 *       {@code {"detail": {...}}} → {@code JPN_単語詳細情報.詳細JSON}</li>
 *   <li>{@link JapaneseWordAiDtoMapper#parseProblems(String, String, String, String)} …
 *       {@code problems} / {@code problem} → 問題の行と選択肢の行</li>
 * </ol>
 *
 * <p>プロンプトは DTO（{@code JapaneseWordAiDtos}）から生成した JSON Schema を渡すので、
 * AI は <b>DTO のキー名（英語 camelCase）</b> で返す。期待値は「文字列に日本語が含まれるか」では
 * なく、<b>詳細JSON を Jackson で読み直してキーと値を突き合わせて</b>確かめる
 * （キー名の取り違えを contains では見逃すため）。</p>
 *
 * <p>正解の規則は 2.0 の実データ（{@code JPN_単語問題情報} 1,700 行 / 選択肢 6,800 行）から:
 * C1 の正解は<b>入力の読み</b>、C2 の正解は<b>入力の表記</b>（AI が書いた値は使わない）。
 * <b>正解の行の {@code wrongType} は null</b>。</p>
 *
 * <p><b>選択肢は 4 択ではなくプール</b>（切片6・設計「2. 选项池」）: 正解 1 件 ＋ 誤答 4〜6 件
 * （合計 5〜7 件）・値は一意・正解はちょうど 1 件。足りない・多い・誤答が 3 件以下・正解が
 * 1 件でない応答は<b>整題不合格</b>（{@code INVALID_CHOICES}）にして 1 行も書かない。
 * テストを作るときに、このプールから「正解 1 ＋ 誤答 3」を選んで 4 択にする。</p>
 */
class JapaneseWordAiDtoMapperTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 詳細JSON を読み直すための読み込み（文字列 contains で済ませないため）。 */
    private static JsonNode readTree(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    /** DTO のキー名で返る詳細の応答（語義 2・例文 2・コロケーション 2 で下限を満たす）。 */
    private static final String DETAIL_RESPONSE = """
            {
              "detail": {
                "coreMeaning": "爱；喜爱",
                "descriptionJa": "人や物を大切に思う気持ち。",
                "descriptionZh": "对人或物怀有珍视的感情。",
                "partOfSpeech": "名詞",
                "jlpt": "N3",
                "conjugation": "なし",
                "transitivity": "NONE",
                "importance": 5,
                "senses": [
                  {"number": 1, "japanese": "大切に思う気持ち。", "chinese": "爱；喜爱",
                   "context": "家族・友人", "style": "NEUTRAL",
                   "noteJapanese": "対象が広い。", "noteChinese": "对象很广。"},
                  {"number": 2, "japanese": "男女間の恋愛感情。", "chinese": "爱情",
                   "context": "恋愛", "style": "NEUTRAL", "noteJapanese": "", "noteChinese": ""}
                ],
                "examples": [
                  {"japanese": "親の愛は無条件だ。", "reading": "おやのあいはむじょうけんだ。",
                   "chinese": "父母的爱是无条件的。", "senseNumber": 1, "source": "家族", "level": "BASIC"},
                  {"japanese": "彼は植物を愛している。", "reading": "かれはしょくぶつをあいしている。",
                   "chinese": "他热爱植物。", "senseNumber": 2, "source": "", "level": "APPLIED"}
                ],
                "patterns": [
                  {"pattern": "**を**愛する", "reading": "をあいする", "chinese": "爱…",
                   "example": "音楽を愛する。", "exampleChinese": "热爱音乐。"}
                ],
                "dialogs": [
                  {"scene": "職員室で先生に相談する",
                   "lines": [{"speaker": "先生", "japanese": "家族を愛していますか。",
                              "chinese": "你爱家人吗。"}]}
                ],
                "synonyms": [
                  {"heading": "恋", "reading": "こい", "chinese": "恋爱", "shared": "大切に思う",
                   "difference": "「恋」は恋愛感情。", "scene": "恋愛の場面"}
                ],
                "cautions": [
                  {"kind": "MEANING", "title": "「恋」との違い", "wrong": "文化を恋する。",
                   "correct": "文化を愛する。", "reason": "「恋」は恋愛に限る。"}
                ],
                "conjugations": [
                  {"form": "て形", "value": "愛して", "example": "家族を愛している。"}
                ],
                "transitivityPair": {"intransitive": "愛される", "transitive": "愛する",
                  "particleNote": "～を／～に", "intransitiveExample": "彼は愛されている。",
                  "transitiveExample": "彼は家族を愛している。"},
                "pronunciation": {"reading": "あい", "accentType": 1, "accentNotation": "あꜜい",
                  "hint": "「あ」を高く。", "hasAudioSample": false},
                "collocations": [
                  {"expression": "愛を込める", "reading": "あいをこめる", "chinese": "倾注爱意",
                   "usage": "手紙で", "exampleJapanese": "心を込めて愛を伝える。",
                   "exampleChinese": "用心传达爱意。"},
                  {"expression": "愛が深い", "reading": "あいがふかい", "chinese": "爱意深厚",
                   "usage": "家族の話で", "exampleJapanese": "母の愛が深い。",
                   "exampleChinese": "母爱深厚。"}
                ],
                "relatedWords": [
                  {"relation": "類義語", "heading": "恋", "reading": "こい", "chinese": "恋爱"}
                ],
                "usageNotes": [
                  {"register": "どちらも", "politeness": "普通", "audience": "家族に",
                   "note": "書き言葉でも使う。", "senseNumber": 1}
                ],
                "memoryHint": {"hint": "「心」が真ん中にある字。", "basis": "漢字の形"},
                "practices": [
                  {"kind": "PARTICLE", "question": "音楽（ ）愛する。", "questionChinese": "热爱音乐。",
                   "choices": ["に", "を"], "answer": "を", "explanation": "対象は「を」。",
                   "freeWriting": false}
                ]
              }
            }
            """;

    /* ==================================================== 詳細（batC41） */

    @Test
    @DisplayName("詳細の正常系: DTO を 詳細JSON のキーへ写す（キーと値は Jackson で読み直して確かめる）")
    void parsesDetail() {
        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail(DETAIL_RESPONSE);

        assertThat(result.isSuccess()).isTrue();
        JsonNode json = readTree(result.detailJson());

        // トップレベル（学習画面が読む名前。jlpt ではなく jlptLevel）
        assertThat(json.path("coreMeaning").asText()).isEqualTo("爱；喜爱");
        assertThat(json.path("descriptionJa").asText()).isEqualTo("人や物を大切に思う気持ち。");
        assertThat(json.path("descriptionZh").asText()).isEqualTo("对人或物怀有珍视的感情。");
        assertThat(json.path("partOfSpeech").asText()).isEqualTo("名詞");
        assertThat(json.path("jlptLevel").asText()).isEqualTo("N3");
        assertThat(json.path("conjugation").asText()).isEqualTo("なし");
        assertThat(json.path("transitivity").asText()).isEqualTo("NONE");
        assertThat(json.path("importance").asInt()).isEqualTo(5);
        // AI の生成直後は「手で直していない」
        assertThat(json.path("manuallyCorrected").asBoolean()).isFalse();
        assertThat(json.has("manualCorrected")).isFalse();

        // 配列の中も 1 件ずつ写す
        assertThat(json.path("senses")).hasSize(2);
        assertThat(json.path("senses").path(0).path("number").asInt()).isEqualTo(1);
        assertThat(json.path("senses").path(0).path("noteJapanese").asText()).isEqualTo("対象が広い。");
        assertThat(json.path("senses").path(1).path("chinese").asText()).isEqualTo("爱情");

        assertThat(json.path("examples")).hasSize(2);
        assertThat(json.path("examples").path(0).path("reading").asText())
                .isEqualTo("おやのあいはむじょうけんだ。");
        assertThat(json.path("examples").path(0).path("senseNumber").asInt()).isEqualTo(1);
        assertThat(json.path("examples").path(0).path("level").asText()).isEqualTo("BASIC");

        assertThat(json.path("patterns").path(0).path("pattern").asText()).isEqualTo("**を**愛する");
        assertThat(json.path("patterns").path(0).path("exampleChinese").asText()).isEqualTo("热爱音乐。");

        assertThat(json.path("dialogs").path(0).path("scene").asText()).isEqualTo("職員室で先生に相談する");
        assertThat(json.path("dialogs").path(0).path("lines").path(1).isMissingNode()).isTrue();
        assertThat(json.path("dialogs").path(0).path("lines").path(0).path("speaker").asText())
                .isEqualTo("先生");

        assertThat(json.path("synonyms").path(0).path("difference").asText()).isEqualTo("「恋」は恋愛感情。");
        assertThat(json.path("cautions").path(0).path("kind").asText()).isEqualTo("MEANING");
        assertThat(json.path("cautions").path(0).path("wrong").asText()).isEqualTo("文化を恋する。");
        assertThat(json.path("conjugations").path(0).path("value").asText()).isEqualTo("愛して");

        assertThat(json.path("transitivityPair").path("intransitive").asText()).isEqualTo("愛される");
        assertThat(json.path("transitivityPair").path("particleNote").asText()).isEqualTo("～を／～に");

        // pronunciation（1 件）と pronunciations（2.1 の既存画面が読む配列）の両方を入れる
        assertThat(json.path("pronunciation").path("reading").asText()).isEqualTo("あい");
        assertThat(json.path("pronunciation").path("accentType").asInt()).isEqualTo(1);
        assertThat(json.path("pronunciation").path("accentNotation").asText()).isEqualTo("あꜜい");
        assertThat(json.path("pronunciation").path("hint").asText()).isEqualTo("「あ」を高く。");
        assertThat(json.path("pronunciation").path("hasAudioSample").asBoolean()).isFalse();
        assertThat(json.path("pronunciations")).hasSize(1);
        assertThat(json.path("pronunciations").path(0).path("reading").asText()).isEqualTo("あい");
        assertThat(json.path("pronunciations").path(0).path("accentNotation").asText()).isEqualTo("あꜜい");
        // DTO に無い盛り数は読みの文字数で埋める（2 文字）
        assertThat(json.path("pronunciations").path(0).path("moraCount").asInt()).isEqualTo(2);
        // 音声は別の仕組みが持つ。ここは常に null（値を捏造しない）
        assertThat(json.path("pronunciations").path(0).path("audioUrl").isNull()).isTrue();
        assertThat(json.path("pronunciations").path(0).path("audioProvider").isNull()).isTrue();

        assertThat(json.path("collocations")).hasSize(2);
        assertThat(json.path("collocations").path(0).path("usage").asText()).isEqualTo("手紙で");
        assertThat(json.path("relatedWords").path(0).path("relation").asText()).isEqualTo("類義語");
        assertThat(json.path("usageNotes").path(0).path("register").asText()).isEqualTo("どちらも");
        assertThat(json.path("usageNotes").path(0).path("senseNumber").asInt()).isEqualTo(1);
        assertThat(json.path("memoryHint").path("basis").asText()).isEqualTo("漢字の形");
        assertThat(json.path("practices").path(0).path("kind").asText()).isEqualTo("PARTICLE");
        assertThat(json.path("practices").path(0).path("choices").path(1).asText()).isEqualTo("を");
        assertThat(json.path("practices").path(0).path("freeWriting").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("詳細: AI の生の応答は structured にそのまま残す（あとから突き合わせられるように）")
    void keepsRawDetailResponse() {
        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail(DETAIL_RESPONSE);

        JsonNode structured = readTree(result.detailJson()).path("structured");

        // ルートの JSON をそのまま（DTO のキー名のまま）
        assertThat(structured.path("detail").path("jlpt").asText()).isEqualTo("N3");
        assertThat(structured.path("detail").path("senses")).hasSize(2);
    }

    @Test
    @DisplayName("詳細: DTO に無い項目は null / 空配列で埋める（発音が無ければ pronunciation は null）")
    void fillsMissingDetailKeys() {
        // 発音・自他・記憶のヒント・活用は DTO に無い（null）／配列は空
        String body = """
                {"detail": {
                  "senses": [{"number": 1, "japanese": "意味", "chinese": "意思"}],
                  "examples": [{"japanese": "例1"}, {"japanese": "例2"}],
                  "collocations": [{"expression": "言い回し1"}, {"expression": "言い回し2"}]
                }}
                """;

        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail(body);

        assertThat(result.isSuccess()).isTrue();
        JsonNode json = readTree(result.detailJson());
        assertThat(json.path("pronunciation").isNull()).isTrue();
        assertThat(json.path("pronunciations")).isEmpty();
        assertThat(json.path("transitivityPair").isNull()).isTrue();
        assertThat(json.path("memoryHint").isNull()).isTrue();
        assertThat(json.path("conjugations")).isEmpty();
        // 語義番号が無ければ 1 から振る（学習画面が番号で結び付けるため）
        assertThat(json.path("senses").path(0).path("number").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("詳細: 読みを持たずに登録された語のために、AI の読み（pronunciation.reading）を取り出す")
    void readsPronunciationReading() {
        String body = """
                {"detail": {
                  "senses": [{"number": 1, "japanese": "意味", "chinese": "意思"}],
                  "examples": [{"japanese": "例1"}, {"japanese": "例2"}],
                  "collocations": [{"expression": "言い回し1"}, {"expression": "言い回し2"}],
                  "pronunciation": {"reading": "  あい  ", "hint": "低く始める。"}
                }}
                """;

        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail(body);

        assertThat(result.isSuccess()).isTrue();
        // 前後の空白は落とす（そのまま 読みキー になる）
        assertThat(result.reading()).isEqualTo("あい");
    }

    @Test
    @DisplayName("詳細: AI が読みを書かなければ null（空で上書きしない）")
    void readsNoPronunciationReading() {
        String body = """
                {"detail": {
                  "senses": [{"number": 1, "japanese": "意味", "chinese": "意思"}],
                  "examples": [{"japanese": "例1"}, {"japanese": "例2"}],
                  "collocations": [{"expression": "言い回し1"}, {"expression": "言い回し2"}],
                  "pronunciation": {"reading": "   ", "hint": "低く始める。"}
                }}
                """;

        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail(body);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.reading()).isNull();
    }

    @Test
    @DisplayName("詳細: 単語のレベルとして使うため、AI の jlpt を N1〜N5 だけ取り出す")
    void readsJlptLevel() {
        String body = """
                {"detail": {
                  "senses": [{"number": 1, "japanese": "意味", "chinese": "意思"}],
                  "examples": [{"japanese": "例1"}, {"japanese": "例2"}],
                  "collocations": [{"expression": "言い回し1"}, {"expression": "言い回し2"}],
                  "jlpt": "  n3 "
                }}
                """;

        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail(body);

        assertThat(result.isSuccess()).isTrue();
        // 表記ゆれは直す（そのまま 単語情報.JLPTレベル に入る）
        assertThat(result.jlpt()).isEqualTo("N3");
    }

    @Test
    @DisplayName("詳細: N1〜N5 でない値は使わない（CHECK に当てない）")
    void ignoresInvalidJlptLevel() {
        String body = """
                {"detail": {
                  "senses": [{"number": 1, "japanese": "意味", "chinese": "意思"}],
                  "examples": [{"japanese": "例1"}, {"japanese": "例2"}],
                  "collocations": [{"expression": "言い回し1"}, {"expression": "言い回し2"}],
                  "jlpt": "3 級"
                }}
                """;

        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail(body);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.jlpt()).isNull();
    }

    @Test
    @DisplayName("詳細: 語義1件以上・例文2件以上・コロケーション2件以上を満たさなければ失敗にする")
    void rejectsThinDetail() {
        // 例文が 1 件しかない（プロンプトは 2 件以上を求めている）
        JsonNode root = readTree(DETAIL_RESPONSE);
        ((ArrayNode) root.path("detail").path("examples")).remove(1);
        JapaneseWordAiDtoMapper.DetailResult result = JapaneseWordAiDtoMapper.parseDetail(root.toString());

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("THIN_CONTENT");
        assertThat(result.errorMessage()).contains("例文");

        // 語義が 0 件
        String noSense = DETAIL_RESPONSE.replace("\"senses\": [", "\"senses0\": [");
        assertThat(JapaneseWordAiDtoMapper.parseDetail(noSense).errorCode()).isEqualTo("THIN_CONTENT");
    }

    @Test
    @DisplayName("詳細: 空の応答・detail が無い応答・JSON でない応答は失敗にする（例外は投げない）")
    void rejectsBrokenDetail() {
        assertThat(JapaneseWordAiDtoMapper.parseDetail("").errorCode()).isEqualTo("EMPTY_RESPONSE");
        assertThat(JapaneseWordAiDtoMapper.parseDetail("  ").errorCode()).isEqualTo("EMPTY_RESPONSE");
        // detail が無い（DTO のルートキーは detail）
        assertThat(JapaneseWordAiDtoMapper.parseDetail("{}").errorCode()).isEqualTo("EMPTY_RESPONSE");
        assertThat(JapaneseWordAiDtoMapper.parseDetail("これは JSON ではありません").errorCode())
                .isEqualTo("INVALID_JSON");
    }

    /* ==================================================== 問題（batC42〜batC44） */

    /**
     * C（C1_READING / C2_KANJI の 2 件）。DTO のキー名で返る。
     *
     * <p>わざと <b>C2 を先</b> に置き、AI の {@code correctValue} も入力語と違う値にしてある
     * （正解は入力語から決まるので、どちらも出力に影響しない）。</p>
     *
     * <p>選択肢はプール（合計 6 件 = 正解 1 ＋ 誤答 5）。誤答は<b>正解の後ろ</b>に置いてあるので、
     * 正解の位置は 4 択だったころと同じ（C1 は 4 件目、C2 は 3 件目）。</p>
     */
    private static final String C_RESPONSE = """
            {
              "problems": [
                {"problemType": "C2_KANJI", "questionJapanese": "読みを聞いて漢字を選んでください",
                 "questionChinese": "请根据读音选择正确的汉字。",
                 "targetHeading": "まちがい", "targetReading": "まちがい", "audioText": "あい",
                 "correctValue": "AIが書いた値",
                 "options": [{"value": "哀", "reading": "あい", "wrongType": "KANJI_SIMILAR"},
                             {"value": "相", "reading": "あい", "wrongType": "KANJI_SIMILAR"},
                             {"value": "愛", "reading": "あい", "wrongType": "KANJI_CORRECT"},
                             {"value": "藍", "reading": "あい", "wrongType": "KANJI_SIMILAR"},
                             {"value": "挨", "reading": "あい", "wrongType": "KANJI_SIMILAR"},
                             {"value": "曖", "reading": "あい", "wrongType": "KANJI_SIMILAR"}]},
                {"problemType": "C1_READING", "questionJapanese": "漢字の読みを選んでください",
                 "questionChinese": "请选择汉字的读音。",
                 "targetHeading": "愛", "targetReading": "あい",
                 "correctValue": "AIが書いた値",
                 "options": [{"value": "えい", "reading": "えい", "wrongType": "READING_SIMILAR"},
                             {"value": "あう", "reading": "あう", "wrongType": "READING_SIMILAR"},
                             {"value": "い", "reading": "い", "wrongType": "READING_SIMILAR"},
                             {"value": "あい", "reading": "あい", "wrongType": "READING_CORRECT"},
                             {"value": "あえ", "reading": "あえ", "wrongType": "READING_SIMILAR"},
                             {"value": "あお", "reading": "あお", "wrongType": "READING_SIMILAR"}]}
              ]
            }
            """;

    /** D（文脈に合う意味の四択。プールは正解 1 ＋ 誤答 5）。 */
    private static final String D_RESPONSE = """
            {
              "problem": {
                "problemType": "D_CONTEXT_MEANING",
                "questionJapanese": "下線部の「愛」の意味として最も適切なものを選びなさい。",
                "questionChinese": "请选择下划线部分「愛」最合适的意思。",
                "targetHeading": "愛", "targetReading": "あい",
                "sentenceJapanese": "彼は長年育てた植物に対する愛を語った。",
                "sentenceReading": "かれはながねんそだてたしょくぶつにたいするあいをかたった。",
                "correctValue": "喜爱", "correctNote": "物や趣味への深い感情。",
                "explanationJapanese": "「植物に対する」とあるので恋愛感情ではない。",
                "explanationChinese": "结合上下文判断对象是物品，因此选“喜爱”。",
                "difficulty": "NORMAL",
                "options": [{"value": "爱情", "wrongType": "MEANING_SIMILAR", "explanationChinese": "男女間の感情。"},
                            {"value": "恋爱", "wrongType": "MEANING_SIMILAR", "explanationChinese": "男女間の感情。"},
                            {"value": "喜爱", "wrongType": "MEANING_CORRECT", "explanationChinese": "正解。"},
                            {"value": "爱好", "wrongType": "CONTEXT_MISMATCH", "explanationChinese": "趣味のこと。"},
                            {"value": "爱慕", "wrongType": "MEANING_SIMILAR", "explanationChinese": "慕わしく思うこと。"},
                            {"value": "关爱", "wrongType": "CONTEXT_MISMATCH", "explanationChinese": "気遣うこと。"}]
              }
            }
            """;

    /** E（同音漢字の使い分け四択。プールは正解 1 ＋ 誤答 5）。 */
    private static final String E_RESPONSE = """
            {
              "problem": {
                "problemType": "E_KANJI_USAGE",
                "questionJapanese": "（ ）に入る最も適切な漢字表記を選んでください。",
                "questionChinese": "请选择括号中最合适的汉字。",
                "targetHeading": "愛", "targetReading": "あい",
                "sentenceJapanese": "親の（ ）は、子供を無条件で受け入れるものである。",
                "sentenceReading": "おやのあいは、こどもをむじょうけんどうけいれいる。",
                "audioText": "あい", "correctValue": "愛", "correctNote": "家族への深い感情。",
                "explanationJapanese": "文脈は家族への深い感情なので「愛」。",
                "explanationChinese": "根据语境应使用“爱”。",
                "difficulty": "EASY",
                "options": [{"value": "哀", "reading": "あい", "wrongType": "HOMOPHONE", "explanationChinese": "悲しみ。"},
                            {"value": "相", "reading": "あい", "wrongType": "HOMOPHONE", "explanationChinese": "互い。"},
                            {"value": "藍", "reading": "あい", "wrongType": "HOMOPHONE", "explanationChinese": "染料。"},
                            {"value": "愛", "reading": "あい", "wrongType": "HOMOPHONE_CORRECT", "explanationChinese": "正解。"},
                            {"value": "挨", "reading": "あい", "wrongType": "HOMOPHONE", "explanationChinese": "押すこと。"},
                            {"value": "曖", "reading": "あい", "wrongType": "HOMOPHONE", "explanationChinese": "暗いこと。"}]
              }
            }
            """;

    @Test
    @DisplayName("問題C: problems の 2 件を C1・C2 の順に分け、問題番号は種別ごとに 1 から")
    void parsesCProblems() {
        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(C_RESPONSE, "C", "愛", "あい");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.problems()).hasSize(2);

        JapaneseWordAiDtoMapper.ParsedProblem c1 = result.problems().get(0);
        assertThat(c1.questionType()).isEqualTo("C1_READING");
        assertThat(c1.questionNo()).isEqualTo(1);
        assertThat(c1.questionJa()).isEqualTo("漢字の読みを選んでください");
        assertThat(c1.questionZh()).isEqualTo("请选择汉字的读音。");
        assertThat(c1.targetHeading()).isEqualTo("愛");
        assertThat(c1.targetReading()).isEqualTo("あい");
        assertThat(c1.choices()).hasSize(6);
        // structuredJson には、その問題ぶんの DTO が入る（人が後から追えるように）
        assertThat(readTree(c1.structuredJson()).path("problemType").asText()).isEqualTo("C1_READING");
        assertThat(readTree(c1.structuredJson()).path("options")).hasSize(6);

        JapaneseWordAiDtoMapper.ParsedProblem c2 = result.problems().get(1);
        assertThat(c2.questionType()).isEqualTo("C2_KANJI");
        assertThat(c2.questionNo()).isEqualTo(1);
        assertThat(c2.audioText()).isEqualTo("あい");
        assertThat(c1.audioText()).isNull();
        // 表記と読みは入力の語で上書きする（AI の targetHeading は使わない）
        assertThat(c2.targetHeading()).isEqualTo("愛");
        assertThat(c2.targetReading()).isEqualTo("あい");
    }

    @Test
    @DisplayName("問題C: 正解は入力の語（C1＝入力の読み・C2＝入力の表記）。AI の correctValue は無視する")
    void usesInputWordAsCorrectValue() {
        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(C_RESPONSE, "C", "愛", "あい");

        JapaneseWordAiDtoMapper.ParsedProblem c1 = result.problems().get(0);
        JapaneseWordAiDtoMapper.ParsedProblem c2 = result.problems().get(1);

        // AI は「AIが書いた値」と書いているが、入力の読み・表記が正
        assertThat(c1.correctValue()).isEqualTo("あい");
        assertThat(c2.correctValue()).isEqualTo("愛");
        assertThat(c1.choices()).filteredOn(JapaneseWordAiDtoMapper.ParsedChoice::correct).hasSize(1);
        assertThat(c2.choices()).filteredOn(JapaneseWordAiDtoMapper.ParsedChoice::correct).hasSize(1);
        assertThat(c1.choices().get(3).correct()).isTrue();
        assertThat(c2.choices().get(2).correct()).isTrue();
    }

    @Test
    @DisplayName("問題C: 入力の読みが空なら AI の読みを正解にする（C だけ先に走らせても失敗させない）")
    void usesAiReadingWhenInputReadingIsBlank() {
        // 読みを持たずに登録した語（入力の reading は空）。AI は読みを書いてくる
        String body = """
                {"problems": [
                  {"problemType": "C1_READING", "questionJapanese": "読みを選んでください",
                   "targetHeading": "愛", "targetReading": "あい",
                   "options": [{"value": "えい", "reading": "えい", "wrongType": "READING_SIMILAR"},
                               {"value": "あう", "reading": "あう", "wrongType": "READING_SIMILAR"},
                               {"value": "い", "reading": "い", "wrongType": "READING_SIMILAR"},
                               {"value": "あい", "reading": "あい", "wrongType": "READING_CORRECT"},
                               {"value": "あえ", "reading": "あえ", "wrongType": "READING_SIMILAR"},
                               {"value": "あお", "reading": "あお", "wrongType": "READING_SIMILAR"}]},
                  {"problemType": "C2_KANJI", "questionJapanese": "漢字を選んでください",
                   "targetHeading": "愛", "targetReading": "あい", "audioText": "あい",
                   "options": [{"value": "哀", "reading": "あい", "wrongType": "KANJI_SIMILAR"},
                               {"value": "相", "reading": "あい", "wrongType": "KANJI_SIMILAR"},
                               {"value": "藍", "reading": "あい", "wrongType": "KANJI_SIMILAR"},
                               {"value": "愛", "reading": "あい", "wrongType": "KANJI_CORRECT"},
                               {"value": "挨", "reading": "あい", "wrongType": "KANJI_SIMILAR"},
                               {"value": "曖", "reading": "あい", "wrongType": "KANJI_SIMILAR"}]}
                ]}
                """;

        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(body, "C", "愛", "");

        assertThat(result.isSuccess()).isTrue();
        JapaneseWordAiDtoMapper.ParsedProblem c1 = result.problems().get(0);
        JapaneseWordAiDtoMapper.ParsedProblem c2 = result.problems().get(1);
        // 入力に読みが無いので、AI が書いた読みを正解にする（選択肢の印もそれで付く）
        assertThat(c1.correctValue()).isEqualTo("あい");
        assertThat(c1.choices()).filteredOn(JapaneseWordAiDtoMapper.ParsedChoice::correct).hasSize(1);
        assertThat(c1.choices().get(3).correct()).isTrue();
        assertThat(c1.targetReading()).isEqualTo("あい");
        // C2 は読みから表記を問うので、対象読みも同じ読みで埋める
        assertThat(c2.targetReading()).isEqualTo("あい");
    }

    @Test
    @DisplayName("問題: 正解の行だけ wrongType を null にし、誤答には区分と理由を残す")
    void clearsWrongTypeOfCorrectChoice() {
        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(C_RESPONSE, "C", "愛", "あい");

        JapaneseWordAiDtoMapper.ParsedProblem c1 = result.problems().get(0);
        assertThat(c1.choices()).filteredOn(JapaneseWordAiDtoMapper.ParsedChoice::correct)
                .allSatisfy(choice -> assertThat(choice.wrongType()).isNull());
        assertThat(c1.choices().get(0).wrongType()).isEqualTo("READING_SIMILAR");
        // 選択肢の並びは届いた順のまま（画面の 1〜4 と対応させる）
        assertThat(c1.choices().get(0).orderNo()).isEqualTo(1);
        assertThat(c1.choices().get(3).orderNo()).isEqualTo(4);
        assertThat(c1.choices().get(3).reading()).isEqualTo("あい");

        JapaneseWordAiDtoMapper.ParsedProblem d =
                JapaneseWordAiDtoMapper.parseProblems(D_RESPONSE, "D", "愛", "あい").problems().get(0);
        assertThat(d.choices().get(2).correct()).isTrue();
        assertThat(d.choices().get(2).wrongType()).isNull();
        assertThat(d.choices().get(0).wrongType()).isEqualTo("MEANING_SIMILAR");
        assertThat(d.choices().get(0).explanationZh()).isEqualTo("男女間の感情。");
    }

    @Test
    @DisplayName("問題D: 文脈の例文・音声・解説・難易度を問題行へ写す")
    void parsesDProblem() {
        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(D_RESPONSE, "D", "愛", "あい");

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.problems()).hasSize(1);
        JapaneseWordAiDtoMapper.ParsedProblem problem = result.problems().get(0);
        assertThat(problem.questionType()).isEqualTo("D_CONTEXT_MEANING");
        assertThat(problem.questionNo()).isEqualTo(1);
        assertThat(problem.exampleJa()).isEqualTo("彼は長年育てた植物に対する愛を語った。");
        assertThat(problem.exampleReading())
                .isEqualTo("かれはながねんそだてたしょくぶつにたいするあいをかたった。");
        // D は音声で読み上げる文が無ければ例文を使う
        assertThat(problem.audioText()).isEqualTo("彼は長年育てた植物に対する愛を語った。");
        assertThat(problem.correctValue()).isEqualTo("喜爱");
        assertThat(problem.correctNote()).isEqualTo("物や趣味への深い感情。");
        assertThat(problem.explanationJa()).isEqualTo("「植物に対する」とあるので恋愛感情ではない。");
        assertThat(problem.explanationZh()).isEqualTo("结合上下文判断对象是物品，因此选“喜爱”。");
        assertThat(problem.difficulty()).isEqualTo("NORMAL");
    }

    @Test
    @DisplayName("問題E: 空欄入りの例文と読みの音声テキストを持つ問題行を作る")
    void parsesEProblem() {
        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(E_RESPONSE, "E", "愛", "あい");

        assertThat(result.isSuccess()).isTrue();
        JapaneseWordAiDtoMapper.ParsedProblem problem = result.problems().get(0);
        assertThat(problem.questionType()).isEqualTo("E_KANJI_USAGE");
        assertThat(problem.questionNo()).isEqualTo(1);
        assertThat(problem.exampleJa()).isEqualTo("親の（ ）は、子供を無条件で受け入れるものである。");
        assertThat(problem.audioText()).isEqualTo("あい");
        assertThat(problem.correctValue()).isEqualTo("愛");
        assertThat(problem.difficulty()).isEqualTo("EASY");
        assertThat(problem.choices().get(3).correct()).isTrue();
        assertThat(problem.choices().get(3).wrongType()).isNull();
        assertThat(problem.choices().get(1).reading()).isEqualTo("あい");
    }

    @Test
    @DisplayName("問題: 値が重複していれば失敗にする（件数と正解の数はプールの規則で別に見る）")
    void rejectsInvalidChoices() {
        // 正解が 0 件（入力の読み「あいさん」がどの選択肢にも無い）
        assertThat(JapaneseWordAiDtoMapper.parseProblems(C_RESPONSE, "C", "愛", "あいさん").errorCode())
                .isEqualTo("INVALID_CHOICES");

        // 正解が 2 件（同じ値が 2 行あり、どちらも正解になる）
        JsonNode twoCorrect = readTree(E_RESPONSE);
        ((ObjectNode) twoCorrect.path("problem").path("options").path(2)).put("value", "愛");
        assertThat(JapaneseWordAiDtoMapper.parseProblems(twoCorrect.toString(), "E", "愛", "あい").errorCode())
                .isEqualTo("INVALID_CHOICES");

        // 値が重複（正解とは別の値）
        JsonNode duplicated = readTree(E_RESPONSE);
        ((ObjectNode) duplicated.path("problem").path("options").path(1)).put("value", "哀");
        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(duplicated.toString(), "E", "愛", "あい");
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_CHOICES");
        assertThat(result.errorMessage()).contains("重複");
    }

    /* ==================================================== 選択肢プール（切片6） */

    /**
     * E の応答から選択肢を {@code count} 件にして返す（合格する 6 件から削って作る）。
     *
     * <p>削るのは<b>後ろ（誤答）から</b>。正解（4 件目の「愛」）は残るので、
     * 「件数だけが規則を外れている」応答になる。</p>
     */
    private static String eResponseWithChoices(int count) {
        JsonNode root = readTree(E_RESPONSE);
        ArrayNode options = (ArrayNode) root.path("problem").path("options");
        while (options.size() > count) {
            options.remove(options.size() - 1);
        }
        return root.toString();
    }

    @Test
    @DisplayName("選択肢プール: 5〜7 件で正解 1 件なら合格（正解の位置はそのまま・誤答に区分が残る）")
    void acceptsChoicePoolWithinRange() {
        for (int count = 5; count <= 7; count += 1) {
            // 6 件の応答から作るので、7 件のときは誤答を 1 件足す
            String body = count <= 6 ? eResponseWithChoices(count) : addFillerChoice(eResponseWithChoices(6));
            JapaneseWordAiDtoMapper.ProblemResult result =
                    JapaneseWordAiDtoMapper.parseProblems(body, "E", "愛", "あい");

            assertThat(result.isSuccess()).as("選択肢 %d 件は合格するはず", count).isTrue();
            JapaneseWordAiDtoMapper.ParsedProblem problem = result.problems().get(0);
            assertThat(problem.choices()).hasSize(count);
            assertThat(problem.choices()).filteredOn(JapaneseWordAiDtoMapper.ParsedChoice::correct).hasSize(1);
            assertThat(problem.correctValue()).isEqualTo("愛");
            // 並びは届いた順のまま（プールの表示順は AI の順。テスト作成時に選び直す）
            assertThat(problem.choices()).extracting(JapaneseWordAiDtoMapper.ParsedChoice::orderNo)
                    .containsExactly(java.util.stream.IntStream.rangeClosed(1, count).boxed().toArray(Integer[]::new));
        }
    }

    @Test
    @DisplayName("選択肢プール: 4 件・8 件は不合格（合計 5〜7 件の外）")
    void rejectsChoicePoolOutOfRange() {
        JapaneseWordAiDtoMapper.ProblemResult four =
                JapaneseWordAiDtoMapper.parseProblems(eResponseWithChoices(4), "E", "愛", "あい");
        assertThat(four.isSuccess()).isFalse();
        assertThat(four.errorCode()).isEqualTo("INVALID_CHOICES");
        assertThat(four.errorMessage()).contains("5〜7");

        JapaneseWordAiDtoMapper.ProblemResult eight = JapaneseWordAiDtoMapper.parseProblems(
                addFillerChoice(addFillerChoice(eResponseWithChoices(6))), "E", "愛", "あい");
        assertThat(eight.isSuccess()).isFalse();
        assertThat(eight.errorCode()).isEqualTo("INVALID_CHOICES");
        assertThat(eight.errorMessage()).contains("5〜7");
    }

    @Test
    @DisplayName("選択肢プール: 誤答が 4 件未満になる応答は、件数か正解の数で必ず不合格になる")
    void rejectsPoolWithoutEnoughDistractors() {
        // 合計 5〜7 件は「正解 1 ＋ 誤答 4」以上と同じ意味なので、誤答 3 件を作るには
        // 正解を 2 件にするか、件数を 4 件に落とすしかない。どちらも INVALID_CHOICES になる
        // 1) 4 件（正解 1 ＋ 誤答 3）→ 件数で不合格
        JapaneseWordAiDtoMapper.ProblemResult four =
                JapaneseWordAiDtoMapper.parseProblems(eResponseWithChoices(4), "E", "愛", "あい");
        assertThat(four.isSuccess()).isFalse();
        assertThat(four.errorCode()).isEqualTo("INVALID_CHOICES");
        assertThat(four.errorMessage()).contains("5〜7");

        // 2) 7 件のうち誤答 2 件を正解と同じ値にする → 値が重複し、正解も 3 件になる
        JsonNode root = readTree(addFillerChoice(eResponseWithChoices(6)));
        ArrayNode options = (ArrayNode) root.path("problem").path("options");
        ((ObjectNode) options.path(0)).put("value", "愛");
        ((ObjectNode) options.path(1)).put("value", "愛");
        assertThat(options.size()).isEqualTo(7);
        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(root.toString(), "E", "愛", "あい");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_CHOICES");
        assertThat(result.errorMessage()).contains("重複");
    }

    @Test
    @DisplayName("選択肢プール: 正解 2 件（値はすべて異なる）なら不合格")
    void rejectsPoolWithTwoCorrectChoices() {
        // 6 件（正解 1 ＋ 誤答 5）で、誤答 1 件を正解と同じ**意味の別表記**にはできないので、
        // 正解の値をもう 1 件入れて正解 2 件にする（値は重複するので、重複としても落ちる）
        JsonNode root = readTree(eResponseWithChoices(6));
        ((ObjectNode) root.path("problem").path("options").path(0)).put("value", "愛");
        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(root.toString(), "E", "愛", "あい");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_CHOICES");
        assertThat(result.problems()).isNull();
    }

    @Test
    @DisplayName("選択肢プール: 正解 0 件なら不合格（整題を書かない）")
    void rejectsPoolWithoutCorrectChoice() {
        // どの選択肢にも正解の値（「愛」）が無い（6 件あるので件数では落ちない）
        JsonNode root = readTree(eResponseWithChoices(6));
        ArrayNode options = (ArrayNode) root.path("problem").path("options");
        for (int index = 0; index < options.size(); index += 1) {
            ((ObjectNode) options.path(index)).put("value", "仮" + index + "字");
        }
        JapaneseWordAiDtoMapper.ProblemResult result =
                JapaneseWordAiDtoMapper.parseProblems(root.toString(), "E", "愛", "あい");

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.errorCode()).isEqualTo("INVALID_CHOICES");
        assertThat(result.errorMessage()).contains("ちょうど 1 件");
        // 不合格なら 1 行も作らない（呼び側が書き込む問題が無い）
        assertThat(result.problems()).isNull();
    }

    /** 6 件の応答に、値の重複しない誤答を 1 件足す（7 件・8 件を作るため）。 */
    private static String addFillerChoice(String body) {
        JsonNode root = readTree(body);
        ArrayNode options = (ArrayNode) root.path("problem").path("options");
        ObjectNode filler = MAPPER.createObjectNode();
        filler.put("value", "愛" + options.size() + "番");
        filler.put("reading", "あい");
        filler.put("wrongType", "HOMOPHONE");
        filler.put("explanationChinese", "ダミー。");
        options.add(filler);
        return root.toString();
    }

    @Test
    @DisplayName("問題: 取得区分が不正・JSON でない・空・問題が無い応答はそれぞれの理由で失敗にする")
    void rejectsWrongShape() {
        assertThat(JapaneseWordAiDtoMapper.parseProblems(C_RESPONSE, "X", "愛", "あい").errorCode())
                .isEqualTo("UNKNOWN_KIND");
        assertThat(JapaneseWordAiDtoMapper.parseProblems("not json", "D", "愛", "あい").errorCode())
                .isEqualTo("INVALID_JSON");
        assertThat(JapaneseWordAiDtoMapper.parseProblems("", "D", "愛", "あい").errorCode())
                .isEqualTo("EMPTY_RESPONSE");
        // 問題が入っていない（D はルートの problem、C は problems）
        assertThat(JapaneseWordAiDtoMapper.parseProblems("{}", "D", "愛", "あい").errorCode())
                .isEqualTo("MISSING_PROBLEMS");
        assertThat(JapaneseWordAiDtoMapper.parseProblems("{\"problems\":[]}", "C", "愛", "あい").errorCode())
                .isEqualTo("MISSING_PROBLEMS");
        // C は C1_READING と C2_KANJI が 1 件ずつ揃っていなければ受け取らない
        String onlyC1 = C_RESPONSE.replace("\"C2_KANJI\"", "\"C1_READING\"");
        assertThat(JapaneseWordAiDtoMapper.parseProblems(onlyC1, "C", "愛", "あい").errorCode())
                .isEqualTo("MISSING_PROBLEMS");
    }
}

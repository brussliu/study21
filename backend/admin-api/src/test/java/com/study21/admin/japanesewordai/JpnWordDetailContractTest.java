package com.study21.admin.japanesewordai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.japanese.JpnWordDetailAssembler;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 詳細のキーの契約（<b>書きと読みで形が食い違わない</b>こと）。
 *
 * <p>詳細の形は admin-api の {@code JapaneseWordAiDtoMapper.toDetailJson}（AI の DTO →
 * 旧 {@code 詳細JSON} の形）が唯一の基準。この形が 3 か所で使われる:</p>
 * <ol>
 *   <li>AI の応答 → 詳細（{@link JapaneseWordAiDtoMapper#parseDetail(String)}）</li>
 *   <li>詳細 → 版のヘッダ ＋ 11 の子テーブル（{@link JapaneseWordAiDetailComposer}）</li>
 *   <li>版 ＋ 子テーブル → 詳細（{@link JpnWordDetailAssembler}。user-api と admin-api の共通）</li>
 * </ol>
 *
 * <p>ここでは 1 → 2 → 3 と往復させ、<b>ルートのキーと入れ子のキーを 1 つずつ</b>確かめる
 * （キー名の取り違えを contains では見逃すため）。期待値は基準の形をそのまま書き下す
 * （実装から作らない。独立した正解として持つ）。</p>
 */
class JpnWordDetailContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** ルートのキー（この順で出す）。 */
    private static final List<String> ROOT_KEYS = List.of(
            "coreMeaning", "chineseMeaning", "descriptionJa", "descriptionZh", "partOfSpeech",
            "jlptLevel", "conjugation", "transitivity", "importance", "manuallyCorrected",
            "senses", "examples", "patterns", "dialogs", "synonyms", "cautions", "conjugations",
            "transitivityPair", "pronunciation", "pronunciations", "collocations", "relatedWords",
            "usageNotes", "memoryHint", "practices", "structured");

    /** 配列の要素のキー（段落ごと）。 */
    private static final Map<String, List<String>> ITEM_KEYS = Map.of(
            "senses", List.of("number", "japanese", "chinese", "context", "style",
                    "noteJapanese", "noteChinese"),
            "examples", List.of("japanese", "reading", "chinese", "senseNumber", "source", "level"),
            "patterns", List.of("pattern", "reading", "chinese", "example", "exampleChinese"),
            "synonyms", List.of("heading", "reading", "chinese", "shared", "difference", "scene"),
            "cautions", List.of("kind", "title", "wrong", "correct", "reason"),
            "conjugations", List.of("form", "value", "example"),
            "collocations", List.of("expression", "reading", "chinese", "usage",
                    "exampleJapanese", "exampleChinese"),
            "relatedWords", List.of("relation", "heading", "reading", "chinese"),
            "usageNotes", List.of("register", "politeness", "audience", "note", "senseNumber"),
            "practices", List.of("kind", "question", "questionChinese", "choices", "answer",
                    "explanation", "freeWriting"));

    /** 会話（2 層）のキー。 */
    private static final List<String> DIALOG_KEYS = List.of("scene", "lines");

    private static final List<String> DIALOG_LINE_KEYS = List.of("speaker", "japanese", "chinese");

    private static final List<String> PRONUNCIATION_KEYS = List.of("reading", "accentType",
            "accentNotation", "hint", "hasAudioSample");

    private static final List<String> PRONUNCIATIONS_ITEM_KEYS = List.of("reading", "accentNotation",
            "accentType", "moraCount", "audioUrl", "audioProvider");

    private static final List<String> TRANSITIVITY_PAIR_KEYS = List.of("intransitive", "transitive",
            "particleNote", "intransitiveExample", "transitiveExample");

    private static final List<String> MEMORY_HINT_KEYS = List.of("hint", "basis");

    /** AI の応答（DTO のキー名）。すべての段落に 1 件以上入れる。 */
    private static final String RESPONSE = """
            {"detail":{
             "coreMeaning":"爱；喜爱","descriptionJa":"人や物を大切に思う気持ち。","descriptionZh":"对人或物怀有珍视的感情。",
             "partOfSpeech":"名詞","jlpt":"N3","conjugation":"なし","transitivity":"NONE","importance":5,
             "senses":[{"number":1,"japanese":"大切に思う気持ち。","chinese":"爱；喜爱",
                        "context":"家族・友人","style":"NEUTRAL","noteJapanese":"対象が広い。","noteChinese":"对象很广。"}],
             "examples":[{"japanese":"親の愛は無条件だ。","reading":"おやのあいはむじょうけんだ。",
                          "chinese":"父母的爱是无条件的。","senseNumber":1,"source":"家族","level":"BASIC"},
                         {"japanese":"彼は植物を愛している。","reading":"かれはしょくぶつをあいしている。",
                          "chinese":"他热爱植物。","senseNumber":1,"source":"","level":"APPLIED"}],
             "patterns":[{"pattern":"**を**愛する","reading":"をあいする","chinese":"爱…",
                          "example":"音楽を愛する。","exampleChinese":"热爱音乐。"}],
             "dialogs":[{"scene":"職員室で先生に相談する",
                         "lines":[{"speaker":"先生","japanese":"家族を愛していますか。","chinese":"你爱家人吗。"},
                                  {"speaker":"学生","japanese":"はい、愛しています。","chinese":"是的，我爱他们。"}]}],
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
                             {"expression":"愛が深い","reading":"あいがふかい","chinese":"爱意深厚",
                              "usage":"家族の話で","exampleJapanese":"母の愛が深い。",
                              "exampleChinese":"母爱深厚。"}],
             "relatedWords":[{"relation":"類義語","heading":"恋","reading":"こい","chinese":"恋爱"}],
             "usageNotes":[{"register":"どちらも","politeness":"普通","audience":"家族に",
                            "note":"書き言葉でも使う。","senseNumber":1}],
             "memoryHint":{"hint":"「心」が真ん中にある字。","basis":"漢字の形"},
             "practices":[{"kind":"PARTICLE","question":"音楽（ ）愛する。","questionChinese":"热爱音乐。",
                           "choices":["に","を"],"answer":"を","explanation":"対象は「を」。",
                           "freeWriting":false}]}}
            """;

    /** 版 ＋ 子テーブルから組み立てた詳細（user-api / admin-api が画面と AI に渡す形）。 */
    private static JsonNode assembled() {
        JapaneseWordAiDtoMapper.DetailResult parsed = JapaneseWordAiDtoMapper.parseDetail(RESPONSE);
        assertThat(parsed.isSuccess()).isTrue();

        // 詳細 → 版 ＋ 11 の子テーブル
        JapaneseWordAiDetailComposer.NewVersion version = JapaneseWordAiDetailComposer.compose(
                parsed.detailJson(), null, 77L, "qwen", "qwen3.7-plus", 0);
        JpnWordDetailEntity header = version.header();
        header.setWordId(101L);

        // 版 ＋ 子テーブル → 詳細（読みの経路）
        Map<String, Object> detail = JpnWordDetailAssembler.assemble(header, version.children());
        return MAPPER.valueToTree(detail);
    }

    private static List<String> keysOf(JsonNode node) {
        return node.fieldNames().hasNext() ? List.copyOf(iterable(node.fieldNames())) : List.of();
    }

    private static List<String> iterable(java.util.Iterator<String> source) {
        java.util.List<String> list = new java.util.ArrayList<>();
        source.forEachRemaining(list::add);
        return list;
    }

    @Test
    @DisplayName("ルートのキーは 26 個で、書き側（toDetailJson）と同じ順")
    void keepsRootKeys() {
        assertThat(keysOf(assembled())).containsExactlyElementsOf(ROOT_KEYS);
    }

    @Test
    @DisplayName("配列の要素のキーは段落ごとに決まっている（1 件ずつ確かめる）")
    void keepsItemKeys() {
        JsonNode detail = assembled();

        ITEM_KEYS.forEach((section, expected) -> {
            JsonNode items = detail.path(section);
            assertThat(items.isArray()).as("%s は配列", section).isTrue();
            assertThat(items).as("%s は 1 件以上", section).isNotEmpty();
            for (JsonNode item : items) {
                assertThat(keysOf(item)).as("%s の要素", section).containsExactlyElementsOf(expected);
            }
        });
    }

    @Test
    @DisplayName("会話は dialogs[].lines[] の 2 層で、キーは scene / lines と speaker / japanese / chinese")
    void keepsDialogKeys() {
        JsonNode dialogs = assembled().path("dialogs");

        assertThat(dialogs).hasSize(1);
        assertThat(keysOf(dialogs.path(0))).containsExactlyElementsOf(DIALOG_KEYS);
        JsonNode lines = dialogs.path(0).path("lines");
        assertThat(lines).hasSize(2);
        for (JsonNode line : lines) {
            assertThat(keysOf(line)).containsExactlyElementsOf(DIALOG_LINE_KEYS);
        }
    }

    @Test
    @DisplayName("小構造（発音・発音の配列・自他対応・記憶のヒント）のキーが揃う")
    void keepsSmallStructureKeys() {
        JsonNode detail = assembled();

        assertThat(keysOf(detail.path("pronunciation"))).containsExactlyElementsOf(PRONUNCIATION_KEYS);
        assertThat(keysOf(detail.path("transitivityPair")))
                .containsExactlyElementsOf(TRANSITIVITY_PAIR_KEYS);
        assertThat(keysOf(detail.path("memoryHint"))).containsExactlyElementsOf(MEMORY_HINT_KEYS);
        JsonNode pronunciations = detail.path("pronunciations");
        assertThat(pronunciations).hasSize(1);
        assertThat(keysOf(pronunciations.path(0))).containsExactlyElementsOf(PRONUNCIATIONS_ITEM_KEYS);
    }

    @Test
    @DisplayName("往復しても値が変わらない（詳細 → 版＋子テーブル → 詳細）")
    void keepsValuesThroughRoundTrip() {
        JapaneseWordAiDtoMapper.DetailResult parsed = JapaneseWordAiDtoMapper.parseDetail(RESPONSE);
        JsonNode before = read(parsed.detailJson());
        JsonNode after = assembled();

        // 段落の中身を突き合わせる（組み立ての取り違えを値で捕まえる）
        assertThat(after.path("senses")).isEqualTo(before.path("senses"));
        assertThat(after.path("examples")).isEqualTo(before.path("examples"));
        assertThat(after.path("patterns")).isEqualTo(before.path("patterns"));
        assertThat(after.path("dialogs")).isEqualTo(before.path("dialogs"));
        assertThat(after.path("synonyms")).isEqualTo(before.path("synonyms"));
        assertThat(after.path("cautions")).isEqualTo(before.path("cautions"));
        assertThat(after.path("collocations")).isEqualTo(before.path("collocations"));
        assertThat(after.path("relatedWords")).isEqualTo(before.path("relatedWords"));
        assertThat(after.path("usageNotes")).isEqualTo(before.path("usageNotes"));
        assertThat(after.path("practices")).isEqualTo(before.path("practices"));
        // 語レベルの値と小構造
        assertThat(after.path("coreMeaning")).isEqualTo(before.path("coreMeaning"));
        assertThat(after.path("chineseMeaning")).isEqualTo(before.path("chineseMeaning"));
        assertThat(after.path("descriptionJa")).isEqualTo(before.path("descriptionJa"));
        assertThat(after.path("descriptionZh")).isEqualTo(before.path("descriptionZh"));
        assertThat(after.path("partOfSpeech")).isEqualTo(before.path("partOfSpeech"));
        assertThat(after.path("jlptLevel")).isEqualTo(before.path("jlptLevel"));
        assertThat(after.path("conjugation")).isEqualTo(before.path("conjugation"));
        assertThat(after.path("transitivity")).isEqualTo(before.path("transitivity"));
        assertThat(after.path("importance")).isEqualTo(before.path("importance"));
        assertThat(after.path("conjugations")).isEqualTo(before.path("conjugations"));
        assertThat(after.path("transitivityPair")).isEqualTo(before.path("transitivityPair"));
        assertThat(after.path("pronunciation")).isEqualTo(before.path("pronunciation"));
        assertThat(after.path("pronunciations")).isEqualTo(before.path("pronunciations"));
        assertThat(after.path("memoryHint")).isEqualTo(before.path("memoryHint"));
        // AI の生の応答も残る（あとから突き合わせられるように）
        assertThat(after.path("structured")).isEqualTo(before.path("structured"));
    }

    private static JsonNode read(String json) {
        try {
            return MAPPER.readTree(json);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }
}

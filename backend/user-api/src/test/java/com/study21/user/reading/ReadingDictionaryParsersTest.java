package com.study21.user.reading;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 外部の辞書・翻訳 API の応答の解釈（{@link ReadingDictionaryParsers}）の検証。
 *
 * <p>ここに置く本文は**この環境から実際に取得した応答**（2026-09-14 実測）を元にしたもの。
 * 外部へは一切出ないので、ネットワークが無い環境でもこのテストは通る。</p>
 */
class ReadingDictionaryParsersTest {

    /** ExcelAPI enja（`hello`）の実応答。JSON ではなくプレーンテキスト。 */
    private static final String EXCEL_API_HELLO =
            "(電話の応答で)『もしもし』;(あいさつ・呼びかけ・驚きの声などに用いて)『こんにちは』,やあ,おい,ちょっと,おや,まあ "
                    + "/ こんにちは(やあ,おいなど)という呼びかけ(あいさつ)";

    /** 有道 suggest（`hello`）の実応答。 */
    private static final String YOUDAO_SUGGEST_HELLO = """
            {"result":{"msg":"success","code":200},"data":{"entries":[\
            {"explain":"int. 喂，你好（用于问候或打招呼）；喂，你好（打电话时的招呼语）；喂，你好（引起别人注意的招呼语...",\
            "entry":"hello"}],"query":"hello","language":"en","type":"dict"}}""";

    /** 有道 jsonapi（`汉字`）の実応答から必要な部分を写したもの。 */
    private static final String YOUDAO_JSONAPI_HANZI = """
            {
              "input": "汉字",
              "ce": {
                "source": {"language": "zh"},
                "word": [{
                  "return-phrase": "汉字",
                  "phone": "hàn zì",
                  "trs": [
                    {"tr": [{"l": {"i": ["", {"#text": "Chinese"}, " ", {"#text": "characters"}],\
                     "#tran": "汉字；中国字；"}}]},
                    {"tr": [{"l": {"pos": "n.", "i": ["", {"#text": "Hanzi"}], "#tran": "[语] 汉字；"}}]}
                  ]
                }]
              },
              "simple": {"query": "汉字", "word": [{"return-phrase": "汉字", "phone": "hàn zì"}]},
              "newhh": {"dataList": [{"pinyin": "hànzì", "word": "汉字"}]}
            }""";

    // ------------------------------------------------------------ ExcelAPI

    @Test
    void readsExcelApiPlainText() {
        assertThat(ReadingDictionaryParsers.excelApiJapanese(EXCEL_API_HELLO))
                .isEqualTo(EXCEL_API_HELLO);
    }

    @Test
    void treatsBlankOrErrorBodyAsNothing() {
        assertThat(ReadingDictionaryParsers.excelApiJapanese(null)).isNull();
        assertThat(ReadingDictionaryParsers.excelApiJapanese("   ")).isNull();
        assertThat(ReadingDictionaryParsers.excelApiJapanese("null")).isNull();
        assertThat(ReadingDictionaryParsers.excelApiJapanese("{\"error\":\"not found\"}")).isNull();
    }

    // ------------------------------------------------------- 有道 suggest

    @Test
    void readsYoudaoSuggestExplain() {
        assertThat(ReadingDictionaryParsers.youdaoSuggestChinese(YOUDAO_SUGGEST_HELLO))
                .startsWith("int. 喂，你好").contains("招呼语");
    }

    @Test
    void treatsFailedYoudaoSuggestAsNothing() {
        // result.msg が success でない
        assertThat(ReadingDictionaryParsers.youdaoSuggestChinese(
                "{\"result\":{\"msg\":\"fail\",\"code\":500},\"data\":{\"entries\":[]}}")).isNull();
        // entries が空
        assertThat(ReadingDictionaryParsers.youdaoSuggestChinese(
                "{\"result\":{\"msg\":\"success\",\"code\":200},\"data\":{\"entries\":[]}}")).isNull();
        // JSON ではない・空
        assertThat(ReadingDictionaryParsers.youdaoSuggestChinese("Sorry, unable to process request")).isNull();
        assertThat(ReadingDictionaryParsers.youdaoSuggestChinese("")).isNull();
        assertThat(ReadingDictionaryParsers.youdaoSuggestChinese(null)).isNull();
    }

    // ------------------------------------------------------- 有道 jsonapi

    @Test
    void readsYoudaoJsonApiPinyinAndExplanation() {
        ReadingDictionaryParsers.ChineseEntry entry =
                ReadingDictionaryParsers.youdaoJsonApi(YOUDAO_JSONAPI_HANZI);

        assertThat(entry).isNotNull();
        assertThat(entry.pinyin()).isEqualTo("hàn zì");
        assertThat(entry.explanation()).isEqualTo("汉字；中国字；\n[语] 汉字；");
    }

    /** `ce` が無ければ `simple` の phone、`newhh` の pinyin の順に拾う。 */
    @Test
    void fallsBackToSimpleAndNewhhForPinyin() {
        ReadingDictionaryParsers.ChineseEntry simpleOnly = ReadingDictionaryParsers.youdaoJsonApi(
                "{\"simple\":{\"word\":[{\"phone\":\"hàn zì\"}]}}");
        assertThat(simpleOnly.pinyin()).isEqualTo("hàn zì");
        assertThat(simpleOnly.explanation()).isNull();

        ReadingDictionaryParsers.ChineseEntry newhhOnly = ReadingDictionaryParsers.youdaoJsonApi(
                "{\"newhh\":{\"dataList\":[{\"pinyin\":\"hànzì\",\"word\":\"汉字\"}]}}");
        assertThat(newhhOnly.pinyin()).isEqualTo("hànzì");
        assertThat(newhhOnly.explanation()).isNull();

        // 解説は ec（英語対訳）からも拾う
        ReadingDictionaryParsers.ChineseEntry ecOnly = ReadingDictionaryParsers.youdaoJsonApi(
                "{\"ec\":{\"word\":[{\"trs\":[{\"tr\":[{\"l\":{\"#tran\":\"Chinese characters\"}}]}]}]}}");
        assertThat(ecOnly.pinyin()).isNull();
        assertThat(ecOnly.explanation()).isEqualTo("Chinese characters");
    }

    @Test
    void treatsUnreadableJsonApiAsNothing() {
        assertThat(ReadingDictionaryParsers.youdaoJsonApi("<html>502 Bad Gateway</html>")).isNull();
        assertThat(ReadingDictionaryParsers.youdaoJsonApi("{}")).isNull();
        assertThat(ReadingDictionaryParsers.youdaoJsonApi(null)).isNull();
        // 別の語を引いただけの応答（拼音も解説も無い）
        assertThat(ReadingDictionaryParsers.youdaoJsonApi("{\"input\":\"zzz\",\"lang\":\"chn\"}")).isNull();
    }
}

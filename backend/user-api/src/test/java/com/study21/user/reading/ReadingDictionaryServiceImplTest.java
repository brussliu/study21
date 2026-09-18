package com.study21.user.reading;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 語彙・読みの引き当ての業務ルール（{@link ReadingDictionaryServiceImpl}）。
 *
 * <p>外部 HTTP は {@link StubClient}（応答を固定したスタブ）に差し替えるので、
 * **ネットワークが無い環境でもこのテストは通る**。キャッシュは Mapper のモック。</p>
 */
class ReadingDictionaryServiceImplTest {

    private ReadingDictionaryMapper dictionaryMapper;
    private StubClient client;
    private ReadingDictionaryServiceImpl service;

    /** 応答を固定した辞書クライアント（呼ばれた回数を数える）。 */
    private static final class StubClient implements ReadingDictionaryClient {
        private String excelApi;
        private String youdaoSuggest;
        private String youdaoJsonApi;
        private int excelApiCalls;
        private int youdaoSuggestCalls;
        private int youdaoJsonApiCalls;

        @Override
        public String englishToJapanese(String word) {
            excelApiCalls++;
            return excelApi;
        }

        @Override
        public String englishToChinese(String word) {
            youdaoSuggestCalls++;
            return youdaoSuggest;
        }

        @Override
        public String chineseEntry(String word) {
            youdaoJsonApiCalls++;
            return youdaoJsonApi;
        }

        int calls() {
            return excelApiCalls + youdaoSuggestCalls + youdaoJsonApiCalls;
        }
    }

    @BeforeEach
    void setUp() {
        dictionaryMapper = mock(ReadingDictionaryMapper.class);
        client = new StubClient();
        service = new ReadingDictionaryServiceImpl(dictionaryMapper, client);
    }

    private static final String EXCEL_API_HELLO =
            "(電話の応答で)『もしもし』;(あいさつ)『こんにちは』";
    private static final String YOUDAO_SUGGEST_HELLO =
            "{\"result\":{\"msg\":\"success\",\"code\":200},\"data\":{\"entries\":"
                    + "[{\"explain\":\"int. 喂，你好\",\"entry\":\"hello\"}]}}";
    private static final String YOUDAO_JSONAPI_HANZI =
            "{\"ce\":{\"word\":[{\"phone\":\"hàn zì\",\"trs\":[{\"tr\":[{\"l\":{\"#tran\":\"汉字；中国字；\"}}]}]}]}}";

    // ---------------------------------------------------------------- 英語の本

    @Test
    void looksUpEnglishWordFromBothProvidersAndCachesIt() {
        client.excelApi = EXCEL_API_HELLO;
        client.youdaoSuggest = YOUDAO_SUGGEST_HELLO;
        when(dictionaryMapper.find("英語", "hello")).thenReturn(null);

        ReadingModels.LookupResult result =
                service.lookup(new ReadingModels.LookupRequest("hello", "英語"));

        assertThat(result.text()).isEqualTo("hello");
        assertThat(result.language()).isEqualTo("英語");
        assertThat(result.japanese()).isEqualTo(EXCEL_API_HELLO);
        assertThat(result.chinese()).isEqualTo("int. 喂，你好");
        assertThat(result.pinyin()).isNull();
        assertThat(result.source()).isEqualTo("EXCELAPI+YOUDAO");
        assertThat(result.cached()).isFalse();
        assertThat(result.message()).contains("取得しました");

        // 取れた項目がそのまま貯まる
        verify(dictionaryMapper).upsert(any(ReadingDictionaryEntity.class));
    }

    /** 片方だけ取れたときは取れた方だけを貯める（取得元も使ったものだけ）。 */
    @Test
    void cachesOnlyWhatWasFetched() {
        client.excelApi = EXCEL_API_HELLO;
        client.youdaoSuggest = null;
        when(dictionaryMapper.find("英語", "hello")).thenReturn(null);

        ReadingModels.LookupResult result =
                service.lookup(new ReadingModels.LookupRequest("hello", "英語"));

        assertThat(result.japanese()).isEqualTo(EXCEL_API_HELLO);
        assertThat(result.chinese()).isNull();
        assertThat(result.source()).isEqualTo("EXCELAPI");
    }

    /** 外部が全部失敗したら null ＋ message で返す（例外にしない＝200）。行も作らない。 */
    @Test
    void returnsNullsWhenNothingCouldBeFetched() {
        client.excelApi = null;
        client.youdaoSuggest = null;
        when(dictionaryMapper.find("英語", "hello")).thenReturn(null);

        ReadingModels.LookupResult result =
                service.lookup(new ReadingModels.LookupRequest("hello", "英語"));

        assertThat(result.japanese()).isNull();
        assertThat(result.chinese()).isNull();
        assertThat(result.source()).isNull();
        assertThat(result.cached()).isFalse();
        assertThat(result.message()).isEqualTo("辞書から意味を取得できませんでした。");
        verify(dictionaryMapper, never()).upsert(any());
    }

    // ------------------------------------------------------------ キャッシュ

    /** キャッシュに行があれば**外部を呼ばない**（呼び出し回数 0）。 */
    @Test
    void returnsCachedRowWithoutCallingProviders() {
        ReadingDictionaryEntity cached = new ReadingDictionaryEntity();
        cached.setLanguage("英語");
        cached.setHeadword("hello");
        cached.setJapanese("こんにちは");
        cached.setChinese("你好");
        cached.setSource("EXCELAPI+YOUDAO");
        when(dictionaryMapper.find("英語", "hello")).thenReturn(cached);

        ReadingModels.LookupResult result =
                service.lookup(new ReadingModels.LookupRequest("  Hello ", "英語"));

        assertThat(result.cached()).isTrue();
        assertThat(result.japanese()).isEqualTo("こんにちは");
        assertThat(result.chinese()).isEqualTo("你好");
        assertThat(result.message()).contains("キャッシュ");
        assertThat(client.calls()).as("外部へ行かない").isZero();
        verify(dictionaryMapper, never()).upsert(any());
    }

    /** 正規化した見出し語でキャッシュを引く（全角空白・連続空白・大文字小文字）。 */
    @Test
    void normalizesHeadwordBeforeCacheLookup() {
        when(dictionaryMapper.find(eq("英語"), eq("hello world"))).thenReturn(null);
        when(dictionaryMapper.find(eq("中国語"), eq("汉 字"))).thenReturn(null);

        assertThat(service.lookup(new ReadingModels.LookupRequest("　Hello　　World　", "英語")).text())
                .isEqualTo("hello world");
        assertThat(service.lookup(new ReadingModels.LookupRequest(" 汉  字 ", "中国語")).text())
                .isEqualTo("汉 字");

        verify(dictionaryMapper).find("英語", "hello world");
        verify(dictionaryMapper).find("中国語", "汉 字");
    }

    // -------------------------------------------------------------- 中国語の本

    @Test
    void looksUpChineseWordForPinyinAndExplanation() {
        client.youdaoJsonApi = YOUDAO_JSONAPI_HANZI;
        when(dictionaryMapper.find("中国語", "汉字")).thenReturn(null);

        ReadingModels.LookupResult result =
                service.lookup(new ReadingModels.LookupRequest("汉字", "中国語"));

        assertThat(result.pinyin()).isEqualTo("hàn zì");
        assertThat(result.explanation()).isEqualTo("汉字；中国字；");
        assertThat(result.chinese()).isEqualTo("汉字；中国字；");
        assertThat(result.japanese()).isNull();
        assertThat(result.source()).isEqualTo("YOUDAO");
        assertThat(result.cached()).isFalse();
    }

    // -------------------------------------------------------------- 日本語の本

    @Test
    void doesNotLookUpJapaneseBooks() {
        ReadingModels.LookupResult result =
                service.lookup(new ReadingModels.LookupRequest("こんにちは", "日本語"));

        assertThat(result.japanese()).isNull();
        assertThat(result.chinese()).isNull();
        assertThat(result.pinyin()).isNull();
        assertThat(result.explanation()).isNull();
        assertThat(result.message()).isEqualTo("日本語の本では語彙の引き当ては使いません。");
        assertThat(client.calls()).isZero();
        verify(dictionaryMapper, never()).find(any(), any());
    }

    // ------------------------------------------------------------------ 検証

    @Test
    void rejectsUnknownLanguageAndBlankText() {
        assertThatThrownBy(() -> service.lookup(new ReadingModels.LookupRequest("hello", "フランス語")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("言語");
        assertThatThrownBy(() -> service.lookup(new ReadingModels.LookupRequest("hello", null)))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> service.lookup(new ReadingModels.LookupRequest("  　 ", "英語")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("語を入力");
        assertThat(client.calls()).isZero();
    }
}

package com.study21.user.reading;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * `POST /api/user/reading/lookup`（語彙・読みの引き当て）を実際の HTTP で検証する。
 *
 * <p>外部の辞書・翻訳 API は {@link StubDictionary}（この環境で実測した応答を返すスタブ）に
 * 差し替えるので、**テストは外部ネットワークに依存しない**（オフラインでも通る）。</p>
 */
@Import(ReadingLookupApiTest.StubDictionaryConfig.class)
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class ReadingLookupApiTest extends ReadingHttpTestSupport {

    /** 本番の `ReadingDictionaryHttpClient` の代わりに使うスタブ（実測した応答）。 */
    static class StubDictionary implements ReadingDictionaryClient {
        static final String EXCEL_API_HELLO =
                "(電話の応答で)『もしもし』;(あいさつ)『こんにちは』";
        static final String YOUDAO_SUGGEST_HELLO =
                "{\"result\":{\"msg\":\"success\",\"code\":200},\"data\":{\"entries\":"
                        + "[{\"explain\":\"int. 喂，你好\",\"entry\":\"hello\"}]}}";
        static final String YOUDAO_JSONAPI_HANZI =
                "{\"ce\":{\"word\":[{\"phone\":\"hàn zì\","
                        + "\"trs\":[{\"tr\":[{\"l\":{\"#tran\":\"汉字；中国字；\"}}]}]}]}}";

        @Override
        public String englishToJapanese(String word) {
            return "hello".equals(word) ? EXCEL_API_HELLO : null;
        }

        @Override
        public String englishToChinese(String word) {
            return "hello".equals(word) ? YOUDAO_SUGGEST_HELLO : null;
        }

        @Override
        public String chineseEntry(String word) {
            return "汉字".equals(word) ? YOUDAO_JSONAPI_HANZI : null;
        }
    }

    /** スタブを本番の実装より優先させる（`@Primary`）。 */
    @TestConfiguration
    static class StubDictionaryConfig {
        @Bean
        @Primary
        ReadingDictionaryClient stubReadingDictionaryClient() {
            return new StubDictionary();
        }
    }

    @Autowired
    private ReadingDictionaryMapper dictionaryMapper;

    private final ObjectMapper json = new ObjectMapper();

    /**
     * テストは「キャッシュが空」の状態から検証する。同じ DB（study21）を E2E や手動操作で
     * 触るとキャッシュ行が残り、外部（スタブ）へ行かずに前回の実データが返ってしまうため、
     * 対象の語だけ毎回消してから走らせる（残っても実害はないキャッシュ表なので削除で問題ない）。
     */
    @BeforeEach
    void clearDictionaryCache() {
        dictionaryMapper.delete("英語", "hello");
        dictionaryMapper.delete("中国語", "汉字");
        dictionaryMapper.delete("英語", "zzzznotfound");
    }

    @Test
    void looksUpEnglishWordAndReturnsJsonShape() throws Exception {
        Cookie cookie = login();

        MvcResult result = mockMvc.perform(post("/api/user/reading/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"  Hello \",\"language\":\"英語\"}")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = json.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("success").asBoolean()).isTrue();
        assertThat(root.path("message").asText()).isEqualTo("辞書から意味を取得しました。");
        JsonNode data = root.path("data");
        assertThat(data.path("text").asText()).as("正規化した見出し語").isEqualTo("hello");
        assertThat(data.path("language").asText()).isEqualTo("英語");
        assertThat(data.path("japanese").asText()).startsWith("(電話の応答で)");
        assertThat(data.path("chinese").asText()).isEqualTo("int. 喂，你好");
        assertThat(data.path("pinyin").isNull()).isTrue();
        assertThat(data.path("explanation").isNull()).isTrue();
        assertThat(data.path("source").asText()).isEqualTo("EXCELAPI+YOUDAO");
        assertThat(data.path("cached").asBoolean()).isFalse();

        // 2 回目はキャッシュ（外部へ行かない）
        MvcResult again = mockMvc.perform(post("/api/user/reading/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\",\"language\":\"英語\"}")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode cached = json.readTree(again.getResponse().getContentAsString()).path("data");
        assertThat(cached.path("cached").asBoolean()).as("2 回目はキャッシュから").isTrue();
        assertThat(cached.path("japanese").asText()).startsWith("(電話の応答で)");
        assertThat(readingDictionaryCount("英語", "hello")).as("キャッシュは 1 行のまま").isEqualTo(1);
    }

    @Test
    void looksUpChineseWordForPinyin() throws Exception {
        Cookie cookie = login();

        MvcResult result = mockMvc.perform(post("/api/user/reading/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"汉字\",\"language\":\"中国語\"}")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode data = json.readTree(result.getResponse().getContentAsString()).path("data");
        assertThat(data.path("pinyin").asText()).isEqualTo("hàn zì");
        assertThat(data.path("explanation").asText()).isEqualTo("汉字；中国字；");
        assertThat(data.path("source").asText()).isEqualTo("YOUDAO");
    }

    /** 外部（スタブ）が取れない語は 200 のまま null とメッセージで返る。 */
    @Test
    void returnsNullsWhenProvidersHaveNothing() throws Exception {
        Cookie cookie = login();

        MvcResult result = mockMvc.perform(post("/api/user/reading/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"zzzznotfound\",\"language\":\"英語\"}")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = json.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("message").asText()).isEqualTo("辞書から意味を取得できませんでした。");
        assertThat(root.path("data").path("japanese").isNull()).isTrue();
        assertThat(root.path("data").path("chinese").isNull()).isTrue();
        assertThat(readingDictionaryCount("英語", "zzzznotfound")).as("取れなかったら行を作らない").isZero();
    }

    /** 日本語の本では引かない（200 のまま）。 */
    @Test
    void doesNotLookUpJapanese() throws Exception {
        Cookie cookie = login();

        MvcResult result = mockMvc.perform(post("/api/user/reading/lookup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"こんにちは\",\"language\":\"日本語\"}")
                        .cookie(cookie))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode root = json.readTree(result.getResponse().getContentAsString());
        assertThat(root.path("message").asText()).isEqualTo("日本語の本では語彙の引き当ては使いません。");
        assertThat(root.path("data").path("pinyin").isNull()).isTrue();
    }

    /** 不正な body は 400（言語が 3 種類以外・空の語・文字数超過）。 */
    @Test
    void rejectsInvalidBody() throws Exception {
        Cookie cookie = login();

        mockMvc.perform(post("/api/user/reading/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\",\"language\":\"フランス語\"}").cookie(cookie))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/user/reading/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"   \",\"language\":\"英語\"}").cookie(cookie))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/user/reading/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"language\":\"英語\"}").cookie(cookie))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/user/reading/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"" + "a".repeat(101) + "\",\"language\":\"英語\"}").cookie(cookie))
                .andExpect(status().isBadRequest());
    }

    /** 認証が無ければ 401。 */
    @Test
    void requiresAuthentication() throws Exception {
        mockMvc.perform(post("/api/user/reading/lookup").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"text\":\"hello\",\"language\":\"英語\"}"))
                .andExpect(status().isUnauthorized());
    }

    /** キャッシュ行の数（本番と同じ Mapper で数える）。 */
    private long readingDictionaryCount(String language, String headword) {
        return dictionaryMapper.find(language, headword) == null ? 0 : 1;
    }
}

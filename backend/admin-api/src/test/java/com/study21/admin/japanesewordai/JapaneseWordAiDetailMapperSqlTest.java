package com.study21.admin.japanesewordai;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AI 取得（batC41〜batC44）の SQL のうち、<b>詳細の版を読む部分</b>の契約。
 *
 * <p>確かめる接縫（実 DB は使わない。MyBatis に XML を読ませて SQL を固定する）:</p>
 * <ol>
 *   <li>AI に渡す既存詳細は<b>有効版</b>（{@code 状態コード='ACTIVE'}）から組む。
 *       履歴（ARCHIVED）を AI に渡さない</li>
 *   <li>そのために 11 の子テーブルを {@code 詳細ID} で読む（user-api の
 *       {@code JpnWordDetailMapper.xml} と同じ並び・同じ列）</li>
 *   <li>廃止した {@code 詳細JSON} は読まない</li>
 * </ol>
 */
class JapaneseWordAiDetailMapperSqlTest {

    private static final String NAMESPACE = "com.study21.admin.japanesewordai.JapaneseWordAiMapper.";

    private static final String XML = readXml("/mapper/JapaneseWordAiMapper.xml");

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = JapaneseWordAiDetailMapperSqlTest.class
                .getResourceAsStream("/mapper/JapaneseWordAiMapper.xml")) {
            assertThat(input).as("JapaneseWordAiMapper.xml").isNotNull();
            new XMLMapperBuilder(input, configuration, "JapaneseWordAiMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String readXml(String resource) {
        try (InputStream stream = JapaneseWordAiDetailMapperSqlTest.class.getResourceAsStream(resource)) {
            assertThat(stream).as(resource).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    /** 指定した id の要素の中身（開始タグの次から終了タグまで）。 */
    private static String statement(String tag, String id) {
        String start = "<" + tag + " id=\"" + id + "\"";
        int from = XML.indexOf(start);
        assertThat(from).as("%s id=%s", tag, id).isNotNegative();
        int to = XML.indexOf("</" + tag + ">", from);
        return XML.substring(from, to);
    }

    @Test
    @DisplayName("AI に渡す既存詳細は有効版だけ（履歴を AI に渡さない）")
    void readsActiveDetailForAiInput() {
        String sql = statement("select", "findActiveDetail");

        assertThat(sql).contains("FROM public.\"JPN_単語詳細情報\"");
        assertThat(sql).contains("d.\"単語ID\" = #{wordId}");
        assertThat(sql).contains("d.\"状態コード\" = 'ACTIVE'");
        assertThat(sql).contains("LIMIT 1");
    }

    @Test
    @DisplayName("版のヘッダは語レベルの内容と JSONB の小構造をすべて読む（user-api と同じ列）")
    void selectsHeaderColumns() {
        String columns = statement("sql", "DetailHeaderColumns");

        for (String column : new String[]{"詳細ID", "単語ID", "内容版数", "状態コード", "元詳細ID", "生成ID",
                "AIプロバイダ", "AIモデル", "取得日時", "手修正フラグ", "核心意味", "説明_日本語", "説明_中国語",
                "品詞", "JLPTレベル", "活用型", "自他", "重要度", "記憶ヒント", "記憶ヒント根拠"}) {
            assertThat(columns).as("列 %s", column).contains("\"" + column + "\"");
        }
        for (String column : new String[]{"発音JSON", "活用形JSON", "自他対応JSON", "元レスポンスJSON"}) {
            assertThat(columns).as("JSONB の列 %s", column)
                    .contains("\"" + column + "\"::text AS \"" + column + "\"");
        }
    }

    @Test
    @DisplayName("11 の子テーブルを読み、並びは 表示順 → 段落ID")
    void selectsAllChildTables() {
        String[][] tables = {
                {"listDetailSenses", "JPN_単語詳細_語義情報", "語義ID"},
                {"listDetailExamples", "JPN_単語詳細_例文情報", "例文ID"},
                {"listDetailPatterns", "JPN_単語詳細_文型情報", "文型ID"},
                {"listDetailDialogs", "JPN_単語詳細_会話情報", "会話ID"},
                {"listDetailDialogLines", "JPN_単語詳細_会話行情報", "会話行ID"},
                {"listDetailSynonyms", "JPN_単語詳細_類義語情報", "類義語ID"},
                {"listDetailCautions", "JPN_単語詳細_注意情報", "注意ID"},
                {"listDetailCollocations", "JPN_単語詳細_コロケーション情報", "コロケーションID"},
                {"listDetailRelatedWords", "JPN_単語詳細_関連語情報", "関連語ID"},
                {"listDetailUsageNotes", "JPN_単語詳細_使用場面情報", "使用場面ID"},
                {"listDetailPractices", "JPN_単語詳細_練習情報", "練習ID"},
        };
        for (String[] table : tables) {
            String sql = statement("select", table[0]);
            assertThat(sql).as("%s", table[0]).contains("public.\"" + table[1] + "\"");
            assertThat(sql).as("%s の並び", table[0]).contains("\"表示順\"").contains(table[2]);
        }
    }

    @Test
    @DisplayName("子テーブルは詳細ID で絞り、会話の発言だけは会話ID 経由で引く")
    void filtersChildrenByVersion() {
        for (String id : new String[]{"listDetailSenses", "listDetailExamples", "listDetailPatterns",
                "listDetailDialogs", "listDetailSynonyms", "listDetailCautions", "listDetailCollocations",
                "listDetailRelatedWords", "listDetailUsageNotes", "listDetailPractices"}) {
            assertThat(statement("select", id)).as("%s", id).contains("\"詳細ID\" = #{detailId}");
        }
        String lines = statement("select", "listDetailDialogLines");
        assertThat(lines).contains("JOIN public.\"JPN_単語詳細_会話情報\"");
        assertThat(lines).contains("g.\"詳細ID\" = #{detailId}");
        assertThat(lines).doesNotContain("l.\"詳細ID\"");
    }

    @Test
    @DisplayName("対象の語を引くときは既存の詳細を JOIN しない（有効版は別に引く）")
    void findTargetsDoesNotJoinOldDetailJson() {
        String sql = statement("select", "findTargets");

        assertThat(sql).doesNotContain("詳細JSON");
        assertThat(sql).doesNotContain("JPN_単語詳細情報");
    }

    @Test
    @DisplayName("既存の詳細を読む口は有効版のものだけ（廃止した 詳細JSON は読まない）")
    void readsOnlyActiveVersionColumns() {
        // 詳細JSON（全部入りの JSONB）は切片1 で廃止した。読むのは版のヘッダと子テーブル
        assertThat(configuration.hasStatement(NAMESPACE + "findActiveDetail")).isTrue();
        assertThat(statement("select", "findActiveDetail")).doesNotContain("\"詳細JSON\"");
    }
}

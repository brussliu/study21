package com.study21.user.japanese;

import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 詳細（版のヘッダ ＋ 11 の子テーブル）を読む SQL の契約。
 *
 * <p>確かめる接縫（実 DB は使わない。MyBatis に XML を読ませて SQL を固定する）:</p>
 * <ol>
 *   <li>読むのは<b>有効版</b>（{@code 状態コード='ACTIVE'}）だけ</li>
 *   <li>子テーブルは 11 すべてに SELECT があり、{@code 詳細ID} で絞る</li>
 *   <li>並びは {@code 表示順, <段落>ID}（組み立ては並べ替えないので、並びは SQL が決める）</li>
 *   <li>会話の発言は {@code 会話ID} で親にぶら下がる（{@code 詳細ID} の列を持たない）ので
 *       会話を経由して引く</li>
 *   <li>JSONB の列は {@code ::text} で読む（列ごとの TypeHandler を足さない）</li>
 *   <li>廃止した {@code 詳細JSON} を読まない・書かない</li>
 * </ol>
 */
class JpnWordDetailMapperSqlTest {

    private static final String NAMESPACE = "com.study21.user.japanese.JpnWordDetailMapper.";

    private static final String XML = readXml("/mapper/JpnWordDetailMapper.xml");

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = JpnWordDetailMapperSqlTest.class
                .getResourceAsStream("/mapper/JpnWordDetailMapper.xml")) {
            assertThat(input).as("JpnWordDetailMapper.xml").isNotNull();
            new XMLMapperBuilder(input, configuration, "JpnWordDetailMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String readXml(String resource) {
        try (InputStream stream = JpnWordDetailMapperSqlTest.class.getResourceAsStream(resource)) {
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
    @DisplayName("読むのは有効版（状態コード='ACTIVE'）だけ。まだ無ければ 0 行")
    void readsOnlyActiveVersion() {
        String sql = statement("select", "findActiveDetail");

        assertThat(sql).contains("FROM public.\"JPN_単語詳細情報\"");
        assertThat(sql).contains("d.\"状態コード\" = 'ACTIVE'");
        assertThat(sql).contains("d.\"単語ID\" = #{wordId}");
        // 1 語 1 行（uq_jpn_detail_active）なので 1 行に決める
        assertThat(sql).contains("LIMIT 1");
    }

    @Test
    @DisplayName("版のヘッダは語レベルの内容と JSONB の小構造をすべて読む")
    void selectsHeaderColumns() {
        String columns = statement("sql", "DetailHeaderColumns");

        for (String column : new String[]{"詳細ID", "単語ID", "内容版数", "状態コード", "元詳細ID", "生成ID",
                "AIプロバイダ", "AIモデル", "取得日時", "手修正フラグ", "核心意味", "説明_日本語", "説明_中国語",
                "品詞", "JLPTレベル", "活用型", "自他", "重要度", "記憶ヒント", "記憶ヒント根拠"}) {
            assertThat(columns).as("列 %s", column).contains("\"" + column + "\"");
        }
        // JSONB は文字列で受けて、組み立て（JpnWordDetailAssembler）が読む
        for (String column : new String[]{"発音JSON", "活用形JSON", "自他対応JSON", "元レスポンスJSON"}) {
            assertThat(columns).as("JSONB の列 %s", column).contains("\"" + column + "\"::text AS \"" + column + "\"");
        }
    }

    @Test
    @DisplayName("11 の子テーブルすべてに SELECT があり、詳細ID で絞る")
    void selectsAllChildTables() {
        String[][] tables = {
                {"listSenses", "JPN_単語詳細_語義情報", "語義ID"},
                {"listExamples", "JPN_単語詳細_例文情報", "例文ID"},
                {"listPatterns", "JPN_単語詳細_文型情報", "文型ID"},
                {"listDialogs", "JPN_単語詳細_会話情報", "会話ID"},
                {"listDialogLines", "JPN_単語詳細_会話行情報", "会話行ID"},
                {"listSynonyms", "JPN_単語詳細_類義語情報", "類義語ID"},
                {"listCautions", "JPN_単語詳細_注意情報", "注意ID"},
                {"listCollocations", "JPN_単語詳細_コロケーション情報", "コロケーションID"},
                {"listRelatedWords", "JPN_単語詳細_関連語情報", "関連語ID"},
                {"listUsageNotes", "JPN_単語詳細_使用場面情報", "使用場面ID"},
                {"listPractices", "JPN_単語詳細_練習情報", "練習ID"},
        };
        for (String[] table : tables) {
            String id = table[0];
            String sql = statement("select", id);
            assertThat(sql).as("%s は %s を読む", id, table[1]).contains("public.\"" + table[1] + "\"");
            // 並びは「表示順 → 段落ID」（人が入れた順に読む）
            assertThat(sql).as("%s の並び", id).contains("ORDER BY").contains("\"表示順\"");
            assertThat(sql).as("%s の並び（同順の決め手）", id).contains(table[2]);
        }
    }

    @Test
    @DisplayName("子テーブルは詳細ID で絞り、会話の発言だけは会話ID 経由で引く")
    void filtersChildrenByVersion() {
        for (String id : new String[]{"listSenses", "listExamples", "listPatterns", "listDialogs",
                "listSynonyms", "listCautions", "listCollocations", "listRelatedWords",
                "listUsageNotes", "listPractices"}) {
            assertThat(statement("select", id)).as("%s", id).contains("\"詳細ID\" = #{detailId}");
        }
        // 会話行は 会話ID で親にぶら下がる（詳細ID の列を持たない）
        String lines = statement("select", "listDialogLines");
        assertThat(lines).contains("public.\"JPN_単語詳細_会話行情報\"");
        assertThat(lines).contains("JOIN public.\"JPN_単語詳細_会話情報\"");
        assertThat(lines).contains("g.\"詳細ID\" = #{detailId}");
        assertThat(lines).doesNotContain("l.\"詳細ID\"");
    }

    @Test
    @DisplayName("一覧の「詳細情報件数」は、指定した語の有効版の段落をまとめて数える")
    void countsSectionsForTheGivenWords() {
        String sql = statement("select", "listActiveSectionCounts");

        // 有効版だけを数える（履歴の版は数えない）
        assertThat(sql).contains("d.\"状態コード\" = 'ACTIVE'");
        // 1 ページぶんの語 ID をまとめて渡す（1 語ずつ引かない）
        assertThat(sql).contains("d.\"単語ID\" IN");
        assertThat(sql).contains("<foreach item=\"wordId\" collection=\"wordIds\"");
        // 数えるのは段落の親テーブル 10 個。会話の発言は会話に含まれるので数えない
        for (String table : new String[]{"語義情報", "例文情報", "文型情報", "会話情報", "類義語情報",
                "注意情報", "コロケーション情報", "関連語情報", "使用場面情報", "練習情報"}) {
            assertThat(sql).as("段落 %s", table).contains("JPN_単語詳細_" + table);
        }
        assertThat(sql).doesNotContain("会話行情報");
        // 語ごとに 1 行（wordId が主キー）
        assertThat(sql).contains("d.\"単語ID\"                                 AS \"単語ID\"");
    }

    @Test
    @DisplayName("練習の選択肢JSON は文字列で読む（組み立てで文字列の配列にする）")
    void readsPracticeChoicesAsText() {
        assertThat(statement("select", "listPractices"))
                .contains("\"選択肢JSON\"::text AS \"選択肢JSON\"");
    }

    @Test
    @DisplayName("廃止した 詳細JSON は読まないし書かない")
    void neverTouchesRemovedDetailJsonColumn() {
        assertThat(XML).doesNotContain("詳細JSON");
        // 詳細を読むのは findActiveDetail だけ（有効版）
        assertThat(configuration.hasStatement(NAMESPACE + "findActiveDetail")).isTrue();
    }

    @Test
    @DisplayName("詳細の段落を読む口を 11 個そろえる（書き側が作った版をそのまま読める）")
    void exposesOneStatementPerChildTable() {
        for (String id : new String[]{"findActiveDetail", "listSenses", "listExamples", "listPatterns",
                "listDialogs", "listDialogLines", "listSynonyms", "listCautions", "listCollocations",
                "listRelatedWords", "listUsageNotes", "listPractices"}) {
            assertThat(configuration.hasStatement(NAMESPACE + id)).as("%s", id).isTrue();
        }
    }
}

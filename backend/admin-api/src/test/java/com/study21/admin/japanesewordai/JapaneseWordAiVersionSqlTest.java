package com.study21.admin.japanesewordai;

import com.study21.common.core.japanese.JpnWordDetailChildren;
import com.study21.common.core.japanese.JpnWordDetailEntity;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * batC41 の詳細の書き込み（＝<b>新しい版を作る</b>）SQL の契約。
 *
 * <p>確かめる接縫（実 DB は使わない。MyBatis に XML を読ませて SQL を固定する）:</p>
 * <ol>
 *   <li>{@code upsertDetail}（1 語 1 行の上書き）は<b>無い</b>。版は積み上げる</li>
 *   <li>新しい版は {@code 状態コード='ACTIVE'}、古い有効版は {@code ARCHIVED}（消さない）</li>
 *   <li>内容版数は「その語の最大 + 1」。元詳細ID は「今の有効版」</li>
 *   <li>人の行（{@code 登録元コード='APP'} または {@code 手修正フラグ=true}）は
 *       <b>新しい版へ複製する</b>（内容も出所も {@code 表示順} もそのまま）</li>
 *   <li>AI の新しい行は、複製した行の後ろに付ける（{@code 表示順} は
 *       「今の版の最大 + 1」から。複製した行を上書きしない）</li>
 *   <li>版のヘッダは AI 生成（{@code 生成ID}・{@code AIプロバイダ}・{@code AIモデル}・
 *       {@code 取得日時}）を持ち、{@code 登録元コード='BATCH'}</li>
 * </ol>
 */
class JapaneseWordAiVersionSqlTest {

    private static final String NAMESPACE = "com.study21.admin.japanesewordai.JapaneseWordAiMapper.";

    private static final String XML = readXml();

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = JapaneseWordAiVersionSqlTest.class
                .getResourceAsStream("/mapper/JapaneseWordAiMapper.xml")) {
            assertThat(input).as("JapaneseWordAiMapper.xml").isNotNull();
            new XMLMapperBuilder(input, configuration, "JapaneseWordAiMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String readXml() {
        try (InputStream stream = JapaneseWordAiVersionSqlTest.class
                .getResourceAsStream("/mapper/JapaneseWordAiMapper.xml")) {
            assertThat(stream).as("JapaneseWordAiMapper.xml").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    /** 文の SQL（MyBatis が組み立てた形。パラメータは ? になる）。 */
    private static String boundSql(String id, Map<String, Object> parameters) {
        MappedStatement statement = configuration.getMappedStatement(NAMESPACE + id);
        return statement.getBoundSql(parameters).getSql().replaceAll("\\s+", " ");
    }

    private static String boundSql(String id) {
        return boundSql(id, new HashMap<>());
    }

    /** 指定した id の要素の中身（XML の生の形。{@code #{...}} のままで見たいとき）。 */
    private static String statement(String tag, String id) {
        String start = "<" + tag + " id=\"" + id + "\"";
        int from = XML.indexOf(start);
        assertThat(from).as("%s id=%s", tag, id).isNotNegative();
        int to = XML.indexOf("</" + tag + ">", from);
        return XML.substring(from, to);
    }

    private static Map<String, Object> parameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("wordId", 101L);
        parameters.put("detailId", 901L);
        parameters.put("sourceDetailId", 900L);
        parameters.put("generationId", 77L);
        parameters.put("originDetailId", 900L);
        parameters.put("aiProvider", "qwen");
        parameters.put("aiModel", "qwen3.7-plus");
        parameters.put("startOrderNo", 6);
        parameters.put("coreMeaning", "愛");
        parameters.put("descriptionJa", "説明。");
        parameters.put("descriptionZh", "说明。");
        parameters.put("partOfSpeech", "名詞");
        parameters.put("jlptLevel", "N3");
        parameters.put("conjugation", "なし");
        parameters.put("transitivity", "NONE");
        parameters.put("importance", 5);
        parameters.put("memoryHint", "ヒント");
        parameters.put("memoryHintBasis", "根拠");
        parameters.put("fetchedAt", java.sql.Timestamp.valueOf("2026-01-01 00:00:00"));
        parameters.put("pronunciationJson", "{}");
        parameters.put("conjugationsJson", "[]");
        parameters.put("transitivityPairJson", "{}");
        parameters.put("structuredJson", "{}");
        return parameters;
    }

    /** 子テーブルへの INSERT（{@code row} と {@code header} を渡す）。 */
    private static Map<String, Object> rowParameters(String id) {
        Map<String, Object> parameters = parameters();
        parameters.put("row", rowOf(id));
        parameters.put("dialogId", 5001L);
        parameters.put("header", header());
        return parameters;
    }

    /** INSERT が読む {@code row}（どの段落かで型が変わる）。 */
    private static Object rowOf(String id) {
        return switch (id) {
            case "insertDetailSense" -> new JpnWordDetailChildren.Sense();
            case "insertDetailExample" -> new JpnWordDetailChildren.Example();
            case "insertDetailPattern" -> new JpnWordDetailChildren.Pattern();
            case "insertDetailDialog" -> new JpnWordDetailChildren.Dialog();
            case "insertDetailDialogLine" -> new JpnWordDetailChildren.DialogLine();
            case "insertDetailSynonym" -> new JpnWordDetailChildren.Synonym();
            case "insertDetailCaution" -> new JpnWordDetailChildren.Caution();
            case "insertDetailCollocation" -> new JpnWordDetailChildren.Collocation();
            case "insertDetailRelatedWord" -> new JpnWordDetailChildren.RelatedWord();
            case "insertDetailUsageNote" -> new JpnWordDetailChildren.UsageNote();
            case "insertDetailPractice" -> new JpnWordDetailChildren.Practice();
            default -> null;
        };
    }

    private static JpnWordDetailEntity header() {
        return new JpnWordDetailEntity();
    }

    /** 11 の子テーブルの「複製」。表と段落ID の対応。 */
    private static final String[][] COPIES = {
            {"copyDetailSenses", "JPN_単語詳細_語義情報", "語義ID"},
            {"copyDetailExamples", "JPN_単語詳細_例文情報", "例文ID"},
            {"copyDetailPatterns", "JPN_単語詳細_文型情報", "文型ID"},
            {"copyDetailDialogs", "JPN_単語詳細_会話情報", "会話ID"},
            {"copyDetailDialogLines", "JPN_単語詳細_会話行情報", "会話行ID"},
            {"copyDetailSynonyms", "JPN_単語詳細_類義語情報", "類義語ID"},
            {"copyDetailCautions", "JPN_単語詳細_注意情報", "注意ID"},
            {"copyDetailCollocations", "JPN_単語詳細_コロケーション情報", "コロケーションID"},
            {"copyDetailRelatedWords", "JPN_単語詳細_関連語情報", "関連語ID"},
            {"copyDetailUsageNotes", "JPN_単語詳細_使用場面情報", "使用場面ID"},
            {"copyDetailPractices", "JPN_単語詳細_練習情報", "練習ID"},
    };

    /** 11 の子テーブルの「AI の新しい行」。 */
    private static final String[][] INSERTS = {
            {"insertDetailSense", "JPN_単語詳細_語義情報"},
            {"insertDetailExample", "JPN_単語詳細_例文情報"},
            {"insertDetailPattern", "JPN_単語詳細_文型情報"},
            {"insertDetailDialog", "JPN_単語詳細_会話情報"},
            {"insertDetailDialogLine", "JPN_単語詳細_会話行情報"},
            {"insertDetailSynonym", "JPN_単語詳細_類義語情報"},
            {"insertDetailCaution", "JPN_単語詳細_注意情報"},
            {"insertDetailCollocation", "JPN_単語詳細_コロケーション情報"},
            {"insertDetailRelatedWord", "JPN_単語詳細_関連語情報"},
            {"insertDetailUsageNote", "JPN_単語詳細_使用場面情報"},
            {"insertDetailPractice", "JPN_単語詳細_練習情報"},
    };

    @Test
    @DisplayName("1 語 1 行の上書き（upsertDetail）は消す。版は積み上げる")
    void doesNotOverwriteSingleDetail() {
        assertThat(configuration.hasStatement(NAMESPACE + "upsertDetail")).isFalse();
        assertThat(XML).doesNotContain("ON CONFLICT (\"単語ID\")");
    }

    @Test
    @DisplayName("古い有効版は ARCHIVED にする（消さない）")
    void archivesPreviousActiveVersion() {
        String sql = boundSql("archiveActiveDetail");

        assertThat(sql).contains("UPDATE public.\"JPN_単語詳細情報\"");
        assertThat(sql).contains("SET \"状態コード\" = 'ARCHIVED'");
        assertThat(sql).contains("WHERE \"単語ID\" = ?");
        assertThat(sql).contains("AND \"状態コード\" = 'ACTIVE'");
        assertThat(configuration.getMappedStatement(NAMESPACE + "archiveActiveDetail").getSqlCommandType())
                .isEqualTo(SqlCommandType.UPDATE);
    }

    @Test
    @DisplayName("新しい版は ACTIVE で入り、生成ID・AIプロバイダ・AIモデル・取得日時を持つ")
    void insertsNewActiveVersion() {
        String sql = boundSql("insertDetailVersion", rowParameters("insertDetailVersion"));

        assertThat(sql).contains("INSERT INTO public.\"JPN_単語詳細情報\"");
        assertThat(sql).contains("'ACTIVE'");
        assertThat(sql).contains("'BATCH'");
        for (String column : new String[]{"単語ID", "内容版数", "状態コード", "元詳細ID", "生成ID",
                "AIプロバイダ", "AIモデル", "取得日時", "手修正フラグ"}) {
            assertThat(sql).as("列 %s", column).contains("\"" + column + "\"");
        }
        // 語レベルの内容もこの版に入る
        for (String column : new String[]{"核心意味", "説明_日本語", "説明_中国語", "品詞", "JLPTレベル",
                "活用型", "自他", "重要度", "記憶ヒント", "記憶ヒント根拠",
                "発音JSON", "活用形JSON", "自他対応JSON", "元レスポンスJSON"}) {
            assertThat(sql).as("列 %s", column).contains("\"" + column + "\"");
        }
        // JSONB はキャストして入れる
        assertThat(sql).contains("CAST(COALESCE(?");
        assertThat(configuration.getMappedStatement(NAMESPACE + "insertDetailVersion").getSqlCommandType())
                .isEqualTo(SqlCommandType.INSERT);
        // 採番した 詳細ID を Java 側へ返す（子テーブルの 詳細ID に使う）
        assertThat(configuration.getMappedStatement(NAMESPACE + "insertDetailVersion").getKeyGenerator())
                .isNotNull();
    }

    @Test
    @DisplayName("内容版数は「その語の最大 + 1」を INSERT の中で数えて入れる")
    void numbersVersionInsideTheInsert() {
        String sql = boundSql("insertDetailVersion", rowParameters("insertDetailVersion"));

        // 採番は INSERT の中の SELECT（同じ語を並行に取っても番号がぶつからない）
        assertThat(sql).contains("MAX(d.\"内容版数\")");
        assertThat(sql).contains("+ 1");
        // 元詳細ID は「今の有効版」（基にした版が分かる）
        assertThat(sql).contains("'ACTIVE'");
    }

    @Test
    @DisplayName("11 の子テーブルを、人の行（APP か 手修正フラグ）だけ新しい版へ複製する")
    void copiesHumanRowsToNewVersion() {
        for (String[] table : COPIES) {
            String id = table[0];
            String sql = boundSql(id, parameters());
            assertThat(sql).as("%s", id).contains("INSERT INTO public.\"" + table[1] + "\"");
            // 新しい版へ移す（元の版の行はそのまま残す）
            assertThat(sql).as("%s の移し先", id).contains("\"詳細ID\"");
            // 人の行だけを複製する（AI の古い行は新しい内容で置き換える）
            assertThat(sql).as("%s の人の行の条件", id).contains("手修正フラグ");
            assertThat(sql).as("%s の人の行の条件", id).contains("'APP'");
            // 内容の出所と手修正フラグはそのまま引き継ぐ
            assertThat(sql).as("%s の出所", id).contains("登録元コード");
            assertThat(configuration.getMappedStatement(NAMESPACE + id).getSqlCommandType())
                    .isEqualTo(SqlCommandType.INSERT);
        }
    }

    @Test
    @DisplayName("会話行の複製は 会話ID を付け替える（旧版の会話 → 新版の同じ表示順の会話）")
    void remapsDialogIdsWhenCopyingLines() {
        String sql = boundSql("copyDetailDialogLines", parameters());

        assertThat(sql).contains("public.\"JPN_単語詳細_会話行情報\"");
        assertThat(sql).contains("JOIN public.\"JPN_単語詳細_会話情報\"");
        // 旧版の会話から、新しい版の同じ 表示順 の会話へ付け替える
        assertThat(sql).contains("表示順");
        // 段落ID は採番し直す（元の値を入れない）
        assertThat(sql).doesNotContain("SELECT l.\"会話行ID\"");
    }

    @Test
    @DisplayName("11 の子テーブルに AI の新しい行を入れる（表示順は複製した行の後ろに続ける）")
    void insertsNewChildRows() {
        for (String[] insert : INSERTS) {
            String sql = boundSql(insert[0], rowParameters(insert[0]));
            assertThat(sql).as("%s", insert[0]).contains("INSERT INTO public.\"" + insert[1] + "\"");
            assertThat(sql).as("%s は AI が作った行", insert[0]).contains("'BATCH'");
            // AI の表示順は複製した行の後ろ（人が入れた行を上書きしない）。
            // 値は Java 側（JapaneseWordAiDetailComposer）が「複製した行の最大 + 段落内の位置」で入れる
            assertThat(statement("insert", insert[0])).as("%s の表示順", insert[0]).contains("#{row.orderNo}");
            // 会話行だけは 会話ID で親にぶら下がる（詳細ID の列を持たない）
            if ("insertDetailDialogLine".equals(insert[0])) {
                assertThat(statement("insert", insert[0])).as("%s の親", insert[0]).contains("#{row.dialogId}");
            } else {
                assertThat(statement("insert", insert[0])).as("%s の詳細ID", insert[0]).contains("#{detailId}");
            }
            assertThat(configuration.getMappedStatement(NAMESPACE + insert[0]).getSqlCommandType())
                    .isEqualTo(SqlCommandType.INSERT);
        }
    }

    @Test
    @DisplayName("選択肢JSON はキャストして入れる（文字列の配列。NULL なら空配列）")
    void castsPracticeChoicesToJsonb() {
        assertThat(statement("insert", "insertDetailPractice"))
                .contains("CAST(COALESCE(#{row.choicesJson}, '[]') AS jsonb)");
    }

    @Test
    @DisplayName("人が入れた行の 表示順 の最大を引く（AI の表示順をその後ろから始めるため）")
    void countsKeptRows() {
        String sql = boundSql("maxKeptDetailOrderNo", parameters());

        assertThat(sql).contains("MAX(o.\"表示順\")");
        // 人の行だけを見る（AI の古い行は複製しない）
        assertThat(sql).contains("手修正フラグ");
        assertThat(sql).contains("'APP'");
        assertThat(sql).contains("\"詳細ID\" = ?");
    }

    @Test
    @DisplayName("11 の子テーブルすべてに複製と追加の口がある")
    void hasBothStatementsPerChildTable() {
        for (int index = 0; index < COPIES.length; index += 1) {
            assertThat(configuration.hasStatement(NAMESPACE + COPIES[index][0])).as(COPIES[index][0]).isTrue();
            assertThat(configuration.hasStatement(NAMESPACE + INSERTS[index][0])).as(INSERTS[index][0]).isTrue();
        }
    }
}

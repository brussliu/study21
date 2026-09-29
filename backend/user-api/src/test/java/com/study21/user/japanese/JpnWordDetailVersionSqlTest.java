package com.study21.user.japanese;

import com.study21.common.core.japanese.JpnWordDetailChildren;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 詳細の版を<b>書く</b> SQL（切片4）と、版の一覧・切り替えの契約。
 *
 * <p>確かめる接縫（実 DB は使わない。MyBatis に XML を読ませて SQL を固定する）:</p>
 * <ol>
 *   <li>新しい版は {@code 状態コード='ACTIVE'}、古い有効版は先に {@code ARCHIVED}</li>
 *   <li>内容版数は「その語の最大 + 1」を INSERT の中で数える。{@code 元詳細ID} は基にした版</li>
 *   <li>段落の行は 1 段落 1 文でまとめて入れる（行ごとに呼ばない）。{@code 表示順} は Java 側</li>
 *   <li>行の出所（{@code 登録元コード}）は呼ぶ側が決める。{@code 'APP'} の行だけ
 *       {@code 手修正フラグ=true}（人が触った行は AI に上書きされない）</li>
 *   <li>版の一覧は新しい順（{@code 内容版数} の降順）で、段落の行数を一緒に返す</li>
 *   <li>有効版の切り替えは {@code AND "バージョン" = ?} の楽観的ロック</li>
 * </ol>
 */
class JpnWordDetailVersionSqlTest {

    private static final String NAMESPACE = "com.study21.user.japanese.JpnWordDetailMapper.";

    private static final String XML = readXml();

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = JpnWordDetailVersionSqlTest.class
                .getResourceAsStream("/mapper/JpnWordDetailMapper.xml")) {
            assertThat(input).as("JpnWordDetailMapper.xml").isNotNull();
            new XMLMapperBuilder(input, configuration, "JpnWordDetailMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    private static String readXml() {
        try (InputStream stream = JpnWordDetailVersionSqlTest.class
                .getResourceAsStream("/mapper/JpnWordDetailMapper.xml")) {
            assertThat(stream).as("JpnWordDetailMapper.xml").isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception cause) {
            throw new IllegalStateException(cause);
        }
    }

    /** 文の SQL（MyBatis が組み立てた形。パラメータは ? になる。空白は 1 つにまとめる）。 */
    private static String boundSql(String id, Map<String, Object> parameters) {
        MappedStatement statement = configuration.getMappedStatement(NAMESPACE + id);
        return statement.getBoundSql(parameters).getSql()
                .replaceAll("\\s+", " ")
                .replaceAll("\\s+,", ",");
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

    /** 版の一覧・切り替えが読むパラメータ。 */
    private static Map<String, Object> parameters() {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("wordId", 101L);
        parameters.put("detailId", 901L);
        parameters.put("version", 3);
        parameters.put("accountId", 2L);
        parameters.put("originDetailId", 900L);
        parameters.put("generationId", null);
        parameters.put("aiProvider", "qwen");
        parameters.put("aiModel", "qwen3.7-plus");
        parameters.put("fetchedAt", java.sql.Timestamp.valueOf("2026-01-01 00:00:00"));
        parameters.put("manualCorrected", true);
        parameters.put("note", null);
        parameters.put("coreMeaning", "愛");
        parameters.put("descriptionJa", "説明。");
        parameters.put("descriptionZh", "说明。");
        parameters.put("partOfSpeech", "名詞");
        parameters.put("jlptLevel", "N3");
        parameters.put("conjugation", "なし");
        parameters.put("transitivity", "NONE");
        parameters.put("importance", 3);
        parameters.put("memoryHint", "ヒント");
        parameters.put("memoryHintBasis", "根拠");
        parameters.put("pronunciationJson", "{}");
        parameters.put("conjugationsJson", "[]");
        parameters.put("transitivityPairJson", null);
        parameters.put("structuredJson", "{}");
        parameters.put("createdBy", 2L);
        return parameters;
    }

    /** 段落の INSERT（{@code rows} を渡す）。 */
    private static Map<String, Object> rowParameters(String id, String sourceCode) {
        Map<String, Object> parameters = parameters();
        parameters.put("rows", List.of(rowOf(id, sourceCode)));
        return parameters;
    }

    /** INSERT が読む {@code row}（出所は行が持つ。どの段落かで型が変わる）。 */
    private static Object rowOf(String id, String sourceCode) {
        boolean manual = "APP".equals(sourceCode);
        return switch (id) {
            case "insertSenses" -> {
                JpnWordDetailChildren.Sense row = new JpnWordDetailChildren.Sense();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertExamples" -> {
                JpnWordDetailChildren.Example row = new JpnWordDetailChildren.Example();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertPatterns" -> {
                JpnWordDetailChildren.Pattern row = new JpnWordDetailChildren.Pattern();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertDialogs" -> {
                JpnWordDetailChildren.Dialog row = new JpnWordDetailChildren.Dialog();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertDialogLines" -> {
                JpnWordDetailChildren.DialogLine row = new JpnWordDetailChildren.DialogLine();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertSynonyms" -> {
                JpnWordDetailChildren.Synonym row = new JpnWordDetailChildren.Synonym();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertCautions" -> {
                JpnWordDetailChildren.Caution row = new JpnWordDetailChildren.Caution();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertCollocations" -> {
                JpnWordDetailChildren.Collocation row = new JpnWordDetailChildren.Collocation();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertRelatedWords" -> {
                JpnWordDetailChildren.RelatedWord row = new JpnWordDetailChildren.RelatedWord();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertUsageNotes" -> {
                JpnWordDetailChildren.UsageNote row = new JpnWordDetailChildren.UsageNote();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            case "insertPractices" -> {
                JpnWordDetailChildren.Practice row = new JpnWordDetailChildren.Practice();
                row.setSourceCode(sourceCode);
                row.setManualCorrected(manual);
                yield row;
            }
            default -> null;
        };
    }

    /** 11 の段落の INSERT。表と段落ID の対応。 */
    private static final String[][] INSERTS = {
            {"insertSenses", "JPN_単語詳細_語義情報"},
            {"insertExamples", "JPN_単語詳細_例文情報"},
            {"insertPatterns", "JPN_単語詳細_文型情報"},
            {"insertDialogs", "JPN_単語詳細_会話情報"},
            {"insertDialogLines", "JPN_単語詳細_会話行情報"},
            {"insertSynonyms", "JPN_単語詳細_類義語情報"},
            {"insertCautions", "JPN_単語詳細_注意情報"},
            {"insertCollocations", "JPN_単語詳細_コロケーション情報"},
            {"insertRelatedWords", "JPN_単語詳細_関連語情報"},
            {"insertUsageNotes", "JPN_単語詳細_使用場面情報"},
            {"insertPractices", "JPN_単語詳細_練習情報"},
    };

    @Test
    @DisplayName("画面の編集も新しい版を作る（1 語 1 行の上書きはしない）")
    void createsANewVersionForEditorSave() {
        assertThat(configuration.hasStatement(NAMESPACE + "insertDetailVersion")).isTrue();
        // 段落の内容を 1 つの JSONB に戻す（廃止した 詳細JSON）は復活させない
        assertThat(XML).doesNotContain("詳細JSON");
    }

    @Test
    @DisplayName("古い有効版は先に ARCHIVED にする（消さない＝履歴。1 語 1 版の ACTIVE を守る）")
    void archivesActiveVersionBeforeInserting() {
        String sql = boundSql("archiveActiveDetail", parameters());

        assertThat(sql).contains("UPDATE public.\"JPN_単語詳細情報\"");
        assertThat(sql).contains("SET \"状態コード\" = 'ARCHIVED'");
        assertThat(sql).contains("WHERE \"単語ID\" = ?");
        assertThat(sql).contains("AND \"状態コード\" = 'ACTIVE'");
        assertThat(configuration.getMappedStatement(NAMESPACE + "archiveActiveDetail").getSqlCommandType())
                .isEqualTo(SqlCommandType.UPDATE);
    }

    @Test
    @DisplayName("新しい版のヘッダは ACTIVE・元詳細ID・内容版数 = 最大 + 1。人の版なので生成ID は入れない")
    void insertsNewActiveHeader() {
        String sql = boundSql("insertDetailVersion", parameters());

        assertThat(sql).contains("INSERT INTO public.\"JPN_単語詳細情報\"");
        assertThat(sql).contains("'ACTIVE'");
        assertThat(sql).contains("MAX(d.\"内容版数\")");
        assertThat(sql).contains("+ 1");
        for (String column : new String[]{"単語ID", "内容版数", "状態コード", "元詳細ID", "生成ID",
                "AIプロバイダ", "AIモデル", "取得日時", "手修正フラグ", "備考",
                "核心意味", "説明_日本語", "説明_中国語", "品詞", "JLPTレベル", "活用型", "自他",
                "重要度", "記憶ヒント", "記憶ヒント根拠",
                "発音JSON", "活用形JSON", "自他対応JSON", "元レスポンスJSON"}) {
            assertThat(sql).as("列 %s", column).contains("\"" + column + "\"");
        }
        // 採番した 詳細ID を Java 側へ返す（段落の 詳細ID に使う）
        assertThat(configuration.getMappedStatement(NAMESPACE + "insertDetailVersion").getKeyGenerator())
                .isNotNull();
        assertThat(configuration.getMappedStatement(NAMESPACE + "insertDetailVersion").getSqlCommandType())
                .isEqualTo(SqlCommandType.INSERT);
    }

    @Test
    @DisplayName("段落の行は 11 すべてに INSERT があり、1 文でまとめて入れる（行ごとに呼ばない）")
    void insertsChildRowsInOneStatement() {
        for (String[] insert : INSERTS) {
            String id = insert[0];
            String sql = boundSql(id, rowParameters(id, "APP"));
            assertThat(sql).as("%s", id).contains("INSERT INTO public.\"" + insert[1] + "\"");
            assertThat(sql).as("%s はまとめて入れる", id).contains("VALUES");
            assertThat(statement("insert", id)).as("%s の繰り返し", id).contains("<foreach");
            assertThat(configuration.getMappedStatement(NAMESPACE + id).getSqlCommandType())
                    .isEqualTo(SqlCommandType.INSERT);
        }
    }

    @Test
    @DisplayName("行の出所は行が持つ値。'APP' の行だけ 手修正フラグ=true（AI の行は false）")
    void marksOnlyHumanRowsAsManuallyCorrected() {
        for (String[] insert : INSERTS) {
            String id = insert[0];
            String human = statement("insert", id);
            assertThat(human).as("%s の出所", id).contains("#{row.sourceCode}");
            // 手修正フラグは出所から決める（人が触った行だけ true）
            assertThat(human).as("%s の 手修正フラグ", id).contains("row.sourceCode == 'APP'");

            String boundHuman = boundSql(id, rowParameters(id, "APP"));
            assertThat(boundHuman).as("%s（人）", id).contains("true, NULL, ?");
            String boundAi = boundSql(id, rowParameters(id, "BATCH"));
            assertThat(boundAi).as("%s（AI）", id).contains("false, NULL, ?");
        }
    }

    @Test
    @DisplayName("表示順は Java 側が入れる（画面の並びを保存する）")
    void takesOrderNumbersFromJava() {
        for (String[] insert : INSERTS) {
            assertThat(statement("insert", insert[0])).as("%s の 表示順", insert[0])
                    .contains("#{row.orderNo}");
        }
    }

    @Test
    @DisplayName("会話の行は 会話ID で親にぶら下がる（詳細ID の列を持たない）")
    void insertsDialogLinesByDialogId() {
        String sql = statement("insert", "insertDialogLines");

        assertThat(sql).contains("public.\"JPN_単語詳細_会話行情報\"");
        assertThat(sql).contains("#{row.dialogId}");
        assertThat(sql).doesNotContain("#{detailId}, #{row.orderNo}");
    }

    @Test
    @DisplayName("練習の選択肢JSON はキャストして入れる（NULL なら空配列）")
    void castsPracticeChoicesToJsonb() {
        assertThat(statement("insert", "insertPractices"))
                .contains("CAST(COALESCE(#{row.choicesJson}, '[]') AS jsonb)");
    }

    @Test
    @DisplayName("版の一覧は新しい順（内容版数 の降順）で、段落の行数も一緒に返す")
    void listsVersionsNewestFirst() {
        String sql = boundSql("listDetailVersions", parameters());

        assertThat(sql).contains("FROM public.\"JPN_単語詳細情報\"");
        assertThat(sql).contains("d.\"単語ID\" = ?");
        assertThat(sql).contains("ORDER BY");
        assertThat(sql).contains("d.\"内容版数\" DESC");
        // 画面に「どの版が内容が多いか」を出すので、段落の行数を数える（N+1 にしない）
        for (String table : new String[]{"語義情報", "例文情報", "文型情報", "会話情報", "類義語情報",
                "注意情報", "コロケーション情報", "関連語情報", "使用場面情報", "練習情報"}) {
            assertThat(sql).as("段落 %s の行数", table)
                    .contains("public.\"JPN_単語詳細_" + table + "\"");
        }
        // 会話の発言は会話に含まれるので別に数えない
        assertThat(sql).doesNotContain("JPN_単語詳細_会話行情報");
    }

    @Test
    @DisplayName("人の行があるかは段落も見る（段落だけ直した版が「人の版」に見える）")
    void looksAtChildRowsForManualFlag() {
        String sql = boundSql("listDetailVersions", parameters());

        assertThat(sql).contains("手修正フラグ");
        assertThat(sql).contains("'APP'");
    }

    @Test
    @DisplayName("版 1 つは 単語ID と 詳細ID の両方で絞る（別の語の版を取れない）")
    void findsOneVersionWithinTheWord() {
        String sql = boundSql("findDetailVersion", parameters());

        assertThat(sql).contains("d.\"単語ID\" = ?");
        assertThat(sql).contains("d.\"詳細ID\" = ?");
    }

    @Test
    @DisplayName("有効版の切り替えは バージョン で楽観的ロックする")
    void activatesVersionWithOptimisticLock() {
        String sql = boundSql("activateDetailVersion", parameters());

        assertThat(sql).contains("UPDATE public.\"JPN_単語詳細情報\"");
        assertThat(sql).contains("SET \"状態コード\" = 'ACTIVE'");
        assertThat(sql).contains("WHERE \"単語ID\" = ?");
        assertThat(sql).contains("AND \"詳細ID\" = ?");
        assertThat(sql).contains("AND \"バージョン\" = ?");
        assertThat(sql).contains("\"バージョン\" = \"バージョン\" + 1");
        assertThat(configuration.getMappedStatement(NAMESPACE + "activateDetailVersion").getSqlCommandType())
                .isEqualTo(SqlCommandType.UPDATE);
    }
}

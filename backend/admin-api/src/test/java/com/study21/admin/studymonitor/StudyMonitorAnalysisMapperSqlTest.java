package com.study21.admin.studymonitor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 対象の選定と書き戻しの SQL（{@code StudyMonitorAnalysisMapper.xml}）。
 *
 * <p>確かめる接縫（実 DB は使わない。MyBatis に XML を読ませて SQL を固定する）:</p>
 * <ol>
 *   <li>対象は**スナップショットの完成状態**（状態='1' かつ 切出状態コード='CREATED'）で選び、
 *       時間のずらしで「batL02 が済んだ」と決めつけない</li>
 *   <li>分析の最新版（最新版フラグ='1'）がある行は選ばない＝**ERROR 行があっても再選定しない**</li>
 *   <li>古い順（撮影日時）に一度の分析枚数（{@code LIMIT}）だけ選ぶ</li>
 *   <li>ERROR 行を**削除しない**（2.0 は実行のたびに全 ERROR を DELETE していた）。
 *       そのため Mapper に削除系のメソッドが 1 つも無い</li>
 *   <li>書き戻しの列（最新版フラグ・二次判定なしの固定値・監査コード・jsonb）</li>
 * </ol>
 */
class StudyMonitorAnalysisMapperSqlTest {

    private static final String NAMESPACE = "com.study21.admin.studymonitor.StudyMonitorAnalysisMapper.";

    private static final String XML = readXml();

    private static Configuration configuration;

    /** MyBatis に XML を読ませる（文が揃っていること＝実際に実行できる形かどうかも確かめる）。 */
    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        try (InputStream input = StudyMonitorAnalysisMapperSqlTest.class
                .getResourceAsStream("/mapper/StudyMonitorAnalysisMapper.xml")) {
            assertThat(input).as("StudyMonitorAnalysisMapper.xml").isNotNull();
            new XMLMapperBuilder(input, configuration, "StudyMonitorAnalysisMapper.xml",
                    configuration.getSqlFragments()).parse();
        }
    }

    /** 文の SQL（MyBatis が組み立てた形。パラメータは ? になる）。 */
    private static String boundSql(String id) {
        return configuration.getMappedStatement(NAMESPACE + id).getBoundSql(null).getSql()
                .replaceAll("\\s+", " ");
    }

    private static String readXml() {
        try (InputStream stream = StudyMonitorAnalysisMapperSqlTest.class
                .getResourceAsStream("/mapper/StudyMonitorAnalysisMapper.xml")) {
            assertThat(stream).as("StudyMonitorAnalysisMapper.xml").isNotNull();
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

    /** コメント（<!-- ... -->）を落とす（説明文に同じ語が出ても誤検知しないように）。 */
    private static String withoutComments(String sql) {
        return sql.replaceAll("(?s)<!--.*?-->", "");
    }

    /** SQL に出てくる {@code #{row.xxx}} の xxx を集める。 */
    private static List<String> boundProperties(String sql) {
        return java.util.regex.Pattern.compile("#\\{row\\.([A-Za-z0-9_]+)}").matcher(sql).results()
                .map(match -> match.group(1))
                .distinct()
                .collect(Collectors.toList());
    }

    @Test
    @DisplayName("#{row.*} と結果の別名は record のプロパティとして読める（綴り違いを検出する）")
    void parameterPropertiesExist() {
        // MyBatis は record のアクセサを読む（3.5.17 の Reflector#addRecordGetMethods 経由）
        org.apache.ibatis.reflection.ReflectorFactory reflectors =
                new org.apache.ibatis.reflection.DefaultReflectorFactory();
        org.apache.ibatis.reflection.MetaClass completed = org.apache.ibatis.reflection.MetaClass
                .forClass(StudyMonitorAnalysisMapper.CompletedRow.class, reflectors);
        List<String> completedProperties =
                boundProperties(withoutComments(statement("insert", "insertCompleted")));
        assertThat(completedProperties).isNotEmpty();
        assertThat(completedProperties).allSatisfy(property ->
                assertThat(completed.hasGetter(property)).as("CompletedRow.%s", property).isTrue());

        org.apache.ibatis.reflection.MetaClass error = org.apache.ibatis.reflection.MetaClass
                .forClass(StudyMonitorAnalysisMapper.ErrorRow.class, reflectors);
        List<String> errorProperties = boundProperties(withoutComments(statement("insert", "upsertError")));
        assertThat(errorProperties).isNotEmpty();
        assertThat(errorProperties).allSatisfy(property ->
                assertThat(error.hasGetter(property)).as("ErrorRow.%s", property).isTrue());

        // 選定の結果は別名で record へ入る（エイリアスと成分名を揃える）
        org.apache.ibatis.reflection.MetaClass target = org.apache.ibatis.reflection.MetaClass
                .forClass(StudyMonitorAnalysisMapper.Target.class, reflectors);
        assertThat(List.of("snapshotId", "savePath", "capturedAt"))
                .allSatisfy(property -> assertThat(target.hasGetter(property)).as("Target.%s", property).isTrue());
        assertThat(withoutComments(statement("select", "findAnalysisTargets")))
                .contains("StudyMonitorAnalysisMapper$Target");
    }

    @Test
    @DisplayName("MyBatis が XML を読み、3 つの文が揃っている")
    void parsesAndDeclaresStatements() {
        assertThat(configuration.getMappedStatement(NAMESPACE + "findAnalysisTargets").getSqlCommandType())
                .isEqualTo(SqlCommandType.SELECT);
        assertThat(configuration.getMappedStatement(NAMESPACE + "insertCompleted").getSqlCommandType())
                .isEqualTo(SqlCommandType.INSERT);
        assertThat(configuration.getMappedStatement(NAMESPACE + "upsertError").getSqlCommandType())
                .isEqualTo(SqlCommandType.INSERT);

        // 文として組み立てた SQL（別名や改行の違いに依存しない確かめ）
        assertThat(boundSql("findAnalysisTargets")).contains("NOT EXISTS").contains("LIMIT ?");
        assertThat(boundSql("insertCompleted")).contains("'BAT_L03'").contains("'COMPLETED'").contains("::jsonb");
        assertThat(boundSql("upsertError")).contains("ON CONFLICT").contains("DO UPDATE");
    }

    @Test
    @DisplayName("対象は完成したスナップショットのうち分析の最新版が無いものだけ")
    void selectsOnlyUnanalysedCompletedSnapshots() {
        String sql = withoutComments(statement("select", "findAnalysisTargets"));

        assertThat(sql).contains("public.\"MON_学習モニタースナップショット情報\"");
        assertThat(sql).contains("S.\"状態\" = '1'");
        assertThat(sql).contains("S.\"切出状態コード\" = 'CREATED'");
        assertThat(sql).contains("NOT EXISTS");
        assertThat(sql).contains("public.\"MON_学習モニター画像分析情報\"");
        assertThat(sql).contains("A.\"スナップショットID\" = S.\"スナップショットID\"");
        // ERROR 行にも最新版フラグが立つので、この条件がそのまま「ERROR は再選定しない」になる
        assertThat(sql).contains("A.\"最新版フラグ\" = '1'");
        assertThat(sql).contains("ORDER BY S.\"撮影日時\"");
        assertThat(sql).contains("LIMIT #{limit}");
    }

    @Test
    @DisplayName("ERROR 行を削除しない（Mapper に削除系のメソッドが無い）")
    void neverDeletesErrorRows() {
        assertThat(XML).doesNotContain("<delete");
        assertThat(withoutComments(XML).toUpperCase()).doesNotContain("DELETE FROM");

        List<String> methods = Arrays.stream(StudyMonitorAnalysisMapper.class.getDeclaredMethods())
                .map(java.lang.reflect.Method::getName)
                .collect(Collectors.toList());
        assertThat(methods).containsExactlyInAnyOrder("findAnalysisTargets", "insertCompleted", "upsertError");
        assertThat(StudyMonitorAnalysisMapper.class.isAnnotationPresent(Mapper.class))
                .as("@Mapper（SqlLoggingInterceptor が記録する）")
                .isTrue();
    }

    @Test
    @DisplayName("分析結果は最新版フラグ・二次判定なしの固定値・監査コードつきで書く")
    void writesCompletedRowWithFixedValues() {
        String sql = withoutComments(statement("insert", "insertCompleted"));

        assertThat(sql).contains("\"最新版フラグ\"");
        assertThat(sql).contains("'1'");
        assertThat(sql).contains("'COMPLETED'");
        assertThat(sql).contains("\"二次判定要否\"").contains("#{row.secondRequired}");
        assertThat(sql).contains("\"二次分析状態コード\"").contains("#{row.secondStateCode}");
        assertThat(sql).contains("\"最終採用段階コード\"").contains("#{row.finalStageCode}");
        assertThat(sql).contains("\"最終分析結果コード\"").contains("\"最終信頼度\"").contains("\"最終判定理由\"");
        assertThat(sql).contains("\"一次応答JSON\"").contains("::jsonb");
        assertThat(sql).contains("\"撮影日時\"").contains("#{row.capturedAt}");
        // 監査: バッチが書くので アカウントID は NULL、登録元/更新元コードは BAT_L03
        assertThat(sql).contains("NULL, NULL, 'BAT_L03', 'BAT_L03'");
    }

    @Test
    @DisplayName("失敗した 1 枚は ERROR 行として残す（既にあれば更新する）")
    void writesErrorRowWithoutRetry() {
        String sql = withoutComments(statement("insert", "upsertError"));

        assertThat(sql).contains("'ERROR'");
        assertThat(sql).contains("\"一次エラー内容\"");
        assertThat(sql).contains("'BAT_L03'");
        assertThat(sql).contains("ON CONFLICT (\"スナップショットID\") WHERE \"最新版フラグ\" = '1'");
        assertThat(sql).contains("DO UPDATE");
        assertThat(sql).contains("\"MON_学習モニター画像分析情報\".\"バージョン\" + 1");
    }
}

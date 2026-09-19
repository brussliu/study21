package com.study21.admin.studymonitor;

import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.BoundSql;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code StudyMonitorImportMapper.xml} の SQL を固定するテスト。
 *
 * <p>実 DB は使わず、MyBatis が XML を読めること（文が揃っていること）と、
 * 2.0 から移植した要点（動画ID の採番・既取込の判定・監査の固定・重複時の DO NOTHING・
 * アカウントID を触らないカメラ upsert）が SQL に残っていることを確かめる。</p>
 */
class StudyMonitorImportMapperXmlTest {

    private static Configuration configuration;

    @BeforeAll
    static void parseMapper() throws Exception {
        configuration = new Configuration();
        Path mapper = Path.of("src/main/resources/mapper/StudyMonitorImportMapper.xml");
        assertThat(Files.exists(mapper)).as("Mapper XML の置き場").isTrue();
        try (InputStream input = Files.newInputStream(mapper)) {
            new XMLMapperBuilder(input, configuration, mapper.toString(), configuration.getSqlFragments()).parse();
        }
    }

    @Test
    void declaresAllStatements() {
        assertThat(statement("upsertCamera").getSqlCommandType()).isEqualTo(SqlCommandType.INSERT);
        assertThat(statement("findCameraId").getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(statement("existsVideo").getSqlCommandType()).isEqualTo(SqlCommandType.SELECT);
        assertThat(statement("insertVideo").getSqlCommandType()).isEqualTo(SqlCommandType.INSERT);
        assertThat(statement("insertSnapshots").getSqlCommandType()).isEqualTo(SqlCommandType.INSERT);
    }

    @Test
    void insertVideoReturnsGeneratedKey() throws Exception {
        MappedStatement statement = statement("insertVideo");

        // useGeneratedKeys + keyProperty が読めていること（2.0 の RETURNING "学習モニター動画ID" 相当）
        assertThat(statement.getKeyGenerator()).isInstanceOf(Jdbc3KeyGenerator.class);
        java.lang.reflect.Field field = MappedStatement.class.getDeclaredField("keyProperties");
        field.setAccessible(true);
        assertThat((String[]) field.get(statement)).as("採番した 動画ID を entity に返す").containsExactly("videoId");
        java.lang.reflect.Field columns = MappedStatement.class.getDeclaredField("keyColumns");
        columns.setAccessible(true);
        assertThat((String[]) columns.get(statement)).containsExactly("動画ID");
    }

    @Test
    void upsertCameraKeepsAccountColumnsUntouched() {
        String sql = sql("upsertCamera");

        assertThat(sql).contains("MON_学習モニターカメラ情報");
        // カメラコードで一意（1 台だけ）
        assertThat(sql).contains("ON CONFLICT (\"カメラコード\") DO UPDATE");
        // 2.1 の監査（登録元/更新元 = batL02。アカウントID はバッチなので指定しない）
        assertThat(sql).contains("'BAT_L02'");
        assertThat(sql).doesNotContain("登録者アカウントID");
        assertThat(sql).doesNotContain("更新者アカウントID");
        // 既存行の持ち主の情報を壊さない
        assertThat(sql).doesNotContain("\"アカウントID\" =");
        assertThat(sql).doesNotContain("\"旧ユーザーID\"");
        assertThat(sql).doesNotContain("旧ユーザーID");
    }

    @Test
    void insertVideoWritesStateAndRelativePath() {
        String sql = sql("insertVideo");

        assertThat(sql).contains("'IMPORTED'").contains("\"保存パス\"");
        assertThat(sql).contains("\"撮影日\"").contains("\"動画時間秒\"").contains("\"ファイルサイズ\"");
        assertThat(sql).contains("'BAT_L02'");
        assertThat(sql).doesNotContain("登録者アカウントID");
    }

    @Test
    void insertSnapshotsIgnoresDuplicates() {
        String sql = sqlForOneRow("insertSnapshots");

        assertThat(sql).contains("MON_学習モニタースナップショット情報");
        assertThat(sql).contains("'CREATED'").contains("'1'").contains("'BAT_L02'");
        // 2.0 と同じく「同じ動画の同じ位置は 1 枚だけ」
        assertThat(sql).contains("ON CONFLICT (\"動画ID\", \"動画内オフセットミリ秒\") DO NOTHING");
        assertThat(sql).contains("\"撮影日時\"").contains("\"画像ファイル名\"").contains("\"幅\"").contains("\"高さ\"");
        assertThat(sql).doesNotContain("登録者アカウントID");
    }

    @Test
    void existsVideoLooksUpByCameraAndFileName() {
        String sql = sql("existsVideo");

        assertThat(sql).contains("\"カメラID\" = ?").contains("\"動画ファイル名\" = ?");
        assertThat(sql).contains("MON_学習モニター動画情報");
    }

    @Test
    void findCameraIdLooksUpByCameraCode() {
        String sql = sql("findCameraId");

        assertThat(sql).contains("\"カメラID\"").contains("\"カメラコード\" = ?");
    }

    private static MappedStatement statement(String id) {
        return configuration.getMappedStatement(
                "com.study21.admin.studymonitor.StudyMonitorImportMapper." + id);
    }

    /**
     * 文の SQL（空白を 1 つに畳んだ形）。
     *
     * <p>{@code foreach} が使うコレクション（{@code list}）を空にして組み立てるので、
     * 実際に組み立てられること（XML が壊れていないこと）も同時に確かめられる。
     * バインド変数は {@code ?} になるため、確認するのは SQL の**形**だけ。</p>
     */
    private static String sql(String id) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("list", List.of());
        return normalize(statement(id).getBoundSql(parameters).getSql());
    }

    /** 1 行分の {@code VALUES} を組み立てた SQL（固定値と列の並びを見るため）。 */
    private static String sqlForOneRow(String id) {
        Map<String, Object> parameters = new HashMap<>();
        parameters.put("list", List.of(new StudyMonitorSnapshotEntity()));
        return normalize(statement(id).getBoundSql(parameters).getSql());
    }

    private static String normalize(String sql) {
        return sql.replaceAll("\\s+", " ").trim();
    }
}

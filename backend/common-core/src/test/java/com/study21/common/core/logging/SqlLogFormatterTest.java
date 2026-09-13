package com.study21.common.core.logging;

import java.util.List;
import java.util.Map;

import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB 操作ログの 1 行フォーマットのテスト。
 * 1 操作 = 1 行（改行を潰す）・パラメータの解決・切り詰めを確認する。
 */
class SqlLogFormatterTest {

    private final Configuration configuration = new Configuration();

    @Test
    void sqlIsFlattenedToOneLine() {
        String sql = """
                SELECT "サイトID",
                       "サイト名称"
                  FROM public."NET_サイト情報"
                 WHERE "ステータス" = ?
                """;

        String normalized = SqlLogFormatter.normalizeSql(sql);

        assertThat(normalized).doesNotContain("\n");
        assertThat(normalized).isEqualTo(
                "SELECT \"サイトID\", \"サイト名称\" FROM public.\"NET_サイト情報\" WHERE \"ステータス\" = ?");
    }

    @Test
    void longSqlIsTruncated() {
        String normalized = SqlLogFormatter.normalizeSql("SELECT " + "x".repeat(5000));

        assertThat(normalized).endsWith("...<truncated>");
        assertThat(normalized.length()).isEqualTo(SqlLogFormatter.MAX_SQL_LENGTH + "...<truncated>".length());
    }

    @Test
    void successLineContainsMapperSqlParametersDurationAndRows() {
        String line = SqlLogFormatter.success(
                "com.study21.user.account.AccountMapper.findByLoginId", "SELECT",
                "SELECT * FROM acc_account WHERE login_id = ?",
                List.of("loginId=foo@example.com"), 12, 1);

        assertThat(line).startsWith("op=SELECT mapper=com.study21.user.account.AccountMapper.findByLoginId");
        assertThat(line).contains("duration=12ms");
        assertThat(line).contains("rows=1");
        assertThat(line).contains("params=[loginId=foo@example.com]");
        assertThat(line).endsWith("sql=SELECT * FROM acc_account WHERE login_id = ?");
    }

    @Test
    void failureLineMarksStatusAndError() {
        String line = SqlLogFormatter.failure(
                "com.study21.user.account.AccountMapper.insert", "INSERT",
                "INSERT INTO acc_account (login_id) VALUES (?)",
                List.of("loginId=dup@example.com"), 5,
                new IllegalStateException("duplicate key value violates unique constraint"));

        assertThat(line).contains("status=FAILED");
        assertThat(line).contains("rows=-");
        assertThat(line).contains("error=java.lang.IllegalStateException: duplicate key value violates unique constraint");
    }

    @Test
    void parametersAreResolvedFromMapEntityAndSingleValue() {
        BoundSql boundSql = boundSql("SELECT 1 FROM t WHERE a = ? AND b = ?",
                List.of(mapping("a"), mapping("b")), Map.of("a", "A", "b", 2));

        assertThat(SqlLogFormatter.describeParameters(boundSql, Map.of("a", "A", "b", 2)))
                .containsExactly("a=A", "b=2");
    }

    @Test
    void entityParameterIsReadThroughGetter() {
        BoundSql boundSql = boundSql("SELECT 1 FROM t WHERE login_id = ?",
                List.of(mapping("loginId")), new SampleEntity("foo@example.com"));

        assertThat(SqlLogFormatter.describeParameters(boundSql, new SampleEntity("foo@example.com")))
                .containsExactly("loginId=foo@example.com");
    }

    @Test
    void singlePrimitiveParameterIsUsedAsValue() {
        BoundSql boundSql = boundSql("SELECT 1 FROM t WHERE id = ?", List.of(mapping("value")), 7L);

        assertThat(SqlLogFormatter.describeParameters(boundSql, 7L)).containsExactly("value=7");
    }

    @Test
    void nullAndBinaryAndCollectionValuesAreSummarized() {
        assertThat(SqlLogFormatter.describeValue(null)).isEqualTo("null");
        assertThat(SqlLogFormatter.describeValue("")).isEqualTo("''");
        assertThat(SqlLogFormatter.describeValue(new byte[] {1, 2, 3})).isEqualTo("<bytes:3>");
        assertThat(SqlLogFormatter.describeValue(List.of(1, 2))).isEqualTo("<collection:2>");
    }

    @Test
    void parameterValueWithNewlineIsFlattened() {
        assertThat(SqlLogFormatter.describeValue("line1\nline2")).isEqualTo("line1 line2");
    }

    private ParameterMapping mapping(String property) {
        return new ParameterMapping.Builder(configuration, property, String.class).build();
    }

    private BoundSql boundSql(String sql, List<ParameterMapping> mappings, Object parameterObject) {
        return new BoundSql(configuration, sql, mappings, parameterObject);
    }

    /** ゲッター経由で値を読む確認用のエンティティ。 */
    static class SampleEntity {
        private final String loginId;

        SampleEntity(String loginId) {
            this.loginId = loginId;
        }

        public String getLoginId() {
            return loginId;
        }
    }
}

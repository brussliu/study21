package com.study21.common.core.logging;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * SqlLoggingInterceptor のテスト。
 * すべての DB 操作（SELECT / INSERT / UPDATE / DELETE）が 1 行ずつ記録され、
 * 失敗時は FAILED として記録したうえで例外をそのまま投げ直すことを確認する。
 */
class SqlLoggingInterceptorTest {

    private final SqlLoggingInterceptor interceptor = new SqlLoggingInterceptor();
    private final Configuration configuration = new Configuration();
    private Logger sqlLogger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        sqlLogger = (Logger) LoggerFactory.getLogger(SqlLoggingInterceptor.SQL_LOGGER_NAME);
        appender = new ListAppender<>();
        appender.start();
        sqlLogger.addAppender(appender);
        sqlLogger.setLevel(Level.INFO);
    }

    @AfterEach
    void tearDown() {
        sqlLogger.detachAppender(appender);
    }

    @Test
    void selectIsLoggedWithRowCount() throws Throwable {
        MappedStatement statement = statement("com.study21.user.account.AccountMapper.findByLoginId",
                SqlCommandType.SELECT,
                boundSql("SELECT * FROM acc_account\n WHERE login_id = ?", "loginId"));
        Executor executor = mock(Executor.class);
        when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class), any()))
                .thenReturn(List.of("row1", "row2", "row3"));

        Object result = interceptor.intercept(queryInvocation(statement, executor, Map.of("loginId", "foo")));

        assertThat((List<?>) result).hasSize(3);
        assertThat(lines()).hasSize(1);
        assertThat(lines().get(0))
                .startsWith("op=SELECT mapper=com.study21.user.account.AccountMapper.findByLoginId")
                .contains("rows=3")
                .contains("params=[loginId=foo]")
                .contains("sql=SELECT * FROM acc_account WHERE login_id = ?");
    }

    @Test
    void insertIsLoggedWithAffectedRows() throws Throwable {
        MappedStatement statement = statement("com.study21.user.account.AccountMapper.insert",
                SqlCommandType.INSERT, boundSql("INSERT INTO acc_account (login_id) VALUES (?)", "loginId"));
        Executor executor = mock(Executor.class);
        when(executor.update(any(MappedStatement.class), any())).thenReturn(1);

        Object result = interceptor.intercept(updateInvocation(statement, executor, Map.of("loginId", "foo")));

        assertThat(result).isEqualTo(1);
        assertThat(lines()).hasSize(1);
        assertThat(lines().get(0)).startsWith("op=INSERT").contains("rows=1");
    }

    @Test
    void failureIsLoggedAndRethrown() throws Throwable {
        MappedStatement statement = statement("com.study21.user.account.AccountMapper.delete",
                SqlCommandType.DELETE, boundSql("DELETE FROM acc_account WHERE account_id = ?", "accountId"));
        Executor executor = mock(Executor.class);
        Invocation invocation = updateInvocation(statement, executor, Map.of("accountId", 9L));
        RuntimeException failure = new IllegalStateException("connection is closed");
        when(executor.update(any(MappedStatement.class), any())).thenThrow(failure);

        // MyBatis の Invocation.proceed() は InvocationTargetException で包む
        // （呼び出し側へは Plugin が従来どおり原因例外を展開して渡す）
        assertThatThrownBy(() -> interceptor.intercept(invocation))
                .hasRootCauseInstanceOf(IllegalStateException.class)
                .hasRootCauseMessage("connection is closed");

        assertThat(lines()).hasSize(1);
        assertThat(lines().get(0))
                .startsWith("op=DELETE")
                .contains("status=FAILED")
                .contains("error=java.lang.IllegalStateException: connection is closed")
                .contains("params=[accountId=9]");
    }

    @Test
    void queryWithCacheKeyAndBoundSqlSignatureIsLogged() throws Throwable {
        MappedStatement statement = statement("com.study21.admin.setting.SettingValueMapper.selectAll",
                SqlCommandType.SELECT, boundSql("SELECT 1", null));
        Executor executor = mock(Executor.class);
        when(executor.query(any(MappedStatement.class), any(), any(RowBounds.class), any(), any(), any()))
                .thenReturn(List.of());

        Method method = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class, org.apache.ibatis.cache.CacheKey.class, BoundSql.class);
        Invocation invocation = new Invocation(executor, method,
                new Object[] {statement, null, RowBounds.DEFAULT, null, null, statement.getBoundSql(null)});

        interceptor.intercept(invocation);

        assertThat(lines()).hasSize(1);
        assertThat(lines().get(0)).contains("rows=0").contains("params=[]");
    }

    private List<String> lines() {
        return appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
    }

    private MappedStatement statement(String id, SqlCommandType commandType, BoundSql boundSql) {
        MappedStatement statement = mock(MappedStatement.class);
        when(statement.getId()).thenReturn(id);
        when(statement.getSqlCommandType()).thenReturn(commandType);
        when(statement.getBoundSql(any())).thenReturn(boundSql);
        return statement;
    }

    private BoundSql boundSql(String sql, String property) {
        List<ParameterMapping> mappings = property == null
                ? List.of()
                : List.of(new ParameterMapping.Builder(configuration, property, String.class).build());
        return new BoundSql(configuration, sql, mappings, null);
    }

    private Invocation queryInvocation(MappedStatement statement, Executor executor, Object parameter)
            throws NoSuchMethodException {
        Method method = Executor.class.getMethod("query", MappedStatement.class, Object.class,
                RowBounds.class, ResultHandler.class);
        return new Invocation(executor, method, new Object[] {statement, parameter, RowBounds.DEFAULT, null});
    }

    private Invocation updateInvocation(MappedStatement statement, Executor executor, Object parameter)
            throws NoSuchMethodException {
        Method method = Executor.class.getMethod("update", MappedStatement.class, Object.class);
        return new Invocation(executor, method, new Object[] {statement, parameter});
    }
}

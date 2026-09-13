package com.study21.common.core.logging;

import java.util.List;
import java.util.Properties;

import org.apache.ibatis.cache.CacheKey;
import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.BoundSql;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Plugin;
import org.apache.ibatis.plugin.Signature;
import org.apache.ibatis.reflection.ExceptionUtil;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * すべての DB 操作を専用ログ（logs/backend/&lt;service&gt;-sql.log）に記録する MyBatis インターセプタ。
 *
 * <p>MyBatis の Executor（query / update）を横取りするので、<b>Mapper を追加するだけで自動的に
 * 記録される</b>。リポジトリ層で手書きのログを書く必要はない。</p>
 *
 * <p>記録内容：実行時刻 / traceId（MDC）/ 種別（SELECT・INSERT・UPDATE・DELETE）/ Mapper メソッド /
 * 実行時間 / 取得行数または更新件数 / バインドパラメータ / SQL 文。
 * 失敗時は {@code status=FAILED error=...} を同じファイルに残し、例外のスタックトレースは
 * error.log（ERROR 以上）に残す。</p>
 *
 * <p>この Bean は common-core にあり、admin-api / user-api のコンポーネントスキャン
 * （{@code scanBasePackages = "com.study21"}）で登録される。MyBatis の自動設定が
 * {@code org.apache.ibatis.plugin.Interceptor} Bean を自動で組み込む。</p>
 */
@Intercepts({
        @Signature(type = Executor.class, method = "update",
                args = {MappedStatement.class, Object.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class}),
        @Signature(type = Executor.class, method = "query",
                args = {MappedStatement.class, Object.class, RowBounds.class, ResultHandler.class,
                        CacheKey.class, BoundSql.class})
})
@Component
public class SqlLoggingInterceptor implements Interceptor {

    /** SQL ログ専用のロガー名（logback-spring.xml で sql.log にだけ出力する）。 */
    public static final String SQL_LOGGER_NAME = "STUDY21_SQL";

    private static final Logger sqlLog = LoggerFactory.getLogger(SQL_LOGGER_NAME);
    /** DB 操作失敗時に error.log 側へスタックトレースを残すためのロガー。 */
    private static final Logger errorLog = LoggerFactory.getLogger(SqlLoggingInterceptor.class);

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        MappedStatement statement = (MappedStatement) invocation.getArgs()[0];
        Object parameter = invocation.getArgs().length > 1 ? invocation.getArgs()[1] : null;

        String mapper = statement.getId();
        String commandType = String.valueOf(statement.getSqlCommandType());
        BoundSql boundSql = resolveBoundSql(invocation, statement, parameter);
        String sql = SqlLogFormatter.normalizeSql(boundSql.getSql());
        List<String> parameters = SqlLogFormatter.describeParameters(boundSql, parameter);

        long startedAt = System.nanoTime();
        try {
            Object result = invocation.proceed();
            if (sqlLog.isInfoEnabled()) {
                sqlLog.info(SqlLogFormatter.success(mapper, commandType, sql, parameters,
                        elapsedMillis(startedAt), rowCount(result)));
            }
            return result;
        } catch (Throwable error) {
            // Invocation.proceed() は InvocationTargetException で包むため、実際の原因を記録する
            // （呼び出し側へ投げ直す例外は MyBatis の Plugin が従来どおり展開する）。
            String line = SqlLogFormatter.failure(mapper, commandType, sql, parameters,
                    elapsedMillis(startedAt), ExceptionUtil.unwrapThrowable(error));
            sqlLog.error(line);
            errorLog.error("DB 操作に失敗しました。 {}", line, error);
            throw error;
        }
    }

    @Override
    public Object plugin(Object target) {
        return Plugin.wrap(target, this);
    }

    @Override
    public void setProperties(Properties properties) {
        // 設定項目なし
    }

    private static BoundSql resolveBoundSql(Invocation invocation, MappedStatement statement, Object parameter) {
        Object[] args = invocation.getArgs();
        if (args.length >= 6 && args[5] instanceof BoundSql boundSql) {
            return boundSql;
        }
        return statement.getBoundSql(parameter);
    }

    private static long elapsedMillis(long startedAtNanos) {
        return (System.nanoTime() - startedAtNanos) / 1_000_000L;
    }

    /** SELECT は取得件数、INSERT / UPDATE / DELETE は更新件数を返す。 */
    static int rowCount(Object result) {
        if (result == null) {
            return 0;
        }
        if (result instanceof Integer count) {
            return count;
        }
        if (result instanceof Number number) {
            return number.intValue();
        }
        if (result instanceof List<?> list) {
            return list.size();
        }
        if (result instanceof java.util.Collection<?> collection) {
            return collection.size();
        }
        if (result instanceof Object[] array) {
            return array.length;
        }
        return 1;
    }
}

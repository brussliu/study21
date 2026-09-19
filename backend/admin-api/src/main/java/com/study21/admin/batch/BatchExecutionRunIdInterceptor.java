package com.study21.admin.batch;

import org.apache.ibatis.executor.Executor;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * 実行記録の挿入に**起動識別子**（{@link ProcessRunId}）を自動で刻む MyBatis インターセプタ。
 *
 * <p>なぜインターセプタか: 実行記録を作る入口は「30 秒スケジューラの確保」「起動時バッチ」
 * 「画面の【再実行】」「復旧のやり直し」の 4 つあり、これから増える可能性もある。
 * 入口ごとに値を設定する書き方だと、**新しい入口を足したときに刻み忘れる**と
 * 復旧が自分の実行を遺留と誤認して二重実行する。挿入の直前に一括で入れておけば、
 * 入口が増えても漏れない（{@code BatchExecutionMapper} の挿入 2 文だけを対象にする）。</p>
 *
 * <p>SQL を書き換えず、パラメータ（{@link BatchExecutionEntity}）に値を入れるだけなので、
 * 既存の SQL ログ（{@code SqlLoggingInterceptor}）にもそのまま記録される。</p>
 */
@Component
@Intercepts(@Signature(type = Executor.class, method = "update",
        args = {MappedStatement.class, Object.class}))
public class BatchExecutionRunIdInterceptor implements Interceptor {

    /** 実行記録を作る文（この 2 つだけが対象）。 */
    private static final Set<String> INSERT_STATEMENTS = Set.of(
            "com.study21.admin.batch.BatchExecutionMapper.insert",
            "com.study21.admin.batch.BatchExecutionMapper.insertRetryIfAbsent");

    private static final Logger log = LoggerFactory.getLogger(BatchExecutionRunIdInterceptor.class);

    private final ProcessRunId processRunId;

    public BatchExecutionRunIdInterceptor(ProcessRunId processRunId) {
        this.processRunId = processRunId;
    }

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        Object[] args = invocation.getArgs();
        if (args.length >= 2 && args[0] instanceof MappedStatement statement
                && INSERT_STATEMENTS.contains(statement.getId())
                && args[1] instanceof BatchExecutionEntity entity) {
            if (entity.getRunId() == null || entity.getRunId().isBlank()) {
                entity.setRunId(processRunId.value());
            } else if (!processRunId.value().equals(entity.getRunId())) {
                // 呼び出し側が別の値を入れている（テストの再現など）→ そのまま使うが、気づけるように残す
                log.debug("実行記録に呼び出し側の起動識別子を使います。executionRunId={} current={}",
                        entity.getRunId(), processRunId.value());
            }
        }
        return invocation.proceed();
    }
}

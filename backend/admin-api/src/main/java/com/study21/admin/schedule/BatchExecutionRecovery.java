package com.study21.admin.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.study21.admin.batch.BatchExecutionMapper;

/**
 * サービス再起動で**未完了のまま残った実行**を復旧する。
 *
 * <p>落ちた実行（QUEUED / RUNNING）を残したままだと、同じタスクの次の実行が
 * 「前回がまだ実行中」と見なされて永久に走らない。起動時に 1 回、失敗として閉じる
 * （業務は再開しない＝同じ計画実行点を二重に実行しない）。</p>
 *
 * <p>順序は {@link Ordered#HIGHEST_PRECEDENCE}。既存の {@code BatchStartupRunner}
 * （起動時バッチ）より**先**に走らせて、起動時バッチの新しい実行を誤って失敗にしないようにする。</p>
 *
 * <p>前提: 第一版は admin-api が 1 インスタンスで動く（多重起動する構成にしたときは、
 * 他インスタンスが実行中の行を閉じてしまわないよう、この復旧の条件を見直すこと）。</p>
 */
@Component
public class BatchExecutionRecovery {

    /** 再起動で閉じた実行に残すメッセージ。 */
    static final String RECOVERY_MESSAGE = "サービス再起動のため中断しました（自動では再開しません）。";

    private static final Logger log = LoggerFactory.getLogger(BatchExecutionRecovery.class);

    private final BatchExecutionMapper executionMapper;

    public BatchExecutionRecovery(BatchExecutionMapper executionMapper) {
        this.executionMapper = executionMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void recoverInterruptedExecutions() {
        try {
            int recovered = executionMapper.markUnfinishedAsFailed(RECOVERY_MESSAGE);
            if (recovered > 0) {
                log.warn("サービス再起動で中断した実行を復旧しました（失敗として閉じました）。count={}", recovered);
            }
        } catch (RuntimeException cause) {
            log.warn("再起動で中断した実行の復旧に失敗しました。reason={}", cause.getMessage());
        }
    }
}

package com.study21.admin.schedule;

import com.study21.admin.batch.BatchControlEntity;
import com.study21.admin.batch.BatchControlMapper;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import com.study21.admin.batch.BatchExecutionStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * サービス再起動で**未完了のまま残った実行**を復旧する。
 *
 * <p>落ちた実行（待機中・実行中）を残したままだと、同じタスクの次の実行が
 * 「前回がまだ実行中」と見なされて永久に走らない。起動時に 1 回、次のように扱う:</p>
 *
 * <ul>
 *   <li><b>待機中（まだ始まっていない）</b>: そのまま実行し直す。計画実行点は確保済みで、
 *       業務（端末モードの一括切替・動画取込・スナップショット分析）は**何度実行しても同じ結果**に
 *       なるように作ってある（同じ動画は取込済み判定で飛ばし、分析は最新版フラグで二重に作らない）。
 *       「実行されないまま永久にスキップ」を作らない。</li>
 *   <li><b>実行中（結果が分からない）</b>: まず失敗として閉じる（どこまで進んだか分からないため）。
 *       そのうえで、**その計画実行点がまだ「いまの計画」で、タスクが有効**なときだけ 1 回やり直す。
 *       新しい計画実行点に追い越されていればやり直さない（古い状態で新しい状態を上書きしない）。</li>
 *   <li>スケジューラが管理しないタスク（起動時バッチなど）は今までどおり失敗として閉じるだけ
 *       （それぞれの入口が別にあり、ここで勝手に走らせない）。</li>
 * </ul>
 *
 * <p>順序は {@link Ordered#HIGHEST_PRECEDENCE}。既存の {@code BatchStartupRunner}
 * （起動時バッチ）より**先**に走らせて、起動時バッチの新しい実行を誤って失敗にしないようにする。</p>
 *
 * <p>前提: 第一版は admin-api が 1 インスタンスで動く（多重起動する構成にしたときは、
 * 他インスタンスが実行中の行を閉じてしまわないよう、この復旧の条件を見直すこと）。</p>
 */
@Component
public class BatchExecutionRecovery {

    /** 再起動で閉じた実行に残すメッセージ（結果が分からない実行）。 */
    static final String RECOVERY_MESSAGE = "サービス再起動のため中断しました（結果は不明です）。";

    /** 再起動で閉じた実行に残すメッセージ（まだ始まっていない実行を無効化したとき）。 */
    static final String DISABLED_MESSAGE = "サービス再起動時に無効だったため実行しませんでした。";

    /** やり直しの実行記録に残す依頼元コード。 */
    static final String RETRY_CODE = "RECOVERY";

    private static final DateTimeFormatter PAYLOAD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final Logger log = LoggerFactory.getLogger(BatchExecutionRecovery.class);

    private final BatchExecutionMapper executionMapper;
    private final BatchControlMapper controlMapper;
    private final SchedulePlanMapper planMapper;
    private final ScheduledTriggerStore triggerStore;
    private final BatchScheduleExecutor executor;
    private final ScheduleRuleCatalog catalog;

    public BatchExecutionRecovery(BatchExecutionMapper executionMapper,
                                  BatchControlMapper controlMapper,
                                  SchedulePlanMapper planMapper,
                                  ScheduledTriggerStore triggerStore,
                                  BatchScheduleExecutor executor,
                                  ScheduleRuleCatalog catalog) {
        this.executionMapper = executionMapper;
        this.controlMapper = controlMapper;
        this.planMapper = planMapper;
        this.triggerStore = triggerStore;
        this.executor = executor;
        this.catalog = catalog;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(Ordered.HIGHEST_PRECEDENCE)
    public void recoverInterruptedExecutions() {
        List<BatchExecutionEntity> unfinished;
        try {
            unfinished = executionMapper.findUnfinished();
        } catch (RuntimeException cause) {
            log.warn("再起動で中断した実行の復旧に失敗しました（一覧を読めません）。reason={}", cause.getMessage());
            return;
        }
        if (unfinished.isEmpty()) {
            return;
        }
        int closed = 0;
        int retried = 0;
        for (BatchExecutionEntity row : unfinished) {
            // 1 件の復旧で例外が出ても、起動を止めたり残りの行の復旧を止めたりしない
            try {
                if (catalog.taskCodes().contains(row.getBatchCode())) {
                    if (recoverScheduledTask(row)) {
                        retried++;
                    } else {
                        closed++;
                    }
                } else {
                    // スケジューラが管理しないタスクは今までどおり閉じるだけ
                    close(row, RECOVERY_MESSAGE, BatchExecutionStatus.FAILED);
                    closed++;
                }
            } catch (RuntimeException cause) {
                log.warn("中断した実行の復旧に失敗しました（この実行は次の起動で再試行します）。"
                        + "taskCode={} executionId={} reason={}",
                        row.getBatchCode(), row.getExecutionId(), cause.getMessage());
            }
        }
        log.warn("サービス再起動で中断した実行を復旧しました。closed={} retried={}", closed, retried);
    }

    /** @return 実行し直した（またはやり直しを投入した）ら true */
    private boolean recoverScheduledTask(BatchExecutionEntity row) {
        String taskCode = row.getBatchCode();
        if (BatchExecutionStatus.QUEUED.name().equals(row.getStatus())) {
            if (!isEnabled(taskCode)) {
                close(row, DISABLED_MESSAGE, BatchExecutionStatus.SKIPPED);
                return false;
            }
            // まだ始まっていない → そのまま実行し直す（実行記録は残っているものをそのまま使う）
            log.warn("再起動前に確保していた計画実行点を実行し直します。taskCode={} executionId={} 予定={}",
                    taskCode, row.getExecutionId(), row.getScheduleTime());
            executor.submit(taskCode, row.getExecutionId());
            return true;
        }

        // 実行中 → 結果が分からないので失敗として閉じ、必要なら 1 回だけやり直す
        close(row, RECOVERY_MESSAGE, BatchExecutionStatus.FAILED);
        LocalDateTime plannedAt = parse(row.getScheduleTime());
        if (plannedAt == null || !isStillCurrentPlan(taskCode, plannedAt) || !isEnabled(taskCode)) {
            return false;
        }
        Long retryId = triggerStore.insertRetryRow(taskCode, plannedAt,
                row.getTriggerType() == null ? "C" : row.getTriggerType(), RETRY_CODE);
        if (retryId == null) {
            return false;
        }
        executor.submit(taskCode, retryId);
        return true;
    }

    /**
     * その計画実行点が**いまの計画**か（計画表の 最終予定日時 と一致するか）。
     * 新しい点に追い越されていれば、古い点をやり直さない（古い状態で新しい状態を上書きしない）。
     */
    private boolean isStillCurrentPlan(String taskCode, LocalDateTime plannedAt) {
        try {
            Map<String, Object> plan = planMapper.findPlan(taskCode);
            Object current = plan == null ? null : plan.get("lastPlannedAt");
            if (current instanceof java.sql.Timestamp timestamp) {
                return timestamp.toLocalDateTime().isEqual(plannedAt);
            }
            return false;
        } catch (RuntimeException cause) {
            log.warn("計画状態を読めなかったため、やり直しは行いません。taskCode={} reason={}",
                    taskCode, cause.getMessage());
            return false;
        }
    }

    private boolean isEnabled(String taskCode) {
        BatchControlEntity control = controlMapper.findByBatchCode(taskCode);
        return control != null && control.isActive();
    }

    private void close(BatchExecutionEntity row, String message, BatchExecutionStatus status) {
        executionMapper.markFinished(row.getExecutionId(), status.name(), message, message, 0L);
    }

    /** 実行記録の 予定時刻（ISO 形式。空白区切りで入っている行も読めるようにする）。 */
    private static LocalDateTime parse(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value.trim(), PAYLOAD_FORMAT);
        } catch (RuntimeException cause) {
            try {
                return LocalDateTime.parse(value.trim().replace(' ', 'T'));
            } catch (RuntimeException ignored) {
                return null;
            }
        }
    }
}

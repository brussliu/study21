package com.study21.admin.schedule;

import com.study21.admin.batch.BatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * スケジューラが確保した実行を**バックグラウンドで**走らせる実行器。
 *
 * <p>スケジュール検査のスレッドを業務（ffmpeg・AI 呼び出し・端末切替）で塞がないためのもの。
 * 業務は 1 本ずつ順に走らせる（ffmpeg と AI が同時に走るとマシンと API が飽和するため、
 * 既定の並列数は 1。設定で変えられる）。</p>
 *
 * <p><b>実行の直前に計画をもう一度見る</b>（{@link SchedulePlanGuard}）。待ち行列で待っている間に
 * 利用者が実行設定を変えた／タスクを無効にした／計画が古くなった場合、そのまま実行すると
 * 「もう効いていない設定」で動いてしまう。実行しないと判定したら**理由を残してスキップ**する
 * （待機中のまま残さない。残すと次の計画実行点が確保できない）。</p>
 *
 * <p>キューがあふれたときは**確保を捨てずに記録を失敗として残す**（実行されないまま
 * 未完了で残ると、次の検査が同じ計画実行点を確保できないため）。</p>
 */
@Component
public class BatchScheduleExecutor {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduleExecutor.class);

    private static final AtomicLong THREAD_SEQUENCE = new AtomicLong();

    private final BatchService batchService;
    private final SchedulePlanGuard planGuard;
    private final Clock clock;
    private final ThreadPoolExecutor executor;

    @org.springframework.beans.factory.annotation.Autowired
    public BatchScheduleExecutor(BatchService batchService,
                                 SchedulePlanGuard planGuard,
                                 @org.springframework.beans.factory.annotation.Value(
                                         "${study21.batch.schedule.workers:1}") int workers) {
        this(batchService, planGuard, workers, Clock.system(ScheduleConfigService.ZONE));
    }

    /** テスト用（時計を差し替える）。 */
    public BatchScheduleExecutor(BatchService batchService, SchedulePlanGuard planGuard, int workers, Clock clock) {
        this.batchService = batchService;
        this.planGuard = planGuard;
        this.clock = clock;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "batch-schedule-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        int poolSize = Math.max(1, workers);
        this.executor = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(16), factory,
                new ThreadPoolExecutor.AbortPolicy());
    }

    /**
     * 実行を投入する（スケジュール検査のスレッドは待たない）。
     *
     * @param taskCode    バッチコード
     * @param executionId 確保済みの実行記録の ID
     * @param plannedAt   計画実行点（実行直前の再検証に使う。分からないときは null）
     */
    public void submit(String taskCode, long executionId, LocalDateTime plannedAt) {
        try {
            executor.execute(() -> {
                try {
                    runVerified(taskCode, executionId, plannedAt);
                } catch (RuntimeException cause) {
                    log.error("スケジュール実行で想定外の例外が出ました。taskCode={} executionId={}",
                            taskCode, executionId, cause);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException cause) {
            log.error("スケジュール実行の待ち行列があふれました。taskCode={} executionId={}", taskCode, executionId);
            batchService.markQueuedAsFailed(executionId, "実行待ちの行列があふれたため実行しませんでした。");
        }
    }

    /**
     * 実行の直前にもう一度計画を見てから走らせる。
     *
     * <p>確保した時点の設定ではなく、**いま効いている設定**で判断する
     * （待機中に設定が変わった／計画が古くなった実行を走らせない）。</p>
     *
     * @return 実行を投入したら true（計画が無効でスキップしたら false）
     */
    public boolean runVerified(String taskCode, long executionId, LocalDateTime plannedAt) {
        if (plannedAt == null) {
            batchService.runQueued(executionId);
            return true;
        }
        SchedulePlanGuard.PlanDecision decision =
                planGuard.decide(taskCode, plannedAt, Instant.now(clock), SchedulePlanGuard.Stage.BEFORE_RUN);
        if (decision.allowed()) {
            batchService.runQueued(executionId);
            return true;
        }
        String prefix = decision.deferred()
                ? "実行の直前に計画を判定できなかったため実行しませんでした。"   // 設定を読めていない等
                : "実行の直前に計画が有効でなくなったため実行しませんでした。";
        String message = prefix + decision.reason();
        log.warn("実行直前に計画が無効になったため実行しません。taskCode={} executionId={} 予定={} "
                + "reason={} configVersion={}", taskCode, executionId, plannedAt,
                decision.reason(), decision.configVersion());
        batchService.markQueuedAsSkipped(executionId, message);
        return false;
    }

    /** 実行中の本数（監視用）。 */
    public int activeCount() {
        return executor.getActiveCount();
    }

    /** 待ち行列の本数（監視用）。 */
    public int queuedCount() {
        return executor.getQueue().size();
    }

    @PreDestroy
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        }
    }
}

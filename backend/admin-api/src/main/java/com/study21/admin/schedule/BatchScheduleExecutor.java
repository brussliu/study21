package com.study21.admin.schedule;

import com.study21.admin.batch.BatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
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
 * （待機中のまま残さない。残すと次の計画実行点が確保できない）。
 * このとき使う設定は**そのとき最新の 1 枚**（待機中に変わっていれば新しい設定で判断する）。</p>
 *
 * <p><b>待ち行列があふれたときは記録を閉じない。</b>次の検査（{@link #retryPendingSubmissions()}）で
 * 入り直しを試し、それでも入らなければ失敗として閉じる。即失敗にすると
 * 「確保した計画実行点が実行されないまま消える」＝漏執行になる。入り直しを待っている間に
 * サービスが落ちても、記録は**待機中のまま残る**ので次の起動の復旧が拾える。</p>
 */
@Component
public class BatchScheduleExecutor {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduleExecutor.class);

    private static final AtomicLong THREAD_SEQUENCE = new AtomicLong();

    /** 待ち行列があふれたときの入り直しの上限（これを超えたら失敗として閉じる）。 */
    private static final int MAX_SUBMIT_ATTEMPTS = 20;

    /** 実行待ち行列の既定の長さ（テストでは短くできる）。 */
    static final int DEFAULT_QUEUE_CAPACITY = 16;

    private final BatchService batchService;
    private final SchedulePlanGuard planGuard;
    private final ScheduleConfigService configService;
    private final Clock clock;
    private final ThreadPoolExecutor executor;

    /** あふれて入らなかった実行（次の検査で入り直す）。キーは実行ID。 */
    private final Map<Long, PendingSubmission> pendingSubmissions = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public BatchScheduleExecutor(BatchService batchService,
                                 SchedulePlanGuard planGuard,
                                 ScheduleConfigService configService,
                                 @org.springframework.beans.factory.annotation.Value(
                                         "${study21.batch.schedule.workers:1}") int workers) {
        this(batchService, planGuard, configService, workers, DEFAULT_QUEUE_CAPACITY,
                Clock.system(ScheduleConfigService.ZONE));
    }

    /** テスト用（時計と待ち行列の長さを差し替える）。 */
    public BatchScheduleExecutor(BatchService batchService, SchedulePlanGuard planGuard,
                                 ScheduleConfigService configService, int workers, int queueCapacity, Clock clock) {
        this.batchService = batchService;
        this.planGuard = planGuard;
        this.configService = configService;
        this.clock = clock;
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "batch-schedule-" + THREAD_SEQUENCE.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        int poolSize = Math.max(1, workers);
        this.executor = new ThreadPoolExecutor(poolSize, poolSize, 60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(Math.max(1, queueCapacity)), factory,
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
        if (enqueue(taskCode, executionId, plannedAt)) {
            return;
        }
        // あふれた: **記録は閉じない**。次の検査で入り直す（入り直せなければ失敗として閉じる）
        PendingSubmission previous = pendingSubmissions.get(executionId);
        int attempts = previous == null ? 1 : previous.attempts() + 1;
        pendingSubmissions.put(executionId, new PendingSubmission(taskCode, plannedAt, attempts));
        log.warn("実行の待ち行列があふれたため、次の検査で入り直します。taskCode={} executionId={} attempts={}",
                taskCode, executionId, attempts);
    }

    /**
     * あふれて入らなかった実行を入り直す（30 秒の検査から呼ぶ）。
     *
     * <p>上限まで試して入らなければ失敗として閉じる（待機中のまま残すと、次の計画実行点が
     * 確保できなくなる）。入り直しを待っている間に落ちた場合は、記録が待機中のまま残るので
     * 次の起動の復旧が拾う。</p>
     *
     * @return 入り直せた件数
     */
    public int retryPendingSubmissions() {
        if (pendingSubmissions.isEmpty()) {
            return 0;   // 通常はここで終わる（DB も引かない）
        }
        int submitted = 0;
        List<Long> giveUp = new ArrayList<>();
        for (Map.Entry<Long, PendingSubmission> entry : pendingSubmissions.entrySet()) {
            long executionId = entry.getKey();
            PendingSubmission submission = entry.getValue();
            if (submission.attempts() >= MAX_SUBMIT_ATTEMPTS) {
                giveUp.add(executionId);
                continue;
            }
            if (enqueue(submission.taskCode(), executionId, submission.plannedAt())) {
                pendingSubmissions.remove(executionId);
                submitted++;
            } else {
                pendingSubmissions.put(executionId,
                        new PendingSubmission(submission.taskCode(), submission.plannedAt(),
                                submission.attempts() + 1));
            }
        }
        for (Long executionId : giveUp) {
            PendingSubmission submission = pendingSubmissions.remove(executionId);
            log.error("実行の待ち行列があふれたまま入り直せませんでした。taskCode={} executionId={}",
                    submission == null ? null : submission.taskCode(), executionId);
            batchService.markQueuedAsFailed(executionId, "実行待ちの行列があふれたため実行できませんでした。");
        }
        return submitted;
    }

    /** 入り直しを待っている実行の数（監視・画面用）。 */
    public int pendingSubmissionCount() {
        return pendingSubmissions.size();
    }

    /** 1 回だけ投入を試す。あふれたら false（例外は投げない）。 */
    private boolean enqueue(String taskCode, long executionId, LocalDateTime plannedAt) {
        try {
            executor.execute(() -> {
                try {
                    runVerified(taskCode, executionId, plannedAt);
                } catch (RuntimeException cause) {
                    log.error("スケジュール実行で想定外の例外が出ました。taskCode={} executionId={}",
                            taskCode, executionId, cause);
                }
            });
            return true;
        } catch (java.util.concurrent.RejectedExecutionException cause) {
            return false;
        }
    }

    /**
     * 実行の直前にもう一度計画を見てから走らせる。
     *
     * @return 実行を投入したら true（計画が無効でスキップしたら false）
     */
    public boolean runVerified(String taskCode, long executionId, LocalDateTime plannedAt) {
        if (plannedAt == null) {
            batchService.runQueued(executionId);
            return true;
        }
        // 実行直前は**そのとき最新の 1 枚**を取り直す（待ち行列で待つ間に設定が変わっている）
        ScheduleConfigSnapshot snapshot = configService.snapshot();
        SchedulePlanGuard.PlanDecision decision = planGuard.decide(snapshot, taskCode, plannedAt,
                Instant.now(clock), SchedulePlanGuard.Stage.BEFORE_RUN);
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

    /** 入り直しを待っている実行。 */
    private record PendingSubmission(String taskCode, LocalDateTime plannedAt, int attempts) {
    }
}

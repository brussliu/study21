package com.study21.admin.schedule;

import com.study21.admin.batch.BatchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

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
 * <p>キューがあふれたときは**確保を捨てずに記録を失敗として残す**（実行されないまま
 * 未完了で残ると、次の検査が同じ計画実行点を確保できないため）。</p>
 */
@Component
public class BatchScheduleExecutor {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduleExecutor.class);

    private static final AtomicLong THREAD_SEQUENCE = new AtomicLong();

    private final BatchService batchService;
    private final ThreadPoolExecutor executor;

    public BatchScheduleExecutor(BatchService batchService,
                                 @org.springframework.beans.factory.annotation.Value(
                                         "${study21.batch.schedule.workers:1}") int workers) {
        this.batchService = batchService;
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

    /** 実行を投入する（スケジュール検査のスレッドは待たない）。 */
    public void submit(String taskCode, long executionId) {
        try {
            executor.execute(() -> {
                try {
                    batchService.runQueued(executionId);
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

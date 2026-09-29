package com.study21.admin.englishessay;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 英作文の AI 添削の<b>働き手</b>（受付が積んだ {@code QUEUED} を実行する）。
 *
 * <p>2.0 と同じく<b>添削は非同期</b>: 画面は受付だけを行い、実行はバックエンドが続ける
 * （画面を閉じても・更新しても・ページを移っても、受け付けた添削は進む）。</p>
 *
 * <ul>
 *   <li>間隔: {@link #INTERVAL_SECONDS} 秒。1 周期で <b>1 件</b>を確保して実行する
 *       （{@code ENGLISH_ESSAY_*} に並列数の設定が無いので、AI を連打しない安全側の 1 件）</li>
 *   <li>落ちたままの {@code RUNNING} は {@link #STALE_MINUTES} 分より古ければ拾い直す</li>
 *   <li>1 件の失敗で働き手は止めない（理由は添削履歴の {@code FAILED} と実行ログに残る）</li>
 *   <li>{@code FAILED} は拾わない（利用者のやり直しだけが再開する＝黙って課金し直さない）</li>
 * </ul>
 */
@Component
public class EnglishEssayAiWorker implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(EnglishEssayAiWorker.class);

    /** 取りに行く間隔（秒）。 */
    static final int INTERVAL_SECONDS = 5;

    /** 実行中のまま残った行を拾い直すまでの分数（落ちた・再起動した場合の保険）。 */
    static final int STALE_MINUTES = 5;

    /** 1 周期で確保する件数。設定に並列数が無いので 1 件ずつ。 */
    static final int CLAIM_LIMIT = 1;

    private final EnglishEssayAiQueue queue;
    private final EnglishEssayGradingStep step;

    private ScheduledExecutorService scheduler;

    public EnglishEssayAiWorker(EnglishEssayAiQueue queue, EnglishEssayGradingStep step) {
        this.queue = queue;
        this.step = step;
    }

    /** 起動したら回し始める（画面の操作は要らない）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "english-essay-ai-worker");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::tickQuietly, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("英作文の AI 添削の働き手を開始しました（{} 秒ごと、最大 {} 件ずつ）。",
                INTERVAL_SECONDS, CLAIM_LIMIT);
    }

    @Override
    public void destroy() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    /** 例外で働き手を止めない（理由はログに残す）。 */
    private void tickQuietly() {
        try {
            tick();
        } catch (Exception cause) {
            log.error("英作文の AI 添削の働き手で想定外の失敗がありました（次の周期で続けます）。", cause);
        }
    }

    /**
     * 1 回分の処理（テストと診断の入口）。
     *
     * @return 実行した件数（失敗も数える。確保できなければ 0）
     */
    public int tick() {
        List<EnglishEssayAiMapper.GradingRow> items = queue.claim(CLAIM_LIMIT, STALE_MINUTES);
        if (items.isEmpty()) {
            return 0;
        }
        List<Future<Boolean>> futures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(CLAIM_LIMIT, items.size()), task -> {
            Thread thread = new Thread(task, "english-essay-ai-item");
            thread.setDaemon(true);
            return thread;
        });
        try {
            for (EnglishEssayAiMapper.GradingRow item : items) {
                futures.add(pool.submit(() -> runItemQuietly(item)));
            }
            for (Future<Boolean> future : futures) {
                await(future);
            }
            return items.size();
        } finally {
            pool.shutdown();
            try {
                if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                    pool.shutdownNow();
                }
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                pool.shutdownNow();
            }
        }
    }

    /** 1 件を実行する。想定外の例外でも働き手（と他の件）を止めない。 */
    private boolean runItemQuietly(EnglishEssayAiMapper.GradingRow item) {
        try {
            return step.runItem(item);
        } catch (Exception cause) {
            // 理由は添削履歴に残っている（Step が失敗として記録する）。ここでは次へ進む
            log.warn("英作文の AI 添削に失敗しました。gradingId={} essayId={} reason={}",
                    item.getGradingId(), item.getEssayId(), cause.getMessage());
            return false;
        }
    }

    /** 待って結果を取る（実行そのものは既に終わっている。例外は握る）。 */
    private static void await(Future<Boolean> future) {
        try {
            future.get();
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException cause) {
            log.warn("英作文の AI 添削の実行で想定外の失敗がありました。reason={}",
                    cause.getCause() == null ? cause.getMessage() : cause.getCause().getMessage());
        }
    }
}

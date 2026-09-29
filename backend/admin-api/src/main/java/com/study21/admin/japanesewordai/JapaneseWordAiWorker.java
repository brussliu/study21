package com.study21.admin.japanesewordai;

import com.study21.admin.setting.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 日本語単語の AI 取得の**働き手**（受付が積んだ {@code QUEUED} を実行する）。
 *
 * <p>利用者の指示（2026-09-27「改成异步处理」）と、AI 生図の働き手（{@code GeometryAiWorker}）と同じ考え方:
 * 画面は受付だけを行い、<b>実行はバックエンドが続ける</b>（画面を閉じても・更新しても・ページを移っても
 * 受け付けた取得は進む）。</p>
 *
 * <ul>
 *   <li>間隔: {@link #INTERVAL_SECONDS} 秒。1 周期で<b>最大 {@code THREADS} 件</b>を確保して並列に実行する
 *       （件数は<b>設定の {@code THREADS} の 4 バッチ最小値</b>＝どのバッチの並列数の上限も超えない。
 *       設定が読めないときは 1 件ずつ。{@link JapaneseWordAiSettings#threadsOrOne})</li>
 *   <li>落ちたままの {@code RUNNING} は {@link #STALE_MINUTES} 分より古ければ拾い直す
 *       （働き手の再起動で止まったままにしない）</li>
 *   <li>1 件の失敗で働き手は止めない（理由は生成履歴の {@code FAILED} と実行ログに残る）</li>
 *   <li>{@code FAILED} は拾わない（利用者のやり直しだけが再開する＝黙って課金し直さない）</li>
 * </ul>
 */
@Component
public class JapaneseWordAiWorker implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(JapaneseWordAiWorker.class);

    /** 取りに行く間隔（秒）。 */
    static final int INTERVAL_SECONDS = 5;

    /** 実行中のまま残った行を拾い直すまでの分数（落ちた・再起動した場合の保険）。 */
    static final int STALE_MINUTES = 5;

    private final JapaneseWordAiQueue queue;
    private final JapaneseWordAiStep step;
    private final SettingsService settingsService;

    private ScheduledExecutorService scheduler;

    public JapaneseWordAiWorker(JapaneseWordAiQueue queue, JapaneseWordAiStep step,
                               SettingsService settingsService) {
        this.queue = queue;
        this.step = step;
        this.settingsService = settingsService;
    }

    /** 起動したら回し始める（画面の操作は要らない）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (scheduler != null) {
            return;
        }
        scheduler = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "japanese-word-ai-worker");
            thread.setDaemon(true);
            return thread;
        });
        scheduler.scheduleWithFixedDelay(this::tickQuietly, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("日本語単語の AI 取得の働き手を開始しました（{} 秒ごと、最大 {} 件ずつ）。",
                INTERVAL_SECONDS, claimLimit());
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
            log.error("日本語単語の AI 取得の働き手で想定外の失敗がありました（次の周期で続けます）。", cause);
        }
    }

    /**
     * 1 回分の処理（テストと診断の入口）。
     *
     * @return 実行した件数（失敗も数える。確保できなければ 0）
     */
    public int tick() {
        int limit = claimLimit();
        List<JapaneseWordAiMapper.GenerationRow> items = queue.claim(limit, STALE_MINUTES);
        if (items.isEmpty()) {
            return 0;
        }
        List<Future<Boolean>> futures = new ArrayList<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(limit, items.size()), task -> {
            Thread thread = new Thread(task, "japanese-word-ai-item");
            thread.setDaemon(true);
            return thread;
        });
        try {
            for (JapaneseWordAiMapper.GenerationRow item : items) {
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
    private boolean runItemQuietly(JapaneseWordAiMapper.GenerationRow item) {
        try {
            return step.runItem(item);
        } catch (Exception cause) {
            // 理由は生成履歴に残っている（Step が失敗として記録する）。ここでは次へ進む
            log.warn("日本語単語の AI 取得に失敗しました。generationId={} wordId={} contentType={} reason={}",
                    item.getGenerationId(), item.getWordId(), item.getContentType(), cause.getMessage());
            return false;
        }
    }

    /** 待って結果を取る（実行そのものは既に終わっている。例外は握る）。 */
    private static void await(Future<Boolean> future) {
        try {
            future.get();
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
        } catch (java.util.concurrent.ExecutionException cause) {
            // runItemQuietly が握っているので、ここへ来るのは想定外（ログだけ残す）
            log.warn("日本語単語の AI 取得の実行で想定外の失敗がありました。reason={}",
                    cause.getCause() == null ? cause.getMessage() : cause.getCause().getMessage());
        }
    }

    /**
     * この周期で確保する件数（設定の {@code THREADS} の 4 バッチ最小値）。
     *
     * <p>どのバッチの行を拾うかは確保するまで分からないので、**4 つの最小値**を使う
     * （どのバッチの並列数の上限も超えない安全側）。設定が読めないときは 1。
     * 規則そのものは {@link JapaneseWordAiSettings#threadsLimitOf} が持つ。</p>
     */
    private int claimLimit() {
        try {
            return JapaneseWordAiSettings.threadsLimitOf(settingsService);
        } catch (Exception cause) {
            log.warn("AI 取得の設定が読めないので 1 件ずつ進めます。reason={}", cause.getMessage());
            return 1;
        }
    }
}

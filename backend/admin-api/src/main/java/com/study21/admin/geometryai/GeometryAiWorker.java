package com.study21.admin.geometryai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * AI 生図の**バックエンドの働き手**（画面からの起動に依存しない）。
 *
 * <p>利用者の指示: 「タスクはバックエンドが本当に调度する。画面を閉じても・更新しても・
 * ページを移っても、受け付けたタスクは実行され続ける」。そこで要求行そのものを待ち行列にし、
 * この働き手が**一定間隔で 1 件ずつ**拾って流水線（読み取り → 生成 → 検証）を進める。
 * 画面が `run` を呼ぶ必要はもう無い（管理画面からの手動実行だけが残る）。</p>
 *
 * <ul>
 *   <li>間隔: {@link #INTERVAL_SECONDS} 秒（1 件終わってから次を取りに行く。同時実行は 1）</li>
 *   <li>落ちたままの行（`PREPROCESSING` / `GENERATING` / `VALIDATING`）は
 *       {@link #STALE_MINUTES} 分より古ければ拾い直す（worker の再起動で止まったままにしない）</li>
 *   <li>1 件の失敗で働き手は止めない（理由をログに残し、次の周期で別の行を拾う）</li>
 *   <li>`GENERATED`（検証待ち）は**検証だけ**を行う（AI をもう一度呼ばない＝二重課金しない）</li>
 * </ul>
 */
@Component
public class GeometryAiWorker implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(GeometryAiWorker.class);

    /** 取りに行く間隔（秒）。 */
    static final int INTERVAL_SECONDS = 5;
    /** 処理中のまま残った行を拾い直すまでの分数（落ちた・再起動した場合の保険）。 */
    static final int STALE_MINUTES = 5;
    /** 働き手の名前（実行履歴・ログに残す）。 */
    private static final String OPERATOR = "geometry-ai-worker";

    private final GeometryAiTaskQueue queue;
    private final AiFigurePipelineService pipelineService;

    private ScheduledExecutorService executor;

    public GeometryAiWorker(GeometryAiTaskQueue queue, AiFigurePipelineService pipelineService) {
        this.queue = queue;
        this.pipelineService = pipelineService;
    }

    /** 起動したら回し始める（画面の操作は要らない）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (executor != null) {
            return;
        }
        executor = Executors.newSingleThreadScheduledExecutor(task -> {
            Thread thread = new Thread(task, "geometry-ai-worker");
            thread.setDaemon(true);
            return thread;
        });
        executor.scheduleWithFixedDelay(this::tickQuietly, INTERVAL_SECONDS, INTERVAL_SECONDS, TimeUnit.SECONDS);
        log.info("AI 生図の働き手を開始しました（{} 秒ごと、1 件ずつ）。", INTERVAL_SECONDS);
    }

    @Override
    public void destroy() {
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    /** 例外で働き手を止めない（理由はログに残す）。 */
    private void tickQuietly() {
        try {
            tick();
        } catch (Exception cause) {
            log.error("AI 生図の働き手で想定外の失敗がありました（次の周期で続けます）。", cause);
        }
    }

    /**
     * 1 回分の処理（テストと診断の入口）。
     *
     * @return 処理した要求 ID（拾うものが無ければ null）
     */
    public Long tick() {
        Long pipelineId = queue.claimForPipeline(STALE_MINUTES);
        if (isClaimed(pipelineId)) {
            runPipeline(pipelineId);
            return pipelineId;
        }
        Long validationId = queue.claimForValidation(STALE_MINUTES);
        if (isClaimed(validationId)) {
            runValidation(validationId);
            return validationId;
        }
        return null;
    }

    /** 確保できたか（採番は 1 以上。0 以下は「無し」として扱う）。 */
    private static boolean isClaimed(Long requestId) {
        return requestId != null && requestId > 0;
    }

    /** 読み取り → 生成 → 検証を順に進める（失敗しても働き手は止めない）。 */
    private void runPipeline(long aiRequestId) {
        try {
            pipelineService.run(aiRequestId, OPERATOR);
        } catch (Exception cause) {
            // 要求行には失敗の理由が入っている（画面から確認できる）。働き手は次へ進む
            log.warn("AI 生図の処理に失敗しました。requestId={} reason={}", aiRequestId, cause.getMessage());
        }
    }

    /** 検証だけを行う（AI は呼ばない）。 */
    private void runValidation(long aiRequestId) {
        try {
            pipelineService.resumeValidation(aiRequestId, OPERATOR);
        } catch (Exception cause) {
            log.warn("AI 生図の検証に失敗しました。requestId={} reason={}", aiRequestId, cause.getMessage());
        }
    }
}

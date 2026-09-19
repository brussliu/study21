package com.study21.admin.schedule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/**
 * 統一スケジューラ（既定 30 秒ごと）。
 *
 * <p>1 回の検査でやること:</p>
 * <ol>
 *   <li>メモリのスナップショットを 1 枚取り、**その 1 枚だけ**で各タスクの計画実行点を決める
 *       （検査の途中で設定が入れ替わって、古い間隔と新しい時刻が混ざらない）。</li>
 *   <li>メモリに無いタスクだけ {@link ScheduleConfigService#ensureTaskConfig} で DB 托底
 *       （退避中は DB を引かない）。設定が無い・不正なタスクは**実行しない**（隠れた既定値を使わない）。</li>
 *   <li>「いま以前で最後に到来した計画実行点」を確保（claim）する。**最新の 1 点だけ**なので、
 *       何度取りこぼしても一括の補跑にはならない（L は最大 1 回の合并、R は最新の有効な 1 点）。</li>
 *   <li>確保できたものだけ実行記録を作り、業務は {@link BatchScheduleExecutor} に渡す
 *       （検査のスレッドは ffmpeg・AI・ネット制御で塞がない）。</li>
 *   <li>無効なタスクは**計画だけ進める**（有効に戻したときに古い計画実行点を実行しない）。</li>
 * </ol>
 *
 * <p>時刻は常に Asia/Tokyo（サーバーの OS タイムゾーンに依存しない）。</p>
 */
@Component
public class BatchScheduleScheduler {

    private static final Logger log = LoggerFactory.getLogger(BatchScheduleScheduler.class);

    private final ScheduleConfigService configService;
    private final ScheduledTriggerStore triggerStore;
    private final BatchScheduleExecutor executor;
    private final ScheduleRuleCatalog catalog;
    private final Clock clock;

    /** 設定が読めないときの案内ログを出しすぎないための間隔（この回数ごとに 1 行）。 */
    private final int skipLogEvery;

    private long skipLogCounter;

    @org.springframework.beans.factory.annotation.Autowired
    public BatchScheduleScheduler(ScheduleConfigService configService,
                                  ScheduledTriggerStore triggerStore,
                                  BatchScheduleExecutor executor,
                                  ScheduleRuleCatalog catalog,
                                  @Value("${study21.batch.schedule.skip-log-every:20}") int skipLogEvery) {
        this(configService, triggerStore, executor, catalog, skipLogEvery,
                Clock.system(ScheduleConfigService.ZONE));
    }

    /** テスト用（時計を差し替える）。 */
    public BatchScheduleScheduler(ScheduleConfigService configService,
                                  ScheduledTriggerStore triggerStore,
                                  BatchScheduleExecutor executor,
                                  ScheduleRuleCatalog catalog,
                                  int skipLogEvery,
                                  Clock clock) {
        this.configService = configService;
        this.triggerStore = triggerStore;
        this.executor = executor;
        this.catalog = catalog;
        this.skipLogEvery = Math.max(1, skipLogEvery);
        this.clock = clock;
    }

    /** 30 秒ごとの検査（アプリ起動から 10 秒後に始める）。 */
    @Scheduled(fixedDelayString = "${study21.batch.schedule.check-interval-ms:30000}",
            initialDelayString = "${study21.batch.schedule.initial-delay-ms:10000}")
    public void checkDueTasks() {
        try {
            runOnce(clock.instant());
        } catch (RuntimeException cause) {
            // 検査そのものが落ちても次の周期で再開する（スケジューラを止めない）
            log.error("バッチのスケジュール検査で想定外の例外が出ました（次の周期で再開します）。", cause);
        }
    }

    /**
     * 1 回ぶんの検査（テストから直接呼べる）。
     *
     * @param now 検査の時刻（Asia/Tokyo に変換して計画実行点を決める）
     */
    public ScheduleCheckResult runOnce(Instant now) {
        // 反映できていない設定があれば、退避の期限が来ていれば読み直す
        configService.retryPendingIfDue();

        ScheduleConfigSnapshot snapshot = configService.snapshot();
        LocalDateTime nowLocal = LocalDateTime.ofInstant(now, ScheduleConfigService.ZONE);
        List<String> triggered = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (ScheduleTaskRule rule : catalog.rules()) {
            String taskCode = rule.taskCode();
            if (!snapshot.statusOf(taskCode).usable()) {
                // メモリに無い（未読込・未設定・不正）ときだけ托底を試す
                snapshot = configService.ensureTaskConfig(taskCode);
            }
            TaskConfigStatus status = snapshot.statusOf(taskCode);
            TaskSchedule schedule = snapshot.taskOf(taskCode).orElse(null);
            if (!status.usable() || schedule == null) {
                skipped.add(taskCode);
                continue;
            }

            LocalDateTime due = schedule.previousPointAtOrBefore(nowLocal);
            if (due == null) {
                continue;
            }
            String batchType = rule.kind() == ScheduleKind.DAILY ? "R" : "L";
            if (!schedule.enabled()) {
                // 無効の間は計画だけ進める（実行記録は残さない＝履歴をゴミで埋めない）
                triggerStore.advanceWithoutRun(taskCode, due);
                continue;
            }
            if (triggerStore.alreadyClaimed(taskCode, due)) {
                continue;
            }
            Long executionId = triggerStore.claimAndRecord(taskCode, due, batchType);
            if (executionId == null) {
                continue;   // 他の実行（別インスタンス・前の検査）が先に確保した
            }
            executor.submit(taskCode, executionId);
            triggered.add(taskCode + "@" + due);
        }

        if (!triggered.isEmpty()) {
            log.info("バッチの計画実行点を実行します。{}", triggered);
        }
        if (!skipped.isEmpty() && ++skipLogCounter % skipLogEvery == 0) {
            log.warn("設定が無い・不正なため自動実行しないバッチがあります。{}（設定を確認してください）", skipped);
        }
        return new ScheduleCheckResult(nowLocal, snapshot.version(), triggered, skipped,
                executor.activeCount(), executor.queuedCount());
    }

    /** いまの時計（Asia/Tokyo）。 */
    public ZoneId zone() {
        return ScheduleConfigService.ZONE;
    }

    /**
     * 1 回の検査の結果。
     *
     * @param checkedAt    検査時刻（Asia/Tokyo）
     * @param configVersion 使ったスナップショットの版
     * @param triggered    確保して実行を投入した計画実行点（{@code taskCode@yyyy-MM-ddTHH:mm}）
     * @param skipped      設定が無い・不正で実行しなかったタスク
     * @param runningWorkers 実行中の業務の本数
     * @param queuedWorkers  実行待ちの本数
     */
    public record ScheduleCheckResult(LocalDateTime checkedAt, long configVersion,
                                      List<String> triggered, List<String> skipped,
                                      int runningWorkers, int queuedWorkers) {
    }
}

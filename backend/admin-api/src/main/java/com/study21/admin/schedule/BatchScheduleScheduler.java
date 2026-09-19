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
        List<String> deferred = new ArrayList<>();

        // メモリに無い（未読込・未設定・不正）タスクだけ托底を試す
        for (ScheduleTaskRule rule : catalog.rules()) {
            if (!snapshot.statusOf(rule.taskCode()).usable()) {
                snapshot = configService.ensureTaskConfig(rule.taskCode());
            }
        }

        // (1) 種別 R（ネット利用の開始／終了）は**1 つのネット状態の切替**として扱う
        runNetworkGroup(snapshot, nowLocal, triggered, skipped, deferred);

        // (2) 種別 L（学習モニターの取込・分析）はタスクごと
        for (ScheduleTaskRule rule : catalog.rules()) {
            if (rule.kind() != ScheduleKind.INTERVAL) {
                continue;
            }
            String taskCode = rule.taskCode();
            TaskConfigStatus status = snapshot.statusOf(taskCode);
            TaskSchedule schedule = snapshot.taskOf(taskCode).orElse(null);
            if (!status.usable() || schedule == null) {
                skipped.add(taskCode);
                continue;
            }
            // 設定の適用時刻より前の点は実行しない（利用者が間隔・ずらしを変えた直後の過去の点）
            LocalDateTime due = schedule.previousRunnablePointAtOrBefore(nowLocal).orElse(null);
            if (due == null) {
                continue;
            }
            if (!schedule.enabled()) {
                // 無効の間は計画だけ進める（実行記録は残さない＝履歴をゴミで埋めない）
                triggerStore.advanceWithoutRun(taskCode, due);
                continue;
            }
            if (triggerStore.alreadyClaimed(taskCode, due)) {
                continue;
            }
            Long executionId = triggerStore.claimAndRecord(taskCode, due, "L");
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
        return new ScheduleCheckResult(nowLocal, snapshot.version(), triggered, skipped, deferred,
                executor.activeCount(), executor.queuedCount());
    }

    /**
     * 種別 R（`batR03` 停止 / `batR04` 開始）を**1 つのネット状態の切替**として処理する。
     *
     * <p>取りこぼした点が両方にあるとき（例: 夜間に停止して朝に復帰）に、**古い方から順に両方**を
     * 実行すると、適用する順番によっては「開始したのに止まっている」という誤った状態になる。
     * そこで「いま以前で最後に到来した 1 点（＝現在有効なはずのネット状態）」だけを実行し、
     * それより古い点は**実行せずに計画だけ進める**（逆向きの操作を後から走らせない）。</p>
     *
     * <p>最新の点が「無効」または「設定の適用時刻より前」なら、古い点も実行しない
     * （利用者が止めた／設定を変えた直後に、古い状態を適用しないため）。</p>
     */
    private void runNetworkGroup(ScheduleConfigSnapshot snapshot, LocalDateTime nowLocal,
                                 List<String> triggered, List<String> skipped, List<String> deferred) {
        List<NetworkCandidate> candidates = new ArrayList<>();
        for (ScheduleTaskRule rule : catalog.rules()) {
            if (rule.kind() != ScheduleKind.DAILY) {
                continue;
            }
            String taskCode = rule.taskCode();
            TaskSchedule schedule = snapshot.taskOf(taskCode).orElse(null);
            if (schedule == null) {
                skipped.add(taskCode);
                continue;
            }
            LocalDateTime due = schedule.previousPointAtOrBefore(nowLocal);
            if (due == null || triggerStore.alreadyClaimed(taskCode, due)) {
                continue;
            }
            candidates.add(new NetworkCandidate(taskCode, schedule, due));
        }
        if (candidates.isEmpty()) {
            return;
        }

        // いま以前で最後に到来した点＝現在有効なはずのネット状態
        NetworkCandidate newest = candidates.get(0);
        for (NetworkCandidate candidate : candidates) {
            if (candidate.due().isAfter(newest.due())) {
                newest = candidate;
            }
        }
        // それより古い点は実行しない（対になる操作を後から走らせない）
        for (NetworkCandidate candidate : candidates) {
            if (candidate == newest) {
                continue;
            }
            if (triggerStore.advanceWithoutRun(candidate.taskCode(), candidate.due())) {
                log.info("ネット利用の古い計画実行点は実行せずに進めました（新しい点を優先）。taskCode={} due={}",
                        candidate.taskCode(), candidate.due());
            }
        }

        if (!newest.schedule().canRunAt(newest.due())) {
            // 設定の適用時刻より前＝利用者が設定を変えた直後。過去の点は実行しない
            triggerStore.advanceWithoutRun(newest.taskCode(), newest.due());
            log.info("設定変更後なので、過去の計画実行点は実行しません。taskCode={} due={} effectiveFrom={}",
                    newest.taskCode(), newest.due(), newest.schedule().effectiveFrom());
            return;
        }
        if (!newest.schedule().enabled()) {
            // いま有効なはずの状態の切替が無効 → 古い状態も適用しない（計画だけ進める）
            triggerStore.advanceWithoutRun(newest.taskCode(), newest.due());
            return;
        }
        // もう一方のネット切替が実行中なら、今回は見送る（点は確保しない＝次の検査で再挑戦）
        String sibling = siblingTaskCode(newest.taskCode());
        if (sibling != null && triggerStore.isTaskRunning(sibling)) {
            deferred.add(newest.taskCode());
            log.info("もう一方のネット切替が実行中のため、今回は見送ります（次の検査で再挑戦）。taskCode={} running={}",
                    newest.taskCode(), sibling);
            return;
        }

        Long executionId = triggerStore.claimAndRecord(newest.taskCode(), newest.due(), "R");
        if (executionId == null) {
            return;   // 他の実行が先に確保した
        }
        executor.submit(newest.taskCode(), executionId);
        triggered.add(newest.taskCode() + "@" + newest.due());
    }

    /** ネット利用のもう一方のタスク（batR03 ⇄ batR04）。 */
    private String siblingTaskCode(String taskCode) {
        if ("batR03".equals(taskCode)) {
            return "batR04";
        }
        if ("batR04".equals(taskCode)) {
            return "batR03";
        }
        return null;
    }

    /** 種別 R の候補（タスク・その設定・いま以前で最後の計画実行点）。 */
    private record NetworkCandidate(String taskCode, TaskSchedule schedule, LocalDateTime due) {
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
     * @param deferred     もう一方のネット切替が実行中のため、次の検査に回したタスク
     * @param runningWorkers 実行中の業務の本数
     * @param queuedWorkers  実行待ちの本数
     */
    public record ScheduleCheckResult(LocalDateTime checkedAt, long configVersion,
                                      List<String> triggered, List<String> skipped, List<String> deferred,
                                      int runningWorkers, int queuedWorkers) {
    }
}

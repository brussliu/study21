package com.study21.admin.schedule;

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

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * サービス再起動で**未完了のまま残った実行**を復旧する。
 *
 * <p>落ちた実行（待機中・実行中）を残したままだと、同じタスクの次の実行が
 * 「前回がまだ実行中」と見なされて永久に走らない。起動時に 1 回、次のように扱う:</p>
 *
 * <ul>
 *   <li><b>まず「いまの計画」で有効かを判定する</b>（{@link SchedulePlanGuard}）。スケジューラと
 *       **同じ規則**を使う（現在時刻・設定の版・適用時刻・有効／無効・実行状態）。
 *       有効でなければ**スキップとして閉じ、理由を残す**（実行しないまま未完了にしない）。</li>
 *   <li><b>待機中（まだ始まっていない）</b>: そのまま実行し直す。業務（端末モードの一括切替・
 *       動画取込・スナップショット分析）は**何度実行しても同じ結果**になるように作ってある。</li>
 *   <li><b>実行中（結果が分からない）</b>: まず失敗として閉じ、**その点がまだ「いまの計画」で
 *       有効なときだけ**、新しい実行記録（{@code 起動種別=RETRY}）で 1 回だけやり直す。
 *       新しい計画実行点に追い越されていればやり直さない（古い状態で新しい状態を上書きしない）。</li>
 *   <li>スケジューラが管理しないタスク（起動時バッチなど）は今までどおり失敗として閉じるだけ。</li>
 * </ul>
 *
 * <p><b>種別 R（batR03 / batR04）は 1 つのネット状態</b>なので、未完了が両方にあるときは
 * **新しい点から順に見る**。古い方（＝逆向きの操作）は判定で無効になり、スキップとして閉じる
 * （夜間の停止が翌朝まで残っていても、復旧で実行されるのは朝の開始だけ）。</p>
 *
 * <p><b>種別 L（batL02 / batL03）は取りこぼしを最新の 1 点に合并する</b>。古い点は実行せず
 * スキップとして閉じ、次の検査が最新の 1 点を確保して 1 回だけ実行する（履歴を 1 件ずつ再生しない）。</p>
 *
 * <p>順序は {@link Ordered#HIGHEST_PRECEDENCE}。既存の {@code BatchStartupRunner}
 * （起動時バッチ）より**先**に走らせて、起動時バッチの新しい実行を誤って失敗にしないようにする。</p>
 *
 * <p><b>手動操作の保護は業務側で続く</b>（{@code NetworkUsageService} が端末の更新日時を見て、
 * 計画時刻より後に利用者が触っていれば切り替えない）。ここでは実行してよいかを決めるだけ。</p>
 *
 * <p>前提: 第一版は admin-api が 1 インスタンスで動く（多重起動する構成にしたときは、
 * 他インスタンスが実行中の行を閉じてしまわないよう、この復旧の条件を見直すこと）。</p>
 */
@Component
public class BatchExecutionRecovery {

    /** 再起動で閉じた実行に残すメッセージ（結果が分からない実行）。 */
    static final String RECOVERY_MESSAGE = "サービス再起動のため中断しました（結果は不明です）。";

    /** 実行しないと判定して閉じた実行に残すメッセージの前置き（理由を続ける）。 */
    static final String SKIPPED_PREFIX = "サービス再起動時に計画が有効でなかったため実行しませんでした。";

    /** やり直しの実行記録に残す依頼元コード。 */
    static final String RETRY_CODE = "RECOVERY";

    private static final DateTimeFormatter PAYLOAD_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");

    private static final Logger log = LoggerFactory.getLogger(BatchExecutionRecovery.class);

    private final BatchExecutionMapper executionMapper;
    private final ScheduledTriggerStore triggerStore;
    private final BatchScheduleExecutor executor;
    private final ScheduleRuleCatalog catalog;
    private final SchedulePlanGuard planGuard;
    private final ScheduleConfigService configService;
    private final Clock clock;

    @org.springframework.beans.factory.annotation.Autowired
    public BatchExecutionRecovery(BatchExecutionMapper executionMapper,
                                  ScheduledTriggerStore triggerStore,
                                  BatchScheduleExecutor executor,
                                  ScheduleRuleCatalog catalog,
                                  SchedulePlanGuard planGuard,
                                  ScheduleConfigService configService) {
        this(executionMapper, triggerStore, executor, catalog, planGuard, configService,
                Clock.system(ScheduleConfigService.ZONE));
    }

    /** テスト用（時計を差し替える）。 */
    public BatchExecutionRecovery(BatchExecutionMapper executionMapper,
                                  ScheduledTriggerStore triggerStore,
                                  BatchScheduleExecutor executor,
                                  ScheduleRuleCatalog catalog,
                                  SchedulePlanGuard planGuard,
                                  ScheduleConfigService configService,
                                  Clock clock) {
        this.executionMapper = executionMapper;
        this.triggerStore = triggerStore;
        this.executor = executor;
        this.catalog = catalog;
        this.planGuard = planGuard;
        this.configService = configService;
        this.clock = clock;
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
        // **設定を読んでから判定する**。この復旧は起動時（ApplicationReadyEvent）で最初に走るため、
        // 何もしないとメモリのスナップショットが空（未読込）で、正しい実行まで「計画が無効」と
        // 誤判定してしまう（实际に起きた）。托底の仕組みで 1 回読み、それでも読めないときは
        // 判定を保留する（閉じない）
        configService.ensureUsableConfig();

        List<BatchExecutionEntity> scheduled = new ArrayList<>();
        int closed = 0;
        for (BatchExecutionEntity row : unfinished) {
            if (catalog.taskCodes().contains(row.getBatchCode())) {
                scheduled.add(row);
            } else {
                // スケジューラが管理しないタスクは今までどおり閉じるだけ
                close(row, RECOVERY_MESSAGE, BatchExecutionStatus.FAILED);
                closed++;
            }
        }
        // **計画実行点の新しい順**に見る。R（ネット状態）は新しい点だけが有効なので、
        // 古い逆向きの操作から先に処理しない（閉じてから新しい点を判定する）
        scheduled.sort(Comparator
                .comparing((BatchExecutionEntity row) -> plannedAtOf(row),
                        Comparator.nullsFirst(Comparator.naturalOrder()))
                .reversed()
                .thenComparing(BatchExecutionEntity::getExecutionId));

        int retried = 0;
        int skipped = 0;
        for (BatchExecutionEntity row : scheduled) {
            // 1 件の復旧で例外が出ても、起動を止めたり残りの行の復旧を止めたりしない
            try {
                Boolean retriedRow = recoverScheduledTask(row);
                if (retriedRow == null) {
                    skipped++;
                } else if (retriedRow) {
                    retried++;
                } else {
                    closed++;
                }
            } catch (RuntimeException cause) {
                log.warn("中断した実行の復旧に失敗しました（この実行は次の起動で再試行します）。"
                        + "taskCode={} executionId={} reason={}",
                        row.getBatchCode(), row.getExecutionId(), cause.getMessage());
            }
        }
        log.warn("サービス再起動で中断した実行を復旧しました。closed={} retried={} skipped={}",
                closed, retried, skipped);
    }

    /**
     * 1 件の復旧。
     *
     * @return 実行し直した（またはやり直しを投入した）ら true、失敗として閉じたら false、
     *         スキップとして閉じた（または判定を保留した）ら null
     */
    private Boolean recoverScheduledTask(BatchExecutionEntity row) {
        String taskCode = row.getBatchCode();
        LocalDateTime plannedAt = plannedAtOf(row);
        Instant now = Instant.now(clock);
        SchedulePlanGuard.PlanDecision decision =
                planGuard.decide(taskCode, plannedAt, now, SchedulePlanGuard.Stage.RECOVERY);
        if (decision.deferred()) {
            // 設定をまだ読めていない等で**判定できない**。無効と決めつけず、そのまま残す
            // （次の起動・次の検査でもう一度判定する。実行記録をスキップで埋めない）
            log.warn("中断した実行の判定を保留しました（設定を読めていないため）。taskCode={} executionId={} 予定={} "
                    + "reason={}", taskCode, row.getExecutionId(), plannedAt, decision.reason());
            return null;
        }
        if (!decision.allowed()) {
            if (BatchExecutionStatus.QUEUED.name().equals(row.getStatus())) {
                // まだ始まっていない → 実行しないと決めたのでスキップ（理由を残す）
                close(row, SKIPPED_PREFIX + decision.reason(), BatchExecutionStatus.SKIPPED);
            } else {
                // 途中まで動いた可能性がある（結果は不明）。やり直しはしない
                close(row, RECOVERY_MESSAGE + "（いまの計画では有効でないため、やり直しません。"
                        + decision.reason() + "）", BatchExecutionStatus.FAILED);
            }
            log.warn("中断した実行は実行しません（いまの計画では有効でない）。taskCode={} executionId={} 予定={} "
                    + "status={} reason={} configVersion={}", taskCode, row.getExecutionId(), plannedAt,
                    row.getStatus(), decision.reason(), decision.configVersion());
            return null;
        }

        if (BatchExecutionStatus.QUEUED.name().equals(row.getStatus())) {
            // まだ始まっていない → そのまま実行し直す（実行記録は残っているものをそのまま使う）。
            // 実行の直前にもう一度計画を見る（起動処理の途中で設定が変わることがある）
            log.warn("再起動前に確保していた計画実行点を実行し直します。taskCode={} executionId={} 予定={} "
                    + "configVersion={}", taskCode, row.getExecutionId(), plannedAt, decision.configVersion());
            executor.submit(taskCode, row.getExecutionId(), plannedAt);
            return true;
        }

        // 実行中 → 結果が分からないので失敗として閉じ、**同じ計画実行点**を 1 回だけやり直す。
        // やり直してよい根拠は業務の冪等性（端末モードは設定・L02 は取込済みの重複除外・
        // L03 は最新版フラグ）。同じ点が二重に走らないことは DB の確保が保証する
        close(row, RECOVERY_MESSAGE, BatchExecutionStatus.FAILED);
        Long retryId = triggerStore.insertRetryRow(taskCode, plannedAt,
                row.getTriggerType() == null ? "C" : row.getTriggerType(), RETRY_CODE);
        if (retryId == null) {
            return false;
        }
        executor.submit(taskCode, retryId, plannedAt);
        return true;
    }

    private void close(BatchExecutionEntity row, String message, BatchExecutionStatus status) {
        executionMapper.markFinished(row.getExecutionId(), status.name(), message, message, 0L);
    }

    /** 実行記録の 予定時刻（ISO 形式。空白区切りで入っている行も読めるようにする）。 */
    private static LocalDateTime plannedAtOf(BatchExecutionEntity row) {
        return parse(row.getScheduleTime());
    }

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

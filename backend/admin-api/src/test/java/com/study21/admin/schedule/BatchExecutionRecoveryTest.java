package com.study21.admin.schedule;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import com.study21.admin.batch.BatchExecutionStatus;
import com.study21.admin.batch.ProcessRunId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 再起動の復旧（{@link BatchExecutionRecovery}）。
 *
 * <p>利用者の指摘のうち、ここで固定するもの:</p>
 * <ol>
 *   <li>復旧は**スケジューラと同じ規則**（{@link SchedulePlanGuard}）で「いまの計画」かを見る。</li>
 *   <li><b>起動時に読めなくても自動で続く</b>: 設定や遺留リストが読めないときは保留し、
 *       30 秒の検査（{@link BatchExecutionRecovery#retryPendingRecoveryIfDue()}）で判定し直す
 *       （**再起動は要らない**）。</li>
 *   <li><b>復旧の境界</b>: 起動前に作られた実行だけを対象にする（そのときの最大の実行ID で固定）。
 *       このプロセスが作った実行は復旧しない。</li>
 *   <li>種別 R は**新しい方だけ**、種別 L は**最新の 1 点に合并**。</li>
 *   <li>実行中（結果が不明）は**1 トランザクション**で閉じてやり直しを作り、**コミット後に**投入する。</li>
 *   <li>完了したら以後は DB を引かない（同じ行を 2 回処理しない）。</li>
 * </ol>
 */
class BatchExecutionRecoveryTest {

    private static final ZoneId ZONE = ScheduleConfigService.ZONE;

    /** 2026-09-20 09:00 JST（前夜 23:30 の停止と当日 06:30 の開始の両方が過ぎている）。 */
    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");

    /** テストのプロセスの起動識別子（復旧の境界）。 */
    private static final String CURRENT_RUN_ID = "20260920T090000-aaaaaaaa";

    /** 前のプロセスの起動識別子（遺留の目印）。 */
    private static final String OLD_RUN_ID = "20260919T230000-bbbbbbbb";

    private BatchExecutionMapper executionMapper;
    private ScheduledTriggerStore triggerStore;
    private BatchScheduleExecutor executor;
    private ScheduleConfigService configService;
    private ScheduleRuleCatalog catalog;
    private MutableClock clock;
    private SchedulePlanGuard planGuard;
    private BatchExecutionRecovery recovery;

    /** テスト用の起動識別子（固定値）。 */
    private static final class ProcessRunIdOf extends ProcessRunId {
        private final String value;

        ProcessRunIdOf(String value) {
            super(Clock.fixed(NOW, ZONE));
            this.value = value;
        }

        @Override
        public String value() {
            return value;
        }
    }

    /** テスト用の時計（進められる）。 */
    private static final class MutableClock extends Clock {
        private Instant now = NOW;

        @Override
        public ZoneId getZone() {
            return ZONE;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    @BeforeEach
    void setUp() {
        executionMapper = mock(BatchExecutionMapper.class);
        triggerStore = mock(ScheduledTriggerStore.class);
        executor = mock(BatchScheduleExecutor.class);
        configService = mock(ScheduleConfigService.class);
        catalog = new ScheduleRuleCatalog();
        clock = new MutableClock();
        planGuard = new SchedulePlanGuard(catalog, triggerStore);
        recovery = new BatchExecutionRecovery(executionMapper, triggerStore, executor, catalog, planGuard,
                configService, new ProcessRunIdOf(CURRENT_RUN_ID), true, clock);
        when(triggerStore.closeLeftover(anyLong(), any(), anyString())).thenReturn(true);
    }

    /** 直近に作ったスナップショット（托底の戻り値に使う。Mockito の入れ子呼び出しを避ける）。 */
    private ScheduleConfigSnapshot lastSnapshot;

    private void config(TaskSchedule... schedules) {
        Map<String, TaskSchedule> tasks = new LinkedHashMap<>();
        Map<String, TaskConfigStatus> statuses = new LinkedHashMap<>();
        for (String code : catalog.taskCodes()) {
            statuses.put(code, TaskConfigStatus.MISSING);
        }
        for (TaskSchedule schedule : schedules) {
            tasks.put(schedule.taskCode(), schedule);
            statuses.put(schedule.taskCode(), TaskConfigStatus.LOADED);
        }
        lastSnapshot = new ScheduleConfigSnapshot(7, NOW, ZONE, tasks, statuses, Map.of(), null);
        when(configService.snapshot()).thenReturn(lastSnapshot);
        when(configService.ensureUsableConfig()).thenReturn(lastSnapshot);
    }

    /** 設定をまだ読めていない状態（起動直後・DB を読めなかった）。 */
    private void configNotLoaded() {
        lastSnapshot = ScheduleConfigSnapshot.empty(ZONE, catalog.taskCodes());
        when(configService.snapshot()).thenReturn(lastSnapshot);
        when(configService.ensureUsableConfig()).thenReturn(lastSnapshot);
    }

    /** 前夜の停止と当日の開始（R の 2 タスク。両方有効）。 */
    private void networkTasks() {
        config(TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)),
                TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
    }

    /** 前のプロセスが作った実行（遺留）。 */
    private BatchExecutionEntity row(String taskCode, String status, Long id, String plannedAt) {
        BatchExecutionEntity entity = new BatchExecutionEntity();
        entity.setExecutionId(id);
        entity.setBatchCode(taskCode);
        entity.setStatus(status);
        entity.setTriggerType("R");
        entity.setScheduleTime(plannedAt);
        entity.setRunId(OLD_RUN_ID);
        return entity;
    }

    /** このプロセスが作った実行（遺留ではない）。 */
    private BatchExecutionEntity ownRow(String taskCode, String status, Long id, String plannedAt) {
        BatchExecutionEntity entity = row(taskCode, status, id, plannedAt);
        entity.setRunId(CURRENT_RUN_ID);
        return entity;
    }

    /** 遺留リスト（前のプロセスの実行だけ）と、1 件ずつの読み直しを用意する。 */
    private void leftovers(BatchExecutionEntity... rows) {
        when(executionMapper.findLeftoversExceptRunId(CURRENT_RUN_ID)).thenReturn(List.of(rows));
        for (BatchExecutionEntity row : rows) {
            when(executionMapper.findById(row.getExecutionId())).thenReturn(row);
        }
    }

    // ------------------------------------------------------------------ 起動時の復旧

    @Test
    @DisplayName("待機中（まだ始まっていない）で、いまの計画なら実行し直す（永久にスキップしない）")
    void queuedExecutionIsRetried() {
        networkTasks();
        leftovers(row("batR04", "QUEUED", 800L, "2026-09-20T06:30:00"));

        recovery.recoverInterruptedExecutions();

        verify(executor).submit(eq("batR04"), eq(800L), any());
        verify(triggerStore, never()).closeLeftover(anyLong(), any(), anyString());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("夜間の停止が翌朝まで残っていても、実行するのは「いま有効なはずの切替」だけ")
    void staleStopIsNotRecoveredInTheMorning() {
        networkTasks();
        leftovers(row("batR03", "QUEUED", 801L, "2026-09-19T23:30:00"));

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(triggerStore).closeLeftover(eq(801L), eq(BatchExecutionStatus.SKIPPED), anyString());
    }

    @Test
    @DisplayName("R03 / R04 の未完了が両方あっても、新しい方（朝の開始）だけを実行する")
    void onlyTheNewestNetworkEventIsRecovered() {
        networkTasks();
        leftovers(row("batR03", "QUEUED", 802L, "2026-09-19T23:30:00"),
                row("batR04", "QUEUED", 803L, "2026-09-20T06:30:00"));

        recovery.recoverInterruptedExecutions();

        verify(executor).submit(eq("batR04"), eq(803L), any());
        verify(executor, never()).submit(eq("batR03"), anyLong(), any());
        verify(triggerStore).closeLeftover(eq(802L), eq(BatchExecutionStatus.SKIPPED), anyString());
    }

    @Test
    @DisplayName("L02 の古い計画実行点は実行しない（最新の 1 点に合并する）")
    void staleIntervalPointIsNotRecovered() {
        config(TaskSchedule.interval("batL02", true, 5, 1));
        leftovers(row("batL02", "QUEUED", 804L, "2026-09-20T08:36:00"));

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(triggerStore).closeLeftover(eq(804L), eq(BatchExecutionStatus.SKIPPED), anyString());
    }

    @Test
    @DisplayName("実行中（結果不明）はいまの計画なら、閉じてやり直しを作り、コミット後に投入する")
    void runningExecutionIsClosedAndRetriedInOneTransaction() {
        networkTasks();
        leftovers(row("batR04", "RUNNING", 806L, "2026-09-20T06:30:00"));
        when(triggerStore.recoverRunningExecution(eq(806L), eq("batR04"), eq("R"),
                eq(LocalDateTime.of(2026, 9, 20, 6, 30)), anyString(),
                eq(BatchExecutionRecovery.RETRY_CODE)))
                .thenReturn(new ScheduledTriggerStore.RecoveryResult(true, 807L, true));
        // やり直しの記録は**待機中**（投入の直前に状態を確かめる）
        when(executionMapper.findById(807L)).thenReturn(row("batR04", "QUEUED", 807L, "2026-09-20T06:30:00"));

        recovery.recoverInterruptedExecutions();

        // 旧実行の読み直し → 1 トランザクションの復旧 → コミット後に投入
        verify(triggerStore).recoverRunningExecution(eq(806L), eq("batR04"), eq("R"), any(), anyString(),
                eq(BatchExecutionRecovery.RETRY_CODE));
        verify(executor).submit(eq("batR04"), eq(807L), any());
    }

    @Test
    @DisplayName("実行中でも、もっと新しい計画実行点があるならやり直さない")
    void runningExecutionIsNotRetriedWhenSuperseded() {
        networkTasks();
        leftovers(row("batR03", "RUNNING", 808L, "2026-09-19T23:30:00"));

        recovery.recoverInterruptedExecutions();

        verify(triggerStore, never()).recoverRunningExecution(anyLong(), anyString(), anyString(), any(), anyString(), anyString());
        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(triggerStore).closeLeftover(eq(808L), eq(BatchExecutionStatus.FAILED), anyString());
    }

    @Test
    @DisplayName("スケジューラが管理しないタスクは今までどおり失敗として閉じるだけ")
    void otherTasksAreOnlyClosed() {
        networkTasks();
        leftovers(row("batS01", "RUNNING", 811L, "2026-09-20T06:30:00"));

        recovery.recoverInterruptedExecutions();

        verify(triggerStore).closeLeftover(eq(811L), eq(BatchExecutionStatus.FAILED), anyString());
        verify(executor, never()).submit(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("未完了が無ければ何もしない（以後も問い合わせしない）")
    void doesNothingWhenNothingIsUnfinished() {
        networkTasks();
        leftovers();

        recovery.recoverInterruptedExecutions();

        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
        verify(executor, never()).submit(anyString(), anyLong(), any());

        // 2 回目（周期検査から）は遺留リストも読み直さない
        recovery.retryPendingRecoveryIfDue();
        verify(executionMapper, times(1)).findLeftoversExceptRunId(CURRENT_RUN_ID);
    }

    // ------------------------------------------------------------------ 自動で続く

    @Test
    @DisplayName("起動時に設定が読めないときは保留し、設定が読めたら**再起動なしで**実行し直す")
    void keepsAndRetriesWhenTheConfigIsNotLoadedYet() {
        // 起動時: 設定がまだ読めていない → 判定できないので閉じない
        configNotLoaded();
        leftovers(row("batR04", "QUEUED", 820L, "2026-09-20T06:30:00"));
        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(triggerStore, never()).closeLeftover(anyLong(), any(), anyString());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.PENDING_REVIEW);
        assertThat(recovery.status().pendingCount()).isEqualTo(1);

        // 退避の間は DB を引かない
        recovery.retryPendingRecoveryIfDue();
        verify(executionMapper, times(1)).findById(820L);

        // 設定が読めるようになった（托底が成功した）→ 30 秒の検査で自動的に続く
        clock.advance(Duration.ofSeconds(31));
        config(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
        recovery.retryPendingRecoveryIfDue();

        verify(executor).submit(eq("batR04"), eq(820L), any());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("設定が無い・不正でも復旧では閉じない（直せば実行できるため）")
    void missingConfigIsKeptWaitingDuringRecovery() {
        config();   // どのタスクも設定が無い
        leftovers(row("batR04", "QUEUED", 821L, "2026-09-20T06:30:00"));

        recovery.recoverInterruptedExecutions();

        verify(triggerStore, never()).closeLeftover(anyLong(), any(), anyString());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.PENDING_REVIEW);
    }

    @Test
    @DisplayName("遺留リストの読み込みが失敗したら退避して読み直す（**帰属**で読むので自分の実行は入らない）")
    void discoveryFailureIsRetriedWithTheSameOwnershipFilter() {
        networkTasks();
        // 1 回目は失敗し、その間に現在のプロセスが実行を作った（自分の実行）
        BatchExecutionEntity own = ownRow("batR04", "RUNNING", 9999L, "2026-09-20T06:30:00");
        when(executionMapper.findLeftoversExceptRunId(CURRENT_RUN_ID))
                .thenThrow(new IllegalStateException("DB が混んでいます"))
                .thenReturn(List.of(row("batR04", "QUEUED", 830L, "2026-09-20T06:30:00")));
        when(executionMapper.findById(830L)).thenReturn(row("batR04", "QUEUED", 830L, "2026-09-20T06:30:00"));
        when(executionMapper.findById(9999L)).thenReturn(own);

        recovery.recoverInterruptedExecutions();
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.PENDING_DISCOVERY);
        verify(executor, never()).submit(anyString(), anyLong(), any());

        // 退避の間は読み直さない
        recovery.retryPendingRecoveryIfDue();
        verify(executionMapper, times(1)).findLeftoversExceptRunId(CURRENT_RUN_ID);

        clock.advance(Duration.ofSeconds(31));
        recovery.retryPendingRecoveryIfDue();

        // 読み直しでも**同じ帰属の条件**で読む（ID の大小では決めない）
        verify(executionMapper, times(2)).findLeftoversExceptRunId(CURRENT_RUN_ID);
        // 自分の実行（9999）は遺留に入らない → 実行し直さない・閉じない
        verify(executor, never()).submit(eq("batR04"), eq(9999L), any());
        verify(triggerStore, never()).closeLeftover(eq(9999L), any(), anyString());
        // 前のプロセスの遺留（830）は実行し直す
        verify(executor).submit(eq("batR04"), eq(830L), any());
    }

    @Test
    @DisplayName("このプロセスが作った実行（帰属が現在の識別子）は復旧しない")
    void executionsCreatedByThisProcessAreNotRecovered() {
        networkTasks();
        // 帰属が現在のプロセスの実行: 実行中でも、復旧の対象にしてはいけない
        BatchExecutionEntity own = ownRow("batR04", "RUNNING", 5001L, "2026-09-20T06:30:00");
        leftovers(own);

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(triggerStore, never()).closeLeftover(anyLong(), any(), anyString());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("遺留リストに紛れ込んだ自分の実行も、判定の直前に帰属で外す（二重の守り）")
    void ownExecutionIsDroppedAgainAtReviewTime() {
        networkTasks();
        // 遺留リストに自分の実行が入ってしまった状況を作る（読みのタイミング次第でありうる）
        BatchExecutionEntity own = ownRow("batR04", "QUEUED", 5002L, "2026-09-20T06:30:00");
        leftovers(own);

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(triggerStore, never()).closeLeftover(anyLong(), any(), anyString());
        assertThat(recovery.status().pendingCount()).isZero();
    }

    @Test
    @DisplayName("同じ遺留の行は 2 回処理しない（周期検査から何度呼ばれても投入は 1 回）")
    void eachLeftoverIsProcessedOnce() {
        networkTasks();
        leftovers(row("batR04", "QUEUED", 840L, "2026-09-20T06:30:00"));

        recovery.recoverInterruptedExecutions();
        clock.advance(Duration.ofSeconds(31));
        recovery.retryPendingRecoveryIfDue();

        verify(executor, times(1)).submit(eq("batR04"), eq(840L), any());
    }

    @Test
    @DisplayName("復旧が作ったやり直し（待機中）は、次の起動で拾って実行し直す（入隊前に落ちても漏れない）")
    void retryRowCreatedByRecoveryIsPickedUpByTheNextStart() {
        networkTasks();
        BatchExecutionEntity retry = row("batR04", "QUEUED", 900L, "2026-09-20T06:30:00");
        retry.setSourceExecutionId(806L);   // 前のプロセスの復旧が作ったやり直し
        leftovers(retry);

        recovery.recoverInterruptedExecutions();

        verify(executor).submit(eq("batR04"), eq(900L), any());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("保留の間に無効にされた実行は、設定が読めたあとの判定で閉じる（理由つき）")
    void disabledWhileWaitingIsClosedOnTheNextPass() {
        // 1 回目: 設定が読めない → 保留（閉じない）
        configNotLoaded();
        leftovers(row("batR04", "QUEUED", 860L, "2026-09-20T06:30:00"));
        recovery.recoverInterruptedExecutions();
        verify(triggerStore, never()).closeLeftover(anyLong(), any(), anyString());

        // 保留の間に利用者が無効にした（設定は読めるようになった）
        clock.advance(Duration.ofSeconds(31));
        config(TaskSchedule.daily("batR04", false, LocalTime.of(6, 30)));
        recovery.retryPendingRecoveryIfDue();

        verify(triggerStore).closeLeftover(eq(860L), eq(BatchExecutionStatus.SKIPPED), anyString());
        verify(executor, never()).submit(anyString(), anyLong(), any());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("保留の間に計画が過ぎた実行も、判定し直して閉じる（古い逆向きの操作を実行しない）")
    void supersededWhileWaitingIsClosedOnTheNextPass() {
        configNotLoaded();
        leftovers(row("batR03", "QUEUED", 861L, "2026-09-19T23:30:00"),
                row("batR04", "QUEUED", 862L, "2026-09-20T06:30:00"));
        recovery.recoverInterruptedExecutions();
        assertThat(recovery.status().pendingCount()).isEqualTo(2);

        // 設定が読めるようになった → 新しい方（朝の開始）だけ実行し、古い方（前夜の停止）は閉じる
        clock.advance(Duration.ofSeconds(31));
        networkTasks();
        recovery.retryPendingRecoveryIfDue();

        verify(executor).submit(eq("batR04"), eq(862L), any());
        verify(triggerStore).closeLeftover(eq(861L), eq(BatchExecutionStatus.SKIPPED), anyString());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("他の復旧が処理中の行（handled=false）は保留にして次のパスで確認する")
    void keepsWaitingWhenAnotherRecoveryIsHandlingTheRow() {
        networkTasks();
        leftovers(row("batR04", "RUNNING", 841L, "2026-09-20T06:30:00"));
        when(triggerStore.recoverRunningExecution(anyLong(), anyString(), anyString(), any(), anyString(), anyString()))
                .thenReturn(new ScheduledTriggerStore.RecoveryResult(false, null, false));

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.PENDING_REVIEW);
        assertThat(recovery.status().pendingCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("起動イベント・周期検査・手動の再読み込みが同時に来ても、遺留は 1 回だけ処理する")
    void concurrentTriggersProcessEachLeftoverOnce() throws Exception {
        networkTasks();
        leftovers(row("batR04", "QUEUED", 870L, "2026-09-20T06:30:00"));

        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(3);
        try {
            java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<?>> futures = List.of(
                    pool.submit(() -> {
                        start.await();
                        recovery.recoverInterruptedExecutions();
                        return null;
                    }),
                    pool.submit(() -> {
                        start.await();
                        recovery.retryPendingRecoveryIfDue();
                        return null;
                    }),
                    pool.submit(() -> {
                        start.await();
                        recovery.retryPendingRecoveryNow("設定の再読み込み");
                        return null;
                    }));
            start.countDown();
            for (java.util.concurrent.Future<?> future : futures) {
                future.get(10, java.util.concurrent.TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        // 同じ遺留を 2 回投入しない・2 回閉じない
        verify(executor, times(1)).submit(eq("batR04"), eq(870L), any());
        verify(triggerStore, never()).closeLeftover(anyLong(), any(), anyString());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    // ------------------------------------------------------------------ 整理してから投入する（競合の修正）

    @Test
    @DisplayName("同じタスクに古い記録と最新の記録: **古い方を閉じてから**最新を投入する（1 回だけ）")
    void closesTheOlderRecordBeforeSubmittingTheNewestOne() {
        config(TaskSchedule.interval("batL02", true, 5, 1));
        // 09:00 の時点で、最新の点は 08:56・その前は 08:51（古い方は「追い越された」で閉じる）
        leftovers(row("batL02", "QUEUED", 700L, "2026-09-20T08:51:00"),
                row("batL02", "QUEUED", 701L, "2026-09-20T08:56:00"));

        recovery.recoverInterruptedExecutions();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(triggerStore, executor);
        order.verify(triggerStore).closeLeftover(eq(700L), eq(BatchExecutionStatus.SKIPPED), anyString());
        order.verify(executor).submit(eq("batL02"), eq(701L), any());
        order.verifyNoMoreInteractions();
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("古い方が実行中（RUNNING）でも、先に閉じてから最新を投入する（最新は誤ってスキップされない）")
    void closesTheOlderRunningRecordBeforeSubmittingTheNewestOne() {
        config(TaskSchedule.interval("batL02", true, 5, 1));
        // 古い方は「追い越された」ので、結果不明として閉じるだけ（やり直しは作らない）
        leftovers(row("batL02", "RUNNING", 702L, "2026-09-20T08:51:00"),
                row("batL02", "QUEUED", 703L, "2026-09-20T08:56:00"));

        recovery.recoverInterruptedExecutions();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(triggerStore, executor);
        order.verify(triggerStore).closeLeftover(eq(702L), eq(BatchExecutionStatus.FAILED), anyString());
        order.verify(executor).submit(eq("batL02"), eq(703L), any());
        order.verifyNoMoreInteractions();
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("同じタスクに未決の遺留が残っている間は投入を**持ち越す**（スキップで失わせない）")
    void holdsSubmissionWhileTheSameTaskHasAnUnresolvedLeftover() {
        config(TaskSchedule.interval("batL02", true, 5, 1));
        leftovers(row("batL02", "QUEUED", 704L, "2026-09-20T08:51:00"),
                row("batL02", "QUEUED", 705L, "2026-09-20T08:56:00"));
        // 古い方を閉じるのに失敗する（＝未決が残る）
        when(triggerStore.closeLeftover(eq(704L), any(), anyString()))
                .thenThrow(new IllegalStateException("DB が混んでいます"))
                .thenReturn(true);

        recovery.recoverInterruptedExecutions();

        // 未決があるので**投入しない**（投入すると「前回が終わっていない」でスキップされ、失われる）
        verify(executor, never()).submit(anyString(), anyLong(), any());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.PENDING_REVIEW);
        assertThat(recovery.status().pendingCount()).isEqualTo(2);

        // 次のパス: 閉じられたら、最新を投入する（投入待ちは失われていない）
        clock.advance(Duration.ofSeconds(31));
        recovery.retryPendingRecoveryIfDue();

        // 閉じるのは 2 回（1 回目は失敗・2 回目で成功）。成功したあとに 1 回だけ投入する
        verify(triggerStore, times(2)).closeLeftover(eq(704L), eq(BatchExecutionStatus.SKIPPED), anyString());
        verify(executor, times(1)).submit(eq("batL02"), eq(705L), any());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("復旧トランザクションが失敗したら投入せず、次のパスで**同じやり直し**を 1 回だけ投入する")
    void retriesWhenTheRecoveryTransactionFailsWithoutDuplicatingTheRetry() {
        networkTasks();
        leftovers(row("batR04", "RUNNING", 706L, "2026-09-20T06:30:00"));
        when(executionMapper.findById(707L)).thenReturn(row("batR04", "QUEUED", 707L, "2026-09-20T06:30:00"));
        when(triggerStore.recoverRunningExecution(eq(706L), eq("batR04"), eq("R"), any(), anyString(),
                eq(BatchExecutionRecovery.RETRY_CODE)))
                .thenThrow(new IllegalStateException("DB が混んでいます"))
                .thenReturn(new ScheduledTriggerStore.RecoveryResult(true, 707L, true),
                        new ScheduledTriggerStore.RecoveryResult(true, 707L, false));

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        assertThat(recovery.status().pendingCount()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(31));
        recovery.retryPendingRecoveryIfDue();

        // やり直しの作成は 2 回目に成功。投入は**1 回だけ**（同じ実行IDを二重に走らせない）
        verify(executor, times(1)).submit(eq("batR04"), eq(707L), any());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("持ち越した投入待ちの記録が既に終わっていたら、投入せずに追跡から外す")
    void dropsThePendingDispatchWhenTheRecordIsAlreadyTerminal() {
        config(TaskSchedule.interval("batL02", true, 5, 1));
        BatchExecutionEntity stale = row("batL02", "QUEUED", 708L, "2026-09-20T08:51:00");
        BatchExecutionEntity fresh = row("batL02", "QUEUED", 709L, "2026-09-20T08:56:00");
        leftovers(stale, fresh);
        when(triggerStore.closeLeftover(eq(708L), any(), anyString()))
                .thenThrow(new IllegalStateException("DB が混んでいます"))
                .thenReturn(true);

        recovery.recoverInterruptedExecutions();   // 未決があるので持ち越し
        assertThat(recovery.status().pendingCount()).isEqualTo(2);

        // 次のパスまでに、その記録が別の経路で終わっていた（＝二重実行しない）
        fresh.setStatus(BatchExecutionStatus.SUCCESS.name());
        clock.advance(Duration.ofSeconds(31));
        recovery.retryPendingRecoveryIfDue();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        assertThat(recovery.status().pendingCount()).isZero();
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("設定が読めない間は両方とも保留し、読めたら古い方を閉じて最新を 1 回だけ投入する")
    void defersBothAndExecutesTheNewestAfterTheConfigRecovers() {
        configNotLoaded();
        leftovers(row("batL02", "QUEUED", 710L, "2026-09-20T08:51:00"),
                row("batL02", "QUEUED", 711L, "2026-09-20T08:56:00"));

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        assertThat(recovery.status().pendingCount()).isEqualTo(2);

        clock.advance(Duration.ofSeconds(31));
        config(TaskSchedule.interval("batL02", true, 5, 1));
        recovery.retryPendingRecoveryIfDue();

        verify(triggerStore).closeLeftover(eq(710L), eq(BatchExecutionStatus.SKIPPED), anyString());
        verify(executor, times(1)).submit(eq("batL02"), eq(711L), any());
    }

    // ------------------------------------------------------------------ 追跡の不変条件（照会失敗・暫緩・投入失敗）

    @Test
    @DisplayName("A: 復旧トランザクション成功後の照会が失敗しても、新しいやり直しは追跡に残り次で投入する")
    void keepsTheNewRetryTrackedWhenTheFirstLookupFails() {
        networkTasks();
        leftovers(row("batR04", "RUNNING", 720L, "2026-09-20T06:30:00"));
        // 復旧トランザクションは成功（旧実行を閉じ、やり直し 721 を作ってコミット済み）
        when(triggerStore.recoverRunningExecution(eq(720L), eq("batR04"), eq("R"), any(), anyString(),
                eq(BatchExecutionRecovery.RETRY_CODE)))
                .thenReturn(new ScheduledTriggerStore.RecoveryResult(true, 721L, true));
        // 1 回目の照会（状態確認）が失敗する
        when(executionMapper.findById(721L))
                .thenThrow(new IllegalStateException("DB が混んでいます"))
                .thenReturn(row("batR04", "QUEUED", 721L, "2026-09-20T06:30:00"));

        recovery.recoverInterruptedExecutions();

        // 照会に失敗したので投入しない。追跡には残っている（COMPLETED にしない）
        verify(executor, never()).submit(anyString(), anyLong(), any());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.PENDING_REVIEW);
        assertThat(recovery.status().pendingCount()).isEqualTo(1);

        // 次のパス: 同じ実行IDを投入する（やり直しを作り直さない）
        clock.advance(Duration.ofSeconds(31));
        recovery.retryPendingRecoveryIfDue();

        verify(executor, times(1)).submit(eq("batR04"), eq(721L), any());
        // 復旧トランザクションは 1 回だけ（やり直しを二重に作らない）
        verify(triggerStore, times(1)).recoverRunningExecution(eq(720L), eq("batR04"), eq("R"), any(),
                anyString(), eq(BatchExecutionRecovery.RETRY_CODE));
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("B: 同じパスで作った複数のやり直しは、1 つの照会失敗でも全部追跡に残り、次で 1 回ずつ投入する")
    void keepsAllRetriesTrackedWhenOneLookupFails() {
        // 2 つのタスク（R と L）で、それぞれ有効な実行中を復旧してやり直しを作る
        config(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)),
                TaskSchedule.interval("batL02", true, 5, 1));
        leftovers(row("batR04", "RUNNING", 730L, "2026-09-20T06:30:00"),
                row("batL02", "RUNNING", 731L, "2026-09-20T08:56:00"));
        when(triggerStore.recoverRunningExecution(eq(730L), eq("batR04"), eq("R"), any(), anyString(),
                eq(BatchExecutionRecovery.RETRY_CODE)))
                .thenReturn(new ScheduledTriggerStore.RecoveryResult(true, 732L, true));
        when(triggerStore.recoverRunningExecution(eq(731L), eq("batL02"), eq("R"), any(), anyString(),
                eq(BatchExecutionRecovery.RETRY_CODE)))
                .thenReturn(new ScheduledTriggerStore.RecoveryResult(true, 733L, true));
        // 732 の照会だけが失敗する（733 は待機中）
        when(executionMapper.findById(732L))
                .thenThrow(new IllegalStateException("DB が混んでいます"))
                .thenReturn(row("batR04", "QUEUED", 732L, "2026-09-20T06:30:00"));
        when(executionMapper.findById(733L)).thenReturn(row("batL02", "QUEUED", 733L, "2026-09-20T08:56:00"));

        recovery.recoverInterruptedExecutions();

        // 照会できた方は投入され、できなかった方も追跡に残る
        verify(executor, times(1)).submit(eq("batL02"), eq(733L), any());
        verify(executor, never()).submit(eq("batR04"), anyLong(), any());
        assertThat(recovery.status().pendingCount()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(31));
        recovery.retryPendingRecoveryIfDue();

        verify(executor, times(1)).submit(eq("batR04"), eq(732L), any());   // 1 回だけ
        verify(executor, times(1)).submit(eq("batL02"), eq(733L), any());   // 既に接収済みは再投入しない
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("C: 同じタスクの未決が 2 パス続いても持ち越しを失わず、片付いてから 1 回投入する")
    void keepsThePendingDispatchAcrossSeveralPasses() {
        config(TaskSchedule.interval("batL02", true, 5, 1));
        leftovers(row("batL02", "QUEUED", 740L, "2026-09-20T08:51:00"),
                row("batL02", "QUEUED", 741L, "2026-09-20T08:56:00"));
        // 古い方（追い越された記録）を閉じるのが 2 パス続けて失敗する（未決が残る）
        when(triggerStore.closeLeftover(eq(740L), any(), anyString()))
                .thenThrow(new IllegalStateException("DB が混んでいます"))
                .thenThrow(new IllegalStateException("まだ混んでいます"))
                .thenReturn(true);

        for (int pass = 0; pass < 2; pass++) {
            recovery.recoverInterruptedExecutions();
            recovery.recoverInterruptedExecutions();   // 同じパス内の再呼び出しでも二重に進めない
            verify(executor, never()).submit(anyString(), anyLong(), any());
            assertThat(recovery.status().pendingCount()).isEqualTo(2);   // 未決＋投入待ち
            assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.PENDING_REVIEW);
            clock.advance(Duration.ofSeconds(31));
        }

        // 未決が片付いた → 古い方を閉じて、有効な方を 1 回だけ投入する
        recovery.recoverInterruptedExecutions();

        verify(triggerStore, times(3)).closeLeftover(eq(740L), eq(BatchExecutionStatus.SKIPPED), anyString());
        verify(executor, times(1)).submit(eq("batL02"), eq(741L), any());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("D: 投入失敗は保留し、記録が明確に終わったら追跡から外す（業務を二重に走らせない）")
    void keepsOnSubmitFailureAndDropsWhenTerminal() {
        networkTasks();
        leftovers(row("batR04", "QUEUED", 750L, "2026-09-20T06:30:00"));
        // 1 回目の投入は想定外の例外
        org.mockito.Mockito.doThrow(new IllegalStateException("実行器が止まっています"))
                .doNothing().when(executor).submit(eq("batR04"), eq(750L), any());

        recovery.recoverInterruptedExecutions();

        verify(executor, times(1)).submit(eq("batR04"), eq(750L), any());   // 例外になった 1 回
        assertThat(recovery.status().pendingCount()).isEqualTo(1);          // 追跡には残る

        // 記録が別の経路で終わっていたら、投入せずに追跡から外す
        BatchExecutionEntity done = row("batR04", "SUCCESS", 750L, "2026-09-20T06:30:00");
        when(executionMapper.findById(750L)).thenReturn(done);
        clock.advance(Duration.ofSeconds(31));
        recovery.retryPendingRecoveryIfDue();

        verify(executor, times(1)).submit(eq("batR04"), eq(750L), any());   // 追加の投入はない
        assertThat(recovery.status().pendingCount()).isZero();
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    // ------------------------------------------------------------------ 自動運転のスイッチ

    @Test
    @DisplayName("自動復旧のスイッチ: 無効なら起動イベントでも周期検査でも進めない（明示呼び出しは動く）")
    void autoRecoverySwitchGatesOnlyTheAutomaticEntries() {
        networkTasks();
        leftovers(row("batR04", "QUEUED", 760L, "2026-09-20T06:30:00"));
        BatchExecutionRecovery manual = new BatchExecutionRecovery(executionMapper, triggerStore, executor,
                catalog, planGuard, configService, new ProcessRunIdOf(CURRENT_RUN_ID), false, clock);

        // 自動の入口（起動イベント・周期検査）は何もしない
        manual.recoverInterruptedExecutions();
        manual.retryPendingRecoveryIfDue();
        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(executionMapper, never()).findLeftoversExceptRunId(anyString());
        assertThat(manual.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.PENDING_DISCOVERY);

        // 明示的に呼べば動く（業務の入口はスイッチの影響を受けない）
        manual.retryPendingRecoveryNow("手動");
        verify(executor, times(1)).submit(eq("batR04"), eq(760L), any());
        assertThat(manual.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
    }

    @Test
    @DisplayName("判定は 1 パス 1 枚のスナップショットで行う（パスごとに取り直す）")
    void usesOneSnapshotPerPass() {
        networkTasks();
        leftovers(row("batR04", "QUEUED", 850L, "2026-09-20T06:30:00"),
                row("batR03", "QUEUED", 851L, "2026-09-19T23:30:00"));

        recovery.recoverInterruptedExecutions();

        // 2 行を同じ 1 枚で判定する（行ごとに取り直さない）
        verify(configService, times(1)).snapshot();
    }
}

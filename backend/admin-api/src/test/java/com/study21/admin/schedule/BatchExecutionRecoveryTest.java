package com.study21.admin.schedule;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import com.study21.admin.batch.BatchExecutionStatus;
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

    private static final long BOUNDARY = 5000L;

    private BatchExecutionMapper executionMapper;
    private ScheduledTriggerStore triggerStore;
    private BatchScheduleExecutor executor;
    private ScheduleConfigService configService;
    private ScheduleRuleCatalog catalog;
    private MutableClock clock;
    private BatchExecutionRecovery recovery;

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
        SchedulePlanGuard planGuard = new SchedulePlanGuard(catalog, triggerStore);
        recovery = new BatchExecutionRecovery(executionMapper, triggerStore, executor, catalog, planGuard,
                configService, clock);
        when(executionMapper.findMaxExecutionId()).thenReturn(BOUNDARY);
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

    private BatchExecutionEntity row(String taskCode, String status, Long id, String plannedAt) {
        BatchExecutionEntity entity = new BatchExecutionEntity();
        entity.setExecutionId(id);
        entity.setBatchCode(taskCode);
        entity.setStatus(status);
        entity.setTriggerType("R");
        entity.setScheduleTime(plannedAt);
        return entity;
    }

    /** 遺留リスト（起動時に読むもの）と、1 件ずつの読み直しを用意する。 */
    private void leftovers(BatchExecutionEntity... rows) {
        when(executionMapper.findUnfinishedBefore(BOUNDARY)).thenReturn(List.of(rows));
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

        // 2 回目（周期検査から）は境界も読み直さない
        recovery.retryPendingRecoveryIfDue();
        verify(executionMapper, times(1)).findMaxExecutionId();
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
    @DisplayName("遺留リストの読み込みが失敗したら退避して読み直す（境界は同じものを使う）")
    void discoveryFailureIsRetriedWithTheSameBoundary() {
        networkTasks();
        when(executionMapper.findMaxExecutionId())
                .thenThrow(new IllegalStateException("DB が混んでいます"))
                .thenReturn(BOUNDARY);
        when(executionMapper.findUnfinishedBefore(BOUNDARY))
                .thenReturn(List.of(row("batR04", "QUEUED", 830L, "2026-09-20T06:30:00")));
        when(executionMapper.findById(830L)).thenReturn(row("batR04", "QUEUED", 830L, "2026-09-20T06:30:00"));

        recovery.recoverInterruptedExecutions();
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.PENDING_DISCOVERY);
        verify(executor, never()).submit(anyString(), anyLong(), any());

        // 退避の間は読み直さない
        recovery.retryPendingRecoveryIfDue();
        verify(executionMapper, times(1)).findMaxExecutionId();

        clock.advance(Duration.ofSeconds(31));
        recovery.retryPendingRecoveryIfDue();

        verify(executionMapper, times(2)).findMaxExecutionId();
        verify(executionMapper).findUnfinishedBefore(BOUNDARY);   // 同じ境界で読む
        verify(executor).submit(eq("batR04"), eq(830L), any());
    }

    @Test
    @DisplayName("起動の境界より新しい実行（このプロセスが作ったもの）は復旧しない")
    void executionsCreatedByThisProcessAreNotRecovered() {
        networkTasks();
        // 境界（5000）より新しい = このプロセスが作った実行
        leftovers(row("batR04", "QUEUED", 5001L, "2026-09-20T06:30:00"));

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(triggerStore, never()).closeLeftover(anyLong(), any(), anyString());
        assertThat(recovery.status().phase()).isEqualTo(BatchExecutionRecovery.Phase.COMPLETED);
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

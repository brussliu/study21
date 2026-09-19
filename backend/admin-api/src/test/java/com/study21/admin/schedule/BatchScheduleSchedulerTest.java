package com.study21.admin.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * 統一スケジューラ（{@link BatchScheduleScheduler}）の振る舞い。
 *
 * <p>利用者の指示のうち、ここで固定するもの:</p>
 * <ol>
 *   <li>計画実行点が来たら「確保（claim）→ 実行記録 → バックグラウンド実行」の順に進む</li>
 *   <li>「いま以前で最後の 1 点」だけを確保する（取りこぼしを一括で補跑しない）</li>
 *   <li>無効なタスクは**計画だけ進める**（有効に戻しても古い点を実行しない）</li>
 *   <li>設定が無い・不正なタスクは実行しない（隠れた既定値を使わない）</li>
 *   <li>既に確保済みの点では何もしない（再起動・多重起動・ポーリングのゆらぎで二重実行しない）</li>
 *   <li>検査のスレッドは業務を待たない（実行器に渡すだけ）</li>
 * </ol>
 */
class BatchScheduleSchedulerTest {

    private static final ZoneId ZONE = ScheduleConfigService.ZONE;

    private ScheduleConfigService configService;
    private ScheduledTriggerStore triggerStore;
    private BatchScheduleExecutor executor;
    private BatchScheduleRecoveryStub recovery;
    private ScheduleRuleCatalog catalog;
    private SchedulePlanGuard planGuard;
    private BatchScheduleScheduler scheduler;

    /** 再起動の復旧の代役（周期検査から呼ばれることだけを確かめる）。 */
    private static final class BatchScheduleRecoveryStub extends BatchExecutionRecovery {
        private int retries;

        BatchScheduleRecoveryStub(ScheduleRuleCatalog catalog, ScheduledTriggerStore triggerStore) {
            super(mock(com.study21.admin.batch.BatchExecutionMapper.class), triggerStore,
                    mock(BatchScheduleExecutor.class), catalog,
                    new SchedulePlanGuard(catalog, triggerStore), mock(ScheduleConfigService.class),
                    new com.study21.admin.batch.ProcessRunId(
                            Clock.fixed(Instant.parse("2026-09-19T14:30:00Z"), ZONE)),
                    true,   // 自動運転のスイッチ（このテストでは有効）
                    Clock.fixed(Instant.parse("2026-09-19T14:30:00Z"), ZONE));
        }

        @Override
        public void retryPendingRecoveryIfDue() {
            retries++;
        }

        int retryCount() {
            return retries;
        }
    }

    @BeforeEach
    void setUp() {
        configService = mock(ScheduleConfigService.class);
        triggerStore = mock(ScheduledTriggerStore.class);
        executor = mock(BatchScheduleExecutor.class);
        catalog = new ScheduleRuleCatalog();
        // 実行してよいかの判定は本物を使う（スケジューラ・復旧・実行前で同じ規則であることを確かめる）
        planGuard = new SchedulePlanGuard(catalog, triggerStore);
        recovery = new BatchScheduleRecoveryStub(catalog, triggerStore);
        Clock clock = Clock.fixed(Instant.parse("2026-09-19T14:30:00Z"), ZONE);   // 2026-09-19 23:30 JST
        scheduler = new BatchScheduleScheduler(configService, triggerStore, executor,
                catalog, planGuard, recovery, 20, true, clock);
    }

    /** タスクぶんのスナップショット（指定しなかったタスクは「未設定」になる）。 */
    private ScheduleConfigSnapshot snapshot(TaskSchedule... schedules) {
        Map<String, TaskSchedule> tasks = new java.util.LinkedHashMap<>();
        Map<String, TaskConfigStatus> statuses = new java.util.LinkedHashMap<>();
        for (String code : new ScheduleRuleCatalog().taskCodes()) {
            statuses.put(code, TaskConfigStatus.MISSING);
        }
        for (TaskSchedule schedule : schedules) {
            tasks.put(schedule.taskCode(), schedule);
            statuses.put(schedule.taskCode(), TaskConfigStatus.LOADED);
        }
        return new ScheduleConfigSnapshot(3, Instant.parse("2026-09-19T14:00:00Z"), ZONE, tasks, statuses,
                Map.of(), null);
    }

    @Test
    @DisplayName("定時のタスクが時刻に達したら確保して実行を投入する（検査は業務を待たない）")
    void triggersDailyTaskWhenDue() {
        ScheduleConfigSnapshot snap = snapshot(TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);
        when(triggerStore.alreadyClaimed(eq("batR03"), any())).thenReturn(false);
        when(triggerStore.claimAndRecord(eq("batR03"), any(), eq("R"))).thenReturn(900L);

        BatchScheduleScheduler.ScheduleCheckResult result = scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        assertThat(result.triggered()).containsExactly("batR03@2026-09-19T23:30");
        ArgumentCaptor<LocalDateTime> plannedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(triggerStore).claimAndRecord(eq("batR03"), plannedAt.capture(), eq("R"));
        assertThat(plannedAt.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 19, 23, 30));
        verify(executor).submit(eq("batR03"), eq(900L), any());
    }

    @Test
    @DisplayName("循環のタスクは「ずらし + n×間隔」の最新の 1 点だけを確保する（一括の補跑をしない）")
    void triggersOnlyTheLatestIntervalPoint() {
        // 23:30（= 毎時 01/06/…/56 分の 31 分より後）→ 23:26 が最後の点
        ScheduleConfigSnapshot snap = snapshot(TaskSchedule.interval("batL02", true, 5, 1));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);
        when(triggerStore.claimAndRecord(eq("batL02"), any(), eq("L"))).thenReturn(901L);

        BatchScheduleScheduler.ScheduleCheckResult result = scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        ArgumentCaptor<LocalDateTime> plannedAt = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(triggerStore).claimAndRecord(eq("batL02"), plannedAt.capture(), eq("L"));
        assertThat(plannedAt.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 19, 23, 26));
        assertThat(result.triggered()).hasSize(1);
    }

    @Test
    @DisplayName("無効なタスクは計画だけ進める（有効に戻しても古い計画実行点を実行しない）")
    void disabledTaskOnlyAdvancesThePlan() {
        ScheduleConfigSnapshot snap = snapshot(TaskSchedule.daily("batR03", false, LocalTime.of(23, 30)));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);

        BatchScheduleScheduler.ScheduleCheckResult result = scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        verify(triggerStore).advanceWithoutRun("batR03", LocalDateTime.of(2026, 9, 19, 23, 30));
        verify(triggerStore, never()).claimAndRecord(anyString(), any(), anyString());
        verify(executor, never()).submit(anyString(), org.mockito.ArgumentMatchers.anyLong(), any());
        assertThat(result.triggered()).isEmpty();
    }

    @Test
    @DisplayName("設定が無い・不正なタスクは実行しない（隠れた既定値を使わない）。托底はタスクごとに 1 回")
    void doesNotRunTasksWithoutConfig() {
        ScheduleConfigSnapshot empty = snapshot();
        when(configService.snapshot()).thenReturn(empty);
        when(configService.ensureUsableConfig()).thenReturn(empty);

        BatchScheduleScheduler.ScheduleCheckResult result = scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        verify(triggerStore, never()).claimAndRecord(anyString(), any(), anyString());
        verify(triggerStore, never()).advanceWithoutRun(anyString(), any());
        assertThat(result.skipped()).containsExactlyInAnyOrder("batR03", "batR04", "batL02", "batL03");
    }

    @Test
    @DisplayName("既に確保済みの計画実行点では何もしない（再起動・多重起動・ゆらぎで二重実行しない）")
    void doesNothingWhenAlreadyClaimed() {
        ScheduleConfigSnapshot snap = snapshot(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);
        when(triggerStore.alreadyClaimed(eq("batR04"), any())).thenReturn(true);

        BatchScheduleScheduler.ScheduleCheckResult result = scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        assertThat(result.triggered()).isEmpty();
        verify(triggerStore, never()).claimAndRecord(anyString(), any(), anyString());
        verify(executor, never()).submit(anyString(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("別の実行が先に確保したとき（claim が null）は実行しない")
    void doesNotRunWhenAnotherExecutionClaimedFirst() {
        ScheduleConfigSnapshot snap = snapshot(TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);
        when(triggerStore.claimAndRecord(eq("batR03"), any(), eq("R"))).thenReturn(null);

        BatchScheduleScheduler.ScheduleCheckResult result = scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        assertThat(result.triggered()).isEmpty();
        verify(executor, never()).submit(anyString(), org.mockito.ArgumentMatchers.anyLong(), any());
    }

    @Test
    @DisplayName("検査のたびに設定を読み直さない（メモリにあるタスクは托底を呼ばない）")
    void doesNotTouchTheLoaderWhenConfigIsInMemory() {
        ScheduleConfigSnapshot snap = snapshot(
                TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)),
                TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)),
                TaskSchedule.interval("batL02", true, 5, 1),
                TaskSchedule.interval("batL03", true, 5, 0));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);
        when(triggerStore.alreadyClaimed(anyString(), any())).thenReturn(true);

        scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        // 設定はすべてメモリにある → 托底の読み込みも再読み込みもしない
        verify(configService, never()).ensureTaskConfig(anyString());
        verify(configService, never()).refresh(anyString());
    }

    @Test
    @DisplayName("検査の前に、反映できていない設定の自動再試行を促す")
    void retriesPendingRefreshBeforeChecking() {
        ScheduleConfigSnapshot empty = snapshot();
        when(configService.snapshot()).thenReturn(empty);
        when(configService.ensureUsableConfig()).thenReturn(empty);

        scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        verify(configService).retryPendingIfDue();
    }

    @Test
    @DisplayName("検査の結果に、使った設定の版と実行待ちの本数が入る")
    void reportsCheckedAtAndVersion() {
        ScheduleConfigSnapshot snap = snapshot(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);
        when(triggerStore.claimAndRecord(eq("batR04"), any(), eq("R"))).thenReturn(902L);
        when(executor.queuedCount()).thenReturn(1);

        BatchScheduleScheduler.ScheduleCheckResult result = scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        assertThat(result.checkedAt()).isEqualTo(LocalDateTime.of(2026, 9, 19, 23, 30));
        assertThat(result.configVersion()).isEqualTo(3);
        assertThat(result.queuedWorkers()).isEqualTo(1);
    }

    @Test
    @DisplayName("自動運転のスイッチ: 無効なら定期検査は何もしない（手動で動かす環境のため）")
    void autoRunSwitchDisablesThePeriodicCheck() {
        BatchScheduleScheduler disabled = new BatchScheduleScheduler(configService, triggerStore, executor,
                catalog, planGuard, recovery, 20, false,
                Clock.fixed(Instant.parse("2026-09-19T14:30:00Z"), ZONE));

        disabled.checkDueTasks();

        // 設定も計画も実行器も触らない（復旧の再試行も促さない）
        verifyNoInteractions(configService, triggerStore, executor);
        assertThat(recovery.retryCount()).isZero();
    }

    @Test
    @DisplayName("自動運転のスイッチ: 有効（既定）なら定期検査は今までどおり動く")
    void autoRunSwitchEnabledKeepsThePeriodicCheck() {
        ScheduleConfigSnapshot empty = snapshot();
        when(configService.snapshot()).thenReturn(empty);
        when(configService.ensureUsableConfig()).thenReturn(empty);

        scheduler.checkDueTasks();

        assertThat(recovery.retryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("検査が例外でも外に投げない（スケジューラを止めない）")
    void checkDueTasksSwallowsExceptions() {
        when(configService.snapshot()).thenThrow(new IllegalStateException("壊れた"));
        assertThatCode(scheduler::checkDueTasks).doesNotThrowAnyException();
    }

    // ------------------------------------------------------------------ 設定変更・復帰の規則

    @Test
    @DisplayName("夜間に停止していて朝に復帰したら、最新の 1 点（朝の開始）だけを実行する（両方は実行しない）")
    void nightRestartAppliesOnlyTheNewestNetworkEvent() {
        ScheduleConfigSnapshot snap = snapshot(
                TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)),
                TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);
        when(triggerStore.claimAndRecord(eq("batR04"), any(), eq("R"))).thenReturn(910L);

        // 2026-09-20 09:00 JST（前夜 23:30 の停止と当日 06:30 の開始の両方が未実行）
        BatchScheduleScheduler.ScheduleCheckResult result =
                scheduler.runOnce(Instant.parse("2026-09-20T00:00:00Z"));

        // 新しい点（当日 06:30 の開始）だけを実行する
        ArgumentCaptor<LocalDateTime> planned = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(triggerStore).claimAndRecord(eq("batR04"), planned.capture(), eq("R"));
        assertThat(planned.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 20, 6, 30));
        verify(executor).submit(eq("batR04"), eq(910L), any());
        assertThat(result.triggered()).containsExactly("batR04@2026-09-20T06:30");
        // 古い点（前夜 23:30 の停止）は**実行せずに計画だけ進める**
        verify(triggerStore).advanceWithoutRun("batR03", LocalDateTime.of(2026, 9, 19, 23, 30));
        verify(triggerStore, never()).claimAndRecord(eq("batR03"), any(), anyString());
    }

    @Test
    @DisplayName("停止時刻を過ぎて復帰したら、その日の停止だけを実行する（逆向きの開始は実行しない）")
    void restartAfterTheStopTimeAppliesTheStopOnly() {
        ScheduleConfigSnapshot snap = snapshot(
                TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)),
                TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);
        when(triggerStore.claimAndRecord(eq("batR03"), any(), eq("R"))).thenReturn(911L);

        // 2026-09-19 23:50 JST
        BatchScheduleScheduler.ScheduleCheckResult result =
                scheduler.runOnce(Instant.parse("2026-09-19T14:50:00Z"));

        ArgumentCaptor<LocalDateTime> planned = ArgumentCaptor.forClass(LocalDateTime.class);
        verify(triggerStore).claimAndRecord(eq("batR03"), planned.capture(), eq("R"));
        assertThat(planned.getValue()).isEqualTo(LocalDateTime.of(2026, 9, 19, 23, 30));
        verify(triggerStore).advanceWithoutRun("batR04", LocalDateTime.of(2026, 9, 19, 6, 30));
        assertThat(result.triggered()).containsExactly("batR03@2026-09-19T23:30");
    }

    @Test
    @DisplayName("設定を変えた直後は、適用時刻より前の点を実行しない（22:00 に 23:30→21:00 でも止めない）")
    void doesNotRunPointsBeforeTheConfigEffectiveFrom() {
        TaskSchedule r03 = TaskSchedule.daily("batR03", true, LocalTime.of(21, 0))
                .withEffectiveFrom(LocalDateTime.of(2026, 9, 19, 22, 0));
        ScheduleConfigSnapshot snap = snapshot(r03);
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);

        // 2026-09-19 22:30 JST（今日の 21:00 は「設定を変えた 22:00」より前）
        BatchScheduleScheduler.ScheduleCheckResult result =
                scheduler.runOnce(Instant.parse("2026-09-19T13:30:00Z"));

        assertThat(result.triggered()).isEmpty();
        verify(triggerStore, never()).claimAndRecord(anyString(), any(), anyString());
        verify(executor, never()).submit(anyString(), org.mockito.ArgumentMatchers.anyLong(), any());
        // 実行しない点は計画だけ進める（明日の 21:00 から通常どおり）
        verify(triggerStore).advanceWithoutRun("batR03", LocalDateTime.of(2026, 9, 19, 21, 0));
    }

    @Test
    @DisplayName("循環のタスクも適用時刻より前の点は実行しない（間隔を変えた直後に走らせない）")
    void intervalTaskDoesNotRunAPointBeforeTheConfigEffectiveFrom() {
        TaskSchedule l02 = TaskSchedule.interval("batL02", true, 5, 1)
                .withEffectiveFrom(LocalDateTime.of(2026, 9, 19, 23, 28));
        ScheduleConfigSnapshot snap = snapshot(l02);
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);

        // 23:30 JST（この設定での最後の点は 23:26 = 適用時刻より前）
        BatchScheduleScheduler.ScheduleCheckResult result =
                scheduler.runOnce(Instant.parse("2026-09-19T14:30:00Z"));

        assertThat(result.triggered()).isEmpty();
        verify(triggerStore, never()).claimAndRecord(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("もう一方のネット切替が実行中なら、今回の点は見送る（点は確保しない＝次の検査で再挑戦）")
    void defersWhenTheSiblingNetworkTaskIsRunning() {
        ScheduleConfigSnapshot snap = snapshot(
                TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));
        when(configService.snapshot()).thenReturn(snap);
        when(configService.ensureUsableConfig()).thenReturn(snap);
        when(triggerStore.isTaskRunning("batR03")).thenReturn(true);

        BatchScheduleScheduler.ScheduleCheckResult result =
                scheduler.runOnce(Instant.parse("2026-09-20T00:00:00Z"));

        assertThat(result.deferred()).containsExactly("batR04");
        assertThat(result.triggered()).isEmpty();
        verify(triggerStore, never()).claimAndRecord(anyString(), any(), anyString());
    }
}

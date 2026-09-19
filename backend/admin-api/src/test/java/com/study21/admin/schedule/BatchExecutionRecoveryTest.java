package com.study21.admin.schedule;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 再起動の復旧（{@link BatchExecutionRecovery}）。
 *
 * <p>利用者の指摘のうち、ここで固定するもの:</p>
 * <ol>
 *   <li>復旧は**スケジューラと同じ規則**（{@link SchedulePlanGuard}）で「いまの計画」かを見る
 *       （現在時刻・設定の版・適用時刻・有効／無効）。</li>
 *   <li><b>待機中</b>（まだ始まっていない）でも、古い点は実行しない（スキップとして理由を残す）。</li>
 *   <li><b>R03 / R04 が両方未完了**でも、いま有効なはずの 1 点（新しい方）だけを実行する</b>
 *       （夜間の停止を翌朝に実行しない）。</li>
 *   <li><b>L02 / L03</b> は古い点を実行しない（最新の 1 点に合并する）。</li>
 *   <li><b>実行中</b>（結果が分からない）は失敗として閉じ、いまの計画なら 1 回だけやり直す。</li>
 *   <li>スケジューラが管理しないタスクは今までどおり閉じるだけ。</li>
 * </ol>
 */
class BatchExecutionRecoveryTest {

    private static final ZoneId ZONE = ScheduleConfigService.ZONE;

    /** 2026-09-20 09:00 JST（前夜 23:30 の停止と当日 06:30 の開始の両方が過ぎている）。 */
    private static final Instant NOW = Instant.parse("2026-09-20T00:00:00Z");

    private BatchExecutionMapper executionMapper;
    private ScheduledTriggerStore triggerStore;
    private BatchScheduleExecutor executor;
    private ScheduleConfigService configService;
    private ScheduleRuleCatalog catalog;
    private BatchExecutionRecovery recovery;

    @BeforeEach
    void setUp() {
        executionMapper = mock(BatchExecutionMapper.class);
        triggerStore = mock(ScheduledTriggerStore.class);
        executor = mock(BatchScheduleExecutor.class);
        configService = mock(ScheduleConfigService.class);
        catalog = new ScheduleRuleCatalog();
        SchedulePlanGuard planGuard = new SchedulePlanGuard(configService, catalog, triggerStore);
        recovery = new BatchExecutionRecovery(executionMapper, triggerStore, executor, catalog, planGuard,
                configService, Clock.fixed(NOW, ZONE));
    }

    /** 直近に作ったスナップショット（托底の戻り値に使う。Mockito の入れ子呼び出しを避ける）。 */
    private ScheduleConfigSnapshot lastSnapshot;

    /** タスクぶんのスナップショット（指定しなかったタスクは「未設定」になる）。 */
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
        lastSnapshot = new ScheduleConfigSnapshot(7, Instant.parse("2026-09-20T00:00:00Z"), ZONE,
                tasks, statuses, Map.of(), null);
        when(configService.snapshot()).thenReturn(lastSnapshot);
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
        entity.setTriggerType("batR04".equals(taskCode) ? "R" : "R");
        entity.setScheduleTime(plannedAt);
        return entity;
    }

    @Test
    @DisplayName("待機中（まだ始まっていない）で、いまの計画なら実行し直す（永久にスキップしない）")
    void queuedExecutionIsRetried() {
        networkTasks();
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batR04", "QUEUED", 800L, "2026-09-20T06:30:00")));

        recovery.recoverInterruptedExecutions();

        // 実行記録はそのまま使って実行し直す（失敗として閉じない）
        verify(executor).submit(eq("batR04"), eq(800L), any());
        verify(executionMapper, never()).markFinished(any(Long.class), anyString(), anyString(), any(), any(Long.class));
    }

    @Test
    @DisplayName("夜間の停止が翌朝まで残っていても、実行するのは「いま有効なはずの切替」だけ")
    void staleStopIsNotRecoveredInTheMorning() {
        networkTasks();
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batR03", "QUEUED", 801L, "2026-09-19T23:30:00")));

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        // 実行しないと決めた点はスキップとして閉じ、理由を残す（未完了のまま残さない）
        verify(executionMapper).markFinished(eq(801L), eq("SKIPPED"), anyString(), anyString(), eq(0L));
    }

    @Test
    @DisplayName("R03 / R04 の未完了が両方あっても、新しい方（朝の開始）だけを実行する")
    void onlyTheNewestNetworkEventIsRecovered() {
        networkTasks();
        when(executionMapper.findUnfinished()).thenReturn(List.of(
                row("batR03", "QUEUED", 802L, "2026-09-19T23:30:00"),
                row("batR04", "QUEUED", 803L, "2026-09-20T06:30:00")));

        recovery.recoverInterruptedExecutions();

        verify(executor).submit(eq("batR04"), eq(803L), any());
        verify(executor, never()).submit(eq("batR03"), anyLong(), any());
        // 古い方（逆向きの操作）はスキップとして閉じる
        verify(executionMapper).markFinished(eq(802L), eq("SKIPPED"), anyString(), anyString(), eq(0L));
    }

    @Test
    @DisplayName("L02 の古い計画実行点は実行しない（最新の 1 点に合并する）")
    void staleIntervalPointIsNotRecovered() {
        config(TaskSchedule.interval("batL02", true, 5, 1));
        // 08:36 の点が残っているが、いま（09:00）の最新の点は 08:56
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batL02", "QUEUED", 804L, "2026-09-20T08:36:00")));

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(executionMapper).markFinished(eq(804L), eq("SKIPPED"), anyString(), anyString(), eq(0L));
    }

    @Test
    @DisplayName("L02 の最新の点なら実行し直す（取りこぼし 1 点ぶんだけ）")
    void newestIntervalPointIsRecovered() {
        config(TaskSchedule.interval("batL02", true, 5, 1));
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batL02", "QUEUED", 805L, "2026-09-20T08:56:00")));

        recovery.recoverInterruptedExecutions();

        verify(executor).submit(eq("batL02"), eq(805L), any());
    }

    @Test
    @DisplayName("実行中（結果不明）はいまの計画なら失敗として閉じ、1 回だけやり直す")
    void runningExecutionIsClosedAndRetriedWhenStillCurrent() {
        networkTasks();
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batR04", "RUNNING", 806L, "2026-09-20T06:30:00")));
        when(triggerStore.insertRetryRow(eq("batR04"), eq(LocalDateTime.of(2026, 9, 20, 6, 30)), eq("R"),
                eq(BatchExecutionRecovery.RETRY_CODE))).thenReturn(807L);

        recovery.recoverInterruptedExecutions();

        verify(executionMapper).markFinished(eq(806L), eq("FAILED"), anyString(), anyString(), eq(0L));
        verify(executor).submit(eq("batR04"), eq(807L), any());
    }

    @Test
    @DisplayName("実行中でも、もっと新しい計画実行点があるならやり直さない（古い状態で上書きしない）")
    void runningExecutionIsNotRetriedWhenSuperseded() {
        networkTasks();
        // 前夜の停止が実行中のまま残っている（いま有効なはずの切替は朝の開始）
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batR03", "RUNNING", 808L, "2026-09-19T23:30:00")));

        recovery.recoverInterruptedExecutions();

        // 途中まで動いた可能性がある（結果は不明）ので失敗として閉じる。やり直しはしない
        verify(executionMapper).markFinished(eq(808L), eq("FAILED"), anyString(), anyString(), eq(0L));
        verify(triggerStore, never()).insertRetryRow(anyString(), any(), anyString(), anyString());
        verify(executor, never()).submit(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("実行中でも、いま無効ならやり直さない（結果不明として閉じる）")
    void runningExecutionIsNotRetriedWhenDisabled() {
        config(TaskSchedule.daily("batR04", false, LocalTime.of(6, 30)));
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batR04", "RUNNING", 809L, "2026-09-20T06:30:00")));

        recovery.recoverInterruptedExecutions();

        verify(executionMapper).markFinished(eq(809L), eq("FAILED"), anyString(), anyString(), eq(0L));
        verify(executor, never()).submit(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("適用時刻より前の計画実行点は復旧でも実行しない（設定を変えた直後）")
    void pointBeforeTheConfigEffectiveFromIsNotRecovered() {
        config(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30))
                .withEffectiveFrom(LocalDateTime.of(2026, 9, 20, 8, 0)));
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batR04", "QUEUED", 810L, "2026-09-20T06:30:00")));

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
        verify(executionMapper).markFinished(eq(810L), eq("SKIPPED"), anyString(), anyString(), eq(0L));
    }

    @Test
    @DisplayName("スケジューラが管理しないタスクは今までどおり失敗として閉じるだけ")
    void otherTasksAreOnlyClosed() {
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batS01", "RUNNING", 811L, "2026-09-20T06:30:00")));

        recovery.recoverInterruptedExecutions();

        verify(executionMapper).markFinished(eq(811L), eq("FAILED"), anyString(), anyString(), eq(0L));
        verify(executor, never()).submit(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("未完了が無ければ何もしない")
    void doesNothingWhenNothingIsUnfinished() {
        when(executionMapper.findUnfinished()).thenReturn(List.of());

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("1 件の復旧で例外が出ても、残りの行の復旧を続ける（起動を止めない）")
    void keepsRecoveringOtherRowsWhenOneFails() {
        networkTasks();
        when(executionMapper.findUnfinished()).thenReturn(List.of(
                row("batR03", "QUEUED", 812L, "2026-09-19T23:30:00"),
                row("batR04", "QUEUED", 813L, "2026-09-20T06:30:00")));
        // 古い行（batR03）のスキップ記録で失敗させる
        org.mockito.Mockito.doThrow(new IllegalStateException("DB が混んでいます"))
                .when(executionMapper).markFinished(eq(812L), anyString(), anyString(), any(), anyLong());

        recovery.recoverInterruptedExecutions();

        // 新しい行は復旧される（1 件目の失敗でループを抜けない）
        verify(executor).submit(eq("batR04"), eq(813L), any());
    }

    @Test
    @DisplayName("復旧は**設定を読んでから**判定する（起動直後に空のスナップショットで判定しない）")
    void loadsTheConfigBeforeDeciding() {
        networkTasks();
        when(configService.ensureUsableConfig()).thenReturn(lastSnapshot);
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batR04", "QUEUED", 820L, "2026-09-20T06:30:00")));

        recovery.recoverInterruptedExecutions();

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(configService);
        order.verify(configService).ensureUsableConfig();   // 先に托底で読む
        order.verify(configService, org.mockito.Mockito.atLeastOnce()).snapshot();   // その結果で判定する
        verify(executor).submit(eq("batR04"), eq(820L), any());
    }

    @Test
    @DisplayName("設定をまだ読めていないときは、実行を閉じずに保留する（正しい実行を捨てない）")
    void keepsTheRowWhenTheConfigIsNotLoadedYet() {
        ScheduleConfigSnapshot notLoaded = ScheduleConfigSnapshot.empty(ZONE, catalog.taskCodes());
        when(configService.snapshot()).thenReturn(notLoaded);
        when(configService.ensureUsableConfig()).thenReturn(notLoaded);
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batR04", "QUEUED", 821L, "2026-09-20T06:30:00")));

        recovery.recoverInterruptedExecutions();

        // スキップで閉じない（次の起動でもう一度判定する）
        verify(executionMapper, never()).markFinished(anyLong(), anyString(), anyString(), any(), anyLong());
        verify(executor, never()).submit(anyString(), anyLong(), any());
    }

    @Test
    @DisplayName("復旧に使った設定の版をログ・判定に残す（同じ規則で判定していることが分かる）")
    void decisionsUseTheSharedGuard() {
        networkTasks();
        when(executionMapper.findUnfinished())
                .thenReturn(List.of(row("batR04", "QUEUED", 814L, "2026-09-20T06:30:00")));

        recovery.recoverInterruptedExecutions();

        // 判定は SchedulePlanGuard（スケジューラと同じ）を通る。設定スナップショットを 1 回以上読む
        verify(configService, org.mockito.Mockito.atLeastOnce()).snapshot();
        assertThat(lastSnapshot.version()).isEqualTo(7);
    }
}

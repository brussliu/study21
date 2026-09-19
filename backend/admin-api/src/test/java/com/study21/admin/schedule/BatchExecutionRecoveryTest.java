package com.study21.admin.schedule;

import com.study21.admin.batch.BatchControlEntity;
import com.study21.admin.batch.BatchControlMapper;
import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchExecutionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
 *   <li><b>待機中</b>（まだ始まっていない）は失敗にせず**実行し直す**（永久にスキップしない）</li>
 *   <li><b>実行中</b>（結果が分からない）は失敗として閉じ、**まだいまの計画点でタスクが有効**なら
 *       1 回だけやり直す（新しい点に追い越されていればやり直さない）</li>
 *   <li>無効なタスクは実行しない</li>
 *   <li>スケジューラが管理しないタスクは今までどおり閉じるだけ</li>
 * </ol>
 */
class BatchExecutionRecoveryTest {

    private BatchExecutionMapper executionMapper;
    private BatchControlMapper controlMapper;
    private SchedulePlanMapper planMapper;
    private ScheduledTriggerStore triggerStore;
    private BatchScheduleExecutor executor;
    private BatchExecutionRecovery recovery;

    private static final LocalDateTime PLANNED = LocalDateTime.of(2026, 9, 19, 23, 30);

    @BeforeEach
    void setUp() {
        executionMapper = mock(BatchExecutionMapper.class);
        controlMapper = mock(BatchControlMapper.class);
        planMapper = mock(SchedulePlanMapper.class);
        triggerStore = mock(ScheduledTriggerStore.class);
        executor = mock(BatchScheduleExecutor.class);
        recovery = new BatchExecutionRecovery(executionMapper, controlMapper, planMapper,
                triggerStore, executor, new ScheduleRuleCatalog());
    }

    private BatchExecutionEntity row(String taskCode, String status, Long id) {
        BatchExecutionEntity entity = new BatchExecutionEntity();
        entity.setExecutionId(id);
        entity.setBatchCode(taskCode);
        entity.setStatus(status);
        entity.setTriggerType("R");
        entity.setScheduleTime(PLANNED.toString());
        return entity;
    }

    private void enabled(String taskCode, boolean active) {
        BatchControlEntity control = new BatchControlEntity();
        control.setBatchCode(taskCode);
        control.setStatus(active ? "1" : "0");
        when(controlMapper.findByBatchCode(taskCode)).thenReturn(control);
    }

    private void currentPlan(String taskCode, LocalDateTime plannedAt) {
        when(planMapper.findPlan(taskCode)).thenReturn(Map.of(
                "batchCode", taskCode,
                "lastPlannedAt", java.sql.Timestamp.valueOf(plannedAt)));
    }

    @Test
    @DisplayName("待機中（まだ始まっていない）は実行し直す（永久にスキップしない）")
    void queuedExecutionIsRetried() {
        when(executionMapper.findUnfinished()).thenReturn(List.of(row("batR03", "QUEUED", 800L)));
        enabled("batR03", true);

        recovery.recoverInterruptedExecutions();

        // 実行記録はそのまま使って実行し直す（失敗として閉じない）
        verify(executor).submit("batR03", 800L);
        verify(executionMapper, never()).markFinished(any(Long.class), anyString(), anyString(), any(), any(Long.class));
    }

    @Test
    @DisplayName("待機中でも、いま無効なら実行しない（スキップとして記録する）")
    void queuedExecutionIsSkippedWhenDisabled() {
        when(executionMapper.findUnfinished()).thenReturn(List.of(row("batR03", "QUEUED", 801L)));
        enabled("batR03", false);

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), any(Long.class));
        verify(executionMapper).markFinished(eq(801L), eq("SKIPPED"), anyString(), anyString(), eq(0L));
    }

    @Test
    @DisplayName("実行中（結果不明）は失敗として閉じ、まだ現在の計画点なら 1 回だけやり直す")
    void runningExecutionIsClosedAndRetriedWhenStillCurrent() {
        when(executionMapper.findUnfinished()).thenReturn(List.of(row("batR03", "RUNNING", 802L)));
        enabled("batR03", true);
        currentPlan("batR03", PLANNED);
        when(triggerStore.insertRetryRow(eq("batR03"), eq(PLANNED), eq("R"),
                eq(BatchExecutionRecovery.RETRY_CODE))).thenReturn(803L);

        recovery.recoverInterruptedExecutions();

        verify(executionMapper).markFinished(eq(802L), eq("FAILED"), anyString(), anyString(), eq(0L));
        verify(triggerStore).insertRetryRow(eq("batR03"), eq(PLANNED), eq("R"),
                eq(BatchExecutionRecovery.RETRY_CODE));
        verify(executor).submit("batR03", 803L);
    }

    @Test
    @DisplayName("実行中でも、新しい計画点に追い越されていたらやり直さない（古い状態で上書きしない）")
    void runningExecutionIsNotRetriedWhenSuperseded() {
        when(executionMapper.findUnfinished()).thenReturn(List.of(row("batR03", "RUNNING", 804L)));
        enabled("batR03", true);
        currentPlan("batR03", PLANNED.plusDays(1));   // 既に次の点を確保済み

        recovery.recoverInterruptedExecutions();

        verify(executionMapper).markFinished(eq(804L), eq("FAILED"), anyString(), anyString(), eq(0L));
        verify(triggerStore, never()).insertRetryRow(anyString(), any(), anyString(), anyString());
        verify(executor, never()).submit(anyString(), any(Long.class));
    }

    @Test
    @DisplayName("実行中でも、いま無効ならやり直さない")
    void runningExecutionIsNotRetriedWhenDisabled() {
        when(executionMapper.findUnfinished()).thenReturn(List.of(row("batR04", "RUNNING", 805L)));
        enabled("batR04", false);
        currentPlan("batR04", PLANNED);

        recovery.recoverInterruptedExecutions();

        verify(executionMapper).markFinished(eq(805L), eq("FAILED"), anyString(), anyString(), eq(0L));
        verify(executor, never()).submit(anyString(), any(Long.class));
    }

    @Test
    @DisplayName("スケジューラが管理しないタスクは今までどおり失敗として閉じるだけ")
    void otherTasksAreOnlyClosed() {
        when(executionMapper.findUnfinished()).thenReturn(List.of(row("batS01", "RUNNING", 806L)));

        recovery.recoverInterruptedExecutions();

        verify(executionMapper).markFinished(eq(806L), eq("FAILED"), anyString(), anyString(), eq(0L));
        verify(executor, never()).submit(anyString(), any(Long.class));
        verify(controlMapper, never()).findByBatchCode(anyString());
    }

    @Test
    @DisplayName("未完了が無ければ何もしない")
    void doesNothingWhenNothingIsUnfinished() {
        when(executionMapper.findUnfinished()).thenReturn(List.of());

        recovery.recoverInterruptedExecutions();

        verify(executor, never()).submit(anyString(), any(Long.class));
        assertThat(true).isTrue();
    }

    @Test
    @DisplayName("1 件の復旧で例外が出ても、残りの行の復旧を続ける（起動を止めない）")
    void keepsRecoveringOtherRowsWhenOneFails() {
        when(executionMapper.findUnfinished()).thenReturn(List.of(
                row("batR03", "QUEUED", 810L),
                row("batR04", "QUEUED", 811L)));
        enabled("batR03", true);
        enabled("batR04", true);
        // 1 件目（batR03）の投入だけ失敗させる
        org.mockito.Mockito.doThrow(new IllegalStateException("実行器が止まっています"))
                .when(executor).submit("batR03", 810L);

        recovery.recoverInterruptedExecutions();

        // 2 件目は実行し直される（1 件目の失敗でループを抜けない）
        verify(executor).submit("batR04", 811L);
    }
}

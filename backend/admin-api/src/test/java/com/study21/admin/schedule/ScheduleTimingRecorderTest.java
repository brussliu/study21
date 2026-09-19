package com.study21.admin.schedule;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 実行設定の変更の記録（{@link ScheduleTimingRecorder}）。
 *
 * <p>設定値の保存と**同じトランザクション**で「適用時刻＋計画バージョン」を書く側。
 * どの入口（設定ページの保存・有効／無効の切替）から来ても**同じ規則**で、
 * **変わったタスクだけ**を記録することを固定する。</p>
 */
class ScheduleTimingRecorderTest {

    private static final ZoneId ZONE = ScheduleConfigService.ZONE;

    private SchedulePlanMapper planMapper;
    private ScheduleTimingRecorder recorder;

    @BeforeEach
    void setUp() {
        planMapper = mock(SchedulePlanMapper.class);
        // 2026-09-19 22:00 JST
        recorder = new ScheduleTimingRecorder(planMapper, new ScheduleRuleCatalog(),
                Clock.fixed(Instant.parse("2026-09-19T13:00:00Z"), ZONE));
    }

    @Test
    @DisplayName("時刻を変えたら、そのタスクの適用時刻と計画バージョンを記録する")
    void recordsTheTaskWhoseTimeChanged() {
        var recorded = recorder.recordTimingChange(Map.of(
                "NET_CONTROL", Map.of("NET_CONTROL_END_TIME", "21:00")));

        assertThat(recorded).containsExactly("batR03");
        verify(planMapper).markConfigEffectiveFrom("batR03", "2026-09-19T22:00");
        verify(planMapper, never()).markConfigEffectiveFrom(eq("batR04"), anyString());
    }

    @Test
    @DisplayName("実行間隔・ずらしでも同じ規則で記録する（入口ごとに規則を分けない）")
    void recordsIntervalAndOffsetChanges() {
        var recorded = recorder.recordTimingChange(Map.of(
                "STUDY_MONITOR", Map.of(
                        "STUDY_MONITOR_L02_OFFSET_MINUTES", "2",
                        "STUDY_MONITOR_L03_INTERVAL_MINUTES", "10")));

        assertThat(recorded).containsExactlyInAnyOrder("batL02", "batL03");
        verify(planMapper).markConfigEffectiveFrom("batL02", "2026-09-19T22:00");
        verify(planMapper).markConfigEffectiveFrom("batL03", "2026-09-19T22:00");
    }

    @Test
    @DisplayName("実行設定に関係しない設定（AI モデル等）では何も記録しない")
    void ignoresUnrelatedSettings() {
        var recorded = recorder.recordTimingChange(Map.of(
                "AI_MODEL", Map.of("AI_QWEN_MODEL", "qwen-max")));

        assertThat(recorded).isEmpty();
        verify(planMapper, never()).markConfigEffectiveFrom(anyString(), anyString());
    }

    @Test
    @DisplayName("有効／無効の切替でも同じ記録をする（同じ時刻・同じ版の進め方）")
    void recordsEnableToggle() {
        var recorded = recorder.recordTimingChange(List.of("batR03"));

        assertThat(recorded).containsExactly("batR03");
        ArgumentCaptor<String> effective = ArgumentCaptor.forClass(String.class);
        verify(planMapper).markConfigEffectiveFrom(eq("batR03"), effective.capture());
        assertThat(effective.getValue()).isEqualTo("2026-09-19T22:00");
    }

    @Test
    @DisplayName("スケジュール対象外のバッチ（起動時バッチ等）は記録しない")
    void ignoresTasksOutsideTheCatalog() {
        var recorded = recorder.recordTimingChange(List.of("batS01", "batC61"));

        assertThat(recorded).isEmpty();
        verify(planMapper, never()).markConfigEffectiveFrom(anyString(), anyString());
    }

    @Test
    @DisplayName("同じ保存で複数のタスクが変わっても、記録は 1 回ずつ")
    void recordsEachChangedTaskOnce() {
        var recorded = recorder.recordTimingChange(Map.of(
                "NET_CONTROL", Map.of("NET_CONTROL_START_TIME", "06:00", "NET_CONTROL_END_TIME", "22:00")));

        assertThat(recorded).containsExactlyInAnyOrder("batR03", "batR04");
        verify(planMapper, times(1)).markConfigEffectiveFrom(eq("batR03"), anyString());
        verify(planMapper, times(1)).markConfigEffectiveFrom(eq("batR04"), anyString());
    }

    @Test
    @DisplayName("適用時刻はスケジュールの時計（Asia/Tokyo）で記録する")
    void usesTheScheduleClock() {
        recorder.recordTimingChange(List.of("batR04"));

        ArgumentCaptor<String> effective = ArgumentCaptor.forClass(String.class);
        verify(planMapper).markConfigEffectiveFrom(eq("batR04"), effective.capture());
        assertThat(LocalDateTime.parse(effective.getValue())).isEqualTo(LocalDateTime.of(2026, 9, 19, 22, 0));
    }
}

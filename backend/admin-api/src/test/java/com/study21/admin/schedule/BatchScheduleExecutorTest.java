package com.study21.admin.schedule;

import com.study21.admin.batch.BatchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 実行器（{@link BatchScheduleExecutor}）の**実行直前の再検証**。
 *
 * <p>利用者の指摘: 待ち行列に入ったあと、実行が始まるまでに
 * 設定が変わったり計画が古くなったりすることがある。そのまま実行すると
 * 「もう効いていない設定」で業務が動いてしまう。実行の直前に {@link SchedulePlanGuard} で
 * もう一度見て、無効なら**スキップとして理由を残す**（待機中のまま残さない）。</p>
 *
 * <p>「手動操作を上書きしない」規則は業務側（{@code NetworkUsageService}）にあるため、
 * ここでは判定しない（そのまま実行させる）。</p>
 */
class BatchScheduleExecutorTest {

    private static final ZoneId ZONE = ScheduleConfigService.ZONE;

    private BatchService batchService;
    private ScheduleConfigService configService;
    private ScheduledTriggerStore triggerStore;
    private SchedulePlanGuard planGuard;
    private BatchScheduleExecutor executor;

    @BeforeEach
    void setUp() {
        batchService = mock(BatchService.class);
        configService = mock(ScheduleConfigService.class);
        triggerStore = mock(ScheduledTriggerStore.class);
        planGuard = new SchedulePlanGuard(configService, new ScheduleRuleCatalog(), triggerStore);
        // 2026-09-20 07:00 JST
        executor = new BatchScheduleExecutor(batchService, planGuard, 1,
                Clock.fixed(Instant.parse("2026-09-19T22:00:00Z"), ZONE));
    }

    private void config(TaskSchedule... schedules) {
        ScheduleRuleCatalog catalog = new ScheduleRuleCatalog();
        Map<String, TaskSchedule> tasks = new LinkedHashMap<>();
        Map<String, TaskConfigStatus> statuses = new LinkedHashMap<>();
        for (String code : catalog.taskCodes()) {
            statuses.put(code, TaskConfigStatus.MISSING);
        }
        for (TaskSchedule schedule : schedules) {
            tasks.put(schedule.taskCode(), schedule);
            statuses.put(schedule.taskCode(), TaskConfigStatus.LOADED);
        }
        when(configService.snapshot()).thenReturn(new ScheduleConfigSnapshot(5,
                Instant.parse("2026-09-19T22:00:00Z"), ZONE, tasks, statuses, Map.of(), null));
    }

    @Test
    @DisplayName("いまの計画のままなら実行する")
    void runsWhenThePlanIsStillValid() {
        config(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30)));

        boolean ran = executor.runVerified("batR04", 900L, LocalDateTime.of(2026, 9, 20, 6, 30));

        org.assertj.core.api.Assertions.assertThat(ran).isTrue();
        verify(batchService).runQueued(900L);
        verify(batchService, never()).markQueuedAsSkipped(anyLong(), anyString());
    }

    @Test
    @DisplayName("待機中に無効へ変えられたら実行せず、スキップとして理由を残す")
    void skipsWhenTheTaskWasDisabledWhileQueued() {
        config(TaskSchedule.daily("batR04", false, LocalTime.of(6, 30)));

        boolean ran = executor.runVerified("batR04", 901L, LocalDateTime.of(2026, 9, 20, 6, 30));

        org.assertj.core.api.Assertions.assertThat(ran).isFalse();
        verify(batchService, never()).runQueued(anyLong());
        verify(batchService).markQueuedAsSkipped(eq(901L), contains("無効"));
    }

    @Test
    @DisplayName("待機中に実行設定が変わり適用時刻が後ろへ動いたら実行しない")
    void skipsWhenTheConfigChangedWhileQueued() {
        config(TaskSchedule.daily("batR04", true, LocalTime.of(6, 30))
                .withEffectiveFrom(LocalDateTime.of(2026, 9, 20, 6, 45)));

        boolean ran = executor.runVerified("batR04", 902L, LocalDateTime.of(2026, 9, 20, 6, 30));

        org.assertj.core.api.Assertions.assertThat(ran).isFalse();
        verify(batchService).markQueuedAsSkipped(eq(902L), contains("設定の適用時刻"));
    }

    @Test
    @DisplayName("待機中に新しい計画実行点が到来していたら、古い点は実行しない（L の取りこぼし合并）")
    void skipsStaleIntervalPoint() {
        config(TaskSchedule.interval("batL02", true, 5, 1));

        // 06:56 の点を 07:00 に実行しようとしている（1 周期超の遅れ）
        boolean ran = executor.runVerified("batL02", 903L, LocalDateTime.of(2026, 9, 20, 6, 51));

        org.assertj.core.api.Assertions.assertThat(ran).isFalse();
        verify(batchService).markQueuedAsSkipped(eq(903L), contains("古くなっている"));
    }

    @Test
    @DisplayName("計画実行点が分からない実行（手動など）は今までどおり実行する")
    void runsWhenThePlannedPointIsUnknown() {
        boolean ran = executor.runVerified("batR04", 904L, null);

        org.assertj.core.api.Assertions.assertThat(ran).isTrue();
        verify(batchService).runQueued(904L);
    }
}

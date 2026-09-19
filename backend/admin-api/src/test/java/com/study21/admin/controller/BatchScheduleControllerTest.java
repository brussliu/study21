package com.study21.admin.controller;

import com.study21.admin.schedule.BatchScheduleExecutor;
import com.study21.admin.schedule.BatchScheduleScheduler;
import com.study21.admin.schedule.ScheduleConfigReport;
import com.study21.admin.schedule.ScheduleConfigService;
import com.study21.admin.schedule.ScheduledTriggerStore;
import com.study21.admin.schedule.TaskConfigStatus;
import com.study21.common.core.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 実行スケジュールの API（状態の照会と、管理者の再読み込み）。
 *
 * <p>画面（設定ページ）が「時計・版・次回実行時刻・反映待ち」を出すために使う。
 * 秘密は返さない。再読み込みで失敗したときも 200 で「反映待ち」を返す（保存は済んでいるため）。</p>
 */
class BatchScheduleControllerTest {

    private ScheduleConfigService configService;
    private ScheduledTriggerStore triggerStore;
    private BatchScheduleExecutor executor;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        configService = mock(ScheduleConfigService.class);
        triggerStore = mock(ScheduledTriggerStore.class);
        executor = mock(BatchScheduleExecutor.class);
        BatchScheduleController controller = new BatchScheduleController(
                configService, triggerStore, executor, mock(BatchScheduleScheduler.class));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ScheduleConfigReport report(boolean pending, String error) {
        ScheduleConfigReport.TaskStatus batR03 = new ScheduleConfigReport.TaskStatus(
                "batR03", "LOADED", "有効", true, "毎日 23:30", null, null, "23:30",
                List.of("23:30"), LocalDateTime.of(2026, 9, 19, 23, 30), "2026-09-19 23:30");
        ScheduleConfigReport.TaskStatus batL02 = new ScheduleConfigReport.TaskStatus(
                "batL02", "INVALID", "設定不正", false, "設定不正", null, null, null,
                List.of(), null, "設定不正");
        return new ScheduleConfigReport("Asia/Tokyo", 4L, Instant.parse("2026-09-19T14:00:00Z"),
                pending, Instant.parse("2026-09-19T14:30:00Z"), error, null, 0L, List.of(batR03, batL02));
    }

    @Test
    @DisplayName("状態の照会: 時計・版・タスクごとの次回実行時刻を返す（秘密は返さない）")
    void statusReturnsSchedule() throws Exception {
        when(configService.report()).thenReturn(report(false, null));
        when(triggerStore.lastClaimedAt("batR03")).thenReturn(LocalDateTime.of(2026, 9, 18, 23, 30));

        mockMvc.perform(get("/api/admin/batch/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.zone").value("Asia/Tokyo"))
                .andExpect(jsonPath("$.data.version").value(4))
                .andExpect(jsonPath("$.data.pendingRefresh").value(false))
                .andExpect(jsonPath("$.data.checkIntervalSeconds").value(30))
                .andExpect(jsonPath("$.data.tasks[0].taskCode").value("batR03"))
                .andExpect(jsonPath("$.data.tasks[0].describe").value("毎日 23:30"))
                .andExpect(jsonPath("$.data.tasks[0].nextRunLabel").value("2026-09-19 23:30"))
                .andExpect(jsonPath("$.data.tasks[1].statusLabel").value("設定不正"))
                .andExpect(jsonPath("$.data.tasks[1].nextRunAt").doesNotExist());
    }

    @Test
    @DisplayName("反映に失敗しているときは「保存済み・実行設定への反映待ち」と理由を返す")
    void statusShowsPendingRefresh() throws Exception {
        when(configService.report()).thenReturn(report(true, "接続できません"));

        mockMvc.perform(get("/api/admin/batch/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.pendingRefresh").value(true))
                .andExpect(jsonPath("$.data.pendingMessage")
                        .value("保存済み・実行設定への反映待ち（接続できません）。自動で再試行します。"));
    }

    @Test
    @DisplayName("再読み込み: 成功したら新しい版とメッセージを返す（計画状態も読み直す）")
    void reloadPublishes() throws Exception {
        when(configService.refresh(anyString()))
                .thenReturn(new ScheduleConfigService.RefreshResult(true, 5L, null, null));
        when(configService.report()).thenReturn(report(false, null));

        mockMvc.perform(post("/api/admin/batch/schedule/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshPublished").value(true))
                .andExpect(jsonPath("$.data.message").value("実行設定を読み直しました。"))
                .andExpect(jsonPath("$.data.version").value(4));
        verify(triggerStore).reloadPlans();
    }

    @Test
    @DisplayName("再読み込みに失敗しても 200 で「前の設定のまま」を返す（DB は巻き戻さない）")
    void reloadKeepsPreviousWhenFailed() throws Exception {
        when(configService.refresh(anyString()))
                .thenReturn(new ScheduleConfigService.RefreshResult(false, 4L, "接続できません", null));
        when(configService.report()).thenReturn(report(true, "接続できません"));

        mockMvc.perform(post("/api/admin/batch/schedule/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.refreshPublished").value(false))
                .andExpect(jsonPath("$.data.message").value(
                        "実行設定を読み直せませんでした（前の設定のまま動きます）。接続できません"));
    }

    @Test
    @DisplayName("タスクの状態は「未読込／有効／未設定／設定不正」で区別する（null で兼用しない）")
    void statusLabelsAreExplicit() {
        assertThat(TaskConfigStatus.NOT_LOADED.label()).isEqualTo("未読込");
        assertThat(TaskConfigStatus.INVALID.label()).isEqualTo("設定不正");
        assertThat(TaskConfigStatus.INVALID.usable()).isFalse();
        assertThat(TaskConfigStatus.LOADED.usable()).isTrue();
    }
}

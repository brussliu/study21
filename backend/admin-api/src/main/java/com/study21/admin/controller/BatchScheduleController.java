package com.study21.admin.controller;

import com.study21.admin.schedule.BatchExecutionRecovery;
import com.study21.admin.schedule.BatchScheduleExecutor;
import com.study21.admin.schedule.BatchScheduleScheduler;
import com.study21.admin.schedule.ScheduleConfigReport;
import com.study21.admin.schedule.ScheduleConfigService;
import com.study21.admin.schedule.ScheduledTriggerStore;
import com.study21.common.core.api.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * バッチのスケジュール設定の状態と再読み込み（管理者向け）。
 *
 * <p>見せるもの: いまメモリで効いている**版**・読み込み時刻・時計（Asia/Tokyo）・
 * タスクごとの実行タイミングと**次回実行時刻**・「保存済み・実行設定への反映待ち」の状態・
 * 反映に失敗した理由。**秘密（API キー等）は出さない**。</p>
 *
 * <p>【設定を再読み込み】は、DB を直接触ったとき（SQL で設定を直したとき）に使う。
 * 設定画面の保存・有効／無効の切替は自動で反映される（この API を叩かなくてよい）。</p>
 */
@RestController
@RequestMapping("/api/admin/batch/schedule")
public class BatchScheduleController {

    private final ScheduleConfigService configService;
    private final ScheduledTriggerStore triggerStore;
    private final BatchScheduleExecutor executor;
    private final BatchScheduleScheduler scheduler;
    /** 再起動の復旧の進み具合（保留のまま残っていないか）。 */
    private final BatchExecutionRecovery recovery;

    public BatchScheduleController(ScheduleConfigService configService,
                                   ScheduledTriggerStore triggerStore,
                                   BatchScheduleExecutor executor,
                                   BatchScheduleScheduler scheduler,
                                   BatchExecutionRecovery recovery) {
        this.configService = configService;
        this.triggerStore = triggerStore;
        this.executor = executor;
        this.scheduler = scheduler;
        this.recovery = recovery;
    }

    /** いまの実行スケジュール（メモリの設定。DB は読まない）。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.ok(payload());
    }

    /**
     * 設定を DB から読み直してメモリに反映する（管理者の再読み込み）。
     *
     * <p>失敗しても DB は巻き戻さない（保存は済んでいる）。前の有効な設定を残し、
     * 「実行設定への反映待ち」として返す。</p>
     */
    @PostMapping("/reload")
    public ApiResponse<Map<String, Object>> reload() {
        ScheduleConfigService.RefreshResult result = configService.refresh("管理者の再読み込み");
        // 計画状態（最後に確保した計画実行点）も読み直す
        triggerStore.reloadPlans();
        // 設定を直した直後は、保留している再起動の復旧もその場で判定し直す
        // （完了していれば中で即 return。DB は引かない）
        if (result.published()) {
            recovery.retryPendingRecoveryNow("設定の再読み込み");
        }
        Map<String, Object> payload = payload();
        payload.put("refreshPublished", result.published());
        payload.put("refreshError", result.error());
        payload.put("message", result.published()
                ? "実行設定を読み直しました。"
                : "実行設定を読み直せませんでした（前の設定のまま動きます）。" + result.error());
        return ApiResponse.ok(payload);
    }

    private Map<String, Object> payload() {
        ScheduleConfigReport report = configService.report();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("zone", report.zone());
        payload.put("version", report.activeVersion());
        payload.put("loadedAt", report.loadedAt());
        payload.put("pendingRefresh", report.pendingRefresh());
        payload.put("pendingMessage", report.pendingMessage());
        payload.put("lastRefreshAt", report.lastRefreshAt());
        payload.put("lastRefreshError", report.lastRefreshError());
        payload.put("nextRetryAt", report.nextRetryAt());
        payload.put("configMissing", report.configMissing());
        payload.put("configMissingMessage", report.configMissingMessage());
        // 再起動の復旧の進み具合（未確認 / 判定待ち / 完了）と、保留のままの件数
        BatchExecutionRecovery.RecoveryStatus recoveryStatus = recovery.status();
        payload.put("recoveryPhase", recoveryStatus.phase().name());
        payload.put("recoveryPhaseLabel", recoveryStatus.phaseLabel());
        payload.put("recoveryPendingCount", recoveryStatus.pendingCount());
        payload.put("pendingSubmissions", executor.pendingSubmissionCount());
        payload.put("checkIntervalSeconds", 30);
        payload.put("runningWorkers", executor.activeCount());
        payload.put("queuedWorkers", executor.queuedCount());
        payload.put("tasks", report.tasks().stream().map(task -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("taskCode", task.taskCode());
            row.put("status", task.status());
            row.put("statusLabel", task.statusLabel());
            row.put("enabled", task.enabled());
            row.put("describe", task.describe());
            row.put("intervalMinutes", task.intervalMinutes());
            row.put("offsetMinutes", task.offsetMinutes());
            row.put("dailyTime", task.dailyTime());
            row.put("examplePoints", task.examplePoints());
            row.put("nextRunAt", task.nextRunAt());
            row.put("nextRunLabel", task.nextRunLabel());
            row.put("configEffectiveFrom", task.configEffectiveFrom());
            row.put("planVersion", task.planVersion());
            row.put("fallbackFailures", task.fallbackFailures());
            row.put("nextFallbackCheckAt", task.nextFallbackCheckAt());
            row.put("fallbackMessage", task.fallbackMessage());
            row.put("lastPlannedAt", triggerStore.lastClaimedAt(task.taskCode()));
            return row;
        }).toList());
        return payload;
    }
}

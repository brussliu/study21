package com.study21.admin.schedule;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * スケジュール設定の**読み込み結果**（管理画面・設定画面に見せる情報。秘密は含めない）。
 *
 * @param zone             スケジュールの時計（常に Asia/Tokyo）
 * @param activeVersion    いまメモリで**効いている**スナップショットの版（0 = 未読込）
 * @param loadedAt         その版を DB から読んだ実時刻
 * @param pendingRefresh   DB は保存済みだが、メモリへの反映がまだ成功していないか
 * @param lastRefreshAt    最後に反映を試した時刻
 * @param lastRefreshError 最後の反映失敗の理由（成功なら null）
 * @param nextRetryAt      次の自動再試行の予定時刻（待避中でなければ null）
 * @param suppressedCount  同じ失敗を繰り返してログを抑止した回数
 * @param tasks            タスクごとの状態
 */
public record ScheduleConfigReport(
        String zone,
        long activeVersion,
        Instant loadedAt,
        boolean pendingRefresh,
        Instant lastRefreshAt,
        String lastRefreshError,
        Instant nextRetryAt,
        long suppressedCount,
        List<TaskStatus> tasks) {

    /**
     * タスク 1 件ぶんの状態。
     *
     * @param taskCode    バッチコード
     * @param status      設定の状態（未読込／有効／未設定／設定不正）
     * @param statusLabel 画面に出す日本語
     * @param enabled     有効か（BAT_バッチコントロール情報 の値。設定が無いときは null）
     * @param describe    実行タイミングの説明（例「毎日 23:30」「毎時 01/06/…/56 分（5 分間隔・ずらし 1 分）」）
     * @param intervalMinutes INTERVAL の間隔（DAILY は null）
     * @param offsetMinutes   INTERVAL のずらし（DAILY は null）
     * @param dailyTime       DAILY の時刻（INTERVAL は null）
     * @param examplePoints   1 日の実行時刻の例（画面表示用）
     * @param nextRunAt       次の計画実行時刻（設定が無い・不正なときは null）
     * @param nextRunLabel    次の計画実行時刻の表示（「2026-09-20 06:30」など。無いときは理由）
     * @param configEffectiveFrom いまの設定が効き始めた時刻（未変更なら null）。
     *                        この時刻以前の計画実行点は実行しない（画面で理由が分かるように出す）
     */
    public record TaskStatus(
            String taskCode,
            String status,
            String statusLabel,
            Boolean enabled,
            String describe,
            Integer intervalMinutes,
            Integer offsetMinutes,
            String dailyTime,
            List<String> examplePoints,
            LocalDateTime nextRunAt,
            String nextRunLabel,
            String configEffectiveFrom) {
    }

    /** 画面に出す「保存済み・実行設定への反映待ち」の案内（そのまま出してよい日本語）。 */
    public String pendingMessage() {
        if (!pendingRefresh) {
            return null;
        }
        String reason = lastRefreshError == null || lastRefreshError.isBlank() ? "原因不明" : lastRefreshError;
        return "保存済み・実行設定への反映待ち（" + reason + "）。自動で再試行します。";
    }

    /** タスクコードで引く（見つからなければ null）。 */
    public TaskStatus taskStatus(String taskCode) {
        return tasks.stream().filter(task -> task.taskCode().equals(taskCode)).findFirst().orElse(null);
    }

    /** 版と状態の要約（ログ用）。 */
    public String summarize() {
        Map<String, Long> counts = tasks.stream()
                .collect(java.util.stream.Collectors.groupingBy(TaskStatus::statusLabel, java.util.LinkedHashMap::new,
                        java.util.stream.Collectors.counting()));
        return "version=" + activeVersion + " pending=" + pendingRefresh + " tasks=" + counts;
    }
}

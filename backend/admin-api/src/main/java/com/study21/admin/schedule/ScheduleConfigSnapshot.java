package com.study21.admin.schedule;

import java.time.Instant;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * バッチのスケジュール設定の**不変スナップショット**（メモリに置く唯一の実行設定）。
 *
 * <p>1 回のスケジュール検査は**同じスナップショット**だけを見る（検査の途中で新しい設定に
 * 入れ替わって、古い間隔と新しい時刻が混ざることを防ぐ）。入れ替えは
 * {@link java.util.concurrent.atomic.AtomicReference} で丸ごと差し替える。</p>
 *
 * @param version  発行の通し番号（1 から。0 は「まだ読んでいない」）
 * @param loadedAt このスナップショットを DB から読んだ実時刻
 * @param zone     スケジュールの時計（常に Asia/Tokyo）
 * @param tasks    タスクコード → 解決済みスケジュール（{@link TaskConfigStatus#LOADED} のものだけ）
 * @param statuses タスクコード → 設定の状態（すべての対象タスクぶん。null を使わない）
 * @param planVersions タスクコード → **計画バージョン**（実行設定の保存と同じトランザクションで
 *                     1 つ進む版。読み込んだ設定が保存時の版と同じかの判定に使う）
 * @param loadError 直近の読み込み失敗の理由（成功なら null）
 */
public record ScheduleConfigSnapshot(
        long version,
        Instant loadedAt,
        ZoneId zone,
        Map<String, TaskSchedule> tasks,
        Map<String, TaskConfigStatus> statuses,
        Map<String, Long> planVersions,
        String loadError) {

    public ScheduleConfigSnapshot {
        tasks = Map.copyOf(new LinkedHashMap<>(tasks));
        statuses = Map.copyOf(new LinkedHashMap<>(statuses));
        planVersions = Map.copyOf(new LinkedHashMap<>(planVersions));
    }

    /** まだ 1 度も読んでいない初期状態。 */
    public static ScheduleConfigSnapshot empty(ZoneId zone, java.util.Collection<String> taskCodes) {
        Map<String, TaskConfigStatus> statuses = new LinkedHashMap<>();
        for (String taskCode : taskCodes) {
            statuses.put(taskCode, TaskConfigStatus.NOT_LOADED);
        }
        return new ScheduleConfigSnapshot(0, null, zone, Map.of(), statuses, Map.of(), null);
    }

    /** このタスクの計画バージョン（未記録は 0）。 */
    public long planVersionOf(String taskCode) {
        return planVersions.getOrDefault(taskCode, 0L);
    }

    /** DB から 1 度でも読めたか（読めていなくても、失敗理由つきで「読んだ」ことがある）。 */
    public boolean loadedOnce() {
        return loadedAt != null;
    }

    /**
     * このタスクの設定状態。
     *
     * <p>対象外のコードは {@link TaskConfigStatus#NOT_LOADED}（null を返さない）。</p>
     */
    public TaskConfigStatus statusOf(String taskCode) {
        return statuses.getOrDefault(taskCode, TaskConfigStatus.NOT_LOADED);
    }

    /** このタスクのスケジュール（有効な設定があるときだけ）。 */
    public Optional<TaskSchedule> taskOf(String taskCode) {
        return Optional.ofNullable(tasks.get(taskCode));
    }

    /** 設定がそろっていて自動実行に使えるタスクの数。 */
    public long usableTaskCount() {
        return statuses.values().stream().filter(TaskConfigStatus::usable).count();
    }

    /** 失敗理由が付いているか（画面の「反映待ち」表示に使う）。 */
    public boolean hasLoadError() {
        return loadError != null && !loadError.isBlank();
    }

    /** 中身を差し替えた新しいスナップショット（version を 1 つ進める）。 */
    public ScheduleConfigSnapshot next(Instant loadedAt,
                                       Map<String, TaskSchedule> tasks,
                                       Map<String, TaskConfigStatus> statuses,
                                       Map<String, Long> planVersions,
                                       String loadError) {
        return new ScheduleConfigSnapshot(version + 1, loadedAt, zone, tasks, statuses, planVersions, loadError);
    }
}

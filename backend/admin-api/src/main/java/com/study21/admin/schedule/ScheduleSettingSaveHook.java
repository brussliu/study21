package com.study21.admin.schedule;

import com.study21.admin.setting.SettingSaveTransactionHook;
import org.springframework.stereotype.Component;

/**
 * 設定の保存トランザクションの中で、**実行設定の変更**（適用時刻＋計画バージョン）を記録する。
 *
 * <p>実行時刻・実行間隔・ずらしのどれが変わっても、そのタスクの計画バージョンを同じ
 * トランザクションで進める（{@link ScheduleTimingRecorder}）。設定値と適用時刻が
 * 食い違った状態を残さないための仕組み（docs/BATCH_SCHEDULE.md §4）。</p>
 *
 * <p>設定画面から実行設定を変える入口はこの 1 つだけ（有効／無効は
 * {@code BatchServiceImpl#updateActive} が同じ記録を別に行う）。</p>
 */
@Component
public class ScheduleSettingSaveHook implements SettingSaveTransactionHook {

    private final ScheduleTimingRecorder recorder;

    public ScheduleSettingSaveHook(ScheduleTimingRecorder recorder) {
        this.recorder = recorder;
    }

    @Override
    public void onSettingsSaved(SettingSaveEvent event) {
        recorder.recordTimingChange(event.changedByPage());
    }
}

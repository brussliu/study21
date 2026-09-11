package com.study21.admin.batch;

/**
 * バッチタスクの業務処理ハンドラ（拡張点）。
 *
 * <p>旧 study2.0 の各タスク（BatC01 等）の業務処理を移植する際は、本インタフェースを実装し、
 * {@link BatchTaskRegistry} に対応するハンドラを登録する。業務処理は必ず
 * {@code SettingsService#requireSettings} で設定を検証・読込してから開始すること。</p>
 */
public interface BatchTaskHandler {

    String taskCode();

    void execute(BatchExecutionEntity execution);
}

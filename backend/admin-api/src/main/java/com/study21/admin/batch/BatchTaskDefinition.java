package com.study21.admin.batch;

import com.study21.admin.setting.SettingRequirement;

import java.util.List;

/**
 * バッチタスク定義。
 *
 * @param taskCode          安定したタスクコード
 * @param taskType          種別（R/L/C）
 * @param description       説明
 * @param active            有効フラグ
 * @param loopEveryMinutes  循環間隔（分、L のみ）
 * @param minuteOfHour      毎時実行分（L のみ、60分以上の間隔で使用）
 * @param allowConcurrent   並列実行許可
 * @param pageCode          関連する設定ページ
 * @param requiredSettings  実行に必須の設定（page_code + setting_key）
 */
public record BatchTaskDefinition(
        String taskCode,
        BatchTaskType taskType,
        String description,
        boolean active,
        Integer loopEveryMinutes,
        Integer minuteOfHour,
        boolean allowConcurrent,
        String pageCode,
        List<SettingRequirement> requiredSettings) {

    public boolean canManualReRun() {
        if ((taskType == BatchTaskType.L || taskType == BatchTaskType.R) && !active) {
            return false;
        }
        return taskType == BatchTaskType.L || taskType == BatchTaskType.R || taskType == BatchTaskType.C;
    }

    public boolean canToggleActive() {
        return taskType == BatchTaskType.L || taskType == BatchTaskType.R;
    }
}

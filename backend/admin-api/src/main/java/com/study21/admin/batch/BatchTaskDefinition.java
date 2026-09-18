package com.study21.admin.batch;

import com.study21.admin.setting.SettingRequirement;

import java.util.List;

/**
 * バッチタスク定義。
 *
 * @param taskCode          安定したタスクコード
 * @param taskType          種別（S/L/R/C）
 * @param description       説明
 * @param active            有効フラグ（既定値。実際の有効／無効は BAT_バッチコントロール情報 が正）
 * @param loopEveryMinutes  循環間隔（分、L のみ）
 * @param minuteOfHour      毎時実行分（L のみ、60分以上の間隔で使用）
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
        String pageCode,
        List<SettingRequirement> requiredSettings) {

    /**
     * 画面の【再実行】で起動できるか。
     *
     * <p>S（システム起動時）・L（循環）・R（定時）は有効／無効に関係なく再実行できる。
     * L / R は無効にしても手動実行は可能という 2.0 の運用（BAT_バッチコントロール情報 の備考
     * 「OFF時は定時実行しない」）に合わせ、無効でも再実行は許可する。</p>
     *
     * <p>種別 C（呼出）は<b>画面からは起動しない</b>。C は AI 生図の流水線・授業ノートなど
     * 他の処理が工程として呼ぶバッチで、画面の【再実行】から動かすと、その処理が
     * 用意する要求（要求内容の aiRequestId など）が無いまま走ってしまう
     * （利用者の指示で、一覧からは【再実行】ボタンごと外した）。他の処理からの
     * 呼出（{@link BatchService#rerunStep}）は種別に関係なく今までどおり動く。</p>
     */
    public boolean canManualRerun() {
        return taskType != BatchTaskType.C;
    }

    /** 画面から有効／無効を切り替えられるか（定時・循環・システム起動のバッチ）。 */
    public boolean canToggleActive() {
        return taskType == BatchTaskType.S || taskType == BatchTaskType.L || taskType == BatchTaskType.R;
    }

    /**
     * 起動時に 1 回だけ実行する種別（S）で、かつ有効か。
     * admin-api の起動時に走らせる対象の判定に使う。
     */
    public boolean runsOnStartup() {
        return taskType == BatchTaskType.S && active;
    }
}

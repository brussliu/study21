package com.study21.admin.schedule;

/**
 * スケジュール対象タスクの**設定の在り処**（どの設定キーを見るか）。
 *
 * <p>値そのもの（時刻・間隔）は COM_設定情報 にあり、有効／無効は
 * BAT_バッチコントロール情報 が唯一の正。この record は「どのキーを読むか」だけを持つ
 * （コードに実行時刻を書かない）。</p>
 *
 * @param taskCode          バッチコード（例 batR03）
 * @param kind              スケジュール種別
 * @param pageCode          設定のページ区分（COM_設定情報 の ページ区分）
 * @param timeSettingKey    DAILY のときの時刻キー（{@code HH:mm}）。INTERVAL では null
 * @param intervalSettingKey INTERVAL のときの実行間隔キー（分）。DAILY では null
 * @param offsetSettingKey  INTERVAL のときのずらしキー（分）。DAILY では null
 */
public record ScheduleTaskRule(
        String taskCode,
        ScheduleKind kind,
        String pageCode,
        String timeSettingKey,
        String intervalSettingKey,
        String offsetSettingKey) {

    public ScheduleTaskRule {
        if (taskCode == null || taskCode.isBlank()) {
            throw new IllegalArgumentException("taskCode は必須です。");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind は必須です。");
        }
        if (pageCode == null || pageCode.isBlank()) {
            throw new IllegalArgumentException("pageCode は必須です。");
        }
        if (kind == ScheduleKind.DAILY && (timeSettingKey == null || timeSettingKey.isBlank())) {
            throw new IllegalArgumentException("DAILY には timeSettingKey が必要です: " + taskCode);
        }
        if (kind == ScheduleKind.INTERVAL
                && (intervalSettingKey == null || intervalSettingKey.isBlank()
                    || offsetSettingKey == null || offsetSettingKey.isBlank())) {
            throw new IllegalArgumentException("INTERVAL には intervalSettingKey と offsetSettingKey が必要です: " + taskCode);
        }
    }

    /** このルールが読む設定キー（DB からの一括読み込みに使う）。 */
    public java.util.List<String> settingKeys() {
        java.util.List<String> keys = new java.util.ArrayList<>();
        if (timeSettingKey != null) {
            keys.add(timeSettingKey);
        }
        if (intervalSettingKey != null) {
            keys.add(intervalSettingKey);
        }
        if (offsetSettingKey != null) {
            keys.add(offsetSettingKey);
        }
        return java.util.List.copyOf(keys);
    }
}

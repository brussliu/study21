package com.study21.admin.schedule;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * スケジュール対象タスクの**カタログ**（どのタスクを、どの設定キーで動かすか）。
 *
 * <p>実行時刻・実行間隔は**ここに書かない**（COM_設定情報 から読む）。ここが持つのは
 * 「どのタスクが、どの種別で、どのキーを見るか」だけ。</p>
 *
 * <p>2.0 は batR03/batR04 の 23:30/06:30 がコードと通知文に固定で、batL02 は専用の
 * 5 分スケジューラ（{@code BatL02FiveMinuteScheduler}）、batL03 は定義の
 * {@code loopEveryMinutes=5} だった。2.1 は**この 4 つを 1 つのスケジューラで扱い**、
 * 間隔・ずらし・時刻を設定で変えられるようにする。</p>
 */
@Component
public class ScheduleRuleCatalog {

    /** 2.1 で自動実行する 4 タスク。 */
    private static final List<ScheduleTaskRule> RULES = List.of(
            // ネットワーク制御（種別 R。時刻は設定）
            new ScheduleTaskRule("batR04", ScheduleKind.DAILY, "NET_CONTROL",
                    "NET_CONTROL_START_TIME", null, null),
            new ScheduleTaskRule("batR03", ScheduleKind.DAILY, "NET_CONTROL",
                    "NET_CONTROL_END_TIME", null, null),
            // 学習状況モニター（種別 L。間隔とずらしは設定）
            new ScheduleTaskRule("batL02", ScheduleKind.INTERVAL, "STUDY_MONITOR",
                    null, "STUDY_MONITOR_L02_INTERVAL_MINUTES", "STUDY_MONITOR_L02_OFFSET_MINUTES"),
            new ScheduleTaskRule("batL03", ScheduleKind.INTERVAL, "STUDY_MONITOR",
                    null, "STUDY_MONITOR_L03_INTERVAL_MINUTES", "STUDY_MONITOR_L03_OFFSET_MINUTES"));

    /** 設定値としての実行間隔の候補（画面と同じ。バッチの実行間隔であって、切図間隔ではない）。 */
    public static final List<Integer> INTERVAL_CHOICES = List.of(1, 5, 10, 15, 30, 60);

    public List<ScheduleTaskRule> rules() {
        return RULES;
    }

    public List<String> taskCodes() {
        return RULES.stream().map(ScheduleTaskRule::taskCode).toList();
    }

    public Optional<ScheduleTaskRule> find(String taskCode) {
        return RULES.stream().filter(rule -> rule.taskCode().equals(taskCode)).findFirst();
    }

    /** DB から一括で読むべき設定キー（このカタログぶんだけ読む。全設定は読まない）。 */
    public List<String> requiredSettingKeys() {
        return RULES.stream().flatMap(rule -> rule.settingKeys().stream()).distinct().toList();
    }
}

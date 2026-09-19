package com.study21.admin.schedule;

import com.study21.common.core.exception.ValidationException;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * 実行スケジュールの設定の**欄をまたぐ検証**（保存の前に行う）。
 *
 * <p>1 つの値だけを見る検証（型・範囲）は {@code COM_設定項目} のカタログと
 * {@code SettingsServiceImpl} が行うが、次の 2 つは**他の欄との関係**で決まるため
 * カタログでは表せない:</p>
 * <ul>
 *   <li><b>ずらしは 0〜実行間隔-1 分</b>（例: 間隔 5 分ならずらしは 0〜4 分）。
 *       はみ出すと「毎時 ずらし + n×間隔」が 1 時間に収まらず、計画実行点がずれる。</li>
 *   <li><b>利用開始時刻と利用終了時刻は同じにできない</b>（同じだと 1 日のうち
 *       「開始」と「終了」が同時刻になり、どちらを適用すべきか決まらない。
 *       跨ぐ時刻（22:00〜06:00）は許可する）。</li>
 * </ul>
 *
 * <p>ここで弾けば **DB に保存されない**（スケジューラには不正な設定が入らない）。
 * 保存をすり抜けた場合でも、スケジューラはそのタスクを「設定不正」として自動実行しない。</p>
 */
@Component
public class ScheduleSettingValidator {

    /** 画面のフィールドキー（camelCase）。 */
    static final String FIELD_L02_INTERVAL = "monitorL02IntervalMinutes";
    static final String FIELD_L02_OFFSET = "monitorL02OffsetMinutes";
    static final String FIELD_L03_INTERVAL = "monitorL03IntervalMinutes";
    static final String FIELD_L03_OFFSET = "monitorL03OffsetMinutes";
    static final String FIELD_START_TIME = "netControlStartTime";
    static final String FIELD_END_TIME = "netControlEndTime";

    /**
     * 保存前の検証（対象の欄が含まれないときは何もしない）。
     *
     * @param settings 画面のフィールドキー → 値
     * @throws ValidationException 不正がある場合（1 件目で止める。画面が直しやすいように具体的に言う）
     */
    public void validate(Map<String, String> settings) {
        validateIntervalAndOffset("batL02", settings.get(FIELD_L02_INTERVAL), settings.get(FIELD_L02_OFFSET));
        validateIntervalAndOffset("batL03", settings.get(FIELD_L03_INTERVAL), settings.get(FIELD_L03_OFFSET));
        validateNetworkTimes(settings.get(FIELD_START_TIME), settings.get(FIELD_END_TIME));
    }

    private void validateIntervalAndOffset(String taskCode, String intervalValue, String offsetValue) {
        if (intervalValue == null && offsetValue == null) {
            return;
        }
        Integer interval = parseInteger(intervalValue, "実行間隔（" + taskCode + "）");
        Integer offset = parseInteger(offsetValue, "ずらし（" + taskCode + "）");
        if (interval == null || offset == null) {
            return;   // 片方だけの保存は他の検証（必須・型）に任せる
        }
        if (!ScheduleRuleCatalog.INTERVAL_CHOICES.contains(interval)) {
            throw new ValidationException("実行間隔は " + ScheduleRuleCatalog.INTERVAL_CHOICES.stream()
                    .map(String::valueOf).collect(java.util.stream.Collectors.joining(" / "))
                    + " 分のいずれかにしてください（" + taskCode + "）: " + interval);
        }
        if (offset < 0 || offset >= interval) {
            throw new ValidationException("ずらしは 0〜実行間隔-1 分で指定してください（" + taskCode
                    + "。実行間隔=" + interval + " 分 / ずらし=" + offset + " 分）。");
        }
    }

    private void validateNetworkTimes(String startValue, String endValue) {
        if (startValue == null && endValue == null) {
            return;
        }
        LocalTime start = parseTime(startValue, "インターネット利用開始時刻");
        LocalTime end = parseTime(endValue, "インターネット利用終了時刻");
        if (start == null || end == null) {
            return;
        }
        if (start.equals(end)) {
            throw new ValidationException("インターネット利用の開始時刻と終了時刻を同じにはできません（"
                    + start + "）。日をまたぐ時刻（例 22:00〜06:00）は指定できます。");
        }
    }

    private static Integer parseInteger(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(value.trim());
        } catch (NumberFormatException cause) {
            throw new ValidationException(label + "は整数で指定してください: " + value);
        }
    }

    private static LocalTime parseTime(String value, String label) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalTime.parse(value.trim());
        } catch (DateTimeParseException cause) {
            throw new ValidationException(label + "は HH:mm で指定してください: " + value);
        }
    }
}

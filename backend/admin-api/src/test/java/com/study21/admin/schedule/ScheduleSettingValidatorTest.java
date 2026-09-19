package com.study21.admin.schedule;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実行スケジュールの**欄をまたぐ検証**（保存の前）。
 *
 * <p>1 つの値の型・範囲はカタログが持つが、「ずらしは実行間隔より小さい」と
 * 「利用開始と終了は同時刻にしない」は他の欄との関係なので、ここで弾く
 * （弾けば DB に保存されない＝スケジューラに不正な設定が入らない）。</p>
 */
class ScheduleSettingValidatorTest {

    private final ScheduleSettingValidator validator = new ScheduleSettingValidator();

    @Test
    @DisplayName("ずらしは実行間隔より小さいこと（間隔 5 なら 0〜4）")
    void offsetMustBeSmallerThanInterval() {
        assertThatCode(() -> validator.validate(Map.of(
                "monitorL02IntervalMinutes", "5", "monitorL02OffsetMinutes", "4")))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> validator.validate(Map.of(
                "monitorL02IntervalMinutes", "5", "monitorL02OffsetMinutes", "5")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ずらしは 0〜実行間隔-1")
                .hasMessageContaining("実行間隔=5 分 / ずらし=5 分");
        assertThatThrownBy(() -> validator.validate(Map.of(
                "monitorL03IntervalMinutes", "60", "monitorL03OffsetMinutes", "60")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ずらしは 0〜実行間隔-1");
    }

    @Test
    @DisplayName("実行間隔は 1/5/10/15/30/60 分のいずれか")
    void intervalMustBeOneOfTheChoices() {
        assertThatThrownBy(() -> validator.validate(Map.of(
                "monitorL02IntervalMinutes", "7", "monitorL02OffsetMinutes", "1")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("実行間隔は 1 / 5 / 10 / 15 / 30 / 60 分のいずれか");
    }

    @Test
    @DisplayName("利用開始と終了は同時刻にできない（跨ぐ時刻は許可）")
    void networkTimesMustDiffer() {
        assertThatThrownBy(() -> validator.validate(Map.of(
                "netControlStartTime", "06:30", "netControlEndTime", "06:30")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("同じにはできません");
        assertThatCode(() -> validator.validate(Map.of(
                "netControlStartTime", "22:00", "netControlEndTime", "06:00")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("対象外の欄だけの保存では何もしない（他の設定の保存を邪魔しない）")
    void ignoresSettingsOutsideTheSchedule() {
        assertThatCode(() -> validator.validate(Map.of("qwenModel", "qwen-max")))
                .doesNotThrowAnyException();
        assertThatCode(() -> validator.validate(Map.of())).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("時刻・数値の形式が違えば保存前に弾く")
    void rejectsMalformedValues() {
        assertThatThrownBy(() -> validator.validate(Map.of("netControlStartTime", "6時30分")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("HH:mm");
        assertThatThrownBy(() -> validator.validate(Map.of(
                "monitorL02IntervalMinutes", "5", "monitorL02OffsetMinutes", "1.5")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("整数");
    }
}

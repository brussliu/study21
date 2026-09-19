package com.study21.admin.schedule;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 計画実行点の計算（{@link TaskSchedule}）。
 *
 * <p>スケジューラは「いま以前で最後に到来した計画実行点」だけを確保する。ここではその計算と、
 * ずらし・間隔の検証、次回実行時刻の計算を固定する（時刻は Asia/Tokyo のローカル時刻）。</p>
 */
class TaskScheduleTest {

    @Test
    @DisplayName("定時（DAILY）の計画実行点は毎日同じ時刻。日付をまたぐと前日になる")
    void dailyPoints() {
        TaskSchedule schedule = TaskSchedule.daily("batR03", true, LocalTime.of(23, 30));

        assertThat(schedule.previousPointAtOrBefore(LocalDateTime.of(2026, 9, 19, 23, 30)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 23, 30));
        assertThat(schedule.previousPointAtOrBefore(LocalDateTime.of(2026, 9, 19, 23, 29)))
                .isEqualTo(LocalDateTime.of(2026, 9, 18, 23, 30));
        // 翌朝に復帰したときは「前日の 23:30」が最後の計画実行点（当日の 23:30 はまだ来ていない）
        assertThat(schedule.previousPointAtOrBefore(LocalDateTime.of(2026, 9, 20, 6, 30)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 23, 30));

        assertThat(schedule.nextPointAfter(LocalDateTime.of(2026, 9, 19, 23, 30)))
                .isEqualTo(LocalDateTime.of(2026, 9, 20, 23, 30));
        assertThat(schedule.nextPointAfter(LocalDateTime.of(2026, 9, 19, 0, 0)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 23, 30));
    }

    @Test
    @DisplayName("循環（INTERVAL）は「ずらし + n×間隔」分。既定は batL02 = 5 分間隔・ずらし 1 分")
    void intervalPoints() {
        TaskSchedule schedule = TaskSchedule.interval("batL02", true, 5, 1);

        // 毎時 01, 06, 11, …, 56 分
        assertThat(schedule.pointsOfDay(LocalDate.of(2026, 9, 19)))
                .containsExactly(LocalTime.of(0, 1), LocalTime.of(0, 6), LocalTime.of(0, 11),
                        LocalTime.of(0, 16), LocalTime.of(0, 21), LocalTime.of(0, 26),
                        LocalTime.of(0, 31), LocalTime.of(0, 36), LocalTime.of(0, 41),
                        LocalTime.of(0, 46), LocalTime.of(0, 51), LocalTime.of(0, 56));

        assertThat(schedule.previousPointAtOrBefore(LocalDateTime.of(2026, 9, 19, 10, 23)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 10, 21));
        assertThat(schedule.previousPointAtOrBefore(LocalDateTime.of(2026, 9, 19, 10, 21)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 10, 21));
        // 毎時の最初の点より前は、前の時間の最後の点（10:00 → 09:56）
        assertThat(schedule.previousPointAtOrBefore(LocalDateTime.of(2026, 9, 19, 10, 0)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 9, 56));

        assertThat(schedule.nextPointAfter(LocalDateTime.of(2026, 9, 19, 10, 21)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 10, 26));
        assertThat(schedule.nextPointAfter(LocalDateTime.of(2026, 9, 19, 10, 56)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 11, 1));
    }

    @Test
    @DisplayName("ずらし 0 分・5 分間隔（batL03 の既定）は毎時 00/05/…/55 分")
    void intervalWithZeroOffset() {
        TaskSchedule schedule = TaskSchedule.interval("batL03", true, 5, 0);
        assertThat(schedule.previousPointAtOrBefore(LocalDateTime.of(2026, 9, 19, 10, 4)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 10, 0));
        assertThat(schedule.nextPointAfter(LocalDateTime.of(2026, 9, 19, 10, 0)))
                .isEqualTo(LocalDateTime.of(2026, 9, 19, 10, 5));
        assertThat(schedule.pointsOfDay(LocalDate.of(2026, 9, 19))).hasSize(12);
    }

    @Test
    @DisplayName("間隔 60 分は毎時 1 点。間隔・ずらしの範囲外は例外")
    void intervalValidation() {
        TaskSchedule hourly = TaskSchedule.interval("batX", true, 60, 0);
        assertThat(hourly.pointsOfDay(LocalDate.of(2026, 9, 19))).containsExactly(LocalTime.of(0, 0));

        assertThatThrownBy(() -> TaskSchedule.interval("batX", true, 5, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ずらしは 0〜実行間隔-1");
        assertThatThrownBy(() -> TaskSchedule.interval("batX", true, 0, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("正の実行間隔");
    }

    @Test
    @DisplayName("画面に出す説明に時刻・間隔・ずらしが入る（実行時刻をコードに書かない）")
    void describe() {
        assertThat(TaskSchedule.daily("batR03", true, LocalTime.of(23, 30)).describe())
                .isEqualTo("毎日 23:30");
        assertThat(TaskSchedule.interval("batL02", true, 5, 1).describe())
                .isEqualTo("毎時 01/06/…/56 分（5 分間隔・ずらし 1 分）");
    }

    @Test
    @DisplayName("1 日の実行時刻の例は画面表示用（INTERVAL は 24 時間ぶんの時刻）")
    void pointsOfDayForDisplay() {
        List<LocalTime> points = TaskSchedule.interval("batL03", true, 30, 0).pointsOfDay(LocalDate.now());
        assertThat(points).containsExactly(LocalTime.of(0, 0), LocalTime.of(0, 30));
    }
}

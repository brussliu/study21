package com.study21.admin.schedule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 1 つのタスクの**解決済みスケジュール**（不変。設定値＋有効／無効を 1 つにまとめたもの）。
 *
 * <p>計画実行点（occurrence）の決め方は種別で決まる:</p>
 * <ul>
 *   <li>{@link ScheduleKind#DAILY} … 毎日 {@link #dailyTime} の 1 点</li>
 *   <li>{@link ScheduleKind#INTERVAL} … 毎時「{@link #offsetMinutes} + n × {@link #intervalMinutes}」分
 *       （例: 間隔 5・ずらし 1 → 01, 06, 11, …, 56 分）</li>
 * </ul>
 *
 * <p>時刻は**常に Asia/Tokyo のローカル時刻**として扱う（サーバーの OS タイムゾーンに依存しない）。
 * この record は「いつ動かすか」だけを持ち、業務（端末切替・動画取込・AI 分析）は持たない。</p>
 *
 * @param taskCode        バッチコード
 * @param kind            スケジュール種別
 * @param enabled         有効か（BAT_バッチコントロール情報 の値。無効なら自動実行しない）
 * @param dailyTime       DAILY のときの実行時刻
 * @param intervalMinutes INTERVAL のときの実行間隔（分）
 * @param offsetMinutes   INTERVAL のときのずらし（分。0〜間隔-1）
 */
public record TaskSchedule(
        String taskCode,
        ScheduleKind kind,
        boolean enabled,
        LocalTime dailyTime,
        Integer intervalMinutes,
        Integer offsetMinutes) {

    public TaskSchedule {
        if (taskCode == null || taskCode.isBlank()) {
            throw new IllegalArgumentException("taskCode は必須です。");
        }
        if (kind == null) {
            throw new IllegalArgumentException("kind は必須です: " + taskCode);
        }
        if (kind == ScheduleKind.DAILY && dailyTime == null) {
            throw new IllegalArgumentException("DAILY には時刻が必要です: " + taskCode);
        }
        if (kind == ScheduleKind.INTERVAL) {
            if (intervalMinutes == null || intervalMinutes <= 0) {
                throw new IllegalArgumentException("INTERVAL には正の実行間隔が必要です: " + taskCode);
            }
            if (offsetMinutes == null || offsetMinutes < 0 || offsetMinutes >= intervalMinutes) {
                throw new IllegalArgumentException(
                        "ずらしは 0〜実行間隔-1 の範囲で指定してください: " + taskCode
                                + "（間隔=" + intervalMinutes + " ずらし=" + offsetMinutes + "）");
            }
        }
    }

    /** 定時（DAILY）を作る。 */
    public static TaskSchedule daily(String taskCode, boolean enabled, LocalTime time) {
        return new TaskSchedule(taskCode, ScheduleKind.DAILY, enabled, time, null, null);
    }

    /** 循環（INTERVAL）を作る。 */
    public static TaskSchedule interval(String taskCode, boolean enabled, int everyMinutes, int offsetMinutes) {
        return new TaskSchedule(taskCode, ScheduleKind.INTERVAL, enabled, null, everyMinutes, offsetMinutes);
    }

    /**
     * {@code now} 以前で**最後に到来した計画実行点**（まだ来ていなければ空）。
     *
     * <p>スケジューラはこれを「今回はこの点を実行する」として確保する。何度も取りこぼしても
     * **最新の 1 点だけ**が返るので、取りこぼしの一括補跑にはならない。</p>
     */
    public LocalDateTime previousPointAtOrBefore(LocalDateTime now) {
        if (kind == ScheduleKind.DAILY) {
            LocalDateTime today = now.toLocalDate().atTime(dailyTime);
            return today.isAfter(now) ? today.minusDays(1) : today;
        }
        int every = intervalMinutes;
        // その時の「ずらし + n×間隔」の点（now 以前で最大のもの）
        int minute = now.getMinute();
        int latest = offsetMinutes + ((minute - offsetMinutes) / every) * every;
        if (latest > minute) {
            latest -= every;
        }
        LocalDateTime candidate = (latest < 0)
                ? now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).minusMinutes(every - offsetMinutes)
                : now.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusMinutes(latest);
        return candidate.withSecond(0).withNano(0);
    }

    /**
     * {@code after} より後に来る**最初の計画実行点**（画面の「次回実行時刻」用）。
     */
    public LocalDateTime nextPointAfter(LocalDateTime after) {
        if (kind == ScheduleKind.DAILY) {
            LocalDateTime today = after.toLocalDate().atTime(dailyTime);
            return today.isAfter(after) ? today : today.plusDays(1);
        }
        int every = intervalMinutes;
        int minute = after.getMinute();
        int next = offsetMinutes + ((minute - offsetMinutes) / every) * every;
        if (next <= minute) {
            next += every;
        }
        LocalDateTime candidate = after.truncatedTo(java.time.temporal.ChronoUnit.HOURS).plusMinutes(next)
                .withSecond(0).withNano(0);
        return candidate.isAfter(after) ? candidate : candidate.plusMinutes(every);
    }

    /**
     * 1 日（{@code date}）の計画実行点（画面の「実行時間の例」用。INTERVAL は 24 時間分）。
     */
    public List<LocalTime> pointsOfDay(LocalDate date) {
        if (kind == ScheduleKind.DAILY) {
            return List.of(dailyTime);
        }
        List<LocalTime> points = new ArrayList<>();
        for (int minute = offsetMinutes; minute < 60; minute += intervalMinutes) {
            points.add(LocalTime.of(minute / 60, minute % 60));
        }
        return List.copyOf(points);
    }

    /** 画面に出す説明（例: 「毎日 23:30」「毎時 01/06/…/56 分（5 分間隔・ずらし 1 分）」）。 */
    public String describe() {
        if (kind == ScheduleKind.DAILY) {
            return "毎日 " + dailyTime;
        }
        List<String> labels = pointsOfDay(LocalDate.now()).stream()
                .map(time -> String.format("%02d", time.getMinute()))
                .toList();
        String example = labels.size() <= 4
                ? String.join("/", labels)
                : String.join("/", labels.subList(0, 2)) + "/…/" + labels.get(labels.size() - 1);
        return "毎時 " + example + " 分（" + intervalMinutes + " 分間隔・ずらし " + offsetMinutes + " 分）";
    }
}

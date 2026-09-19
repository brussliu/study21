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
 * @param effectiveFrom   この設定が**効き始めた時刻**（Asia/Tokyo）。この時刻以前の計画実行点は
 *                        実行しない（利用者が設定を変えた瞬間に、過去の点を今さら実行しないため）。
 *                        {@code null} は制限なし（起動時に DB から読んだ「まだ変更していない」状態）
 */
public record TaskSchedule(
        String taskCode,
        ScheduleKind kind,
        boolean enabled,
        LocalTime dailyTime,
        Integer intervalMinutes,
        Integer offsetMinutes,
        LocalDateTime effectiveFrom) {

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
        return new TaskSchedule(taskCode, ScheduleKind.DAILY, enabled, time, null, null, null);
    }

    /** 循環（INTERVAL）を作る。 */
    public static TaskSchedule interval(String taskCode, boolean enabled, int everyMinutes, int offsetMinutes) {
        return new TaskSchedule(taskCode, ScheduleKind.INTERVAL, enabled, null, everyMinutes, offsetMinutes, null);
    }

    /** 設定の適用時刻を付けた複製（この時刻以前の計画実行点は実行しない）。 */
    public TaskSchedule withEffectiveFrom(LocalDateTime effectiveFrom) {
        return new TaskSchedule(taskCode, kind, enabled, dailyTime, intervalMinutes, offsetMinutes, effectiveFrom);
    }

    /**
     * この計画実行点を実行してよいか（**設定の適用時刻より後**か）。
     *
     * <p>利用者が 22:00 に「停止時刻 23:30 → 21:00」と変えたとき、21:00 の点（今日）を
     * 今さら実行しないための判定。適用時刻が無い（起動時の設定）ときは制限しない
     * （サービス再起動の補執行は今までどおり）。</p>
     */
    public boolean canRunAt(LocalDateTime point) {
        return point != null && (effectiveFrom == null || point.isAfter(effectiveFrom));
    }

    /**
     * {@code now} 以前で**最後に到来した実行可能な計画実行点**（無ければ空）。
     *
     * <p>設定の適用時刻以前の点しか無いときは空を返す（＝何もしない）。
     * 何度取りこぼしても最新の 1 点だけが返ることは変えない。</p>
     */
    public java.util.Optional<LocalDateTime> previousRunnablePointAtOrBefore(LocalDateTime now) {
        LocalDateTime latest = previousPointAtOrBefore(now);
        return canRunAt(latest) ? java.util.Optional.of(latest) : java.util.Optional.empty();
    }

    /**
     * {@code after} より後で**次に実行する計画実行点**（画面の「次回実行時刻」）。
     *
     * <p>スケジューラが実行する点と同じ規則（適用時刻より後）で返す。画面と実際の動作を一致させる。</p>
     */
    public LocalDateTime nextRunnablePointAfter(LocalDateTime after) {
        LocalDateTime next = nextPointAfter(after);
        while (!canRunAt(next)) {
            next = nextPointAfter(next);
        }
        return next;
    }

    /** 実行タイミングの中身（時刻・間隔・ずらし）が同じか（設定変更の検出に使う）。 */
    public boolean sameTiming(TaskSchedule other) {
        return other != null && kind == other.kind && enabled == other.enabled
                && java.util.Objects.equals(dailyTime, other.dailyTime)
                && java.util.Objects.equals(intervalMinutes, other.intervalMinutes)
                && java.util.Objects.equals(offsetMinutes, other.offsetMinutes);
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

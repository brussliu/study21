package com.study21.admin.schedule;

/**
 * バッチのスケジュール種別。
 *
 * <p>2.1 の統一スケジューラが扱う 2 種類だけ:</p>
 * <ul>
 *   <li>{@link #DAILY} … 1 日 1 回の定時（batR03 / batR04。時刻は COM_設定情報）</li>
 *   <li>{@link #INTERVAL} … 毎時「ずらし + n × 実行間隔」分（batL02 / batL03。間隔とずらしは COM_設定情報）</li>
 * </ul>
 *
 * <p>2.0 の {@code BatL02FiveMinuteScheduler} のような**固定 5 分**の専用スケジューラは作らない
 * （間隔は設定で変えられるため）。</p>
 */
public enum ScheduleKind {
    /** 定時（1 日 1 回。時刻は設定）。 */
    DAILY,
    /** 循環（毎時、ずらし + n × 実行間隔 分）。 */
    INTERVAL
}

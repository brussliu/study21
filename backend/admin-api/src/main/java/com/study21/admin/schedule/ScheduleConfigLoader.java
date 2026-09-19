package com.study21.admin.schedule;

import java.util.Map;

/**
 * スケジュール設定の**DB 読み込み**（1 回の検査・1 回の更新でまとめて読む）。
 *
 * <p>実装は MyBatis の Mapper を使う（{@code SqlLoggingInterceptor} が SQL を記録する）。
 * テストでは差し替えて、DB 無しでキャッシュの振る舞いを確かめられるようにしてある。</p>
 */
public interface ScheduleConfigLoader {

    /**
     * 対象タスクの問題部分（COM_設定情報）と有効／無効（BAT_バッチコントロール情報）を
     * **まとめて**読む（タスクごとに 1 回ずつ問い合わせない）。
     *
     * @param settingKeys カタログが必要とする設定キー
     * @param taskCodes   対象タスクのコード
     * @return 設定値と有効状態
     * @throws ScheduleConfigLoadException DB に触れない（接続不能・タイムアウトなど）
     */
    ScheduleSourceData load(java.util.List<String> settingKeys, java.util.List<String> taskCodes);

    /**
     * DB から読んだ生の値。
     *
     * @param settings       設定キー → 設定値（COM_設定情報。行が無いキーは含まれない）
     * @param enabledByTask  タスクコード → 有効か（BAT_バッチコントロール情報。行が無いタスクは含まれない）
     */
    record ScheduleSourceData(Map<String, String> settings, Map<String, Boolean> enabledByTask) {
    }

    /** DB から読めなかった（値が不正な場合とは区別する）。 */
    class ScheduleConfigLoadException extends RuntimeException {
        public ScheduleConfigLoadException(String message, Throwable cause) {
            super(message, cause);
        }

        public ScheduleConfigLoadException(String message) {
            super(message);
        }
    }
}

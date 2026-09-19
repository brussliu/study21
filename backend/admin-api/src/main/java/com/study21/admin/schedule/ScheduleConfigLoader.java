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
     * 対象タスクの設定値（COM_設定情報）・有効／無効（BAT_バッチコントロール情報）・
     * 設定の適用時刻と計画バージョン（BAT_スケジュール状態情報）を **まとめて**読む
     * （タスクごとに 1 回ずつ問い合わせない）。
     *
     * <p><b>この読み込みは何も書かない。</b>適用時刻と計画バージョンは、設定値の保存と
     * **同じトランザクション**で書かれる（{@code ScheduleTimingRecorder}）。読み込みの途中で
     * 補って書くと、書けたかどうか分からないまま「新しい設定＋古い適用時刻」が残りうる。</p>
     *
     * @param settingKeys カタログが必要とする設定キー
     * @param taskCodes   対象タスクのコード
     * @return 設定値・有効状態・適用時刻・計画バージョン
     * @throws ScheduleConfigLoadException DB に触れない（接続不能・タイムアウトなど）
     */
    ScheduleSourceData load(java.util.List<String> settingKeys, java.util.List<String> taskCodes);

    /**
     * DB から読んだ生の値。
     *
     * @param settings       設定キー → 設定値（COM_設定情報。行が無いキーは含まれない）
     * @param enabledByTask  タスクコード → 有効か（BAT_バッチコントロール情報。行が無いタスクは含まれない）
     * @param configEffectiveFrom タスクコード → 設定の適用時刻（BAT_スケジュール状態情報。未設定は含まれない）
     * @param planVersions   タスクコード → 計画バージョン（BAT_スケジュール状態情報。「設定値の保存と
     *                       同じトランザクションで 1 つ進む」版。行が無い・未記録のタスクは含まれない）
     */
    record ScheduleSourceData(Map<String, String> settings, Map<String, Boolean> enabledByTask,
                              Map<String, java.time.LocalDateTime> configEffectiveFrom,
                              Map<String, Long> planVersions) {
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

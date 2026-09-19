package com.study21.admin.schedule;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBatis でスケジュール設定を読む {@link ScheduleConfigLoader} の実装。
 *
 * <p>「設定値（COM_設定情報）」「有効／無効（BAT_バッチコントロール情報）」「設定の適用時刻と
 * 計画バージョン（BAT_スケジュール状態情報）」を**1 つの読み取り専用トランザクション
 * （REPEATABLE READ）の中で 3 クエリ**でまとめて読む（タスクごとに 1 回ずつ問い合わせない）。読み込み中の例外は
 * {@link ScheduleConfigLoadException} に包み、呼び出し側（{@link ScheduleConfigService}）が
 * 「前の有効な設定を残して再試行」を判断できるようにする。</p>
 */
@Component
public class MyBatisScheduleConfigLoader implements ScheduleConfigLoader {

    private final ScheduleConfigMapper mapper;
    private final SchedulePlanMapper planMapper;

    public MyBatisScheduleConfigLoader(ScheduleConfigMapper mapper, SchedulePlanMapper planMapper) {
        this.mapper = mapper;
        this.planMapper = planMapper;
    }

    /**
     * 3 つの表（設定値・有効／無効・適用時刻と計画バージョン）を**同じスナップショット**で読む。
     *
     * <p><b>なぜ 1 本のトランザクションで読むか（単一 SQL ではなく）</b>:
     * 3 つは別の表にあり、1 文にすると「種類の違う行を UNION して Java で振り分ける」形になって
     * 既存の Mapper と SQL ログの形を崩す。PostgreSQL の {@code REPEATABLE READ} は
     * **トランザクションの中で 1 つのスナップショット**を使うので、3 クエリでも
     * 「設定値は新しい・有効状態は古い」という混ざった読み方をしない
     * （既定の {@code READ COMMITTED} は**文ごと**にスナップショットを取るため混ざりうる）。</p>
     *
     * <p>読み取り専用（{@code readOnly = true}）なので副作用は無い。MyBatis は Spring の
     * トランザクションに紐づいた**同じ接続**を使う（{@code SqlSessionTemplate} の既定の動き）。</p>
     *
     * <p>呼び出し側はトランザクションの外から呼ぶ（{@code ScheduleConfigService} は別の Bean で、
     * 設定の保存が**コミットしたあと**に呼ぶ）。ここで読み込みの途中に
     * 適用時刻や計画バージョンを**書かない**（設定値の保存と同じトランザクションで書く）。</p>
     */
    @Override
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public ScheduleSourceData load(List<String> settingKeys, List<String> taskCodes) {
        try {
            List<String> pageCodes = List.of("NET_CONTROL", "STUDY_MONITOR");
            Map<String, String> settings = new LinkedHashMap<>();
            for (Map<String, Object> row : mapper.findSettingValues(pageCodes, settingKeys)) {
                Object key = row.get("settingKey");
                Object value = row.get("settingValue");
                if (key != null) {
                    settings.put(String.valueOf(key), value == null ? null : String.valueOf(value));
                }
            }
            Map<String, Boolean> enabledByTask = new LinkedHashMap<>();
            for (Map<String, Object> row : mapper.findControlStatuses(taskCodes)) {
                Object code = row.get("batchCode");
                Object status = row.get("status");
                if (code != null) {
                    enabledByTask.put(String.valueOf(code), "1".equals(String.valueOf(status)));
                }
            }
            // 設定の適用時刻（利用者が最後に設定を変えた時刻）と計画バージョン。再起動でも引き継ぐ
            Map<String, java.time.LocalDateTime> effectiveFrom = new LinkedHashMap<>();
            Map<String, Long> planVersions = new LinkedHashMap<>();
            for (Map<String, Object> row : planMapper.findPlans(taskCodes)) {
                Object code = row.get("batchCode");
                if (code == null) {
                    continue;
                }
                Object effective = row.get("configEffectiveFrom");
                if (effective instanceof java.sql.Timestamp timestamp) {
                    effectiveFrom.put(String.valueOf(code), timestamp.toLocalDateTime());
                }
                Object version = row.get("planVersion");
                if (version instanceof Number number) {
                    planVersions.put(String.valueOf(code), number.longValue());
                }
            }
            return new ScheduleSourceData(settings, enabledByTask, effectiveFrom, planVersions);
        } catch (RuntimeException cause) {
            throw new ScheduleConfigLoadException("スケジュール設定を DB から読めませんでした: " + messageOf(cause), cause);
        }
    }

    private static String messageOf(Throwable cause) {
        String message = cause.getMessage();
        return message == null || message.isBlank() ? cause.getClass().getSimpleName() : message.strip();
    }
}

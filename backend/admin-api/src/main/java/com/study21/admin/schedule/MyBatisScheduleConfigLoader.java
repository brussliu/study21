package com.study21.admin.schedule;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * MyBatis でスケジュール設定を読む {@link ScheduleConfigLoader} の実装。
 *
 * <p>「設定値（COM_設定情報）」「有効／無効（BAT_バッチコントロール情報）」「設定の適用時刻と
 * 計画バージョン（BAT_スケジュール状態情報）」を**3 クエリ**でまとめて読む（タスクごとに 1 回ずつ問い合わせない）。読み込み中の例外は
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

    @Override
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

package com.study21.user.net;

import com.study21.common.core.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * サイトアクセス履歴の照会実装。
 */
@Service
public class NetAccessLogServiceImpl implements NetAccessLogService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    /** 集計する日数（2.0 と同じ 3 日） */
    private static final int USAGE_DAYS = 3;
    private static final int HOURS_PER_DAY = 24;

    /** サイトの区分（区分コード）。2.0 の「1.通常 / 2.休憩 / 3.ゲーム」に対応する。 */
    private static final List<String> SITE_KINDS = List.of("STUDY", "NORMAL", "BREAK", "GAME");

    private final NetAccessLogMapper netAccessLogMapper;

    public NetAccessLogServiceImpl(NetAccessLogMapper netAccessLogMapper) {
        this.netAccessLogMapper = netAccessLogMapper;
    }

    @Override
    public NetAccessLogModels.AccessLogSearchResult search(String host, String terminalName,
                                                           String result, int page, int size) {
        String resultCode = normalizeResult(result);
        String hostFilter = blankToNull(host);
        String terminalFilter = blankToNull(terminalName);

        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int safePage = Math.max(1, page);

        long total = netAccessLogMapper.count(hostFilter, terminalFilter, resultCode);
        List<NetAccessLogModels.AccessLogRow> items = netAccessLogMapper.search(
                        hostFilter, terminalFilter, resultCode, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(NetAccessLogServiceImpl::toRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);

        return new NetAccessLogModels.AccessLogSearchResult(items, total, safePage, safeSize, totalPages);
    }

    @Override
    public NetAccessLogModels.HourlyUsageSummary hourlyUsage(String terminalName, String kindCode) {
        String terminalFilter = blankToNull(terminalName);
        String kindFilter = normalizeKind(kindCode);

        LocalDate today = LocalDate.now();
        LocalDate oldest = today.minusDays(USAGE_DAYS - 1L);
        Timestamp receivedFrom = Timestamp.valueOf(oldest.atStartOfDay());

        List<List<Long>> buckets = new ArrayList<>();
        for (int day = 0; day < USAGE_DAYS; day += 1) {
            List<Long> hours = new ArrayList<>();
            for (int hour = 0; hour < HOURS_PER_DAY; hour += 1) {
                hours.add(0L);
            }
            buckets.add(hours);
        }

        long total = 0L;
        for (NetAccessLogModels.HourlyUsageBucket row : netAccessLogMapper
                .hourlySummary(terminalFilter, kindFilter, receivedFrom)) {
            if (row == null || row.bucketAt() == null) {
                continue;
            }
            LocalDateTime bucketAt = row.bucketAt().toLocalDateTime();
            long dayDiff = today.toEpochDay() - bucketAt.toLocalDate().toEpochDay();
            if (dayDiff < 0 || dayDiff >= USAGE_DAYS) {
                continue;
            }
            long count = Math.max(0L, row.requestCount());
            int dayIndex = (int) dayDiff;
            int hourIndex = bucketAt.getHour();
            buckets.get(dayIndex).set(hourIndex, buckets.get(dayIndex).get(hourIndex) + count);
            total += count;
        }

        List<String> days = new ArrayList<>();
        for (int day = 0; day < USAGE_DAYS; day += 1) {
            days.add(today.minusDays(day).toString());
        }
        return new NetAccessLogModels.HourlyUsageSummary(days, buckets, total, terminalFilter, kindFilter);
    }

    /** エンティティを画面に返す形へ（結果コードは応答状態コードから決める）。 */
    private static NetAccessLogModels.AccessLogRow toRow(NetAccessLogEntity entity) {
        return new NetAccessLogModels.AccessLogRow(
                entity.getLogId() == null ? 0L : entity.getLogId(),
                entity.getReceivedAt(),
                entity.getClientIp(),
                entity.getTerminalName(),
                entity.getHttpMethod(),
                entity.getHost(),
                entity.getUrl(),
                entity.getStatusCode(),
                entity.getStatusCode() == null ? "ALLOW" : "DENY",
                entity.getErrorDetail());
    }

    private String normalizeResult(String result) {
        String value = blankToNull(result);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase();
        if (!NetAccessLogModels.RESULTS.contains(upper)) {
            throw new ValidationException("結果は ALLOW / DENY のいずれかを指定してください。");
        }
        return upper;
    }

    private String normalizeKind(String kindCode) {
        String value = blankToNull(kindCode);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase(Locale.ROOT);
        if (!SITE_KINDS.contains(upper)) {
            throw new ValidationException("区分は STUDY / NORMAL / BREAK / GAME のいずれかを指定してください。");
        }
        return upper;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

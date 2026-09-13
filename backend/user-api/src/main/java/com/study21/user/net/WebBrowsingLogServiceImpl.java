package com.study21.user.net;

import com.study21.common.core.exception.ValidationException;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Web閲覧履歴の照会実装。
 */
@Service
public class WebBrowsingLogServiceImpl implements WebBrowsingLogService {

    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final WebBrowsingLogMapper webBrowsingLogMapper;

    public WebBrowsingLogServiceImpl(WebBrowsingLogMapper webBrowsingLogMapper) {
        this.webBrowsingLogMapper = webBrowsingLogMapper;
    }

    @Override
    public WebBrowsingLogModels.BrowsingLogSearchResult search(String terminalId, String terminalName,
                                                               String domain, String eventType, String keyword,
                                                               String dateFrom, String dateTo, int page, int size) {
        String event = normalizeEventType(eventType);
        String from = normalizeDate(dateFrom);
        String to = normalizeDate(dateTo);

        int safeSize = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int safePage = Math.max(1, page);
        String terminalIdFilter = blankToNull(terminalId);
        String terminalNameFilter = blankToNull(terminalName);
        String domainFilter = blankToNull(domain);
        String keywordFilter = blankToNull(keyword);

        long total = webBrowsingLogMapper.count(terminalIdFilter, terminalNameFilter, domainFilter,
                event, keywordFilter, from, to);
        List<WebBrowsingLogModels.BrowsingLogRow> items = webBrowsingLogMapper.search(
                        terminalIdFilter, terminalNameFilter, domainFilter, event, keywordFilter,
                        from, to, safeSize, (safePage - 1) * safeSize)
                .stream()
                .map(WebBrowsingLogServiceImpl::toRow)
                .toList();
        int totalPages = (int) Math.ceil((double) total / safeSize);

        return new WebBrowsingLogModels.BrowsingLogSearchResult(items, total, safePage, safeSize, totalPages);
    }

    /** エンティティを画面に返す形へ。 */
    private static WebBrowsingLogModels.BrowsingLogRow toRow(WebBrowsingLogEntity entity) {
        return new WebBrowsingLogModels.BrowsingLogRow(
                entity.getLogId() == null ? 0L : entity.getLogId(),
                entity.getAccessedAt(),
                entity.getTerminalId(),
                entity.getTerminalName(),
                entity.getEventType(),
                entity.getDomain(),
                entity.getUrl(),
                entity.getPageTitle(),
                entity.getActiveFlag() == null ? "0" : entity.getActiveFlag(),
                entity.getStaySeconds(),
                entity.getViewCount());
    }

    /** イベント種別は 2.0 が送ってくる 5 種類だけを受け付ける。 */
    private String normalizeEventType(String eventType) {
        String value = blankToNull(eventType);
        if (value == null) {
            return null;
        }
        String upper = value.toUpperCase();
        if (!WebBrowsingLogModels.EVENT_TYPES.contains(upper)) {
            throw new ValidationException("イベント種別は " + String.join(" / ", WebBrowsingLogModels.EVENT_TYPES)
                    + " のいずれかを指定してください。");
        }
        return upper;
    }

    /** yyyy-MM-dd だけを受け付ける（空は絞り込み無し）。 */
    private String normalizeDate(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return null;
        }
        if (!text.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new ValidationException("日付は yyyy-MM-dd の形式で指定してください。");
        }
        return text;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

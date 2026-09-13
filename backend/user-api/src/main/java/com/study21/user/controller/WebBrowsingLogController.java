package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.net.WebBrowsingLogModels;
import com.study21.user.net.WebBrowsingLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Web閲覧履歴 API（user-api）。
 *
 * <p>インターネット利用履歴（ネットワーク制御）の「Web閲覧履歴」タブが使う。
 * 2.0 の履歴管理画面（history.jsp）の「ブラウザ閲覧履歴」に当たる。</p>
 */
@RestController
@RequestMapping("/api/user/web-browsing-logs")
public class WebBrowsingLogController {

    private final WebBrowsingLogService webBrowsingLogService;

    public WebBrowsingLogController(WebBrowsingLogService webBrowsingLogService) {
        this.webBrowsingLogService = webBrowsingLogService;
    }

    @GetMapping
    public ApiResponse<WebBrowsingLogModels.BrowsingLogSearchResult> search(
            @RequestParam(value = "terminalId", required = false) String terminalId,
            @RequestParam(value = "terminalName", required = false) String terminalName,
            @RequestParam(value = "domain", required = false) String domain,
            @RequestParam(value = "eventType", required = false) String eventType,
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(webBrowsingLogService.search(terminalId, terminalName, domain, eventType,
                keyword, dateFrom, dateTo, page, size));
    }

    /** 画面の絞り込みに出すイベント種別の一覧。 */
    @GetMapping("/event-types")
    public ApiResponse<Map<String, Object>> eventTypes() {
        return ApiResponse.ok(Map.of("items", WebBrowsingLogModels.EVENT_TYPES));
    }
}

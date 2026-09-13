package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.net.NetAccessLogModels;
import com.study21.user.net.NetAccessLogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * サイトアクセス履歴 API（user-api）。
 *
 * <p>インターネット利用履歴（ネットワーク制御）の「サイトアクセス履歴」タブが使う。
 * 2.0 の履歴管理画面（history.jsp）の「上網履歴」に当たる。</p>
 */
@RestController
@RequestMapping("/api/user/net-access-logs")
public class NetAccessLogController {

    private final NetAccessLogService netAccessLogService;

    public NetAccessLogController(NetAccessLogService netAccessLogService) {
        this.netAccessLogService = netAccessLogService;
    }

    @GetMapping
    public ApiResponse<NetAccessLogModels.AccessLogSearchResult> search(
            @RequestParam(value = "host", required = false) String host,
            @RequestParam(value = "terminalName", required = false) String terminalName,
            @RequestParam(value = "result", required = false) String result,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "size", defaultValue = "20") int size) {
        return ApiResponse.ok(netAccessLogService.search(host, terminalName, result, page, size));
    }

    /** 最近 3 日の時間帯別の件数（ホームの「最近3日 上網状況（時間帯別）」）。 */
    @GetMapping("/hourly-summary")
    public ApiResponse<NetAccessLogModels.HourlyUsageSummary> hourlySummary(
            @RequestParam(value = "terminalName", required = false) String terminalName,
            @RequestParam(value = "kind", required = false) String kind) {
        return ApiResponse.ok(netAccessLogService.hourlyUsage(terminalName, kind));
    }
}

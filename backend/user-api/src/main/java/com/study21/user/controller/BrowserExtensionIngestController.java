package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.browserext.BrowserExtensionModels;
import com.study21.user.browserext.BrowserExtensionService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ブラウザ拡張からの受信 API（user-api。ログイン不要・接続コードで認証）。
 *
 * <ul>
 *   <li>`POST /register` … 起動時の端末登録（upsert）</li>
 *   <li>`POST /heartbeat` … 5 分ごとの生存通知</li>
 *   <li>`POST /events` … 閲覧イベントのバッチ登録（最大 500 件。拡張は 100 件ずつ送る）</li>
 * </ul>
 *
 * <p>すべて `X-Study21-Extension-Token` ヘッダに接続コードを要求する
 * （{@code /api/user/browser-extension/registration} で発行したもの）。
 * 接続コードが無い・違う場合は 401 を返す。2.0 はリクエストの userId を信じていたが、
 * 2.1 では持ち主は接続コードからのみ決まる。</p>
 */
@RestController
@RequestMapping("/api/user/browser-extension")
public class BrowserExtensionIngestController {

    private static final String TOKEN_HEADER = "X-Study21-Extension-Token";

    private final BrowserExtensionService browserExtensionService;

    public BrowserExtensionIngestController(BrowserExtensionService browserExtensionService) {
        this.browserExtensionService = browserExtensionService;
    }

    @PostMapping("/register")
    public ApiResponse<BrowserExtensionModels.RegisterResult> register(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody BrowserExtensionModels.RegisterRequest request) {
        BrowserExtensionModels.RegisterResult result = browserExtensionService.register(token, request);
        return ApiResponse.ok(result, result.message());
    }

    @PostMapping("/heartbeat")
    public ApiResponse<BrowserExtensionModels.HeartbeatResult> heartbeat(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody BrowserExtensionModels.HeartbeatRequest request) {
        BrowserExtensionModels.HeartbeatResult result = browserExtensionService.heartbeat(token, request);
        return ApiResponse.ok(result, result.message());
    }

    @PostMapping("/events")
    public ApiResponse<BrowserExtensionModels.BatchSaveResult> events(
            @RequestHeader(value = TOKEN_HEADER, required = false) String token,
            @Valid @RequestBody BrowserExtensionModels.EventBatchRequest request) {
        BrowserExtensionModels.BatchSaveResult result = browserExtensionService.saveEvents(token, request);
        return ApiResponse.ok(result, result.message());
    }
}

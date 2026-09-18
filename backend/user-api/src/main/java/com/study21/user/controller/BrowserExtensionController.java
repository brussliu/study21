package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.browserext.BrowserExtensionModels;
import com.study21.user.browserext.BrowserExtensionService;
import com.study21.user.security.UserPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * ブラウザ拡張の接続設定 API（user-api。画面＝ログイン中の本人が使う）。
 *
 * <ul>
 *   <li>`GET  /registration` … 接続コードと接続中の端末（未発行なら issued=false）</li>
 *   <li>`POST /registration` … 接続コードを発行（発行済みならそのまま返す）</li>
 *   <li>`POST /registration/reissue` … 接続コードを再発行（以前のコードは無効）</li>
 * </ul>
 *
 * <p>インターネット利用履歴（`/internet-usage`）の「Web閲覧履歴」タブの
 * 「ブラウザ拡張」カードが使う。拡張からの受信は
 * {@link BrowserExtensionIngestController}（接続コードで認証）。</p>
 */
@RestController
@RequestMapping("/api/user/browser-extension")
public class BrowserExtensionController {

    private final BrowserExtensionService browserExtensionService;

    public BrowserExtensionController(BrowserExtensionService browserExtensionService) {
        this.browserExtensionService = browserExtensionService;
    }

    @GetMapping("/registration")
    public ApiResponse<BrowserExtensionModels.RegistrationView> registration(
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(browserExtensionService.registration(user.accountId()));
    }

    @PostMapping("/registration")
    public ApiResponse<BrowserExtensionModels.RegistrationView> issue(
            @AuthenticationPrincipal UserPrincipal user) {
        BrowserExtensionModels.RegistrationView view = browserExtensionService.issueToken(user.accountId());
        return ApiResponse.ok(view, view.message());
    }

    @PostMapping("/registration/reissue")
    public ApiResponse<BrowserExtensionModels.RegistrationView> reissue(
            @AuthenticationPrincipal UserPrincipal user) {
        BrowserExtensionModels.RegistrationView view = browserExtensionService.reissueToken(user.accountId());
        return ApiResponse.ok(view, view.message());
    }
}

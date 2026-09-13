package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.account.AccountProfileService;
import com.study21.user.account.PasswordChangeRequest;
import com.study21.user.account.ProfileUpdateRequest;
import com.study21.user.account.UserProfileResponse;
import com.study21.user.security.UserPrincipal;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 「ユーザー情報の修正」「パスワード変更」の API。
 *
 * どちらもログイン中のアカウント自身だけを対象にする（他人の情報は扱わない）。
 * メールアドレス（＝ログインID）は変更できないため、更新リクエストにも含まれない。
 * パスワード変更は現在のパスワードで本人確認する。
 */
@RestController
@RequestMapping("/api/user/profile")
public class AccountProfileController {

    private final AccountProfileService accountProfileService;

    public AccountProfileController(AccountProfileService accountProfileService) {
        this.accountProfileService = accountProfileService;
    }

    /** 自分の情報を取得する（メールアドレス・権限は参照のみ）。 */
    @GetMapping
    public ApiResponse<UserProfileResponse> profile(@AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(accountProfileService.getProfile(user));
    }

    /** 自分の情報を更新する（メールアドレス・権限は変更対象外）。 */
    @PutMapping
    public ApiResponse<UserProfileResponse> update(@AuthenticationPrincipal UserPrincipal user,
                                                   @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.ok(accountProfileService.updateProfile(user, request), "ユーザー情報を更新しました。");
    }

    /** パスワードを変更する（現在のパスワードで本人確認。成功後もログインは継続する）。 */
    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal UserPrincipal user,
                                            @Valid @RequestBody PasswordChangeRequest request) {
        accountProfileService.changePassword(user, request);
        return ApiResponse.ok(null, "パスワードを変更しました。");
    }
}

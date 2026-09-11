package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.account.PasswordResetConfirmRequest;
import com.study21.user.account.PasswordResetRequest;
import com.study21.user.account.PasswordResetService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * パスワード再設定の公開 API（認証前）。
 */
@RestController
@RequestMapping("/api/user/password")
public class PasswordResetController {

    private final PasswordResetService passwordResetService;

    public PasswordResetController(PasswordResetService passwordResetService) {
        this.passwordResetService = passwordResetService;
    }

    /** パスワード再設定の依頼（トークン発行）。アカウントの有無は応答で区別しない。 */
    @PostMapping("/forgot")
    public ApiResponse<Void> forgot(@Valid @RequestBody PasswordResetRequest request) {
        passwordResetService.requestReset(request.getLoginId());
        return ApiResponse.ok(null, "登録済みのメールアドレスに再設定のご案内を送信しました。");
    }

    /** トークンを検証して新しいパスワードを設定する。 */
    @PostMapping("/reset")
    public ApiResponse<Void> reset(@Valid @RequestBody PasswordResetConfirmRequest request) {
        passwordResetService.confirmReset(request.getToken(), request.getNewPassword());
        return ApiResponse.ok(null, "パスワードを再設定しました。");
    }
}

package com.study21.user.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * パスワード再設定の確定リクエスト（トークン + 新しいパスワード）。
 */
public class PasswordResetConfirmRequest {

    @NotBlank(message = "再設定用トークンを入力してください。")
    private String token;

    @NotBlank(message = "新しいパスワードを入力してください。")
    @Pattern(regexp = RegisterRequest.PASS_RE,
            message = "パスワードは8文字以上で、英字と数字をそれぞれ1文字以上含めてください。")
    private String newPassword;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

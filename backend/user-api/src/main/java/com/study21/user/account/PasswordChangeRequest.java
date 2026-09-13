package com.study21.user.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * パスワード変更のリクエスト（現在のパスワードで本人確認する）。
 * 確認用入力（もう一度入力）は画面側で照合し、API へは送らない。
 */
public class PasswordChangeRequest {

    @NotBlank(message = "現在のパスワードを入力してください。")
    private String currentPassword;

    @NotBlank(message = "新しいパスワードを入力してください。")
    @Pattern(regexp = RegisterRequest.PASS_RE,
            message = "パスワードは8文字以上で、英字と数字をそれぞれ1文字以上含めてください。")
    private String newPassword;

    public String getCurrentPassword() {
        return currentPassword;
    }

    public void setCurrentPassword(String currentPassword) {
        this.currentPassword = currentPassword;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public void setNewPassword(String newPassword) {
        this.newPassword = newPassword;
    }
}

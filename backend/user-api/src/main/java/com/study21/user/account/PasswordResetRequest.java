package com.study21.user.account;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * パスワード再設定の依頼リクエスト（メールアドレス入力）。
 */
public class PasswordResetRequest {

    @NotBlank(message = "メールアドレスを入力してください。")
    @Email(message = "メールアドレスの形式が正しくありません。")
    private String loginId;

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }
}

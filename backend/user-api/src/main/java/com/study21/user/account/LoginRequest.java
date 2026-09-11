package com.study21.user.account;

import jakarta.validation.constraints.NotBlank;

/**
 * ログインリクエスト。
 */
public class LoginRequest {

    @NotBlank(message = "メールアドレスを入力してください。")
    private String loginId;

    @NotBlank(message = "パスワードを入力してください。")
    private String password;

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}

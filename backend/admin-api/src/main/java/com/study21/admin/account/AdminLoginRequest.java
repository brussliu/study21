package com.study21.admin.account;

import jakarta.validation.constraints.NotBlank;

/**
 * 管理端ログインリクエスト。
 */
public class AdminLoginRequest {

    @NotBlank(message = "ユーザーIDを入力してください。")
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

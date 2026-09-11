package com.study21.user.account;

import java.time.LocalDate;

/**
 * ログイン成功時の応答。フロントエンドはこの情報でセッション（画面ロール）を確定する。
 */
public class LoginResponse {

    private final Long accountId;
    private final String loginId;
    private final String displayName;
    private final AccountType accountType;
    private final LocalDate expiryDate;

    public LoginResponse(Long accountId, String loginId, String displayName,
                         AccountType accountType, LocalDate expiryDate) {
        this.accountId = accountId;
        this.loginId = loginId;
        this.displayName = displayName;
        this.accountType = accountType;
        this.expiryDate = expiryDate;
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getLoginId() {
        return loginId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountType getAccountType() {
        return accountType;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}

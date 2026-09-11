package com.study21.admin.account;

import java.time.LocalDate;

/**
 * 管理端ログイン成功時の応答。
 */
public class AdminLoginResponse {

    private final Long accountId;
    private final String loginId;
    private final String displayName;
    /** アカウント種別（'ADMIN' 固定）。 */
    private final String accountType;
    /** 有効期限。管理者は無期限（null）の場合がある。 */
    private final LocalDate expiryDate;

    public AdminLoginResponse(Long accountId, String loginId, String displayName,
                              String accountType, LocalDate expiryDate) {
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

    public String getAccountType() {
        return accountType;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}

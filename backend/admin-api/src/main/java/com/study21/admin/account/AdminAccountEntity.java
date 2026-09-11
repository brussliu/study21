package com.study21.admin.account;

import java.sql.Date;

/**
 * 管理端ログイン用の ACC_アカウント エンティティ（ADMIN 種別のみ）。
 */
public class AdminAccountEntity {

    private Long accountId;
    private String loginId;
    private String passwordHash;
    private String accountType;
    private String status;
    private String sei;
    private String mei;
    private Date expiryDate;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getSei() {
        return sei;
    }

    public void setSei(String sei) {
        this.sei = sei;
    }

    public String getMei() {
        return mei;
    }

    public void setMei(String mei) {
        this.mei = mei;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }
}

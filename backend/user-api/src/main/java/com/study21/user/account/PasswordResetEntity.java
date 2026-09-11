package com.study21.user.account;

import java.sql.Timestamp;

/**
 * ACC_パスワードリセット（パスワード再設定トークン）のエンティティ。
 */
public class PasswordResetEntity {

    private Long resetId;
    private Long accountId;
    private String tokenHash;
    private Timestamp expiresAt;
    private String usedFlag;
    private String createdBy;
    private String updatedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getResetId() {
        return resetId;
    }

    public void setResetId(Long resetId) {
        this.resetId = resetId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public Timestamp getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(Timestamp expiresAt) {
        this.expiresAt = expiresAt;
    }

    public String getUsedFlag() {
        return usedFlag;
    }

    public void setUsedFlag(String usedFlag) {
        this.usedFlag = usedFlag;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}

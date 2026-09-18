package com.study21.user.browserext;

import java.sql.Timestamp;

/**
 * NET_ブラウザ接続情報 の 1 行（ブラウザ拡張と 2.1 をつなぐ接続コード。1 アカウント = 1 行）。
 */
public class BrowserConnectionEntity {

    private Long connectionId;
    private Long accountId;
    private String token;
    private String status;
    private Timestamp lastUsedAt;
    private int version;
    private Timestamp createdAt;

    public Long getConnectionId() {
        return connectionId;
    }

    public void setConnectionId(Long connectionId) {
        this.connectionId = connectionId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Timestamp getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(Timestamp lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }
}

package com.study21.user.net;

import java.sql.Timestamp;

/**
 * NET_端末コントロール情報（端末コントロール）のエンティティ。
 * 値の意味は database/端末コントロール/TBL_NET_端末コントロール情報.sql の COMMENT を参照。
 */
public class NetTerminalEntity {

    private Long terminalId;
    private String ipAddress;
    private String terminalName;
    /** T=通常 / K=休憩 / G=ゲーム / B=勉強 / S=停止 / J=自由 */
    private String terminalMode;
    /** '1'=有効 / '0'=無効 */
    private String status;
    private String note;
    private Timestamp lastSeenAt;
    private Integer version;
    private Long createdByAccountId;
    private Long updatedByAccountId;
    private String createdByCode;
    private String updatedByCode;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    /** 表示用: 更新者の姓名（ACC_アカウント を結合して取得。アカウントが無い場合は null） */
    private String updatedByName;

    public Long getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(Long terminalId) {
        this.terminalId = terminalId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getTerminalName() {
        return terminalName;
    }

    public void setTerminalName(String terminalName) {
        this.terminalName = terminalName;
    }

    public String getTerminalMode() {
        return terminalMode;
    }

    public void setTerminalMode(String terminalMode) {
        this.terminalMode = terminalMode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Timestamp getLastSeenAt() {
        return lastSeenAt;
    }

    public void setLastSeenAt(Timestamp lastSeenAt) {
        this.lastSeenAt = lastSeenAt;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public Long getUpdatedByAccountId() {
        return updatedByAccountId;
    }

    public void setUpdatedByAccountId(Long updatedByAccountId) {
        this.updatedByAccountId = updatedByAccountId;
    }

    public String getCreatedByCode() {
        return createdByCode;
    }

    public void setCreatedByCode(String createdByCode) {
        this.createdByCode = createdByCode;
    }

    public String getUpdatedByCode() {
        return updatedByCode;
    }

    public void setUpdatedByCode(String updatedByCode) {
        this.updatedByCode = updatedByCode;
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

    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }
}

package com.study21.user.net;

import java.sql.Timestamp;

/**
 * NET_サイト情報（サイト管理）のエンティティ。
 * 値の意味は database/サイト管理/TBL_NET_サイト情報.sql の COMMENT を参照。
 */
public class NetSiteEntity {

    private Long siteId;
    private String siteName;
    private String siteUrl;
    /** アプリ側で正規化したホスト名（小文字・www.除去・ポート/パス除去） */
    private String hostName;
    /** STUDY / NORMAL / BREAK / GAME */
    private String kindCode;
    /** PREFIX / SUFFIX / CONTAINS / EXACT */
    private String judgeMethodCode;
    /** LEARNING / ENTERTAINMENT / SHOPPING / SNS / OTHER */
    private String categoryCode;
    private String categoryName;
    /** PENDING / APPROVED / REJECTED */
    private String approvalStatus;
    private Long approvedByAccountId;
    private Timestamp approvedAt;
    /** '1'=有効 / '0'=無効 */
    private String status;
    private String note;
    private Integer version;
    private Long createdByAccountId;
    private Long updatedByAccountId;
    private String createdByCode;
    private String updatedByCode;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** 表示用の分類（分類名称があればそれを、無ければコードを返す）。 */
    public String displayCategory() {
        return categoryName == null || categoryName.isBlank() ? categoryCode : categoryName;
    }

    public Long getSiteId() {
        return siteId;
    }

    public void setSiteId(Long siteId) {
        this.siteId = siteId;
    }

    public String getSiteName() {
        return siteName;
    }

    public void setSiteName(String siteName) {
        this.siteName = siteName;
    }

    public String getSiteUrl() {
        return siteUrl;
    }

    public void setSiteUrl(String siteUrl) {
        this.siteUrl = siteUrl;
    }

    public String getHostName() {
        return hostName;
    }

    public void setHostName(String hostName) {
        this.hostName = hostName;
    }

    public String getKindCode() {
        return kindCode;
    }

    public void setKindCode(String kindCode) {
        this.kindCode = kindCode;
    }

    public String getJudgeMethodCode() {
        return judgeMethodCode;
    }

    public void setJudgeMethodCode(String judgeMethodCode) {
        this.judgeMethodCode = judgeMethodCode;
    }

    public String getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(String categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public String getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(String approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Long getApprovedByAccountId() {
        return approvedByAccountId;
    }

    public void setApprovedByAccountId(Long approvedByAccountId) {
        this.approvedByAccountId = approvedByAccountId;
    }

    public Timestamp getApprovedAt() {
        return approvedAt;
    }

    public void setApprovedAt(Timestamp approvedAt) {
        this.approvedAt = approvedAt;
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
}

package com.study21.user.net;

import java.sql.Timestamp;

/**
 * NET_Web閲覧履歴情報 の 1 行（Mapper の戻り値）。
 * 画面に返す形は {@link WebBrowsingLogModels.BrowsingLogRow}。
 */
public class WebBrowsingLogEntity {

    private Long logId;
    private Timestamp accessedAt;
    private String terminalId;
    private String terminalName;
    private String eventType;
    private String domain;
    private String url;
    private String pageTitle;
    private String activeFlag;
    private Integer staySeconds;
    private Integer viewCount;
    private String ownerUserId;
    /** 一覧では使わないが、詳細（プロンプト相当の調査用）で返す。 */
    private String jsonDetail;

    public Long getLogId() {
        return logId;
    }

    public void setLogId(Long logId) {
        this.logId = logId;
    }

    public Timestamp getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(Timestamp accessedAt) {
        this.accessedAt = accessedAt;
    }

    public String getTerminalId() {
        return terminalId;
    }

    public void setTerminalId(String terminalId) {
        this.terminalId = terminalId;
    }

    public String getTerminalName() {
        return terminalName;
    }

    public void setTerminalName(String terminalName) {
        this.terminalName = terminalName;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPageTitle() {
        return pageTitle;
    }

    public void setPageTitle(String pageTitle) {
        this.pageTitle = pageTitle;
    }

    public String getActiveFlag() {
        return activeFlag;
    }

    public void setActiveFlag(String activeFlag) {
        this.activeFlag = activeFlag;
    }

    public Integer getStaySeconds() {
        return staySeconds;
    }

    public void setStaySeconds(Integer staySeconds) {
        this.staySeconds = staySeconds;
    }

    public Integer getViewCount() {
        return viewCount;
    }

    public void setViewCount(Integer viewCount) {
        this.viewCount = viewCount;
    }

    public String getOwnerUserId() {
        return ownerUserId;
    }

    public void setOwnerUserId(String ownerUserId) {
        this.ownerUserId = ownerUserId;
    }

    public String getJsonDetail() {
        return jsonDetail;
    }

    public void setJsonDetail(String jsonDetail) {
        this.jsonDetail = jsonDetail;
    }
}

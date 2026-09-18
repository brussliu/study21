package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_WordDetail の 1 行（Mapper の戻り値）。 */
public class JpnWordDetailEntity {

    private Long detailId;
    private Long wordId;
    private Integer contentVersion;
    private String detailJson;
    private String aiProvider;
    private String aiModel;
    private java.sql.Timestamp fetchedAt;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer contentVersion) { this.contentVersion = contentVersion; }
    public String getDetailJson() { return detailJson; }
    public void setDetailJson(String detailJson) { this.detailJson = detailJson; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    public java.sql.Timestamp getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(java.sql.Timestamp fetchedAt) { this.fetchedAt = fetchedAt; }
}

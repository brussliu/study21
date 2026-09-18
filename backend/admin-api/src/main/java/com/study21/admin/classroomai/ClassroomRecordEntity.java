package com.study21.admin.classroomai;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * CR_授業記録情報（授業録音の 1 セッション）の 1 行（admin-api 側）。
 *
 * <p>batC62 が最終まとめを書き、batR02 が保持期限切れの音声を掃除するときに使う最小の列だけを持つ。</p>
 */
public class ClassroomRecordEntity {

    private Long recordId;
    private String recordNo;
    private String languageMode;
    private Long createdBy;
    private String status;
    private String summaryJson;
    private String audioPath;
    private String audioName;
    private String audioMime;
    private Date retentionDeadline;
    private Integer version;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getRecordNo() { return recordNo; }
    public void setRecordNo(String recordNo) { this.recordNo = recordNo; }
    public String getLanguageMode() { return languageMode; }
    public void setLanguageMode(String languageMode) { this.languageMode = languageMode; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
    public String getAudioPath() { return audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }
    public String getAudioName() { return audioName; }
    public void setAudioName(String audioName) { this.audioName = audioName; }
    public String getAudioMime() { return audioMime; }
    public void setAudioMime(String audioMime) { this.audioMime = audioMime; }
    public Date getRetentionDeadline() { return retentionDeadline; }
    public void setRetentionDeadline(Date retentionDeadline) { this.retentionDeadline = retentionDeadline; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

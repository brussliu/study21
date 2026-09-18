package com.study21.user.classroom;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * CR_授業記録情報（授業録音の 1 セッション）の 1 行。
 *
 * <p>音声は DB に持たずファイルで持つ（録音ファイル保存先 + 保存ファイル名 は相対パス）。
 * 最終まとめ JSON は JSONB なので、SQL 側で {@code ::text} キャストして文字列として扱う。</p>
 */
public class ClassroomRecordEntity {

    private Long recordId;
    private String recordNo;
    private Long createdBy;
    private Long studentId;
    private Long familyId;
    private String title;
    /** 科目（数学・英語など）。授業名とは別に持つ。 */
    private String subject;
    private String languageMode;
    private Long presetId;
    private String presetText;
    private String status;
    private Timestamp startTime;
    private Timestamp endTime;
    private String audioPath;
    private String audioName;
    private String audioMime;
    private Long audioSize;
    private Integer durationSeconds;
    private Integer transcribedChars;
    private String summaryJson;
    private Date retentionDeadline;
    private Integer version;
    private Long updatedBy;
    private String sourceCode;
    private String updateSourceCode;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getRecordNo() { return recordNo; }
    public void setRecordNo(String recordNo) { this.recordNo = recordNo; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getStudentId() { return studentId; }
    public void setStudentId(Long studentId) { this.studentId = studentId; }
    public Long getFamilyId() { return familyId; }
    public void setFamilyId(Long familyId) { this.familyId = familyId; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getLanguageMode() { return languageMode; }
    public void setLanguageMode(String languageMode) { this.languageMode = languageMode; }
    public Long getPresetId() { return presetId; }
    public void setPresetId(Long presetId) { this.presetId = presetId; }
    public String getPresetText() { return presetText; }
    public void setPresetText(String presetText) { this.presetText = presetText; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Timestamp getStartTime() { return startTime; }
    public void setStartTime(Timestamp startTime) { this.startTime = startTime; }
    public Timestamp getEndTime() { return endTime; }
    public void setEndTime(Timestamp endTime) { this.endTime = endTime; }
    public String getAudioPath() { return audioPath; }
    public void setAudioPath(String audioPath) { this.audioPath = audioPath; }
    public String getAudioName() { return audioName; }
    public void setAudioName(String audioName) { this.audioName = audioName; }
    public String getAudioMime() { return audioMime; }
    public void setAudioMime(String audioMime) { this.audioMime = audioMime; }
    public Long getAudioSize() { return audioSize; }
    public void setAudioSize(Long audioSize) { this.audioSize = audioSize; }
    public Integer getDurationSeconds() { return durationSeconds; }
    public void setDurationSeconds(Integer durationSeconds) { this.durationSeconds = durationSeconds; }
    public Integer getTranscribedChars() { return transcribedChars; }
    public void setTranscribedChars(Integer transcribedChars) { this.transcribedChars = transcribedChars; }
    public String getSummaryJson() { return summaryJson; }
    public void setSummaryJson(String summaryJson) { this.summaryJson = summaryJson; }
    public Date getRetentionDeadline() { return retentionDeadline; }
    public void setRetentionDeadline(Date retentionDeadline) { this.retentionDeadline = retentionDeadline; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getUpdateSourceCode() { return updateSourceCode; }
    public void setUpdateSourceCode(String updateSourceCode) { this.updateSourceCode = updateSourceCode; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

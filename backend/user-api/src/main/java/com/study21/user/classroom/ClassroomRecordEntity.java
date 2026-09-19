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

    // ---- 収尾で確定した「録れた分塊」の範囲（終了後の遅い分塊を断る根拠） ----
    /** 収尾で確定した「実際に録れた」最後の分塊の連番（未確定は null）。 */
    private Integer recordedLastSeq;
    /** 収尾で確定した「録れた分塊の数」（画面の一覧の件数）。 */
    private Integer recordedCount;
    /** 収尾で確定した録音の終わりの位置（統一時間軸。16kHz のサンプル数）。 */
    private Long recordedEndSample;
    /** 収尾のときに音声が全部そろっていると確認できたか。 */
    private Boolean recordedComplete;
    /** 不完全なまま終えた回に失った連番（カンマ区切り）。 */
    private String lostSeqs;

    // ---- 再生用 1 本（分塊の結合）の状態（再起動後も読めるように DB に残す） ----
    /** 結合の状態（NOT_STARTED / QUEUED / PROCESSING / READY / FAILED / INCOMPLETE）。 */
    private String assemblyState;
    /** 結合のもとにした分塊の内容の要約（SHA-256）。 */
    private String assemblyDigest;
    /** できた 1 本の長さ（秒）。 */
    private java.math.BigDecimal assemblyDuration;
    /** 人が読む理由（日本語）。 */
    private String assemblyReason;
    /** 結合を始めた時刻（PROCESSING のまま残った回を再起動後に見分ける）。 */
    private Timestamp assemblyStartedAt;
    /** 結合が終わった時刻。 */
    private Timestamp assemblyFinishedAt;

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

    public Integer getRecordedLastSeq() { return recordedLastSeq; }
    public void setRecordedLastSeq(Integer recordedLastSeq) { this.recordedLastSeq = recordedLastSeq; }
    public Integer getRecordedCount() { return recordedCount; }
    public void setRecordedCount(Integer recordedCount) { this.recordedCount = recordedCount; }
    public Long getRecordedEndSample() { return recordedEndSample; }
    public void setRecordedEndSample(Long recordedEndSample) { this.recordedEndSample = recordedEndSample; }
    public Boolean getRecordedComplete() { return recordedComplete; }
    public void setRecordedComplete(Boolean recordedComplete) { this.recordedComplete = recordedComplete; }
    public String getLostSeqs() { return lostSeqs; }
    public void setLostSeqs(String lostSeqs) { this.lostSeqs = lostSeqs; }
    public String getAssemblyState() { return assemblyState; }
    public void setAssemblyState(String assemblyState) { this.assemblyState = assemblyState; }
    public String getAssemblyDigest() { return assemblyDigest; }
    public void setAssemblyDigest(String assemblyDigest) { this.assemblyDigest = assemblyDigest; }
    public java.math.BigDecimal getAssemblyDuration() { return assemblyDuration; }
    public void setAssemblyDuration(java.math.BigDecimal assemblyDuration) {
        this.assemblyDuration = assemblyDuration;
    }
    public String getAssemblyReason() { return assemblyReason; }
    public void setAssemblyReason(String assemblyReason) { this.assemblyReason = assemblyReason; }
    public Timestamp getAssemblyStartedAt() { return assemblyStartedAt; }
    public void setAssemblyStartedAt(Timestamp assemblyStartedAt) { this.assemblyStartedAt = assemblyStartedAt; }
    public Timestamp getAssemblyFinishedAt() { return assemblyFinishedAt; }
    public void setAssemblyFinishedAt(Timestamp assemblyFinishedAt) { this.assemblyFinishedAt = assemblyFinishedAt; }
}

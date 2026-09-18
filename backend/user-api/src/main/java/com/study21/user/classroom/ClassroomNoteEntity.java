package com.study21.user.classroom;

import java.sql.Timestamp;

/**
 * CR_授業ノート情報（フェーズノート + 最終まとめ）の 1 行。
 *
 * <p>生成状態=PENDING の行が admin-api バッチ（batC61/batC62）のキューになる。
 * ノート本文は JSONB なので、SQL 側で {@code ::text} キャストして文字列として扱う。</p>
 */
public class ClassroomNoteEntity {

    private Long noteId;
    private Long recordId;
    private String kind;
    private Integer phaseNo;
    private Integer startSeq;
    private Integer endSeq;
    private String noteJson;
    private String status;
    private Long aiCallId;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private Integer version;
    private Long createdBy;
    private Long updatedBy;
    private String sourceCode;
    private String updateSourceCode;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getNoteId() { return noteId; }
    public void setNoteId(Long noteId) { this.noteId = noteId; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public Integer getPhaseNo() { return phaseNo; }
    public void setPhaseNo(Integer phaseNo) { this.phaseNo = phaseNo; }
    public Integer getStartSeq() { return startSeq; }
    public void setStartSeq(Integer startSeq) { this.startSeq = startSeq; }
    public Integer getEndSeq() { return endSeq; }
    public void setEndSeq(Integer endSeq) { this.endSeq = endSeq; }
    public String getNoteJson() { return noteJson; }
    public void setNoteJson(String noteJson) { this.noteJson = noteJson; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Long getAiCallId() { return aiCallId; }
    public void setAiCallId(Long aiCallId) { this.aiCallId = aiCallId; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
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

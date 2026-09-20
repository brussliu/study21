package com.study21.admin.classroomai;

import java.sql.Timestamp;

/**
 * CR_授業ノート情報（フェーズノート + 最終まとめ）の 1 行（admin-api 側）。
 *
 * <p>batC61（フェーズ分析）と batC62（最終まとめ）が**生成状態=PENDING の行をキューとして**
 * 拾う。画面向けの入口（作成）は user-api にあり、両サービスは互いを呼べないので
 * この表の状態列だけで橋渡しする（docs/ARCHITECTURE.md §3）。</p>
 */
public class ClassroomNoteEntity {

    private Long noteId;
    private Long recordId;
    /** PHASE / FINAL */
    private String kind;
    private Integer phaseNo;
    private Integer startSeq;
    private Integer endSeq;
    private String noteJson;
    /** PENDING / GENERATING / READY / FAILED */
    private String status;
    private Long aiCallId;
    private String errorCode;
    private String errorMessage;
    private Integer retryCount;
    private Integer version;
    private Long createdBy;
    private Timestamp createdAt;
    /** 生成の起動を受理した時刻（落ちたままの `GENERATING` を見分ける）。 */
    private Timestamp generationStartedAt;
    /**
     * **この試行の識別子**（起動を受理するたびに新しくなる）。
     *
     * <p>完了・失敗の更新はこの値が一致するときだけ通る＝**遅れて返ってきた古い試行**が
     * 新しい試行や既にできた結果を上書きしない。</p>
     */
    private String generationToken;
    /**
     * **この試行を実行しているバッチ実行記録**の ID（`BAT_バッチ実行履歴情報`.`実行ID`）。
     *
     * <p>`AI呼出履歴ID` とは**別物**（あちらは AI 呼び出しログの ID）。混ぜると、失联判定や
     * 呼び出しの追跡が壊れる。</p>
     */
    private Long generationExecutionId;
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
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getGenerationStartedAt() { return generationStartedAt; }
    public void setGenerationStartedAt(Timestamp generationStartedAt) {
        this.generationStartedAt = generationStartedAt;
    }
    public String getGenerationToken() { return generationToken; }
    public void setGenerationToken(String generationToken) { this.generationToken = generationToken; }
    public Long getGenerationExecutionId() { return generationExecutionId; }
    public void setGenerationExecutionId(Long generationExecutionId) {
        this.generationExecutionId = generationExecutionId;
    }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

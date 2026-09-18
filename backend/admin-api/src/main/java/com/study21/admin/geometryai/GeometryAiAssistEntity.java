package com.study21.admin.geometryai;

import java.sql.Timestamp;

/**
 * GEO_AI画図指示情報（AI 画図助手の 1 指示 = 1 行）の 1 行（admin-api 側）。
 *
 * <p>batC52（AI 画図助手 生成）が**生成状態=PENDING の行をキューとして**拾い、AI を呼んで
 * READY / FAILED に遷移させる。画面向けの入口（依頼作成）は user-api にあり、両サービスは
 * この表の状態列だけで橋渡しする（docs/ARCHITECTURE.md §3）。</p>
 */
public class GeometryAiAssistEntity {

    private Long assistId;
    private Long figureId;
    private String instruction;
    private String beforeXml;
    /** 指示時点のオブジェクト一覧（「名前 = 定義（型）」）。AI の {objects} に使う。 */
    private String objectsSummary;
    /** 前の案が実行できなかった内容（実行できなかった行と理由）。AI の {failure} に使う。 */
    private String failureDetail;
    private String commands;
    private Integer commandCount;
    private String applyKind;
    private String mode;
    private String description;
    private Long aiCallId;
    /** PENDING / GENERATING / READY / FAILED */
    private String status;
    private Integer retryCount;
    private String errorCode;
    private String errorMessage;
    private Long createdBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getAssistId() { return assistId; }
    public void setAssistId(Long assistId) { this.assistId = assistId; }
    public Long getFigureId() { return figureId; }
    public void setFigureId(Long figureId) { this.figureId = figureId; }
    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }
    public String getBeforeXml() { return beforeXml; }
    public void setBeforeXml(String beforeXml) { this.beforeXml = beforeXml; }
    public String getObjectsSummary() { return objectsSummary; }
    public void setObjectsSummary(String objectsSummary) { this.objectsSummary = objectsSummary; }
    public String getFailureDetail() { return failureDetail; }
    public void setFailureDetail(String failureDetail) { this.failureDetail = failureDetail; }
    public String getCommands() { return commands; }
    public void setCommands(String commands) { this.commands = commands; }
    public Integer getCommandCount() { return commandCount; }
    public void setCommandCount(Integer commandCount) { this.commandCount = commandCount; }
    public String getApplyKind() { return applyKind; }
    public void setApplyKind(String applyKind) { this.applyKind = applyKind; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getAiCallId() { return aiCallId; }
    public void setAiCallId(Long aiCallId) { this.aiCallId = aiCallId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

package com.study21.user.geometry;

import java.sql.Timestamp;

/**
 * GEO_AI画図指示情報 の 1 行（AI 画図助手の 1 指示 = 1 行）。
 *
 * <p>「誰が何を指示して AI が何をしたか」を残す（学校で生徒が使う・問い合わせ対応）。
 * 画図助手はバッチではない（対話的・同期）ので、`BAT_AI呼出履歴情報` には
 * `バッチコード = 'geometry-ai-assist'` で 1 行残す。</p>
 */
public class GeometryAiAssistEntity {

    private Long assistId;
    /** 編集対象の図形（新規作図中は NULL） */
    private Long figureId;
    private String instruction;
    /** 適用前の作図データ（取消・検証用） */
    private String beforeXml;
    /** 指示時点のオブジェクト一覧（「名前 = 定義（型）」）。AI の {objects} に使う。 */
    private String objectsSummary;
    /** 前の案が実行できなかった内容（実行できなかった行と理由）。AI の {failure} に使う。 */
    private String failureDetail;
    /**
     * 「指示前XML」を持っているか（**一覧クエリだけ**が返す。中身は運ばない）。
     *
     * <p>一覧では XML そのものを返さず、有無だけを返して【戻す】の可否を伝える
     * （必要になったら `findById` で 1 件だけ取る）。</p>
     */
    private Boolean beforeXmlAvailable;
    private String commands;
    private Integer commandCount;
    /** APPLIED / REJECTED / FAILED */
    private String applyKind;
    /**
     * 記録用の反映方法（APPEND / REPLACE）。
     *
     * <p>いまは新しい依頼は **APPEND だけ**（画面に選択が無く、作り直したいときは利用者が【全消去】する）。
     * REPLACE は過去の履歴の表示・互換のために残る。</p>
     */
    private String mode;
    private String description;
    private Long aiCallId;
    /** PENDING=依頼済み / GENERATING=batC52 実行中 / READY=生成済み / FAILED=失敗 */
    private String status;
    private Integer retryCount;
    private String errorCode;
    private String errorMessage;
    private Long createdBy;
    private Long updatedBy;
    private String sourceCode;
    private String updateSourceCode;
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
    public Boolean getBeforeXmlAvailable() { return beforeXmlAvailable; }
    public void setBeforeXmlAvailable(Boolean beforeXmlAvailable) { this.beforeXmlAvailable = beforeXmlAvailable; }
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

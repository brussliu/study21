package com.study21.admin.batch;

/**
 * バッチ実行履歴のエンティティ（BAT_バッチ実行履歴情報）。
 * 有効／無効は BAT_バッチコントロール情報（BatchControlEntity）が持つ。
 */
public class BatchExecutionEntity {

    private Long executionId;
    /** バッチコード（BatchTaskRegistry のコード。旧: タスクコード） */
    private String batchCode;
    /** C=呼出 / L=循環 / R=定時 */
    private String batchType;
    /** 起動のされ方（C/L/R） */
    private String triggerType;
    private String status;
    /** 要求内容（JSON 文字列。任意） */
    private String requestPayload;
    /** 画面から起動した実行者。定時・循環などでは null */
    private Long requestedByAccountId;
    /** 人が起動していない場合の識別子（scheduler など） */
    private String requestedByCode;
    /** 開始から終了までのミリ秒 */
    private Long durationMs;
    /** 異常終了時のスタックトレースなど */
    private String errorDetail;
    private String scheduleTime;
    private String startTime;
    private String endTime;
    private String message;
    private java.sql.Timestamp createdAt;
    private java.sql.Timestamp updatedAt;

    public Long getExecutionId() {
        return executionId;
    }

    public void setExecutionId(Long executionId) {
        this.executionId = executionId;
    }

    public String getBatchCode() {
        return batchCode;
    }

    public void setBatchCode(String batchCode) {
        this.batchCode = batchCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getScheduleTime() {
        return scheduleTime;
    }

    public void setScheduleTime(String scheduleTime) {
        this.scheduleTime = scheduleTime;
    }

    public String getStartTime() {
        return startTime;
    }

    public void setStartTime(String startTime) {
        this.startTime = startTime;
    }

    public String getEndTime() {
        return endTime;
    }

    public void setEndTime(String endTime) {
        this.endTime = endTime;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getRequestedByCode() {
        return requestedByCode;
    }

    public void setRequestedByCode(String requestedByCode) {
        this.requestedByCode = requestedByCode;
    }

    public java.sql.Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(java.sql.Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public java.sql.Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(java.sql.Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getBatchType() {
        return batchType;
    }

    public void setBatchType(String batchType) {
        this.batchType = batchType;
    }

    public String getTriggerType() {
        return triggerType;
    }

    public void setTriggerType(String triggerType) {
        this.triggerType = triggerType;
    }

    public String getRequestPayload() {
        return requestPayload;
    }

    public void setRequestPayload(String requestPayload) {
        this.requestPayload = requestPayload;
    }

    public Long getRequestedByAccountId() {
        return requestedByAccountId;
    }

    public void setRequestedByAccountId(Long requestedByAccountId) {
        this.requestedByAccountId = requestedByAccountId;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public void setDurationMs(Long durationMs) {
        this.durationMs = durationMs;
    }

    public String getErrorDetail() {
        return errorDetail;
    }

    public void setErrorDetail(String errorDetail) {
        this.errorDetail = errorDetail;
    }
}

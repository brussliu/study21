package com.study21.admin.batch;

import java.sql.Timestamp;

/**
 * BAT_バッチコントロール情報（バッチの有効／無効）のエンティティ。
 *
 * 2.0 は COM_設定情報 の「BATCH_TASK_ENABLED_&lt;バッチコード&gt;」で保持していた。
 * 値の意味は database/バッチ/TBL_BAT_バッチコントロール情報.sql の COMMENT を参照。
 */
public class BatchControlEntity {

    private String batchCode;
    /** '1'=有効（定時・循環で実行する） / '0'=無効（定時実行しない。手動実行は可） */
    private String status;
    /** 最後に実行が終わった日時（一覧表示用） */
    private Timestamp lastRunAt;
    private String note;
    private Integer version;
    private Long createdByAccountId;
    private Long updatedByAccountId;
    private String createdByCode;
    private String updatedByCode;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** 有効かどうか。 */
    public boolean isActive() {
        return !"0".equals(status);
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

    public Timestamp getLastRunAt() {
        return lastRunAt;
    }

    public void setLastRunAt(Timestamp lastRunAt) {
        this.lastRunAt = lastRunAt;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public Long getCreatedByAccountId() {
        return createdByAccountId;
    }

    public void setCreatedByAccountId(Long createdByAccountId) {
        this.createdByAccountId = createdByAccountId;
    }

    public Long getUpdatedByAccountId() {
        return updatedByAccountId;
    }

    public void setUpdatedByAccountId(Long updatedByAccountId) {
        this.updatedByAccountId = updatedByAccountId;
    }

    public String getCreatedByCode() {
        return createdByCode;
    }

    public void setCreatedByCode(String createdByCode) {
        this.createdByCode = createdByCode;
    }

    public String getUpdatedByCode() {
        return updatedByCode;
    }

    public void setUpdatedByCode(String updatedByCode) {
        this.updatedByCode = updatedByCode;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}

package com.study21.user.classroom;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * CR_授業録音分塊情報（録音の分塊 1 塊 = ファイル 1 本）の 1 行。
 *
 * <p>`分塊連番` は画面が送る `seq` そのもの。(授業記録ID, 分塊連番) が一意なので、
 * **同じ分塊の再送は同じ行・同じファイルに落ち着く**（到着順・再起動に依らない）。
 * 中身の照合は `バイト数` と `チェックサム`（SHA-256）で行い、同じ連番で違う中身が来たら
 * 上書きせずエラーにする（別の分塊を黙って捨てない）。</p>
 *
 * <p>再生用の 1 本はこの行ではなく**ファイルの連番順**で組立てる（ファイルの追記は成功したが
 * DB のトランザクションが失敗した分塊も、音声としては残っているため）。</p>
 */
public class ClassroomRecordingChunkEntity {

    private Long chunkId;
    private Long recordId;
    private Integer seq;
    private BigDecimal startOffsetSeconds;
    private BigDecimal endOffsetSeconds;
    private Long byteSize;
    /** SHA-256 の 16 進 64 文字（同じ連番で違う中身を見分ける）。 */
    private String checksum;
    private String storageDir;
    private String fileName;
    private String mime;
    /** 新しいコンテナ（EBML/OGG/MP4）のヘッダで始まる分塊か。 */
    private Boolean containerHead;
    /** STORED（音声だけ保存）/ TRANSCRIBED（書き起こし済み）/ SKIPPED（書き起こしを省略）。 */
    private String processingStatus;
    private Integer segmentCount;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** 同じ連番・同じ中身か（冪等判定）。 */
    public boolean sameContent(long size, String hash) {
        return byteSize != null && byteSize == size
                && checksum != null && checksum.equals(hash);
    }

    public Long getChunkId() { return chunkId; }
    public void setChunkId(Long chunkId) { this.chunkId = chunkId; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public BigDecimal getStartOffsetSeconds() { return startOffsetSeconds; }
    public void setStartOffsetSeconds(BigDecimal startOffsetSeconds) { this.startOffsetSeconds = startOffsetSeconds; }
    public BigDecimal getEndOffsetSeconds() { return endOffsetSeconds; }
    public void setEndOffsetSeconds(BigDecimal endOffsetSeconds) { this.endOffsetSeconds = endOffsetSeconds; }
    public Long getByteSize() { return byteSize; }
    public void setByteSize(Long byteSize) { this.byteSize = byteSize; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public String getStorageDir() { return storageDir; }
    public void setStorageDir(String storageDir) { this.storageDir = storageDir; }
    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }
    public String getMime() { return mime; }
    public void setMime(String mime) { this.mime = mime; }
    public Boolean getContainerHead() { return containerHead; }
    public void setContainerHead(Boolean containerHead) { this.containerHead = containerHead; }
    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }
    public Integer getSegmentCount() { return segmentCount; }
    public void setSegmentCount(Integer segmentCount) { this.segmentCount = segmentCount; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

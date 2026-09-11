package com.study21.user.testinfo;

import java.sql.Timestamp;

/** TST_テストファイル情報 の 1 行。 */
public class TestFileEntity {
    private Long fileId;
    private Long testId;
    private Integer displayOrder;
    private String originalFileName;
    private String storedFileName;
    private String extension;
    private String mimeType;
    private Long fileSize;
    private String sha256;
    private String path;
    private String thumbnail500;
    private String thumbnail200;
    private String thumbnail50;
    private String comment;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getTestId() { return testId; }
    public void setTestId(Long testId) { this.testId = testId; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public String getSha256() { return sha256; }
    public void setSha256(String sha256) { this.sha256 = sha256; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getThumbnail500() { return thumbnail500; }
    public void setThumbnail500(String thumbnail500) { this.thumbnail500 = thumbnail500; }
    public String getThumbnail200() { return thumbnail200; }
    public void setThumbnail200(String thumbnail200) { this.thumbnail200 = thumbnail200; }
    public String getThumbnail50() { return thumbnail50; }
    public void setThumbnail50(String thumbnail50) { this.thumbnail50 = thumbnail50; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

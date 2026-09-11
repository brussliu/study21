package com.study21.user.tempfile;

import java.sql.Timestamp;

/** public."COM_臨時ファイル情報" の 1 行を表すエンティティ。 */
public class TempFileEntity {
    private Long tempFileId;
    private Long familyStudentId;
    private String originalFileName;
    private String storedFileName;
    private String extension;
    private String mimeType;
    private Long fileSize;
    private String path;
    private String thumbnail500;
    private String thumbnail200;
    private String thumbnail50;
    private String comment;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getTempFileId() { return tempFileId; }
    public void setTempFileId(Long tempFileId) { this.tempFileId = tempFileId; }
    public Long getFamilyStudentId() { return familyStudentId; }
    public void setFamilyStudentId(Long familyStudentId) { this.familyStudentId = familyStudentId; }
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

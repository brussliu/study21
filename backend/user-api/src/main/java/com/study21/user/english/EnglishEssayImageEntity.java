package com.study21.user.english;

import java.sql.Timestamp;

/**
 * `ENG_英作文画像情報` の 1 行。
 *
 * <p>`orderNo` は列 `表示順`（1 から。同じ作文の中で UNIQUE）。Java の `order` という名前を避けて
 * SQL と区別しやすくしてある（API では `order` として返す）。</p>
 */
public class EnglishEssayImageEntity {

    private Long imageId;
    private Long essayId;
    private Integer orderNo;
    private String category;
    private String originalFileName;
    private String storedFileName;
    private String relativePath;
    private String mimeType;
    private Long fileSize;
    private String recognizedText;
    private Integer confidence;
    private Long createdBy;
    private Timestamp createdAt;

    public Long getImageId() { return imageId; }
    public void setImageId(Long imageId) { this.imageId = imageId; }

    public Long getEssayId() { return essayId; }
    public void setEssayId(Long essayId) { this.essayId = essayId; }

    public Integer getOrderNo() { return orderNo; }
    public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }

    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }

    public String getRelativePath() { return relativePath; }
    public void setRelativePath(String relativePath) { this.relativePath = relativePath; }

    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getRecognizedText() { return recognizedText; }
    public void setRecognizedText(String recognizedText) { this.recognizedText = recognizedText; }

    public Integer getConfidence() { return confidence; }
    public void setConfidence(Integer confidence) { this.confidence = confidence; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

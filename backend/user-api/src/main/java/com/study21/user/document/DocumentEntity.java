package com.study21.user.document;

import java.sql.Timestamp;

public class DocumentEntity {
    private String documentNo;
    private Long familyStudentId;
    private Long folderId;
    private String status;
    private String expiryDate;
    private String largeCategory;
    private String mediumCategory;
    private String smallCategory;
    private String detailCategory;
    private String comment;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public String getDocumentNo() { return documentNo; }
    public void setDocumentNo(String documentNo) { this.documentNo = documentNo; }
    public Long getFamilyStudentId() { return familyStudentId; }
    public void setFamilyStudentId(Long familyStudentId) { this.familyStudentId = familyStudentId; }
    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getExpiryDate() { return expiryDate; }
    public void setExpiryDate(String expiryDate) { this.expiryDate = expiryDate; }
    public String getLargeCategory() { return largeCategory; }
    public void setLargeCategory(String largeCategory) { this.largeCategory = largeCategory; }
    public String getMediumCategory() { return mediumCategory; }
    public void setMediumCategory(String mediumCategory) { this.mediumCategory = mediumCategory; }
    public String getSmallCategory() { return smallCategory; }
    public void setSmallCategory(String smallCategory) { this.smallCategory = smallCategory; }
    public String getDetailCategory() { return detailCategory; }
    public void setDetailCategory(String detailCategory) { this.detailCategory = detailCategory; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

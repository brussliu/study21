package com.study21.user.document;

public class DocumentFolderEntity {
    private Long folderId;
    private Long familyStudentId;
    private Long parentFolderId;
    private String folderName;
    private Integer displayOrder;
    private String note;

    public Long getFolderId() { return folderId; }
    public void setFolderId(Long folderId) { this.folderId = folderId; }
    public Long getFamilyStudentId() { return familyStudentId; }
    public void setFamilyStudentId(Long familyStudentId) { this.familyStudentId = familyStudentId; }
    public Long getParentFolderId() { return parentFolderId; }
    public void setParentFolderId(Long parentFolderId) { this.parentFolderId = parentFolderId; }
    public String getFolderName() { return folderName; }
    public void setFolderName(String folderName) { this.folderName = folderName; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}

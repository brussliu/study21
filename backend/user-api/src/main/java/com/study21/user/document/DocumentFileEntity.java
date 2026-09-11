package com.study21.user.document;

public class DocumentFileEntity {
    private String documentNo;
    private Integer branchNo;
    private String extension;
    private String originalFileName;
    private String thumbnail500;
    private String thumbnail200;
    private String thumbnail50;
    private String comment;
    private String path;
    private String storedFileName;

    public String getDocumentNo() { return documentNo; }
    public void setDocumentNo(String documentNo) { this.documentNo = documentNo; }
    public Integer getBranchNo() { return branchNo; }
    public void setBranchNo(Integer branchNo) { this.branchNo = branchNo; }
    public String getExtension() { return extension; }
    public void setExtension(String extension) { this.extension = extension; }
    public String getOriginalFileName() { return originalFileName; }
    public void setOriginalFileName(String originalFileName) { this.originalFileName = originalFileName; }
    public String getThumbnail500() { return thumbnail500; }
    public void setThumbnail500(String thumbnail500) { this.thumbnail500 = thumbnail500; }
    public String getThumbnail200() { return thumbnail200; }
    public void setThumbnail200(String thumbnail200) { this.thumbnail200 = thumbnail200; }
    public String getThumbnail50() { return thumbnail50; }
    public void setThumbnail50(String thumbnail50) { this.thumbnail50 = thumbnail50; }
    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getStoredFileName() { return storedFileName; }
    public void setStoredFileName(String storedFileName) { this.storedFileName = storedFileName; }
}

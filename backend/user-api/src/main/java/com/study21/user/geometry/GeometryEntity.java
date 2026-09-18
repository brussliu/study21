package com.study21.user.geometry;

import java.sql.Timestamp;

/** GEO_図形情報 の 1 行（Mapper の戻り値）。 */
public class GeometryEntity {

    private Long figureId;
    private String figureNo;
    private String subject;
    private String figureType;
    private String kind;
    private String title;
    private String memo;
    private String tags;
    private String construction;
    private String thumbnail;
    private Integer displayOrder;
    private String statusCode;
    private Integer version;
    /** 一覧用: サムネイルがあるか（中身は返さない） */
    private Boolean hasThumbnail;
    /** 一覧用: GeoGebraXML の文字数 */
    private Integer constructionLength;
    /** 登録元コード（APP / AI / MIGRATION）。AI 生図から作った図形は 'AI' */
    private String sourceCode;
    private Long createdBy;
    private Long updatedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getFigureId() { return figureId; }
    public void setFigureId(Long figureId) { this.figureId = figureId; }
    public String getFigureNo() { return figureNo; }
    public void setFigureNo(String figureNo) { this.figureNo = figureNo; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getFigureType() { return figureType; }
    public void setFigureType(String figureType) { this.figureType = figureType; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getConstruction() { return construction; }
    public void setConstruction(String construction) { this.construction = construction; }
    public String getThumbnail() { return thumbnail; }
    public void setThumbnail(String thumbnail) { this.thumbnail = thumbnail; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Boolean getHasThumbnail() { return hasThumbnail; }
    public void setHasThumbnail(Boolean hasThumbnail) { this.hasThumbnail = hasThumbnail; }
    public Integer getConstructionLength() { return constructionLength; }
    public void setConstructionLength(Integer constructionLength) { this.constructionLength = constructionLength; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

package com.study21.user.reading;

import java.math.BigDecimal;
import java.sql.Timestamp;

/** RED_標記情報 の 1 行。 */
public class ReadingMarkEntity {

    private Long markId;
    private Long bookId;
    private Integer pageNo;
    private String markType;
    private String targetText;
    private String content;
    private String color;
    /**
     * PDF に重ねるための座標（ページ内の正規化値 0〜1）。DDL は NUMERIC(10,6) なので
     * Java 側は BigDecimal で扱い、加工せずそのまま返す。
     */
    private BigDecimal positionX;
    private BigDecimal positionY;
    private BigDecimal width;
    private BigDecimal height;
    /** 手書き（pen）の点列 JSON。文字列のまま扱う */
    private String drawingData;
    private Integer orderNo;
    /** 登録した人（監査用） */
    private Long createdBy;
    private Timestamp createdAt;

    public Long getMarkId() { return markId; }
    public void setMarkId(Long markId) { this.markId = markId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public Integer getPageNo() { return pageNo; }
    public void setPageNo(Integer pageNo) { this.pageNo = pageNo; }
    public String getMarkType() { return markType; }
    public void setMarkType(String markType) { this.markType = markType; }
    public String getTargetText() { return targetText; }
    public void setTargetText(String targetText) { this.targetText = targetText; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }
    public Integer getOrderNo() { return orderNo; }
    public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
    public BigDecimal getPositionX() { return positionX; }
    public void setPositionX(BigDecimal positionX) { this.positionX = positionX; }
    public BigDecimal getPositionY() { return positionY; }
    public void setPositionY(BigDecimal positionY) { this.positionY = positionY; }
    public BigDecimal getWidth() { return width; }
    public void setWidth(BigDecimal width) { this.width = width; }
    public BigDecimal getHeight() { return height; }
    public void setHeight(BigDecimal height) { this.height = height; }
    public String getDrawingData() { return drawingData; }
    public void setDrawingData(String drawingData) { this.drawingData = drawingData; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

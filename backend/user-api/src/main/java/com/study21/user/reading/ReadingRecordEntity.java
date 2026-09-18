package com.study21.user.reading;

import java.sql.Timestamp;

/** RED_読書記録情報 の 1 行。 */
public class ReadingRecordEntity {

    private Long recordId;
    private Long bookId;
    private String bookTitle;
    private Timestamp readAt;
    private Integer pageStart;
    private Integer pageEnd;
    private Integer minutes;
    private Integer markCount;
    private String memo;
    /** 登録した人（監査用） */
    private Long createdBy;

    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getBookTitle() { return bookTitle; }
    public void setBookTitle(String bookTitle) { this.bookTitle = bookTitle; }
    public Timestamp getReadAt() { return readAt; }
    public void setReadAt(Timestamp readAt) { this.readAt = readAt; }
    public Integer getPageStart() { return pageStart; }
    public void setPageStart(Integer pageStart) { this.pageStart = pageStart; }
    public Integer getPageEnd() { return pageEnd; }
    public void setPageEnd(Integer pageEnd) { this.pageEnd = pageEnd; }
    public Integer getMinutes() { return minutes; }
    public void setMinutes(Integer minutes) { this.minutes = minutes; }
    public Integer getMarkCount() { return markCount; }
    public void setMarkCount(Integer markCount) { this.markCount = markCount; }
    public String getMemo() { return memo; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public void setMemo(String memo) { this.memo = memo; }
}

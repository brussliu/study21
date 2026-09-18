package com.study21.user.reading;

import java.sql.Timestamp;

/**
 * RED_書籍情報 の 1 行（Mapper の戻り値）。
 * 画面に返す形は {@link ReadingModels.BookRow}。
 */
public class ReadingBookEntity {

    private Long bookId;
    private String bookNo;
    private String subject;
    /** 本の言語（中国語 / 英語 / 日本語）。教科 は 2.0 から引き継いだ名残 */
    private String language;
    private String title;
    private String author;
    private String difficulty;
    private String status;
    private Integer totalPages;
    private Integer currentPage;
    private Boolean pinned;
    private String tags;
    private String summary;
    private String note;
    private Integer recentMinutes;
    private Integer totalMinutes;
    private Integer markCount;
    private Timestamp lastReadAt;
    private Integer version;
    /** 登録・更新した人（監査用。画面には出さない） */
    private Long createdBy;
    private Long updatedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /* --- 公開範囲（2026-09-14 の追補。GLOBAL=全体書籍 / FAMILY=家庭の書籍） --- */
    private String scope;
    /** FAMILY のときの持ち主の家庭（生徒のアカウントID）。GLOBAL は null */
    private Long ownerFamilyId;
    /** その家庭の生徒の表示名（画面の「家庭の本」バッジに出す） */
    private String ownerFamilyLabel;
    /** 見ている本人が自分の本棚に入れているか（EXISTS の結果） */
    private Boolean inMyShelf;

    /* --- 本棚の分類（RED_書籍分類情報 との結合。未分類は null） --- */
    private Long categoryId;
    private String categoryName;

    /* --- 本文 PDF（RED_書籍ファイル情報・ファイル区分='PDF'。行が無ければ null） --- */
    private Long pdfFileId;
    private String pdfOriginalName;
    private String pdfStoredName;
    private String pdfStoredPath;
    private String pdfMimeType;
    private Integer pdfPageCount;

    /* --- 表紙画像（ファイル区分='COVER'。行が無ければ null） --- */
    private Long coverFileId;
    private String coverStoredName;
    private String coverStoredPath;
    private String coverMimeType;

    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getBookNo() { return bookNo; }
    public void setBookNo(String bookNo) { this.bookNo = bookNo; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getAuthor() { return author; }
    public void setAuthor(String author) { this.author = author; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalPages() { return totalPages; }
    public void setTotalPages(Integer totalPages) { this.totalPages = totalPages; }
    public Integer getCurrentPage() { return currentPage; }
    public void setCurrentPage(Integer currentPage) { this.currentPage = currentPage; }
    public Boolean getPinned() { return pinned; }
    public void setPinned(Boolean pinned) { this.pinned = pinned; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getRecentMinutes() { return recentMinutes; }
    public void setRecentMinutes(Integer recentMinutes) { this.recentMinutes = recentMinutes; }
    public Integer getTotalMinutes() { return totalMinutes; }
    public void setTotalMinutes(Integer totalMinutes) { this.totalMinutes = totalMinutes; }
    public Integer getMarkCount() { return markCount; }
    public void setMarkCount(Integer markCount) { this.markCount = markCount; }
    public Timestamp getLastReadAt() { return lastReadAt; }
    public void setLastReadAt(Timestamp lastReadAt) { this.lastReadAt = lastReadAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getOwnerFamilyId() { return ownerFamilyId; }
    public void setOwnerFamilyId(Long ownerFamilyId) { this.ownerFamilyId = ownerFamilyId; }
    public String getOwnerFamilyLabel() { return ownerFamilyLabel; }
    public void setOwnerFamilyLabel(String ownerFamilyLabel) { this.ownerFamilyLabel = ownerFamilyLabel; }
    public Boolean getInMyShelf() { return inMyShelf; }
    public void setInMyShelf(Boolean inMyShelf) { this.inMyShelf = inMyShelf; }

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public Long getPdfFileId() { return pdfFileId; }
    public void setPdfFileId(Long pdfFileId) { this.pdfFileId = pdfFileId; }
    public String getPdfOriginalName() { return pdfOriginalName; }
    public void setPdfOriginalName(String pdfOriginalName) { this.pdfOriginalName = pdfOriginalName; }
    public String getPdfStoredName() { return pdfStoredName; }
    public void setPdfStoredName(String pdfStoredName) { this.pdfStoredName = pdfStoredName; }
    public String getPdfStoredPath() { return pdfStoredPath; }
    public void setPdfStoredPath(String pdfStoredPath) { this.pdfStoredPath = pdfStoredPath; }
    public String getPdfMimeType() { return pdfMimeType; }
    public void setPdfMimeType(String pdfMimeType) { this.pdfMimeType = pdfMimeType; }
    public Integer getPdfPageCount() { return pdfPageCount; }
    public void setPdfPageCount(Integer pdfPageCount) { this.pdfPageCount = pdfPageCount; }
    public Long getCoverFileId() { return coverFileId; }
    public void setCoverFileId(Long coverFileId) { this.coverFileId = coverFileId; }
    public String getCoverStoredName() { return coverStoredName; }
    public void setCoverStoredName(String coverStoredName) { this.coverStoredName = coverStoredName; }
    public String getCoverStoredPath() { return coverStoredPath; }
    public void setCoverStoredPath(String coverStoredPath) { this.coverStoredPath = coverStoredPath; }
    public String getCoverMimeType() { return coverMimeType; }
    public void setCoverMimeType(String coverMimeType) { this.coverMimeType = coverMimeType; }
}

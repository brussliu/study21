package com.study21.user.reading;

import java.sql.Timestamp;

/**
 * RED_書籍ファイル情報 の 1 行（書籍 1 冊の本文 PDF または表紙画像）。
 *
 * <p>実体ファイルはストレージ（{@link ReadingFileStorage}）にあり、この行は
 * その管理情報。2.0 から引き継いだ行は実体が無いことがある（`保存パス` は
 * `file/ENGLISH_READING/…` のまま）。実体の有無はストレージで判定する。</p>
 */
public class ReadingFileEntity {

    private Long fileId;
    private Long bookId;
    /** PDF=本文 / COVER=表紙画像 */
    private String fileKind;
    /** 利用者が選んだ元のファイル名 */
    private String originalName;
    /** ストレージ上のファイル名 */
    private String storedName;
    /** ストレージルートからの相対ディレクトリ（末尾 '/'） */
    private String storedPath;
    private String mimeType;
    private Long fileSize;
    /** PDF の総ページ数（不明は null） */
    private Integer pageCount;
    /** 登録・更新した人（監査用。画面には出さない） */
    private Long createdBy;
    private Long updatedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getFileId() { return fileId; }
    public void setFileId(Long fileId) { this.fileId = fileId; }
    public Long getBookId() { return bookId; }
    public void setBookId(Long bookId) { this.bookId = bookId; }
    public String getFileKind() { return fileKind; }
    public void setFileKind(String fileKind) { this.fileKind = fileKind; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getStoredName() { return storedName; }
    public void setStoredName(String storedName) { this.storedName = storedName; }
    public String getStoredPath() { return storedPath; }
    public void setStoredPath(String storedPath) { this.storedPath = storedPath; }
    public String getMimeType() { return mimeType; }
    public void setMimeType(String mimeType) { this.mimeType = mimeType; }
    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }
    public Integer getPageCount() { return pageCount; }
    public void setPageCount(Integer pageCount) { this.pageCount = pageCount; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

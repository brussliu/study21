package com.study21.user.reading;

/** 本棚のサマリ（RED_書籍情報 の集計行）。 */
public class ReadingShelfTotals {

    private Long bookCount;
    private Long readingCount;
    private Long finishedCount;
    private Long totalMinutes;
    private Long markCount;
    /** `分類ID IS NULL` の冊数（本棚の「未分類」タブのバッジ） */
    private Long uncategorizedCount;
    /** 自分の【自分の本棚】の冊数（見ている本人のアカウントの行数） */
    private Long myShelfCount;

    public long books() { return bookCount == null ? 0L : bookCount; }
    public long reading() { return readingCount == null ? 0L : readingCount; }
    public long finished() { return finishedCount == null ? 0L : finishedCount; }
    public long minutes() { return totalMinutes == null ? 0L : totalMinutes; }
    public long marks() { return markCount == null ? 0L : markCount; }
    public long uncategorized() { return uncategorizedCount == null ? 0L : uncategorizedCount; }
    public long myShelf() { return myShelfCount == null ? 0L : myShelfCount; }

    public void setBookCount(Long bookCount) { this.bookCount = bookCount; }
    public void setReadingCount(Long readingCount) { this.readingCount = readingCount; }
    public void setFinishedCount(Long finishedCount) { this.finishedCount = finishedCount; }
    public void setTotalMinutes(Long totalMinutes) { this.totalMinutes = totalMinutes; }
    public void setMarkCount(Long markCount) { this.markCount = markCount; }
    public void setUncategorizedCount(Long uncategorizedCount) { this.uncategorizedCount = uncategorizedCount; }
    public void setMyShelfCount(Long myShelfCount) { this.myShelfCount = myShelfCount; }
}

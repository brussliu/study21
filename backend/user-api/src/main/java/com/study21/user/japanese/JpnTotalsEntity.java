package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * 集計行（単語のサマリ・テストのサマリ・勉強状況のサマリで共用）。
 * 使わない列は null のまま。
 */
public class JpnTotalsEntity {

    private Long wordCount;
    private Long learnedCount;
    private Long favoriteCount;
    private BigDecimal averageMastery;
    private Long answeredCount;
    private Long correctCount;
    private Long activeMs;
    private Long todayActiveMs;
    private Timestamp lastStudiedAt;
    private Long testCount;
    private Long completedCount;
    private Long runningCount;
    private Integer averageScore;
    private Long totalActiveMs;

    public long words() { return wordCount == null ? 0L : wordCount; }
    public long learned() { return learnedCount == null ? 0L : learnedCount; }
    public long favorites() { return favoriteCount == null ? 0L : favoriteCount; }
    public BigDecimal mastery() { return averageMastery == null ? BigDecimal.ZERO : averageMastery; }
    public long answered() { return answeredCount == null ? 0L : answeredCount; }
    public long correct() { return correctCount == null ? 0L : correctCount; }
    public long active() { return activeMs == null ? 0L : activeMs; }
    public long todayActive() { return todayActiveMs == null ? 0L : todayActiveMs; }
    public long tests() { return testCount == null ? 0L : testCount; }
    public long completed() { return completedCount == null ? 0L : completedCount; }
    public long running() { return runningCount == null ? 0L : runningCount; }
    public int averageScoreValue() { return averageScore == null ? 0 : averageScore; }
    public long totalActive() { return totalActiveMs == null ? 0L : totalActiveMs; }

    public void setWordCount(Long wordCount) { this.wordCount = wordCount; }
    public void setLearnedCount(Long learnedCount) { this.learnedCount = learnedCount; }
    public void setFavoriteCount(Long favoriteCount) { this.favoriteCount = favoriteCount; }
    public void setAverageMastery(BigDecimal averageMastery) { this.averageMastery = averageMastery; }
    public void setAnsweredCount(Long answeredCount) { this.answeredCount = answeredCount; }
    public void setCorrectCount(Long correctCount) { this.correctCount = correctCount; }
    public void setActiveMs(Long activeMs) { this.activeMs = activeMs; }
    public void setTodayActiveMs(Long todayActiveMs) { this.todayActiveMs = todayActiveMs; }
    public Timestamp getLastStudiedAt() { return lastStudiedAt; }
    public void setLastStudiedAt(Timestamp lastStudiedAt) { this.lastStudiedAt = lastStudiedAt; }
    public void setTestCount(Long testCount) { this.testCount = testCount; }
    public void setCompletedCount(Long completedCount) { this.completedCount = completedCount; }
    public void setRunningCount(Long runningCount) { this.runningCount = runningCount; }
    public void setAverageScore(Integer averageScore) { this.averageScore = averageScore; }
    public void setTotalActiveMs(Long totalActiveMs) { this.totalActiveMs = totalActiveMs; }
}

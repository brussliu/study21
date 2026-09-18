package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_Test の 1 行（Mapper の戻り値）。 */
public class JpnTestEntity {

    private Long testId;
    private Long legacyTestId;
    private String testNo;
    private Long accountId;
    private String testType;
    private String level;
    private String book;
    private String categoryFrom;
    private String categoryTo;
    private String difficulty;
    private String mode;
    private Integer questionCount;
    private Integer doneCount;
    private Integer correctCount;
    private Integer wrongCount;
    private String stateCode;
    private java.sql.Timestamp startedAt;
    private java.sql.Timestamp finishedAt;
    private java.sql.Timestamp lastStudiedAt;
    private Long activeMs;
    private Integer version;

    public Long getTestId() { return testId; }
    public void setTestId(Long testId) { this.testId = testId; }
    public Long getLegacyTestId() { return legacyTestId; }
    public void setLegacyTestId(Long legacyTestId) { this.legacyTestId = legacyTestId; }
    public String getTestNo() { return testNo; }
    public void setTestNo(String testNo) { this.testNo = testNo; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getBook() { return book; }
    public void setBook(String book) { this.book = book; }
    public String getCategoryFrom() { return categoryFrom; }
    public void setCategoryFrom(String categoryFrom) { this.categoryFrom = categoryFrom; }
    public String getCategoryTo() { return categoryTo; }
    public void setCategoryTo(String categoryTo) { this.categoryTo = categoryTo; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public Integer getDoneCount() { return doneCount; }
    public void setDoneCount(Integer doneCount) { this.doneCount = doneCount; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public java.sql.Timestamp getStartedAt() { return startedAt; }
    public void setStartedAt(java.sql.Timestamp startedAt) { this.startedAt = startedAt; }
    public java.sql.Timestamp getFinishedAt() { return finishedAt; }
    public void setFinishedAt(java.sql.Timestamp finishedAt) { this.finishedAt = finishedAt; }
    public java.sql.Timestamp getLastStudiedAt() { return lastStudiedAt; }
    public void setLastStudiedAt(java.sql.Timestamp lastStudiedAt) { this.lastStudiedAt = lastStudiedAt; }
    public Long getActiveMs() { return activeMs; }
    public void setActiveMs(Long activeMs) { this.activeMs = activeMs; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
}

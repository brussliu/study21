package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_TestQuestion の 1 行（Mapper の戻り値）。 */
public class JpnTestQuestionEntity {

    private Long entryId;
    private Long testId;
    private Long wordId;
    private Long questionId;
    private Long collectionId;
    private Long accountId;
    private Integer orderNo;
    private String entryState;
    private String judgment;
    private Integer answerCount;
    private Integer wrongCount;
    private Long activeMs;
    private java.sql.Timestamp answeredAt;
    private String word;
    private String reading;
    private String questionType;
    private String questionTextJa;
    private String correctValue;
    private String explanationJa;
    private String difficulty;
    private String snapshotJson;
    private String book;
    private String category;

    public Long getEntryId() { return entryId; }
    public void setEntryId(Long entryId) { this.entryId = entryId; }
    public Long getTestId() { return testId; }
    public void setTestId(Long testId) { this.testId = testId; }
    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }
    public Long getQuestionId() { return questionId; }
    public Long getCollectionId() { return collectionId; }
    public void setCollectionId(Long collectionId) { this.collectionId = collectionId; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Integer getOrderNo() { return orderNo; }
    public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
    public String getEntryState() { return entryState; }
    public void setEntryState(String entryState) { this.entryState = entryState; }
    public String getJudgment() { return judgment; }
    public void setJudgment(String judgment) { this.judgment = judgment; }
    public Integer getAnswerCount() { return answerCount; }
    public void setAnswerCount(Integer answerCount) { this.answerCount = answerCount; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public Long getActiveMs() { return activeMs; }
    public void setActiveMs(Long activeMs) { this.activeMs = activeMs; }
    public java.sql.Timestamp getAnsweredAt() { return answeredAt; }
    public void setAnsweredAt(java.sql.Timestamp answeredAt) { this.answeredAt = answeredAt; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getReading() { return reading; }
    public void setReading(String reading) { this.reading = reading; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public String getQuestionTextJa() { return questionTextJa; }
    public void setQuestionTextJa(String questionTextJa) { this.questionTextJa = questionTextJa; }
    public String getCorrectValue() { return correctValue; }
    public void setCorrectValue(String correctValue) { this.correctValue = correctValue; }
    public String getExplanationJa() { return explanationJa; }
    public void setExplanationJa(String explanationJa) { this.explanationJa = explanationJa; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getSnapshotJson() { return snapshotJson; }
    public void setSnapshotJson(String snapshotJson) { this.snapshotJson = snapshotJson; }
    public String getBook() { return book; }
    public void setBook(String book) { this.book = book; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}

package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_Status の 1 行（Mapper の戻り値）。 */
public class JpnStatusEntity {

    private Long wordId;
    private String word;
    private String reading;
    private String jlptLevel;
    private String partOfSpeech;
    private String book;
    private String category;
    private String learnState;
    private java.math.BigDecimal mastery;
    private Boolean learned;
    private Boolean favorite;
    private Integer answeredCount;
    private Integer correctCount;
    private Integer wrongCount;
    private Integer streak;
    private Integer bestStreak;
    private Long activeMs;
    private String lastTestType;
    private String lastJudgment;
    private java.sql.Timestamp firstStudiedAt;
    private java.sql.Timestamp lastStudiedAt;
    private java.sql.Timestamp nextReviewAt;
    private Integer reviewIntervalDays;

    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getReading() { return reading; }
    public void setReading(String reading) { this.reading = reading; }
    public String getJlptLevel() { return jlptLevel; }
    public void setJlptLevel(String jlptLevel) { this.jlptLevel = jlptLevel; }
    public String getPartOfSpeech() { return partOfSpeech; }
    public void setPartOfSpeech(String partOfSpeech) { this.partOfSpeech = partOfSpeech; }
    public String getBook() { return book; }
    public void setBook(String book) { this.book = book; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLearnState() { return learnState; }
    public void setLearnState(String learnState) { this.learnState = learnState; }
    public java.math.BigDecimal getMastery() { return mastery; }
    public void setMastery(java.math.BigDecimal mastery) { this.mastery = mastery; }
    public Boolean getLearned() { return learned; }
    public void setLearned(Boolean learned) { this.learned = learned; }
    public Boolean getFavorite() { return favorite; }
    public void setFavorite(Boolean favorite) { this.favorite = favorite; }
    public Integer getAnsweredCount() { return answeredCount; }
    public void setAnsweredCount(Integer answeredCount) { this.answeredCount = answeredCount; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
    public Integer getStreak() { return streak; }
    public void setStreak(Integer streak) { this.streak = streak; }
    public Integer getBestStreak() { return bestStreak; }
    public void setBestStreak(Integer bestStreak) { this.bestStreak = bestStreak; }
    public Long getActiveMs() { return activeMs; }
    public void setActiveMs(Long activeMs) { this.activeMs = activeMs; }
    public String getLastTestType() { return lastTestType; }
    public void setLastTestType(String lastTestType) { this.lastTestType = lastTestType; }
    public String getLastJudgment() { return lastJudgment; }
    public void setLastJudgment(String lastJudgment) { this.lastJudgment = lastJudgment; }
    public java.sql.Timestamp getFirstStudiedAt() { return firstStudiedAt; }
    public void setFirstStudiedAt(java.sql.Timestamp firstStudiedAt) { this.firstStudiedAt = firstStudiedAt; }
    public java.sql.Timestamp getLastStudiedAt() { return lastStudiedAt; }
    public void setLastStudiedAt(java.sql.Timestamp lastStudiedAt) { this.lastStudiedAt = lastStudiedAt; }
    public java.sql.Timestamp getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(java.sql.Timestamp nextReviewAt) { this.nextReviewAt = nextReviewAt; }
    public Integer getReviewIntervalDays() { return reviewIntervalDays; }
    public void setReviewIntervalDays(Integer reviewIntervalDays) { this.reviewIntervalDays = reviewIntervalDays; }
}

package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_Skill の 1 行（Mapper の戻り値）。 */
public class JpnSkillEntity {

    private Long wordId;
    private String word;
    private String reading;
    private String testType;
    private String skillCode;
    private String learnState;
    private java.math.BigDecimal mastery;
    private Integer answeredCount;
    private Integer correctCount;
    private Integer wrongCount;
    private Integer streak;
    private Integer bestStreak;
    private String lastJudgment;
    private java.sql.Timestamp lastStudiedAt;
    private java.sql.Timestamp nextReviewAt;

    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getReading() { return reading; }
    public void setReading(String reading) { this.reading = reading; }
    public String getTestType() { return testType; }
    public void setTestType(String testType) { this.testType = testType; }
    public String getSkillCode() { return skillCode; }
    public void setSkillCode(String skillCode) { this.skillCode = skillCode; }
    public String getLearnState() { return learnState; }
    public void setLearnState(String learnState) { this.learnState = learnState; }
    public java.math.BigDecimal getMastery() { return mastery; }
    public void setMastery(java.math.BigDecimal mastery) { this.mastery = mastery; }
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
    public String getLastJudgment() { return lastJudgment; }
    public void setLastJudgment(String lastJudgment) { this.lastJudgment = lastJudgment; }
    public java.sql.Timestamp getLastStudiedAt() { return lastStudiedAt; }
    public void setLastStudiedAt(java.sql.Timestamp lastStudiedAt) { this.lastStudiedAt = lastStudiedAt; }
    public java.sql.Timestamp getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(java.sql.Timestamp nextReviewAt) { this.nextReviewAt = nextReviewAt; }
}

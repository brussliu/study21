package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_Daily の 1 行（Mapper の戻り値）。 */
public class JpnDailyEntity {

    private java.time.LocalDate studyDate;
    private Long activeMs;
    private Long typeAMs;
    private Long typeBMs;
    private Long typeCMs;
    private Long typeDMs;
    private Long typeEMs;
    private Integer wordCount;
    private Integer testCount;
    private Integer doneCount;
    private Integer correctCount;
    private Integer wrongCount;

    public java.time.LocalDate getStudyDate() { return studyDate; }
    public void setStudyDate(java.time.LocalDate studyDate) { this.studyDate = studyDate; }
    public Long getActiveMs() { return activeMs; }
    public void setActiveMs(Long activeMs) { this.activeMs = activeMs; }
    public Long getTypeAMs() { return typeAMs; }
    public void setTypeAMs(Long typeAMs) { this.typeAMs = typeAMs; }
    public Long getTypeBMs() { return typeBMs; }
    public void setTypeBMs(Long typeBMs) { this.typeBMs = typeBMs; }
    public Long getTypeCMs() { return typeCMs; }
    public void setTypeCMs(Long typeCMs) { this.typeCMs = typeCMs; }
    public Long getTypeDMs() { return typeDMs; }
    public void setTypeDMs(Long typeDMs) { this.typeDMs = typeDMs; }
    public Long getTypeEMs() { return typeEMs; }
    public void setTypeEMs(Long typeEMs) { this.typeEMs = typeEMs; }
    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }
    public Integer getTestCount() { return testCount; }
    public void setTestCount(Integer testCount) { this.testCount = testCount; }
    public Integer getDoneCount() { return doneCount; }
    public void setDoneCount(Integer doneCount) { this.doneCount = doneCount; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public Integer getWrongCount() { return wrongCount; }
    public void setWrongCount(Integer wrongCount) { this.wrongCount = wrongCount; }
}

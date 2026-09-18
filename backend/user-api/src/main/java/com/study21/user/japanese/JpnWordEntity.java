package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_Word の 1 行（Mapper の戻り値）。 */
public class JpnWordEntity {

    private Long wordId;
    private Long legacyWordId;
    private String word;
    private String reading;
    private String wordKey;
    private String readingKey;
    private String jlptLevel;
    private String partOfSpeech;
    private String stateCode;
    private String note;
    private Integer version;
    /** 登録・更新した人（監査用） */
    private Long createdBy;
    private Long updatedBy;
    private String book;
    private String category;
    private String level;
    private Integer wordSeq;
    private Long collectionCount;
    private String learnState;
    private java.math.BigDecimal mastery;
    private Integer answeredCount;
    private Integer correctCount;
    private Boolean favorite;
    private Boolean learned;
    private java.sql.Timestamp lastStudiedAt;
    private java.sql.Timestamp nextReviewAt;

    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }
    public Long getLegacyWordId() { return legacyWordId; }
    public void setLegacyWordId(Long legacyWordId) { this.legacyWordId = legacyWordId; }
    public String getWord() { return word; }
    public void setWord(String word) { this.word = word; }
    public String getReading() { return reading; }
    public void setReading(String reading) { this.reading = reading; }
    public String getWordKey() { return wordKey; }
    public void setWordKey(String wordKey) { this.wordKey = wordKey; }
    public String getReadingKey() { return readingKey; }
    public void setReadingKey(String readingKey) { this.readingKey = readingKey; }
    public String getJlptLevel() { return jlptLevel; }
    public void setJlptLevel(String jlptLevel) { this.jlptLevel = jlptLevel; }
    public String getPartOfSpeech() { return partOfSpeech; }
    public void setPartOfSpeech(String partOfSpeech) { this.partOfSpeech = partOfSpeech; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Integer getVersion() { return version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public void setVersion(Integer version) { this.version = version; }
    public String getBook() { return book; }
    public void setBook(String book) { this.book = book; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public Integer getWordSeq() { return wordSeq; }
    public void setWordSeq(Integer wordSeq) { this.wordSeq = wordSeq; }
    public Long getCollectionCount() { return collectionCount; }
    public void setCollectionCount(Long collectionCount) { this.collectionCount = collectionCount; }
    public String getLearnState() { return learnState; }
    public void setLearnState(String learnState) { this.learnState = learnState; }
    public java.math.BigDecimal getMastery() { return mastery; }
    public void setMastery(java.math.BigDecimal mastery) { this.mastery = mastery; }
    public Integer getAnsweredCount() { return answeredCount; }
    public void setAnsweredCount(Integer answeredCount) { this.answeredCount = answeredCount; }
    public Integer getCorrectCount() { return correctCount; }
    public void setCorrectCount(Integer correctCount) { this.correctCount = correctCount; }
    public Boolean getFavorite() { return favorite; }
    public void setFavorite(Boolean favorite) { this.favorite = favorite; }
    public Boolean getLearned() { return learned; }
    public void setLearned(Boolean learned) { this.learned = learned; }
    public java.sql.Timestamp getLastStudiedAt() { return lastStudiedAt; }
    public void setLastStudiedAt(java.sql.Timestamp lastStudiedAt) { this.lastStudiedAt = lastStudiedAt; }
    public java.sql.Timestamp getNextReviewAt() { return nextReviewAt; }
    public void setNextReviewAt(java.sql.Timestamp nextReviewAt) { this.nextReviewAt = nextReviewAt; }
}

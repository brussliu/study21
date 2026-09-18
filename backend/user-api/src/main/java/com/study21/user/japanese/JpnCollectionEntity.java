package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_Collection の 1 行（Mapper の戻り値）。 */
public class JpnCollectionEntity {

    private Long collectionId;
    private Long wordId;
    private String level;
    private String book;
    private String category;
    private Integer wordSeq;
    private String listedWord;
    private String listedReading;
    private String listedPartOfSpeech;
    private String chineseMeaning;

    public Long getCollectionId() { return collectionId; }
    public void setCollectionId(Long collectionId) { this.collectionId = collectionId; }
    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }
    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }
    public String getBook() { return book; }
    public void setBook(String book) { this.book = book; }
    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
    public Integer getWordSeq() { return wordSeq; }
    public void setWordSeq(Integer wordSeq) { this.wordSeq = wordSeq; }
    public String getListedWord() { return listedWord; }
    public void setListedWord(String listedWord) { this.listedWord = listedWord; }
    public String getListedReading() { return listedReading; }
    public void setListedReading(String listedReading) { this.listedReading = listedReading; }
    public String getListedPartOfSpeech() { return listedPartOfSpeech; }
    public void setListedPartOfSpeech(String listedPartOfSpeech) { this.listedPartOfSpeech = listedPartOfSpeech; }
    public String getChineseMeaning() { return chineseMeaning; }
    public void setChineseMeaning(String chineseMeaning) { this.chineseMeaning = chineseMeaning; }
}

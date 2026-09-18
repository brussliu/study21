package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_Question の 1 行（Mapper の戻り値）。 */
public class JpnQuestionEntity {

    private Long questionId;
    private Long wordId;
    private String questionType;
    private Integer questionNo;
    private String questionTextJa;
    private String questionTextZh;
    private String targetWord;
    private String targetReading;
    private String exampleJa;
    private String correctValue;
    private String explanationJa;
    private String difficulty;
    private String stateCode;
    private Integer choiceCount;

    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }
    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public Integer getQuestionNo() { return questionNo; }
    public void setQuestionNo(Integer questionNo) { this.questionNo = questionNo; }
    public String getQuestionTextJa() { return questionTextJa; }
    public void setQuestionTextJa(String questionTextJa) { this.questionTextJa = questionTextJa; }
    public String getQuestionTextZh() { return questionTextZh; }
    public void setQuestionTextZh(String questionTextZh) { this.questionTextZh = questionTextZh; }
    public String getTargetWord() { return targetWord; }
    public void setTargetWord(String targetWord) { this.targetWord = targetWord; }
    public String getTargetReading() { return targetReading; }
    public void setTargetReading(String targetReading) { this.targetReading = targetReading; }
    public String getExampleJa() { return exampleJa; }
    public void setExampleJa(String exampleJa) { this.exampleJa = exampleJa; }
    public String getCorrectValue() { return correctValue; }
    public void setCorrectValue(String correctValue) { this.correctValue = correctValue; }
    public String getExplanationJa() { return explanationJa; }
    public void setExplanationJa(String explanationJa) { this.explanationJa = explanationJa; }
    public String getDifficulty() { return difficulty; }
    public void setDifficulty(String difficulty) { this.difficulty = difficulty; }
    public String getStateCode() { return stateCode; }
    public Integer getChoiceCount() { return choiceCount; }
    public void setChoiceCount(Integer choiceCount) { this.choiceCount = choiceCount; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
}

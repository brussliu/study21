package com.study21.user.japanese;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;

/** JPN_Choice の 1 行（Mapper の戻り値）。 */
public class JpnChoiceEntity {

    private Long choiceId;
    private Long questionId;
    private Integer orderNo;
    private String value;
    private String reading;
    private Boolean correct;
    private String descriptionJa;
    private String descriptionZh;

    public Long getChoiceId() { return choiceId; }
    public void setChoiceId(Long choiceId) { this.choiceId = choiceId; }
    public Long getQuestionId() { return questionId; }
    public void setQuestionId(Long questionId) { this.questionId = questionId; }
    public Integer getOrderNo() { return orderNo; }
    public void setOrderNo(Integer orderNo) { this.orderNo = orderNo; }
    public String getValue() { return value; }
    public void setValue(String value) { this.value = value; }
    public String getReading() { return reading; }
    public void setReading(String reading) { this.reading = reading; }
    public Boolean getCorrect() { return correct; }
    public void setCorrect(Boolean correct) { this.correct = correct; }
    public String getDescriptionJa() { return descriptionJa; }
    public void setDescriptionJa(String descriptionJa) { this.descriptionJa = descriptionJa; }
    public String getDescriptionZh() { return descriptionZh; }
    public void setDescriptionZh(String descriptionZh) { this.descriptionZh = descriptionZh; }
}

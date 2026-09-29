package com.study21.user.english;

import java.sql.Timestamp;

/**
 * `ENG_AI添削履歴情報` の 1 行（**user-api は参照するだけ**。書き込むのは admin-api の働き手）。
 *
 * <p>級・題・設問・本文はそのときの写し（あとで作文を直しても、出したレポートは変わらない）。
 * `report` は `添削結果JSON` の生の文字列で、サービスが Jackson で JSON に展開して返す。</p>
 */
public class EnglishEssayGradingEntity {

    private Long gradingId;
    private Long essayId;
    private Integer roundNo;
    private String statusCode;
    private String level;
    private String titleJa;
    private String titleZh;
    private String questionText;
    private String essayText;
    private Integer wordCount;
    private Integer score;
    private Integer maxScore;
    /** `添削結果JSON`（JSONB）の生の文字列。 */
    private String report;
    private String failureReason;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private Timestamp createdAt;

    public Long getGradingId() { return gradingId; }
    public void setGradingId(Long gradingId) { this.gradingId = gradingId; }

    public Long getEssayId() { return essayId; }
    public void setEssayId(Long essayId) { this.essayId = essayId; }

    public Integer getRoundNo() { return roundNo; }
    public void setRoundNo(Integer roundNo) { this.roundNo = roundNo; }

    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getTitleJa() { return titleJa; }
    public void setTitleJa(String titleJa) { this.titleJa = titleJa; }

    public String getTitleZh() { return titleZh; }
    public void setTitleZh(String titleZh) { this.titleZh = titleZh; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getEssayText() { return essayText; }
    public void setEssayText(String essayText) { this.essayText = essayText; }

    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }

    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }

    public Integer getMaxScore() { return maxScore; }
    public void setMaxScore(Integer maxScore) { this.maxScore = maxScore; }

    public String getReport() { return report; }
    public void setReport(String report) { this.report = report; }

    public String getFailureReason() { return failureReason; }
    public void setFailureReason(String failureReason) { this.failureReason = failureReason; }

    public Timestamp getStartedAt() { return startedAt; }
    public void setStartedAt(Timestamp startedAt) { this.startedAt = startedAt; }

    public Timestamp getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Timestamp finishedAt) { this.finishedAt = finishedAt; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

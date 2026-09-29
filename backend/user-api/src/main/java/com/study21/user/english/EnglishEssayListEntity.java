package com.study21.user.english;

import java.sql.Timestamp;

/**
 * 一覧（`GET /api/user/english-essays`）の 1 行。
 *
 * <p>作文の列に、LATERAL でまとめた**画像枚数**と**最新の添削**（回数 DESC LIMIT 1）を足したもの。
 * 1 件ずつ引き直さない（N+1 を作らない）。</p>
 *
 * <p>`ownerAccountId` / `ownerName` は**その作文の持ち主**（保護者が子どもの作文を見たときに
 * 「誰の作文か」を画面へ出すため。`ACC_アカウント` を 1 回だけ左結合して取る）。</p>
 */
public class EnglishEssayListEntity {

    private Long essayId;

    /** その作文の持ち主（＝`ENG_英作文情報.利用者アカウントID`）。 */
    private Long ownerAccountId;
    /** 持ち主の氏名（`姓 + 名`。無ければ null）。 */
    private String ownerName;

    private String level;
    private String title;
    private String titleZh;
    private String questionText;
    private String essayText;
    private Integer wordCount;
    private Integer imageCount;
    private Integer questionImageCount;
    private Integer answerImageCount;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    /** 最新の添削（まだ無ければすべて null）。 */
    private Long latestGradingId;
    private Integer latestGradingRound;
    private String latestGradingStatus;
    private Integer latestGradingScore;
    private Integer latestGradingMaxScore;
    private Timestamp latestGradingCreatedAt;

    public Long getEssayId() { return essayId; }
    public void setEssayId(Long essayId) { this.essayId = essayId; }

    public Long getOwnerAccountId() { return ownerAccountId; }
    public void setOwnerAccountId(Long ownerAccountId) { this.ownerAccountId = ownerAccountId; }

    public String getOwnerName() { return ownerName; }
    public void setOwnerName(String ownerName) { this.ownerName = ownerName; }

    public String getLevel() { return level; }
    public void setLevel(String level) { this.level = level; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getTitleZh() { return titleZh; }
    public void setTitleZh(String titleZh) { this.titleZh = titleZh; }

    public String getQuestionText() { return questionText; }
    public void setQuestionText(String questionText) { this.questionText = questionText; }

    public String getEssayText() { return essayText; }
    public void setEssayText(String essayText) { this.essayText = essayText; }

    public Integer getWordCount() { return wordCount; }
    public void setWordCount(Integer wordCount) { this.wordCount = wordCount; }

    public Integer getImageCount() { return imageCount; }
    public void setImageCount(Integer imageCount) { this.imageCount = imageCount; }

    public Integer getQuestionImageCount() { return questionImageCount; }
    public void setQuestionImageCount(Integer questionImageCount) { this.questionImageCount = questionImageCount; }

    public Integer getAnswerImageCount() { return answerImageCount; }
    public void setAnswerImageCount(Integer answerImageCount) { this.answerImageCount = answerImageCount; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }

    public Long getLatestGradingId() { return latestGradingId; }
    public void setLatestGradingId(Long latestGradingId) { this.latestGradingId = latestGradingId; }

    public Integer getLatestGradingRound() { return latestGradingRound; }
    public void setLatestGradingRound(Integer latestGradingRound) { this.latestGradingRound = latestGradingRound; }

    public String getLatestGradingStatus() { return latestGradingStatus; }
    public void setLatestGradingStatus(String latestGradingStatus) { this.latestGradingStatus = latestGradingStatus; }

    public Integer getLatestGradingScore() { return latestGradingScore; }
    public void setLatestGradingScore(Integer latestGradingScore) { this.latestGradingScore = latestGradingScore; }

    public Integer getLatestGradingMaxScore() { return latestGradingMaxScore; }
    public void setLatestGradingMaxScore(Integer latestGradingMaxScore) {
        this.latestGradingMaxScore = latestGradingMaxScore;
    }

    public Timestamp getLatestGradingCreatedAt() { return latestGradingCreatedAt; }
    public void setLatestGradingCreatedAt(Timestamp latestGradingCreatedAt) {
        this.latestGradingCreatedAt = latestGradingCreatedAt;
    }
}

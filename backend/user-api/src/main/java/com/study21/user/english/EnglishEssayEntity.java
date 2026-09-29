package com.study21.user.english;

import java.sql.Timestamp;

/**
 * `ENG_英作文情報` の 1 行（MyBatis が埋める入れ物）。
 *
 * <p>添削の結果はここに持たない（`ENG_AI添削履歴情報` に積む。何度も添削できる）。</p>
 */
public class EnglishEssayEntity {

    private Long essayId;
    private Long accountId;
    /**
     * その作文の持ち主（＝`利用者アカウントID`）。`findById` が返す**表示用の写し**で、
     * `accountId` と同じ値。保護者が子どもの作文を見たときに「誰の作文か」を画面へ出すために持つ
     * （登録・更新では使わない。書き込みは `accountId`＝自分の行だけ）。
     */
    private Long ownerAccountId;
    /** 持ち主の氏名（`ACC_アカウント.姓 + 名`。無ければ null）。同じく表示用。 */
    private String ownerName;
    private String level;
    private String title;
    private String titleZh;
    private String questionText;
    private String essayText;
    private Integer wordCount;
    private String stateCode;
    private Integer version;
    private Long createdBy;
    private Long updatedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getEssayId() { return essayId; }
    public void setEssayId(Long essayId) { this.essayId = essayId; }

    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }

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

    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }

    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }

    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }

    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }

    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }

    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

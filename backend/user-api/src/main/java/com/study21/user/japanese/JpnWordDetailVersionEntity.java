package com.study21.user.japanese;

import java.sql.Timestamp;

/**
 * 「版の履歴」の 1 行（{@code JPN_単語詳細情報} のヘッダ ＋ 段落の行数）。
 *
 * <p>一覧は 1 回の SQL で引く（版ごとに子テーブルを数えると N+1 になるので、段落の行数は
 * 副問い合わせでまとめて数える）。数える対象は段落の親テーブル 10 個で、会話の発言
 * （{@code JPN_単語詳細_会話行情報}）は会話（{@code dialogs}）に含まれるので数えない。</p>
 */
public class JpnWordDetailVersionEntity {

    private Long detailId;
    private Integer contentVersion;
    /** 楽観的ロック（画面が読んだときに渡し、切り替えのときに突き合わせる）。 */
    private Integer version;
    /** ACTIVE=有効版 / ARCHIVED=履歴。 */
    private String stateCode;
    /** 版のヘッダの 手修正フラグ（人が作った版か）。 */
    private Boolean manualCorrected;
    /** 段落のいずれかに人の行（手修正フラグ / 登録元コード='APP'）があるか。 */
    private Boolean manualRows;
    private String aiProvider;
    private String aiModel;
    private Long generationId;
    private Timestamp fetchedAt;
    private String note;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    private Integer senseCount;
    private Integer exampleCount;
    private Integer patternCount;
    private Integer dialogCount;
    private Integer synonymCount;
    private Integer cautionCount;
    private Integer collocationCount;
    private Integer relatedWordCount;
    private Integer usageNoteCount;
    private Integer practiceCount;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer contentVersion) { this.contentVersion = contentVersion; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public Boolean getManualCorrected() { return manualCorrected; }
    public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
    public Boolean getManualRows() { return manualRows; }
    public void setManualRows(Boolean manualRows) { this.manualRows = manualRows; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    public Long getGenerationId() { return generationId; }
    public void setGenerationId(Long generationId) { this.generationId = generationId; }
    public Timestamp getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Timestamp fetchedAt) { this.fetchedAt = fetchedAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    public Integer getSenseCount() { return senseCount; }
    public void setSenseCount(Integer senseCount) { this.senseCount = senseCount; }
    public Integer getExampleCount() { return exampleCount; }
    public void setExampleCount(Integer exampleCount) { this.exampleCount = exampleCount; }
    public Integer getPatternCount() { return patternCount; }
    public void setPatternCount(Integer patternCount) { this.patternCount = patternCount; }
    public Integer getDialogCount() { return dialogCount; }
    public void setDialogCount(Integer dialogCount) { this.dialogCount = dialogCount; }
    public Integer getSynonymCount() { return synonymCount; }
    public void setSynonymCount(Integer synonymCount) { this.synonymCount = synonymCount; }
    public Integer getCautionCount() { return cautionCount; }
    public void setCautionCount(Integer cautionCount) { this.cautionCount = cautionCount; }
    public Integer getCollocationCount() { return collocationCount; }
    public void setCollocationCount(Integer collocationCount) { this.collocationCount = collocationCount; }
    public Integer getRelatedWordCount() { return relatedWordCount; }
    public void setRelatedWordCount(Integer relatedWordCount) { this.relatedWordCount = relatedWordCount; }
    public Integer getUsageNoteCount() { return usageNoteCount; }
    public void setUsageNoteCount(Integer usageNoteCount) { this.usageNoteCount = usageNoteCount; }
    public Integer getPracticeCount() { return practiceCount; }
    public void setPracticeCount(Integer practiceCount) { this.practiceCount = practiceCount; }
}

package com.study21.user.japanese;

/**
 * 1 語の**有効版**の詳細にある段落の件数（一覧の「詳細情報件数」列）。
 *
 * <p>2.0 の英語学習の単語情報管理（`word.jsp`）は、詳細情報を 1 つの JSON に持っていたので
 * **JSON の配列の長さ**を数えて「語義 2・例文 3…」のように出していた
 * （`WordServiceImpl#intermediateDetailCounts`）。2.1 は段落を子テーブルに分けて持つので、
 * **行数を数える**のが同じ意味になる。</p>
 *
 * <p>数えるのは段落の親テーブル 10 個。会話の発言（{@code JPN_単語詳細_会話行情報}）は
 * 会話（{@code dialogs}）に含まれるので数えない（版の履歴の行数と同じ規則）。</p>
 */
public class JpnDetailCountEntity {

    private Long wordId;
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

    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }
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

package com.study21.common.core.japanese;

import java.sql.Timestamp;

/**
 * JPN_単語詳細情報（語の詳細の「版」＝バージョン）の 1 行。
 *
 * <p>user-api と admin-api の両方が読むため common-core に置く（どちらのモジュールも
 * common-core に依存している）。</p>
 *
 * <p><strong>JSONB の列は文字列として持つ</strong>（{@code 発音JSON}・{@code 活用形JSON}・
 * {@code 自他対応JSON}・{@code 元レスポンスJSON}・{@code 選択肢JSON}）。
 * PostgreSQL のドライバが返す JSONB の値をそのまま文字列で受け取り、
 * 組み立て（{@link JpnWordDetailAssembler}）で Jackson が読む。
 * 列ごとの TypeHandler を足さずに済み、AI が形を変えても壊れない。</p>
 */
public class JpnWordDetailEntity {

    /* ----- 版の管理（JPN_単語詳細情報 の列） ----- */

    private Long detailId;
    private Long wordId;
    private Integer contentVersion;
    /**
     * 楽観ロック（版ごとの {@code バージョン}）。
     *
     * <p>画面はこの値を控えておき、「この版を使う」ときに渡す。ほかの操作が先に
     * 更新していれば食い違うので 409 になる。</p>
     */
    private Integer version;
    /** ACTIVE=有効版（1 語に 1 行） / ARCHIVED=履歴。 */
    private String stateCode;
    /** この版が基にした版（最初の版は NULL）。 */
    private Long originDetailId;
    /** この版を作った AI 生成（JPN_AI生成履歴情報.生成ID）。人の編集版は NULL。 */
    private Long generationId;
    private String aiProvider;
    private String aiModel;
    private Timestamp fetchedAt;
    /** この版に人の編集が含まれるか。 */
    private Boolean manualCorrected;
    private String note;

    /* ----- 語レベルの内容 ----- */

    private String coreMeaning;
    private String descriptionJa;
    private String descriptionZh;
    private String partOfSpeech;
    private String jlptLevel;
    private String conjugation;
    private String transitivity;
    private Integer importance;
    private String memoryHint;
    private String memoryHintBasis;

    /** 発音JSON（オブジェクト）。読み・アクセント型・アクセント表記・ヒント・音声の有無。 */
    private String pronunciationJson;
    /** 活用形JSON（配列）。{@code [{form, value, example}]}。 */
    private String conjugationsJson;
    /** 自他対応JSON（オブジェクト）。無い語は NULL。 */
    private String transitivityPairJson;
    /** 元レスポンスJSON（AI の生の応答。組み立ての {@code structured} になる）。 */
    private String structuredJson;
    /** この版を作った人（{@code 登録者アカウントID}）。AI の版は NULL。 */
    private Long createdBy;

    public Long getDetailId() { return detailId; }
    public void setDetailId(Long detailId) { this.detailId = detailId; }
    public Long getWordId() { return wordId; }
    public void setWordId(Long wordId) { this.wordId = wordId; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer contentVersion) { this.contentVersion = contentVersion; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public Long getOriginDetailId() { return originDetailId; }
    public void setOriginDetailId(Long originDetailId) { this.originDetailId = originDetailId; }
    public Long getGenerationId() { return generationId; }
    public void setGenerationId(Long generationId) { this.generationId = generationId; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    public Timestamp getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Timestamp fetchedAt) { this.fetchedAt = fetchedAt; }
    public Boolean getManualCorrected() { return manualCorrected; }
    public void setManualCorrected(Boolean manualCorrected) { this.manualCorrected = manualCorrected; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getCoreMeaning() { return coreMeaning; }
    public void setCoreMeaning(String coreMeaning) { this.coreMeaning = coreMeaning; }
    public String getDescriptionJa() { return descriptionJa; }
    public void setDescriptionJa(String descriptionJa) { this.descriptionJa = descriptionJa; }
    public String getDescriptionZh() { return descriptionZh; }
    public void setDescriptionZh(String descriptionZh) { this.descriptionZh = descriptionZh; }
    public String getPartOfSpeech() { return partOfSpeech; }
    public void setPartOfSpeech(String partOfSpeech) { this.partOfSpeech = partOfSpeech; }
    public String getJlptLevel() { return jlptLevel; }
    public void setJlptLevel(String jlptLevel) { this.jlptLevel = jlptLevel; }
    public String getConjugation() { return conjugation; }
    public void setConjugation(String conjugation) { this.conjugation = conjugation; }
    public String getTransitivity() { return transitivity; }
    public void setTransitivity(String transitivity) { this.transitivity = transitivity; }
    public Integer getImportance() { return importance; }
    public void setImportance(Integer importance) { this.importance = importance; }
    public String getMemoryHint() { return memoryHint; }
    public void setMemoryHint(String memoryHint) { this.memoryHint = memoryHint; }
    public String getMemoryHintBasis() { return memoryHintBasis; }
    public void setMemoryHintBasis(String memoryHintBasis) { this.memoryHintBasis = memoryHintBasis; }
    public String getPronunciationJson() { return pronunciationJson; }
    public void setPronunciationJson(String pronunciationJson) { this.pronunciationJson = pronunciationJson; }
    public String getConjugationsJson() { return conjugationsJson; }
    public void setConjugationsJson(String conjugationsJson) { this.conjugationsJson = conjugationsJson; }
    public String getTransitivityPairJson() { return transitivityPairJson; }
    public void setTransitivityPairJson(String transitivityPairJson) { this.transitivityPairJson = transitivityPairJson; }
    public String getStructuredJson() { return structuredJson; }
    public void setStructuredJson(String structuredJson) { this.structuredJson = structuredJson; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
}

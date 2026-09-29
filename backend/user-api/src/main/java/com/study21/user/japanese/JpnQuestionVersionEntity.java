package com.study21.user.japanese;

import java.sql.Timestamp;

/**
 * 問題（C/D/E）の 1 版（一覧の「取得状態」から開く履歴の 1 行）。
 *
 * <p>2.0 の英語学習の単語情報管理（`word.jsp`）の「詳細情報取得履歴」は、AI の取得 1 回を 1 行として
 * プロバイダ・モデル・状態・取得日時・リトライ回数を出し、**そのうちの 1 つを「使用する版」として
 * 指定**できた。2.1 の日本語学習も同じで、1 行 = `JPN_AI生成履歴情報` の 1 回（＝版
 * {@code 内容版数}）に対応し、その版で書かれた問題（{@code JPN_単語問題情報.内容版数}）を
 * 有効／無効にする。</p>
 *
 * <p>問題そのものの行は AI の実行管理（モデル・失敗理由）を持たない（設計どおり。
 * `TBL_JPN_単語問題情報.sql` の注記）ので、履歴の表示は生成履歴から引く。</p>
 */
public class JpnQuestionVersionEntity {

    /** C1_READING / C2_KANJI / D_CONTEXT_MEANING / E_KANJI_USAGE */
    private String questionType;
    /** この取得の版（同じ語・同じ種別で 1 から数える）。 */
    private Integer contentVersion;
    /** その版の問題数（0 = 失敗した取得。切り替えられない）。 */
    private Integer questionCount;
    /** その版の問題が今使われているか。 */
    private Boolean active;
    /** 生成の状態（RUNNING / SUCCEEDED / FAILED）。 */
    private String generationState;
    private String aiProvider;
    private String aiModel;
    private Integer generatedCount;
    private Integer failedCount;
    private String errorMessage;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private Long generationId;

    public String getQuestionType() { return questionType; }
    public void setQuestionType(String questionType) { this.questionType = questionType; }
    public Integer getContentVersion() { return contentVersion; }
    public void setContentVersion(Integer contentVersion) { this.contentVersion = contentVersion; }
    public Integer getQuestionCount() { return questionCount; }
    public void setQuestionCount(Integer questionCount) { this.questionCount = questionCount; }
    public Boolean getActive() { return active; }
    public void setActive(Boolean active) { this.active = active; }
    public String getGenerationState() { return generationState; }
    public void setGenerationState(String generationState) { this.generationState = generationState; }
    public String getAiProvider() { return aiProvider; }
    public void setAiProvider(String aiProvider) { this.aiProvider = aiProvider; }
    public String getAiModel() { return aiModel; }
    public void setAiModel(String aiModel) { this.aiModel = aiModel; }
    public Integer getGeneratedCount() { return generatedCount; }
    public void setGeneratedCount(Integer generatedCount) { this.generatedCount = generatedCount; }
    public Integer getFailedCount() { return failedCount; }
    public void setFailedCount(Integer failedCount) { this.failedCount = failedCount; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Timestamp getStartedAt() { return startedAt; }
    public void setStartedAt(Timestamp startedAt) { this.startedAt = startedAt; }
    public Timestamp getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Timestamp finishedAt) { this.finishedAt = finishedAt; }
    public Long getGenerationId() { return generationId; }
    public void setGenerationId(Long generationId) { this.generationId = generationId; }
}

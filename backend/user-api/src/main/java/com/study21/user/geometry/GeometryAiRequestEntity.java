package com.study21.user.geometry;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * GEO_AI生図リクエスト情報 の 1 行（Mapper の戻り値）。
 *
 * <p>AI 生図（画像 → 分類 → AI → GeoGebra コマンド）の 1 リクエスト = 1 行。
 * 画像は**ファイルで持つ**（パス + ファイル名）ので、この行には画像の中身を入れない。
 * GeoGebraXML とサムネイルもここには持たない（作図画面の applet が作り、
 * `GEO_図形情報` に保存する）。</p>
 *
 * <p>AI 生成（batC51/52/53）は admin-api が行う。両サービスは互いを呼べないので、
 * 橋渡しは**この表の状態列だけ**にする（docs/ARCHITECTURE.md §3）。</p>
 */
public class GeometryAiRequestEntity {

    private Long requestId;
    /** 利用者に見せる番号（'AIG' + yyyyMMddHHmmssSSS + 4桁） */
    private String requestNo;
    /** QUEUED / PREPROCESSED / GENERATING / GENERATED / READY / REGISTERED / FAILED / CANCELLED */
    private String statusCode;

    private String originalPath;
    private String originalName;
    private String originalMime;
    private Long originalSize;
    private Integer originalWidth;
    private Integer originalHeight;
    private String croppedPath;
    private String croppedName;
    private Long croppedSize;
    private Integer croppedWidth;
    private Integer croppedHeight;
    /** 切り抜き範囲（元画像に対する正規化座標 0..1。画面の CropRect と同じ） */
    private BigDecimal cropX;
    private BigDecimal cropY;
    private BigDecimal cropW;
    private BigDecimal cropH;

    /** 利用者が選んだ大分類（FIGURE / FUNCTION / MIXED。NULL は未指定） */
    /** A / B / C / D（作図モード）。NULL は歴史的な要求（モードが無い時代。A として扱う） */
    private String mode;
    /** AUTO / GEOMETRY / GRAPH / MIXED（利用者が指定した「作成する図の種類」） */
    private String requestedOutputType;
    /** GEOMETRY / GRAPH / MIXED（実際に作る種類。AI が決めた／検証で確定した値） */
    private String resolvedOutputType;
    /** モードと種類に当てはまる補充パラメータ（日本語の項目名 → 値の JSON） */
    private String supplementsJson;
    /** 追跡用の設定スナップショット（テンプレートの版。API キーは入らない） */
    private String settingsSnapshotJson;
    /** AI の判定（GENERATABLE / NEEDS_INPUT / UNSUPPORTED）。実行・保存の結果ではない */
    private String outcome;
    /** 追加入力待ちの質問（JSON 配列） */
    private String questionsJson;

    private String userKind;
    /** 利用者が選んだ図形の種類（TRIANGLE / CIRCLE / QUAD / OTHER。利用者区分が FIGURE のときだけ） */
    private String userSubKind;
    /** AI が判定した大分類（FIGURE / FUNCTION / MIXED / UNKNOWN） */
    private String aiKind;
    /** GeoGebra の appName に対応する最終種別（geometry / function） */
    private String figureType;

    private String note;
    private String prompt;
    private String commands;
    private Integer commandCount;
    /** 提案（図形名・タグ・メモ・認識テキスト）の JSON 文字列 */
    private String proposalJson;
    private String validationError;
    private Long aiCallId;
    private Integer retryCount;

    /** PREPROCESS / GENERATE / VALIDATE */
    private String failedStage;
    private String errorCode;
    private String errorMessage;

    private Long preprocessExecutionId;
    private Long aiExecutionId;
    private Long validateExecutionId;
    private Long figureId;

    private Integer version;
    private Long createdBy;
    private Long updatedBy;
    private String sourceCode;
    private String updateSourceCode;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getRequestId() { return requestId; }
    public void setRequestId(Long requestId) { this.requestId = requestId; }
    public String getRequestNo() { return requestNo; }
    public void setRequestNo(String requestNo) { this.requestNo = requestNo; }
    public String getStatusCode() { return statusCode; }
    public void setStatusCode(String statusCode) { this.statusCode = statusCode; }
    public String getOriginalPath() { return originalPath; }
    public void setOriginalPath(String originalPath) { this.originalPath = originalPath; }
    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }
    public String getOriginalMime() { return originalMime; }
    public void setOriginalMime(String originalMime) { this.originalMime = originalMime; }
    public Long getOriginalSize() { return originalSize; }
    public void setOriginalSize(Long originalSize) { this.originalSize = originalSize; }
    public Integer getOriginalWidth() { return originalWidth; }
    public void setOriginalWidth(Integer originalWidth) { this.originalWidth = originalWidth; }
    public Integer getOriginalHeight() { return originalHeight; }
    public void setOriginalHeight(Integer originalHeight) { this.originalHeight = originalHeight; }
    public String getCroppedPath() { return croppedPath; }
    public void setCroppedPath(String croppedPath) { this.croppedPath = croppedPath; }
    public String getCroppedName() { return croppedName; }
    public void setCroppedName(String croppedName) { this.croppedName = croppedName; }
    public Long getCroppedSize() { return croppedSize; }
    public void setCroppedSize(Long croppedSize) { this.croppedSize = croppedSize; }
    public Integer getCroppedWidth() { return croppedWidth; }
    public void setCroppedWidth(Integer croppedWidth) { this.croppedWidth = croppedWidth; }
    public Integer getCroppedHeight() { return croppedHeight; }
    public void setCroppedHeight(Integer croppedHeight) { this.croppedHeight = croppedHeight; }
    public BigDecimal getCropX() { return cropX; }
    public void setCropX(BigDecimal cropX) { this.cropX = cropX; }
    public BigDecimal getCropY() { return cropY; }
    public void setCropY(BigDecimal cropY) { this.cropY = cropY; }
    public BigDecimal getCropW() { return cropW; }
    public void setCropW(BigDecimal cropW) { this.cropW = cropW; }
    public BigDecimal getCropH() { return cropH; }
    public void setCropH(BigDecimal cropH) { this.cropH = cropH; }
    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }
    public String getRequestedOutputType() { return requestedOutputType; }
    public void setRequestedOutputType(String requestedOutputType) { this.requestedOutputType = requestedOutputType; }
    public String getResolvedOutputType() { return resolvedOutputType; }
    public void setResolvedOutputType(String resolvedOutputType) { this.resolvedOutputType = resolvedOutputType; }
    public String getSupplementsJson() { return supplementsJson; }
    public void setSupplementsJson(String supplementsJson) { this.supplementsJson = supplementsJson; }
    public String getSettingsSnapshotJson() { return settingsSnapshotJson; }
    public void setSettingsSnapshotJson(String settingsSnapshotJson) { this.settingsSnapshotJson = settingsSnapshotJson; }
    public String getOutcome() { return outcome; }
    public void setOutcome(String outcome) { this.outcome = outcome; }
    public String getQuestionsJson() { return questionsJson; }
    public void setQuestionsJson(String questionsJson) { this.questionsJson = questionsJson; }
    public String getUserKind() { return userKind; }
    public void setUserKind(String userKind) { this.userKind = userKind; }
    public String getUserSubKind() { return userSubKind; }
    public void setUserSubKind(String userSubKind) { this.userSubKind = userSubKind; }
    public String getAiKind() { return aiKind; }
    public void setAiKind(String aiKind) { this.aiKind = aiKind; }
    public String getFigureType() { return figureType; }
    public void setFigureType(String figureType) { this.figureType = figureType; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public String getPrompt() { return prompt; }
    public void setPrompt(String prompt) { this.prompt = prompt; }
    public String getCommands() { return commands; }
    public void setCommands(String commands) { this.commands = commands; }
    public Integer getCommandCount() { return commandCount; }
    public void setCommandCount(Integer commandCount) { this.commandCount = commandCount; }
    public String getProposalJson() { return proposalJson; }
    public void setProposalJson(String proposalJson) { this.proposalJson = proposalJson; }
    public String getValidationError() { return validationError; }
    public void setValidationError(String validationError) { this.validationError = validationError; }
    public Long getAiCallId() { return aiCallId; }
    public void setAiCallId(Long aiCallId) { this.aiCallId = aiCallId; }
    public Integer getRetryCount() { return retryCount; }
    public void setRetryCount(Integer retryCount) { this.retryCount = retryCount; }
    public String getFailedStage() { return failedStage; }
    public void setFailedStage(String failedStage) { this.failedStage = failedStage; }
    public String getErrorCode() { return errorCode; }
    public void setErrorCode(String errorCode) { this.errorCode = errorCode; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
    public Long getPreprocessExecutionId() { return preprocessExecutionId; }
    public void setPreprocessExecutionId(Long preprocessExecutionId) { this.preprocessExecutionId = preprocessExecutionId; }
    public Long getAiExecutionId() { return aiExecutionId; }
    public void setAiExecutionId(Long aiExecutionId) { this.aiExecutionId = aiExecutionId; }
    public Long getValidateExecutionId() { return validateExecutionId; }
    public void setValidateExecutionId(Long validateExecutionId) { this.validateExecutionId = validateExecutionId; }
    public Long getFigureId() { return figureId; }
    public void setFigureId(Long figureId) { this.figureId = figureId; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public String getSourceCode() { return sourceCode; }
    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public String getUpdateSourceCode() { return updateSourceCode; }
    public void setUpdateSourceCode(String updateSourceCode) { this.updateSourceCode = updateSourceCode; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

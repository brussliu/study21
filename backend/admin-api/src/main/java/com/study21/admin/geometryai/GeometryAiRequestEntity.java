package com.study21.admin.geometryai;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * GEO_AI生図リクエスト情報 の 1 行（admin-api 側）。
 *
 * <p>AI 生図の 3 工程（batC51/52/53）が読む・更新する列だけを持つ。画面向けの入口は user-api に
 * あり、両サービスは互いを呼べないので**この表の状態列だけ**で橋渡しする
 * （`docs/ARCHITECTURE.md` §3）。</p>
 */
public class GeometryAiRequestEntity {

    private Long requestId;
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
    private BigDecimal cropX;
    private BigDecimal cropY;
    private BigDecimal cropW;
    private BigDecimal cropH;

    /** A / B / C / D（作図モード）。NULL は歴史的な要求（モードが無い時代。A として扱う） */
    private String mode;
    /** AUTO / GEOMETRY / GRAPH / MIXED（利用者が指定した「作成する図の種類」） */
    private String requestedOutputType;
    /** GEOMETRY / GRAPH / MIXED（実際に作る種類。AUTO のときだけ AI が決める） */
    private String resolvedOutputType;
    /** モード・結果種別ごとの補充パラメータ（JSON。**そのモードに当てはまる項目だけ**を入れる） */
    private String supplementsJson;
    /** 追跡用の設定スナップショット（JSON。テンプレートの版＝ハッシュ。API キーは入れない） */
    private String settingsSnapshotJson;
    /** GENERATABLE / NEEDS_INPUT / UNSUPPORTED（AI の判定。実行・検証・保存の結果ではない） */
    private String outcome;
    /** 確認質問（JSON 配列。NEEDS_INPUT のとき画面が復元して表示する） */
    private String questionsJson;

    /** FIGURE / FUNCTION / MIXED（利用者が選んだ大分類。歴史的な要求の名残） */
    private String userKind;
    /** TRIANGLE / CIRCLE / QUAD / OTHER */
    private String userSubKind;
    /** FIGURE / FUNCTION / MIXED / UNKNOWN（AI が判定した大分類） */
    private String aiKind;
    /** geometry / function（GeoGebra の appName を決める最終値） */
    private String figureType;

    private String note;
    private String prompt;
    private String commands;
    private Integer commandCount;
    private String proposalJson;
    private String validationError;
    private Long aiCallId;
    private Integer retryCount;

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
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

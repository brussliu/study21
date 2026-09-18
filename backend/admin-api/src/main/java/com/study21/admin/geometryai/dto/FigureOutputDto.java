package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * A〜D の出力 DTO が**共有する公共の項目**（設計 §7 の公共字段）。
 *
 * <p>このクラスが「AI 出力の共通部分」の唯一の定義。モードごとの DTO はこのクラスを継承して
 * 固有の項目を足す（{@link BatC51AResultDto} 〜 {@link BatC51DResultDto}）。継承にしているのは、
 * **JSON Schema の生成（プロンプトへ渡す出力形式・設定ページの Data TAB）で共通項目が
 * 各モードに自動で出る**ようにするため（共通項目をモードごとに書き写さない）。</p>
 *
 * <p>キーは日本語（プロンプトの日本語と揃える）。英語キーは {@link JsonAlias} で受ける。</p>
 *
 * <p><strong>{@code 判定}（outcome）は「AI が作図データを出せたか」だけを表す。</strong>
 * 実際にコマンドが実行できたか・条件を満たしたか・保存できたかは、サーバーの実行・検証・保存が決める。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class FigureOutputDto {

    @Schema(description = "AI の判定。GENERATABLE=作図データを作れた / "
            + "NEEDS_INPUT=情報が足りない（質問を返す）/ UNSUPPORTED=対応できない",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("判定")
    @JsonAlias({"outcome"})
    private FigureOutcome outcome;

    @Schema(description = "利用者が指定した「作成する図の種類」。"
            + "AUTO=自動判定 / GEOMETRY=幾何図形 / GRAPH=関数・方程式のグラフ / MIXED=図形とグラフの組み合わせ。"
            + "**指定があるときは必ず従う**（合わない・作れないときは黙って変えず、説明して確認を求める）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("要求図種")
    @JsonAlias({"requestedOutputType"})
    private FigureOutputType requestedOutputType;

    @Schema(description = "実際に作る図の種類（要求図種が AUTO のときは AI が決める。"
            + "決められないときは null）。AUTO は書かない")
    @JsonProperty("確定図種")
    @JsonAlias({"resolvedOutputType"})
    private FigureOutputType resolvedOutputType;

    @Schema(description = "作図の名前（例: 三角形ABC、放物線 y = x^2）")
    @JsonProperty("タイトル")
    @JsonAlias({"title"})
    private String title;

    @Schema(description = "作図の説明（日本語 1〜3 文）")
    @JsonProperty("説明")
    @JsonAlias({"description"})
    private String description;

    @Schema(description = "明確な条件（与えられた条件と、そこから導いた条件）")
    @JsonProperty("明確条件")
    @JsonAlias({"conditions"})
    private List<FigureParts.Condition> conditions;

    @Schema(description = "作図のために計算した結果（値と根拠）")
    @JsonProperty("導出結果")
    @JsonAlias({"derived"})
    private List<FigureParts.Value> derived;

    @Schema(description = "近似の仮定（情報が足りず置いた仮定。明確な数値・条件を上書きできない）")
    @JsonProperty("近似仮定")
    @JsonAlias({"approximations"})
    private List<FigureParts.Approximation> approximations;

    @Schema(description = "警告（作図に影響する注意。日本語）")
    @JsonProperty("警告")
    @JsonAlias({"warnings"})
    private List<String> warnings;

    @Schema(description = "利用者への質問（判定が NEEDS_INPUT のときに必ず入れる）")
    @JsonProperty("質問")
    @JsonAlias({"questions"})
    private List<FigureParts.Question> questions;

    @Schema(description = "検証の目標（作図が条件を満たしているかを確かめる対象）")
    @JsonProperty("検証目標")
    @JsonAlias({"verificationTargets"})
    private List<FigureParts.VerificationTarget> verificationTargets;

    @Schema(description = "作図オブジェクト（**A〜D が共有する作図の構造**。"
            + "点・線・円・多角形だけでなく、関数・曲線・定義域もここに書く）")
    @JsonProperty("作図オブジェクト")
    @JsonAlias({"objects"})
    private List<FigureParts.MathObject> objects;

    @Schema(description = "GeoGebra のコマンド（1 要素 = 1 コマンド。改行は入れない）。"
            + "判定が NEEDS_INPUT / UNSUPPORTED のときは空でよい")
    @JsonProperty("コマンド")
    @JsonAlias({"commands", "Command"})
    private List<String> commands;

    public FigureOutcome getOutcome() {
        return outcome;
    }

    public void setOutcome(FigureOutcome outcome) {
        this.outcome = outcome;
    }

    public FigureOutputType getRequestedOutputType() {
        return requestedOutputType;
    }

    public void setRequestedOutputType(FigureOutputType requestedOutputType) {
        this.requestedOutputType = requestedOutputType;
    }

    public FigureOutputType getResolvedOutputType() {
        return resolvedOutputType;
    }

    public void setResolvedOutputType(FigureOutputType resolvedOutputType) {
        this.resolvedOutputType = resolvedOutputType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<FigureParts.Condition> getConditions() {
        return conditions;
    }

    public void setConditions(List<FigureParts.Condition> conditions) {
        this.conditions = conditions;
    }

    public List<FigureParts.Value> getDerived() {
        return derived;
    }

    public void setDerived(List<FigureParts.Value> derived) {
        this.derived = derived;
    }

    public List<FigureParts.Approximation> getApproximations() {
        return approximations;
    }

    public void setApproximations(List<FigureParts.Approximation> approximations) {
        this.approximations = approximations;
    }

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }

    public List<FigureParts.Question> getQuestions() {
        return questions;
    }

    public void setQuestions(List<FigureParts.Question> questions) {
        this.questions = questions;
    }

    public List<FigureParts.VerificationTarget> getVerificationTargets() {
        return verificationTargets;
    }

    public void setVerificationTargets(List<FigureParts.VerificationTarget> verificationTargets) {
        this.verificationTargets = verificationTargets;
    }

    public List<FigureParts.MathObject> getObjects() {
        return objects;
    }

    public void setObjects(List<FigureParts.MathObject> objects) {
        this.objects = objects;
    }

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands;
    }
}

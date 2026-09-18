package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * batC51-C「文章の条件から作図」の出力 DTO（{@link FigureMode#C}）。
 *
 * <p>問題文などの**文章の条件だけ**から作図する（画像は使わない）。作図の目標は利用者が選ぶ
 * （既知の条件だけを図にする／問題文が求める作図を完成させる）。**幾何の文章題だけでなく、
 * 関数・方程式の文章題も扱う**（その場合は共通の {@code 作図オブジェクト} に式を書く）。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatC51CResultDto extends FigureOutputDto {

    @Schema(description = "作図の対象になった問題文（読み取ったまま。訂正したときは訂正後の文）")
    @JsonProperty("問題文")
    @JsonAlias({"problemText"})
    private String problemText;

    @Schema(description = "文章から読み取った既知の条件")
    @JsonProperty("既知条件")
    @JsonAlias({"given"})
    private List<FigureParts.Condition> given;

    @Schema(description = "求める内容（問題文が求めているもの）")
    @JsonProperty("求める内容")
    @JsonAlias({"goals"})
    private List<String> goals;

    @Schema(description = "作図の目標（何を作ればよいか。1 文）")
    @JsonProperty("作図目標")
    @JsonAlias({"constructionGoal"})
    private String constructionGoal;

    @Schema(description = "条件が足りない点（質問と対応させる）")
    @JsonProperty("条件不足")
    @JsonAlias({"missing"})
    private List<String> missing;

    @Schema(description = "条件の矛盾（両立しない条件。勝手に片方を採らない）")
    @JsonProperty("矛盾")
    @JsonAlias({"contradictions"})
    private List<String> contradictions;

    @Schema(description = "作図に必要な手順（順番に並べる）")
    @JsonProperty("作図手順")
    @JsonAlias({"steps"})
    private List<FigureParts.Step> steps;

    public String getProblemText() {
        return problemText;
    }

    public void setProblemText(String problemText) {
        this.problemText = problemText;
    }

    public List<FigureParts.Condition> getGiven() {
        return given;
    }

    public void setGiven(List<FigureParts.Condition> given) {
        this.given = given;
    }

    public List<String> getGoals() {
        return goals;
    }

    public void setGoals(List<String> goals) {
        this.goals = goals;
    }

    public String getConstructionGoal() {
        return constructionGoal;
    }

    public void setConstructionGoal(String constructionGoal) {
        this.constructionGoal = constructionGoal;
    }

    public List<String> getMissing() {
        return missing;
    }

    public void setMissing(List<String> missing) {
        this.missing = missing;
    }

    public List<String> getContradictions() {
        return contradictions;
    }

    public void setContradictions(List<String> contradictions) {
        this.contradictions = contradictions;
    }

    public List<FigureParts.Step> getSteps() {
        return steps;
    }

    public void setSteps(List<FigureParts.Step> steps) {
        this.steps = steps;
    }
}

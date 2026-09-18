package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * batC51-D「文章と図を合わせて作図」の出力 DTO（{@link FigureMode#D}）。
 *
 * <p>文章の条件と参考図の両方を使う（図の再現、または文字による補完・変換）。**文字と図が食い違う
 * ときは黙って片方を採用せず**、{@code 衝突} に書いて確認を求める。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatC51DResultDto extends FigureOutputDto {

    @Schema(description = "文章から読み取った条件")
    @JsonProperty("文字条件")
    @JsonAlias({"textConditions"})
    private List<String> textConditions;

    @Schema(description = "図から読み取った情報")
    @JsonProperty("図の情報")
    @JsonAlias({"imageFacts"})
    private List<String> imageFacts;

    @Schema(description = "文字の条件・式と、図のオブジェクトの対応（決められないものは確度を下げる）")
    @JsonProperty("対応関係")
    @JsonAlias({"mappings"})
    private List<FigureParts.Mapping> mappings;

    @Schema(description = "残すオブジェクトの名前（利用者の指定・元の図の要素）")
    @JsonProperty("既存オブジェクト")
    @JsonAlias({"keptObjects"})
    private List<String> keptObjects;

    @Schema(description = "追加・変更・削除するオブジェクトと、その理由")
    @JsonProperty("追加変更")
    @JsonAlias({"changes"})
    private List<FigureParts.Change> changes;

    @Schema(description = "文字と図の食い違い（勝手に片方を採らない）")
    @JsonProperty("衝突")
    @JsonAlias({"conflicts"})
    private List<FigureParts.Conflict> conflicts;

    @Schema(description = "総合の作図手順（文字と図の両方を考慮した順番）")
    @JsonProperty("作図手順")
    @JsonAlias({"steps"})
    private List<FigureParts.Step> steps;

    public List<String> getTextConditions() {
        return textConditions;
    }

    public void setTextConditions(List<String> textConditions) {
        this.textConditions = textConditions;
    }

    public List<String> getImageFacts() {
        return imageFacts;
    }

    public void setImageFacts(List<String> imageFacts) {
        this.imageFacts = imageFacts;
    }

    public List<FigureParts.Mapping> getMappings() {
        return mappings;
    }

    public void setMappings(List<FigureParts.Mapping> mappings) {
        this.mappings = mappings;
    }

    public List<String> getKeptObjects() {
        return keptObjects;
    }

    public void setKeptObjects(List<String> keptObjects) {
        this.keptObjects = keptObjects;
    }

    public List<FigureParts.Change> getChanges() {
        return changes;
    }

    public void setChanges(List<FigureParts.Change> changes) {
        this.changes = changes;
    }

    public List<FigureParts.Conflict> getConflicts() {
        return conflicts;
    }

    public void setConflicts(List<FigureParts.Conflict> conflicts) {
        this.conflicts = conflicts;
    }

    public List<FigureParts.Step> getSteps() {
        return steps;
    }

    public void setSteps(List<FigureParts.Step> steps) {
        this.steps = steps;
    }
}

package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * batC51-A「画像をもとに再現」の出力 DTO（{@link FigureMode#A}）。
 *
 * <p>画像から読み取った図形・式・文章をもとに、同じ作図を作り直す。**幾何図形だけに限定されない**
 * （関数・方程式のグラフ、混在も作れる。その場合は共通の {@code 作図オブジェクト} に
 * {@link FigureParts.ObjectKind#FUNCTION} などを書く）。</p>
 *
 * <p>共通項目は {@link FigureOutputDto}（唯一の定義）。ここには A 固有の項目だけを置く。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatC51AResultDto extends FigureOutputDto {

    @Schema(description = "画像から読み取ったオブジェクト（種類・見た目・配置・ラベル）")
    @JsonProperty("画像オブジェクト")
    @JsonAlias({"imageObjects"})
    private List<FigureParts.ImageObject> imageObjects;

    @Schema(description = "原図のラベル（点の名前・式のラベル）と、作図での表示。**既定は元のまま残す**")
    @JsonProperty("ラベルと配置")
    @JsonAlias({"labels"})
    private List<FigureParts.Label> labels;

    @Schema(description = "図に見える幾何的な関係（平行・垂直・等しい長さ・中点・接するなど）")
    @JsonProperty("幾何関係")
    @JsonAlias({"relations"})
    private List<FigureParts.Relation> relations;

    @Schema(description = "曲線・座標軸・目盛（関数のグラフが写っているとき。軸や目盛の範囲も書く）")
    @JsonProperty("曲線と軸")
    @JsonAlias({"curves"})
    private List<FigureParts.Curve> curves;

    @Schema(description = "画像から読み取った既知の式・点・長さ・角（読めない値は書かない）")
    @JsonProperty("既知の式と点")
    @JsonAlias({"knownValues"})
    private List<FigureParts.KnownValue> knownValues;

    @Schema(description = "近似で置いたオブジェクトの名前（{@code 近似仮定} と対応させる）")
    @JsonProperty("近似箇所")
    @JsonAlias({"approximateObjects"})
    private List<String> approximateObjects;

    public List<FigureParts.ImageObject> getImageObjects() {
        return imageObjects;
    }

    public void setImageObjects(List<FigureParts.ImageObject> imageObjects) {
        this.imageObjects = imageObjects;
    }

    public List<FigureParts.Label> getLabels() {
        return labels;
    }

    public void setLabels(List<FigureParts.Label> labels) {
        this.labels = labels;
    }

    public List<FigureParts.Relation> getRelations() {
        return relations;
    }

    public void setRelations(List<FigureParts.Relation> relations) {
        this.relations = relations;
    }

    public List<FigureParts.Curve> getCurves() {
        return curves;
    }

    public void setCurves(List<FigureParts.Curve> curves) {
        this.curves = curves;
    }

    public List<FigureParts.KnownValue> getKnownValues() {
        return knownValues;
    }

    public void setKnownValues(List<FigureParts.KnownValue> knownValues) {
        this.knownValues = knownValues;
    }

    public List<String> getApproximateObjects() {
        return approximateObjects;
    }

    public void setApproximateObjects(List<String> approximateObjects) {
        this.approximateObjects = approximateObjects;
    }
}

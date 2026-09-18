package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * batC51-B「数式からグラフを作成」の出力 DTO（{@link FigureMode#B}）。
 *
 * <p>式・方程式・パラメータ・定義域からグラフを作る。結果種別は {@link FigureOutputType#GRAPH} に固定。
 * 曲線だけの画像からは元の式が一意に戻らないので、**式が足りないときは質問する**
 * （勝手に決めない。近似するときは {@code 近似仮定} に書く）。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatC51BResultDto extends FigureOutputDto {

    @Schema(description = "利用者が入力した元の式（書き方のまま）")
    @JsonProperty("元の式")
    @JsonAlias({"originalExpressions"})
    private List<String> originalExpressions;

    @Schema(description = "式（元の書き方と、コマンドに使う正規化した形）")
    @JsonProperty("式")
    @JsonAlias({"expressions"})
    private List<FigureParts.Expression> expressions;

    @Schema(description = "変数・パラメータ・定数（パラメータの値もここに書く）")
    @JsonProperty("変数とパラメータ")
    @JsonAlias({"symbols"})
    private List<FigureParts.Symbol> symbols;

    @Schema(description = "定義域（その式が意味を持つ範囲。**表示範囲と混ぜない**）")
    @JsonProperty("定義域")
    @JsonAlias({"domains"})
    private List<FigureParts.Domain> domains;

    @Schema(description = "画面の表示範囲（見え方。定義域ではない）")
    @JsonProperty("表示範囲")
    @JsonAlias({"view"})
    private FigureParts.ViewWindow view;

    @Schema(description = "式の読み方が 1 つに決まらない箇所（採用した読み方と理由）")
    @JsonProperty("式の曖昧さ")
    @JsonAlias({"ambiguities"})
    private List<FigureParts.Ambiguity> ambiguities;

    public List<String> getOriginalExpressions() {
        return originalExpressions;
    }

    public void setOriginalExpressions(List<String> originalExpressions) {
        this.originalExpressions = originalExpressions;
    }

    public List<FigureParts.Expression> getExpressions() {
        return expressions;
    }

    public void setExpressions(List<FigureParts.Expression> expressions) {
        this.expressions = expressions;
    }

    public List<FigureParts.Symbol> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<FigureParts.Symbol> symbols) {
        this.symbols = symbols;
    }

    public List<FigureParts.Domain> getDomains() {
        return domains;
    }

    public void setDomains(List<FigureParts.Domain> domains) {
        this.domains = domains;
    }

    public FigureParts.ViewWindow getView() {
        return view;
    }

    public void setView(FigureParts.ViewWindow view) {
        this.view = view;
    }

    public List<FigureParts.Ambiguity> getAmbiguities() {
        return ambiguities;
    }

    public void setAmbiguities(List<FigureParts.Ambiguity> ambiguities) {
        this.ambiguities = ambiguities;
    }
}

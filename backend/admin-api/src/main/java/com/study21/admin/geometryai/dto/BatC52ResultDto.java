package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * batC52（AI 画図助手）が AI から受け取るデータの DTO。
 *
 * <p><strong>このクラスが AI 出力データ構造の唯一の定義（Single Source of Truth）。</strong>
 * プロンプトへ渡す「出力形式」はこの DTO から JSON Schema を自動生成して注入し、
 * 設定ページの Data TAB も同じ生成結果を表示する。</p>
 *
 * <p>いま作図されているオブジェクトを踏まえて「追加・変更するコマンド」だけを返す。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatC52ResultDto {

    @Schema(description = "追加・変更する GeoGebra のコマンド（1 要素 = 1 コマンド。既にあるコマンドは繰り返さない）。"
            + "許可リストのコマンドだけを使う",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("コマンド")
    @JsonAlias({"commands", "Command"})
    private List<String> commands;

    @Schema(description = "何を変えたかの説明（日本語 1 文）。無ければ null")
    @JsonProperty("説明")
    @JsonAlias({"description"})
    private String description;

    public List<String> getCommands() {
        return commands;
    }

    public void setCommands(List<String> commands) {
        this.commands = commands;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}

package com.study21.admin.geometryai.dto;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * batC51（AI 生図・AI 生成）が AI から受け取るデータの DTO。
 *
 * <p><strong>このクラスが AI 出力データ構造の唯一の定義（Single Source of Truth）。</strong>
 * プロンプトへ渡す「出力形式」はこの DTO から JSON Schema を自動生成して注入し、
 * 設定ページの Data TAB も同じ生成結果を表示する。プロンプトや DB に JSON を手書きしない。</p>
 *
 * <p>JSON のキーは**今までと同じ日本語**（DB の {@code 提案JSON} と設計 §6.2 の契約に合わせる）にし、
 * 英語キーは {@link JsonAlias} で受ける。キー名の定義はこのクラス 1 か所だけに置く。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatC51ResultDto {

    @Schema(description = "AI が判定した作図の分類。画面の作図種別と同じ語彙",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("分類")
    @JsonAlias({"kind", "分類区分"})
    private FigureKind kind;

    @Schema(description = "図形の種類（分類が FIGURE のときだけ。判別できないときは OTHER）")
    @JsonProperty("図形種")
    @JsonAlias({"subKind", "sub_kind"})
    private FigureSubKind subKind;

    @Schema(description = "図形名（例: 三角形ABC）。読み取れないときは null")
    @JsonProperty("図形名")
    @JsonAlias({"title"})
    private String title;

    @Schema(description = "分類・検索用のタグ（例: [\"三角形\",\"作図\"]）。無ければ空配列")
    @JsonProperty("タグ")
    @JsonAlias({"tags"})
    private List<String> tags;

    @Schema(description = "教材としての補足メモ（1 文程度）。無ければ null")
    @JsonProperty("メモ")
    @JsonAlias({"memo"})
    private String memo;

    @Schema(description = "画像から読み取った数式・文字（例: AB=5, BC=4, ∠B=90°）。読めない値は書かない")
    @JsonProperty("認識")
    @JsonAlias({"recognized", "認識テキスト"})
    private String recognized;

    @Schema(description = "GeoGebra のコマンド（1 要素 = 1 コマンド。改行は入れない）。"
            + "許可リストのコマンドだけを使い、日本語のラベルは Text で作る",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("コマンド")
    @JsonAlias({"commands", "Command"})
    private List<String> commands;

    @Schema(description = "作図の説明（日本語 1 文）。無ければ null")
    @JsonProperty("説明")
    @JsonAlias({"description"})
    private String description;

    public FigureKind getKind() {
        return kind;
    }

    public void setKind(FigureKind kind) {
        this.kind = kind;
    }

    public FigureSubKind getSubKind() {
        return subKind;
    }

    public void setSubKind(FigureSubKind subKind) {
        this.subKind = subKind;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<String> getTags() {
        return tags;
    }

    public void setTags(List<String> tags) {
        this.tags = tags;
    }

    public String getMemo() {
        return memo;
    }

    public void setMemo(String memo) {
        this.memo = memo;
    }

    public String getRecognized() {
        return recognized;
    }

    public void setRecognized(String recognized) {
        this.recognized = recognized;
    }

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

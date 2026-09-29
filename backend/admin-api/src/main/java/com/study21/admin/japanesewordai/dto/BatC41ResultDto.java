package com.study21.admin.japanesewordai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * batC41（単語詳細 A・B 共通）の出力 DTO。
 *
 * <p>1 つの語について、学習画面（A. 勉強）が要求する詳細（語義・例文・文型・会話・類義語・
 * 間違えやすいポイント・活用・自他・発音・コロケーション・関連語・使用場面・記憶のヒント・
 * ミニ練習）を 1 つの {@link JapaneseWordAiDtos.Detail} にまとめて受け取る。</p>
 *
 * <p>A（勉強）からも B（一覧）からも同じバッチを呼ぶので、出力の形は 1 つだけ。
 * 値は AI が入力の語（見出し語・読み・教材に載っている意味）から作り、
 * {@code JapaneseDetailResponseParser} が {@code JPN_単語詳細情報.詳細JSON} へ正規化する。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatC41ResultDto {

    @Schema(description = "単語詳細（学習画面 A. 勉強 が要求する構造）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("detail")
    private JapaneseWordAiDtos.Detail detail;

    public JapaneseWordAiDtos.Detail getDetail() {
        return detail;
    }

    public void setDetail(JapaneseWordAiDtos.Detail detail) {
        this.detail = detail;
    }
}

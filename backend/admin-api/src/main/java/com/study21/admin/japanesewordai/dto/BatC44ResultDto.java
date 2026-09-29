package com.study21.admin.japanesewordai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * batC44（単語問題 E）の出力 DTO。
 *
 * <p>1 つの語について **漢字の用法を問う問題を 1 件だけ**作るので、
 * 問題は配列ではなくルートに直接置く（応答の形は 2.0 のまま）。
 * 値は AI が入力の語から作り、{@code JapaneseProblemResponseParser} が
 * {@code JPN_単語問題情報} と選択肢へ正規化する。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatC44ResultDto {

    @Schema(description = "作った問題（E_KANJI_USAGE の 1 件）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("problem")
    private JapaneseWordAiDtos.Problem problem;

    public JapaneseWordAiDtos.Problem getProblem() {
        return problem;
    }

    public void setProblem(JapaneseWordAiDtos.Problem problem) {
        this.problem = problem;
    }
}

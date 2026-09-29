package com.study21.admin.japanesewordai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/**
 * batC42（単語問題 C）の出力 DTO。
 *
 * <p>1 つの語から **2 種類の問題**（C1 表記から読みを問う / C2 読みから表記を問う）を作るので、
 * 問題の配列で受け取る。値は AI が入力の語（見出し語・読み）から作り、
 * {@code JapaneseProblemResponseParser} が {@code JPN_単語問題情報} と選択肢へ正規化する。</p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class BatC42ResultDto {

    @Schema(description = "作った問題（C1_READING と C2_KANJI の 2 件）",
            requiredMode = Schema.RequiredMode.REQUIRED)
    @JsonProperty("problems")
    private List<JapaneseWordAiDtos.Problem> problems;

    public List<JapaneseWordAiDtos.Problem> getProblems() {
        return problems;
    }

    public void setProblems(List<JapaneseWordAiDtos.Problem> problems) {
        this.problems = problems;
    }
}

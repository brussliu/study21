package com.study21.admin.geometryai;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 画像の要約（設計 §6.2）。
 *
 * <p>プロンプトの**変数の展開は 1 か所**（{@link FigurePromptTemplate}）に集めてある。
 * ここに残っているのは「画像を本文へ混ぜない」ための要約だけ（呼出履歴を base64 で太らせない）。</p>
 */
class GeometryAiPromptBuilderTest {

    @Test
    void imageSummaryNeverContainsTheImageItself() {
        String summary = GeometryAiPromptBuilder.imageSummary("AIG123-crop.png", 1536, 1024);

        assertThat(summary).isEqualTo("<image:AIG123-crop.png 1536x1024>");
        assertThat(summary).doesNotContain("base64");
    }

    @Test
    void imageSummaryFallsBackWhenTheFileNameIsMissing() {
        assertThat(GeometryAiPromptBuilder.imageSummary(null, 0, 0))
                .isEqualTo("<image:cropped.png 0x0>");
    }
}

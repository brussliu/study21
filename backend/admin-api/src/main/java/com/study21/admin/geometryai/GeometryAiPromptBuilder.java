package com.study21.admin.geometryai;

import org.springframework.stereotype.Component;

/**
 * AI 生図の**画像の要約**（呼出履歴を太らせないための 1 行）。
 *
 * <p>かつてここにプロンプトの展開（`{kind}` `{figureType}` …）も置いていたが、
 * 変数の一覧が {@link FigurePromptTemplate} と二重になり、設定ページの説明と実際に使える
 * 変数が食い違う原因になった。**展開は {@link FigurePromptTemplate} の 1 か所に集約**し、
 * ここには画像の要約だけを残す。</p>
 */
@Component
public class GeometryAiPromptBuilder {

    /** 画像の要約（**base64 は入れない**。呼出履歴を太らせないため。設計 §7）。 */
    static String imageSummary(String fileName, int width, int height) {
        return "<image:" + (fileName == null ? "cropped.png" : fileName) + " " + width + "x" + height + ">";
    }
}

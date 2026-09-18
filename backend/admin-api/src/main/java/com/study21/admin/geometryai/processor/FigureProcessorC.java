package com.study21.admin.geometryai.processor;

import com.study21.admin.geometryai.dto.FigureMode;
import org.springframework.stereotype.Component;

/**
 * batC51-C「文章の条件から作図」の処理（{@link FigureMode#C}）。
 *
 * <p>入力は**文章**（問題文・条件）。**幾何の文章題だけでなく関数・方程式の文章題も扱う**。</p>
 */
@Component
public class FigureProcessorC extends AbstractFigureProcessor {

    /** モードの役割（コードが持つ）。 */
    private static final String GUIDE = """
            【この作図の種類: C 文章の条件から作図】
            - 入力は文章（問題文・条件）です。**画像は使いません**。
            - 文章に書かれている条件だけを使い、書かれていない条件を勝手に足しません。
            - 条件が足りないときは「条件不足」、両立しないときは「矛盾」に書き、確認の質問を返します。
            - 幾何の文章題だけでなく、**関数・方程式の文章題も扱います**（作図オブジェクトに式・関数・定義域を書きます）。
            - 作図に必要な計算だけを行い、問題が求めていない問い（答えの値の計算など）には踏み込みません。
            """;

    @Override
    public FigureMode mode() {
        return FigureMode.C;
    }

    @Override
    public String modeGuide() {
        return GUIDE;
    }
}

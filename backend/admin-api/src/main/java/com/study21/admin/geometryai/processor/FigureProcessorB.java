package com.study21.admin.geometryai.processor;

import com.study21.admin.geometryai.dto.FigureMode;
import org.springframework.stereotype.Component;

/**
 * batC51-B「数式からグラフを作成」の処理（{@link FigureMode#B}）。
 *
 * <p>入力は**式・方程式・パラメータ・定義域**（画像は使わない）。結果種別は GRAPH に固定。</p>
 */
@Component
public class FigureProcessorB extends AbstractFigureProcessor {

    /** モードの役割（コードが持つ）。 */
    private static final String GUIDE = """
            【この作図の種類: B 数式からグラフを作成】
            - 入力は式・方程式・パラメータ・定義域です。**画像は使いません**（曲線の画像から式を復元することもしません）。
            - 式は「元の式」と「正規化した式」の両方を書き、コマンドには正規化した形を使います。
            - 定義域と表示範囲を**分けて**書きます（定義域 = 式が意味を持つ範囲、表示範囲 = 画面の見え方）。
            - 読み方が 1 つに決まらない記号・式は「式の曖昧さ」に書き、決められないときは質問を返します
              （判定 = NEEDS_INPUT。勝手に決めません）。
            - パラメータの値が指定されていないときは、記号のまま扱い、必要なら質問します。
            """;

    @Override
    public FigureMode mode() {
        return FigureMode.B;
    }

    @Override
    public String modeGuide() {
        return GUIDE;
    }
}

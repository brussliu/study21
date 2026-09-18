package com.study21.admin.geometryai.processor;

import com.study21.admin.geometryai.dto.FigureMode;
import org.springframework.stereotype.Component;

/**
 * batC51-D「文章と図を合わせて作図」の処理（{@link FigureMode#D}）。
 *
 * <p>入力は**文章の条件と参考図の両方**。図の再現だけでなく、文字による補完・変換も行う。</p>
 */
@Component
public class FigureProcessorD extends AbstractFigureProcessor {

    /** モードの役割（コードが持つ）。 */
    private static final String GUIDE = """
            【この作図の種類: D 文章と図を合わせて作図】
            - 入力は文章の条件と参考図の両方です。
            - 文字と図の対応を「対応関係」に書き、対応が決められないときは確度を下げて質問します。
            - 文字と図が食い違うときは「衝突」に書き、**黙って片方を採用しません**（確認の質問を返します）。
            - 利用者が指定した「残すオブジェクト」「追加・変更するオブジェクト」に従います。
            - 利用者が何も指定していないときは、元の図をそのまま複製するのが既定です。
            """;

    @Override
    public FigureMode mode() {
        return FigureMode.D;
    }

    @Override
    public String modeGuide() {
        return GUIDE;
    }
}

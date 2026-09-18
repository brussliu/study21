package com.study21.admin.geometryai.processor;

import com.study21.admin.geometryai.dto.FigureMode;
import com.study21.admin.geometryai.dto.FigureOutputDto;
import com.study21.admin.geometryai.dto.FigureOutputType;

import java.util.Optional;

/**
 * AI 生図の**作図モードごとの処理**（A〜D で独立して登録する）。
 *
 * <p>1 つのモード = 1 つのバッチ（{@code batC51-A}〜{@code batC51-D}）= 1 つの出力 DTO。
 * 違うのは**入力の読み方（役割）とプロンプト・DTO**だけで、画像の取込・モデルの呼び出し・
 * コマンド検証・タスク管理・ログ・保存は共通の部品を使う（実装は
 * {@link AbstractFigureProcessor} が持つ）。</p>
 *
 * <p>A・C・D は**幾何図形だけに限定しない**（関数・方程式のグラフ、両方の混在も作れる）。
 * 何を作るかは {@link FigureOutputType}（結果種別）が決める。</p>
 */
public interface FigureProcessor {

    /** この処理が担当するモード。 */
    FigureMode mode();

    /** バッチコード（既定はモードから決まる: batC51-A〜D）。 */
    default String taskCode() {
        return mode().taskCode();
    }

    /** このモードの出力 DTO（**DTO が唯一の定義**。プロンプトの出力形式はここから生成する）。 */
    default Class<? extends FigureOutputDto> dtoClass() {
        return mode().dtoClass();
    }

    /** モード別の system プロンプトの設定キー。 */
    String systemPromptKey();

    /** モード別のタスクテンプレートの設定キー。 */
    String taskTemplateKey();

    /**
     * モードの役割（**コードが持つ機械的な指示**）。
     *
     * <p>「入力をどう読むか」は設定のプロンプト（利用者が書く文面）とは別に、モードの意味として
     * 固定でモデルへ伝える必要がある。設定が空でも A〜D の区別が効くようにここへ置く。</p>
     */
    String modeGuide();

    /**
     * 利用者が結果種別を指定したときの指示（**黙って種類を変えない**）。
     *
     * @param requested 利用者の指定（AUTO のこともある）
     */
    default String resultTypeRule(FigureOutputType requested) {
        FigureOutputType type = requested == null ? FigureOutputType.defaultType() : requested;
        StringBuilder builder = new StringBuilder();
        builder.append("【作成する図の種類】").append(type.name()).append("（").append(type.label()).append("）\n");
        switch (type) {
            case GEOMETRY -> builder.append("- 点・線・三角形・円などの**図形だけ**を作ります。"
                    + "関数・方程式のグラフは作りません。\n");
            case GRAPH -> builder.append("- **関数・方程式のグラフ**を作ります（座標軸・目盛・定義域・表示範囲も書きます）。"
                    + "図形だけの作図はしません。\n");
            case MIXED -> builder.append("- **図形とグラフの両方**を作ります（作図オブジェクトに両方を書きます）。\n");
            case AUTO -> builder.append("- 入力の内容から実際の種類を判定し、「確定図種」に "
                    + "GEOMETRY / GRAPH / MIXED のどれかを書きます。\n");
        }
        builder.append("- 利用者が種類を指定しているときは**必ずそれに従います**。内容が合わない・作れないときは、"
                + "種類を黙って変えず「判定」を UNSUPPORTED（情報が足りないだけなら NEEDS_INPUT）にして、"
                + "説明と質問を返します。\n");
        return builder.toString();
    }

    /**
     * この処理に必要な設定（モード別のモデルパラメータは**未設定なら共通を継承**するので必須にしない）。
     */
    default Optional<String> fixedOutputTypeName() {
        return mode().fixedOutputType().map(Enum::name);
    }
}

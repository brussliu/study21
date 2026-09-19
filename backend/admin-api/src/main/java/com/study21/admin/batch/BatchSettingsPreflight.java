package com.study21.admin.batch;

import com.study21.admin.setting.SettingRequirement;

import java.util.List;

/**
 * 実行前の**設定検証**（バッチごとに差し替えられる拡張点）。
 *
 * <p>既定は「いまの設定（{@code COM_設定情報}）を検証する」。ふつうのバッチはそれでよい
 * （実行の直前に設定を読むので、設定が欠けていれば実行しない）。</p>
 *
 * <p><strong>AI 生図だけは違う</strong>: 要求の受付時に有効な設定を固定してあるので、
 * 実行の入口が「いまの設定」を見ると、**提出後に設定を消しただけで並んでいるタスクが止まる**
 * （固定した設定は完全なのに、である）。そこで AI 生図のバッチには実装を差し込み、
 * 「**その要求に固定した設定**」を検証させる（{@code FigureTaskConfigPreflight}）。
 * いまの設定を見るのは、固定が無い歴史的な要求のときだけ。</p>
 */
public interface BatchSettingsPreflight {

    /**
     * この検証が担当するバッチコードか。
     *
     * <p>担当が無ければ {@link BatchServiceImpl} が既定の検証（いまの設定）を行う。</p>
     */
    boolean supports(String batchCode);

    /**
     * 実行前の設定を検証する（問題があれば例外。実行記録は残さない）。
     *
     * @param batchCode       実行するバッチコード
     * @param requiredSettings 定義が宣言している必須設定（履歴・一覧の表示にも使う）
     * @param requestPayloadJson 実行の要求内容（AI 生図は {@code {"aiRequestId": N}}）
     */
    void verify(String batchCode, List<SettingRequirement> requiredSettings, String requestPayloadJson);
}

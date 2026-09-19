package com.study21.admin.setting;

import java.util.Map;

/**
 * 設定の保存トランザクションの**中（コミット前）**に呼ぶフック。
 *
 * <p>設定値の保存と**同じトランザクション**で書かないと困るもの（実行スケジュールの
 * 「適用時刻」と「計画バージョン」など）は、この入口を使う。</p>
 *
 * <p>なぜコミット後ではいけないか: コミット後に書くと、その間でサービスが落ちたときに
 * 「設定値は新しいが適用時刻は古い」という状態が残る。実行スケジュールはこれを
 * 「設定が変わったのに過去の計画実行点を実行してよい」と解釈してしまう（誤実行）。</p>
 *
 * <p>フックが例外を投げた場合は**保存ごとロールバック**する（どちらか片方だけ残さない）。</p>
 */
public interface SettingSaveTransactionHook {

    /**
     * 設定値の upsert が終わった直後（コミット前）に呼ぶ。
     *
     * @param event 保存で**値が変わった**設定だけを持つ（変わっていない項目は含めない）
     */
    void onSettingsSaved(SettingSaveEvent event);

    /**
     * 保存 1 回ぶんの内容。
     *
     * @param operator       操作者
     * @param changedByPage  ページ区分 → （設定キー → 保存後の値）。値が変わった項目だけ
     */
    record SettingSaveEvent(String operator, Map<String, Map<String, String>> changedByPage) {
    }
}

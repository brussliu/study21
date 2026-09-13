package com.study21.admin.batch;

import java.util.List;
import java.util.Map;

/**
 * バッチ管理サービス。
 */
public interface BatchService {

    /**
     * 全バッチタスク定義 + 有効／無効 + 設定充足状態 + 最新実行状態を返す。
     */
    Map<String, Object> listTasks();

    /**
     * 画面の【再実行】。設定検証（不足なら拒否）→ 二重起動チェック → 実行 → 履歴記録。
     *
     * <p>有効／無効に関係なく実行できる（無効は「定時実行しない」の意味）。
     * 業務処理のハンドラが未実装のバッチは拒否する。</p>
     */
    Map<String, Object> rerun(String batchCode, String operator);

    /**
     * admin-api の起動時に実行する対象（種別 S かつ有効なバッチ）のコード。
     */
    List<String> startupTargets();

    /**
     * 起動時実行。履歴には 起動種別='S'・依頼元コード='STARTUP' で残す。
     */
    Map<String, Object> runOnStartup(String batchCode);

    /**
     * 実行履歴（新しい順・ページング）。batchCode / status / keyword で絞り込める。
     */
    Map<String, Object> history(String batchCode, String status, String keyword, int page, int size);

    /**
     * AI 呼び出し履歴（新しい順・ページング）。
     * batchCode / aiType / result / keyword / startFrom / startTo で絞り込める。
     * 一覧ではプロンプト・レスポンスの本文を返さない。
     */
    Map<String, Object> aiCalls(String batchCode, String aiType, String result, String keyword,
                                String startFrom, String startTo, int page, int size);

    /**
     * AI 呼び出し履歴の 1 件（プロンプト・レスポンスの本文を含む）。
     */
    Map<String, Object> aiCallDetail(long callId);

    /**
     * AI呼出履歴画面の絞り込みに出す値（AI 区分・バッチコード・モデル名の実データ一覧）。
     */
    Map<String, Object> aiCallFilters();

    /**
     * バッチの有効／無効を切り替える（BAT_バッチコントロール情報）。
     * 切り替えられるのは batS / batL / batR のみ（C は実行のきっかけを持たない）。
     */
    Map<String, Object> updateActive(String batchCode, boolean active, String operator);
}

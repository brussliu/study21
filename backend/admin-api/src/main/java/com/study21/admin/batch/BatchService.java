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
     * 業務処理のハンドラが未実装のバッチは拒否する。種別 C（呼出）も拒否する
     * （他の処理が工程として呼ぶバッチで、画面の一覧には【再実行】ボタンを出さない。
     * 他の処理からの呼出は {@link #rerunStep}）。</p>
     */
    Map<String, Object> rerun(String batchCode, String operator);

    /**
     * 要求内容（`要求内容` 列の JSONB）を添えて 1 工程だけ実行する。
     *
     * <p>AI 生図は「1 操作 = 3 工程（batC51 → 52 → 53）」で、起動の入口（`AiFigurePipelineService`）が
     * この 1 件ずつを順に呼ぶ。`要求内容` に `{"aiRequestId": N}` を入れるので、ハンドラは
     * **どの要求を処理するか**を実行履歴から知れる（省略時は「未処理の最古の 1 件」を拾う）。</p>
     *
     * <p>`rerun` との違いは `要求内容` を渡せることと、**有効設定が OFF のバッチを拒否すること**。
     * 種別 C の有効は「いま使っているか」の目印で、OFF は「その処理を使わない」という意思表示
     * なので、呼出の入口（この入口）で止める（バッチ一覧のスイッチがそのまま効く。
     * 画面の【再実行】は 2.0 と同じく無効でも実行できる）。</p>
     */
    Map<String, Object> rerunStep(String batchCode, String operator, String requestPayloadJson);

    /**
     * 呼出（{@link #rerunStep}）や<b>受付</b>の前提: 有効設定が ON であること（OFF なら例外）。
     *
     * <p>種別 C の「有効」は「いま使っているか」の目印であり、「その処理を使わない」という
     * 意思表示でもある（バッチ一覧のスイッチをそのまま効かせる。2026-09-22 の利用者指示）。
     * 非同期の受付（例: 日本語単語の AI 取得）は {@code rerunStep} を通らないので、
     * 受付の入口が**これを呼んで**同じ規則を守る。</p>
     */
    void requireCallable(String batchCode);

    /**
     * スケジューラ用: **記録済み（待機中）の実行**を実行する（実行記録はスケジューラが作る）。
     *
     * <p>設定検証 → 二重起動チェック → 実行 → 終了の記録、の流れは {@link #rerun} と同じ。
     * 前回の実行がまだ終わっていないときは**実行せずにスキップ**として記録する。</p>
     */
    Map<String, Object> runQueued(long executionId);

    /**
     * スケジューラ用: 実行できなかった（待ち行列があふれた等）記録を失敗として閉じる。
     * 待機中のまま残すと、次の計画実行点が確保できなくなる。
     */
    void markQueuedAsFailed(long executionId, String message);

    /**
     * スケジューラ用: **実行せずにスキップ**として閉じる（実行の直前に計画が無効になった等）。
     *
     * <p>失敗ではない（業務は正しく判断して実行しなかった）ので、履歴には {@code SKIPPED} と
     * 理由を残す。待機中のまま残すと、次の計画実行点が確保できなくなる。</p>
     */
    void markQueuedAsSkipped(long executionId, String message);

    /**
     * AI 生図の要求（`要求内容` の aiRequestId）に紐づく実行履歴を古い順に返す。
     * 工程ごとの実行 ID・状態・処理時間・エラーを画面に出すために使う。
     */
    List<Map<String, Object>> executionsOfRequest(long aiRequestId);

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
     *
     * <p>切り替えられるのは batS / batL / batR と、**業務処理が実装済みの呼出（種別 C）**。
     * 未実装の C は動かしようがないので拒否する。種別 C を無効にすると、他の処理からの
     * 呼出（{@link #rerunStep}）が拒否される。</p>
     */
    Map<String, Object> updateActive(String batchCode, boolean active, String operator);
}

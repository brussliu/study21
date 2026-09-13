package com.study21.admin.batch;

/**
 * バッチタスクの業務処理ハンドラ（拡張点）。
 *
 * <p>旧 study2.0 の各タスク（BatC01 等）の業務処理を移植する際は、本インタフェースを実装し、
 * Spring のコンポーネントとして登録する（{@link BatchServiceImpl} が taskCode で引く）。
 * 設定の検証・読込は {@link BatchServiceImpl} が実行前に済ませる。</p>
 */
public interface BatchTaskHandler {

    String taskCode();

    /**
     * 業務処理を実行する。
     *
     * @param execution 実行中として記録済みの履歴行（実行ID などが入っている）
     * @return 履歴の メッセージ に残す要約（例: プロキシの起動結果）
     * @throws Exception 異常終了として記録したい失敗
     */
    String execute(BatchExecutionEntity execution) throws Exception;
}

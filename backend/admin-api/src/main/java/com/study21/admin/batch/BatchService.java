package com.study21.admin.batch;

import java.util.List;
import java.util.Map;

/**
 * バッチ管理サービス。
 */
public interface BatchService {

    /**
     * 全バッチタスク定義 + 設定充足状態 + 最新実行状態を返す。
     */
    Map<String, Object> listTasks();

    /**
     * 手動実行。設定検証（不足なら拒否）→ 並列実行チェック → 実行記録作成。
     */
    Map<String, Object> trigger(String taskCode, String requestedBy);

    /**
     * 実行履歴。
     */
    List<BatchExecutionEntity> recentExecutions(String taskCode, int limit);
}

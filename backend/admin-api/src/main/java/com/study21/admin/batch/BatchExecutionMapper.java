package com.study21.admin.batch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * BAT_バッチ実行履歴情報（バッチの実行履歴）の Mapper。
 * 有効／無効は BAT_バッチコントロール情報（{@link BatchControlMapper}）が持つ。
 */
@Mapper
public interface BatchExecutionMapper {

    int insert(BatchExecutionEntity entity);

    int markRunning(@Param("executionId") long executionId);

    /**
     * 実行の終了を記録する（状態・終了時刻・処理時間・メッセージ・エラー詳細）。
     * 処理時間は呼び出し側が測った値を入れる（ハンドラの実行時間をそのまま残す）。
     */
    int markFinished(@Param("executionId") long executionId,
                     @Param("status") String status,
                     @Param("message") String message,
                     @Param("errorDetail") String errorDetail,
                     @Param("durationMs") long durationMs);

    BatchExecutionEntity findById(@Param("executionId") long executionId);

    BatchExecutionEntity findRunningByBatchCode(@Param("batchCode") String batchCode);

    List<BatchExecutionEntity> findRecent(@Param("batchCode") String batchCode, @Param("limit") int limit);

    /**
     * バッチごとの最新 1 件（一覧画面の「最新実行」を 1 クエリで引く）。
     */
    List<BatchExecutionEntity> findLatestPerBatch();

    /** 実行履歴の件数（batchCode / status / keyword で絞り込み）。 */
    long countHistory(@Param("batchCode") String batchCode,
                      @Param("status") String status,
                      @Param("keyword") String keyword);

    /** 実行履歴の 1 ページ（新しい順）。keyword はバッチコード・メッセージ・エラー詳細を対象にする。 */
    List<BatchExecutionEntity> searchHistory(@Param("batchCode") String batchCode,
                                             @Param("status") String status,
                                             @Param("keyword") String keyword,
                                             @Param("limit") int limit,
                                             @Param("offset") int offset);

    int countByBatchCodeAndStatus(@Param("batchCode") String batchCode, @Param("status") String status);
}

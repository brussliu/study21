package com.study21.admin.batch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * BAT_バッチコントロール情報（バッチの有効／無効）の Mapper。
 *
 * 2.0 の COM_設定情報「BATCH_TASK_ENABLED_&lt;バッチコード&gt;」を置き換える。
 * バッチ定義はコード側（{@link BatchTaskRegistry}）が持つため、
 * この表は「状態・最終実行日時・備考」だけを保持する。
 */
@Mapper
public interface BatchControlMapper {

    /** 定義にあるバッチの行を用意する（既にある行は変更しない）。 */
    int insertIfAbsent(BatchControlEntity entity);

    BatchControlEntity findByBatchCode(@Param("batchCode") String batchCode);

    List<BatchControlEntity> findAll();

    /**
     * 有効／無効を切り替える。楽観的ロックのため変更前バージョンも照合する。
     * 更新できた件数（0 なら他の管理者が先に更新している）を返す。
     */
    int updateStatus(@Param("batchCode") String batchCode,
                     @Param("status") String status,
                     @Param("version") Integer version,
                     @Param("updatedByAccountId") Long updatedByAccountId,
                     @Param("updatedByCode") String updatedByCode);

    /** 実行が終わったときに最終実行日時を更新する。 */
    int touchLastRunAt(@Param("batchCode") String batchCode);
}

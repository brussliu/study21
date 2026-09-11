package com.study21.admin.batch;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * BAT_タスク実行情報 の Mapper。
 */
@Mapper
public interface BatchExecutionMapper {

    int insert(BatchExecutionEntity entity);

    int markRunning(@Param("executionId") long executionId);

    int markFinished(@Param("executionId") long executionId,
                     @Param("status") String status,
                     @Param("message") String message,
                     @Param("errorDetail") String errorDetail);

    BatchExecutionEntity findById(@Param("executionId") long executionId);

    BatchExecutionEntity findRunningByTaskCode(@Param("taskCode") String taskCode);

    List<BatchExecutionEntity> findRecent(@Param("taskCode") String taskCode, @Param("limit") int limit);

    int countByTaskCodeAndStatus(@Param("taskCode") String taskCode, @Param("status") String status);
}

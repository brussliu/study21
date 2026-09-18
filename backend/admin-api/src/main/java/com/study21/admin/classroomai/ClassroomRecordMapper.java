package com.study21.admin.classroomai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CR_授業記録情報（授業録音の 1 セッション）の Mapper（admin-api 側）。
 *
 * <p>batC62 が最終まとめを書き、batR02 が保持期限切れの音声を掃除する。</p>
 */
@Mapper
public interface ClassroomRecordMapper {

    ClassroomRecordEntity findById(@Param("recordId") long recordId);

    /** 最終まとめを書き、状態を COMPLETED にする（batC62）。 */
    int updateSummaryCompleted(@Param("recordId") long recordId, @Param("summaryJson") String summaryJson);

    /**
     * 保持期限を過ぎた音声を持つ行（batR02 のクリーンアップ）。
     * **行は残し、音声ファイルだけ消す**（記録の履歴は監査として残す）。
     */
    List<ClassroomRecordEntity> findRetentionTargets(@Param("retentionDays") int retentionDays,
                                                     @Param("limit") int limit);

    /** 音声の参照を消す（ファイルは呼び出し側が消す）。 */
    int clearAudioFiles(@Param("recordId") long recordId);
}

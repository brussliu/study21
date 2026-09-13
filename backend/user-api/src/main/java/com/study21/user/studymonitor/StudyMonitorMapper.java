package com.study21.user.studymonitor;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学習状況モニター（MON_学習モニター*）の Mapper。
 *
 * <p>取り込み（batL02）と AI 分析（batL03）は 2.1 では未実装。ここは画面の照会と
 * 手動修正だけを持つ。</p>
 */
@Mapper
public interface StudyMonitorMapper {

    /** 対象日の動画（切出枚数・分析済み枚数つき）。 */
    List<StudyMonitorModels.VideoRow> searchVideos(@Param("from") LocalDateTime from,
                                                   @Param("to") LocalDateTime to,
                                                   @Param("videoId") Long videoId);

    /**
     * 対象日のスナップショット（最新の分析つき）。
     * 時間帯・AI分析の状態・分析結果で絞る（null は絞らない）。
     */
    List<StudyMonitorModels.SnapshotRow> searchSnapshots(@Param("from") LocalDateTime from,
                                                         @Param("to") LocalDateTime to,
                                                         @Param("videoId") Long videoId,
                                                         @Param("analysisState") String analysisState,
                                                         @Param("resultCode") String resultCode);

    /** 1 枚のスナップショット（画像を開くときに使う）。 */
    StudyMonitorModels.SnapshotRow findSnapshot(@Param("snapshotId") long snapshotId);

    /** 手動修正を 1 件書き込む（版が合わなければ 0 件）。 */
    int updateManualAnalysis(@Param("snapshotId") long snapshotId,
                             @Param("version") int version,
                             @Param("resultCode") String resultCode,
                             @Param("reason") String reason,
                             @Param("accountId") Long accountId);
}

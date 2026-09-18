package com.study21.user.classroom;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CR_授業転写セグメント情報（転写のセグメント行）の Mapper（user-api 側）。
 */
@Mapper
public interface ClassroomSegmentMapper {

    int insert(ClassroomSegmentEntity entity);

    /**
     * 同じ発話（発話キー）の行。無ければ null。
     *
     * <p>再送・再保存を **INSERT ではなく UPDATE** に寄せるために使う
     * （認識セッションを作り直すと連番は 1 から振り直されるので、INSERT だと一意制約に衝突する）。</p>
     */
    ClassroomSegmentEntity findByUtteranceKey(@Param("recordId") long recordId,
                                              @Param("utteranceKey") String utteranceKey);

    /** 同じ発話の文と時刻を書き換える（認識し直した・確定した文で置き換える）。 */
    int updateByUtterance(@Param("segmentId") long segmentId,
                          @Param("startOffsetSeconds") java.math.BigDecimal startOffsetSeconds,
                          @Param("endOffsetSeconds") java.math.BigDecimal endOffsetSeconds,
                          @Param("text") String text,
                          @Param("language") String language);

    /** その授業記録の最大連番（チャンクの冪等判定に使う。無ければ null）。 */
    Integer maxSeq(@Param("recordId") long recordId);

    /** 指定連番より後のセグメント（ポーリング用。連番順）。 */
    List<ClassroomSegmentEntity> findByRecordAfter(@Param("recordId") long recordId,
                                                   @Param("afterSeq") int afterSeq);

    /** その授業記録の全セグメント（詳細用。連番順）。 */
    List<ClassroomSegmentEntity> findByRecord(@Param("recordId") long recordId);
}

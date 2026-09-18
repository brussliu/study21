package com.study21.admin.classroomai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CR_授業転写セグメント情報（転写のセグメント行）の Mapper（admin-api 側）。
 *
 * <p>batC61 / batC62 がノートを生成するときに、対象範囲の転写を連番順に読み取る。</p>
 */
@Mapper
public interface ClassroomSegmentMapper {

    /** 対象範囲（開始連番〜終了連番）の転写本文を連番順に返す。 */
    List<ClassroomSegmentEntity> findTextsByRange(@Param("recordId") long recordId,
                                                  @Param("startSeq") int startSeq,
                                                  @Param("endSeq") int endSeq);
}

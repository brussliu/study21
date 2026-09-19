package com.study21.admin.classroomai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * CR_授業録音分塊情報（録音の分塊）の Mapper（admin-api 側）。
 *
 * <p>batR02 の掃除（保持期限切れ）で、**音声の実体を消したあとにその行も消す**ために使う。
 * 行を残すと「保存済み」に見えるため、再送の冪等判定（user-api）が実体の無い分塊を
 * 「もう在る」と判断してしまい、遅れて届いた分塊の音声が保存されない。</p>
 */
@Mapper
public interface ClassroomRecordingChunkMapper {

    /** その記録の分塊の行を全部消す。 */
    int deleteByRecord(@Param("recordId") long recordId);
}

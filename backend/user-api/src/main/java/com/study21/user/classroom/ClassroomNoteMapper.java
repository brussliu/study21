package com.study21.user.classroom;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CR_授業ノート情報（フェーズノート + 最終まとめ）の Mapper（user-api 側）。
 *
 * <p>user-api は「トリガー成立で PENDING 行を作る」「終了時に FINAL の PENDING 行を作る」
 * 「詳細でノート一覧を返す」だけ。PENDING 行の生成（batC61/batC62）と状態遷移は admin-api の
 * Mapper が行う。</p>
 */
@Mapper
public interface ClassroomNoteMapper {

    int insert(ClassroomNoteEntity entity);

    /** 直近のフェーズノート（コールダウン・間隔判定・フェーズ番号の起点に使う。無ければ null）。 */
    ClassroomNoteEntity findLastPhase(@Param("recordId") long recordId);

    /** その授業記録のノート一覧（詳細用。種別・フェーズ番号順）。 */
    List<ClassroomNoteEntity> findByRecord(@Param("recordId") long recordId);
}

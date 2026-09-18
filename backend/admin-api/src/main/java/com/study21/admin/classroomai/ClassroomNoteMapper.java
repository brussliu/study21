package com.study21.admin.classroomai;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * CR_授業ノート情報（フェーズノート + 最終まとめ）の Mapper（admin-api 側）。
 *
 * <p>batC61（PHASE）/ batC62（FINAL）が**生成状態=PENDING の行をキューとして**拾い、
 * 生成状態を遷移させる。更新は楽観的ロック（バージョン）を伴う。</p>
 */
@Mapper
public interface ClassroomNoteMapper {

    ClassroomNoteEntity findById(@Param("noteId") long noteId);

    /** batC61 が拾う 1 件（PENDING / GENERATING の PHASE の最古）。 */
    ClassroomNoteEntity findPhaseTarget();

    /** batC62 が拾う 1 件（PENDING / GENERATING の FINAL の最古）。 */
    ClassroomNoteEntity findFinalTarget();

    /** AI へ送る前に「生成中」を確定する（再実行時に「前回は呼び出し中に落ちた」と分かる）。 */
    int markGenerating(@Param("noteId") long noteId,
                       @Param("executionId") Long executionId,
                       @Param("version") int version);

    /** AI の結果を書く（生成状態=READY、ノートJSON、呼出履歴ID）。 */
    int updateReady(ClassroomNoteEntity entity);

    /** 失敗を書く（生成状態=FAILED、エラーコード、エラーメッセージ）。 */
    int updateFailed(ClassroomNoteEntity entity);
}

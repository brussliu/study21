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

    /**
     * 生成の**起動を受理する**（`PENDING`／`FAILED` → `GENERATING` を 1 文で）。
     *
     * <p>0 行なら「別の要求が既に始めている・すでに終わっている」。画面のボタンやメモリの
     * 真偽値ではなく、**この条件つき更新**で二重起動を防ぐ（同時に 2 本来ても勝つのは 1 本）。</p>
     *
     * @return 1 = 受理した / 0 = 受理しなかった（既に走っている・終わっている）
     */
    int claimGeneration(@Param("noteId") long noteId);

    /**
     * **前に走らせたまま落ちた**行をやり直す（前回の開始から一定時間たった `GENERATING` だけ）。
     *
     * <p>プロセスが落ちると `GENERATING` のまま残る。それを「生成中」と見せ続けると画面は
     * 永久に待つ。一定時間たったものだけを**もう一度受理**して、やり直せるようにする。</p>
     *
     * @param staleBefore これより前に始まったものを「落ちた」とみなす時刻
     * @return 1 = 受理した / 0 = まだ新しい（本当に走っている）
     */
    int reclaimStaleGeneration(@Param("noteId") long noteId,
                               @Param("staleBefore") java.sql.Timestamp staleBefore);

    /** AI へ送る前に「生成中」を確定する（再実行時に「前回は呼び出し中に落ちた」と分かる）。 */
    int markGenerating(@Param("noteId") long noteId,
                       @Param("executionId") Long executionId,
                       @Param("version") int version);

    /** AI の結果を書く（生成状態=READY、ノートJSON、呼出履歴ID）。 */
    int updateReady(ClassroomNoteEntity entity);

    /** 失敗を書く（生成状態=FAILED、エラーコード、エラーメッセージ）。 */
    int updateFailed(ClassroomNoteEntity entity);
}

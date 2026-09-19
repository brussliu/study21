package com.study21.user.classroom;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * CR_授業記録情報（授業録音の 1 セッション）の Mapper（user-api 側）。
 *
 * <p>画面向けの入口が作る／読む／終了する／消す行だけを扱う。ノートの生成（batC61/batC62）と
 * 最終まとめの書き込みは admin-api の Mapper が行う（サービス間で API を呼ばず、
 * DB の状態列だけで橋渡しする。docs/ARCHITECTURE.md §3）。</p>
 */
@Mapper
public interface ClassroomRecordMapper {

    int insert(ClassroomRecordEntity entity);

    ClassroomRecordEntity findById(@Param("recordId") long recordId);

    /**
     * 授業記録の行を**排他で押さえて**読む（`SELECT ... FOR UPDATE`）。
     *
     * <p>分塊の受け入れ（{@code uploadChunk}）と収尾（{@code end}）は**同じ記録の行**を
     * 押さえてから状態を読み直す。押さえる前の状態で判断すると、次の競合が起きる:</p>
     * <ol>
     *   <li>収尾が状態を {@code TRANSCRIBING} にして、収尾の処理を進める。</li>
     *   <li>同時に走っていたアップロードが**押さえる前の状態（RECORDING）**を根拠に通り、
     *       収尾のあとに分塊の行を足す（確定した範囲が動く）。</li>
     * </ol>
     *
     * <p>行を押さえれば、収尾の更新はアップロードのコミットを待ち、アップロードは収尾の
     * コミット後に**新しい状態**を読む（どちらか一方が必ず後になる）。長い処理（AI 解析・
     * FFmpeg）は**このトランザクションの中で走らせない**（押さえたままにしない）。</p>
     *
     * <p><b>トランザクションの中からだけ呼ぶ</b>（外で呼ぶと 1 文でコミットされ、押さえても
     * 意味が無い）。</p>
     */
    ClassroomRecordEntity lockById(@Param("recordId") long recordId);

    ClassroomRecordEntity findByNo(@Param("recordNo") String recordNo);

    /** 履歴の件数。owner（学生）・familyId（保護者）のどちらかで絞る（管理者は両方 null = 全件）。 */
    long count(@Param("owner") Long owner, @Param("familyId") Long familyId, @Param("status") String status);

    /** 履歴の 1 ページ（新しい順）。 */
    List<ClassroomRecordEntity> search(@Param("owner") Long owner,
                                       @Param("familyId") Long familyId,
                                       @Param("status") String status,
                                       @Param("limit") int limit,
                                       @Param("offset") int offset);

    /** 1 アカウントの今日の作成件数（日次上限の判定）。 */
    long countTodayByAccount(@Param("accountId") long accountId);

    /** 録音開始（状態=RECORDING・開始時刻=現在）。楽観的ロック。 */
    int updateStarted(@Param("recordId") long recordId,
                      @Param("operator") Long operator,
                      @Param("version") int version);

    /**
     * 収尾（終了処理）の**鍵を取る**: 状態を {@code TRANSCRIBING}（収尾中）にする。
     *
     * <p>`状態 = RECORDING` のときだけ 1 行を更新する（**原子的な確保**）。0 行なら
     * 「別の要求が先に収尾を始めた・すでに終わっている」なので、呼び側は 409 で断る。
     * これで「確認したあとに未調整のアップロードが入る」競合を作らない
     * （状態そのものが排他になる）。</p>
     *
     * @return 1 = 確保できた / 0 = 取れなかった
     */
    int claimFinalize(@Param("recordId") long recordId, @Param("operator") long operator);

    /**
     * 収尾（終了処理）を**完了**させる: 状態を {@code STOPPED} にして長さ・保持期限を書く。
     *
     * <p>確保（{@link #claimFinalize}）でバージョンが進むので、**そのバージョン**を渡す
     * （収尾のあいだに別の操作が入っていないことを確かめる）。</p>
     */
    int markFinalized(@Param("recordId") long recordId,
                      @Param("durationSeconds") int durationSeconds,
                      @Param("retentionDays") int retentionDays,
                      @Param("operator") long operator,
                      @Param("version") int version,
                      /** 収尾で確定した「実際に録れた」最後の分塊の連番（旧い画面は 0）。 */
                      @Param("recordedLastSeq") Integer recordedLastSeq,
                      /** 収尾で確定した「録れた分塊の数」。 */
                      @Param("recordedCount") Integer recordedCount,
                      /** 収尾で確定した録音の終わりの位置（統一時間軸。16kHz のサンプル数）。 */
                      @Param("recordedEndSample") Long recordedEndSample,
                      /** 音声が全部そろっていると確認できたか。 */
                      @Param("recordedComplete") Boolean recordedComplete,
                      /** 不完全なまま終えたときに失った連番（カンマ区切り。無ければ null）。 */
                      @Param("lostSeqs") String lostSeqs);

    /**
     * 結合（再生用の 1 本）の状態を書く（**再起動しても読めるように DB に残す**）。
     *
     * <p>{@code state} が {@code PROCESSING} のときは開始時刻を入れ、{@code READY}／
     * {@code FAILED}／{@code INCOMPLETE} のときは終了時刻を入れる。</p>
     */
    int updateAssemblyState(@Param("recordId") long recordId,
                            @Param("state") String state,
                            @Param("digest") String digest,
                            @Param("durationSeconds") java.math.BigDecimal durationSeconds,
                            @Param("reason") String reason);

    /** 最初の分塊で音声ファイルの保存先を確定する。 */
    int updateAudio(@Param("recordId") long recordId,
                    @Param("path") String path,
                    @Param("name") String name,
                    @Param("mime") String mime,
                    @Param("size") long size,
                    @Param("operator") Long operator);

    /** 転写済み文字数を加算する（トリガー判定の累積カウンタ）。 */
    int addTranscribedChars(@Param("recordId") long recordId, @Param("chars") int chars);

    /** 終了（状態=STOPPED・終了時刻・録音時間・保持期限）。楽観的ロック。 */
    int updateEnded(@Param("recordId") long recordId,
                    @Param("durationSeconds") int durationSeconds,
                    @Param("retentionDays") int retentionDays,
                    @Param("operator") Long operator,
                    @Param("version") int version);

    /** 削除（所有者のみ。FK CASCADE で転写・ノートも消える）。 */
    int delete(@Param("recordId") long recordId, @Param("operator") Long operator);
}

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

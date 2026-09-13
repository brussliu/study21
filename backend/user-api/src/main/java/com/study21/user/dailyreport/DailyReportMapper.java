package com.study21.user.dailyreport;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * TRN_学習日報情報 / 授業情報 / 科目情報 の Mapper（閲覧用）。
 */
@Mapper
public interface DailyReportMapper {

    /** 期間内の記載・提出状況（記録がある日だけ返る）。 */
    List<DailyReportModels.DayStatusRow> findStatus(@Param("accountId") long accountId,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /** 曜日×時限ごとの教科（最も多い教科を 1 行で返す）。 */
    List<DailyReportModels.TimetableCell> findTimetable(@Param("accountId") long accountId);

    /** その日の日報ヘッダ（無ければ null）。 */
    DailyReportEntity findReport(@Param("accountId") long accountId, @Param("date") LocalDate date);

    List<DailyReportModels.DayLesson> findLessons(@Param("reportId") long reportId);

    List<DailyReportModels.DaySubject> findSubjects(@Param("reportId") long reportId);

    /** 期間内の授業（日報がある日だけ。日付で引くために日報と結合する）。 */
    List<DailyReportModels.WeekLessonRow> findLessonsBetween(@Param("accountId") long accountId,
                                                             @Param("from") java.time.LocalDate from,
                                                             @Param("to") java.time.LocalDate to);

    /** 日報が無ければ作る（1 限ぶんの入力から日報を作るとき）。 */
    int insertReport(@Param("accountId") long accountId, @Param("date") java.time.LocalDate date);

    /** 1 限ぶんの授業を登録または更新する（日報ID＋時限で一意）。 */
    int upsertLesson(@Param("reportId") long reportId,
                     @Param("period") int period,
                     @Param("subject") String subject,
                     @Param("content") String content,
                     @Param("mastery") Integer mastery,
                     @Param("noteRecorded") boolean noteRecorded,
                     @Param("noteSkippedReason") String noteSkippedReason,
                     @Param("concentration") Integer concentration,
                     @Param("studyVolume") Integer studyVolume,
                     @Param("attitude") Integer attitude,
                     @Param("accountId") long accountId);

    /** 期間内の日報ヘッダ（週表示で、その日のまとめと提出状態を出すのに使う）。 */
    List<DailyReportEntity> findReportsBetween(@Param("accountId") long accountId,
                                              @Param("from") LocalDate from,
                                              @Param("to") LocalDate to);

    /** その日の授業数（提出できるかの判定に使う）。 */
    int countLessons(@Param("accountId") long accountId, @Param("date") LocalDate date);

    /** 提出する（提出状態を SUBMITTED にし、提出日時を記録する）。 */
    int submit(@Param("accountId") long accountId, @Param("date") LocalDate date);

    /**
     * 提出済の日報を下書きに戻す（提出後に編集したとき＝再提出待ち）。
     * 提出日時は残す（「提出したことがある」ことを画面で示すため）。
     */
    int reopenIfSubmitted(@Param("accountId") long accountId, @Param("date") LocalDate date);

    /** 1 日のまとめ（休日区分・振り返り・宿題）を更新する。 */
    int updateDaySummary(@Param("reportId") long reportId,
                         @Param("holidayType") String holidayType,
                         @Param("review") String review,
                         @Param("homework") String homework,
                         @Param("accountId") long accountId);

    /** その日の授業を全部消す（祝日・休日にしたとき）。 */
    int deleteLessons(@Param("reportId") long reportId);

    /** 1 限ぶんの授業を消す（消した時限は詰めない）。 */
    int deleteLesson(@Param("reportId") long reportId, @Param("period") int period);

    /** 授業を保存したとき、その日を「通常」に戻す（祝日・休日の指定を解除する）。 */
    int resetHolidayType(@Param("accountId") long accountId, @Param("date") LocalDate date);
}

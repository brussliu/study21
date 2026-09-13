package com.study21.user.dailyreport;

import java.time.LocalDate;

/**
 * 学習日報（本人の分だけ）。
 *
 * 閲覧（カレンダー・週表示・1 日の詳細）と、1 限ぶんの記録の保存・提出を行う。
 * 記載あり（DRAFT）と提出済（SUBMITTED）を分けて持ち、提出後に編集すると下書きに戻る
 * （＝再提出待ち）。
 */
public interface DailyReportService {

    /** 期間内の提出状況（カレンダー・週表示の素）。 */
    DailyReportModels.StatusResult status(long accountId, LocalDate from, LocalDate to);

    /** 記録から推定した週の時間割（曜日×時限ごとの教科）。 */
    DailyReportModels.TimetableResult timetable(long accountId);

    /** 1 日の詳細（日報が無い日は hasReport=false）。 */
    DailyReportModels.DayDetail day(long accountId, LocalDate date);

    /**
     * 週表示（月〜金。記録がある土日も足す）。記録が無い日は、過去の日報から推定した
     * 時間割で埋める（recorded=false）。
     */
    DailyReportModels.WeekResult week(long accountId, LocalDate anyDateInWeek);

    /** 1 限ぶんの日報を保存する（日報が無ければ作る）。提出済の日は下書きに戻す。 */
    DailyReportModels.LessonSaveResult saveLesson(long accountId, DailyReportModels.LessonSaveRequest request);

    /**
     * その日を提出する（提出済にする）。
     * 記録が 1 件も無い日は提出できない。すでに提出済みなら 400。
     */
    DailyReportModels.SubmitResult submit(long accountId, LocalDate date);

    /**
     * 1 限ぶんの授業を消す（消した時限は詰めない＝空いたままにする）。
     * 提出済の日を編集したときと同じく、下書き（再提出待ち）に戻す。
     */
    DailyReportModels.LessonDeleteResult deleteLesson(long accountId, LocalDate date, int period);

    /**
     * 1 日のまとめ（祝日/休日区分・今日の振り返り・今夜の勉強内容）を保存する。
     * 祝日・休日にしたときは、その日の授業の記録を全部消す。
     */
    DailyReportModels.DaySummaryResult saveDaySummary(long accountId,
                                                     DailyReportModels.DaySummarySaveRequest request);
}

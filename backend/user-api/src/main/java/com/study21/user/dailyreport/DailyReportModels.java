package com.study21.user.dailyreport;

import java.time.LocalDate;
import java.util.List;

/**
 * 学習日報のモデル（閲覧用）。
 *
 * 画面（`views/daily-report/DailyReportView.vue`）は 3 つの見方で使う:
 *   * 月カレンダー … どの日に日報があるか（{@link DayStatus}）
 *   * 週の時間割   … 曜日×時限ごとの教科と出現状況（{@link TimetableCell}）
 *   * 1 日の詳細   … 授業ごとの記録と 4 観点の自己評価（{@link DayDetail}）
 */
public final class DailyReportModels {

    /** 期間内の授業 1 行（Mapper の戻り値。日付つき）。 */
    public record WeekLessonRow(
            LocalDate date,
            int period,
            String subject,
            String content,
            Integer mastery,
            boolean noteRecorded,
            String noteSkippedReason,
            Integer concentration,
            Integer studyVolume,
            Integer attitude) {
    }

    private DailyReportModels() {
    }

    /** 提出状態。DRAFT=記載あり（未提出・再提出待ち）/ SUBMITTED=提出済。 */
    public static final List<String> SUBMIT_STATUSES = List.of("DRAFT", "SUBMITTED");

    /**
     * その日の区分。NORMAL=通常 / HOLIDAY=祝日 / REST=休日。
     * 祝日・休日にするとその日の授業の記録は消え、未提出にも提出率にも数えない。
     */
    public static final List<String> HOLIDAY_TYPES = List.of("NORMAL", "HOLIDAY", "REST");

    /**
     * カレンダー 1 マスの集計行（Mapper の戻り値）。
     * 教科は SQL では CSV で受け取り、{@link DayStatus#subjects()} で配列にする。
     */
    public record DayStatusRow(
            LocalDate date,
            int weekday,
            int lessonCount,
            Double averageMastery,
            int noteCount,
            String submitStatus,
            boolean submitted,
            java.time.LocalDateTime submittedAt,
            String subjectsCsv,
            int detailedLessonCount,
            String holidayType,
            String review,
            String homework) {
    }

    /** ある日の記載・提出状況（カレンダー 1 マス）。 */
    public record DayStatus(
            LocalDate date,
            /** 1=月 … 7=日 */
            int weekday,
            boolean hasReport,
            int lessonCount,
            /** 掌握度の平均（未入力のみの日は null） */
            Double averageMastery,
            int noteCount,
            /** DRAFT / SUBMITTED */
            String submitStatus,
            /** 提出済か（submitStatus == SUBMITTED） */
            boolean submitted,
            /** 最後に提出した日時（未提出なら null） */
            java.time.LocalDateTime submittedAt,
            /** その日に記録した教科（重複なし・時限順。カレンダーの色分けに使う） */
            List<String> subjects,
            /** その日の合計時限数に対する、内容か掌握度が入っている授業の数 */
            int detailedLessonCount,
            /** NORMAL / HOLIDAY / REST */
            String holidayType,
            /** その日の振り返り（未入力なら null。未展開のマスにも出す） */
            String review,
            /** 今夜の勉強内容（未入力なら null。未展開のマスにも出す） */
            String homework) {

        /** 提出済みか（画面の分岐を 1 か所にまとめる）。 */
        public boolean isSubmitted() {
            return "SUBMITTED".equals(submitStatus);
        }

        /** 祝日・休日か（未提出にも提出率にも数えない）。 */
        public boolean isRestDay() {
            return "HOLIDAY".equals(holidayType) || "REST".equals(holidayType);
        }

        /** 提出したことがあるが、そのあと編集して下書きに戻った（再提出待ち）。 */
        public boolean isReopened() {
            return !isSubmitted() && submittedAt != null;
        }
    }

    /** 期間内の提出状況。schoolDays は平日の数（祝日は考慮しない）。 */
    public record StatusResult(
            LocalDate from,
            LocalDate to,
            List<DayStatus> days,
            int schoolDays,
            /** 記録がある日（提出前の下書きを含む） */
            int reportDays,
            /** 提出済の日（月の提出率はこれで数える） */
            int submittedDays,
            /** 提出したことがあるが編集で下書きに戻った日 */
            int reopenedDays,
            /** 祝日・休日として指定された日（提出率の分母から外す） */
            int restDays) {
    }

    /** 週の時間割の 1 マス（曜日×時限）。subject は最も多く記録された教科。 */
    public record TimetableCell(
            int weekday,
            int period,
            String subject,
            /** その教科が記録された回数 */
            int appearances,
            /** その曜日・時限に記録があった日数 */
            int days) {
    }

    public record TimetableResult(
            List<Integer> weekdays,
            List<Integer> periods,
            List<TimetableCell> cells) {
    }

    /** 1 日の授業。 */
    public record DayLesson(
            int period,
            String subject,
            String content,
            Integer mastery,
            boolean noteRecorded,
            String noteSkippedReason,
            Integer concentration,
            Integer studyVolume,
            Integer attitude) {
    }

    /** 1 日の科目ごとの学習内容（2.0 の 科目情報）。 */
    public record DaySubject(
            int order,
            String subject,
            String content) {
    }


    /** 週表示の 1 限（記録が無い日は推定で埋める）。 */
    public record WeekLesson(
            int period,
            String subject,
            String content,
            Integer mastery,
            boolean noteRecorded,
            String noteSkippedReason,
            Integer concentration,
            Integer studyVolume,
            Integer attitude,
            /** 記録済みか（false = 過去の日報から推定しただけ） */
            boolean recorded) {
    }

    /**
     * 週表示の 1 日。カレンダーのマスを開いたときの表示に使うため、その日の
     * まとめ（4 観点・振り返り・宿題）と提出状態も返す。
     */
    public record WeekDay(
            LocalDate date,
            int weekday,
            boolean hasReport,
            /** DRAFT / SUBMITTED（記録が無い日は null） */
            String submitStatus,
            boolean submitted,
            /** 最後に提出した日時（未提出なら null） */
            java.time.LocalDateTime submittedAt,
            String review,
            Integer concentration,
            Integer understanding,
            Integer studyVolume,
            Integer attitude,
            String homework,
            /** NORMAL / HOLIDAY / REST */
            String holidayType,
            /** 記録した教科（重複なし・時限順） */
            List<String> subjects,
            List<WeekLesson> lessons) {
    }

    public record WeekResult(LocalDate from, LocalDate to, List<WeekDay> days) {
    }

    /** 1 限ぶんの日報入力（授業をクリックして開くダイアログから保存する）。 */
    public record LessonSaveRequest(
            @jakarta.validation.constraints.NotNull(message = "日付を指定してください。") LocalDate date,
            @jakarta.validation.constraints.Min(value = 1, message = "時限を指定してください。") int period,
            @jakarta.validation.constraints.NotBlank(message = "教科名を入力してください。")
            @jakarta.validation.constraints.Size(max = 50, message = "教科名は50文字以内で入力してください。") String subject,
            @jakarta.validation.constraints.Size(max = 2000, message = "授業内容は2000文字以内で入力してください。") String content,
            Integer mastery,
            /** ノートを記録したか（画面はラジオ。主科だけ選べる） */
            Boolean noteRecorded,
            /** ノートを記録しなかった理由（noteRecorded=false のときだけ） */
            @jakarta.validation.constraints.Size(max = 200, message = "理由は200文字以内で入力してください。")
            String noteSkippedReason,
            /** この授業の自己評価（1〜5） */
            Integer concentration,
            Integer studyVolume,
            Integer attitude) {
    }

    /** 授業を 1 つ消した結果。 */
    public record LessonDeleteResult(String message, LocalDate date, int period, boolean reopened) {
    }

    /** 1 日のまとめ（日まとめのダイアログから保存する）。 */
    public record DaySummarySaveRequest(
            @jakarta.validation.constraints.NotNull(message = "日付を指定してください。") LocalDate date,
            /** NORMAL / HOLIDAY / REST */
            String holidayType,
            /** 今日の振り返り */
            @jakarta.validation.constraints.Size(max = 2000, message = "振り返りは2000文字以内で入力してください。")
            String review,
            /** 今夜の勉強内容 */
            @jakarta.validation.constraints.Size(max = 2000, message = "今夜の勉強内容は2000文字以内で入力してください。")
            String homework) {
    }

    /** 日まとめの保存結果。祝日・休日にしたときは授業を消した件数を返す。 */
    public record DaySummaryResult(
            String message,
            LocalDate date,
            String holidayType,
            /** 消した授業の件数（祝日・休日にしたときだけ 1 以上） */
            int clearedLessons) {
    }

    public record LessonSaveResult(String message, LocalDate date, int period, boolean created,
                                   /** 保存で下書きに戻った（＝再提出待ち）か */
                                   boolean reopened) {
    }

    /** 提出の結果。 */
    public record SubmitResult(
            String message,
            LocalDate date,
            int lessonCount,
            java.time.LocalDateTime submittedAt) {
    }

    /** 1 日の詳細。日報が無い日は hasReport=false で他は空。 */
    public record DayDetail(
            LocalDate date,
            boolean hasReport,
            /** DRAFT / SUBMITTED（記録が無い日は null） */
            String submitStatus,
            boolean submitted,
            java.time.LocalDateTime submittedAt,
            /** NORMAL / HOLIDAY / REST（記録が無い日は NORMAL） */
            String holidayType,
            String review,
            Integer concentration,
            Integer understanding,
            Integer studyVolume,
            Integer attitude,
            String homework,
            String note,
            List<DayLesson> lessons,
            List<DaySubject> subjects) {
    }
}

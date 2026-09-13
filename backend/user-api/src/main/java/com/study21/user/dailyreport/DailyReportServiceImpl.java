package com.study21.user.dailyreport;

import com.study21.common.core.exception.ValidationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * 学習日報の閲覧実装。
 *
 * 「その日の日報があるか」「週の時間割がどう埋まっているか」を、記録済みの
 * 学習日報（2.0 から移行した分を含む）から組み立てて返す。
 */
@Service
public class DailyReportServiceImpl implements DailyReportService {

    /** 期間指定の上限（1 年を超える要求は誤操作とみなす）。 */
    private static final int MAX_RANGE_DAYS = 366;

    private final DailyReportMapper dailyReportMapper;

    public DailyReportServiceImpl(DailyReportMapper dailyReportMapper) {
        this.dailyReportMapper = dailyReportMapper;
    }

    @Override
    public DailyReportModels.StatusResult status(long accountId, LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new ValidationException("期間（from / to）を指定してください。");
        }
        if (to.isBefore(from)) {
            throw new ValidationException("期間の指定が逆です（from が to より後になっています）。");
        }
        if (from.plusDays(MAX_RANGE_DAYS).isBefore(to)) {
            throw new ValidationException("期間は 1 年以内で指定してください。");
        }

        List<DailyReportModels.DayStatus> reported = new ArrayList<>();
        int submittedDays = 0;
        int reopenedDays = 0;
        int restDays = 0;
        for (DailyReportModels.DayStatusRow row : dailyReportMapper.findStatus(accountId, from, to)) {
            DailyReportModels.DayStatus day = toDayStatus(row);
            if (day.isSubmitted()) {
                submittedDays += 1;
            } else if (day.isReopened()) {
                reopenedDays += 1;
            }
            if (day.isRestDay()) {
                // 祝日・休日は未提出にも提出率の分母にも数えない
                restDays += 1;
            }
            reported.add(day);
        }
        // 行がある日 = 記録あり（カレンダー側は「行が無い = 未提出」として扱う）
        int schoolDays = schoolDays(from, to) - restDays;
        return new DailyReportModels.StatusResult(from, to, reported, Math.max(0, schoolDays),
                reported.size(), submittedDays, reopenedDays, restDays);
    }

    /** 集計行を画面に返す形へ（教科の CSV を配列にする）。 */
    private static DailyReportModels.DayStatus toDayStatus(DailyReportModels.DayStatusRow row) {
        return new DailyReportModels.DayStatus(
                row.date(), row.weekday(), true, row.lessonCount(), row.averageMastery(),
                row.noteCount(), row.submitStatus(), row.submitted(), row.submittedAt(),
                splitSubjects(row.subjectsCsv()), row.detailedLessonCount(),
                row.holidayType() == null ? "NORMAL" : row.holidayType(),
                row.review(), row.homework());
    }

    /** 「国語,数学,英語」のような CSV を配列にする（空なら空配列）。 */
    private static List<String> splitSubjects(String csv) {
        if (csv == null || csv.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(csv.split(","))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .toList();
    }

    @Override
    public DailyReportModels.TimetableResult timetable(long accountId) {
        List<DailyReportModels.TimetableCell> cells = dailyReportMapper.findTimetable(accountId);
        // 月〜金を基本に、記録がある曜日・時限を足す（土日の記録も落とさない）
        TreeSet<Integer> weekdays = new TreeSet<>(List.of(1, 2, 3, 4, 5));
        TreeSet<Integer> periods = new TreeSet<>();
        for (DailyReportModels.TimetableCell cell : cells) {
            weekdays.add(cell.weekday());
            periods.add(cell.period());
        }
        return new DailyReportModels.TimetableResult(
                List.copyOf(weekdays), List.copyOf(periods), cells);
    }

    @Override
    public DailyReportModels.DayDetail day(long accountId, LocalDate date) {
        if (date == null) {
            throw new ValidationException("日付を指定してください。");
        }
        DailyReportEntity report = dailyReportMapper.findReport(accountId, date);
        if (report == null) {
            return new DailyReportModels.DayDetail(date, false, null, false, null,
                    "NORMAL", null, null, null, null, null, null, null, List.of(), List.of());
        }
        List<DailyReportModels.DayLesson> lessons = dailyReportMapper.findLessons(report.getReportId());
        List<DailyReportModels.DaySubject> subjects = dailyReportMapper.findSubjects(report.getReportId());
        return new DailyReportModels.DayDetail(
                report.getTargetDate(), true, report.getSubmitStatus(),
                "SUBMITTED".equals(report.getSubmitStatus()),
                report.getSubmittedAt() == null ? null : report.getSubmittedAt().toLocalDateTime(),
                report.getHolidayType() == null ? "NORMAL" : report.getHolidayType(),
                report.getReview(),
                report.getConcentration(), report.getUnderstanding(),
                report.getStudyVolume(), report.getAttitude(),
                report.getHomework(), report.getNote(), lessons, subjects);
    }


    @Override
    public DailyReportModels.WeekResult week(long accountId, LocalDate anyDateInWeek) {
        if (anyDateInWeek == null) {
            throw new ValidationException("週の日付を指定してください。");
        }
        LocalDate monday = anyDateInWeek.with(java.time.temporal.TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        List<DailyReportModels.WeekLessonRow> recorded =
                dailyReportMapper.findLessonsBetween(accountId, monday, sunday);
        // その日のまとめ（4 観点・振り返り・宿題）と提出状態（カレンダーのマスを開いたときに出す）
        java.util.Map<LocalDate, DailyReportEntity> reports = new java.util.HashMap<>();
        for (DailyReportEntity report : dailyReportMapper.findReportsBetween(accountId, monday, sunday)) {
            reports.put(report.getTargetDate(), report);
        }
        // 記録から推定した時間割（記録が無い日を埋めるのに使う）
        java.util.Map<String, DailyReportModels.TimetableCell> inferred = new java.util.HashMap<>();
        for (DailyReportModels.TimetableCell cell : dailyReportMapper.findTimetable(accountId)) {
            inferred.put(cell.weekday() + "-" + cell.period(), cell);
        }

        List<DailyReportModels.WeekDay> days = new ArrayList<>();
        for (int offset = 0; offset < 7; offset += 1) {
            LocalDate date = monday.plusDays(offset);
            int weekday = date.getDayOfWeek().getValue();
            List<DailyReportModels.WeekLessonRow> rows = recorded.stream()
                    .filter(row -> date.equals(row.date()))
                    .toList();

            DailyReportEntity report = reports.get(date);
            boolean restDay = report != null && report.getHolidayType() != null
                    && !"NORMAL".equals(report.getHolidayType());
            boolean submitted = report != null && "SUBMITTED".equals(report.getSubmitStatus());
            // 時限順に並べる（記録済みと推定を混ぜるため、時限で持ち回る）
            java.util.NavigableMap<Integer, DailyReportModels.WeekLesson> byPeriod = new java.util.TreeMap<>();
            if (restDay) {
                // 祝日・休日は授業が無い（推定もしない）… この場合は何も足さない
                byPeriod.clear();
            } else {
                for (DailyReportModels.WeekLessonRow row : rows) {
                    byPeriod.put(row.period(), new DailyReportModels.WeekLesson(row.period(), row.subject(),
                            row.content(), row.mastery(), row.noteRecorded(), row.noteSkippedReason(),
                            row.concentration(), row.studyVolume(), row.attitude(), true));
                }
            }
            // 推定（過去の日報から作った時間割）で埋めるのは **提出前だけ**。
            // 提出前は、まだ記録していない時限の推定枠を消さない（1 限だけ記録した日に
            // 2〜6 限の枠が消えると、続けて入力できなくなるため）。
            // 提出済みは提出した内容だけを見せる（推定は混ぜない）。
            // 土日も授業があることがあるので、平日と同じ扱いにする（推定の材料が無ければ空のまま）。
            if (!restDay && !submitted) {
                inferred.entrySet().stream()
                        .filter(entry -> entry.getKey().startsWith(weekday + "-"))
                        .forEach(entry -> byPeriod.putIfAbsent(entry.getValue().period(),
                                new DailyReportModels.WeekLesson(
                                        entry.getValue().period(), entry.getValue().subject(), null, null, false,
                                        null, null, null, null, false)));
            }
            List<DailyReportModels.WeekLesson> lessons = new ArrayList<>(byPeriod.values());
            boolean hasReport = !rows.isEmpty() && !restDay;
            // 教科は時限順（記録のある授業だけ。推定は含めない）
            List<String> subjects = lessons.stream()
                    .filter(DailyReportModels.WeekLesson::recorded)
                    .map(DailyReportModels.WeekLesson::subject)
                    .distinct()
                    .toList();
            days.add(new DailyReportModels.WeekDay(
                    date, weekday, hasReport,
                    report == null ? null : report.getSubmitStatus(),
                    submitted,
                    report == null || report.getSubmittedAt() == null
                            ? null : report.getSubmittedAt().toLocalDateTime(),
                    report == null ? null : report.getReview(),
                    report == null ? null : report.getConcentration(),
                    report == null ? null : report.getUnderstanding(),
                    report == null ? null : report.getStudyVolume(),
                    report == null ? null : report.getAttitude(),
                    report == null ? null : report.getHomework(),
                    report == null || report.getHolidayType() == null ? "NORMAL" : report.getHolidayType(),
                    subjects, lessons));
        }
        return new DailyReportModels.WeekResult(monday, sunday, days);
    }

    @Override
    @Transactional
    public DailyReportModels.LessonSaveResult saveLesson(long accountId,
                                                         DailyReportModels.LessonSaveRequest request) {
        if (request == null || request.date() == null) {
            throw new ValidationException("日付を指定してください。");
        }
        if (request.period() < 1 || request.period() > 10) {
            throw new ValidationException("時限は 1〜10 で指定してください。");
        }
        if (request.subject() == null || request.subject().isBlank()) {
            throw new ValidationException("教科名を入力してください。");
        }
        checkRating(request.mastery(), "掌握度");
        checkRating(request.concentration(), "学習集中度");
        checkRating(request.studyVolume(), "学習量");
        checkRating(request.attitude(), "学習態度");

        boolean noteRecorded = Boolean.TRUE.equals(request.noteRecorded());
        String noteSkippedReason = blankToNull(request.noteSkippedReason());
        if (noteRecorded) {
            // 記録したなら理由は残さない
            noteSkippedReason = null;
        }

        DailyReportEntity before = dailyReportMapper.findReport(accountId, request.date());
        boolean created = before == null;
        if (created) {
            dailyReportMapper.insertReport(accountId, request.date());
        }
        DailyReportEntity report = dailyReportMapper.findReport(accountId, request.date());
        if (report == null) {
            throw new ValidationException("日報を作成できませんでした。");
        }
        dailyReportMapper.upsertLesson(report.getReportId(), request.period(), request.subject().trim(),
                blankToNull(request.content()), request.mastery(), noteRecorded, noteSkippedReason,
                request.concentration(), request.studyVolume(), request.attitude(), accountId);

        // 祝日・休日だった日に授業を入れたら「通常」に戻す（授業がある方が新しい情報のため）
        boolean wasRestDay = report.getHolidayType() != null && !"NORMAL".equals(report.getHolidayType());
        if (wasRestDay) {
            dailyReportMapper.resetHolidayType(accountId, request.date());
        }
        // 提出済の日を編集したら下書きに戻す（もう一度【提出】で確定する＝再提出待ち）
        boolean reopened = dailyReportMapper.reopenIfSubmitted(accountId, request.date()) > 0;
        String message = created
                ? "日報を作成し、" + request.period() + "限を登録しました。"
                : request.period() + "限を保存しました。";
        if (wasRestDay) {
            message = message + "祝日・休日の指定は解除しました。";
        }
        if (reopened) {
            message = message + "提出済みだったため、再提出待ちに戻しました。";
        }
        return new DailyReportModels.LessonSaveResult(message, request.date(), request.period(),
                created, reopened);
    }

    @Override
    @Transactional
    public DailyReportModels.SubmitResult submit(long accountId, LocalDate date) {
        if (date == null) {
            throw new ValidationException("日付を指定してください。");
        }
        DailyReportEntity report = dailyReportMapper.findReport(accountId, date);
        if (report == null) {
            throw new ValidationException("記録が無い日は提出できません。先に授業を登録してください。");
        }
        // 祝日・休日は授業が無い（0 限）ので、そのまま提出できる（ユーザーの指定）
        String holidayType = report.getHolidayType();
        boolean restDay = holidayType != null && !"NORMAL".equals(holidayType);
        if (!restDay && dailyReportMapper.countLessons(accountId, date) == 0) {
            throw new ValidationException("記録が無い日は提出できません。先に授業を登録してください。");
        }
        if ("SUBMITTED".equals(report.getSubmitStatus())) {
            throw new ValidationException("この日はすでに提出済みです。");
        }
        if (dailyReportMapper.submit(accountId, date) == 0) {
            throw new ValidationException("この日はすでに提出済みです。");
        }
        DailyReportEntity after = dailyReportMapper.findReport(accountId, date);
        int lessonCount = dailyReportMapper.countLessons(accountId, date);
        return new DailyReportModels.SubmitResult(
                date + " の日報を提出しました（" + lessonCount + " 限）。",
                date, lessonCount,
                after == null || after.getSubmittedAt() == null
                        ? null : after.getSubmittedAt().toLocalDateTime());
    }

    @Override
    @Transactional
    public DailyReportModels.LessonDeleteResult deleteLesson(long accountId, LocalDate date, int period) {
        if (date == null) {
            throw new ValidationException("日付を指定してください。");
        }
        if (period < 1 || period > 10) {
            throw new ValidationException("時限は 1〜10 で指定してください。");
        }
        DailyReportEntity report = dailyReportMapper.findReport(accountId, date);
        if (report == null) {
            throw new ValidationException("この日の日報がありません。");
        }
        if (dailyReportMapper.deleteLesson(report.getReportId(), period) == 0) {
            throw new ValidationException(period + "限の記録が見つかりません。");
        }
        // 提出済の日を編集したときと同じく、下書き（再提出待ち）に戻す
        boolean reopened = dailyReportMapper.reopenIfSubmitted(accountId, date) > 0;
        String message = period + "限の授業を削除しました。";
        if (reopened) {
            message = message + "提出済みだったため、再提出待ちに戻しました。";
        }
        // 消した時限は詰めない（他の時限の番号はそのまま）
        return new DailyReportModels.LessonDeleteResult(message, date, period, reopened);
    }

    @Override
    @Transactional
    public DailyReportModels.DaySummaryResult saveDaySummary(
            long accountId, DailyReportModels.DaySummarySaveRequest request) {
        if (request == null || request.date() == null) {
            throw new ValidationException("日付を指定してください。");
        }
        String holidayType = normalizeHolidayType(request.holidayType());

        DailyReportEntity before = dailyReportMapper.findReport(accountId, request.date());
        if (before == null) {
            dailyReportMapper.insertReport(accountId, request.date());
        }
        DailyReportEntity report = dailyReportMapper.findReport(accountId, request.date());
        if (report == null) {
            throw new ValidationException("日報を作成できませんでした。");
        }
        dailyReportMapper.updateDaySummary(report.getReportId(), holidayType,
                blankToNull(request.review()), blankToNull(request.homework()), accountId);

        // 祝日・休日にしたら、その日の授業は全部消す（時間割ごと休みにするため）
        int cleared = 0;
        boolean isRest = !"NORMAL".equals(holidayType);
        if (isRest) {
            cleared = dailyReportMapper.countLessons(accountId, request.date());
            dailyReportMapper.deleteLessons(report.getReportId());
        }
        // 提出済の日を編集したら下書きに戻す
        boolean reopened = dailyReportMapper.reopenIfSubmitted(accountId, request.date()) > 0;

        String message = switch (holidayType) {
            case "HOLIDAY" -> "祝日として保存しました。";
            case "REST" -> "休日として保存しました。";
            default -> "この日のまとめを保存しました。";
        };
        if (cleared > 0) {
            message = message + "授業の記録 " + cleared + " 件を消しました。";
        }
        if (reopened) {
            message = message + "提出済みだったため、再提出待ちに戻しました。";
        }
        return new DailyReportModels.DaySummaryResult(message, request.date(), holidayType, cleared);
    }

    /** 休日区分（NORMAL / HOLIDAY / REST。未指定は NORMAL）。 */
    private String normalizeHolidayType(String value) {
        String text = blankToNull(value);
        if (text == null) {
            return "NORMAL";
        }
        String upper = text.toUpperCase();
        if (!DailyReportModels.HOLIDAY_TYPES.contains(upper)) {
            throw new ValidationException("祝日・休日の区分は NORMAL / HOLIDAY / REST のいずれかを指定してください。");
        }
        return upper;
    }

    private void checkRating(Integer value, String label) {
        if (value != null && (value < 1 || value > 5)) {
            throw new ValidationException(label + "は 1〜5 で入力してください。");
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /** 期間内の平日の数（土日を除く。祝日は考慮しない）。 */
    private int schoolDays(LocalDate from, LocalDate to) {
        int count = 0;
        for (LocalDate date = from; !date.isAfter(to); date = date.plusDays(1)) {
            DayOfWeek dow = date.getDayOfWeek();
            if (dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY) {
                count += 1;
            }
        }
        return count;
    }
}

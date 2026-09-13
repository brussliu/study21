package com.study21.user.dailyreport;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対する 学習日報 の検証。
 *
 * 2.0 から移行した 24 日分（アカウント 2。移行時に提出済へ寄せてある）を、
 * 期間の絞り込み・時間割の推定・1 日の詳細で読めること、および
 * 記録の保存 → 提出 → 編集で再提出待ち、という流れを確かめる。
 * テストはロールバックするので DB は汚れない。
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class DailyReportRepositoryTest {

    /** 2.0 から移行した日報の持ち主（'ljz' = 劉競澤）。 */
    private static final long ACCOUNT_ID = 2L;
    private static final LocalDate FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);
    /**
     * 2.0 からの移行データだけが入っている期間（件数を厳密に見る用）。
     * アカウント 2 は画面の確認にも使われていて、あとから日報が足されることがあるため、
     * 最近の月では件数を固定しない（2026-09 以降は触らない期間として使う）。
     */
    private static final LocalDate MIGRATED_ONLY_FROM = LocalDate.of(2026, 6, 1);
    private static final LocalDate MIGRATED_ONLY_TO = LocalDate.of(2026, 7, 31);

    @Autowired
    private DailyReportService dailyReportService;

    @Test
    void statusReturnsReportedDaysOnly() {
        // 移行データだけが入っている期間で、件数を厳密に見る
        var result = dailyReportService.status(ACCOUNT_ID, MIGRATED_ONLY_FROM, MIGRATED_ONLY_TO);

        // 2026-06〜07 の移行データは 15 件（月別: 6 月 2 件・7 月 13 件）
        assertThat(result.days()).hasSize(15);
        assertThat(result.reportDays()).isEqualTo(15);
        // 2.0 から移行した日報は「提出済」として扱う（MIG_TRN_学習日報_提出）
        assertThat(result.submittedDays()).isEqualTo(15);
        assertThat(result.reopenedDays()).isZero();
        assertThat(result.schoolDays()).isGreaterThan(15);
        // 日報がある日はすべて平日（土日には無い）
        assertThat(result.days()).allSatisfy(day -> {
            assertThat(day.hasReport()).isTrue();
            assertThat(day.weekday()).isBetween(1, 5);
            assertThat(day.lessonCount()).isGreaterThan(0);
            // 2.0 の実データは 掌握度 と ノート記録フラグ が未入力（NULL / false）なので、
            // 平均は null になる。値が入っている場合は 1〜5 に収まっていること。
            assertThat(day.averageMastery() == null || (day.averageMastery() >= 1 && day.averageMastery() <= 5))
                    .isTrue();
            assertThat(day.noteCount()).isGreaterThanOrEqualTo(0);
        });
        // 対象日は昇順
        assertThat(result.days()).isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));
        // 移行データは提出日時を持ち、教科は授業から拾えている
        assertThat(result.days()).allSatisfy(day -> {
            assertThat(day.submitted()).isTrue();
            assertThat(day.submittedAt()).isNotNull();
        });
        assertThat(result.days().stream().flatMap(day -> day.subjects().stream()).distinct())
                .isNotEmpty();
        // 未展開のマスに出すため、status にも振り返りと今夜の勉強内容を返す（入力がある日は入る）
        assertThat(result.days().stream().map(DailyReportModels.DayStatus::review).filter(java.util.Objects::nonNull))
                .isNotEmpty();
        assertThat(result.days().stream().map(DailyReportModels.DayStatus::homework).filter(java.util.Objects::nonNull))
                .isNotEmpty();

        // 期間全体（画面で足した日報も含む）では、移行分がそのまま残っていることだけを見る
        var all = dailyReportService.status(ACCOUNT_ID, FROM, TO);
        assertThat(all.reportDays()).isGreaterThanOrEqualTo(15);
        assertThat(all.submittedDays()).isGreaterThanOrEqualTo(15);
        assertThat(all.days()).isSortedAccordingTo((a, b) -> a.date().compareTo(b.date()));
    }

    @Test
    void savingRecordingAndSubmittingADayFollowsTheSubmissionFlow() {
        LocalDate date = LocalDate.of(2026, 9, 30);

        // 1) 記録を保存した時点は下書き（記載あり）
        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                date, 1, "数学", "二次関数の最大値", 4, true, null, 4, 3, 4));
        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                date, 2, "国語", "小説の読解", 3, false, "板書が不要だった", 3, 3, 3));

        var afterSave = dailyReportService.day(ACCOUNT_ID, date);
        assertThat(afterSave.hasReport()).isTrue();
        assertThat(afterSave.submitted()).isFalse();
        assertThat(afterSave.submitStatus()).isEqualTo("DRAFT");
        assertThat(afterSave.lessons()).hasSize(2);

        var draftStatus = dailyReportService.status(ACCOUNT_ID, date, date);
        assertThat(draftStatus.reportDays()).isEqualTo(1);
        assertThat(draftStatus.submittedDays()).isZero();
        assertThat(draftStatus.reopenedDays()).isZero();
        assertThat(draftStatus.days().get(0).subjects()).containsExactly("数学", "国語");

        // 2) 提出すると提出済になり、提出日時が入る
        var submitted = dailyReportService.submit(ACCOUNT_ID, date);
        assertThat(submitted.lessonCount()).isEqualTo(2);
        assertThat(submitted.submittedAt()).isNotNull();

        var submittedStatus = dailyReportService.status(ACCOUNT_ID, date, date);
        assertThat(submittedStatus.submittedDays()).isEqualTo(1);
        assertThat(submittedStatus.days().get(0).submitted()).isTrue();
        assertThat(submittedStatus.days().get(0).submittedAt()).isNotNull();

        // 3) 提出済の日に同じことをもう一度提出すると 400
        assertThatThrownBy(() -> dailyReportService.submit(ACCOUNT_ID, date))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("すでに提出済み");

        // 4) 提出後に編集すると下書き（再提出待ち）に戻る。提出日時は残る
        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                date, 3, "英語", "不定詞", 4, true, null, null, null, null));

        var reopenedStatus = dailyReportService.status(ACCOUNT_ID, date, date);
        assertThat(reopenedStatus.submittedDays()).isZero();
        assertThat(reopenedStatus.reopenedDays()).isEqualTo(1);
        assertThat(reopenedStatus.days().get(0).isReopened()).isTrue();
        assertThat(reopenedStatus.days().get(0).submittedAt()).isNotNull();
        assertThat(reopenedStatus.days().get(0).lessonCount()).isEqualTo(3);

        // 5) もう一度提出できる
        dailyReportService.submit(ACCOUNT_ID, date);
        assertThat(dailyReportService.status(ACCOUNT_ID, date, date).submittedDays()).isEqualTo(1);
    }

    @Test
    void lessonKeepsItsOwnRatingsAndNoteReason() {
        LocalDate date = LocalDate.of(2026, 9, 28);

        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                date, 1, "数学", "二次関数", 4, false, "時間が無かった", 5, 4, 3));
        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                date, 2, "体育", null, null, true, "（記録したので理由は消える）", null, null, null));

        var lessons = dailyReportService.day(ACCOUNT_ID, date).lessons();
        assertThat(lessons).hasSize(2);
        var math = lessons.get(0);
        assertThat(math.concentration()).isEqualTo(5);
        assertThat(math.studyVolume()).isEqualTo(4);
        assertThat(math.attitude()).isEqualTo(3);
        assertThat(math.noteRecorded()).isFalse();
        assertThat(math.noteSkippedReason()).isEqualTo("時間が無かった");
        // ノートを記録した授業に理由は残らない
        assertThat(lessons.get(1).noteRecorded()).isTrue();
        assertThat(lessons.get(1).noteSkippedReason()).isNull();
    }

    @Test
    void daySummaryCanMarkHolidaysAndClearsLessons() {
        LocalDate date = LocalDate.of(2026, 9, 27);

        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                date, 1, "数学", "二次関数", 4, true, null, null, null, null));
        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                date, 2, "英語", "不定詞", 3, false, null, null, null, null));
        assertThat(dailyReportService.status(ACCOUNT_ID, date, date).days().get(0).lessonCount()).isEqualTo(2);

        // 祝日にすると授業は全部消え、未提出にも提出率の分母にも数えない
        var result = dailyReportService.saveDaySummary(ACCOUNT_ID,
                new DailyReportModels.DaySummarySaveRequest(date, "HOLIDAY", "文化の日", "宿題なし"));
        assertThat(result.clearedLessons()).isEqualTo(2);
        assertThat(result.message()).contains("祝日").contains("2 件");

        var day = dailyReportService.day(ACCOUNT_ID, date);
        assertThat(day.holidayType()).isEqualTo("HOLIDAY");
        assertThat(day.review()).isEqualTo("文化の日");
        assertThat(day.homework()).isEqualTo("宿題なし");
        assertThat(day.lessons()).isEmpty();

        var status = dailyReportService.status(ACCOUNT_ID, date, date);
        assertThat(status.days().get(0).isRestDay()).isTrue();
        assertThat(status.restDays()).isEqualTo(1);
        // 9/27 は日曜なので平日の分母は元から 0。休日区分でマイナスにならない
        assertThat(status.schoolDays()).isGreaterThanOrEqualTo(0);

        // 授業を入れると「通常」に戻る
        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                date, 3, "国語", "小説", 3, true, null, null, null, null));
        assertThat(dailyReportService.day(ACCOUNT_ID, date).holidayType()).isEqualTo("NORMAL");
    }

    @Test
    void restDayCanBeSubmittedWithoutLessons() {
        // 平日を祝日にして（授業は消える）、0 限のまま提出できる
        LocalDate date = LocalDate.of(2026, 10, 7);
        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                date, 1, "数学", "二次関数", 4, true, null, null, null, null));
        dailyReportService.saveDaySummary(ACCOUNT_ID,
                new DailyReportModels.DaySummarySaveRequest(date, "REST", "学校行事で休み", null));

        var day = dailyReportService.day(ACCOUNT_ID, date);
        assertThat(day.holidayType()).isEqualTo("REST");
        assertThat(day.lessons()).isEmpty();

        var result = dailyReportService.submit(ACCOUNT_ID, date);

        assertThat(result.lessonCount()).isZero();
        assertThat(result.submittedAt()).isNotNull();
        // 提出済みになる（祝日・休日のまま・授業は 0 件）
        var submitted = dailyReportService.day(ACCOUNT_ID, date);
        assertThat(submitted.submitted()).isTrue();
        assertThat(submitted.lessons()).isEmpty();
        assertThat(submitted.holidayType()).isEqualTo("REST");
        // 祝日・休日は提出率の分母から外したまま（提出しても率は動かない）
        var status = dailyReportService.status(ACCOUNT_ID, date, date);
        assertThat(status.days().get(0).isRestDay()).isTrue();
        assertThat(status.days().get(0).lessonCount()).isZero();
    }

    @Test
    void weekendsBehaveLikeWeekdaysInTheWeekView() {
        LocalDate saturday = LocalDate.of(2026, 9, 12);

        // 土曜に授業を入れられる（週表示にも出る）
        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                saturday, 1, "数学", "土曜補習", 4, true, null, null, null, null));

        var week = dailyReportService.week(ACCOUNT_ID, saturday);
        var saturdayDay = week.days().stream().filter(item -> item.date().equals(saturday)).findFirst().orElseThrow();
        assertThat(saturdayDay.hasReport()).isTrue();
        assertThat(saturdayDay.lessons()).hasSize(1);
        assertThat(saturdayDay.lessons().get(0).content()).isEqualTo("土曜補習");

        // 記録が無い土日も週に含める（7 日ぶん返す）
        assertThat(week.days()).hasSize(7);
        assertThat(week.days().get(6).weekday()).isEqualTo(7);
        assertThat(week.days().get(5).weekday()).isEqualTo(6);
    }

    @Test
    void submitRejectsADayWithoutRecords() {
        LocalDate date = LocalDate.of(2026, 9, 29);

        assertThatThrownBy(() -> dailyReportService.submit(ACCOUNT_ID, date))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("記録が無い日は提出できません");
    }

    @Test
    void weekReturnsSubmissionSummaryAndSubjects() {
        LocalDate anyDate = LocalDate.of(2026, 9, 9);

        var week = dailyReportService.week(ACCOUNT_ID, anyDate);

        // 記録がある日（この週は火〜木）は教科とまとめが付く
        // （アカウント 2 は画面の確認にも使われていて、あとから「記載あり」の日が増えるため、
        //   提出状態そのものは「提出済の日」だけを厳密に見る）
        var recorded = week.days().stream().filter(DailyReportModels.WeekDay::hasReport).toList();
        assertThat(recorded).isNotEmpty();
        assertThat(recorded).allSatisfy(day -> {
            assertThat(day.subjects()).isNotEmpty();
            // 移行データは 4 観点が入っている日がある（無い日は null のまま返す）
            assertThat(day.concentration() == null || day.concentration() >= 1).isTrue();
            // 提出済みなら提出日時も入っている
            if (day.submitted()) {
                assertThat(day.submitStatus()).isEqualTo("SUBMITTED");
                assertThat(day.submittedAt()).isNotNull();
            }
        });
        assertThat(recorded.stream().filter(DailyReportModels.WeekDay::submitted)).isNotEmpty();
        assertThat(recorded.stream().filter(DailyReportModels.WeekDay::submitted))
                .allSatisfy(day -> assertThat(day.submittedAt()).isNotNull());
        // 記録が無い日は提出済にならない
        // （祝日・休日は 0 限のまま提出できるので、そこだけは例外）
        assertThat(week.days().stream().filter(day -> !day.hasReport()
                        && !("HOLIDAY".equals(day.holidayType()) || "REST".equals(day.holidayType()))))
                .allSatisfy(day -> {
                    assertThat(day.submitted()).isFalse();
                    assertThat(day.subjects()).isEmpty();
                });
    }

    @Test
    void weekKeepsInferredLessonsUntilSubmitted() {
        // まだ日報が無い週（2026-10 の火曜）に、1 限だけ記録する
        LocalDate tuesday = LocalDate.of(2026, 10, 6);
        dailyReportService.saveLesson(ACCOUNT_ID, new DailyReportModels.LessonSaveRequest(
                tuesday, 1, "数学", "一次関数", 4, true, null, null, null, null));

        // 提出前: 記録した 1 限はそのまま、まだ記録していない時限は推定の枠として残る
        var day = dailyReportService.week(ACCOUNT_ID, tuesday).days().stream()
                .filter(item -> item.date().equals(tuesday)).findFirst().orElseThrow();
        assertThat(day.hasReport()).isTrue();
        assertThat(day.lessons()).hasSizeGreaterThan(1);
        assertThat(day.lessons()).isSortedAccordingTo(
                (a, b) -> Integer.compare(a.period(), b.period()));
        var first = day.lessons().get(0);
        assertThat(first.period()).isEqualTo(1);
        assertThat(first.recorded()).isTrue();
        assertThat(first.content()).isEqualTo("一次関数");
        assertThat(day.lessons().stream().filter(lesson -> !lesson.recorded())).isNotEmpty();
        assertThat(day.lessons().stream().filter(lesson -> !lesson.recorded()))
                .allSatisfy(lesson -> assertThat(lesson.period()).isNotEqualTo(1));

        // 提出後: 提出した内容だけになる（推定は混ぜない）
        dailyReportService.submit(ACCOUNT_ID, tuesday);
        var submittedDay = dailyReportService.week(ACCOUNT_ID, tuesday).days().stream()
                .filter(item -> item.date().equals(tuesday)).findFirst().orElseThrow();
        assertThat(submittedDay.submitted()).isTrue();
        assertThat(submittedDay.lessons()).hasSize(1);
        assertThat(submittedDay.lessons().get(0).recorded()).isTrue();
    }

    @Test
    void statusIsScopedToTheAccountAndPeriod() {
        assertThat(dailyReportService.status(ACCOUNT_ID, LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 31)).days())
                .allSatisfy(day -> assertThat(day.date().getMonthValue()).isEqualTo(7));
        // 別のアカウントには日報が無い
        assertThat(dailyReportService.status(99_999L, FROM, TO).days()).isEmpty();
        // 期間の指定ミスは拒否する
        assertThatThrownBy(() -> dailyReportService.status(ACCOUNT_ID, TO, FROM))
                .isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> dailyReportService.status(ACCOUNT_ID, FROM, FROM.plusDays(400)))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void timetableIsDerivedFromLessons() {
        var result = dailyReportService.timetable(ACCOUNT_ID);

        assertThat(result.weekdays()).contains(1, 2, 3, 4, 5);
        assertThat(result.periods()).contains(1, 2, 3);
        assertThat(result.cells()).isNotEmpty();
        // 月曜は 7 限まである（実データ）
        assertThat(result.cells()).anySatisfy(cell -> {
            assertThat(cell.weekday()).isEqualTo(1);
            assertThat(cell.period()).isEqualTo(7);
        });
        // 各枠は 1 件だけ（最も多い教科）で、出現回数は記録日数以下
        assertThat(result.cells()).allSatisfy(cell -> {
            assertThat(cell.subject()).isNotBlank();
            assertThat(cell.appearances()).isPositive();
            assertThat(cell.appearances()).isLessThanOrEqualTo(cell.days());
        });
    }

    @Test
    void dayDetailReturnsLessonsRatingsAndSubjects() {
        var detail = dailyReportService.day(ACCOUNT_ID, LocalDate.of(2026, 9, 10));

        assertThat(detail.hasReport()).isTrue();
        assertThat(detail.lessons()).isNotEmpty();
        assertThat(detail.lessons()).isSortedAccordingTo((a, b) -> Integer.compare(a.period(), b.period()));
        assertThat(detail.lessons()).allSatisfy(lesson -> {
            assertThat(lesson.subject()).isNotBlank();
            assertThat(lesson.mastery() == null || (lesson.mastery() >= 1 && lesson.mastery() <= 5)).isTrue();
        });
        assertThat(detail.concentration()).isBetween(1, 5);
        assertThat(detail.attitude()).isBetween(1, 5);

        // 日報が無い日は hasReport=false（例外にしない）。
        // アカウント 2 は画面の確認にも使われていて 9 月にあとから日報が足されるため、
        // だれも触らない先の日付で確かめる。
        var empty = dailyReportService.day(ACCOUNT_ID, LocalDate.of(2026, 12, 31));
        assertThat(empty.hasReport()).isFalse();
        assertThat(empty.lessons()).isEmpty();

        // 期間の端（最初の日報）
        assertThat(dailyReportService.day(ACCOUNT_ID, FROM).hasReport()).isFalse();
        assertThat(DayOfWeek.MONDAY).isNotNull();
    }
}

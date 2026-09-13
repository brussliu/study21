package com.study21.user.dailyreport;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 学習日報の「記載あり（下書き）」と「提出済」の扱い。
 *
 * <p>2.0 は「日報の行がある＝提出した」という扱いだったが、2.1 は分ける:
 * 記録を保存した時点は下書き、【提出】で提出済、提出後に編集すると再提出待ち（下書き）に戻る。</p>
 */
class DailyReportSubmitTest {

    private static final long ACCOUNT_ID = 2L;
    private static final LocalDate DATE = LocalDate.of(2026, 9, 12);

    private DailyReportMapper mapper;
    private DailyReportServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(DailyReportMapper.class);
        service = new DailyReportServiceImpl(mapper);
    }

    private DailyReportEntity report(String submitStatus, Timestamp submittedAt) {
        DailyReportEntity entity = new DailyReportEntity();
        entity.setReportId(10L);
        entity.setAccountId(ACCOUNT_ID);
        entity.setTargetDate(DATE);
        entity.setSubmitStatus(submitStatus);
        entity.setSubmittedAt(submittedAt);
        return entity;
    }

    @Test
    void submitMarksTheDayAsSubmitted() {
        when(mapper.findReport(ACCOUNT_ID, DATE)).thenReturn(report("DRAFT", null));
        when(mapper.countLessons(ACCOUNT_ID, DATE)).thenReturn(6);
        when(mapper.submit(ACCOUNT_ID, DATE)).thenReturn(1);
        // 提出後の再読み込みでは提出済み・提出日時つきで返る
        when(mapper.findReport(ACCOUNT_ID, DATE))
                .thenReturn(report("DRAFT", null))
                .thenReturn(report("SUBMITTED", Timestamp.valueOf("2026-09-12 21:30:00")));

        DailyReportModels.SubmitResult result = service.submit(ACCOUNT_ID, DATE);

        assertThat(result.lessonCount()).isEqualTo(6);
        assertThat(result.submittedAt()).isEqualTo(LocalDateTime.of(2026, 9, 12, 21, 30));
        assertThat(result.message()).contains("提出しました").contains("6 限");
        verify(mapper).submit(ACCOUNT_ID, DATE);
    }

    /** 祝日・休日は授業が無い（0 限）ので、そのまま提出できる（ユーザーの指定）。 */
    @Test
    void submitAcceptsARestDayWithoutLessons() {
        DailyReportEntity holiday = report("DRAFT", null);
        holiday.setHolidayType("HOLIDAY");
        when(mapper.findReport(ACCOUNT_ID, DATE)).thenReturn(holiday);
        when(mapper.countLessons(ACCOUNT_ID, DATE)).thenReturn(0);
        when(mapper.submit(ACCOUNT_ID, DATE)).thenReturn(1);
        when(mapper.findReport(ACCOUNT_ID, DATE))
                .thenReturn(holiday)
                .thenReturn(report("SUBMITTED", Timestamp.valueOf("2026-09-12 21:30:00")));

        DailyReportModels.SubmitResult result = service.submit(ACCOUNT_ID, DATE);

        assertThat(result.lessonCount()).isZero();
        assertThat(result.message()).contains("提出しました").contains("0 限");
        verify(mapper).submit(ACCOUNT_ID, DATE);
    }

    @Test
    void submitRejectsADayWithoutLessons() {
        when(mapper.findReport(ACCOUNT_ID, DATE)).thenReturn(report("DRAFT", null));
        when(mapper.countLessons(ACCOUNT_ID, DATE)).thenReturn(0);

        assertThatThrownBy(() -> service.submit(ACCOUNT_ID, DATE))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("記録が無い日は提出できません");
        verify(mapper, never()).submit(anyLong(), any());
    }

    @Test
    void submitRejectsADayWithoutReport() {
        when(mapper.findReport(ACCOUNT_ID, DATE)).thenReturn(null);

        assertThatThrownBy(() -> service.submit(ACCOUNT_ID, DATE))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("記録が無い日は提出できません");
        verify(mapper, never()).submit(anyLong(), any());
    }

    @Test
    void submitRejectsAnAlreadySubmittedDay() {
        when(mapper.findReport(ACCOUNT_ID, DATE))
                .thenReturn(report("SUBMITTED", Timestamp.valueOf("2026-09-12 20:00:00")));
        when(mapper.countLessons(ACCOUNT_ID, DATE)).thenReturn(6);

        assertThatThrownBy(() -> service.submit(ACCOUNT_ID, DATE))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("すでに提出済み");
        verify(mapper, never()).submit(anyLong(), any());
    }

    @Test
    void submitRequiresADate() {
        assertThatThrownBy(() -> service.submit(ACCOUNT_ID, null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("日付を指定してください");
    }

    @Test
    void savingALessonOnASubmittedDayReopensIt() {
        when(mapper.findReport(ACCOUNT_ID, DATE)).thenReturn(report("SUBMITTED", Timestamp.valueOf("2026-09-12 20:00:00")));
        when(mapper.reopenIfSubmitted(ACCOUNT_ID, DATE)).thenReturn(1);

        DailyReportModels.LessonSaveResult result = service.saveLesson(ACCOUNT_ID,
                new DailyReportModels.LessonSaveRequest(DATE, 3, "数学", "二次関数", 4, true,
                        null, null, null, null));

        assertThat(result.created()).isFalse();
        assertThat(result.reopened()).isTrue();
        assertThat(result.message()).contains("再提出待ち");
        verify(mapper).reopenIfSubmitted(ACCOUNT_ID, DATE);
    }

    @Test
    void savingALessonOnADraftDayDoesNotTouchSubmission() {
        when(mapper.findReport(ACCOUNT_ID, DATE)).thenReturn(report("DRAFT", null));
        when(mapper.reopenIfSubmitted(ACCOUNT_ID, DATE)).thenReturn(0);

        DailyReportModels.LessonSaveResult result = service.saveLesson(ACCOUNT_ID,
                new DailyReportModels.LessonSaveRequest(DATE, 3, "数学", null, null, false,
                        null, null, null, null));

        assertThat(result.reopened()).isFalse();
        assertThat(result.message()).doesNotContain("再提出待ち");
    }

    @Test
    void statusCountsSubmittedAndReopenedDaysSeparately() {
        DailyReportModels.DayStatusRow submitted = new DailyReportModels.DayStatusRow(
                LocalDate.of(2026, 9, 1), 2, 6, 3.5, 2, "SUBMITTED", true,
                LocalDateTime.of(2026, 9, 1, 21, 0), "国語,数学,英語", 6, "NORMAL", "9/1 の振り返り", "ワーク p.1");
        DailyReportModels.DayStatusRow reopened = new DailyReportModels.DayStatusRow(
                LocalDate.of(2026, 9, 2), 3, 5, 4.0, 1, "DRAFT", false,
                LocalDateTime.of(2026, 9, 2, 20, 0), "数学,理科", 4, "NORMAL", null, null);
        DailyReportModels.DayStatusRow draft = new DailyReportModels.DayStatusRow(
                LocalDate.of(2026, 9, 3), 4, 2, null, 0, "DRAFT", false, null, "", 1, "NORMAL", null, null);
        when(mapper.findStatus(eq(ACCOUNT_ID), any(), any()))
                .thenReturn(List.of(submitted, reopened, draft));

        DailyReportModels.StatusResult result = service.status(ACCOUNT_ID,
                LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

        assertThat(result.reportDays()).isEqualTo(3);
        assertThat(result.submittedDays()).isEqualTo(1);
        assertThat(result.reopenedDays()).isEqualTo(1);
        // 平日の数を分母にした提出率は画面側で出す（9/1〜9/30 の平日 = 22 日）
        assertThat(result.schoolDays()).isEqualTo(22);

        DailyReportModels.DayStatus first = result.days().get(0);
        assertThat(first.subjects()).containsExactly("国語", "数学", "英語");
        assertThat(first.isSubmitted()).isTrue();
        assertThat(first.isReopened()).isFalse();

        DailyReportModels.DayStatus second = result.days().get(1);
        assertThat(second.isSubmitted()).isFalse();
        // 一度提出したあと編集した日は「再提出待ち」
        assertThat(second.isReopened()).isTrue();
        assertThat(second.subjects()).containsExactly("数学", "理科");
        assertThat(second.detailedLessonCount()).isEqualTo(4);

        DailyReportModels.DayStatus third = result.days().get(2);
        assertThat(third.subjects()).isEmpty();
        assertThat(third.isReopened()).isFalse();
    }

    @Test
    void weekCarriesSubmissionAndSummaryPerDay() {
        LocalDate monday = LocalDate.of(2026, 9, 7);
        when(mapper.findLessonsBetween(eq(ACCOUNT_ID), any(), any())).thenReturn(List.of(
                new DailyReportModels.WeekLessonRow(monday, 1, "数学", "二次関数", 4, true, null, 4, 3, 4),
                new DailyReportModels.WeekLessonRow(monday, 2, "国語", "小説", 3, false, "板書が不要", 3, 3, 3)));
        DailyReportEntity header = report("SUBMITTED", Timestamp.valueOf("2026-09-07 21:00:00"));
        header.setTargetDate(monday);
        header.setReview("今日の振り返り");
        header.setHomework("英語のワーク");
        header.setConcentration(4);
        header.setUnderstanding(3);
        header.setStudyVolume(4);
        header.setAttitude(3);
        when(mapper.findReportsBetween(eq(ACCOUNT_ID), any(), any())).thenReturn(List.of(header));
        when(mapper.findTimetable(ACCOUNT_ID)).thenReturn(List.of());

        DailyReportModels.WeekResult week = service.week(ACCOUNT_ID, monday.plusDays(2));

        DailyReportModels.WeekDay day = week.days().stream()
                .filter(item -> item.date().equals(monday)).findFirst().orElseThrow();
        assertThat(day.submitted()).isTrue();
        assertThat(day.submitStatus()).isEqualTo("SUBMITTED");
        assertThat(day.review()).isEqualTo("今日の振り返り");
        assertThat(day.homework()).isEqualTo("英語のワーク");
        assertThat(day.concentration()).isEqualTo(4);
        assertThat(day.attitude()).isEqualTo(3);
        // 教科は時限順（1 限 数学 → 2 限 国語）
        assertThat(day.subjects()).containsExactly("数学", "国語");

        // 記録が無い平日は提出状態なし（推定の授業だけ）
        DailyReportModels.WeekDay empty = week.days().stream()
                .filter(item -> item.date().equals(monday.plusDays(1))).findFirst().orElseThrow();
        assertThat(empty.hasReport()).isFalse();
        assertThat(empty.submitStatus()).isNull();
        assertThat(empty.review()).isNull();
    }

    @Test
    void dayDetailCarriesSubmission() {
        when(mapper.findReport(ACCOUNT_ID, DATE))
                .thenReturn(report("SUBMITTED", Timestamp.valueOf("2026-09-12 21:00:00")));
        when(mapper.findLessons(10L)).thenReturn(List.of());
        when(mapper.findSubjects(10L)).thenReturn(List.of());

        DailyReportModels.DayDetail detail = service.day(ACCOUNT_ID, DATE);

        assertThat(detail.hasReport()).isTrue();
        assertThat(detail.submitted()).isTrue();
        assertThat(detail.submitStatus()).isEqualTo("SUBMITTED");
        assertThat(detail.submittedAt()).isEqualTo(LocalDateTime.of(2026, 9, 12, 21, 0));
    }
}

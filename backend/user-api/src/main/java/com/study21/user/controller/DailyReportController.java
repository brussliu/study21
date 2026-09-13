package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.dailyreport.DailyReportModels;
import com.study21.user.dailyreport.DailyReportService;
import com.study21.user.security.UserPrincipal;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * 学習日報 API（user-api）。対象はログイン中の本人の日報。
 *
 * <ul>
 *   <li>`GET /status?from=&amp;to=` … 期間内の記載・提出状況（カレンダーの素）</li>
 *   <li>`GET /timetable` … 記録から推定した週の時間割（曜日×時限ごとの教科）</li>
 *   <li>`GET /week?date=` … 週表示（マスを開いたときの授業と、その日のまとめ・提出状態）</li>
 *   <li>`GET /day?date=` … 1 日の詳細</li>
 *   <li>`POST /lesson` … 1 限ぶんの記録を保存（提出済の日は下書きに戻る）</li>
 *   <li>`DELETE /lesson?date=&amp;period=` … 1 限ぶんの記録を削除（消した時限は詰めない）</li>
 *   <li>`POST /day` … 1 日のまとめ（祝日/休日区分・振り返り・今夜の勉強内容）を保存</li>
 *   <li>`POST /submit` … その日を提出する（記録が無い日は 400）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/user/daily-reports")
public class DailyReportController {

    private final DailyReportService dailyReportService;

    public DailyReportController(DailyReportService dailyReportService) {
        this.dailyReportService = dailyReportService;
    }

    @GetMapping("/status")
    public ApiResponse<DailyReportModels.StatusResult> status(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.ok(dailyReportService.status(user.accountId(), from, to));
    }

    @GetMapping("/timetable")
    public ApiResponse<DailyReportModels.TimetableResult> timetable(
            @AuthenticationPrincipal UserPrincipal user) {
        return ApiResponse.ok(dailyReportService.timetable(user.accountId()));
    }

    /** 週表示（月〜金。記録が無い日は過去の日報から推定した時間割で埋める）。 */
    @GetMapping("/week")
    public ApiResponse<DailyReportModels.WeekResult> week(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(dailyReportService.week(user.accountId(), date));
    }

    /** 1 限ぶんの日報を保存する（授業をクリックして開くダイアログから）。 */
    @PostMapping("/lesson")
    public ApiResponse<DailyReportModels.LessonSaveResult> saveLesson(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody DailyReportModels.LessonSaveRequest request) {
        DailyReportModels.LessonSaveResult result = dailyReportService.saveLesson(user.accountId(), request);
        return ApiResponse.ok(result, result.message());
    }

    /** 1 限ぶんの記録を削除する（消した時限は詰めない）。 */
    @DeleteMapping("/lesson")
    public ApiResponse<DailyReportModels.LessonDeleteResult> deleteLesson(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam("period") int period) {
        DailyReportModels.LessonDeleteResult result = dailyReportService.deleteLesson(user.accountId(), date, period);
        return ApiResponse.ok(result, result.message());
    }

    /**
     * 1 日のまとめを保存する（日まとめのダイアログから）。
     * 祝日・休日にしたときは、その日の授業の記録を全部消す。
     */
    @PostMapping("/day")
    public ApiResponse<DailyReportModels.DaySummaryResult> saveDaySummary(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody DailyReportModels.DaySummarySaveRequest request) {
        DailyReportModels.DaySummaryResult result = dailyReportService.saveDaySummary(user.accountId(), request);
        return ApiResponse.ok(result, result.message());
    }

    /**
     * その日を提出する（提出済にする）。
     * 記録が 1 件も無い日と、すでに提出済みの日は 400。
     */
    @PostMapping("/submit")
    public ApiResponse<DailyReportModels.SubmitResult> submit(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody SubmitRequest request) {
        DailyReportModels.SubmitResult result = dailyReportService.submit(user.accountId(), request.date());
        return ApiResponse.ok(result, result.message());
    }

    /** 提出のリクエスト（日付だけ）。 */
    public record SubmitRequest(
            @jakarta.validation.constraints.NotNull(message = "日付を指定してください。")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
    }

    @GetMapping("/day")
    public ApiResponse<DailyReportModels.DayDetail> day(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestParam("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.ok(dailyReportService.day(user.accountId(), date));
    }
}

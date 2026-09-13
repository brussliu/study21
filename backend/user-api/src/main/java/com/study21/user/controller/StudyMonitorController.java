package com.study21.user.controller;

import com.study21.common.core.api.ApiResponse;
import com.study21.user.security.UserPrincipal;
import com.study21.user.studymonitor.StudyMonitorModels;
import com.study21.user.studymonitor.StudyMonitorService;
import jakarta.validation.Valid;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 学習状況モニター API（user-api）。
 *
 * <p>2.0 の `api/study-monitor/snapshots`（対象日の動画とスナップショット）と
 * `api/study-monitor/updateManualAnalysis`（判定結果の手動修正）に当たる。
 * 画像は 2.0 と同じように 1 枚ずつ返す（`/snapshots/{id}/image`）。</p>
 */
@RestController
@RequestMapping("/api/user/study-monitor")
public class StudyMonitorController {

    private final StudyMonitorService studyMonitorService;

    public StudyMonitorController(StudyMonitorService studyMonitorService) {
        this.studyMonitorService = studyMonitorService;
    }

    /** 対象日の動画セグメントとスナップショット（最新の分析つき）。 */
    @GetMapping("/snapshots")
    public ApiResponse<StudyMonitorModels.SnapshotSearchResult> search(
            @RequestParam(value = "date", required = false) String date,
            @RequestParam(value = "timeFrom", required = false) String timeFrom,
            @RequestParam(value = "timeTo", required = false) String timeTo,
            @RequestParam(value = "videoId", required = false) Long videoId,
            @RequestParam(value = "analysisState", required = false) String analysisState,
            @RequestParam(value = "result", required = false) String result) {
        return ApiResponse.ok(studyMonitorService.search(date, timeFrom, timeTo, videoId, analysisState, result));
    }

    /** 判定結果の一括修正（修正理由は必須）。 */
    @PatchMapping("/snapshots")
    public ApiResponse<StudyMonitorModels.ManualCorrectionResult> correct(
            @AuthenticationPrincipal UserPrincipal user,
            @Valid @RequestBody StudyMonitorModels.ManualCorrectionRequest request) {
        StudyMonitorModels.ManualCorrectionResult result = studyMonitorService.correctManually(user, request);
        return ApiResponse.ok(result, result.message());
    }

    /**
     * スナップショットの画像。
     * 画像は保存ルート（`study21.study-monitor.snapshot-root`）からの相対パスで解決する。
     */
    @GetMapping("/snapshots/{snapshotId}/image")
    public ResponseEntity<InputStreamResource> image(@PathVariable long snapshotId) {
        String fileName = studyMonitorService.imageFileName(snapshotId);
        InputStreamResource body = new InputStreamResource(studyMonitorService.openImage(snapshotId));
        MediaType type = fileName.toLowerCase().endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .header("Cache-Control", "private, max-age=300")
                .body(body);
    }
}

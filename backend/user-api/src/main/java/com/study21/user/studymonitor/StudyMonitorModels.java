package com.study21.user.studymonitor;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotEmpty;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 学習状況モニターの API で使う型。
 *
 * <p>2.0 の `api/study-monitor/snapshots` と同じ考え方で、対象日の
 * 「動画セグメント」と「スナップショット（最新の分析つき）」をまとめて返す。</p>
 */
public final class StudyMonitorModels {

    /** 分析結果のコード（DB の 最終分析結果コード と同じ）。 */
    public static final List<String> RESULT_CODES = List.of(
            "STUDY_NO_PC", "STUDY_PC", "AWAY", "PC_NON_STUDY", "OTHER", "UNKNOWN");

    /** 画面の「AI分析」の絞り込み。 */
    public static final List<String> ANALYSIS_STATES = List.of("all", "done", "waiting", "error");

    private StudyMonitorModels() {
    }

    /** その日の動画セグメント。 */
    public record VideoRow(
            long videoId,
            String fileName,
            LocalDateTime startedAt,
            LocalDateTime endedAt,
            int durationSeconds,
            String importState,
            int snapshotCount,
            int completedCount) {
    }

    /** スナップショット 1 枚（最新の分析つき）。 */
    public record SnapshotRow(
            long snapshotId,
            long videoId,
            String videoFileName,
            LocalDateTime capturedAt,
            String imagePath,
            Integer width,
            Integer height,
            /** WAITING / RUNNING / COMPLETED / ERROR */
            String analysisState,
            /** 画面に出す結果（手動修正があればそれ）。null は未判定 */
            String resultCode,
            BigDecimal confidence,
            /** 画面に出す理由（手動修正の理由があればそれ） */
            String reason,
            boolean manual,
            int version) {
    }

    /** 検索結果の集計。 */
    public record Summary(
            int videoCount,
            int snapshotCount,
            int completed,
            int waiting,
            int errors,
            Map<String, Integer> resultCounts) {
    }

    /** 一覧のレスポンス。 */
    public record SnapshotSearchResult(
            String date,
            String timeFrom,
            String timeTo,
            List<VideoRow> videos,
            List<SnapshotRow> snapshots,
            Summary summary) {
    }

    /** 一括修正の 1 件（楽観的ロック用に版を送る）。 */
    public record ManualUpdate(long snapshotId, int version) {
    }

    /**
     * 判定結果の修正。
     *
     * <p>修正理由は**任意**（ユーザーの指定。2026-09-13。詳細画面では必須にしない）。
     * 空のときは DB に NULL で残す（「理由なし」と分かるように）。
     * 一括変更の画面側は理由を必須にしているが、サーバーは空でも受け付ける。</p>
     */
    public record ManualCorrectionRequest(
            @NotEmpty(message = "分析結果を変更する画像を選択してください。")
            List<ManualUpdate> updates,
            @NotBlank(message = "分析結果を選択してください。")
            String result,
            @Size(max = 2000, message = "修正理由は2000文字以内で入力してください。")
            String reason) {
    }

    /** 一括修正のレスポンス。 */
    public record ManualCorrectionResult(String message, int updatedCount) {
    }
}

package com.study21.admin.studymonitor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * MON_学習モニター動画情報 の 1 行（batL02 が INSERT する内容）。
 *
 * <p>{@code 取込状態コード}（IMPORTED）・{@code 状態}（1）・監査列（登録元/更新元コード = BAT_L02、
 * アカウントID は NULL）は Mapper の SQL 側で固定する。</p>
 *
 * <p>{@code 保存パス} は**保存ルートからの相対パス**（2.0 は絶対パスだった）。</p>
 */
public class StudyMonitorVideoEntity {

    /** INSERT 後に採番された 動画ID（useGeneratedKeys）。 */
    private Long videoId;
    private Long cameraId;
    /** 撮影日（撮影開始日時の日付）。 */
    private LocalDate capturedDate;
    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
    private String fileName;
    /** 保存ルートからの相対パス（例: 20260726/xxx.mp4）。 */
    private String relativePath;
    /** ffprobe が返した動画の長さ（秒。四捨五入・最低 1）。 */
    private Long durationSeconds;
    /** ファイルサイズ（バイト）。 */
    private Long fileSize;

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public Long getCameraId() {
        return cameraId;
    }

    public void setCameraId(Long cameraId) {
        this.cameraId = cameraId;
    }

    public LocalDate getCapturedDate() {
        return capturedDate;
    }

    public void setCapturedDate(LocalDate capturedDate) {
        this.capturedDate = capturedDate;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public void setEndedAt(LocalDateTime endedAt) {
        this.endedAt = endedAt;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getRelativePath() {
        return relativePath;
    }

    public void setRelativePath(String relativePath) {
        this.relativePath = relativePath;
    }

    public Long getDurationSeconds() {
        return durationSeconds;
    }

    public void setDurationSeconds(Long durationSeconds) {
        this.durationSeconds = durationSeconds;
    }

    public Long getFileSize() {
        return fileSize;
    }

    public void setFileSize(Long fileSize) {
        this.fileSize = fileSize;
    }
}

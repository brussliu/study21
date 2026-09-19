package com.study21.admin.studymonitor;

import java.time.LocalDateTime;

/**
 * MON_学習モニタースナップショット情報 に INSERT する 1 枚分。
 *
 * <p>{@code 撮影日時} は「動画の撮影開始日時 + 動画内オフセット」で決める（処理時刻ではない）。
 * {@code 切出状態コード}（CREATED）・{@code 状態}（1）・監査列は Mapper の SQL 側で固定する。</p>
 */
public class StudyMonitorSnapshotEntity {

    private Long snapshotId;
    private Long videoId;
    /** 撮影日時（撮影開始日時 + 動画内オフセットミリ秒）。 */
    private LocalDateTime capturedAt;
    /** 動画の先頭からの位置（ミリ秒。index × 切出間隔秒 × 1000）。 */
    private Long offsetMillis;
    /** 画像ファイル名（snapshot_%06d.jpg）。 */
    private String fileName;
    /** 保存ルートからの相対パス（例: 20260726/snapshot_000045.jpg）。 */
    private String relativePath;
    private Integer width;
    private Integer height;

    public Long getSnapshotId() {
        return snapshotId;
    }

    public void setSnapshotId(Long snapshotId) {
        this.snapshotId = snapshotId;
    }

    public Long getVideoId() {
        return videoId;
    }

    public void setVideoId(Long videoId) {
        this.videoId = videoId;
    }

    public LocalDateTime getCapturedAt() {
        return capturedAt;
    }

    public void setCapturedAt(LocalDateTime capturedAt) {
        this.capturedAt = capturedAt;
    }

    public Long getOffsetMillis() {
        return offsetMillis;
    }

    public void setOffsetMillis(Long offsetMillis) {
        this.offsetMillis = offsetMillis;
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

    public Integer getWidth() {
        return width;
    }

    public void setWidth(Integer width) {
        this.width = width;
    }

    public Integer getHeight() {
        return height;
    }

    public void setHeight(Integer height) {
        this.height = height;
    }
}

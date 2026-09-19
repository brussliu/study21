package com.study21.admin.studymonitor;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 動画 1 本と、そこから切り出したスナップショットを 1 つのトランザクションで登録する（batL02）。
 *
 * <p>2.0 の {@code BatL02Task#saveVideoAndSnapshots} と同じで、動画を入れてから
 * スナップショットを一括で入れる。途中で失敗したらロールバックする（
 * そのとき動画ファイルは {@code checked/} に残るので、次の実行でやり直せる）。</p>
 *
 * <p>DB アクセスは Mapper（MyBatis）だけを通す。SQL は
 * {@code resources/mapper/StudyMonitorImportMapper.xml}。SQL ログは
 * {@code SqlLoggingInterceptor} が自動で記録する。</p>
 */
@Component
public class StudyMonitorRecordWriter {

    private final StudyMonitorImportMapper mapper;

    public StudyMonitorRecordWriter(StudyMonitorImportMapper mapper) {
        this.mapper = mapper;
    }

    /**
     * 動画とスナップショットを登録する。
     *
     * @param cameraId   カメラID（upsert 済み）
     * @param video      取り込む動画（{@code checked/} に移した後のパス）
     * @param period     撮影時間帯（ファイル名から）
     * @param snapshots  切り出して所定の場所へ移したスナップショット
     * @param durationSeconds ffprobe が返した長さ（秒）
     * @param fileSize   動画ファイルのサイズ（バイト）
     * @param snapshotRoot 保存ルート（相対パスの基準）
     * @param imageSize  スナップショットの大きさ（1 枚目から読んだ値。2.0 と同じく全枚に使う）
     * @return 登録した 動画ID
     */
    @Transactional
    public long write(long cameraId, Path video, VideoFileTools.VideoPeriod period,
                      List<StudyMonitorSnapshotWriter.StagedSnapshot> snapshots,
                      long durationSeconds, long fileSize, Path snapshotRoot,
                      VideoFileTools.ImageSize imageSize) {
        StudyMonitorVideoEntity entity = new StudyMonitorVideoEntity();
        entity.setCameraId(cameraId);
        entity.setCapturedDate(period.startedAt().toLocalDate());
        entity.setStartedAt(period.startedAt());
        entity.setEndedAt(period.endedAt());
        entity.setFileName(video.getFileName().toString());
        entity.setRelativePath(StudyMonitorSnapshotWriter.toRelativePath(snapshotRoot, video));
        entity.setDurationSeconds(durationSeconds);
        entity.setFileSize(fileSize);
        mapper.insertVideo(entity);

        Long videoId = entity.getVideoId();
        if (videoId == null) {
            throw new IllegalStateException("動画の登録で 動画ID を採番できませんでした: " + video.getFileName());
        }

        List<StudyMonitorSnapshotEntity> rows = new ArrayList<>(snapshots.size());
        for (StudyMonitorSnapshotWriter.StagedSnapshot snapshot : snapshots) {
            StudyMonitorSnapshotEntity row = new StudyMonitorSnapshotEntity();
            row.setVideoId(videoId);
            row.setCapturedAt(snapshot.capturedAt());
            row.setOffsetMillis(snapshot.offsetMillis());
            row.setFileName(snapshot.fileName());
            row.setRelativePath(snapshot.relativePath());
            row.setWidth(imageSize.width());
            row.setHeight(imageSize.height());
            rows.add(row);
        }
        if (!rows.isEmpty()) {
            mapper.insertSnapshots(rows);
        }
        return videoId;
    }
}

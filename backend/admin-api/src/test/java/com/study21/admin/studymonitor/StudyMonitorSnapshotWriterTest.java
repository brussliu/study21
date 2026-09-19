package com.study21.admin.studymonitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link StudyMonitorSnapshotWriter} のテスト（2.0 {@code BatL02Task#moveSnapshotsToDateFolders} の移植）。
 *
 * <p>規則を固定する: 置き場は {@code <保存ルート>/<yyyyMMdd>/snapshot_%06d.jpg}、
 * 連番は「その日の 0 時からの秒 ÷ 切出間隔 + 1」、撮影日時は「撮影開始日時 + index × 切出間隔」、
 * DB に入れる 保存パス は保存ルートからの相対パス。</p>
 */
class StudyMonitorSnapshotWriterTest {

    private final VideoFileTools tools = new VideoFileTools("ffmpeg", "ffprobe");
    private final StudyMonitorSnapshotWriter writer = new StudyMonitorSnapshotWriter(tools);

    @Test
    void buildsCapturedAtOffsetAndSequence() throws Exception {
        Path root = Files.createTempDirectory("snap-root");
        Path staging = Files.createDirectories(root.resolve(".staging/cam"));
        writeStaged(staging, 3);
        VideoFileTools.VideoPeriod period =
                VideoFileTools.parseVideoPeriod("cam_20260726090000_20260726090300.mp4");

        List<StudyMonitorSnapshotWriter.StagedSnapshot> snapshots =
                writer.moveToDateFolders(root, period, staged(staging), 60);

        assertThat(snapshots).hasSize(3);
        // 09:00:00 → 9*3600/60 + 1 = 541
        assertThat(snapshots.get(0).fileName()).isEqualTo("snapshot_000541.jpg");
        assertThat(snapshots.get(0).capturedAt()).isEqualTo(LocalDateTime.of(2026, 7, 26, 9, 0));
        assertThat(snapshots.get(0).offsetMillis()).isZero();
        assertThat(snapshots.get(0).relativePath()).isEqualTo("20260726/snapshot_000541.jpg");
        assertThat(snapshots.get(1).capturedAt()).isEqualTo(LocalDateTime.of(2026, 7, 26, 9, 1));
        assertThat(snapshots.get(1).offsetMillis()).isEqualTo(60_000L);
        assertThat(snapshots.get(2).fileName()).isEqualTo("snapshot_000543.jpg");
        assertThat(snapshots.get(2).relativePath()).isEqualTo("20260726/snapshot_000543.jpg");
        // 実ファイルが移動している（staging には残らない）
        assertThat(Files.exists(root.resolve("20260726/snapshot_000541.jpg"))).isTrue();
        assertThat(Files.exists(staging.resolve("snapshot_000001.jpg"))).isFalse();
    }

    @Test
    void splitsSnapshotsOfVideoAcrossMidnight() throws Exception {
        Path root = Files.createTempDirectory("snap-root");
        Path staging = Files.createDirectories(root.resolve(".staging/cam"));
        writeStaged(staging, 4);
        VideoFileTools.VideoPeriod period =
                VideoFileTools.parseVideoPeriod("cam_20260726235800_20260727000300.mp4");

        List<StudyMonitorSnapshotWriter.StagedSnapshot> snapshots =
                writer.moveToDateFolders(root, period, staged(staging), 60);

        assertThat(snapshots).extracting(StudyMonitorSnapshotWriter.StagedSnapshot::relativePath)
                .containsExactly("20260726/snapshot_001439.jpg", "20260726/snapshot_001440.jpg",
                        "20260727/snapshot_000001.jpg", "20260727/snapshot_000002.jpg");
        assertThat(snapshots).extracting(StudyMonitorSnapshotWriter.StagedSnapshot::capturedAt)
                .containsExactly(
                        LocalDateTime.of(2026, 7, 26, 23, 58),
                        LocalDateTime.of(2026, 7, 26, 23, 59),
                        LocalDateTime.of(2026, 7, 27, 0, 0),
                        LocalDateTime.of(2026, 7, 27, 0, 1));
    }

    @Test
    void sequenceDependsOnInterval() throws Exception {
        Path root = Files.createTempDirectory("snap-root");
        Path staging = Files.createDirectories(root.resolve(".staging/cam"));
        writeStaged(staging, 1);
        VideoFileTools.VideoPeriod period =
                VideoFileTools.parseVideoPeriod("cam_20260726090000_20260726090500.mp4");

        List<StudyMonitorSnapshotWriter.StagedSnapshot> snapshots =
                writer.moveToDateFolders(root, period, staged(staging), 300);

        // 9*3600/300 + 1 = 109
        assertThat(snapshots.get(0).fileName()).isEqualTo("snapshot_000109.jpg");
    }

    @Test
    void buildsRelativePathFromSnapshotRoot() {
        Path root = Path.of("/data/snapshots");
        assertThat(StudyMonitorSnapshotWriter.toRelativePath(root, Path.of("/data/snapshots/20260726/a.jpg")))
                .isEqualTo("20260726/a.jpg");
        assertThat(StudyMonitorSnapshotWriter.toRelativePath(root, Path.of("/other/a.jpg")))
                .isEqualTo(Path.of("/other/a.jpg").toAbsolutePath().normalize().toString());
    }

    @Test
    void readsImageSize(@TempDir Path tempDirectory) throws Exception {
        Path image = tempDirectory.resolve("snapshot_000001.jpg");
        BufferedImage bufferedImage = new BufferedImage(1280, 720, BufferedImage.TYPE_INT_RGB);
        ImageIO.write(bufferedImage, "jpg", image.toFile());

        assertThat(writer.readImageSize(image)).isEqualTo(new VideoFileTools.ImageSize(1280, 720));
    }

    @Test
    void failsWhenImageCannotBeRead(@TempDir Path tempDirectory) throws Exception {
        Path notImage = tempDirectory.resolve("snapshot_000001.jpg");
        Files.writeString(notImage, "not an image");

        assertThatThrownBy(() -> writer.readImageSize(notImage))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("画像として読めません");
    }

    private static void writeStaged(Path staging, int count) throws IOException {
        BufferedImage image = new BufferedImage(640, 480, BufferedImage.TYPE_INT_RGB);
        for (int index = 1; index <= count; index++) {
            ImageIO.write(image, "jpg", staging.resolve("snapshot_%06d.jpg".formatted(index)).toFile());
        }
    }

    private static List<Path> staged(Path staging) throws IOException {
        try (var files = Files.list(staging)) {
            return files.filter(Files::isRegularFile).sorted().toList();
        }
    }
}

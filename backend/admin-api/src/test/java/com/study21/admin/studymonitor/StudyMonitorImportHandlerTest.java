package com.study21.admin.studymonitor;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.setting.SettingsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.stream.IntStream;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@link StudyMonitorImportHandler} の取込の流れ（2.0 BatL02Task の移植）のテスト。
 *
 * <p>実ファイル（{@code @TempDir}）で動かし、ffmpeg / ffprobe だけをスタブにする
 * （{@link VideoFileTools} を継承して差し替える。実プロセスは起動しない）。
 * DB は Mapper をインメモリの偽物にして確かめる（実 DB は使わない）。</p>
 */
class StudyMonitorImportHandlerTest {

    private static final String CAMERA_CODE = "XiaomiCamera_00_B88880D0F03E";
    private static final long MB = 1024L * 1024L;
    /** 最新動画の判定を通る大きさ（125MB より大きい）。 */
    private static final long BIG_VIDEO_BYTES = 200 * MB;
    /** 最終更新から 5 分より前に見える最終更新時刻。 */
    private static final FileTime OLD_ENOUGH =
            FileTime.from(Instant.now().minus(Duration.ofMinutes(30)));

    @TempDir
    Path tempDirectory;

    private Path sourceDirectory;
    private Path snapshotRoot;
    private StubVideoFileTools tools;
    private FakeMapper mapper;
    private SettingsService settingsService;

    @BeforeEach
    void setUp() throws IOException {
        sourceDirectory = Files.createDirectories(tempDirectory.resolve(CAMERA_CODE));
        snapshotRoot = Files.createDirectories(tempDirectory.resolve("snapshots"));
        tools = new StubVideoFileTools();
        mapper = new FakeMapper();
        settingsService = mock(SettingsService.class);
        when(settingsService.requireSettings(eq("batL02"), any())).thenReturn(settings());
    }

    private Map<String, String> settings() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY", sourceDirectory.toString());
        values.put("STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY", snapshotRoot.toString());
        values.put("STUDY_MONITOR_VIDEO_PROCESSING_START_TIME", "09:00");
        values.put("STUDY_MONITOR_VIDEO_PROCESSING_END_TIME", "23:59");
        values.put("STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS", "60");
        values.put("STUDY_MONITOR_CAMERA_LOCATION", "自習室の学習机正面");
        return values;
    }

    private StudyMonitorImportHandler handler() {
        return new StudyMonitorImportHandler(mapper, settingsService, tools,
                new StudyMonitorSnapshotWriter(tools), new StudyMonitorRecordWriter(mapper),
                snapshotRoot.toString());
    }

    private String execute() throws Exception {
        return handler().execute(new BatchExecutionEntity());
    }

    // ---------------------------------------------------------------- 正常系

    @Test
    void importsOneVideoAndExtractsSnapshots() throws Exception {
        // 09:00:00 の動画から 3 枚（09:00 / 09:01 / 09:02）
        createSourceVideo("cam_20260726090000_20260726090300.mp4", BIG_VIDEO_BYTES);
        tools.snapshotCount = 3;

        String message = execute();

        // 動画は checked/ へ移っている
        assertThat(Files.exists(sourceDirectory.resolve("checked/cam_20260726090000_20260726090300.mp4")))
                .isTrue();
        assertThat(Files.exists(sourceDirectory.resolve("cam_20260726090000_20260726090300.mp4"))).isFalse();

        // スナップショットは日付フォルダーへ snapshot_%06d.jpg で置かれる
        assertThat(directoryNames(snapshotRoot)).containsExactly("20260726");
        assertThat(fileNames(snapshotRoot.resolve("20260726")))
                .containsExactly("snapshot_000541.jpg", "snapshot_000542.jpg", "snapshot_000543.jpg");

        // 撮影日時 = 開始 + index × 間隔、オフセット = index × 間隔、保存パス = 保存ルートからの相対パス
        FakeMapper.RecordedVideo video = mapper.videos.get(0);
        assertThat(video.cameraId).isEqualTo(1L);
        assertThat(video.capturedDate).isEqualTo(java.time.LocalDate.of(2026, 7, 26));
        assertThat(video.startedAt).isEqualTo(LocalDateTime.of(2026, 7, 26, 9, 0));
        assertThat(video.endedAt).isEqualTo(LocalDateTime.of(2026, 7, 26, 9, 3));
        assertThat(video.fileName).isEqualTo("cam_20260726090000_20260726090300.mp4");
        assertThat(video.durationSeconds).isEqualTo(180L);
        assertThat(video.fileSize).isEqualTo(BIG_VIDEO_BYTES);
        assertThat(mapper.snapshots).hasSize(3);
        assertThat(mapper.snapshots.get(0).capturedAt).isEqualTo(LocalDateTime.of(2026, 7, 26, 9, 0));
        assertThat(mapper.snapshots.get(1).capturedAt).isEqualTo(LocalDateTime.of(2026, 7, 26, 9, 1));
        assertThat(mapper.snapshots.get(2).capturedAt).isEqualTo(LocalDateTime.of(2026, 7, 26, 9, 2));
        assertThat(mapper.snapshots).extracting(s -> s.offsetMillis).containsExactly(0L, 60_000L, 120_000L);
        assertThat(mapper.snapshots).extracting(s -> s.fileName)
                .containsExactly("snapshot_000541.jpg", "snapshot_000542.jpg", "snapshot_000543.jpg");
        assertThat(mapper.snapshots).extracting(s -> s.relativePath)
                .containsExactly("20260726/snapshot_000541.jpg", "20260726/snapshot_000542.jpg",
                        "20260726/snapshot_000543.jpg");
        // 幅・高さは切り出した画像から読む
        assertThat(mapper.snapshots).extracting(s -> s.width).containsOnly(640);
        assertThat(mapper.snapshots).extracting(s -> s.height).containsOnly(480);

        assertThat(message).contains("取込=1 件").contains("失敗=0 件").contains("09:00-23:59");
    }

    @Test
    void removesStagingDirectory() throws Exception {
        createSourceVideo("cam_20260726090000_20260726090100.mp4", BIG_VIDEO_BYTES);
        tools.snapshotCount = 2;

        execute();

        assertThat(Files.exists(snapshotRoot.resolve(".staging")
                .resolve("cam_20260726090000_20260726090100"))).isFalse();
    }

    @Test
    void splitsSnapshotsOfVideoAcrossMidnight() throws Exception {
        // 23:58 開始、翌 00:03 終了。09:00-23:59 の窓と重なる（終了 00:03 は窓外だが開始が窓内）
        createSourceVideo("cam_20260726235800_20260727000300.mp4", BIG_VIDEO_BYTES);
        tools.snapshotCount = 4;

        execute();

        assertThat(directoryNames(snapshotRoot)).containsExactlyInAnyOrder("20260726", "20260727");
        assertThat(mapper.snapshots).extracting(s -> s.relativePath)
                .containsExactly("20260726/snapshot_001439.jpg", "20260726/snapshot_001440.jpg",
                        "20260727/snapshot_000001.jpg", "20260727/snapshot_000002.jpg");
    }

    // ---------------------------------------------------------------- 窓の外は bak/ へ

    @Test
    void archivesVideoOutsideWindowToBak() throws Exception {
        Path video = createSourceVideo("cam_20260726050000_20260726053000.mp4", BIG_VIDEO_BYTES);

        String message = execute();

        assertThat(Files.exists(sourceDirectory.resolve("bak").resolve(video.getFileName()))).isTrue();
        assertThat(mapper.videos).isEmpty();
        assertThat(tools.extractedVideos).isEmpty();
        assertThat(message).contains("取込=0 件").contains("対象外/取込済=1 件");
    }

    @Test
    void archivesInvalidFileNameAndRecordsFailure() throws Exception {
        Path video = createSourceVideo("broken-name.mp4", BIG_VIDEO_BYTES);

        String message = execute();

        assertThat(Files.exists(sourceDirectory.resolve("bak").resolve(video.getFileName()))).isTrue();
        assertThat(mapper.videos).isEmpty();
        assertThat(message).contains("失敗=1 件").contains("対応していない動画ファイル名");
    }

    // ---------------------------------------------------------------- checked/ を先に処理する

    @Test
    void processesCheckedBeforeSource() throws Exception {
        Path checkedDirectory = Files.createDirectories(sourceDirectory.resolve("checked"));
        // checked は 10:00 の動画、ソースは 11:00 の動画（どちらも窓の中）
        createVideo(checkedDirectory.resolve("cam_20260726100000_20260726100100.mp4"), BIG_VIDEO_BYTES);
        createSourceVideo("cam_20260726110000_20260726110100.mp4", BIG_VIDEO_BYTES);
        tools.snapshotCount = 1;

        execute();

        assertThat(mapper.videos).extracting(v -> v.fileName)
                .containsExactly("cam_20260726100000_20260726100100.mp4",
                        "cam_20260726110000_20260726110100.mp4");
    }

    @Test
    void archivesCheckedVideoOutsideWindow() throws Exception {
        Path checkedDirectory = Files.createDirectories(sourceDirectory.resolve("checked"));
        createVideo(checkedDirectory.resolve("cam_20260726030000_20260726033000.mp4"), BIG_VIDEO_BYTES);

        execute();

        assertThat(Files.exists(checkedDirectory.resolve("bak/cam_20260726030000_20260726033000.mp4"))).isTrue();
        assertThat(Files.exists(checkedDirectory.resolve("cam_20260726030000_20260726033000.mp4"))).isFalse();
        assertThat(mapper.videos).isEmpty();
    }

    @Test
    void skipsAlreadyImportedCheckedVideo() throws Exception {
        Path checkedDirectory = Files.createDirectories(sourceDirectory.resolve("checked"));
        Path video = createVideo(checkedDirectory.resolve("cam_20260726100000_20260726100100.mp4"), BIG_VIDEO_BYTES);
        mapper.videos.add(FakeMapper.RecordedVideo.of(1L, video.getFileName().toString()));

        String message = execute();

        assertThat(Files.exists(video)).isTrue();
        assertThat(mapper.recordedVideos).isEmpty();
        assertThat(message).contains("対象外/取込済=1 件");
    }

    @Test
    void skipsAlreadyImportedSourceVideo() throws Exception {
        Path video = createSourceVideo("cam_20260726100000_20260726100100.mp4", BIG_VIDEO_BYTES);
        mapper.videos.add(FakeMapper.RecordedVideo.of(1L, video.getFileName().toString()));

        String message = execute();

        assertThat(Files.exists(video)).isTrue();
        assertThat(mapper.recordedVideos).isEmpty();
        assertThat(message).contains("対象外/取込済=1 件");
    }

    // ---------------------------------------------------------------- 最新動画のスキップ

    @Test
    void skipsNewestSourceVideoWhenSmall() throws Exception {
        createSourceVideo("cam_20260726110000_20260726110100.mp4", 10 * MB);
        // 2 番目に新しい 10:00 の動画は取り込む
        createSourceVideo("cam_20260726100000_20260726100100.mp4", BIG_VIDEO_BYTES);
        tools.snapshotCount = 1;

        execute();

        assertThat(mapper.videos).extracting(v -> v.fileName)
                .containsExactly("cam_20260726100000_20260726100100.mp4");
        assertThat(Files.exists(sourceDirectory.resolve("cam_20260726110000_20260726110100.mp4"))).isTrue();
    }

    @Test
    void skipsNewestSourceVideoWhenJustModified() throws Exception {
        Path newest = createSourceVideo("cam_20260726110000_20260726110100.mp4", BIG_VIDEO_BYTES);
        Files.setLastModifiedTime(newest, FileTime.from(Instant.now()));
        createVideo(sourceDirectory.resolve("cam_20260726100000_20260726100100.mp4"), BIG_VIDEO_BYTES);
        tools.snapshotCount = 1;

        execute();

        assertThat(mapper.videos).extracting(v -> v.fileName)
                .containsExactly("cam_20260726100000_20260726100100.mp4");
        assertThat(Files.exists(newest)).isTrue();
    }

    @Test
    void importsNewestSourceVideoWhenBigAndOld() throws Exception {
        createSourceVideo("cam_20260726110000_20260726110100.mp4", BIG_VIDEO_BYTES);
        tools.snapshotCount = 1;

        execute();

        assertThat(mapper.videos).extracting(v -> v.fileName)
                .containsExactly("cam_20260726110000_20260726110100.mp4");
    }

    // ---------------------------------------------------------------- 1 回の上限

    @Test
    void importsAtMostFiveVideosPerRun() throws Exception {
        // 最新（11:06）が大きい＝取り込む対象。11:01〜11:05 の 5 本も窓の中。
        // 合計 6 本が候補になるが、取り込むのは 5 本まで。
        for (int minute : IntStream.rangeClosed(1, 6).toArray()) {
            createSourceVideo("cam_2026072611%02d00_2026072611%02d59.mp4".formatted(minute, minute),
                    BIG_VIDEO_BYTES);
        }
        tools.snapshotCount = 1;

        String message = execute();

        assertThat(mapper.videos).hasSize(5);
        assertThat(message).contains("取込=5 件");
        // 一番古い 11:01 は次回に残る
        assertThat(Files.exists(sourceDirectory.resolve("cam_20260726110100_20260726110159.mp4"))).isTrue();
        assertThat(Files.list(sourceDirectory.resolve("checked")).count()).isEqualTo(5);
    }

    // ---------------------------------------------------------------- カメラの upsert

    @Test
    void upsertsCameraBeforeImport() throws Exception {
        createSourceVideo("cam_20260726100000_20260726100100.mp4", BIG_VIDEO_BYTES);
        tools.snapshotCount = 1;

        execute();

        assertThat(mapper.cameras).hasSize(1);
        StudyMonitorCameraEntity camera = mapper.cameras.get(0);
        assertThat(camera.getCameraCode()).isEqualTo(CAMERA_CODE);
        assertThat(camera.getCameraName()).isEqualTo("Xiaomi Camera 00");
        assertThat(camera.getLocation()).isEqualTo("自習室の学習机正面");
        assertThat(camera.getSnapshotIntervalSeconds()).isEqualTo(60);
        assertThat(mapper.upsertedCameraCodes).containsExactly(CAMERA_CODE);
    }

    @Test
    void failsWhenCameraIdIsMissing() {
        mapper.cameraId = null;

        assertThatThrownBy(this::execute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("カメラを登録できませんでした");
    }

    // ---------------------------------------------------------------- 設定と前提の異常

    @Test
    void returnsMessageWhenSourceDirectoryIsMissing() throws Exception {
        Map<String, String> values = settings();
        values.put("STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY", tempDirectory.resolve("no-such-dir").toString());
        when(settingsService.requireSettings(eq("batL02"), any())).thenReturn(values);

        String message = execute();

        assertThat(message).contains("動画のソースフォルダーがありません");
        assertThat(mapper.upsertedCameraCodes).isEmpty();
    }

    @Test
    void rejectsSourceDirectoryWithoutCameraCode() {
        // カメラコードはソースフォルダー名から決めるので、ルートは指定できない
        Map<String, String> values = settings();
        values.put("STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY", "/");
        when(settingsService.requireSettings(eq("batL02"), any())).thenReturn(values);

        assertThatThrownBy(this::execute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ソースフォルダー名からカメラコードを決められません");
    }

    @Test
    void failsWhenSettingsAreMissing() {
        when(settingsService.requireSettings(eq("batL02"), any()))
                .thenThrow(new IllegalStateException("設定が足りません"));

        assertThatThrownBy(this::execute).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsIntervalOutOfRange() {
        Map<String, String> values = settings();
        values.put("STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS", "0");
        when(settingsService.requireSettings(eq("batL02"), any())).thenReturn(values);

        assertThatThrownBy(this::execute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("1〜3600");
    }

    @Test
    void rejectsInvalidProcessingTime() {
        Map<String, String> values = settings();
        values.put("STUDY_MONITOR_VIDEO_PROCESSING_START_TIME", "9時");
        when(settingsService.requireSettings(eq("batL02"), any())).thenReturn(values);

        assertThatThrownBy(this::execute)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("HH:mm");
    }

    @Test
    void recordsFailureWhenNoSnapshotExtracted() throws Exception {
        Path video = createSourceVideo("cam_20260726100000_20260726100100.mp4", BIG_VIDEO_BYTES);
        tools.snapshotCount = 0;

        String message = execute();

        assertThat(message).contains("失敗=1 件").contains("1 枚も切り出しませんでした");
        assertThat(Files.exists(sourceDirectory.resolve("checked").resolve(video.getFileName()))).isTrue();
        assertThat(mapper.recordedVideos).isEmpty();
    }

    @Test
    void recordsFailureWhenFfmpegLeavesNoOutputDirectory() throws Exception {
        Path video = createSourceVideo("cam_20260726100000_20260726100100.mp4", BIG_VIDEO_BYTES);
        tools.skipOutputDirectory = true;

        String message = execute();

        assertThat(message).contains("失敗=1 件").contains("ffmpeg の出力フォルダーがありません");
        assertThat(Files.exists(sourceDirectory.resolve("checked").resolve(video.getFileName()))).isTrue();
    }

    @Test
    void retriesFailedVideoOnNextRun() throws Exception {
        Path video = createSourceVideo("cam_20260726100000_20260726100100.mp4", BIG_VIDEO_BYTES);
        tools.failExtraction = true;

        String message = execute();

        assertThat(message).contains("失敗=1 件").contains("ffmpeg が失敗");
        Path checked = sourceDirectory.resolve("checked").resolve(video.getFileName());
        assertThat(Files.exists(checked)).isTrue();
        // 次の実行では checked から拾ってやり直す
        tools.failExtraction = false;
        tools.snapshotCount = 1;
        String second = execute();
        assertThat(second).contains("取込=1 件");
        assertThat(mapper.videos).extracting(v -> v.fileName).containsExactly(video.getFileName().toString());
    }

    // ---------------------------------------------------------------- 2.0 の保存先設定との食い違い

    @Test
    void notifiesSnapshotRootDifferenceOnce() {
        // ログの中身までは見ないが、起動時の知らせが例外にならないこと（1 回だけ実行されること）を確かめる
        StudyMonitorImportHandler handler = handler();
        handler.notifySnapshotRootOnce();
        handler.notifySnapshotRootOnce();
    }

    @Test
    void survivesUnreadableSettingsOnStartupNotice() {
        when(settingsService.requireSettings(eq("batL02"), any()))
                .thenThrow(new IllegalStateException("設定がありません"));

        handler().notifySnapshotRootOnce();
    }

    @Test
    void keepsAbsolutePathWhenVideoIsOutsideRoot(@TempDir Path outside) {
        assertThat(StudyMonitorSnapshotWriter.toRelativePath(snapshotRoot, snapshotRoot.resolve("20260726/a.jpg")))
                .isEqualTo("20260726/a.jpg");
        assertThat(StudyMonitorSnapshotWriter.toRelativePath(snapshotRoot, outside.resolve("a.jpg")))
                .isEqualTo(outside.resolve("a.jpg").toAbsolutePath().normalize().toString());
    }

    // ---------------------------------------------------------------- テスト用の道具

    private Path createSourceVideo(String fileName, long size) throws IOException {
        return createVideo(sourceDirectory.resolve(fileName), size);
    }

    private Path createVideo(Path path, long size) throws IOException {
        Files.createDirectories(path.getParent());
        byte[] content = new byte[1024];
        try (java.nio.channels.FileChannel channel = java.nio.channels.FileChannel.open(path,
                java.nio.file.StandardOpenOption.CREATE, java.nio.file.StandardOpenOption.WRITE)) {
            long remaining = size;
            while (remaining > 0) {
                int length = (int) Math.min(content.length, remaining);
                channel.write(java.nio.ByteBuffer.wrap(content, 0, length));
                remaining -= length;
            }
        }
        Files.setLastModifiedTime(path, OLD_ENOUGH);
        return path;
    }

    private static List<String> directoryNames(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .filter(name -> !name.startsWith("."))
                    .sorted()
                    .toList();
        }
    }

    private static List<String> fileNames(Path directory) throws IOException {
        try (var files = Files.list(directory)) {
            return files.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    /** ffmpeg / ffprobe の代わり。実際のプロセスは起動しない。 */
    private static final class StubVideoFileTools extends VideoFileTools {

        /** 1 本の動画から切り出す枚数（実 ffmpeg の fps=1/間隔 の代わり）。 */
        private int snapshotCount = 1;
        /** true にすると切出が失敗する（ffmpeg の異常終了の代わり）。 */
        private boolean failExtraction;
        /** true にすると出力フォルダーを作らない（ffmpeg が何も残さなかったときの代わり）。 */
        private boolean skipOutputDirectory;
        private final List<Path> extractedVideos = new ArrayList<>();

        StubVideoFileTools() {
            super("ffmpeg", "ffprobe");
        }

        @Override
        public long probeDurationSeconds(Path video) {
            return 180L;
        }

        @Override
        public void extractSnapshots(Path video, Path outputDirectory, int snapshotIntervalSeconds)
                throws IOException {
            if (failExtraction) {
                throw new IllegalStateException("ffmpeg が失敗しました（テスト）");
            }
            if (skipOutputDirectory) {
                return;
            }
            extractedVideos.add(video);
            Files.createDirectories(outputDirectory);
            for (int index = 1; index <= snapshotCount; index++) {
                writeJpeg(outputDirectory.resolve("snapshot_%06d.jpg".formatted(index)), 640, 480);
            }
        }

        @Override
        public ImageSize readImageSize(Path image) throws IOException {
            if (!Files.exists(image)) {
                throw new IOException("切り出したスナップショットがありません: " + image);
            }
            return new ImageSize(640, 480);
        }

        private static void writeJpeg(Path path, int width, int height) throws IOException {
            BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
            ImageIO.write(image, "jpg", path.toFile());
        }
    }

    /** DB の代わり（SQL は走らせない）。 */
    private static final class FakeMapper implements StudyMonitorImportMapper {

        private final Map<String, StudyMonitorCameraEntity> cameraRows = new HashMap<>();
        private final List<StudyMonitorCameraEntity> cameras = new ArrayList<>();
        private final List<String> upsertedCameraCodes = new ArrayList<>();
        private final List<RecordedVideo> videos = new ArrayList<>();
        private final List<RecordedVideo> recordedVideos = new ArrayList<>();
        private final List<RecordedSnapshot> snapshots = new ArrayList<>();
        private Long cameraId = 1L;
        private long nextVideoId = 100L;

        @Override
        public int upsertCamera(StudyMonitorCameraEntity camera) {
            cameraRows.put(camera.getCameraCode(), camera);
            cameras.add(camera);
            upsertedCameraCodes.add(camera.getCameraCode());
            return 1;
        }

        @Override
        public Long findCameraId(String cameraCode) {
            return cameraRows.containsKey(cameraCode) ? cameraId : null;
        }

        @Override
        public boolean existsVideo(long cameraId, String fileName) {
            return videos.stream().anyMatch(v -> v.cameraId == cameraId && v.fileName.equals(fileName));
        }

        @Override
        public int insertVideo(StudyMonitorVideoEntity video) {
            RecordedVideo recorded = RecordedVideo.of(video.getCameraId(), video.getFileName());
            recorded.videoId = nextVideoId++;
            recorded.capturedDate = video.getCapturedDate();
            recorded.startedAt = video.getStartedAt();
            recorded.endedAt = video.getEndedAt();
            recorded.relativePath = video.getRelativePath();
            recorded.durationSeconds = video.getDurationSeconds();
            recorded.fileSize = video.getFileSize();
            videos.add(recorded);
            recordedVideos.add(recorded);
            // MyBatis の useGeneratedKeys の代わり
            video.setVideoId(recorded.videoId);
            return 1;
        }

        @Override
        public int insertSnapshots(List<StudyMonitorSnapshotEntity> rows) {
            for (StudyMonitorSnapshotEntity row : rows) {
                RecordedSnapshot snapshot = new RecordedSnapshot();
                snapshot.videoId = row.getVideoId();
                snapshot.capturedAt = row.getCapturedAt();
                snapshot.offsetMillis = row.getOffsetMillis();
                snapshot.fileName = row.getFileName();
                snapshot.relativePath = row.getRelativePath();
                snapshot.width = row.getWidth();
                snapshot.height = row.getHeight();
                snapshots.add(snapshot);
            }
            return rows.size();
        }

        private static final class RecordedVideo {
            private long videoId;
            private long cameraId;
            private java.time.LocalDate capturedDate;
            private LocalDateTime startedAt;
            private LocalDateTime endedAt;
            private String fileName;
            private String relativePath;
            private Long durationSeconds;
            private Long fileSize;

            static RecordedVideo of(long cameraId, String fileName) {
                RecordedVideo video = new RecordedVideo();
                video.cameraId = cameraId;
                video.fileName = fileName;
                return video;
            }
        }

        private static final class RecordedSnapshot {
            private Long videoId;
            private LocalDateTime capturedAt;
            private Long offsetMillis;
            private String fileName;
            private String relativePath;
            private Integer width;
            private Integer height;
        }
    }
}

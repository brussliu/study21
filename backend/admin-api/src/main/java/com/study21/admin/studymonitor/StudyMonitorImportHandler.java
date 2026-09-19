package com.study21.admin.studymonitor;

import com.study21.admin.batch.BatchExecutionEntity;
import com.study21.admin.batch.BatchTaskHandler;
import com.study21.admin.setting.SettingRequirement;
import com.study21.admin.setting.SettingsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * batL02「学習モニター動画取込・スナップショット切出」の業務処理（2.0 の BatL02Task の移植）。
 *
 * <p>1 回の実行でやること（2.0 と同じ流れ）:</p>
 * <ol>
 *   <li>設定を解決する（{@link #REQUIRED_SETTINGS}。**コード既定値は持たない**）</li>
 *   <li>カメラを upsert して カメラID を得る（カメラは 1 台。カメラコード = ソースフォルダー名）</li>
 *   <li><b>checked/ を先に</b>処理する（前回失敗して残った動画のやり直し）</li>
 *   <li>ソースフォルダーの動画を処理する。ただし最新の 1 本は「125MB より大きく、
 *       最終更新から 5 分以上」のときだけ取り込む（まだ書き込み中の可能性があるため）</li>
 *   <li>撮影時間帯（ファイル名の撮影開始〜終了）が処理対象時間帯と重ならない動画は
 *       取り込まずに bak/ へ移す（ファイル名が不正な動画も同じく bak/ へ）</li>
 *   <li>既に取り込んだ動画（カメラID + 動画ファイル名）は飛ばす</li>
 *   <li>取り込む動画は checked/ へ移してから、スナップショットを切り出して登録する
 *       （失敗したら checked/ に残るので次の実行でやり直せる）</li>
 *   <li>1 回の実行で取り込むのは最大 5 本</li>
 * </ol>
 *
 * <p>2.0 からの違いは <b>保存ルートの決め方だけ</b>。切出先は 2.0 の設定
 * {@code STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY} ではなく、2.1 の
 * {@code study21.study-monitor.snapshot-root}（user-api が画像を配信するルート）を使う。
 * DB の 保存パス はそのルートからの相対パスになる。</p>
 */
@Component
public class StudyMonitorImportHandler implements BatchTaskHandler {

    private static final Logger log = LoggerFactory.getLogger(StudyMonitorImportHandler.class);

    /** 2.0 から引き継ぐバッチコード。 */
    public static final String BATCH_CODE = "batL02";

    /** 1 回の実行で取り込む動画の上限（2.0 と同じ 5 本）。 */
    static final int MAX_FILES_PER_RUN = 5;

    /** カメラ名称。2.1 の設定キーにはカメラ名称が無いので 2.0 と同じ固定文言を使う。 */
    static final String CAMERA_NAME = "Xiaomi Camera 00";

    /** 処理対象時間帯の書式（設定の 有効値 HH:mm）。 */
    private static final DateTimeFormatter PROCESSING_TIME_FORMAT = DateTimeFormatter.ofPattern("HH:mm");

    /** 切り出した画像の一時置き場（保存ルートの中。日付フォルダーと紛れないよう . で始める）。 */
    private static final String STAGING_DIRECTORY = ".staging";

    /** 取り込み済みの動画を移すフォルダー。 */
    static final String CHECKED_DIRECTORY = "checked";

    /** 処理対象外の動画を移すフォルダー。 */
    static final String BAK_DIRECTORY = "bak";

    /** 識別子（設定ページ区分）。 */
    private static final String PAGE_CODE = "STUDY_MONITOR";

    /**
     * batL02 の必須設定（既定値はコードに持たない。未設定なら {@link SettingsService} が例外にする）。
     *
     * <p>{@code STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY} は 2.0 の**絶対パス**の設定。
     * 2.1 ではこの設定を使わず {@code study21.study-monitor.snapshot-root} を使うが、
     * 設定としては必須のまま（{@code BatchTaskRegistry} の宣言と揃える）なので要求し続け、
     * 実際の値と食い違っていれば警告する。</p>
     */
    public static final List<SettingRequirement> REQUIRED_SETTINGS = List.of(
            new SettingRequirement(PAGE_CODE, "STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY"),
            new SettingRequirement(PAGE_CODE, "STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY"),
            new SettingRequirement(PAGE_CODE, "STUDY_MONITOR_VIDEO_PROCESSING_START_TIME"),
            new SettingRequirement(PAGE_CODE, "STUDY_MONITOR_VIDEO_PROCESSING_END_TIME"),
            new SettingRequirement(PAGE_CODE, "STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS"),
            new SettingRequirement(PAGE_CODE, "STUDY_MONITOR_CAMERA_LOCATION"));

    private final StudyMonitorImportMapper mapper;
    private final SettingsService settingsService;
    private final VideoFileTools videoFileTools;
    private final StudyMonitorSnapshotWriter snapshotWriter;
    private final StudyMonitorRecordWriter recordWriter;
    private final Path snapshotRoot;
    /** 起動時の一度だけ知らせるための印（食い違いの警告を毎回出さない）。 */
    private final AtomicBoolean startupNoticeLogged = new AtomicBoolean(false);

    public StudyMonitorImportHandler(StudyMonitorImportMapper mapper,
                                     SettingsService settingsService,
                                     VideoFileTools videoFileTools,
                                     StudyMonitorSnapshotWriter snapshotWriter,
                                     StudyMonitorRecordWriter recordWriter,
                                     @Value("${study21.study-monitor.snapshot-root:${user.dir}/data/snapshots}")
                                     String snapshotRoot) {
        this.mapper = mapper;
        this.settingsService = settingsService;
        this.videoFileTools = videoFileTools;
        this.snapshotWriter = snapshotWriter;
        this.recordWriter = recordWriter;
        this.snapshotRoot = Path.of(snapshotRoot).toAbsolutePath().normalize();
    }

    @Override
    public String taskCode() {
        return BATCH_CODE;
    }

    @Override
    public String execute(BatchExecutionEntity execution) throws Exception {
        Map<String, String> values = settingsService.requireSettings(BATCH_CODE, REQUIRED_SETTINGS);
        RuntimeSettings settings = toRuntimeSettings(values);

        Path videoDirectory = settings.videoDirectory();
        if (!Files.isDirectory(videoDirectory)) {
            String message = "動画のソースフォルダーがありません: " + videoDirectory;
            log.warn("{} を実行できません。{}", BATCH_CODE, message);
            return message;
        }
        Files.createDirectories(snapshotRoot);

        long cameraId = ensureCamera(settings);
        Counters counters = new Counters();
        List<String> failures = new ArrayList<>();
        LocalTime windowStart = settings.windowStart();
        LocalTime windowEnd = settings.windowEnd();

        // (1) checked/ を先に処理する（前回失敗して残った動画のやり直し）
        Path checkedDirectory = videoDirectory.resolve(CHECKED_DIRECTORY).toAbsolutePath().normalize();
        List<Path> checkedVideos = Files.isDirectory(checkedDirectory)
                ? listVideoFiles(checkedDirectory) : List.of();
        log.info("{} の取込を開始します。source={} checked={} snapshotRoot={} 処理対象時間帯={}-{} 切出間隔={}秒",
                BATCH_CODE, videoDirectory, checkedVideos.size(), snapshotRoot,
                formatTime(windowStart), formatTime(windowEnd), settings.snapshotIntervalSeconds());
        for (Path video : checkedVideos) {
            if (counters.imported >= MAX_FILES_PER_RUN) {
                break;
            }
            if (mapper.existsVideo(cameraId, video.getFileName().toString())) {
                counters.skipped++;
                continue;
            }
            VideoFileTools.VideoPeriod period;
            try {
                period = VideoFileTools.parseVideoPeriod(video);
            } catch (RuntimeException e) {
                // 前回はファイル名が正しいまま checked/ へ移しているので、ここへは来ないはず
                fail(failures, video, e, "checked の動画のファイル名を解析できません。");
                continue;
            }
            if (!VideoFileTools.overlapsProcessingWindow(period, windowStart, windowEnd)) {
                Path archived = archiveToBak(failures, video);
                counters.skipped++;
                log.info("処理対象時間帯の外なので bak/ へ移しました。 taskCode={} source={} target={} 時間帯={}-{}",
                        BATCH_CODE, video, archived, formatTime(windowStart), formatTime(windowEnd));
                continue;
            }
            try {
                importVideo(cameraId, video, period, settings);
                counters.imported++;
            } catch (Exception e) {
                fail(failures, video, e, "checked の動画の取込に失敗しました。");
            }
        }

        // (2) ソースフォルダーの動画を処理する
        List<Path> queue = new ArrayList<>(listVideoFiles(videoDirectory));
        if (!queue.isEmpty()) {
            Path newest = queue.get(0);
            if (videoFileTools.shouldProcessNewestVideo(newest)) {
                log.info("最新のソース動画を取り込みます（125MB より大きく、最終更新から 5 分以上）。 taskCode={} file={}",
                        BATCH_CODE, newest);
            } else {
                queue.remove(0);
                log.info("最新のソース動画はまだ書き込み中の可能性があるため今回も飛ばします。 taskCode={} file={}",
                        BATCH_CODE, newest);
            }
        }
        for (Path video : queue) {
            if (counters.imported >= MAX_FILES_PER_RUN) {
                break;
            }
            VideoFileTools.VideoPeriod period;
            try {
                period = VideoFileTools.parseVideoPeriod(video);
            } catch (RuntimeException e) {
                // ファイル名が不正な動画は取り込めないので、処理対象外として bak/ へ移す（次回も同じ結果になる）
                fail(failures, video, e, "動画のファイル名を解析できません。");
                archiveToBak(failures, video);
                continue;
            }
            if (!VideoFileTools.overlapsProcessingWindow(period, windowStart, windowEnd)) {
                Path archived = archiveToBak(failures, video);
                counters.skipped++;
                log.info("処理対象時間帯の外なので bak/ へ移しました。 taskCode={} source={} target={} 時間帯={}-{}",
                        BATCH_CODE, video, archived, formatTime(windowStart), formatTime(windowEnd));
                continue;
            }
            if (mapper.existsVideo(cameraId, video.getFileName().toString())) {
                counters.skipped++;
                continue;
            }
            try {
                Path checkedVideo = moveToDirectory(CHECKED_DIRECTORY, video);
                importVideo(cameraId, checkedVideo, period, settings);
                counters.imported++;
            } catch (Exception e) {
                fail(failures, video, e, "動画の取込に失敗しました。");
            }
        }

        String message = summary(counters, failures, windowStart, windowEnd);
        log.info(message);
        return message;
    }

    /**
     * 起動時に一度だけ、2.0 の設定と 2.1 の保存ルートの食い違いを知らせる。
     *
     * <p>設定の読込に失敗しても起動は止めない（実行時に {@code requireSettings} が改めて検証する）。</p>
     */
    @EventListener(ApplicationReadyEvent.class)
    public void notifySnapshotRootOnce() {
        if (!startupNoticeLogged.compareAndSet(false, true)) {
            return;
        }
        try {
            Map<String, String> values = settingsService.requireSettings(BATCH_CODE, REQUIRED_SETTINGS);
            warnIfSnapshotRootDiffers(values);
        } catch (RuntimeException e) {
            log.debug("起動時の {} の設定確認を飛ばしました。 error={}", BATCH_CODE, e.toString());
        }
    }

    /** 設定 → 実行時の値。既定値は持たず、値が無い・不正なら例外にする。 */
    private RuntimeSettings toRuntimeSettings(Map<String, String> values) {
        warnIfSnapshotRootDiffers(values);
        String source = requireValue(values, "STUDY_MONITOR_VIDEO_SOURCE_DIRECTORY");
        String location = requireValue(values, "STUDY_MONITOR_CAMERA_LOCATION");
        int intervalSeconds = parseSnapshotInterval(
                requireValue(values, "STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS"));
        LocalTime windowStart = parseTime(requireValue(values, "STUDY_MONITOR_VIDEO_PROCESSING_START_TIME"),
                "STUDY_MONITOR_VIDEO_PROCESSING_START_TIME");
        LocalTime windowEnd = parseTime(requireValue(values, "STUDY_MONITOR_VIDEO_PROCESSING_END_TIME"),
                "STUDY_MONITOR_VIDEO_PROCESSING_END_TIME");
        return new RuntimeSettings(Path.of(source).toAbsolutePath().normalize(),
                location, intervalSeconds, windowStart, windowEnd);
    }

    /** 2.0 の設定（絶対パス）と 2.1 の保存ルートが食い違っていれば警告する。 */
    private void warnIfSnapshotRootDiffers(Map<String, String> values) {
        String configured = values.get("STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY");
        if (configured == null || configured.isBlank()) {
            return;
        }
        Path legacy = Path.of(configured.trim()).toAbsolutePath().normalize();
        if (!legacy.equals(snapshotRoot)) {
            log.warn("設定 STUDY_MONITOR_SNAPSHOT_OUTPUT_DIRECTORY（2.0 の絶対パス）={} と"
                            + " study21.study-monitor.snapshot-root={} が違います。"
                            + " batL02 は snapshot-root に切り出し、DB の 保存パス にはその相対パスを入れます。",
                    legacy, snapshotRoot);
        }
    }

    private long ensureCamera(RuntimeSettings settings) {
        StudyMonitorCameraEntity camera = new StudyMonitorCameraEntity();
        camera.setCameraCode(settings.cameraCode());
        camera.setCameraName(CAMERA_NAME);
        camera.setLocation(settings.cameraLocation());
        camera.setSnapshotIntervalSeconds(settings.snapshotIntervalSeconds());
        mapper.upsertCamera(camera);
        Long cameraId = mapper.findCameraId(settings.cameraCode());
        if (cameraId == null) {
            throw new IllegalStateException("カメラを登録できませんでした: " + settings.cameraCode());
        }
        return cameraId;
    }

    /**
     * 動画 1 本を取り込む（2.0 の {@code importVideo} と同じ流れ）。
     *
     * <p>スナップショットの切出 → 日付フォルダーへ移動 → 動画とスナップショットの登録。
     * 失敗したら例外を投げる（動画ファイルは {@code checked/} に残る）。</p>
     */
    private void importVideo(long cameraId, Path video, VideoFileTools.VideoPeriod period,
                             RuntimeSettings settings) throws Exception {
        long durationSeconds = videoFileTools.probeDurationSeconds(video);
        long fileSize = Files.size(video);

        Path stagingDirectory = snapshotRoot.resolve(STAGING_DIRECTORY)
                .resolve(VideoFileTools.stripExtension(video.getFileName().toString()));
        videoFileTools.extractSnapshots(video, stagingDirectory, settings.snapshotIntervalSeconds());
        if (!Files.isDirectory(stagingDirectory)) {
            throw new IllegalStateException("ffmpeg の出力フォルダーがありません: " + stagingDirectory);
        }

        List<Path> staged;
        try (var files = Files.list(stagingDirectory)) {
            staged = files.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".jpg"))
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
        }
        if (staged.isEmpty()) {
            throw new IllegalStateException("ffmpeg がスナップショットを 1 枚も切り出しませんでした。");
        }

        VideoFileTools.ImageSize imageSize = snapshotWriter.readImageSize(staged.get(0));
        List<StudyMonitorSnapshotWriter.StagedSnapshot> snapshots = snapshotWriter.moveToDateFolders(
                snapshotRoot, period, staged, settings.snapshotIntervalSeconds());
        Files.deleteIfExists(stagingDirectory);

        long videoId = recordWriter.write(cameraId, video, period, snapshots, durationSeconds, fileSize,
                snapshotRoot, imageSize);
        log.info("動画を取り込みました。 taskCode={} videoId={} file={} 撮影={}〜{} 長さ={}秒 切出={}枚",
                BATCH_CODE, videoId, video.getFileName(), period.startedAt(), period.endedAt(),
                durationSeconds, snapshots.size());
    }

    /**
     * 対象フォルダーの動画を、ファイル名の降順（新しい順）で返す。
     *
     * <p>そのフォルダーの直下にある {@code checked/} と {@code bak/} の中は対象にしない
     * （管理下のフォルダーなので。{@code checked/} をそのまま対象にしたときも同じ規則で、
     * その中の {@code bak/} を二重に拾わない）。</p>
     */
    List<Path> listVideoFiles(Path targetDirectory) throws IOException {
        Path base = targetDirectory.toAbsolutePath().normalize();
        try (var files = Files.walk(targetDirectory)) {
            return files.filter(Files::isRegularFile)
                    .filter(path -> !isManagedDirectoryFile(base, path))
                    .filter(VideoFileTools::isSupportedVideo)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
        }
    }

    private boolean isManagedDirectoryFile(Path baseDirectory, Path path) {
        Path relative = baseDirectory.relativize(path.toAbsolutePath().normalize());
        if (relative.getNameCount() <= 1) {
            return false;
        }
        String top = relative.getName(0).toString();
        return CHECKED_DIRECTORY.equalsIgnoreCase(top) || BAK_DIRECTORY.equalsIgnoreCase(top);
    }

    /**
     * 動画を管理下のフォルダー（{@code checked/} または {@code bak/}）へ移す。
     * 移動先は**動画のあるフォルダーの直下**に作る（ソースの動画も checked/ の動画も同じ規則）。
     * 移動先に同じ名前があれば例外（2.0 と同じ。黙って上書きしない）。
     */
    Path moveToDirectory(String directoryName, Path video) {
        try {
            Path directory = video.getParent().resolve(directoryName).toAbsolutePath().normalize();
            Files.createDirectories(directory);
            Path target = directory.resolve(video.getFileName().toString()).normalize();
            if (!target.startsWith(directory)) {
                throw new IOException("移動先のパスが不正です: " + target);
            }
            if (Files.exists(target)) {
                throw new IOException("移動先に同じ名前のファイルがあります: " + target);
            }
            return Files.move(video, target);
        } catch (IOException e) {
            throw new IllegalStateException(
                    directoryName + "/ へ動画を移動できません。 file=" + video + " error=" + e.getMessage(), e);
        }
    }

    /**
     * 処理対象外の動画を {@code bak/} へ移す。移動できなかったときは失敗として記録し、
     * 実行そのものは続ける（1 本の移動で残りを取りこぼさない）。
     */
    private Path archiveToBak(List<String> failures, Path video) {
        try {
            Path archived = moveToDirectory(BAK_DIRECTORY, video);
            log.info("処理対象外の動画を bak/ へ移しました。 taskCode={} source={} target={}",
                    BATCH_CODE, video, archived);
            return archived;
        } catch (RuntimeException e) {
            fail(failures, video, e, "bak/ へ動画を移動できません。");
            return null;
        }
    }

    private void fail(List<String> failures, Path video, Exception e, String message) {
        failures.add(video.getFileName() + ": " + shorten(e.getMessage(), 240));
        log.error("{} {} file={}", BATCH_CODE, message, video, e);
    }

    /** 実行結果の要約（履歴の メッセージ に残す。失敗があればその内容も載せる）。 */
    private String summary(Counters counters, List<String> failures,
                           LocalTime windowStart, LocalTime windowEnd) {
        StringBuilder message = new StringBuilder(BATCH_CODE).append(" が完了しました。 取込=")
                .append(counters.imported).append(" 件、").append("対象外/取込済=").append(counters.skipped)
                .append(" 件、失敗=").append(failures.size()).append(" 件、処理対象時間帯=")
                .append(formatTime(windowStart)).append("-").append(formatTime(windowEnd));
        if (!failures.isEmpty()) {
            message.append(" [").append(String.join(" | ", failures)).append(']');
        }
        return message.toString();
    }

    private static String requireValue(Map<String, String> values, String key) {
        String value = values.get(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("設定 " + key + " がありません。");
        }
        return value.trim();
    }

    /** 切出間隔（秒）。設定カタログの 有効値 1..3600 を守る。 */
    private static int parseSnapshotInterval(String value) {
        int parsed;
        try {
            parsed = Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("設定 STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS が数値ではありません: " + value, e);
        }
        if (parsed < 1 || parsed > 3600) {
            throw new IllegalStateException("設定 STUDY_MONITOR_SNAPSHOT_INTERVAL_SECONDS は 1〜3600 の範囲にしてください: " + value);
        }
        return parsed;
    }

    private static LocalTime parseTime(String value, String key) {
        try {
            return LocalTime.parse(value.trim(), PROCESSING_TIME_FORMAT);
        } catch (RuntimeException e) {
            throw new IllegalStateException("設定 " + key + " が HH:mm の形式ではありません: " + value, e);
        }
    }

    private static String formatTime(LocalTime time) {
        return time.format(PROCESSING_TIME_FORMAT);
    }

    private String shorten(String value, int maxLength) {
        if (value == null) {
            return "原因不明のエラー";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    /** 1 回の実行の件数。 */
    private static final class Counters {
        private int imported;
        private int skipped;
    }

    /** 実行時の設定（ソースフォルダー・カメラ・処理対象時間帯・切出間隔）。 */
    private record RuntimeSettings(Path videoDirectory, String cameraLocation,
                                   int snapshotIntervalSeconds, LocalTime windowStart, LocalTime windowEnd) {

        RuntimeSettings {
            if (videoDirectory.getFileName() == null) {
                // カメラコードはソースフォルダー名から決める（ファイルシステムのルートは指定できない）
                throw new IllegalStateException(
                        "動画のソースフォルダー名からカメラコードを決められません: " + videoDirectory);
            }
        }

        /** カメラコード = ソースフォルダー名（2.1 はカメラが 1 台）。 */
        String cameraCode() {
            return videoDirectory.getFileName().toString();
        }
    }
}

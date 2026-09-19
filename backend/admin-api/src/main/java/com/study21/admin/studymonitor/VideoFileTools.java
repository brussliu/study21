package com.study21.admin.studymonitor;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 学習モニターの動画ファイルまわりの道具（batL02）。
 *
 * <p>2.0 の {@code BatL02Task} が持っていた「ファイル名の解析」「処理対象時間帯の判定」
 * 「ffprobe / ffmpeg の起動」「画像サイズの取得」をそのまま移植したもの。
 * <b>コマンドは外から注入できる</b>ので、実 ffmpeg が無い環境でもテストできる
 * （テストは本クラスを継承して結果を差し替える）。</p>
 *
 * <p>コマンド・タイムアウト・出力の扱いは 2.0 と同じ:
 * ffprobe は 30 秒、ffmpeg は 10 分。ffmpeg の出力は標準エラーと併せて読み、
 * 終了コードが 0 以外なら出力の先頭 2,000 文字を付けて例外にする。</p>
 */
@Component
public class VideoFileTools {

    private static final Logger log = LoggerFactory.getLogger(VideoFileTools.class);

    /** ffmpeg の待ち時間（2.0 と同じ 10 分）。 */
    static final Duration FFMPEG_TIMEOUT = Duration.ofMinutes(10);
    /** ffprobe の待ち時間（2.0 と同じ 30 秒）。 */
    static final Duration FFPROBE_TIMEOUT = Duration.ofSeconds(30);
    /** 例外メッセージに載せる ffmpeg 出力の上限（2.0 と同じ）。 */
    static final int FFMPEG_OUTPUT_LIMIT = 2_000;
    /** 最新のソース動画を「書き込み中でない」と見なすサイズ（2.0 と同じ 125MB）。 */
    static final long NEWEST_VIDEO_MIN_BYTES = 125L * 1024 * 1024;
    /** 最新のソース動画を「書き込みが終わった」と見なすまでの経過時間（2.0 と同じ 5 分）。 */
    static final Duration NEWEST_VIDEO_MIN_AGE = Duration.ofMinutes(5);

    /** 切り出したスナップショットのファイル名（連番）。 */
    static final String SNAPSHOT_FILE_NAME_FORMAT = "snapshot_%06d.jpg";
    /** 日付フォルダーの名前（2.0 と同じ yyyyMMdd）。 */
    static final DateTimeFormatter SNAPSHOT_DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;
    /** 動画ファイル名の撮影開始・終了の書式。 */
    static final DateTimeFormatter VIDEO_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    /** カメラが付けるファイル名（接頭辞_撮影開始_撮影終了.拡張子）。 */
    private static final Pattern CAMERA_FILE_NAME =
            Pattern.compile("^(.*_)(\\d{14})_(\\d{14})(\\.[^.]+)$", Pattern.CASE_INSENSITIVE);

    /** 取り込む対象の動画拡張子。 */
    private static final Set<String> VIDEO_EXTENSIONS = Set.of("mp4", "mov", "mkv", "avi");

    private static final int SECONDS_PER_DAY = 24 * 60 * 60;

    private final String ffmpegCommand;
    private final String ffprobeCommand;

    public VideoFileTools(@Value("${study21.study-monitor.ffmpeg-command:ffmpeg}") String ffmpegCommand,
                          @Value("${study21.study-monitor.ffprobe-command:ffprobe}") String ffprobeCommand) {
        this.ffmpegCommand = ffmpegCommand;
        this.ffprobeCommand = ffprobeCommand;
    }

    /**
     * 処理の対象になる拡張子か（大文字小文字は区別しない）。
     */
    public static boolean isSupportedVideo(Path path) {
        return isSupportedVideo(path.getFileName().toString());
    }

    static boolean isSupportedVideo(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot >= 0 && VIDEO_EXTENSIONS.contains(fileName.substring(dot + 1).toLowerCase(Locale.ROOT));
    }

    /**
     * ファイル名から撮影時間帯を取り出す（2.0 と同じ規則・同じメッセージ）。
     *
     * @throws IllegalArgumentException 名前の形が違う／終了が開始より後でないとき
     */
    public static VideoPeriod parseVideoPeriod(String fileName) {
        Matcher matcher = CAMERA_FILE_NAME.matcher(fileName);
        if (!matcher.matches()) {
            throw new IllegalArgumentException(
                    "対応していない動画ファイル名です。*_yyyyMMddHHmmss_yyyyMMddHHmmss.ext の形にしてください: " + fileName);
        }
        LocalDateTime startedAt = LocalDateTime.parse(matcher.group(2), VIDEO_TIME_FORMAT);
        LocalDateTime endedAt = LocalDateTime.parse(matcher.group(3), VIDEO_TIME_FORMAT);
        if (!endedAt.isAfter(startedAt)) {
            throw new IllegalArgumentException("動画の撮影終了日時は撮影開始日時より後にしてください: " + fileName);
        }
        return new VideoPeriod(startedAt, endedAt);
    }

    /** 動画のパスから撮影時間帯を取り出す。 */
    public static VideoPeriod parseVideoPeriod(Path video) {
        return parseVideoPeriod(video.getFileName().toString());
    }

    /**
     * 撮影時間帯が処理対象時間帯と少しでも重なるか（重ならない動画は取り込まず bak/ へ移す）。
     *
     * <p>開始時刻だけを見ていた 2.0 の旧実装では、08:50〜09:05 のように処理開始時刻（9 時）を
     * またぐ動画を取りこぼしていたため、終了時刻も含めて判定する（2.0 の最終形と同じ）。</p>
     */
    public static boolean overlapsProcessingWindow(VideoPeriod period,
                                                   LocalTime windowStart, LocalTime windowEnd) {
        if (windowStart.equals(windowEnd)) {
            // 開始と終了が同じときは「終日」とみなす（2.0 と同じ）
            return true;
        }
        int videoStartSecond = period.startedAt().toLocalTime().toSecondOfDay();
        int videoEndSecond = period.endedAt().toLocalTime().toSecondOfDay();
        int windowStartSecond = windowStart.toSecondOfDay();
        int windowEndSecond = windowEnd.toSecondOfDay();

        if (videoEndSecond <= videoStartSecond) {
            // 動画が日をまたぐ場合：[開始, 24:00) と [0, 終了] に分けて判定する
            return overlapsLinear(videoStartSecond, SECONDS_PER_DAY, windowStartSecond, windowEndSecond)
                    || overlapsLinear(0, videoEndSecond, windowStartSecond, windowEndSecond);
        }
        return overlapsLinear(videoStartSecond, videoEndSecond, windowStartSecond, windowEndSecond);
    }

    /** 線形な区間どうしの重なり（{@code videoStart < videoEnd} を前提とする）。 */
    private static boolean overlapsLinear(int videoStartSecond, int videoEndSecond,
                                          int windowStartSecond, int windowEndSecond) {
        if (windowStartSecond < windowEndSecond) {
            return videoStartSecond <= windowEndSecond && videoEndSecond >= windowStartSecond;
        }
        // 処理対象時間帯が日をまたぐ場合（例: 22:00〜03:00）：[開始, 24:00) と [0, 終了] に分ける
        return overlapsLinear(videoStartSecond, videoEndSecond, windowStartSecond, SECONDS_PER_DAY)
                || overlapsLinear(videoStartSecond, videoEndSecond, 0, windowEndSecond);
    }

    /**
     * 最新のソース動画を取り込んでよいか（2.0 と同じ規則）。
     *
     * <p>「125MB より大きく、最終更新から 5 分以上経っている」ときだけ取り込む。
     * それ以外は書き込み中かもしれないので次の実行に回す（＝今回の実行では飛ばす）。
     * ファイルの属性が読めないときは、安全側に倒して飛ばす（2.0 と同じ）。</p>
     */
    public static boolean shouldProcessNewestVideo(long sizeBytes, long ageMillis) {
        return sizeBytes > NEWEST_VIDEO_MIN_BYTES && ageMillis >= NEWEST_VIDEO_MIN_AGE.toMillis();
    }

    /** ファイルの属性から最新動画の判定をする（読めなければ false）。 */
    public boolean shouldProcessNewestVideo(Path file) {
        try {
            return shouldProcessNewestVideo(Files.size(file),
                    System.currentTimeMillis() - Files.getLastModifiedTime(file).toMillis());
        } catch (IOException e) {
            log.warn("最新のソース動画の属性を確認できないため、今回は飛ばします。 file={} error={}", file, e.toString());
            return false;
        }
    }

    /** ファイル名から拡張子を除いた部分（staging フォルダー名に使う）。 */
    public static String stripExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot < 0 ? fileName : fileName.substring(0, dot);
    }

    /**
     * ffprobe で動画の長さ（秒）を読む。四捨五入して最低 1 秒にする（2.0 と同じ）。
     *
     * @throws IllegalStateException 長さが数値でない／0 以下のとき
     */
    public long probeDurationSeconds(Path video) throws Exception {
        List<String> command = probeCommand(video);
        String output = runProcess(command, null, FFPROBE_TIMEOUT);
        double seconds;
        try {
            seconds = Double.parseDouble(output.trim());
        } catch (NumberFormatException e) {
            throw new IllegalStateException("ffprobe が不正な動画の長さを返しました: " + output, e);
        }
        if (!Double.isFinite(seconds) || seconds <= 0) {
            throw new IllegalStateException("ffprobe が 0 以下の動画の長さを返しました: " + output);
        }
        return Math.max(1, Math.round(seconds));
    }

    /** ffprobe の起動コマンド（2.0 と同じ引数）。 */
    List<String> probeCommand(Path video) {
        return List.of(ffprobeCommand, "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", video.toString());
    }

    /**
     * ffmpeg でスナップショットを切り出す（2.0 と同じ引数・同じ待ち時間）。
     *
     * <p>出力先のフォルダーに {@code snapshot_%06d.jpg} を書く。呼び出し側は
     * 「1 枚も出来なかった」ことを確認してから次へ進む（2.0 と同じ）。</p>
     *
     * @throws IllegalStateException 10 分で終わらない／終了コードが 0 以外のとき
     */
    public void extractSnapshots(Path video, Path outputDirectory, int snapshotIntervalSeconds) throws Exception {
        Files.createDirectories(outputDirectory);
        List<String> command = snapshotCommand(video, outputDirectory, snapshotIntervalSeconds);
        runProcess(command, FFMPEG_OUTPUT_LIMIT, FFMPEG_TIMEOUT);
    }

    /** ffmpeg の起動コマンド（2.0 と同じ引数）。 */
    List<String> snapshotCommand(Path video, Path outputDirectory, int snapshotIntervalSeconds) {
        return List.of(ffmpegCommand, "-hide_banner", "-loglevel", "error", "-y", "-i", video.toString(),
                "-vf", "fps=1/" + snapshotIntervalSeconds, "-q:v", "2",
                outputDirectory.resolve(SNAPSHOT_FILE_NAME_FORMAT).toString());
    }

    /**
     * 外部コマンドを 1 回だけ起動する。
     *
     * <p>ffmpeg は標準エラーと標準出力をまとめて読み（デッドロックを避けるため別スレッドで読む）、
     * 終了コードが 0 以外なら出力の先頭を付けて例外にする。ffprobe は標準エラーを捨てて
     * 標準出力だけを読む（2.0 と同じ扱い）。待ち時間を過ぎたら強制終了する。</p>
     *
     * @param outputLimit 例外メッセージに載せる出力の上限（0 なら出力を使わない）
     */
    String runProcess(List<String> command, Integer outputLimit, Duration timeout) throws Exception {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        boolean captureOutput = outputLimit != null;
        if (captureOutput) {
            processBuilder.redirectErrorStream(true);
        } else {
            processBuilder.redirectError(ProcessBuilder.Redirect.DISCARD);
        }
        Process process = processBuilder.start();

        FutureTask<byte[]> outputTask = new FutureTask<>(() -> process.getInputStream().readAllBytes());
        Thread.startVirtualThread(outputTask);

        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException(command.get(0) + " が " + timeoutDescription(timeout)
                    + "以内に終了しませんでした。");
        }
        String output = new String(outputTask.get(10, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            String trimmed = output.trim();
            throw new IllegalStateException(command.get(0) + " が終了コード " + process.exitValue()
                    + " で失敗しました。" + (trimmed.isEmpty() ? ""
                    : ": " + shorten(trimmed, outputLimit == null ? FFMPEG_OUTPUT_LIMIT : outputLimit)));
        }
        return output;
    }

    /** 待ち時間の説明（2.0 と同じ「10 分」の形。1 分未満は秒で出す）。 */
    private static String timeoutDescription(Duration timeout) {
        return timeout.toMinutes() >= 1 ? timeout.toMinutes() + " 分" : timeout.toMillis() + " ミリ秒";
    }

    /**
     * 切り出した画像の大きさを読む（スナップショット情報の 幅・高さ）。
     *
     * @throws IOException 画像として読めないとき
     */
    public ImageSize readImageSize(Path image) throws IOException {
        BufferedImage bufferedImage = ImageIO.read(image.toFile());
        if (bufferedImage == null) {
            throw new IOException("切り出したスナップショットを画像として読めません: " + image);
        }
        return new ImageSize(bufferedImage.getWidth(), bufferedImage.getHeight());
    }

    private static String shorten(String value, int maxLength) {
        if (value == null) {
            return "unknown error";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    /** 動画の撮影時間帯（ファイル名の撮影開始・終了）。 */
    public record VideoPeriod(LocalDateTime startedAt, LocalDateTime endedAt) {
    }

    /** 画像の大きさ。 */
    public record ImageSize(int width, int height) {
    }
}

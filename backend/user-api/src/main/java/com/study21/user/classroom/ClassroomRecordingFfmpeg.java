package com.study21.user.classroom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.FutureTask;
import java.util.concurrent.TimeUnit;

/**
 * 録音の結合に使う**外部コマンド（ffmpeg / ffprobe）**の起動。
 *
 * <p><b>なぜ必要か</b>: `MediaRecorder` は一時停止・画面の開き直しのたびに作り直され、
 * 新しいセッションの分塊は**タイムスタンプが 0 から始まる**。コンテナのヘッダを落として
 * 繋ぐだけでは時刻が重なるので、**セッションごとに時刻を積み直して**から 1 本にする
 * （`-itsoffset` で本来の位置へずらし、`concat` フィルタで繋ぐ＝再符号化なしの再多重化）。</p>
 *
 * <p><b>使えない環境でも壊さない</b>: {@link #available()} が false のときは結合せず、
 * 分塊を残したまま「結合できていない（もう一度試せる）」と画面に出す。**壊れた 1 本を
 * 公開しない**ことを優先する。</p>
 */
@Component
public class ClassroomRecordingFfmpeg {

    private static final Logger log = LoggerFactory.getLogger(ClassroomRecordingFfmpeg.class);

    /** ffmpeg の待ち時間（学習モニターの 10 分と同じ考え方。録音の結合はもっと短い）。 */
    static final Duration RUN_TIMEOUT = Duration.ofMinutes(10);
    /** 例外メッセージに載せる出力の上限。 */
    static final int OUTPUT_LIMIT = 2_000;
    /** 出力の先読みに使う入力の上限（`-probesize`。既定より小さくして起動を速くする）。 */
    static final String PROBE_SIZE = "32M";

    /** 外部コマンドの起動（テストで差し替える）。 */
    @FunctionalInterface
    public interface Runner {
        /**
         * 1 回だけ起動して標準出力（ffmpeg は標準エラーも併合）を返す。
         *
         * @throws IllegalStateException 起動できない・時間内に終わらない・終了コードが 0 以外
         */
        String run(List<String> command, Duration timeout) throws Exception;
    }

    private final String ffmpegCommand;
    private final String ffprobeCommand;
    private final Runner runner;
    private final boolean available;

    /** 本番の生成（Spring が使う口。コマンド名は設定から注入する）。 */
    @org.springframework.beans.factory.annotation.Autowired
    public ClassroomRecordingFfmpeg(
            @Value("${study21.classroom.ffmpeg-command:ffmpeg}") String ffmpegCommand,
            @Value("${study21.classroom.ffprobe-command:ffprobe}") String ffprobeCommand) {
        this(ffmpegCommand, ffprobeCommand, ClassroomRecordingFfmpeg::runProcess, null);
    }

    /** テスト用（起動を差し替える・使えるかどうかを決め打ちする）。 */
    ClassroomRecordingFfmpeg(String ffmpegCommand, String ffprobeCommand, Runner runner, Boolean available) {
        this.ffmpegCommand = ffmpegCommand;
        this.ffprobeCommand = ffprobeCommand;
        this.runner = runner;
        this.available = available != null ? available : probeAvailability(ffmpegCommand, ffprobeCommand, runner);
    }

    /** ffmpeg / ffprobe が使える環境か（使えないときは結合せず、分塊を残して再試行に回す）。 */
    public static ClassroomRecordingFfmpeg unavailable() {
        return new ClassroomRecordingFfmpeg("ffmpeg", "ffprobe",
                (command, timeout) -> {
                    throw new IllegalStateException("ffmpeg は使えません（テスト）");
                }, false);
    }

    /** この環境で結合ができるか。 */
    public boolean available() {
        return available;
    }

    /**
     * セッションを 1 本へ再多重化する（**出力は新しいファイル**。既にあるファイルは上書きしない）。
     *
     * <p>各セッションは「録音の時間軸での位置（秒）」へ `-itsoffset` で置き直してから
     * `concat` フィルタで繋ぐ。これで**最終の長さ・再生の順序・頭出しの位置が書き起こしの
     * 時間軸と一致**する（タイムスタンプが重ならない）。</p>
     *
     * @param sessions セッション（この順に繋ぐ）
     * @param starts  各セッションの開始位置（秒。セッションと同じ数）
     * @param output   書き出し先（**まだ存在しない**新しいパス）
     */
    public void remuxSessions(List<Path> sessions, List<Double> starts, Path output) throws Exception {
        if (sessions.isEmpty()) {
            throw new IllegalArgumentException("結合するセッションがありません。");
        }
        if (sessions.size() != starts.size()) {
            throw new IllegalArgumentException("セッションと開始位置の数が違います。");
        }
        if (Files.exists(output)) {
            // **既にあるファイルを上書きしない**（前の結合結果を壊さない）
            throw new IllegalStateException("出力先が既にあります: " + output);
        }
        // 起動できなければ例外（呼び側が「結合できていない」として分塊を残す）
        runner.run(remuxCommand(sessions, starts, output), RUN_TIMEOUT);
        if (!Files.isRegularFile(output) || sizeOf(output) <= 0) {
            throw new IllegalStateException("ffmpeg は終了しましたが、出力がありません: " + output);
        }
    }

    /** ファイルの大きさ（読めなければ 0）。 */
    private static long sizeOf(Path path) {
        try {
            return Files.size(path);
        } catch (IOException cause) {
            return 0;
        }
    }

    /** 結合のコマンド（テストで中身を確かめる）。 */
    public List<String> remuxCommand(List<Path> sessions, List<Double> starts, Path output) {
        List<String> command = new ArrayList<>();
        command.add(ffmpegCommand);
        command.add("-hide_banner");
        command.add("-loglevel");
        command.add("error");
        // **上書きしない**（-y を付けない）。出力は常に新しい作業ファイル
        command.add("-nostdin");
        for (int index = 0; index < sessions.size(); index += 1) {
            command.add("-itsoffset");
            command.add(formatSeconds(starts.get(index)));
            command.add("-probesize");
            command.add(PROBE_SIZE);
            command.add("-i");
            command.add(sessions.get(index).toString());
        }
        StringBuilder filter = new StringBuilder();
        for (int index = 0; index < sessions.size(); index += 1) {
            // 各セッションの時刻を 0 から数え直してから繋ぐ（重なりを作らない）
            filter.append('[').append(index).append(":a]asetpts=PTS-STARTPTS[s").append(index).append("];");
        }
        for (int index = 0; index < sessions.size(); index += 1) {
            filter.append("[s").append(index).append(']');
        }
        filter.append("concat=n=").append(sessions.size()).append(":v=0:a=1[out]");
        command.add("-filter_complex");
        command.add(filter.toString());
        command.add("-map");
        command.add("[out]");
        command.add("-c:a");
        command.add("libopus");
        command.add("-b:a");
        command.add("64k");
        command.add("-f");
        command.add("webm");
        command.add(output.toString());
        return command;
    }

    /**
     * メディアの長さ（秒）を読む（結合の検証・記録の長さに使う）。
     *
     * @return 読めなければ null
     */
    public Double probeDurationSeconds(Path media) {
        if (!Files.isRegularFile(media)) {
            return null;
        }
        try {
            String output = runner.run(probeCommand(media), RUN_TIMEOUT);
            double seconds = Double.parseDouble(output.trim());
            return Double.isFinite(seconds) && seconds > 0 ? seconds : null;
        } catch (Exception cause) {
            log.warn("録音の長さを読めませんでした。path={} error={}", media, cause.toString());
            return null;
        }
    }

    /** 長さを読むコマンド。 */
    public List<String> probeCommand(Path media) {
        return List.of(ffprobeCommand, "-v", "error", "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1", media.toString());
    }

    /** 秒を ffmpeg の書式（小数 3 桁）へ。 */
    private static String formatSeconds(Double seconds) {
        double value = seconds == null || !Double.isFinite(seconds) || seconds < 0 ? 0 : seconds;
        return String.format(java.util.Locale.ROOT, "%.3f", value);
    }

    /** 使えるかを実際に起動して確かめる（起動できなければ false）。 */
    private static boolean probeAvailability(String ffmpegCommand, String ffprobeCommand, Runner runner) {
        try {
            runner.run(List.of(ffmpegCommand, "-version"), Duration.ofSeconds(20));
            runner.run(List.of(ffprobeCommand, "-version"), Duration.ofSeconds(20));
            return true;
        } catch (Exception cause) {
            log.warn("ffmpeg / ffprobe が使えないため、続録の結合は行いません"
                    + "（分塊は残るので、あとから結合できます）。error={}", cause.toString());
            return false;
        }
    }

    /** 外部コマンドを 1 回起動する（出力を読み切ってから終了コードを見る）。 */
    static String runProcess(List<String> command, Duration timeout) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        FutureTask<byte[]> outputTask = new FutureTask<>(() -> process.getInputStream().readAllBytes());
        Thread.startVirtualThread(outputTask);
        if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException(command.get(0) + " が時間内に終了しませんでした。");
        }
        String output = new String(outputTask.get(10, TimeUnit.SECONDS), StandardCharsets.UTF_8);
        if (process.exitValue() != 0) {
            String trimmed = output.trim();
            throw new IllegalStateException(command.get(0) + " が終了コード " + process.exitValue()
                    + " で失敗しました。" + (trimmed.isEmpty() ? ""
                    : ": " + shorten(trimmed, OUTPUT_LIMIT)));
        }
        return output;
    }

    private static String shorten(String value, int maxLength) {
        if (value == null) {
            return "unknown error";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength) + "...";
    }

    /** 書き込み途中のファイルを消す（呼び側の後片付け）。 */
    static void deleteQuietly(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException cause) {
            log.warn("作業ファイルを消せませんでした。path={}", path, cause);
        }
    }
}

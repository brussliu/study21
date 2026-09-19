package com.study21.admin.studymonitor;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link VideoFileTools} の外部コマンド（ffprobe / ffmpeg）まわりのテスト。
 *
 * <p>2.0 {@code BatL02Task} の引数・タイムアウト・出力の扱いをそのまま移植できているかを固定する。
 * 実 ffmpeg は使わず、引数は組み立てたコマンドを、動きは小さなシェルスクリプトを見て確かめる
 * （コマンドは外から注入できる）。</p>
 */
class VideoFileToolsCommandTest {

    private static final String FFMPEG = "/opt/ffmpeg/bin/ffmpeg";
    private static final String FFPROBE = "/opt/ffmpeg/bin/ffprobe";

    private final VideoFileTools tools = new VideoFileTools(FFMPEG, FFPROBE);

    // ---------------------------------------------------------------- 組み立てるコマンド

    @Test
    void ffprobeCommandMatches20() {
        assertThat(tools.probeCommand(Path.of("/src/videos/cam_20260726000000_20260726000100.mp4")))
                .containsExactly(FFPROBE, "-v", "error", "-show_entries", "format=duration",
                        "-of", "default=noprint_wrappers=1:nokey=1",
                        "/src/videos/cam_20260726000000_20260726000100.mp4");
    }

    @Test
    void ffmpegCommandMatches20() {
        assertThat(tools.snapshotCommand(Path.of("/src/videos/cam.mp4"), Path.of("/data/snap/.staging/cam"), 60))
                .containsExactly(FFMPEG, "-hide_banner", "-loglevel", "error", "-y",
                        "-i", "/src/videos/cam.mp4",
                        "-vf", "fps=1/60", "-q:v", "2",
                        "/data/snap/.staging/cam/snapshot_%06d.jpg".replace('/', java.io.File.separatorChar));
    }

    @Test
    void ffmpegCommandUsesConfiguredInterval() {
        List<String> command = tools.snapshotCommand(Path.of("/v/cam.mp4"), Path.of("/d/staging/cam"), 300);

        assertThat(command).containsSequence("-vf", "fps=1/300");
    }

    @Test
    void timeoutsMatch20() {
        assertThat(VideoFileTools.FFMPEG_TIMEOUT).isEqualTo(Duration.ofMinutes(10));
        assertThat(VideoFileTools.FFPROBE_TIMEOUT).isEqualTo(Duration.ofSeconds(30));
        assertThat(VideoFileTools.FFMPEG_OUTPUT_LIMIT).isEqualTo(2_000);
    }

    // ---------------------------------------------------------------- 起動できないとき

    @Test
    void failsWhenCommandIsMissing(@TempDir Path tempDirectory) {
        // ProcessBuilder が投げる IOException をそのまま通す（2.0 と同じ。
        // 呼び出し側＝StudyMonitorImportHandler が失敗として記録し、動画は checked/ に残す）
        assertThatThrownBy(() -> tools.runProcess(
                List.of(tempDirectory.resolve("no-such-command").toString()),
                VideoFileTools.FFMPEG_OUTPUT_LIMIT, VideoFileTools.FFMPEG_TIMEOUT))
                .isInstanceOf(java.io.IOException.class);
    }

    // ---------------------------------------------------------------- タイムアウト

    @Test
    void killsFfmpegOnTimeout(@TempDir Path tempDirectory) throws Exception {
        Path slow = script(tempDirectory, "slow.sh", "sleep 30\n");
        long startedMillis = System.currentTimeMillis();

        assertThatIllegalStateException()
                .isThrownBy(() -> tools.runProcess(List.of(slow.toString()), VideoFileTools.FFMPEG_OUTPUT_LIMIT,
                        Duration.ofMillis(400)))
                .withMessageContaining("終了しませんでした");
        // 30 秒待たずに戻ってきている（強制終了できている）
        assertThat(System.currentTimeMillis() - startedMillis).isLessThan(10_000);
    }

    @Test
    void killsFfprobeOnTimeout(@TempDir Path tempDirectory) throws Exception {
        Path slow = script(tempDirectory, "slow-probe.sh", "sleep 30\n");

        assertThatIllegalStateException()
                .isThrownBy(() -> tools.runProcess(List.of(slow.toString()), null, Duration.ofMillis(300)))
                .withMessageContaining("終了しませんでした");
    }

    // ---------------------------------------------------------------- 異常終了

    @Test
    void failsWithOutputWhenExitCodeIsNotZero(@TempDir Path tempDirectory) throws Exception {
        Path failing = script(tempDirectory, "fail.sh",
                "echo 'Invalid data found when processing input' 1>&2\nexit 1\n");

        assertThatIllegalStateException()
                .isThrownBy(() -> tools.runProcess(List.of(failing.toString()), VideoFileTools.FFMPEG_OUTPUT_LIMIT,
                        Duration.ofSeconds(10)))
                .withMessageContaining("終了コード 1")
                .withMessageContaining("Invalid data found when processing input");
    }

    @Test
    void shortensLongOutput(@TempDir Path tempDirectory) throws Exception {
        Path failing = script(tempDirectory, "long.sh",
                "for i in $(seq 1 200); do echo 'xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx'; done\nexit 3\n");

        String message = org.assertj.core.api.Assertions.catchThrowableOfType(
                        IllegalStateException.class,
                        () -> tools.runProcess(List.of(failing.toString()), 100, Duration.ofSeconds(30)))
                .getMessage();

        assertThat(message).contains("終了コード 3");
        assertThat(message).contains("...");
        assertThat(message.length()).isLessThan(500);
    }

    @Test
    void readsDurationFromProbeOutput(@TempDir Path tempDirectory) throws Exception {
        Path probe = script(tempDirectory, "probe.sh", "echo 61.400000\n");

        long seconds = new VideoFileTools(FFMPEG, probe.toString()).probeDurationSeconds(Path.of("/v/cam.mp4"));

        // 四捨五入（2.0 と同じ）
        assertThat(seconds).isEqualTo(61L);
    }

    @Test
    void rejectsNonNumericProbeOutput(@TempDir Path tempDirectory) throws Exception {
        Path probe = script(tempDirectory, "bad-probe.sh", "echo 'N/A'\n");

        assertThatIllegalStateException()
                .isThrownBy(() -> new VideoFileTools(FFMPEG, probe.toString())
                        .probeDurationSeconds(Path.of("/v/cam.mp4")))
                .withMessageContaining("不正な動画の長さ");
    }

    @Test
    void rejectsNonPositiveProbeOutput(@TempDir Path tempDirectory) throws Exception {
        Path probe = script(tempDirectory, "zero-probe.sh", "echo 0\n");

        assertThatIllegalStateException()
                .isThrownBy(() -> new VideoFileTools(FFMPEG, probe.toString())
                        .probeDurationSeconds(Path.of("/v/cam.mp4")))
                .withMessageContaining("0 以下の動画の長さ");
    }

    @Test
    void extractsSnapshotsIntoOutputDirectory(@TempDir Path tempDirectory) throws Exception {
        // 実 ffmpeg の代わりに、引数の最後の出力先へダミーの jpg を 2 枚書くスクリプト
        Path fake = script(tempDirectory, "fake-ffmpeg.sh",
                "out=\"${@: -1}\"\ndir=$(dirname \"$out\")\nmkdir -p \"$dir\"\nbase=$(basename \"$out\")\n"
                        + "printf 'one' > \"$dir/$(echo \"$base\" | sed 's/%06d/000001/')\"\n"
                        + "printf 'two' > \"$dir/$(echo \"$base\" | sed 's/%06d/000002/')\"\n");
        Path outputDirectory = tempDirectory.resolve("staging").resolve("cam");

        new VideoFileTools(fake.toString(), FFPROBE)
                .extractSnapshots(Path.of("/v/cam.mp4"), outputDirectory, 60);

        assertThat(Files.list(outputDirectory).map(p -> p.getFileName().toString()).sorted().toList())
                .containsExactly("snapshot_000001.jpg", "snapshot_000002.jpg");
    }

    /** 実行できるシェルスクリプトを一時フォルダーに作る。 */
    private static Path script(Path tempDirectory, String fileName, String body) throws Exception {
        Path script = tempDirectory.resolve(fileName);
        Files.writeString(script, "#!/bin/bash\n" + body, StandardCharsets.UTF_8);
        try {
            Files.setPosixFilePermissions(script, PosixFilePermissions.fromString("rwxr-xr-x"));
        } catch (UnsupportedOperationException e) {
            assertThat(script.toFile().setExecutable(true)).isTrue();
        }
        return script;
    }
}

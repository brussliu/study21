package com.study21.admin.studymonitor;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.LocalTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/**
 * {@link VideoFileTools} の判定（2.0 BatL02Task の移植）のテスト。
 *
 * <p>対象は 3 つの純粋な判定: 動画ファイル名の解析、処理対象時間帯との重なり、
 * 最新ソース動画を今回取り込むかどうか。実ファイルも ffmpeg も使わない。</p>
 */
class VideoFileToolsTest {

    // ---------------------------------------------------------------- ファイル名の解析

    @Test
    void parsesStartAndEndFromFileName() {
        VideoFileTools.VideoPeriod period =
                VideoFileTools.parseVideoPeriod("XiaomiCamera_00_B88880D0F03E_20260726095000_20260726100000.mp4");

        assertThat(period.startedAt()).isEqualTo(LocalDateTime.of(2026, 7, 26, 9, 50));
        assertThat(period.endedAt()).isEqualTo(LocalDateTime.of(2026, 7, 26, 10, 0));
    }

    @Test
    void parsesPrefixWithUnderscores() {
        // 接頭辞に "_" が複数あっても、後ろの 2 つの 14 桁を撮影開始・終了として読む
        VideoFileTools.VideoPeriod period = VideoFileTools.parseVideoPeriod(
                "a_b_20260726000000_20260726000100.MP4");

        assertThat(period.startedAt()).isEqualTo(LocalDateTime.of(2026, 7, 26, 0, 0));
        assertThat(period.endedAt()).isEqualTo(LocalDateTime.of(2026, 7, 26, 0, 1));
    }

    @Test
    void rejectsUnsupportedFileName() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VideoFileTools.parseVideoPeriod("20260726095000.mp4"))
                .withMessageContaining("対応していない動画ファイル名");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VideoFileTools.parseVideoPeriod("XiaomiCamera_2026072609500_20260726100000.mp4"))
                .withMessageContaining("対応していない動画ファイル名");
    }

    @Test
    void rejectsEndNotAfterStart() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VideoFileTools.parseVideoPeriod("cam_20260726095000_20260726095000.mp4"))
                .withMessageContaining("撮影終了日時は撮影開始日時より後");
        assertThatIllegalArgumentException()
                .isThrownBy(() -> VideoFileTools.parseVideoPeriod("cam_20260726100000_20260726095000.mp4"))
                .withMessageContaining("撮影終了日時は撮影開始日時より後");
    }

    @Test
    void acceptsSupportedVideoExtensionsOnly() {
        assertThat(VideoFileTools.isSupportedVideo(Path.of("a.mp4"))).isTrue();
        assertThat(VideoFileTools.isSupportedVideo(Path.of("a.MOV"))).isTrue();
        assertThat(VideoFileTools.isSupportedVideo(Path.of("a.mkv"))).isTrue();
        assertThat(VideoFileTools.isSupportedVideo(Path.of("a.avi"))).isTrue();
        assertThat(VideoFileTools.isSupportedVideo(Path.of("a.jpg"))).isFalse();
        assertThat(VideoFileTools.isSupportedVideo(Path.of("a"))).isFalse();
    }

    @Test
    void stripsExtension() {
        assertThat(VideoFileTools.stripExtension("cam_20260726000000_20260726000100.mp4"))
                .isEqualTo("cam_20260726000000_20260726000100");
        assertThat(VideoFileTools.stripExtension("noextension")).isEqualTo("noextension");
    }

    // ---------------------------------------------------------------- 処理対象時間帯

    @Test
    void processesVideoInsideWindow() {
        assertThat(overlaps("20260726100000", "20260726101000", "09:00", "23:59")).isTrue();
    }

    @Test
    void excludesVideoOutsideWindow() {
        assertThat(overlaps("20260726070000", "20260726080000", "09:00", "23:59")).isFalse();
        assertThat(overlaps("20260726220000", "20260726230000", "09:00", "18:00")).isFalse();
    }

    @Test
    void processesVideoCrossingWindowStart() {
        // 2.0 の旧実装は開始時刻だけを見て 08:50〜09:05 を取りこぼしていた
        assertThat(overlaps("20260726085000", "20260726090500", "09:00", "23:59")).isTrue();
    }

    @Test
    void processesVideoTouchingWindowEdge() {
        assertThat(overlaps("20260726085500", "20260726090000", "09:00", "23:59")).isTrue();
        assertThat(overlaps("20260726235900", "20260726235959", "09:00", "23:59")).isTrue();
    }

    @Test
    void treatsSameStartAndEndAsAllDay() {
        assertThat(overlaps("20260726030000", "20260726040000", "09:00", "09:00")).isTrue();
        assertThat(overlaps("20260726230000", "20260726233000", "09:00", "09:00")).isTrue();
    }

    @Test
    void handlesWindowAcrossMidnight() {
        // 22:00〜03:00 の窓
        assertThat(overlaps("20260726223000", "20260726230000", "22:00", "03:00")).isTrue();
        assertThat(overlaps("20260726003000", "20260726010000", "22:00", "03:00")).isTrue();
        assertThat(overlaps("20260726120000", "20260726130000", "22:00", "03:00")).isFalse();
    }

    @Test
    void handlesVideoAcrossMidnight() {
        // 23:58〜00:03 の動画（日をまたぐ）
        assertThat(overlaps("20260726235800", "20260727000300", "09:00", "23:59")).isTrue();
        // 翌日側の 00:00〜00:03 だけが候補になるので、01:00 からの窓とは重ならない
        assertThat(overlaps("20260726235800", "20260727000300", "01:00", "05:00")).isFalse();
        assertThat(overlaps("20260726235800", "20260727000300", "00:00", "05:00")).isTrue();
        assertThat(overlaps("20260726235800", "20260727000300", "10:00", "18:00")).isFalse();
        assertThat(overlaps("20260726200000", "20260726230000", "09:00", "18:00")).isFalse();
    }

    private static boolean overlaps(String start, String end, String windowStart, String windowEnd) {
        VideoFileTools.VideoPeriod period = VideoFileTools.parseVideoPeriod(
                "cam_" + start + "_" + end + ".mp4");
        return VideoFileTools.overlapsProcessingWindow(period,
                LocalTime.parse(windowStart), LocalTime.parse(windowEnd));
    }

    // ---------------------------------------------------------------- 最新ソース動画のスキップ規則

    @Test
    void processesNewestVideoWhenBigAndOld() {
        long age = VideoFileTools.NEWEST_VIDEO_MIN_AGE.toMillis();
        assertThat(VideoFileTools.shouldProcessNewestVideo(125L * 1024 * 1024 + 1, age)).isTrue();
        assertThat(VideoFileTools.shouldProcessNewestVideo(300L * 1024 * 1024, age + 1)).isTrue();
    }

    @Test
    void skipsNewestVideoWhenNotBiggerThan125Mb() {
        long age = VideoFileTools.NEWEST_VIDEO_MIN_AGE.toMillis();
        assertThat(VideoFileTools.shouldProcessNewestVideo(125L * 1024 * 1024, age)).isFalse();
        assertThat(VideoFileTools.shouldProcessNewestVideo(1L, age + 60_000)).isFalse();
    }

    @Test
    void skipsNewestVideoWhenModifiedWithin5Minutes() {
        long bigSize = 300L * 1024 * 1024;
        assertThat(VideoFileTools.shouldProcessNewestVideo(bigSize,
                VideoFileTools.NEWEST_VIDEO_MIN_AGE.toMillis() - 1)).isFalse();
        assertThat(VideoFileTools.shouldProcessNewestVideo(bigSize, 0)).isFalse();
    }
}

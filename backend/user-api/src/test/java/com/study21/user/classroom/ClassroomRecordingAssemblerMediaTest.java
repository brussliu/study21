package com.study21.user.classroom;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 続録（停止・開き直し）の結合の検証（**実 ffmpeg で作った本物の webm** を使う）。
 *
 * <p>見張るのは「**タイムスタンプを積み直して 1 本にする**」こと:</p>
 * <ol>
 *   <li>同じ `MediaRecorder` の分塊（1 塊目だけヘッダ）は 1 つのセッションで、そのまま繋ぐ。</li>
 *   <li>停止・開き直しのあとの分塊（ヘッダ有り）は**別セッション**で、時刻を 0 から
 *       積み直してから繋ぐ（重ねない）。</li>
 *   <li>結合した 1 本の長さは**各セッションの合計**と一致する（重なり・欠落が無い）。</li>
 *   <li>結合できないとき（欠落・ffmpeg 失敗）は**前の 1 本を壊さず**、状態を
 *       {@code INCOMPLETE} / {@code FAILED} として残す。</li>
 * </ol>
 *
 * <p>ffmpeg / ffprobe が無い環境ではこのクラスはスキップする（起動できなければ
 * {@link Assumptions#assumeTrue} で落とす）。「結合の道具が無いときは結合せず分塊を残す」
 * ことは {@link ClassroomRecordingAssemblerFfmpegTest} が見張る。</p>
 */
class ClassroomRecordingAssemblerMediaTest {

    private static final long RECORD_ID = 10L;
    private static final String DIR = "classroom/2/202609/rec";
    private static final String FFMPEG = "ffmpeg";
    private static final String FFPROBE = "ffprobe";

    @TempDir
    Path root;

    private ClassroomRecordingStorage storage;
    private ClassroomRecordingFfmpeg media;
    private ClassroomRecordingAssembler assembler;

    @BeforeEach
    void setUp() {
        Assumptions.assumeTrue(hasTool(FFMPEG) && hasTool(FFPROBE),
                "ffmpeg / ffprobe が無い環境ではスキップします");
        storage = new ClassroomRecordingStorage(root.toString());
        storage.prepareRecordingDirectory(DIR);
        media = new ClassroomRecordingFfmpeg(FFMPEG, FFPROBE);
        assembler = new ClassroomRecordingAssembler(storage, media);
    }

    @Test
    @DisplayName("② 同じ録音セッションの分塊は、そのまま繋いで 1 本になる（時刻のずれを作らない）")
    void singleSessionIsConcatenatedAsIs() throws Exception {
        // 1 つの MediaRecorder が 2 回に分けて出した分塊（2 塊目はヘッダ無し）
        Path whole = makeWebm("session", 2.0, 440);
        Path head = splitHead(whole);
        Path body = splitBody(whole);
        rows(List.of(row(1, head, true), row(2, body, false)));

        assertThat(assembler.assembleIfNeeded(record(), rows)).isTrue();

        ClassroomAssembly status = assembler.statusOf(RECORD_ID);
        assertThat(status.state()).isEqualTo(ClassroomAssembly.State.READY);
        assertThat(status.sessionCount()).isEqualTo(1);
        assertThat(Files.isRegularFile(assembled())).isTrue();
        // 結合した長さは元の 1 本と同じ（繋ぎ直しで音を足したり削ったりしていない）
        assertThat(media.probeDurationSeconds(assembled())).isNotNull();
        assertThat(media.probeDurationSeconds(assembled()))
                .isCloseTo(media.probeDurationSeconds(whole), org.assertj.core.data.Offset.offset(0.3));
    }

    @Test
    @DisplayName("② 停止 → 続きの録音は、時刻を積み直して繋ぐ（長さが合計と一致する）")
    void newSessionIsOffsetAndJoined() throws Exception {
        /*
         * 1 回目の録音（2 秒）と、停止 → 続きの録音（3 秒）。続きは**新しい MediaRecorder** なので
         * タイムスタンプが 0 から始まる。そのまま繋ぐと 2 本目が前半へ重なり、長さは 3 秒になる
         * （正しくは 5 秒）。
         */
        Path first = makeWebm("first", 2.0, 440);
        Path second = makeWebm("second", 3.0, 880);
        rows(List.of(
                row(1, first, true, 0.0),
                row(2, second, true, 2.0)));

        assertThat(assembler.assembleIfNeeded(record(), rows)).isTrue();

        ClassroomAssembly status = assembler.statusOf(RECORD_ID);
        assertThat(status.state()).isEqualTo(ClassroomAssembly.State.READY);
        assertThat(status.sessionCount()).isEqualTo(2);
        Double duration = media.probeDurationSeconds(assembled());
        assertThat(duration).isNotNull();
        // 2 秒 + 3 秒 = 5 秒（重なっていれば 3 秒前後になる）
        assertThat(duration).isCloseTo(5.0, org.assertj.core.data.Offset.offset(0.4));
    }

    @Test
    @DisplayName("② 3 回に分けて録っても、長さは合計と一致する（何回停止しても壊れない）")
    void threeSessionsKeepTotalDuration() throws Exception {
        Path one = makeWebm("one", 1.5, 440);
        Path two = makeWebm("two", 2.0, 660);
        Path three = makeWebm("three", 1.0, 880);
        rows(List.of(
                row(1, one, true, 0.0),
                row(2, two, true, 1.5),
                row(3, three, true, 3.5)));

        assertThat(assembler.assembleIfNeeded(record(), rows)).isTrue();

        assertThat(assembler.statusOf(RECORD_ID).sessionCount()).isEqualTo(3);
        assertThat(media.probeDurationSeconds(assembled()))
                .isCloseTo(4.5, org.assertj.core.data.Offset.offset(0.4));
    }

    @Test
    @DisplayName("② 欠落があるときは結合しない（途中までの 1 本を「録れている」と見せない）")
    void incompleteChunksAreNotAssembled() throws Exception {
        Path first = makeWebm("first", 2.0, 440);
        Path third = makeWebm("third", 2.0, 880);
        // 連番 2 が欠けている（最後の分塊がまだ届いていない・送信に失敗した）
        rows(List.of(
                row(1, first, true, 0.0),
                row(3, third, true, 4.0)));

        assertThat(assembler.assembleIfNeeded(record(), rows)).isFalse();

        ClassroomAssembly status = assembler.statusOf(RECORD_ID);
        assertThat(status.state()).isEqualTo(ClassroomAssembly.State.INCOMPLETE);
        assertThat(status.complete()).isFalse();
        assertThat(status.missingSeqs()).containsExactly(2);
        // 分塊は 1 つも失われない
        assertThat(storage.listChunks(DIR, RECORD_ID)).hasSize(2);
        // 途中までの 1 本は作らない（無い、または前の 1 本のまま）
        assertThat(Files.exists(assembled())).isFalse();
    }

    @Test
    @DisplayName("② 実体が欠けている分塊があるときは結合しない（大きさの食い違いも欠落として扱う）")
    void missingEntityIsTreatedAsIncomplete() throws Exception {
        Path first = makeWebm("first", 2.0, 440);
        rows(List.of(row(1, first, true, 0.0)));
        assembler.assembleIfNeeded(record(), rows);
        long previousSize = Files.size(assembled());

        // 続きの録音の**行だけ**が届いている（書き込みが失敗した・掃除の途中）
        ClassroomRecordingChunkEntity second = row(2, makeWebm("second", 1.0, 880), true, 2.0);
        Files.delete(storage.resolve(DIR, second.getFileName()));
        List<ClassroomRecordingChunkEntity> next = new ArrayList<>(rows);
        next.add(second);

        assertThat(assembler.assembleIfNeeded(record(), next)).isFalse();
        ClassroomAssembly status = assembler.statusOf(RECORD_ID);
        assertThat(status.state()).isEqualTo(ClassroomAssembly.State.INCOMPLETE);
        assertThat(status.reason()).contains("実体");
        // 前の 1 本はそのまま（欠けたまま 1 本を作り直さない）
        assertThat(Files.exists(assembled())).isTrue();
        assertThat(Files.size(assembled())).isEqualTo(previousSize);
        // 分塊の行は残っている（欠落として見つけられる）
        assertThat(status.missingSeqs()).containsExactly(2);
    }

    @Test
    @DisplayName("② ffmpeg が失敗したら、前の 1 本を壊さず状態を FAILED にして再試行できる")
    void failedRemuxKeepsPreviousFile() throws Exception {
        Path first = makeWebm("first", 2.0, 440);
        rows(List.of(row(1, first, true, 0.0)));
        assembler.assembleIfNeeded(record(), rows);
        long previousSize = Files.size(assembled());

        // 2 本目のセッションが ffmpeg で読めない（中身が途中で切れた・壊れた）
        Path broken = Files.createFile(root.resolve("broken.webm"));
        Files.write(broken, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, 0x01, 0x02, 0x03, 0x04});
        List<ClassroomRecordingChunkEntity> next = new ArrayList<>(rows);
        next.add(row(2, broken, true, 2.0));

        assertThat(assembler.assembleIfNeeded(record(), next)).isFalse();
        ClassroomAssembly status = assembler.statusOf(RECORD_ID);
        assertThat(status.state()).isEqualTo(ClassroomAssembly.State.FAILED);
        assertThat(status.reason()).contains("再試行");
        // 前の 1 本はそのまま（壊れた 1 本で上書きしない）
        assertThat(Files.exists(assembled())).isTrue();
        assertThat(Files.size(assembled())).isEqualTo(previousSize);
        // 分塊は残っている
        assertThat(storage.listChunks(DIR, RECORD_ID)).hasSize(2);
    }

    @Test
    @DisplayName("② 結合しても、分塊（元の音）は 1 つも消さない")
    void assemblyKeepsAllChunks() throws Exception {
        Path first = makeWebm("first", 1.0, 440);
        Path second = makeWebm("second", 1.0, 880);
        rows(List.of(row(1, first, true, 0.0), row(2, second, true, 1.0)));

        assembler.assembleIfNeeded(record(), rows);

        assertThat(storage.listChunks(DIR, RECORD_ID)).hasSize(2);
        // 作業ファイル（.part）を残さない
        try (var entries = Files.list(storage.resolveDirectory(DIR))) {
            assertThat(entries.map(path -> path.getFileName().toString()).toList())
                    .noneMatch(name -> name.endsWith(".part"));
        }
    }

    // ------------------------------------------------------------------ 資材

    private List<ClassroomRecordingChunkEntity> rows = new ArrayList<>();

    /** 分塊の行を作って置き場へ置く（**固有の場所**に置く＝上書きしない）。 */
    private ClassroomRecordingChunkEntity row(int seq, Path content, boolean containerHead) throws IOException {
        return row(seq, content, containerHead, 0.0);
    }

    private ClassroomRecordingChunkEntity row(int seq, Path content, boolean containerHead, double startSeconds)
            throws IOException {
        ClassroomRecordingStorage.ChunkLocation location =
                storage.newChunkLocation(DIR, RECORD_ID, seq, "audio/webm");
        storage.writeChunkLocation(location, Files.readAllBytes(content));

        ClassroomRecordingChunkEntity entity = new ClassroomRecordingChunkEntity();
        entity.setRecordId(RECORD_ID);
        entity.setSeq(seq);
        entity.setByteSize(Files.size(content));
        entity.setChecksum("checksum-" + seq);
        entity.setStorageDir(location.relativeDir());
        entity.setFileName(location.fileName());
        entity.setMime("audio/webm");
        entity.setContainerHead(containerHead);
        entity.setStartOffsetSeconds(java.math.BigDecimal.valueOf(startSeconds));
        entity.setEndOffsetSeconds(java.math.BigDecimal.valueOf(startSeconds + 1));
        entity.setProcessingStatus(ClassroomModels.CHUNK_STORED);
        entity.setSegmentCount(0);
        return entity;
    }

    private void rows(List<ClassroomRecordingChunkEntity> value) {
        this.rows = new ArrayList<>(value);
    }

    private ClassroomRecordEntity record() {
        ClassroomRecordEntity record = new ClassroomRecordEntity();
        record.setRecordId(RECORD_ID);
        record.setAudioPath(DIR);
        record.setAudioName("recording.webm");
        record.setAudioMime("audio/webm");
        record.setStatus(ClassroomModels.STATUS_RECORDING);
        return record;
    }

    private Path assembled() {
        return storage.resolve(DIR, "recording.webm");
    }

    /** 本物の webm（opus・モノラル）を作る。 */
    private Path makeWebm(String name, double seconds, int frequency) throws Exception {
        Path output = root.resolve(name + ".webm");
        List<String> command = List.of(FFMPEG, "-hide_banner", "-loglevel", "error", "-y",
                "-f", "lavfi", "-i", "sine=frequency=" + frequency + ":sample_rate=48000:duration=" + seconds,
                "-c:a", "libopus", "-b:a", "64k", "-f", "webm", output.toString());
        ClassroomRecordingFfmpeg.runProcess(command, Duration.ofMinutes(1));
        assertThat(Files.size(output)).isGreaterThan(0);
        return output;
    }

    /** webm の先頭（EBML ヘッダ〜最初の Cluster の手前）を取り出す。 */
    private Path splitHead(Path whole) throws IOException {
        byte[] bytes = Files.readAllBytes(whole);
        long offset = RecordingContainerCutter.bodyOffset(bytes, bytes.length);
        assertThat(offset).isGreaterThan(0);
        Path head = root.resolve("head.webm");
        Files.write(head, java.util.Arrays.copyOf(bytes, (int) offset));
        return head;
    }

    /** webm の最初の Cluster から最後までを取り出す（同じストリームの続き）。 */
    private Path splitBody(Path whole) throws IOException {
        byte[] bytes = Files.readAllBytes(whole);
        long offset = RecordingContainerCutter.bodyOffset(bytes, bytes.length);
        assertThat(offset).isGreaterThan(0);
        Path body = root.resolve("body.bin");
        Files.write(body, java.util.Arrays.copyOfRange(bytes, (int) offset, bytes.length));
        return body;
    }

    /** 外部コマンドを 1 回起動する（テストの資材づくりだけに使う）。 */
    private static void run(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        byte[] output = process.getInputStream().readAllBytes();
        if (!process.waitFor(120, java.util.concurrent.TimeUnit.SECONDS) || process.exitValue() != 0) {
            throw new IllegalStateException("コマンドが失敗しました: " + command
                    + " output=" + new String(output, java.nio.charset.StandardCharsets.UTF_8));
        }
    }

    private static boolean hasTool(String tool) {
        try {
            List<String> command = tool.equals(FFMPEG)
                    ? List.of(tool, "-hide_banner", "-version") : List.of(tool, "-version");
            run(command);
            return true;
        } catch (Exception cause) {
            return false;
        }
    }

    /** 未使用の警告を避ける。 */
    private static final Map<String, String> UNUSED = Map.of();
}

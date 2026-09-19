package com.study21.user.classroom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 分塊から再生用の 1 本を組立てる部分の検証（**外部コマンドを使わない範囲**）。
 *
 * <p>見張るのは:</p>
 * <ol>
 *   <li><b>正体は DB の行だけ</b>。置き場のファイルを数えて繋ぐと、トランザクションが失敗して
 *       残った孤立ファイルまで音として混ざる。</li>
 *   <li><b>同じセッション（ヘッダ無しの続き）はそのまま繋ぐ**（1 本目はヘッダを持つ）。</li>
 *   <li>結合の道具（ffmpeg）が無い環境では**結合しない**（時刻が重なった壊れた 1 本を公開しない）。
 *       状態は FAILED にし、**分塊は 1 つも消さない**。</li>
 *   <li>組立ての判定は「1 本が分塊より新しいか」で行う（再生のたびに作り直さない）。</li>
 * </ol>
 *
 * <p>続録（複数セッション）の再多重化は {@link ClassroomRecordingAssemblerMediaTest} が
 * **実 ffmpeg の本物の webm** で見張る。</p>
 */
class ClassroomRecordingAssemblerTest {

    private static final long RECORD_ID = 10L;
    private static final String DIR = "classroom/2/202609/rec";

    @TempDir
    Path root;

    private ClassroomRecordingStorage storage;
    private ClassroomRecordingAssembler assembler;

    private void setUp(ClassroomRecordingFfmpeg media) {
        storage = new ClassroomRecordingStorage(root.toString());
        storage.prepareRecordingDirectory(DIR);
        assembler = new ClassroomRecordingAssembler(storage, media);
    }

    @Test
    @DisplayName("② 同じセッションの分塊（ヘッダ無しの続き）はそのまま繋いで 1 本にする")
    void singleSessionIsConcatenated() throws IOException {
        setUp(ClassroomRecordingFfmpeg.unavailable());
        byte[] first = webmChunk("first".getBytes(StandardCharsets.UTF_8));
        byte[] second = "cluster-payload-2".getBytes(StandardCharsets.UTF_8);
        byte[] third = "end".getBytes(StandardCharsets.UTF_8);
        List<ClassroomRecordingChunkEntity> rows = List.of(
                row(1, first, true), row(2, second, false), row(3, third, false));

        assertThat(assembler.assembleIfNeeded(record(), rows)).isTrue();

        byte[] assembled = readAssembled();
        assertThat(assembled).isEqualTo(concat(first, second, third));
        assertThat(assembler.statusOf(RECORD_ID).state()).isEqualTo(ClassroomAssembly.State.READY);
        assertThat(assembler.statusOf(RECORD_ID).complete()).isTrue();
    }

    @Test
    @DisplayName("② 分塊は到着順ではなく**連番順**に繋ぐ（順不同で届いても時系列が壊れない）")
    void assembleOrdersBySeq() throws IOException {
        setUp(ClassroomRecordingFfmpeg.unavailable());
        byte[] first = webmChunk("one".getBytes(StandardCharsets.UTF_8));
        List<ClassroomRecordingChunkEntity> rows = new ArrayList<>(List.of(
                row(3, "three".getBytes(StandardCharsets.UTF_8), false),
                row(1, first, true),
                row(2, "two".getBytes(StandardCharsets.UTF_8), false)));

        assembler.assembleIfNeeded(record(), rows);

        assertThat(readAssembled()).isEqualTo(concat(first,
                "two".getBytes(StandardCharsets.UTF_8), "three".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 置き場にファイルがあっても、**DB の行が引用していなければ音として使わない**。
     *
     * <p>「ファイルは書けたがトランザクションが失敗した」分塊を混ぜると、1 本の長さと
     * 書き起こしの時間軸が食い違う。孤立ファイルは別の手順（{@code collectOrphanChunks}）で
     * 片付ける。</p>
     */
    @Test
    @DisplayName("② 行から引用されていないファイルは組立てに混ぜない")
    void assembleUsesRowsNotFiles() throws IOException {
        setUp(ClassroomRecordingFfmpeg.unavailable());
        byte[] first = webmChunk("first".getBytes(StandardCharsets.UTF_8));
        // 行が無いファイル（トランザクションが失敗して残った分塊）
        ClassroomRecordingStorage.ChunkLocation orphan =
                storage.newChunkLocation(DIR, RECORD_ID, 2, "audio/webm");
        storage.writeChunkLocation(orphan, "orphan".getBytes(StandardCharsets.UTF_8));

        assembler.assembleIfNeeded(record(), List.of(row(1, first, true)));

        assertThat(readAssembled()).isEqualTo(first);
        // 孤立ファイルは消さない（別の手順が古さを見て片付ける）
        assertThat(Files.exists(storage.resolve(DIR, orphan.fileName()))).isTrue();
    }

    @Test
    @DisplayName("② 結合の道具が無い環境では結合せず、状態を FAILED にして分塊を残す")
    void refusesToJoinWithoutFfmpeg() throws IOException {
        setUp(ClassroomRecordingFfmpeg.unavailable());
        byte[] first = webmChunk("first".getBytes(StandardCharsets.UTF_8));
        byte[] second = webmChunk("second".getBytes(StandardCharsets.UTF_8));
        List<ClassroomRecordingChunkEntity> rows = List.of(row(1, first, true), row(2, second, true));

        assertThat(assembler.assembleIfNeeded(record(), rows)).isFalse();

        ClassroomAssembly status = assembler.statusOf(RECORD_ID);
        assertThat(status.state()).isEqualTo(ClassroomAssembly.State.FAILED);
        assertThat(status.complete()).isFalse();
        // 壊れた 1 本を公開しない
        assertThat(Files.exists(storage.resolve(DIR, "recording.webm"))).isFalse();
        // 分塊は 1 つも消えない
        assertThat(Files.exists(storage.resolve(DIR, rows.get(0).getFileName()))).isTrue();
        assertThat(Files.exists(storage.resolve(DIR, rows.get(1).getFileName()))).isTrue();
    }

    @Test
    @DisplayName("② 分塊が増えたら組み立て直す（増えていなければ作り直さない）")
    void assembleRebuildsOnlyWhenNeeded() throws IOException {
        setUp(ClassroomRecordingFfmpeg.unavailable());
        List<ClassroomRecordingChunkEntity> rows = new ArrayList<>();
        rows.add(row(1, webmChunk("first".getBytes(StandardCharsets.UTF_8)), true));
        assertThat(assembler.assembleIfNeeded(record(), rows)).isTrue();

        /*
         * 2 回目は作り直さない（再生のたびに組立てない）。
         * 判定は「組立てが分塊より後か」なので、分塊の時刻を過去へずらしてその状態を作る。
         */
        backdateChunks(60_000);
        assertThat(assembler.assembleIfNeeded(record(), rows)).isFalse();

        // 分塊が増えた（いまの時刻）＝組立てより新しいので作り直す
        rows.add(row(2, "later".getBytes(StandardCharsets.UTF_8), false));
        backdateAssembled(30_000);
        assertThat(assembler.assembleIfNeeded(record(), rows)).isTrue();
        assertThat(readAssembled()).endsWith("later".getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("② 分塊が 1 つも無い（改修前の録音・取り込み）ときは何もしない")
    void assembleDoesNothingWithoutChunks() {
        setUp(ClassroomRecordingFfmpeg.unavailable());
        assertThat(assembler.assembleIfNeeded(record(), List.of())).isFalse();
        assertThat(assembler.statusOf(RECORD_ID).state()).isEqualTo(ClassroomAssembly.State.NONE);
    }

    @Test
    @DisplayName("② コンテナの切れ目の判定: webm は Cluster まで・Ogg は連結可・MP4 は連結しない")
    void cutterDecidesWhereToCut() {
        byte[] webm = webmChunk("x".getBytes(StandardCharsets.UTF_8));
        long offset = RecordingContainerCutter.bodyOffset(webm, webm.length);
        // 9（EBML ヘッダ）+ 12（Segment。大きさ未定）+ 7（Info）+ 7（Tracks）= 35 バイト目から Cluster
        assertThat(offset).isEqualTo(35);
        assertThat(RecordingContainerCutter.isContainerHead(webm, webm.length)).isTrue();

        // ヘッダの無い分塊（同じストリームの続き）はそのまま繋ぐ
        assertThat(RecordingContainerCutter.bodyOffset("no-header".getBytes(StandardCharsets.UTF_8), 9))
                .isEqualTo(RecordingContainerCutter.CONTINUE);

        // Ogg は連結した論理ビットストリームが規格で認められている
        byte[] ogg = "OggS....".getBytes(StandardCharsets.UTF_8);
        assertThat(RecordingContainerCutter.bodyOffset(ogg, ogg.length))
                .isEqualTo(RecordingContainerCutter.CONTINUE);

        // MP4 は単純に連結できない（勝手に繋がない）
        byte[] mp4 = new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 0, 0, 0, 0};
        assertThat(RecordingContainerCutter.bodyOffset(mp4, mp4.length))
                .isEqualTo(RecordingContainerCutter.NOT_CUT);

        assertThat(RecordingContainerCutter.bodyOffset(null, 0)).isEqualTo(RecordingContainerCutter.CONTINUE);
    }

    @Test
    @DisplayName("② 分塊のファイル名は記録と連番で決まる（新しい名前でも連番が取れる）")
    void chunkFileNameIsStable() {
        assertThat(ClassroomRecordingStorage.chunkFileName(RECORD_ID, 1, "audio/webm"))
                .isEqualTo("chunk-10-000001.webm");
        assertThat(ClassroomRecordingStorage.chunkSeqOf("chunk-10-000012.webm", RECORD_ID)).isEqualTo(12);
        // **新しい書き込み先**（UUID つき）でも連番が取れる（取れないと掃除から漏れる）
        assertThat(ClassroomRecordingStorage.chunkSeqOf(
                "chunk-10-000012-0f3a8b7c9d.webm", RECORD_ID)).isEqualTo(12);
        // 他の記録の分塊は拾わない（置き場を共有していても混ざらない）
        assertThat(ClassroomRecordingStorage.chunkSeqOf("chunk-11-000012.webm", RECORD_ID)).isNull();
        assertThat(ClassroomRecordingStorage.chunkSeqOf("recording.webm", RECORD_ID)).isNull();
    }

    @Test
    @DisplayName("② 分塊の一覧は連番順で、他の記録のファイルを混ぜない")
    void listChunksFiltersByRecord() {
        setUp(ClassroomRecordingFfmpeg.unavailable());
        storage.writeChunk(DIR, ClassroomRecordingStorage.chunkFileName(RECORD_ID, 2, "audio/webm"), new byte[]{2});
        storage.writeChunk(DIR, ClassroomRecordingStorage.chunkFileName(RECORD_ID, 1, "audio/webm"), new byte[]{1});
        storage.writeChunk(DIR, ClassroomRecordingStorage.chunkFileName(99L, 1, "audio/webm"), new byte[]{9});

        List<ClassroomRecordingStorage.StoredChunk> chunks = storage.listChunks(DIR, RECORD_ID);

        assertThat(chunks).extracting(ClassroomRecordingStorage.StoredChunk::seq).containsExactly(1, 2);
    }

    // ------------------------------------------------------------------ 資材

    private ClassroomRecordEntity record() {
        ClassroomRecordEntity record = new ClassroomRecordEntity();
        record.setRecordId(RECORD_ID);
        record.setAudioPath(DIR);
        record.setAudioName("recording.webm");
        record.setAudioMime("audio/webm");
        return record;
    }

    /** 分塊の行を作って置き場へ置く（**固有の場所**へ書く）。 */
    private ClassroomRecordingChunkEntity row(int seq, byte[] content, boolean containerHead) {
        ClassroomRecordingStorage.ChunkLocation location =
                storage.newChunkLocation(DIR, RECORD_ID, seq, "audio/webm");
        storage.writeChunkLocation(location, content);
        ClassroomRecordingChunkEntity entity = new ClassroomRecordingChunkEntity();
        entity.setRecordId(RECORD_ID);
        entity.setSeq(seq);
        entity.setByteSize((long) content.length);
        entity.setChecksum("checksum-" + seq);
        entity.setStorageDir(location.relativeDir());
        entity.setFileName(location.fileName());
        entity.setMime("audio/webm");
        entity.setContainerHead(containerHead);
        entity.setProcessingStatus(ClassroomModels.CHUNK_STORED);
        entity.setSegmentCount(0);
        return entity;
    }

    /** 分塊の時刻を過去へずらす（「組立てのあとに分塊が増えていない」状態を作る）。 */
    private void backdateChunks(long millisAgo) {
        try (var entries = Files.list(storage.resolveDirectory(DIR))) {
            for (Path path : entries.toList()) {
                if (path.getFileName().toString().startsWith("chunk-")) {
                    setModified(path, millisAgo);
                }
            }
        } catch (IOException cause) {
            throw new IllegalStateException(cause);
        }
    }

    /** 再生用の 1 本の時刻を過去へずらす（分塊のほうを新しくする）。 */
    private void backdateAssembled(long millisAgo) {
        setModified(storage.resolve(DIR, "recording.webm"), millisAgo);
    }

    private static void setModified(Path path, long millisAgo) {
        try {
            Files.setLastModifiedTime(path,
                    java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - millisAgo));
        } catch (IOException cause) {
            throw new IllegalStateException(cause);
        }
    }

    private byte[] readAssembled() {
        try {
            return Files.readAllBytes(storage.resolve(DIR, "recording.webm"));
        } catch (IOException cause) {
            throw new IllegalStateException(cause);
        }
    }

    /**
     * テスト用の 1 塊の webm（**先頭にヘッダを持つ**）。
     *
     * <p>EBML ヘッダ（大きさ 4）→ Segment（大きさ未定。ライブ配信の webm と同じ）→
     * Info（大きさ 2）→ Tracks（大きさ 2）→ Cluster（中身は与えたバイト列）。</p>
     */
    private static byte[] webmChunk(byte[] clusterPayload) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3, (byte) 0x84, 0, 0, 0, 0});
        out.writeBytes(new byte[]{0x18, 0x53, (byte) 0x80, 0x67,
                0x01, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF, (byte) 0xFF});
        out.writeBytes(new byte[]{0x15, 0x49, (byte) 0xA9, 0x66, (byte) 0x82, 0x0A, 0x0B});
        out.writeBytes(new byte[]{0x16, 0x54, (byte) 0xAE, 0x6B, (byte) 0x82, 0x0C, 0x0D});
        out.writeBytes(clusterBytes(clusterPayload));
        return out.toByteArray();
    }

    /** Cluster の要素（ID ＋ 大きさ ＋ 中身）。 */
    private static byte[] clusterBytes(byte[] payload) {
        assertThat(payload.length).isLessThan(127);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        out.writeBytes(new byte[]{0x1F, 0x43, (byte) 0xB6, 0x75, (byte) (0x80 | payload.length)});
        out.writeBytes(payload);
        return out.toByteArray();
    }

    private static byte[] concat(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }
}

package com.study21.user.classroom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 保存した分塊から**再生用の 1 本**を組立てる部分の検証。
 *
 * <p>録音は一時停止・画面の開き直しで `MediaRecorder` を作り直すため、2 本目の分塊は
 * **コンテナのヘッダから始まる**。単純に連結するとファイルの途中にもう 1 つ EBML ヘッダが
 * 入り、プレイヤーは最初の回の音しか再生しない。ここでは「ヘッダを外して Cluster だけを
 * 繋ぐ」「連番順に繋ぐ」「短い最後の分塊も落とさない」「分塊の行が無いファイルも拾う」を固定する。</p>
 */
class ClassroomRecordingAssemblerTest {

    private static final long RECORD_ID = 10L;
    /** 同じ `MediaRecorder` が timeslice で切った分塊（1 塊目だけヘッダを持つ）。 */
    private static final byte[] CONTINUATION_CHUNK = "cluster-payload-2".getBytes(StandardCharsets.UTF_8);
    private static final byte[] SHORT_LAST_CHUNK = "end".getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path root;

    @Test
    @DisplayName("2 本目のコンテナ（一時停止・開き直し）はヘッダを外して Cluster だけを繋ぐ")
    void assembleStripsSecondContainerHeader() {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        byte[] first = webmChunk("first-run".getBytes(StandardCharsets.UTF_8));
        byte[] resumed = webmChunk("second-run".getBytes(StandardCharsets.UTF_8));
        writeChunk(storage, 1, first);
        writeChunk(storage, 2, resumed);

        ClassroomRecordingAssembler assembler = new ClassroomRecordingAssembler(storage);
        assertThat(assembler.assembleIfNeeded(record())).isTrue();

        byte[] assembled = readAssembled(storage);
        // 1 塊目はそのまま ＋ 2 塊目は Cluster の ID から後ろだけ
        assertThat(assembled).startsWith(first);
        assertThat(assembled).endsWith(clusterBytes("second-run".getBytes(StandardCharsets.UTF_8)));
        // EBML ヘッダは 1 つだけ（途中にもう 1 つ入っていない）
        assertThat(countOccurrences(assembled, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3})).isEqualTo(1);
    }

    @Test
    @DisplayName("同じストリームの続き（ヘッダ無し）はそのまま繋ぐ。短い最後の分塊も落とさない")
    void assembleKeepsContinuationAndShortLastChunk() {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        byte[] first = webmChunk("first".getBytes(StandardCharsets.UTF_8));
        writeChunk(storage, 1, first);
        writeChunk(storage, 2, CONTINUATION_CHUNK);
        writeChunk(storage, 3, SHORT_LAST_CHUNK);

        ClassroomRecordingAssembler assembler = new ClassroomRecordingAssembler(storage);
        assertThat(assembler.assembleIfNeeded(record())).isTrue();

        byte[] assembled = readAssembled(storage);
        assertThat(assembled).isEqualTo(concat(first, CONTINUATION_CHUNK, SHORT_LAST_CHUNK));
    }

    @Test
    @DisplayName("分塊は到着順ではなく**連番順**に繋ぐ（順不同で届いても時系列が壊れない）")
    void assembleOrdersBySeq() {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        // 3 → 1 → 2 の順に保存された（到着順）
        byte[] first = webmChunk("one".getBytes(StandardCharsets.UTF_8));
        writeChunk(storage, 3, "three".getBytes(StandardCharsets.UTF_8));
        writeChunk(storage, 1, first);
        writeChunk(storage, 2, "two".getBytes(StandardCharsets.UTF_8));

        new ClassroomRecordingAssembler(storage).assembleIfNeeded(record());

        assertThat(readAssembled(storage)).isEqualTo(concat(first,
                "two".getBytes(StandardCharsets.UTF_8), "three".getBytes(StandardCharsets.UTF_8)));
    }

    /**
     * 「ファイルの追記は成功したが DB のトランザクションが失敗した」分塊。
     * 行が無くてもファイルは残っているので、**音は捨てない**（ファイルを正体として組立てる）。
     */
    @Test
    @DisplayName("分塊の行が無いファイルも組立てに含める（DB が失敗した分塊の音を失わない）")
    void assembleUsesFilesNotRows() {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        byte[] first = webmChunk("first".getBytes(StandardCharsets.UTF_8));
        writeChunk(storage, 1, first);
        // 行の登録が失敗した分塊（ファイルだけがある）
        writeChunk(storage, 2, "orphan".getBytes(StandardCharsets.UTF_8));

        new ClassroomRecordingAssembler(storage).assembleIfNeeded(record());

        assertThat(readAssembled(storage)).isEqualTo(concat(first, "orphan".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("分塊が増えたら組み立て直す（増えていなければ作り直さない）")
    void assembleRebuildsOnlyWhenNeeded() {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        byte[] first = webmChunk("first".getBytes(StandardCharsets.UTF_8));
        writeChunk(storage, 1, first);
        ClassroomRecordingAssembler assembler = new ClassroomRecordingAssembler(storage);
        assertThat(assembler.assembleIfNeeded(record())).isTrue();

        /*
         * 2 回目は作り直さない（再生のたびに組立てない）。
         * 判定は「組立てが分塊より後か」で行うので、分塊の時刻を過去へずらしてその状態を作る。
         */
        backdateChunks(storage, 60_000);
        assertThat(assembler.assembleIfNeeded(record())).isFalse();

        writeChunk(storage, 2, "later".getBytes(StandardCharsets.UTF_8));
        // 分塊が増えた（いまの時刻）＝組立てより新しいので作り直す
        backdateAssembled(storage, 30_000);
        assertThat(assembler.assembleIfNeeded(record())).isTrue();
        assertThat(readAssembled(storage)).isEqualTo(concat(first, "later".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    @DisplayName("分塊が 1 つも無い（改修前の録音・取り込み）ときは何もしない")
    void assembleDoesNothingWithoutChunks() {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        assertThat(new ClassroomRecordingAssembler(storage).assembleIfNeeded(record())).isFalse();
    }

    // ------------------------------------------------------------------ 資材

    private ClassroomRecordEntity record() {
        ClassroomRecordEntity record = new ClassroomRecordEntity();
        record.setRecordId(RECORD_ID);
        record.setAudioPath("classroom/2/202609/rec");
        record.setAudioName("recording.webm");
        return record;
    }

    private void writeChunk(ClassroomRecordingStorage storage, int seq, byte[] bytes) {
        storage.writeChunk("classroom/2/202609/rec",
                ClassroomRecordingStorage.chunkFileName(RECORD_ID, seq, "audio/webm"), bytes);
    }

    /** 分塊の時刻を過去へずらす（「組立てのあとに分塊が増えていない」状態を作る）。 */
    private void backdateChunks(ClassroomRecordingStorage storage, long millisAgo) {
        for (ClassroomRecordingStorage.StoredChunk chunk
                : storage.listChunks("classroom/2/202609/rec", RECORD_ID)) {
            setModified(storage.resolve("classroom/2/202609/rec", chunk.fileName()), millisAgo);
        }
    }

    /** 再生用の 1 本の時刻を過去へずらす（分塊のほうを新しくする）。 */
    private void backdateAssembled(ClassroomRecordingStorage storage, long millisAgo) {
        setModified(storage.resolve("classroom/2/202609/rec", "recording.webm"), millisAgo);
    }

    private static void setModified(java.nio.file.Path path, long millisAgo) {
        try {
            java.nio.file.Files.setLastModifiedTime(path,
                    java.nio.file.attribute.FileTime.fromMillis(System.currentTimeMillis() - millisAgo));
        } catch (java.io.IOException cause) {
            throw new IllegalStateException(cause);
        }
    }

    private byte[] readAssembled(ClassroomRecordingStorage storage) {
        try {
            return Files.readAllBytes(storage.resolve("classroom/2/202609/rec", "recording.webm"));
        } catch (java.io.IOException cause) {
            throw new IllegalStateException(cause);
        }
    }

    /**
     * テスト用の 1 塊の webm。
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

    private static int countOccurrences(byte[] haystack, byte[] needle) {
        int count = 0;
        for (int index = 0; index + needle.length <= haystack.length; index += 1) {
            boolean found = true;
            for (int offset = 0; offset < needle.length; offset += 1) {
                if (haystack[index + offset] != needle[offset]) {
                    found = false;
                    break;
                }
            }
            if (found) {
                count += 1;
            }
        }
        return count;
    }

    @Test
    @DisplayName("コンテナの切れ目の判定: webm は Cluster まで・Ogg は連結可・MP4 は連結しない")
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
    @DisplayName("分塊のファイル名は記録と連番で決まる（再送は同じ名前＝増えない）")
    void chunkFileNameIsStable() {
        assertThat(ClassroomRecordingStorage.chunkFileName(RECORD_ID, 1, "audio/webm"))
                .isEqualTo("chunk-10-000001.webm");
        assertThat(ClassroomRecordingStorage.chunkFileName(RECORD_ID, 1, "audio/webm"))
                .isEqualTo(ClassroomRecordingStorage.chunkFileName(RECORD_ID, 1, "audio/webm"));
        assertThat(ClassroomRecordingStorage.chunkSeqOf("chunk-10-000012.webm", RECORD_ID)).isEqualTo(12);
        // 他の記録の分塊は拾わない（置き場を共有していても混ざらない）
        assertThat(ClassroomRecordingStorage.chunkSeqOf("chunk-11-000012.webm", RECORD_ID)).isNull();
        assertThat(ClassroomRecordingStorage.chunkSeqOf("recording.webm", RECORD_ID)).isNull();
    }

    @Test
    @DisplayName("分塊の一覧は連番順で、他の記録のファイルを混ぜない")
    void listChunksFiltersByRecord() {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        storage.writeChunk("classroom/2/202609/rec",
                ClassroomRecordingStorage.chunkFileName(RECORD_ID, 2, "audio/webm"), new byte[]{2});
        storage.writeChunk("classroom/2/202609/rec",
                ClassroomRecordingStorage.chunkFileName(RECORD_ID, 1, "audio/webm"), new byte[]{1});
        storage.writeChunk("classroom/2/202609/rec",
                ClassroomRecordingStorage.chunkFileName(99L, 1, "audio/webm"), new byte[]{9});

        List<ClassroomRecordingStorage.StoredChunk> chunks = storage.listChunks("classroom/2/202609/rec", RECORD_ID);

        assertThat(chunks).extracting(ClassroomRecordingStorage.StoredChunk::seq).containsExactly(1, 2);
    }
}

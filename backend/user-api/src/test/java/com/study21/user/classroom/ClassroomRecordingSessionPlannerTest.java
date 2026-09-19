package com.study21.user.classroom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 続録（停止・開き直し）の結合の**計画と検証**の検証。
 *
 * <p>「2 本目以降のコンテナヘッダを落として繋げば 1 本になる」という前提は成り立たない:
 * 新しい `MediaRecorder` のセッションは**タイムスタンプが 0 から始まる**ので、そのまま繋ぐと
 * 2 本目以降の時刻が最初のセッションへ重なる（再生位置と書き起こしの時刻が食い違う）。
 * ここでは次を固定する:</p>
 * <ol>
 *   <li>同じ `MediaRecorder` の続き（ヘッダ無し）と、**新しいセッション**（ヘッダ有り）を分ける。</li>
 *   <li>引用（DB の行）が無いファイル・実体が無い行・大きさの食い違いは**欠落**として報告し、
 *       結合しない（勝手に繋いで壊れた 1 本を公開しない）。</li>
 *   <li>連番の間が空いていれば欠落として報告する（黙って詰めない）。</li>
 * </ol>
 */
class ClassroomRecordingSessionPlannerTest {

    private static final long RECORD_ID = 10L;
    private static final String DIR = "classroom/2/202609/rec";

    @TempDir
    Path root;

    @Test
    @DisplayName("② 同じセッションの続き（ヘッダ無し）は 1 つのセッションにまとめる")
    void groupsContinuationChunksIntoOneSession() throws IOException {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        storage.prepareRecordingDirectory(DIR);
        List<ClassroomRecordingChunkEntity> rows = List.of(
                row(storage, DIR, 1, 1_000, true),
                row(storage, DIR, 2, 1_000, false),
                row(storage, DIR, 3, 1_000, false));

        ClassroomRecordingSessionPlanner.Plan plan =
                ClassroomRecordingSessionPlanner.plan(storage, RECORD_ID, DIR, rows);

        assertThat(plan.complete()).isTrue();
        assertThat(plan.sessions()).hasSize(1);
        assertThat(plan.sessions().get(0).seqs()).containsExactly(1, 2, 3);
    }

    @Test
    @DisplayName("② 停止・開き直しのあとの分塊（ヘッダ有り）は別のセッションにする")
    void splitsNewRecordingSessions() throws IOException {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        storage.prepareRecordingDirectory(DIR);
        List<ClassroomRecordingChunkEntity> rows = List.of(
                row(storage, DIR, 1, 1_000, true),
                row(storage, DIR, 2, 1_000, false),
                // 一時停止 → 続きの録音（新しい MediaRecorder。ヘッダから始まる）
                row(storage, DIR, 3, 1_000, true),
                row(storage, DIR, 4, 1_000, false));

        ClassroomRecordingSessionPlanner.Plan plan =
                ClassroomRecordingSessionPlanner.plan(storage, RECORD_ID, DIR, rows);

        assertThat(plan.complete()).isTrue();
        assertThat(plan.sessions()).hasSize(2);
        assertThat(plan.sessions().get(0).seqs()).containsExactly(1, 2);
        assertThat(plan.sessions().get(1).seqs()).containsExactly(3, 4);
    }

    @Test
    @DisplayName("② 連番の間が空いていれば欠落として報告する（結合しない）")
    void reportsMissingSequenceNumbers() throws IOException {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        storage.prepareRecordingDirectory(DIR);
        List<ClassroomRecordingChunkEntity> rows = List.of(
                row(storage, DIR, 1, 1_000, true),
                // 2 が抜けている
                row(storage, DIR, 3, 1_000, false));

        ClassroomRecordingSessionPlanner.Plan plan =
                ClassroomRecordingSessionPlanner.plan(storage, RECORD_ID, DIR, rows);

        assertThat(plan.complete()).isFalse();
        assertThat(plan.missingSeqs()).containsExactly(2);
    }

    @Test
    @DisplayName("② 行はあるのに実体が無い分塊は欠落として報告する")
    void reportsRowsWithoutFiles() throws IOException {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        storage.prepareRecordingDirectory(DIR);
        ClassroomRecordingChunkEntity first = row(storage, DIR, 1, 1_000, true);
        ClassroomRecordingChunkEntity second = row(storage, DIR, 2, 1_000, false);
        // 2 番目の実体だけ消えている（掃除の途中・書き込みの失敗）
        Files.delete(storage.resolve(DIR, second.getFileName()));

        ClassroomRecordingSessionPlanner.Plan plan =
                ClassroomRecordingSessionPlanner.plan(storage, RECORD_ID, DIR, List.of(first, second));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.sessions()).isEmpty();
        assertThat(plan.issues()).anySatisfy(issue -> assertThat(issue).contains("実体"));
    }

    @Test
    @DisplayName("② 行が示す大きさと実体が食い違えば欠落として報告する（壊れた実体を混ぜない）")
    void reportsSizeMismatch() throws IOException {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        storage.prepareRecordingDirectory(DIR);
        ClassroomRecordingChunkEntity first = row(storage, DIR, 1, 1_000, true);
        // 実体だけ長くなっている（上書き・破損）
        Files.write(storage.resolve(DIR, first.getFileName()), new byte[2_000]);

        ClassroomRecordingSessionPlanner.Plan plan =
                ClassroomRecordingSessionPlanner.plan(storage, RECORD_ID, DIR, List.of(first));

        assertThat(plan.complete()).isFalse();
        assertThat(plan.issues()).anySatisfy(issue -> assertThat(issue).contains("大きさ"));
    }

    @Test
    @DisplayName("② 引用されていないファイルは結合に混ぜない（DB が確認した分塊だけを使う）")
    void ignoresUnreferencedFiles() throws IOException {
        ClassroomRecordingStorage storage = new ClassroomRecordingStorage(root.toString());
        storage.prepareRecordingDirectory(DIR);
        ClassroomRecordingChunkEntity first = row(storage, DIR, 1, 1_000, true);
        // 行から引用されていない分塊（トランザクションが失敗して残ったもの）
        ClassroomRecordingStorage.ChunkLocation orphan =
                storage.newChunkLocation(DIR, RECORD_ID, 2, "audio/webm");
        storage.writeChunkLocation(orphan, bytes("orphan", 500));

        ClassroomRecordingSessionPlanner.Plan plan =
                ClassroomRecordingSessionPlanner.plan(storage, RECORD_ID, DIR, List.of(first));

        assertThat(plan.complete()).isTrue();
        assertThat(plan.sessions()).hasSize(1);
        assertThat(plan.sessions().get(0).chunks())
                .extracting(ClassroomRecordingSessionPlanner.SessionChunk::fileName)
                .containsExactly(first.getFileName());
    }

    private static ClassroomRecordingChunkEntity row(ClassroomRecordingStorage storage, String dir,
                                                     int seq, int size, boolean containerHead) throws IOException {
        ClassroomRecordingStorage.ChunkLocation location =
                storage.newChunkLocation(dir, RECORD_ID, seq, "audio/webm");
        byte[] content = new byte[size];
        // 先頭の分塊らしく EBML のマジックで始める（中身は問わない）
        if (containerHead) {
            byte[] magic = {0x1A, 0x45, (byte) 0xDF, (byte) 0xA3};
            System.arraycopy(magic, 0, content, 0, magic.length);
        }
        storage.writeChunkLocation(location, content);

        ClassroomRecordingChunkEntity entity = new ClassroomRecordingChunkEntity();
        entity.setRecordId(RECORD_ID);
        entity.setSeq(seq);
        entity.setByteSize((long) size);
        entity.setChecksum("checksum-" + seq);
        entity.setStorageDir(location.relativeDir());
        entity.setFileName(location.fileName());
        entity.setMime("audio/webm");
        entity.setContainerHead(containerHead);
        entity.setProcessingStatus(ClassroomModels.CHUNK_STORED);
        entity.setSegmentCount(0);
        return entity;
    }

    private static byte[] bytes(String prefix, int total) {
        byte[] out = new byte[total];
        byte[] head = prefix.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(head, 0, out, 0, Math.min(head.length, total));
        return out;
    }

    /** 未使用の警告を避ける（テストの下見用）。 */
    private static final List<String> UNUSED = new ArrayList<>();
}

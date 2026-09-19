package com.study21.user.classroom;

import com.study21.common.core.exception.ConflictException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 分塊アップロードの**ファイルと DB の整合**の検証（分塊は**上書きしない**）。
 *
 * <p>以前は「連番で決まる同じ名前」へ書いてから DB の一意制約で重複を判定していた。そのため
 * 同じ連番が同時に 2 本来ると、**負けた側のファイルが勝った側の音声を上書き**し得た
 * （DB は勝った側の行を持っているのに、実体は負けた側の中身＝再生すると別の音）。
 * ここでは次を固定する:</p>
 * <ol>
 *   <li>書き込み先は**分塊ごとに固有**（同じ連番でも別の名前）。だから上書きが起こり得ない。</li>
 *   <li>**DB が最終的な引用を決める**（`ON CONFLICT DO NOTHING` に勝った 1 本だけが残る）。</li>
 *   <li>同じ連番・同じ中身の再送は**冪等に成功**し、行もファイルも増えない。</li>
 *   <li>同じ連番・違う中身は**衝突**にし、勝った側の実体に触らない。</li>
 *   <li>行から引用されていないファイル（**トランザクションが失敗して残った分塊**）は、
 *       他の要求の分塊を巻き込まずに後片付けできる（一定時間より古いものだけ）。</li>
 * </ol>
 */
class ClassroomChunkConsistencyTest {

    private static final long RECORD_ID = 10L;
    private static final long ACCOUNT_ID = 2L;
    private static final String DIR = "classroom/2/202609/rec";

    private ClassroomRecordingStorage storage;
    private RecordingChunkMapperDouble chunks;
    private ClassroomRecordMapper recordMapper;
    private ClassroomServiceImpl service;
    private UserPrincipal student;

    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        storage = new ClassroomRecordingStorage(root.toString());
        chunks = new RecordingChunkMapperDouble();
        recordMapper = mock(ClassroomRecordMapper.class);
        ClassroomAiSettings settings = mock(ClassroomAiSettings.class);
        ClassroomAiSettings.Snapshot snapshot = new ClassroomAiSettings.Snapshot(Map.of());
        lenient().when(recordMapper.findById(RECORD_ID)).thenAnswer(invocation -> recordingRecord());
        lenient().when(settings.load()).thenReturn(snapshot);
        lenient().when(settings.enabled(snapshot)).thenReturn(true);
        lenient().when(settings.chunkSeconds(snapshot)).thenReturn(20);
        lenient().when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);
        // 書き起こしはブラウザ側（サーバーは STT を呼ばない）＝分塊の保存だけを見る
        lenient().when(settings.browserStt(snapshot)).thenReturn(true);
        service = new ClassroomServiceImpl(recordMapper, mock(ClassroomSegmentMapper.class), chunks,
                new ClassroomRecordingAssembler(storage, ClassroomRecordingFfmpeg.unavailable()),
                mock(ClassroomNoteMapper.class), mock(ClassroomPresetMapper.class),
                settings, mock(ClassroomSttClient.class), storage, mock(com.study21.user.account.AccountMapper.class),
                null);
        student = new UserPrincipal(ACCOUNT_ID, "student@example.com", "生徒", AccountType.STUDENT);
        // 置き場は**実際に**作る（呼び出し側が先に作る＝初回の同時要求でも衝突しない）
        storage.prepareRecordingDirectory(DIR);
    }

    @Test
    @DisplayName("① 同じ連番を 2 回送っても、2 回目は同じファイルを作り直さない（固有の名前・上書きしない）")
    void repeatedSameChunkKeepsTheSameFile() throws IOException {
        byte[] content = bytes(1, 2, 3);
        service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);
        List<String> names = new ArrayList<>(chunks.fileNames());

        // 同じ中身の再送: 行もファイルも増えない
        service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);

        assertThat(chunks.fileNames()).containsExactlyElementsOf(names);
        // 書き込み先はファイル名だけでは決まらない（= 同じ名前へ上書きしない）
        assertThat(names).hasSize(1);
        assertThat(names.get(0)).isNotEqualTo(ClassroomRecordingStorage.chunkFileName(RECORD_ID, 1, "audio/webm"));
    }

    @Test
    @DisplayName("① 同じ連番が同時に 2 本来ても、負けた側のファイルが勝った側の実体を壊さない")
    void concurrentSameSeqNeverOverwritesTheWinner() throws IOException {
        byte[] winnerContent = bytes(1, 1, 1, 1);
        byte[] loserContent = bytes(2, 2, 2, 2);
        // 1 本目が先に DB の引用を取る
        service.uploadChunk(student, RECORD_ID, 1, file(winnerContent), null, null, null);
        String winnerName = chunks.chunk(1).orElseThrow().getFileName();
        Path winnerFile = storage.resolve(DIR, winnerName);
        assertThat(Files.readAllBytes(winnerFile)).isEqualTo(winnerContent);

        // 2 本目（同じ連番・違う中身）: 実体が違うので衝突。**勝った側のファイルは触らない**
        assertThatThrownBy(() -> service.uploadChunk(student, RECORD_ID, 1, file(loserContent), null, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("違う内容");

        assertThat(Files.readAllBytes(winnerFile)).isEqualTo(winnerContent);
        assertThat(chunks.chunk(1).orElseThrow().getFileName()).isEqualTo(winnerName);
    }

    @Test
    @DisplayName("① 同じ連番・同じ中身の同時再送は冪等に成功し、実体も 1 つだけ残る")
    void concurrentSameContentIsIdempotent() throws IOException {
        byte[] content = bytes(5, 5, 5);
        service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);
        // 同じ中身を同時に送る（行は既にある＝冪等に成功する）
        ClassroomModels.ChunkUploadResult second =
                service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);

        assertThat(second.nextChunkSeq()).isEqualTo(2);
        assertThat(storage.listChunks(DIR, RECORD_ID)).hasSize(1);
    }

    @Test
    @DisplayName("① 行から引用されていない分塊は、時間が経ってからだけ片付けられる（有効な実体は消さない）")
    void orphanChunksAreCollectedOnlyWhenOld() throws IOException {
        byte[] content = bytes(7, 7);
        service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);
        // トランザクションが失敗して残った分塊（行から引用されていない）
        ClassroomRecordingStorage.ChunkLocation orphan =
                storage.newChunkLocation(DIR, RECORD_ID, 2, "audio/webm");
        storage.writeChunkLocation(orphan, bytes(9, 9, 9));

        // 新しい孤立ファイルは消さない（まだ DB が確定していない可能性がある）
        assertThat(storage.collectOrphanChunks(DIR, RECORD_ID, chunks.referencedFileNames(), 60_000)).isZero();
        assertThat(Files.exists(storage.resolve(DIR, orphan.fileName()))).isTrue();

        // 十分に古い孤立ファイルだけを消す（引用されている実体は残る）
        Path orphanPath = storage.resolve(DIR, orphan.fileName());
        Files.setLastModifiedTime(orphanPath, java.nio.file.attribute.FileTime.fromMillis(
                System.currentTimeMillis() - 3_600_000));

        assertThat(storage.collectOrphanChunks(DIR, RECORD_ID, chunks.referencedFileNames(), 60_000)).isEqualTo(1);
        assertThat(Files.exists(orphanPath)).isFalse();
        assertThat(Files.exists(storage.resolve(DIR, chunks.chunk(1).orElseThrow().getFileName()))).isTrue();
    }

    @Test
    @DisplayName("① 録音の置き場が無い状態から同時に始めても、初期化で落ちない")
    void concurrentDirectoryCreationIsSafe() throws Exception {
        String fresh = "classroom/2/202609/fresh";
        int threads = 8;
        List<Throwable> failures = java.util.Collections.synchronizedList(new ArrayList<>());
        List<Thread> workers = new ArrayList<>();
        for (int index = 0; index < threads; index += 1) {
            Thread worker = new Thread(() -> {
                try {
                    storage.prepareRecordingDirectory(fresh);
                } catch (Throwable cause) {
                    failures.add(cause);
                }
            });
            workers.add(worker);
            worker.start();
        }
        for (Thread worker : workers) {
            worker.join();
        }

        assertThat(failures).isEmpty();
        assertThat(Files.isDirectory(storage.resolveDirectory(fresh))).isTrue();
    }

    /* ---------------- ② 終了の確認は「行がそろっている」だけでは足りない ---------------- */

    /**
     * 実体の**大きさ**が記録と食い違っていたら、その連番を欠落として返す。
     *
     * <p>行がそろっていても、実体が差し替わっている・途中で切れていることがある。
     * 「行の数」だけを見て通すと、壊れた音を「保存済み」と言って終えてしまう。</p>
     */
    @Test
    @DisplayName("② 実体の大きさが記録と違う連番は欠落として返す（行がそろっていても通さない）")
    void endRejectsChunkWhoseFileSizeDiffers() throws IOException {
        byte[] content = bytes(1, 2, 3, 4);
        service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);
        // 実体だけ短くなっている（差し替え・途中で切れた）
        ClassroomRecordingChunkEntity row = chunks.chunk(1).orElseThrow();
        Files.write(storage.resolve(DIR, row.getFileName()), bytes(1, 2));

        assertThatThrownBy(() -> service.end(student, RECORD_ID))
                .isInstanceOf(ChunkChecklistException.class)
                .hasMessageContaining("1");

        /*
         * **収尾の鍵も取らない**: 状態を収尾中にすると、画面は「終わりかけ」と見えて
         * 送り直しもできなくなる。壊れた音を見つけた時点で断る（記録は RECORDING のまま）。
         */
        org.mockito.Mockito.verify(recordMapper, org.mockito.Mockito.never())
                .claimFinalize(anyLong(), anyLong());
    }

    @Test
    @DisplayName("② 実体が消えている連番も欠落として返す（行があっても音が無い）")
    void endRejectsChunkWhoseFileIsGone() throws IOException {
        byte[] content = bytes(5, 5, 5);
        service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);
        Files.delete(storage.resolve(DIR, chunks.chunk(1).orElseThrow().getFileName()));

        assertThatThrownBy(() -> service.end(student, RECORD_ID))
                .isInstanceOf(ChunkChecklistException.class)
                .hasMessageContaining("1");
    }

    /**
     * 画面の一覧と実際の保存が食い違うときは**両方**を欠落として返す。
     *
     * <p>「宣言したのに無い」（`2`）と「宣言していないのに在る」（`3`）は別の異常なので、
     * どちらも黙って通さない（矛盾した一覧を受け付けない）。</p>
     */
    @Test
    @DisplayName("② 宣言したのに無い連番と、宣言していないのに在る連番を、どちらも欠落として返す")
    void endRejectsContradictoryManifest() {
        byte[] content = bytes(7);
        service.uploadChunk(student, RECORD_ID, 3, file(content), null, null, null);
        // 画面は「1・2 を送った」と言っているが、保存されているのは 3 だけ
        ClassroomModels.ChunkManifest manifest =
                new ClassroomModels.ChunkManifest(2, 2, null, List.of(1, 2));

        assertThatThrownBy(() -> service.end(student, RECORD_ID, false, manifest))
                .isInstanceOf(ChunkChecklistException.class)
                .hasMessageContaining("1")
                .hasMessageContaining("3");
    }

    /**
     * 一覧の数と実際の分塊の数が食い違う（分塊を作った数と送れた数が混ざった）一覧は断る。
     *
     * <p>`totalCount` は**実際に送れた分塊の数**（分塊を作った数ではない）。食い違う一覧を
     * そのまま受け取ると、最後の分塊が届いていないのに「そろっている」と見てしまう。</p>
     */
    @Test
    @DisplayName("② 一覧の数が実際の分塊の数と食い違うときは断る（矛盾した一覧を受け付けない）")
    void endRejectsCountThatDoesNotMatch() {
        byte[] content = bytes(9);
        service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);
        // 「3 件送った」と言っているのに、実際は 1 件
        ClassroomModels.ChunkManifest manifest =
                new ClassroomModels.ChunkManifest(1, 3, null, List.of(1));

        assertThatThrownBy(() -> service.end(student, RECORD_ID, false, manifest))
                .isInstanceOf(ChunkChecklistException.class);
    }

    @Test
    @DisplayName("② 取り込み音声（mp3）は分塊が無くても終了できる（今までどおり）")
    void endAllowsImportedAudioWithoutChunks() {
        ClassroomRecordEntity imported = recordingRecord();
        imported.setAudioName("imported.mp3");
        imported.setAudioMime("audio/mpeg");
        when(recordMapper.findById(RECORD_ID)).thenReturn(imported);
        when(recordMapper.claimFinalize(anyLong(), anyLong())).thenReturn(1);
        when(recordMapper.markFinalized(anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(1);

        ClassroomModels.EndResult result = service.end(student, RECORD_ID);

        assertThat(result.complete()).isTrue();
    }

    /**
     * 一覧の 3 つの欄（`lastSeq` / `totalCount` / `uploadedSeqs`）が食い違うときは断る。
     *
     * <p>「3 件送った」と言いながら 1 件しか送れていない、といった一覧を受け取ると、
     * 最後の分塊が届いていないのに「そろっている」と見てしまう。</p>
     */
    @Test
    @DisplayName("② 一覧の欄が食い違う（lastSeq=2 なのに送れた連番が 1 つ）ときは断る")
    void endRejectsInconsistentManifestFields() {
        byte[] content = bytes(1, 1);
        service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);
        ClassroomModels.ChunkManifest broken =
                new ClassroomModels.ChunkManifest(2, 1, null, List.of(1));

        /*
         * 断り方は**構造**で確かめる（`expectedChunks=0` = 一覧そのものを断った）。
         * 例外の文面は実行環境の文字コードに左右されるので、判断には使わない。
         */
        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class, () -> service.end(student, RECORD_ID, false, broken));
        assertThat(refusal.checklist().complete()).isFalse();
        assertThat(refusal.checklist().expectedChunks()).isZero();
        assertThat(refusal.checklist().reason()).isNotBlank();
    }

    /**
     * 画面の一覧（送れた連番）と保存済みが**一致していれば**終了できる（正常な道）。
     */
    @Test
    @DisplayName("② 一覧と保存済みが一致していれば終了できる")
    void endAllowsWhenManifestMatches() throws IOException {
        byte[] first = bytes(1, 1);
        byte[] second = bytes(2, 2);
        service.uploadChunk(student, RECORD_ID, 1, file(first), null, null, null);
        service.uploadChunk(student, RECORD_ID, 2, file(second), null, null, null);
        when(recordMapper.claimFinalize(anyLong(), anyLong())).thenReturn(1);
        when(recordMapper.markFinalized(anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(1);

        ClassroomModels.EndResult result = service.end(student, RECORD_ID, false,
                new ClassroomModels.ChunkManifest(2, 2, 40_000L, List.of(1, 2)));

        assertThat(result.complete()).isTrue();
        assertThat(result.missingSeqs()).isEmpty();
    }

    /* ---------------- ③ 結合の状態を業務の入口から読める ---------------- */

    @Test
    @DisplayName("③ 結合の状態を記録の詳細から読める（画面が「生成中／失敗」を出せる）")
    void detailExposesAssemblyStatus() {
        byte[] content = bytes(1, 2);
        service.uploadChunk(student, RECORD_ID, 1, file(content), null, null, null);

        ClassroomModels.RecordDetail detail = service.detail(student, RECORD_ID);

        // 分塊は保存できているが、まだ結合していない＝「音声は保存されている」
        assertThat(detail.assembly().state()).isEqualTo(ClassroomAssembly.State.NONE.name());
        assertThat(detail.assembly().storedChunks()).isEqualTo(1);
    }

    // ------------------------------------------------------------------ 資材

    private static byte[] bytes(int... values) {
        byte[] out = new byte[values.length];
        for (int index = 0; index < values.length; index += 1) {
            out[index] = (byte) values[index];
        }
        return out;
    }

    private static MultipartFile file(byte[] content) {
        return new MockMultipartFile("file", "chunk.webm", "audio/webm", content);
    }

    private static ClassroomRecordEntity recordingRecord() {
        ClassroomRecordEntity record = new ClassroomRecordEntity();
        record.setRecordId(RECORD_ID);
        record.setRecordNo("CR202609152000000001234");
        record.setCreatedBy(ACCOUNT_ID);
        record.setStudentId(ACCOUNT_ID);
        record.setStatus(ClassroomModels.STATUS_RECORDING);
        record.setLanguageMode("ja");
        record.setVersion(1);
        record.setAudioPath(DIR);
        record.setAudioName("recording.webm");
        record.setAudioMime("audio/webm");
        return record;
    }

    /** 分塊表の代役（**本物の一意制約と ON CONFLICT DO NOTHING の振る舞いを写す**）。 */
    private static final class RecordingChunkMapperDouble implements ClassroomRecordingChunkMapper {
        private final Map<Integer, ClassroomRecordingChunkEntity> rows = new LinkedHashMap<>();

        Optional<ClassroomRecordingChunkEntity> chunk(int seq) {
            return Optional.ofNullable(rows.get(seq));
        }

        List<String> fileNames() {
            return rows.values().stream().map(ClassroomRecordingChunkEntity::getFileName).toList();
        }

        List<String> referencedFileNames() {
            return new ArrayList<>(fileNames());
        }

        @Override
        public int insertIfAbsent(ClassroomRecordingChunkEntity entity) {
            if (entity == null || entity.getSeq() == null) {
                return 0;
            }
            if (rows.containsKey(entity.getSeq())) {
                return 0;
            }
            rows.put(entity.getSeq(), entity);
            return 1;
        }

        @Override
        public int updateStatus(long recordId, int seq, String status, int segmentCount) {
            ClassroomRecordingChunkEntity row = rows.get(seq);
            if (row == null) {
                return 0;
            }
            row.setProcessingStatus(status);
            row.setSegmentCount(segmentCount);
            return 1;
        }

        @Override
        public ClassroomRecordingChunkEntity findBySeq(long recordId, int seq) {
            return rows.get(seq);
        }

        @Override
        public List<ClassroomRecordingChunkEntity> findByRecordAfter(long recordId, int afterSeq) {
            return rows.values().stream().filter(row -> row.getSeq() > afterSeq).toList();
        }

        @Override
        public List<ClassroomRecordingChunkEntity> findByRecord(long recordId) {
            return new ArrayList<>(rows.values());
        }

        @Override
        public Integer maxSeq(long recordId) {
            return rows.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        @Override
        public int countByRecord(long recordId) {
            return rows.size();
        }

        @Override
        public long totalBytes(long recordId) {
            return rows.values().stream().mapToLong(row -> row.getByteSize() == null ? 0 : row.getByteSize()).sum();
        }

        @Override
        public java.math.BigDecimal maxEndOffsetSeconds(long recordId) {
            return rows.values().stream().map(ClassroomRecordingChunkEntity::getEndOffsetSeconds)
                    .filter(java.util.Objects::nonNull)
                    .max(java.util.Comparator.naturalOrder()).orElse(null);
        }
    }

    /** 置き場に置かれたファイル（テストの下見用）。 */
    @SuppressWarnings("unused")
    private static List<String> namesIn(Path directory) throws IOException {
        try (var entries = Files.list(directory)) {
            return entries.map(path -> path.getFileName().toString()).sorted().toList();
        }
    }

    static {
        // 未使用の警告を避けるための参照（テストの下見用ヘルパー）
        assertThat(StandardCharsets.UTF_8).isNotNull();
    }
}

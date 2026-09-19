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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 最終分塊一覧（manifest）の**契約**の検証。
 *
 * <p>画面と後端が同じ意味で読む欄を固定する:</p>
 * <ul>
 *   <li>`expectedLastSeq` … **実際に録れた**最後の連番（分塊を作った時点で決まる）。</li>
 *   <li>`expectedCount` … **実際に録れた**分塊の数。</li>
 *   <li>`expectedEndSample` … 最後に**録れた**分塊が終わる統一時間軸の位置（16kHz のサンプル数）。</li>
 *   <li>`uploadedSeqs` … **送信の応答を受け取れた**連番（**補助情報**。保存の事実ではない）。</li>
 * </ul>
 *
 * <p>以前は「送れた範囲」をそのまま「そろっている範囲」として扱っていた。そのため</p>
 * <ol>
 *   <li>最後の分塊の応答だけ失われた回（**後端には保存できている**）に、後端が
 *       「足りない」と誤って断る、</li>
 *   <li>逆に画面が「作った数」を送ると、届いていない最後の分塊を見落とす、</li>
 * </ol>
 * <p>という**どちら向きにも**壊れ得た。ここは「後端に保存されている事実」を正とする。</p>
 */
class ClassroomManifestProtocolTest {

    private static final long RECORD_ID = 10L;
    private static final long ACCOUNT_ID = 2L;
    private static final String DIR = "classroom/2/202609/rec";
    /** 1 分塊 = 20 秒（設定の既定と同じ）。 */
    private static final int CHUNK_SECONDS = 20;
    private static final long SAMPLE_RATE = 16_000L;

    private ClassroomRecordingStorage storage;
    private ChunkTable chunks;
    private ClassroomRecordMapper recordMapper;
    private ClassroomServiceImpl service;
    private UserPrincipal student;

    @TempDir
    Path root;

    @BeforeEach
    void setUp() {
        storage = new ClassroomRecordingStorage(root.toString());
        chunks = new ChunkTable();
        recordMapper = mock(ClassroomRecordMapper.class);
        ClassroomAiSettings settings = mock(ClassroomAiSettings.class);
        ClassroomAiSettings.Snapshot snapshot = new ClassroomAiSettings.Snapshot(Map.of());
        lenient().when(recordMapper.findById(RECORD_ID)).thenAnswer(invocation -> recordingRecord());
        lenient().when(settings.load()).thenReturn(snapshot);
        lenient().when(settings.enabled(snapshot)).thenReturn(true);
        lenient().when(settings.chunkSeconds(snapshot)).thenReturn(CHUNK_SECONDS);
        lenient().when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);
        lenient().when(settings.browserStt(snapshot)).thenReturn(true);
        service = new ClassroomServiceImpl(recordMapper, mock(ClassroomSegmentMapper.class), chunks,
                new ClassroomRecordingAssembler(storage, ClassroomRecordingFfmpeg.unavailable()),
                mock(ClassroomNoteMapper.class), mock(ClassroomPresetMapper.class),
                settings, mock(ClassroomSttClient.class), storage,
                mock(com.study21.user.account.AccountMapper.class), null);
        // 組立ては**その場で**走らせる（背景のままだと、置き場を片付ける前に走って試験が不安定になる）
        service.setBackgroundRunner(Runnable::run);
        student = new UserPrincipal(ACCOUNT_ID, "student@example.com", "生徒", AccountType.STUDENT);
        storage.prepareRecordingDirectory(DIR);
    }

    /* ---------------- ① 実際に録れた範囲と、保存された事実 ---------------- */

    @Test
    @DisplayName("① 10 個録れて 9 個しか保存できていないときは、普通の終了を断り 10 番を欠落として返す")
    void endRefusesWhenLastChunkWasNeverSaved() throws IOException {
        // サーバーには 1〜9 だけが保存されている（10 番は届いていない）
        save(1, 9);
        // 画面は「10 個録れて、1〜9 の応答は受け取った」と言っている
        ClassroomModels.ChunkManifest manifest = manifest(10, 10, endSampleOf(10), seqs(1, 9), List.of());

        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class, () -> service.end(student, RECORD_ID, false, manifest));

        ClassroomModels.ChunkChecklist checklist = refusal.checklist();
        assertThat(checklist.complete()).isFalse();
        // **10 番が欠けている**と分かる（文面ではなく構造で判断する）
        assertThat(checklist.missingSeqs()).containsExactly(10);
        assertThat(checklist.reasonCode()).isEqualTo(ClassroomModels.CHECK_MISSING);
        // 録れた数（期待）と保存できた数を区別して返す
        assertThat(checklist.expectedLastSeq()).isEqualTo(10);
        assertThat(checklist.savedSeqs()).containsExactlyElementsOf(seqs(1, 9));
    }

    @Test
    @DisplayName("① 応答だけ失われた分塊は、後端に保存されているので「欠落」にしない（ACK ではなく保存の事実を見る）")
    void lostResponseIsReconciledFromWhatServerStored() throws IOException {
        // 10 個すべて保存できている（10 番目の応答だけ画面へ届かなかった）
        save(1, 10);
        ClassroomModels.ChunkManifest manifest = manifest(10, 10, endSampleOf(10), seqs(1, 9), List.of());
        allowFinalize();

        ClassroomModels.EndResult result = service.end(student, RECORD_ID, false, manifest);

        assertThat(result.complete()).isTrue();
        assertThat(result.missingSeqs()).isEmpty();
    }

    @Test
    @DisplayName("① 保存されている分塊の一覧を返して画面が照合できる（不足・破損・余分は別の理由コード）")
    void checklistReportsSavedAndBrokenAndExtraSeparately() throws IOException {
        save(1, 3);
        // 2 番の実体だけ消す（行はあるが音が無い）
        Files.delete(chunkFile(2));
        // 画面は 1〜3 を録ったと言い、4 番が余分に保存されている（食い違い）
        save(4, 4);
        ClassroomModels.ChunkManifest manifest = manifest(3, 3, endSampleOf(3), seqs(1, 3), List.of());

        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class, () -> service.end(student, RECORD_ID, false, manifest));

        ClassroomModels.ChunkChecklist checklist = refusal.checklist();
        assertThat(checklist.brokenSeqs()).containsExactly(2);
        assertThat(checklist.extraSeqs()).containsExactly(4);
        assertThat(checklist.missingSeqs()).containsExactly(2, 4);
        assertThat(checklist.reasonCode()).isEqualTo(ClassroomModels.CHECK_BROKEN);
        assertThat(checklist.savedSeqs()).containsExactly(1, 3);
    }

    @Test
    @DisplayName("① 行はあるのに実体が壊れている（大きさが違う）ときも破損として返す")
    void damagedFileIsReportedAsBroken() throws IOException {
        save(1, 2);
        Files.write(chunkFile(1), new byte[]{9, 9, 9, 9, 9, 9});

        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class,
                () -> service.end(student, RECORD_ID, false, manifest(2, 2, endSampleOf(2), seqs(1, 2), List.of())));

        assertThat(refusal.checklist().brokenSeqs()).containsExactly(1);
        assertThat(refusal.checklist().reasonCode()).isEqualTo(ClassroomModels.CHECK_BROKEN);
    }

    /* ---------------- ② 一覧そのものの矛盾と上限 ---------------- */

    @Test
    @DisplayName("② 録れた数と最後の連番が合わない一覧は「矛盾」として断る（録れた数は 1..last の件数）")
    void contradictoryCountIsRefused() throws IOException {
        save(1, 3);
        // 「3 番まで録れた」なのに「録れた数は 2」
        ClassroomModels.ChunkManifest broken = manifest(3, 2, endSampleOf(3), seqs(1, 3), List.of());

        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class, () -> service.end(student, RECORD_ID, false, broken));

        assertThat(refusal.checklist().reasonCode()).isEqualTo(ClassroomModels.CHECK_MANIFEST_CONTRADICTION);
        assertThat(refusal.checklist().complete()).isFalse();
    }

    @Test
    @DisplayName("② 録れた範囲の外を「送れた」と言う一覧は断る（範囲外）")
    void uploadedOutsideExpectedRangeIsRefused() throws IOException {
        save(1, 2);
        ClassroomModels.ChunkManifest weird = manifest(2, 2, endSampleOf(2), List.of(1, 2, 5), List.of());

        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class, () -> service.end(student, RECORD_ID, false, weird));

        assertThat(refusal.checklist().reasonCode()).isEqualTo(ClassroomModels.CHECK_MANIFEST_CONTRADICTION);
    }

    @Test
    @DisplayName("② 送れた連番の重複も断る")
    void duplicatedUploadedSeqIsRefused() throws IOException {
        save(1, 2);
        ClassroomModels.ChunkManifest duplicated = manifest(2, 2, endSampleOf(2), List.of(1, 1, 2), List.of());

        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class, () -> service.end(student, RECORD_ID, false, duplicated));

        assertThat(refusal.checklist().reasonCode()).isEqualTo(ClassroomModels.CHECK_MANIFEST_CONTRADICTION);
    }

    @Test
    @DisplayName("② 現実にあり得ない件数の一覧（上限超え）は、調べる前に断る")
    void absurdlyLargeManifestIsRefused() throws IOException {
        save(1, 1);
        // 20 秒の分塊が 24 時間ぶん = 4320 個。それを大きく超える宣言は受け付けない
        ClassroomModels.ChunkManifest huge = manifest(1_000_000, 1_000_000, null, List.of(), List.of());

        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class, () -> service.end(student, RECORD_ID, false, huge));

        assertThat(refusal.checklist().reasonCode()).isEqualTo(ClassroomModels.CHECK_MANIFEST_CONTRADICTION);
        assertThat(refusal.checklist().reason()).contains("上限");
    }

    /* ---------------- ③ 終わりの位置（endSample）を使う ---------------- */

    @Test
    @DisplayName("③ 終わりの位置が最後の分塊の終わりと食い違う一覧は断る（受理して捨てない）")
    void endSampleMismatchIsRefused() throws IOException {
        save(1, 2);
        // 実際は 20 秒 × 2 = 40 秒 = 640,000 サンプル。90 秒と言っている＝録れた範囲と食い違う
        ClassroomModels.ChunkManifest mismatched =
                manifest(2, 2, 90L * SAMPLE_RATE, seqs(1, 2), List.of());

        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class, () -> service.end(student, RECORD_ID, false, mismatched));

        assertThat(refusal.checklist().reasonCode()).isEqualTo(ClassroomModels.CHECK_END_SAMPLE_MISMATCH);
        assertThat(refusal.checklist().endSample()).isEqualTo(90L * SAMPLE_RATE);
    }

    @Test
    @DisplayName("③ 終わりの位置は、分塊 1 つぶんの誤差までは許す（端数のため）")
    void endSampleWithinOneChunkIsAccepted() throws IOException {
        save(1, 2);
        allowFinalize();
        // 実際は 40 秒。端数（1 秒未満）のずれは許す
        ClassroomModels.ChunkManifest near = manifest(2, 2, 40L * SAMPLE_RATE + 8_000L, seqs(1, 2), List.of());

        ClassroomModels.EndResult result = service.end(student, RECORD_ID, false, near);

        assertThat(result.complete()).isTrue();
    }

    /* ---------------- ④ 旧い画面（録れた範囲を送らない）の互換 ---------------- */

    @Test
    @DisplayName("④ 録れた範囲を送らない旧い画面は、保存済みの範囲で確かめる（最後の分塊は保証できないと分かる形で）")
    void legacyManifestFallsBackToStoredRange() throws IOException {
        save(1, 3);
        allowFinalize();
        // 旧い画面: lastSeq/totalCount/uploadedSeqs だけ
        ClassroomModels.ChunkManifest legacy =
                new ClassroomModels.ChunkManifest(3, 3, null, seqs(1, 3));

        ClassroomModels.EndResult result = service.end(student, RECORD_ID, false, legacy);

        assertThat(result.complete()).isTrue();
        // 「実際に録れた範囲」は分からないので 0（＝最後の分塊を証明したとは言わない）
        assertThat(result.expectedLastSeq()).isZero();
    }

    @Test
    @DisplayName("④ 録れた範囲を送らない旧い画面でも、保存済みが足りなければ断る")
    void legacyManifestStillRefusesGap() throws IOException {
        save(1, 1);
        save(3, 3);
        ClassroomModels.ChunkManifest legacy =
                new ClassroomModels.ChunkManifest(3, 3, null, List.of(1, 3));

        ChunkChecklistException refusal = org.junit.jupiter.api.Assertions.assertThrows(
                ChunkChecklistException.class, () -> service.end(student, RECORD_ID, false, legacy));

        assertThat(refusal.checklist().missingSeqs()).containsExactly(2);
        assertThat(refusal.checklist().reasonCode()).isEqualTo(ClassroomModels.CHECK_MISSING);
    }

    /* ---------------- ⑤ 取り込み音声・文字だけの流れ ---------------- */

    @Test
    @DisplayName("⑤ 取り込み音声（mp3）は分塊の一覧で断らない（今までどおり終われる）")
    void importedAudioIsNotCheckedAgainstChunks() {
        ClassroomRecordEntity imported = recordingRecord();
        imported.setAudioName("imported.mp3");
        imported.setAudioMime("audio/mpeg");
        when(recordMapper.findById(RECORD_ID)).thenReturn(imported);
        allowFinalize();
        // 分塊は 1 つも無いのに、録れた範囲は 5 と言っている（取り込みには無関係）
        ClassroomModels.ChunkManifest manifest = manifest(5, 5, null, seqs(1, 5), List.of());

        ClassroomModels.EndResult result = service.end(student, RECORD_ID, false, manifest);

        assertThat(result.complete()).isTrue();
    }

    /* ---------------- ⑥ 明示の不完全終了 ---------------- */

    @Test
    @DisplayName("⑥ 明示の不完全終了は、確認した欠落を返しつつ通り、失う範囲を残す")
    void forcedEndKeepsLossRecord() throws IOException {
        save(1, 9);
        allowFinalize();
        ClassroomModels.ChunkManifest manifest = manifest(10, 10, endSampleOf(10), seqs(1, 9), List.of());

        ClassroomModels.EndResult result = service.end(student, RECORD_ID, true, manifest);

        assertThat(result.forced()).isTrue();
        assertThat(result.complete()).isFalse();
        assertThat(result.missingSeqs()).containsExactly(10);
        // 何を失ったかを後から読める形で残す（「一部が保存できていません」だけでは分からない）
        assertThat(result.notice()).contains("10");
        assertThat(result.lossSeqs()).containsExactly(10);
    }

    /* ---------------- 資材 ---------------- */

    private void allowFinalize() {
        lenient().when(recordMapper.claimFinalize(anyLong(), anyLong())).thenReturn(1);
        lenient().when(recordMapper.markFinalized(anyLong(), anyInt(), anyInt(), anyLong(), anyInt(), any(), any(), any(), any(), any()))
                .thenReturn(1);
    }

    /** 連番 `from`〜`to` の分塊を、実体のファイルごと保存する。 */
    private void save(int from, int to) throws IOException {
        for (int seq = from; seq <= to; seq += 1) {
            byte[] content = new byte[32];
            content[0] = (byte) seq;
            service.uploadChunk(student, RECORD_ID, seq, file(content), null,
                    java.math.BigDecimal.valueOf((long) (seq - 1) * CHUNK_SECONDS),
                    java.math.BigDecimal.valueOf((long) seq * CHUNK_SECONDS));
        }
    }

    private Path chunkFile(int seq) {
        ClassroomRecordingChunkEntity row = chunks.bySeq.get(seq);
        return storage.resolve(DIR, row.getFileName());
    }

    private static ClassroomModels.ChunkManifest manifest(int lastSeq, int count, Long endSample,
                                                          List<Integer> uploaded, List<Integer> unrecoverable) {
        return new ClassroomModels.ChunkManifest(lastSeq, count, uploaded, endSample, unrecoverable);
    }

    /** 連番 `1..last` の終わり（16kHz のサンプル数）。 */
    private static long endSampleOf(int last) {
        return (long) last * CHUNK_SECONDS * SAMPLE_RATE;
    }

    private List<Integer> seqs(int from, int to) {
        List<Integer> out = new ArrayList<>();
        for (int seq = from; seq <= to; seq += 1) {
            out.add(seq);
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

    /** 分塊表の代役（一意制約と ON CONFLICT DO NOTHING の振る舞いを写す）。 */
    private static final class ChunkTable implements ClassroomRecordingChunkMapper {
        private final Map<Integer, ClassroomRecordingChunkEntity> bySeq = new LinkedHashMap<>();

        @Override
        public int insertIfAbsent(ClassroomRecordingChunkEntity entity) {
            if (entity == null || entity.getSeq() == null || bySeq.containsKey(entity.getSeq())) {
                return 0;
            }
            bySeq.put(entity.getSeq(), entity);
            return 1;
        }

        @Override
        public int updateStatus(long recordId, int seq, String status, int segmentCount) {
            ClassroomRecordingChunkEntity row = bySeq.get(seq);
            if (row == null) {
                return 0;
            }
            row.setProcessingStatus(status);
            row.setSegmentCount(segmentCount);
            return 1;
        }

        @Override
        public ClassroomRecordingChunkEntity findBySeq(long recordId, int seq) {
            return bySeq.get(seq);
        }

        @Override
        public List<ClassroomRecordingChunkEntity> findByRecordAfter(long recordId, int afterSeq) {
            return bySeq.values().stream().filter(row -> row.getSeq() > afterSeq).toList();
        }

        @Override
        public List<ClassroomRecordingChunkEntity> findByRecord(long recordId) {
            return new ArrayList<>(bySeq.values());
        }

        @Override
        public Integer maxSeq(long recordId) {
            return bySeq.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
        }

        @Override
        public int countByRecord(long recordId) {
            return bySeq.size();
        }

        @Override
        public long totalBytes(long recordId) {
            return bySeq.values().stream()
                    .mapToLong(row -> row.getByteSize() == null ? 0 : row.getByteSize()).sum();
        }

        @Override
        public java.math.BigDecimal maxEndOffsetSeconds(long recordId) {
            return bySeq.values().stream().map(ClassroomRecordingChunkEntity::getEndOffsetSeconds)
                    .filter(java.util.Objects::nonNull)
                    .max(java.util.Comparator.naturalOrder()).orElse(null);
        }

        Optional<ClassroomRecordingChunkEntity> chunk(int seq) {
            return Optional.ofNullable(bySeq.get(seq));
        }
    }

    static {
        // 未使用の警告を避ける（この型は保存の事実だけを見る）
        assertThat(ConflictException.class).isNotNull();
    }
}

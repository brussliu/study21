package com.study21.user.classroom;

import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.user.account.AccountMapper;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 授業録音の終了処理の検証。
 *
 * <p>転写セグメントが 1 件も無いまま終了したときに、最終まとめ（FINAL）の行を作らないことを
 * 固定する。作ってしまうと DDL の {@code CK_CR_授業ノート_範囲}（終了連番 >= 開始連番）に
 * 違反し、{@code @Transactional} のため終了そのものが失敗して記録が RECORDING のまま
 * 残ってしまう（実際に発生した不具合）。</p>
 */
@ExtendWith(MockitoExtension.class)
class ClassroomServiceImplTest {

    /** 実体を置く一時的な置き場（テストごとに消える）。 */
    @org.junit.jupiter.api.io.TempDir
    java.nio.file.Path stubFiles;

    private static final long RECORD_ID = 10L;
    private static final long ACCOUNT_ID = 2L;

    @Mock
    private ClassroomRecordMapper recordMapper;
    @Mock
    private ClassroomSegmentMapper segmentMapper;
    @Mock
    private ClassroomRecordingChunkMapper chunkMapper;
    @Mock
    private ClassroomNoteMapper noteMapper;
    @Mock
    private ClassroomPresetMapper presetMapper;
    @Mock
    private ClassroomAiSettings settings;
    @Mock
    private ClassroomSttClient sttClient;
    @Mock
    private ClassroomRecordingStorage storage;
    @Mock
    private AccountMapper accountMapper;

    private ClassroomServiceImpl service;
    private ClassroomAiSettings.Snapshot snapshot;
    private UserPrincipal student;

    @BeforeEach
    void setUp() {
        service = new ClassroomServiceImpl(recordMapper, segmentMapper, chunkMapper,
                new ClassroomRecordingAssembler(storage), noteMapper, presetMapper,
                settings, sttClient, storage, accountMapper, null);
        snapshot = new ClassroomAiSettings.Snapshot(Map.of());
        student = new UserPrincipal(ACCOUNT_ID, "student@example.com", "生徒", AccountType.STUDENT);
        // AI 解析（ノート生成）は未設定なら有効（本番の flag(KEY_NOTE_ENABLED, true) と同じ既定）。
        // 無効のケースは各テストで上書きする。
        lenient().when(settings.noteEnabled(any())).thenReturn(true);
        stubChunkTable();
        stubChunkStorage();
    }

    @Test
    @DisplayName("書き起こしが無いまま終了しても 200 で終わり、最終まとめの行は作らない")
    void endWithoutTranscriptSkipsFinalNote() {
        stubContinuousChunks();
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(recordMapper.markFinalized(eq(RECORD_ID), anyInt(), anyInt(), eq(ACCOUNT_ID), anyInt()))
                .thenReturn(1);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);

        ClassroomModels.EndResult result = service.end(student, RECORD_ID);

        assertThat(result.status()).isEqualTo(ClassroomModels.STATUS_STOPPED);
        assertThat(result.finalNoteId()).isNull();
        assertThat(result.runPath()).isNull();
        assertThat(result.notice()).isNotBlank();
        // ノート行を作らないので、admin-api のバッチ起動 URL も返さない
        verify(noteMapper, never()).insert(any());
    }

    @Test
    @DisplayName("書き起こしがあれば最終まとめの行を全範囲（1〜最後）で作る")
    void endWithTranscriptCreatesFinalNote() {
        stubContinuousChunks();
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(recordMapper.markFinalized(eq(RECORD_ID), anyInt(), anyInt(), eq(ACCOUNT_ID), anyInt()))
                .thenReturn(1);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(3);
        doAnswer(invocation -> {
            ((ClassroomNoteEntity) invocation.getArgument(0)).setNoteId(99L);
            return 1;
        }).when(noteMapper).insert(any());

        ClassroomModels.EndResult result = service.end(student, RECORD_ID);

        ArgumentCaptor<ClassroomNoteEntity> captor = ArgumentCaptor.forClass(ClassroomNoteEntity.class);
        verify(noteMapper).insert(captor.capture());
        ClassroomNoteEntity note = captor.getValue();
        assertThat(note.getKind()).isEqualTo(ClassroomModels.NOTE_FINAL);
        assertThat(note.getStartSeq()).isEqualTo(1);
        assertThat(note.getEndSeq()).isEqualTo(3);
        assertThat(note.getStatus()).isEqualTo(ClassroomModels.NOTE_PENDING);
        assertThat(result.finalNoteId()).isEqualTo(99L);
        assertThat(result.runPath()).isNotBlank();
        assertThat(result.notice()).isNull();
    }

    @Test
    @DisplayName("冪等: 更新が競合したときはノート行を作らずに 409 で終わる")
    void endRejectsWhenVersionConflicts() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        // 分塊は 1 から連続している（分塊の確認で断らないように）
        storedChunks(1);
        // **収尾の鍵を取れない**＝別の要求が先に収尾を始めている（二重の終了を防ぐ）
        when(recordMapper.claimFinalize(eq(RECORD_ID), eq(ACCOUNT_ID))).thenReturn(0);

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                com.study21.common.core.exception.ConflictException.class,
                () -> service.end(student, RECORD_ID))).isNotNull();
        // ノート行も作らない（終わりかけの状態で最終まとめを固定しない）
        verify(noteMapper, never()).insert(any());
    }

    /* ---------------- 終了のサーバー側検証（利用者の指示 7・8・9） ---------------- */

    /**
     * **収尾が済んでいないまま終了させない**（利用者の指示 7）。
     *
     * <p>画面の【授業を終了】を信じると、まだ保存されていない尾部の文が最終まとめの範囲から
     * 落ちる（静かな取りこぼし）。サーバー側で断り、**記録も終わらせない**（終わらせてしまうと、
     * やり直しても RECORDING ではないので収尾を受け付けられない）。</p>
     */
    @Test
    @DisplayName("終了: 収尾が済んでいない音源があれば 409 で断り、記録も終わらせない")
    void endRefusesWhenFinalizeIncomplete() {
        stubContinuousChunks();
        stubFinalize(new ClassroomSttStreamService.FinalizeStatus(RECORD_ID, "AUDIO_ACCEPTING",
                "書き起こし中です", false, true, true,
                "書き起こしの収尾が済んでいない音源があります。",
                java.util.List.of(
                        source("mic", ClassroomSttStreamService.FINALIZE_AUDIO_ACCEPTING, false, true,
                                "収尾がまだです。", 0, true),
                        source("shared", ClassroomSttStreamService.FINALIZE_NOT_STARTED, true, false,
                                null, 0, false))));
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(3);

        com.study21.common.core.exception.ConflictException cause =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.study21.common.core.exception.ConflictException.class,
                        () -> service.end(student, RECORD_ID));

        assertThat(cause.getMessage()).contains("収尾").contains("マイク").contains("収尾がまだです");
        // **記録は終わらせない**（終わらせると、やり直しの収尾を受け付けられなくなる）
        verify(recordMapper, never()).claimFinalize(anyLong(), anyLong());
        verify(noteMapper, never()).insert(any());
    }

    /** 書き起こしがあるのに**音声の分塊が 1 つも無い**なら断る（音は後から作り直せない）。 */
    @Test
    @DisplayName("終了: 書き起こしがあるのに分塊が 1 つも無ければ 409 で断る")
    void endRefusesWhenChunksMissing() {
        stubFinalize(completedStatus(java.util.List.of(
                source("mic", ClassroomSttStreamService.FINALIZE_SAVED, true, false, null, 2, true),
                source("shared", ClassroomSttStreamService.FINALIZE_NOT_STARTED, true, false, null, 0, false))));
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(2);
        // 分塊の表は空（音声が 1 つも保存されていない）
        when(chunkMapper.findByRecord(RECORD_ID)).thenReturn(java.util.List.of());

        com.study21.common.core.exception.ConflictException cause =
                org.junit.jupiter.api.Assertions.assertThrows(
                        com.study21.common.core.exception.ConflictException.class,
                        () -> service.end(student, RECORD_ID));

        assertThat(cause.getMessage()).contains("分塊").contains("そろっていません");
        verify(recordMapper, never()).claimFinalize(anyLong(), anyLong());
    }

    /**
     * やり直しても直らない終端（試行の上限）は**断らずに通し**、知らせを出す（利用者の指示 9）。
     *
     * <p>断り続けると、その授業を永久に終えられない。書き起こしが不完全であることは
     * `notice` に残し、最終まとめは**保存できた範囲**で作る。</p>
     */
    @Test
    @DisplayName("終了: 直らない終端の音源があっても通し、知らせを出す（授業を終えられなくしない）")
    void endProceedsWithNoticeWhenFinalizeIsTerminal() {
        stubContinuousChunks();
        stubFinalize(new ClassroomSttStreamService.FinalizeStatus(RECORD_ID,
                ClassroomSttStreamService.FINALIZE_FAILED, "収尾が済んでいません（やり直せません）",
                false, true, false,
                "書き起こしの収尾が済んでいない音源があります。",
                java.util.List.of(
                        source("mic", ClassroomSttStreamService.FINALIZE_FAILED, false, false,
                                "書き起こしの一部を保存できませんでした。", 1, true),
                        source("shared", ClassroomSttStreamService.FINALIZE_SAVED, true, false, null, 4, true))));
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(4);
        when(recordMapper.markFinalized(eq(RECORD_ID), anyInt(), anyInt(), eq(ACCOUNT_ID), anyInt()))
                .thenReturn(1);
        // 分塊は 1 から連続している（分塊の確認で断らないように）
        stubContinuousChunks();
        doAnswer(invocation -> {
            ((ClassroomNoteEntity) invocation.getArgument(0)).setNoteId(77L);
            return 1;
        }).when(noteMapper).insert(any());

        ClassroomModels.EndResult result = service.end(student, RECORD_ID);

        assertThat(result.status()).isEqualTo(ClassroomModels.STATUS_STOPPED);
        assertThat(result.finalNoteId()).isEqualTo(77L);
        // 保存できた範囲（1〜4）で最終まとめを作る
        ArgumentCaptor<ClassroomNoteEntity> captor = ArgumentCaptor.forClass(ClassroomNoteEntity.class);
        verify(noteMapper).insert(captor.capture());
        assertThat(captor.getValue().getEndSeq()).isEqualTo(4);
        assertThat(result.notice()).contains("完了できなかった").contains("マイク");
    }

    /**
     * **取り込み（mp3 1 本）は分塊を持たない**ので、終了の分塊検証で断らない。
     *
     * <p>取り込みは「音声 1 本 + 書き起こし」を作るだけで分塊の行を作らない。分塊を必須にすると、
     * 取り込んだあとの終了（画面は取り込みの直後に終了を呼ぶ）が**永久に通らなくなる**。</p>
     */
    @Test
    @DisplayName("終了: 取り込んだ mp3 は分塊が無くても断らない（取り込みの流れを塞がない）")
    void endAllowsImportedAudioWithoutChunks() {
        stubFinalize(completedStatus(java.util.List.of(
                source("mic", ClassroomSttStreamService.FINALIZE_NOT_STARTED, true, false, null, 0, false),
                source("shared", ClassroomSttStreamService.FINALIZE_NOT_STARTED, true, false, null, 0, false))));
        ClassroomRecordEntity imported = recordingRecord();
        imported.setAudioPath("classroom/2/202609/imported");
        imported.setAudioName("recording.mp3");
        imported.setAudioMime("audio/mpeg");
        when(recordMapper.findById(RECORD_ID)).thenReturn(imported);
        when(settings.load()).thenReturn(snapshot);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(5);
        when(recordMapper.markFinalized(eq(RECORD_ID), anyInt(), anyInt(), eq(ACCOUNT_ID), anyInt()))
                .thenReturn(1);
        doAnswer(invocation -> {
            ((ClassroomNoteEntity) invocation.getArgument(0)).setNoteId(55L);
            return 1;
        }).when(noteMapper).insert(any());

        ClassroomModels.EndResult result = service.end(student, RECORD_ID);

        assertThat(result.status()).isEqualTo(ClassroomModels.STATUS_STOPPED);
        assertThat(result.finalNoteId()).isEqualTo(55L);
    }

    /** 収尾の状態を返す模擬へ差し替える（ストリーミングを使う構成）。 */
    private void stubFinalize(ClassroomSttStreamService.FinalizeStatus status) {        ClassroomSttStreamService streamService = org.mockito.Mockito.mock(ClassroomSttStreamService.class);
        lenient().when(streamService.finalizeStatus(RECORD_ID)).thenReturn(status);
        service = new ClassroomServiceImpl(recordMapper, segmentMapper, chunkMapper,
                new ClassroomRecordingAssembler(storage), noteMapper, presetMapper,
                settings, sttClient, storage, accountMapper, streamService);
    }

    /** 全部済んでいる状態（分塊の検証だけを見たいとき）。 */
    private static ClassroomSttStreamService.FinalizeStatus completedStatus(
            java.util.List<ClassroomSttStreamService.SourceFinalizeStatus> sources) {
        return new ClassroomSttStreamService.FinalizeStatus(RECORD_ID, "COMPLETE",
                "書き起こしの収尾は完了しています", true, true, false, null, sources);
    }

    /** 音源 1 つの収尾の状態（表示名は状態から決める）。 */
    private static ClassroomSttStreamService.SourceFinalizeStatus source(String name, String status,
            boolean completed, boolean retryable, String reason, int savedCount, boolean audioReceived) {
        return new ClassroomSttStreamService.SourceFinalizeStatus(name,
                ClassroomSttStreamService.labelOf(name), status,
                ClassroomSttStreamService.labelOf(status), completed, retryable, reason,
                retryable ? ClassroomSttStreamService.RECOVERY_RESAVE_PENDING : null,
                savedCount, 0, audioReceived ? 20 : 0, audioReceived, false, null, completed, 1,
                "2026-09-19T12:00:00Z");
    }

    // ------------------------------------------------ ブラウザ認識（Web Speech API）

    @Test
    @DisplayName("ブラウザ認識のときは分塊で STT を呼ばず、音声だけ保存する")
    void uploadChunkSkipsSttWhenBrowserRecognition() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.chunkSeconds(snapshot)).thenReturn(20);
        when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);
        when(settings.browserStt(snapshot)).thenReturn(true);
        when(storage.newRecording(eq(ACCOUNT_ID), anyLong(), any()))
                .thenReturn(new ClassroomRecordingStorage.StoredRecording("classroom/1/202609", "a.webm", "audio/webm"));

        ClassroomModels.ChunkUploadResult result = service.uploadChunk(student, RECORD_ID, 1, chunkFile());

        verify(sttClient, never()).transcribe(any());
        verify(segmentMapper, never()).insert(any());
        assertThat(result.appendedSegments()).isEmpty();
        assertThat(result.triggered()).isFalse();
        assertThat(result.nextSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("ブラウザ認識の結果はサーバーが連番を振ってセグメントに足す")
    void appendTranscriptStoresText() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.sttLanguageCode(snapshot, "ja")).thenReturn("ja-JP");
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(2);
        // 直前のセグメント（終わり 40 秒）
        ClassroomSegmentEntity previous = new ClassroomSegmentEntity();
        previous.setRecordId(RECORD_ID);
        previous.setSeq(2);
        previous.setEndOffsetSeconds(java.math.BigDecimal.valueOf(40));
        when(segmentMapper.findByRecordAfter(RECORD_ID, 1)).thenReturn(java.util.List.of(previous));
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(501L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomModels.ChunkUploadResult result = service.appendTranscript(student, RECORD_ID,
                new ClassroomModels.TranscriptRequest("三角形の面積を求めます。",
                        java.math.BigDecimal.valueOf(52.5)));

        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper).insert(captor.capture());
        ClassroomSegmentEntity segment = captor.getValue();
        assertThat(segment.getSeq()).isEqualTo(3);
        assertThat(segment.getText()).isEqualTo("三角形の面積を求めます。");
        assertThat(segment.getSpeaker()).isEqualTo("講義");
        assertThat(segment.getLanguage()).isEqualTo("ja-JP");
        assertThat(segment.getStartOffsetSeconds()).isEqualByComparingTo("40");
        assertThat(segment.getEndOffsetSeconds()).isEqualByComparingTo("52.5");
        assertThat(result.appendedSegments()).hasSize(1);
        verify(recordMapper).addTranscribedChars(RECORD_ID, "三角形の面積を求めます。".length());
        // サーバーの STT は呼ばない
        verify(sttClient, never()).transcribe(any());
    }

    @Test
    @DisplayName("経過秒が前のセグメントより前でも、始まりより小さくはしない")
    void appendTranscriptKeepsOffsetsMonotonic() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.sttLanguageCode(snapshot, "ja")).thenReturn("ja-JP");
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(1);
        ClassroomSegmentEntity previous = new ClassroomSegmentEntity();
        previous.setRecordId(RECORD_ID);
        previous.setSeq(1);
        previous.setEndOffsetSeconds(java.math.BigDecimal.valueOf(30));
        when(segmentMapper.findByRecordAfter(RECORD_ID, 0)).thenReturn(java.util.List.of(previous));
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(502L);
            return 1;
        }).when(segmentMapper).insert(any());

        service.appendTranscript(student, RECORD_ID,
                new ClassroomModels.TranscriptRequest("はい", java.math.BigDecimal.ONE));

        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getStartOffsetSeconds()).isEqualByComparingTo("30");
        assertThat(captor.getValue().getEndOffsetSeconds()).isEqualByComparingTo("30");
    }

    @Test
    @DisplayName("録音中でない記録には書き起こしを足せない（409）")
    void appendTranscriptRejectsStoppedRecord() {
        ClassroomRecordEntity stopped = recordingRecord();
        stopped.setStatus(ClassroomModels.STATUS_STOPPED);
        when(recordMapper.findById(RECORD_ID)).thenReturn(stopped);
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                com.study21.common.core.exception.ConflictException.class,
                () -> service.appendTranscript(student, RECORD_ID,
                        new ClassroomModels.TranscriptRequest("テスト", null)))).isNotNull();
        verify(segmentMapper, never()).insert(any());
    }

    // ------------------------------------------------ AI 解析を一時的にオフにする

    @Test
    @DisplayName("AI 解析が無効なら、フェーズノートのトリガーが成立してもノートを作らない")
    void triggerCreatesNoNoteWhenAiNotesDisabled() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        // AI 解析は無効（＝ノート行を作らない）。無効なら前回フェーズの照会もしない
        when(settings.noteEnabled(snapshot)).thenReturn(false);

        // 書き起こしを 1 件足す（トリガー評価まで進む経路）
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(601L);
            return 1;
        }).when(segmentMapper).insert(any());
        ClassroomModels.ChunkUploadResult result = service.appendTranscript(student, RECORD_ID,
                new ClassroomModels.TranscriptRequest("テスト", java.math.BigDecimal.TEN));

        verify(noteMapper, never()).insert(any());
        assertThat(result.pendingNoteId()).isNull();
        assertThat(result.triggered()).isFalse();
        assertThat(result.runPath()).isNull();
    }

    @Test
    @DisplayName("AI 解析が無効なら、終了しても最終まとめの行を作らず理由を返す")
    void endCreatesNoFinalNoteWhenAiNotesDisabled() {
        stubContinuousChunks();
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.noteEnabled(snapshot)).thenReturn(false);
        when(settings.retentionDays(snapshot)).thenReturn(30);
        when(recordMapper.markFinalized(eq(RECORD_ID), anyInt(), anyInt(), eq(ACCOUNT_ID), anyInt()))
                .thenReturn(1);

        ClassroomModels.EndResult result = service.end(student, RECORD_ID);

        verify(noteMapper, never()).insert(any());
        assertThat(result.finalNoteId()).isNull();
        assertThat(result.runPath()).isNull();
        assertThat(result.notice()).contains("AI 解析").contains("オフ");
    }

    // ------------------------------------------------ 書き起こし用の音声（分塊と別に送る PCM）

    @Test
    @DisplayName("書き起こし用の音声（audio/L16）があるときは、そちらを STT へ送る")
    void uploadChunkPrefersDedicatedSttAudio() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.chunkSeconds(snapshot)).thenReturn(20);
        when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);
        when(settings.sttTimeoutSeconds(snapshot)).thenReturn(60);
        when(settings.browserStt(snapshot)).thenReturn(false);
        when(settings.resolveStt(snapshot)).thenReturn(
                new ClassroomAiSettings.SttConnection("google", "latest_long", "https://example.test/stt", "key"));
        when(settings.sttLanguageCode(snapshot, "ja")).thenReturn("ja-JP");
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(storage.newRecording(eq(ACCOUNT_ID), anyLong(), any()))
                .thenReturn(new ClassroomRecordingStorage.StoredRecording("classroom/1/202609", "a.webm", "audio/webm"));
        when(sttClient.transcribe(any())).thenReturn(ClassroomSttClient.SttResponse.success(200,
                java.util.List.of(new ClassroomSttClient.Segment("比例のグラフを学びます。", null, "ja-JP", null, null))));
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(601L);
            return 1;
        }).when(segmentMapper).insert(any());

        // 再生用（webm）と書き起こし用（PCM）を一緒に送る
        service.uploadChunk(student, RECORD_ID, 1, chunkFile(),
                new org.springframework.mock.web.MockMultipartFile(
                        "stt", "chunk-1.pcm", "audio/L16", new byte[]{9, 8, 7, 6}));

        ArgumentCaptor<ClassroomSttClient.SttRequest> captor =
                ArgumentCaptor.forClass(ClassroomSttClient.SttRequest.class);
        verify(sttClient).transcribe(captor.capture());
        // STT は書き起こし用の音声（4 バイト・audio/L16）を使う
        assertThat(captor.getValue().audio()).containsExactly((byte) 9, (byte) 8, (byte) 7, (byte) 6);
        assertThat(captor.getValue().mime()).isEqualTo("audio/L16");
        // 保存する音声は今までどおり再生用（webm）。STT の形式で上書きしない
        verify(storage).writeChunkLocation(locationOf("chunk-10-000001-test.webm"), new byte[]{1, 2, 3});

        ArgumentCaptor<ClassroomSegmentEntity> segment =
                ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper).insert(segment.capture());
        assertThat(segment.getValue().getText()).isEqualTo("比例のグラフを学びます。");
        assertThat(segment.getValue().getStartOffsetSeconds()).isEqualByComparingTo("0");
        assertThat(segment.getValue().getEndOffsetSeconds()).isEqualByComparingTo("20");
    }

    @Test
    @DisplayName("書き起こし用の音声が無ければ、今までどおり保存した分塊で STT を呼ぶ（互換）")
    void uploadChunkFallsBackToStoredChunkForStt() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.chunkSeconds(snapshot)).thenReturn(20);
        when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);
        when(settings.sttTimeoutSeconds(snapshot)).thenReturn(60);
        when(settings.browserStt(snapshot)).thenReturn(false);
        when(settings.resolveStt(snapshot)).thenReturn(
                new ClassroomAiSettings.SttConnection("google", "latest_long", "https://example.test/stt", "key"));
        when(settings.sttLanguageCode(snapshot, "ja")).thenReturn("ja-JP");
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(storage.newRecording(eq(ACCOUNT_ID), anyLong(), any()))
                .thenReturn(new ClassroomRecordingStorage.StoredRecording("classroom/1/202609", "a.webm", "audio/webm"));
        when(sttClient.transcribe(any())).thenReturn(ClassroomSttClient.SttResponse.success(200,
                java.util.List.of(new ClassroomSttClient.Segment("テスト", null, "ja-JP", null, null))));
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(602L);
            return 1;
        }).when(segmentMapper).insert(any());

        service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null);

        ArgumentCaptor<ClassroomSttClient.SttRequest> captor =
                ArgumentCaptor.forClass(ClassroomSttClient.SttRequest.class);
        verify(sttClient).transcribe(captor.capture());
        assertThat(captor.getValue().audio()).containsExactly((byte) 1, (byte) 2, (byte) 3);
        assertThat(captor.getValue().mime()).isEqualTo("audio/webm");
    }

    /**
     * 分塊の書き起こしに失敗しても、**音声は捨てない**（409 で弾かない）。
     *
     * <p>実測: 二音源（マイク＋スピーカー）の録音で、画面が書き起こし用 PCM を送れない状態になり、
     * サーバーが保存した webm で STT を呼んだところ Paraformer-Realtime-V2 が
     * 「audio/webm;codecs=opus は認識できません」で失敗。**分塊ごと 409 で捨てられた**ため、
     * 録音の音声がほぼ残らなかった（`seq=8〜12` が全部 409）。音声は代替が効かないので、
     * 書き起こしが失敗しても保存して受け入れる（書き起こしだけが欠ける）。</p>
     */
    @Test
    @DisplayName("分塊の書き起こしが失敗しても、音声は保存して受け入れる（409 にしない）")
    void uploadChunkKeepsAudioWhenSttFails() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.chunkSeconds(snapshot)).thenReturn(20);
        when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);
        when(settings.sttTimeoutSeconds(snapshot)).thenReturn(60);
        when(settings.browserStt(snapshot)).thenReturn(false);
        when(settings.resolveStt(snapshot)).thenReturn(
                new ClassroomAiSettings.SttConnection("alibaba", "paraformer-realtime-v2",
                        "wss://example.test/asr", "key"));
        when(settings.sttLanguageCode(snapshot, "ja")).thenReturn("ja-JP");
        when(storage.newRecording(eq(ACCOUNT_ID), anyLong(), any()))
                .thenReturn(new ClassroomRecordingStorage.StoredRecording("classroom/1/202609", "a.webm", "audio/webm"));
        when(sttClient.transcribe(any())).thenReturn(ClassroomSttClient.SttResponse.failure(0, "HTTP_4XX",
                "この音声形式（audio/webm;codecs=opus）は Paraformer-Realtime-V2 では認識できません。"));

        ClassroomModels.ChunkUploadResult result = service.uploadChunk(student, RECORD_ID, 8, chunkFile(), null);

        // 分塊は受け入れる（連番は進む）＝音声は保存されている
        assertThat(result.seq()).isEqualTo(8);
        assertThat(result.appendedSegments()).isEmpty();
        verify(storage).writeChunkLocation(eq(locationOf("chunk-10-000008-test.webm")), any());
        verify(segmentMapper, never()).insert(any());
    }

    // ------------------------------------------------ 終了直後に届いた分塊（音声を捨てない）

    /**
     * 【終了】の直前に送られた最後の分塊が、終了のあとに届くことがある（`MediaRecorder` の最後の分塊は
     * `stop()` の後に届く）。ここで 409 を返すと**その回の音声が丸ごと残らない**（実測）ので、
     * 停止直後（10 分以内）は**音声だけ保存**して受け入れる。
     */
    @Test
    @DisplayName("終了直後に届いた分塊は、音声だけ保存して受け入れる（409 にしない）")
    void uploadChunkAcceptsLateChunkRightAfterEnd() {
        ClassroomRecordEntity record = recordingRecord();
        record.setStatus(ClassroomModels.STATUS_STOPPED);
        record.setEndTime(new java.sql.Timestamp(System.currentTimeMillis() - 5_000));
        when(recordMapper.findById(RECORD_ID)).thenReturn(record);
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.chunkSeconds(snapshot)).thenReturn(20);
        when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);
        when(storage.newRecording(eq(ACCOUNT_ID), anyLong(), any()))
                .thenReturn(new ClassroomRecordingStorage.StoredRecording("classroom/1/202609", "a.webm", "audio/webm"));

        ClassroomModels.ChunkUploadResult result = service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null);

        // 音声は保存する（残さないと授業の記録が丸ごと消える）
        verify(storage).writeChunkLocation(locationOf("chunk-10-000001-test.webm"), new byte[]{1, 2, 3});
        // 書き起こしはしない（録音は終わっていて、最終まとめも作られている）
        verify(sttClient, never()).transcribe(any());
        verify(segmentMapper, never()).insert(any());
        assertThat(result.appendedSegments()).isEmpty();
        assertThat(result.nextSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("終了から時間が経った分塊は今までどおり拒否する（古い記録に追記しない）")
    void uploadChunkRejectsLateChunkAfterGrace() {
        ClassroomRecordEntity record = recordingRecord();
        record.setStatus(ClassroomModels.STATUS_STOPPED);
        record.setEndTime(new java.sql.Timestamp(System.currentTimeMillis() - 30L * 60 * 1000));
        when(recordMapper.findById(RECORD_ID)).thenReturn(record);
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);

        assertThatThrownBy(() -> service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null))
                .isInstanceOf(com.study21.common.core.exception.ConflictException.class)
                .hasMessageContaining("録音中ではありません");
        verify(storage, org.mockito.Mockito.never()).writeChunkLocation(any(), any());
    }

    // ------------------------------------------------ 書き起こしの時刻（実際の経過秒）

    private void stubSttChunk() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.chunkSeconds(snapshot)).thenReturn(20);
        when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);
        when(settings.sttTimeoutSeconds(snapshot)).thenReturn(60);
        when(settings.browserStt(snapshot)).thenReturn(false);
        when(settings.resolveStt(snapshot)).thenReturn(
                new ClassroomAiSettings.SttConnection("alibaba", "paraformer-realtime-v2", "wss://example.test", "key"));
        // 空の結果のときは使われないので lenient（テストの意図は「埋め草を入れない」こと）
        lenient().when(settings.sttLanguageCode(snapshot, "ja")).thenReturn("ja-JP");
        when(storage.newRecording(eq(ACCOUNT_ID), anyLong(), any()))
                .thenReturn(new ClassroomRecordingStorage.StoredRecording("classroom/1/202609", "a.webm", "audio/webm"));
        lenient().doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(700L);
            return 1;
        }).when(segmentMapper).insert(any());
    }

    /**
     * 時間は**画面が送った実際の経過秒**を使う。
     *
     * <p>従来は「(連番-1) × 現在の分塊の長さ」で計算していたため、録音中に設定を変えると
     * 2 本の時間軸が混ざって時系列が壊れた（実測: 00:00 / 00:20 / 00:40 / 00:15 / 00:20 …）。</p>
     */
    @Test
    @DisplayName("時間は画面が送った実際の経過秒を使う（設定を変えても時系列が壊れない）")
    void uploadChunkUsesReportedOffsets() {
        stubSttChunk();
        when(sttClient.transcribe(any())).thenReturn(ClassroomSttClient.SttResponse.success(200,
                java.util.List.of(new ClassroomSttClient.Segment("続きです。", null, "ja-JP", null, null))));

        service.uploadChunk(student, RECORD_ID, 3, chunkFile(), null,
                java.math.BigDecimal.valueOf(62.5), java.math.BigDecimal.valueOf(81.25));

        ArgumentCaptor<ClassroomSegmentEntity> segment =
                ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper).insert(segment.capture());
        // 連番×20 ではなく、送られた実際の値（1 秒未満も落とさない）
        assertThat(segment.getValue().getStartOffsetSeconds()).isEqualByComparingTo("62.5");
        assertThat(segment.getValue().getEndOffsetSeconds()).isEqualByComparingTo("81.25");
    }

    @Test
    @DisplayName("時間が送られてこなければ今までどおり連番×分塊の長さで計算する（互換）")
    void uploadChunkFallsBackToSeqBasedOffsets() {
        stubSttChunk();
        when(sttClient.transcribe(any())).thenReturn(ClassroomSttClient.SttResponse.success(200,
                java.util.List.of(new ClassroomSttClient.Segment("続きです。", null, "ja-JP", null, null))));

        service.uploadChunk(student, RECORD_ID, 4, chunkFile(), null, null, null);

        ArgumentCaptor<ClassroomSegmentEntity> segment =
                ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper).insert(segment.capture());
        assertThat(segment.getValue().getStartOffsetSeconds()).isEqualByComparingTo("60");
        assertThat(segment.getValue().getEndOffsetSeconds()).isEqualByComparingTo("80");
    }

    /**
     * 認識結果が空のときは**セグメントを作らない**。
     *
     * <p>従来は「（聞き取れませんでした）」という**埋め草**を DB に入れており、文字数と AI まとめを汚していた
     * （実測: 実害のある行が並んだ）。</p>
     */
    @Test
    @DisplayName("認識結果が空のときは、埋め草を入れずにセグメントを作らない")
    void uploadChunkSkipsEmptyTranscript() {
        stubSttChunk();
        when(sttClient.transcribe(any()))
                .thenReturn(ClassroomSttClient.SttResponse.success(200, java.util.List.of()));

        ClassroomModels.ChunkUploadResult result =
                service.uploadChunk(student, RECORD_ID, 2, chunkFile(), null, null, null);

        verify(segmentMapper, never()).insert(any());
        verify(recordMapper, never()).addTranscribedChars(anyLong(), anyInt());
        assertThat(result.appendedSegments()).isEmpty();
        // 連番は進める（次の分塊が同じ番号にならないように）
        assertThat(result.nextSeq()).isEqualTo(result.seq() + 1);
    }

    // ------------------------------------------------ 録音せずに取り込む（mp3／貼り付け）

    /**
     * 音声ファイルは **mp3 だけ**、**90 分まで**、貼り付けは **50 万字まで**（利用者の指示）。
     * 文字起こしは行・文ごとに分けて保存する（1 行が長くなりすぎないように）。
     */
    @Test
    @DisplayName("取り込み: mp3 以外は断り、90 分を超える音声も断る")
    void importSourceValidatesFile() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);

        // wav は受け付けない
        assertThatThrownBy(() -> service.importSource(student, RECORD_ID,
                new org.springframework.mock.web.MockMultipartFile("file", "a.wav", "audio/wav", new byte[]{1}),
                null, 10))
                .hasMessageContaining("mp3");

        // 90 分を超える音声は断る
        assertThatThrownBy(() -> service.importSource(student, RECORD_ID,
                new org.springframework.mock.web.MockMultipartFile("file", "a.mp3", "audio/mpeg", new byte[]{1}),
                null, 91 * 60))
                .hasMessageContaining("90 分");

        // どちらも送らなければ断る
        assertThatThrownBy(() -> service.importSource(student, RECORD_ID, null, "  ", null))
                .hasMessageContaining("音声ファイル");
    }

    /**
     * 名前と種類だけが mp3 のファイルを**保存しない**。
     *
     * <p>文字のテキストを `.mp3` に変えたファイルを受け取ると、書き起こしも再生もできない音声が
     * 残り、利用者には原因が分からない（実測: 画面の事前チェックも通ってしまった）。</p>
     */
    @Test
    @DisplayName("取り込み: 中身が mp3 でないファイル（拡張子だけ mp3）は断る")
    void importSourceRejectsFileWithoutMp3Content() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);

        byte[] text = "これは音声ではありません".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        assertThatThrownBy(() -> service.importSource(student, RECORD_ID,
                new org.springframework.mock.web.MockMultipartFile("file", "a.mp3", "audio/mpeg", text),
                null, 3))
                .hasMessageContaining("mp3 ではありません");
    }

    @Test
    @DisplayName("mp3 らしさの判定: ID3 タグかフレーム同期で始まるものだけを受け付ける")
    void looksLikeMp3ChecksHeader() {
        assertThat(ClassroomServiceImpl.looksLikeMp3(new byte[]{'I', 'D', '3', 3, 0})).isTrue();
        assertThat(ClassroomServiceImpl.looksLikeMp3(new byte[]{(byte) 0xFF, (byte) 0xFB, (byte) 0x90, 0})).isTrue();
        assertThat(ClassroomServiceImpl.looksLikeMp3("これは音声ではない".getBytes(
                java.nio.charset.StandardCharsets.UTF_8))).isFalse();
        assertThat(ClassroomServiceImpl.looksLikeMp3(new byte[]{1})).isFalse();
        assertThat(ClassroomServiceImpl.looksLikeMp3(null)).isFalse();
    }

    @Test
    @DisplayName("取り込み: 貼り付けた文字起こしを、行ごとに分けてセグメントにする")
    void importSourceStoresPastedText() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.chunkSeconds(snapshot)).thenReturn(20);
        when(settings.sttLanguageCode(snapshot, "ja")).thenReturn("ja-JP");
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(900L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomModels.ChunkUploadResult result = service.importSource(student, RECORD_ID, null,
                "先生：今日は比例を学びます。\n次に練習問題です。", null);

        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).getText()).isEqualTo("先生：今日は比例を学びます。");
        assertThat(captor.getAllValues().get(1).getText()).isEqualTo("次に練習問題です。");
        assertThat(captor.getAllValues().get(0).getSeq()).isEqualTo(1);
        assertThat(captor.getAllValues().get(1).getSeq()).isEqualTo(2);
        assertThat(result.appendedSegments()).hasSize(2);
    }

    /* ---------------- 書き起こしの入口（所有者と録音の状態。HTTP と常時接続で共通） ---------------- */

    @Test
    @DisplayName("書き起こしの音声は「録音中」の自分の記録だけ受け取る")
    void sttAudioRequiresOwnerAndRecordingState() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        assertThat(service.requireSttAudioAccountId(student, RECORD_ID)).isEqualTo(ACCOUNT_ID);

        // 停止した記録へは音を受け取らない（確定した書き起こしを後から書き換えない）
        ClassroomRecordEntity stopped = recordingRecord();
        stopped.setStatus(ClassroomModels.STATUS_STOPPED);
        when(recordMapper.findById(RECORD_ID)).thenReturn(stopped);
        assertThatThrownBy(() -> service.requireSttAudioAccountId(student, RECORD_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("停止");
    }

    @Test
    @DisplayName("他人の記録・無い記録へは音を送れない（HTTP と同じ 404 の道を通る）")
    void sttAudioRefusesOthersRecord() {
        ClassroomRecordEntity others = recordingRecord();
        others.setCreatedBy(99L);
        when(recordMapper.findById(RECORD_ID)).thenReturn(others);
        assertThatThrownBy(() -> service.requireSttAudioAccountId(student, RECORD_ID))
                .isInstanceOf(NotFoundException.class);

        when(recordMapper.findById(RECORD_ID)).thenReturn(null);
        assertThatThrownBy(() -> service.requireSttAudioAccountId(student, RECORD_ID))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    @DisplayName("収尾は停止直後だけ受け取り、完了・古い停止では受け取らない")
    void sttFinishAcceptsRecentlyStoppedOnly() {
        // 尾部の確定文は停止のあとに届くので、直後の猶予は収尾を受け付ける（分塊の遅延受け入れと同じ）
        ClassroomRecordEntity stopped = recordingRecord();
        stopped.setStatus(ClassroomModels.STATUS_STOPPED);
        stopped.setEndTime(java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(30)));
        when(recordMapper.findById(RECORD_ID)).thenReturn(stopped);
        assertThat(service.requireSttFinishAccountId(student, RECORD_ID)).isEqualTo(ACCOUNT_ID);

        // 完了した記録は書き換えない
        ClassroomRecordEntity completed = recordingRecord();
        completed.setStatus(ClassroomModels.STATUS_COMPLETED);
        completed.setEndTime(java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(30)));
        when(recordMapper.findById(RECORD_ID)).thenReturn(completed);
        assertThatThrownBy(() -> service.requireSttFinishAccountId(student, RECORD_ID))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("完了");

        // 停止から時間が経った記録（猶予の外）も受け取らない
        ClassroomRecordEntity old = recordingRecord();
        old.setStatus(ClassroomModels.STATUS_STOPPED);
        old.setEndTime(java.sql.Timestamp.from(java.time.Instant.now().minusSeconds(3_600)));
        when(recordMapper.findById(RECORD_ID)).thenReturn(old);
        assertThatThrownBy(() -> service.requireSttFinishAccountId(student, RECORD_ID))
                .isInstanceOf(ConflictException.class);
    }

    /* ---------------- 分塊の保存（転写セグメントとは別の連番で持つ） ---------------- */

    /**
     * 転写セグメントの連番は**文の数**、分塊の連番は**音の数**。同じものとして扱うと、
     * 転写が分塊より多く出た回に「3 個目の分塊が重複」と誤判定されて音声が捨てられる
     * （実測: 転写 8 文・分塊 3 個で 3 個目が保存されなかった）。
     */
    @Test
    @DisplayName("転写の文が分塊より多くても、分塊は全部保存する（文の数で捨てない）")
    void uploadChunkStoresAllChunksWhenTranscriptsAreMore() {
        stubChunkUpload();

        ClassroomModels.ChunkUploadResult result =
                service.uploadChunk(student, RECORD_ID, 3, chunkFile(), null, null, null);

        ArgumentCaptor<ClassroomRecordingChunkEntity> captor =
                ArgumentCaptor.forClass(ClassroomRecordingChunkEntity.class);
        verify(chunkMapper).insertIfAbsent(captor.capture());
        assertThat(captor.getValue().getSeq()).isEqualTo(3);
        verify(storage).writeChunkLocation(eq(locationOf("chunk-10-000003-test.webm")), any());
        assertThat(result.seq()).isEqualTo(3);
        // 分塊の経路は**転写セグメントの連番を一度も見ない**（文の数で音を捨てない）
        verify(segmentMapper, never()).maxSeq(anyLong());
    }

    @Test
    @DisplayName("転写が 1 件も無くても、同じ分塊を 2 回送れば 1 つだけ保存する（冪等）")
    void uploadChunkIsIdempotentWithoutTranscripts() {
        stubChunkUpload();

        service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null, null, null);
        service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null, null, null);

        // 行もファイルも 1 つだけ（2 回目は保存済みの結果を返す）
        verify(chunkMapper, org.mockito.Mockito.times(1)).insertIfAbsent(any());
        verify(storage, org.mockito.Mockito.times(1)).writeChunkLocation(any(), any());
    }

    @Test
    @DisplayName("順不同で届いた分塊も保存する（大きい連番があっても小さい連番を捨てない）")
    void uploadChunkStoresOutOfOrderSeq() {
        stubChunkUpload();

        service.uploadChunk(student, RECORD_ID, 3, chunkFile(), null, null, null);
        service.uploadChunk(student, RECORD_ID, 2, chunkFile(), null, null, null);

        ArgumentCaptor<ClassroomRecordingChunkEntity> captor =
                ArgumentCaptor.forClass(ClassroomRecordingChunkEntity.class);
        verify(chunkMapper, org.mockito.Mockito.times(2)).insertIfAbsent(captor.capture());
        assertThat(captor.getAllValues()).extracting(ClassroomRecordingChunkEntity::getSeq)
                .containsExactly(3, 2);
        verify(storage).writeChunkLocation(eq(locationOf("chunk-10-000002-test.webm")), any());
    }

    /**
     * 応答を失った再送（画面は同じ Blob をそのまま送り直す）。
     * 中身が同じなら**保存済みの結果**を返し、音も行も増やさない。
     */
    @Test
    @DisplayName("応答を失った再送は、中身が同じなら保存済みの結果を返す（音も行も増えない）")
    void uploadChunkReturnsStoredResultOnRetry() {
        stubChunkUpload();

        ClassroomModels.ChunkUploadResult first =
                service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null, null, null);
        ClassroomModels.ChunkUploadResult retry =
                service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null, null, null);

        assertThat(retry.seq()).isEqualTo(first.seq());
        assertThat(retry.nextChunkSeq()).isEqualTo(2);
        verify(chunkMapper, org.mockito.Mockito.times(1)).insertIfAbsent(any());
        verify(storage, org.mockito.Mockito.times(1)).writeChunkLocation(any(), any());
    }

    @Test
    @DisplayName("同じ連番に違う中身が届いたらエラーにする（保存済みは書き換えない）")
    void uploadChunkRejectsDifferentContentOnSameSeq() {
        stubChunkUpload();
        service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null, null, null);

        // 同じ連番で違う中身（別の分塊が混ざった・作り直された）
        var different = new org.springframework.mock.web.MockMultipartFile(
                "file", "chunk-1.webm", "audio/webm", new byte[]{9, 9, 9, 9});

        assertThatThrownBy(() -> service.uploadChunk(student, RECORD_ID, 1, different, null, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("違う内容");

        /*
         * **実体を書かない**: 同じ連番の別の中身は、書き込み先が分塊ごとに固有なので
         * そもそも上書きできない。勝った側は DB の引用（行）がそのまま指し続ける。
         */
        verify(chunkMapper, org.mockito.Mockito.times(1)).insertIfAbsent(any());
        assertThat(chunkRows.get(1).getByteSize()).isEqualTo(3L);
    }

    /**
     * 同時再送（同じ分塊がほぼ同時に 2 本来る）。先に入れた側の中身と一致すれば、
     * 負けた側も**保存済みの結果**として 200 で返す（二重に作らない・どちらも失敗しない）。
     */
    @Test
    @DisplayName("同時再送: 他が先に入れていても、中身が同じなら冪等に成功する")
    void uploadChunkHandlesConcurrentRetry() {
        stubChunkUpload();
        // 別の要求が先に同じ連番を入れた（INSERT は 0 行・照会では同じ中身が返る）
        ClassroomRecordingChunkEntity winner = storedChunkRow(1, new byte[]{1, 2, 3});
        // 別の要求が先に入れた（こちらが読んだときはまだ無く、挿入は 0 行）
        chunkRows.put(winner.getSeq(), winner);
        when(chunkMapper.insertIfAbsent(any())).thenReturn(0);
        when(chunkMapper.findBySeq(RECORD_ID, 1)).thenReturn(null, winner);

        ClassroomModels.ChunkUploadResult result =
                service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null, null, null);

        assertThat(result.seq()).isEqualTo(1);
        assertThat(result.nextChunkSeq()).isEqualTo(2);
    }

    @Test
    @DisplayName("同時再送: 他が先に入れた中身が違えばエラーにする")
    void uploadChunkRejectsConcurrentDifferentContent() {
        stubChunkUpload();
        ClassroomRecordingChunkEntity winner = storedChunkRow(1, new byte[]{7, 7, 7});
        chunkRows.put(winner.getSeq(), winner);
        when(chunkMapper.insertIfAbsent(any())).thenReturn(0);
        when(chunkMapper.findBySeq(RECORD_ID, 1)).thenReturn(null, winner);

        assertThatThrownBy(() -> service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null, null, null))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("違う内容");
    }

    /**
     * 後端（user-api）を再起動しても、分塊は DB の行とファイルが正体なので失われない。
     * 再起動後に同じ分塊が届いても（画面の送り直し）、保存済みとして扱う。
     */
    @Test
    @DisplayName("再起動後の再送でも、保存済みの分塊は二重にならない")
    void uploadChunkSurvivesRestart() {
        stubChunkUpload();
        // 再起動後に届いた分塊: DB には既に行がある（メモリには何も無い）
        when(chunkMapper.findBySeq(RECORD_ID, 2)).thenReturn(storedChunkRow(2, new byte[]{1, 2, 3}));

        ClassroomModels.ChunkUploadResult result =
                service.uploadChunk(student, RECORD_ID, 2, chunkFile(), null, null, null);

        assertThat(result.seq()).isEqualTo(2);
        verify(chunkMapper, never()).insertIfAbsent(any());
        verify(storage, org.mockito.Mockito.never()).writeChunkLocation(any(), any());
    }

    @Test
    @DisplayName("保存済みの分塊を一覧できる（次に送る連番と録音の位置）")
    void chunksReturnsNextSeqAndRecordedSeconds() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        ClassroomRecordingChunkEntity stored = storedChunkRow(7, new byte[]{1, 2, 3});
        stored.setEndOffsetSeconds(java.math.BigDecimal.valueOf(140.5));
        when(chunkMapper.findByRecordAfter(RECORD_ID, 0)).thenReturn(java.util.List.of(stored));
        when(chunkMapper.maxSeq(RECORD_ID)).thenReturn(7);
        when(chunkMapper.countByRecord(RECORD_ID)).thenReturn(1);
        when(chunkMapper.totalBytes(RECORD_ID)).thenReturn(3L);
        when(chunkMapper.maxEndOffsetSeconds(RECORD_ID)).thenReturn(java.math.BigDecimal.valueOf(140.5));

        ClassroomModels.ChunkListResult result = service.chunks(student, RECORD_ID, 0);

        assertThat(result.items()).hasSize(1);
        assertThat(result.maxSeq()).isEqualTo(7);
        // 画面はこの 8 をそのまま次の分塊の連番にする（転写の連番から作らない）
        assertThat(result.nextSeq()).isEqualTo(8);
        assertThat(result.recordedSeconds()).isEqualTo(140.5);
    }

    /* ---------------- 録音の最大時間は実際の録音の位置で見る ---------------- */

    @Test
    @DisplayName("録音の上限は、転写の連番ではなく実際の経過秒で判定する")
    void uploadChunkAppliesLimitByRecordedPosition() {
        stubChunkUpload();
        // 保存済みの分塊はまだ 20 秒の位置（上限 120 分には遠い）
        when(chunkMapper.maxEndOffsetSeconds(RECORD_ID)).thenReturn(java.math.BigDecimal.valueOf(20));

        ClassroomModels.ChunkUploadResult result = service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null,
                java.math.BigDecimal.ZERO, java.math.BigDecimal.valueOf(20));

        assertThat(result.seq()).isEqualTo(1);
        verify(chunkMapper).insertIfAbsent(any());
        // 判定は実際の経過秒で行う（転写セグメントの連番は見ない）
        verify(segmentMapper, never()).maxSeq(anyLong());
    }

    @Test
    @DisplayName("上限を超える位置の分塊は受け取らない（実際の経過秒で見る）")
    void uploadChunkRejectsBeyondMaxMinutes() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.enabled(snapshot)).thenReturn(true);
        when(settings.chunkSeconds(snapshot)).thenReturn(20);
        when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);

        // 120 分 = 7200 秒。7201 秒の分塊は断る
        assertThatThrownBy(() -> service.uploadChunk(student, RECORD_ID, 2, chunkFile(), null,
                java.math.BigDecimal.valueOf(7200), java.math.BigDecimal.valueOf(7201)))
                .hasMessageContaining("120 分");

        verify(chunkMapper, never()).insertIfAbsent(any());
        verify(storage, org.mockito.Mockito.never()).writeChunkLocation(any(), any());
    }

    /* ---------------- ③ 終了前の確認（分塊の連続性・収尾） ---------------- */

    /**
     * 終了のテストの前提: **分塊が 1 から連続している**（分塊の確認で断らないようにする）。
     *
     * <p>分塊の中身はこのテストの主眼ではない（{@code ClassroomChunkConsistencyTest} と
     * {@code ClassroomRecordingSessionPlannerTest} が実体つきで見張る）。</p>
     */
    private void stubContinuousChunks() {
        storedChunks(1, 2);
    }

    /** 分塊の行を「1 から n まで連続している」形にする（**数えるのは行だけ**）。 */
    private void storedChunks(int... seqs) {
        List<ClassroomRecordingChunkEntity> rows = new java.util.ArrayList<>();
        for (int seq : seqs) {
            ClassroomRecordingChunkEntity row = storedChunkRow(seq, new byte[]{(byte) seq});
            row.setContainerHead(seq == 1);
            chunkRows.put(seq, row);
            rows.add(row);
        }
        when(chunkMapper.findByRecord(RECORD_ID)).thenReturn(rows);
        for (ClassroomRecordingChunkEntity row : rows) {
            writeStubFile(row);
        }
    }

    /**
     * 行が指す**実体**をテスト用の置き場に作る。
     *
     * <p>終了の確認は「行がそろっているか」だけでなく**実体の存在と大きさ**まで見るので、
     * テストでも本物のファイルを置く（置かないと「実体が無い」として断られる）。</p>
     */
    private void writeStubFile(ClassroomRecordingChunkEntity row) {
        java.nio.file.Path path = storage.resolve(row.getStorageDir(), row.getFileName());
        if (path == null) {
            return;
        }
        try {
            java.nio.file.Files.createDirectories(path.getParent());
            java.nio.file.Files.write(path, new byte[row.getByteSize() == null
                    ? 1 : (int) (long) row.getByteSize()]);
        } catch (java.io.IOException cause) {
            throw new IllegalStateException(cause);
        }
    }

    /**
     * 終了してよいかの確認は「少なくとも 1 つある」では足りない。
     *
     * <p>画面は停止のあと、**最後の分塊まで送り切ってから**終了を送る。サーバーは分塊が
     * **1 から連続しているか**を確かめ、欠けていれば**欠けている連番**を返して断る
     * （画面はその分塊だけ送り直す）。</p>
     */
    @Test
    @DisplayName("③ 途中の分塊が欠けていたら終了を断り、欠けている連番を返す")
    void endRefusesWhenChunksHaveGap() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        // 1 と 3 はあるが 2 が無い
        storedChunks(1, 3);

        assertThatThrownBy(() -> service.end(student, RECORD_ID))
                .isInstanceOf(ChunkChecklistException.class)
                .hasMessageContaining("2");

        // 記録は終わらせない（受け付けられる状態のまま＝送り直せる）
        verify(recordMapper, never()).claimFinalize(anyLong(), anyLong());
        assertThat(chunkRows).hasSize(2);
    }

    @Test
    @DisplayName("③ 最後の分塊がまだ届いていない（画面が宣言した範囲に足りない）ときも断る")
    void endRefusesWhenLastChunkIsMissing() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        // 1・2 はあるが、画面は 3 まで送ったつもり（3 が届いていない）
        storedChunks(1, 2);

        assertThatThrownBy(() -> service.end(student, RECORD_ID, false,
                new ClassroomModels.ChunkManifest(3, 3, null, null)))
                .isInstanceOf(ChunkChecklistException.class)
                .hasMessageContaining("3");
    }

    @Test
    @DisplayName("③ 分塊が 1 から連続していれば終了できて、「終了できます」を返す")
    void endAllowsWhenChunksAreContinuous() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(recordMapper.markFinalized(eq(RECORD_ID), anyInt(), anyInt(), eq(ACCOUNT_ID), anyInt()))
                .thenReturn(1);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        storedChunks(1, 2);

        ClassroomModels.EndResult result = service.end(student, RECORD_ID);

        assertThat(result.status()).isEqualTo(ClassroomModels.STATUS_STOPPED);
        assertThat(result.complete()).isTrue();
        assertThat(result.missingSeqs()).isEmpty();
        assertThat(result.forced()).isFalse();
    }

    /**
     * 明示の「不完全なまま終了」。利用者が影響を確認して押したときだけ通す
     * （既定は**断る**＝黙って音を失わない）。
     */
    @Test
    @DisplayName("③ 明示の不完全終了だけは通す（欠落の一覧を notice に載せる）")
    void endAllowsExplicitIncompleteEnd() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(recordMapper.markFinalized(eq(RECORD_ID), anyInt(), anyInt(), eq(ACCOUNT_ID), anyInt()))
                .thenReturn(1);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        storedChunks(1, 3);

        ClassroomModels.EndResult result = service.end(student, RECORD_ID, true, null);

        assertThat(result.status()).isEqualTo(ClassroomModels.STATUS_STOPPED);
        assertThat(result.complete()).isFalse();
        assertThat(result.missingSeqs()).containsExactly(2);
        assertThat(result.forced()).isTrue();
        assertThat(result.notice()).contains("2");
    }

    /**
     * **アップロードと終了の同時実行**は「状態の鍵」で防ぐ。
     *
     * <p>確認を 2 回繰り返すのは排他ではない（その間にも書き込みが入る）。収尾は
     * `状態 = RECORDING → TRANSCRIBING` を 1 文で行って**鍵を取る**ので、同時に 2 本来ても
     * 勝つのは 1 つだけ。負けた側は 409 で断り、画面は状態を読み直す。</p>
     */
    @Test
    @DisplayName("③ アップロードと終了が同時でも、勝つのは 1 つだけ（鍵を取れなければ断る）")
    void endIsExclusiveWithUploads() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        storedChunks(1);
        // 別の要求が先に鍵を取った
        when(recordMapper.claimFinalize(eq(RECORD_ID), eq(ACCOUNT_ID))).thenReturn(0);

        assertThatThrownBy(() -> service.end(student, RECORD_ID))
                .isInstanceOf(ChunkChecklistException.class)
                .hasMessageContaining("すでに終了処理に入っています");

        // 記録は終わらせない・最終まとめも作らない
        verify(recordMapper, never()).markFinalized(anyLong(), anyInt(), anyInt(), anyLong(), anyInt());
        verify(noteMapper, never()).insert(any());
    }

    /**
     * 鍵を取った**あとに**分塊が増えていたら（状態が排他なので通常は起きない）、完了させずに断る。
     */
    @Test
    @DisplayName("③ 鍵を取ったあとに分塊が増えていたら、完了させずにもう一度やり直させる")
    void endRefusesWhenChunksChangedAfterClaim() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        storedChunks(1);
        /*
         * 鍵を取る前は 1 件、取ったあとは 2 件（別の要求が足した）。
         * `checkChunks` と「鍵の前後の数」で同じ照会を使うので、順番に 2 つの答えを返す。
         */
        ClassroomRecordingChunkEntity extra = storedChunkRow(2, new byte[]{2});
        when(chunkMapper.findByRecord(RECORD_ID)).thenReturn(
                new java.util.ArrayList<>(java.util.List.of(chunkRows.get(1))),
                new java.util.ArrayList<>(java.util.List.of(chunkRows.get(1))),
                new java.util.ArrayList<>(java.util.List.of(chunkRows.get(1), extra)));

        assertThatThrownBy(() -> service.end(student, RECORD_ID))
                .isInstanceOf(ChunkChecklistException.class)
                .hasMessageContaining("もう一度");

        verify(recordMapper, never()).markFinalized(anyLong(), anyInt(), anyInt(), anyLong(), anyInt());
    }

    /**
     * 分塊の置き場と書き込み先の代役。
     *
     * <p>書き込み先は**分塊ごとに固有**（実装と同じ形の名前）にする。同じ連番でも名前が
     * 変わるので、テストでも「上書きしない」ことを名前で見分けられる。</p>
     */
    private void stubChunkStorage() {
        lenient().when(storage.newChunkLocation(any(), anyLong(), anyInt(), any()))
                .thenAnswer(invocation -> new ClassroomRecordingStorage.ChunkLocation(
                        invocation.getArgument(0),
                        "chunk-" + invocation.getArgument(1) + "-"
                                + String.format("%06d", (Integer) invocation.getArgument(2)) + "-test.webm"));
        java.nio.file.Path stubRoot = stubFiles;
        lenient().when(storage.resolve(any(), any()))
                .thenAnswer(invocation -> stubRoot
                        .resolve(String.valueOf((Object) invocation.getArgument(0)))
                        .resolve(String.valueOf((Object) invocation.getArgument(1))));
        lenient().when(storage.prepareRecordingDirectory(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(true);
        /*
         * 収尾は「鍵を取る（claimFinalize）→ 完了（markFinalized）」の 2 段。
         * 既定は成功にしておく（失敗の経路はそれぞれのテストで上書きする）。
         */
        lenient().when(recordMapper.claimFinalize(anyLong(), anyLong())).thenReturn(1);
        lenient().when(recordMapper.markFinalized(anyLong(), org.mockito.ArgumentMatchers.anyInt(),
                org.mockito.ArgumentMatchers.anyInt(), anyLong(),
                org.mockito.ArgumentMatchers.anyInt())).thenReturn(1);
        lenient().when(chunkMapper.countByRecord(anyLong())).thenReturn(0);
    }

    /** 分塊の書き込み先（テストの下見用。名前は {@code stubChunkUpload} の決め方と同じ形）。 */
    private static ClassroomRecordingStorage.ChunkLocation locationOf(String fileName) {
        return new ClassroomRecordingStorage.ChunkLocation("classroom/1/202609", fileName);
    }

    /* ---------------- 分塊の表の代役（一意制約と ON CONFLICT の振る舞いを写す） ---------------- */

    /** そのテストだけの分塊表（同じ連番は 1 行・INSERT は 0 行を返す）。 */
    private final java.util.Map<Integer, ClassroomRecordingChunkEntity> chunkRows =
            new java.util.LinkedHashMap<>();

    /**
     * 分塊表の代役（**本物の一意制約と ON CONFLICT DO NOTHING の振る舞いを写す**）。
     *
     * <p>未設定のままにすると `insertIfAbsent` が 0（＝既にあった）を返し、
     * 「同時再送で他が先に入れた」経路に入ってしまう。全部のテストで同じ表を持たせる。</p>
     */
    private void stubChunkTable() {
        lenient().when(chunkMapper.findBySeq(anyLong(), anyInt()))
                .thenAnswer(invocation -> chunkRows.get((Integer) invocation.getArgument(1)));
        lenient().when(chunkMapper.insertIfAbsent(any())).thenAnswer(invocation -> {
            // `when(...)` の下見で引数が null のまま呼ばれることがある（Mockito の作り）
            ClassroomRecordingChunkEntity entity = invocation.getArgument(0);
            if (entity == null || entity.getSeq() == null) {
                return 1;
            }
            if (chunkRows.containsKey(entity.getSeq())) {
                return 0;
            }
            chunkRows.put(entity.getSeq(), entity);
            return 1;
        });
        lenient().when(chunkMapper.maxSeq(anyLong())).thenAnswer(
                invocation -> chunkRows.keySet().stream().mapToInt(Integer::intValue).max().orElse(0));
    }

    /** 分塊アップロードの共通の仕込み（設定・保存先）。 */
    private void stubChunkUpload() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        lenient().when(settings.load()).thenReturn(snapshot);
        lenient().when(settings.enabled(snapshot)).thenReturn(true);
        lenient().when(settings.chunkSeconds(snapshot)).thenReturn(20);
        lenient().when(settings.maxRecordingMinutes(snapshot)).thenReturn(120);
        // ブラウザ認識にしておく（STT を呼ばず、分塊の保存だけを見る）
        lenient().when(settings.browserStt(snapshot)).thenReturn(true);
        lenient().when(storage.newRecording(eq(ACCOUNT_ID), anyLong(), any()))
                .thenReturn(new ClassroomRecordingStorage.StoredRecording(
                        "classroom/1/202609", "a.webm", "audio/webm"));
    }

    /** 保存済みの分塊の行（DB に入っている中身と同じ大きさ・チェックサム）。 */
    private static ClassroomRecordingChunkEntity storedChunkRow(int seq, byte[] content) {
        ClassroomRecordingChunkEntity entity = new ClassroomRecordingChunkEntity();
        entity.setRecordId(RECORD_ID);
        entity.setSeq(seq);
        entity.setByteSize((long) content.length);
        entity.setChecksum(sha256Of(content));
        entity.setStorageDir("classroom/1/202609");
        entity.setFileName(ClassroomRecordingStorage.chunkFileName(RECORD_ID, seq, "audio/webm"));
        entity.setMime("audio/webm");
        return entity;
    }

    /** 本番と同じ SHA-256（16 進 64 文字）。 */
    private static String sha256Of(byte[] content) {
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(content);
            StringBuilder builder = new StringBuilder();
            for (byte value : digest) {
                builder.append(String.format("%02x", value));
            }
            return builder.toString();
        } catch (java.security.NoSuchAlgorithmException cause) {
            throw new IllegalStateException(cause);
        }
    }

    private static org.springframework.web.multipart.MultipartFile chunkFile() {
        return new org.springframework.mock.web.MockMultipartFile(
                "file", "chunk-1.webm", "audio/webm", new byte[]{1, 2, 3});
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
        return record;
    }
}

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

    private static final long RECORD_ID = 10L;
    private static final long ACCOUNT_ID = 2L;

    @Mock
    private ClassroomRecordMapper recordMapper;
    @Mock
    private ClassroomSegmentMapper segmentMapper;
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
        service = new ClassroomServiceImpl(recordMapper, segmentMapper, noteMapper, presetMapper,
                settings, sttClient, storage, accountMapper, null);
        snapshot = new ClassroomAiSettings.Snapshot(Map.of());
        student = new UserPrincipal(ACCOUNT_ID, "student@example.com", "生徒", AccountType.STUDENT);
        // AI 解析（ノート生成）は未設定なら有効（本番の flag(KEY_NOTE_ENABLED, true) と同じ既定）。
        // 無効のケースは各テストで上書きする。
        lenient().when(settings.noteEnabled(any())).thenReturn(true);
    }

    @Test
    @DisplayName("書き起こしが無いまま終了しても 200 で終わり、最終まとめの行は作らない")
    void endWithoutTranscriptSkipsFinalNote() {
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(recordMapper.updateEnded(eq(RECORD_ID), anyInt(), anyInt(), eq(ACCOUNT_ID), eq(1)))
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
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(recordMapper.updateEnded(eq(RECORD_ID), anyInt(), anyInt(), eq(ACCOUNT_ID), eq(1)))
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
        when(recordMapper.updateEnded(anyLong(), anyInt(), anyInt(), anyLong(), anyInt())).thenReturn(0);

        assertThat(org.junit.jupiter.api.Assertions.assertThrows(
                com.study21.common.core.exception.ConflictException.class,
                () -> service.end(student, RECORD_ID))).isNotNull();
        verify(noteMapper, never()).insert(any());
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
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(storage.newRecording(eq(ACCOUNT_ID), any()))
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
        when(recordMapper.findById(RECORD_ID)).thenReturn(recordingRecord());
        when(settings.load()).thenReturn(snapshot);
        when(settings.noteEnabled(snapshot)).thenReturn(false);
        when(settings.retentionDays(snapshot)).thenReturn(30);
        when(recordMapper.updateEnded(eq(RECORD_ID), anyInt(), eq(30), eq(ACCOUNT_ID), eq(1)))
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
        when(storage.newRecording(eq(ACCOUNT_ID), any()))
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
        verify(storage).append("classroom/1/202609", "a.webm", new byte[]{1, 2, 3});

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
        when(storage.newRecording(eq(ACCOUNT_ID), any()))
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
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(storage.newRecording(eq(ACCOUNT_ID), any()))
                .thenReturn(new ClassroomRecordingStorage.StoredRecording("classroom/1/202609", "a.webm", "audio/webm"));
        when(sttClient.transcribe(any())).thenReturn(ClassroomSttClient.SttResponse.failure(0, "HTTP_4XX",
                "この音声形式（audio/webm;codecs=opus）は Paraformer-Realtime-V2 では認識できません。"));

        ClassroomModels.ChunkUploadResult result = service.uploadChunk(student, RECORD_ID, 8, chunkFile(), null);

        // 分塊は受け入れる（連番は進む）＝音声は保存されている
        assertThat(result.seq()).isEqualTo(8);
        assertThat(result.appendedSegments()).isEmpty();
        verify(storage).append(eq("classroom/1/202609"), eq("a.webm"), any());
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
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(storage.newRecording(eq(ACCOUNT_ID), any()))
                .thenReturn(new ClassroomRecordingStorage.StoredRecording("classroom/1/202609", "a.webm", "audio/webm"));

        ClassroomModels.ChunkUploadResult result = service.uploadChunk(student, RECORD_ID, 1, chunkFile(), null);

        // 音声は保存する（残さないと授業の記録が丸ごと消える）
        verify(storage).append("classroom/1/202609", "a.webm", new byte[]{1, 2, 3});
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
        verify(storage, never()).append(any(), any(), any());
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
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(storage.newRecording(eq(ACCOUNT_ID), any()))
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

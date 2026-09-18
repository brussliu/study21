package com.study21.user.classroom;

import com.study21.common.core.stt.DashScopeAsrClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ストリーミング書き起こし（音源別セッション・音声クロックの時間軸・冪等な保存）の検証。
 *
 * <p>外へは出ない（WebSocket は偽物に差し替える）。見張るのは:</p>
 * <ul>
 *   <li>話者ラベルは**音源**で決まる（マイクのみ＝講義／二音源＝学生・先生）</li>
 *   <li>音源ごとに別のセッションを張る（マイクと共有で取り違えない）</li>
 *   <li>文の時刻は**音声クロック**（送ったサンプル数＋阿里雲の begin_time）で決まる。
 *       セッションを作り直しても時間が戻らない</li>
 *   <li>同じ発話（発話キー）の再送は INSERT ではなく UPDATE（連番の一意制約に衝突しない）</li>
 *   <li>二音源が同時に走って連番が競合したら、採り直して再試行する</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class ClassroomSttStreamServiceTest {

    private static final long RECORD_ID = 10L;
    private static final long ACCOUNT_ID = 2L;

    @Mock
    private ClassroomAiSettings settings;
    @Mock
    private ClassroomSegmentMapper segmentMapper;
    @Mock
    private ClassroomRecordMapper recordMapper;

    private ClassroomAiSettings.Snapshot snapshot;
    private List<FakeWebSocket> sockets;

    @BeforeEach
    void setUp() {
        snapshot = new ClassroomAiSettings.Snapshot(Map.of());
        sockets = new ArrayList<>();
        lenient().when(settings.load()).thenReturn(snapshot);
        lenient().when(settings.resolveStt(snapshot)).thenReturn(
                new ClassroomAiSettings.SttConnection("alibaba", "paraformer-realtime-v2",
                        "wss://example.test/asr", "key"));
        lenient().when(settings.sttLanguageCode(snapshot, "ja")).thenReturn("ja-JP");
        lenient().when(settings.sttTimeoutSeconds(snapshot)).thenReturn(60);
        ClassroomRecordEntity record = new ClassroomRecordEntity();
        record.setRecordId(RECORD_ID);
        record.setLanguageMode("ja");
        lenient().when(recordMapper.findById(RECORD_ID)).thenReturn(record);
    }

    /** 偽の WebSocket（run-task への応答として、決めた文のイベントを返す）。 */
    static final class FakeWebSocket implements WebSocket {
        final List<String> texts = new ArrayList<>();
        final List<String> sentences;
        /** 認識へ流した音声のバイト数（同じ音を二度流していないかを見る）。 */
        long binaryBytes;
        /** `finish-task` に `task-finished` を返すか（尾部を取り切れたかどうかの作り分け）。 */
        boolean finishTask = true;
        /** `finish-task` のあとに届く尾部の確定文（停止のあとに届く文）。 */
        List<String> tailSentences = List.of();
        /** 認識そのものが失敗したことにする（`task-failed`。接続の直後に返す）。 */
        boolean taskFailed = false;

        FakeWebSocket(List<String> sentences) { this.sentences = sentences; }

        private void emit(String json) {
            // listener は接続時に渡されるので、DashScopeAsrClient 側から呼んでもらう
            listener.onText(this, json, true);
        }

        WebSocket.Listener listener;
        FakeWebSocket bind(WebSocket.Listener listener) { this.listener = listener; return this; }

        @Override public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            texts.add(data.toString());
            if (data.toString().contains("run-task")) {
                emit("{\"header\":{\"event\":\"task-started\"},\"payload\":{}}");
                if (taskFailed) {
                    emit("{\"header\":{\"event\":\"task-failed\",\"error_code\":\"InvalidParameter\","
                            + "\"error_message\":\"Audio format is not supported\"},\"payload\":{}}");
                }
                for (String sentence : sentences) emit(sentence);
            }
            if (data.toString().contains("finish-task")) {
                for (String sentence : tailSentences) emit(sentence);
                if (finishTask) emit("{\"header\":{\"event\":\"task-finished\"},\"payload\":{}}");
            }
            return CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            binaryBytes += data.remaining();
            return CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }
        @Override public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            return CompletableFuture.completedFuture(this);
        }
        @Override public void request(long n) { }
        @Override public String getSubprotocol() { return ""; }
        @Override public boolean isOutputClosed() { return false; }
        @Override public boolean isInputClosed() { return false; }
        @Override public void abort() { }
    }

    /** 1 文の `result-generated`（確定）。 */
    private static String sentence(int id, int beginMs, int endMs, String text) {
        return "{\"header\":{\"event\":\"result-generated\"},\"payload\":{\"output\":{\"sentence\":{"
                + "\"sentence_id\":" + id + ",\"begin_time\":" + beginMs + ",\"end_time\":" + endMs
                + ",\"text\":\"" + text + "\",\"sentence_end\":true}}}}";
    }

    /**
     * 偽 WebSocket を作るサービス（**開いた順**に、渡した文を返す）。
     * 音源ごとに別のセッションが開くので、順番で「どの音源の文か」を決められる。
     */
    private ClassroomSttStreamService serviceWith(List<List<String>> sentencesPerSocket) {
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            int index = sockets.size();
            List<String> sentences = index < sentencesPerSocket.size()
                    ? sentencesPerSocket.get(index) : List.of();
            FakeWebSocket socket = new FakeWebSocket(sentences);
            socket.bind(listener);
            sockets.add(socket);
            return socket;
        });
        return new ClassroomSttStreamService(settings, segmentMapper, recordMapper, client);
    }

    /** 16kHz 16bit の `seconds` 秒ぶんの PCM。 */
    private static byte[] pcm(int seconds) {
        return new byte[16_000 * 2 * seconds];
    }

    /** 接続の直後に `task-failed` を返す（認識そのものが失敗した）サービス。 */
    private ClassroomSttStreamService serviceWithTaskFailure() {
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            FakeWebSocket socket = new FakeWebSocket(List.of());
            socket.bind(listener);
            socket.taskFailed = true;
            sockets.add(socket);
            return socket;
        });
        return new ClassroomSttStreamService(settings, segmentMapper, recordMapper, client);
    }

@Test
    @DisplayName("保存に失敗しても接続は死なない（やり直したうえで警告を返し、残りの文も試す）")
    void saveFailureKeepsSessionAlive() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        // 実測で起きた形: 記録が消えていて外部キー違反（DataIntegrityViolationException）
        when(segmentMapper.insert(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("fk"));

        ClassroomSttStreamService service = serviceWith(List.of(List.of(
                sentence(1, 100, 900, "一つ目。"), sentence(2, 1_000, 1_800, "二つ目。"))));

        ClassroomSttStreamService.StreamPush push = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(3));

        // 例外は投げない（投げると WebSocket が 1011 で閉じ、このあとの文も全部落ちる）
        assertThat(push.added()).isEmpty();
        assertThat(push.error()).contains("保存できませんでした");
        // 1 文目で止まらず、2 文目も保存を試している（1 文につき 3 回やり直す＝6 回）
        verify(segmentMapper, org.mockito.Mockito.times(6)).insert(any());
        // **保存できていないので受け取り確認は進めない**（画面は同じ音を送り直せる）
        assertThat(push.processedFrames()).isZero();
    }

    @Test
    @DisplayName("保存をやり直して成功すれば受け取り確認を進める（1 回の失敗で諦めない）")
    void retriesSaveBeforeAdvancing() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doThrow(new org.springframework.dao.TransientDataAccessResourceException("一時的な失敗"))
                .doAnswer(invocation -> {
                    ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_101L);
                    return 1;
                }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 500, "やり直し。"))));

        ClassroomSttStreamService.StreamPush push = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);

        assertThat(push.error()).isNull();
        assertThat(push.added()).hasSize(1);
        assertThat(push.processedFrames()).isEqualTo(1);
        verify(segmentMapper, org.mockito.Mockito.times(2)).insert(any());
    }

    /**
     * 保存に失敗した文があるうちは、その文を含むフレームを「処理済み」にしない。
     *
     * <p>処理済みと返すと画面はその音を捨てるので、**保存できなかった文は永久に消える**。
     * 進めなければ画面は同じ番号を送り直し、認識へは流し直さずに（＝二重に認識せず）保存だけを
     * やり直せる。</p>
     */
    @Test
    @DisplayName("保存できていない文があるうちは受け取り確認を進めない（送り直しでやり直す）")
    void acknowledgementWaitsForSave() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        // はじめの 3 回（やり直しの上限）は失敗し、送り直したあとの 1 回で成功する
        doThrow(new org.springframework.dao.DataIntegrityViolationException("fk"))
                .doThrow(new org.springframework.dao.DataIntegrityViolationException("fk"))
                .doThrow(new org.springframework.dao.DataIntegrityViolationException("fk"))
                .doAnswer(invocation -> {
                    ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_201L);
                    return 1;
                }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "消えては困る文。"))));

        ClassroomSttStreamService.StreamPush failed = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        assertThat(failed.added()).isEmpty();
        assertThat(failed.error()).contains("保存できませんでした");
        assertThat(failed.processedFrames()).isZero();

        // 画面は「確認できた番号の続き」＝同じ 1 番を送り直す
        ClassroomSttStreamService.StreamPush retried = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        assertThat(retried.added()).hasSize(1);
        assertThat(retried.error()).isNull();
        assertThat(retried.processedFrames()).isEqualTo(1);

        // 認識へ流した音は 1 回だけ（同じ音を二度認識しない）
        assertThat(sockets.get(0).binaryBytes).isEqualTo(32_000L);
        verify(segmentMapper, org.mockito.Mockito.times(4)).insert(any());
    }

    @Test
    @DisplayName("話者ラベルは音源で決まる（マイクのみ＝講義／二音源＝学生・先生）")
    void speakerComesFromSource() {
        assertThat(ClassroomSttStreamService.speakerOf("mic", false)).isEqualTo("講義");
        assertThat(ClassroomSttStreamService.speakerOf("mic", true)).isEqualTo("学生");
        assertThat(ClassroomSttStreamService.speakerOf("shared", true)).isEqualTo("先生");
        // 知らない音源はマイク扱い（今までどおり）
        assertThat(ClassroomSttStreamService.normalizeSource("なにか")).isEqualTo("mic");
        assertThat(ClassroomSttStreamService.normalizeSource(null)).isEqualTo("mic");
    }

    @Test
    @DisplayName("マイクのみ: 1 本のセッションで「講義」として保存し、時刻は音声クロックで決まる")
    void micOnlyStoresLectureWithAudioClock() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(501L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 220, 2_460, "それでは始めます。"))));

        // 5 秒ぶん送ってから、文（220ms〜2460ms）が確定する
        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(5));

        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper).insert(captor.capture());
        ClassroomSegmentEntity saved = captor.getValue();
        assertThat(saved.getSpeaker()).isEqualTo("講義");
        assertThat(saved.getSource()).isEqualTo("mic");
        // 発話キーは**音声の位置**（音源#開始ミリ秒）。セッション番号は入らない
        assertThat(saved.getUtteranceKey()).isEqualTo("mic#220");
        // 位置は「送ったサンプル数（このセッションは 0 から）＋ begin_time」。HTTP の時刻は使わない
        assertThat(saved.getStartOffsetSeconds()).isEqualByComparingTo("0.220");
        assertThat(saved.getEndOffsetSeconds()).isEqualByComparingTo("2.460");
        assertThat(saved.getSeq()).isEqualTo(1);
        verify(recordMapper).addTranscribedChars(RECORD_ID, "それでは始めます。".length());
    }

    @Test
    @DisplayName("二音源: 音源ごとに別のセッションを張り、学生／先生で保存する")
    void twoSourcesUseSeparateSessions() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(601L);
            return 1;
        }).when(segmentMapper).insert(any());

        // 先に shared（先生）のセッションが開き、次に mic（学生）が開く
        ClassroomSttStreamService service = serviceWith(List.of(
                List.of(sentence(1, 100, 900, "はい、どうぞ。")),
                List.of(sentence(1, 100, 900, "先生、質問です。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "shared", pcm(1));
        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1));

        // セッションは 2 本（音源ごと）
        assertThat(sockets).hasSize(2);
        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        List<ClassroomSegmentEntity> saved = captor.getAllValues();
        assertThat(saved.get(0).getSpeaker()).isEqualTo("先生");
        assertThat(saved.get(0).getSource()).isEqualTo("shared");
        assertThat(saved.get(0).getUtteranceKey()).isEqualTo("shared#100");
        assertThat(saved.get(1).getSpeaker()).isEqualTo("学生");
        assertThat(saved.get(1).getSource()).isEqualTo("mic");
        // 同じ位置でも**音源が違えば別の発話**（同時に走る 2 音源が混ざらない）
        assertThat(saved.get(1).getUtteranceKey()).isEqualTo("mic#100");
    }

    @Test
    @DisplayName("セッションを作り直しても時間は戻らない（音源ごとに送ったサンプルを積み上げる）")
    void timelineSurvivesSessionRebuild() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(701L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(List.of(
                List.of(sentence(1, 0, 1_000, "一回目。")),
                List.of(sentence(1, 0, 1_000, "二回目。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(10));   // 10 秒ぶん（1 本目のセッション）
        service.close(RECORD_ID, "mic");                       // 回線が切れて作り直す
        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1));    // 続きを送る（2 本目のセッション）

        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        // 1 本目は 0 秒から、2 本目は**10 秒から**続く（0 に戻らない）
        assertThat(captor.getAllValues().get(0).getStartOffsetSeconds()).isEqualByComparingTo("0.000");
        assertThat(captor.getAllValues().get(1).getStartOffsetSeconds()).isEqualByComparingTo("10.000");
        // 発話キーは音声の位置（音源#開始ミリ秒）。セッションを作り直しても番号が混ざらない
        assertThat(captor.getAllValues().get(0).getUtteranceKey()).isEqualTo("mic#0");
        assertThat(captor.getAllValues().get(1).getUtteranceKey()).isEqualTo("mic#10000");
    }

    /**
     * 発話キーは**音声の位置**（音源#開始ミリ秒）で決める。認識セッションの通し番号は使わない。
     *
     * <p>使うと、張り直し・後端の再起動でセッションを作り直すたびに同じ発話が別のキーになり、
     * **同じ音が 2 行になる**（逆に、遅れて届いた重複が別の古い行を上書きすることもある）。</p>
     *
     * <p>再起動のあとは**画面が送る番号（`resume`）で音声クロックを戻す**ので、1 本目の
     * プロセスが付けたキーと同じキーになる＝INSERT ではなく UPDATE に寄る。</p>
     */
    @Test
    @DisplayName("再起動しても同じ発話は同じキー（行が増えない・別の行を壊さない）")
    void utteranceKeyIsStableAcrossRestart() {
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_301L);
            return 1;
        }).when(segmentMapper).insert(any());

        // 1 本目のプロセス: 10 秒ぶん（番号 100 まで）送り、10 秒の位置の文を保存する
        ClassroomSttStreamService first = serviceWith(
                List.of(List.of(sentence(1, 10_000, 10_900, "十秒目の文。"))));
        first.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(10), 100);

        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper).insert(captor.capture());
        String key = captor.getValue().getUtteranceKey();
        assertThat(key).isEqualTo("mic#10000");
        assertThat(key).doesNotContain("#1#");   // セッション番号を含まない

        // 後端を再起動（別のインスタンス）。画面は確認できた番号の続き＝101 番（10 秒）から送り直す
        sockets.clear();
        ClassroomSttStreamService restarted = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "十秒目の文。"))));
        restarted.resume(RECORD_ID, "mic", 101);

        ClassroomSegmentEntity stored = captor.getValue();
        stored.setSegmentId(1_301L);
        when(segmentMapper.findByUtteranceKey(RECORD_ID, "mic#10000")).thenReturn(stored);

        ClassroomSttStreamService.StreamPush push =
                restarted.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 101);

        assertThat(push.added()).hasSize(1);
        // 同じ発話キーなので INSERT は 1 本目の 1 回だけ（行が 2 つにならない）
        verify(segmentMapper, org.mockito.Mockito.times(1)).insert(any());
        verify(segmentMapper).updateByUtterance(eq(1_301L), any(), any(), eq("十秒目の文。"), any());
    }

    @Test
    @DisplayName("同じ発話の再送は INSERT せず UPDATE（連番の一意制約に衝突しない）")
    void sameUtteranceIsUpdated() {
        ClassroomSegmentEntity existing = new ClassroomSegmentEntity();
        existing.setSegmentId(801L);
        existing.setSeq(3);
        existing.setText("ありがと");
        // 発話キーは音声の位置（音源#開始ミリ秒）。同じ位置の同じ発話は同じキーになる
        when(segmentMapper.findByUtteranceKey(RECORD_ID, "mic#220")).thenReturn(existing);

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 220, 1_900, "ありがとう。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1));

        verify(segmentMapper, never()).insert(any());
        verify(segmentMapper).updateByUtterance(eq(801L), any(), any(), eq("ありがとう。"), eq("ja-JP"));
        // 文字数は**差分**だけ足す（「ありがと」→「ありがとう。」＝+2）
        verify(recordMapper).addTranscribedChars(RECORD_ID, 2);
    }

    /**
     * 同じ発話が**同時に**保存されたとき（HTTP の再送が重なった等）も、行を増やさない。
     *
     * <p>発話キーが同じ INSERT は一意制約（`UK_CR_転写セグメント_発話キー`）で弾かれる。そこで
     * 諦めると「保存できませんでした」になるので、**もう一度探して UPDATE に寄せる**。</p>
     */
    @Test
    @DisplayName("同じ発話が同時に保存されたら UPDATE に寄せる（行を増やさない・失敗にしない）")
    void duplicateUtteranceRaceFallsBackToUpdate() {
        ClassroomSegmentEntity raced = new ClassroomSegmentEntity();
        raced.setSegmentId(1_701L);
        raced.setSeq(1);
        raced.setText("同じ文。");
        // 1 回目の問い合わせでは見つからず、INSERT が一意制約で弾かれ、そのあとの問い合わせで見つかる
        when(segmentMapper.findByUtteranceKey(RECORD_ID, "mic#0")).thenReturn(null, raced);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(segmentMapper.insert(any()))
                .thenThrow(new org.springframework.dao.DuplicateKeyException("UK_CR_転写セグメント_発話キー"));

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "同じ文。"))));

        ClassroomSttStreamService.StreamPush push = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);

        assertThat(push.error()).isNull();
        assertThat(push.added()).hasSize(1);
        assertThat(push.processedFrames()).isEqualTo(1);
        verify(segmentMapper, org.mockito.Mockito.times(1)).insert(any());
        verify(segmentMapper).updateByUtterance(eq(1_701L), any(), any(), eq("同じ文。"), eq("ja-JP"));
    }

    @Test
    @DisplayName("連番が競合したら採り直して再試行する（二音源の同時保存で落とさない）")    void retriesWhenSeqConflicts() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(4, 5);
        doAnswer(invocation -> {
            throw new org.springframework.dao.DuplicateKeyException("同じ連番");
        }).doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(901L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 500, "再試行。"))));

        ClassroomSttStreamService.StreamPush result = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1));

        assertThat(result.error()).isNull();
        assertThat(result.added()).hasSize(1);
        verify(segmentMapper, org.mockito.Mockito.times(2)).insert(any());
        // 2 回目は採り直した連番（5+1=6）
        assertThat(result.added().get(0).seq()).isEqualTo(6);
    }

    /**
     * 断線して張り直すと、画面は**最後に確認できた番号の続き**から送り直す。すでに処理した番号を
     * もう一度受け取っても**二重に認識しない**（冪等）。受け取り確認の番号を返して、画面が
     * どこから送ればよいか分かるようにする。
     */
    @Test
    @DisplayName("フレーム番号: 同じ番号の送り直しは処理せず、処理済みの番号を返す")
    void skipsAlreadyProcessedFrame() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_001L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 500, "一回だけ。"))));

        ClassroomSttStreamService.StreamPush first = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        assertThat(first.processedFrames()).isEqualTo(1);
        assertThat(first.added()).hasSize(1);

        // 同じ番号をもう一度（断線後の送り直し）→ 何も足さない・番号も進めない
        ClassroomSttStreamService.StreamPush again = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        assertThat(again.added()).isEmpty();
        assertThat(again.processedFrames()).isEqualTo(1);

        // 続きの番号は普通に処理される（番号が進む。認識への送信も行われる）
        ClassroomSttStreamService.StreamPush next = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 2);
        assertThat(next.processedFrames()).isEqualTo(2);
        // 二重に保存していない（1 文だけ）
        verify(segmentMapper, org.mockito.Mockito.times(1)).insert(any());
    }

    @Test
    @DisplayName("フレーム番号なし（HTTP の分塊送信）は到着順に番号が進む")
    void assignsFrameNumbersInArrivalOrder() {
        ClassroomSttStreamService service = serviceWith(List.of(List.of()));

        assertThat(service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1)).processedFrames()).isEqualTo(1);
        assertThat(service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1)).processedFrames()).isEqualTo(2);
    }

    /* ---------------- フレームの識別（番号＋絶対位置。画面の統一の欄） ---------------- */

    /**
     * 文の時刻は**フレームの絶対位置（音声クロック）**から決める。
     *
     * <p>画面は録音の先頭からの位置を送るので、後端が「この接続で受け取ったサンプル数」から
     * 数え直すと、途中から続けた録音（張り直し・後端の再起動）で**時刻が 0 に戻る／詰まる**。</p>
     */
    @Test
    @DisplayName("絶対位置つきのフレーム: 録音の時間軸で保存する（0 に戻らない・詰まらない）")
    void absolutePositionDrivesRecordingTimeline() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(2_001L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 220, 2_460, "途中から始まる文。"))));

        // 画面は 10 秒（160000 サンプル）の位置から送る。後端は再起動直後で基準を持たない
        ClassroomSttStreamService.StreamPush push =
                service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 101, 160_000L);

        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper).insert(captor.capture());
        ClassroomSegmentEntity saved = captor.getValue();
        // 位置は「フレームの絶対位置＋begin_time」（このセッションで受け取った量ではない）
        assertThat(saved.getStartOffsetSeconds()).isEqualByComparingTo("10.220");
        assertThat(saved.getEndOffsetSeconds()).isEqualByComparingTo("12.460");
        assertThat(saved.getUtteranceKey()).isEqualTo("mic#10220");
        // 受け取り確認は画面が送った番号
        assertThat(push.processedFrames()).isEqualTo(101);
    }

    /**
     * 阿里雲の `begin_time` は**そのセッションへ送った音声の先頭**からの位置なので、
     * 録音の時間軸へは「セッションの開始位置」を 1 回だけ足す（フレームごとの位置は足さない）。
     */
    @Test
    @DisplayName("文の時刻は「セッションの開始位置＋begin_time」（フレームごとの位置を二重に足さない）")
    void sentenceTimeUsesSessionOrigin() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(2_101L);
            return 1;
        }).when(segmentMapper).insert(any());

        // 1 本目のフレーム（位置 160000＝10 秒）でセッションが始まる。2 つ目の文はその先頭から 1220ms
        ClassroomSttStreamService service = serviceWith(List.of(List.of(
                sentence(1, 0, 500, "最初の文。"), sentence(2, 1_220, 1_900, "二つ目の文。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 101, 160_000L);
        // 続きのフレーム（位置 176000＝11 秒）を送っても、基準はセッションの開始位置のまま
        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 102, 176_000L);

        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().get(0).getStartOffsetSeconds()).isEqualByComparingTo("10.000");
        // 11.220 秒（フレームの位置 11 秒を足すと 12.220 秒＝二重に数えたことになる）
        assertThat(captor.getAllValues().get(1).getStartOffsetSeconds()).isEqualByComparingTo("11.220");
    }

    /**
     * 古い位置のフレームで**時間軸を巻き戻さない**（録音の時間軸は前へしか進まない）。
     *
     * <p>送り直し・順序の入れ替わりで古い位置が届いても、認識へ流し直さず（同じ音を二度認識せず）、
     * 受け取り確認も進めない。進めると画面がその音を捨て、あとから届く新しい音まで送られなくなる。</p>
     */
    @Test
    @DisplayName("古い位置のフレームは流し直さず、時間軸も確認も戻さない")
    void stalePositionDoesNotRewindTimeline() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(2_201L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "十秒目の文。"))));

        ClassroomSttStreamService.StreamPush first =
                service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 101, 160_000L);
        assertThat(first.processedFrames()).isEqualTo(101);

        // 古い位置（0 サンプル）のフレームが届いても、時間軸は 10 秒のまま（0 へ戻さない）
        ClassroomSttStreamService.StreamPush stale =
                service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 102, 0L);

        assertThat(stale.added()).isEmpty();
        assertThat(stale.error()).isNull();
        // 番号も進めない（画面は同じ音を送り直せる）
        assertThat(stale.processedFrames()).isEqualTo(101);
        // 認識へは 1 回だけ流している（古い音を二度認識しない）
        assertThat(sockets.get(0).binaryBytes).isEqualTo(32_000L);
        verify(segmentMapper, org.mockito.Mockito.times(1)).insert(any());
        verify(segmentMapper, never()).updateByUtterance(anyLong(), any(), any(), any(), any());

        // 前へ進む位置のフレームは普通に処理される
        ClassroomSttStreamService.StreamPush next =
                service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 103, 176_000L);
        assertThat(next.processedFrames()).isEqualTo(103);
        assertThat(sockets.get(0).binaryBytes).isEqualTo(64_000L);
    }

    /**
     * 同じ識別（番号）のフレームは、位置が違って届いても**処理しない**（冪等）。
     * 画面は張り直しのあとに「確認できた番号の続き」を送り直すので、同じ番号が二度届く。
     */
    @Test
    @DisplayName("同じ番号のフレームは位置が違っても処理しない（受け取り確認は進んだまま）")
    void sameFrameNumberIsNotProcessedTwice() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(2_301L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "一回だけの文。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 101, 160_000L);
        ClassroomSttStreamService.StreamPush again =
                service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 101, 999_000L);

        assertThat(again.added()).isEmpty();
        assertThat(again.processedFrames()).isEqualTo(101);
        assertThat(sockets.get(0).binaryBytes).isEqualTo(32_000L);
        verify(segmentMapper, org.mockito.Mockito.times(1)).insert(any());
    }

    /**
     * 後端を再起動すると基準（送ったサンプル数）は 0 に戻るが、画面は**録音の絶対位置**を送るので
     * 文の時刻は録音の時間軸のまま（0 に戻らない・詰まらない）。発話キーも同じ位置なら同じ値になる。
     */
    @Test
    @DisplayName("後端を再起動しても、絶対位置つきのフレームは録音の時間軸を保つ")
    void absolutePositionSurvivesRestart() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(2_401L);
            return 1;
        }).when(segmentMapper).insert(any());

        // 1 本目のプロセス: 10 秒（160000）の位置から
        ClassroomSttStreamService first = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "十秒目の文。"))));
        first.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 101, 160_000L);

        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper).insert(captor.capture());
        assertThat(captor.getValue().getStartOffsetSeconds()).isEqualByComparingTo("10.000");
        assertThat(captor.getValue().getUtteranceKey()).isEqualTo("mic#10000");

        // 後端を再起動（別のインスタンス）。画面は 11 秒（176000）から続ける
        sockets.clear();
        ClassroomSttStreamService restarted = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "十一秒目の文。"))));
        ClassroomSttStreamService.StreamPush push =
                restarted.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 102, 176_000L);

        ArgumentCaptor<ClassroomSegmentEntity> second = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper, org.mockito.Mockito.times(2)).insert(second.capture());
        ClassroomSegmentEntity saved = second.getAllValues().get(1);
        // 0 秒（セッションの中の時刻）でも 1 秒（詰めた時刻）でもなく、録音の 11 秒
        assertThat(saved.getStartOffsetSeconds()).isEqualByComparingTo("11.000");
        assertThat(saved.getUtteranceKey()).isEqualTo("mic#11000");
        assertThat(push.processedFrames()).isEqualTo(102);
    }

    /**
     * 認識そのものが失敗した（`task-failed`）ときは**成功として返さない**。
     *
     * <p>そのうえで受け取り確認も進めない: 進めると画面がその音を捨て、書き起こしに残らない
     * 区間が確定してしまう（送り直せば新しいセッションで認識できる）。</p>
     */
    @Test
    @DisplayName("認識が失敗したら理由を返し、受け取り確認も進めない")
    void recognitionFailureIsNotSuccess() {
        ClassroomSttStreamService service = serviceWithTaskFailure();

        ClassroomSttStreamService.StreamPush push =
                service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 101, 160_000L);

        assertThat(push.added()).isEmpty();
        assertThat(push.error()).contains("音声認識が失敗しました");
        // **成功として返さない**（エラーを返す）うえで、番号も進めない（画面は送り直せる）
        assertThat(push.processedFrames()).isZero();
        verify(segmentMapper, never()).insert(any());
    }

    @Test
    @DisplayName("途中の文（interim）は保存せず、確定した文だけ保存する")
    void interimIsNotStored() {
        String interim = "{\"header\":{\"event\":\"result-generated\"},\"payload\":{\"output\":{\"sentence\":{"
                + "\"sentence_id\":1,\"begin_time\":100,\"end_time\":null,\"text\":\"ありが\",\"sentence_end\":false}}}}";
        ClassroomSttStreamService service = serviceWith(List.of(List.of(interim)));

        ClassroomSttStreamService.StreamPush result = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1));

        assertThat(result.interim()).isEqualTo("ありが");
        assertThat(result.added()).isEmpty();
        verify(segmentMapper, never()).insert(any());
    }

    /* ---------------- 収尾（finish）の検証 ---------------- */

    /**
     * 収尾は**サーバー側で見る**（画面が待ったかどうかに頼らない）。
     *
     * <p>見るところは 2 つ: (1) 認識の尾部（停止のあとに確定する文）を取り切れたか、
     * (2) 取り出した文を保存できたか。どちらも駄目なら**理由を返す**（黙って成功にしない）。</p>
     */
    @Test
    @DisplayName("尾部の確定文を集めて保存できたら、理由を返さない（正常な収尾）")
    void finishCollectsTail() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_401L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(List.of(List.of()));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        // 停止のあとに届く尾部の文（実測でいちばん遅れて来る）
        sockets.get(0).tailSentences = List.of(sentence(1, 0, 900, "尾部の文。"));

        ClassroomSttStreamService.StreamPush finished = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(finished.error()).isNull();
        assertThat(finished.added()).hasSize(1);
        assertThat(finished.added().get(0).text()).isEqualTo("尾部の文。");
        // 送り終えた番号までを確認している
        assertThat(finished.processedFrames()).isEqualTo(1);
    }

    @Test
    @DisplayName("尾部を取り切れなかったときは成功として返さない（遅れて届く文が残らない可能性を伝える）")
    void finishReportsTailNotCollected() {
        // 認識の終わり（task-finished）が時間内に来ない。待ち時間を短くして測る
        lenient().when(settings.sttTimeoutSeconds(snapshot)).thenReturn(2);
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_501L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(List.of(List.of()));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        sockets.get(0).finishTask = false;
        sockets.get(0).tailSentences = List.of(sentence(1, 0, 900, "間に合った文。"));

        ClassroomSttStreamService.StreamPush finished = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        // 取れた文は保存する（捨てない）が、**成功としては返さない**
        assertThat(finished.added()).hasSize(1);
        assertThat(finished.error()).contains("尾部");
    }

    @Test
    @DisplayName("収尾で保存に失敗したら、理由を返し受け取り確認も進めない")
    void finishReportsSaveFailure() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(segmentMapper.insert(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("fk"));

        // 文は録音の途中で確定し、保存に失敗し続ける（フレーム 1 の文）
        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "保存できない文。"))));

        ClassroomSttStreamService.StreamPush pushed = service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        assertThat(pushed.error()).contains("保存できませんでした");
        assertThat(pushed.processedFrames()).isZero();

        ClassroomSttStreamService.StreamPush finished = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(finished.added()).isEmpty();
        assertThat(finished.error()).contains("保存できませんでした");
        // 保存できていないので、送り終えた番号までを「処理済み」にしない（画面は送り直せる）
        assertThat(finished.processedFrames()).isZero();
    }

    @Test
    @DisplayName("認識セッションが無い終了は黙って成功にしない（理由を返す）")
    void finishWithoutSessionIsNotSilentSuccess() {
        ClassroomSttStreamService service = serviceWith(List.of(List.of()));

        ClassroomSttStreamService.StreamPush finished = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(finished.error()).isNotNull();
        assertThat(finished.added()).isEmpty();
    }

    @Test
    @DisplayName("終了は 2 回呼んでも行を増やさない・壊さない（2 回目は理由つき）")
    void finishTwiceIsIdempotent() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_601L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "一回だけの文。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        ClassroomSttStreamService.StreamPush first = service.finish(RECORD_ID, ACCOUNT_ID, "mic");
        ClassroomSttStreamService.StreamPush second = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(first.error()).isNull();
        // 2 回目は何も足さない（壊さない）が、黙って成功とも言わない
        assertThat(second.added()).isEmpty();
        assertThat(second.error()).isNotNull();
        verify(segmentMapper, org.mockito.Mockito.times(1)).insert(any());
    }
}

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

        /**
         * **停止のあとに尾部が遅れて届く**（同じ接続のまま。新しいセッションは開かない）。
         *
         * <p>`finish-task` は 1 回しか送らないので、時間内に `task-finished` が来なかった回の
         * やり直しでは、この方法でしか尾部を届けられない（本番でも「遅れて届く」形）。</p>
         */
        void deliverTail(String... sentences) {
            for (String sentence : sentences) {
                emit(sentence);
            }
            emit("{\"header\":{\"event\":\"task-finished\"},\"payload\":{}}");
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
    @DisplayName("音声を 1 つも送っていない音源の終了は「音声なし」の終端（黙って失敗にしない・永久に再試行させない）")
    void finishWithoutSessionIsTerminalNoAudio() {
        ClassroomSttStreamService service = serviceWith(List.of(List.of()));

        ClassroomSttStreamService.StreamPush finished = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        // 終端なので**失敗ではない**（失敗で返すと、画面が永久に再試行を出し続ける）
        assertThat(finished.error()).isNull();
        assertThat(finished.finalizeStatus())
                .isEqualTo(ClassroomSttStreamService.FINALIZE_NO_AUDIO);
        assertThat(finished.finalizeCompleted()).isTrue();
        assertThat(finished.notice()).contains("音声は送られていません");
        assertThat(finished.added()).isEmpty();
    }

    /**
     * **2 回目の収尾は「セッションが無い」ではなく、完了した結果を返す**（利用者の指示 4）。
     *
     * <p>画面は応答を失うと収尾をやり直す（`runFinalize` の【続きをやり直す】）。そこで
     * 「受け付けている認識セッションがありません」を返すと、**保存済みの文を捨てて失敗に落ちる**
     * （実際に起きていた不具合）。保存済みの文をそのまま返し、行も増やさない。</p>
     */
    @Test
    @DisplayName("終了は 2 回呼んでも同じ完了結果を返す（行を増やさない・失敗にしない）")
    void finishTwiceIsIdempotent() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_601L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(List.of(List.of()));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        // 停止のあとに届く尾部の文（収尾のこの呼び出しで保存される）
        sockets.get(0).tailSentences = List.of(sentence(1, 0, 900, "一回だけの文。"));
        ClassroomSttStreamService.StreamPush first = service.finish(RECORD_ID, ACCOUNT_ID, "mic");
        ClassroomSttStreamService.StreamPush second = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(first.error()).isNull();
        assertThat(first.finalizeCompleted()).isTrue();
        assertThat(first.added()).hasSize(1);
        assertThat(second.error()).isNull();
        assertThat(second.finalizeStatus()).isEqualTo(ClassroomSttStreamService.FINALIZE_SAVED);
        assertThat(second.finalizeCompleted()).isTrue();
        // **保存済みの文をそのまま返す**（画面は詳細へ進める）
        assertThat(second.added()).hasSize(1);
        assertThat(second.added().get(0).text()).isEqualTo("一回だけの文。");
        assertThat(second.savedCount()).isEqualTo(1);
        verify(segmentMapper, org.mockito.Mockito.times(1)).insert(any());
    }

    /* ---------------- 収尾の復帰（利用者の指示 1〜6・9） ---------------- */

    /**
     * 収尾のあとに**続きを録れる**（画面の【再開】）。
     *
     * <p>画面は録音を再開すると**番号を 1 から数え直す**ので、後端が番号で「送り直し」と
     * 「続き」を区別すると、続きの音を丸ごと捨ててしまう（音は後から作り直せない）。
     * 送り直しは**位置**（すでに処理した位置より古い）で落ちるので、番号では弾かない。</p>
     */
    @Test
    @DisplayName("収尾のあとに続きを録れる（番号が 1 からでも音を捨てない・前の文は壊さない）")
    void continuesAfterCompletedFinalize() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(2_201L);
            return 1;
        }).when(segmentMapper).insert(any());

        // 1 本目（最初の録音）と 2 本目（続き。画面は番号を 1 から数え直す）
        ClassroomSttStreamService service = serviceWith(List.of(
                List.of(sentence(1, 0, 900, "最初の文。")),
                List.of(sentence(1, 0, 900, "続きの文。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1, 0L);
        ClassroomSttStreamService.StreamPush first = service.finish(RECORD_ID, ACCOUNT_ID, "mic");
        assertThat(first.finalizeCompleted()).isTrue();

        // 再開: 番号は 1 からだが、位置は録音の時間軸のまま進む（1 秒＝16000 サンプル）
        ClassroomSttStreamService.StreamPush resumed =
                service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1, 16_000L);

        assertThat(resumed.error()).isNull();
        assertThat(resumed.finalizeStatus())
                .isEqualTo(ClassroomSttStreamService.FINALIZE_AUDIO_ACCEPTING);
        // 続きを録っているので「完了」ではない（終了の検証は新しい収尾を待つ）
        assertThat(resumed.finalizeCompleted()).isFalse();
        assertThat(sockets).hasSize(2);

        ClassroomSttStreamService.StreamPush second = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(second.error()).isNull();
        assertThat(second.finalizeCompleted()).isTrue();
        // 前の文は壊さず、続きの文を加える（2 行。同じ位置を上書きしていない）
        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper, org.mockito.Mockito.times(2)).insert(captor.capture());
        assertThat(captor.getAllValues().stream().map(ClassroomSegmentEntity::getUtteranceKey).toList())
                .containsExactly("mic#0", "mic#1000");
    }
    /**
     * **STT の尾部が時間内に来ない**ときは、状態を残して**完了にしない**（利用者の指示 1・3）。
     *
     * <p>接続は閉じない（(a) 元の要求をまだ待てる）。やり直すと同じ要求の尾部をもう一度待つので、
     * **認識し直さない**（音を送り直さない・新しいセッションを開かない）。</p>
     */
    @Test
    @DisplayName("尾部のタイムアウト: 状態を残して未完了。やり直すと認識し直さずに完了する")
    void tailTimeoutKeepsStateAndRetryCompletes() {
        lenient().when(settings.sttTimeoutSeconds(snapshot)).thenReturn(2);
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_801L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(List.of(List.of()));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        // 停止のあとに `task-finished` が来ない（＝尾部を取り切れない）
        sockets.get(0).finishTask = false;

        ClassroomSttStreamService.StreamPush timedOut = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(timedOut.error()).contains("尾部");
        assertThat(timedOut.finalizeCompleted()).isFalse();
        assertThat(timedOut.finalizeStatus()).isEqualTo(ClassroomSttStreamService.FINALIZE_FAILED);
        assertThat(timedOut.recovery()).isEqualTo(ClassroomSttStreamService.RECOVERY_AWAIT_RESULTS);
        // 状態照会でも「済んでいない・やり直せる・まだ待てる」が見える
        ClassroomSttStreamService.FinalizeStatus status = service.finalizeStatus(RECORD_ID);
        assertThat(status.completed()).isFalse();
        assertThat(status.retryable()).isTrue();
        ClassroomSttStreamService.SourceFinalizeStatus mic = status.sources().get(0);
        assertThat(mic.source()).isEqualTo("mic");
        assertThat(mic.completed()).isFalse();
        assertThat(mic.retryable()).isTrue();
        assertThat(mic.recovery()).isEqualTo(ClassroomSttStreamService.RECOVERY_AWAIT_RESULTS);
        assertThat(mic.reason()).contains("尾部");
        assertThat(mic.audioReceived()).isTrue();

        // 遅れて尾部が届く（同じ接続のまま。**認識し直していない**）
        sockets.get(0).deliverTail(sentence(1, 0, 900, "尾部の文。"));

        ClassroomSttStreamService.StreamPush retried = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(retried.error()).isNull();
        assertThat(retried.finalizeCompleted()).isTrue();
        assertThat(retried.added()).hasSize(1);
        assertThat(retried.added().get(0).text()).isEqualTo("尾部の文。");
        // 認識し直していない（接続は 1 本のまま・音も送り直していない）
        assertThat(sockets).hasSize(1);
        assertThat(sockets.get(0).binaryBytes).isEqualTo(32_000L);
        verify(segmentMapper, org.mockito.Mockito.times(1)).insert(any());
    }

    /**
     * **保存に失敗した文は残して、やり直しで保存する**（利用者の指示 2）。
     *
     * <p>やり直しでは**認識し直さない**（同じ音を流し直さない）。発話キーは認識した時点で
     * 確定しているので、同じ行に寄る（行が 2 つにならない）。</p>
     */
    @Test
    @DisplayName("保存に失敗したら文を残す: やり直すと認識し直さずに保存する（行は増えない）")
    void saveFailureKeepsPendingForRetry() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        // はじめの 6 回（1 文につき 3 回のやり直し × 2）は失敗し、収尾のやり直しで成功する
        org.springframework.dao.DataIntegrityViolationException broken =
                new org.springframework.dao.DataIntegrityViolationException("fk");
        doThrow(broken).doThrow(broken).doThrow(broken).doThrow(broken).doThrow(broken).doThrow(broken)
                .doAnswer(invocation -> {
                    ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(1_901L);
                    return 1;
                }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "あとで保存する文。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        ClassroomSttStreamService.StreamPush failed = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(failed.error()).contains("保存できませんでした");
        assertThat(failed.finalizeCompleted()).isFalse();
        assertThat(failed.pendingCount()).isEqualTo(1);
        assertThat(failed.recovery()).isEqualTo(ClassroomSttStreamService.RECOVERY_RESAVE_PENDING);

        ClassroomSttStreamService.StreamPush retried = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(retried.error()).isNull();
        assertThat(retried.finalizeCompleted()).isTrue();
        assertThat(retried.pendingCount()).isZero();
        assertThat(retried.added()).hasSize(1);
        assertThat(retried.added().get(0).text()).isEqualTo("あとで保存する文。");
        // 認識し直していない（音を送り直さない・セッションも増えない）
        assertThat(sockets).hasSize(1);
        assertThat(sockets.get(0).binaryBytes).isEqualTo(32_000L);
        ArgumentCaptor<ClassroomSegmentEntity> captor = ArgumentCaptor.forClass(ClassroomSegmentEntity.class);
        verify(segmentMapper, org.mockito.Mockito.times(7)).insert(captor.capture());
        // 行になるのは 1 回だけ（同じ発話キーに寄る＝行が 2 つにならない）
        assertThat(captor.getAllValues().stream().map(ClassroomSegmentEntity::getUtteranceKey)
                .distinct().toList()).containsExactly("mic#0");
    }

    /**
     * **接続が復帰しない**（`task-failed`）ときは、死んだ接続を保留の状態として持たず、
     * **控えた音声から認識し直す**（利用者の指示 3 (b)）。
     */
    @Test
    @DisplayName("認識が失敗したら控えた音声で認識し直す（死んだ接続は持たない）")
    void unrecoverableConnectionRetranscribesRetainedAudio() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        doAnswer(invocation -> {
            ((ClassroomSegmentEntity) invocation.getArgument(0)).setSegmentId(2_001L);
            return 1;
        }).when(segmentMapper).insert(any());

        // 1 本目は音を受け取ったあとに `task-failed` を返す。2 本目（認識し直し）は尾部の文を返す
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            FakeWebSocket socket = new FakeWebSocket(List.of());
            socket.bind(listener);
            if (sockets.isEmpty()) {
                socket.taskFailed = true;
            } else {
                socket.tailSentences = List.of(sentence(1, 0, 900, "認識し直した文。"));
            }
            sockets.add(socket);
            return socket;
        });
        ClassroomSttStreamService service =
                new ClassroomSttStreamService(settings, segmentMapper, recordMapper, client);

        ClassroomSttStreamService.StreamPush pushed =
                service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);

        // 認識そのものが失敗した（音声は受け取っている）
        assertThat(pushed.error()).contains("音声認識が失敗しました");
        // **保留の状態に閉じた接続を残さない**: 控えた音声でやり直す、と状態に出る
        ClassroomSttStreamService.SourceFinalizeStatus state =
                service.finalizeStatus(RECORD_ID).sources().get(0);
        assertThat(state.status()).isEqualTo(ClassroomSttStreamService.FINALIZE_FAILED);
        assertThat(state.recovery()).isEqualTo(ClassroomSttStreamService.RECOVERY_RETRANSCRIBE);
        assertThat(state.audioRetained()).isTrue();
        assertThat(state.retainedAudioRef()).startsWith("memory:");

        ClassroomSttStreamService.StreamPush finished = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(finished.error()).isNull();
        assertThat(finished.finalizeCompleted()).isTrue();
        assertThat(finished.added()).hasSize(1);
        assertThat(finished.added().get(0).text()).isEqualTo("認識し直した文。");
        // 2 本目のセッションを開き、控えておいた音を**認識し直している**
        assertThat(sockets).hasSize(2);
        assertThat(sockets.get(1).binaryBytes).isEqualTo(32_000L);
    }

    /** 無音の授業: 音声は受け取ったが確定した文が 1 つも無い＝**発話なしの終端**（利用者の指示 9）。 */
    @Test
    @DisplayName("音声はあるが発話が無いときは「発話なし」の終端（失敗でも未完了でもない）")
    void silenceReachesTerminalNoUtterance() {
        ClassroomSttStreamService service = serviceWith(List.of(List.of()));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(2), 1);
        ClassroomSttStreamService.StreamPush finished = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        assertThat(finished.error()).isNull();
        assertThat(finished.finalizeStatus()).isEqualTo(ClassroomSttStreamService.FINALIZE_NO_UTTERANCE);
        assertThat(finished.finalizeCompleted()).isTrue();
        assertThat(finished.savedCount()).isZero();
        assertThat(finished.notice()).contains("発話");
        // 2 回目も同じ終端（やり直しを促さない）
        ClassroomSttStreamService.StreamPush again = service.finish(RECORD_ID, ACCOUNT_ID, "mic");
        assertThat(again.error()).isNull();
        assertThat(again.finalizeCompleted()).isTrue();
    }

    /**
     * **片方の音源が失敗しても、もう片方の保存済みの文は壊れない**（利用者の指示 9・10）。
     * やり直しは**まだ済んでいない音源だけ**を進める。
     */
    @Test
    @DisplayName("片方の音源が失敗しても、もう片方は完了したまま（やり直しは未完了だけ）")
    void oneSourceFailureDoesNotDamageTheOther() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        // shared（先生）の INSERT だけ失敗する
        doAnswer(invocation -> {
            ClassroomSegmentEntity entity = invocation.getArgument(0);
            if ("shared".equals(entity.getSource())) {
                throw new org.springframework.dao.DataIntegrityViolationException("fk");
            }
            entity.setSegmentId(2_101L);
            return 1;
        }).when(segmentMapper).insert(any());

        ClassroomSttStreamService service = serviceWith(List.of(
                List.of(sentence(1, 100, 900, "先生の話。")),
                List.of(sentence(1, 100, 900, "学生の話。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "shared", pcm(1), 1);
        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);

        ClassroomSttStreamService.FinalizeStatus status = service.finalizeStatus(RECORD_ID);
        // まだ収尾をしていないので両方とも未完了（音声は受け取っている）
        assertThat(status.completed()).isFalse();
        assertThat(status.audioReceived()).isTrue();

        ClassroomSttStreamService.StreamPush shared = service.finish(RECORD_ID, ACCOUNT_ID, "shared");
        ClassroomSttStreamService.StreamPush mic = service.finish(RECORD_ID, ACCOUNT_ID, "mic");

        // 失敗したのは shared だけ。mic は完了して文も保存できている
        assertThat(shared.error()).contains("保存できませんでした");
        assertThat(shared.finalizeCompleted()).isFalse();
        assertThat(mic.error()).isNull();
        assertThat(mic.finalizeCompleted()).isTrue();
        assertThat(mic.savedCount()).isEqualTo(1);

        // 照会すると、済んだのは mic だけ・やり直せるのは shared だけ
        ClassroomSttStreamService.FinalizeStatus after = service.finalizeStatus(RECORD_ID);
        assertThat(after.completed()).isFalse();
        assertThat(after.sources().get(0).source()).isEqualTo("mic");
        assertThat(after.sources().get(0).completed()).isTrue();
        assertThat(after.sources().get(0).retryable()).isFalse();
        assertThat(after.sources().get(1).source()).isEqualTo("shared");
        assertThat(after.sources().get(1).completed()).isFalse();
        assertThat(after.sources().get(1).retryable()).isTrue();
        assertThat(after.sources().get(1).pendingCount()).isEqualTo(1);
        // 通す側の条件は「両方済んでいるか」なので、まだ通さない
        assertThat(after.notice()).contains("共有した音");

        // もう片方（mic）はやり直しても何も変わらない（行が増えない・失敗しない）
        ClassroomSttStreamService.StreamPush micAgain = service.finish(RECORD_ID, ACCOUNT_ID, "mic");
        assertThat(micAgain.error()).isNull();
        assertThat(micAgain.finalizeCompleted()).isTrue();
        assertThat(micAgain.savedCount()).isEqualTo(1);
        // 保存済みの文を書き換えていない（完了した音源には触らない）
        verify(segmentMapper, never()).updateByUtterance(anyLong(), any(), any(), any(), any());
    }

    /**
     * やり直しても直らない終端（試行の上限）は**失敗として返さない**（利用者の指示 9）。
     *
     * <p>失敗として返し続けると、画面が永久に再試行を出し続けて授業を終えられない。
     * 知らせ（`notice`）で伝え、`/end` は通す（書き起こしが不完全であることは残す）。</p>
     */
    @Test
    @DisplayName("やり直しても直らない収尾は終端: 失敗ではなく知らせで返す")
    void exhaustedFinalizeIsTerminalNotFailure() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        // データベースがずっと受け付けない（保存待ちが片付かない）
        when(segmentMapper.insert(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("fk"));

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "保存できない文。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);

        ClassroomSttStreamService.StreamPush last = null;
        for (int attempt = 1; attempt <= ClassroomSttStreamService.MAX_FINALIZE_ATTEMPTS + 1; attempt += 1) {
            last = service.finish(RECORD_ID, ACCOUNT_ID, "mic");
        }

        assertThat(last).isNotNull();
        // 終端: **失敗として返さない**（画面は先へ進める）が、完了とも言わない
        assertThat(last.error()).isNull();
        assertThat(last.finalizeCompleted()).isFalse();
        assertThat(last.notice()).contains("これ以上やり直しても直りません");
        ClassroomSttStreamService.FinalizeStatus status = service.finalizeStatus(RECORD_ID);
        assertThat(status.retryable()).isFalse();
        assertThat(status.sources().get(0).retryable()).isFalse();
        assertThat(status.sources().get(0).pendingCount()).isEqualTo(1);
    }

    /** 保持期限: 控えた収尾の状態（保存待ち・控えた音声・完了の控え）は期限を過ぎたら片付ける。 */
    @Test
    @DisplayName("収尾の状態は保持期限を過ぎたら片付けられる（資源を無限に増やさない）")
    void finalizeStateIsReleasedAfterRetention() {
        when(segmentMapper.findByUtteranceKey(anyLong(), any())).thenReturn(null);
        when(segmentMapper.maxSeq(RECORD_ID)).thenReturn(null);
        when(segmentMapper.insert(any()))
                .thenThrow(new org.springframework.dao.DataIntegrityViolationException("fk"));

        ClassroomSttStreamService service = serviceWith(
                List.of(List.of(sentence(1, 0, 900, "保存できない文。"))));

        service.push(RECORD_ID, ACCOUNT_ID, "mic", pcm(1), 1);
        service.finish(RECORD_ID, ACCOUNT_ID, "mic");
        assertThat(service.finalizeStatus(RECORD_ID).sources().get(0).pendingCount()).isEqualTo(1);
        assertThat(service.finalizeStatus(RECORD_ID).sources().get(0).audioRetained()).isTrue();

        // 期限の直前は残っている
        assertThat(service.evictExpired(System.currentTimeMillis()
                + ClassroomSttStreamService.RETENTION_MILLIS - 1_000L)).isZero();
        assertThat(service.finalizeStatus(RECORD_ID).sources().get(0).pendingCount()).isEqualTo(1);

        // 期限を過ぎたら片付く（保存待ちも控えた音声も解放される）
        assertThat(service.evictExpired(System.currentTimeMillis()
                + ClassroomSttStreamService.RETENTION_MILLIS + 1_000L)).isEqualTo(1);
        ClassroomSttStreamService.FinalizeStatus after = service.finalizeStatus(RECORD_ID);
        assertThat(after.sources().get(0).pendingCount()).isZero();
        assertThat(after.sources().get(0).audioRetained()).isFalse();
        assertThat(after.sources().get(0).status())
                .isEqualTo(ClassroomSttStreamService.FINALIZE_NOT_STARTED);
    }
}

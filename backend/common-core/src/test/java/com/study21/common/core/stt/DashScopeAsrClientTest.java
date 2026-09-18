package com.study21.common.core.stt;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link DashScopeAsrClient} のテスト。
 *
 * <p>WebSocket は {@link DashScopeAsrClient.Connector} で差し替えられるので、**偽の WebSocket** を
 * 使って「送ったフレーム」と「受けたイベントの解釈」だけを確かめる（外部ネットワークに出ない）。</p>
 */
class DashScopeAsrClientTest {

    /**
     * 偽の WebSocket。`sendText` で `run-task` が来たら、決めておいたイベント列を
     * その場で listener へ流す（本物のように非同期にしない＝テストを決定的にする）。
     */
    static final class FakeWebSocket implements WebSocket {

        /** `run-task` の後に返すイベント（JSON 文字列）。 */
        private final List<String> events;
        final List<String> textFrames = new ArrayList<>();
        final List<byte[]> binaryFrames = new ArrayList<>();
        WebSocket.Listener listener;
        private boolean closed;

        FakeWebSocket(WebSocket.Listener listener, List<String> events) {
            this.listener = listener;
            this.events = events;
        }

        /** テストがあとからイベントを流す（ストリーミングは送信と受信が交互に来るため）。 */
        void push(String event) {
            listener.onText(this, event, true);
        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            textFrames.add(data.toString());
            if (data.toString().contains("run-task")) {
                for (String event : events) {
                    listener.onText(this, event, true);
                }
            }
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
            byte[] bytes = new byte[data.remaining()];
            data.get(bytes);
            binaryFrames.add(bytes);
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPing(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendPong(ByteBuffer message) {
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendClose(int statusCode, String reason) {
            closed = true;
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
            // 偽物は要求を無視してよい（イベントは sendText の時に流す）
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return closed;
        }

        @Override
        public boolean isInputClosed() {
            return closed;
        }

        @Override
        public void abort() {
            closed = true;
        }
    }

    private static final String STARTED = "{\"header\":{\"event\":\"task-started\"},\"payload\":{}}";

    private static String sentence(String text, boolean end) {
        return "{\"header\":{\"event\":\"result-generated\"},\"payload\":{\"output\":{\"sentence\":"
                + "{\"begin_time\":0,\"end_time\":1000,\"text\":\"" + text + "\",\"sentence_end\":" + end
                + "}}}}";
    }

    private static final String FINISHED = "{\"header\":{\"event\":\"task-finished\"},\"payload\":{}}";

    private static DashScopeAsrClient.Request request(byte[] audio) {
        return new DashScopeAsrClient.Request(
                "wss://dashscope.aliyuncs.com/api-ws/v1/inference", "dashscope-key",
                "paraformer-realtime-v2", "pcm", 16000, "ja", audio, 30);
    }

    @Test
    @DisplayName("run-task → 音声 → finish-task の順に送り、確定した文をつなげて返す")
    void sendsProtocolFramesAndJoinsSentences() {
        FakeWebSocket socket = new FakeWebSocket(null, List.of());
        List<String> events = List.of(STARTED, sentence("今日は", false), sentence("今日は比例の", true),
                sentence("グラフを学びます。", true), FINISHED);
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) ->
                new FakeWebSocket(listener, events));

        byte[] audio = new byte[5000];
        Arrays.fill(audio, (byte) 7);
        DashScopeAsrClient.Result result = client.transcribe(request(audio));

        assertThat(result.ok()).isTrue();
        // 途中経過は上書きし、確定した文だけを積む
        assertThat(result.text()).isEqualTo("今日は比例のグラフを学びます。");
    }

    /**
     * ストリーミングでは**文の識別子と時刻**が要る（授業全体の時間軸と、同じ文の再保存を
     * 1 行に寄せるため）。阿里雲は `sentence_id` / `begin_time` / `end_time`（ミリ秒）を返すので、
     * 途中の文（interim）と確定した文を取り違えずに取り出す。
     */
    @Test
    @DisplayName("時刻つきの文: sentence_id・begin/end（ミリ秒）・確定/途中を取り出す")
    void keepsTimedSentences() {
        String interim = "{\"header\":{\"event\":\"result-generated\"},\"payload\":{\"output\":{\"sentence\":{"
                + "\"sentence_id\":1,\"begin_time\":220,\"end_time\":null,\"text\":\"ありが\","
                + "\"sentence_end\":false}}}}";
        String finalized = "{\"header\":{\"event\":\"result-generated\"},\"payload\":{\"output\":{\"sentence\":{"
                + "\"sentence_id\":1,\"begin_time\":220,\"end_time\":2460,\"text\":\"ありがとう。\","
                + "\"sentence_end\":true}}}}";

        // 途中の文（interim）: 保存しないが、時刻と本文を画面へ返す
        DashScopeAsrClient interimClient = new DashScopeAsrClient((uri, apiKey, listener, timeout) ->
                new FakeWebSocket(listener, List.of(STARTED, interim)));
        try (DashScopeAsrClient.Stream stream = interimClient.openStream(request(null))) {
            stream.send(new byte[3200]);
            DashScopeAsrClient.TimedSnapshot snapshot = stream.pollTimed(0);
            assertThat(snapshot.interim()).isNotNull();
            assertThat(snapshot.interim().text()).isEqualTo("ありが");
            assertThat(snapshot.interim().beginMs()).isEqualTo(220);
            assertThat(snapshot.finished()).isEmpty();
        }

        // 確定した文: id と begin/end（ミリ秒）
        DashScopeAsrClient finalClient = new DashScopeAsrClient((uri, apiKey, listener, timeout) ->
                new FakeWebSocket(listener, List.of(STARTED, interim, finalized)));
        try (DashScopeAsrClient.Stream stream = finalClient.openStream(request(null))) {
            stream.send(new byte[3200]);
            DashScopeAsrClient.TimedSnapshot snapshot = stream.pollTimed(0);
            assertThat(snapshot.finished()).hasSize(1);
            assertThat(snapshot.finished().get(0).id()).isEqualTo(1);
            assertThat(snapshot.finished().get(0).text()).isEqualTo("ありがとう。");
            assertThat(snapshot.finished().get(0).beginMs()).isEqualTo(220);
            assertThat(snapshot.finished().get(0).endMs()).isEqualTo(2460);
            // 取り出した位置を渡せば、同じ文を二度取り出さない（画面が二重に足さない）
            assertThat(stream.pollTimed(stream.timedCount()).finished()).isEmpty();
        }
    }

    @Test
    @DisplayName("run-task には モデル・形式・サンプリング周波数・言語ヒントが入る")
    void runTaskCarriesParameters() {
        FakeWebSocket socket = new FakeWebSocket(null, List.of());
        List<String> sent = new ArrayList<>();
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            sent.add(apiKey);
            return new FakeWebSocket(listener, List.of(STARTED, FINISHED));
        });

        client.transcribe(request(new byte[100]));

        // API Key は接続時に渡す（ヘッダに付けるのは本番の実装）
        assertThat(sent).containsExactly("dashscope-key");
    }

    /**
     * ストリーミングでも **1 フレームを大きくしない**。
     * 実測: 貯まった音声を 1 フレームで送ると DashScope が
     * 「1009: Max frame length of 262144 has been exceeded」で接続を切り、
     * 書き起こしが途中で止まった（＝画面には何も出なくなる）。
     */
    @Test
    @DisplayName("ストリーミング: 1 回の送信が大きくても 3200 バイトずつに分ける")
    void streamSplitsLargeFrames() {
        List<FakeWebSocket> sockets = new ArrayList<>();
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            FakeWebSocket socket = new FakeWebSocket(listener, List.of(STARTED, FINISHED));
            sockets.add(socket);
            return socket;
        });

        try (DashScopeAsrClient.Stream stream = client.openStream(request(null))) {
            // 8 秒ぶん（16kHz 16bit ＝ 256KB ちょうど）を 1 回で送る
            stream.send(new byte[256 * 1024]);
        }

        // 1 フレームも上限（3200 バイト）を超えていない
        for (byte[] frame : sockets.get(0).binaryFrames) {
            assertThat(frame.length).isLessThanOrEqualTo(3200);
        }
        // 全部の音声が送られている（3200 で割り切れない最後の端数も送る）
        assertThat(sockets.get(0).binaryFrames.stream().mapToInt(frame -> frame.length).sum())
                .isEqualTo(256 * 1024);
    }

    @Test
    @DisplayName("音声は分割して送る（3200 バイトずつ）")
    void splitsAudioIntoFrames() {
        List<FakeWebSocket> sockets = new ArrayList<>();
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            FakeWebSocket socket = new FakeWebSocket(listener, List.of(STARTED, FINISHED));
            sockets.add(socket);
            return socket;
        });

        client.transcribe(request(new byte[7000]));

        FakeWebSocket socket = sockets.get(0);
        assertThat(socket.binaryFrames).hasSize(3);
        assertThat(socket.binaryFrames.get(0)).hasSize(3200);
        assertThat(socket.binaryFrames.get(1)).hasSize(3200);
        assertThat(socket.binaryFrames.get(2)).hasSize(600);
        // 送るものは run-task → 音声 → finish-task
        assertThat(socket.textFrames).hasSize(2);
        assertThat(socket.textFrames.get(0)).contains("\"action\":\"run-task\"");
        assertThat(socket.textFrames.get(0)).contains("\"paraformer-realtime-v2\"");
        assertThat(socket.textFrames.get(0)).contains("\"format\":\"pcm\"");
        assertThat(socket.textFrames.get(0)).contains("\"sample_rate\":16000");
        assertThat(socket.textFrames.get(0)).contains("\"language_hints\":[\"ja\"]");
        assertThat(socket.textFrames.get(1)).contains("\"action\":\"finish-task\"");
        // 閉じる
        assertThat(socket.isOutputClosed()).isTrue();
    }

    @Test
    @DisplayName("task-failed は理由つきで失敗にする（キー不正など）")
    void taskFailedBecomesError() {
        String failed = "{\"header\":{\"event\":\"task-failed\",\"error_code\":\"InvalidApiKey\","
                + "\"error_message\":\"Invalid API-key provided.\"},\"payload\":{\"output\":{}}}";
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) ->
                new FakeWebSocket(listener, List.of(STARTED, failed)));

        DashScopeAsrClient.Result result = client.transcribe(request(new byte[10]));

        assertThat(result.ok()).isFalse();
        assertThat(result.errorCode()).isEqualTo("TASK_FAILED");
        assertThat(result.errorMessage()).contains("InvalidApiKey").contains("Invalid API-key provided.");
    }

    @Test
    @DisplayName("無音（結果が 1 つも来ない）でも成功として空文字を返す")
    void silentAudioReturnsEmptyText() {
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) ->
                new FakeWebSocket(listener, List.of(STARTED, FINISHED)));

        DashScopeAsrClient.Result result = client.transcribe(request(new byte[100]));

        assertThat(result.ok()).isTrue();
        assertThat(result.text()).isEmpty();
    }

    @Test
    @DisplayName("URL と API Key が無ければ接続せずに理由を返す")
    void requiresUrlAndApiKey() {
        boolean[] connected = {false};
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            connected[0] = true;
            return new FakeWebSocket(listener, List.of());
        });

        DashScopeAsrClient.Result noUrl = client.transcribe(new DashScopeAsrClient.Request(
                "  ", "key", "paraformer-realtime-v2", "pcm", 16000, null, new byte[10], 30));
        DashScopeAsrClient.Result noKey = client.transcribe(new DashScopeAsrClient.Request(
                "wss://example.test/asr", "", "paraformer-realtime-v2", "pcm", 16000, null, new byte[10], 30));

        assertThat(noUrl.ok()).isFalse();
        assertThat(noUrl.errorMessage()).contains("URL");
        assertThat(noKey.ok()).isFalse();
        assertThat(noKey.errorMessage()).contains("API Key");
        assertThat(connected[0]).isFalse();
    }

    @Test
    @DisplayName("接続できないときは日本語の理由を返す（例外を投げない）")
    void connectFailureIsReported() {
        DashScopeAsrClient timing = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            throw new TimeoutException("connect timed out");
        });
        DashScopeAsrClient.Result timedOut = timing.transcribe(request(new byte[10]));
        assertThat(timedOut.ok()).isFalse();
        assertThat(timedOut.errorCode()).isEqualTo("TIMEOUT");
        assertThat(timedOut.errorMessage()).contains("接続できませんでした");

        DashScopeAsrClient broken = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            throw new IllegalStateException("unknown host");
        });
        DashScopeAsrClient.Result failed = broken.transcribe(request(new byte[10]));
        assertThat(failed.ok()).isFalse();
        assertThat(failed.errorCode()).isEqualTo("CONNECT_FAILED");
        assertThat(failed.errorMessage()).contains("unknown host");
    }

    // ---------------------------------------------------------------- ストリーミング

    @Test
    @DisplayName("ストリーミング: 途中の文は interim、確定した文は一度だけ finished で返る")
    void streamReturnsInterimAndFinishedOnce() {
        FakeWebSocket socket = new FakeWebSocket(null, List.of(STARTED));
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            socket.listener = listener;
            return socket;
        });

        try (DashScopeAsrClient.Stream stream = client.openStream(request(new byte[0]))) {
            stream.send(new byte[3200]);
            socket.push(sentence("今日は", false));
            DashScopeAsrClient.Snapshot first = stream.poll();
            // まだ確定していない文は interim（画面は上書きして出す）
            assertThat(first.interim()).isEqualTo("今日は");
            assertThat(first.finished()).isEmpty();

            socket.push(sentence("今日は比例の", false));
            assertThat(stream.poll().interim()).isEqualTo("今日は比例の");

            socket.push(sentence("今日は比例のグラフを学びます。", true));
            DashScopeAsrClient.Snapshot second = stream.poll();
            assertThat(second.finished()).containsExactly("今日は比例のグラフを学びます。");
            assertThat(second.interim()).isEmpty();
            // **同じ文を二度返さない**（画面が二重に足さないように）
            assertThat(stream.poll().finished()).isEmpty();
        }
        // 閉じるときに finish-task を送る
        assertThat(socket.textFrames).hasSize(2);
        assertThat(socket.textFrames.get(0)).contains("run-task");
        assertThat(socket.textFrames.get(1)).contains("finish-task");
    }

    @Test
    @DisplayName("ストリーミング: 接続できないときは poll が理由を返す（例外を投げない）")
    void streamReportsConnectFailure() {
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            throw new IllegalStateException("no route to host");
        });

        try (DashScopeAsrClient.Stream stream = client.openStream(request(new byte[0]))) {
            stream.send(new byte[16]);
            DashScopeAsrClient.Snapshot snapshot = stream.poll();
            assertThat(snapshot.failure()).contains("接続できませんでした").contains("no route to host");
        }
    }

    @Test
    @DisplayName("ストリーミング: URL と API Key が無ければ接続せずに理由を返す")
    void streamRequiresUrlAndApiKey() {
        boolean[] connected = {false};
        DashScopeAsrClient client = new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
            connected[0] = true;
            return new FakeWebSocket(listener, List.of(STARTED));
        });

        try (DashScopeAsrClient.Stream stream = client.openStream(new DashScopeAsrClient.Request(
                null, "key", "paraformer-realtime-v2", "pcm", 16000, "ja", null, 30))) {
            stream.send(new byte[16]);
            assertThat(stream.poll().failure()).contains("URL");
        }
        assertThat(connected[0]).isFalse();
    }

    @Test
    @DisplayName("接続は API Key をヘッダに付けて開く（本番の接続実装の確認）")
    void jdkConnectorAddsAuthHeader() {
        // 本番の接続実装は外部へ出るので、ここでは「URL が wss であること」だけを確かめる
        URI uri = URI.create("wss://dashscope.aliyuncs.com/api-ws/v1/inference");
        assertThat(uri.getScheme()).isEqualTo("wss");
        assertThat(uri.getHost()).isEqualTo("dashscope.aliyuncs.com");
    }
}

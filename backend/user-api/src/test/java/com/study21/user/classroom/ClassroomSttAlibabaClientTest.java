package com.study21.user.classroom;

import com.study21.common.core.stt.DashScopeAsrClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ClassroomSttAlibabaClient} の検証（**外部ネットワークに出ない**）。
 *
 * <p>WebSocket は {@link DashScopeAsrClient.Connector} で差し替えられるので、偽の WebSocket を
 * 使って「送った run-task の中身」と「結果の翻訳」だけを確かめる。</p>
 */
class ClassroomSttAlibabaClientTest {

    /** 送られたテキストフレームを記録し、決めたイベントを返す偽の WebSocket。 */
    static final class FakeWebSocket implements WebSocket {
        private final WebSocket.Listener listener;
        private final List<String> events;
        final List<String> texts = new ArrayList<>();

        FakeWebSocket(WebSocket.Listener listener, List<String> events) {
            this.listener = listener;
            this.events = events;
        }

        @Override
        public CompletableFuture<WebSocket> sendText(CharSequence data, boolean last) {
            texts.add(data.toString());
            if (data.toString().contains("run-task")) {
                for (String event : events) {
                    listener.onText(this, event, true);
                }
            }
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public CompletableFuture<WebSocket> sendBinary(ByteBuffer data, boolean last) {
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
            return CompletableFuture.completedFuture(this);
        }

        @Override
        public void request(long n) {
        }

        @Override
        public String getSubprotocol() {
            return "";
        }

        @Override
        public boolean isOutputClosed() {
            return false;
        }

        @Override
        public boolean isInputClosed() {
            return false;
        }

        @Override
        public void abort() {
        }
    }

    private static final String STARTED = "{\"header\":{\"event\":\"task-started\"},\"payload\":{}}";
    private static final String FINISHED = "{\"header\":{\"event\":\"task-finished\"},\"payload\":{}}";

    private static String sentence(String text) {
        return "{\"header\":{\"event\":\"result-generated\"},\"payload\":{\"output\":{\"sentence\":"
                + "{\"text\":\"" + text + "\",\"sentence_end\":true}}}}";
    }

    private static ClassroomSttClient.SttRequest request(String mime, String languageCode) {
        return request(mime, languageCode, new byte[800]);
    }

    private static ClassroomSttClient.SttRequest request(String mime, String languageCode, byte[] audio) {
        return new ClassroomSttClient.SttRequest("alibaba", "paraformer-realtime-v2",
                "wss://dashscope.aliyuncs.com/api-ws/v1/inference", "dashscope-key",
                languageCode, "en-US", audio, mime, 30);
    }

    /** MPEG 1 Layer III・44.1kHz のフレームヘッダ（`FF FB 90 00`）で始まる mp3。 */
    private static byte[] mp3Audio() {
        byte[] bytes = new byte[800];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xFB;
        bytes[2] = (byte) 0x90;
        return bytes;
    }

    @Test
    @DisplayName("audio/L16（ヘッダ無し PCM）は pcm・16kHz・言語ヒント付きで送る")
    void sendsPcmFormatForRawAudio() {
        List<FakeWebSocket> sockets = new ArrayList<>();
        ClassroomSttAlibabaClient client = new ClassroomSttAlibabaClient(
                new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
                    FakeWebSocket socket = new FakeWebSocket(listener,
                            List.of(STARTED, sentence("比例のグラフを学びます。"), FINISHED));
                    sockets.add(socket);
                    return socket;
                }));

        ClassroomSttClient.SttResponse response = client.transcribe(request("audio/L16", "ja-JP"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.segments()).hasSize(1);
        assertThat(response.segments().get(0).text()).isEqualTo("比例のグラフを学びます。");
        assertThat(response.segments().get(0).language()).isEqualTo("ja-JP");

        String runTask = sockets.get(0).texts.get(0);
        assertThat(runTask).contains("\"model\":\"paraformer-realtime-v2\"");
        assertThat(runTask).contains("\"format\":\"pcm\"");
        assertThat(runTask).contains("\"sample_rate\":16000");
        assertThat(runTask).contains("\"language_hints\":[\"ja\"]");
    }

    /**
     * 取り込んだ mp3 は**ファイル自身のサンプリング周波数**（44.1kHz など）を持つ。
     * 画面が作る PCM と同じ 16kHz を宣言すると、DashScope が
     * 「sample rate 16000 not equals with real 44100」で**デコードに失敗**する（実測）。
     * そのため mp3・wav はヘッダから読んだ周波数を宣言する。
     */
    @Test
    @DisplayName("mp3（取り込み）はヘッダから読んだ 44.1kHz を宣言する")
    void declaresSampleRateOfImportedMp3() {
        List<FakeWebSocket> sockets = new ArrayList<>();
        ClassroomSttAlibabaClient client = new ClassroomSttAlibabaClient(
                new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
                    FakeWebSocket socket = new FakeWebSocket(listener,
                            List.of(STARTED, sentence("比例のグラフを学びます。"), FINISHED));
                    sockets.add(socket);
                    return socket;
                }));

        ClassroomSttClient.SttResponse response =
                client.transcribe(request("audio/mpeg", "ja-JP", mp3Audio()));

        assertThat(response.isSuccess()).isTrue();
        String runTask = sockets.get(0).texts.get(0);
        assertThat(runTask).contains("\"format\":\"mp3\"");
        assertThat(runTask).contains("\"sample_rate\":44100");
    }

    @Test
    @DisplayName("周波数が読めないファイルは sample_rate を送らない（分からない値を宣言しない）")
    void omitsSampleRateWhenUnknown() {
        List<FakeWebSocket> sockets = new ArrayList<>();
        ClassroomSttAlibabaClient client = new ClassroomSttAlibabaClient(
                new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
                    FakeWebSocket socket = new FakeWebSocket(listener, List.of(STARTED, FINISHED));
                    sockets.add(socket);
                    return socket;
                }));

        client.transcribe(request("audio/mpeg", "ja-JP", new byte[800]));

        assertThat(sockets.get(0).texts.get(0)).doesNotContain("sample_rate");
    }

    @Test
    @DisplayName("中国語の授業は zh のヒントを送る（混在モードの副言語は使わない）")
    void mapsLanguageHint() {
        List<FakeWebSocket> sockets = new ArrayList<>();
        ClassroomSttAlibabaClient client = new ClassroomSttAlibabaClient(
                new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
                    FakeWebSocket socket = new FakeWebSocket(listener, List.of(STARTED, FINISHED));
                    sockets.add(socket);
                    return socket;
                }));

        client.transcribe(request("audio/L16", "zh-CN"));

        assertThat(sockets.get(0).texts.get(0)).contains("\"language_hints\":[\"zh\"]");
        assertThat(ClassroomSttAlibabaClient.languageHintOf("en-US")).isEqualTo("en");
        assertThat(ClassroomSttAlibabaClient.languageHintOf("ja")).isEqualTo("ja");
        assertThat(ClassroomSttAlibabaClient.languageHintOf(null)).isNull();
        assertThat(ClassroomSttAlibabaClient.languageHintOf("xx-YY")).isNull();
    }

    @Test
    @DisplayName("認識できない形式（webm など）は理由を返す（送らない）")
    void rejectsUnsupportedAudioFormat() {
        boolean[] connected = {false};
        ClassroomSttAlibabaClient client = new ClassroomSttAlibabaClient(
                new DashScopeAsrClient((uri, apiKey, listener, timeout) -> {
                    connected[0] = true;
                    return new FakeWebSocket(listener, List.of(STARTED, FINISHED));
                }));

        ClassroomSttClient.SttResponse response = client.transcribe(request("audio/webm", "ja-JP"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
        assertThat(response.errorMessage()).contains("audio/webm").contains("Chrome / Edge");
        assertThat(connected[0]).isFalse();
    }

    @Test
    @DisplayName("無音（結果なし）は空のセグメントを返す（失敗にしない）")
    void silentAudioReturnsNoSegments() {
        ClassroomSttAlibabaClient client = new ClassroomSttAlibabaClient(
                new DashScopeAsrClient((uri, apiKey, listener, timeout) ->
                        new FakeWebSocket(listener, List.of(STARTED, FINISHED))));

        ClassroomSttClient.SttResponse response = client.transcribe(request("audio/L16", "ja-JP"));

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.segments()).isEmpty();
    }

    @Test
    @DisplayName("task-failed（キー不正など）は 4xx 相当として理由を返す")
    void taskFailureBecomesFatalError() {
        String failed = "{\"header\":{\"event\":\"task-failed\",\"error_code\":\"InvalidApiKey\","
                + "\"error_message\":\"Invalid API-key provided.\"},\"payload\":{\"output\":{}}}";
        ClassroomSttAlibabaClient client = new ClassroomSttAlibabaClient(
                new DashScopeAsrClient((uri, apiKey, listener, timeout) ->
                        new FakeWebSocket(listener, List.of(STARTED, failed))));

        ClassroomSttClient.SttResponse response = client.transcribe(request("audio/L16", "ja-JP"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("HTTP_4XX");
        assertThat(response.isFatal()).isTrue();
        assertThat(response.errorMessage()).contains("Invalid API-key provided.");
    }

    @Test
    @DisplayName("接続できないときは TIMEOUT / CONNECT_FAILED として返す（再試行の余地を残す）")
    void connectFailureIsNotFatal() {
        ClassroomSttAlibabaClient timeout = new ClassroomSttAlibabaClient(
                new DashScopeAsrClient((uri, apiKey, listener, t) -> {
                    throw new java.util.concurrent.TimeoutException("connect timed out");
                }));
        ClassroomSttClient.SttResponse response = timeout.transcribe(request("audio/L16", "ja-JP"));

        assertThat(response.isSuccess()).isFalse();
        assertThat(response.errorCode()).isEqualTo("TIMEOUT");
        assertThat(response.isFatal()).isFalse();
    }

    @Test
    @DisplayName("WAV で来たときは wav として送る（旧クライアントの互換）")
    void acceptsWavContainer() {
        assertThat(ClassroomSttAlibabaClient.formatOf("audio/wav")).isEqualTo("wav");
        assertThat(ClassroomSttAlibabaClient.formatOf("audio/L16;rate=16000;channels=1")).isEqualTo("pcm");
        assertThat(ClassroomSttAlibabaClient.formatOf("audio/mp4")).isNull();
    }
}

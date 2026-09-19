package com.study21.common.core.stt;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * 阿里巴巴 DashScope の**リアルタイム音声認識**（Paraformer-Realtime-V2）を 1 回呼ぶクライアント。
 *
 * <p>DashScope のリアルタイム認識は HTTP ではなく **WebSocket**（`wss://…/api-ws/v1/inference`）で、
 * 1 つの発話を次の順で送る:</p>
 * <ol>
 *   <li>接続（ヘッダ {@code Authorization: bearer <API Key>}）</li>
 *   <li>{@code run-task} … モデル・音声形式・サンプリング周波数を伝える（JSON テキスト）</li>
 *   <li>サーバーの {@code task-started} を待つ</li>
 *   <li>音声（バイナリフレーム。分割して送る）</li>
 *   <li>{@code finish-task} … 終わりを伝える</li>
 *   <li>{@code result-generated}（認識結果）と {@code task-finished} / {@code task-failed} を待つ</li>
 * </ol>
 *
 * <p>授業録音は「分塊 1 つ = 1 回の認識」なので、分塊ごとにこの 1 往復を行う（分塊は 20 秒程度なので
 * リアルタイムの待ちは発生しない）。</p>
 *
 * <p>通信は JDK の {@link java.net.http.WebSocket} を使う（追加の依存を入れない）。テストは
 * {@link Connector} に偽の実装を差し込んで、送るフレームと受けるイベントだけを確かめる。</p>
 */
public class DashScopeAsrClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 音声を送る 1 フレームの大きさ（バイト）。小さすぎると往復が増え、大きすぎると待たされる。 */
    private static final int AUDIO_FRAME_BYTES = 3200;

    /** 接続そのもののタイムアウト（秒）。認識の待ち時間は呼び出し側が決める。 */
    private static final int CONNECT_TIMEOUT_SECONDS = 10;

    /** 1 回の認識要求。 */
    public record Request(
            /** WebSocket の URL（`wss://dashscope.aliyuncs.com/api-ws/v1/inference`）。 */
            String url,
            /** DashScope の API Key（千問と同じキーを使える）。 */
            String apiKey,
            /** モデル（`paraformer-realtime-v2`）。 */
            String model,
            /** 音声の形式（`pcm` = ヘッダ無しの 16bit PCM / `wav` = WAV コンテナ）。 */
            String format,
            /** サンプリング周波数（`16000`）。 */
            int sampleRate,
            /** 言語のヒント（`ja` / `zh` / `en` など。null なら送らない＝自動）。 */
            String languageHint,
            /** 音声そのもの。 */
            byte[] audio,
            /** `finish-task` を送ってから結果を待つ最大秒数。 */
            int timeoutSeconds) {
    }

    /** 1 回の応答。`ok` が false なら理由が `errorMessage`（日本語）に入る。 */
    public record Result(boolean ok, String text, String errorCode, String errorMessage) {

        public static Result success(String text) {
            return new Result(true, text == null ? "" : text, null, null);
        }

        public static Result failure(String code, String message) {
            return new Result(false, "", code, message);
        }
    }

    /** ストリーミングの途中経過（{@link Stream#poll()}）。 */
    public record Snapshot(List<String> finished, String interim, String failure) {
    }

    /**
     * 認識された 1 文（**時刻つき**）。
     *
     * <p>時刻は DashScope の `begin_time` / `end_time`（ミリ秒）で、**そのセッションへ送った
     * 音声の先頭からの位置**。授業全体の時間軸へは、サーバーが「そのセッションを始めるまでに
     * 送ったサンプル数」を足して換算する（HTTP の往復時間や結果の到着順は使わない）。</p>
     */
    public record TimedSentence(int id, String text, long beginMs, long endMs) {
    }

    /** ストリーミング用の状態（確定した文・途中の文・失敗の理由）。 */
    public record TimedSnapshot(List<TimedSentence> finished, TimedSentence interim, String failure) {
    }

    /** WebSocket を作る接縫（テストは偽の実装を差し込む）。 */
    @FunctionalInterface
    public interface Connector {
        WebSocket connect(URI uri, String apiKey, WebSocket.Listener listener, Duration timeout) throws Exception;
    }

    private final Connector connector;

    /** 本番用（JDK の HttpClient で接続する）。 */
    public DashScopeAsrClient() {
        this(DashScopeAsrClient::connectWithJdk);
    }

    public DashScopeAsrClient(Connector connector) {
        this.connector = connector;
    }

    /** 1 回の書き起こし（同期。呼び出し側のスレッドで待つ）。 */
    public Result transcribe(Request request) {
        if (request.url() == null || request.url().isBlank()) {
            return Result.failure("INVALID_REQUEST", "音声認識の URL が設定されていません。");
        }
        if (request.apiKey() == null || request.apiKey().isBlank()) {
            return Result.failure("INVALID_REQUEST", "音声認識の API Key が設定されていません。");
        }
        URI uri;
        try {
            uri = URI.create(request.url().trim());
        } catch (IllegalArgumentException cause) {
            return Result.failure("INVALID_REQUEST", "音声認識の URL が正しくありません（" + request.url() + "）。");
        }
        int timeoutSeconds = Math.max(5, request.timeoutSeconds());
        String taskId = UUID.randomUUID().toString().replace("-", "");
        Collector collector = new Collector();
        WebSocket socket = null;
        try {
            socket = connector.connect(uri, request.apiKey(), collector,
                    Duration.ofSeconds(Math.min(timeoutSeconds, CONNECT_TIMEOUT_SECONDS + 5)));
            socket.sendText(runTaskJson(request, taskId), true).join();
            if (!collector.awaitStarted(timeoutSeconds)) {
                return collector.failed() != null
                        ? Result.failure("TASK_FAILED", collector.failed())
                        : Result.failure("TIMEOUT", timeoutMessage(timeoutSeconds));
            }
            if (collector.failed() != null) {
                return Result.failure("TASK_FAILED", collector.failed());
            }
            byte[] audio = request.audio() == null ? new byte[0] : request.audio();
            for (int offset = 0; offset < audio.length; offset += AUDIO_FRAME_BYTES) {
                int length = Math.min(AUDIO_FRAME_BYTES, audio.length - offset);
                socket.sendBinary(ByteBuffer.wrap(audio, offset, length), true).join();
            }
            socket.sendText(finishTaskJson(taskId), true).join();
            if (!collector.awaitFinished(timeoutSeconds)) {
                return collector.failed() != null
                        ? Result.failure("TASK_FAILED", collector.failed())
                        : Result.failure("TIMEOUT", timeoutMessage(timeoutSeconds));
            }
            if (collector.failed() != null) {
                return Result.failure("TASK_FAILED", collector.failed());
            }
            return Result.success(collector.text());
        } catch (java.util.concurrent.TimeoutException cause) {
            return Result.failure("TIMEOUT", "音声認識に接続できませんでした（" + timeoutSeconds + " 秒）。");
        } catch (InterruptedException cause) {
            Thread.currentThread().interrupt();
            return Result.failure("INTERRUPTED", "音声認識の待ちが中断されました。");
        } catch (Exception cause) {
            return Result.failure("CONNECT_FAILED",
                    "音声認識に接続できませんでした（" + cause.getClass().getSimpleName()
                            + (cause.getMessage() == null ? "" : ": " + cause.getMessage()) + "）。");
        } finally {
            closeQuietly(socket);
        }
    }

    private static String timeoutMessage(int timeoutSeconds) {
        return "音声認識の応答が時間内に返りませんでした（" + timeoutSeconds + " 秒）。";
    }

    private static void closeQuietly(WebSocket socket) {
        if (socket == null) {
            return;
        }
        try {
            socket.sendClose(WebSocket.NORMAL_CLOSURE, "bye");
        } catch (RuntimeException ignored) {
            // すでに閉じている・送れないときは何もしない
        }
        try {
            socket.abort();
        } catch (RuntimeException ignored) {
            // 同上
        }
    }

    /** JDK の HttpClient で WebSocket を開く（本番の経路）。 */
    private static WebSocket connectWithJdk(URI uri, String apiKey, WebSocket.Listener listener,
                                            Duration timeout) throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .build();
        return client.newWebSocketBuilder()
                .header("Authorization", "bearer " + apiKey)
                .header("X-DashScope-DataInspection", "enable")
                .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                .buildAsync(uri, listener)
                .get(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }

    /**
     * **ストリーミングのセッション**を開く（1 授業 = 1 本）。
     *
     * <p>1 分塊ごとに 1 往復する `transcribe` と違い、**話しながら途中経過が取れる**。
     * 使い方は「{@link Stream#send(byte[])} で 16kHz PCM を小刻みに送る」→「{@link Stream#poll()} で
     * 途中の文と新しく確定した文を受け取る」→「最後に {@link Stream#close()}」。</p>
     *
     * <p>画面（ブラウザ）は音声を直接 DashScope へ送れない（API Key を見せられない）ので、
     * サーバーがこのセッションを持ち、画面は HTTP で PCM を送って結果を受け取る。</p>
     */
    public Stream openStream(Request request) {
        return new Stream(request);
    }

    /** ストリーミングの 1 セッション。 */
    public final class Stream implements AutoCloseable {

        private final Request request;
        private final Collector collector = new Collector();
        private final String taskId = UUID.randomUUID().toString().replace("-", "");
        private WebSocket socket;
        private String startError;
        private volatile boolean closed;
        /** `finish-task`（音声の終わり）を送ったか（二度送らない）。 */
        private boolean finishSent;
        /** `finish-task` のあと、認識の終わりを時間内に受け取れたか（`tailFinished()`）。 */
        private boolean tailFinished;

        private Stream(Request request) {
            this.request = request;
        }

        /**
         * 音声を送る（最初の呼び出しで接続し、`run-task` を送る）。
         *
         * <p>送れなかったときは理由を覚えておき、{@link #poll()} が返す（例外は投げない）。</p>
         */
        public synchronized void send(byte[] pcm) {
            if (closed) {
                return;
            }
            if (socket == null && startError == null) {
                start();
            }
            if (socket == null || pcm == null || pcm.length == 0) {
                return;
            }
            try {
                /*
                 * **1 フレームを大きくしない**。まとめて 1 フレームで送ると、音声が貯まったときに
                 * DashScope の上限を超えて接続が切られる（実測:
                 * `1009: Max frame length of 262144 has been exceeded` ＝ 16kHz 16bit で約 8 秒）。
                 * 分塊の区切り（100ms）に合わせて小分けに送る。
                 */
                for (int offset = 0; offset < pcm.length; offset += AUDIO_FRAME_BYTES) {
                    int length = Math.min(AUDIO_FRAME_BYTES, pcm.length - offset);
                    // **1 枚ずつ送り終わりを待つ**（`java.net.http` の WebSocket は
                    // 同時に 1 送信しか許さず、重ねると CompletionException で切れる。実測）
                    socket.sendBinary(ByteBuffer.wrap(pcm, offset, length), true).join();
                }
            } catch (RuntimeException cause) {
                Throwable root = cause.getCause() == null ? cause : cause.getCause();
                startError = "音声認識への送信に失敗しました（" + root.getClass().getSimpleName()
                        + (root.getMessage() == null ? "" : ": " + root.getMessage()) + "）。";
            }
        }

        /** いまの状態を**時刻つき**で取る（ストリーミングの保存・表示に使う）。 */
        public TimedSnapshot pollTimed(int drainedSentences) {
            TimedSnapshot snapshot = collector.drainTimed(drainedSentences);
            if (startError != null && snapshot.failure() == null) {
                return new TimedSnapshot(snapshot.finished(), snapshot.interim(), startError);
            }
            if (collector.failed() != null && snapshot.failure() == null) {
                return new TimedSnapshot(snapshot.finished(), snapshot.interim(), collector.failed());
            }
            return snapshot;
        }

        /** いままでに確定した文の数（次に取る位置を決めるのに使う）。 */
        public int timedCount() {
            return collector.timedCount();
        }

        /**
         * 終わりを伝えたあと、**認識の終わり（`task-finished`）を時間内に受け取れたか**。
         *
         * <p>尾部の確定文は停止のあとに届くので、これが false のときは「取り切れなかった」＝
         * 遅れて確定した文が残らない可能性がある（利用者に黙って成功と言わないための材料）。</p>
         */
        public boolean tailFinished() {
            return tailFinished;
        }

        /** いまの状態（前回以降に確定した文・途中の文・失敗の理由）。 */
        public Snapshot poll() {
            Snapshot snapshot = collector.drain();
            if (startError != null && snapshot.failure() == null) {
                return new Snapshot(snapshot.finished(), snapshot.interim(), startError);
            }
            if (collector.failed() != null && snapshot.failure() == null) {
                return new Snapshot(snapshot.finished(), snapshot.interim(), collector.failed());
            }
            return snapshot;
        }

        /** 接続して `run-task` を送り、`task-started` を待つ（短く）。 */
        private void start() {
            if (request.url() == null || request.url().isBlank()) {
                startError = "音声認識の URL が設定されていません。";
                return;
            }
            if (request.apiKey() == null || request.apiKey().isBlank()) {
                startError = "音声認識の API Key が設定されていません。";
                return;
            }
            URI uri;
            try {
                uri = URI.create(request.url().trim());
            } catch (IllegalArgumentException cause) {
                startError = "音声認識の URL が正しくありません（" + request.url() + "）。";
                return;
            }
            int timeoutSeconds = Math.max(5, request.timeoutSeconds());
            try {
                socket = connector.connect(uri, request.apiKey(), collector,
                        Duration.ofSeconds(Math.min(timeoutSeconds, CONNECT_TIMEOUT_SECONDS + 5)));
                socket.sendText(runTaskJson(request, taskId), true).join();
                if (!collector.awaitStarted(timeoutSeconds)) {
                    startError = collector.failed() != null ? collector.failed()
                            : "音声認識の開始が時間内に確認できませんでした。";
                }
            } catch (java.util.concurrent.TimeoutException cause) {
                startError = "音声認識に接続できませんでした（" + timeoutSeconds + " 秒）。";
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                startError = "音声認識の待ちが中断されました。";
            } catch (Exception cause) {
                startError = "音声認識に接続できませんでした（" + cause.getClass().getSimpleName()
                        + (cause.getMessage() == null ? "" : ": " + cause.getMessage()) + "）。";
            }
        }

        /**
         * **終わりを伝えるだけ**（`finish-task` を送る。**接続は閉じない**）。
         *
         * <p>尾部の確定文はこのあとに届くので、「送る」と「待つ」と「閉じる」を分けておくと、
         * 時間内に尾部を取り切れなかったときに**閉じずに持ち越して、あとからもう一度待てる**
         * （授業録音の収尾は、待てなかったことを理由にセッションを捨てない）。</p>
         *
         * <p>二度呼んでも `finish-task` は 1 回しか送らない（同じ要求を 2 回終わらせない）。</p>
         */
        public synchronized void requestFinish() {
            if (closed || finishSent || socket == null) {
                return;
            }
            finishSent = true;
            try {
                socket.sendText(finishTaskJson(taskId), true).join();
            } catch (RuntimeException ignored) {
                // すでに切れているときは何もしない（理由は collector / startError が持っている）
            }
        }

        /**
         * 認識の終わり（`task-finished`）を待つ。**尾部の確定文はこの待ちの間に届く**。
         *
         * <p>すでに受け取っていれば待たずに true。取れなかったときは false を返し、
         * {@link #tailFinished()} も false のままになる（＝遅れて確定した文が残らない可能性）。</p>
         */
        public boolean awaitTail(int maxSeconds) {
            try {
                boolean arrived = collector.awaitFinished(Math.min(5, Math.max(2, maxSeconds)));
                if (arrived) {
                    tailFinished = true;
                }
                return arrived;
            } catch (InterruptedException cause) {
                Thread.currentThread().interrupt();
                return false;
            }
        }

        /**
         * 終わりを伝えて閉じる（残りの確定文は閉じる前の {@link #poll()} で取る）。
         *
         * <p>まだ {@link #requestFinish()} を送っていなければ送り、尾部を**この呼び出しの中で**
         * 待ってから閉じる（今までの `close()` と同じ振る舞い）。</p>
         */
        @Override
        public synchronized void close() {
            if (closed) {
                return;
            }
            if (socket != null) {
                requestFinish();
                // 尾部の確定文はこの待ちの間に届く。取れなかったことを呼ぶ側が分かるように残す
                awaitTail(request.timeoutSeconds());
            }
            closed = true;
            closeQuietly(socket);
        }
    }

    /** `run-task`（認識の開始）の JSON。 */
    static String runTaskJson(Request request, String taskId) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", "run-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");

        ObjectNode payload = root.putObject("payload");
        payload.put("task_group", "audio");
        payload.put("task", "asr");
        payload.put("function", "recognition");
        payload.put("model", request.model() == null || request.model().isBlank()
                ? "paraformer-realtime-v2" : request.model().trim());

        ObjectNode parameters = payload.putObject("parameters");
        parameters.put("format", request.format() == null || request.format().isBlank()
                ? "pcm" : request.format().trim());
        // 0 以下は「宣言しない」（mp3・wav などはファイル自身の周波数に任せる）
        if (request.sampleRate() > 0) {
            parameters.put("sample_rate", request.sampleRate());
        }
        if (request.languageHint() != null && !request.languageHint().isBlank()) {
            ArrayNode hints = parameters.putArray("language_hints");
            hints.add(request.languageHint().trim());
        }
        payload.putObject("input");
        return root.toString();
    }

    /** `finish-task`（音声の終わり）の JSON。 */
    static String finishTaskJson(String taskId) {
        ObjectNode root = MAPPER.createObjectNode();
        ObjectNode header = root.putObject("header");
        header.put("action", "finish-task");
        header.put("task_id", taskId);
        header.put("streaming", "duplex");
        root.putObject("payload").putObject("input");
        return root.toString();
    }

    /**
     * サーバーからのイベントを集める。
     *
     * <p>DashScope は同じ発話について **途中経過を何度も返し、後のイベントほど長い文**になる。
     * そのため「文が確定したもの（sentence_end=true）」を順に積み、まだ確定していない文は
     * 上書きしながら持ち、最後に「確定した文＋未確定の文」を書き起こしにする。</p>
     */
    static final class Collector implements WebSocket.Listener {

        private final StringBuilder partialFrame = new StringBuilder();
        private final List<String> sentences = new ArrayList<>();
        private String pending = "";
        /** 時刻つきの確定文・途中の文（ストリーミングの画面表示と保存に使う）。 */
        private final List<TimedSentence> timedSentences = new ArrayList<>();
        private TimedSentence timedPending;
        private String failure;
        private final CountDownLatch started = new CountDownLatch(1);
        private final CountDownLatch finished = new CountDownLatch(1);

        /** まだ取り出していない「確定した文」の位置（ストリーミングで使う）。 */
        private int drained = 0;

        String text() {
            String tail = pending == null ? "" : pending.trim();
            String head = String.join("", sentences).trim();
            if (head.isEmpty()) {
                return tail;
            }
            return tail.isEmpty() ? head : head + tail;
        }

        /**
         * いまの状態を取り出す（ストリーミング用）。
         *
         * <p>`finished` は**前回の呼び出し以降に確定した文**（画面が 1 度だけ足せるように）、
         * `interim` はまだ確定していない途中の文（同じ文が伸びていくので画面は上書きする）。</p>
         */
        /**
         * いまの状態を**時刻つき**で取り出す（ストリーミング用）。
         *
         * <p>`finished` は前回の呼び出し以降に確定した文、`interim` はまだ確定していない文。</p>
         */
        synchronized TimedSnapshot drainTimed(int drainedSentences) {
            List<TimedSentence> newly = drainedSentences >= timedSentences.size()
                    ? List.of()
                    : new ArrayList<>(timedSentences.subList(drainedSentences, timedSentences.size()));
            return new TimedSnapshot(newly, timedPending, failure);
        }

        /** 確定した文の数（どこまで取り出したかを画面／サーバーが覚えておく）。 */
        synchronized int timedCount() {
            return timedSentences.size();
        }

        synchronized Snapshot drain() {
            List<String> newly = new ArrayList<>(sentences.subList(Math.min(drained, sentences.size()),
                    sentences.size()));
            drained = sentences.size();
            return new Snapshot(newly, pending == null ? "" : pending.trim(), failure);
        }

        String failed() {
            return failure;
        }

        boolean awaitStarted(int timeoutSeconds) throws InterruptedException {
            return started.await(timeoutSeconds, TimeUnit.SECONDS);
        }

        boolean awaitFinished(int timeoutSeconds) throws InterruptedException {
            return finished.await(timeoutSeconds, TimeUnit.SECONDS);
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            partialFrame.append(data);
            if (last) {
                handle(partialFrame.toString());
                partialFrame.setLength(0);
            }
            webSocket.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            // 理由が分かる形で閉じられたときは、それを失敗の理由にする（キー不正など）
            if (statusCode != WebSocket.NORMAL_CLOSURE && failure == null) {
                failure = "音声認識との接続が切れました（" + statusCode
                        + (reason == null || reason.isBlank() ? "" : ": " + reason) + "）。";
            }
            started.countDown();
            finished.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            if (failure == null) {
                failure = "音声認識との通信でエラーになりました（" + error.getClass().getSimpleName() + "）。";
            }
            started.countDown();
            finished.countDown();
        }

        private void handle(String json) {
            JsonNode root;
            try {
                root = MAPPER.readTree(json);
            } catch (Exception cause) {
                return;
            }
            JsonNode header = root.path("header");
            String event = header.path("event").asText("");
            switch (event) {
                case "task-started" -> started.countDown();
                case "result-generated" -> acceptSentence(root.path("payload").path("output").path("sentence"));
                case "task-finished" -> finished.countDown();
                case "task-failed" -> {
                    String code = header.path("error_code").asText("");
                    String message = header.path("error_message").asText("");
                    String output = root.path("payload").path("output").path("message").asText("");
                    String detail = !message.isBlank() ? message : output;
                    failure = "音声認識が失敗しました"
                            + (code.isBlank() ? "" : "（" + code + "）")
                            + (detail.isBlank() ? "。" : ": " + detail + "。");
                    started.countDown();
                    finished.countDown();
                }
                default -> {
                    // 知らないイベント（heartbeat など）は無視する
                }
            }
        }

        private void acceptSentence(JsonNode sentence) {
            if (sentence == null || sentence.isMissingNode()) {
                return;
            }
            String value = sentence.path("text").asText("");
            pending = value;
            // 時刻つき（sentence_id はセッション内で 1 から。begin_time / end_time はミリ秒）
            int id = sentence.path("sentence_id").asInt(0);
            long beginMs = sentence.path("begin_time").asLong(0);
            JsonNode endNode = sentence.path("end_time");
            long endMs = endNode.isNumber() ? endNode.asLong() : beginMs;
            boolean end = sentence.path("sentence_end").asBoolean(false);
            timedPending = new TimedSentence(id, value, beginMs, endMs);
            if (end) {
                sentences.add(value);
                timedSentences.add(timedPending);
                pending = "";
                timedPending = null;
            }
        }
    }
}

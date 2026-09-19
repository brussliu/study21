package com.study21.user.classroom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.study21.user.security.UserPrincipal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.AbstractWebSocketHandler;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 書き起こし音声の常時接続（`/api/user/classroom/{recordId}/stt/socket?source=mic|shared`）。
 *
 * <p><b>やり取り</b>（画面 ↔ このハンドラ）:</p>
 * <ul>
 *   <li>画面 → サーバー: **バイナリ**＝100ms ぶんの 16kHz モノラル 16bit PCM（ヘッダ無し）の前に、
 *       **見出しのテキスト** `{"type":"frame","no":N,"startSample":S,"sampleRate":16000,"source":"mic"}`
 *       を 1 枚送る（`no` ＝ その音源で 1 から数えた番号、`startSample` ＝ 録音の先頭からの絶対位置）。
 *       接続の最初に `{"type":"resume","from":N}` を送ると、その番号から数え直す。**知らない種類の
 *       テキストは黙って無視する**（見出し以外を足しても音は流れる）。</li>
 *   <li>サーバー → 画面: `{"type":"result","interim":"…","added":[…],"error":null,"processedFrames":N}`
 *       （`processedFrames` が**受け取り確認**。画面はここまで送れていれば捨ててよい）。</li>
 *   <li>画面 → サーバー: `{"type":"finish"}` で終わり。サーバーは最後の確定文を返して閉じる
 *       （`{"type":"finished","added":[…]}`）。</li>
 * </ul>
 *
 * <p><b>番号と位置は画面が送る**識別**を使う</b>（到着順に採番しない）: 張り直しのあとに同じ音が
 * 届いても、後端が同じフレームとして扱えるので二重に認識しない。見出しが無いフレーム
 * （古い画面）は今までどおり到着順に採番し、位置はサービス側が送ったサンプル数から積む。</p>
 *
 * <p>音源ごとに別の接続・別の認識セッション。片方が切れても、もう片方は続く。</p>
 *
 * <p><b>入口の確認は HTTP と同じ</b>: 接続のたび・収尾のたびに
 * {@link ClassroomService#requireSttAudioAccountId}（収尾は {@link ClassroomService#requireSttFinishAccountId}）
 * を通す。常時接続は**接続時にしか認証しない**ので、接続後に録音が終わった記録へ音が流れ続ける
 * （＝HTTP 側は 409 で止まるのに常時接続だけ素通りする）ことを防ぐ。</p>
 *
 * <p><b>ただし音声フレームごとには引かない</b>: 画面は 100ms ごとにフレームを送るので、そのたびに
 * 主キー参照をすると 1 音源あたり毎秒 10 回の DB 参照と SQL ログになる。接続のときに通った確認は
 * {@value #AUTHORIZE_WINDOW_MILLIS}ms のあいだ使い回し、窓が切れたら引き直す（＝音声が続いている
 * あいだは 5 秒に 1 回）。収尾（`finish`）は窓の内側でも**必ず**引き直す。</p>
 */
@Component
public class ClassroomSttSocketHandler extends AbstractWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(ClassroomSttSocketHandler.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 受け付けられない接続を断るときの理由（閉じるフレームに載るので短くする）。 */
    private static final String REFUSED_REASON = "この授業記録の書き起こしは受け付けられません";

    /**
     * 入口の確認を使い回す窓（ミリ秒）。
     *
     * <p>窓を切るときは音声が続いていても必ず引き直すので、途中で終わった／消えた記録へ流れる音は
     * 最悪でもこの窓のぶん（約 5 秒）で止まる。その音は保存側が記録の不在で弾く。</p>
     */
    static final long AUTHORIZE_WINDOW_MILLIS = 5_000L;

    /** 接続ごとの「次に受け取るフレーム番号」（`resume` で指定できる。見出しが無いフレーム用）。 */
    private final Map<String, Integer> nextFrameBySession = new ConcurrentHashMap<>();
    /** 接続ごとの見出し（`{"type":"frame",…}`。次のバイナリ 1 枚ぶんの識別）。 */
    private final Map<String, FrameHeader> headerBySession = new ConcurrentHashMap<>();
    /** 接続ごとの確認の控え（セッション ID → 確認を通った所有者と、次に確かめる時刻）。 */
    private final Map<String, Authorized> authorizedBySession = new ConcurrentHashMap<>();
    /** 入口の確認で断った接続（閉じたあとに届くフレームで、もう一度 DB を引かない・閉じ直さない）。 */
    private final Set<String> refusedSessions = ConcurrentHashMap.newKeySet();

    private final ClassroomSttStreamService streamService;
    /** 所有者と録音の状態の確認（HTTP の STT 入口と同じ実装を再利用する）。 */
    private final ClassroomService classroomService;

    public ClassroomSttSocketHandler(ClassroomSttStreamService streamService, ClassroomService classroomService) {
        this.streamService = streamService;
        this.classroomService = classroomService;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        ClassroomSttSocketHandler.Target target = target(session);
        UserPrincipal principal = principal(session);
        if (target == null || principal == null) {
            session.close(CloseStatus.BAD_DATA.withReason("接続先が正しくありません"));
            return;
        }
        // 未ログインはハンドシェイクで弾いているが、ここでも確かめる。そのうえで所有者と状態を見る
        // （ここで通れば、その結果は窓のあいだ使い回せる）
        if (authorizedAccountId(session, target, principal) == null) {
            return;
        }
        nextFrameBySession.put(session.getId(), 1);
        log.info("classroom stt socket opened. recordId={} source={}", target.recordId(), target.source());
        send(session, "{\"type\":\"ready\",\"source\":\"" + target.source() + "\"}");
    }

    @Override
    protected void handleBinaryMessage(WebSocketSession session, BinaryMessage message) throws Exception {
        if (refusedSessions.contains(session.getId())) {
            return;
        }
        Target target = target(session);
        UserPrincipal principal = principal(session);
        if (target == null || principal == null) {
            session.close(CloseStatus.BAD_DATA.withReason("接続先が正しくありません"));
            return;
        }
        // 窓ごとに見る。見ないと、録音が終わったあとに届いた音まで認識へ流れて、
        // 確定済みの書き起こしの後ろに文が足されてしまう。ただし 100ms ごとに届く音のたびに
        // 主キー参照はしない（接続のときに通った確認を窓のあいだ使い回す）
        Long accountId = authorizedAccountId(session, target, principal);
        if (accountId == null) {
            return;
        }
        int frameNo = nextFrameBySession.merge(session.getId(), 1, Integer::sum) - 1;
        long startSample = ClassroomSttStreamService.UNKNOWN_SAMPLE;
        // 直前の見出し（`{"type":"frame",…}`）があれば、その番号と絶対位置を使う（1 枚ぶんだけ）
        FrameHeader header = headerBySession.remove(session.getId());
        if (header != null) {
            if (header.frameNo() > 0) {
                frameNo = header.frameNo();
                // 見出しの無いフレームが続いても番号が飛ばないように、次の番号を合わせておく
                nextFrameBySession.put(session.getId(), frameNo + 1);
            }
            startSample = header.startSample();
        }
        ByteBuffer payload = message.getPayload();
        byte[] pcm = new byte[payload.remaining()];
        payload.get(pcm);

        ClassroomSttStreamService.StreamPush push =
                streamService.push(target.recordId(), accountId, target.source(), pcm, frameNo, startSample);
        sendResult(session, push);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        if (refusedSessions.contains(session.getId())) {
            return;
        }
        JsonNode root = MAPPER.readTree(message.getPayload());
        String type = root.path("type").asText("");
        if ("frame".equals(type)) {
            /*
             * **見出し**（バイナリ 1 枚の識別）: 番号と絶対位置を控えておき、次のバイナリで使う。
             * 番号が無い見出しは到着順（今までどおり）に任せる。位置が無い見出しは「分からない」
             * として渡し、サービス側が送ったサンプル数から積む。
             */
            int no = root.path("no").asInt(0);
            JsonNode startSample = root.path("startSample");
            headerBySession.put(session.getId(), new FrameHeader(no,
                    startSample.isNumber() ? startSample.asLong() : ClassroomSttStreamService.UNKNOWN_SAMPLE));
            return;
        }
        if ("resume".equals(type)) {
            // 断線して張り直した: 最後に確認できた番号の続きから数え直す
            int from = root.path("from").asInt(1);
            nextFrameBySession.put(session.getId(), Math.max(1, from));
            // 後端が再起動していても**音声の位置（＝発話キー）が変わらない**ように音声クロックを合わせる
            Target target = target(session);
            if (target != null) {
                streamService.resume(target.recordId(), target.source(), from);
            }
            log.info("classroom stt socket resumed. session={} from={}", session.getId(), from);
            return;
        }
        if ("finish".equals(type)) {
            Target target = target(session);
            UserPrincipal principal = principal(session);
            if (target == null || principal == null) {
                session.close(CloseStatus.BAD_DATA.withReason("接続先が正しくありません"));
                return;
            }
            // 収尾も入口で確かめる（画面が待たずに送っても、確定した記録を書き換えない）
            Long accountId = authorize(session, target, principal, true);
            if (accountId == null) {
                return;
            }
            ClassroomSttStreamService.StreamPush push =
                    streamService.finish(target.recordId(), accountId, target.source());
            ObjectNode body = MAPPER.createObjectNode();
            body.put("type", "finished");
            body.set("added", MAPPER.valueToTree(push.added()));
            body.put("error", push.error());
            // 収尾の状態（段階・やり直しの仕方・件数・失敗ではない知らせ）。欄は増えるだけで、
            // 画面が見ている `type` / `added` / `error` の契約は変えない
            putFinalizeState(body, push);
            send(session, body.toString());
            session.close(CloseStatus.NORMAL);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        nextFrameBySession.remove(session.getId());
        headerBySession.remove(session.getId());
        authorizedBySession.remove(session.getId());
        refusedSessions.remove(session.getId());
        // 認識セッションは閉じない（同じ録音の続きを、張り直した接続で送れるようにする）
        log.info("classroom stt socket closed. session={} status={}", session.getId(), status.getCode());
    }

    /* ---------------- 内部 ---------------- */

    /**
     * 音声フレームの入口の確認。直前に通っていれば**その結果を使い回す**（窓の内側）。
     *
     * <p>100ms ごとに届くフレームのたびに DB を引くと、1 音源あたり毎秒 10 回の主キー参照と
     * SQL ログになる。窓（{@value #AUTHORIZE_WINDOW_MILLIS}ms）が切れたら必ず引き直すので、
     * 途中で終わった／消えた記録の音は最悪でも窓 1 つぶんで止まる。</p>
     *
     * @return 所有者のアカウント ID（断ったときは null）
     */
    private Long authorizedAccountId(WebSocketSession session, Target target, UserPrincipal principal)
            throws Exception {
        long now = nowMillis();
        Authorized cached = authorizedBySession.get(session.getId());
        if (cached != null && cached.isFreshAt(now)) {
            return cached.accountId();
        }
        Long accountId = authorize(session, target, principal, false);
        if (accountId == null) {
            // 断った接続の控えは残さない（窓の内側でも使い回さない）
            authorizedBySession.remove(session.getId());
            return null;
        }
        authorizedBySession.put(session.getId(), new Authorized(accountId, now + AUTHORIZE_WINDOW_MILLIS));
        return accountId;
    }

    /** いまの時刻（ミリ秒）。確認の窓を測るのに使い、テストでは時計を進められるようにしておく。 */
    long nowMillis() {
        return System.currentTimeMillis();
    }

    /**
     * 送ってよい接続かを確かめる（**HTTP の STT 入口と同じ確認**＝所有者と録音の状態）。
     *
     * <p>断るときは理由をログに残し、画面へは短い理由つきで閉じる（収尾のときは `finished` に
     * 理由を載せて返す。画面は `finished` を待っているので、黙って閉じると成功に見える）。</p>
     *
     * @return 所有者のアカウント ID（断ったときは null）
     */
    private Long authorize(WebSocketSession session, Target target, UserPrincipal principal, boolean finishing)
            throws Exception {
        try {
            return finishing
                    ? classroomService.requireSttFinishAccountId(principal, target.recordId())
                    : classroomService.requireSttAudioAccountId(principal, target.recordId());
        } catch (RuntimeException refused) {
            String reason = refused.getMessage() == null || refused.getMessage().isBlank()
                    ? "この授業記録の書き起こしは受け付けられません。" : refused.getMessage();
            log.warn("classroom stt socket refused. recordId={} source={} finishing={} account={} reason={}",
                    target.recordId(), target.source(), finishing, principal.accountId(), reason);
            refusedSessions.add(session.getId());
            if (finishing) {
                // 画面は `finished` を待っている。理由つきで返してから閉じる（黙って成功にしない）
                ObjectNode body = MAPPER.createObjectNode();
                body.put("type", "finished");
                body.set("added", MAPPER.createArrayNode());
                body.put("error", reason);
                send(session, body.toString());
            }
            session.close(CloseStatus.POLICY_VIOLATION.withReason(REFUSED_REASON));
            return null;
        }
    }

    private void sendResult(WebSocketSession session, ClassroomSttStreamService.StreamPush push) throws Exception {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("type", "result");
        body.put("interim", push.interim());
        body.set("added", MAPPER.valueToTree(push.added()));
        body.put("error", push.error());
        body.put("processedFrames", push.processedFrames());
        // 収尾の状態も載せる（画面は「まだ収尾が済んでいない」ことを随時知れる）
        putFinalizeState(body, push);
        send(session, body.toString());
    }

    /**
     * 収尾の状態をメッセージへ載せる（`finish` の戻りと 1 回ごとの結果で同じ欄）。
     *
     * <p>**欄を増やすだけ**にする（`type` / `added` / `error` の契約は変えない）ので、
     * 古い画面はそのまま動く。`notice` は失敗ではない知らせ（音声なし・発話なしの終端など）。</p>
     */
    private static void putFinalizeState(ObjectNode body, ClassroomSttStreamService.StreamPush push) {
        body.put("finalizeStatus", push.finalizeStatus());
        body.put("finalizeCompleted", push.finalizeCompleted());
        body.put("recovery", push.recovery());
        body.put("savedCount", push.savedCount());
        body.put("pendingCount", push.pendingCount());
        body.put("notice", push.notice());
    }

    private void send(WebSocketSession session, String json) throws Exception {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(json));
        }
    }

    private UserPrincipal principal(WebSocketSession session) {
        Object value = session.getAttributes().get(ClassroomSttSocketConfig.PRINCIPAL_ATTR);
        return value instanceof UserPrincipal principal ? principal : null;
    }

    /** 接続先（`/api/user/classroom/{id}/stt/socket?source=…`）から記録 ID と音源を読む。 */
    Target target(WebSocketSession session) {
        URI uri = session.getUri();
        if (uri == null) {
            return null;
        }
        String path = uri.getPath() == null ? "" : uri.getPath();
        int socket = path.indexOf("/stt/socket");
        if (socket < 0) {
            return null;
        }
        String head = path.substring(0, socket);
        int slash = head.lastIndexOf('/');
        if (slash < 0) {
            return null;
        }
        long recordId;
        try {
            recordId = Long.parseLong(head.substring(slash + 1));
        } catch (NumberFormatException cause) {
            return null;
        }
        String source = ClassroomSttStreamService.SOURCE_MIC;
        String query = uri.getQuery();
        if (query != null) {
            for (String part : query.split("&")) {
                if (part.startsWith("source=")) {
                    source = ClassroomSttStreamService.normalizeSource(part.substring("source=".length()));
                }
            }
        }
        return new Target(recordId, source);
    }

    /** 接続先（記録 ID と音源）。 */
    record Target(long recordId, String source) {
    }

    /**
     * 見出しのフレーム（次のバイナリ 1 枚の識別）。
     *
     * @param frameNo     その音源で数えた番号（0 以下＝番号が無い見出し）
     * @param startSample 録音の先頭からの絶対位置（16kHz のサンプル数。無ければ
     *                    {@link ClassroomSttStreamService#UNKNOWN_SAMPLE}）
     */
    private record FrameHeader(int frameNo, long startSample) {
    }

    /** 入口の確認を通った接続の控え（確認を通った所有者と、次に確かめる時刻）。 */
    private record Authorized(long accountId, long expiresAt) {

        /** まだ窓の内側か（＝引き直さなくてよいか）。 */
        boolean isFreshAt(long nowMillis) {
            return nowMillis < expiresAt;
        }
    }
}

package com.study21.user.classroom;

import com.study21.common.core.exception.ConflictException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.socket.BinaryMessage;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;

import java.net.URI;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 書き起こし音声の常時接続（WebSocket）の検証。
 *
 * <p>見張るのは「接続先の読み取り」「フレーム番号と受け取り確認」「断線後の番号の続き」
 * 「終了の合図」、そして**入口の確認**（所有者と録音の状態。HTTP の STT 入口と同じ確認を
 * 使う＝他人の授業や終わった授業へ音を流し込めない）の 5 つ（実際のソケットは使わない）。</p>
 *
 * <p>入口の確認は**音声フレームごとには引かない**（100ms ごとに来るので DB と SQL ログが
 * 毎秒 10 回になってしまう）。接続のときに通った確認を約 5 秒の窓のあいだ使い回し、窓が切れたら
 * 引き直す。収尾は窓の内側でも必ず引き直す。そこの見張りが
 * {@link #checksRecordOncePerWindow()}・{@link #checksRecordAgainAfterWindow()}・
 * {@link #checksFinishEvenInsideWindow()}・{@link #forgetsCheckWhenSessionCloses()}。</p>
 */
class ClassroomSttSocketHandlerTest {

    private static final long RECORD_ID = 12L;
    private static final long ACCOUNT_ID = 2L;

    /** 送ったメッセージを覚える**モックの**セッション（実ソケットは使わない）。 */
    static final class Sent {
        final List<String> messages = new ArrayList<>();
    }

    private static WebSocketSession session(String id, String path, UserPrincipal principalUser, Sent sent)
            throws Exception {
        WebSocketSession session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn(id);
        when(session.getUri()).thenReturn(URI.create("ws://127.0.0.1:8083" + path));
        Map<String, Object> attributes = new HashMap<>();
        if (principalUser != null) {
            attributes.put(ClassroomSttSocketConfig.PRINCIPAL_ATTR, principalUser);
        }
        when(session.getAttributes()).thenReturn(attributes);
        when(session.isOpen()).thenReturn(true);
        org.mockito.Mockito.doAnswer(invocation -> {
            sent.messages.add(((TextMessage) invocation.getArgument(0)).getPayload());
            return null;
        }).when(session).sendMessage(any(WebSocketMessage.class));
        return session;
    }

    private static UserPrincipal principal() {
        return new UserPrincipal(ACCOUNT_ID, "student@example.com", "生徒", AccountType.STUDENT);
    }

    /**
     * 入口の確認（所有者・状態）を通す**偽の**サービス。
     *
     * <p>本物は記録を読んで 404 / 403 / 409 を投げる。ここでは「通った」ことにして、
     * ハンドラが返ってきた所有者 ID を使うかどうかだけを見る。</p>
     */
    private static ClassroomService allowed() {
        ClassroomService classroom = mock(ClassroomService.class);
        when(classroom.requireSttAudioAccountId(any(UserPrincipal.class), anyLong())).thenReturn(ACCOUNT_ID);
        when(classroom.requireSttFinishAccountId(any(UserPrincipal.class), anyLong())).thenReturn(ACCOUNT_ID);
        return classroom;
    }

    /**
     * 入口の確認の窓を跨がせるために、時計を進められる**テスト用の**ハンドラ。
     *
     * <p>本物は {@link System#currentTimeMillis()} で測るので、テストから窓の経過を作れない。
     * そこだけ差し替える（実装は時間に依存したままにする）。</p>
     */
    private static final class ClockedHandler extends ClassroomSttSocketHandler {
        long now = 1_700_000_000_000L;

        ClockedHandler(ClassroomSttStreamService streamService, ClassroomService classroomService) {
            super(streamService, classroomService);
        }

        @Override
        long nowMillis() {
            return now;
        }
    }

    /** 受け取り確認の番号をそのまま返す**偽の**ストリーミングサービス。 */
    private static ClassroomSttStreamService acking() {
        ClassroomSttStreamService service = mock(ClassroomSttStreamService.class);
        when(service.push(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong()))
                .thenAnswer(invocation -> new ClassroomSttStreamService.StreamPush("うう", List.of(), null,
                        invocation.getArgument(4)));
        return service;
    }

    @Test
    @DisplayName("接続先から記録 ID と音源を読む（知らない音源はマイク扱い・壊れた URL は受け付けない）")
    void readsTargetFromUri() throws Exception {
        ClassroomSttSocketHandler handler =
                new ClassroomSttSocketHandler(mock(ClassroomSttStreamService.class), allowed());

        Sent sent = new Sent();
        assertThat(handler.target(session("a", "/api/user/classroom/12/stt/socket?source=shared", principal(), sent)))
                .isEqualTo(new ClassroomSttSocketHandler.Target(RECORD_ID, "shared"));
        assertThat(handler.target(session("b", "/api/user/classroom/12/stt/socket", principal(), sent)))
                .isEqualTo(new ClassroomSttSocketHandler.Target(RECORD_ID, "mic"));
        assertThat(handler.target(session("c", "/api/user/classroom/12/stt/socket?source=なにか", principal(), sent)))
                .isEqualTo(new ClassroomSttSocketHandler.Target(RECORD_ID, "mic"));
        assertThat(handler.target(session("d", "/api/user/classroom/abc/stt/socket", principal(), sent))).isNull();
    }

    @Test
    @DisplayName("バイナリのフレームは番号つきで認識へ渡し、受け取り確認を返す")
    void passesFramesWithNumbersAndAcks() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, allowed());
        Sent sent = new Sent();
        WebSocketSession session = session("s1", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        byte[] pcm = new byte[3_200];
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(pcm)));
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(pcm)));

        // 見出しが無いときは 1 から順に番号を振る（到着順）。位置は分からないので渡さない
        verify(service).push(eq(RECORD_ID), eq(ACCOUNT_ID), eq("mic"), any(), eq(1),
                eq(ClassroomSttStreamService.UNKNOWN_SAMPLE));
        verify(service).push(eq(RECORD_ID), eq(ACCOUNT_ID), eq("mic"), any(), eq(2),
                eq(ClassroomSttStreamService.UNKNOWN_SAMPLE));
        // 受け取り確認（processedFrames）を返している
        assertThat(sent.messages.get(0)).contains("\"type\":\"ready\"");
        assertThat(sent.messages.get(1)).contains("\"processedFrames\":1");
        assertThat(sent.messages.get(2)).contains("\"processedFrames\":2");
    }

    /**
     * 画面はバイナリの前に**見出しのテキスト**（`{"type":"frame","no":…,"startSample":…}`）を送る。
     * 番号はそのまま使い、絶対位置もそのまま渡す（到着順に採番しない＝張り直し・後端の再起動でも
     * 同じ音が同じ識別になる）。
     */
    @Test
    @DisplayName("見出しのフレームから番号と絶対位置を受け取る（到着順に採番しない）")
    void usesFrameIdentityFromHeader() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, allowed());
        Sent sent = new Sent();
        WebSocketSession session = session("s14", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"type\":\"frame\",\"no\":7,\"startSample\":96000,"
                + "\"sampleRate\":16000,\"source\":\"mic\"}"));
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));

        verify(service).push(eq(RECORD_ID), eq(ACCOUNT_ID), eq("mic"), any(), eq(7), eq(96_000L));

        // 見出しが無いフレームは、見出しの続きの番号で到着順に送る（互換）
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));
        verify(service).push(eq(RECORD_ID), eq(ACCOUNT_ID), eq("mic"), any(), eq(8),
                eq(ClassroomSttStreamService.UNKNOWN_SAMPLE));
        assertThat(sent.messages.get(1)).contains("\"processedFrames\":7");
    }

    /**
     * 知らない種類のテキストは**黙って無視する**（今までどおり）。見出しに番号が入っていなければ
     * 到着順に採番する（番号だけ欠けた見出しで音を落とさない）。
     */
    @Test
    @DisplayName("知らない種類のテキストは無視する（番号の無い見出しは到着順）")
    void ignoresUnknownTextTypes() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, allowed());
        Sent sent = new Sent();
        WebSocketSession session = session("s15", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"type\":\"heartbeat\"}"));
        handler.handleMessage(session, new TextMessage("{\"type\":\"frame\"}"));
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));

        verify(service, times(0)).finish(anyLong(), anyLong(), anyString());
        verify(session, times(0)).close(any(CloseStatus.class));
        verify(service).push(eq(RECORD_ID), eq(ACCOUNT_ID), eq("mic"), any(), eq(1),
                eq(ClassroomSttStreamService.UNKNOWN_SAMPLE));
    }

    @Test
    @DisplayName("断線して張り直したら、指定した番号から数え直す（重複を送り直しても二重に認識しない）")
    void resumesFromRequestedFrame() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, allowed());
        Sent sent = new Sent();
        WebSocketSession session = session("s2", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"type\":\"resume\",\"from\":9}"));
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));

        verify(service).push(eq(RECORD_ID), eq(ACCOUNT_ID), eq("mic"), any(), eq(9),
                eq(ClassroomSttStreamService.UNKNOWN_SAMPLE));
    }

    @Test
    @DisplayName("終了の合図で音源の書き起こしを閉じ、最後の確定文を返す")
    void finishesOnRequest() throws Exception {
        ClassroomSttStreamService service = mock(ClassroomSttStreamService.class);
        when(service.finish(anyLong(), anyLong(), anyString()))
                .thenReturn(new ClassroomSttStreamService.StreamPush("", List.of(), null, 5));
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, allowed());
        Sent sent = new Sent();
        WebSocketSession session = session("s3", "/api/user/classroom/12/stt/socket?source=shared", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"type\":\"finish\"}"));

        verify(service).finish(RECORD_ID, ACCOUNT_ID, "shared");
        assertThat(sent.messages.get(1)).contains("\"type\":\"finished\"");
        assertThat(sent.messages.get(1)).contains("\"error\":null");
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("未ログインの接続は受け付けない（利用者が入っていないセッション）")
    void rejectsAnonymousSession() throws Exception {
        ClassroomSttStreamService service = mock(ClassroomSttStreamService.class);
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, allowed());
        Sent sent = new Sent();
        WebSocketSession session = session("s4", "/api/user/classroom/12/stt/socket", null, sent);

        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));

        verify(service, times(0)).push(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong());
        verify(session).close(any(CloseStatus.class));
    }

    /* ---------------- 入口の確認（所有者・状態。HTTP の STT 入口と同じ） ---------------- */

    @Test
    @DisplayName("他人の授業記録へは音を送れない（認識へ渡さずに閉じる）")
    void refusesOthersClassroom() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomService classroom = mock(ClassroomService.class);
        when(classroom.requireSttAudioAccountId(any(UserPrincipal.class), anyLong()))
                .thenThrow(ClassroomApiException.forbidden("この録音は自分が作成したものではありません。"));
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, classroom);
        Sent sent = new Sent();
        WebSocketSession session = session("s5", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));

        verify(service, times(0)).push(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong());
        verify(session).close(any(CloseStatus.class));
        // 画面へ「準備できた」と嘘を言わない
        assertThat(sent.messages).noneMatch(message -> message.contains("\"type\":\"ready\""));
    }

    @Test
    @DisplayName("録音が終わった記録へは音を送れない（状態を見て断る）")
    void refusesRecordThatNoLongerAcceptsAudio() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomService classroom = mock(ClassroomService.class);
        when(classroom.requireSttAudioAccountId(any(UserPrincipal.class), anyLong()))
                .thenThrow(new ConflictException("この録音は停止（まとめ作成待ち）のため、書き起こしの音声を受け取れません。"));
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, classroom);
        Sent sent = new Sent();
        WebSocketSession session = session("s6", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));

        verify(service, times(0)).push(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong());
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("存在しない授業記録へは音を送れない（見つからないときも断る）")
    void refusesMissingRecord() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomService classroom = mock(ClassroomService.class);
        when(classroom.requireSttAudioAccountId(any(UserPrincipal.class), anyLong()))
                .thenThrow(ClassroomApiException.notFound("授業記録が見つかりません。"));
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, classroom);
        Sent sent = new Sent();
        WebSocketSession session = session("s7", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));

        verify(service, times(0)).push(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong());
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("音声が続いても入口の確認は窓ごとに 1 回だけ（フレームごとに DB を引かない）")
    void checksRecordOncePerWindow() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomService classroom = allowed();
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, classroom);
        Sent sent = new Sent();
        WebSocketSession session = session("s8", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        // 100ms のフレームが 2 秒ぶん届く（窓の内側）
        for (int frame = 0; frame < 20; frame++) {
            handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));
        }

        // 接続のときの 1 回だけ。窓の間は使い回す（DB も SQL ログも 20 回引かない）
        verify(classroom, times(1)).requireSttAudioAccountId(any(UserPrincipal.class), eq(RECORD_ID));
        // 音そのものは全部認識へ渡している（間引かない）
        verify(service, times(20)).push(eq(RECORD_ID), eq(ACCOUNT_ID), eq("mic"), any(), anyInt(), anyLong());
        assertThat(sent.messages.get(sent.messages.size() - 1)).contains("\"processedFrames\":20");
    }

    @Test
    @DisplayName("確認の窓を過ぎたら、音声が続いていてももう一度確かめる")
    void checksRecordAgainAfterWindow() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomService classroom = allowed();
        ClockedHandler handler = new ClockedHandler(service, classroom);
        Sent sent = new Sent();
        WebSocketSession session = session("s11", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));
        handler.now += 5_000L;   // 窓（5 秒）を跨ぐ
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));
        handler.now += 1_000L;   // そのあとは窓の内側
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));

        // 接続のとき＋窓が切れたとき
        verify(classroom, times(2)).requireSttAudioAccountId(any(UserPrincipal.class), eq(RECORD_ID));
    }

    @Test
    @DisplayName("収尾は窓の内側でも必ず確かめる（終わった記録を書き換えない）")
    void checksFinishEvenInsideWindow() throws Exception {
        ClassroomSttStreamService service = mock(ClassroomSttStreamService.class);
        when(service.push(anyLong(), anyLong(), anyString(), any(), anyInt(), anyLong()))
                .thenAnswer(invocation -> new ClassroomSttStreamService.StreamPush("うう", List.of(), null,
                        invocation.getArgument(4)));
        when(service.finish(anyLong(), anyLong(), anyString()))
                .thenReturn(new ClassroomSttStreamService.StreamPush("", List.of(), null, 2));
        ClassroomService classroom = allowed();
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, classroom);
        Sent sent = new Sent();
        WebSocketSession session = session("s12", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));
        handler.handleMessage(session, new TextMessage("{\"type\":\"finish\"}"));

        // 音声は窓で 1 回だけでも、収尾は必ず引き直す（窓の内側でも）
        verify(classroom, times(1)).requireSttAudioAccountId(any(UserPrincipal.class), eq(RECORD_ID));
        verify(classroom, times(1)).requireSttFinishAccountId(any(UserPrincipal.class), eq(RECORD_ID));
        verify(service).finish(RECORD_ID, ACCOUNT_ID, "mic");
    }

    @Test
    @DisplayName("閉じた接続の確認は残さない（張り直したらもう一度確かめる）")
    void forgetsCheckWhenSessionCloses() throws Exception {
        ClassroomSttStreamService service = acking();
        ClassroomService classroom = allowed();
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, classroom);
        Sent sent = new Sent();
        WebSocketSession session = session("s13", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));
        verify(classroom, times(1)).requireSttAudioAccountId(any(UserPrincipal.class), eq(RECORD_ID));

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);
        handler.handleMessage(session, new BinaryMessage(ByteBuffer.wrap(new byte[3_200])));

        // 閉じたあとに同じセッション ID で届いても、控えを使い回さず引き直す
        verify(classroom, times(2)).requireSttAudioAccountId(any(UserPrincipal.class), eq(RECORD_ID));
    }

    @Test
    @DisplayName("終了できない状態の記録では収尾せず、理由を返す（黙って成功にしない）")
    void refusesFinishWhenRecordIsNotFinalizable() throws Exception {
        ClassroomSttStreamService service = mock(ClassroomSttStreamService.class);
        ClassroomService classroom = mock(ClassroomService.class);
        when(classroom.requireSttAudioAccountId(any(UserPrincipal.class), anyLong())).thenReturn(ACCOUNT_ID);
        when(classroom.requireSttFinishAccountId(any(UserPrincipal.class), anyLong()))
                .thenThrow(new ConflictException("この録音は完了のため、書き起こしの終了を受け取れません。"));
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, classroom);
        Sent sent = new Sent();
        WebSocketSession session = session("s9", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.afterConnectionEstablished(session);
        handler.handleMessage(session, new TextMessage("{\"type\":\"finish\"}"));

        verify(service, times(0)).finish(anyLong(), anyLong(), anyString());
        // 画面は `finished` 待ちなので、理由つきで返してから閉じる
        assertThat(sent.messages.get(1)).contains("\"type\":\"finished\"");
        assertThat(sent.messages.get(1)).contains("完了");
        verify(session).close(any(CloseStatus.class));
    }

    @Test
    @DisplayName("収尾は所有者のアカウント ID で行う（確認を通った本人の行として保存する）")
    void finishUsesCheckedOwner() throws Exception {
        ClassroomSttStreamService service = mock(ClassroomSttStreamService.class);
        when(service.finish(anyLong(), anyLong(), anyString()))
                .thenReturn(new ClassroomSttStreamService.StreamPush("", List.of(), null, 3));
        ClassroomService classroom = mock(ClassroomService.class);
        when(classroom.requireSttFinishAccountId(any(UserPrincipal.class), anyLong())).thenReturn(77L);
        ClassroomSttSocketHandler handler = new ClassroomSttSocketHandler(service, classroom);
        Sent sent = new Sent();
        WebSocketSession session = session("s10", "/api/user/classroom/12/stt/socket?source=mic", principal(), sent);

        handler.handleMessage(session, new TextMessage("{\"type\":\"finish\"}"));

        verify(service).finish(RECORD_ID, 77L, "mic");
    }
}

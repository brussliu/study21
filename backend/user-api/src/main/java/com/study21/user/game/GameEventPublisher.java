package com.study21.user.game;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 対戦のリアルタイム通知（SSE）。
 *
 * <p>アカウントID → 接続中の {@link SseEmitter} をメモリに持ち、対戦の変化を push する。
 * **DB が唯一の正**で、ここは「変わったよ」と知らせるだけ。受け取った画面は必ず
 * `GET /api/user/games/matches/{id}` で読み直す（切断・再読み込みでも壊れない）。</p>
 *
 * <p>5 秒ごとに心拍（ping）を送る。途中のプロキシに切られないようにする役目と、
 * 画面を閉じた接続を早く見つける（相手が下线したことを早く出す）役目を兼ねる。
 * user-api は単一インスタンスの前提（複数にするときは PostgreSQL の LISTEN/NOTIFY に置き換える）。</p>
 */
@Component
public class GameEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(GameEventPublisher.class);
    /**
     * 心拍（ping）の間隔。
     * 画面を閉じた接続は「書き込めなくなったとき」に外れるため、この間隔が
     * 「相手が下线したことに気づくまでの遅れ」になる（対局中の相手の状態表示に使う）。
     */
    private static final long HEARTBEAT_SECONDS = 5;
    /** 接続を保持する上限時間（無限に張らせない。切れたら画面が繋ぎ直す）。 */
    private static final long TIMEOUT_MILLIS = TimeUnit.HOURS.toMillis(2);

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "game-sse-heartbeat");
        thread.setDaemon(true);
        return thread;
    });

    public GameEventPublisher() {
        this.heartbeat.scheduleAtFixedRate(this::sendHeartbeat, HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
    }

    /** 画面からの接続を受け付ける。 */
    public SseEmitter subscribe(long accountId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitters.computeIfAbsent(accountId, key -> new CopyOnWriteArrayList<>()).add(emitter);
        emitter.onCompletion(() -> remove(accountId, emitter));
        emitter.onTimeout(() -> remove(accountId, emitter));
        emitter.onError(error -> remove(accountId, emitter));
        try {
            emitter.send(SseEmitter.event().name("connected").data(Map.of("accountId", accountId)));
        } catch (IOException e) {
            remove(accountId, emitter);
        }
        return emitter;
    }

    /** 1 人に送る（接続していなければ何もしない）。 */
    public void publish(long accountId, String eventName, Map<String, Object> data) {
        List<SseEmitter> targets = emitters.get(accountId);
        if (targets == null || targets.isEmpty()) {
            return;
        }
        Map<String, Object> payload = new LinkedHashMap<>(data);
        for (SseEmitter emitter : targets) {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(payload));
            } catch (IOException | IllegalStateException e) {
                // 送れなくなった接続は外す（画面側は繋ぎ直して GET で読み直す）
                log.debug("SSE 送信に失敗したため接続を外します。accountId={} event={}", accountId, eventName);
                remove(accountId, emitter);
            }
        }
    }

    /** 対戦の両者に送る。 */
    public void publishToBoth(long challengerAccountId, long opponentAccountId, String eventName,
                              Map<String, Object> data) {
        publish(challengerAccountId, eventName, data);
        publish(opponentAccountId, eventName, data);
    }

    /**
     * 1 人に送る（**トランザクションのコミット後**）。
     *
     * <p>通知を受けた画面は「変わった」を知らせとして受け取り、必ず GET で読み直す。
     * そのため、書き込みが確定する前に送ると、読み直しが古い内容を返して
     * 画面が更新されない（申し込みの通知が届かない等）。ここでコミット後まで遅らせる。</p>
     */
    public void publishAfterCommit(long accountId, String eventName, Map<String, Object> data) {
        afterCommit(() -> publish(accountId, eventName, data));
    }

    /** 対戦の両者に送る（コミット後）。 */
    public void publishToBothAfterCommit(long challengerAccountId, long opponentAccountId, String eventName,
                                         Map<String, Object> data) {
        afterCommit(() -> publishToBoth(challengerAccountId, opponentAccountId, eventName, data));
    }

    /** トランザクションの中なら確定後に、外なら今すぐ実行する。 */
    private static void afterCommit(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
            return;
        }
        action.run();
    }

    /**
     * いま接続しているか（＝ゲーム画面を開いているか）。
     *
     * <p>対戦の申し込みは「相手が応戦ダイアログを見られる状態か」を先に確かめるために使う。
     * 接続は画面を開いている間だけなので、切断（タブを閉じた・遷移した）は
     * {@code onCompletion} などで外れ、ここは false になる。</p>
     */
    public boolean isOnline(long accountId) {
        List<SseEmitter> targets = emitters.get(accountId);
        return targets != null && !targets.isEmpty();
    }

    /** 心拍（ping）。切れた接続はここでも掃除する。 */
    private void sendHeartbeat() {
        for (Map.Entry<Long, List<SseEmitter>> entry : emitters.entrySet()) {
            for (SseEmitter emitter : entry.getValue()) {
                try {
                    emitter.send(SseEmitter.event().name("ping").data(Map.of("at", System.currentTimeMillis())));
                } catch (IOException | IllegalStateException e) {
                    remove(entry.getKey(), emitter);
                }
            }
        }
    }

    private void remove(long accountId, SseEmitter emitter) {
        List<SseEmitter> targets = emitters.get(accountId);
        if (targets == null) {
            return;
        }
        targets.remove(emitter);
        if (targets.isEmpty()) {
            emitters.remove(accountId);
        }
    }

    /** テスト用: 接続を全部切る（画面を閉じた状態にする）。 */
    void disconnectAll(long accountId) {
        List<SseEmitter> targets = emitters.get(accountId);
        if (targets == null) {
            return;
        }
        for (SseEmitter emitter : List.copyOf(targets)) {
            try {
                emitter.complete();
            } catch (RuntimeException ignored) {
                // すでに終わっている接続は無視する
            }
            remove(accountId, emitter);
        }
    }

    /** テスト用: 接続数。 */
    int connectionCount(long accountId) {
        List<SseEmitter> targets = emitters.get(accountId);
        return targets == null ? 0 : targets.size();
    }
}

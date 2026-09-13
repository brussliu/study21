package com.study21.user.game;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 対戦のリアルタイム通知（SSE）とオンライン判定。
 *
 * <p>「相手がオンライン」は**ゲーム画面を開いている（接続が生きている）**ことで判定する。
 * 申し込み・応戦ダイアログはゲーム画面でしか出せないため、この判定と一致する。</p>
 */
class GameEventPublisherTest {

    private final GameEventPublisher publisher = new GameEventPublisher();

    @Test
    void isOnlineFollowsTheOpenConnections() {
        // 画面を開いていない＝オフライン
        assertThat(publisher.isOnline(1L)).isFalse();

        SseEmitter emitter = publisher.subscribe(1L);
        assertThat(publisher.isOnline(1L)).isTrue();
        assertThat(publisher.connectionCount(1L)).isEqualTo(1);

        // 別の人の接続は影響しない
        assertThat(publisher.isOnline(2L)).isFalse();

        // 画面を閉じた（接続が終わった）らオフラインへ戻る。
        // 実機では接続が切れたときの onCompletion / onError で外れるが、
        // ここでは送れなくなった接続が publish で外れることも確かめる。
        emitter.complete();
        publisher.publish(1L, "ping", Map.of());
        assertThat(publisher.isOnline(1L)).isFalse();
    }

    @Test
    void publishAfterCommitWaitsUntilTheTransactionCommits() {
        // 書き込みが確定する前に送ると、画面が GET で読み直しても新しい行が無い
        // （申し込みの通知が届かない）。コミット後まで遅らせることを確かめる。
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishAfterCommit(1L, "invited", Map.of("matchId", 1L));
            publisher.publishToBothAfterCommit(1L, 2L, "accepted", Map.of("matchId", 1L));

            // まだ送らず、コミット後の処理として積まれている
            assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(2);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void publishAfterCommitSendsImmediatelyOutsideATransaction() {
        // トランザクションの外（テストや管理ツール）ではその場で送る
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
        publisher.publishAfterCommit(1L, "invited", Map.of("matchId", 1L));
        assertThat(TransactionSynchronizationManager.isSynchronizationActive()).isFalse();
    }

    @Test
    void publishSkipsAccountsWithoutConnections() {
        // 接続が無い相手への送信は何も起きない（例外にしない）
        publisher.publish(9L, "invited", Map.of("matchId", 1L));
        assertThat(publisher.isOnline(9L)).isFalse();
    }
}

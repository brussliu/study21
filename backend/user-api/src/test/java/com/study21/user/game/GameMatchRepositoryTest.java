package com.study21.user.game;

import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 実 DB（PostgreSQL）に対するゲーム対戦の検証。
 *
 * <p>招待 → 承諾 → 数手 → 終了（投了）までを実際の SQL で通し、
 * `GAM_対戦情報` の盤面・手数と `GAM_対戦手情報` が食い違わないことを確かめる。
 * テストはロールバックするので DB は汚れない。</p>
 *
 * <p>実行には DB のパスワードが要る（無いときはスキップ）:
 * `STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test`</p>
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class GameMatchRepositoryTest {

    /** 2.0 から移行した実アカウント（1 = 保護者 bruss / 2 = 生徒 劉競澤）。 */
    private static final long GUARDIAN_ID = 1L;
    private static final long STUDENT_ID = 2L;

    @Autowired
    private GameMatchService gameMatchService;

    @Autowired
    private GameEventPublisher publisher;

    /**
     * その人がゲーム画面を開いている状態にする。
     * オンライン判定は SSE の接続で見るため、申し込みには相手の接続が要る。
     */
    private void online(long accountId) {
        publisher.subscribe(accountId);
    }

    private UserPrincipal guardian() {
        return new UserPrincipal(GUARDIAN_ID, "bruss.ji.liu@gmail.com", "Liu Bruss", AccountType.GUARDIAN);
    }

    private UserPrincipal student() {
        return new UserPrincipal(STUDENT_ID, "ricky.jingze@gmail.com", "劉 競澤", AccountType.STUDENT);
    }

    @Test
    void inviteAcceptMovesAndResignRoundTrip() {
        // 1. 招待（挑戦者＝保護者、相手＝生徒）
        online(STUDENT_ID);
        GameMatchModels.MatchDetail invited = gameMatchService.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, null));
        long matchId = invited.matchId();
        assertThat(invited.status()).isEqualTo("WAITING");
        assertThat(invited.challengerAccountId()).isEqualTo(GUARDIAN_ID);
        assertThat(invited.myStone()).isEqualTo("BLACK");
        assertThat(invited.moveCount()).isZero();
        assertThat(invited.board()).hasSize(15);
        assertThat(invited.moves()).isEmpty();

        // 生徒の一覧にも招待として見える
        assertThat(gameMatchService.listMatches(student()).items())
                .anySatisfy(row -> {
                    assertThat(row.matchId()).isEqualTo(matchId);
                    assertThat(row.status()).isEqualTo("WAITING");
                    assertThat(row.iAmChallenger()).isFalse();
                    assertThat(row.myTurn()).isFalse();          // まだ承諾前
                });

        // 2. 承諾（相手のみ）
        GameMatchModels.MatchDetail playing = gameMatchService.acceptMatch(student(), matchId,
                invited.version());
        assertThat(playing.status()).isEqualTo("PLAYING");
        assertThat(playing.turnAccountId()).isEqualTo(GUARDIAN_ID);   // 挑戦者（黒）が先手

        // 3. 数手（保護者 → 生徒 → 保護者）
        GameMatchModels.MatchDetail afterBlack = gameMatchService.move(guardian(), matchId,
                new GameMatchModels.MoveRequest(7, 7, playing.version()));
        assertThat(afterBlack.moveCount()).isEqualTo(1);
        assertThat(afterBlack.board().get(7).get(7)).isEqualTo("BLACK");
        assertThat(afterBlack.myTurn()).isFalse();

        GameMatchModels.MatchDetail afterWhite = gameMatchService.move(student(), matchId,
                new GameMatchModels.MoveRequest(7, 8, afterBlack.version()));
        assertThat(afterWhite.moveCount()).isEqualTo(2);
        assertThat(afterWhite.board().get(7).get(8)).isEqualTo("WHITE");
        assertThat(afterWhite.moves()).hasSize(2);
        assertThat(afterWhite.moves().get(0).stone()).isEqualTo("BLACK");
        assertThat(afterWhite.moves().get(0).row()).isEqualTo(7);
        assertThat(afterWhite.moves().get(1).accountId()).isEqualTo(STUDENT_ID);

        // 4. 投了（保護者）→ 勝者は生徒
        GameMatchModels.MatchDetail finished = gameMatchService.resignMatch(guardian(), matchId,
                afterWhite.version());
        assertThat(finished.status()).isEqualTo("FINISHED");
        assertThat(finished.resultCode()).isEqualTo("RESIGN");
        assertThat(finished.winnerAccountId()).isEqualTo(STUDENT_ID);
        assertThat(finished.finishedAt()).isNotNull();
        // 投了は指し手を増やさない
        assertThat(finished.moveCount()).isEqualTo(2);
        assertThat(finished.moves()).hasSize(2);
    }

    @Test
    void reversiMatchKeepsTheBoardAndMovesInSync() {
        online(GUARDIAN_ID);
        GameMatchModels.MatchDetail invited = gameMatchService.createMatch(student(),
                new GameMatchModels.CreateRequest("REVERSI", GUARDIAN_ID, null));
        long matchId = invited.matchId();
        assertThat(invited.board()).hasSize(8);

        GameMatchModels.MatchDetail playing = gameMatchService.acceptMatch(guardian(), matchId, invited.version());
        // 初期盤面は中央 4 マス（黒 2・白 2）
        assertThat(count(playing.board(), "BLACK")).isEqualTo(2);
        assertThat(count(playing.board(), "WHITE")).isEqualTo(2);

        // 生徒（黒）が (2,3) に置くと (3,3) の白が返る
        GameMatchModels.MatchDetail moved = gameMatchService.move(student(), matchId,
                new GameMatchModels.MoveRequest(2, 3, playing.version()));
        assertThat(moved.moveCount()).isEqualTo(1);
        assertThat(count(moved.board(), "BLACK")).isEqualTo(4);
        assertThat(count(moved.board(), "WHITE")).isEqualTo(1);
        assertThat(moved.moves().get(0).flipped()).hasSize(1);
        assertThat(moved.moves().get(0).flipped().get(0)).containsExactly(3, 3);

        // 盤面（対戦情報）と指し手（対戦手情報）の整合: 手数 = 行数
        assertThat(moved.moves()).hasSize(moved.moveCount());
    }

    @Test
    void declineAndCancelKeepTheMatchClosed() {
        // 断り（相手）
        online(STUDENT_ID);
        GameMatchModels.MatchDetail invited = gameMatchService.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, null));
        GameMatchModels.MatchDetail declined = gameMatchService.declineMatch(student(), invited.matchId(),
                invited.version());
        assertThat(declined.status()).isEqualTo("DECLINED");
        assertThat(declined.moves()).isEmpty();

        // 取消（挑戦者）
        online(GUARDIAN_ID);
        GameMatchModels.MatchDetail invited2 = gameMatchService.createMatch(student(),
                new GameMatchModels.CreateRequest("GOMOKU", GUARDIAN_ID, null));
        GameMatchModels.MatchDetail cancelled = gameMatchService.cancelMatch(student(), invited2.matchId(),
                invited2.version());
        assertThat(cancelled.status()).isEqualTo("CANCELLED");
    }

    @Test
    void winningMoveFinishesTheMatchAndKeepsTheTurn() {
        // 五子棋を最後まで打ち切る（5 連で終了）。
        // 終了時に 手番アカウントID を NULL にすると CK_ゲーム対戦_手番 に違反して
        // 更新が失敗する（本番で「最後の一手」だけがエラーになった不具合の再発防止）。
        online(STUDENT_ID);
        GameMatchModels.MatchDetail invited = gameMatchService.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, null));
        GameMatchModels.MatchDetail playing = gameMatchService.acceptMatch(student(), invited.matchId(),
                invited.version());

        // 保護者（黒）が 0 行目に 5 連、生徒（白）は 1 行目に 4 つ置く
        GameMatchModels.MatchDetail current = playing;
        long matchId = invited.matchId();
        int[][] blackCells = {{0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}};
        int[][] whiteCells = {{1, 0}, {1, 1}, {1, 2}, {1, 3}};
        for (int i = 0; i < 4; i += 1) {
            current = gameMatchService.move(guardian(), matchId,
                    new GameMatchModels.MoveRequest(blackCells[i][0], blackCells[i][1], current.version()));
            current = gameMatchService.move(student(), matchId,
                    new GameMatchModels.MoveRequest(whiteCells[i][0], whiteCells[i][1], current.version()));
        }
        // 5 連目の黒（保護者）で終了する
        GameMatchModels.MatchDetail finished = gameMatchService.move(guardian(), matchId,
                new GameMatchModels.MoveRequest(blackCells[4][0], blackCells[4][1], current.version()));

        assertThat(finished.status()).isEqualTo("FINISHED");
        assertThat(finished.resultCode()).isEqualTo("WIN");
        assertThat(finished.winnerAccountId()).isEqualTo(GUARDIAN_ID);
        // 終了後も手番は NULL ではない（勝者を残す）
        assertThat(finished.turnAccountId()).isEqualTo(GUARDIAN_ID);
    }

    @Test
    void opponentOfflineLetsTheOtherSideEndTheMatch() {
        // 对局中对手的实时连接断开（= 关掉页面），留在场上的一侧可以点【终了】结束
        online(STUDENT_ID);
        GameMatchModels.MatchDetail invited = gameMatchService.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, null));
        long matchId = invited.matchId();
        GameMatchModels.MatchDetail playing = gameMatchService.acceptMatch(student(), matchId,
                invited.version());

        // 保护者的连接建立 → 学生から見て对手在线
        online(GUARDIAN_ID);
        assertThat(gameMatchService.getMatch(student(), matchId).opponentOnline()).isTrue();

        // 保护者断开（页面を閉じた）→ 学生から見て对手不在线
        publisher.disconnectAll(GUARDIAN_ID);
        assertThat(gameMatchService.getMatch(student(), matchId).opponentOnline()).isFalse();

        GameMatchModels.MatchDetail finished = gameMatchService.timeoutMatch(student(), matchId,
                playing.version());
        assertThat(finished.status()).isEqualTo("FINISHED");
        assertThat(finished.resultCode()).isEqualTo("TIMEOUT");
        assertThat(finished.winnerAccountId()).isEqualTo(STUDENT_ID);
        assertThat(finished.turnAccountId()).isEqualTo(STUDENT_ID);
    }

    @Test
    void challengerCanChooseToMoveSecond() {
        // 挑戦者（保護者）が後手（白）を選ぶと、承諾後の最初の手番は相手（生徒＝黒）になる
        online(STUDENT_ID);
        GameMatchModels.MatchDetail invited = gameMatchService.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, "WHITE"));
        assertThat(invited.myStone()).isEqualTo("WHITE");
        // 開始前でも手番は黒（先手）を持つ人＝生徒
        assertThat(invited.turnAccountId()).isEqualTo(STUDENT_ID);

        GameMatchModels.MatchDetail playing = gameMatchService.acceptMatch(student(), invited.matchId(),
                invited.version());
        assertThat(playing.status()).isEqualTo("PLAYING");
        assertThat(playing.turnAccountId()).isEqualTo(STUDENT_ID);   // 黒（先手）＝生徒
        assertThat(playing.myTurn()).isTrue();
    }

    @Test
    void inviteIsRejectedWhenTheOpponentIsNotOnline() {
        // 誰も接続していない＝2 人ともゲーム画面を閉じている

        assertThatThrownBy(() -> gameMatchService.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("相手がオンラインではありません");
    }

    @Test
    void listOpponentsReturnsTheFamilyPairAndOnlineState() {
        assertThat(gameMatchService.listOpponents(guardian()).items())
                .anySatisfy(opponent -> assertThat(opponent.accountId()).isEqualTo(STUDENT_ID));

        // ゲーム画面を開いている（接続中の）相手はオンラインとして返る。
        // オフラインの扱いは GameEventPublisherTest と GameMatchServiceImplTest で固定する。
        online(STUDENT_ID);

        assertThat(gameMatchService.listOpponents(guardian()).items())
                .anySatisfy(opponent -> assertThat(opponent.online()).isTrue());
        assertThat(gameMatchService.listOpponents(student()).items())
                .anySatisfy(opponent -> assertThat(opponent.accountId()).isEqualTo(GUARDIAN_ID));
    }

    private static long count(List<List<String>> board, String stone) {
        return board.stream().flatMap(List::stream).filter(stone::equals).count();
    }
}

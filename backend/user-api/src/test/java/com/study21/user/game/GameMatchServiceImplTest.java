package com.study21.user.game;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.study21.common.core.exception.ConflictException;
import com.study21.common.core.exception.NotFoundException;
import com.study21.common.core.exception.ValidationException;
import com.study21.user.account.AccountEntity;
import com.study21.user.account.AccountMapper;
import com.study21.user.account.AccountType;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ゲーム対戦の業務ルール。
 * 手番・権限（他人の対戦は 404）・楽観的ロック（409）・パス・投了を固定する。
 */
class GameMatchServiceImplTest {

    private static final long STUDENT_ID = 4L;
    private static final long GUARDIAN_ID = 3L;

    private GameMatchMapper mapper;
    private AccountMapper accountMapper;
    private GameEventPublisher publisher;
    private GameMatchServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(GameMatchMapper.class);
        accountMapper = mock(AccountMapper.class);
        publisher = mock(GameEventPublisher.class);
        service = new GameMatchServiceImpl(mapper, accountMapper, publisher, new ObjectMapper());
    }

    private UserPrincipal student() {
        return new UserPrincipal(STUDENT_ID, "student@example.com", "試験 生徒", AccountType.STUDENT);
    }

    private UserPrincipal guardian() {
        return new UserPrincipal(GUARDIAN_ID, "parent@example.com", "試験 保護者", AccountType.GUARDIAN);
    }

    private AccountEntity account(long id, String type, Long guardianId) {
        AccountEntity entity = new AccountEntity();
        entity.setAccountId(id);
        entity.setAccountType(type);
        entity.setLoginId("user" + id + "@example.com");
        entity.setSei("試験");
        entity.setMei("利用者" + id);
        entity.setGuardianId(guardianId);
        return entity;
    }

    private GameMatchEntity match(String status, long turnAccountId, int version) {
        GameMatchEntity entity = new GameMatchEntity();
        entity.setMatchId(100L);
        entity.setGameType("GOMOKU");
        entity.setChallengerAccountId(STUDENT_ID);
        entity.setOpponentAccountId(GUARDIAN_ID);
        entity.setTurnAccountId(turnAccountId);
        entity.setStatus(status);
        entity.setChallengerStone("BLACK");
        entity.setBoard(new GameBoardJson().emptyGomoku());
        entity.setMoveCount(0);
        entity.setVersion(version);
        entity.setUpdatedAt(new Timestamp(System.currentTimeMillis()));
        return entity;
    }

    /** テスト用の空盤面（JSON 文字列）。 */
    private static final class GameBoardJson {
        String emptyGomoku() {
            return new GameMatchServiceImplTest().json(GameBoard.initial("GOMOKU").toJson());
        }
    }

    private String json(Object value) {
        try {
            return new ObjectMapper().writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /* ---------- 相手と招待 ---------- */

    @Test
    void guardianCanInviteTheirStudent() {
        when(accountMapper.findById(GUARDIAN_ID)).thenReturn(account(GUARDIAN_ID, "GUARDIAN", null));
        when(accountMapper.findStudentByGuardianId(GUARDIAN_ID)).thenReturn(account(STUDENT_ID, "STUDENT", GUARDIAN_ID));
        when(accountMapper.findById(STUDENT_ID)).thenReturn(account(STUDENT_ID, "STUDENT", GUARDIAN_ID));

        assertThat(service.listOpponents(guardian()).items())
                .singleElement()
                .satisfies(opponent -> {
                    assertThat(opponent.accountId()).isEqualTo(STUDENT_ID);
                    assertThat(opponent.role()).isEqualTo("STUDENT");
                    assertThat(opponent.displayName()).contains("試験");
                });

        when(mapper.insertMatch(any(GameMatchEntity.class))).thenAnswer(invocation -> {
            GameMatchEntity inserted = invocation.getArgument(0);
            inserted.setMatchId(100L);
            return 1;
        });
        GameMatchEntity created = match("WAITING", GUARDIAN_ID, 1);
        created.setChallengerAccountId(GUARDIAN_ID);
        created.setOpponentAccountId(STUDENT_ID);
        when(mapper.findMatchById(eq(100L), anyLong())).thenReturn(created);
        when(mapper.listMoves(100L)).thenReturn(List.of());

        when(publisher.isOnline(STUDENT_ID)).thenReturn(true);

        GameMatchModels.MatchDetail detail = service.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, null));

        assertThat(detail.status()).isEqualTo("WAITING");
        assertThat(detail.challengerAccountId()).isEqualTo(GUARDIAN_ID);
        assertThat(detail.opponentAccountId()).isEqualTo(STUDENT_ID);
        assertThat(detail.myStone()).isEqualTo("BLACK");     // 挑戦者が先手
        // 通知はコミット後に送る（送った直後に画面が GET で読み直すため）
        verify(publisher).publishAfterCommit(eq(STUDENT_ID), eq("invited"), any());
    }

    @Test
    void challengerChoosesFirstOrSecondMove() {
        when(accountMapper.findById(GUARDIAN_ID)).thenReturn(account(GUARDIAN_ID, "GUARDIAN", null));
        when(accountMapper.findById(STUDENT_ID)).thenReturn(account(STUDENT_ID, "STUDENT", GUARDIAN_ID));
        when(accountMapper.findStudentByGuardianId(GUARDIAN_ID)).thenReturn(account(STUDENT_ID, "STUDENT", GUARDIAN_ID));
        when(publisher.isOnline(STUDENT_ID)).thenReturn(true);
        when(mapper.insertMatch(any(GameMatchEntity.class))).thenAnswer(invocation -> {
            GameMatchEntity inserted = invocation.getArgument(0);
            inserted.setMatchId(100L);
            return 1;
        });

        // 先手（黒）を選ぶと、手番は挑戦者（保護者）
        GameMatchEntity asBlack = match("WAITING", GUARDIAN_ID, 1);
        asBlack.setChallengerAccountId(GUARDIAN_ID);
        asBlack.setOpponentAccountId(STUDENT_ID);
        asBlack.setChallengerStone("BLACK");
        when(mapper.findMatchById(eq(100L), anyLong())).thenReturn(asBlack);
        when(mapper.listMoves(100L)).thenReturn(List.of());
        GameMatchModels.MatchDetail first = service.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, "BLACK"));
        assertThat(first.myStone()).isEqualTo("BLACK");
        assertThat(first.turnAccountId()).isEqualTo(GUARDIAN_ID);

        // 後手（白）を選ぶと、手番は相手（生徒＝黒）
        GameMatchEntity asWhite = match("WAITING", STUDENT_ID, 1);
        asWhite.setChallengerAccountId(GUARDIAN_ID);
        asWhite.setOpponentAccountId(STUDENT_ID);
        asWhite.setChallengerStone("WHITE");
        when(mapper.findMatchById(eq(100L), anyLong())).thenReturn(asWhite);
        GameMatchModels.MatchDetail second = service.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, "WHITE"));
        assertThat(second.myStone()).isEqualTo("WHITE");
        assertThat(second.turnAccountId()).isEqualTo(STUDENT_ID);
    }

    @Test
    void inviteIsRejectedWhenTheOpponentIsOffline() {
        when(accountMapper.findById(GUARDIAN_ID)).thenReturn(account(GUARDIAN_ID, "GUARDIAN", null));
        when(accountMapper.findById(STUDENT_ID)).thenReturn(account(STUDENT_ID, "STUDENT", GUARDIAN_ID));
        when(accountMapper.findStudentByGuardianId(GUARDIAN_ID)).thenReturn(account(STUDENT_ID, "STUDENT", GUARDIAN_ID));
        when(publisher.isOnline(STUDENT_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("相手がオンラインではありません");
        // 対戦は作らない
        verify(mapper, never()).insertMatch(any(GameMatchEntity.class));
    }

    @Test
    void inviteRejectsAnUnknownStoneChoice() {
        when(accountMapper.findById(GUARDIAN_ID)).thenReturn(account(GUARDIAN_ID, "GUARDIAN", null));
        when(accountMapper.findById(STUDENT_ID)).thenReturn(account(STUDENT_ID, "STUDENT", GUARDIAN_ID));
        when(accountMapper.findStudentByGuardianId(GUARDIAN_ID)).thenReturn(account(STUDENT_ID, "STUDENT", GUARDIAN_ID));
        when(publisher.isOnline(STUDENT_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.createMatch(guardian(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, "RED")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("先手・後手の指定が不正です");
    }

    @Test
    void studentCanInviteTheirGuardianAndOthersAreRejected() {
        when(accountMapper.findById(STUDENT_ID)).thenReturn(account(STUDENT_ID, "STUDENT", GUARDIAN_ID));
        when(accountMapper.findById(GUARDIAN_ID)).thenReturn(account(GUARDIAN_ID, "GUARDIAN", null));

        when(publisher.isOnline(GUARDIAN_ID)).thenReturn(true);
        assertThat(service.listOpponents(student()).items()).singleElement()
                .satisfies(opponent -> {
                    assertThat(opponent.role()).isEqualTo("GUARDIAN");
                    assertThat(opponent.online()).isTrue();
                });

        // 親子でない相手（存在するが対象外）は拒否
        when(accountMapper.findById(99L)).thenReturn(account(99L, "STUDENT", null));
        assertThatThrownBy(() -> service.createMatch(student(),
                new GameMatchModels.CreateRequest("GOMOKU", 99L, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("対戦できる相手ではありません");
        // 自分自身も拒否
        assertThatThrownBy(() -> service.createMatch(student(),
                new GameMatchModels.CreateRequest("GOMOKU", STUDENT_ID, null)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("自分とは対戦できません");
    }

    /* ---------- 権限 ---------- */

    @Test
    void unrelatedMatchLooksLikeNotFound() {
        // 自分の対戦ではない（挑戦者でも相手でもない）
        GameMatchEntity other = match("PLAYING", 1L, 1);
        other.setChallengerAccountId(1L);
        other.setOpponentAccountId(2L);
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(other);

        assertThatThrownBy(() -> service.getMatch(student(), 100L))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("対戦が見つかりません");
    }

    @Test
    void onlyTheOpponentCanAcceptOrDecline() {
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(match("WAITING", STUDENT_ID, 1));
        when(mapper.findMatchById(100L, GUARDIAN_ID)).thenReturn(match("WAITING", STUDENT_ID, 1));

        // 挑戦者（自分が招待した側）は承諾できない
        assertThatThrownBy(() -> service.acceptMatch(student(), 100L, 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("招待された本人だけ");
        // 相手は承諾できる
        when(mapper.acceptMatch(100L, GUARDIAN_ID, 1)).thenReturn(1);
        when(mapper.listMoves(100L)).thenReturn(List.of());
        // 承諾後は PLAYING の行が返る（findMatchById は上の thenReturn を置き換えないよう
        // この時点で呼ばれる = 同じスタブのままなので、状態はモックの値に従う）
        GameMatchModels.MatchDetail accepted = service.acceptMatch(guardian(), 100L, 1);
        assertThat(accepted.status()).isEqualTo("WAITING");   // モックの返り値のまま（更新は mapper の責務）
        verify(mapper).acceptMatch(100L, GUARDIAN_ID, 1);
    }

    /* ---------- 着手 ---------- */

    @Test
    void moveRejectsOutOfTurnAndStaleVersion() {
        when(mapper.findMatchById(100L, GUARDIAN_ID)).thenReturn(match("PLAYING", STUDENT_ID, 5));
        when(mapper.listMoves(100L)).thenReturn(List.of());

        // 手番ではない（手番は生徒）
        assertThatThrownBy(() -> service.move(guardian(), 100L, new GameMatchModels.MoveRequest(7, 7, 5)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("あなたの手番ではありません");
        verify(mapper, never()).updateAfterMove(anyLong(), anyString(), anyInt(), any(), anyString(), any(), any(),
                any(), any(), anyInt());

        // 手番の人が版を間違えると 409（更新件数 0）
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(match("PLAYING", STUDENT_ID, 5));
        when(mapper.updateAfterMove(anyLong(), anyString(), anyInt(), any(), anyString(), any(), any(), any(),
                any(), anyInt())).thenReturn(0);
        assertThatThrownBy(() -> service.move(student(), 100L, new GameMatchModels.MoveRequest(7, 7, 4)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("他の操作で先に更新されました");
    }

    @Test
    void moveAppliesTheRuleAndFinishesTheGame() {
        // 五子棋で 4 連を作った状態から 5 連目を指す
        GameBoard board = GameBoard.initial("GOMOKU");
        for (int col = 0; col < 4; col += 1) {
            GameRules.apply(board, "GOMOKU", "BLACK", 7, col);
        }
        GameMatchEntity playing = match("PLAYING", STUDENT_ID, 3);
        playing.setBoard(json(board.toJson()));
        playing.setMoveCount(4);
        GameMatchEntity finished = match("FINISHED", STUDENT_ID, 4);
        finished.setWinnerAccountId(STUDENT_ID);
        finished.setResultCode("WIN");
        finished.setFinishedAt(new Timestamp(System.currentTimeMillis()));
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(playing, finished);
        when(mapper.insertMove(any(GameMoveEntity.class))).thenReturn(1);
        // 手番アカウントID は勝者（NULL にすると CK_ゲーム対戦_手番 に違反する）
        when(mapper.updateAfterMove(eq(100L), anyString(), eq(5), eq(STUDENT_ID), eq("FINISHED"), eq(STUDENT_ID),
                eq("WIN"), any(), eq(STUDENT_ID), eq(3))).thenReturn(1);
        when(mapper.listMoves(100L)).thenReturn(List.of());

        GameMatchModels.MatchDetail detail = service.move(student(), 100L, new GameMatchModels.MoveRequest(7, 4, 3));

        assertThat(detail.status()).isEqualTo("FINISHED");
        assertThat(detail.winnerAccountId()).isEqualTo(STUDENT_ID);
        assertThat(detail.resultCode()).isEqualTo("WIN");
        // 指し手が 1 行だけ入る（パスは無し）
        verify(mapper).insertMove(any(GameMoveEntity.class));
        verify(publisher).publishToBothAfterCommit(eq(STUDENT_ID), eq(GUARDIAN_ID), eq("finished"), any());
    }

    /* ---------- 对手不在线时的结束（终了） ---------- */

    @Test
    void opponentOfflineEndsTheMatchWithTheCallerAsWinner() {
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(match("PLAYING", STUDENT_ID, 5));
        when(publisher.isOnline(GUARDIAN_ID)).thenReturn(false);
        GameMatchEntity finished = match("FINISHED", STUDENT_ID, 6);
        finished.setWinnerAccountId(STUDENT_ID);
        finished.setResultCode("TIMEOUT");
        finished.setFinishedAt(new Timestamp(System.currentTimeMillis()));
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(match("PLAYING", STUDENT_ID, 5), finished);
        when(mapper.timeoutMatch(eq(100L), eq(STUDENT_ID), eq(STUDENT_ID), eq(5))).thenReturn(1);
        when(mapper.listMoves(100L)).thenReturn(List.of());

        GameMatchModels.MatchDetail detail = service.timeoutMatch(student(), 100L, 5);

        assertThat(detail.status()).isEqualTo("FINISHED");
        assertThat(detail.resultCode()).isEqualTo("TIMEOUT");
        assertThat(detail.winnerAccountId()).isEqualTo(STUDENT_ID);
        verify(publisher).publishToBothAfterCommit(eq(STUDENT_ID), eq(GUARDIAN_ID), eq("finished"), any());
    }

    @Test
    void opponentStillOnlineCannotBeTimedOut() {
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(match("PLAYING", STUDENT_ID, 5));
        when(publisher.isOnline(GUARDIAN_ID)).thenReturn(true);

        assertThatThrownBy(() -> service.timeoutMatch(student(), 100L, 5))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("相手はまだオンラインです");
        verify(mapper, never()).timeoutMatch(anyLong(), anyLong(), anyLong(), anyInt());
    }

    @Test
    void timeoutIsRejectedWhenTheMatchIsNotPlaying() {
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(match("WAITING", STUDENT_ID, 1));

        assertThatThrownBy(() -> service.timeoutMatch(student(), 100L, 1))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("対戦中ではありません");
    }

    @Test
    void matchDetailReportsWhetherTheOpponentIsOnline() {
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(match("PLAYING", STUDENT_ID, 5));
        when(mapper.listMoves(100L)).thenReturn(List.of());
        when(publisher.isOnline(GUARDIAN_ID)).thenReturn(true);

        assertThat(service.getMatch(student(), 100L).opponentOnline()).isTrue();

        when(publisher.isOnline(GUARDIAN_ID)).thenReturn(false);
        assertThat(service.getMatch(student(), 100L).opponentOnline()).isFalse();
    }

    @Test
    void moveAddsAPassRowWhenTheOpponentCannotMove() {
        // 黑白棋: 白が置けないので、黒の手のあとにパスの行が入る
        GameBoard board = GameBoard.empty(8);
        board.set(0, 0, "BLACK");
        board.set(0, 1, "WHITE");
        board.set(0, 3, "BLACK");
        board.set(0, 4, "WHITE");

        GameMatchEntity playing = match("PLAYING", STUDENT_ID, 2);
        playing.setGameType("REVERSI");
        playing.setBoard(json(board.toJson()));
        playing.setMoveCount(4);
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(playing);
        when(mapper.insertMove(any(GameMoveEntity.class))).thenReturn(1);
        // 黒が 1 手打ち、パスの行が 1 行入るので 手数 は 6、手番は同じ黒のまま
        when(mapper.updateAfterMove(eq(100L), anyString(), eq(6), eq(STUDENT_ID), eq("PLAYING"), eq(null),
                eq(null), eq(null), eq(STUDENT_ID), eq(2))).thenReturn(1);
        when(mapper.listMoves(100L)).thenReturn(List.of());

        GameMatchModels.MatchDetail detail = service.move(student(), 100L, new GameMatchModels.MoveRequest(0, 2, 2));

        assertThat(detail.myTurn()).isTrue();      // パスのあとも自分の番
        // 1 手目は着手、2 行目は「相手のパス」
        org.mockito.ArgumentCaptor<GameMoveEntity> captor =
                org.mockito.ArgumentCaptor.forClass(GameMoveEntity.class);
        verify(mapper, org.mockito.Mockito.times(2)).insertMove(captor.capture());
        GameMoveEntity passRow = captor.getAllValues().get(1);
        assertThat(passRow.getPassFlag()).isEqualTo("1");
        assertThat(passRow.getAccountId()).isEqualTo(GUARDIAN_ID);   // 打てなかった側
        assertThat(passRow.getStone()).isEqualTo("WHITE");           // 相手の石
        assertThat(passRow.getRowIndex()).isNull();
        assertThat(passRow.getColIndex()).isNull();
    }

    @Test
    void moveRejectsIllegalCell() {
        GameMatchEntity playing = match("PLAYING", STUDENT_ID, 1);
        GameBoard board = GameBoard.initial("GOMOKU");
        board.set(7, 7, "BLACK");
        playing.setBoard(json(board.toJson()));
        when(mapper.findMatchById(100L, STUDENT_ID)).thenReturn(playing);

        assertThatThrownBy(() -> service.move(student(), 100L, new GameMatchModels.MoveRequest(7, 7, 1)))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("既に石があります");
        verify(mapper, never()).insertMove(any(GameMoveEntity.class));
    }

    /* ---------- 投了 ---------- */

    @Test
    void resignMakesTheOpponentTheWinner() {
        GameMatchEntity finished = match("FINISHED", GUARDIAN_ID, 5);
        finished.setWinnerAccountId(GUARDIAN_ID);
        finished.setResultCode("RESIGN");
        finished.setFinishedAt(new Timestamp(System.currentTimeMillis()));
        // 1 回目（確認）は対戦中、2 回目（結果の取得）は終了した行を返す
        when(mapper.findMatchById(100L, STUDENT_ID))
                .thenReturn(match("PLAYING", STUDENT_ID, 4), finished);
        when(mapper.resignMatch(100L, STUDENT_ID, GUARDIAN_ID, STUDENT_ID, 4)).thenReturn(1);
        when(mapper.listMoves(100L)).thenReturn(List.of());

        GameMatchModels.MatchDetail detail = service.resignMatch(student(), 100L, 4);

        assertThat(detail.status()).isEqualTo("FINISHED");
        assertThat(detail.winnerAccountId()).isEqualTo(GUARDIAN_ID);
        assertThat(detail.resultCode()).isEqualTo("RESIGN");
        verify(publisher).publishToBothAfterCommit(eq(STUDENT_ID), eq(GUARDIAN_ID), eq("finished"), any());
    }

    @Test
    void loginIsRequired() {
        assertThatThrownBy(() -> service.listMatches(null))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ログイン");
    }
}

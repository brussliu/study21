package com.study21.user.game;

import com.study21.common.core.exception.ValidationException;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 五子棋・黑白棋のルール（サーバーが唯一の判定者）。
 * frontend の gomoku.ts / reversi.ts と同じ判定であることを固定する。
 */
class GameRulesTest {

    /* ---------- 五子棋 ---------- */

    @Test
    void gomokuWinsWithFiveInARow() {
        GameBoard board = GameBoard.initial("GOMOKU");
        // 横に 4 つ並べてから 5 つ目を置く
        for (int col = 0; col < 4; col += 1) {
            GameRules.apply(board, "GOMOKU", "BLACK", 7, col);
        }
        GameRules.MoveResult result = GameRules.apply(board, "GOMOKU", "BLACK", 7, 4);

        assertThat(result.win()).isTrue();
        assertThat(result.line()).hasSize(5);
        assertThat(result.line().get(0)).containsExactly(7, 0);
        assertThat(result.line().get(4)).containsExactly(7, 4);
        assertThat(result.gameOver()).isTrue();
        assertThat(result.draw()).isFalse();
    }

    @Test
    void gomokuWinsDiagonally() {
        GameBoard board = GameBoard.initial("GOMOKU");
        for (int step = 0; step < 4; step += 1) {
            GameRules.apply(board, "GOMOKU", "BLACK", step, step);
        }
        GameRules.MoveResult result = GameRules.apply(board, "GOMOKU", "BLACK", 4, 4);

        assertThat(result.win()).isTrue();
        assertThat(result.line()).hasSize(5);
    }

    @Test
    void gomokuRejectsOutsideAndFilledCells() {
        GameBoard board = GameBoard.initial("GOMOKU");
        GameRules.apply(board, "GOMOKU", "BLACK", 7, 7);

        assertThatThrownBy(() -> GameRules.apply(board, "GOMOKU", "WHITE", 7, 7))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("既に石があります");
        assertThatThrownBy(() -> GameRules.apply(board, "GOMOKU", "WHITE", -1, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("盤面の外");
        assertThatThrownBy(() -> GameRules.apply(board, "GOMOKU", "WHITE", 15, 0))
                .isInstanceOf(ValidationException.class);
    }

    @Test
    void gomokuIsDrawWhenBoardIsFullWithoutFiveInARow() {
        // 5 連ができないように 2 個ずつ交互に敷き詰めた盤面を直接作る（引き分けの判定だけを見る）
        GameBoard board = GameBoard.empty(4);
        for (int row = 0; row < 4; row += 1) {
            for (int col = 0; col < 4; col += 1) {
                board.set(row, col, (row % 2 == 0) ? "BLACK" : "WHITE");
            }
        }
        board.set(0, 0, null);
        GameRules.MoveResult result = GameRules.apply(board, "GOMOKU", "BLACK", 0, 0);

        assertThat(result.win()).isFalse();
        assertThat(result.boardFull()).isTrue();
        assertThat(result.gameOver()).isTrue();
        assertThat(result.draw()).isTrue();
    }

    /* ---------- 黑白棋 ---------- */

    @Test
    void reversiInitialBoardHasTwoDiscsEach() {
        GameBoard board = GameBoard.initial("REVERSI");

        assertThat(board.size()).isEqualTo(8);
        assertThat(board.count("BLACK")).isEqualTo(2);
        assertThat(board.count("WHITE")).isEqualTo(2);
        assertThat(board.get(3, 3)).isEqualTo("WHITE");
        assertThat(board.get(3, 4)).isEqualTo("BLACK");
        assertThat(board.get(4, 3)).isEqualTo("BLACK");
        assertThat(board.get(4, 4)).isEqualTo("WHITE");
    }

    @Test
    void reversiFlipsTheSandwichedDiscs() {
        GameBoard board = GameBoard.initial("REVERSI");
        // 黒は (2,3) に置くと (3,3) の白を挟んで返せる
        GameRules.MoveResult result = GameRules.apply(board, "REVERSI", "BLACK", 2, 3);

        assertThat(result.flipped()).hasSize(1);
        assertThat(result.flipped().get(0)).containsExactly(3, 3);
        assertThat(board.get(2, 3)).isEqualTo("BLACK");
        assertThat(board.get(3, 3)).isEqualTo("BLACK");
        assertThat(board.count("BLACK")).isEqualTo(4);
        assertThat(result.gameOver()).isFalse();
        assertThat(result.nextStone()).isEqualTo("WHITE");
    }

    @Test
    void reversiRejectsIllegalMove() {
        GameBoard board = GameBoard.initial("REVERSI");

        // 何も挟めないマス
        assertThatThrownBy(() -> GameRules.apply(board, "REVERSI", "BLACK", 0, 0))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("挟めません");
        // 既に石があるマス
        assertThatThrownBy(() -> GameRules.apply(board, "REVERSI", "BLACK", 3, 3))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("既に石があります");
    }

    @Test
    void reversiPassesWhenOpponentCannotMove() {
        // 白が置ける所が無く、黒はまだ置ける盤面を作る
        //   0行: 黒(0,0) 白(0,1) 空(0,2) 黒(0,3) 白(0,4)
        GameBoard board = GameBoard.empty(8);
        board.set(0, 0, "BLACK");
        board.set(0, 1, "WHITE");
        board.set(0, 3, "BLACK");
        board.set(0, 4, "WHITE");
        // 黒が (0,2) に置くと (0,1) の白を返す。残った白は (0,4) だけで、白はどこも挟めない
        GameRules.MoveResult result = GameRules.apply(board, "REVERSI", "BLACK", 0, 2);

        assertThat(result.flipped()).hasSize(1);
        assertThat(result.nextPassed()).isTrue();
        assertThat(result.nextStone()).isEqualTo("BLACK");   // もう一度 黒の番
        assertThat(result.gameOver()).isFalse();
    }

    @Test
    void reversiEndsWhenNobodyCanMoveAndCountsDiscs() {
        GameBoard board = GameBoard.empty(8);
        board.set(0, 0, "BLACK");
        board.set(0, 1, "WHITE");
        board.set(0, 2, null);
        // 黒が置いた後、白も黒も置けない形にするため、周りを埋めておく
        for (int col = 3; col < 8; col += 1) {
            board.set(0, col, "BLACK");
        }
        GameRules.MoveResult result = GameRules.apply(board, "REVERSI", "BLACK", 0, 2);

        // 黒 7 個（0,0 と 0,2〜0,7）／白 0 個（(0,1) は返される）
        assertThat(result.gameOver()).isTrue();
        assertThat(result.win()).isTrue();
        assertThat(result.draw()).isFalse();
        assertThat(board.count("WHITE")).isZero();
    }

    @Test
    void boardJsonRoundTripKeepsThePosition() {
        GameBoard board = GameBoard.initial("REVERSI");
        GameRules.apply(board, "REVERSI", "BLACK", 2, 3);

        GameBoard restored = GameBoard.fromJson("REVERSI", board.toJson());
        assertThat(restored.toJson()).isEqualTo(board.toJson());
        assertThat(restored.count("BLACK")).isEqualTo(4);
    }

    @Test
    void legalMovesAreFourAtTheStart() {
        GameBoard board = GameBoard.initial("REVERSI");
        List<List<Integer>> blackMoves = GameRules.legalMoves(board, "BLACK");

        assertThat(blackMoves).hasSize(4);
        assertThat(blackMoves).contains(List.of(2, 3), List.of(3, 2), List.of(4, 5), List.of(5, 4));
    }
}

package com.study21.user.game;

import com.study21.common.core.exception.ValidationException;

import java.util.ArrayList;
import java.util.List;

/**
 * 五子棋・黑白棋のルール（**サーバーが唯一の判定者**）。
 *
 * <p>frontend の `features/game/gomoku.ts` / `reversi.ts` と同じ判定を Java に移植した。
 * 画面は「自分が置けるか」を先に見せるだけの補助で、正当性はここで必ず確認する。</p>
 */
public final class GameRules {

    /** 五子棋の勝利に必要な連続数。 */
    public static final int WIN_LENGTH = 5;
    /** 五子棋の 4 方向（横 → 縦 → ↘ → ↗）。 */
    private static final int[][] GOMOKU_DIRECTIONS = {{0, 1}, {1, 0}, {1, 1}, {1, -1}};
    /** 黑白棋の 8 方向。 */
    private static final int[][] REVERSI_DIRECTIONS = {
            {-1, -1}, {-1, 0}, {-1, 1}, {0, -1}, {0, 1}, {1, -1}, {1, 0}, {1, 1}
    };

    private GameRules() {
    }

    /** 反対の石。 */
    public static String otherStone(String stone) {
        return "BLACK".equals(stone) ? "WHITE" : "BLACK";
    }

    /**
     * 一手の結果。
     *
     * @param flipped    この手で返した石（黑白棋。[[行,列], ...]）
     * @param line       勝ちが成立した並び（五子棋。[[行,列], ...]。勝ちでなければ空）
     * @param win        この手で勝ったか
     * @param boardFull  盤面が埋まったか（五子棋の引き分け判定）
     * @param nextStone  次に打つ石（通常は相手。黑白棋で相手が打てないときは同じ石）
     * @param nextPassed 相手が打てずにパスしたか（黑白棋）
     * @param gameOver   この手で対局が終わったか（勝ち・引き分け・両者打てない）
     * @param draw       引き分けで終わったか
     */
    public record MoveResult(
            List<List<Integer>> flipped,
            List<List<Integer>> line,
            boolean win,
            boolean boardFull,
            String nextStone,
            boolean nextPassed,
            boolean gameOver,
            boolean draw) {
    }

    /**
     * 一手を適用する（盤面は呼び出し側が渡したものを更新する）。
     *
     * @throws ValidationException 盤外・空きでないマス・ルール上置けない手のとき
     */
    public static MoveResult apply(GameBoard board, String gameType, String stone, int row, int col) {
        if (!board.isInside(row, col)) {
            throw new ValidationException("盤面の外には置けません。");
        }
        if (!board.isEmpty(row, col)) {
            throw new ValidationException("そのマスには既に石があります。");
        }
        return "REVERSI".equals(gameType)
                ? applyReversi(board, stone, row, col)
                : applyGomoku(board, stone, row, col);
    }

    /* ---------- 五子棋 ---------- */

    private static MoveResult applyGomoku(GameBoard board, String stone, int row, int col) {
        board.set(row, col, stone);
        List<List<Integer>> line = findWinningLine(board, row, col, stone);
        boolean win = !line.isEmpty();
        boolean full = board.isFull();
        return new MoveResult(List.of(), line, win, full, otherStone(stone), false,
                win || full, !win && full);
    }

    /** その位置に置いた結果できる「勝利の一線」（5 連以上）。無ければ空。 */
    public static List<List<Integer>> findWinningLine(GameBoard board, int row, int col, String stone) {
        for (int[] direction : GOMOKU_DIRECTIONS) {
            List<List<Integer>> line = collectLine(board, row, col, stone, direction[0], direction[1]);
            if (line.size() >= WIN_LENGTH) {
                return line;
            }
        }
        return List.of();
    }

    private static List<List<Integer>> collectLine(GameBoard board, int row, int col, String stone,
                                                   int deltaRow, int deltaCol) {
        List<List<Integer>> backward = new ArrayList<>();
        for (int step = 1; step < WIN_LENGTH; step += 1) {
            int nextRow = row - deltaRow * step;
            int nextCol = col - deltaCol * step;
            if (!stone.equals(board.get(nextRow, nextCol))) {
                break;
            }
            backward.add(0, List.of(nextRow, nextCol));
        }
        List<List<Integer>> forward = new ArrayList<>();
        for (int step = 1; step < WIN_LENGTH; step += 1) {
            int nextRow = row + deltaRow * step;
            int nextCol = col + deltaCol * step;
            if (!stone.equals(board.get(nextRow, nextCol))) {
                break;
            }
            forward.add(List.of(nextRow, nextCol));
        }
        List<List<Integer>> line = new ArrayList<>(backward);
        line.add(List.of(row, col));
        line.addAll(forward);
        return line;
    }

    /* ---------- 黑白棋 ---------- */

    private static MoveResult applyReversi(GameBoard board, String stone, int row, int col) {
        List<List<Integer>> flipped = flipsFor(board, row, col, stone);
        if (flipped.isEmpty()) {
            throw new ValidationException("そこには置けません（相手の石を挟めません）。");
        }
        board.set(row, col, stone);
        for (List<Integer> position : flipped) {
            board.set(position.get(0), position.get(1), stone);
        }

        String opponent = otherStone(stone);
        boolean opponentCanMove = !legalMoves(board, opponent).isEmpty();
        boolean selfCanMove = !legalMoves(board, stone).isEmpty();

        if (opponentCanMove) {
            return new MoveResult(flipped, List.of(), false, board.isFull(), opponent, false, false, false);
        }
        if (selfCanMove) {
            // 相手は置けないが自分は置ける → 相手はパスして、もう一度自分の番
            return new MoveResult(flipped, List.of(), false, board.isFull(), stone, true, false, false);
        }
        // 両者とも置けない → 対局終了（石の数で勝敗）
        int mine = board.count(stone);
        int theirs = board.count(opponent);
        boolean win = mine > theirs;
        boolean draw = mine == theirs;
        return new MoveResult(flipped, List.of(), win, board.isFull(), opponent, false, true, draw);
    }

    /** 置けるマス（黑白棋）。 */
    public static List<List<Integer>> legalMoves(GameBoard board, String stone) {
        List<List<Integer>> moves = new ArrayList<>();
        for (int row = 0; row < board.size(); row += 1) {
            for (int col = 0; col < board.size(); col += 1) {
                if (board.isEmpty(row, col) && !flipsFor(board, row, col, stone).isEmpty()) {
                    moves.add(List.of(row, col));
                }
            }
        }
        return moves;
    }

    /** そのマスに置いたときに返る石。置けないときは空。 */
    public static List<List<Integer>> flipsFor(GameBoard board, int row, int col, String stone) {
        if (!board.isEmpty(row, col)) {
            return List.of();
        }
        List<List<Integer>> flips = new ArrayList<>();
        String opponent = otherStone(stone);
        for (int[] direction : REVERSI_DIRECTIONS) {
            List<List<Integer>> found = flipsInDirection(board, row, col, opponent, direction[0], direction[1]);
            flips.addAll(found);
        }
        return flips;
    }

    /** 1 方向ぶん。「相手の石が 1 個以上続き、その先が自分の石」のときだけその間を返す。 */
    private static List<List<Integer>> flipsInDirection(GameBoard board, int row, int col,
                                                       String opponent, int deltaRow, int deltaCol) {
        List<List<Integer>> found = new ArrayList<>();
        int step = 1;
        while (true) {
            int nextRow = row + deltaRow * step;
            int nextCol = col + deltaCol * step;
            if (!board.isInside(nextRow, nextCol)) {
                return List.of();
            }
            String value = board.get(nextRow, nextCol);
            if (value == null) {
                return List.of();
            }
            if (!opponent.equals(value)) {
                // 自分の石で挟めた（1 個以上あれば成立、無ければ不成立）
                return found.isEmpty() ? List.of() : found;
            }
            found.add(List.of(nextRow, nextCol));
            step += 1;
        }
    }
}

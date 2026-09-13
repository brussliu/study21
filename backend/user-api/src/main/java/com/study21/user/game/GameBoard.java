package com.study21.user.game;

import java.util.ArrayList;
import java.util.List;

/**
 * 対戦の盤面（ゲーム共通）。
 *
 * <p>石は 'BLACK' / 'WHITE'、空きマスは null。行・列は 0 始まり。
 * 五子棋と黑白棋で同じ入れ物を使い、JSON（API と DB の JSONB）との変換もここで行う。</p>
 */
public final class GameBoard {

    /** 五子棋の盤面サイズ（15 路盤。frontend の GOMOKU_SIZE と同じ）。 */
    public static final int GOMOKU_SIZE = 15;
    /** 黑白棋の盤面サイズ（8×8。frontend の REVERSI_SIZE と同じ）。 */
    public static final int REVERSI_SIZE = 8;

    private final String[][] cells;

    private GameBoard(String[][] cells) {
        this.cells = cells;
    }

    /** ゲーム種別に応じた初期盤面を作る（黑白棋は中央 4 マスに石を置く）。 */
    public static GameBoard initial(String gameType) {
        return "REVERSI".equals(gameType) ? initialReversi() : empty(sizeOf(gameType));
    }

    /** 指定サイズの空の盤面。 */
    public static GameBoard empty(int size) {
        return new GameBoard(new String[size][size]);
    }

    /** 黑白棋の初期配置（中央 4 マス。frontend の createReversi と同じ向き）。 */
    private static GameBoard initialReversi() {
        GameBoard board = empty(REVERSI_SIZE);
        int top = REVERSI_SIZE / 2 - 1;
        int left = REVERSI_SIZE / 2 - 1;
        board.set(top, left, "WHITE");
        board.set(top, left + 1, "BLACK");
        board.set(top + 1, left, "BLACK");
        board.set(top + 1, left + 1, "WHITE");
        return board;
    }

    /** ゲーム種別の盤面サイズ。 */
    public static int sizeOf(String gameType) {
        return "REVERSI".equals(gameType) ? REVERSI_SIZE : GOMOKU_SIZE;
    }

    /** DB・API の JSON（文字列の 2 次元配列）から作る。null や壊れた値は空の盤面として扱う。 */
    public static GameBoard fromJson(String gameType, List<List<String>> values) {
        int size = sizeOf(gameType);
        GameBoard board = empty(size);
        if (values == null) {
            return board;
        }
        for (int row = 0; row < Math.min(size, values.size()); row += 1) {
            List<String> line = values.get(row);
            if (line == null) {
                continue;
            }
            for (int col = 0; col < Math.min(size, line.size()); col += 1) {
                board.set(row, col, line.get(col));
            }
        }
        return board;
    }

    /** JSON に載せる形（文字列の 2 次元配列）にする。 */
    public List<List<String>> toJson() {
        List<List<String>> result = new ArrayList<>();
        for (String[] row : cells) {
            // 空きマスは null なので List.of（null 不可）は使えない
            result.add(new ArrayList<>(java.util.Arrays.asList(row)));
        }
        return result;
    }

    public int size() {
        return cells.length;
    }

    public boolean isInside(int row, int col) {
        return row >= 0 && row < cells.length && col >= 0 && col < cells.length;
    }

    public String get(int row, int col) {
        return isInside(row, col) ? cells[row][col] : null;
    }

    public void set(int row, int col, String stone) {
        if (isInside(row, col)) {
            cells[row][col] = stone;
        }
    }

    public boolean isEmpty(int row, int col) {
        return isInside(row, col) && cells[row][col] == null;
    }

    /** 空きマスが無いか（五子棋の引き分け判定）。 */
    public boolean isFull() {
        for (String[] row : cells) {
            for (String cell : row) {
                if (cell == null) {
                    return false;
                }
            }
        }
        return true;
    }

    /** 石の数。 */
    public int count(String stone) {
        int total = 0;
        for (String[] row : cells) {
            for (String cell : row) {
                if (stone.equals(cell)) {
                    total += 1;
                }
            }
        }
        return total;
    }

    /** 複製する（ルールの判定で盤面を壊さないため）。 */
    public GameBoard copy() {
        GameBoard copy = empty(cells.length);
        for (int row = 0; row < cells.length; row += 1) {
            System.arraycopy(cells[row], 0, copy.cells[row], 0, cells.length);
        }
        return copy;
    }
}

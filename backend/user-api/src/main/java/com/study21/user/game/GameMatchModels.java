package com.study21.user.game;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDateTime;
import java.util.List;

/**
 * ゲーム対戦の API で使う型。
 *
 * <p>五子棋（GOMOKU）・黑白棋（REVERSI）を生徒と保護者が同時に対戦する。
 * 盤面は DB の `GAM_対戦手情報` が正で、`GAM_対戦情報` は表示用の写しを持つ。</p>
 */
public final class GameMatchModels {

    /** ゲーム種別。 */
    public static final List<String> GAME_TYPES = List.of("GOMOKU", "REVERSI");
    /** 状態。 */
    public static final List<String> STATUSES = List.of("WAITING", "PLAYING", "FINISHED", "DECLINED", "CANCELLED");
    /** 石。 */
    public static final List<String> STONES = List.of("BLACK", "WHITE");
    /** 結果。 */
    public static final List<String> RESULT_CODES = List.of("WIN", "DRAW", "RESIGN", "TIMEOUT");

    private GameMatchModels() {
    }

    /** 対戦できる相手（自分の子ども／自分の親）。 */
    /** online は「いまゲーム画面を開いている（リアルタイム接続が生きている）」か。 */
    public record Opponent(long accountId, String displayName, String role, boolean online) {
    }

    /** 相手の一覧。 */
    public record OpponentList(List<Opponent> items) {
    }

    /** 対戦一覧の 1 行。 */
    public record MatchRow(
            long matchId,
            String gameType,
            String status,
            boolean myTurn,
            boolean iAmChallenger,
            String opponentName,
            String myStone,
            int moveCount,
            String resultCode,
            Long winnerAccountId,
            LocalDateTime updatedAt,
            int version) {
    }

    /** 対戦一覧。 */
    public record MatchList(List<MatchRow> items) {
    }

    /** 1 手。 */
    public record MoveRow(
            int moveNumber,
            long accountId,
            String stone,
            Integer row,
            Integer col,
            boolean pass,
            List<List<Integer>> flipped,
            List<List<Integer>> line) {
    }

    /** 対戦の詳細（盤面と指し手）。 */
    public record MatchDetail(
            long matchId,
            String gameType,
            String status,
            List<List<String>> board,
            String myStone,
            /** 挑戦者が持つ石（BLACK=先手 / WHITE=後手）。まだ 1 手も無いときに自分を割り出す。 */
            String challengerStone,
            Long turnAccountId,
            boolean myTurn,
            int moveCount,
            Long winnerAccountId,
            String resultCode,
            int version,
            long challengerAccountId,
            long opponentAccountId,
            String challengerName,
            String opponentName,
            /** 見ている側から見た相手が、いまゲーム画面を開いているか。 */
            boolean opponentOnline,
            LocalDateTime finishedAt,
            List<MoveRow> moves) {
    }

    /**
     * 招待（対戦の作成）。
     *
     * <p>{@code challengerStone} は挑戦者が持つ石＝先手か後手か
     * （BLACK＝先手 / WHITE＝後手。省略・null は先手）。
     * 承諾されたあとの最初の手番は黒を持つ人になる。</p>
     */
    public record CreateRequest(
            @NotBlank(message = "ゲームの種類を選択してください。") String gameType,
            @NotNull(message = "対戦相手を選択してください。") Long opponentAccountId,
            String challengerStone) {
    }

    /** 着手。 */
    public record MoveRequest(
            @NotNull(message = "置くマスを指定してください。") Integer row,
            @NotNull(message = "置くマスを指定してください。") Integer col,
            @NotNull(message = "バージョンを指定してください。") Integer version) {
    }

    /** 状態を変える操作（accept / decline / cancel / resign）のリクエスト。 */
    public record StateChangeRequest(Integer version) {
    }
}

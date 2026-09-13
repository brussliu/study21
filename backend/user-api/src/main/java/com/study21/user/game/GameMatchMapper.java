package com.study21.user.game;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * GAM_対戦情報 / GAM_対戦手情報 の Mapper。
 *
 * <p>SQL のログは common-core の SqlLoggingInterceptor が自動で記録する（手書きのログは書かない）。</p>
 */
@Mapper
public interface GameMatchMapper {

    /** 対戦を新規登録する（対戦ID は採番された値を entity に書き戻す）。 */
    int insertMatch(GameMatchEntity entity);

    /** 対戦を 1 件取得する（対戦相手の名前つき）。viewerAccountId は「相手」を決めるために使う。 */
    GameMatchEntity findMatchById(@Param("matchId") long matchId,
                                  @Param("viewerAccountId") long viewerAccountId);

    /** 自分が関わっている対戦の一覧（新しい順）。 */
    List<GameMatchEntity> listMyMatches(@Param("accountId") long accountId);

    /** 招待を承諾する（相手のみ・WAITING のみ）。 */
    int acceptMatch(@Param("matchId") long matchId,
                    @Param("opponentAccountId") long opponentAccountId,
                    @Param("version") int version);

    /** 招待を断る（相手のみ・WAITING のみ）。 */
    int declineMatch(@Param("matchId") long matchId,
                     @Param("opponentAccountId") long opponentAccountId,
                     @Param("version") int version);

    /** 招待を取り消す（挑戦者のみ・WAITING のみ）。 */
    int cancelMatch(@Param("matchId") long matchId,
                    @Param("challengerAccountId") long challengerAccountId,
                    @Param("version") int version);

    /** 相手が不在のときの終了を書き込む（PLAYING かつ版が合うときのみ）。 */
    int timeoutMatch(@Param("matchId") long matchId,
                     @Param("accountId") long accountId,
                     @Param("winnerAccountId") long winnerAccountId,
                     @Param("version") int version);

    /** 投了する（対戦者のみ・PLAYING のみ）。 */
    int resignMatch(@Param("matchId") long matchId,
                    @Param("accountId") long accountId,
                    @Param("winnerAccountId") long winnerAccountId,
                    @Param("updatedByAccountId") Long updatedByAccountId,
                    @Param("version") int version);

    /** 着手の結果を対戦へ反映する（盤面・手番・勝敗。版が合わなければ 0 件）。 */
    int updateAfterMove(@Param("matchId") long matchId,
                        @Param("board") String board,
                        @Param("moveCount") int moveCount,
                        @Param("turnAccountId") Long turnAccountId,
                        @Param("status") String status,
                        @Param("winnerAccountId") Long winnerAccountId,
                        @Param("resultCode") String resultCode,
                        @Param("finishedAt") java.sql.Timestamp finishedAt,
                        @Param("updatedByAccountId") Long updatedByAccountId,
                        @Param("version") int version);

    /** 指し手を 1 件追加する。 */
    int insertMove(GameMoveEntity entity);

    /** 対戦の指し手（手数の昇順）。 */
    List<GameMoveEntity> listMoves(@Param("matchId") long matchId);
}

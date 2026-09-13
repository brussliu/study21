package com.study21.user.game;

import java.sql.Timestamp;

/**
 * GAM_対戦情報 のエンティティ。
 * 盤面（JSONB）は MyBatis でそのまま扱えるよう文字列で持つ。
 */
public class GameMatchEntity {

    private Long matchId;
    private String gameType;
    private Long challengerAccountId;
    private Long opponentAccountId;
    private Long turnAccountId;
    private String status;
    private String challengerStone;
    /** 盤面（JSON 文字列。API に返すときに 2 次元配列へ変換する） */
    private String board;
    private Integer moveCount;
    private Long winnerAccountId;
    private String resultCode;
    private Timestamp startedAt;
    private Timestamp finishedAt;
    private Integer version;
    private Long createdByAccountId;
    private Long updatedByAccountId;
    private String createdByCode;
    private String updatedByCode;
    private Timestamp createdAt;
    private Timestamp updatedAt;
    /** 表示用: 対戦相手の名前（ACC_アカウント を結合して取得） */
    private String opponentName;
    /** 表示用: 自分の名前 */
    private String myName;

    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public String getGameType() { return gameType; }
    public void setGameType(String gameType) { this.gameType = gameType; }
    public Long getChallengerAccountId() { return challengerAccountId; }
    public void setChallengerAccountId(Long challengerAccountId) { this.challengerAccountId = challengerAccountId; }
    public Long getOpponentAccountId() { return opponentAccountId; }
    public void setOpponentAccountId(Long opponentAccountId) { this.opponentAccountId = opponentAccountId; }
    public Long getTurnAccountId() { return turnAccountId; }
    public void setTurnAccountId(Long turnAccountId) { this.turnAccountId = turnAccountId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getChallengerStone() { return challengerStone; }
    public void setChallengerStone(String challengerStone) { this.challengerStone = challengerStone; }
    public String getBoard() { return board; }
    public void setBoard(String board) { this.board = board; }
    public Integer getMoveCount() { return moveCount; }
    public void setMoveCount(Integer moveCount) { this.moveCount = moveCount; }
    public Long getWinnerAccountId() { return winnerAccountId; }
    public void setWinnerAccountId(Long winnerAccountId) { this.winnerAccountId = winnerAccountId; }
    public String getResultCode() { return resultCode; }
    public void setResultCode(String resultCode) { this.resultCode = resultCode; }
    public Timestamp getStartedAt() { return startedAt; }
    public void setStartedAt(Timestamp startedAt) { this.startedAt = startedAt; }
    public Timestamp getFinishedAt() { return finishedAt; }
    public void setFinishedAt(Timestamp finishedAt) { this.finishedAt = finishedAt; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Long getCreatedByAccountId() { return createdByAccountId; }
    public void setCreatedByAccountId(Long createdByAccountId) { this.createdByAccountId = createdByAccountId; }
    public Long getUpdatedByAccountId() { return updatedByAccountId; }
    public void setUpdatedByAccountId(Long updatedByAccountId) { this.updatedByAccountId = updatedByAccountId; }
    public String getCreatedByCode() { return createdByCode; }
    public void setCreatedByCode(String createdByCode) { this.createdByCode = createdByCode; }
    public String getUpdatedByCode() { return updatedByCode; }
    public void setUpdatedByCode(String updatedByCode) { this.updatedByCode = updatedByCode; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
    public String getOpponentName() { return opponentName; }
    public void setOpponentName(String opponentName) { this.opponentName = opponentName; }
    public String getMyName() { return myName; }
    public void setMyName(String myName) { this.myName = myName; }
}

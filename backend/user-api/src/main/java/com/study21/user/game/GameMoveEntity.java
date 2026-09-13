package com.study21.user.game;

import java.sql.Timestamp;

/**
 * GAM_対戦手情報 のエンティティ（1 手 = 1 行。盤面の正）。
 */
public class GameMoveEntity {

    private Long moveId;
    private Long matchId;
    private Integer moveNumber;
    private Long accountId;
    private String stone;
    private Integer rowIndex;
    private Integer colIndex;
    /** '1'=パス（黑白棋で置ける所が無い） */
    private String passFlag;
    /** 返した石（JSON 文字列。[[行,列], ...]） */
    private String flipped;
    /** 成立した並び（JSON 文字列） */
    private String line;
    private Timestamp createdAt;

    public Long getMoveId() { return moveId; }
    public void setMoveId(Long moveId) { this.moveId = moveId; }
    public Long getMatchId() { return matchId; }
    public void setMatchId(Long matchId) { this.matchId = matchId; }
    public Integer getMoveNumber() { return moveNumber; }
    public void setMoveNumber(Integer moveNumber) { this.moveNumber = moveNumber; }
    public Long getAccountId() { return accountId; }
    public void setAccountId(Long accountId) { this.accountId = accountId; }
    public String getStone() { return stone; }
    public void setStone(String stone) { this.stone = stone; }
    public Integer getRowIndex() { return rowIndex; }
    public void setRowIndex(Integer rowIndex) { this.rowIndex = rowIndex; }
    public Integer getColIndex() { return colIndex; }
    public void setColIndex(Integer colIndex) { this.colIndex = colIndex; }
    public String getPassFlag() { return passFlag; }
    public void setPassFlag(String passFlag) { this.passFlag = passFlag; }
    public String getFlipped() { return flipped; }
    public void setFlipped(String flipped) { this.flipped = flipped; }
    public String getLine() { return line; }
    public void setLine(String line) { this.line = line; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

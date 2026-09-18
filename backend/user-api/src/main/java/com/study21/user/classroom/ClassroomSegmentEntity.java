package com.study21.user.classroom;

import java.math.BigDecimal;
import java.sql.Timestamp;

/**
 * CR_授業転写セグメント情報（転写の 1 セグメント）の 1 行。
 *
 * <p>`連番` は**記録内の表示順**（1 から。並べ替えは「開始オフセット秒 → 連番」）。
 * `発話キー` は発話の安定した識別子（`音源#認識セッション番号#sentence_id`）で、
 * 同じ発話の再保存を UPDATE に寄せる（認識セッションを作り直しても衝突しない）。
 * 話者ラベルは**音源**で決める（マイク＝学生／共有の音＝先生／マイクのみ＝講義）。</p>
 */
public class ClassroomSegmentEntity {

    private Long segmentId;
    private Long recordId;
    private Integer seq;
    private BigDecimal startOffsetSeconds;
    private BigDecimal endOffsetSeconds;
    private String speaker;
    /** 発話の安定した識別子（`mic#2#17` のように 音源#セッション番号#sentence_id）。旧データは null。 */
    private String utteranceKey;
    /** この文を出した音源（`mic` / `shared`）。旧データは null（＝講義）。 */
    private String source;
    private String text;
    private String language;
    private Timestamp createdAt;

    public Long getSegmentId() { return segmentId; }
    public void setSegmentId(Long segmentId) { this.segmentId = segmentId; }
    public Long getRecordId() { return recordId; }
    public void setRecordId(Long recordId) { this.recordId = recordId; }
    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public BigDecimal getStartOffsetSeconds() { return startOffsetSeconds; }
    public void setStartOffsetSeconds(BigDecimal startOffsetSeconds) { this.startOffsetSeconds = startOffsetSeconds; }
    public BigDecimal getEndOffsetSeconds() { return endOffsetSeconds; }
    public void setEndOffsetSeconds(BigDecimal endOffsetSeconds) { this.endOffsetSeconds = endOffsetSeconds; }
    public String getSpeaker() { return speaker; }
    public void setSpeaker(String speaker) { this.speaker = speaker; }
    public String getUtteranceKey() { return utteranceKey; }
    public void setUtteranceKey(String utteranceKey) { this.utteranceKey = utteranceKey; }
    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public String getLanguage() { return language; }
    public void setLanguage(String language) { this.language = language; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
}

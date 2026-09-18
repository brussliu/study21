package com.study21.admin.classroomai;

/**
 * CR_授業転写セグメント情報（転写のセグメント行）の 1 行（admin-api 側）。
 *
 * <p>batC61 / batC62 がノートを生成するときに、対象範囲の転写を連結するために
 * 連番と本文だけを読む。</p>
 */
public class ClassroomSegmentEntity {

    private Integer seq;
    private String text;

    public Integer getSeq() { return seq; }
    public void setSeq(Integer seq) { this.seq = seq; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
}

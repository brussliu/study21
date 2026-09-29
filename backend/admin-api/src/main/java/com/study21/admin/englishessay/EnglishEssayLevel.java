package com.study21.admin.englishessay;

import com.study21.common.core.exception.ValidationException;

/**
 * 英検級（{@code ENG_英作文情報.英検級} と同じ 3 つ）。
 *
 * <p>添削の配点は級で決まる（設計・DDL の注記と同じ）:
 * <b>GRADE1 = 各観点 8 点で満点 32／PRE1・GRADE2 = 各観点 4 点で満点 16</b>。
 * 語数の目安は画面の見出しに使う（実際の字数条件は<b>設問文から AI が読む</b>のが正で、
 * ここは「AI が条件を示さなかったとき」の表示用の目安）。</p>
 */
public enum EnglishEssayLevel {

    GRADE1(8, "200〜240語"),
    PRE1(4, "120〜150語"),
    GRADE2(4, "80〜100語");

    private final int rubricMax;
    private final String wordRequirement;

    EnglishEssayLevel(int rubricMax, String wordRequirement) {
        this.rubricMax = rubricMax;
        this.wordRequirement = wordRequirement;
    }

    /** 1 観点の満点（8 か 4）。 */
    public int rubricMax() {
        return rubricMax;
    }

    /** 4 観点の満点（32 か 16）。 */
    public int maxScore() {
        return rubricMax * 4;
    }

    /** 語数の目安（AI が設問文から条件を読めなかったときの表示用）。 */
    public String wordRequirement() {
        return wordRequirement;
    }

    /**
     * 画面・DB の値を級にする（前後の空白と大文字小文字は許す）。
     *
     * @throws ValidationException 未指定・未知の値のとき（日本語の理由）
     */
    public static EnglishEssayLevel parse(String value) {
        String text = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT);
        for (EnglishEssayLevel level : values()) {
            if (level.name().equals(text)) {
                return level;
            }
        }
        throw new ValidationException("英検級は GRADE1 / PRE1 / GRADE2 のいずれかで指定してください。");
    }
}

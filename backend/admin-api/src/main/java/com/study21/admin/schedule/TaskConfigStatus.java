package com.study21.admin.schedule;

/**
 * タスクごとの設定の状態（**null で兼用しない**。利用者に見せる区別）。
 *
 * <ul>
 *   <li>{@link #NOT_LOADED} … まだ DB から読んでいない</li>
 *   <li>{@link #LOADED} … 有効な設定がある（スケジュールできる）</li>
 *   <li>{@link #MISSING} … 設定が登録されていない（COM_設定情報 に行が無い）</li>
 *   <li>{@link #INVALID} … あるが値が不正（時刻・間隔・ずらしの範囲外など）</li>
 * </ul>
 *
 * <p>{@code MISSING} / {@code INVALID} のタスクは**自動実行しない**（コードに隠れた既定値で
 * 走らせない。利用者の指示）。</p>
 */
public enum TaskConfigStatus {
    /** 未読込。 */
    NOT_LOADED("未読込"),
    /** 有効な設定がある。 */
    LOADED("有効"),
    /** 設定が無い。 */
    MISSING("未設定"),
    /** 設定が不正。 */
    INVALID("設定不正");

    private final String label;

    TaskConfigStatus(String label) {
        this.label = label;
    }

    /** 画面に出す日本語。 */
    public String label() {
        return label;
    }

    /** スケジュールに使える状態か。 */
    public boolean usable() {
        return this == LOADED;
    }
}

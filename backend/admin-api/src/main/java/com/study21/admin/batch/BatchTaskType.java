package com.study21.admin.batch;

/**
 * バッチタスク種別。
 */
public enum BatchTaskType {
    /** 定時実行 */
    R,
    /** 循環実行 */
    L,
    /** 呼出（コマンド）実行 */
    C;

    public static BatchTaskType from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return BatchTaskType.valueOf(value.trim().toUpperCase());
    }
}

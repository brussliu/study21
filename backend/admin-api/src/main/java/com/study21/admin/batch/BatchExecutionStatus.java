package com.study21.admin.batch;

/**
 * バッチ実行状態。
 */
public enum BatchExecutionStatus {
    QUEUED("待機中"),
    RUNNING("実行中"),
    SUCCESS("正常終了"),
    FAILED("異常終了"),
    SKIPPED("スキップ");

    private final String label;

    BatchExecutionStatus(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public static BatchExecutionStatus from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (BatchExecutionStatus s : values()) {
            if (s.name().equalsIgnoreCase(value.trim())) {
                return s;
            }
        }
        return null;
    }
}

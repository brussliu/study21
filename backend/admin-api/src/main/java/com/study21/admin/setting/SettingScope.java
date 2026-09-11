package com.study21.admin.setting;

/**
 * 設定の作用域（scope）。
 *
 * <ul>
 *   <li>{@link #GLOBAL} - 管理者が保守するグローバル設定</li>
 *   <li>{@link #STUDENT} - 特定学生のみ（保護者へは自動公開しない）</li>
 *   <li>{@link #PARENT} - 特定保護者のみ（学生へは自動公開しない）</li>
 *   <li>{@link #STUDENT_PARENT_SHARED} - 特定学生×特定保護者の組み合わせのみ共有</li>
 * </ul>
 */
public enum SettingScope {
    GLOBAL,
    STUDENT,
    PARENT,
    STUDENT_PARENT_SHARED;

    public static SettingScope from(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        for (SettingScope scope : values()) {
            if (scope.name().equalsIgnoreCase(value.trim())) {
                return scope;
            }
        }
        throw new IllegalArgumentException("不正な設定スコープです: " + value);
    }
}

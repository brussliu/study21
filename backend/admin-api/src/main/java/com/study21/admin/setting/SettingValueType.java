package com.study21.admin.setting;

/**
 * 設定値の型。
 */
public enum SettingValueType {
    STRING,
    INTEGER,
    DECIMAL,
    BOOLEAN,
    TEXT,
    TIME,
    ENUM;

    public static SettingValueType from(String value) {
        if (value == null || value.isBlank()) {
            return STRING;
        }
        for (SettingValueType type : values()) {
            if (type.name().equalsIgnoreCase(value.trim())) {
                return type;
            }
        }
        throw new IllegalArgumentException("不正な設定値型です: " + value);
    }
}

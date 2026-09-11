package com.study21.common.core.api;

/**
 * 统一错误码（Study 2.1 统一 API 规范）。
 */
public enum ErrorCode {
    OK("OK", "OK"),
    VALIDATION_ERROR("VALIDATION_ERROR", "入力値が不正です"),
    UNAUTHENTICATED("UNAUTHENTICATED", "認証が必要です"),
    FORBIDDEN("FORBIDDEN", "アクセス権限がありません"),
    NOT_FOUND("NOT_FOUND", "リソースが見つかりません"),
    CONFLICT("CONFLICT", "競合が発生しました"),
    INTERNAL_ERROR("INTERNAL_ERROR", "サーバー内部エラーが発生しました"),
    NOT_IMPLEMENTED("NOT_IMPLEMENTED", "この機能は未実装です");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() {
        return code;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}

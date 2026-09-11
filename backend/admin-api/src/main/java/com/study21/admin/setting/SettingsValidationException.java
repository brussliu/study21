package com.study21.admin.setting;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ValidationException;

import java.util.List;

/**
 * 設定検証失敗。欠落・不正な設定項目を一括で保持する。
 */
public class SettingsValidationException extends ValidationException {

    private final List<String> details;

    public SettingsValidationException(List<String> details) {
        super(String.join("; ", details));
        this.details = List.copyOf(details);
    }

    public List<String> getDetails() {
        return details;
    }

    @Override
    public ErrorCode getErrorCode() {
        return ErrorCode.VALIDATION_ERROR;
    }
}

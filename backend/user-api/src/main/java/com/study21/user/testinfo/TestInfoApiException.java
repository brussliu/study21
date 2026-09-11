package com.study21.user.testinfo;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class TestInfoApiException extends ApiException {
    private TestInfoApiException(ErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }

    public static TestInfoApiException invalid(String message) {
        return new TestInfoApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    public static TestInfoApiException notFound() {
        return new TestInfoApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "テスト情報が見つかりません。");
    }

    public static TestInfoApiException conflict(String message) {
        return new TestInfoApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}

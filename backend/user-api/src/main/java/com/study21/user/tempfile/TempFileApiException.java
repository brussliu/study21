package com.study21.user.tempfile;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class TempFileApiException extends ApiException {
    private TempFileApiException(ErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }

    public static TempFileApiException invalid(String message) {
        return new TempFileApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    public static TempFileApiException notFound() {
        return new TempFileApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "臨時ファイルが見つかりません。");
    }

    public static TempFileApiException conflict(String message) {
        return new TempFileApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}

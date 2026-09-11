package com.study21.user.document;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class DocumentApiException extends ApiException {
    private DocumentApiException(ErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }

    public static DocumentApiException invalid(String message) {
        return new DocumentApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    public static DocumentApiException notFound() {
        return new DocumentApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "資料が見つかりません。");
    }

    public static DocumentApiException conflict(String message) {
        return new DocumentApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}

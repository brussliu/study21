package com.study21.common.core.exception;

import com.study21.common.core.api.ErrorCode;
import org.springframework.http.HttpStatus;

/**
 * API 异常基类。
 */
public abstract class ApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;

    protected ApiException(ErrorCode errorCode, HttpStatus status, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
    }

    protected ApiException(ErrorCode errorCode, HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.status = status;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }
}

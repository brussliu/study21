package com.study21.common.core.exception;

import com.study21.common.core.api.ErrorCode;
import org.springframework.http.HttpStatus;

public class InternalErrorException extends ApiException {

    public InternalErrorException(String message) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }

    public InternalErrorException(String message, Throwable cause) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message, cause);
    }
}

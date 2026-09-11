package com.study21.common.security.exception;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 未认证异常（401）。
 */
public class UnauthenticatedException extends ApiException {

    public UnauthenticatedException() {
        this("認証が必要です");
    }

    public UnauthenticatedException(String message) {
        super(ErrorCode.UNAUTHENTICATED, HttpStatus.UNAUTHORIZED, message);
    }
}

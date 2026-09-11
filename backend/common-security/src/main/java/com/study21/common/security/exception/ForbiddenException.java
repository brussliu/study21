package com.study21.common.security.exception;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 无权限异常（403）。
 */
public class ForbiddenException extends ApiException {

    public ForbiddenException() {
        this("アクセス権限がありません");
    }

    public ForbiddenException(String message) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }
}

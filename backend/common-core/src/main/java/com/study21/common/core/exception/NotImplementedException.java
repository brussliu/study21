package com.study21.common.core.exception;

import com.study21.common.core.api.ErrorCode;
import org.springframework.http.HttpStatus;

public class NotImplementedException extends ApiException {

    public NotImplementedException() {
        this("この機能は未実装です");
    }

    public NotImplementedException(String message) {
        super(ErrorCode.NOT_IMPLEMENTED, HttpStatus.NOT_IMPLEMENTED, message);
    }
}

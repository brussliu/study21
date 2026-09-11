package com.study21.user.linkclip;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

public class LinkClipApiException extends ApiException {
    private LinkClipApiException(ErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }

    public static LinkClipApiException invalid(String message) {
        return new LinkClipApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    public static LinkClipApiException notFound() {
        return new LinkClipApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, "リンククリップが見つかりません。");
    }

    public static LinkClipApiException conflict(String message) {
        return new LinkClipApiException(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
    }
}

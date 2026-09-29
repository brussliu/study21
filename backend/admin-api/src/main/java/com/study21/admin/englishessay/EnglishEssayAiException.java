package com.study21.admin.englishessay;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 英作文 AI の呼び出し・応答の失敗（日本語の理由をそのまま画面へ返す）。
 *
 * <p>入力の誤り（枚数・大きさ・級）は {@code ValidationException}（400）、
 * <b>AI 側の失敗・応答が読めない</b>はこれ（502）にして、画面が「入力の問題」と
 * 「AI の問題」を区別できるようにする。</p>
 */
public class EnglishEssayAiException extends ApiException {

    public EnglishEssayAiException(String message) {
        super(ErrorCode.INTERNAL_ERROR, HttpStatus.BAD_GATEWAY, message);
    }
}

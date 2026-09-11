package com.study21.user.account;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 新規登録時の重複・競合エラー（409）。
 * どの項目で衝突したかをフロントエンドに伝えるため、フィールド名を持つ。
 */
public class RegisterConflictException extends ApiException {

    private final String field;

    /**
     * @param field   衝突した入力項目（parentEmail / studentEmail）
     * @param message ユーザー向け日本語メッセージ
     */
    public RegisterConflictException(String field, String message) {
        super(ErrorCode.CONFLICT, HttpStatus.CONFLICT, message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}

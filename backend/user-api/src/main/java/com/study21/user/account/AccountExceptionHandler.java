package com.study21.user.account;

import com.study21.common.core.api.ApiResponse;
import com.study21.common.core.api.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * アカウント関連の例外ハンドラ。
 * 登録時のメール重複（409）は、どの項目で衝突したかを data.field で返す。
 */
@RestControllerAdvice
public class AccountExceptionHandler {

    @ExceptionHandler(RegisterConflictException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleRegisterConflict(RegisterConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ErrorCode.CONFLICT.code(), ex.getMessage(), Map.of("field", ex.getField())));
    }
}

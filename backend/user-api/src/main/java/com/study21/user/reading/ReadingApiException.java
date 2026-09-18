package com.study21.user.reading;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 読書管理のファイル（本文 PDF・表紙）まわりの例外。
 *
 * <p>ストレージの解決失敗（行はあるが実体が無い・legacy ルート未設定）は 404 にして、
 * 画面が「PDF 未登録」を出せるようにする。Range が不正・範囲外のときは 416。</p>
 */
public class ReadingApiException extends ApiException {

    private ReadingApiException(ErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }

    /** 入力が不正（PDF 以外・大きすぎる・画像以外 など）。 */
    public static ReadingApiException invalid(String message) {
        return new ReadingApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    /** 行が無い／実体が無い／legacy ルート未設定。画面は「PDF 未登録」を出す。 */
    public static ReadingApiException notFound(String message) {
        return new ReadingApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    /** Range が不正・範囲外（RFC 9110 の 416）。 */
    public static ReadingApiException rangeNotSatisfiable() {
        return new ReadingApiException(ErrorCode.VALIDATION_ERROR,
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "要求された範囲（Range）が不正です。");
    }
}

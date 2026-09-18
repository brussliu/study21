package com.study21.user.classroom;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 授業録音 / AI 授業記録の例外。
 *
 * <p>見えない記録は 404（存在を漏らさない）、見えるが操作できない（保護者・管理者が他人の録音を
 * 消そうとした）ときは 403。Range が不正・範囲外のときは 416（RFC 9110）。</p>
 */
public class ClassroomApiException extends ApiException {

    private ClassroomApiException(ErrorCode code, HttpStatus status, String message) {
        super(code, status, message);
    }

    /** 入力が不正（言語モード・連番・上限超過 など）。 */
    public static ClassroomApiException invalid(String message) {
        return new ClassroomApiException(ErrorCode.VALIDATION_ERROR, HttpStatus.BAD_REQUEST, message);
    }

    /** 行が無い／実体が無い。画面は「録音が見つかりません」を出す。 */
    public static ClassroomApiException notFound(String message) {
        return new ClassroomApiException(ErrorCode.NOT_FOUND, HttpStatus.NOT_FOUND, message);
    }

    /** 見えるが操作できない（他人の録音の削除）。 */
    public static ClassroomApiException forbidden(String message) {
        return new ClassroomApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }

    /** Range が不正・範囲外（RFC 9110 の 416）。 */
    public static ClassroomApiException rangeNotSatisfiable() {
        return new ClassroomApiException(ErrorCode.VALIDATION_ERROR,
                HttpStatus.REQUESTED_RANGE_NOT_SATISFIABLE, "要求された範囲（Range）が不正です。");
    }
}

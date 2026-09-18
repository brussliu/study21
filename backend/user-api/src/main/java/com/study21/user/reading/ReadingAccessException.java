package com.study21.user.reading;

import com.study21.common.core.api.ErrorCode;
import com.study21.common.core.exception.ApiException;
import org.springframework.http.HttpStatus;

/**
 * 読書管理の権限（ロールと所有）の例外。
 *
 * <p>2026-09-14 の決定で、読書管理だけロールで機能を分ける（`docs/SECURITY_AND_ROLES.md`
 * §2.1「当面ロールで分けない」の例外）。見えない本は {@link com.study21.common.core.exception.NotFoundException}
 * （404）にして本の存在を漏らさず、**見えるが操作できない**ときだけこの 403 を返す。</p>
 */
public class ReadingAccessException extends ApiException {

    private ReadingAccessException(String message) {
        super(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN, message);
    }

    /** ロール不足（生徒の登録・修正・削除など）。 */
    public static ReadingAccessException roleNotAllowed(String action) {
        return new ReadingAccessException("この操作（" + action + "）を行う権限がありません。");
    }

    /** 所有違い（他の家庭の本・全体書籍を保護者が直そうとした など）。 */
    public static ReadingAccessException notOwner(String action) {
        return new ReadingAccessException("この本は自分の家庭の書籍ではないため、" + action + "はできません。");
    }
}

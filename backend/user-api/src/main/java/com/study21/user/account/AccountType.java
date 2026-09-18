package com.study21.user.account;

/**
 * アカウント種別。common-security の Role の GUARDIAN / STUDENT / ADMIN に対応する。
 *
 * <p>もともと user-api は一般ユーザー（保護者・生徒）専用で ADMIN を持っていなかったが、
 * 2026-09-14 の決定（Q8）で**読書管理の全体書籍を管理者が管理できる**ようにするため
 * ADMIN を足した。管理者の管理画面（admin-api）とは別に、user-api でも
 * セッションを持てるようにしてある（読書の業務ロジックを 1 か所に保つため）。</p>
 */
public enum AccountType {

    GUARDIAN("GUARDIAN"),
    STUDENT("STUDENT"),
    /** 管理者（有効期限・学年・保護者ID は持たない。DDL の CK_ACC_有効期限 を参照）。 */
    ADMIN("ADMIN");

    private final String code;

    AccountType(String code) {
        this.code = code;
    }

    public String code() {
        return code;
    }

    /**
     * 保存値（コード）から対応する種別を返す。未知の値は null。
     */
    public static AccountType fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (AccountType type : values()) {
            if (type.code.equals(code)) {
                return type;
            }
        }
        return null;
    }
}

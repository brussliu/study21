package com.study21.user.account;

/**
 * アカウント種別。common-security の Role のうち GUARDIAN / STUDENT に対応する。
 */
public enum AccountType {

    GUARDIAN("GUARDIAN"),
    STUDENT("STUDENT");

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

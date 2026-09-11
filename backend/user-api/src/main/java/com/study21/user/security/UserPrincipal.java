package com.study21.user.security;

import com.study21.user.account.AccountType;

import java.io.Serial;
import java.io.Serializable;

/** user-api のサーバーセッションに保存する認証済み一般ユーザー。 */
public record UserPrincipal(long accountId, String loginId, String displayName, AccountType accountType)
        implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
}

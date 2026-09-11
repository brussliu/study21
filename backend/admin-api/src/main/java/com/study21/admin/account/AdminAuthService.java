package com.study21.admin.account;

/**
 * 管理端ログインサービス。
 */
public interface AdminAuthService {

    /**
     * ADMIN アカウントを認証し、ログイン情報を返す。
     * 認証失敗時は {@link com.study21.common.security.exception.UnauthenticatedException} を送出する。
     */
    AdminLoginResponse login(AdminLoginRequest request);
}

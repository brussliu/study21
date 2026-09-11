package com.study21.user.account;

/**
 * パスワード再設定サービス。
 */
public interface PasswordResetService {

    /**
     * パスワード再設定を依頼し、トークンを発行する。
     * アカウントの存在有無は応答に漏らさない。
     */
    void requestReset(String loginId);

    /**
     * トークンを検証して新しいパスワードを設定する。
     * トークンが無効・期限切れの場合は {@link com.study21.common.security.exception.UnauthenticatedException} を送出する。
     */
    void confirmReset(String token, String newPassword);
}

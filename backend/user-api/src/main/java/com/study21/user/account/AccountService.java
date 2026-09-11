package com.study21.user.account;

/**
 * 保護者・生徒アカウントの登録・ログイン処理。
 */
public interface AccountService {

    /**
     * 保護者アカウント + 初期生徒アカウントを同時に作成する（トランザクション）。
     *
     * @throws RegisterConflictException メール重複・保護者と生徒のメール一致（409）
     */
    RegisterResponse register(RegisterRequest request);

    /**
     * メールアドレス + パスワードで認証する。
     *
     * @throws com.study21.common.security.exception.UnauthenticatedException 認証失敗・停止中・期限切れ（401）
     */
    LoginResponse login(LoginRequest request);
}

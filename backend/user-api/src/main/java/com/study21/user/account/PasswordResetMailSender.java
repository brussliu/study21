package com.study21.user.account;

/**
 * パスワード再設定メール送信の抽象。
 * メール基盤が未接続でも動作するよう、既定実装はログ出力に留める。
 */
public interface PasswordResetMailSender {

    /**
     * 再設定トークンを含むメールを送信する。
     *
     * @param email 送信先メールアドレス
     * @param token 再設定トークン（平文）
     */
    void sendResetMail(String email, String token);
}

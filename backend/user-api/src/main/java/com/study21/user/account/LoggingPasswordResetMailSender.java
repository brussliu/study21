package com.study21.user.account;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 開発用のパスワード再設定メール送信実装。
 * SMTP 等のメール基盤が未接続のため、トークンをログに出力する。
 */
@Component
public class LoggingPasswordResetMailSender implements PasswordResetMailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingPasswordResetMailSender.class);

    @Override
    public void sendResetMail(String email, String token) {
        // TODO: 本番では SMTP 連携実装（Spring Mail 等）に差し替える。
        log.info("[password-reset] 再設定トークン発行: email={}, token={}", email, token);
    }
}

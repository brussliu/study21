package com.study21.user.account;

import com.study21.common.security.exception.UnauthenticatedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * パスワード再設定サービスの実装。
 *
 * <p>トークンは平文を保存せず SHA-256 ハッシュのみ保存し、有効期限（30 分）で
 * 無効化する。依頼時はアカウントの有無を応答に漏らさない。</p>
 */
@Service
public class PasswordResetServiceImpl implements PasswordResetService {

    private static final Duration TOKEN_TTL = Duration.ofMinutes(30);
    private static final String OPERATOR = "PASSWORD_RESET";
    private static final String UNUSED = "0";

    private final AccountMapper accountMapper;
    private final PasswordResetMapper passwordResetMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetMailSender mailSender;

    public PasswordResetServiceImpl(AccountMapper accountMapper,
                                    PasswordResetMapper passwordResetMapper,
                                    PasswordEncoder passwordEncoder,
                                    PasswordResetMailSender mailSender) {
        this.accountMapper = accountMapper;
        this.passwordResetMapper = passwordResetMapper;
        this.passwordEncoder = passwordEncoder;
        this.mailSender = mailSender;
    }

    @Override
    @Transactional
    public void requestReset(String loginId) {
        String normalized = normalizeEmail(loginId);
        AccountEntity account = accountMapper.findByLoginId(normalized);

        // 存在しない・無効なアカウントでも応答を区別しない（メール列挙防止）。
        if (account == null || !"1".equals(account.getStatus())) {
            return;
        }

        String token = generateToken();
        PasswordResetEntity entity = new PasswordResetEntity();
        entity.setAccountId(account.getAccountId());
        entity.setTokenHash(sha256Hex(token));
        entity.setExpiresAt(Timestamp.valueOf(LocalDateTime.now().plus(TOKEN_TTL)));
        entity.setUsedFlag(UNUSED);
        entity.setCreatedBy(OPERATOR);
        entity.setUpdatedBy(OPERATOR);
        passwordResetMapper.insert(entity);

        mailSender.sendResetMail(normalized, token);
    }

    @Override
    @Transactional
    public void confirmReset(String token, String newPassword) {
        PasswordResetEntity reset = passwordResetMapper.findByTokenHash(sha256Hex(token));
        if (reset == null || !isUsable(reset)) {
            throw new UnauthenticatedException("再設定用トークンが無効または期限切れです。もう一度お手続きください。");
        }

        int updated = accountMapper.updatePasswordHash(reset.getAccountId(), passwordEncoder.encode(newPassword));
        if (updated == 0) {
            throw new UnauthenticatedException("対象のアカウントが見つかりません。");
        }
        passwordResetMapper.markUsed(reset.getResetId(), OPERATOR);
    }

    private boolean isUsable(PasswordResetEntity reset) {
        if (!UNUSED.equals(reset.getUsedFlag())) {
            return false;
        }
        if (reset.getExpiresAt() == null) {
            return false;
        }
        return reset.getExpiresAt().toLocalDateTime().isAfter(LocalDateTime.now());
    }

    private String generateToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

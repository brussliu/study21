package com.study21.admin.account;

import com.study21.common.security.exception.UnauthenticatedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Locale;

/**
 * 管理端ログインサービスの実装。
 *
 * <p>ACC_アカウント の ADMIN 種別のみを認証対象とする。アカウント種別の
 * フィルタを SQL 側でも行い、一般ユーザー（GUARDIAN/STUDENT）の認証を防ぐ。</p>
 */
@Service
public class AdminAuthServiceImpl implements AdminAuthService {

    private static final String STATUS_ACTIVE = "1";
    private static final String ROLE_ADMIN = "ADMIN";

    private final AdminAccountMapper adminAccountMapper;
    private final PasswordEncoder passwordEncoder;

    public AdminAuthServiceImpl(AdminAccountMapper adminAccountMapper, PasswordEncoder passwordEncoder) {
        this.adminAccountMapper = adminAccountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public AdminLoginResponse login(AdminLoginRequest request) {
        String loginId = normalizeLoginId(request.getLoginId());
        AdminAccountEntity account = adminAccountMapper.findByLoginId(loginId);

        if (account == null || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new UnauthenticatedException("ユーザーIDまたはパスワードが正しくありません。");
        }
        if (!STATUS_ACTIVE.equals(account.getStatus())) {
            throw new UnauthenticatedException("このアカウントは利用停止中です。");
        }
        Date expiry = account.getExpiryDate();
        if (expiry != null && expiry.toLocalDate().isBefore(LocalDate.now())) {
            throw new UnauthenticatedException("利用期限が切れています。");
        }

        String displayName = String.join(" ",
                account.getSei() == null ? "" : account.getSei(),
                account.getMei() == null ? "" : account.getMei()).trim();
        LocalDate expiryDate = expiry == null ? null : expiry.toLocalDate();

        return new AdminLoginResponse(account.getAccountId(), account.getLoginId(),
                displayName, ROLE_ADMIN, expiryDate);
    }

    private String normalizeLoginId(String loginId) {
        return loginId.trim().toLowerCase(Locale.ROOT);
    }
}

package com.study21.user.account;

import com.study21.common.security.exception.UnauthenticatedException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Date;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AccountServiceImplTest {

    private AccountMapper accountMapper;
    private PasswordEncoder passwordEncoder;
    private AccountServiceImpl service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AccountServiceImpl(accountMapper, passwordEncoder);
    }

    @Test
    void studentCanLoginWithNormalizedEmail() {
        AccountEntity account = activeAccount("student@example.com", "STUDENT");
        when(accountMapper.findByLoginIdForLogin("student@example.com")).thenReturn(account);
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        LoginResponse response = service.login(loginRequest(" Student@Example.com ", "secret123"));

        assertThat(response.getAccountId()).isEqualTo(10L);
        assertThat(response.getLoginId()).isEqualTo("student@example.com");
        assertThat(response.getDisplayName()).isEqualTo("山田 太郎");
        assertThat(response.getAccountType()).isEqualTo(AccountType.STUDENT);
        assertThat(response.getExpiryDate()).isEqualTo(LocalDate.now().plusDays(1));
        // ログインは管理者も返す検索を使う（パスワード再設定などの画面は findByLoginId のまま）
        verify(accountMapper).findByLoginIdForLogin("student@example.com");
    }

    @Test
    void guardianCanLogin() {
        AccountEntity account = activeAccount("guardian@example.com", "GUARDIAN");
        when(accountMapper.findByLoginIdForLogin("guardian@example.com")).thenReturn(account);
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        LoginResponse response = service.login(loginRequest("guardian@example.com", "secret123"));

        assertThat(response.getAccountType()).isEqualTo(AccountType.GUARDIAN);
    }

    @Test
    void invalidPasswordDoesNotRevealWhetherAccountExists() {
        AccountEntity account = activeAccount("student@example.com", "STUDENT");
        when(accountMapper.findByLoginIdForLogin("student@example.com")).thenReturn(account);
        when(passwordEncoder.matches("wrong-password", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.login(loginRequest("student@example.com", "wrong-password")))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("メールアドレスまたはパスワードが正しくありません。");
    }

    @Test
    void inactiveAccountCannotLogin() {
        AccountEntity account = activeAccount("student@example.com", "STUDENT");
        account.setStatus("0");
        when(accountMapper.findByLoginIdForLogin("student@example.com")).thenReturn(account);
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(loginRequest("student@example.com", "secret123")))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("利用停止中");
    }

    @Test
    void expiredAccountCannotLogin() {
        AccountEntity account = activeAccount("student@example.com", "STUDENT");
        account.setExpiryDate(Date.valueOf(LocalDate.now().minusDays(1)));
        when(accountMapper.findByLoginIdForLogin("student@example.com")).thenReturn(account);
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(loginRequest("student@example.com", "secret123")))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("利用期限が切れています");
    }

    @Test
    void accountWithoutExpiryCannotBecomeUnlimitedUser() {
        AccountEntity account = activeAccount("student@example.com", "STUDENT");
        account.setExpiryDate(null);
        when(accountMapper.findByLoginIdForLogin("student@example.com")).thenReturn(account);
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(loginRequest("student@example.com", "secret123")))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("利用できません");
    }

    /**
     * 管理者も user-api でログインできる（2026-09-14 の決定 Q8）。
     * 有効期限を持たない（無期限）ので、期限の検査は一般ユーザーだけに行う。
     */
    @Test
    void adminCanLoginWithoutExpiry() {
        AccountEntity account = activeAccount("admin@example.com", "ADMIN");
        account.setExpiryDate(null);
        when(accountMapper.findByLoginIdForLogin("admin@example.com")).thenReturn(account);
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        LoginResponse response = service.login(loginRequest("admin@example.com", "secret123"));

        assertThat(response.getAccountType()).isEqualTo(AccountType.ADMIN);
        assertThat(response.getExpiryDate()).as("管理者は無期限").isNull();
    }

    /** 未知の種別は認証しない（ID の存在も漏らさない）。 */
    @Test
    void unknownAccountTypeIsRejected() {
        AccountEntity account = activeAccount("someone@example.com", "TEACHER");
        when(accountMapper.findByLoginIdForLogin("someone@example.com")).thenReturn(account);
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.login(loginRequest("someone@example.com", "secret123")))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessage("メールアドレスまたはパスワードが正しくありません。");
    }

    private AccountEntity activeAccount(String loginId, String type) {
        AccountEntity account = new AccountEntity();
        account.setAccountId(10L);
        account.setLoginId(loginId);
        account.setPasswordHash("hash");
        account.setAccountType(type);
        account.setStatus("1");
        account.setSei("山田");
        account.setMei("太郎");
        account.setExpiryDate(Date.valueOf(LocalDate.now().plusDays(1)));
        return account;
    }

    private LoginRequest loginRequest(String loginId, String password) {
        LoginRequest request = new LoginRequest();
        request.setLoginId(loginId);
        request.setPassword(password);
        return request;
    }
}

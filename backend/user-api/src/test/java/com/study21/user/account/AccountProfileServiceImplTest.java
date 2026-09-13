package com.study21.user.account;

import com.study21.common.core.exception.ValidationException;
import com.study21.common.security.exception.UnauthenticatedException;
import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 「ユーザー情報の修正」「パスワード変更」の業務ルール。
 * DB はモックし、種別ごとの必須項目・本人確認・保存値の変換を固定する。
 */
class AccountProfileServiceImplTest {

    private static final long ACCOUNT_ID = 42L;

    private AccountMapper accountMapper;
    private PasswordEncoder passwordEncoder;
    private AccountProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        accountMapper = mock(AccountMapper.class);
        passwordEncoder = mock(PasswordEncoder.class);
        service = new AccountProfileServiceImpl(accountMapper, passwordEncoder);
    }

    private AccountEntity account(String type) {
        AccountEntity entity = new AccountEntity();
        entity.setAccountId(ACCOUNT_ID);
        entity.setLoginId("user@example.com");
        entity.setPasswordHash("hash");
        entity.setAccountType(type);
        entity.setStatus("1");
        entity.setSei("山田");
        entity.setMei("太郎");
        entity.setSeiKana("やまだ");
        entity.setMeiKana("たろう");
        entity.setGrade(AccountType.STUDENT.code().equals(type) ? "中学1年生" : null);
        entity.setMailNotify("1");
        entity.setReminderNotify("0");
        entity.setExpiryDate(Date.valueOf(LocalDate.now().plusDays(1)));
        entity.setTermsAgreedAt(new Timestamp(System.currentTimeMillis()));
        return entity;
    }

    private UserPrincipal user(String type) {
        return new UserPrincipal(ACCOUNT_ID, "user@example.com", "山田 太郎", AccountType.fromCode(type));
    }

    private ProfileUpdateRequest request() {
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setSei("  鈴木  ");
        request.setMei("花子");
        request.setSeiKana("すずき");
        request.setMeiKana("はなこ");
        request.setPhone(" 090-1111-2222 ");
        request.setMailNotify(false);
        request.setReminderNotify(true);
        return request;
    }

    @Test
    void profileReturnsCurrentValuesWithReadOnlyEmail() {
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(account("STUDENT"));

        UserProfileResponse response = service.getProfile(user("STUDENT"));

        assertThat(response.getEmail()).isEqualTo("user@example.com");
        assertThat(response.getSei()).isEqualTo("山田");
        assertThat(response.getDisplayName()).isEqualTo("山田 太郎");
        assertThat(response.getGrade()).isEqualTo("中学1年生");
        assertThat(response.isMailNotify()).isTrue();
        assertThat(response.isReminderNotify()).isFalse();
    }

    @Test
    void missingAccountAsksForLoginAgain() {
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.getProfile(user("STUDENT")))
                .isInstanceOf(UnauthenticatedException.class)
                .hasMessageContaining("再度ログイン");
    }

    @Test
    void studentUpdateTrimsValuesAndConvertsFlags() {
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(account("STUDENT"));

        ProfileUpdateRequest request = request();
        request.setGrade(" 中学2年生 ");
        service.updateProfile(user("STUDENT"), request);

        verify(accountMapper).updateProfile(any(AccountEntity.class));
        AccountEntity saved = capturedEntity();
        assertThat(saved.getSei()).isEqualTo("鈴木");
        assertThat(saved.getMei()).isEqualTo("花子");
        assertThat(saved.getPhone()).isEqualTo("090-1111-2222");
        assertThat(saved.getGrade()).isEqualTo("中学2年生");
        assertThat(saved.getMailNotify()).isEqualTo("0");
        assertThat(saved.getReminderNotify()).isEqualTo("1");
        assertThat(saved.getUpdatedBy()).isEqualTo("SELF");
        // メールアドレス（ログインID）は更新対象ではない
        assertThat(saved.getLoginId()).isEqualTo("user@example.com");
    }

    @Test
    void studentWithoutKanaIsRejected() {
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(account("STUDENT"));
        ProfileUpdateRequest request = request();
        request.setSeiKana("   ");
        request.setGrade("中学1年生");

        assertThatThrownBy(() -> service.updateProfile(user("STUDENT"), request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("ふりがな（せい）");
        verify(accountMapper, never()).updateProfile(any(AccountEntity.class));
    }

    @Test
    void studentWithoutGradeIsRejected() {
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(account("STUDENT"));
        ProfileUpdateRequest request = request();
        request.setGrade("");

        assertThatThrownBy(() -> service.updateProfile(user("STUDENT"), request))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("学年");
    }

    @Test
    void guardianGradeIsIgnoredAndEmptyValuesBecomeNull() {
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(account("GUARDIAN"));
        ProfileUpdateRequest request = request();
        request.setGrade("中学1年生"); // 保護者は学年を持たない（送られても無視する）
        request.setPhone("   ");
        request.setMeiKana("  ");

        service.updateProfile(user("GUARDIAN"), request);

        AccountEntity saved = capturedEntity();
        assertThat(saved.getGrade()).isNull();
        assertThat(saved.getPhone()).isNull();
        assertThat(saved.getMeiKana()).isNull();
    }

    @Test
    void guardianDoesNotNeedKana() {
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(account("GUARDIAN"));
        ProfileUpdateRequest request = request();
        request.setSeiKana("");
        request.setMeiKana("");

        service.updateProfile(user("GUARDIAN"), request);

        AccountEntity saved = capturedEntity();
        assertThat(saved.getSeiKana()).isNull();
        assertThat(saved.getMeiKana()).isNull();
    }

    private AccountEntity capturedEntity() {
        var captor = org.mockito.ArgumentCaptor.forClass(AccountEntity.class);
        verify(accountMapper).updateProfile(captor.capture());
        return captor.getValue();
    }

    @Test
    void passwordChangeRejectsWrongCurrentPassword() {
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(account("GUARDIAN"));
        when(passwordEncoder.matches("wrong-pass1", "hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(user("GUARDIAN"), changeRequest("wrong-pass1", "new-pass123")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("現在のパスワード");
        verify(accountMapper, never()).updatePasswordHashBySelf(anyLong(), any(), any());
    }

    @Test
    void passwordChangeRejectsSameAsCurrent() {
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(account("GUARDIAN"));
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(user("GUARDIAN"), changeRequest("secret123", "secret123")))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("違うもの");
        verify(accountMapper, never()).updatePasswordHashBySelf(anyLong(), any(), any());
    }

    @Test
    void passwordChangeStoresBcryptHashAndKeepsLoginId() {
        AccountEntity current = account("GUARDIAN");
        when(accountMapper.findById(ACCOUNT_ID)).thenReturn(current);
        when(passwordEncoder.matches("secret123", "hash")).thenReturn(true);
        when(passwordEncoder.matches("newpass123", "hash")).thenReturn(false);
        when(passwordEncoder.encode("newpass123")).thenReturn("bcrypt-hash");

        service.changePassword(user("GUARDIAN"), changeRequest("secret123", "newpass123"));

        verify(accountMapper).updatePasswordHashBySelf(eq(ACCOUNT_ID), eq("bcrypt-hash"), eq("SELF"));
        verify(accountMapper, never()).updateProfile(any(AccountEntity.class));
        assertThat(current.getLoginId()).isEqualTo("user@example.com");
    }

    private PasswordChangeRequest changeRequest(String current, String next) {
        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword(current);
        request.setNewPassword(next);
        return request;
    }
}

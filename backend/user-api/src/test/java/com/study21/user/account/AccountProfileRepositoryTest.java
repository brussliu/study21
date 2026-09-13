package com.study21.user.account;

import com.study21.user.security.UserPrincipal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 実 DB（PostgreSQL）に対する「ユーザー情報の修正」「パスワード変更」の検証。
 *
 * 列名・CHECK 制約・メールアドレス（ログインID）が変わらないことを、
 * モックではなく実際の SQL で確かめる。テストはロールバックするので DB は汚れない。
 *
 * 実行には DB のパスワードが要る（テスト環境の既定は空のため、無いときはスキップする）:
 *   STUDY21_DATASOURCE_PASSWORD=... mvn -pl user-api -am test
 */
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "STUDY21_DATASOURCE_PASSWORD", matches = ".+",
        disabledReason = "DB のパスワード（STUDY21_DATASOURCE_PASSWORD）が未設定のためスキップ")
class AccountProfileRepositoryTest {

    @Autowired
    private AccountMapper accountMapper;

    @Autowired
    private AccountProfileService profileService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void profileRoundTripStoresEditableColumnsAndKeepsEmail() {
        AccountEntity guardian = insert("profile-guardian-" + System.nanoTime() + "@example.com", "GUARDIAN", null, null);
        UserPrincipal user = principal(guardian);

        // 既定値（メール通知 '1' / リマインダー通知 '0'）が DB から読める
        UserProfileResponse before = profileService.getProfile(user);
        assertThat(before.getEmail()).isEqualTo(guardian.getLoginId());
        assertThat(before.isMailNotify()).isTrue();
        assertThat(before.isReminderNotify()).isFalse();
        assertThat(before.getGrade()).isNull();

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setSei("鈴木");
        request.setMei("花子");
        request.setSeiKana("すずき");
        request.setMeiKana("はなこ");
        request.setPhone("090-1111-2222");
        request.setMailNotify(false);
        request.setReminderNotify(true);
        request.setGrade("中学1年生"); // 保護者なので無視される

        UserProfileResponse updated = profileService.updateProfile(user, request);

        assertThat(updated.getDisplayName()).isEqualTo("鈴木 花子");
        assertThat(updated.getPhone()).isEqualTo("090-1111-2222");
        assertThat(updated.getGrade()).isNull();

        AccountEntity raw = accountMapper.findById(guardian.getAccountId());
        assertThat(raw.getLoginId()).isEqualTo(guardian.getLoginId()); // メールアドレスは不変
        assertThat(raw.getSei()).isEqualTo("鈴木");
        assertThat(raw.getMei()).isEqualTo("花子");
        assertThat(raw.getMailNotify()).isEqualTo("0");
        assertThat(raw.getReminderNotify()).isEqualTo("1");
        assertThat(raw.getUpdatedBy()).isEqualTo("SELF");
    }

    @Test
    void studentProfileStoresGradeAndKana() {
        AccountEntity guardian = insert("profile-parent-" + System.nanoTime() + "@example.com", "GUARDIAN", null, null);
        AccountEntity student = insert("profile-student-" + System.nanoTime() + "@example.com", "STUDENT",
                guardian.getAccountId(), "中学1年生");

        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setSei("山田");
        request.setMei("太郎");
        request.setSeiKana("やまだ");
        request.setMeiKana("たろう");
        request.setGrade("中学2年生");
        request.setMailNotify(true);
        request.setReminderNotify(false);

        UserProfileResponse updated = profileService.updateProfile(principal(student), request);

        assertThat(updated.getGrade()).isEqualTo("中学2年生");
        assertThat(updated.getSeiKana()).isEqualTo("やまだ");
        AccountEntity raw = accountMapper.findById(student.getAccountId());
        assertThat(raw.getGrade()).isEqualTo("中学2年生");
        assertThat(raw.getLoginId()).isEqualTo(student.getLoginId());
    }

    @Test
    void passwordChangeReplacesHashAndKeepsLoginId() {
        AccountEntity guardian = insert("profile-pass-" + System.nanoTime() + "@example.com", "GUARDIAN", null, null);
        accountMapper.updatePasswordHash(guardian.getAccountId(), passwordEncoder.encode("secret123"));

        PasswordChangeRequest request = new PasswordChangeRequest();
        request.setCurrentPassword("secret123");
        request.setNewPassword("newpass123");

        profileService.changePassword(principal(guardian), request);

        AccountEntity raw = accountMapper.findById(guardian.getAccountId());
        assertThat(passwordEncoder.matches("newpass123", raw.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("secret123", raw.getPasswordHash())).isFalse();
        assertThat(raw.getLoginId()).isEqualTo(guardian.getLoginId());
        assertThat(raw.getUpdatedBy()).isEqualTo("SELF");
    }

    private UserPrincipal principal(AccountEntity entity) {
        return new UserPrincipal(entity.getAccountId(), entity.getLoginId(),
                UserProfileResponse.displayName(entity), AccountType.fromCode(entity.getAccountType()));
    }

    /** 検証用のアカウントを 1 件作る（テスト終了時にロールバックされる）。 */
    private AccountEntity insert(String email, String type, Long guardianId, String grade) {
        AccountEntity entity = new AccountEntity();
        entity.setLoginId(email);
        entity.setPasswordHash(passwordEncoder.encode("secret123"));
        entity.setAccountType(type);
        entity.setStatus("1");
        entity.setSei("試験");
        entity.setMei("太郎");
        entity.setSeiKana("しけん");
        entity.setMeiKana("たろう");
        entity.setGrade(grade);
        entity.setGuardianId(guardianId);
        entity.setExpiryDate(Date.valueOf(LocalDate.now().plusMonths(1)));
        entity.setTermsAgreedAt(new Timestamp(System.currentTimeMillis()));
        entity.setCreatedBy("SELF");
        entity.setUpdatedBy("SELF");
        accountMapper.insert(entity);
        assertThat(entity.getAccountId()).isNotNull();
        return entity;
    }
}

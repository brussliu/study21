package com.study21.user.account;

import com.study21.common.core.exception.ValidationException;
import com.study21.common.security.exception.UnauthenticatedException;
import com.study21.user.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 「ユーザー情報の修正」「パスワード変更」の実装。
 *
 * 方針:
 *  - メールアドレス（＝ログインID）は変更しない（リクエストに項目が無い）。
 *  - ふりがな・学年は生徒のみ必須。保護者は学年を扱わない（DB の CHECK に合わせる）。
 *  - パスワードは現在のものと照合してから BCrypt で再ハッシュ化する。
 */
@Service
public class AccountProfileServiceImpl implements AccountProfileService {

    /** 本人による操作であることを示す更新ID。 */
    private static final String SELF = "SELF";

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountProfileServiceImpl(AccountMapper accountMapper, PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserProfileResponse getProfile(UserPrincipal user) {
        return UserProfileResponse.from(loadAccount(user));
    }

    @Override
    @Transactional
    public UserProfileResponse updateProfile(UserPrincipal user, ProfileUpdateRequest request) {
        AccountEntity account = loadAccount(user);
        boolean student = AccountType.STUDENT.code().equals(account.getAccountType());

        String sei = trim(request.getSei());
        String mei = trim(request.getMei());
        if (sei.isEmpty()) {
            throw new ValidationException("姓を入力してください。");
        }
        if (mei.isEmpty()) {
            throw new ValidationException("名を入力してください。");
        }

        String seiKana = trim(request.getSeiKana());
        String meiKana = trim(request.getMeiKana());
        if (student) {
            if (seiKana.isEmpty()) {
                throw new ValidationException("ふりがな（せい）を入力してください。");
            }
            if (meiKana.isEmpty()) {
                throw new ValidationException("ふりがな（めい）を入力してください。");
            }
        }

        String grade = trim(request.getGrade());
        if (student) {
            if (grade.isEmpty()) {
                throw new ValidationException("学年を選択してください。");
            }
        } else {
            // 保護者・管理者は学年を持たない（DB の CHECK_ACC_学年種別 を守る）
            grade = "";
        }

        account.setSei(sei);
        account.setMei(mei);
        account.setSeiKana(nullIfEmpty(seiKana));
        account.setMeiKana(nullIfEmpty(meiKana));
        account.setGrade(nullIfEmpty(grade));
        account.setPhone(nullIfEmpty(trim(request.getPhone())));
        account.setMailNotify(flag(request.isMailNotify()));
        account.setReminderNotify(flag(request.isReminderNotify()));
        account.setUpdatedBy(SELF);

        accountMapper.updateProfile(account);
        // 更新後の状態を返す（更新日時なども DB の値で揃える）
        return UserProfileResponse.from(loadAccount(user));
    }

    @Override
    @Transactional
    public void changePassword(UserPrincipal user, PasswordChangeRequest request) {
        AccountEntity account = loadAccount(user);

        if (!passwordEncoder.matches(request.getCurrentPassword(), account.getPasswordHash())) {
            throw new ValidationException("現在のパスワードが正しくありません。");
        }
        if (passwordEncoder.matches(request.getNewPassword(), account.getPasswordHash())) {
            throw new ValidationException("新しいパスワードは現在のパスワードと違うものを入力してください。");
        }

        accountMapper.updatePasswordHashBySelf(
                account.getAccountId(),
                passwordEncoder.encode(request.getNewPassword()),
                SELF);
    }

    /** ログイン中のアカウントを読む。消えている場合はセッションが無効なので再ログインを促す。 */
    private AccountEntity loadAccount(UserPrincipal user) {
        AccountEntity account = accountMapper.findById(user.accountId());
        if (account == null) {
            throw new UnauthenticatedException("アカウントが見つかりません。再度ログインしてください。");
        }
        return account;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullIfEmpty(String value) {
        return value.isEmpty() ? null : value;
    }

    private static String flag(boolean on) {
        return on ? "1" : "0";
    }
}

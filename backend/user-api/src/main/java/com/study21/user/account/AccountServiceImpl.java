package com.study21.user.account;

import com.study21.common.security.exception.UnauthenticatedException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Locale;

/**
 * ACC_アカウント の登録・ログイン実装。
 *
 * <p>登録は「保護者 + 初期生徒」の2行を1トランザクションで作成する。
 * メールアドレスは小文字正規化して保存・照合し、
 * 大文字小文字の違いによる重複も防ぐ（DB 側の LOWER 一意索引と二重に防御）。</p>
 */
@Service
public class AccountServiceImpl implements AccountService {

    /** システム運用者ID（自己登録のため） */
    private static final String SELF = "SELF";
    /** 有効状態（既存ステータス規約: '1'=有効） */
    private static final String STATUS_ACTIVE = "1";

    private final AccountMapper accountMapper;
    private final PasswordEncoder passwordEncoder;

    public AccountServiceImpl(AccountMapper accountMapper, PasswordEncoder passwordEncoder) {
        this.accountMapper = accountMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String parentEmail = normalizeEmail(request.getParentEmail());
        String studentEmail = normalizeEmail(request.getStudentEmail());

        // 1) 保護者と生徒で同じメールアドレスは不可（フロントでも事前チェック済みだが、API でも防御）
        if (parentEmail.equalsIgnoreCase(studentEmail)) {
            throw new RegisterConflictException("studentEmail",
                    "保護者アカウントと同じメールアドレスはお子さま用に使用できません。別のメールアドレスを入力してください。");
        }
        // 2) システム全体での一意性（保護者メール）
        if (accountMapper.existsByLoginId(parentEmail) > 0) {
            throw new RegisterConflictException("parentEmail",
                    "このメールアドレスは既に登録されています。ログインをお試しください。");
        }
        // 3) システム全体での一意性（生徒メール）。保護者・生徒を問わず重複不可
        if (accountMapper.existsByLoginId(studentEmail) > 0) {
            throw new RegisterConflictException("studentEmail",
                    "このメールアドレスは既に使用されています。別のメールアドレスを入力してください。");
        }

        // 4) 有効期限 = 登録日 + 1ヶ月（月末の繰り上がりは LocalDate.plusMonths が自動クランプ）
        LocalDate expiry = LocalDate.now().plusMonths(1);

        // 5) 保護者アカウント作成
        AccountEntity guardian = buildAccount(request, parentEmail, expiry, AccountType.GUARDIAN, null, null);
        try {
            accountMapper.insert(guardian);
        } catch (DuplicateKeyException e) {
            // 並行登録等で一意索引に衝突した場合
            throw new RegisterConflictException("parentEmail",
                    "このメールアドレスは既に登録されています。ログインをお試しください。");
        }

        // 6) 初期生徒アカウント作成（保護者に紐づけ）
        AccountEntity student = buildAccount(request, studentEmail, expiry, AccountType.STUDENT,
                guardian.getAccountId(), request.getGrade().trim());
        try {
            accountMapper.insert(student);
        } catch (DuplicateKeyException e) {
            throw new RegisterConflictException("studentEmail",
                    "このメールアドレスは既に使用されています。別のメールアドレスを入力してください。");
        }

        return new RegisterResponse(guardian.getAccountId(), student.getAccountId(),
                parentEmail, studentEmail, expiry);
    }

    @Override
    public LoginResponse login(LoginRequest request) {
        String loginId = normalizeEmail(request.getLoginId());
        AccountEntity account = accountMapper.findByLoginId(loginId);

        // 存在しない場合もパスワード不一致の場合も同じメッセージ（ID の存在を漏らさない）
        if (account == null || !passwordEncoder.matches(request.getPassword(), account.getPasswordHash())) {
            throw new UnauthenticatedException("メールアドレスまたはパスワードが正しくありません。");
        }
        if (!STATUS_ACTIVE.equals(account.getStatus())) {
            throw new UnauthenticatedException("このアカウントは利用停止中です。管理者にお問い合わせください。");
        }
        AccountType type = AccountType.fromCode(account.getAccountType());
        if (type == null) {
            // user-api は一般ユーザー（保護者・生徒）専用。未知種別や管理者を認証しない。
            throw new UnauthenticatedException("メールアドレスまたはパスワードが正しくありません。");
        }
        Date expiry = account.getExpiryDate();
        if (expiry == null) {
            // 一般ユーザーの有効期限は必須。壊れたアカウントを無期限として扱わない。
            throw new UnauthenticatedException("このアカウントは利用できません。管理者にお問い合わせください。");
        }
        if (expiry.toLocalDate().isBefore(LocalDate.now())) {
            throw new UnauthenticatedException("利用期限が切れています。更新手続き（有料）が必要です。");
        }

        String displayName = String.join(" ",
                account.getSei() == null ? "" : account.getSei(),
                account.getMei() == null ? "" : account.getMei()).trim();
        return new LoginResponse(account.getAccountId(), account.getLoginId(), displayName,
                type, expiry.toLocalDate());
    }

    /** アカウントエンティティを組み立てる（パスワードは BCrypt ハッシュ化）。 */
    private AccountEntity buildAccount(RegisterRequest request, String loginId, LocalDate expiry,
                                       AccountType type, Long guardianId, String grade) {
        AccountEntity entity = new AccountEntity();
        entity.setLoginId(loginId);
        entity.setPasswordHash(passwordEncoder.encode(type == AccountType.GUARDIAN
                ? request.getParentPassword() : request.getStudentPassword()));
        entity.setAccountType(type.code());
        entity.setStatus(STATUS_ACTIVE);
        entity.setSei(request.getSei().trim());
        entity.setMei(request.getMei().trim());
        entity.setSeiKana(request.getSeiKana().trim());
        entity.setMeiKana(request.getMeiKana().trim());
        entity.setGrade(grade);
        entity.setGuardianId(guardianId);
        entity.setExpiryDate(Date.valueOf(expiry));
        entity.setTermsAgreedAt(new Timestamp(System.currentTimeMillis()));
        entity.setCreatedBy(SELF);
        entity.setUpdatedBy(SELF);
        return entity;
    }

    /** メールアドレスの正規化（前後空白除去 + 小文字化）。 */
    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}

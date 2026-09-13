package com.study21.user.account;

/**
 * 「ユーザー情報の修正」画面が表示する内容。
 *
 * メールアドレス（＝ログインID）は変更できないため {@code email} として参照専用で返す。
 * 権限（アカウント種別）も参照のみ。
 */
public class UserProfileResponse {

    private final Long accountId;
    /** メールアドレス（ログインID 兼用。変更不可） */
    private final String email;
    private final String sei;
    private final String mei;
    private final String seiKana;
    private final String meiKana;
    /** 学年（生徒のみ。保護者は null） */
    private final String grade;
    private final String phone;
    private final boolean mailNotify;
    private final boolean reminderNotify;
    /** 'GUARDIAN' / 'STUDENT' */
    private final String accountType;
    /** ヘッダーなどに出す表示名（姓 + 名） */
    private final String displayName;

    public UserProfileResponse(Long accountId, String email, String sei, String mei,
                               String seiKana, String meiKana, String grade, String phone,
                               boolean mailNotify, boolean reminderNotify,
                               String accountType, String displayName) {
        this.accountId = accountId;
        this.email = email;
        this.sei = sei;
        this.mei = mei;
        this.seiKana = seiKana;
        this.meiKana = meiKana;
        this.grade = grade;
        this.phone = phone;
        this.mailNotify = mailNotify;
        this.reminderNotify = reminderNotify;
        this.accountType = accountType;
        this.displayName = displayName;
    }

    /** エンティティから応答を組み立てる（保存値 '1'/'0' を真偽へ変換する）。 */
    public static UserProfileResponse from(AccountEntity entity) {
        return new UserProfileResponse(
                entity.getAccountId(),
                entity.getLoginId(),
                entity.getSei(),
                entity.getMei(),
                entity.getSeiKana(),
                entity.getMeiKana(),
                entity.getGrade(),
                entity.getPhone(),
                isOn(entity.getMailNotify()),
                isOn(entity.getReminderNotify()),
                entity.getAccountType(),
                displayName(entity));
    }

    /** 表示名（姓 + 名）。どちらかが未設定なら設定済みの片方だけを返す。 */
    public static String displayName(AccountEntity entity) {
        return String.join(" ",
                entity.getSei() == null ? "" : entity.getSei(),
                entity.getMei() == null ? "" : entity.getMei()).trim();
    }

    private static boolean isOn(String flag) {
        return "1".equals(flag);
    }

    public Long getAccountId() {
        return accountId;
    }

    public String getEmail() {
        return email;
    }

    public String getSei() {
        return sei;
    }

    public String getMei() {
        return mei;
    }

    public String getSeiKana() {
        return seiKana;
    }

    public String getMeiKana() {
        return meiKana;
    }

    public String getGrade() {
        return grade;
    }

    public String getPhone() {
        return phone;
    }

    public boolean isMailNotify() {
        return mailNotify;
    }

    public boolean isReminderNotify() {
        return reminderNotify;
    }

    public String getAccountType() {
        return accountType;
    }

    public String getDisplayName() {
        return displayName;
    }
}

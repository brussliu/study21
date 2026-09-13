package com.study21.user.account;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 「ユーザー情報の修正」の更新リクエスト。
 *
 * メールアドレス（＝ログインID）は**変更できない**ため、項目自体を持たない
 * （送られてきても無視される）。権限・状態も同様に対象外。
 * ふりがな・学年が必須かどうかはアカウント種別で変わるため、Service 側で検証する。
 */
public class ProfileUpdateRequest {

    /** 電話番号（任意）。数字・ハイフン・括弧・空白・+ のみ、20 文字以内。 */
    public static final String PHONE_RE = "^[0-9+()\\-\\s]{0,20}$";

    @NotBlank(message = "姓を入力してください。")
    @Size(max = 100, message = "姓は100文字以内で入力してください。")
    private String sei;

    @NotBlank(message = "名を入力してください。")
    @Size(max = 100, message = "名は100文字以内で入力してください。")
    private String mei;

    @Size(max = 100, message = "ふりがな（せい）は100文字以内で入力してください。")
    private String seiKana;

    @Size(max = 100, message = "ふりがな（めい）は100文字以内で入力してください。")
    private String meiKana;

    @Size(max = 50, message = "学年は50文字以内で入力してください。")
    private String grade;

    @Pattern(regexp = PHONE_RE, message = "電話番号の形式が正しくありません。")
    private String phone;

    private boolean mailNotify;

    private boolean reminderNotify;

    public String getSei() {
        return sei;
    }

    public void setSei(String sei) {
        this.sei = sei;
    }

    public String getMei() {
        return mei;
    }

    public void setMei(String mei) {
        this.mei = mei;
    }

    public String getSeiKana() {
        return seiKana;
    }

    public void setSeiKana(String seiKana) {
        this.seiKana = seiKana;
    }

    public String getMeiKana() {
        return meiKana;
    }

    public void setMeiKana(String meiKana) {
        this.meiKana = meiKana;
    }

    public String getGrade() {
        return grade;
    }

    public void setGrade(String grade) {
        this.grade = grade;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public boolean isMailNotify() {
        return mailNotify;
    }

    public void setMailNotify(boolean mailNotify) {
        this.mailNotify = mailNotify;
    }

    public boolean isReminderNotify() {
        return reminderNotify;
    }

    public void setReminderNotify(boolean reminderNotify) {
        this.reminderNotify = reminderNotify;
    }
}

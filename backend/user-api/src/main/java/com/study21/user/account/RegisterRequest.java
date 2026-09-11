package com.study21.user.account;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 保護者・生徒の新規登録リクエスト。
 *
 * <p>フロントエンド（RegisterView）の入力項目と対応する。
 * 構造チェック（形式・必須・文字数）は Bean Validation で行い、
 * 「メール重複」「保護者と生徒のメール一致」等の整合性チェックは
 * AccountServiceImpl で行う（DB 参照が必要なため）。</p>
 */
public class RegisterRequest {

    /** メール形式（フロントエンドの EMAIL_RE と同じ規則） */
    public static final String EMAIL_RE = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$";
    /** パスワード規則: 8文字以上・英字と数字をそれぞれ1文字以上（フロントと同じ） */
    public static final String PASS_RE = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$";

    @NotBlank(message = "メールアドレスを入力してください。")
    @Pattern(regexp = EMAIL_RE, message = "メールアドレスの形式が正しくありません。")
    private String parentEmail;

    @NotBlank(message = "パスワードを入力してください。")
    @Pattern(regexp = PASS_RE, message = "パスワードは8文字以上で、英字と数字をそれぞれ1文字以上含めてください。")
    private String parentPassword;

    @NotBlank(message = "姓を入力してください。")
    @Size(max = 100, message = "姓は100文字以内で入力してください。")
    private String sei;

    @NotBlank(message = "名を入力してください。")
    @Size(max = 100, message = "名は100文字以内で入力してください。")
    private String mei;

    @NotBlank(message = "ふりがな（せい）を入力してください。")
    @Size(max = 100, message = "ふりがな（せい）は100文字以内で入力してください。")
    private String seiKana;

    @NotBlank(message = "ふりがな（めい）を入力してください。")
    @Size(max = 100, message = "ふりがな（めい）は100文字以内で入力してください。")
    private String meiKana;

    @NotBlank(message = "学年を選択してください。")
    @Size(max = 50, message = "学年は50文字以内で入力してください。")
    private String grade;

    @NotBlank(message = "お子さまのメールアドレスを入力してください。")
    @Pattern(regexp = EMAIL_RE, message = "お子さまのメールアドレスの形式が正しくありません。")
    private String studentEmail;

    @NotBlank(message = "お子さまのパスワードを入力してください。")
    @Pattern(regexp = PASS_RE, message = "お子さまのパスワードは8文字以上で、英字と数字をそれぞれ1文字以上含めてください。")
    private String studentPassword;

    @AssertTrue(message = "利用規約への同意が必要です。")
    private boolean agreed;

    public String getParentEmail() {
        return parentEmail;
    }

    public void setParentEmail(String parentEmail) {
        this.parentEmail = parentEmail;
    }

    public String getParentPassword() {
        return parentPassword;
    }

    public void setParentPassword(String parentPassword) {
        this.parentPassword = parentPassword;
    }

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

    public String getStudentEmail() {
        return studentEmail;
    }

    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }

    public String getStudentPassword() {
        return studentPassword;
    }

    public void setStudentPassword(String studentPassword) {
        this.studentPassword = studentPassword;
    }

    public boolean isAgreed() {
        return agreed;
    }

    public void setAgreed(boolean agreed) {
        this.agreed = agreed;
    }
}

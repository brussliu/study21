package com.study21.user.account;

import java.time.LocalDate;

/**
 * 新規登録の応答。登録完了後の確認画面に表示する情報。
 */
public class RegisterResponse {

    private final Long guardianAccountId;
    private final Long studentAccountId;
    private final String parentEmail;
    private final String studentEmail;
    private final LocalDate expiryDate;

    public RegisterResponse(Long guardianAccountId, Long studentAccountId,
                            String parentEmail, String studentEmail, LocalDate expiryDate) {
        this.guardianAccountId = guardianAccountId;
        this.studentAccountId = studentAccountId;
        this.parentEmail = parentEmail;
        this.studentEmail = studentEmail;
        this.expiryDate = expiryDate;
    }

    public Long getGuardianAccountId() {
        return guardianAccountId;
    }

    public Long getStudentAccountId() {
        return studentAccountId;
    }

    public String getParentEmail() {
        return parentEmail;
    }

    public String getStudentEmail() {
        return studentEmail;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }
}

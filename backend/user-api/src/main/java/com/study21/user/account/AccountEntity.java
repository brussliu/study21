package com.study21.user.account;

import java.sql.Date;
import java.sql.Timestamp;

/**
 * ACC_アカウント（保護者・生徒のログインアカウント）のエンティティ。
 */
public class AccountEntity {

    private Long accountId;
    private String loginId;
    private String passwordHash;
    private String accountType;
    private String status;
    private String sei;
    private String mei;
    private String seiKana;
    private String meiKana;
    private String grade;
    private Long guardianId;
    private Date expiryDate;
    private Timestamp termsAgreedAt;
    private String createdBy;
    private String updatedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getLoginId() {
        return loginId;
    }

    public void setLoginId(String loginId) {
        this.loginId = loginId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getAccountType() {
        return accountType;
    }

    public void setAccountType(String accountType) {
        this.accountType = accountType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Long getGuardianId() {
        return guardianId;
    }

    public void setGuardianId(Long guardianId) {
        this.guardianId = guardianId;
    }

    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(Date expiryDate) {
        this.expiryDate = expiryDate;
    }

    public Timestamp getTermsAgreedAt() {
        return termsAgreedAt;
    }

    public void setTermsAgreedAt(Timestamp termsAgreedAt) {
        this.termsAgreedAt = termsAgreedAt;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(String updatedBy) {
        this.updatedBy = updatedBy;
    }

    public Timestamp getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Timestamp createdAt) {
        this.createdAt = createdAt;
    }

    public Timestamp getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Timestamp updatedAt) {
        this.updatedAt = updatedAt;
    }
}

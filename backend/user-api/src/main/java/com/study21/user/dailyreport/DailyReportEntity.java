package com.study21.user.dailyreport;

import java.sql.Timestamp;
import java.time.LocalDate;

/**
 * TRN_学習日報情報 の 1 行（Mapper の戻り値）。
 */
public class DailyReportEntity {

    private Long reportId;
    private Long accountId;
    private String legacyUserId;
    private LocalDate targetDate;
    private String review;
    private Integer concentration;
    private Integer understanding;
    private Integer studyVolume;
    private Integer attitude;
    private String homework;
    private String note;
    /** DRAFT=記載あり（未提出・再提出待ち）/ SUBMITTED=提出済 */
    private String submitStatus;
    /** 最後に提出した日時（未提出なら null） */
    private Timestamp submittedAt;
    /** NORMAL=通常 / HOLIDAY=祝日 / REST=休日 */
    private String holidayType;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getReportId() {
        return reportId;
    }

    public void setReportId(Long reportId) {
        this.reportId = reportId;
    }

    public Long getAccountId() {
        return accountId;
    }

    public void setAccountId(Long accountId) {
        this.accountId = accountId;
    }

    public String getLegacyUserId() {
        return legacyUserId;
    }

    public void setLegacyUserId(String legacyUserId) {
        this.legacyUserId = legacyUserId;
    }

    public LocalDate getTargetDate() {
        return targetDate;
    }

    public void setTargetDate(LocalDate targetDate) {
        this.targetDate = targetDate;
    }

    public String getReview() {
        return review;
    }

    public void setReview(String review) {
        this.review = review;
    }

    public Integer getConcentration() {
        return concentration;
    }

    public void setConcentration(Integer concentration) {
        this.concentration = concentration;
    }

    public Integer getUnderstanding() {
        return understanding;
    }

    public void setUnderstanding(Integer understanding) {
        this.understanding = understanding;
    }

    public Integer getStudyVolume() {
        return studyVolume;
    }

    public void setStudyVolume(Integer studyVolume) {
        this.studyVolume = studyVolume;
    }

    public Integer getAttitude() {
        return attitude;
    }

    public void setAttitude(Integer attitude) {
        this.attitude = attitude;
    }

    public String getHomework() {
        return homework;
    }

    public void setHomework(String homework) {
        this.homework = homework;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public String getSubmitStatus() {
        return submitStatus;
    }

    public void setSubmitStatus(String submitStatus) {
        this.submitStatus = submitStatus;
    }

    public Timestamp getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(Timestamp submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getHolidayType() {
        return holidayType;
    }

    public void setHolidayType(String holidayType) {
        this.holidayType = holidayType;
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

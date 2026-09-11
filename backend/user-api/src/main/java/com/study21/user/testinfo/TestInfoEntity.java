package com.study21.user.testinfo;

import java.sql.Timestamp;
import java.time.LocalDate;

/** TST_テスト情報 の 1 行。 */
public class TestInfoEntity {
    private Long testId;
    private Long familyStudentId;
    private String testNo;
    private String testName;
    private String subject;
    private String kind;
    private LocalDate examDate;
    private Integer score;
    private Integer fullScore;
    private String memo;
    private Integer version;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getTestId() { return testId; }
    public void setTestId(Long testId) { this.testId = testId; }
    public Long getFamilyStudentId() { return familyStudentId; }
    public void setFamilyStudentId(Long familyStudentId) { this.familyStudentId = familyStudentId; }
    public String getTestNo() { return testNo; }
    public void setTestNo(String testNo) { this.testNo = testNo; }
    public String getTestName() { return testName; }
    public void setTestName(String testName) { this.testName = testName; }
    public String getSubject() { return subject; }
    public void setSubject(String subject) { this.subject = subject; }
    public String getKind() { return kind; }
    public void setKind(String kind) { this.kind = kind; }
    public LocalDate getExamDate() { return examDate; }
    public void setExamDate(LocalDate examDate) { this.examDate = examDate; }
    public Integer getScore() { return score; }
    public void setScore(Integer score) { this.score = score; }
    public Integer getFullScore() { return fullScore; }
    public void setFullScore(Integer fullScore) { this.fullScore = fullScore; }
    public String getMemo() { return memo; }
    public void setMemo(String memo) { this.memo = memo; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

package com.study21.user.reading;

import java.sql.Timestamp;

/**
 * RED_書籍分類情報 の 1 行（本棚の分類マスタ）。
 *
 * <p>分類は保護者が書籍管理画面で作り・改名し・並べ替え・消す。分類を消しても本は消えず、
 * `RED_書籍情報.分類ID` が NULL に戻って「未分類」になる（DDL の ON DELETE SET NULL）。</p>
 */
public class ReadingCategoryEntity {

    private Long categoryId;
    private String name;
    private Integer displayOrder;
    private String description;
    /** その分類に入っている冊数（一覧の count で埋まる） */
    private Long bookCount;
    /**
     * 分類の持ち主の家庭（生徒のアカウントID）。
     * null＝全体の分類（管理者が作り、全家庭に見える）。
     */
    private Long ownerFamilyId;
    /** 登録・更新した人（監査用。画面には出さない） */
    private Long createdBy;
    private Long updatedBy;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getCategoryId() { return categoryId; }
    public void setCategoryId(Long categoryId) { this.categoryId = categoryId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public Long getBookCount() { return bookCount; }
    public void setBookCount(Long bookCount) { this.bookCount = bookCount; }
    public Long getOwnerFamilyId() { return ownerFamilyId; }
    public void setOwnerFamilyId(Long ownerFamilyId) { this.ownerFamilyId = ownerFamilyId; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public Long getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(Long updatedBy) { this.updatedBy = updatedBy; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

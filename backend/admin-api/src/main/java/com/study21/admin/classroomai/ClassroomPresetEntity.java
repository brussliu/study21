package com.study21.admin.classroomai;

import java.sql.Timestamp;

/**
 * CR_前置詞プリセット情報（授業の「前置詞」シナリオプリセット）の 1 行（admin-api 側）。
 *
 * <p>設定画面の専用サブパネルが管理するのは **GLOBAL スコープのみ**（登録者アカウントID は NULL）。
 * user-api 側は「GLOBAL + 自分のスコープ」を読むだけ（保存はこの admin-api が行う）。</p>
 */
public class ClassroomPresetEntity {

    private Long presetId;
    private String scope;
    private Long createdBy;
    private String name;
    private String text;
    private Integer displayOrder;
    private Timestamp createdAt;
    private Timestamp updatedAt;

    public Long getPresetId() { return presetId; }
    public void setPresetId(Long presetId) { this.presetId = presetId; }
    public String getScope() { return scope; }
    public void setScope(String scope) { this.scope = scope; }
    public Long getCreatedBy() { return createdBy; }
    public void setCreatedBy(Long createdBy) { this.createdBy = createdBy; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getText() { return text; }
    public void setText(String text) { this.text = text; }
    public Integer getDisplayOrder() { return displayOrder; }
    public void setDisplayOrder(Integer displayOrder) { this.displayOrder = displayOrder; }
    public Timestamp getCreatedAt() { return createdAt; }
    public void setCreatedAt(Timestamp createdAt) { this.createdAt = createdAt; }
    public Timestamp getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Timestamp updatedAt) { this.updatedAt = updatedAt; }
}

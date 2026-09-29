package com.study21.user.english;

/**
 * `COM_設定情報`（GLOBAL）の 1 行を読むための入れ物（user-api 側・英作文AI添削）。
 *
 * <p>`COM_設定項目`（カタログ）の型・有効値の検証は admin-api の `SettingsService` が持つ。
 * user-api は**画像の上限**を読むためだけに、この最小の Mapper を使う
 * （`ClassroomAiSettingEntity` と同じ作法）。</p>
 */
public class EnglishEssaySettingEntity {

    private String settingKey;
    private String settingValue;

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }

    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }
}

package com.study21.user.classroom;

/**
 * COM_設定情報（GLOBAL）の 1 行を読むための入れ物（user-api 側・授業録音）。
 *
 * <p>型・有効値の検証は admin-api の COM_設定項目 を正とする。ここは画面に出す上限と既定値、
 * STT の接続情報を読むためだけの最小 Mapper を使う。</p>
 */
public class ClassroomAiSettingEntity {

    private String settingKey;
    private String settingValue;

    public String getSettingKey() { return settingKey; }
    public void setSettingKey(String settingKey) { this.settingKey = settingKey; }
    public String getSettingValue() { return settingValue; }
    public void setSettingValue(String settingValue) { this.settingValue = settingValue; }
}

package com.study21.user.classroom;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * COM_設定情報（GLOBAL の設定値）を**読むだけ**の Mapper（user-api 側・授業録音）。
 *
 * <p>設定の**保存**は admin-api の `SettingsService`（設定画面）だけが行う。
 * 授業録音は STT の接続情報・トリガー・上限・保存期間を読む。</p>
 */
@Mapper
public interface ClassroomAiSettingMapper {

    /** 指定した (ページ区分, 設定キー) の GLOBAL 値を取る（未設定は返らない）。 */
    List<ClassroomAiSettingEntity> findByKeys(@Param("pageCode") String pageCode,
                                              @Param("settingKeys") List<String> settingKeys);
}

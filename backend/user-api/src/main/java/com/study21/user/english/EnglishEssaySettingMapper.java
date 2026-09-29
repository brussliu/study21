package com.study21.user.english;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * `COM_設定情報`（GLOBAL の設定値）を**読むだけ**の Mapper（user-api 側・英作文AI添削）。
 *
 * <p>設定の**保存**は admin-api の `SettingsService`（設定画面の 【英作文AI添削】 ページ）だけが行う。
 * user-api は画像の上限（枚数・1 枚の大きさ）を読む。</p>
 */
@Mapper
public interface EnglishEssaySettingMapper {

    /** 指定した (ページ区分, 設定キー) の GLOBAL 値を取る（未設定は返らない）。 */
    List<EnglishEssaySettingEntity> findByKeys(@Param("pageCode") String pageCode,
                                               @Param("settingKeys") List<String> settingKeys);
}

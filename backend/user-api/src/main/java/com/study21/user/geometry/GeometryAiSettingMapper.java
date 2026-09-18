package com.study21.user.geometry;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * COM_設定情報（GLOBAL の設定値）を**読むだけ**の Mapper（user-api 側）。
 *
 * <p>AI 生図の画面（有効／無効・画像の上限・既定の分類）と AI 画図助手が使う。
 * 設定の**保存**は admin-api の `SettingsService`（設定画面）だけが行う。</p>
 *
 * <p>値の型・有効値の検証は admin-api 側の `COM_設定項目` を正とする。ここでは検証せず、
 * 使う直前に「未設定なら日本語で理由を出す」ようにしている。</p>
 */
@Mapper
public interface GeometryAiSettingMapper {

    /** 指定した (ページ区分, 設定キー) の GLOBAL 値を取る（未設定は返らない）。 */
    List<GeometryAiSettingEntity> findByKeys(@Param("pageCode") String pageCode,
                                            @Param("settingKeys") List<String> settingKeys);
}

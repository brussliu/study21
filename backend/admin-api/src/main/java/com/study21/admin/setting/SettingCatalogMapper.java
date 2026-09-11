package com.study21.admin.setting;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * COM_設定項目（設定カタログ）の Mapper。
 */
@Mapper
public interface SettingCatalogMapper {

    List<SettingCatalogEntity> findByPage(@Param("pageCode") String pageCode);

    List<SettingCatalogEntity> findByPageAndKeys(
            @Param("pageCode") String pageCode,
            @Param("keys") List<String> keys);
}

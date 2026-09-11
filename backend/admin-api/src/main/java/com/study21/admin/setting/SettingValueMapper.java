package com.study21.admin.setting;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * COM_設定情報（設定値）の Mapper。
 */
@Mapper
public interface SettingValueMapper {

    SettingValueEntity findGlobal(@Param("pageCode") String pageCode, @Param("settingKey") String settingKey);

    List<SettingValueEntity> findGlobalByKeys(
            @Param("pageCode") String pageCode,
            @Param("keys") List<String> keys);

    List<SettingValueEntity> findAllGlobal();

    List<SettingValueEntity> findByScope(
            @Param("scope") String scope,
            @Param("studentId") String studentId,
            @Param("parentId") String parentId);

    int upsertGlobal(SettingValueEntity entity);

    int upsertScoped(SettingValueEntity entity);
}

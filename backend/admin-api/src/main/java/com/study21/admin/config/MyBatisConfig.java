package com.study21.admin.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis Mapper スキャン設定。
 *
 * <p>ベースパッケージ配下の全インターフェースを対象にすると、Mapper ではない
 * サービスinterface（SettingsService 等）まで Mapper プロキシとして登録され、
 * 実装クラス（@Service）の注入を妨げる。そのため @Mapper 注釈を持つ
 * interface（SettingCatalogMapper・SettingValueMapper・BatchExecutionMapper）だけを登録する。</p>
 */
@Configuration
@MapperScan(basePackages = "com.study21.admin", annotationClass = Mapper.class)
public class MyBatisConfig {
}

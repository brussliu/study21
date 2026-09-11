package com.study21.admin.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 管理端ログイン用の ACC_アカウント（ADMIN 種別）Mapper。
 */
@Mapper
public interface AdminAccountMapper {

    /** ログインID（大文字小文字無視）で ADMIN アカウントを検索する。 */
    AdminAccountEntity findByLoginId(@Param("loginId") String loginId);
}

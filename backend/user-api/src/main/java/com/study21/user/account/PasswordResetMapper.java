package com.study21.user.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ACC_パスワードリセット（パスワード再設定トークン）の Mapper。
 */
@Mapper
public interface PasswordResetMapper {

    /** 再設定トークンを発行（保存）する。 */
    int insert(PasswordResetEntity entity);

    /** トークンハッシュで再設定トークンを検索する。 */
    PasswordResetEntity findByTokenHash(@Param("tokenHash") String tokenHash);

    /** トークンを使用済みにする。 */
    int markUsed(@Param("resetId") Long resetId, @Param("updatedBy") String updatedBy);
}

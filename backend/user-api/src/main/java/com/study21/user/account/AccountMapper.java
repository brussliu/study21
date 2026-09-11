package com.study21.user.account;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * ACC_アカウント（保護者・生徒アカウント）の Mapper。
 */
@Mapper
public interface AccountMapper {

    /** アカウントを新規登録する（アカウントID は自動採番）。 */
    int insert(AccountEntity entity);

    /** ログインID（メールアドレス・大文字小文字無視）で検索する。 */
    AccountEntity findByLoginId(@Param("loginId") String loginId);

    /** ログインID（メールアドレス・大文字小文字無視）の存在数を返す。 */
    int existsByLoginId(@Param("loginId") String loginId);

    /** パスワードハッシュを更新する（パスワード再設定用）。 */
    int updatePasswordHash(@Param("accountId") Long accountId, @Param("passwordHash") String passwordHash);
}

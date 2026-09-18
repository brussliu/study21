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

    /**
     * ログインID（メールアドレス・大文字小文字無視）で検索する。
     * **一般ユーザー（保護者・生徒）だけ**を返す（パスワード再設定などの画面向け）。
     */
    AccountEntity findByLoginId(@Param("loginId") String loginId);

    /**
     * ログイン認証用の検索。`findByLoginId` と違って**管理者（ADMIN）も返す**
     * （2026-09-14 の決定 Q8: 管理者も user-api のセッションを持ち、
     * 読書管理の全体書籍を管理できるようにする）。
     */
    AccountEntity findByLoginIdForLogin(@Param("loginId") String loginId);

    /** 保護者アカウントに紐づく生徒アカウント（1 保護者 = 1 生徒の運用）。居なければ null。 */
    AccountEntity findStudentByGuardianId(@Param("guardianAccountId") long guardianAccountId);

    /** ログインID（メールアドレス・大文字小文字無視）の存在数を返す。 */
    int existsByLoginId(@Param("loginId") String loginId);

    /** パスワードハッシュを更新する（パスワード再設定用）。 */
    int updatePasswordHash(@Param("accountId") Long accountId, @Param("passwordHash") String passwordHash);

    /** アカウントID で検索する（自分の情報の取得・更新用）。 */
    AccountEntity findById(@Param("accountId") long accountId);

    /**
     * 「ユーザー情報の修正」で編集できる項目だけを更新する。
     * メールアドレス（＝ログインID）と権限・状態は変更しない。
     */
    int updateProfile(AccountEntity entity);

    /** 自分でパスワードを変更する（更新ID に操作者を記録する）。 */
    int updatePasswordHashBySelf(@Param("accountId") long accountId,
                                 @Param("passwordHash") String passwordHash,
                                 @Param("updatedBy") String updatedBy);
}

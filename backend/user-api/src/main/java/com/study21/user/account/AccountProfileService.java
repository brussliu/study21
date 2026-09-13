package com.study21.user.account;

import com.study21.user.security.UserPrincipal;

/**
 * 「ユーザー情報の修正」「パスワード変更」の業務処理。
 * 対象は常にログイン中のアカウント自身（他人の情報は扱わない）。
 */
public interface AccountProfileService {

    /** ログイン中のアカウントの情報を返す。 */
    UserProfileResponse getProfile(UserPrincipal user);

    /** 編集できる項目を更新し、更新後の情報を返す（メールアドレスは変更しない）。 */
    UserProfileResponse updateProfile(UserPrincipal user, ProfileUpdateRequest request);

    /** 現在のパスワードで本人確認してから新しいパスワードへ変更する。 */
    void changePassword(UserPrincipal user, PasswordChangeRequest request);
}

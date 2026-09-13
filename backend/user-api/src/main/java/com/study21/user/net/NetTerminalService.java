package com.study21.user.net;

import com.study21.user.security.UserPrincipal;

/**
 * 端末コントロールの業務処理。
 *
 * 2.0（`com.study2.terminalcontrol`）は保護者だけが使えたが、2.1 は**当面ロールで分けない**
 * （ログインしていれば生徒・保護者とも使える。ユーザーの指定。ロールの分離は後で設計する）。
 *
 * 管理画面からは一覧の参照・端末の新規登録・編集・モード変更（選択した複数台）を提供する。
 * 2.0 はモード変更後に batL01（プロキシ再起動）を起動していたが、2.1 のプロキシは batS01 が
 * admin-api の起動時に立ち上げる（docs/PROXY.md）。モード変更では再起動せず DB 更新だけを行う。
 */
public interface NetTerminalService {

    NetTerminalModels.TerminalSearchResult search(UserPrincipal user, NetTerminalSearchQuery query);

    /** 端末を新規登録する。 */
    NetTerminalModels.TerminalMutationResult create(UserPrincipal user,
                                                    NetTerminalModels.TerminalSaveRequest request);

    /** 端末の内容（IP・名称・モード・状態・備考）を編集する。 */
    NetTerminalModels.TerminalMutationResult update(UserPrincipal user, long terminalId,
                                                    NetTerminalModels.TerminalSaveRequest request);

    /** 1 台のモードを変更する。 */
    NetTerminalModels.TerminalMutationResult updateMode(UserPrincipal user, long terminalId,
                                                       NetTerminalModels.ModeChangeRequest request);

    /** 選択した端末のモードを一括で変更する（2.0 の updateTerminalStatuses 相当）。 */
    NetTerminalModels.TerminalMutationResult updateModes(UserPrincipal user,
                                                         NetTerminalModels.BulkModeChangeRequest request);

    /** 一覧の検索条件。mode は T/K/G/B/S/J、status は '1'/'0'。 */
    record NetTerminalSearchQuery(
            String terminalMode,
            String status,
            String keyword,
            Integer page,
            Integer size) {
    }
}

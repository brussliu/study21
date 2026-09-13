package com.study21.user.net;

import com.study21.user.security.UserPrincipal;

import java.util.List;

/**
 * サイト管理の業務処理。
 *
 * 2.0（`com.study2.site`）の挙動を引き継ぐ:
 *  - 新規登録は未承認（PENDING）で登録し、編集すると未承認に戻す（再承認が必要）。
 *  - 承認は 1 件ずつ、または選択した複数件をまとめて。承認者と承認日時を残す。
 *  - 却下は「却下」にし、承認者・承認日時は残さない。
 *
 * 2.0 の `enableInternetUsage` / `disableInternetUsage`（全サイトの有効／無効の一括切替）は
 * **画面に入口が無く、呼び出し元も無かった**（2.0 内で未使用）ため 2.1 では実装しない。
 * 2.0 の「インターネット利用の開始／停止」は batR03 / batR04 が**端末モード**を S / T に変えて行う。
 *
 * 2.0 では操作後に batL01（プロキシ再起動）を起動していたが、2.1 のプロキシは batS01 が
 * admin-api の起動時に立ち上げる（docs/PROXY.md）。サイト・端末の更新では再起動せず、
 * DB 更新だけを行う（各実装のコメント参照）。
 */
public interface NetSiteService {

    NetSiteModels.SiteSearchResult search(NetSiteSearchQuery query);

    NetSiteModels.SiteMutationResult create(UserPrincipal user, NetSiteModels.SiteSaveRequest request);

    NetSiteModels.SiteMutationResult update(UserPrincipal user, long siteId, NetSiteModels.SiteSaveRequest request);

    NetSiteModels.SiteMutationResult delete(UserPrincipal user, long siteId);

    NetSiteModels.SiteMutationResult approve(UserPrincipal user, long siteId);

    /** 却下する（未承認・承認済みのどちらからでも「却下」にできる）。 */
    NetSiteModels.SiteMutationResult reject(UserPrincipal user, long siteId);

    /**
     * 一覧の検索条件。
     * sortBy は画面の列名（siteName / siteUrl / kind / judgeMethod / category / approvalStatus /
     * status / memo / updatedAt / createdAt）、sortDir は asc / desc。
     */
    record NetSiteSearchQuery(
            String kindCode,
            String judgeMethodCode,
            String categoryCode,
            String approvalStatus,
            String status,
            String keyword,
            String sortBy,
            String sortDir,
            Integer page,
            Integer size) {
    }
}

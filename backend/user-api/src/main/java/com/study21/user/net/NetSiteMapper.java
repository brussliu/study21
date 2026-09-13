package com.study21.user.net;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

/**
 * NET_サイト情報（サイト管理）の Mapper。
 * プロキシの許可判定はアプリ側で行うため、ここは管理画面が必要とする検索・更新だけを持つ。
 */
@Mapper
public interface NetSiteMapper {

    /** 条件に一致するサイトを返す（並び順・ページングは呼び出し側で検証した値を使う）。 */
    List<NetSiteEntity> search(@Param("kindCode") String kindCode,
                               @Param("judgeMethodCode") String judgeMethodCode,
                               @Param("categoryCode") String categoryCode,
                               @Param("approvalStatus") String approvalStatus,
                               @Param("status") String status,
                               @Param("keyword") String keyword,
                               @Param("sortColumn") String sortColumn,
                               @Param("sortDirection") String sortDirection,
                               @Param("limit") int limit,
                               @Param("offset") int offset);

    /** 条件に一致する件数。 */
    long count(@Param("kindCode") String kindCode,
               @Param("judgeMethodCode") String judgeMethodCode,
               @Param("categoryCode") String categoryCode,
               @Param("approvalStatus") String approvalStatus,
               @Param("status") String status,
               @Param("keyword") String keyword);

    NetSiteEntity findById(@Param("siteId") long siteId);

    /** 新規登録（アカウントID は自動採番）。 */
    int insert(NetSiteEntity entity);

    /**
     * 内容を更新する。編集すると承認は未承認に戻る（2.0 と同じ）。
     * 楽観的ロックのため変更前バージョンも照合する。
     */
    int update(NetSiteEntity entity);

    int delete(@Param("siteId") long siteId);

    /** 承認する（承認者と承認日時を残す）。 */
    int approve(@Param("siteId") long siteId,
                @Param("approvedByAccountId") Long approvedByAccountId,
                @Param("approvedAt") Timestamp approvedAt,
                @Param("updatedByAccountId") Long updatedByAccountId);

    /** 却下する（承認日時・承認者は残さない）。 */
    int reject(@Param("siteId") long siteId,
               @Param("updatedByAccountId") Long updatedByAccountId);

}

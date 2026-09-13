package com.study21.user.net;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * NET_Web閲覧履歴情報（Web閲覧履歴）の Mapper。
 */
@Mapper
public interface WebBrowsingLogMapper {

    /** 件数（一覧と同じ絞り込み条件）。 */
    long count(@Param("terminalId") String terminalId,
               @Param("terminalName") String terminalName,
               @Param("domain") String domain,
               @Param("eventType") String eventType,
               @Param("keyword") String keyword,
               @Param("dateFrom") String dateFrom,
               @Param("dateTo") String dateTo);

    /** 新しい順の 1 ページ。 */
    List<WebBrowsingLogEntity> search(@Param("terminalId") String terminalId,
                                      @Param("terminalName") String terminalName,
                                      @Param("domain") String domain,
                                      @Param("eventType") String eventType,
                                      @Param("keyword") String keyword,
                                      @Param("dateFrom") String dateFrom,
                                      @Param("dateTo") String dateTo,
                                      @Param("limit") int limit,
                                      @Param("offset") int offset);
}

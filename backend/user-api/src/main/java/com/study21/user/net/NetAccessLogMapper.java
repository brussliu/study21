package com.study21.user.net;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;
import java.util.List;

/**
 * NET_プロキシ通信履歴情報（サイトアクセス履歴）の Mapper。
 */
@Mapper
public interface NetAccessLogMapper {

    /** 件数（一覧と同じ絞り込み条件）。 */
    long count(@Param("host") String host,
               @Param("terminalName") String terminalName,
               @Param("result") String result);

    /**
     * 指定日時以降を 1 時間ごとに数える（2.0 の getRecentUsageHourlySummary 相当）。
     *
     * @param terminalName 端末名称（完全一致。null ならすべて）
     * @param kindCode     サイトの区分（STUDY / NORMAL / BREAK / GAME。null ならすべて）
     * @param receivedFrom この日時以降の履歴だけを数える
     */
    List<NetAccessLogModels.HourlyUsageBucket> hourlySummary(
            @Param("terminalName") String terminalName,
            @Param("kindCode") String kindCode,
            @Param("receivedFrom") Timestamp receivedFrom);

    /** 新しい順の 1 ページ。 */
    List<NetAccessLogEntity> search(@Param("host") String host,
                                                 @Param("terminalName") String terminalName,
                                                 @Param("result") String result,
                                                 @Param("limit") int limit,
                                                 @Param("offset") int offset);
}

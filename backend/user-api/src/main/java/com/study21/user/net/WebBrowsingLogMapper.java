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

    /**
     * ブラウザ拡張から受信したイベントをまとめて登録する。
     *
     * <p>同じ 端末識別子 + イベント識別子 の行がすでにあれば飛ばす（拡張の再送で二重登録しない）。
     * 戻り値は実際に登録できた件数。</p>
     *
     * @param accountId  接続コードから特定した持ち主（拡張は人が操作しないので 登録者/更新者 は NULL）
     * @param terminalId 拡張が採番した端末識別子
     */
    int insertBatch(@Param("accountId") long accountId,
                    @Param("terminalId") String terminalId,
                    @Param("terminalName") String terminalName,
                    @Param("browserType") String browserType,
                    @Param("profileId") String profileId,
                    @Param("events") List<WebBrowsingLogModels.NewEvent> events);
}

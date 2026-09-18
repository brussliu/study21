package com.study21.user.net;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * NET_端末コントロール情報（端末コントロール）の Mapper。
 * 画面から端末を登録・編集できる（新規／編集）ほか、一覧・モード変更・状態を持つ。
 */
@Mapper
public interface NetTerminalMapper {

    List<NetTerminalEntity> search(@Param("terminalMode") String terminalMode,
                                    @Param("status") String status,
                                    @Param("keyword") String keyword,
                                    @Param("limit") int limit,
                                    @Param("offset") int offset);

    long count(@Param("terminalMode") String terminalMode,
               @Param("status") String status,
               @Param("keyword") String keyword);

    NetTerminalEntity findById(@Param("terminalId") long terminalId);

    /** 有効（状態='1'）な端末のうち、同じ IP のもの（自分自身は除く）。IP の重複チェックに使う。 */
    NetTerminalEntity findActiveByIpAddress(@Param("ipAddress") String ipAddress,
                                            @Param("excludeTerminalId") Long excludeTerminalId);

    /** 端末を新規登録する（端末ID は採番された値を entity に書き戻す）。 */
    int insert(NetTerminalEntity entity);

    /** 端末の内容を更新する（楽観的ロックのため変更前バージョンも照合する）。 */
    int update(@Param("terminalId") long terminalId,
               @Param("ipAddress") String ipAddress,
               @Param("terminalName") String terminalName,
               @Param("terminalMode") String terminalMode,
               @Param("status") String status,
               @Param("note") String note,
               @Param("version") Integer version,
               @Param("updatedByAccountId") Long updatedByAccountId);

    /** 1 台のモードを変更する（楽観的ロックのため変更前バージョンも照合する）。 */
    /** 端末を削除する（存在しなければ 0 件）。 */
    int delete(@Param("terminalId") long terminalId);

    /** モードを 1 台変更する（版が合わなければ 0 件）。 */
    int updateMode(@Param("terminalId") long terminalId,
                   @Param("terminalMode") String terminalMode,
                   @Param("version") Integer version,
                   @Param("updatedByAccountId") Long updatedByAccountId);

    /** 選択した端末のモードを一括で変更し、更新件数を返す（2.0 の updateTerminalStatuses 相当）。 */
    int updateModes(@Param("terminalIds") List<Long> terminalIds,
                    @Param("terminalMode") String terminalMode,
                    @Param("updatedByAccountId") Long updatedByAccountId);

    /** 存在する端末IDの件数（削除済み ID が混ざっていないかの確認）。 */
    long countByIds(@Param("terminalIds") List<Long> terminalIds);
}

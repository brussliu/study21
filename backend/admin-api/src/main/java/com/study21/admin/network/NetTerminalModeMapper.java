package com.study21.admin.network;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * インターネット利用の開始／終了（batR03 / batR04）で端末モードを一括で切り替える Mapper。
 *
 * <p><strong>なぜ admin-api が端末の表を書くか</strong>: 2.0 はバッチ（別プロセス）が
 * `TerminalControlRepository#updateAllTerminalStatus` で切り替えていた。2.1 はバッチが
 * admin-api にあり、端末の表は admin-api のプロキシも読む（{@code ProxyTerminalMapper}）ため、
 * **書き込みも admin-api から行う**（サービス間で API を呼ばない。docs/NET_CONTROL.md）。</p>
 *
 * <p>プロキシは 1 リクエストごとに端末モードを DB から読むので、**モードを書き換えれば
 * 次のリクエストから効く**（再起動は不要。ただしプロキシ自体は動いていないといけない＝
 * {@link com.study21.admin.proxy.ProxyServerService} を起動保証する）。</p>
 */
@Mapper
public interface NetTerminalModeMapper {

    /**
     * 有効な端末（{@code 状態='1'}）のモードを一括で切り替える。
     *
     * <p>既に同じモードの端末は触らない（更新日時を無駄に動かさない。
     * 「計画時刻より後に端末が更新されたか」の判定に更新日時を使うため）。</p>
     *
     * @param mode          切り替えるモード（'S' = 停止 / 'T' = 通常）
     * @param updatedByCode 更新元コード（'batR03' / 'batR04'）
     * @return 更新した端末の数
     */
    int updateAllTerminalModes(@Param("mode") String mode, @Param("updatedByCode") String updatedByCode);

    /**
     * 有効な端末が最後に更新された日時（無ければ null）。
     *
     * <p>計画実行点より後に端末が触られているときは、利用者の手動操作を上書きしないよう
     * 切り替えを見送る、という判断に使う。</p>
     */
    LocalDateTime findLatestUpdatedAt();

    /** 有効な端末の数（メッセージ用）。 */
    int countActiveTerminals();

    /** いま「停止（S）」になっている有効な端末の数（メッセージ用）。 */
    int countActiveTerminalsWithMode(@Param("mode") String mode);
}

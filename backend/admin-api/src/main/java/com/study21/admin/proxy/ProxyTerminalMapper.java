package com.study21.admin.proxy;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * NET_端末コントロール情報（端末コントロール）の Mapper。
 *
 * <p>プロキシはクライアント IP から端末モードを引くためだけに使う。2.0 は
 * 端末ステータス 列と ステータス='有効' で判定していたが、2.1 は 端末モード 列と
 * 状態='1'（有効）で判定する。</p>
 */
@Mapper
public interface ProxyTerminalMapper {

    /**
     * IP アドレスから有効な端末のモード（T/K/G/B/S/J）を返す。
     *
     * @param ip クライアント IP（正規化済みの値）
     * @return 端末モード。未登録・無効（状態='0'）なら null
     */
    String findTerminalModeByIp(@Param("ip") String ip);
}

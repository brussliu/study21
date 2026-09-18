package com.study21.user.browserext;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.sql.Timestamp;

/**
 * NET_ブラウザ接続情報（接続コード）の Mapper。
 */
@Mapper
public interface BrowserConnectionMapper {

    /** アカウントの接続情報（未発行なら null）。 */
    BrowserConnectionEntity findByAccountId(@Param("accountId") long accountId);

    /** 接続コードから引く（有効なものだけ）。拡張からのリクエストはすべてこれを使う。 */
    BrowserConnectionEntity findActiveByToken(@Param("token") String token);

    int insert(BrowserConnectionEntity entity);

    /** 接続コードの再発行（古いコードは即時に無効になる）。 */
    int updateToken(@Param("connectionId") long connectionId,
                    @Param("token") String token,
                    @Param("updatedByAccountId") Long updatedByAccountId,
                    @Param("version") int version);

    /** 拡張から受け付けた時刻を記録する（失敗しても処理は続けるので更新件数は見ない）。 */
    int touch(@Param("connectionId") long connectionId, @Param("usedAt") Timestamp usedAt);
}

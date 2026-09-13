package com.study21.admin.proxy;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * NET_プロキシ通信履歴情報（プロキシ通信履歴）の Mapper。
 *
 * <p>2.0 の ProxyAccessLogRepository から、プロキシが使う INSERT（1 リクエスト = 1 行）だけを
 * 移植した。検索・集計（履歴画面）はここには置かない。2.0 は素の JDBC だったが、2.1 は
 * すべての DB 操作を MyBatis 経由にして SqlLoggingInterceptor に記録させる決まりのため
 * Mapper にしてある。</p>
 *
 * <p>許可した要求は 応答状態コード と エラー内容 を NULL、拒否した要求は 403 と拒否理由を入れる
 * （拒否理由は 端末へ返す HTML と同じ文言）。</p>
 */
@Mapper
public interface ProxyAccessLogMapper {

    /**
     * 通信履歴を 1 行追記する。
     *
     * <p>登録者／更新者アカウントID は人が操作しないため NULL、登録元コード は 'PROXY'、
     * 登録日時／更新日時 は CURRENT_TIMESTAMP を SQL 側で入れる。要求日時 は要求側の時刻が
     * 取得できた場合だけ入れる項目のため、プロキシからは NULL を渡す。</p>
     *
     * @param receivedAt         受付日時（プロキシが要求を受け付けた時刻。NOT NULL）
     * @param clientIp           クライアント IP（NOT NULL）
     * @param clientPort         クライアントポート（不明なら null）
     * @param httpMethod         HTTP メソッド
     * @param host               接続先ホスト
     * @param hostPort           接続先ポート
     * @param requestUrl         要求 URL
     * @param requestPath        要求パス
     * @param queryString        クエリ文字列
     * @param protocolType       プロトコル種別（HTTP / HTTPS）
     * @param userAgent          ユーザーエージェント
     * @param referer            参照元 URL
     * @param responseStatusCode NULL=許可 / 403=拒否
     * @param errorContent       拒否理由（403 のときは必須）
     * @return 更新件数（1）
     */
    int insertRequestLog(@Param("receivedAt") LocalDateTime receivedAt,
                         @Param("clientIp") String clientIp,
                         @Param("clientPort") Integer clientPort,
                         @Param("httpMethod") String httpMethod,
                         @Param("host") String host,
                         @Param("hostPort") Integer hostPort,
                         @Param("requestUrl") String requestUrl,
                         @Param("requestPath") String requestPath,
                         @Param("queryString") String queryString,
                         @Param("protocolType") String protocolType,
                         @Param("userAgent") String userAgent,
                         @Param("referer") String referer,
                         @Param("responseStatusCode") Integer responseStatusCode,
                         @Param("errorContent") String errorContent);
}

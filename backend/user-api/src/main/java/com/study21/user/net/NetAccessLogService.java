package com.study21.user.net;

/**
 * サイトアクセス履歴（インターネット利用履歴）の照会。
 *
 * <p>参照のみ。記録はプロキシサービス（admin-api の batS01）が行う。</p>
 */
public interface NetAccessLogService {

    /**
     * 新しい順に 1 ページ返す。
     *
     * @param host         接続先ホストの部分一致
     * @param terminalName 端末名称の部分一致
     * @param result       ALLOW（許可）/ DENY（拒否）/ null（すべて）
     */
    NetAccessLogModels.AccessLogSearchResult search(String host, String terminalName,
                                                    String result, int page, int size);

    /**
     * 最近 3 日（今日・昨日・一昨日）の時間帯別の件数（2.0 の「最近3日 上網状況（時間帯別）」）。
     *
     * @param terminalName 端末名称（完全一致。2.0 と同じく登録端末の名前を選ぶ）
     * @param kindCode     サイトの区分（STUDY / NORMAL / BREAK / GAME。null ならすべて）
     */
    NetAccessLogModels.HourlyUsageSummary hourlyUsage(String terminalName, String kindCode);
}

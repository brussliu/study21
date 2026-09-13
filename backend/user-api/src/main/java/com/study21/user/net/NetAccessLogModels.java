package com.study21.user.net;

import java.sql.Timestamp;
import java.util.List;

/**
 * インターネット利用履歴（サイトアクセス履歴）のモデル。
 *
 * データは `NET_プロキシ通信履歴情報`（プロキシサービス batS01 が記録。
 * 2.0 から移行済み）を読む。端末名称は `NET_端末コントロール情報` から IP で引く。
 */
public final class NetAccessLogModels {

    private NetAccessLogModels() {
    }

    /** 結果の絞り込み値。応答状態コードが NULL なら許可、入っていれば拒否。 */
    public static final List<String> RESULTS = List.of("ALLOW", "DENY");

    /**
     * 時間帯別の集計（2.0 の「最近3日 上網状況（時間帯別）」）。
     *
     * `days` は新しい順（[0] が今日）、`buckets` は days と同じ並びで 24 時間ぶんの件数。
     */
    public record HourlyUsageSummary(
            List<String> days,
            List<List<Long>> buckets,
            long totalCount,
            String terminalName,
            String kind) {
    }

    /** 1 時間ぶんの集計行（Mapper が返す生の値）。 */
    public record HourlyUsageBucket(Timestamp bucketAt, long requestCount) {
    }

    /** 一覧の 1 行。 */
    public record AccessLogRow(
            long logId,
            Timestamp receivedAt,
            String clientIp,
            String terminalName,
            String httpMethod,
            String host,
            String url,
            Integer statusCode,
            /** 画面表示用の結果コード（ALLOW=許可 / DENY=拒否）。応答状態コードが NULL なら許可。 */
            String result,
            String errorDetail) {
    }

    /** 一覧のレスポンス。 */
    public record AccessLogSearchResult(
            List<AccessLogRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages) {
    }
}

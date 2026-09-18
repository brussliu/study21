package com.study21.user.net;

import java.sql.Timestamp;
import java.util.List;

/**
 * インターネット利用履歴（Web閲覧履歴）のモデル。
 *
 * <p>データは `NET_Web閲覧履歴情報`（ブラウザ拡張が記録。2.0 の
 * `TRN_ブラウザ閲覧履歴情報` から移行済み）を読む。</p>
 */
public final class WebBrowsingLogModels {

    private WebBrowsingLogModels() {
    }

    /**
     * 2.0 のブラウザ拡張が送ってくるイベント種別（画面の絞り込みに使う）。
     * 2.0 の実データに存在する 5 種類。
     */
    public static final List<String> EVENT_TYPES = List.of(
            "HISTORY_VISITED",
            "NAV_HISTORY_UPDATED",
            "TAB_UPDATED",
            "TAB_ACTIVATED",
            "NAV_COMMITTED");

    /** 一覧の 1 行。 */
    public record BrowsingLogRow(
            long logId,
            Timestamp accessedAt,
            String terminalId,
            String terminalName,
            String eventType,
            String domain,
            String url,
            String pageTitle,
            /** '1'=アクティブなタブだった / '0'=非アクティブ */
            String activeFlag,
            Integer staySeconds,
            Integer viewCount) {
    }

    /** 一覧のレスポンス。 */
    public record BrowsingLogSearchResult(
            List<BrowsingLogRow> items,
            long totalElements,
            int page,
            int size,
            int totalPages) {
    }

    /**
     * 登録する 1 イベント（ブラウザ拡張から受信したものを、そのまま INSERT できる形にしたもの）。
     *
     * @param eventKey 拡張が付ける一意な ID（UUID）。再送で二重登録しないための鍵
     */
    public record NewEvent(
            String eventKey,
            String eventType,
            String url,
            String domain,
            String pageTitle,
            String referrerUrl,
            String faviconUrl,
            String transitionType,
            Long tabId,
            Long windowId,
            String sessionId,
            /** '1'=アクティブなタブだった / '0'=非アクティブ */
            String activeFlag,
            /** '1'=ブラウザ履歴の同期で入った行 / '0'=リアルタイムのイベント */
            String historySyncFlag,
            Timestamp visitedAt,
            Integer staySeconds,
            Integer viewCount) {
    }
}

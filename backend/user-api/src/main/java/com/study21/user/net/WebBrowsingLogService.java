package com.study21.user.net;

/**
 * Web閲覧履歴（インターネット利用履歴）の照会。
 *
 * <p>参照のみ。記録はブラウザ拡張が行う（2.0 と同じ）。</p>
 */
public interface WebBrowsingLogService {

    /**
     * 新しい順に 1 ページ返す。
     *
     * @param terminalId   端末識別子の部分一致
     * @param terminalName 端末名称の部分一致
     * @param domain       ドメインの部分一致
     * @param eventType    イベント種別（完全一致。{@link WebBrowsingLogModels#EVENT_TYPES}）
     * @param keyword      URL / ページタイトルの部分一致
     * @param dateFrom     アクセス日（yyyy-MM-dd。その日の 00:00 から）
     * @param dateTo       アクセス日（yyyy-MM-dd。その日の 23:59:59 まで）
     */
    WebBrowsingLogModels.BrowsingLogSearchResult search(String terminalId, String terminalName,
                                                        String domain, String eventType, String keyword,
                                                        String dateFrom, String dateTo, int page, int size);
}

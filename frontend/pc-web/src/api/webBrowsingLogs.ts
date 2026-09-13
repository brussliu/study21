import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * Web閲覧履歴 API（/api/user/web-browsing-logs）。
 * user-api の WebBrowsingLogController と対応する。
 *
 * データは `NET_Web閲覧履歴情報`（ブラウザ拡張が記録。2.0 の
 * `TRN_ブラウザ閲覧履歴情報` から移行済み）。
 */
export type BrowsingEventType =
  | 'HISTORY_VISITED'
  | 'NAV_HISTORY_UPDATED'
  | 'TAB_UPDATED'
  | 'TAB_ACTIVATED'
  | 'NAV_COMMITTED'

export interface BrowsingLogRow {
  logId: number
  accessedAt: string | null
  terminalId: string | null
  terminalName: string | null
  eventType: BrowsingEventType
  domain: string | null
  url: string | null
  pageTitle: string | null
  /** '1'=アクティブなタブだった / '0'=非アクティブ。 */
  activeFlag: string
  staySeconds: number | null
  viewCount: number | null
}

export interface BrowsingLogPage {
  items: BrowsingLogRow[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

/** イベント種別の日本語ラベル（2.0 の拡張が送ってくる 5 種類）。 */
export const EVENT_TYPE_LABELS: Record<BrowsingEventType, string> = {
  HISTORY_VISITED: '履歴から閲覧',
  NAV_HISTORY_UPDATED: '履歴を更新',
  TAB_UPDATED: 'タブを更新',
  TAB_ACTIVATED: 'タブを切替',
  NAV_COMMITTED: 'ページ遷移'
}

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/user' })

export function searchBrowsingLogs(params: {
  terminalId?: string
  terminalName?: string
  domain?: string
  eventType?: string
  keyword?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}): Promise<ApiResponse<BrowsingLogPage>> {
  return http.get<BrowsingLogPage>('/web-browsing-logs', { params })
}

/** 絞り込みに出すイベント種別の一覧（サーバが持つ正の値）。 */
export function fetchBrowsingEventTypes(): Promise<ApiResponse<{ items: BrowsingEventType[] }>> {
  return http.get<{ items: BrowsingEventType[] }>('/web-browsing-logs/event-types')
}

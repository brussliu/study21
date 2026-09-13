import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * サイトアクセス履歴 API（/api/user/net-access-logs）。
 * user-api の NetAccessLogController と対応する。
 *
 * データは `NET_プロキシ通信履歴情報`（プロキシサービス batS01 が記録。2.0 から移行済み）。
 */
export type AccessResultCode = 'ALLOW' | 'DENY'

export interface AccessLogRow {
  logId: number
  receivedAt: string | null
  clientIp: string | null
  terminalName: string | null
  httpMethod: string | null
  host: string | null
  url: string | null
  statusCode: number | null
  /** ALLOW=許可 / DENY=拒否（応答状態コードが無ければ許可）。 */
  result: AccessResultCode
  errorDetail: string | null
}

export interface AccessLogPage {
  items: AccessLogRow[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

/**
 * 最近 3 日の時間帯別の件数（ホームの「最近3日 上網状況（時間帯別）」）。
 * `days` は新しい順（[0] が今日）、`buckets` は days と同じ並びで 24 時間ぶん。
 */
export interface HourlyUsageSummary {
  days: string[]
  buckets: number[][]
  totalCount: number
  terminalName: string | null
  kind: string | null
}

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/user' })

export function searchAccessLogs(params: {
  host?: string
  terminalName?: string
  result?: string
  page?: number
  size?: number
}): Promise<ApiResponse<AccessLogPage>> {
  return http.get<AccessLogPage>('/net-access-logs', { params })
}

/** 最近 3 日の時間帯別の件数（2.0 の「最近3日 上網状況（時間帯別）」と同じ）。 */
export function fetchHourlyUsageSummary(params: {
  terminalName?: string
  kind?: string
}): Promise<ApiResponse<HourlyUsageSummary>> {
  return http.get<HourlyUsageSummary>('/net-access-logs/hourly-summary', {
    params: {
      terminalName: params.terminalName?.trim() || undefined,
      kind: params.kind?.trim() || undefined
    }
  })
}

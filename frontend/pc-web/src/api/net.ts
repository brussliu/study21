import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * サイト管理 / 端末コントロール API（/api/user）。
 * 型とエンドポイントは user-api の NetSiteController / NetTerminalController と対応する。
 *
 * 値はコードでやり取りし、日本語の表示文言は画面側（この機能のラベル定義）で作る。
 */

/** 区分コード（端末モード別の許可区分）。 */
export type SiteKindCode = 'STUDY' | 'NORMAL' | 'BREAK' | 'GAME'
/** URL の一致方法。 */
export type JudgeMethodCode = 'PREFIX' | 'SUFFIX' | 'CONTAINS' | 'EXACT'
/** 分類コード。 */
export type SiteCategoryCode = 'LEARNING' | 'ENTERTAINMENT' | 'SHOPPING' | 'SNS' | 'OTHER'
/** 承認ステータス。 */
export type ApprovalStatus = 'PENDING' | 'APPROVED' | 'REJECTED'
/** 有効／無効（2.1 共通）。 */
export type StatusCode = '1' | '0'
/** 端末モード: T=通常 / K=休憩 / G=ゲーム / B=勉強 / S=停止 / J=自由。 */
export type TerminalModeCode = 'T' | 'K' | 'G' | 'B' | 'S' | 'J'

export interface SiteRow {
  siteId: number
  siteName: string
  siteUrl: string
  hostName: string
  kindCode: SiteKindCode
  judgeMethodCode: JudgeMethodCode
  categoryCode: SiteCategoryCode
  categoryName: string | null
  approvalStatus: ApprovalStatus
  status: StatusCode
  note: string | null
  version: number
  approvedAt: string | null
  createdAt: string | null
  updatedAt: string | null
}

export interface SiteSearchResult {
  items: SiteRow[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface SiteSearchQuery {
  kind?: string
  judgeMethod?: string
  category?: string
  approvalStatus?: string
  status?: string
  keyword?: string
  sortBy?: string
  sortDir?: 'asc' | 'desc'
  page?: number
  size?: number
}

export interface SiteSaveRequest {
  siteName: string
  siteUrl: string
  kindCode: SiteKindCode
  judgeMethodCode: JudgeMethodCode
  categoryCode: SiteCategoryCode
  categoryName?: string | null
  note?: string | null
  /** 楽観的ロック用（画面が表示していたバージョン）。 */
  version?: number
}

export interface SiteMutationResult {
  message: string
  row: SiteRow | null
  updatedCount: number | null
}

export interface TerminalRow {
  terminalId: number
  ipAddress: string
  terminalName: string
  terminalMode: TerminalModeCode
  status: StatusCode
  note: string | null
  lastSeenAt: string | null
  version: number
  updatedByName: string | null
  updatedByCode: string | null
  updatedAt: string | null
}

export interface TerminalSearchResult {
  items: TerminalRow[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

export interface TerminalSearchQuery {
  mode?: string
  status?: string
  keyword?: string
  page?: number
  size?: number
}

/** 端末の新規登録・編集の内容（version は編集時の楽観ロック用）。 */
export interface TerminalSaveRequest {
  ipAddress: string
  terminalName: string
  terminalMode: TerminalModeCode
  status: StatusCode
  note: string | null
  version?: number | null
}

export interface TerminalMutationResult {
  message: string
  requestedCount: number
  updatedCount: number
}

const http = new HttpClient({ baseUrl: '/api/user' })

/* ---------- サイト管理 ---------- */

/** 一覧（検索条件・並び替え・ページング）。 */
export function searchSites(query: SiteSearchQuery): Promise<ApiResponse<SiteSearchResult>> {
  return http.get<SiteSearchResult>('/net-sites', {
    params: {
      kind: query.kind?.trim() || undefined,
      judgeMethod: query.judgeMethod?.trim() || undefined,
      category: query.category?.trim() || undefined,
      approvalStatus: query.approvalStatus?.trim() || undefined,
      status: query.status?.trim() || undefined,
      keyword: query.keyword?.trim() || undefined,
      sortBy: query.sortBy?.trim() || undefined,
      sortDir: query.sortDir,
      page: query.page,
      size: query.size
    }
  })
}

/** 新規登録（未承認で登録される）。 */
export function createSite(body: SiteSaveRequest): Promise<ApiResponse<SiteMutationResult>> {
  return http.post<SiteMutationResult>('/net-sites', { body })
}

/** 更新（未承認に戻る＝再承認が必要）。 */
export function updateSite(siteId: number, body: SiteSaveRequest): Promise<ApiResponse<SiteMutationResult>> {
  return http.put<SiteMutationResult>(`/net-sites/${siteId}`, { body })
}

export function deleteSite(siteId: number): Promise<ApiResponse<SiteMutationResult>> {
  return http.delete<SiteMutationResult>(`/net-sites/${siteId}`)
}

/** 承認（1 件ずつ）。 */
export function approveSite(siteId: number): Promise<ApiResponse<SiteMutationResult>> {
  return http.post<SiteMutationResult>(`/net-sites/${siteId}/approval`)
}

/** 却下（承認の取り消しにも使う）。 */
export function rejectSite(siteId: number): Promise<ApiResponse<SiteMutationResult>> {
  return http.post<SiteMutationResult>(`/net-sites/${siteId}/rejection`)
}

/* ---------- 端末コントロール ---------- */

export function searchTerminals(query: TerminalSearchQuery = {}): Promise<ApiResponse<TerminalSearchResult>> {
  return http.get<TerminalSearchResult>('/net-terminals', {
    params: {
      mode: query.mode?.trim() || undefined,
      status: query.status?.trim() || undefined,
      keyword: query.keyword?.trim() || undefined,
      page: query.page,
      size: query.size
    }
  })
}

/** 端末を新規登録する。 */
export function createTerminal(body: TerminalSaveRequest): Promise<ApiResponse<TerminalMutationResult>> {
  return http.post<TerminalMutationResult>('/net-terminals', { body })
}

/** 端末の内容（IP・名称・モード・状態・備考）を編集する。 */
export function updateTerminal(
  terminalId: number,
  body: TerminalSaveRequest
): Promise<ApiResponse<TerminalMutationResult>> {
  return http.put<TerminalMutationResult>(`/net-terminals/${terminalId}`, { body })
}

/** 端末を削除する（物理削除。一覧から消える）。 */
export function deleteTerminal(terminalId: number): Promise<ApiResponse<TerminalMutationResult>> {
  return http.delete<TerminalMutationResult>(`/net-terminals/${terminalId}`)
}

/** 1 台のモードを変更する。 */
export function changeTerminalMode(
  terminalId: number,
  terminalMode: TerminalModeCode,
  version?: number
): Promise<ApiResponse<TerminalMutationResult>> {
  return http.post<TerminalMutationResult>(`/net-terminals/${terminalId}/mode`, {
    body: { terminalMode, version }
  })
}

/** 選択した端末のモードを一括で変更する。 */
export function changeTerminalModes(
  terminalIds: number[],
  terminalMode: TerminalModeCode
): Promise<ApiResponse<TerminalMutationResult>> {
  return http.post<TerminalMutationResult>('/net-terminals/mode', {
    body: { terminalIds, terminalMode }
  })
}

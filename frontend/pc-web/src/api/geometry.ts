import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * 図形管理 API（/api/user/geometry）。
 * user-api の GeometryController と対応する。
 *
 * データは `GEO_図形情報`（2.0 の `TRN_図形作成情報` から移行済み）。
 * 図形は GeoGebra の作図データ（XML）とサムネイル（Base64 PNG）を持ち、
 * 家族で共有する教材として扱う。
 */
export type GeometryFigureType = 'geometry' | 'function'
export type GeometrySort = 'updatedDesc' | 'createdDesc' | 'titleAsc'

export const FIGURE_TYPE_OPTIONS: GeometryFigureType[] = ['geometry', 'function']

/** 図形の種類のラベル（2.0 の選択肢と同じ）。 */
export const FIGURE_TYPE_LABELS: Record<GeometryFigureType, string> = {
  geometry: '幾何図形',
  function: '関数グラフ'
}

/** 一覧の並び替え（2.0 と同じ 3 種類）。 */
export const SORT_OPTIONS: { value: GeometrySort; label: string }[] = [
  { value: 'updatedDesc', label: '更新日が新しい順' },
  { value: 'createdDesc', label: '作成日が新しい順' },
  { value: 'titleAsc', label: '名前順' }
]

/** タグの区切り（2.0 と同じ）。 */
export const TAG_SEPARATOR = '|'

export interface GeometryFigure {
  figureId: number
  /** 利用者に見せる番号（GEO2026… / geometry-demo-1） */
  figureNo: string
  subject: string
  figureType: GeometryFigureType
  /** demo=初期データ / saved=利用者が作ったもの */
  kind: 'demo' | 'saved'
  title: string
  memo: string | null
  tags: string[]
  displayOrder: number
  status: 'ACTIVE' | 'DELETED'
  /** サムネイルがあるか（中身は /figures/{id}/thumbnail で取る） */
  hasThumbnail: boolean
  /** GeoGebraXML の文字数 */
  constructionLength: number
  version: number
  createdAt: string | null
  updatedAt: string | null
}

export interface GeometryTotals {
  figureCount: number
  geometryCount: number
  functionCount: number
  deletedCount: number
}

export interface GeometryFigurePage {
  items: GeometryFigure[]
  totalElements: number
  page: number
  size: number
  totalPages: number
  totals: GeometryTotals
}

/** 作図画面が使う 1 件（XML とサムネイルつき）。 */
export interface GeometryFigureDetail {
  figure: GeometryFigure
  /** GeoGebra の作図データ（XML） */
  construction: string
  /** サムネイル（Base64 PNG。無ければ null） */
  thumbnail: string | null
}

export interface GeometryTag {
  tag: string
  count: number
}

export interface GeometryFigureSave {
  title: string
  figureType: GeometryFigureType
  memo?: string
  tags?: string[]
  construction?: string
  thumbnail?: string
  version?: number
}

export interface GeometryFigureMutation {
  figure: GeometryFigure
  message: string
}

export interface GeometrySimpleResult {
  count: number
  message: string
}

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/user/geometry' })

/** 図形の一覧（検索・並び替え・ページング）。 */
export function searchGeometryFigures(params: {
  keyword?: string
  figureType?: string
  tag?: string
  sort?: string
  includeDeleted?: boolean
  page?: number
  size?: number
}): Promise<ApiResponse<GeometryFigurePage>> {
  return http.get<GeometryFigurePage>('/figures', { params })
}

/** 作図画面用（1 件。GeoGebraXML とサムネイルつき）。 */
export function fetchGeometryFigure(figureId: number): Promise<ApiResponse<GeometryFigureDetail>> {
  return http.get<GeometryFigureDetail>(`/figures/${figureId}`)
}

/** 一覧のカードに出すサムネイルの URL（img src にそのまま使う）。 */
export function geometryThumbnailUrl(figureId: number, version = 0): string {
  return `/api/user/geometry/figures/${figureId}/thumbnail?v=${version}`
}

/** タグの候補。 */
export function fetchGeometryTags(includeDeleted = false): Promise<ApiResponse<{ items: GeometryTag[] }>> {
  return http.get<{ items: GeometryTag[] }>('/tags', { params: { includeDeleted } })
}

export function createGeometryFigure(body: GeometryFigureSave): Promise<ApiResponse<GeometryFigureMutation>> {
  return http.post<GeometryFigureMutation>('/figures', { body })
}

export function updateGeometryFigure(
  figureId: number, body: GeometryFigureSave
): Promise<ApiResponse<GeometryFigureMutation>> {
  return http.put<GeometryFigureMutation>(`/figures/${figureId}`, { body })
}

export function copyGeometryFigure(figureId: number): Promise<ApiResponse<GeometryFigureMutation>> {
  return http.post<GeometryFigureMutation>(`/figures/${figureId}/copy`)
}

export function updateGeometryOrder(
  figureId: number, displayOrder: number, version?: number
): Promise<ApiResponse<GeometryFigureMutation>> {
  return http.patch<GeometryFigureMutation>(`/figures/${figureId}/order`, { body: { displayOrder, version } })
}

export function deleteGeometryFigure(figureId: number): Promise<ApiResponse<GeometrySimpleResult>> {
  return http.delete<GeometrySimpleResult>(`/figures/${figureId}`)
}

/** Base64 のサムネイルを img で使える data URL にする。 */
export function thumbnailDataUrl(base64: string | null): string {
  if (base64 === null || base64 === '') return ''
  return base64.startsWith('data:') ? base64 : `data:image/png;base64,${base64}`
}

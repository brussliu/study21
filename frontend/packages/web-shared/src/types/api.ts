/**
 * Study 2.1 — 共通 API 型定義（PC / Mobile 共用）
 *
 * バックエンド共通仕様（docs/API_CONVENTIONS.md）と対になる。
 * ビジネス意味を持たない「統一レスポンス」「エラーコード」等のみを含む。
 */

/** バックエンド共通エラーコード（統一仕様） */
export type ErrorCode =
  | 'OK'
  | 'VALIDATION_ERROR'
  | 'UNAUTHENTICATED'
  | 'FORBIDDEN'
  | 'NOT_FOUND'
  | 'CONFLICT'
  | 'INTERNAL_ERROR'
  | 'NOT_IMPLEMENTED'

/** クライアント側で発生する追加エラーコード */
export type ClientErrorCode = 'NETWORK_ERROR' | 'TIMEOUT' | 'CANCELED'

/** 統一 API レスポンス */
export interface ApiResponse<T = unknown> {
  success: boolean
  code: string
  message: string
  data: T
  traceId: string
  timestamp: string
}

/** 将来のページネーション用（予約。本段階では使用しない） */
export interface PageResponse<T> {
  items: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
}

/** health エンドポイントのデータ */
export interface HealthData {
  status: 'UP' | 'DOWN' | 'UNKNOWN'
  service: string
}

/** system/info エンドポイントのデータ（非機密情報のみ） */
export interface SystemInfo {
  systemName: string
  version: string
  serviceName: string
  environment: string
  timestamp: string
}

/** クライアント向けヘルス判定状態 */
export type HealthState = 'UP' | 'DOWN' | 'UNKNOWN'

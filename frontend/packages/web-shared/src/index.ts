/**
 * Study 2.1 — web-shared 公開 API
 * デザイントークン / 型 / HTTP / 汎用ユーティリティ / 基礎コンポーネントのみ。
 * ビジネスページ・権限・業務メニュー・旧システム Mock は含まない。
 */
export { designTokens } from './tokens/tokens'
export type { DesignTokens } from './tokens/tokens'

export type {
  ApiResponse,
  PageResponse,
  HealthData,
  SystemInfo,
  HealthState,
  ErrorCode,
  ClientErrorCode
} from './types/api'
export type { Role, ThemeMode, MenuItem } from './types/common'

export { nowIso, formatIsoDateTime, formatIsoDate } from './utils/date'
export { isBlank, truncate, capitalize, toKebabCase } from './utils/string'

export { ApiError } from './http/ApiError'
export type { ApiErrorInit } from './http/ApiError'
export { HttpClient } from './http/HttpClient'
export type { HttpClientConfig, HttpRequestOptions, HttpMethod } from './http/types'

export { AdminApiClient } from './api/AdminApiClient'
export { UserApiClient } from './api/UserApiClient'
export { healthStateOf, healthStateFromError } from './api/health'

export { useToast } from './composables/useToast'
export type { ToastType, ToastItem } from './composables/useToast'
export { useApiLoading } from './composables/useApiLoading'
export type { ApiLoadingState } from './composables/useApiLoading'

export { default as BaseLoading } from './components/BaseLoading.vue'
export { default as BaseEmpty } from './components/BaseEmpty.vue'
export { default as BaseError } from './components/BaseError.vue'
export { default as BaseToast } from './components/BaseToast.vue'
export { default as ToastHost } from './components/ToastHost.vue'
export { default as BaseDialog } from './components/BaseDialog.vue'
export { default as BaseButton } from './components/BaseButton.vue'

import type { ApiResponse, HealthData, HealthState } from '../types/api'

/** ヘルスレスポンスをクライアント表示用の状態へ変換する。 */
export function healthStateOf(response: ApiResponse<HealthData>): HealthState {
  if (!response.success) {
    return 'DOWN'
  }
  const status = response.data?.status
  if (status === 'UP') {
    return 'UP'
  }
  if (status === 'DOWN') {
    return 'DOWN'
  }
  return 'UNKNOWN'
}

/** エラー時のヘルス状態。 */
export function healthStateFromError(): HealthState {
  return 'DOWN'
}

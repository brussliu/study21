import { HttpClient } from '../http/HttpClient'
import type { HttpRequestOptions } from '../http/types'
import type { ApiResponse, HealthData, SystemInfo } from '../types/api'

const DEFAULT_BASE_URL = '/api/admin'

/**
 * 管理側 API クライアント（/api/admin）。
 * 本段階では health / system/info のみ。ビジネス API は含まない。
 */
export class AdminApiClient {
  private readonly http: HttpClient

  constructor(baseUrl?: string, fetchFn?: typeof fetch) {
    this.http = new HttpClient({ baseUrl: baseUrl ?? this.resolveBaseUrl(), fetchFn })
  }

  health(options?: HttpRequestOptions): Promise<ApiResponse<HealthData>> {
    return this.http.get<HealthData>('/health', options)
  }

  getSystemInfo(options?: HttpRequestOptions): Promise<ApiResponse<SystemInfo>> {
    return this.http.get<SystemInfo>('/system/info', options)
  }

  private resolveBaseUrl(): string {
    const env = import.meta.env as unknown as Record<string, string | undefined>
    const value = env.VITE_ADMIN_API_BASE_URL
    return typeof value === 'string' && value.length > 0 ? value : DEFAULT_BASE_URL
  }
}

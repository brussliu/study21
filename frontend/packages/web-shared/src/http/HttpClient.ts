import { ApiError } from './ApiError'
import type { ApiResponse } from '../types/api'
import type { HttpClientConfig, HttpMethod, HttpRequestOptions } from './types'

const DEFAULT_TIMEOUT_MS = 10_000

function isApiResponseBody(value: unknown): value is ApiResponse<unknown> {
  if (typeof value !== 'object' || value === null) {
    return false
  }
  const record = value as Record<string, unknown>
  return (
    typeof record.success === 'boolean' &&
    typeof record.code === 'string' &&
    typeof record.message === 'string'
  )
}

function statusToCode(status: number): string {
  if (status === 400 || status === 422) {
    return 'VALIDATION_ERROR'
  }
  if (status === 401) {
    return 'UNAUTHENTICATED'
  }
  if (status === 403) {
    return 'FORBIDDEN'
  }
  if (status === 404) {
    return 'NOT_FOUND'
  }
  if (status === 409) {
    return 'CONFLICT'
  }
  if (status === 501) {
    return 'NOT_IMPLEMENTED'
  }
  return 'INTERNAL_ERROR'
}

function isAbortError(err: unknown): boolean {
  if (err instanceof DOMException) {
    return err.name === 'AbortError'
  }
  return err instanceof Error && err.name === 'AbortError'
}

/**
 * fetch を基盤とする軽量 HTTP クライアント。
 * - タイムアウト / キャンセル（AbortSignal）
 * - 非 2xx の統一エラー変換（401/403/404/500 等）
 * - Trace ID の取得
 * - JSON レスポンスの統一パース
 */
export class HttpClient {
  private readonly baseUrl: string
  private readonly defaultTimeoutMs: number
  private readonly fetchFn: typeof fetch

  constructor(config: HttpClientConfig) {
    this.baseUrl = config.baseUrl.replace(/\/+$/, '')
    this.defaultTimeoutMs = config.timeoutMs ?? DEFAULT_TIMEOUT_MS
    this.fetchFn = config.fetchFn ?? ((input, init) => fetch(input, init))
  }

  get<T>(path: string, options: HttpRequestOptions = {}): Promise<ApiResponse<T>> {
    return this.request<T>('GET', path, options)
  }

  post<T>(path: string, options: HttpRequestOptions = {}): Promise<ApiResponse<T>> {
    return this.request<T>('POST', path, options)
  }

  put<T>(path: string, options: HttpRequestOptions = {}): Promise<ApiResponse<T>> {
    return this.request<T>('PUT', path, options)
  }

  patch<T>(path: string, options: HttpRequestOptions = {}): Promise<ApiResponse<T>> {
    return this.request<T>('PATCH', path, options)
  }

  delete<T>(path: string, options: HttpRequestOptions = {}): Promise<ApiResponse<T>> {
    return this.request<T>('DELETE', path, options)
  }

  async request<T>(
    method: HttpMethod,
    path: string,
    options: HttpRequestOptions = {}
  ): Promise<ApiResponse<T>> {
    const url = this.buildUrl(path, options.params)
    const timeoutMs = options.timeoutMs ?? this.defaultTimeoutMs
    const controller = new AbortController()
    const externalSignal = options.signal
    const onExternalAbort = (): void => controller.abort(externalSignal?.reason)

    if (externalSignal) {
      if (externalSignal.aborted) {
        controller.abort(externalSignal.reason)
      } else {
        externalSignal.addEventListener('abort', onExternalAbort, { once: true })
      }
    }

    let timedOut = false
    const timer = setTimeout(() => {
      timedOut = true
      controller.abort()
    }, timeoutMs)

    const headers: Record<string, string> = {
      Accept: 'application/json',
      ...(options.headers ?? {})
    }
    let body: string | undefined
    if (options.body !== undefined) {
      headers['Content-Type'] = 'application/json'
      body = JSON.stringify(options.body)
    }

    try {
      const response = await this.fetchFn(url, {
        method,
        headers,
        body,
        signal: controller.signal
      })
      return await this.parse<T>(response)
    } catch (err) {
      throw this.normalizeError(err, timedOut)
    } finally {
      clearTimeout(timer)
      externalSignal?.removeEventListener('abort', onExternalAbort)
    }
  }

  private buildUrl(path: string, params?: HttpRequestOptions['params']): string {
    const cleanPath = path.startsWith('/') ? path : `/${path}`
    const url = `${this.baseUrl}${cleanPath}`
    if (!params) {
      return url
    }
    const query = Object.entries(params)
      .filter(([, value]) => value !== undefined && value !== null)
      .map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
      .join('&')
    return query.length > 0 ? `${url}?${query}` : url
  }

  private async parse<T>(response: Response): Promise<ApiResponse<T>> {
    const headerTraceId = response.headers.get('x-trace-id') ?? undefined
    const text = await response.text()
    let jsonBody: unknown
    if (text.length > 0) {
      try {
        jsonBody = JSON.parse(text) as unknown
      } catch {
        jsonBody = undefined
      }
    }

    if (!response.ok) {
      const apiBody = isApiResponseBody(jsonBody) ? jsonBody : undefined
      throw new ApiError({
        code: apiBody?.code ?? statusToCode(response.status),
        message: apiBody?.message ?? `HTTP ${response.status}`,
        status: response.status,
        traceId: apiBody?.traceId ?? headerTraceId,
        data: apiBody?.data
      })
    }

    if (isApiResponseBody(jsonBody)) {
      return jsonBody as ApiResponse<T>
    }
    return {
      success: true,
      code: 'OK',
      message: 'OK',
      data: jsonBody as T,
      traceId: headerTraceId ?? '',
      timestamp: new Date().toISOString()
    }
  }

  private normalizeError(err: unknown, timedOut: boolean): ApiError {
    if (err instanceof ApiError) {
      return err
    }
    if (timedOut) {
      return new ApiError({ code: 'TIMEOUT', message: 'リクエストがタイムアウトしました' })
    }
    if (isAbortError(err)) {
      return new ApiError({ code: 'CANCELED', message: 'リクエストがキャンセルされました' })
    }
    return new ApiError({ code: 'NETWORK_ERROR', message: 'ネットワークエラーが発生しました', cause: err })
  }
}

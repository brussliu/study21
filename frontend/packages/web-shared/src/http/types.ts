export type HttpMethod = 'GET' | 'POST' | 'PUT' | 'PATCH' | 'DELETE'

export interface HttpClientConfig {
  baseUrl: string
  timeoutMs?: number
  fetchFn?: typeof fetch
}

export interface HttpRequestOptions {
  params?: Record<string, string | number | boolean | undefined>
  timeoutMs?: number
  signal?: AbortSignal
  headers?: Record<string, string>
  body?: unknown
}

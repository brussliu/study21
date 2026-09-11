import { describe, expect, it, vi } from 'vitest'
import { HttpClient } from '../src/http/HttpClient'
import { ApiError } from '../src/http/ApiError'
import { AdminApiClient } from '../src/api/AdminApiClient'
import { UserApiClient } from '../src/api/UserApiClient'
import { healthStateOf } from '../src/api/health'
import type { ApiResponse, HealthData } from '../src/types/api'

function okResponse(body: unknown, status = 200, headers: Record<string, string> = {}): Response {
  return {
    ok: status >= 200 && status < 300,
    status,
    headers: { get: (name: string) => headers[name.toLowerCase()] ?? null },
    text: async () => JSON.stringify(body)
  } as unknown as Response
}

describe('HttpClient', () => {
  it('2xx の統一レスポンスを返す', async () => {
    const body: ApiResponse<{ name: string }> = {
      success: true,
      code: 'OK',
      message: 'OK',
      data: { name: 'a' },
      traceId: 't-1',
      timestamp: '2024-01-01T00:00:00Z'
    }
    const fetchFn = vi.fn(async () => okResponse(body))
    const client = new HttpClient({ baseUrl: 'http://localhost:8081', fetchFn })
    const result = await client.get<{ name: string }>('/health')
    expect(result.success).toBe(true)
    expect(result.data.name).toBe('a')
    expect(result.traceId).toBe('t-1')
  })

  it.each([
    [401, 'UNAUTHENTICATED'],
    [403, 'FORBIDDEN'],
    [404, 'NOT_FOUND'],
    [500, 'INTERNAL_ERROR']
  ])('非 2xx の %i を %s へ変換する', async (status, code) => {
    const fetchFn = vi.fn(async () => okResponse({}, status))
    const client = new HttpClient({ baseUrl: 'http://x', fetchFn })
    await expect(client.get('/health')).rejects.toMatchObject({ code, status })
  })

  it('レスポンスボディの code/message/traceId を優先する', async () => {
    const body = { success: false, code: 'FORBIDDEN', message: 'アクセス権限がありません', data: null, traceId: 't-9', timestamp: '' }
    const fetchFn = vi.fn(async () => okResponse(body, 403))
    const client = new HttpClient({ baseUrl: 'http://x', fetchFn })
    await expect(client.get('/health')).rejects.toMatchObject({
      code: 'FORBIDDEN',
      message: 'アクセス権限がありません',
      status: 403,
      traceId: 't-9'
    })
  })

  it('X-Trace-Id ヘッダーを取得する', async () => {
    const body: ApiResponse<null> = { success: true, code: 'OK', message: 'OK', data: null, traceId: 'h-1', timestamp: '' }
    const fetchFn = vi.fn(async () => okResponse(body, 200, { 'x-trace-id': 'h-1' }))
    const client = new HttpClient({ baseUrl: 'http://x', fetchFn })
    const result = await client.get('/health')
    expect(result.traceId).toBe('h-1')
  })

  it('ネットワークエラーを NETWORK_ERROR へ変換する', async () => {
    const fetchFn = vi.fn(async () => {
      throw new Error('connection refused')
    })
    const client = new HttpClient({ baseUrl: 'http://x', fetchFn })
    await expect(client.get('/health')).rejects.toBeInstanceOf(ApiError)
    await expect(client.get('/health')).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
  })

  it('AbortSignal でキャンセルする', async () => {
    const controller = new AbortController()
    const fetchFn = vi.fn(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () =>
            reject(new DOMException('aborted', 'AbortError'))
          )
        })
    )
    const client = new HttpClient({ baseUrl: 'http://x', fetchFn })
    const promise = client.get('/health', { signal: controller.signal })
    controller.abort()
    await expect(promise).rejects.toMatchObject({ code: 'CANCELED' })
  })

  it('タイムアウトする', async () => {
    vi.useFakeTimers()
    const fetchFn = vi.fn(
      (_url: string, init: RequestInit) =>
        new Promise((_resolve, reject) => {
          init.signal?.addEventListener('abort', () =>
            reject(new DOMException('aborted', 'AbortError'))
          )
        })
    )
    const client = new HttpClient({ baseUrl: 'http://x', timeoutMs: 1000, fetchFn })
    const promise = client.get('/health')
    vi.advanceTimersByTime(1001)
    await expect(promise).rejects.toMatchObject({ code: 'TIMEOUT' })
    vi.useRealTimers()
  })
})

describe('AdminApiClient / UserApiClient', () => {
  it('AdminApiClient.health は /api/admin/health を呼ぶ', async () => {
    const health: HealthData = { status: 'UP', service: 'admin-api' }
    const body: ApiResponse<HealthData> = { success: true, code: 'OK', message: 'OK', data: health, traceId: 't', timestamp: '' }
    const fetchFn = vi.fn(async () => okResponse(body, 200, {}))
    const client = new AdminApiClient('http://localhost:8081/api/admin', fetchFn)
    const result = await client.health()
    expect(fetchFn).toHaveBeenCalledWith(
      'http://localhost:8081/api/admin/health',
      expect.objectContaining({ method: 'GET' })
    )
    expect(result.data.status).toBe('UP')
  })

  it('UserApiClient.health は /api/user/health を呼ぶ', async () => {
    const health: HealthData = { status: 'UP', service: 'user-api' }
    const body: ApiResponse<HealthData> = { success: true, code: 'OK', message: 'OK', data: health, traceId: 't', timestamp: '' }
    const fetchFn = vi.fn(async () => okResponse(body, 200, {}))
    const client = new UserApiClient('http://localhost:8082/api/user', fetchFn)
    const result = await client.health()
    expect(fetchFn).toHaveBeenCalledWith(
      'http://localhost:8082/api/user/health',
      expect.objectContaining({ method: 'GET' })
    )
    expect(result.data.service).toBe('user-api')
  })
})

describe('healthStateOf', () => {
  it('success と data.status から状態を判定する', () => {
    expect(healthStateOf({ success: true, code: 'OK', message: 'OK', data: { status: 'UP', service: 'x' }, traceId: '', timestamp: '' })).toBe('UP')
    expect(healthStateOf({ success: false, code: 'INTERNAL_ERROR', message: '', data: { status: 'DOWN', service: 'x' }, traceId: '', timestamp: '' })).toBe('DOWN')
  })
})

import { describe, expect, it, vi } from 'vitest'
import { AdminApiClient, UserApiClient } from '@study21/web-shared'
import type { ApiResponse, HealthData } from '@study21/web-shared'

function healthResponse(service: string): Response {
  const body: ApiResponse<HealthData> = {
    success: true,
    code: 'OK',
    message: 'OK',
    data: { status: 'UP', service },
    traceId: 't-1',
    timestamp: '2024-01-01T00:00:00Z'
  }
  return {
    ok: true,
    status: 200,
    headers: { get: () => null },
    text: async () => JSON.stringify(body)
  } as unknown as Response
}

describe('pc-web API clients', () => {
  it('AdminApiClient は /api/admin 配下を呼ぶ', async () => {
    const fetchFn = vi.fn(async () => healthResponse('admin-api'))
    const client = new AdminApiClient('http://localhost:8081/api/admin', fetchFn)
    const result = await client.health()
    expect(fetchFn).toHaveBeenCalledWith(
      'http://localhost:8081/api/admin/health',
      expect.objectContaining({ method: 'GET' })
    )
    expect(result.data.status).toBe('UP')
    expect(result.data.service).toBe('admin-api')
  })

  it('UserApiClient は /api/user 配下を呼ぶ', async () => {
    const fetchFn = vi.fn(async () => healthResponse('user-api'))
    const client = new UserApiClient('http://localhost:8082/api/user', fetchFn)
    const result = await client.getSystemInfo()
    expect(fetchFn).toHaveBeenCalledWith(
      'http://localhost:8082/api/user/system/info',
      expect.objectContaining({ method: 'GET' })
    )
    expect(result.success).toBe(true)
  })
})

import { describe, expect, it, vi, afterEach } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { useSystemStore } from '@/stores/system'

function makeHealth(service: string): Response {
  return {
    ok: true,
    status: 200,
    headers: { get: () => null },
    text: async () =>
      JSON.stringify({
        success: true,
        code: 'OK',
        message: 'OK',
        data: { status: 'UP', service },
        traceId: 't',
        timestamp: '2024-01-01T00:00:00Z'
      })
  } as unknown as Response
}

describe('mobile system store health handling', () => {
  afterEach(() => {
    vi.unstubAllGlobals()
  })

  it('checkHealth は両 API の状態を UP にする', async () => {
    setActivePinia(createPinia())
    vi.stubGlobal(
      'fetch',
      vi.fn(async (url: unknown) => {
        const target = String(url)
        return makeHealth(target.includes('/api/admin') ? 'admin-api' : 'user-api')
      })
    )

    const store = useSystemStore()
    await store.checkHealth()

    expect(store.adminHealth).toBe('UP')
    expect(store.userHealth).toBe('UP')
  })
})

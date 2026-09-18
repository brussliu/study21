import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  addReadingBookToShelf,
  removeReadingBookFromShelf,
  searchReadingBooks
} from '@/api/reading'

/**
 * 読書管理 API（`/api/user/reading`）の呼び方。
 *
 * 2026-09-14 の決定で足した 2 つを固定する:
 * ・一覧の `scope`（ALL / GLOBAL / FAMILY）と `shelf=MINE`（【自分の本棚】）
 * ・【本棚に入れる】＝ PUT `/books/{id}/shelf` ／【本棚から外す】＝ DELETE `/books/{id}/shelf`
 *   （どちらもサーバ側が自分の行だけを触る冪等な操作）
 */

function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

type Call = { url: string; method: string; json: Record<string, unknown> | null }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    const body = init.body
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      json: typeof body === 'string' ? (JSON.parse(body) as Record<string, unknown>) : null
    }
  })
}

function page(): unknown {
  return {
    items: [],
    totalElements: 0,
    page: 1,
    size: 24,
    totalPages: 1,
    totals: {
      bookCount: 0,
      readingCount: 0,
      finishedCount: 0,
      totalMinutes: 0,
      markCount: 0,
      pdfCount: 0,
      uncategorizedCount: 0,
      myShelfCount: 0
    }
  }
}

describe('読書管理 API: 公開範囲と自分の本棚', () => {
  let fetchMock: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchMock = vi.fn(async () => ok(page()))
    vi.stubGlobal('fetch', fetchMock)
  })

  it('一覧は scope と shelf と language をそのままクエリに載せる', async () => {
    await searchReadingBooks({ scope: 'FAMILY', shelf: 'MINE', language: '日本語', page: 2, size: 24 })

    const url = recorded(fetchMock)[0]?.url ?? ''
    expect(url).toContain('/api/user/reading/books?')
    expect(url).toContain('scope=FAMILY')
    expect(url).toContain('shelf=MINE')
    expect(url).toContain('language=%E6%97%A5%E6%9C%AC%E8%AA%9E')
    expect(url).toContain('page=2')
  })

  it('scope / shelf を省略したときはクエリに載せない（既定はサーバが決める）', async () => {
    await searchReadingBooks({ page: 1, size: 24 })

    const url = recorded(fetchMock)[0]?.url ?? ''
    expect(url).not.toContain('scope=')
    expect(url).not.toContain('shelf=')
  })

  it('【本棚に入れる】は PUT、【本棚から外す】は DELETE をその本に送る', async () => {
    fetchMock.mockImplementation(async () => ok({ book: { bookId: 7, inMyShelf: true }, message: '入れた' }))

    await addReadingBookToShelf(7)
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/reading/books/7/shelf',
      method: 'PUT'
    })

    fetchMock.mockImplementation(async () => ok({ book: { bookId: 7, inMyShelf: false }, message: '外した' }))
    await removeReadingBookFromShelf(7)
    expect(recorded(fetchMock).at(-1)).toMatchObject({
      url: '/api/user/reading/books/7/shelf',
      method: 'DELETE'
    })
  })
})

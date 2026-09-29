import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import JapaneseWordView from '@/views/japanese/JapaneseWordView.vue'
import type { JpnDetailVersion, JpnWord, JpnWordDetailResult, JpnWordTotals } from '@/api/japanese'

/**
 * 詳細の「版の履歴」（版の一覧 ＋【この版を使う】）。
 *
 * 確かめる接縫は画面（`JapaneseWordView` → 修正ダイアログ）と API のリクエスト:
 * ・版の一覧が新しい順に出る（版番号・生效中・AI/人・段落の件数）
 * ・【この版を使う】は、いま読んでいる詳細の `バージョン` を付けて PUT する
 * ・409 のときは読み直しを促し、画面が壊れない
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function conflict(message: string): Response {
  return new Response(
    JSON.stringify({ success: false, code: 'CONFLICT', message, data: null, timestamp: '' }),
    { status: 409, headers: { 'Content-Type': 'application/json' } }
  )
}

function word(overrides: Partial<JpnWord> = {}): JpnWord {
  return {
    wordId: 101,
    word: '愛する',
    reading: 'あいする',
    jlptLevel: 'N3',
    partOfSpeech: '動詞',
    chineseMeaning: null,
    // 一覧の「詳細情報件数」列（1 件取得の戻りでは入らない）
    detailCounts: null,
    stateCode: 'ACTIVE',
    note: null,
    version: 7,
    book: null,
    category: null,
    level: null,
    wordSeq: null,
    collectionCount: 0,
    learnState: 'NOT_STARTED',
    mastery: 0,
    answeredCount: 0,
    correctCount: 0,
    favorite: false,
    learned: false,
    lastStudiedAt: null,
    nextReviewAt: null,
    aiState: { detail: 'SUCCEEDED', reading: null, context: null, kanji: null },
    ...overrides
  }
}

/** 表示中の詳細（版数 3 ＝ その版の バージョン 12）。 */
function detailResult(): JpnWordDetailResult {
  return {
    word: word(),
    collections: [],
    questions: [],
    detail: {
      detailId: 900,
      contentVersion: 3,
      version: 12,
      aiProvider: 'qwen',
      aiModel: 'qwen3.7-plus',
      fetchedAt: '2026-10-01T09:00:00',
      detail: {
        partOfSpeech: '動詞',
        chineseMeaning: '爱；喜爱',
        coreMeaning: '爱；喜爱',
        descriptionJa: '人や物を大切に思う気持ち。',
        manuallyCorrected: false,
        senses: [{ number: 1, japanese: '大切に思う気持ち。', chinese: '爱', context: '', style: '', noteJapanese: null, noteChinese: null }],
        examples: [],
        patterns: [],
        dialogs: [],
        synonyms: [],
        cautions: [],
        conjugations: [],
        collocations: [],
        relatedWords: [],
        usageNotes: [],
        practices: []
      }
    }
  }
}

/** 版の一覧（新しい順）。AI の新しい版が生效中、古い版は人が直した版。 */
function versionItems(): JpnDetailVersion[] {
  const counts = {
    senses: 1, examples: 2, patterns: 1, dialogs: 0, synonyms: 0,
    cautions: 1, collocations: 0, relatedWords: 0, usageNotes: 1, practices: 0
  }
  return [
    {
      detailId: 900, contentVersion: 3, version: 12, stateCode: 'ACTIVE', active: true, manual: false,
      aiProvider: 'qwen', aiModel: 'qwen3.7-plus', generationId: 77, fetchedAt: '2026-10-01T09:00:00',
      note: null, createdAt: '2026-10-01T09:00:00', updatedAt: '2026-10-01T09:00:00', counts
    },
    {
      detailId: 800, contentVersion: 2, version: 21, stateCode: 'ARCHIVED', active: false, manual: true,
      aiProvider: null, aiModel: null, generationId: null, fetchedAt: '2026-09-20T09:00:00',
      note: '人が直した', createdAt: '2026-09-20T09:00:00', updatedAt: '2026-09-20T09:00:00',
      counts: { ...counts, examples: 5 }
    }
  ]
}

const TOTALS: JpnWordTotals = {
  wordCount: 1, learnedCount: 0, favoriteCount: 0, averageMastery: 0, answeredCount: 0
}

type Handler = (url: string, method: string, body: Record<string, unknown> | null) => Response | null

async function setup(handlers?: Handler): Promise<{
  wrapper: ReturnType<typeof mount>
  calls: { url: string; method: string; body: Record<string, unknown> | null }[]
}> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const calls: { url: string; method: string; body: Record<string, unknown> | null }[] = []
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target = String(url)
    const method = (init?.method ?? 'GET').toUpperCase()
    const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
    calls.push({ url: target, method, body })
    const handled = handlers?.(target, method, body)
    if (handled) return handled
    if (method === 'GET' && target.startsWith('/api/user/japanese/words?')) {
      return ok({
        items: [word()], totalElements: 1, page: 1, size: 20, totalPages: 1, totals: TOTALS
      })
    }
    if (method === 'GET' && target === '/api/user/japanese/words/101') {
      return ok(detailResult())
    }
    if (method === 'GET' && target === '/api/user/japanese/words/101/detail-versions') {
      return ok({ items: versionItems() })
    }
    return ok({ word: word(), message: 'OK' })
  })
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(JapaneseWordView, { global: { plugins: [pinia] } })
  await flushPromises()
  // 一覧の【修正】から詳細編集ダイアログを開く（版の一覧もここで読み込む）
  await wrapper.get('[data-jp-word-row="101"] [data-jp-edit]').trigger('click')
  await flushPromises()
  return { wrapper, calls }
}

function putCalls(calls: { url: string; method: string; body: Record<string, unknown> | null }[]) {
  return calls.filter((call) => call.method === 'PUT')
}

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

describe('詳細の版の履歴', () => {
  beforeEach(() => {
    useToast().items.splice(0)
    vi.restoreAllMocks()
  })

  it('版の一覧を新しい順に、版番号・生效中・AI/人・段落の件数つきで出す', async () => {
    const { wrapper } = await setup()

    const items = wrapper.findAll('[data-jp-version-item]')
    expect(items).toHaveLength(2)

    // 新しい版（先頭）が「生效中」
    const first = items[0]!
    expect(first.get('[data-jp-version-no]').text()).toContain('3')
    expect(first.get('[data-jp-version-active]').text()).toContain('生效中')
    expect(first.text()).toContain('qwen3.7-plus')

    // 古い版は人が直した版で、生效中ではない
    const second = items[1]!
    expect(second.text()).toContain('2')
    expect(second.text()).toContain('人が作成')
    expect(second.find('[data-jp-version-active]').exists()).toBe(false)

    // 段落の件数（例文 5 件など）を出す
    expect(second.text()).toContain('例文')
    expect(second.text()).toContain('5')
  })

  it('【この版を使う】は、その版の バージョン を付けて PUT し、表示を差し替える', async () => {
    const { wrapper, calls } = await setup((url, method) => {
      if (method === 'PUT' && url === '/api/user/japanese/words/101/detail-versions/800/active') {
        return ok(detailResult())
      }
      return null
    })

    // 生效中でない版（ARCHIVED の 800）を使う
    await wrapper.findAll('[data-jp-version-use]')[0]!.trigger('click')
    await flushPromises()

    const put = putCalls(calls).find((call) => call.url.includes('/detail-versions/'))
    expect(put?.url).toBe('/api/user/japanese/words/101/detail-versions/800/active')
    // 楽観的ロックは**その版の バージョン**（800 の 21）。表示中の版の contentVersion（3）ではない
    expect(put?.body).toEqual({ version: 21 })
    expect(toastMessages().some((message) => message.includes('切り替え'))).toBe(true)
  })

  it('409 のときは読み直しを促し、画面は壊れない', async () => {
    const { wrapper, calls } = await setup((url, method) => {
      if (method === 'PUT' && url.includes('/detail-versions/')) {
        return conflict('版が更新されています。再読み込みしてください。')
      }
      return null
    })

    await wrapper.findAll('[data-jp-version-use]')[0]!.trigger('click')
    await flushPromises()

    // 版の一覧を取り直している（読み直し）
    expect(calls.filter((call) => call.url.endsWith('/detail-versions')).length).toBeGreaterThan(1)
    // 画面は開いたままで、理由が分かる
    expect(wrapper.find('[data-jp-word-dialog]').exists()).toBe(true)
    expect(wrapper.text()).toContain('他の操作で先に更新されました。読み直してください。')
  })

  it('生效中の版には【この版を使う】を出さない', async () => {
    const { wrapper } = await setup()

    const first = wrapper.findAll('[data-jp-version-item]')[0]!
    expect(first.find('[data-jp-version-use]').exists()).toBe(false)
    expect(wrapper.findAll('[data-jp-version-item]')[1]!.find('[data-jp-version-use]').exists()).toBe(true)
  })
})

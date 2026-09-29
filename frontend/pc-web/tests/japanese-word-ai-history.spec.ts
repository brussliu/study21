import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import JpnAiHistoryDialog from '@/features/japanese-word/JpnAiHistoryDialog.vue'
import type { JpnDetailVersion, JpnQuestionVersion } from '@/api/japanese'

/**
 * AI 取得の履歴（一覧の「取得状態」のタグから開く）。
 *
 * <p>2.0 の英語学習（`word.jsp`）の「詳細情報取得履歴」と同じで、**AI の取得 1 回 = 1 行**を出し、
 * 【この版を使う】で使う版を切り替える。</p>
 *
 * <ul>
 *   <li>A・B … 詳細の版（人が直した版も混ざる／楽観的ロックは版ごとの `version`）</li>
 *   <li>C・D・E … 問題の版（C は C1 と C2 の 2 つ。失敗した版は切り替えられない）</li>
 * </ul>
 */

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function detailVersion(overrides: Partial<JpnDetailVersion> = {}): JpnDetailVersion {
  return {
    detailId: 12,
    contentVersion: 2,
    version: 5,
    stateCode: 'ACTIVE',
    active: true,
    manual: false,
    aiProvider: 'deepseek',
    aiModel: 'deepseek-v4-flash',
    generationId: 31,
    fetchedAt: '2026-09-25T20:31:55',
    note: null,
    createdAt: '2026-09-25T20:31:55',
    updatedAt: '2026-09-25T20:31:55',
    counts: {
      senses: 2, examples: 3, patterns: 1, dialogs: 1, synonyms: 1,
      cautions: 1, collocations: 2, relatedWords: 1, usageNotes: 1, practices: 1
    },
    ...overrides
  }
}

function questionVersion(overrides: Partial<JpnQuestionVersion> = {}): JpnQuestionVersion {
  return {
    questionType: 'C1_READING',
    contentVersion: 2,
    questionCount: 1,
    active: true,
    generationState: 'SUCCEEDED',
    aiProvider: 'deepseek',
    aiModel: 'deepseek-v4-flash',
    generatedCount: 1,
    failedCount: 0,
    errorMessage: null,
    startedAt: '2026-09-25T20:30:00',
    finishedAt: '2026-09-25T20:30:05',
    ...overrides
  }
}

type Call = { url: string; method: string; body: Record<string, unknown> | null }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      body: init.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
    }
  })
}

async function setup(options: {
  section: 'AB' | 'C' | 'D' | 'E'
  details?: JpnDetailVersion[]
  questions?: JpnQuestionVersion[]
  failLoad?: boolean
}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useToast().items.splice(0)

  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target = String(url)
    const method = (init?.method ?? 'GET').toUpperCase()
    if (options.failLoad === true && method === 'GET') {
      return new Response(JSON.stringify({ success: false, message: '履歴を読み込めませんでした。' }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' }
      })
    }
    if (method === 'GET' && target.endsWith('/detail-versions')) {
      return ok({ items: options.details ?? [] })
    }
    if (method === 'GET' && target.endsWith('/question-versions')) {
      const items = options.questions ?? []
      return ok({ items, totalCount: items.length })
    }
    return ok({ items: options.questions ?? [], totalCount: 0 }, '使用する版を切り替えました。')
  })
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(JpnAiHistoryDialog, {
    props: { visible: true, wordId: 101, section: options.section },
    global: { plugins: [pinia] }
  })
  await flushPromises()
  return { wrapper, fetchMock }
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('AI 取得の履歴', () => {
  it('A・B は詳細の版を出し、【この版を使う】で切り替える（楽観ロックはその版の値）', async () => {
    const { wrapper, fetchMock } = await setup({
      section: 'AB',
      details: [
        detailVersion(),
        detailVersion({ detailId: 9, contentVersion: 1, version: 3, active: false, manual: true })
      ]
    })

    expect(recorded(fetchMock).some((call) => call.url === '/api/user/japanese/words/101/detail-versions')).toBe(true)
    const items = wrapper.findAll('[data-jp-history-item]')
    expect(items).toHaveLength(2)
    expect(items[0]?.get('[data-jp-history-version]').text()).toBe('版 2')
    expect(items[0]?.get('[data-jp-history-active]').text()).toBe('使用中')
    // 使用中の版には【この版を使う】を出さない
    expect(items[0]?.find('[data-jp-history-use]').exists()).toBe(false)
    expect(items[1]?.get('[data-jp-history-version]').text()).toBe('版 1')
    expect(items[1]?.text()).toContain('人が作成')

    await items[1]?.get('[data-jp-history-use]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.method === 'PUT')
    expect(call?.url).toBe('/api/user/japanese/words/101/detail-versions/9/active')
    // 切り替えたい版の バージョン（表示中の版ではない）
    expect(call?.body).toEqual({ version: 3 })
    expect(wrapper.emitted('activated')).toBeTruthy()
    expect(useToast().items.map((item) => item.message).join(' ')).toContain('使用する版を切り替えました。')
  })

  it('C は C1 と C2 の版を分けて出す（問題数・モデル・失敗の理由つき）', async () => {
    const { wrapper } = await setup({
      section: 'C',
      questions: [
        questionVersion(),
        questionVersion({ contentVersion: 1, active: false, aiModel: 'qwen3.7-plus' }),
        questionVersion({ questionType: 'C2_KANJI', contentVersion: 1, active: true }),
        questionVersion({
          questionType: 'C2_KANJI', contentVersion: 2, active: false, questionCount: 0,
          generationState: 'FAILED', errorMessage: 'AI がリクエストを受け付けませんでした（HTTP 400）。'
        })
      ]
    })

    const groups = wrapper.findAll('[data-jp-history-group]')
    expect(groups.map((group) => group.attributes('data-jp-history-group'))).toEqual(['C1_READING', 'C2_KANJI'])
    expect(groups[0]?.text()).toContain('C1 読み問題')
    expect(groups[1]?.text()).toContain('C2 漢字問題')

    const c1 = groups[0]?.findAll('[data-jp-history-item]') ?? []
    expect(c1).toHaveLength(2)
    expect(c1[0]?.get('[data-jp-history-active]').text()).toBe('使用中')
    expect(c1[1]?.text()).toContain('qwen3.7-plus')
    // 失敗した版は理由を出し、切り替えられない
    const c2 = groups[1]?.findAll('[data-jp-history-item]') ?? []
    expect(c2[1]?.text()).toContain('失敗')
    expect((c2[1]?.get('[data-jp-history-use]').element as HTMLButtonElement).disabled).toBe(true)
    expect(groups[1]?.get('[data-jp-history-failure]').text()).toContain('HTTP 400')
  })

  it('C の版を切り替えると、その種別の版を有効にする API を呼ぶ', async () => {
    const { wrapper, fetchMock } = await setup({
      section: 'C',
      questions: [
        questionVersion(),
        questionVersion({ contentVersion: 1, active: false })
      ]
    })

    const items = wrapper.findAll('[data-jp-history-item]')
    await items[1]?.get('[data-jp-history-use]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.method === 'PUT')
    expect(call?.url).toBe('/api/user/japanese/words/101/question-versions/C1_READING/active')
    expect(call?.body).toEqual({ contentVersion: 1 })
    expect(wrapper.emitted('activated')).toBeTruthy()
  })

  it('D・E はその種別だけを出す', async () => {
    const { wrapper, fetchMock } = await setup({
      section: 'E',
      questions: [questionVersion({ questionType: 'E_KANJI_USAGE', contentVersion: 1, active: true })]
    })

    const groups = wrapper.findAll('[data-jp-history-group]')
    expect(groups.map((group) => group.attributes('data-jp-history-group'))).toEqual(['E_KANJI_USAGE'])
    // A・B の詳細の版は引かない
    expect(recorded(fetchMock).some((call) => call.url.endsWith('/detail-versions'))).toBe(false)
    expect(wrapper.text()).toContain('E 漢字問題の履歴')
  })

  it('履歴が無いときは、その旨を出す', async () => {
    const { wrapper } = await setup({ section: 'D', questions: [] })

    expect(wrapper.get('[data-jp-history-empty]').text()).toContain('まだ取得の履歴がありません')
    expect(wrapper.find('[data-jp-history-use]').exists()).toBe(false)
  })

  it('読み込みに失敗したら理由を出す', async () => {
    const { wrapper } = await setup({ section: 'AB', failLoad: true })

    expect(wrapper.get('[data-jp-history-error]').text().length).toBeGreaterThan(0)
  })

  it('表示していないときは何も出さない（履歴も読みに行かない）', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(JpnAiHistoryDialog, {
      props: { visible: false, wordId: 101, section: 'AB' },
      global: { plugins: [pinia] }
    })
    await flushPromises()

    expect(wrapper.find('[data-jp-history-dialog]').exists()).toBe(false)
    expect(fetchMock).not.toHaveBeenCalled()
  })
})

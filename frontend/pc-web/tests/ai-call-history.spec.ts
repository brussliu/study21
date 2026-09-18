import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import AiCallHistoryView from '@/views/batch/AiCallHistoryView.vue'
import type { AiCallDetail, AiCallRow } from '@/api/batch'

/**
 * AI呼出履歴（バッチ管理＞AI呼出履歴）。
 *
 * 2.0 の履歴管理画面（history.jsp）の「AI呼出履歴」タブに当たる。
 * 2.0 の `BAT_AI呼出履歴情報`（66,800 件・約 400MB）を全件移行したものを読む。
 * 一覧では本文（プロンプト・レスポンス）を返さず、【詳細】で 1 件だけ取るのが要点。
 */
function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function callRow(overrides: Partial<AiCallRow> = {}): AiCallRow {
  return {
    callId: 66974,
    executionId: 49934,
    batchCode: 'batL03',
    processKey: 'study-monitor/snapshot/30437/first',
    language: 'ja',
    aiType: 'qwen',
    modelName: 'qwen3-vl-flash',
    callUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions',
    httpStatus: 200,
    startTime: '2026-09-12T13:40:00',
    endTime: '2026-09-12T13:40:24',
    durationMs: 24541,
    result: 'SUCCESS',
    resultLabel: '成功',
    errorCode: null,
    errorMessage: null,
    inputTokens: 1200,
    outputTokens: 340,
    totalTokens: 1540,
    ...overrides
  }
}

type Call = { url: string }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => ({ url: String(call[0]) }))
}

beforeEach(() => {
  useToast().items.splice(0)
})

async function setup(options: { items?: AiCallRow[]; total?: number; detail?: Partial<AiCallDetail> } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const items = options.items ?? [
    callRow(),
    callRow({
      callId: 66800, executionId: null, batchCode: 'batC04', aiType: 'deepseek',
      modelName: 'deepseek-chat', language: 'ja-zh', result: 'FAILURE', resultLabel: '失敗',
      httpStatus: 500, errorCode: 'TIMEOUT', errorMessage: 'AI の応答がありませんでした',
      durationMs: 30000, inputTokens: null, outputTokens: null, totalTokens: null
    })
  ]
  const total = options.total ?? items.length
  const detail: AiCallDetail = {
    ...callRow(),
    prompt: 'この画像の学習状況を判定してください。',
    response: '{"result":"学習中","confidence":0.92}',
    bodyTruncated: false,
    bodyLimit: 200000,
    ...options.detail
  }

  const fetchMock = vi.fn(async (url: string) => {
    const target = String(url)
    if (target.includes('/ai-calls/filters')) {
      return ok({ aiTypes: ['deepseek', 'qwen'], batchCodes: ['batC04', 'batL03'], models: ['qwen3-vl-flash'], results: ['SUCCESS', 'FAILURE'] })
    }
    if (/\/ai-calls\/\d+/.test(target)) {
      return ok(detail)
    }
    if (target.includes('/ai-calls')) {
      return ok({ items, totalElements: total, page: 1, size: 20, totalPages: Math.max(1, Math.ceil(total / 20)) })
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(AiCallHistoryView, { global: { plugins: [pinia] } })
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock }
}

describe('AI呼出履歴', () => {
  it('検索条件のカードと一覧のカードを分けて出す', async () => {
    const { wrapper } = await setup()

    expect(wrapper.get('.search-panel__title').text()).toContain('検索条件')
    expect(wrapper.findAll('.table-section__title').map((el) => el.text())).toEqual(['AI呼出履歴'])
    // 一覧の見出しには一覧アイコンを付ける（他の一覧画面と揃える）
    expect(wrapper.get('.table-section__title').get('use').attributes('href')).toBe('#i-list')
    expect(wrapper.text()).toContain('全 2 件')
  })

  it('一覧を出す（操作が最左・結果は日本語ラベル・トークン）', async () => {
    const { wrapper } = await setup()

    const headers = wrapper.findAll('thead th').map((el) => el.text())
    expect(headers[0]).toBe('操作')
    expect(headers).toContain('バッチコード')
    expect(headers).toContain('結果')
    expect(headers).toContain('処理時間')
    expect(headers).toContain('トークン')

    const success = wrapper.get('tbody tr[data-call-id="66974"]')
    expect(success.text()).toContain('batL03')
    expect(success.text()).toContain('qwen')
    expect(success.text()).toContain('qwen3-vl-flash')
    expect(success.text()).toContain('成功')
    expect(success.text()).toContain('200')
    expect(success.text()).toContain('1200 / 340 / 1540')
    expect(success.text()).toContain('study-monitor/snapshot/30437/first')

    const failure = wrapper.get('tbody tr[data-call-id="66800"]')
    expect(failure.text()).toContain('失敗')
    expect(failure.text()).toContain('AI の応答がありませんでした')
    // 実行ID が無い呼び出しは「—」
    expect(failure.findAll('td')[2].text()).toBe('—')
    // トークンが無い呼び出しは「—」
    expect(failure.text()).toContain('—')
  })

  it('絞り込みの値はサーバから取った実データを使う', async () => {
    const { wrapper } = await setup()

    const selects = wrapper.findAll('select')
    const batchOptions = selects[0].findAll('option').map((option) => option.text())
    expect(batchOptions).toEqual(['すべて', 'batC04', 'batL03'])

    const aiTypeOptions = selects[1].findAll('option').map((option) => option.text())
    expect(aiTypeOptions).toEqual(['すべて', 'deepseek', 'qwen'])

    const resultOptions = selects[3].findAll('option').map((option) => option.text())
    expect(resultOptions).toEqual(['すべて', '成功', '失敗'])
  })

  it('検索条件（バッチコード・AI区分・結果・キーワード・期間）を送る', async () => {
    const { wrapper, fetchMock } = await setup()

    const selects = wrapper.findAll('select')
    await selects[0].setValue('batL03')
    await selects[1].setValue('qwen')
    await selects[3].setValue('FAILURE')
    await wrapper.get('input[aria-label="キーワード"]').setValue('snapshot')
    await wrapper.get('input[aria-label="開始日時From"]').setValue('2026-09-01T00:00')
    await wrapper.get('input[aria-label="開始日時To"]').setValue('2026-09-12T23:59')

    const searchButton = wrapper.findAll('.search-panel__actions .btn').find((button) => button.text().includes('検索'))
    await searchButton?.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/ai-calls?'))
    expect(call?.url).toContain('batchCode=batL03')
    expect(call?.url).toContain('aiType=qwen')
    expect(call?.url).toContain('result=FAILURE')
    expect(call?.url).toContain('keyword=snapshot')
    expect(call?.url).toContain('startFrom=2026-09-01T00%3A00%3A00')
    expect(call?.url).toContain('startTo=2026-09-12T23%3A59%3A59')
  })

  it('検索条件は他の一覧画面と同じ検索パネルで、項目は 2 行に整列する', async () => {
    const { wrapper } = await setup()

    // 余白を持つ検索パネル（資料管理・インターネット利用履歴と同じ .search-panel）
    const panel = wrapper.get('.search-panel')
    expect(panel.get('.search-panel__title').text()).toContain('検索条件')
    expect(panel.get('.search-panel__actions').findAll('.btn').map((button) => button.text()))
      .toEqual(['検索', 'リセット'])
    // 検索条件の中にボタン行は無い（項目だけ）
    expect(panel.findAll('.filters__row')).toHaveLength(2)
    expect(panel.findAll('.filters__row .btn')).toHaveLength(0)

    // 1 行目: 選択 4 つだけ（狭い画面でも折り返さないよう、残り幅を使う項目は置かない）
    const firstRow = panel.findAll('.filters__row')[0]
    expect(firstRow.findAll('.filter-item .select').map((select) => select.attributes('aria-label')))
      .toEqual(['バッチコード', 'AI区分', 'モデル名', '結果'])
    // 幅は内容に左右されないよう固定する（選択肢が長いと広がって折り返すため）
    expect(firstRow.findAll('.filter-item .select').map((select) => select.attributes('style')?.trim()))
      .toEqual(['width: 10rem;', 'width: 10rem;', 'width: 12rem;', 'width: 10rem;'])
    expect(firstRow.find('.filter-item--grow').exists()).toBe(false)

    // 2 行目: キーワード（残り幅）＋ 期間（From / To は同じ行・同じ幅）
    const secondRow = panel.findAll('.filters__row')[1]
    expect(secondRow.findAll('input').map((input) => input.attributes('aria-label')))
      .toEqual(['キーワード', '開始日時From', '開始日時To'])
    expect(secondRow.get('.filter-item--grow input').attributes('aria-label')).toBe('キーワード')
    expect(secondRow.findAll('.filter-item:not(.filter-item--grow) input').map((input) => input.attributes('style')?.trim()))
      .toEqual(['width: 13rem;', 'width: 13rem;'])
  })

  it('選択の幅は設計システムの最小幅（150px）以上を指定する', async () => {
    const { wrapper } = await setup()

    // 設計システムは .filter-item .select { min-width: 150px } なので、
    // それより小さい width は無視されて幅がそろわない（以前は AI区分 8rem・結果 7rem が
    // 150px に上書きされていた）。指定する値は 150px 以上にする。
    const declared = wrapper.findAll('.filter-item .select')
      .map((select) => /width:\s*([\d.]+)rem/.exec(select.attributes('style') ?? '')?.[1])
      .map((value) => Number(value))
    expect(declared.every((rem) => rem >= 150 / 16)).toBe(true)
  })

  it('件数の選択はページングの左隣にある（検索条件には置かない）', async () => {
    const { wrapper, fetchMock } = await setup()

    // 検索条件のカード（.filters）には「件数」を置かない
    expect(wrapper.get('.filters').text()).not.toContain('件数')

    const pagination = wrapper.get('.pagination')
    expect(pagination.find('[data-page-size]').exists()).toBe(true)
    expect([...pagination.element.children].map((child) => child.className.split(' ')[0]))
      .toEqual(['pagination__info', 'pagination__size', 'pagination__pages'])

    await pagination.get('[data-page-size]').setValue('50')
    await flushPromises()
    expect(recorded(fetchMock).at(-1)?.url).toContain('size=50')
  })

  it('リセットで条件を消して 1 ページ目を読み直す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('input[aria-label="キーワード"]').setValue('snapshot')
    const resetButton = wrapper.findAll('.search-panel__actions .btn').find((button) => button.text().includes('リセット'))
    await resetButton?.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/ai-calls?'))
    expect(call?.url).not.toContain('keyword=snapshot')
    expect((wrapper.get('input[aria-label="キーワード"]').element as HTMLInputElement).value).toBe('')
  })

  it('【詳細】でプロンプトとレスポンスの本文を出す', async () => {
    const { wrapper, fetchMock } = await setup()

    expect(wrapper.find('[data-testid="ai-call-detail"]').exists()).toBe(false)

    await wrapper.get('[data-detail="66974"]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some((entry) => entry.url.includes('/ai-calls/66974'))).toBe(true)
    expect(wrapper.get('[data-testid="ai-call-detail"]').text()).toContain('batL03')
    expect(wrapper.get('[data-testid="ai-call-prompt"]').text()).toContain('この画像の学習状況を判定してください。')
    expect(wrapper.get('[data-testid="ai-call-response"]').text()).toContain('"result":"学習中"')
  })

  it('本文が長いときは切ったことを書く', async () => {
    const { wrapper } = await setup({ detail: { bodyTruncated: true } })

    await wrapper.get('[data-detail="66974"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('200,000 文字で切って表示しています')
  })

  it('該当が無いときは案内を出す', async () => {
    const { wrapper } = await setup({ items: [], total: 0 })

    expect(wrapper.text()).toContain('該当する AI 呼び出し履歴がありません。')
    expect(wrapper.findAll('tbody tr[data-call-id]').length).toBe(0)
  })
})

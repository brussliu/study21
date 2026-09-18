import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import InternetUsageHistoryView from '@/views/network/InternetUsageHistoryView.vue'
import type { AccessLogRow } from '@/api/netAccessLogs'
import type { BrowsingLogRow } from '@/api/webBrowsingLogs'

/**
 * インターネット利用履歴（ネットワーク制御＞インターネット利用履歴）。
 *
 * 2.0 の履歴管理画面（history.jsp）のタブ構成を引き継ぎ、
 * 「サイトアクセス履歴」（= 上網履歴。プロキシの通信履歴）と
 * 「Web閲覧履歴」（= ブラウザ閲覧履歴。ブラウザ拡張の閲覧イベント）をまとめる。
 * どちらも 2.0 から移行した実データを読む。
 */
function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function logRow(overrides: Partial<AccessLogRow> = {}): AccessLogRow {
  return {
    logId: 6207595,
    receivedAt: '2026-09-11T23:58:37',
    clientIp: '192.168.0.92',
    terminalName: '勉強用PC',
    httpMethod: 'CONNECT',
    host: 'spclient.wg.spotify.com',
    url: 'spclient.wg.spotify.com:443',
    statusCode: 403,
    result: 'DENY',
    errorDetail: '停止モードのためアクセス不可',
    ...overrides
  }
}

function browsingRow(overrides: Partial<BrowsingLogRow> = {}): BrowsingLogRow {
  return {
    logId: 30948,
    accessedAt: '2026-09-12T13:38:07.807',
    terminalId: 'chrome-1789108496512-ku3zp6lc',
    terminalName: 'LIU-PC',
    eventType: 'TAB_ACTIVATED',
    domain: 'platform.deepseek.com',
    url: 'https://platform.deepseek.com/usage',
    pageTitle: 'DeepSeek 開放平台',
    activeFlag: '1',
    staySeconds: 37119,
    viewCount: 3,
    ...overrides
  }
}

type Call = { url: string }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => ({ url: String(call[0]) }))
}

beforeEach(() => {
  useToast().items.splice(0)
  window.scrollTo = vi.fn()
})

async function setup(options: {
  items?: AccessLogRow[]
  total?: number
  browsingItems?: BrowsingLogRow[]
  browsingTotal?: number
} = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const items = options.items ?? [
    logRow(),
    logRow({ logId: 6207594, host: 'www.google.com', url: 'www.google.com:443', statusCode: null, result: 'ALLOW', errorDetail: null, terminalName: null })
  ]
  const total = options.total ?? items.length
  const browsingItems = options.browsingItems ?? [
    browsingRow(),
    browsingRow({
      logId: 30947, eventType: 'TAB_UPDATED', domain: 'youtube.com',
      url: 'https://www.youtube.com/watch?v=1', pageTitle: '動画', activeFlag: '0',
      staySeconds: 45, viewCount: 1, terminalName: '勉強用PC'
    })
  ]
  const browsingTotal = options.browsingTotal ?? browsingItems.length
  const fetchMock = vi.fn(async (url: string) => {
    if (String(url).includes('/api/user/net-access-logs')) {
      return ok({ items, totalElements: total, page: 1, size: 20, totalPages: Math.max(1, Math.ceil(total / 20)) })
    }
    if (String(url).includes('/api/user/net-terminals')) {
      return ok({
        items: [
          { terminalId: 1, ipAddress: '192.168.0.92', terminalName: '勉強用PC', terminalMode: 'T', status: '1' },
          { terminalId: 2, ipAddress: '192.168.0.93', terminalName: 'HUAWEI P50', terminalMode: 'T', status: '1' },
          { terminalId: 3, ipAddress: '192.168.0.60', terminalName: 'ゲーム用PC', terminalMode: 'T', status: '1' },
          // 同じ名前の端末が複数あっても 1 つだけ出す
          { terminalId: 4, ipAddress: '127.0.0.1', terminalName: '勉強用PC', terminalMode: 'T', status: '1' }
        ],
        page: 1, size: 200, totalElements: 4, totalPages: 1
      })
    }
    if (String(url).includes('/api/user/web-browsing-logs')) {
      return ok({
        items: browsingItems, totalElements: browsingTotal, page: 1, size: 20,
        totalPages: Math.max(1, Math.ceil(browsingTotal / 20))
      })
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(InternetUsageHistoryView, { global: { plugins: [pinia] } })
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock }
}

describe('インターネット利用履歴', () => {
  it('2 つのタブ（サイトアクセス履歴 / Web閲覧履歴）を持つ', async () => {
    const { wrapper } = await setup()

    const tabs = wrapper.findAll('.tabs__tab').map((tab) => tab.text())
    expect(tabs[0]).toContain('サイトアクセス履歴')
    expect(tabs[1]).toContain('Web閲覧履歴')
    // 既定はサイトアクセス履歴
    expect(wrapper.find('.tabs__tab.is-active').text()).toContain('サイトアクセス履歴')
  })

  it('両方のタブに検索条件カードがある（資料管理と同じ形）', async () => {
    const { wrapper } = await setup()

    // サイトアクセス履歴: 検索条件カード＋一覧カード
    const accessPanel = wrapper.get('.search-panel')
    expect(accessPanel.get('.search-panel__title').text()).toContain('検索条件')
    expect(accessPanel.findAll('.search-panel__actions .btn').map((button) => button.text()))
      .toEqual(['検索', 'リセット'])
    expect(wrapper.find('.card.table-section').exists()).toBe(true)
    // 一覧の見出しには一覧アイコンを付ける（他の一覧画面と揃える）
    expect(wrapper.get('.table-section__title').get('use').attributes('href')).toBe('#i-list')

    // Web閲覧履歴にも同じカードがある
    await wrapper.findAll('.tabs__tab')[1].trigger('click')
    await flushPromises()
    const browsingPanel = wrapper.get('.search-panel')
    expect(browsingPanel.get('.search-panel__title').text()).toContain('検索条件')
    expect(browsingPanel.findAll('.search-panel__actions .btn').map((button) => button.text()))
      .toEqual(['検索', 'リセット'])
    expect(wrapper.get('.table-section__title').get('use').attributes('href')).toBe('#i-list')
  })

  it('タブ名に件数を出さない', async () => {
    const { wrapper } = await setup()

    // タブの中はラベルだけ（バッジを付けない）
    expect(wrapper.get('.tabs__tab[data-tab="access"]').text()).toBe('サイトアクセス履歴')
    expect(wrapper.get('.tabs__tab[data-tab="browsing"]').text()).toBe('Web閲覧履歴')
    expect(wrapper.get('.tabs__tab[data-tab="access"]').find('.badge').exists()).toBe(false)
    // 件数は一覧カードの見出しに出す
    expect(wrapper.get('.table-section__meta').text()).toContain('全 2 件')
  })

  it('端末名称は登録端末のドロップダウン（重複は 1 つ・名前順）', async () => {
    const { wrapper, fetchMock } = await setup()
    await flushPromises()

    // 端末一覧を読んで選択肢を作る
    expect(recorded(fetchMock).some((entry) => entry.url.includes('/api/user/net-terminals'))).toBe(true)
    const terminalSelect = wrapper.findAll('select').find((select) =>
      select.findAll('option').some((option) => option.text() === '勉強用PC'))
    expect(terminalSelect?.findAll('option').map((option) => option.text()))
      .toEqual(['すべて', 'HUAWEI P50', 'ゲーム用PC', '勉強用PC'])
  })

  it('サイトアクセス履歴を表示する（端末名称・応答・結果）', async () => {
    const { wrapper } = await setup()

    const denied = wrapper.get('tbody tr[data-log-id="6207595"]')
    expect(denied.text()).toContain('spclient.wg.spotify.com')
    expect(denied.text()).toContain('192.168.0.92')
    expect(denied.text()).toContain('勉強用PC')
    expect(denied.text()).toContain('403')
    expect(denied.text()).toContain('拒否')

    const allowed = wrapper.get('tbody tr[data-log-id="6207594"]')
    expect(allowed.text()).toContain('許可')
  })

  it('接続先・端末名称・結果・件数を検索条件として送る', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('input[placeholder="例: youtube.com"]').setValue('spotify')
    // 端末名称は登録端末のドロップダウン
    const terminalSelect = wrapper.findAll('select').find((select) =>
      select.findAll('option').some((option) => option.text() === '勉強用PC'))
    await terminalSelect?.setValue('勉強用PC')
    wrapper.findAll('select').find((select) =>
      select.findAll('option').some((option) => option.text() === '拒否'))?.setValue('DENY')
    await wrapper.findAll('.search-panel__actions .btn')[0].trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/net-access-logs'))
    expect(call?.url).toContain('host=spotify')
    expect(call?.url).toContain('terminalName=%E5%8B%89%E5%BC%B7%E7%94%A8PC')
    expect(call?.url).toContain('result=DENY')
    expect(call?.url).toContain('size=20')
  })

  it('リセットで条件を消して 1 ページ目に戻す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('input[placeholder="例: youtube.com"]').setValue('spotify')
    const resetButton = wrapper.findAll('.search-panel__actions .btn').find((button) => button.text().includes('リセット'))
    await resetButton?.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/net-access-logs'))
    expect(call?.url).not.toContain('host=spotify')
    expect((wrapper.get('input[placeholder="例: youtube.com"]').element as HTMLInputElement).value).toBe('')
  })

  it('件数の選択はページングの左隣にある（検索条件には置かない）', async () => {
    const { wrapper, fetchMock } = await setup()

    // 検索条件カードには「件数」を置かない
    expect(wrapper.get('.search-panel').text()).not.toContain('件数')

    const pagination = wrapper.get('.pagination')
    expect(pagination.find('[data-page-size]').exists()).toBe(true)
    expect(pagination.findAll('option').map((option) => option.text()))
      .toEqual(['20 件', '50 件', '100 件'])
    // 件数情報 → 件数 → ページ番号 の順
    const children = pagination.element.children
    expect([...children].map((child) => child.className.split(' ')[0]))
      .toEqual(['pagination__info', 'pagination__size', 'pagination__pages'])

    // 変えるとその件数で取り直す
    await pagination.get('[data-page-size]').setValue('50')
    await flushPromises()
    expect(recorded(fetchMock).at(-1)?.url).toContain('size=50')

    // Web閲覧履歴にも同じ形で置く
    await wrapper.findAll('.tabs__tab')[1].trigger('click')
    await flushPromises()
    expect(wrapper.get('.pagination').find('[data-page-size]').exists()).toBe(true)
  })

  it('該当が無いときは案内を出す', async () => {
    const { wrapper } = await setup({ items: [], total: 0 })

    expect(wrapper.text()).toContain('該当するアクセス履歴がありません。')
    expect(wrapper.findAll('tbody tr[data-log-id]').length).toBe(0)
  })

  it('Web閲覧履歴タブを開いたときにだけ読み込む（2.0 と同じ）', async () => {
    const { wrapper, fetchMock } = await setup()

    // 既定はサイトアクセス履歴のタブなので、Web閲覧履歴はまだ読まない
    expect(recorded(fetchMock).some((entry) => entry.url.includes('/web-browsing-logs'))).toBe(false)

    await wrapper.findAll('.tabs__tab')[1].trigger('click')
    await flushPromises()

    const calls = recorded(fetchMock).filter((entry) => entry.url.includes('/web-browsing-logs'))
    expect(calls.length).toBe(1)
    // 初期値は「今日」の範囲
    expect(calls[0].url).toContain('dateFrom=')
    expect(calls[0].url).toContain('dateTo=')
  })

  it('Web閲覧履歴を表示する（端末・イベント・ドメイン・滞在時間・回数）', async () => {
    const { wrapper } = await setup()

    await wrapper.findAll('.tabs__tab')[1].trigger('click')
    await flushPromises()

    const active = wrapper.get('tbody tr[data-browsing-id="30948"]')
    expect(active.text()).toContain('chrome-1789108496512-ku3zp6lc')
    expect(active.text()).toContain('LIU-PC')
    // イベント種別は日本語ラベルで出す
    expect(active.text()).toContain('タブを切替')
    expect(active.text()).toContain('platform.deepseek.com')
    expect(active.text()).toContain('DeepSeek 開放平台')
    expect(active.text()).toContain('アクティブ')
    expect(active.text()).toContain('10 時間 18 分')
    expect(active.text()).toContain('3')

    const inactive = wrapper.get('tbody tr[data-browsing-id="30947"]')
    expect(inactive.text()).toContain('タブを更新')
    expect(inactive.text()).toContain('45 秒')
    expect(inactive.text()).not.toContain('アクティブ')
  })

  it('Web閲覧履歴の検索条件（端末ID・端末名称・ドメイン・イベント・キーワード・日付・件数）を送る', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.findAll('.tabs__tab')[1].trigger('click')
    await flushPromises()

    await wrapper.get('input[placeholder="例: chrome-xxxx"]').setValue('chrome-178')
    await wrapper.get('input[placeholder="例: LIU-PC"]').setValue('LIU')
    await wrapper.get('input[placeholder="例: youtube"]').setValue('youtube')
    await wrapper.get('input[placeholder="URL / ページタイトル"]').setValue('動画')
    const selects = wrapper.findAll('select')
    // イベントの select は「すべて + 5 種類」
    const eventSelect = selects.find((select) => select.findAll('option').length === 6)
    await eventSelect?.setValue('TAB_UPDATED')
    await wrapper.findAll('input[type="date"]')[0].setValue('2026-09-01')
    await wrapper.findAll('input[type="date"]')[1].setValue('2026-09-12')
    const searchButton = wrapper.findAll('.search-panel__actions .btn').find((button) => button.text().includes('検索'))
    await searchButton?.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/web-browsing-logs'))
    expect(call?.url).toContain('terminalId=chrome-178')
    expect(call?.url).toContain('terminalName=LIU')
    expect(call?.url).toContain('domain=youtube')
    expect(call?.url).toContain('eventType=TAB_UPDATED')
    expect(call?.url).toContain('keyword=')
    expect(call?.url).toContain('dateFrom=2026-09-01')
    expect(call?.url).toContain('dateTo=2026-09-12')
  })

  it('Web閲覧履歴のリセットで条件を消す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.findAll('.tabs__tab')[1].trigger('click')
    await flushPromises()
    await wrapper.get('input[placeholder="例: youtube"]').setValue('youtube')

    // Web閲覧履歴のタブだけが DOM にあるので、リセットは 1 つ
    const resetButton = wrapper.findAll('.search-panel__actions .btn').find((button) => button.text().includes('リセット'))
    await resetButton?.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/web-browsing-logs'))
    expect(call?.url).not.toContain('domain=youtube')
    // 日付も空に戻る（HttpClient は空文字のパラメータをそのまま載せる。サーバ側は「指定なし」扱い）
    expect(call?.url).toContain('dateFrom=&dateTo=')
    expect((wrapper.get('input[placeholder="例: youtube"]').element as HTMLInputElement).value).toBe('')
  })

  it('Web閲覧履歴が 0 件のときは案内を出す', async () => {
    const { wrapper } = await setup({ browsingItems: [], browsingTotal: 0 })

    await wrapper.findAll('.tabs__tab')[1].trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('該当する閲覧履歴がありません。')
    expect(wrapper.findAll('tbody tr[data-browsing-id]').length).toBe(0)
  })
})

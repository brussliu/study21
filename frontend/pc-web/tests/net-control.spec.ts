import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import SiteManagementView from '@/views/net/SiteManagementView.vue'
import TerminalControlView from '@/views/net/TerminalControlView.vue'
import type { SiteRow, TerminalRow } from '@/api/net'

/**
 * サイト管理 / 端末コントロール（2.0 の site.jsp / terminal_control.jsp 相当）。
 * API はモックし、画面の表示・検証・送信内容を固定する。
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function forbidden(message: string): Response {
  return new Response(
    JSON.stringify({ success: false, code: 'FORBIDDEN', message, data: null, timestamp: '' }),
    { status: 403, headers: { 'Content-Type': 'application/json' } }
  )
}

function siteRow(overrides: Partial<SiteRow> = {}): SiteRow {
  return {
    siteId: 1,
    siteName: 'youtube',
    siteUrl: 'youtube.com',
    hostName: 'youtube.com',
    kindCode: 'BREAK',
    judgeMethodCode: 'SUFFIX',
    categoryCode: 'ENTERTAINMENT',
    categoryName: null,
    approvalStatus: 'APPROVED',
    status: '1',
    note: '休憩用',
    version: 2,
    approvedAt: '2026-09-11T10:00:00',
    createdAt: '2026-08-10T09:00:00',
    updatedAt: '2026-08-10T09:00:00',
    ...overrides
  }
}

function terminalRow(overrides: Partial<TerminalRow> = {}): TerminalRow {
  return {
    terminalId: 1,
    ipAddress: '192.168.0.92',
    terminalName: '勉強用PC',
    terminalMode: 'T',
    status: '1',
    note: null,
    lastSeenAt: null,
    version: 3,
    updatedByName: '試験 保護者',
    updatedByCode: null,
    updatedAt: '2026-08-13T10:00:00',
    ...overrides
  }
}

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
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

beforeEach(() => {
  useToast().items.splice(0)
  window.scrollTo = vi.fn()
})

describe('サイト管理', () => {
  async function setup(options: {
    items?: SiteRow[]
    total?: number
    pages?: number
    handlers?: (url: string, method: string, body: Record<string, unknown> | null) => Response | null
  } = {}) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const items = options.items ?? [siteRow(), siteRow({ siteId: 2, siteName: 'example.com', siteUrl: 'example.com', kindCode: 'NORMAL', judgeMethodCode: 'EXACT', categoryCode: 'OTHER', categoryName: '英会話', approvalStatus: 'PENDING', status: '0', note: null })]
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
      const handled = options.handlers?.(String(url), method, body)
      if (handled) return handled
      if (String(url).startsWith('/api/user/net-sites') && method === 'GET') {
        return ok({ items, page: 1, size: 15, totalElements: options.total ?? items.length, totalPages: options.pages ?? 1 })
      }
      return ok({ message: 'サイトを登録しました。', row: siteRow(), updatedCount: null })
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(SiteManagementView, { global: { plugins: [pinia] } })
    await flushPromises()
    return { wrapper, fetchMock }
  }

  it('一覧を日本語の表示文言で描画する', async () => {
    const { wrapper } = await setup()

    const text = wrapper.text()
    expect(text).toContain('youtube')
    expect(text).toContain('2.休憩') // 区分 BREAK
    expect(text).toContain('末尾一致') // SUFFIX
    expect(text).toContain('娯楽')
    expect(text).toContain('承認済')
    expect(text).toContain('有効')
    // 分類が OTHER の行は分類名称を優先して表示する
    expect(text).toContain('英会話')
    expect(text).not.toContain('OTHER')
    expect(text).toContain('全 2 件')
    // 一覧の見出しには一覧アイコンを付ける（他の一覧画面と揃える）
    expect(wrapper.get('.table-section__title').get('use').attributes('href')).toBe('#i-list')
  })

  it('検索条件をクエリで送る', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('.filters select').setValue('ENTERTAINMENT')
    await wrapper.get('.filter-item--grow input').setValue('youtube')
    await wrapper.get('.search-panel__actions .btn--primary').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.method === 'GET' && entry.url.includes('category='))
    expect(call?.url).toContain('category=ENTERTAINMENT')
    expect(call?.url).toContain('keyword=youtube')
    expect(call?.url).toContain('sortBy=createdAt')
  })

  it('新規登録は未承認で登録され、成功後に一覧を再読み込みする', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('.search-panel__actions .btn--primary:last-child').trigger('click')
    expect(wrapper.find('#netSiteName').exists()).toBe(true)

    await wrapper.get('#netSiteName').setValue('khanacademy.org')
    await wrapper.get('#netSiteUrl').setValue('https://www.khanacademy.org/learning')
    await wrapper.get('#netSiteKind').setValue('STUDY')
    await wrapper.get('#netSiteJudge').setValue('PREFIX')
    await wrapper.get('#netSiteCategory').setValue('OTHER')
    await wrapper.get('#netSiteCategoryName').setValue('学習')
    await wrapper.get('.dialog__foot .btn--primary').trigger('click')
    await flushPromises()

    const post = recorded(fetchMock).find((entry) => entry.method === 'POST' && entry.url === '/api/user/net-sites')
    expect(post?.body).toMatchObject({
      siteName: 'khanacademy.org',
      siteUrl: 'https://www.khanacademy.org/learning',
      kindCode: 'STUDY',
      judgeMethodCode: 'PREFIX',
      categoryCode: 'OTHER',
      categoryName: '学習'
    })
    expect(toastMessages()).toContain('サイトを登録しました。')
  })

  it('必須項目が空なら送信しない', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('.search-panel__actions .btn--primary:last-child').trigger('click')
    await wrapper.get('#netSiteUrl').setValue('example.com')
    await wrapper.get('.dialog__foot .btn--primary').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('サイト名称を入力してください。')
    expect(recorded(fetchMock).some((entry) => entry.method === 'POST')).toBe(false)
    expect(toastMessages()).toContain('入力内容を確認してください。')
  })

  it('編集は表示していたバージョンを送る（未承認に戻る）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('tbody tr:first-child [title="編集"]').trigger('click')
    await wrapper.get('#netSiteName').setValue('youtube2')
    await wrapper.get('.dialog__foot .btn--primary').trigger('click')
    await flushPromises()

    const put = recorded(fetchMock).find((entry) => entry.method === 'PUT')
    expect(put?.url).toBe('/api/user/net-sites/1')
    expect(put?.body).toMatchObject({ siteName: 'youtube2', version: 2 })
  })

  it('承認と削除をそれぞれの API へ送る', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { wrapper, fetchMock } = await setup()

    // 未承認の行だけ承認ボタンが出る（2 行目が未承認）
    await wrapper.get('tbody tr:nth-child(2) [title="承認"]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some((entry) => entry.method === 'POST' && entry.url === '/api/user/net-sites/2/approval')).toBe(true)

    await wrapper.get('tbody tr:first-child [title="削除"]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some((entry) => entry.method === 'DELETE' && entry.url === '/api/user/net-sites/1')).toBe(true)
    confirmSpy.mockRestore()
  })

  it('全サイト一括のインターネット利用スイッチは置かない（2.0 の画面に無いため）', async () => {
    const { wrapper } = await setup()

    expect(wrapper.text()).not.toContain('インターネット利用')
    expect(wrapper.find('.net-page__actions').exists()).toBe(false)
  })

  it('一括承認のチェックボックス列とボタンを置かない', async () => {
    const { wrapper } = await setup()

    // 選択用のチェックボックスは一覧に無い（端末コントロールの一括切替とは別物）
    expect(wrapper.findAll('tbody input[type="checkbox"]').length).toBe(0)
    expect(wrapper.find('thead .col-check').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('選択したサイトを承認')
  })

  it('未承認の行は承認だけ、承認済の行は却下だけを出す', async () => {
    // 1 行目=承認済、2 行目=未承認（setup の既定データ）
    const { wrapper } = await setup()

    const approvedRow = wrapper.get('tbody tr:first-child')
    expect(approvedRow.find('[title="却下"]').exists()).toBe(true)
    expect(approvedRow.find('[title="承認"]').exists()).toBe(false)

    const pendingRow = wrapper.get('tbody tr:nth-child(2)')
    expect(pendingRow.find('[title="承認"]').exists()).toBe(true)
    expect(pendingRow.find('[title="却下"]').exists()).toBe(false)
  })

  it('却下アイコンは削除と区別できる（赤を使わない）', async () => {
    const { wrapper } = await setup()

    // 却下は取り消し（橙）、削除はゴミ箱（赤）
    const rejectIcon = wrapper.get('tbody tr:first-child [title="却下"] .icon')
    expect(rejectIcon.classes()).toContain('icon--reject')
    expect(rejectIcon.classes()).not.toContain('icon--danger')

    const deleteIcon = wrapper.get('tbody tr:first-child [title="削除"] .icon')
    expect(deleteIcon.classes()).toContain('icon--danger')
  })

  it('却下は確認のうえ却下 API へ送る', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('tbody tr:first-child [title="却下"]').trigger('click')
    await flushPromises()

    expect(
      recorded(fetchMock).some(
        (entry) => entry.method === 'POST' && entry.url === '/api/user/net-sites/1/rejection'
      )
    ).toBe(true)
    confirmSpy.mockRestore()
  })

  it('生徒でも承認・却下の操作ができる（権限を区別しない）', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const items = [siteRow({ siteId: 3, approvalStatus: 'PENDING', status: '1' })]
    const fetchMock = vi.fn(async (_url: string, init?: RequestInit) =>
      (init?.method ?? 'GET').toUpperCase() === 'GET'
        ? ok({ items, page: 1, size: 15, totalElements: 1, totalPages: 1 })
        : ok({ message: 'サイトを承認しました。', row: items[0], updatedCount: null })
    )
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(SiteManagementView, { global: { plugins: [pinia] } })
    await flushPromises()

    // 未承認の行には承認だけが出る（却下は承認済みの行にだけ出る）
    expect(wrapper.find('tbody [title="承認"]').exists()).toBe(true)
    expect(wrapper.find('tbody [title="却下"]').exists()).toBe(false)

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    await wrapper.get('tbody [title="承認"]').trigger('click')
    await flushPromises()
    expect(
      recorded(fetchMock).some(
        (entry) => entry.method === 'POST' && entry.url === '/api/user/net-sites/3/approval'
      )
    ).toBe(true)
    confirmSpy.mockRestore()
  })

  it('リセットボタンの見た目をリンククリップと揃える（secondary ＋ rotate アイコン）', async () => {
    const { wrapper } = await setup()

    const reset = wrapper
      .findAll('.search-panel__actions .btn')
      .find((button) => button.text().includes('リセット'))
    expect(reset).toBeTruthy()
    expect(reset?.classes()).toContain('btn--secondary')
    // 既定サイズ（検索ボタンと同じ大きさ）
    expect(reset?.classes()).not.toContain('btn--sm')
    expect(reset?.find('svg.icon.icon--sm use').attributes('href')).toBe('#i-rotate')
    expect(reset?.text()).toContain('リセット')
  })

  it('判定方法で絞り込める（検索条件のドロップダウン）', async () => {
    const { wrapper, fetchMock } = await setup()

    // 検索条件に「判定方法」がある（2.0 には無かった追加項目）
    const labels = wrapper.findAll('.filters .filter-item__label').map((el) => el.text())
    expect(labels).toContain('判定方法：')

    const judge = wrapper.findAll('.filters select').find((select) =>
      select.findAll('option').some((option) => option.text() === '末尾一致')
    )
    expect(judge).toBeTruthy()
    // 空（すべて）＋ 4 種類
    expect(judge?.findAll('option').map((option) => option.text()))
      .toEqual(['すべて', '先頭一致', '末尾一致', '含める', '完全一致'])

    await judge?.setValue('EXACT')
    await wrapper.findAll('.search-panel__actions .btn')
      .find((button) => button.text().includes('検索'))?.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.url.includes('judgeMethod=EXACT'))
    expect(call).toBeTruthy()
    expect(call?.url).toContain('/api/user/net-sites?')

    // リセットで絞り込みが消える
    await wrapper.findAll('.search-panel__actions .btn')
      .find((button) => button.text().includes('リセット'))?.trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).at(-1)?.url).not.toContain('judgeMethod')
  })

  it('サイトURL の並び替えはドメイン単位のキーで頼む（サブドメインを親の隣に並べる）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.findAll('.net-sort').find((button) => button.text().includes('サイトURL'))?.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).at(-1)
    expect(call?.url).toContain('sortBy=siteUrl')
    expect(call?.url).toContain('sortDir=asc')
  })

  it('ページングで次のページを取りに行く', async () => {
    const { wrapper, fetchMock } = await setup({ total: 175, pages: 12 })

    expect(wrapper.text()).toContain('全 175 件（1 / 12 ページ）')
    const next = wrapper.findAll('.pagination .page-btn').at(-1)
    await next?.trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some((entry) => entry.url.includes('page=2'))).toBe(true)
  })
})

describe('端末コントロール', () => {
  async function setup(handlers?: (url: string, method: string) => Response | null) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      const handled = handlers?.(String(url), method)
      if (handled) return handled
      if (method === 'GET') {
        return ok({ items: [terminalRow(), terminalRow({ terminalId: 2, ipAddress: '192.168.0.60', terminalName: 'ゲーム用PC', terminalMode: 'G', status: '0', note: '来客用', updatedByName: null, updatedByCode: 'AGENT' })], page: 1, size: 50, totalElements: 2, totalPages: 1 })
      }
      return ok({ message: '端末ステータスを更新しました。', requestedCount: 1, updatedCount: 1 })
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(TerminalControlView, { global: { plugins: [pinia] } })
    await flushPromises()
    return { wrapper, fetchMock }
  }

  it('端末の一覧とモード・更新者を表示する', async () => {
    const { wrapper } = await setup()

    const text = wrapper.text()
    expect(text).toContain('192.168.0.92')
    expect(text).toContain('勉強用PC')
    expect(text).toContain('T.通常モード')
    expect(text).toContain('G.ゲームモード')
    expect(text).toContain('試験 保護者')
    // アカウントが無い更新（エージェント等）はコードを表示する
    expect(text).toContain('AGENT')
    // 備考も一覧に出す（未入力は「—」）
    expect(text).toContain('来客用')
  })

  it('削除は確認してから DELETE を呼び、一覧を取り直す', async () => {
    const { wrapper, fetchMock } = await setup()
    const confirmMock = vi.fn(() => true)
    vi.stubGlobal('confirm', confirmMock)

    await wrapper.get('[data-delete-terminal="1"]').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    const call = fetchMock.mock.calls.find((entry) => ((entry[1] ?? {}) as RequestInit).method === 'DELETE')
    expect(String(call?.[0])).toBe('/api/user/net-terminals/1')
    // 削除後に一覧を取り直す（GET が 2 回目）
    expect(fetchMock.mock.calls.filter((entry) => {
      const method = ((entry[1] ?? {}) as RequestInit).method ?? 'GET'
      return method === 'GET' && String(entry[0]).startsWith('/api/user/net-terminals')
    }).length).toBe(2)
  })

  it('確認をキャンセルしたら削除しない', async () => {
    const { wrapper, fetchMock } = await setup()
    vi.stubGlobal('confirm', vi.fn(() => false))

    await wrapper.get('[data-delete-terminal="1"]').trigger('click')
    await flushPromises()

    expect(fetchMock.mock.calls.some((entry) => ((entry[1] ?? {}) as RequestInit).method === 'DELETE')).toBe(false)
  })

  it('保護者以外はサーバーの 403 をそのまま画面に出す', async () => {
    const { wrapper } = await setup((url, method) =>
      method === 'GET' && url.startsWith('/api/user/net-terminals')
        ? forbidden('端末コントロールは保護者のみ利用できます。')
        : null
    )

    expect(wrapper.text()).toContain('アクセス権限がありません')
    expect(wrapper.text()).toContain('端末コントロールは保護者のみ利用できます。')
    expect(wrapper.find('.data-table').exists()).toBe(false)
  })

  it('一覧に「状態」列がある（有効／無効をバッジで出す）', async () => {
    const { wrapper } = await setup()

    // 見出しに「状態」がある（端末ステータスとは別の列）
    const headers = wrapper.findAll('thead th').map((th) => th.text().trim())
    expect(headers).toContain('状態')
    expect(headers).toContain('端末ステータス')
    expect(headers.indexOf('状態')).toBe(headers.indexOf('端末ステータス') + 1)

    // 有効な端末は「有効」（success）、無効な端末は「無効」（danger）
    const active = wrapper.get('tbody tr[data-terminal-id="1"]')
    const activeStatus = active.get('[data-terminal-status="1"]')
    expect(activeStatus.text()).toBe('有効')
    expect(activeStatus.classes()).toContain('badge--success')
    // 端末ステータス列にはモードだけを出す（状態は混ぜない）
    expect(active.findAll('td')[4].text()).toBe('T.通常モード')

    const inactive = wrapper.get('tbody tr[data-terminal-id="2"]')
    const inactiveStatus = inactive.get('[data-terminal-status="0"]')
    expect(inactiveStatus.text()).toBe('無効')
    expect(inactiveStatus.classes()).toContain('badge--danger')
  })

  it('操作列は編集と削除のアイコンを置く（行ごとのモード切替は置かない）', async () => {
    const { wrapper } = await setup()

    const firstRow = wrapper.get('tbody tr:first-child')
    // 編集アイコンと削除アイコン
    expect(firstRow.find('[data-edit-terminal]').exists()).toBe(true)
    const remove = firstRow.get('[data-delete-terminal="1"]')
    expect(remove.find('.icon--danger').exists()).toBe(true)
    expect(remove.attributes('title')).toBe('削除')
    expect(firstRow.findAll('.row-actions .btn').length).toBe(2)
    // 行の中にモードの選択も切替ボタンも無い（切替は右上の【一括適用】だけ）
    expect(firstRow.find('.net-mode-select').exists()).toBe(false)
    expect(firstRow.find('select').exists()).toBe(false)
    expect(firstRow.text()).not.toContain('モード切替')
  })

  it('青い案内と検索条件は出さない（端末は台数が少ない）', async () => {
    const { wrapper } = await setup()

    // 上部の案内（alert--info）は置かない
    expect(wrapper.find('.alert--info').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('端末の動作モードを一括・個別に切り替えます')
    // 検索条件（絞り込み・検索・リセット）も置かない
    expect(wrapper.find('.filters').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('キーワード')
    expect(wrapper.find('[data-action="bulk-apply"]').exists()).toBe(true)
    expect(wrapper.find('[data-action="create-terminal"]').exists()).toBe(true)
  })

  it('【新規】で端末を登録できる', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-action="create-terminal"]').trigger('click')
    expect(wrapper.get('[data-testid="terminal-dialog-title"]').text()).toContain('端末新規登録')

    // 必須項目が空なら送らない
    await wrapper.get('[data-testid="terminal-save"]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('IPアドレスを入力してください。')
    expect(recorded(fetchMock).some((entry) => entry.method === 'POST' && entry.url === '/api/user/net-terminals')).toBe(false)

    await wrapper.get('#netTerminalIp').setValue('192.168.0.70')
    await wrapper.get('#netTerminalName').setValue('  書斎のPC  ')
    await wrapper.get('#netTerminalMode').setValue('B')
    await wrapper.get('#netTerminalStatus').setValue('1')
    await wrapper.get('#netTerminalNote').setValue(' 勉強用 ')
    await wrapper.get('[data-testid="terminal-save"]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.method === 'POST' && entry.url === '/api/user/net-terminals')
    expect(call?.body).toEqual({
      ipAddress: '192.168.0.70',
      terminalName: '書斎のPC',
      terminalMode: 'B',
      status: '1',
      note: '勉強用',
      version: null
    })
  })

  it('編集アイコンで端末の内容を編集できる（バージョン付き）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-edit-terminal="1"]').trigger('click')
    expect(wrapper.get('[data-testid="terminal-dialog-title"]').text()).toContain('端末編集')
    // 既存の値が入っている
    expect((wrapper.get('#netTerminalIp').element as HTMLInputElement).value).toBe('192.168.0.92')
    expect((wrapper.get('#netTerminalName').element as HTMLInputElement).value).toBe('勉強用PC')

    await wrapper.get('#netTerminalName').setValue('勉強用PC（改名）')
    await wrapper.get('#netTerminalStatus').setValue('0')
    await wrapper.get('[data-testid="terminal-save"]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.method === 'PUT')
    expect(call?.url).toBe('/api/user/net-terminals/1')
    expect(call?.body).toEqual({
      ipAddress: '192.168.0.92',
      terminalName: '勉強用PC（改名）',
      terminalMode: 'T',
      status: '0',
      note: null,
      version: 3
    })
  })

  it('選択した端末を一括で切り替える', async () => {
    const { wrapper, fetchMock } = await setup()

    const checks = wrapper.findAll('tbody input[type="checkbox"]')
    await checks[0].setValue(true)
    await checks[1].setValue(true)
    await wrapper.get('.net-toolbar__mode').setValue('S')
    await wrapper.get('[data-action="bulk-apply"]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.url === '/api/user/net-terminals/mode')
    expect(call?.method).toBe('POST')
    expect(call?.body).toEqual({ terminalIds: [1, 2], terminalMode: 'S' })
  })

  it('未選択で一括適用すると注意を出して送信しない', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-action="bulk-apply"]').trigger('click')
    await flushPromises()

    expect(toastMessages()).toContain('更新対象の端末を選択してください。')
    expect(recorded(fetchMock).some((entry) => entry.url === '/api/user/net-terminals/mode')).toBe(false)
  })
})

/**
 * 端末コントロールの 2 つ目のタブ「ブラウザ端末」。
 *
 * IP で管理する端末（プロキシが使う `NET_端末コントロール情報`）とは別に、
 * ブラウザ拡張が接続コードで登録した端末（`NET_ブラウザ端末情報`）を並べる。
 * 接続コードの発行・再発行はインターネット利用履歴の「Web閲覧履歴」側に置いてある。
 */
describe('端末コントロール（ブラウザ端末タブ）', () => {
  function browserDevice(overrides: Record<string, unknown> = {}) {
    return {
      registrationId: 1,
      deviceId: 'chrome-1789301529492-quwe0vj',
      deviceName: 'LIU-PC',
      osType: 'Windows',
      browserType: 'Chrome',
      browserVersion: '153.0.0.0',
      extensionVersion: '2.1.0',
      profileId: 'Default',
      status: 'ACTIVE',
      online: true,
      lastStartedAt: '2026-09-13T20:00:00',
      lastSentAt: '2026-09-13T21:14:09',
      lastHeartbeatAt: '2026-09-13T21:15:00',
      createdAt: '2026-09-13T20:00:00',
      ...overrides
    }
  }

  async function setup(devices: unknown[] = [
    browserDevice(),
    browserDevice({
      registrationId: 2, deviceId: 'chrome-1719480000-abcd1234', deviceName: '勉強用PC',
      browserType: 'Edge', browserVersion: '130.0.0.0', extensionVersion: '2.0.0',
      online: false, lastSentAt: null, lastHeartbeatAt: null
    })
  ]) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const fetchMock = vi.fn(async (url: string) => {
      if (String(url).includes('/browser-extension/registration')) {
        return ok({
          issued: true, token: 'a'.repeat(64), deviceCount: devices.length,
          onlineCount: devices.filter((device) => (device as { online?: boolean }).online === true).length,
          devices, message: null
        })
      }
      if (String(url).startsWith('/api/user/net-terminals')) {
        return ok({ items: [terminalRow()], page: 1, size: 50, totalElements: 1, totalPages: 1 })
      }
      return ok({})
    })
    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(TerminalControlView, { global: { plugins: [pinia] } })
    await flushPromises()
    return { wrapper, fetchMock }
  }

  it('タブを 2 つ持ち、既定は端末コントロール', async () => {
    const { wrapper } = await setup()

    const tabs = wrapper.findAll('.tabs__tab').map((tab) => tab.text())
    expect(tabs).toEqual(['端末コントロール', 'ブラウザ端末'])
    expect(wrapper.get('.tabs__tab.is-active').text()).toBe('端末コントロール')
    // 既定のタブは IP で管理する端末の一覧
    expect(wrapper.text()).toContain('端末コントロール一覧')
    expect(wrapper.find('[data-browser-devices]').exists()).toBe(false)
  })

  it('ブラウザ端末タブを開いたときにだけ一覧を読む', async () => {
    const { wrapper, fetchMock } = await setup()

    expect(recorded(fetchMock).some((call) => call.url.includes('/browser-extension/registration'))).toBe(false)

    await wrapper.get('.tabs__tab[data-tab="browser"]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).filter((call) => call.url.includes('/browser-extension/registration')).length).toBe(1)
    expect(wrapper.find('[data-terminal-id]').exists()).toBe(false)
  })

  it('ブラウザ拡張が登録した端末を表示する（接続中・オフライン）', async () => {
    const { wrapper } = await setup()

    await wrapper.get('.tabs__tab[data-tab="browser"]').trigger('click')
    await flushPromises()

    const online = wrapper.get('[data-browser-device="chrome-1789301529492-quwe0vj"]')
    expect(online.text()).toContain('LIU-PC')
    expect(online.text()).toContain('chrome-1789301529492-quwe0vj')
    expect(online.text()).toContain('Chrome 153.0.0.0')
    expect(online.text()).toContain('2.1.0')
    expect(online.text()).toContain('接続中')

    const offline = wrapper.get('[data-browser-device="chrome-1719480000-abcd1234"]')
    expect(offline.text()).toContain('勉強用PC')
    expect(offline.text()).toContain('Edge 130.0.0.0')
    expect(offline.text()).toContain('オフライン')

    // 見出しの列（IP で管理する端末の表とは別物）
    expect(wrapper.get('[data-browser-devices] thead').text())
      .toContain('最終心拍')
  })

  it('接続している端末が無いときは案内を出す', async () => {
    const { wrapper } = await setup([])

    await wrapper.get('.tabs__tab[data-tab="browser"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-browser-devices]').text()).toContain('接続している端末はありません')
    expect(wrapper.findAll('[data-browser-device]').length).toBe(0)
  })
})

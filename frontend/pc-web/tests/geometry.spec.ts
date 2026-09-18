import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { formatIsoDateTime, useToast } from '@study21/web-shared'
import GeometryView from '@/views/geometry/GeometryView.vue'
import type { GeometryFigure, GeometryTag, GeometryTotals } from '@/api/geometry'

/** 画面のソース（CSS など）を読むときの基点（他機能のテストと同じ作法）。 */
const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/**
 * 図形管理【一覧画面】（2.0 の geometry.jsp 相当）。
 * API はモックし、カードの表示と、各ボタンが送るリクエスト・画面遷移を固定する。
 * 実データ（2.0 から移行した図形 96 件）が入っている前提の画面。
 *
 * ・削除済みの図形は出さない（サーバの既定＝ACTIVE のみ。画面に絞り込みは置かない）
 * ・並び替えは画面から外した（常に既定＝更新日の新しい順）
 * ・【コピー】は複製せず、作図画面を `?copyFrom=` つきで開く
 * ・【印刷】は画面を移らず、その 1 件の印刷プレビューを小窓で開く
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function failure(message: string, status = 500): Response {
  return new Response(
    JSON.stringify({ success: false, code: 'INTERNAL_ERROR', message, data: null, timestamp: '' }),
    { status, headers: { 'Content-Type': 'application/json' } }
  )
}

/** 幾何図形（サムネイルあり・タグあり・メモあり）。 */
function geometryFigure(overrides: Partial<GeometryFigure> = {}): GeometryFigure {
  return {
    figureId: 1,
    figureNo: 'GEO20260402-124745',
    subject: '数学',
    figureType: 'geometry',
    kind: 'saved',
    title: '三角形の重心',
    memo: '中線の交点を作図する。',
    tags: ['三角形', '重心'],
    displayOrder: 1,
    status: 'ACTIVE',
    hasThumbnail: true,
    constructionLength: 1234,
    version: 3,
    createdAt: '2026-04-02T12:47:45',
    updatedAt: '2026-09-12T23:02:00',
    ...overrides
  }
}

/** 関数グラフ（サムネイルなし・タグなし・メモなし・初期データ）。 */
function functionFigure(): GeometryFigure {
  return geometryFigure({
    figureId: 2,
    figureNo: 'geometry-demo-2',
    figureType: 'function',
    kind: 'demo',
    title: '二次関数 y = x²',
    memo: null,
    tags: [],
    hasThumbnail: false,
    constructionLength: 512,
    version: 1,
    updatedAt: null
  })
}

/** 削除済みの図形（API の既定では返らない。返ってきても削除済みの表示は出さない）。 */
function deletedFigure(): GeometryFigure {
  return geometryFigure({
    figureId: 3,
    figureNo: 'GEO20260403-090000',
    title: '削除した円',
    memo: '使わなくなった作図。',
    tags: ['円'],
    status: 'DELETED',
    hasThumbnail: false,
    version: 2
  })
}

const TOTALS: GeometryTotals = {
  figureCount: 96,
  geometryCount: 60,
  functionCount: 36,
  deletedCount: 2
}

function tagList(): GeometryTag[] {
  return [
    { tag: '三角形', count: 12 },
    { tag: '放物線', count: 5 }
  ]
}

function router(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/geometry', component: Dummy },
      { path: '/:area/geometry-draw', component: Dummy },
      { path: '/:area/geometry-ai', component: Dummy }
    ]
  })
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

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

/** 一覧の応答（クエリの page / size をそのまま返す）。 */
function figurePage(
  url: string,
  items: GeometryFigure[],
  totalElements: number,
  totalPages: number
): Response {
  const params = new URLSearchParams(url.split('?')[1] ?? '')
  return ok({
    items,
    totalElements,
    page: Number(params.get('page') ?? '1'),
    size: Number(params.get('size') ?? '24'),
    totalPages,
    totals: TOTALS
  })
}

describe('図形管理【一覧画面】', () => {
  async function setup(options: {
    items?: GeometryFigure[]
    tags?: GeometryTag[]
    totalElements?: number
    totalPages?: number
    handlers?: (url: string, method: string, body: Record<string, unknown> | null) => Response | null
  } = {}) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const items = options.items ?? [geometryFigure(), functionFigure()]
    const tags = options.tags ?? tagList()
    const totalElements = options.totalElements ?? items.length
    const totalPages = options.totalPages ?? 1

    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url)
      const method = (init?.method ?? 'GET').toUpperCase()
      const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
      const handled = options.handlers?.(target, method, body)
      if (handled) return handled
      if (method === 'GET' && target.startsWith('/api/user/geometry/figures?')) {
        return figurePage(target, items, totalElements, totalPages)
      }
      if (method === 'GET' && target.startsWith('/api/user/geometry/tags')) {
        return ok({ items: tags })
      }
      // AI 生図のタスク（この画面は図形一覧の上に出す。既定は 0 件＝セクションを出さない）
      if (method === 'GET' && target.startsWith('/api/user/geometry/ai/tasks')) {
        return ok({ items: [], totalElements: 0, limit: 20 })
      }
      if (method === 'DELETE' && /\/figures\/\d+$/.test(target)) {
        return ok({ count: 1, message: '図形を削除しました。' })
      }
      return ok({ count: 1, message: 'OK' })
    })

    vi.stubGlobal('fetch', fetchMock)
    const appRouter = router()
    await appRouter.push('/student/geometry')
    await appRouter.isReady()
    const wrapper = mount(GeometryView, { global: { plugins: [pinia, appRouter] } })
    await flushPromises()
    return { wrapper, fetchMock, appRouter }
  }

  beforeEach(() => {
    useToast().items.splice(0)
    vi.restoreAllMocks()
  })

  it('一覧の見出しに「一覧」のアイコンが付く', async () => {
    const { wrapper } = await setup()

    const title = wrapper.get('.card__title')
    expect(title.text()).toContain('図形一覧')
    expect(title.get('use').attributes('href')).toBe('#i-list')
  })

  it('図形カードと一覧のサマリを日本語で描画する', async () => {
    const { wrapper } = await setup()

    const text = wrapper.text()
    // 図形名・図形番号・種別・登録区分
    expect(text).toContain('三角形の重心')
    expect(text).toContain('GEO20260402-124745')
    expect(wrapper.get('[data-gm-card="1"] [data-gm-type-badge]').text()).toBe('幾何図形')
    expect(wrapper.get('[data-gm-card="1"] [data-gm-type-badge]').classes()).toContain('badge--info')
    expect(wrapper.get('[data-gm-card="2"] [data-gm-type-badge]').text()).toBe('関数グラフ')
    expect(wrapper.get('[data-gm-card="2"] [data-gm-type-badge]').classes()).toContain('badge--warning')
    expect(wrapper.get('[data-gm-card="1"] [data-gm-kind-badge]').text()).toBe('自作')
    expect(wrapper.get('[data-gm-card="2"] [data-gm-kind-badge]').text()).toBe('初期データ')
    // タグ・メモ・作図データの文字数
    expect(wrapper.get('[data-gm-card="1"] [data-gm-tags]').text()).toContain('三角形')
    expect(wrapper.get('[data-gm-card="1"] [data-gm-tags]').text()).toContain('重心')
    expect(wrapper.get('[data-gm-card="1"] [data-gm-memo]').text()).toBe('中線の交点を作図する。')
    expect(wrapper.get('[data-gm-card="2"] [data-gm-memo]').text()).toBe('メモはありません。')
    expect(wrapper.get('[data-gm-card="2"] [data-gm-tags]').text()).toBe('タグなし')
    expect(wrapper.get('[data-gm-card="1"] [data-gm-meta]').text()).toContain('更新')
    // 作図データの文字数は出さない（利用者にとって意味が無い。更新日時だけ出す）
    expect(wrapper.get('[data-gm-card="1"] [data-gm-meta]').text()).toContain('更新')
    expect(wrapper.get('[data-gm-card="1"] [data-gm-meta]').text()).not.toContain('文字')
    expect(wrapper.get('[data-gm-card="2"] [data-gm-meta]').text()).not.toContain('文字')
    // 一覧のサマリ（totals）
    const summary = wrapper.get('[data-gm-summary]').text()
    expect(summary).toContain('全 96 件')
    expect(summary).toContain('幾何 60')
    expect(summary).toContain('関数 36')
    expect(summary).toContain('削除済 2')
  })

  it('サムネイルの img は version 付きの API の URL を使う', async () => {
    const { wrapper } = await setup()

    const thumb = wrapper.get('[data-gm-card="1"] [data-gm-thumb]')
    expect(thumb.attributes('src')).toBe('/api/user/geometry/figures/1/thumbnail?v=3')
    expect(thumb.attributes('alt')).toBe('三角形の重心')
    expect(thumb.attributes('loading')).toBe('lazy')
  })

  it('サムネイルが無いカードは案内を出す', async () => {
    const { wrapper } = await setup()

    expect(wrapper.find('[data-gm-card="2"] [data-gm-thumb]').exists()).toBe(false)
    expect(wrapper.get('[data-gm-card="2"] [data-gm-thumb-empty]').text()).toContain('サムネイルなし')
  })

  it('並び替えと「削除済みも表示」の入力は置かない', async () => {
    const { wrapper } = await setup()

    // 並び替えは常に既定（更新日の新しい順）、削除済みはサーバの既定（ACTIVE のみ）で読む
    expect(wrapper.find('[data-gm-filter="sort"]').exists()).toBe(false)
    expect(wrapper.find('[data-gm-filter="includeDeleted"]').exists()).toBe(false)
    // 残す検索条件はキーワードとタイプだけ
    expect(wrapper.find('[data-gm-filter="keyword"]').exists()).toBe(true)
    expect(wrapper.find('[data-gm-filter="figureType"]').exists()).toBe(true)
  })

  it('検索条件をクエリで送る（キーワード・タイプ・既定の並び）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-gm-filter="keyword"]').setValue('三角形')
    await wrapper.get('[data-gm-filter="figureType"]').setValue('function')
    await wrapper.get('[data-gm-search]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).filter((entry) => entry.url.startsWith('/api/user/geometry/figures?')).at(-1)
    expect(call?.method).toBe('GET')
    expect(call?.url).toContain(`keyword=${encodeURIComponent('三角形')}`)
    expect(call?.url).toContain('figureType=function')
    // 常に更新日の新しい順で読み、削除済みは取りに行かない
    expect(call?.url).toContain('sort=updatedDesc')
    expect(call?.url).not.toContain('includeDeleted')
    expect(call?.url).toContain('page=1')
  })

  it('キーワードは Enter でも検索する', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-gm-filter="keyword"]').setValue('放物線')
    await wrapper.get('[data-gm-filter="keyword"]').trigger('keyup.enter')
    await flushPromises()

    const call = recorded(fetchMock).filter((entry) => entry.url.startsWith('/api/user/geometry/figures?')).at(-1)
    expect(call?.url).toContain(`keyword=${encodeURIComponent('放物線')}`)
  })

  it('リセットで検索条件を消して 1 ページ目から読み直す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-gm-filter="keyword"]').setValue('三角形')
    await wrapper.get('[data-gm-filter="figureType"]').setValue('geometry')
    await wrapper.get('[data-gm-reset]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).filter((entry) => entry.url.startsWith('/api/user/geometry/figures?')).at(-1)
    expect(call?.url).toBe('/api/user/geometry/figures?sort=updatedDesc&page=1&size=24')
    // 画面の入力も既定値に戻る
    expect((wrapper.get('[data-gm-filter="keyword"]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('[data-gm-filter="figureType"]').element as HTMLSelectElement).value).toBe('')
  })

  it('タグ候補を件数つきで描画し、クリックで絞り込み・もう一度で解除する', async () => {
    const { wrapper, fetchMock } = await setup()

    expect(wrapper.get('[data-gm-tag-chip="三角形"]').text()).toContain('12')
    expect(wrapper.get('[data-gm-tag-chip="放物線"]').text()).toContain('5')

    await wrapper.get('[data-gm-tag-chip="三角形"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-gm-tag-chip="三角形"]').classes()).toContain('is-active')
    const filtered = recorded(fetchMock).filter((entry) => entry.url.startsWith('/api/user/geometry/figures?')).at(-1)
    expect(filtered?.url).toContain(`tag=${encodeURIComponent('三角形')}`)

    await wrapper.get('[data-gm-tag-chip="三角形"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-gm-tag-chip="三角形"]').classes()).not.toContain('is-active')
    const cleared = recorded(fetchMock).filter((entry) => entry.url.startsWith('/api/user/geometry/figures?')).at(-1)
    expect(cleared?.url).not.toContain('tag=')
  })

  it('タグが 1 つも無ければその案内を出す', async () => {
    const { wrapper } = await setup({ tags: [] })

    expect(wrapper.get('[data-gm-tag-empty]').text()).toBe('タグはまだありません。')
    expect(wrapper.find('[data-gm-tag-chip="三角形"]').exists()).toBe(false)
  })

  it('該当が無いときは新規作成を案内する', async () => {
    const { wrapper } = await setup({ items: [] })

    expect(wrapper.get('[data-gm-empty]').text()).toContain('該当する図形がありません。【新規】から作図できます。')
    expect(wrapper.find('[data-gm-card="1"]').exists()).toBe(false)
  })

  it('読み込みに失敗したらエラーを alert に出す', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        method === 'GET' && url.startsWith('/api/user/geometry/figures?')
          ? failure('サーバーでエラーが発生しました。')
          : null
    })

    expect(wrapper.get('.alert.alert--danger').text()).toContain('サーバーでエラーが発生しました。')
  })

  it('削除は確認してから DELETE を呼び、キャンセルなら呼ばない', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-gm-card="1"] [data-gm-delete]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some((entry) => entry.method === 'DELETE')).toBe(false)

    confirmSpy.mockReturnValue(true)
    await wrapper.get('[data-gm-card="1"] [data-gm-delete]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some(
      (entry) => entry.method === 'DELETE' && entry.url === '/api/user/geometry/figures/1'
    )).toBe(true)
    expect(toastMessages()).toContain('図形を削除しました。')
    // 削除後は一覧を取り直す
    expect(recorded(fetchMock).filter(
      (entry) => entry.method === 'GET' && entry.url.startsWith('/api/user/geometry/figures?')
    ).length).toBeGreaterThan(1)
    confirmSpy.mockRestore()
  })

  it('コピーは複製せず、作図画面を copyFrom つきで開く', async () => {
    const { wrapper, fetchMock, appRouter } = await setup()

    await wrapper.get('[data-gm-card="1"] [data-gm-copy]').trigger('click')
    await flushPromises()

    // 複製の API（POST /figures/{id}/copy）は呼ばない
    expect(recorded(fetchMock).some((entry) => entry.method === 'POST')).toBe(false)
    // 一覧も取り直さない（画面を離れるだけ）
    expect(recorded(fetchMock).filter(
      (entry) => entry.method === 'GET' && entry.url.startsWith('/api/user/geometry/figures?')
    )).toHaveLength(1)
    expect(appRouter.currentRoute.value.path).toBe('/student/geometry-draw')
    expect(appRouter.currentRoute.value.query.copyFrom).toBe('1')
    expect(appRouter.currentRoute.value.query.geometryId).toBeUndefined()
  })

  it('編集で作図画面（geometryId つき）へ移動する', async () => {
    const { wrapper, appRouter } = await setup()

    await wrapper.get('[data-gm-card="2"] [data-gm-edit]').trigger('click')
    await flushPromises()

    expect(appRouter.currentRoute.value.path).toBe('/student/geometry-draw')
    expect(appRouter.currentRoute.value.query.geometryId).toBe('2')
  })

  it('【新規】で作成方法の選択ダイアログが開き、選ぶと作図画面へ移動する', async () => {
    const { wrapper, appRouter } = await setup()

    // 【新規】はすぐには画面を移らない（まず作成方法を選ぶ）
    await wrapper.get('[data-gm-create]').trigger('click')
    await flushPromises()
    expect(appRouter.currentRoute.value.path).toBe('/student/geometry')
    expect(document.querySelector('[data-gm-create-dialog]')).not.toBeNull()

    // 手動で作図する（現在の方法）で、今までどおり作図画面を開く
    document.querySelector<HTMLButtonElement>('[data-gm-create-manual]')?.click()
    await flushPromises()

    expect(appRouter.currentRoute.value.path).toBe('/student/geometry-draw')
    expect(appRouter.currentRoute.value.query.geometryId).toBeUndefined()
    // 画面を移ったらダイアログは閉じる
    expect(document.querySelector('[data-gm-create-dialog]')).toBeNull()
    wrapper.unmount()
  })

  it('ページングは件数の変更とページ移動で読み直す', async () => {
    const { wrapper, fetchMock } = await setup({ totalElements: 60, totalPages: 3 })

    const first = recorded(fetchMock).filter((entry) => entry.url.startsWith('/api/user/geometry/figures?')).at(-1)
    expect(first?.url).toContain('page=1')
    expect(first?.url).toContain('size=24')

    await wrapper.get('[data-gm-page-size]').setValue('48')
    await flushPromises()

    const sized = recorded(fetchMock).filter((entry) => entry.url.startsWith('/api/user/geometry/figures?')).at(-1)
    expect(sized?.url).toBe('/api/user/geometry/figures?sort=updatedDesc&page=1&size=48')

    await wrapper.get('[data-gm-page="2"]').trigger('click')
    await flushPromises()

    const paged = recorded(fetchMock).filter((entry) => entry.url.startsWith('/api/user/geometry/figures?')).at(-1)
    expect(paged?.url).toContain('page=2')
    expect(wrapper.get('.pagination__info').text()).toContain('全 60 件（2 / 3 ページ）')
  })

  it('削除済みのバッジ・注記は出さない（削除済みは一覧に出さない）', async () => {
    const { wrapper } = await setup({ items: [deletedFigure()] })

    const card = wrapper.get('[data-gm-card="3"]')
    expect(card.find('[data-gm-deleted-badge]').exists()).toBe(false)
    expect(card.find('[data-gm-deleted-note]').exists()).toBe(false)
    expect(card.classes()).not.toContain('is-deleted')
    expect(card.text()).not.toContain('削除済み')
  })

  it('検索条件の列は【検索】【新規】【リセット】だけ（印刷はカードの中）', async () => {
    const { wrapper } = await setup()

    // 検索条件の操作列（検索 → 新規 → リセット の順）
    const actions = wrapper.get('.search-panel__actions')
    const labels = actions.findAll('button').map((button) => button.text().replace(/\s+/g, ' ').trim())
    expect(labels).toEqual(['検索', '新規', 'リセット'])
    expect(actions.find('[data-gm-search]').exists()).toBe(true)
    expect(actions.find('[data-gm-create]').exists()).toBe(true)
    // 印刷は上部の操作列には置かない（カードの中に出す。利用者の指定）
    expect(actions.find('[data-gm-print]').exists()).toBe(false)
    // 新規は他のページと同じ主色のボタン（「＋ 新規」）
    const create = actions.get('[data-gm-create]')
    expect(create.classes()).toContain('btn--primary')
    expect(create.findAll('svg')).toHaveLength(1)
    expect(create.get('use').attributes('href')).toBe('#i-plus')

    // 【再読み込み】は置かない（一覧ヘッダーにも操作ボタンは無い）
    expect(wrapper.find('[data-gm-refresh]').exists()).toBe(false)
    expect(wrapper.get('.card__header').findAll('button')).toHaveLength(0)
  })

  it('新規作成は検索条件の列から開ける（【新規】は主色のボタンのまま）', async () => {
    const { wrapper, appRouter } = await setup()
    await wrapper.get('.search-panel__actions [data-gm-create]').trigger('click')
    await flushPromises()

    // 押した直後は選択ダイアログ（画面は移らない）
    expect(appRouter.currentRoute.value.path).toBe('/student/geometry')
    expect(document.querySelector('[data-gm-create-dialog]')).not.toBeNull()
    wrapper.unmount()
  })

  it('検索で一覧を取り直す（タグ候補は画面を開いたときに読む）', async () => {
    const { wrapper, fetchMock } = await setup()

    const before = recorded(fetchMock).length
    await wrapper.get('[data-gm-search]').trigger('click')
    await flushPromises()

    const calls = recorded(fetchMock).slice(before)
    expect(calls.some((entry) => entry.url.startsWith('/api/user/geometry/figures?'))).toBe(true)
  })

  /**
   * 作成方法の選択ダイアログ（【新規】で開く）。
   * 2 択（手動で作図する／AI で作図する）だけを出し、選んだ先へ画面を移す。
   * 小窓は `body` へ Teleport するので、中身は `document` から取る。
   */
  describe('作成方法の選択ダイアログ（【新規】）', () => {
    beforeEach(() => {
      // 前のテストの小窓が残らないようにする（Teleport 先は body）
      document.body.innerHTML = ''
    })

    /** 【新規】を押してダイアログを開く。 */
    async function openDialog(options: Record<string, unknown> = {}) {
      const mounted = await setup(options)
      // AI 生図の設定を読み終えてから【新規】を押す（設定で導線が変わるため）
      await flushPromises()
      await mounted.wrapper.get('[data-gm-create]').trigger('click')
      await flushPromises()
      return mounted
    }

    it('【新規】は 2 択のダイアログを開く（手動で作図する／AI で作図する）', async () => {
      const { wrapper, appRouter } = await openDialog()

      // 画面は移らない（まず選ばせる）
      expect(appRouter.currentRoute.value.path).toBe('/student/geometry')
      const dialog = document.querySelector('[data-gm-create-dialog]')
      expect(dialog).not.toBeNull()
      // ダイアログの作法（role / aria-modal / × の aria-label）
      expect(dialog?.getAttribute('role')).toBe('dialog')
      expect(dialog?.getAttribute('aria-modal')).toBe('true')
      expect(dialog?.getAttribute('aria-label')).toBe('新規の作成方法')
      expect(dialog?.querySelector('.dialog__close')?.getAttribute('aria-label')).toBe('閉じる')
      // 選択肢は 2 つだけ
      const choices = [...dialog!.querySelectorAll<HTMLElement>('.gm-choice')]
      expect(choices).toHaveLength(2)
      expect(choices[0].dataset.gmCreateManual).toBe('')
      expect(choices[1].dataset.gmCreateAi).toBe('')
      expect(choices[0].textContent).toContain('手動で作図する（現在の方法）')
      expect(choices[1].textContent).toContain('AI で作図する（画像から）')
      // AI 側は使えることを示す（AI 生図の画面で作図まで進める）
      expect(dialog?.querySelector('[data-gm-create-ai-badge]')?.textContent).toBe('使えます')
      expect(dialog?.textContent).toContain('作図画面で確認・調整してから')
      // アイコン（手動＝編集・AI＝AI の杖）
      expect(choices[0].querySelector('use')?.getAttribute('href')).toBe('#i-edit')
      expect(choices[1].querySelector('use')?.getAttribute('href')).toBe('#i-wand')
      wrapper.unmount()
    })

    it('AI 生図が無効（設定）なら選択肢を出さず、ダイアログを挟まずに作図画面を開く', async () => {
      const { wrapper, appRouter } = await openDialog({
        handlers: (url: string) => (url.endsWith('/geometry/ai/options')
          ? ok({ enabled: false, assistEnabled: false, maxImageMb: 10, maxImagePixels: 1536,
              defaultCrop: 'manual', defaultKind: 'figure', approval: 'manual',
              dailyLimit: 20, usedToday: 0, notice: '「AI 生図」はシステム設定で無効になっています。' })
          : null)
      })
      // 選択肢が 1 つだけなので、ダイアログを出さずにそのまま作図画面へ
      expect(document.querySelector('[data-gm-create-dialog]')).toBeNull()
      expect(appRouter.currentRoute.value.path).toBe('/student/geometry-draw')
      wrapper.unmount()
    })

    it('AI で作図するを選ぶと AI 生図の画面へ移動する', async () => {
      const { wrapper, appRouter } = await openDialog()

      document.querySelector<HTMLButtonElement>('[data-gm-create-ai]')?.click()
      await flushPromises()

      expect(appRouter.currentRoute.value.path).toBe('/student/geometry-ai')
      expect(document.querySelector('[data-gm-create-dialog]')).toBeNull()
      wrapper.unmount()
    })

    it('キャンセルと右上の × で閉じる（画面は移らない）', async () => {
      const first = await openDialog()
      document.querySelector<HTMLButtonElement>('[data-gm-create-cancel]')?.click()
      await flushPromises()
      expect(document.querySelector('[data-gm-create-dialog]')).toBeNull()
      expect(first.appRouter.currentRoute.value.path).toBe('/student/geometry')
      first.wrapper.unmount()

      const second = await openDialog()
      document.querySelector<HTMLButtonElement>('[data-gm-create-dialog] .dialog__close')?.click()
      await flushPromises()
      expect(document.querySelector('[data-gm-create-dialog]')).toBeNull()
      second.wrapper.unmount()
    })

    it('背景（灰色の部分）をクリックしても閉じない', async () => {
      const { wrapper } = await openDialog()

      const overlay = document.querySelector<HTMLElement>('.overlay')
      expect(overlay).not.toBeNull()
      overlay?.dispatchEvent(new MouseEvent('click', { bubbles: true }))
      await flushPromises()

      expect(document.querySelector('[data-gm-create-dialog]')).not.toBeNull()
      wrapper.unmount()
    })

    it('ダイアログを開いても API を呼ばない（存在しない API を叩かない）', async () => {
      const { wrapper, fetchMock } = await setup()
      const before = recorded(fetchMock).length

      await wrapper.get('[data-gm-create]').trigger('click')
      await flushPromises()

      expect(recorded(fetchMock).length).toBe(before)
      wrapper.unmount()
    })
  })

  /**
   * 印刷プレビューの小窓（カードの【印刷】で開く）。
   * 画面は移らず、その 1 件の紙面を出して、【印刷する】で紙に、【閉じる】で閉じる。
   * 小窓は `body` へ Teleport するので、中身は `document` から取る。
   */
  describe('印刷プレビューの小窓（カードの【印刷】）', () => {
    beforeEach(() => {
      // 前のテストの小窓が残らないようにする（Teleport 先は body）
      document.body.innerHTML = ''
      document.documentElement.classList.remove('gm-print-open')
    })

    /** カードの【印刷】を押して小窓を開く。 */
    async function openPrint(figureId: number) {
      const mounted = await setup()
      await mounted.wrapper.get(`[data-gm-card="${figureId}"] [data-gm-print]`).trigger('click')
      await flushPromises()
      return mounted
    }

    it('カードの操作は 編集 → コピー → 印刷 → 削除 のアイコンボタン（意味は title と aria-label）', async () => {
      const { wrapper } = await setup()

      const actions = wrapper.get('[data-gm-card="1"] .gm-card__actions')
      const buttons = actions.findAll('button')
      // 文字は入れない（カードが狭くて入らない）。意味は title と aria-label で伝える
      expect(buttons.map((button) => button.text().trim())).toEqual(['', '', '', ''])
      expect(buttons.map((button) => button.attributes('title')))
        .toEqual(['「三角形の重心」を編集', '「三角形の重心」をコピー', '「三角形の重心」を印刷', '「三角形の重心」を削除'])
      expect(buttons.map((button) => button.attributes('aria-label')))
        .toEqual(buttons.map((button) => button.attributes('title')))
      // アイコンだけのボタン（他の画面の操作列と同じ書き方）
      expect(buttons.every((button) => button.classes().includes('btn--icon'))).toBe(true)
      expect(buttons.every((button) => button.classes().includes('btn--sm'))).toBe(true)
      expect(buttons.map((button) => button.get('use').attributes('href')))
        .toEqual(['#i-edit', '#i-copy', '#i-print', '#i-trash'])
      // 削除だけ赤（危険な操作）
      expect(buttons[3].classes()).toContain('is-danger')
      expect(buttons.slice(0, 3).every((button) => !button.classes().includes('is-danger'))).toBe(true)
      // 並び順（DOM の順）とフックも今までどおり
      expect(actions.find('[data-gm-edit]').exists()).toBe(true)
      expect(actions.find('[data-gm-copy]').exists()).toBe(true)
      expect(actions.find('[data-gm-print="1"]').exists()).toBe(true)
      expect(actions.find('[data-gm-delete]').exists()).toBe(true)
      expect(buttons.map((button) => button.attributes('data-gm-edit') !== undefined ||
        button.attributes('data-gm-copy') !== undefined ||
        button.attributes('data-gm-print') !== undefined ||
        button.attributes('data-gm-delete') !== undefined)).toEqual([true, true, true, true])
      wrapper.unmount()
    })

    it('操作アイコンの帯はカード本体と面の色を変える（tokens の色から作る）', () => {
      // jsdom は CSS を当てないので、**スタイル定義**を確かめる（実機の色は E2E が見る）
      const css = readFileSync(path.join(webRoot, 'src', 'features', 'geometry', 'geometry.css'), 'utf8')
      const block = css.slice(css.indexOf('.gm-card__actions {'), css.indexOf('.gm-card__actions-spacer'))

      // 帯の背景は tokens の変数から作る（生の色は書かない）
      expect(block).toContain('background: color-mix(in srgb, var(--color-surface-alt) 70%, transparent)')
      expect(block).not.toMatch(/background:\s*#/)
      // カードの角に合わせて丸める（面がはみ出さない）
      expect(block).toContain('border-bottom-left-radius')
      expect(block).toContain('border-bottom-right-radius')
      // 4 つのアイコンは今までどおり（この帯の中に並ぶ）
      expect(block).toContain('display: flex')
      expect(block).toContain('border-top: 1px solid var(--color-border)')
    })

    it('カードの【印刷】は画面を移らず、その図形の小窓を開く', async () => {
      const { wrapper, appRouter } = await openPrint(1)

      // 印刷プレビューのページへは移らない（小窓で見せる）
      expect(appRouter.currentRoute.value.path).toBe('/student/geometry')
      expect(document.querySelector('[data-gm-print-dialog]')).not.toBeNull()
      // 閉じる手段（右上の × と【閉じる】）がある
      expect(document.querySelector('[data-gm-print-dialog] .dialog__close')?.getAttribute('aria-label')).toBe('閉じる')
      expect(document.querySelector('[data-gm-print-close]')).not.toBeNull()
      wrapper.unmount()
    })

    it('既定は図だけを出し、詳細情報の行は出さない', async () => {
      const { wrapper } = await openPrint(1)

      const sheet = document.querySelector('[data-gm-print-sheet]')
      expect(sheet).not.toBeNull()
      expect(sheet?.classList.contains('gm-print-sheet--figure')).toBe(true)
      // 図形名・番号・種別・区分・タグ・更新日・メモは出さない（図に集中する）
      const detailHooks = [
        'data-gm-print-title', 'data-gm-print-no', 'data-gm-print-fields', 'data-gm-print-type',
        'data-gm-print-kind', 'data-gm-print-tags', 'data-gm-print-updated', 'data-gm-print-memo'
      ]
      for (const hook of detailHooks) {
        expect(document.querySelector(`[${hook}]`)).toBeNull()
      }
      // 図（サムネイル）だけは出す（一覧と同じ URL）
      const thumb = sheet?.querySelector('[data-gm-print-thumb]')
      expect(thumb?.getAttribute('src')).toBe('/api/user/geometry/figures/1/thumbnail?v=3')
      expect(thumb?.getAttribute('alt')).toBe('三角形の重心')
      // 切り替えのチェックは既定で外れている
      expect(document.querySelector<HTMLInputElement>('[data-gm-print-detail]')?.checked).toBe(false)
      wrapper.unmount()
    })

    it('【詳細情報も印刷する】を入れると情報が出て、外すと図だけに戻る', async () => {
      const { wrapper } = await openPrint(1)

      document.querySelector<HTMLInputElement>('[data-gm-print-detail]')?.click()
      await flushPromises()

      const sheet = document.querySelector('[data-gm-print-sheet]')
      expect(sheet?.classList.contains('gm-print-sheet--detail')).toBe(true)
      expect(sheet?.querySelector('[data-gm-print-title]')?.textContent).toBe('三角形の重心')
      expect(sheet?.querySelector('[data-gm-print-no]')?.textContent).toBe('GEO20260402-124745')
      expect(sheet?.querySelector('[data-gm-print-type]')?.textContent).toBe('幾何図形')
      expect(sheet?.querySelector('[data-gm-print-kind]')?.textContent).toBe('作成した図形')
      expect(sheet?.querySelector('[data-gm-print-tags]')?.textContent).toBe('三角形 / 重心')
      expect(sheet?.querySelector('[data-gm-print-updated]')?.textContent)
        .toBe(formatIsoDateTime('2026-09-12T23:02:00'))
      expect(sheet?.querySelector('[data-gm-print-memo]')?.textContent).toBe('中線の交点を作図する。')
      expect(sheet?.querySelector('[data-gm-print-thumb]')).not.toBeNull()

      // もう一度押すと図だけに戻る
      document.querySelector<HTMLInputElement>('[data-gm-print-detail]')?.click()
      await flushPromises()

      expect(document.querySelector('[data-gm-print-sheet]')?.classList.contains('gm-print-sheet--figure')).toBe(true)
      expect(document.querySelector('[data-gm-print-fields]')).toBeNull()
      expect(document.querySelector('[data-gm-print-title]')).toBeNull()
      expect(document.querySelector('[data-gm-print-thumb]')).not.toBeNull()
      wrapper.unmount()
    })

    it('サムネイル・タグ・メモが無い図形も、詳細を入れれば今までどおり出す', async () => {
      const { wrapper } = await openPrint(2)

      // 既定では図だけ（図が無い図形は「サムネイルなし」の案内）
      expect(document.querySelector('[data-gm-print-thumb]')).toBeNull()
      expect(document.querySelector('[data-gm-print-thumb-empty]')?.textContent).toContain('サムネイルなし')
      expect(document.querySelector('[data-gm-print-fields]')).toBeNull()

      document.querySelector<HTMLInputElement>('[data-gm-print-detail]')?.click()
      await flushPromises()

      expect(document.querySelector('[data-gm-print-tags]')?.textContent).toBe('タグなし')
      expect(document.querySelector('[data-gm-print-memo]')).toBeNull()
      expect(document.querySelector('[data-gm-print-kind]')?.textContent).toBe('初期データ')
      // 更新日が無い図形は作成日を出す
      expect(document.querySelector('[data-gm-print-updated]')?.textContent)
        .toBe(formatIsoDateTime('2026-04-02T12:47:45'))
      wrapper.unmount()
    })

    it('【印刷する】でブラウザの印刷を 1 回だけ呼ぶ', async () => {
      const print = vi.spyOn(window, 'print').mockImplementation(() => undefined)
      const { wrapper } = await openPrint(1)

      // 開いただけでは印刷しない
      expect(print).not.toHaveBeenCalled()
      const run = document.querySelector<HTMLElement>('[data-gm-print-run]')
      expect(run?.tagName).toBe('BUTTON')
      run?.click()

      expect(print).toHaveBeenCalledTimes(1)
      print.mockRestore()
      wrapper.unmount()
    })

    it('【閉じる】で小窓が閉じ、紙の切り替えも戻る', async () => {
      const { wrapper } = await openPrint(1)
      expect(document.documentElement.classList.contains('gm-print-open')).toBe(true)

      document.querySelector<HTMLElement>('[data-gm-print-close]')?.click()
      await flushPromises()

      expect(document.querySelector('[data-gm-print-dialog]')).toBeNull()
      expect(document.documentElement.classList.contains('gm-print-open')).toBe(false)
      wrapper.unmount()
    })

    it('印刷するときは紙面だけを出し、画面と操作の行は隠す（@media print）', () => {
      const css = readFileSync(
        path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src/features/geometry/geometry.css'),
        'utf8'
      )
      const printBlock = /@media print\s*\{([\s\S]*?)\n\}/.exec(css)?.[1] ?? ''
      expect(printBlock).not.toBe('')

      // 画面の切り替えは小窓が付ける html の gm-print-open で行う
      expect(printBlock).toContain('html.gm-print-open')
      const hidden = /([^{}]+)\{\s*display:\s*none\s*!important/.exec(printBlock)?.[1] ?? ''
      expect(hidden).toContain('.gm-print-dialog__foot')
      expect(hidden).toContain('.dialog__close')
      // アプリの画面（サイドバー・上部バー・検索条件・一覧）は出さない
      expect(hidden).toContain('.app')
      expect(hidden).toContain('.sidebar')
      expect(hidden).toContain('.topbar')
      expect(hidden).toContain('.gm-page')
      // 紙面はそのままの大きさで出す（図だけ／詳細の 2 つの状態を出し分ける）
      expect(printBlock).toContain('.gm-print-sheet')
      expect(printBlock).toContain('.gm-print-sheet--figure')
      expect(printBlock).toContain('.gm-print-sheet--detail')
    })
  })
})

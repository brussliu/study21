import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import BookManagementView from '@/views/reading/BookManagementView.vue'
import { useAuthStore } from '@/stores/auth'
import type { ReadingBook, ReadingCategory } from '@/api/reading'

/**
 * 読書管理【書籍管理】（保護者が使う画面）。
 *
 * API をモックし、本棚（分類タブ・カード）と書籍ダイアログが送るリクエスト、
 * および画面に出さないもの（進捗・読書履歴・書籍を開く導線・検索条件カード）を固定する。
 *
 * 2.1 での決めごとをテストで押さえる:
 * ・分類タブは実在する分類から自動で作る（分類の追加・変更・削除・並べ替えのボタンは無い）
 * ・分類はジャンルだけ。本の言語は `言語` フィールド（既定 英語）で送る
 * ・1 冊 1 PDF・1 表紙（選び直しは差し替え。外す・リセットのボタンは無い）
 * ・本文 PDF はドラッグ＆ドロップ、表紙はドラッグ＆ドロップと貼り付け（Ctrl+V）で選ぶ
 * ・PDF の総ページ数は同梱の pdf.js で数える（`window.pdfjsLib` を偽物に差し替える）
 */
/** 機能の CSS（読み手側の本棚と同じ見た目かどうかを定義で確かめる）。 */
function readReadingCss(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/features/reading/reading.css'), 'utf8')
}

/** 書籍閲覧（読み手側）の CSS。表紙の大きさを 2 画面で揃えるために読む。 */
function readReaderCss(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/features/reading/reading-reader.css'), 'utf8')
}

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

/* ---------- テストデータ（2.0 から移行した 6 冊のうち 4 冊分） ---------- */

function book(overrides: Partial<ReadingBook> = {}): ReadingBook {
  return {
    bookId: 1,
    bookNo: 'ER-20260402-124745',
    subject: '英語',
    title: '01.Harry Potter and the Sorcerers Stone',
    author: 'J. K. Rowling',
    language: '英語',
    difficulty: 'Starter',
    status: '読書中',
    totalPages: 272,
    currentPage: 7,
    pinned: true,
    tags: ['Novel'],
    summary: '毎日読みます。',
    note: null,
    recentMinutes: 10,
    totalMinutes: 120,
    markCount: 808,
    lastReadAt: '2026-09-12T23:02:00',
    readPercent: 3,
    categoryId: 11,
    categoryName: '小説',
    hasPdf: true,
    pdfOriginalName: '01.Harry Potter and the Sorcerers Stone.pdf',
    // 2.0 の実体は未取得なので「PDF 未登録」になる
    pdfAvailable: false,
    hasCover: true,
    coverAvailable: true,
    version: 3,
    createdAt: '2026-04-02T12:47:45',
    updatedAt: '2026-09-12T23:02:00',
    // 2026-09-14 の追加: 公開範囲と自分の本棚（既定は移行済みの全体書籍・本棚には入っていない）
    scope: 'GLOBAL',
    ownerFamilyId: null,
    ownerFamilyLabel: null,
    inMyShelf: false,
    ...overrides
  }
}

/** 実体のある PDF を持つ本（「PDF あり」の表示）。 */
function bookWithPdf(): ReadingBook {
  return book({
    bookId: 2,
    bookNo: 'ER-20260403-170753',
    title: '02.The Secret Garden',
    author: 'Frances Hodgson Burnett',
    difficulty: 'Elementary',
    status: '未着手',
    totalPages: 180,
    currentPage: 1,
    pinned: false,
    tags: [],
    summary: null,
    recentMinutes: 0,
    totalMinutes: 0,
    markCount: 0,
    lastReadAt: null,
    readPercent: 0,
    categoryId: 11,
    categoryName: '小説',
    hasPdf: true,
    pdfOriginalName: '02.The Secret Garden.pdf',
    pdfAvailable: true,
    hasCover: true,
    coverAvailable: false,
    version: 1
  })
}

/** 未分類・日本語・PDF も表紙もまだ無い本。 */
function bookWithoutFile(): ReadingBook {
  return book({
    bookId: 3,
    bookNo: 'ER-20260404-090000',
    title: '03.Chinese Stories',
    author: 'Li Wei',
    language: '日本語',
    difficulty: 'Intermediate',
    status: '未着手',
    totalPages: 120,
    currentPage: 1,
    pinned: false,
    tags: [],
    summary: null,
    recentMinutes: 0,
    totalMinutes: 0,
    markCount: 0,
    lastReadAt: null,
    readPercent: 0,
    categoryId: null,
    categoryName: null,
    hasPdf: false,
    pdfOriginalName: null,
    pdfAvailable: false,
    hasCover: false,
    coverAvailable: false,
    version: 1
  })
}

/** 中国語の本（言語バッジの確認用。置頂は 1 冊目だけにして、棚の分け方を試しやすくする）。 */
function secondNovel(): ReadingBook {
  return book({
    bookId: 4,
    bookNo: 'ER-20260405-090000',
    title: '04.Harry Potter and the Chamber of Secrets',
    author: 'J. K. Rowling',
    language: '中国語',
    pinned: false,
    totalPages: 300,
    hasPdf: false,
    pdfOriginalName: null,
    pdfAvailable: false,
    hasCover: false,
    coverAvailable: false,
    version: 2
  })
}

/** 実在する分類はジャンルだけ（冊数バッジ付き。タブはここから自動で作られる）。 */
const CATEGORIES: ReadingCategory[] = [
  { categoryId: 11, name: '小説', displayOrder: 0, description: '物語', bookCount: 5, ownerFamilyId: null },
  { categoryId: 12, name: '雑誌・ガイド', displayOrder: 1, description: null, bookCount: 1, ownerFamilyId: null }
]

function bookPage(items: ReadingBook[], totalElements: number, page: number, size: number): unknown {
  return {
    items,
    totalElements,
    page,
    size,
    totalPages: Math.max(1, Math.ceil(totalElements / size)),
    // 進捗・時間の集計は API にはあるが、この画面は使わない。
    // pdfCount（実体のある PDF の冊数）と uncategorizedCount（未分類の冊数）はサマリとタブに使う
    totals: {
      bookCount: totalElements,
      readingCount: 1,
      finishedCount: 0,
      totalMinutes: 120,
      markCount: 808,
      pdfCount: items.filter((item) => item.pdfAvailable).length,
      uncategorizedCount: items.filter((item) => item.categoryId === null).length,
      myShelfCount: 0
    }
  }
}

/* ---------- 通信の記録とモック ---------- */

type Call = {
  url: string
  method: string
  /** JSON の body（JSON のときだけ） */
  json: Record<string, unknown> | null
  /** multipart の body（FormData のときだけ） */
  form: FormData | null
}

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    const body = init.body
    const isForm = typeof FormData !== 'undefined' && body instanceof FormData
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      json: typeof body === 'string' ? (JSON.parse(body) as Record<string, unknown>) : null,
      form: isForm ? (body as FormData) : null
    }
  })
}

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

function fileOf(name: string, type = 'application/pdf', bytes = 4): File {
  return new File([new Uint8Array(bytes)], name, { type })
}

/** input[type=file] にファイルを入れて change を起こす。 */
async function attachFile(wrapper: VueWrapper, selector: string, file: File): Promise<void> {
  const input = wrapper.get(selector)
  Object.defineProperty(input.element, 'files', { value: [file], configurable: true })
  await input.trigger('change')
  await flushPromises()
  await flushPromises()
}

/**
 * ドロップゾーンにファイルを落とす。
 * jsdom に DataTransfer が無いので、必要な形だけを持たせた偽物を dataTransfer に入れて `drop` を起こす。
 */
async function dropFile(wrapper: VueWrapper, selector: string, ...files: File[]): Promise<void> {
  await wrapper.get(selector).trigger('drop', { dataTransfer: { files, types: ['Files'] } })
  await flushPromises()
  await flushPromises()
}

/** 表紙の貼り付け（Ctrl+V）。`clipboardData.items` に画像の item を入れて `paste` を起こす。 */
async function pasteImage(wrapper: VueWrapper, file: File): Promise<void> {
  const items = [{ kind: 'file', type: file.type, getAsFile: () => file }]
  await wrapper.get('[data-rd-dialog]').trigger('paste', { clipboardData: { items, files: [file] } })
  await flushPromises()
}

interface PdfJsStub {
  getDocument: ReturnType<typeof vi.fn>
}

/** 同梱の pdf.js の代わりに、決まったページ数を返す偽物を入れる。 */
function stubPdfJs(numPages = 15): PdfJsStub {
  const getDocument = vi.fn(() => ({ promise: Promise.resolve({ numPages }) }))
  ;(window as unknown as { pdfjsLib?: unknown }).pdfjsLib = {
    getDocument,
    GlobalWorkerOptions: { workerSrc: '' }
  }
  return { getDocument }
}

/** 更新系の既定の応答（画面は data.message をトーストに出す）。 */
function mutationResponse(url: string, method: string): Response {
  if (method === 'POST' && url === '/api/user/reading/books') {
    return ok({ book: book({ bookId: 9 }), message: '書籍を登録しました。（ER-20260913-101500）' })
  }
  if (method === 'PUT' && /\/books\/\d+$/.test(url)) {
    return ok({ book: book({ title: '01.Harry Potter 1' }), message: '書籍を更新しました。' })
  }
  if (method === 'PATCH' && url.endsWith('/pin')) {
    return ok({ book: book({ pinned: false }), message: '置頂を解除しました。' })
  }
  if (method === 'POST' && /\/books\/\d+\/pdf$/.test(url)) {
    return ok({ book: book({ pdfAvailable: true }), message: '本文 PDF を登録しました。' })
  }
  if (method === 'POST' && /\/books\/\d+\/cover$/.test(url)) {
    return ok({ book: book({ coverAvailable: true }), message: '表紙を登録しました。' })
  }
  if (method === 'DELETE' && /\/books\/\d+$/.test(url)) {
    return ok({ count: 1, message: '書籍を削除しました。' })
  }
  if (method === 'POST' && url === '/api/user/reading/categories') {
    return ok({ category: { categoryId: 21, name: '絵本', displayOrder: 2, description: null, bookCount: 0, ownerFamilyId: null }, message: '分類を追加しました。' })
  }
  return ok({ count: 1, message: 'OK' })
}

describe('読書管理【書籍管理】', () => {
  async function setup(options: {
    items?: ReadingBook[]
    categories?: ReadingCategory[]
    /** 画面を使うロール（既定は管理者＝全体書籍を管理する。2026-09-14 の決定 Q3） */
    role?: 'ADMIN' | 'GUARDIAN'
    handlers?: (url: string, method: string, json: Record<string, unknown> | null) => Response | null
  } = {}) {
    const items = options.items ?? [book(), bookWithPdf(), bookWithoutFile(), secondNovel()]
    const categories = options.categories ?? CATEGORIES
    // 画面はロールで区分（公開範囲）と操作の出し分けをする
    setActivePinia(createPinia())
    useAuthStore().login(options.role ?? 'ADMIN', 'テスト管理者')

    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url)
      const method = (init?.method ?? 'GET').toUpperCase()
      const body = init?.body
      const json = typeof body === 'string' ? (JSON.parse(body) as Record<string, unknown>) : null
      const handled = options.handlers?.(target, method, json)
      if (handled) return handled

      const parsed = new URL(target, 'http://localhost')
      if (method === 'GET' && parsed.pathname === '/api/user/reading/books') {
        const categoryParam = parsed.searchParams.get('categoryId')
        const scopeParam = parsed.searchParams.get('scope')
        const pageNo = Number(parsed.searchParams.get('page') ?? '1')
        // 画面はページングせず size=100（API の上限）で 1 回だけ読む
        const size = Number(parsed.searchParams.get('size') ?? '100')
        const scoped = scopeParam === null || scopeParam === 'ALL'
          ? items
          : items.filter((item) => item.scope === scopeParam)
        const filtered = categoryParam === null
          ? scoped
          : categoryParam === '0'
            ? scoped.filter((item) => item.categoryId === null)
            : scoped.filter((item) => item.categoryId === Number(categoryParam))
        const start = (pageNo - 1) * size
        return ok(bookPage(filtered.slice(start, start + size), filtered.length, pageNo, size))
      }
      if (method === 'GET' && parsed.pathname === '/api/user/reading/categories') {
        return ok({ items: categories })
      }
      return mutationResponse(target, method)
    })

    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(BookManagementView)
    await flushPromises()
    return { wrapper, fetchMock }
  }

  beforeEach(() => {
    useToast().items.splice(0)
    vi.restoreAllMocks()
    delete (window as unknown as { pdfjsLib?: unknown }).pdfjsLib
    // 既定は 15 ページを返す偽の pdf.js（画面は選択時に総ページ数を自動入力する）
    stubPdfJs(15)
    // jsdom には無いので、表紙の見本（オブジェクトURL）だけ偽物にする
    vi.stubGlobal('URL', Object.assign(URL, {
      createObjectURL: vi.fn(() => 'blob:test'),
      revokeObjectURL: vi.fn()
    }))
  })

  /* ---------- B. 書籍管理（一覧） ---------- */

  it('1 行目は【本棚】と操作、2 行目は分類と冊数のサマリ（全・読書中・読了）', async () => {
    const { wrapper } = await setup()

    // 1 行目: 見出しと、その右の【再読み込み】【新規】
    const header = wrapper.get('.card__header')
    expect(header.get('.card__title').text()).toBe('本棚')
    for (const [hook, label] of [['data-rd-refresh', '再読み込み'], ['data-rd-add', '新規']] as const) {
      const button = header.get(`[${hook}]`)
      expect(button.text()).toContain(label)
      expect(button.findAll('svg').length).toBe(1)
    }
    expect(header.find('[data-rd-refresh]').classes()).toContain('btn--secondary')
    expect(header.find('[data-rd-add]').classes()).toContain('btn--primary')

    // 2 行目: 分類と、その右のサマリ
    const row = wrapper.get('.rd-cats__row')
    expect(row.get('[data-rd-category="all"]').text()).toBe('すべて')
    const summary = row.get('[data-rd-summary]')
    expect(summary.text()).toContain('全 4 冊')
    expect(summary.text()).toContain('読書中 1 冊')
    expect(summary.text()).toContain('読了 0 冊')
    expect(summary.text()).toContain('・')
    // 分類の数・PDF の登録数は出さない（本棚タブと同じ 3 つだけ）
    expect(summary.text()).not.toContain('分類')
    expect(summary.text()).not.toContain('PDF')

    // 見出しの行にサマリは出さない（2 行目に移した）
    expect(header.find('[data-rd-summary]').exists()).toBe(false)
    expect(header.find('[data-rd-category]').exists()).toBe(false)
  })

  it('置頂の本は「置頂」の棚に分かれ、分類の棚には出ない（すべてのとき）', async () => {
    const { wrapper } = await setup()

    // 置頂の棚が最上部。その下に分類（小説 → 未分類）が続く
    expect(wrapper.findAll('[data-rd-shelf-section]')
      .map((el) => el.attributes('data-rd-shelf-section')))
      .toEqual(['pinned', 'cat-11', 'none'])

    const pinned = wrapper.get('[data-rd-shelf-section="pinned"]')
    expect(pinned.text()).toContain('1 冊')
    expect(pinned.get('[data-rd-book="1"]').get('[data-rd-pin-badge]').text()).toContain('置頂')

    // 置頂に移した本は分類の棚には出さない（同じ本が 2 回並ばない）
    const novel = wrapper.get('[data-rd-shelf-section="cat-11"]')
    expect(novel.text()).toContain('2 冊')
    expect(novel.find('[data-rd-book="1"]').exists()).toBe(false)
    expect(novel.find('[data-rd-book="2"]').exists()).toBe(true)
    expect(wrapper.findAll('[data-rd-book="1"]').length).toBe(1)
  })

  it('分類で絞ったときも、置頂の本は「置頂」の棚に分かれて最上部に出る', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-rd-category="11"]').trigger('click')
    await flushPromises()

    expect(wrapper.findAll('[data-rd-shelf-section]')
      .map((el) => el.attributes('data-rd-shelf-section'))).toEqual(['pinned', 'cat-11'])
    // 置頂の棚が上、その下に絞り込んだ分類の本（置頂の本は分類の棚には出さない）
    const pinned = wrapper.get('[data-rd-shelf-section="pinned"]')
    expect(pinned.text()).toContain('置頂')
    expect(pinned.findAll('[data-rd-book]').map((el) => el.attributes('data-rd-book'))).toEqual(['1'])
    const novel = wrapper.get('[data-rd-shelf-section="cat-11"]')
    expect(novel.text()).toContain('小説')
    expect(novel.findAll('[data-rd-book]').map((el) => el.attributes('data-rd-book'))).toEqual(['2', '4'])
    expect(novel.find('[data-rd-book="1"]').exists()).toBe(false)

    // 置頂が 1 冊も無い棚では「置頂」の見出しを出さない
    await wrapper.get('[data-rd-category="none"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-rd-shelf-section="pinned"]').exists()).toBe(false)
    expect(wrapper.findAll('[data-rd-shelf-section]')
      .map((el) => el.attributes('data-rd-shelf-section'))).toEqual(['none'])
  })

  it('分類ボタンは【書籍閲覧】の本棚タブと同じ丸いボタン（設計システムの .tabs ではない）', async () => {
    const { wrapper } = await setup()

    const tab = wrapper.get('[data-rd-category="all"]')
    expect(tab.classes()).toContain('rd-cat')
    expect(tab.classes()).not.toContain('tabs__tab')
    expect(tab.attributes('role')).toBe('tab')
    expect(tab.attributes('aria-selected')).toBe('true')

    // 見た目は読み手側の本棚タブ（.rrd-tab）と同じ: 丸い枠・選択中は主色で塗る
    const css = readReadingCss()
    const rule = /\.rd-cat\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(rule).toMatch(/border:\s*1px solid var\(--color-border\)/)
    expect(rule).toMatch(/border-radius:\s*var\(--radius-pill\)/)
    expect(rule).toMatch(/font-size:\s*var\(--fs-sm\)/)
    const active = /\.rd-cat\.is-active\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(active).toMatch(/background:\s*var\(--color-primary\)/)
    expect(active).toMatch(/color:\s*var\(--color-on-primary\)/)
  })

  it('分類の行は【書籍閲覧】のタブ行と同じく、下に区切り線を引く', async () => {
    const { wrapper } = await setup()

    // 分類のボタンと冊数のサマリは同じ行（.rd-cats__row）に入れる
    expect(wrapper.get('.rd-cats__row').findAll('[data-rd-category]').length).toBe(4)

    // jsdom は CSS を適用しないので、定義そのものを確認する。
    // 線はタブ行（reading-reader.css の .rrd-tabs）と同じ 1px・同じトークンで引く
    const css = readReadingCss()
    const row = /\.rd-cats__row\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(row).toMatch(/border-bottom:\s*1px solid var\(--color-border\)/)
    // 線はカード幅いっぱいに引く（左右の余白は padding で戻す）
    expect(row).toMatch(/padding:\s*0 var\(--sp-5\) var\(--sp-3\)/)
  })

  it('表紙の枠は【書籍閲覧】の本棚カードと同じ大きさ（両画面で揃える）', async () => {
    await setup()

    // jsdom は CSS を適用しないので、定義そのものを確認する。
    // 2 画面で同じ値にしておく（実機の検証でも一致を見ている）
    const coverSize = (css: string, selector: string): string => {
      const pattern = new RegExp(`${selector.replace(/\./g, '\\.')}\\s*\\{[^}]*\\}`)
      const rule = pattern.exec(css)?.[0] ?? ''
      const width = /width:\s*(\d+)px/.exec(rule)?.[1] ?? ''
      const height = /height:\s*(\d+)px/.exec(rule)?.[1] ?? ''
      return `${width}×${height}`
    }
    const management = coverSize(readReadingCss(), '.rd-book__cover')
    const reader = coverSize(readReaderCss(), '.rrd-book__cover')
    expect(management).toBe('136×190')
    expect(reader).toBe(management)
  })

  it('本が 1 冊も無い棚は見出しごと出さない', async () => {
    const { wrapper } = await setup({ items: [bookWithPdf()] })

    expect(wrapper.findAll('[data-rd-shelf-section]')
      .map((el) => el.attributes('data-rd-shelf-section'))).toEqual(['cat-11'])
    expect(wrapper.find('[data-rd-shelf-section="pinned"]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-shelf-section="none"]').exists()).toBe(false)
  })

  it('分類タブは実在する分類から自動で作られ、分類の追加・変更・削除・並べ替えのボタンは無い', async () => {
    const { wrapper, fetchMock } = await setup()

    // 実在する分類（小説 5 冊 / 雑誌・ガイド 1 冊）がそのままタブになる。
    // 冊数の書き方は本棚タブ（書籍閲覧）と同じく分類名の括弧書き
    expect(wrapper.get('[data-rd-category="all"]').text()).toBe('すべて')
    expect(wrapper.get('[data-rd-category="11"]').text()).toBe('小説（5）')
    expect(wrapper.get('[data-rd-category="12"]').text()).toBe('雑誌・ガイド（1）')
    expect(wrapper.get('[data-rd-category="none"]').text()).toBe('未分類')
    // タブは分類の数（2）＋「すべて」＋「未分類」だけ
    expect(wrapper.findAll('[data-rd-category]').length).toBe(4)
    expect(wrapper.get('.rd-cats__row').findAll('[data-rd-category]').length).toBe(4)
    // タブに言語混じりの分類名は無い（言語は言語フィールドで表す）
    expect(wrapper.text()).not.toContain('英語 小説')
    expect(wrapper.text()).not.toContain('英語 リーディング')

    // 分類の管理（追加・変更・削除・並べ替え）のボタン・ダイアログは無い
    for (const hook of [
      'data-rd-category-add', 'data-rd-category-edit', 'data-rd-category-delete',
      'data-rd-category-up', 'data-rd-category-down', 'data-rd-category-dialog'
    ]) {
      expect(wrapper.find(`[${hook}]`).exists(), hook).toBe(false)
    }
    expect(wrapper.text()).not.toContain('分類を追加')
    expect(wrapper.text()).not.toContain('↑')

    // 分類を選ぶと categoryId 付きで読み直す（1 ページ目から）。未分類は 0
    await wrapper.get('[data-rd-category="11"]').trigger('click')
    await flushPromises()
    const byCategory = recorded(fetchMock).filter(
      (call) => call.method === 'GET' && call.url.includes('categoryId=11')
    )
    expect(byCategory.length).toBe(1)
    expect(byCategory[0].url).toContain('page=1')
    expect(wrapper.find('[data-rd-book="3"]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-book="1"]').exists()).toBe(true)

    await wrapper.get('[data-rd-category="none"]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some(
      (call) => call.method === 'GET' && call.url.includes('categoryId=0')
    )).toBe(true)
    expect(wrapper.find('[data-rd-book="3"]').exists()).toBe(true)
    expect(wrapper.find('[data-rd-book="1"]').exists()).toBe(false)

    // 選択中の分類は is-active で分かる
    expect(wrapper.get('[data-rd-category="none"]').classes()).toContain('is-active')
    expect(wrapper.get('[data-rd-category="all"]').classes()).not.toContain('is-active')
  })

  it('見出しの行に【再読み込み】【新規】があり、分類の一覧は role=tablist で出す', async () => {
    const { wrapper, fetchMock } = await setup()

    // 見出しは書籍閲覧の本棚タブと同じ「本棚」。ページタイトルは共通レイアウトの役割
    expect(wrapper.find('.rd-page__head').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('書籍管理')

    const list = wrapper.get('.rd-cat-list')
    expect(list.attributes('role')).toBe('tablist')
    expect(list.attributes('aria-label')).toBe('本棚の分類')
    for (const tab of list.findAll('[role="tab"]')) {
      expect(tab.attributes('aria-selected')).toBeDefined()
    }
    expect(list.get('.rd-cat.is-active').attributes('data-rd-category')).toBe('all')

    // 再読み込みは GET をやり直す
    const before = recorded(fetchMock).filter(
      (call) => call.method === 'GET' && call.url.includes('/books?')
    ).length
    await wrapper.get('[data-rd-refresh]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).filter(
      (call) => call.method === 'GET' && call.url.includes('/books?')
    ).length).toBeGreaterThan(before)

    // 登録のボタンでダイアログが開く
    await wrapper.get('[data-rd-add]').trigger('click')
    expect(wrapper.find('[data-rd-dialog]').exists()).toBe(true)
  })

  it('カードは読み手側（本棚タブ）と同じ並びで、操作はアイコンボタン、PDF アップロードのボタンは無い', async () => {
    const { wrapper } = await setup()

    const card = wrapper.get('[data-rd-book="1"]')
    expect(card.get('[data-rd-title]').text()).toBe('01.Harry Potter and the Sorcerers Stone')
    expect(card.get('[data-rd-author]').text()).toBe('J. K. Rowling')
    expect(card.get('[data-rd-difficulty]').text()).toBe('Starter（入門）')
    expect(card.get('[data-rd-language]').text()).toBe('英語')
    expect(card.get('[data-rd-tags]').text()).toContain('Novel')
    expect(card.get('[data-rd-pages]').text()).toContain('全 272 頁')
    expect(card.get('[data-rd-category-name]').text()).toBe('小説')
    // 置頂中の本は見出しに「置頂」バッジが付く（取れなければ get が失敗する）
    expect(card.get('[data-rd-pin-badge]').text()).toContain('置頂')

    // 並びは読み手側と同じ: 表紙 → 書名 → 作者 → バッジ（難易度/言語/分類）→ ページ数 → タグ → PDF → 操作
    // （本文 PDF がある 2 冊目で見る。ダウンロードも操作の先頭に付く）
    const ordered = wrapper.get('[data-rd-book="2"]').html()
    let previous = -1
    for (const hook of [
      'data-rd-cover', 'data-rd-title', 'data-rd-author', 'data-rd-difficulty', 'data-rd-language',
      'data-rd-category-name', 'data-rd-pages', 'data-rd-tags', 'data-rd-pdf-state',
      'data-rd-download', 'data-rd-edit'
    ]) {
      const at = ordered.indexOf(hook)
      expect(at, hook).toBeGreaterThan(previous)
      previous = at
    }
    // 作者は独立した 1 行（読み手側と同じ .rd-book__author）
    expect(card.find('.rd-book__author').exists()).toBe(true)

    // 表紙は読み手側と同じ img[data-rd-cover]（大きさは CSS 側で 136×190 に揃える）
    const cover = card.get('img[data-rd-cover]')
    expect(cover.attributes('src')).toContain('/api/user/reading/books/1/cover')
    expect(cover.attributes('width')).toBeUndefined()
    // 操作は 3 つのアイコンボタン（修正＝edit / 置頂＝bookmark / 削除＝trash）＋ PDF のダウンロード。文字は出さない
    const actions = card.get('.rd-book__actions')
    expect(actions.findAll('button').length).toBe(3)
    for (const button of actions.findAll('button')) {
      expect(button.text()).toBe('')
      expect(button.attributes('title')).toBeTruthy()
      expect(button.attributes('aria-label')).toBeTruthy()
      expect(button.attributes('aria-label')).toMatch(/[ぁ-んァ-ヶ一-龠]/)
    }
    expect(actions.find('[data-rd-edit="1"]').exists()).toBe(true)
    expect(actions.find('[data-rd-pin="1"]').exists()).toBe(true)
    expect(actions.find('[data-rd-delete="1"]').exists()).toBe(true)
    // 本文 PDF が無い本にはダウンロードを出さない（2 冊目にあるのは別テストで固定）
    expect(actions.find('[data-rd-download]').exists()).toBe(false)
    // 置頂中はアイコンが is-active
    expect(actions.get('[data-rd-pin="1"]').classes()).toContain('is-active')

    // 進捗・読書ステータス・標記数・最終読書はカードに出さない
    for (const hidden of ['現在ページ', '読書ステータス', '読書時間', '標記 ', '最終読書', '進捗']) {
      expect(card.text(), hidden).not.toContain(hidden)
    }
    expect(card.find('.rd-book__progress').exists()).toBe(false)
    expect(card.find('.rd-book__stats').exists()).toBe(false)

    // PDF のアップロードは編集ダイアログだけで行う（カードには置かない）
    expect(wrapper.find('[data-rd-upload-pdf]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-pdf-upload-input]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('PDF をアップロード')

    // 書籍を開く導線は置かない
    expect(card.find('[data-rd-open]').exists()).toBe(false)

    const noCover = wrapper.get('[data-rd-book="3"]')
    expect(noCover.find('img[data-rd-cover]').exists()).toBe(false)
    expect(noCover.get('[data-rd-cover-empty]').text()).toContain('表紙なし')
    expect(noCover.get('[data-rd-tags]').text()).toContain('タグ未設定')
    expect(noCover.get('[data-rd-language]').text()).toBe('日本語')
    expect(noCover.find('[data-rd-download]').exists()).toBe(false)
    // 置頂していない本のブックマークは is-active にしない
    expect(noCover.get('[data-rd-pin="3"]').classes()).not.toContain('is-active')

    // 「すべて」のときは棚ごとの見出しを出す（置頂は別の棚に分かれる）
    expect(wrapper.get('[data-rd-shelf-section="pinned"]').text()).toContain('置頂')
    expect(wrapper.get('[data-rd-shelf-section="cat-11"]').text()).toContain('小説')
    expect(wrapper.get('[data-rd-shelf-section="none"]').text()).toContain('未分類')
  })

  it('カードの最後の行は、左に PDF の状態・右に操作アイコンを同じ行に並べる', async () => {
    const { wrapper } = await setup()

    // 操作を別の行に置くとカードの下が間延びするので、メタ情報と同じ行（.rd-book__foot）に入れる
    const card = wrapper.get('[data-rd-book="1"]')
    const foot = card.get('.rd-book__foot')
    expect(foot.find('[data-rd-pdf-state]').exists()).toBe(true)
    expect(foot.find('.rd-book__actions').exists()).toBe(true)
    // 最後の行であること（タグの後・カードの一番下）
    expect(card.get('.rd-book__main').element.lastElementChild).toBe(foot.element)

    // 見た目は「左＝メタ情報、右＝アイコン」（jsdom は CSS を適用しないので定義で確かめる）
    const css = readReadingCss()
    const rule = /\.rd-book__foot\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(rule).toMatch(/display:\s*flex/)
    expect(rule).toMatch(/justify-content:\s*space-between/)
    expect(rule).toMatch(/align-items:\s*center/)
    // アイコンは行の右端に寄せ、下に押し下げない（margin-top: auto は付けない）
    const actions = /\.rd-book__actions\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(actions).toMatch(/flex:\s*0 0 auto/)
    expect(actions).not.toMatch(/margin-top/)

    // 長いファイル名でも 1 行に収める（折り返すと行が高くなり、アイコンが行の真ん中に落ちる）
    const name = card.get('.rd-book__pdf-name')
    expect(name.text()).toContain('01.Harry Potter and the Sorcerers Stone.pdf')
    const nameRule = /\.rd-book__pdf-name\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(nameRule).toMatch(/white-space:\s*nowrap/)
    expect(nameRule).toMatch(/text-overflow:\s*ellipsis/)
    // 省略しても全文は title で読める
    expect(card.get('[data-rd-pdf-state]').attributes('title')).toContain('Harry Potter')
  })

  it('カードのグリッドは読み手側（【図書館】）と同じ 420px の最小幅にする', async () => {
    await setup()

    // 340px だと書名やバッジが折り返してカードが縦に伸びる
    // （読み手側の .rrd-shelf と同じ値にして、2 画面のカードの見た目を揃える）。
    // 1280/1680/1920 のどれでも横に溢れないことは実機で確認する
    expect(readReadingCss()).toMatch(
      /\.rd-shelf\s*\{[^}]*grid-template-columns:\s*repeat\(auto-fill,\s*minmax\(420px,\s*1fr\)\)/
    )
  })

  it('操作アイコンは色付きのチップ（ダウンロード＝主色 / 修正＝アクセント / 置頂＝注意 / 削除＝危険）', async () => {
    const { wrapper } = await setup()

    // 色は data のフックごとに CSS の変数（--rd-act）を差し替えて付ける。
    // クラスは増やさないので、アイコンのフックと title・aria-label は今までどおり
    const css = readReadingCss()
    const actRule = (hook: string): string =>
      new RegExp(`\\.rd-book__actions \\.btn--icon\\[data-rd-${hook}\\]\\s*\\{[^}]*\\}`).exec(css)?.[0] ?? ''
    expect(actRule('download')).toMatch(/--rd-act:\s*var\(--color-primary\)/)
    expect(actRule('edit')).toMatch(/--rd-act:\s*var\(--color-info\)/)
    expect(actRule('pin')).toMatch(/--rd-act:\s*var\(--color-warning\)/)
    expect(actRule('delete')).toMatch(/--rd-act:\s*var\(--color-danger\)/)

    // 淡い下地＋色付きのアイコン＋枠線のチップ（色は tokens.css の変数だけを使う）
    const chip = /\.rd-book__actions \.btn--icon\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(chip).toMatch(/width:\s*var\(--control-sm\)/)
    expect(chip).toMatch(/border:\s*1px solid color-mix\(in srgb, var\(--rd-act\)/)
    expect(chip).toMatch(/background:\s*color-mix\(in srgb, var\(--rd-act\)/)
    expect(chip).toMatch(/color:\s*var\(--rd-act\)/)
    // 生の色（16 進・rgb）は書かない（暗色テーマは themes.css が変数を差し替える）
    const actionsCss = css.slice(css.indexOf('/* カードの最後の行'), css.indexOf('/* ---------- ファイルの選択'))
    expect(actionsCss).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    expect(actionsCss).not.toMatch(/\brgba?\(/)

    // ホバーは色を保ったまま下地と枠を濃くする
    const hover = /\.rd-book__actions \.btn--icon:hover:not\(:disabled\)\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(hover).toMatch(/color:\s*var\(--rd-act\)/)

    // 置頂中は注意色を濃くして、いま置頂されていることが分かるようにする
    const activePin = /\.rd-book__actions \.btn--icon\[data-rd-pin\]\.is-active\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(activePin).toMatch(/background:\s*color-mix\(in srgb, var\(--color-warning\)/)
    expect(activePin).toMatch(/border-color:\s*var\(--color-warning\)/)

    // 押せないときは色を落とす
    const disabled = /\.rd-book__actions \.btn--icon:disabled\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(disabled).toMatch(/color:\s*var\(--color-text-subtle\)/)

    // 4 つのフックは今までどおり（画面の見た目だけを変える）
    const actions = wrapper.get('[data-rd-book="1"] .rd-book__actions')
    expect(actions.findAll('[data-rd-edit], [data-rd-pin], [data-rd-delete]').length).toBe(3)
    expect(wrapper.find('[data-rd-book="2"] [data-rd-download="2"]').exists()).toBe(true)
    expect(actions.get('[data-rd-pin="1"]').attributes('title')).toBe('置頂を解除')
    expect(actions.get('[data-rd-delete="1"]').attributes('aria-label')).toContain('を削除')
  })

  it('本文 PDF がある本にはダウンロードのアイコンがあり、実体が無い本には無い', async () => {
    const { wrapper } = await setup()

    // 実体がある本（2 冊目）だけがダウンロードできる
    const link = wrapper.get('[data-rd-book="2"] [data-rd-download="2"]')
    expect(link.element.tagName).toBe('A')
    expect(link.attributes('href')).toBe('/api/user/reading/books/2/pdf?v=1&download=true')
    // サーバが attachment で返すので download 属性は付けない
    expect(link.attributes('download')).toBeUndefined()
    expect(link.find('svg').exists()).toBe(true)
    expect(link.get('use').attributes('href')).toBe('#i-download')
    expect(link.attributes('title')).toContain('02.The Secret Garden.pdf')
    expect(link.attributes('aria-label')).toMatch(/[ぁ-んァ-ヶ一-龠]/)

    // 行はあるが実体が無い本（1 冊目）と、行も無い本（3 冊目）には出さない
    expect(wrapper.find('[data-rd-book="1"] [data-rd-download]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-book="3"] [data-rd-download]').exists()).toBe(false)
  })

  it('PDF の表示は、実体があればファイル名だけ・無ければ「PDF 未登録」と分かる', async () => {
    const { wrapper } = await setup()

    // 実体がある本はファイル名だけ（「PDF あり」は出さない）
    const ready = wrapper.get('[data-rd-book="2"] [data-rd-pdf-state]')
    expect(ready.text()).toContain('02.The Secret Garden.pdf')
    expect(ready.text()).not.toContain('PDF あり')
    expect(wrapper.text()).not.toContain('PDF あり')

    // 実体が無い本は未登録だと分かるように残す（元ファイル名を添える）
    expect(wrapper.get('[data-rd-book="1"] [data-rd-pdf-state]').text())
      .toContain('PDF 未登録（01.Harry Potter and the Sorcerers Stone.pdf）')
    expect(wrapper.get('[data-rd-book="3"] [data-rd-pdf-state]').text()).toContain('PDF 未登録')
    expect(wrapper.get('[data-rd-book="3"] [data-rd-pdf-state]').text()).not.toContain('（')
  })

  it('進捗・読書履歴・書籍を開く導線・検索条件カードを出さない', async () => {
    const { wrapper } = await setup()
    const text = wrapper.text()

    // 冊数のサマリ（全・読書中・読了）は出す。1 冊ごとの進捗は出さない
    for (const hidden of ['現在ページ', '読書ステータス', '読書時間', '累計', '読書履歴', '進捗']) {
      expect(text, hidden).not.toContain(hidden)
    }
    expect(wrapper.find('[data-rd-history]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-open]').exists()).toBe(false)
    expect(wrapper.find('.search-panel').exists()).toBe(false)
    expect(wrapper.find('[data-rd-progress]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-status]').exists()).toBe(false)
  })

  /* ---------- A. 書籍編集ダイアログ ---------- */

  it('ダイアログ: 言語（中国語・英語・日本語）を選べ、【リセット】【PDF を外す】【表紙を外す】は無い', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-rd-edit="3"]').trigger('click')
    expect(wrapper.find('[data-rd-dialog]').exists()).toBe(true)

    // 言語のドロップダウン（選択肢は 3 つ・修正時は現在値）
    const select = wrapper.get('#rdLanguage')
    expect(select.findAll('option').map((option) => option.text()))
      .toEqual(['中国語', '英語', '日本語'])
    expect((select.element as HTMLSelectElement).value).toBe('日本語')

    // 分類の選択肢はジャンルだけ（＋ 未分類 ＋ 新しい分類を追加…）。言語混じりの分類名は出さない
    const categories = wrapper.get('#rdCategory')
    expect(categories.findAll('option').map((option) => option.text()))
      .toEqual(['未分類', '小説', '雑誌・ガイド', '＋ 新しい分類を追加…'])
    expect(wrapper.find('#rdNewCategory').exists()).toBe(false)

    // 消したボタン
    for (const hook of ['data-rd-form-reset', 'data-rd-pdf-delete', 'data-rd-cover-delete']) {
      expect(wrapper.find(`[${hook}]`).exists(), hook).toBe(false)
    }
    expect(wrapper.text()).not.toContain('リセット')
    expect(wrapper.text()).not.toContain('PDF を外す')
    expect(wrapper.text()).not.toContain('表紙を外す')

    // 1 冊 1 PDF・1 表紙（ドロップゾーンと、差し替えだと分かる案内）
    expect(wrapper.find('[data-rd-pdf-drop]').exists()).toBe(true)
    expect(wrapper.find('[data-rd-cover-drop]').exists()).toBe(true)
    expect(wrapper.get('[data-rd-pdf-drop]').text()).toContain('ここに PDF をドロップ または クリックして選択')
    // ネイティブの [ファイル選択] は出さず、input はゾーンに重ねて透明にする
    expect(wrapper.get('[data-rd-pdf-input]').classes()).toContain('rd-drop__input')
    expect(wrapper.get('[data-rd-cover-input]').classes()).toContain('rd-drop__input')
    expect(wrapper.get('[data-rd-dialog-pdf-state]').text()).toContain('PDF 未登録')
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).toContain('表紙なし')
  })

  it('総ページ数の入力欄は無く、PDF から数えた値を目立つ表示で出す（空なら 1 で登録）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-rd-add]').trigger('click')
    // 入力欄は無い（PDF から自動で取れるので手入力させない）
    expect(wrapper.find('#rdTotalPages').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('総ページ数（PDF を選ぶと自動で入ります）')
    expect(wrapper.find('[data-rd-pdf-pages]').exists()).toBe(false)

    // 空のままでも保存できる（1 頁で登録する）
    await wrapper.get('#rdTitle').setValue('はじめての絵本')
    await wrapper.get('#rdAuthor').setValue('山田 太郎')
    await wrapper.get('[data-rd-save]').trigger('click')
    await flushPromises()
    const create = recorded(fetchMock).find(
      (call) => call.method === 'POST' && call.url === '/api/user/reading/books'
    )
    expect(create?.json).toMatchObject({ title: 'はじめての絵本', totalPages: 1 })
    expect(wrapper.find('[data-rd-dialog]').exists()).toBe(false)

    // PDF を選ぶと pdf.js が数えた値を出し、その値で保存する
    await wrapper.get('[data-rd-add]').trigger('click')
    await dropFile(wrapper, '[data-rd-pdf-drop]', fileOf('picture-book.pdf'))
    const pages = wrapper.get('[data-rd-pdf-pages]')
    expect(pages.text()).toContain('総ページ数')
    expect(pages.text()).toContain('15')
    expect(wrapper.get('[data-rd-pdf-pages]').text()).toContain('頁')

    // 目立つ色（tokens の主色）と大きめの文字で出す
    const css = readReadingCss()
    const rule = /\.rd-pdf-pages\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(rule).toMatch(/color:\s*var\(--color-primary\)/)
    expect(rule).toMatch(/font-size:\s*var\(--fs-md\)/)

    // 数えられないときは案内だけ出す（入力欄は無いので手入力は求めない）
    ;(window as unknown as { pdfjsLib?: unknown }).pdfjsLib = {
      getDocument: () => ({ promise: Promise.reject(new Error('読めません')) }),
      GlobalWorkerOptions: { workerSrc: '' }
    }
    const failing = await setup()
    await failing.wrapper.get('[data-rd-add]').trigger('click')
    await dropFile(failing.wrapper, '[data-rd-pdf-drop]', fileOf('broken.pdf'))
    expect(failing.wrapper.find('[data-rd-pdf-pages]').exists()).toBe(false)
    expect(failing.wrapper.get('[data-rd-pdf-page-note]').text()).toContain('数えられませんでした')
  })

  it('PDF のファイル名は状態の行で目立たせる（選択中・現在のどちらも）', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-rd-edit="1"]').trigger('click')
    // 現在の PDF（実体は無いが行はある）のファイル名を出す
    const state = wrapper.get('[data-rd-dialog-pdf-state]')
    expect(state.text()).toContain('01.Harry Potter and the Sorcerers Stone.pdf')
    expect(state.get('[data-rd-pdf-name]').text()).toBe('01.Harry Potter and the Sorcerers Stone.pdf')

    // 選び直したら選択中のファイル名に変わる
    await attachFile(wrapper, '[data-rd-pdf-input]', fileOf('new-book.pdf'))
    expect(wrapper.get('[data-rd-dialog-pdf-state]').get('[data-rd-pdf-name]').text()).toBe('new-book.pdf')

    const css = readReadingCss()
    const name = /\.rd-file-name\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(name).toMatch(/color:\s*var\(--color-text-strong\)/)
    expect(name).toMatch(/font-weight:\s*var\(--fw-semibold\)/)
  })

  it('表紙が登録済みの本を修正すると、いまの表紙をダイアログに出す', async () => {
    const { wrapper } = await setup()

    // 1 冊目は表紙あり（実体あり）
    await wrapper.get('[data-rd-edit="1"]').trigger('click')
    const preview = wrapper.get('img.rd-drop__preview')
    expect(preview.attributes('src')).toBe('/api/user/reading/books/1/cover?v=3')
    expect(preview.attributes('alt')).toContain('表紙')
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).toContain('現在: 表紙あり')

    // 選び直すと選択中の画像に入れ替わる
    await attachFile(wrapper, '[data-rd-cover-input]', fileOf('new-cover.png', 'image/png'))
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).toContain('new-cover.png')
    expect(wrapper.find('img.rd-drop__preview').exists()).toBe(true)

    // 表紙が無い本では出さない
    await wrapper.get('[data-rd-dialog-close]').trigger('click')
    await wrapper.get('[data-rd-edit="3"]').trigger('click')
    expect(wrapper.find('img.rd-drop__preview').exists()).toBe(false)
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).toContain('表紙なし')
  })

  it('本文 PDF と表紙のアップロード欄は同じ行に左右で並べる（縦長にしない）', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-rd-add]').trigger('click')
    const row = wrapper.get('.rd-upload-row')
    // 左右の 2 列（PDF が左・表紙が右）
    expect(row.findAll(':scope > .field').length).toBe(2)
    expect(row.element.children[0].querySelector('[data-rd-pdf-drop]')).not.toBeNull()
    expect(row.element.children[1].querySelector('[data-rd-cover-drop]')).not.toBeNull()

    const css = readReadingCss()
    const rule = /\.rd-upload-row\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(rule).toMatch(/grid-template-columns:\s*repeat\(2, minmax\(0, 1fr\)\)/)
    // 狭い画面では縦に積む
    expect(css).toMatch(/@media \(max-width: 1100px\)[\s\S]*?\.rd-upload-row\s*\{[^}]*grid-template-columns:\s*minmax\(0, 1fr\)/)
  })

  it('新規登録は language 付きで POST /books → 本文 PDF → 表紙 の順に送る', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-rd-add]').trigger('click')
    // 新規の言語は既定で 英語
    expect((wrapper.get('#rdLanguage').element as HTMLSelectElement).value).toBe('英語')
    expect((wrapper.get('#rdCategory').element as HTMLSelectElement).value).toBe('')

    // 必須（書籍名・作者）が空なら送らない。総ページ数は必須ではない（空なら 1 で登録）
    await wrapper.get('[data-rd-save]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('書籍名を入力してください。')
    expect(wrapper.text()).toContain('作者を入力してください。')
    expect(wrapper.text()).not.toContain('総ページ数は1以上で入力してください。')
    expect(recorded(fetchMock).some((call) => call.method !== 'GET')).toBe(false)
    expect(toastMessages()).toContain('入力内容を確認してください。')

    await wrapper.get('#rdTitle').setValue('The Secret Garden')
    await wrapper.get('#rdAuthor').setValue('Frances Hodgson Burnett')
    await wrapper.get('#rdLanguage').setValue('中国語')
    await wrapper.get('#rdDifficulty').setValue('Elementary')
    await wrapper.get('#rdCategory').setValue('11')
    await wrapper.get('#rdTags').setValue('Classic')
    await wrapper.get('[data-rd-tag-add]').trigger('click')
    // PDF を落とすと pdf.js（偽物は 15 ページ）で総ページ数が決まる
    await dropFile(wrapper, '[data-rd-pdf-drop]', fileOf('garden.pdf'))
    expect(wrapper.get('[data-rd-pdf-pages]').text()).toContain('15')
    await attachFile(wrapper, '[data-rd-cover-input]', fileOf('garden-cover.png', 'image/png'))
    await wrapper.get('#rdSummary').setValue('毎日読みます。')
    await wrapper.get('#rdNote').setValue('図書館で借りた')

    await wrapper.get('[data-rd-save]').trigger('click')
    await flushPromises()
    await flushPromises()

    const calls = recorded(fetchMock)
    const create = calls.findIndex((call) => call.method === 'POST' && call.url === '/api/user/reading/books')
    const pdf = calls.findIndex((call) => call.method === 'POST' && call.url === '/api/user/reading/books/9/pdf')
    const cover = calls.findIndex((call) => call.method === 'POST' && call.url === '/api/user/reading/books/9/cover')
    expect(create).toBeGreaterThan(-1)
    expect(pdf).toBeGreaterThan(create)
    expect(cover).toBeGreaterThan(pdf)

    expect(calls[create].json).toMatchObject({
      title: 'The Secret Garden',
      author: 'Frances Hodgson Burnett',
      language: '中国語',
      difficulty: 'Elementary',
      totalPages: 15,
      categoryId: 11,
      tags: ['Classic'],
      summary: '毎日読みます。',
      note: '図書館で借りた'
    })
    // 新規登録は version を送らない
    expect(calls[create].json?.version).toBeUndefined()
    // 本文 PDF は multipart（file ＋ 総ページ数）で送る
    expect(calls[pdf].form?.get('file')).toBeInstanceOf(File)
    expect(calls[pdf].form?.get('totalPages')).toBe('15')
    expect(calls[cover].form?.get('file')).toBeInstanceOf(File)

    expect(toastMessages()).toContain('書籍を登録しました。（ER-20260913-101500）')
    expect(wrapper.find('[data-rd-dialog]').exists()).toBe(false)
    expect(recorded(fetchMock).filter(
      (call) => call.method === 'GET' && call.url.includes('/api/user/reading/books?')
    ).length).toBeGreaterThan(1)
  })

  it('修正は version 付きの PUT（言語も送る）。置頂は PATCH、削除は確認してから DELETE', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-rd-edit="1"]').trigger('click')
    expect((wrapper.get('#rdTitle').element as HTMLInputElement).value)
      .toBe('01.Harry Potter and the Sorcerers Stone')
    expect((wrapper.get('#rdCategory').element as HTMLSelectElement).value).toBe('11')
    expect(wrapper.get('[data-rd-dialog-pdf-state]').text()).toContain('差し替え')
    await wrapper.get('#rdTitle').setValue('01.Harry Potter 1')
    await wrapper.get('#rdCategory').setValue('12')
    // 表紙を選び直すと差し替えになる（外すボタンは無い）
    await attachFile(wrapper, '[data-rd-cover-input]', fileOf('new-cover.png', 'image/png'))
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).toContain('new-cover.png')
    await wrapper.get('[data-rd-save]').trigger('click')
    await flushPromises()

    const put = recorded(fetchMock).find((call) => call.method === 'PUT')
    expect(put?.url).toBe('/api/user/reading/books/1')
    expect(put?.json).toMatchObject({
      title: '01.Harry Potter 1', categoryId: 12, language: '英語', version: 3
    })
    // PDF を選んでいなければアップロードはしない（外す DELETE も送らない）
    expect(recorded(fetchMock).some(
      (call) => call.method === 'POST' && call.url === '/api/user/reading/books/1/pdf'
    )).toBe(false)
    expect(recorded(fetchMock).some((call) => call.method === 'DELETE')).toBe(false)
    expect(recorded(fetchMock).some(
      (call) => call.method === 'POST' && call.url === '/api/user/reading/books/1/cover'
    )).toBe(true)
    expect(toastMessages()).toContain('書籍を更新しました。')

    // 置頂（1 冊目は置頂済みなので解除）
    await wrapper.get('[data-rd-pin="1"]').trigger('click')
    await flushPromises()
    const pin = recorded(fetchMock).find((call) => call.method === 'PATCH' && call.url.endsWith('/pin'))
    expect(pin?.url).toBe('/api/user/reading/books/1/pin')
    expect(pin?.json).toMatchObject({ pinned: false, version: 3 })
    expect(toastMessages()).toContain('置頂を解除しました。')

    // 削除（キャンセルしたら送らない）
    confirmSpy.mockReturnValue(false)
    await wrapper.get('[data-rd-delete="2"]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some(
      (call) => call.method === 'DELETE' && call.url === '/api/user/reading/books/2'
    )).toBe(false)

    confirmSpy.mockReturnValue(true)
    await wrapper.get('[data-rd-delete="2"]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some(
      (call) => call.method === 'DELETE' && call.url === '/api/user/reading/books/2'
    )).toBe(true)
    expect(toastMessages()).toContain('書籍を削除しました。')
    confirmSpy.mockRestore()
  })

  /* ---------- ドラッグ＆ドロップ・貼り付け ---------- */

  it('本文 PDF はドラッグ＆ドロップで選べ、pdf.js で数えた総ページ数を自動入力する', async () => {
    const pdfJs = stubPdfJs(42)
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-rd-add]').trigger('click')
    expect(wrapper.find('[data-rd-pdf-pages]').exists()).toBe(false)

    // ドラッグ中は枠を強調する
    await wrapper.get('[data-rd-pdf-drop]').trigger('dragover')
    expect(wrapper.get('[data-rd-pdf-drop]').classes()).toContain('is-dragging')
    await wrapper.get('[data-rd-pdf-drop]').trigger('dragleave')
    expect(wrapper.get('[data-rd-pdf-drop]').classes()).not.toContain('is-dragging')

    await dropFile(wrapper, '[data-rd-pdf-drop]', fileOf('42-pages.pdf'))
    expect(pdfJs.getDocument).toHaveBeenCalledTimes(1)
    // 数えたページ数を目立つ表示で出す（入力欄は無い）
    expect(wrapper.get('[data-rd-pdf-pages]').text()).toContain('42')
    expect(wrapper.get('[data-rd-dialog-pdf-state]').text()).toContain('42-pages.pdf')
    expect(wrapper.get('[data-rd-pdf-drop]').classes()).not.toContain('is-dragging')
    expect(recorded(fetchMock).some((call) => call.method === 'POST')).toBe(false)

    // 選び直すと前の選択と差し替わる（1 冊 1 PDF）
    await dropFile(wrapper, '[data-rd-pdf-drop]', fileOf('second.pdf'))
    expect(wrapper.get('[data-rd-dialog-pdf-state]').text()).toContain('second.pdf')
    expect(wrapper.get('[data-rd-dialog-pdf-state]').text()).not.toContain('42-pages.pdf')
  })

  it('表紙は貼り付け（Ctrl+V）とドラッグ＆ドロップで選べ、選び直すと差し替わる', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-rd-edit="1"]').trigger('click')

    // スクリーンショットの貼り付け
    await pasteImage(wrapper, fileOf('image.png', 'image/png'))
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).toContain('image.png')
    expect(wrapper.find('img.rd-drop__preview').exists()).toBe(true)

    // 画像以外（文字など）の貼り付けは無視する
    await wrapper.get('[data-rd-dialog]').trigger('paste', {
      clipboardData: {
        items: [{ kind: 'string', type: 'text/plain', getAsFile: () => null }],
        files: []
      }
    })
    await flushPromises()
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).toContain('image.png')

    // ドラッグ＆ドロップでも選べる（1 冊 1 表紙なので置き換わる）
    await dropFile(wrapper, '[data-rd-cover-drop]', fileOf('screenshot.png', 'image/png'))
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).toContain('screenshot.png')
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).not.toContain('image.png')
  })

  it('表紙に画像以外を落とす／PDF 以外を落とすとトーストで弾き、送らない', async () => {
    const { wrapper, fetchMock } = await setup()
    const writes = (): Call[] => recorded(fetchMock).filter((call) => call.method !== 'GET')

    await wrapper.get('[data-rd-add]').trigger('click')

    // 表紙に PDF を落としたら弾く
    await dropFile(wrapper, '[data-rd-cover-drop]', fileOf('cover.pdf', 'application/pdf'))
    expect(toastMessages()).toContain('表紙は PNG・JPEG・WebP の画像を選んでください。')
    expect(wrapper.get('[data-rd-dialog-cover-state]').text()).toContain('登録されていません')

    // 5MB を超える画像も弾く
    const tooLargeCover = fileOf('big.png', 'image/png')
    Object.defineProperty(tooLargeCover, 'size', { value: 5 * 1024 * 1024 + 1 })
    await dropFile(wrapper, '[data-rd-cover-drop]', tooLargeCover)
    expect(toastMessages()).toContain('表紙は 5MB 以下にしてください。')

    // PDF 以外を落としたら弾く
    await dropFile(wrapper, '[data-rd-pdf-drop]', fileOf('book.png', 'image/png'))
    expect(toastMessages()).toContain('PDF ファイル（.pdf）を選んでください。')

    // 200MB を超える PDF も弾く
    const tooLarge = fileOf('big.pdf')
    Object.defineProperty(tooLarge, 'size', { value: 200 * 1024 * 1024 + 1 })
    await dropFile(wrapper, '[data-rd-pdf-drop]', tooLarge)
    expect(toastMessages()).toContain('本文 PDF は 200MB 以下にしてください。')

    expect(writes().length).toBe(0)
  })

  /* ---------- 分類の新規追加（ダイアログの 1 経路だけ） ---------- */

  it('「＋ 新しい分類を追加…」を選ぶと createReadingCategory → その ID で書籍を保存、の順になる', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-rd-add]').trigger('click')
    await wrapper.get('#rdCategory').setValue('__new__')
    // 選ぶと分類名の入力が出る
    expect(wrapper.find('#rdNewCategory').exists()).toBe(true)

    await wrapper.get('#rdTitle').setValue('はじめての絵本')
    await wrapper.get('#rdAuthor').setValue('山田 太郎')
    // 分類名が空なら保存しない
    await wrapper.get('[data-rd-save]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('新しい分類名を入力してください。')
    expect(recorded(fetchMock).some((call) => call.method === 'POST')).toBe(false)

    await wrapper.get('#rdNewCategory').setValue('絵本')
    await wrapper.get('[data-rd-save]').trigger('click')
    await flushPromises()
    await flushPromises()

    const calls = recorded(fetchMock)
    const category = calls.findIndex(
      (call) => call.method === 'POST' && call.url === '/api/user/reading/categories'
    )
    const create = calls.findIndex((call) => call.method === 'POST' && call.url === '/api/user/reading/books')
    expect(category).toBeGreaterThan(-1)
    expect(create).toBeGreaterThan(category)
    expect(calls[category].json).toMatchObject({ name: '絵本' })
    // 作った分類の ID で書籍を保存する
    expect(calls[create].json).toMatchObject({ title: 'はじめての絵本', language: '英語', categoryId: 21 })
    expect(toastMessages()).toContain('書籍を登録しました。（ER-20260913-101500）')
    // 保存後はタブも作り直す（新しい分類がタブに出る）
    expect(recorded(fetchMock).filter(
      (call) => call.method === 'GET' && call.url.includes('/api/user/reading/categories')
    ).length).toBeGreaterThan(1)
  })

  /* ---------- 全件読み込み・空・失敗 ---------- */

  it('ページングはせず、size=100 で全件を 1 回で読み、ページング UI は無い', async () => {
    // 120 冊あっても 1 回のリクエストで読む（API の 1 回の上限が 100 件）
    const many = Array.from({ length: 120 }, (_, index) => book({
      bookId: index + 1,
      title: `Book ${index + 1}`,
      pinned: false,
      categoryId: 11
    }))
    const { wrapper, fetchMock } = await setup({
      items: many,
      categories: [{ categoryId: 11, name: '小説', displayOrder: 0, description: null, bookCount: 120, ownerFamilyId: null }]
    })

    const listCalls = (): Call[] => recorded(fetchMock).filter(
      (call) => call.method === 'GET' && call.url.includes('/books?')
    )
    // 初回は 1 回だけ・page=1・size=100（20 件ずつではない）
    expect(listCalls().length).toBe(1)
    expect(listCalls()[0].url).toContain('page=1')
    expect(listCalls()[0].url).toContain('size=100')
    expect(listCalls()[0].url).not.toContain('size=20')
    // 返ってきたぶんは全部並べる（この API は 100 件までなので、100 冊を超えると 100 冊まで）
    expect(wrapper.findAll('[data-rd-book]').length).toBe(100)

    // ページング UI は残っていない
    expect(wrapper.find('.pagination').exists()).toBe(false)
    for (const hook of ['data-rd-page-size', 'data-rd-page-prev', 'data-rd-page-next', 'data-rd-page']) {
      expect(wrapper.find(`[${hook}]`).exists(), hook).toBe(false)
    }
    expect(wrapper.text()).not.toContain('ページ')
    expect(wrapper.text()).not.toContain('件数')

    // 分類で絞り込んだときも同じ（1 回・page=1・size=100）
    await wrapper.get('[data-rd-category="11"]').trigger('click')
    await flushPromises()
    const byCategory = listCalls().filter((call) => call.url.includes('categoryId=11'))
    expect(byCategory.length).toBe(1)
    expect(byCategory[0].url).toContain('page=1')
    expect(byCategory[0].url).toContain('size=100')

    // 「全 N 冊」は API の totals.bookCount（120 冊）を使う
    expect(wrapper.get('[data-rd-summary]').text()).toContain('全 120 冊')
  })

  it('棚が空のときの案内と、読み込みに失敗したときのエラーを出す', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        method === 'GET' && url.includes('/books?') && url.includes('categoryId=12')
          ? ok(bookPage([], 0, 1, 24))
          : null
    })

    await wrapper.get('[data-rd-category="12"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-rd-shelf-empty]').text()).toContain('この棚に書籍がありません。')

    const failing = await setup({
      handlers: (url, method) =>
        method === 'GET' && url.includes('/books?') && !url.includes('categoryId=')
          ? failure('サーバーでエラーが発生しました。')
          : null
    })
    expect(failing.wrapper.get('.alert.alert--danger').text()).toContain('サーバーでエラーが発生しました。')
  })


/**
 * 区分（公開範囲）の絞り込み（2026-09-14 の決定 Q3）。
 * 管理者＝全体書籍・保護者＝自分の家庭の本を、**同じ画面**で区分を切り替えて管理する。
 */
describe('読書管理【書籍管理】: 区分（公開範囲）とロール', () => {
  beforeEach(() => {
    // 上の describe の beforeEach と同じ準備（この describe だけを見ても動くように）
    useToast().items.splice(0)
    vi.restoreAllMocks()
    delete (window as unknown as { pdfjsLib?: unknown }).pdfjsLib
    stubPdfJs(15)
    vi.stubGlobal('URL', Object.assign(URL, {
      createObjectURL: vi.fn(() => 'blob:test'),
      revokeObjectURL: vi.fn()
    }))
  })

  it('管理者は区分【全体の本】で scope=GLOBAL を読む（家庭の本の区分は出さない）', async () => {
    const { wrapper, fetchMock } = await setup({ role: 'ADMIN' })

    const scopes = wrapper.findAll('[data-rd-scope-filter]').map((button) => button.attributes('data-rd-scope-filter'))
    expect(scopes).toEqual(['global', 'all'])
    expect(wrapper.get('[data-rd-scope-filter="global"]').attributes('aria-selected')).toBe('true')

    const books = recorded(fetchMock).find(
      (call) => call.method === 'GET' && /\/reading\/books(\?|$)/.test(call.url)
    )
    expect(books?.url).toContain('scope=GLOBAL')
  })

  it('保護者は区分【家庭の本】で scope=FAMILY を読み、全体書籍の操作は出さない', async () => {
    const { wrapper, fetchMock } = await setup({ role: 'GUARDIAN' })

    expect(wrapper.findAll('[data-rd-scope-filter]').map((button) => button.attributes('data-rd-scope-filter')))
      .toEqual(['family', 'all'])
    expect(recorded(fetchMock).find(
      (call) => call.method === 'GET' && /\/reading\/books(\?|$)/.test(call.url)
    )?.url).toContain('scope=FAMILY')

    // 全体書籍（GLOBAL）は読めるだけなので、修正・置頂・削除を出さない
    expect(wrapper.find('[data-rd-book="1"] [data-rd-edit]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-book="1"] [data-rd-pin]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-book="1"] [data-rd-delete]').exists()).toBe(false)

    // 自分の家庭の本（FAMILY）は直せる
    const { wrapper: guardianView } = await setup({
      role: 'GUARDIAN',
      items: [book({ bookId: 9, scope: 'FAMILY', ownerFamilyId: 2, ownerFamilyLabel: '山田 太郎' })]
    })
    expect(guardianView.find('[data-rd-book="9"] [data-rd-edit]').exists()).toBe(true)
    expect(guardianView.get('[data-rd-book="9"] [data-rd-scope-badge]').text()).toContain('家庭の本')
  })

  it('区分を切り替えると、その区分で読み直す', async () => {
    const { wrapper, fetchMock } = await setup({ role: 'ADMIN' })

    await wrapper.get('[data-rd-scope-filter="all"]').trigger('click')
    await flushPromises()

    const books = recorded(fetchMock).filter(
      (call) => call.method === 'GET' && /\/reading\/books(\?|$)/.test(call.url)
    )
    expect(books.at(-1)?.url).toContain('scope=ALL')
  })

  it('登録のダイアログには区分の選択を置かず、サーバが決めることを文章で示す', async () => {
    const admin = await setup({ role: 'ADMIN' })
    await admin.wrapper.get('[data-rd-add]').trigger('click')
    await flushPromises()
    expect(admin.wrapper.get('[data-rd-dialog-scope]').text()).toContain('全体の本')
    expect(admin.wrapper.find('[data-rd-dialog] select[name="scope"]').exists()).toBe(false)

    const guardian = await setup({ role: 'GUARDIAN' })
    await guardian.wrapper.get('[data-rd-add]').trigger('click')
    await flushPromises()
    expect(guardian.wrapper.get('[data-rd-dialog-scope]').text()).toContain('家庭の本')
  })
})
})

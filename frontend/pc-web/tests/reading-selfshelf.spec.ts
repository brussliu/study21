import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { existsSync, readFileSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import BookReaderView from '@/views/reading/BookReaderView.vue'
import { SHELF_MARKS } from '@/features/reading/shelfLook'
import {
  SHELF_BG_COUNT,
  SHELF_BG_STORAGE_KEY,
  normalizeShelfBg,
  shelfBgLabel,
  shelfBgUrl
} from '@/features/reading/shelfBackgrounds'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'
import type {
  ReadingBook, ReadingBookDetail, ReadingCategory, ReadingLookupResult, ReadingMark, ReadingRecord
} from '@/api/reading'

/**
 * 読書管理【書籍閲覧】の「自分の本棚」タブ（背表紙が並ぶ木の棚）。
 *
 * 旧【書籍閲覧2】（`BookShelfView.vue`）の本棚タブを【書籍閲覧】へ移したものなので、
 * このファイルは**棚まわり**（背表紙の見た目・並び・ホバー・抜き出し・木の質感）だけを固定する。
 * 閲覧（PDF・標記・読書記録・ページ一覧・言語ごとのツール）は `reading-reader.spec.ts` が見る。
 *
 * jsdom ではレイアウトもアニメーションも無いので、演出は `prefers-reduced-motion` を偽装して
 * 既定で切り、演出そのものを見るテストだけ `reduceMotion: false` にして
 * DOM のクラス（is-hover / is-pulled / is-dealing）で確かめる。
 */

/* ---------- テストデータ ---------- */

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function book(overrides: Partial<ReadingBook> = {}): ReadingBook {
  return {
    bookId: 1,
    bookNo: 'ER-20260402-124745',
    subject: '英語',
    title: 'The Secret Garden',
    author: 'Frances Hodgson Burnett',
    language: '英語',
    difficulty: 'Elementary',
    status: '読書中',
    totalPages: 40,
    currentPage: 1,
    pinned: true,
    tags: ['Novel'],
    summary: '毎日を読みます。',
    note: null,
    recentMinutes: 4,
    totalMinutes: 1576,
    markCount: 3,
    lastReadAt: '2026-08-05T09:31:22',
    readPercent: 3,
    categoryId: 1,
    categoryName: '英語 小説',
    hasPdf: true,
    pdfOriginalName: '01.The Secret Garden.pdf',
    pdfAvailable: true,
    hasCover: false,
    coverAvailable: false,
    version: 3,
    createdAt: '2026-04-02T12:47:45',
    updatedAt: '2026-08-05T09:31:22',
    // 2026-09-14 の追加: 公開範囲と自分の本棚（既定は移行済みの全体書籍・本棚には入っていない）
    scope: 'GLOBAL',
    ownerFamilyId: null,
    ownerFamilyLabel: null,
    inMyShelf: false,
    ...overrides
  }
}

/** 2 冊目: 実体 PDF が無い本（本棚では淡い色＋「PDF 未登録」の印が付く）。 */
function bookWithoutPdf(): ReadingBook {
  return book({
    bookId: 2,
    bookNo: 'ER-20260402-124746',
    title: 'Lonely Planet China',
    author: 'Lonely Planet',
    language: '中国語',
    categoryId: 2,
    categoryName: '英語 リーディング',
    pinned: false,
    currentPage: 0,
    totalPages: 1479,
    readPercent: 0,
    status: '未着手',
    markCount: 0,
    lastReadAt: null,
    hasPdf: true,
    pdfOriginalName: '02.Lonely Planet China.pdf',
    pdfAvailable: false
  })
}

/** 3 冊目: 未分類の本。 */
function bookUncategorized(): ReadingBook {
  return book({
    bookId: 3,
    bookNo: 'ER-20260402-124747',
    title: '中国語の絵本',
    author: '王小明',
    language: '中国語',
    categoryId: null,
    categoryName: null,
    pinned: false,
    status: '読了',
    currentPage: 20,
    totalPages: 20,
    readPercent: 100
  })
}

const CATEGORIES: ReadingCategory[] = [
  { categoryId: 1, name: '英語 小説', displayOrder: 1, description: null, bookCount: 1, ownerFamilyId: null },
  { categoryId: 2, name: '英語 リーディング', displayOrder: 2, description: null, bookCount: 1, ownerFamilyId: null }
]

function mark(overrides: Partial<ReadingMark> = {}): ReadingMark {
  return {
    markId: 11,
    bookId: 1,
    pageNo: 1,
    markType: 'vocabulary',
    targetText: 'plume',
    content: 'プルーム',
    color: '#7a4de8',
    positionX: 0.4,
    positionY: 0.3,
    width: 0.08,
    height: 0.03,
    drawingData: null,
    createdAt: '2026-08-05T09:30:00',
    ...overrides
  }
}

function record(overrides: Partial<ReadingRecord> = {}): ReadingRecord {
  return {
    recordId: 5,
    bookId: 1,
    bookTitle: 'The Secret Garden',
    readAt: '2026-08-05T09:31:22',
    pageStart: 1,
    pageEnd: 4,
    minutes: 24,
    markCount: 1,
    memo: null,
    ...overrides
  }
}

/* ---------- fetch の記録 ---------- */

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

function queryOf(url: string): URLSearchParams {
  return new URLSearchParams(url.split('?')[1] ?? '')
}

/** 最後に呼ばれた「本棚（GET /books）」のクエリ。 */
function lastShelfCall(fetchMock: ReturnType<typeof vi.fn>): Call {
  const calls = recorded(fetchMock).filter((call) => call.method === 'GET' && /\/reading\/books(\?|$)/.test(call.url))
  const last = calls.at(-1)
  if (!last) throw new Error('GET /books が呼ばれていません。')
  return last
}

/** 連鎖する非同期処理（PDF の読み込み・描画）が終わるまで待つ。 */
async function settle(times = 4): Promise<void> {
  for (let index = 0; index < times; index += 1) await flushPromises()
}

/* ---------- pdf.js の偽物 ---------- */

interface PdfStub {
  getDocument: ReturnType<typeof vi.fn>
  renderTextLayer: ReturnType<typeof vi.fn>
  pages: number[]
  renders: { pageNo: number; scale: number }[]
  workerSrc: () => string
}

function stubPdfjs(options: { numPages?: number; fail?: 'missing' | 'other' } = {}): PdfStub {
  const pages: number[] = []
  const renders: { pageNo: number; scale: number }[] = []
  const numPages = options.numPages ?? 3
  /**
   * 本物の pdf.js と同じく **私的なフィールド（#…）を持つクラス**で偽物を作る。
   * Vue の `ref` は深い反応性で Proxy に包むため、pdf.js の文書を `ref` で持つと
   * `getPage()` の中で「Cannot read private member #… from an object whose class did not
   * declare it」で落ちる。新画面が `shallowRef` を使っていることをこの偽物が守る。
   */
  class FakePdfPage {
    readonly #pageNo: number
    readonly #brand = 'fake-pdf'
    constructor(pageNo: number) {
      this.#pageNo = pageNo
    }
    getViewport({ scale }: { scale: number }): { width: number; height: number; scale: number } {
      if (this.#brand !== 'fake-pdf') throw new Error('private field')
      return { width: 600 * scale, height: 800 * scale, scale }
    }
    render(params: { viewport: { scale: number } }): { promise: Promise<void> } {
      renders.push({ pageNo: this.#pageNo, scale: params.viewport.scale })
      return { promise: Promise.resolve() }
    }
    async getTextContent(): Promise<{ items: unknown[] }> {
      return { items: [] }
    }
  }

  class FakePdfDocument {
    readonly #brand = 'fake-document'
    readonly numPages: number
    constructor(numPages: number) {
      this.numPages = numPages
    }
    async getPage(pageNo: number): Promise<FakePdfPage> {
      if (this.#brand !== 'fake-document') throw new Error('private field')
      pages.push(pageNo)
      return new FakePdfPage(pageNo)
    }
  }
  const getDocument = vi.fn(() => {
    const promise = options.fail
      ? Promise.reject(options.fail === 'missing'
        ? Object.assign(new Error('Unexpected server response (404) while retrieving PDF'), { name: 'MissingPDFException' })
        : new Error('壊れた PDF'))
      : Promise.resolve(new FakePdfDocument(numPages))
    return { promise }
  })
  const renderTextLayer = vi.fn(() => ({ promise: Promise.resolve() }))
  const lib = { GlobalWorkerOptions: { workerSrc: '' }, getDocument, renderTextLayer }
  ;(window as unknown as { pdfjsLib?: unknown }).pdfjsLib = lib
  return { getDocument, renderTextLayer, pages, renders, workerSrc: () => lib.GlobalWorkerOptions.workerSrc }
}

/* ---------- アニメーション設定（matchMedia）の偽物 ---------- */

function stubMatchMedia(reduce: boolean): void {
  window.matchMedia = vi.fn((query: string) => ({
    matches: reduce && query.includes('prefers-reduced-motion'),
    media: query,
    onchange: null,
    addEventListener: vi.fn(),
    removeEventListener: vi.fn(),
    addListener: vi.fn(),
    removeListener: vi.fn(),
    dispatchEvent: vi.fn()
  })) as unknown as typeof window.matchMedia
}

/* ---------- レイアウトの偽物（jsdom には無い） ---------- */

function rect(left: number, top: number, width: number, height: number): DOMRect {
  return {
    left, top, width, height, right: left + width, bottom: top + height, x: left, y: top,
    toJSON: () => ({})
  } as DOMRect
}

function stubLayoutApis(): void {
  Object.defineProperty(Range.prototype, 'getClientRects', {
    configurable: true, writable: true, value: () => [rect(250, 150, 120, 20)]
  })
  Object.defineProperty(Range.prototype, 'getBoundingClientRect', {
    configurable: true, writable: true, value: () => rect(250, 150, 120, 20)
  })
  const context = { setTransform: vi.fn(), clearRect: vi.fn() }
  HTMLCanvasElement.prototype.getContext = vi.fn(() => context) as unknown as typeof HTMLCanvasElement.prototype.getContext
}

/** 背表紙の inline 寸法（`--rds-spine-h` / `--rds-spine-w`）と書名の行数。 */
function spineSize(wrapper: VueWrapper, bookId: number): { height: number; width: number; lines: number } {
  const spine = wrapper.get(`[data-rd-spine="${bookId}"]`)
  const style = spine.attributes('style') ?? ''
  return {
    height: Number(/--rds-spine-h:\s*(\d+)px/.exec(style)?.[1] ?? '0'),
    width: Number(/--rds-spine-w:\s*(\d+)px/.exec(style)?.[1] ?? '0'),
    lines: Number(spine.attributes('data-rd-spine-lines') ?? '0')
  }
}


/* ---------- 画面のセットアップ ---------- */

interface SetupOptions {
  mode?: 'shelf' | 'viewer'
  bookId?: number
  /** 本棚モードのタブ（`?view=history` で開くかどうか）。 */
  view?: 'shelf' | 'history'
  books?: ReadingBook[]
  categories?: ReadingCategory[]
  detail?: (page: number) => ReadingBookDetail
  bookMarks?: ReadingMark[]
  /** 読書履歴（GET /records）が返す記録と総件数。 */
  records?: ReadingRecord[]
  recordTotal?: number
  pdfPages?: number
  pdfFail?: 'missing' | 'other'
  /** 引き当て（POST /lookup）を失敗させるか。 */
  lookupFail?: boolean
  /** 引き当ての戻り値を上書きする（辞書に無いときの案内など）。 */
  lookup?: Partial<ReadingLookupResult>
  /** 演出を有効にするか（既定は reduced motion＝演出なし）。 */
  reduceMotion?: boolean
}

/** afterEach で確実に unmount する（document の mouseup 購読とタイマーを残さない）。 */
let mounted: VueWrapper | null = null

async function setup(options: SetupOptions = {}) {
  /** 引き当て（POST /lookup）の呼び出し記録（この画面のテストで見る）。 */
  const lookups: { text: string; language: string }[] = []
  const pinia = createPinia()
  setActivePinia(pinia)
  // ルータの認証ガードを通す（生徒として開く）
  useAuthStore().login('STUDENT', 'テスト利用者')
  const router = createAppRouter()
  // 自分の本棚タブ（既定）で開く。閲覧の検証は reading-reader.spec.ts が持つ
  const query = '?view=self'
  await router.push(`/student/reading-reader${query}`)
  await router.isReady()

  // アニメーションは既定で切る（＝`prefers-reduced-motion: reduce`）。演出そのものを見る
  // テストだけ reduceMotion: false にすると、クリックで 420ms 待たずに開いて決定的になる。
  stubMatchMedia(options.reduceMotion ?? true)

  const shelf = options.books ?? [book(), bookWithoutPdf(), bookUncategorized()]
  const categories = options.categories ?? CATEGORIES
  const bookMarks = options.bookMarks ?? [
    mark(),
    mark({
      markId: 12, markType: 'highlight', targetText: 'intricate', content: null, color: '#f2d04f',
      positionX: 0.2, positionY: 0.5, width: 0.12, height: 0.03
    })
  ]
  const detailFor = options.detail ?? ((page: number) => ({
    book: book({ currentPage: page }),
    records: [record()],
    marks: page === 1 ? bookMarks : [],
    markedPages: [1, 4]
  }))
  const pdf = stubPdfjs({ numPages: options.pdfPages ?? 3, fail: options.pdfFail })

  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target = String(url)
    const method = (init?.method ?? 'GET').toUpperCase()
    const params = queryOf(target)
    // 分類マスタ
    if (method === 'GET' && target.includes('/reading/categories')) {
      return ok({ items: categories })
    }
    // 標記（この本）
    if (/\/reading\/books\/\d+\/marks/.test(target)) {
      if (method === 'POST') {
        const body = JSON.parse(String(init?.body ?? '{}')) as Partial<ReadingMark>
        return ok({
          mark: mark({ ...body, markId: 99, bookId: 1 }),
          book: book(),
          message: '標記を追加しました。'
        }, '標記を追加しました。')
      }
      return ok({
        items: bookMarks, totalElements: bookMarks.length, page: 1, size: 200, totalPages: 1,
        vocabularyCount: bookMarks.filter((item) => item.markType === 'vocabulary').length,
        highlightCount: bookMarks.filter((item) => item.markType === 'highlight').length,
        memoCount: bookMarks.filter((item) => item.markType === 'memo').length,
        underlineCount: 0
      })
    }
    if (method === 'DELETE' && /\/reading\/marks\/\d+/.test(target)) {
      return ok({ book: book({ markCount: 2 }), message: '標記を削除しました。' }, '標記を削除しました。')
    }
    // 語の意味・読みの引き当て（語彙／読み方）
    if (method === 'POST' && /\/reading\/lookup/.test(target)) {
      const body = JSON.parse(String(init?.body ?? '{}')) as { text?: string; language?: string }
      lookups.push({ text: String(body.text ?? ''), language: String(body.language ?? '') })
      if (options.lookupFail) {
        return new Response(JSON.stringify({
          success: false, code: 'INTERNAL_ERROR', message: '辞書に接続できませんでした。', data: null, timestamp: ''
        }), { status: 500, headers: { 'Content-Type': 'application/json' } })
      }
      const language = body.language === '中国語' ? '中国語' : '英語'
      return ok({
        text: String(body.text ?? ''),
        language,
        japanese: language === '英語' ? 'こんにちは' : null,
        chinese: language === '英語' ? '喂，你好' : '汉字',
        pinyin: language === '中国語' ? 'hàn zì' : null,
        explanation: language === '中国語' ? '漢字のこと' : null,
        source: 'EXCELAPI',
        cached: false,
        message: '',
        ...options.lookup
      })
    }
    // 読書記録（保存）
    if (method === 'POST' && /\/reading\/books\/\d+\/records/.test(target)) {
      return ok({
        record: record({ recordId: 9 }),
        book: book({ currentPage: 4, readPercent: 10 }),
        message: '読書記録を保存しました。'
      }, '読書記録を保存しました。')
    }
    if (method === 'DELETE' && /\/reading\/records\/\d+/.test(target)) {
      return ok({ count: 1, message: '読書記録を削除しました。' }, '読書記録を削除しました。')
    }
    if (method === 'GET' && /\/reading\/records/.test(target)) {
      // ページングは実際の API と同じく、要求された page / size をそのまま返す
      const page = Number(params.get('page') ?? '1')
      const size = Number(params.get('size') ?? '20')
      const records = options.records ?? [record()]
      const total = options.recordTotal ?? records.length
      return ok({
        items: records,
        totalElements: total,
        page,
        size,
        totalPages: Math.max(1, Math.ceil(total / size))
      })
    }
    // 1 冊（閲覧）
    if (method === 'GET' && /\/reading\/books\/\d+(\?|$)/.test(target)) {
      const requested = Number(target.match(/\/reading\/books\/(\d+)/)?.[1] ?? '1')
      const shelfBook = shelf.find((item) => item.bookId === requested) ?? book()
      const page = Number(params.get('markPage') ?? String(shelfBook.currentPage || 1))
      const detail = detailFor(page > 0 ? page : 1)
      return ok({ ...detail, book: { ...shelfBook, currentPage: page > 0 ? page : 1 } })
    }
    // 本棚
    if (method === 'GET' && /\/reading\/books(\?|$)/.test(target)) {
      const raw = params.get('categoryId')
      const filtered = raw === null
        ? shelf
        : shelf.filter((item) => (raw === '0' ? item.categoryId === null : item.categoryId === Number(raw)))
      return ok({
        items: filtered,
        totalElements: filtered.length,
        page: 1,
        size: 200,
        totalPages: 1,
        totals: {
          bookCount: shelf.length,
          readingCount: 2,
          finishedCount: 1,
          totalMinutes: 2000,
          markCount: 33,
          pdfCount: 2,
          uncategorizedCount: 1,
          myShelfCount: shelf.length
        }
      })
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(BookReaderView, { global: { plugins: [pinia, router] }, attachTo: document.body })
  mounted = wrapper
  await settle()
  return { wrapper, fetchMock, router, pdf, lookups }
}

beforeEach(() => {
  useToast().items.splice(0)
  window.sessionStorage.clear()
  // 棚の背景の選択（localStorage）が次のテストへ残らないようにする
  window.localStorage.clear()
  window.scrollTo = vi.fn()
  stubLayoutApis()
})

afterEach(() => {
  mounted?.unmount()
  mounted = null
  window.getSelection()?.removeAllRanges()
  delete (window as unknown as { pdfjsLib?: unknown }).pdfjsLib
  vi.unstubAllGlobals()
})

/* ---------- 本棚（分類ごとの背表紙） ---------- */

describe('書籍閲覧: 自分の本棚: 本棚', () => {
  it('分類タブを実在の分類から作り、切り替えると categoryId 付きで /books を呼ぶ', async () => {
    const { wrapper, fetchMock } = await setup()

    expect(wrapper.findAll('[data-rd-spine-book]').length).toBe(3)
    expect(lastShelfCall(fetchMock).url).not.toContain('categoryId')

    const tabs = wrapper.findAll('[data-rd-category]')
    expect(tabs.map((tab) => tab.text())).toEqual([
      'すべて', '英語 小説（1）', '英語 リーディング（1）', '未分類'
    ])
    expect(wrapper.get('[data-rd-category="all"]').classes()).toContain('is-active')
    // 分類のボタンは図書館タブと同じ丸いボタン（.rrd-tab）
    expect(wrapper.get('[data-rd-category="1"]').classes()).toContain('rrd-tab')

    await wrapper.get('[data-rd-category="2"]').trigger('click')
    await settle()
    expect(queryOf(lastShelfCall(fetchMock).url).get('categoryId')).toBe('2')
    expect(wrapper.findAll('[data-rd-spine-book]').length).toBe(1)
    expect(wrapper.get('[data-rd-spine-book="2"]').text()).toContain('Lonely Planet China')
    expect(wrapper.get('[data-rd-category="2"]').classes()).toContain('is-active')

    // 未分類は categoryId=0 で問い合わせる（既存の書籍閲覧と同じ契約）
    await wrapper.get('[data-rd-category="none"]').trigger('click')
    await settle()
    expect(queryOf(lastShelfCall(fetchMock).url).get('categoryId')).toBe('0')
    expect(wrapper.get('[data-rd-spine-book="3"]').text()).toContain('中国語の絵本')
  })

  it('「すべて」では分類ごとに棚（棚板）を分けて並べる', async () => {
    const { wrapper } = await setup()

    expect(wrapper.find('[data-rd-self-shelf]').exists()).toBe(true)
    const sections = wrapper.findAll('[data-rd-shelf-section]')
    expect(sections.map((section) => section.attributes('data-rd-shelf-section'))).toEqual(['cat-1', 'cat-2', 'none'])
    expect(wrapper.get('[data-rd-shelf-section="cat-1"]').text()).toContain('英語 小説')
    expect(wrapper.get('[data-rd-shelf-section="none"]').text()).toContain('未分類')
    // 棚板（木口）は棚ごとに 1 本
    expect(wrapper.findAll('.rds-case__plank').length).toBe(3)

    // 分類を選ぶと、その棚だけになる
    await wrapper.get('[data-rd-category="1"]').trigger('click')
    await settle()
    expect(wrapper.findAll('[data-rd-shelf-section]').length).toBe(1)
  })

  it('背表紙が冊数ぶん並び、書名と作者を出す（縦書きのスタイル）', async () => {
    const { wrapper } = await setup()

    const spines = wrapper.findAll('[data-rd-spine]')
    expect(spines.length).toBe(3)

    const first = wrapper.get('[data-rd-spine="1"]')
    expect(first.get('[data-rd-spine-title]').text()).toBe('The Secret Garden')
    expect(first.get('[data-rd-spine-author]').text()).toBe('Frances Hodgson Burnett')
    // 背表紙の寸法は書名から決まる（inline の CSS 変数で入る）
    expect(first.attributes('style')).toContain('--rds-spine-h:')
    expect(first.attributes('style')).toContain('--rds-spine-w:')

    // 同じ本はいつも同じ見た目（乱数を使っていない）
    const styleOf = wrapper.get('[data-rd-spine="2"]').attributes('style')
    const { wrapper: again } = await setup()
    expect(again.get('[data-rd-spine="2"]').attributes('style')).toBe(styleOf)

    // 縦書き（CSS は jsdom で適用されないので、スタイル定義そのものを確認する）。
    // 縦書きにするのは書名だけ（作者・印・状態バッジは横書き。参照したプロトタイプと同じ）。
    const css = readShelfCss()
    expect(css).toMatch(/\.rds-spine__title\s*\{[^}]*writing-mode:\s*vertical-rl/)
    // 縦書きにするのは書名だけ（背表紙そのものは横書き。作者・印・バッジを横書きにするため）
    expect(css).not.toMatch(/\.rds-spine\s*\{[^}]*writing-mode/)
    expect(css).toMatch(/\.rds-spine__title\s*\{/)
    // 色は tokens.css の変数だけを使う（生の色は書かない）
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    expect(css).not.toMatch(/\brgba?\(/)
    expect(css).not.toMatch(/\bhsla?\(/)
  })

  it('PDF 未登録の本は淡い色と印が付き、開くと読み方の案内を出す', async () => {
    const { wrapper, pdf } = await setup()

    const missing = wrapper.get('[data-rd-spine="2"]')
    expect(missing.classes()).toContain('is-no-pdf')
    expect(missing.get('[data-rd-spine-flag="missing"]').text()).toContain('PDF 未登録')
    expect(wrapper.get('[data-rd-spine="1"]').classes()).not.toContain('is-no-pdf')
    expect(wrapper.find('[data-rd-spine="1"] [data-rd-spine-flag]').exists()).toBe(false)

    await wrapper.get('[data-rd-spine-book="2"] [data-rd-open]').trigger('click')
    await settle(6)

    expect(wrapper.get('[data-rd-pdf-status]').text()).toContain('PDF 未登録')
    expect(wrapper.get('[data-rd-pdf-status]').text()).toContain('02.Lonely Planet China.pdf')
    const help = wrapper.get('[data-rd-pdf-help]').text()
    expect(help).toContain('書籍管理')
    expect(help).toContain('アップロード')
    // 実体が無いので PDF を取りに行かない
    expect(pdf.getDocument).not.toHaveBeenCalled()
  })

  it('自分の本棚が空なら、図書館から入れる案内を出す（決定 D3）', async () => {
    const { wrapper, router } = await setup({ books: [] })

    expect(wrapper.findAll('[data-rd-spine]').length).toBe(0)
    const empty = wrapper.get('[data-rd-my-shelf-empty]')
    expect(empty.text()).toContain('まだ本がありません。図書館から本を追加してください。')

    // 図書館への導線（そのまま図書館タブへ切り替わる）
    await empty.get('[data-rd-my-shelf-open-library]').trigger('click')
    await settle()
    await settle()
    expect(router.currentRoute.value.query.view).toBe('library')
    expect(wrapper.find('[data-rd-shelf]').exists()).toBe(true)
  })

  it('分類で絞った結果が空のときは「別の分類を選んでください」を出す', async () => {
    const { wrapper } = await setup({ books: [] })

    await wrapper.get('[data-rd-category="1"]').trigger('click')
    await settle()
    expect(wrapper.get('[data-rd-shelf-section="cat-1"]').find('[data-rd-shelf-empty]').text())
      .toContain('別の分類を選んでください')
  })

  /**
   * 【自分の本棚】タブは `shelf=MINE`（自分の本棚の行だけ）を読み、
   * 【図書館】タブは見える本すべてを読む（2026-09-14 の決定。本棚はアカウントごと）。
   */
  it('自分の本棚は shelf=MINE で読み、図書館は shelf を付けずに読む', async () => {
    const { wrapper, fetchMock, router } = await setup()

    const bookCalls = (): string[] => recorded(fetchMock)
      .filter((call) => call.method === 'GET' && /\/reading\/books(\?|$)/.test(call.url))
      .map((call) => call.url)

    expect(bookCalls().at(-1)).toContain('shelf=MINE')
    // 冊数は「自分の本棚 N 冊」として出す（本文のサマリ）
    expect(wrapper.get('[data-rd-shelf-count]').text()).toContain('3')

    // 図書館へ切り替えると shelf を付けずに読み直す
    await router.push('/student/reading-reader?view=library')
    await settle()
    await settle()
    expect(bookCalls().at(-1)).not.toContain('shelf=MINE')
  })

  it('書籍の登録・修正・削除・アップロードは置かない（閲覧専用）', async () => {
    const { wrapper } = await setup()

    expect(wrapper.find('[data-rd-add]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-edit]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-delete]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-upload-pdf]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-category-add]').exists()).toBe(false)
  })
})

/* ---------- 演出（ホバー・抜き出し） ---------- */

describe('書籍閲覧: 自分の本棚: 本棚の演出', () => {
  it('ホバーで背表紙が手前に出て（is-hover）、表紙パネルに書名・作者・難易度・言語が出る', async () => {
    const { wrapper } = await setup({ reduceMotion: false })

    const item = wrapper.get('[data-rd-spine-book="1"]')
    expect(item.classes()).not.toContain('is-hover')
    expect(wrapper.get('[data-rd-spine="1"]').classes()).not.toContain('is-hover')

    await item.trigger('mouseenter')
    expect(item.classes()).toContain('is-hover')
    expect(wrapper.get('[data-rd-spine="1"]').classes()).toContain('is-hover')

    // 情報は表紙パネル（[data-rd-tip]）の中に出す
    const tip = item.get('[data-rd-tip="1"]')
    expect(tip.classes()).toContain('rds-cover-panel')
    expect(tip.text()).toContain('The Secret Garden')
    expect(tip.text()).toContain('Frances Hodgson Burnett')
    expect(tip.text()).toContain('Elementary（初級）')
    expect(tip.text()).toContain('英語')

    await item.trigger('mouseleave')
    expect(item.classes()).not.toContain('is-hover')
  })

  it('棚を開くと本が左から順に並ぶ演出が付く（is-dealing と index ずらし）', async () => {
    const { wrapper } = await setup({ reduceMotion: false })

    const board = wrapper.get('[data-rd-shelf-section="cat-1"] .rds-case__board')
    expect(board.classes()).toContain('is-dealing')
    expect(wrapper.get('[data-rd-spine-book="1"]').attributes('style')).toContain('--rds-index: 0')
    expect(wrapper.get('[data-rd-shelf-section="none"] [data-rd-spine-book="3"]').attributes('style'))
      .toContain('--rds-index: 0')
  })

  it('クリックすると本が抜き出され（is-pulled）、演出のあとで閲覧に切り替わる', async () => {
    const { wrapper, router, pdf } = await setup({ reduceMotion: false })

    await wrapper.get('[data-rd-spine-book="1"] [data-rd-open]').trigger('click')
    await settle()

    // 演出のあいだはまだ本棚のままで、抜き出した本に is-pulled が付く
    expect(wrapper.get('[data-rd-spine-book="1"]').classes()).toContain('is-pulled')
    expect(wrapper.get('[data-rd-spine="1"]').classes()).toContain('is-pulled')
    expect(router.currentRoute.value.query.bookId).toBeUndefined()
    expect(pdf.getDocument).not.toHaveBeenCalled()

    // 演出（420ms）が終わると開く
    await new Promise((resolve) => setTimeout(resolve, 550))
    await settle(6)

    expect(router.currentRoute.value.query.bookId).toBe('1')
    expect(pdf.getDocument).toHaveBeenCalled()
    expect(wrapper.find('[data-rd-viewer]').exists()).toBe(true)
  })

  it('prefers-reduced-motion のときは演出用のクラスを付けず、すぐ開く', async () => {
    const { wrapper, router, pdf } = await setup({ reduceMotion: true })

    // 並ぶ演出は付かない
    expect(wrapper.get('[data-rd-shelf-section="cat-1"] .rds-case__board').classes()).not.toContain('is-dealing')

    // ホバーしても動きのクラスは付かない（ツールチップの is-tip は付く）
    const item = wrapper.get('[data-rd-spine-book="1"]')
    await item.trigger('mouseenter')
    expect(item.classes()).not.toContain('is-hover')
    expect(item.classes()).toContain('is-tip')
    expect(item.get('[data-rd-tip="1"]').text()).toContain('The Secret Garden')

    // クリックすると演出を挟まずに閲覧へ切り替わる
    await wrapper.get('[data-rd-spine-book="1"] [data-rd-open]').trigger('click')
    await settle(6)
    expect(wrapper.find('[data-rd-spine-book="1"]').exists()).toBe(false)
    expect(router.currentRoute.value.query.bookId).toBe('1')
    expect(pdf.getDocument).toHaveBeenCalled()
  })

  it('キーボードでも開ける（背表紙は button）', async () => {
    const { wrapper, router } = await setup()

    const spine = wrapper.get('[data-rd-spine="1"]')
    expect(spine.element.tagName).toBe('BUTTON')
    await spine.trigger('focus')
    await spine.trigger('click')
    await settle(6)

    expect(router.currentRoute.value.query.bookId).toBe('1')
  })
})

/* ---------- 背表紙の大きさと色 ---------- */

describe('書籍閲覧: 自分の本棚: 背表紙の大きさと色', () => {
  it('背表紙がひと回り大きい（高さ・幅を上げ、棚の段と文字も合わせる）', async () => {
    const { wrapper } = await setup()

    const sizes = wrapper.findAll('[data-rd-spine]').map((spine) => {
      const style = spine.attributes('style') ?? ''
      return {
        height: Number(/--rds-spine-h:\s*(\d+)px/.exec(style)?.[1] ?? '0'),
        width: Number(/--rds-spine-w:\s*(\d+)px/.exec(style)?.[1] ?? '0')
      }
    })
    expect(sizes.length).toBe(3)
    // 前は高さ 190〜234px・幅 44〜62px だった。一段上げる（6〜20 冊が 1 画面に収まる範囲で）
    expect(Math.min(...sizes.map((size) => size.height))).toBeGreaterThanOrEqual(216)
    expect(Math.max(...sizes.map((size) => size.height))).toBeGreaterThanOrEqual(260)
    expect(Math.min(...sizes.map((size) => size.width))).toBeGreaterThanOrEqual(56)
    // 本ごとに少しずつ違う（乱数ではなく書名のハッシュで決まる）
    expect(new Set(sizes.map((size) => size.height)).size).toBeGreaterThanOrEqual(2)

    const css = readShelfCss()
    // inline が無いときの既定値も一段上げる
    expect(css).toMatch(/\.rds-spine\s*\{[^}]*height:\s*var\(--rds-spine-h,\s*2\d{2}px\)/)
    expect(css).toMatch(/\.rds-spine\s*\{[^}]*width:\s*var\(--rds-spine-w,\s*\d{2,3}px\)/)
    // 棚の奥板の高さは「背表紙の高さの上限 ＋ 上下の余白 ＋ 枠線」で決める
    // （実写の写真のどこに本を座らせるかは下の「実写の棚」のテストが見る）
    const boardRule = /\.rds-case__board\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(boardRule).toMatch(/min-height:\s*calc\(var\(--rds-spine-max-h\)/)
    const caseRule = /\.rds-case\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    const spineMax = Number(/--rds-spine-max-h:\s*(\d+)px/.exec(caseRule)?.[1] ?? '0')
    // shelfLook.ts の SPINE_MAX_HEIGHT（320px）と一致していること（食い違うと本が棚からはみ出す）
    expect(spineMax).toBe(320)
    expect(css).toMatch(/\.rds-spine__title\s*\{[^}]*font-size:\s*var\(--fs-md\)/)
  })

  it('書名が長い本ほど背表紙が広い（書名の行数から幅を決める）', async () => {
    const { wrapper } = await setup({
      books: [
        book({ bookId: 1, title: 'The Secret Garden' }),
        book({ bookId: 2, title: '01.Harry Potter and the Sorcerers Stone' }),
        book({
          bookId: 3,
          title: 'The Absolutely True Diary of a Part-Time Indian (English Edition)'
        })
      ]
    })

    const short = spineSize(wrapper, 1)
    const long = spineSize(wrapper, 2)
    const longest = spineSize(wrapper, 3)

    // 幅は「余白（26px）＋ 行数 × 列の幅（19px）」以上（書名が入る幅を確保する）
    for (const size of [short, long, longest]) {
      expect(size.width).toBeGreaterThanOrEqual(26 + size.lines * 19)
      expect(size.width).toBeGreaterThanOrEqual(56)
      expect(size.width).toBeLessThanOrEqual(140)
    }
    // 長い書名のほうが広い（同系の薄い背表紙にならない）
    expect(long.width).toBeGreaterThan(short.width)
    expect(longest.width).toBeGreaterThanOrEqual(long.width)
    // 「01.Harry Potter and the Sorcerers Stone」は 4 行以内で収まる（切れない・省略されない）
    expect(long.lines).toBeGreaterThanOrEqual(2)
    expect(long.lines).toBeLessThanOrEqual(4)
    expect(long.width).toBeGreaterThanOrEqual(26 + long.lines * 19)
    // 文字が入ることを優先し、長い書名の本は高さも確保する
    expect(longest.height).toBeGreaterThanOrEqual(216)

    // CSS 側: 書名の枠（上下の余白）は、幅を決めるときに仮定した高さ（4rem ＝ 64px ぶんの
    // 余白 ＋ 左右の余白 28px）より狭くしない ＝ 実際の列数が計算より増えない
    const css = readShelfCss()
    const titleRule = /\.rds-spine__title\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    const titleTop = Number(/top:\s*(\d+)px/.exec(titleRule)?.[1] ?? '0')
    const titleBottom = Number(/bottom:\s*(\d+)px/.exec(titleRule)?.[1] ?? '0')
    expect(titleTop).toBeGreaterThan(0)
    expect(titleTop + titleBottom).toBeLessThanOrEqual(28 + 64)
    // 書名の幅は「背表紙の幅 − 26px」（shelfLook.ts が幅を決めるときの余白と同じ）
    expect(titleRule).toMatch(/max-width:\s*calc\(100% - 26px\)/)
    // 作者は書名の下端と自分の下端のあいだに収める（入りきらないぶんは省略する）
    const authorRule = /\.rds-spine__author\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(authorRule).toMatch(/max-height:\s*calc\(53px - 17px - var\(--sp-1\)\)/)
    expect(authorRule).toContain('text-overflow: ellipsis')
  })

  it('背表紙の色相が本ごとに散り、柄も複数ある（同系色に見えない）', async () => {
    // 色相の散り方を見るため、書名の違う 8 冊で確かめる
    const others = [
      'Harry Potter and the Sorcerers Stone',
      'A Little Princess',
      'The Wind in the Willows',
      'Peter Pan',
      'Alice in Wonderland',
      '動物農場',
      'The Jungle Book'
    ]
    const { wrapper } = await setup({
      books: [
        book(),
        ...others.map((title, index) => book({
          bookId: index + 10, title, author: '作者', coverAvailable: false
        }))
      ]
    })

    const spines = wrapper.findAll('[data-rd-spine]')
    expect(spines.length).toBe(8)

    const tones = spines.map((spine) => {
      const style = spine.attributes('style') ?? ''
      const tone = Number(/--rds-tone:\s*(\d+)/.exec(style)?.[1] ?? '-1')
      const light = Number(/--rds-light:\s*([\d.]+)/.exec(style)?.[1] ?? '-1')
      return { tone, light }
    })
    // 色相の番号は必ず入る（CSS 側で 16 色に割り当てる）
    expect(tones.every((item) => item.tone >= 0)).toBe(true)
    expect(new Set(tones.map((item) => item.tone)).size).toBeGreaterThanOrEqual(5)
    // 明度の揺らぎ（--rds-light）も本ごとに変える
    expect(new Set(tones.map((item) => item.light)).size).toBeGreaterThanOrEqual(2)
    // 色相はクラスでも分かる（`is-tone-N`）
    const toneClasses = new Set(spines.map((spine) =>
      spine.classes().find((name) => name.startsWith('is-tone-')) ?? ''))
    expect(toneClasses.size).toBeGreaterThanOrEqual(5)

    const css = readShelfCss()
    // 16 色ぶんの定義があり、6 種類以上の tokens.css の変数を組み合わせている（生の色は書かない）
    for (let tone = 0; tone < 16; tone += 1) {
      expect(css).toContain(`.rds-spine.is-tone-${tone},`)
    }
    const toneRules = css.slice(css.indexOf('/* ---------- 背表紙・表紙の色'), css.indexOf('/* 背表紙。高さ・幅は書名から決まる値'))
    const tokens = new Set(Array.from(toneRules.matchAll(/var\((--[a-z0-9-]+)\)/g)).map((match) => match[1]))
    expect(tokens.size).toBeGreaterThanOrEqual(6)
    expect(toneRules).toMatch(/color-mix\(/)

    // 柄（金のライン・縦縞・帯）も複数ある
    const patterns = new Set(spines.map((spine) =>
      spine.classes().find((name) => name.startsWith('is-pattern-')) ?? ''))
    expect(patterns.size).toBeGreaterThanOrEqual(2)
    for (let pattern = 0; pattern < 3; pattern += 1) {
      expect(css).toContain(`.rds-spine.is-pattern-${pattern}::after`)
    }
  })
})

/* ---------- 背表紙の意匠（実写の棚に合わせた飾り） ---------- */

describe('書籍閲覧: 自分の本棚: 背表紙の意匠', () => {
  /** 背表紙の中の飾り（布目・内枠・リブ・記号・光沢）の数と中身。 */
  function decorations(wrapper: VueWrapper, bookId: number) {
    const spine = wrapper.get(`[data-rd-spine="${bookId}"]`)
    return {
      grain: spine.findAll('.rds-spine__grain').length,
      bevel: spine.findAll('.rds-spine__bevel').length,
      ribs: spine.findAll('.rds-spine__rib').length,
      gloss: spine.findAll('.rds-spine__gloss').length,
      mark: spine.find('.rds-spine__mark').exists() ? spine.get('.rds-spine__mark').text() : '',
      badge: spine.find('.rds-spine__badge').exists() ? spine.get('.rds-spine__badge').text() : '',
      hidden: spine.findAll('[aria-hidden="true"]').length
    }
  }

  it('本ごとに少し傾いて立つ（--rds-lean。底を支点にするので足元は浮かない）', async () => {
    // 傾きの散り方を見るため、書名の違う 8 冊で確かめる
    const titles = [
      'The Secret Garden', 'Harry Potter and the Sorcerers Stone', 'A Little Princess',
      'The Wind in the Willows', 'Peter Pan', 'Alice in Wonderland', '動物農場', 'The Jungle Book'
    ]
    const { wrapper } = await setup({
      books: titles.map((title, index) => book({ bookId: index + 1, title, author: '作者' }))
    })

    const leans = wrapper.findAll('[data-rd-spine]').map((spine) =>
      Number(/--rds-lean:\s*(-?[\d.]+)deg/.exec(spine.attributes('style') ?? '')?.[1] ?? 'NaN'))
    expect(leans.every((lean) => Number.isFinite(lean))).toBe(true)
    // わずかに傾ける（大きく傾けると棚が乱れて見える）
    expect(leans.every((lean) => Math.abs(lean) <= 0.6)).toBe(true)
    // 同じ傾きばかりにならない（本ごとに違う）
    expect(new Set(leans).size).toBeGreaterThanOrEqual(3)
    // 傾き 0 の本もある（全部が傾いていると不自然）
    expect(leans.some((lean) => lean === 0)).toBe(true)

    // 同じ本はいつも同じ傾き（乱数を使っていない）
    const style = wrapper.get('[data-rd-spine="2"]').attributes('style')
    const { wrapper: again } = await setup({
      books: titles.map((title, index) => book({ bookId: index + 1, title, author: '作者' }))
    })
    expect(again.get('[data-rd-spine="2"]').attributes('style')).toBe(style)

    const css = readShelfCss()
    const spine = /\.rds-spine\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(spine).toMatch(/transform:\s*rotateZ\(var\(--rds-lean,\s*0deg\)\)/)
    // 底を支点にする（傾いても足元が棚から浮かない）
    expect(spine).toMatch(/transform-origin:\s*50% 100%/)
  })

  it('背表紙に布目・内枠・リブ・飾りの記号・光沢が入る（プロトタイプの意匠）', async () => {
    const { wrapper } = await setup()

    for (const bookId of [1, 2, 3]) {
      const item = decorations(wrapper, bookId)
      expect(item.grain).toBe(1)
      expect(item.bevel).toBe(1)
      // 上下 2 本のリブ
      expect(item.ribs).toBe(2)
      expect(item.gloss).toBe(1)
      // 飾りの記号は必ず 1 文字（読み上げには出さない）
      expect(SHELF_MARKS).toContain(item.mark)
      expect(item.hidden).toBeGreaterThanOrEqual(6)
    }

    // 記号は書名で決まる（同じ本はいつも同じ・本ごとに散る）
    const marks = [1, 2, 3].map((bookId) => decorations(wrapper, bookId).mark)
    const { wrapper: again } = await setup()
    expect([1, 2, 3].map((bookId) => decorations(again, bookId).mark)).toEqual(marks)

    const css = readShelfCss()
    // 布目（細かい縞）と左右の丸み
    expect(css).toMatch(/\.rds-spine__grain\s*\{[^}]*repeating-linear-gradient/)
    // 内枠（左＝光・右＝影）とリブ（上下の細い帯）
    expect(css).toMatch(/\.rds-spine__bevel\s*\{[^}]*border-left/)
    expect(css).toMatch(/\.rds-spine__bevel\s*\{[^}]*border-right/)
    expect(css).toMatch(/\.rds-spine__rib\s*\{[^}]*border-top/)
    expect(css).toMatch(/\.rds-spine__rib--head\s*\{[^}]*top:/)
    expect(css).toMatch(/\.rds-spine__rib--foot\s*\{[^}]*bottom:/)
    // 記号は金（tokens.css の変数を混ぜた色）で、書名の下に置く
    expect(css).toMatch(/\.rds-spine__mark\s*\{[^}]*--rds-spine-ink/)
    expect(css).toMatch(/--rds-spine-ink:\s*color-mix\(/)
    // 光沢（ホバーで左から右へ抜ける）
    expect(css).toMatch(/\.rds-spine__gloss::before\s*\{[^}]*linear-gradient/)
    expect(css).toMatch(/\.rds-book\.is-hover \.rds-spine__gloss::before[\s\S]{0,160}left:\s*118%/)
    // 生の色は書かない（飾りも tokens.css の変数だけで作る）
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    expect(css).not.toMatch(/\brgba?\(/)
  })

  it('ホバーで本が手前に起き上がる（浮き上がり・角度・光沢）', async () => {
    const { wrapper } = await setup({ reduceMotion: false })

    const item = wrapper.get('[data-rd-spine-book="1"]')
    expect(item.findAll('.rds-book__shadow').length).toBe(1)
    await item.trigger('mouseenter')

    const css = readShelfCss()
    const hover = /\.rds-book\.is-hover \.rds-spine,\s*\.rds-spine\.is-hover,\s*\.rds-spine:focus-visible\s*\{[^}]*\}/
      .exec(css)?.[0] ?? ''
    // プロトタイプと同じ動き（持ち上げて手前に起こし、傾きは 0 に戻す）
    expect(hover).toContain('translateY(-17px)')
    expect(hover).toContain('translateZ(25px)')
    expect(hover).toContain('rotateY(-10deg)')
    expect(hover).toContain('rotateZ(0deg)')
    expect(hover).toMatch(/filter:\s*saturate\(1\.08\)/)
    expect(hover).toContain('box-shadow')
    // 手前に起こすための視点（perspective）は本ごとに持つ（棚全体の重なり順を変えない）
    const bookRule = /\.rds-book\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(bookRule).toContain('perspective: 1500px')
    expect(bookRule).toMatch(/z-index:\s*1/)

    await item.trigger('mouseleave')
    expect(item.classes()).not.toContain('is-hover')
  })

  it('本の足元に接地の影が落ちる（ホバーで濃くなる）', async () => {
    const { wrapper } = await setup()

    // 本ごとに 1 つ。飾りなので読み上げには出さない
    expect(wrapper.findAll('.rds-book__shadow').length).toBe(3)
    expect(wrapper.get('[data-rd-spine-book="1"] .rds-book__shadow').attributes('aria-hidden')).toBe('true')

    const css = readShelfCss()
    const shadow = /\.rds-book__shadow\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(shadow).toContain('radial-gradient')
    expect(shadow).toContain('blur(')
    expect(shadow).toMatch(/bottom:\s*-4px/)
    // 背表紙より後ろに描く（DOM の順で先に置く）
    expect(wrapper.get('[data-rd-spine-book="1"]').element.firstElementChild?.className)
      .toContain('rds-book__shadow')
    // ホバー・抜き出しで濃く、少し横へ伸びる
    expect(css).toMatch(/\.rds-book\.is-hover \.rds-book__shadow,[\s\S]{0,80}\.rds-book\.is-pulled \.rds-book__shadow\s*\{[^}]*opacity:\s*0\.95/)
  })

  it('いま読んでいる本（読書中）だけに状態バッジが出る', async () => {
    const { wrapper } = await setup()

    // 1 冊目は読書中（既定）、2 冊目は未着手、3 冊目は読了
    expect(decorations(wrapper, 1).badge).toBe('読書中')
    expect(decorations(wrapper, 2).badge).toBe('')
    expect(decorations(wrapper, 3).badge).toBe('')
    expect(wrapper.findAll('.rds-spine__badge').length).toBe(1)

    const css = readShelfCss()
    const badge = /\.rds-spine__badge\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    // 背表紙の右上（プロトタイプと同じ位置）。書名の枠（上端 26px）に掛からない高さに置く
    const badgeTop = Number(/top:\s*(\d+)px/.exec(badge)?.[1] ?? '99')
    expect(badgeTop).toBeLessThan(26)
    expect(badge).toMatch(/right:\s*-8px/)
    expect(badge).toContain('--rds-spine-ink')
  })
})

/* ---------- 棚の背景の切替 ---------- */

describe('書籍閲覧: 自分の本棚: 棚の背景の切替', () => {
  it('見出し行に背景の切替が出る（6 つ。既定は 0 番）', async () => {
    const { wrapper } = await setup()

    const options = wrapper.findAll('[data-rd-shelf-bg]')
    expect(SHELF_BG_COUNT).toBe(6)
    expect(options.length).toBe(SHELF_BG_COUNT)
    expect(options.map((option) => option.attributes('data-rd-shelf-bg'))).toEqual(['0', '1', '2', '3', '4', '5'])
    // 「自分の本棚」のカードの見出し行にある
    const picker = wrapper.get('[data-rd-shelf-bg-picker]')
    expect(picker.element.closest('.card__header')).not.toBe(null)
    expect(picker.attributes('aria-label')).toBe('棚の背景')
    // 読み上げ・マウスの説明（何番の背景かが分かる）
    expect(options.map((option) => option.attributes('aria-label'))).toEqual([
      '棚の背景（既定）', '棚の背景 1', '棚の背景 2', '棚の背景 3', '棚の背景 4', '棚の背景 5'
    ])
    expect(options[3].attributes('title')).toBe('棚の背景 3')

    // 既定は 0 番。棚（分類ごと）にもそのクラスが付く
    expect(options[0].classes()).toContain('is-active')
    expect(options[0].attributes('aria-pressed')).toBe('true')
    expect(options[1].classes()).not.toContain('is-active')
    expect(wrapper.get('[data-rd-shelf-section="cat-1"]').classes()).toContain('rds-bg-0')
    // 見本には写真そのものを使う（2 番＝ shelf-bg-3.jpg）
    expect(options[2].attributes('style')).toContain('shelf-bg-3')
  })

  it('選ぶとすぐ切り替わり、端末に覚えて次に開いたときも同じ', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-rd-shelf-bg="2"]').trigger('click')

    // 再読み込みなしで、棚（すべての段）の背景が変わる
    expect(wrapper.get('[data-rd-shelf-section="cat-1"]').classes()).toContain('rds-bg-2')
    expect(wrapper.get('[data-rd-shelf-section="none"]').classes()).toContain('rds-bg-2')
    // 敷く写真も差し替える（棚ごとの inline の CSS 変数）
    expect(wrapper.get('[data-rd-shelf-section="cat-1"]').attributes('style')).toContain('shelf-bg-3')
    // 選んでいるものが分かる
    expect(wrapper.get('[data-rd-shelf-bg="2"]').classes()).toContain('is-active')
    expect(wrapper.get('[data-rd-shelf-bg="2"]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-rd-shelf-bg="0"]').classes()).not.toContain('is-active')
    // 端末に覚える（サーバには保存しない）
    expect(window.localStorage.getItem(SHELF_BG_STORAGE_KEY)).toBe('2')

    // 開き直しても前の選択のまま
    const { wrapper: again } = await setup()
    expect(again.get('[data-rd-shelf-section="cat-1"]').classes()).toContain('rds-bg-2')
    expect(again.get('[data-rd-shelf-bg="2"]').classes()).toContain('is-active')
  })

  it('壊れた値・知らない値は既定（0 番）にする', () => {
    expect(normalizeShelfBg(0)).toBe(0)
    expect(normalizeShelfBg(4)).toBe(4)
    expect(normalizeShelfBg('3')).toBe(3)
    expect(normalizeShelfBg(1.7)).toBe(1)
    expect(normalizeShelfBg(5)).toBe(5)
    // 範囲の外・数でない値・空
    expect(normalizeShelfBg(6)).toBe(0)
    expect(normalizeShelfBg(-1)).toBe(0)
    expect(normalizeShelfBg('abc')).toBe(0)
    expect(normalizeShelfBg(null)).toBe(0)
    expect(normalizeShelfBg(undefined)).toBe(0)
    // 見出しと URL も同じ丸め方をする
    expect(shelfBgLabel(2)).toBe('棚の背景 2')
    expect(shelfBgLabel(9)).toBe('棚の背景（既定）')
    expect(shelfBgUrl(9)).toBe(shelfBgUrl(0))
  })

  it('背景ごとに本を座らせる位置と左右の余白を持つ（写真の実測に合わせる）', () => {
    const css = readShelfCss()
    // 6 つぶんの規則がある（背景ごとに座り位置と左右の余白を決める）
    for (let index = 0; index < SHELF_BG_COUNT; index += 1) {
      expect(css).toContain(`.rds-case.rds-bg-${index} {`)
    }
    // 敷く写真は CSS 変数で差し替える（既定は 0 番の写真）
    expect(css).toMatch(/var\(--rds-shelf-photo,\s*url\('\.\.\/\.\.\/assets\/reading\/shelf-bg-1\.jpg'\)\)/)
    // 左右の余白は割合（%）で持つ ＝ 横に引き伸ばしても写真の側板と同じ割合で動く
    expect(css).toMatch(/\.rds-case__board\s*\{[^}]*padding-inline:\s*var\(--rds-book-inset\)/)

    // 写真ごとの実測（`tmp/tools/import-shelf-bg.mjs` が測って出す）:
    // 明るい棚板の帯（行の明るさ）と、両端の縦の側板の厚み（列の明るさ）
    const measured = [
      { band: [0.769, 0.994], panel: [0.0216, 0.0216] },
      { band: [0.809, 0.996], panel: [0.0244, 0.0239] },
      { band: [0.849, 0.852], panel: [0.0184, 0.0198] },
      { band: [0.789, 0.994], panel: [0.0193, 0.0203] },
      { band: [0.838, 0.844], panel: [0.0189, 0.0189] },
      { band: [0.798, 0.852], panel: [0.0193, 0.0216] }
    ]
    expect(measured.length).toBe(SHELF_BG_COUNT)
    for (let index = 0; index < SHELF_BG_COUNT; index += 1) {
      const seat = seatOf(css, index)
      // 本の底（棚の上端から）＝ 余白 24px ＋ 背表紙の高さの上限 320px ＝ 344px
      // 棚の高さ ＝ 奥板（320 + 24 + S + 2）＋ 棚板 22 ＝ 368 + S
      const feet = 344 / (368 + seat)
      expect(feet, `背景 ${index} の本の底`).toBeGreaterThanOrEqual(measured[index].band[0])
      expect(feet, `背景 ${index} の本の底`).toBeLessThanOrEqual(measured[index].band[1])

      // 左右の余白は、その写真の側板の厚みより広いこと（本が側板に重ならない）
      const inset = insetOf(css, index)
      expect(inset, `背景 ${index} の左の余白`).toBeGreaterThan(measured[index].panel[0] * 100)
      expect(inset, `背景 ${index} の右の余白`).toBeGreaterThan(measured[index].panel[1] * 100)
      // 余白が広すぎない（本が棚の中央に寄りすぎない）
      expect(inset, `背景 ${index} の余白`).toBeLessThan(6)
    }

    // 画像の実体がある（6 枚。すべて 400KB 以内）。前の版の写真・テクスチャは残っていない
    for (let index = 1; index <= SHELF_BG_COUNT; index += 1) {
      const file = shelfBackgroundPath(`shelf-bg-${index}.jpg`)
      expect(existsSync(file), `shelf-bg-${index}.jpg`).toBe(true)
      expect(statSync(file).size, `shelf-bg-${index}.jpg`).toBeLessThanOrEqual(400 * 1024)
    }
    expect(existsSync(shelfBackgroundPath('shelf-hd.jpg'))).toBe(false)
  })
})

/* ---------- 本棚の作り（タブ・木の棚・重なり順） ---------- */

describe('書籍閲覧: 自分の本棚: 本棚の作り', () => {
  it('タブ行に縦スクロールバーを出さない（スクロール領域にしない）', async () => {
    const { wrapper } = await setup()

    // 自分の本棚の分類タブは図書館タブと同じ .rrd-tabs（overflow-x: auto を付けない）。
    // スクロール領域にすると CSS の仕様で overflow-y も auto になり、
    // 親の flex で縮んだときに縦スクロールバーが出る
    const row = /\.rrd-tabs\s*\{[^}]*\}/.exec(readReaderCss())?.[0] ?? ''
    expect(row).not.toContain('overflow-x: auto')
    expect(row).toContain('flex-wrap: wrap')
    expect(wrapper.findAll('[data-rd-category]').length).toBeGreaterThan(1)
  })

  it('棚が木の質感になる（奥板は実写の棚の写真・棚板と側板は木目。色は tokens.css の変数だけ）', async () => {
    const { wrapper } = await setup()

    // 棚ごとに「奥板 ＋ 棚板 ＋ 側板（擬似要素）」が揃う
    expect(wrapper.findAll('.rds-case__unit').length).toBe(3)
    expect(wrapper.findAll('.rds-case__plank').length).toBe(3)
    expect(wrapper.findAll('.rds-case__board').length).toBe(3)

    const css = readShelfCss()
    // 木の色は tokens.css の変数だけを color-mix() で混ぜて作る。
    // 芯になる茶色は --warning-600（茶）に --danger-600（赤）を足して作り、明度は面の色で上げる
    const caseRule = /\.rds-case\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(caseRule).toMatch(/--rds-wood-tone:\s*color-mix\(in srgb, var\(--warning-600\) 88%, var\(--danger-600\)\)/)
    expect(caseRule).toMatch(/--rds-wood:\s*color-mix\(in srgb, var\(--rds-wood-tone\) 74%, var\(--color-surface\)\)/)
    expect(caseRule).toMatch(/--rds-wood-dark:\s*color-mix\(in srgb, var\(--rds-wood-tone\) 40%, var\(--neutral-900\)\)/)

    // 奥板: 実写の棚の写真を 1 枚、棚いっぱいに引き伸ばす（プロトタイプと同じ）。
    // 写真は手前の棚板（木口）まで含むので、そのぶん下へ伸ばして 1 枚で棚全体を覆う。
    const board = /\.rds-case__board::before\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(board).toContain("var(--rds-shelf-photo, url('../../assets/reading/shelf-bg-1.jpg'))")
    expect(board).toMatch(/background-size:\s*100% 100%, 100% 100%/)
    expect(board).toMatch(/background-repeat:\s*no-repeat, no-repeat/)
    expect(board).toMatch(/inset:\s*-1px -1px calc\(-1px - var\(--rds-plank-h\)\)/)
    expect(board).toContain('linear-gradient')
    // 画像の実体がある（高解像度の写真。古い手続き生成のテクスチャは使わない）
    expect(existsSync(shelfBackgroundPath('shelf-bg-1.jpg'))).toBe(true)
    expect(css).not.toContain('wood-shelf')
    expect(existsSync(legacyShelfImagePath())).toBe(false)

    // 棚板: 厚み（--rds-plank-h ＝ 22px）・面取り・影。写真の上に重ねるので木を塗らない
    const plank = /\.rds-case__plank\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(plank).toContain('repeating-linear-gradient')
    expect(plank).toMatch(/height:\s*var\(--rds-plank-h\)/)
    expect(caseRule).toMatch(/--rds-plank-h:\s*22px/)
    expect(plank).toContain('box-shadow')
    expect(css).toMatch(/\.rds-case__plank::before\s*\{[^}]*--rds-shelf-light/)
    // 側板: 左右を擬似要素で出し、木目を縦に流す（写真の上に半透明で重ねる）
    const unit = /\.rds-case__unit::before,\s*\.rds-case__unit::after\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(unit).toContain('repeating-linear-gradient')
    expect(unit).toMatch(/width:\s*12px/)
    expect(unit).not.toContain('background-color')
    // 暗色テーマでは写真を落ち着かせる
    expect(css).toMatch(/html\[data-theme='dark'\] \.rds-case__board::before/)
    // 生の色は書かない
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    expect(css).not.toMatch(/\brgba?\(/)
    expect(css).not.toMatch(/\bhsla?\(/)
  })

  it('本の底が実写の写真の「明るい棚板」に乗る（本の底＝棚の上から約 87%）', async () => {
    const css = readShelfCss()
    const caseRule = /\.rds-case\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    const seat = Number(/--rds-shelf-seat:\s*(\d+)px/.exec(caseRule)?.[1] ?? '0')
    // 余白はデザインシステムの間隔トークン（--sp-6 ＝ 24px）で持つ
    const padToken = /--rds-shelf-pad:\s*var\((--sp-\d+)\)/.exec(caseRule)?.[1] ?? ''
    const pad = padToken === '' ? Number(/--rds-shelf-pad:\s*(\d+)px/.exec(caseRule)?.[1] ?? '0')
      : spacingToken(padToken)
    const plank = Number(/--rds-plank-h:\s*(\d+)px/.exec(caseRule)?.[1] ?? '0')
    const spineMax = Number(/--rds-spine-max-h:\s*(\d+)px/.exec(caseRule)?.[1] ?? '0')

    // 奥板（枠線 2px ＋ 余白 ＋ 背表紙の上限）＋ 棚板 ＝ 棚の高さ
    expect(padToken).toBe('--sp-6')
    expect(pad).toBeGreaterThanOrEqual(24)
    expect(seat).toBeGreaterThan(0)
    const shelfHeight = spineMax + pad + seat + 2 + plank
    // いちばん高い本の底 ＝ 奥板の上端 ＋ 上の余白 ＋ 背表紙の高さ
    //（奥板の高さの下限が「背表紙の上限 ＋ 上下の余白 ＋ 枠線」なので、この位置で止まる）
    const feet = spineMax + pad
    const feetRatio = feet / shelfHeight
    // 写真の明るい棚板は上から 80〜90%（実測。いちばん明るい行が 87%）。そこに乗っていること
    expect(feetRatio).toBeGreaterThan(0.8)
    expect(feetRatio).toBeLessThan(0.9)
    // プロトタイプ（360px の棚に bottom:48px ＝ 13%）と同じ割合に近い
    expect(1 - feetRatio).toBeGreaterThan(0.11)
    expect(1 - feetRatio).toBeLessThan(0.15)
  })

  it('ホバー中の本が棚の中で最上位になる（隣の本の下に隠れない）', async () => {
    const { wrapper } = await setup({ reduceMotion: false })

    const hovered = wrapper.get('[data-rd-spine-book="1"]')
    const neighbour = wrapper.get('[data-rd-spine-book="2"]')
    expect(/z-index/.test(neighbour.attributes('style') ?? '')).toBe(false)

    await hovered.trigger('mouseenter')
    const hoveredZ = Number(/z-index:\s*(\d+)/.exec(hovered.attributes('style') ?? '')?.[1] ?? '0')
    // ホバー中の本は棚の中で最上位（20〜30）
    expect(hoveredZ).toBeGreaterThanOrEqual(20)
    expect(hoveredZ).toBeLessThanOrEqual(31)
    // 隣の本（CSS では z-index 1）より必ず上
    const css = readShelfCss()
    const book = /\.rds-book\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(book).toMatch(/z-index:\s*1/)
    expect(hoveredZ).toBeGreaterThan(1)
    // 表紙パネル（表紙＋情報）も棚の中で上位
    const panel = /\.rds-cover-panel\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    const preview = /\.rds-preview\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(panel).toMatch(/z-index:\s*2[0-9]/)
    expect(preview).toMatch(/z-index:\s*2[0-9]/)
    expect(css).toMatch(/\.rds-book\.is-hover,\s*\.rds-book\.is-tip\s*\{[^}]*z-index:\s*30/)

    await hovered.trigger('mouseleave')
    expect(/z-index/.test(hovered.attributes('style') ?? '')).toBe(false)
  })

  it('棚の左端の本は表紙パネルを右へ出す（左メニューに隠れない）', async () => {
    const { wrapper } = await setup({
      reduceMotion: false,
      books: [1, 2, 3, 4, 5].map((bookId) => book({ bookId, title: `Book ${bookId}` }))
    })

    // 左端の本は右へ出す（サイドメニューは 248px ある）
    expect(wrapper.get('[data-rd-spine-book="1"]').classes()).toContain('is-tip-right')
    expect(wrapper.get('[data-rd-spine-book="3"]').classes()).not.toContain('is-tip-right')
    // 右端の本は左へ出す
    expect(wrapper.get('[data-rd-spine-book="5"]').classes()).toContain('is-preview-left')

    const css = readShelfCss()
    expect(css).toMatch(/\.rds-book\.is-tip-right \.rds-cover-panel\s*\{[^}]*left:\s*calc\(100% \+ var\(--sp-3\)\)/)
    expect(css).toMatch(/\.rds-book\.is-preview-left \.rds-cover-panel\s*\{[^}]*right:\s*calc\(100% \+ var\(--sp-3\)\)/)
    // サイドメニュー（z-index 300）より上げるのではなく、位置で避ける
    const panel = /\.rds-cover-panel\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    const panelZ = Number(/z-index:\s*(\d+)/.exec(panel)?.[1] ?? '0')
    expect(panelZ).toBeGreaterThanOrEqual(20)
    expect(panelZ).toBeLessThan(300)
  })
})

/* ---------- ホバーで出る表紙パネル（情報も 1 枚にまとめる）と進捗 ---------- */

describe('書籍閲覧: 自分の本棚: ホバーの表紙と進捗', () => {
  it('ホバーの情報は表紙パネル 1 枚にまとめる（ツールチップを別に出さない）', async () => {
    const { wrapper } = await setup()

    // ホバーするまでは出さない
    expect(wrapper.find('[data-rd-tip]').exists()).toBe(false)
    expect(wrapper.findAll('.rds-tip').length).toBe(0)

    await wrapper.get('[data-rd-spine-book="1"]').trigger('mouseenter')
    const panel = wrapper.get('[data-rd-tip="1"]')
    // 表紙と情報が同じ 1 枚の中にある
    expect(panel.classes()).toContain('rds-cover-panel')
    expect(panel.find('[data-rd-cover-preview]').exists()).toBe(true)
    expect(panel.find('[data-rd-progress]').exists()).toBe(true)
    // 旧ツールチップの内容（難易度・言語・状態・進捗・標記）
    expect(panel.text()).toContain('Elementary（初級）')
    expect(panel.text()).toContain('英語')
    expect(panel.text()).toContain('読書中')
    expect(panel.get('[data-rd-progress]').text()).toContain('進捗 3%')
    expect(panel.get('[data-rd-progress]').text()).toContain('1 / 40 ページ')
    expect(panel.text()).toContain('標記 3 件')

    // PDF 未登録の本は、その案内と元ファイル名も同じパネルに出す
    await wrapper.get('[data-rd-spine-book="2"]').trigger('mouseenter')
    const missing = wrapper.get('[data-rd-tip="2"]')
    expect(missing.text()).toContain('PDF 未登録（02.Lonely Planet China.pdf）')
    // パネルはホバーした 1 冊だけ（別の本のものは出さない）
    expect(wrapper.findAll('[data-rd-tip]').length).toBe(1)

    // ツールチップの浮層は CSS にも残さない
    const css = readShelfCss()
    expect(css).not.toContain('rds-tip')
    expect(css).toContain('.rds-cover-panel__badges')
    expect(css).toContain('.rds-cover-panel__progress')
    expect(css).toContain('.rds-cover-panel__note')
  })

  it('ホバーで出す表紙パネルは暗いガラスの面に 165×236 の表紙と書名・作者を出す', async () => {
    const { wrapper } = await setup({
      books: [book({ coverAvailable: true, hasCover: true })]
    })

    await wrapper.get('[data-rd-spine-book="1"]').trigger('mouseenter')
    const panel = wrapper.get('.rds-cover-panel')
    const frame = panel.get('.rds-cover-panel__frame')
    // 表紙は枠の中（枠が大きさを持つ。表紙は枠いっぱい）
    expect(frame.find('img[data-rd-cover-preview]').exists()).toBe(true)
    expect(panel.get('.rds-cover-panel__meta').text()).toContain('The Secret Garden')
    expect(panel.get('.rds-cover-panel__meta').text()).toContain('Frances Hodgson Burnett')

    const css = readShelfCss()
    const frameRule = /\.rds-cover-panel__frame\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(frameRule).toMatch(/width:\s*165px/)
    expect(frameRule).toMatch(/height:\s*236px/)
    // 表紙の上を光が通る（ホバーで 1 回）
    expect(css).toMatch(/@keyframes rds-cover-gloss/)
    // パネルは暗いガラス（tokens.css の変数だけを混ぜた半透明の黒）
    const panelRule = /\.rds-cover-panel\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(panelRule).toMatch(/--neutral-900/)
    expect(panelRule).toContain('backdrop-filter')
    // 狭い画面では文字を隠して表紙を小さくする
    const narrow = /@media \(max-width: 1100px\)[\s\S]*?\.rds-cover-panel__frame\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(narrow).toMatch(/width:\s*116px/)
    expect(css).toMatch(/@media \(max-width: 1100px\)[\s\S]*?\.rds-cover-panel__meta\s*\{[^}]*display:\s*none/)
  })

  it('表紙がある本はホバーで img[data-rd-cover-preview] が背表紙の近くに出る', async () => {
    const { wrapper } = await setup({
      reduceMotion: false,
      books: [book({ coverAvailable: true, hasCover: true })]
    })

    const item = wrapper.get('[data-rd-spine-book="1"]')
    // ホバーするまでは描かない（表紙画像を先に取りに行かない）
    expect(wrapper.find('[data-rd-cover-preview]').exists()).toBe(false)

    await item.trigger('mouseenter')
    const preview = item.get('img[data-rd-cover-preview]')
    expect(preview.attributes('src')).toBe('/api/user/reading/books/1/cover?v=3')
    expect(preview.attributes('alt')).toContain('The Secret Garden')
    // 表紙はパネルの枠の中に出す
    expect(item.get('.rds-cover-panel__frame').find('[data-rd-cover-preview]').exists()).toBe(true)

    await item.trigger('mouseleave')
    expect(wrapper.find('[data-rd-cover-preview]').exists()).toBe(false)
  })

  it('表紙が無い本は頭文字の代替表示を出す（パネルの中の 165×236 の枠）', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-rd-spine-book="1"]').trigger('mouseenter')
    const preview = wrapper.get('[data-rd-spine-book="1"] [data-rd-cover-preview]')
    expect(preview.element.tagName).toBe('DIV')
    expect(preview.classes()).toContain('rds-preview--fallback')
    // 頭文字（The Secret Garden → T）と書名を出す
    expect(preview.get('.rds-preview__initial').text()).toBe('T')
    expect(preview.text()).toContain('The Secret Garden')

    await wrapper.get('[data-rd-spine-book="3"]').trigger('mouseenter')
    const japanese = wrapper.get('[data-rd-spine-book="3"] [data-rd-cover-preview]')
    expect(japanese.get('.rds-preview__initial').text()).toBe('中')
  })

  it('prefers-reduced-motion でも表紙は出す（演出だけ切る）', async () => {
    const { wrapper } = await setup({ reduceMotion: true, books: [book({ coverAvailable: true })] })

    await wrapper.get('[data-rd-spine-book="1"]').trigger('mouseenter')
    expect(wrapper.find('img[data-rd-cover-preview]').exists()).toBe(true)
    expect(wrapper.findAll('.rds-cover-panel').length).toBe(1)

    const css = readShelfCss()
    const reduced = css.slice(css.indexOf('@media (prefers-reduced-motion: reduce)'))
    expect(reduced).toMatch(/\.rds-book\.is-tip \.rds-cover-panel[\s\S]*?animation:\s*none/)
  })

  it('棚の右端の本は表紙を左側に出す（画面の外へはみ出さない）', async () => {
    const { wrapper } = await setup({
      books: [1, 2, 3, 4, 5].map((bookId) => book({ bookId, title: `Book ${bookId}` }))
    })

    expect(wrapper.get('[data-rd-spine-book="1"]').classes()).not.toContain('is-preview-left')
    expect(wrapper.get('[data-rd-spine-book="5"]').classes()).toContain('is-preview-left')
  })

  it('ツールチップに進捗を % で出す（無ければ現在ページから計算する）', async () => {
    const { wrapper } = await setup({
      books: [
        book({ bookId: 1, readPercent: 42, currentPage: 115, totalPages: 272 }),
        book({ bookId: 2, categoryId: 2, readPercent: 0, currentPage: 5, totalPages: 20 })
      ]
    })

    await wrapper.get('[data-rd-spine-book="1"]').trigger('mouseenter')
    const tip = wrapper.get('[data-rd-spine-book="1"] [data-rd-tip="1"]')
    expect(tip.get('[data-rd-progress]').text()).toContain('42%')
    expect(tip.get('[data-rd-progress]').text()).toContain('115 / 272 ページ')
    expect(tip.text()).toContain('The Secret Garden')

    // readPercent が 0 のときは currentPage / totalPages から計算する（5 / 20 = 25%）
    await wrapper.get('[data-rd-spine-book="2"]').trigger('mouseenter')
    expect(wrapper.get('[data-rd-spine-book="2"] [data-rd-progress]').text()).toContain('25%')
  })
})

/* ---------- 本棚 / 読書履歴 のタブ ---------- */

describe('書籍閲覧: 自分の本棚: ルートとメニュー', () => {
  it('/reading-shelf（書籍閲覧2）のルートは 3 エリアとも無い', async () => {
    const { routes } = await import('@/router')
    const shelf = routes
      .flatMap((route) => (route.children ?? []).map((child) => child as { name?: unknown; path?: string }))
      .filter((child) => typeof child.name === 'string' && String(child.name).includes('reading-shelf'))

    expect(shelf).toEqual([])
  })

  it('読書管理の子メニューが 書籍管理 → 書籍閲覧 の順で並ぶ（書籍閲覧2 は無い）', async () => {
    const { prototypeMenu } = await import('@/config/menuRegistry')
    for (const area of ['admin', 'student', 'parent'] as const) {
      const reading = prototypeMenu(area).find((item) => item.id === 'reading')
      expect(reading?.children?.map((child) => child.label)).toEqual(['書籍管理', '書籍閲覧'])
      expect(reading?.children?.map((child) => child.path)).toEqual([
        `/${area}/reading-books`, `/${area}/reading-reader`
      ])
    }
  })
})

/** 新画面のスタイル定義（縦書きなど、jsdom では確かめられない見た目を確認する）。 */
function readShelfCss(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/features/reading/reading-shelf.css'), 'utf8')
}

/** 棚の見た目を合わせている【図書館】タブ側のスタイル（タブ行など）。 */
function readReaderCss(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/features/reading/reading-reader.css'), 'utf8')
}

/** 背景ごとの「本を座らせる位置」（CSS の `.rds-case.rds-bg-N` の --rds-shelf-seat）。 */
function seatOf(css: string, index: number): number {
  const rule = new RegExp(`\\.rds-case\\.rds-bg-${index}\\s*\\{[^}]*\\}`).exec(css)?.[0] ?? ''
  return Number(/--rds-shelf-seat:\s*(\d+)px/.exec(rule)?.[1] ?? '0')
}

/** 背景ごとの「本の左右の余白」（CSS の `.rds-case.rds-bg-N` の --rds-book-inset。%）。 */
function insetOf(css: string, index: number): number {
  const rule = new RegExp(`\\.rds-case\\.rds-bg-${index}\\s*\\{[^}]*\\}`).exec(css)?.[0] ?? ''
  return Number(/--rds-book-inset:\s*([\d.]+)%/.exec(rule)?.[1] ?? '0')
}

/** 棚の背景の画像（`src/assets/reading/`）の場所。 */
function shelfBackgroundPath(name: string): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return path.join(webRoot, 'src/assets/reading', name)
}

/** 前の版で使っていた手続き生成の木目テクスチャの場所（もう使っていないことを確かめる）。 */
function legacyShelfImagePath(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return path.join(webRoot, 'src/assets/reading/wood-shelf.jpg')
}

/** tokens.css の間隔トークンの値（px）。 */
function spacingToken(name: string): number {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  const tokens = readFileSync(path.join(webRoot, 'src/assets/prototype/tokens.css'), 'utf8')
  return Number(new RegExp(`${name}:\\s*(\\d+)px`).exec(tokens)?.[1] ?? '0')
}

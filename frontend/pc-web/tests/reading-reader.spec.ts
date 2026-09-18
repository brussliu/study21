import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import BookReaderView from '@/views/reading/BookReaderView.vue'
import { progressPercent } from '@/features/reading/shelfLook'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'
import {
  RECORD_PAGE_SIZES,
  type ReadingBook, type ReadingBookDetail, type ReadingCategory, type ReadingMark, type ReadingRecord
} from '@/api/reading'

/**
 * 読書管理【書籍閲覧】（生徒が使う画面）。
 *
 * 2.0 の英語読書（english_reading_reader.jsp）と同じく、本文 PDF を pdf.js で
 * canvas に描画して読めること。本棚（分類ごと）・標記・読書記録・読書履歴を扱い、
 * 書籍の登録・修正・削除・アップロード（＝保護者の書籍管理）は**置かない**。
 *
 * jsdom では本物の pdf.js は動かないので `window.pdfjsLib` を偽物に差し替える。
 * また jsdom にはレイアウトが無いため、選択範囲の矩形（Range#getClientRects）と
 * ページ枠の getBoundingClientRect はテスト側で与える。
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

/** 2 冊目: 実体 PDF が無い本（2.0 から移行した 6 冊と同じ状態）。 */
function bookWithoutPdf(): ReadingBook {
  return book({
    bookId: 2,
    bookNo: 'ER-20260402-124746',
    title: 'Lonely Planet China',
    author: 'Lonely Planet',
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

/** 読書履歴（GET /records）の呼び出し。 */
function historyCalls(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return recorded(fetchMock).filter((call) => call.method === 'GET' && /\/reading\/records/.test(call.url))
}

/** 最後に呼ばれた「読書履歴（GET /records）」のクエリ。 */
function lastHistoryQuery(fetchMock: ReturnType<typeof vi.fn>): URLSearchParams {
  const last = historyCalls(fetchMock).at(-1)
  if (!last) throw new Error('GET /records が呼ばれていません。')
  return queryOf(last.url)
}

/** 連鎖する非同期処理（PDF の読み込み・描画）が終わるまで待つ。 */
async function settle(times = 3): Promise<void> {
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
   * declare it」で落ちる（実機で発生した不具合）。`shallowRef` なら落ちない。
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

/* ---------- 本文選択（jsdom にはレイアウトが無いので矩形をこちらで与える） ---------- */

/** ページ枠（600x800）の左上を (100, 50) とみなす。 */
const PAGE_BOX = { left: 100, top: 50, width: 600, height: 800 }

function rect(left: number, top: number, width: number, height: number): DOMRect {
  return {
    left, top, width, height, right: left + width, bottom: top + height, x: left, y: top,
    toJSON: () => ({})
  } as DOMRect
}

let selectionRects: DOMRect[] = []

function stubLayoutApis(): void {
  Object.defineProperty(Range.prototype, 'getClientRects', {
    configurable: true, writable: true, value: () => selectionRects
  })
  Object.defineProperty(Range.prototype, 'getBoundingClientRect', {
    configurable: true, writable: true, value: () => selectionRects[0] ?? rect(0, 0, 0, 0)
  })
  const context = { setTransform: vi.fn(), clearRect: vi.fn() }
  HTMLCanvasElement.prototype.getContext = vi.fn(() => context) as unknown as typeof HTMLCanvasElement.prototype.getContext
}

/** 本文（テキストレイヤー）を選択して mouseup を発火する。 */
async function selectText(wrapper: VueWrapper, text = 'Harry Potter'): Promise<void> {
  const layer = wrapper.get('[data-rd-pdf-text-layer]').element as HTMLElement
  layer.innerHTML = `<span>${text}</span>`
  const range = document.createRange()
  range.selectNodeContents(layer.querySelector('span') as HTMLElement)
  const selection = window.getSelection()
  selection?.removeAllRanges()
  selection?.addRange(range)
  await wrapper.get('[data-rd-pdf-text-layer]').trigger('mouseup')
  await settle()
}

/** ページ枠に実寸を与える（jsdom は 0 を返すため）。 */
function stubPageBox(wrapper: VueWrapper): void {
  const page = wrapper.get('[data-rd-pdf-page]').element as HTMLElement
  page.getBoundingClientRect = () => rect(PAGE_BOX.left, PAGE_BOX.top, PAGE_BOX.width, PAGE_BOX.height)
}

/**
 * 本文の枠（[data-rd-stage]）の実寸を与える（jsdom はレイアウトを持たないため）。
 * 高さは「枠の上端」を実測して決めるので、top も与えられるようにする。
 */
function stubStageLayout(wrapper: VueWrapper, options: { width?: number; top?: number } = {}): void {
  const stage = wrapper.get('[data-rd-stage]').element as HTMLElement
  const width = options.width ?? 900
  const top = options.top ?? 0
  Object.defineProperty(stage, 'clientWidth', { configurable: true, value: width })
  stage.getBoundingClientRect = () => rect(0, top, width, 0)
}

/** ポインタ操作のイベント（jsdom の Event には clientX/clientY が乗らないので自分で載せる）。 */
function pointerEvent(type: string, clientX: number, clientY: number): Event {
  const event = new Event(type, { bubbles: true })
  Object.defineProperty(event, 'clientX', { value: clientX })
  Object.defineProperty(event, 'clientY', { value: clientY })
  return event
}

/** ブラウザの高さ（window.innerHeight）。 */
function stubWindowHeight(height: number): void {
  Object.defineProperty(window, 'innerHeight', { configurable: true, value: height })
}

/** 画面のスタイル（jsdom は CSS を適用しないので、定義そのものを確認する）。 */
function readReaderCss(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/features/reading/reading-reader.css'), 'utf8')
}

/** アイコンのスプライト（新しい標記アイコンが定義されているか確認する）。 */
function readIconsSvg(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/assets/icons/icons.svg'), 'utf8')
}

/* ---------- 画面のセットアップ ---------- */

interface SetupOptions {
  mode?: 'shelf' | 'viewer'
  /** 本棚モードのタブを `?view=` で指定して開く（未指定は図書館＝カードの一覧）。 */
  view?: 'library' | 'self' | 'shelf' | 'history'
  bookId?: number
  books?: ReadingBook[]
  categories?: ReadingCategory[]
  detail?: (page: number) => ReadingBookDetail
  bookMarks?: ReadingMark[]
  records?: ReadingRecord[]
  allRecords?: ReadingRecord[]
  pdfPages?: number
  pdfFail?: 'missing' | 'other'
  /** 引き当て（POST /lookup）の応答を差し替える（取れないときの検証に使う）。 */
  lookupResponse?: ((body: { text?: string; language?: string }) => Response) | null
}

/** afterEach で確実に unmount する（document の mouseup 購読を残さない）。 */
let mounted: VueWrapper | null = null

/** テストで書き換える前のブラウザの高さ（afterEach で戻す）。 */
const ORIGINAL_INNER_HEIGHT = window.innerHeight

async function setup(options: SetupOptions = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  // ルータの認証ガードを通す（生徒として開く）
  useAuthStore().login('STUDENT', 'テスト利用者')
  const router = createAppRouter()
  // 既定のタブは自分の本棚だが、テストは図書館のカードを見ることが多いので library を既定にする
  const viewQuery = options.view === undefined
    ? '?view=library'
    : options.view === 'self' ? '' : `?view=${options.view}`
  const query = options.mode === 'viewer' ? `?bookId=${options.bookId ?? 1}` : viewQuery
  await router.push(`/student/reading-reader${query}`)
  await router.isReady()

  const shelf = options.books ?? [book(), bookWithoutPdf(), bookUncategorized()]
  const categories = options.categories ?? CATEGORIES
  const bookMarks = options.bookMarks ?? [
    mark(),
    mark({ markId: 12, markType: 'highlight', targetText: 'intricate', content: null, color: '#f2d04f', positionX: 0.2, positionY: 0.5, width: 0.12, height: 0.03 })
  ]
  const detailFor = options.detail ?? ((page: number) => ({
    book: book({ currentPage: page }),
    records: [record()],
    marks: page === 1 ? bookMarks : [],
    markedPages: [1, 4]
  }))
  const allRecords = options.allRecords ?? [
    record(),
    record({ recordId: 6, bookId: 2, bookTitle: 'Lonely Planet China', readAt: '2026-08-04T20:10:00', pageStart: 2, pageEnd: 6, minutes: 18, memo: '電車の中で読んだ', markCount: 3 })
  ]
  const fetchedRecords = options.records ?? [record()]
  const pdf = stubPdfjs({ numPages: options.pdfPages ?? 3, fail: options.pdfFail })
  const lookupResponse = options.lookupResponse === undefined ? null : options.lookupResponse

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
      if (method === 'GET') {
        return ok({
          items: bookMarks, totalElements: bookMarks.length, page: 1, size: 200, totalPages: 1,
          vocabularyCount: bookMarks.filter((item) => item.markType === 'vocabulary').length,
          highlightCount: bookMarks.filter((item) => item.markType === 'highlight').length,
          memoCount: bookMarks.filter((item) => item.markType === 'memo').length,
          underlineCount: 0
        })
      }
    }
    if (method === 'DELETE' && /\/reading\/marks\/\d+/.test(target)) {
      return ok({ book: book({ markCount: 2 }), message: '標記を削除しました。' }, '標記を削除しました。')
    }
    // 語彙・読み方の引き当て（辞書・翻訳サービスは実物の代わりに固定の応答を返す）
    if (method === 'POST' && target.includes('/reading/lookup')) {
      const asked = JSON.parse(String(init?.body ?? '{}')) as { text?: string; language?: string }
      if (lookupResponse !== null) return lookupResponse(asked)
      return ok({
        text: asked.text ?? '', language: asked.language ?? '英語',
        japanese: '（電話の応答で）もしもし', chinese: 'int. 喂，你好',
        pinyin: null, explanation: null, source: 'EXCELAPI+YOUDAO', cached: false,
        message: '辞書から意味を取得しました。'
      })
    }
    // その本の標記をすべて削除し、読書の進捗もリセット（【標記クリア】）
    if (method === 'DELETE' && /\/reading\/books\/\d+\/marks$/.test(target)) {
      return ok(
        { count: 3, message: '標記 3 件・読書記録 2 件と読書の進捗（現在ページ・ステータス）をリセットしました。' },
        '標記 3 件・読書記録 2 件と読書の進捗（現在ページ・ステータス）をリセットしました。'
      )
    }
    // 【自分の本棚】への出し入れ（2026-09-14 の決定。冪等なので何度でも 200）
    if (method === 'PUT' && /\/reading\/books\/\d+\/shelf$/.test(target)) {
      const bookId = Number(target.match(/\/books\/(\d+)\/shelf/)?.[1] ?? '1')
      const target0 = shelf.find((item) => item.bookId === bookId) ?? book()
      return ok({ book: { ...target0, inMyShelf: true }, message: '本棚に入れました。【自分の本棚】から読めます。' },
        '本棚に入れました。【自分の本棚】から読めます。')
    }
    if (method === 'DELETE' && /\/reading\/books\/\d+\/shelf$/.test(target)) {
      const bookId = Number(target.match(/\/books\/(\d+)\/shelf/)?.[1] ?? '1')
      const target0 = shelf.find((item) => item.bookId === bookId) ?? book()
      return ok({ book: { ...target0, inMyShelf: false }, message: '本棚から外しました。' }, '本棚から外しました。')
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
    // 読書履歴（全書籍・この本。dateFrom / dateTo / pageNo / page / size はクエリで受ける）
    if (method === 'GET' && /\/reading\/records/.test(target)) {
      const bookId = params.get('bookId')
      const page = Number(params.get('page') ?? '1')
      const size = Number(params.get('size') ?? '20')
      const source = bookId ? fetchedRecords : allRecords
      const from = (page - 1) * size
      return ok({
        items: source.slice(from, from + size),
        totalElements: source.length,
        page,
        size,
        totalPages: Math.max(1, Math.ceil(source.length / size))
      })
    }
    // 1 冊（閲覧）
    if (method === 'GET' && /\/reading\/books\/\d+(\?|$)/.test(target)) {
      const requested = Number(target.match(/\/reading\/books\/(\d+)/)?.[1] ?? '1')
      const shelfBook = shelf.find((item) => item.bookId === requested) ?? book()
      const page = Number(params.get('markPage') ?? String(shelfBook.currentPage || 1))
      const requested_ = page > 0 ? page : 1
      const detail = detailFor(requested_)
      return ok({ ...detail, book: { ...shelfBook, currentPage: requested_ } })
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
        page: Number(params.get('page') ?? '1'),
        size: Number(params.get('size') ?? '24'),
        totalPages: 1,
        totals: { bookCount: shelf.length, readingCount: 2, finishedCount: 1, totalMinutes: 2000, markCount: 33 }
      })
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(BookReaderView, { global: { plugins: [pinia, router] }, attachTo: document.body })
  mounted = wrapper
  await settle()
  return { wrapper, fetchMock, router, pdf }
}

beforeEach(() => {
  useToast().items.splice(0)
  window.sessionStorage.clear()
  window.scrollTo = vi.fn()
  selectionRects = [rect(250, 150, 120, 20)] // ページ枠内: x 0.25 / y 0.125 / w 0.2 / h 0.025
  stubLayoutApis()
})

afterEach(() => {
  mounted?.unmount()
  mounted = null
  window.getSelection()?.removeAllRanges()
  delete (window as unknown as { pdfjsLib?: unknown }).pdfjsLib
  vi.unstubAllGlobals()
  stubWindowHeight(ORIGINAL_INNER_HEIGHT)
})

/* ---------- 本棚（bookId 未指定） ---------- */

describe('書籍閲覧: 本棚', () => {
  it('分類タブで棚を切り替え、categoryId を付けて本を取り直す', async () => {
    const { wrapper, fetchMock } = await setup()

    expect(wrapper.findAll('[data-rd-shelf-book]').length).toBe(3)
    expect(lastShelfCall(fetchMock).url).not.toContain('categoryId')
    expect(wrapper.get('[data-rd-category="1"]').text()).toContain('英語 小説')

    await wrapper.get('[data-rd-category="2"]').trigger('click')
    await settle()

    expect(queryOf(lastShelfCall(fetchMock).url).get('categoryId')).toBe('2')
    expect(wrapper.findAll('[data-rd-shelf-book]').length).toBe(1)
    expect(wrapper.get('[data-rd-shelf-book="2"]').text()).toContain('Lonely Planet China')

    // 未分類は categoryId=0 で問い合わせる（契約 §3.2）
    await wrapper.get('[data-rd-category="none"]').trigger('click')
    await settle()
    expect(queryOf(lastShelfCall(fetchMock).url).get('categoryId')).toBe('0')
    expect(wrapper.get('[data-rd-shelf-book="3"]').text()).toContain('中国語の絵本')
  })

  it('「未着手」の本の進捗は 0% と出す（現在ページは 1 のまま）', async () => {
    const { wrapper } = await setup({
      books: [
        // 標記クリアのあとの本（現在ページ 1・未着手。総ページは 2）
        book({ bookId: 1, status: '未着手', currentPage: 1, totalPages: 2, readPercent: 50 }),
        // 読書中の本は今までどおり readPercent を出す
        book({ bookId: 2, status: '読書中', currentPage: 1, totalPages: 40, readPercent: 3 })
      ]
    })

    const reset = wrapper.get('[data-rd-shelf-book="1"]')
    expect(reset.text()).toContain('1 / 2 頁（0%）')
    // 目盛りも 0 幅（読んでいない本が進んでいるように見えない）
    expect(wrapper.get('[data-rd-shelf-book="1"] .rrd-progress__bar').attributes('style')).toContain('width: 0%')

    const reading = wrapper.get('[data-rd-shelf-book="2"]')
    expect(reading.text()).toContain('1 / 40 頁（3%）')
    expect(wrapper.get('[data-rd-shelf-book="2"] .rrd-progress__bar').attributes('style')).toContain('width: 3%')

    // 閲覧画面の【読書状況】の進捗も同じ規則（未着手なら 0%）
    await wrapper.get('[data-rd-shelf-book="2"] [data-rd-open]').trigger('click')
    await settle()
    await settle()
    expect(wrapper.get('[data-rd-progress]').text()).toContain('（3%）')

    // 自分の本棚のホバー表紙パネルも同じ規則を使う（単一の計算に寄せてある）
    expect(progressPercent(book({ status: '未着手', currentPage: 1, totalPages: 2, readPercent: 50 }))).toBe(0)
    expect(progressPercent(book({ status: '読書中', currentPage: 1, totalPages: 40, readPercent: 3 }))).toBe(3)
  })

  it('カードに表紙・書名・作者・難易度・タグ・進捗・標記数・最終読書日時・「読む」を出す', async () => {
    const { wrapper } = await setup()
    const card = wrapper.get('[data-rd-shelf-book="1"]')

    expect(card.text()).toContain('The Secret Garden')
    expect(card.text()).toContain('Frances Hodgson Burnett')
    expect(card.text()).toContain('Elementary')
    expect(card.text()).toContain('Novel')
    expect(card.text()).toContain('読書中')
    expect(card.text()).toContain('1 / 40')
    expect(card.text()).toContain('標記 3 件')
    expect(card.text()).toContain('2026')
    expect(card.find('[data-rd-open]').exists()).toBe(true)
    // 表紙が無い本は代替表示（頭文字）
    expect(card.find('[data-rd-shelf-cover-fallback]').exists()).toBe(true)

    // 表紙の枠は書籍管理（reading.css の .rd-book__cover）と同じ大きさにする
    // （jsdom は CSS を適用しないので、定義そのものを確認する）
    const rule = /\.rrd-book__cover\s*\{[^}]*\}/.exec(readReaderCss())?.[0] ?? ''
    expect(rule).toMatch(/width:\s*136px/)
    expect(rule).toMatch(/height:\s*190px/)
  })

  it('カードに PDF の情報を出さず、【閲覧】【本棚に入れる】【標記クリア】を出す', async () => {
    const { wrapper } = await setup()

    // PDF の有無は一覧では出さない（利用者の指示。開いた画面で分かる）
    expect(wrapper.find('[data-rd-pdf-state]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('PDF あり')
    expect(wrapper.text()).not.toContain('PDF 未登録')

    // 操作はアイコンボタンだけ（2026-09-15 の指示。文字を出すとはみ出すため）。
    // 意味は title / aria-label で補う
    const card = wrapper.get('[data-rd-shelf-book="1"]')
    const open = card.get('[data-rd-open]')
    expect(open.classes()).toContain('btn--icon')
    expect(open.text()).toBe('')
    expect(open.attributes('aria-label')).toContain('閲覧')
    expect(open.get('use').attributes('href')).toBe('#i-book-open')

    const clear = card.get('[data-rd-mark-clear]')
    expect(clear.classes()).toContain('btn--icon')
    expect(clear.text()).toBe('')
    expect(clear.attributes('aria-label')).toContain('標記クリア')
    // 「標記＋読書記録＋進捗」を消すことが title で分かる（ボタンは消しゴムのアイコン）
    expect(clear.attributes('title')).toContain('読書記録')
    expect(clear.attributes('title')).toContain('進捗')
    expect(clear.get('use').attributes('href')).toBe('#i-eraser')

    // 本棚に入れていない本は【本棚に入れる】（2026-09-14 の決定）
    const add = card.get('[data-rd-shelf-add]')
    expect(add.classes()).toContain('btn--icon')
    expect(add.text()).toBe('')
    expect(add.attributes('title')).toContain('本棚に入れる')
    expect(add.get('use').attributes('href')).toBe('#i-plus')
    expect(card.find('[data-rd-shelf-remove]').exists()).toBe(false)
  })

  /**
   * 操作のアイコンは、メタ情報の最後の行（最終読書 …）の右端に同じ行で並べる（2026-09-15 の指示）。
   * 操作だけの行を作らないので、カードの下に空白ができない。
   */
  it('操作のアイコンはメタ情報の最後の行の右端にあり、カードの幅を取らない', async () => {
    const { wrapper } = await setup()
    const card = wrapper.get('[data-rd-shelf-book="1"]')
    const foot = card.get('.rrd-book__stat--foot')
    const actions = foot.get('.rrd-book__actions')
    const buttons = actions.findAll('button')
    expect(buttons.length).toBe(3)
    // 操作だけの行（カード直下）は作らない
    expect(card.find('.rrd-book__main > .rrd-book__actions').exists()).toBe(false)
    expect(foot.text()).toContain('最終読書')
    for (const button of buttons) {
      expect(button.classes()).toContain('btn--icon')
      expect(button.text()).toBe('')
      expect(button.find('svg').exists()).toBe(true)
      // 読み上げ用の名前は必ず付ける
      expect((button.attributes('aria-label') ?? '').length).toBeGreaterThan(0)
      expect((button.attributes('title') ?? '').length).toBeGreaterThan(0)
    }
    // 3 つを 1 行に並べる（折り返さない＝カードの高さも変わらない）
    const css = readReaderCss()
    const row = /\.rrd-book__stat--foot\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(row).toMatch(/display:\s*flex/)
    expect(row).toMatch(/justify-content:\s*space-between/)
    const rule = /\.rrd-book__actions\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(rule).not.toMatch(/flex-wrap:\s*wrap/)
    expect(rule).not.toMatch(/margin-top:\s*auto/)
    expect(buttons.every((button) => button.classes().includes('btn--sm'))).toBe(true)
    // カードの幅は広げた（折り返しで縦に伸びないように）
    expect(css).toMatch(/\.rrd-shelf\s*\{[^}]*grid-template-columns:\s*repeat\(auto-fill,\s*minmax\(420px,\s*1fr\)\)/)
  })

  /** 押せる場所だと分かるように、アイコンは色の付いたチップにする（2026-09-15 の指示）。 */
  it('操作のアイコンは色の付いたチップで、暗色テーマでも読める（tokens の変数だけ）', async () => {
    const { wrapper } = await setup()
    const card = wrapper.get('[data-rd-shelf-book="1"]')
    expect(card.get('[data-rd-open]').classes()).toContain('rrd-book__action--open')
    expect(card.get('[data-rd-shelf-add]').classes()).toContain('rrd-book__action--shelf')
    expect(card.get('[data-rd-mark-clear]').classes()).toContain('rrd-book__action--clear')

    const css = readReaderCss()
    expect(css).toMatch(/\.rrd-book__actions \.btn--icon\s*\{[^}]*color:\s*var\(--color-primary\)/)
    expect(css).toMatch(/\.rrd-book__actions \.btn--icon\s*\{[^}]*background:\s*color-mix\(in srgb, currentcolor/)
    expect(css).toMatch(/\.rrd-book__actions \.btn--icon\s*\{[^}]*border:\s*1px solid color-mix\(in srgb, currentcolor/)
    expect(css).toMatch(/\.rrd-book__action--open\s*\{[^}]*background:\s*var\(--color-primary\)/)
    expect(css).toMatch(/\.rrd-book__action--open\s*\{[^}]*color:\s*var\(--color-on-primary\)/)
    expect(css).toMatch(/\.rrd-book__action--clear\s*\{[^}]*color:\s*var\(--color-warning\)/)
    // 生の色は書かない
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    expect(css).not.toMatch(/\brgba?\(/)
  })

  /** 【自分の本棚】に入っている本はカードの地色を変えて区別する（2026-09-15 の指示）。 */
  it('【自分の本棚】に入っている本だけカードの地色を変え、バッジを出す', async () => {
    const { wrapper } = await setup({
      books: [
        book({ bookId: 1, inMyShelf: false }),
        book({ bookId: 2, inMyShelf: true })
      ]
    })

    const outside = wrapper.get('[data-rd-shelf-book="1"]')
    const inside = wrapper.get('[data-rd-shelf-book="2"]')
    expect(outside.classes()).not.toContain('is-in-shelf')
    expect(inside.classes()).toContain('is-in-shelf')
    expect(outside.find('[data-rd-in-shelf-badge]').exists()).toBe(false)
    expect(inside.get('[data-rd-in-shelf-badge]').text()).toContain('本棚に追加済み')
    // 入っている本は【本棚から外す】に切り替わる
    expect(outside.find('[data-rd-shelf-add]').exists()).toBe(true)
    expect(inside.find('[data-rd-shelf-remove]').exists()).toBe(true)
    expect(inside.get('[data-rd-shelf-remove]').get('use').attributes('href')).toBe('#i-minus')

    // 地色は tokens の変数だけで作る（生の色は書かない）
    const css = readReaderCss()
    const rule = /\.rrd-book\.is-in-shelf\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(rule).toMatch(/background:\s*color-mix\(in srgb, var\(--color-primary\) 6%, var\(--color-surface\)\)/)
    expect(rule).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
  })

  /**
   * 【本棚に入れる】/【本棚から外す】。押すと自分の本棚が変わり、ボタンが入れ替わる
   * （2026-09-14 の決定。本棚はアカウントごと／入れる・外すは冪等）。
   */
  it('【本棚に入れる】は PUT、すでに入っていれば【本棚から外す】は DELETE を送る', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-rd-shelf-book="1"] [data-rd-shelf-add]').trigger('click')
    await settle()
    await settle()

    const put = recorded(fetchMock).find((call) => call.method === 'PUT')
    expect(put?.url).toBe('/api/user/reading/books/1/shelf')
    // 返ってきた inMyShelf でボタンが入れ替わる（本棚を読み直さない）
    expect(wrapper.find('[data-rd-shelf-book="1"] [data-rd-shelf-add]').exists()).toBe(false)
    expect(wrapper.get('[data-rd-shelf-book="1"] [data-rd-shelf-remove]').attributes('title')).toContain('本棚から外す')
    expect(useToast().items.map((item) => item.message).some((m) => m.includes('本棚に入れました'))).toBe(true)

    await wrapper.get('[data-rd-shelf-book="1"] [data-rd-shelf-remove]').trigger('click')
    await settle()
    await settle()

    const deletes = recorded(fetchMock).filter((call) => call.method === 'DELETE')
    expect(deletes.map((call) => call.url)).toContain('/api/user/reading/books/1/shelf')
    expect(wrapper.get('[data-rd-shelf-book="1"] [data-rd-shelf-add]').attributes('title')).toContain('本棚に入れる')
  })

  /** 家庭の本（FAMILY）はバッジで分かる（全体書籍はバッジを出さない）。 */
  it('家庭の本だけ「家庭の本」バッジを出す', async () => {
    const { wrapper } = await setup({
      books: [book({ bookId: 1, scope: 'FAMILY', ownerFamilyId: 2, ownerFamilyLabel: '山田 太郎' })]
    })

    const badge = wrapper.get('[data-rd-shelf-book="1"] [data-rd-scope-badge]')
    expect(badge.text()).toContain('家庭の本')
    expect(badge.text()).toContain('山田 太郎')
  })

  it('【標記クリア】は確認してから、標記・読書記録の全削除と進捗のリセットを 1 回の要求で行う', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    const { wrapper, fetchMock } = await setup()

    // ボタンの説明に「読書記録も進捗も消える」ことが出る
    const clear = wrapper.get('[data-rd-shelf-book="1"] [data-rd-mark-clear]')
    expect(clear.attributes('title')).toContain('標記')
    expect(clear.attributes('title')).toContain('読書記録')
    expect(clear.attributes('title')).toContain('進捗')

    await clear.trigger('click')
    await settle()
    await settle()

    // 確認の文言に、標記だけでなく読書記録・読書の進捗も消えることが出る
    const asked = confirmSpy.mock.calls.map((call) => String(call[0])).join(' ')
    expect(asked).toContain('標記')
    expect(asked).toContain('読書記録')
    expect(asked).toContain('進捗')
    expect(asked).toContain('現在ページ')
    expect(asked).toContain('ステータス')

    // 標記・読書記録の削除と進捗のリセットは 1 つの要求でまとめて行う（DELETE は 1 回だけ）
    const deletes = recorded(fetchMock).filter((call) => call.method === 'DELETE')
    expect(deletes).toHaveLength(1)
    expect(deletes[0]?.url).toBe('/api/user/reading/books/1/marks')
    expect(useToast().items.map((item) => item.message).some((message) => message.includes('進捗'))).toBe(true)
    // 消したあとは本棚を読み直す
    expect(recorded(fetchMock).filter(
      (call) => call.method === 'GET' && call.url.includes('/books?')
    ).length).toBeGreaterThan(1)

    // キャンセルしたら送らない
    confirmSpy.mockReturnValue(false)
    const before = recorded(fetchMock).filter((call) => call.method === 'DELETE').length
    await wrapper.get('[data-rd-shelf-book="2"] [data-rd-mark-clear]').trigger('click')
    await settle()
    expect(recorded(fetchMock).filter((call) => call.method === 'DELETE').length).toBe(before)
    confirmSpy.mockRestore()
  })

  it('書籍の登録・修正・削除・アップロードは置かない', async () => {
    const { wrapper } = await setup()

    expect(wrapper.find('[data-rd-add]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-edit]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-delete]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-upload-pdf]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-category-add]').exists()).toBe(false)
  })

  it('タブは【自分の本棚】【図書館】【読書履歴】の 3 つで、既定は自分の本棚（?view= と同期）', async () => {
    const { wrapper, fetchMock, router } = await setup({ view: 'self' })

    // 3 つのタブがこの順で並ぶ
    expect(wrapper.findAll('[data-rd-tab]').map((tab) => tab.attributes('data-rd-tab')))
      .toEqual(['self', 'library', 'history'])
    // 既定（?view= なし）は自分の本棚＝背表紙の棚
    expect(wrapper.get('[data-rd-tab="self"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('[data-rd-self-shelf]').exists()).toBe(true)
    expect(wrapper.findAll('[data-rd-spine]').length).toBeGreaterThan(0)
    // 図書館のカード一覧は出さない
    expect(wrapper.find('[data-rd-shelf]').exists()).toBe(false)
    expect(router.currentRoute.value.query.view).toBeUndefined()

    // 図書館へ切り替えるとカードの一覧（?view=library）
    await wrapper.get('[data-rd-tab="library"]').trigger('click')
    await settle()
    expect(router.currentRoute.value.query.view).toBe('library')
    expect(wrapper.find('[data-rd-shelf]').exists()).toBe(true)
    expect(wrapper.find('[data-rd-self-shelf]').exists()).toBe(false)

    // 読書履歴を開くと履歴を読みに行く（?view=history）
    const before = historyCalls(fetchMock).length
    await wrapper.get('[data-rd-tab="history"]').trigger('click')
    await settle()
    expect(router.currentRoute.value.query.view).toBe('history')
    expect(historyCalls(fetchMock).length).toBeGreaterThan(before)
    const history = wrapper.get('[data-rd-history-all]')
    expect(history.findAll('[data-rd-history-row]').length).toBe(2)
    expect(history.text()).toContain('Lonely Planet China')

    // 自分の本棚へ戻ると ?view= が消える（既定はクエリに残さない）
    await wrapper.get('[data-rd-tab="self"]').trigger('click')
    await settle()
    expect(router.currentRoute.value.query.view).toBeUndefined()
    expect(wrapper.find('[data-rd-self-shelf]').exists()).toBe(true)
  })

  it('古い ?view=shelf の直リンクは図書館タブとして開く', async () => {
    const { wrapper, router } = await setup({ view: 'shelf' })

    expect(router.currentRoute.value.query.view).toBe('shelf')
    expect(wrapper.get('[data-rd-tab="library"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('[data-rd-shelf]').exists()).toBe(true)
  })

  it('?view=history の直リンク（リロード）でも読書履歴タブが開く', async () => {
    const { wrapper } = await setup({ view: 'history' })

    expect(wrapper.get('[data-rd-tab="history"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.find('[data-rd-shelf]').exists()).toBe(false)
    expect(wrapper.get('[data-rd-history-all]').findAll('[data-rd-history-row]').length).toBe(2)
  })

  it('読書履歴を日付とページ番号で絞り込み、リセットで空に戻す（空欄は送らない）', async () => {
    const { wrapper, fetchMock } = await setup({ view: 'history' })

    // 開いた直後は絞り込み無し
    const initial = lastHistoryQuery(fetchMock)
    expect(initial.get('dateFrom')).toBeNull()
    expect(initial.get('dateTo')).toBeNull()
    expect(initial.get('pageNo')).toBeNull()

    await wrapper.get('[data-rd-history-from]').setValue('2026-08-01')
    await wrapper.get('[data-rd-history-to]').setValue('2026-08-31')
    await wrapper.get('[data-rd-history-page-no]').setValue('3')
    await wrapper.get('[data-rd-history-search]').trigger('click')
    await settle()

    const searched = lastHistoryQuery(fetchMock)
    expect(searched.get('dateFrom')).toBe('2026-08-01')
    expect(searched.get('dateTo')).toBe('2026-08-31')
    expect(searched.get('pageNo')).toBe('3')
    // 検索は 1 ページ目から
    expect(searched.get('page')).toBe('1')

    // 片方だけの指定でも送れる（To は付けない）
    await wrapper.get('[data-rd-history-to]').setValue('')
    await wrapper.get('[data-rd-history-search]').trigger('click')
    await settle()
    expect(lastHistoryQuery(fetchMock).get('dateFrom')).toBe('2026-08-01')
    expect(lastHistoryQuery(fetchMock).get('dateTo')).toBeNull()

    await wrapper.get('[data-rd-history-reset]').trigger('click')
    await settle()

    const reset = lastHistoryQuery(fetchMock)
    expect(reset.get('dateFrom')).toBeNull()
    expect(reset.get('dateTo')).toBeNull()
    expect(reset.get('pageNo')).toBeNull()
    expect((wrapper.get('[data-rd-history-from]').element as HTMLInputElement).value).toBe('')
    expect((wrapper.get('[data-rd-history-page-no]').element as HTMLInputElement).value).toBe('')
  })

  it('件数のドロップダウンを変えると size が変わり、1 ページ目から読み直す', async () => {
    const many = Array.from({ length: 25 }, (_, index) => record({ recordId: 200 + index }))
    const { wrapper, fetchMock } = await setup({ view: 'history', allRecords: many })

    // 選択肢は画面共通の 20 / 50 / 100 件
    const options = wrapper.findAll('[data-rd-history-size] option').map((item) => (item.element as HTMLOptionElement).value)
    expect(options).toEqual(RECORD_PAGE_SIZES.map(String))
    expect((wrapper.get('[data-rd-history-size]').element as HTMLSelectElement).value).toBe('20')
    expect(lastHistoryQuery(fetchMock).get('size')).toBe('20')
    expect(wrapper.findAll('[data-rd-history-row]').length).toBe(20)

    // 2 ページ目へ移動してから件数を変えると、1 ページ目から読み直す
    await wrapper.get('[data-rd-history-page="2"]').trigger('click')
    await settle()
    expect(lastHistoryQuery(fetchMock).get('page')).toBe('2')

    await wrapper.get('[data-rd-history-size]').setValue('50')
    await settle()

    const changed = lastHistoryQuery(fetchMock)
    expect(changed.get('size')).toBe('50')
    expect(changed.get('page')).toBe('1')
    expect(wrapper.findAll('[data-rd-history-row]').length).toBe(25)
  })

  it('ページャで 2 ページ目に移動できる（件数情報とページ番号を出す）', async () => {
    const many = Array.from({ length: 25 }, (_, index) => record({ recordId: 300 + index }))
    const { wrapper, fetchMock } = await setup({ view: 'history', allRecords: many })

    const pager = wrapper.get('[data-rd-history-pager]')
    expect(pager.text()).toContain('全 25 件（1 / 2 ページ）')

    await wrapper.get('[data-rd-history-page="2"]').trigger('click')
    await settle()

    expect(lastHistoryQuery(fetchMock).get('page')).toBe('2')
    expect(wrapper.findAll('[data-rd-history-row]').length).toBe(5)
    expect(wrapper.get('[data-rd-history-pager]').text()).toContain('全 25 件（2 / 2 ページ）')
    expect(wrapper.get('[data-rd-history-page="2"]').classes()).toContain('is-active')

    await wrapper.get('[data-rd-history-prev]').trigger('click')
    await settle()
    expect(lastHistoryQuery(fetchMock).get('page')).toBe('1')
  })

  it('読書履歴に削除アイコンを置かず、DELETE も呼ばない', async () => {
    const { wrapper, fetchMock } = await setup({ view: 'history' })

    expect(wrapper.findAll('[data-rd-history-row]').length).toBeGreaterThan(0)
    expect(wrapper.find('[data-rd-record-delete]').exists()).toBe(false)
    expect(recorded(fetchMock).some((call) => call.method === 'DELETE')).toBe(false)
  })

  it('読書履歴を書籍で絞り込め、リセットで「すべての書籍」に戻る', async () => {
    const { wrapper, fetchMock } = await setup({ view: 'history' })

    // 選択肢は searchReadingBooks で 1 回だけ読む
    const bookListCalls = (): Call[] =>
      recorded(fetchMock).filter((call) => /\/reading\/books(\?|$)/.test(call.url) && queryOf(call.url).get('size') === '100')
    expect(bookListCalls().length).toBe(1)
    expect(queryOf(bookListCalls()[0].url).get('page')).toBe('1')

    const select = wrapper.get('[data-rd-history-book]')
    const options = select.findAll('option')
    expect(options.map((item) => item.text()))
      .toEqual(['すべての書籍', 'The Secret Garden', 'Lonely Planet China', '中国語の絵本'])
    expect(options[1].attributes('title')).toBe('The Secret Garden')

    // 選ぶと bookId 付きで 1 ページ目から読む
    await select.setValue('2')
    await settle()
    expect(lastHistoryQuery(fetchMock).get('bookId')).toBe('2')
    expect(lastHistoryQuery(fetchMock).get('page')).toBe('1')

    // 日付・ページ番号の絞り込みと併用できる
    await wrapper.get('[data-rd-history-from]').setValue('2026-08-01')
    await wrapper.get('[data-rd-history-page-no]').setValue('3')
    await wrapper.get('[data-rd-history-search]').trigger('click')
    await settle()
    expect(lastHistoryQuery(fetchMock).get('bookId')).toBe('2')
    expect(lastHistoryQuery(fetchMock).get('dateFrom')).toBe('2026-08-01')
    expect(lastHistoryQuery(fetchMock).get('pageNo')).toBe('3')

    // リセットで「すべての書籍」に戻る（bookId を送らない）
    await wrapper.get('[data-rd-history-reset]').trigger('click')
    await settle()
    expect(lastHistoryQuery(fetchMock).get('bookId')).toBeNull()
    expect((wrapper.get('[data-rd-history-book]').element as HTMLSelectElement).value).toBe('')

    // タブを行き来しても選択肢を取り直さない（1 回だけ）
    await wrapper.get('[data-rd-tab="library"]').trigger('click')
    await settle()
    await wrapper.get('[data-rd-tab="history"]').trigger('click')
    await settle()
    expect(bookListCalls().length).toBe(1)
  })

  it('閲覧モードでは書籍の絞り込みを出さない（その本の履歴なので）', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })

    const own = wrapper.get('[data-rd-history]')
    expect(own.find('[data-rd-history-book]').exists()).toBe(false)
    expect(own.find('[data-rd-history-from]').exists()).toBe(true)
  })

  it('該当が無いときは案内を出す', async () => {
    const { wrapper } = await setup({ view: 'history', allRecords: [] })

    expect(wrapper.get('[data-rd-history-empty]').text()).toContain('該当する読書履歴がありません。')
    expect(wrapper.find('[data-rd-history-pager]').exists()).toBe(false)
  })
})

/* ---------- 閲覧（?bookId=） ---------- */

describe('書籍閲覧: 本文 PDF', () => {
  it('【閲覧】で URL が ?bookId= になり、PDF を読み込んで canvas に描画する', async () => {
    const { wrapper, router, pdf } = await setup()

    await wrapper.get('[data-rd-shelf-book="1"] [data-rd-open]').trigger('click')
    await settle(6)

    expect(router.currentRoute.value.query.bookId).toBe('1')
    expect(pdf.getDocument).toHaveBeenCalledWith({
      url: '/api/user/reading/books/1/pdf?v=3', withCredentials: true
    })
    expect(pdf.workerSrc()).toBe('/lib/pdfjs/pdf.worker.min.js')
    expect(pdf.renders.length).toBeGreaterThan(0)
    expect(pdf.renders[0].pageNo).toBe(1)
    expect(wrapper.find('canvas[data-rd-pdf-canvas]').exists()).toBe(true)
    expect(pdf.renderTextLayer).toHaveBeenCalled()
    expect(wrapper.get('[data-rd-page-count]').text()).toBe('/ 3')
    expect(wrapper.find('[data-rd-shelf]').exists()).toBe(false)
  })

  it('ページ送り（次へ・前へ・番号入力）で描画するページが変わる', async () => {
    const { wrapper, pdf, fetchMock } = await setup({ mode: 'viewer' })

    await wrapper.get('[data-rd-page-next]').trigger('click')
    await settle()
    expect(pdf.pages.at(-1)).toBe(2)
    expect((wrapper.get('[data-rd-page-input]').element as HTMLInputElement).value).toBe('2')
    // そのページの標記を取り直す（markPage）
    expect(queryOf(recorded(fetchMock).filter((call) => /\/reading\/books\/1\?/.test(call.url)).at(-1)?.url ?? '').get('markPage')).toBe('2')

    await wrapper.get('[data-rd-page-prev]').trigger('click')
    await settle()
    expect(pdf.pages.at(-1)).toBe(1)

    await wrapper.get('[data-rd-page-input]').setValue(3)
    await wrapper.get('[data-rd-page-input]').trigger('change')
    await settle()
    expect(pdf.pages.at(-1)).toBe(3)
    expect(wrapper.get('[data-rd-page-count]').text()).toBe('/ 3')

    // ページ一覧（悬浮式。マウスを乗せると出る）からも移動でき、標記のあるページには印が付く
    expect(wrapper.find('[data-rd-page-rail]').exists()).toBe(false)
    await wrapper.get('.rrd-rail-card').trigger('mouseenter')
    await settle()
    expect(wrapper.find('[data-rd-page-rail]').exists()).toBe(true)
    expect(wrapper.get('[data-rd-page="4"]').classes()).toContain('has-mark')

    await wrapper.get('[data-rd-page="2"]').trigger('click')
    await settle()
    expect(pdf.pages.at(-1)).toBe(2)
    expect((wrapper.get('[data-rd-page-input]').element as HTMLInputElement).value).toBe('2')
  })

  it('拡大・縮小は手動倍率になり、トグルで高さ合わせに戻せる', async () => {
    const { wrapper, pdf } = await setup({ mode: 'viewer' })

    // 初期表示は高さ基準。トグルは「次に押すと幅になる」ことを示す
    const base = pdf.renders.at(-1)?.scale ?? 0
    expect(base).toBeGreaterThan(0)
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-fit-mode')).toBe('height')
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-zoom-fit')).toBeDefined()
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-zoom-height')).toBeUndefined()

    await wrapper.get('[data-rd-zoom-in]').trigger('click')
    await settle()
    const zoomedIn = pdf.renders.at(-1)?.scale ?? 0
    expect(zoomedIn).toBeGreaterThan(base)
    // 手動倍率になると、トグルは「高さに合わせる」（戻す手段）になる
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-fit-mode')).toBe('manual')
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-zoom-height')).toBeDefined()
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-zoom-fit')).toBeUndefined()
    expect(wrapper.get('[data-rd-fit-mode]').text()).toContain('高さに合わせる')

    await wrapper.get('[data-rd-zoom-out]').trigger('click')
    await settle()
    expect(pdf.renders.at(-1)?.scale ?? 0).toBeLessThan(zoomedIn)

    await wrapper.get('[data-rd-fit-mode]').trigger('click')
    await settle()
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-fit-mode')).toBe('height')
    expect(pdf.renders.at(-1)?.scale ?? 0).toBeCloseTo(base, 5)
  })

  it('初期表示はブラウザの高さに収まる倍率で、リサイズでも測り直す', async () => {
    stubWindowHeight(900)
    const { wrapper, pdf } = await setup({ mode: 'viewer' })

    // ページは 600×800。枠の上端は jsdom では 0 なので 900 - 0 - 40（余白）= 860 → 860 / 800
    expect(pdf.renders.at(-1)?.scale).toBeCloseTo(860 / 800, 5)
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-fit-mode')).toBe('height')
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-zoom-fit')).toBeDefined()

    // 枠の上端を実測して測り直す（ブラウザの高さが変わったとき）
    stubStageLayout(wrapper, { width: 900, top: 100 })
    window.dispatchEvent(new Event('resize'))
    await settle()
    expect(pdf.renders.at(-1)?.scale).toBeCloseTo((900 - 100 - 40) / 800, 5)
  })

  it('ズームのトグルで【幅に合わせる】⇄【高さに合わせる】を切り替える', async () => {
    stubWindowHeight(900)
    const { wrapper, pdf } = await setup({ mode: 'viewer' })
    stubStageLayout(wrapper, { width: 900, top: 100 })

    // 高さ基準のときは「幅に合わせる」＋目のアイコン
    expect(wrapper.get('[data-rd-fit-mode]').text()).toContain('幅に合わせる')
    expect(wrapper.get('[data-rd-fit-mode]').get('use').attributes('href')).toBe('#i-eye')

    await wrapper.get('[data-rd-zoom-fit]').trigger('click')
    await settle()
    // 枠の幅 900 ÷ ページ幅 600
    expect(pdf.renders.at(-1)?.scale).toBeCloseTo(900 / 600, 5)
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-fit-mode')).toBe('width')
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-zoom-height')).toBeDefined()
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-zoom-fit')).toBeUndefined()
    // 幅基準のときは「高さに合わせる」＋モニターのアイコン
    expect(wrapper.get('[data-rd-fit-mode]').text()).toContain('高さに合わせる')
    expect(wrapper.get('[data-rd-fit-mode]').get('use').attributes('href')).toBe('#i-monitor')

    await wrapper.get('[data-rd-zoom-height]').trigger('click')
    await settle()
    expect(pdf.renders.at(-1)?.scale).toBeCloseTo((900 - 100 - 40) / 800, 5)
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-fit-mode')).toBe('height')
    expect(wrapper.get('[data-rd-fit-mode]').attributes('data-rd-zoom-fit')).toBeDefined()
  })

  it('ページ一覧は右カラムのカードで、既定は出ておらず、マウスを乗せると悬浮式で出る（【開く】は無い）', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })

    // 本文の枠ではなく右カラムのカードに置く
    const side = wrapper.get('.rrd-side')
    const card = side.get('[data-rd-rail-open]')
    expect(card.text()).toContain('ページ一覧')
    expect(wrapper.find('[data-rd-viewer] [data-rd-page-rail]').exists()).toBe(false)
    // カードの中の常駐一覧と【開く】【閉じる】のボタンは 2026-09-15 の指示で廃止した
    expect(wrapper.find('[data-rd-rail-toggle]').exists()).toBe(false)
    expect(card.text()).not.toContain('開く')
    expect(card.text()).not.toContain('閉じる')
    // 見出しの要約（P.現在 / 総ページ）はカードに出す
    expect(card.get('[data-rd-rail-summary]').text()).toContain('P.1')

    expect(side.find('[data-rd-page-rail]').exists()).toBe(false)
    expect(card.attributes('data-rd-rail-open')).toBe('false')

    // マウスを乗せると出る（data-rd-rail-open は「悬浮式が出ているか」を表す）
    await card.trigger('mouseenter')
    await settle()
    expect(card.attributes('data-rd-rail-open')).toBe('true')
    expect(side.find('[data-rd-page-rail]').exists()).toBe(true)
    expect(side.get('[data-rd-page-rail]').findAll('[data-rd-page]').length).toBe(40)

    // マウスが外れると（少し待って）閉じる
    await card.trigger('mouseleave')
    await new Promise((resolve) => setTimeout(resolve, 320))
    await settle()
    expect(card.attributes('data-rd-rail-open')).toBe('false')
    expect(side.find('[data-rd-page-rail]').exists()).toBe(false)
  })

  it('本文が左（広い列）・カードは右カラム 320px（DOM の順とグリッドを合わせる）', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })

    // DOM の順（本文 → カード）。読み上げ・Tab の順も見た目と同じにする
    const children = [...wrapper.get('.rrd-reader').element.children].map((element) => element.className)
    expect(children[0]).toContain('rrd-viewer-card')
    expect(children[1]).toContain('rrd-side')

    // グリッドは 本文 1fr ｜ カード 320px（左右が入れ替わっていたら気づけるように）
    const css = readReaderCss()
    const rule = /\.rrd-reader\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(rule).toMatch(/grid-template-columns:\s*minmax\(0,\s*1fr\)\s*320px/)
    // 狭い画面は 1 カラムに折り返す（今までどおり）
    expect(css).toMatch(/@media \(max-width: 1024px\)[\s\S]*?\.rrd-reader\s*\{[^}]*grid-template-columns:\s*minmax\(0,\s*1fr\)/)
  })

  it('カードにマウスを乗せると「悬浮式」のページ一覧が出て、ページを選ぶと移動する（レイアウトは動かさない）', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })
    const card = wrapper.get('.rrd-rail-card')

    // 既定は出ていない（カードの中の一覧も閉じたまま）
    expect(wrapper.find('[data-rd-rail-popover]').exists()).toBe(false)
    expect(card.attributes('data-rd-rail-open')).toBe('false')
    const stageWidthBefore = wrapper.get('[data-rd-stage]').element.clientWidth

    // マウスを乗せると出る（本文の枠の幅＝レイアウトは変わらない）
    await card.trigger('mouseenter')
    await settle()
    const popover = wrapper.get('[data-rd-rail-popover]')
    expect(wrapper.get('[data-rd-stage]').element.clientWidth).toBe(stageWidthBefore)
    expect(card.attributes('data-rd-rail-open')).toBe('true')

    // 見出し・凡例・現在ページ
    expect(popover.text()).toContain('ページ一覧')
    expect(popover.text()).toContain('既読')
    expect(popover.text()).toContain('未読')
    expect(popover.text()).toContain('標記あり')
    expect(popover.get('[data-rd-rail-popover-summary]').text()).toContain('P.1')

    // ページのマスは総ページ数ぶん（この本は 40 ページ）
    const chips = popover.findAll('[data-rd-page]')
    expect(chips.length).toBe(40)
    expect(chips[0]?.attributes('data-rd-page-state')).toBe('read')
    expect(chips[1]?.attributes('data-rd-page-state')).toBe('unread')
    expect(popover.get('[data-rd-page="1"]').classes()).toContain('is-active')
    expect(popover.get('[data-rd-page="4"]').classes()).toContain('has-mark')
    expect(popover.get('[data-rd-page="12"]').attributes('title')).toContain('P.12')

    // マスを選ぶとそのページへ移動して、現在ページの印も移る
    await popover.get('[data-rd-page="3"]').trigger('click')
    await settle()
    expect((wrapper.get('[data-rd-page-input]').element as HTMLInputElement).value).toBe('3')
    expect(wrapper.get('[data-rd-page="3"]').classes()).toContain('is-active')
    expect(wrapper.get('[data-rd-page="1"]').classes()).not.toContain('is-active')

    // マウスが外れてもすぐには閉じない（一覧へ移って選べるように）。少し待つと閉じる
    await card.trigger('mouseleave')
    await settle()
    expect(wrapper.find('[data-rd-rail-popover]').exists()).toBe(true)
    await new Promise((resolve) => setTimeout(resolve, 320))
    await settle()
    expect(wrapper.find('[data-rd-rail-popover]').exists()).toBe(false)

    // キーボードで入ったときも出る
    await card.trigger('focusin')
    await settle()
    expect(wrapper.find('[data-rd-rail-popover]').exists()).toBe(true)
    await card.trigger('mouseleave')
    await new Promise((resolve) => setTimeout(resolve, 320))
    await settle()

    // カードの中の常駐一覧は廃止（一覧は悬浮式だけ）
    expect(wrapper.find('[data-rd-rail-toggle]').exists()).toBe(false)

    const css = readReaderCss()
    // 1 行 10 ページの固定列（2026-09-15 の指示）／幅は広く・高さは低く
    expect(css).toMatch(/\.rrd-rail-popover__grid\s*\{[^}]*grid-template-columns:\s*repeat\(10,\s*minmax\(0,\s*1fr\)\)/)
    expect(css).toMatch(/\.rrd-rail-popover\s*\{[^}]*width:\s*min\(33rem,/)
    expect(css).toMatch(/\.rrd-rail-popover\s*\{[^}]*max-height:\s*45vh/)
    // 使わなくなった常駐一覧のスタイルは消してある
    expect(css).not.toMatch(/\.rrd-rail\s*\{/)

    // 動きが苦手な設定ではアニメーションを付けない（機能は同じ）
    expect(css).toMatch(/\.rrd-rail-popover\s*\{[^}]*position:\s*absolute/)
    expect(css).toMatch(/\.rrd-rail-popover\s*\{[^}]*right:\s*calc\(100% \+ var\(--sp-3\)\)/)
    expect(css).toMatch(/@media \(prefers-reduced-motion: reduce\)[\s\S]*?\.rrd-rail-popover\s*\{[^}]*animation:\s*none/)
    expect(css).not.toMatch(/\.rrd-rail-popover[^{]*\{[^}]*#[0-9a-fA-F]{3,8}\b/)
  })

  it('ページ一覧で読んだページと読んでいないページを区別する', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })
    await wrapper.get('.rrd-rail-card').trigger('mouseenter')
    await settle()

    // チップは右カラムのカードの悬浮式の一覧の中にある
    expect(wrapper.find('.rrd-side [data-rd-page="1"]').exists()).toBe(true)

    // この本の現在ページ（1）までが既読
    expect(wrapper.get('[data-rd-page="1"]').classes()).toContain('is-read')
    expect(wrapper.get('[data-rd-page="1"]').classes()).not.toContain('is-unread')
    expect(wrapper.get('[data-rd-page="1"]').classes()).toContain('is-active')
    expect(wrapper.get('[data-rd-page="1"]').attributes('data-rd-page-state')).toBe('read')
    expect(wrapper.get('[data-rd-page="2"]').classes()).toContain('is-unread')
    expect(wrapper.get('[data-rd-page="2"]').classes()).not.toContain('is-read')
    expect(wrapper.get('[data-rd-page="2"]').attributes('data-rd-page-state')).toBe('unread')
    // 標記の印は今までどおり
    expect(wrapper.get('[data-rd-page="4"]').classes()).toContain('has-mark')

    // 読み進めると、そこまでが既読になる
    await wrapper.get('[data-rd-page="2"]').trigger('click')
    await settle()
    expect(wrapper.get('[data-rd-page="2"]').classes()).toContain('is-read')
    expect(wrapper.get('[data-rd-page="2"]').classes()).toContain('is-active')
    expect(wrapper.get('[data-rd-page="3"]').classes()).toContain('is-unread')

    // 既読は濃く・未読は淡く（jsdom は CSS を適用しないので定義を確認する。色はトークンだけ）
    const css = readReaderCss()
    expect(css).toMatch(/\.rrd-page-chip\.is-read\s*\{[^}]*font-weight:\s*var\(--fw-semibold\)/)
    expect(css).toMatch(/\.rrd-page-chip\.is-unread\s*\{[^}]*color:\s*var\(--color-text-subtle\)/)
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    expect(css).not.toMatch(/\brgba?\(/)
  })

  it('標記ツールはアイコン付きで、本文の枠に貼り付く（スクロールしても常に見える）', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })

    const tools = wrapper.get('[data-rd-tools]')
    const icons: Record<string, string> = {
      vocabulary: 'bookmark',
      highlight: 'highlighter',
      underline: 'underline',
      memo: 'note',
      pen: 'pen'
    }
    for (const [tool, icon] of Object.entries(icons)) {
      const button = tools.get(`[data-rd-tool="${tool}"]`)
      expect(button.find('svg.icon').exists(), `${tool} のアイコン`).toBe(true)
      expect(button.get('use').attributes('href')).toBe(`#i-${icon}`)
      expect(button.text().length).toBeGreaterThan(0) // ラベルも残す
    }
    expect(tools.text()).toContain('ハイライト')
    expect(tools.text()).toContain('手書き')

    // アイコンの実体（スプライト）に定義がある
    const sprite = readIconsSvg()
    for (const icon of ['highlighter', 'underline', 'note', 'pen']) {
      expect(sprite).toContain(`id="i-${icon}" viewBox="0 0 24 24"`)
    }

    // 標記ツールは入れ物（.rrd-tools-anchor）ごと PDF のページの中に置く（既存のフックは維持）。
    // 実際の位置は fixed なので placeTools() が入れる（本文の高さは消費しない）
    const page = wrapper.get('[data-rd-pdf-page]')
    const stage = wrapper.get('[data-rd-stage]')
    const anchor = wrapper.get('[data-rd-tools-anchor]')
    expect(page.find('[data-rd-tools]').exists()).toBe(true)
    expect(anchor.find('[data-rd-tools]').exists()).toBe(true)
    expect(tools.element.parentElement).toBe(anchor.element)
    expect(anchor.element.parentElement).toBe(page.element)
    // スクロール枠（[data-rd-stage]）の直下ではない
    expect(stage.element.contains(tools.element)).toBe(true)
    expect(tools.element.parentElement).not.toBe(stage.element)
    // ページは重ね合わせの基準になる
    expect(readReaderCss()).toMatch(/\.rrd-page-box\s*\{[^}]*position:\s*relative/)
    // 長い説明文は出さない（ツールごとの title で説明する）
    expect(wrapper.find('.rrd-tools__hint').exists()).toBe(false)
    for (const tool of ['highlight', 'pen']) {
      expect(tools.get(`[data-rd-tool="${tool}"]`).attributes('title')).toContain('標記を付ける')
    }

    // jsdom は CSS を適用しないので定義を確認する
    const css = readReaderCss()
    expect(css).toMatch(/\.rrd-tools-anchor\s*\{[^}]*position:\s*fixed/)
    expect(css).toMatch(/\.rrd-tools-anchor\s*\{[^}]*z-index:\s*5/)
    expect(css).not.toMatch(/\.rrd-tools\s*\{[^}]*position:\s*absolute/)
  })

  it('標記ツールはスクロールしても見えている範囲の左上に残る（固定ヘッダの下・視口の中）', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })

    const anchor = wrapper.get('[data-rd-tools-anchor]')
    const stage = wrapper.get('[data-rd-stage]')
    // jsdom はレイアウトしないので、枠とツールの大きさをその場で入れる
    const rect = (values: Partial<DOMRect>): DOMRect => ({
      top: 0, bottom: 0, left: 0, right: 0, width: 0, height: 0, x: 0, y: 0,
      toJSON: () => ({}), ...values
    } as DOMRect)
    const stageRect = (top: number, bottom: number) => {
      stage.element.getBoundingClientRect = () => rect({ top, bottom, left: 300, right: 1300, width: 1000, height: bottom - top })
    }
    anchor.element.getBoundingClientRect = () => rect({ width: 320, height: 40 })
    const settleTools = async () => {
      window.dispatchEvent(new Event('scroll'))
      await new Promise((resolve) => setTimeout(resolve, 20))
    }

    // 1) 本文の枠が画面の下にあるときは、枠の上端 + 8px（読む場所の左上から出ない）
    stageRect(100, 800)
    await settleTools()
    expect(anchor.attributes('style')).toContain('top: 108px')
    expect(anchor.attributes('style')).toContain('left: 308px')
    expect(anchor.attributes('style')).toContain('visibility: visible')

    // 2) 下へスクロールして枠の上端が画面の外へ出ても、ツールは視口の左上 + 8px に残る（見えなくならない）
    stageRect(-200, 500)
    await settleTools()
    expect(anchor.attributes('style')).toContain('top: 8px')
    expect(anchor.attributes('style')).toContain('visibility: visible')

    // 3) 画面の上に貼り付いた帯（アプリのヘッダ）があるときは、その下 + 8px に置く（帯に隠れない）
    const header = document.createElement('header')
    header.className = 'topbar'
    header.style.position = 'sticky'
    header.getBoundingClientRect = () => rect({ top: 0, bottom: 60, height: 60, width: 1680 })
    document.body.appendChild(header)
    try {
      stageRect(-200, 500)
      await settleTools()
      expect(anchor.attributes('style')).toContain('top: 68px')
      expect(anchor.attributes('style')).toContain('visibility: visible')

      // 4) 本文の枠が画面から完全に出たときだけ隠す（ほかのカードの上に出ない）
      stageRect(-900, -200)
      await settleTools()
      expect(anchor.attributes('style')).toContain('top: 68px')
      expect(anchor.attributes('style')).toContain('visibility: hidden')
    } finally {
      header.remove()
    }

    // 5) また読める位置に戻ったら、すぐ出る
    stageRect(100, 800)
    await settleTools()
    expect(anchor.attributes('style')).toContain('visibility: visible')
  })

  it('ページを送っても標記ツールは入れ物ごとページの中にある（位置は fixed のまま）', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })

    const parentBefore = wrapper.get('[data-rd-tools]').element.parentElement
    expect(parentBefore).toBe(wrapper.get('[data-rd-tools-anchor]').element)

    await wrapper.get('[data-rd-page-next]').trigger('click')
    await settle()

    const parentAfter = wrapper.get('[data-rd-tools]').element.parentElement
    expect(parentAfter).toBe(parentBefore)
    expect(wrapper.get('[data-rd-pdf-page]').find('[data-rd-tools]').exists()).toBe(true)
  })

  it('PDF が出ていないときは標記ツールを出さない（標記できないため）', async () => {
    const { wrapper } = await setup({ mode: 'viewer', pdfFail: 'missing' })

    expect(wrapper.find('[data-rd-pdf-help]').exists()).toBe(true)
    expect(wrapper.find('[data-rd-pdf-page]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-tools]').exists()).toBe(false)
  })

  it('【読書記録を保存】はツールバーの右端にあり、書名と進捗は【読書状況】カードにある', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })

    // ツールバーの一番右（縮小・拡大・ズーム切替の右）に置く
    const toolbar = wrapper.get('.rrd-viewer-card .card__header .rrd-toolbar')
    const buttons = toolbar.findAll('button')
    expect(buttons.at(-1)?.attributes('data-rd-log')).toBeDefined()
    expect(buttons.at(-1)?.text()).toContain('読書記録を保存')
    expect(buttons.at(-2)?.attributes('data-rd-fit-mode')).toBeDefined()
    expect(toolbar.find('[data-rd-zoom-in]').exists()).toBe(true)

    // 「本棚へ戻る」とツールバーは本文の上に残す（縦を食わない 1 行）
    const head = wrapper.get('.rrd-viewer-card .card__header')
    expect(head.find('[data-rd-back]').exists()).toBe(true)
    expect(head.find('[data-rd-page-next]').exists()).toBe(true)
    expect(head.find('[data-rd-page-input]').exists()).toBe(true)

    // 書名・バッジ・進捗は【読書状況】カードの中（右カラム）
    const side = wrapper.get('.rrd-side')
    const status = side.findAll('.card').find((card) => card.text().includes('読書状況'))
    expect(status).toBeTruthy()
    expect(status?.text()).toContain('The Secret Garden')
    expect(status?.text()).toContain('英語')
    expect(status?.find('[data-rd-status-badge]').exists()).toBe(true)
    expect(status?.find('[data-rd-progress]').exists()).toBe(true)
    // 進捗は 1 か所だけ（重複させない）
    expect(wrapper.findAll('[data-rd-progress]').length).toBe(1)
    expect(side.find('[data-rd-log]').exists()).toBe(false)

    // 本文の上にあったカードは無くなっている（PDF の表示領域を広く取る）
    expect(wrapper.find('.rrd-head').exists()).toBe(false)
  })
})

describe('書籍閲覧: 標記', () => {
  it('本文を選択すると、正規化した座標つきで標記を登録し、レイヤーに重ねる', async () => {
    const { wrapper, fetchMock } = await setup({ mode: 'viewer' })
    stubPageBox(wrapper)

    await wrapper.get('[data-rd-tool="highlight"]').trigger('click')
    await selectText(wrapper, 'Harry Potter')

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.includes('/marks'))
    expect(post?.url).toBe('/api/user/reading/books/1/marks')
    expect(post?.body).toMatchObject({
      pageNo: 1,
      markType: 'highlight',
      targetText: 'Harry Potter',
      color: '#f5c518',
      positionX: 0.25,
      positionY: 0.125,
      width: 0.2,
      height: 0.025
    })

    // 標記レイヤーに位置が反映される（left 25% / top 12.5% / width 20% / height 2.5%）
    const drawn = wrapper.get('[data-rd-mark-layer] [data-rd-mark-rect="99"]')
    expect(drawn.attributes('style')).toContain('left: 25%')
    expect(drawn.attributes('style')).toContain('top: 12.5%')
    expect(drawn.attributes('style')).toContain('width: 20%')
    expect(drawn.attributes('style')).toContain('height: 2.5%')
  })

  it('ツールを選んでページをクリックすると、その位置に標記を作る（メモはダイアログで内容を入れる）', async () => {
    const { wrapper, fetchMock } = await setup({ mode: 'viewer' })
    stubPageBox(wrapper)

    await wrapper.get('[data-rd-tool="memo"]').trigger('click')
    // ページ枠（600x800・左上 100,50）の 20% / 20% をクリック
    await wrapper.get('[data-rd-pdf-page]').trigger('click', { clientX: 220, clientY: 210 })
    await settle()

    expect(wrapper.find('[data-rd-mark-dialog]').exists()).toBe(true)
    await wrapper.get('#rdMarkContent').setValue('あとで調べる')
    await wrapper.get('[data-rd-mark-save]').trigger('click')
    await settle()

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.includes('/marks'))
    expect(post?.body).toMatchObject({
      pageNo: 1, markType: 'memo', content: 'あとで調べる', positionX: 0.2, positionY: 0.2, width: 0.18, height: 0.04
    })
  })

  it('語彙は選択した語を見出し語にして、意味をダイアログで入れて登録する', async () => {
    const { wrapper, fetchMock } = await setup({ mode: 'viewer' })
    stubPageBox(wrapper)

    await wrapper.get('[data-rd-tool="vocabulary"]').trigger('click')
    await selectText(wrapper, 'plume')

    expect(wrapper.find('[data-rd-mark-dialog]').exists()).toBe(true)
    expect((wrapper.get('#rdMarkTarget').element as HTMLInputElement).value).toBe('plume')

    await wrapper.get('#rdMarkContent').setValue('羽毛')
    await wrapper.get('[data-rd-mark-save]').trigger('click')
    await settle()

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.includes('/marks'))
    expect(post?.body).toMatchObject({
      pageNo: 1, markType: 'vocabulary', targetText: 'plume', content: '羽毛', color: '#7a4de8'
    })
  })

  it('本の言語でツールが変わる（英語＝語彙 / 中国語＝読み方 / 日本語＝語彙なし）', async () => {
    // 英語の本は「語彙」
    const english = await setup({ mode: 'viewer' })
    const englishTools = english.wrapper.get('[data-rd-tools]')
    expect(englishTools.findAll('[data-rd-tool]').map((button) => button.attributes('data-rd-tool')))
      .toEqual(['vocabulary', 'highlight', 'underline', 'memo', 'pen'])
    expect(englishTools.get('[data-rd-tool="vocabulary"]').text()).toContain('語彙')
    expect(englishTools.get('[data-rd-tool="vocabulary"]').attributes('data-rd-tool-label')).toBe('語彙')

    // 中国語の本は同じフックのまま「読み方」
    const chinese = await setup({ mode: 'viewer', books: [book({ language: '中国語' })] })
    const chineseTools = chinese.wrapper.get('[data-rd-tools]')
    expect(chineseTools.get('[data-rd-tool="vocabulary"]').text()).toContain('読み方')
    expect(chineseTools.get('[data-rd-tool="vocabulary"]').attributes('data-rd-tool-label')).toBe('読み方')

    // 日本語の本は語彙（引き当て）を出さない
    const japanese = await setup({ mode: 'viewer', books: [book({ language: '日本語' })] })
    expect(japanese.wrapper.findAll('[data-rd-tool]').map((button) => button.attributes('data-rd-tool')))
      .toEqual(['highlight', 'underline', 'memo', 'pen'])
  })

  it('語彙で語を選ぶと引き当てを呼び、意味を内容に入れられる', async () => {
    const { wrapper, fetchMock } = await setup({ mode: 'viewer' })
    stubPageBox(wrapper)

    await wrapper.get('[data-rd-tool="vocabulary"]').trigger('click')
    await selectText(wrapper, 'plume')
    await settle()

    // 英語の本なので language=英語 で引く
    const lookup = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.includes('/lookup'))
    expect(lookup?.body).toMatchObject({ text: 'plume', language: '英語' })

    const box = wrapper.get('[data-rd-lookup]')
    expect(box.get('[data-rd-lookup-status]').text()).toContain('EXCELAPI')
    expect(box.get('[data-rd-lookup-japanese]').text()).toContain('もしもし')
    expect(box.get('[data-rd-lookup-chinese]').text()).toContain('喂')

    // 「内容に入れる」で内容の欄に入る（あとから編集できる）
    await box.get('[data-rd-lookup-apply]').trigger('click')
    const content = (wrapper.get('#rdMarkContent').element as HTMLTextAreaElement).value
    expect(content).toContain('もしもし')
    expect(content).toContain('喂')

    // 引き当てが取れないときは message を出し、手入力で保存できる
    const failing = await setup({
      mode: 'viewer',
      lookupResponse: () => ok({
        text: 'zzz', language: '英語', japanese: null, chinese: null, pinyin: null,
        explanation: null, source: null, cached: false, message: '辞書から意味を取得できませんでした。'
      })
    })
    stubPageBox(failing.wrapper)
    await failing.wrapper.get('[data-rd-tool="vocabulary"]').trigger('click')
    await selectText(failing.wrapper, 'zzz')
    await settle()
    expect(failing.wrapper.find('[data-rd-lookup-apply]').exists()).toBe(false)
    expect(failing.wrapper.get('[data-rd-lookup-note]').text()).toContain('取得できませんでした')

    await failing.wrapper.get('#rdMarkContent').setValue('手で入れた意味')
    await failing.wrapper.get('[data-rd-mark-save]').trigger('click')
    await settle()
    expect(recorded(failing.fetchMock).find(
      (call) => call.method === 'POST' && call.url.includes('/marks')
    )?.body).toMatchObject({ targetText: 'zzz', content: '手で入れた意味' })
  })

  it('中国語の本の「読み方」は拼音と解説を引く', async () => {
    const { wrapper, fetchMock } = await setup({
      mode: 'viewer',
      books: [book({ language: '中国語' })],
      lookupResponse: () => ok({
        text: '汉字', language: '中国語', japanese: null, chinese: null,
        pinyin: 'hàn zì', explanation: '汉字；中国字；', source: 'YOUDAO', cached: true,
        message: '辞書のキャッシュから返しました。'
      })
    })
    stubPageBox(wrapper)

    await wrapper.get('[data-rd-tool="vocabulary"]').trigger('click')
    await selectText(wrapper, '汉字')
    await settle()

    expect(recorded(fetchMock).find((call) => call.method === 'POST' && call.url.includes('/lookup'))?.body)
      .toMatchObject({ text: '汉字', language: '中国語' })
    const box = wrapper.get('[data-rd-lookup]')
    expect(box.get('[data-rd-lookup-pinyin]').text()).toBe('hàn zì')
    // ピンインの行は「読み方」の主役として、専用の見た目にする（大きさ・色は tokens の変数だけ）
    expect(box.get('[data-rd-lookup-pinyin]').element.closest('.rrd-lookup__row--pinyin')).not.toBeNull()
    const css = readReaderCss()
    const pinyin = /\.rrd-lookup__row--pinyin dd\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    const others = /\.rrd-lookup__row dd\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(pinyin).toMatch(/font-size:\s*var\(--fs-2xl\)/)
    expect(pinyin).toMatch(/color:\s*var\(--color-primary\)/)
    expect(pinyin).toMatch(/font-weight:\s*var\(--fw-bold\)/)
    expect(others).toMatch(/font-size:\s*var\(--fs-md\)/)
    expect(box.get('[data-rd-lookup-explanation]').text()).toContain('汉字')
    // キャッシュから返ったことも分かる
    expect(box.get('[data-rd-lookup-status]').text()).toContain('キャッシュ')
  })

  it('標記の入力ダイアログは文字をひと回り大きくする（tokens の変数だけを使う）', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })
    stubPageBox(wrapper)
    await wrapper.get('[data-rd-tool="memo"]').trigger('click')
    await wrapper.get('[data-rd-pdf-page]').trigger('click')
    await settle()

    // ダイアログに専用のクラスを付けて、本文・ラベル・入力欄を大きくする
    expect(wrapper.get('[data-rd-mark-dialog]').classes()).toContain('rrd-mark-dialog')
    const css = readReaderCss()
    expect(css).toMatch(/\.rrd-mark-dialog \.dialog__title\s*\{[^}]*font-size:\s*var\(--fs-lg\)/)
    expect(css).toMatch(/\.rrd-mark-dialog \.dialog__body\s*\{[^}]*font-size:\s*var\(--fs-md\)/)
    expect(css).toMatch(/\.rrd-mark-dialog \.field__label\s*\{[^}]*font-size:\s*var\(--fs-md\)/)
    const inputs = /\.rrd-mark-dialog \.input,[\s\S]{0,80}?\.rrd-mark-dialog \.textarea\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(inputs).toMatch(/font-size:\s*var\(--fs-md\)/)
    expect(inputs).toMatch(/line-height:\s*var\(--lh-relaxed\)/)
    const hint = /\.rrd-mark-dialog \.rrd-hint,[\s\S]{0,80}?\.rrd-mark-dialog \.field__error\s*\{[^}]*\}/.exec(css)?.[0] ?? ''
    expect(hint).toMatch(/font-size:\s*var\(--fs-sm\)/)
    // 既存の標記のポップアップも同じように読みやすくする
    expect(css).toMatch(/\.rrd-popup__content\s*\{[^}]*font-size:\s*var\(--fs-md\)/)
    expect(css).toMatch(/\.rrd-popup__word\s*\{[^}]*font-size:\s*var\(--fs-md\)/)
    // 生の色・px を増やさない（大きさは tokens の変数で決める）
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    expect(css).not.toMatch(/\brgba?\(/)
  })

  it('標記をクリックすると内容を表示し、そこから削除できる', async () => {
    const { wrapper, fetchMock } = await setup({ mode: 'viewer' })

    await wrapper.get('[data-rd-mark-rect="11"]').trigger('click')
    await settle()
    const popup = wrapper.get('[data-rd-mark-popup]')
    expect(popup.text()).toContain('plume')
    expect(popup.text()).toContain('プルーム')

    await popup.get('[data-rd-mark-delete="11"]').trigger('click')
    await settle()
    expect(recorded(fetchMock).find((call) => call.method === 'DELETE')?.url).toBe('/api/user/reading/marks/11')
  })

  it('標記の色をツールごとに選べる（既定色・パレット・選んだ色で登録）', async () => {
    const { wrapper, fetchMock } = await setup({ mode: 'viewer' })
    stubPageBox(wrapper)

    // 何も選んでいないときはハイライトの既定色
    expect(wrapper.get('[data-rd-color-value]').text()).toBe('#f5c518')

    // ツールを選ぶと、そのツールの現在色（既定）になる
    await wrapper.get('[data-rd-tool="vocabulary"]').trigger('click')
    await settle()
    expect(wrapper.get('[data-rd-color-value]').text()).toBe('#7a4de8')

    // パレット（10 色）。そのツールの既定色が先頭に出る
    await wrapper.get('[data-rd-color-button]').trigger('click')
    await settle()
    const popover = wrapper.get('[data-rd-color-popover]')
    const swatches = popover.findAll('[data-rd-color]')
    expect(swatches.length).toBe(10)
    expect(swatches[0].attributes('data-rd-color')).toBe('#7a4de8')

    await popover.get('[data-rd-color="#2f9e6f"]').trigger('click')
    await settle()
    expect(wrapper.find('[data-rd-color-popover]').exists()).toBe(false)
    expect(wrapper.get('[data-rd-color-value]').text()).toBe('#2f9e6f')

    // ツールごとに色を覚える（ほかのツールは既定色のまま、戻ると変更色）
    await wrapper.get('[data-rd-tool="underline"]').trigger('click')
    await settle()
    expect(wrapper.get('[data-rd-color-value]').text()).toBe('#2f7bd6')
    await wrapper.get('[data-rd-tool="vocabulary"]').trigger('click')
    await settle()
    expect(wrapper.get('[data-rd-color-value]').text()).toBe('#2f9e6f')

    // 選んだ色で標記を作る（ハイライト）
    await wrapper.get('[data-rd-tool="highlight"]').trigger('click')
    await settle()
    await wrapper.get('[data-rd-color-button]').trigger('click')
    await wrapper.get('[data-rd-color="#e83e8c"]').trigger('click')
    await settle()
    expect(wrapper.get('[data-rd-color-value]').text()).toBe('#e83e8c')

    await selectText(wrapper, 'Harry Potter')
    const highlight = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.includes('/marks'))
    expect(highlight?.body).toMatchObject({ markType: 'highlight', color: '#e83e8c' })

    // 手書き（pen）も同じ色を使う（drawingData の color）
    await wrapper.get('[data-rd-tool="pen"]').trigger('click')
    await settle()
    await wrapper.get('[data-rd-color-button]').trigger('click')
    await wrapper.get('[data-rd-color="#111827"]').trigger('click')
    await settle()
    expect(wrapper.get('[data-rd-color-value]').text()).toBe('#111827')

    const page = wrapper.get('[data-rd-pdf-page]').element
    page.dispatchEvent(pointerEvent('pointerdown', 200, 200))
    page.dispatchEvent(pointerEvent('pointermove', 240, 230))
    page.dispatchEvent(pointerEvent('pointermove', 280, 260))
    page.dispatchEvent(pointerEvent('pointerup', 280, 260))
    await settle()

    const pen = recorded(fetchMock).filter((call) => call.method === 'POST' && call.url.includes('/marks')).at(-1)
    expect(pen?.body).toMatchObject({ markType: 'pen', color: '#111827' })
    expect(String(pen?.body?.drawingData)).toContain('#111827')
  })

  it('標記の一覧を種別ごとに件数つきで出し、クリックでそのページへ移動する', async () => {
    const { wrapper, pdf } = await setup({
      mode: 'viewer',
      bookMarks: [
        mark(),
        mark({ markId: 13, pageNo: 3, markType: 'memo', targetText: 'intricate', content: '難しい', color: '#e07b39' })
      ],
      detail: (page) => ({
        book: book({ currentPage: page }),
        records: [record()],
        marks: page === 1 ? [mark()] : [],
        markedPages: [1, 3]
      })
    })

    const list = wrapper.get('[data-rd-mark-list]')
    expect(list.text()).toContain('語彙')
    expect(list.text()).toContain('メモ')
    expect(list.get('[data-rd-mark-group-count="vocabulary"]').text()).toBe('1 件')

    await list.get('[data-rd-mark-item="13"]').trigger('click')
    await settle()
    expect(pdf.pages.at(-1)).toBe(3)
    expect((wrapper.get('[data-rd-page-input]').element as HTMLInputElement).value).toBe('3')
  })
})

describe('書籍閲覧: 読書記録と履歴', () => {
  it('読書記録を保存すると POST し、本棚と読書履歴を出し直す', async () => {
    const { wrapper, fetchMock } = await setup({ mode: 'viewer' })

    await wrapper.get('[data-rd-page-next]').trigger('click')
    await settle()
    await wrapper.get('[data-rd-log]').trigger('click')
    await settle()

    const dialog = wrapper.get('[data-rd-record-dialog]')
    expect((dialog.get('#rdRecStart').element as HTMLInputElement).value).toBe('1')
    expect((dialog.get('#rdRecEnd').element as HTMLInputElement).value).toBe('2')

    const historyBefore = historyCalls(fetchMock).length
    await dialog.get('#rdRecMinutes').setValue(35)
    await dialog.get('#rdRecMemo').setValue('2 ページまで読んだ')
    await dialog.get('[data-rd-record-save]').trigger('click')
    await settle()

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.includes('/records'))
    expect(post?.url).toBe('/api/user/reading/books/1/records')
    expect(post?.body).toMatchObject({ pageStart: 1, pageEnd: 2, minutes: 35, memo: '2 ページまで読んだ' })
    expect(wrapper.find('[data-rd-record-dialog]').exists()).toBe(false)
    // 保存後は本棚と履歴を取り直す（閲覧中はその本の履歴）
    expect(historyCalls(fetchMock).length).toBeGreaterThan(historyBefore)
    expect(lastHistoryQuery(fetchMock).get('bookId')).toBe('1')
    expect(recorded(fetchMock).some((call) => call.method === 'GET' && /\/reading\/books(\?|$)/.test(call.url))).toBe(true)
  })

  it('終了ページが開始ページより前なら送信しない', async () => {
    const { wrapper, fetchMock } = await setup({ mode: 'viewer' })

    await wrapper.get('[data-rd-log]').trigger('click')
    await settle()
    await wrapper.get('#rdRecStart').setValue(3)
    await wrapper.get('#rdRecEnd').setValue(2)
    await wrapper.get('[data-rd-record-save]').trigger('click')
    await settle()

    expect(recorded(fetchMock).some((call) => call.method === 'POST')).toBe(false)
    expect(wrapper.get('[data-rd-record-dialog]').text()).toContain('終了ページは開始ページ以上で入力してください。')
  })

  it('閲覧モードでは bookId 付きで履歴を読み、この本の履歴に同じ絞り込み・件数・ページングを出す', async () => {
    const { wrapper, fetchMock } = await setup({ mode: 'viewer' })

    // 閲覧中はその本の履歴だけを読む
    const requested = lastHistoryQuery(fetchMock)
    expect(requested.get('bookId')).toBe('1')
    expect(requested.get('size')).toBe('20')

    const own = wrapper.get('[data-rd-history]')
    expect(own.findAll('[data-rd-history-row]').length).toBe(1)
    expect(own.text()).toContain('P.1 - P.4')
    expect(own.text()).toContain('24 分')
    expect(own.text()).toContain('2026')

    // 全書籍の履歴は本棚の「読書履歴」タブへ移した（閲覧画面には出さない）
    expect(wrapper.find('[data-rd-history-all]').exists()).toBe(false)

    // この本の履歴でも同じ絞り込み・件数・ページングが使える（bookId も付く）
    expect(own.find('[data-rd-history-from]').exists()).toBe(true)
    expect(own.find('[data-rd-history-size]').exists()).toBe(true)
    expect(own.find('[data-rd-history-pager]').exists()).toBe(true)

    await own.get('[data-rd-history-from]').setValue('2026-08-01')
    await own.get('[data-rd-history-page-no]').setValue('2')
    await own.get('[data-rd-history-search]').trigger('click')
    await settle()

    const filtered = lastHistoryQuery(fetchMock)
    expect(filtered.get('bookId')).toBe('1')
    expect(filtered.get('dateFrom')).toBe('2026-08-01')
    expect(filtered.get('pageNo')).toBe('2')

    // 閲覧画面の履歴にも削除アイコンは置かない
    expect(own.find('[data-rd-record-delete]').exists()).toBe(false)
    expect(recorded(fetchMock).some((call) => call.method === 'DELETE')).toBe(false)

    // 全書籍の履歴は本棚のタブから読める
    await wrapper.get('[data-rd-back]').trigger('click')
    await settle()
    await wrapper.get('[data-rd-tab="history"]').trigger('click')
    await settle()
    const all = wrapper.get('[data-rd-history-all]')
    expect(all.findAll('[data-rd-history-row]').length).toBe(2)
    expect(all.text()).toContain('Lonely Planet China')
    expect(all.text()).toContain('電車の中で読んだ')
    expect(lastHistoryQuery(fetchMock).get('bookId')).toBeNull()
  })

  it('「本棚へ戻る」で本棚に戻る', async () => {
    const { wrapper, router } = await setup({ mode: 'viewer' })

    expect(wrapper.find('[data-rd-viewer]').exists()).toBe(true)
    await wrapper.get('[data-rd-back]').trigger('click')
    await settle()

    expect(router.currentRoute.value.query.bookId).toBeUndefined()
    // 戻る先は既定のタブ（自分の本棚）
    expect(wrapper.find('[data-rd-self-shelf]').exists()).toBe(true)
  })

  it('閲覧画面にも登録・修正・削除・アップロードは置かない', async () => {
    const { wrapper } = await setup({ mode: 'viewer' })

    expect(wrapper.find('[data-rd-add]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-edit]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-delete]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-upload-pdf]').exists()).toBe(false)
  })
})

describe('書籍閲覧: PDF 未登録', () => {
  it('実体が無い本は案内を出し、PDF を取りに行かない', async () => {
    const { wrapper, pdf } = await setup({ mode: 'viewer', bookId: 2 })

    const status = wrapper.get('[data-rd-pdf-status]').text()
    expect(status).toContain('PDF 未登録')
    expect(status).toContain('02.Lonely Planet China.pdf')

    const help = wrapper.get('[data-rd-pdf-help]').text()
    expect(help).toContain('書籍管理')
    expect(help).toContain('アップロード')
    expect(pdf.getDocument).not.toHaveBeenCalled()
  })

  it('PDF が 404 なら「PDF 未登録」と元ファイル名を出す', async () => {
    const { wrapper } = await setup({ mode: 'viewer', pdfFail: 'missing' })

    const status = wrapper.get('[data-rd-pdf-status]').text()
    expect(status).toContain('PDF 未登録')
    expect(status).toContain('01.The Secret Garden.pdf')
  })

  it('PDF の読み込みに失敗したら理由を出す', async () => {
    const { wrapper } = await setup({ mode: 'viewer', pdfFail: 'other' })

    expect(wrapper.get('[data-rd-pdf-status]').text()).toContain('PDF の表示に失敗しました')
  })
})

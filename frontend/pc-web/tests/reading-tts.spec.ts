import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import BookReaderView from '@/views/reading/BookReaderView.vue'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { ReadingBook, ReadingBookDetail, ReadingMark } from '@/api/reading'
import {
  TTS_CHUNK_CHARS,
  TTS_DEFAULT_RATE,
  TTS_STORAGE_KEY,
  buildTtsSegments,
  bookLangToTtsLang,
  chunkSentence,
  detectTextLang,
  rateLabel,
  readTtsPrefs,
  resolveVoice,
  segmentIndexAt,
  splitSentences,
  textOffsetOf,
  textOfItems,
  textRangeOf,
  voicesForLang,
  writeTtsPrefs
} from '@/features/reading/readingSpeech'

/**
 * 読書管理【書籍閲覧】の**音声読み上げ（TTS）**（phase 1・ブラウザ内蔵の音声合成だけ）。
 *
 * jsdom には `speechSynthesis` も `SpeechSynthesisUtterance` も無いので、**どちらもスタブする**。
 * 読み進めるのは `utterance.onend()` をテストから呼んで行い、タイマーには頼らない。
 * ハイライトは `Range#getClientRects` とページ枠の `getBoundingClientRect` を
 * テスト側で与える（jsdom にレイアウトが無いため。既存の reading-reader.spec.ts と同じ流儀）。
 */

/* ---------- テストデータ ---------- */

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

/** 本の言語ごとの本文（1 つの item が 1 行に相当する）。 */
const PAGE_TEXT: Record<'中国語' | '英語' | '日本語', string[]> = {
  中国語: ['这是一个句子。', '这是第二个句子。'],
  英語: ['This is the first sentence.', 'This is the second sentence.'],
  日本語: ['むかしむかし、あるところに。', 'おじいさんとおばあさんが。', 'This is a pen.']
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
    totalPages: 3,
    currentPage: 1,
    pinned: true,
    tags: [],
    summary: '',
    note: null,
    recentMinutes: 4,
    totalMinutes: 1576,
    markCount: 0,
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
    scope: 'GLOBAL',
    ownerFamilyId: null,
    ownerFamilyLabel: null,
    inMyShelf: true,
    ...overrides
  }
}

/* ---------- speechSynthesis のスタブ（jsdom には無い） ---------- */

interface VoiceLike {
  name: string
  lang: string
  voiceURI?: string
  localService?: boolean
  default?: boolean
}

/** 端末にある音声の例（日本語・英語・中国語。繁体字の中華民国の声も混ぜる）。 */
const DEVICE_VOICES: VoiceLike[] = [
  { name: 'Kyoko', lang: 'ja-JP', voiceURI: 'kyoko' },
  { name: 'Zira', lang: 'en-US', voiceURI: 'zira' },
  { name: 'Huihui', lang: 'zh-CN', voiceURI: 'huihui' },
  { name: 'Hanhan', lang: 'zh-TW', voiceURI: 'hanhan' }
]

/** スタブした `SpeechSynthesisUtterance`（必要なところだけ）。 */
class FakeUtterance {
  text: string
  lang = ''
  rate = TTS_DEFAULT_RATE
  voice: VoiceLike | null = null
  onend: (() => void) | null = null
  onerror: (() => void) | null = null

  constructor(text: string) {
    this.text = text
  }
}

interface Spoken {
  text: string
  lang: string
  rate: number
  voice: string | null
}

let voices: VoiceLike[] = [...DEVICE_VOICES]
let spoken: Spoken[] = []
/** まだ終わっていない utterance（`onend()` を呼ぶまで残る）。 */
let pending: FakeUtterance[] = []
let cancels = 0
let voicesChangedHandlers: (() => void)[] = []

function stubSpeech(): void {
  const synthesis = {
    speak: (utterance: FakeUtterance) => {
      spoken.push({
        text: utterance.text,
        lang: utterance.lang,
        rate: utterance.rate,
        voice: utterance.voice?.name ?? null
      })
      pending.push(utterance)
    },
    cancel: () => {
      cancels += 1
      pending = []
    },
    getVoices: () => voices,
    addEventListener: (type: string, listener: () => void) => {
      if (type === 'voiceschanged') voicesChangedHandlers.push(listener)
    },
    removeEventListener: () => undefined
  }
  Object.defineProperty(window, 'speechSynthesis', { configurable: true, writable: true, value: synthesis })
  Object.defineProperty(window, 'SpeechSynthesisUtterance', {
    configurable: true, writable: true, value: FakeUtterance
  })
}

/** いま話している utterance を終わらせる（次の文が話し始める）。 */
async function finishSpeaking(): Promise<void> {
  const utterance = pending.shift()
  utterance?.onend?.()
  await settle()
}

/** 端末の音声の一覧が変わったことにする（`voiceschanged`）。 */
async function fireVoicesChanged(): Promise<void> {
  for (const handler of voicesChangedHandlers) handler()
  await settle()
}

/* ---------- pdf.js の偽物（テキスト層に実際の span を作る） ---------- */

interface PdfStub {
  pages: number[]
  /** 実際に `getTextContent()` が返した items。 */
  contents: unknown[][]
}

interface PdfStubOptions {
  /** ページ番号 → 本文（item の並び）。 */
  texts?: Record<number, string[]>
  /** ページ番号に関係なく使う本文（1 ページだけの検証用）。 */
  text?: string[]
  numPages?: number
  fail?: 'missing' | 'other'
}

function stubPdfjs(options: PdfStubOptions = {}): PdfStub {
  const pages: number[] = []
  const contents: unknown[][] = []
  const numPages = options.numPages ?? 3
  const itemsFor = (pageNo: number): unknown[] => {
    const lines = options.texts?.[pageNo] ?? options.text ?? PAGE_TEXT['日本語']
    return lines.map((str) => ({ str, hasEOL: false, width: str.length * 8, transform: [1, 0, 0, 1, 0, 0] }))
  }

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
    render(): { promise: Promise<void> } {
      return { promise: Promise.resolve() }
    }
    async getTextContent(): Promise<{ items: unknown[] }> {
      const items = itemsFor(this.#pageNo)
      contents.push(items)
      return { items }
    }
  }

  class FakePdfDocument {
    readonly #brand = 'fake-document'
    readonly numPages: number
    constructor(count: number) {
      this.numPages = count
    }
    async getPage(pageNo: number): Promise<FakePdfPage> {
      if (this.#brand !== 'fake-document') throw new Error('private field')
      pages.push(pageNo)
      return new FakePdfPage(pageNo)
    }
  }

  const lib = {
    GlobalWorkerOptions: { workerSrc: '' },
    getDocument: () => ({
      promise: options.fail
        ? Promise.reject(options.fail === 'missing'
          ? Object.assign(new Error('Unexpected server response (404) while retrieving PDF'), { name: 'MissingPDFException' })
          : new Error('壊れた PDF'))
        : Promise.resolve(new FakePdfDocument(numPages))
    }),
    /** 本物と同じく、item の `str` を span にしてテキスト層へ入れる。 */
    renderTextLayer: (params: { container: HTMLElement; textContentSource: { items: unknown[] } }) => {
      const layer = params.container
      layer.innerHTML = ''
      for (const item of params.textContentSource.items) {
        const str = (item as { str?: unknown }).str
        if (typeof str !== 'string') continue
        const span = document.createElement('span')
        span.textContent = str
        layer.appendChild(span)
      }
      return { promise: Promise.resolve() }
    }
  }
  ;(window as unknown as { pdfjsLib?: unknown }).pdfjsLib = lib
  return { pages, contents }
}

/* ---------- レイアウト（jsdom には無いのでテスト側で与える） ---------- */

/** ページ枠（600x800）の左上を (100, 50) とみなす。 */
const PAGE_BOX = { left: 100, top: 50, width: 600, height: 800 }

function rect(left: number, top: number, width: number, height: number): DOMRect {
  return {
    left, top, width, height, right: left + width, bottom: top + height, x: left, y: top,
    toJSON: () => ({})
  } as DOMRect
}

/** `Range#getClientRects` が返す矩形（文が変わったことを見るために差し替える）。 */
let rangeRects: DOMRect[] = []

function stubLayoutApis(): void {
  Object.defineProperty(Range.prototype, 'getClientRects', {
    configurable: true, writable: true, value: () => rangeRects
  })
  Object.defineProperty(Range.prototype, 'getBoundingClientRect', {
    configurable: true, writable: true, value: () => rangeRects[0] ?? rect(0, 0, 0, 0)
  })
  const context = { setTransform: vi.fn(), clearRect: vi.fn() }
  HTMLCanvasElement.prototype.getContext = vi.fn(() => context) as unknown as typeof HTMLCanvasElement.prototype.getContext
}

/**
 * 本文（テキスト層）の中の一部分を選ぶ（連結した本文の `start`〜`end` 文字目）。
 * 読み上げが「選択の起点を含む文から」始まることの検証に使う。
 */
function selectLayerText(wrapper: VueWrapper, start: number, end: number): string {
  const layer = wrapper.get('[data-rd-pdf-text-layer]').element as HTMLElement
  const nodes: Text[] = []
  const walker = document.createTreeWalker(layer, 4 /* NodeFilter.SHOW_TEXT */)
  for (let node = walker.nextNode(); node !== null; node = walker.nextNode()) nodes.push(node as Text)
  const range = document.createRange()
  let cursor = 0
  let started = false
  for (const node of nodes) {
    const next = cursor + node.data.length
    if (!started && start >= cursor && start < next) {
      range.setStart(node, start - cursor)
      started = true
    }
    if (started && end <= next) {
      range.setEnd(node, end - cursor)
      break
    }
    cursor = next
  }
  const selection = window.getSelection()
  selection?.removeAllRanges()
  selection?.addRange(range)
  return selection?.toString() ?? ''
}

/**
 * 本文（テキスト層）の中で選ぶ。
 * `to` を渡さなければ `from` ちょうど、渡せば `from` の途中から `to` の終わりまでを選ぶ。
 */
function selectFrom(wrapper: VueWrapper, from: string, offsetInFrom = 0, to?: string): string {
  const layer = wrapper.get('[data-rd-pdf-text-layer]').element as HTMLElement
  const text = layer.textContent ?? ''
  const at = text.indexOf(from)
  expect(at).toBeGreaterThanOrEqual(0)
  const end = to === undefined ? at + from.length : text.indexOf(to, at) + to.length
  return selectLayerText(wrapper, at + offsetInFrom, end)
}

/** ページ枠に実寸を与える（jsdom は 0 を返すため）。PDF が無いときは何もしない。 */
function stubPageBox(wrapper: VueWrapper): void {
  const page = wrapper.find('[data-rd-pdf-page]')
  if (!page.exists()) return
  page.element.getBoundingClientRect = () => rect(PAGE_BOX.left, PAGE_BOX.top, PAGE_BOX.width, PAGE_BOX.height)
}

function stubStageLayout(wrapper: VueWrapper): void {
  const stage = wrapper.get('[data-rd-stage]').element as HTMLElement
  Object.defineProperty(stage, 'clientWidth', { configurable: true, value: 900 })
  stage.getBoundingClientRect = () => rect(0, 0, 900, 0)
}

/** 画面のスタイル（jsdom は CSS を適用しないので、定義そのものを確認する）。 */
function readReaderCss(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/features/reading/reading-reader.css'), 'utf8')
}

function readIconsSvg(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/assets/icons/icons.svg'), 'utf8')
}

/* ---------- 画面のセットアップ ---------- */

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

interface SetupOptions {
  language?: '中国語' | '英語' | '日本語'
  /** ページ番号 → 本文（既定は本の言語の本文）。 */
  texts?: Record<number, string[]>
  /** 全ページ同じ本文にする（テキスト層なしの検証は `[]`）。 */
  text?: string[]
  pdfFail?: 'missing' | 'other'
  bookMarks?: ReadingMark[]
  voices?: VoiceLike[]
  /** `speechSynthesis` が無いブラウザを作る（スタブを入れない）。 */
  withoutSpeech?: boolean
}

/** afterEach で確実に unmount する（document の mouseup 購読を残さない）。 */
let mounted: VueWrapper | null = null

async function settle(times = 4): Promise<void> {
  for (let index = 0; index < times; index += 1) await flushPromises()
}

async function setup(options: SetupOptions = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().login('STUDENT', 'テスト利用者')
  const router = createAppRouter()
  await router.push('/student/reading-reader?bookId=1')
  await router.isReady()

  const language = options.language ?? '日本語'
  const target = book({ language, title: `${language}の本`, markCount: options.bookMarks?.length ?? 0 })
  const marks = options.bookMarks ?? []
  const pdf = stubPdfjs({
    numPages: 3,
    texts: options.texts ?? (options.text ? undefined : { 1: PAGE_TEXT[language], 2: PAGE_TEXT[language], 3: PAGE_TEXT[language] }),
    text: options.text,
    fail: options.pdfFail
  })

  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target_ = String(url)
    const method = (init?.method ?? 'GET').toUpperCase()
    if (method === 'GET' && target_.includes('/reading/categories')) return ok({ items: [] })
    if (method === 'GET' && /\/reading\/books\/\d+\/marks/.test(target_)) {
      return ok({ items: marks, totalElements: marks.length, page: 1, size: 200, totalPages: 1 })
    }
    if (method === 'GET' && /\/reading\/records/.test(target_)) {
      return ok({ items: [], totalElements: 0, page: 1, size: 20, totalPages: 1 })
    }
    if (method === 'GET' && /\/reading\/books\/\d+(\?|$)/.test(target_)) {
      const detail: ReadingBookDetail = { book: target, records: [], marks, markedPages: [] }
      return ok(detail)
    }
    if (method === 'GET' && /\/reading\/books(\?|$)/.test(target_)) {
      return ok({ items: [target], totalElements: 1, page: 1, size: 24, totalPages: 1, totals: null })
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)

  stubSpeech()
  if (options.withoutSpeech) {
    delete (window as unknown as { speechSynthesis?: unknown }).speechSynthesis
    delete (window as unknown as { SpeechSynthesisUtterance?: unknown }).SpeechSynthesisUtterance
  }
  if (options.voices) voices = options.voices

  const wrapper = mount(BookReaderView, { global: { plugins: [pinia, router] }, attachTo: document.body })
  mounted = wrapper
  await settle()
  stubPageBox(wrapper)
  stubStageLayout(wrapper)
  return { wrapper, fetchMock, pdf, router }
}

beforeEach(() => {
  useToast().items.splice(0)
  window.sessionStorage.clear()
  window.localStorage.clear()
  window.scrollTo = vi.fn()
  voices = [...DEVICE_VOICES]
  spoken = []
  pending = []
  cancels = 0
  voicesChangedHandlers = []
  // ハイライトの矩形（ページ枠の中。左 25% / 上 12.5% / 幅 20% / 高さ 2.5%）
  rangeRects = [rect(250, 150, 120, 20)]
  stubLayoutApis()
})

afterEach(() => {
  mounted?.unmount()
  mounted = null
  window.getSelection()?.removeAllRanges()
  delete (window as unknown as { pdfjsLib?: unknown }).pdfjsLib
  delete (window as unknown as { speechSynthesis?: unknown }).speechSynthesis
  delete (window as unknown as { SpeechSynthesisUtterance?: unknown }).SpeechSynthesisUtterance
  vi.unstubAllGlobals()
})

/* ---------- 文の分割と言語の判定（readingSpeech.ts） ---------- */

describe('音声読み上げ: 文の分割と言語の判定', () => {
  it('文の終わり（。．.！!？?；;）で分ける', () => {
    expect(splitSentences('これは一つ目です。これは二つ目です！', 'ja-JP'))
      .toEqual(['これは一つ目です。', 'これは二つ目です！'])
    expect(splitSentences('This is one. Is this two? Yes!', 'en-US'))
      .toEqual(['This is one.', 'Is this two?', 'Yes!'])
  })

  it('Intl.Segmenter が無い環境でも句読点で分ける（フォールバック）', () => {
    const original = (Intl as unknown as { Segmenter?: unknown }).Segmenter
    try {
      delete (Intl as unknown as { Segmenter?: unknown }).Segmenter
      expect(splitSentences('これは一つ目です。これは二つ目です。', 'ja-JP'))
        .toEqual(['これは一つ目です。', 'これは二つ目です。'])
      expect(splitSentences('第一句。第二句！', 'zh-CN')).toEqual(['第一句。', '第二句！'])
    } finally {
      ;(Intl as unknown as { Segmenter?: unknown }).Segmenter = original
    }
  })

  it('長すぎる文は読点・カンマで分ける（Chrome の長文カット対策）', () => {
    const long = `${'あ'.repeat(80)}、${'い'.repeat(80)}、${'う'.repeat(80)}`
    const chunks = chunkSentence(long, 100)
    expect(chunks.length).toBeGreaterThan(1)
    expect(chunks.every((chunk) => chunk.length <= 100)).toBe(true)
    expect(chunks.join('')).toBe(long)
    // 既定は 120 字
    expect(chunkSentence('あ'.repeat(TTS_CHUNK_CHARS)).length).toBe(1)
    expect(chunkSentence('あ'.repeat(TTS_CHUNK_CHARS + 1)).length).toBe(2)
  })

  it('文ごとに文字種で言語を決める（かな→日本語 / 漢字だけ→中国語 / ラテン→英語）', () => {
    expect(detectTextLang('これは日本語です。', 'ja')).toBe('ja')
    expect(detectTextLang('中文的句子。', 'ja')).toBe('zh')
    expect(detectTextLang('This is a pen.', 'ja')).toBe('en')
    expect(detectTextLang('12345', 'zh')).toBe('zh')
    // 短い漢字だけの断片（柱・ページ番号など）は本の言語に従う
    expect(detectTextLang('本文', 'ja')).toBe('ja')
    // 中国語の本のピンイン行は中国語の声のまま読む（設計 §2.2。phase 1 は許容）
    expect(detectTextLang('nǐ hǎo', 'zh')).toBe('zh')
  })

  it('本の言語 → 読み上げの言語（中国語→zh / 英語→en / 日本語→ja）', () => {
    expect(bookLangToTtsLang('中国語')).toBe('zh')
    expect(bookLangToTtsLang('英語')).toBe('en')
    expect(bookLangToTtsLang('日本語')).toBe('ja')
  })

  it('本文の文字列は pdf.js の items の並びどおりに組み立てる', () => {
    expect(textOfItems([{ str: 'あ' }, { str: 'い' }, { type: 'beginMarkedContent' }, { str: 'う' }])).toBe('あいう')
  })

  it('文ごとに BCP-47 の言語コードを付ける（混在の本文）', () => {
    const segments = buildTtsSegments('これは日本語です。This is a pen.', 'ja')
    expect(segments.map((segment) => [segment.text, segment.langCode]))
      .toEqual([['これは日本語です。', 'ja-JP'], ['This is a pen.', 'en-US']])
  })
})

/* ---------- 声の選択（readingSpeech.ts） ---------- */

describe('音声読み上げ: 声の選択と端末の設定', () => {
  it('完全一致の声を優先する（zh-CN があれば zh-TW より先に選ぶ）', () => {
    expect(resolveVoice(DEVICE_VOICES, 'zh-CN')?.name).toBe('Huihui')
    expect(voicesForLang(DEVICE_VOICES, 'zh-CN').map((voice) => voice.name)).toEqual(['Huihui', 'Hanhan'])
    expect(resolveVoice(DEVICE_VOICES, 'ja-JP')?.name).toBe('Kyoko')
    expect(resolveVoice(DEVICE_VOICES, 'en-US')?.name).toBe('Zira')
  })

  it('音声が無い言語では null（呼び出し側が理由を出す）', () => {
    expect(resolveVoice([{ name: 'Kyoko', lang: 'ja-JP' }], 'zh-CN')).toBe(null)
  })

  it('速さは 0.5〜2.0 に丸め、表示は「1.0×」の形にする', () => {
    expect(rateLabel(TTS_DEFAULT_RATE)).toBe('1.0×')
    expect(rateLabel(0.5)).toBe('0.5×')
    expect(rateLabel(2)).toBe('2.0×')
    expect(rateLabel(9)).toBe('2.0×')
    expect(rateLabel(0.1)).toBe('0.5×')
  })

  it('速さと声は localStorage にだけ保存する（DB には書かない）', () => {
    expect(readTtsPrefs()).toEqual({ rate: TTS_DEFAULT_RATE, voices: {} })
    writeTtsPrefs({ rate: 1.5, voices: { ja: 'Kyoko' } })
    expect(JSON.parse(String(window.localStorage.getItem(TTS_STORAGE_KEY))))
      .toEqual({ rate: 1.5, voices: { ja: 'Kyoko' } })
    expect(readTtsPrefs()).toEqual({ rate: 1.5, voices: { ja: 'Kyoko' } })
    // 壊れた値・知らない値は既定に戻す
    window.localStorage.setItem(TTS_STORAGE_KEY, '{')
    expect(readTtsPrefs()).toEqual({ rate: TTS_DEFAULT_RATE, voices: {} })
  })

  it('選択の起点の文字位置から、その文の番号を求める', () => {
    const layer = document.createElement('div')
    layer.innerHTML = '<span>一つ目です。</span><span>二つ目です。三つ目です。</span>'
    document.body.appendChild(layer)
    const segments = buildTtsSegments('一つ目です。二つ目です。三つ目です。', 'ja')
    expect(segments.length).toBe(3)
    const nodes = layer.querySelectorAll('span')
    // 2 つ目の span の「二つ目です。」の途中（span 内の 2 文字目）→ 2 文目
    expect(textOffsetOf(layer, nodes[1].firstChild as Text, 2)).toBe(6 + 2)
    expect(segmentIndexAt(layer, segments, 8)).toBe(1)
    // 3 つ目の文の中 → 3 文目
    expect(segmentIndexAt(layer, segments, 14)).toBe(2)
    // 先頭・本文の外
    expect(segmentIndexAt(layer, segments, 0)).toBe(0)
    expect(textOffsetOf(layer, document.body, 0)).toBe(null)
    layer.remove()
  })

  it('テキスト層の中から読み上げる文の位置を探す（複数のノードにまたがってもよい）', () => {
    const layer = document.createElement('div')
    layer.innerHTML = '<span>これは一つ目です。</span><span>これは二つ目です。</span>'
    document.body.appendChild(layer)
    expect(textRangeOf(layer, 'これは二つ目です。')?.toString()).toBe('これは二つ目です。')
    // 文が 2 つの span にまたがっても、開始と終了を別々のノードで持てる（Offset out of bound にしない）
    expect(textRangeOf(layer, '一つ目です。これは二つ目')?.toString()).toBe('一つ目です。これは二つ目')
    expect(textRangeOf(layer, '出てこない文')).toBe(null)
    layer.remove()
  })
})

/* ---------- 画面（BookReaderView）: 読み上げの基本 ---------- */

describe('音声読み上げ: 本文ツールバーのボタン', () => {
  it('「読み上げ」は浮いている標記ツールバーの中のアイコンボタンで、最後のボタンは「読書記録を保存」のまま', async () => {
    const { wrapper } = await setup()
    // 2026-09-15 の指示: 読み上げは標記ツールと同じ「浮いているツールバー」に移した（常に見える）
    const tools = wrapper.get('[data-rd-tools]')
    const tts = tools.get('[data-rd-tts]')
    // アイコンだけ（小喇叭）。意味は title / aria-label で補う
    expect(tts.text()).toBe('')
    expect(tts.get('use').attributes('href')).toBe('#i-speaker')
    expect(tts.attributes('aria-label')).toContain('読み上げ')
    expect(tts.attributes('title')).toContain('読み上げ')
    expect(tts.attributes('data-rd-tts-state')).toBe('idle')
    expect(tts.element.tagName).toBe('BUTTON')
    // 標記ツールのあとに、区切り線をはさんで置く
    expect(tools.get('.rrd-tools__sep').element.compareDocumentPosition(tts.element) &
      Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy()

    // 本文ツールバーには置かない（ページ送り・ズーム・記録だけ。最後のボタンは今までどおり）
    const toolbar = wrapper.get('.rrd-viewer-card .card__header .rrd-toolbar')
    expect(toolbar.find('[data-rd-tts]').exists()).toBe(false)
    expect(toolbar.findAll('button').at(-1)?.attributes('data-rd-log')).toBeDefined()

    // 「はやさ／こえ」はポップオーバー（既定は閉じている）
    expect(wrapper.find('[data-rd-tts-panel]').exists()).toBe(false)
    await wrapper.get('[data-rd-tts-settings]').trigger('click')
    const panel = wrapper.get('[data-rd-tts-panel]')
    expect(panel.find('[data-rd-tts-rate]').exists()).toBe(true)
    // 中国語・日本語・英語の声が選べる（この端末には 3 言語ぶんある）
    expect(panel.findAll('[data-rd-tts-voice]').length).toBe(2)
    expect(panel.get('[data-rd-tts-voice="ja"]').findAll('option').length).toBe(2) // 自動で選ぶ + Kyoko
  })

  it('速さの選択肢は 0.5×〜2.0×（0.25 刻み）', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-rd-tts-settings]').trigger('click')
    const options = wrapper.get('[data-rd-tts-rate]').findAll('option').map((option) => option.text())
    expect(options).toEqual(['0.5×', '0.75×', '1.0×', '1.25×', '1.5×', '1.75×', '2.0×'])
  })
})

describe('音声読み上げ: 現在のページを文ごとに順に読む', () => {
  it('日本語の本は ja-JP で文ごとに順に読み、同時に話すのは 1 つだけ', async () => {
    const { wrapper } = await setup({ language: '日本語' })
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(wrapper.get('[data-rd-tts]').attributes('data-rd-tts-state')).toBe('playing')
    // 読み上げ中は「停止」のアイコンと説明に変わる（アイコンだけのボタンなので）
    expect(wrapper.get('[data-rd-tts]').get('use').attributes('href')).toBe('#i-stop')
    expect(wrapper.get('[data-rd-tts]').attributes('title')).toContain('停止')
    expect(spoken.map((item) => item.text)).toEqual(['むかしむかし、あるところに。'])
    expect(spoken[0].lang).toBe('ja-JP')
    // 未 onend の utterance は常に 1 つ（逐次 enqueue）
    expect(pending.length).toBe(1)

    await finishSpeaking()
    expect(spoken.map((item) => item.text))
      .toEqual(['むかしむかし、あるところに。', 'おじいさんとおばあさんが。'])
    expect(pending.length).toBe(1)

    await finishSpeaking()
    // 混在: ラテン文字だけの文は英語の声になる
    expect(spoken[2]).toMatchObject({ text: 'This is a pen.', lang: 'en-US' })
  })

  it('中国語の本は zh-CN、英語の本は en-US で読む', async () => {
    const first = await setup({ language: '中国語' })
    await first.wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0]).toMatchObject({ text: '这是一个句子。', lang: 'zh-CN' })
    expect(spoken[0].voice).toBe('Huihui')
    first.wrapper.unmount()
    mounted = null
    spoken = []
    pending = []

    const second = await setup({ language: '英語' })
    await second.wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0].lang).toBe('en-US')
    expect(spoken[0].text).toContain('This is the first sentence.')
    expect(spoken[0].voice).toBe('Zira')
  })

  it('最後の文まで読み終わると停止の状態に戻る', async () => {
    const { wrapper } = await setup({ text: ['一つ目です。', '二つ目です。'] })
    await wrapper.get('[data-rd-tts]').trigger('click')
    await finishSpeaking()
    await finishSpeaking()
    await settle()
    expect(spoken.length).toBe(2)
    expect(wrapper.get('[data-rd-tts]').attributes('data-rd-tts-state')).toBe('idle')
    expect(wrapper.get('[data-rd-tts]').get('use').attributes('href')).toBe('#i-speaker')
    expect(wrapper.get('[data-rd-tts]').attributes('title')).toContain('読み上げ')
    expect(wrapper.find('[data-rd-tts-highlight]').exists()).toBe(false)
  })

  it('「停止」で cancel() が呼ばれ、そのあと話し続けない', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-rd-tts]').trigger('click')
    const before = cancels
    const spokenBefore = spoken.length
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(cancels).toBeGreaterThan(before)
    expect(wrapper.get('[data-rd-tts]').attributes('data-rd-tts-state')).toBe('idle')
    expect(wrapper.find('[data-rd-tts-highlight]').exists()).toBe(false)
    // 止めたあとに古い onend が来ても次を読まない
    await finishSpeaking()
    await settle()
    expect(spoken.length).toBe(spokenBefore)
  })

  it('本文を選んでいるときは、その選択の起点を含む文から読む', async () => {
    const { wrapper } = await setup()
    expect(selectFrom(wrapper, 'おじいさんとおばあさんが。')).toBe('おじいさんとおばあさんが。')
    await wrapper.get('[data-rd-tts]').trigger('click')
    // 1 文目ではなく、選んだ 2 文目から始まる
    expect(spoken.map((item) => item.text)).toEqual(['おじいさんとおばあさんが。'])
    await finishSpeaking()
    expect(spoken.map((item) => item.text))
      .toEqual(['おじいさんとおばあさんが。', 'This is a pen.'])
  })

  it('選択が複数の文にまたがるときは、選択の起点を含む文から読む', async () => {
    const { wrapper } = await setup()
    // 2 文目の途中から本文の終わりまで選ぶ（2 文目と 3 文目にまたがる）
    expect(selectFrom(wrapper, 'おじいさんとおばあさんが。', 3, 'This is a pen.')).toBe('さんとおばあさんが。This is a pen.')
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0]?.text).toBe('おじいさんとおばあさんが。')
    await finishSpeaking()
    expect(spoken.map((item) => item.text))
      .toEqual(['おじいさんとおばあさんが。', 'This is a pen.'])
  })

  it('選択が無ければ 1 文目から読む（既定のまま）', async () => {
    const { wrapper } = await setup()
    expect(window.getSelection()?.isCollapsed).toBe(true)
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0]?.text).toBe('むかしむかし、あるところに。')
  })

  it('本文の外（ツールバーなど）の選択は使わず、1 文目から読む', async () => {
    const { wrapper } = await setup()
    const outside = document.createElement('p')
    outside.textContent = 'ツールバーの文字です。'
    document.body.appendChild(outside)
    const range = document.createRange()
    range.selectNodeContents(outside)
    const selection = window.getSelection()
    selection?.removeAllRanges()
    selection?.addRange(range)
    expect(selection?.isCollapsed).toBe(false)
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0]?.text).toBe('むかしむかし、あるところに。')
    outside.remove()
  })

  it('標記ツールを選んでいるときは選択を使わず、1 文目から読む', async () => {
    const { wrapper } = await setup()
    // 標記ツールを選ぶ（本文の選択が無いので activeTool が立つ）
    await wrapper.get('[data-rd-tool="highlight"]').trigger('click')
    expect(wrapper.get('[data-rd-tool="highlight"]').classes()).toContain('is-active')
    // その状態で本文を選んでも（標記が消費する前の状態を作っても）、読み上げは先頭から
    selectFrom(wrapper, 'おじいさんとおばあさんが。')
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0]?.text).toBe('むかしむかし、あるところに。')
  })

  it('本文の選択を消費しない（読み上げても選択は残る）', async () => {
    const { wrapper } = await setup()
    const layer = wrapper.get('[data-rd-pdf-text-layer]').element as HTMLElement
    const range = document.createRange()
    range.selectNodeContents(layer.querySelector('span') as HTMLElement)
    const selection = window.getSelection()
    selection?.removeAllRanges()
    selection?.addRange(range)
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(window.getSelection()?.isCollapsed).toBe(false)
    expect(window.getSelection()?.toString()).toBe('むかしむかし、あるところに。')
  })

  it('標記ツールを選ぶと読み上げを止める', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-rd-tts]').trigger('click')
    const before = cancels
    await wrapper.get('[data-rd-tool="highlight"]').trigger('click')
    expect(cancels).toBeGreaterThan(before)
    expect(wrapper.get('[data-rd-tts]').attributes('data-rd-tts-state')).toBe('idle')
    const spokenBefore = spoken.length
    await settle()
    expect(spoken.length).toBe(spokenBefore)
  })

  it('ページを送ると前のページの読み上げを止めて、新しいページを読む', async () => {
    const { wrapper } = await setup({
      texts: { 1: ['一つ目のページです。'], 2: ['二つ目のページです。'] }
    })
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0].text).toBe('一つ目のページです。')
    await wrapper.get('[data-rd-page-next]').trigger('click')
    await settle()
    expect(wrapper.get('[data-rd-tts]').attributes('data-rd-tts-state')).toBe('idle')
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken.at(-1)?.text).toBe('二つ目のページです。')
  })

  it('画面を離れると cancel() が呼ばれる', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-rd-tts]').trigger('click')
    const before = cancels
    wrapper.unmount()
    mounted = null
    expect(cancels).toBeGreaterThan(before)
  })
})

describe('音声読み上げ: はやさ・こえ（端末に覚える）', () => {
  it('速さを変えると localStorage に残り、次の文からその速さで読む', async () => {
    const { wrapper, fetchMock } = await setup()
    await wrapper.get('[data-rd-tts-settings]').trigger('click')
    await wrapper.get('[data-rd-tts-rate]').setValue('0.75')
    expect(JSON.parse(String(window.localStorage.getItem(TTS_STORAGE_KEY))).rate).toBe(0.75)
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0].rate).toBe(0.75)
    // 端末の設定なので DB には書かない（GET 以外の呼び出しが増えていない）
    expect(recorded(fetchMock).filter((call) => call.method !== 'GET')).toEqual([])
  })

  it('声を選ぶと localStorage に残り、その声で読む', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-rd-tts-settings]').trigger('click')
    await wrapper.get('[data-rd-tts-voice="ja"]').setValue('kyoko')
    expect(JSON.parse(String(window.localStorage.getItem(TTS_STORAGE_KEY))).voices).toEqual({ ja: 'kyoko' })
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0].voice).toBe('Kyoko')
  })

  it('中国語の声が無い端末では、中国語の理由を出す（日本語の本は読める）', async () => {
    const { wrapper } = await setup({
      language: '日本語',
      texts: { 1: ['これは日本語です。', '这是中文。'] },
      voices: [{ name: 'Kyoko', lang: 'ja-JP' }]
    })
    await wrapper.get('[data-rd-tts-settings]').trigger('click')
    const note = wrapper.get('[data-rd-tts-voice-note="zh"]')
    expect(note.text()).toContain('この端末に中国語の音声がありません')
    expect(wrapper.find('[data-rd-tts-voice="zh"]').exists()).toBe(false)
    // 日本語の声はあるので、読み上げ自体は使える
    expect(wrapper.get('[data-rd-tts]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-rd-tts]').trigger('click')
    expect(spoken[0]).toMatchObject({ text: 'これは日本語です。', lang: 'ja-JP' })
  })
})

describe('音声読み上げ: 読み上げ中の文のハイライト', () => {
  it('読んでいる文だけをハイライトし、文が進むと移る（DB には書かない）', async () => {
    const { wrapper, fetchMock } = await setup({ text: ['一つ目です。', '二つ目です。'] })
    const before = recorded(fetchMock).filter((call) => call.method !== 'GET').length
    await wrapper.get('[data-rd-tts]').trigger('click')
    const layer = wrapper.get('[data-rd-tts-highlight]')
    const rects = layer.findAll('.rrd-tts-highlight__rect')
    expect(rects.length).toBe(1)
    expect(rects[0].attributes('style')).toContain('left: 25%')
    // 標記ではない（標記レイヤーには入らない）
    expect(wrapper.findAll('[data-rd-mark-rect]').length).toBe(0)

    // 2 文目の位置が変われば、ハイライトも移る
    rangeRects = [rect(150, 250, 200, 24)] // x 8.3% / y 25% / w 33.3% / h 3%
    await finishSpeaking()
    const moved = wrapper.get('[data-rd-tts-highlight]').findAll('.rrd-tts-highlight__rect')
    expect(moved[0].attributes('style')).toContain('left: 8.3%')
    expect(moved[0].attributes('style')).toContain('top: 25%')

    // 読み上げで DB に書かない（POST / PUT / DELETE が増えていない）
    expect(recorded(fetchMock).filter((call) => call.method !== 'GET').length).toBe(before)
    // 標記も増えていない
    expect(wrapper.findAll('[data-rd-mark-rect]').length).toBe(0)
  })

  it('ハイライトは独立した層で、当たり判定を持たない（選択を邪魔しない）', async () => {
    const css = readReaderCss()
    const layer = css.match(/\.rrd-tts-highlight\s*\{[^}]*\}/)?.[0] ?? ''
    expect(layer).toContain('pointer-events: none')
    expect(layer).toContain('position: absolute')
    // 色は tokens.css の変数だけを使う（生の色は書かない）
    const rectRule = css.match(/\.rrd-tts-highlight__rect\s*\{[^}]*\}/)?.[0] ?? ''
    expect(rectRule).toContain('var(--color-primary)')
    expect(rectRule).not.toMatch(/#[0-9a-fA-F]{3,6}|rgba?\(|hsla?\(/)
    expect(readIconsSvg()).toContain('id="i-speaker"')
  })
})

describe('音声読み上げ: 使えないとき（日本語の理由を出して無効にする）', () => {
  it('音声が 1 つも無い端末ではボタンを無効にして理由を出す', async () => {
    const { wrapper } = await setup({ voices: [] })
    const button = wrapper.get('[data-rd-tts]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-rd-tts-note]').text()).toBe('この端末に音声がありません。読み上げはできません。')
    await button.trigger('click')
    expect(spoken.length).toBe(0)
  })

  it('テキスト層が無い（スキャンした PDF）ではボタンを無効にして理由を出す', async () => {
    const { wrapper } = await setup({ text: [] })
    expect(wrapper.get('[data-rd-tts]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-rd-tts-note]').text())
      .toBe('この PDF は文字を取り出せません（画像の PDF）。読み上げはできません。')
  })

  it('音声の一覧が後から届いても使えるようになる（voiceschanged）', async () => {
    const { wrapper } = await setup({ voices: [] })
    expect(wrapper.get('[data-rd-tts]').attributes('disabled')).toBeDefined()
    voices = [...DEVICE_VOICES]
    await fireVoicesChanged()
    expect(wrapper.get('[data-rd-tts]').attributes('disabled')).toBeUndefined()
    expect(wrapper.find('[data-rd-tts-note]').exists()).toBe(false)
  })

  it('PDF が無い本では、読み上げの理由ではなく既存の案内を出す', async () => {
    const { wrapper } = await setup({ pdfFail: 'missing' })
    expect(wrapper.find('[data-rd-pdf-help]').exists()).toBe(true)
    expect(wrapper.find('[data-rd-tts-note]').exists()).toBe(false)
    // 読み上げ（と標記ツール）は本文の枠の中に出すので、PDF が無いときは出さない
    expect(wrapper.find('[data-rd-tools]').exists()).toBe(false)
    expect(wrapper.find('[data-rd-tts]').exists()).toBe(false)
  })

  it('音声合成の API が無いブラウザではボタンを無効にして理由を出す', async () => {
    // 端末に音声はあるが、API 自体が無い場合（Firefox for Android など）
    const { wrapper } = await setup({ withoutSpeech: true })
    expect(wrapper.get('[data-rd-tts-note]').text())
      .toBe('このブラウザは音声読み上げ（Web Speech API）に対応していません。')
    expect(wrapper.get('[data-rd-tts]').attributes('disabled')).toBeDefined()
    expect(spoken.length).toBe(0)
  })
})

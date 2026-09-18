/**
 * 書籍閲覧（本文ビューア）の**音声読み上げ（TTS）**。
 *
 * phase 1 は**ブラウザ内蔵の音声合成**（Web Speech API）だけを使う（設計:
 * `tmp/reading-tts-design.md` §3.1・§4）。サーバー TTS は phase 3 なので、
 * ここでは**差し替えられるように小さな `ReadingTtsProvider`（実装は Browser だけ）**を定義し、
 * 文分割・文ごとの言語判定・音声の選択・端末ごとの設定（localStorage）をまとめる。
 *
 * <p>この file は **DOM とブラウザの音声合成にだけ**依存する（API も DB も触らない）。
 * `BookReaderView.vue` は「本文テキスト（pdf.js の `getTextContent()`）を渡す」→
 * 「返ってきた文を順に `speak()` する」→「読み終わったら次を `speak()` する」だけを行う。</p>
 */

/** 読み上げに使う言語（本の言語・文ごとの判定の結果）。 */
export type TtsLang = 'zh' | 'ja' | 'en'

/** 言語 → BCP-47 の言語コード（設計 §2.1。設定で上書きするのは phase 3）。 */
export const TTS_LANG_CODES: Record<TtsLang, string> = {
  zh: 'zh-CN',
  ja: 'ja-JP',
  en: 'en-US'
}

/** 言語 → 日本語の表示名（「声の選択」「理由」に出す）。 */
export const TTS_LANG_LABELS: Record<TtsLang, string> = {
  zh: '中国語',
  ja: '日本語',
  en: '英語'
}

/** 読み上げの速さ（設計 §5.3。0.5〜2.0 を 0.25 刻み）。 */
export const TTS_RATE_MIN = 0.5
export const TTS_RATE_MAX = 2
export const TTS_RATE_STEP = 0.25
/** 既定の速さ（設計 §6 の `READING_TTS_DEFAULT_RATE` = 100 ＝ 1.0×）。 */
export const TTS_DEFAULT_RATE = 1
/** 1 回に読み上げる塊の最大文字数（設計 §6 の `READING_TTS_CHUNK_CHARS` の既定 120）。 */
export const TTS_CHUNK_CHARS = 120
/** 声・速さを覚えておくキー（端末ごと。**サーバにも設定画面にも保存しない**）。 */
export const TTS_STORAGE_KEY = 'study21.reading.tts'

/** 話す速さの選択肢（0.5×〜2.0×）。 */
export const TTS_RATE_OPTIONS: readonly number[] = Array.from(
  { length: Math.round((TTS_RATE_MAX - TTS_RATE_MIN) / TTS_RATE_STEP) + 1 },
  (_, index) => Math.round((TTS_RATE_MIN + index * TTS_RATE_STEP) * 100) / 100
)

/* ---------- 文の分割（設計 §2.2） ---------- */

/** 文の終わりとして扱う記号（日本語の「。」と中国語の「。」、英語の「.」を同じ実装で扱う）。 */
const SENTENCE_END = '。．.！!？?；;'
/** 長すぎる文を分けるときの区切り（読点・カンマ・空白）。 */
const CHUNK_BREAKS = ['、', '，', ',', '；', ';', '：', ':', ' ']

/** かな（ひらがな・カタカナ）。あれば日本語の文とみなす。 */
const KANA_PATTERN = /[\p{Script=Hiragana}\p{Script=Katakana}]/u
/** 漢字（CJK 統合漢字）。かな無しで漢字だけなら中国語とみなす。 */
const HAN_PATTERN = /\p{Script=Han}/u
/** ラテン文字。 */
const LATIN_PATTERN = /[A-Za-z]/u

/**
 * 文を区切り文字ごとに分けるフォールバック
 * （`Intl.Segmenter` が無いブラウザ・環境で使う）。
 */
function splitSentencesByPattern(text: string): string[] {
  const pattern = new RegExp(`[^${SENTENCE_END}\\n]+[${SENTENCE_END}]*`, 'g')
  return (text.match(pattern) ?? []).map((part) => part.trim()).filter((part) => part !== '')
}

/**
 * 文単位に分ける。第一候補は `Intl.Segmenter`（Chrome 87+ / Safari 14.1+ / Firefox 125+）、
 * 使えなければ句読点の正規表現に落とす（設計 §2.2）。
 */
export function splitSentences(text: string, locale = TTS_LANG_CODES.ja): string[] {
  const source = String(text ?? '')
  if (source.trim() === '') return []
  const segmenter = intlSegmenter(locale)
  if (segmenter === null) return splitSentencesByPattern(source)
  const parts: string[] = []
  for (const item of segmenter.segment(source)) {
    const part = item.segment.trim()
    if (part !== '') parts.push(part)
  }
  // 分割できなかった（1 つも取れなかった）ときはフォールバックへ
  return parts.length > 0 ? parts : splitSentencesByPattern(source)
}

/** `Intl.Segmenter` があれば作る（無い環境では null）。 */
function intlSegmenter(locale: string): { segment: (text: string) => Iterable<{ segment: string }> } | null {
  const ctor = (Intl as unknown as { Segmenter?: unknown }).Segmenter
  if (typeof ctor !== 'function') return null
  try {
    const SegmenterClass = ctor as new (locale: string, options: { granularity: string }) =>
    { segment: (text: string) => Iterable<{ segment: string }> }
    return new SegmenterClass(locale, { granularity: 'sentence' })
  } catch {
    return null
  }
}

/**
 * 長すぎる文を読点・カンマ・空白で分ける。
 * Chrome は 1 つの utterance が長いと途中で止まる（約 15 秒問題）ため（設計 §2.2）。
 */
export function chunkSentence(text: string, maxChars = TTS_CHUNK_CHARS): string[] {
  const limit = Number.isFinite(maxChars) && maxChars > 0 ? Math.floor(maxChars) : TTS_CHUNK_CHARS
  let rest = String(text ?? '').trim()
  const chunks: string[] = []
  while (rest.length > limit) {
    const window = rest.slice(0, limit)
    let cut = 0
    for (const mark of CHUNK_BREAKS) {
      const at = window.lastIndexOf(mark)
      if (at + 1 > cut) cut = at + 1
    }
    const take = cut > 0 ? cut : limit
    const head = rest.slice(0, take).trim()
    if (head !== '') chunks.push(head)
    rest = rest.slice(take).trim()
  }
  if (rest !== '') chunks.push(rest)
  return chunks
}

/* ---------- 言語の判定（設計 §2.2） ---------- */

/** 本の言語（`ReadingBook.language`）→ 読み上げの言語。 */
export function bookLangToTtsLang(language: string | null | undefined): TtsLang {
  if (language === '中国語') return 'zh'
  if (language === '英語') return 'en'
  return 'ja'
}

/**
 * 文ごとに文字種で言語を決める。
 *
 * ・かながあれば日本語（漢字だけの日本語文は稀）
 * ・かな無しの漢字だけなら中国語
 * ・ラテン文字だけなら英語。ただし**中国語の本のピンイン行**は中国語の声のまま読む（設計 §2.2）
 * ・記号・数字だけなどは**本の言語**に従う
 */
export function detectTextLang(text: string, fallback: TtsLang): TtsLang {
  const source = String(text ?? '')
  if (KANA_PATTERN.test(source)) return 'ja'
  if (HAN_PATTERN.test(source)) {
    // 短い断片（見出し・柱・ページ番号など）は本の言語を優先する
    if (source.trim().length <= 3 && fallback === 'ja') return 'ja'
    return 'zh'
  }
  if (LATIN_PATTERN.test(source)) return fallback === 'zh' ? 'zh' : 'en'
  return fallback
}

/* ---------- 話す内容（文＋言語） ---------- */

/** 読み上げる 1 つの塊（文、または長い文を分けたもの）。 */
export interface TtsSegment {
  /** 読み上げる文字列。 */
  text: string
  /** 判定した言語。 */
  lang: TtsLang
  /** `SpeechSynthesisUtterance.lang` に入れる BCP-47 のコード。 */
  langCode: string
}

/** pdf.js の `getTextContent().items` から本文の文字列を作る（テキスト層と同じ順・同じ並び）。 */
export function textOfItems(items: readonly unknown[]): string {
  let out = ''
  for (const item of items) {
    if (item === null || typeof item !== 'object') continue
    const str = (item as { str?: unknown }).str
    if (typeof str === 'string') out += str
  }
  return out
}

/** 本文を「文 → 長すぎる文は分割」して、塊ごとに言語を付ける。 */
export function buildTtsSegments(text: string, bookLang: TtsLang, maxChars = TTS_CHUNK_CHARS): TtsSegment[] {
  const segments: TtsSegment[] = []
  for (const sentence of splitSentences(text, TTS_LANG_CODES[bookLang])) {
    for (const chunk of chunkSentence(sentence, maxChars)) {
      const lang = detectTextLang(chunk, bookLang)
      segments.push({ text: chunk, lang, langCode: TTS_LANG_CODES[lang] })
    }
  }
  return segments
}

/** 本文に出てくる言語（声の選択を出す対象。並びは 中国語 → 日本語 → 英語）。 */
export function segmentLangs(segments: readonly TtsSegment[]): TtsLang[] {
  const order: TtsLang[] = ['zh', 'ja', 'en']
  return order.filter((lang) => segments.some((segment) => segment.lang === lang))
}

/* ---------- 音声（voice）の選択（設計 §2.1・§5.3） ---------- */

/** 音声の情報（`SpeechSynthesisVoice` の必要な部分だけ）。 */
export interface TtsVoice {
  name: string
  lang: string
  voiceURI?: string
  localService?: boolean
  default?: boolean
}

/** `zh_CN` のような表記ゆれを `zh-cn` にそろえる。 */
function normalizeLangTag(lang: string | null | undefined): string {
  return String(lang ?? '').replace(/_/g, '-').toLowerCase()
}

/** その言語コードの音声を「完全一致 → 同じ言語（前方一致）」の順に並べて返す。 */
export function voicesForLang(voices: readonly TtsVoice[], code: string): TtsVoice[] {
  const wanted = normalizeLangTag(code)
  const base = wanted.split('-')[0]
  const same = voices.filter((voice) => normalizeLangTag(voice.lang).split('-')[0] === base)
  return [...same].sort((a, b) => {
    const exactA = normalizeLangTag(a.lang) === wanted ? 0 : 1
    const exactB = normalizeLangTag(b.lang) === wanted ? 0 : 1
    if (exactA !== exactB) return exactA - exactB
    const localA = a.localService === false ? 1 : 0
    const localB = b.localService === false ? 1 : 0
    if (localA !== localB) return localA - localB
    return a.name.localeCompare(b.name)
  })
}

/**
 * 言語コードに合う音声を 1 つ選ぶ。
 * 完全一致（`zh-CN`）→ 同じ言語（`zh-TW` など）の順に落とす（設計 §2.1）。
 */
export function resolveVoice(voices: readonly TtsVoice[], code: string): TtsVoice | null {
  return voicesForLang(voices, code)[0] ?? null
}

/** 覚えている声（`voiceURI` か名前）に一致する音声を探す。 */
export function findVoice(voices: readonly TtsVoice[], key: string | undefined): TtsVoice | null {
  if (!key) return null
  return voices.find((voice) => voice.voiceURI === key || voice.name === key) ?? null
}

/** 「声の選択」に出す 1 行（例 `Microsoft Nanami（ja-JP）`）。 */
export function voiceLabel(voice: TtsVoice): string {
  return `${voice.name}（${voice.lang}）`
}

/** その言語の音声が 1 つも無いときの理由（日本語。設計 §5.3・Q6）。 */
export function missingVoiceNote(lang: TtsLang): string {
  return `この端末に${TTS_LANG_LABELS[lang]}の音声がありません（サーバー読み上げは未対応です）。`
}

/* ---------- 端末ごとの設定（声・速さ。localStorage だけ。設計 §6） ---------- */

/** 端末に覚える読み上げの設定。 */
export interface TtsPrefs {
  /** 速さ（0.5〜2.0）。 */
  rate: number
  /** 言語ごとに選んだ声（`voiceURI` か名前）。 */
  voices: Partial<Record<TtsLang, string>>
}

/** 速さを 0.5〜2.0 の 0.25 刻みに丸める（壊れた値は既定の 1.0×）。 */
export function normalizeTtsRate(value: unknown): number {
  const raw = typeof value === 'number' ? value : Number.parseFloat(String(value ?? ''))
  if (!Number.isFinite(raw)) return TTS_DEFAULT_RATE
  const stepped = Math.round(raw / TTS_RATE_STEP) * TTS_RATE_STEP
  return Math.min(TTS_RATE_MAX, Math.max(TTS_RATE_MIN, Math.round(stepped * 100) / 100))
}

/** 速さの表示（`1.0×`）。 */
export function rateLabel(rate: number): string {
  return `${normalizeTtsRate(rate).toFixed(2).replace(/0$/, '')}×`
}

/** 覚えている設定を読む（保存できない環境・壊れた値は既定）。 */
export function readTtsPrefs(): TtsPrefs {
  const fallback: TtsPrefs = { rate: TTS_DEFAULT_RATE, voices: {} }
  try {
    const raw = window.localStorage.getItem(TTS_STORAGE_KEY)
    if (!raw) return fallback
    const parsed = JSON.parse(raw) as { rate?: unknown; voices?: Record<string, unknown> }
    const voices: Partial<Record<TtsLang, string>> = {}
    for (const lang of ['zh', 'ja', 'en'] as TtsLang[]) {
      const value = parsed.voices?.[lang]
      if (typeof value === 'string' && value.trim() !== '') voices[lang] = value
    }
    return { rate: normalizeTtsRate(parsed.rate), voices }
  } catch {
    return fallback
  }
}

/** 覚えている設定を書く（保存できない環境では何もしない）。 */
export function writeTtsPrefs(prefs: TtsPrefs): void {
  try {
    const voices: Partial<Record<TtsLang, string>> = {}
    for (const lang of ['zh', 'ja', 'en'] as TtsLang[]) {
      const value = prefs.voices[lang]
      if (typeof value === 'string' && value !== '') voices[lang] = value
    }
    window.localStorage.setItem(TTS_STORAGE_KEY, JSON.stringify({ rate: normalizeTtsRate(prefs.rate), voices }))
  } catch {
    // 保存できない環境（プライベートモードなど）では、その場の設定だけ使う
  }
}

/* ---------- テキスト層の文字列 ↔ 読み上げる文（ハイライト・選択位置） ---------- */

/** テキスト層の中のテキストノードを並び順に集める（ハイライトと選択位置の計算で使う）。 */
function textNodesOf(layer: HTMLElement): Text[] {
  const nodes: Text[] = []
  const walker = layer.ownerDocument.createTreeWalker(layer, 4 /* NodeFilter.SHOW_TEXT */)
  for (let node = walker.nextNode(); node !== null; node = walker.nextNode()) nodes.push(node as Text)
  return nodes
}

/**
 * テキスト層の中から `text` を探して `Range` にする（読み上げ中の文のハイライトに使う）。
 *
 * <p>文は pdf.js の `getTextContent()` から作るので、テキスト層の DOM と**同じ並び**になる。
 * ただし空白の入り方が実装によって少し違うことがあるため、
 * まず全体一致、次に先頭の一部で探し、見つからなければ **null（ハイライト無しで読み上げだけ続ける）**。</p>
 */
export function textRangeOf(layer: HTMLElement | null, text: string, from = 0): Range | null {
  const target = String(text ?? '').trim()
  if (!layer || target === '') return null
  const nodes = textNodesOf(layer)
  let value = ''
  for (const node of nodes) value += node.data
  if (value === '') return null
  let at = value.indexOf(target, from)
  if (at < 0) at = value.indexOf(target)
  let end = at < 0 ? 0 : at + target.length
  if (at < 0) {
    // 空白の入り方が pdf.js の実装で違うことがあるので、先頭の一部だけで探す
    const head = target.slice(0, 12)
    at = head.length > 0 ? value.indexOf(head, from) : -1
    if (at < 0 && head.length > 0) at = value.indexOf(head)
    if (at < 0) return null
    end = Math.min(value.length, at + target.length)
  }
  const bounds = locate(nodes, at, end)
  if (bounds === null) return null
  const range = layer.ownerDocument.createRange()
  range.setStart(bounds.startNode, bounds.startOffset)
  range.setEnd(bounds.endNode, bounds.endOffset)
  return range
}

/** 文字の位置（連結した本文の何文字目か）を、テキストノードとその中の位置に直す。 */
function locate(
  nodes: readonly Text[],
  start: number,
  end: number
): { startNode: Text; startOffset: number; endNode: Text; endOffset: number } | null {
  let cursor = 0
  let startNode: Text | null = null
  let startOffset = 0
  let endNode: Text | null = null
  let endOffset = 0
  for (const node of nodes) {
    const next = cursor + node.data.length
    if (startNode === null && start >= cursor && start < next) {
      startNode = node
      startOffset = start - cursor
    }
    if (endNode === null && end <= next) {
      endNode = node
      endOffset = end - cursor
      break
    }
    cursor = next
  }
  if (startNode === null || endNode === null) return null
  return { startNode, startOffset, endNode, endOffset }
}

/**
 * 本文（テキスト層）の選択の起点を「連結した本文の何文字目か」に直す。
 *
 * <p>`Range` の起点はテキストノードのこともあれば、レイヤーや span（要素）のこともある。
 * 選択が本文の外にあるときは **null**（＝読み上げは先頭から始める）。</p>
 */
export function textOffsetOf(layer: HTMLElement | null, container: Node, offset: number): number | null {
  if (!layer || !layer.contains(container)) return null
  const nodes = textNodesOf(layer)
  const position = Math.max(0, Math.trunc(offset))
  if (container.nodeType === 3 /* Node.TEXT_NODE */) {
    let cursor = 0
    for (const node of nodes) {
      if (node === container) return cursor + Math.min(position, node.data.length)
      cursor += node.data.length
    }
    return null
  }
  // 要素（レイヤー自体・span など）のときは、その子の位置から先頭のテキストまでを数える
  const reference = (container as Element).childNodes[position] ?? null
  let cursor = 0
  for (const node of nodes) {
    if (reference !== null && (node === reference || reference.contains(node))) return cursor
    cursor += node.data.length
  }
  return cursor
}

/**
 * その文字位置を**含む文**（塊）の番号を返す（選択の起点から読み始めるときに使う）。
 * 見つからないときは 0（先頭から読む）。
 */
export function segmentIndexAt(layer: HTMLElement | null, segments: readonly TtsSegment[], offset: number): number {
  if (!layer || segments.length === 0 || !Number.isFinite(offset)) return 0
  const value = layer.textContent ?? ''
  let from = 0
  let fallback = 0
  for (let index = 0; index < segments.length; index += 1) {
    const text = segments[index].text
    if (text === '') continue
    // 文は本文と同じ並びなので、前から順に探せば位置が分かる（見つからなければ順番だけ進める）
    let at = value.indexOf(text, from)
    if (at < 0) at = value.indexOf(text)
    if (at < 0) continue
    from = at + text.length
    if (offset >= at && offset < at + text.length) return index
    if (offset >= at) fallback = index
  }
  return fallback
}

// `document.createTreeWalker` の 4（SHOW_TEXT）を使うので NodeFilter に依存しない

/* ---------- provider（差し替えの受け口。phase 1 は Browser 実装だけ） ---------- */

/** 読み上げ 1 回ぶんの指示。 */
export interface TtsSpeakRequest {
  text: string
  /** BCP-47 の言語コード。 */
  lang: string
  voice: TtsVoice | null
  rate: number
  onend: () => void
  onerror?: () => void
}

/**
 * 読み上げの実装（provider）。
 * phase 1 は `BrowserTtsProvider` だけ。phase 3 でサーバー TTS を足すときは
 * **「1 回の朗読の間は provider を固定する」**（設計 §3.3）。
 */
export interface ReadingTtsProvider {
  /** 実装の名前（`browser` など。設定 `READING_TTS_PROVIDER` と同じ語）。 */
  readonly id: string
  /** この端末で使えるか（音声合成の API があるか）。 */
  isSupported(): boolean
  /** いま使える音声の一覧（無い端末では空）。 */
  listVoices(): TtsVoice[]
  /** 音声の一覧が変わったときに呼ばれる（初回は空配列のことがあるため）。解除の関数を返す。 */
  onVoicesChanged(handler: () => void): () => void
  /** 1 つ話す（終わったら `onend`、失敗したら `onerror`）。 */
  speak(request: TtsSpeakRequest): void
  /** いま話しているものと待っているものを全部やめる。 */
  stop(): void
}

/** 話すのに使うブラウザの API（テストで差し替えられるように分けてある）。 */
export interface TtsSynthesisLike {
  speak(utterance: unknown): void
  cancel(): void
  getVoices(): TtsVoice[]
  addEventListener?(type: string, listener: () => void): void
  removeEventListener?(type: string, listener: () => void): void
}

/** `SpeechSynthesisUtterance` の必要な部分だけ。 */
export interface TtsUtteranceLike {
  text: string
  lang: string
  rate: number
  voice: TtsVoice | null
  onend: (() => void) | null
  onerror: (() => void) | null
}

/** browser provider が使うものの組（`window` から取り出す）。 */
export interface TtsBrowserApis {
  synthesis: TtsSynthesisLike
  createUtterance: (text: string) => TtsUtteranceLike
}

/** `window` から音声合成の API を取り出す（無い環境では null）。 */
export function browserTtsApis(target: Window | null = typeof window === 'undefined' ? null : window): TtsBrowserApis | null {
  if (!target) return null
  const synthesis = (target as unknown as { speechSynthesis?: TtsSynthesisLike }).speechSynthesis
  const Utterance = (target as unknown as { SpeechSynthesisUtterance?: new (text: string) => TtsUtteranceLike })
    .SpeechSynthesisUtterance
  if (!synthesis || typeof synthesis.speak !== 'function' || typeof Utterance !== 'function') return null
  return {
    synthesis,
    createUtterance: (text: string) => new Utterance(text)
  }
}

/**
 * ブラウザ内蔵の音声合成で読み上げる provider（phase 1 の既定）。
 *
 * <p>Firefox for Android など `speechSynthesis` が無い環境では `isSupported()` が false になり、
 * 呼び出し側が**日本語の理由を出してボタンを無効にする**（設計 §3.1 の短所 1）。</p>
 */
export class BrowserTtsProvider implements ReadingTtsProvider {
  readonly id = 'browser'

  #apis: TtsBrowserApis | null

  constructor(apis: TtsBrowserApis | null = browserTtsApis()) {
    this.#apis = apis
  }

  isSupported(): boolean {
    return this.#apis !== null
  }

  listVoices(): TtsVoice[] {
    const apis = this.#apis
    if (!apis) return []
    try {
      const voices = apis.synthesis.getVoices()
      return Array.isArray(voices) ? voices : []
    } catch {
      return []
    }
  }

  onVoicesChanged(handler: () => void): () => void {
    const synthesis = this.#apis?.synthesis
    if (!synthesis || typeof synthesis.addEventListener !== 'function') return () => undefined
    // 初回の getVoices() は空配列のことがあるので `voiceschanged` を待つ（設計 §3.1 の短所 2）
    synthesis.addEventListener('voiceschanged', handler)
    return () => synthesis.removeEventListener?.('voiceschanged', handler)
  }

  speak(request: TtsSpeakRequest): void {
    const apis = this.#apis
    if (!apis) {
      request.onerror?.()
      return
    }
    const utterance = apis.createUtterance(request.text)
    utterance.text = request.text
    utterance.lang = request.lang
    utterance.rate = request.rate
    utterance.voice = request.voice
    utterance.onend = () => request.onend()
    utterance.onerror = () => {
      if (request.onerror) request.onerror()
      else request.onend()
    }
    apis.synthesis.speak(utterance)
  }

  stop(): void {
    try {
      this.#apis?.synthesis.cancel()
    } catch {
      // 停止できなくても画面の状態は戻す
    }
  }
}

/** 既定の provider（phase 1 はブラウザ内蔵だけ）。 */
export function createReadingTtsProvider(): ReadingTtsProvider {
  return new BrowserTtsProvider()
}

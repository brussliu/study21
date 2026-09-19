/**
 * 日本語勉強【単語情報管理】デモの**純粋な処理**。
 *
 * 画面（Vue）から切り離してテストできるように、ここには副作用を持たせない
 * （fetch もタイマーもストレージも使わない）。状態は `store/japaneseDemo.ts` が持つ。
 *
 * ここで実装している業務ルールのうち、正式開発でも同じ判断が要るもの:
 * ・貼り付けテキストの解釈（区切り・空白・重複・読みが複数あるもの）
 * ・入力順と容量による Unit の自動割り当て（**AI の意味分類ではない**）
 * ・重複収録の判定（同じ見出し語でも読みが違えば別の語）
 */

import type {
  DemoBook,
  DemoCollection,
  DemoDetailContent,
  DemoDisplayState,
  DemoPartOfSpeech,
  DemoWord
} from './types'

/* ------------------------------------------------------------------ */
/* 1. 貼り付けテキストの解釈                                            */
/* ------------------------------------------------------------------ */

/** 取り込み行の状態。 */
export type ParsedRowState =
  /** 取り込める */
  | 'OK'
  /** 見出し語が空（読み・意味だけの行） */
  | 'BLANK_WORD'
  /** 読みが空（見出し語から推測できない） */
  | 'BLANK_READING'
  /** 読みが複数書かれている（同表記・別読みの可能性） */
  | 'MULTI_READING'
  /** 形式が読み取れない（区切りが多すぎる等） */
  | 'FORMAT_ERROR'
  /** 同じ入力の中で重複している */
  | 'DUPLICATE'
  /** すでに母表にある語と重複している */
  | 'DUPLICATE_EXISTING'

/** 貼り付けを 1 行ずつ解釈した結果。 */
export interface ParsedRow {
  /** 入力の行番号（1 から） */
  line: number
  /** 入力そのままの文字列 */
  raw: string
  heading: string
  reading: string
  chineseMeaning: string
  /** 複数読みの候補（MULTI_READING のときだけ 2 つ以上） */
  readingCandidates: string[]
  state: ParsedRowState
  /** 画面に出す短い理由 */
  note: string
}

/**
 * 1 行を「見出し語／読み／中国語意味」に分ける。
 *
 * 区切りは分かりやすさを優先し、次の順で受け付ける:
 *   1. タブ
 *   2. カンマ（半角 , ／ 全角 ，）
 *   3. 連続する半角スペース 2 つ以上
 * 空白 1 つは読みと意味の区切りに使いたいので、区切りにはしない。
 */
export function splitLine(raw: string): string[] {
  const line = raw.replace(/\r$/, '')
  if (line.includes('\t')) {
    return line.split('\t')
  }
  if (line.includes('\uFF0C') || line.includes(',')) {
    return line.split(/[\uFF0C,]/)
  }
  if (/ {2,}/.test(line)) {
    return line.split(/ {2,}/)
  }
  return [line]
}

/**
 * 区切り文字が混ざっている行か（タブとカンマの両方がある）。
 *
 * どちらで区切るつもりだったか決められないので、勝手に解釈せずに形式エラーとして返す。
 */
export function isMixedDelimiter(raw: string): boolean {
  const hasTab = raw.includes('\t')
  const hasComma = raw.includes(',') || raw.includes('\uFF0C')
  return hasTab && hasComma
}

/** 複数読みらしい文字列か（「あく・ひらく」「あく/ひらく」など）。 */
export function splitReadings(value: string): string[] {
  if (value === '') {
    return []
  }
  return value
    .split(/[・/／、,，]|\s+または\s+/)
    .map((part) => part.trim())
    .filter((part) => part !== '')
}

/**
 * 貼り付けテキストを解釈する。
 *
 * `existing` には母表の（見出し語, 読み）を渡す。同じ見出し語でも読みが違えば別の語なので、
 * 重複の判定は **見出し語＋読み** の組み合わせで行う。
 */
export function parsePaste(
  text: string,
  existing: { heading: string; reading: string }[] = []
): ParsedRow[] {
  const rows = text
    .split('\n')
    .map((raw, index) => ({ raw, line: index + 1 }))
    // 空行は黙って飛ばす（行番号は入力のまま残す）
    .filter((entry) => entry.raw.trim() !== '')

  const seen = new Map<string, number>()
  const existingKeys = new Set(existing.map((word) => `${word.heading}\u0000${word.reading}`))

  return rows.map((entry) => {
    const parts = splitLine(entry.raw).map((part) => part.trim())
    const heading = parts[0] ?? ''
    const readingValue = parts[1] ?? ''
    const chineseMeaning = parts.slice(2).join(' ').trim()
    const readings = splitReadings(readingValue)
    const base = {
      line: entry.line,
      raw: entry.raw,
      heading,
      reading: readings[0] ?? '',
      chineseMeaning,
      readingCandidates: readings
    }

    if (heading === '') {
      return { ...base, state: 'BLANK_WORD' as const, note: '見出し語がありません。' }
    }
    // 区切りを決められない行は、勝手に解釈せずに人が直せる形で返す
    if (isMixedDelimiter(entry.raw)) {
      return {
        ...base,
        state: 'FORMAT_ERROR' as const,
        note: 'タブとカンマが混ざっています。どちらかの区切りに統一してください。'
      }
    }
    if (parts.length === 1 && /\s/.test(heading)) {
      return {
        ...base,
        state: 'FORMAT_ERROR' as const,
        note: '区切りが分かりません。見出し語・読み・意味をタブかカンマで区切ってください。'
      }
    }
    if (readings.length > 1) {
      return {
        ...base,
        state: 'MULTI_READING' as const,
        note: `読みが ${readings.length} つあります。どちらで登録するか選んでください（同じ表記でも読みが違えば別の単語です）。`
      }
    }
    if (readingValue === '') {
      return { ...base, state: 'BLANK_READING' as const, note: '読みが空です。入力するか、空のまま登録してください。' }
    }

    const key = `${heading}\u0000${readings[0]}`
    const firstLine = seen.get(key)
    if (firstLine !== undefined) {
      return { ...base, state: 'DUPLICATE' as const, note: `${firstLine} 行目と同じ単語です。` }
    }
    seen.set(key, entry.line)
    if (existingKeys.has(key)) {
      return {
        ...base,
        state: 'DUPLICATE_EXISTING' as const,
        note: 'すでに母表にある単語です。登録すると「収録を追加」になります。'
      }
    }
    return { ...base, state: 'OK' as const, note: '' }
  })
}

/** 取り込みの対象になる行か（人が選んで直せる行も含めて「取り込める」扱いにする）。 */
export function isImportable(row: ParsedRow): boolean {
  return row.state !== 'BLANK_WORD' && row.state !== 'FORMAT_ERROR' && row.state !== 'DUPLICATE'
}

/** 解釈の集計。 */
export function summarizeParsed(rows: ParsedRow[]): {
  importable: number
  blank: number
  duplicates: number
  errors: number
  multiReading: number
  existingReuse: number
} {
  return {
    importable: rows.filter(isImportable).length,
    blank: rows.filter((row) => row.state === 'BLANK_WORD' || row.state === 'BLANK_READING').length,
    duplicates: rows.filter((row) => row.state === 'DUPLICATE').length,
    errors: rows.filter((row) => row.state === 'FORMAT_ERROR').length,
    multiReading: rows.filter((row) => row.state === 'MULTI_READING').length,
    existingReuse: rows.filter((row) => row.state === 'DUPLICATE_EXISTING').length
  }
}

/* ------------------------------------------------------------------ */
/* 2. 書籍・Unit の自動割り当て                                        */
/* ------------------------------------------------------------------ */

/** Unit の割り当て方（画面で選ぶ 3 つ）。 */
export type UnitPlacement = 'CONTINUE' | 'NEW_UNIT' | 'NEW_BOOK'

/** 割り当ての入力。 */
export interface AllocationInput {
  /** 取り込む語（入力順に並んでいることが前提） */
  words: { heading: string; reading: string }[]
  /** 既存の書籍から選ぶとき（新規書籍なら null） */
  book: DemoBook | null
  /** 新規書籍の名前（書籍を選んだときは空） */
  newBookName: string
  /** 1 Unit あたりの語数 */
  unitSize: number
  placement: UnitPlacement
  /** いまの Unit の語数（未満の Unit を補うときに使う） */
  currentUnitCount: number
}

/** 割り当てた 1 語ぶん。 */
export interface AllocatedWord {
  heading: string
  reading: string
  /** 割り当て先の Unit 名（例: Unit003） */
  unit: string
  /** Unit の中での順番（1 から） */
  seq: number
}

/** Unit ごとのまとめ。 */
export interface AllocationSummary {
  unit: string
  count: number
  /** この Unit に割り当てた順番の範囲（表示用） */
  fromSeq: number
  toSeq: number
  /** 容量を超えてよいか（既存の未満 Unit を補うときは容量まで） */
  capacity: number
}

/** Unit 名を作る（`Unit001` の形）。 */
export function unitName(index: number): string {
  return `Unit${String(index).padStart(3, '0')}`
}

/** 既存の Unit 名から番号を取り出す（取れないときは null）。 */
export function unitIndex(name: string): number | null {
  const match = /^Unit0*(\d+)$/.exec(name.trim())
  return match ? Number(match[1]) : null
}

/**
 * 入力順と容量で Unit を割り当てる。
 *
 * **AI の意味分類ではない**（入力順に詰めるだけ）。正式開発でもこの割り当ては
 * 「教材の並び順を人が決める」ための補助なので、AI に置き換えない前提で設計している。
 *
 * ・新規書籍 … Unit001 から
 * ・既存の続きから … 最後の Unit が未満ならそこを埋めてから次の Unit
 * ・新しい Unit から … 次の Unit 番号から（いまの Unit は触らない）
 */
export function allocateUnits(input: AllocationInput): {
  words: AllocatedWord[]
  summaries: AllocationSummary[]
  /** 容量を超えたぶん（警告に使う） */
  overflow: number
  /** 自動で決めた開始 Unit（画面に出す） */
  startUnit: string
} {
  const size = Math.max(1, Math.floor(input.unitSize))
  const words: AllocatedWord[] = []
  const summaries: AllocationSummary[] = []
  const book = input.book
  const units = book?.units ?? []

  let index: number
  let used: number

  if (input.placement === 'NEW_BOOK' || book === null) {
    index = 1
    used = 0
  } else if (input.placement === 'NEW_UNIT') {
    const lastIndex = units.length === 0 ? 0 : unitIndex(units[units.length - 1]!.name) ?? units.length
    index = lastIndex + 1
    used = 0
  } else {
    // 続きから: 最後の Unit が未満ならそこを埋める
    const last = units[units.length - 1]
    index = last ? unitIndex(last.name) ?? units.length : 1
    used = last ? last.count : 0
  }

  const startUnit = unitName(index)
  let overflow = 0

  for (const word of input.words) {
    if (used >= size) {
      index += 1
      used = 0
    }
    used += 1
    const name = unitName(index)
    const current = summaries.find((summary) => summary.unit === name)
    if (current) {
      current.count += 1
      current.toSeq = used
    } else {
      summaries.push({ unit: name, count: 1, fromSeq: used, toSeq: used, capacity: size })
    }
    words.push({ heading: word.heading, reading: word.reading, unit: name, seq: used })
  }

  if (book !== null && input.placement === 'CONTINUE') {
    const last = units[units.length - 1]
    if (last && last.count + (summaries[0]?.count ?? 0) > size) {
      // 未満の Unit を容量まで埋め、あふれたぶんは次の Unit へ（上のループで処理済み）
      overflow = 0
    }
  }

  return { words, summaries, overflow, startUnit }
}

/* ------------------------------------------------------------------ */
/* 3. 一覧の絞り込みとページング                                       */
/* ------------------------------------------------------------------ */

/** 一覧の絞り込み条件。 */
export interface DemoFilters {
  keyword: string
  book: string
  unitFrom: string
  unitTo: string
  partOfSpeech: string
  jlpt: string
  detailStatus: string
}

export function emptyFilters(): DemoFilters {
  return { keyword: '', book: '', unitFrom: '', unitTo: '', partOfSpeech: '', jlpt: '', detailStatus: '' }
}

/** 条件が 1 つでも入っているか。 */
export function hasFilter(filters: DemoFilters): boolean {
  return Object.values(filters).some((value) => value !== '')
}

/** Unit 名の比較（Unit010 > Unit009 になるように番号で比べる）。 */
export function compareUnit(left: string, right: string): number {
  const leftIndex = unitIndex(left)
  const rightIndex = unitIndex(right)
  if (leftIndex !== null && rightIndex !== null) {
    return leftIndex - rightIndex
  }
  return left.localeCompare(right, 'ja')
}

/** 表示用の状態（デモ表示設定で上書きしたあとの状態）。 */
export function displayStatus(word: DemoWord, state: DemoDisplayState): DemoWord['detailStatus'] {
  switch (state.listMode) {
    case 'NOT_GENERATED':
      return 'NOT_GENERATED'
    case 'GENERATING':
      return 'RUNNING'
    case 'FAILED':
      return 'FAILED'
    default:
      return word.detailStatus
  }
}

/**
 * 一覧に出す失敗の理由。
 *
 * デモ表示設定で「AI 生成失敗」に切り替えたときは元の理由が無いので、
 * **デモ用の理由**を出す（空欄のままだと、何が起きたのか分からない行になる）。
 */
export function displayFailureReason(word: DemoWord, state: DemoDisplayState): string | null {
  const status = displayStatus(word, state)
  if (status !== 'FAILED') {
    return null
  }
  return word.failureReason ?? '生成サービスが応答しませんでした。時間をおいて再試行してください。'
}

/**
 * 一覧を絞り込む。
 *
 * デモ表示設定の「内容未生成／AI生成中／AI生成失敗」を選んでいるときは、
 * 全語をその状態として見せる（状態ごとの画面を確認するための上書き）。
 */
export function filterWords(
  words: DemoWord[],
  filters: DemoFilters,
  state: DemoDisplayState
): DemoWord[] {
  if (state.listMode === 'NO_RESULT') {
    return []
  }
  const keyword = filters.keyword.trim().toLowerCase()
  return words.filter((word) => {
    if (keyword !== '') {
      const haystack = [
        word.heading,
        word.reading,
        word.chineseMeaning,
        word.partOfSpeech,
        word.alternateReading ?? ''
      ]
        .join(' ')
        .toLowerCase()
      if (!haystack.includes(keyword)) {
        return false
      }
    }
    if (filters.partOfSpeech !== '' && word.partOfSpeech !== filters.partOfSpeech) {
      return false
    }
    if (filters.jlpt !== '' && (word.jlpt ?? '') !== filters.jlpt) {
      return false
    }
    if (filters.detailStatus !== '' && displayStatus(word, state) !== filters.detailStatus) {
      return false
    }
    if (filters.book !== '') {
      const inBook = word.collections.filter((collection) => collection.book === filters.book)
      if (inBook.length === 0) {
        return false
      }
      // Unit の範囲は「その書籍の中の Unit」で見る
      if (filters.unitFrom !== '' || filters.unitTo !== '') {
        const matched = inBook.some((collection) => {
          if (filters.unitFrom !== '' && compareUnit(collection.unit, filters.unitFrom) < 0) {
            return false
          }
          if (filters.unitTo !== '' && compareUnit(collection.unit, filters.unitTo) > 0) {
            return false
          }
          return true
        })
        if (!matched) {
          return false
        }
      }
    } else if (filters.unitFrom !== '' || filters.unitTo !== '') {
      // 書籍を選んでいないときは、どの書籍かの Unit でも当たれば残す
      const matched = word.collections.some((collection) => {
        if (filters.unitFrom !== '' && compareUnit(collection.unit, filters.unitFrom) < 0) {
          return false
        }
        if (filters.unitTo !== '' && compareUnit(collection.unit, filters.unitTo) > 0) {
          return false
        }
        return true
      })
      if (!matched) {
        return false
      }
    }
    return true
  })
}

/** ページの切り出し。 */
export function paginate<T>(items: T[], page: number, size: number): {
  items: T[]
  page: number
  totalPages: number
  total: number
} {
  const total = items.length
  const totalPages = Math.max(1, Math.ceil(total / Math.max(1, size)))
  const safePage = Math.min(Math.max(1, page), totalPages)
  const start = (safePage - 1) * size
  return { items: items.slice(start, start + size), page: safePage, totalPages, total }
}

/* ------------------------------------------------------------------ */
/* 4. 表示の小物                                                       */
/* ------------------------------------------------------------------ */

/** 詳細情報の状態の日本語ラベル。 */
export const DETAIL_STATUS_LABELS: Record<DemoWord['detailStatus'], string> = {
  NOT_GENERATED: '未生成',
  RUNNING: '生成中',
  GENERATED: '生成済み',
  EDITED: '編集済み',
  FAILED: '生成失敗'
}

/** 詳細情報の状態のバッジの色（設計システムのクラス）。 */
export const DETAIL_STATUS_BADGES: Record<DemoWord['detailStatus'], string> = {
  NOT_GENERATED: 'badge--neutral',
  RUNNING: 'badge--info',
  GENERATED: 'badge--success',
  EDITED: 'badge--warning',
  FAILED: 'badge--danger'
}

/** 品詞の選択肢。 */
export const PART_OF_SPEECH_OPTIONS: DemoPartOfSpeech[] = [
  '名詞', '動詞', 'い形容詞', 'な形容詞', '副詞', '名詞・動詞'
]

/** 間違えやすいポイントの種類のラベル（色だけで区別しないための文字も兼ねる）。 */
export const CAUTION_KIND_LABELS: Record<string, string> = {
  GRAMMAR: '文法の誤り',
  UNNATURAL: 'この場面では不自然',
  MEANING: '意味が違う',
  PARTICLE: '助詞の使い方'
}

/** ミニ練習の種類のラベル。 */
export const PRACTICE_KIND_LABELS: Record<string, string> = {
  PARTICLE: '助詞を選ぶ',
  SYNONYM: '似た言葉を選ぶ',
  SCENE: '場面に合う言い方',
  WRITING: '自由に文を作る'
}

/** 類義語の入れ替え可否のラベル。 */
export const INTERCHANGEABLE_LABELS: Record<string, string> = {
  YES: '入れ替えられる',
  SOMETIMES: '場合による',
  NO: '入れ替えられない'
}

/** 詳細に中身が 1 つでもあるか（部分的な詳細の判定に使う）。 */
export function hasAnyDetail(detail: DemoDetailContent | null): boolean {
  if (detail === null) {
    return false
  }
  return (
    detail.coreMeaning !== '' ||
    detail.senses.length > 0 ||
    detail.examples.length > 0 ||
    detail.patterns.length > 0 ||
    detail.dialogs.length > 0 ||
    detail.synonyms.length > 0 ||
    detail.cautions.length > 0 ||
    detail.collocations.length > 0 ||
    detail.relatedWords.length > 0
  )
}

/** 収録のまとめ（「デモ日本語 初級 Unit001」のような 1 行）。 */
export function collectionLabel(collection: DemoCollection): string {
  return `${collection.book} ${collection.unit} #${collection.seq}`
}

/** 同じ見出し語で読みが違う語を探す（同表記・別読みの注意に使う）。 */
export function findSameHeading(words: DemoWord[], word: DemoWord): DemoWord[] {
  return words.filter((other) => other.heading === word.heading && other.id !== word.id)
}

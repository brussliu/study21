/**
 * 授業録音 / AI 授業記録 — 画面で使う共通の型・定数・表示用ヘルパー。
 *
 * データは API（`src/api/classroom.ts`）から取り、ここでは**画面に出す選択肢とラベル・
 * 整形だけ**を一元管理する（ダミーの授業履歴や転写文は持たない）。
 *
 * `DEFAULT_PRESETS` はサーバーの前置詞プリセット（`CR_前置詞プリセット情報` の GLOBAL seed）が
 * 取れなかったときの**画面の初期値**で、API が返ればそちらを使う。
 */

/** 授業名・科目・言語モードなどを画面から画面へ渡すときの query のキー。 */
export const CLASSROOM_QUERY = {
  name: 'name',
  subject: 'subject',
  languageMode: 'languageMode',
  preset: 'preset',
  /**
   * 録音する音源（`mic` / `mic-screen` / `mic-tab`）。
   * **【新しい授業】で選び**、録音中の画面がその音源で録音を始める（利用者の指示で選択を移動した）。
   */
  audioMode: 'audioMode',
  /** 「新しい授業」の dialog から来たとき、録音ページで**自動的に録音を始める**（利用者の指示）。 */
  autostart: 'autostart'
} as const

/**
 * 言語モード（5 択）。値はルーティングの query に載せても分かる短いコード。
 * 廃止した `auto`（自動判別）は選べない（言語コードはコード側で固定になった）。
 * 旧データに `auto` が残っていても表示できるように `LEGACY_LANGUAGE_MODE_LABELS` を持つ。
 */
export type LanguageMode = 'zh' | 'ja' | 'en' | 'zh-en' | 'ja-en'

export interface LanguageModeOption {
  value: LanguageMode
  label: string
  help: string
}

/** 授業の言語モード（中国語 / 日本語 / 英語 とその組み合わせ）。 */
export const LANGUAGE_MODES: LanguageModeOption[] = [
  {
    value: 'zh',
    label: '中国語',
    help: '先生と生徒が中国語で話す授業。中国語で書き起こします。'
  },
  {
    value: 'ja',
    label: '日本語',
    help: '先生と生徒が日本語で話す授業。日本語で書き起こします。'
  },
  {
    value: 'en',
    label: '英語',
    help: '英語の授業。英語で書き起こします。'
  },
  {
    value: 'zh-en',
    label: '中国語＋英語',
    help: '中国語を中心に英語を交える授業。両方を聞き分けます。'
  },
  {
    value: 'ja-en',
    label: '日本語＋英語',
    help: '日本語を中心に英語を交える授業。両方を聞き分けます。'
  }
]

/**
 * 旧データに残っている言語モードの表示（選べない値）。
 * `auto`（自動判別）は廃止した。サーバーは `auto` の記録を**日本語（ja-JP）**として扱う。
 */
export const LEGACY_LANGUAGE_MODE_LABELS: Record<string, string> = {
  auto: '自動判別（廃止）'
}

/** 科目の選択肢（値はそのままラベルとして使う）。 */
export const SUBJECTS = ['国語', '数学', '理科', '社会', '英語', 'その他'] as const

/** 前置詞（シナリオプリセット）。授業の目的に合わせて AI ノートの整理方針を変える。 */
export interface ClassroomPreset {
  id: string
  name: string
  description: string
}

/**
 * 既定の前置詞（設定画面で増減できる。ここは画面の初期値）。
 * MVP ではまず 3 つの汎用プリセットだけを占位として置く。
 */
export const DEFAULT_PRESETS: ClassroomPreset[] = [
  {
    id: 'standard',
    name: '通常の授業',
    description: '一般的な授業。テーマ・学習内容・先生の重点・宿題を順に整理します。'
  },
  {
    id: 'math-zh',
    name: '数学（中国語）',
    description: '数学の授業（中国語）。公式や解法の手順・例題を重点的に整理します。'
  },
  {
    id: 'english-native',
    name: '英語（外教）',
    description: '英語の授業（ネイティブ講師）。新出単語・会話表現・発音を整理します。'
  }
]

/**
 * 言語モードの値からラベルを引く（**知らない値・旧データでも落ちない**）。
 * 選択肢 → 旧データのラベル → 生の値 → 「（未指定）」の順に見る。
 */
export function languageModeLabel(value: string | undefined | null): string {
  const found = LANGUAGE_MODES.find((option) => option.value === value)
  if (found !== undefined) return found.label
  if (value !== undefined && value !== null && value !== '') {
    return LEGACY_LANGUAGE_MODE_LABELS[value] ?? value
  }
  return '（未指定）'
}

/** 前置詞の id から名前を引く（知らない id はそのまま返す）。 */
export function presetName(value: string | undefined | null): string {
  const found = DEFAULT_PRESETS.find((preset) => preset.id === value)
  return found?.name ?? value ?? '（未指定）'
}

/**
 * API が返す日時（`2026-09-18T00:24:47.280939`）を「2026-09-18 00:24:47」にする。
 *
 * <p>一覧では**秒まで**出す（利用者の指示）。読めない値はそのまま返す（画面を止めない）。</p>
 */
export function formatDateTime(value: string | null | undefined): string {
  if (value === null || value === undefined || value === '') return ''
  const match = /^(\d{4})-(\d{2})-(\d{2})[T ](\d{2}):(\d{2}):(\d{2})/.exec(value)
  if (match === null) return value
  return `${match[1]}-${match[2]}-${match[3]} ${match[4]}:${match[5]}:${match[6]}`
}

/** 経過時間（秒）を「HH:MM:SS」にする。 */
export function formatElapsed(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(safe / 3600)
  const minutes = Math.floor((safe % 3600) / 60)
  const seconds = safe % 60
  const pad = (value: number): string => String(value).padStart(2, '0')
  return `${pad(hours)}:${pad(minutes)}:${pad(seconds)}`
}

/* ---------- 転写（セグメント） ---------- */

/**
 * 転写の行頭に出す時刻（録音開始からの経過秒）。
 * 1 時間未満は「MM:SS」、それ以上は「HH:MM:SS」（無ければ「--:--」）。
 */
export function formatSegmentTime(offsetSeconds: number | null | undefined): string {
  if (offsetSeconds === null || offsetSeconds === undefined || !Number.isFinite(offsetSeconds)) {
    return '--:--'
  }
  const safe = Math.max(0, Math.floor(offsetSeconds))
  const minutes = Math.floor(safe / 60)
  const seconds = safe % 60
  const pad = (value: number): string => String(value).padStart(2, '0')
  return `${pad(minutes)}:${pad(seconds)}`
}

/**
 * 転写の話者ラベル。
 * MVP は話者を分けないので、サーバーが空を返したときは「講義」1 つに丸める（設計 §6）。
 */
export function segmentSpeaker(value: string | null | undefined): string {
  const speaker = (value ?? '').trim()
  return speaker === '' ? '講義' : speaker
}

/* ---------- AI 授業ノート（AI が返す JSON） ---------- */

/**
 * AI 授業ノートの 4 段落（画面の見出しと、AI の応答 JSON のキー）。
 * `keys` は日本語の見出しを優先し、英語のキーも受け付ける（プロンプトの揺れを吸収する）。
 */
export const NOTE_SECTIONS: { key: string; title: string; icon: string; keys: string[] }[] = [
  { key: 'theme', title: '本時のテーマ', icon: 'bookmark', keys: ['テーマ', 'theme'] },
  { key: 'content', title: '学習内容', icon: 'list', keys: ['学習内容', 'content'] },
  { key: 'focus', title: '先生の重点', icon: 'alert', keys: ['先生の重点', '重点', 'focus'] },
  { key: 'homework', title: '宿題', icon: 'check-square', keys: ['宿題', 'homework'] }
]

/** AI の応答（JSON 文字列）を読む。読めなければ null。 */
function parseJsonObject(json: string | null | undefined): Record<string, unknown> | null {
  if (json === null || json === undefined || json.trim() === '') return null
  const text = json.trim()
  const start = text.indexOf('{')
  const end = text.lastIndexOf('}')
  if (start < 0 || end <= start) return null
  try {
    const parsed = JSON.parse(text.slice(start, end + 1)) as unknown
    if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) return null
    return parsed as Record<string, unknown>
  } catch {
    return null
  }
}

/** JSON の値を 1 行の文字列にする（配列は「・」でつなぐ）。 */
function jsonText(value: unknown): string {
  if (value === null || value === undefined) return ''
  if (typeof value === 'string') return value.trim()
  if (typeof value === 'number' || typeof value === 'boolean') return String(value)
  if (Array.isArray(value)) {
    return value.map((item) => jsonText(item)).filter((item) => item !== '').join(' / ')
  }
  return ''
}

/**
 * AI 授業ノート（`noteJson` / `summaryJson`）から 4 段落を取り出す。
 * 読めない・キーが無い段落は空文字（画面は「まだありません」の案内を出す）。
 */
export function noteSections(json: string | null | undefined): { key: string; title: string; icon: string; body: string }[] {
  const parsed = parseJsonObject(json)
  return NOTE_SECTIONS.map((section) => {
    let body = ''
    if (parsed !== null) {
      for (const key of section.keys) {
        body = jsonText(parsed[key])
        if (body !== '') break
      }
    }
    return { key: section.key, title: section.title, icon: section.icon, body }
  })
}

/** AI の応答に 1 つでも中身があるか（無ければ「まだありません」を出す）。 */
export function hasNoteBody(json: string | null | undefined): boolean {
  return noteSections(json).some((section) => section.body !== '')
}

/* ---------- 前置詞（API のプリセット） ---------- */

/** 画面が使う前置詞の形（選択肢は値が文字列）。 */
export interface ClassroomPresetOption {
  id: string
  name: string
  description: string
}

/** API のプリセットを画面の選択肢へ写す（値はプリセット ID の文字列）。 */
export function presetOptions(
  presets: { presetId: number; name: string; text: string }[]
): ClassroomPresetOption[] {
  return presets.map((preset) => ({
    id: String(preset.presetId),
    name: preset.name,
    description: preset.text
  }))
}

/** 選択肢の中から id の名前を引く（見つからなければ null）。 */
export function presetOptionName(options: ClassroomPresetOption[], value: string | null | undefined): string | null {
  const found = options.find((option) => option.id === String(value ?? ''))
  return found?.name ?? null
}

/* ---------- 状態の見た目 ---------- */

/** 状態バッジの色（設計システムの修飾子）。 */
export function statusBadgeClass(status: string): string {
  if (status === 'COMPLETED') return 'badge--success'
  if (status === 'FAILED' || status === 'CANCELLED') return 'badge--danger'
  if (status === 'RECORDING') return 'badge--danger'
  if (status === 'ANALYZING' || status === 'TRANSCRIBING' || status === 'STOPPED') return 'badge--warning'
  return 'badge--neutral'
}

/** 録音時間（秒）を「HH:MM:SS」で出す（未確定は「--:--:--」）。 */
export function durationLabel(seconds: number | null | undefined): string {
  if (seconds === null || seconds === undefined || !Number.isFinite(seconds)) return '--:--:--'
  return formatElapsed(seconds)
}

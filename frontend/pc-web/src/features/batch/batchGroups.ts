import type { BatchTaskRow } from '@/api/batch'

/**
 * バッチ管理のタブ（グルーピング）。
 *
 * 2.0 のバッチ管理画面（`batch.jsp` → 移行用プロトタイプ `views/prototype/generated/BatchPage.vue`）
 * が持っていたタブをそのまま引き継ぐ。所属は**タスクコード**で決めている
 * （同じ設定ページでも別のタブに入るタスクがあるため。例: AI_MODEL は「英単語問題生成」と
 * 「AI共通・OCR」の両方にまたがる）。
 *
 * 2.1 で追加・改名したタスク:
 *   * batL01 → **batS01**（プロキシサービス。タブは「システム」）
 *   * **batC51-A〜D**（AI生図の AI 生成を**作図モードごとに 1 バッチ**。タブは「図形管理」＝2.1 で足した新タブ）
 *   * **batC52**（AI 画図助手。タブは同じ「図形管理」）
 *   * 上の表に無い新しいコードは、設定ページ（pageCode）から推定し、
 *     それでも決まらなければ「その他」に入れる（タブから漏れないようにするため）。
 *
 * **2026-09 のバッチ構成変更（AI 生図）**:
 *   以前は 1 操作で batC51（画像取込・前処理）→ batC52（AI 生成）→ batC53（検証・確定）の
 *   3 バッチを通っていたが、**AI を呼ぶ工程だけをバッチに残した**:
 *     * 画像の取込・前処理と、コマンドの検証・確定は**バッチではない**（バックエンドの通常コードが実行）
 *     * AI 生成は**作図モードごとに 1 バッチ**（`batC51-A`〜`batC51-D`）
 *     * `batC52`＝**AI 画図助手**（空いた番号を再利用。依頼を受けたらその場で実行）
 *   そのため `batC53` は**この表から外した**。モードが無い時代の裸の `batC51` も
 *   **2026-09-19 に削除した**（実行の入口が無くなったため。要求行の 作図モード が NULL の行は
 *   今までどおりモード A として処理する）。ただし `BAT_バッチ実行履歴情報` には
 *   **古い batC51（モードが無い時代の AI 生成）と新しい batC52（AI 画図助手）の履歴が同居する**。
 *   番号だけでは区別できないので、画面がコード決め打ちで意味を推測しないようにしてある
 *   （タブは設定ページから推定し、履歴は**コードをそのまま表示**する）。
 */

/** タブの表示順（「すべて」はこの前に付く）。 */
export const BATCH_GROUP_ORDER = [
  '英語単語',
  '英単語問題生成',
  '英熟語',
  '日本語単語',
  '英作文',
  '英語穴埋め',
  '英語長文精読',
  '図形管理',
  '授業録音',
  'AI共通・OCR',
  '学習モニター',
  'システム',
] as const

/** タスクコード → タブ。原型（batch.jsp）の所属をそのまま持つ。 */
const TASK_GROUPS: Record<string, string> = {
  'batC01': '英語単語',
  'batC02': '英語単語',
  'batC03': '英語単語',
  'batC04': '英単語問題生成',
  'batC05': '英語単語',
  'batC06': '英語単語',
  'batC07': 'AI共通・OCR',
  'batC09': 'AI共通・OCR',
  'batC10': '英単語問題生成',
  'batC11': '英作文',
  'batC12': '英作文',
  'batC13': '英語穴埋め',
  'batC14': '英語穴埋め',
  'batC15': '英語長文精読',
  'batC15-1': '英語長文精読',
  'batC15-2': '英語長文精読',
  'batC15-3': '英語長文精読',
  'batC16': '英語長文精読',
  'batC17': '英語長文精読',
  'batC18': '英語長文精読',
  'batC19': '英語単語',
  'batC21': '英語単語',
  'batC22': '英単語問題生成',
  'batC23': '英単語問題生成',
  'batC31': '英熟語',
  'batC32': '英熟語',
  'batC33': '英熟語',
  'batC34': '英熟語',
  'batC41': '日本語単語',
  'batC42': '日本語単語',
  'batC43': '日本語単語',
  'batC44': '日本語単語',
  // AI生図の AI 生成（画像 → 分類 → AI → GeoGebra コマンド）。**作図モードごとに 1 バッチ**
  // （batC51-A〜D）。画像の取込・前処理と、コマンドの検証・確定はバックエンドの通常コードが実行する。
  'batC51-A': '図形管理',
  'batC51-B': '図形管理',
  'batC51-C': '図形管理',
  'batC51-D': '図形管理',
  // AI 画図助手（作図画面の指示。依頼を受けたらその場で実行する。旧 batC52 の番号を再利用）
  'batC52': '図形管理',
  // 授業録音 / AI 授業記録（フェーズ分析 batC61・最終まとめ batC62）
  'batC61': '授業録音',
  'batC62': '授業録音',
  'batC91': 'AI共通・OCR',
  'batL02': '学習モニター',
  'batL03': '学習モニター',
  'batR01': 'システム',
  'batR02': 'システム',
  'batR03': 'システム',
  'batR04': 'システム',
  'batR05': 'システム',
  'batS01': 'システム',
}

/**
 * 設定ページ（pageCode）→ タブの推定表。
 * 原型の所属から「その設定ページのタスクがどのタブに多いか」で作った保険。
 */
const PAGE_GROUPS: Record<string, string> = {
  AI_MODEL: 'AI共通・OCR',
  CLASSROOM_AI: '授業録音',
  DAILY_REPORT: 'システム',
  ENGLISH_CLOZE: '英語穴埋め',
  ENGLISH_ESSAY: '英作文',
  ENGLISH_PHRASE_DETAIL_AI: '英熟語',
  ENGLISH_READING_INTENSIVE: '英語長文精読',
  ENGLISH_WORD_DETAIL_AI: '英語単語',
  ENGLISH_WORD_TEXTBOOK_AI: '英語単語',
  GEOMETRY_AI: '図形管理',
  JAPANESE_WORD_AI: '日本語単語',
  STUDY_MONITOR: '学習モニター',
  SYSTEM: 'システム',
  TRANSLATION: '英語単語',
  VOICE: '英語単語',
  WORD_EXPLANATION: '英語単語',
  WORD_QUESTION: '英単語問題生成',
}

export const OTHER_GROUP = 'その他'

export interface BatchTab {
  key: string
  title: string
  rows: BatchTaskRow[]
}

/** タスクが属するタブ名を決める。 */
export function groupTitleOf(row: BatchTaskRow): string {
  const byCode = TASK_GROUPS[row.taskCode]
  if (byCode) return byCode
  const byPage = row.pageCode ? PAGE_GROUPS[row.pageCode] : undefined
  return byPage ?? OTHER_GROUP
}

/**
 * タスク一覧を「すべて」＋グループのタブに分ける（空のグループは出さない）。
 * 順番は BATCH_GROUP_ORDER、未知のグループは「その他」として最後に置く。
 */
export function buildBatchTabs(rows: BatchTaskRow[]): BatchTab[] {
  const groups = new Map<string, BatchTaskRow[]>()
  for (const row of rows) {
    const title = groupTitleOf(row)
    const bucket = groups.get(title)
    if (bucket) bucket.push(row)
    else groups.set(title, [row])
  }
  const known = BATCH_GROUP_ORDER.filter((title) => groups.has(title))
  const unknown = [...groups.keys()].filter((title) => !(BATCH_GROUP_ORDER as readonly string[]).includes(title))
  const ordered = [...known, ...unknown]
  return [
    { key: 'all', title: 'すべて', rows },
    ...ordered.map((title) => ({ key: title, title, rows: groups.get(title) ?? [] }))
  ]
}

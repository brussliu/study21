/**
 * 一覧の「取得状態」のタグから開く**履歴**の小さな規則。
 *
 * <p>2.0 の英語学習の単語情報管理（`word.jsp`）の「詳細情報取得履歴」と同じで、
 * **AI の取得 1 回 = 1 行**を出し、そのうちの 1 つを「使用する版」として指定する。
 * ここは表示の言葉に直すだけの純関数（画面とテストの両方から使う）。</p>
 */

import type { JpnAiStateKey } from '@/features/japanese-word/aiStateLabel'

/** その区画（A・B／C／D／E）が見る内容種別（問題は C が C1 と C2 の 2 つ）。 */
const QUESTION_TYPES: Record<JpnAiStateKey, string[]> = {
  // A・B は詳細の版（JPN_単語詳細情報）で見るので、問題の種別は無い
  AB: [],
  C: ['C1_READING', 'C2_KANJI'],
  D: ['D_CONTEXT_MEANING'],
  E: ['E_KANJI_USAGE']
}

/** 内容種別の見出し（画面に出す日本語）。 */
const QUESTION_TYPE_LABELS: Record<string, string> = {
  C1_READING: 'C1 読み問題',
  C2_KANJI: 'C2 漢字問題',
  D_CONTEXT_MEANING: 'D 文脈問題',
  E_KANJI_USAGE: 'E 漢字問題'
}

/** 生成の状態（`JPN_AI生成履歴情報.状態コード`）の言葉。 */
const GENERATION_STATE_LABELS: Record<string, string> = {
  RUNNING: '取得中',
  SUCCEEDED: '取得済',
  FAILED: '失敗',
  QUEUED: '取得待ち',
  CANCELED: '中止'
}

/** その区画が問題の履歴を見るか（A・B は詳細の版を見る）。 */
export function sectionUsesQuestionVersions(section: JpnAiStateKey): boolean {
  return QUESTION_TYPES[section].length > 0
}

/** その区画が見る内容種別（A・B は空）。 */
export function questionTypesOf(section: JpnAiStateKey): string[] {
  return QUESTION_TYPES[section]
}

/** 内容種別の見出し（知らない種別はコードのまま出す）。 */
export function questionTypeLabel(questionType: string): string {
  return QUESTION_TYPE_LABELS[questionType] ?? questionType
}

/** 生成の状態の言葉（知らないコードはそのまま出す）。 */
export function generationStateLabel(state: string | null | undefined): string {
  if (state === null || state === undefined || state === '') {
    return '—'
  }
  return GENERATION_STATE_LABELS[state] ?? state
}

/** 履歴の見出し（「A・B 詳細情報の履歴」のように出す）。 */
export function historyTitleOf(section: JpnAiStateKey): string {
  switch (section) {
    case 'AB':
      return 'A・B 詳細情報の履歴'
    case 'C':
      return 'C 読み・漢字問題の履歴'
    case 'D':
      return 'D 文脈問題の履歴'
    default:
      return 'E 漢字問題の履歴'
  }
}

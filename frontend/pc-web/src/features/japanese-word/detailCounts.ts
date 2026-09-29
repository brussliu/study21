/**
 * 一覧の「詳細情報件数」列（有効版の詳細の段落ごとの件数）。
 *
 * <p>2.0 の英語学習の単語情報管理（`word.jsp`）は、この列に「語義 2・例文 3…」のような
 * **段落ごとの件数タグ**を出していた（`word_intermediate.js` の `renderDetailCounts`。
 * 詳細が未取得なら「-」）。件数が 0 の段落も出して「何が足りないか」が分かるようにしていたので、
 * 2.1 も同じにする。</p>
 *
 * <p>2.1 の段落は 10（会話の発言は会話に含める）。ラベルは 2.0 と同じ短い形にする。</p>
 */

import type { JpnWordDetailCounts } from '@/api/japanese'

/** 件数タグ 1 つ。 */
export interface JpnDetailCountChip {
  /** API の項目名（senses など。テストとキーに使う） */
  key: keyof JpnWordDetailCounts
  /** タグに出す短いラベル（2.0 と同じ） */
  label: string
  /** マウスオーバーの説明（「例文 3件」のように件数まで書く） */
  titleOf: (count: number) => string
  count: number
}

/** 段落の並びとラベル（2.0 の英語学習と同じ並び）。 */
const SECTIONS: { key: keyof JpnWordDetailCounts; label: string; name: string }[] = [
  { key: 'senses', label: '語義', name: '語義' },
  { key: 'examples', label: '例文', name: '例文' },
  { key: 'patterns', label: '文型', name: '文型' },
  { key: 'dialogs', label: '会話', name: '会話' },
  { key: 'synonyms', label: '類義', name: '類義語' },
  { key: 'cautions', label: '注意', name: '注意（間違えやすいポイント）' },
  { key: 'collocations', label: 'コロ', name: 'コロケーション' },
  { key: 'relatedWords', label: '関連', name: '関連語' },
  { key: 'usageNotes', label: '場面', name: '使用場面' },
  { key: 'practices', label: '練習', name: 'ミニ練習' }
]

/**
 * 詳細情報件数のタグを作る。
 *
 * @param counts 有効版の詳細の件数。**まだ詳細が無い語は null**（画面は「—」を出す）
 * @returns 10 段落ぶんのタグ（0 件も出す）
 */
export function detailCountChips(counts: JpnWordDetailCounts | null | undefined): JpnDetailCountChip[] {
  if (counts === null || counts === undefined) {
    return []
  }
  return SECTIONS.map((section) => ({
    key: section.key,
    label: section.label,
    titleOf: (count: number) => `${section.name} ${count}件`,
    count: counts[section.key] ?? 0
  }))
}

/** 段落の数（タグを出すかどうかの目安）。 */
export const DETAIL_SECTION_COUNT = SECTIONS.length

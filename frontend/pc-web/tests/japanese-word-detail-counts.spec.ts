import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { describe, expect, it } from 'vitest'
import type { JpnWordDetailCounts } from '@/api/japanese'
import { DETAIL_SECTION_COUNT, detailCountChips } from '@/features/japanese-word/detailCounts'

/**
 * 一覧の「詳細情報件数」列のタグ（純関数）。
 *
 * <p>2.0 の英語学習の単語情報管理（`word.jsp`）は、この列に段落ごとの件数タグを出していた
 * （詳細が未取得なら「-」。0 件の段落も出して「何が足りないか」が分かる）。
 * 2.1 も同じ並び・同じ短いラベルで出す。</p>
 */

function counts(overrides: Partial<JpnWordDetailCounts> = {}): JpnWordDetailCounts {
  return {
    senses: 0, examples: 0, patterns: 0, dialogs: 0, synonyms: 0,
    cautions: 0, collocations: 0, relatedWords: 0, usageNotes: 0, practices: 0,
    ...overrides
  }
}

describe('一覧の詳細情報件数', () => {
  it('段落 10 個ぶんのタグを 2.0 と同じ並び・短いラベルで作る', () => {
    const chips = detailCountChips(counts({ senses: 2, examples: 3 }))

    expect(chips).toHaveLength(DETAIL_SECTION_COUNT)
    expect(chips.map((chip) => chip.label)).toEqual([
      '語義', '例文', '文型', '会話', '類義', '注意', 'コロ', '関連', '場面', '練習'
    ])
    expect(chips.map((chip) => chip.key)).toEqual([
      'senses', 'examples', 'patterns', 'dialogs', 'synonyms',
      'cautions', 'collocations', 'relatedWords', 'usageNotes', 'practices'
    ])
    expect(chips[0]?.count).toBe(2)
    expect(chips[1]?.count).toBe(3)
  })

  it('0 件の段落も出す（何が足りないかが分かる。2.0 と同じ）', () => {
    const chips = detailCountChips(counts())

    expect(chips).toHaveLength(DETAIL_SECTION_COUNT)
    expect(chips.every((chip) => chip.count === 0)).toBe(true)
  })

  it('マウスオーバーの説明に段落名と件数を出す', () => {
    const chips = detailCountChips(counts({ examples: 3, collocations: 2 }))

    const examples = chips.find((chip) => chip.key === 'examples')
    expect(examples?.titleOf(examples.count)).toBe('例文 3件')
    const collocations = chips.find((chip) => chip.key === 'collocations')
    expect(collocations?.titleOf(collocations.count)).toBe('コロケーション 2件')
    // 注意は何の注意かまで書く
    const cautions = chips.find((chip) => chip.key === 'cautions')
    expect(cautions?.titleOf(1)).toBe('注意（間違えやすいポイント） 1件')
  })

  it('詳細がまだ無い語（null）はタグを作らない（画面は「—」）', () => {
    expect(detailCountChips(null)).toEqual([])
    expect(detailCountChips(undefined)).toEqual([])
  })

  it('数が欠けていても 0 として扱う（古い応答でも壊れない）', () => {
    const partial = { senses: 1 } as JpnWordDetailCounts

    const chips = detailCountChips(partial)

    expect(chips[0]?.count).toBe(1)
    expect(chips[1]?.count).toBe(0)
  })

  it('列はタグ 10 個が 2 行に収まる幅を確保する（狭いと 3 行になり行が高くなる）', () => {
    // jsdom にレイアウトが無いので、CSS の規則そのものを確かめる。
    // 実寸は Chrome の実測（tmp/jpn-badges/list-counts2.png）で見る:
    // タグ 11px・列 18rem で 5 + 5 の 2 行に収まる。
    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')

    const columnStart = css.indexOf('.jp-words-table .col-jp-detail-counts')
    expect(columnStart).toBeGreaterThanOrEqual(0)
    const columnRule = css.slice(columnStart, css.indexOf('}', columnStart))
    const width = Number(columnRule.match(/width:\s*([\d.]+)rem/)?.[1])
    expect(width).toBeGreaterThanOrEqual(16)
    expect(columnRule).toContain(`min-width: ${width}rem`)

    // タグは折り返さない（文字の途中で折らない）
    const chipStart = css.indexOf('.jp-detail-count {')
    expect(chipStart).toBeGreaterThanOrEqual(0)
    expect(css.slice(chipStart, css.indexOf('}', chipStart))).toContain('white-space: nowrap')
  })
})

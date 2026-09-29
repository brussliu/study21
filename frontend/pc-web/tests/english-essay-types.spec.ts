import { describe, expect, it } from 'vitest'
import {
  countWords,
  ESSAY_LEVEL_LABELS,
  ESSAY_LEVELS,
  ESSAY_RUBRIC_KEYS,
  rubricMaxOf,
  wordRequirementOf
} from '@/features/english-essay/types'

/**
 * 英作文の型と純関数（採点の形は 2.0 と同じ）。
 *
 * <p>見本データ用の生成器（旧 `grading.ts` の `gradeEssay`）は、本物の DB と AI に載せ替えた
 * ときに消した。残したのは画面と API が共有する型・定数と、級から決まる純関数だけ。</p>
 */
describe('英作文の型と純関数', () => {
  it('英検級は 2.0 と同じ 3 つで、表示名を持つ', () => {
    expect(ESSAY_LEVELS).toEqual(['GRADE1', 'PRE1', 'GRADE2'])
    for (const level of ESSAY_LEVELS) {
      expect(ESSAY_LEVEL_LABELS[level].length).toBeGreaterThan(0)
    }
  })

  it('観点は 2.0 と同じ 4 つ（内容・構成・語彙・文法）', () => {
    expect(ESSAY_RUBRIC_KEYS).toEqual(['内容', '構成', '語彙', '文法'])
  })

  it('1 観点の満点は級で決まる（GRADE1 は 8、PRE1・GRADE2 は 4）', () => {
    expect(rubricMaxOf('GRADE1')).toBe(8)
    expect(rubricMaxOf('PRE1')).toBe(4)
    expect(rubricMaxOf('GRADE2')).toBe(4)
  })

  it('語数の目安は級で決まる（GRADE1 は長め、GRADE2 は短め）', () => {
    expect(wordRequirementOf('GRADE1')).toMatch(/\d+/)
    expect(wordRequirementOf('GRADE2')).toMatch(/\d+/)
    expect(wordRequirementOf('GRADE1')).not.toBe(wordRequirementOf('GRADE2'))
  })

  it('語数は空白で区切って数える（2.0 の wordCount と同じ）', () => {
    expect(countWords('one two three four five')).toBe(5)
    // 連続する空白・前後の空白は語に数えない
    expect(countWords('  one   two  ')).toBe(2)
    expect(countWords('')).toBe(0)
    expect(countWords('   ')).toBe(0)
  })
})

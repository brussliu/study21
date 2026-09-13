import { describe, expect, it } from 'vitest'
import {
  MAIN_SUBJECT_TONES,
  MINOR_SUBJECT_LEGEND,
  SUBJECT_LEGEND,
  isMainSubject,
  subjectClass,
  subjectTone
} from '@/features/daily-report/subjectColors'

/**
 * 教科の見分け方（学習日報のカレンダー・授業カード）。
 *
 * **どの教科にも色を付ける**（ユーザーの指定）。主科・副科の違いは色ではなく線で示す
 * （主科＝実線 / 副科＝破線）。色は tokens.css の変数だけを使い、
 * 教科名は必ずラベルにも出す（色だけで意味を伝えない）。
 */
describe('教科の色分け', () => {
  it('主要 3 教科は専用の色になる', () => {
    expect(subjectTone('英語')).toBe('english')
    expect(subjectTone('国語')).toBe('japanese')
    expect(subjectTone('数学')).toBe('math')
  })

  it('表記ゆれや別名も同じ色にまとめる', () => {
    expect(subjectTone('英語表現')).toBe('english')
    expect(subjectTone('English')).toBe('english')
    expect(subjectTone('現代文')).toBe('japanese')
    expect(subjectTone('古文')).toBe('japanese')
    expect(subjectTone('算数')).toBe('math')
    expect(subjectTone('数学I')).toBe('math')
    expect(subjectTone('化学')).toBe('science')
    expect(subjectTone('世界史')).toBe('social')
    expect(subjectTone('美術')).toBe('art')
    expect(subjectTone('音楽')).toBe('music')
    expect(subjectTone('体育')).toBe('pe')
    expect(subjectTone('家庭')).toBe('tech')
    expect(subjectTone('情報')).toBe('tech')
  })

  it('分からない教科はその他（グレー）にする', () => {
    expect(subjectTone('道徳')).toBe('other')
    expect(subjectTone('総合的な学習')).toBe('other')
    expect(subjectTone('')).toBe('other')
    expect(subjectTone(null)).toBe('other')
  })

  it('主科は英語・国語・数学・理科・社会の 5 教科', () => {
    expect(MAIN_SUBJECT_TONES).toEqual(['english', 'japanese', 'math', 'science', 'social'])
    for (const subject of ['英語', '国語', '数学', '理科', '社会']) {
      expect(isMainSubject(subject)).toBe(true)
      // 主科は教科ごとの色クラス（破線の印は付かない）
      expect(subjectClass(subject)).toMatch(/^dr-subject--(english|japanese|math|science|social)$/)
    }
  })

  it('副科も教科ごとの色を持ち、破線の印（dr-subject--minor）が付く', () => {
    const expected: Array<[string, string]> = [
      ['音楽', 'dr-subject--music dr-subject--minor'],
      ['美術', 'dr-subject--art dr-subject--minor'],
      ['体育', 'dr-subject--pe dr-subject--minor'],
      ['保健体育', 'dr-subject--pe dr-subject--minor'],
      ['技術', 'dr-subject--tech dr-subject--minor'],
      ['家庭', 'dr-subject--tech dr-subject--minor'],
      ['道徳', 'dr-subject--other dr-subject--minor'],
      ['総合的な学習', 'dr-subject--other dr-subject--minor'],
      ['', 'dr-subject--other dr-subject--minor']
    ]
    for (const [subject, classes] of expected) {
      expect(isMainSubject(subject)).toBe(false)
      expect(subjectClass(subject)).toBe(classes)
    }
    expect(subjectClass(undefined)).toBe('dr-subject--other dr-subject--minor')
  })

  it('教科ごとに違う色のクラスになる（同じ色にまとめない）', () => {
    const tones = ['英語', '国語', '数学', '理科', '社会', '音楽', '美術', '体育', '技術', '道徳']
      .map((subject) => subjectTone(subject))
    expect(new Set(tones).size).toBe(10)
  })

  it('凡例は主科 5 件と副科 5 件を持つ（どちらも教科ごとの色）', () => {
    expect(SUBJECT_LEGEND).toHaveLength(5)
    for (const entry of SUBJECT_LEGEND) {
      expect(subjectClass(entry.label)).toBe(`dr-subject--${entry.tone}`)
    }
    expect(MINOR_SUBJECT_LEGEND).toHaveLength(5)
    for (const entry of MINOR_SUBJECT_LEGEND) {
      expect(subjectClass(entry.label)).toBe(`dr-subject--${entry.tone} dr-subject--minor`)
    }
  })
})

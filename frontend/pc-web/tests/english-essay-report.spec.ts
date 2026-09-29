import { describe, expect, it } from 'vitest'
import { normalizeGradingReport } from '@/features/english-essay/report'

/**
 * 添削結果 JSON の正規化。
 *
 * <p>2.0 の `批改結果JSON` は**形が 2 種類**あり、移行データにはそのまま入っている
 * （2026-09-27 の実データで 35 件中 24 件が新形式・11 件が古い形式）。どちらでも画面が
 * 壊れないことを固定する。**足りない項目は空**にして、偽の値は作らない。</p>
 *
 * <p>`wordRequirement` と `taskRequirements` は、さらに**それぞれ形が 2 つ**ある
 * （実データの 英作文ID 25 のような行）。ここで両方を固定する。</p>
 */

/** 新形式（2.0 の移行データ）のレポート JSON。 */
function newFormatReport(): Record<string, unknown> {
  return {
    status: 'AI_GRADED',
    version: 'eiken-ai-v1',
    level: 'GRADE1',
    score: 24,
    maxScore: 32,
    rubricMax: 8,
    rubricValues: [7, 6, 6, 5],
    modelAnswer: 'In conclusion, learning English opens many opportunities for our future.',
    wordCount: 210,
    wordRequirement: '200〜240語',
    taskRequirements: ['設問の問いに答える', '理由を 2 つ以上挙げる', '結論を述べる'],
    warnings: ['語数が目安より少なめです。'],
    titleJa: '部活動の時間を増やすべきか',
    titleZh: '是否应该增加社团活动时间',
    japanese: {
      title: '前回の指摘が活きています',
      summary: '第 3 回の添削では、前回の指摘が改善されているかを確認しました。',
      tags: ['設問適合', '理由の充実', '接続表現'],
      rubric: ['内容', '構成', '語彙', '文法'],
      rubricNotes: ['理由の数が増えました。', '段落の役割が明確です。', '書き言葉らしい語彙が増えました。', 'コンマの使い方が正確です。'],
      corrections: [['I think', 'I believe', '表現', '繰り返しを避けます。']],
      advice: '結論の前に理由を 2 回示すと評価が上がります。',
      notice: '本結果は公式採点ではありません。'
    },
    chinese: {
      title: '上次指出的问题已改善',
      summary: '第 3 次批改重点确认了上次指出的问题是否已改善。',
      tags: ['切题', '理由充分', '连接词'],
      rubric: ['内容', '结构', '词汇', '语法'],
      rubricNotes: ['理由增加了。', '段落清晰。', '书面语词汇增加。', '逗号使用准确。'],
      corrections: [['I think', 'I believe', '表达', '避免重复。']],
      advice: '结论前用两次理由更容易体现数量。',
      notice: '本结果并非官方评分。'
    }
  }
}

/** 古い形式（2.0 の移行データ。ほとんど入っていない）。 */
function oldFormatReport(): Record<string, unknown> {
  return {
    status: 'AI_GRADED',
    version: 'eiken-ai-v1',
    level: 'PRE1',
    japanese: {
      title: '主張は伝わります',
      summary: '内容・構成・語彙・文法の 4 観点で評価しました。',
      advice: '理由をもう 1 つ足しましょう。'
    },
    chinese: {
      title: '主张清楚',
      summary: '按四项观点进行了评价。'
    }
  }
}

/** 行の値（API の `gradings[]` の 1 行）。 */
function row(report: unknown, overrides: Record<string, unknown> = {}) {
  return {
    gradingId: '301',
    level: 'GRADE1',
    titleJa: '部活動の時間を増やすべきか',
    titleZh: '是否应该增加社团活动时间',
    essayText: 'I think that students should learn English in elementary school.',
    wordCount: 210,
    score: 24,
    maxScore: 32,
    createdAt: '2026-09-26T20:30:00.000Z',
    report,
    essayLevel: 'GRADE1' as const,
    ...overrides
  }
}

describe('添削結果 JSON の正規化', () => {
  it('新形式は 4 観点を rubricValues と rubricNotes から組み立てる', () => {
    const grading = normalizeGradingReport(row(newFormatReport()))

    expect(grading.id).toBe('grading-301')
    expect(grading.status).toBe('AI_GRADED')
    expect(grading.version).toBe('eiken-ai-v1')
    expect(grading.level).toBe('GRADE1')
    expect(grading.score).toBe(24)
    expect(grading.maxScore).toBe(32)
    expect(grading.rubricMax).toBe(8)

    // 観点は名前（rubric）・点数（rubricValues）・講評（rubricNotes）・満点（rubricMax）から
    expect(grading.japanese.rubric).toEqual([
      { key: '内容', score: 7, maxScore: 8, note: '理由の数が増えました。' },
      { key: '構成', score: 6, maxScore: 8, note: '段落の役割が明確です。' },
      { key: '語彙', score: 6, maxScore: 8, note: '書き言葉らしい語彙が増えました。' },
      { key: '文法', score: 5, maxScore: 8, note: 'コンマの使い方が正確です。' }
    ])
    expect(grading.chinese.rubric.map((item) => item.key)).toEqual(['内容', '结构', '词汇', '语法'])
    expect(grading.chinese.rubric.map((item) => item.note)).toEqual([
      '理由增加了。', '段落清晰。', '书面语词汇增加。', '逗号使用准确。'
    ])

    // corrections は配列の配列（新形式）
    expect(grading.japanese.corrections).toEqual([
      { original: 'I think', corrected: 'I believe', category: '表現', reason: '繰り返しを避けます。' }
    ])
    expect(grading.chinese.corrections[0]?.corrected).toBe('I believe')

    expect(grading.modelAnswer).toBe('In conclusion, learning English opens many opportunities for our future.')
    expect(grading.wordRequirement).toBe('200〜240語')
    expect(grading.taskRequirements).toEqual({
      kind: 'list',
      items: ['設問の問いに答える', '理由を 2 つ以上挙げる', '結論を述べる']
    })
    expect(grading.warnings).toEqual(['語数が目安より少なめです。'])
    expect(grading.japanese.tags).toEqual(['設問適合', '理由の充実', '接続表現'])
    expect(grading.japanese.summary).toContain('第 3 回')
    expect(grading.japanese.notice).toBe('本結果は公式採点ではありません。')
    expect(grading.createdAt).toBe('2026-09-26T20:30:00.000Z')
  })

  it('古い形式は足りない項目を空にする（4 観点・修正ポイント・作文例は作らない）', () => {
    const grading = normalizeGradingReport(row(oldFormatReport(), { level: 'PRE1', score: 18, maxScore: 16, wordCount: 130 }))

    // 行の値はそのまま使う
    expect(grading.level).toBe('PRE1')
    expect(grading.score).toBe(18)
    expect(grading.maxScore).toBe(16)
    expect(grading.wordCount).toBe(130)
    // 1 観点の満点は級から決まる（準 1 級は 4）
    expect(grading.rubricMax).toBe(4)

    // 無いものは空にする（見出しだけ残さないため。画面はこれを見てブロックを出さない）
    expect(grading.japanese.rubric).toEqual([])
    expect(grading.chinese.rubric).toEqual([])
    expect(grading.japanese.corrections).toEqual([])
    expect(grading.chinese.corrections).toEqual([])
    expect(grading.japanese.tags).toEqual([])
    expect(grading.modelAnswer).toBe('')
    expect(grading.taskRequirements).toBeNull()
    expect(grading.warnings).toEqual([])
    // 語数の目安は**埋め草にしない**（勝手な値を出さない）
    expect(grading.wordRequirement).toBe('')

    // 有るものはそのまま出す
    expect(grading.japanese.title).toBe('主張は伝わります')
    expect(grading.japanese.advice).toBe('理由をもう 1 つ足しましょう。')
    expect(grading.chinese.summary).toBe('按四项观点进行了评价。')
  })

  it('round は API の行の値をそのまま使う（失敗した回があってもずれない）', () => {
    expect(normalizeGradingReport(row(newFormatReport(), { round: 1 })).round).toBe(1)
    expect(normalizeGradingReport(row(newFormatReport(), { round: 3 })).round).toBe(3)
  })

  it('満点が分からない回は maxScore を「不明」にする（0 を不明の印にしない）', () => {
    const grading = normalizeGradingReport(row(oldFormatReport(), { level: 'PRE1', score: 18, maxScore: null }))

    expect(grading.score).toBe(18)
    // 0 は「満点が 0 点」と区別できないので使わない。無いことは undefined で表す
    expect(grading.maxScore).toBeUndefined()
    expect(grading.maxScore).not.toBe(0)
  })

  it('得点が分からない回は score も「不明」にする（0 を不明の印にしない）', () => {
    const grading = normalizeGradingReport(row(oldFormatReport(), {
      level: 'PRE1',
      score: null,
      maxScore: 16
    }))

    // `0 / 16` のような**嘘の数字**を出さないために、0 では埋めない
    expect(grading.score).toBeUndefined()
    expect(grading.score).not.toBe(0)
    expect(grading.maxScore).toBe(16)
  })

  it('語数の目安がオブジェクトの行（実データ）を日本語に組み立てる', () => {
    // 英作文ID 25 の行（2026-09-27 の実データ）
    const grading = normalizeGradingReport(row({
      wordRequirement: { type: 'range', target: null, minimum: 80, maximum: 100 },
      japanese: { summary: '80〜100語の英作文です。' }
    }))

    expect(grading.wordRequirement).toBe('80〜100語')
  })

  it('語数の目安のオブジェクトは target を優先し、片側だけでも組み立てる', () => {
    const target = normalizeGradingReport(row({
      wordRequirement: { type: 'range', target: 120, minimum: 80, maximum: 150 }
    }))
    expect(target.wordRequirement).toBe('120語')

    expect(normalizeGradingReport(row({ wordRequirement: { minimum: 80 } })).wordRequirement).toBe('80語以上')
    expect(normalizeGradingReport(row({ wordRequirement: { maximum: 100 } })).wordRequirement).toBe('100語以内')
    // 読めない形は出さない（勝手な値をでっち上げない）
    expect(normalizeGradingReport(row({ wordRequirement: { type: 'range' } })).wordRequirement).toBe('')
    expect(normalizeGradingReport(row({ wordRequirement: 80 })).wordRequirement).toBe('')
  })

  it('設問の要求がオブジェクトの行（実データ）は、2 言語の 1 段落として読む', () => {
    // 実データの行（`taskRequirements` が commentJa / commentZh のオブジェクト）
    const grading = normalizeGradingReport(row({
      taskRequirements: {
        commentJa: '設問の問いに答え、理由を 2 つ以上挙げて結論を述べる。',
        commentZh: '回答题目要求，列出两个以上理由并给出结论。'
      }
    }))

    expect(grading.taskRequirements).toEqual({
      kind: 'note',
      ja: '設問の問いに答え、理由を 2 つ以上挙げて結論を述べる。',
      zh: '回答题目要求，列出两个以上理由并给出结论。'
    })
  })

  it('設問の要求が読めない形なら null にする（節ごと出さない）', () => {
    for (const broken of [undefined, null, '', 42, [], ['  '], {}, { comment: '   ' }]) {
      expect(normalizeGradingReport(row({ taskRequirements: broken })).taskRequirements).toBeNull()
    }
  })

  it('設問の要求の配列からは文字列だけを拾う（数値・null・空白は捨てる）', () => {
    const grading = normalizeGradingReport(row({ taskRequirements: ['設問の問いに答える', 1, null, '  '] }))

    expect(grading.taskRequirements).toEqual({ kind: 'list', items: ['設問の問いに答える'] })
  })

  it('設問の要求が comment だけの行でも読む（両言語に同じ文を出す）', () => {
    const grading = normalizeGradingReport(row({ taskRequirements: { comment: '理由を 2 つ挙げる。' } }))

    expect(grading.taskRequirements).toEqual({
      kind: 'note',
      ja: '理由を 2 つ挙げる。',
      zh: '理由を 2 つ挙げる。'
    })
  })

  it('前後の空白は落として読む（画面に余白を出さない）', () => {
    const grading = normalizeGradingReport(row({
      wordRequirement: '  80〜100語  ',
      taskRequirements: ['  設問の問いに答える  '],
      japanese: { title: '  主張は伝わります  ', advice: '  理由を足しましょう。  ' }
    }))

    expect(grading.wordRequirement).toBe('80〜100語')
    expect(grading.taskRequirements).toEqual({ kind: 'list', items: ['設問の問いに答える'] })
    expect(grading.japanese.title).toBe('主張は伝わります')
    expect(grading.japanese.advice).toBe('理由を足しましょう。')
  })

  it('見本の形（観点がオブジェクトの配列）もそのまま読む', () => {
    const grading = normalizeGradingReport(row({
      version: 'eiken-ai-v1',
      modelAnswer: 'Improved essay.',
      taskRequirements: ['設問の問いに答える'],
      japanese: {
        title: '主張は伝わります。理由をもう一歩',
        summary: '4 観点で 20 / 32 点です。',
        tags: ['設問適合', '理由の提示', 'つながり'],
        rubric: [
          { key: '内容', score: 5, maxScore: 8, note: '設問の問いには答えられています。' },
          { key: '構成', score: 5, maxScore: 8, note: '段落の切れ目が分かりにくい箇所があります。' },
          { key: '語彙', score: 5, maxScore: 8, note: '平易な語に偏っています。' },
          { key: '文法', score: 5, maxScore: 8, note: 'コンマの位置に誤りがあります。' }
        ],
        corrections: [{ original: 'Because ', corrected: 'This is because ', category: '文法', reason: '理由を導く節を独立させます。' }],
        advice: '結論の前に理由を 2 回示すと評価が上がります。',
        notice: '本結果は公式採点ではありません。'
      }
    }))

    expect(grading.japanese.rubric).toHaveLength(4)
    expect(grading.japanese.rubric[0]?.score).toBe(5)
    expect(grading.japanese.rubric[3]?.note).toBe('コンマの位置に誤りがあります。')
    // 前後の空白は落とす（画面では詰めて出るので、値も詰めて持つ）
    expect(grading.japanese.corrections).toEqual([
      { original: 'Because', corrected: 'This is because', category: '文法', reason: '理由を導く節を独立させます。' }
    ])
    // 中国語側が無い回は空にする（日本語だけでも画面は出す）
    expect(grading.chinese.rubric).toEqual([])
    expect(grading.chinese.summary).toBe('')
  })

  it('行の値が JSON より優先される', () => {
    const grading = normalizeGradingReport(row(newFormatReport(), {
      level: 'GRADE2',
      titleJa: '行の題',
      titleZh: '行的标题',
      score: 30,
      maxScore: 16,
      wordCount: 99
    }))

    expect(grading.level).toBe('GRADE2')
    expect(grading.titleJa).toBe('行の題')
    expect(grading.titleZh).toBe('行的标题')
    expect(grading.score).toBe(30)
    expect(grading.maxScore).toBe(16)
    expect(grading.wordCount).toBe(99)
    // 1 観点の満点は**行には無い**ので JSON の値を使う（無ければ級から決める）
    expect(grading.rubricMax).toBe(8)
  })

  it('rubricValues が足りない分は作らない', () => {
    const grading = normalizeGradingReport(row({
      rubricMax: 8,
      rubricValues: [6, 5],
      japanese: {
        rubric: ['内容', '構成', '語彙', '文法'],
        rubricNotes: ['内容の講評', '構成の講評', '語彙の講評', '文法の講評']
      }
    }))

    expect(grading.japanese.rubric.map((item) => item.key)).toEqual(['内容', '構成'])
    expect(grading.japanese.rubric.map((item) => item.score)).toEqual([6, 5])
  })

  it('report が文字列でも読む（2.0 の JSON 列）', () => {
    const grading = normalizeGradingReport(row(JSON.stringify(newFormatReport())))

    expect(grading.japanese.rubric).toHaveLength(4)
    expect(grading.modelAnswer).toContain('In conclusion')
  })

  it('report が無い・壊れていても空のレポートにする', () => {
    for (const broken of [null, undefined, '', '{壊れた', 42, []]) {
      const grading = normalizeGradingReport(row(broken))
      expect(grading.japanese.rubric).toEqual([])
      expect(grading.japanese.corrections).toEqual([])
      expect(grading.modelAnswer).toBe('')
      // 行の値は使う（画面は作文の題と得点を出せる）
      expect(grading.titleJa).toBe('部活動の時間を増やすべきか')
      expect(grading.score).toBe(24)
      expect(grading.maxScore).toBe(32)
    }
  })
})

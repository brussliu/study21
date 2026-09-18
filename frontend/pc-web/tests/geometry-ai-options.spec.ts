import { describe, expect, it } from 'vitest'
import {
  MODE_DEFINITIONS,
  OUTPUT_TYPE_OPTIONS,
  defaultSupplementValues,
  fromSupplementRows,
  modeLabel,
  outputTypeLabel,
  supplementFieldsFor,
  toSupplementPayload,
  type SupplementValues
} from '@/features/geometry/geometry-ai-options'

/**
 * AI 生図の作図方法（A〜D）・作成する図の種類・補充項目の定義。
 *
 * 確かめること:
 *  ・作図方法は A〜D の 4 つで、B だけ種類を選ばせない（GRAPH 固定）
 *  ・作成する図の種類は AUTO が既定
 *  ・補充項目はモードと種類で変わり、**当てはまらない項目は送らない**
 *  ・保存済みの補充（日本語の項目名）から画面の値へ戻せる（追加入力待ちの再開）
 */
describe('AI 生図の作図方法と結果種別', () => {
  it('作図方法は A〜D の 4 つで、B だけ種類を選ばせない', () => {
    expect(MODE_DEFINITIONS.map((definition) => definition.mode)).toEqual(['A', 'B', 'C', 'D'])
    expect(MODE_DEFINITIONS.filter((definition) => definition.asksOutputType).map((d) => d.mode))
      .toEqual(['A', 'C', 'D'])
    expect(modeLabel('A')).toBe('画像をもとに再現')
    expect(modeLabel('B')).toBe('数式からグラフを作成')
    expect(modeLabel('C')).toBe('文章の条件から作図')
    expect(modeLabel('D')).toBe('文章と図を合わせて作図')
  })

  it('作成する図の種類は 4 つで、AUTO が既定（自動判定）', () => {
    expect(OUTPUT_TYPE_OPTIONS.map((option) => option.value)).toEqual([
      'AUTO', 'GEOMETRY', 'GRAPH', 'MIXED'
    ])
    expect(outputTypeLabel('AUTO')).toBe('自動判定')
    expect(outputTypeLabel(null)).toBe('自動判定')
  })

  it('どのモードにも「読み取る範囲」の案内がある', () => {
    for (const definition of MODE_DEFINITIONS) {
      expect(definition.cropHint.length).toBeGreaterThan(0)
      expect(definition.description.length).toBeGreaterThan(0)
    }
  })
})

describe('補充項目の出し分け', () => {
  it('A は再現の重点・情報不足の扱いを持ち、グラフのときだけ座標の範囲を出す', () => {
    const geometry = supplementFieldsFor('A', 'GEOMETRY').map((field) => field.key)
    expect(geometry).toContain('reproduceFocus')
    expect(geometry).toContain('whenInsufficient')
    expect(geometry).not.toContain('coordinateRange')

    const graph = supplementFieldsFor('A', 'GRAPH').map((field) => field.key)
    expect(graph).toContain('coordinateRange')
  })

  it('B は式・パラメータ・定義域・表示範囲を持ち、幾何の項目は出さない', () => {
    const keys = supplementFieldsFor('B', 'GRAPH').map((field) => field.key)
    expect(keys).toEqual(expect.arrayContaining([
      'formulaCorrection', 'parameters', 'domain', 'viewRange', 'showAuxiliary'
    ]))
    expect(keys).not.toContain('reproduceFocus')
  })

  it('C は目標と情報不足の扱いを持ち、グラフのときだけパラメータ・定義域・表示範囲を出す', () => {
    const mixed = supplementFieldsFor('C', 'MIXED').map((field) => field.key)
    expect(mixed).toEqual(expect.arrayContaining([
      'goal', 'problemCorrection', 'whenInsufficient', 'parameters', 'domain', 'viewRange'
    ]))

    const geometry = supplementFieldsFor('C', 'GEOMETRY').map((field) => field.key)
    expect(geometry).not.toContain('parameters')
    expect(geometry).not.toContain('domain')
  })

  it('D は目標・残す対象・追加変更を持ち、グラフのときだけ表示範囲を出す', () => {
    const keys = supplementFieldsFor('D', 'GRAPH').map((field) => field.key)
    expect(keys).toEqual(expect.arrayContaining([
      'goal', 'keepObjects', 'changeObjects', 'textCorrection', 'viewRange'
    ]))
  })

  it('上級の項目には印が付く（単純な項目を先に出す）', () => {
    const fields = supplementFieldsFor('B', 'GRAPH')
    const advanced = fields.filter((field) => field.advanced === true).map((field) => field.key)
    expect(advanced).toContain('formulaCorrection')
    expect(advanced).not.toContain('viewRange')
  })

  it('モードごとの既定値が入る（A・C は確認してから進める、D は再現）', () => {
    expect(defaultSupplementValues('A', 'GEOMETRY').whenInsufficient).toBe('ASK_FIRST')
    expect(defaultSupplementValues('C', 'GEOMETRY').whenInsufficient).toBe('ASK_FIRST')
    expect(defaultSupplementValues('D', 'GEOMETRY').goal).toBe('REPRODUCE')
    expect(defaultSupplementValues('B', 'GRAPH').showAuxiliary).toBe('false')
  })
})

describe('送信する補充パラメータ', () => {
  const values: SupplementValues = {
    reproduceFocus: 'MATH_FIRST',
    whenInsufficient: 'ASK_FIRST',
    knownValues: 'AB = 5',
    coordinateRange: 'x: -5..5',
    formulaCorrection: 'x2 → x²',
    parameters: 'a = 2',
    domain: 'すべての実数',
    viewRange: 'x: -10..10',
    showAuxiliary: 'true',
    goal: 'GIVEN_ONLY',
    problemCorrection: '問題文の訂正',
    keepObjects: '円 c',
    changeObjects: '垂線を追加',
    textCorrection: 'ラベルの訂正',
    keepLabels: true
  }

  it('幾何図形のときはグラフ用の項目を送らない（種類が AUTO のときも送らない）', () => {
    const payload = toSupplementPayload('A', 'GEOMETRY', values)
    expect(payload.reproduceFocus).toBe('MATH_FIRST')
    expect(payload.knownValues).toBe('AB = 5')
    expect(payload.coordinateRange).toBeUndefined()

    const auto = toSupplementPayload('A', 'AUTO', values)
    expect(auto.coordinateRange).toBeUndefined()
    expect(auto.reproduceFocus).toBe('MATH_FIRST')
  })

  it('グラフのときは座標の範囲を送る', () => {
    expect(toSupplementPayload('A', 'GRAPH', values).coordinateRange).toBe('x: -5..5')
    expect(toSupplementPayload('D', 'MIXED', values).viewRange).toBe('x: -10..10')
  })

  it('B では他のモードの項目を送らない', () => {
    const payload = toSupplementPayload('B', 'GRAPH', values)
    expect(Object.keys(payload).sort()).toEqual(
      ['domain', 'formulaCorrection', 'keepLabels', 'parameters', 'showAuxiliary', 'viewRange'])
    expect(payload.showAuxiliary).toBe(true)
  })

  it('空欄の項目は送らない（指定なしとして扱う）', () => {
    const empty: SupplementValues = { ...values, knownValues: '   ', reproduceFocus: '' }
    const payload = toSupplementPayload('A', 'GEOMETRY', empty)
    expect(payload.knownValues).toBeUndefined()
    expect(payload.reproduceFocus).toBeUndefined()
  })

  it('元の名前とラベルは既定で「残す」（チェックを外したときだけ false）', () => {
    expect(toSupplementPayload('A', 'GEOMETRY', values).keepLabels).toBe(true)
    expect(toSupplementPayload('A', 'GEOMETRY', { ...values, keepLabels: false }).keepLabels).toBe(false)
  })
})

describe('保存済みの補充からの復元（追加入力待ちの再開）', () => {
  it('日本語の項目名から画面の値へ戻す', () => {
    const rows = {
      再現の重点: '数学的な関係を優先',
      '情報が足りないとき': '確認してから進める',
      '既知の値（式・点・寸法・角）': 'AB = 5',
      元の名前とラベル: 'いいえ（元の名前・ラベルにこだわらない）'
    }
    const restored = fromSupplementRows(rows)
    expect(restored.reproduceFocus).toBe('MATH_FIRST')
    expect(restored.whenInsufficient).toBe('ASK_FIRST')
    expect(restored.knownValues).toBe('AB = 5')
    expect(restored.keepLabels).toBe(false)
  })

  it('知らない項目は無視する（古い要求でも壊れない）', () => {
    expect(fromSupplementRows({ 知らない項目: 'x' })).toEqual({})
  })
})

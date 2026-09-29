import { describe, expect, it } from 'vitest'
import { acquirePlan, needsAiFetch } from '@/features/japanese-word/acquirePlan'
import type { JpnWord, JpnWordAiState } from '@/api/japanese'

/**
 * 右上の A〜E 取得ボタンを押したときの**対象の決め方**（2.0 の英語学習の単語情報管理
 * `word.jsp` の「取得方法」の窓と同じ考え方）。
 *
 * <p>利用者の指示（2026-09-26）: 4 つの取得ボタンは押した瞬間に走らせず、**窓を出して**
 * 「取得済みをスキップ」か「すべて再取得」かを選ばせる。一覧は語ごとに A・B／C／D／E の
 * 取得状態を持っているので、どちらを選ぶと何語が対象になるかはここで決められる
 * （＝窓に件数を出せる。AI を呼ぶ前に確認できる）。</p>
 *
 * <p>確かめる接縫: <b>一覧の行と区画・選択から、対象の語と件数が決まる</b>こと。
 * ここは純関数なので、通信も画面も絡めずに境界（未取得・失敗・取得中・上限）を固定できる。</p>
 */

/** 1 語ぶんの行（テストに要る列だけ。AI の状態は区画ごとに渡す）。 */
function row(wordId: number, aiState: Partial<JpnWordAiState> = {}): JpnWord {
  return {
    wordId,
    aiState: { detail: null, reading: null, context: null, kanji: null, ...aiState }
  } as JpnWord
}

describe('AI 取得の対象の決め方', () => {
  const ROWS = [
    row(1, { detail: 'SUCCEEDED' }),
    row(2, { detail: 'FAILED' }),
    row(3),
    row(4, { detail: 'SUCCEEDED' }),
    row(5, { detail: 'RUNNING' })
  ]

  it('区画ごとの状態を見る（A・B は detail、C は reading、D は context、E は kanji）', () => {
    const rows = [
      row(1, { reading: 'SUCCEEDED' }),
      row(2, { context: 'SUCCEEDED' }),
      row(3, { kanji: 'SUCCEEDED' })
    ]

    expect(acquirePlan(rows, 'C', true, 50).wordIds).toEqual([2, 3])
    expect(acquirePlan(rows, 'D', true, 50).wordIds).toEqual([1, 3])
    expect(acquirePlan(rows, 'E', true, 50).wordIds).toEqual([1, 2])
    // A・B は detail だけを見る（他の区画が取得済みでも対象）
    expect(acquirePlan(rows, 'AB', true, 50).wordIds).toEqual([1, 2, 3])
  })

  it('「取得済みをスキップ」は取得済みと取得中を外し、未取得と失敗を対象にする', () => {
    const plan = acquirePlan(ROWS, 'AB', true, 50)

    // 未取得(3) と失敗(2) だけが対象。取得済み(1,4) と取得中(5) は外す
    expect(plan.wordIds).toEqual([2, 3])
    expect(plan.total).toBe(5)
    expect(plan.acquired).toBe(2)
    expect(plan.skipped).toBe(3)
    expect(plan.targets).toBe(2)
  })

  it('「すべて再取得」は表示中の語をそのまま対象にする（スキップ 0）', () => {
    const plan = acquirePlan(ROWS, 'AB', false, 50)

    expect(plan.wordIds).toEqual([1, 2, 3, 4, 5])
    expect(plan.total).toBe(5)
    expect(plan.acquired).toBe(2)
    expect(plan.skipped).toBe(0)
    expect(plan.targets).toBe(5)
  })

  it('上限で切る（表示中が上限より多いときは、残りを overLimit で知らせる）', () => {
    // 上限は API 側と揃えた 1 回あたりの語数。切った分は窓に出す
    const plan = acquirePlan(ROWS, 'AB', false, 3)

    expect(plan.wordIds).toEqual([1, 2, 3])
    expect(plan.targets).toBe(3)
    expect(plan.overLimit).toBe(2)
  })

  it('スキップしてから上限で切る（取得済みを外した分だけ多く取れる）', () => {
    const rows = [
      row(1, { detail: 'SUCCEEDED' }),
      row(2, { detail: 'SUCCEEDED' }),
      row(3, { detail: 'SUCCEEDED' }),
      row(4),
      row(5),
      row(6)
    ]

    const plan = acquirePlan(rows, 'AB', true, 2)

    // 取得済み 3 語を外してから 2 語に切る（切る前に対象を絞る）
    expect(plan.wordIds).toEqual([4, 5])
    expect(plan.acquired).toBe(3)
    expect(plan.skipped).toBe(3)
    expect(plan.targets).toBe(2)
    expect(plan.overLimit).toBe(1)
  })

  it('全部取得済みなら対象は 0（窓で「取得済みをスキップ」を選べることは分かる）', () => {
    const rows = [row(1, { detail: 'SUCCEEDED' }), row(2, { detail: 'SUCCEEDED' })]

    const plan = acquirePlan(rows, 'AB', true, 50)

    expect(plan.wordIds).toEqual([])
    expect(plan.acquired).toBe(2)
    expect(plan.skipped).toBe(2)
    expect(plan.targets).toBe(0)
  })

  it('表示中に語が無ければ対象も 0', () => {
    const plan = acquirePlan([], 'AB', false, 50)

    expect(plan.wordIds).toEqual([])
    expect(plan.total).toBe(0)
    expect(plan.acquired).toBe(0)
    expect(plan.overLimit).toBe(0)
  })

  it('使った上限を計画に残す（窓が「1 回の受付は N 語」と出せる）', () => {
    // 上限は設定ページの「1 回の最大単語数」から来る（固定値ではない）。
    // 窓はその数をそのまま出すので、計画に残しておく
    const plan = acquirePlan(ROWS, 'AB', false, 3)

    expect(plan.limit).toBe(3)
    expect(acquirePlan(ROWS, 'AB', true, 10).limit).toBe(10)
  })

  it('取得が要る状態かどうかは 1 か所で決める（取得済みと取得中は要らない）', () => {
    // 「未取得」は状態コードが無い（null / 空文字）ことを表す
    expect(needsAiFetch(null)).toBe(true)
    expect(needsAiFetch('')).toBe(true)
    expect(needsAiFetch('FAILED')).toBe(true)
    expect(needsAiFetch('CANCELED')).toBe(true)
    // 一度でも成功した内容と、いま走っている内容は呼び直さない
    expect(needsAiFetch('SUCCEEDED')).toBe(false)
    expect(needsAiFetch('RUNNING')).toBe(false)
    expect(needsAiFetch('QUEUED')).toBe(false)
  })
})

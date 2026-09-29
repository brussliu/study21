import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import JpnAiFetchDialog from '@/features/japanese-word/JpnAiFetchDialog.vue'
import { acquirePlan, type JpnAcquirePlan } from '@/features/japanese-word/acquirePlan'
import type { JpnAiStateKey } from '@/features/japanese-word/aiStateLabel'
import type { JpnWord, JpnWordAiState } from '@/api/japanese'

/**
 * AI 取得の窓（右上の**1 つの**取得ボタンを押すと出る）。
 *
 * <p>利用者の指示（2026-09-26）: 4 つ並んでいた取得ボタンは場所を取るので**1 つにまとめ**、
 * 押したら**窓の中でどの情報を取るかを選ぶ**。窓は「どの情報か」と「取得済みをどうするか」を
 * 同じ 1 つの中で決める。</p>
 *
 * <p>確かめる接縫: <b>窓に何が出て、どの選択が外へ伝わるか</b>。件数の計算は `acquirePlan` の
 * テストが見るので、ここは文言・切り替え・合図だけを見る。</p>
 */

function row(wordId: number, aiState: Partial<JpnWordAiState> = {}): JpnWord {
  return {
    wordId,
    aiState: { detail: null, reading: null, context: null, kanji: null, ...aiState }
  } as JpnWord
}

const ROWS = [
  row(101, { detail: 'SUCCEEDED' }),
  row(102, { detail: 'FAILED' }),
  row(103),
  row(104, { detail: 'SUCCEEDED' })
]

const SECTIONS: JpnAiStateKey[] = ['AB', 'C', 'D', 'E']

/** 区画ごとの上限（設定ページの「1 回の最大単語数」から来る想定。区画ごとに違う）。 */
const LIMITS: Record<JpnAiStateKey, number> = { AB: 10, C: 50, D: 30, E: 50 }

/** 区画ごとの計画（画面が窓へ渡す形）。 */
function plansOf(
  rows: JpnWord[],
  limits: Record<JpnAiStateKey, number> = LIMITS
): Record<JpnAiStateKey, { skip: JpnAcquirePlan; all: JpnAcquirePlan }> {
  return Object.fromEntries(SECTIONS.map((key) => [
    key,
    { skip: acquirePlan(rows, key, true, limits[key]), all: acquirePlan(rows, key, false, limits[key]) }
  ])) as Record<JpnAiStateKey, { skip: JpnAcquirePlan; all: JpnAcquirePlan }>
}

function setup(
  options: {
    visible?: boolean
    rows?: JpnWord[]
    limits?: Record<JpnAiStateKey, number>
    targetLabel?: string
  } = {}
) {
  const wrapper = mount(JpnAiFetchDialog, {
    props: {
      visible: options.visible ?? true,
      targetLabel: options.targetLabel ?? '表示中の 4 語',
      plans: plansOf(options.rows ?? ROWS, options.limits ?? LIMITS)
    }
  })
  return wrapper
}

describe('AI 取得の窓', () => {
  it('どの情報を取るかを窓の中で選べる（4 つ。最初は詳細情報（A・B））', () => {
    const wrapper = setup()

    expect(wrapper.find('[data-jp-fetch-dialog]').exists()).toBe(true)
    expect(wrapper.get('[data-jp-fetch-title]').text()).toContain('AI 取得')
    // 4 つの情報（2.0 の 4 ボタン相当）を 1 つの窓にまとめる
    const kinds = wrapper.findAll('[data-jp-fetch-kind]')
    expect(kinds.map((kind) => kind.attributes('value'))).toEqual(SECTIONS)
    expect((wrapper.get('[data-jp-fetch-kind="AB"]').element as HTMLInputElement).checked).toBe(true)

    const labels = wrapper.findAll('.jp-fetch__kind-label').map((label) => label.text())
    expect(labels[0]).toContain('詳細情報（A・B）')
    expect(labels[1]).toContain('読み問題（C）')
    expect(labels[2]).toContain('文脈問題（D）')
    expect(labels[3]).toContain('漢字問題（E）')
  })

  it('選んだ情報の件数に切り替わる（AI を呼ぶ前に確かめられる）', async () => {
    const wrapper = setup()

    // A・B は 2 語が取得済み → スキップすると 2 語。上限は設定ページの値（A・B は 10 語）
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('表示中の 4 語が対象です')
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('うち取得済み 2 語')
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('1 回の受付は 10 語')
    expect(wrapper.get('[data-jp-fetch-skip]').text()).toContain('未取得と失敗の 2 語')

    // C はまだ 1 語も取得していない → スキップしても 4 語。上限は C の設定値（50 語）
    await wrapper.get('[data-jp-fetch-kind="C"]').setValue()
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('うち取得済み 0 語')
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('1 回の受付は 50 語')
    expect(wrapper.get('[data-jp-fetch-skip]').text()).toContain('未取得と失敗の 4 語')

    // D も同じ（区画ごとの状態を見ている）
    await wrapper.get('[data-jp-fetch-kind="D"]').setValue()
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('うち取得済み 0 語')
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('1 回の受付は 30 語')
  })

  it('対象の説明は外から受け取る（一覧全体か、行の 1 語か）', () => {
    // 一覧の右上のボタンから開いたとき
    expect(setup().get('[data-jp-fetch-summary]').text()).toContain('表示中の 4 語が対象です')

    // 行の操作（AI 取得アイコン）から開いたとき。窓は同じで、対象の説明だけが変わる
    const row = setup({ rows: [ROWS[2]!], targetLabel: 'この単語（図書館）' })
    const summary = row.get('[data-jp-fetch-summary]').text()
    expect(summary).toContain('この単語（図書館）が対象です')
    expect(summary).toContain('うち取得済み 0 語')
  })

  it('情報と取得方法の両方を外へ伝える（スキップ＝true / すべて再取得＝false）', async () => {
    const wrapper = setup()

    // 何も触らなければ A・B
    await wrapper.get('[data-jp-fetch-skip]').trigger('click')

    await wrapper.get('[data-jp-fetch-kind="E"]').setValue()
    await wrapper.get('[data-jp-fetch-reacquire]').trigger('click')

    expect(wrapper.emitted('choose')).toEqual([['AB', true], ['E', false]])
  })

  it('取得方法の文言は、選んだ情報の件数を出す', async () => {
    const wrapper = setup()

    await wrapper.get('[data-jp-fetch-kind="C"]').setValue()

    const skip = wrapper.get('[data-jp-fetch-skip]')
    expect(skip.text()).toContain('取得済みをスキップ')
    expect(skip.text()).toContain('未取得と失敗の 4 語')
    expect(skip.text()).toContain('取得済みと取得中は呼びません')

    const all = wrapper.get('[data-jp-fetch-reacquire]')
    expect(all.text()).toContain('すべて再取得')
    // 対象の件数（一覧のときも、行の 1 語のときも同じ言い方にする）
    expect(all.text()).toContain('対象の 4 語')
    expect(all.text()).toContain('新しい版')
  })

  it('キャンセルと閉じるで閉じられる（背景クリックでは閉じない。何も実行しない）', async () => {
    const wrapper = setup()

    await wrapper.get('[data-jp-fetch-cancel]').trigger('click')
    await wrapper.get('[data-jp-fetch-close]').trigger('click')
    // 背景（灰色の部分）をクリックしても閉じない（docs/FRONTEND_GUIDE.md §3.9 の利用者指定。
    // 閉じる手段は ×・閉じる・キャンセルだけ）
    await wrapper.get('[data-jp-fetch-backdrop]').trigger('click')

    expect(wrapper.emitted('close')).toHaveLength(2)
    expect(wrapper.emitted('choose')).toBeUndefined()
  })

  it('表示中の語が上限より多いときは、次回に回る語数を知らせる', async () => {
    // 上限は A・B が 3 語（C は設定値どおり 50 語）
    const wrapper = setup({ limits: { AB: 3, C: 50, D: 30, E: 50 } })

    expect(wrapper.get('[data-jp-fetch-dialog]').text()).toContain('1 回の受付を超える 1 語は次回に回します')

    // 選んだ情報の計画で数える（C は取得済みが無く、上限 50 なので超える語は無い）
    await wrapper.get('[data-jp-fetch-kind="C"]').setValue()
    expect(wrapper.find('[data-jp-fetch-over-limit]').exists()).toBe(false)
  })

  it('表示していないときは何も出さない', () => {
    const wrapper = setup({ visible: false })

    expect(wrapper.find('[data-jp-fetch-dialog]').exists()).toBe(false)
  })
})

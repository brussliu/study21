import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import WordTestStats from '@/components/home/WordTestStats.vue'
import { WORD_TEST_MINIMUM_MINUTES, buildWordTestDays } from '@/features/home/homeMockData'

/**
 * ホームの「直近単語テスト情報統計」（2.0 english.jsp の複合グラフ）。
 *
 * 2.0 は Chart.js で 30 日ぶんを描いていた（棒＝単語数 / 破線＝時間(分) /
 * バブル＝中断回数 / 赤い破線＝最低時間 25 分）。2.1 は SVG で同じ形を出す。
 * 単語テストのデータはまだ無いため仮データ。
 */
describe('ホーム：直近単語テスト情報統計', () => {
  it('30 日ぶんの棒・折れ線・バブルと最低時間ラインを出す', () => {
    const wrapper = mount(WordTestStats)

    expect(wrapper.text()).toContain('直近単語テスト情報統計')
    expect(wrapper.text()).toContain('過去 1 ヶ月')

    // 棒は 30 日ぶん
    expect(wrapper.findAll('[data-word-test-bar]').length).toBe(30)
    // 折れ線と最低時間ライン
    expect(wrapper.find('[data-word-test-line]').exists()).toBe(true)
    expect(wrapper.find('[data-word-test-minimum]').exists()).toBe(true)
    // バブルは中断回数がある日だけ（サンプルでは日曜が 0 回）
    const bubbles = wrapper.findAll('[data-word-test-bubble]')
    expect(bubbles.length).toBeGreaterThan(0)
    expect(bubbles.length).toBeLessThanOrEqual(30)

    // 凡例
    const legend = wrapper.get('.home-word-test__legend').text()
    for (const label of ['単語数（平日）', '単語数（週末）', '時間（分）', '中断回数', '最低時間 25 分']) {
      expect(legend).toContain(label)
    }
  })

  it('X 軸は 1 日おきの日付、週末は色を変える', () => {
    const wrapper = mount(WordTestStats)
    const days = buildWordTestDays()

    const labels = wrapper.findAll('.home-word-test__axis text').map((el) => el.text())
    // 日付ラベル（MM/DD）が 1 日おきに入る
    expect(labels).toContain(days[0]?.label)
    expect(labels.filter((text) => /^\d{2}\/\d{2}$/.test(text)).length).toBe(15)
    // 週末の棒にはクラスが付く
    const weekendBars = wrapper.findAll('[data-word-test-bar].is-weekend')
    expect(weekendBars.length).toBeGreaterThan(0)
    expect(weekendBars.length).toBeLessThan(30)
  })

  it('集計（計測日数・単語数・時間・中断・最低時間以上）を出す', () => {
    const wrapper = mount(WordTestStats)
    const days = buildWordTestDays()
    const tested = days.filter((day) => day.wordCount > 0)
    const above = days.filter((day) => day.minutes >= WORD_TEST_MINIMUM_MINUTES).length

    const summary = wrapper.get('.home-word-test__summary').text()
    expect(summary).toContain(`計測 ${tested.length} 日`)
    expect(summary).toContain(`単語 ${tested.reduce((sum, day) => sum + day.wordCount, 0).toLocaleString()} 語`)
    expect(summary).toContain(`最低時間（${WORD_TEST_MINIMUM_MINUTES} 分）以上 ${above} 日`)
  })

  it('サンプルデータであることを画面に書いてある', () => {
    const wrapper = mount(WordTestStats)

    expect(wrapper.text()).toContain('サンプルデータ')
  })
})

import { describe, expect, it, beforeEach } from 'vitest'
import { mount } from '@vue/test-utils'
import HomeView from '@/views/home/HomeView.vue'

/**
 * ホーム（2.0 の english.jsp の内容をまとめた画面）。
 *
 * KPI・レーダーチャート（初級／中級）・学習状況の一覧がそろっていること、
 * いまはサンプルデータを表示していることを固定する。
 */
beforeEach(() => {
  window.sessionStorage.setItem('study21.auth.v2', JSON.stringify({ username: '検証 生徒', role: 'STUDENT' }))
})

describe('ホーム', () => {
  it('2.0 の英語勉強の KPI をまとめて出す', () => {
    const wrapper = mount(HomeView)

    const labels = wrapper.findAll('.home-kpi__label').map((el) => el.text())
    for (const label of ['総学習時間', '学習レベル', '今日の学習時間', '今日の読書時間', '連続学習日数']) {
      expect(labels.some((text) => text.includes(label))).toBe(true)
    }
    expect(wrapper.get('[data-kpi="total"]').text()).toContain('128時間30分')
    expect(wrapper.get('[data-kpi="streak"]').text()).toContain('14日')
  })

  it('初級・中級のレーダーチャートを出す（現在値と目標値）', () => {
    const wrapper = mount(HomeView)

    expect(wrapper.find('[data-radar="beginner"]').exists()).toBe(true)
    expect(wrapper.find('[data-radar="intermediate"]').exists()).toBe(true)
    const beginner = wrapper.get('[data-radar="beginner"]')
    // 目盛り枠 4 + 目標 + 現在 = 6 個の polygon、軸 5 本、ラベル 5 個
    expect(beginner.findAll('polygon').length).toBe(6)
    expect(beginner.findAll('line').length).toBe(5)
    expect(beginner.findAll('text').map((t) => t.text())).toEqual(['単語', '熟語', '文法', '読解', 'リスニング'])
    expect(wrapper.text()).toContain('現在')
    expect(wrapper.text()).toContain('目標')
  })

  it('学習状況の一覧と進捗バーを出す', () => {
    const wrapper = mount(HomeView)

    const items = wrapper.findAll('[data-home-item]').map((el) => el.attributes('data-home-item'))
    expect(items).toEqual(['word', 'phrase', 'word-test', 'phrase-test', 'status', 'reading'])
    expect(wrapper.get('[data-home-item="word"]').text()).toContain('1,240 語')
    expect(wrapper.findAll('.home-bar__fill').length).toBeGreaterThan(0)
  })

  it('サンプルデータであることを画面に書いてある', () => {
    const wrapper = mount(HomeView)
    expect(wrapper.text()).toContain('サンプルデータ')
  })

  it('表示名をセッションから出す', () => {
    const wrapper = mount(HomeView)
    expect(wrapper.text()).toContain('検証 生徒')
  })
})

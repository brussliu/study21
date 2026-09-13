import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ActionTimeline from '@/components/home/ActionTimeline.vue'

/**
 * ホームの「行動タイムライン」（2.0 home.jsp の home-monitor-card）。
 *
 * 2.0 は学習状況モニターのスナップショット（AI 分析つき）を時間軸に並べていた。
 * 2.1 はその API が未実装のため同じサンプルデータで見た目を再現している
 * （学習状況モニター画面と同じ仮データ）。
 */
describe('ホーム：行動タイムライン', () => {
  it('サンプルのスナップショットを区間にして、凡例と学習時間を出す', async () => {
    const wrapper = mount(ActionTimeline)

    expect(wrapper.text()).toContain('行動タイムライン')
    expect(wrapper.text()).toContain('学習状況モニターの分析結果を時間軸で表示します。')
    // 既定は 08:00〜23:59
    expect((wrapper.get('[data-timeline-from]').element as HTMLInputElement).value).toBe('08:00')
    expect((wrapper.get('[data-timeline-to]').element as HTMLInputElement).value).toBe('23:59')

    // 区間（同じ分析結果が続くところはまとまる）
    const segments = wrapper.findAll('[data-timeline-segment]')
    expect(segments.length).toBeGreaterThan(0)
    expect(segments.map((segment) => segment.attributes('data-timeline-segment')))
      .toContain('study')
    // 午前は学習、昼は休憩、夕方はゲームというサンプル
    const slugs = new Set(segments.map((segment) => segment.attributes('data-timeline-segment')))
    expect(slugs.has('break')).toBe(true)
    expect(slugs.has('game')).toBe(true)

    // 目盛りと凡例、状態の行
    expect(wrapper.findAll('.home-timeline__tick').length).toBeGreaterThan(0)
    const legend = wrapper.get('[data-timeline-legend]').text()
    expect(legend).toContain('学習中')
    const status = wrapper.get('[data-timeline-status]').text()
    expect(status).toMatch(/\d+ 枚 \/ \d+ 区間｜学習時間 \d{2}:\d{2}（[\d.]+%）/)
  })

  it('区間は開始位置と幅を % で持つ（2.0 と同じ絶対配置）', async () => {
    const wrapper = mount(ActionTimeline)

    const first = wrapper.findAll('[data-timeline-segment]')[0]
    const style = first.attributes('style') ?? ''
    expect(style).toMatch(/left: [\d.]+%/)
    expect(style).toMatch(/width: [\d.]+%/)
  })

  it('時間帯を狭めると区間が減り、開始＞終了では案内を出す', async () => {
    const wrapper = mount(ActionTimeline)
    const before = wrapper.findAll('[data-timeline-segment]').length

    // 08:00〜09:00 に絞る（サンプルは 08:05 以降に学習）
    await wrapper.get('[data-timeline-to]').setValue('09:00')
    const narrowed = wrapper.findAll('[data-timeline-segment]').length
    expect(narrowed).toBeLessThan(before)

    // 開始＞終了
    await wrapper.get('[data-timeline-from]').setValue('22:00')
    expect(wrapper.text()).toContain('終了時刻は開始時刻より後に設定してください。')
    expect(wrapper.findAll('[data-timeline-segment]').length).toBe(0)
  })

  it('サンプルデータであることを画面に書いてある', () => {
    const wrapper = mount(ActionTimeline)

    expect(wrapper.text()).toContain('サンプルデータ')
    expect(wrapper.text()).toContain('batL02')
  })
})

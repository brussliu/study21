import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { UseTimersResult } from '@/features/home/useTimers'
import {
  STORAGE_KEY,
  TIMER_DEFAULTS,
  createTimersState,
  formatRemaining,
  normalizeSeconds,
  parseTimersState,
  serializeTimersState,
  setDuration,
  startTimer,
  stopTimer,
  tickTimers
} from '@/features/home/homeTimers'

/**
 * 学習・休憩・ゲームタイマー（2.0 home.jsp / js/home.js の移植）。
 *
 * 2.0 はサーバーに残り秒数と実行状態を持っていたが、2.1 はブラウザ内（localStorage）で持つ。
 * 「保存した時刻との差」から残り時間を計算し直すので、再読み込みしても続きから動く。
 */
const BASE = Date.UTC(2026, 8, 12, 3, 0, 0) // 2026-09-12 12:00 (JST)

/** `setup` が返した useTimers の結果（テスト用の型付け）。 */
function timersOf(wrapper: { vm: unknown }): UseTimersResult {
  return (wrapper.vm as { timers: UseTimersResult }).timers
}

describe('タイマーの計算（純粋な部分）', () => {
  it('既定は 学習 30 分・休憩 15 分・ゲーム 30 分（2.0 と同じ）', () => {
    const state = createTimersState(BASE)

    expect(TIMER_DEFAULTS).toEqual({ study: 1800, break: 900, game: 1800 })
    expect(state.timers.study).toEqual({ total: 1800, remaining: 1800, running: false })
    expect(state.timers.break.remaining).toBe(900)
    expect(state.timers.game.remaining).toBe(1800)
    expect(state.updatedAt).toBe(BASE)
  })

  it('開始すると動き出し、経過した秒数だけ残りが減る', () => {
    const started = startTimer(createTimersState(BASE), 'study', BASE)

    expect(started.timers.study.running).toBe(true)
    const after90 = tickTimers(started, BASE + 90_000)
    expect(after90.timers.study.remaining).toBe(1800 - 90)
    // 動いていないタイマーは減らない
    expect(after90.timers.break.remaining).toBe(900)
  })

  it('停止すると残りはそのまま（もう一度 開始 すると続きから動く）', () => {
    const started = startTimer(createTimersState(BASE), 'study', BASE)
    const stopped = stopTimer(started, 'study', BASE + 60_000)

    expect(stopped.timers.study.running).toBe(false)
    expect(stopped.timers.study.remaining).toBe(1740)

    // 止めている間は減らない
    expect(tickTimers(stopped, BASE + 600_000).timers.study.remaining).toBe(1740)
    expect(startTimer(stopped, 'study', BASE + 600_000).timers.study.remaining).toBe(1740)
  })

  it('0 になると止まる（2.0 と同じく 0 では開始できない）', () => {
    const started = startTimer(setDuration(createTimersState(BASE), 'study', 300, BASE), 'study', BASE)
    const done = tickTimers(started, BASE + 300_000)

    expect(done.timers.study.remaining).toBe(0)
    expect(done.timers.study.running).toBe(false)
    expect(startTimer(done, 'study', BASE + 400_000).timers.study.running).toBe(false)
  })

  it('時間を設定すると残りもその時間に戻り、実行中なら止まる', () => {
    const started = startTimer(createTimersState(BASE), 'study', BASE)
    const changed = setDuration(started, 'study', 600, BASE + 10_000)

    expect(changed.timers.study).toEqual({ total: 600, remaining: 600, running: false })
  })

  it('秒数は 1 分〜12 時間に丸める', () => {
    expect(normalizeSeconds(0, 1800)).toBe(1800)
    expect(normalizeSeconds(30, 1800)).toBe(60)
    expect(normalizeSeconds(99 * 60 * 60, 1800)).toBe(12 * 60 * 60)
  })

  it('表示は HH:MM:SS（2.0 と同じ）', () => {
    expect(formatRemaining(1800)).toBe('00:30:00')
    expect(formatRemaining(900)).toBe('00:15:00')
    expect(formatRemaining(0)).toBe('00:00:00')
    expect(formatRemaining(3661)).toBe('01:01:01')
  })

  it('保存した状態を読み直すと、実行中でも経過分を引いた残りになる', () => {
    const started = startTimer(createTimersState(BASE), 'break', BASE)
    const saved = serializeTimersState(started)

    const restored = parseTimersState(saved, BASE + 120_000)
    expect(restored.timers.break.running).toBe(true)
    expect(restored.timers.break.remaining).toBe(900 - 120)
  })

  it('壊れた保存値は既定に戻す', () => {
    expect(parseTimersState(null, BASE).timers.study.remaining).toBe(1800)
    expect(parseTimersState('{', BASE).timers.study.remaining).toBe(1800)
    expect(parseTimersState('{"version":2}', BASE).timers.study.remaining).toBe(1800)
    expect(parseTimersState('{"version":1,"updatedAt":0,"timers":{"study":{}}}', BASE).timers.study.remaining)
      .toBe(1800)
  })
})

describe('タイマーの接続（useTimers）', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.useRealTimers()
  })

  it('開始・停止・時間設定が localStorage に残る', async () => {
    const { useTimers } = await import('@/features/home/useTimers')
    const { mount } = await import('@vue/test-utils')
    const Host = { template: '<div />', setup: () => ({ timers: useTimers() }) }
    const wrapper = mount(Host)

    timersOf(wrapper).start('study')
    expect(timersOf(wrapper).state.value.timers.study.running).toBe(true)
    const saved = JSON.parse(window.localStorage.getItem(STORAGE_KEY) ?? '{}')
    expect(saved.timers.study.running).toBe(true)

    timersOf(wrapper).setDurationSeconds('break', 600)
    expect(timersOf(wrapper).state.value.timers.break).toEqual({ total: 600, remaining: 600, running: false })
    expect(timersOf(wrapper).remainingText('break')).toBe('00:10:00')

    timersOf(wrapper).stop('study')
    expect(timersOf(wrapper).state.value.timers.study.running).toBe(false)
    wrapper.unmount()
  })

  it('1 秒ごとに残りが減る', async () => {
    vi.useFakeTimers()
    const { useTimers } = await import('@/features/home/useTimers')
    const { mount } = await import('@vue/test-utils')
    const Host = { template: '<div />', setup: () => ({ timers: useTimers() }) }
    const wrapper = mount(Host)

    timersOf(wrapper).setDurationSeconds('game', 300)
    timersOf(wrapper).start('game')
    vi.advanceTimersByTime(3000)
    await Promise.resolve()

    expect(timersOf(wrapper).state.value.timers.game.remaining).toBe(297)
    expect(timersOf(wrapper).remainingText('game')).toBe('00:04:57')
    wrapper.unmount()
    vi.useRealTimers()
  })

  it('別タブの変更（storage イベント）を取り込む', async () => {
    const { useTimers } = await import('@/features/home/useTimers')
    const { mount } = await import('@vue/test-utils')
    const Host = { template: '<div />', setup: () => ({ timers: useTimers() }) }
    const wrapper = mount(Host)

    const other = setDuration(createTimersState(Date.now()), 'study', 1200)
    window.dispatchEvent(new StorageEvent('storage', {
      key: STORAGE_KEY,
      newValue: serializeTimersState(other)
    }))

    expect(timersOf(wrapper).state.value.timers.study.total).toBe(1200)
    wrapper.unmount()
  })
})

describe('タイマーカード（画面）', () => {
  beforeEach(() => {
    window.localStorage.clear()
    vi.useRealTimers()
  })

  it('3 つのカードを出し、開始・停止が効く（学習は時間設定つき）', async () => {
    const { mount } = await import('@vue/test-utils')
    const StudyTimers = (await import('@/components/home/StudyTimers.vue')).default
    const wrapper = mount(StudyTimers)

    expect(wrapper.findAll('[data-timer]').map((card) => card.attributes('data-timer')))
      .toEqual(['study', 'break', 'game'])
    expect(wrapper.get('[data-timer-clock="study"]').text()).toBe('00:30:00')
    expect(wrapper.get('[data-timer-clock="break"]').text()).toBe('00:15:00')
    expect(wrapper.get('[data-timer-clock="game"]').text()).toBe('00:30:00')

    // 学習タイマーだけ時間設定がある
    expect(wrapper.findAll('[data-study-duration]').length).toBe(1)

    // 開始 → 実行中バッジ、停止ボタンが有効
    await wrapper.get('[data-timer-start="break"]').trigger('click')
    expect(wrapper.get('[data-timer="break"]').text()).toContain('実行中')
    expect((wrapper.get('[data-timer-stop="break"]').element as HTMLButtonElement).disabled).toBe(false)
    expect((wrapper.get('[data-timer-start="break"]').element as HTMLButtonElement).disabled).toBe(true)

    // 停止 → 実行中が消える
    await wrapper.get('[data-timer-stop="break"]').trigger('click')
    expect(wrapper.get('[data-timer="break"]').text()).not.toContain('実行中')
  })

  it('学習タイマーの時間を 15 分に変えると 00:15:00 になる', async () => {
    const { mount } = await import('@vue/test-utils')
    const StudyTimers = (await import('@/components/home/StudyTimers.vue')).default
    const wrapper = mount(StudyTimers)

    await wrapper.get('[data-study-duration]').setValue('900')

    expect(wrapper.get('[data-timer-clock="study"]').text()).toBe('00:15:00')
    // 一覧に無い時間（カスタム）のときだけ分数入力が出る
    expect(wrapper.find('[data-study-duration-custom]').exists()).toBe(false)
  })
})

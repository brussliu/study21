import { beforeEach, describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import GameHelpCard from '@/views/game/GameHelpCard.vue'
import GomokuView from '@/views/game/GomokuView.vue'
import { GAME_HELP } from '@/features/game/gameHelp'
import { GAMES } from '@/features/game/games'
import { useHelpLanguage } from '@/features/game/useHelpLanguage'

beforeEach(() => {
  // 言語はアプリ内で共有されるため、テストごとに日本語へ戻す
  useHelpLanguage().setLanguage('ja')
})

describe('遊び方カードの言語切り替え', () => {
  it('既定は日本語で、右上に 2 つの切り替えボタンがある', async () => {
    const wrapper = mount(GameHelpCard, { props: { slug: 'minesweeper' } })

    expect(wrapper.find('.card__title').text()).toBe('遊び方')
    expect(wrapper.find('.gm-help li').text()).toBe('左クリック：マスを開く（初手は地雷を踏みません）。')
    expect(wrapper.find('.gm-note').text()).toBe('記録は保存されません（画面を閉じると消えます）。')

    const buttons = wrapper.findAll('.gm-lang__btn')
    expect(buttons.map((button) => button.text())).toEqual(['日本語', '中文'])
    expect(buttons[0].attributes('aria-pressed')).toBe('true')
    expect(buttons[1].attributes('aria-pressed')).toBe('false')
    expect(buttons[0].classes()).toContain('is-active')
  })

  it('「中文」を押すと中国語に切り替わり、もう一度押すと日本語に戻る', async () => {
    const wrapper = mount(GameHelpCard, { props: { slug: 'minesweeper' } })
    const buttons = wrapper.findAll('.gm-lang__btn')

    await buttons[1].trigger('click')
    expect(wrapper.find('.card__title').text()).toBe('玩法')
    expect(wrapper.find('.gm-help li').text()).toBe('左键点击：翻开格子（第一步不会踩到地雷）。')
    expect(wrapper.find('.gm-note').text()).toBe('不会保存记录（关闭页面即消失）。')
    expect(wrapper.findAll('.gm-lang__btn')[1].classes()).toContain('is-active')

    await wrapper.findAll('.gm-lang__btn')[0].trigger('click')
    expect(wrapper.find('.card__title').text()).toBe('遊び方')
    expect(wrapper.find('.gm-note').text()).toBe('記録は保存されません（画面を閉じると消えます）。')
  })

  it('選んだ言語は他のゲームのカードにも引き継がれる', async () => {
    const first = mount(GameHelpCard, { props: { slug: 'sudoku' } })
    await first.findAll('.gm-lang__btn')[1].trigger('click')
    expect(first.find('.card__title').text()).toBe('玩法')

    const second = mount(GameHelpCard, { props: { slug: 'reversi' } })
    expect(second.find('.card__title').text()).toBe('玩法')
    expect(second.find('.gm-help li').text()).toContain('点击')
    first.unmount()
    second.unmount()
  })

  it('五目並べの凡例も同じ言語に追従する', async () => {
    const router = createRouter({
      history: createMemoryHistory(),
      routes: [
        { path: '/:area/game', component: defineComponent({ render: () => h('div') }) },
        { path: '/:area/game/:slug', component: GomokuView }
      ]
    })
    await router.push('/student/game/gomoku')
    await router.isReady()
    const wrapper = mount(GomokuView, { global: { plugins: [router] } })

    const legend = () => wrapper.findAll('.gm-legend__item').map((item) => item.text())
    expect(legend()).toEqual(['黒（先手・あなた）', '白（後手）'])

    await wrapper.find('.gm-lang__btn:last-child').trigger('click')
    expect(legend()).toEqual(['黑（先手・你）', '白（后手）'])
    wrapper.unmount()
  })
})

describe('遊び方の文言データ', () => {
  it('6 種類すべてに日本語と中国語がある', () => {
    expect(Object.keys(GAME_HELP).sort()).toEqual(GAMES.map((game) => game.slug).sort())

    for (const game of GAMES) {
      const help = GAME_HELP[game.slug]
      expect(help).toBeDefined()
      for (const language of ['ja', 'zh'] as const) {
        const text = help[language]
        expect(text.title.trim()).not.toBe('')
        expect(text.items.length).toBeGreaterThan(0)
        for (const item of text.items) expect(item.trim()).not.toBe('')
        expect(text.note.trim()).not.toBe('')
      }
      // 日本語と中国語で箇条書きの数が揃っている（訳抜けの検出）
      expect(help.zh.items).toHaveLength(help.ja.items.length)
      // 凡例などの追加文言も両言語に揃っている
      expect(Object.keys(help.zh.extras ?? {}).sort()).toEqual(Object.keys(help.ja.extras ?? {}).sort())
    }
  })

  it('中国語側は日本語のままコピーされていない（訳漏れの検出）', () => {
    for (const game of GAMES) {
      const help = GAME_HELP[game.slug]
      expect(help.zh.note).not.toBe(help.ja.note)
      help.zh.items.forEach((item, index) => {
        // 画面上のボタン名（メモ・ヒント・元に戻す等）は日本語のまま引用しているため、
        // 「行全体が同じ」でないことを確認する。
        expect(item).not.toBe(help.ja.items[index])
      })
    }
  })
})

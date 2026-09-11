import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { defineComponent, h, type Component } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'

const Dummy = defineComponent({ name: 'Dummy', render: () => h('div') })

interface GameCase {
  label: string
  slug: string
  /** Vue の動的 import は { default: コンポーネント } を返す。 */
  load: () => Promise<{ default: Component }>
  /** 盤面のルート要素（game.css のクラス）。 */
  board: string
}

/**
 * 6 ゲームが同じ画面構造（.gm-page / .gm-head / カード / サイドパネル / 遊び方）で
 * 作られていることをまとめて確認する。
 */
const GAMES: GameCase[] = [
  { label: 'マインスイーパー', slug: 'minesweeper', load: () => import('@/views/game/MinesweeperView.vue'), board: '.gm-mine-board' },
  { label: '数独', slug: 'sudoku', load: () => import('@/views/game/SudokuView.vue'), board: '.gm-sudoku' },
  { label: '2048', slug: '2048', load: () => import('@/views/game/Game2048View.vue'), board: '.gm-g2048' },
  { label: '五目並べ', slug: 'gomoku', load: () => import('@/views/game/GomokuView.vue'), board: '.gm-gomoku' },
  { label: 'リバーシ', slug: 'reversi', load: () => import('@/views/game/ReversiView.vue'), board: '.gm-rev' },
  { label: 'ノノグラム', slug: 'nonogram', load: () => import('@/views/game/NonogramView.vue'), board: '.gm-nono' }
]

async function mountGame(component: Component, slug: string) {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/game', component: Dummy },
      { path: '/:area/game/:slug', component: Dummy }
    ]
  })
  await router.push(`/student/game/${slug}`)
  await router.isReady()
  return mount(component, { global: { plugins: [router] } })
}

describe('ゲーム画面の共通レイアウト', () => {
  for (const game of GAMES) {
    it(`${game.label}: ページ枠・盤面・ステータス・遊び方を備える`, async () => {
      const wrapper = await mountGame((await game.load()).default, game.slug)

      expect(wrapper.find('.gm-page').exists()).toBe(true)
      expect(wrapper.find('.gm-head__title').text()).toContain(game.label)
      expect(wrapper.find(game.board).exists()).toBe(true)

      // 盤面カード
      expect(wrapper.find('.card__title').text()).toBe('盤面')

      // ステータス（aria-live で読み上げ対象）
      const status = wrapper.find('.gm-status')
      expect(status.exists()).toBe(true)
      expect(status.attributes('role')).toBe('status')
      expect(status.attributes('aria-live')).toBe('polite')
      expect(wrapper.findAll('.gm-metric').length).toBeGreaterThanOrEqual(2)

      // 遊び方と「記録は保存されない」注記（既定は日本語）
      expect(wrapper.text()).toContain('遊び方')
      expect(wrapper.text()).toContain('記録は保存されません')

      // 遊び方カードの右上に日本語／中国語の切り替えがある
      const langButtons = wrapper.findAll('.gm-lang__btn')
      expect(langButtons.map((button) => button.text())).toEqual(['日本語', '中文'])

      // 一覧へ戻るリンク（エリアを保つ）
      const back = wrapper.findAll('a').find((link) => link.text().includes('ゲーム一覧'))
      expect(back?.attributes('href')).toBe('/student/game')
    })
  }
})

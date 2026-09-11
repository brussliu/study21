import { describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import GameView from '@/views/game/GameView.vue'
import { GAMES } from '@/features/game/games'
import { prototypeMenu } from '@/config/menuRegistry'
import { routes } from '@/router'

const Dummy = defineComponent({ name: 'Dummy', render: () => h('div') })

async function mountGames(path = '/student/game') {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/game', component: GameView },
      { path: '/:area/:rest(.*)', component: Dummy }
    ]
  })
  await router.push(path)
  await router.isReady()
  const wrapper = mount(GameView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

/**
 * タブの中身は defineAsyncComponent（動的 import）なので、
 * SFC の変換が終わって描画されるまで待つ。
 */
async function expectBoard(wrapper: VueWrapper, selector: string): Promise<void> {
  await vi.waitFor(() => {
    expect(wrapper.find(selector).exists()).toBe(true)
  }, { timeout: 10000, interval: 20 })
}

/** タブのラベル一覧。 */
function tabLabels(wrapper: VueWrapper): string[] {
  return wrapper.findAll('.tabs__tab').map((tab) => tab.text())
}

describe('ゲーム（タブ表示）', () => {
  it('6 種類のゲームをタブで表示する', async () => {
    const { wrapper } = await mountGames()
    expect(wrapper.find('.tabs[role="tablist"]').exists()).toBe(true)
    expect(tabLabels(wrapper)).toEqual(GAMES.map((game) => game.label))
  })

  it('既定はマインスイーパーで、その盤面を表示する', async () => {
    const { wrapper } = await mountGames()
    expect(wrapper.find('.tabs__tab.is-active').text()).toBe('マインスイーパー')
    await expectBoard(wrapper, '.gm-mine-board')
    // 埋め込み表示なので、ゲーム側のページ見出し（ゲーム一覧リンク）は出さない
    expect(wrapper.find('.gm-head').exists()).toBe(false)
    expect(wrapper.find('.gm-tabs__desc').text()).toContain('地雷')
  })

  it('?game=sudoku の直リンクでそのタブが開く', async () => {
    const { wrapper } = await mountGames('/student/game?game=sudoku')
    expect(wrapper.find('.tabs__tab.is-active').text()).toBe('数独')
    await expectBoard(wrapper, '.gm-sudoku')
    expect(wrapper.find('.gm-mine-board').exists()).toBe(false)
  })

  it('不明な ?game= はマインスイーパーに戻す', async () => {
    const { wrapper } = await mountGames('/student/game?game=unknown')
    expect(wrapper.find('.tabs__tab.is-active').text()).toBe('マインスイーパー')
    await expectBoard(wrapper, '.gm-mine-board')
  })

  it('タブをクリックすると URL クエリが変わり、盤面が切り替わる', async () => {
    const { wrapper, router } = await mountGames()
    await expectBoard(wrapper, '.gm-mine-board')

    const sudokuIndex = GAMES.findIndex((game) => game.slug === 'sudoku')
    await wrapper.findAll('.tabs__tab')[sudokuIndex].trigger('click')
    await expectBoard(wrapper, '.gm-sudoku')

    expect(router.currentRoute.value.query.game).toBe('sudoku')
    expect(wrapper.find('.tabs__tab.is-active').text()).toBe('数独')
    expect(wrapper.find('.gm-mine-board').exists()).toBe(false)

    const reversiIndex = GAMES.findIndex((game) => game.slug === 'reversi')
    await wrapper.findAll('.tabs__tab')[reversiIndex].trigger('click')
    await expectBoard(wrapper, '.gm-rev')
    expect(router.currentRoute.value.query.game).toBe('reversi')
  })

  it('同じタブをもう一度押しても URL は変わらない', async () => {
    const { wrapper, router } = await mountGames('/student/game?game=gomoku')
    await expectBoard(wrapper, '.gm-gomoku')
    await wrapper.find('.tabs__tab.is-active').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.query.game).toBe('gomoku')
  })
})

describe('ゲームのルートとメニュー', () => {
  it('3 エリア分のゲームルート（1 ページ）を定義している', () => {
    const names = routes
      .flatMap((r) => [r.name, ...(r.children ?? []).map((c) => c.name)])
      .filter((n): n is string => typeof n === 'string')

    for (const area of ['admin', 'student', 'parent']) {
      expect(names).toContain(`${area}-game`)
    }
    // ゲームごとの個別ルートは持たない（タブで切り替える）
    expect(names.some((name) => name.startsWith('student-game-'))).toBe(false)
    expect(names.some((name) => name.startsWith('parent-game-'))).toBe(false)
    expect(names.some((name) => name.startsWith('admin-game-'))).toBe(false)
  })

  it('サイドメニューの「ゲーム」は 1 項目（子メニューなし）', () => {
    const game = prototypeMenu('student').find((item) => item.id === 'game')
    expect(game?.path).toBe('/student/game')
    expect(game?.children ?? []).toHaveLength(0)
  })

  it('タブの定義は 6 種類で、slug が重複しない', () => {
    expect(GAMES).toHaveLength(6)
    expect(new Set(GAMES.map((game) => game.slug)).size).toBe(6)
    expect(GAMES.map((game) => game.slug)).toEqual([
      'minesweeper', 'sudoku', '2048', 'gomoku', 'reversi', 'nonogram'
    ])
  })
})

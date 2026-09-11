import { describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h } from 'vue'
import { createMemoryHistory, createRouter } from 'vue-router'
import MinesweeperView from '@/views/game/MinesweeperView.vue'
import {
  chordCell, createBoard, minePreset, neighborIndexes, openCell, toggleFlag, type MineBoard
} from '@/features/game/minesweeper'

const Dummy = defineComponent({ name: 'Dummy', render: () => h('div') })

async function mountView(): Promise<VueWrapper> {
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/game', component: Dummy },
      { path: '/:area/game/:slug', component: MinesweeperView }
    ]
  })
  await router.push('/student/game/minesweeper')
  await router.isReady()
  return mount(MinesweeperView, { global: { plugins: [router] } })
}

/** ラベルからメトリクスの値を読む。 */
function metric(wrapper: VueWrapper, label: string): string {
  const item = wrapper.findAll('.gm-metric').find((row) => row.find('.gm-metric__label').text() === label)
  return item ? item.find('.gm-metric__value').text() : ''
}

/** 盤面が指定しているマスの大きさ（描画された CSS 変数）。 */
function boardCellSize(wrapper: VueWrapper): string {
  const board = wrapper.find('.gm-mine-board').element as HTMLElement
  return board.style.getPropertyValue('--gm-cell').trim()
}

/** 決まった順番で数を返す擬似乱数（ビューと盤面の再現で同じ配置にする）。 */
function seededRng(seed: number): () => number {
  let value = seed
  return () => {
    value = (value * 1664525 + 1013904223) % 4294967296
    return value / 4294967296
  }
}

const FIRST_CLICK = 40

describe('マインスイーパー画面', () => {
  it('初級の盤面（9×9）とステータスを表示する', async () => {
    const wrapper = await mountView()
    expect(wrapper.findAll('.gm-mine-cell')).toHaveLength(81)
    expect(wrapper.find('.gm-status__title').text()).toBe('開始前')
    expect(metric(wrapper, '残りの地雷')).toBe('10')
    expect(metric(wrapper, '開いたマス')).toBe('0 / 71')
  })

  it('マスをクリックすると開いて状態が変わる', async () => {
    const wrapper = await mountView()
    await wrapper.findAll('.gm-mine-cell')[40].trigger('click')

    expect(wrapper.findAll('.gm-mine-cell.is-open').length).toBeGreaterThan(0)
    expect(wrapper.find('.gm-status__title').text()).not.toBe('開始前')
    expect(metric(wrapper, '開いたマス')).not.toBe('0 / 71')
  })

  it('右クリックで旗を立てられ、残りの地雷が減る', async () => {
    const wrapper = await mountView()
    const cells = wrapper.findAll('.gm-mine-cell')
    await cells[0].trigger('contextmenu')

    expect(cells[0].classes()).toContain('is-flag')
    expect(metric(wrapper, '残りの地雷')).toBe('9')
    expect(metric(wrapper, '立てた旗')).toBe('1')

    await cells[0].trigger('contextmenu')
    expect(wrapper.findAll('.gm-mine-cell.is-flag')).toHaveLength(0)
    expect(metric(wrapper, '残りの地雷')).toBe('10')
  })

  it('「新しいゲーム」で盤面がリセットされる', async () => {
    const wrapper = await mountView()
    await wrapper.findAll('.gm-mine-cell')[40].trigger('click')
    await wrapper.findAll('.gm-mine-cell')[0].trigger('contextmenu')

    const newGame = wrapper.findAll('.gm-actions button').find((button) => button.text().includes('新しいゲーム'))
    expect(newGame).toBeTruthy()
    await newGame!.trigger('click')

    expect(wrapper.findAll('.gm-mine-cell.is-open')).toHaveLength(0)
    expect(wrapper.findAll('.gm-mine-cell.is-flag')).toHaveLength(0)
    expect(wrapper.find('.gm-status__title').text()).toBe('開始前')
    expect(metric(wrapper, '開いたマス')).toBe('0 / 71')
  })

  it('難易度を変えると盤面の大きさが変わる', async () => {
    const wrapper = await mountView()
    await wrapper.find('select').setValue('intermediate')
    expect(wrapper.findAll('.gm-mine-cell')).toHaveLength(256)
    expect(metric(wrapper, '残りの地雷')).toBe('40')
    expect(wrapper.find('.card__sub').text()).toContain('16 × 16')
  })

  it('マスの大きさは難易度にかかわらず同じ（40px）', async () => {
    const wrapper = await mountView()
    expect(boardCellSize(wrapper)).toBe('40px')

    await wrapper.find('select').setValue('intermediate')
    expect(boardCellSize(wrapper)).toBe('40px')

    await wrapper.find('select').setValue('expert')
    expect(boardCellSize(wrapper)).toBe('40px')
    expect(wrapper.findAll('.gm-mine-cell')).toHaveLength(480)
  })

  it('左右のボタンを同時に押すと、旗の数が合っている周囲をまとめて開く', async () => {
    // 乱数を固定して、ビューと論理モジュールで同じ盤面になるようにする
    const viewRng = seededRng(7)
    const random = vi.spyOn(Math, 'random').mockImplementation(() => viewRng())
    try {
      const wrapper = await mountView()
      const cells = wrapper.findAll('.gm-mine-cell')
      await cells[FIRST_CLICK].trigger('click') // 初手（ここで地雷が配置される）
      expect(wrapper.find('.gm-status__title').text()).toBe('プレイ中')

      // ビューと同じ手順で盤面を再現し、チョードの対象と旗の位置を決める
      const board: MineBoard = createBoard(minePreset('beginner'))
      openCell(board, FIRST_CLICK, seededRng(7))
      const center = board.cells.findIndex((cell, index) =>
        cell.open && !cell.mine && cell.adjacent > 0 &&
        neighborIndexes(board, index).some((neighbor) => !board.cells[neighbor].open)
      )
      expect(center).toBeGreaterThanOrEqual(0)

      const around = neighborIndexes(board, center)
      const mines = around.filter((index) => board.cells[index].mine)
      expect(mines.length).toBe(board.cells[center].adjacent)
      for (const index of mines) {
        await cells[index].trigger('contextmenu')
        toggleFlag(board, index) // 再現した盤面にも同じ旗を立てる
      }
      expect(wrapper.findAll('.gm-mine-cell.is-flag')).toHaveLength(mines.length)

      const openedBefore = wrapper.findAll('.gm-mine-cell.is-open').length
      await cells[center].trigger('mousedown', { buttons: 3 })

      // 論理モジュールと同じ結果になる（旗のない周囲のマスが開く）
      const expected = chordCell(board, center)
      expect(expected.triggered).toBe(true)
      expect(expected.exploded).toBe(false)
      const openedAfter = wrapper.findAll('.gm-mine-cell.is-open').length
      expect(openedAfter).toBe(board.cells.filter((cell) => cell.open).length)
      expect(openedAfter).toBeGreaterThan(openedBefore)
      expect(wrapper.find('.gm-status__title').text()).not.toBe('失敗…')

      // チョード直後の右クリックは無視される（旗が増えない）
      const closed = board.cells.findIndex((cell, index) => !cell.open && !cell.flag && index !== center)
      await cells[closed].trigger('contextmenu')
      expect(wrapper.findAll('.gm-mine-cell.is-flag')).toHaveLength(mines.length)
      expect(wrapper.findAll('.gm-mine-cell.is-open').length).toBe(openedAfter)
      wrapper.unmount()
    } finally {
      random.mockRestore()
    }
  })

  it('チョードは旗の数が数字と合っていないときは何もしない', async () => {
    const viewRng = seededRng(7)
    const random = vi.spyOn(Math, 'random').mockImplementation(() => viewRng())
    try {
      const wrapper = await mountView()
      const cells = wrapper.findAll('.gm-mine-cell')
      await cells[FIRST_CLICK].trigger('click')

      const board: MineBoard = createBoard(minePreset('beginner'))
      openCell(board, FIRST_CLICK, seededRng(7))
      const center = board.cells.findIndex((cell, index) =>
        cell.open && !cell.mine && cell.adjacent > 0 &&
        neighborIndexes(board, index).some((neighbor) => !board.cells[neighbor].open)
      )
      const openedBefore = wrapper.findAll('.gm-mine-cell.is-open').length

      // 旗を立てずにチョードしても開かない
      await cells[center].trigger('mousedown', { buttons: 3 })
      expect(wrapper.findAll('.gm-mine-cell.is-open').length).toBe(openedBefore)
      expect(wrapper.find('.gm-status__title').text()).toBe('プレイ中')
      wrapper.unmount()
    } finally {
      random.mockRestore()
    }
  })
})

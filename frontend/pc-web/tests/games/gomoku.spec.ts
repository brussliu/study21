import { afterEach, describe, expect, it, vi } from 'vitest'
import { mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { useToast } from '@study21/web-shared'
import {
  GOMOKU_SIZE, WIN_LENGTH, chooseCpuMove, createGomoku, findWinningLine, isBoardFull, isInside,
  lineScore, otherStone, placeStone, type Stone
} from '@/features/game/gomoku'
import GomokuView from '@/views/game/GomokuView.vue'

const SIZE = 15

/** 行・列から盤面の index を作る。 */
function at(row: number, col: number, size: number = SIZE): number {
  return row * size + col
}

/** 石を直接置いた盤面を作る（配置ロジックを通さないテスト用）。 */
function boardWith(placements: Array<[number, number, Stone]>, size: number = SIZE): (Stone | null)[] {
  const cells = createGomoku(size)
  for (const [row, col, stone] of placements) cells[at(row, col, size)] = stone
  return cells
}

/** 決まった順番で数を返す擬似乱数（テストを再現可能にする）。 */
function seededRng(seed: number): () => number {
  let value = seed
  return () => {
    value = (value * 1664525 + 1013904223) % 4294967296
    return value / 4294967296
  }
}

const Dummy = defineComponent({ name: 'Dummy', render: () => null })

/** 画面をマウントするための最小限のルーター。 */
function createTestRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/game/gomoku', component: Dummy },
      { path: '/:area/game', component: Dummy },
      { path: '/:pathMatch(.*)*', component: Dummy }
    ]
  })
}

let wrapper: VueWrapper | null = null

/** GomokuView をマウントする（route.path で area が決まる）。 */
async function mountView(path = '/student/game/gomoku', attach = false): Promise<VueWrapper> {
  const router = createTestRouter()
  await router.push(path)
  await router.isReady()
  wrapper = mount(GomokuView, {
    global: { plugins: [router] },
    ...(attach ? { attachTo: document.body } : {})
  })
  await wrapper.vm.$nextTick()
  return wrapper
}

/** 盤面のマスをクリックする。 */
async function clickCell(target: VueWrapper, index: number): Promise<void> {
  await target.findAll('.gm-gomoku-cell')[index].trigger('click')
}

/** 盤面のマスにフォーカスを当てる（矢印キーのテスト用）。 */
function focusCell(target: VueWrapper, index: number): void {
  const element = target.findAll('.gm-gomoku-cell')[index].element
  if (element instanceof HTMLElement) element.focus()
}

/** 積み上がったトーストを取り出す。 */
function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

afterEach(() => {
  const current = useToast().items
  current.splice(0, current.length)
  vi.useRealTimers()
  wrapper?.unmount()
  wrapper = null
  document.body.innerHTML = ''
})

describe('五目並べ：盤面の生成', () => {
  it('既定は 15×15 の空の盤面', () => {
    const cells = createGomoku()
    expect(GOMOKU_SIZE).toBe(15)
    expect(WIN_LENGTH).toBe(5)
    expect(cells).toHaveLength(225)
    expect(cells.every((cell) => cell === null)).toBe(true)
  })

  it('サイズを指定でき、毎回新しい配列を返す', () => {
    const small = createGomoku(9)
    expect(small).toHaveLength(81)
    const other = createGomoku(9)
    expect(other).not.toBe(small)
    other[0] = 'black'
    expect(small[0]).toBeNull()
  })

  it('相手の石の色を返す', () => {
    expect(otherStone('black')).toBe('white')
    expect(otherStone('white')).toBe('black')
  })

  it('盤内・盤外を判定する', () => {
    expect(isInside(15, 0, 0)).toBe(true)
    expect(isInside(15, 14, 14)).toBe(true)
    expect(isInside(15, -1, 0)).toBe(false)
    expect(isInside(15, 15, 0)).toBe(false)
    expect(isInside(15, 0, 15)).toBe(false)
  })
})

describe('五目並べ：石を置く', () => {
  it('空きマスに置ける（破壊的更新）', () => {
    const cells = createGomoku()
    expect(placeStone(cells, SIZE, at(7, 7), 'black')).toBe(true)
    expect(cells[at(7, 7)]).toBe('black')
  })

  it('既に石があるマスには置けない', () => {
    const cells = createGomoku()
    placeStone(cells, SIZE, at(0, 0), 'white')
    expect(placeStone(cells, SIZE, at(0, 0), 'black')).toBe(false)
    expect(cells[at(0, 0)]).toBe('white')
  })

  it('盤外の index には置けない', () => {
    const cells = createGomoku()
    expect(placeStone(cells, SIZE, -1, 'black')).toBe(false)
    expect(placeStone(cells, SIZE, 225, 'black')).toBe(false)
    expect(placeStone(cells, SIZE, 1.5, 'black')).toBe(false)
    expect(cells.every((cell) => cell === null)).toBe(true)
  })

  it('すべて埋まったかどうかを判定する', () => {
    const cells = createGomoku()
    expect(isBoardFull(cells)).toBe(false)
    cells.fill('black')
    expect(isBoardFull(cells)).toBe(true)
    cells[10] = null
    expect(isBoardFull(cells)).toBe(false)
  })
})

describe('五目並べ：勝利判定', () => {
  it('横 5 連を検出する', () => {
    const cells = boardWith([[7, 3, 'black'], [7, 4, 'black'], [7, 5, 'black'], [7, 6, 'black'], [7, 7, 'black']])
    expect(findWinningLine(cells, SIZE, at(7, 7), 'black')).toEqual([
      at(7, 3), at(7, 4), at(7, 5), at(7, 6), at(7, 7)
    ])
  })

  it('縦 5 連を検出する', () => {
    const cells = boardWith([[3, 2, 'white'], [4, 2, 'white'], [5, 2, 'white'], [6, 2, 'white'], [7, 2, 'white']])
    expect(findWinningLine(cells, SIZE, at(5, 2), 'white')).toEqual([
      at(3, 2), at(4, 2), at(5, 2), at(6, 2), at(7, 2)
    ])
  })

  it('斜め（↘）5 連を検出する', () => {
    const cells = boardWith([[2, 2, 'black'], [3, 3, 'black'], [4, 4, 'black'], [5, 5, 'black'], [6, 6, 'black']])
    expect(findWinningLine(cells, SIZE, at(4, 4), 'black')).toEqual([
      at(2, 2), at(3, 3), at(4, 4), at(5, 5), at(6, 6)
    ])
  })

  it('斜め（↗）5 連を検出する', () => {
    const cells = boardWith([[4, 8, 'white'], [3, 7, 'white'], [2, 6, 'white'], [1, 5, 'white'], [0, 4, 'white']])
    expect(findWinningLine(cells, SIZE, at(0, 4), 'white')).toEqual([
      at(0, 4), at(1, 5), at(2, 6), at(3, 7), at(4, 8)
    ])
  })

  it('6 連以上はその一線すべてを返す', () => {
    const cells = boardWith([
      [0, 0, 'black'], [0, 1, 'black'], [0, 2, 'black'], [0, 3, 'black'], [0, 4, 'black'], [0, 5, 'black']
    ])
    expect(findWinningLine(cells, SIZE, at(0, 5), 'black')).toEqual([
      at(0, 0), at(0, 1), at(0, 2), at(0, 3), at(0, 4), at(0, 5)
    ])
  })

  it('4 連では勝ちにならない', () => {
    const cells = boardWith([[7, 3, 'black'], [7, 4, 'black'], [7, 5, 'black'], [7, 6, 'black']])
    expect(findWinningLine(cells, SIZE, at(7, 6), 'black')).toEqual([])
  })

  it('相手の石で分断された連続は勝ちにならない', () => {
    const cells = boardWith([
      [7, 3, 'black'], [7, 4, 'black'], [7, 5, 'white'], [7, 6, 'black'], [7, 7, 'black']
    ])
    expect(findWinningLine(cells, SIZE, at(7, 7), 'black')).toEqual([])
  })

  it('そのマスに石が無い場合は空配列を返す', () => {
    const cells = boardWith([[7, 3, 'black'], [7, 4, 'black'], [7, 5, 'black'], [7, 6, 'black'], [7, 7, 'black']])
    expect(findWinningLine(cells, SIZE, at(0, 0), 'black')).toEqual([])
  })
})

describe('五目並べ：CPU の評価', () => {
  it('空きマスは加点、石があるマスは 0', () => {
    const cells = boardWith([[7, 7, 'black']])
    expect(lineScore(cells, SIZE, at(0, 0), 'black')).toBeGreaterThan(0)
    expect(lineScore(cells, SIZE, at(7, 7), 'black')).toBe(0)
    expect(lineScore(cells, SIZE, -1, 'black')).toBe(0)
  })

  it('5 連完成 > 4 連 > 3 連 の順に高く評価する', () => {
    const win = boardWith([[7, 0, 'black'], [7, 1, 'black'], [7, 2, 'black'], [7, 3, 'black']])
    const four = boardWith([[7, 1, 'black'], [7, 2, 'black'], [7, 3, 'black']])
    const three = boardWith([[7, 2, 'black'], [7, 3, 'black']])
    const scoreWin = lineScore(win, SIZE, at(7, 4), 'black')
    const scoreFour = lineScore(four, SIZE, at(7, 4), 'black')
    const scoreThree = lineScore(three, SIZE, at(7, 4), 'black')
    expect(scoreWin).toBeGreaterThan(scoreFour)
    expect(scoreFour).toBeGreaterThan(scoreThree)
    expect(scoreThree).toBeGreaterThan(lineScore(createGomoku(), SIZE, at(7, 4), 'black'))
  })

  it('空の盤面では中央に打つ', () => {
    const cells = createGomoku()
    expect(chooseCpuMove(cells, SIZE, 'black', () => 0)).toBe(at(7, 7))
  })

  it('自分が 5 連になる手を選ぶ（勝ちを逃さない）', () => {
    // 黒 (7,3)〜(7,6) の 4 連。左端 (7,2) は白が塞いでいるので (7,7) だけが勝ち。
    const cells = boardWith([
      [7, 3, 'black'], [7, 4, 'black'], [7, 5, 'black'], [7, 6, 'black'], [7, 2, 'white']
    ])
    expect(chooseCpuMove(cells, SIZE, 'black', seededRng(1))).toBe(at(7, 7))
  })

  it('相手のリーチ（4 連）を止める手を選ぶ', () => {
    // 黒 (7,0)〜(7,3) は左端が盤外なので、伸ばせるのは (7,4) だけ。CPU は白。
    const cells = boardWith([
      [7, 0, 'black'], [7, 1, 'black'], [7, 2, 'black'], [7, 3, 'black'], [0, 0, 'white'], [0, 1, 'white']
    ])
    expect(chooseCpuMove(cells, SIZE, 'white', seededRng(3))).toBe(at(7, 4))
  })

  it('必ず空いているマスを返す（同点は乱数でばらける）', () => {
    const cells = boardWith([[7, 7, 'black'], [7, 8, 'white'], [6, 6, 'black']])
    for (let seed = 1; seed <= 20; seed += 1) {
      const index = chooseCpuMove(cells, SIZE, 'white', seededRng(seed))
      expect(index).toBeGreaterThanOrEqual(0)
      expect(cells[index]).toBeNull()
    }
  })

  it('同じ乱数なら同じ手を返す（再現可能）', () => {
    const cells = boardWith([[7, 7, 'black'], [8, 8, 'white']])
    const first = chooseCpuMove(cells, SIZE, 'black', seededRng(42))
    const second = chooseCpuMove(cells, SIZE, 'black', seededRng(42))
    expect(first).toBe(second)
  })

  it('盤面が埋まっていたら -1 を返す', () => {
    const cells = createGomoku()
    cells.fill('black')
    expect(chooseCpuMove(cells, SIZE, 'white', () => 0)).toBe(-1)
  })
})

describe('五目並べ：画面', () => {
  it('15×15 のマスと aria-label を描画する', async () => {
    const view = await mountView()
    expect(view.find('.gm-gomoku').attributes('aria-label')).toBe('五目並べの盤面 15×15')
    const boardCells = view.findAll('.gm-gomoku-cell')
    expect(boardCells).toHaveLength(225)
    expect(boardCells[0].attributes('aria-label')).toBe('1行1列 空き')
    expect(boardCells[224].attributes('aria-label')).toBe('15行15列 空き')
    expect(view.find('.gm-status').classes()).toContain('is-play')
    expect(view.text()).toContain('対局前')
  })

  it('クリックすると石が置かれ、直前の着手に印が付く', async () => {
    const view = await mountView()
    await clickCell(view, at(7, 7))
    const cell = view.findAll('.gm-gomoku-cell')[at(7, 7)]
    expect(cell.classes()).toContain('is-taken')
    expect(cell.classes()).toContain('is-last')
    expect(cell.find('.gm-stone--black').exists()).toBe(true)
    expect(cell.attributes('aria-label')).toBe('8行8列 黒')
    // 手数が進み、手番が白になる
    expect(view.findAll('.gm-metric__value')[0].text()).toBe('1')
    expect(view.text()).toContain('白の手番')
  })

  it('石があるマスには重ねて置けない', async () => {
    const view = await mountView()
    await clickCell(view, at(7, 7))
    await clickCell(view, at(7, 7))
    expect(view.findAll('.gm-stone')).toHaveLength(1)
    expect(view.findAll('.gm-metric__value')[0].text()).toBe('1')
  })

  it('5 連を並べると勝利表示とトーストになる', async () => {
    const view = await mountView()
    // 黒（先手）が 1 行目に 5 連、白は 11 行目に 4 つまで
    for (const index of [at(0, 0), at(10, 0), at(0, 1), at(10, 1), at(0, 2), at(10, 2), at(0, 3), at(10, 3), at(0, 4)]) {
      await clickCell(view, index)
    }
    const status = view.find('.gm-status')
    expect(status.classes()).toContain('is-win')
    expect(status.text()).toContain('黒の勝ち')
    expect(view.findAll('.gm-gomoku-cell.is-win')).toHaveLength(5)
    expect(toastMessages()).toContain('黒の勝ちです！')
    // 決着後はクリックしても石は増えない
    await clickCell(view, at(5, 5))
    expect(view.findAll('.gm-stone')).toHaveLength(9)
  })

  it('「待った」で直前の手を戻す（2 人対戦は 1 手）', async () => {
    const view = await mountView()
    await clickCell(view, at(0, 0))
    await clickCell(view, at(10, 0))
    const undoButton = view.findAll('.gm-actions button')[1]
    await undoButton.trigger('click')
    expect(view.findAll('.gm-stone')).toHaveLength(1)
    expect(view.findAll('.gm-gomoku-cell')[at(10, 0)].classes()).not.toContain('is-taken')
    await undoButton.trigger('click')
    expect(view.findAll('.gm-stone')).toHaveLength(0)
    expect(undoButton.attributes('disabled')).toBeDefined()
  })

  it('「新しい対局」で盤面と手数がリセットされる', async () => {
    const view = await mountView()
    await clickCell(view, at(3, 3))
    await view.findAll('.gm-actions button')[0].trigger('click')
    expect(view.findAll('.gm-stone')).toHaveLength(0)
    expect(view.findAll('.gm-metric__value')[0].text()).toBe('0')
    expect(view.text()).toContain('対局前')
  })

  it('矢印キーでマス間を移動できる', async () => {
    const view = await mountView('/student/game/gomoku', true)
    const boardCells = view.findAll('.gm-gomoku-cell')
    focusCell(view, 0)
    await boardCells[0].trigger('keydown', { key: 'ArrowRight' })
    expect(document.activeElement?.getAttribute('aria-label')).toBe('1行2列 空き')
    await boardCells[1].trigger('keydown', { key: 'ArrowDown' })
    expect(document.activeElement?.getAttribute('aria-label')).toBe('2行2列 空き')
    // 盤外へは移動しない
    focusCell(view, 0)
    await boardCells[0].trigger('keydown', { key: 'ArrowUp' })
    expect(document.activeElement?.getAttribute('aria-label')).toBe('1行1列 空き')
  })

  it('CPU モードでは自分の着手後に CPU が白を打つ', async () => {
    vi.useFakeTimers()
    const view = await mountView()
    await view.find('select').setValue('cpu')
    await clickCell(view, at(7, 7))
    expect(view.findAll('.gm-stone')).toHaveLength(1)
    expect(view.text()).toContain('CPU の手番')

    vi.advanceTimersByTime(300)
    await view.vm.$nextTick()

    expect(view.findAll('.gm-stone')).toHaveLength(2)
    expect(view.find('.gm-stone--white').exists()).toBe(true)
    expect(toastMessages().some((message) => message.startsWith('CPU が'))).toBe(true)
    expect(view.text()).toContain('黒の手番')
  })

  it('CPU モードの「待った」は 2 手戻す', async () => {
    vi.useFakeTimers()
    const view = await mountView()
    await view.find('select').setValue('cpu')
    await clickCell(view, at(7, 7))
    vi.advanceTimersByTime(300)
    await view.vm.$nextTick()
    expect(view.findAll('.gm-stone')).toHaveLength(2)

    await view.findAll('.gm-actions button')[1].trigger('click')
    expect(view.findAll('.gm-stone')).toHaveLength(0)
    expect(view.text()).toContain('対局前')
  })
})

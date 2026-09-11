import { describe, expect, it, vi } from 'vitest'
import { mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { useToast } from '@study21/web-shared'
import SudokuView from '@/views/game/SudokuView.vue'
import {
  SUDOKU_CELLS, SUDOKU_PRESETS, SUDOKU_SIZE, boxIndexOf, candidatesFor, colIndexOf, countSolutions,
  findConflicts, generateSudoku, isComplete, isSolved, rowIndexOf, sudokuPreset,
  type SudokuDifficulty
} from '@/features/game/sudoku'

// SudokuView は useRoute しか使わないので、テストでは必要最小限のモックに差し替える
// （テンプレートの RouterLink はマウント時のスタブで置き換える）。
vi.mock('vue-router', () => ({
  useRoute: () => ({ path: '/student/game/sudoku' })
}))

/** 決まった順番で数を返す擬似乱数（テストを再現可能にする）。 */
function seededRng(seed: number): () => number {
  let value = seed
  return () => {
    value = (value * 1664525 + 1013904223) % 4294967296
    return value / 4294967296
  }
}

/** 検算に使う完成盤（よく知られた数独の解答）。 */
const SOLUTION: number[] = [
  5, 3, 4, 6, 7, 8, 9, 1, 2,
  6, 7, 2, 1, 9, 5, 3, 4, 8,
  1, 9, 8, 3, 4, 2, 5, 6, 7,
  8, 5, 9, 7, 6, 1, 4, 2, 3,
  4, 2, 6, 8, 5, 3, 7, 9, 1,
  7, 1, 3, 9, 2, 4, 8, 5, 6,
  9, 6, 1, 5, 3, 7, 2, 8, 4,
  2, 8, 7, 4, 1, 9, 6, 3, 5,
  3, 4, 5, 2, 8, 6, 1, 7, 9
]

/** 初期数字の数（0 以外のマス）を数える。 */
function countGivens(grid: number[]): number {
  return grid.filter((value) => value !== 0).length
}

describe('数独：定数とプリセット', () => {
  it('9×9 のサイズ定数と、3 種類の難易度プリセットを持つ', () => {
    expect(SUDOKU_SIZE).toBe(9)
    expect(SUDOKU_CELLS).toBe(81)
    expect(SUDOKU_PRESETS.map((preset) => preset.key)).toEqual(['easy', 'normal', 'hard'])
    expect(SUDOKU_PRESETS.map((preset) => preset.givens)).toEqual([45, 34, 27])
  })

  it('難易度からプリセットを引ける（不明な難易度は例外）', () => {
    expect(sudokuPreset('normal').givens).toBe(34)
    expect(sudokuPreset('hard').label).toContain('27')
    expect(() => sudokuPreset('insane' as SudokuDifficulty)).toThrow()
  })

  it('行・列・ブロックの番号を求められる', () => {
    expect(rowIndexOf(0)).toBe(0)
    expect(colIndexOf(0)).toBe(0)
    expect(rowIndexOf(80)).toBe(8)
    expect(colIndexOf(80)).toBe(8)
    // ブロック 0 は 0〜2 行 × 0〜2 列（0,1,2,9,10,11,18,19,20）
    expect(boxIndexOf(0)).toBe(0)
    expect(boxIndexOf(9)).toBe(0)
    expect(boxIndexOf(8)).toBe(2)
    expect(boxIndexOf(27)).toBe(3)
    expect(boxIndexOf(40)).toBe(4)
    expect(boxIndexOf(80)).toBe(8)
  })

  it('ブロック番号は 0〜8 で、どのブロックも 9 マスになる', () => {
    const counts = new Array<number>(SUDOKU_SIZE).fill(0)
    for (let index = 0; index < SUDOKU_CELLS; index += 1) {
      const box = boxIndexOf(index)
      expect(box).toBeGreaterThanOrEqual(0)
      expect(box).toBeLessThan(SUDOKU_SIZE)
      counts[box] += 1
    }
    expect(counts).toEqual(new Array<number>(SUDOKU_SIZE).fill(SUDOKU_SIZE))
  })
})

describe('数独：問題の生成', () => {
  it('難易度ごとの初期数字数どおりの問題と、完成した解答を作る', () => {
    const expected: Record<SudokuDifficulty, number> = { easy: 45, normal: 34, hard: 27 }
    for (const key of ['easy', 'normal', 'hard'] as SudokuDifficulty[]) {
      const { puzzle, solution } = generateSudoku(key, seededRng(11))
      expect(puzzle).toHaveLength(SUDOKU_CELLS)
      expect(solution).toHaveLength(SUDOKU_CELLS)
      expect(countGivens(puzzle)).toBe(expected[key])
      // 解答は完成盤
      expect(solution.every((value) => value >= 1 && value <= SUDOKU_SIZE)).toBe(true)
      expect(isComplete(solution)).toBe(true)
    }
  })

  it('問題の数字は解答と一致し、空きマスは 0 になっている', () => {
    const { puzzle, solution } = generateSudoku('normal', seededRng(5))
    puzzle.forEach((value, index) => {
      if (value === 0) return
      expect(value).toBe(solution[index])
    })
    expect(puzzle.some((value) => value === 0)).toBe(true)
  })

  it('問題の解は一意（countSolutions が 1）', () => {
    for (const key of ['easy', 'normal', 'hard'] as SudokuDifficulty[]) {
      const { puzzle } = generateSudoku(key, seededRng(23))
      expect(countSolutions(puzzle, 2)).toBe(1)
    }
  })

  it('同じ乱数なら同じ問題を作り、違う乱数なら別の問題を作る', () => {
    const first = generateSudoku('easy', seededRng(99))
    const again = generateSudoku('easy', seededRng(99))
    const other = generateSudoku('easy', seededRng(100))
    expect(first.puzzle).toEqual(again.puzzle)
    expect(first.solution).toEqual(again.solution)
    expect(other.solution).not.toEqual(first.solution)
  })

  it('問題を解答どおりに埋めると完成判定になる（画面のクリア条件と同じ）', () => {
    const { puzzle, solution } = generateSudoku('hard', seededRng(7))
    const grid = [...puzzle]
    expect(isComplete(grid)).toBe(false)
    for (let index = 0; index < SUDOKU_CELLS; index += 1) {
      if (grid[index] === 0) grid[index] = solution[index]
    }
    expect(isComplete(grid)).toBe(true)
    expect(isSolved(grid, solution)).toBe(true)
    expect(findConflicts(grid).size).toBe(0)
  })
})

describe('数独：解の個数', () => {
  it('空の盤面は解がたくさんあるが、上限で打ち切る', () => {
    const empty = new Array<number>(SUDOKU_CELLS).fill(0)
    expect(countSolutions(empty, 1)).toBe(1)
    expect(countSolutions(empty, 2)).toBe(2)
    expect(countSolutions(empty, 5)).toBe(5)
  })

  it('完成盤の解は 1 つだけ', () => {
    expect(countSolutions(SOLUTION, 2)).toBe(1)
  })

  it('同じ行・列・ブロックに重複がある盤面は解なし（0）', () => {
    const rowDuplicate = [...SOLUTION]
    rowDuplicate[1] = rowDuplicate[0]
    expect(countSolutions(rowDuplicate, 2)).toBe(0)

    const columnDuplicate = [...SOLUTION]
    columnDuplicate[9] = columnDuplicate[0]
    expect(countSolutions(columnDuplicate, 2)).toBe(0)

    const partial = new Array<number>(SUDOKU_CELLS).fill(0)
    partial[0] = 4
    partial[1] = 4
    expect(countSolutions(partial, 2)).toBe(0)
  })

  it('空きマスが多すぎる盤面は解が一意にならない', () => {
    const grid = [...SOLUTION]
    for (let index = SUDOKU_SIZE; index < SUDOKU_CELLS; index += 1) grid[index] = 0
    expect(countSolutions(grid, 2)).toBe(2)
  })
})

describe('数独：重複の検出', () => {
  it('重複が無ければ空の集合を返す', () => {
    expect(findConflicts(SOLUTION).size).toBe(0)
    expect(findConflicts(new Array<number>(SUDOKU_CELLS).fill(0)).size).toBe(0)
  })

  it('同じ行の重複を両方のマスについて報告する', () => {
    const grid = new Array<number>(SUDOKU_CELLS).fill(0)
    grid[0] = 5
    grid[4] = 5 // 同じ行（別の列・別のブロック）
    expect(boxIndexOf(0)).not.toBe(boxIndexOf(4))
    expect([...findConflicts(grid)].sort((a, b) => a - b)).toEqual([0, 4])
  })

  it('同じ列の重複を検出する（ブロックは別でも重複）', () => {
    const grid = new Array<number>(SUDOKU_CELLS).fill(0)
    grid[0] = 5
    grid[36] = 5 // 同じ列（別の行・別のブロック）
    expect(boxIndexOf(0)).not.toBe(boxIndexOf(36))
    expect([...findConflicts(grid)].sort((a, b) => a - b)).toEqual([0, 36])
  })

  it('同じ 3×3 ブロックの重複を行・列が違っても検出する', () => {
    const grid = new Array<number>(SUDOKU_CELLS).fill(0)
    grid[0] = 5
    grid[10] = 5 // 同じブロック（別の行・別の列）
    expect(rowIndexOf(0)).not.toBe(rowIndexOf(10))
    expect(colIndexOf(0)).not.toBe(colIndexOf(10))
    expect([...findConflicts(grid)].sort((a, b) => a - b)).toEqual([0, 10])
  })

  it('1 つのマスが行と列の両方で重複しているときは、関係するマスをすべて報告する', () => {
    const grid = [...SOLUTION]
    grid[1] = grid[0] // 行 0 の 5 と、列 1 の 5（index 28）の両方と重複する
    expect(grid[28]).toBe(grid[0])
    expect([...findConflicts(grid)].sort((a, b) => a - b)).toEqual([0, 1, 28])
  })

  it('空きマス（0）は重複として扱わない', () => {
    const grid = new Array<number>(SUDOKU_CELLS).fill(0)
    expect(findConflicts(grid).size).toBe(0)
  })
})

describe('数独：候補数字と完成判定', () => {
  it('行・列・ブロックで使われている数字を除いた候補を返す', () => {
    const grid = new Array<number>(SUDOKU_CELLS).fill(0)
    // 調べるのは 0 行 1 列（ブロック 0）の空きマス
    grid[3] = 3 // 同じ行
    grid[28] = 2 // 同じ列
    grid[11] = 4 // 同じブロックだけ
    grid[0] = 5 // 同じ行・同じブロック
    grid[10] = 9 // 同じ列・同じブロック
    expect(candidatesFor(grid, 1)).toEqual([1, 6, 7, 8])
  })

  it('数字が入っているマスの候補は空になる', () => {
    expect(candidatesFor(SOLUTION, 0)).toEqual([])
  })

  it('完成盤・未完成・重複ありを区別する', () => {
    expect(isComplete(SOLUTION)).toBe(true)

    const withEmpty = [...SOLUTION]
    withEmpty[40] = 0
    expect(isComplete(withEmpty)).toBe(false)

    const withConflict = [...SOLUTION]
    withConflict[1] = withConflict[0]
    expect(isComplete(withConflict)).toBe(false)
  })

  it('解答との一致を判定する（長さが違えば不一致）', () => {
    expect(isSolved(SOLUTION, SOLUTION)).toBe(true)

    const almost = [...SOLUTION]
    almost[80] = almost[80] === 1 ? 2 : 1
    expect(isSolved(almost, SOLUTION)).toBe(false)

    expect(isSolved([1, 2, 3], SOLUTION)).toBe(false)
  })
})

/** 画面のテスト用に SudokuView をマウントする（RouterLink はスタブに置き換える）。 */
function mountView(): VueWrapper {
  return mount(SudokuView, { global: { stubs: { RouterLink: true } } })
}

/** 盤面の空きマス（初期数字ではないマス）の一覧。 */
function emptyCells(wrapper: VueWrapper): DOMWrapper<Element>[] {
  return wrapper.findAll('.gm-sudoku-cell').filter((cell) => !cell.classes().includes('is-given'))
}

/** 選択中のマスの位置（見つからないときは -1）。 */
function selectedIndex(wrapper: VueWrapper): number {
  return wrapper.findAll('.gm-sudoku-cell').findIndex((cell) => cell.classes().includes('is-selected'))
}

/** サイドパネルのボタンをラベルで探す。 */
function buttonByText(wrapper: VueWrapper, label: string): DOMWrapper<Element> {
  const found = wrapper.findAll('.gm-actions .btn').find((button) => button.text().includes(label))
  if (!found) throw new Error(`ボタンが見つかりません: ${label}`)
  return found
}

/** 残りマスの表示（メトリクスの 2 番目）。 */
function remainingCount(wrapper: VueWrapper): number {
  const metrics = wrapper.findAll('.gm-metric')
  return Number(metrics[1].find('.gm-metric__value').text())
}

describe('数独：画面', () => {
  it('9×9 のマスと数字パッドを描画し、初期数字に is-given を付ける', () => {
    const wrapper = mountView()
    const cells = wrapper.findAll('.gm-sudoku-cell')
    expect(cells).toHaveLength(SUDOKU_CELLS)
    // 初期値はふつう（34 マス）
    expect(cells.filter((cell) => cell.classes().includes('is-given'))).toHaveLength(34)
    expect(wrapper.findAll('.gm-numpad .btn')).toHaveLength(SUDOKU_SIZE + 1)
    expect(cells[0].attributes('aria-label')).toMatch(/^1行1列 (初期数字|入力済み|空き)/)
    wrapper.unmount()
  })

  it('盤面のマスの大きさは --gm-cell で指定する（難易度によらず 56px）', () => {
    const wrapper = mountView()
    const board = wrapper.find('.gm-sudoku').element as HTMLElement
    expect(board.style.getPropertyValue('--gm-cell').trim()).toBe('56px')
    wrapper.unmount()
  })

  it('数字パッドで入力でき、同じ数字をもう一度押すと消える', async () => {
    const wrapper = mountView()
    const target = emptyCells(wrapper)[0]
    await target.trigger('click')
    const numpad = wrapper.findAll('.gm-numpad .btn')
    await numpad[2].trigger('click') // 「3」
    expect(target.text()).toBe('3')
    await numpad[2].trigger('click') // もう一度押すと消える
    expect(target.text()).toBe('')
    wrapper.unmount()
  })

  it('キーボードで数字を入力し、矢印キーで選択マスを移動できる', async () => {
    const wrapper = mountView()
    const board = wrapper.find('.gm-sudoku')
    const before = selectedIndex(wrapper)
    expect(before).toBeGreaterThanOrEqual(0)

    await board.trigger('keydown', { key: '7' })
    expect(wrapper.findAll('.gm-sudoku-cell')[before].text()).toBe('7')

    // 矢印キーで選択が隣のマスへ移る（行の端ではそのまま）
    await board.trigger('keydown', { key: 'ArrowRight' })
    const after = selectedIndex(wrapper)
    expect(after).toBe(before % SUDOKU_SIZE === SUDOKU_SIZE - 1 ? before : before + 1)

    // 入力したマスに戻して、Backspace で消せることを確かめる
    await wrapper.findAll('.gm-sudoku-cell')[before].trigger('click')
    expect(selectedIndex(wrapper)).toBe(before)
    await board.trigger('keydown', { key: 'Backspace' })
    expect(wrapper.findAll('.gm-sudoku-cell')[before].text()).toBe('')
    wrapper.unmount()
  })

  it('初期数字は変更できず、警告を表示する', async () => {
    const wrapper = mountView()
    const toast = useToast()
    const givenIndex = wrapper.findAll('.gm-sudoku-cell').findIndex((cell) => cell.classes().includes('is-given'))
    const given = wrapper.findAll('.gm-sudoku-cell')[givenIndex]
    const original = given.text()

    await given.trigger('click')
    await wrapper.find('.gm-sudoku').trigger('keydown', { key: '1' })

    expect(wrapper.findAll('.gm-sudoku-cell')[givenIndex].text()).toBe(original)
    expect(toast.items.at(-1)?.type).toBe('warning')
    wrapper.unmount()
  })

  it('メモを ON にすると候補数字を書き込める', async () => {
    const wrapper = mountView()
    await buttonByText(wrapper, 'メモ').trigger('click')
    const numpad = wrapper.findAll('.gm-numpad .btn')
    await numpad[0].trigger('click') // 「1」
    await numpad[8].trigger('click') // 「9」

    const notes = wrapper.find('.gm-sudoku-notes')
    expect(notes.exists()).toBe(true)
    expect(notes.text()).toBe('19')
    wrapper.unmount()
  })

  it('ヒントを繰り返すと盤面が埋まり、状態が完成になる', async () => {
    const wrapper = mountView()
    const hint = buttonByText(wrapper, 'ヒント')
    let guard = 0
    while (remainingCount(wrapper) > 0 && guard < SUDOKU_CELLS + 5) {
      await hint.trigger('click')
      guard += 1
    }

    expect(remainingCount(wrapper)).toBe(0)
    expect(wrapper.find('.gm-status').classes()).toContain('is-win')
    expect(wrapper.find('.gm-status__title').text()).toBe('完成！')
    wrapper.unmount()
  })
})

import { describe, expect, it } from 'vitest'
import {
  NONOGRAM_PICTURES, NONOGRAM_PRESETS, cluesFor, createPuzzle, createRandomPuzzle, hintIndex,
  isSolved, lineProgress, mistakes, puzzleFromPicture, type NonoState
} from '@/features/game/nonogram'

/** 決まった順番で数を返す擬似乱数（テストを再現可能にする）。 */
function seededRng(seed: number): () => number {
  let value = seed
  return () => {
    value = (value * 1664525 + 1013904223) % 4294967296
    return value / 4294967296
  }
}

/** 解答から「プレイヤーが正しく塗った」状態を作る。 */
function filledFrom(solution: boolean[]): NonoState[] {
  return solution.map((filled): NonoState => (filled ? 'filled' : 'empty'))
}

/** 5×5 のハート（解答が分かっている問題）を作る。 */
function heartPuzzle(): ReturnType<typeof puzzleFromPicture> {
  return puzzleFromPicture('ハート', ['##.##', '#####', '#####', '.###.', '..#..'])
}

describe('ノノグラム：ヒント数字', () => {
  it('連続する塗りマスの長さをヒント数字にする', () => {
    expect(cluesFor([true, true, false, true, false])).toEqual([2, 1])
    expect(cluesFor([true, false, false, true, true])).toEqual([1, 2])
  })

  it('塗りマスが無い行は [0] を返す', () => {
    expect(cluesFor([false, false, false, false])).toEqual([0])
    expect(cluesFor([])).toEqual([0])
  })

  it('全部塗られた行は行の長さを返す', () => {
    expect(cluesFor([true, true, true, true, true])).toEqual([5])
  })

  it('端の塗りマスを取りこぼさない', () => {
    expect(cluesFor([true, true, false, false, true])).toEqual([2, 1])
    expect(cluesFor([true, false, true, false, true])).toEqual([1, 1, 1])
  })
})

describe('ノノグラム：プリセットと絵柄', () => {
  it('3 種類のプリセット（5×5 / 10×10 / 15×15）を持つ', () => {
    expect(NONOGRAM_PRESETS.map((preset) => preset.size)).toEqual([5, 10, 15])
    expect(NONOGRAM_PRESETS.every((preset) => preset.key.length > 0 && preset.label.length > 0)).toBe(true)
  })

  it('絵柄は 3 つ以上あり、すべて正方形で # と . だけを使う', () => {
    expect(NONOGRAM_PICTURES.length).toBeGreaterThanOrEqual(3)
    for (const picture of NONOGRAM_PICTURES) {
      expect(picture.pattern.length).toBeGreaterThan(0)
      for (const line of picture.pattern) {
        expect(line).toHaveLength(picture.pattern.length)
        expect(/^[#.]+$/.test(line)).toBe(true)
      }
    }
  })

  it('絵柄から問題を作ると解答・ヒント数字・名前が揃う', () => {
    const puzzle = puzzleFromPicture('テスト', ['.##', '###', '#.#'])
    expect(puzzle.size).toBe(3)
    expect(puzzle.label).toBe('テスト')
    expect(puzzle.solution).toEqual([false, true, true, true, true, true, true, false, true])
    expect(puzzle.rowClues).toEqual([[2], [3], [1, 1]])
    expect(puzzle.colClues).toEqual([[2], [2], [3]])
  })

  it('正方形でない絵柄は例外にする', () => {
    expect(() => puzzleFromPicture('x', ['##', '#'])).toThrow()
    expect(() => puzzleFromPicture('x', [])).toThrow()
  })

  it('絵柄に使えない文字があると例外にする', () => {
    expect(() => puzzleFromPicture('x', ['#x', '##'])).toThrow()
  })
})

describe('ノノグラム：ランダム生成', () => {
  it('指定したサイズの解答とヒント数字を作る', () => {
    const puzzle = createRandomPuzzle(10, seededRng(1))
    expect(puzzle.size).toBe(10)
    expect(puzzle.solution).toHaveLength(100)
    expect(puzzle.rowClues).toHaveLength(10)
    expect(puzzle.colClues).toHaveLength(10)

    // ヒント数字が解答と一致していること（全行・全列を検算）
    for (let row = 0; row < 10; row += 1) {
      expect(puzzle.rowClues[row]).toEqual(cluesFor(puzzle.solution.slice(row * 10, (row + 1) * 10)))
    }
    for (let col = 0; col < 10; col += 1) {
      const line: boolean[] = []
      for (let row = 0; row < 10; row += 1) line.push(puzzle.solution[row * 10 + col])
      expect(puzzle.colClues[col]).toEqual(cluesFor(line))
    }
  })

  it('空の行・空の列を作らない（20 回試行）', () => {
    for (let seed = 1; seed <= 20; seed += 1) {
      const puzzle = createRandomPuzzle(10, seededRng(seed))
      expect(puzzle.rowClues.every((clue) => clue[0] !== 0)).toBe(true)
      expect(puzzle.colClues.every((clue) => clue[0] !== 0)).toBe(true)
    }
  })

  it('塗るマスの割合がおおよそ 0.5 前後になる', () => {
    let filled = 0
    let total = 0
    for (let seed = 1; seed <= 20; seed += 1) {
      const puzzle = createRandomPuzzle(10, seededRng(seed))
      filled += puzzle.solution.filter(Boolean).length
      total += puzzle.solution.length
    }
    const density = filled / total
    expect(density).toBeGreaterThan(0.4)
    expect(density).toBeLessThan(0.72)
  })

  it('同じ種を使えば同じ問題になる', () => {
    const first = createRandomPuzzle(8, seededRng(5))
    const second = createRandomPuzzle(8, seededRng(5))
    expect(second.solution).toEqual(first.solution)
    expect(second.rowClues).toEqual(first.rowClues)
    expect(second.label).toBe(first.label)
  })

  it('サイズが不正な場合は例外にする', () => {
    expect(() => createRandomPuzzle(0)).toThrow()
    expect(() => createRandomPuzzle(-3)).toThrow()
  })
})

describe('ノノグラム：プリセットからの出題', () => {
  it('5×5 は固定の絵柄から出題する', () => {
    const pictureLabels = NONOGRAM_PICTURES.map((picture) => picture.label)
    for (let seed = 1; seed <= 12; seed += 1) {
      const puzzle = createPuzzle('easy', seededRng(seed))
      expect(puzzle.size).toBe(5)
      expect(puzzle.solution).toHaveLength(25)
      expect(pictureLabels).toContain(puzzle.label)
    }
    // 乱数の値によって選ばれる絵柄が変わる
    expect(createPuzzle('easy', () => 0.01).label).toBe(NONOGRAM_PICTURES[0].label)
    expect(createPuzzle('easy', () => 0.99).label).toBe(NONOGRAM_PICTURES[NONOGRAM_PICTURES.length - 1].label)
  })

  it('10×10 以上はランダム生成にする', () => {
    const puzzle = createPuzzle('standard', seededRng(3))
    expect(puzzle.size).toBe(10)
    expect(puzzle.solution).toHaveLength(100)
    expect(puzzle.label).toContain('ランダム')
    expect(puzzle.solution.some(Boolean)).toBe(true)
  })

  it('知らないプリセットは例外にする', () => {
    expect(() => createPuzzle('unknown')).toThrow()
  })
})

describe('ノノグラム：完成判定と手がかりの状態', () => {
  it('塗ったマスが解答と完全に一致したら完成', () => {
    const puzzle = heartPuzzle()
    expect(isSolved(filledFrom(puzzle.solution), puzzle.solution)).toBe(true)
    // ×印は塗っていないので完成にはならない
    const crossed: NonoState[] = puzzle.solution.map((filled): NonoState => (filled ? 'crossed' : 'empty'))
    expect(isSolved(crossed, puzzle.solution)).toBe(false)
  })

  it('塗り忘れ・塗りすぎ・長さ違いは完成ではない', () => {
    const puzzle = heartPuzzle()
    const states = filledFrom(puzzle.solution)
    states[0] = 'empty'
    expect(isSolved(states, puzzle.solution)).toBe(false)

    const extra = filledFrom(puzzle.solution)
    extra[2] = 'filled' // 1 行目 3 列目（'##.##' なので解答では空）
    expect(isSolved(extra, puzzle.solution)).toBe(false)

    expect(isSolved([], puzzle.solution)).toBe(false)
  })

  it('lineProgress は満たした / 塗りすぎ / 途中 を区別する', () => {
    expect(lineProgress([2], ['filled', 'filled', 'empty', 'empty', 'empty'])).toBe('satisfied')
    expect(lineProgress([1, 1], ['filled', 'empty', 'filled', 'empty', 'empty'])).toBe('satisfied')
    expect(lineProgress([0], ['empty', 'empty', 'empty', 'empty', 'empty'])).toBe('satisfied')

    expect(lineProgress([2], ['filled', 'filled', 'filled', 'empty', 'empty'])).toBe('over')
    expect(lineProgress([0], ['filled', 'empty', 'empty', 'empty', 'empty'])).toBe('over')

    expect(lineProgress([2], ['empty', 'filled', 'empty', 'empty', 'empty'])).toBe('pending')
    expect(lineProgress([2], ['empty', 'empty', 'empty', 'empty', 'empty'])).toBe('pending')
    // 数は合っているが並びが違う
    expect(lineProgress([2], ['filled', 'empty', 'filled', 'empty', 'empty'])).toBe('pending')
    // ×印は塗りマスとして数えない
    expect(lineProgress([2], ['crossed', 'crossed', 'empty', 'empty', 'empty'])).toBe('pending')
  })

  it('mistakes は解答と違う塗りマスの位置を返す', () => {
    const puzzle = heartPuzzle()
    const states = filledFrom(puzzle.solution)
    states[2] = 'filled' // 1 行目 3 列目（'##.##' なので間違い）
    states[24] = 'filled' // 5 行目 5 列目（'..#..' なので間違い）
    expect(mistakes(states, puzzle.solution)).toEqual([2, 24])

    // ×印は間違いとして数えない
    states[2] = 'crossed'
    states[24] = 'crossed'
    expect(mistakes(states, puzzle.solution)).toEqual([])
  })

  it('hintIndex はまだ塗られていない解答マスを返す', () => {
    const puzzle = heartPuzzle()
    const states = filledFrom(puzzle.solution)
    expect(hintIndex(states, puzzle.solution)).toBe(-1)

    states[0] = 'empty'
    const index = hintIndex(states, puzzle.solution, seededRng(2))
    expect(index).toBe(0)
    expect(puzzle.solution[index]).toBe(true)
  })

  it('hintIndex は ×印の解答マスもヒントの対象にする', () => {
    const puzzle = heartPuzzle()
    const states = filledFrom(puzzle.solution)
    states[0] = 'crossed' // 解答マスを 1 つだけ ×印 にしたので、対象はそこだけになる
    expect(hintIndex(states, puzzle.solution, seededRng(1))).toBe(0)
  })
})

describe('ノノグラム：解答から出題まで通しで確認する', () => {
  it('解答どおりに塗るとヒント数字がすべて満たされる', () => {
    const puzzle = heartPuzzle()
    const states = filledFrom(puzzle.solution)
    expect(isSolved(states, puzzle.solution)).toBe(true)
    const progress = puzzle.rowClues.map((clue, row) => lineProgress(clue, states.slice(row * 5, (row + 1) * 5)))
    expect(progress).toEqual(puzzle.rowClues.map(() => 'satisfied'))
  })

  it('ランダム問題でも解答どおりに塗れば完成する', () => {
    for (let seed = 1; seed <= 10; seed += 1) {
      const puzzle = createRandomPuzzle(10, seededRng(seed))
      expect(isSolved(filledFrom(puzzle.solution), puzzle.solution)).toBe(true)
      expect(mistakes(filledFrom(puzzle.solution), puzzle.solution)).toEqual([])
    }
  })
})

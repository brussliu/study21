import { describe, expect, it } from 'vitest'
import {
  MINE_PRESETS, boardStatus, chordCell, countFlags, createBoard, indexOf, minePreset, neighborIndexes,
  openCell, remainingMines, revealMines, toggleFlag, type MineBoard, type MinePreset
} from '@/features/game/minesweeper'

/** 決まった順番で数を返す擬似乱数（テストを再現可能にする）。 */
function seededRng(seed: number): () => number {
  let value = seed
  return () => {
    value = (value * 1664525 + 1013904223) % 4294967296
    return value / 4294967296
  }
}

const SMALL: MinePreset = { key: 'beginner', label: 'test 3×3', rows: 3, cols: 3, mines: 1 }

/** 地雷の位置を直接指定して盤面を作る（配置ロジックを通さないテスト用）。 */
function boardWithMines(rows: number, cols: number, mineIndexes: number[]): MineBoard {
  const board = createBoard({ key: 'beginner', label: 'test', rows, cols, mines: mineIndexes.length })
  for (const index of mineIndexes) board.cells[index].mine = true
  for (let index = 0; index < board.cells.length; index += 1) {
    board.cells[index].adjacent = neighborIndexes(board, index).filter((n) => board.cells[n].mine).length
  }
  board.armed = true
  return board
}

describe('マインスイーパー：盤面の生成', () => {
  it('プリセットのサイズどおりに空の盤面を作る', () => {
    const board = createBoard(minePreset('beginner'))
    expect(board.rows).toBe(9)
    expect(board.cols).toBe(9)
    expect(board.cells).toHaveLength(81)
    expect(board.mines).toBe(10)
    expect(board.armed).toBe(false)
    expect(board.opened).toBe(0)
    expect(board.cells.every((cell) => !cell.mine && !cell.open && !cell.flag)).toBe(true)
  })

  it('3 種類の難易度プリセットを持つ', () => {
    expect(MINE_PRESETS.map((preset) => preset.key)).toEqual(['beginner', 'intermediate', 'expert'])
  })

  it('周囲 8 マスを盤外を除いて返す', () => {
    const board = createBoard({ key: 'beginner', label: 'test', rows: 3, cols: 3, mines: 1 })
    expect(neighborIndexes(board, 4)).toEqual([0, 1, 2, 3, 5, 6, 7, 8])
    expect(neighborIndexes(board, 0)).toEqual([1, 3, 4])
    expect(indexOf(board, 2, 1)).toBe(7)
  })
})

describe('マインスイーパー：地雷の配置', () => {
  it('初手のマスとその周囲には地雷を置かない', () => {
    const board = createBoard(minePreset('intermediate'))
    const safe = indexOf(board, 8, 8)
    openCell(board, safe, seededRng(7))
    expect(board.cells[safe].mine).toBe(false)
    expect(neighborIndexes(board, safe).some((index) => board.cells[index].mine)).toBe(false)
  })

  it('指定した数の地雷を置き、隣接数を数える', () => {
    const board = createBoard(minePreset('beginner'))
    openCell(board, 0, seededRng(3))
    expect(board.cells.filter((cell) => cell.mine)).toHaveLength(10)

    // 隣接数が実際の周囲の地雷数と一致すること（地雷以外の全マスを検算）
    for (let index = 0; index < board.cells.length; index += 1) {
      if (board.cells[index].mine) continue
      const expected = neighborIndexes(board, index).filter((n) => board.cells[n].mine).length
      expect(board.cells[index].adjacent).toBe(expected)
    }
  })

  it('初手で地雷を踏まない（200 回試行）', () => {
    for (let attempt = 0; attempt < 200; attempt += 1) {
      const board = createBoard(minePreset('beginner'))
      const result = openCell(board, 40, seededRng(attempt + 1))
      expect(result.exploded).toBe(false)
      expect(board.exploded).toBeNull()
    }
  })

  it('地雷が多すぎる場合は例外にする', () => {
    const board = createBoard({ key: 'beginner', label: 'test', rows: 2, cols: 2, mines: 4 })
    expect(() => openCell(board, 0, seededRng(1))).toThrow()
  })
})

describe('マインスイーパー：マスを開く', () => {
  it('0 のマスは周囲へ連鎖して開く', () => {
    const board = boardWithMines(3, 3, [8])
    const result = openCell(board, 0)
    expect(result.exploded).toBe(false)
    // 8 以外（地雷の周囲でない）はすべて開く
    expect(board.opened).toBe(8)
    expect(board.cells.filter((cell) => cell.open)).toHaveLength(8)
    expect(board.cells[8].open).toBe(false)
  })

  it('旗の立ったマスは連鎖で開かない', () => {
    const board = boardWithMines(3, 3, [8])
    toggleFlag(board, 7)
    openCell(board, 0)
    expect(board.cells[7].open).toBe(false)
    expect(board.opened).toBe(7)
  })

  it('地雷を開くと爆発し、盤面の状態が lost になる', () => {
    const board = boardWithMines(3, 3, [4])
    const result = openCell(board, 4)
    expect(result.exploded).toBe(true)
    expect(board.exploded).toBe(4)
    expect(boardStatus(board)).toBe('lost')
  })

  it('開いたマス・旗のマスをもう一度開いても何も起きない', () => {
    const board = boardWithMines(3, 3, [8])
    openCell(board, 0)
    const before = board.opened
    expect(openCell(board, 0).opened).toEqual([])
    expect(board.opened).toBe(before)

    toggleFlag(board, 8)
    expect(openCell(board, 8).opened).toEqual([])
    expect(board.exploded).toBeNull()
  })
})

describe('マインスイーパー：旗と勝敗', () => {
  it('旗を立てて外せる（開いたマスは対象外）', () => {
    const board = boardWithMines(3, 3, [8])
    expect(toggleFlag(board, 0)).toBe(true)
    expect(countFlags(board)).toBe(1)
    expect(remainingMines(board)).toBe(0)
    expect(toggleFlag(board, 0)).toBe(false)
    expect(countFlags(board)).toBe(0)

    board.cells[1].open = true
    expect(toggleFlag(board, 1)).toBe(false)
  })

  it('地雷以外をすべて開くとクリア判定', () => {
    const board = boardWithMines(3, 3, [4])
    for (const index of [0, 1, 2, 3, 5, 6, 7, 8]) openCell(board, index)
    expect(boardStatus(board)).toBe('won')
  })

  it('開始前・プレイ中の状態を区別する', () => {
    const board = boardWithMines(3, 3, [4])
    expect(boardStatus(board)).toBe('ready')
    openCell(board, 0)
    expect(boardStatus(board)).toBe('playing')
  })

  it('失敗時はすべての地雷を開き、付け間違いの旗に印を付ける', () => {
    const board = boardWithMines(3, 3, [4])
    toggleFlag(board, 0)
    openCell(board, 4)
    revealMines(board)
    expect(board.cells[4].open).toBe(true)
    expect(board.cells[0].wrong).toBe(true)
    // 旗を立てていないマスには印を付けない
    expect(board.cells.slice(1, 4).some((cell) => cell.wrong)).toBe(false)
    expect(board.cells[8].wrong).toBe(false)
  })
})

describe('マインスイーパー：左右同時押し（チョード）', () => {
  /** 中央 (4) が数字 2 になる盤面（地雷は 0 と 1）。 */
  function chordBoard(): MineBoard {
    const board = boardWithMines(3, 3, [0, 1])
    openCell(board, 4)
    return board
  }

  it('旗の数が数字と一致していれば、旗のない周囲のマスをまとめて開く', () => {
    const board = chordBoard()
    toggleFlag(board, 0)
    toggleFlag(board, 1)

    const result = chordCell(board, 4)
    expect(result.triggered).toBe(true)
    expect(result.exploded).toBe(false)
    // 周囲の未開封マス（2,3,5,6,7,8）がすべて開く
    for (const index of [2, 3, 5, 6, 7, 8]) {
      expect(board.cells[index].open).toBe(true)
    }
    // 旗のマスは開かない
    expect(board.cells[0].open).toBe(false)
    expect(board.cells[1].open).toBe(false)
    expect(result.opened).toContain(3)
  })

  it('旗の数が足りないときは何もしない', () => {
    const board = chordBoard()
    toggleFlag(board, 0)
    expect(chordCell(board, 4)).toEqual({ triggered: false, opened: [], exploded: false })
    expect(board.opened).toBe(1)
  })

  it('旗の数が多いときも何もしない', () => {
    const board = chordBoard()
    toggleFlag(board, 0)
    toggleFlag(board, 1)
    toggleFlag(board, 3)
    expect(chordCell(board, 4).triggered).toBe(false)
    expect(board.cells[3].open).toBe(false)
  })

  it('旗の位置が間違っていると地雷を踏んで失敗になる', () => {
    const board = chordBoard()
    toggleFlag(board, 1) // 正しい旗
    toggleFlag(board, 3) // 間違い（地雷ではない）
    const result = chordCell(board, 4)

    expect(result.triggered).toBe(true)
    expect(result.exploded).toBe(true)
    expect(boardStatus(board)).toBe('lost')
    expect(board.exploded).toBe(0)
  })

  it('未開封・地雷・数字 0 のマスでは動かない', () => {
    const board = boardWithMines(3, 3, [8])
    // 未開封のマス
    expect(chordCell(board, 0).triggered).toBe(false)

    openCell(board, 0) // 数字 0 なので周囲が開く
    expect(board.cells[0].adjacent).toBe(0)
    expect(chordCell(board, 0).triggered).toBe(false)

    // 地雷のマス（開いてしまった場合）
    const exploded = boardWithMines(3, 3, [4])
    openCell(exploded, 4)
    expect(chordCell(exploded, 4).triggered).toBe(false)
  })

  it('チョードで最後のマスを開くとクリア判定になる', () => {
    // 2×2・地雷 1。1 を開いて旗を立て、1 から周囲（2 と 3）をまとめて開く
    const board = boardWithMines(2, 2, [0])
    openCell(board, 1)
    toggleFlag(board, 0)
    expect(boardStatus(board)).toBe('playing')

    const result = chordCell(board, 1)
    expect(result.triggered).toBe(true)
    expect(result.exploded).toBe(false)
    expect(boardStatus(board)).toBe('won')
  })

  it('初手前（地雷未配置）では動かない', () => {
    const board = createBoard(SMALL)
    expect(chordCell(board, 0).triggered).toBe(false)
  })
})

describe('マインスイーパー：小さい盤面でも破綻しない', () => {
  it('3×3・地雷 1 で初手安全が成立する', () => {
    const board = createBoard(SMALL)
    const result = openCell(board, 4, seededRng(11))
    expect(result.exploded).toBe(false)
    expect(board.cells.filter((cell) => cell.mine)).toHaveLength(1)
    expect(board.cells[4].mine).toBe(false)
  })
})

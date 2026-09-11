import { describe, expect, it } from 'vitest'
import {
  BOARD_2048_SIZE, TARGET_2048, addRandomTile, canMove, cloneBoard, createBoard2048,
  emptyIndexes, maxTile, move, slideLine, type Board2048, type Rng
} from '@/features/game/game2048'

/** 決まった順番で数を返す擬似乱数（テストを再現可能にする）。 */
function seededRng(seed: number): Rng {
  let value = seed
  return () => {
    value = (value * 1664525 + 1013904223) % 4294967296
    return value / 4294967296
  }
}

/** 指定した値を順番に返す乱数（足りなくなったら最後の値を返し続ける）。 */
function seqRng(values: number[]): Rng {
  let index = 0
  return () => {
    const value = values[Math.min(index, values.length - 1)]
    index += 1
    return value
  }
}

/** 固定の盤面を作る（乱数を通さないテスト用）。 */
function boardWith(cells: number[], overrides: Partial<Board2048> = {}): Board2048 {
  const board: Board2048 = { cells: [...cells], score: 0, moves: 0, reached: false, over: false }
  return Object.assign(board, overrides)
}

/**
 * テスト用の固定配置。
 *   2 . . .
 *   2 . . .
 *   . . . 4
 *   . . . 4
 */
const GRID = [2, 0, 0, 0, 2, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 4]

/** 合体できるペアが 1 つもない（＝動かせない）満杯の盤面。 */
const FULL = [
  2, 4, 2, 4,
  4, 2, 4, 2,
  2, 4, 2, 4,
  4, 2, 4, 2
]

/** 満杯だが縦に合体できる盤面。 */
const FULL_VERTICAL = [
  2, 4, 2, 4,
  4, 2, 4, 2,
  4, 2, 4, 2,
  2, 4, 2, 4
]

/** 満杯だが横に合体できる盤面。 */
const FULL_HORIZONTAL = [
  2, 2, 4, 2,
  4, 2, 4, 2,
  2, 4, 2, 4,
  4, 2, 4, 2
]

describe('2048：盤面の生成', () => {
  it('16 マスを作り、最初はタイルを 2 つだけ置く', () => {
    const board = createBoard2048(seededRng(1))
    expect(board.cells).toHaveLength(BOARD_2048_SIZE * BOARD_2048_SIZE)
    expect(board.cells.filter((value) => value > 0)).toHaveLength(2)
    expect(board.cells.every((value) => value === 0 || value === 2 || value === 4)).toBe(true)
    expect(board.score).toBe(0)
    expect(board.moves).toBe(0)
    expect(board.reached).toBe(false)
    expect(board.over).toBe(false)
  })

  it('同じ乱数を渡せば同じ初期盤面になる', () => {
    const first = createBoard2048(seededRng(42))
    const second = createBoard2048(seededRng(42))
    const other = createBoard2048(seededRng(43))
    expect(first.cells).toEqual(second.cells)
    expect(first.cells).not.toEqual(other.cells)
  })

  it('初期タイルは重複しない空きマスに置かれる', () => {
    const board = createBoard2048(seededRng(7))
    const placed = board.cells.filter((value) => value > 0)
    expect(placed).toHaveLength(2)
    expect(emptyIndexes(board.cells)).toHaveLength(BOARD_2048_SIZE * BOARD_2048_SIZE - 2)
  })
})

describe('2048：1 行／1 列の合成（slideLine）', () => {
  it('空きを詰めて同じ数字を合体させる', () => {
    const result = slideLine([2, 0, 2, 0])
    expect(result.line).toEqual([4, 0, 0, 0])
    expect(result.gained).toBe(4)
    expect(result.mergedOffsets).toEqual([0])
  })

  it('1 つのタイルが 1 手で 2 回合体しない', () => {
    const result = slideLine([2, 2, 2, 2])
    expect(result.line).toEqual([4, 4, 0, 0])
    expect(result.gained).toBe(8)
    expect(result.mergedOffsets).toEqual([0, 1])
  })

  it('合体は詰める向きの先頭から順に行う', () => {
    const result = slideLine([4, 4, 8, 0])
    expect(result.line).toEqual([8, 8, 0, 0])
    expect(result.gained).toBe(8)
    expect(result.mergedOffsets).toEqual([0])
  })

  it('動かない行はそのまま返す', () => {
    const result = slideLine([2, 4, 8, 16])
    expect(result.line).toEqual([2, 4, 8, 16])
    expect(result.gained).toBe(0)
    expect(result.mergedOffsets).toEqual([])
  })

  it('空の行はすべて 0 のままにする', () => {
    const result = slideLine([0, 0, 0, 0])
    expect(result.line).toEqual([0, 0, 0, 0])
    expect(result.gained).toBe(0)
    expect(result.mergedOffsets).toEqual([])
  })
})

describe('2048：新しいタイルの出現', () => {
  it('90% で 2、10% で 4 を置く', () => {
    const two = new Array<number>(16).fill(0)
    expect(addRandomTile(two, seqRng([0, 0.899]))).toBe(0)
    expect(two[0]).toBe(2)

    const four = new Array<number>(16).fill(0)
    expect(addRandomTile(four, seqRng([0, 0.9]))).toBe(0)
    expect(four[0]).toBe(4)
  })

  it('空きマスがないときは何も置かず null を返す', () => {
    const cells = [...FULL]
    expect(addRandomTile(cells, () => 0)).toBeNull()
    expect(cells).toEqual(FULL)
  })

  it('空きマスの中から位置を選ぶ', () => {
    const cells = new Array<number>(16).fill(2)
    cells[15] = 0
    expect(addRandomTile(cells, seqRng([0.5, 0.95]))).toBe(15)
    expect(cells[15]).toBe(4)
  })
})

describe('2048：盤面の移動', () => {
  it('左に動かすと行ごとに左詰めになる', () => {
    const board = boardWith(GRID)
    const result = move(board, 'left', seqRng([0, 0]))
    expect(result.moved).toBe(true)
    expect(result.gained).toBe(0)
    expect(result.merged).toEqual([])
    expect(board.cells.slice(0, 4)).toEqual([2, 2, 0, 0])
    expect(board.cells[8]).toBe(4)
    expect(board.cells[12]).toBe(4)
    expect(board.cells[3]).toBe(0)
    expect(board.moves).toBe(1)
    // 出現したタイルは空きマスの先頭（位置 1）に置かれる
    expect(result.spawned).toBe(1)
    expect(board.cells[1]).toBe(2)
  })

  it('右に動かすと行ごとに右詰めになる', () => {
    const board = boardWith(GRID)
    const result = move(board, 'right', seqRng([0, 0]))
    expect(result.moved).toBe(true)
    expect(result.gained).toBe(0)
    expect(board.cells[3]).toBe(2)
    expect(board.cells[7]).toBe(2)
    expect(board.cells[11]).toBe(4)
    expect(board.cells[15]).toBe(4)
    // 出現したタイルは空きマスの先頭（位置 0）に置かれる
    expect(result.spawned).toBe(0)
    expect(board.cells[0]).toBe(2)
  })

  it('上に動かすと列ごとに詰めて合体する', () => {
    const board = boardWith(GRID)
    const result = move(board, 'up', seqRng([0, 0]))
    expect(result.moved).toBe(true)
    expect(result.gained).toBe(12)
    expect(result.merged).toEqual([0, 3])
    expect(board.cells[0]).toBe(4)
    expect(board.cells[3]).toBe(8)
    expect(board.score).toBe(12)
    expect(board.moves).toBe(1)
    expect(result.spawned).toBe(1)
  })

  it('下に動かすと列ごとに下詰めにして合体する', () => {
    const board = boardWith(GRID)
    const result = move(board, 'down', seqRng([0, 0]))
    expect(result.moved).toBe(true)
    expect(result.gained).toBe(12)
    expect(result.merged).toEqual([12, 15])
    expect(board.cells[12]).toBe(4)
    expect(board.cells[15]).toBe(8)
    // 出現したタイルは空きマスの先頭（位置 0）に置かれる
    expect(result.spawned).toBe(0)
    expect(board.cells[0]).toBe(2)
    expect(board.score).toBe(12)
  })

  it('動きがないときはタイルもスコアも手数も変えない', () => {
    const board = boardWith([2, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0])
    const result = move(board, 'left')
    expect(result).toEqual({ moved: false, gained: 0, merged: [], spawned: null })
    expect(board.cells.slice(0, 4)).toEqual([2, 4, 0, 0])
    expect(board.score).toBe(0)
    expect(board.moves).toBe(0)
    expect(board.over).toBe(false)
  })

  it('動かせなくなるとゲームオーバーになる', () => {
    const board = boardWith(FULL)
    const result = move(board, 'left')
    expect(result.moved).toBe(false)
    expect(result.spawned).toBeNull()
    expect(result.merged).toEqual([])
    expect(board.over).toBe(true)
    expect(board.cells).toEqual(FULL)
  })

  it('ゲームオーバー後の移動は受け付けない', () => {
    const board = boardWith(FULL, { over: true, score: 100, moves: 9 })
    const result = move(board, 'up')
    expect(result.moved).toBe(false)
    expect(board.over).toBe(true)
    expect(board.score).toBe(100)
    expect(board.moves).toBe(9)
    expect(board.cells).toEqual(FULL)
  })

  it('2048 に到達すると reached が立ち、そのまま続けられる', () => {
    const board = boardWith([1024, 1024, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0])
    const result = move(board, 'left', seqRng([0, 0]))
    expect(result.gained).toBe(TARGET_2048)
    expect(board.cells[0]).toBe(TARGET_2048)
    expect(board.score).toBe(TARGET_2048)
    expect(board.reached).toBe(true)
    // まだ空きマスがあるので続行できる
    expect(board.over).toBe(false)
    expect(result.spawned).toBe(1)
  })
})

describe('2048：補助関数', () => {
  it('空きマスの一覧と最大タイルを返す', () => {
    expect(emptyIndexes([2, 0, 4, 0])).toEqual([1, 3])
    expect(emptyIndexes([2, 4, 8, 16])).toEqual([])
    expect(maxTile([0, 2, 0, 8])).toBe(8)
    expect(maxTile(new Array<number>(16).fill(0))).toBe(0)
  })

  it('canMove は空きマスがあるか隣り合う同じ数字で判定する', () => {
    expect(canMove(new Array<number>(16).fill(0))).toBe(true)
    expect(canMove(FULL)).toBe(false)
    expect(canMove(FULL_VERTICAL)).toBe(true)
    expect(canMove(FULL_HORIZONTAL)).toBe(true)
  })

  it('cloneBoard は盤面を複製する（元の盤面を壊さない）', () => {
    const board = boardWith(GRID, { score: 8, moves: 3, reached: true })
    const copy = cloneBoard(board)
    expect(copy).not.toBe(board)
    expect(copy.cells).not.toBe(board.cells)
    expect(copy.cells).toEqual(board.cells)

    copy.cells[0] = 999
    copy.score = 100
    expect(board.cells[0]).toBe(2)
    expect(board.score).toBe(8)
    expect(board.moves).toBe(3)
    expect(board.reached).toBe(true)
  })
})

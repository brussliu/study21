/**
 * 2048 のロジック（UI から独立。テストは tests/games/game2048.spec.ts）。
 *
 * ・盤面オブジェクトは「破壊的に」更新する（Vue の ref に入れて使う前提）。
 * ・乱数は差し替え可能（テストで固定値を使う）。1 回の出現で 2 回だけ rng を呼ぶ
 *   （1 回目で置くマス、2 回目で 2 か 4 かを決める）。
 * ・移動は 4 方向とも「1 行／1 列を取り出して slideLine に通し、元に戻す」だけで表現する。
 */

/** 0 以上 1 未満の乱数を返す関数。 */
export type Rng = () => number

export type Direction2048 = 'up' | 'down' | 'left' | 'right'

/** 盤面の一辺のマス数（4 × 4）。 */
export const BOARD_2048_SIZE = 4

/** 盤面のマス数（16）。 */
export const BOARD_2048_CELLS = BOARD_2048_SIZE * BOARD_2048_SIZE

/** 到達を目指すタイルの値。 */
export const TARGET_2048 = 2048

/** 新しいタイルが 2 になる確率（残りは 4）。 */
const SPAWN_TWO_RATE = 0.9

export interface Board2048 {
  /** 長さ 16 のマス（0 は空）。 */
  cells: number[]
  /** 合計スコア。 */
  score: number
  /** 有効だった移動の回数。 */
  moves: number
  /** 2048 に到達したか（一度でも到達したら true）。 */
  reached: boolean
  /** 動かせる場所がなくなったか。 */
  over: boolean
}

export interface MoveResult {
  /** 盤面が実際に動いたか。 */
  moved: boolean
  /** 今回の合体で増えたスコア。 */
  gained: number
  /** 合体してできたタイルの位置（アニメーション用）。 */
  merged: number[]
  /** 新しいタイルが出現した位置（出現しなかったら null）。 */
  spawned: number | null
}

/** 空のマスの位置を昇順で返す。 */
export function emptyIndexes(cells: number[]): number[] {
  const result: number[] = []
  for (let index = 0; index < cells.length; index += 1) {
    if (cells[index] === 0) result.push(index)
  }
  return result
}

/** 盤面の中で一番大きなタイルの値（空の盤面は 0）。 */
export function maxTile(cells: number[]): number {
  return cells.reduce((max, value) => (value > max ? value : max), 0)
}

/**
 * 空いているマスに新しいタイル（90% で 2、10% で 4）を 1 つ置く。
 * 置いた位置を返す（空きがなければ null）。
 */
export function addRandomTile(cells: number[], rng: Rng = Math.random): number | null {
  const empty = emptyIndexes(cells)
  if (empty.length === 0) return null

  const pick = Math.min(empty.length - 1, Math.floor(rng() * empty.length))
  const index = empty[pick]
  cells[index] = rng() < SPAWN_TWO_RATE ? 2 : 4
  return index
}

/**
 * 1 行／1 列を「詰める向き」に並べた配列を受け取り、左詰め＋合体した結果を返す純関数。
 * 合体は左から順に行い、1 つのタイルが 1 手で 2 回合体しないようにする。
 * `mergedOffsets` は結果の配列の中で「合体してできたタイル」の位置。
 */
export function slideLine(line: number[]): { line: number[]; gained: number; mergedOffsets: number[] } {
  const tiles = line.filter((value) => value !== 0)
  const result: number[] = []
  const mergedOffsets: number[] = []
  let gained = 0

  for (let index = 0; index < tiles.length; index += 1) {
    const value = tiles[index]
    if (index + 1 < tiles.length && tiles[index + 1] === value) {
      const merged = value * 2
      result.push(merged)
      mergedOffsets.push(result.length - 1)
      gained += merged
      index += 1 // 合体に使ったタイルは消費する
    } else {
      result.push(value)
    }
  }

  while (result.length < line.length) result.push(0)
  return { line: result, gained, mergedOffsets }
}

/**
 * それぞれの方向について「詰める向きに並べた」1 行／1 列のマス位置。
 * 例: left は各行を左から、down は各列を下から並べる。
 */
function lineIndexes(direction: Direction2048): number[][] {
  const last = BOARD_2048_SIZE - 1
  const lines: number[][] = []
  for (let outer = 0; outer < BOARD_2048_SIZE; outer += 1) {
    const line: number[] = []
    for (let inner = 0; inner < BOARD_2048_SIZE; inner += 1) {
      switch (direction) {
        case 'left':
          line.push(outer * BOARD_2048_SIZE + inner)
          break
        case 'right':
          line.push(outer * BOARD_2048_SIZE + (last - inner))
          break
        case 'up':
          line.push(inner * BOARD_2048_SIZE + outer)
          break
        default:
          line.push((last - inner) * BOARD_2048_SIZE + outer)
          break
      }
    }
    lines.push(line)
  }
  return lines
}

/** 空の盤面に新しいタイルを 2 つ置いて返す（2048 の初期状態）。 */
export function createBoard2048(rng: Rng = Math.random): Board2048 {
  const cells: number[] = new Array<number>(BOARD_2048_CELLS).fill(0)
  addRandomTile(cells, rng)
  addRandomTile(cells, rng)
  return {
    cells,
    score: 0,
    moves: 0,
    reached: maxTile(cells) >= TARGET_2048,
    over: false
  }
}

/** 盤面の複製を作る（「元に戻す」用に状態を保存するときなど）。 */
export function cloneBoard(board: Board2048): Board2048 {
  return {
    cells: [...board.cells],
    score: board.score,
    moves: board.moves,
    reached: board.reached,
    over: board.over
  }
}

/**
 * 盤面を指定した方向へ動かす（board を破壊的に更新する）。
 * 動いた場合だけ新しいタイルを 1 つ出現させ、スコアと手数を更新する。
 */
export function move(board: Board2048, direction: Direction2048, rng: Rng = Math.random): MoveResult {
  const merged: number[] = []
  let moved = false
  let gained = 0

  for (const indexes of lineIndexes(direction)) {
    const line = indexes.map((index) => board.cells[index])
    const result = slideLine(line)
    if (result.line.some((value, offset) => value !== line[offset])) moved = true
    for (let offset = 0; offset < indexes.length; offset += 1) {
      board.cells[indexes[offset]] = result.line[offset]
    }
    for (const offset of result.mergedOffsets) merged.push(indexes[offset])
    gained += result.gained
  }

  if (!moved) {
    // 動けなかったときもゲームオーバー判定だけは更新する
    board.over = !canMove(board.cells)
    return { moved: false, gained: 0, merged: [], spawned: null }
  }

  board.score += gained
  board.moves += 1
  const spawned = addRandomTile(board.cells, rng)
  if (maxTile(board.cells) >= TARGET_2048) board.reached = true
  board.over = !canMove(board.cells)
  return { moved: true, gained, merged, spawned }
}

/** 空きマスがあるか、隣り合う同じ数字を合体できるなら true。 */
export function canMove(cells: number[]): boolean {
  if (emptyIndexes(cells).length > 0) return true

  for (let row = 0; row < BOARD_2048_SIZE; row += 1) {
    for (let col = 0; col < BOARD_2048_SIZE; col += 1) {
      const value = cells[row * BOARD_2048_SIZE + col]
      if (col + 1 < BOARD_2048_SIZE && cells[row * BOARD_2048_SIZE + col + 1] === value) return true
      if (row + 1 < BOARD_2048_SIZE && cells[(row + 1) * BOARD_2048_SIZE + col] === value) return true
    }
  }
  return false
}

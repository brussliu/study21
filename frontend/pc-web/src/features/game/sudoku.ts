/**
 * 数独のロジック（UI から独立。テストは tests/games/sudoku.spec.ts）。
 *
 * ・盤面は長さ 81 の配列で表す（0 が空きマス、1〜9 が数字）。
 * ・解答はランダム化バックトラックで作り、そこから「解が一意のまま」マスを取り除いて問題にする。
 * ・乱数は差し替え可能（テストで固定値を使う）。
 * ・盤面は破壊的に更新しない（新しい配列を返す）。呼び出し側は ref に入れて使う。
 */

/** 0 以上 1 未満の乱数を返す関数。 */
export type Rng = () => number

export type SudokuDifficulty = 'easy' | 'normal' | 'hard'

export interface SudokuPreset {
  key: SudokuDifficulty
  label: string
  /** 初期数字（はじめから入っているマス）の数。少ないほど難しい。 */
  givens: number
}

/** 難易度プリセット（易しい / 普通 / 難しい）。 */
export const SUDOKU_PRESETS: SudokuPreset[] = [
  { key: 'easy', label: '易しい 45 マス', givens: 45 },
  { key: 'normal', label: '普通 34 マス', givens: 34 },
  { key: 'hard', label: '難しい 27 マス', givens: 27 }
]

/** 数独の一辺のマス数。 */
export const SUDOKU_SIZE = 9
/** マスの総数（9 × 9）。 */
export const SUDOKU_CELLS = SUDOKU_SIZE * SUDOKU_SIZE
/** 3×3 ブロックの一辺のマス数。 */
export const SUDOKU_BOX_SIZE = 3

/** 難易度に対応するプリセットを返す。 */
export function sudokuPreset(key: SudokuDifficulty): SudokuPreset {
  const found = SUDOKU_PRESETS.find((preset) => preset.key === key)
  if (!found) throw new Error(`unknown difficulty: ${key}`)
  return found
}

/** マスの行（0 始まり）。 */
export function rowIndexOf(index: number): number {
  return Math.floor(index / SUDOKU_SIZE)
}

/** マスの列（0 始まり）。 */
export function colIndexOf(index: number): number {
  return index % SUDOKU_SIZE
}

/** マスが属する 3×3 ブロックの番号（左上から右下へ 0〜8）。 */
export function boxIndexOf(index: number): number {
  return Math.floor(rowIndexOf(index) / SUDOKU_BOX_SIZE) * SUDOKU_BOX_SIZE
    + Math.floor(colIndexOf(index) / SUDOKU_BOX_SIZE)
}

/** 同じ行・同じ列・同じブロックにあるマス（自分自身は含まない。重複は許容する）。 */
function peerIndexes(index: number): number[] {
  const row = rowIndexOf(index)
  const col = colIndexOf(index)
  const boxRow = Math.floor(row / SUDOKU_BOX_SIZE) * SUDOKU_BOX_SIZE
  const boxCol = Math.floor(col / SUDOKU_BOX_SIZE) * SUDOKU_BOX_SIZE
  const peers: number[] = []
  for (let offset = 0; offset < SUDOKU_SIZE; offset += 1) {
    peers.push(row * SUDOKU_SIZE + offset) // 同じ行
    peers.push(offset * SUDOKU_SIZE + col) // 同じ列
    peers.push(
      (boxRow + Math.floor(offset / SUDOKU_BOX_SIZE)) * SUDOKU_SIZE + boxCol + (offset % SUDOKU_BOX_SIZE)
    ) // 同じ 3×3 ブロック
  }
  return peers.filter((peer) => peer !== index)
}

/** そのマスに value を置いても数独のルール（行・列・ブロックの重複なし）を破らないか。 */
function canPlace(grid: number[], index: number, value: number): boolean {
  return peerIndexes(index).every((peer) => grid[peer] !== value)
}

/** ビットマスク（1〜9 をビット 1〜9 で表す）に含まれる数字の数。 */
function countBits(mask: number): number {
  let count = 0
  let rest = mask
  while (rest !== 0) {
    rest &= rest - 1
    count += 1
  }
  return count
}

/**
 * 解の個数を数える（最大 limit 個で打ち切る）。
 * 解が一意かどうかの判定は `countSolutions(grid, 2) === 1` で行う。
 *
 * 空きマスのうち「置ける数字が最も少ないマス」から埋めていき（MRV）、
 * 行・列・ブロックで使った数字をビットマスクで持って枝を素早く打ち切る。
 */
export function countSolutions(grid: number[], limit = 2): number {
  const cap = Math.max(1, Math.floor(limit))
  const work = [...grid]
  // 最初から重複がある盤面は解なし（完成盤でも重複していれば 0 を返す）
  if (findConflicts(work).size > 0) return 0

  const allMask = 0b1111111110 // 1〜9 のビットを立てたマスク
  const rowUsed = new Array<number>(SUDOKU_SIZE).fill(0)
  const colUsed = new Array<number>(SUDOKU_SIZE).fill(0)
  const boxUsed = new Array<number>(SUDOKU_SIZE).fill(0)

  for (let index = 0; index < SUDOKU_CELLS; index += 1) {
    const value = work[index]
    if (value === 0) continue
    const bit = 1 << value
    rowUsed[rowIndexOf(index)] |= bit
    colUsed[colIndexOf(index)] |= bit
    boxUsed[boxIndexOf(index)] |= bit
  }

  let found = 0

  const search = (): void => {
    if (found >= cap) return

    let bestIndex = -1
    let bestMask = 0
    let bestCount = SUDOKU_SIZE + 1
    for (let index = 0; index < SUDOKU_CELLS; index += 1) {
      if (work[index] !== 0) continue
      const mask = allMask & ~(rowUsed[rowIndexOf(index)] | colUsed[colIndexOf(index)] | boxUsed[boxIndexOf(index)])
      const count = countBits(mask)
      if (count === 0) return // 置ける数字が無い＝この枝は解なし
      if (count < bestCount) {
        bestIndex = index
        bestMask = mask
        bestCount = count
        if (count === 1) break
      }
    }

    if (bestIndex === -1) {
      found += 1 // すべて埋まった
      return
    }

    const row = rowIndexOf(bestIndex)
    const col = colIndexOf(bestIndex)
    const box = boxIndexOf(bestIndex)
    for (let value = 1; value <= SUDOKU_SIZE; value += 1) {
      const bit = 1 << value
      if ((bestMask & bit) === 0) continue
      work[bestIndex] = value
      rowUsed[row] |= bit
      colUsed[col] |= bit
      boxUsed[box] |= bit

      search()

      rowUsed[row] &= ~bit
      colUsed[col] &= ~bit
      boxUsed[box] &= ~bit
      work[bestIndex] = 0
      if (found >= cap) return
    }
  }

  search()
  return found
}

/**
 * 行・列・3×3 ブロックで重複しているマスの位置を返す。
 * 空きマス（0）は対象外。重複している両方のマスを返す。
 */
export function findConflicts(grid: number[]): Set<number> {
  const conflicts = new Set<number>()
  for (let index = 0; index < grid.length; index += 1) {
    const value = grid[index]
    if (value === 0) continue
    for (const peer of peerIndexes(index)) {
      if (grid[peer] === value) {
        conflicts.add(index)
        conflicts.add(peer)
      }
    }
  }
  return conflicts
}

/**
 * そのマスに入れられる数字の一覧（昇順）。
 * 既に数字が入っているマスは空配列を返す。
 */
export function candidatesFor(grid: number[], index: number): number[] {
  if (grid[index] !== 0) return []
  const candidates: number[] = []
  for (let value = 1; value <= SUDOKU_SIZE; value += 1) {
    if (canPlace(grid, index, value)) candidates.push(value)
  }
  return candidates
}

/** すべてのマスが埋まっていて、重複もないか。 */
export function isComplete(grid: number[]): boolean {
  if (grid.length !== SUDOKU_CELLS) return false
  if (grid.some((value) => value === 0)) return false
  return findConflicts(grid).size === 0
}

/** 盤面が解答と一致しているか（完成判定に使う）。 */
export function isSolved(grid: number[], solution: number[]): boolean {
  if (grid.length !== solution.length) return false
  return grid.every((value, index) => value === solution[index])
}

/** 配列を rng でシャッフルした新しい配列を返す（Fisher-Yates）。 */
function shuffled<T>(items: T[], rng: Rng): T[] {
  const result = [...items]
  for (let i = result.length - 1; i > 0; i -= 1) {
    const pick = Math.floor(rng() * (i + 1))
    const swap = result[i]
    result[i] = result[pick]
    result[pick] = swap
  }
  return result
}

/** 候補をランダムな順番に試して完成盤を 1 つ作る（バックトラック）。 */
function fillGrid(grid: number[], rng: Rng): boolean {
  const index = grid.indexOf(0)
  if (index === -1) return true
  const values: number[] = []
  for (let value = 1; value <= SUDOKU_SIZE; value += 1) values.push(value)

  for (const value of shuffled(values, rng)) {
    if (!canPlace(grid, index, value)) continue
    grid[index] = value
    if (fillGrid(grid, rng)) return true
    grid[index] = 0
  }
  return false
}

/** 完成盤を 1 つ作る。 */
function buildSolution(rng: Rng): number[] {
  const grid = new Array<number>(SUDOKU_CELLS).fill(0)
  fillGrid(grid, rng)
  return grid
}

/**
 * 完成盤からマスを取り除いて問題を作る。
 * 「取り除いても解が一意（countSolutions が 1）」のときだけ取り除き、givens 個まで削る。
 * 一意性を保てるマスが尽きた場合は、目標より多い初期数字のまま返す。
 */
function digHoles(solution: number[], givens: number, rng: Rng): number[] {
  const puzzle = [...solution]
  const order: number[] = []
  for (let index = 0; index < SUDOKU_CELLS; index += 1) order.push(index)

  let remaining = SUDOKU_CELLS
  for (const index of shuffled(order, rng)) {
    if (remaining <= givens) break
    const value = puzzle[index]
    if (value === 0) continue
    puzzle[index] = 0
    if (countSolutions(puzzle, 2) === 1) remaining -= 1
    else puzzle[index] = value
  }
  return puzzle
}

export interface SudokuPuzzle {
  /** 問題（0 が空きマス）。 */
  puzzle: number[]
  /** 解答（完成盤）。 */
  solution: number[]
}

/**
 * 難易度に応じた問題と解答を作る。
 * 目標の初期数字数まで削れないことがあるため、数回まで作り直して一番少ないものを採用する。
 */
export function generateSudoku(difficulty: SudokuDifficulty, rng: Rng = Math.random): SudokuPuzzle {
  const preset = sudokuPreset(difficulty)
  let best: SudokuPuzzle | null = null
  let bestGivens = SUDOKU_CELLS + 1

  for (let attempt = 0; attempt < 3; attempt += 1) {
    const solution = buildSolution(rng)
    const puzzle = digHoles(solution, preset.givens, rng)
    const givens = puzzle.filter((value) => value !== 0).length
    if (givens < bestGivens) {
      bestGivens = givens
      best = { puzzle, solution }
    }
    if (bestGivens === preset.givens) break
  }

  if (best === null) throw new Error('数独の問題を生成できませんでした')
  return best
}

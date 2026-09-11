/**
 * マインスイーパーのロジック（UI から独立。テストは tests/games/minesweeper.spec.ts）。
 *
 * ・盤面オブジェクトは「破壊的に」更新する（Vue の ref に入れて使う前提）。
 * ・地雷の配置は最初のクリックまで遅らせる（初手で絶対に爆発しないため）。
 * ・乱数は差し替え可能（テストで固定値を使う）。
 */

/** 0 以上 1 未満の乱数を返す関数。 */
export type Rng = () => number

export type MineDifficulty = 'beginner' | 'intermediate' | 'expert'

export interface MinePreset {
  key: MineDifficulty
  label: string
  rows: number
  cols: number
  mines: number
}

/** 難易度プリセット（初級 / 中級 / 上級）。 */
export const MINE_PRESETS: MinePreset[] = [
  { key: 'beginner', label: '初級 9×9・10', rows: 9, cols: 9, mines: 10 },
  { key: 'intermediate', label: '中級 16×16・40', rows: 16, cols: 16, mines: 40 },
  { key: 'expert', label: '上級 16×30・99', rows: 16, cols: 30, mines: 99 }
]

export function minePreset(key: MineDifficulty): MinePreset {
  const found = MINE_PRESETS.find((preset) => preset.key === key)
  if (!found) throw new Error(`unknown difficulty: ${key}`)
  return found
}

export interface MineCell {
  /** 地雷か（配置前はすべて false）。 */
  mine: boolean
  /** 開いたか。 */
  open: boolean
  /** 旗が立っているか。 */
  flag: boolean
  /** 隣接する地雷の数（配置後に確定）。 */
  adjacent: number
  /** 失敗時に「旗の付け間違い」として表示するか。 */
  wrong: boolean
}

export interface MineBoard {
  rows: number
  cols: number
  mines: number
  cells: MineCell[]
  /** 地雷を配置済みか。 */
  armed: boolean
  /** 爆発したセルの位置（未爆発は null）。 */
  exploded: number | null
  /** 開いた（地雷以外の）セル数。 */
  opened: number
}

export type MineStatus = 'ready' | 'playing' | 'won' | 'lost'

/** 空の盤面を作る（地雷はまだ置かない）。 */
export function createBoard(preset: MinePreset): MineBoard {
  const cells: MineCell[] = Array.from({ length: preset.rows * preset.cols }, () => ({
    mine: false,
    open: false,
    flag: false,
    adjacent: 0,
    wrong: false
  }))
  return {
    rows: preset.rows,
    cols: preset.cols,
    mines: preset.mines,
    cells,
    armed: false,
    exploded: null,
    opened: 0
  }
}

export function indexOf(board: MineBoard, row: number, col: number): number {
  return row * board.cols + col
}

export function rowOf(board: MineBoard, index: number): number {
  return Math.floor(index / board.cols)
}

export function colOf(board: MineBoard, index: number): number {
  return index % board.cols
}

/** 周囲 8 マスの位置（盤外は含めない）。 */
export function neighborIndexes(board: MineBoard, index: number): number[] {
  const row = rowOf(board, index)
  const col = colOf(board, index)
  const result: number[] = []
  for (let dr = -1; dr <= 1; dr += 1) {
    for (let dc = -1; dc <= 1; dc += 1) {
      if (dr === 0 && dc === 0) continue
      const nextRow = row + dr
      const nextCol = col + dc
      if (nextRow < 0 || nextRow >= board.rows || nextCol < 0 || nextCol >= board.cols) continue
      result.push(indexOf(board, nextRow, nextCol))
    }
  }
  return result
}

/**
 * 初手の位置（とその周囲 8 マス）を避けて地雷を置き、隣接数を数える。
 * 盤面が狭くて避けきれない場合は、初手の位置だけを避ける。
 */
export function armBoard(board: MineBoard, safeIndex: number, rng: Rng = Math.random): void {
  const safe = new Set<number>([safeIndex, ...neighborIndexes(board, safeIndex)])
  const forbidden = safe.size + board.mines <= board.cells.length ? safe : new Set<number>([safeIndex])

  const candidates: number[] = []
  for (let index = 0; index < board.cells.length; index += 1) {
    if (!forbidden.has(index)) candidates.push(index)
  }
  if (candidates.length < board.mines) throw new Error('地雷の数が盤面に対して多すぎます')

  // 部分 Fisher-Yates で必要数だけ選ぶ（偏りなく選べる）
  for (let i = 0; i < board.mines; i += 1) {
    const pick = i + Math.floor(rng() * (candidates.length - i))
    const swap = candidates[i]
    candidates[i] = candidates[pick]
    candidates[pick] = swap
    board.cells[candidates[i]].mine = true
  }

  for (const cell of board.cells) cell.adjacent = 0
  for (let index = 0; index < board.cells.length; index += 1) {
    if (board.cells[index].mine) continue
    board.cells[index].adjacent = neighborIndexes(board, index).filter((n) => board.cells[n].mine).length
  }
  board.armed = true
}

export interface OpenResult {
  /** 地雷を踏んだか。 */
  exploded: boolean
  /** 今回開いたセルの位置。 */
  opened: number[]
}

/**
 * セルを開く。0 のマスは周囲へ連鎖して開く（旗の立ったマスは自動で開かない）。
 * 初回は地雷を配置してから開く（初手安全）。旗のマス・開済みのマスは何もしない。
 */
export function openCell(board: MineBoard, index: number, rng: Rng = Math.random): OpenResult {
  const cell = board.cells[index]
  if (!cell || cell.open || cell.flag) return { exploded: false, opened: [] }
  if (!board.armed) armBoard(board, index, rng)

  if (cell.mine) {
    cell.open = true
    board.exploded = index
    return { exploded: true, opened: [index] }
  }

  const opened: number[] = []
  const stack: number[] = [index]
  while (stack.length > 0) {
    const current = stack.pop() as number
    const target = board.cells[current]
    if (target.open || target.flag || target.mine) continue
    target.open = true
    opened.push(current)
    board.opened += 1
    if (target.adjacent === 0) {
      for (const neighbor of neighborIndexes(board, current)) {
        if (!board.cells[neighbor].open && !board.cells[neighbor].flag) stack.push(neighbor)
      }
    }
  }
  return { exploded: false, opened }
}

/** 旗を立てる／外す。開済みのマスは対象外。 */
export function toggleFlag(board: MineBoard, index: number): boolean {
  const cell = board.cells[index]
  if (!cell || cell.open) return false
  cell.flag = !cell.flag
  return cell.flag
}

export interface ChordResult {
  /** 周囲をまとめて開く条件がそろっていたか。 */
  triggered: boolean
  /** 今回開いたマスの位置。 */
  opened: number[]
  /** 旗の付け間違いで地雷を踏んだか。 */
  exploded: boolean
}

/**
 * 左右のボタンを同時に押したときの動作（チョード / 2.0 の onChordOpen と同じ）。
 *
 * ・対象は「開いていて、地雷ではなく、数字が 1 以上」のマスだけ。
 * ・周囲 8 マスの旗の数がその数字と一致していれば、旗のない未開封のマスをまとめて開く。
 * ・一致しないときは何もしない。旗の位置が間違っていれば地雷を踏んで失敗になる。
 */
export function chordCell(board: MineBoard, index: number): ChordResult {
  const idle: ChordResult = { triggered: false, opened: [], exploded: false }
  const center = board.cells[index]
  if (!board.armed || !center || !center.open || center.mine || center.adjacent <= 0) return idle

  const around = neighborIndexes(board, index)
  const flags = around.filter((neighbor) => board.cells[neighbor].flag).length
  if (flags !== center.adjacent) return idle

  const opened: number[] = []
  let exploded = false
  for (const neighbor of around) {
    if (exploded) break
    const target = board.cells[neighbor]
    if (target.open || target.flag) continue
    const result = openCell(board, neighbor)
    opened.push(...result.opened)
    if (result.exploded) exploded = true
  }
  return { triggered: true, opened, exploded }
}

export function countFlags(board: MineBoard): number {
  return board.cells.filter((cell) => cell.flag).length
}

/** 残りの地雷数の目安（地雷数 − 旗の数）。 */
export function remainingMines(board: MineBoard): number {
  return board.mines - countFlags(board)
}

/** 地雷以外のマスがすべて開いたか。 */
export function isCleared(board: MineBoard): boolean {
  return board.opened >= board.rows * board.cols - board.mines
}

export function boardStatus(board: MineBoard): MineStatus {
  if (board.exploded !== null) return 'lost'
  if (isCleared(board)) return 'won'
  return board.opened === 0 ? 'ready' : 'playing'
}

/** 失敗時にすべての地雷を開き、付け間違いの旗に印を付ける。 */
export function revealMines(board: MineBoard): void {
  for (const cell of board.cells) {
    if (cell.mine) cell.open = true
    else if (cell.flag) cell.wrong = true
  }
}

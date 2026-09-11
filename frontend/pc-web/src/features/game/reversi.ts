/**
 * リバーシ（黒白棋 / オセロ）のロジック（UI から独立。テストは tests/games/reversi.spec.ts）。
 *
 * ・盤面は 1 次元配列で表す（index = 行 * size + 列）。要素は石の色、または空きマスなら null。
 * ・石を置く関数は盤面を「破壊的に」更新する（Vue の ref に入れて使う前提）。
 * ・乱数は差し替え可能（テストで固定値を使う）。
 */

/** 0 以上 1 未満の乱数を返す関数。 */
export type Rng = () => number

/** 石の色。 */
export type Disc = 'black' | 'white'

/** 標準の盤面サイズ（8×8）。 */
export const REVERSI_SIZE = 8

/** 8 方向（上下左右と斜め 4 方向）の増分。 */
const DIRECTIONS: readonly (readonly [number, number])[] = [
  [-1, -1], [-1, 0], [-1, 1],
  [0, -1], [0, 1],
  [1, -1], [1, 0], [1, 1]
]

/** 角を取る手の評価点（他の要素より常に優先させる）。 */
const CORNER_BONUS = 100
/** X 打ち（角の斜め隣）の減点。相手に角を渡しやすい。 */
const X_SQUARE_PENALTY = 12
/** C 打ち（角の辺隣）の減点。 */
const C_SQUARE_PENALTY = 8

/** 盤面サイズの検証（2×2 未満では中央 4 マスの初期配置が作れない）。 */
function assertSize(size: number): void {
  if (!Number.isInteger(size) || size < 2) {
    throw new Error(`盤面のサイズが不正です: ${size}`)
  }
}

/**
 * 中央 4 マスに白黒 2 個ずつ置いた初期盤面を作る。
 *
 * 8×8 の場合（白が左上と右下、黒が右上と左下）:
 *   3行4列=白 3行5列=黒
 *   4行4列=黒 4行5列=白
 */
export function createReversi(size: number = REVERSI_SIZE): (Disc | null)[] {
  assertSize(size)
  const cells: (Disc | null)[] = Array.from({ length: size * size }, () => null)
  const top = Math.floor(size / 2) - 1
  const left = Math.floor(size / 2) - 1
  cells[top * size + left] = 'white'
  cells[top * size + left + 1] = 'black'
  cells[(top + 1) * size + left] = 'black'
  cells[(top + 1) * size + left + 1] = 'white'
  return cells
}

/** 反対の色を返す。 */
export function otherDisc(disc: Disc): Disc {
  return disc === 'black' ? 'white' : 'black'
}

/**
 * ある方向に挟める相手の石を調べる。
 * 「相手の石が 1 個以上続き、その先が自分の石」のときだけ、その間の位置を返す。
 */
function flipsInDirection(
  cells: (Disc | null)[],
  size: number,
  row: number,
  col: number,
  disc: Disc,
  dr: number,
  dc: number
): number[] {
  const opponent = otherDisc(disc)
  const flips: number[] = []
  let r = row + dr
  let c = col + dc
  while (r >= 0 && r < size && c >= 0 && c < size && cells[r * size + c] === opponent) {
    flips.push(r * size + c)
    r += dr
    c += dc
  }
  if (flips.length === 0) return []
  // 相手の石を挟んだ先が盤外、または自分の石でなければ裏返せない
  if (r < 0 || r >= size || c < 0 || c >= size) return []
  return cells[r * size + c] === disc ? flips : []
}

/**
 * index に disc を置いたときに裏返る位置を返す（8 方向すべてを調べる）。
 * 空きマスでない場合・どこも挟めない場合は空配列（＝そこには置けない）。
 */
export function flipsFor(cells: (Disc | null)[], size: number, index: number, disc: Disc): number[] {
  if (!Number.isInteger(index) || index < 0 || index >= size * size) return []
  const current = cells[index]
  if (current !== null && current !== undefined) return []
  const row = Math.floor(index / size)
  const col = index % size
  const flips: number[] = []
  for (const [dr, dc] of DIRECTIONS) {
    const found = flipsInDirection(cells, size, row, col, disc, dr, dc)
    for (const position of found) flips.push(position)
  }
  return flips
}

/** disc が置けるマスの位置（昇順）。 */
export function legalMoves(cells: (Disc | null)[], size: number, disc: Disc): number[] {
  const moves: number[] = []
  for (let index = 0; index < size * size; index += 1) {
    const current = cells[index]
    if (current !== null && current !== undefined) continue
    if (flipsFor(cells, size, index, disc).length > 0) moves.push(index)
  }
  return moves
}

/**
 * disc を index に置き、挟んだ相手の石を裏返す（盤面を破壊的に更新）。
 * 置けない手の場合は盤面を変更せず空配列を返す。返り値は裏返した位置。
 */
export function applyMove(cells: (Disc | null)[], size: number, index: number, disc: Disc): number[] {
  const flips = flipsFor(cells, size, index, disc)
  if (flips.length === 0) return []
  cells[index] = disc
  for (const position of flips) cells[position] = disc
  return flips
}

/** 黒と白の石数を数える（空きマスは数えない）。 */
export function countDiscs(cells: (Disc | null)[]): { black: number; white: number } {
  let black = 0
  let white = 0
  for (const disc of cells) {
    if (disc === 'black') black += 1
    else if (disc === 'white') white += 1
  }
  return { black, white }
}

/** 両者とも打てる手が無い（＝対局終了）か。 */
export function isGameOver(cells: (Disc | null)[], size: number): boolean {
  const blackCanMove = legalMoves(cells, size, 'black').length > 0
  if (blackCanMove) return false
  return legalMoves(cells, size, 'white').length === 0
}

/** 角の位置か。 */
function isCorner(row: number, col: number, size: number): boolean {
  return (row === 0 || row === size - 1) && (col === 0 || col === size - 1)
}

/** 角の隣（X 打ち・C 打ち）の減点。角から離れたマスは 0。 */
function cornerPenalty(row: number, col: number, size: number): number {
  let penalty = 0
  for (const cornerRow of [0, size - 1]) {
    for (const cornerCol of [0, size - 1]) {
      const rowGap = Math.abs(row - cornerRow)
      const colGap = Math.abs(col - cornerCol)
      if (rowGap === 1 && colGap === 1) {
        // 角の斜め隣（X 打ち）
        penalty = Math.max(penalty, X_SQUARE_PENALTY)
      } else if ((rowGap === 0 && colGap === 1) || (rowGap === 1 && colGap === 0)) {
        // 角の辺隣（C 打ち）
        penalty = Math.max(penalty, C_SQUARE_PENALTY)
      }
    }
  }
  return penalty
}

/**
 * CPU の手を選ぶ（素朴な評価）。
 * ・角が打てるなら最優先。
 * ・次に角の隣（X 打ち・C 打ち）を避ける。
 * ・同点なら裏返せる数が多い手を選び、それも同じなら乱数で選ぶ。
 * 打てる手が無ければ -1。
 */
export function chooseCpuMove(
  cells: (Disc | null)[],
  size: number,
  disc: Disc,
  rng: Rng = Math.random
): number {
  const moves = legalMoves(cells, size, disc)
  if (moves.length === 0) return -1

  let bestScore = Number.NEGATIVE_INFINITY
  let best: number[] = []
  for (const index of moves) {
    const flips = flipsFor(cells, size, index, disc)
    const row = Math.floor(index / size)
    const col = index % size
    const score = isCorner(row, col, size)
      ? CORNER_BONUS + flips.length
      : flips.length - cornerPenalty(row, col, size)
    if (score > bestScore) {
      bestScore = score
      best = [index]
    } else if (score === bestScore) {
      best.push(index)
    }
  }

  if (best.length === 1) return best[0]
  // 同点の手から 1 つ選ぶ（rng が 1 を返しても範囲内に収める）
  const picked = Math.min(best.length - 1, Math.floor(rng() * best.length))
  return best[picked]
}

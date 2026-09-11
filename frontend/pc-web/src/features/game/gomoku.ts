/**
 * 五目並べ（Gomoku）のロジック（UI から独立。テストは tests/games/gomoku.spec.ts）。
 *
 * ・盤面は 1 次元配列で表す（index = row * size + col）。
 * ・`cells` は「破壊的に」更新する（Vue の ref に入れて使う前提）。
 * ・CPU は素朴なヒューリスティック（自分の連続を伸ばす／相手の連続を止める）。
 * ・乱数は差し替え可能（同点の手のばらけをテストで固定する）。
 */

/** 0 以上 1 未満の乱数を返す関数。 */
export type Rng = () => number

/** 石の色。'black' が先手。 */
export type Stone = 'black' | 'white'

/** 標準の盤面サイズ（15 路盤）。 */
export const GOMOKU_SIZE = 15

/** 勝利に必要な連続数。 */
export const WIN_LENGTH = 5

/** 4 方向（横 → 縦 → ↘ → ↗）の増分。勝利判定と評価で共用する。 */
const DIRECTIONS: ReadonlyArray<readonly [number, number]> = [
  [0, 1],
  [1, 0],
  [1, 1],
  [1, -1]
]

/** 空の盤面を作る（すべて null）。 */
export function createGomoku(size: number = GOMOKU_SIZE): (Stone | null)[] {
  return Array.from({ length: size * size }, () => null)
}

/** 相手の石の色を返す。 */
export function otherStone(stone: Stone): Stone {
  return stone === 'black' ? 'white' : 'black'
}

/** (row, col) が盤面の中か。 */
export function isInside(size: number, row: number, col: number): boolean {
  return row >= 0 && row < size && col >= 0 && col < size
}

/** そのマスが空きか（盤外・配列外は空きではない）。 */
function isEmpty(cell: Stone | null | undefined): boolean {
  return cell === null || cell === undefined
}

/**
 * index のマスに石を置く（破壊的更新）。
 * 盤外・既に石があるマスには置けず false を返す。
 */
export function placeStone(cells: (Stone | null)[], size: number, index: number, stone: Stone): boolean {
  if (!Number.isInteger(index) || index < 0 || index >= size * size) return false
  if (!isEmpty(cells[index])) return false
  cells[index] = stone
  return true
}

/**
 * index を含む (dr, dc) 方向の、同じ石が連続しているマスの位置（昇順）。
 * index 自身が stone でなければ空配列。
 */
function collectLine(
  cells: (Stone | null)[],
  size: number,
  index: number,
  stone: Stone,
  dr: number,
  dc: number
): number[] {
  if (cells[index] !== stone) return []
  const row = Math.floor(index / size)
  const col = index % size

  // 反対方向へさかのぼる（見つかった順に先頭へ積むと昇順になる）
  const back: number[] = []
  for (let step = 1; ; step += 1) {
    const nextRow = row - dr * step
    const nextCol = col - dc * step
    if (!isInside(size, nextRow, nextCol)) break
    if (cells[nextRow * size + nextCol] !== stone) break
    back.unshift(nextRow * size + nextCol)
  }

  // 進む方向へたどる
  const forward: number[] = []
  for (let step = 1; ; step += 1) {
    const nextRow = row + dr * step
    const nextCol = col + dc * step
    if (!isInside(size, nextRow, nextCol)) break
    if (cells[nextRow * size + nextCol] !== stone) break
    forward.push(nextRow * size + nextCol)
  }

  return [...back, index, ...forward]
}

/**
 * index に stone を置いた結果できる「勝利の一線」を返す。
 * 縦・横・斜め（↘・↗）の 4 方向を見て、WIN_LENGTH 以上連続していれば
 * その連続マスの位置（5 連以上はすべて）を返す。勝っていなければ空配列。
 */
export function findWinningLine(
  cells: (Stone | null)[],
  size: number,
  index: number,
  stone: Stone
): number[] {
  if (index < 0 || index >= size * size) return []
  for (const [dr, dc] of DIRECTIONS) {
    const line = collectLine(cells, size, index, stone, dr, dc)
    if (line.length >= WIN_LENGTH) return line
  }
  return []
}

/** 盤面がすべて埋まっているか（引き分け判定）。 */
export function isBoardFull(cells: (Stone | null)[]): boolean {
  return cells.every((cell) => !isEmpty(cell))
}

/** 連続の長さと両端の空き具合から、その形の強さを点数化する。 */
function runScore(count: number, openEnds: number): number {
  if (count >= WIN_LENGTH) return 10_000_000 // 5 連完成（勝ち）。最優先。
  if (count === 4) return openEnds > 0 ? 50_000 : 5_000
  if (count === 3) return openEnds === 2 ? 3_000 : openEnds === 1 ? 300 : 30
  if (count === 2) return openEnds === 2 ? 200 : openEnds === 1 ? 20 : 2
  return openEnds > 0 ? 2 : 1
}

/**
 * 空きマス index に stone を置いたときの「一手の価値」。
 * 4 方向それぞれについて、できる連続の長さと両端の空きで点数を出し、
 * いちばん良い方向の点数を返す（石があるマス・盤外は 0）。
 */
export function lineScore(cells: (Stone | null)[], size: number, index: number, stone: Stone): number {
  if (!Number.isInteger(index) || index < 0 || index >= size * size) return 0
  if (!isEmpty(cells[index])) return 0

  const row = Math.floor(index / size)
  const col = index % size
  let best = 0

  for (const [dr, dc] of DIRECTIONS) {
    let count = 1 // これから置く石自身
    let openEnds = 0

    for (const sign of [1, -1]) {
      for (let step = 1; ; step += 1) {
        const nextRow = row + dr * step * sign
        const nextCol = col + dc * step * sign
        if (!isInside(size, nextRow, nextCol)) break
        const value = cells[nextRow * size + nextCol] ?? null
        if (value === stone) {
          count += 1
          continue
        }
        if (value === null) openEnds += 1
        break
      }
    }

    best = Math.max(best, runScore(count, openEnds))
  }

  return best
}

/** 中央に近いほど高くなる小さな加点（同点の手を中央寄りに寄せる）。 */
function centerBonus(size: number, index: number): number {
  const center = (size - 1) / 2
  const row = Math.floor(index / size)
  const col = index % size
  return size - (Math.abs(row - center) + Math.abs(col - center))
}

/**
 * CPU の着手を選ぶ。
 *
 * ・自分の連続を伸ばす点（攻め）と、相手の連続を止める点（守り）を足し、
 *   守りは少し高めに評価する（相手のリーチを放置しない）。
 * ・中央寄りをわずかに加点する。
 * ・同点は rng でばらけさせる（テストでは rng を固定する）。
 * ・必ず空いているマスを返し、空きが無ければ -1 を返す。
 */
export function chooseCpuMove(
  cells: (Stone | null)[],
  size: number,
  stone: Stone,
  rng: Rng = Math.random
): number {
  const opponent = otherStone(stone)
  let bestScore = Number.NEGATIVE_INFINITY
  let best: number[] = []

  for (let index = 0; index < size * size; index += 1) {
    if (!isEmpty(cells[index])) continue
    const attack = lineScore(cells, size, index, stone)
    const block = lineScore(cells, size, index, opponent)
    const score = attack + block * 1.1 + centerBonus(size, index)
    if (score > bestScore) {
      bestScore = score
      best = [index]
    } else if (score === bestScore) {
      best.push(index)
    }
  }

  if (best.length === 0) return -1
  if (best.length === 1) return best[0]
  // 同点の手から 1 つ選ぶ（rng() が 1 を返しても範囲外にならないようにする）
  const pick = Math.min(best.length - 1, Math.floor(rng() * best.length))
  return best[pick]
}

/**
 * ノノグラム（お絵かきロジック）のロジック（UI から独立。テストは tests/games/nonogram.spec.ts）。
 *
 * ・「解答（solution）」と「プレイヤーの操作（states）」を分けて扱う。
 *   盤面は boolean[] / NonoState[] の 1 次元配列（index = row * size + col）。
 * ・問題の生成は乱数を差し替えられるようにして、テストを再現可能にする（Rng）。
 * ・DB も API も使わない（記録は保存しない）。
 */

/** 0 以上 1 未満の乱数を返す関数。 */
export type Rng = () => number

/** セルの状態: 未操作 / 塗る / ×印 */
export type NonoState = 'empty' | 'filled' | 'crossed'

/** 手がかり（行・列のヒント数字）の状態。 */
export type LineProgress = 'pending' | 'satisfied' | 'over'

export interface NonogramPuzzle {
  /** 1 辺のマス数（盤面は size × size）。 */
  size: number
  /** 解答（長さ size*size、true = 塗る）。 */
  solution: boolean[]
  /** 各行のヒント数字（空行は [0]）。 */
  rowClues: number[][]
  /** 各列のヒント数字（空列は [0]）。 */
  colClues: number[][]
  /** 問題名（例: 'ハート'）。 */
  label: string
}

export interface NonogramPreset {
  key: string
  label: string
  size: number
}

/** サイズのプリセット（入門 / 標準 / 挑戦）。 */
export const NONOGRAM_PRESETS: NonogramPreset[] = [
  { key: 'easy', label: '入門 5×5', size: 5 },
  { key: 'standard', label: '標準 10×10', size: 10 },
  { key: 'challenge', label: '挑戦 15×15', size: 15 }
]

/**
 * 5×5 の固定絵柄（'#' = 塗る、'.' = 空）。
 * 入門サイズは小さな絵が描けるように、ランダムではなくこの絵柄から出題する。
 */
export const NONOGRAM_PICTURES: { key: string; label: string; pattern: string[] }[] = [
  {
    key: 'heart',
    label: 'ハート',
    pattern: [
      '##.##',
      '#####',
      '#####',
      '.###.',
      '..#..'
    ]
  },
  {
    key: 'face',
    label: 'かお',
    pattern: [
      '.###.',
      '#.#.#',
      '#...#',
      '#.#.#',
      '.###.'
    ]
  },
  {
    key: 'tree',
    label: 'き',
    pattern: [
      '..#..',
      '.###.',
      '#####',
      '..#..',
      '..#..'
    ]
  },
  {
    key: 'plus',
    label: 'プラス',
    pattern: [
      '..#..',
      '..#..',
      '#####',
      '..#..',
      '..#..'
    ]
  }
]

/**
 * 1 本の線（行または列）のヒント数字を作る。
 * 連続する true の長さを並べ、全部 false の線は [0] を返す。
 *
 *   [true, true, false, true] → [2, 1]
 *   [false, false, false]     → [0]
 */
export function cluesFor(line: boolean[]): number[] {
  const clue: number[] = []
  let run = 0
  for (const filled of line) {
    if (filled) {
      run += 1
      continue
    }
    if (run > 0) clue.push(run)
    run = 0
  }
  if (run > 0) clue.push(run)
  return clue.length > 0 ? clue : [0]
}

/** 0 以上 size 未満の整数を 1 つ選ぶ（乱数が 1 を返しても範囲内に収める）。 */
function pickIndex(size: number, rng: Rng): number {
  return Math.min(size - 1, Math.max(0, Math.floor(rng() * size)))
}

/** 解答から問題一式（ヒント数字つき）を組み立てる。 */
function buildPuzzle(size: number, solution: boolean[], label: string): NonogramPuzzle {
  const rowClues: number[][] = []
  const colClues: number[][] = []

  for (let row = 0; row < size; row += 1) {
    rowClues.push(cluesFor(solution.slice(row * size, (row + 1) * size)))
  }

  for (let col = 0; col < size; col += 1) {
    const line: boolean[] = []
    for (let row = 0; row < size; row += 1) line.push(solution[row * size + col])
    colClues.push(cluesFor(line))
  }

  return { size, solution, rowClues, colClues, label }
}

/** 行に 1 マスでも塗られているか。 */
function rowHasFill(size: number, solution: boolean[], row: number): boolean {
  for (let col = 0; col < size; col += 1) {
    if (solution[row * size + col]) return true
  }
  return false
}

/** 列に 1 マスでも塗られているか。 */
function colHasFill(size: number, solution: boolean[], col: number): boolean {
  for (let row = 0; row < size; row += 1) {
    if (solution[row * size + col]) return true
  }
  return false
}

/**
 * ランダムな問題を作る（密度は 0.5〜0.6 程度）。
 * ヒントが [0] だけの行・列が出ると解きにくいので、空になった行・列には 1 マス塗り足す。
 */
export function createRandomPuzzle(size: number, rng: Rng = Math.random): NonogramPuzzle {
  if (!Number.isInteger(size) || size < 1) throw new Error(`盤面のサイズが不正です: ${size}`)

  const solution: boolean[] = Array.from({ length: size * size }, () => false)
  const density = 0.5 + rng() * 0.1

  for (let index = 0; index < solution.length; index += 1) {
    solution[index] = rng() < density
  }

  // 空の行を 1 マス塗ってなくす（行 → 列の順に処理すれば、後から行が空になることはない）
  for (let row = 0; row < size; row += 1) {
    if (rowHasFill(size, solution, row)) continue
    solution[row * size + pickIndex(size, rng)] = true
  }
  for (let col = 0; col < size; col += 1) {
    if (colHasFill(size, solution, col)) continue
    solution[pickIndex(size, rng) * size + col] = true
  }

  return buildPuzzle(size, solution, `ランダム ${size}×${size}`)
}

/**
 * 絵柄（'#' = 塗る、'.' = 空）から問題を作る。正方形でない・使えない文字がある場合は例外。
 */
export function puzzleFromPicture(label: string, pattern: string[]): NonogramPuzzle {
  const size = pattern.length
  if (size === 0) throw new Error('絵柄が空です')

  const solution: boolean[] = []
  pattern.forEach((line, row) => {
    if (line.length !== size) {
      throw new Error(`絵柄の ${row + 1} 行目の長さが ${size} ではありません: ${line}`)
    }
    for (const char of line) {
      if (char === '#') solution.push(true)
      else if (char === '.') solution.push(false)
      else throw new Error(`絵柄に使えない文字です: ${char}`)
    }
  })

  return buildPuzzle(size, solution, label)
}

/**
 * プリセットから問題を作る。
 * 5×5 の入門サイズは固定の絵柄から 1 つ選び、それ以外はランダムに生成する。
 */
export function createPuzzle(presetKey: string, rng: Rng = Math.random): NonogramPuzzle {
  const preset = NONOGRAM_PRESETS.find((item) => item.key === presetKey)
  if (!preset) throw new Error(`unknown preset: ${presetKey}`)

  if (preset.size <= 5 && NONOGRAM_PICTURES.length > 0) {
    const picture = NONOGRAM_PICTURES[pickIndex(NONOGRAM_PICTURES.length, rng)]
    return puzzleFromPicture(picture.label, picture.pattern)
  }
  return createRandomPuzzle(preset.size, rng)
}

/** 塗ったマスが解答と完全に一致していれば完成（×印や塗り忘れは完成ではない）。 */
export function isSolved(states: NonoState[], solution: boolean[]): boolean {
  if (states.length !== solution.length) return false
  return solution.every((filled, index) => (states[index] === 'filled') === filled)
}

/**
 * 行・列 1 本の進み具合。
 * ・satisfied: ヒント数字どおりに塗れている
 * ・over: ヒントの合計より多く塗っている
 * ・pending: それ以外（まだ途中・並びが違う）
 */
export function lineProgress(clue: number[], line: NonoState[]): LineProgress {
  const filled = line.map((state) => state === 'filled')
  const expected = clue.length > 0 ? clue : [0]
  const target = expected.reduce((sum, count) => sum + count, 0)
  const painted = filled.filter(Boolean).length

  if (painted > target) return 'over'

  const current = cluesFor(filled)
  const matched = current.length === expected.length && current.every((count, index) => count === expected[index])
  return matched ? 'satisfied' : 'pending'
}

/** 塗ったが解答では空のマス（間違い）の位置。 */
export function mistakes(states: NonoState[], solution: boolean[]): number[] {
  const result: number[] = []
  states.forEach((state, index) => {
    if (state === 'filled' && !solution[index]) result.push(index)
  })
  return result
}

/**
 * ヒントで塗るマス（まだ塗られていない解答マス）を 1 つ返す。
 * すべて塗り終わっていれば -1。
 */
export function hintIndex(states: NonoState[], solution: boolean[], rng: Rng = Math.random): number {
  const candidates: number[] = []
  solution.forEach((filled, index) => {
    if (filled && states[index] !== 'filled') candidates.push(index)
  })
  if (candidates.length === 0) return -1
  return candidates[pickIndex(candidates.length, rng)]
}

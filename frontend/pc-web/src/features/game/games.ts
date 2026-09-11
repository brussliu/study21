/**
 * ゲーム機能のメタ情報（タブの並び順・表示名・説明文）。
 * 画面本体は views/game/*.vue、ロジックは features/game/*.ts にある。
 * すべてフロントエンドだけで動作し、記録は保存しない。
 */
export interface GameDefinition {
  /** URL クエリ `?game=<slug>` の値。 */
  slug: string
  label: string
  /** タブの下に出す短い説明。 */
  desc: string
  /** 人数・難易度などの補足。 */
  meta: string
  /** アイコン（icons.svg の `#i-*`）。 */
  icon: string
  /** 識別色（game.css の `.gm-accent--*`）。 */
  accent: string
}

export const GAMES: GameDefinition[] = [
  {
    slug: 'minesweeper',
    label: 'マインスイーパー',
    desc: '地雷を避けて数字を手がかりに安全なマスをすべて開きます。',
    meta: 'ひとり用 ・ 初級 / 中級 / 上級',
    icon: 'alert',
    accent: 'danger'
  },
  {
    slug: 'sudoku',
    label: '数独',
    desc: '1〜9 を 1 つずつ。行・列・3×3 ブロックの重複を避けて埋めます。',
    meta: 'ひとり用 ・ 易しい / 普通 / 難しい',
    icon: 'grid',
    accent: 'primary'
  },
  {
    slug: '2048',
    label: '2048',
    desc: '同じ数字をぶつけて合成し、2048 のタイルを目指します。',
    meta: 'ひとり用 ・ 4×4',
    icon: 'sigma',
    accent: 'warning'
  },
  {
    slug: 'gomoku',
    label: '五目並べ',
    desc: '黒白の石を交互に置き、先に 5 つ並べた方が勝ちです。',
    meta: '2人対戦 / CPU ・ 15×15',
    icon: 'circle',
    accent: 'info'
  },
  {
    slug: 'reversi',
    label: 'リバーシ',
    desc: '相手の石を挟んで裏返し、最後に石が多い方の勝ちです。',
    meta: '2人対戦 / CPU ・ 8×8',
    icon: 'check-circle',
    accent: 'success'
  },
  {
    slug: 'nonogram',
    label: 'ノノグラム',
    desc: '行と列のヒント数字を手がかりにマスを塗り、絵を完成させます。',
    meta: 'ひとり用 ・ 5×5 / 10×10 / 15×15',
    icon: 'image',
    accent: 'subject'
  }
]

export function gameBySlug(slug: string): GameDefinition | undefined {
  return GAMES.find((game) => game.slug === slug)
}

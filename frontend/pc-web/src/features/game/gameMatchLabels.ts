import type { GameMatchDetail, GameStatusCode } from '@/api/games'

/**
 * 対戦の表示名（DB はコード、画面は日本語）。
 * ゲーム名・状態名・結果の文言を 1 か所にまとめる。
 */
export const STATUS_LABELS: Record<GameStatusCode, string> = {
  WAITING: '招待中',
  PLAYING: '対戦中',
  FINISHED: '終了',
  DECLINED: '断られました',
  CANCELLED: '取消'
}

export const GAME_TYPE_LABELS = {
  GOMOKU: '五目並べ',
  REVERSI: 'リバーシ'
} as const

export function statusBadgeClass(status: GameStatusCode): string {
  switch (status) {
    case 'PLAYING':
      return 'badge--success'
    case 'WAITING':
      return 'badge--info'
    case 'FINISHED':
      return 'badge--neutral'
    default:
      return 'badge--danger'
  }
}

/**
 * 自分のアカウントIDを対局の情報から割り出す。
 * 指し手の石（myStone）と一致する手の実行者が自分。まだ 1 手も無いときは
 * 「挑戦者が持つ石（先手/後手は申し込むときに選ぶ）」と見比べて決める
 * （挑戦者が後手のこともあるため、黒＝挑戦者と決め打ちしない）。
 */
export function myAccountIdOf(match: GameMatchDetail): number | null {
  const own = match.moves.find((move) => move.stone === match.myStone)
  if (own !== undefined) return own.accountId
  return match.challengerStone === match.myStone ? match.challengerAccountId : match.opponentAccountId
}

/** 結果の文言（自分の立場から見た勝ち負け）。 */
export function resultLabel(match: GameMatchDetail): string {
  if (match.status !== 'FINISHED') return ''
  if (match.resultCode === 'DRAW' || match.winnerAccountId === null) return '引き分けです'
  const mine = myAccountIdOf(match)
  const win = match.winnerAccountId === mine
  if (match.resultCode === 'RESIGN') {
    return win ? '相手が投了しました（あなたの勝ち）' : '投了しました（あなたの負け）'
  }
  if (match.resultCode === 'TIMEOUT') {
    return win ? '相手が不在のため終了しました（あなたの勝ち）' : '相手が不在のため終了しました（あなたの負け）'
  }
  return win ? 'あなたの勝ちです！' : 'あなたの負けです'
}

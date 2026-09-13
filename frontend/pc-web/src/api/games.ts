import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * ゲーム対戦 API（/api/user/games）。五子棋・黑白棋で生徒と保護者が同時に対戦する。
 *
 * ・DB が唯一の正（招待は 状態=WAITING の対戦行）。リアルタイムの通知は SSE
 *   （`GET /api/user/games/stream`）で受けるが、通知を受けたら必ず GET で読み直す。
 * ・持ち時間の制限は無し（ユーザーの指定）。
 */
export type GameTypeCode = 'GOMOKU' | 'REVERSI'
export type GameStatusCode = 'WAITING' | 'PLAYING' | 'FINISHED' | 'DECLINED' | 'CANCELLED'
export type GameResultCode = 'WIN' | 'DRAW' | 'RESIGN' | 'TIMEOUT'
/** API の石は大文字（画面の `Stone` / `Disc` は小文字）。 */
export type ApiStone = 'BLACK' | 'WHITE'

export interface GameOpponent {
  accountId: number
  displayName: string
  role: string
  /** いまゲーム画面を開いているか（＝リアルタイム接続が生きているか）。 */
  online: boolean
}

export interface GameMatchSummary {
  matchId: number
  gameType: GameTypeCode
  status: GameStatusCode
  myTurn: boolean
  iAmChallenger: boolean
  opponentName: string
  myStone: ApiStone
  moveCount: number
  resultCode: GameResultCode | null
  winnerAccountId: number | null
  updatedAt: string
  version: number
}

export interface GameMoveRow {
  moveNumber: number
  accountId: number
  stone: ApiStone
  row: number | null
  col: number | null
  pass: boolean
  /** 黑白棋でこの手が返した石（[行, 列] の配列）。 */
  flipped: number[][]
  /** 五子棋で勝ちが成立した 5 連（[行, 列] の配列）。 */
  line: number[][]
}

export interface GameMatchDetail {
  matchId: number
  gameType: GameTypeCode
  status: GameStatusCode
  /** 2 次元配列。空きは null。 */
  board: (ApiStone | null)[][]
  myStone: ApiStone
  /** 挑戦者が持つ石（先手=BLACK / 後手=WHITE）。 */
  challengerStone: ApiStone
  turnAccountId: number | null
  myTurn: boolean
  moveCount: number
  winnerAccountId: number | null
  resultCode: GameResultCode | null
  version: number
  challengerAccountId: number
  opponentAccountId: number
  challengerName: string
  opponentName: string
  /** 見ている側から見た相手が、いまゲーム画面を開いているか。 */
  opponentOnline: boolean
  finishedAt: string | null
  moves: GameMoveRow[]
}

const http = new HttpClient({ baseUrl: '/api/user' })

/** 対戦できる相手（自分の子ども／親）。 */
export function fetchGameOpponents(): Promise<ApiResponse<{ items: GameOpponent[] }>> {
  return http.get<{ items: GameOpponent[] }>('/games/opponents')
}

/** 自分の対戦一覧（招待中・対戦中・終了）。 */
export function fetchGameMatches(): Promise<ApiResponse<{ items: GameMatchSummary[] }>> {
  return http.get<{ items: GameMatchSummary[] }>('/games/matches')
}

/**
 * 対戦を申し込む（招待＝状態 WAITING の対戦を作る）。
 * `challengerStone` は自分が持つ石＝先手（BLACK）か後手（WHITE）。
 * 相手がゲーム画面を開いていないときは 400（「相手がオンラインではありません」）。
 */
export function createGameMatch(
  gameType: GameTypeCode,
  opponentAccountId: number,
  challengerStone: ApiStone
): Promise<ApiResponse<GameMatchDetail>> {
  return http.post<GameMatchDetail>('/games/matches', {
    body: { gameType, opponentAccountId, challengerStone }
  })
}

/** 招待を受ける。 */
export function acceptGameMatch(matchId: number): Promise<ApiResponse<GameMatchDetail>> {
  return http.post<GameMatchDetail>(`/games/matches/${matchId}/accept`, { body: {} })
}

/** 招待を断る。 */
export function declineGameMatch(matchId: number): Promise<ApiResponse<GameMatchDetail>> {
  return http.post<GameMatchDetail>(`/games/matches/${matchId}/decline`, { body: {} })
}

/** 申し込んだ対戦を取り消す。 */
export function cancelGameMatch(matchId: number): Promise<ApiResponse<GameMatchDetail>> {
  return http.post<GameMatchDetail>(`/games/matches/${matchId}/cancel`, { body: {} })
}

/**
 * 相手が不在（ゲーム画面を閉じた）ときに対局を終了する。
 * 残った側の勝ち（結果コードは TIMEOUT）。相手がまだオンラインなら 400。
 */
export function timeoutGameMatch(matchId: number): Promise<ApiResponse<GameMatchDetail>> {
  return http.post<GameMatchDetail>(`/games/matches/${matchId}/timeout`, { body: {} })
}

/** 投了する（自分の負けで終了）。 */
export function resignGameMatch(matchId: number): Promise<ApiResponse<GameMatchDetail>> {
  return http.post<GameMatchDetail>(`/games/matches/${matchId}/resign`, { body: {} })
}

/** 1 局の盤面・手番・結果・指し手。 */
export function fetchGameMatch(matchId: number): Promise<ApiResponse<GameMatchDetail>> {
  return http.get<GameMatchDetail>(`/games/matches/${matchId}`)
}

/** 着手する（版を送る。版違いは 409）。 */
export function playGameMove(
  matchId: number,
  row: number,
  col: number,
  version: number
): Promise<ApiResponse<GameMatchDetail>> {
  return http.post<GameMatchDetail>(`/games/matches/${matchId}/moves`, { body: { row, col, version } })
}

/**
 * リアルタイム通知（SSE）の URL。
 * EventSource は Cookie を送るので、この画面はログインしていればそのまま繋がる。
 */
export function gameStreamUrl(): string {
  return '/api/user/games/stream'
}

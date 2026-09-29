/**
 * 単語情報管理の一覧にある「取得状態」列の表示。
 *
 * <p>一覧 API は `v_jpn_word_ai_state`（AI 生成履歴の最新 ＋ 一度でも成功したか）から、
 * 4 つの区画（A・B＝詳細／C＝読み問題／D＝文脈問題／E＝漢字問題）にまとめた状態を返す。
 * ここは**表示の言葉に直すだけ**の純関数（画面とテストの両方から使う）。</p>
 */

import type { JpnWord, JpnWordAiState } from '@/api/japanese'

/** 取得状態の区画（取得ボタンの key と同じ並び）。 */
export type JpnAiStateKey = 'AB' | 'C' | 'D' | 'E'

/** 状態コード → 一覧に出す一言。 */
const STATE_LABELS: Record<string, string> = {
  SUCCEEDED: '取得済',
  FAILED: '失敗',
  RUNNING: '取得中',
  QUEUED: '取得待ち',
  CANCELED: '中止'
}

/** まだ実行していないときの表示。 */
export const AI_STATE_NONE_LABEL = '未取得'

/** 状態コードを一覧の一言にする（知らないコードはそのまま出す）。 */
export function aiStateLabelOf(state: string | null | undefined): string {
  if (state === null || state === undefined || state === '') {
    return AI_STATE_NONE_LABEL
  }
  return STATE_LABELS[state] ?? state
}

/** その区画の状態を取り出す（API が返していなければ null）。 */
export function aiStateOf(row: JpnWord, key: JpnAiStateKey): string | null {
  const state: JpnWordAiState | undefined = row.aiState
  if (!state) {
    return null
  }
  switch (key) {
    case 'AB':
      return state.detail ?? null
    case 'C':
      return state.reading ?? null
    case 'D':
      return state.context ?? null
    case 'E':
      return state.kanji ?? null
    default:
      return null
  }
}

/** 取得できているか（列の見た目を変えるときに使う）。 */
export function isAiStateDone(state: string | null | undefined): boolean {
  return state === 'SUCCEEDED'
}

/** 失敗しているか。 */
export function isAiStateFailed(state: string | null | undefined): boolean {
  return state === 'FAILED'
}

/**
 * 一覧の「取得状態」タグの色（設計システムの `.badge` の修飾クラス）。
 *
 * <p>「取得済」と「未取得」を色で見分けられるようにする（利用者の指示）。
 * 処理中・待ちは黄色、中止は未取得と同じグレーにする。</p>
 */
export function aiStateBadgeClass(state: string | null | undefined): string {
  switch (state) {
    case 'SUCCEEDED':
      return 'badge--success'
    case 'FAILED':
      return 'badge--danger'
    case 'RUNNING':
    case 'QUEUED':
      return 'badge--warning'
    default:
      // 未取得・中止・知らないコードはグレー（言葉は aiStateLabelOf がそのまま出す）
      return 'badge--neutral'
  }
}

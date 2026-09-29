/**
 * 取得ボタンを押したときの**対象の決め方**（純関数）。
 *
 * <p>利用者の指示（2026-09-26）: 4 つの取得ボタンは押した瞬間に走らせず、**窓を出して**
 * 「取得済みをスキップ」か「すべて再取得」かを選ばせる（参照は 2.0 の英語学習の
 * 単語情報管理 `word.jsp` の「取得方法」の窓）。AI を呼ぶ前に「何語が対象か」を見せたい。</p>
 *
 * <p><b>2026-09-27 以降の使い分け</b>: 一覧の右上【AI 取得】は対象が**検索条件に一致する語全体**
 * （ページを問わない）なので、選定は**サーバー**（`GET /words/ai-targets`）が行う。
 * ここは**行の【AI 取得】（その 1 語だけ）**の判定に使う（1 語なら問い合わせる必要が無い）。</p>
 *
 * <p>状態は一覧 API が返す `aiState`（`v_jpn_word_ai_state` の 4 区画）をそのまま使う。
 * つまり**画面の「取得状態」列と同じ判断**になる（列に「取得済」と出ている語はスキップされる）。</p>
 */

import type { JpnWord } from '@/api/japanese'
import { aiStateOf, isAiStateDone, type JpnAiStateKey } from './aiStateLabel'

/** 取得に出す語と、窓に出す件数。 */
export interface JpnAcquirePlan {
  /** 実際に AI へ渡す語（上限まで。表示順のまま）。 */
  wordIds: number[]
  /** いま出ている語の数。 */
  total: number
  /** そのうち取得済みの数（窓に出す。「取得中」は数えない）。 */
  acquired: number
  /** 今回 AI へ渡す数。 */
  targets: number
  /** 取得済み・取得中で今回渡さない数（すべて再取得のときは 0）。 */
  skipped: number
  /** 1 回の上限を超えて今回渡せない数（次回に回す）。 */
  overLimit: number
  /** 計算に使った 1 回の上限（窓が「1 回の受付は N 語まで」と出す）。 */
  limit: number
}

/**
 * AI を呼び直す必要があるか。
 *
 * <p>一度でも成功した内容（{@code SUCCEEDED}）と、いま走っている内容
 * （{@code RUNNING} / {@code QUEUED}）は呼び直さない。未取得（状態なし）と失敗は呼ぶ。</p>
 */
export function needsAiFetch(state: string | null | undefined): boolean {
  if (isAiStateDone(state)) {
    return false
  }
  return state !== 'RUNNING' && state !== 'QUEUED'
}

/** その区画で AI を呼ぶ必要があるか（行がないときは呼ぶ）。 */
function needsFetch(row: JpnWord, key: JpnAiStateKey): boolean {
  return needsAiFetch(aiStateOf(row, key))
}

/**
 * 対象を決める。
 *
 * <p>「取得済みをスキップ」は**先に外してから上限で切る**（外した分だけ多く取れる）。
 * 「すべて再取得」は表示中の語をそのまま上限まで渡す。</p>
 *
 * @param rows          いま出ている語（表示順）
 * @param key           取得ボタンの区画（A・B／C／D／E）
 * @param skipAcquired  取得済み・取得中を外すか
 * @param max           1 回の上限（**設定ページの「1 回の最大単語数」**。API から取る）
 */
export function acquirePlan(
  rows: readonly JpnWord[],
  key: JpnAiStateKey,
  skipAcquired: boolean,
  max: number
): JpnAcquirePlan {
  const candidates = skipAcquired ? rows.filter((row) => needsFetch(row, key)) : [...rows]
  const limit = Math.max(0, Math.floor(max))
  const targets = candidates.slice(0, limit)

  return {
    wordIds: targets.map((row) => row.wordId),
    total: rows.length,
    acquired: rows.filter((row) => isAiStateDone(aiStateOf(row, key))).length,
    targets: targets.length,
    skipped: rows.length - candidates.length,
    overLimit: Math.max(0, candidates.length - limit),
    limit
  }
}

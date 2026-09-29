/**
 * AI 取得の**種類**（右上の取得ボタン 1 つと、その窓が選ばせる 4 つの情報）。
 *
 * <p>画面（単語情報管理）と窓（`JpnAiFetchDialog.vue`）が同じ一覧を使う。以前は画面に
 * 4 つのボタンを並べていたが、場所を取るので**1 つのボタン ＋ 窓の中の選択**にまとめた
 * （利用者の指示 2026-09-26）。並びと名前はここが唯一の定義。</p>
 */

import type { JpnAiKind } from '@/api/japanese'
import type { JpnAiStateKey } from './aiStateLabel'

/** 取得できる情報 1 つぶん。 */
export interface JpnAcquireAction {
  /** 一覧の取得状態と同じ区画（A・B／C／D／E）。 */
  key: JpnAiStateKey
  /** API に渡す取得区分。 */
  kind: JpnAiKind
  /**
   * この取得を実行するバッチのコード。
   *
   * <p>**サーバーの {@code JapaneseWordAiLimits.BATCH_OF_KIND} と同じ組み合わせ**にする
   * （変えるときは両方そろえる）。画面は批次一覧（`GET /api/admin/batch/tasks`）の状態を
   * このコードで引いて、バッチの状態（有効・実行中・最後の結果）を出す。</p>
   */
  batchCode: string
  /** 一覧のタグに出す短い名前。 */
  short: string
  /** 窓に出す名前（何を取るかが分かるように詳しく）。 */
  label: string
  /** アイコン（設計システムの SVG スプライト）。 */
  icon: string
}

/**
 * 取得できる情報（この順に並べる）。
 *
 * <p>順番は 2.0 の英語学習の単語情報管理（`word.jsp`）の取得ボタンと同じ考え方で、
 * **詳細（A・B）→ C → D → E**（A・B が土台になるので先頭）。</p>
 */
export const JPN_ACQUIRE_ACTIONS: readonly JpnAcquireAction[] = [
  { key: 'AB', kind: 'DETAIL', batchCode: 'batC41', short: 'A・B', label: '詳細情報（A・B）', icon: 'info' },
  { key: 'C', kind: 'C', batchCode: 'batC42', short: 'C', label: '読み問題（C）', icon: 'book-open' },
  { key: 'D', kind: 'D', batchCode: 'batC43', short: 'D', label: '文脈問題（D）', icon: 'note' },
  { key: 'E', kind: 'E', batchCode: 'batC44', short: 'E', label: '漢字問題（E）', icon: 'pen' }
]

/** 区画から取得の種類を引く（窓と一覧のタグが同じ定義を使う）。 */
export function acquireActionOf(key: JpnAiStateKey): JpnAcquireAction {
  const action = JPN_ACQUIRE_ACTIONS.find((item) => item.key === key)
  if (!action) {
    throw new Error(`知らない取得の区画です: ${key}`)
  }
  return action
}

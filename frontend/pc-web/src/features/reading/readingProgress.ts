import { type ReadingBook } from '@/api/reading'

/** まだ読んでいない本のステータス（DB の既定値）。 */
const STATUS_NOT_STARTED = '未着手'

/** 進捗として出す本の項目（カード・読書状況・表紙パネルで同じ規則を使う）。 */
export type ReadingProgressSource =
  Pick<ReadingBook, 'status' | 'readPercent' | 'currentPage' | 'totalPages'>

/**
 * 画面に出す進捗（％）。
 *
 * **ステータスが「未着手」の本は 0%** として出す（【標記クリア】で進捗を戻したあと、
 * まだ読んでいない本が 現在ページ 1 / 総ページ の割合（例: 2 頁の本で 50%）と出ないように。
 * 現在ページは 1 のままにする＝DDL の `CHECK ("現在ページ" >= 1)` に合わせる）。
 * それ以外は API の `readPercent` を使い、0 のときは 現在ページ / 総ページ から計算する。
 */
export function displayReadPercent(item: ReadingProgressSource): number {
  if (String(item.status ?? '') === STATUS_NOT_STARTED) return 0
  const percent = Number(item.readPercent ?? 0)
  if (Number.isFinite(percent) && percent > 0) return Math.round(clamp(percent, 0, 100))
  const total = Number(item.totalPages ?? 0)
  const current = Number(item.currentPage ?? 0)
  if (total > 0 && current > 0) return Math.round(clamp((current / total) * 100, 0, 100))
  return 0
}

function clamp(value: number, min: number, max: number): number {
  return Math.max(min, Math.min(max, value))
}

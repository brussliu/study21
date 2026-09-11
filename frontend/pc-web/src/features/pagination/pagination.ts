/**
 * 一覧のページング表示ロジック（全画面共通）。
 *
 * 以前は「1 ページ目・最終ページ・現在±1」だけを出していたため、
 * 7 ページあるときに「1 2 7」のように途中が飛んで見えていた。
 * ここでは現在ページの前後と先頭・末尾を必ず出し、離れている部分は省略記号にする。
 */

/** ページ番号ボタン、または省略記号。 */
export type PaginationItem = number | 'gap'

/**
 * 省略記号つきのページ番号リストを作る（siblings = 現在ページの左右に何件出すか）。
 *
 * 例（siblings = 1）:
 *   全 7 ページ / 1 ページ目   → [1, 2, 3, 4, 5, 6, 7]
 *   全 20 ページ / 1 ページ目  → [1, 2, 3, 4, 5, 'gap', 20]
 *   全 20 ページ / 10 ページ目 → [1, 'gap', 9, 10, 11, 'gap', 20]
 *   全 20 ページ / 20 ページ目 → [1, 'gap', 16, 17, 18, 19, 20]
 */
export function paginationItems(current: number, total: number, siblings = 1): PaginationItem[] {
  const totalPages = Math.max(1, Math.floor(total) || 1)
  const active = Math.min(Math.max(1, Math.floor(current) || 1), totalPages)
  // 端に寄っているときに連続表示するページ数（1 ページ目なら 1..edgeWindow）。
  const edgeWindow = siblings * 2 + 3
  if (totalPages <= edgeWindow + 2) return range(1, totalPages)

  let start: number
  let end: number
  if (active <= siblings + 2) {
    start = 2
    end = Math.min(totalPages - 1, edgeWindow)
  } else if (active >= totalPages - (siblings + 1)) {
    start = Math.max(2, totalPages - edgeWindow + 1)
    end = totalPages - 1
  } else {
    start = active - siblings
    end = active + siblings
  }

  const items: PaginationItem[] = [1]
  if (start > 2) items.push('gap')
  for (let page = start; page <= end; page += 1) items.push(page)
  if (end < totalPages - 1) items.push('gap')
  items.push(totalPages)
  return items
}

function range(from: number, to: number): number[] {
  return Array.from({ length: Math.max(0, to - from + 1) }, (_, index) => from + index)
}

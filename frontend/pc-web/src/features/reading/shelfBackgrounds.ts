import shelfBg1 from '@/assets/reading/shelf-bg-1.jpg'
import shelfBg2 from '@/assets/reading/shelf-bg-2.jpg'
import shelfBg3 from '@/assets/reading/shelf-bg-3.jpg'
import shelfBg4 from '@/assets/reading/shelf-bg-4.jpg'
import shelfBg5 from '@/assets/reading/shelf-bg-5.jpg'
import shelfBg6 from '@/assets/reading/shelf-bg-6.jpg'

/**
 * 「自分の本棚」の棚の背景（実写の写真）。
 *
 * 並び順がそのまま背景の番号（`data-rd-shelf-bg`）。0 番（shelf-bg-1.jpg）が既定。
 * 画面の「棚の背景」で切り替えられ、選んだ番号は端末の localStorage に覚える
 * （**サーバには保存しない**。端末ごとの見た目の好み）。
 *
 * 写真ごとに「明るい棚板の位置」と「両端の縦の側板の厚み」が違うので、
 * 本を座らせる高さ（--rds-shelf-seat）と本の左右の余白（--rds-book-inset）も
 * 背景ごとに `reading-shelf.css` の `.rds-bg-N` で決めている。
 * それぞれの実測値はその CSS のコメントにある（`tmp/tools/import-shelf-bg.mjs` で測る）。
 */
export const SHELF_BACKGROUNDS: readonly string[] = [
  shelfBg1, shelfBg2, shelfBg3, shelfBg4, shelfBg5, shelfBg6
]

/** 背景の数（既定を含む。`data-rd-shelf-bg` の選択肢の数）。 */
export const SHELF_BG_COUNT = SHELF_BACKGROUNDS.length

/** 選んだ背景を覚えておくキー（端末ごと。サーバには保存しない）。 */
export const SHELF_BG_STORAGE_KEY = 'study21.reading.shelfBg'

/** 背景の番号を、使える範囲に丸める（壊れた値・知らない値は既定の 0 にする）。 */
export function normalizeShelfBg(value: unknown): number {
  const index = typeof value === 'number' ? Math.trunc(value) : Number.parseInt(String(value ?? ''), 10)
  return Number.isInteger(index) && index >= 0 && index < SHELF_BG_COUNT ? index : 0
}

/**
 * 背景の見出し（読み上げ・マウスの説明に使う）。
 * 0 番は既定の写真なので「既定」と書く（利用者に番号の意味が分かるように）。
 */
export function shelfBgLabel(index: number): string {
  const safe = normalizeShelfBg(index)
  return safe === 0 ? '棚の背景（既定）' : `棚の背景 ${safe}`
}

/** 背景のクラス（`.rds-bg-N`。本を座らせる位置を背景ごとに変える）。 */
export function shelfBgClass(index: number): string {
  return `rds-bg-${normalizeShelfBg(index)}`
}

/** 背景の画像の URL（棚の背景に敷く写真）。 */
export function shelfBgUrl(index: number): string {
  return SHELF_BACKGROUNDS[normalizeShelfBg(index)]
}

/** 前回選んだ背景を localStorage から読む（保存できない環境では既定）。 */
export function readShelfBg(): number {
  try {
    return normalizeShelfBg(window.localStorage.getItem(SHELF_BG_STORAGE_KEY))
  } catch {
    return 0
  }
}

/** 選んだ背景を localStorage に覚える（保存できない環境では何もしない）。 */
export function writeShelfBg(index: number): void {
  try {
    window.localStorage.setItem(SHELF_BG_STORAGE_KEY, String(normalizeShelfBg(index)))
  } catch {
    // 保存できない環境（プライベートモードなど）では見た目だけ変える
  }
}

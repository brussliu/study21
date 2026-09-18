import { STATUS_LABELS, type ReadingBook } from '@/api/reading'
import { displayReadPercent } from '@/features/reading/readingProgress'

/**
 * 本棚（自分の本棚）の背表紙の見た目を決める純粋な計算。
 *
 * 【書籍閲覧2】から【書籍閲覧】の「自分の本棚」タブへ移したときに、画面から切り離して
 * ここにまとめた（画面は `components/reading/ShelfCaseList.vue`）。
 * **乱数は使わない**: 書名と作者のハッシュから決めるので、同じ本はいつも同じ背表紙になる。
 */

/** 背表紙の色数（`.rds-spine.is-tone-*` の数）。色相を広く散らすため 16 色。 */
export const SPINE_TONES = 16

/** 背表紙の柄の数（`.rds-spine.is-pattern-*` の数）。 */
export const SPINE_PATTERNS = 3

/** 背表紙にいれる飾りの記号（表紙の意匠の代わりに 1 文字だけ。プロトタイプと同じ記号）。 */
export const SHELF_MARKS = ['✦', '◆', '◇', '♞', '§', '◎'] as const

/** 背表紙を傾ける段の数と 1 段の角度（度）。−0.6〜＋0.6 度のあいだで揺らす。 */
const SPINE_LEAN_STEPS = 7
const SPINE_LEAN_DEGREE = 0.2

/**
 * 背表紙の寸法の決め方（縦書きの書名が全部入ることを最優先にする）。
 * ・内側（文字が入る範囲）＝ 高さ − 上下の余白（CSS の `.rds-spine__title` の top/bottom）
 * ・書名に渡す高さ ＝ 100% − 4rem（作者と印のぶん。CSS 側はこれより広く取ってある）
 * ・縦書き 1 文字ぶんの送り ＝ --fs-md(14px) + letter-spacing(.06em) ≒ 15px
 * ・列（縦書きの行）1 本ぶんの幅 ＝ --fs-md(14px) × 行高(1.3) ≒ 19px
 * ・左右の余白 ＋ 枠線 ＝ --sp-3 × 2 + 2px = 26px（CSS の `.rds-spine__title` の max-width と同じ）
 * これで「書名が何列になるか」から必要な幅を計算できる（幅は 56〜140px）。
 */
const SPINE_PAD_INLINE = 28
const SPINE_TITLE_RESERVE = 64
const SPINE_CHAR_ADVANCE = 15
const SPINE_COLUMN_ADVANCE = 19
const SPINE_PAD_BLOCK = 26
const SPINE_MIN_WIDTH = 56
const SPINE_MAX_WIDTH = 140
const SPINE_MIN_HEIGHT = 216

/** 背表紙の高さの上限（棚の段の高さはこれを基準にする）。 */
export const SPINE_MAX_HEIGHT = 320

/** 書名の列（縦書きの行）の上限。これを超えそうなら本を高くして列を増やさない。 */
const SPINE_MAX_COLUMNS = 4

/** ホバー中の本を持ち上げる z-index（棚の中の最上位。ほかの本より必ず上）。 */
export const SHELF_HOVER_Z = 30

/** ツールチップ・表紙を右に出す本の数（左端の本がサイドメニューに隠れないように）。 */
const SHELF_TIP_RIGHT_COUNT = 2

/** 抜き出し演出の長さ（この時間だけ見せてから閲覧へ切り替える）。 */
export const PULL_MS = 420

/** 表紙を出してから本文に切り替わるまでの長さ。 */
export const OPENING_MS = 900

function clamp(value: number, min: number, max: number): number {
  return Math.min(Math.max(value, min), max)
}

/** 文字列から決まる数（同じ本はいつも同じ背表紙になる）。 */
export function seedOf(value: string): number {
  let seed = 7
  for (let index = 0; index < value.length; index += 1) {
    seed = (seed * 31 + value.charCodeAt(index)) % 1000003
  }
  return seed
}

/** 書名の文字数（見た目どおりに数える。絵文字なども 1 文字）。 */
export function titleLength(title: string): number {
  return Array.from(title.trim()).length
}

/**
 * 書名が縦書きで何列（＝行）になるか。
 * 列数 = ceil(文字数 ÷ 1 列に入る文字数)。1 列に入る文字数は
 * 「書名に渡す高さ」を「1 文字ぶんの送り」で割って求める（控えめに見積もる）。
 */
export function spineColumns(title: string, height: number): number {
  const space = Math.max(SPINE_CHAR_ADVANCE * 4, height - SPINE_PAD_INLINE - SPINE_TITLE_RESERVE)
  const perColumn = Math.max(4, Math.floor(space / SPINE_CHAR_ADVANCE))
  return Math.max(1, Math.ceil(titleLength(title) / perColumn))
}

/**
 * 背表紙の寸法と色みの揺らぎ（書名と作者から決まる。乱数は使わない）。
 * ・高さ … ハッシュで 216〜294px のあいだで揺らし、長い書名の本は列が増えないよう高くする
 * ・幅 … 書名の列数から計算する（`余白 + 列数 × 列の幅`）。56〜140px に収める
 *   ＝「01.Harry Potter and the Sorcerers Stone」のような長い書名でも切れない
 * ・`--rds-tone` … 色相の番号（0〜15）。CSS 側で 16 色に割り当てる
 * ・`--rds-light` … 明度の揺らぎ（0〜0.12）。CSS 側の filter に使う
 * ・`--rds-lean` … 傾き（度）。背表紙をわずかに傾けて「並べた本」らしく見せる
 */
export function spineStyle(item: ReadingBook): Record<string, string> {
  const seed = seedOf(`${item.title}|${item.author}`)
  const length = titleLength(item.title)
  // 長い書名は列が増えすぎないよう、必要な高さまで本を高くする（書名が入ることを優先）
  const needed = SPINE_PAD_INLINE + SPINE_TITLE_RESERVE
    + Math.ceil(length / SPINE_MAX_COLUMNS) * SPINE_CHAR_ADVANCE
  const height = clamp(Math.max(SPINE_MIN_HEIGHT + (seed % 4) * 26, needed), SPINE_MIN_HEIGHT, SPINE_MAX_HEIGHT)
  const columns = spineColumns(item.title, height)
  const width = clamp(SPINE_PAD_BLOCK + columns * SPINE_COLUMN_ADVANCE, SPINE_MIN_WIDTH, SPINE_MAX_WIDTH)
  return {
    '--rds-spine-h': `${height}px`,
    '--rds-spine-w': `${width}px`,
    '--rds-tone': String(seed % SPINE_TONES),
    '--rds-light': String(((Math.floor(seed / 3) % 5) * 0.03).toFixed(2)),
    '--rds-lean': `${leanOf(item)}deg`
  }
}

/**
 * 背表紙の傾き（度）。本ごとに −0.6〜＋0.6 度のあいだでわずかに変える
 * （まっすぐ並べるより、棚に立てた本らしく見える）。書名と作者のハッシュで決まる。
 */
export function leanOf(item: ReadingBook): number {
  const seed = seedOf(`${item.title}|${item.author}`)
  const step = Math.floor(seed / 13) % SPINE_LEAN_STEPS - (SPINE_LEAN_STEPS - 1) / 2
  return Math.round(step * SPINE_LEAN_DEGREE * 10) / 10
}

/**
 * 背表紙に入れる飾りの記号（1 文字）。表紙の絵が無い本でも「本らしく」見せるための
 * 飾りで、書名と作者のハッシュで決まる（同じ本はいつも同じ記号）。
 * ハッシュの別の桁を 2 つ足して混ぜる：同じ作者のシリーズのように書名が似た本が
 * 並んでも、同じ記号が続かないようにするため。
 */
export function markOf(item: ReadingBook): string {
  const seed = seedOf(`${item.title}|${item.author}`)
  return SHELF_MARKS[(Math.floor(seed / 11) + Math.floor(seed / 3)) % SHELF_MARKS.length]
}

/**
 * 背表紙の上に出す状態バッジ。いま読んでいる本（読書中）だけに出す
 * （プロトタイプの「読書中」バッジ。読んでいない本には出さない）。
 */
export function badgeOf(item: ReadingBook): string {
  return item.status === '読書中' ? STATUS_LABELS[item.status] : ''
}

/** 背表紙の書名が何列になるか（実機確認・テストで見られるように出す）。 */
export function spineLines(item: ReadingBook): number {
  return spineColumns(item.title, Number.parseFloat(spineStyle(item)['--rds-spine-h']))
}

/** 背表紙・表紙の色（`.is-tone-*`。書名のハッシュで色相を 16 色に散らす）。 */
export function toneClass(item: ReadingBook): string {
  return `is-tone-${seedOf(`${item.title}|${item.author}`) % SPINE_TONES}`
}

/** 背表紙の柄（`.is-pattern-*`。金のライン・縦縞・帯）。 */
export function patternClass(item: ReadingBook): string {
  return `is-pattern-${Math.floor(seedOf(item.title) / 5) % SPINE_PATTERNS}`
}

/** 棚の右端の本は、ホバーの表紙とツールチップを左側に出す（画面の外へはみ出さないように）。 */
export function previewOnLeft(index: number, total: number): boolean {
  return total > 4 && index >= total - 2
}

/**
 * 棚の左端の本は、ツールチップを右側に出す。
 * 中央寄せのままだと左のサイドメニュー（248px）の下に隠れてしまうため、位置で避ける
 * （サイドバーより上に z-index を上げるのは避ける＝レイアウトの前提を壊さない）。
 */
export function tipOnRight(index: number): boolean {
  return index < SHELF_TIP_RIGHT_COUNT
}

/** 表紙が無い本の代替表示に使う頭文字（日本語・英語のどちらでも 1 文字）。 */
export function initialOf(title: string): string {
  const trimmed = title.trim()
  return trimmed === '' ? '?' : Array.from(trimmed)[0]
}

/**
 * 進捗（%）。カード・読書状況と同じ規則（`displayReadPercent`）を使う。
 * `readPercent` が 0 のときは 現在ページ / 総ページ から計算し、
 * **ステータスが「未着手」の本は 0%** として出す。
 */
export function progressPercent(item: ReadingBook): number {
  return displayReadPercent(item)
}

/** 本文 PDF が読める状態か。 */
export function pdfReady(item: ReadingBook): boolean {
  return item.hasPdf && item.pdfAvailable
}

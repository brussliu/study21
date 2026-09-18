/**
 * AI 生図の切り抜き（crop）の計算。
 *
 * 切り抜き範囲は**元画像に対する割合（0〜1）**で持つ。
 * 表示するときは CSS の % にそのまま使えるので、画面幅が変わっても
 * 計算し直さなくてよい（実寸の px は元画像の大きさが分かったときだけ併記する）。
 *
 * ここは画面から切り離した純関数だけを置く（テストで境界の計算を固定する）。
 */

/** 切り抜き範囲（元画像に対する割合。x / y は左上、w / h は幅・高さ）。 */
export interface CropRect {
  x: number
  y: number
  w: number
  h: number
}

/** 切り抜きのハンドル（8 方向）。 */
export type CropHandle = 'nw' | 'n' | 'ne' | 'e' | 'se' | 's' | 'sw' | 'w'

/** ハンドルの並び（左上から時計回り）。 */
export const CROP_HANDLES: CropHandle[] = ['nw', 'n', 'ne', 'e', 'se', 's', 'sw', 'w']

/** 画像全体（既定の切り抜き）。 */
export const CROP_ALL: CropRect = { x: 0, y: 0, w: 1, h: 1 }

/** 切り抜きの最小の大きさ（元画像の 5%。これ以上は小さくしない）。 */
export const CROP_MIN = 0.05

/** 元画像の実寸（読み込めたときだけ分かる）。 */
export interface ImageSize {
  width: number
  height: number
}

function clamp(value: number, min: number, max: number): number {
  if (Number.isNaN(value)) return min
  return Math.min(max, Math.max(min, value))
}

/** 割合を 0〜1 に収め、最小の大きさを確保する（はみ出したら内側へ寄せる）。 */
export function clampCrop(rect: CropRect): CropRect {
  const w = clamp(rect.w, CROP_MIN, 1)
  const h = clamp(rect.h, CROP_MIN, 1)
  return {
    x: clamp(rect.x, 0, 1 - w),
    y: clamp(rect.y, 0, 1 - h),
    w,
    h
  }
}

/** 切り抜き枠を動かす（dx / dy は元画像に対する割合。大きさは変えない）。 */
export function moveCrop(rect: CropRect, dx: number, dy: number): CropRect {
  const base = clampCrop(rect)
  return {
    x: clamp(base.x + dx, 0, 1 - base.w),
    y: clamp(base.y + dy, 0, 1 - base.h),
    w: base.w,
    h: base.h
  }
}

/**
 * 切り抜き枠の辺・角をつまんで大きさを変える（dx / dy は元画像に対する割合）。
 * 反対側の辺は動かさない（つまんだ側だけを動かす）。
 */
export function resizeCrop(rect: CropRect, handle: CropHandle, dx: number, dy: number): CropRect {
  const base = clampCrop(rect)
  const right = base.x + base.w
  const bottom = base.y + base.h
  let { x, y, w, h } = base

  // 左辺（nw / w / sw）をつまんだら、右辺は固定して左辺を動かす
  if (handle === 'nw' || handle === 'w' || handle === 'sw') {
    x = clamp(base.x + dx, 0, right - CROP_MIN)
    w = right - x
  }
  // 右辺（ne / e / se）をつまんだら、左辺は固定して幅を変える
  if (handle === 'ne' || handle === 'e' || handle === 'se') {
    w = clamp(base.w + dx, CROP_MIN, 1 - base.x)
  }
  // 上辺（nw / n / ne）
  if (handle === 'nw' || handle === 'n' || handle === 'ne') {
    y = clamp(base.y + dy, 0, bottom - CROP_MIN)
    h = bottom - y
  }
  // 下辺（sw / s / se）
  if (handle === 'sw' || handle === 's' || handle === 'se') {
    h = clamp(base.h + dy, CROP_MIN, 1 - base.y)
  }
  return { x, y, w, h }
}

/** 切り抜き枠の位置と大きさ（CSS の % 指定にそのまま使える）。 */
export function cropStyle(rect: CropRect): Record<string, string> {
  const safe = clampCrop(rect)
  return {
    left: `${safe.x * 100}%`,
    top: `${safe.y * 100}%`,
    width: `${safe.w * 100}%`,
    height: `${safe.h * 100}%`
  }
}

/** 切り抜きの大きさを割合で表す（例: 横 90% ・ 縦 80%）。 */
export function cropPercentLabel(rect: CropRect): string {
  const safe = clampCrop(rect)
  return `横 ${Math.round(safe.w * 100)}% ・ 縦 ${Math.round(safe.h * 100)}%`
}

/** 切り抜きの実寸（元画像の大きさが分かっているときだけ）。 */
export function cropPixelSize(rect: CropRect, natural: ImageSize | null): ImageSize | null {
  if (natural === null || natural.width <= 0 || natural.height <= 0) return null
  const safe = clampCrop(rect)
  return {
    width: Math.max(1, Math.round(safe.w * natural.width)),
    height: Math.max(1, Math.round(safe.h * natural.height))
  }
}

/** 切り抜きの大きさの表示（実寸が分かれば px を併記する）。 */
export function cropSizeLabel(rect: CropRect, natural: ImageSize | null): string {
  const percent = cropPercentLabel(rect)
  const pixels = cropPixelSize(rect, natural)
  if (pixels === null) return percent
  return `横 ${pixels.width} × 縦 ${pixels.height} px（${percent}）`
}

/** 切り抜きの左上の位置（実寸が分かれば px を併記する）。 */
export function cropOriginLabel(rect: CropRect, natural: ImageSize | null): string {
  const safe = clampCrop(rect)
  if (natural === null || natural.width <= 0 || natural.height <= 0) {
    return `横 ${Math.round(safe.x * 100)}% ・ 縦 ${Math.round(safe.y * 100)}%`
  }
  return `横 ${Math.round(safe.x * natural.width)} × 縦 ${Math.round(safe.y * natural.height)} px から`
}

/** 画像全体を使っているか（切り抜きの操作をしていない状態）。 */
export function isFullCrop(rect: CropRect): boolean {
  const safe = clampCrop(rect)
  return safe.x === 0 && safe.y === 0 && safe.w === 1 && safe.h === 1
}

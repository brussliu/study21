/**
 * 画像トリミングの**座標の計算だけ**（純関数）。
 *
 * <p>不具合の再発防止のため切り出した: 以前は「選択範囲を元画像のどこから切り出すか（sx/sy）」を
 * 使わずに**常に全体**を書き出していた。Canvas は jsdom で動かないので、計算をここに置いて
 * テストで固定する。</p>
 *
 * <p>座標の流れ: 画面で選んだ範囲（**回転後の見た目**の px）→ 元画像を回転させたときの実寸 px。</p>
 */

/** 選択範囲（表示座標。`<img>`／表示枠の左上が原点）。 */
export interface CropSelection {
  x: number
  y: number
  width: number
  height: number
}

/** 表示の大きさ（枠＝回転後の見た目、inner＝`<img>` に指定する回転前の大きさ）。 */
export interface CropFit {
  frame: { width: number; height: number }
  inner: { width: number; height: number }
}

/** 切り出し範囲（元画像を回転させたときの実寸 px）。 */
export interface CropSourceRect {
  sx: number
  sy: number
  sw: number
  sh: number
}

function rotated(
  naturalWidth: number,
  naturalHeight: number,
  angle: number
): { width: number; height: number } {
  return angle === 90 || angle === 270
    ? { width: naturalHeight, height: naturalWidth }
    : { width: naturalWidth, height: naturalHeight }
}

/**
 * 枠に合わせた表示の大きさ（縦横比は変えない）。
 *
 * <p>回転しているときは、**枠は回転後の見た目**・**`<img>` は回転前**の大きさになる
 * （`transform: rotate()` は要素の箱を回すので、箱は回転前の大きさでなければ収まらない）。</p>
 */
export function fitCrop(
  naturalWidth: number,
  naturalHeight: number,
  angle: number,
  available: { width: number; height: number },
  padding = 12
): CropFit {
  const turn = rotated(naturalWidth, naturalHeight, angle)
  if (naturalWidth <= 0 || naturalHeight <= 0) {
    return { frame: { width: 1, height: 1 }, inner: { width: 1, height: 1 } }
  }
  const availableWidth = Math.max(1, available.width - padding)
  const availableHeight = Math.max(1, available.height - padding)
  const scale = Math.min(availableWidth / turn.width, availableHeight / turn.height)
  const inner = {
    width: Math.max(1, Math.floor(naturalWidth * scale)),
    height: Math.max(1, Math.floor(naturalHeight * scale))
  }
  return angle === 90 || angle === 270
    ? { frame: { width: inner.height, height: inner.width }, inner }
    : { frame: { width: inner.width, height: inner.height }, inner }
}

/** 選択範囲を、元画像（回転後）の実寸の切り出し範囲に変換する。 */
export function cropSourceRect(
  selection: CropSelection,
  frame: { width: number; height: number },
  naturalWidth: number,
  naturalHeight: number,
  angle: number
): CropSourceRect {
  const turn = rotated(naturalWidth, naturalHeight, angle)
  const scaleX = turn.width / Math.max(frame.width, 1)
  const scaleY = turn.height / Math.max(frame.height, 1)
  const sx = Math.min(turn.width - 1, Math.max(0, Math.round(selection.x * scaleX)))
  const sy = Math.min(turn.height - 1, Math.max(0, Math.round(selection.y * scaleY)))
  const sw = Math.max(1, Math.min(turn.width - sx, Math.round(selection.width * scaleX)))
  const sh = Math.max(1, Math.min(turn.height - sy, Math.round(selection.height * scaleY)))
  return { sx, sy, sw, sh }
}

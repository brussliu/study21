/**
 * トリミングの書き出し名（**純関数**）。
 *
 * <p>トリミング窓は**常に JPEG で書き出す**（`canvas.toDataURL('image/jpeg', 0.92)`）。
 * 元のファイル名が `.png` のままだと、実体と名前が食い違う。サーバーは MIME タイプを別の列に
 * 持つので配信は壊れないが、保存名と実体は合わせておく。</p>
 */

/** 元の名前（拡張子つき）。無いときは `image`。 */
const DEFAULT_NAME = 'image'

/**
 * JPEG として書き出した画像のファイル名。
 *
 * <p>`.jpg` / `.jpeg`（大文字小文字は問わない）はそのまま。それ以外の拡張子は `.jpg` に直し、
 * 拡張子が無ければ `.jpg` を足す。空の名前は `image.jpg` にする。</p>
 */
export function jpegFileName(fileName: string): string {
  const name = fileName.trim()
  if (name === '') {
    return `${DEFAULT_NAME}.jpg`
  }
  if (/\.jpe?g$/i.test(name)) {
    return name
  }
  const dot = name.lastIndexOf('.')
  // 拡張子が無い（`image`）・ドットで始まる（`.png`）ときは、名前に `.jpg` を足す
  return dot <= 0 ? `${name}.jpg` : `${name.slice(0, dot)}.jpg`
}

/**
 * AI 生図に送る画像の下ごしらえ（受け付ける形式と、ブラウザ側での変換）。
 *
 * サーバー（user-api / batC51）は JDK の `ImageIO` で切り抜き・縮小するが、
 * **JDK の ImageIO は WebP を読めない**。画面は WebP も選べるので、
 * アップロードの前にブラウザの canvas で PNG に変換する（利用者は形式を気にしなくてよい）。
 *
 * canvas が使えない環境（一部のテスト）では変換できないので、そのときは**そのまま送り**、
 * サーバーが返す日本語の理由（「PNG か JPEG を選んでください」）を画面に出す。
 * **黙って失敗させない**のがこの関数の約束。
 */

/** 画面が受け付ける画像（`accept` 属性とサーバーの検証に同じ値を使う）。 */
export const AI_IMAGE_ACCEPT = 'image/png,image/jpeg,image/webp'

/** 受け付ける MIME。 */
export const AI_IMAGE_MIME_TYPES = ['image/png', 'image/jpeg', 'image/webp']

/** 変換を待つ上限（ms）。画像が読み込めない環境で固まらないようにする。 */
export const CONVERT_TIMEOUT_MS = 3_000

/** そのまま送れる形式か（サーバーがデコードできる）。 */
export function isServerReadable(file: File): boolean {
  return file.type === 'image/png' || file.type === 'image/jpeg'
}

/** 変換が必要か（WebP だけ）。 */
export function needsConversion(file: File): boolean {
  return file.type === 'image/webp'
}

/** 受け付ける形式か。 */
export function isAcceptedImage(file: File): boolean {
  return AI_IMAGE_MIME_TYPES.includes(file.type)
}

/**
 * WebP を PNG に変換する（canvas が使えないときは null）。
 *
 * 変換したファイルの名前は元の名前の拡張子だけ `.png` に変える
 * （サーバーは拡張子と MIME の両方を見るため）。
 */
export async function convertToPng(file: File): Promise<File | null> {
  try {
    // 読み込みが返らない環境（画像を読み込まないテスト用 DOM など）では諦めて null を返す。
    // ここで固まると【AI に送る】が押せたまま何も起きない状態になる
    return await withTimeout(convert(file), CONVERT_TIMEOUT_MS)
  } catch {
    return null
  }
}

async function convert(file: File): Promise<File | null> {
  try {
    const dataUrl = await readAsDataUrl(file)
    const image = await loadImage(dataUrl)
    const canvas = document.createElement('canvas')
    canvas.width = image.naturalWidth
    canvas.height = image.naturalHeight
    if (canvas.width <= 0 || canvas.height <= 0) return null
    const context = canvas.getContext('2d')
    if (context === null) return null
    context.drawImage(image, 0, 0)
    const blob = await toBlob(canvas)
    if (blob === null) return null
    const name = file.name.replace(/\.[^.]*$/, '') + '.png'
    return new File([blob], name, { type: 'image/png' })
  } catch {
    return null
  }
}

function withTimeout<T>(promise: Promise<T>, timeoutMs: number): Promise<T | null> {
  return new Promise<T | null>((resolve) => {
    const timer = setTimeout(() => resolve(null), timeoutMs)
    promise
      .then((value) => resolve(value))
      .catch(() => resolve(null))
      .finally(() => clearTimeout(timer))
  })
}

/**
 * サーバーへ送るファイルを決める。
 *
 * ・PNG / JPEG はそのまま
 * ・WebP は PNG に変換してから送る（変換できないときはそのまま＝サーバーが理由を返す）
 */
export async function toUploadableImage(file: File): Promise<File> {
  if (!needsConversion(file)) return file
  const converted = await convertToPng(file)
  return converted ?? file
}

function readAsDataUrl(file: File): Promise<string> {
  return new Promise<string>((resolve, reject) => {
    const reader = new FileReader()
    reader.addEventListener('load', () => {
      const result = reader.result
      if (typeof result === 'string') resolve(result)
      else reject(new Error('empty'))
    })
    reader.addEventListener('error', () => reject(new Error('read error')))
    reader.readAsDataURL(file)
  })
}

function loadImage(src: string): Promise<HTMLImageElement> {
  return new Promise<HTMLImageElement>((resolve, reject) => {
    const image = new Image()
    image.addEventListener('load', () => resolve(image))
    image.addEventListener('error', () => reject(new Error('image error')))
    image.src = src
  })
}

function toBlob(canvas: HTMLCanvasElement): Promise<Blob | null> {
  return new Promise<Blob | null>((resolve) => {
    if (typeof canvas.toBlob !== 'function') {
      resolve(null)
      return
    }
    canvas.toBlob((blob) => resolve(blob), 'image/png')
  })
}

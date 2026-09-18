import { describe, expect, it, vi } from 'vitest'
import {
  AI_IMAGE_ACCEPT,
  AI_IMAGE_MIME_TYPES,
  CONVERT_TIMEOUT_MS,
  convertToPng,
  isAcceptedImage,
  isServerReadable,
  needsConversion,
  toUploadableImage
} from '@/features/geometry/geometry-ai-image'

/**
 * AI 生図に送る画像の下ごしらえ。
 *
 * サーバー（user-api / batC51）は JDK の `ImageIO` で切り抜き・縮小するが、**WebP を読めない**。
 * そのため画面側で WebP → PNG に変換してから送る（利用者は形式を気にしなくてよい）。
 * 変換できない環境（canvas が無い）では**そのまま送り**、サーバーが返す日本語の理由を画面に出す。
 * **黙って失敗させない**ことをここで固定する。
 */
function file(name: string, type: string): File {
  return new File([new Uint8Array(64)], name, { type })
}

describe('AI 生図に送る画像', () => {
  it('PNG・JPEG・WebP を受け付ける（画面の accept と同じ値）', () => {
    expect(AI_IMAGE_ACCEPT).toBe('image/png,image/jpeg,image/webp')
    expect(AI_IMAGE_MIME_TYPES).toEqual(['image/png', 'image/jpeg', 'image/webp'])
    expect(isAcceptedImage(file('a.png', 'image/png'))).toBe(true)
    expect(isAcceptedImage(file('a.jpg', 'image/jpeg'))).toBe(true)
    expect(isAcceptedImage(file('a.webp', 'image/webp'))).toBe(true)
    expect(isAcceptedImage(file('a.gif', 'image/gif'))).toBe(false)
    expect(isAcceptedImage(file('a.txt', 'text/plain'))).toBe(false)
  })

  it('サーバーがそのまま読めるのは PNG と JPEG だけ（WebP は変換が必要）', () => {
    expect(isServerReadable(file('a.png', 'image/png'))).toBe(true)
    expect(isServerReadable(file('a.jpg', 'image/jpeg'))).toBe(true)
    expect(isServerReadable(file('a.webp', 'image/webp'))).toBe(false)
    expect(needsConversion(file('a.webp', 'image/webp'))).toBe(true)
    expect(needsConversion(file('a.png', 'image/png'))).toBe(false)
  })

  it('PNG はそのまま送る（変換しない）', async () => {
    const source = file('figure.png', 'image/png')

    expect(await toUploadableImage(source)).toBe(source)
  })

  it('canvas が無い環境では変換せずにそのまま返す（黙って失敗させない）', async () => {
    const source = file('figure.webp', 'image/webp')
    // jsdom には canvas の実装が無い（画像が読み込まれない）ので、変換はタイムアウトで諦める
    vi.useFakeTimers()
    try {
      const converted = convertToPng(source)
      await vi.advanceTimersByTimeAsync(CONVERT_TIMEOUT_MS + 100)
      expect(await converted).toBeNull()

      // 変換できなくても送る（サーバーが「PNG か JPEG を選んでください」と日本語で返す）
      const uploading = toUploadableImage(source)
      await vi.advanceTimersByTimeAsync(CONVERT_TIMEOUT_MS + 100)
      expect(await uploading).toBe(source)
    } finally {
      vi.useRealTimers()
    }
  })
})

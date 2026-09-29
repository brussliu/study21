import { describe, expect, it } from 'vitest'
import { cropSourceRect, fitCrop } from '@/features/english-essay/cropGeometry'
import { jpegFileName } from '@/features/english-essay/cropFileName'

/**
 * 画像トリミングの座標計算（画面が使う純関数）。
 *
 * <p>直した不具合を固定する: 以前は選択範囲（sx/sy）を使わず**常に画像全体**を書き出していた。
 * ここでは「左上を選んだら切り出しの原点が 0 になる」ことを確かめる。</p>
 */
describe('画像トリミングの座標', () => {
  const NATURAL = { width: 600, height: 800 }

  it('回転していなければ、枠と <img> の大きさは同じ（縦横比を保つ）', () => {
    // 内側の余白を 0 にして、収め方の算術だけを見る
    const fit = fitCrop(NATURAL.width, NATURAL.height, 0, { width: 300, height: 300 }, 0)

    // 300x300 の枠に 600x800 を収める → 高さ基準で 225x300
    expect(fit.frame).toEqual({ width: 225, height: 300 })
    expect(fit.inner).toEqual({ width: 225, height: 300 })
  })

  it('90 度回転では枠の縦横が入れ替わり、<img> は回転前のまま', () => {
    const fit = fitCrop(NATURAL.width, NATURAL.height, 90, { width: 400, height: 400 }, 0)

    // 回転後の実寸は 800x600 → 枠は 400x300、<img> は回転前の 300x400
    expect(fit.frame).toEqual({ width: 400, height: 300 })
    expect(fit.inner).toEqual({ width: 300, height: 400 })
  })

  it('左上を選ぶと、切り出しの原点は 0 になる（中心固定をやめた）', () => {
    const fit = fitCrop(NATURAL.width, NATURAL.height, 0, { width: 300, height: 400 })
    const rect = cropSourceRect(
      { x: 0, y: 0, width: fit.frame.width / 2, height: fit.frame.height / 2 },
      fit.frame,
      NATURAL.width,
      NATURAL.height,
      0
    )

    expect(rect.sx).toBe(0)
    expect(rect.sy).toBe(0)
    expect(rect.sw).toBe(NATURAL.width / 2)
    expect(rect.sh).toBe(NATURAL.height / 2)
  })

  it('右下を選ぶと、切り出しは右端・下端に寄る', () => {
    const fit = fitCrop(NATURAL.width, NATURAL.height, 0, { width: 300, height: 400 })
    const half = { width: fit.frame.width / 2, height: fit.frame.height / 2 }
    const rect = cropSourceRect(
      { x: half.width, y: half.height, width: half.width, height: half.height },
      fit.frame,
      NATURAL.width,
      NATURAL.height,
      0
    )

    expect(rect.sx).toBe(300)
    expect(rect.sy).toBe(400)
    expect(rect.sw).toBe(300)
    expect(rect.sh).toBe(400)
  })

  it('画像より大きい範囲を選んでも、元画像の外へは出ない', () => {
    const rect = cropSourceRect(
      { x: -50, y: -50, width: 2000, height: 2000 },
      { width: 100, height: 100 },
      NATURAL.width,
      NATURAL.height,
      0
    )

    expect(rect.sx).toBe(0)
    expect(rect.sy).toBe(0)
    expect(rect.sw).toBe(NATURAL.width)
    expect(rect.sh).toBe(NATURAL.height)
  })

  it('90 度回転では、切り出しの基準が回転後の実寸になる', () => {
    const fit = fitCrop(NATURAL.width, NATURAL.height, 90, { width: 400, height: 400 })
    const rect = cropSourceRect(
      { x: 0, y: 0, width: fit.frame.width, height: fit.frame.height },
      fit.frame,
      NATURAL.width,
      NATURAL.height,
      90
    )

    // 回転後の実寸は 800x600
    expect(rect.sx).toBe(0)
    expect(rect.sy).toBe(0)
    expect(rect.sw).toBe(800)
    expect(rect.sh).toBe(600)
  })

  it('読み込み前（実寸 0）でも 1px を返して壊れない', () => {
    const fit = fitCrop(0, 0, 0, { width: 100, height: 100 })

    expect(fit.frame).toEqual({ width: 1, height: 1 })
    expect(cropSourceRect({ x: 0, y: 0, width: 1, height: 1 }, fit.frame, 0, 0, 0).sw).toBe(1)
  })
})

/**
 * 書き出しの名前。
 *
 * <p>トリミング窓は**常に JPEG で書き出す**ので、名前の拡張子も実体に合わせる
 * （元が `.png` のまま `.jpg` の中身を保存しない）。</p>
 */
describe('トリミングの書き出し名', () => {
  it('JPEG 以外の拡張子は .jpg に直す', () => {
    expect(jpegFileName('question.png')).toBe('question.jpg')
    expect(jpegFileName('答案用紙.webp')).toBe('答案用紙.jpg')
    expect(jpegFileName('scan.PNG')).toBe('scan.jpg')
  })

  it('JPEG の拡張子はそのまま（大文字小文字も変えない）', () => {
    expect(jpegFileName('a.jpeg')).toBe('a.jpeg')
    expect(jpegFileName('a.jpg')).toBe('a.jpg')
    expect(jpegFileName('a.JPEG')).toBe('a.JPEG')
  })

  it('拡張子が無い・空の名前でも壊れない', () => {
    expect(jpegFileName('image')).toBe('image.jpg')
    expect(jpegFileName('  ')).toBe('image.jpg')
  })
})

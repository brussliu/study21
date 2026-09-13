import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const publicDir = path.join(webRoot, 'public')

/** PNG の IHDR から実寸を読む（画像ライブラリを足さずに検証する）。 */
function pngSize(file: string): { width: number; height: number } {
  const buffer = readFileSync(file)
  expect(buffer.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a')
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) }
}

/**
 * システムアイコン（PC 側）。
 * 参照だけ足して実ファイルを置き忘れる／占位アイコンのまま出す、という事故を防ぐ。
 */
describe('システムアイコン（pc-web）', () => {
  const html = readFileSync(path.join(webRoot, 'index.html'), 'utf8')

  it('index.html がアイコンとテーマカラーを参照している', () => {
    expect(html).toContain('rel="icon" href="/favicon.svg"')
    expect(html).toContain('rel="apple-touch-icon" href="/apple-touch-icon.png"')
    expect(html).toContain('name="theme-color" content="#25876f"')
  })

  it('参照先のファイルが実在する', () => {
    for (const file of ['favicon.svg', 'favicon-32.png', 'apple-touch-icon.png']) {
      expect(existsSync(path.join(publicDir, file)), `${file} がありません（node tmp/tools/generate-app-icons.mjs）`)
        .toBe(true)
    }
  })

  it('favicon.svg はサイドバーのロゴマークと同じ図形（占位アイコンではない）', () => {
    const svg = readFileSync(path.join(publicDir, 'favicon.svg'), 'utf8')

    expect(svg).toContain('study21-icon-grad') // サイドバーと同じグラデーション定義
    expect(svg).toContain('#2a8a72') // グラデーション開始色（サイドバーのロゴマークと同一）
    expect(svg).toContain('#4cb39a') // グラデーション終了色
    expect(svg).toContain('#ffffff') // 学ぶ人（頭）
    expect(svg).not.toContain('>S2<') // 旧プレースホルダの文字
  })

  it('favicon-32.png の実寸は 32×32', () => {
    expect(pngSize(path.join(publicDir, 'favicon-32.png'))).toEqual({ width: 32, height: 32 })
  })
})

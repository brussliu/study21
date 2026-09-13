import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const publicDir = path.join(webRoot, 'public')

interface ManifestIcon {
  src: string
  sizes: string
  type: string
  purpose: string
}

/** PNG の IHDR から実寸を読む（画像ライブラリを足さずに検証する）。 */
function pngSize(file: string): { width: number; height: number } {
  const buffer = readFileSync(file)
  expect(buffer.subarray(0, 8).toString('hex')).toBe('89504e470d0a1a0a')
  return { width: buffer.readUInt32BE(16), height: buffer.readUInt32BE(20) }
}

/**
 * システムアイコン（Mobile / PWA）。
 * manifest の icons 宣言と実ファイルの食い違い（サイズ違い・置き忘れ・占位のまま）を防ぐ。
 */
describe('システムアイコン（mobile-web / PWA）', () => {
  const manifest = JSON.parse(
    readFileSync(path.join(publicDir, 'manifest.webmanifest'), 'utf8')
  ) as { icons: ManifestIcon[] }

  it('manifest の icons が実在し、宣言サイズと実寸が一致する', () => {
    expect(manifest.icons.length).toBeGreaterThanOrEqual(5)

    for (const icon of manifest.icons) {
      const file = path.join(publicDir, icon.src.replace(/^\//, ''))
      expect(existsSync(file), `${icon.src} がありません（node tmp/tools/generate-app-icons.mjs）`).toBe(true)

      if (icon.type === 'image/png') {
        const size = pngSize(file)
        expect(`${size.width}x${size.height}`, `${icon.src} の実寸`).toBe(icon.sizes)
      }
    }
  })

  it('maskable と any の両方が登録されている', () => {
    const purposes = manifest.icons.map((icon) => icon.purpose)
    expect(purposes).toContain('any')
    expect(purposes).toContain('maskable')
  })

  it('index.html が icon.svg と apple-touch-icon を参照している', () => {
    const html = readFileSync(path.join(webRoot, 'index.html'), 'utf8')

    expect(html).toContain('rel="icon" href="/icons/icon.svg"')
    expect(html).toContain('rel="apple-touch-icon" href="/icons/apple-touch-icon.png"')
  })

  it('icon.svg はサイドバーのロゴマークと同じ図形（占位アイコンではない）', () => {
    const svg = readFileSync(path.join(publicDir, 'icons', 'icon.svg'), 'utf8')

    expect(svg).toContain('study21-icon-grad')
    expect(svg).not.toContain('>S2<')
  })
})

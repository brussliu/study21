import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync, statSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const srcRoot = path.join(webRoot, 'src')

/** src 以下の .vue を全部集める。 */
function vueFiles(dir: string): string[] {
  const result: string[] = []
  for (const entry of readdirSync(dir)) {
    const full = path.join(dir, entry)
    if (statSync(full).isDirectory()) {
      result.push(...vueFiles(full))
    } else if (entry.endsWith('.vue')) {
      result.push(full)
    }
  }
  return result
}

// プロトタイプの画面（src/views/prototype/**）は設計の見本なので対象外
const files = vueFiles(srcRoot)
  .filter((file) => !file.includes(`${path.sep}prototype${path.sep}`))
/** ダイアログ・ドロワー・ビューアの背景（クリックで閉じてしまいそうな要素）。 */
const overlayFiles = files.filter((file) => /class="[^"]*overlay[^"]*"/.test(readFileSync(file, 'utf8')))

/**
 * ダイアログは **背景（灰色の部分）をクリックしても閉じない**（ユーザーの指定）。
 * 閉じるのは右上の × か「閉じる」「キャンセル」ボタンだけにする
 * （入力中に背景を押して消えてしまうのを防ぐ）。
 */
describe('ダイアログは背景クリックで閉じない', () => {
  it('対象の画面が見つかっている（テスト自体が空回りしていない）', () => {
    expect(overlayFiles.length).toBeGreaterThan(10)
  })

  it('背景クリックで閉じる指定（@click.self）が残っていない', () => {
    const offenders = overlayFiles.filter((file) => readFileSync(file, 'utf8').includes('@click.self'))

    expect(offenders.map((file) => path.relative(webRoot, file))).toEqual([])
  })

  it('閉じる手段（× か 閉じる / キャンセル）は残っている', () => {
    // 背景クリックを外したので、代わりに閉じるボタンが必ずあること
    const closable = overlayFiles.filter((file) => {
      const source = readFileSync(file, 'utf8')
      return source.includes('dialog__close') || source.includes('閉じる') || source.includes('キャンセル')
    })

    expect(closable.length).toBe(overlayFiles.length)
  })
})

/**
 * 複数行の入力（textarea）に上下の余白を入れる。
 * 設計システムの `.input` は 1 行入力用（height ＋ padding: 0 …）なので、
 * `<textarea class="input">` に使うと文字が上端に張り付いてしまう。
 */
describe('複数行の入力（textarea）の余白', () => {
  it('アプリ共通のスタイルで textarea.input の上下の余白を入れている', () => {
    const appCss = readFileSync(path.join(srcRoot, 'assets/app/app.css'), 'utf8')

    expect(appCss).toContain('textarea.input {')
    expect(appCss).toContain('padding: var(--sp-2) var(--sp-3);')
    expect(appCss).toContain('height: auto;')
  })
})

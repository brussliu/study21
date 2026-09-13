import { describe, expect, it } from 'vitest'
import { readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { prototypePageMap, prototypePages } from '@/config/prototypePages.generated'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const generatedDir = path.join(webRoot, 'src', 'views', 'prototype', 'generated')
const appCss = readFileSync(path.join(webRoot, 'src', 'assets', 'app', 'app.css'), 'utf8')

describe('migrated prototype pages', () => {
  it('旧ログインを除く94画面をVueコンポーネントとして登録する', () => {
    expect(prototypePages).toHaveLength(94)
    expect(prototypePageMap.has('home')).toBe(true)
    expect(prototypePageMap.has('english')).toBe(true)
    expect(prototypePageMap.has('math-wrong')).toBe(true)
    expect(prototypePageMap.has('study-monitor')).toBe(true)
  })

  it('登録された全画面に生成済みVueコンポーネントがある', () => {
    const components = import.meta.glob('@/views/prototype/generated/*.vue')
    expect(Object.keys(components)).toHaveLength(prototypePages.length)
  })
})

/**
 * 移行画面のレイアウト規約。
 * 生成された画面は外枠のスタイルを持たないため、カード同士の縦の間隔は
 * app.css の .prototype-screen（flex column + gap var(--sp-4)）が唯一のよりどころ。
 * ここが崩れると「検索条件と一覧が隙間なく接する」状態に戻る（実測 0px の不具合）。
 */
describe('生成画面のレイアウト規約', () => {
  it('全画面のルート要素が .prototype-screen を持つ（縦の間隔の前提）', () => {
    const files = readdirSync(generatedDir).filter((file) => file.endsWith('.vue'))
    expect(files).toHaveLength(prototypePages.length)

    const withoutRhythmClass = files.filter((file) => {
      const source = readFileSync(path.join(generatedDir, file), 'utf8')
      const template = source.slice(source.indexOf('<template>'))
      const rootClass = template.match(/class="([^"]+)"/)?.[1] ?? ''
      return !rootClass.split(/\s+/).includes('prototype-screen')
    })

    expect(withoutRhythmClass).toEqual([])
  })

  it('.prototype-screen が縦積みの間隔（flex column + gap var(--sp-4)）を定義している', () => {
    const rules = appCss.match(/\.prototype-screen[^{]*\{[^}]*\}/g) ?? []
    const rhythm = rules.find((rule) => rule.includes('flex-direction: column'))

    expect(rhythm, '.prototype-screen の縦積みルールがありません').toBeTruthy()
    expect(rhythm).toContain('gap: var(--sp-4)')
  })

  it('タブパネルの display を上書きしない（非表示タブが見えてしまう回帰の防止）', () => {
    // デザインシステムでは .tabs__panel が display:none、.is-active だけ表示される。
    // 間隔ルールは表示中のパネルにだけ当てる（.is-active / :not(...) で限定する）。
    // コメント内の記述を選択器と誤認しないよう、コメントを除去してから見る。
    const css = appCss.replace(/\/\*[\s\S]*?\*\//g, '')
    const selectors = [...css.matchAll(/([^{}]+)\{/g)]
      .map((match) => match[1].trim())
      .filter((selector) => selector.includes('.tabs__panel'))

    expect(selectors.length).toBeGreaterThan(0)
    for (const selector of selectors) {
      expect(selector, `${selector} は表示中パネルに限定されていません`).toMatch(/\.is-active|:not\(/)
    }
  })
})

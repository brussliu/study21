import { readFileSync } from 'node:fs'
import path from 'node:path'
import { describe, expect, it } from 'vitest'

/**
 * 操作ボタンの見た目の統一（ソース側の番人）。
 *
 * 同じ役割のボタンはどの画面でも同じクラス・アイコンにする。実機での一致は
 * `tmp/e2e/e2e-action-buttons.mjs`（57 項目）が確認する。ここでは、うっかり
 * 片方の画面だけ書き換えてしまうのを防ぐためにソースを直接見る。
 *
 *   リセット … `.btn.btn--secondary` ＋ rotate アイコン（基準はリンククリップ）
 *   新規     … `.btn.btn--primary`  ＋ plus アイコン（基準はサイト管理）
 */
const src = path.resolve(__dirname, '../src')

/**
 * 「リセット」を持つ検索条件の画面（画像ビューア・画像エディタはツールバー用に例外）。
 * 端末コントロールは端末の台数が少ないため検索条件を置かなくした（2026-09-12）ので対象外。
 */
const RESET_VIEWS = [
  'views/linkclip/LinkClipView.vue',
  'views/document/DocumentListView.vue',
  'views/testinfo/TestInfoView.vue',
  'views/net/SiteManagementView.vue'
]

/** 「新規」を持つ画面。 */
const CREATE_VIEWS = [
  'views/document/DocumentFolderView.vue',
  'views/document/DocumentListView.vue',
  'views/linkclip/LinkClipView.vue',
  'views/tempfile/TempFileView.vue',
  'views/testinfo/TestInfoView.vue',
  'views/net/SiteManagementView.vue'
]

function read(relative: string): string {
  return readFileSync(path.join(src, relative), 'utf8')
}

/** その行がボタンの開始行なら、ボタン 1 つ分の文字列をまとめて返す。 */
function buttonsWithLabel(source: string, label: string): string[] {
  const found: string[] = []
  const lines = source.split('\n')
  for (let index = 0; index < lines.length; index += 1) {
    if (!lines[index].includes('<button')) continue
    // 開始タグから閉じタグ（または数行先）までを 1 つのボタンとして扱う
    const chunk = lines.slice(index, index + 5).join('\n')
    const end = chunk.indexOf('</button>')
    const button = end >= 0 ? chunk.slice(0, end) : chunk
    if (button.includes(`>${label}`) || button.includes(`> ${label}`) || button.includes(`${label}</button>`)) {
      found.push(button)
    }
  }
  return found
}

describe('操作ボタンの見た目（ソース側の番人）', () => {
  it.each(RESET_VIEWS)('%s のリセットは secondary ＋ rotate アイコン', (view) => {
    const buttons = buttonsWithLabel(read(view), 'リセット')
    expect(buttons.length).toBeGreaterThan(0)
    for (const button of buttons) {
      expect(button).toContain('btn--secondary')
      expect(button).not.toContain('btn--sm')
      expect(button).toContain('name="rotate"')
    }
  })

  it.each(CREATE_VIEWS)('%s の新規は primary ＋ plus アイコン', (view) => {
    const buttons = buttonsWithLabel(read(view), '新規')
    expect(buttons.length).toBeGreaterThan(0)
    for (const button of buttons) {
      expect(button).toContain('btn--primary')
      expect(button).toContain('name="plus"')
      expect(button).not.toContain('btn--sm')
    }
  })
})

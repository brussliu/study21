import { beforeEach, describe, expect, it, vi } from 'vitest'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const runtimeSource = readFileSync(
  path.join(webRoot, 'src', 'features', 'system-settings', 'study2SettingRuntime.ts'),
  'utf8',
)
// バッチ管理画面の説明はこの2つが出所（原型ページ = 見た目、レジストリ = 実データ）
const batchPageSource = readFileSync(
  path.join(webRoot, 'src', 'views', 'prototype', 'generated', 'BatchPage.vue'),
  'utf8',
)
const batchRegistrySource = readFileSync(
  path.join(webRoot, '..', '..', 'backend', 'admin-api', 'src', 'main', 'java', 'com', 'study21', 'admin', 'batch', 'BatchTaskRegistry.java'),
  'utf8',
)

type RuntimeWindow = Window & { __study21SystemSettings?: { mount: () => void } }

/**
 * 設定画面の AI サービス名は繁体字で表記する（智譜・千問）。
 * 2.0 の setting.js から移した文言に簡体字（智谱・千问）が残っていたため、
 * 「実際に描画された文字」で固定する。あわせて、値のマッピングに使うキー
 * （qwen / bigmodel など）を書き換えていないことも確認する。
 */
describe('設定画面のAIサービス名（繁体字表記）', () => {
  beforeEach(() => {
    vi.resetModules()
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline'))))
    // SystemSettingsView.vue が用意する器（描画に必要な id だけを再現する）
    document.body.innerHTML = `
      <button type="button" id="settingReloadBtn"></button>
      <button type="button" id="settingSaveBtn"></button>
      <aside id="settingCategoryNav"></aside>
      <div id="settingLoading"></div>
      <div id="settingPanels" hidden></div>
      <div id="settingToast"></div>`
  })

  async function mountSettings(): Promise<string> {
    await import('@/features/system-settings/study2SettingRuntime')
    ;(window as RuntimeWindow).__study21SystemSettings?.mount()
    await new Promise((resolve) => setTimeout(resolve, 0))
    return document.getElementById('settingPanels')?.textContent ?? ''
  }

  it('智譜・千問（繁体字）で表示する', async () => {
    const text = await mountSettings()

    expect(text).toContain('千問')
    expect(text).toContain('智譜')
  })

  it('簡体字（智谱・千问）を表示しない', async () => {
    const text = await mountSettings()

    expect(text).not.toContain('千问')
    expect(text).not.toContain('智谱')
  })

  it('設定キー・プロバイダ値（qwen / bigmodel など）は変えていない', () => {
    expect(runtimeSource).toContain("{ key: 'qwen', label: '千問', prefix: 'qwen' }")
    expect(runtimeSource).toContain("modelFields('qwen','千問')")
    expect(runtimeSource).toContain("value: 'bigmodel:1'")
    expect(runtimeSource).not.toContain("key: '千問'")
  })
})

/**
 * バッチ管理画面（batC91 = 共通AI OCR）の説明も同じ表記に揃える。
 * 画面に出る文言はバックエンドのレジストリが正で、原型ページはその写し。
 */
describe('バッチ管理画面のAIサービス名（繁体字表記）', () => {
  const expected = 'AI OCR（智譜OCR 共通処理）'

  it('原型ページ（BatchPage.vue）が繁体字', () => {
    expect(batchPageSource).toContain(expected)
    expect(batchPageSource).not.toContain('智谱')
  })

  it('バックエンドのレジストリ（BatchTaskRegistry.java）が繁体字', () => {
    expect(batchRegistrySource).toContain(expected)
    expect(batchRegistrySource).not.toContain('智谱')
  })
})

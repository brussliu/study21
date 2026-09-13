import { describe, expect, it } from 'vitest'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const read = (relativePath: string): string => readFileSync(path.join(webRoot, relativePath), 'utf8')

/**
 * 日本語 UI に簡体字・中国語の語句を残さない。
 *
 * 2.0（JSP）から移した文言に、中国語のままの語句が残っていた箇所を固定する。
 * 判定は「日本語の画面・ヘルプに出る文字」だけを対象にし、
 * 中国語が仕様として出る箇所（遊び方の中国語訳、中国語の列・解説、AI プロンプト、
 * 中文ドキュメント、コメント）は対象外にしている。
 */
describe('日本語UIに簡体字・中国語の語句を残さない', () => {
  it('設定画面のヘルプ（Qwen の URL / 採点基準）が日本語', () => {
    const source = read('src/features/system-settings/study2SettingRuntime.ts')

    expect(source).toContain('ベースURL（/compatible-mode/v1）')
    expect(source).toContain('採点基準、文字数判定原則')
    expect(source).not.toContain('基础URL')
  })

  it('マインドマップ画面のボタン・見出しが日本語', () => {
    const source = read('src/views/prototype/generated/MindmapPage.vue')

    for (const japanese of [
      '展開',
      '折りたたむ',
      'JSONエクスポート',
      'Demoをリセット',
      'ノードのスタイル',
      'スタイルを適用',
      '文字サイズ',
      '太字',
    ]) {
      expect(source, japanese).toContain(japanese)
    }
    for (const chinese of ['展开', '折叠', '导出', '重置Demo', '样式', '字号', '粗体']) {
      expect(source, chinese).not.toContain(chinese)
    }
  })

  it('移行ページのラベル・説明が日本語', () => {
    const japaneseWord = read('src/views/prototype/generated/JapaneseTestDemoPage.vue')
    expect(japaneseWord).toContain('各問題形式のデモ')
    expect(japaneseWord).not.toContain('题型')

    const testA = read('src/views/prototype/generated/TestworldTestAPage.vue')
    expect(testA).toContain('中国語の意味 / 日本語の意味')
    expect(testA).not.toContain('含义')

    const testB = read('src/views/prototype/generated/TestworldTestBPage.vue')
    expect(testB).toContain('中国語の意味 / 日本語の意味を見て英語を入力してください')
    expect(testB).not.toContain('含义')

    const documentDialog = read('src/views/prototype/generated/DocumentInputdialog1Page.vue')
    expect(documentDialog).toContain('4級分類/アップロード/コメント')
    expect(documentDialog).not.toContain('上传')

    const noteCopy = read('src/views/prototype/generated/TempFileUploadNoteCopyPage.vue')
    expect(noteCopy).toContain('中国語名のバックアップ副本')
    expect(noteCopy).not.toContain('中文命名')

    const designSystem = read('src/views/prototype/generated/DesignSystemPage.vue')
    expect(designSystem).toContain('全画面サイズ')
    expect(designSystem).not.toContain('全屏')
  })

  it('ホーム画面の読み上げラベルが日本語', () => {
    const source = read('src/views/prototype/generated/HomePage.vue')

    expect(source).toContain('aria-label="ネット利用の棒グラフ"')
    expect(source).not.toContain('上网柱状图')
  })

  it('メニュー定義のコメントに中国語を残さない', () => {
    const source = read('src/config/menuRegistry.ts')

    expect(source).toContain('ui-demo のサイドバー')
    expect(source).not.toContain('側栏')
  })
})

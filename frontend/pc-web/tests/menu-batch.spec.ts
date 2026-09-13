import { describe, expect, it } from 'vitest'
import { prototypeMenu } from '@/config/menuRegistry'

/**
 * 左メニューの「バッチ管理」は親メニューで、子に バッチ一覧 / バッチ実行履歴 / AI呼出履歴 を持つ。
 * 3 エリア（admin / student / parent）とも同じ構成にする。
 */
describe('メニュー：バッチ管理', () => {
  it.each(['admin', 'student', 'parent'] as const)('%s: 親メニューと 3 つの子を持つ', (area) => {
    const batch = prototypeMenu(area).find((item) => item.id === 'batch')
    expect(batch).toBeTruthy()
    expect(batch?.label).toBe('バッチ管理')
    expect(batch?.children?.map((child) => child.label)).toEqual(['バッチ一覧', 'バッチ実行履歴', 'AI呼出履歴'])
    expect(batch?.children?.map((child) => child.path)).toEqual([
      `/${area}/batch`, `/${area}/batch-history`, `/${area}/batch-ai-history`
    ])
  })
})

/**
 * 左メニューの「ネットワーク制御」は親メニューで、子に サイト管理 / 端末コントロール /
 * インターネット利用履歴 を持つ。サイト管理・端末コントロールはトップレベルから移した。
 */
describe('メニュー：ネットワーク制御', () => {
  it.each(['admin', 'student', 'parent'] as const)('%s: 親メニューと 3 つの子を持つ', (area) => {
    const menu = prototypeMenu(area)
    const network = menu.find((item) => item.id === 'network')
    expect(network).toBeTruthy()
    expect(network?.label).toBe('ネットワーク制御')
    expect(network?.children?.map((child) => child.label)).toEqual([
      'サイト管理', '端末コントロール', 'インターネット利用履歴'
    ])
    expect(network?.children?.map((child) => child.path)).toEqual([
      `/${area}/site`, `/${area}/terminal-control`, `/${area}/internet-usage`
    ])
    // トップレベルには残っていない（親の下に入れた）
    expect(menu.filter((item) => !item.children).map((item) => item.label))
      .not.toContain('サイト管理')
    expect(menu.filter((item) => !item.children).map((item) => item.label))
      .not.toContain('端末コントロール')
  })
})

/**
 * 左メニューの「アプリ制御」は親メニューで、子に アプリ管理 / アプリ利用履歴 を持つ。
 * （旧: トップレベルの「アプリ制御」「エージェント履歴」）
 */
describe('メニュー：アプリ制御', () => {
  it.each(['admin', 'student', 'parent'] as const)('%s: 親メニューと 2 つの子を持つ', (area) => {
    const menu = prototypeMenu(area)
    const app = menu.find((item) => item.id === 'app-control')
    expect(app).toBeTruthy()
    expect(app?.label).toBe('アプリ制御')
    expect(app?.children?.map((child) => child.label)).toEqual(['アプリ管理', 'アプリ利用履歴'])
    // ルートは既存のまま（画面は移行用プロトタイプが受ける）
    expect(app?.children?.map((child) => child.path)).toEqual([
      `/${area}/agent-control`, `/${area}/agent-history`
    ])
    // トップレベルには残っていない
    const top = menu.filter((item) => !item.children).map((item) => item.label)
    expect(top).not.toContain('エージェント履歴')
    expect(top.filter((label) => label === 'アプリ制御')).toHaveLength(0)
  })
})

/**
 * 「履歴管理」（2.0 の history.jsp を再現したプロトタイプ画面）は左メニューから外した。
 * 中身は インターネット利用履歴 / バッチ実行履歴 / AI呼出履歴 として個別の画面にある。
 */
describe('メニュー：履歴管理を出さない', () => {
  it.each(['admin', 'student', 'parent'] as const)('%s: トップレベルに「履歴管理」が無い', (area) => {
    const menu = prototypeMenu(area)
    const top = menu.filter((item) => !item.children).map((item) => item.label)

    expect(top).not.toContain('履歴管理')
    // 子メニューとしても持たない
    expect(menu.flatMap((item) => item.children ?? []).map((child) => child.label))
      .not.toContain('履歴管理')
    // 代わりの画面（個別の履歴）は残っている
    const labels = menu.flatMap((item) => [item.label, ...(item.children ?? []).map((child) => child.label)])
    expect(labels).toContain('インターネット利用履歴')
    expect(labels).toContain('バッチ実行履歴')
    expect(labels).toContain('AI呼出履歴')
  })
})

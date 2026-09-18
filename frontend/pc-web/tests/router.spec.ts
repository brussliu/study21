import { describe, expect, it, vi } from 'vitest'
import { createAppRouter, isChunkLoadError, recoverFromChunkLoadError, routes } from '@/router'

describe('pc-web router', () => {
  it('主要なルートを定義している', () => {
    const names = routes
      .flatMap((r) => [r.name, ...(r.children ?? []).map((c) => c.name)])
      .filter((n): n is string => typeof n === 'string')

    expect(names).toContain('login')
    expect(names).toContain('admin-login')
    expect(names).toContain('admin-home')
    expect(names).toContain('admin-system-settings')
    expect(names).toContain('admin-screen')
    expect(names).toContain('student-home')
    expect(names).toContain('admin-site')
    expect(names).toContain('admin-terminal-control')
    expect(names).toContain('student-site')
    expect(names).toContain('student-terminal-control')
    expect(names).toContain('parent-site')
    expect(names).toContain('parent-terminal-control')
    expect(names).toContain('student-system-settings')
    expect(names).toContain('student-screen')
    expect(names).toContain('parent-home')
    expect(names).toContain('parent-system-settings')
    expect(names).toContain('parent-screen')
    expect(names).toContain('forbidden')
    expect(names).toContain('not-found')
    expect(names).toContain('server-error')
  })

  it('catch-all は /404 へリダイレクトする', () => {
    const catchAll = routes.find((r) => r.path === '/:pathMatch(.*)*')
    expect(catchAll).toBeDefined()
    expect(catchAll?.redirect).toBe('/404')
  })

  it('ルーターを生成できる', () => {
    const router = createAppRouter()
    expect(router).toBeDefined()
  })
})

/**
 * 配備で画面のファイル名が変わると、開いたままのタブは古い名前を取りに行き、
 * nginx が index.html を返す（MIME が違うので読み込めない）。このときは
 * **ページ全体を読み込み直して**新しいビルドを取りに行く（`createAppRouter` の onError）。
 */
describe('配備後のタブの自動復帰', () => {
  it('画面の読み込み失敗を見分けられる', () => {
    expect(isChunkLoadError(
      new TypeError('Failed to fetch dynamically imported module: http://x/assets/A.js'))).toBe(true)
    expect(isChunkLoadError(new Error('Importing a module script failed.'))).toBe(true)
    expect(isChunkLoadError(new Error('error loading dynamically imported module'))).toBe(true)
    // 別のエラーは読み込み直さない（無限ループを避ける）
    expect(isChunkLoadError(new Error('Network Error'))).toBe(false)
    expect(isChunkLoadError('なんでもない文字列')).toBe(false)
    expect(isChunkLoadError(undefined)).toBe(false)
  })

  it('読み込み失敗のときはページ全体を読み込み直す（1 回だけ）', () => {
    window.sessionStorage.clear()
    const navigate = vi.fn()
    const error = new TypeError(
      'Failed to fetch dynamically imported module: http://x/assets/ClassroomDetailView-xxxx.js')

    expect(recoverFromChunkLoadError(error, '/student/classroom/12', navigate)).toBe(true)
    expect(navigate).toHaveBeenCalledWith('/student/classroom/12')

    // 2 回目は読み込み直さない（壊れた状態で無限に再読み込みしない）
    expect(recoverFromChunkLoadError(error, '/student/classroom/12', navigate)).toBe(false)
    expect(navigate).toHaveBeenCalledTimes(1)

    // 別のエラーでも読み込み直さない
    window.sessionStorage.clear()
    expect(recoverFromChunkLoadError(new Error('Network Error'), '/student/home', navigate)).toBe(false)
    expect(navigate).toHaveBeenCalledTimes(1)
  })
})

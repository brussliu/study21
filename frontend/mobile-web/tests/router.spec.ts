import { describe, expect, it } from 'vitest'
import { createAppRouter, routes } from '@/router'

describe('mobile-web router', () => {
  it('主要なルートを定義している', () => {
    const names = routes
      .flatMap((r) => [r.name, ...(r.children ?? []).map((c) => c.name)])
      .filter((n): n is string => typeof n === 'string')

    expect(names).toContain('login')
    expect(names).toContain('admin-login')
    expect(names).toContain('admin-home')
    expect(names).toContain('student-home')
    expect(names).toContain('parent-home')
    expect(names).toContain('forbidden')
    expect(names).toContain('not-found')
    expect(names).toContain('server-error')
  })

  it('catch-all は /404 へリダイレクトする', () => {
    const catchAll = routes.find((r) => r.path === '/:pathMatch(.*)*')
    expect(catchAll?.redirect).toBe('/404')
  })

  it('ルーターを生成できる', () => {
    expect(createAppRouter()).toBeDefined()
  })
})

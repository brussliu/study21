import { describe, expect, it } from 'vitest'
import { existsSync, readFileSync, readdirSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { mount } from '@vue/test-utils'
import PreparationView from '@/views/PreparationView.vue'
import { routes } from '@/router'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const at = (...parts: string[]): string => path.join(webRoot, ...parts)

/**
 * mobile-web は「空の骨組み」だけを置く方針。
 * 業務画面を追加するときは、このファイルの断言も一緒に見直す（README.md 参照）。
 */
describe('mobile-web の骨組み', () => {
  it('ルートは「準備中」1 画面だけ', () => {
    expect(routes.map((route) => route.path)).toEqual(['/'])
    expect(routes.map((route) => route.name)).toEqual(['home'])
  })

  it('業務用のルートを持たない（ログイン・各ロールのホームなど）', () => {
    const paths = routes.map((route) => route.path)
    for (const business of ['/login', '/admin', '/student', '/parent', '/forgot-password']) {
      expect(paths, business).not.toContain(business)
    }
  })

  it('画面は準備中ページだけ', () => {
    expect(readdirSync(at('src', 'views'))).toEqual(['PreparationView.vue'])
  })

  it('削除した業務コードが戻っていない', () => {
    const removed = [
      'src/views/login',
      'src/views/admin',
      'src/views/student',
      'src/views/parent',
      'src/views/error',
      'src/components/layout',
      'src/components/SystemStatus.vue',
      'src/layouts/MobileLayout.vue',
      'src/stores/auth.ts',
      'src/stores/system.ts',
      'src/stores/theme.ts',
      'src/config/menuRegistry.ts',
      'src/api/register.ts',
    ]
    for (const relative of removed) {
      expect(existsSync(at(relative)), relative).toBe(false)
    }
  })

  it('後で内容を入れるディレクトリは空のまま残している', () => {
    for (const directory of ['layouts', 'components', 'stores', 'api', 'config']) {
      expect(existsSync(at('src', directory, '.gitkeep')), directory).toBe(true)
      const entries = readdirSync(at('src', directory))
      expect(entries, directory).toEqual(['.gitkeep'])
    }
  })

  it('準備中ページが表示される', () => {
    const wrapper = mount(PreparationView)
    expect(wrapper.text()).toContain('準備中')
    expect(wrapper.text()).toContain('Study 2.1 Mobile')
  })

  it('起動に必要なファイルが揃っている', () => {
    for (const file of ['index.html', 'src/main.ts', 'src/App.vue', 'src/router/index.ts', 'src/assets/main.css']) {
      expect(existsSync(at(file)), file).toBe(true)
    }
    const main = readFileSync(at('src', 'main.ts'), 'utf8')
    expect(main).toContain("import App from './App.vue'")
    expect(main).toContain("import { createAppRouter } from './router'")
  })
})

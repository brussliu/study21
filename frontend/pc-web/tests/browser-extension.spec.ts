import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import AppTopbar from '@/components/layout/AppTopbar.vue'
import InternetUsageHistoryView from '@/views/network/InternetUsageHistoryView.vue'
import { createAppRouter } from '@/router'
import { useAuthStore } from '@/stores/auth'
import type { BrowserExtensionRegistration } from '@/api/browserExtension'

/**
 * ブラウザ拡張まわりの画面（2.0 は拡張を手で配り、認証も無かった）。
 *
 * ・ダウンロード … 画面右上（学生の表示のとなり）の「プラグイン」から小さなパネルで出す
 * ・接続コード … Web閲覧履歴 の一覧見出しのボタンからダイアログで発行・再発行する
 *   （接続してきた端末の一覧は 端末コントロール の「ブラウザ端末」タブ）
 */
const TOKEN = 'abcd'.repeat(16)

function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function registration(overrides: Partial<BrowserExtensionRegistration> = {}): BrowserExtensionRegistration {
  return {
    issued: false,
    token: null,
    deviceCount: 0,
    onlineCount: 0,
    devices: [],
    message: null,
    ...overrides
  }
}

const ISSUED = registration({
  issued: true,
  token: TOKEN,
  deviceCount: 2,
  onlineCount: 1,
  devices: [
    {
      registrationId: 1,
      deviceId: 'chrome-1750000000000-ab12cd34',
      deviceName: 'LIU-PC',
      osType: 'Windows',
      browserType: 'Chrome',
      browserVersion: '153.0.0.0',
      extensionVersion: '2.1.0',
      profileId: 'Default',
      status: 'ACTIVE',
      online: true,
      lastStartedAt: '2026-09-13T20:00:00',
      lastSentAt: '2026-09-13T21:00:00',
      lastHeartbeatAt: '2026-09-13T21:05:00',
      createdAt: '2026-09-13T20:00:00'
    }
  ]
})

/** ダイアログは body へ Teleport されるので、DOM から直接見る。 */
function dialogEl(selector: string): HTMLElement | null {
  return document.querySelector(selector)
}

function dialogText(selector: string): string {
  return dialogEl(selector)?.textContent?.trim() ?? ''
}

/** Teleport された要素のクリック（Vue のリスナーは native click で動く）。 */
async function clickIn(selector: string): Promise<void> {
  const element = dialogEl(selector)
  expect(element, `${selector} が見つかりません`).not.toBeNull()
  element?.click()
  await flushPromises()
}

function recorded(fetchMock: ReturnType<typeof vi.fn>): { url: string; method: string }[] {
  return fetchMock.mock.calls.map((call) => ({
    url: String(call[0]),
    method: String((call[1] as RequestInit | undefined)?.method ?? 'GET').toUpperCase()
  }))
}

beforeEach(() => {
  useToast().items.splice(0)
  window.sessionStorage.clear()
  document.body.innerHTML = ''
  window.scrollTo = vi.fn()
})

/* ============================================================
   1. 右上のプラグインメニュー（ダウンロード）
   ============================================================ */
async function mountTopbar(options: {
  registrationData?: BrowserExtensionRegistration
  packageInfo?: Record<string, unknown> | null
} = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  useAuthStore().login('STUDENT', '山田 太郎')
  const router = createAppRouter()
  await router.push('/student/home')
  await router.isReady()

  const fetchMock = vi.fn(async (url: string) => {
    const target = String(url)
    if (target.includes('/downloads/study21-extension.json')) {
      if (options.packageInfo === null) return new Response('not found', { status: 404 })
      return new Response(JSON.stringify(options.packageInfo ?? {
        version: '2.1.0', file: 'study21-extension.zip', size: 35854, files: 15,
        builtAt: '2026-09-13T21:00:00+09:00'
      }), { status: 200, headers: { 'Content-Type': 'application/json' } })
    }
    if (target.includes('/browser-extension/registration')) {
      return ok(options.registrationData ?? ISSUED)
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(AppTopbar, { global: { plugins: [pinia, router] }, attachTo: document.body })
  return { wrapper, fetchMock }
}

describe('右上のプラグインメニュー', () => {
  it('学生の表示のとなりにプラグインのボタンを置く（既定は閉じている）', async () => {
    const { wrapper } = await mountTopbar()

    const button = wrapper.get('[data-plugin-menu-button]')
    expect(button.attributes('aria-haspopup')).toBe('menu')
    expect(button.attributes('aria-expanded')).toBe('false')
    expect(button.find('svg').exists()).toBe(true)
    // 学生（役割）の表示より後ろ（右側）にある
    const right = wrapper.get('.topbar__right')
    const children = [...right.element.children]
    expect(children.indexOf(wrapper.get('.topbar__meta').element))
      .toBeLessThan(children.indexOf(wrapper.get('.bx-anchor').element))
    expect(wrapper.find('[data-plugin-menu]').exists()).toBe(false)
  })

  it('クリックでプラグインのパネルが開き、ダウンロード先を出す', async () => {
    const { wrapper, fetchMock } = await mountTopbar()

    await wrapper.get('[data-plugin-menu-button]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-plugin-menu-button]').attributes('aria-expanded')).toBe('true')
    const menu = wrapper.get('[data-plugin-menu]')
    expect(menu.text()).toContain('プラグイン')
    const plugin = menu.get('[data-plugin="browser-extension"]')
    expect(plugin.text()).toContain('ブラウザ記録（Chrome 拡張）')
    expect(plugin.text()).toContain('Web閲覧履歴')

    const download = plugin.get('[data-plugin-download="browser-extension"]')
    // 版を付けて、更新後に古いファイルを掴まないようにする
    expect(download.attributes('href')).toBe('/downloads/study21-extension.zip?v=2.1.0')
    expect(download.attributes('download')).toBe('study21-extension.zip')
    expect(plugin.get('[data-plugin-meta="browser-extension"]').text()).toBe('v2.1.0 / 35 KB / 15 ファイル')
    expect(recorded(fetchMock).some((call) => call.url.includes('/downloads/study21-extension.json'))).toBe(true)
  })

  it('インストール手順（Chrome への読み込み方）を出す', async () => {
    const { wrapper } = await mountTopbar()

    await wrapper.get('[data-plugin-menu-button]').trigger('click')
    await flushPromises()

    const steps = wrapper.get('[data-plugin="browser-extension"] .bx-steps')
    expect(steps.text()).toContain('インストール手順を見る')
    expect(steps.text()).toContain('chrome://extensions')
    expect(steps.text()).toContain('パッケージ化されていない拡張機能を読み込む')
    expect(steps.findAll('li').length).toBe(5)
  })

  it('配布パッケージがまだ無いときは案内を出す（デプロイで作られる）', async () => {
    const { wrapper } = await mountTopbar({ packageInfo: null })

    await wrapper.get('[data-plugin-menu-button]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-plugin-meta="browser-extension"]').text()).toContain('配布パッケージはまだありません')
    expect(wrapper.get('[data-plugin-download="browser-extension"]').attributes('href'))
      .toBe('/downloads/study21-extension.zip')
  })

  it('外側のクリックと Esc で閉じる', async () => {
    const { wrapper } = await mountTopbar()

    await wrapper.get('[data-plugin-menu-button]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-plugin-menu]').exists()).toBe(true)

    document.body.click()
    await flushPromises()
    expect(wrapper.find('[data-plugin-menu]').exists()).toBe(false)

    await wrapper.get('[data-plugin-menu-button]').trigger('click')
    await flushPromises()
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await flushPromises()
    expect(wrapper.find('[data-plugin-menu]').exists()).toBe(false)
  })
})

/* ============================================================
   2. Web閲覧履歴 の接続コード ダイアログ
   ============================================================ */
async function mountBrowsing(options: { registrationData?: BrowserExtensionRegistration } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target = String(url)
    const method = String(init?.method ?? 'GET').toUpperCase()
    if (target.includes('/browser-extension/registration/reissue')) {
      return ok({ ...ISSUED, token: 'ffff'.repeat(16), message: '再発行しました。' })
    }
    if (target.includes('/browser-extension/registration')) {
      return ok(method === 'POST' ? ISSUED : (options.registrationData ?? registration()))
    }
    if (target.includes('/api/user/net-terminals')) {
      return ok({ items: [], page: 1, size: 200, totalElements: 0, totalPages: 1 })
    }
    if (target.includes('/api/user/net-access-logs')) {
      return ok({ items: [], totalElements: 0, page: 1, size: 20, totalPages: 1 })
    }
    if (target.includes('/api/user/web-browsing-logs')) {
      return ok({ items: [], totalElements: 0, page: 1, size: 20, totalPages: 1 })
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(InternetUsageHistoryView, { global: { plugins: [pinia] }, attachTo: document.body })
  await flushPromises()
  await wrapper.get('.tabs__tab[data-tab="browsing"]').trigger('click')
  await flushPromises()
  return { wrapper, fetchMock }
}

describe('接続コードのダイアログ', () => {
  it('一覧の見出しにボタンを置く（サイトアクセス履歴のタブには無い）', async () => {
    const { wrapper } = await mountBrowsing()

    expect(dialogEl('[data-ext-code-dialog]')).toBeNull()
    const button = wrapper.get('[data-ext-code]')
    expect(button.text()).toContain('接続コード')
    // 検索条件カードには置かない（見出しの右側）
    expect(wrapper.get('.search-panel').find('[data-ext-code]').exists()).toBe(false)
    expect(wrapper.get('.table-section__head').find('[data-ext-code]').exists()).toBe(true)
  })

  it('開いたときにだけ接続の状態を読む', async () => {
    const { wrapper, fetchMock } = await mountBrowsing()

    expect(recorded(fetchMock).some((call) => call.url.includes('/browser-extension/registration'))).toBe(false)

    await wrapper.get('[data-ext-code]').trigger('click')
    await flushPromises()

    expect(dialogEl('[data-ext-code-dialog]')).not.toBeNull()
    const calls = recorded(fetchMock).filter((call) => call.url.includes('/browser-extension/registration'))
    expect(calls.length).toBe(1)
    expect(calls[0].method).toBe('GET')
  })

  it('未発行なら案内と発行ボタンを出し、押すと接続コードが出る', async () => {
    const { wrapper, fetchMock } = await mountBrowsing()

    await wrapper.get('[data-ext-code]').trigger('click')
    await flushPromises()

    expect(dialogText('[data-ext-code-dialog]')).toContain('接続コードはまだ発行されていません')
    expect(dialogEl('[data-ext-token]')).toBeNull()

    await clickIn('[data-ext-issue]')

    const post = recorded(fetchMock).find((call) =>
      call.url.includes('/browser-extension/registration') && call.method === 'POST')
    expect(post).toBeDefined()
    expect(dialogText('[data-ext-token]').replace(/ /g, '')).toBe(TOKEN)
  })

  it('発行済みならコードを 4 文字区切りで出し、端末数も出す', async () => {
    const { wrapper } = await mountBrowsing({ registrationData: ISSUED })

    await wrapper.get('[data-ext-code]').trigger('click')
    await flushPromises()

    const token = dialogText('[data-ext-token]')
    expect(token.replace(/ /g, '')).toBe(TOKEN)
    expect(token).toContain('abcd abcd')
    expect(dialogText('[data-ext-device-count]')).toBe('接続中 1 台 / 登録 2 台')
    expect(dialogText('[data-ext-code-dialog]')).toContain('端末コントロール')
  })

  it('コピーでクリップボードに入れる', async () => {
    const { wrapper } = await mountBrowsing({ registrationData: ISSUED })
    const writeText = vi.fn(async () => undefined)
    Object.defineProperty(window.navigator, 'clipboard', { value: { writeText }, configurable: true })
    Object.defineProperty(window, 'isSecureContext', { value: true, configurable: true })

    await wrapper.get('[data-ext-code]').trigger('click')
    await flushPromises()
    await clickIn('[data-ext-copy]')

    expect(writeText).toHaveBeenCalledWith(TOKEN)
    expect(dialogText('[data-ext-copy]')).toContain('コピーしました')
  })

  it('再発行は確認してから実行する（キャンセルなら何もしない）', async () => {
    const { wrapper, fetchMock } = await mountBrowsing({ registrationData: ISSUED })
    const confirmMock = vi.fn(() => true)
    vi.stubGlobal('confirm', confirmMock)

    await wrapper.get('[data-ext-code]').trigger('click')
    await flushPromises()
    await clickIn('[data-ext-reissue]')

    expect(confirmMock).toHaveBeenCalled()
    expect(recorded(fetchMock).some((call) => call.url.includes('/registration/reissue'))).toBe(true)
    expect(dialogText('[data-ext-token]').replace(/ /g, '')).toBe('ffff'.repeat(16))

    // キャンセルしたら再発行しない（表示中のコードのまま）
    vi.stubGlobal('confirm', vi.fn(() => false))
    const before = recorded(fetchMock).filter((call) => call.url.includes('/registration/reissue')).length
    await clickIn('[data-ext-reissue]')
    expect(recorded(fetchMock).filter((call) => call.url.includes('/registration/reissue')).length).toBe(before)
    expect(dialogText('[data-ext-token]').replace(/ /g, '')).toBe('ffff'.repeat(16))
  })

  it('閉じるボタンで閉じる', async () => {
    const { wrapper } = await mountBrowsing({ registrationData: ISSUED })

    await wrapper.get('[data-ext-code]').trigger('click')
    await flushPromises()
    await clickIn('[data-ext-code-dialog] .dialog__close')

    expect(dialogEl('[data-ext-code-dialog]')).toBeNull()
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import GeometryAiView from '@/views/geometry/GeometryAiView.vue'

/**
 * 図形管理【AI 生図】の作り直し（作図方法 A〜D・作成する図の種類・補充項目）。
 *
 * 確かめる接縫:
 *   1. ステップは利用者の指定どおり 4 つ（画像を追加 / 読み取る範囲を指定 / 作図方法を選択 /
 *      内容を確認して送信）
 *   2. 画像は **1 枚だけ**。複数渡されたら知らせて、いまの画像を黙って置き換えない
 *   3. 貼り付けは**画像のときだけ**拾う（普通の文字の貼り付けは邪魔しない）
 *   4. 作図方法で「作成する図の種類」の出し方が変わる（B は GRAPH 固定で選ばせない）
 *   5. 補充項目はモードと種類で変わり、**当てはまらない項目は送らない**
 *   6. 送信は受付だけ（`runPath` を呼ばない）。実行はバックエンドの働き手が行う
 *   7. 「結果プレビュー」は出さない（一覧へ戻ってタスクを見る）
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

function router(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/geometry', component: Dummy },
      { path: '/:area/geometry-draw', component: Dummy },
      { path: '/:area/geometry-ai', component: GeometryAiView }
    ]
  })
}

function imageFile(name = 'figure.png', type = 'image/png'): File {
  return new File([new Uint8Array(4096)], name, { type })
}

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

/** 送信された内容（fetch の呼び出し）を記録する。 */
interface Call {
  url: string
  method: string
  body: Record<string, unknown> | null
}

describe('AI 生図（作図方法と作成する図の種類）', () => {
  let fetchMock: ReturnType<typeof vi.fn>
  let calls: Call[]
  let view: Router

  beforeEach(() => {
    setActivePinia(createPinia())
    calls = []
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    // 画像のプレビュー（object URL）は jsdom に無いので用意する
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:preview'),
      revokeObjectURL: vi.fn()
    })
  })

  function setupRoutes(): void {
    fetchMock.mockImplementation((url: string, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      const body = typeof init?.body === 'string' ? JSON.parse(init.body) as Record<string, unknown> : null
      calls.push({ url: String(url), method, body })
      if (String(url).includes('/geometry/ai/options')) {
        return Promise.resolve(ok({
          enabled: true,
          assistEnabled: true,
          maxImageMb: 10,
          maxImagePixels: 1536,
          defaultCrop: 'manual',
          defaultKind: 'figure',
          approval: 'manual',
          dailyLimit: 20,
          usedToday: 0,
          notice: ''
        }))
      }
      if (String(url).includes('/geometry/ai/requests/images')) {
        return Promise.resolve(ok({
          imageToken: 'token-1', fileName: 'figure.png', mime: 'image/png',
          size: 4096, width: 1000, height: 800
        }))
      }
      if (String(url).includes('/geometry/ai/requests') && method === 'POST') {
        return Promise.resolve(ok({
          requestId: 31, requestNo: 'AIG1', status: 'QUEUED', statusLabel: '待機中（順番待ち）',
          version: 1, runPath: '/api/admin/batch/geometry-ai/requests/31/run'
        }, 'AI 生図を受け付けました。'))
      }
      if (String(url).includes('/temp-files')) {
        return Promise.resolve(ok([]))
      }
      return Promise.resolve(ok({}))
    })
  }

  async function mountView(): Promise<VueWrapper> {
    setupRoutes()
    view = router()
    await view.push('/student/geometry-ai')
    await view.isReady()
    const wrapper = mount(GeometryAiView, { global: { plugins: [view] } })
    await flushPromises()
    return wrapper
  }

  /** 画像を選んで、指定したステップまで進める。 */
  async function goToStep(wrapper: VueWrapper, step: number): Promise<void> {
    const input = wrapper.find('[data-gm-ai-file]')
    Object.defineProperty(input.element, 'files', { value: [imageFile()], configurable: true })
    await input.trigger('change')
    await flushPromises()
    for (let current = 1; current < step; current += 1) {
      await wrapper.find('[data-gm-ai-next]').trigger('click')
      await flushPromises()
    }
  }

  it('4 ステップは利用者の指定どおりの名前になっている', async () => {
    const wrapper = await mountView()
    const labels = wrapper.findAll('.gm-ai-step__label').map((node) => node.text())
    expect(labels).toEqual(['画像を追加', '読み取る範囲を指定', '作図方法を選択', '内容を確認して送信'])
  })

  it('結果プレビューは無い（一覧へ戻ってタスクを見る）', async () => {
    const wrapper = await mountView()
    expect(wrapper.find('[data-gm-ai-result]').exists()).toBe(false)
  })

  it('画像を 2 枚渡されたら知らせて、いまの画像を置き換えない', async () => {
    const wrapper = await mountView()
    const input = wrapper.find('[data-gm-ai-file]')
    Object.defineProperty(input.element, 'files', {
      value: [imageFile('a.png'), imageFile('b.png')], configurable: true
    })
    await input.trigger('change')
    await flushPromises()

    expect(wrapper.find('[data-gm-ai-file-error]').text()).toContain('1 枚だけ')
    expect(wrapper.find('[data-gm-ai-picked]').exists()).toBe(false)
  })

  it('文字だけの貼り付けは何もしない（画像のときだけ拾う）', async () => {
    const wrapper = await mountView()
    const event = new Event('paste', { bubbles: true, cancelable: true }) as ClipboardEvent
    Object.defineProperty(event, 'clipboardData', {
      value: { items: [{ kind: 'string', type: 'text/plain', getAsFile: () => null }] },
      configurable: true
    })
    window.dispatchEvent(event)
    await flushPromises()

    expect(wrapper.find('[data-gm-ai-picked]').exists()).toBe(false)
    expect(event.defaultPrevented).toBe(false)
  })

  it('スクリーンショットの貼り付けは画像として受け付ける', async () => {
    const wrapper = await mountView()
    const file = imageFile('image.png')
    const event = new Event('paste', { bubbles: true, cancelable: true }) as ClipboardEvent
    Object.defineProperty(event, 'clipboardData', {
      value: { items: [{ kind: 'file', type: 'image/png', getAsFile: () => file }] },
      configurable: true
    })
    window.dispatchEvent(event)
    await flushPromises()

    expect(wrapper.find('[data-gm-ai-picked]').exists()).toBe(true)
    expect(event.defaultPrevented).toBe(true)
  })

  it('読み取る範囲の案内は作図方法で変わる', async () => {
    const wrapper = await mountView()
    await goToStep(wrapper, 2)
    expect(wrapper.find('[data-gm-ai-crop-hint]').text()).toContain('座標軸・目盛')

    await wrapper.find('[data-gm-ai-step-link="3"]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-gm-ai-mode-option="C"] input').setValue()
    await wrapper.find('[data-gm-ai-step-link="2"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-gm-ai-crop-hint]').text()).toContain('問題文')
  })

  it('B は「作成する図の種類」を選ばせず、グラフ固定だと伝える', async () => {
    const wrapper = await mountView()
    await goToStep(wrapper, 3)
    expect(wrapper.find('[data-gm-ai-output-type]').exists()).toBe(true)

    await wrapper.find('[data-gm-ai-mode-option="B"] input').setValue()
    await flushPromises()
    expect(wrapper.find('[data-gm-ai-output-type]').exists()).toBe(false)
    expect(wrapper.find('[data-gm-ai-output-type-fixed]').text()).toContain('関数・方程式のグラフ')
  })

  it('補充項目はモードと種類で変わる（座標の範囲はグラフのときだけ）', async () => {
    const wrapper = await mountView()
    await goToStep(wrapper, 3)
    // A ＋ 自動判定: グラフの項目は出ない（上級を開いても出ない）
    await wrapper.find('[data-gm-ai-advanced-toggle]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-gm-ai-supplement="coordinateRange"]').exists()).toBe(false)

    await wrapper.find('[data-gm-ai-output-type-option="GRAPH"] input').setValue()
    await flushPromises()
    expect(wrapper.find('[data-gm-ai-supplement="coordinateRange"]').exists()).toBe(true)

    // 作図方法を変えると上級の指定は畳み直される（項目の並びが変わるため）
    await wrapper.find('[data-gm-ai-mode-option="B"] input').setValue()
    await flushPromises()
    expect(wrapper.find('[data-gm-ai-supplement="coordinateRange"]').exists()).toBe(false)
    await wrapper.find('[data-gm-ai-advanced-toggle]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-gm-ai-supplement="parameters"]').exists()).toBe(true)
    expect(wrapper.find('[data-gm-ai-supplement="reproduceFocus"]').exists()).toBe(false)
  })

  it('送信は受付だけで、当てはまらない補充を送らない（実行はバックエンド）', async () => {
    const wrapper = await mountView()
    await goToStep(wrapper, 3)

    // A の項目（上級）を入れてから C（幾何図形）へ切り替える（A の項目は送られない）
    await wrapper.find('[data-gm-ai-advanced-toggle]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-gm-ai-supplement="knownValues"]').setValue('AB = 5')
    await wrapper.find('[data-gm-ai-mode-option="C"] input').setValue()
    await flushPromises()
    await wrapper.find('[data-gm-ai-output-type-option="GEOMETRY"] input').setValue()
    await flushPromises()

    await wrapper.find('[data-gm-ai-next]').trigger('click')
    await flushPromises()
    await wrapper.find('[data-gm-ai-send]').trigger('click')
    await flushPromises()

    const created = calls.find((call) => call.url.endsWith('/geometry/ai/requests') && call.method === 'POST')
    expect(created).toBeDefined()
    expect(created?.body?.mode).toBe('C')
    expect(created?.body?.resultType).toBe('GEOMETRY')
    const supplements = created?.body?.supplements as Record<string, unknown>
    // 当てはまらない項目（A の「既知の値」）は入らない
    expect(supplements.knownValues).toBeUndefined()
    // 元の名前とラベルは既定で「残す」
    expect(supplements.keepLabels).toBe(true)
    // B の項目・グラフの項目も入らない
    expect(supplements.formulaCorrection).toBeUndefined()
    expect(supplements.viewRange).toBeUndefined()
    // **起動 API は呼ばない**（バックエンドの働き手が実行する）
    expect(calls.some((call) => call.url.includes('/api/admin/batch/geometry-ai'))).toBe(false)
    // 図形管理の一覧へ戻る
    expect(view.currentRoute.value.path).toBe('/student/geometry')
  })

  it('画像以外と大きすぎる画像は受け付けない（理由を日本語で出す）', async () => {
    const wrapper = await mountView()
    const input = wrapper.find('[data-gm-ai-file]')

    Object.defineProperty(input.element, 'files', {
      value: [new File([new Uint8Array(10)], 'note.txt', { type: 'text/plain' })], configurable: true
    })
    await input.trigger('change')
    await flushPromises()
    expect(wrapper.find('[data-gm-ai-file-error]').text()).toContain('PNG・JPEG・WebP')

    // 設定の上限（10MB）を超える画像
    const big = new File([new Uint8Array(11 * 1024 * 1024)], 'big.png', { type: 'image/png' })
    Object.defineProperty(input.element, 'files', { value: [big], configurable: true })
    await input.trigger('change')
    await flushPromises()
    expect(wrapper.find('[data-gm-ai-file-error]').text()).toContain('10 MB まで')
    expect(wrapper.find('[data-gm-ai-picked]').exists()).toBe(false)
  })

  it('読み取る範囲は 8 方向のハンドルで変えられる', async () => {
    const wrapper = await mountView()
    await goToStep(wrapper, 2)
    expect(wrapper.find('[data-gm-ai-crop-size]').text()).toContain('横 100%')

    // jsdom では要素に大きさが無いので、実寸を差し替える
    const canvas = wrapper.find('[data-gm-ai-crop-canvas]')
    ;(canvas.element as HTMLElement).getBoundingClientRect = () => ({
      x: 0, y: 0, top: 0, left: 0, right: 400, bottom: 300, width: 400, height: 300,
      toJSON: () => ({ width: 400, height: 300 })
    }) as DOMRect

    const handle = wrapper.find('[data-gm-ai-crop-handle="w"]')
    await handle.trigger('pointerdown', { clientX: 200, clientY: 150 })
    window.dispatchEvent(new MouseEvent('pointermove', { clientX: 240, clientY: 150, bubbles: true }))
    window.dispatchEvent(new MouseEvent('pointerup', { bubbles: true }))
    await flushPromises()

    // 左の辺を右へ 10%（40px / 400px）動かしたので、横は 90% になる
    expect(wrapper.find('[data-gm-ai-crop-size]').text()).toContain('横 90%')
    expect(wrapper.find('[data-gm-ai-crop-size]').text()).toContain('縦 100%')
  })

  it('日次の上限に達していたら送れない（理由を日本語で出す）', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (String(url).includes('/geometry/ai/options')) {
        return Promise.resolve(ok({
          enabled: true, assistEnabled: true, maxImageMb: 10, maxImagePixels: 1536,
          defaultCrop: 'manual', defaultKind: 'figure', approval: 'manual',
          dailyLimit: 20, usedToday: 20,
          notice: '本日の AI 生図は上限（20 回）に達しました。明日またお試しください。'
        }))
      }
      return Promise.resolve(ok({}))
    })
    view = router()
    await view.push('/student/geometry-ai')
    await view.isReady()
    const wrapper = mount(GeometryAiView, { global: { plugins: [view] } })
    await flushPromises()

    expect(wrapper.find('[data-gm-ai-notice]').text()).toContain('上限（20 回）')
    await goToStep(wrapper, 4)
    expect(wrapper.find('[data-gm-ai-send]').attributes('disabled')).toBeDefined()
  })

  it('設定で無効なら案内を出して送れない', async () => {
    fetchMock.mockImplementation((url: string) => {
      if (String(url).includes('/geometry/ai/options')) {
        return Promise.resolve(ok({
          enabled: false, assistEnabled: false, maxImageMb: 10, maxImagePixels: 1536,
          defaultCrop: 'manual', defaultKind: 'figure', approval: 'manual',
          dailyLimit: 0, usedToday: 0,
          notice: '「AI 生図」はシステム設定で無効になっています。'
        }))
      }
      return Promise.resolve(ok({}))
    })
    view = router()
    await view.push('/student/geometry-ai')
    await view.isReady()
    const wrapper = mount(GeometryAiView, { global: { plugins: [view] } })
    await flushPromises()

    expect(wrapper.find('[data-gm-ai-notice]').text()).toContain('無効になっています')
    await goToStep(wrapper, 4)
    expect(wrapper.find('[data-gm-ai-send]').attributes('disabled')).toBeDefined()
  })

  it('確認のステップに補充の指定と作図方法が出る', async () => {
    const wrapper = await mountView()
    await goToStep(wrapper, 4)

    const summary = wrapper.find('[data-gm-ai-summary]').text()
    expect(summary).toContain('作図方法')
    expect(summary).toContain('画像をもとに再現')
    expect(summary).toContain('自動判定')
    const supplements = wrapper.find('[data-gm-ai-supplements]').text()
    expect(supplements).toContain('再現の重点')
    expect(supplements).toContain('数学的な関係を優先')
    expect(supplements).toContain('元の名前とラベル')
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SystemSettingsView from '@/views/admin/system-settings/SystemSettingsView.vue'

/**
 * AIモデルページの【接続テスト】ボタン。
 *
 * ボタンは URL の項目に付き（千問 / 豆包 / DeepSeek / OpenAI の 4 つ）、押すと
 * `POST /api/admin/setting/testAi` へ**保存前の入力値**（モデル1・API Key・URL）を送る。
 * 音声認識（STT）の 2 つ（Google Speech-to-Text / Alibaba Paraformer-Realtime-V2）は音声を 1 回送る
 * 別の入口（`POST /api/admin/setting/testStt`）へ行く。
 * 画面は結果をボタンの隣とトーストに出す:
 * ・接続できた（ok=true）      → 緑（is-success）＋「接続できました（120ms）」＋モデル名
 * ・AI に断られた（ok=false）   → 赤（is-error）＋理由
 * ・設定の不備など 400 の失敗    → 赤（is-error）＋理由
 * BigModel / 智譜 OCR は接続テストの対象外なのでボタンを出さない。
 */
function jsonResponse(data: unknown): Response {
  return new Response(
    JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
}

/** 設定ページのフェッチを差し替える（testAi だけ差し替え、他は設定値）。 */
function setup(options: {
  testAi?: () => Promise<Response>
  init?: Record<string, string>
} = {}): { wrapper: VueWrapper; testCalls: Record<string, unknown>[] } {
  const testCalls: Record<string, unknown>[] = []
  const fetchMock = vi.fn((url: string, request?: RequestInit) => {
    if (String(url).includes('/testAi') || String(url).includes('/testStt')) {
      testCalls.push({ url: String(url), body: JSON.parse(String(request?.body)) as Record<string, unknown> })
      return options.testAi
        ? options.testAi()
        : Promise.resolve(jsonResponse({ ok: true, provider: 'qwen', model: 'qwen3.7-flash', message: '接続できました（120ms）' }))
    }
    return Promise.resolve(jsonResponse({ settings: options.init ?? {} }))
  })
  vi.stubGlobal('fetch', fetchMock)
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(SystemSettingsView, { global: { plugins: [pinia] }, attachTo: document.body })
  return { wrapper, testCalls }
}

const modelsPanel = (): HTMLElement => document.querySelector('[data-panel="models"]') as HTMLElement
const button = (provider: string): HTMLButtonElement =>
  modelsPanel().querySelector(`.setting-api-test[data-provider="${provider}"]`) as HTMLButtonElement
const status = (provider: string): HTMLElement =>
  document.getElementById(`setting_test_status_${provider}`) as HTMLElement
const toast = (): HTMLElement => document.getElementById('settingToast') as HTMLElement

async function open(): Promise<void> {
  await flushPromises()
  await flushPromises()
}

describe('AIモデルページ：【接続テスト】', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('チャット系の 4 つと音声認識（STT）の 2 つにボタンを出す（BigModel OCR には出さない）', async () => {
    const context = setup()
    wrapper = context.wrapper
    await open()

    const providers = [...modelsPanel().querySelectorAll('.setting-api-test')].map((item) => item.getAttribute('data-provider'))
    expect(providers).toEqual(['qwen', 'doubao', 'deepseek', 'openai', 'google-stt', 'alibaba-stt'])
    // BigModel / 智譜 OCR のタブにはボタンが無い（画像を送る別形式なので接続テストの対象外）
    expect(modelsPanel().textContent).toContain('BigModel')
    expect(providers).not.toContain('bigmodel')
  })

  it('押すと保存前の入力値を送り、成功は緑で出す', async () => {
    const context = setup({ init: { qwenModel: 'qwen3.7-flash', qwenApiKey: 'secret-key', qwenUrl: 'https://example.com/v1/chat/completions' } })
    wrapper = context.wrapper
    await open()

    button('qwen').click()
    await flushPromises()

    // 送る中身は画面の入力値（未保存でも試せる）
    expect(context.testCalls[0]).toEqual({
      url: expect.stringContaining('/testAi'),
      body: {
        provider: 'qwen',
        model: 'qwen3.7-flash',
        apiKey: 'secret-key',
        url: 'https://example.com/v1/chat/completions'
      }
    })
    expect(status('qwen').textContent).toContain('接続できました')
    expect(status('qwen').className).toContain('is-success')
    expect(toast().textContent).toContain('qwen3.7-flash')
    // テスト中はボタンを押せない
    expect(button('qwen').disabled).toBe(false)
  })

  it('STT の【接続テスト】は testStt へ、AIモデルページのタブの値を送る', async () => {
    const context = setup({
      init: {
        googleSttModel: 'latest_long',
        googleSttApiKey: 'google-key',
        googleSttUrl: 'https://speech.googleapis.com/v1/speech:recognize'
      },
      testAi: () => Promise.resolve(jsonResponse({ ok: true, message: '接続できました', model: 'latest_long' }))
    })
    wrapper = context.wrapper
    await open()

    button('google-stt').click()
    await flushPromises()

    expect(context.testCalls[0]).toEqual({
      url: expect.stringContaining('/testStt'),
      body: {
        provider: 'google',
        model: 'latest_long',
        apiKey: 'google-key',
        url: 'https://speech.googleapis.com/v1/speech:recognize'
      }
    })
    expect(status('google-stt').className).toContain('is-success')

    button('alibaba-stt').click()
    await flushPromises()
    expect((context.testCalls[1].body as Record<string, unknown>).provider).toBe('alibaba')
  })

  it('押す前と押した後でボタンの見た目が変わらない（アイコンは SVG スプライトのまま）', async () => {
    const context = setup()
    wrapper = context.wrapper
    await open()

    const before = button('qwen').innerHTML
    expect(before).toContain('<svg class="icon"')   // 描画時に FontAwesome からスプライトへ変換されている
    expect(before).toContain('接続テスト')

    button('qwen').click()
    await flushPromises()

    const after = button('qwen').innerHTML
    // テスト後に元へ戻すときも同じ見た目に戻す（アイコンが消えたり行の高さが変わったりしない）
    expect(after).not.toContain('fas fa-')
    expect(after).toBe(before)
  })

  it('保存ボタンも押した後で見た目が変わらない', async () => {
    const context = setup()
    wrapper = context.wrapper
    await open()

    const save = document.getElementById('settingSaveBtn') as HTMLButtonElement
    const before = save.innerHTML
    // 画面（Vue）が描くボタンはスコープ用の属性が付くので、アイコンは <svg> の有無で見る
    expect(before).toContain('<svg')
    expect(before).toContain('設定を保存')

    save.click()
    await flushPromises()

    expect(save.innerHTML).not.toContain('fas fa-')
    expect(save.innerHTML).toContain('<svg')
    expect(save.innerHTML).toContain('設定を保存')
  })

  it('AI に断られた（ok=false）ときは赤で理由を出す', async () => {
    const context = setup({
      testAi: () => Promise.resolve(jsonResponse({
        ok: false, provider: 'deepseek', model: 'deepseek-v4-flash',
        message: 'AI がリクエストを受け付けませんでした（HTTP 401）。API Key とモデル名を確認してください。'
      }))
    })
    wrapper = context.wrapper
    await open()

    button('deepseek').click()
    await flushPromises()

    expect(status('deepseek').textContent).toContain('HTTP 401')
    expect(status('deepseek').className).toContain('is-error')
    expect(toast().textContent).toContain('API Key とモデル名を確認してください')
  })

  it('設定の不備（400 など）も赤で理由を出す', async () => {
    const context = setup({
      testAi: () => Promise.resolve(new Response(
        JSON.stringify({ success: false, code: 'VALIDATION_ERROR', message: '接続テストには API Key が必要です。', data: null }),
        { status: 400, headers: { 'Content-Type': 'application/json' } }
      ))
    })
    wrapper = context.wrapper
    await open()

    button('openai').click()
    await flushPromises()

    expect(status('openai').textContent).toContain('API Key が必要です')
    expect(status('openai').className).toContain('is-error')
  })
})

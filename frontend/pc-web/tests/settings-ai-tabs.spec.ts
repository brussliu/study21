import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SystemSettingsView from '@/views/admin/system-settings/SystemSettingsView.vue'

/**
 * システム設定の分類ナビに **【AI生図】【AI授業記録】の 2 つの分頁**があり、
 * それぞれの設定項目が**その分頁の中だけ**に出ること（どの分類でも下に出ていた不具合の再発防止）。
 *
 * 仕組み: 設定ランタイム（`study2SettingRuntime.ts`）のカテゴリに `external: true` を付け、
 * パネルには空のマウント点（`[data-external-slot]`）だけを出して、項目の描画は
 * `GeometryAiSettingsSection.vue` / `ClassroomAiSettingsSection.vue` が **Teleport** で行う。
 * 保存・再読込は registerSection（画面全体の【設定を保存】）で従来どおり参加する。
 *
 * パネルの表示切替はランタイムの既存ロジック（`data-panel` の `hidden`）をそのまま使う。
 * jsdom はレイアウトを持たないので、ここでは「どのパネルの中に居るか」と `hidden` で確かめ、
 * 実機の見え方は `tmp/e2e/e2e-classroom-front.mjs` のスクリーンショットで確かめる。
 */
const GEOMETRY_KEYS = [
  'geometryAiEnabled', 'geometryAiProvider', 'geometryAiMaxImageMb', 'geometryAiDefaultCrop',
  'geometryAiDefaultKind', 'geometryAiApproval', 'geometryAiInstructionTemplate'
]
const CLASSROOM_KEYS = [
  'classroomAiEnabled', 'classroomAiSttProvider', 'classroomAiChunkSeconds',
  'classroomAiTriggerIntervalMinutes', 'classroomAiTriggerMinChars',
  'classroomAiTriggerKeywords', 'classroomAiMaxRecordingMinutes'
]

function jsonResponse(data: unknown): Response {
  return new Response(
    JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
}

/** 設定ページを実 DOM に載せる（ランタイムが document から要素を引くため attachTo が要る）。 */
function setup(options: { init?: Record<string, string> } = {}): {
  wrapper: VueWrapper
  saved: { body: Record<string, unknown> | null }
  setInit: (settings: Record<string, string>) => void
} {
  const saved: { body: Record<string, unknown> | null } = { body: null }
  let init = options.init ?? {}
  const fetchMock = vi.fn((url: string, request?: RequestInit) => {
    const body = request?.body === undefined ? null : JSON.parse(String(request.body)) as Record<string, unknown>
    if (String(url).includes('/saveSettings')) {
      saved.body = body
      return Promise.resolve(jsonResponse({ settings: body?.settings ?? {} }))
    }
    return Promise.resolve(jsonResponse({ settings: init }))
  })
  vi.stubGlobal('fetch', fetchMock)
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(SystemSettingsView, { global: { plugins: [pinia] }, attachTo: document.body })
  return { wrapper, saved, setInit: (settings) => { init = settings } }
}

const nav = (): HTMLElement => document.getElementById('settingCategoryNav') as HTMLElement
const panel = (id: string): HTMLElement => document.querySelector(`[data-panel="${id}"]`) as HTMLElement
const categoryButton = (id: string): HTMLElement =>
  nav().querySelector(`[data-category="${id}"]`) as HTMLElement

/** 分類を切り替える（ランタイムの委譲クリックをそのまま使う）。 */
async function selectCategory(id: string): Promise<void> {
  categoryButton(id).click()
  await flushPromises()
}

describe('システム設定：AI生図 / AI授業記録の分頁', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    document.body.innerHTML = ''
    // 注意: `window.__study21SystemSettings` は消さない。
    // 画面（SystemSettingsView）はこれを mount するので、消すと設定ページが組み立てられない
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  async function open(options: { init?: Record<string, string> } = {}): Promise<ReturnType<typeof setup>> {
    const context = setup(options)
    wrapper = context.wrapper
    await flushPromises()
    return context
  }

  it('分類ナビに【図形管理】【授業録音】が出る（既存の分類も残る）', async () => {
    await open()

    expect(categoryButton('geometry_ai').textContent).toContain('図形管理')
    expect(categoryButton('classroom_ai').textContent).toContain('授業録音')
    // 既存の分類はそのまま
    expect(categoryButton('models').textContent).toContain('AIモデル')
    expect(nav().querySelectorAll('[data-category]').length).toBeGreaterThan(2)
  })

  it('パネルも作られ、最初は【AIモデル】だけが見えている', async () => {
    await open()

    expect(panel('geometry_ai')).not.toBeNull()
    expect(panel('classroom_ai')).not.toBeNull()
    expect(panel('models').hidden).toBe(false)
    expect(panel('geometry_ai').hidden).toBe(true)
    expect(panel('classroom_ai').hidden).toBe(true)
  })

  it('分頁のパネルには外部コンポーネントのマウント点がある', async () => {
    await open()

    const geometrySlot = panel('geometry_ai').querySelector('[data-external-slot="geometry_ai"]')
    const classroomSlot = panel('classroom_ai').querySelector('[data-external-slot="classroom_ai"]')
    expect(geometrySlot).not.toBeNull()
    expect(classroomSlot).not.toBeNull()
    // 項目はマウント点の中だけ（ランタイムがカタログから描いた項目は無い＝バッチ専用キーを出さない）
    expect(panel('geometry_ai').querySelector(':scope > .setting-field')).toBeNull()
    expect(panel('classroom_ai').querySelector(':scope > .setting-field')).toBeNull()
    expect(geometrySlot?.querySelector('.setting-field')).not.toBeNull()
  })

  it('【AI生図】を選ぶと AI生図の設定だけが出る（他の分類には出ない）', async () => {
    await open()
    await selectCategory('geometry_ai')

    expect(panel('geometry_ai').hidden).toBe(false)
    expect(panel('models').hidden).toBe(true)
    expect(panel('classroom_ai').hidden).toBe(true)
    // 中身は AI 生図の設定（Teleport 済み）
    expect(panel('geometry_ai').querySelector('[data-gm-ai-settings]')).not.toBeNull()
    expect(panel('geometry_ai').querySelector('[data-gm-set-model]')).not.toBeNull()
    // 授業録音の設定はこの分頁には出ない
    expect(panel('geometry_ai').querySelector('[data-cr-set]')).toBeNull()
  })

  it('【AI授業記録】を選ぶと授業録音の設定だけが出る（他の分類には出ない）', async () => {
    await open()
    await selectCategory('classroom_ai')

    expect(panel('classroom_ai').hidden).toBe(false)
    expect(panel('models').hidden).toBe(true)
    expect(panel('geometry_ai').hidden).toBe(true)
    expect(panel('classroom_ai').querySelector('[data-cr-set]')).not.toBeNull()
    expect(panel('classroom_ai').querySelector('[data-cr-set-stt-provider]')).not.toBeNull()
    expect(panel('classroom_ai').querySelector('[data-gm-ai-settings]')).toBeNull()
  })

  it('【AIモデル】など他の分類には、どちらの設定も出ない（今回の不具合の核心）', async () => {
    await open()

    for (const id of ['models', 'translation', 'batchai']) {
      await selectCategory(id)
      expect(panel(id).querySelector('[data-gm-ai-settings]')).toBeNull()
      expect(panel(id).querySelector('[data-cr-set]')).toBeNull()
      expect(panel(id).querySelector('[data-external-slot]')).toBeNull()
    }

    // 画面全体で見ても、それぞれ 1 つだけ（分類の外に置かれていない）
    expect(document.querySelectorAll('[data-gm-ai-settings]')).toHaveLength(1)
    expect(document.querySelectorAll('[data-cr-set]')).toHaveLength(1)
    expect(panel('geometry_ai').querySelectorAll('[data-cr-set]')).toHaveLength(0)
    expect(panel('classroom_ai').querySelectorAll('[data-gm-ai-settings]')).toHaveLength(0)
  })

  it('切って戻っても中身と入力値が残る', async () => {
    await open()
    await selectCategory('classroom_ai')
    const interval = panel('classroom_ai').querySelector('[data-cr-set-interval]') as HTMLInputElement
    interval.value = '9'
    interval.dispatchEvent(new Event('input', { bubbles: true }))

    await selectCategory('models')
    expect(panel('models').querySelector('[data-cr-set]')).toBeNull()

    await selectCategory('classroom_ai')
    expect(panel('classroom_ai').querySelector('[data-gm-ai-settings]')).toBeNull()
    const again = panel('classroom_ai').querySelector('[data-cr-set-interval]') as HTMLInputElement
    expect(again.value).toBe('9')
  })

  it('全体の【設定を保存】で AI生図と AI授業記録の両方のキーが送られる', async () => {
    // 有効／無効（画面に出さないキー）は「読み込んだ値」をそのまま送り返す
    const { saved } = await open({ init: { geometryAiEnabled: 'true', classroomAiEnabled: 'true' } })
    await selectCategory('classroom_ai')
    const interval = panel('classroom_ai').querySelector('[data-cr-set-interval]') as HTMLInputElement
    interval.value = '9'
    interval.dispatchEvent(new Event('input', { bubbles: true }))

    ;(document.getElementById('settingSaveBtn') as HTMLButtonElement).click()
    await flushPromises()

    const settings = saved.body?.settings as Record<string, string>
    expect(settings).toBeTruthy()
    for (const key of [...GEOMETRY_KEYS, ...CLASSROOM_KEYS]) {
      expect(Object.keys(settings), `${key} が保存に入っていない`).toContain(key)
    }
    expect(settings.classroomAiTriggerIntervalMinutes).toBe('9')
  })

  it('【再読込】でサーバーの値が両方の分頁に反映される（Teleport が外れない）', async () => {
    const context = await open()
    await selectCategory('geometry_ai')
    expect(panel('geometry_ai').querySelector('[data-gm-ai-settings]')).not.toBeNull()

    context.setInit({
      geometryAiMaxImageMb: '25',
      classroomAiEnabled: 'true',
      classroomAiChunkSeconds: '30',
      classroomAiSttProvider: 'google',
      classroomAiTriggerIntervalMinutes: '8',
      classroomAiMaxRecordingMinutes: '60'
    })
    ;(document.getElementById('settingReloadBtn') as HTMLButtonElement).click()
    await flushPromises()

    // パネルが作り直されても、外部コンポーネントは新しいマウント点へ入り直す
    expect(panel('geometry_ai').querySelector('[data-gm-ai-settings]')).not.toBeNull()
    expect(panel('classroom_ai').querySelector('[data-cr-set]')).not.toBeNull()
    // 画像の最大サイズは数値欄（旧スライダーをやめた）
    expect((panel('geometry_ai').querySelector('[data-gm-set-max-size]') as HTMLInputElement).value)
      .toBe('25')
    expect((panel('classroom_ai').querySelector('[data-cr-set-stt-provider]') as HTMLSelectElement).value)
      .toBe('google')
    expect((panel('classroom_ai').querySelector('[data-cr-set-interval]') as HTMLInputElement).value)
      .toBe('8')
    expect((panel('classroom_ai').querySelector('[data-cr-set-max-minutes]') as HTMLInputElement).value)
      .toBe('60')
  })
})

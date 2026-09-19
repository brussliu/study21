import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import GeometryAiView from '@/views/geometry/GeometryAiView.vue'

/**
 * 【作図方法を選択】（ステップ 3）の**画面構成**（利用者の指示 2026-09-19）。
 *
 * <p>「内容が多く、層が無い」という指摘への作り直し。確かめる接縫:</p>
 * <ol>
 *   <li>A〜D は**タブ**（1 行に 4 つ）。選択肢そのものは今までどおり「ラジオ」なので、
 *       キーボードでも選べる（`data-gm-ai-mode-option` は従来のフックを残す）</li>
 *   <li>方法の説明は**選択中の 1 つだけ**をタブの下に出す（4 枚分を常に出さない＝縦が縮む）</li>
 *   <li>下は 3 つの**節**に分ける: ① 作成する図の種類（必須）／② この方法の指定（任意）／
 *       ③ すべての方法に共通（任意）</li>
 *   <li>「作成する図の種類」の説明も**選択中の 1 つだけ**を出す</li>
 *   <li>節の中の入力（補充・上級・補足要求・名前とラベル）は**従来のフックのまま**</li>
 * </ol>
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

function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

describe('AI 生図【作図方法を選択】の画面構成', () => {
  let fetchMock: ReturnType<typeof vi.fn>
  let view: Router

  beforeEach(() => {
    setActivePinia(createPinia())
    fetchMock = vi.fn()
    vi.stubGlobal('fetch', fetchMock)
    vi.stubGlobal('URL', {
      ...URL,
      createObjectURL: vi.fn(() => 'blob:preview'),
      revokeObjectURL: vi.fn()
    })
    fetchMock.mockImplementation((url: string) => {
      if (String(url).includes('/geometry/ai/options')) {
        return Promise.resolve(ok({
          enabled: true, assistEnabled: true, maxImageMb: 10, maxImagePixels: 1536, defaultCrop: 'manual',
          defaultKind: 'figure', approval: 'manual', dailyLimit: 20, usedToday: 0, notice: ''
        }))
      }
      return Promise.resolve(ok({}))
    })
  })

  async function mountStep3(): Promise<VueWrapper> {
    view = router()
    await view.push('/student/geometry-ai')
    await view.isReady()
    const wrapper = mount(GeometryAiView, { global: { plugins: [view] } })
    await flushPromises()
    const input = wrapper.find('[data-gm-ai-file]')
    Object.defineProperty(input.element, 'files', { value: [imageFile()], configurable: true })
    await input.trigger('change')
    await flushPromises()
    for (let step = 1; step < 3; step += 1) {
      await wrapper.find('[data-gm-ai-next]').trigger('click')
      await flushPromises()
    }
    return wrapper
  }

  it('A〜D はタブとして 1 行に並び、選択肢はラジオのまま（キーボードで選べる）', async () => {
    const wrapper = await mountStep3()

    const tabs = wrapper.findAll('[data-gm-ai-mode-option]')
    expect(tabs).toHaveLength(4)
    expect(tabs.map((tab) => tab.attributes('data-gm-ai-mode-option'))).toEqual(['A', 'B', 'C', 'D'])
    // タブの中身は今までどおりラジオ（同じ name で 1 つだけ選べる）
    for (const tab of tabs) {
      const input = tab.find('input[type="radio"]')
      expect(input.exists()).toBe(true)
      expect(input.attributes('name')).toBe('gm-ai-mode')
    }
    // タブの並び（タブ列）が 1 つだけある
    expect(wrapper.findAll('[data-gm-ai-mode-tabs]')).toHaveLength(1)
    // 選ばれている方法が分かる（既定は A）
    expect(wrapper.find('[data-gm-ai-mode-option="A"]').classes()).toContain('is-active')
    expect(wrapper.find('[data-gm-ai-mode-option="C"]').classes()).not.toContain('is-active')
  })

  it('方法の説明は選択中の 1 つだけをタブの下に出す', async () => {
    const wrapper = await mountStep3()

    const summaries = wrapper.findAll('[data-gm-ai-mode-summary]')
    expect(summaries).toHaveLength(1)
    expect(summaries[0].text()).toContain('画像から読み取った図形・式・文章をもとに')
    // 選んでいない方法の説明は出さない（4 枚分を常に出さない）
    expect(summaries[0].text()).not.toContain('問題文などの文章にある条件だけから')

    await wrapper.find('[data-gm-ai-mode-option="C"] input').setValue()
    await flushPromises()

    const updated = wrapper.findAll('[data-gm-ai-mode-summary]')
    expect(updated).toHaveLength(1)
    expect(updated[0].text()).toContain('問題文などの文章にある条件だけから')
    expect(updated[0].text()).not.toContain('画像から読み取った図形・式・文章をもとに')
    expect(wrapper.find('[data-gm-ai-mode-option="C"]').classes()).toContain('is-active')
  })

  it('下は「種類 → この方法の指定 → 全方法共通」の 3 節に分かれる', async () => {
    const wrapper = await mountStep3()

    const sections = wrapper.findAll('[data-gm-ai-section]')
    expect(sections.map((section) => section.attributes('data-gm-ai-section')))
      .toEqual(['output-type', 'method', 'common'])

    // 節は見出しを持つ（番号つき・必須か任意かが分かる）
    const outputType = wrapper.get('[data-gm-ai-section="output-type"]')
    expect(outputType.get('[data-gm-ai-section-title]').text()).toContain('作成する図の種類')
    expect(outputType.get('[data-gm-ai-section-tag]').text()).toBe('必須')
    const method = wrapper.get('[data-gm-ai-section="method"]')
    expect(method.get('[data-gm-ai-section-title]').text()).toContain('この方法の指定')
    expect(method.get('[data-gm-ai-section-tag]').text()).toBe('任意')
    const common = wrapper.get('[data-gm-ai-section="common"]')
    expect(common.get('[data-gm-ai-section-title]').text()).toContain('すべての方法に共通')

    // 入力はそれぞれの節に入る（従来のフックはそのまま）
    expect(method.find('[data-gm-ai-supplement-option="reproduceFocus:MATH_FIRST"]').exists()).toBe(true)
    expect(method.find('[data-gm-ai-advanced-toggle]').exists()).toBe(true)
    expect(common.find('[data-gm-ai-note]').exists()).toBe(true)
    expect(common.find('[data-gm-ai-keep-labels]').exists()).toBe(true)
  })

  it('「作成する図の種類」の説明も選択中の 1 つだけを出す', async () => {
    const wrapper = await mountStep3()

    const help = wrapper.get('[data-gm-ai-output-type-help]')
    expect(help.text()).toContain('内容を見て AI が決めます')
    expect(help.text()).not.toContain('点・線・三角形・円などの図形を作ります')

    await wrapper.find('[data-gm-ai-output-type-option="GRAPH"] input').setValue()
    await flushPromises()

    const updated = wrapper.get('[data-gm-ai-output-type-help]')
    expect(updated.text()).toContain('関数や方程式のグラフを作ります')
    expect(updated.text()).not.toContain('内容を見て AI が決めます')
    // 種類の選択肢は 4 つ（従来のフックのまま）
    expect(wrapper.findAll('[data-gm-ai-output-type-option]')).toHaveLength(4)
  })

  it('B を選ぶと種類の節は「グラフ固定」の案内になり、確認の案内は出さない', async () => {
    const wrapper = await mountStep3()

    await wrapper.find('[data-gm-ai-mode-option="B"] input').setValue()
    await flushPromises()

    expect(wrapper.find('[data-gm-ai-output-type-option]').exists()).toBe(false)
    const fixed = wrapper.get('[data-gm-ai-output-type-fixed]')
    expect(fixed.text()).toContain('関数・方程式のグラフ')
    // 節そのものは残る（種類の話をしていることが分かる）
    expect(wrapper.findAll('[data-gm-ai-section="output-type"]')).toHaveLength(1)
  })

  it('方法を変えても入力は残り、当てはまらない項目だけが消える（従来どおり）', async () => {
    const wrapper = await mountStep3()

    await wrapper.find('[data-gm-ai-note]').setValue('点の名前は A・B・C のまま')
    await wrapper.find('[data-gm-ai-supplement-option="reproduceFocus:MATH_FIRST"] input').setValue()
    await wrapper.find('[data-gm-ai-mode-option="C"] input').setValue()
    await flushPromises()

    expect((wrapper.get('[data-gm-ai-note]').element as HTMLTextAreaElement).value)
      .toBe('点の名前は A・B・C のまま')
    // A の項目（再現の重点）は C では出ない
    expect(wrapper.find('[data-gm-ai-supplement-option="reproduceFocus:MATH_FIRST"]').exists()).toBe(false)
    // C の項目（作図の目標）が出る
    expect(wrapper.findAll('[data-gm-ai-supplement-option^="goal:"]').length).toBeGreaterThan(0)
  })
})

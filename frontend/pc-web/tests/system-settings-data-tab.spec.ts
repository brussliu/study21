import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SystemSettingsView from '@/views/admin/system-settings/SystemSettingsView.vue'
import AiDataSchemaPanel from '@/features/system-settings/AiDataSchemaPanel.vue'
import {
  cancelAiDataSchemaRender,
  renderAllAiDataSchemas
} from '@/features/system-settings/aiDataSchemaPanel'

/**
 * 設定ページ【日本語単語AI】の **Data TAB**（AI 出力データ構造）。
 *
 * 設定ページの各バッチ区画は素の HTML で組み立てるので、Data TAB は
 * 「マウント点（`data-ai-data-schema-slot`）を出す」ところまでをランタイムが持ち、
 * 中身は素の DOM（`aiDataSchemaPanel.ts`）が描く。
 *
 * ここではその**配線**を確かめる（実寸やスキーマの中身はサーバー側の DTO とそのテストが見る）:
 * 1. 4 つの区画に `Data` タブがある
 * 2. 各区画が**自分のバッチコード**を Data TAB に渡す（サーバーへその DTO を引きに行く）
 * 3. Data TAB は項目を持たない（マウント点だけを出す）
 * 4. 描画の再試行タイマーが、画面を離れた後に暴れない
 */
describe('設定ページ：日本語単語AI の Data TAB', () => {
  const runtime = readFileSync(
    resolve(process.cwd(), 'src/features/system-settings/study2SettingRuntime.ts'), 'utf8'
  )
  /** 日本語単語AI のカテゴリ定義（sections を持つ 1 か所）。 */
  const category = runtime.slice(
    runtime.indexOf("id: 'japanese_word_ai'"),
    runtime.indexOf("id: 'japanese_word_ai'") + 4000
  )

  it('4 つのバッチ区画が Data タブを持ち、自分のバッチコードを渡す', () => {
    // tabs の並びは 基本設定 → System Prompt → User Prompt → Data → その他
    for (const [section, task] of [
      ['detail', 'batC41'],
      ['problem_c', 'batC42'],
      ['problem_d', 'batC43'],
      ['problem_e', 'batC44']
    ] as const) {
      const start = category.indexOf(`id:'${section}'`)
      expect(start, `${section} の区画`).toBeGreaterThanOrEqual(0)
      const block = category.slice(start, category.indexOf('{ id:', start + 1) === -1
        ? category.length
        : category.indexOf('{ id:', start + 1))

      expect(block).toContain(`tabs:['基本設定','System Prompt','User Prompt','Data','その他']`)
      expect(block).toContain(`dataTask:'${task}'`)
    }
  })

  it('Data タブはマウント点だけを出す（項目は出さない）', () => {
    // 区画側は空のマウント点を出し、中身は素の DOM で描く。
    // 属性名は Vue 側の部品（AiDataSchemaPanel.vue の data-ai-data-schema）と**分ける**
    // （同じ名前にすると、既にその部品を使っている AI生図の区画まで描き直して壊れる）
    expect(runtime).toContain('data-ai-data-schema-slot="' + "' + escapeHtml(category.dataTask)")
    expect(runtime).toContain("tab === 'Data' && category.dataTask")
    expect(runtime).toContain('dataTask: section.dataTask')
  })

  it('Data タブの中身は、素の DOM で描く（2 つ目の Vue アプリを載せない）', () => {
    const panel = readFileSync(
      resolve(process.cwd(), 'src/features/system-settings/aiDataSchemaPanel.ts'), 'utf8'
    )
    // マウント点を探して描く
    expect(panel).toContain("querySelectorAll<HTMLElement>('[data-ai-data-schema-slot]')")
    expect(panel).toContain('loadAiResponseSchema(task)')
    // スキーマの変換は Vue 側と同じ純関数を使う（DTO を直せば両方変わる）
    expect(panel).toContain('fieldsOfSchema')
    expect(panel).toContain('flattenSchemaFields')
    // **2 つ目の Vue アプリを載せない**（設定ページの再描画とぶつかって落ちるため）
    expect(panel).not.toContain('createApp')

    // 設定ページの描画の最後に呼ばれる
    expect(runtime).toContain('renderAllAiDataSchemas()')
  })
})

/**
 * 設定ページ【日本語単語AI】の Data TAB を、**実際に組み立てた DOM** で確かめる。
 *
 * 上の describe はソースの配線を見るだけなので、「画面に出ているか」は分からない。
 * ここは設定ページ（`SystemSettingsView` → 設定ランタイム）を jsdom に載せ、
 * 4 つの区画それぞれのタブに `Data` が出ていること・押すとそのバッチの DTO を
 * サーバーへ引きに行くことまで見る。
 */
describe('設定ページ：日本語単語AI の Data TAB（実 DOM）', () => {
  let wrapper: VueWrapper | null = null

  function jsonResponse(data: unknown): Response {
    return new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )
  }

  beforeEach(() => {
    document.body.innerHTML = ''
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    cancelAiDataSchemaRender()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  /** 設定ページを実 DOM に載せる（ランタイムが document から要素を引くため attachTo が要る）。 */
  async function openSettings(): Promise<void> {
    const fetchMock = vi.fn((url: string) => {
      const target = String(url)
      if (target.includes('/ai-response-schema')) {
        const task = new URL(target, 'http://localhost').searchParams.get('task') ?? ''
        // 「どのバッチの DTO を引いたか」が分かるように、項目名と DTO 名へバッチコードを入れる
        return Promise.resolve(jsonResponse({
          dto: `Dto_${task}`,
          schema: {
            type: 'object',
            properties: { [`field_of_${task}`]: { type: 'string', description: '例' } },
            required: [`field_of_${task}`]
          }
        }))
      }
      return Promise.resolve(jsonResponse({ settings: {} }))
    })
    vi.stubGlobal('fetch', fetchMock)
    const pinia = createPinia()
    setActivePinia(pinia)
    wrapper = mount(SystemSettingsView, { global: { plugins: [pinia] }, attachTo: document.body })
    await flushPromises()
  }

  /** 【日本語単語AI】のパネル。 */
  function panel(): HTMLElement {
    return document.querySelector('[data-panel="japanese_word_ai"]') as HTMLElement
  }

  it('4 つの区画すべてに Data タブが出ている', async () => {
    await openSettings()

    const sections = panel().querySelectorAll('[data-setting-subsection]')
    expect(sections).toHaveLength(4)

    const expected: [string, string[]][] = [
      ['detail', ['基本設定', 'System Prompt', 'User Prompt', 'Data', 'その他']],
      ['problem_c', ['基本設定', 'System Prompt', 'User Prompt', 'Data', 'その他']],
      ['problem_d', ['基本設定', 'System Prompt', 'User Prompt', 'Data', 'その他']],
      ['problem_e', ['基本設定', 'System Prompt', 'User Prompt', 'Data', 'その他']]
    ]
    expected.forEach(([id, tabs], index) => {
      const section = sections[index] as HTMLElement
      expect(section.dataset.settingSubsection, `${id} の区画`).toBe(id)
      const shown = Array.from(section.querySelectorAll('[data-method-tab]'))
        .map((button) => button.textContent?.trim() ?? '')
      expect(shown, `${id} のタブ`).toEqual(tabs)
    })
  })

  it('Data タブを押すと、その区画のバッチコードに対応する出力 DTO の構造が出る', async () => {
    await openSettings()

    const section = panel().querySelector('[data-setting-subsection="detail"]') as HTMLElement
    const dataTab = section.querySelector('[data-method-tab="Data"]') as HTMLButtonElement
    dataTab.click()
    await flushPromises()

    // 押したタブだけが開く（基本設定 → Data）
    expect((section.querySelector('[data-method-panel="Data"]') as HTMLElement).hidden).toBe(false)
    expect((section.querySelector('[data-method-panel="基本設定"]') as HTMLElement).hidden).toBe(true)

    // 区画のマウント点は自分のバッチコード（batC41）を持つ
    const slot = section.querySelector('[data-ai-data-schema-slot="batC41"]') as HTMLElement
    expect(slot).not.toBeNull()
    // そのバッチの DTO から作った構造が出る（サーバーが返したスキーマ。項目名にバッチコードが入る）
    expect(slot.querySelector('[data-ai-data-structure]')).not.toBeNull()
    expect(slot.querySelector('[data-ai-data-field="field_of_batC41"]')).not.toBeNull()
    // 縦スクロールする枠はキーボードでも送れる（Chrome DevTools の
    // 「Scrollable region must have keyboard access」を出さない）
    expect(slot.querySelector('pre.ai-data-schema__json')?.getAttribute('tabindex')).toBe('0')
    // 項目（設定値）は Data タブには出さない
    expect(section.querySelector('[data-method-panel="Data"] .setting-field')).toBeNull()
  })
})

/**
 * Data TAB の **JSON Schema の枠**は縦に長い（DTO 全体を整形して出すので数百行になる）。
 * そのまま出すと Data TAB が長くなりすぎるので、**高さを抑えて縦スクロール**にする。
 *
 * <p>DTO 表示は「素の DOM（設定ランタイムの区画）」と「Vue 部品（図形管理のカード）」の
 * 2 通りで描く。見た目の規則がどちらかにしか無いと、**片方だけスクロールしない**
 * （実際、日本語単語AI の Data TAB だけ縦スクロールが出ていなかった）ので、
 * 規則は 1 か所（`aiDataSchema.css`）に置いて両方から読む。</p>
 */
describe('設定ページ：Data TAB の JSON Schema の枠', () => {
  const panelCss = (): string =>
    readFileSync(resolve(process.cwd(), 'src/features/system-settings/aiDataSchema.css'), 'utf8')
  const runtimePanel = (): string =>
    readFileSync(resolve(process.cwd(), 'src/features/system-settings/aiDataSchemaPanel.ts'), 'utf8')

  it('高さを抑えて縦スクロールにする（Data TAB が長くなりすぎない）', () => {
    const css = panelCss()
    const start = css.indexOf('.ai-data-schema__json {')
    expect(start, '.ai-data-schema__json の規則').toBeGreaterThanOrEqual(0)
    const rule = css.slice(start, css.indexOf('}', start))
    // jsdom にレイアウトが無いので、CSS の規則そのものを確かめる（実寸は Chrome の実測で見る）
    const maxHeight = Number((rule.match(/max-height:\s*(\d+)px/) ?? [])[1])
    expect(maxHeight).toBeGreaterThan(0)
    // 画面（設定ページ）に収まる高さにする。大きすぎると「長すぎる」が戻る
    expect(maxHeight).toBeLessThanOrEqual(480)
    expect(rule).toMatch(/overflow-y:\s*auto/)
    // 横は折り返さずスクロール（インデントが崩れない）
    expect(rule).toMatch(/overflow-x:\s*auto/)
  })

  it('見た目は両方の描画経路で同じ 1 か所から読む', () => {
    // 素の DOM 側（日本語単語AI の Data TAB）はこの CSS を読む
    expect(runtimePanel()).toContain("import '@/features/system-settings/aiDataSchema.css'")
    // Vue 側（図形管理のカード）も同じ CSS を読み、自前の <style> は持たない
    const component = readFileSync(
      resolve(process.cwd(), 'src/features/system-settings/AiDataSchemaPanel.vue'), 'utf8'
    )
    expect(component).toContain("import '@/features/system-settings/aiDataSchema.css'")
    // 自前の <style> ブロックは持たない（規則が 2 か所に分かれるのを防ぐ。
    // 行頭の <style だけを見る。説明の文中に `<style>` と書くことはある）
    expect(component).not.toMatch(/^<style/m)
  })

  it('Vue 部品の JSON Schema の枠も、同じクラス・キーボードで送れる', async () => {
    const fetchMock = vi.fn(() => Promise.resolve(new Response(
      JSON.stringify({
        success: true, code: 'OK', message: 'OK',
        data: { taskCode: 'batC41', dto: 'BatC41ResultDto', schema: { type: 'object', properties: {} } }
      }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )))
    vi.stubGlobal('fetch', fetchMock)
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(AiDataSchemaPanel, {
      props: { task: 'batC41' },
      global: { plugins: [pinia] }
    })
    await flushPromises()
    try {
      const json = wrapper.get('pre.ai-data-schema__json')
      expect(json.attributes('tabindex')).toBe('0')
    } finally {
      wrapper.unmount()
      vi.unstubAllGlobals()
    }
  })
})

/**
 * 描画の再試行タイマー（設定ページの組み立ては非同期なので、少しの間だけ繰り返し探す）。
 *
 * ここが暴れると、画面を離れた後（テストの片付けの後）にタイマーが動いて
 * `document is not defined` で落ちる。**実際にスイート全体が落ちていた**ので、
 * 「増やさない・離れたら止まる・document が無くても落ちない」を固定する。
 */
describe('設定ページ：Data TAB の描画タイマー', () => {
  beforeEach(() => {
    vi.useFakeTimers()
  })

  afterEach(() => {
    cancelAiDataSchemaRender()
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
    vi.useRealTimers()
  })

  it('繰り返し呼んでも、動くのは最後の 1 本だけ（再描画で増やさない）', () => {
    const findAll = vi.spyOn(document, 'querySelectorAll')

    renderAllAiDataSchemas(3, 10)
    renderAllAiDataSchemas(3, 10)
    // 呼んだ回数ぶん、その場で 1 回ずつ探す
    expect(findAll).toHaveBeenCalledTimes(2)

    vi.advanceTimersByTime(10)

    // 先に呼んだ世代は止まっているので、増えるのは後から呼んだ 1 本だけ
    expect(findAll).toHaveBeenCalledTimes(3)
  })

  it('画面を離れた後にタイマーが動いても落ちない（document が無い）', () => {
    const findAll = vi.spyOn(document, 'querySelectorAll')

    renderAllAiDataSchemas(5, 10)
    vi.stubGlobal('document', undefined)

    expect(() => vi.advanceTimersByTime(100)).not.toThrow()
    // 2 回目以降は document が無いので触らない
    expect(findAll).toHaveBeenCalledTimes(1)
  })

  it('画面を離れるときに止められる（タイマーを残さない）', () => {
    const findAll = vi.spyOn(document, 'querySelectorAll')

    renderAllAiDataSchemas(5, 10)
    cancelAiDataSchemaRender()
    vi.advanceTimersByTime(100)

    expect(findAll).toHaveBeenCalledTimes(1)
  })
})

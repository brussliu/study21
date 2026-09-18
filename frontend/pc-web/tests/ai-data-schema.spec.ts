import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import AiDataSchemaPanel from '@/features/system-settings/AiDataSchemaPanel.vue'

/**
 * 設定ページの **Data TAB**（AI 出力データ構造の表示。batC51-A〜D / batC52 で共用）。
 *
 * DTO が唯一の定義なので、画面はサーバーが DTO から生成した JSON Schema を出すだけ。
 * ここでは
 * ・バッチコードを渡してスキーマを取りに行くこと
 * ・項目構造（フィールド名・型・説明・必須・入れ子の開閉）を出すこと
 * ・JSON Schema を整形して出すこと
 * ・**見るだけ**（スキーマを編集する入力が無い）であること
 * を固定する。
 */
const SCHEMA = {
  type: 'object',
  properties: {
    コマンド: {
      type: 'array',
      items: { type: 'string' },
      description: 'GeoGebra のコマンド（1 要素 = 1 コマンド）'
    },
    分類: {
      type: 'string',
      enum: ['FIGURE', 'FUNCTION', 'MIXED', 'UNKNOWN'],
      description: 'AI が判定した作図の分類'
    },
    入れ子: {
      type: 'object',
      description: '入れ子のオブジェクト',
      properties: { 名前: { type: 'string', description: '名前' } },
      required: ['名前']
    }
  },
  required: ['コマンド', '分類']
}

function jsonResponse(data: unknown): Response {
  return new Response(
    JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
}

function setup(options: { fail?: boolean } = {}): { wrapper: VueWrapper; urls: string[] } {
  const urls: string[] = []
  const fetchMock = vi.fn((url: string) => {
    urls.push(String(url))
    if (options.fail) {
      return Promise.resolve(
        new Response(JSON.stringify({ success: false, code: 'ERROR', message: '取得できませんでした' }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        })
      )
    }
    return Promise.resolve(jsonResponse({ taskCode: 'batC51-A', dto: 'BatC51AResultDto', schema: SCHEMA }))
  })
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(AiDataSchemaPanel, { props: { task: 'batC51-A' } })
  return { wrapper, urls }
}

const rowsOf = (wrapper: VueWrapper): string[] =>
  wrapper.findAll('[data-ai-data-field]').map((row) => row.attributes('data-ai-data-field') ?? '')

describe('設定ページ：Data TAB（AI 出力スキーマ）', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    vi.unstubAllGlobals()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  it('バッチコードを渡してスキーマを取りに行く', async () => {
    const context = setup()
    wrapper = context.wrapper
    await flushPromises()

    expect(context.urls[0]).toContain('/api/admin/setting/ai-response-schema')
    expect(context.urls[0]).toContain('task=batC51-A')
  })

  it('項目構造に フィールド名・型・説明・必須 を出す', async () => {
    const context = setup()
    wrapper = context.wrapper
    await flushPromises()

    const rows = wrapper.findAll('[data-ai-data-field]')
    expect(rows.map((row) => row.attributes('data-ai-data-field'))).toEqual(['コマンド', '分類', '入れ子'])
    // 型（配列・Enum は値を含める）
    expect(rows[0].find('.ai-data-schema__type').text()).toBe('array<string>')
    expect(rows[1].find('.ai-data-schema__type').text()).toContain('FIGURE')
    // 説明（@Schema の description）
    expect(rows[0].find('.ai-data-schema__description').text()).toBe('GeoGebra のコマンド（1 要素 = 1 コマンド）')
    // 必須
    expect(rows[0].find('[data-ai-data-required]').exists()).toBe(true)
    expect(rows[2].find('[data-ai-data-required]').exists()).toBe(false)
  })

  it('入れ子は開いたときだけ行に出す', async () => {
    const context = setup()
    wrapper = context.wrapper
    await flushPromises()

    expect(rowsOf(wrapper)).toEqual(['コマンド', '分類', '入れ子'])
    await wrapper.get('[data-ai-data-toggle="入れ子"]').trigger('click')
    expect(rowsOf(wrapper)).toEqual(['コマンド', '分類', '入れ子', '入れ子.名前'])
    // 入れ子の行は 1 段深く出す
    expect(wrapper.get('[data-ai-data-field="入れ子.名前"]').attributes('data-ai-data-depth')).toBe('1')
    await wrapper.get('[data-ai-data-toggle="入れ子"]').trigger('click')
    expect(rowsOf(wrapper)).toEqual(['コマンド', '分類', '入れ子'])
  })

  it('JSON Schema を整形して出す（DTO から生成された本文そのもの）', async () => {
    const context = setup()
    wrapper = context.wrapper
    await flushPromises()

    const json = wrapper.get('[data-ai-data-json] pre').text()
    expect(json).toContain('"コマンド"')
    expect(json).toContain('"FIGURE"')
    expect(json).toContain('\n')          // 整形されている
    expect(JSON.parse(json)).toEqual(SCHEMA)
  })

  it('見るだけ（スキーマを編集する入力が無い）', async () => {
    const context = setup()
    wrapper = context.wrapper
    await flushPromises()

    expect(wrapper.find('input:not([type="button"])').exists()).toBe(false)
    expect(wrapper.find('textarea').exists()).toBe(false)
    expect(wrapper.find('select').exists()).toBe(false)
    // 操作できるのは入れ子の開閉ボタンだけ
    for (const button of wrapper.findAll('button')) {
      expect(button.attributes('data-ai-data-toggle')).toBeTruthy()
    }
    expect(wrapper.get('[data-ai-data-note]').text()).toContain('見るだけ')
  })

  it('取れなかったら理由を出す', async () => {
    const context = setup({ fail: true })
    wrapper = context.wrapper
    await flushPromises()

    expect(wrapper.find('[data-ai-data-error]').exists()).toBe(true)
    expect(wrapper.find('[data-ai-data-structure]').exists()).toBe(false)
  })
})

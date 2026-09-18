import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import GeometryAiSettingsSection from '@/views/admin/system-settings/GeometryAiSettingsSection.vue'

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
/** リポジトリ直下（移行 SQL を読むため）。 */
const repoRoot = path.resolve(webRoot, '..', '..')

/**
 * システム設定【図形管理】分頁 — AI 生図と AI 画図助手。
 *
 * 画面はこの Vue コンポーネントが描き（設定ランタイムの同カテゴリは external）、値の読み書きは
 * `/api/admin/setting/initSettings` / `saveSettings` を通す。**送るキーは GEOMETRY_AI のカタログ
 * 54 キー**（共通の 26 キー ＋ 作図モード別の 28 キー。未定義キーを送ると 400 になり、画面の
 * 他の設定もまとめて保存できなくなる）。
 *
 * ここでは
 * ・セクションカード（前処理 / 共通規則 / 作図モード A〜D / 検証 / batC52（AI 画図助手））と
 *   入力→出力バッジ・タブ
 * ・「使用モデル」が「AIモデル」ページのスロットを選ぶドロップダウンであること
 * ・AI を呼ぶカードが**共通レイアウト**（aiSettingsLayout.ts）に揃っていること
 *   （TAB・統一した名称・並び・スライダーの刻みと単位。Data はプロンプトの後ろ・その他の前）
 * ・**作図モードは 4 枚**（batC51-A〜D）で、それぞれが自分の System Prompt / User Prompt の欄と
 *   自分の出力 DTO（Data TAB＝バッチコード）を持つこと
 * ・モード別の欄が**空のときは共通の設定を継承する**ので、空欄を既定値で埋めずに空のまま送ること
 * ・AI の数字項目はスライダー（設定ランタイムと同じマークアップ）で、範囲がカタログと一致すること
 *   （ずれると保存が 400）。AI を呼ばないカード（前処理・検証）は今までどおり数値入力であること
 * ・AI 画図助手が**バッチを通らない**（user-api が同期で呼ぶ）ことを画面に書いていること
 * ・有効／無効のスイッチを出さず、読み込んだ値をそのまま保持すること
 * を固定する。
 */
/** 既存の共通キー（26）。 */
const LEGACY_KEYS = [
  'geometryAiAllowedCommands',
  'geometryAiApproval',
  'geometryAiAssistDailyLimitPerAccount',
  'geometryAiAssistEnabled',
  'geometryAiAssistMaxCommands',
  'geometryAiAssistProvider',
  'geometryAiAssistSystemPrompt',
  'geometryAiAssistTimeoutSeconds',
  'geometryAiAssistUserPrompt',
  'geometryAiDailyLimitPerAccount',
  'geometryAiDefaultCrop',
  'geometryAiDefaultKind',
  'geometryAiEnabled',
  'geometryAiImageRetentionDays',
  'geometryAiInstructionTemplate',
  'geometryAiMaxCommands',
  'geometryAiMaxCompletionTokens',
  'geometryAiMaxConcurrency',
  'geometryAiMaxImageMb',
  'geometryAiMaxImagePixels',
  'geometryAiOutputFormat',
  'geometryAiProvider',
  'geometryAiRequestTimeoutSeconds',
  'geometryAiRetryLimit',
  'geometryAiSystemPrompt',
  'geometryAiTemperature'
]

/**
 * 作図モード別のキー（28 ＝ 7 項目 × 4 モード）。
 *
 * サーバー側の設定キーは `GEOMETRY_AI_<モード>_<項目>`（`FigureProcessorSettings` が解決する）。
 * **空の値は「共通の設定を継承する」**を意味する（サーバーは任意項目として読む）。
 */
const MODE_KEYS = ['A', 'B', 'C', 'D'].flatMap((mode) => [
  `geometryAi${mode}MaxCompletionTokens`,
  `geometryAi${mode}Provider`,
  `geometryAi${mode}RequestTimeoutSeconds`,
  `geometryAi${mode}RetryLimit`,
  `geometryAi${mode}SystemPrompt`,
  `geometryAi${mode}TaskTemplate`,
  `geometryAi${mode}Temperature`
])

/** 画面が送る GEOMETRY_AI の 54 キー（共通 26 ＋ モード別 28）。 */
const GEOMETRY_AI_KEYS: string[] = [...LEGACY_KEYS, ...MODE_KEYS].sort()

/** 作図モードの 4 枚のカード（画面の区切り・バッチコード・見出し・自分専用のフックの接尾辞）。 */
const MODE_CARDS: {
  mode: string
  id: string
  task: string
  slug: string
  title: string
}[] = [
  { mode: 'A', id: 'geometry-ai-mode-a', task: 'batC51-A', slug: 'a', title: 'batC51-A（画像をもとに再現）' },
  { mode: 'B', id: 'geometry-ai-mode-b', task: 'batC51-B', slug: 'b', title: 'batC51-B（数式からグラフを作成）' },
  { mode: 'C', id: 'geometry-ai-mode-c', task: 'batC51-C', slug: 'c', title: 'batC51-C（文章の条件から作図）' },
  { mode: 'D', id: 'geometry-ai-mode-d', task: 'batC51-D', slug: 'd', title: 'batC51-D（文章と図を合わせて作図）' }
]

/**
 * AI を呼ぶカード（batC51 / batC52）の数字項目＝スライダー。
 *
 * 範囲は `COM_設定項目` の有効値と同じ（ずれると保存が 400）。刻み（step）と単位（suffix）は
 * 共通レイアウト `aiSettingsLayout.ts` の `aiSliderOf` が決めるので、その値と一致すること。
 * `text` は右側の値表示（既定値＋単位。設定ランタイム未マウントでもコンポーネントが作る）。
 */
const AI_SLIDERS: {
  key: string
  hook: string
  min: string
  max: string
  step: string
  suffix: string
  text: string
}[] = [
  {
    key: 'geometryAiRequestTimeoutSeconds', hook: 'data-gm-set-request-timeout',
    min: '30', max: '600', step: '30', suffix: 's', text: '120s'
  },
  {
    key: 'geometryAiTemperature', hook: 'data-gm-set-temperature',
    min: '0', max: '2', step: '0.1', suffix: '', text: '0.2'
  },
  {
    key: 'geometryAiMaxCompletionTokens', hook: 'data-gm-set-max-tokens',
    min: '1024', max: '65536', step: '512', suffix: '', text: '4096'
  },
  {
    key: 'geometryAiRetryLimit', hook: 'data-gm-set-retry',
    min: '0', max: '3', step: '1', suffix: '回', text: '1回'
  },
  {
    key: 'geometryAiMaxConcurrency', hook: 'data-gm-set-max-concurrency',
    min: '1', max: '10', step: '1', suffix: '', text: '1'
  },
  {
    key: 'geometryAiDailyLimitPerAccount', hook: 'data-gm-set-daily-limit',
    min: '0', max: '100', step: '1', suffix: '回', text: '20回'
  },
  {
    key: 'geometryAiAssistTimeoutSeconds', hook: 'data-gm-set-assist-timeout',
    min: '30', max: '600', step: '30', suffix: 's', text: '60s'
  },
  {
    key: 'geometryAiAssistMaxCommands', hook: 'data-gm-set-assist-max-commands',
    min: '1', max: '100', step: '1', suffix: '個', text: '20個'
  },
  {
    key: 'geometryAiAssistDailyLimitPerAccount', hook: 'data-gm-set-assist-daily-limit',
    min: '0', max: '100', step: '1', suffix: '回', text: '50回'
  },
  // AI を呼ばないカード（画像の前処理・コマンド検証）の数字も、同じ規則でスライダー（利用者の指示）
  {
    key: 'geometryAiMaxImageMb', hook: 'data-gm-set-max-size',
    min: '1', max: '50', step: '1', suffix: 'MB', text: '10MB'
  },
  {
    key: 'geometryAiMaxImagePixels', hook: 'data-gm-set-max-pixels',
    min: '1024', max: '8192', step: '512', suffix: 'px', text: '1536px'
  },
  {
    key: 'geometryAiImageRetentionDays', hook: 'data-gm-set-retention',
    min: '1', max: '365', step: '1', suffix: '日', text: '30日'
  },
  {
    key: 'geometryAiMaxCommands', hook: 'data-gm-set-max-commands',
    min: '1', max: '100', step: '1', suffix: '個', text: '80個'
  }
]

/** 有効／無効（画面に出さないキー）は読み込んだ値をそのまま送り返すので、init で渡しておく。 */
const ENABLED_INIT = { geometryAiEnabled: 'true', geometryAiAssistEnabled: 'true' }

describe('システム設定：AI 生図（図形管理）', () => {
  let fetchMock: ReturnType<typeof vi.fn>
  let saved: { body: Record<string, unknown> | null }
  /** Data TAB が要求したバッチコード（カードごとの出力 DTO。要求の順に並ぶ）。 */
  let schemaTasks: string[]

  /** 設定 API の応答を返す fetch（initSettings / saveSettings）。 */
  function setup(
    options: { init?: Record<string, string>; saveError?: string } = {}
  ): { wrapper: VueWrapper } {
    const pinia = createPinia()
    setActivePinia(pinia)
    saved = { body: null }
    schemaTasks = []
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const body = init?.body === undefined ? null : JSON.parse(String(init.body)) as Record<string, unknown>
      // Data TAB は AI 出力 DTO のスキーマを取る（サーバーが DTO から生成したもの）
      if (String(url).includes('/ai-response-schema')) {
        // どのバッチコードを要求したかを記録する（作図モードごとの出力 DTO を固定する）
        const task = new URL(String(url), 'http://localhost').searchParams.get('task') ?? ''
        schemaTasks.push(task)
        return Promise.resolve(new Response(
          JSON.stringify({
            success: true, code: 'OK', message: 'OK',
            data: {
              taskCode: task,
              dto: /^batC51-[A-D]$/.test(task) ? `BatC51${task.slice(-1)}ResultDto` : 'BatC51ResultDto',
              schema: {
                type: 'object',
                properties: {
                  コマンド: { type: 'array', items: { type: 'string' }, description: 'GeoGebra のコマンド' }
                },
                required: ['コマンド']
              }
            }
          }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        ))
      }
      if (String(url).includes('/saveSettings')) {
        saved.body = body
        if (options.saveError !== undefined) {
          return Promise.resolve(new Response(
            JSON.stringify({ success: false, code: 'VALIDATION_ERROR', message: options.saveError, data: null }),
            { status: 400, headers: { 'Content-Type': 'application/json' } }
          ))
        }
        return Promise.resolve(new Response(
          JSON.stringify({ success: true, code: 'OK', message: '設定を保存しました。', data: { settings: body?.settings ?? {} } }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        ))
      }
      return Promise.resolve(new Response(
        JSON.stringify({
          success: true, code: 'OK', message: 'OK',
          data: { settings: options.init ?? {} }
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      ))
    })
    vi.stubGlobal('fetch', fetchMock)
    return { wrapper: mount(GeometryAiSettingsSection, { global: { plugins: [pinia] } }) }
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    delete window.__study21SystemSettings
  })

  /** カードの TAB の中の項目ラベル（表示順。並びは共通レイアウトが決める）。 */
  function labelsOf(wrapper: VueWrapper, section: string, tab: string): string[] {
    return wrapper.get(`[data-setting-subsection="${section}"] [data-method-panel="${tab}"]`)
      .findAll('.setting-label span')
      .map((item) => item.text())
  }

  it('見出し（タイトル・アイコン・バッジ）を出し、接続設定の共用は 1 行で書かない', async () => {
    const { wrapper } = setup()
    await flushPromises()

    expect(wrapper.get('.card__title').text()).toContain('AI 生図（図形管理）')
    expect(wrapper.get('.card__title').get('use').attributes('href')).toBe('#i-wand')
    expect(wrapper.get('[data-gm-set-badge]').text()).toBe('図形管理')
    // 「API Key と URL は「AIモデル」ページの同じスロットを共通利用します。」は出さない
    expect(wrapper.find('[data-gm-set-note]').exists()).toBe(false)
  })

  it('8 つのセクションカード（前処理 / 共通規則 / モード A〜D / 検証 / batC52）と入力→出力バッジを出す', async () => {
    const { wrapper } = setup()

    const subsections = wrapper.findAll('[data-gm-ai-settings] [data-setting-subsection]')
    expect(subsections.map((node) => node.attributes('data-setting-subsection')))
      .toEqual([
        'geometry-preprocess',
        'geometry-ai-common',
        ...MODE_CARDS.map((mode) => mode.id),
        'geometry-verify',
        'geometry-assist'
      ])

    const badge = (id: string): string =>
      wrapper.get(`[data-setting-subsection="${id}"] .setting-io-badge`).text().replace(/\s+/g, ' ')
    const title = (id: string): string =>
      wrapper.get(`[data-setting-subsection="${id}"] .setting-batch-section-head h4`).text()
    // バッチは AI 生成（batC51-A〜D）と AI 画図助手（batC52）だけ。前処理と検証はバックエンドの通常コード
    expect(title('geometry-preprocess')).toBe('画像の取込・前処理（バックエンドで実行）')
    // 共通規則は AI 生成ブロックの先頭（4 モードが空のときに使う値）
    expect(title('geometry-ai-common')).toBe('共通規則（4 モード共通）')
    for (const mode of MODE_CARDS) {
      expect(title(mode.id), mode.id).toBe(mode.title)
    }
    expect(title('geometry-verify')).toBe('コマンド検証・確定（バックエンドで実行）')
    expect(title('geometry-assist')).toContain('batC52')
    expect(badge('geometry-preprocess')).toContain('図形の写真')
    expect(badge('geometry-preprocess')).toContain('AI に送る画像')
    expect(badge('geometry-ai-common')).toContain('4 モードの既定')
    // モードのバッジは「入力 → 出力」（モードごとに違う）
    expect(badge('geometry-ai-mode-a')).toContain('AI に送る画像')
    expect(badge('geometry-ai-mode-a')).toContain('GeoGebra コマンド')
    expect(badge('geometry-ai-mode-b')).toContain('数式・方程式・定義域')
    expect(badge('geometry-ai-mode-b')).toContain('関数・方程式のグラフ')
    expect(badge('geometry-ai-mode-c')).toContain('問題文などの文章')
    expect(badge('geometry-ai-mode-d')).toContain('文章＋参考図')
    expect(badge('geometry-verify')).toContain('検証済みの作図')
    expect(badge('geometry-assist')).toContain('いまの作図＋日本語の指示')
    expect(badge('geometry-assist')).toContain('変更後のコマンド')
  })

  it('前処理と検証は「バッチではない」と書いてある（バッチは AI 生成の 4 モードだけ）', async () => {
    const { wrapper } = setup()

    // 前処理・検証は AI を呼ばないのでバッチではない（履歴に残らない）
    for (const id of ['geometry-preprocess', 'geometry-verify']) {
      const description = wrapper.get(`[data-setting-subsection="${id}"] .setting-batch-section-head p`)
        .text().replace(/\s+/g, ' ')
      expect(description, id).toContain('バッチではありません')
      expect(description, id).toContain('バッチ実行履歴には残りません')
    }
    // バッチは AI 生成（batC51-A〜D）だけ
    expect(wrapper.get('[data-setting-subsection="geometry-ai-common"] .setting-batch-section-head p')
      .text().replace(/\s+/g, ' ')).toContain('バッチを通るのはこのブロックだけ')
    for (const mode of MODE_CARDS) {
      expect(wrapper.get(`[data-setting-subsection="${mode.id}"] .setting-batch-section-head p`)
        .text().replace(/\s+/g, ' '), mode.id).toContain(`${mode.task} のバッチとして実行されます`)
    }

    // AI 画図助手のカードには実行の仕組みを書かない（画面から外した。2026-09-16 の指示）
    expect(wrapper.find('[data-gm-assist-note]').exists()).toBe(false)
  })

  it('内部タブは日本語単語AI と同じ（モード A〜D と batC52 は 5 つ、共通規則は Data を持たない）', async () => {
    const { wrapper } = setup()

    const tabs = (id: string): string[] => wrapper
      .findAll(`[data-setting-subsection="${id}"] [data-method-tab]`)
      .map((tab) => tab.text())
    // Data TAB（DTO から生成した JSON Schema を見る）はプロンプトの後ろ・その他の前
    for (const mode of MODE_CARDS) {
      expect(tabs(mode.id), mode.id).toEqual(['基本設定', 'System Prompt', 'User Prompt', 'Data', 'その他'])
    }
    expect(tabs('geometry-assist')).toEqual(['基本設定', 'System Prompt', 'User Prompt', 'Data', 'その他'])
    // 共通規則は 4 モード共通の値なので、モード固有の出力 DTO（Data）は持たない
    expect(tabs('geometry-ai-common')).toEqual(['基本設定', 'System Prompt', 'User Prompt', 'その他'])
    // Data TAB はカードごとの DTO（モードは batC51-A〜D、助手は batC52）を出す
    for (const mode of MODE_CARDS) {
      expect(
        wrapper.get(`[data-setting-subsection="${mode.id}"] [data-ai-data-schema]`)
          .attributes('data-ai-data-schema'),
        mode.id
      ).toBe(mode.task)
    }
    expect(wrapper.find('[data-setting-subsection="geometry-assist"] [data-ai-data-schema]').attributes('data-ai-data-schema'))
      .toBe('batC52')
    // 使用モデルは 1 行（runtime が描く他の AI ブロックと同じ規則）
    expect(wrapper.get('[data-gm-set-model]').element.closest('.setting-field')?.classList.contains('full')).toBe(true)
    expect(wrapper.get('[data-gm-set-a-provider]').element.closest('.setting-field')?.classList.contains('full')).toBe(true)
    expect(wrapper.get('[data-gm-set-assist-provider]').element.closest('.setting-field')?.classList.contains('full')).toBe(true)
    // 前処理と検証は 基本設定 だけ
    expect(tabs('geometry-preprocess')).toEqual(['基本設定'])
    expect(tabs('geometry-verify')).toEqual(['基本設定'])
    // 既定で開くのは 基本設定（他は hidden。切替は設定ランタイムが行う）
    const panels = wrapper.findAll('[data-setting-subsection="geometry-ai-mode-a"] [data-method-panel]')
    expect(panels[0]?.attributes('hidden')).toBeUndefined()
    expect(panels[1]?.attributes('hidden')).toBeDefined()
  })

  it('「使用モデル」は「AIモデル」ページのスロットを選ぶドロップダウン（AI 生図と助手の両方）', async () => {
    const { wrapper } = setup({
      init: { qwenModel4: 'Qwen3-VL-Plus', deepseekModel: 'deepseek-chat' }
    })
    await flushPromises()

    const expectedValues = [
      'qwen:1', 'qwen:2', 'qwen:3', 'qwen:4', 'qwen:5',
      'doubao:1', 'doubao:2', 'deepseek:1', 'deepseek:2', 'chatgpt:1', 'chatgpt:2'
    ]
    const main = wrapper.get('[data-gm-set-model]')
    expect(main.attributes('class')).toContain('setting-dropdown')
    expect(wrapper.findAll('[data-gm-set-model] option').map((option) => option.attributes('value')))
      .toEqual([...expectedValues, 'bigmodel:1'])
    expect(wrapper.findAll('[data-gm-set-model] option').map((option) => option.text()))
      .toContain('千問 / Qwen3-VL-Plus')

    const assist = wrapper.get('[data-gm-set-assist-provider]')
    expect(assist.attributes('class')).toContain('setting-dropdown')
    expect(wrapper.findAll('[data-gm-set-assist-provider] option').map((option) => option.attributes('value')))
      .toEqual(expectedValues)

    // モード別の「使用モデル」も同じスロット（先頭は「空＝共通の設定を継承する」）
    for (const mode of MODE_CARDS) {
      const modeProvider = wrapper.get(`[data-gm-set-${mode.slug}-provider]`)
      expect(modeProvider.attributes('class'), mode.id).toContain('setting-dropdown')
      expect(wrapper.findAll(`[data-gm-set-${mode.slug}-provider] option`).map((option) => option.attributes('value')),
        mode.id).toEqual(['', ...expectedValues, 'bigmodel:1'])
    }

    await main.setValue('deepseek:1')
    await assist.setValue('deepseek:2')
    await wrapper.get('[data-gm-set-b-provider]').setValue('chatgpt:2')
    await wrapper.get('[data-gm-set-b-provider]').setValue('')
    await wrapper.get('[data-gm-set-c-provider]').setValue('chatgpt:2')
    await wrapper.get('[data-gm-set-save]').trigger('click')
    await flushPromises()
    const settings = saved.body?.settings as Record<string, string>
    expect(settings.geometryAiProvider).toBe('deepseek:1')
    expect(settings.geometryAiAssistProvider).toBe('deepseek:2')
    expect(settings.geometryAiCProvider).toBe('chatgpt:2')
    // 「共通の設定を使う（未選択）」に戻すと空で送る＝共通の設定を継承する
    expect(settings.geometryAiBProvider).toBe('')
  })

  it('AI を呼ぶカードは共通レイアウト（TAB・名称・並び）に揃っている', async () => {
    const { wrapper } = setup()

    // 共通規則（4 モード共通）: 使用モデル → リクエストタイムアウト → Temperature → 最大出力Token数 → 出力形式
    expect(labelsOf(wrapper, 'geometry-ai-common', '基本設定')).toEqual([
      '使用モデル', 'リクエストタイムアウト', 'Temperature', '最大出力Token数', '出力形式'
    ])
    expect(labelsOf(wrapper, 'geometry-ai-common', 'System Prompt')).toEqual(['System Prompt'])
    expect(labelsOf(wrapper, 'geometry-ai-common', 'User Prompt'))
      .toEqual(['User Prompt（共通の指示テンプレート・任意）'])
    // 最大再実行回数 → スレッド数（旧「同時処理数」）→ 日次の上限（旧「日次の上限（回）」）→ 承認フロー
    expect(labelsOf(wrapper, 'geometry-ai-common', 'その他')).toEqual([
      '最大再実行回数', 'スレッド数', '日次の上限', '処理結果の承認フロー'
    ])

    // 作図モード A〜D: 使用モデル → リクエストタイムアウト → Temperature → 最大出力Token数
    // 出力形式・スレッド数・日次の上限・承認フローは共通規則にだけ置く（モードごとには持たない）
    for (const mode of MODE_CARDS) {
      expect(labelsOf(wrapper, mode.id, '基本設定'), mode.id).toEqual([
        '使用モデル', 'リクエストタイムアウト', 'Temperature', '最大出力Token数'
      ])
      expect(labelsOf(wrapper, mode.id, 'System Prompt'), mode.id).toEqual(['System Prompt'])
      // User Prompt はモードのテンプレート（共通レイアウトの TAB に載る）
      expect(labelsOf(wrapper, mode.id, 'User Prompt'), mode.id)
        .toEqual(['User Prompt（このモードの指示テンプレート）'])
      expect(labelsOf(wrapper, mode.id, 'その他'), mode.id).toEqual(['最大再実行回数'])
      // 日次の上限は共通規則にだけ出す（モードごとに重複させない）
      expect(wrapper.findAll(`[data-setting-subsection="${mode.id}"] [data-gm-set-daily-limit]`), mode.id)
        .toHaveLength(0)
    }

    // 日次の上限は画面に 1 つだけ（AI 画図助手の分と合わせて 2 つ）
    expect(wrapper.findAll('[data-gm-ai-settings] [data-gm-set-daily-limit]')).toHaveLength(1)

    // batC52（AI 画図助手）: 使用モデル → リクエストタイムアウト
    expect(labelsOf(wrapper, 'geometry-assist', '基本設定')).toEqual([
      '使用モデル', 'リクエストタイムアウト'
    ])
    expect(labelsOf(wrapper, 'geometry-assist', 'System Prompt')).toEqual(['System Prompt'])
    expect(labelsOf(wrapper, 'geometry-assist', 'User Prompt')).toEqual(['User Prompt'])
    // コマンド数の上限・日次の上限は AI の設定ではなく実行条件なので「その他」
    expect(labelsOf(wrapper, 'geometry-assist', 'その他')).toEqual(['コマンド数の上限', '日次の上限'])
    expect(wrapper.get('[data-setting-subsection="geometry-assist"] [data-method-panel="その他"]').text())
      .not.toContain('このブロックに追加の設定はありません')
  })

  it('作図モードの 4 枚はそれぞれ自分の System Prompt / User Prompt の欄を持つ', async () => {
    const { wrapper } = setup()
    await flushPromises()

    for (const mode of MODE_CARDS) {
      const card = `[data-setting-subsection="${mode.id}"]`
      const systemPrompt = wrapper.get(`${card} [data-gm-set-${mode.slug}-system-prompt]`)
      const taskTemplate = wrapper.get(`${card} [data-gm-set-${mode.slug}-task-template]`)
      expect(systemPrompt.element.tagName, mode.id).toBe('TEXTAREA')
      expect(taskTemplate.element.tagName, mode.id).toBe('TEXTAREA')
      // ラベルには対応表のキー（SettingPageFields と同じ camelCase）を出す
      expect(wrapper.get(`${card} .setting-field-key`).text()).toBe(`geometryAi${mode.mode}Provider`)
      // System Prompt / User Prompt の入力欄はモード別のキーに結び付く
      await systemPrompt.setValue(`${mode.task} の system。`)
      await taskTemplate.setValue(`{mode} の条件から作図します（${mode.task}）。`)
    }
    await wrapper.get('[data-gm-set-save]').trigger('click')
    await flushPromises()
    const settings = saved.body?.settings as Record<string, string>
    for (const mode of MODE_CARDS) {
      expect(settings[`geometryAi${mode.mode}SystemPrompt`], mode.id).toBe(`${mode.task} の system。`)
      expect(settings[`geometryAi${mode.mode}TaskTemplate`], mode.id)
        .toBe(`{mode} の条件から作図します（${mode.task}）。`)
    }
  })

  it('作図モードの Data TAB は自分のバッチコード（batC51-A〜D）の出力 DTO を要求する', async () => {
    const { wrapper } = setup()
    await flushPromises()

    for (const mode of MODE_CARDS) {
      const panel = wrapper.get(`[data-setting-subsection="${mode.id}"] [data-ai-data-schema]`)
      expect(panel.attributes('data-ai-data-schema'), mode.id).toBe(mode.task)
      expect(panel.text(), mode.id).toContain(`BatC51${mode.mode}ResultDto`)
    }
    // 実際に要求したバッチコード（モード 4 件＋AI 画図助手 1 件。前処理・検証・共通規則は持たない）
    expect([...schemaTasks].sort()).toEqual(['batC51-A', 'batC51-B', 'batC51-C', 'batC51-D', 'batC52'])
  }, 20000)

  it('モード別の 28 キーを 1 回ずつ送り、対応表に無いキーは送らない（空は共通の設定を継承する）', async () => {
    const { wrapper } = setup({ init: ENABLED_INIT })
    await flushPromises()

    // モード別の数字もスライダー（刻みと単位は共通レイアウト `aiSliderOf` が決める）
    for (const mode of MODE_CARDS) {
      const timeout = wrapper.get(`#setting_geometryAi${mode.mode}RequestTimeoutSeconds`)
      expect(timeout.attributes('type'), mode.id).toBe('range')
      expect(timeout.attributes('step'), mode.id).toBe('30')
      expect(timeout.attributes('data-suffix'), mode.id).toBe('s')
      expect(timeout.attributes('max'), mode.id).toBe('600')
      expect(wrapper.get(`#setting_geometryAi${mode.mode}Temperature`).attributes('step'), mode.id).toBe('0.1')
      expect(wrapper.get(`#setting_geometryAi${mode.mode}MaxCompletionTokens`).attributes('step'), mode.id)
        .toBe('512')
      expect(wrapper.get(`#setting_geometryAi${mode.mode}RetryLimit`).attributes('data-suffix'), mode.id)
        .toBe('回')
    }

    // 空のまま（＝共通の設定を継承する）では既定値で埋めない
    await wrapper.get('[data-gm-set-save]').trigger('click')
    await flushPromises()
    const blank = saved.body?.settings as Record<string, string>
    expect(blank.geometryAiARequestTimeoutSeconds).toBe('')
    expect(blank.geometryAiAMaxCompletionTokens).toBe('')
    expect(blank.geometryAiATemperature).toBe('')
    expect(blank.geometryAiARetryLimit).toBe('')
    expect(blank.geometryAiDSystemPrompt).toBe('')
    expect(blank.geometryAiDTaskTemplate).toBe('')

    // 入力したモードの値だけが入る（ほかのモードには漏れない）
    await wrapper.get('[data-gm-set-b-request-timeout]').setValue('300')
    await wrapper.get('[data-gm-set-b-temperature]').setValue('0.7')
    await wrapper.get('[data-gm-set-b-retry]').setValue('3')
    await wrapper.get('[data-gm-set-b-task-template]').setValue('{mode} の式から作図。')
    await wrapper.get('[data-gm-set-save]').trigger('click')
    await flushPromises()
    const settings = saved.body?.settings as Record<string, string>
    expect(settings.geometryAiBRequestTimeoutSeconds).toBe('300')
    expect(settings.geometryAiBTemperature).toBe('0.7')
    expect(settings.geometryAiBRetryLimit).toBe('3')
    expect(settings.geometryAiBTaskTemplate).toBe('{mode} の式から作図。')
    expect(settings.geometryAiARequestTimeoutSeconds).toBe('')

    // 送るキーは 54 件（重複なし）。対応表（SettingPageFields）にあるキーだけを送る
    const keys = Object.keys(settings)
    expect(keys.length).toBe(new Set(keys).size)
    expect([...keys].sort()).toEqual(GEOMETRY_AI_KEYS)
    for (const key of MODE_KEYS) {
      expect(keys.filter((sent) => sent === key), key).toHaveLength(1)
    }
    const fields = readFileSync(
      path.join(webRoot, '..', '..', 'backend', 'admin-api', 'src', 'main', 'java', 'com', 'study21',
        'admin', 'setting', 'SettingPageFields.java'), 'utf8'
    )
    const allowed = [...fields.matchAll(/put\(m, "(geometryAi\w+)",\s+"GEOMETRY_AI"/g)]
      .map((match) => match[1] as string)
    for (const key of keys) {
      expect(allowed, `${key} が対応表に無い（400 になる）`).toContain(key)
    }
  })

  it('数字の項目はすべてスライダー（範囲・刻み・単位・右側の値表示はカタログと共通レイアウトに一致）', async () => {
    const { wrapper } = setup()

    for (const slider of AI_SLIDERS) {
      const input = wrapper.get(`#setting_${slider.key}`)
      expect(input.attributes('type'), slider.key).toBe('range')
      expect(input.attributes('min'), slider.key).toBe(slider.min)
      expect(input.attributes('max'), slider.key).toBe(slider.max)
      expect(input.attributes('step'), slider.key).toBe(slider.step)
      // 単位はラベルではなく右側の値表示で示す（ランタイムの updateRangeControl が読む）
      expect(input.attributes('data-suffix'), slider.key).toBe(slider.suffix)
      // 数字の項目はスライダー用のクラスを付ける（ランタイムと同じマークアップ）
      expect(input.element.closest('.setting-field')?.className, slider.key)
        .toContain('setting-range-field')
      // 塗り具合はランタイムと同じ CSS 変数で表す
      expect(input.attributes('style'), slider.key).toContain('--setting-range-progress')
      // 今のテスト用フック（data-gm-set-*）は同じ input に残す
      const hooked = wrapper.get(`[${slider.hook}]`)
      expect(hooked.attributes('id'), slider.hook).toBe(`setting_${slider.key}`)

      const output = wrapper.get(`#setting_${slider.key}_value`)
      expect(output.attributes('for')).toBe(`setting_${slider.key}`)
      expect(output.text(), slider.key).toBe(slider.text)
    }
  })

  it('AI を呼ばないカード（前処理・検証）の数字も、同じ見た目のスライダーにする', async () => {
    const { wrapper } = setup()

    // 数字の入力欄は 1 つも残っていない（すべてスライダー）
    expect(wrapper.findAll('input[type="number"]')).toHaveLength(0)
    for (const key of ['geometryAiMaxImageMb', 'geometryAiMaxImagePixels',
      'geometryAiImageRetentionDays', 'geometryAiMaxCommands']) {
      const input = wrapper.get(`#setting_${key}`)
      expect(input.attributes('type'), key).toBe('range')
      expect(input.element.closest('.setting-field')?.className, key).toContain('setting-range-field')
    }
  })

  it('共通規則のカードに「空は共通の設定を使う」案内を出し、モードの欄にも同じ案内を書く', async () => {
    const { wrapper } = setup()

    // 案内は共通規則のカードにだけ出す（継承の規則とモードのプロンプトは空でよいこと）
    const notes = wrapper.findAll('[data-gm-set-inherit-note]')
    expect(notes).toHaveLength(1)
    const note = notes[0].text()
    expect(note).toContain('空のときはこの共通の設定をそのまま使います')
    expect(note).toContain('空のままで構いません')
    expect(wrapper.find('[data-setting-subsection="geometry-ai-common"] [data-gm-set-inherit-note]').exists())
      .toBe(true)

    // モード別カードの各項目の説明にも「空のときは共通の設定を使います」と書いてある
    // （Data TAB の説明 `.ai-data-schema__note` も `setting-help` なので、項目の説明だけを取る）
    for (const mode of MODE_CARDS) {
      const helps = wrapper.findAll(`[data-setting-subsection="${mode.id}"] .setting-field .setting-help`)
        .map((node) => node.text())
      expect(helps.length, mode.id).toBe(7)
      for (const help of helps) {
        expect(help, `${mode.id}: ${help}`).toContain('空のときは共通の設定を使います')
      }
    }
  })

  it('各項目の説明（hint）は 1 行で、事実に沿っている', async () => {
    const { wrapper } = setup()

    const hints = wrapper.findAll('[data-gm-ai-settings] .setting-help').map((node) => node.text())
    expect(hints.length).toBeGreaterThanOrEqual(20)
    for (const hint of hints) {
      expect(hint.length, `hint が長すぎる: ${hint}`).toBeLessThan(80)
    }
    const all = hints.join(' ')
    expect(all).toContain('同時に処理する AI 生図の件数です')
    expect(all).toContain('取り込んだ画像を保持する日数です')
    expect(all).toContain('ここに無いコマンドは検証の工程で弾かれます')
    expect(all).toContain('1 回の指示で受け付けるコマンド数の上限です')
  })

  it('有効／無効のスイッチは出さない（読み込んだ値をそのまま保持する）', async () => {
    const { wrapper } = setup({ init: ENABLED_INIT })
    await flushPromises()
    expect(wrapper.find('[data-gm-set-enabled]').exists()).toBe(false)
    expect(wrapper.find('[data-gm-set-assist-enabled]').exists()).toBe(false)

    await wrapper.get('[data-gm-set-save]').trigger('click')
    await flushPromises()
    const settings = saved.body?.settings as Record<string, string>
    expect(settings.geometryAiEnabled).toBe('true')
    expect(settings.geometryAiAssistEnabled).toBe('true')

    // サーバーが値を返さないときは送らない（空文字で上書きしない）
    const blank = setup()
    await flushPromises()
    await blank.wrapper.get('[data-gm-set-save]').trigger('click')
    await flushPromises()
    const blankSettings = saved.body?.settings as Record<string, string>
    expect(blankSettings).not.toHaveProperty('geometryAiEnabled')
    expect(blankSettings).not.toHaveProperty('geometryAiAssistEnabled')
  })

  it('保存ボタンで 54 キーを送り、結果を日本語で出す', async () => {
    const { wrapper } = setup({ init: ENABLED_INIT })
    await flushPromises()

    const save = wrapper.get('[data-gm-set-save]')
    expect(save.attributes('disabled')).toBeUndefined()
    expect(save.text()).toContain('この設定を保存')

    await wrapper.get('[data-gm-set-model]').setValue('chatgpt:1')
    await wrapper.get('[data-gm-set-max-pixels]').setValue('2048')
    await wrapper.get('[data-gm-set-retention]').setValue('90')
    await wrapper.get('[data-gm-set-temperature]').setValue('0.5')
    await wrapper.get('[data-gm-set-max-concurrency]').setValue('3')
    await wrapper.get('[data-gm-set-max-commands]').setValue('90')
    await wrapper.get('[data-gm-set-assist-max-commands]').setValue('30')
    await save.trigger('click')
    await flushPromises()

    const settings = saved.body?.settings as Record<string, string>
    expect(Object.keys(settings).sort()).toEqual(GEOMETRY_AI_KEYS)
    expect(settings.geometryAiProvider).toBe('chatgpt:1')
    expect(settings.geometryAiMaxImagePixels).toBe('2048')
    expect(settings.geometryAiImageRetentionDays).toBe('90')
    expect(settings.geometryAiTemperature).toBe('0.5')
    expect(settings.geometryAiMaxConcurrency).toBe('3')
    expect(settings.geometryAiMaxCommands).toBe('90')
    expect(settings.geometryAiAssistMaxCommands).toBe('30')
    expect(saved.body?.userId).toBe('setting.jsp')
    expect(wrapper.get('[data-gm-set-save-note]').text()).toContain('設定を保存しました。')
  })

  it('検証エラー（400）の理由を画面に出す（保存できなかったことを隠さない）', async () => {
    const { wrapper } = setup({ saveError: '設定ページに存在しないキーです: geometryAiUnknown' })
    await flushPromises()

    await wrapper.get('[data-gm-set-save]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-gm-set-save-note]').text())
      .toContain('設定ページに存在しないキーです: geometryAiUnknown')
  })

  it('サーバーの値を読み込んで画面に反映する（画面に出さないキーも保持する）', async () => {
    const { wrapper } = setup({
      init: {
        ...ENABLED_INIT,
        geometryAiMaxImageMb: '25',
        geometryAiMaxImagePixels: '4096',
        geometryAiImageRetentionDays: '90',
        geometryAiProvider: 'chatgpt:2',
        geometryAiOutputFormat: 'COMMAND',
        geometryAiTemperature: '0.7',
        geometryAiMaxCompletionTokens: '8192',
        geometryAiRequestTimeoutSeconds: '600',
        geometryAiSystemPrompt: 'コマンドだけを出してください。',
        geometryAiInstructionTemplate: '三角形を作図してください。',
        geometryAiRetryLimit: '3',
        geometryAiDailyLimitPerAccount: '5',
        geometryAiMaxConcurrency: '2',
        geometryAiApproval: 'auto',
        geometryAiAllowedCommands: 'Point,Segment',
        geometryAiMaxCommands: '50',
        geometryAiAssistProvider: 'deepseek:2',
        geometryAiAssistTimeoutSeconds: '120',
        geometryAiAssistMaxCommands: '10',
        geometryAiAssistDailyLimitPerAccount: '0',
        geometryAiAssistSystemPrompt: '指示をコマンドに直してください。',
        geometryAiAssistUserPrompt: '指示: {instruction}',
        // 作図モード別（空の項目は共通の設定を継承する）
        geometryAiAProvider: 'deepseek:1',
        geometryAiARequestTimeoutSeconds: '300',
        geometryAiATemperature: '0.9',
        geometryAiASystemPrompt: 'A 用の system プロンプト。',
        geometryAiATaskTemplate: 'A 用のテンプレート {mode}。',
        geometryAiBRetryLimit: '2',
        geometryAiCMaxCompletionTokens: '2048'
      }
    })
    await flushPromises()

    expect((wrapper.get('[data-gm-set-max-size]').element as HTMLInputElement).value).toBe('25')
    expect((wrapper.get('[data-gm-set-max-pixels]').element as HTMLInputElement).value).toBe('4096')
    expect((wrapper.get('[data-gm-set-retention]').element as HTMLInputElement).value).toBe('90')
    expect((wrapper.get('[data-gm-set-model]').element as HTMLSelectElement).value).toBe('chatgpt:2')
    expect((wrapper.get('[data-gm-set-output-format]').element as HTMLSelectElement).value).toBe('COMMAND')
    // Temperature は旧 select（0.0〜1.0 の ENUM）から共通レイアウトのスライダーになった
    expect((wrapper.get('[data-gm-set-temperature]').element as HTMLInputElement).value).toBe('0.7')
    expect((wrapper.get('[data-gm-set-max-tokens]').element as HTMLInputElement).value).toBe('8192')
    expect((wrapper.get('[data-gm-set-request-timeout]').element as HTMLInputElement).value).toBe('600')
    expect((wrapper.get('[data-gm-set-system-prompt]').element as HTMLTextAreaElement).value)
      .toBe('コマンドだけを出してください。')
    expect((wrapper.get('[data-gm-set-template]').element as HTMLTextAreaElement).value)
      .toBe('三角形を作図してください。')
    expect((wrapper.get('[data-gm-set-retry]').element as HTMLInputElement).value).toBe('3')
    expect((wrapper.get('[data-gm-set-daily-limit]').element as HTMLInputElement).value).toBe('5')
    expect((wrapper.get('[data-gm-set-max-concurrency]').element as HTMLInputElement).value).toBe('2')
    expect((wrapper.get('[data-gm-set-approval="auto"]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.get('[data-gm-set-allowed-commands]').element as HTMLTextAreaElement).value)
      .toBe('Point,Segment')
    expect((wrapper.get('[data-gm-set-max-commands]').element as HTMLInputElement).value).toBe('50')
    expect((wrapper.get('[data-gm-set-assist-provider]').element as HTMLSelectElement).value).toBe('deepseek:2')
    expect((wrapper.get('[data-gm-set-assist-timeout]').element as HTMLInputElement).value).toBe('120')
    expect((wrapper.get('[data-gm-set-assist-max-commands]').element as HTMLInputElement).value).toBe('10')
    expect((wrapper.get('[data-gm-set-assist-daily-limit]').element as HTMLInputElement).value).toBe('0')
    expect((wrapper.get('[data-gm-set-assist-system-prompt]').element as HTMLTextAreaElement).value)
      .toBe('指示をコマンドに直してください。')
    expect((wrapper.get('[data-gm-set-assist-user-prompt]').element as HTMLTextAreaElement).value)
      .toBe('指示: {instruction}')
    // 作図モード別の欄（空＝共通の設定を継承する。読み込んだ値はモード別の欄に入る）
    expect((wrapper.get('[data-gm-set-a-provider]').element as HTMLSelectElement).value).toBe('deepseek:1')
    expect((wrapper.get('[data-gm-set-a-request-timeout]').element as HTMLInputElement).value).toBe('300')
    expect((wrapper.get('[data-gm-set-a-temperature]').element as HTMLInputElement).value).toBe('0.9')
    expect((wrapper.get('[data-gm-set-a-system-prompt]').element as HTMLTextAreaElement).value)
      .toBe('A 用の system プロンプト。')
    expect((wrapper.get('[data-gm-set-a-task-template]').element as HTMLTextAreaElement).value)
      .toBe('A 用のテンプレート {mode}。')
    expect((wrapper.get('[data-gm-set-b-retry]').element as HTMLInputElement).value).toBe('2')
    expect((wrapper.get('[data-gm-set-c-max-tokens]').element as HTMLInputElement).value).toBe('2048')
    // サーバーが値を返さなかったモードの欄は空のまま（勝手に共通の値を書き込まない）
    expect((wrapper.get('[data-gm-set-d-system-prompt]').element as HTMLTextAreaElement).value).toBe('')

    // 読み込んだ値をそのまま送り返す（画面に出さない有効／無効も失わない）
    await wrapper.get('[data-gm-set-save]').trigger('click')
    await flushPromises()
    const settings = saved.body?.settings as Record<string, string>
    expect(settings.geometryAiEnabled).toBe('true')
    expect(settings.geometryAiAssistEnabled).toBe('true')
    expect(settings.geometryAiAssistProvider).toBe('deepseek:2')
    expect(settings.geometryAiAllowedCommands).toBe('Point,Segment')
    expect(settings.geometryAiAProvider).toBe('deepseek:1')
    expect(settings.geometryAiARequestTimeoutSeconds).toBe('300')
    expect(settings.geometryAiASystemPrompt).toBe('A 用の system プロンプト。')
    expect(settings.geometryAiBRetryLimit).toBe('2')
    expect(settings.geometryAiDSystemPrompt).toBe('')
  })

  it('画面全体の【設定を保存】に載せるため registerSection に登録する', async () => {
    const registered: { getValues?: () => Record<string, string>; applyValues?: (values: Record<string, string>) => void }[] = []
    window.__study21SystemSettings = {
      mount: () => undefined,
      registerSection: (section) => { registered.push(section) },
      collectValues: () => ({})
    }

    const { wrapper } = setup({ init: ENABLED_INIT })
    await flushPromises()

    expect(registered).toHaveLength(1)
    // 画面全体の保存に載る値（GEOMETRY_AI の 54 キー）
    expect(Object.keys(registered[0].getValues?.() ?? {}).sort()).toEqual(GEOMETRY_AI_KEYS)
    // 再読込でサーバーの値が画面に入る（ランタイムの applyValues から呼ばれる）
    registered[0].applyValues?.({ geometryAiMaxImageMb: '45' })
    await flushPromises()
    expect((wrapper.get('[data-gm-set-max-size]').element as HTMLInputElement).value).toBe('45')
  })

  it('カードの見出しに廃止した batC53 を出さず、batC51 と batC52 を出す', () => {
    const { wrapper } = setup()

    const text = wrapper.get('[data-gm-ai-settings]').text()
    expect(text).not.toContain('batC53')
    expect(text).not.toContain('batC54')
    // 共通規則のカードが AI 生成（batC51）のブロックだと書いている
    expect(text).toContain('batC51（AI 生成）')
    expect(text).toContain('batC52')
    for (const mode of MODE_CARDS) {
      expect(text).toContain(mode.title)
    }
  })

  it('設定の定義（対応表・カタログ）が 54 キーで揃っている', () => {
    const runtime = readFileSync(
      path.join(webRoot, 'src', 'features', 'system-settings', 'study2SettingRuntime.ts'), 'utf8'
    )
    const fields = readFileSync(
      path.join(webRoot, '..', '..', 'backend', 'admin-api', 'src', 'main', 'java', 'com', 'study21',
        'admin', 'setting', 'SettingPageFields.java'), 'utf8'
    )
    const catalog = readFileSync(
      path.join(webRoot, '..', '..', 'database', '設定', 'TBL_COM_設定項目_init.sql'), 'utf8'
    )

    // 画面が送るキーは、サーバーの対応表が許可しているキーの部分集合でなければ 400 になる
    const allowed = [...fields.matchAll(/put\(m, "(geometryAi\w+)",\s+"GEOMETRY_AI"/g)]
      .map((match) => match[1] as string)
    for (const key of GEOMETRY_AI_KEYS) {
      expect(allowed, `${key} が対応表に無い`).toContain(key)
    }
    expect(GEOMETRY_AI_KEYS.length).toBe(54)
    expect(LEGACY_KEYS.length).toBe(26)
    expect(MODE_KEYS.length).toBe(28)
    expect(new Set(MODE_KEYS).size).toBe(28)
    expect([...allowed].sort()).toEqual([...GEOMETRY_AI_KEYS].sort())
    // 対応表の (フィールドキー → DB キー) を読み、既存 26 キーはカタログ（COM_設定項目）に行があること
    const pairs = [...fields.matchAll(
      /put\(m, "(geometryAi\w+)",\s+"GEOMETRY_AI",\s+"(GEOMETRY_AI_\w+)"\)/g
    )].map((match) => ({ fieldKey: match[1] as string, dbKey: match[2] as string }))
    const catalogKeys = [...catalog.matchAll(/'GEOMETRY_AI','(GEOMETRY_AI_\w+)'/g)]
      .map((match) => match[1] as string)
    for (const key of LEGACY_KEYS) {
      const dbKey = pairs.find((pair) => pair.fieldKey === key)?.dbKey
      expect(dbKey, `${key} が対応表に無い`).toBeDefined()
      expect(catalogKeys, `${dbKey} がカタログに無い`).toContain(dbKey)
    }
    // 作図モード別の 28 キーも対応表にはある（モードごとに 1 行ずつ）
    for (const key of MODE_KEYS) {
      expect(pairs.filter((pair) => pair.fieldKey === key), key).toHaveLength(1)
    }
    // モード別の 28 キーのカタログ行は**移行 SQL**
    // （database/移行/MIG_GEO_AI作図モードと結果種別_20260918.sql）が足す。
    // init（2.0 からの種）は既存 26 行のままなので、ここでは init が 26 行であることだけを見る。
    expect(catalogKeys.length).toBe(26)
    const migration = readFileSync(
      path.join(repoRoot, 'database', '移行', 'MIG_GEO_AI作図モードと結果種別_20260918.sql'),
      'utf8'
    )
    for (const key of MODE_KEYS) {
      const dbKey = pairs.find((pair) => pair.fieldKey === key)?.dbKey
      expect(dbKey, `${key} が対応表に無い`).toBeDefined()
      // カタログと値の両方に 1 回ずつ入っている（空の値＝共通を継承）
      const occurrences = migration.split(`'${dbKey}'`).length - 1
      expect(occurrences, `${dbKey} の行が移行 SQL に無い`).toBeGreaterThanOrEqual(2)
    }
    // ランタイム側の分類は external（ナビに出して、項目の描画はこのコンポーネントが行う）
    expect(runtime).toContain("id: 'geometry_ai'")
    expect(runtime).toContain('external: true')
    expect(runtime).toContain('data-external-slot=')
  })

  it('【既定に戻す】で共通は既定値・モード別は空（＝共通を継承）に戻る', () => {
    const source = readFileSync(
      path.join(webRoot, 'src', 'views', 'admin', 'system-settings', 'GeometryAiSettingsSection.vue'),
      'utf8'
    )
    // ボタンがあり、モード別は空へ・共通は DEFAULT_VALUES へ戻す
    expect(source).toContain('data-gm-set-reset')
    expect(source).toContain('function resetToDefaults()')
    expect(source).toMatch(/next\[key\] = MODE_INHERIT_KEYS\.has\(key\) \? '' : DEFAULT_VALUES\[key\]/)
  })

  it('ブラウザの保管領域には保存しない（サーバーにだけ保存する）', () => {
    const source = readFileSync(
      path.join(webRoot, 'src', 'views', 'admin', 'system-settings', 'GeometryAiSettingsSection.vue'),
      'utf8'
    )

    expect(source).not.toContain('localStorage')
    expect(source).not.toContain('sessionStorage')
    expect(source).not.toContain('fetch(')
    // 保存は API モジュールを通す
    expect(source).toContain('saveSettingFields')
  })
})

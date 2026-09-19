import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SystemSettingsView from '@/views/admin/system-settings/SystemSettingsView.vue'

/**
 * 設定ページの AI バッチのブロックが**共通レイアウト**（aiSettingsLayout.ts）で描かれること。
 *
 * 確かめること:
 * - TAB は 基本設定 / System Prompt / User Prompt / その他（項目の無い TAB は出さない）
 * - 基本設定は「使用モデル → リクエストタイムアウト → Temperature → 最大出力Token数」
 * - その他は「最大再実行回数 → スレッド数 → 画像条件 → 1回の最大処理数」
 * - 名称は統一（使用AIモデル（複数可）→ 使用モデル など）、数字はすべてスライダー
 * - AI を使わないブロック（batL02・翻訳など）は今のまま
 */
function jsonResponse(data: unknown): Response {
  return new Response(
    JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
}

function setup(): VueWrapper {
  const fetchMock = vi.fn(() => Promise.resolve(jsonResponse({ settings: {} })))
  vi.stubGlobal('fetch', fetchMock)
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(SystemSettingsView, { global: { plugins: [pinia] }, attachTo: document.body })
}

/** 分類のパネル（同じ id のブロックが別の分類にもあるので、必ず分類の中を探す）。 */
const panel = (categoryId: string): HTMLElement =>
  document.querySelector(`[data-panel="${categoryId}"]`) as HTMLElement

const section = (categoryId: string, sectionId: string): HTMLElement =>
  panel(categoryId).querySelector(`[data-setting-subsection="${sectionId}"]`) as HTMLElement

/** TAB のボタン名（表示順）。 */
const tabsOf = (categoryId: string, sectionId: string): string[] =>
  [...section(categoryId, sectionId).querySelectorAll('[data-method-tab]')]
    .map((button) => button.textContent?.trim() ?? '')

/** TAB の中の項目ラベル（表示順）。 */
const labelsOf = (categoryId: string, sectionId: string, tab: string): string[] =>
  [...section(categoryId, sectionId)
    .querySelector(`[data-method-panel="${tab}"]`)!
    .querySelectorAll('.setting-label span')]
    .map((item) => item.textContent?.trim() ?? '')

/** 1 項目のラッパー（`.setting-field`）。 */
const fieldOf = (key: string): HTMLElement =>
  document.getElementById(`setting_${key}`)!.closest('.setting-field') as HTMLElement

/** 1 項目のコントロール（id="setting_<key>"）。 */
const control = (key: string): HTMLInputElement =>
  document.getElementById(`setting_${key}`) as HTMLInputElement

async function selectCategory(id: string): Promise<void> {
  ;(document.querySelector(`#settingCategoryNav [data-category="${id}"]`) as HTMLElement).click()
  await flushPromises()
}

describe('システム設定：AI バッチの共通レイアウト', () => {
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

  async function open(categoryId: string): Promise<void> {
    wrapper = setup()
    await flushPromises()
    await selectCategory(categoryId)
  }

  it('batC04 は 4 つの TAB に分かれ、モデルは基本設定・スレッド数はその他へ入る', async () => {
    await open('batchai')

    expect(tabsOf('batchai', 'bat-c04')).toEqual(['基本設定', 'System Prompt', 'User Prompt', 'その他'])
    expect(labelsOf('batchai', 'bat-c04', '基本設定')).toEqual(['使用モデル'])
    expect(labelsOf('batchai', 'bat-c04', 'その他')).toEqual(['スレッド数'])
    // スライダーになっている（単位は右側の値表示）
    expect(control('c04Threads').type).toBe('range')
    expect(control('c04Threads').step).toBe('1')
  })

  it('項目の無い TAB は出さない（batC05 は User Prompt を出さない）', async () => {
    await open('batchai')

    expect(tabsOf('batchai', 'bat-c05')).toEqual(['基本設定', 'System Prompt', 'その他'])
    expect(labelsOf('batchai', 'bat-c05', '基本設定')).toEqual(['使用モデル']) // 旧「使用AIモデル（複数可）」
    expect(labelsOf('batchai', 'bat-c05', 'その他')).toEqual(['スレッド数'])
  })

  it('基本設定は 使用モデル → リクエストタイムアウト → Temperature → 最大出力Token数 の順', async () => {
    await open('japanese_word_ai')

    expect(labelsOf('japanese_word_ai', 'detail', '基本設定')).toEqual([
      '使用モデル',
      'リクエストタイムアウト',
      'Temperature',
      '最大出力Token数'
    ])
    // 全部スライダーで、刻みと単位も揃っている
    expect(control('c25RequestTimeoutSeconds').type).toBe('range')
    expect(control('c25RequestTimeoutSeconds').step).toBe('30')
    expect(control('c25RequestTimeoutSeconds').dataset.suffix).toBe('s')
    expect(control('c25Temperature').step).toBe('0.1')
    expect(control('c25MaxCompletionTokens').step).toBe('512')
  })

  it('その他は 最大再実行回数 → スレッド数 → 1回の最大処理数 の順（単位はブロックごと）', async () => {
    await open('japanese_word_ai')

    expect(labelsOf('japanese_word_ai', 'detail', 'その他')).toEqual([
      '最大再実行回数',
      'スレッド数',
      '1回の最大処理数'
    ])
    expect(control('c25RetryLimit').dataset.suffix).toBe('回')
    expect(control('c25BatchMax').dataset.suffix).toBe('語')
  })

  it('画像条件もその他へ入り、スライダーの単位が付く', async () => {
    await open('english_cloze')

    expect(tabsOf('english_cloze', 'bat-c13')).toEqual(['基本設定', 'System Prompt', 'User Prompt', 'その他'])
    expect(labelsOf('english_cloze', 'bat-c13', 'その他')).toEqual([
      '最大再実行回数',
      '最大画像枚数',
      '画像1枚の最大サイズ',
      'AI送信画像の最大辺'
    ])
    expect(control('clozeMaxImages').dataset.suffix).toBe('枚')
    expect(control('clozeMaxImageMb').dataset.suffix).toBe('MB')
    expect(control('clozeOcrMaxImagePixels').dataset.suffix).toBe('px')
    expect(control('clozeOcrMaxImagePixels').step).toBe('512')
  })

  it('AI を使わないブロック（batL02）は今のまま', async () => {
    await open('study_monitor')

    expect(tabsOf('study_monitor', 'bat-l02')).toEqual(['基本設定', 'カメラ基本情報'])
    expect(labelsOf('study_monitor', 'bat-l02', '基本設定')).toContain('スナップショット間隔（秒）')
    // batL02 の**実行設定**（バッチをいつ動かすか）は 2026-09-19 に足した。
    // 動画処理時間帯・スナップショット間隔とは別の欄（AI を使わないので共通レイアウトの対象外）
    expect(labelsOf('study_monitor', 'bat-l02', '基本設定')).toEqual(expect.arrayContaining([
      '動画処理時間帯（開始）',
      '動画処理時間帯（終了）',
      '実行間隔（バッチを動かす間隔）',
      'ずらし（毎時の何分に実行するか）'
    ]))
    // AI 分析（batL03）は共通レイアウト
    expect(tabsOf('study_monitor', 'bat-l03')).toEqual([
      '基本設定',
      'System Prompt',
      'User Prompt',
      'その他'
    ])
    // 実行設定（間隔・ずらし）は数値のスライダーではない（6 択のラジオ）ので、AI の数字項目の
    // 後ろ＝同じ 基本設定 TAB の末尾に並ぶ。名前が `…Timeout` / `…Provider` に似ていても、
    // AI の通信条件ではないので「その他」TAB へは入れない
    expect(labelsOf('study_monitor', 'bat-l03', '基本設定')).toEqual([
      '使用モデル',
      'リクエストタイムアウト',
      '実行間隔（バッチを動かす間隔）',
      'ずらし（毎時の何分に実行するか）'
    ])
    expect(labelsOf('study_monitor', 'bat-l03', 'その他')).toEqual([
      'スレッド数',
      '1回の最大処理数',
      'AI送信画像解像度'
    ])
  })

  it('モデルの選択はどのブロックでも 1 行（full）で出る', async () => {
    // runtime が描く AI ブロックは 27 個あり、モデルの選択だけが例外なく 1 行になる
    await open('batchai')
    expect(fieldOf('c04AiModel').classList.contains('full')).toBe(true)

    // ここだけ今まで半行だった（表記ゆれの残り）＝規則を当てて揃える
    await selectCategory('english_reading_intensive')
    expect(fieldOf('intensiveMethodAArticleOcrProvider').classList.contains('full')).toBe(true)
    expect(fieldOf('intensiveMethodBOcrProvider').classList.contains('full')).toBe(true)

    await selectCategory('japanese_word_ai')
    expect(fieldOf('c25AiProvider').classList.contains('full')).toBe(true)
    // 数字（スライダー）は 2 列のまま
    expect(fieldOf('c25Temperature').classList.contains('full')).toBe(false)
  })

  it('AI 以外の分類（翻訳など）の数字も、同じ規則でスライダーにする', async () => {
    await open('translation')

    for (const key of ['zhThreads', 'jaThreads', 'enVoiceThreads']) {
      expect(control(key).type, key).toBe('range')
      expect(control(key).step, key).toBe('1')
      expect(control(key).min, key).toBe('1')
      expect(control(key).max, key).toBe('10')
    }
  })
})

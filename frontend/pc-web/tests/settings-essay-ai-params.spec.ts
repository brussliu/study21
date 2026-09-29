import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SystemSettingsView from '@/views/admin/system-settings/SystemSettingsView.vue'

/**
 * 設定ページ「英作文AI添削」の**AI の実行パラメータ**（Temperature / 最大出力Token数）。
 *
 * <p>もとはコード固定だった値（`EnglishEssayOcrStep` の `TEMPERATURE` など）を、設定ページから
 * 変えられるようにした。画面に出ていないと保存できない（`collectValues` は描かれた欄だけを送る）ので、
 * **4 項目が出ること**と**保存に載ること**をここで固定する。</p>
 *
 * <p>確かめる接縫:</p>
 * <ol>
 *   <li>batC11（OCR）と batC12（添削）の「基本設定」に、Temperature と最大出力Token数が出る
 *       （並びは共通レイアウト: 使用モデル → リクエストタイムアウト → Temperature → 最大出力Token数）</li>
 *   <li>数値はスライダーで、刻みと範囲がサーバーの受け付け範囲と一致する
 *       （Temperature 0.0〜2.0 を 0.1 刻み / 最大出力Token数 1024〜65536 を 512 刻み）</li>
 *   <li>サーバーの値が画面に入り、【設定を保存】で同じキー名で送られる
 *       （キーはバックエンドの対応表 `SettingPageFields` と同じ `essay…` の形）</li>
 * </ol>
 */

function jsonResponse(data: unknown, message = 'OK'): Response {
  return new Response(
    JSON.stringify({ success: true, code: 'OK', message, data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
}

/** 画面を実 DOM に載せ、保存で送られた設定を捕まえる。 */
function setup(settings: Record<string, string> = {}): {
  wrapper: VueWrapper
  saved: Array<Record<string, string>>
} {
  const saved: Array<Record<string, string>> = []
  const fetchMock = vi.fn((url: string, request?: RequestInit) => {
    if (String(url).includes('/saveSettings')) {
      const body = JSON.parse(String(request?.body ?? '{}')) as { settings?: Record<string, string> }
      saved.push(body.settings ?? {})
      return Promise.resolve(jsonResponse({ settings: body.settings ?? {} }, '設定を保存しました。'))
    }
    if (String(url).includes('/initSettings')) {
      return Promise.resolve(jsonResponse({ settings }))
    }
    return Promise.resolve(jsonResponse({}))
  })
  vi.stubGlobal('fetch', fetchMock)
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(SystemSettingsView, { global: { plugins: [pinia] }, attachTo: document.body })
  return { wrapper, saved }
}

/** 分類のパネル（同じ id のブロックが別の分類にもあるので、必ず分類の中を探す）。 */
const section = (sectionId: string): HTMLElement =>
  document.querySelector(`[data-panel="essay"] [data-setting-subsection="${sectionId}"]`) as HTMLElement

/** TAB の中の項目ラベル（表示順）。 */
const labelsOf = (sectionId: string, tab: string): string[] =>
  [...section(sectionId).querySelector(`[data-method-panel="${tab}"]`)!
    .querySelectorAll('.setting-label span')]
    .map((item) => item.textContent?.trim() ?? '')

/** 1 項目のコントロール（id="setting_<key>"）。 */
const control = (key: string): HTMLInputElement =>
  document.getElementById(`setting_${key}`) as HTMLInputElement

/** スライダーを動かす（実際の操作と同じ input を投げる）。 */
async function slide(key: string, value: string): Promise<void> {
  const input = control(key)
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await flushPromises()
}

/** 英作文AI添削の分類を開く。 */
async function openEssay(): Promise<void> {
  ;(document.querySelector('#settingCategoryNav [data-category="essay"]') as HTMLElement).click()
  await flushPromises()
}

const OCR_TEMPERATURE = 'essayOcrTemperature'
const OCR_MAX_TOKENS = 'essayOcrMaxCompletionTokens'
const GRADING_TEMPERATURE = 'essayGradingTemperature'
const GRADING_MAX_TOKENS = 'essayGradingMaxCompletionTokens'

describe('システム設定：英作文AI添削の実行パラメータ', () => {
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

  async function open(settings: Record<string, string> = {}): Promise<{
    saved: Array<Record<string, string>>
  }> {
    const context = setup(settings)
    wrapper = context.wrapper
    await flushPromises()
    await openEssay()
    return { saved: context.saved }
  }

  it('batC11（OCR）と batC12（添削）の基本設定に Temperature と最大出力Token数を出す', async () => {
    await open()

    // 並びは共通レイアウト（使用モデル → リクエストタイムアウト → Temperature → 最大出力Token数）
    expect(labelsOf('bat-c11', '基本設定')).toEqual([
      '使用モデル',
      'リクエストタイムアウト',
      'Temperature',
      '最大出力Token数'
    ])
    expect(labelsOf('bat-c12', '基本設定')).toEqual([
      '使用モデル',
      'リクエストタイムアウト',
      'Temperature',
      '最大出力Token数'
    ])

    // スライダーの範囲と刻みはサーバーが受け付ける範囲と同じ（Temperature は 0.1 刻み）
    expect(control(OCR_TEMPERATURE).type).toBe('range')
    expect(control(OCR_TEMPERATURE).min).toBe('0')
    expect(control(OCR_TEMPERATURE).max).toBe('2')
    expect(control(OCR_TEMPERATURE).step).toBe('0.1')
    expect(control(OCR_MAX_TOKENS).type).toBe('range')
    expect(control(OCR_MAX_TOKENS).min).toBe('1024')
    expect(control(OCR_MAX_TOKENS).max).toBe('65536')
    expect(control(OCR_MAX_TOKENS).step).toBe('512')

    expect(control(GRADING_TEMPERATURE).min).toBe('0')
    expect(control(GRADING_TEMPERATURE).max).toBe('2')
    expect(control(GRADING_TEMPERATURE).step).toBe('0.1')
    expect(control(GRADING_MAX_TOKENS).min).toBe('1024')
    expect(control(GRADING_MAX_TOKENS).max).toBe('65536')
    expect(control(GRADING_MAX_TOKENS).step).toBe('512')
  })

  it('保存値が画面に入り、【設定を保存】で同じキー名のまま送られる', async () => {
    const { saved } = await open({
      [OCR_TEMPERATURE]: '0.0',
      [OCR_MAX_TOKENS]: '4096',
      [GRADING_TEMPERATURE]: '0.2',
      [GRADING_MAX_TOKENS]: '8192'
    })

    // 保存値（サーバー）が画面に入る
    expect(control(OCR_TEMPERATURE).value).toBe('0')
    expect(control(OCR_MAX_TOKENS).value).toBe('4096')
    expect(control(GRADING_TEMPERATURE).value).toBe('0.2')
    expect(control(GRADING_MAX_TOKENS).value).toBe('8192')

    // 動かした値が保存に載る（キーはバックエンドの対応表と同じ `essay…`）
    await slide(OCR_TEMPERATURE, '0.4')
    await slide(OCR_MAX_TOKENS, '2048')
    await slide(GRADING_TEMPERATURE, '1')
    await slide(GRADING_MAX_TOKENS, '16384')

    ;(document.getElementById('settingSaveBtn') as HTMLButtonElement).click()
    await flushPromises()

    expect(saved).toHaveLength(1)
    expect(saved[0][OCR_TEMPERATURE]).toBe('0.4')
    expect(saved[0][OCR_MAX_TOKENS]).toBe('2048')
    expect(saved[0][GRADING_TEMPERATURE]).toBe('1')
    expect(saved[0][GRADING_MAX_TOKENS]).toBe('16384')
  })

  it('英作文の他の項目（画像の上限・タイムアウト）はそのまま残っている', async () => {
    await open()

    expect(labelsOf('bat-c11', 'その他')).toEqual([
      '最大再実行回数',
      '最大画像枚数',
      '画像1枚の最大サイズ',
      'AI送信画像の最大辺'
    ])
    expect(labelsOf('bat-c12', 'その他')).toEqual(['最大再実行回数'])
    expect(control('essayOcrRequestTimeoutSeconds').step).toBe('30')
    expect(control('essayMaxImages').max).toBe('20')
  })
})

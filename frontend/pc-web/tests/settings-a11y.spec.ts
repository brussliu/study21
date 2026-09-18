import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import SystemSettingsView from '@/views/admin/system-settings/SystemSettingsView.vue'

/**
 * システム設定ページ（/admin/setting）のアクセシビリティ。
 *
 * Chrome DevTools の Issues に出ていた次の 2 種類を、**DOM の不変条件**として固定する
 * （2026-09-19 に 18 件＋1 件を実測して直した。同じ形の項目を足すとまた出るので機械で見張る）。
 *
 *   1. Incorrect use of <label for=FORM_ELEMENT>
 *      `label[for]` は **label できる要素**（input / select / textarea / output など）を指すこと。
 *      ラジオやチェックボックスを**包む div**（`<div class="setting-radio-group" id="setting_xxx">`）を
 *      指してはいけない（グループの見出しは `<span class="setting-label">` ＋
 *      `role="radiogroup"` / `role="group"` ＋ `aria-labelledby` で結び付ける）。
 *   2. A form field element should have an id or name attribute
 *      `input` / `select` / `textarea` は id か name を持つこと。
 *
 * 描画は 3 か所に分かれる（設定ランタイムの `renderField`、AI生図分頁、AI授業記録分頁）ので、
 * ページ全体を 1 回載せて **document 全体**を検査する。
 */
const LABELABLE_TAGS = new Set(['INPUT', 'SELECT', 'TEXTAREA', 'BUTTON', 'METER', 'OUTPUT', 'PROGRESS'])
/** ラベルも名前も要らない入力（Autofill の対象外）。 */
const SKIP_INPUT_TYPES = new Set(['hidden', 'submit', 'button', 'reset', 'image'])

/** 「label の for が入力要素を指していない」を集める（DevTools と同じ判定）。 */
function labelForViolations(): string[] {
  const rows: string[] = []
  document.querySelectorAll('label[for]').forEach((label) => {
    const forValue = label.getAttribute('for') ?? ''
    const target = forValue === '' ? null : document.getElementById(forValue)
    if (target === null || !LABELABLE_TAGS.has(target.tagName)) {
      const why = target === null ? 'id が存在しない' : `${target.tagName.toLowerCase()} は label の対象になれない`
      const text = (label.textContent ?? '').replace(/\s+/g, ' ').trim()
      rows.push(`for="${forValue}"（${why}）: ${text}`)
    }
  })
  return rows
}

/** 「id も name も無い入力要素」を集める（DevTools と同じ判定）。 */
function fieldViolations(): string[] {
  const rows: string[] = []
  document.querySelectorAll('input, select, textarea').forEach((field) => {
    if (SKIP_INPUT_TYPES.has((field.getAttribute('type') ?? '').toLowerCase())) return
    const hasId = (field.getAttribute('id') ?? '') !== ''
    const hasName = (field.getAttribute('name') ?? '') !== ''
    if (!hasId && !hasName) rows.push(field.outerHTML.replace(/\s+/g, ' '))
  })
  return rows
}

function jsonResponse(data: unknown): Response {
  return new Response(
    JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
}

/** 設定ページを実 DOM に載せる（ランタイムが document から要素を引くため attachTo が要る）。 */
function setup(): VueWrapper {
  const fetchMock = vi.fn((url: string, request?: RequestInit) => {
    const body = request?.body === undefined ? null : (JSON.parse(String(request.body)) as Record<string, unknown>)
    if (String(url).includes('/saveSettings')) {
      return Promise.resolve(jsonResponse({ settings: body?.settings ?? {} }))
    }
    return Promise.resolve(jsonResponse({ settings: {}, fields: [], presets: [] }))
  })
  vi.stubGlobal('fetch', fetchMock)
  const pinia = createPinia()
  setActivePinia(pinia)
  return mount(SystemSettingsView, { global: { plugins: [pinia] }, attachTo: document.body })
}

describe('システム設定ページ：アクセシビリティ（label[for] と入力要素の id/name）', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    document.body.innerHTML = ''
    // 注意: `window.__study21SystemSettings` は消さない（画面がこれを mount するため）
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
    vi.restoreAllMocks()
  })

  async function open(): Promise<void> {
    wrapper = setup()
    await flushPromises()
    await flushPromises()
  }

  it('label[for] はすべて label できる入力要素を指す', async () => {
    await open()

    // 検査そのものが働いていること（label が 1 つも無いページで緑にならない）
    expect(document.querySelectorAll('label[for]').length).toBeGreaterThan(50)

    expect(labelForViolations()).toEqual([])
  })

  it('入力要素はすべて id か name を持つ', async () => {
    await open()

    expect(document.querySelectorAll('input, select, textarea').length).toBeGreaterThan(50)

    expect(fieldViolations()).toEqual([])
  })

  it('ラジオ／チェックボックスのかたまりは role と aria-labelledby で見出しと結び付く', async () => {
    await open()

    const groups = Array.from(document.querySelectorAll('.setting-radio-group, .setting-check-group'))
    expect(groups.length).toBeGreaterThan(0)
    for (const group of groups) {
      const labelId = group.getAttribute('aria-labelledby') ?? ''
      expect(labelId).not.toBe('')
      // 見出し（span.setting-label）が実在し、同じ「かたまり」の中にあること
      const caption = document.getElementById(labelId)
      expect(caption).not.toBeNull()
      expect(caption?.classList.contains('setting-label')).toBe(true)
      expect(caption?.closest('.setting-field')).toBe(group.closest('.setting-field'))
    }
  })

  it('グループの見出しは label ではなく span（for を持たない）', async () => {
    await open()

    // ラジオ／チェックボックスのかたまりを for で指す label が 1 つも無いこと
    const pointingAtGroup = Array.from(document.querySelectorAll('label[for]')).filter((label) => {
      const target = document.getElementById(label.getAttribute('for') ?? '')
      return target !== null && (target.classList.contains('setting-radio-group') || target.classList.contains('setting-check-group'))
    })
    expect(pointingAtGroup).toEqual([])
  })
})

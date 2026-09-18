import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import TestInfoView from '@/views/testinfo/TestInfoView.vue'
import DocumentListView from '@/views/document/DocumentListView.vue'

/**
 * 「一覧」見出しのアイコン（`#i-list`）。
 *
 * ユーザーの指定で、一覧を出す見出しはどの画面でも左手前に同じ一覧アイコンを置く
 * （基準は【図形一覧】）。ここでは画面ごとのテストを持たない 2 画面
 * （**テスト情報管理**・**資料管理**）を実際に描画して、見出しのアイコンを確認する。
 *
 * 残りの画面は、その画面のテスト（japanese-test / todo / net-control / batch-* /
 * ai-call-history / internet-usage / linkclip-view / home-action-timeline）と、
 * ソース側の番人 `tests/ui-action-buttons.spec.ts` が見る。
 */

function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

/** 画面は開いたときに一覧を読む。中身は空でも見出しは出る。 */
function setupViews() {
  window.sessionStorage.setItem('study21.auth.v2', JSON.stringify({ username: '検証 生徒', role: 'STUDENT' }))
  vi.stubGlobal('fetch', vi.fn(async () => ok({ rows: [], documents: [], folders: [] })))
  const pinia = createPinia()
  setActivePinia(pinia)
  return pinia
}

describe('一覧の見出しアイコン（一覧アイコンで統一）', () => {
  beforeEach(() => {
    vi.restoreAllMocks()
    window.sessionStorage.clear()
  })

  it('テスト情報管理の「テスト一覧」に一覧アイコンを付ける', async () => {
    const wrapper = mount(TestInfoView, { global: { plugins: [setupViews()] } })
    await flushPromises()

    const title = wrapper.get('.table-section__title')
    expect(title.text()).toBe('テスト一覧')
    expect(title.get('use').attributes('href')).toBe('#i-list')
  })

  it('資料管理の「資料一覧」に一覧アイコンを付ける', async () => {
    const wrapper = mount(DocumentListView, { global: { plugins: [setupViews()] } })
    await flushPromises()

    const title = wrapper.get('.table-section__title')
    expect(title.text()).toBe('資料一覧')
    expect(title.get('use').attributes('href')).toBe('#i-list')
  })
})

import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'

/**
 * リンククリップ画面の描画スモークテスト。
 * 実 API の応答形（2.0 から移行したデータに相当）をフィクスチャとして持ち、
 * 描画時に実行時エラーが出ないことを確認する。
 */
const rows = [
  {
    linkClipId: 1, folderCode: 'INBOX', sourceCode: 'WEB', clipType: 'LINK',
    siteName: 'platform.openai.com', pageTitle: 'OpenAI料金及びAPI key',
    url: 'https://platform.openai.com/home', normalizedUrl: 'https://platform.openai.com/home',
    summary: null, aiSummary: null, memo: null, publisherName: 'platform.openai.com',
    publishedAt: null, videoSeconds: null, thumbnailUrl: 'https://cdn.openai.com/x.png',
    favorite: false, read: true, archived: false, viewCount: 0,
    lastViewedAt: null, metaFetchedAt: '2026-06-13T17:59:13.040Z', aiSummarizedAt: null,
    version: 1, tags: ['OpenAI'], createdAt: '2026-06-13T08:59:13.043Z', updatedAt: '2026-06-13T08:59:13.043Z'
  },
  {
    linkClipId: 2, folderCode: 'INBOX', sourceCode: 'YOUTUBE', clipType: 'VIDEO',
    siteName: 'YouTube', pageTitle: 'Chrome OS',
    url: 'https://www.youtube.com/watch?v=5MOzGrsWJSk', normalizedUrl: 'https://www.youtube.com/watch?v=5MOzGrsWJSk',
    summary: 'chromeOS のインストール手順', aiSummary: null, memo: null, publisherName: 'YouTube',
    publishedAt: '2026-06-12T04:00:25.000Z', videoSeconds: 1056, thumbnailUrl: 'https://i.ytimg.com/vi/5MOzGrsWJSk/hqdefault.jpg',
    favorite: true, read: false, archived: false, viewCount: 3,
    lastViewedAt: null, metaFetchedAt: '2026-06-13T09:01:17.802Z', aiSummarizedAt: null,
    version: 1, tags: ['Chrome OS', 'e'], createdAt: '2026-06-13T09:01:17.803Z', updatedAt: '2026-06-13T09:01:17.803Z'
  },
  {
    linkClipId: 3, folderCode: 'REFERENCE', sourceCode: 'LOCAL_FILE', clipType: 'FILE',
    siteName: 'ローカルファイル', pageTitle: '初中数学公式大全',
    url: 'file:///D:/BaiduNetdiskDownload/math/formula.pdf', normalizedUrl: 'file:///D:/BaiduNetdiskDownload/math/formula.pdf',
    summary: 'PDF ファイル / D:/BaiduNetdiskDownload/math/formula.pdf', aiSummary: null, memo: null,
    publisherName: 'ローカルファイル', publishedAt: null, videoSeconds: null, thumbnailUrl: null,
    favorite: false, read: true, archived: false, viewCount: 18,
    lastViewedAt: '2026-06-13T12:46:53.529Z', metaFetchedAt: '2026-06-13T12:42:00.974Z', aiSummarizedAt: null,
    version: 1, tags: [], createdAt: '2026-06-13T12:42:00.975Z', updatedAt: '2026-06-13T12:46:53.530Z'
  },
  {
    linkClipId: 4, folderCode: 'LEARNING', sourceCode: 'YOUTUBE', clipType: 'VIDEO',
    siteName: 'YouTube', pageTitle: 'アーカイブ済みの動画',
    url: 'https://www.youtube.com/watch?v=IhrwjtGLv7Q', normalizedUrl: 'https://www.youtube.com/watch?v=IhrwjtGLv7Q',
    summary: null, aiSummary: 'AI要約のサンプル', memo: '自分メモ', publisherName: 'YouTube',
    publishedAt: '2026-06-12T02:03:21.000Z', videoSeconds: 632, thumbnailUrl: null,
    favorite: false, read: true, archived: true, viewCount: 1,
    lastViewedAt: '2026-06-12T11:15:25.868Z', metaFetchedAt: '2026-06-12T11:03:38.295Z', aiSummarizedAt: null,
    version: 1, tags: ['eee'], createdAt: '2026-06-12T11:03:38.296Z', updatedAt: '2026-06-12T11:15:43.572Z'
  }
]
const workspace = {
  success: true, code: 'OK', message: 'OK',
  data: {
    rows,
    tags: [{ name: 'OpenAI', count: 1 }, { name: 'Chrome OS', count: 1 }, { name: 'eee', count: 1 }],
    lanes: [{ folderCode: 'INBOX', count: 2 }, { folderCode: 'READ_LATER', count: 0 }, { folderCode: 'LEARNING', count: 1 }, { folderCode: 'REFERENCE', count: 1 }, { folderCode: 'DONE', count: 0 }]
  }
}

vi.mock('@/api/linkclip', () => ({
  getLinkClips: vi.fn(async () => workspace),
  previewLinkClip: vi.fn(async () => ({ success: true, data: null })),
  createLinkClip: vi.fn(),
  updateLinkClip: vi.fn(),
  deleteLinkClip: vi.fn(),
  updateLinkClipFlags: vi.fn(),
  recordLinkClipView: vi.fn(),
  checkLinkClipDuplicates: vi.fn(async () => ({ success: true, data: { duplicated: false, rows: [] } }))
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ role: 'GUARDIAN', accountId: 1 }) }))

import LinkClipView from '@/views/linkclip/LinkClipView.vue'

describe('LinkClipView', () => {
  it('一覧・タグ・レーンを描画できる（例外なし）', async () => {
    const wrapper = mount(LinkClipView, { global: { plugins: [createPinia()] } })
    await new Promise((resolve) => setTimeout(resolve, 30))
    await wrapper.vm.$nextTick()
    const text = wrapper.text()
    expect(wrapper.html().length).toBeGreaterThan(500)
    // カード・レーン説明・タグ候補
    expect(text).toContain('OpenAI料金及びAPI key')
    expect(text).toContain('まず投げ込む一時置き場')
    expect(text).toContain('保存リンク一覧')
    expect(text).toContain('OpenAI')
    // 動画の長さ（1056 秒 = 17:36）とローカルファイル表示
    expect(text).toContain('17:36')
    expect(text).toContain('ローカルファイル')
  })

  it('検索条件のアクションは「新規」が「検索」の左にある', async () => {
    const wrapper = mount(LinkClipView, { global: { plugins: [createPinia()] } })
    await new Promise((resolve) => setTimeout(resolve, 30))
    const labels = wrapper.findAll('.search-panel__actions button').map((button) => button.text())
    expect(labels[0]).toContain('新規')
    expect(labels[1]).toContain('検索')
    // 画面上部にインラインの入力エリアは無い（ダイアログで入力する）
    expect(wrapper.find('.lc-composer__head').exists()).toBe(false)
  })

  it('「新規」でダイアログが開く', async () => {
    const wrapper = mount(LinkClipView, { global: { plugins: [createPinia()] } })
    await new Promise((resolve) => setTimeout(resolve, 30))
    expect(wrapper.find('.lc-composer').exists()).toBe(false)

    await wrapper.findAll('.search-panel__actions button')[0].trigger('click')
    await wrapper.vm.$nextTick()

    const dialog = wrapper.find('.lc-composer')
    expect(dialog.exists()).toBe(true)
    expect(dialog.text()).toContain('リンクを保存')
    // フォーム（URL・保存先・タグ・メモ）がダイアログ内にある
    expect(dialog.find('input[type="url"]').exists()).toBe(true)
    expect(dialog.text()).toContain('URL')
  })

  it('リンクをクリックすると詳細が右のドロワーで開き、閉じられる', async () => {
    const wrapper = mount(LinkClipView, { global: { plugins: [createPinia()] } })
    await new Promise((resolve) => setTimeout(resolve, 30))
    // 初期状態では詳細ドロワーは無い（右ペインも無い）
    expect(wrapper.find('.lc-drawer-overlay').exists()).toBe(false)
    expect(wrapper.find('.lc-body .lc-detail').exists()).toBe(false)

    await wrapper.find('.lc-card').trigger('click')
    await wrapper.vm.$nextTick()
    const drawer = wrapper.find('.lc-drawer-overlay .lc-detail')
    expect(drawer.exists()).toBe(true)
    expect(drawer.text()).toContain('OpenAI料金及びAPI key')

    // 閉じるボタンで消える
    const closeButton = drawer.findAll('button').find((button) => button.attributes('title') === '閉じる')
    expect(closeButton).toBeTruthy()
    await closeButton!.trigger('click')
    await wrapper.vm.$nextTick()
    expect(wrapper.find('.lc-drawer-overlay').exists()).toBe(false)
  })

  it('カード右上の修正アイコンの右に赤い削除アイコンがあり、その場で削除できる', async () => {
    const { deleteLinkClip } = await import('@/api/linkclip')
    const wrapper = mount(LinkClipView, { global: { plugins: [createPinia()] } })
    await new Promise((resolve) => setTimeout(resolve, 30))

    const buttons = wrapper.find('.lc-card__actions').findAll('button')
    const labels = buttons.map((button) => button.attributes('title'))
    expect(labels[labels.length - 1]).toBe('削除')
    // 修正アイコンの右隣にあり、赤（is-danger）
    expect(labels[labels.length - 2]).toBe('修正')
    const removeButton = buttons[buttons.length - 1]
    expect(removeButton.classes()).toContain('is-danger')
    // 編集アイコンは青（app.css の .icon--edit）
    expect(wrapper.find('.lc-card__actions .icon--edit').exists()).toBe(true)

    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(true)
    await removeButton.trigger('click')
    await new Promise((resolve) => setTimeout(resolve, 20))
    confirmSpy.mockRestore()

    expect(vi.mocked(deleteLinkClip)).toHaveBeenCalledWith(1)
  })
})

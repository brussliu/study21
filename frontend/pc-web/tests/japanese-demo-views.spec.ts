import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import DemoWordListView from '@/views/japanese/demo/DemoWordListView.vue'
import DemoWordEditView from '@/views/japanese/demo/DemoWordEditView.vue'
import DemoWordStudyView from '@/views/japanese/demo/DemoWordStudyView.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import { DRAFT_KEY, readStoredDraft, writeStoredDraft } from '@/features/japanese-demo/store/draftStorage'

/**
 * 日本語勉強【単語情報管理】の画面。
 *
 * 確かめること:
 * ・本番 API（fetch）を一度も呼ばない
 * ・2.0 と同じ形（詳細編集と学習画面は**別ウィンドウ**、新規登録と削除確認は**ダイアログ**）
 * ・画面に「デモ」の表示を出さない（本物の画面と同じ見た目にする）
 * ・一覧の状態ごとに行の操作が変わる
 * ・新規登録の 3 ステップ、削除確認、編集の保存（失敗・衝突）、学習画面のタブと練習
 */

function makeRouter() {
  return createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/', name: 'student-japanese-demo', component: { template: '<div />' } },
      { path: '/edit', name: 'student-japanese-demo-edit', component: { template: '<div />' } },
      { path: '/study', name: 'student-japanese-demo-study', component: { template: '<div />' } }
    ]
  })
}

/** 別ウィンドウを開く呼び出しを捕まえる（テストでは本当のウィンドウは開けない）。 */
function stubWindowOpen(): { calls: { url: string; name: string; features: string }[]; restore: () => void } {
  const calls: { url: string; name: string; features: string }[] = []
  const original = window.open
  window.open = ((url?: string, name?: string, features?: string) => {
    calls.push({ url: String(url), name: String(name), features: String(features) })
    return { focus: () => undefined, closed: false } as unknown as Window
  }) as typeof window.open
  return {
    calls,
    restore: () => {
      window.open = original
    }
  }
}

async function mountList() {
  const pinia = createPinia()
  setActivePinia(pinia)
  const store = useJapaneseDemoStore()
  const router = makeRouter()
  await router.push('/')
  await router.isReady()
  const wrapper = mount(DemoWordListView, { global: { plugins: [pinia, router] } })
  await flushPromises()
  return { wrapper, store }
}

describe('単語情報管理：画面', () => {
  let fetchSpy: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchSpy = vi.fn(() => {
      throw new Error('画面が fetch を呼びました（本番 API につながってはいけません）')
    })
    vi.stubGlobal('fetch', fetchSpy)
    window.localStorage.clear()
  })

  it('一覧は本物の画面と同じ見た目で描画され、本番 API を呼ばない', async () => {
    const { wrapper } = await mountList()

    expect(wrapper.find('[data-demo-words]').exists()).toBe(true)
    expect(wrapper.findAll('[data-demo-word-row]').length).toBeGreaterThan(0)
    // 「デモ」の表示・注意書き・演示表示設定は画面に出さない
    expect(wrapper.find('[data-demo-badge]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-notice]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-status-panel]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('デモ')
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('検索条件は既定でたたまれ、キーワードで絞り込める', async () => {
    const { wrapper, store } = await mountList()

    expect(wrapper.find('[data-demo-advanced]').exists()).toBe(false)
    await wrapper.get('[data-demo-advanced-toggle]').trigger('click')
    expect(wrapper.find('[data-demo-advanced]').exists()).toBe(true)

    const before = wrapper.findAll('[data-demo-word-row]').length
    await wrapper.get('[data-demo-filter-keyword]').setValue('図書館')
    expect(store.filters.keyword).toBe('図書館')
    expect(wrapper.findAll('[data-demo-word-row]').length).toBeLessThan(before)
    expect(wrapper.find('[data-demo-word-row="w-toshokan"]').exists()).toBe(true)
  })

  it('「該当なし」の状態では案内を出す（URL のパラメータで状態を固定できる）', async () => {
    const { wrapper, store } = await mountList()
    // 動作確認用: 画面には出さず、URL で状態を固定する
    store.applyDisplayFromQuery('?list=NO_RESULT')
    await flushPromises()

    expect(wrapper.get('[data-demo-empty]').text()).toContain('条件に一致する単語がありません')
    expect(wrapper.find('[data-demo-words]').exists()).toBe(false)
  })

  it('未生成の語には生成の入口、生成失敗の語には理由と再試行を出す', async () => {
    const { wrapper, store } = await mountList()
    store.setPageSize(50)
    await flushPromises()

    const notGenerated = store.words.find((word) => word.detailStatus === 'NOT_GENERATED')!
    const failed = store.words.find((word) => word.detailStatus === 'FAILED')!

    const notGeneratedRow = wrapper.get(`[data-demo-word-row="${notGenerated.id}"]`)
    expect(notGeneratedRow.get('[data-demo-status="NOT_GENERATED"]').text()).toContain('未生成')
    expect(notGeneratedRow.find('[data-demo-generate]').exists()).toBe(true)
    expect(notGeneratedRow.find('[data-demo-retry]').exists()).toBe(false)

    const failedRow = wrapper.get(`[data-demo-word-row="${failed.id}"]`)
    expect(failedRow.get('[data-demo-status="FAILED"]').text()).toContain('生成失敗')
    expect(failedRow.get('[data-demo-failure]').text()).toContain(failed.failureReason ?? '')
    expect(failedRow.find('[data-demo-retry]').exists()).toBe(true)
  })

  it('「詳細編集」と「学習画面」は別ウィンドウで開く（2.0 と同じ形）', async () => {
    const { wrapper, store } = await mountList()

    const opener = stubWindowOpen()
    try {
      const row = wrapper.get('[data-demo-word-row="w-toshokan"]')

      await row.get('[data-demo-edit]').trigger('click')
      await flushPromises()
      expect(opener.calls.at(-1)?.url).toBe('/student/japanese-demo/edit?wordId=w-toshokan')
      expect(opener.calls.at(-1)?.features).toContain('popup=yes')
      // 下書きは別ウィンドウへ渡すために控えへ置く
      expect(store.draft?.id).toBe('w-toshokan')
      expect(readStoredDraft()?.id).toBe('w-toshokan')

      await row.get('[data-demo-study]').trigger('click')
      await flushPromises()
      expect(opener.calls.at(-1)?.url).toBe('/student/japanese-demo/study?wordId=w-toshokan')
      expect(opener.calls.at(-1)?.name).toBe('jpWordStudy_w-toshokan')

      // 一覧はそのまま残る（画面が切り替わらない）
      expect(wrapper.find('[data-demo-words]').exists()).toBe(true)
    } finally {
      opener.restore()
    }
  })

  it('新規登録は 1 ページのダイアログで、入力と書籍・Unit と確認が並ぶ', async () => {
    const { wrapper, store } = await mountList()

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-demo-new-dialog]').exists()).toBe(true)
    // 手順を分けず、1 ページにまとめて出す
    expect(wrapper.find('[data-demo-step="1"]').exists()).toBe(true)
    expect(wrapper.find('[data-demo-step="2"]').exists()).toBe(true)
    expect(wrapper.find('[data-demo-step="3"]').exists()).toBe(true)
    // 説明・入力例・シナリオ・操作のヒントは置かない
    expect(wrapper.text()).not.toContain('単語だけを入力します')
    expect(wrapper.text()).not.toContain('セルをクリックすると選ばれ')
    expect(wrapper.find('[data-demo-sample]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-scenario-new]').exists()).toBe(false)
    expect(wrapper.find('[data-demo-scenario-append]').exists()).toBe(false)
    // 開いた直後は空行 1 つ
    expect(store.registerHeadings).toEqual([''])
    expect(wrapper.findAll('[data-demo-word-row-index]').length).toBe(1)
    expect(wrapper.text()).not.toContain('デモ')

    // 表に入れると、確認と Unit の割り当てに出る
    store.setRegisterHeadings(['りんご', 'みかん', 'りんご'])
    await flushPromises()
    expect(wrapper.get('[data-demo-count-fresh]').text()).toBe('2')
    expect(wrapper.get('[data-demo-count-skipped]').text()).toBe('1')
    expect(wrapper.get('[data-demo-confirm-units]').text()).toContain('Unit')

    await wrapper.get('[data-demo-save]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => expect(wrapper.find('[data-demo-new-dialog]').exists()).toBe(false))
    expect(wrapper.get('[data-demo-notice-bar]').text()).toContain('登録しました')
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('Excel 風の表: クリックでは入力欄が出ず、ダブルクリック / F2 で入力する', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['りんご', 'みかん', 'ぶどう'])
    await flushPromises()

    // 値はテキストで出ていて、入力欄は出ていない（Excel と同じ）
    expect(wrapper.findAll('[data-demo-word-value]').length).toBeGreaterThan(0)
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(0)

    // セルをクリック＝選ぶだけ（入力欄は出ない・選択の印が付く）
    const firstCell = wrapper.get('[data-demo-word-cell="0"]')
    await firstCell.trigger('mousedown')
    expect(firstCell.classes()).toContain('is-selected')
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(0)

    // ダブルクリックで入力できる
    await firstCell.trigger('dblclick')
    await flushPromises()
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(1)
    const input = wrapper.get('[data-demo-word-input="0"]')
    expect((input.element as HTMLInputElement).value).toBe(store.registerHeadings[0])

    // 入力すると、その場で表と解釈の両方に反映される
    await input.setValue('書き換えた語')
    await flushPromises()
    expect(store.registerHeadings[0]).toBe('書き換えた語')
    expect(store.parsedRows[0]?.heading).toBe('書き換えた語')

    // Enter で確定すると、入力欄が閉じて次の行へ移る
    await input.trigger('keydown', { key: 'Enter' })
    await flushPromises()
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(0)
    expect(wrapper.get('[data-demo-word-cell="1"]').classes()).toContain('is-selected')
  })

  it('Excel 風の表: F2 で入力、Esc で取り消し、Delete で消せる', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['りんご', 'みかん'])
    await flushPromises()

    // F2 で入力モード（クリックだけでは入らない）
    await wrapper.get('[data-demo-word-cell="0"]').trigger('mousedown')
    await wrapper.get('[data-demo-word-grid]').trigger('keydown', { key: 'F2' })
    await flushPromises()
    expect(wrapper.findAll('[data-demo-word-input]').length).toBe(1)

    // 書き換えてから Esc で取り消すと、元の値に戻る
    const input = wrapper.get('[data-demo-word-input="0"]')
    await input.setValue('りんご（書きかけ）')
    await input.trigger('keydown', { key: 'Escape' })
    await flushPromises()
    expect(store.registerHeadings[0]).toBe('りんご')

    // セルを選んで Delete で消せる
    await wrapper.get('[data-demo-word-cell="1"]').trigger('mousedown')
    await wrapper.get('[data-demo-word-grid]').trigger('keydown', { key: 'Delete' })
    await flushPromises()
    expect(store.registerHeadings[1]).toBe('')
    expect(store.registerWords.map((word) => word.heading)).toEqual(['りんご'])
  })

  it('Excel 風の表: 選んだセルを起点に貼り付け、足りない行は増える', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['既存の語'])
    await flushPromises()

    // 1 行目を選んでから、Excel のコピー（タブ区切り）を貼る
    await wrapper.get('[data-demo-word-cell="0"]').trigger('mousedown')
    const paste = (text: string) => wrapper.get('[data-demo-word-grid]').trigger('paste', {
      clipboardData: { getData: () => text }
    })
    await paste('りんご\tapple\tリンゴ\nみかん\tmandarin\tミカン')
    await flushPromises()

    // 1 列目だけを使い、選んだセルから下へ流し込む（余計な列は無視する）
    expect(store.registerHeadings).toEqual(['りんご', 'みかん'])
    expect(wrapper.find('[data-demo-paste-notice]').exists()).toBe(true)

    // 行が足りなければ自動で増える
    await wrapper.get('[data-demo-word-cell="1"]').trigger('mousedown')
    await paste('ぶどう\nもも\nなし')
    await flushPromises()
    expect(store.registerHeadings).toEqual(['りんご', 'ぶどう', 'もも', 'なし'])
  })

  it('Excel 風の表: 行の追加と削除', async () => {
    const { wrapper, store } = await mountList()
    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()

    // 開いた直後は空行 1 つ。行追加で 2 行になる
    expect(store.registerHeadings).toEqual([''])
    await wrapper.get('[data-demo-add-row]').trigger('click')
    await flushPromises()
    expect(store.registerHeadings.length).toBe(2)

    store.setRegisterHeadings(['あ', 'い', 'う'])
    await flushPromises()
    expect(wrapper.findAll('[data-demo-word-row-index]').length).toBe(3)

    await wrapper.get('[data-demo-remove-row="1"]').trigger('click')
    await flushPromises()
    expect(store.registerHeadings).toEqual(['あ', 'う'])
  })

  it('書籍はプルダウンで選び、最後の「新しい書籍を追加…」で入力欄が出る', async () => {
    const { wrapper, store } = await mountList()

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['りんご', 'みかん'])
    await flushPromises()

    // 既存の書籍: 1 Unit の語数は本から自動で計算される（入力欄は出ない）
    const options = wrapper.get('[data-demo-book-select]').findAll('option').map((option) => option.text())
    expect(options.at(-1)).toBe('新しい書籍を追加…')
    expect(options.length).toBe(store.bookNameOptions.length + 1)
    expect(wrapper.find('[data-demo-unit-size]').exists()).toBe(false)
    expect(wrapper.get('[data-demo-unit-size-auto]').text()).toContain('自動')

    // 「新しい書籍を追加…」を選ぶと、名前と語数の入力欄が出る
    await wrapper.get('[data-demo-book-select]').setValue('__NEW_BOOK__')
    await flushPromises()
    expect(wrapper.find('[data-demo-book-name]').exists()).toBe(true)
    expect(wrapper.find('[data-demo-unit-size]').exists()).toBe(true)
    expect(store.registerBookMode).toBe('NEW')
  })

  it('重複した単語の扱いをラジオで選べる（既定はこの書籍の中）', async () => {
    const { wrapper, store } = await mountList()

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    // 既定は「この書籍の中で重複した単語を飛ばす」
    expect(store.registerDuplicateMode).toBe('BOOK')
    expect((wrapper.get('[data-demo-duplicate-book]').element as HTMLInputElement).checked).toBe(true)
    expect((wrapper.get('[data-demo-duplicate-all]').element as HTMLInputElement).checked).toBe(false)

    // 切り替えると、説明も「すべての書籍」に変わる
    await wrapper.get('[data-demo-duplicate-all]').setValue(true)
    await flushPromises()
    expect(store.registerDuplicateMode).toBe('ALL')
    expect(wrapper.get('[data-demo-duplicate-note]').text()).toContain('ほかの書籍')
  })

  it('登録に失敗したらダイアログを閉じず、理由を出して入力を残す', async () => {
    vi.useFakeTimers()
    const { wrapper, store } = await mountList()
    store.setDisplay({ saveMode: 'SAVE_FAILED' })

    await wrapper.get('[data-demo-new]').trigger('click')
    await flushPromises()
    store.setRegisterHeadings(['失敗確認用の語'])
    await flushPromises()
    const before = store.words.length

    await wrapper.get('[data-demo-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(store.words.length).toBe(before)
    expect(wrapper.find('[data-demo-new-dialog]').exists(), '失敗したら閉じない').toBe(true)
    expect(wrapper.get('[data-demo-register-error]').text()).toContain('登録できませんでした')
    // 入力（表の中身）は残す（やり直せる）
    expect(store.registerHeadings.length).toBeGreaterThan(0)
    vi.useRealTimers()
  })

  it('削除確認はダイアログで出し、失敗したら閉じずに理由を出す', async () => {
    vi.useFakeTimers()
    const { wrapper, store } = await mountList()

    const count = store.words.length
    store.setDisplay({ saveMode: 'SAVE_FAILED' })
    await wrapper.get('[data-demo-delete]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-demo-delete-dialog]').exists()).toBe(true)

    await wrapper.get('[data-demo-delete-confirm]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-delete-dialog]').exists(), '失敗したら閉じない').toBe(true)
    expect(wrapper.get('[data-demo-delete-error]').text()).toContain('削除できませんでした')
    expect(store.words.length).toBe(count)

    store.setDisplay({ saveMode: 'NORMAL' })
    await wrapper.get('[data-demo-delete-confirm]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-delete-dialog]').exists()).toBe(false)
    expect(store.words.length).toBe(count - 1)
    vi.useRealTimers()
  })

  it('詳細編集は保存の失敗・衝突を再現し、入力を残す', async () => {
    vi.useFakeTimers()
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const target = store.words.find((word) => word.detailStatus === 'GENERATED')!
    store.openEditor(target.id)
    const router = makeRouter()
    await router.push('/edit')
    await router.isReady()

    const wrapper = mount(DemoWordEditView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    expect(wrapper.text()).not.toContain('デモ')
    expect(wrapper.findAll('[data-demo-editor-nav-item]').length).toBe(11)

    store.setDisplay({ saveMode: 'SAVE_FAILED' })
    await wrapper.get('[data-demo-edit-chinese]').setValue('変更した意味')
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.get('[data-demo-save-failed]').text()).toContain('保存できませんでした')
    expect(store.draft?.chineseMeaning).toBe('変更した意味')

    store.setDisplay({ saveMode: 'CONFLICT' })
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-save-conflict]').exists()).toBe(true)
    expect(store.draft?.chineseMeaning).toBe('変更した意味')

    store.setDisplay({ saveMode: 'NORMAL' })
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-save-saved]').exists()).toBe(true)
    vi.useRealTimers()
  })

  it('未保存のまま閉じようとすると確認が出る', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const target = store.words.find((word) => word.detailStatus === 'GENERATED')!
    store.openEditor(target.id)
    const router = makeRouter()
    await router.push('/edit')
    await router.isReady()

    const wrapper = mount(DemoWordEditView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    await wrapper.get('[data-demo-edit-heading]').setValue('変更した見出し')
    await wrapper.get('[data-demo-editor-cancel]').trigger('click')
    expect(wrapper.find('[data-demo-leave-prompt]').exists()).toBe(true)
    expect(store.draft?.heading).toBe('変更した見出し')
  })

  it('学習画面は既定で要点だけを見せ、残りはタブに分ける', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const word = store.findWord('w-toshokan')!

    const wrapper = mount(DemoWordStudyView, { props: { word }, global: { plugins: [pinia] } })
    await flushPromises()

    expect(wrapper.get('[data-demo-study-word]').text()).toBe('図書館')
    expect(wrapper.get('[data-demo-study-core]').text()).not.toBe('')
    expect(wrapper.findAll('[data-demo-study-example]').length).toBeGreaterThanOrEqual(2)
    expect(wrapper.find('[data-demo-study-caution]').exists()).toBe(true)
    expect(wrapper.text()).not.toContain('デモ')

    const tabs = wrapper.findAll('[data-demo-study-tab]').map((tab) => tab.attributes('data-demo-study-tab'))
    expect(tabs).toEqual(['meaning', 'examples', 'compare', 'form', 'practice'])

    // ミニ練習はこの画面の中で完結する
    await wrapper.get('[data-demo-study-tab="practice"]').trigger('click')
    await flushPromises()
    expect(wrapper.findAll('[data-demo-quiz-choice]').length).toBeGreaterThan(0)
    await wrapper.get('[data-demo-quiz-choice]').trigger('click')
    expect(wrapper.find('[data-demo-quiz-result]').exists()).toBe(true)
  })

  it('学習画面は中国語訳と読みを切り替えられる', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const word = store.findWord('w-toshokan')!

    const wrapper = mount(DemoWordStudyView, { props: { word }, global: { plugins: [pinia] } })
    await flushPromises()

    expect(wrapper.find('[data-demo-study-reading]').exists()).toBe(true)
    await wrapper.get('[data-demo-toggle-reading]').setValue(false)
    expect(wrapper.find('[data-demo-study-reading]').exists()).toBe(false)

    await wrapper.get('[data-demo-toggle-chinese]').setValue(false)
    expect(wrapper.find('[data-demo-study-core]').exists()).toBe(false)
    // 日本語は残る
    expect(wrapper.get('[data-demo-study-word]').text()).toBe('図書館')
  })

  it('下書きを表示するときは、未保存であることを示す', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const target = store.words.find((word) => word.detailStatus === 'GENERATED')!
    store.openEditor(target.id)

    const wrapper = mount(DemoWordStudyView, {
      props: { word: store.draft!, draft: true },
      global: { plugins: [pinia] }
    })
    await flushPromises()

    expect(wrapper.find('[data-demo-draft-band]').exists()).toBe(true)
    expect(wrapper.get('[data-demo-draft-band]').text()).toContain('未保存')
  })
})

/** 下書きの控え（別ウィンドウへ渡す仕組み）。 */
describe('下書きの控え', () => {
  it('書いて読み直せる（別ウィンドウはこれで下書きを受け取る）', () => {
    window.localStorage.clear()
    const word = { id: 'w-x', heading: '試験' } as never
    writeStoredDraft(word)
    expect(JSON.parse(window.localStorage.getItem(DRAFT_KEY) ?? 'null')).toMatchObject({ id: 'w-x' })
    expect(readStoredDraft()?.id).toBe('w-x')
    writeStoredDraft(null)
    expect(window.localStorage.getItem(DRAFT_KEY)).toBeNull()
  })
})

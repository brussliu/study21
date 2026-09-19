import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import DemoWordListView from '@/views/japanese/demo/DemoWordListView.vue'
import DemoWordNewView from '@/views/japanese/demo/DemoWordNewView.vue'
import DemoWordEditView from '@/views/japanese/demo/DemoWordEditView.vue'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import { DEMO_PASTE_SAMPLE } from '@/features/japanese-demo/mock/demoWords'

/**
 * 日本語勉強【単語情報管理】デモの画面。
 *
 * デモの約束を画面の側から確かめる:
 * ・本番 API（fetch）を一度も呼ばない
 * ・デモであることの表示が出る
 * ・一覧の状態（未生成・生成失敗）ごとに行の操作が変わる
 * ・新規登録の 3 ステップが動き、貼り付けの解釈が画面に出る
 */

function makeRouter() {
  return createRouter({
    history: createWebHashHistory(),
    routes: [
      { path: '/', name: 'student-japanese-demo', component: { template: '<div />' } },
      { path: '/new', name: 'student-japanese-demo-new', component: { template: '<div />' } },
      { path: '/edit/:wordId', name: 'student-japanese-demo-edit', component: { template: '<div />' } },
      { path: '/study/:wordId', name: 'student-japanese-demo-study', component: { template: '<div />' } }
    ]
  })
}

describe('単語情報管理デモ：画面', () => {
  let fetchSpy: ReturnType<typeof vi.fn>

  beforeEach(() => {
    fetchSpy = vi.fn(() => {
      throw new Error('デモ画面が fetch を呼びました（本番 API につながってはいけません）')
    })
    vi.stubGlobal('fetch', fetchSpy)
  })

  it('一覧はデモ表示つきで描画され、本番 API を呼ばない', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const router = makeRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(DemoWordListView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    expect(wrapper.find('[data-demo-badge]').text()).toContain('デモ')
    expect(wrapper.get('[data-demo-notice]').text()).toContain('実際のシステムには保存されません')
    expect(wrapper.find('[data-demo-words]').exists()).toBe(true)
    expect(wrapper.findAll('[data-demo-word-row]').length).toBeGreaterThan(0)
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('検索条件は既定でたたまれ、キーワードで絞り込める', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const router = makeRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(DemoWordListView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    // 二次条件はたたんである（首屏を混み合わせない）
    expect(wrapper.find('[data-demo-advanced]').exists()).toBe(false)
    await wrapper.get('[data-demo-advanced-toggle]').trigger('click')
    expect(wrapper.find('[data-demo-advanced]').exists()).toBe(true)

    // キーワードで絞ると件数が変わる
    const before = wrapper.findAll('[data-demo-word-row]').length
    await wrapper.get('[data-demo-filter-keyword]').setValue('図書館')
    expect(store.filters.keyword).toBe('図書館')
    expect(wrapper.findAll('[data-demo-word-row]').length).toBeLessThan(before)
    expect(wrapper.find('[data-demo-word-row="w-toshokan"]').exists()).toBe(true)
  })

  it('「該当なし」の状態では案内を出す', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    store.setDisplay({ listMode: 'NO_RESULT' })
    const router = makeRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(DemoWordListView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    expect(wrapper.get('[data-demo-empty]').text()).toContain('条件に一致する単語がありません')
    expect(wrapper.find('[data-demo-words]').exists()).toBe(false)
  })

  it('未生成の語には生成の入口、生成失敗の語には理由と再試行を出す', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    // 1 ページに全語を出して、状態ごとの行を確かめる
    store.setPageSize(50)
    const router = makeRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(DemoWordListView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    const notGenerated = store.words.find((word) => word.detailStatus === 'NOT_GENERATED')!
    const failed = store.words.find((word) => word.detailStatus === 'FAILED')!

    // 未生成と生成失敗は別の状態として見せる（同じ扱いにしない）
    const notGeneratedRow = wrapper.get(`[data-demo-word-row="${notGenerated.id}"]`)
    expect(notGeneratedRow.get('[data-demo-status="NOT_GENERATED"]').text()).toContain('未生成')
    expect(notGeneratedRow.find('[data-demo-generate]').exists()).toBe(true)
    expect(notGeneratedRow.find('[data-demo-retry]').exists()).toBe(false)

    const failedRow = wrapper.get(`[data-demo-word-row="${failed.id}"]`)
    expect(failedRow.get('[data-demo-status="FAILED"]').text()).toContain('生成失敗')
    expect(failedRow.get('[data-demo-failure]').text()).toContain(failed.failureReason ?? '')
    expect(failedRow.find('[data-demo-retry]').exists()).toBe(true)
    expect(failedRow.find('[data-demo-generate]').exists()).toBe(false)
  })

  it('削除に失敗したらダイアログを閉じず、理由を見せてやり直せる', async () => {
    vi.useFakeTimers()
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const router = makeRouter()
    await router.push('/')
    await router.isReady()

    const wrapper = mount(DemoWordListView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    const count = store.words.length
    store.setDisplay({ saveMode: 'SAVE_FAILED' })
    await wrapper.get('[data-demo-delete]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-demo-delete-confirm]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    // 閉じずに理由を出す（「消えたように見える」状態にしない）
    expect(wrapper.find('[data-demo-delete-dialog]').exists(), '失敗したら閉じない').toBe(true)
    expect(wrapper.get('[data-demo-delete-error]').text()).toContain('削除できませんでした')
    expect(store.words.length).toBe(count)

    // 設定を戻せば、そのまま削除できる（やり直せる）
    store.setDisplay({ saveMode: 'NORMAL' })
    await wrapper.get('[data-demo-delete-confirm]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-delete-dialog]').exists()).toBe(false)
    expect(store.words.length).toBe(count - 1)
    vi.useRealTimers()
  })

  it('新規登録は 3 ステップで進み、入力例の解釈結果を画面に出す', async () => {
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const router = makeRouter()
    await router.push('/new')
    await router.isReady()

    const wrapper = mount(DemoWordNewView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    expect(wrapper.find('[data-demo-step="1"]').exists()).toBe(true)
    // 対応する区切り方を最初に書いてある（当てずっぽうで入力させない）
    expect(wrapper.text()).toContain('対応している貼り付けの形式')

    // 入力例を入れると解析され、要確認の行が状態つきで並ぶ
    await wrapper.get('[data-demo-sample]').trigger('click')
    await flushPromises()
    expect(store.pasteText).toBe(DEMO_PASTE_SAMPLE)
    expect(wrapper.find('[data-demo-parsed]').exists()).toBe(true)
    expect(wrapper.findAll('[data-demo-parsed-row]').length).toBeGreaterThan(5)
    // 読みが複数の行は、選ばせる（入力例には「あく・ひらく」の行が入っている）
    expect(store.parsedRows.some((row) => row.state === 'MULTI_READING')).toBe(true)
    expect(wrapper.find('[data-demo-multi-reading]').exists()).toBe(true)

    // ステップ 2 へ
    expect(wrapper.get('[data-demo-next-1]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-demo-next-1]').trigger('click')
    expect(wrapper.find('[data-demo-step="2"]').exists()).toBe(true)
    expect(wrapper.find('[data-demo-start-unit]').exists()).toBe(true)
    expect(store.registerBookName).not.toBe('')

    // ステップ 3 で確認（Unit ごとのまとめが出る）
    await wrapper.get('[data-demo-next-2]').trigger('click')
    expect(wrapper.find('[data-demo-step="3"]').exists()).toBe(true)
    expect(wrapper.get('[data-demo-confirm-units]').text()).toContain('Unit')
    expect(fetchSpy).not.toHaveBeenCalled()
  })

  it('詳細編集にもデモ表示設定があり、保存の失敗・衝突を切り替えられる', async () => {
    vi.useFakeTimers()
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    const target = store.words.find((word) => word.detailStatus === 'GENERATED')!
    store.openEditor(target.id)
    const router = makeRouter()
    await router.push(`/edit/${target.id}`)
    await router.isReady()

    const wrapper = mount(DemoWordEditView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    // 編集画面からも状態を切り替えられる（保存の失敗・衝突を再現するため）
    expect(wrapper.find('[data-demo-status-panel]').exists()).toBe(true)
    await wrapper.get('[data-demo-status-toggle]').trigger('click')
    await wrapper.get('[data-demo-display-save]').setValue('SAVE_FAILED')
    expect(store.display.saveMode).toBe('SAVE_FAILED')

    // 保存 → 失敗の知らせが出て、入力は残る
    await wrapper.get('[data-demo-edit-chinese]').setValue('変更した意味')
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.get('[data-demo-save-failed]').text()).toContain('保存できませんでした')
    expect(store.draft?.chineseMeaning).toBe('変更した意味')

    // 衝突も再現できる（入力は残したまま確認を促す）
    await wrapper.get('[data-demo-display-save]').setValue('CONFLICT')
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()
    expect(wrapper.find('[data-demo-save-conflict]').exists()).toBe(true)
    expect(store.draft?.chineseMeaning).toBe('変更した意味')
    vi.useRealTimers()
  })

  it('デモ表示設定で保存失敗にすると、登録は失敗し入力が残る', async () => {
    vi.useFakeTimers()
    const pinia = createPinia()
    setActivePinia(pinia)
    const store = useJapaneseDemoStore()
    store.setDisplay({ saveMode: 'SAVE_FAILED' })
    const router = makeRouter()
    await router.push('/new')
    await router.isReady()

    const wrapper = mount(DemoWordNewView, { global: { plugins: [pinia, router] } })
    await flushPromises()

    const wordsBefore = store.words.length
    await wrapper.get('[data-demo-scenario-append]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-demo-save]').trigger('click')
    await vi.advanceTimersByTimeAsync(1000)
    await flushPromises()

    expect(store.words.length).toBe(wordsBefore)
    expect(wrapper.get('[data-demo-register-error]').text()).toContain('登録できませんでした')
    // 入力は残す（やり直せる）
    expect(store.pasteText).not.toBe('')
    expect(fetchSpy).not.toHaveBeenCalled()
    vi.useRealTimers()
  })
})

import { afterEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import { practiceDemoRoutes } from '@/features/english-practice-demo/routes'
import { usePracticeDemoStore } from '@/features/english-practice-demo/store'
import { useEnglishStudyStore } from '@/features/english-study/store'
import AppSidebar from '@/components/layout/AppSidebar.vue'

let wrapper: VueWrapper | undefined
afterEach(() => { wrapper?.unmount(); document.body.innerHTML = ''; vi.restoreAllMocks() })
async function open(path: string) {
  const pinia = createPinia()
  const router = createRouter({ history: createMemoryHistory(), routes: [...practiceDemoRoutes('student').map(r => ({ ...r, path: `/student/${r.path}` })), { path: '/:pathMatch(.*)*', component: { template: '<div />' } }] })
  await router.push(`/student/${path}`); await router.isReady()
  wrapper = mount(RouterView, { attachTo: document.body, global: { plugins: [pinia, router], stubs: { Teleport: true } } })
  await flushPromises()
  return { router, store: usePracticeDemoStore(pinia), words: useEnglishStudyStore(pinia), pinia }
}
async function click(text: string) {
  const button = wrapper!.findAll('button').find(b => b.text() === text)
  expect(button, text).toBeDefined(); await button!.trigger('click'); await flushPromises()
}
describe('穴埋め・精読の画面フロー', () => {
  it('穴埋め一覧で学習をダイアログ表示し、行と検索結果からAI解説を生成する', async () => {
    const { router, store } = await open('english-cloze')
    expect(wrapper!.find('th').text()).toBe('操作')
    expect(wrapper!.findAll('input[type="checkbox"]')).toHaveLength(0)
    const row = wrapper!.findAll('tbody tr').find(r => r.text().includes('文法トレーニング'))!
    const target = store.clozeSets[1].questions[0]
    expect(wrapper!.findAll('th').map(th => th.text())).toContain('再挑戦')
    expect(row.find('[aria-label="編集"]').exists()).toBe(false)
    expect(row.get('[aria-label="AI解説生成"] use').attributes('href')).toBe('#i-robot')
    expect(row.get('.ep-cloze-actions').classes()).toContain('ep-cloze-actions')
    store.recordAttempt('cloze', store.clozeSets[1].id, [target], { [target.id]: target.correct! })
    store.recordAttempt('cloze', store.clozeSets[1].id, [target], { [target.id]: target.correct === 0 ? 1 : 0 })
    await flushPromises()
    expect(row.text()).toContain('正解 1')
    expect(row.text()).toContain('不正解 1')
    expect(store.clozeSets[1].explanationState).toBe('WAITING')
    await row.get('button[aria-label="AI解説生成"]').trigger('click')
    expect(target.explanationState).toBe('COMPLETED')
    expect(store.clozeSets[1].questions[1].explanationState).toBeUndefined()
    await row.get('button[aria-label="学習を開く"]').trigger('click')
    expect(router.currentRoute.value.path).toBe('/student/english-cloze')
    expect(wrapper!.get('[role="dialog"]').text()).toContain(target.text)
    await wrapper!.get('[role="dialog"] button[aria-label="閉じる"]').trigger('click')
    await wrapper!.get('input[placeholder="問題文・知識点・出典・メモ"]').setValue('文法トレーニング')
    await wrapper!.get('form').trigger('submit')
    await click('AI解説生成')
    expect(store.clozeSets[1].questions.every(q => q.explanationState === 'COMPLETED')).toBe(true)
    expect(store.clozeSets[0].questions[0].explanationState).toBeUndefined()
  })
  it('一覧の検索・リセットと詳細経由の再挑戦を連動する', async () => {
    const { router, store } = await open('english-cloze')
    const count = wrapper!.findAll('tbody tr').length
    await wrapper!.get('input[placeholder="問題文・知識点・出典・メモ"]').setValue('見つからないキーワード')
    await wrapper!.get('form').trigger('submit')
    expect(wrapper!.text()).toContain('該当するデータがありません')
    await click('リセット')
    expect(wrapper!.findAll('tbody tr')).toHaveLength(count)
    const set = store.clozeSets[0], q = set.questions[0], original = q.student
    await router.push(`/student/english-cloze/${set.id}/${q.id}`); await flushPromises()
    await click('再挑戦'); await click('採点して記録')
    expect(wrapper!.get('[role="dialog"]').text()).toContain('未回答')
    await wrapper!.findAll('[role="dialog"] input[type="radio"]')[q.correct!].setValue(true)
    await click('採点して記録')
    expect(wrapper!.get('[role="dialog"]').text()).toContain('1 / 1 正解')
    await click('結果を閉じる')
    expect(q.student).toBe(original)
    expect(wrapper!.text()).toContain('再挑戦履歴（1）')
    await click('ロック解除・答案修正')
    await wrapper!.findAll('select')[1].setValue(String(q.correct))
    await click('答案を保存')
    expect(q.student).toBe(q.correct)
    expect(store.attempts[0].answers[0].isCorrect).toBe(true)
  })
  it('編集の破棄では教材を変えず、保存後は一覧へ反映する', async () => {
    const { router, store } = await open('english-reading-intensive/reading-1/edit')
    const original = store.readings[0].title
    await wrapper!.get('input[maxlength="200"]').setValue('変更したタイトル')
    const navigation = router.push('/student/english-reading-intensive')
    await flushPromises()
    expect(wrapper!.text()).toContain('未保存の変更')
    await click('編集を続ける'); await navigation
    expect(store.readings[0].title).toBe(original)
    await click('保存して一覧へ')
    expect(router.currentRoute.value.path).toBe('/student/english-reading-intensive')
    expect(wrapper!.text()).toContain('変更したタイトル')
  })
  it('精読で言語・根拠を切り替え、語彙を管理画面のデータへ登録する', async () => {
    Object.defineProperty(HTMLElement.prototype, 'scrollIntoView', { configurable: true, value: vi.fn() })
    const { words } = await open('english-reading-intensive/reading-1')
    await wrapper!.get('select').setValue('zh')
    expect(wrapper!.text()).toContain('城市树木')
    await click('設問解析')
    const evidence = wrapper!.findAll('button').find(b => b.text().includes('本文で確認'))!
    await evidence.trigger('click'); await flushPromises()
    expect(wrapper!.get('[role="tab"][aria-selected="true"]').text()).toBe('原文精読')
    expect(wrapper!.get('#sentence-s2').classes()).toContain('is-active')
    await click('重点語彙')
    await wrapper!.findAll('button').find(b => b.text() === '単語帳に追加')!.trigger('click')
    const dialog = wrapper!.get('[role="dialog"]')
    await dialog.get('input[list="vocabulary-books"]').setValue('精読テスト書籍')
    await dialog.findAll('button').find(b => b.text() === '単語帳に追加')!.trigger('click')
    expect(words.words.some(w => w.english === 'pollutant' && w.book === '精読テスト書籍')).toBe(true)
    expect(wrapper!.text()).toContain('精読テスト書籍')
  })
  it('教材の深いURLでもメニューの選択状態が保たれる', async () => {
    const { router, pinia } = await open('english-reading-intensive/reading-1/edit')
    const sidebar = mount(AppSidebar, { props: { area: 'student' }, global: { plugins: [pinia, router] } })
    expect(sidebar.get('[data-group="english"] > button').attributes('aria-expanded')).toBe('true')
    expect(sidebar.get('a[href="/student/english-reading-intensive"]').classes()).toContain('is-active')
    sidebar.unmount()
  })
})

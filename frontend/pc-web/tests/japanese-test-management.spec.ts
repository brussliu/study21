import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import JapaneseTestView from '@/views/japanese/JapaneseTestView.vue'

const api = vi.hoisted(() => ({
  fetchJpnTestConditions: vi.fn(), searchJpnTestRange: vi.fn(), createJpnTests: vi.fn(), deleteJpnTest: vi.fn()
}))
vi.mock('@/api/japanese', async original => ({ ...await original<object>(), ...api }))
vi.mock('vue-router', async original => ({ ...await original<object>(), useRoute: () => ({ path: '/student/japanese-test' }) }))
beforeEach(() => {
  api.fetchJpnTestConditions.mockResolvedValue({ data: { books: ['教材A'], categories: ['01', '02'] } })
  api.searchJpnTestRange.mockResolvedValue({ data: { items: [], totalElements: 0, totalPages: 0 } })
  api.createJpnTests.mockResolvedValue({ data: { createdCount: 1, skippedCount: 1, results: [
    { rowNo: 1, testId: 12, skipped: false, message: '作成しました。' },
    { rowNo: 2, testId: null, skipped: true, message: '条件に合う問題がありません。' }
  ] } })
})
afterEach(() => vi.clearAllMocks())
it('書籍・分類を検索へ渡し、数量全部で複数の種別を作成して結果を表示する', async () => {
  const wrapper = mount(JapaneseTestView, { global: { plugins: [createPinia()] } })
  await flushPromises()
  await wrapper.get('[data-test-filter="book"]').setValue('教材A')
  await flushPromises()
  await wrapper.get('[data-test-filter="from"]').setValue('01')
  await wrapper.get('[data-test-search]').trigger('submit')
  await flushPromises()
  expect(api.searchJpnTestRange).toHaveBeenLastCalledWith(expect.objectContaining({ book: '教材A', categoryFrom: '01', page: 1, size: 15 }))
  await wrapper.get('[data-test-multiple]').trigger('click')
  await flushPromises()
  const headers = wrapper.findAll('[data-test-create-form] th').map(header => header.text())
  expect(headers).toEqual(['操作', '書籍', '分類 From', '分類 To', '数量', 'テスト種別'])
  expect(wrapper.get('[data-test-row-count]').findAll('option').map(option => option.text())).toEqual(['全部', '10', '20', '30', '50', '100'])
  expect(wrapper.get('[data-test-row-delete]').find('svg').exists()).toBe(true)
  await wrapper.get('[data-test-add-row]').trigger('click')
  await flushPromises()
  await wrapper.findAll('[data-test-row-type]')[1]!.setValue('B')
  await wrapper.get('[data-test-create-form]').trigger('submit')
  await flushPromises()
  expect(api.createJpnTests).toHaveBeenCalledWith([
    expect.objectContaining({ testType: 'A', questionCount: 0, book: '教材A' }),
    expect.objectContaining({ testType: 'B', questionCount: 0, book: '教材A' })
  ])
  expect(wrapper.text()).toContain('2行目：条件に合う問題がありません。')
  wrapper.unmount()
})

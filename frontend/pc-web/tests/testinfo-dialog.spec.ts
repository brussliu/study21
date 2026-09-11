import { describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createPinia } from 'pinia'

/**
 * 得点・満点は <input type="number"> の v-model が数値を入れる。
 * 以前は数値に対して .trim() を呼んでおり、ダイアログで点数を入力すると
 * 「t.trim is not a function」で画面が落ちていた（回帰防止）。
 */
vi.mock('@/api/testinfo', () => ({
  createTestInfo: vi.fn(),
  updateTestInfo: vi.fn(),
  uploadTestFiles: vi.fn(),
  addTestFileFromTempFile: vi.fn(),
  addTestFileFromTestFile: vi.fn(),
  deleteTestFile: vi.fn(),
  saveTestFileImage: vi.fn(),
  getTestInfos: vi.fn(async () => ({ success: true, code: 'OK', message: 'OK', data: { rows: [] } }))
}))
vi.mock('@/api/tempfiles', () => ({
  getTempFiles: vi.fn(async () => ({ success: true, code: 'OK', message: 'OK', data: [] }))
}))
vi.mock('@/stores/auth', () => ({ useAuthStore: () => ({ role: 'GUARDIAN', accountId: 1 }) }))

import TestInfoFormDialog from '@/views/testinfo/TestInfoFormDialog.vue'

const row = {
  testId: 1,
  testNo: 'TST-20260806-195038',
  testName: '大学共通テスト',
  subject: '英語',
  kind: '模擬',
  examDate: '2026-08-06',
  score: 80,
  fullScore: 100,
  accuracyText: '80.0%',
  memo: null,
  version: 1,
  photoCount: 0,
  files: [],
  createdAt: null,
  updatedAt: null
}

function mountDialog() {
  return mount(TestInfoFormDialog, {
    props: { open: true, row: row as never },
    attachTo: document.body,
    global: { plugins: [createPinia()] }
  })
}

/** Teleport 先（body）から数値入力と表示テキストを取る。 */
function numberInputs(): HTMLInputElement[] {
  return Array.from(document.querySelectorAll<HTMLInputElement>('input[type="number"]'))
}
/** 正答率は readonly の input なので value を見る。 */
function accuracyValue(): string {
  return document.querySelector<HTMLInputElement>('input[readonly]')?.value ?? ''
}

describe('TestInfoFormDialog の得点入力', () => {
  it('数値の得点・満点でも正答率が計算できる（例外にならない）', async () => {
    const wrapper = mountDialog()
    await wrapper.vm.$nextTick()
    expect(accuracyValue()).toBe('80.0%')

    const inputs = numberInputs()
    expect(inputs.length).toBeGreaterThanOrEqual(2)

    // v-model は input type=number の値を数値として入れる（文字列にはならない）
    inputs[0].value = '50'
    inputs[0].dispatchEvent(new Event('input'))
    inputs[1].value = '200'
    inputs[1].dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()

    expect(accuracyValue()).toBe('25.0%')
  })

  it('未入力（空）でも落ちない', async () => {
    const wrapper = mountDialog()
    await wrapper.vm.$nextTick()
    const inputs = numberInputs()
    expect(inputs.length).toBeGreaterThanOrEqual(2)
    inputs[0].value = ''
    inputs[0].dispatchEvent(new Event('input'))
    inputs[1].value = ''
    inputs[1].dispatchEvent(new Event('input'))
    await wrapper.vm.$nextTick()
    expect(accuracyValue()).toBe('--')
  })
})

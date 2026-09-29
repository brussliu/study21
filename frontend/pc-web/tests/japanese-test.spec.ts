import { mount, flushPromises } from '@vue/test-utils'
import { beforeEach, afterEach, expect, it, vi } from 'vitest'
import { createPinia } from 'pinia'
import JapaneseTestRunner from '@/views/japanese/JapaneseTestRunner.vue'
import type { JpnTestDetail, TestType } from '@/api/japanese'

const api = vi.hoisted(() => ({ startJpnTest: vi.fn(), fetchJpnTest: vi.fn(), answerJpnTest: vi.fn(), fetchJpnWord: vi.fn() }))
vi.mock('@/api/japanese', async original => ({ ...await original<object>(), ...api }))
vi.mock('vue-router', async original => ({ ...await original<object>(), useRoute: () => ({ query: { testId: '77' } }) }))
vi.mock('@/views/japanese/demo/DemoWordStudyView.vue', () => ({ default: { props: ['word'], template: '<div data-study-content>学習の詳細</div>' } }))
let data: JpnTestDetail
function session(type: TestType = 'C'): JpnTestDetail {
  return {
    test: { testId: 77, testNo: 'JT-77', testType: type, level: null, book: '教材A', categoryFrom: '01', categoryTo: '02', difficulty: 'NORMAL', mode: 'ALL', questionCount: 1, doneCount: 0, correctCount: 0, wrongCount: 0, state: 'RUNNING', startedAt: null, finishedAt: null, lastStudiedAt: null, activeMs: 0, scorePercent: 0, version: 1 },
    questions: [{ question: { entryId: 1, orderNo: 1, state: 'PENDING', judgment: null, answerCount: 0, wrongCount: 0, activeMs: 0, answeredAt: null, questionId: 5, wordId: 12, word: '経験', reading: 'けいけん', questionType: 'C1_READING', questionText: '読みを選んでください。', correctValue: 'けいけん', explanation: '説明', book: '教材A', category: '01' }, choices: [
      { choiceId: 10, choiceKey: 10, orderNo: 1, value: 'けいけん', reading: null, correct: true, description: null },
      { choiceId: 11, choiceKey: 11, orderNo: 2, value: 'けいげん', reading: null, correct: false, description: null }
    ], snapshot: { word: '経験', reading: 'けいけん', meaning: '经验' }, history: [] }]
  }
}
beforeEach(() => {
  data = session()
  api.startJpnTest.mockImplementation(async () => ({ data: structuredClone(data) }))
  api.fetchJpnTest.mockImplementation(async () => ({ data: structuredClone(data) }))
  api.answerJpnTest.mockImplementation(async (_id, body) => {
    const q = data.questions[0]!
    const correct = body.choiceId === 10 || body.answerText === '学習完了'
    q.question.state = 'ANSWERED'; q.question.judgment = correct ? 'CORRECT' : 'INCORRECT'; q.question.answerCount++
    q.history = [{ answer: body.answerText, reading: body.readingText, choiceId: body.choiceId, correct, at: '' }]
    data.test.state = 'COMPLETED'; data.test.doneCount = 1
    return { data: { correct, judgment: q.question.judgment, correctValue: 'けいけん', explanation: '説明', test: data.test } }
  })
})
afterEach(() => { vi.clearAllMocks(); vi.restoreAllMocks() })
async function screen() { const w = mount(JapaneseTestRunner, { global: { plugins: [createPinia()] } }); await flushPromises(); return w }

it('選んだ固定選択肢のキーを送信し、最終問題でも判定を確認してから結果へ進む', async () => {
  const w = await screen()
  expect(api.startJpnTest).toHaveBeenCalledWith(77)
  expect(w.text()).not.toContain('けいけん）')
  await w.get('.jpt-choice').trigger('click'); await flushPromises()
  expect(api.answerJpnTest).toHaveBeenCalledWith(77, expect.objectContaining({ choiceId: 10, orderNo: 1 }))
  expect(w.get('[role="status"]').text()).toContain('正解です')
  expect(w.find('[data-test-result]').exists()).toBe(false)
  await w.get('[data-test-next]').trigger('click')
  expect(w.get('[data-test-result]').text()).toContain('けいけん')
  w.unmount()
})
it('誤答は選んだ選択肢と正解を区別し、二重送信しない', async () => {
  const w = await screen(); await w.findAll('.jpt-choice')[1]!.trigger('click'); await flushPromises()
  expect(w.findAll('.is-wrong')).toHaveLength(1); expect(w.findAll('.is-correct')).toHaveLength(1)
  await w.get('.jpt-choice').trigger('click'); expect(api.answerJpnTest).toHaveBeenCalledTimes(1); w.unmount()
})
it('B は表記と読みを送り、不正解が未完了なら修正できる', async () => {
  data = session('B')
  api.answerJpnTest.mockImplementationOnce(async () => { data.questions[0]!.question.answerCount = 1; return { data: { correct: false, judgment: 'MIXED', test: data.test } } })
  const w = await screen(); expect(w.findAll('.jpt-choice')).toHaveLength(0)
  await w.get('[data-test-heading]').setValue('経験'); await w.get('[data-test-reading]').setValue('けいげん')
  await w.get('[data-test-submit]').trigger('click'); await flushPromises()
  expect(api.answerJpnTest).toHaveBeenCalledWith(77, expect.objectContaining({ answerText: '経験', readingText: 'けいげん' }))
  expect(w.text()).toContain('残り2回'); expect(w.get('[data-test-reading]').attributes('disabled')).toBeUndefined(); w.unmount()
})
it('通信エラー時は入力を残して再送できる', async () => {
  api.answerJpnTest.mockRejectedValueOnce(new Error('保存に失敗しました。'))
  const w = await screen(); await w.get('.jpt-choice').trigger('click'); await flushPromises()
  expect(w.get('[role="alert"]').text()).toContain('保存に失敗')
  expect(w.get('.jpt-choice').attributes('disabled')).toBeUndefined(); w.unmount()
})
it('再開は最初の未回答から始まり、回答済みを戻って確認できる', async () => {
  const first = structuredClone(data.questions[0]!)
  first.question.state = 'ANSWERED'; first.question.judgment = 'CORRECT'; first.history = [{ choiceId: 10, correct: true, at: '' }]
  data.questions[0]!.question.orderNo = 2; data.questions[0]!.question.entryId = 2; data.questions.unshift(first); data.test.questionCount = 2; data.test.doneCount = 1
  const w = await screen(); expect(w.text()).toContain('2 / 2 語')
  await w.findAll('button').find(b => b.text() === '前の語')!.trigger('click'); await flushPromises()
  expect(w.get('.is-selected').text()).toContain('けいけん'); expect(w.get('.jpt-choice').attributes('disabled')).toBeDefined(); w.unmount()
})
it('完了済みは回答履歴から開き、保存された入力を表示する', async () => {
  data = session('B'); data.test.state = 'COMPLETED'; data.test.doneCount = 1
  data.questions[0]!.question.state = 'ANSWERED'; data.questions[0]!.question.judgment = 'MIXED'; data.questions[0]!.history = [{ answer: '経験', reading: 'けいげん', correct: false, at: '' }]
  const w = await screen(); expect(w.get('[data-test-result]').text()).toContain('けいげん'); expect(w.text()).toContain('一部正解'); w.unmount()
})
it('C2 は表記と読みを明かさず音声から選ばせる', async () => {
  data.questions[0]!.question.questionType = 'C2_KANJI'
  const w = await screen(); expect(w.get('.jpt-prompt').text()).toContain('音声を聞いて'); expect(w.get('.jpt-prompt').text()).not.toContain('経験'); expect(w.get('.jpt-prompt').text()).not.toContain('けいけん'); w.unmount()
})
it('非アクティブの時間を学習時間へ含めない', async () => {
  const now = vi.spyOn(Date, 'now'); now.mockReturnValue(1000)
  const w = await screen(); now.mockReturnValue(3000); window.dispatchEvent(new Event('blur')); now.mockReturnValue(63000); window.dispatchEvent(new Event('focus')); now.mockReturnValue(64000)
  await w.get('.jpt-choice').trigger('click'); await flushPromises()
  expect(api.answerJpnTest).toHaveBeenCalledWith(77, expect.objectContaining({ elapsedMs: 3000 })); w.unmount()
})

it('C1 の未回答では Space キーでも正解の読みを再生しない', async () => {
  const speak = vi.fn()
  vi.stubGlobal('speechSynthesis', { speak, cancel: vi.fn() })
  const w = await screen()
  window.dispatchEvent(new KeyboardEvent('keydown', { key: ' ' }))
  expect(speak).not.toHaveBeenCalled()
  w.unmount()
  vi.unstubAllGlobals()
})

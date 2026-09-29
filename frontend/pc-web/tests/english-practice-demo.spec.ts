import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { usePracticeDemoStore } from '@/features/english-practice-demo/store'
import { practiceDemoRoutes } from '@/features/english-practice-demo/routes'

beforeEach(() => { setActivePinia(createPinia()); vi.useRealTimers() })
describe('穴埋め・精読デモの公開操作', () => {
  it('各エリアに一覧・作成・編集・学習の入口を提供する', () => {
    for (const area of ['admin', 'student', 'parent'] as const) {
      const routes = practiceDemoRoutes(area)
      expect(routes).toHaveLength(8)
      expect(new Set(routes.map(r => r.name)).size).toBe(8)
      expect(routes.map(r => r.path)).toContain('english-cloze/:recordId/:questionId')
      expect(routes.map(r => r.path)).toContain('english-reading-intensive/:recordId')
    }
  })
  it('精読の工程を順に進め、OCR再実行で下流を未完了に戻す', async () => {
    vi.useFakeTimers()
    const store = usePracticeDemoStore()
    const reading = store.readings[1]
    expect(() => store.runStage(reading, 2)).toThrow('前の工程')
    expect(() => store.runStage(reading, -1)).toThrow('工程')
    for (let stage = 0; stage < 4; stage++) {
      const task = store.runStage(reading, stage)
      expect(reading.stages[stage]).toBe('RUNNING')
      expect(() => store.runStage(reading, stage)).toThrow('完了を')
      await vi.runAllTimersAsync(); await task
      expect(reading.stages[stage]).toBe('COMPLETED')
    }
    const rerun = store.runStage(reading, 0)
    expect(reading.stages.slice(1)).toEqual(['WAITING', 'WAITING', 'WAITING'])
    await vi.runAllTimersAsync(); await rerun
    expect(reading.paragraphs).not.toHaveLength(0)
    expect(reading.vocabulary).toHaveLength(0)
  })
  it('再挑戦は元の学生答案を保持し、履歴を別に集計する', () => {
    const store = usePracticeDemoStore()
    const set = store.clozeSets[0]
    const question = set.questions[0]
    const original = question.student
    const attempt = store.recordAttempt('cloze', set.id, [question], { [question.id]: question.correct! })
    expect(attempt.correctCount).toBe(1)
    expect(question.student).toBe(original)
    expect(store.attemptsFor('cloze', set.id, question.id)).toHaveLength(1)
    expect(() => store.recordAttempt('cloze', set.id, [question], {})).toThrow('未回答')
  })
  it('ページ単位のOCRで画像と設問を結びつけ、解説の確認まで進める', () => {
    const store = usePracticeDemoStore()
    const set = store.clozeSets[0]
    set.images.push({ ...set.images[0], id: 'second-page' })
    set.parallel = true
    store.scanCloze(set)
    expect(new Set(set.questions.map(q => q.imageId)).size).toBe(2)
    expect(store.scans[0].count).toBe(set.questions.length)
    store.generateCloze(set)
    expect(set.explanationState).toBe('COMPLETED')
  })
})

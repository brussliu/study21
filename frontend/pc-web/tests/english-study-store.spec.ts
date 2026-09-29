import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { expectedAnswer, useEnglishStudyStore, type TestType } from '@/features/english-study/store'

beforeEach(() => setActivePinia(createPinia()))
describe('英語デモの学習連携', () => {
  it('区分を分離し、誤答の再テストと書籍集計に回答を反映する', () => {
    const store = useEnglishStudyStore()
    const words = store.select('word', 'beginner')
    expect(words.length).toBeGreaterThan(1)
    expect(store.select('phrase', 'intermediate').every(w => w.kind === 'phrase' && w.level === 'intermediate')).toBe(true)
    const test = store.createTest({ kind: 'word', level: 'beginner', type: 'B', wordIds: words.slice(0, 2).map(w => w.id) })
    store.answer(test.id, 'wrong')
    store.answer(test.id, words[1].english.toUpperCase())
    expect(test.state).toBe('COMPLETED')
    expect(test.answers.map(a => a.correct)).toEqual([false, true])
    const retry = store.retry(test.id)
    expect(retry.wordIds).toEqual([words[0].id])
    expect(store.select('word', 'beginner')[0].skills.B.attempts).toBeGreaterThan(0)
    expect(store.select('phrase', 'beginner').some(w => w.id === words[0].id)).toBe(false)
  })
  it.each(['A', 'B', 'C', 'D', 'E', 'F'] as TestType[])('%s の回答を集計し、作成時の問題を編集から守る', type => {
    const store = useEnglishStudyStore()
    const word = store.select('phrase', 'intermediate')[0]
    const test = store.createTest({ kind: 'phrase', level: 'intermediate', type, wordIds: [word.id] })
    const expected = expectedAnswer(test.questions[0], type)
    const attempts = word.skills[type].attempts
    store.start(test.id); store.tick(test.id)
    word.english = 'changed'
    store.answer(test.id, expected)
    store.answer(test.id, 'duplicate')
    store.tick(test.id)
    expect(test.answers).toHaveLength(1)
    expect(test.answers[0].correct).toBe(true)
    expect(test.activeSeconds).toBe(1)
    expect(word.skills[type].attempts).toBe(attempts + 1)
    expect(test.questions[0].english).toBe('take advantage of')
  })
  it('空のテストと別区分の語を拒否する', () => {
    const store = useEnglishStudyStore()
    expect(() => store.createTest({ kind: 'word', level: 'beginner', type: 'B', wordIds: [store.select('phrase', 'beginner')[0].id] })).toThrow('出題できる語')
  })
})

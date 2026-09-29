import { defineStore } from 'pinia'
import { ref } from 'vue'
import { blankCloze, clozeQuestions, clozeSeeds, demoId, intensiveSeeds, readingSample } from './mock'
import { answerLabel, copy, type ClozeSet, type IntensiveRecord, type PracticeAttempt, type Question, type ScanRecord } from './types'

export function validateQuestions(questions: Question[]) {
  if (!questions.length) throw new Error('設問を1件以上登録してください。')
  questions.forEach((q, i) => {
    if (!q.text.trim() || q.options.length < 2 || q.options.some(o => !o.trim())) throw new Error(`${i + 1}問目の問題文・選択肢を入力してください。`)
    if (new Set(q.options.map(o => o.trim().toLowerCase())).size !== q.options.length) throw new Error(`${i + 1}問目の選択肢が重複しています。`)
    if (q.correct !== null && !q.options[q.correct] || q.student !== null && !q.options[q.student]) throw new Error(`${i + 1}問目の答案を確認してください。`)
  })
}
/** 画面設計用。外部API・DB・ブラウザ永続ストレージを使用しない。 */
export const usePracticeDemoStore = defineStore('english-practice-demo', () => {
  const clozeSets = ref<ClozeSet[]>(clozeSeeds())
  const readings = ref<IntensiveRecord[]>(intensiveSeeds())
  const attempts = ref<PracticeAttempt[]>([])
  const scans = ref<ScanRecord[]>(clozeSets.value.map(set => ({ id: demoId('scan'), setId: set.id, source: set.sourceName, date: set.createdAt, images: copy(set.images), count: set.questions.length, state: 'COMPLETED' })))
  function saveCloze(record: ClozeSet) {
    if (!record.sourceName.trim()) throw new Error('試験・練習名を入力してください。')
    validateQuestions(record.questions)
    const index = clozeSets.value.findIndex(s => s.id === record.id)
    if (index < 0) clozeSets.value.unshift(copy(record)); else clozeSets.value[index] = copy(record)
  }
  function saveReading(record: IntensiveRecord) {
    if (!record.title.trim()) throw new Error('タイトルを入力してください。')
    if (record.questions.length) validateQuestions(record.questions)
    const index = readings.value.findIndex(r => r.id === record.id)
    if (index < 0) readings.value.unshift(copy(record)); else readings.value[index] = copy(record)
  }
  function recordAttempt(kind: PracticeAttempt['kind'], parentId: string, questions: Question[], values: Record<string, number>) {
    if (!questions.length || questions.some(q => values[q.id] === undefined)) throw new Error('未回答の設問があります。')
    if (questions.some(q => q.correct === null || !q.options[q.correct] || !q.options[values[q.id]])) throw new Error('正解・選択肢を確認してください。')
    const answers = questions.map(q => ({ questionId: q.id, text: q.text, answer: values[q.id], correct: q.correct!, options: [...q.options], isCorrect: q.correct === values[q.id] }))
    const attempt: PracticeAttempt = { id: demoId('attempt'), kind, parentId, date: new Date().toLocaleString('ja-JP'), answers, correctCount: answers.filter(a => a.isCorrect).length }
    attempts.value.unshift(attempt)
    return attempt
  }
  function attemptsFor(kind: PracticeAttempt['kind'], parentId: string, questionId?: string) { return attempts.value.filter(a => a.kind === kind && a.parentId === parentId && (!questionId || a.answers.some(q => q.questionId === questionId))) }
  function scanCloze(record: ClozeSet) {
    if (!record.images.length) throw new Error('画像を追加してください。')
    record.questions = (record.parallel ? record.images : record.images.slice(0, 1)).flatMap(img => clozeQuestions().map(q => ({ ...q, imageId: img.id })))
    record.ocrState = 'COMPLETED'; record.explanationState = 'WAITING'
    scans.value.unshift({ id: demoId('scan'), setId: record.id, source: record.sourceName || '出典未入力', date: new Date().toLocaleString('ja-JP'), images: copy(record.images), count: record.questions.length, state: 'COMPLETED' })
  }
  function generateCloze(record: ClozeSet, questionIds?: string[]) {
    const questions = questionIds ? record.questions.filter(q => questionIds.includes(q.id)) : record.questions
    validateQuestions(questions)
    for (const q of questions) {
      q.explanation.ja ||= q.correct === null ? '正解が未確定です。教材を編集して正解を設定してください。' : `登録された正解は ${answerLabel(q.correct)}（${q.options[q.correct]}）です。前後の文脈と各選択肢を比較しましょう。これは解説欄の表示例です。`
      q.explanation.zh ||= q.correct === null ? '正确答案尚未确定，请先编辑题目。' : `已登记的正确答案为 ${answerLabel(q.correct)}（${q.options[q.correct]}）。请对照上下文比较各选项。这是解说栏的展示示例。`
      q.explanationState = 'COMPLETED'
    }
    if (record.questions.every(q => q.explanationState === 'COMPLETED')) record.explanationState = 'COMPLETED'
  }
  function runStage(record: IntensiveRecord, index: number) {
    if (!Number.isInteger(index) || index < 0 || index > 3) throw new Error('有効な工程を選択してください。')
    if (record.stages.includes('RUNNING')) throw new Error('処理の完了をお待ちください。')
    if (index > 0 && record.stages.slice(0, index).some(s => s !== 'COMPLETED')) throw new Error('前の工程を完了してください。')
    if (index === 0 && !record.images.some(i => i.category === 'PASSAGE')) throw new Error('文章に分類した画像を1枚以上追加してください。')
    record.stages[index] = 'RUNNING'; record.stageError = ''
    record.stages = record.stages.map((s, i) => i > index ? 'WAITING' : s)
    return new Promise<void>(resolve => setTimeout(() => {
      const sample = readingSample()
      if (index === 0) { record.paragraphs = sample.paragraphs; record.questions = sample.questions; record.guide = { ja: '', zh: '' }; record.points = { ja: '', zh: '' }; record.vocabulary = []; record.title ||= sample.title }
      if (index === 1) { record.guide = sample.guide; record.points = sample.points; record.theme ||= sample.theme }
      if (index === 2) { record.vocabulary = sample.vocabulary }
      record.stages[index] = 'COMPLETED'; resolve()
    }, 220))
  }
  function remove(kind: PracticeAttempt['kind'], id: string) {
    if (kind === 'cloze') clozeSets.value = clozeSets.value.filter(s => s.id !== id)
    else readings.value = readings.value.filter(r => r.id !== id)
    attempts.value = attempts.value.filter(a => !(a.kind === kind && a.parentId === id))
  }
  return { clozeSets, readings, scans, attempts, saveCloze, saveReading, recordAttempt, attemptsFor, scanCloze, generateCloze, runStage, remove, blankCloze }
})

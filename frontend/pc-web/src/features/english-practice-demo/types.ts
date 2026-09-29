export type Language = 'ja' | 'zh'
export type Bilingual = Record<Language, string>
export type SourceType = 'EXAM' | 'PRACTICE' | 'OTHER'
export const sourceLabels: Record<SourceType, string> = { EXAM: '試験過去問', PRACTICE: '練習問題', OTHER: 'その他' }
export type StageState = 'WAITING' | 'RUNNING' | 'COMPLETED' | 'ERROR'
export const stageLabels = ['OCR実行', '基本情報・導読', '解説・重点語彙', '設問解析']
export interface LessonImage { id: string; name: string; src: string; category: 'PASSAGE' | 'QUESTION' | 'OTHER' }
export interface Question {
  id: string; text: string; translation: Bilingual; options: string[]; correct: number | null; student: number | null
  knowledge: string; explanation: Bilingual; optionNotes: Bilingual[]; evidence: string[]; imageId: string; explanationState?: StageState
}
export interface SourceInfo { sourceType: SourceType; sourceName: string; year: string; chapter: string; note: string }
export interface ClozeSet extends SourceInfo {
  id: string; createdAt: string; images: LessonImage[]; questions: Question[]; parallel: boolean
  ocrState: StageState; explanationState: StageState
}
export interface Sentence { id: string; english: string; translation: Bilingual; grammar: Bilingual }
export interface Paragraph { id: string; role: Bilingual; sentences: Sentence[] }
export interface Vocabulary { id: string; term: string; pronunciation: string; meaning: Bilingual; context: string; usage: Bilingual; book: string }
export interface IntensiveRecord extends SourceInfo {
  id: string; createdAt: string; title: string; grade: string; difficulty: string; theme: string
  images: LessonImage[]; imageMode: 'TEXT' | 'TEXT_FIGURE'; stages: StageState[]; stageError: string
  paragraphs: Paragraph[]; questions: Question[]; vocabulary: Vocabulary[]; guide: Bilingual; points: Bilingual; memo: string
}
export interface PracticeAttempt {
  id: string; kind: 'cloze' | 'intensive'; parentId: string; date: string; correctCount: number
  answers: { questionId: string; text: string; answer: number; correct: number; options: string[]; isCorrect: boolean }[]
}
export interface ScanRecord { id: string; setId: string; source: string; date: string; images: LessonImage[]; count: number; state: StageState }
export function copy<T>(value: T): T { return JSON.parse(JSON.stringify(value)) as T }
export function answerLabel(index: number | null) { return index === null ? '未回答' : String.fromCharCode(65 + index) }
export function judged(question: Question) { return question.correct === null || question.student === null ? '未判定' : question.correct === question.student ? '正解' : '不正解' }
export function clozeExplanationReady(set: ClozeSet, question: Question) { return (question.explanationState ?? set.explanationState) === 'COMPLETED' }
export function wordCount(record: IntensiveRecord) { return record.paragraphs.flatMap(p => p.sentences).map(s => s.english).join(' ').trim().split(/\s+/).filter(Boolean).length }
export function recordStatus(record: IntensiveRecord) {
  if (record.stages.includes('ERROR')) return 'エラー'
  if (record.stages[0] === 'RUNNING') return 'OCR処理中'
  if (record.stages.includes('RUNNING')) return 'AI生成中'
  return record.stages.every(s => s === 'COMPLETED') ? '生成済み' : '未完了'
}

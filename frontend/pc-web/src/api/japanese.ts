import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * 日本語勉強 API（/api/user/japanese）。
 * user-api の JapaneseController と対応する。
 *
 * 3 つの画面（単語情報管理・単語テスト・単語勉強状況）が使う。
 * データは `JPN_*` テーブル（2.0 の日本語機能＝study3 DB から移行済み）。
 * 学習状況（習得度・お気に入り・復習日）は**アカウントごと**に持つ。
 */
export type LearnState = 'NOT_STARTED' | 'LEARNING' | 'REVIEW' | 'MASTERED'
export type TestType = 'A' | 'B' | 'C' | 'D' | 'E'
export type TestState = 'CREATED' | 'RUNNING' | 'COMPLETED'

export const TEST_TYPE_OPTIONS: TestType[] = ['A', 'B', 'C', 'D', 'E']
export const LEARN_STATE_OPTIONS: LearnState[] = ['NOT_STARTED', 'LEARNING', 'REVIEW', 'MASTERED']

/** テスト種別の説明（2.0 の A〜E）。 */
export const TEST_TYPE_LABELS: Record<TestType, string> = {
  A: 'A：読み確認',
  B: 'B：表記・読み',
  C: 'C：漢字と読み',
  D: 'D：文脈の意味',
  E: 'E：漢字の使い方'
}

/**
 * AI が作った問題の種別（`JPN_単語問題情報.問題種別`）の日本語名。
 *
 * 4 種別は 2.0 の画面とテストの A〜E に対応する（C は読みと漢字の 2 種類ある）。
 * `問題種別` に CHECK が無く種別が増えうるので、未知のコードはコードのまま出す。
 */
export const QUESTION_TYPE_LABELS: Record<string, string> = {
  A_STUDY: 'A：読み確認',
  B1_WRITING: 'B：表記',
  B2_READING: 'B：読み',
  C1_READING: 'C：漢字と読み',
  C2_KANJI: 'C：漢字の選択',
  D_CONTEXT_MEANING: 'D：文脈の意味',
  E_KANJI_USAGE: 'E：漢字の使い方'
}

export const LEARN_STATE_LABELS: Record<LearnState, string> = {
  NOT_STARTED: '未学習',
  LEARNING: '学習中',
  REVIEW: '復習',
  MASTERED: '習得済'
}

export const TEST_STATE_LABELS: Record<TestState, string> = {
  CREATED: '未開始',
  RUNNING: '学習中',
  COMPLETED: '完了'
}

export const LEARN_STATE_BADGES: Record<LearnState, string> = {
  NOT_STARTED: 'badge--neutral',
  LEARNING: 'badge--info',
  REVIEW: 'badge--warning',
  MASTERED: 'badge--success'
}

export const TEST_STATE_BADGES: Record<TestState, string> = {
  CREATED: 'badge--neutral',
  RUNNING: 'badge--info',
  COMPLETED: 'badge--success'
}

export interface JpnWord {
  wordId: number
  word: string
  reading: string
  jlptLevel: string | null
  partOfSpeech: string | null
  stateCode: string
  note: string | null
  version: number
  /** 収録（教材のどこに載っているか） */
  book: string | null
  category: string | null
  level: string | null
  wordSeq: number | null
  collectionCount: number
  /** 学習状況 */
  learnState: LearnState
  mastery: number
  answeredCount: number
  correctCount: number
  favorite: boolean
  learned: boolean
  lastStudiedAt: string | null
  nextReviewAt: string | null
}

export interface JpnWordTotals {
  wordCount: number
  learnedCount: number
  favoriteCount: number
  averageMastery: number
  answeredCount: number
}

export interface JpnWordPage {
  items: JpnWord[]
  totalElements: number
  page: number
  size: number
  totalPages: number
  totals: JpnWordTotals
}

export interface JpnCollection {
  collectionId: number
  level: string
  book: string
  category: string
  wordSeq: number
  listedWord: string | null
  listedReading: string | null
  listedPartOfSpeech: string | null
  chineseMeaning: string | null
}

export interface JpnWordQuestion {
  questionId: number
  questionType: string
  questionNo: number
  questionText: string | null
  correctValue: string
  choiceCount: number
}

/**
 * 2.0 の 6 つの子テーブル（語義・例文・発音・コロケーション・関連語・使用注意）と
 * 詳細本体の列を 1 つにまとめたもの。キーは 2.0 の列名を camelCase にしたもの。
 */
export interface JpnWordDetail {
  detailId: number
  contentVersion: number
  aiProvider: string | null
  aiModel: string | null
  fetchedAt: string | null
  /**
   * 6 つの配列の件数。
   * 一覧 API は取得状態（A・B／C〜E）の判定に使うため、配列を持たずに数だけを返す。
   * 詳細 API では配列の長さと同じ値が返るので、詳細ダイアログはこちらを使わなくてよい。
   */
  senseCount?: number
  exampleCount?: number
  pronunciationCount?: number
  collocationCount?: number
  relatedWordCount?: number
  cautionCount?: number
  detail: {
    /* 詳細本体（2.0 の STY_日本語単語詳細情報 の列） */
    jlptLevel?: string | null
    partOfSpeech?: string | null
    conjugation?: string | null
    transitivity?: string | null
    importance?: number | null
    chineseMeaning?: string | null
    descriptionJa?: string | null
    descriptionZh?: string | null
    structuredSchemaVersion?: string | null
    manuallyCorrected?: boolean | null
    structured?: Record<string, unknown> | null
    /* 子テーブル */
    senses?: JpnSense[]
    examples?: JpnExample[]
    pronunciations?: JpnPronunciation[]
    collocations?: JpnCollocation[]
    relatedWords?: JpnRelatedWord[]
    cautions?: JpnCaution[]
  }
}

/** 語義。 */
export interface JpnSense {
  number: number
  japanese: string | null
  chinese: string | null
  context: string | null
  style: string | null
  noteJapanese: string | null
  noteChinese: string | null
}

/** 例文。 */
export interface JpnExample {
  japanese: string | null
  reading: string | null
  chinese: string | null
  contextJapanese: string | null
  contextChinese: string | null
  source: string | null
  senseNumber: number | null
}

/** 発音。 */
export interface JpnPronunciation {
  reading: string | null
  accentNotation: string | null
  accentType: number | null
  moraCount: number | null
  audioUrl: string | null
  audioProvider: string | null
}

/** コロケーション（よく使う言い回し）。 */
export interface JpnCollocation {
  expression: string | null
  reading: string | null
  chinese: string | null
  exampleJapanese: string | null
  exampleChinese: string | null
}

/** 関連語（類義語・対義語・間違えやすい語）。 */
export interface JpnRelatedWord {
  relatedWordId: number | null
  relationType: string | null
  heading: string | null
  reading: string | null
  chinese: string | null
  differenceJapanese: string | null
  differenceChinese: string | null
  eCandidate: boolean | null
}

/** 使用注意。 */
export interface JpnCaution {
  noteType: string | null
  japanese: string | null
  chinese: string | null
  wrongExample: string | null
  correctExample: string | null
}

/** 関連語の関係（2.0 の 関係種別）。 */
export const RELATED_TYPE_LABELS: Record<string, string> = {
  SYNONYM: '類義語',
  ANTONYM: '対義語',
  CONFUSABLE: '間違えやすい',
  SAME_READING: '同じ読み',
  RELATED: '関連語'
}

export interface JpnWordDetailResult {
  word: JpnWord
  collections: JpnCollection[]
  questions: JpnWordQuestion[]
  detail: JpnWordDetail | null
}

export interface JpnTest {
  testId: number
  testNo: string
  testType: TestType
  level: string | null
  book: string | null
  categoryFrom: string | null
  categoryTo: string | null
  difficulty: string
  mode: string
  questionCount: number
  doneCount: number
  correctCount: number
  wrongCount: number
  state: TestState
  startedAt: string | null
  finishedAt: string | null
  lastStudiedAt: string | null
  activeMs: number
  scorePercent: number
  version: number
}

export interface JpnTestTotals {
  testCount: number
  completedCount: number
  runningCount: number
  averageScore: number
  totalActiveMs: number
}

export interface JpnTestPage {
  items: JpnTest[]
  totalElements: number
  page: number
  size: number
  totalPages: number
  totals: JpnTestTotals
}

export interface JpnTestQuestion {
  entryId: number
  orderNo: number
  state: 'PENDING' | 'ANSWERED' | 'SKIPPED'
  judgment: string | null
  answerCount: number
  wrongCount: number
  activeMs: number
  answeredAt: string | null
  questionId: number | null
  wordId: number
  word: string
  reading: string | null
  questionType: string | null
  questionText: string | null
  correctValue: string | null
  explanation: string | null
  book: string | null
  category: string | null
}

export interface JpnChoice {
  choiceId: number
  orderNo: number
  value: string
  reading: string | null
  correct: boolean
  description: string | null
}

export interface JpnTestQuestionView {
  question: JpnTestQuestion
  choices: JpnChoice[]
}

export interface JpnTestDetail {
  test: JpnTest
  questions: JpnTestQuestionView[]
}

export interface JpnAnswerResult {
  correct: boolean
  judgment: string
  correctValue: string | null
  explanation: string | null
  test: JpnTest
  message: string
}

export interface JpnStatusSummary {
  studiedWordCount: number
  learnedCount: number
  favoriteCount: number
  averageMastery: number
  answeredCount: number
  correctCount: number
  accuracyPercent: number
  activeMs: number
  todayActiveMs: number
  lastStudiedAt: string | null
}

export interface JpnStatusRow {
  wordId: number
  word: string
  reading: string | null
  jlptLevel: string | null
  partOfSpeech: string | null
  book: string | null
  category: string | null
  learnState: LearnState
  mastery: number
  learned: boolean
  favorite: boolean
  answeredCount: number
  correctCount: number
  wrongCount: number
  streak: number
  bestStreak: number
  activeMs: number
  lastTestType: string | null
  lastJudgment: string | null
  firstStudiedAt: string | null
  lastStudiedAt: string | null
  nextReviewAt: string | null
  reviewIntervalDays: number
}

export interface JpnDailyRow {
  studyDate: string
  activeMs: number
  typeAMs: number
  typeBMs: number
  typeCMs: number
  typeDMs: number
  typeEMs: number
  wordCount: number
  testCount: number
  doneCount: number
  correctCount: number
  wrongCount: number
}

export interface JpnStatusPage {
  summary: JpnStatusSummary
  daily: JpnDailyRow[]
  items: JpnStatusRow[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export interface JpnSkillRow {
  wordId: number
  word: string
  reading: string | null
  testType: string
  skillCode: string
  learnState: LearnState
  mastery: number
  answeredCount: number
  correctCount: number
  wrongCount: number
  streak: number
  bestStreak: number
  lastJudgment: string | null
  lastStudiedAt: string | null
  nextReviewAt: string | null
}

export interface JpnSkillPage {
  items: JpnSkillRow[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export interface JpnWordSave {
  word: string
  reading?: string
  jlptLevel?: string
  partOfSpeech?: string
  stateCode?: string
  note?: string
  version?: number
}

export interface JpnTestCreate {
  testType: TestType
  level?: string
  book?: string
  categoryFrom?: string
  categoryTo?: string
  difficulty?: string
  mode?: 'ALL' | 'RANDOM'
  questionCount: number
}

export interface JpnAnswerSave {
  orderNo: number
  choiceId?: number
  answerText?: string
  elapsedMs?: number
}

export interface JpnWordMutation {
  word: JpnWord
  message: string
}

export interface JpnTestMutation {
  test: JpnTest
  message: string
}

export interface JpnSimpleResult {
  count: number
  message: string
}

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/user/japanese' })

/* ---------- 単語情報管理 ---------- */

export function searchJpnWords(params: {
  keyword?: string
  reading?: string
  jlpt?: string
  part?: string
  state?: string
  book?: string
  category?: string
  learnState?: string
  page?: number
  size?: number
}): Promise<ApiResponse<JpnWordPage>> {
  return http.get<JpnWordPage>('/words', { params })
}

export function fetchJpnWord(wordId: number): Promise<ApiResponse<JpnWordDetailResult>> {
  return http.get<JpnWordDetailResult>(`/words/${wordId}`)
}

export function createJpnWord(body: JpnWordSave): Promise<ApiResponse<JpnWordMutation>> {
  return http.post<JpnWordMutation>('/words', { body })
}

export function updateJpnWord(wordId: number, body: JpnWordSave): Promise<ApiResponse<JpnWordMutation>> {
  return http.put<JpnWordMutation>(`/words/${wordId}`, { body })
}

export function deleteJpnWord(wordId: number): Promise<ApiResponse<JpnSimpleResult>> {
  return http.delete<JpnSimpleResult>(`/words/${wordId}`)
}

export function setJpnWordFavorite(wordId: number, favorite: boolean): Promise<ApiResponse<JpnWordMutation>> {
  return http.patch<JpnWordMutation>(`/words/${wordId}/favorite`, { body: { favorite } })
}

export function setJpnWordLearned(wordId: number, learned: boolean): Promise<ApiResponse<JpnWordMutation>> {
  return http.patch<JpnWordMutation>(`/words/${wordId}/learned`, { body: { learned } })
}

/* ---------- 単語テスト ---------- */

export function searchJpnTests(params: {
  state?: string
  testType?: string
  page?: number
  size?: number
}): Promise<ApiResponse<JpnTestPage>> {
  return http.get<JpnTestPage>('/tests', { params })
}

export function fetchJpnTest(testId: number): Promise<ApiResponse<JpnTestDetail>> {
  return http.get<JpnTestDetail>(`/tests/${testId}`)
}

export function createJpnTest(body: JpnTestCreate): Promise<ApiResponse<JpnTestDetail>> {
  return http.post<JpnTestDetail>('/tests', { body })
}

export function answerJpnTest(testId: number, body: JpnAnswerSave): Promise<ApiResponse<JpnAnswerResult>> {
  return http.post<JpnAnswerResult>(`/tests/${testId}/answers`, { body })
}

export function completeJpnTest(testId: number): Promise<ApiResponse<JpnTestMutation>> {
  return http.post<JpnTestMutation>(`/tests/${testId}/complete`)
}

export function deleteJpnTest(testId: number): Promise<ApiResponse<JpnSimpleResult>> {
  return http.delete<JpnSimpleResult>(`/tests/${testId}`)
}

/* ---------- 単語勉強状況 ---------- */

export function fetchJpnStatus(params: {
  learnState?: string
  jlpt?: string
  page?: number
  size?: number
}): Promise<ApiResponse<JpnStatusPage>> {
  return http.get<JpnStatusPage>('/status', { params })
}

export function searchJpnSkills(params: {
  testType?: string
  skill?: string
  page?: number
  size?: number
}): Promise<ApiResponse<JpnSkillPage>> {
  return http.get<JpnSkillPage>('/status/skills', { params })
}

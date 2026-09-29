import { HttpClient, type ApiResponse } from '@study21/web-shared'
import type { DemoDetailContent } from '@/features/japanese-demo/types'

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
  A: 'A：勉強',
  B: 'B：意味→日本語',
  C: 'C：漢字→読み',
  D: 'D：文脈詞義判断',
  E: 'E：漢字選択'
}

/**
 * AI が作った問題の種別（`JPN_単語問題情報.問題種別`）の日本語名。
 *
 * 4 種別は 2.0 の画面とテストの A〜E に対応する（C は読みと漢字の 2 種類ある）。
 * `問題種別` に CHECK が無く種別が増えうるので、未知のコードはコードのまま出す。
 */
export const QUESTION_TYPE_LABELS: Record<string, string> = {
  A_STUDY: 'A：勉強',
  B1_WRITING: 'B：表記',
  B2_READING: 'B：読み',
  C1_READING: 'C：漢字→読み',
  C2_KANJI: 'C：漢字の選択',
  D_CONTEXT_MEANING: 'D：文脈詞義判断',
  E_KANJI_USAGE: 'E：漢字選択'
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

/**
 * 一覧の「取得状態」列に出す AI 取得の状態（`v_jpn_word_ai_state` の最新行を 4 区画にまとめたもの）。
 * 値は `QUEUED` / `RUNNING` / `SUCCEEDED` / `FAILED` / `CANCELED`。まだ実行していない区画は null。
 */
export interface JpnWordAiState {
  /** A・B（詳細） */
  detail: string | null
  /** C（読み問題。C1 か C2 のどちらかが成功していれば SUCCEEDED） */
  reading: string | null
  /** D（文脈問題） */
  context: string | null
  /** E（漢字問題） */
  kanji: string | null
}

/**
 * 有効版の詳細にある段落の件数（一覧の「詳細情報件数」列）。
 *
 * <p>2.0 の英語学習（`word.jsp`）は「語義 2・例文 3…」のように段落ごとの件数を出していた。
 * 2.0 は詳細を 1 つの JSON に持っていたので配列の長さを数えていたが、2.1 は段落を子テーブルに
 * 分けて持つので**行数**を数える。まだ詳細が無い語は `null`（画面は「—」）。</p>
 */
export interface JpnWordDetailCounts {
  /** 語義 */
  senses: number
  /** 例文 */
  examples: number
  /** 文型 */
  patterns: number
  /** 会話（発言は数えない） */
  dialogs: number
  /** 類義語 */
  synonyms: number
  /** 注意（間違えやすいポイント） */
  cautions: number
  /** コロケーション */
  collocations: number
  /** 関連語 */
  relatedWords: number
  /** 使用場面 */
  usageNotes: number
  /** ミニ練習 */
  practices: number
}

export interface JpnWord {
  wordId: number
  word: string
  reading: string
  jlptLevel: string | null
  partOfSpeech: string | null
  /**
   * 中国語訳（一覧の列）。単語情報には列が無いので、**有効版の詳細の最初の語義の中国語**を
   * 一覧 API が引いて返す（詳細がまだ無ければ null＝画面は「—」）。
   */
  chineseMeaning: string | null
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
  /** AI 取得の状態（A・B／C／D／E の 4 区画） */
  aiState: JpnWordAiState
  /**
   * 有効版の詳細の段落の件数（一覧の「詳細情報件数」列）。
   * 詳細がまだ無い語は null（画面は「—」）。
   */
  detailCounts: JpnWordDetailCounts | null
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
 * 詳細 1 版（`JPN_単語詳細情報` の版ヘッダ ＋ 11 の子テーブルをサーバーが 1 つの JSON に
 * 組み立てたもの）。
 *
 * **キーはバックエンドの産出が唯一の規範**（`JpnWordDetailAssembler` /
 * `JapaneseWordAiDtoMapper.toDetailJson`）。2.0 の列名（`noteType` / `relationType` など）は
 * もう返らないので読まない。
 */
export interface JpnWordDetail {
  detailId: number
  contentVersion: number
  /**
   * この版の `バージョン`（楽観ロック）。
   *
   * **版ごとに違う値**なので、版を切り替えるときは版の一覧（`JpnDetailVersion.version`）の
   * 値を送る。ここにあるのは「いま表示している版」の値。
   */
  version: number
  aiProvider: string | null
  aiModel: string | null
  fetchedAt: string | null
  /**
   * 段落の件数。
   * 一覧 API は取得状態（A・B／C〜E）の判定に使うため、配列を持たずに数だけを返す。
   * 詳細 API では配列の長さと同じ値が返るので、詳細ダイアログはこちらを使わなくてよい。
   */
  senseCount?: number
  exampleCount?: number
  pronunciationCount?: number
  collocationCount?: number
  relatedWordCount?: number
  cautionCount?: number
  detail: JpnWordDetailBody
}

/** 版ヘッダ ＋ 11 段落の 1 版。規範のキー（バックエンドの産出）だけを持つ。 */
export interface JpnWordDetailBody {
  /** 詳細編集で保存した全項目（旧キー。参照専用で、読み出しには使わない）。 */
  editorContent?: DemoDetailContent | null
  alternateReading?: string | null
  /* 語レベルの項目（版ヘッダ） */
  jlptLevel?: string | null
  partOfSpeech?: string | null
  conjugation?: string | null
  transitivity?: string | null
  importance?: number | null
  /** 中国語の代表義（`coreMeaning` と同じ値が入る）。 */
  chineseMeaning?: string | null
  /** 一言の核心的な意味。 */
  coreMeaning?: string | null
  descriptionJa?: string | null
  descriptionZh?: string | null
  structuredSchemaVersion?: string | null
  manuallyCorrected?: boolean | null
  /** AI の生の応答（元レスポンス JSON）。 */
  structured?: Record<string, unknown> | null
  /* 11 の段落 */
  senses?: JpnSense[]
  examples?: JpnExample[]
  patterns?: JpnPattern[]
  dialogs?: JpnDialog[]
  synonyms?: JpnSynonym[]
  cautions?: JpnCaution[]
  conjugations?: JpnConjugation[]
  transitivityPair?: JpnTransitivityPair | null
  pronunciation?: JpnPronunciation | null
  /** 発音から作る 0/1 件の配列（旧 API 互換）。 */
  pronunciations?: JpnPronunciationRow[]
  collocations?: JpnCollocation[]
  relatedWords?: JpnRelatedWord[]
  usageNotes?: JpnUsageNote[]
  memoryHint?: JpnMemoryHint | null
  practices?: JpnPractice[]
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

/** 例文のレベル（`BASIC`＝やさしい例文／`APPLIED`＝応用例文）。 */
export type JpnExampleLevel = 'BASIC' | 'APPLIED'

/** 例文。 */
export interface JpnExample {
  japanese: string | null
  reading: string | null
  chinese: string | null
  senseNumber: number | null
  source: string | null
  level: JpnExampleLevel | null
}

/** 文型・助詞の使い方（助詞を `**` で囲む）。 */
export interface JpnPattern {
  pattern: string | null
  reading: string | null
  chinese: string | null
  example: string | null
  exampleChinese: string | null
}

/** 会話の 1 文。 */
export interface JpnDialogLine {
  speaker: string | null
  japanese: string | null
  chinese: string | null
}

/** 会話（場面つき）。 */
export interface JpnDialog {
  scene: string | null
  lines: JpnDialogLine[]
}

/** 類義語・使い分け。 */
export interface JpnSynonym {
  heading: string | null
  reading: string | null
  chinese: string | null
  shared: string | null
  difference: string | null
  scene: string | null
}

/** 活用形（形の名前・活用した形・短い例文）。 */
export interface JpnConjugation {
  form: string | null
  value: string | null
  example: string | null
}

/** 自他動詞の対応。 */
export interface JpnTransitivityPair {
  intransitive: string | null
  transitive: string | null
  particleNote: string | null
  intransitiveExample: string | null
  transitiveExample: string | null
}

/** 発音・アクセント（画面が使う形）。 */
export interface JpnPronunciation {
  reading: string | null
  accentType: number | null
  accentNotation: string | null
  hint: string | null
  hasAudioSample: boolean | null
}

/** 発音の行（`pronunciations[]`。旧 API 互換の配列）。 */
export interface JpnPronunciationRow {
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
  usage: string | null
  exampleJapanese: string | null
  exampleChinese: string | null
}

/** 関連語の関係（規範の値はこの 4 つ）。 */
export type JpnRelatedRelation = '類義語' | '対義語' | '間違えやすい' | '同じ読み'

/** 関連語（類義語・対義語・間違えやすい語・同じ読み）。 */
export interface JpnRelatedWord {
  relation: JpnRelatedRelation | string | null
  heading: string | null
  reading: string | null
  chinese: string | null
}

/** 使用注意の区分。 */
export type JpnCautionKind = 'GRAMMAR' | 'UNNATURAL' | 'MEANING' | 'PARTICLE'

/** 間違えやすいところ。 */
export interface JpnCaution {
  kind: JpnCautionKind | null
  title: string | null
  wrong: string | null
  correct: string | null
  reason: string | null
}

/** 使用場面・語感のメモ。 */
export interface JpnUsageNote {
  senseNumber: number | null
  register: string | null
  politeness: string | null
  audience: string | null
  note: string | null
}

/** 記憶のヒント（語源ではない。記憶の助けとして明示する）。 */
export interface JpnMemoryHint {
  hint: string | null
  basis: string | null
}

/** ミニ練習の種類。 */
export type JpnPracticeKind = 'PARTICLE' | 'SYNONYM' | 'SCENE' | 'WRITING'

/** ミニ練習（`choices` は文字列の配列。記述式は空）。 */
export interface JpnPractice {
  kind: JpnPracticeKind | null
  question: string | null
  questionChinese: string | null
  choices: string[]
  freeWriting: boolean | null
  answer: string | null
  explanation: string | null
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

/**
 * この出題で実際に見せた選択肢（テスト作成時に固定したもの）。
 *
 * プール（問題が持つ正解 1 ＋ 誤答 4〜6）から「正解 1 ＋ 誤答 3」を選んで並べ替えた 4 件で、
 * テストを開き直してもプールを取り直しても変わらない。
 */
export interface JpnChoice {
  /**
   * プールの選択肢ID（C/D/E）。
   *
   * A・B はプールを持たないので `null`（そのときは `orderNo` で答える）。
   */
  choiceId: number | null
  orderNo: number
  value: string
  reading: string | null
  correct: boolean
  description: string | null
  /**
   * 回答でサーバーへ送る値。
   *
   * プールの選択肢ID があればそれ、無ければ表示順。**画面はこちらを使う**
   * （`choiceId` が null の出題でも同じ送り方にするため）。
   */
  choiceKey: number
}

export interface JpnTestQuestionView {
  snapshot?: Record<string, unknown>
  history?: { answer?: string; reading?: string; choiceId?: number; correct: boolean; judgment?: string; at: string }[]
  question: JpnTestQuestion
  choices: JpnChoice[]
}

export interface JpnTestDetail {
  test: JpnTest
  questions: JpnTestQuestionView[]
}

export interface JpnAnswerResult {
  answered?: boolean
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
  readingText?: string
  orderNo: number
  choiceId?: number
  answerText?: string
  elapsedMs?: number
}

export interface JpnWordMutation {
  word: JpnWord
  message: string
}

/** 詳細 1 版の段落ごとの行数（版の一覧に出す。会話の発言は会話に含めて数えない）。 */
export interface JpnDetailVersionCounts {
  senses: number
  examples: number
  patterns: number
  dialogs: number
  synonyms: number
  cautions: number
  collocations: number
  relatedWords: number
  usageNotes: number
  practices: number
}

/** 詳細の版 1 つの要約（「版の履歴」の 1 行）。 */
export interface JpnDetailVersion {
  detailId: number
  /** 内容の版数（`内容版数`）。画面に出す「版番号」。 */
  contentVersion: number
  /**
   * この版の `バージョン`（楽観ロック）。
   *
   * 【この版を使う】は**この値**を送る（表示中の版の値ではない）。
   */
  version: number
  /** `ACTIVE` / `ARCHIVED`。 */
  stateCode: string
  /** いま有効な版か。 */
  active: boolean
  /** 人が手を入れた版か。 */
  manual: boolean
  aiProvider: string | null
  aiModel: string | null
  generationId: number | null
  /** 取得した日時。 */
  fetchedAt: string | null
  note: string | null
  createdAt: string | null
  updatedAt: string | null
  counts: JpnDetailVersionCounts
}

/** 版の一覧（**新しい順**）。 */
export interface JpnDetailVersions {
  items: JpnDetailVersion[]
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
  /** 分類の範囲（From ～ To。教材の課次 Unit001〜Unit005 など。片方だけでもよい） */
  categoryFrom?: string
  categoryTo?: string
  learnState?: string
  page?: number
  size?: number
}): Promise<ApiResponse<JpnWordPage>> {
  return http.get<JpnWordPage>('/words', { params })
}

export function fetchJpnWord(wordId: number): Promise<ApiResponse<JpnWordDetailResult>> {
  return http.get<JpnWordDetailResult>(`/words/${wordId}`)
}

export interface JpnWordEditorSave {
  word: JpnWordSave
  contentVersion: number
  detail: JpnWordDetail['detail']
}

export function saveJpnWordEditor(wordId: number, body: JpnWordEditorSave): Promise<ApiResponse<JpnWordDetailResult>> {
  return http.put<JpnWordDetailResult>(`/words/${wordId}/editor`, { body })
}

/**
 * 詳細の版の履歴（**新しい順**）。
 *
 * 版ごとに「誰が作ったか（AI／人）」「いつ取得したか」「段落が何件あるか」が返る。
 */
export function fetchJpnDetailVersions(wordId: number): Promise<ApiResponse<JpnDetailVersions>> {
  return http.get<JpnDetailVersions>(`/words/${wordId}/detail-versions`)
}

/**
 * 指定した版を有効にする（楽観的ロックは `version`）。
 *
 * `version` は**切り替えたい版の `バージョン`**（版の一覧 `JpnDetailVersion.version` が返す）。
 * 表示中の版の値ではないので、画面は一覧の行の値をそのまま渡す。
 * ほかの操作が先に更新していれば 409（画面は読み直しを促す）。
 */
export function activateJpnDetailVersion(
  wordId: number,
  detailId: number,
  version: number
): Promise<ApiResponse<JpnWordDetailResult>> {
  return http.put<JpnWordDetailResult>(`/words/${wordId}/detail-versions/${detailId}/active`, { body: { version } })
}

/**
 * 問題（C/D/E）の 1 版（一覧の「取得状態」のタグから開く履歴の 1 行）。
 *
 * 2.0 の英語学習（`word.jsp`）の「詳細情報取得履歴」と同じで、**AI の取得 1 回 = 1 行**。
 * `questionCount` が 0 の版（失敗した取得）は切り替えられない。
 */
export interface JpnQuestionVersion {
  /** C1_READING / C2_KANJI / D_CONTEXT_MEANING / E_KANJI_USAGE */
  questionType: string
  /** この取得の版（1 から）。 */
  contentVersion: number
  /** その版の問題数（0 = 失敗した取得）。 */
  questionCount: number
  /** 今その版を使っているか。 */
  active: boolean
  /** 生成の状態（RUNNING / SUCCEEDED / FAILED）。 */
  generationState: string | null
  aiProvider: string | null
  aiModel: string | null
  generatedCount: number
  failedCount: number
  errorMessage: string | null
  startedAt: string | null
  finishedAt: string | null
}

export interface JpnQuestionVersions {
  items: JpnQuestionVersion[]
  totalCount: number
}

/** 問題（C/D/E）の版の履歴（新しい順。AI の取得 1 回 = 1 行）。 */
export function fetchJpnQuestionVersions(wordId: number): Promise<ApiResponse<JpnQuestionVersions>> {
  return http.get<JpnQuestionVersions>(`/words/${wordId}/question-versions`)
}

/**
 * 問題の版を切り替える（その種別の中で、指定した版だけを有効にする）。
 *
 * 問題は消さない（過去のテストの出題が参照している）ので、切り替えても履歴は壊れない。
 */
export function activateJpnQuestionVersion(
  wordId: number,
  questionType: string,
  contentVersion: number
): Promise<ApiResponse<JpnQuestionVersions>> {
  return http.put<JpnQuestionVersions>(
    `/words/${wordId}/question-versions/${encodeURIComponent(questionType)}/active`,
    { body: { contentVersion } }
  )
}

export function createJpnWord(body: JpnWordSave): Promise<ApiResponse<JpnWordMutation>> {
  return http.post<JpnWordMutation>('/words', { body })
}

/**
 * 新規登録画面の保存（**語と収録をまとめて**入れる）。
 *
 * 語だけを作ると一覧の「書籍」「分類」が空になるので、画面はこちらを使う。
 * 同じ見出し語・読みの語が既にあれば作り直さず、収録だけを足す。
 */
export interface JpnRegisterWord {
  /** 既存の語を使うときの ID（新規なら省略）。 */
  wordId?: number
  word: string
  reading?: string
  /** 教材のレベル（2.0 の 収録.レベル。省略時は 'N1-N5'）。 */
  level?: string
  /** 教材の書籍名（必須）。 */
  book: string
  /** Unit（例: Unit001。必須）。 */
  category: string
  /** Unit の中での順番（1 から。省略時はサーバーが次の番号を決める）。 */
  wordSeq?: number
  /** 教材の紙面どおりの表記（省略時は見出し語）。 */
  listedWord?: string
  listedPartOfSpeech?: string
  listedChineseMeaning?: string
}

export interface JpnRegisterResult {
  wordCount: number
  collectionCount: number
  skippedCount: number
  skipped: string[]
  message: string
}

export function registerJpnWords(words: JpnRegisterWord[]): Promise<ApiResponse<JpnRegisterResult>> {
  return http.post<JpnRegisterResult>('/words/register', { body: { words } })
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

/* ---------- AI 取得（batC41〜batC44） ---------- */

/**
 * 日本語単語の AI 取得の起動（admin-api `/api/admin/batch/japanese-word-ai/run`）。
 *
 * batC41〜44 は**種別 C（呼出）**なので、バッチ管理画面の【再実行】にボタンが出ない。
 * その代わりにこの入口を日本語単語の画面から呼ぶ（AI 生図の起動と同じ考え方）。
 */
const aiHttp = new HttpClient({ baseUrl: '/api/admin/batch/japanese-word-ai' })

/**
 * AI 取得の待ち時間（`/run`）。
 *
 * <p>2026-09-27 に `/run` は**受付**（{@code QUEUED} を積んで即返る・実行は働き手）へ変えたので、
 * 実際には数十ミリ秒で返る。それでも 60 秒にしてあるのは、<b>nginx の
 * {@code proxy_read_timeout 60s} と同じ値に揃えておく</b>ため（利用者の指示 2026-09-27。
 * 片方だけ変えると短いほうで切れる。サーバーが混んで受付に時間がかかっても、
 * 画面が理不尽に早く諦めない）</p>
 */
export const AI_RUN_TIMEOUT_MS = 60_000

/** 取得区分（画面のボタンとの対応）。 */
export type JpnAiKind = 'DETAIL' | 'C' | 'D' | 'E'

/** 取得区分の説明（AI に何を作らせるか）。 */
export const JPN_AI_KIND_LABELS: Record<JpnAiKind, string> = {
  DETAIL: '詳細情報（A・B）',
  C: '読み問題・漢字問題（C）',
  D: '文脈問題（D）',
  E: '漢字問題（E）'
}

/**
 * AI 取得の**受付**の結果（`POST /api/admin/batch/japanese-word-ai/run`）。
 *
 * <p>2026-09-27 に同期実行から**受付**へ変更した。実行はバックエンドの働き手が行うので、
 * ここには「何件受付けたか」だけが返る（成功・失敗の件数は一覧の「取得状態」で見る）。</p>
 */
export interface JpnAiRunResult {
  /** 受付けた件数（語 × 内容種別）。 */
  accepted: number
  /** 既に実行中で、積まなかった件数。 */
  reused: number
  /** 受付けた行の生成 ID（進み具合をこの ID で見る。実行中だった行も含む）。 */
  generationIds?: number[]
  batchCode?: string
  kind?: string
  wordIds?: number[]
}

/**
 * 受付けた取得の進み具合（`GET /api/admin/batch/japanese-word-ai/progress`）。
 *
 * <p>一覧の「取得状態」は**一度でも成功したか**を優先して返すので、取り直しの進み具合には
 * 使えない（成功済みの語は最初から「取得済」に見える）。受付が返した生成 ID で
 * **生の状態**を数えた結果がこれ（2026-09-27）。</p>
 */
export interface JpnAiProgress {
  /** まだ終わっていない件数（`QUEUED` / `RUNNING`）。 */
  pending: number
  succeeded: number
  failed: number
}

/**
 * AI 取得の対象（**検索条件に一致する語**のうち、今回受付ける分）。
 *
 * <p>2026-09-27: 対象は当ページではなく、**検索条件に一致する語全体**から
 * 表示順に選ぶ（サーバーが選ぶ。一覧と同じ絞り込み・同じ並び）。</p>
 */
export interface JpnAiTargets {
  /** 検索条件に一致する語数。 */
  total: number
  /** そのうち取得済みの語数。 */
  acquired: number
  /** 今回対象になりうる語数（「取得済みをスキップ」なら未取得の数）。 */
  candidates: number
  /** 今回受付ける語（上限まで）。 */
  wordIds: number[]
  /** 今回に収まらない数（次回に回す）。 */
  overLimit: number
  /** この回の上限（設定ページの「1 回の最大単語数」）。 */
  limit: number
}

/** 一覧と対象の選定で共通の絞り込み（同じ条件を渡す）。 */
export interface JpnWordFilters {
  keyword?: string
  reading?: string
  jlpt?: string
  part?: string
  state?: string
  book?: string
  categoryFrom?: string
  categoryTo?: string
  learnState?: string
}

/**
 * AI 取得の対象を選ぶ（`GET /api/user/japanese/words/ai-targets`）。
 *
 * <p>一覧（`/words`）と**同じ絞り込み**を渡す。ページは関係しない（何ページ目でも、
 * 条件に一致する語全体から表示順に選ぶ）。</p>
 */
export function fetchJpnAiTargets(
  params: JpnWordFilters & { kind: JpnAiKind; skipAcquired: boolean; limit: number }
): Promise<ApiResponse<JpnAiTargets>> {
  return http.get<JpnAiTargets>('/words/ai-targets', {
    params: {
      ...params,
      // 真偽は文字列で渡す（undefined の項目は HttpClient が落とす）
      skipAcquired: params.skipAcquired ? 'true' : 'false'
    }
  })
}

/** 受付けた取得の進み具合を取る。 */
export function fetchJpnAiProgress(generationIds: number[]): Promise<ApiResponse<JpnAiProgress>> {
  return aiHttp.get<JpnAiProgress>('/progress', {
    params: { generationIds: generationIds.join(',') }
  })
}

/**
 * 取得区分ごとの**1 回の上限**（`GET /api/admin/batch/japanese-word-ai/limits`）。
 *
 * <p>上限は**設定ページの「1 回の最大単語数」だけ**（`BAT_C41_BATCH_MAX`〜`BAT_C44_BATCH_MAX`）。
 * コード側には上限の定数を持たない（利用者の指示 2026-09-27）。読めないときはサーバーがエラーにし、
 * 画面は**実行させない**（既定値へ落とすと設定漏れに気づけないまま課金されうる）。</p>
 */
export interface JpnAiRunLimit {
  /** 対応するバッチコード（`batC41` など）。 */
  batchCode: string
  /** 1 回に渡せる語数。 */
  limit: number
}

export type JpnAiRunLimits = Partial<Record<JpnAiKind, JpnAiRunLimit>>

/**
 * 取得区分ごとの 1 回の上限を取る（画面の窓が「1 回の受付は N 語まで」と出す）。
 */
export function fetchJpnAiLimits(): Promise<ApiResponse<JpnAiRunLimits>> {
  return aiHttp.get<JpnAiRunLimits>('/limits')
}

/**
 * AI 取得を**受付ける**（`POST /api/admin/batch/japanese-word-ai/run`）。
 *
 * <p>2026-09-27 に同期実行から受付へ変更した。ここは {@code QUEUED} を積んで**すぐ返る**
 * （AI は呼ばない）。実行はバックエンドの働き手が行い、進み具合は一覧の「取得状態」に出る。</p>
 */
export function runJpnWordAi(
  kind: JpnAiKind,
  wordIds: number[]
): Promise<ApiResponse<JpnAiRunResult>> {
  return aiHttp.post<JpnAiRunResult>('/run', {
    body: { kind, wordIds, operator: 'japanese-word' },
    timeoutMs: AI_RUN_TIMEOUT_MS
  })
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

export function fetchJpnTestConditions(book = '') {
  return http.get<{ books: string[]; categories: string[] }>('/tests/conditions', { params: { book } })
}
export function searchJpnTestRange(params: { book?: string; categoryFrom?: string; categoryTo?: string; testType?: string; state?: string; page: number; size: number }) {
  return http.get<JpnTestPage>('/tests/search', { params })
}
export function createJpnTests(rows: JpnTestCreate[]) {
  return http.post<{ createdCount: number; skippedCount: number; results: { rowNo: number; testId: number | null; skipped: boolean; message: string }[] }>('/tests/multiple', { body: { rows } })
}


export function startJpnTest(testId: number) { return http.post<JpnTestDetail>(`/tests/${testId}/start`) }

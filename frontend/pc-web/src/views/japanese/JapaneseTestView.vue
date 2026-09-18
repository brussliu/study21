<script setup lang="ts">
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { ApiError, formatIsoDateTime, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import '@/features/japanese/japanese.css'
import {
  TEST_STATE_BADGES,
  TEST_STATE_LABELS,
  QUESTION_TYPE_LABELS,
  TEST_TYPE_LABELS,
  TEST_TYPE_OPTIONS,
  answerJpnTest,
  completeJpnTest,
  createJpnTest,
  deleteJpnTest,
  fetchJpnTest,
  searchJpnTests,
  type JpnTest,
  type JpnTestCreate,
  type JpnTestDetail,
  type JpnTestQuestionView,
  type TestState,
  type TestType
} from '@/api/japanese'
import { paginationItems } from '@/features/pagination/pagination'

/**
 * 日本語勉強【単語テスト】。
 *
 * 2.0 の japanese_test.jsp（履歴一覧）と japanese_test_a〜e.jsp＋japanese_word_test.js（出題）を
 * 1 つの画面にまとめ、3 つのモードを切り替える。
 *
 * ・履歴モード: これまでのテストの検索・新規作成・開始/再開・結果閲覧・削除
 * ・出題モード: 1 問ずつ選択肢で回答する（回答は API に保存し、誤答と解説はその場で出す）
 * ・結果モード: 正答率・学習時間・誤答一覧を出し、完了にする／同じ条件でやり直す
 */
type Mode = 'list' | 'question' | 'result'

/** 難易度（2.0 の並びに合わせる）。 */
const DIFFICULTY_OPTIONS = ['NORMAL', 'HARD', 'EASY'] as const

/** 出題方式。 */
const MODE_OPTIONS: { value: 'ALL' | 'RANDOM'; label: string }[] = [
  { value: 'ALL', label: '順番' },
  { value: 'RANDOM', label: 'ランダム' }
]

/** 状態の絞り込み（空文字は「すべて」）。 */
const STATE_OPTIONS: TestState[] = ['CREATED', 'RUNNING', 'COMPLETED']

/** 1 ページの件数。 */
const PAGE_SIZES = [20, 50, 100]

/** 1 回のテストで作れる出題数の範囲。 */
const MIN_QUESTION_COUNT = 1
const MAX_QUESTION_COUNT = 50

const toast = useToast()

/* ---------- 画面全体の状態 ---------- */

const mode = ref<Mode>('list')
const loading = ref(false)
const busy = ref(false)
const error = ref('')

/* ---------- 履歴モード ---------- */

const tests = ref<JpnTest[]>([])
const totals = ref({ testCount: 0, completedCount: 0, runningCount: 0, averageScore: 0, totalActiveMs: 0 })
const totalElements = ref(0)
const totalPages = ref(1)
const page = ref(1)
const pageSize = ref(20)

// 絞り込み（入力中）と適用済みを分け、「検索」で反映する。
const stateFilter = ref('')
const typeFilter = ref('')
const appliedState = ref('')
const appliedType = ref('')

/** 0 件でもサマリを出せるよう、API の合計を使う。 */
const summaryText = computed(() => {
  const found = totalElements.value > 0 ? totalElements.value : tests.value.length
  const item = totals.value
  const testCount = item.testCount > 0 ? item.testCount : found
  return `テスト ${testCount} 回 ・ 完了 ${item.completedCount} ・ 学習中 ${item.runningCount} ・ 平均正答率 ${item.averageScore}% ・ 総学習時間 ${millisToMinutes(item.totalActiveMs)} 分`
})

const pageItems = computed(() => paginationItems(page.value, Math.max(1, totalPages.value)))

/* ---------- 新規テスト作成ダイアログ ---------- */

const createOpen = ref(false)
const createError = ref('')
const questionForm = reactive<{
  testType: TestType
  level: string
  book: string
  categoryFrom: string
  categoryTo: string
  difficulty: string
  mode: 'ALL' | 'RANDOM'
  questionCount: number
}>({
  testType: 'A',
  level: '',
  book: '',
  categoryFrom: '',
  categoryTo: '',
  difficulty: 'NORMAL',
  mode: 'ALL',
  questionCount: 10
})

/* ---------- 出題・結果モード ---------- */

const detail = ref<JpnTestDetail | null>(null)
const index = ref(0)
const selected = ref<Record<number, number>>({})
const focusMode = ref(false)
/** 判定（正誤・正解・解説・経過時間）を出題番号ごとに持つ。 */
const answered = ref<Record<number, { correct: boolean; correctValue: string; explanation: string }>>({})
/** 結果モードで使う終了日時（API が返さない場合の控え）。 */
const finishedLocally = ref('')

/** テストを表示し始めた時刻（表示中の 1 問の経過時間を測る）。 */
let questionShownAt = Date.now()

const test = computed<JpnTest | null>(() => detail.value?.test ?? null)
const questions = computed<JpnTestQuestionView[]>(() => detail.value?.questions ?? [])
const current = computed<JpnTestQuestionView | null>(() => questions.value[index.value] ?? null)
const answeredCount = computed(() => Object.keys(answered.value).length)

/** 出題文と一緒に置く到達状況（済み / 全体）。 */
const doneCount = computed(() => test.value?.doneCount ?? answeredCount.value)
const questionCount = computed(() => test.value?.questionCount ?? questions.value.length)

/**
 * 正解・不正解の内訳。
 * 出題中は API が返す最新の件数を、取得できていないときはこの画面で付けた判定を使う。
 */
const scoreCounts = computed(() => {
  const row = test.value
  if (row !== null) return { correct: row.correctCount, wrong: row.wrongCount }
  const list = Object.values(answered.value)
  const correct = list.filter((item) => item.correct).length
  return { correct, wrong: list.length - correct }
})

/** 正答率（%）。出題中・結果のどちらも同じ数え方にする。 */
const accuracyPercent = computed(() => {
  const counts = scoreCounts.value
  const total = counts.correct + counts.wrong
  return total === 0 ? 0 : Math.round((counts.correct * 100) / total)
})

/** その問題が誤答かどうか（今回の回答、または API の判定のどちらでも見る）。 */
function isWrong(item: JpnTestQuestionView): boolean {
  const local = answered.value[item.question.orderNo]
  if (local !== undefined) return !local.correct
  return item.question.judgment === 'INCORRECT'
}

/** その問題に回答済みか（今回の回答、または API の判定）。 */
function isAnswered(item: JpnTestQuestionView | null): boolean {
  if (item === null) return false
  return answered.value[item.question.orderNo] !== undefined
      || item.question.state === 'ANSWERED'
      || item.question.judgment !== null
}

/** 誤答（判定が誤りの問題）だけを出題順に並べる。過去のテスト（API の判定）にも対応する。 */
const wrongAnswers = computed(() =>
  questions.value
    .filter((item) => isWrong(item))
    .map((item) => ({
      orderNo: item.question.orderNo,
      word: questionWordLabel(item),
      text: item.question.questionText,
      correctValue: answered.value[item.question.orderNo]?.correctValue ?? item.question.correctValue ?? '—',
      chosenValue: chosenValueOf(item)
    }))
)

const finishedAtLabel = computed(() => {
  const value = test.value?.finishedAt ?? finishedLocally.value
  return value === '' ? '—' : formatIsoDateTime(value)
})

/* ---------- 共通の小物 ---------- */

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

function millisToMinutes(millis: number): number {
  return Math.round(millis / 60000)
}

function restore(value: unknown, fallback = ''): string {
  return typeof value === 'string' && value !== '' ? value : fallback
}

/** 出題の見出し語（読みがあれば添える）。 */
function questionWordLabel(item: JpnTestQuestionView): string {
  const reading = item.question.reading
  return reading === null || reading === '' ? item.question.word : `${item.question.word}（${reading}）`
}

/** 出題の範囲（レベル・書籍・分類開始〜終了。無ければ「—」）。 */
function rangeLabel(row: JpnTest): string {
  const parts: string[] = []
  if (row.level !== null && row.level !== '') parts.push(row.level)
  if (row.book !== null && row.book !== '') parts.push(row.book)
  const from = row.categoryFrom ?? ''
  const to = row.categoryTo ?? ''
  if (from !== '' || to !== '') {
    parts.push(from === to ? from : `${from === '' ? '—' : from}〜${to === '' ? '—' : to}`)
  }
  return parts.length === 0 ? '—' : parts.join(' ・ ')
}

/** 問題の種類（API のコードを日本語にして出す。未知のコードはテスト種別を出す）。 */
function questionTypeLabel(item: JpnTestQuestionView): string {
  const code = item.question.questionType ?? ''
  const row = test.value
  return QUESTION_TYPE_LABELS[code] ?? (row === null ? '—' : TEST_TYPE_LABELS[row.testType])
}

function stateLabel(state: TestState): string {
  return TEST_STATE_LABELS[state]
}

function stateBadge(state: TestState): string {
  return TEST_STATE_BADGES[state]
}

/** 選択肢の読み（問題の種類によっては正解の読みが入っている）。 */
function choiceReading(choiceId: number): string {
  const item = current.value
  if (item === null) return ''
  return item.choices.find((choice) => choice.choiceId === choiceId)?.reading ?? ''
}

/* ---------- 履歴モードの操作 ---------- */

async function loadTests(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await searchJpnTests({
      state: appliedState.value === '' ? undefined : appliedState.value,
      testType: appliedType.value === '' ? undefined : appliedType.value,
      page: page.value,
      size: pageSize.value
    })
    const data = response.data
    tests.value = data.items
    totalElements.value = data.totalElements
    totalPages.value = Math.max(1, data.totalPages)
    if (data.totals !== undefined) totals.value = data.totals
    // 削除などでページが余ったときは前のページに寄せる。
    if (page.value > totalPages.value) {
      page.value = totalPages.value
      await loadTests()
    }
  } catch (cause) {
    error.value = messageOf(cause, 'テストの一覧を取得できませんでした。')
    tests.value = []
  } finally {
    loading.value = false
  }
}

function search(): void {
  appliedState.value = stateFilter.value
  appliedType.value = typeFilter.value
  page.value = 1
  void loadTests()
}

function reset(): void {
  stateFilter.value = ''
  typeFilter.value = ''
  appliedState.value = ''
  appliedType.value = ''
  page.value = 1
  void loadTests()
}

function goto(target: number): void {
  page.value = Math.min(Math.max(1, target), Math.max(1, totalPages.value))
  void loadTests()
}

function changePageSize(): void {
  page.value = 1
  void loadTests()
}

/** 作成ダイアログを既定値で開く。 */
function newTest(): void {
  questionForm.testType = 'A'
  questionForm.level = ''
  questionForm.book = ''
  questionForm.categoryFrom = ''
  questionForm.categoryTo = ''
  questionForm.difficulty = 'NORMAL'
  questionForm.mode = 'ALL'
  questionForm.questionCount = 10
  createError.value = ''
  createOpen.value = true
}

function closeCreate(): void {
  createOpen.value = false
  createError.value = ''
}

/** 受けたテストと同じ条件で、新しいテストのダイアログを開く。 */
function retryTest(): void {
  const row = test.value
  if (row === null) return
  questionForm.testType = row.testType
  questionForm.level = row.level ?? ''
  questionForm.book = row.book ?? ''
  questionForm.categoryFrom = row.categoryFrom ?? ''
  questionForm.categoryTo = row.categoryTo ?? ''
  questionForm.difficulty = restore(row.difficulty, 'NORMAL')
  questionForm.mode = row.mode === 'RANDOM' ? 'RANDOM' : 'ALL'
  questionForm.questionCount = row.questionCount > 0 ? row.questionCount : 10
  createError.value = ''
  createOpen.value = true
}

async function saveCreate(): Promise<void> {
  if (busy.value) return
  const count = Number(questionForm.questionCount)
  if (!Number.isInteger(count) || count < MIN_QUESTION_COUNT || count > MAX_QUESTION_COUNT) {
    createError.value = `出題数は ${MIN_QUESTION_COUNT}〜${MAX_QUESTION_COUNT} の範囲で入力してください。`
    return
  }
  const body: JpnTestCreate = {
    testType: questionForm.testType,
    level: questionForm.level.trim() === '' ? undefined : questionForm.level.trim(),
    book: questionForm.book.trim() === '' ? undefined : questionForm.book.trim(),
    categoryFrom: questionForm.categoryFrom.trim() === '' ? undefined : questionForm.categoryFrom.trim(),
    categoryTo: questionForm.categoryTo.trim() === '' ? undefined : questionForm.categoryTo.trim(),
    difficulty: questionForm.difficulty,
    mode: questionForm.mode,
    questionCount: count
  }
  busy.value = true
  createError.value = ''
  try {
    const response = await createJpnTest(body)
    createOpen.value = false
    toast.success(`テスト「${response.data.test.testNo}」を作成しました。`)
    openSession(response.data)
  } catch (cause) {
    createError.value = messageOf(cause, 'テストを作成できませんでした。')
  } finally {
    busy.value = false
  }
}

async function removeTest(row: JpnTest): Promise<void> {
  if (!window.confirm(`テスト「${row.testNo}」を削除しますか。回答履歴を含むテストデータが削除されます。`)) return
  try {
    const response = await deleteJpnTest(row.testId)
    toast.success(response.data.message)
    await loadTests()
  } catch (cause) {
    toast.danger(messageOf(cause, 'テストを削除できませんでした。'))
  }
}

/* ---------- 出題モードの操作 ---------- */

/** テストを読み込んで出題モードに入る（開始・再開）。 */
async function startTest(row: JpnTest): Promise<void> {
  if (busy.value) return
  busy.value = true
  error.value = ''
  try {
    const response = await fetchJpnTest(row.testId)
    openSession(response.data)
  } catch (cause) {
    error.value = messageOf(cause, 'テストを取得できませんでした。')
  } finally {
    busy.value = false
  }
}

/**
 * 読み込んだテストで出題モードに入る。
 * 完了済みのテストを見直すときは、そのまま結果モードで開く。
 */
function openSession(data: JpnTestDetail): void {
  detail.value = data
  selected.value = {}
  answered.value = {}
  finishedLocally.value = ''
  for (const item of data.questions) {
    if (item.question.state === 'ANSWERED' && item.question.judgment !== null) {
      answered.value[item.question.orderNo] = {
        correct: item.question.judgment === 'CORRECT',
        correctValue: item.question.correctValue ?? '',
        explanation: item.question.explanation ?? ''
      }
    }
  }
  // 最初はまだ答えていない問題から始める。
  const firstPending = data.questions.findIndex((item) => answered.value[item.question.orderNo] === undefined)
  index.value = firstPending < 0 ? 0 : firstPending
  focusMode.value = false
  questionShownAt = Date.now()
  mode.value = data.test.state === 'COMPLETED' || isAllAnswered() ? 'result' : 'question'
}

/** すべての問題に回答済みか。 */
function isAllAnswered(): boolean {
  const list = questions.value
  return list.length > 0 && list.every((item) => answered.value[item.question.orderNo] !== undefined)
}

/** 出題モードの見出しに出す到達状況。 */
function hasAnsweredQuestion(): boolean {
  return answeredCount.value > 0
}

function answerOf(item: JpnTestQuestionView | null): { correct: boolean; correctValue: string; explanation: string } | null {
  if (item === null) return null
  return answered.value[item.question.orderNo] ?? null
}

function choiceClass(choiceId: number): Record<string, boolean> {
  const item = current.value
  if (item === null) return {}
  const result = answered.value[item.question.orderNo]
  if (result === undefined) return { 'is-selected': selected.value[item.question.orderNo] === choiceId }
  const choice = item.choices.find((entry) => entry.choiceId === choiceId)
  const isCorrect = choice?.correct === true
  const isChosen = selected.value[item.question.orderNo] === choiceId
  return { 'is-correct': isCorrect, 'is-wrong': isChosen && !isCorrect }
}

/** 自分が選んだ選択肢の本文（誤答一覧で使う）。 */
function chosenValueOf(item: JpnTestQuestionView): string {
  const choiceId = selected.value[item.question.orderNo]
  if (choiceId === undefined) return '（未回答）'
  return item.choices.find((choice) => choice.choiceId === choiceId)?.value ?? '—'
}

function selectChoice(choiceId: number): void {
  const item = current.value
  if (item === null || isAnswered(item)) return
  selected.value[item.question.orderNo] = choiceId
}

/** 回答を保存し、判定と解説をその場に出す。 */
async function submitAnswer(): Promise<void> {
  const item = current.value
  const row = test.value
  if (item === null || row === null || busy.value || isAnswered(item)) return
  const choiceId = selected.value[item.question.orderNo]
  if (choiceId === undefined) {
    toast.warning('選択肢を選んでください。')
    return
  }
  busy.value = true
  const elapsedMs = Math.max(0, Date.now() - questionShownAt)
  try {
    const response = await answerJpnTest(row.testId, {
      orderNo: item.question.orderNo,
      choiceId,
      elapsedMs
    })
    const result = response.data
    answered.value[item.question.orderNo] = {
      correct: result.correct,
      correctValue: result.correctValue ?? '',
      explanation: result.explanation ?? ''
    }
    mergeTest(result.test)
    questionShownAt = Date.now()
    // 全問答え終えたら、そのまま結果モードへ移る。
    if (isAllAnswered()) {
      finishedLocally.value = new Date().toISOString()
      mode.value = 'result'
    }
  } catch (cause) {
    toast.danger(messageOf(cause, '回答を保存できませんでした。'))
  } finally {
    busy.value = false
  }
}

/** 回答後に返る最新のテスト情報を反映する。 */
function mergeTest(updated: JpnTest): void {
  if (detail.value === null) return
  const merged: JpnTestDetail = { test: updated, questions: detail.value.questions }
  for (const item of merged.questions) {
    const result = answered.value[item.question.orderNo]
    if (result !== undefined) {
      item.question.state = 'ANSWERED'
      item.question.judgment = result.correct ? 'CORRECT' : 'WRONG'
      item.question.answeredAt = item.question.answeredAt ?? new Date().toISOString()
    }
  }
  detail.value = merged
}

/** 出題を移動する（表示時刻を測り直す）。 */
function showQuestion(target: number): void {
  if (target < 0 || target >= questions.value.length) return
  index.value = target
  questionShownAt = Date.now()
}

function goPrev(): void {
  showQuestion(index.value - 1)
}

function goNext(): void {
  showQuestion(index.value + 1)
}

/** スキップ: 回答せずに次の未回答の問題へ進む。 */
function skip(): void {
  const list = questions.value
  for (let step = index.value + 1; step < list.length; step += 1) {
    const candidate = list[step]
    if (candidate !== undefined && answered.value[candidate.question.orderNo] === undefined) {
      showQuestion(step)
      return
    }
  }
  for (let step = 0; step < index.value; step += 1) {
    const candidate = list[step]
    if (candidate !== undefined && answered.value[candidate.question.orderNo] === undefined) {
      showQuestion(step)
      return
    }
  }
  toast.info('未回答の問題はありません。結果を確認してください。')
}

/** 中断: 途中までの回答を保存して一覧へ戻る。 */
async function suspend(): Promise<void> {
  const row = test.value
  if (row === null || busy.value) return
  if (!hasAnsweredQuestion()) {
    mode.value = 'list'
    void loadTests()
    return
  }
  if (!window.confirm(`テスト「${row.testNo}」を中断して一覧へ戻りますか。ここまでの回答は保存され、あとで再開できます。`)) return
  // 回答は 1 問ごとに保存済みなので、状態（RUNNING）はそのままで一覧へ戻る
  toast.info('ここまでの回答を保存しました。あとで「再開」から続けられます。')
  await backToList()
}

/* ---------- 結果モードの操作 ---------- */

/** 残りの問題を残したままテストを完了にする。 */
async function completeTest(): Promise<void> {
  const row = test.value
  if (row === null || busy.value) return
  busy.value = true
  try {
    const response = await completeJpnTest(row.testId)
    mergeTest(response.data.test)
    finishedLocally.value = response.data.test.finishedAt ?? new Date().toISOString()
    toast.success(response.data.message)
  } catch (cause) {
    toast.danger(messageOf(cause, 'テストを完了できませんでした。'))
  } finally {
    busy.value = false
  }
}

async function backToList(): Promise<void> {
  mode.value = 'list'
  detail.value = null
  selected.value = {}
  answered.value = {}
  focusMode.value = false
  await loadTests()
}

onMounted(loadTests)

// 画面を離れるときは進行中のテストを完了にして、次回も続きから開けるようにする。
onUnmounted(() => {
  const row = test.value
  if (mode.value === 'question' && row !== null && hasAnsweredQuestion()) {
    void completeJpnTest(row.testId).catch(() => undefined)
  }
})
</script>

<template>
  <div class="jp-page">
    <!-- ==================== 履歴一覧モード ==================== -->
    <template v-if="mode === 'list'">
      <p v-if="error" class="alert alert--danger">{{ error }}</p>

      <div class="jp-list-head">
        <div class="jp-summary" data-jp-summary>
          <span class="jp-summary__item">
            <span class="jp-summary__label">日本語単語テスト</span>
            <span class="jp-summary__value">{{ summaryText }}</span>
          </span>
        </div>
      </div>

      <div class="search-panel">
        <div class="search-panel__head">
          <h3 class="search-panel__title"><AppIcon name="search" /> テスト検索</h3>
          <div class="search-panel__actions">
            <button type="button" class="btn btn--primary" data-jp-test-create @click="newTest">
              <AppIcon name="plus" size="sm" /> 新しいテスト
            </button>
            <button type="button" class="btn btn--primary" data-jp-test-search @click="search">
              <AppIcon name="search" size="sm" /> 検索
            </button>
            <button type="button" class="btn btn--secondary" data-jp-test-reset @click="reset">
              <AppIcon name="rotate" size="sm" /> リセット
            </button>
          </div>
        </div>
        <div class="filters">
          <div class="filters__row">
            <span class="filter-item">
              <span class="filter-item__label">状態：</span>
              <select v-model="stateFilter" class="select" aria-label="状態" data-jp-test-state-filter>
                <option value="">すべて</option>
                <option v-for="state in STATE_OPTIONS" :key="state" :value="state">{{ stateLabel(state) }}</option>
              </select>
            </span>
            <span class="filter-item">
              <span class="filter-item__label">種別：</span>
              <select v-model="typeFilter" class="select" aria-label="テスト種別" data-jp-test-type-filter>
                <option value="">すべて</option>
                <option v-for="type in TEST_TYPE_OPTIONS" :key="type" :value="type">{{ TEST_TYPE_LABELS[type] }}</option>
              </select>
            </span>
          </div>
        </div>
      </div>

      <div class="table-section">
        <div class="table-section__head">
          <h3 class="table-section__title"><AppIcon name="list" size="sm" /> テストの履歴</h3>
          <span class="table-section__meta" data-jp-test-count>全 {{ totalElements }} 件</span>
        </div>
        <div class="table-wrap">
          <table class="data-table jp-test-table" data-jp-tests>
            <thead>
              <tr>
                <th>テスト番号</th>
                <th>種別</th>
                <th>範囲</th>
                <th>出題数</th>
                <th>正解・不正解</th>
                <th>正答率</th>
                <th>状態</th>
                <th>学習時間</th>
                <th>最終学習</th>
                <th class="col-actions">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-if="loading">
                <td colspan="10" class="jp-page__loading">読み込んでいます...</td>
              </tr>
              <tr v-else-if="tests.length === 0">
                <td colspan="10" class="jp-page__empty" data-jp-test-empty>
                  テストの履歴がありません。新しいテストを作成してください。
                </td>
              </tr>
              <tr v-for="row in tests" v-else :key="row.testId" :data-jp-test-row="row.testId">
                <td class="cell-strong">{{ row.testNo }}</td>
                <td>{{ TEST_TYPE_LABELS[row.testType] }}</td>
                <td class="cell-muted jp-col-range">{{ rangeLabel(row) }}</td>
                <td class="cell-muted">{{ row.doneCount }} / {{ row.questionCount }}</td>
                <td class="cell-muted">
                  <span class="jp-score-correct">{{ row.correctCount }}</span> /
                  <span class="jp-score-wrong">{{ row.wrongCount }}</span>
                </td>
                <td>
                  <span class="jp-mastery">
                    <span class="jp-mastery__bar">
                      <span class="jp-mastery__fill" :style="{ width: `${row.scorePercent}%` }" />
                    </span>
                    <span class="jp-mastery__value">{{ row.scorePercent }}%</span>
                  </span>
                </td>
                <td><span class="badge" :class="stateBadge(row.state)">{{ stateLabel(row.state) }}</span></td>
                <td class="cell-muted">{{ millisToMinutes(row.activeMs) }} 分</td>
                <td class="cell-muted">{{ row.lastStudiedAt === null ? '—' : formatIsoDateTime(row.lastStudiedAt) }}</td>
                <td class="row-actions">
                  <button
                    v-if="row.state !== 'COMPLETED'" type="button" class="btn btn--primary btn--sm"
                    :data-jp-test-start="row.testId" @click="startTest(row)"
                  >
                    <AppIcon name="play" size="sm" />
                    {{ row.state === 'RUNNING' ? '再開' : '開始' }}
                  </button>
                  <button
                    v-if="row.state === 'COMPLETED'" type="button" class="btn btn--secondary btn--sm"
                    :data-jp-test-result="row.testId" @click="startTest(row)"
                  >
                    <AppIcon name="eye" size="sm" /> 結果を見る
                  </button>
                  <button
                    type="button" class="btn btn--icon btn--sm is-danger" title="削除"
                    :data-jp-test-delete="row.testId" @click="removeTest(row)"
                  >
                    <AppIcon name="trash" size="sm" />
                  </button>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <div v-if="!loading && totalElements > 0" class="pagination">
          <span class="pagination__info">全 {{ totalElements }} 件（{{ page }} / {{ totalPages }} ページ）</span>
          <!-- 件数はページングの左隣に置く（画面共通のルール） -->
          <label class="pagination__size">
            <span>件数</span>
            <select v-model.number="pageSize" class="select" aria-label="1ページの件数" data-jp-page-size @change="changePageSize">
              <option v-for="size in PAGE_SIZES" :key="size" :value="size">{{ size }} 件</option>
            </select>
          </label>
          <div class="pagination__pages">
            <button type="button" class="page-btn" :disabled="page <= 1" @click="goto(page - 1)">‹</button>
            <template v-for="(item, key) in pageItems" :key="key">
              <span v-if="item === 'gap'" class="page-gap">…</span>
              <button
                v-else type="button" class="page-btn"
                :class="{ 'is-active': item === page }" @click="goto(item)"
              >
                {{ item }}
              </button>
            </template>
            <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goto(page + 1)">›</button>
          </div>
        </div>
      </div>
    </template>

    <!-- ==================== 出題モード ==================== -->
    <template v-else-if="mode === 'question' && current !== null">
      <div class="jp-test-layout" :class="{ 'is-focus': focusMode }" data-jp-test-layout>
        <aside class="jp-test-rail">
          <section class="card">
            <div class="card__header">
              <h2 class="card__title">出題</h2>
              <span class="cell-muted">{{ doneCount }} / {{ questionCount }}</span>
            </div>
            <div class="jp-question-rail" data-jp-question-rail>
              <button
                v-for="item in questions" :key="item.question.orderNo"
                type="button" class="jp-question-chip"
                :class="{
                  'is-active': item.question.orderNo === current.question.orderNo,
                  'is-correct': answered[item.question.orderNo]?.correct === true,
                  'is-wrong': answered[item.question.orderNo]?.correct === false
                }"
                :data-jp-question-chip="item.question.orderNo"
                :title="questionWordLabel(item)"
                @click="showQuestion(questions.indexOf(item))"
              >
                {{ item.question.orderNo }}
              </button>
            </div>
          </section>
        </aside>

        <main class="jp-test-main">
          <section class="card">
            <div class="card__header">
              <h2 class="card__title">{{ test === null ? '' : TEST_TYPE_LABELS[test.testType] }}</h2>
              <span class="card__sub">{{ test?.testNo }}</span>
              <div class="jp-list-head__spacer" />
              <span class="cell-muted" data-jp-progress>
                {{ doneCount }} / {{ questionCount }} ・ 正答率 {{ accuracyPercent }}%
              </span>
              <button type="button" class="btn btn--secondary btn--sm" data-jp-back-to-list @click="backToList">
                <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
              </button>
              <button
                type="button" class="btn btn--secondary btn--sm" data-jp-focus
                @click="focusMode = !focusMode"
              >
                <AppIcon name="eye" size="sm" /> {{ focusMode ? '通常表示' : '集中' }}
              </button>
            </div>

            <div class="card__body">
              <article class="jp-question" :data-jp-question="current.question.orderNo">
                <div class="jp-question__head">
                  <span class="jp-question__no">P.{{ current.question.orderNo }} / 全 {{ questionCount }} 問</span>
                  <span class="jp-question__type">{{ questionTypeLabel(current) }}</span>
                  <span v-if="test !== null" class="badge" :class="stateBadge(test.state)">{{ stateLabel(test.state) }}</span>
                </div>

                <p class="jp-question__target">{{ questionWordLabel(current) }}</p>
                <p class="jp-question__text">{{ current.question.questionText ?? '—' }}</p>

                <div class="jp-choices" data-jp-choices>
                  <button
                    v-for="(choice, choiceIndex) in current.choices" :key="choice.choiceId"
                    type="button" class="jp-choice"
                    :class="choiceClass(choice.choiceId)"
                    :data-jp-choice="choice.choiceId"
                    :disabled="isAnswered(current)"
                    @click="selectChoice(choice.choiceId)"
                  >
                    <span class="jp-choice__no">{{ choiceIndex + 1 }}</span>
                    <span class="jp-choice__body">
                      <span class="jp-choice__value">{{ choice.value }}</span>
                      <span v-if="choiceReading(choice.choiceId) !== ''" class="jp-choice__sub">
                        {{ choiceReading(choice.choiceId) }}
                      </span>
                    </span>
                  </button>
                </div>

                <div
                  v-if="answerOf(current) !== null" class="jp-judgment"
                  :class="answerOf(current)?.correct === true ? 'is-correct' : 'is-wrong'"
                  data-jp-judgment
                >
                  <div class="jp-judgment__head">
                    <span>{{ answerOf(current)?.correct === true ? '正解' : '不正解' }}</span>
                    <span class="jp-judgment__text">正解：{{ answerOf(current)?.correctValue === '' ? '—' : answerOf(current)?.correctValue }}</span>
                  </div>
                  <p v-if="answerOf(current)?.explanation !== ''" class="jp-judgment__text">
                    {{ answerOf(current)?.explanation }}
                  </p>
                </div>

                <div class="jp-test-actions">
                  <button
                    type="button" class="btn btn--secondary btn--sm" data-jp-prev
                    :disabled="index <= 0" @click="goPrev"
                  >
                    <AppIcon name="chevron-left" size="sm" /> 前へ
                  </button>
                  <button
                    type="button" class="btn btn--secondary btn--sm" data-jp-next
                    :disabled="index >= questions.length - 1" @click="goNext"
                  >
                    次へ <AppIcon name="chevron-right" size="sm" />
                  </button>
                  <button
                    type="button" class="btn btn--secondary btn--sm" data-jp-skip
                    :title="hasAnsweredQuestion() ? '未回答の問題へ進みます' : '未回答の問題へ進みます（中断はできません）'"
                    @click="skip"
                  >
                    スキップ
                  </button>
                  <button
                    v-if="!isAnswered(current)" type="button" class="btn btn--primary btn--sm"
                    data-jp-answer :disabled="busy" @click="submitAnswer"
                  >
                    <AppIcon name="check" size="sm" /> {{ busy ? '保存中...' : '回答する' }}
                  </button>
                  <span class="jp-test-actions__spacer" />
                  <button type="button" class="btn btn--secondary btn--sm" data-jp-suspend @click="suspend">
                    <AppIcon name="x" size="sm" /> 中断して一覧へ
                  </button>
                </div>
              </article>
            </div>
          </section>
        </main>
      </div>
    </template>

    <!-- ==================== 結果モード ==================== -->
    <template v-else-if="mode === 'result' && test !== null">
      <div class="jp-list-head">
        <h2 class="card__title">テストの結果（{{ test.testNo }}）</h2>
        <div class="jp-list-head__spacer" />
        <button type="button" class="btn btn--secondary btn--sm" data-jp-back-to-list @click="backToList">
          <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
        </button>
        <button v-if="test.state !== 'COMPLETED'" type="button" class="btn btn--primary btn--sm" data-jp-complete :disabled="busy" @click="completeTest">
          <AppIcon name="check" size="sm" /> 完了にする
        </button>
      </div>

      <section class="card">
        <div class="card__body">
          <div class="jp-summary jp-score" data-jp-score>
            <span class="jp-summary__item">
              <span class="jp-summary__label">正答率</span>
              <span class="jp-summary__value jp-score__value">{{ accuracyPercent }}<span class="jp-summary__unit">%</span></span>
            </span>
            <span class="jp-summary__item">
              <span class="jp-summary__label">正解</span>
              <span class="jp-summary__value">{{ scoreCounts.correct }}<span class="jp-summary__unit">問</span></span>
            </span>
            <span class="jp-summary__item">
              <span class="jp-summary__label">不正解</span>
              <span class="jp-summary__value">{{ scoreCounts.wrong }}<span class="jp-summary__unit">問</span></span>
            </span>
            <span class="jp-summary__item">
              <span class="jp-summary__label">学習時間</span>
              <span class="jp-summary__value">{{ millisToMinutes(test.activeMs) }}<span class="jp-summary__unit">分</span></span>
            </span>
            <span class="jp-summary__item">
              <span class="jp-summary__label">種別</span>
              <span class="jp-summary__value">{{ TEST_TYPE_LABELS[test.testType] }}</span>
            </span>
            <span class="jp-summary__item">
              <span class="jp-summary__label">範囲</span>
              <span class="jp-summary__value">{{ rangeLabel(test) }}</span>
            </span>
            <span class="jp-summary__item">
              <span class="jp-summary__label">終了日時</span>
              <span class="jp-summary__value">{{ finishedAtLabel }}</span>
            </span>
          </div>
        </div>
      </section>

      <section class="card">
        <div class="card__header">
          <h2 class="card__title"><AppIcon name="list" size="sm" /> 誤答一覧</h2>
          <span class="cell-muted">{{ wrongAnswers.length }} 件</span>
        </div>
        <div class="card__body">
          <p v-if="wrongAnswers.length === 0" class="jp-hint" data-jp-wrong-empty>
            誤答はありません。すべて正解です。
          </p>
          <div v-else class="jp-wrong-list" data-jp-wrong-list>
            <article
              v-for="row in wrongAnswers" :key="row.orderNo"
              class="jp-judgment is-wrong" :data-jp-wrong="row.orderNo"
            >
              <div class="jp-judgment__head">
                <span>P.{{ row.orderNo }}</span>
                <span>{{ row.word }}</span>
              </div>
              <p class="jp-judgment__text">{{ row.text === null ? '—' : row.text }}</p>
              <p class="jp-judgment__text">正解：{{ row.correctValue }}</p>
              <p class="jp-judgment__text">自分の回答：{{ row.chosenValue }}</p>
            </article>
          </div>
        </div>
      </section>

      <div class="jp-test-actions">
        <button type="button" class="btn btn--primary" data-jp-retry @click="retryTest">
          <AppIcon name="rotate" size="sm" /> もう一度（同じ条件で新しいテスト）
        </button>
        <button type="button" class="btn btn--secondary" data-jp-back-to-list @click="backToList">
          <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
        </button>
      </div>
    </template>

    <!-- ==================== 新規テスト作成ダイアログ ==================== -->
    <div v-if="createOpen" class="overlay">
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="jpCreateTitle" data-jp-create-dialog>
        <div class="dialog__head">
          <h2 id="jpCreateTitle" class="dialog__title"><AppIcon name="plus" size="sm" /> 新しい単語テスト</h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="closeCreate">
            <AppIcon name="x" size="sm" />
          </button>
        </div>
        <div class="dialog__body">
          <div class="jp-dialog-grid">
            <div class="field">
              <label class="field__label" for="jpTestType">テスト種別<span class="jp-required">必須</span></label>
              <select id="jpTestType" v-model="questionForm.testType" class="select">
                <option v-for="type in TEST_TYPE_OPTIONS" :key="type" :value="type">{{ TEST_TYPE_LABELS[type] }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="jpTestDifficulty">難易度</label>
              <select id="jpTestDifficulty" v-model="questionForm.difficulty" class="select">
                <option v-for="level in DIFFICULTY_OPTIONS" :key="level" :value="level">{{ level }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="jpTestLevel">レベル</label>
              <input id="jpTestLevel" v-model="questionForm.level" class="input" type="text" maxlength="50" placeholder="例: N3">
            </div>
            <div class="field">
              <label class="field__label" for="jpTestBook">書籍</label>
              <input id="jpTestBook" v-model="questionForm.book" class="input" type="text" maxlength="100" placeholder="例: 日本語総まとめ">
            </div>
            <div class="field">
              <label class="field__label" for="jpTestCategoryFrom">分類開始</label>
              <input id="jpTestCategoryFrom" v-model="questionForm.categoryFrom" class="input" type="text" maxlength="100">
            </div>
            <div class="field">
              <label class="field__label" for="jpTestCategoryTo">分類終了</label>
              <input id="jpTestCategoryTo" v-model="questionForm.categoryTo" class="input" type="text" maxlength="100">
            </div>
            <div class="field">
              <label class="field__label" for="jpTestMode">出題方式</label>
              <select id="jpTestMode" v-model="questionForm.mode" class="select">
                <option v-for="option in MODE_OPTIONS" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="jpTestCount">出題数<span class="jp-required">必須</span></label>
              <input
                id="jpTestCount" v-model.number="questionForm.questionCount" class="input" type="number"
                :min="MIN_QUESTION_COUNT" :max="MAX_QUESTION_COUNT"
              >
            </div>
          </div>
          <p class="jp-hint">
            条件に合う単語から出題を作ります。作成すると、そのまま出題画面を開きます。
          </p>
          <p v-if="createError" class="alert alert--danger" data-jp-create-error>{{ createError }}</p>
        </div>
        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" data-jp-create-cancel @click="closeCreate">キャンセル</button>
          <button type="button" class="btn btn--primary" data-jp-create-save :disabled="busy" @click="saveCreate">
            <AppIcon name="check" size="sm" /> {{ busy ? '作成中...' : '作成して開始' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<style scoped>
/* 履歴テーブルの列幅（範囲とテスト番号が潰れないようにする）。 */
.jp-test-table {
  table-layout: fixed;
  min-width: 1180px;
}

.jp-test-table .jp-col-range {
  width: 20%;
  white-space: normal;
  overflow-wrap: anywhere;
}

.jp-score-correct { color: var(--color-success, #2e7d5b); font-weight: var(--fw-semibold); }
.jp-score-wrong { color: var(--color-danger); font-weight: var(--fw-semibold); }

/* 結果モードのスコア（大きめに出す）。 */
.jp-score {
  gap: var(--sp-6);
}

.jp-score .jp-score__value {
  font-size: var(--fs-3xl, var(--fs-2xl));
}

/* 誤答一覧は縦に積む。 */
.jp-wrong-list {
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}
</style>

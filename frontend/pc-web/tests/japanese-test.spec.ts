import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import JapaneseTestView from '@/views/japanese/JapaneseTestView.vue'
import type {
  JpnTest,
  JpnTestDetail,
  JpnTestQuestion,
  JpnTestQuestionView,
  JpnTestTotals,
  TestType
} from '@/api/japanese'

/**
 * 日本語勉強【単語テスト】。
 *
 * 1 つの画面で 3 つのモード（履歴・出題・結果）を切り替える。
 * ここでは「どの API をどの条件で呼ぶか」と「画面に出る判定・スコア」を確かめる。
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function fail(message: string, code = 'VALIDATION_ERROR'): Response {
  return new Response(JSON.stringify({ success: false, code, message, timestamp: '' }), {
    status: 400,
    headers: { 'Content-Type': 'application/json' }
  })
}

function testRow(overrides: Partial<JpnTest> = {}): JpnTest {
  return {
    testId: 1,
    testNo: 'JP-20260810-090000',
    testType: 'C',
    level: 'N3',
    book: '日本語総まとめ',
    categoryFrom: '第1課',
    categoryTo: '第5課',
    difficulty: 'NORMAL',
    mode: 'ALL',
    questionCount: 4,
    doneCount: 3,
    correctCount: 3,
    wrongCount: 1,
    state: 'RUNNING',
    startedAt: '2026-08-10T09:00:00',
    finishedAt: null,
    lastStudiedAt: '2026-08-11T20:15:00',
    activeMs: 600000,
    scorePercent: 75,
    version: 2,
    ...overrides
  }
}

function choice(id: number, orderNo: number, value: string, correct = false, reading: string | null = null) {
  return { choiceId: id, orderNo, value, reading, correct, description: null }
}

function question(overrides: Partial<JpnTestQuestion> = {}): JpnTestQuestion {
  return {
    entryId: 900,
    orderNo: 1,
    state: 'PENDING',
    judgment: null,
    answerCount: 0,
    wrongCount: 0,
    activeMs: 0,
    answeredAt: null,
    questionId: 500,
    wordId: 10,
    word: '経験',
    reading: 'けいけん',
    questionType: 'C1_READING',
    questionText: '「経験」の読みを選んでください。',
    correctValue: 'けいけん',
    explanation: '「経験」は「けいけん」と読みます。',
    book: '日本語総まとめ',
    category: '第3課',
    ...overrides
  }
}

function view(questionOverrides: Partial<JpnTestQuestion> = {}, correctChoiceId = 101): JpnTestQuestionView {
  const item = question(questionOverrides)
  return {
    question: item,
    choices: [
      choice(correctChoiceId, 1, item.correctValue ?? 'けいけん', true, item.reading),
      choice(correctChoiceId + 1, 2, 'きょうけん', false, 'きょうけん'),
      choice(correctChoiceId + 2, 3, 'きょうこう', false, 'きょうこう')
    ]
  }
}

/** 未回答 2 問＋回答済み誤り 1 問。既定の問題セット。 */
function sessionQuestions(): JpnTestQuestionView[] {
  return [
    view(),
    view({ orderNo: 2, entryId: 901, word: '挑戦', reading: 'ちょうせん', questionText: '「挑戦」の読みを選んでください。', correctValue: 'ちょうせん', explanation: '「挑戦」は「ちょうせん」です。' }, 201),
    view(
      {
        orderNo: 3,
        entryId: 902,
        state: 'ANSWERED',
        judgment: 'WRONG',
        answerCount: 1,
        wrongCount: 1,
        word: '突然',
        reading: 'とつぜん',
        questionText: '「突然」の読みを選んでください。',
        correctValue: 'とつぜん',
        explanation: '「突然」は「とつぜん」です。',
        answeredAt: '2026-08-11T20:15:00'
      },
      301
    )
  ]
}

const TOTALS: JpnTestTotals = {
  testCount: 37,
  completedCount: 12,
  runningCount: 5,
  averageScore: 68,
  totalActiveMs: 3_720_000
}

type Call = { url: string; method: string; body: Record<string, unknown> | null }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      body: init.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
    }
  })
}

function queryOf(url: string): URLSearchParams {
  return new URLSearchParams(url.split('?')[1] ?? '')
}

function pageOf(rows: JpnTest[], params: URLSearchParams) {
  const size = Number(params.get('size') ?? '20')
  const page = Number(params.get('page') ?? '1')
  const totalPages = Math.max(1, Math.ceil(rows.length / size))
  return {
    items: rows.slice((page - 1) * size, page * size),
    totalElements: rows.length,
    page,
    size,
    totalPages,
    totals: TOTALS
  }
}

beforeEach(() => {
  useToast().items.splice(0)
  window.sessionStorage.clear()
  window.scrollTo = vi.fn()
})

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
})

async function setup(options: {
  rows?: JpnTest[]
  session?: JpnTestDetail
  answerFails?: boolean
  createFails?: boolean
} = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)

  const rows = options.rows ?? [
    testRow(),
    testRow({
      testId: 2, testNo: 'JP-20260809-120000', testType: 'A', level: null, book: null,
      categoryFrom: null, categoryTo: null, difficulty: 'EASY', mode: 'RANDOM',
      questionCount: 10, doneCount: 0, correctCount: 0, wrongCount: 0, state: 'CREATED',
      startedAt: null, lastStudiedAt: null, activeMs: 0, scorePercent: 0
    }),
    testRow({
      testId: 3, testNo: 'JP-20260801-080000', testType: 'D', level: 'N2', book: '新完全マスター',
      categoryFrom: '第7課', categoryTo: '第7課', questionCount: 30, doneCount: 30, correctCount: 24,
      wrongCount: 6, state: 'COMPLETED', finishedAt: '2026-08-01T09:30:00', activeMs: 1_800_000,
      scorePercent: 80
    })
  ]

  const detailFor = (testId: number): JpnTestDetail => {
    const base = rows.find((row) => row.testId === testId) ?? testRow({ testId })
    return { test: base, questions: sessionQuestions() }
  }
  const session = options.session

  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target = String(url)
    const method = (init?.method ?? 'GET').toUpperCase()
    const params = queryOf(target)
    const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : {}

    if (method === 'GET' && /\/japanese\/tests\/\d+$/.test(target)) {
      const testId = Number(target.split('/').pop())
      const detail = session ?? detailFor(testId)
      return ok(detail)
    }
    if (method === 'GET' && target.includes('/japanese/tests')) {
      return ok(pageOf(rows, params))
    }
    if (method === 'POST' && /\/japanese\/tests\/\d+\/answers$/.test(target)) {
      if (options.answerFails === true) return fail('回答を保存できませんでした。')
      const orderNo = Number(body.orderNo ?? 0)
      const choiceId = Number(body.choiceId ?? 0)
      // 正解の選択肢は各問の 1 番目（101/201/301…）に置いてある。
      const correct = choiceId % 100 === 1
      const current = (session ?? detailFor(1)).questions.find((item) => item.question.orderNo === orderNo)
      const base = (session ?? detailFor(1)).test
      return ok({
        correct,
        judgment: correct ? 'CORRECT' : 'WRONG',
        correctValue: current?.question.correctValue ?? 'けいけん',
        explanation: current?.question.explanation ?? '',
        test: {
          ...base,
          doneCount: orderNo,
          correctCount: correct ? orderNo : orderNo - 1,
          wrongCount: correct ? 0 : 1,
          activeMs: 300000
        },
        message: correct ? '正解です。' : '不正解です。'
      }, correct ? '正解です。' : '不正解です。')
    }
    if (method === 'POST' && /\/japanese\/tests\/\d+\/complete$/.test(target)) {
      const testId = Number(target.split('/').slice(-2)[0])
      return ok({
        test: testRow({
          testId, state: 'COMPLETED', finishedAt: '2026-08-12T21:00:00',
          doneCount: 3, correctCount: 2, wrongCount: 1, scorePercent: 67
        }),
        message: 'テストを完了にしました。'
      }, 'テストを完了にしました。')
    }
    if (method === 'POST' && target.endsWith('/japanese/tests')) {
      if (options.createFails === true) return fail('条件に合う単語がありません。')
      const created = testRow({
        testId: 77, testNo: 'JP-20260812-210000', testType: (body.testType as TestType) ?? 'A',
        level: (body.level as string | undefined) ?? null, book: (body.book as string | undefined) ?? null,
        categoryFrom: (body.categoryFrom as string | undefined) ?? null,
        categoryTo: (body.categoryTo as string | undefined) ?? null,
        difficulty: (body.difficulty as string | undefined) ?? 'NORMAL',
        mode: (body.mode as string | undefined) ?? 'ALL',
        questionCount: Number(body.questionCount ?? 10), doneCount: 0, correctCount: 0, wrongCount: 0,
        state: 'CREATED', startedAt: null, lastStudiedAt: null, activeMs: 0, scorePercent: 0
      })
      return ok({
        test: created,
        questions: [
          view({ orderNo: 1 }, 401),
          view({ orderNo: 2, entryId: 903, word: '準備', reading: 'じゅんび', correctValue: 'じゅんび' }, 501)
        ]
      }, 'テストを作成しました。')
    }
    if (method === 'DELETE' && /\/japanese\/tests\/\d+$/.test(target)) {
      return ok({ count: 1, message: 'テストを削除しました。' }, 'テストを削除しました。')
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(JapaneseTestView, { global: { plugins: [pinia] }, attachTo: document.body })
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock }
}

/** 出題モードで n 番目の選択肢（1 始まり）を選んで回答する。 */
async function answerChoice(wrapper: Awaited<ReturnType<typeof setup>>['wrapper'], choiceId: number): Promise<void> {
  await wrapper.get(`[data-jp-choice="${choiceId}"]`).trigger('click')
  await wrapper.get('[data-jp-answer]').trigger('click')
  await flushPromises()
}

describe('日本語勉強（単語テスト）— 履歴一覧モード', () => {
  it('サマリと履歴の一覧（種別・範囲・出題数・正答率・状態）を表示する', async () => {
    const { wrapper } = await setup()

    // 「テスト N 回 ・ 完了 N ・ 学習中 N ・ 平均正答率 X% ・ 総学習時間 M 分」
    const summary = wrapper.get('[data-jp-summary]').text().replace(/\s+/g, ' ')
    expect(summary).toContain('テスト 37 回')
    expect(summary).toContain('完了 12')
    expect(summary).toContain('学習中 5')
    expect(summary).toContain('平均正答率 68%')
    expect(summary).toContain('総学習時間 62 分')

    const rows = wrapper.findAll('[data-jp-test-row]')
    expect(rows.length).toBe(3)
    // 一覧の見出しには一覧アイコンを付ける（他の一覧画面と揃える）
    expect(wrapper.get('.table-section__title').get('use').attributes('href')).toBe('#i-list')

    const first = wrapper.get('[data-jp-test-row="1"]')
    expect(first.text()).toContain('JP-20260810-090000')
    expect(first.text()).toContain('C：漢字と読み')
    expect(first.text()).toContain('N3')
    expect(first.text()).toContain('日本語総まとめ')
    expect(first.text()).toContain('第1課〜第5課')
    expect(first.text()).toContain('3 / 4')
    expect(first.text()).toContain('75%')
    expect(first.text()).toContain('学習中')
    expect(first.text()).toContain('10 分')

    // レベル・書籍・分類が無い行は「—」
    expect(wrapper.get('[data-jp-test-row="2"]').text()).toContain('—')
  })

  it('状態と種別で絞り込んで検索できる（クエリに載る）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-test-state-filter]').setValue('COMPLETED')
    await wrapper.get('[data-jp-test-type-filter]').setValue('D')
    await wrapper.get('[data-jp-test-search]').trigger('click')
    await flushPromises()

    const listCalls = recorded(fetchMock).filter((call) => call.method === 'GET' && call.url.includes('/japanese/tests'))
    const last = queryOf(listCalls.at(-1)?.url ?? '')
    expect(last.get('state')).toBe('COMPLETED')
    expect(last.get('testType')).toBe('D')
    expect(last.get('page')).toBe('1')
    expect(last.get('size')).toBe('20')
  })

  it('リセットで絞り込みを外して全件を取り直す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-test-state-filter]').setValue('RUNNING')
    await wrapper.get('[data-jp-test-search]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-jp-test-reset]').trigger('click')
    await flushPromises()

    expect((wrapper.get('[data-jp-test-state-filter]').element as HTMLSelectElement).value).toBe('')
    const listCalls = recorded(fetchMock).filter((call) => call.method === 'GET' && call.url.includes('/japanese/tests'))
    const last = queryOf(listCalls.at(-1)?.url ?? '')
    expect(last.get('state')).toBeNull()
    expect(last.get('testType')).toBeNull()
  })

  it('1 ページの件数を変えると 1 ページ目から取り直す', async () => {
    const many = Array.from({ length: 45 }, (_, index) => testRow({ testId: index + 1, testNo: `JP-20260810-${index}` }))
    const { wrapper, fetchMock } = await setup({ rows: many })

    expect(wrapper.findAll('[data-jp-test-row]').length).toBe(20)
    await wrapper.get('[data-jp-page-size]').setValue('50')
    await flushPromises()

    expect(wrapper.findAll('[data-jp-test-row]').length).toBe(45)
    const listCalls = recorded(fetchMock).filter((call) => call.method === 'GET' && call.url.includes('/japanese/tests'))
    const last = queryOf(listCalls.at(-1)?.url ?? '')
    expect(last.get('size')).toBe('50')
    expect(last.get('page')).toBe('1')
  })

  it('ページ番号で次のページの履歴を読み込む', async () => {
    const many = Array.from({ length: 45 }, (_, index) => testRow({ testId: index + 1, testNo: `JP-20260810-${index}` }))
    const { wrapper, fetchMock } = await setup({ rows: many })

    // 45 件を 20 件ずつ → 3 ページ。2 ページ目へ移動する
    await wrapper.get('.pagination__pages .page-btn:nth-child(3)').trigger('click')
    await flushPromises()

    const listCalls = recorded(fetchMock).filter((call) => call.method === 'GET' && call.url.includes('/japanese/tests'))
    expect(queryOf(listCalls.at(-1)?.url ?? '').get('page')).toBe('2')
    expect(wrapper.findAll('[data-jp-test-row]').length).toBe(20)
    expect(wrapper.findAll('.pagination__pages .page-btn.is-active')[0]?.text()).toBe('2')
  })

  it('履歴が 0 件なら案内を出す', async () => {
    const { wrapper } = await setup({ rows: [] })

    expect(wrapper.get('[data-jp-test-empty]').text()).toContain('テストの履歴がありません。新しいテストを作成してください。')
    expect(wrapper.findAll('[data-jp-test-row]').length).toBe(0)
  })
})

describe('日本語勉強（単語テスト）— 履歴の操作', () => {
  it('新しいテストを作成できる（出題数などの条件が POST に載る）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-test-create]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-jp-create-dialog]').exists()).toBe(true)

    await wrapper.get('#jpTestType').setValue('B')
    await wrapper.get('#jpTestLevel').setValue('N4')
    await wrapper.get('#jpTestBook').setValue('日本語総まとめ')
    await wrapper.get('#jpTestCategoryFrom').setValue('第2課')
    await wrapper.get('#jpTestCategoryTo').setValue('第6課')
    await wrapper.get('#jpTestDifficulty').setValue('HARD')
    await wrapper.get('#jpTestMode').setValue('RANDOM')
    await wrapper.get('#jpTestCount').setValue(15)
    await wrapper.get('[data-jp-create-save]').trigger('click')
    await flushPromises()

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.endsWith('/api/user/japanese/tests'))
    expect(post?.body).toEqual({
      testType: 'B',
      level: 'N4',
      book: '日本語総まとめ',
      categoryFrom: '第2課',
      categoryTo: '第6課',
      difficulty: 'HARD',
      mode: 'RANDOM',
      questionCount: 15
    })
    // 作成したテストは、そのまま出題モードで開く
    expect(wrapper.find('[data-jp-create-dialog]').exists()).toBe(false)
    expect(wrapper.find('[data-jp-question-chip="1"]').exists()).toBe(true)
  })

  it('出題数が範囲外なら作成せずエラーを出す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-test-create]').trigger('click')
    await flushPromises()
    await wrapper.get('#jpTestCount').setValue(60)
    await wrapper.get('[data-jp-create-save]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some((call) => call.method === 'POST' && call.url.endsWith('/api/user/japanese/tests'))).toBe(false)
    expect(wrapper.get('[data-jp-create-error]').text()).toContain('出題数は 1〜50')
  })

  it('作成に失敗したらダイアログにエラーを出して閉じない', async () => {
    const { wrapper } = await setup({ createFails: true })

    await wrapper.get('[data-jp-test-create]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-jp-create-save]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-jp-create-dialog]').exists()).toBe(true)
    expect(wrapper.get('[data-jp-create-error]').text()).toContain('条件に合う単語がありません。')
  })

  it('キャンセルで作成ダイアログを閉じる（作成しない）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-test-create]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-jp-create-cancel]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-jp-create-dialog]').exists()).toBe(false)
    expect(recorded(fetchMock).some((call) => call.method === 'POST')).toBe(false)
  })

  it('RUNNING のテストは【再開】、COMPLETED は【結果を見る】を出す', async () => {
    const { wrapper, fetchMock } = await setup()

    const runningRow = wrapper.get('[data-jp-test-row="1"]')
    expect(runningRow.get('[data-jp-test-start="1"]').text()).toContain('再開')
    expect(wrapper.find('[data-jp-test-row="1"] [data-jp-test-result="1"]').exists()).toBe(false)

    const createdRow = wrapper.get('[data-jp-test-row="2"]')
    expect(createdRow.get('[data-jp-test-start="2"]').text()).toContain('開始')

    const completedRow = wrapper.get('[data-jp-test-row="3"]')
    expect(completedRow.get('[data-jp-test-result="3"]').text()).toContain('結果を見る')
    expect(wrapper.find('[data-jp-test-row="3"] [data-jp-test-start="3"]').exists()).toBe(false)

    // 結果を見る → テストを取得し、API の件数で結果を出す
    await completedRow.get('[data-jp-test-result="3"]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some((call) => call.method === 'GET' && call.url.endsWith('/api/user/japanese/tests/3'))).toBe(true)

    const score = wrapper.get('[data-jp-score]').text().replace(/\s+/g, ' ')
    expect(score).toContain('正答率80%')
    expect(score).toContain('正解24問')
    expect(score).toContain('不正解6問')
    expect(score).toContain('D：文脈の意味')
    expect(score).toContain('学習時間30分')
  })

  it('削除は確認してから行い、キャンセルなら削除しない', async () => {
    const { wrapper, fetchMock } = await setup()
    const confirmMock = vi.fn(() => true)
    vi.stubGlobal('confirm', confirmMock)

    await wrapper.get('[data-jp-test-delete="1"]').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    const deleted = recorded(fetchMock).filter((call) => call.method === 'DELETE')
    expect(deleted.length).toBe(1)
    expect(deleted[0]?.url).toBe('/api/user/japanese/tests/1')

    vi.stubGlobal('confirm', vi.fn(() => false))
    await wrapper.get('[data-jp-test-delete="1"]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).filter((call) => call.method === 'DELETE').length).toBe(1)
  })
})

describe('日本語勉強（単語テスト）— 出題モード', () => {
  it('【開始】でテストを取得し、1 問目を表示する', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-jp-question="1"]').exists()).toBe(true)
    expect(wrapper.get('.jp-question__no').text()).toContain('P.1 / 全 4 問')
    expect(wrapper.get('.jp-question__target').text()).toBe('経験（けいけん）')
    expect(wrapper.get('.jp-question__text').text()).toContain('「経験」の読みを選んでください。')
    expect(wrapper.findAll('[data-jp-choice]').length).toBe(3)
    expect(wrapper.get('[data-jp-progress]').text()).toContain('3 / 4')
    expect(wrapper.findAll('[data-jp-question-chip]').length).toBe(3)

    // 回答前は判定を出さない
    expect(wrapper.find('[data-jp-judgment]').exists()).toBe(false)
  })

  it('選択肢を選んで回答すると、選んだ内容が POST される', async () => {
    const { wrapper, fetchMock } = await setup()
    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    // 未選択では回答できない
    await wrapper.get('[data-jp-answer]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some((call) => call.method === 'POST' && call.url.includes('/answers'))).toBe(false)
    expect(useToast().items.map((item) => item.message)).toContain('選択肢を選んでください。')

    await wrapper.get('[data-jp-choice="102"]').trigger('click')
    expect(wrapper.get('[data-jp-choice="102"]').classes()).toContain('is-selected')
    await wrapper.get('[data-jp-answer]').trigger('click')
    await flushPromises()

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.includes('/answers'))
    expect(post?.url).toBe('/api/user/japanese/tests/1/answers')
    expect(post?.body).toMatchObject({ orderNo: 1, choiceId: 102 })
    expect(typeof post?.body?.elapsedMs).toBe('number')
  })

  it('回答の保存に失敗したら判定を出さず、もう一度答えられる', async () => {
    const { wrapper } = await setup({ answerFails: true })
    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    await answerChoice(wrapper, 101)

    expect(wrapper.find('[data-jp-judgment]').exists()).toBe(false)
    expect(useToast().items.map((item) => item.message)).toContain('回答を保存できませんでした。')
    expect(wrapper.find('[data-jp-answer]').exists()).toBe(true)
  })

  it('正解なら判定・正解値・解説を出し、選択肢は押せなくなる', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    await answerChoice(wrapper, 101)

    const judgment = wrapper.get('[data-jp-judgment]')
    expect(judgment.classes()).toContain('is-correct')
    expect(judgment.text()).toContain('正解')
    expect(judgment.text()).toContain('けいけん')
    expect(judgment.text()).toContain('「経験」は「けいけん」と読みます。')
    expect(wrapper.get('[data-jp-choice="101"]').classes()).toContain('is-correct')
    expect(wrapper.get('[data-jp-choice="101"]').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-jp-question-chip="1"]').classes()).toContain('is-correct')
  })

  it('不正解なら選んだ誤りに印を付け、正解と解説を出す', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    // 2 問目（正解は 201）
    await wrapper.get('[data-jp-question-chip="2"]').trigger('click')
    await answerChoice(wrapper, 203)

    const judgment = wrapper.get('[data-jp-judgment]')
    expect(judgment.classes()).toContain('is-wrong')
    expect(judgment.text()).toContain('不正解')
    expect(judgment.text()).toContain('ちょうせん')
    expect(judgment.text()).toContain('「挑戦」は「ちょうせん」です。')
    expect(wrapper.get('[data-jp-choice="201"]').classes()).toContain('is-correct')
    expect(wrapper.get('[data-jp-choice="203"]').classes()).toContain('is-wrong')
    expect(wrapper.get('[data-jp-question-chip="2"]').classes()).toContain('is-wrong')
  })

  it('【次へ】【前へ】で出題が切り替わり、レールのチップでも移動できる', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-jp-next]').trigger('click')
    expect(wrapper.get('.jp-question__no').text()).toContain('P.2 / 全 4 問')
    expect(wrapper.get('.jp-question__target').text()).toContain('挑戦')
    expect(wrapper.get('[data-jp-question-chip="2"]').classes()).toContain('is-active')

    await wrapper.get('[data-jp-prev]').trigger('click')
    expect(wrapper.get('.jp-question__no').text()).toContain('P.1 / 全 4 問')

    await wrapper.get('[data-jp-question-chip="3"]').trigger('click')
    expect(wrapper.get('.jp-question__no').text()).toContain('P.3 / 全 4 問')
    expect(wrapper.get('[data-jp-question-chip="3"]').classes()).toContain('is-active')
  })

  it('回答済みの問題は判定つきで開き直せる', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    // 3 問目は回答済み（誤答）
    await wrapper.get('[data-jp-question-chip="3"]').trigger('click')
    expect(wrapper.get('[data-jp-judgment]').classes()).toContain('is-wrong')
    expect(wrapper.find('[data-jp-answer]').exists()).toBe(false)
  })

  it('【集中】でレールを隠し、【スキップ】で未回答の問題へ進む', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-jp-test-layout]').classes()).not.toContain('is-focus')
    await wrapper.get('[data-jp-focus]').trigger('click')
    expect(wrapper.get('[data-jp-test-layout]').classes()).toContain('is-focus')

    // 2 問目は回答済み（3 問目）を飛ばして 2 問目へ
    await wrapper.get('[data-jp-skip]').trigger('click')
    expect(wrapper.get('.jp-question__no').text()).toContain('P.2 / 全 4 問')
  })

  it('表示してからの経過時間を elapsedMs として送る', async () => {
    vi.useFakeTimers()
    vi.setSystemTime(new Date('2026-08-12T09:00:00'))
    const { wrapper, fetchMock } = await setup()
    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    vi.setSystemTime(new Date('2026-08-12T09:00:07'))
    await answerChoice(wrapper, 101)

    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.includes('/answers'))
    expect(post?.body?.elapsedMs).toBe(7000)
  })
})

describe('日本語勉強（単語テスト）— 結果モード', () => {
  it('全問回答すると結果モードへ移り、スコアと誤答一覧を出す', async () => {
    const { wrapper, fetchMock } = await setup({ session: { test: testRow({ state: 'RUNNING' }), questions: [view(), view({ orderNo: 2, entryId: 901, word: '挑戦', reading: 'ちょうせん', correctValue: 'ちょうせん', explanation: '「挑戦」は「ちょうせん」です。' }, 201)] } })

    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    await answerChoice(wrapper, 101)
    expect(wrapper.find('[data-jp-score]').exists()).toBe(false)

    await wrapper.get('[data-jp-question-chip="2"]').trigger('click')
    await answerChoice(wrapper, 203)

    // 全問回答 → 自動で結果モード
    const score = wrapper.get('[data-jp-score]')
    expect(score.text().replace(/\s+/g, ' ')).toContain('正答率50%')
    expect(score.text().replace(/\s+/g, ' ')).toContain('学習時間5分')
    expect(score.text().replace(/\s+/g, ' ')).toContain('正解1問')
    expect(score.text().replace(/\s+/g, ' ')).toContain('不正解1問')
    expect(score.text().replace(/\s+/g, ' ')).toContain('C：漢字と読み')
    expect(score.text()).toContain('N3')

    const wrong = wrapper.get('[data-jp-wrong-list]')
    expect(wrong.findAll('[data-jp-wrong]').length).toBe(1)
    expect(wrong.get('[data-jp-wrong="2"]').classes()).toContain('is-wrong')
    expect(wrong.get('[data-jp-wrong="2"]').text()).toContain('挑戦（ちょうせん）')
    expect(wrong.get('[data-jp-wrong="2"]').text()).toContain('正解：ちょうせん')
    expect(wrong.get('[data-jp-wrong="2"]').text()).toContain('自分の回答：きょうこう')
    expect(recorded(fetchMock).filter((call) => call.method === 'POST' && call.url.includes('/answers')).length).toBe(2)
  })

  it('完了済みのテストの結果には【完了にする】を出さない', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-jp-test-result="3"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-jp-score]').exists()).toBe(true)
    expect(wrapper.find('[data-jp-complete]').exists()).toBe(false)
  })

  it('誤答がなければその旨を出す（全問正解）', async () => {
    const { wrapper } = await setup({
      session: { test: testRow({ state: 'RUNNING' }), questions: [view()] }
    })

    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()
    await answerChoice(wrapper, 101)

    expect(wrapper.get('[data-jp-score]').text().replace(/\s+/g, ' ')).toContain('正答率100%')
    expect(wrapper.get('[data-jp-wrong-empty]').text()).toContain('誤答はありません')
  })

  it('【もう一度】は同じ条件で作成ダイアログを開く', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-test-result="3"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-jp-retry]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-jp-create-dialog]').exists()).toBe(true)
    expect((wrapper.get('#jpTestType').element as HTMLSelectElement).value).toBe('D')
    expect((wrapper.get('#jpTestLevel').element as HTMLInputElement).value).toBe('N2')
    expect((wrapper.get('#jpTestBook').element as HTMLInputElement).value).toBe('新完全マスター')
    expect((wrapper.get('#jpTestCategoryFrom').element as HTMLInputElement).value).toBe('第7課')
    expect((wrapper.get('#jpTestCategoryTo').element as HTMLInputElement).value).toBe('第7課')
    expect((wrapper.get('#jpTestDifficulty').element as HTMLSelectElement).value).toBe('NORMAL')
    expect((wrapper.get('#jpTestMode').element as HTMLSelectElement).value).toBe('ALL')
    expect((wrapper.get('#jpTestCount').element as HTMLInputElement).value).toBe('30')

    // 作成すると、同じ条件が POST に載る
    await wrapper.get('[data-jp-create-save]').trigger('click')
    await flushPromises()
    const post = recorded(fetchMock).find((call) => call.method === 'POST' && call.url.endsWith('/api/user/japanese/tests'))
    expect(post?.body).toMatchObject({ testType: 'D', level: 'N2', book: '新完全マスター', categoryFrom: '第7課', categoryTo: '第7課', difficulty: 'NORMAL', mode: 'ALL', questionCount: 30 })
  })

  it('【完了にする】で完了 API を呼び、状態が変わる', async () => {
    const { wrapper, fetchMock } = await setup({ session: { test: testRow({ state: 'RUNNING' }), questions: [view()] } })

    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()
    await answerChoice(wrapper, 101)

    expect(wrapper.find('[data-jp-complete]').exists()).toBe(true)
    await wrapper.get('[data-jp-complete]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some((call) => call.method === 'POST' && call.url === '/api/user/japanese/tests/1/complete')).toBe(true)
    expect(useToast().items.map((item) => item.message)).toContain('テストを完了にしました。')
    // 完了後は完了ボタンを出さない
    expect(wrapper.find('[data-jp-complete]').exists()).toBe(false)
  })

  it('【一覧へ戻る】で履歴を取り直して一覧に戻る', async () => {
    const { wrapper, fetchMock } = await setup()
    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()

    const callsBefore = recorded(fetchMock).filter((call) => call.method === 'GET' && call.url.includes('/japanese/tests')).length
    await wrapper.get('[data-jp-back-to-list]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-jp-question]').exists()).toBe(false)
    expect(wrapper.find('[data-jp-tests]').exists()).toBe(true)
    const callsAfter = recorded(fetchMock).filter((call) => call.method === 'GET' && call.url.includes('/japanese/tests')).length
    expect(callsAfter).toBe(callsBefore + 1)
  })

  it('【中断して一覧へ】は確認してから一覧へ戻る（回答は保存済みで、状態は学習中のまま）', async () => {
    const { wrapper, fetchMock } = await setup()
    const confirmMock = vi.fn(() => true)
    vi.stubGlobal('confirm', confirmMock)

    await wrapper.get('[data-jp-test-start="1"]').trigger('click')
    await flushPromises()
    // 3 問目（回答済み）だけでは保存対象が無いので、1 問目に答えてから中断する
    await answerChoice(wrapper, 101)

    // キャンセルなら中断しない
    vi.stubGlobal('confirm', vi.fn(() => false))
    await wrapper.get('[data-jp-suspend]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some((call) => call.method === 'POST' && call.url === '/api/user/japanese/tests/1/complete')).toBe(false)
    expect(wrapper.find('[data-jp-question]').exists()).toBe(true)

    vi.stubGlobal('confirm', confirmMock)
    await wrapper.get('[data-jp-suspend]').trigger('click')
    await flushPromises()

    expect(confirmMock).toHaveBeenCalled()
    // 回答は 1 問ごとに保存されているので、中断では完了にしない（あとで再開できる）
    expect(recorded(fetchMock).some((call) => call.method === 'POST' && call.url === '/api/user/japanese/tests/1/complete')).toBe(false)
    expect(await wrapper.find('[data-jp-tests]').exists()).toBe(true)
    expect(wrapper.find('[data-jp-tests]').exists()).toBe(true)
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick, defineComponent } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { useToast } from '@study21/web-shared'
import EssayDetailView from '@/views/english/EssayDetailView.vue'
import { resetEssays } from '@/features/english-essay/store'

/**
 * 英作文AI添削【詳細】（`/admin|/student|/parent/english-essay/:essayId`）。
 *
 * <p>API は `fetch` でスタブする（`tests/japanese-word.spec.ts` と同じ作り）。作文は user-api、
 * 添削は admin-api。見張る接縫は**画面の公開の振る舞い**だけ:</p>
 *
 * <ul>
 *   <li>新形式の `report`（`rubric` は観点名・点数は `rubricValues`・講評は `rubricNotes`）で
 *       4 観点・得点・修正ポイント・改善後の作文例が出る</li>
 *   <li>添削の回は `[data-ee-round]` の**タブ**（`role="tablist"` ＋ `role="tab"` ＋ `aria-selected`）で
 *       切り替える。既定は**最新**の回で、失敗・実行中の回も選べる（タブの印と `title` に日時・得点）</li>
 *   <li>登録画像の実体が読めない（`img` の `error`）ときは、その 1 枚だけ
 *       `[data-ee-image-missing]` に変えて、その画像の拡大を押せなくする</li>
 *   <li>結果の言語（`data-ee-lang`）で日本語と中国語が入れ替わる</li>
 *   <li>古い形式（2.0 の移行データ。4 観点が無い）の回でも壊れない（見出しだけ残さない）</li>
 *   <li><b>非同期</b>: 【この内容でAI添削】は**受付**だけ。`QUEUED`／`RUNNING` のうちは 3 秒ごとに
 *       **静かに**読み直し（`loading` を立てない・要素を作り直さない＝ちらつかない）、
 *       終わったら知らせる</li>
 *   <li>親エリアは閲覧だけ（添削のボタンを出さない）／「デモ」の語を出さない</li>
 * </ul>
 */

/* ---------- 偽のサーバー（DB の代わり） ---------- */

const QUESTION = 'Some people say that students should learn English in elementary school. Do you agree with this opinion? Give two reasons to support your answer.'
const ESSAY = 'I think that students should learn English in elementary school. Because English is very useful for our future.'

interface FakeImage {
  imageId: string
  order: number
  category: string
  originalFileName: string
  mimeType: string
  fileSize: number
  recognizedText: string | null
  confidence: number | null
}

interface FakeGrading {
  gradingId: string
  round: number
  statusCode: string
  level: string
  titleJa: string | null
  titleZh: string | null
  questionText: string | null
  essayText: string | null
  wordCount: number | null
  score: number | null
  maxScore: number | null
  report: unknown
  failureReason: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

interface FakeEssay {
  essayId: string
  level: string
  title: string
  titleZh: string
  questionText: string
  essayText: string
  wordCount: number
  stateCode: string
  createdAt: string
  updatedAt: string
  images: FakeImage[]
  gradings: FakeGrading[]
}

let db: FakeEssay[] = []
let detailCalls = 0
let acceptCalls = 0

/** 新形式のレポート（2.0 の移行データと同じ形）。 */
function newReport(round: number): Record<string, unknown> {
  return {
    status: 'AI_GRADED',
    version: 'eiken-ai-v1',
    level: 'GRADE1',
    score: 20 + round,
    maxScore: 32,
    rubricMax: 8,
    rubricValues: [6 + (round > 1 ? 1 : 0), 5, 5, 5],
    modelAnswer: `In conclusion, learning English opens many opportunities for our future. (${round})`,
    wordRequirement: '200〜240語',
    taskRequirements: ['設問の問いに答える', '理由を 2 つ以上挙げる', '結論を述べる'],
    warnings: [],
    japanese: {
      title: `前回の指摘が活きています（${round}）`,
      summary: `第 ${round} 回の添削では、前回の指摘が改善されているかを確認しました。`,
      tags: ['設問適合', '理由の充実', '接続表現'],
      rubric: ['内容', '構成', '語彙', '文法'],
      rubricNotes: [`内容の講評（${round}）`, '構成の講評', '語彙の講評', '文法の講評'],
      corrections: [['I think', 'I believe', '表現', '繰り返しを避けます。']],
      advice: '結論の前に理由を 2 回示すと評価が上がります。',
      notice: '本結果は公式採点ではありません。'
    },
    chinese: {
      title: `上次指出的问题已改善（${round}）`,
      summary: `第 ${round} 次批改重点确认了上次指出的问题是否已改善。`,
      tags: ['切题', '理由充分', '连接词'],
      rubric: ['内容', '结构', '词汇', '语法'],
      rubricNotes: [`内容的评语（${round}）`, '结构的评语', '词汇的评语', '语法的评语'],
      corrections: [['I think', 'I believe', '表达', '避免重复。']],
      advice: '结论前用两次理由更容易体现数量。',
      notice: '本结果并非官方评分。'
    }
  }
}

/** 古い形式のレポート（ほとんど入っていない。4 観点が無い）。 */
const OLD_REPORT = {
  status: 'AI_GRADED',
  version: 'eiken-ai-v1',
  level: 'GRADE1',
  japanese: {
    title: '主張は伝わります',
    summary: '内容・構成・語彙・文法の 4 観点で評価しました。',
    advice: '理由をもう 1 つ足しましょう。'
  },
  chinese: {
    title: '主张清楚',
    summary: '按四项观点进行了评价。'
  }
}

/** 添削 1 回。 */
function grading(round: number, overrides: Partial<FakeGrading> = {}): FakeGrading {
  return {
    gradingId: `${100 + round}`,
    round,
    statusCode: 'SUCCEEDED',
    level: 'GRADE1',
    titleJa: '部活動の時間を増やすべきか',
    titleZh: '是否应该增加社团活动时间',
    questionText: QUESTION,
    essayText: ESSAY,
    wordCount: 47,
    score: 20 + round,
    maxScore: 32,
    report: newReport(round),
    failureReason: null,
    startedAt: null,
    finishedAt: null,
    createdAt: `2026-09-2${round}T20:30:00.000Z`,
    ...overrides
  }
}

function image(imageId: string, category: string, order: number, originalFileName: string): FakeImage {
  return {
    imageId, order, category, originalFileName,
    mimeType: 'image/png', fileSize: 1024, recognizedText: null, confidence: null
  }
}

/** DB の初期状態（3 回添削ずみ・未添削・古い形式の 3 篇）。 */
function seedDb(): FakeEssay[] {
  return [
    {
      essayId: 'essay-3',
      level: 'GRADE1',
      title: '部活動の時間を増やすべきか',
      titleZh: '是否应该增加社团活动时间',
      questionText: QUESTION,
      essayText: ESSAY,
      wordCount: 47,
      stateCode: 'A',
      createdAt: '2026-09-24T18:05:00.000Z',
      updatedAt: '2026-09-26T20:30:00.000Z',
      images: [
        image('31', 'question', 1, 'essay-3-q1.png'),
        image('32', 'answer', 2, 'essay-3-a1.png'),
        image('33', 'answer', 3, 'essay-3-a2.png')
      ],
      gradings: [grading(1), grading(2), grading(3)]
    },
    {
      essayId: 'essay-4',
      level: 'PRE1',
      title: 'AI と教育（未添削）',
      titleZh: 'AI 与教育（未批改）',
      questionText: QUESTION,
      essayText: '',
      wordCount: 0,
      stateCode: 'A',
      createdAt: '2026-09-26T08:40:00.000Z',
      updatedAt: '2026-09-26T08:40:00.000Z',
      images: [image('41', 'question', 1, 'essay-4-q1.png')],
      gradings: []
    },
    {
      essayId: 'essay-7',
      level: 'GRADE1',
      title: '移行データの英作文',
      titleZh: '迁移数据的英语作文',
      questionText: QUESTION,
      essayText: ESSAY,
      wordCount: 47,
      stateCode: 'A',
      createdAt: '2026-09-10T08:00:00.000Z',
      updatedAt: '2026-09-11T08:00:00.000Z',
      images: [image('71', 'question', 1, 'essay-7-q1.png')],
      // 1 回目は新形式、2 回目（最新）が古い形式
      gradings: [
        grading(1),
        grading(2, { report: OLD_REPORT, score: 18, maxScore: null, titleJa: '移行データの英作文' })
      ]
    },
    {
      essayId: 'essay-8',
      level: 'GRADE1',
      title: '第 1 回が失敗した英作文',
      titleZh: '第 1 次失败的英语作文',
      questionText: QUESTION,
      essayText: ESSAY,
      wordCount: 47,
      stateCode: 'A',
      createdAt: '2026-09-27T05:00:00.000Z',
      updatedAt: '2026-09-27T05:30:00.000Z',
      images: [],
      // 第 1 回は失敗（行は残る）、第 2 回は成功。画面の「第 N 回」は API の round と一致させる
      gradings: [
        grading(1, {
          gradingId: '81',
          statusCode: 'FAILED',
          score: null,
          maxScore: null,
          report: null,
          failureReason: 'AI が混み合っています。時間をおいて、もう一度お試しください。',
          createdAt: '2026-09-27T05:05:00.000Z'
        }),
        grading(2, { gradingId: '82', createdAt: '2026-09-27T05:30:00.000Z' })
      ]
    }
  ]
}

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function notFound(): Response {
  return new Response(JSON.stringify({ success: false, code: 'NOT_FOUND', message: '英作文がありません', data: null, timestamp: '' }), {
    status: 404,
    headers: { 'Content-Type': 'application/json' }
  })
}

/** 偽のサーバー（未定義の呼び出しは失敗させる）。 */
function serverFetch(url: string, method: string, init: RequestInit): Response | undefined {
  const path = new URL(url, 'http://localhost').pathname
  if (method === 'GET' && path.startsWith('/api/user/english-essays/')) {
    detailCalls += 1
    const essayId = decodeURIComponent(path.slice('/api/user/english-essays/'.length))
    const found = db.find((item) => item.essayId === essayId)
    return found ? ok(found) : notFound()
  }
  const accepted = /^\/api\/user\/english-essays\/([^/]+)\/gradings$/.exec(path)
  if (method === 'POST' && accepted) {
    acceptCalls += 1
    const essay = db.find((item) => item.essayId === decodeURIComponent(accepted[1]!))
    if (essay === undefined) {
      // 削除済み・他人の作文（admin-api は呼ばない）
      return notFound()
    }
    const body = JSON.parse(String(init.body)) as { round?: number }
    const round = body.round ?? essay.gradings.length + 1
    essay.gradings.push(grading(round, { statusCode: 'QUEUED', score: null, maxScore: null, report: null }))
    return ok({ gradingId: `grading-${round}`, round, message: '受付けました' })
  }
  return undefined
}

function installServer(): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = (init?.method ?? 'GET').toUpperCase()
    const response = serverFetch(url, method, init ?? {})
    if (response) {
      return response
    }
    throw new Error(`未定義の呼び出し: ${method} ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

/** 添削を成功させる（様子見の途中で DB が変わることを表す）。 */
function finishGrading(essayId: string, round: number): void {
  db = db.map((essay) => essay.essayId === essayId
    ? { ...essay, gradings: essay.gradings.map((item) => (item.round === round ? grading(round) : item)) }
    : essay)
}

/* ---------- 画面の組み立て ---------- */

const Dummy = defineComponent({ name: 'Dummy', render: () => null })

function essayRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Dummy },
      { path: '/:area/english-essay', component: Dummy },
      { path: '/:area/english-essay/:essayId', component: EssayDetailView }
    ]
  })
}

async function open(essayId: string, area = 'admin'): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = essayRouter()
  await router.push(`/${area}/english-essay/${essayId}`)
  await router.isReady()
  const wrapper = mount(EssayDetailView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

/** 空白の差で落ちないようにそろえる（長い英文をそのまま比べるため）。 */
function normalize(text: string): string {
  return text.replace(/\s+/g, ' ').trim()
}

/** 画面に出ている得点（`score / maxScore` の前の数）。 */
function shownScore(wrapper: VueWrapper): number {
  const match = /(\d+)\s*\/\s*(\d+)/.exec(wrapper.get('[data-ee-score]').text())
  return match ? Number(match[1]) : Number.NaN
}

/** 選ばれている回のタブ（`data-ee-round-tab` の値＝API の `round`）。 */
function activeRound(wrapper: VueWrapper): string {
  return wrapper.get('[data-ee-round] .is-active').attributes('data-ee-round-tab') ?? ''
}

/** その回のタブの `title`（旧・セレクトの選択肢の全文。日時と得点が入る）。 */
function roundTitle(wrapper: VueWrapper, round: number): string {
  return wrapper.get(`[data-ee-round-tab="${round}"]`).attributes('title') ?? ''
}

/** 添削の回のタブを押して切り替える（`data-ee-round-tab="N"`）。 */
async function selectRound(wrapper: VueWrapper, round: number): Promise<void> {
  await wrapper.get(`[data-ee-round-tab="${round}"]`).trigger('click')
  await flushPromises()
}

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

async function pressEscape(): Promise<void> {
  window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
  await nextTick()
}

describe('英作文AI添削の詳細', () => {
  beforeEach(() => {
    db = seedDb()
    detailCalls = 0
    acceptCalls = 0
    resetEssays()
    useToast().items.splice(0)
    installServer()
  })

  it('3 回添削ずみの作文は、**最新の回**のレポートを出す', async () => {
    const { wrapper } = await open('essay-3')
    const latest = db.find((essay) => essay.essayId === 'essay-3')!.gradings[2]!
    const report = latest.report as Record<string, unknown>
    const japanese = report.japanese as Record<string, unknown>

    // 1. 得点（score / maxScore）と級名
    expect(normalize(wrapper.get('[data-ee-score]').text())).toBe(`${latest.score} / ${latest.maxScore}`)
    expect(wrapper.text()).toContain('英検1級')

    // 2. 見出し・要約・タグ 3 件
    expect(wrapper.text()).toContain('EIKEN WRITING FEEDBACK')
    expect(normalize(wrapper.get('[data-ee-summary]').text())).toBe(normalize(String(japanese.summary)))
    expect(wrapper.findAll('.ee-tag')).toHaveLength(3)

    // 3. 4 観点（観点名 ＋ 得点 ＋ meter ＋ 講評）。点数は rubricValues、講評は rubricNotes から
    const rubric = wrapper.findAll('[data-ee-rubric]')
    expect(rubric).toHaveLength(4)
    expect(rubric[0]!.text()).toContain('内容')
    expect(rubric[0]!.text()).toContain('7 / 8')
    expect(rubric[0]!.get('meter').attributes('max')).toBe('8')
    expect(rubric[0]!.text()).toContain('内容の講評（3）')

    // 4. 修正ポイント（原文 → 修正後）と改善後の作文例・語数の目安
    const corrections = wrapper.findAll('[data-ee-correction]')
    expect(corrections.length).toBeGreaterThanOrEqual(1)
    expect(corrections[0]!.get('del').text()).toBe('I think')
    expect(corrections[0]!.get('ins').text()).toBe('I believe')
    expect(corrections[0]!.text()).toContain('表現')
    expect(normalize(wrapper.get('[data-ee-model]').text())).toBe(normalize(String(report.modelAnswer)))
    expect(wrapper.text()).toContain('200〜240語')

    // 5. ワンポイントと注意書き
    expect(wrapper.text()).toContain(String(japanese.advice))
    expect(wrapper.text()).toContain(String(japanese.notice))
  })

  it('添削の回を切り替えると、レポートの中身がその回に入れ替わる', async () => {
    const { wrapper } = await open('essay-3')
    const essay = db.find((item) => item.essayId === 'essay-3')!
    const first = essay.gradings[0]!
    const latest = essay.gradings[2]!
    const firstReport = first.report as Record<string, unknown>
    const firstJapanese = firstReport.japanese as Record<string, unknown>

    // 既定は最新（第 3 回）
    expect(activeRound(wrapper)).toBe('3')
    expect(wrapper.findAll('[data-ee-round-tab]')).toHaveLength(3)
    expect(wrapper.get('[data-ee-round-tab="3"]').text()).toContain('第 3 回')
    expect(roundTitle(wrapper, 3)).toContain(`得点 ${latest.score} / ${latest.maxScore}`)
    const latestScore = shownScore(wrapper)

    await selectRound(wrapper, 1)

    // 得点・要約・修正ポイント・観点の講評が、すべて第 1 回のものに入れ替わる
    expect(shownScore(wrapper)).toBe(first.score)
    expect(shownScore(wrapper)).toBeLessThan(latestScore)
    expect(normalize(wrapper.get('[data-ee-summary]').text())).toBe(normalize(String(firstJapanese.summary)))
    expect(wrapper.get('[data-ee-correction]').get('del').text()).toBe('I think')
    expect(wrapper.get('[data-ee-rubric]').text()).toContain('内容の講評（1）')
    expect(normalize(wrapper.get('[data-ee-model]').text())).toBe(normalize(String(firstReport.modelAnswer)))
  })

  it('途中に失敗した回があっても、「第 N 回」は API の round と一致する', async () => {
    const { wrapper } = await open('essay-8')
    const essay = db.find((item) => item.essayId === 'essay-8')!
    const failed = essay.gradings[0]!
    const succeeded = essay.gradings[1]!
    const report = succeeded.report as Record<string, unknown>
    const japanese = report.japanese as Record<string, unknown>

    // タブは失敗した回も出す。番号は成功した回を数えず、API の round をそのまま使う
    const tabs = wrapper.findAll('[data-ee-round-tab]')
    expect(tabs.map((tab) => tab.attributes('data-ee-round-tab'))).toEqual(['1', '2'])
    expect(tabs[0]!.text()).toContain(`第 ${failed.round} 回`)
    expect(tabs[0]!.text()).toContain('失敗')
    expect(tabs[1]!.text()).toContain(`第 ${succeeded.round} 回`)
    expect(roundTitle(wrapper, 2)).toContain(`得点 ${succeeded.score}`)

    // 既定は最新の第 2 回（成功）。成功した回だけを数えて「第 1 回」にしない
    expect(activeRound(wrapper)).toBe('2')
    expect(wrapper.get('[data-ee-round] .is-active').text()).toContain(`第 ${succeeded.round} 回`)
    expect(normalize(wrapper.get('[data-ee-score]').text())).toBe(`${succeeded.score} / 32`)
    expect(wrapper.findAll('[data-ee-rubric]')).toHaveLength(4)
    expect(normalize(wrapper.get('[data-ee-summary]').text())).toBe(normalize(String(japanese.summary)))
  })

  it('失敗した回を選ぶと、失敗の理由を出して 4 観点は出さない', async () => {
    const { wrapper } = await open('essay-8')
    const failed = db.find((item) => item.essayId === 'essay-8')!.gradings[0]!

    await selectRound(wrapper, 1)

    const notice = wrapper.get('[data-ee-grade-failed]')
    expect(notice.text()).toContain('この回の添削は失敗しました')
    expect(notice.text()).toContain(String(failed.failureReason))
    expect(notice.text()).toContain('もう一度AI添削でやり直せます')

    // レポートのデータが無いので、4 観点などは見出しごと出さない
    expect(wrapper.findAll('[data-ee-rubric]')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('4 観点の評価')
    expect(wrapper.findAll('[data-ee-task-requirement]')).toHaveLength(0)
    expect(wrapper.find('[data-ee-model]').exists()).toBe(false)
    expect(wrapper.find('[data-ee-summary]').exists()).toBe(false)
    expect(wrapper.find('[data-ee-score]').exists()).toBe(false)

    // 成功した回を選び直せば、これまでどおりレポートが出る
    await selectRound(wrapper, 2)
    expect(wrapper.find('[data-ee-grade-failed]').exists()).toBe(false)
    expect(wrapper.findAll('[data-ee-rubric]')).toHaveLength(4)
    expect(wrapper.find('[data-ee-score]').exists()).toBe(true)

    // 親エリアはやり直せない（【もう一度AI添削】が無い）ので、その案内も出さない
    const parent = await open('essay-8', 'parent')
    await selectRound(parent.wrapper, 1)
    const parentNotice = parent.wrapper.get('[data-ee-grade-failed]')
    expect(parentNotice.text()).toContain('この回の添削は失敗しました')
    expect(parentNotice.text()).not.toContain('もう一度AI添削でやり直せます')
    expect(parent.wrapper.find('[data-ee-regrade]').exists()).toBe(false)
  })

  it('満点が分からない回は点数だけを出し、/ 0 や / undefined を出さない', async () => {
    const { wrapper } = await open('essay-7')
    const essay = db.find((item) => item.essayId === 'essay-7')!
    const legacy = essay.gradings[1]!

    // 既定は最新の第 2 回（古い形式。満点が無い）
    expect(activeRound(wrapper)).toBe('2')
    expect(normalize(wrapper.get('[data-ee-score]').text())).toBe(String(legacy.score))
    expect(wrapper.get('[data-ee-score]').text()).not.toContain('/')

    const title = roundTitle(wrapper, 2)
    expect(title).toContain(`得点 ${legacy.score}`)
    expect(title).not.toContain(`得点 ${legacy.score} /`)

    // 「不明」を 0 で表さない（点数だけを見せる）
    expect(wrapper.text()).not.toContain('/ 0')
    expect(wrapper.text()).not.toContain('/ undefined')
  })

  it('添削の回は、タブ（role="tablist"）で選ばせて、選択中を 1 つだけ示す', async () => {
    const { wrapper } = await open('essay-3')

    const bar = wrapper.get('[data-ee-round]')
    expect(bar.attributes('role')).toBe('tablist')
    expect(bar.attributes('aria-label')).toBe('添削の回')

    const tabs = wrapper.findAll('[data-ee-round-tab]')
    expect(tabs).toHaveLength(3)
    expect(tabs.map((tab) => tab.attributes('role'))).toEqual(['tab', 'tab', 'tab'])
    // タブの値は API の round をそのまま使う
    expect(tabs.map((tab) => tab.attributes('data-ee-round-tab'))).toEqual(['1', '2', '3'])

    // 既定は最新（第 3 回）だけが選択中
    expect(tabs.map((tab) => tab.attributes('aria-selected'))).toEqual(['false', 'false', 'true'])
    expect(tabs[2]!.classes()).toContain('is-active')
    expect(tabs[0]!.classes()).not.toContain('is-active')
    expect(wrapper.findAll('[data-ee-round] .is-active')).toHaveLength(1)

    // ドロップダウンは残さない（2.0 の英作文は select だったが、利用者の指示でタブにする）
    expect(bar.find('select').exists()).toBe(false)
    expect(wrapper.find('select').exists()).toBe(false)
  })

  it('タブを押すと、その回へ切り替わり、選択中の印も移る', async () => {
    const { wrapper } = await open('essay-3')
    const first = db.find((item) => item.essayId === 'essay-3')!.gradings[0]!

    await wrapper.get('[data-ee-round-tab="1"]').trigger('click')
    await flushPromises()

    expect(activeRound(wrapper)).toBe('1')
    expect(wrapper.findAll('[data-ee-round] .is-active')).toHaveLength(1)
    expect(wrapper.get('[data-ee-round-tab="1"]').attributes('aria-selected')).toBe('true')
    expect(wrapper.get('[data-ee-round-tab="1"]').classes()).toContain('is-active')
    expect(wrapper.get('[data-ee-round-tab="3"]').attributes('aria-selected')).toBe('false')
    expect(wrapper.get('[data-ee-round-tab="3"]').classes()).not.toContain('is-active')
    // レポートの中身も、その回のものに入れ替わる
    expect(shownScore(wrapper)).toBe(first.score)
  })

  it('回のタブのバーには、「全 N 回」と【もう一度AI添削】を残す', async () => {
    const { wrapper } = await open('essay-3')

    const bar = wrapper.get('[data-ee-round-bar]')
    expect(bar.get('[data-ee-round-count]').text()).toBe('全 3 回')
    expect(bar.get('[data-ee-regrade]').text()).toContain('もう一度AI添削')
    // 2.1 で足した部分（回のタブ）は、そのバーの中に置く
    expect(bar.find('[data-ee-round]').exists()).toBe(true)
  })

  it('各タブの title に、日時と得点（失敗した回は状態）を出す', async () => {
    const { wrapper } = await open('essay-8')
    const essay = db.find((item) => item.essayId === 'essay-8')!
    const failed = essay.gradings[0]!
    const succeeded = essay.gradings[1]!

    // 成功した回は「第 N 回（YYYY/MM/DD HH:mm・得点 22 / 32）」
    expect(roundTitle(wrapper, succeeded.round))
      .toMatch(new RegExp(`^第 ${succeeded.round} 回（\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}・得点 ${succeeded.score} / ${succeeded.maxScore}）$`))
    // 失敗した回は、得点の代わりに状態を出す
    expect(roundTitle(wrapper, failed.round))
      .toMatch(new RegExp(`^第 ${failed.round} 回（\\d{4}/\\d{2}/\\d{2} \\d{2}:\\d{2}・失敗）$`))
    // 一覧の見た目は「第 N 回」＋印だけ（全文は title）
    expect(wrapper.get(`[data-ee-round-tab="${failed.round}"]`).text()).toBe(`第 ${failed.round} 回・失敗`)
  })

  it('実行中の回のタブには、「実行中」の印を出す', async () => {
    vi.useFakeTimers()
    try {
      db = db.map((essay) => essay.essayId === 'essay-3'
        ? {
            ...essay,
            gradings: [
              ...essay.gradings.slice(0, 2),
              grading(3, { statusCode: 'RUNNING', score: null, maxScore: null, report: null })
            ]
          }
        : essay)
      const { wrapper } = await open('essay-3')

      const running = wrapper.get('[data-ee-round-tab="3"]')
      expect(running.text()).toBe('第 3 回・実行中')
      expect(running.attributes('title')).toContain('・実行中')
      expect(running.attributes('title')).not.toContain('得点')
      // 実行中の回も選べる（既定は最新なので、その回が選択中）
      expect(activeRound(wrapper)).toBe('3')
      expect(running.attributes('aria-selected')).toBe('true')

      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('結果の言語を切り替えると、日本語と中国語が入れ替わる', async () => {
    const { wrapper } = await open('essay-3')
    const latest = db.find((essay) => essay.essayId === 'essay-3')!.gradings[2]!
    const report = latest.report as Record<string, unknown>
    const japanese = report.japanese as Record<string, unknown>
    const chinese = report.chinese as Record<string, unknown>

    expect(wrapper.get('[data-ee-lang-ja]').attributes('aria-pressed')).toBe('true')
    expect(normalize(wrapper.get('[data-ee-summary]').text())).toBe(normalize(String(japanese.summary)))

    await wrapper.get('[data-ee-lang-zh]').trigger('click')
    await flushPromises()

    expect(normalize(wrapper.get('[data-ee-summary]').text())).toBe(normalize(String(chinese.summary)))
    expect(wrapper.get('[data-ee-rubric]').text()).toContain('内容的评语（3）')
    expect(wrapper.get('[data-ee-correction]').text()).toContain('避免重复。')
    expect(wrapper.text()).toContain(String(chinese.advice))
    expect(wrapper.text()).toContain(String(chinese.notice))

    await wrapper.get('[data-ee-lang-ja]').trigger('click')
    await flushPromises()

    expect(normalize(wrapper.get('[data-ee-summary]').text())).toBe(normalize(String(japanese.summary)))
  })

  it('結果言語は 2.0 のセグメント（2 つのボタン）で切り替える', async () => {
    const { wrapper } = await open('essay-3')

    // 2.0 の `word2-language-toggle` と同じ形（`role="group"` ＋ 2 つの button ＋ `aria-pressed`）
    const group = wrapper.get('[data-ee-lang]')
    expect(group.attributes('role')).toBe('group')
    const buttons = group.findAll('button')
    expect(buttons.map((button) => button.text())).toEqual(['日本語', '中文'])
    expect(buttons[0]!.attributes('aria-pressed')).toBe('true')
    expect(buttons[1]!.attributes('aria-pressed')).toBe('false')
    // 選んでいる側は塗り（2.0 の `.active`）
    expect(buttons[0]!.classes()).toContain('is-active')
    expect(buttons[1]!.classes()).not.toContain('is-active')
    // ドロップダウンは残さない（2.0 の英作文は select だったが、利用者の指示でセグメントにする）
    expect(group.find('select').exists()).toBe(false)

    await wrapper.get('[data-ee-lang-zh]').trigger('click')
    expect(wrapper.get('[data-ee-lang-zh]').attributes('aria-pressed')).toBe('true')
    expect(wrapper.get('[data-ee-lang-ja]').attributes('aria-pressed')).toBe('false')
    expect(wrapper.get('[data-ee-lang-zh]').classes()).toContain('is-active')
  })

  it('レポートに「作文タイトル」を出し、結果の言語に追随する', async () => {
    const { wrapper } = await open('essay-3')

    const title = wrapper.get('[data-ee-report-title]')
    expect(title.text()).toContain('作文タイトル')
    expect(title.text()).toContain('部活動の時間を増やすべきか')

    await wrapper.get('[data-ee-lang-zh]').trigger('click')
    await flushPromises()

    const titleZh = wrapper.get('[data-ee-report-title]')
    expect(titleZh.text()).toContain('作文标题')
    expect(titleZh.text()).toContain('是否应该增加社团活动时间')
    expect(titleZh.text()).not.toContain('作文タイトル')
  })

  it('「設問が求めていること」を、添削結果の箇条書きで出す', async () => {
    const { wrapper } = await open('essay-3')

    const items = wrapper.findAll('[data-ee-task-requirement]')
    expect(items.map((item) => item.text())).toEqual(['設問の問いに答える', '理由を 2 つ以上挙げる', '結論を述べる'])
    expect(wrapper.text()).toContain('設問が求めていること')
  })

  it('レポートの見出しは 2.0 の文言で出す', async () => {
    const { wrapper } = await open('essay-3')

    expect(wrapper.text()).toContain('AI添削レポート')
    expect(wrapper.text()).toContain('設問への適合度、内容、構成、語彙・文法を総合評価')
  })

  it('親エリアでは閲覧だけにして、【AI添削】のボタンを出さない', async () => {
    const parent = await open('essay-3', 'parent')

    expect(parent.wrapper.find('[data-ee-grade]').exists()).toBe(false)
    expect(parent.wrapper.find('[data-ee-regrade]').exists()).toBe(false)
    // レポートと回の切り替えは見られる
    expect(parent.wrapper.findAll('[data-ee-rubric]')).toHaveLength(4)
    expect(activeRound(parent.wrapper)).toBe('3')

    // 未添削の作文でも、親は添削を始められない
    const parentUngraded = await open('essay-4', 'parent')
    expect(parentUngraded.wrapper.get('[data-ee-empty]').text()).toContain('この作文はまだAI添削されていません')
    expect(parentUngraded.wrapper.find('[data-ee-grade]').exists()).toBe(false)

    // 生徒・管理者はこれまでどおり添削できる
    const student = await open('essay-3', 'student')
    expect(student.wrapper.find('[data-ee-regrade]').exists()).toBe(true)
    const admin = await open('essay-4')
    expect(admin.wrapper.find('[data-ee-grade]').exists()).toBe(true)
  })

  it('受付で 404（削除済みなど）のときは、日本語のエラーを知らせる', async () => {
    const { wrapper } = await open('essay-3')
    // 開いている間に消された（ほかの端末で削除したなど）
    db = db.filter((essay) => essay.essayId !== 'essay-3')

    await wrapper.get('[data-ee-regrade]').trigger('click')
    await flushPromises()

    expect(acceptCalls).toBe(1)
    expect(toastMessages().join(' '))
      .toContain('この英作文は見つかりませんでした。削除された可能性があります。')
    // 画面の文言（ボタン・見出し）はこれまでどおり
    expect(wrapper.get('[data-ee-regrade]').text()).toContain('もう一度AI添削')
    expect(wrapper.get('[data-ee-report-heading]').text()).toBe('AI添削レポート')
  })

  it('まだ添削していない作文は、案内と【この内容でAI添削】を出し、押すと受付けて結果を待つ', async () => {
    vi.useFakeTimers()
    try {
      const { wrapper } = await open('essay-4')

      expect(wrapper.get('[data-ee-empty]').text()).toContain('この作文はまだAI添削されていません')
      expect(wrapper.find('[data-ee-round]').exists()).toBe(false)
      expect(wrapper.findAll('[data-ee-rubric]')).toHaveLength(0)

      await wrapper.get('[data-ee-grade]').trigger('click')
      await flushPromises()

      // 受付だけ（実行はバックエンド）。すぐ知らせて、結果は様子見で拾う
      expect(acceptCalls).toBe(1)
      expect(toastMessages().join(' ')).toContain('AI添削を受付けました（第 1 回）')
      expect(wrapper.find('[data-ee-empty]').exists()).toBe(true)

      // 1 周期目の様子見ではまだ実行中（受付けた回は QUEUED のまま）
      await vi.advanceTimersByTimeAsync(3_000)
      await flushPromises()
      expect(toastMessages().some((message) => message.includes('終わりました'))).toBe(false)
      expect(wrapper.find('[data-ee-empty]').exists()).toBe(true)

      // 実行が終わったら、結果が出て知らせる
      finishGrading('essay-4', 1)
      await vi.advanceTimersByTimeAsync(3_000)
      await flushPromises()

      expect(wrapper.find('[data-ee-empty]').exists()).toBe(false)
      expect(wrapper.findAll('[data-ee-rubric]')).toHaveLength(4)
      expect(activeRound(wrapper)).toBe('1')
      expect(wrapper.findAll('[data-ee-correction]').length).toBeGreaterThanOrEqual(1)
      expect(toastMessages().join(' ')).toContain('AI添削が終わりました（成功 1 / 失敗 0）')

      // 終わったら様子見を止める（読み直しに行かない）
      const callsAfterFinish = detailCalls
      await vi.advanceTimersByTimeAsync(9_000)
      await flushPromises()
      expect(detailCalls).toBe(callsAfterFinish)

      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('様子見のたびに要素を作り直さない（画面がちらつかない）', async () => {
    vi.useFakeTimers()
    try {
      // 3 回目の添削がまだ実行中（RUNNING）の状態で開く
      db = db.map((essay) => essay.essayId === 'essay-3'
        ? {
            ...essay,
            gradings: [
              ...essay.gradings.slice(0, 2),
              grading(3, { statusCode: 'RUNNING', score: null, maxScore: null, report: null })
            ]
          }
        : essay)
      const { wrapper } = await open('essay-3')

      const meta = wrapper.get('.ee-meta').element
      const images = wrapper.get('.ee-images').element
      const switcher = wrapper.get('[data-ee-round-bar]').element

      // 様子見の周期に入った直後（取得はまだ未解決）: 表を差し替えない・待たせない
      vi.advanceTimersByTime(3_000)
      await nextTick()
      expect(wrapper.text()).not.toContain('読み込んでいます')
      expect(wrapper.get('.ee-meta').element).toBe(meta)

      // 取得が返っても、**同じ要素**のまま（組み直さない＝ちらつかない）
      await flushPromises()
      expect(wrapper.get('.ee-meta').element).toBe(meta)
      expect(wrapper.get('.ee-images').element).toBe(images)
      expect(wrapper.get('[data-ee-round-bar]').element).toBe(switcher)

      // 中身はちゃんと新しくなる（実行中 → 成功）
      finishGrading('essay-3', 3)
      await vi.advanceTimersByTimeAsync(3_000)
      await flushPromises()
      expect(wrapper.get('.ee-meta').element).toBe(meta)
      expect(wrapper.findAll('[data-ee-round-tab]')).toHaveLength(3)
      expect(wrapper.findAll('[data-ee-rubric]')).toHaveLength(4)
      expect(shownScore(wrapper)).toBe(23)
      expect(toastMessages().join(' ')).toContain('AI添削が終わりました（成功 1 / 失敗 0）')

      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('古い形式（4 観点が無い）の回を選んでも画面が壊れない', async () => {
    const { wrapper } = await open('essay-7')

    // 既定は最新（古い形式の第 2 回）。満点が分からないので点数だけを出す
    expect(activeRound(wrapper)).toBe('2')
    expect(normalize(wrapper.get('[data-ee-score]').text())).toBe('18')
    expect(roundTitle(wrapper, 2)).toContain('得点 18')

    // 無いブロックは見出しごと出さない（空の見出しを残さない）
    expect(wrapper.findAll('[data-ee-rubric]')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('4 観点の評価')
    expect(wrapper.findAll('[data-ee-correction]')).toHaveLength(0)
    expect(wrapper.text()).not.toContain('修正ポイント')
    expect(wrapper.find('[data-ee-model]').exists()).toBe(false)
    expect(wrapper.text()).not.toContain('改善後の作文例')
    expect(wrapper.findAll('[data-ee-task-requirement]')).toHaveLength(0)

    // 有るものは出す
    expect(wrapper.get('[data-ee-summary]').text()).toContain('内容・構成・語彙・文法の 4 観点で評価しました。')
    expect(wrapper.text()).toContain('理由をもう 1 つ足しましょう。')

    // 新形式の回へ切り替えれば、これまでどおり 4 観点が出る
    await selectRound(wrapper, 1)
    expect(wrapper.findAll('[data-ee-rubric]')).toHaveLength(4)
    expect(wrapper.find('[data-ee-model]').exists()).toBe(true)
    expect(wrapper.findAll('[data-ee-correction]')).toHaveLength(1)
  })

  it('設問の要求がオブジェクトの行（実データ）は 1 段落で出し、結果の言語に追随する', async () => {
    const template = db.find((item) => item.essayId === 'essay-7')!
    db = [
      ...db,
      {
        ...template,
        essayId: 'essay-9',
        title: '設問の要求がオブジェクトの英作文',
        gradings: [
          grading(1, {
            report: {
              ...newReport(1),
              // 実データの形（英作文ID 25 の行）。字数条件も設問の要求もオブジェクト
              wordRequirement: { type: 'range', target: null, minimum: 80, maximum: 100 },
              taskRequirements: {
                commentJa: '設問の問いに答え、理由を 2 つ以上挙げて結論を述べる。',
                commentZh: '回答题目要求，列出两个以上理由并给出结论。'
              }
            }
          })
        ]
      }
    ]
    const { wrapper } = await open('essay-9')

    // 箇条書きではなく 1 段落で出す
    const items = wrapper.findAll('[data-ee-task-requirement]')
    expect(items).toHaveLength(1)
    expect(items[0]!.text()).toBe('設問の問いに答え、理由を 2 つ以上挙げて結論を述べる。')
    // 字数条件もオブジェクトから日本語に組み立てる
    expect(wrapper.text()).toContain('80〜100語')

    await wrapper.get('[data-ee-lang-zh]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-ee-task-requirement]').text()).toBe('回答题目要求，列出两个以上理由并给出结论。')
  })

  it('得点が分からない回は数字を出さない（0 / 16 のような嘘を出さない）', async () => {
    const template = db.find((item) => item.essayId === 'essay-7')!
    db = [
      ...db,
      {
        ...template,
        essayId: 'essay-10',
        title: '得点が無い英作文',
        // 行にも JSON にも得点が無い（2.0 の移行データでありうる）
        gradings: [grading(1, { score: null, maxScore: 16, report: OLD_REPORT })]
      }
    ]
    const { wrapper } = await open('essay-10')

    expect(normalize(wrapper.get('[data-ee-score]').text())).toBe('—')
    expect(wrapper.text()).not.toContain('0 / 16')
    expect(wrapper.text()).not.toContain('undefined')
    // 回のタブも同じ（点数をでっち上げない）
    expect(roundTitle(wrapper, 1)).toContain('得点 —')
  })

  it('作文が見つからないときは、案内と一覧への戻りを出す', async () => {
    const { wrapper } = await open('essay-unknown')

    expect(wrapper.text()).toContain('作文が見つかりません')
    expect(wrapper.get('[data-ee-back]').attributes('href')).toBe('/admin/english-essay')
    expect(wrapper.find('[data-ee-round]').exists()).toBe(false)
    expect(wrapper.find('[data-ee-grade]').exists()).toBe(false)
  })

  it('【一覧へ戻る】は、開いているエリアの一覧（/student など）へ戻す', async () => {
    const { wrapper } = await open('essay-3', 'student')

    expect(wrapper.get('[data-ee-back]').attributes('href')).toBe('/student/english-essay')
  })

  it('設問・本文・登録画像を、区分のラベルつきで出す', async () => {
    const { wrapper } = await open('essay-3')
    const essay = db.find((item) => item.essayId === 'essay-3')!

    expect(wrapper.get('[data-ee-title]').text()).toBe(essay.title)
    expect(wrapper.get('[data-ee-question]').text()).toBe(essay.questionText)
    expect(wrapper.get('[data-ee-essay]').text()).toBe(essay.essayText)
    expect(wrapper.get('[data-ee-words]').text()).toContain(String(essay.wordCount))

    const images = wrapper.findAll('[data-ee-image]')
    expect(images).toHaveLength(3)
    expect(images[0]!.get('img').attributes('alt')).toContain('設問画像')
    expect(images[1]!.get('img').attributes('alt')).toContain('答案画像')
    expect(wrapper.text()).toContain('設問画像')
    expect(wrapper.text()).toContain('答案画像')
  })

  it('登録画像はクリックで拡大し、Esc で閉じる', async () => {
    const { wrapper } = await open('essay-3')

    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)
    await wrapper.findAll('[data-ee-image]')[1]!.trigger('click')
    await flushPromises()

    const preview = wrapper.get('[data-ee-image-preview]')
    // 画像は API の URL をそのまま使う
    expect(preview.get('img').attributes('src')).toBe('/api/user/english-essays/essay-3/images/32')
    expect(preview.text()).toContain('答案画像')

    await pressEscape()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)
  })

  it('登録画像の拡大は、右上の × でも【閉じる】でも閉じられる（背景クリックでは閉じない）', async () => {
    const { wrapper } = await open('essay-3')

    await wrapper.findAll('[data-ee-image]')[0]!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(true)

    // 背景（灰色の部分）を押しても閉じない（`docs/FRONTEND_GUIDE.md` 3.9）
    await wrapper.get('[data-ee-image-preview]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(true)

    // 右上の ×（フォームと同じ共通部品なので、閉じ方も同じ）
    await wrapper.get('[data-ee-image-preview-close]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)

    // 足元の【閉じる】
    await wrapper.findAll('[data-ee-image]')[1]!.trigger('click')
    await flushPromises()
    await wrapper.get('[data-ee-image-preview-dismiss]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)
  })

  it('画像の実体が読めないときは、その 1 枚だけ「表示できません」に変える', async () => {
    const { wrapper } = await open('essay-3')

    expect(wrapper.findAll('[data-ee-image]')).toHaveLength(3)
    expect(wrapper.findAll('[data-ee-image] img')).toHaveLength(3)
    expect(wrapper.findAll('[data-ee-image-missing]')).toHaveLength(0)

    // 1 枚目（設問画像）の実体が読めない
    await wrapper.findAll('[data-ee-image] img')[0]!.trigger('error')
    await flushPromises()

    // 変わったのは 1 枚目だけ。2 枚目・3 枚目（答案画像）は無事
    expect(wrapper.findAll('[data-ee-image-missing]')).toHaveLength(1)
    expect(wrapper.findAll('[data-ee-image] img')).toHaveLength(2)
    expect(wrapper.get('[data-ee-image-missing]').text()).toBe('表示できません')

    const thumbs = wrapper.findAll('[data-ee-image]')
    expect(thumbs[0]!.find('img').exists()).toBe(false)
    expect(thumbs[0]!.text()).toContain('表示できません')
    expect(thumbs[1]!.find('img').exists()).toBe(true)
    expect(thumbs[1]!.get('img').attributes('alt')).toContain('答案画像')
    expect(thumbs[2]!.find('img').exists()).toBe(true)
  })

  it('読めない画像は、その画像の拡大（サムネイルと【拡大】）を押せなくする', async () => {
    const { wrapper } = await open('essay-3')

    expect(wrapper.findAll('.ee-images__zoom')).toHaveLength(3)
    expect(wrapper.findAll('[data-ee-image]')[0]!.attributes('disabled')).toBeUndefined()
    expect(wrapper.findAll('.ee-images__zoom')[0]!.attributes('disabled')).toBeUndefined()

    await wrapper.findAll('[data-ee-image] img')[0]!.trigger('error')
    await flushPromises()

    const thumbs = wrapper.findAll('[data-ee-image]')
    const zooms = wrapper.findAll('.ee-images__zoom')
    // 読めない 1 枚目は、サムネイルも【拡大】も押せない
    expect(thumbs[0]!.attributes('disabled')).toBeDefined()
    expect(zooms[0]!.attributes('disabled')).toBeDefined()
    expect((thumbs[0]!.element as HTMLButtonElement).disabled).toBe(true)
    expect((zooms[0]!.element as HTMLButtonElement).disabled).toBe(true)
    // 読める画像は押せるまま
    expect(thumbs[1]!.attributes('disabled')).toBeUndefined()
    expect(zooms[1]!.attributes('disabled')).toBeUndefined()
    expect(thumbs[2]!.attributes('disabled')).toBeUndefined()
    expect(zooms[2]!.attributes('disabled')).toBeUndefined()
  })

  it('設問と作文は、詳細でも拡大して読める（読み取り専用・×／【閉じる】／Esc で閉じる）', async () => {
    const { wrapper } = await open('essay-3')

    const buttons = wrapper.findAll('[data-ee-zoom-text]')
    expect(buttons).toHaveLength(2)
    expect(wrapper.find('[data-ee-text-preview]').exists()).toBe(false)

    // 1 つ目＝作文の設問。全文をそのまま出す
    await buttons[0]!.trigger('click')
    await flushPromises()
    const preview = wrapper.get('[data-ee-text-preview]')
    expect(preview.get('[data-ee-text-preview-title]').text()).toBe('作文の設問')
    expect(preview.get('[data-ee-text-preview-text]').text()).toBe(QUESTION)
    // 読むだけ（2.0 の詳細は textarea だったが、2.1 の詳細は閲覧のみ。編集欄を出さない）
    expect(preview.find('textarea').exists()).toBe(false)
    expect(preview.find('input').exists()).toBe(false)

    // 背景（灰色の部分）を押しても閉じない（`docs/FRONTEND_GUIDE.md` 3.9）
    await preview.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-text-preview]').exists()).toBe(true)

    // 右上の ×
    await wrapper.get('[data-ee-text-preview-close]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-text-preview]').exists()).toBe(false)

    // 2 つ目＝手書き作文。Esc でも閉じる
    await wrapper.findAll('[data-ee-zoom-text]')[1]!.trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-ee-text-preview-title]').text()).toBe('手書き作文')
    expect(wrapper.get('[data-ee-text-preview-text]').text()).toBe(ESSAY)
    await pressEscape()
    expect(wrapper.find('[data-ee-text-preview]').exists()).toBe(false)

    // 足元の【閉じる】
    await wrapper.findAll('[data-ee-zoom-text]')[0]!.trigger('click')
    await flushPromises()
    await wrapper.get('[data-ee-text-preview-dismiss]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-text-preview]').exists()).toBe(false)
  })

  it('【拡大表示】はモーダルで出し、背景クリックでは閉じず、閉じるボタンと Esc で閉じる', async () => {
    const { wrapper } = await open('essay-3')
    const latest = db.find((essay) => essay.essayId === 'essay-3')!.gradings[2]!
    const japanese = (latest.report as Record<string, unknown>).japanese as Record<string, unknown>

    const zooms = wrapper.findAll('[data-ee-zoom]')
    expect(zooms.length).toBeGreaterThanOrEqual(4)

    // 1 つ目＝総合評価。要約まで大きく出す
    await zooms[0]!.trigger('click')
    await flushPromises()

    const dialog = wrapper.get('[data-ee-zoom-dialog]')
    expect(normalize(dialog.text())).toContain(normalize(String(japanese.summary)))
    expect(dialog.text()).toContain('EIKEN WRITING FEEDBACK')

    // 背景（灰色の部分）を押しても閉じない
    await dialog.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-zoom-dialog]').exists()).toBe(true)

    // 右上の × で閉じる
    await dialog.get('.dialog__close').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-zoom-dialog]').exists()).toBe(false)

    // 開き直して Esc でも閉じる
    await zooms[2]!.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-ee-zoom-dialog]').exists()).toBe(true)
    await pressEscape()
    expect(wrapper.find('[data-ee-zoom-dialog]').exists()).toBe(false)
  })

  it('【コピー】は改善後の作文例をクリップボードに写して知らせる', async () => {
    const { wrapper } = await open('essay-3')
    const latest = db.find((essay) => essay.essayId === 'essay-3')!.gradings[2]!
    const modelAnswer = String((latest.report as Record<string, unknown>).modelAnswer)
    const writeText = vi.fn(() => Promise.resolve())
    Object.defineProperty(navigator, 'clipboard', { value: { writeText }, configurable: true })

    await wrapper.get('[data-ee-copy]').trigger('click')
    await flushPromises()

    expect(writeText).toHaveBeenCalledWith(modelAnswer)
    expect(toastMessages().join(' ')).toContain('コピー')
  })

  it('2.0 の詳細画面の並び（見出し → 設問と作文 → 登録画像 → AI添削レポート）で出す', async () => {
    const { wrapper } = await open('essay-3')
    const essay = db.find((item) => item.essayId === 'essay-3')!

    // 見出し（2.0 の essay-hero。題は説明の位置に出し、登録番号・級・登録日時を並べる）
    const hero = wrapper.get('.ee-hero')
    expect(hero.text()).toContain('英作文 詳細')
    expect(hero.get('[data-ee-title]').text()).toBe(essay.title)
    expect(hero.text()).toContain(`登録番号 ${essay.essayId}`)
    expect(hero.text()).toContain('英検1級')
    expect(hero.text()).toContain('保存データ')
    expect(hero.find('[data-ee-back]').exists()).toBe(true)

    // 本文（2.0 の recognition-card。設問 → 作文の順）
    const meta = wrapper.get('.ee-meta')
    const headings = meta.findAll('.ee-block__title').map((title) => title.text())
    expect(headings).toEqual(['作文の設問', '手書き作文'])
    expect(meta.get('[data-ee-question]').text()).toBe(essay.questionText)
    expect(meta.get('[data-ee-essay]').text()).toBe(essay.essayText)

    // 登録画像（2.0 の upload-card。詳細では「登録画像」＋説明）
    const images = wrapper.get('.ee-images')
    expect(images.text()).toContain('登録画像')
    expect(images.text()).toContain('OCRに使用した保存済み画像です。')
    expect(images.findAll('[data-ee-image]')).toHaveLength(essay.images.length)

    // AI添削レポート（2.0 の result-card。見出し・説明・**結果言語**）
    const report = wrapper.get('.ee-report-card')
    expect(report.get('[data-ee-report-heading]').text()).toBe('AI添削レポート')
    expect(report.get('[data-ee-report-lead]').text()).toBe('設問への適合度、内容、構成、語彙・文法を総合評価')
    expect(report.find('[data-ee-lang]').exists()).toBe(true)

    // 節の順序（本文が先、登録画像が後。登録画像は右の柱に固定する）
    const html = wrapper.html()
    expect(html.indexOf('ee-hero')).toBeLessThan(html.indexOf('ee-meta'))
    expect(html.indexOf('ee-meta')).toBeLessThan(html.indexOf('ee-images'))
    expect(html.indexOf('ee-images')).toBeLessThan(html.indexOf('ee-report-card'))
  })

  it('添削の回（2.1 の追加要件）は、2.0 の見た目に足す形でレポートの上に置く', async () => {
    const { wrapper } = await open('essay-3')

    // 回の切り替えはレポートの見出し（と結果言語）の下・レポート本体の上
    const report = wrapper.get('.ee-report-card')
    const html = report.html()
    expect(html.indexOf('ee-report-card__head')).toBeLessThan(html.indexOf('data-ee-round-bar'))
    expect(html.indexOf('data-ee-round-bar')).toBeLessThan(html.indexOf('data-ee-rubric'))
    // 見ている回は選択中のタブで示す（2.0 には無い足した部分。旧・「第 N 回の結果」の見出しはタブが兼ねる）
    expect(report.get('[data-ee-round] .is-active').text()).toContain('第 3 回')
  })

  it('2.0 の詳細の欄構成（領域・節の順序・登録画像の右の柱）を守る', async () => {
    const { wrapper } = await open('essay-3')
    const essay = db.find((item) => item.essayId === 'essay-3')!

    // 領域は 2.0 の `grid-template-areas` と同じ 4 つ
    const regions = wrapper.findAll('[data-ee-region]').map((node) => node.attributes('data-ee-region'))
    expect(regions).toEqual(['hero', 'recognition', 'side', 'result'])

    // 設問 : 作文は `.ocr-grid` の 2 面（見出しは 2.0 と同じ 2 つ、語数は作文の足元）
    const panels = wrapper.get('.ee-meta').findAll('.ee-ocr-panel')
    expect(panels).toHaveLength(2)
    expect(panels.map((panel) => panel.get('.ee-block__title').text()))
      .toEqual(['作文の設問', '手書き作文'])
    expect(panels[1]!.get('footer [data-ee-words]').text()).toBe(`${essay.wordCount} words`)

    // 登録画像は 2.0 の `upload-card` と同じく、サムネイル＋ファイル名の一覧（格子に並べない）
    const items = wrapper.get('.ee-images').findAll('.ee-images__item')
    expect(items).toHaveLength(essay.images.length)
    expect(items[0]!.get('.ee-images__meta strong').text()).toBe(essay.images[0]!.originalFileName)
    expect(items[0]!.text()).toContain('設問画像')
    // 画像は `[data-ee-image]` の中の `img` を出す（区分のラベルつき）
    expect(items[0]!.get('[data-ee-image] img').attributes('alt')).toContain('設問画像')

    // 節の順序は 2.0 の詳細の DOM の順（英雄 → 設問と作文 → 登録画像 → レポート）
    const html = wrapper.html()
    expect(html.indexOf('data-ee-region="hero"')).toBeLessThan(html.indexOf('data-ee-region="recognition"'))
    expect(html.indexOf('data-ee-region="recognition"')).toBeLessThan(html.indexOf('data-ee-region="side"'))
    expect(html.indexOf('data-ee-region="side"')).toBeLessThan(html.indexOf('data-ee-region="result"'))
  })

  it('レポートの中は 2.0 と同じ割り付け（得点 → 4 観点 → 修正ポイント／改善後の作文例 → ワンポイント）', async () => {
    const { wrapper } = await open('essay-3')
    const latest = db.find((item) => item.essayId === 'essay-3')!.gradings[2]!
    const japanese = (latest.report as Record<string, unknown>).japanese as Record<string, unknown>
    const report = wrapper.get('[data-ee-report]')

    // 得点の輪の中は「点数 / 満点」＋級名（2.0 の `.score-ring`）
    const ring = report.get('.ee-report-score')
    expect(normalize(ring.get('[data-ee-score]').text())).toBe(`${latest.score} / ${latest.maxScore}`)
    expect(ring.text()).toContain(`/ ${String(latest.maxScore)}`)
    expect(ring.text()).toContain('英検1級')

    // 直下のブロックは、2.0 の `renderReport` と同じ順（総合評価 → 4 観点 → 2 列 → ワンポイント）
    const blocks = report.element.children
    const classes = (index: number): string => (blocks[index] as HTMLElement).className
    expect(classes(1)).toContain('ee-overview')
    expect(classes(2)).toContain('ee-requirements-sec')
    expect(classes(3)).toContain('ee-rubric-panel')
    expect(classes(4)).toContain('ee-columns')
    expect(classes(5)).toContain('ee-advice-sec')

    // 4 観点・修正ポイント・改善後の作文例・ワンポイントは、それぞれ 1 つの枠にまとめる
    expect(report.findAll('.ee-rubric-panel [data-ee-rubric]')).toHaveLength(4)
    const columns = report.get('.ee-columns')
    expect(columns.findAll('.ee-panel')).toHaveLength(2)
    expect(columns.get('.ee-corrections').findAll('[data-ee-correction]').length).toBeGreaterThanOrEqual(1)
    expect(columns.find('[data-ee-model]').exists()).toBe(true)
    expect(report.get('.ee-advice-sec').text()).toContain(String(japanese.advice))
    expect(report.get('.ee-advice-sec').text()).toContain(String(japanese.notice))
  })

  it('結果言語の切り替えは 2.0 と同じくレポートの見出しの右に 1 つだけ置く', async () => {
    const { wrapper } = await open('essay-3')

    expect(wrapper.findAll('[data-ee-lang]')).toHaveLength(1)
    expect(wrapper.get('.ee-report-card__head').find('[data-ee-lang]').exists()).toBe(true)
    expect(wrapper.get('[data-ee-report]').find('[data-ee-lang]').exists()).toBe(false)
  })

  it('「デモ」「Demo」「Mock」「サンプル」を画面に出さない', async () => {
    const withGrading = await open('essay-3')
    const withoutGrading = await open('essay-4')
    const legacy = await open('essay-7')

    for (const wrapper of [withGrading.wrapper, withoutGrading.wrapper, legacy.wrapper]) {
      const text = wrapper.text()
      for (const forbidden of ['デモ', 'Demo', 'demo', 'Mock', 'mock', 'サンプル', 'サンプルデータ']) {
        expect(text).not.toContain(forbidden)
      }
    }
  })
})

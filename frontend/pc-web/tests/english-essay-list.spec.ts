/* eslint-disable vue/one-component-per-file -- テストの中で窓の代役を作るため */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { defineComponent, nextTick, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { useToast } from '@study21/web-shared'
import EssayListView from '@/views/english/EssayListView.vue'
import { ESSAY_LEVEL_LABELS } from '@/features/english-essay/types'
import { essayList, resetEssays } from '@/features/english-essay/store'

// 臨時ファイルの一覧は本物の API を呼ばない（画面の振る舞いだけを見る）。
vi.mock('@/api/tempfiles', () => ({
  getTempFiles: vi.fn(async () => ({
    success: true,
    code: 'OK',
    message: 'OK',
    data: [
      {
        tempFileId: 7,
        originalFileName: '答案用紙.png',
        extension: 'png',
        mimeType: 'image/png',
        fileSize: 2048,
        comment: null,
        thumbnail: 'RAWTHUMB',
        image: true,
        contentUrl: '/content/7',
        createdAt: null,
        updatedAt: null
      }
    ]
  }))
}))

/**
 * 英作文AI添削の一覧（2.0 の `english_essay.jsp` 相当）。
 *
 * <p>見るのは**公開の振る舞い**だけ: 一覧の行（得点・状態は `latestGrading`）、サーバー側の
 * 絞り込みとページング、目のアイコンでの詳細への遷移、【英作文新規】と【編集】が
 * **ページ遷移**であること（モーダルを開かない）、削除、そして【デモ】の語が画面に出ないこと。</p>
 *
 * <p>API は `fetch` でスタブする（`tests/japanese-word.spec.ts` と同じ作り）。作文と画像は
 * user-api、文字認識と添削の受付は admin-api。</p>
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
let sequence = 0
/** 画像の上限（サーバーが返す値。テストで差し替える。`null` は取得失敗）。 */
let limits: { maxImages: number; maxImageMb: number } | null = { maxImages: 8, maxImageMb: 10 }

/** 添削 1 回（成功）。レポートは新形式（4 観点は rubricValues と rubricNotes）。 */
function grading(round: number, score: number, maxScore: number | null = 32): FakeGrading {
  return {
    gradingId: `${100 + round}`,
    round,
    statusCode: 'SUCCEEDED',
    level: 'GRADE1',
    titleJa: null,
    titleZh: null,
    questionText: null,
    essayText: null,
    wordCount: null,
    score,
    maxScore,
    report: {
      version: 'eiken-ai-v1',
      rubricMax: 8,
      rubricValues: [7, 6, 6, 5],
      modelAnswer: 'In conclusion, learning English opens many opportunities for our future.',
      wordRequirement: '200〜240語',
      taskRequirements: ['設問の問いに答える', '理由を 2 つ以上挙げる', '結論を述べる'],
      japanese: {
        title: '前回の指摘が活きています',
        summary: `第 ${round} 回の要約です。`,
        tags: ['設問適合', '理由の充実', '接続表現'],
        rubric: ['内容', '構成', '語彙', '文法'],
        rubricNotes: ['理由の数が増えました。', '段落の役割が明確です。', '書き言葉らしい語彙が増えました。', 'コンマの使い方が正確です。'],
        corrections: [['I think', 'I believe', '表現', '繰り返しを避けます。']],
        advice: '結論の前に理由を 2 回示すと評価が上がります。',
        notice: '本結果は公式採点ではありません。'
      },
      chinese: {
        title: '上次指出的问题已改善',
        summary: `第 ${round} 次批改的总结。`,
        tags: ['切题', '理由充分', '连接词'],
        rubric: ['内容', '结构', '词汇', '语法'],
        rubricNotes: ['理由增加了。', '段落清晰。', '书面语词汇增加。', '逗号使用准确。'],
        corrections: [['I think', 'I believe', '表达', '避免重复。']],
        advice: '结论前用两次理由更容易体现数量。',
        notice: '本结果并非官方评分。'
      }
    },
    failureReason: null,
    startedAt: null,
    finishedAt: null,
    createdAt: `2026-09-2${round}T20:30:00.000Z`
  }
}

/** 画像 1 枚。 */
function image(imageId: string, category: string, order: number, originalFileName: string): FakeImage {
  return {
    imageId, order, category, originalFileName,
    mimeType: 'image/png', fileSize: 1024, recognizedText: null, confidence: null
  }
}

/** DB の初期状態（見本の 4 篇。1 篇は未添削）。 */
function seedDb(): FakeEssay[] {
  return [
    {
      essayId: 'essay-1',
      level: 'GRADE2',
      title: '小学生からの英語教育',
      titleZh: '小学英语教育',
      questionText: QUESTION,
      essayText: ESSAY,
      wordCount: 20,
      stateCode: 'A',
      createdAt: '2026-09-20T09:12:00.000Z',
      updatedAt: '2026-09-20T09:20:00.000Z',
      images: [image('11', 'question', 1, 'essay-1-q1.png'), image('12', 'answer', 2, 'essay-1-a1.png')],
      gradings: [grading(1, 21, 32)]
    },
    {
      essayId: 'essay-2',
      level: 'PRE1',
      title: '読書と動画の学習効果',
      titleZh: '读书与视频的学习效果',
      questionText: QUESTION,
      essayText: ESSAY,
      wordCount: 24,
      stateCode: 'A',
      createdAt: '2026-09-22T14:30:00.000Z',
      updatedAt: '2026-09-23T10:05:00.000Z',
      images: [image('21', 'question', 1, 'essay-2-q1.png'), image('22', 'answer', 2, 'essay-2-a1.png')],
      gradings: [grading(1, 12, 16), grading(2, 15, 16)]
    },
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
      gradings: [grading(1, 20, 32), grading(2, 22, 32), grading(3, 23, 32)]
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
      createdAt: '2026-09-26T08:00:00.000Z',
      updatedAt: '2026-09-26T08:00:00.000Z',
      images: [image('41', 'question', 1, 'essay-4-q1.png')],
      gradings: []
    }
  ]
}

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function notFound(): Response {
  return new Response(JSON.stringify({ success: false, code: 'NOT_FOUND', message: '見つかりません' }), {
    status: 404,
    headers: { 'Content-Type': 'application/json' }
  })
}

function validationError(message: string): Response {
  return new Response(JSON.stringify({ success: false, code: 'VALIDATION_ERROR', message }), {
    status: 400,
    headers: { 'Content-Type': 'application/json' }
  })
}

function serverError(message: string): Response {
  return new Response(JSON.stringify({ success: false, code: 'ERROR', message }), {
    status: 500,
    headers: { 'Content-Type': 'application/json' }
  })
}

/** 一覧の 1 行に組み替える（API の形）。 */
function listRowOf(essay: FakeEssay): Record<string, unknown> {
  const latest = essay.gradings[essay.gradings.length - 1] ?? null
  return {
    essayId: essay.essayId,
    level: essay.level,
    title: essay.title,
    titleZh: essay.titleZh,
    questionText: essay.questionText,
    essayText: essay.essayText,
    wordCount: essay.wordCount,
    imageCount: essay.images.length,
    questionImageCount: essay.images.filter((item) => item.category === 'question').length,
    answerImageCount: essay.images.filter((item) => item.category === 'answer').length,
    createdAt: essay.createdAt,
    updatedAt: essay.updatedAt,
    latestGrading: latest === null ? null : {
      gradingId: latest.gradingId,
      round: latest.round,
      statusCode: latest.statusCode,
      score: latest.score,
      maxScore: latest.maxScore,
      createdAt: latest.createdAt
    }
  }
}

/** 一覧（絞り込み・ページングはサーバー側）。 */
function pageOf(params: URLSearchParams): Record<string, unknown> {
  const keyword = params.get('keyword') ?? ''
  const level = params.get('level') ?? ''
  const from = params.get('dateFrom') ?? ''
  const to = params.get('dateTo') ?? ''
  const page = Number(params.get('page') ?? 1)
  const size = Number(params.get('size') ?? 20)
  const filtered = db
    .filter((essay) => {
      if (keyword !== '' && !`${essay.title} ${essay.questionText} ${essay.essayText}`.includes(keyword)) return false
      if (level !== '' && essay.level !== level) return false
      const day = essay.createdAt.slice(0, 10)
      if (from !== '' && day < from) return false
      if (to !== '' && day > to) return false
      return true
    })
    .sort((left, right) => right.createdAt.localeCompare(left.createdAt))
  return {
    items: filtered.slice((page - 1) * size, page * size).map(listRowOf),
    total: filtered.length,
    page,
    size,
    totalPages: Math.max(1, Math.ceil(filtered.length / size))
  }
}

/** 偽のサーバー（未定義の呼び出しは失敗させる）。 */
function serverFetch(url: string, method: string, init: RequestInit): Response | undefined {
  const parsed = new URL(url, 'http://localhost')
  const path = parsed.pathname
  if (method === 'GET' && path === '/api/user/english-essays') {
    return ok(pageOf(parsed.searchParams))
  }
  // 画像の上限（サーバーが決める。取れなければ画面は制限しない）
  if (method === 'GET' && path === '/api/user/english-essays/limits') {
    return limits === null
      ? serverError('画像の上限を取得できませんでした。')
      : ok(limits)
  }
  if (method === 'POST' && path === '/api/user/english-essays') {
    const body = JSON.parse(String(init.body)) as Record<string, string>
    sequence += 1
    const created: FakeEssay = {
      essayId: `essay-new-${sequence}`,
      level: body.level ?? 'PRE1',
      title: body.title ?? '',
      titleZh: body.titleZh ?? '',
      questionText: body.questionText ?? '',
      essayText: body.essayText ?? '',
      wordCount: (body.essayText ?? '').split(/\s+/).filter(Boolean).length,
      stateCode: 'A',
      createdAt: '2026-09-27T10:00:00.000Z',
      updatedAt: '2026-09-27T10:00:00.000Z',
      images: [],
      gradings: []
    }
    db = [created, ...db]
    return ok(created)
  }
  if (method === 'POST' && /^\/api\/user\/english-essays\/[^/]+\/images$/.test(path)) {
    const essay = db.find((item) => path.includes(item.essayId))
    const form = init.body as FormData
    const file = form.get('file') as File
    const order = Number(form.get('order') ?? 1)
    // 本物のサーバーと同じ約束を守らせる: 同じ表示順は弾く／上限枚数も弾く
    // （消した画像の行は PUT まで残るので、枠を先に空けないと重なる）
    if (essay !== undefined && essay.images.some((item) => item.order === order)) {
      return validationError(`表示順 ${order} は既に使われています。`)
    }
    if (essay !== undefined && essay.images.length >= 8) {
      return validationError('画像は 8 枚までです。')
    }
    sequence += 1
    const uploaded: FakeImage = {
      imageId: `${900 + sequence}`,
      order,
      category: String(form.get('category') ?? 'question'),
      originalFileName: file.name,
      mimeType: file.type,
      fileSize: file.size,
      recognizedText: null,
      confidence: null
    }
    essay?.images.push(uploaded)
    return ok(uploaded)
  }
  const detail = /^\/api\/user\/english-essays\/([^/]+)$/.exec(path)
  if (detail) {
    const essayId = decodeURIComponent(detail[1]!)
    const found = db.find((item) => item.essayId === essayId)
    if (method === 'GET') {
      return found ? ok(found) : notFound()
    }
    if (method === 'PUT' && found) {
      const body = JSON.parse(String(init.body)) as {
        level: string; title: string; titleZh: string; questionText: string; essayText: string
        images: { imageId: string; category: string; order: number }[]
      }
      found.level = body.level
      found.title = body.title
      found.titleZh = body.titleZh
      found.questionText = body.questionText
      found.essayText = body.essayText
      found.wordCount = body.essayText.split(/\s+/).filter(Boolean).length
      // 残す画像だけを、送られた並び・区分にする
      found.images = body.images
        .map((entry) => {
          const existing = found!.images.find((item) => item.imageId === entry.imageId)
          return existing === undefined
            ? null
            : { ...existing, category: entry.category, order: entry.order }
        })
        .filter((item): item is FakeImage => item !== null)
      found.updatedAt = '2026-09-27T11:00:00.000Z'
      return ok(found)
    }
    if (method === 'DELETE') {
      db = db.filter((item) => item.essayId !== essayId)
      return new Response(null, { status: 204 })
    }
  }
  if (method === 'POST' && path === '/api/user/english-essays/ocr') {
    return ok({
      questionText: QUESTION,
      essayText: ESSAY,
      pages: [
        { category: 'question', text: QUESTION, confidence: 96 },
        { category: 'answer', text: ESSAY, confidence: 91 }
      ],
      questionConfidence: 96,
      essayConfidence: 91
    })
  }
  const accepted = /^\/api\/user\/english-essays\/([^/]+)\/gradings$/.exec(path)
  if (method === 'POST' && accepted) {
    const essay = db.find((item) => item.essayId === decodeURIComponent(accepted[1]!))
    if (essay === undefined) {
      // 削除済み・他人の作文（admin-api は呼ばない）
      return notFound()
    }
    const body = JSON.parse(String(init.body)) as { round?: number }
    const round = body.round ?? essay.gradings.length + 1
    essay.gradings.push({
      ...grading(round, 0, 32),
      statusCode: 'QUEUED',
      score: null,
      maxScore: null,
      report: null
    })
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

/** 送った呼び出し（URL・メソッド）。 */
function sentUrls(fetchMock: ReturnType<typeof vi.fn>): string[] {
  return fetchMock.mock.calls.map((call) => `${(call[1] as RequestInit | undefined)?.method ?? 'GET'} ${String(call[0])}`)
}

function detailRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: defineComponent({ render: () => null }) },
      { path: '/admin/english-essay', component: defineComponent({ render: () => null }) },
      { path: '/parent/english-essay', component: defineComponent({ render: () => null }) },
      { path: '/admin/english-essay/new', component: defineComponent({ render: () => null }) },
      { path: '/admin/english-essay/:essayId/edit', component: defineComponent({ render: () => null }) },
      { path: '/admin/english-essay/:essayId', component: defineComponent({ render: () => null }) },
      { path: '/:pathMatch(.*)*', component: defineComponent({ render: () => null }) }
    ]
  })
}

/** 一覧の行（`data-ee-row`）。 */
function rows(wrapper: VueWrapper): DOMWrapper<Element>[] {
  return wrapper.findAll('[data-ee-row]')
}

/** 画面の DOM を取る。 */
function el(selector: string): HTMLElement {
  const found = document.querySelector<HTMLElement>(selector)
  if (!found) throw new Error(`要素がありません: ${selector}`)
  return found
}

/** DOM の値を変えてから、Vue の更新とマイクロタスクを進める。 */
async function settle(): Promise<void> {
  await nextTick()
  await flushPromises()
  await nextTick()
}

/** 見本の作文をタイトルで 1 件引く（偽のサーバーの DB から）。 */
function seeded(title: string): FakeEssay {
  const essay = db.find((item) => item.title === title)
  if (!essay) throw new Error(`見本の英作文がありません: ${title}`)
  return essay
}

/**
 * ページングを見るために見本を足す。
 *
 * <p>**古い日付**にするので、既存の 4 件のうしろに並ぶ（1 ページ目の中身が変わらない）。</p>
 */
function seedEssays(count: number): void {
  const template = seeded('AI と教育（未添削）')
  const added: FakeEssay[] = []
  for (let index = 1; index <= count; index += 1) {
    added.push({
      ...template,
      essayId: `essay-many-${index}`,
      title: `追加の英作文 ${index}`,
      createdAt: `2026-08-01T00:00:${String(index % 60).padStart(2, '0')}.000Z`,
      updatedAt: '2026-08-01T00:00:00.000Z',
      images: [],
      gradings: []
    })
  }
  db = [...db, ...added]
}

/** 文字入力（`v-model` を DOM から動かす）。 */
async function setInput(selector: string, value: string): Promise<void> {
  const input = el(selector) as HTMLInputElement | HTMLTextAreaElement
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await settle()
}

/** セレクト（`v-model` を DOM から動かす）。 */
async function setSelect(selector: string, value: string): Promise<void> {
  const select = el(selector) as HTMLSelectElement
  select.value = value
  select.dispatchEvent(new Event('change', { bubbles: true }))
  await settle()
}

/** 各テストで 1 回だけ組み立てる。 */
let editorWrapper: VueWrapper
let editorRouter: Router
let fetchMock: ReturnType<typeof vi.fn>

beforeEach(async () => {
  db = seedDb()
  sequence = 0
  limits = { maxImages: 8, maxImageMb: 10 }
  resetEssays()
  useToast().items.splice(0)
  fetchMock = installServer()

  const router = detailRouter()
  await router.push('/admin/english-essay')
  await router.isReady()
  editorRouter = router
  editorWrapper = mount(EssayListView as Component, {
    global: { plugins: [router] },
    // 画面の要素を `document` から取るので、body に取り付けて見る
    attachTo: document.body
  })
  await settle()
})

afterEach(() => {
  editorWrapper?.unmount()
  vi.unstubAllGlobals()
  document.body.innerHTML = ''
})

describe('英作文AI添削の一覧', () => {
  it('API の行を出す（画像枚数・題・級・語数・得点・状態・登録日）', async () => {
    await settle()
    const titles = editorWrapper.findAll('.ee-row-title').map((cell) => cell.text())
    expect(titles).toContain('小学生からの英語教育')
    expect(titles).toContain('読書と動画の学習効果')
    expect(titles).toContain('部活動の時間を増やすべきか')

    const essay = seeded('小学生からの英語教育')
    const row = editorWrapper.get(`[data-ee-row="${essay.essayId}"]`)
    const latest = essay.gradings[essay.gradings.length - 1]!
    expect(row.text()).toContain(ESSAY_LEVEL_LABELS.GRADE2)
    expect(row.text()).toContain(String(essay.wordCount))
    expect(row.text()).toContain(`${latest.score} / ${latest.maxScore}`)
    expect(row.text()).toContain('設問 1 / 答案 1')
    expect(row.text()).toContain('2026/09/20')
    // 操作は 目・編集・削除 の 3 つ
    expect(row.find('[data-ee-view]').exists()).toBe(true)
    expect(row.find('[data-ee-edit]').exists()).toBe(true)
    expect(row.find('[data-ee-delete]').exists()).toBe(true)
  })

  it('得点と状態は latestGrading で決める（未添削・添削中・失敗）', async () => {
    const ungraded = seeded('AI と教育（未添削）')
    const ungradedRow = editorWrapper.get(`[data-ee-row="${ungraded.essayId}"]`)
    expect(ungradedRow.text()).toContain('—')
    expect(ungradedRow.text()).toContain('未添削')

    // 添削中（RUNNING）と失敗（FAILED）は一覧で見分けられる
    db = [
      ...db,
      {
        ...ungraded,
        essayId: 'essay-5',
        title: '添削中の英作文',
        createdAt: '2026-09-27T07:00:00.000Z',
        gradings: [{ ...grading(1, 0), statusCode: 'RUNNING', score: null, maxScore: null }]
      },
      {
        ...ungraded,
        essayId: 'essay-6',
        title: '失敗した英作文',
        createdAt: '2026-09-27T06:00:00.000Z',
        gradings: [{ ...grading(1, 0), statusCode: 'FAILED', score: null, maxScore: null }]
      }
    ]
    await editorWrapper.vm.$forceUpdate()
    // 読み直して、サーバーの最新を出す
    await editorWrapper.get('[data-ee-search]').trigger('click')
    await settle()

    expect(editorWrapper.get('[data-ee-row="essay-5"]').text()).toContain('添削中')
    expect(editorWrapper.get('[data-ee-row="essay-5"]').text()).toContain('—')
    expect(editorWrapper.get('[data-ee-row="essay-6"]').text()).toContain('失敗')
  })

  it('満点が分からない回（古い形式）の得点は、点数だけを出す（/ — を出さない）', async () => {
    const target = seeded('小学生からの英語教育')
    target.gradings = [{ ...grading(1, 21, null), maxScore: null }]
    await editorWrapper.get('[data-ee-search]').trigger('click')
    await settle()

    const row = editorWrapper.get(`[data-ee-row="${target.essayId}"]`)
    expect(row.text()).toContain('21')
    expect(editorWrapper.text()).not.toContain('21 / —')
  })

  it('絞り込みとページングはサーバー側（クエリで送る）', async () => {
    await settle()
    await setInput('[data-ee-filter="keyword"]', '部活動')
    await editorWrapper.get('[data-ee-search]').trigger('click')
    await settle()

    const urls = fetchMock.mock.calls.map((call) => String(call[0]))
    expect(urls.some((url) => url.includes('keyword='))).toBe(true)
    expect(rows(editorWrapper)).toHaveLength(1)

    // 英検級でも絞れる（サーバーが返した行だけを出す）
    await setInput('[data-ee-filter="keyword"]', '')
    await setSelect('[data-ee-filter="level"]', 'GRADE1')
    await editorWrapper.get('[data-ee-search]').trigger('click')
    await settle()

    expect(rows(editorWrapper).map((row) => row.text()).join(' ')).toContain('部活動の時間を増やすべきか')
    expect(rows(editorWrapper)).toHaveLength(1)

    // リセットで全件に戻る
    await editorWrapper.get('[data-ee-reset]').trigger('click')
    await settle()
    expect(rows(editorWrapper)).toHaveLength(4)
  })

  it('登録日の範囲でも絞れる（サーバー側の条件）', async () => {
    await setInput('[data-ee-filter="dateFrom"]', '2026-09-24')
    await setInput('[data-ee-filter="dateTo"]', '2026-09-26')
    await editorWrapper.get('[data-ee-search]').trigger('click')
    await settle()

    expect(rows(editorWrapper)).toHaveLength(2)
    const titles = editorWrapper.findAll('.ee-row-title').map((cell) => cell.text())
    expect(titles).toContain('部活動の時間を増やすべきか')
    expect(titles).toContain('AI と教育（未添削）')
  })

  it('1 ページの件数は 20/50/100 から選べ、選んだ値をそのまま送る', async () => {
    const options = editorWrapper.findAll('[data-ee-page-size] option').map((option) => option.text())
    expect(options).toEqual(['20 件', '50 件', '100 件'])
    expect((editorWrapper.get('[data-ee-page-size]').element as HTMLSelectElement).value).toBe('20')

    await setSelect('[data-ee-page-size]', '50')
    const urls = fetchMock.mock.calls.map((call) => String(call[0]))
    expect(urls[urls.length - 1]).toContain('size=50')
  })

  it('件数を変えると 1 ページ目から読み直す', async () => {
    seedEssays(30)
    await editorWrapper.get('[data-ee-search]').trigger('click')
    await settle()

    // 2 ページ目へ
    await editorWrapper.findAll('.page-btn')[2]!.trigger('click')
    await settle()
    expect(editorWrapper.get('.pagination__info').text()).toContain('2 /')

    // 件数を変えたら 1 ページ目に戻る
    await setSelect('[data-ee-page-size]', '50')
    expect(editorWrapper.get('.pagination__info').text()).toContain('1 /')
  })

  it('2 ページ目はサーバーから取り直す（No. はページを通した番号）', async () => {
    seedEssays(30)
    await editorWrapper.get('[data-ee-search]').trigger('click')
    await settle()

    await editorWrapper.findAll('.page-btn')[2]!.trigger('click')
    await settle()

    const urls = fetchMock.mock.calls.map((call) => String(call[0]))
    expect(urls[urls.length - 1]).toContain('page=2')
    // 21 件目から（1 ページ目の 20 件の続き）
    expect(editorWrapper.findAll('tbody tr')[0]!.text()).toContain('21')
  })

  it('行が 1 件だけでもページングを出す（件数は変えられる）', async () => {
    await setInput('[data-ee-filter="keyword"]', '部活動')
    await editorWrapper.get('[data-ee-search]').trigger('click')
    await settle()

    expect(rows(editorWrapper)).toHaveLength(1)
    expect(editorWrapper.find('.pagination').exists()).toBe(true)
    expect(editorWrapper.get('.pagination__info').text()).toContain('全 1 件')
  })

  it('ページ番号は多いとき省略する（‹ 1 2 3 4 5 … N ›）', async () => {
    await settle()
    // 7 ページを超えると、先頭と末尾の間が省略記号になる（`paginationItems`）
    seedEssays(140)
    await editorWrapper.get('[data-ee-search]').trigger('click')
    await settle()

    const pages = editorWrapper.findAll('.pagination__pages')[0]!
    const labels = pages.findAll('.page-btn').map((button) => button.text())
    expect(labels[0]).toBe('‹')
    expect(labels[labels.length - 1]).toBe('›')
    expect(pages.findAll('.page-gap').length).toBeGreaterThanOrEqual(1)

    // 後ろのページへ移ると、前側が省略される
    await pages.findAll('.page-btn')[6]!.trigger('click')
    await settle()
    const last = editorWrapper.findAll('.pagination__pages')[0]!
    expect(last.findAll('.page-btn').map((button) => button.text())).toEqual(['‹', '1', '4', '5', '6', '7', '8', '›'])
  })

  it('【英作文新規】は、モーダルではなく新規のページへ進む', async () => {
    await settle()
    await editorWrapper.get('[data-ee-new]').trigger('click')
    await settle()

    expect(editorRouter.currentRoute.value.path).toBe('/admin/english-essay/new')
    // 窓は開かない（ページ遷移）
    expect(document.querySelector('[data-ee-editor]')).toBeNull()
  })

  it('行の【編集】は、その作文の編集ページへ進む', async () => {
    const target = seeded('小学生からの英語教育')
    await editorWrapper.get(`[data-ee-row="${target.essayId}"] [data-ee-edit]`).trigger('click')
    await settle()

    expect(editorRouter.currentRoute.value.path).toBe(`/admin/english-essay/${target.essayId}/edit`)
    expect(document.querySelector('[data-ee-editor]')).toBeNull()
  })

  it('親エリアでは【英作文新規】も【編集】も出さない（閲覧だけ）', async () => {
    const router = detailRouter()
    await router.push('/parent/english-essay')
    await router.isReady()
    const wrapper = mount(EssayListView as Component, { global: { plugins: [router] } })
    await settle()

    expect(wrapper.find('[data-ee-new]').exists()).toBe(false)
    expect(wrapper.find('[data-ee-edit]').exists()).toBe(false)
    expect(wrapper.find('[data-ee-view]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('目のアイコンで詳細ルートへ遷移する', async () => {
    const target = seeded('読書と動画の学習効果')
    await editorWrapper.get(`[data-ee-row="${target.essayId}"] [data-ee-view]`).trigger('click')
    await settle()

    expect(editorRouter.currentRoute.value.path).toBe(`/admin/english-essay/${target.essayId}`)
  })

  it('削除は確認のうえで DELETE を送り、一覧から消える', async () => {
    const target = seeded('部活動の時間を増やすべきか')
    const confirm = vi.fn(() => true)
    vi.stubGlobal('confirm', confirm)

    await editorWrapper.get(`[data-ee-row="${target.essayId}"] [data-ee-delete]`).trigger('click')
    await settle()

    expect(confirm).toHaveBeenCalled()
    expect(sentUrls(fetchMock)).toContain(`DELETE /api/user/english-essays/${target.essayId}`)
    expect(db.map((item) => item.essayId)).not.toContain(target.essayId)
    expect(editorWrapper.find(`[data-ee-row="${target.essayId}"]`).exists()).toBe(false)
    expect(useToast().items.map((item) => item.message).join(' ')).toContain('削除')
  })

  it('一覧は store のキャッシュに載る（画面は API を直接触らない）', () => {
    expect(essayList.value.map((essay) => essay.id)).toHaveLength(4)
  })
})

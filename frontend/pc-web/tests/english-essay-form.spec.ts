/* eslint-disable vue/one-component-per-file -- テストの中で窓の代役を作るため */
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, h, nextTick, type Component } from 'vue'
import { createMemoryHistory, createRouter, RouterView, type Router } from 'vue-router'
import { useToast } from '@study21/web-shared'
import EssayNewView from '@/views/english/EssayNewView.vue'
import { resetEssays } from '@/features/english-essay/store'

/** 新規・編集ページの見た目の定義（jsdom は CSS を当てないので定義そのものを確かめる）。 */
function readPageCss(): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/features/english-essay/essay-page.css'), 'utf8')
}

/** 英作文の共通部品（`components/*.vue`）のソース（同じくスタイルの定義を確かめる）。 */
function readComponentSource(name: string): string {
  const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
  return readFileSync(path.join(webRoot, 'src/features/english-essay/components', name), 'utf8')
}

/** 2.0 の `grid-template-areas` が名付けた領域（2.1 は 02 と 03 を左の柱にまとめるので `main`）。 */
const FORM_AREAS = ['hero', 'steps', 'side', 'main'] as const

/** 2.0 の新規画面の 5 つの節（`data-ee-region`。02 と 03 は左の柱の中に入る）。 */
const FORM_REGIONS = ['hero', 'steps', 'side', 'recognition', 'result'] as const

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
 * 英作文AI添削【新規登録／編集】のページ（2.0 の `english_essay.jsp?mode=new` 相当）。
 *
 * <p>見るのは**公開の振る舞い**だけ: 2.0 の新規画面の節と項目（見出し・3 ステップ・
 * 画像を追加・AI文字認識・AI添削レポート）、1 枚ずつ必ずトリミング窓を通すこと、区分ごとの
 * 信頼度と参照画像、設問・本文の拡大編集、保存（POST／PUT ＋ 画像アップロード）、
 * 添削の受付と詳細への遷移、【一覧へ戻る】と**未保存の確認**。</p>
 *
 * <p>API は `fetch` でスタブする（`tests/english-essay-list.spec.ts` と同じ作り）。</p>
 */

/** トリミング窓の代役（【この範囲で保存】を押すと `confirm` を返す）。 */
const CropStub = defineComponent({
  name: 'EssayCropDialog',
  props: {
    open: { type: Boolean, default: false },
    src: { type: String, default: '' },
    fileName: { type: String, default: '' }
  },
  emits: ['confirm', 'cancel'],
  setup(props, { emit }) {
    return () => h('div', { 'data-crop-stub': '' }, [
      h('button', {
        type: 'button',
        'data-crop-stub-apply': '',
        disabled: !props.open,
        onClick: () => emit('confirm', { dataUrl: 'data:image/png;base64,TRIM', fileName: props.fileName })
      }, 'この範囲で保存')
    ])
  }
})

const FAKE_DATA_URL = 'data:image/png;base64,FAKE'

/** FileReader はすぐ終わる（テストで待ち時間を作らない）。 */
function stubFileReader(): void {
  class InstantFileReader {
    result: string | ArrayBuffer | null = null
    error: unknown = null
    onload: (() => void) | null = null
    onerror: (() => void) | null = null
    readAsDataURL(): void {
      this.result = FAKE_DATA_URL
      this.onload?.()
    }
  }
  vi.stubGlobal('FileReader', InstantFileReader)
}

function imageFile(name = 'answer.png'): File {
  return new File([new Uint8Array([1, 2, 3])], name, { type: 'image/png' })
}

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

/** 画像 1 枚。 */
function image(imageId: string, category: string, order: number, originalFileName: string): FakeImage {
  return {
    imageId, order, category, originalFileName,
    mimeType: 'image/png', fileSize: 1024, recognizedText: null, confidence: null
  }
}

/** DB の初期状態（見本の 2 篇。1 篇は未添削）。 */
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
      gradings: []
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
      images: [image('21', 'question', 1, 'essay-2-q1.png')],
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

function imageResponse(): Response {
  return new Response(new Uint8Array([1, 2, 3]), { status: 200, headers: { 'Content-Type': 'image/png' } })
}

/** 偽のサーバー（未定義の呼び出しは失敗させる）。 */
function serverFetch(url: string, method: string, init: RequestInit): Response | undefined {
  const parsed = new URL(url, 'http://localhost')
  const path = parsed.pathname
  if (url.startsWith('data:') || url === '/content/7') {
    return imageResponse()
  }
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
      return notFound()
    }
    const body = JSON.parse(String(init.body)) as { round?: number }
    const round = body.round ?? essay.gradings.length + 1
    essay.gradings.push({
      gradingId: `${100 + round}`,
      round,
      statusCode: 'QUEUED',
      level: essay.level,
      titleJa: null,
      titleZh: null,
      questionText: null,
      essayText: null,
      wordCount: null,
      score: null,
      maxScore: null,
      report: null,
      failureReason: null,
      startedAt: null,
      finishedAt: null,
      createdAt: '2026-09-27T12:00:00.000Z'
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

/* ---------- 画面の操作 ---------- */

/**
 * 新規／編集のページを載せるルーター。
 *
 * <p>`EssayNewView` は **router-view の中**に置く（`onBeforeRouteLeave` は router-view の
 * 下でしか効かない＝実際のアプリと同じ形で見る）。</p>
 */
function formRouter(): Router {
  const Blank = defineComponent({ render: () => null })
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/admin/english-essay', name: 'list', component: Blank },
      { path: '/admin/english-essay/new', name: 'new', component: EssayNewView },
      { path: '/admin/english-essay/:essayId/edit', name: 'edit', component: EssayNewView },
      { path: '/admin/english-essay/:essayId', name: 'detail', component: Blank },
      { path: '/:pathMatch(.*)*', component: Blank }
    ]
  })
}

/** 画面を載せる親（router-view を出すだけ）。 */
const RouterHost = defineComponent({
  name: 'RouterHost',
  setup() {
    return () => h(RouterView)
  }
})

/** 画面の DOM を取る（ページなので Teleport は無い）。 */
function el(wrapper: VueWrapper, selector: string): HTMLElement {
  const found = (wrapper.element as HTMLElement).querySelector<HTMLElement>(selector)
  if (!found) throw new Error(`要素がありません: ${selector}`)
  return found
}

function els(wrapper: VueWrapper, selector: string): HTMLElement[] {
  return Array.from((wrapper.element as HTMLElement).querySelectorAll<HTMLElement>(selector))
}

/** body へ Teleport された窓の中（臨時ファイルのピッカーなど）から取る。 */
function inBody(selector: string): HTMLElement {
  const found = document.querySelector<HTMLElement>(selector)
  if (!found) throw new Error(`要素がありません（body）: ${selector}`)
  return found
}

/** DOM の値を変えてから、Vue の更新とマイクロタスクを進める。 */
async function settle(): Promise<void> {
  await nextTick()
  await flushPromises()
  await nextTick()
}

async function setInput(wrapper: VueWrapper, selector: string, value: string): Promise<void> {
  const input = el(wrapper, selector) as HTMLInputElement | HTMLTextAreaElement
  input.value = value
  input.dispatchEvent(new Event('input', { bubbles: true }))
  await settle()
}

async function setSelect(wrapper: VueWrapper, selector: string, value: string): Promise<void> {
  const select = el(wrapper, selector) as HTMLSelectElement
  select.value = value
  select.dispatchEvent(new Event('change', { bubbles: true }))
  await settle()
}

/** n 枚目の画像の区分を切り替える（設問と答案を並べるとき）。 */
async function setCategoryAt(wrapper: VueWrapper, index: number, value: string): Promise<void> {
  const select = els(wrapper, '[data-ee-image-category]')[index] as HTMLSelectElement | undefined
  if (!select) throw new Error(`${index + 1} 枚目の画像がありません`)
  select.value = value
  select.dispatchEvent(new Event('change', { bubbles: true }))
  await settle()
}

async function setCategory(wrapper: VueWrapper, value: string): Promise<void> {
  await setCategoryAt(wrapper, 0, value)
}

/** ドロップゾーンのファイル入力へ 1 枚渡すだけ（トリミング窓は通さない）。 */
async function pickImage(wrapper: VueWrapper, file: File): Promise<void> {
  const input = el(wrapper, '[data-ee-file-input]') as HTMLInputElement
  Object.defineProperty(input, 'files', { value: [file], configurable: true })
  input.dispatchEvent(new Event('change', { bubbles: true }))
  await settle()
}

/** ドロップゾーンのファイル入力へ 1 枚渡し、トリミング窓（の代役）を通す。 */
async function addImage(wrapper: VueWrapper, name = 'answer.png'): Promise<void> {
  await pickImage(wrapper, imageFile(name))
  el(wrapper, '[data-crop-stub-apply]').click()
  await settle()
}

/** 新規のページを載せる（router-view の中に置く）。 */
async function openNew(): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = formRouter()
  await router.push('/admin/english-essay')
  await router.isReady()
  const wrapper = mount(RouterHost as Component, {
    global: { plugins: [router], stubs: { EssayCropDialog: CropStub } },
    // 拡大編集や臨時ファイルの窓は body へ Teleport されるので、body に取り付けて見る
    attachTo: document.body
  })
  await router.push('/admin/english-essay/new')
  await settle()
  return { wrapper, router }
}

/** 編集のページを載せる（`essayId` の作文を読み込む）。 */
async function openEdit(essayId: string): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = formRouter()
  await router.push('/admin/english-essay')
  await router.isReady()
  const wrapper = mount(RouterHost as Component, {
    global: { plugins: [router], stubs: { EssayCropDialog: CropStub } },
    attachTo: document.body
  })
  await router.push(`/admin/english-essay/${essayId}/edit`)
  await settle()
  return { wrapper, router }
}

let fetchMock: ReturnType<typeof vi.fn>

beforeEach(() => {
  db = seedDb()
  sequence = 0
  limits = { maxImages: 8, maxImageMb: 10 }
  resetEssays()
  useToast().items.splice(0)
  stubFileReader()
  fetchMock = installServer()
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.body.innerHTML = ''
})

describe('英作文AI添削の新規・編集ページ', () => {
  it('2.0 の新規画面の節と項目を、この順に出す', async () => {
    const { wrapper } = await openNew()

    expect(wrapper.find('[data-ee-form]').exists()).toBe(true)
    // 見出しと説明（2.0 の hero）
    expect(wrapper.get('[data-ee-form-title]').text()).toContain('英作文 新規登録')
    expect(wrapper.text()).toContain('画像を追加し、AI文字認識・英作文添削を行って保存します。')
    // 3 ステップ（2.0 の stepper）
    expect(wrapper.get('[data-ee-stepper]').text()).toContain('1画像アップロード問題・答案を追加')
    expect(wrapper.get('[data-ee-stepper]').text()).toContain('2文字認識・確認OCR結果を修正')
    expect(wrapper.get('[data-ee-stepper]').text()).toContain('3AI添削評価と改善案')
    // 01 画像を追加 → 02 AI文字認識 → 03 AI添削レポート
    expect(wrapper.get('[data-ee-card="upload"]').text()).toContain('画像を追加')
    expect(wrapper.get('[data-ee-card="upload"]').text()).toContain('問題用紙と答案用紙をまとめて選択できます')
    expect(wrapper.get('[data-ee-card="recognition"]').text()).toContain('AI文字認識')
    expect(wrapper.get('[data-ee-card="recognition"]').text()).toContain('AIが「作文の設問」と「あなたの作文」を分けて抽出します')
    expect(wrapper.get('[data-ee-card="result"]').text()).toContain('AI添削レポート')
    expect(wrapper.get('[data-ee-card="result"]').text()).toContain('設問への適合度、内容、構成、語彙・文法を総合評価')
    // 画面の骨格は 2.0 と同じ 5 つの節（`data-ee-region`）
    for (const region of FORM_REGIONS) {
      expect(wrapper.find(`[data-ee-region="${region}"]`).exists()).toBe(true)
    }
    // モーダルではなくページ（窓の骨格を出さない）
    expect(wrapper.find('.dialog').exists()).toBe(false)
    // 保存は英雄の右（2.0 の hero-actions）。足元は置かず、【キャンセル】も無い
    // （利用者の指示。離れる導線は英雄の【一覧へ戻る】とブラウザの戻る）
    expect(wrapper.get('.ee-hero__actions [data-ee-save]').text()).toContain('保存')
    expect(wrapper.get('.ee-hero__actions [data-ee-list-back]').text()).toContain('一覧へ戻る')
    expect(wrapper.find('.ee-form__foot').exists()).toBe(false)
    expect(wrapper.find('[data-ee-cancel]').exists()).toBe(false)
  })

  it('英検級は 03 AI添削レポートの中（題の 2 欄より前）に出す', async () => {
    const { wrapper } = await openNew()

    // 02 AI文字認識 には置かない
    expect(el(wrapper, '[data-ee-card="recognition"]').querySelector('[data-ee-level]')).toBeNull()

    // 03 AI添削レポート の中にあり、題の 2 欄より前に出る（読む順は 級 → 日本語題 → 中文題）
    const result = el(wrapper, '[data-ee-card="result"]')
    expect(result.querySelector('[data-ee-level]')).not.toBeNull()
    const fields = [...result.querySelectorAll('[data-ee-level], [data-ee-title], [data-ee-title-zh]')]
    const order = fields.map((node) => {
      if (node.hasAttribute('data-ee-level')) return 'level'
      return node.hasAttribute('data-ee-title') ? 'title-ja' : 'title-zh'
    })
    expect(order).toEqual(['level', 'title-ja', 'title-zh'])
  })

  it('語数の目安の欄は出さない（手書き作文の words の数え方だけ残す）', async () => {
    const { wrapper } = await openNew()

    // 02 の読み取り専用の欄は消す（利用者の指示）
    expect(wrapper.text()).not.toContain('語数の目安')
    expect(wrapper.find('[data-ee-form-words]').exists()).toBe(false)
    // 本文の語数（手書き作文の足元）は残す
    expect(el(wrapper, '[data-ee-word-count]').textContent).toContain('words')
  })

  it('編集で開くと、前回の得点を英雄の右（前回の得点を出す場所）に出す', async () => {
    const target = db[0]!
    target.gradings.push({
      gradingId: 'g-1',
      round: 1,
      statusCode: 'SUCCEEDED',
      level: target.level,
      titleJa: null,
      titleZh: null,
      questionText: null,
      essayText: null,
      wordCount: null,
      score: 13,
      maxScore: 16,
      report: null,
      failureReason: null,
      startedAt: null,
      finishedAt: null,
      createdAt: '2026-09-24T09:00:00.000Z'
    })

    const { wrapper } = await openEdit(target.essayId)

    // 出る場所は英雄の右（足元は無い）。操作（【一覧へ戻る】【保存】）の隣に並ぶ
    const note = wrapper.get('.ee-hero__actions [data-ee-previous-score]')
    expect(note.text()).toBe('前回の得点: 13 / 16')
    expect(wrapper.find('.ee-form__foot').exists()).toBe(false)
    // 英検級の位置は編集でも同じ（新規・編集は同じ EssayForm を共有している）
    expect(el(wrapper, '[data-ee-card="result"]').querySelector('[data-ee-level]')).not.toBeNull()
  })

  it('題は日本語題と中文題の 2 欄で、どちらも保存する', async () => {
    const before = db.length
    const { wrapper } = await openNew()

    expect(el(wrapper, '[data-ee-title]')).toBeTruthy()
    expect(el(wrapper, '[data-ee-title-zh]')).toBeTruthy()

    await setInput(wrapper, '[data-ee-title]', '朝の読書の効果')
    await setInput(wrapper, '[data-ee-title-zh]', '晨读的效果')
    ;(el(wrapper, '[data-ee-save]') as HTMLButtonElement).click()
    await settle()

    expect(db.length).toBe(before + 1)
    const created = db.find((item) => item.title === '朝の読書の効果')!
    expect(created.titleZh).toBe('晨读的效果')
  })

  it('画像 0 枚では【AIで画像を読み取る】を押せない', async () => {
    const { wrapper } = await openNew()
    expect((el(wrapper, '[data-ee-ocr]') as HTMLButtonElement).disabled).toBe(true)
    expect(el(wrapper, '[data-ee-image-count]').textContent).toContain('0 / 8枚')
    expect(el(wrapper, '[data-ee-ocr-status]').textContent).toContain('画像待ち')
  })

  it('画像を 1 枚渡すと（トリミング窓を通って）カテゴリを選べる', async () => {
    const { wrapper } = await openNew()

    // トリミング窓を経由するまで一覧には並ばない
    await pickImage(wrapper, imageFile())
    expect(wrapper.find('[data-ee-image]').exists()).toBe(false)

    el(wrapper, '[data-crop-stub-apply]').click()
    await settle()

    const card = el(wrapper, '[data-ee-image]')
    expect(card.querySelector('img')?.getAttribute('src')).toContain('data:image/png')
    const category = card.querySelector('[data-ee-image-category]') as HTMLSelectElement
    expect(Array.from(category.options).map((option) => option.textContent)).toEqual(['設問画像', '答案画像'])
    await setCategory(wrapper, 'answer')
    expect((el(wrapper, '[data-ee-image-category]') as HTMLSelectElement).value).toBe('answer')
    // 画像があるので読み取れる
    expect((el(wrapper, '[data-ee-ocr]') as HTMLButtonElement).disabled).toBe(false)
  })

  it('【AIで画像を読み取る】は user-api へ送り、設問と本文（編集できる）が入る', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper)
    await setCategory(wrapper, 'answer')

    ;(el(wrapper, '[data-ee-ocr]') as HTMLButtonElement).click()
    await settle()

    // 画像の実体（区分はファイルと同じ順）を user-api へ送る（admin-api は画面から叩かない）
    expect(sentUrls(fetchMock)).toContain('POST /api/user/english-essays/ocr')

    const essay = el(wrapper, '[data-ee-essay-text]') as HTMLTextAreaElement
    expect(essay.value.length).toBeGreaterThan(0)
    expect(essay.disabled).toBe(false)
    expect(wrapper.text()).toContain('認識後に編集できます')
    expect(wrapper.text()).toContain('誤認識があれば、添削前にテキストを直接修正してください。')
    expect(wrapper.text()).toMatch(/信頼度 \d+%/)
    // 保存するまで作文は作らない（キャンセルしたら残らない）
    expect(db).toHaveLength(2)
  })

  it('信頼度は区分ごとに出す（画像が無い区分は出さない）', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper, 'question.png')
    await setCategory(wrapper, 'question')

    ;(el(wrapper, '[data-ee-ocr]') as HTMLButtonElement).click()
    await settle()

    // 設問画像だけなので、設問には信頼度が出て、手書き作文には出ない
    expect(el(wrapper, '[data-ee-question-confidence]').textContent).toMatch(/信頼度 \d+%/)
    expect(el(wrapper, '[data-ee-essay-confidence]').textContent).not.toMatch(/信頼度/)

    await addImage(wrapper, 'answer.png')
    await setCategoryAt(wrapper, 1, 'answer')
    ;(el(wrapper, '[data-ee-ocr]') as HTMLButtonElement).click()
    await settle()

    // 両方の区分に画像があれば、両方に信頼度が出る
    expect(el(wrapper, '[data-ee-question-confidence]').textContent).toMatch(/信頼度 \d+%/)
    expect(el(wrapper, '[data-ee-essay-confidence]').textContent).toMatch(/信頼度 \d+%/)
  })

  it('OCR パネルに、区分ごとの参照画像（サムネイルと枚数）を出す', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper, 'question.png')
    await addImage(wrapper, 'answer1.png')
    await setCategoryAt(wrapper, 1, 'answer')
    await addImage(wrapper, 'answer2.png')
    await setCategoryAt(wrapper, 2, 'answer')

    const sources = els(wrapper, '[data-ee-source]')
    // 1 つめ＝作文の設問、2 つめ＝手書き作文
    expect(sources).toHaveLength(2)
    expect(sources[0]!.querySelectorAll('img')).toHaveLength(1)
    expect(sources[0]!.textContent).toContain('1枚')
    expect(sources[1]!.querySelectorAll('img')).toHaveLength(2)
    expect(sources[1]!.textContent).toContain('2枚')
    expect(sources[0]!.querySelector('img')?.getAttribute('src')).toContain('data:image/png')
  })

  it('参照画像が無い区分は「参照画像なし」と出す', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper, 'answer.png')
    await setCategory(wrapper, 'answer')

    const sources = els(wrapper, '[data-ee-source]')
    expect(sources).toHaveLength(2)
    expect(sources[0]!.textContent).toContain('参照画像なし')
    expect(sources[0]!.querySelectorAll('img')).toHaveLength(0)
    expect(sources[1]!.querySelectorAll('img')).toHaveLength(1)
  })

  it('画像のサムネイルをクリックすると、拡大表示の窓が開く（2.0 の #essayImagePreviewModal）', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper, 'answer.png')

    // 開くまでは窓を出さない
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)

    await wrapper.get('[data-ee-image] img').trigger('click')
    await settle()

    const preview = wrapper.get('[data-ee-image-preview]')
    // 2.0 と同じく、見出しはファイル名（分からないときは「登録画像」）
    expect(preview.get('[data-ee-image-preview-title]').text()).toBe('answer.png')
    const opened = preview.get('[data-ee-image-preview-image]')
    // サムネイルと同じ実体（トリミング済みのデータ URL）をそのまま出す
    expect(opened.attributes('src')).toBe(wrapper.get('[data-ee-image] img').attributes('src'))
    expect(opened.attributes('alt')).toBe('answer.png')
  })

  it('画像の拡大表示は × と Esc で閉じる（背景クリックでは閉じない）', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper, 'answer.png')

    await wrapper.get('[data-ee-image-thumb]').trigger('click')
    await settle()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(true)

    // 背景（灰色の部分）を押しても閉じない（`docs/FRONTEND_GUIDE.md` 3.9）
    await wrapper.get('[data-ee-image-preview]').trigger('click')
    await settle()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(true)

    // 右上の ×
    await wrapper.get('[data-ee-image-preview-close]').trigger('click')
    await settle()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)

    // 開き直して Esc（足元の【閉じる】でも閉じられる）
    await wrapper.get('[data-ee-image-thumb]').trigger('click')
    await settle()
    await wrapper.get('[data-ee-image-preview-dismiss]').trigger('click')
    await settle()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)

    await wrapper.get('[data-ee-image-thumb]').trigger('click')
    await settle()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await settle()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)
  })

  it('画像の拡大を開いても、区分の切り替えと削除はこれまでどおり動く', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper, 'answer.png')
    await addImage(wrapper, 'question.png')

    // 区分の下拉を押しても拡大は開かない（拡大はサムネイルのクリックだけ）
    await wrapper.get('[data-ee-image-category]').trigger('click')
    await settle()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)

    // 拡大を開いて閉じる
    await wrapper.get('[data-ee-image-thumb]').trigger('click')
    await settle()
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(true)
    await wrapper.get('[data-ee-image-preview-close]').trigger('click')
    await settle()

    // 区分の切り替えはこれまでどおり効く
    await setCategory(wrapper, 'answer')
    expect((el(wrapper, '[data-ee-image-category]') as HTMLSelectElement).value).toBe('answer')

    // 削除もこれまでどおり効く（拡大は開かない）
    els(wrapper, '[data-ee-image] button[aria-label="画像を削除"]')[0]!.click()
    await settle()
    expect(els(wrapper, '[data-ee-image]')).toHaveLength(1)
    expect(wrapper.find('[data-ee-image-preview]').exists()).toBe(false)
  })

  it('設問と手書き作文は、窓で大きく編集して反映できる（背景クリックでは閉じない）', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper)
    await setCategory(wrapper, 'answer')
    ;(el(wrapper, '[data-ee-ocr]') as HTMLButtonElement).click()
    await settle()

    const zoomButtons = els(wrapper, '[data-ee-zoom-text]')
    expect(zoomButtons).toHaveLength(2)

    // 2 つめ＝手書き作文。長い本文を書ける
    zoomButtons[1]!.click()
    await settle()

    const zoom = inBody('[data-ee-text-zoom]')
    ;(zoom.querySelector('[data-ee-zoom-editor]') as HTMLTextAreaElement).value = '長い本文をここで書く'
    ;(zoom.querySelector('[data-ee-zoom-editor]') as HTMLTextAreaElement)
      .dispatchEvent(new Event('input', { bubbles: true }))
    await settle()
    const apply = Array.from(zoom.querySelectorAll('button'))
      .find((button) => (button.textContent ?? '').includes('内容を反映')) as HTMLButtonElement | undefined
    expect(apply).toBeTruthy()
    apply!.click()
    await settle()

    expect((el(wrapper, '[data-ee-essay-text]') as HTMLTextAreaElement).value).toBe('長い本文をここで書く')
  })

  it('拡大編集の【キャンセル】は反映しない（Esc でも閉じる）', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper)
    await setCategory(wrapper, 'answer')
    ;(el(wrapper, '[data-ee-ocr]') as HTMLButtonElement).click()
    await settle()

    const before = (el(wrapper, '[data-ee-question-text]') as HTMLTextAreaElement).value
    els(wrapper, '[data-ee-zoom-text]')[0]!.click()
    await settle()

    const editor = inBody('[data-ee-zoom-editor]') as HTMLTextAreaElement
    editor.value = 'この内容は反映されない'
    editor.dispatchEvent(new Event('input', { bubbles: true }))
    await settle()

    const cancel = Array.from(inBody('[data-ee-text-zoom]').querySelectorAll('button'))
      .find((button) => (button.textContent ?? '').includes('キャンセル')) as HTMLButtonElement | undefined
    expect(cancel).toBeTruthy()
    cancel!.click()
    await settle()

    expect(document.querySelector('[data-ee-text-zoom]')).toBeNull()
    expect((el(wrapper, '[data-ee-question-text]') as HTMLTextAreaElement).value).toBe(before)

    // Esc でも閉じられる（これも反映しない）
    els(wrapper, '[data-ee-zoom-text]')[0]!.click()
    await settle()
    ;(inBody('[data-ee-zoom-editor]') as HTMLTextAreaElement).value = 'これも反映されない'
    ;(inBody('[data-ee-zoom-editor]') as HTMLTextAreaElement).dispatchEvent(new Event('input', { bubbles: true }))
    await settle()
    window.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }))
    await settle()

    expect(document.querySelector('[data-ee-text-zoom]')).toBeNull()
    expect((el(wrapper, '[data-ee-question-text]') as HTMLTextAreaElement).value).toBe(before)
  })

  it('臨時ファイルから選んだ画像は（トリミング窓を通って）並ぶ', async () => {
    const { wrapper } = await openNew()

    ;(el(wrapper, '[data-ee-tempfiles]') as HTMLButtonElement).click()
    await settle()
    // サムネイルは data URL に直して出す（裸の base64 を吸収する）
    expect(inBody('.tf-card img').getAttribute('src')).toBe('data:image/png;base64,RAWTHUMB')
    const check = inBody('.tf-card input[type="checkbox"]') as HTMLInputElement
    check.click()
    await settle()
    ;(inBody('[data-ee-tempfile-pick]') as HTMLButtonElement).click()
    await settle()
    // 取り込んだ画像も、ドロップと同じくトリミング窓を通ってから並ぶ
    el(wrapper, '[data-crop-stub-apply]').click()
    await settle()

    expect(els(wrapper, '[data-ee-image]')).toHaveLength(1)
    expect(el(wrapper, '[data-ee-image] img').getAttribute('src')).toContain('data:image/png')
  })

  it('【この内容でAI添削】は作成 → 画像 → 更新 → 受付の順に送り、詳細へ遷移する', async () => {
    const before = db.length
    const { wrapper, router } = await openNew()
    await setSelect(wrapper, '[data-ee-level]', 'PRE1')
    await setInput(wrapper, '[data-ee-title]', '新しい英作文')
    // 設問と答案の 2 枚（設問が無いと添削は始められない）
    await addImage(wrapper, 'question.png')
    await setCategory(wrapper, 'question')
    await addImage(wrapper, 'answer.png')
    await setCategoryAt(wrapper, 1, 'answer')

    // 本文が空のうちは押せない
    expect((el(wrapper, '[data-ee-grade]') as HTMLButtonElement).disabled).toBe(true)

    ;(el(wrapper, '[data-ee-ocr]') as HTMLButtonElement).click()
    await settle()
    expect((el(wrapper, '[data-ee-grade]') as HTMLButtonElement).disabled).toBe(false)
    ;(el(wrapper, '[data-ee-grade]') as HTMLButtonElement).click()
    await settle()

    expect(db.length).toBe(before + 1)
    const created = db.find((item) => item.title === '新しい英作文')!
    expect(created.level).toBe('PRE1')
    expect(created.images.map((item) => item.category)).toEqual(['question', 'answer'])
    expect(created.images.map((item) => item.originalFileName)).toEqual(['question.png', 'answer.png'])
    // 画像は上げたあと API の URL で出す
    expect(created.images[0]?.imageId).toBeTruthy()
    // 添削は**受付**（QUEUED）で、実行はバックエンド
    expect(created.gradings.map((item) => item.statusCode)).toEqual(['QUEUED'])

    const sent = sentUrls(fetchMock)
    expect(sent).toContain('POST /api/user/english-essays')
    expect(sent).toContain(`POST /api/user/english-essays/${created.essayId}/images`)
    expect(sent).toContain(`PUT /api/user/english-essays/${created.essayId}`)
    expect(sent).toContain(`POST /api/user/english-essays/${created.essayId}/gradings`)
    // 作る → 上げる → 並びを更新 → 受付 の順（画像の前に本文が要る）
    const createdAt = sent.indexOf('POST /api/user/english-essays')
    const uploadedAt = sent.indexOf(`POST /api/user/english-essays/${created.essayId}/images`)
    const updatedAt = sent.indexOf(`PUT /api/user/english-essays/${created.essayId}`)
    const acceptedAt = sent.indexOf(`POST /api/user/english-essays/${created.essayId}/gradings`)
    expect(createdAt).toBeLessThan(uploadedAt)
    expect(uploadedAt).toBeLessThan(updatedAt)
    expect(updatedAt).toBeLessThan(acceptedAt)

    expect(useToast().items.map((item) => item.message).join(' ')).toContain('AI添削を受付けました')
    expect(router.currentRoute.value.path).toBe(`/admin/english-essay/${created.essayId}`)
  })

  it('編集で開くと既存の値が入り、保存で上書きする（新規は増やさない）', async () => {
    const before = db.length
    const target = db[0]!
    const { wrapper } = await openEdit(target.essayId)

    expect(wrapper.get('[data-ee-form-title]').text()).toContain('英作文 編集')
    expect((el(wrapper, '[data-ee-title]') as HTMLInputElement).value).toBe(target.title)
    // 保存済みの画像はそのまま並ぶ（トリミング窓は通さない）
    expect(els(wrapper, '[data-ee-image]')).toHaveLength(target.images.length)

    await setInput(wrapper, '[data-ee-title]', '書き直した題')
    ;(el(wrapper, '[data-ee-save]') as HTMLButtonElement).click()
    await settle()

    expect(db.length).toBe(before)
    expect(db.find((item) => item.essayId === target.essayId)?.title).toBe('書き直した題')
    // 画像は上げ直さない（すでに ID がある）
    expect(sentUrls(fetchMock).some((url) => url.includes(`/english-essays/${target.essayId}/images`))).toBe(false)
  })

  it('編集で画像を足すときは、先に既存の画像だけを送って表示順の枠を空ける', async () => {
    const target = db[1]!
    expect(target.images.map((item) => item.order)).toEqual([1])

    const { wrapper } = await openEdit(target.essayId)
    await addImage(wrapper, 'new.png')
    ;(el(wrapper, '[data-ee-save]') as HTMLButtonElement).click()
    await settle()

    // 送った順: PUT（既存だけを整理）→ POST /images → PUT（全部を並べ直す）
    const sent = sentUrls(fetchMock)
    const imageUpload = `POST /api/user/english-essays/${target.essayId}/images`
    const firstPut = sent.indexOf(`PUT /api/user/english-essays/${target.essayId}`)
    const uploadedAt = sent.indexOf(imageUpload)
    const lastPut = sent.lastIndexOf(`PUT /api/user/english-essays/${target.essayId}`)
    expect(firstPut).toBeGreaterThanOrEqual(0)
    expect(uploadedAt).toBeGreaterThan(firstPut)
    expect(lastPut).toBeGreaterThan(uploadedAt)
    expect(sent.filter((url) => url === `PUT /api/user/english-essays/${target.essayId}`)).toHaveLength(2)

    // 足した 1 枚が入る（並びは 1 から振り直す）
    const saved = db.find((item) => item.essayId === target.essayId)!
    expect(saved.images.map((item) => item.originalFileName)).toContain('new.png')
    expect(saved.images.map((item) => item.order)).toEqual([1, 2])
  })

  it('保存に失敗したら、中途半端な作文を残さず、日本語で知らせる', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper)
    // 画像のアップロードだけ失敗させる（作文はサーバーに作られている）
    fetchMock.mockImplementation(async (input: RequestInfo | URL, init?: RequestInit) => {
      const url = String(input)
      if (url.includes('/images') && (init?.method ?? 'GET').toUpperCase() === 'POST') {
        return serverError('画像を保存できませんでした。')
      }
      const response = serverFetch(url, (init?.method ?? 'GET').toUpperCase(), init ?? {})
      if (response) return response
      throw new Error(`未定義の呼び出し: ${url}`)
    })

    ;(el(wrapper, '[data-ee-save]') as HTMLButtonElement).click()
    await settle()

    expect(useToast().items.map((item) => item.message).join(' ')).toContain('画像を保存できませんでした。')
    // 作った作文は消す（中途半端な行を残さない）
    expect(db).toHaveLength(2)
  })

  it('【一覧へ戻る】（英雄の右）で一覧へ戻る', async () => {
    const { wrapper, router } = await openNew()
    ;(el(wrapper, '[data-ee-list-back]') as HTMLElement).click()
    await settle()
    expect(router.currentRoute.value.path).toBe('/admin/english-essay')
  })

  it('未保存のまま【一覧へ戻る】で離れようとしたら確認する（了承したときだけ離れる）', async () => {
    const confirm = vi.fn(() => false)
    vi.stubGlobal('confirm', confirm)
    const { wrapper, router } = await openNew()

    await addImage(wrapper, 'answer.png')
    ;(el(wrapper, '[data-ee-list-back]') as HTMLElement).click()
    await settle()

    expect(confirm).toHaveBeenCalled()
    // 了承しなければ、この画面に留まる
    expect(router.currentRoute.value.path).toBe('/admin/english-essay/new')
    expect(el(wrapper, '[data-ee-image]')).toBeTruthy()
  })

  it('未保存でも、了承すれば【一覧へ戻る】で一覧へ戻る', async () => {
    vi.stubGlobal('confirm', vi.fn(() => true))
    const { wrapper, router } = await openNew()

    await addImage(wrapper, 'answer.png')
    ;(el(wrapper, '[data-ee-list-back]') as HTMLElement).click()
    await settle()

    expect(router.currentRoute.value.path).toBe('/admin/english-essay')
  })

  it('何も変えていなければ確認しない（そのまま一覧へ戻る）', async () => {
    const confirm = vi.fn(() => true)
    vi.stubGlobal('confirm', confirm)
    const { wrapper, router } = await openNew()

    ;(el(wrapper, '[data-ee-list-back]') as HTMLElement).click()
    await settle()

    expect(confirm).not.toHaveBeenCalled()
    expect(router.currentRoute.value.path).toBe('/admin/english-essay')
  })

  it('未保存のまま**別の画面へ移ろうとしたとき**も確認する（画面の中の移動すべて）', async () => {
    const confirm = vi.fn(() => false)
    vi.stubGlobal('confirm', confirm)
    const { wrapper, router } = await openNew()

    await addImage(wrapper, 'answer.png')
    // メニューや戻るで別の画面へ移ろうとする（画面の外の移動）
    await router.push('/admin/english-essay/essay-1')
    await settle()

    expect(confirm).toHaveBeenCalled()
    // 了承しなければ、この画面に留まる
    expect(router.currentRoute.value.path).toBe('/admin/english-essay/new')
    expect(el(wrapper, '[data-ee-form]')).toBeTruthy()
  })

  it('画面に「デモ」「Demo」「Mock」「サンプル」を出さない', async () => {
    const { wrapper } = await openNew()
    await addImage(wrapper)
    ;(el(wrapper, '[data-ee-ocr]') as HTMLButtonElement).click()
    await settle()

    const dom = wrapper.html()
    expect(dom).not.toContain('デモ')
    expect(dom).not.toContain('Demo')
    expect(dom).not.toContain('demo')
    expect(dom).not.toContain('Mock')
    expect(dom).not.toContain('サンプル')
  })
})

/**
 * 2.0 の欄構成（`english_essay.css` のグリッド）の見張り。
 *
 * <p>jsdom は CSS を当てないので、**画面の骨格**は `data-ee-region` で、**並べ方**は
 * `essay-page.css` の定義そのもので押さえる（グリッドの領域名は DOM からは見えない）。</p>
 */
describe('英作文AI添削の新規・編集ページの欄構成（2.0 のグリッド）', () => {
  it('画像アップロードは side 領域（右の柱）にあり、5 つの領域がそろう', async () => {
    const { wrapper } = await openNew()
    const side = wrapper.get('[data-ee-upload-side]')

    // 右の柱はグリッドの `side` 領域そのもの
    expect(side.attributes('data-ee-region')).toBe('side')
    // 画像アップロードの中身（画像を追加・枚数・臨時ファイル・ドロップゾーン・ヒント）は
    // すべて右の柱の中にある（2.0 の `upload-card` と同じ）
    for (const selector of ['[data-ee-tempfiles]', '[data-ee-image-count]', '[data-ee-dropzone]',
      '.ee-hint']) {
      expect(side.find(selector).exists()).toBe(true)
    }
    // 画像を 1 枚渡すと、サムネイルの一覧も右の柱の中に並ぶ
    await addImage(wrapper)
    expect(side.find('[data-ee-images]').exists()).toBe(true)
    expect(side.find('[data-ee-image]').exists()).toBe(true)
    // 領域の名前は 2.0 のものをそのまま使う（01 画像を追加＝side、02＝recognition、03＝result）
    const regions = els(wrapper, '[data-ee-region]').map((element) => element.dataset.eeRegion)
    expect(regions).toEqual(['hero', 'steps', 'side', 'recognition', 'result'])
    expect(els(wrapper, '[data-ee-card]').map((element) => element.dataset.eeCard))
      .toEqual(['upload', 'recognition', 'result'])
  })

  it('02 と 03 は左の柱（`data-ee-main`）にまとめ、01 は右の柱に残す', async () => {
    const { wrapper } = await openNew()

    // 02 AI文字認識 と 03 AI添削レポートは、同じ左の柱の中（間に空白を作らない）
    const main = wrapper.get('[data-ee-main]')
    expect(main.find('[data-ee-card="recognition"]').exists()).toBe(true)
    expect(main.find('[data-ee-card="result"]').exists()).toBe(true)
    // 01 画像を追加は右の柱のまま（左の柱には入れない）
    expect(main.find('[data-ee-card="upload"]').exists()).toBe(false)
    expect(wrapper.get('[data-ee-upload-side]').attributes('data-ee-card')).toBe('upload')
  })

  it('CSS は 2.0 のグリッド（広い画面と狭い画面の領域）とページの外枠を写している', () => {
    const css = readPageCss()

    // 新規・編集ページの中身はグリッド。2.0 の 5 領域をそのまま使う
    const form = css.slice(css.indexOf('.ee-form {'), css.indexOf('/* ---------- 3 ステップ'))
    expect(form).toContain('display: grid')
    expect(form).toContain('grid-template-columns: minmax(0, 2fr) minmax(380px, 0.9fr)')
    for (const area of FORM_AREAS) {
      expect(form).toContain(`grid-area: ${area}`)
    }
    // 広い画面では steps が消え、狭い画面では 1 列（側の柱は本文の下）
    expect(css).toContain('grid-template-columns: minmax(0, 3fr) minmax(330px, 0.8fr)')
    expect(css).toMatch(/@media \(max-width: 1180px\)[\s\S]*?\.ee-form \{[\s\S]*?grid-template-columns: minmax\(0, 1fr\)/)
    expect(css).toMatch(/@media \(min-width: 1330px\)[\s\S]*?\.ee-form \{[\s\S]*?grid-template-areas:[\s\S]*?'hero hero'[\s\S]*?'main side'/)
    // 02 と 03 は**左の柱**にまとめる。右の柱（01 画像を追加）が背高でも、この柱の中の間隔は 16px のまま
    // （右の柱が 2 行にまたがると、升が右の柱の高さに引き伸ばされて 02 と 03 の間に空白ができる）
    expect(css).toMatch(/\.ee-form__main \{[\s\S]*?grid-area: main;[\s\S]*?gap: var\(--sp-4\);/)
    // 狭い画面（1 列）でも、2.0 と同じ hero → steps → side → main（02 → 03）の順
    expect(css).toMatch(/@media \(max-width: 1180px\)[\s\S]*?\.ee-form \{[\s\S]*?'side'[\s\S]*?'main'/)
    // 足元（`.ee-form__foot`）の定義は持たない（利用者の指示で【キャンセル】ごと無くした）。
    // 前回の得点は**英雄の右**（領域を増やさない＝5 領域のまま）に置く
    expect(css).not.toMatch(/\.ee-form__foot\s*\{/)
    expect(css).toMatch(/\.ee-page-form \.ee-hero__actions \[data-ee-previous-score\] \{[\s\S]*?margin-right:/)
    // 画像アップロードは右の柱（広い画面では上に貼り付く）。狭い画面では貼り付かない
    expect(css).toMatch(/\.ee-upload-side \{[\s\S]*?grid-area: side;[\s\S]*?position: sticky/)
    expect(css).toMatch(/@media \(max-width: 1180px\)[\s\S]*?\.ee-upload-side \{[\s\S]*?position: static/)
    // 設問 : 作文は 2 面（2.0 の ocr-grid）。狭い画面では 1 列
    expect(css).toMatch(/\.ee-ocr-grid \{[\s\S]*?grid-template-columns: 0\.85fr 1\.15fr/)
    expect(css).toMatch(/@media \(max-width: 1000px\)[\s\S]*?\.ee-ocr-grid \{[\s\S]*?grid-template-columns: minmax\(0, 1fr\)/)
    // 題の 2 欄は 2 列
    expect(css).toMatch(/\.ee-title-fields \{[\s\S]*?grid-template-columns: repeat\(2, minmax\(0, 1fr\)\)/)

    // 外枠は**箱にしない**（利用者の指示 2026-09-27。2.0 の薄い枠・影・角丸・灰色の下地は写さない）。
    // アプリのページ本文（`.page` の余白）にそのまま載る
    const frame = css.slice(
      css.indexOf('.ee-page-form,'),
      css.indexOf('/* ---------- 新規・編集の中身のグリッド')
    )
    expect(frame).toContain('width: 100%')
    expect(frame).not.toMatch(/border:\s/)
    expect(frame).not.toMatch(/border-radius:/)
    expect(frame).not.toMatch(/box-shadow:/)
    expect(frame).not.toMatch(/background:/)
    // 高さで引き伸ばさない（下の `align-content: start` と対。カード間の余白を広げない）
    expect(frame).not.toMatch(/min-height:/)
    expect(frame).not.toMatch(/padding:/)
    expect(frame).not.toMatch(/margin: 0 auto/)

    // グリッドの行は**中身の高さ**にする（外枠の高さで行をストレッチしない）。
    // ストレッチすると、カードとカードの間に 16px より広い空白ができる
    expect(form).toMatch(/\.ee-form \{[\s\S]*?align-content: start;/)
    // カード間の余白は 2.0 の 16px（`--sp-4`）
    expect(form).toMatch(/\.ee-form \{[\s\S]*?gap: var\(--sp-4\);/)
    // 詳細ページも同じ（1 つのクラスが外枠とグリッドを兼ねている）
    expect(css).toMatch(/\.ee-detail \{[\s\S]*?align-content: start;/)

    // 色は tokens.css の変数だけを使う（2.0 の生の色は写さない）
    expect(css).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
  })

  it('新規・編集の中身が使う共通のクラスを、このページだけでも読めるようにしている', () => {
    const css = readPageCss()
    const list = readFileSync(
      path.join(path.dirname(fileURLToPath(import.meta.url)), '..', 'src/features/english-essay/essay-list.css'),
      'utf8'
    )

    // ドロップゾーン・画像の一覧・設問 : 作文の 2 面は essay-list.css の定義で描く。
    // `@import` が無いと、一覧を経由せずこのページを開いたとき（再読込・直リンク）だけ崩れる
    expect(css).toMatch(/@import '\.\/essay-list\.css';/)
    for (const rule of ['.ee-dropzone {', '.ee-ocr-panel {', '.ee-ocr-text {', '.ee-form-grid {']) {
      expect(list).toContain(rule)
    }
    // 2 面の列の比は 2.0 のもの（essay-page.css が後から上書きする）
    expect(css).toMatch(/\.ee-ocr-grid \{[\s\S]*?grid-template-columns: 0\.85fr 1\.15fr/)

    // カードの間隔はグリッドの `gap` だけ（`margin-bottom` が残っていると 16px が 32px になる）
    const card = list.slice(list.indexOf('.ee-card {'), list.indexOf('.ee-card__head {'))
    expect(card).toContain('padding: var(--sp-4);')
    expect(card).not.toMatch(/margin/)
  })

  it('画像の拡大表示は 2.0 の寸法を写し、色はトークンだけで作る', () => {
    const source = readComponentSource('EssayImagePreview.vue')
    const style = source.slice(source.indexOf('<style scoped>'))

    // 2.0 の `english_essay.css` の `.essay-image-preview-*`（パネルの幅・高さ・余白）
    expect(style).toContain('width: min(1500px, 96vw);')
    expect(style).toContain('height: min(940px, 94vh);')
    expect(style).toContain('padding: 13px 16px;')
    expect(style).toContain('padding: 18px;')
    // 元の実寸を超えて引き伸ばさない（`contain` で全体を見せる）
    expect(style).toMatch(/max-width: 100%;[\s\S]*?max-height: 100%;[\s\S]*?object-fit: contain;/)
    // 入れ物の升は**確定**させる（`auto` の升では `max-height: 100%` が効かず、
    // 大きな画像が縦にはみ出す。実機の Chrome で見つけた）
    expect(style).toMatch(/\.ee-image-preview__body \{[\s\S]*?grid-template-rows: minmax\(0, 1fr\);/)
    // 色は tokens.css の変数だけを使う（2.0 の生の色は写さない。
    // `color-mix(in srgb, …)` の `srgb(` は生の色ではないので、語の途中は見ない）
    expect(style).not.toMatch(/#[0-9a-fA-F]{3,8}\b/)
    expect(style).not.toMatch(/(?:^|[^a-z])rgba?\(/m)
  })
})

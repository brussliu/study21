import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import { useToast } from '@study21/web-shared'
import type { BatchTaskRow } from '@/api/batch'
import JapaneseWordView from '@/views/japanese/JapaneseWordView.vue'
import JapaneseWordStudyPage from '@/views/japanese/JapaneseWordStudyPage.vue'
import { aiStateOf } from '@/features/japanese-word/aiStateLabel'
import { needsAiFetch } from '@/features/japanese-word/acquirePlan'
import type { JpnWord, JpnWordDetailResult, JpnWordTotals } from '@/api/japanese'

/**
 * 日本語勉強【単語情報管理】（2.0 の japanese_word.jsp 相当）。
 * API はモックし、一覧・サマリの表示と、各ボタンが送るリクエストを固定する。
 * 実データ（移行済みの日本語単語 9,847 語）が入っている前提の画面。
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function failure(message: string, status = 500): Response {
  return new Response(
    JSON.stringify({ success: false, code: 'INTERNAL_ERROR', message, data: null, timestamp: '' }),
    { status, headers: { 'Content-Type': 'application/json' } }
  )
}

function word(overrides: Partial<JpnWord> = {}): JpnWord {
  return {
    wordId: 101,
    word: '勉強',
    reading: 'べんきょう',
    jlptLevel: 'N4',
    partOfSpeech: '名詞',
    // 中国語訳は有効版の詳細（最初の語義の中国語）から一覧 API が返す
    chineseMeaning: '学习；用功',
    stateCode: 'ACTIVE',
    note: null,
    version: 2,
    book: '日本語単語帳①',
    category: 'Unit001',
    level: 'N4',
    wordSeq: 12,
    collectionCount: 1,
    learnState: 'LEARNING',
    mastery: 40,
    answeredCount: 12,
    correctCount: 9,
    favorite: false,
    learned: false,
    lastStudiedAt: '2026-09-12T23:02:00',
    nextReviewAt: '2026-09-20T00:00:00',
    // 一覧の「取得状態」列（A・B は詳細、C は読み問題、D は文脈問題、E は漢字問題）
    aiState: { detail: 'SUCCEEDED', reading: 'FAILED', context: null, kanji: null },
    // 一覧の「詳細情報件数」列（有効版の詳細の段落の行数。AI 取得が済んだ語だけ入る）
    detailCounts: {
      senses: 2, examples: 3, patterns: 1, dialogs: 1, synonyms: 1,
      cautions: 1, collocations: 2, relatedWords: 1, usageNotes: 1, practices: 1
    },
    ...overrides
  }
}

/** 収録がなく、まだ一度も学習していない単語。 */
function secondWord(): JpnWord {
  return word({
    wordId: 102,
    word: '図書館',
    reading: 'としょかん',
    jlptLevel: 'N5',
    partOfSpeech: '名詞',
    // まだ AI 取得していない語は中国語訳も詳細情報件数も無い（画面は「—」）
    chineseMeaning: null,
    detailCounts: null,
    book: null,
    category: null,
    level: null,
    wordSeq: null,
    collectionCount: 0,
    learnState: 'NOT_STARTED',
    mastery: 0,
    answeredCount: 0,
    correctCount: 0,
    favorite: true,
    learned: true,
    lastStudiedAt: null,
    nextReviewAt: null,
    aiState: { detail: null, reading: null, context: null, kanji: null },
    version: 1
  })
}

const TOTALS: JpnWordTotals = {
  wordCount: 9847,
  learnedCount: 3120,
  favoriteCount: 148,
  averageMastery: 62,
  answeredCount: 45210
}

/** バッチ一覧（`GET /api/admin/batch/tasks`）の 1 行。AI 取得の 4 バッチの状態に使う。 */
function batchTask(batchCode: string, overrides: Partial<BatchTaskRow> = {}): BatchTaskRow {
  return {
    taskCode: batchCode,
    taskType: 'S',
    description: '日本語単語の AI 取得',
    active: true,
    activeVersion: 1,
    lastRunAt: null,
    canToggleActive: true,
    canManualRerun: true,
    canRerun: true,
    runsOnStartup: false,
    loopEveryMinutes: null,
    minuteOfHour: null,
    pageCode: 'JAPANESE_WORD_AI',
    requiredSettings: [`${batchCode.toUpperCase()}_BATCH_MAX`],
    settingsComplete: true,
    missingSettings: [],
    latestStatus: null,
    latestStartTime: '2026-09-27T10:00:00',
    latestEndTime: null,
    latestMessage: null,
    running: false,
    ...overrides
  }
}

/**
 * 詳細ダイアログが受け取るデータ。API が返す項目を**すべて**埋めておく
 * （足りない項目は画面に出せないので、テストデータの不足＝表示漏れに気づけるようにする）。
 */
function detailResult(overrides: Partial<JpnWordDetailResult> = {}): JpnWordDetailResult {
  const answers: Partial<JpnWordDetailResult> = {
    word: word(),
    collections: [
      {
        collectionId: 1,
        level: 'N4',
        book: '日本語単語帳①',
        category: 'Unit001',
        wordSeq: 12,
        listedWord: '勉強',
        listedReading: 'べんきょう',
        listedPartOfSpeech: '名詞',
        chineseMeaning: '学习'
      }
    ],
    questions: [
      {
        questionId: 5,
        questionType: 'C1_READING',
        questionNo: 1,
        questionText: '「勉強」の読みとして正しいものはどれですか。',
        correctValue: 'べんきょう',
        choiceCount: 4
      },
      {
        questionId: 6,
        questionType: 'C2_KANJI',
        questionNo: 1,
        questionText: '音声を聞いて正しい漢字表記を選んでください。',
        correctValue: '勉強',
        choiceCount: 4
      },
      {
        questionId: 7,
        questionType: 'D_CONTEXT_MEANING',
        questionNo: 1,
        questionText: '下線部の「勉強」の意味として最も適切なものを選びなさい。',
        correctValue: '学习',
        choiceCount: 4
      },
      {
        questionId: 8,
        questionType: 'E_KANJI_USAGE',
        questionNo: 1,
        questionText: '次の文の（ ）に入る最も適切な漢字表記を選んでください。',
        correctValue: '勉強',
        choiceCount: 4
      }
    ],
    detail: {
      detailId: 9,
      contentVersion: 1,
      version: 3,
      aiProvider: 'qwen',
      aiModel: 'qwen-max',
      fetchedAt: '2026-09-01T10:00:00',
      senseCount: 2,
      exampleCount: 2,
      pronunciationCount: 1,
      collocationCount: 1,
      relatedWordCount: 1,
      cautionCount: 1,
      detail: {
        jlptLevel: 'N4',
        partOfSpeech: '名詞',
        conjugation: 'サ変',
        transitivity: 'TRANSITIVE',
        importance: 4,
        chineseMeaning: '学习',
        coreMeaning: '学习',
        descriptionJa: '学問や技芸を学ぶこと。',
        descriptionZh: '学习学问或技艺。',
        structuredSchemaVersion: 'jp-schema-v1',
        manuallyCorrected: false,
        senses: [
          {
            number: 1,
            japanese: '学問や技芸を学ぶこと。',
            chinese: '学习',
            context: '学校・家庭',
            style: '普通',
            noteJapanese: '名詞としても動詞としても使う。',
            noteChinese: '可作名词也可作动词'
          },
          {
            number: 2,
            japanese: '経験して身につけること。',
            chinese: '经验、体会',
            context: '仕事',
            style: 'やや硬い',
            noteJapanese: null,
            noteChinese: null
          }
        ],
        examples: [
          {
            japanese: '毎日、日本語を勉強します。',
            reading: 'まいにち、にほんごをべんきょうします。',
            chinese: '每天学习日语。',
            source: '日本語単語帳①',
            senseNumber: 1,
            level: 'BASIC'
          },
          {
            japanese: 'いい勉強になりました。',
            reading: 'いいべんきょうになりました。',
            chinese: '很有收获。',
            source: null,
            senseNumber: 2,
            level: 'APPLIED'
          }
        ],
        patterns: [
          {
            pattern: '**を**勉強する',
            reading: 'をべんきょうする',
            chinese: '学习…',
            example: '日本語を勉強する。',
            exampleChinese: '学习日语。'
          }
        ],
        dialogs: [
          {
            scene: '教室で先生に聞く',
            lines: [
              { speaker: '先生', japanese: '毎日勉強していますか。', chinese: '你每天学习吗。' },
              { speaker: '学生', japanese: 'はい、しています。', chinese: '是的，在学。' }
            ]
          }
        ],
        synonyms: [
          {
            heading: '学習',
            reading: 'がくしゅう',
            chinese: '学习',
            shared: '知識を得ること',
            difference: '「学習」はやや硬い言い方。',
            scene: '書き言葉'
          }
        ],
        conjugations: [
          { form: 'て形', value: '勉強して', example: '毎日勉強している。' }
        ],
        transitivityPair: null,
        pronunciation: {
          reading: 'べんきょう',
          accentType: 0,
          accentNotation: '0',
          hint: '「べ」を低く。',
          hasAudioSample: true
        },
        pronunciations: [
          {
            reading: 'べんきょう',
            accentNotation: '0',
            accentType: 0,
            moraCount: 4,
            audioUrl: 'https://example.test/benkyou.mp3',
            audioProvider: 'Google'
          }
        ],
        collocations: [
          {
            expression: '勉強になる',
            reading: 'べんきょうになる',
            chinese: '有收获',
            usage: '会話で',
            exampleJapanese: 'とても勉強になりました。',
            exampleChinese: '很有收获。'
          }
        ],
        relatedWords: [
          {
            relation: '類義語',
            heading: '学習',
            reading: 'がくしゅう',
            chinese: '学习'
          }
        ],
        cautions: [
          {
            kind: 'MEANING',
            title: '中国語の「勉强」とは意味が異なる。',
            wrong: '無理に勉強する。',
            correct: '日本語を勉強する。',
            reason: '与中文「勉强」含义不同'
          }
        ],
        usageNotes: [
          {
            senseNumber: 1,
            register: 'どちらも',
            politeness: '普通',
            audience: '学校・家庭',
            note: '書き言葉でも使う。'
          }
        ],
        memoryHint: { hint: '「強」は「つよい」。', basis: '漢字の形' },
        practices: [
          {
            kind: 'PARTICLE',
            question: '日本語（ ）勉強する。',
            questionChinese: '学习日语。',
            choices: ['に', 'を'],
            freeWriting: false,
            answer: 'を',
            explanation: '対象は「を」。'
          }
        ]
      }
    }
  }
  return { ...answers, ...overrides } as JpnWordDetailResult
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

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

/** AI 取得の起動（POST /api/admin/batch/japanese-word-ai/run）の呼び出し。 */
function runCalls(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return recorded(fetchMock).filter(
    (entry) => entry.method === 'POST' && entry.url === '/api/admin/batch/japanese-word-ai/run'
  )
}

/** 一覧の取り直し（GET /api/user/japanese/words?...）。 */
function wordListCalls(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return recorded(fetchMock).filter(
    (entry) => entry.method === 'GET' && entry.url.startsWith('/api/user/japanese/words?')
  )
}

/** 更新系の既定の応答（画面は data.message をトーストに出す）。 */
function mutationResponse(url: string, method: string): Response {
  if (method === 'POST' && url === '/api/user/japanese/words') {
    return ok({ word: word({ wordId: 500 }), message: '単語を登録しました。' })
  }
  if (method === 'PUT') {
    if (url.endsWith('/editor')) return ok(detailResult({ word: word({ version: 3 }) }))
    return ok({ word: word(), message: '単語を更新しました。' })
  }
  if (method === 'PATCH' && url.endsWith('/favorite')) {
    return ok({ word: word({ favorite: true }), message: 'お気に入りに登録しました。' })
  }
  if (method === 'PATCH' && url.endsWith('/learned')) {
    return ok({ word: word({ learned: true }), message: '習得済にしました。' })
  }
  if (method === 'DELETE') {
    return ok({ count: 1, message: '単語を削除しました。' })
  }
  return ok({ count: 1, message: 'OK' })
}

describe('日本語勉強【単語情報管理】', () => {
  async function setup(options: {
    items?: JpnWord[]
    totalElements?: number
    detail?: JpnWordDetailResult
    handlers?: (url: string, method: string, body: Record<string, unknown> | null) => Response | null
  } = {}) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const items = options.items ?? [word(), secondWord()]
    const detail = options.detail ?? detailResult()

    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url)
      const method = (init?.method ?? 'GET').toUpperCase()
      const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
      const handled = options.handlers?.(target, method, body)
      if (handled) return handled
      if (method === 'GET' && target.startsWith('/api/user/japanese/words?')) {
        const params = new URL(target, 'http://localhost').searchParams
        const size = Number(params.get('size') ?? 20)
        const total = options.totalElements ?? items.length
        return ok({
          items,
          totalElements: total,
          page: Number(params.get('page') ?? 1),
          size,
          totalPages: Math.max(1, Math.ceil(total / size)),
          totals: TOTALS
        })
      }
      if (method === 'GET' && target === '/api/admin/batch/japanese-word-ai/limits') {
        // 上限は**設定ページの「1 回の最大単語数」だけ**（画面に既定値は無い）。
        // 既定のスタブは 4 区分ぶん返す（読めない場合のテストは handlers で上書きする）
        return ok({
          DETAIL: { batchCode: 'batC41', limit: 20 },
          C: { batchCode: 'batC42', limit: 20 },
          D: { batchCode: 'batC43', limit: 20 },
          E: { batchCode: 'batC44', limit: 20 }
        })
      }
      if (method === 'GET' && target === '/api/admin/batch/tasks') {
        // バッチの状態は**批次一覧と同じ入口**から取る（検索条件の右上に出す 4 つのバッジ）
        return ok({
          rows: [
            batchTask('batC41', { running: true, latestStatus: 'RUNNING' }),
            batchTask('batC42', { latestStatus: 'SUCCESS', latestEndTime: '2026-09-27T10:01:00' }),
            batchTask('batC43', { latestStatus: 'FAILED' }),
            batchTask('batC44', { active: false, latestStatus: null })
          ],
          totalCount: 4,
          startupTargets: []
        })
      }
      if (method === 'GET' && target.startsWith('/api/user/japanese/words/ai-targets')) {
        // AI 取得の対象は**サーバーが選ぶ**（検索条件に一致する語全体から表示順に）。
        // テストの既定は「いま出ている語 = 検索条件に一致する語」として同じ判定を返す
        const params = new URL(target, 'http://localhost').searchParams
        const kind = params.get('kind') ?? 'DETAIL'
        const key = (kind === 'DETAIL' ? 'AB' : kind) as 'AB' | 'C' | 'D' | 'E'
        const skipAcquired = params.get('skipAcquired') !== 'false'
        const limit = Number(params.get('limit') ?? 200)
        const candidates = skipAcquired
          ? items.filter((row) => needsAiFetch(aiStateOf(row, key)))
          : [...items]
        const targetIds = candidates.slice(0, limit).map((row) => row.wordId)
        return ok({
          total: items.length,
          acquired: items.filter((row) => aiStateOf(row, key) === 'SUCCEEDED').length,
          candidates: candidates.length,
          wordIds: targetIds,
          overLimit: Math.max(0, candidates.length - targetIds.length),
          limit
        })
      }
      if (method === 'GET' && /^\/api\/user\/japanese\/words\/\d+$/.test(target)) {
        return ok(detail)
      }
      // AI 取得の履歴（取得状態のタグから開く）
      if (method === 'GET' && target.endsWith('/detail-versions')) {
        return ok({ items: [] })
      }
      if (method === 'GET' && target.endsWith('/question-versions')) {
        return ok({ items: [], totalCount: 0 })
      }
      return mutationResponse(target, method)
    })

    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(JapaneseWordView, { global: { plugins: [pinia] } })
    await flushPromises()
    return { wrapper, fetchMock }
  }

  beforeEach(() => {
    useToast().items.splice(0)
    vi.restoreAllMocks()
    // jsdom には無いもの（別ウィンドウを開く画面のため）
    window.scrollTo = vi.fn()
    window.focus = vi.fn()
  })

  /* ---------- 学習画面（新画面） ---------- */

  it('詳細編集の保存競合でも入力を保持し、未保存プレビューで確認できる', async () => {
    const { wrapper } = await setup({ handlers: (url, method) => {
      if (method === 'PUT' && url.endsWith('/editor')) return new Response(JSON.stringify({
        success: false, code: 'CONFLICT', message: '先に更新されました', data: null
      }), { status: 409, headers: { 'Content-Type': 'application/json' } })
      return null
    } })
    await wrapper.get('[data-jp-word-row="101"] [data-jp-edit]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-demo-editor-nav-item="meaning"]').trigger('click')
    await wrapper.get('[data-demo-edit-description-ja]').setValue('保存前の新しい説明')
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await flushPromises()
    expect(wrapper.text()).toContain('先に更新されました')
    expect((wrapper.get('[data-demo-edit-description-ja]').element as HTMLTextAreaElement).value).toBe('保存前の新しい説明')
    await wrapper.get('[data-demo-preview]').trigger('click')
    expect(wrapper.get('[data-jp-editor-preview]').text()).toContain('保存前の新しい説明')
    await wrapper.get('[aria-label="プレビューを閉じる"]').trigger('click')
    await wrapper.get('[data-demo-editor-cancel]').trigger('click')
    expect(wrapper.text()).toContain('保存していない変更があります')
    expect(wrapper.find('[data-jp-word-dialog]').exists()).toBe(true)
    wrapper.unmount()
  })

  it('保存の競合は HTTP 409 でも code CONFLICT でも同じ扱いにする（判定を割らない）', async () => {
    // 409 だが code が CONFLICT 以外（サーバーの書き方に寄らず競合として扱う）
    const { wrapper } = await setup({ handlers: (url, method) => {
      if (method === 'PUT' && url.endsWith('/editor')) return new Response(JSON.stringify({
        success: false, code: 'VALIDATION_ERROR', message: '先に更新されました', data: null
      }), { status: 409, headers: { 'Content-Type': 'application/json' } })
      return null
    } })
    await wrapper.get('[data-jp-word-row="101"] [data-jp-edit]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-demo-editor-nav-item="meaning"]').trigger('click')
    await wrapper.get('[data-demo-edit-description-ja]').setValue('保存前の新しい説明')
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await flushPromises()

    // 競合の知らせ（読み直しを促す帯）が出る。FAILED 扱いにはしない
    expect(wrapper.find('[data-demo-save-conflict]').exists()).toBe(true)
    expect(wrapper.text()).toContain('読み直してください')
    wrapper.unmount()
  })

  it('詳細が未生成の単語も空の欄から編集して初回保存できる', async () => {
    const { wrapper, fetchMock } = await setup({ detail: detailResult({ detail: null }) })
    await wrapper.get('[data-jp-word-row="101"] [data-jp-edit]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-demo-editor-nav-item="meaning"]').trigger('click')
    await wrapper.get('[data-demo-edit-description-zh]').setValue('新增的中文说明')
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await flushPromises()
    const call = fetchMock.mock.calls.find(([url, init]) => String(url).endsWith('/editor') && init?.method === 'PUT')!
    const body = JSON.parse(String(call[1]?.body))
    expect(body.contentVersion).toBe(0)
    expect(body.detail.editorContent.descriptionZh).toBe('新增的中文说明')
    wrapper.unmount()
  })

  /* ---------- 学習画面（別ウィンドウ。自動最大化） ---------- */

  /** 別ウィンドウの呼び出しを捕まえる（テストでは本当のウィンドウは開けない）。 */
  function stubWindowOpen(): { calls: { url: string; name: string; features: string }[]; restore: () => void } {
    const calls: { url: string; name: string; features: string }[] = []
    const original = window.open
    window.open = ((url?: string, name?: string, features?: string) => {
      calls.push({ url: String(url), name: String(name), features: String(features) })
      return { focus: () => undefined, closed: false } as unknown as Window
    }) as typeof window.open
    return {
      calls,
      restore: () => {
        window.open = original
      }
    }
  }

  /** 学習画面（別ウィンドウ）のページを、その URL（`?wordId=`）で開く。 */
  async function mountStudyPage(options: {
    wordId?: number
    detail?: JpnWordDetailResult
    handlers?: (url: string, method: string, body: Record<string, unknown> | null) => Response | null
  } = {}) {
    const pinia = createPinia()
    setActivePinia(pinia)
    const detail = options.detail ?? detailResult()
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url)
      const method = (init?.method ?? 'GET').toUpperCase()
      const body = init?.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
      const handled = options.handlers?.(target, method, body)
      if (handled) return handled
      if (method === 'GET' && /^\/api\/user\/japanese\/words\/\d+$/.test(target)) return ok(detail)
      return ok({ count: 1, message: 'OK' })
    })
    vi.stubGlobal('fetch', fetchMock)

    const router = createRouter({
      history: createWebHashHistory(),
      routes: [{ path: '/study', component: JapaneseWordStudyPage }]
    })
    await router.push(`/study?wordId=${options.wordId ?? 101}`)
    await router.isReady()
    const wrapper = mount(JapaneseWordStudyPage, { global: { plugins: [pinia, router] } })
    await flushPromises()
    return { wrapper, fetchMock }
  }

  it('一覧の【詳細】は学習画面を別ウィンドウで開き、自動最大化する', async () => {
    const { wrapper, fetchMock } = await setup()
    const popup = stubWindowOpen()
    try {
      await wrapper.get('[data-jp-word-row="101"] [data-jp-detail]').trigger('click')
      await flushPromises()

      expect(popup.calls).toHaveLength(1)
      expect(popup.calls[0].url).toBe('/student/japanese-word/study?wordId=101')
      // 同じ語を二重に開かない（ウィンドウ名に語 ID を入れる）
      expect(popup.calls[0].name).toBe('jpWordStudy_101')
      // ブラウザでは window.maximize() が呼べないので、画面の利用可能サイズ＋左上で開く
      expect(popup.calls[0].features).toContain('left=0')
      expect(popup.calls[0].features).toContain('top=0')
      expect(popup.calls[0].features).toMatch(/width=\d+/)
      expect(popup.calls[0].features).toMatch(/height=\d+/)
      // 一覧は学習画面のデータを取りに行かない（開いた窓が取り直す）
      expect(recorded(fetchMock).some((call) => /^\/api\/user\/japanese\/words\/\d+$/.test(call.url))).toBe(false)
      // アプリ内のダイアログは出さない
      expect(wrapper.find('[data-jp-study-dialog]').exists()).toBe(false)
    } finally {
      popup.restore()
    }
  })

  it('学習画面（別ウィンドウ）は語の詳細だけを出し、上部の帯（見出し・修正・閉じる）は置かない', async () => {
    const { wrapper } = await mountStudyPage()

    // API の詳細を 1 語だけ取り直して出す
    expect(wrapper.get('[data-demo-study-word]').text()).toBe('勉強')
    expect(wrapper.get('[data-demo-study-reading]').text()).toBe('べんきょう')
    // 中国語の意味（新画面の「中文释义」の帯）
    expect(wrapper.get('[data-demo-study-primary-text]').text()).toBe('学习')

    // 利用者の指示（2026-09-25）: 左上の【学習画面】【修正】【閉じる】は出さない。
    // 2.0 の学習画面も中身だけを出していた（窓はブラウザの × で閉じる）。
    expect(wrapper.find('.jp-study-window__bar').exists()).toBe(false)
    expect(wrapper.find('[data-jp-study-page-close]').exists()).toBe(false)
    expect(wrapper.find('[data-jp-study-page-edit]').exists()).toBe(false)
    // 中身は残す
    expect(wrapper.find('.jp-study-window__body [data-demo-study-head]').exists()).toBe(true)
  })

  it('学習画面のタブを切り替えると、発音・関連語・コロケーションの内容が出る', async () => {
    const { wrapper } = await mountStudyPage()

    await wrapper.get('[data-demo-study-tab="form"]').trigger('click')
    await flushPromises()
    const pronunciation = wrapper.get('[data-demo-study-pronunciation]')
    expect(pronunciation.text()).toContain('べんきょう')
    expect(pronunciation.text()).toContain('0 型')

    await wrapper.get('[data-demo-study-tab="compare"]').trigger('click')
    await flushPromises()
    const compare = wrapper.get('[data-demo-study-panel="compare"]')
    expect(compare.text()).toContain('学習')
    expect(compare.text()).toContain('中国語の「勉强」とは意味が異なる。')

    await wrapper.get('[data-demo-study-tab="collocations"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-demo-study-panel="collocations"]').text()).toContain('勉強になる')
  })

  it('学習画面は 2.0 の分類（語義・例文・発音・コロケーション・関連語・使用注意）をすべて渡す', async () => {
    const { wrapper } = await mountStudyPage()

    // 例文・会話のタブに、API の例文が 2 つ入っている
    await wrapper.get('[data-demo-study-tab="examples"]').trigger('click')
    await flushPromises()
    const examples = wrapper.get('[data-demo-study-panel="examples"]')
    expect(examples.findAll('[data-demo-study-example]').length).toBeGreaterThanOrEqual(2)
    expect(examples.text()).toContain('毎日、日本語を勉強します。')
    expect(examples.text()).toContain('每天学习日语。')
    expect(examples.text()).toContain('いい勉強になりました。')
    // 使用場面は語義の「使う場面／文体」から作る
    await wrapper.get('[data-demo-study-tab="meaning"]').trigger('click')
    await flushPromises()
    const meaning = wrapper.get('[data-demo-study-panel="meaning"]')
    expect(meaning.text()).toContain('学問や技芸を学ぶこと。')
    expect(meaning.text()).toContain('経験して身につけること。')
    expect(meaning.text()).toContain('学校・家庭')
  })

  it('詳細の取得に失敗したら学習画面に理由を出す', async () => {
    const { wrapper } = await mountStudyPage({
      handlers: (url) =>
        /^\/api\/user\/japanese\/words\/\d+$/.test(url)
          ? new Response(JSON.stringify({ success: false, message: '詳細を取得できませんでした。' }), {
            status: 500,
            headers: { 'Content-Type': 'application/json' }
          })
          : null
    })

    // 失敗した理由を学習画面の中に出す（別ウィンドウを閉じたりしない）
    expect(wrapper.get('[data-jp-study-page-error]').text()).toContain('500')
    expect(wrapper.find('[data-jp-study-page-missing]').exists()).toBe(false)
  })

  it('詳細がまだ無い単語でも学習画面は開き、欠けていることを知らせる', async () => {
    const { wrapper } = await mountStudyPage({ detail: detailResult({ collections: [], detail: null }) })

    expect(wrapper.get('[data-demo-study-word]').text()).toBe('勉強')
    expect(wrapper.find('[data-demo-study-missing]').exists()).toBe(true)
  })

  it('単語が指定されていない学習画面は、その旨を出して閉じられる', async () => {
    const { wrapper, fetchMock } = await mountStudyPage({ wordId: 0 })

    expect(wrapper.get('[data-jp-study-page-error]').text()).toContain('表示する単語が指定されていません')
    // 語が分からないので API は呼ばない
    expect(recorded(fetchMock)).toHaveLength(0)
  })

  /* ---------- 新規登録（新画面） ---------- */

  it('「新規」は新画面の登録画面を開き、語と一緒に書籍・Unit を登録する', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-add]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-demo-new-dialog]').exists()).toBe(true)
    // 一覧の語から作った書籍が選べる（母表に書籍の列は無いので収録から作る）
    const bookSelect = wrapper.get('[data-demo-book-select]')
    expect(bookSelect.findAll('option').map((option) => option.text())).toContain('日本語単語帳①')

    // 表に 3 語入れて登録する（「勉強」は一覧に既にある）
    await wrapper.get('[data-demo-word-grid]').trigger('paste', {
      clipboardData: { getData: () => '弁当\n apples\n勉強' }
    })
    await flushPromises()
    await wrapper.get('[data-demo-save]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find(
      (entry) => entry.method === 'POST' && entry.url === '/api/user/japanese/words/register'
    )
    const words = (call?.body as { words: { word: string; book: string; category: string; wordSeq: number }[] }).words
    // 「勉強」は一覧に既にあるので送らない（重複は登録しない）
    expect(words.map((entry) => entry.word)).toEqual(['弁当', 'apples'])
    // 語だけでは一覧の書籍・分類が空になるので、収録（書籍・Unit・SEQ）も一緒に送る。
    // どの Unit に入るかは既存の収録の埋まり具合で決まるので、**一律に同じ書籍**で
    // **Unit と SEQ が入っている**ことだけを確かめる（割り当ての詳細は logic のテストが見る）
    for (const entry of words) {
      expect(entry.book).toBe('日本語単語帳①')
      expect(entry.category).toMatch(/^Unit\d{3}$/)
      expect(entry.wordSeq).toBeGreaterThanOrEqual(1)
    }
    expect(wrapper.find('[data-demo-new-dialog]').exists()).toBe(false)
  })

  it('登録画面は、一覧に既にある語を「登録しない」として数える', async () => {
    const { wrapper } = await setup()
    await wrapper.get('[data-jp-add]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-demo-word-grid]').trigger('paste', {
      clipboardData: { getData: () => '勉強\n新しい語\n新しい語' }
    })
    await flushPromises()

    // 登録するのは 1 語（新しい語）、一覧に既にある 1 語と、表の中の重複 1 語は登録しない
    expect(wrapper.get('[data-demo-count-fresh]').text()).toBe('1')
    expect(wrapper.get('[data-demo-count-skipped]').text()).toBe('1')
  })

  it('登録に失敗したらダイアログを閉じず、理由と入力を残す', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        url === '/api/user/japanese/words/register' && method === 'POST'
          ? new Response(JSON.stringify({ success: false, code: 'ERROR', message: '登録できませんでした。' }), {
            status: 500,
            headers: { 'Content-Type': 'application/json' }
          })
          : null
    })

    await wrapper.get('[data-jp-add]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-demo-word-grid]').trigger('paste', {
      clipboardData: { getData: () => '新しい語' }
    })
    await flushPromises()
    await wrapper.get('[data-demo-save]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-demo-new-dialog]').exists()).toBe(true)
    expect(wrapper.get('[data-demo-register-error]').text()).toContain('登録できませんでした')
  })

  it('単語の一覧を新しい列順で日本語に描画する', async () => {
    const { wrapper } = await setup()

    expect(wrapper.find('[data-jp-words]').exists()).toBe(true)
    // 列は左から 操作 / JLPTレベル / 単語ID / 書籍 / 分類 /
    // 単語・読み方・品詞・中国語訳（1 列にまとめた）/ 詳細情報件数 / 取得状態
    expect(wrapper.findAll('[data-jp-words] thead th').map((cell) => cell.text())).toEqual([
      '操作', 'JLPTレベル', '単語ID', '書籍', '分類', '単語・読み方・品詞・中国語訳',
      '詳細情報件数', '取得状態（A・B，C，D，E）'
    ])

    const row = wrapper.get('[data-jp-word-row="101"]')
    const cells = row.findAll('td').map((cell) => cell.text())
    // 操作はアイコンだけなので文字を持たない（左端の列）
    expect(cells[0]).toBe('')
    expect(cells[1]).toBe('N4')
    expect(cells[2]).toBe('101')
    expect(cells[3]).toBe('日本語単語帳①')
    expect(cells[4]).toBe('Unit001')
    // 5 列目は 1 行目（単語・読み方・品詞）＋ 2 行目（中国語訳）がまとまって出る
    expect(cells[5]).toContain('勉強')
    expect(cells[5]).toContain('べんきょう')
    expect(cells[5]).toContain('名詞')
    expect(cells[5]).toContain('学习；用功')
    // 詳細情報件数は有効版の段落の行数をタグで出す（2.0 の英語学習と同じ列）
    const countTags = row.get('[data-jp-detail-counts]').findAll('.jp-detail-count')
    expect(countTags.map((tag) => tag.text())).toEqual([
      '語義2', '例文3', '文型1', '会話1', '類義1', '注意1', 'コロ2', '関連1', '場面1', '練習1'
    ])
    expect(countTags[1]?.attributes('title')).toBe('例文 3件')
    // 取得状態は API が返した状態を**タグ**で出し、色で見分ける
    // （A・B は取得済＝緑、C は失敗＝赤、D・E は未取得＝グレー）
    const tags = row.get('[data-jp-acquire]').findAll('.badge')
    expect(tags.map((tag) => tag.text())).toEqual(['A・B 取得済', 'C 失敗', 'D 未取得', 'E 未取得'])
    expect(tags[0].classes()).toContain('badge--success')
    expect(tags[1].classes()).toContain('badge--danger')
    expect(tags[2].classes()).toContain('badge--neutral')
    expect(tags[3].classes()).toContain('badge--neutral')
    // 取得済のタグだけ押せる（履歴を開いて、使う版を選べる）
    expect(tags[0].element.tagName).toBe('BUTTON')
    expect(tags[0].classes()).toContain('is-clickable')
    expect((tags[0].element as HTMLButtonElement).disabled).toBe(false)
    expect((tags[1].element as HTMLButtonElement).disabled).toBe(true)

    // 収録が無い単語は書籍・分類とも「—」
    const second = wrapper.get('[data-jp-word-row="102"]')
    const secondCells = second.findAll('td').map((cell) => cell.text())
    expect(secondCells[3]).toBe('—')
    expect(secondCells[4]).toBe('—')
    expect(second.get('.jp-word').text()).toBe('図書館')
    // 詳細がまだ無い語は中国語訳も「—」
    expect(second.get('[data-jp-chinese]').text()).toBe('—')
    // 詳細情報件数も「—」（有効版が無いので数える対象が無い）
    expect(second.get('[data-jp-detail-counts]').text()).toBe('—')
    expect(second.find('[data-jp-detail-count]').exists()).toBe(false)
    // まだ一度も AI 取得していない語は 4 つとも未取得（グレー）
    const secondTags = second.get('[data-jp-acquire]').findAll('.badge')
    expect(secondTags.map((tag) => tag.text()))
      .toEqual(['A・B 未取得', 'C 未取得', 'D 未取得', 'E 未取得'])
    expect(secondTags.every((tag) => tag.classes().includes('badge--neutral'))).toBe(true)

    // 検索条件と一覧の間にあった統計情報（サマリ）のブロックは置かない
    expect(wrapper.find('[data-jp-summary]').exists()).toBe(false)

    // 件数は一覧のヘッダーとページングに出す
    expect(wrapper.get('[data-jp-count]').text()).toContain('全 2 件')
    expect(wrapper.get('.pagination__info').text()).toContain('全 2 件（1 / 1 ページ）')
  })


  it('初期表示は 1 ページ目・20 件で検索する', async () => {
    const { fetchMock } = await setup()

    // 一覧の検索（同じく画面を開いたときに走る取得の上限は別のテストで見る）
    const call = recorded(fetchMock).find(
      (entry) => entry.method === 'GET' && entry.url.startsWith('/api/user/japanese/words?')
    )
    expect(call?.url).toBe('/api/user/japanese/words?page=1&size=20')
  })

  it('検索条件をクエリで送る（条件をまとめて消すボタンは置かない）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-filter="keyword"]').setValue('勉強')
    await wrapper.get('[data-jp-filter="jlpt"]').setValue('N4')
    await wrapper.get('[data-jp-filter="part"]').setValue('名')
    await wrapper.get('[data-jp-filter="book"]').setValue('日本語単語帳①')
    await wrapper.get('[data-jp-filter="categoryFrom"]').setValue('Unit001')
    await wrapper.get('[data-jp-filter="categoryTo"]').setValue('Unit001')
    await wrapper.get('[data-jp-search]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).at(-1)
    expect(call?.method).toBe('GET')
    expect(call?.url).toContain('/api/user/japanese/words?')
    expect(call?.url).toContain(`keyword=${encodeURIComponent('勉強')}`)
    expect(call?.url).toContain('jlpt=N4')
    expect(call?.url).toContain(`part=${encodeURIComponent('名')}`)
    expect(call?.url).toContain(`book=${encodeURIComponent('日本語単語帳①')}`)
    // 分類は範囲（From ～ To）で送る
    expect(call?.url).toContain('categoryFrom=Unit001')
    expect(call?.url).toContain('categoryTo=Unit001')
    expect(call?.url).toContain('page=1')
    // 読み・学習状態の絞り込みは画面から外した（コントロールも送信も無い）
    expect(wrapper.find('[data-jp-filter="reading"]').exists()).toBe(false)
    expect(wrapper.find('[data-jp-filter="learnState"]').exists()).toBe(false)
    expect(call?.url).not.toContain('reading=')
    expect(call?.url).not.toContain('learnState=')
    // 絞り込み中はそのことが分かる
    expect(wrapper.find('[data-jp-filtered-count]').exists()).toBe(true)

    // 条件は各項目を直接消す（まとめて消す【リセット】は利用者の指示で外した）
    expect(wrapper.find('[data-jp-reset]').exists()).toBe(false)
  })

  it('取得済のタグを押すと、その区画の履歴を開く（未取得のタグは押せない）', async () => {
    const { wrapper, fetchMock } = await setup()

    // 未取得のタグは押しても開かない
    const notAcquired = wrapper.get('[data-jp-word-row="101"] [data-jp-acquire-state="D"]')
    expect((notAcquired.element as HTMLButtonElement).disabled).toBe(true)
    await notAcquired.trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-jp-history-dialog]').exists()).toBe(false)

    // 取得済（A・B）のタグで開く
    await wrapper.get('[data-jp-word-row="101"] [data-jp-acquire-state="AB"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-jp-history-dialog]').exists()).toBe(true)
    expect(wrapper.get('[data-jp-history-dialog]').text()).toContain('A・B 詳細情報の履歴')
    // その語の詳細の版を読みに行く
    expect(recorded(fetchMock).some((call) =>
      call.url === '/api/user/japanese/words/101/detail-versions')).toBe(true)

    // 閉じられる
    await wrapper.get('[data-jp-history-close]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-jp-history-dialog]').exists()).toBe(false)
  })

  it('品詞は選択式で、選ぶと部分一致の条件として送る', async () => {
    const { wrapper, fetchMock } = await setup()

    const part = wrapper.get('[data-jp-filter="part"]')
    expect(part.element.tagName).toBe('SELECT')
    // 2.0 の教材の品詞ラベルを選べる（先頭は絞り込みなし）
    expect(part.findAll('option').map((option) => option.text())).toEqual([
      '（すべて）', '名', '代', '副', '形', '形動', '連体', '接', '感', '助', '接頭', '接尾',
      '五段', '下一段', '上一段', 'サ変'
    ])

    // 「名」は名詞の複合ラベル（[名・他サ] など）にも当たる部分一致として送る
    await part.setValue('名')
    await wrapper.get('[data-jp-search]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).at(-1)
    expect(call?.url).toContain(`part=${encodeURIComponent('名')}`)
  })

  it('検索条件は 1 行にまとめ、分類の範囲は「～」で挟む', async () => {
    const { wrapper } = await setup()

    // 1 行にまとめる（狭い画面では折り返して縦に積む。規則は CSS 側）
    const filters = wrapper.get('.filters')
    expect(filters.findAll('.filters__row').length).toBe(1)

    // 分類（From ～ To）は 1 つのまとまりにして、間を「～」で示す
    const range = wrapper.get('[data-jp-filter-range]')
    const controls = range.findAll('[data-jp-filter]')
    expect(controls.map((control) => control.attributes('data-jp-filter'))).toEqual(['categoryFrom', 'categoryTo'])
    expect(range.get('.range-input__sep').text()).toBe('～')
    expect(controls[0]?.attributes('aria-label')).toBe('分類（From）')
    expect(controls[1]?.attributes('aria-label')).toBe('分類（To）')
    // 「分類（From）：」という長い見出しは置かない
    expect(filters.text()).not.toContain('分類（From）：')
  })

  it('キーワードは左から順に置き、右端へ寄せない（伸ばさない）', async () => {
    const { wrapper } = await setup()

    // 並びは JLPT → 品詞 → 書籍 → 分類 → キーワード（最後のまま。利用者の指示で
    // 右端へ寄せるのをやめ、左から順に並べる）
    const items = wrapper.findAll('.jp-filters__row > .filter-item')
    expect(items.length).toBe(5)
    const last = items[items.length - 1]
    expect(last.classes()).toContain('jp-filters__keyword')
    expect(last.find('[data-jp-filter="keyword"]').exists()).toBe(true)
    // 伸ばすためのクラスは付けない（前は filter-item--grow で横いっぱいに広げていた）
    expect(last.classes()).not.toContain('filter-item--grow')

    // jsdom にレイアウトが無いので、CSS の規則そのものを確かめる
    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')
    const keywordStart = css.indexOf('.jp-filters__keyword {')
    expect(keywordStart).toBeGreaterThanOrEqual(0)
    const keywordRule = css.slice(keywordStart, css.indexOf('}', keywordStart))
    // 右端へ寄せない（左の余白を吸わせない）＋ 伸ばさない
    expect(keywordRule).not.toContain('margin-left: auto')
    expect(keywordRule).toContain('flex: 0 0 auto')

    const inputStart = css.indexOf('.jp-filters__keyword .input {')
    expect(inputStart).toBeGreaterThanOrEqual(0)
    const inputRule = css.slice(inputStart, css.indexOf('}', inputStart))
    const width = Number(inputRule.match(/width:\s*([\d.]+)rem/)?.[1])
    // 画面いっぱいには広げない（12rem 前後）
    expect(width).toBeGreaterThanOrEqual(8)
    expect(width).toBeLessThanOrEqual(16)
  })

  it('検索条件は 1 行に 5 つ並び、狭い画面の見え方は CSS 側で切り替える', async () => {
    const { wrapper } = await setup()

    // 並びはテンプレート側で決まる（JLPT・品詞・書籍・分類の範囲・キーワード）
    const items = wrapper.findAll('.jp-filters__row > .filter-item')
    expect(items.length).toBe(5)
    expect(items[0]?.classes()).toContain('jp-filters__jlpt')
    expect(items[4]?.classes()).toContain('jp-filters__keyword')

    // 折り返して縦に積む規則は、jsdom にレイアウトが無いので CSS の規則そのものを確かめる。
    // 実際の寸法は Chrome の実測（tmp/jpn-badges の画面キャプチャ）で見る。
    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')
    const rule = css.slice(css.indexOf('@media (max-width: 1024px)'))
    expect(rule).toContain('.jp-filters__row > .filter-item')
    expect(rule).toContain('flex: 1 1 100%')
    expect(rule).toContain('max-width: 100%')
  })

  it('取得状態の列は、タグ 2×2 が収まる幅を確保する', () => {
    // jsdom にレイアウトが無いので、CSS の規則そのものを確かめる。
    // 実寸は Chrome の実測（tmp/jpn-badges/measure.html）で見る:
    // 文字 13px・タグ 12px・セル余白 32px で、2×2 の最も広い組み合わせ（全部「取得待ち」）が
    // 204px 必要 → 16rem（256px）で余裕をもって収まる。1 行に 4 つ並べると 395px 必要になり、
    // ほかの列を押し潰す（だから 2×2 にした）
    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')

    // 列は 1 行のまま（折り返すと縦に積まれて行が高くなる）
    const columnStart = css.indexOf('.jp-words-table .col-jp-acquire')
    expect(columnStart).toBeGreaterThanOrEqual(0)
    const columnRule = css.slice(columnStart, css.indexOf('}', columnStart))
    expect(columnRule).toContain('white-space: nowrap')
    const width = Number(columnRule.match(/width:\s*([\d.]+)rem/)?.[1])
    expect(width).toBeGreaterThanOrEqual(15)
    expect(columnRule).toContain(`min-width: ${width}rem`)

    // 中のタグは 2×2 の格子
    const innerStart = css.indexOf('.jp-acquire {')
    const innerRule = css.slice(innerStart, css.indexOf('}', innerStart))
    expect(innerRule).toContain('display: grid')
    expect(innerRule).toContain('grid-template-columns: repeat(2, max-content)')
  })

  it('単語・読み・品詞・中国語訳の列は、中身に押し広げられない（ほかの列を潰さない）', () => {
    // 中国語訳は長いので、`max-width: 0` が無いと列が中身に押されて広がり、
    // そのぶん取得状態が潰れて折り返す（Chrome の実測で確認）。
    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')
    const start = css.indexOf('.jp-words-table .col-jp-word-summary')
    expect(start).toBeGreaterThanOrEqual(0)
    const rule = css.slice(start, css.indexOf('}', start))

    expect(rule).toContain('min-width: 14rem')
    expect(rule).toContain('max-width: 0')
  })

  it('中国語訳の 2 行クランプは、セルの中の span に当てる（td の display を変えない）', async () => {
    // td 自身に `display: -webkit-box` を当てると、表のセルでなくなって列幅の計算が壊れ、
    // 隣の列（取得状態）へ文字がはみ出す（実際に起きた。Chrome の実測で再現）。
    const { wrapper } = await setup()

    const cell = wrapper.get('[data-jp-word-summary]')
    expect(cell.element.tagName).toBe('TD')
    // セル自身は表のセルのまま（2 行に積むのは中の span）
    expect(cell.classes()).not.toContain('jp-word-summary')
    expect(cell.get('.jp-word-summary').element.tagName).toBe('SPAN')

    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')
    const spanStart = css.indexOf('.jp-word-summary__chinese {')
    expect(spanStart).toBeGreaterThanOrEqual(0)
    const spanRule = css.slice(spanStart, css.indexOf('}', spanStart))
    expect(spanRule).toContain('-webkit-line-clamp: 2')

    // セル（[data-jp-word-summary]）側には display を書かない
    expect(css).not.toContain('.jp-words-table [data-jp-word-summary] {')
  })

  it('詳細情報件数のタグは、セルの中の要素で並べる（td の display を変えない）', async () => {
    // td 自身に `display: flex` を当てると、表のセルでなくなって**行の高さが中身に合わず**、
    // 2 行目（注意〜練習）のタグが次の行へ食い込む（利用者の指摘 2026-09-26
    // 「詳細情報件数这一列的行高不一致，看起来串行了」。中国語訳の `display: -webkit-box` と同じ型）。
    const { wrapper } = await setup()

    const cell = wrapper.get('[data-jp-detail-counts]')
    expect(cell.element.tagName).toBe('TD')
    // セル自身は表のセルのまま（並べるのは中の span）
    expect(cell.classes()).not.toContain('jp-detail-counts')
    expect(cell.get('.jp-detail-counts').element.tagName).toBe('SPAN')

    // jsdom にレイアウトが無いので、CSS の規則そのものを確かめる
    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')
    const innerStart = css.indexOf('.jp-words-table [data-jp-detail-counts] .jp-detail-counts')
    expect(innerStart).toBeGreaterThanOrEqual(0)
    const innerRule = css.slice(innerStart, css.indexOf('}', innerStart))
    expect(innerRule).toContain('display: flex')
    expect(innerRule).toContain('flex-wrap: wrap')
  })

  it('書籍と分類の選択肢は、読み込んだ一覧の値から作る（（すべて）つき）', async () => {
    const { wrapper } = await setup()

    // 選択肢一覧を返す API が無いので、読み込んだ単語の値だけが並ぶ
    expect(wrapper.get('[data-jp-filter="book"]').findAll('option').map((option) => option.text()))
      .toEqual(['（すべて）', '日本語単語帳①'])
    expect(wrapper.get('[data-jp-filter="categoryFrom"]').findAll('option').map((option) => option.text()))
      .toEqual(['（すべて）', 'Unit001'])
    // 分類の From と To は同じ選択肢を使う
    expect(wrapper.get('[data-jp-filter="categoryTo"]').findAll('option').map((option) => option.text()))
      .toEqual(['（すべて）', 'Unit001'])
    // 範囲は両方とも効く（「To は未対応」の注記は消した）
    expect(wrapper.get('[data-jp-filter="categoryTo"]').attributes('title')).toBeUndefined()
    expect(wrapper.find('[data-jp-filter-note]').exists()).toBe(false)
  })

  it('分類の To だけでも絞り込める（範囲の片側だけ）', async () => {
    const { wrapper, fetchMock } = await setup({
      items: [word(), word({ wordId: 103, word: '朝', category: 'Unit002' })]
    })

    // 選択肢は読み込んだ一覧の値から作る
    expect(wrapper.get('[data-jp-filter="categoryTo"]').findAll('option').map((option) => option.text()))
      .toEqual(['（すべて）', 'Unit001', 'Unit002'])

    await wrapper.get('[data-jp-filter="categoryTo"]').setValue('Unit002')
    await wrapper.get('[data-jp-search]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).at(-1)
    expect(call?.url).toContain('categoryTo=Unit002')
    expect(call?.url).not.toContain('categoryFrom=')
  })

  it('右上の取得ボタンは 1 つで、押すとどの情報を取るかを窓の中で選ぶ（すぐには AI を呼ばない）', async () => {
    const { wrapper, fetchMock } = await setup()

    const group = wrapper.get('[data-jp-acquire-actions]')
    // 4 つ並べると場所を取るので 1 つにまとめた（利用者の指示）
    const buttons = group.findAll('button')
    expect(buttons).toHaveLength(1)
    expect(buttons[0]!.text()).toContain('AI 取得')
    expect(buttons[0]!.attributes('disabled')).toBeUndefined()
    // 実行はバックグラウンドなので、title も「受付ける」と言う（2026-09-27 の非同期化）
    expect(buttons[0]!.attributes('title')).toContain('取得を受付けます')

    await buttons[0]!.trigger('click')
    await flushPromises()

    // 窓の中で 4 つから選ぶ（最初は詳細情報（A・B）＝2 語のうち取得済み 1 語）
    const dialog = wrapper.get('[data-jp-fetch-dialog]')
    expect(wrapper.findAll('[data-jp-fetch-kind]')).toHaveLength(4)
    // 対象は**検索条件に一致する語**（ページではない）。ここでは出ている 2 語が一致している
    expect(dialog.text()).toContain('検索条件に一致する 2 語が対象です')
    expect(dialog.text()).toContain('うち取得済み 1 語')
    // 選ぶまでは呼ばない（AI は従量課金なので、押した瞬間に走らせない）
    expect(runCalls(fetchMock)).toHaveLength(0)
  })

  it('「取得済みをスキップ」を選ぶと、取得済みを外した語だけを AI に出す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-acquire]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-jp-fetch-skip]').trigger('click')
    await flushPromises()

    // 101 は詳細が取得済み、102 は未取得 → 102 だけ出す
    const call = runCalls(fetchMock).at(-1)
    expect(call?.body).toMatchObject({ kind: 'DETAIL', wordIds: [102] })
    // 窓は閉じ、取得後は一覧を取り直す
    expect(wrapper.find('[data-jp-fetch-dialog]').exists()).toBe(false)
    expect(wordListCalls(fetchMock).length).toBeGreaterThan(1)
    expect(toastMessages().some((message) => message.includes('詳細情報（A・B）'))).toBe(true)
  })

  it('「すべて再取得」を選ぶと、表示中の語をすべて AI に出す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-acquire]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-jp-fetch-reacquire]').trigger('click')
    await flushPromises()

    expect(runCalls(fetchMock).at(-1)?.body).toMatchObject({ kind: 'DETAIL', wordIds: [101, 102] })
  })

  it('窓をキャンセルしたら何も実行しない', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-acquire]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-jp-fetch-cancel]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-jp-fetch-dialog]').exists()).toBe(false)
    expect(runCalls(fetchMock)).toHaveLength(0)
    // 一覧も取り直さない（何もしていない）
    expect(wordListCalls(fetchMock)).toHaveLength(1)
  })

  it('窓で選んだ情報（C・D・E）を、その取得区分で実行する', async () => {
    const { wrapper, fetchMock } = await setup()

    const cases = [['C', 'C'], ['D', 'D'], ['E', 'E']] as const

    for (const [section, kind] of cases) {
      await wrapper.get('[data-jp-acquire]').trigger('click')
      await flushPromises()
      await wrapper.get(`[data-jp-fetch-kind="${section}"]`).setValue()
      // 取得済みをスキップ（C は 101 が失敗・102 が未取得なので 2 語が対象）
      await wrapper.get('[data-jp-fetch-skip]').trigger('click')
      await flushPromises()
      expect(runCalls(fetchMock).at(-1)?.body, section).toMatchObject({ kind, wordIds: [101, 102] })
    }
  })

  it('窓の「1 回の上限」は、設定ページの値（取得区分ごと）から取る', async () => {
    const { wrapper, fetchMock } = await setup({
      handlers: (url) =>
        url === '/api/admin/batch/japanese-word-ai/limits'
          ? ok({
            DETAIL: { batchCode: 'batC41', limit: 1 },
            C: { batchCode: 'batC42', limit: 50 },
            D: { batchCode: 'batC43', limit: 30 },
            E: { batchCode: 'batC44', limit: 50 }
          })
          : null
    })

    // 画面を開いた時点で上限を読みに行く（固定値を持たない）
    expect(recorded(fetchMock).some(
      (entry) => entry.method === 'GET' && entry.url === '/api/admin/batch/japanese-word-ai/limits'
    )).toBe(true)

    await wrapper.get('[data-jp-acquire]').trigger('click')
    await flushPromises()

    // A・B の上限は設定値 1 語 → 2 語のうち 1 語だけ対象、残りは次回
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('1 回の受付は 1 語')
    expect(wrapper.get('[data-jp-fetch-over-limit]').text()).toContain('1 語')

    await wrapper.get('[data-jp-fetch-skip]').trigger('click')
    await flushPromises()
    expect(runCalls(fetchMock).at(-1)?.body).toMatchObject({ kind: 'DETAIL', wordIds: [102] })

    // 区分ごとに違う（C は 50 語）
    await wrapper.get('[data-jp-acquire]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-jp-fetch-kind="C"]').setValue()
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('1 回の受付は 50 語')
  })

  it('検索条件の右上に、AI 取得の 4 バッチの状態を出す（有効・実行中・最後の結果）', async () => {
    const { wrapper } = await setup()

    // 状態は批次一覧と同じ入口（/api/admin/batch/tasks）から取る＝画面ごとに食い違わない
    const badges = wrapper.findAll('[data-jp-batch-state]')
    expect(badges).toHaveLength(4)
    expect(badges.map((badge) => badge.attributes('data-jp-batch-state')))
      .toEqual(['batC41', 'batC42', 'batC43', 'batC44'])
    // 区分名と状態が並ぶ
    expect(wrapper.get('[data-jp-batch-state="batC41"]').text()).toContain('A・B')
    expect(wrapper.get('[data-jp-batch-state="batC41"]').text()).toContain('実行中')
    expect(wrapper.get('[data-jp-batch-state="batC42"]').text()).toContain('正常終了')
    expect(wrapper.get('[data-jp-batch-state="batC43"]').text()).toContain('異常終了')
    expect(wrapper.get('[data-jp-batch-state="batC44"]').text()).toContain('無効')
    // マウスを載せると、批次コードと最終実行が分かる
    const title = wrapper.get('[data-jp-batch-state="batC42"]').attributes('title') ?? ''
    expect(title).toContain('batC42')
    expect(title).toContain('最終実行: 2026-09-27 10:01')
  })

  it('バッチの状態が読めなくても、一覧と AI 取得は使える（バッジを出さないだけ）', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        url === '/api/admin/batch/tasks' && method === 'GET' ? failure('読めません', 500) : null
    })

    expect(wrapper.findAll('[data-jp-batch-state]')).toHaveLength(0)
    // 一覧は出ている（状態が読めないだけで画面は壊さない）
    expect(wrapper.find('[data-jp-word-row="101"]').exists()).toBe(true)
    // 上限は読めているので AI 取得は押せる
    expect(wrapper.get('[data-jp-acquire]').attributes('disabled')).toBeUndefined()
  })

  it('上限が読めないときは実行できない（コード側の既定値へは落とさない）', async () => {    // 設定が未設定・不正だと /limits はエラーになる（サーバーが設定値だけで決める）
    const { wrapper } = await setup({
      handlers: (url, method) =>
        url === '/api/admin/batch/japanese-word-ai/limits' && method === 'GET'
          ? failure('設定「BAT_C41_BATCH_MAX」を設定してください。', 400)
          : null
    })

    // 押せない（title で理由を伝える）
    const button = wrapper.get('[data-jp-acquire]')
    expect(button.attributes('disabled')).toBeDefined()
    expect(button.attributes('title')).toContain('1 回の最大単語数')
    // 行の【AI 取得】も同じ
    const rowButton = wrapper.get('[data-jp-word-row="101"] [data-jp-row-acquire]')
    expect(rowButton.attributes('disabled')).toBeDefined()
    expect(rowButton.attributes('title')).toContain('1 回の最大単語数')
    // 窓も開かない
    expect(wrapper.find('[data-jp-fetch-dialog]').exists()).toBe(false)
  })

  it('すべて取得済みのときに「取得済みをスキップ」を選んだら、AI を呼ばずに知らせる', async () => {
    // A・B が 2 語とも取得済み
    const { wrapper, fetchMock } = await setup({
      items: [
        word(),
        { ...secondWord(), aiState: { detail: 'SUCCEEDED', reading: null, context: null, kanji: null } }
      ]
    })

    await wrapper.get('[data-jp-acquire]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-jp-fetch-skip]').trigger('click')
    await flushPromises()

    expect(runCalls(fetchMock)).toHaveLength(0)
    expect(wrapper.find('[data-jp-fetch-dialog]').exists()).toBe(false)
    expect(toastMessages().some((message) => message.includes('取得済み'))).toBe(true)
  })

  it('AI 取得に失敗したら、その理由を知らせる（一覧はそのまま）', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        url === '/api/admin/batch/japanese-word-ai/run' && method === 'POST'
          ? new Response(JSON.stringify({ success: false, message: 'AI を呼べませんでした。' }), {
            status: 500,
            headers: { 'Content-Type': 'application/json' }
          })
          : null
    })

    await wrapper.get('[data-jp-acquire]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-jp-fetch-kind="C"]').setValue()
    await wrapper.get('[data-jp-fetch-skip]').trigger('click')
    await flushPromises()

    // 失敗を黙って飲み込まない（理由をトーストに出し、一覧は消さない）
    expect(toastMessages().some((message) => message.includes('HTTP 500'))).toBe(true)
    expect(wrapper.find('[data-jp-words]').exists()).toBe(true)
  })

  it('様子見のたびに一覧を組み直さない（画面がちらつかない）', async () => {
    vi.useFakeTimers()
    try {
      // 2 回目以降の一覧は「取得済」で返す（様子見で取得状態が変わることを確かめる）
      let listCalls = 0
      const { wrapper } = await setup({
        handlers: (url, method) => {
          if (method === 'GET' && url.startsWith('/api/user/japanese/words')
            && !url.includes('/ai-targets')) {
            listCalls += 1
            const done = listCalls > 1
            return ok({
              items: done ? [word({ aiState: { detail: 'SUCCEEDED', reading: null, context: null, kanji: null } }), secondWord()] : [word(), secondWord()],
              totalElements: 2,
              page: 1,
              size: 20,
              totalPages: 1,
              totals: TOTALS
            })
          }
          if (method === 'GET' && url.startsWith('/api/admin/batch/japanese-word-ai/progress')) {
            return ok({ pending: 2, succeeded: 0, failed: 0 })
          }
          if (url === '/api/admin/batch/japanese-word-ai/run' && method === 'POST') {
            return ok({ accepted: 1, reused: 0, generationIds: [7] })
          }
          return null
        }
      })

      await wrapper.get('[data-jp-acquire]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-jp-fetch-skip]').trigger('click')
      await flushPromises()

      const table = wrapper.get('[data-jp-words]').element
      const searchButton = wrapper.get('[data-jp-search]')

      // 様子見の周期に入った直後（取得はまだ未解決）: 表を「読み込んでいます...」に差し替えない
      vi.advanceTimersByTime(3_000)
      await wrapper.vm.$nextTick()
      expect(wrapper.find('.jp-page__loading').exists()).toBe(false)
      expect(searchButton.attributes('disabled')).toBeUndefined()

      // 取得が終わって DOM を反映しても、**同じ表の要素**のまま（組み直さない＝ちらつかない）
      await flushPromises()
      expect(wrapper.get('[data-jp-words]').element).toBe(table)
      // 中身（取得状態）はちゃんと新しくなる
      expect(wrapper.get('[data-jp-word-row="101"] [data-jp-acquire-state="AB"]').text()).toContain('取得済')
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('AI 取得は受付だけして、進み具合が終わるまで様子を見る（非同期）', async () => {
    vi.useFakeTimers()
    try {
      // 受付が返す生成 ID で進み具合を数える（一覧の「取得状態」は取り直しの判定に使えない:
      // 成功済みの語はビューが「成功あり」を優先するので最初から SUCCEEDED に見える）
      let pending = 2
      let succeeded = 0
      const progressCalls: string[] = []
      const { wrapper, fetchMock } = await setup({
        handlers: (url, method) => {
          if (method === 'GET' && url.startsWith('/api/admin/batch/japanese-word-ai/progress')) {
            progressCalls.push(url)
            return ok({ pending, succeeded, failed: 0 })
          }
          if (url === '/api/admin/batch/japanese-word-ai/run' && method === 'POST') {
            // 受付だけしてすぐ返る（実行はバックエンドの働き手）
            return ok(
              { accepted: 1, reused: 0, generationIds: [7], batchCode: 'batC41', kind: 'DETAIL', wordIds: [102] },
              '日本語単語の AI 取得（DETAIL）を受付けました（受付 1 件 / 実行中 0 件）。バックグラウンドで取得します。'
            )
          }
          return null
        }
      })

      await wrapper.get('[data-jp-acquire]').trigger('click')
      await flushPromises()
      await wrapper.get('[data-jp-fetch-skip]').trigger('click')
      await flushPromises()

      // 受付の応答をそのまま知らせる（画面は実行を待たない）
      expect(toastMessages().some((message) => message.includes('受付けました'))).toBe(true)
      const afterAccept = wordListCalls(fetchMock).length

      // まだ実行中（pending > 0）。一覧も読み直して「取得中」を出す
      await vi.advanceTimersByTimeAsync(3_000)
      await flushPromises()
      expect(progressCalls).toHaveLength(1)
      expect(progressCalls[0]).toContain('generationIds=7')
      expect(wordListCalls(fetchMock).length).toBeGreaterThan(afterAccept)
      expect(toastMessages().some((message) => message.includes('取得が終わりました'))).toBe(false)

      // 終わったら結果を知らせて、様子見を止める
      pending = 0
      succeeded = 1
      await vi.advanceTimersByTimeAsync(3_000)
      await flushPromises()
      expect(toastMessages().some((message) => message.includes('取得が終わりました（成功 1 / 失敗 0）'))).toBe(true)

      const afterFinish = progressCalls.length
      await vi.advanceTimersByTimeAsync(9_000)
      await flushPromises()
      expect(progressCalls).toHaveLength(afterFinish)
      wrapper.unmount()
    } finally {
      vi.useRealTimers()
    }
  })

  it('【新規】は【検索】の右隣にあり、新画面の登録画面を開く', async () => {
    const { wrapper } = await setup()

    const head = wrapper.get('.search-panel__head')
    // 一覧の操作（検索・新規・再読み込み）は検索条件の行にまとめる。
    // 条件をまとめて消す【リセット】は利用者の指示で外した（各項目を直接消す）
    expect(head.findAll('.search-panel__actions')[0].findAll('button').map((button) => button.text().trim()))
      .toEqual(['検索', '新規', '再読み込み'])
    expect(head.find('[data-jp-reset]').exists()).toBe(false)
    expect(head.find('[data-jp-add]').exists()).toBe(true)
    // 【再読み込み】は一覧の見出しではなく、この行にある（利用者の指示）
    expect(head.find('[data-jp-refresh]').exists()).toBe(true)

    await wrapper.get('[data-jp-add]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-demo-new-dialog]').exists()).toBe(true)
  })

  it('一覧の見出しは件数を右端に出す（絞り込み中はその左）', async () => {
    const { wrapper } = await setup()

    const header = wrapper.get('.card__header')
    const children = header.element.children
    // 見出しの最後のまとまりに件数が入っている＝右端に寄る
    const last = children[children.length - 1]
    expect(last.contains(wrapper.get('[data-jp-count]').element)).toBe(true)
    expect(wrapper.get('[data-jp-count]').text()).toContain('全 2 件')
    // 再読み込みのボタンは見出しから外した
    expect(header.find('[data-jp-refresh]').exists()).toBe(false)

    // 「絞り込み中」は件数の左（件数が右端のまま）
    await wrapper.get('[data-jp-filter="keyword"]').setValue('勉強')
    await flushPromises()
    const group = wrapper.get('[data-jp-filtered-count]').element.parentElement
    expect(group?.lastElementChild?.hasAttribute('data-jp-count')).toBe(true)
  })

  it('単語列の単語は、外部の辞書（有道）へのリンクになっている', async () => {
    const { wrapper } = await setup()

    const link = wrapper.get('[data-jp-word-row="101"] [data-jp-word-link]')
    // 文字はそのまま出しつつ、リンクにする（利用者の指示 2026-09-26）
    expect(link.element.tagName).toBe('A')
    expect(link.text()).toBe('勉強')

    const url = new URL(link.attributes('href') ?? '')
    expect(`${url.origin}${url.pathname}`).toBe('https://www.youdao.com/result')
    expect(url.searchParams.get('word')).toBe('勉強')
    expect(url.searchParams.get('lang')).toBe('ja')

    // 別タブで開く（この画面を置き換えない）＋ noopener（開いた側から操作させない）
    expect(link.attributes('target')).toBe('_blank')
    expect(link.attributes('rel')).toContain('noopener')

    // 行ごとに自分の語を指す
    const second = wrapper.get('[data-jp-word-row="102"] [data-jp-word-link]')
    expect(new URL(second.attributes('href') ?? '').searchParams.get('word')).toBe('図書館')
  })

  it('単語列の単語は、外部の辞書（有道）へのリンクになっている（例）', async () => {
    // 利用者が示した例: 単語が「えくぼ」なら
    // https://www.youdao.com/result?word=えくぼ&lang=ja
    const { wrapper } = await setup({ items: [word({ wordId: 200, word: 'えくぼ' })] })

    const link = wrapper.get('[data-jp-word-row="200"] [data-jp-word-link]')
    const url = new URL(link.attributes('href') ?? '')
    expect(url.searchParams.get('word')).toBe('えくぼ')
    expect(url.searchParams.get('lang')).toBe('ja')
  })

  it('行の【AI 取得】のアイコンは主色（詳細・修正・削除と色で見分ける）', async () => {
    const { wrapper } = await setup()

    const icon = wrapper.get('[data-jp-word-row="101"] [data-jp-row-acquire] .icon')
    expect(icon.classes()).toContain('icon--ai')

    // jsdom は app.css を適用しないので、規則そのものを確かめる
    const appCss = readFileSync(resolve(process.cwd(), 'src/assets/app/app.css'), 'utf8')
    const ruleStart = appCss.indexOf('.icon--ai,')
    expect(ruleStart).toBeGreaterThanOrEqual(0)
    const rule = appCss.slice(ruleStart, appCss.indexOf('}', ruleStart))
    expect(rule).toContain('color: var(--color-primary)')
    // アイコンボタンのホバー背景も淡い同系色（ほかの色付きアイコンと同じ扱い）
    expect(appCss).toContain(':has(> .icon--ai):hover:not(:disabled)')
    expect(appCss).toContain('background: var(--color-primary-soft)')
  })

  it('単語リンクは下線つきで、本文より目を引く色にする（利用者の指示）', async () => {
    // jsdom は japanese.css を適用しないので、規則そのものを確かめる
    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')

    const linkStart = css.indexOf('.jp-word--link {')
    expect(linkStart).toBeGreaterThanOrEqual(0)
    const linkRule = css.slice(linkStart, css.indexOf('}', linkStart))
    // 常に下線（hover のときだけでは、文字と見分けがつかない）
    expect(linkRule).toContain('text-decoration: underline')
    // 本文（`.jp-word` ＝濃い文字色）より目を引く色にする
    expect(linkRule).toContain('color: var(--color-primary)')
    expect(linkRule).not.toContain('color: var(--color-text-strong)')

    // `.jp-word` と同じ強さの規則なので、**あとに書いて**上書きする
    const baseStart = css.indexOf('.jp-word {')
    expect(baseStart).toBeGreaterThanOrEqual(0)
    expect(linkStart).toBeGreaterThan(baseStart)

    const hoverRule = css.slice(css.indexOf('.jp-word--link:hover'))
    expect(hoverRule.slice(0, hoverRule.indexOf('}'))).toContain('color: var(--color-primary-hover)')
  })

  it('操作列のアイコンは田の字（2 行 × 2 列）に並べる', async () => {
    const { wrapper } = await setup()

    const cell = wrapper.get('[data-jp-word-row="101"] td.row-actions')
    // td 自身の display は変えない（表のセルでなくなり行の高さが崩れる。中国語訳・詳細情報件数と同じ型）
    expect(cell.classes()).not.toContain('jp-row-actions__grid')
    const grid = cell.get('.jp-row-actions__grid')
    expect(grid.element.tagName).toBe('SPAN')
    expect(grid.findAll('button')).toHaveLength(4)

    // jsdom にレイアウトが無いので、CSS の規則そのものを確かめる
    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')
    const start = css.indexOf('.jp-row-actions__grid {')
    expect(start).toBeGreaterThanOrEqual(0)
    const rule = css.slice(start, css.indexOf('}', start))
    expect(rule).toContain('display: grid')
    expect(rule).toContain('grid-template-columns: repeat(2, max-content)')
  })

  it('単語・読み方・品詞・中国語訳は 1 列にまとめ、中国語訳を 2 行目に出す', async () => {
    const { wrapper } = await setup()

    // 見出しも 1 つにまとめる（4 列ぶんの見出しを並べない）
    const headers = wrapper.findAll('[data-jp-words] thead th').map((th) => th.text())
    expect(headers).toContain('単語・読み方・品詞・中国語訳')
    expect(headers).not.toContain('読み方')
    expect(headers).not.toContain('品詞')
    expect(headers).not.toContain('中国語訳')

    const cell = wrapper.get('[data-jp-word-row="101"] [data-jp-word-summary]')
    const lines = cell.get('.jp-word-summary').findAll(':scope > span')
    // 1 行目: 単語（リンク）・読み方・品詞 / 2 行目: 中国語訳
    expect(lines).toHaveLength(2)
    expect(lines[0]!.get('[data-jp-word-link]').text()).toBe('勉強')
    expect(lines[0]!.text()).toContain('べんきょう')
    expect(lines[0]!.text()).toContain('名詞')
    expect(lines[0]!.find('[data-jp-chinese]').exists()).toBe(false)
    expect(lines[1]!.attributes('data-jp-chinese')).toBeDefined()
    expect(lines[1]!.text()).toBe('学习；用功')

    // 収録が無い語は書籍・分類が「—」、詳細が無い語は中国語訳も「—」
    const second = wrapper.get('[data-jp-word-row="102"] [data-jp-word-summary]')
    expect(second.get('[data-jp-chinese]').text()).toBe('—')
  })

  it('単語リンクは表の本文より 1 段大きくする（利用者の指示）', async () => {
    const css = readFileSync(resolve(process.cwd(), 'src/features/japanese/japanese.css'), 'utf8')
    const linkStart = css.indexOf('.jp-word--link {')
    expect(linkStart).toBeGreaterThanOrEqual(0)
    const linkRule = css.slice(linkStart, css.indexOf('}', linkStart))
    expect(linkRule).toContain('font-size: var(--fs-md)')

    // 「1 段大きい」ことをトークンの並びで確かめる（表の本文は --fs-sm）
    const tokens = readFileSync(
      resolve(process.cwd(), 'src/assets/prototype/tokens.css'), 'utf8'
    )
    const scale = ['--fs-2xs', '--fs-xs', '--fs-sm', '--fs-md', '--fs-lg', '--fs-xl']
      .map((name) => ({ name, size: Number((tokens.match(new RegExp(`${name}:\\s*(\\d+)px`)) ?? [])[1]) }))
    expect(scale.every((entry) => Number.isFinite(entry.size))).toBe(true)
    const tableSize = scale.find((entry) => entry.name === '--fs-sm')!.size
    const nextUp = scale.filter((entry) => entry.size > tableSize).sort((a, b) => a.size - b.size)[0]!
    // 表（13px）の 1 段上は --fs-md（14px）
    expect(nextUp.name).toBe('--fs-md')
  })

  it('操作列は左端のアイコンボタン（詳細・AI 取得・修正・削除）', async () => {
    const { wrapper } = await setup()

    const row = wrapper.get('[data-jp-word-row="101"]')
    // 先頭のセル（左端）が操作列
    const actionCell = row.get('td')
    expect(actionCell.classes()).toContain('row-actions')
    const buttons = actionCell.findAll('button')
    // 行ごとの AI 取得（その 1 語だけを取る）を足した（利用者の指示 2026-09-26）
    expect(buttons.map((button) => button.attributes('title'))).toEqual(['詳細', 'AI 取得', '修正', '削除'])
    for (const button of buttons) {
      // アイコンだけ（文字を持たない）が、意味は aria-label で伝える
      expect(button.text()).toBe('')
      expect(button.find('.icon').exists()).toBe(true)
      expect(button.attributes('aria-label')).toBeTruthy()
    }

    // お気に入り・習得済のアイコンは一覧から外した（操作は詳細・AI 取得・修正・削除の 4 つ）
    expect(wrapper.findAll('[data-jp-words] tbody [data-jp-favorite]').length).toBe(0)
    expect(wrapper.findAll('[data-jp-words] tbody [data-jp-learned]').length).toBe(0)
    expect(wrapper.findAll('[data-jp-words] tbody [data-jp-detail]').length).toBe(2)
    expect(wrapper.findAll('[data-jp-words] tbody [data-jp-row-acquire]').length).toBe(2)
    expect(wrapper.findAll('[data-jp-words] tbody [data-jp-edit]').length).toBe(2)
    expect(wrapper.findAll('[data-jp-words] tbody [data-jp-delete]').length).toBe(2)
  })

  it('取得ボタンの左に縦罫を置かない（利用者の指示）', async () => {
    const { wrapper } = await setup()

    // 検索条件の行は「一覧の操作」と「AI 取得」の 2 まとまりだが、区切りは余白だけにする
    const group = wrapper.get('[data-jp-acquire-actions]')
    expect(group.classes()).toContain('search-panel__actions')
    expect(group.classes()).not.toContain('jp-head-actions__acquire')

    // jsdom は `<style scoped>` を適用しないので、規則そのものが無いことを確かめる
    const source = readFileSync(
      resolve(process.cwd(), 'src/views/japanese/JapaneseWordView.vue'), 'utf8'
    )
    expect(source).not.toContain('jp-head-actions__acquire')
    // 区切りの縦罫はこの画面の検索条件の行には置かない
    expect(source).not.toContain('border-left')
  })

  it('行の【AI 取得】は、その 1 語だけを窓から取得する', async () => {
    const { wrapper, fetchMock } = await setup()

    // まだ取得していない語（102）の行から開く
    await wrapper.get('[data-jp-word-row="102"] [data-jp-row-acquire]').trigger('click')
    await flushPromises()

    // 窓は一覧のときと同じで、対象の説明だけが「この単語」になる
    const dialog = wrapper.get('[data-jp-fetch-dialog]')
    expect(dialog.text()).toContain('この単語（図書館）が対象です')
    expect(dialog.text()).toContain('うち取得済み 0 語')
    expect(wrapper.findAll('[data-jp-fetch-kind]')).toHaveLength(4)
    expect(runCalls(fetchMock)).toHaveLength(0)

    await wrapper.get('[data-jp-fetch-skip]').trigger('click')
    await flushPromises()

    // その 1 語だけを AI に出す（一覧の 2 語ではない）
    expect(runCalls(fetchMock).at(-1)?.body).toMatchObject({ kind: 'DETAIL', wordIds: [102] })
  })

  it('行の【AI 取得】で、その語が取得済みなら呼ばずに知らせる', async () => {
    const { wrapper, fetchMock } = await setup()

    // 101 は詳細が取得済み → スキップすると 0 語
    await wrapper.get('[data-jp-word-row="101"] [data-jp-row-acquire]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('この単語（勉強）が対象です')
    expect(wrapper.get('[data-jp-fetch-summary]').text()).toContain('うち取得済み 1 語')
    expect(wrapper.get('[data-jp-fetch-skip]').text()).toContain('未取得と失敗の 0 語')

    await wrapper.get('[data-jp-fetch-skip]').trigger('click')
    await flushPromises()

    expect(runCalls(fetchMock)).toHaveLength(0)
    expect(toastMessages().some((message) => message.includes('取得済み'))).toBe(true)
  })

  it('該当がなければその案内を出す', async () => {
    const { wrapper } = await setup({ items: [], totalElements: 0 })

    expect(wrapper.get('[data-jp-empty]').text()).toContain('該当する単語がありません。')
    expect(wrapper.find('[data-jp-word-row="101"]').exists()).toBe(false)
  })

  it('読み込みに失敗したらエラーを alert に出す', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        method === 'GET' && url.startsWith('/api/user/japanese/words?')
          ? failure('サーバーでエラーが発生しました。')
          : null
    })

    expect(wrapper.get('.alert.alert--danger').text()).toContain('サーバーでエラーが発生しました。')
  })



  it('修正すると表示していた version を PUT で送る', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-word-row="101"] [data-jp-edit]').trigger('click')
    await flushPromises()
    expect((wrapper.get('[data-demo-edit-heading]').element as HTMLInputElement).value).toBe('勉強')
    expect((wrapper.get('[data-demo-edit-reading]').element as HTMLInputElement).value).toBe('べんきょう')
    expect(wrapper.findAll('[data-demo-editor-nav-item]')).toHaveLength(11)
    expect(wrapper.find('[data-demo-regenerate]').exists()).toBe(false)

    await wrapper.get('[data-demo-edit-heading]').setValue('勉強（改）')
    await wrapper.get('[data-demo-editor-save]').trigger('click')
    await flushPromises()

    const put = recorded(fetchMock).find((entry) => entry.method === 'PUT')
    expect(put?.url).toBe('/api/user/japanese/words/101/editor')
    expect(put?.body).toMatchObject({ word: { word: '勉強（改）', version: 2 }, detail: { manuallyCorrected: true } })
    expect(wrapper.get('[data-demo-save-saved]').text()).toContain('保存しました')
    expect(wrapper.find('[data-jp-word-dialog]').exists()).toBe(true)
  })

  it('登録画面のキャンセルは送信しない', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-add]').trigger('click')
    await wrapper.get('[data-demo-word-grid]').trigger('paste', {
      clipboardData: { getData: () => '書きかけ' }
    })
    await flushPromises()
    await wrapper.get('[data-demo-cancel]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-demo-new-dialog]').exists()).toBe(false)
    expect(recorded(fetchMock).some((entry) => entry.method === 'POST' || entry.method === 'PUT')).toBe(false)
  })

  it('削除は確認してから DELETE を呼び、キャンセルなら呼ばない', async () => {
    const confirmSpy = vi.spyOn(window, 'confirm').mockReturnValue(false)
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-word-row="101"] [data-jp-delete]').trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).some((entry) => entry.method === 'DELETE')).toBe(false)

    confirmSpy.mockReturnValue(true)
    await wrapper.get('[data-jp-word-row="101"] [data-jp-delete]').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some(
      (entry) => entry.method === 'DELETE' && entry.url === '/api/user/japanese/words/101'
    )).toBe(true)
    expect(toastMessages()).toContain('単語を削除しました。')
    confirmSpy.mockRestore()
  })


















  it('件数とページを変えるとその条件で検索し直す', async () => {
    const { wrapper, fetchMock } = await setup({ totalElements: 60 })

    expect(wrapper.get('.pagination__info').text()).toContain('全 60 件（1 / 3 ページ）')

    const pages = wrapper.findAll('.pagination__pages .page-btn')
    const third = pages.find((button) => button.text() === '3')
    expect(third).toBeDefined()
    await third?.trigger('click')
    await flushPromises()
    expect(recorded(fetchMock).at(-1)?.url).toBe('/api/user/japanese/words?page=3&size=20')

    await wrapper.get('[data-jp-page-size]').setValue('50')
    await flushPromises()
    expect(recorded(fetchMock).at(-1)?.url).toBe('/api/user/japanese/words?page=1&size=50')
    expect(wrapper.get('.pagination__info').text()).toContain('全 60 件（1 / 2 ページ）')
  })

  it('再読み込みは今の条件のまま取り直す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-filter="keyword"]').setValue('勉強')
    await wrapper.get('[data-jp-search]').trigger('click')
    await flushPromises()
    const before = recorded(fetchMock).filter(
      (entry) => entry.method === 'GET' && entry.url.startsWith('/api/user/japanese/words?')
    ).length

    await wrapper.get('[data-jp-refresh]').trigger('click')
    await flushPromises()

    const after = recorded(fetchMock).filter(
      (entry) => entry.method === 'GET' && entry.url.startsWith('/api/user/japanese/words?')
    )
    expect(after.length).toBe(before + 1)
    expect(after.at(-1)?.url).toContain(`keyword=${encodeURIComponent('勉強')}`)
  })
})

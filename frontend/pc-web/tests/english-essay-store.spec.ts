import { beforeEach, describe, expect, it, vi } from 'vitest'
import type { EnglishEssayDetail, EnglishEssayGrading, EnglishEssayPage } from '@/api/english-essay'
import {
  addGradingRound,
  clearEssayList,
  createEssay,
  essayList,
  essayTotal,
  essayTotalPages,
  hasPendingRounds,
  latestGrading,
  listStateOf,
  loadEssay,
  loadEssays,
  recognizeEssay,
  removeEssay,
  resetEssays,
  saveEssay,
  uploadEssayImage,
  type EssayListItem
} from '@/features/english-essay/store'

/**
 * 英作文の保管と AI 添削（**本物の API**）。
 *
 * <p>2.0 のように localStorage へ持つのをやめ、user-api（作文・画像・添削の受付）と
 * admin-api（文字認識）に繋いだ。ここで見張る接縫は**このモジュールの公開関数**だけ: 送る
 * リクエスト（URL・メソッド・本文・multipart）と、返ってきたものを画面が使う形に組み替えた結果。</p>
 */

/** 統一レスポンス（`data` を包む）。 */
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

type Call = { url: string; method: string; body: unknown }

function callsOf(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      body: init.body ?? null
    }
  })
}

/** JSON の本文（POST / PUT）。 */
function jsonOf(call: Call | undefined): Record<string, unknown> {
  return JSON.parse(String(call?.body)) as Record<string, unknown>
}

/** multipart の本文。 */
function formOf(call: Call | undefined): FormData {
  return call?.body as FormData
}

/** 呼ばれた URL とメソッドで応答を決める（未定義は失敗させる）。 */
function stubFetch(handler: (url: string, method: string, init: RequestInit) => Response | undefined): ReturnType<typeof vi.fn> {
  const fetchMock = vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const url = String(input)
    const method = (init?.method ?? 'GET').toUpperCase()
    const handled = handler(url, method, init ?? {})
    if (handled) {
      return handled
    }
    throw new Error(`未定義の呼び出し: ${method} ${url}`)
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

const QUESTION = 'Some people say that students should learn English in elementary school. Do you agree with this opinion? Give two reasons to support your answer.'
const ESSAY = 'I think that students should learn English in elementary school. Because English is very useful for our future.'

/** 添削 1 回（**新形式**のレポート JSON。2.0 の移行データと同じ形）。 */
function grading(round: number, overrides: Partial<EnglishEssayGrading> = {}): EnglishEssayGrading {
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
    report: {
      status: 'AI_GRADED',
      version: 'eiken-ai-v1',
      level: 'GRADE1',
      score: 20 + round,
      maxScore: 32,
      rubricMax: 8,
      rubricValues: [6, 5, 5, 5],
      rubricNotes: ['内容の講評', '構成の講評', '語彙の講評', '文法の講評'],
      modelAnswer: `Improved essay number ${round}.`,
      wordRequirement: '200〜240語',
      taskRequirements: ['設問の問いに答える', '理由を 2 つ以上挙げる', '結論を述べる'],
      warnings: [],
      japanese: {
        title: `前回の指摘が活きています（${round}）`,
        summary: `第 ${round} 回の要約です。`,
        tags: ['設問適合', '理由の充実', '接続表現'],
        rubric: ['内容', '構成', '語彙', '文法'],
        rubricNotes: ['内容の講評', '構成の講評', '語彙の講評', '文法の講評'],
        corrections: [['I think', 'I believe', '表現', '繰り返しを避けます。']],
        advice: '結論の前に理由を 2 回示すと評価が上がります。',
        notice: '本結果は公式採点ではありません。'
      },
      chinese: {
        title: '上次指出的问题已改善',
        summary: `第 ${round} 次批改的总结。`,
        tags: ['切题', '理由充分', '连接词'],
        rubric: ['内容', '结构', '词汇', '语法'],
        rubricNotes: ['内容的评语', '结构的评语', '词汇的评语', '语法的评语'],
        corrections: [['I think', 'I believe', '表达', '避免重复。']],
        advice: '结论前用两次理由更容易体现数量。',
        notice: '本结果并非官方评分。'
      }
    },
    failureReason: null,
    startedAt: '2026-09-26T20:29:00.000Z',
    finishedAt: '2026-09-26T20:30:00.000Z',
    createdAt: `2026-09-2${round}T20:30:00.000Z`,
    ...overrides
  }
}

/** 詳細（既定は 3 回添削ずみ・画像 3 枚・GRADE1）。 */
function detail(overrides: Partial<EnglishEssayDetail> = {}): EnglishEssayDetail {
  return {
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
      {
        imageId: '11', order: 1, category: 'question', originalFileName: 'essay-3-q1.png',
        mimeType: 'image/png', fileSize: 1024, recognizedText: null, confidence: null
      },
      {
        imageId: '12', order: 2, category: 'answer', originalFileName: 'essay-3-a1.png',
        mimeType: 'image/png', fileSize: 2048, recognizedText: null, confidence: null
      },
      {
        imageId: '13', order: 3, category: 'answer', originalFileName: 'essay-3-a2.png',
        mimeType: 'image/png', fileSize: 2048, recognizedText: null, confidence: null
      }
    ],
    gradings: [grading(1), grading(2), grading(3)],
    ...overrides
  }
}

/** 一覧の 1 行。 */
function row(overrides: Partial<EnglishEssayPage['items'][number]> = {}): EnglishEssayPage['items'][number] {
  return {
    essayId: 'essay-1',
    level: 'GRADE2',
    title: '小学生からの英語教育',
    titleZh: '小学英语教育',
    questionText: QUESTION,
    essayText: ESSAY,
    wordCount: 20,
    imageCount: 2,
    questionImageCount: 1,
    answerImageCount: 1,
    createdAt: '2026-09-20T09:12:00.000Z',
    updatedAt: '2026-09-20T09:20:00.000Z',
    latestGrading: {
      gradingId: '101', round: 1, statusCode: 'SUCCEEDED', score: 21, maxScore: 32,
      createdAt: '2026-09-20T09:20:00.000Z'
    },
    ...overrides
  }
}

/** 一覧のページ。 */
function page(items: EnglishEssayPage['items'], overrides: Partial<EnglishEssayPage> = {}): EnglishEssayPage {
  return { items, total: items.length, page: 1, size: 5, totalPages: 1, ...overrides }
}

/** 一覧の 1 行（画面が使う形）。 */
function itemOf(overrides: Partial<EssayListItem> = {}): EssayListItem {
  return {
    id: 'essay-1',
    level: 'GRADE2',
    title: '小学生からの英語教育',
    titleZh: '小学英语教育',
    questionText: QUESTION,
    essayText: ESSAY,
    wordCount: 20,
    imageCount: 2,
    questionImageCount: 1,
    answerImageCount: 1,
    createdAt: '2026-09-20T09:12:00.000Z',
    updatedAt: '2026-09-20T09:20:00.000Z',
    latestGrading: {
      gradingId: '101', round: 1, statusCode: 'SUCCEEDED', score: 21, maxScore: 32,
      createdAt: '2026-09-20T09:20:00.000Z'
    },
    ...overrides
  }
}

/** 画像 1 枚（アップロード・文字認識に渡す）。 */
function imageFile(name = 'answer.png'): File {
  return new File([new Uint8Array([1, 2, 3])], name, { type: 'image/png' })
}

describe('英作文の保管（API 版）', () => {
  beforeEach(() => {
    resetEssays()
  })

  it('一覧は絞り込みとページングをクエリで送り、返ってきた 1 ページを載せる', async () => {
    const fetchMock = stubFetch((url, method) => {
      if (method === 'GET' && url.startsWith('/api/user/english-essays?')) {
        return ok(page([row()], { total: 12, page: 2, size: 5, totalPages: 3 }))
      }
      return undefined
    })

    await loadEssays({ keyword: ' 部活動 ', level: 'GRADE1', dateFrom: '2026-09-01', dateTo: '2026-09-30' }, 2, 5)

    const call = callsOf(fetchMock).at(-1)
    expect(call?.url).toBe(
      '/api/user/english-essays?keyword=%E9%83%A8%E6%B4%BB%E5%8B%95&level=GRADE1'
      + '&dateFrom=2026-09-01&dateTo=2026-09-30&page=2&size=5'
    )
    expect(essayList.value.map((essay) => essay.id)).toEqual(['essay-1'])
    expect(essayTotal.value).toBe(12)
    expect(essayTotalPages.value).toBe(3)
  })

  it('条件が空なら送らない（未指定と空文字を区別する）', async () => {
    const fetchMock = stubFetch((url, method) =>
      method === 'GET' && url.startsWith('/api/user/english-essays?') ? ok(page([])) : undefined)

    await loadEssays({ keyword: '   ', level: '', dateFrom: '', dateTo: '' }, 1, 5)

    expect(callsOf(fetchMock).at(-1)?.url).toBe('/api/user/english-essays?page=1&size=5')
    expect(essayTotalPages.value).toBe(1)
  })

  it('状態は latestGrading から決める（未添削／添削中／添削済み／失敗）', () => {
    expect(listStateOf(itemOf({ latestGrading: null }))).toBe('NONE')
    expect(latestGrading(itemOf({ latestGrading: null }))).toBeNull()
    for (const statusCode of ['QUEUED', 'RUNNING']) {
      const item = itemOf({
        latestGrading: { gradingId: '102', round: 2, statusCode, score: null, maxScore: null, createdAt: '' }
      })
      expect(listStateOf(item)).toBe('PENDING')
    }
    expect(listStateOf(itemOf())).toBe('SUCCEEDED')
    for (const statusCode of ['FAILED', 'CANCELED']) {
      const item = itemOf({
        latestGrading: { gradingId: '103', round: 2, statusCode, score: null, maxScore: null, createdAt: '' }
      })
      expect(listStateOf(item)).toBe('FAILED')
    }
  })

  it('詳細は画像の URL と、成功した添削の历次（古い順）に組み替える', async () => {
    stubFetch((url, method) =>
      method === 'GET' && url === '/api/user/english-essays/essay-3' ? ok(detail()) : undefined)

    const loaded = await loadEssay('essay-3')

    expect(loaded?.essay.id).toBe('essay-3')
    expect(loaded?.essay.level).toBe('GRADE1')
    expect(loaded?.essay.wordCount).toBe(47)
    // 画像は API の URL をそのまま `<img :src>` に使える形にする
    expect(loaded?.essay.images.map((image) => image.dataUrl)).toEqual([
      '/api/user/english-essays/essay-3/images/11',
      '/api/user/english-essays/essay-3/images/12',
      '/api/user/english-essays/essay-3/images/13'
    ])
    expect(loaded?.essay.images.map((image) => image.category)).toEqual(['question', 'answer', 'answer'])
    expect(loaded?.essay.images.map((image) => image.order)).toEqual([1, 2, 3])
    // 「成功した回」だけが画面の历次になる
    expect(loaded?.essay.gradings.map((item) => item.id)).toEqual(['grading-101', 'grading-102', 'grading-103'])
    expect(loaded?.rounds.map((round) => round.statusCode)).toEqual(['SUCCEEDED', 'SUCCEEDED', 'SUCCEEDED'])
    expect(hasPendingRounds(loaded?.rounds ?? [])).toBe(false)
  })

  it('新形式のレポートを 4 観点（点数は rubricValues・講評は rubricNotes）に組み立てる', async () => {
    stubFetch((url, method) =>
      method === 'GET' && url === '/api/user/english-essays/essay-3' ? ok(detail()) : undefined)

    const loaded = await loadEssay('essay-3')
    const latest = loaded?.essay.gradings.at(-1)

    expect(latest?.score).toBe(23)
    expect(latest?.maxScore).toBe(32)
    expect(latest?.rubricMax).toBe(8)
    expect(latest?.japanese.rubric.map((item) => item.key)).toEqual(['内容', '構成', '語彙', '文法'])
    expect(latest?.japanese.rubric.map((item) => item.score)).toEqual([6, 5, 5, 5])
    expect(latest?.japanese.rubric.every((item) => item.maxScore === 8)).toBe(true)
    expect(latest?.japanese.rubric[0]?.note).toBe('内容の講評')
    // corrections は配列の配列（新形式）
    expect(latest?.japanese.corrections[0]).toEqual({
      original: 'I think', corrected: 'I believe', category: '表現', reason: '繰り返しを避けます。'
    })
    expect(latest?.modelAnswer).toBe('Improved essay number 3.')
    expect(latest?.taskRequirements).toEqual({
      kind: 'list',
      items: ['設問の問いに答える', '理由を 2 つ以上挙げる', '結論を述べる']
    })
    expect(latest?.wordRequirement).toBe('200〜240語')
    expect(latest?.titleJa).toBe('部活動の時間を増やすべきか')
  })

  it('未完了の回があれば様子見の対象になり、失敗した回は report を持たない', async () => {
    stubFetch((url, method) => {
      if (method === 'GET' && url === '/api/user/english-essays/essay-5') {
        return ok(detail({
          essayId: 'essay-5',
          gradings: [
            grading(1, { statusCode: 'FAILED', score: null, maxScore: null, report: null, failureReason: 'AI が混んでいます' }),
            grading(2, { gradingId: '200', statusCode: 'RUNNING', score: null, maxScore: null, report: null, round: 2 })
          ]
        }))
      }
      return undefined
    })

    const loaded = await loadEssay('essay-5')

    expect(hasPendingRounds(loaded?.rounds ?? [])).toBe(true)
    expect(loaded?.essay.gradings).toHaveLength(0)
    expect(loaded?.rounds[0]?.failureReason).toBe('AI が混んでいます')
    expect(loaded?.rounds[1]?.report).toBeNull()
  })

  it('中身が変わっていなければ同じオブジェクトを返す（3 秒ごとの読み直しでちらつかせない）', async () => {
    stubFetch((url, method) =>
      method === 'GET' && url === '/api/user/english-essays/essay-3' ? ok(detail()) : undefined)

    const first = await loadEssay('essay-3')
    const second = await loadEssay('essay-3')

    expect(second).toBe(first)
    expect(second?.essay).toBe(first?.essay)
  })

  it('削除済み・不明は null にする（404 はエラーにしない）', async () => {
    stubFetch((url, method) =>
      method === 'GET' && url === '/api/user/english-essays/essay-unknown' ? notFound() : undefined)

    await expect(loadEssay('essay-unknown')).resolves.toBeNull()
  })

  it('新規は本文だけを POST する（画像は上げてから並びを更新する）', async () => {
    const fetchMock = stubFetch((url, method) => {
      if (method === 'POST' && url === '/api/user/english-essays') {
        return ok(detail({ essayId: 'essay-9', gradings: [], images: [] }))
      }
      return undefined
    })

    const created = await createEssay({
      level: 'PRE1',
      title: '新しい英作文',
      titleZh: '新的英语作文',
      questionText: 'Do you like reading?',
      essayText: 'I like reading books very much.'
    })

    expect(jsonOf(callsOf(fetchMock).at(-1))).toEqual({
      level: 'PRE1',
      title: '新しい英作文',
      titleZh: '新的英语作文',
      questionText: 'Do you like reading?',
      essayText: 'I like reading books very much.'
    })
    expect(created.id).toBe('essay-9')
  })

  it('更新は本文と、残す画像の並び・区分を PUT する', async () => {
    const fetchMock = stubFetch((url, method) =>
      method === 'PUT' && url === '/api/user/english-essays/essay-3' ? ok(detail()) : undefined)

    await saveEssay('essay-3', {
      level: 'GRADE1',
      title: '書き直した題',
      titleZh: '重写的题目',
      questionText: QUESTION,
      essayText: ESSAY,
      images: [{ imageId: '13', category: 'question', order: 1 }]
    })

    expect(jsonOf(callsOf(fetchMock).at(-1))).toEqual({
      level: 'GRADE1',
      title: '書き直した題',
      titleZh: '重写的题目',
      questionText: QUESTION,
      essayText: ESSAY,
      images: [{ imageId: '13', category: 'question', order: 1 }]
    })
  })

  it('削除は DELETE を送る', async () => {
    const fetchMock = stubFetch((url, method) =>
      method === 'DELETE' && url === '/api/user/english-essays/essay-3'
        ? new Response(null, { status: 204 })
        : undefined)

    await removeEssay('essay-3')

    expect(callsOf(fetchMock).at(-1)).toMatchObject({
      method: 'DELETE',
      url: '/api/user/english-essays/essay-3'
    })
  })

  it('添削は user-api へ**受付**を送るだけ（すぐ返る）', async () => {
    const fetchMock = stubFetch((url, method) =>
      method === 'POST' && url === '/api/user/english-essays/essay-3/gradings'
        ? ok({ gradingId: '301', round: 4, message: '受付けました' })
        : undefined)

    const accepted = await addGradingRound('essay-3')

    expect(callsOf(fetchMock).at(-1)).toMatchObject({
      method: 'POST',
      url: '/api/user/english-essays/essay-3/gradings'
    })
    // 回は渡さない（キーごと送らない＝次の回はサーバーが決める）
    expect(jsonOf(callsOf(fetchMock).at(-1))).toEqual({})
    expect(accepted).toEqual({ gradingId: '301', round: 4, message: '受付けました' })
  })

  it('添削の受付に回を渡したときだけ、本文に round を載せる', async () => {
    const fetchMock = stubFetch((url, method) =>
      method === 'POST' && url === '/api/user/english-essays/essay-3/gradings'
        ? ok({ gradingId: '302', round: 2, message: '受付けました' })
        : undefined)

    await addGradingRound('essay-3', 2)

    expect(callsOf(fetchMock).at(-1)).toMatchObject({
      method: 'POST',
      url: '/api/user/english-essays/essay-3/gradings'
    })
    expect(jsonOf(callsOf(fetchMock).at(-1))).toEqual({ round: 2 })
  })

  it('添削の受付が 404 のときは、画面向けの日本語のエラーにする', async () => {
    stubFetch((url, method) =>
      method === 'POST' && url === '/api/user/english-essays/essay-3/gradings' ? notFound() : undefined)

    await expect(addGradingRound('essay-3')).rejects.toMatchObject({
      status: 404,
      message: 'この英作文は見つかりませんでした。削除された可能性があります。'
    })
  })

  it('画像のアップロードは multipart（file ＋ category ＋ order）', async () => {
    const fetchMock = stubFetch((url, method) =>
      method === 'POST' && url === '/api/user/english-essays/essay-3/images'
        ? ok({
            imageId: '21', order: 2, category: 'answer', originalFileName: 'answer.png',
            mimeType: 'image/png', fileSize: 3
          })
        : undefined)

    const uploaded = await uploadEssayImage('essay-3', { file: imageFile(), category: 'answer', order: 2 })

    const form = formOf(callsOf(fetchMock).at(-1))
    expect(form.get('category')).toBe('answer')
    expect(form.get('order')).toBe('2')
    expect((form.get('file') as File).name).toBe('answer.png')
    // 画面は API の URL で出す
    expect(uploaded.id).toBe('21')
    expect(uploaded.dataUrl).toBe('/api/user/english-essays/essay-3/images/21')
    expect(uploaded.category).toBe('answer')
  })

  it('文字認識は user-api へ画像の実体を送る（区分はファイルと同じ順）', async () => {
    const fetchMock = stubFetch((url, method) =>
      method === 'POST' && url === '/api/user/english-essays/ocr'
        ? ok({
            questionText: 'Question text',
            essayText: 'Essay text',
            pages: [{ category: 'question', text: 'Question text', confidence: 96 }],
            questionConfidence: 96,
            essayConfidence: 91
          })
        : undefined)

    const result = await recognizeEssay('GRADE1', [
      { file: imageFile('question.png'), category: 'question' },
      { file: imageFile('answer.png'), category: 'answer' }
    ])

    const form = formOf(callsOf(fetchMock).at(-1))
    expect(form.get('level')).toBe('GRADE1')
    expect(form.get('categories')).toBe('question,answer')
    expect((form.getAll('files') as File[]).map((file) => file.name)).toEqual(['question.png', 'answer.png'])
    expect(result).toEqual({
      questionText: 'Question text',
      essayText: 'Essay text',
      pages: [{ category: 'question', text: 'Question text', confidence: 96 }],
      questionConfidence: 96,
      essayConfidence: 91
    })
  })

  it('失敗した呼び出しは ApiError にして投げる（画面が知らせられる）', async () => {
    stubFetch((url, method) =>
      method === 'GET' && url.startsWith('/api/user/english-essays?')
        ? new Response(JSON.stringify({
            success: false, code: 'INTERNAL_ERROR', message: '一覧を取得できませんでした', data: null, timestamp: ''
          }), { status: 500, headers: { 'Content-Type': 'application/json' } })
        : undefined)

    await expect(loadEssays({}, 1, 5)).rejects.toMatchObject({
      code: 'INTERNAL_ERROR',
      message: '一覧を取得できませんでした'
    })
  })

  it('resetEssays は画面のキャッシュを空にする', async () => {
    stubFetch((url, method) =>
      method === 'GET' && url.startsWith('/api/user/english-essays?') ? ok(page([row()], { total: 1 })) : undefined)
    await loadEssays({}, 1, 5)
    expect(essayList.value).toHaveLength(1)

    clearEssayList()
    expect(essayList.value).toHaveLength(0)
    expect(essayTotal.value).toBe(0)
    expect(essayTotalPages.value).toBe(1)
  })
})

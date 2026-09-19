import { beforeEach, describe, expect, it, vi, afterEach } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, nextTick, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { readFileSync } from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { resolveMenu } from '@/config/menuRegistry'
import { routes } from '@/router'
import {
  DEFAULT_PRESETS,
  LANGUAGE_MODES,
  SUBJECTS,
  formatElapsed,
  formatSegmentTime,
  languageModeLabel,
  noteSections,
  presetName,
  segmentSpeaker
} from '@/features/classroom/classroom'
import ClassroomListView from '@/views/classroom/ClassroomListView.vue'
import ClassroomLiveView from '@/views/classroom/ClassroomLiveView.vue'
import ClassroomDetailView from '@/views/classroom/ClassroomDetailView.vue'
import ClassroomAiSettingsSection from '@/views/admin/system-settings/ClassroomAiSettingsSection.vue'

/**
 * 授業録音 / AI 授業記録（画面の接続）。
 *
 * 画面は `src/api/classroom.ts` の関数だけを通して user-api（`/api/user/classroom`）と
 * admin-api の薄い入口（`/api/admin/batch/classroom/notes/{id}/run`）を呼ぶ。ここでは
 * ・共通の表示用ヘルパー（言語モード・前置詞・時刻・AI ノートの段落）
 * ・メニュー項目とルート（3 エリア）の登録
 * ・4 画面が**実際に API を呼び**、成功／失敗で表示が変わること
 * ・設定セクションがサーバーの 7 キーだけを読み書きし、registerSection に載ること
 * を固定する（URL・メソッド・body は `classroom-api.spec.ts` が見張る）。
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/* ------------------------------------------------------------------ モック API */

type Call = {
  url: string
  method: string
  body: Record<string, unknown> | null
  /** multipart（分塊アップロード）のときの中身（`stt` パートの確認に使う）。 */
  form?: FormData
}

/** 記録 1 件（一覧の行）。 */
function row(recordId: number, overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    recordId,
    recordNo: `CR20260914-${recordId}`,
    title: `授業 ${recordId}`,
    languageMode: 'ja',
    status: 'COMPLETED',
    statusLabel: '完了',
    durationSeconds: 2700,
    transcribedChars: 120,
    hasAudio: true,
    createdAt: '2026-09-14T10:00:00',
    updatedAt: '2026-09-14T10:45:00',
    ...overrides
  }
}

/** 詳細 1 件（`GET /classroom/{id}`）。 */
function detail(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    recordId: 12,
    recordNo: 'CR20260914-0001',
    title: '数学 二次関数の復習',
    languageMode: 'ja',
    presetId: 1,
    presetName: '通常の授業',
    presetText: '一般的な授業。',
    status: 'COMPLETED',
    statusLabel: '完了',
    startTime: '2026-09-14T10:00:00',
    endTime: '2026-09-14T10:45:00',
    durationSeconds: 2700,
    transcribedChars: 24,
    summaryJson: JSON.stringify({
      テーマ: '比例のグラフ', 学習内容: '比例定数と直線の傾き', 先生の重点: '右上がりになること', 宿題: '練習問題 1〜5'
    }),
    hasAudio: true,
    audioMime: 'audio/webm',
    segments: [
      {
        segmentId: 1, seq: 1, startOffsetSeconds: 20, endOffsetSeconds: 40,
        speaker: null, text: 'それでは今日の授業を始めます。', language: 'ja', createdAt: null
      }
    ],
    notes: [
      {
        noteId: 91, kind: 'FINAL', phaseNo: null, startSeq: null, endSeq: null,
        status: 'READY', statusLabel: 'できました',
        noteJson: JSON.stringify({ テーマ: '比例のグラフ', 学習内容: '比例定数と直線の傾き' }),
        errorCode: null, errorMessage: null, createdAt: null, updatedAt: null
      }
    ],
    version: 2,
    createdAt: '2026-09-14T10:00:00',
    updatedAt: '2026-09-14T10:45:00',
    ...overrides
  }
}

interface ApiOptions {
  page?: Record<string, unknown>
  detail?: Record<string, unknown>
  /** 終了（`POST /classroom/{id}/end`）の応答。書き起こしが無いときは finalNoteId/runPath が null */
  end?: Record<string, unknown>
  /** 失敗させたい URL の一部（日本語の理由を返す） */
  failOn?: string
  failureMessage?: string
  /** 書き起こしを誰が行うか（options.sttMode）。既定はサーバー認識 */
  sttMode?: 'BROWSER' | 'SERVER'
  /** 授業の AI 解析（batC61 / batC62）を使うか。既定は有効 */
  noteEnabled?: boolean
  /**
   * 分塊（`/chunks`）の応答を保留する（テストが後から解放する）。
   * 【終了】が「最後の分塊の送信を待つ」ことを順番で確かめるために使う。
   */
  holdChunks?: Promise<void>
  /** ストリーミング書き起こし（話しながら文字が出る）を使えることにするか。 */
  streamStt?: boolean
  /** 保存済みの分塊（`GET /chunks`）の応答。既定は 0 件（新規の録音）。 */
  chunkList?: Record<string, unknown>
  /** ストリーミング書き起こしの**最初の N 回だけ失敗**させる（つなぎ直しの確認に使う）。 */
  sttStreamErrorTimes?: number
  /**
   * 書き起こしの收尾（`/stt/stream/finish`）の応答を保留する。
   * 【終了】が「尾部の最後の確定文」を待つことを順番で確かめるために使う。
   */
  holdSttFinish?: Promise<void>
  /**
   * `/stt/stream` が返す**確定した文**を作り直す（呼んだ回と音源を受け取る）。
   *
   * <p>使い道: 音源ごとに違う文（二音源の話者の確認）・同じ発話が伸びた文（訂正の確認）。</p>
   */
  sttAdded?: (call: { index: number; source: string }) => Record<string, unknown>[]
  /** 書き起こしの**最終結果**（話しながら出る文）に出す話者ラベル。既定は「講義」。 */
  sttSpeaker?: string
  /**
   * 収尾（`/stt/stream/finish`）が返す確定文と理由。
   * `error` に理由を入れると「収尾を完了できなかった」応答になる（画面は成功にしない）。
   */
  sttFinish?: { added?: Record<string, unknown>[]; error?: string | null }
  /** `/stt/stream/finish` を**最初の N 回だけ**失敗させる（やり直しの確認に使う）。 */
  sttFinishErrorTimes?: number
  /**
   * `/end`（記録の終了）が**最初の N 回**「完了できなかった」理由つきで返る。
   * 後端は尾部を取り切れなかったときに HTTP 200 で理由を返す（画面は成功にしない）。
   */
  endErrorTimes?: number
}

/**
 * `/api/...` に応える fetch のモック。
 * 呼び出し（URL・メソッド・body）を記録するので、画面が何を送ったかを確かめられる。
 */
function mockApi(options: ApiOptions = {}): { calls: Call[]; fetchMock: ReturnType<typeof vi.fn> } {
  const calls: Call[] = []
  /** `/stt/stream` を呼んだ回数（最初の N 回だけ失敗させるのに使う）。 */
  let sttStreamCalls = 0
  /** `/stt/stream/finish`（収尾）を呼んだ回数（最初の N 回だけ失敗させるのに使う）。 */
  let finishCalls = 0
  /** `/end`（記録の終了）を呼んだ回数（最初の N 回だけ理由つきで返すのに使う）。 */
  let endCalls = 0
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    const body = typeof init?.body === 'string'
      ? (JSON.parse(init.body) as Record<string, unknown>) : null
    const form = init?.body instanceof FormData ? init.body : undefined
    calls.push({ url: String(url), method, body, form })

    const ok = (data: unknown): Response => new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } }
    )
    // 分塊の応答を保留する（後から解放される。順番の確認用）
    if (options.holdChunks !== undefined && String(url).includes('/chunks') && method === 'POST') {
      await options.holdChunks
    }
    // 保存済みの分塊の一覧（GET）。画面はここから**続きの連番**を取る（POST の応答とは別物）
    if (String(url).includes('/chunks') && method === 'GET') {
      return ok(options.chunkList ?? {
        items: [], chunkCount: 0, maxSeq: 0, nextSeq: 1, totalBytes: 0, recordedSeconds: null
      })
    }
    if (options.failOn !== undefined && String(url).includes(options.failOn)) {
      return new Response(
        JSON.stringify({
          success: false, code: 'INTERNAL_ERROR',
          message: options.failureMessage ?? 'サーバーでエラーが発生しました。', data: null
        }),
        { status: 500, headers: { 'Content-Type': 'application/json' } }
      )
    }
    if (String(url).includes('/api/admin/batch/classroom/notes/')) {
      return ok({ noteId: 91, kind: 'FINAL', batchCode: 'batC62', status: 'READY' })
    }
    if (String(url).includes('/options')) {
      return ok({
        enabled: true, chunkSeconds: 20, maxRecordingMinutes: 120, retentionDays: 30,
        dailyLimit: 0, usedToday: 0, notice: '',
        sttMode: options.sttMode ?? 'SERVER',
        sttLanguageCodes: { ja: 'ja-JP', zh: 'zh-CN', en: 'en-US', 'ja-en': 'ja-JP', 'zh-en': 'zh-CN' },
        noteEnabled: options.noteEnabled ?? true,
        streamStt: options.streamStt ?? false
      })
    }
    if (String(url).includes('/stt/stream/finish')) {
      // 尾部の確定文が返るまで保留する（順番の確認用）
      if (options.holdSttFinish !== undefined) await options.holdSttFinish
      finishCalls += 1
      if (finishCalls <= (options.sttFinishErrorTimes ?? 0)) {
        // 後端が「収尾を完了できなかった」と返した場合（HTTP は 200）
        return ok({ interim: '', added: [], error: '尾部の文を取り切れませんでした。' })
      }
      // セッションの終了（つなぎ直しの回数には数えない）
      return ok({
        interim: '', added: options.sttFinish?.added ?? [], error: options.sttFinish?.error ?? null
      })
    }
    if (String(url).includes('/stt/stream')) {
      sttStreamCalls += 1
      if (sttStreamCalls <= (options.sttStreamErrorTimes ?? 0)) {
        // 認識側の失敗（つなぎ直しの確認用）
        return ok({ interim: '', added: [], error: '認識サービスに接続できませんでした。' })
      }
      const source = new URLSearchParams(String(url).split('?')[1] ?? '').get('source') ?? 'mic'
      // ストリーミング書き起こし: 途中の文と、確定した文を返す
      return ok({
        interim: 'ううう',
        added: options.sttAdded?.({ index: sttStreamCalls, source }) ?? [{
          segmentId: 601, seq: 2, startOffsetSeconds: 0, endOffsetSeconds: 1.2,
          speaker: options.sttSpeaker ?? '講義', text: '比例のグラフを学びます。',
          language: 'ja-JP', createdAt: null
        }],
        error: null
      })
    }
    if (String(url).includes('/transcripts')) {
      return ok({
        recordId: 12, seq: 1, nextSeq: 2,
        appendedSegments: [{
          segmentId: 501, seq: 1, startOffsetSeconds: 0, endOffsetSeconds: 3,
          speaker: '講義', text: String(body?.text ?? ''), language: 'ja-JP', createdAt: '2026-09-14T10:00:03'
        }],
        pendingNoteId: null, triggered: false, status: 'RECORDING', runPath: null
      })
    }
    if (String(url).includes('/presets')) {
      return ok([
        { presetId: 1, scope: 'GLOBAL', name: '通常の授業', text: '一般的な授業。テーマ・学習内容・先生の重点・宿題を順に整理します。', displayOrder: 1 },
        { presetId: 2, scope: 'GLOBAL', name: '数学の授業', text: '数学の授業。公式や解法の手順・例題を重点的に整理します。', displayOrder: 2 },
        { presetId: 3, scope: 'GLOBAL', name: '英語の授業', text: '英語の授業。新出単語・会話表現・発音を整理します。', displayOrder: 3 }
      ])
    }
    if (String(url).includes('/segments')) {
      return ok({ items: [], nextSeq: 0 })
    }
    if (String(url).includes('/chunks')) {
      return ok({
        recordId: 12, seq: 1, nextSeq: 2, appendedSegments: [], pendingNoteId: null,
        triggered: false, status: 'RECORDING', runPath: null
      })
    }
    if (String(url).includes('/start')) {
      return ok({ recordId: 12, recordNo: 'CR20260914-0001', status: 'RECORDING', statusLabel: '録音中', version: 2 })
    }
    if (String(url).includes('/end')) {
      endCalls += 1
      const ended = options.end ?? {
        recordId: 12, status: 'STOPPED', statusLabel: '停止（まとめ作成待ち）',
        finalNoteId: 91, runPath: '/api/admin/batch/classroom/notes/91/run', notice: null
      }
      // 後端が「終了を完了できなかった」と返す場合（HTTP は 200）
      if (endCalls <= (options.endErrorTimes ?? 0)) {
        return ok({ ...ended, error: '尾部の文を取り切れませんでした。' })
      }
      return ok(ended)
    }
    if (method === 'POST' && /\/api\/user\/classroom$/.test(String(url))) {
      return ok({ recordId: 12, recordNo: 'CR20260914-0001', status: 'RECORDING', statusLabel: '録音中', version: 1 })
    }
    if (method === 'GET' && /\/api\/user\/classroom\?/.test(String(url))) {
      return ok(options.page ?? { items: [], totalElements: 0, page: 1, size: 100, totalPages: 0 })
    }
    if (method === 'DELETE') {
      return ok({ recordId: 12, deletedAudio: true })
    }
    if (/\/api\/user\/classroom\/\d+$/.test(String(url))) {
      return ok(options.detail ?? detail())
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls, fetchMock }
}

/** 授業録音の 4 画面が使う最小ルーター（useRoute / useRouter のため）。 */
function classroomRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/classroom', component: Dummy },
      { path: '/:area/classroom/:id/live', component: Dummy },
      { path: '/:area/classroom/:id', component: Dummy }
    ]
  })
}

async function mountClassroom(
  view: Component,
  path: string
): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = classroomRouter()
  await router.push(path)
  await router.isReady()
  const wrapper = mount(view, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

/** 「API に送った 1 件」を探す。 */
function callTo(calls: Call[], part: string, method?: string): Call | undefined {
  return calls.find((call) => call.url.includes(part) && (method === undefined || call.method === method))
}

/**
 * 常時接続（WebSocket）が**使えない**環境にする。
 *
 * <p>録音中の画面はまず常時接続を試し、開けなければ HTTP の送信へ退避する。jsdom の
 * `WebSocket` は実際の接続を試みるが、その結果はテストのあいだに返らない（＝どちらの経路も
 * 進まない）。ここでは「開けない」ことをはっきりさせて、**退避したあとの HTTP 送信**を
 * 確かめられるようにする（常時接続そのものは `classroom-source-stream.spec.ts` が見張る）。</p>
 */
function stubNoSocket(): void {
  vi.stubGlobal('WebSocket', undefined)
}

/**
 * Web Speech API（`SpeechRecognition`）の代役。
 * 画面が `lang` / `continuous` をどう設定し、final をいつ送るかを test から確かめる。
 */
function installFakeSpeechRecognition(): {
  instances: unknown[]
  last: { lang: string; continuous: boolean; interimResults: boolean; startCount: number }
  emitResult: (text: string, isFinal: boolean) => void
  finish: () => void
} {
  const instances: unknown[] = []
  const state = { lang: '', continuous: false, interimResults: false, startCount: 0 }
  let onResult: ((event: unknown) => void) | null = null
  let onEnd: (() => void) | null = null
  class FakeRecognition {
    lang = ''
    continuous = false
    interimResults = false
    maxAlternatives = 1
    onerror: ((event: unknown) => void) | null = null
    set onresult(handler: ((event: unknown) => void) | null) { onResult = handler }
    get onresult(): ((event: unknown) => void) | null { return onResult }
    set onend(handler: (() => void) | null) { onEnd = handler }
    get onend(): (() => void) | null { return onEnd }
    start(): void {
      state.startCount += 1
      state.lang = this.lang
      state.continuous = this.continuous
      state.interimResults = this.interimResults
      instances.push(this)
    }
    stop(): void { /* 何もしない */ }
    abort(): void { /* 何もしない */ }
  }
  ;(window as unknown as { SpeechRecognition?: unknown }).SpeechRecognition = FakeRecognition
  delete (window as unknown as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition
  return {
    instances,
    last: state,
    emitResult: (text, isFinal) => {
      onResult?.({
        resultIndex: 0,
        results: { length: 1, 0: { isFinal, 0: { transcript: text }, length: 1 } }
      })
    },
    finish: () => { onEnd?.() }
  }
}

afterEach(() => {
  vi.useRealTimers()
  vi.unstubAllGlobals()
  // 画面が覚えている設定（音源の選択など）を次のテストへ持ち越さない
  localStorage.clear()
  delete (window as { SpeechRecognition?: unknown }).SpeechRecognition
  delete (window as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition
})

describe('授業録音：共通の表示用ヘルパー', () => {
  it('言語モードは 5 択（自動判別は廃止）で、ラベルを引ける', () => {
    expect(LANGUAGE_MODES.map((option) => option.label)).toEqual([
      '中国語', '日本語', '英語', '中国語＋英語', '日本語＋英語'
    ])
    expect(LANGUAGE_MODES.map((option) => option.value)).toEqual(['zh', 'ja', 'en', 'zh-en', 'ja-en'])
    expect(languageModeLabel('ja')).toBe('日本語')
    expect(languageModeLabel('zh-en')).toBe('中国語＋英語')
    expect(languageModeLabel(undefined)).toBe('（未指定）')
    // 旧データに残っている auto（廃止）でも落ちない（サーバーは ja-JP として扱う）
    expect(languageModeLabel('auto')).toBe('自動判別（廃止）')
    // 知らない値はそのまま出す（落ちない）
    expect(languageModeLabel('xx')).toBe('xx')
  })

  it('科目と前置詞（プリセット）の初期値を定義している', () => {
    expect([...SUBJECTS]).toEqual(['国語', '数学', '理科', '社会', '英語', 'その他'])
    expect(DEFAULT_PRESETS.length).toBe(3)
    expect(presetName('standard')).toBe(DEFAULT_PRESETS[0]?.name)
    expect(presetName(undefined)).toBe('（未指定）')
  })

  it('経過時間を HH:MM:SS、転写の時刻を MM:SS にする', () => {
    expect(formatElapsed(0)).toBe('00:00:00')
    expect(formatElapsed(3)).toBe('00:00:03')
    expect(formatElapsed(75)).toBe('00:01:15')
    expect(formatElapsed(3723)).toBe('01:02:03')
    expect(formatSegmentTime(20)).toBe('00:20')
    expect(formatSegmentTime(615)).toBe('10:15')
    expect(formatSegmentTime(null)).toBe('--:--')
  })

  it('話者が空なら「講義」1 つに丸める（MVP は話者を分けない）', () => {
    expect(segmentSpeaker(null)).toBe('講義')
    expect(segmentSpeaker('  ')).toBe('講義')
    expect(segmentSpeaker('先生')).toBe('先生')
  })

  it('AI の応答（JSON）から 4 段落を取り出す（読めないときは空）', () => {
    const sections = noteSections('{"テーマ":"比例のグラフ","学習内容":"傾き","先生の重点":"右上がり","宿題":"練習問題"}')
    expect(sections.map((section) => section.key)).toEqual(['theme', 'content', 'focus', 'homework'])
    expect(sections.map((section) => section.title)).toEqual(['本時のテーマ', '学習内容', '先生の重点', '宿題'])
    expect(sections[0]?.body).toBe('比例のグラフ')
    expect(sections[3]?.body).toBe('練習問題')

    // 英語のキーでも読める（プロンプトの揺れを吸収する）
    expect(noteSections('{"theme":"T","homework":["a","b"]}')[0]?.body).toBe('T')
    expect(noteSections('{"theme":"T","homework":["a","b"]}')[3]?.body).toBe('a / b')
    // 壊れていても落ちない
    expect(noteSections('not json').every((section) => section.body === '')).toBe(true)
    expect(noteSections(null).every((section) => section.body === '')).toBe(true)
  })
})

describe('授業録音：メニューとルート', () => {
  it('3 エリアのメニューに「授業録音」を出す（パスは /<area>/classroom）', () => {
    for (const area of ['admin', 'student', 'parent'] as const) {
      const menu = resolveMenu(area, undefined)
      const item = menu.find((entry) => entry.id === 'classroom')
      expect(item, `${area} に classroom メニューが無い`).toBeDefined()
      expect(item?.label).toBe('授業録音')
      expect(item?.icon).toBe('mic')
      expect(item?.path).toBe(`/${area}/classroom`)
      expect(item?.children).toBeUndefined()
    }
  })

  it('3 エリアに 3 つのルートを登録している（新しい授業は dialog なので専用ルートは無い）', () => {
    const names = routes
      .flatMap((r) => [r.name, ...(r.children ?? []).map((c) => c.name)])
      .filter((n): n is string => typeof n === 'string')

    for (const area of ['admin', 'student', 'parent']) {
      expect(names).toContain(`${area}-classroom`)
      expect(names).toContain(`${area}-classroom-live`)
      expect(names).toContain(`${area}-classroom-detail`)
      expect(names).not.toContain(`${area}-classroom-new`)
    }
  })
})

describe('授業録音：授業一覧', () => {
  it('見出しと【＋ 新しい授業】を出し、履歴は空の案内（ダミーを置かない）', async () => {
    mockApi()
    const { wrapper } = await mountClassroom(ClassroomListView, '/student/classroom')

    expect(wrapper.get('.card__title').text()).toContain('授業録音')
    const create = wrapper.get('[data-cr-create]')
    expect(create.text()).toContain('新しい授業')
    // 履歴のダミーは無い（サーバーが 0 件のときは案内だけ）
    expect(wrapper.find('[data-cr-history-item]').exists()).toBe(false)
    expect(wrapper.get('[data-cr-empty]').text()).toContain('授業の履歴はまだありません')
  })

  it('サーバーの記録をカードで並べ、選ぶと詳細へ進む', async () => {
    const { calls } = mockApi({
      page: { items: [row(12, { title: '数学 二次関数' }), row(13, { title: '英語 長文' })], totalElements: 2, page: 1, size: 100, totalPages: 1 }
    })
    const { wrapper, router } = await mountClassroom(ClassroomListView, '/student/classroom')

    // 一覧は API から読む（1 ページの上限まで。並びはサーバーの既定）
    expect(callTo(calls, '/api/user/classroom?', 'GET')?.url).toContain('page=1')
    expect(callTo(calls, '/api/user/classroom?', 'GET')?.url).toContain('size=100')

    const items = wrapper.findAll('[data-cr-history-item]')
    expect(items).toHaveLength(2)
    expect(items[0]?.text()).toContain('数学 二次関数')
    expect(items[1]?.text()).toContain('英語 長文')
    expect(wrapper.find('[data-cr-empty]').exists()).toBe(false)

    await wrapper.get('[data-cr-open="13"]').trigger('click')
    await flushPromises()
    expect(router.currentRoute.value.path).toBe('/student/classroom/13')
  })

  it('一覧を取れないときは理由を日本語で出し、空の案内は出さない', async () => {
    mockApi({ failOn: '/api/user/classroom?', failureMessage: '授業の一覧を取得できませんでした。' })
    const { wrapper } = await mountClassroom(ClassroomListView, '/student/classroom')

    expect(wrapper.get('[data-cr-list-error]').text()).toContain('授業の一覧を取得できませんでした。')
    expect(wrapper.find('[data-cr-empty]').exists()).toBe(false)
    // 失敗しても【新しい授業】は押せる
    expect(wrapper.find('[data-cr-create]').exists()).toBe(true)
  })

  /**
   * 【＋ 新しい授業】は**ページ遷移ではなく dialog**（利用者の指示）。
   * 授業名は任意・科目と言語モードを選び、始め方を 3 つから選ぶ。
   */
  it('【＋ 新しい授業】で dialog が開く（ページ遷移しない）', async () => {
    mockApi()
    const { wrapper, router } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await flushPromises()

    // 画面は一覧のまま、dialog が出る
    expect(router.currentRoute.value.path).toBe('/student/classroom')
    const dialog = wrapper.get('[data-cr-new-dialog]')
    expect(dialog.text()).toContain('新しい授業')
    // 授業名は任意であることが分かる（未入力なら「授業名未設定」になる）
    expect(dialog.text()).toContain('授業名（任意）')
    expect(dialog.get('[data-cr-new-title]').attributes('placeholder')).toContain('授業名未設定')
    // 始め方は 3 つ
    expect(dialog.find('[data-cr-new-mode-record]').exists()).toBe(true)
    expect(dialog.find('[data-cr-new-mode-upload]').exists()).toBe(true)
    expect(dialog.find('[data-cr-new-mode-paste]').exists()).toBe(true)
    // AI まとめがオフであることも書く
    expect(dialog.text()).toContain('AI まとめ')
  })

  it('dialog の「録音する」で、作成→開始→録音画面（自動で録音）へ進む', async () => {
    const { calls } = mockApi()
    const { wrapper, router } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await wrapper.get('[data-cr-new-title]').setValue('数学の授業')
    await wrapper.get('[data-cr-new-subject]').setValue('数学')
    await wrapper.get('[data-cr-new-submit]').trigger('click')
    await flushPromises()

    // 授業名と科目を送る
    const created = calls.find((call) => call.url === '/api/user/classroom' && call.method === 'POST')
    expect(created?.body?.title).toBe('数学の授業')
    expect(created?.body?.subject).toBe('数学')
    // 開始して、録音画面へ（自動で録音を始める印つき）
    expect(calls.some((call) => call.url.includes('/start'))).toBe(true)
    expect(router.currentRoute.value.path).toContain('/live')
    expect(router.currentRoute.value.query.autostart).toBe('1')
  })

  /**
   * 前置詞（シナリオプリセット）は AI ノートの整理方針を決めるので、授業を作る前に選ぶ。
   * 選択肢は**サーバーのプリセット**（GLOBAL ＋ 自分のスコープ）から読み、説明も出す。
   */
  it('dialog に前置詞（サーバーのプリセット＋説明）を出す', async () => {
    const { calls } = mockApi()
    const { wrapper } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await flushPromises()

    expect(callTo(calls, '/api/user/classroom/presets', 'GET')).toBeDefined()
    expect(wrapper.findAll('[data-cr-new-preset] option').map((option) => option.text()))
      .toEqual(['通常の授業', '数学の授業', '英語の授業'])
    await wrapper.get('[data-cr-new-preset]').setValue('2')
    expect(wrapper.get('[data-cr-new-preset-desc]').text()).toContain('数学の授業')
    expect(wrapper.get('[data-cr-new-preset-desc]').text()).toContain('公式や解法')
  })

  it('dialog: 前置詞を取れないときは画面の初期値を使い、理由を出す', async () => {
    mockApi({ failOn: '/presets', failureMessage: '前置詞の一覧を取得できませんでした。' })
    const { wrapper } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-cr-new-preset-notice]').text()).toContain('前置詞の一覧を取得できませんでした。')
    expect(wrapper.findAll('[data-cr-new-preset] option').map((option) => option.text()))
      .toEqual(DEFAULT_PRESETS.map((preset) => preset.name))
  })

  /**
   * 録音する音源は【新しい授業】で選ぶ（録音中の画面からは選べない＝利用者の指示）。
   * 選んだ音源は覚えておき、次の授業で選び直さなくてよいようにする。
   */
  it('dialog: 録音するときだけ音源（2 択・覚えている選択）を出す', async () => {
    // 統合前に覚えた値（mic-tab）でも「マイク＋スピーカー」として復元する
    localStorage.setItem('study21.classroom.audioMode', 'mic-tab')
    mockApi()
    const { wrapper } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await flushPromises()

    const select = wrapper.get('[data-cr-new-audio-mode]')
    // タブの音と画面共有の音は操作が同じなので 1 つに統合した（利用者の指示）
    expect(select.findAll('option').map((option) => option.attributes('value')))
      .toEqual(['mic', 'mic-pc'])
    expect(select.findAll('option').map((option) => option.text()))
      .toEqual(['マイクのみ（対面の授業）', 'マイク＋スピーカー（オンライン授業）'])
    // 前回選んだ音源（統合前の「マイク＋タブの音」）も「マイク＋スピーカー」として選ばれる
    expect((select.element as HTMLSelectElement).value).toBe('mic-pc')
    // 二音源のときは、共有の選択で「タブの音声を共有」にチェックする案内を出す
    expect(wrapper.get('[data-cr-new-audio-guide]').text()).toContain('タブの音声')
    // 共有しても音は消えないこと（実測で困った点）と、ヘッドホンの案内も出す
    expect(wrapper.get('[data-cr-new-audio-guide]').text()).toContain('音は消えません')
    expect(wrapper.get('[data-cr-new-audio-guide]').text()).toContain('ヘッドホン')

    // 録音以外（音声ファイル・貼り付け）では音源を選ばない
    await wrapper.get('[data-cr-new-mode-upload]').setValue(true)
    expect(wrapper.find('[data-cr-new-audio-mode]').exists()).toBe(false)
    await wrapper.get('[data-cr-new-mode-record]').setValue(true)
    expect(wrapper.find('[data-cr-new-audio-mode]').exists()).toBe(true)
  })

  /**
   * ブラウザ認識で二音源を録ると、スピーカーの音は文字にならない。
   * 授業を始める**前に**気づけるように、選んだ時点で案内する（黙って始めさせない）。
   */
  it('dialog: 二音源＋ブラウザ認識のときは、スピーカーの音が文字にならないと知らせる', async () => {
    mockApi({ sttMode: 'BROWSER' })
    const { wrapper } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await flushPromises()
    // マイクのみのときは出さない
    expect(wrapper.find('[data-cr-new-stt-notice]').exists()).toBe(false)

    await wrapper.get('[data-cr-new-audio-mode]').setValue('mic-pc')
    await flushPromises()
    const notice = wrapper.get('[data-cr-new-stt-notice]').text()
    expect(notice).toContain('マイクの音しか書き起こせません')
    expect(notice).toContain('サーバー認識')

    // 録音以外（音声ファイル）では音源を選ばないので、この案内も出さない
    await wrapper.get('[data-cr-new-mode-upload]').setValue(true)
    expect(wrapper.find('[data-cr-new-stt-notice]').exists()).toBe(false)
  })

  it('dialog: サーバー認識のときは、その案内を出さない', async () => {
    mockApi({ sttMode: 'SERVER' })
    const { wrapper } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-cr-new-audio-mode]').setValue('mic-pc')
    await flushPromises()

    expect(wrapper.find('[data-cr-new-stt-notice]').exists()).toBe(false)
  })

  it('dialog の「録音する」: 前置詞と音源を送り、録音画面へ渡して覚える', async () => {
    const { calls } = mockApi()
    const { wrapper, router } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-cr-new-title]').setValue('オンライン授業')
    await wrapper.get('[data-cr-new-preset]').setValue('2')
    await wrapper.get('[data-cr-new-audio-mode]').setValue('mic-pc')
    await wrapper.get('[data-cr-new-submit]').trigger('click')
    await flushPromises()

    // 前置詞は数値の ID で送る（サーバーが選択時のテキストを記録に残す）
    const created = calls.find((call) => call.url === '/api/user/classroom' && call.method === 'POST')
    expect(created?.body?.presetId).toBe(2)
    // 録音画面は、選んだ音源で自動的に録音を始める
    expect(router.currentRoute.value.query.audioMode).toBe('mic-pc')
    expect(router.currentRoute.value.query.preset).toBe('数学の授業')
    // 次に開いたときは同じ音源が選ばれている
    expect(localStorage.getItem('study21.classroom.audioMode')).toBe('mic-pc')
  })

  it('dialog の「文字起こしを貼り付ける」で、取り込んでから詳細へ進む', async () => {
    const { fetchMock } = mockApi()
    vi.mocked(fetch).mockImplementation(async (url: RequestInfo | URL) => {
      const target = String(url)
      const ok = (data: unknown): Response => new Response(
        JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
      if (target.includes('/source')) {
        return ok({ recordId: 12, seq: 2, nextSeq: 3, appendedSegments: [], pendingNoteId: null, triggered: false, status: 'RECORDING', runPath: null })
      }
      if (target.includes('/end')) {
        return ok({ recordId: 12, status: 'STOPPED', statusLabel: '停止（まとめ作成待ち）', finalNoteId: null, runPath: null, notice: 'AI 解析は現在オフです。' })
      }
      if (target === '/api/user/classroom' ) {
        return ok({ recordId: 12, recordNo: 'CR1', status: 'RECORDING', statusLabel: '録音中', version: 1 })
      }
      if (target.includes('/classroom?')) return ok({ items: [], count: 0, page: 1, size: 100 })
      if (target.includes('/api/user/classroom/12')) {
        return ok({ record: { recordId: 12 }, segments: [], notes: [], summaryJson: null })
      }
      return ok({ items: [], count: 0 })
    })
    void fetchMock

    const { wrapper, router } = await mountClassroom(ClassroomListView, '/student/classroom')
    await wrapper.get('[data-cr-create]').trigger('click')
    await wrapper.get('[data-cr-new-mode-paste]').setValue(true)
    await wrapper.get('[data-cr-new-text]').setValue('先生：今日は比例を学びます。\n次に練習問題です。')
    await wrapper.get('[data-cr-new-submit]').trigger('click')
    await flushPromises()

    const sent = callsWithBody(vi.mocked(fetch))
    const source = sent.find((call) => call.url.includes('/source'))
    // 貼り付けた文字をそのまま送る（サーバーが行ごとに分けて保存する）
    expect(source?.form?.get('text')).toContain('比例')
    expect(sent.some((call) => call.url.includes('/end'))).toBe(true)
    expect(router.currentRoute.value.path).toBe('/student/classroom/12')
  })

  /**
   * 名前と種類だけ mp3 のファイルを**サーバーへ送らない**。
   *
   * <p>文字のテキストを `.mp3` に変えたファイルは、送ってしまうと書き起こしも再生もできない
   * 音声として残る（実測で起きた）。長さが読めない＝音声ではないので、ここで断る。</p>
   */
  it('dialog の「音声ファイル」: 長さが読めないファイルは送らずに理由を出す', async () => {
    stubAudioDuration(null)
    const { calls } = mockApi()
    const { wrapper, router } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await wrapper.get('[data-cr-new-mode-upload]').setValue(true)
    await selectFile(wrapper, 'not-audio.mp3')
    await wrapper.get('[data-cr-new-submit]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-cr-new-error]').text()).toContain('読み取れませんでした')
    // 記録も作らない・取り込みもしない
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
    expect(router.currentRoute.value.path).toBe('/student/classroom')
  })

  it('dialog の「音声ファイル」: 90 分を超える音声は送らずに理由を出す', async () => {
    stubAudioDuration(91 * 60)
    const { calls } = mockApi()
    const { wrapper } = await mountClassroom(ClassroomListView, '/student/classroom')

    await wrapper.get('[data-cr-create]').trigger('click')
    await wrapper.get('[data-cr-new-mode-upload]').setValue(true)
    await selectFile(wrapper, 'long.mp3')
    await wrapper.get('[data-cr-new-submit]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-cr-new-error]').text()).toContain('90 分までです')
    expect(calls.some((call) => call.method === 'POST')).toBe(false)
  })
})

/** `durationOf` が使う `<audio>` の代役（jsdom は音声を読まないので即座に返す）。 */
function stubAudioDuration(seconds: number | null): void {
  class FakeAudio {
    duration = seconds === null ? Number.NaN : seconds
    preload = ''
    onloadedmetadata: (() => void) | null = null
    onerror: (() => void) | null = null
    set src(_value: string) {
      queueMicrotask(() => {
        if (seconds === null) this.onerror?.()
        else this.onloadedmetadata?.()
      })
    }
  }
  vi.stubGlobal('Audio', FakeAudio)
  Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:x') })
  Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
}

/** dialog のファイル選択にファイルを入れる（jsdom は `files` を設定できないので直接差し替える）。 */
async function selectFile(wrapper: VueWrapper, name: string): Promise<void> {
  const input = wrapper.get('[data-cr-new-file]')
  const file = new File([new Uint8Array([1, 2, 3])], name, { type: 'audio/mpeg' })
  Object.defineProperty(input.element, 'files', { configurable: true, value: [file] })
  await input.trigger('change')
}

/** multipart の送信内容（FormData）を取り出す（テスト用）。 */
function callsWithBody(fetchMock: ReturnType<typeof vi.fn>): { url: string; form: FormData | null }[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return { url: String(call[0]), form: init.body instanceof FormData ? init.body : null }
  })
}

describe('授業録音：録音中', () => {
  const LIVE = '/student/classroom/12/live?name=数学%20二次関数&subject=数学&languageMode=zh-en&preset=2'

  /**
   * 【新しい授業】の dialog から来たときの URL（選んだ音源つき・自動で録音を始める）。
   * 音源は dialog で選ぶので、録音中の画面は query の値だけを見る。
   */
  function liveFromNewLesson(audioMode: string): string {
    return `${LIVE}&audioMode=${audioMode}&autostart=1`
  }

  it('見出し・言語モード・前置詞と二段組（転写・AI ノート）を出す', async () => {
    // まだ書き起こしもノートも無い状態＝画面の案内（表示例）を出す
    mockApi({ detail: detail({ segments: [], notes: [], summaryJson: null }) })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    expect(wrapper.get('.card__title').text()).toContain('数学 二次関数の復習')
    expect(wrapper.get('[data-cr-live-language-mode]').text()).toBe('日本語')
    expect(wrapper.get('[data-cr-live-preset]').text()).toBe('通常の授業')

    /*
     * 転写が画面の主役（利用者の指摘 ④-4）。旧い説明（MVP・two_speaker・話者を分けない案内）は
     * **出さない**（過時な開発の説明を利用者に見せない）。無内容のときは短い操作の案内だけ。
     */
    const transcript = wrapper.get('[data-cr-transcript]')
    expect(transcript.text()).toContain('書き起こし')
    expect(transcript.text()).toContain('録音を開始すると')
    expect(transcript.text()).not.toContain('two_speaker')
    expect(transcript.text()).not.toContain('MVP')

    /*
     * AI 解析が無効でノートも無いときは、**空の枠を出さない**（短い案内だけ。利用者の指摘 ④-4）。
     */
    expect(wrapper.find('[data-cr-note]').exists()).toBe(false)
    expect(wrapper.get('[data-cr-ai-notes-off]').text()).toContain('書き起こしだけ')

    // 技術情報は【詳細情報】の中だけ（既定は畳まれている）
    const details = wrapper.get('[data-cr-details]')
    expect(details.attributes('hidden')).toBeDefined()
    expect(details.text()).toContain('20 秒')
  })

  it('録音を開始すると API を呼び、経過時間が動き、停止できる', async () => {
    vi.useFakeTimers()
    const { calls } = mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    // 分塊の長さは設定（options）から出す
    expect(wrapper.get('[data-cr-live-chunk]').text()).toBe('20 秒')
    expect(wrapper.get('[data-cr-timer]').text()).toBe('00:00:00')
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('停止中')

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()
    expect(callTo(calls, '/api/user/classroom/12/start', 'POST')).toBeDefined()

    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('録音中')
    expect(wrapper.find('[data-cr-start-recording]').exists()).toBe(false)
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)

    vi.advanceTimersByTime(3000)
    await nextTick()
    expect(wrapper.get('[data-cr-timer]').text()).toBe('00:00:03')

    await wrapper.get('[data-cr-stop-recording]').trigger('click')
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('停止中')
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(false)
  })

  /**
   * ブラウザ音声認識（Web Speech API。API Key 不要）のときは、録音と同時に画面側で認識を始め、
   * final になった発話を `POST /classroom/{id}/transcripts` で送る（サーバーは STT を呼ばない）。
   */
  it('ブラウザ認識のときは画面側で認識し、確定した発話をサーバーへ送る', async () => {
    vi.useFakeTimers()
    const recognition = installFakeSpeechRecognition()
    // 記録の言語モードは zh-en（＝主言語 zh-CN で認識する）
    const { calls } = mockApi({
      sttMode: 'BROWSER',
      // 記録の言語モードは zh-en（＝主言語 zh-CN で認識する）。既存の書き起こしは無し
      detail: detail({ languageMode: 'zh-en', segments: [] })
    })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    // 誰が書き起こすかを画面に出す
    expect(wrapper.get('[data-cr-live-stt-mode]').text()).toContain('ブラウザ認識')
    expect(wrapper.get('[data-cr-live-stt-mode]').text()).toContain('キー不要')

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    // 言語モード（zh-en）は主言語（zh-CN）で認識する
    expect(recognition.last.lang).toBe('zh-CN')
    expect(recognition.last.continuous).toBe(true)
    expect(recognition.last.interimResults).toBe(true)

    // 途中経過は画面にだけ出す（保存しない）
    recognition.emitResult('三角形の', false)
    await nextTick()
    expect(wrapper.get('[data-cr-transcript-interim]').text()).toContain('三角形の')

    // final はサーバーへ送る（連番はサーバーが振る）
    recognition.emitResult('三角形の面積を求めます。', true)
    await flushPromises()
    const sent = callTo(calls, '/api/user/classroom/12/transcripts', 'POST')
    expect(sent?.body?.text).toBe('三角形の面積を求めます。')
    expect(sent?.body?.offsetSeconds).toBe(0)
    // 書き起こし欄に追記される
    expect(wrapper.get('[data-cr-transcript]').text()).toContain('三角形の面積を求めます。')
  })

  it('認識が終わっても録音中は聞き直す（continuous でも止まるため）', async () => {
    vi.useFakeTimers()
    const recognition = installFakeSpeechRecognition()
    mockApi({ sttMode: 'BROWSER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()
    expect(recognition.last.startCount).toBe(1)

    recognition.finish()
    vi.advanceTimersByTime(400)
    await flushPromises()
    expect(recognition.last.startCount).toBe(2)
  })

  it('ブラウザ認識に対応していないブラウザでは案内を出し、録音は続ける', async () => {
    delete (window as { SpeechRecognition?: unknown }).SpeechRecognition
    delete (window as { webkitSpeechRecognition?: unknown }).webkitSpeechRecognition
    mockApi({ sttMode: 'BROWSER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-cr-browser-stt-notice]').text()).toContain('Chrome / Edge')
    // 録音（音声の保存）は続く
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('録音中')
  })

  /**
   * 利用者の指示（3-4）: 画面は**実際の発言開始時刻**で並べ、遅れて届いた結果も正しい位置へ入れる。
   * 同時刻は連番で安定させ、同時発言（時間の重なり）はそのまま出す。
   */
  it('転写は発言の開始時刻で並べ、遅れて届いた文も正しい位置に入る', async () => {
    const segment = (seq: number, start: number, end: number, text: string, speaker: string): Record<string, unknown> => ({
      segmentId: seq, seq, startOffsetSeconds: start, endOffsetSeconds: end,
      speaker, text, language: 'ja-JP', createdAt: null
    })
    // 到着順は 3 → 1 → 2（先生の文が遅れて届く想定。学生と先生が同時に話している）
    mockApi({
      detail: detail({
        segments: [
          segment(3, 1.0, 2.0, 'はい、どうぞ。', '先生'),
          segment(1, 0.2, 1.0, '先生、質問です。', '学生'),
          segment(2, 1.0, 1.8, '（同時に）はい。', '学生')
        ], notes: []
      })
    })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await flushPromises()

    const rows = wrapper.findAll('[data-cr-transcript-line]')
    // 開始時刻の順（0.2 → 1.0 → 1.0）。同時刻は連番で安定（seq 2 → 3）
    expect(rows.map((row) => row.attributes('data-cr-transcript-line'))).toEqual(['1', '2', '3'])
    // 話者のラベルと色分けが付く（音源で決まる）
    expect(rows[0]?.attributes('data-cr-transcript-speaker')).toBe('学生')
    expect(rows[2]?.attributes('data-cr-transcript-speaker')).toBe('先生')
    expect(rows[2]?.classes()).toContain('cr-transcript__line--teacher')
    // 時間の重なりは消さない（同時発言を落とさない）
    expect(rows[1]?.text()).toContain('（同時に）はい。')
  })

  /**
   * 利用者の指示 2: 話者は**音源**で決まる（モデルが人物を判定するのではない）。共有の音に
   * 混ざる他の発話も「先生」扱いになることと、イヤホンの案内を画面にも書く。
   */
  it('二音源のときは、話者の決め方とイヤホンの案内を出す', async () => {
    stubDualSource(sharedStream(true))
    mockApi({ sttMode: 'SERVER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    const note = wrapper.get('[data-cr-speaker-note]').text()
    expect(note).toContain('「先生」')
    expect(note).toContain('「学生」')
    expect(note).toContain('他の人の声も「先生」扱い')
    expect(note).toContain('イヤホン')

    // マイクのみ（対面）のときは出さない（二音源でないので分けない）
    mockApi({ sttMode: 'SERVER' })
    const solo = await mountClassroom(ClassroomLiveView, LIVE)
    expect(solo.wrapper.find('[data-cr-speaker-note]').exists()).toBe(false)
  })

  /**
   * 利用者の指示 3-7: **古い記録**（発話キー・音源が無い時代の行、話者ラベルが空の行）でも
   * 画面が壊れない（従来どおり「講義」として出し、色分けも付く）。
   */
  /**
   * ストリーミング書き起こし（音源ごとの PCM）を確かめるための代役をそろえる。
   *
   * <p>`feed('mic', 0.5)` でその音源へ音を流し込む（送信は画面の周期が行う）。
   * 常時接続は使わせない（HTTP の送信で確かめる＝`stubNoSocket`）。</p>
   */
  function stubStreamRecorder(options: { dual?: boolean } = {}): {
    feed: (source: 'mic' | 'shared', seconds: number) => Promise<void>
  } {
    class FakeAudioWorkletNode {
      static instances: FakeAudioWorkletNode[] = []
      port = {
        onmessage: null as ((event: MessageEvent<Float32Array>) => void) | null,
        close: () => undefined
      }
      constructor(public context: unknown, public name: string) {
        void context
        void name
        FakeAudioWorkletNode.instances.push(this)
      }
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    class FakeAudioContext {
      sampleRate = 48000
      destination = {}
      audioWorklet = { addModule: vi.fn(async () => undefined) }
      createMediaStreamSource(): unknown { return { connect: () => undefined, disconnect: () => undefined } }
      createGain(): unknown { return { gain: { value: 1 }, connect: () => undefined, disconnect: () => undefined } }
      createMediaStreamDestination(): { stream: object } { return { stream: { id: 'mixed' } } }
      createDynamicsCompressor(): unknown {
        return {
          threshold: { value: 0 }, knee: { value: 0 }, ratio: { value: 1 },
          attack: { value: 0 }, release: { value: 0 },
          connect: () => undefined, disconnect: () => undefined
        }
      }
      createAnalyser(): unknown {
        return {
          fftSize: 1024, connect: () => undefined, disconnect: () => undefined,
          getFloatTimeDomainData: (buffer: Float32Array) => buffer.fill(0)
        }
      }
      close(): Promise<void> { return Promise.resolve() }
    }
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:x') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.stubGlobal('AudioContext', FakeAudioContext)
    vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
    class FakeMediaRecorder {
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      private listeners: Record<string, (() => void)[]> = {}
      constructor(public stream: unknown) { void this.stream }
      // 収尾は「最後の分塊（dataavailable）→ stop」を待つので、stop の通知も届くようにする
      addEventListener(type: string, handler: () => void): void {
        (this.listeners[type] ??= []).push(handler)
      }
      start(): void { this.state = 'recording' }
      stop(): void {
        this.state = 'inactive'
        for (const handler of this.listeners.stop ?? []) handler()
      }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    const media: Record<string, unknown> = {
      getUserMedia: vi.fn(async () => ({
        getAudioTracks: () => [{ kind: 'audio', readyState: 'live' }],
        getTracks: () => [{ stop: () => undefined }]
      }))
    }
    if (options.dual === true) {
      const track = { kind: 'audio', addEventListener: () => undefined, stop: () => undefined }
      media.getDisplayMedia = vi.fn(async () => ({
        getAudioTracks: () => [track],
        getVideoTracks: () => [{ kind: 'video', stop: () => undefined }],
        getTracks: () => [track]
      }))
    }
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: media })
    stubNoSocket()
    return {
      feed: async (source, seconds) => {
        // 音源の取り出しは**マイク → 共有**の順に作られる
        const node = FakeAudioWorkletNode.instances[source === 'shared' ? 1 : 0]
        node?.port.onmessage?.(
          { data: new Float32Array(Math.round(48_000 * seconds)).fill(0.4) } as MessageEvent<Float32Array>)
        await vi.advanceTimersByTimeAsync(2_000)
        await flushPromises()
      }
    }
  }

  /**
   * 録音（`MediaRecorder`）とマイクだけを使えるようにする（音は流さない＝軽い）。
   *
   * <p>収尾は「最後の分塊（`dataavailable`）→ `stop`」を待つので、**両方**が届くようにする
   * （届かないと、最後の分塊を送り切れないまま終了を送ってしまう）。</p>
   */
  function stubRecorderOnly(): void {
    class FakeMediaRecorder {
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      private listeners: Record<string, (() => void)[]> = {}
      constructor(public stream: unknown) { void this.stream }
      addEventListener(type: string, handler: () => void): void {
        (this.listeners[type] ??= []).push(handler)
      }
      start(): void { this.state = 'recording' }
      stop(): void {
        this.state = 'inactive'
        this.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
        for (const handler of this.listeners.stop ?? []) handler()
      }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
    })
  }

  /**
   * **統一の録音回放時間軸**を確かめるための代役（同じ `AudioContext` の時計をテストが動かす）。
   *
   * <p>マイクと共有は別の音源だが、1 つの `AudioContext` に載る＝**同じサンプル時計**になる。
   * ここでは `advance(秒)` でその時計を進め、`feed(source, 秒)` でその音源の音を流し込む
   * （実際の音は 1 秒ぶんを 1 回で渡すのではなく 100ms ごとに届くので、そこも真似る）。</p>
   */
  function stubClockRecorder(options: { dual?: boolean } = {}): {
    advance: (seconds: number) => void
    feed: (source: 'mic' | 'shared', seconds: number) => void
    flush: () => Promise<void>
    contexts: () => unknown[]
  } {
    class FakeNode {
      outputs: unknown[] = []
      gain = { value: 1 }
      threshold = { value: 0 }
      knee = { value: 0 }
      ratio = { value: 1 }
      attack = { value: 0 }
      release = { value: 0 }
      fftSize = 1024
      constructor(public context: FakeAudioContext) { /* 節点は自分のコンテキストを持つ */ }
      connect(target: unknown): void { this.outputs.push(target) }
      disconnect(): void { /* 何もしない */ }
      getFloatTimeDomainData(buffer: Float32Array): void { buffer.fill(0) }
    }
    class FakeAudioWorkletNode {
      static instances: FakeAudioWorkletNode[] = []
      port = {
        onmessage: null as ((event: MessageEvent<unknown>) => void) | null,
        close: () => undefined
      }
      constructor(public context: unknown, public name: string) {
        FakeAudioWorkletNode.instances.push(this)
      }
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    class FakeAudioContext {
      static instances: FakeAudioContext[] = []
      sampleRate = 48000
      /** テストが進める時計（`currentTime` は秒）。 */
      time = 0
      destination = {}
      audioWorklet = { addModule: vi.fn(async () => undefined) }
      limiter: FakeNode | null = null
      constructor() { FakeAudioContext.instances.push(this) }
      get currentTime(): number { return this.time }
      createMediaStreamDestination(): { stream: object } { return { stream: { id: 'mixed' } } }
      createDynamicsCompressor(): FakeNode {
        const node = new FakeNode(this)
        this.limiter = node
        return node
      }
      createGain(): FakeNode { return new FakeNode(this) }
      createAnalyser(): FakeNode { return new FakeNode(this) }
      createMediaStreamSource(): FakeNode { return new FakeNode(this) }
      close(): Promise<void> { return Promise.resolve() }
    }
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:x') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.stubGlobal('AudioContext', FakeAudioContext)
    vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      private listeners: Record<string, (() => void)[]> = {}
      constructor(public stream: unknown) { void this.stream; FakeMediaRecorder.instances.push(this) }
      addEventListener(type: string, handler: () => void): void {
        (this.listeners[type] ??= []).push(handler)
      }
      start(): void { this.state = 'recording' }
      stop(): void {
        this.state = 'inactive'
        for (const handler of this.listeners.stop ?? []) handler()
      }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    const media: Record<string, unknown> = {
      getUserMedia: vi.fn(async () => ({
        getAudioTracks: () => [{ kind: 'audio', readyState: 'live' }],
        getTracks: () => [{ stop: () => undefined }]
      }))
    }
    if (options.dual === true) {
      const track = { kind: 'audio', addEventListener: () => undefined, stop: () => undefined }
      media.getDisplayMedia = vi.fn(async () => ({
        getAudioTracks: () => [track],
        getVideoTracks: () => [{ kind: 'video', stop: () => undefined }],
        getTracks: () => [track]
      }))
    }
    Object.defineProperty(navigator, 'mediaDevices', { configurable: true, value: media })
    stubNoSocket()
    /** 音源ごとの「次の報告の時計」（100ms ずつ進める）。 */
    const nextClock: Record<string, number> = { mic: 0, shared: 0 }
    return {
      advance: (seconds) => {
        for (const context of FakeAudioContext.instances) context.time += seconds
      },
      feed: (source, seconds) => {
        // 取り出しは**マイク → 共有**の順に作られる（二音源のとき）
        const node = FakeAudioWorkletNode.instances[source === 'shared' ? 1 : 0]
        const context = node?.context as FakeAudioContext | undefined
        if (node === undefined || context === undefined) return
        const frame = 0.1
        for (let sent = 0; sent < seconds - 1e-6; sent += frame) {
          const at = nextClock[source] ?? 0
          node.port.onmessage?.({
            data: {
              samples: new Float32Array(Math.round(context.sampleRate * frame)).fill(0.4),
              contextSample: Math.round(at * context.sampleRate),
              sampleRate: context.sampleRate
            } as unknown as Float32Array
          } as MessageEvent<unknown>)
          nextClock[source] = at + frame
        }
      },
      flush: async () => { await flushPromises() },
      contexts: () => FakeAudioContext.instances
    }
  }

  /**
   * 利用者の指示 7: 話者は**音源の設定**（【新しい授業】で選んだ値）で決める。
   *
   * <p>サーバーは「共有の音が届いたか」で二音源かを決めるので、届いていない回は「講義」と返す。
   * 画面はその値に引きずられず、二音源の設定なら**マイク＝学生／共有の音＝先生**で出す。</p>
   */
  it('二音源の設定なら、サーバーが「講義」と返しても、マイクは学生・共有は先生として出す', async () => {
    vi.useFakeTimers()
    try {
      const recorder = stubStreamRecorder({ dual: true })
      mockApi({
        streamStt: true,
        // 既存の書き起こしは無し（ストリーミングで届く文だけを見る）
        detail: detail({ segments: [], notes: [] }),
        sttAdded: ({ source }) => [{
          segmentId: source === 'shared' ? 602 : 601,
          seq: source === 'shared' ? 3 : 2,
          startOffsetSeconds: source === 'shared' ? 3 : 1,
          endOffsetSeconds: source === 'shared' ? 4 : 2,
          // サーバーはまだ共有の音を受け取っていないので、どちらの文も「講義」と返す
          speaker: '講義',
          text: source === 'shared' ? '先生の説明です。' : '学生の質問です。',
          language: 'ja-JP', createdAt: null
        }]
      })
      const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
      await flushPromises()

      // 共有の音を先に送るが、並びは**実際の発言開始時刻**（学生 1 秒 → 先生 3 秒）
      await recorder.feed('shared', 0.5)
      await recorder.feed('mic', 0.5)

      const rows = wrapper.findAll('[data-cr-transcript-line]')
      expect(rows.map((row) => row.attributes('data-cr-transcript-line'))).toEqual(['2', '3'])
      expect(rows[0]?.attributes('data-cr-transcript-speaker')).toBe('学生')
      expect(rows[0]?.text()).toContain('学生の質問です。')
      expect(rows[1]?.attributes('data-cr-transcript-speaker')).toBe('先生')
      expect(rows[1]?.text()).toContain('先生の説明です。')
      expect(rows[0]?.classes()).toContain('cr-transcript__line--student')
      expect(rows[1]?.classes()).toContain('cr-transcript__line--teacher')
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 利用者の指示 7: 単一音源（マイクのみ）の設定では分けない（**講義**）。
   * サーバーが音源を取り違えて「学生」と返しても、画面は設定どおりに出す。
   */
  it('単一音源の設定では、サーバーが「学生」と返しても「講義」として出す', async () => {
    vi.useFakeTimers()
    try {
      const recorder = stubStreamRecorder()
      mockApi({ streamStt: true, sttSpeaker: '学生', detail: detail({ segments: [], notes: [] }) })
      const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic'))
      await flushPromises()

      await recorder.feed('mic', 0.5)

      const rows = wrapper.findAll('[data-cr-transcript-line]')
      expect(rows).toHaveLength(1)
      expect(rows[0]?.attributes('data-cr-transcript-speaker')).toBe('講義')
      expect(rows[0]?.classes()).toContain('cr-transcript__line--lecture')
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 利用者の指示 7: 話者は**その記録の設定**で決める（開いた URL の query ではない）。
   *
   * <p>録音中の画面は、録音を始めた時点で**実際に録音した音源**を**その記録のために覚える**。
   * 詳細画面はこの値で話者を決める（query は一覧・履歴・ブックマークから開いたときには
   * 残っていないか、別の記録の値になっている）。</p>
   */
  it('録音を始めたら、その記録の音源を覚える（詳細画面が話者を決められるように）', async () => {
    stubNoSocket()
    stubRecorderOnly()
    mockApi({ streamStt: true })
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    expect(localStorage.getItem('study21.classroom.audioMode.record.12')).toBe('mic-pc')
    // 端末の「前回の選択」とは別に持つ（別の記録の話者を勝手に決めない）
    expect(localStorage.getItem('study21.classroom.audioMode')).toBeNull()
  })

  /**
   * 利用者の指示 7: **録音中の画面から詳細画面へ進んでも、話者は食い違わない**。
   *
   * <p>録音中の画面が録音を始めたときに残した**その記録の音源**を、詳細画面が読む
   * （詳細画面は query を見ない＝一覧・ブックマークから開いても同じ話者になる）。</p>
   */
  it('録音から進んだ詳細画面でも、話者はその記録の設定で決める（一覧から開いても同じ）', async () => {
    stubNoSocket()
    stubDualSource(sharedStream(true))
    mockApi({ streamStt: true })
    const live = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()
    expect(live.wrapper.get('[data-cr-recording-status]').text()).toContain('録音中')

    // 詳細画面（音源の query は無い＝一覧やブックマークから開いたのと同じ）
    mockApi({
      detail: detail({
        segments: [{
          segmentId: 1, seq: 1, startOffsetSeconds: 0.5, endOffsetSeconds: 2,
          // サーバーは共有の音が届いていない回は「講義」と返す
          speaker: '講義', text: '学生の質問です。', language: 'ja', createdAt: null
        }], notes: []
      })
    })
    const { wrapper } = await mountClassroom(ClassroomDetailView, '/student/classroom/12')
    await flushPromises()

    expect(wrapper.get('[data-cr-transcript-line]').attributes('data-cr-transcript-speaker')).toBe('学生')
  })

  /**
   * 利用者の指示 7: **同じ発話の新しい文**（訂正・追記）を「同じ連番だから」と捨てない。
   *
   * <p>サーバーは発話キーで 1 行に寄せるので、同じ `segmentId`・同じ連番のまま**新しい文**が
   * 届く。連番だけを見て捨てると、直した文が画面に出ない。</p>
   */
  it('同じ発話の新しい文（訂正・追記）は捨てず、新しい文を出す', async () => {
    vi.useFakeTimers()
    try {
      /*
       * **どちらの「流し込み」への応答か**で文を決める。
       *
       * <p>送信は 100ms のフレームごとなので、0.5 秒の流し込みに何回も応答が返る。回数で
       * 決めると、同じ流し込みのあいだに文が入れ替わってしまう（利用者が見るのは「同じ発話が
       * 訂正された文」）。ここでは「2 回目の流し込みからは書き直した文」にする。</p>
       */
      let feed = 0
      const writtenPerCall: number[] = []
      const markFeed = (): void => { feed += 1 }
      const recorder = stubStreamRecorder()
      mockApi({
        streamStt: true,
        detail: detail({ segments: [], notes: [] }),
        // 2 回目は同じ発話が**書き直された**文（同じ segmentId・同じ連番）
        sttAdded: ({ index }) => {
          if (writtenPerCall[index] === undefined) writtenPerCall[index] = feed
          return [{
            segmentId: 601, seq: 2, startOffsetSeconds: 1, endOffsetSeconds: 2,
            speaker: '講義', text: writtenPerCall[index] <= 1 ? '最初の文です。' : '書き直した文です。',
            language: 'ja-JP', createdAt: null
          }]
        }
      })
      const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
      await wrapper.get('[data-cr-start-recording]').trigger('click')
      await flushPromises()

      markFeed()
      await recorder.feed('mic', 0.5)
      expect(wrapper.get('[data-cr-transcript]').text()).toContain('最初の文です。')

      markFeed()
      await recorder.feed('mic', 0.5)
      const rows = wrapper.findAll('[data-cr-transcript-line]')
      // 行は増えない（同じ発話）。**新しい文が勝つ**
      expect(rows).toHaveLength(1)
      expect(rows[0]?.text()).toContain('書き直した文です。')
      expect(rows[0]?.text()).not.toContain('最初の文です。')
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 利用者の指示 7: **古い取得の結果で、画面へ直接届いた新しい文を巻き戻さない**。
   *
   * <p>ポーリングや詳細の取得は**始めた時点の内容**を返すことがある（応答が遅れて返る）。同じ発話
   * について、直接届いた新しい文のあとに古い取得の結果が届いても、古い文で上書きしない
   * （取得の行には音源が付かないので、上書きすると**話者もサーバーの値へ戻ってしまう**）。</p>
   */
  it('古い取得の結果で、直接届いた新しい文を巻き戻さない', async () => {
    vi.useFakeTimers()
    try {
      const recorder = stubStreamRecorder()
      mockApi({
        streamStt: true,
        // 取得（ポーリング・詳細）は**古い文**を返し続ける（応答が遅れて返った想定）
        detail: detail({
          segments: [{
            segmentId: 601, seq: 2, startOffsetSeconds: 0, endOffsetSeconds: 1.2,
            speaker: '講義', text: '最初の文です。', language: 'ja-JP', createdAt: null
          }], notes: []
        }),
        // 2 回目に届くのは同じ発話の**書き直した文**
        sttAdded: ({ index }) => [{
          segmentId: 601, seq: 2, startOffsetSeconds: 0, endOffsetSeconds: 1.2,
          speaker: '講義', text: index <= 1 ? '最初の文です。' : '書き直した文です。',
          language: 'ja-JP', createdAt: null
        }]
      })
      const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
      await wrapper.get('[data-cr-start-recording]').trigger('click')
      await flushPromises()

      await recorder.feed('mic', 0.5)
      await recorder.feed('mic', 0.5)
      expect(wrapper.get('[data-cr-transcript]').text()).toContain('書き直した文です。')

      // 5 周期（10 秒）後に詳細を取り直す＝古い行が届く
      await vi.advanceTimersByTimeAsync(10_000)
      await flushPromises()

      // 直した文のまま（古い取得で巻き戻さない・行も増えない）
      expect(wrapper.get('[data-cr-transcript]').text()).toContain('書き直した文です。')
      expect(wrapper.get('[data-cr-transcript]').text()).not.toContain('最初の文です。')
      expect(wrapper.findAll('[data-cr-transcript-line]')).toHaveLength(1)
    } finally {
      vi.useRealTimers()
    }
  })

  it('古い記録（話者ラベルが空）でも「講義」として表示する', async () => {
    mockApi({
      detail: detail({
        segments: [
          // 旧データ相当（話者ラベルが null・発話キーや音源の概念が無い）
          { segmentId: 1, seq: 1, startOffsetSeconds: 0, endOffsetSeconds: 20, speaker: null,
            text: '昔の書き起こしです。', language: 'ja', createdAt: null },
          // 旧データで「講義」が入っていたもの
          { segmentId: 2, seq: 2, startOffsetSeconds: 20, endOffsetSeconds: 40, speaker: '講義',
            text: 'こちらも昔の行です。', language: 'ja', createdAt: null }
        ], notes: []
      })
    })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await flushPromises()

    const rows = wrapper.findAll('[data-cr-transcript-line]')
    expect(rows).toHaveLength(2)
    for (const row of rows) {
      expect(row.attributes('data-cr-transcript-speaker')).toBe('講義')
      expect(row.classes()).toContain('cr-transcript__line--lecture')
    }
    expect(rows[0]?.text()).toContain('昔の書き起こしです。')
  })

  it('AI 解析がオフのときは、書き起こしだけを行うと案内する', async () => {
    mockApi({ noteEnabled: false })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    expect(wrapper.get('[data-cr-ai-notes-off]').text()).toContain('AI 解析（フェーズノート・最終まとめ）は現在オフ')
    expect(wrapper.get('[data-cr-ai-notes-off]').text()).toContain('書き起こしだけ')
  })

  it('AI 解析が有効なときは、その案内を出さない', async () => {
    mockApi({ noteEnabled: true })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    expect(wrapper.find('[data-cr-ai-notes-off]').exists()).toBe(false)
  })

  it('サーバー認識のときは画面側の認識を始めない', async () => {
    const recognition = installFakeSpeechRecognition()
    mockApi({ sttMode: 'SERVER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    expect(wrapper.get('[data-cr-live-stt-mode]').text()).toContain('サーバー認識')

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    expect(recognition.last.startCount).toBe(0)
  })

  it('マウント時は録音を始めない（マイクは【録音を開始】を押したときだけ使う）', async () => {
    // 記録は作成時点で状態=RECORDING。ここで録音中にしてしまうと MediaRecorder が
    // 動かないまま「録音したつもり」になり、音声が 1 件も送られない
    const { calls } = mockApi({
      detail: detail({ status: 'RECORDING', statusLabel: '録音中', startTime: null, segments: [], notes: [] })
    })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('停止中')
    expect(wrapper.find('[data-cr-start-recording]').exists()).toBe(true)
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(false)
    // 開始 API も分塊の送信も起きない
    expect(callTo(calls, '/api/user/classroom/12/start', 'POST')).toBeUndefined()
    expect(callTo(calls, '/chunks')).toBeUndefined()
  })

  it('録音の途中で開き直したら、続きから録音できると案内する', async () => {
    vi.useFakeTimers()
    mockApi({
      detail: detail({
        status: 'RECORDING', statusLabel: '録音中',
        startTime: new Date(Date.now() - 65_000).toISOString(), segments: [], notes: []
      })
    })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    expect(wrapper.get('[data-cr-resume-notice]').text()).toContain('続きを録音するには')
    // 経過時間はサーバーの開始時刻から続ける
    expect(wrapper.get('[data-cr-timer]').text()).toBe('00:01:05')
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('停止中')

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('録音中')
    expect(wrapper.find('[data-cr-resume-notice]').exists()).toBe(false)
  })

  it('【録音を開始】で MediaRecorder を動かし、分塊を連番つきで送る（トリガーで batC61 も起動）', async () => {
    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      timeslice: number | undefined
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      constructor(public stream: unknown, public options?: unknown) {
        void this.stream
        void this.options
        FakeMediaRecorder.instances.push(this)
      }
      start(timeslice?: number): void { this.timeslice = timeslice; this.state = 'recording' }
      stop(): void { this.state = 'inactive' }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
    })

    const { calls } = mockApi()
    // 分塊の応答はトリガー成立（runPath つき）にする
    vi.mocked(fetch).mockImplementation(async (url: RequestInfo | URL, init?: RequestInit) => {
      const target = String(url)
      calls.push({ url: target, method: (init?.method ?? 'GET').toUpperCase(), body: null })
      const ok = (data: unknown): Response => new Response(
        JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
      if (target.includes('/chunks') && (init?.method ?? 'GET').toUpperCase() === 'GET') {
        return ok({ items: [], chunkCount: 0, maxSeq: 0, nextSeq: 1, totalBytes: 0, recordedSeconds: null })
      }
      if (target.includes('/chunks')) {
        return ok({
          recordId: 12, seq: 1, nextSeq: 2, nextChunkSeq: 2,
          appendedSegments: [{
            segmentId: 1, seq: 1, startOffsetSeconds: 0, endOffsetSeconds: 20,
            speaker: null, text: '比例のグラフについて学びます。', language: 'ja', createdAt: null
          }],
          pendingNoteId: 91, triggered: true, status: 'RECORDING',
          runPath: '/api/admin/batch/classroom/notes/91/run'
        })
      }
      if (target.includes('/options')) {
        return ok({
          enabled: true, chunkSeconds: 20, maxRecordingMinutes: 120, retentionDays: 30,
          dailyLimit: 0, usedToday: 0, notice: ''
        })
      }
      if (target.includes('/start')) {
        return ok({ recordId: 12, recordNo: 'CR20260914-0001', status: 'RECORDING', statusLabel: '録音中', version: 2 })
      }
      return ok(detail({ segments: [], notes: [] }))
    })

    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    // 分塊の長さ（設定値）で MediaRecorder を動かす
    const recorder = FakeMediaRecorder.instances[0]
    expect(recorder?.timeslice).toBe(20_000)

    // 分塊が届いたら multipart で seq つきで送る
    recorder?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    const chunkCall = calls.find((call) => call.method === 'POST' && call.url.includes('/chunks'))
    // 連番に加えて、画面が測った実際の経過秒も載る（時系列が壊れないように）
    expect(chunkCall?.url).toContain('/api/user/classroom/12/chunks?seq=1')
    expect(chunkCall?.url).toContain('startSeconds=0')
    expect(chunkCall?.method).toBe('POST')
    // 応答の転写を画面へ足す
    expect(wrapper.get('[data-cr-transcript]').text()).toContain('比例のグラフについて学びます。')
    // トリガー成立なら admin-api の薄い入口（batC61）を 1 回だけ呼ぶ
    expect(calls.find((call) => call.url === '/api/admin/batch/classroom/notes/91/run')?.method).toBe('POST')
  })

  it('サーバー側認識のときは、書き起こし用の 16kHz PCM を分塊と一緒に送る', async () => {
    /** 音声の取り出し（AudioWorklet）の代役。テストがサンプルを流し込める。 */
    class FakeAudioWorkletNode {
      static instances: FakeAudioWorkletNode[] = []
      port = {
        onmessage: null as ((event: MessageEvent<Float32Array>) => void) | null,
        close: () => undefined
      }
      constructor(public context: unknown, public name: string) {
        FakeAudioWorkletNode.instances.push(this)
      }
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    class FakeAudioContext {
      sampleRate = 48000
      destination = {}
      audioWorklet = { addModule: vi.fn(async () => undefined) }
      createMediaStreamSource(): { connect: () => void; disconnect: () => void } {
        return { connect: () => undefined, disconnect: () => undefined }
      }
      createGain(): { gain: { value: number }; connect: () => void } {
        return { gain: { value: 1 }, connect: () => undefined }
      }
      close(): Promise<void> { return Promise.resolve() }
    }
    // jsdom は URL.createObjectURL を持たない（AudioWorklet のモジュール読み込みで使う）
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true, value: vi.fn(() => 'blob:study21-worklet')
    })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.stubGlobal('AudioContext', FakeAudioContext)
    vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      timeslice: number | undefined
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      constructor(public stream: unknown) { void this.stream; FakeMediaRecorder.instances.push(this) }
      start(timeslice?: number): void { this.timeslice = timeslice; this.state = 'recording' }
      stop(): void { this.state = 'inactive' }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
    })

    const { calls } = mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    // マイクの音が 48kHz で届く（1 秒ぶん）
    const worklet = FakeAudioWorkletNode.instances[0]
    expect(worklet?.name).toBe('study21-pcm-capture')
    worklet?.port.onmessage?.({ data: new Float32Array(48000).fill(0.5) } as MessageEvent<Float32Array>)

    // 分塊が届いたら、その区切りで PCM も一緒に送る
    const recorder = FakeMediaRecorder.instances[0]
    recorder?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    const form = calls.find((call) => call.method === 'POST' && call.url.includes('/chunks'))?.form
    const pcm = form?.get('stt') as File
    expect(pcm).toBeInstanceOf(File)
    // 48kHz の 1 秒 → 16kHz の 1 秒（16bit なので 32000 バイト）
    expect(pcm.size).toBe(32_000)
    expect(form?.get('file')).toBeInstanceOf(Blob)
  })

  /**
   * 二音源（マイク＋スピーカー）＋サーバー認識では、書き起こし用 PCM を**混ぜた後**から取る。
   *
   * <p>マイクの流れから取ってしまうと、サーバー認識でも**先生の声が文字にならない**
   * （実測で利用者から報告があった）。ここでは「PCM の取り出し（AudioWorklet）が
   * ミキサー（リミッター）につながっている」ことを固定する。</p>
   */
  it('二音源＋サーバー認識: 書き起こし用 PCM は混ぜた音（ミキサー）から取る', async () => {
    /** 節点の代役（どこへつないだか・どのコンテキストの節点かを覚える）。 */
    class FakeNode {
      outputs: unknown[] = []
      gain = { value: 1 }
      threshold = { value: 0 }
      knee = { value: 0 }
      ratio = { value: 1 }
      attack = { value: 0 }
      release = { value: 0 }
      fftSize = 1024
      constructor(public context: unknown) { /* 本物と同じく自分のコンテキストを持つ */ }
      connect(target: unknown): void { this.outputs.push(target) }
      disconnect(): void { /* 何もしない */ }
      getFloatTimeDomainData(buffer: Float32Array): void { buffer.fill(0) }
    }
    /** PCM の取り出し（AudioWorklet）の代役。 */
    class FakeAudioWorkletNode {
      static instances: FakeAudioWorkletNode[] = []
      port = {
        onmessage: null as ((event: MessageEvent<Float32Array>) => void) | null,
        close: () => undefined
      }
      constructor(public context: unknown, public name: string) {
        FakeAudioWorkletNode.instances.push(this)
      }
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    class FakeAudioContext {
      static instances: FakeAudioContext[] = []
      sampleRate = 48000
      destination = {}
      /** 書き起こし用の取り出し（AudioWorklet）は**この同じコンテキスト**に載る。 */
      audioWorklet = { addModule: vi.fn(async () => undefined) }
      /** 混ぜる音の出口（リミッター）。ここへ PCM の取り出しをつないでいれば「混ぜた音」から取れる。 */
      limiter: FakeNode | null = null
      /** マイク／共有の流れから作った節点の数（PCM 用に増えていないかを見る）。 */
      streamSourceCount = 0
      constructor() { FakeAudioContext.instances.push(this) }
      createMediaStreamDestination(): { stream: object } { return { stream: { id: 'mixed' } } }
      createDynamicsCompressor(): FakeNode {
        const node = new FakeNode(this)
        this.limiter = node
        return node
      }
      createGain(): FakeNode { return new FakeNode(this) }
      createAnalyser(): FakeNode { return new FakeNode(this) }
      createMediaStreamSource(): FakeNode { this.streamSourceCount += 1; return new FakeNode(this) }
      close(): Promise<void> { return Promise.resolve() }
    }
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true, value: vi.fn(() => 'blob:study21-worklet')
    })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.stubGlobal('AudioContext', FakeAudioContext)
    vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      constructor(public stream: unknown) { void this.stream; FakeMediaRecorder.instances.push(this) }
      start(): void { this.state = 'recording' }
      stop(): void { this.state = 'inactive' }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    const shared = {
      getAudioTracks: () => [{ kind: 'audio', addEventListener: () => undefined }],
      getVideoTracks: () => [],
      getTracks: () => [{ stop: () => undefined }]
    } as unknown as MediaStream
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn(async () => ({
          getAudioTracks: () => [{ kind: 'audio', readyState: 'live' }],
          getTracks: () => [{ stop: () => undefined }]
        })),
        getDisplayMedia: vi.fn(async () => shared)
      }
    })

    const { calls } = mockApi({ sttMode: 'SERVER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    // 共有の音を混ぜたうえで録音を始めている
    const worklet = FakeAudioWorkletNode.instances[0]
    expect(worklet?.name).toBe('study21-pcm-capture')
    // PCM の取り出しは**ミキサー（リミッター）**につながっている（マイクの流れからではない）
    const graphContext = FakeAudioContext.instances.find((context) => context.limiter !== null)
    expect(graphContext?.limiter?.outputs).toContain(worklet)
    // **混ぜているのと同じコンテキスト**で作っている（別のコンテキストだと connect できず、
    // 二音源のときだけ黙って PCM が取れなくなる）
    expect(worklet?.context).toBe(graphContext)
    // 流れから作った節点は「マイク」と「共有」の 2 つだけ（PCM 用に取り直していない）
    expect(graphContext?.streamSourceCount).toBe(2)

    worklet?.port.onmessage?.({ data: new Float32Array(48000).fill(0.5) } as MessageEvent<Float32Array>)
    FakeMediaRecorder.instances[0]?.ondataavailable?.(
      { data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    const form = calls.find((call) => call.method === 'POST' && call.url.includes('/chunks'))?.form
    const pcm = form?.get('stt') as File
    expect(pcm).toBeInstanceOf(File)
    expect(pcm.size).toBe(32_000)
    // 録音そのものも、混ぜた音の流れから取っている
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)
  })

  /**
   * 利用者の指示（統一の録音回放時間軸）: **分塊の経過秒も、書き起こしの位置も同じ時間軸**。
   *
   * <p>1 つの `AudioContext` の時計（サンプル位置）から出すので、**同じ瞬間の音は同じ時刻**に
   * なる。ここでは 3 秒ぶんの音を流して、分塊の終わりの秒（`endSeconds`）と、
   * 書き起こしへ送ったフレームの位置（`startSample`）が**同じ時間軸**であることを見る。</p>
   *
   * <p>測れる誤差: 画面は 100ms のフレームに切って送るので、位置は**フレームの先頭**
   * （±100ms）。時計そのものは同じなので、**その他のずれは 0ms** を期待する。</p>
   */
  it('分塊の経過秒と書き起こしの位置は同じ時間軸から出る（許容 ±100ms）', async () => {
    vi.useFakeTimers()
    try {
    const clock = stubClockRecorder()
    const { calls } = mockApi({ streamStt: true })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await clock.flush()

    /*
     * 3 秒ぶんの音を 100ms ずつ流し込む。**送信の周期（250ms）ごとに区切って進める**ので、
     * 実際の録音と同じ順序（採る → 貯める → 周期で送る）になる。
     */
    for (let elapsed = 0; elapsed < 3; elapsed += 0.1) {
      clock.advance(0.1)
      clock.feed('mic', 0.1)
      await vi.advanceTimersByTimeAsync(100)
      await clock.flush()
    }
    // 送り切るのを待つ（送信は 250ms の周期なので、少し余分に回す）
    await vi.advanceTimersByTimeAsync(2_000)
    await clock.flush()

    // 書き起こしへ送ったフレームの位置は 0 から 3 秒の間（100ms ごと）
    const pushes = calls.filter((call) => call.url.includes('/stt/stream?source=mic'))
    expect(pushes.length).toBeGreaterThan(0)
    const positions = pushes
      .map((call) => Number(/startSample=(\d+)/.exec(call.url)?.[1] ?? '-1'))
      .filter((value) => value >= 0)
    /*
     * 位置は**同じ時間軸から出る**:
     * ・最初は録音の先頭（0 秒）で、前へだけ進む（送り直しで戻らない）
     * ・1 回に送る長さは 100ms の整数倍（フレームの先頭の位置を送っている）
     * ・**送った位置は、流し込んだ音（3 秒）を追い越さない**（位置が先へ飛ばない）
     */
    expect(positions[0]).toBe(0)
    for (let index = 1; index < positions.length; index += 1) {
      const step = (positions[index] as number) - (positions[index - 1] as number)
      expect(step).toBeGreaterThanOrEqual(1_600)
      expect(step % 1_600).toBe(0)
    }
    expect(positions.at(-1) as number).toBeLessThanOrEqual(48_000)
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 利用者の指示（統一の録音回放時間軸）: **停止 → 続きの録音**で位置が続く。
   *
   * <p>保存済みの分塊が 60 秒ぶんある記録を開き直して録音を始めたとき、分塊の経過秒も
   * 書き起こしの位置も**60 秒から続く**（0 から送ると後端が「すでに処理した位置より古い」として
   * その音を丸ごと捨てる＝書き起こしが消える）。</p>
   */
  it('保存済みの位置から続けて録る（分塊の経過秒も書き起こしの位置も 0 へ戻らない）', async () => {
    vi.useFakeTimers()
    try {
    const clock = stubClockRecorder()
    const { calls } = mockApi({
      streamStt: true,
      // 分塊の一覧（GET）は「60 秒ぶん保存済み」を返す（既定のモックは 0 件）
      chunkList: {
        items: [{
          seq: 3, byteSize: 100, startOffsetSeconds: 40, endOffsetSeconds: 60,
          mime: 'audio/webm', containerHead: false, processingStatus: 'STORED',
          segmentCount: 0, createdAt: null
        }],
        chunkCount: 3, maxSeq: 3, nextSeq: 4, totalBytes: 300, recordedSeconds: 60
      }
    })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await clock.flush()

    // 1 秒ぶん録る（送信の周期ごとに区切って進める）
    for (let elapsed = 0; elapsed < 1; elapsed += 0.1) {
      clock.advance(0.1)
      clock.feed('mic', 0.1)
      await vi.advanceTimersByTimeAsync(100)
      await clock.flush()
    }
    await vi.advanceTimersByTimeAsync(1_000)
    await clock.flush()

    // 書き起こしの位置は 60 秒（960,000 サンプル）から続く
    const pushes = calls.filter((call) => call.url.includes('/stt/stream?source=mic'))
    expect(pushes.length).toBeGreaterThan(0)
    const first = Number(/startSample=(\d+)/.exec(pushes[0]?.url ?? '')?.[1] ?? '-1')
    // 0 から送らない（後端が「すでに処理した位置より古い」として捨てる）
    expect(first).toBeGreaterThanOrEqual(960_000)

    // 分塊（再生用の音声）の経過秒も 60 秒から続く（0 へ戻ると前の録音と時系列が重なる）
    const recorder = (globalThis as unknown as {
      MediaRecorder: { instances: { ondataavailable: ((event: { data: Blob }) => void) | null }[] }
    }).MediaRecorder.instances[0]
    recorder?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await clock.flush()
    const chunkCall = [...calls].reverse().find((call) => call.url.includes('/chunks'))
    const startSeconds = Number(/startSeconds=([\d.]+)/.exec(chunkCall?.url ?? '')?.[1] ?? '-1')
    expect(startSeconds).toBeGreaterThanOrEqual(60)
    expect(chunkCall?.url).toContain('seq=4')
    } finally {
      vi.useRealTimers()
    }
  })

  it('ブラウザ認識のときは PCM を取らない（Web Speech API が書き起こすので要らない）', async () => {
    class FakeAudioWorkletNode {
      static instances: unknown[] = []
      port = { onmessage: null, close: () => undefined }
      constructor() { FakeAudioWorkletNode.instances.push(this) }
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    class FakeAudioContext {
      sampleRate = 48000
      destination = {}
      audioWorklet = { addModule: vi.fn(async () => undefined) }
      createMediaStreamSource(): unknown { return { connect: () => undefined, disconnect: () => undefined } }
      createGain(): unknown { return { gain: { value: 1 }, connect: () => undefined } }
      close(): Promise<void> { return Promise.resolve() }
    }
    // jsdom は URL.createObjectURL を持たない（AudioWorklet のモジュール読み込みで使う）
    Object.defineProperty(URL, 'createObjectURL', {
      configurable: true, value: vi.fn(() => 'blob:study21-worklet')
    })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.stubGlobal('AudioContext', FakeAudioContext)
    vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      constructor(public stream: unknown) { void this.stream; FakeMediaRecorder.instances.push(this) }
      start(): void { this.state = 'recording' }
      stop(): void { this.state = 'inactive' }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
    })

    const { calls } = mockApi({ sttMode: 'BROWSER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    expect(FakeAudioWorkletNode.instances).toHaveLength(0)
    const recorder = FakeMediaRecorder.instances[0]
    recorder?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    const form = calls.find((call) => call.method === 'POST' && call.url.includes('/chunks'))?.form
    expect(form?.get('stt')).toBeNull()
  })

  /**
   * 【終了】は**最後の分塊を送り終えてから**送る。
   *
   * `MediaRecorder` の最後の分塊は `stop()` のあとに届くので、待たずに終了を送るとサーバーが先に
   * 録音を終わらせ、あとから届いた分塊が 409（録音中ではありません）で拒否されて
   * **その回の音声が丸ごと残らない**（実測）。送る順番を固定する。
   */
  /**
   * 【録音を停止】と【授業を終了】は**同じ收尾**を通り、2 回呼ばれても 1 回しか走らない
   * （利用者の指示: 停止と終了で同じ待てる・冪等な処理。二重に閉じない・先にまとめを作らない）。
   */
  it('停止と終了は同じ收尾を共有し、二重に走らない', async () => {
    // 録音（MediaRecorder）とマイクが使える環境にする（音は流さないので軽い）
    class FakeMediaRecorder {
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      private listeners: Record<string, (() => void)[]> = {}
      constructor(public stream: unknown) { void this.stream }
      // 収尾は「最後の分塊（dataavailable）→ stop」を待つので、stop の通知が要る
      addEventListener(type: string, handler: () => void): void {
        (this.listeners[type] ??= []).push(handler)
      }
      start(): void { this.state = 'recording' }
      stop(): void {
        this.state = 'inactive'
        for (const handler of this.listeners.stop ?? []) handler()
      }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
    })

    const { calls } = mockApi({ streamStt: true })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()
    // 停止（尾部の送信と STT の收尾は待たずに走る）
    await wrapper.get('[data-cr-stop-recording]').trigger('click')
    await flushPromises()
    const finishesAfterStop = calls.filter((call) => call.url.includes('/stt/stream/finish')).length

    // 終了（同じ收尾を待つ。もう一度 /finish は送らない）
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    const finishesAfterEnd = calls.filter((call) => call.url.includes('/stt/stream/finish')).length

    expect(finishesAfterStop).toBeGreaterThanOrEqual(1)
    expect(finishesAfterEnd).toBe(finishesAfterStop)
  })

  /**
   * 【終了】は**書き起こしの尾部（最後の確定文）まで待ってから**記録を終える。
   *
   * 待たずに終えると、尾部の文は記録の終了後に届く（実測: 停止の 14 秒後に確定した）。
   * そのとき記録は既に終わっているため保存に失敗し、**その音源の書き起こしが丸ごと消えた**
   * （実測: マイク側が 1 文字も残らなかった）。
   */
  it('【終了】は書き起こしの尾部が返るまで待ってから記録を終える', async () => {
    class FakeMediaRecorder {
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      private listeners: Record<string, (() => void)[]> = {}
      constructor(public stream: unknown) { void this.stream }
      // 収尾は「最後の分塊（dataavailable）→ stop」を待つので、stop の通知が要る
      addEventListener(type: string, handler: () => void): void {
        (this.listeners[type] ??= []).push(handler)
      }
      start(): void { this.state = 'recording' }
      stop(): void {
        this.state = 'inactive'
        for (const handler of this.listeners.stop ?? []) handler()
      }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
    })

    const gate: { release: () => void } = { release: () => undefined }
    const holdSttFinish = new Promise<void>((resolve) => { gate.release = resolve })
    const { calls } = mockApi({ streamStt: true, holdSttFinish })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()
    // 【終了】は停止と**同じ収尾**（録音の停止 → 音声の送信 → 書き起こしの最終結果）を通る
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()

    // 尾部の確定文が返るまでは終了を送らない（段階表示も「書き起こしの最終結果を待って」のまま）
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(false)
    expect(wrapper.get('[data-cr-finish-phase]').text()).toContain('書き起こしの最終結果を待って')

    gate.release()
    await flushPromises()
    await flushPromises()

    // 尾部を受け取ってから終了する
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(true)
  })

  /**
   * 画面が認識する設定（ブラウザ認識）でも、**最後の発話がサーバーへ入るまで**待ってから終える。
   *
   * <p>認識の結果は 1 件ずつ順番に送っている（`transcriptChain`）。送信中の 1 件を待たずに記録を
   * 終えると、サーバーは録音中でないとして断り、**その発話が丸ごと残らない**（尾部の文が
   * 最終まとめにも入らない）。収尾は「録音の停止 → 音声の送信 → **書き起こしの最終結果**
   * → 記録の終了」なので、画面が認識した分もここで待つ。</p>
   */
  it('ブラウザ認識: 送信中の最後の発話が入るまで待ってから記録を終える', async () => {
    const recognition = installFakeSpeechRecognition()
    stubNoSocket()
    const { calls, fetchMock } = mockApi({ sttMode: 'BROWSER', detail: detail({ segments: [], notes: [] }) })
    // 発話の送信（`/transcripts`）の応答を保留する（テストが後から解放する）
    const original = fetchMock.getMockImplementation() as (
      url: string, init?: RequestInit
    ) => Promise<Response>
    const gate: { release: () => void } = { release: () => undefined }
    const hold = new Promise<void>((resolve) => { gate.release = resolve })
    let transcriptSent = false
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      if (String(url).includes('/transcripts')) {
        transcriptSent = true
        await hold
      }
      return original(String(url), init)
    })

    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    // 認識した最後の発話（送信はまだ返ってこない）
    recognition.emitResult('最後の発話です。', true)
    await flushPromises()
    expect(transcriptSent).toBe(true)

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await flushPromises()

    // 送信中の発話を待つ（記録の終了はまだ送らない）
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(false)

    gate.release()
    await flushPromises()
    await flushPromises()

    // 発話が入ってから終える（順番で固定）
    const transcriptIndex = calls.findIndex((call) => call.url.includes('/transcripts'))
    const endIndex = calls.findIndex((call) => call.url.includes('/classroom/12/end'))
    expect(transcriptIndex).toBeGreaterThanOrEqual(0)
    expect(endIndex).toBeGreaterThan(transcriptIndex)
  })

  /**
   * 収尾は**停止と終了で同じ 1 つ**の処理（利用者の指示 7 節の残り）。
   *
   * <p>二度押しは**その場で**止める（`await` の前に番人を立てる）。ボタンの無効化（`:disabled`）は
   * 次の描画で効くので、同じ瞬間の早押しはこれで止める。1 回しか走らなければ、記録の終了も
   * 最終まとめの起動も 1 回だけで済む。</p>
   */
  it('収尾は二度押しをその場で止める（音の送信も終了も最終まとめも 1 回だけ）', async () => {
    // 時刻を止める（Vue は「描画より古いイベント」を捨てるので、同じ瞬間の 2 回を確かめられるように）
    vi.useFakeTimers()
    try {
      stubNoSocket()
      stubRecorderOnly()
      // 分塊の送信を保留して、1 段目（音声の送信）の**途中**で二度押しする
      const gate: { release: () => void } = { release: () => undefined }
      const chunks = new Promise<void>((resolve) => { gate.release = resolve })
      const { calls } = mockApi({ streamStt: true, holdChunks: chunks })
      const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
      await wrapper.get('[data-cr-start-recording]').trigger('click')
      await flushPromises()

      // 同じ瞬間の二度押し（1 回目の `await` の前。ボタンの無効化はまだ描画されていない）
      const button = wrapper.get('[data-cr-finish]')
      void button.trigger('click')
      void button.trigger('click')
      await flushPromises()

      gate.release()
      await flushPromises()
      await flushPromises()

      expect(calls.filter((call) => call.method === 'POST' && call.url.includes('/chunks'))).toHaveLength(1)
      expect(calls.filter((call) => call.url.includes('/classroom/12/end'))).toHaveLength(1)
      expect(calls.filter((call) => call.url.includes('/stt/stream/finish'))).toHaveLength(1)
      expect(calls.filter((call) => call.url.includes('/api/admin/batch/classroom/notes/'))).toHaveLength(1)
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 停止したあと**続きを録音し直した**ときは、その録音の収尾も改めて締める。
   *
   * <p>前の録音の「収尾は済んだ」を引き継ぐと、2 回目の `/finish` を送らず、続きの録音の
   * 尾部の文が丸ごと残らない（音源ごとの送信は作り直されるので、収尾も改めて要る）。</p>
   */
  it('停止して続きを録音し直したら、その収尾（書き起こしの最終結果）も改めて締める', async () => {
    stubNoSocket()
    stubRecorderOnly()
    const { calls } = mockApi({ streamStt: true })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    const finishes = (): number => calls.filter((call) => call.url.includes('/stt/stream/finish')).length

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-cr-stop-recording]').trigger('click')
    await flushPromises()
    await flushPromises()
    expect(finishes()).toBe(1)

    // 続きを録音する（収尾が済んだあとなので始められる）
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('録音中')

    await wrapper.get('[data-cr-stop-recording]').trigger('click')
    await flushPromises()
    await flushPromises()
    // 2 回目の収尾も走る（前の録音の「済んだ」を引き継がない）
    expect(finishes()).toBe(2)
  })

  /**
   * 停止の収尾が走っている**最中**に【授業を終了】を押したら、押した操作を捨てない。
   *
   * <p>ボタンの無効化は次の描画で効くので、早押しでは終了の合図が届く。停止の収尾を
   * **そのまま続けて**記録の終了まで進める（二重に走らせない・音は送り直さない）。</p>
   */
  it('停止の収尾が走っている最中に【授業を終了】を押したら、その続きとして記録を終える', async () => {
    vi.useFakeTimers()
    try {
      stubNoSocket()
      stubRecorderOnly()
      const gate: { release: () => void } = { release: () => undefined }
      const chunks = new Promise<void>((resolve) => { gate.release = resolve })
      const { calls } = mockApi({ streamStt: true, holdChunks: chunks })
      const { wrapper, router } = await mountClassroom(ClassroomLiveView, LIVE)
      await wrapper.get('[data-cr-start-recording]').trigger('click')
      await flushPromises()

      // 停止（収尾の 1 段目で止まっている）→ 同じ瞬間に終了
      void wrapper.get('[data-cr-stop-recording]').trigger('click')
      void wrapper.get('[data-cr-finish]').trigger('click')
      await flushPromises()

      gate.release()
      await flushPromises()
      await flushPromises()

      expect(calls.filter((call) => call.method === 'POST' && call.url.includes('/chunks'))).toHaveLength(1)
      expect(calls.filter((call) => call.url.includes('/classroom/12/end'))).toHaveLength(1)
      expect(calls.filter((call) => call.url.includes('/api/admin/batch/classroom/notes/'))).toHaveLength(1)
      expect(router.currentRoute.value.path).toBe('/student/classroom/12')
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 収尾が失敗したら**成功と言わない**（詳細へ進まない・最終まとめを起動しない）。
   *
   * <p>後端は尾部を取り切れなかったときに HTTP 200 で理由を返すので、`finish()` は投げない。
   * 画面は理由を見て止まり、**まだ済んでいない段だけ**をやり直せる（何回失敗しても押せる）。</p>
   */
  it('収尾が失敗したら成功と言わず、やり直しで続きから終われる（何回失敗しても押せる）', async () => {
    stubNoSocket()
    stubRecorderOnly()
    // 収尾の 1 回目と 2 回目は失敗し、3 回目で通る
    const { calls } = mockApi({ streamStt: true, sttFinishErrorTimes: 2 })
    const { wrapper, router } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await flushPromises()

    // 成功と言わない（記録を終えない・最終まとめを起動しない・詳細へ進まない）
    expect(wrapper.get('[data-cr-finalize-error]').text()).toContain('尾部の文を取り切れませんでした。')
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(false)
    expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/'))).toBe(false)
    expect(router.currentRoute.value.path).toBe('/student/classroom/12/live')

    // 1 回目のやり直し（まだ失敗する）: 済んでいない段（書き起こしの収尾）だけをやり直す
    await wrapper.get('[data-cr-retry-finalize]').trigger('click')
    await flushPromises()
    await flushPromises()
    expect(wrapper.get('[data-cr-finalize-error]').text()).toContain('尾部の文を取り切れませんでした。')
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(false)
    expect(calls.filter((call) => call.url.includes('/stt/stream/finish'))).toHaveLength(2)
    // 送り終えた分塊は送り直さない（済んだ段はやり直さない）
    expect(calls.filter((call) => call.method === 'POST' && call.url.includes('/chunks'))).toHaveLength(1)

    // 2 回目のやり直し（今度は通る）→ ここで初めて成功と言う
    await wrapper.get('[data-cr-retry-finalize]').trigger('click')
    await flushPromises()
    await flushPromises()
    expect(calls.filter((call) => call.url.includes('/stt/stream/finish'))).toHaveLength(3)
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(true)
    expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/'))).toBe(true)
    expect(router.currentRoute.value.path).toBe('/student/classroom/12')
    expect(wrapper.find('[data-cr-finalize-error]').exists()).toBe(false)
  })

  /**
   * 収尾が失敗したら、**どの段で止まったか**を日本語で出す（利用者の指示 6 節）。
   *
   * <p>収尾は「録音の停止 → 音声の送信 → 書き起こしの確定 → 記録の終了 → 最終まとめ」と段が
   * 分かれている。理由（後端の日本語）だけを出しても、**どの段をやり直せばよいか**が分からない
   * （音を送り直すのか、書き起こしを締め直すのかで、利用者のすることは違う）。</p>
   */
  it('収尾が失敗したら、止まった段を日本語で出す（音声の送信／書き起こしの確定）', async () => {
    stubNoSocket()
    stubRecorderOnly()
    // 分塊（音声）が送れないまま終わろうとする＝**1 段目（音声の送信）**で止まる
    mockApi({ failOn: '/chunks' })
    const { wrapper, router } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('[data-cr-finalize-stage]').text()).toBe('【音声の送信】')
    expect(wrapper.get('[data-cr-finalize-error]').text()).toContain('送れなかった音声が 1 件あります')
    // 段の表示も「終了しました」ではない（成功と言わない）
    expect(wrapper.get('[data-cr-finish-phase]').text()).toContain('途中で止まりました')
    expect(router.currentRoute.value.path).toBe('/student/classroom/12/live')

    // 2 回目は**書き起こしの確定**で止まる（段が変わる）
    stubRecorderOnly()
    mockApi({ streamStt: true, sttFinishErrorTimes: 1 })
    const second = await mountClassroom(ClassroomLiveView, LIVE)
    await second.wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()
    await second.wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(second.wrapper.get('[data-cr-finalize-stage]').text()).toBe('【書き起こしの確定と記録の終了】')
    expect(second.wrapper.get('[data-cr-finalize-error]').text()).toContain('尾部の文を取り切れませんでした。')
  })

  /**
   * 収尾が失敗しても、**補書き起こしの手がかり（欠落した区間）を消さない**。
   *
   * <p>録音そのものには全部入っているので、欠落はあとから聞き直して書き起こせる。収尾に失敗した
   * ときに消してしまうと、その手がかりを失って**二度と追いかけられない**。</p>
   */
  it('収尾が失敗しても、音源ごとの欠落（補書き起こしの手がかり）を消さない', async () => {
    vi.useFakeTimers()
    try {
      const recorder = stubStreamRecorder()
      const { calls } = mockApi({
        streamStt: true,
        sttFinishErrorTimes: 1,
        detail: detail({ segments: [], notes: [] })
      })
      const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
      await wrapper.get('[data-cr-start-recording]').trigger('click')
      await flushPromises()

      // 上限（60 秒）を超える音を流し込む＝送れずに飛ばした区間ができる
      await recorder.feed('mic', 70)
      expect(wrapper.get('[data-cr-source-missing="mic"]').text()).toContain('飛ばした区間')
      const storageKey = 'classroom-stt-recovery:12:mic'
      expect(localStorage.getItem(storageKey) ?? '').toContain('fromSample')

      await wrapper.get('[data-cr-finish]').trigger('click')
      await flushPromises()
      await vi.advanceTimersByTimeAsync(1_000)
      await flushPromises()

      // 失敗として出す（成功と言わない）
      expect(wrapper.get('[data-cr-finalize-error]').text()).toContain('尾部の文を取り切れませんでした。')
      expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(false)
      // 欠落の案内も、保存した手がかりも消さない
      expect(wrapper.get('[data-cr-source-missing="mic"]').text()).toContain('飛ばした区間')
      expect(localStorage.getItem(storageKey) ?? '').toContain('fromSample')
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 収尾の段階の表示は**本当の段階**を出す（利用者の指示 7 節）。
   *
   * <p>「残りの音声を送っています」→「書き起こしの最終結果を待って、記録を終えています」と進み、
   * **済んでいないのに「終了しました」と言わない**（割合（％）は出さない＝測れないため）。</p>
   */
  it('段階の表示は本当の段階を出し、済むまで「終了しました」と言わない', async () => {
    stubNoSocket()
    stubRecorderOnly()
    const chunkGate: { release: () => void } = { release: () => undefined }
    const sttGate: { release: () => void } = { release: () => undefined }
    const chunks = new Promise<void>((resolve) => { chunkGate.release = resolve })
    const sttFinish = new Promise<void>((resolve) => { sttGate.release = resolve })
    const { calls } = mockApi({ streamStt: true, holdChunks: chunks, holdSttFinish: sttFinish })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()

    // 分塊（再生用の音声）の送り残しを待っている段階
    const phase = (): string => wrapper.get('[data-cr-finish-phase]').text()
    expect(phase()).toContain('残りの音声')
    expect(phase()).not.toContain('終了しました')

    chunkGate.release()
    await flushPromises()
    await flushPromises()

    // 書き起こしの最終結果を待っている段階（記録の終了もここに含む）
    expect(phase()).toContain('書き起こしの最終結果を待って')
    expect(phase()).not.toContain('終了しました')
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(false)

    sttGate.release()
    await flushPromises()
    await flushPromises()

    // 全部済んでから「終了しました」と言う
    // 段の表示は「済んだ」と言ってよい状態でだけ出す（`data-cr-status-title` と同じ段）
    expect(phase()).toContain('終了しました')
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(true)
  })

  /**
   * 収尾で確定した**尾部の文**は、記録の終了（＝最終まとめの対象になる保存）より**前**に
   * 書き起こしへ入れる（利用者の指示 7 節: 最終まとめに尾部の文を含める）。
   *
   * <p>先に記録を終えてしまうと、あとから確定した尾部の文は保存されず、最終まとめに入らない
   * （実測: マイクの確定文が停止の 14 秒後に届き、そのまま消えた）。順番で固定する。</p>
   */
  it('収尾で確定した尾部の文は、記録の終了（最終まとめ）より前に書き起こしへ入る', async () => {
    stubNoSocket()
    stubRecorderOnly()
    const { calls } = mockApi({
      streamStt: true,
      detail: detail({ segments: [], notes: [] }),
      sttFinish: {
        added: [{
          segmentId: 701, seq: 5, startOffsetSeconds: 30, endOffsetSeconds: 31,
          speaker: '講義', text: '尾部で確定した文です。', language: 'ja-JP', createdAt: null
        }]
      }
    })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await flushPromises()

    // 尾部で確定した文が画面の書き起こしに入る
    expect(wrapper.get('[data-cr-transcript]').text()).toContain('尾部で確定した文です。')
    // 記録の終了（最終まとめの対象）は、両方ではなく**尾部を受け取ったあと**
    const finishIndex = calls.findIndex((call) => call.url.includes('/stt/stream/finish'))
    const endIndex = calls.findIndex((call) => call.url.includes('/classroom/12/end'))
    const noteIndex = calls.findIndex((call) => call.url.includes('/api/admin/batch/classroom/notes/'))
    expect(finishIndex).toBeGreaterThanOrEqual(0)
    expect(endIndex).toBeGreaterThan(finishIndex)
    expect(noteIndex).toBeGreaterThan(endIndex)
  })

  /**
   * 二音源（マイク＋スピーカー）のときは、**両方**の音源の収尾（最後の確定文）を待ってから
   * 記録を終える（片方だけ待つと、待たなかった側の尾部が最終まとめに入らない）。
   */
  it('二音源では両方の音源の収尾を待ってから記録を終える', async () => {
    vi.useFakeTimers()
    try {
      const recorder = stubStreamRecorder({ dual: true })
      const { calls } = mockApi({ streamStt: true, detail: detail({ segments: [], notes: [] }) })
      const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
      await flushPromises()
      await recorder.feed('mic', 0.5)
      await recorder.feed('shared', 0.5)

      await wrapper.get('[data-cr-finish]').trigger('click')
      await flushPromises()
      await flushPromises()

      const finishes = calls.filter((call) => call.url.includes('/stt/stream/finish'))
      expect(finishes.map((call) => call.url.includes('source=mic'))).toEqual([true, false])
      expect(finishes.map((call) => call.url.includes('source=shared'))).toEqual([false, true])
      const lastFinish = calls.lastIndexOf(finishes[1] as Call)
      expect(calls.findIndex((call) => call.url.includes('/classroom/12/end'))).toBeGreaterThan(lastFinish)
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 後端が「終了を**完了できなかった**」と返したら（HTTP 200 でも `error` つき）、成功と言わない。
   *
   * <p>記録は終わりの状態になっていても、尾部の文が保存できていない＝最終まとめに尾部が入らない。
   * 画面は理由を出して**やり直し**を促し、最終まとめも起動しない。</p>
   */
  it('記録の終了を後端が完了できなかったら、成功と言わずやり直せる', async () => {
    stubNoSocket()
    stubRecorderOnly()
    // 1 回目の /end だけ「完了できなかった」理由つきで返る
    const { calls } = mockApi({ streamStt: true, endErrorTimes: 1 })
    const { wrapper, router } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await flushPromises()

    // 理由を出して止まる（詳細へ進まない・最終まとめを起動しない）
    expect(wrapper.get('[data-cr-finalize-error]').text()).toContain('尾部の文を取り切れませんでした。')
    expect(wrapper.get('[data-cr-finish-phase]').text()).toContain('途中で止まりました')
    expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/'))).toBe(false)
    expect(router.currentRoute.value.path).toBe('/student/classroom/12/live')

    // やり直し（今度は完了する）→ ここで初めて成功と言い、詳細へ進む
    await wrapper.get('[data-cr-retry-finalize]').trigger('click')
    await flushPromises()
    await flushPromises()
    expect(calls.filter((call) => call.url.includes('/classroom/12/end'))).toHaveLength(2)
    expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/'))).toBe(true)
    expect(router.currentRoute.value.path).toBe('/student/classroom/12')
  })

  it('【終了】は最後の分塊を送り終えてから送る（409 で音声を失わない）', async () => {
    // 解放用の関数はオブジェクト越しに持つ（TS の絞り込みで呼べなくなるのを避ける）
    const gate: { release: () => void } = { release: () => undefined }
    const chunkGate = new Promise<void>((resolve) => { gate.release = resolve })
    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      private listeners: Record<string, (() => void)[]> = {}
      constructor(public stream: unknown) { void this.stream; FakeMediaRecorder.instances.push(this) }
      addEventListener(type: string, handler: () => void): void {
        (this.listeners[type] ??= []).push(handler)
      }
      start(): void { this.state = 'recording' }
      stop(): void {
        this.state = 'inactive'
        // 仕様どおり「最後の分塊（dataavailable）→ stop」の順に来る
        this.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
        for (const handler of this.listeners.stop ?? []) handler()
      }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
    })

    const { calls } = mockApi({ holdChunks: chunkGate })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    // 【終了】を押す（分塊の応答はまだ返ってこない）
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()

    // 分塊の送信が終わるまで【終了】は送らない
    expect(calls.some((call) => call.url.includes('/end'))).toBe(false)
    expect(calls.some((call) => call.method === 'POST' && call.url.includes('/chunks'))).toBe(true)

    gate.release()
    await flushPromises()
    await flushPromises()

    // 送った順番: 分塊 → 終了
    const chunkIndex = calls.findIndex((call) => call.method === 'POST' && call.url.includes('/chunks'))
    const endIndex = calls.findIndex((call) => call.url.includes('/end'))
    expect(chunkIndex).toBeGreaterThanOrEqual(0)
    expect(endIndex).toBeGreaterThan(chunkIndex)
  })

  /**
   * 分塊には**実際の経過秒**を載せる。
   *
   * 以前はサーバーが「連番 × そのときの分塊の長さ設定」で計算していたため、録音中に設定を変えると
   * 時系列が壊れた（実測: 00:00 / 00:20 / 00:40 / 00:15 / 00:20 …）。画面が測った値を送る。
   */
  it('分塊に実際の経過秒（startSeconds / endSeconds）を載せて送る', async () => {
    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      constructor(public stream: unknown) { void this.stream; FakeMediaRecorder.instances.push(this) }
      start(): void { this.state = 'recording' }
      stop(): void { this.state = 'inactive' }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
    })

    const { calls } = mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    const recorder = FakeMediaRecorder.instances[0]
    recorder?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    const chunkCall = [...calls].reverse().find((call) => call.method === 'POST' && call.url.includes('/chunks'))
    expect(chunkCall?.url).toContain('startSeconds=0')
    // 終わりは実際の経過秒（テストでは開始直後なので 0.1 秒以上）
    const endSeconds = Number(/endSeconds=([\d.]+)/.exec(chunkCall?.url ?? '')?.[1] ?? '0')
    expect(endSeconds).toBeGreaterThanOrEqual(0.1)
  })

  /**
   * **話しながら文字が出る**（ストリーミング書き起こし）。
   *
   * 阿里巴巴のリアルタイム認識が使えるときは、500ms ごとに PCM を送り、
   * 返ってきた「途中の文」を 1 行で出し、「確定した文」を書き起こしへ足す。
   * 分塊ごとの STT はしない（同じ音声を二度書き起こさない）。
   */
  it('ストリーミング書き起こし: 途中の文を出し、確定した文を書き起こしへ足す', async () => {
    vi.useFakeTimers()
    // ここで見るのは**話しながら文字が出る**ことなので、常時接続は使えない環境にする（HTTP 送信）
    stubNoSocket()
    try {
      class FakeAudioWorkletNode {
        static instances: FakeAudioWorkletNode[] = []
        port = {
          onmessage: null as ((event: MessageEvent<Float32Array>) => void) | null,
          close: () => undefined
        }
        constructor(public context: unknown, public name: string) { FakeAudioWorkletNode.instances.push(this) }
        connect(): void { /* 何もしない */ }
        disconnect(): void { /* 何もしない */ }
      }
      class FakeAudioContext {
        sampleRate = 48000
        destination = {}
        audioWorklet = { addModule: vi.fn(async () => undefined) }
        createMediaStreamSource(): unknown { return { connect: () => undefined, disconnect: () => undefined } }
        createGain(): unknown { return { gain: { value: 1 }, connect: () => undefined } }
        close(): Promise<void> { return Promise.resolve() }
      }
      Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:x') })
      Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
      vi.stubGlobal('AudioContext', FakeAudioContext)
      vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
      class FakeMediaRecorder {
        static instances: FakeMediaRecorder[] = []
        static isTypeSupported(): boolean { return true }
        state = 'inactive'
        ondataavailable: ((event: { data: Blob }) => void) | null = null
        constructor(public stream: unknown) { void this.stream; FakeMediaRecorder.instances.push(this) }
        start(): void { this.state = 'recording' }
        stop(): void { this.state = 'inactive' }
      }
      vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
      Object.defineProperty(navigator, 'mediaDevices', {
        configurable: true,
        value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
      })

      const { calls } = mockApi({ streamStt: true })
      const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
      await wrapper.get('[data-cr-start-recording]').trigger('click')
      await flushPromises()

      // マイクの音が届く
      FakeAudioWorkletNode.instances[0]?.port.onmessage?.(
        { data: new Float32Array(24000).fill(0.4) } as MessageEvent<Float32Array>)
      await vi.advanceTimersByTimeAsync(2_000)
      await flushPromises()

      // ストリーミングの入口へ送っている
      expect(calls.some((call) => call.url.includes('/stt/stream'))).toBe(true)
      // 確定した文が書き起こしに入る
      expect(wrapper.get('[data-cr-transcript]').text()).toContain('比例のグラフを学びます。')
      // 途中の文は**音源ごと**に 1 行だけ出す（保存はしない）
      expect(wrapper.get('[data-cr-source-interim="mic"]').text()).toContain('ううう')
    } finally {
      vi.useRealTimers()
    }
  })

  /** オンライン授業（マイク＋PC の音）のテストで使う代役をそろえる。 */
  function stubDualSource(shared: MediaStream | (() => Promise<MediaStream>)): {
    recorderInstances: { start: () => void; state: string }[]
    getDisplayMedia: ReturnType<typeof vi.fn>
  } {
    class FakeAudioWorkletNode {
      port = { onmessage: null as unknown, close: () => undefined }
      constructor(public context: unknown, public name: string) { void context; void name }
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    class FakeAudioContext {
      sampleRate = 48000
      destination = {}
      audioWorklet = { addModule: vi.fn(async () => undefined) }
      createMediaStreamDestination(): { stream: object } { return { stream: { id: 'mixed' } } }
      createDynamicsCompressor(): object {
        return {
          threshold: { value: 0 }, knee: { value: 0 }, ratio: { value: 1 },
          attack: { value: 0 }, release: { value: 0 },
          connect: () => undefined, disconnect: () => undefined
        }
      }
      createGain(): object { return { gain: { value: 1 }, connect: () => undefined, disconnect: () => undefined } }
      createAnalyser(): object {
        return {
          fftSize: 1024, connect: () => undefined, disconnect: () => undefined,
          getFloatTimeDomainData: (buffer: Float32Array) => buffer.fill(0)
        }
      }
      createMediaStreamSource(): object { return { connect: () => undefined, disconnect: () => undefined } }
      close(): Promise<void> { return Promise.resolve() }
    }
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:x') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.stubGlobal('AudioContext', FakeAudioContext)
    vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      constructor(public stream: unknown) { void this.stream; FakeMediaRecorder.instances.push(this) }
      start(): void { this.state = 'recording' }
      stop(): void { this.state = 'inactive' }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    const getDisplayMedia = vi.fn(async () => (typeof shared === 'function' ? shared() : shared))
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn(async () => ({
          getAudioTracks: () => [{ kind: 'audio' }], getTracks: () => [{ stop: () => undefined }]
        })),
        getDisplayMedia
      }
    })
    return { recorderInstances: FakeMediaRecorder.instances, getDisplayMedia }
  }

  function sharedStream(audio: boolean): MediaStream & { stopTrack: () => void } {
    const listeners: (() => void)[] = []
    const track = {
      kind: 'audio',
      // 画面は「共有が終わった」をこの合図で知る（録音を止めずに、その音源だけ収尾する）
      addEventListener: (type: string, handler: () => void) => {
        if (type === 'ended') listeners.push(handler)
      },
      stop: () => undefined
    }
    return {
      getAudioTracks: () => (audio ? [track] : []),
      getVideoTracks: () => [{ kind: 'video', stop: () => undefined }],
      getTracks: () => [track],
      // テストから「共有を止めた」ことにする（`ended` を配る）
      stopTrack: () => { for (const handler of [...listeners]) handler() }
    } as unknown as MediaStream & { stopTrack: () => void }
  }

  /**
   * オンライン授業では、共有した**PC の音**をマイクと混ぜて録る。
   * 【新しい授業】で選んだ音源は query で渡り、**入ると自動で録音を始める**（利用者の指示）。
   * 共有の選択が出るのはこのときだけ（取れなければ録音を始めない＝黙ってマイクだけにしない）。
   * 統合前の値（mic-tab / mic-screen）でも同じ動きになる（覚えた選択・古い URL を壊さない）。
   */
  it('オンライン授業: 選んだ音源で共有を選び、マイクと混ぜて自動で録音を始める', async () => {
    const stub = stubDualSource(sharedStream(true))
    const { calls } = mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    expect(stub.getDisplayMedia).toHaveBeenCalledTimes(1)
    // 共有の音を混ぜるので、レベル表示（マイク／網課の音）が出る
    const levels = wrapper.get('[data-cr-audio-levels]').text()
    expect(levels).toContain('マイク')
    expect(levels).toContain('網課の音')
    // 選んだ音源のまま、録音が始まっている（MediaRecorder が動いている）
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)
    expect(callTo(calls, '/api/user/classroom/12/start', 'POST')).toBeDefined()
  })

  it('オンライン授業: 共有に音が入っていなければ、録音を始めずにやり直しを案内する', async () => {
    const stub = stubDualSource(sharedStream(false))
    mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    expect(stub.getDisplayMedia).toHaveBeenCalledTimes(1)
    // 「タブの音声を共有」のチェックを促す（黙ってマイクだけにしない）
    expect(wrapper.get('[data-cr-recorder-notice]').text()).toContain('共有の音が入っていません')
    expect(wrapper.get('[data-cr-recorder-notice]').text()).toContain('タブの音声')
    // 録音は始まっていない
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(false)
  })

  it('オンライン授業: 共有が許可されなければ、録音を始めずに案内する', async () => {
    const stub = stubDualSource(() => Promise.reject(new Error('denied')))
    mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    expect(stub.getDisplayMedia).toHaveBeenCalledTimes(1)
    expect(wrapper.get('[data-cr-recorder-notice]').text()).toContain('共有が許可されませんでした')
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(false)
  })

  it('マイクのみ: 共有の選択は出さずに、自動で録音を始める', async () => {
    const stub = stubDualSource(sharedStream(true))
    mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic'))
    await flushPromises()

    expect(stub.getDisplayMedia).not.toHaveBeenCalled()
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)
  })

  /**
   * 音源の選択は【新しい授業】で行う（利用者の指示で録音中の画面から**移した**）。
   * 録音中は選び直せないので、選択の欄そのものを置かない（試し録りも廃止した）。
   * 二音源のときは、音量表示（マイク／網課の音）で両方入っているかを確かめられる。
   */
  it('録音中の画面には音源の選択も試し録りも無い（【新しい授業】で選ぶ）', async () => {
    mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    expect(wrapper.find('[data-cr-audio-mode]').exists()).toBe(false)
    expect(wrapper.find('[data-cr-source-test]').exists()).toBe(false)
    expect(wrapper.find('[data-cr-test-source]').exists()).toBe(false)

    // 二音源で始めたときは、音量表示で両方の音を確かめられる（選び直しのボタンも残す）
    const stub = stubDualSource(sharedStream(true))
    mockApi()
    const tab = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    expect(stub.getDisplayMedia).toHaveBeenCalledTimes(1)
    expect(tab.wrapper.get('[data-cr-audio-levels]').text()).toContain('網課の音')
    expect(tab.wrapper.find('[data-cr-test-source]').exists()).toBe(false)
  })

  it('音源の指定が壊れていても落ちない（マイクだけから始める）', async () => {
    const stub = stubDualSource(sharedStream(true))
    mockApi()
    const { wrapper } = await mountClassroom(
      ClassroomLiveView, `${LIVE}&audioMode=unknown-value&autostart=1`
    )
    await flushPromises()

    expect(stub.getDisplayMedia).not.toHaveBeenCalled()
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)
  })

  it('統合前の音源（mic-tab / mic-screen）でも二音源として動く', async () => {
    const stub = stubDualSource(sharedStream(true))
    mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, `${LIVE}&audioMode=mic-tab&autostart=1`)
    await flushPromises()

    // 共有を取って混ぜる（＝「マイク＋スピーカー」として扱われている）
    expect(stub.getDisplayMedia).toHaveBeenCalledTimes(1)
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)
    expect(wrapper.get('[data-cr-audio-levels]').text()).toContain('網課の音')
  })

  /**
   * ブラウザ認識（Web Speech API）は**マイクからしか音を取れない**ので、二音源では
   * スピーカーの音（先生の声）が文字にならない。黙って半分の書き起こしを見せないよう、
   * 画面ではっきり知らせる（録音した音声には両方入っている）。
   */
  it('二音源＋ブラウザ認識: マイクだけだと知らせ、転写にも印を付ける', async () => {
    const stub = stubDualSource(sharedStream(true))
    mockApi({ sttMode: 'BROWSER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    expect(stub.getDisplayMedia).toHaveBeenCalledTimes(1)
    const notice = wrapper.get('[data-cr-mic-only-stt-notice]').text()
    expect(notice).toContain('マイクの音だけ')
    expect(notice).toContain('サーバー認識')
    expect(wrapper.get('[data-cr-live-stt-mode]').text()).toContain('マイクのみ')
    expect(wrapper.get('[data-cr-transcript-mic-only]').text()).toContain('マイクのみ')
  })

  /**
   * 利用者の指示: **「共有を選び直す」は消した**。
   *
   * <p>前の実装のそれは、混ぜる遠隔音だけを入れ替えて**共有の音の書き起こしは入れ替えていなかった**
   * （食い違いの元）。入口・案内文とも残さない（同じ役割のボタンを別の名前で足さない）。</p>
   */
  it('録音中の画面に「共有を選び直す」の入口も、その案内文も無い', async () => {
    stubDualSource(sharedStream(true))
    mockApi({ streamStt: true })
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    // 画面（DOM）に入口が無い
    expect(wrapper.find('[data-cr-reshare]').exists()).toBe(false)
    expect(wrapper.find('[data-cr-audio-source]').exists()).toBe(true)
    // 案内文（同じことを促す文言）も無い
    const html = wrapper.html()
    expect(html).not.toContain('選び直す')
    expect(html).not.toContain('選び直して')
    expect(html).not.toContain('共有を選び直す')
  })

  /**
   * 利用者の指示: **共有の音が止まったら、先生の音源は中断したと明確に知らせる**。
   *
   * <p>見せかけを続けない: 共有の音源だけを収尾（`/stt/stream/finish?source=shared`）し、
   * **マイクは録り続ける**（学生の声は残る）。役割のラベル（共有＝先生／マイク＝学生）は変えない。</p>
   */
  it('共有の音が止まったら、先生の音源の中断を知らせて、その音源だけ収尾する（マイクは続く）', async () => {
    const shared = sharedStream(true)
    stubDualSource(shared)
    const { calls } = mockApi({ streamStt: true })
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('録音中')
    // 二音源なので、役割は「マイク＝学生／共有＝先生」（この対応は止まっても変わらない）
    expect(wrapper.get('[data-cr-speaker-note]').text()).toContain('「先生」')

    // 共有の音が終わる（「共有を停止」を押した・共有したタブを閉じた）
    shared.stopTrack()
    await flushPromises()

    // ① 明確に知らせる（マイクは続いていることも書く）
    const notice = wrapper.get('[data-cr-recorder-notice]').text()
    expect(notice).toContain('共有')
    expect(notice).toContain('マイク')
    // ② 共有の音源は収尾する（尾部の文を取り切る）
    expect(calls.some((call) => call.url.includes('/stt/stream/finish')
      && call.url.includes('source=shared'))).toBe(true)
    // ③ マイクは録り続ける（収尾しない・録音中のまま）
    expect(calls.some((call) => call.url.includes('/stt/stream/finish')
      && call.url.includes('source=mic'))).toBe(false)
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('録音中')
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)
    // ④ 役割は変わらない（止まった音源を「講義」へ落とさない）
    expect(wrapper.get('[data-cr-speaker-note]').text()).toContain('「学生」')
    expect(notice).not.toContain('選び直')
  })

  it('二音源＋サーバー認識: その案内は出さない（両方の音を書き起こせる）', async () => {
    stubDualSource(sharedStream(true))
    mockApi({ sttMode: 'SERVER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    expect(wrapper.find('[data-cr-mic-only-stt-notice]').exists()).toBe(false)
    expect(wrapper.find('[data-cr-transcript-mic-only]').exists()).toBe(false)
    expect(wrapper.get('[data-cr-live-stt-mode]').text()).toContain('サーバー認識')
  })

  it('マイクのみ＋ブラウザ認識: その案内は出さない（マイクの音しか無い）', async () => {
    mockApi({ sttMode: 'BROWSER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    expect(wrapper.find('[data-cr-mic-only-stt-notice]').exists()).toBe(false)
    expect(wrapper.find('[data-cr-transcript-mic-only]').exists()).toBe(false)
  })

  /**
   * 送信に失敗した分塊は**捨てない**。
   *
   * ネットワークが波打つと分塊のアップロードが落ちる。そのまま終わると授業の音声が
   * 黙って欠けるので、待ち行列に入れて【送り直す】で送れるようにする（4xx は内容の問題なので除く）。
   */
  /**
   * 書き起こし用の音声（PCM）を用意できないときは、**黙って続けない**。
   * サーバー認識は PCM が無いと分塊のファイルで認識するが、2 つ目以降の分塊は
   * コンテナのヘッダが無く認識できない（＝最初の数秒しか文字にならない）。
   */
  it('書き起こし用の音声を準備できないときは、その旨を案内する', async () => {
    class FakeAudioWorkletNode {
      port = { onmessage: null as unknown, close: () => undefined }
      constructor(public context: unknown, public name: string) { void context; void name }
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    class FakeAudioContext {
      sampleRate = 48000
      destination = {}
      audioWorklet = { addModule: vi.fn(async () => { throw new Error('worklet が読めない') }) }
      createMediaStreamDestination(): { stream: object } { return { stream: { id: 'mixed' } } }
      createDynamicsCompressor(): FakeNode { return new FakeNode(this) }
      createGain(): FakeNode { return new FakeNode(this) }
      createAnalyser(): FakeNode { return new FakeNode(this) }
      createMediaStreamSource(): FakeNode { return new FakeNode(this) }
      close(): Promise<void> { return Promise.resolve() }
    }
    class FakeNode {
      constructor(public context: unknown) { /* 本物と同じく自分のコンテキストを持つ */ }
      gain = { value: 1 }
      threshold = { value: 0 }
      knee = { value: 0 }
      ratio = { value: 1 }
      attack = { value: 0 }
      release = { value: 0 }
      fftSize = 1024
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
      getFloatTimeDomainData(buffer: Float32Array): void { buffer.fill(0) }
    }
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:x') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.stubGlobal('AudioContext', FakeAudioContext)
    vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
    class FakeMediaRecorder {
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      constructor(public stream: unknown) { void this.stream }
      start(): void { this.state = 'recording' }
      stop(): void { this.state = 'inactive' }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    const shared = {
      getAudioTracks: () => [{ kind: 'audio', addEventListener: () => undefined }],
      getVideoTracks: () => [],
      getTracks: () => [{ stop: () => undefined }]
    } as unknown as MediaStream
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn(async () => ({
          getAudioTracks: () => [{ kind: 'audio', readyState: 'live' }],
          getTracks: () => [{ stop: () => undefined }]
        })),
        getDisplayMedia: vi.fn(async () => shared)
      }
    })
    mockApi({ sttMode: 'SERVER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    expect(wrapper.get('[data-cr-pcm-notice]').text()).toContain('書き起こし用の音声を準備できませんでした')
    // 録音そのものは続ける（音は残す）
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)
  })

  /**
   * 送れなかった音声は**捨てない**（同じ音を送り直す）。音源ごとに案内を出し、録音は続ける。
   *
   * <p>保持と再送の細かい動きは `classroom-source-stream.spec.ts` が見張る。ここでは
   * 画面が「音源ごとの案内」を出し、録音が止まらないことを確かめる。</p>
   */
  it('書き起こしへ送れないときは、音源ごとに案内を出して録音を続ける', async () => {
    vi.useFakeTimers()
    try {
      class FakeAudioWorkletNode {
        static instances: FakeAudioWorkletNode[] = []
        port = {
          onmessage: null as ((event: MessageEvent<Float32Array>) => void) | null,
          close: () => undefined
        }
        constructor(public context: unknown, public name: string) { FakeAudioWorkletNode.instances.push(this) }
        connect(): void { /* 何もしない */ }
        disconnect(): void { /* 何もしない */ }
      }
      class FakeAudioContext {
        sampleRate = 48000
        destination = {}
        audioWorklet = { addModule: vi.fn(async () => undefined) }
        createMediaStreamSource(): unknown { return { connect: () => undefined, disconnect: () => undefined } }
        createGain(): unknown { return { gain: { value: 1 }, connect: () => undefined } }
        close(): Promise<void> { return Promise.resolve() }
      }
      Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:x') })
      Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
      vi.stubGlobal('AudioContext', FakeAudioContext)
      vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
      class FakeMediaRecorder {
        static isTypeSupported(): boolean { return true }
        state = 'inactive'
        ondataavailable: ((event: { data: Blob }) => void) | null = null
        constructor(public stream: unknown) { void this.stream }
        start(): void { this.state = 'recording' }
        stop(): void { this.state = 'inactive' }
      }
      vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
      Object.defineProperty(navigator, 'mediaDevices', {
        configurable: true,
        value: { getUserMedia: vi.fn(async () => ({ getTracks: () => [] })) }
      })

      // 書き起こしへの送信だけが失敗する
      mockApi({ streamStt: true, failOn: '/stt/stream' })
      // 常時接続は使わせない（断線の案内と混ざらず、HTTP 送信の失敗の案内を確かめられる）
      stubNoSocket()
      const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
      await wrapper.get('[data-cr-start-recording]').trigger('click')
      await flushPromises()

      FakeAudioWorkletNode.instances[0]?.port.onmessage?.(
        { data: new Float32Array(24000).fill(0.4) } as MessageEvent<Float32Array>)
      await vi.advanceTimersByTimeAsync(2_000)
      await flushPromises()

      const notice = wrapper.get('[data-cr-source-notice="mic"]').text()
      expect(notice).toContain('送れませんでした')
      expect(notice).toContain('送り直します')
      // 録音そのものは続いている（音は録れて、あとで送り直せる）
      expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 送信に失敗した分塊は**捨てない**。
   *
   * ネットワークが波打つと分塊のアップロードが落ちる。そのまま終わると授業の音声が
   * 黙って欠けるので、待ち行列に入れて【送り直す】で送れるようにする（4xx は内容の問題なので除く）。
   */
  /**
   * 書き起こし用の音声（PCM）を用意できないときは、**黙って続けない**。
   * サーバー認識は PCM が無いと分塊のファイルで認識するが、2 つ目以降の分塊は
   * コンテナのヘッダが無く認識できない（＝最初の数秒しか文字にならない）。
   */
  it('書き起こし用の音声を準備できないときは、その旨を案内する', async () => {
    class FakeAudioWorkletNode {
      port = { onmessage: null as unknown, close: () => undefined }
      constructor(public context: unknown, public name: string) { void context; void name }
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
    }
    class FakeAudioContext {
      sampleRate = 48000
      destination = {}
      audioWorklet = { addModule: vi.fn(async () => { throw new Error('worklet が読めない') }) }
      createMediaStreamDestination(): { stream: object } { return { stream: { id: 'mixed' } } }
      createDynamicsCompressor(): FakeNode { return new FakeNode(this) }
      createGain(): FakeNode { return new FakeNode(this) }
      createAnalyser(): FakeNode { return new FakeNode(this) }
      createMediaStreamSource(): FakeNode { return new FakeNode(this) }
      close(): Promise<void> { return Promise.resolve() }
    }
    class FakeNode {
      constructor(public context: unknown) { /* 本物と同じく自分のコンテキストを持つ */ }
      gain = { value: 1 }
      threshold = { value: 0 }
      knee = { value: 0 }
      ratio = { value: 1 }
      attack = { value: 0 }
      release = { value: 0 }
      fftSize = 1024
      connect(): void { /* 何もしない */ }
      disconnect(): void { /* 何もしない */ }
      getFloatTimeDomainData(buffer: Float32Array): void { buffer.fill(0) }
    }
    Object.defineProperty(URL, 'createObjectURL', { configurable: true, value: vi.fn(() => 'blob:x') })
    Object.defineProperty(URL, 'revokeObjectURL', { configurable: true, value: vi.fn() })
    vi.stubGlobal('AudioContext', FakeAudioContext)
    vi.stubGlobal('AudioWorkletNode', FakeAudioWorkletNode)
    class FakeMediaRecorder {
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      constructor(public stream: unknown) { void this.stream }
      start(): void { this.state = 'recording' }
      stop(): void { this.state = 'inactive' }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    const shared = {
      getAudioTracks: () => [{ kind: 'audio', addEventListener: () => undefined }],
      getVideoTracks: () => [],
      getTracks: () => [{ stop: () => undefined }]
    } as unknown as MediaStream
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: {
        getUserMedia: vi.fn(async () => ({
          getAudioTracks: () => [{ kind: 'audio', readyState: 'live' }],
          getTracks: () => [{ stop: () => undefined }]
        })),
        getDisplayMedia: vi.fn(async () => shared)
      }
    })
    mockApi({ sttMode: 'SERVER' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, liveFromNewLesson('mic-pc'))
    await flushPromises()

    expect(wrapper.get('[data-cr-pcm-notice]').text()).toContain('書き起こし用の音声を準備できませんでした')
    // 録音そのものは続ける（音は残す）
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)
  })

  it('送れなかった分塊は待ち行列に入れ、送り直せる（黙って捨てない）', async () => {
    class FakeMediaRecorder {
      static instances: FakeMediaRecorder[] = []
      static isTypeSupported(): boolean { return true }
      state = 'inactive'
      ondataavailable: ((event: { data: Blob }) => void) | null = null
      constructor(public stream: unknown) { void this.stream; FakeMediaRecorder.instances.push(this) }
      start(): void { this.state = 'recording' }
      stop(): void { this.state = 'inactive' }
    }
    vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
    Object.defineProperty(navigator, 'mediaDevices', {
      configurable: true,
      value: { getUserMedia: vi.fn(async () => ({ getAudioTracks: () => [], getTracks: () => [] })) }
    })

    let chunkAttempts = 0
    mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)
    vi.mocked(fetch).mockImplementation(async (url: RequestInfo | URL, init?: RequestInit) => {
      const target = String(url)
      const ok = (data: unknown): Response => new Response(
        JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
      if (target.includes('/chunks') && (init?.method ?? 'GET').toUpperCase() === 'GET') {
        return ok({ items: [], chunkCount: 0, maxSeq: 0, nextSeq: 1, totalBytes: 0, recordedSeconds: null })
      }
      if (target.includes('/chunks')) {
        chunkAttempts += 1
        // 1 回目だけネットワークが落ちる
        if (chunkAttempts === 1) throw new TypeError('Failed to fetch')
        return ok({
          recordId: 12, seq: 5, nextSeq: 6, nextChunkSeq: 6, appendedSegments: [], pendingNoteId: null,
          triggered: false, status: 'RECORDING', runPath: null
        })
      }
      if (target.includes('/options')) {
        return ok({
          enabled: true, chunkSeconds: 20, maxRecordingMinutes: 120, retentionDays: 30,
          dailyLimit: 0, usedToday: 0, notice: '', sttMode: 'SERVER', streamStt: false,
          sttLanguageCodes: {}, noteEnabled: false
        })
      }
      if (target.includes('/start')) {
        return ok({ recordId: 12, recordNo: 'CR1', status: 'RECORDING', statusLabel: '録音中', version: 2 })
      }
      return ok(detail({ segments: [], notes: [] }))
    })

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    const recorder = FakeMediaRecorder.instances[0]
    recorder?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    // 失敗した分塊は待ち行列に入り、画面に件数が出る
    expect(wrapper.find('[data-cr-pending-uploads]').exists()).toBe(true)
    expect(wrapper.get('[data-cr-pending-uploads]').text()).toContain('保存')

    // 【送り直す】で送れる
    await wrapper.get('[data-cr-retry-uploads]').trigger('click')
    await flushPromises()
    await flushPromises()

    expect(chunkAttempts).toBeGreaterThanOrEqual(2)
    // 送れたら案内は消える
    expect(wrapper.find('[data-cr-pending-uploads]').exists()).toBe(false)
  })

  it('録音中は書き起こしを毎周期、ノートの状態を定期的に取りに行く', async () => {
    vi.useFakeTimers()
    const { calls } = mockApi()
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()
    const segmentsBefore = calls.filter((call) => call.url.includes('/segments')).length
    const detailsBefore = calls.filter((call) => /\/api\/user\/classroom\/12$/.test(call.url)).length

    // 2 秒 × 5 周期（書き起こしは毎周期、詳細は 5 周期ごと）
    await vi.advanceTimersByTimeAsync(10_000)
    await flushPromises()

    expect(calls.filter((call) => call.url.includes('/api/user/classroom/12/segments')).length)
      .toBeGreaterThanOrEqual(segmentsBefore + 3)
    expect(calls.filter((call) => /\/api\/user\/classroom\/12$/.test(call.url)).length)
      .toBe(detailsBefore + 1)
  })

  it('録音を開始できないとき（サーバーの理由）は画面に出して録音中にしない', async () => {
    mockApi({ failOn: '/start', failureMessage: '本日の録音は上限（1 回）に達しました。' })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-cr-live-error]').text()).toContain('本日の録音は上限（1 回）に達しました。')
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('停止中')
    expect(wrapper.find('[data-cr-start-recording]').exists()).toBe(true)
  })

  it('詳細から書き起こしと AI ノート（フェーズ）を読んで表示する', async () => {
    mockApi({
      detail: detail({
        status: 'RECORDING',
        statusLabel: '録音中',
        segments: [
          { segmentId: 1, seq: 1, startOffsetSeconds: 20, endOffsetSeconds: 40, speaker: null, text: 'それでは始めます。', language: 'ja', createdAt: null },
          { segmentId: 2, seq: 2, startOffsetSeconds: 45, endOffsetSeconds: 70, speaker: '先生', text: '比例のグラフを見てください。', language: 'ja', createdAt: null }
        ],
        notes: [
          {
            noteId: 90, kind: 'PHASE', phaseNo: 1, startSeq: 1, endSeq: 2,
            status: 'READY', statusLabel: 'できました',
            noteJson: JSON.stringify({ テーマ: '比例のグラフ', 宿題: '練習問題 1〜5' }),
            errorCode: null, errorMessage: null, createdAt: null, updatedAt: null
          },
          {
            noteId: 91, kind: 'FINAL', phaseNo: null, startSeq: null, endSeq: null,
            status: 'PENDING', statusLabel: '分析待ち',
            noteJson: null, errorCode: null, errorMessage: null, createdAt: null, updatedAt: null
          }
        ]
      })
    })
    const { wrapper } = await mountClassroom(ClassroomLiveView, LIVE)

    const lines = wrapper.findAll('[data-cr-transcript-line]')
    expect(lines).toHaveLength(2)
    expect(lines[0]?.text()).toContain('[00:20]')
    expect(lines[0]?.text()).toContain('講義：')
    expect(lines[1]?.text()).toContain('先生：')

    const notes = wrapper.findAll('[data-cr-note-item]')
    expect(notes).toHaveLength(2)
    expect(notes[0]?.text()).toContain('最終まとめ')
    expect(notes[0]?.text()).toContain('分析待ち')
    expect(notes[1]?.text()).toContain('フェーズ 1')
    expect(notes[1]?.text()).toContain('練習問題 1〜5')
  })

  it('【授業を終了】で終了 API と最終まとめ（batC62）を呼び、詳細画面へ進む', async () => {
    const { calls } = mockApi()
    const { wrapper, router } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()

    expect(callTo(calls, '/api/user/classroom/12/end', 'POST')).toBeDefined()
    // 返った runPath（admin-api の薄い入口）を 1 回だけ呼ぶ
    const run = callTo(calls, '/api/admin/batch/classroom/notes/91/run', 'POST')
    expect(run?.body).toEqual({ operator: 'classroom-live-view' })

    expect(router.currentRoute.value.path).toBe('/student/classroom/12')
    expect(router.currentRoute.value.query).toMatchObject({ name: '数学 二次関数' })
  })

  it('【授業を終了】が 409 でも、すでに終わっていれば詳細へ進む（エラーにしない）', async () => {
    mockApi({
      failOn: '/end',
      failureMessage: 'この録音は録音中ではありません（停止（まとめ作成待ち））。',
      detail: detail({ status: 'STOPPED', statusLabel: '停止（まとめ作成待ち）', segments: [] })
    })
    const { wrapper, router } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()

    // 理由を出さず、詳細画面へ進む
    expect(wrapper.find('[data-cr-live-error]').exists()).toBe(false)
    expect(router.currentRoute.value.path).toBe('/student/classroom/12')
  })

  /**
   * 本当に終了できなかったとき（記録はまだ録音中）は、詳細へ進まずに理由を出す。
   * すでに終わっているときは進む（上のテスト）＝**状態で分ける**。
   */
  it('終了できないときは詳細へ進まず、理由を日本語で出す', async () => {
    mockApi({
      failOn: '/end',
      failureMessage: 'サーバーでエラーが発生しました。',
      // まだ録音中なので「終わっている」とは言えない
      detail: detail({ status: 'RECORDING', statusLabel: '録音中', startTime: null, segments: [] })
    })
    const { wrapper, router } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-cr-finalize-error]').text()).toContain('サーバーでエラーが発生しました。')
    expect(router.currentRoute.value.path).toBe('/student/classroom/12/live')
  })

  it('書き起こしが無いまま終了したときは runPath を呼ばず、サーバーの案内を出す', async () => {
    const { calls } = mockApi({
      end: {
        recordId: 12, status: 'STOPPED', statusLabel: '停止（まとめ作成待ち）',
        finalNoteId: null, runPath: null,
        notice: '書き起こしが無かったため、最終まとめは作成しませんでした。'
      }
    })
    const { wrapper, router } = await mountClassroom(ClassroomLiveView, LIVE)

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()

    // 最終まとめ（batC62）の入口は呼ばない（finalNoteId が null）
    expect(callTo(calls, '/api/admin/batch/classroom/notes/', 'POST')).toBeUndefined()
    expect(wrapper.get('[data-cr-finish-notice]').text())
      .toContain('書き起こしが無かったため、最終まとめは作成しませんでした。')
    // それでも詳細へは進む（終了そのものは成功している）
    expect(router.currentRoute.value.path).toBe('/student/classroom/12')
  })
})

describe('授業録音：授業詳細', () => {
  const DETAIL = '/student/classroom/12?name=数学&subject=数学&languageMode=ja&preset=2'

  it('サーバーの最終整理結果・転写全文・元音声を出す', async () => {
    const { calls } = mockApi()
    const { wrapper } = await mountClassroom(ClassroomDetailView, DETAIL)

    expect(callTo(calls, '/api/user/classroom/12', 'GET')).toBeDefined()
    expect(wrapper.get('.card__title').text()).toContain('数学 二次関数の復習')

    // 最終整理結果（テーマ／学習内容／先生の重点／宿題）は AI の JSON から出す
    expect(wrapper.get('[data-cr-detail-section="theme"]').text()).toContain('比例のグラフ')
    expect(wrapper.get('[data-cr-detail-section="content"]').text()).toContain('比例定数と直線の傾き')
    expect(wrapper.get('[data-cr-detail-section="focus"]').text()).toContain('右上がり')
    expect(wrapper.get('[data-cr-detail-section="homework"]').text()).toContain('練習問題 1〜5')

    // 転写全文（時刻・話者つき）
    expect(wrapper.get('[data-cr-transcript-full]').text()).toContain('転写全文')
    const lines = wrapper.findAll('[data-cr-transcript-line]')
    expect(lines).toHaveLength(1)
    expect(lines[0]?.text()).toContain('[00:20]')
    expect(lines[0]?.text()).toContain('それでは今日の授業を始めます。')

    // 元音声は <audio> が API の URL をそのまま読む（サーバーは Range/206 対応）
    expect(wrapper.get('[data-cr-homework]').text()).toContain('練習問題 1〜5')
    const audio = wrapper.get('[data-cr-audio]')
    expect(audio.text()).toContain('録音の再生')
    expect(wrapper.get('[data-cr-audio-element]').attributes('src')).toBe('/api/user/classroom/12/audio?v=2')
  })

  /**
   * 利用者の指示 7: **詳細画面の転写全文**も、録音中の画面と**同じ規則**で出す。
   * 並びは実際の発言開始時刻（音声クロックの先頭オフセット）、話者は**その記録の音源の設定**で
   * 決める（その行の話者は入れ替えない）。
   */
  it('転写全文は発言の開始時刻で並べ、話者は記録の設定で決める', async () => {
    mockApi({
      detail: detail({
        segments: [
          // 到着順（連番）は 3 → 1。発言の開始時刻は 1 秒 → 0.2 秒
          {
            segmentId: 3, seq: 3, startOffsetSeconds: 1, endOffsetSeconds: 2,
            speaker: '先生', text: '先生の説明です。', language: 'ja', createdAt: null
          },
          {
            segmentId: 1, seq: 1, startOffsetSeconds: 0.2, endOffsetSeconds: 1,
            // サーバーは「共有が届いていない」ので講義と返したが、二音源の設定ではマイクの行＝学生
            speaker: '講義', text: '学生の質問です。', language: 'ja', createdAt: null
          }
        ], notes: []
      })
    })
    // その記録の設定（録音中の画面が録音を始めたときに覚えた値）
    localStorage.setItem('study21.classroom.audioMode.record.12', 'mic-pc')
    const { wrapper } = await mountClassroom(ClassroomDetailView, DETAIL)
    await flushPromises()

    const rows = wrapper.findAll('[data-cr-transcript-line]')
    expect(rows.map((row) => row.attributes('data-cr-transcript-line'))).toEqual(['1', '3'])
    expect(rows[0]?.attributes('data-cr-transcript-speaker')).toBe('学生')
    expect(rows[0]?.text()).toContain('学生の質問です。')
    expect(rows[1]?.attributes('data-cr-transcript-speaker')).toBe('先生')
    expect(rows[1]?.text()).toContain('先生の説明です。')
  })

  /**
   * 利用者の指示 7: 詳細画面は**開いている URL の query では話者を決めない**。
   *
   * <p>query は**録音中の画面のもの**（【新しい授業】で選んだ値）で、一覧・履歴・ブックマークから
   * 開いた URL には残っていないか、**別の記録の値**が残っていることがある。それで話者を決めると
   * 「開き方で話者が変わる」ことになる。記録の設定が分からないときは**サーバーが記録した話者**を
   * そのまま出す（分からないことを勝手に決めない）。</p>
   */
  it('詳細画面は URL の音源では話者を決めない（記録の設定が無ければサーバーの値のまま）', async () => {
    mockApi({
      detail: detail({
        segments: [
          {
            segmentId: 3, seq: 3, startOffsetSeconds: 1, endOffsetSeconds: 2,
            speaker: '先生', text: '先生の説明です。', language: 'ja', createdAt: null
          },
          {
            segmentId: 1, seq: 1, startOffsetSeconds: 0.2, endOffsetSeconds: 1,
            speaker: '講義', text: '学生の質問です。', language: 'ja', createdAt: null
          }
        ], notes: []
      })
    })
    // 別の記録のときに選んだ音源が query に残っていても、この記録の話者は変えない
    const { wrapper } = await mountClassroom(ClassroomDetailView, `${DETAIL}&audioMode=mic-pc`)
    await flushPromises()

    const rows = wrapper.findAll('[data-cr-transcript-line]')
    expect(rows.map((row) => row.attributes('data-cr-transcript-speaker'))).toEqual(['講義', '先生'])
  })

  it('音声が無い記録ではプレーヤーの占位（押せない）を出す', async () => {
    mockApi({ detail: detail({ hasAudio: false, audioMime: null }) })
    const { wrapper } = await mountClassroom(ClassroomDetailView, DETAIL)

    expect(wrapper.find('[data-cr-audio-element]').exists()).toBe(false)
    const play = wrapper.get('[data-cr-audio-player] button')
    expect(play.attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-cr-audio]').text()).toContain('この授業の音声はありません')
  })

  it('音声の実体が無くて配信 API が 404 でも、画面は占位に切り替える（壊さない）', async () => {
    mockApi({ detail: detail({ hasAudio: true }) })
    const { wrapper } = await mountClassroom(ClassroomDetailView, DETAIL)

    // 記録は「音声あり」と言っているので、まずはプレーヤーを出す
    expect(wrapper.find('[data-cr-audio-element]').exists()).toBe(true)

    // 保存期間を過ぎた等で配信 API が 404 を返したとき（<audio> の error）
    await wrapper.get('[data-cr-audio-element]').trigger('error')
    await flushPromises()

    expect(wrapper.find('[data-cr-audio-element]').exists()).toBe(false)
    expect(wrapper.get('[data-cr-audio-player] button').attributes('disabled')).toBeDefined()
    expect(wrapper.get('[data-cr-audio-note]').text()).toContain('録音の音声を読み込めませんでした')
  })

  it('旧データの言語モード（auto）でも落ちずに表示する', async () => {
    // 自動判別（auto）は廃止したが、旧レコードには残っている（サーバーは ja-JP として扱う）
    mockApi({ detail: detail({ languageMode: 'auto' }) })
    const { wrapper } = await mountClassroom(ClassroomDetailView, DETAIL)

    expect(wrapper.get('[data-cr-detail]').text()).toContain('自動判別（廃止）')
    expect(wrapper.get('.card__title').text()).toContain('数学 二次関数の復習')
  })

  it('書き起こしが無いまま終了した記録は、まとめを作らなかった理由を出す', async () => {
    // サーバーはこのとき FINAL ノートを作らない（ClassroomServiceImpl.end()）
    mockApi({ detail: detail({ status: 'STOPPED', statusLabel: '停止（まとめ作成待ち）', summaryJson: null, segments: [], notes: [] }) })
    const { wrapper } = await mountClassroom(ClassroomDetailView, DETAIL)

    for (const key of ['theme', 'content', 'focus', 'homework']) {
      expect(wrapper.get(`[data-cr-detail-section="${key}"]`).text())
        .toContain('書き起こしが無かったため、最終まとめは作成しませんでした。')
    }
    expect(wrapper.get('[data-cr-homework]').text()).toContain('書き起こしが無かったため')
  })

  it('最終まとめがまだのときは「AI が作っています」を出し、できるまで取り直す', async () => {
    vi.useFakeTimers()
    let finalStatus = 'PENDING'
    const { calls } = mockApi()
    const fetchMock = vi.mocked(fetch)
    fetchMock.mockImplementation(async (url: RequestInfo | URL, init?: RequestInit) => {
      if (String(url).includes('/api/user/classroom/12') && (init?.method ?? 'GET') === 'GET') {
        calls.push({ url: String(url), method: 'GET', body: null })
        const notes = [{
          noteId: 91, kind: 'FINAL', phaseNo: null, startSeq: null, endSeq: null,
          status: finalStatus, statusLabel: finalStatus === 'READY' ? 'できました' : '分析待ち',
          noteJson: finalStatus === 'READY' ? JSON.stringify({ テーマ: '比例のグラフ' }) : null,
          errorCode: null, errorMessage: null, createdAt: null, updatedAt: null
        }]
        return new Response(JSON.stringify({
          success: true, code: 'OK', message: 'OK',
          data: detail({ status: 'STOPPED', statusLabel: '停止（まとめ作成待ち）', summaryJson: null, notes })
        }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }
      return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data: {} }), {
        status: 200, headers: { 'Content-Type': 'application/json' }
      })
    })

    const { wrapper } = await mountClassroom(ClassroomDetailView, DETAIL)
    expect(wrapper.get('[data-cr-detail-section="theme"]').text()).toContain('AI が最終まとめを作っています')

    // 2 秒ごとに取り直し、できたら表示する
    finalStatus = 'READY'
    await vi.advanceTimersByTimeAsync(2000)
    await flushPromises()
    expect(wrapper.get('[data-cr-detail-section="theme"]').text()).toContain('比例のグラフ')
  })

  it('記録を取れないときは理由を日本語で出す', async () => {
    mockApi({ failOn: '/api/user/classroom/12', failureMessage: '授業記録が見つかりません。' })
    const { wrapper } = await mountClassroom(ClassroomDetailView, DETAIL)

    expect(wrapper.get('[data-cr-detail-error]').text()).toContain('授業記録が見つかりません。')
  })

  it('【削除】は確認してから DELETE を送り、一覧へ戻る', async () => {
    const { calls } = mockApi()
    const { wrapper, router } = await mountClassroom(ClassroomDetailView, DETAIL)
    vi.stubGlobal('confirm', vi.fn(() => true))

    await wrapper.get('[data-cr-detail-delete]').trigger('click')
    await flushPromises()

    expect(callTo(calls, '/api/user/classroom/12', 'DELETE')).toBeDefined()
    expect(router.currentRoute.value.path).toBe('/student/classroom')
  })
})

/**
 * サーバー（`SettingPageFields.java`）が許可している CLASSROOM_AI の全 30 キー。
 * 画面（このセクション）はこの集合を 1 リクエストで送る（未定義キーを送ると 400 になる）。
 * `classroomAiSttApiKey` だけは**空のときは送らない**（登録済みのキーを空文字で消さない）。
 */
const CLASSROOM_AI_SENT_KEYS = [
  'classroomAiChunkSeconds',
  'classroomAiDailyLimitPerAccount',
  'classroomAiEnabled',
  'classroomAiMaxRecordingMinutes',
  'classroomAiNoteEnabled',
  'classroomAiNoteMaxCompletionTokens',
  'classroomAiNoteProvider',
  'classroomAiNoteSystemPrompt',
  'classroomAiNoteTimeoutSeconds',
  'classroomAiNoteUserPrompt',
  'classroomAiRetentionDays',
  'classroomAiSttProvider',
  'classroomAiSttTimeoutSeconds',
  'classroomAiSummarySystemPrompt',
  'classroomAiSummaryUserPrompt',
  'classroomAiTriggerCooldownMinutes',
  'classroomAiTriggerIntervalMinutes',
  'classroomAiTriggerKeywords',
  'classroomAiTriggerMinChars',
  'classroomAiViewScope'
]

/**
 * 旧 STT 接続情報の 3 キー（`classroomAiSttModel` / `_Endpoint` / `_ApiKey`）。
 *
 * 接続情報は「AIモデル」ページの専用タブ（Google Speech-to-Text / Alibaba Paraformer-Realtime-V2）へ
 * 移したので、この画面はもう送らない。サーバー（`SettingPageFields`）は旧プロバイダー
 * （whisper / azure / other）の互換のため受け付け続ける＝**対応表の方が広い**。
 */
const CLASSROOM_AI_LEGACY_STT_KEYS = [
  'classroomAiSttApiKey',
  'classroomAiSttEndpoint',
  'classroomAiSttModel'
]

/**
 * 言語コードの 6 キー（`classroomAiLang*`）。
 * 言語コードはコード側で固定になったため**画面は送らない**が、サーバー（`SettingPageFields`）は
 * 後方互換のため受け付け続ける（古いクライアントが 400 にならないように）。
 */
const CLASSROOM_AI_LEGACY_LANG_KEYS = [
  'classroomAiLangAuto',
  'classroomAiLangEn',
  'classroomAiLangJa',
  'classroomAiLangJaEn',
  'classroomAiLangZh',
  'classroomAiLangZhEn'
]

describe('システム設定：AI 授業記録（授業録音）', () => {
  let fetchMock: ReturnType<typeof vi.fn>
  let saved: { body: Record<string, unknown> | null }

  /** 前置詞プリセット API の呼び出しを記録する（URL・メソッド・body）。 */
  let presetCalls: { url: string; method: string; body: Record<string, unknown> | null }[] = []
  /** 前置詞プリセットの一覧（テストごとに差し替える）。 */
  let presetRows: { presetId: number; name: string; text: string; displayOrder: number }[] = []
  /** プリセット API を失敗させたいときのメッセージ。 */
  let presetFail: string | null = null

  /** 設定 API（initSettings / saveSettings）＋前置詞プリセット API の応答を返す fetch。 */
  function setup(options: { init?: Record<string, string>; saveError?: string } = {}): VueWrapper {
    saved = { body: null }
    presetCalls = []
    presetFail = null
    presetRows = [
      { presetId: 1, name: '通常の授業', text: '一般的な授業。テーマ・学習内容・先生の重点・宿題を順に整理します。', displayOrder: 1 },
      { presetId: 2, name: '数学の授業', text: '数学の授業。公式や解法の手順・例題を重点的に整理します。', displayOrder: 2 },
      { presetId: 3, name: '英語の授業', text: '英語の授業。新出単語・会話表現・発音を整理します。', displayOrder: 3 }
    ]
    fetchMock = vi.fn((url: string, init?: RequestInit) => {
      const body = init?.body === undefined ? null : JSON.parse(String(init.body)) as Record<string, unknown>
      const method = (init?.method ?? 'GET').toUpperCase()
      if (String(url).includes('/api/admin/classroom-presets')) {
        presetCalls.push({ url: String(url), method, body })
        if (presetFail !== null) {
          return Promise.resolve(new Response(
            JSON.stringify({ success: false, code: 'VALIDATION_ERROR', message: presetFail, data: null }),
            { status: 400, headers: { 'Content-Type': 'application/json' } }
          ))
        }
        if (method === 'GET') {
          return Promise.resolve(new Response(
            JSON.stringify({ success: true, code: 'OK', message: 'OK', data: { items: presetRows } }),
            { status: 200, headers: { 'Content-Type': 'application/json' } }
          ))
        }
        return Promise.resolve(new Response(
          JSON.stringify({ success: true, code: 'OK', message: 'OK', data: {} }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        ))
      }
      if (String(url).includes('/saveSettings')) {
        saved.body = body
        if (options.saveError !== undefined) {
          return Promise.resolve(new Response(
            JSON.stringify({ success: false, code: 'VALIDATION_ERROR', message: options.saveError, data: null }),
            { status: 400, headers: { 'Content-Type': 'application/json' } }
          ))
        }
        return Promise.resolve(new Response(
          JSON.stringify({ success: true, code: 'OK', message: '設定を保存しました。', data: { settings: body?.settings ?? {} } }),
          { status: 200, headers: { 'Content-Type': 'application/json' } }
        ))
      }
      return Promise.resolve(new Response(
        JSON.stringify({ success: true, code: 'OK', message: 'OK', data: { settings: options.init ?? {} } }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      ))
    })
    vi.stubGlobal('fetch', fetchMock)
    return mount(ClassroomAiSettingsSection)
  }

  beforeEach(() => {
    vi.restoreAllMocks()
    localStorage.clear()
    delete window.__study21SystemSettings
  })

  /** ブロック（data-setting-subsection）の中の TAB 名（表示順）。 */
  function tabsOf(wrapper: VueWrapper, subsection: string): string[] {
    return wrapper.findAll(`[data-setting-subsection="${subsection}"] [data-method-tab]`)
      .map((button) => button.text())
  }

  /** TAB の中の項目ラベル（表示順）。 */
  function labelsOf(wrapper: VueWrapper, subsection: string, tab: string): string[] {
    return wrapper
      .findAll(`[data-setting-subsection="${subsection}"] [data-method-panel="${tab}"] .setting-label span`)
      .map((item) => item.text())
  }

  /**
   * AI ブロックの数値項目が**スライダー**であること（共通レイアウト）。
   * 値は `setting_<key>` の id と、右側の値表示（`setting_<key>_value`）で出す
   * （設定ランタイムが id で引いて単位つきの表示を更新するため）。
   */
  function expectSlider(
    wrapper: VueWrapper,
    hook: string,
    key: string,
    spec: { min: string; max: string; step: string; suffix: string; text: string }
  ): void {
    const input = wrapper.get(hook)
    expect(input.attributes('type'), `${hook} がスライダーではない`).toBe('range')
    expect(input.attributes('min')).toBe(spec.min)
    expect(input.attributes('max')).toBe(spec.max)
    expect(input.attributes('step')).toBe(spec.step)
    expect(input.attributes('data-suffix')).toBe(spec.suffix)
    expect(input.attributes('id')).toBe(`setting_${key}`)
    expect(input.classes()).toContain('setting-range')
    expect(input.element.closest('.setting-field')?.className).toContain('setting-range-field')
    const output = wrapper.get(`#setting_${key}_value`)
    expect(output.attributes('data-suffix')).toBe(spec.suffix)
    expect(output.attributes('for')).toBe(`setting_${key}`)
    // 単位は名称ではなく右側の値表示で示す（既定値＋単位）
    expect(output.text()).toBe(spec.text)
  }

  it('有効／無効・STT・AI・分析トリガー・前置詞・録音の設定を出す', async () => {
    const wrapper = setup()
    await flushPromises()

    expect(wrapper.get('[data-cr-set]').text()).toContain('AI 授業記録')
    expect(wrapper.get('[data-cr-set-badge]').text()).toBe('授業録音')
    // 「API Key と URL は「AIモデル」ページの同じスロットを共通利用します。」は出さない
    expect(wrapper.find('[data-cr-set-note]').exists()).toBe(false)
    // 機能スイッチは画面に出さない（機能は常に有効。値は読み込んだものをそのまま保持する）
    expect(wrapper.find('[data-cr-set-enabled]').exists()).toBe(false)
    // セクションカード（日本語単語AI と同じレイアウト）と入力→出力バッジ
    expect(wrapper.findAll('[data-cr-set] [data-setting-subsection]')).toHaveLength(6)
    expect(wrapper.get('[data-setting-subsection="classroom-note-phase"] .setting-io-badge').text()
      .replace(/\s+/g, ' ')).toContain('授業の書き起こし（区間）')
    // STT は 3 つだけ（ブラウザ音声認識を先頭＝既定にする。API Key 不要）
    expect(wrapper.findAll('[data-cr-set-stt-provider] option').map((option) => option.text()))
      .toEqual(['ブラウザ音声認識（Chrome / Edge・キー不要）', 'Google Speech-to-Text',
        'Alibaba Paraformer-Realtime-V2'])
    expect((wrapper.get('[data-cr-set-stt-provider]').element as HTMLSelectElement).value).toBe('browser')
    // 接続情報（モデル・URL・API Key）は「AIモデル」ページの専用タブが持つので、ここには出さない
    expect(wrapper.find('[data-cr-set-stt-model]').exists()).toBe(false)
    expect(wrapper.find('[data-cr-set-stt-api-key]').exists()).toBe(false)
    expect(wrapper.find('[data-cr-set-stt-endpoint]').exists()).toBe(false)
    expect(wrapper.find('[data-cr-set-stt-connection]').exists()).toBe(false)
    // ブラウザ認識ではサーバー側の設定（タイムアウト）が要らないので隠す
    expect(wrapper.get('[data-setting-subsection="classroom-stt"] [data-cr-set-stt-timeout]').isVisible()).toBe(false)
    // AI（スロット付きの値。URL と API Key は「AIモデル」ページを共用する）
    expect(wrapper.findAll('[data-cr-set-ai-provider] option').map((option) => option.attributes('value')))
      .toEqual(['qwen:1', 'qwen:2', 'qwen:3', 'qwen:4', 'qwen:5',
        'doubao:1', 'doubao:2', 'deepseek:1', 'deepseek:2', 'chatgpt:1', 'chatgpt:2'])
    expect(wrapper.get('[data-cr-set-prompt]').element as HTMLTextAreaElement).toBeTruthy()
    // 言語コードはコード側で固定なので、画面には入力欄を出さない
    expect(wrapper.findAll('[data-cr-set-mapping-mode]')).toHaveLength(0)
    expect(wrapper.text()).toContain('言語コードはコード側で固定')
    // 分析トリガー・録音・保存（サーバーが受け付ける範囲に合わせる）
    expect(wrapper.get('[data-cr-set-interval]').attributes('min')).toBe('3')
    expect(wrapper.get('[data-cr-set-interval]').attributes('max')).toBe('20')
    expect((wrapper.get('[data-cr-set-interval]').element as HTMLInputElement).value).toBe('5')
    expect(wrapper.get('[data-cr-set-char]').attributes('min')).toBe('100')
    // 数字は AI のブロックと同じくスライダー（利用者の指示 2026-09-18）
    expect(wrapper.get('[data-cr-set-char]').attributes('type')).toBe('range')
    expect(wrapper.get('[data-cr-set-char]').attributes('step')).toBe('100')
    expect(wrapper.get('[data-cr-set-char]').attributes('data-suffix')).toBe('文字')
    expect(wrapper.get('[data-cr-set-max-minutes]').attributes('max')).toBe('240')
    expect((wrapper.get('[data-cr-set-max-minutes]').element as HTMLInputElement).value).toBe('120')
    expect(wrapper.get('[data-cr-set-daily-limit]').attributes('max')).toBe('100')
    expect(wrapper.get('[data-cr-set-retention]').attributes('max')).toBe('365')
    expect((wrapper.get('[data-cr-set-retention]').element as HTMLInputElement).value).toBe('30')
  })

  it('AI を呼ぶ 3 ブロックの TAB は共通レイアウトの固定順（項目の無い TAB は出さない）', async () => {
    const wrapper = setup()
    await flushPromises()

    // STT は「基本設定（AI の接続）」＋「その他（AI 以外＝音声の送り方）」
    expect(tabsOf(wrapper, 'classroom-stt')).toEqual(['基本設定', 'その他'])
    // batC61 は 基本設定 / System Prompt / User Prompt（「その他」に項目が無いので TAB 自体を出さない）
    expect(tabsOf(wrapper, 'classroom-note-phase')).toEqual(['基本設定', 'System Prompt', 'User Prompt'])
    // batC62 は設定項目がプロンプト 2 つだけなので 2 つ
    expect(tabsOf(wrapper, 'classroom-note-final')).toEqual(['System Prompt', 'User Prompt'])

    // TAB とパネルは同じ数だけあり、最初の TAB だけが active（切替はランタイムの委譲ハンドラ）
    expect(wrapper.findAll('[data-setting-subsection="classroom-stt"] [data-method-panel]')).toHaveLength(2)
    expect(wrapper.findAll('[data-setting-subsection="classroom-note-final"] [data-method-panel]')).toHaveLength(2)
    expect(wrapper.get('[data-setting-subsection="classroom-stt"] [data-method-tab="基本設定"]').classes())
      .toContain('active')
    expect(wrapper.get('[data-setting-subsection="classroom-note-final"] [data-method-tab="System Prompt"]').classes())
      .toContain('active')
    expect(wrapper.get('[data-setting-subsection="classroom-stt"] [data-method-panel="基本設定"]')
      .attributes('hidden')).toBeUndefined()
    expect(wrapper.get('[data-setting-subsection="classroom-stt"] [data-method-panel="その他"]')
      .attributes('hidden')).toBeDefined()
    // 「このブロックに追加の設定はありません」の空パネルは残さない
    expect(wrapper.text()).not.toContain('このブロックに追加の設定はありません')
  })

  it('STT の基本設定は 接続 → タイムアウト の順（名称は共通の統一名称）', async () => {
    const wrapper = setup()
    await flushPromises()

    // 接続情報（モデル・URL・API Key）は「AIモデル」ページが持つので、ここには出さない
    expect(labelsOf(wrapper, 'classroom-stt', '基本設定')).toEqual([
      'STT プロバイダー', 'リクエストタイムアウト'
    ])
    // 分塊の長さだけが「その他」（AI 以外の設定）へ入り、その TAB の案内を出す
    expect(labelsOf(wrapper, 'classroom-stt', 'その他')).toEqual(['分塊の長さ'])
    expect(wrapper.get('[data-setting-subsection="classroom-stt"] [data-method-panel="その他"] .setting-help').text())
      .toContain('音声の送り方')
    // STT は音声認識プロバイダーの選択なので「使用モデル」にはしない
    expect(wrapper.get('[data-setting-subsection="classroom-stt"]').text()).not.toContain('使用モデル')
  })

  it('モデル／プロバイダーの選択は 1 行（full）で出る', async () => {
    const wrapper = setup()
    await flushPromises()

    for (const hook of ['data-cr-set-ai-provider', 'data-cr-set-stt-provider']) {
      const control = wrapper.get(`[${hook}]`).element
      expect(control.closest('.setting-field')?.classList.contains('full'), hook).toBe(true)
    }
    // 数値（スライダー）は 2 列のまま
    expect(wrapper.get('[data-cr-set-stt-timeout]').element.closest('.setting-field')?.classList.contains('full'))
      .toBe(false)
  })

  it('batC61 の基本設定は 使用モデル → リクエストタイムアウト → 最大出力Token数 の順', async () => {
    const wrapper = setup()
    await flushPromises()

    expect(labelsOf(wrapper, 'classroom-note-phase', '基本設定'))
      .toEqual(['使用モデル', 'リクエストタイムアウト', '最大出力Token数'])
    expect(labelsOf(wrapper, 'classroom-note-phase', 'System Prompt')).toEqual(['System Prompt'])
    expect(labelsOf(wrapper, 'classroom-note-phase', 'User Prompt')).toEqual(['User Prompt'])
    expect(labelsOf(wrapper, 'classroom-note-final', 'System Prompt')).toEqual(['System Prompt'])
    expect(labelsOf(wrapper, 'classroom-note-final', 'User Prompt')).toEqual(['User Prompt'])
    // 使用モデル・タイムアウトは batC61 と共通であることをカードの説明に残す
    expect(wrapper.get('[data-setting-subsection="classroom-note-final"]').text()).toContain('batC61 と共通')
  })

  it('AI ブロックの数値項目はスライダー（min/max/step ＋ 単位は右側の値表示）', async () => {
    const wrapper = setup()
    await flushPromises()

    // タイムアウトはサーバー側で認識するときだけ出す（ブラウザ認識では要らない）
    await wrapper.get('[data-cr-set-stt-provider]').setValue('google')
    await flushPromises()
    expectSlider(wrapper, '[data-cr-set-stt-timeout]', 'classroomAiSttTimeoutSeconds',
      { min: '30', max: '600', step: '30', suffix: 's', text: '60s' })
    expectSlider(wrapper, '[data-cr-set-chunk-seconds]', 'classroomAiChunkSeconds',
      { min: '5', max: '120', step: '5', suffix: 's', text: '20s' })
    expectSlider(wrapper, '[data-cr-set-note-timeout]', 'classroomAiNoteTimeoutSeconds',
      { min: '30', max: '600', step: '30', suffix: 's', text: '120s' })
    expectSlider(wrapper, '[data-cr-set-note-max-tokens]', 'classroomAiNoteMaxCompletionTokens',
      { min: '1024', max: '65536', step: '512', suffix: '', text: '2048' })

    // スライダーを動かすと右側の値も変わり、保存にもその値が載る
    await wrapper.get('[data-cr-set-note-timeout]').setValue('300')
    expect(wrapper.get('#setting_classroomAiNoteTimeoutSeconds_value').text()).toBe('300s')
    await wrapper.get('[data-cr-set-save]').trigger('click')
    await flushPromises()
    expect((saved.body?.settings as Record<string, string>).classroomAiNoteTimeoutSeconds).toBe('300')

    // AI を呼ばないブロック（録音と保存・トリガー）も、数字はスライダー（利用者の指示）
    const recordTriggers: [string, string, string, string][] = [
      ['[data-cr-set-max-minutes]', '1', '240', '分'],
      ['[data-cr-set-daily-limit]', '0', '100', '回'],
      ['[data-cr-set-retention]', '1', '365', '日'],
      ['[data-cr-set-interval]', '3', '20', '分'],
      ['[data-cr-set-char]', '100', '10000', '文字'],
      ['[data-cr-set-cooldown]', '1', '30', '分']
    ]
    for (const [hook, min, max, suffix] of recordTriggers) {
      const input = wrapper.get(hook)
      expect(input.attributes('type'), `${hook} がスライダーではない`).toBe('range')
      expect(input.attributes('min'), hook).toBe(min)
      expect(input.attributes('max'), hook).toBe(max)
      expect(input.attributes('step'), hook).toBe(hook === '[data-cr-set-char]' ? '100' : '1')
      expect(input.attributes('data-suffix'), hook).toBe(suffix)
      // 右側の値表示（設定ランタイムが読む形）も付ける
      const key = (input.attributes('id') ?? '').replace(/^setting_/, '')
      expect(wrapper.find(`#setting_${key}_value`).exists(), key).toBe(true)
    }
    // 数字の入力欄は 1 つも残っていない
    expect(wrapper.findAll('input[type="number"]')).toHaveLength(0)
  })

  it('対応する設定キーが無いコントロールは残さない（閲覧権限の 3 チェックなど）', async () => {
    const wrapper = setup()
    await flushPromises()

    // 対応キーが無い（または意味が合わない）項目は削除した
    for (const selector of [
      '[data-cr-set-perm-student]', '[data-cr-set-perm-guardian]', '[data-cr-set-perm-admin]',
      '[data-cr-set-stt-enabled]', '[data-cr-set-ai-enabled]',
      '[data-cr-set-ai-model]', '[data-cr-set-ai-api-key]', '[data-cr-set-ai-endpoint]'
    ]) {
      expect(wrapper.find(selector).exists(), `${selector} が残っている`).toBe(false)
    }
  })

  it('トリガーキーワードを追加・削除できる', async () => {
    const wrapper = setup()
    await flushPromises()
    const before = wrapper.findAll('[data-cr-set-keyword-item]').length

    await wrapper.get('[data-cr-set-keyword-input]').setValue('課題')
    await wrapper.get('[data-cr-set-keyword-add]').trigger('click')
    expect(wrapper.findAll('[data-cr-set-keyword-item]')).toHaveLength(before + 1)
    expect(wrapper.text()).toContain('課題')

    const removeIndex = wrapper.findAll('[data-cr-set-keyword-item]').length - 1
    await wrapper.get(`[data-cr-set-keyword-remove="${removeIndex}"]`).trigger('click')
    expect(wrapper.findAll('[data-cr-set-keyword-item]')).toHaveLength(before)
  })

  it('前置詞プリセットをサーバーから読み込んで表示する（表示順つき）', async () => {
    const wrapper = setup()
    await flushPromises()

    expect(presetCalls[0]).toMatchObject({ url: '/api/admin/classroom-presets', method: 'GET' })
    const items = wrapper.findAll('[data-cr-set-preset-item]')
    expect(items.map((item) => item.attributes('data-cr-set-preset-item'))).toEqual(['1', '2', '3'])
    expect(items[0]?.text()).toContain('通常の授業')
    expect(items[0]?.text()).toContain('一般的な授業。')
    expect(items[0]?.get('[data-cr-set-preset-order-label]').text()).toBe('1')
  })

  it('前置詞プリセットを追加できる（POST → 一覧を取り直す）', async () => {
    const wrapper = setup()
    await flushPromises()

    await wrapper.get('[data-cr-set-preset-add]').trigger('click')
    expect((wrapper.get('[data-cr-set-preset-order]').element as HTMLInputElement).value).toBe('4')
    await wrapper.get('[data-cr-set-preset-name]').setValue('漢字の授業')
    await wrapper.get('[data-cr-set-preset-desc]').setValue('新出漢字と読みを整理します。')
    await wrapper.get('[data-cr-set-preset-order]').setValue('5')
    // サーバーが 4 件目を返すようにして、保存後に一覧を取り直すことを確かめる
    presetRows = [...presetRows, { presetId: 9, name: '漢字の授業', text: '新出漢字と読みを整理します。', displayOrder: 5 }]
    await wrapper.get('[data-cr-set-preset-save]').trigger('click')
    await flushPromises()

    // POST のあとに一覧を取り直す（最後の呼び出しは GET）
    expect(presetCalls.find((call) => call.method === 'POST')).toMatchObject({
      url: '/api/admin/classroom-presets',
      body: { name: '漢字の授業', text: '新出漢字と読みを整理します。', displayOrder: 5 }
    })
    expect(presetCalls.filter((call) => call.method === 'GET')).toHaveLength(2)
    expect(wrapper.findAll('[data-cr-set-preset-item]')).toHaveLength(4)
    expect(wrapper.text()).toContain('漢字の授業')
  })

  it('前置詞プリセットを編集できる（PUT）', async () => {
    const wrapper = setup()
    await flushPromises()

    await wrapper.get('[data-cr-set-preset-edit="2"]').trigger('click')
    expect((wrapper.get('[data-cr-set-preset-name]').element as HTMLInputElement).value).toBe('数学の授業')
    expect((wrapper.get('[data-cr-set-preset-desc]').element as HTMLTextAreaElement).value)
      .toContain('公式や解法の手順')
    expect((wrapper.get('[data-cr-set-preset-order]').element as HTMLInputElement).value).toBe('2')

    await wrapper.get('[data-cr-set-preset-name]').setValue('数学の授業（改）')
    presetRows = presetRows.map((row) => (
      row.presetId === 2 ? { ...row, name: '数学の授業（改）' } : row))
    await wrapper.get('[data-cr-set-preset-save]').trigger('click')
    await flushPromises()

    expect(presetCalls.find((call) => call.method === 'PUT')).toMatchObject({
      url: '/api/admin/classroom-presets/2',
      body: { name: '数学の授業（改）', text: '数学の授業。公式や解法の手順・例題を重点的に整理します。', displayOrder: 2 }
    })
    expect(wrapper.text()).toContain('数学の授業（改）')
  })

  it('前置詞プリセットの削除は確認ダイアログを経て DELETE する', async () => {
    const wrapper = setup()
    await flushPromises()

    // 削除を押すと確認ダイアログが出る（まだ消さない）
    expect(wrapper.find('[data-cr-set-preset-delete-dialog]').exists()).toBe(false)
    await wrapper.get('[data-cr-set-preset-delete="3"]').trigger('click')
    const dialog = wrapper.get('[data-cr-set-preset-delete-dialog]')
    expect(dialog.get('[data-cr-set-preset-delete-message]').text()).toContain('英語の授業')
    // 消えるのは選択肢だけ。過去の授業記録は残ることを確認画面に書く
    expect(dialog.get('[data-cr-set-preset-delete-note]').text()).toContain('過去の授業記録はそのまま残り')
    expect(presetCalls.filter((call) => call.method === 'DELETE')).toHaveLength(0)

    // キャンセルしたら閉じて何もしない
    await dialog.get('[data-cr-set-preset-delete-dismiss]').trigger('click')
    expect(wrapper.find('[data-cr-set-preset-delete-dialog]').exists()).toBe(false)
    expect(presetCalls.filter((call) => call.method === 'DELETE')).toHaveLength(0)

    // 「削除する」で DELETE → 一覧を取り直す
    presetRows = presetRows.filter((row) => row.presetId !== 3)
    await wrapper.get('[data-cr-set-preset-delete="3"]').trigger('click')
    await wrapper.get('[data-cr-set-preset-delete-confirm]').trigger('click')
    await flushPromises()
    expect(presetCalls.find((call) => call.method === 'DELETE')).toMatchObject({
      url: '/api/admin/classroom-presets/3'
    })
    expect(wrapper.find('[data-cr-set-preset-delete-dialog]').exists()).toBe(false)
    expect(wrapper.findAll('[data-cr-set-preset-item]')).toHaveLength(2)
    expect(wrapper.text()).not.toContain('英語の授業')
  })

  it('前置詞プリセットの取得に失敗したら理由を日本語で出す', async () => {
    const wrapper = setup()
    // 最初の読み込みを失敗させる
    presetFail = '前置詞プリセットを取得できませんでした。'
    await wrapper.get('[data-cr-set-preset-add]').trigger('click')
    await wrapper.get('[data-cr-set-preset-name]').setValue('漢字の授業')
    await wrapper.get('[data-cr-set-preset-save]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-cr-set-preset-error]').text()).toContain('前置詞プリセットを取得できませんでした。')
  })

  it('保存で CLASSROOM_AI のキーを送る（STT の接続情報は「AIモデル」ページが持つので送らない）', async () => {
    const wrapper = setup({ init: { classroomAiEnabled: 'true' } })
    await flushPromises()

    const save = wrapper.get('[data-cr-set-save]')
    expect(save.attributes('disabled')).toBeUndefined()
    expect(save.text()).toContain('この設定を保存')

    // 画面で編集できる項目を変えて送る
    await wrapper.get('[data-cr-set-stt-provider]').setValue('google')
    await wrapper.get('[data-cr-set-ai-provider]').setValue('chatgpt:1')
    await wrapper.get('[data-cr-set-interval]').setValue('10')
    await wrapper.get('[data-cr-set-char]').setValue('500')
    await wrapper.get('[data-cr-set-max-minutes]').setValue('90')
    await wrapper.get('[data-cr-set-daily-limit]').setValue('20')
    await wrapper.get('[data-cr-set-retention]').setValue('60')
    await wrapper.get('[data-cr-set-keyword-input]').setValue('小テスト')
    await wrapper.get('[data-cr-set-keyword-add]').trigger('click')
    await save.trigger('click')
    await flushPromises()

    // 未定義キーを送ると 400 になり、画面の他の設定もまとめて保存できなくなる
    const settings = saved.body?.settings as Record<string, string>
    expect(Object.keys(settings).sort()).toEqual([...CLASSROOM_AI_SENT_KEYS].sort())
    expect(settings.classroomAiSttProvider).toBe('google')
    // STT の接続情報は「AIモデル」ページの担当なので、この画面からは送らない
    expect(settings.classroomAiSttModel).toBeUndefined()
    expect(settings.classroomAiSttEndpoint).toBeUndefined()
    expect(settings.classroomAiSttApiKey).toBeUndefined()
    expect(settings.classroomAiNoteProvider).toBe('chatgpt:1')
    expect(settings.classroomAiTriggerIntervalMinutes).toBe('10')
    expect(settings.classroomAiTriggerMinChars).toBe('500')
    expect(settings.classroomAiMaxRecordingMinutes).toBe('90')
    expect(settings.classroomAiDailyLimitPerAccount).toBe('20')
    expect(settings.classroomAiRetentionDays).toBe('60')
    expect(settings.classroomAiTriggerKeywords).toContain('小テスト')
    expect(settings.classroomAiEnabled).toBe('true')
    // 画面に編集欄が無いキーも、読み込んだ値（無ければ seed の既定値）で欠けない
    expect(settings.classroomAiChunkSeconds).toBe('20')
    expect(settings.classroomAiSttTimeoutSeconds).toBe('60')
    expect(settings.classroomAiTriggerCooldownMinutes).toBe('3')
    expect(settings.classroomAiViewScope).toBe('family')
    expect(saved.body?.userId).toBe('setting.jsp')
    expect(wrapper.get('[data-cr-set-save-note]').text()).toContain('設定を保存しました。')
  })

  it('AI 解析（フェーズノート・最終まとめ）の有効／無効を選べる', async () => {
    const wrapper = setup({ init: { classroomAiNoteEnabled: 'false' } })
    await flushPromises()

    const select = wrapper.get('[data-cr-set-note-enabled]').element as HTMLSelectElement
    expect([...select.options].map((option) => option.value)).toEqual(['true', 'false'])
    expect(select.value).toBe('false')

    // 保存でサーバーへ送る（値はそのまま）
    await wrapper.get('[data-cr-set-save]').trigger('click')
    await flushPromises()
    expect((saved.body?.settings as Record<string, string>).classroomAiNoteEnabled).toBe('false')
  })

  it('STT の接続情報（旧キー）は、サーバーが値を持っていても送らない（AIモデルページが持つ）', async () => {
    // サーバー側で認識するプロバイダーを選び、旧キーにも値が入っている状態を作る
    const wrapper = setup({
      init: {
        classroomAiEnabled: 'true',
        classroomAiSttProvider: 'google',
        classroomAiSttModel: 'whisper-1-large',
        classroomAiSttEndpoint: 'https://stt.example.com/v1/audio/transcriptions',
        classroomAiSttApiKey: 'sk-existing'
      }
    })
    await flushPromises()

    await wrapper.get('[data-cr-set-save]').trigger('click')
    await flushPromises()

    const settings = saved.body?.settings as Record<string, string>
    // 置き場が「AIモデル」ページへ移ったので、この画面からは送らない（空文字で上書きしない）
    expect(settings.classroomAiSttApiKey).toBeUndefined()
    expect(settings.classroomAiSttEndpoint).toBeUndefined()
    expect(settings.classroomAiSttModel).toBeUndefined()
    expect(Object.keys(settings).sort()).toEqual([...CLASSROOM_AI_SENT_KEYS].sort())
  })

  it('有効／無効（classroomAiEnabled）は画面に出さず、読み込んだ値をそのまま保持する', async () => {
    const wrapper = setup({ init: { classroomAiEnabled: 'false' } })
    await flushPromises()

    // 画面にはスイッチが無い
    expect(wrapper.find('[data-cr-set-enabled]').exists()).toBe(false)
    // 保存では値がそのまま載る（空文字にしない・失わせない）
    await wrapper.get('[data-cr-set-save]').trigger('click')
    await flushPromises()
    expect((saved.body?.settings as Record<string, string>).classroomAiEnabled).toBe('false')

    // サーバーが値を返さないときは**そのキーを送らない**（空文字で上書きしない）
    const blank = setup()
    await flushPromises()
    await blank.get('[data-cr-set-save]').trigger('click')
    await flushPromises()
    expect(saved.body?.settings as Record<string, string>).not.toHaveProperty('classroomAiEnabled')
  })

  /**
   * v-show で出している項目の表示状態。
   *
   * jsdom の getComputedStyle は「style.display を '' に戻した」あと古い値（none）を返し続けることが
   * あるので、実際に v-show が書く inline の display で見る（`isVisible()` は切り替え後に信用できない）。
   */
  function fieldShown(wrapper: VueWrapper, hook: string): boolean {
    const field = wrapper.get(`[${hook}]`).element.closest('.setting-field') as HTMLElement | null
    return field !== null && field.style.display !== 'none'
  }

  it('ブラウザ認識のときはサーバー側の項目を隠し、切り替えると出る', async () => {
    const wrapper = setup({ init: { classroomAiSttProvider: 'browser' } })
    await flushPromises()

    expect(fieldShown(wrapper, 'data-cr-set-stt-timeout')).toBe(false)
    // 接続情報の案内も、サーバー側で認識するときだけ出す
    expect(wrapper.find('[data-cr-set-stt-connection]').exists()).toBe(false)

    // サーバー側で認識するプロバイダーへ切り替えると、必要な項目と「どこで設定するか」が出る
    await wrapper.get('[data-cr-set-stt-provider]').setValue('alibaba')
    await flushPromises()
    expect(fieldShown(wrapper, 'data-cr-set-stt-timeout')).toBe(true)
    const connection = wrapper.get('[data-cr-set-stt-connection]')
    expect(connection.text()).toContain('Alibaba Paraformer-Realtime-V2')
    expect(connection.text()).toContain('paraformer-realtime-v2')
    expect(connection.text()).toContain('wss://dashscope.aliyuncs.com/api-ws/v1/inference')
    expect(connection.text()).toContain('未設定（設定するまで書き起こしは失敗します）')
  })

  it('STT の API Key を「AIモデル」ページで設定すると、この画面は「設定済み」と出す', async () => {
    const wrapper = setup({
      init: { classroomAiSttProvider: 'google', googleSttApiKey: 'google-key-1' }
    })
    await flushPromises()

    expect(wrapper.get('[data-cr-set-stt-connection]').text()).toContain('設定済み')
  })

  it('検証エラー（400）の理由を画面に出す（保存できなかったことを隠さない）', async () => {
    const wrapper = setup({ saveError: '設定ページに存在しないキーです: classroomAiUnknown' })
    await flushPromises()

    await wrapper.get('[data-cr-set-save]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-cr-set-save-note]').text())
      .toContain('設定ページに存在しないキーです: classroomAiUnknown')
  })

  it('サーバーの値を読み込んで画面に反映する（編集欄が無いキーも保持する）', async () => {
    const wrapper = setup({
      init: {
        classroomAiEnabled: 'false',
        classroomAiSttProvider: 'google',
        // 旧キー（モデル・Endpoint・API Key）はもう画面が持たないが、サーバーには残っている
        classroomAiSttModel: 'whisper-1-large',
        classroomAiSttEndpoint: 'https://stt.example.com/v1/audio/transcriptions',
        classroomAiSttApiKey: 'sk-existing',
        classroomAiSttTimeoutSeconds: '90',
        // STT の接続情報は「AIモデル」ページの値（この画面は表示だけに使う）
        googleSttModel: 'latest_long',
        googleSttUrl: 'https://speech.googleapis.com/v1/speech:recognize',
        alibabaSttModel: 'paraformer-realtime-v2',
        classroomAiChunkSeconds: '30',
        classroomAiNoteProvider: 'chatgpt:2',
        classroomAiNoteSystemPrompt: '4 段落に整理してください。',
        classroomAiNoteUserPrompt: '書き起こし:\n{transcript}',
        classroomAiNoteTimeoutSeconds: '300',
        classroomAiNoteMaxCompletionTokens: '4096',
        classroomAiSummarySystemPrompt: 'まとめを作ってください。',
        classroomAiSummaryUserPrompt: '全体:\n{transcript}',
        classroomAiTriggerIntervalMinutes: '8',
        classroomAiTriggerMinChars: '300',
        classroomAiTriggerKeywords: '宿題,試験の重点',
        classroomAiTriggerCooldownMinutes: '10',
        classroomAiMaxRecordingMinutes: '60',
        classroomAiDailyLimitPerAccount: '5',
        classroomAiRetentionDays: '90',
        classroomAiViewScope: 'self'
      }
    })
    await flushPromises()

    expect(wrapper.find('[data-cr-set-enabled]').exists()).toBe(false)
    expect((wrapper.get('[data-cr-set-stt-provider]').element as HTMLSelectElement).value).toBe('google')
    // サーバー側で認識するので、タイムアウトは出し、どこで接続情報を設定するかを案内する
    expect(wrapper.get('[data-setting-subsection="classroom-stt"] [data-cr-set-stt-timeout]').isVisible()).toBe(true)
    const connection = wrapper.get('[data-cr-set-stt-connection]')
    expect(connection.text()).toContain('Google Speech-to-Text')
    expect(connection.text()).toContain('latest_long')
    expect(connection.text()).toContain('未設定（設定するまで書き起こしは失敗します）')
    expect((wrapper.get('[data-cr-set-ai-provider]').element as HTMLSelectElement).value).toBe('chatgpt:2')
    expect((wrapper.get('[data-cr-set-prompt]').element as HTMLTextAreaElement).value)
      .toBe('4 段落に整理してください。')
    expect((wrapper.get('[data-cr-set-interval]').element as HTMLInputElement).value).toBe('8')
    expect((wrapper.get('[data-cr-set-char]').element as HTMLInputElement).value).toBe('300')
    expect((wrapper.get('[data-cr-set-max-minutes]').element as HTMLInputElement).value).toBe('60')
    expect((wrapper.get('[data-cr-set-daily-limit]').element as HTMLInputElement).value).toBe('5')
    expect((wrapper.get('[data-cr-set-retention]').element as HTMLInputElement).value).toBe('90')
    expect(wrapper.findAll('[data-cr-set-keyword-item]').map((item) => item.text().replace('×', '').trim()))
      .toEqual(['宿題', '試験の重点'])

    // 読み込んだ値をそのまま送り返す（編集欄が無いキーも失わない）
    await wrapper.get('[data-cr-set-save]').trigger('click')
    await flushPromises()
    const settings = saved.body?.settings as Record<string, string>
    expect(settings.classroomAiEnabled).toBe('false')
    // STT の接続情報（旧キー）はこの画面の担当ではないので送らない
    expect(settings.classroomAiSttApiKey).toBeUndefined()
    expect(settings.classroomAiChunkSeconds).toBe('30')
    expect(settings.classroomAiSttTimeoutSeconds).toBe('90')
    expect(settings.classroomAiTriggerCooldownMinutes).toBe('10')
    expect(settings.classroomAiNoteUserPrompt).toBe('書き起こし:\n{transcript}')
    expect(settings.classroomAiNoteTimeoutSeconds).toBe('300')
    expect(settings.classroomAiNoteMaxCompletionTokens).toBe('4096')
    expect(settings.classroomAiSummarySystemPrompt).toBe('まとめを作ってください。')
    expect(settings.classroomAiSummaryUserPrompt).toBe('全体:\n{transcript}')
    expect(settings.classroomAiViewScope).toBe('self')
    expect(settings.classroomAiTriggerKeywords).toBe('宿題,試験の重点')
  })

  it('画面全体の【設定を保存】に載せるため registerSection に登録する', async () => {
    const registered: { getValues?: () => Record<string, string>; applyValues?: (values: Record<string, string>) => void }[] = []
    window.__study21SystemSettings = {
      mount: () => undefined,
      registerSection: (section) => { registered.push(section) },
      collectValues: () => ({})
    }

    const wrapper = setup({ init: { classroomAiEnabled: 'true' } })
    await flushPromises()

    expect(registered).toHaveLength(1)
    // 画面全体の保存に載る値（CLASSROOM_AI のうち、この画面が持つキー全部）
    expect(Object.keys(registered[0].getValues?.() ?? {}).sort())
      .toEqual([...CLASSROOM_AI_SENT_KEYS].sort())
    // 再読込でサーバーの値が画面に入る（ランタイムの applyValues から呼ばれる）
    registered[0].applyValues?.({ classroomAiMaxRecordingMinutes: '45' })
    await flushPromises()
    expect((wrapper.get('[data-cr-set-max-minutes]').element as HTMLInputElement).value).toBe('45')
  })

  it('システム設定画面がこのセクションを出している', () => {
    const view = readFileSync(
      path.join(webRoot, 'src', 'views', 'admin', 'system-settings', 'SystemSettingsView.vue'),
      'utf8'
    )

    expect(view).toContain("import ClassroomAiSettingsSection from '@/views/admin/system-settings/ClassroomAiSettingsSection.vue'")
    expect(view).toContain('<ClassroomAiSettingsSection />')
  })

  it('設定の定義（対応表・カタログ）に 30 キーが揃っている', () => {
    const runtime = readFileSync(
      path.join(webRoot, 'src', 'features', 'system-settings', 'study2SettingRuntime.ts'), 'utf8'
    )
    const fields = readFileSync(
      path.join(webRoot, '..', '..', 'backend', 'admin-api', 'src', 'main', 'java', 'com', 'study21',
        'admin', 'setting', 'SettingPageFields.java'), 'utf8'
    )
    const catalog = readFileSync(
      path.join(webRoot, '..', '..', 'database', '設定', 'TBL_COM_設定項目_init.sql'), 'utf8'
    )

    // 画面が送るキーは、サーバーの対応表が許可しているキーの**部分集合**でなければ 400 になる。
    // 対応表の方が広いのは、言語コードの 6 キーを後方互換で残しているため（画面はもう送らない）。
    const allowed = [...fields.matchAll(/put\(m, "(classroomAi\w+)",\s+"CLASSROOM_AI"/g)]
      .map((match) => match[1] as string)
    for (const key of CLASSROOM_AI_SENT_KEYS) {
      expect(allowed, `${key} が対応表に無い`).toContain(key)
    }
    expect(CLASSROOM_AI_SENT_KEYS.length).toBe(20)
    expect([...allowed].sort())
      .toEqual([...CLASSROOM_AI_SENT_KEYS, ...CLASSROOM_AI_LEGACY_LANG_KEYS,
        ...CLASSROOM_AI_LEGACY_STT_KEYS].sort())
    // カタログ（COM_設定項目）にも 29 キーの定義がある（話者分離は廃止して消した）
    expect((catalog.match(/'CLASSROOM_AI','CLASSROOM_AI_/g) ?? []).length).toBe(29)
    // ランタイムは分類を external として持ち、値の受け渡しは registerSection で行う
    expect(runtime).toContain("f('classroomAiEnabled'")
    // ランタイム側のカテゴリは external（分類ナビには出すが、項目の描画はこのコンポーネントが行う）
    expect(runtime).toContain("id: 'classroom_ai'")
    expect(runtime).toContain('external: true')
    expect(runtime).toContain("data-external-slot=")
    // カタログ（COM_設定項目）にはバッチ専用キーも含めて登録されている
    expect(catalog).toContain("'CLASSROOM_AI','CLASSROOM_AI_ENABLED'")
    expect(catalog).toContain("'CLASSROOM_AI','CLASSROOM_AI_STT_PROVIDER'")
    expect(catalog).toContain("'CLASSROOM_AI','CLASSROOM_AI_TRIGGER_INTERVAL_MINUTES'")
    expect(catalog).toContain("'CLASSROOM_AI','CLASSROOM_AI_MAX_RECORDING_MINUTES'")
  })

  it('ブラウザの保管領域には保存しない（サーバーにだけ保存する）', () => {
    const source = readFileSync(
      path.join(webRoot, 'src', 'views', 'admin', 'system-settings', 'ClassroomAiSettingsSection.vue'),
      'utf8'
    )

    expect(source).not.toContain('localStorage')
    expect(source).not.toContain('sessionStorage')
    expect(source).not.toContain('fetch(')
    // 保存は API モジュールを通す
    expect(source).toContain('saveSettingFields')
  })
})

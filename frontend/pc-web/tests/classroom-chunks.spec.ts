import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ClassroomLiveView from '@/views/classroom/ClassroomLiveView.vue'

/**
 * 録音の分塊（音声）の連番と、保存済みの分塊の扱い。
 *
 * <p>以前は分塊の重複判定と「続きの連番」に**転写セグメントの連番**（＝文の数）を使っていた。
 * 文の数と分塊の数は違うので、転写が分塊より多く出た回に分塊が「重複」として捨てられ、
 * 画面は開き直したときに続きの番号を作れなかった。ここでは
 * 「続きの番号は **分塊表**（`GET /classroom/{id}/chunks`）から取る（転写の連番から作らない）」
 * 「録音の位置も保存済みの分塊から続ける」「同じ連番に違う内容は 409 で、送り直さない」を固定する。</p>
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })
const LIVE = '/student/classroom/12/live?name=数学&subject=数学&languageMode=ja'

type Call = { url: string; method: string; form?: FormData }

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

async function mountLive(): Promise<VueWrapper> {
  const router = classroomRouter()
  await router.push(LIVE)
  await router.isReady()
  const wrapper = mount(ClassroomLiveView as Component, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper as VueWrapper
}

/** `MediaRecorder` の代役（テストが分塊を流し込める）。 */
function installFakeRecorder(): { instances: Array<{ ondataavailable: ((event: { data: Blob }) => void) | null }> } {
  const instances: Array<{ ondataavailable: ((event: { data: Blob }) => void) | null }> = []
  class FakeMediaRecorder {
    static isTypeSupported(): boolean { return true }
    state = 'inactive'
    ondataavailable: ((event: { data: Blob }) => void) | null = null
    constructor(public stream: unknown) {
      void this.stream
      instances.push(this as unknown as { ondataavailable: ((event: { data: Blob }) => void) | null })
    }
    start(): void { this.state = 'recording' }
    stop(): void { this.state = 'inactive' }
  }
  vi.stubGlobal('MediaRecorder', FakeMediaRecorder)
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia: vi.fn(async () => ({ getAudioTracks: () => [], getTracks: () => [] })) }
  })
  return { instances }
}

/** 詳細（`GET /classroom/{id}`）。転写セグメントは 2 文だけ入れておく（分塊は 7 個ある想定）。 */
function detail(): Record<string, unknown> {
  return {
    recordId: 12, recordNo: 'CR1', title: '数学', subject: '数学', languageMode: 'ja',
    presetId: null, presetName: null, presetText: null,
    status: 'RECORDING', statusLabel: '録音中', startTime: '2026-09-14T10:00:00', endTime: null,
    durationSeconds: null, transcribedChars: 20, summaryJson: null, hasAudio: true, audioMime: 'audio/webm',
    segments: [
      { segmentId: 1, seq: 1, startOffsetSeconds: 0, endOffsetSeconds: 20, speaker: '講義', text: 'あ', language: 'ja', createdAt: null },
      { segmentId: 2, seq: 2, startOffsetSeconds: 20, endOffsetSeconds: 40, speaker: '講義', text: 'い', language: 'ja', createdAt: null }
    ],
    notes: [], version: 2, createdAt: '2026-09-14T10:00:00', updatedAt: '2026-09-14T10:00:00'
  }
}

/** API のモック（分塊の一覧・アップロードの応答を差し替えられる）。 */
function mockApi(options: {
  chunkList?: Record<string, unknown>
  chunkUpload?: () => Response
} = {}): { calls: Call[]; fetchMock: ReturnType<typeof vi.fn> } {
  const calls: Call[] = []
  const ok = (data: unknown): Response => new Response(
    JSON.stringify({ success: true, code: 'OK', message: 'OK', data }),
    { status: 200, headers: { 'Content-Type': 'application/json' } }
  )
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target = String(url)
    const method = (init?.method ?? 'GET').toUpperCase()
    calls.push({
      url: target, method,
      form: init?.body instanceof FormData ? init.body : undefined
    })
    if (target.includes('/chunks') && method === 'GET') {
      return ok(options.chunkList ?? {
        items: [], chunkCount: 0, maxSeq: 0, nextSeq: 1, totalBytes: 0, recordedSeconds: null
      })
    }
    if (target.includes('/chunks')) {
      return options.chunkUpload === undefined
        ? ok({
            recordId: 12, seq: 8, nextSeq: 3, nextChunkSeq: 9, appendedSegments: [],
            pendingNoteId: null, triggered: false, status: 'RECORDING', runPath: null
          })
        : options.chunkUpload()
    }
    if (target.includes('/options')) {
      return ok({
        enabled: true, chunkSeconds: 20, maxRecordingMinutes: 120, retentionDays: 30,
        dailyLimit: 0, usedToday: 0, notice: '', sttMode: 'BROWSER', streamStt: false,
        sttLanguageCodes: { ja: 'ja-JP' }, noteEnabled: false
      })
    }
    if (target.includes('/start')) {
      return ok({ recordId: 12, recordNo: 'CR1', status: 'RECORDING', statusLabel: '録音中', version: 2 })
    }
    if (target.includes('/segments')) {
      return ok({ items: [], nextSeq: 3 })
    }
    return ok(detail())
  })
  vi.stubGlobal('fetch', fetchMock)
  return { calls, fetchMock }
}

describe('録音の分塊: 続きの連番は分塊表から取る', () => {
  beforeEach(() => {
    vi.unstubAllGlobals()
  })

  it('開き直して録り直すとき、次に送る分塊の連番は分塊表（nextSeq）から取る（転写の連番ではない）', async () => {
    const { instances } = installFakeRecorder()
    // 分塊は 7 個保存済み（転写は 2 文だけ）。録音の位置は 140.5 秒
    const { calls } = mockApi({
      chunkList: {
        items: [], chunkCount: 7, maxSeq: 7, nextSeq: 8, totalBytes: 4000, recordedSeconds: 140.5
      }
    })
    const wrapper = await mountLive()
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    instances[0]?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    const upload = calls.find((call) => call.method === 'POST' && call.url.includes('/chunks'))
    // 転写は 2 文だが、分塊は 7 個ある → 続きは 8 番（2 番や 3 番にしない）
    expect(upload?.url).toContain('seq=8')
    // 経過秒も保存済みの位置から続ける（0 に戻すと前の録音と時系列が重なる）
    expect(upload?.url).toContain('startSeconds=140.5')
    // 一覧は GET で取りに行く（アップロードの POST とは別の呼び出し）
    expect(calls.some((call) => call.method === 'GET' && call.url.includes('/chunks'))).toBe(true)
  })

  it('分塊の状態が読めないときは録音を始めない（0 から送って音を捨てない）', async () => {
    installFakeRecorder()
    const { fetchMock } = mockApi()
    fetchMock.mockImplementation(async (url: string, init?: RequestInit) => {
      const target = String(url)
      const method = (init?.method ?? 'GET').toUpperCase()
      if (target.includes('/chunks') && method === 'GET') {
        return new Response(
          JSON.stringify({ success: false, code: 'INTERNAL_ERROR', message: 'サーバーでエラーが発生しました。', data: null }),
          { status: 500, headers: { 'Content-Type': 'application/json' } }
        )
      }
      return new Response(
        JSON.stringify({
          success: true, code: 'OK', message: 'OK',
          data: method === 'GET' && target.includes('/options')
            ? {
                enabled: true, chunkSeconds: 20, maxRecordingMinutes: 120, retentionDays: 30,
                dailyLimit: 0, usedToday: 0, notice: '', sttMode: 'BROWSER', streamStt: false,
                sttLanguageCodes: { ja: 'ja-JP' }, noteEnabled: false
              }
            : (target.includes('/start')
                ? { recordId: 12, recordNo: 'CR1', status: 'RECORDING', statusLabel: '録音中', version: 2 }
                : detail())
        }),
        { status: 200, headers: { 'Content-Type': 'application/json' } }
      )
    })

    const wrapper = await mountLive()
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    // 録音中にしない（マイクも動かさない）＋理由を出す
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(false)
    expect(wrapper.get('[data-cr-recorder-notice]').text()).toContain('保存済みの音声の状態')
  })

  it('同じ連番に違う内容（409）は、その場で理由を出して送り直さない', async () => {
    const { instances } = installFakeRecorder()
    mockApi({
      chunkUpload: () => new Response(
        JSON.stringify({
          success: false, code: 'CONFLICT',
          message: '同じ連番（1）に違う内容の音声が届きました。保存済みの分塊は書き換えていません。',
          data: null
        }),
        { status: 409, headers: { 'Content-Type': 'application/json' } }
      )
    })
    const wrapper = await mountLive()
    await wrapper.get('[data-cr-start-recording]').trigger('click')
    await flushPromises()

    instances[0]?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    expect(wrapper.text()).toContain('違う内容の音声')
    // 4xx は待ち行列に入れない（何度送っても通らない）
    expect(wrapper.find('[data-cr-pending-uploads]').exists()).toBe(false)
  })
})

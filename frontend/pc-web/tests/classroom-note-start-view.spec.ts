import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ClassroomLiveView from '@/views/classroom/ClassroomLiveView.vue'

/**
 * **最終まとめの起動**の検証（利用者の指摘 ②）。
 *
 * <p>以前は「投げて、待たずに `noteStarted=true`」にしていた。応答が失敗しても画面は起動済みと
 * 見なし、そのまま詳細へ進んでいた（利用者には**見えない失敗**）。ここでは</p>
 * <ol>
 *   <li>受理の応答が返るまで「始めた」と印を付けない、</li>
 *   <li>起動に失敗しても**録音の保存は取り消さない**（音は残っている）、</li>
 *   <li>失敗を画面に残す（成功のトーストで済ませない）、</li>
 *   <li>受理の応答を失ったときは記録を問い合わせ、**二つ目のタスクを作らない**、</li>
 * </ol>
 * <p>を固定する。</p>
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

type Call = { url: string; method: string; body: Record<string, unknown> | null }

function classroomRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Dummy },
      { path: '/:area/classroom', component: Dummy },
      { path: '/:area/classroom/:id/live', component: Dummy },
      { path: '/:area/classroom/:id', component: Dummy }
    ]
  })
}

/** 録音の道具（音は流さない）。 */
function stubRecorder(): void {
  class FakeMediaRecorder {
    state = 'inactive'
    ondataavailable: ((event: { data: Blob }) => void) | null = null
    private listeners: Record<string, (() => void)[]> = {}
    constructor(public stream: unknown) { void this.stream }
    static isTypeSupported(): boolean { return true }
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
  vi.stubGlobal('AudioContext', class {
    sampleRate = 48_000
    state = 'running'
    destination = {}
    audioWorklet = { addModule: vi.fn(async () => undefined) }
    createMediaStreamSource(): unknown { return { connect: () => undefined, disconnect: () => undefined } }
    createGain(): unknown { return { gain: { value: 1 }, connect: () => undefined, disconnect: () => undefined } }
    createMediaStreamDestination(): unknown { return { stream: { id: 'mixed' } } }
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
  })
  vi.stubGlobal('AudioWorkletNode', class {
    port = { onmessage: null as unknown, close: () => undefined }
    connect(): void { /* 何もしない */ }
    disconnect(): void { /* 何もしない */ }
  })
  vi.stubGlobal('WebSocket', undefined)
  const track = { stop: () => undefined, kind: 'audio', readyState: 'live' }
  const stream = () => ({
    getAudioTracks: () => [track], getVideoTracks: () => [], getTracks: () => [track]
  })
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia: vi.fn(async () => stream()), getDisplayMedia: vi.fn(async () => stream()) }
  })
}

/** 最終まとめの行（詳細の読み直しで返す）。 */
function finalNoteRow(status: string): Record<string, unknown> {
  return {
    noteId: 91, kind: 'FINAL', phaseNo: null, startSeq: 1, endSeq: 3, status,
    statusLabel: status === 'READY' ? 'できました' : status === 'FAILED' ? '失敗' : '作成中',
    noteJson: null, errorCode: status === 'FAILED' ? 'AI_ERROR' : null,
    errorMessage: status === 'FAILED' ? 'AI が応答しませんでした。' : null,
    createdAt: null, updatedAt: null
  }
}

interface Options {
  /** 起動の応答を**保留**する（受理の前に画面が何をするか）。 */
  holdNoteRun?: Promise<void>
  /** 起動を失敗させる（録音の保存は成功のまま）。 */
  noteRunFails?: boolean
  /** 詳細が返す最終まとめの行。 */
  notesForDetail?: Record<string, unknown>[]
  /**
   * `GET /classroom/12/notes/91`（**同じノートの状態**）の応答。
   *
   * <p>`null` は「問い合わせが失敗する（**確認できない**）」を表す。</p>
   */
  noteStatus?: { status: string; accepted: boolean; retryable: boolean } | null
}

function mockApi(options: Options = {}): { calls: Call[] } {
  const calls: Call[] = []
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    const body = typeof init?.body === 'string'
      ? (JSON.parse(init.body) as Record<string, unknown>) : null
    calls.push({ url: String(url), method, body })
    const ok = (data: unknown): Response => new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })
    const target = String(url)
    if (/\/api\/user\/classroom\/\d+\/notes\/\d+/.test(target)) {
      if (options.noteStatus === null) {
        return new Response(JSON.stringify({
          success: false, code: 'INTERNAL_ERROR', message: '確認できません', data: null
        }), { status: 500, headers: { 'Content-Type': 'application/json' } })
      }
      const state = options.noteStatus ?? { status: 'PENDING', accepted: false, retryable: true }
      return ok({
        noteId: 91, kind: 'FINAL', status: state.status, statusLabel: state.status,
        accepted: state.accepted, retryable: state.retryable,
        errorCode: state.status === 'FAILED' ? 'AI_ERROR' : null,
        errorMessage: state.status === 'FAILED' ? 'AI が応答しませんでした。' : null
      })
    }
    if (target.includes('/api/admin/batch/classroom/notes/')) {
      if (options.holdNoteRun !== undefined) await options.holdNoteRun
      if (options.noteRunFails === true) {
        return new Response(JSON.stringify({
          success: false, code: 'INTERNAL_ERROR', message: 'AI を開始できませんでした。', data: null
        }), { status: 500, headers: { 'Content-Type': 'application/json' } })
      }
      // **受理の応答**（AI の完了ではない）
      return ok({
        noteId: 91, accepted: true, status: 'GENERATING', message: '最終まとめの作成を始めました。'
      })
    }
    if (target.includes('/stt/stream/finish')) {
      return ok({
        interim: '', added: [], error: null,
        finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false,
        recovery: null, savedCount: 1, pendingCount: 0, notice: null
      })
    }
    if (target.includes('/options')) {
      return ok({
        enabled: true, chunkSeconds: 20, maxRecordingMinutes: 120, retentionDays: 30,
        dailyLimit: 0, usedToday: 0, notice: '', sttMode: 'SERVER', streamStt: true,
        sttLanguageCodes: { ja: 'ja-JP' }, noteEnabled: true
      })
    }
    if (target.includes('/chunks') && method === 'GET') {
      return ok({
        items: [], chunkCount: 0, maxSeq: 0, nextSeq: 1, totalBytes: 0, recordedSeconds: null,
        finalizeCheck: { complete: true, missingSeqs: [], storedChunks: 0, expectedChunks: 0, reason: null }
      })
    }
    if (target.includes('/chunks') && method === 'POST') {
      return ok({
        recordId: 12, seq: 1, nextSeq: 1, nextChunkSeq: 2, appendedSegments: [],
        pendingNoteId: null, triggered: false, status: 'RECORDING', runPath: null
      })
    }
    if (target.includes('/stt/stream')) {
      return ok({
        interim: '', added: [], error: null,
        finalizeStatus: 'AUDIO_ACCEPTING', finalizeCompleted: false, retryable: true,
        recovery: null, savedCount: 0, pendingCount: 0, notice: null
      })
    }
    if (target.includes('/start')) {
      return ok({ recordId: 12, recordNo: 'CR1', status: 'RECORDING', statusLabel: '録音中', version: 2 })
    }
    if (target.includes('/end')) {
      return ok({
        recordId: 12, status: 'STOPPED', statusLabel: '停止', finalNoteId: 91,
        runPath: '/api/admin/batch/classroom/notes/91/run', notice: null,
        complete: true, missingSeqs: [], forced: false, lossSeqs: [],
        transcribe: { status: 'COMPLETE', complete: true, retryable: false, reason: null, sources: [] }
      })
    }
    return ok({
      recordId: 12, recordNo: 'CR1', title: '数学', languageMode: 'ja', status: 'STOPPED',
      statusLabel: '停止', startTime: null, segments: [],
      notes: options.notesForDetail ?? [], summaryJson: null,
      hasAudio: true, audioMime: 'audio/webm', version: 2,
      transcribe: { status: 'COMPLETE', complete: true, retryable: false, reason: null, sources: [] }
    })
  }))
  return { calls }
}

const LIVE = '/student/classroom/12/live?name=数学&audioMode=mic&autostart=1'

/** 録音を始める（自動開始していれば何もしない）。 */
async function startRecording(wrapper: VueWrapper): Promise<void> {
  const start = wrapper.find('[data-cr-start-recording]')
  if (start.exists()) {
    await start.trigger('click')
    await flushPromises()
  }
}

async function open(): Promise<VueWrapper> {
  const router = classroomRouter()
  await router.push(LIVE)
  await router.isReady()
  const wrapper = mount(ClassroomLiveView as Component, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

beforeEach(() => {
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('授業録音：最終まとめの起動（受理を待つ）', () => {
  /** 受理された状態（`GENERATING`）。 */
  const generating = { status: 'GENERATING', accepted: true, retryable: false }

  it('受理の応答が返るまで「始めた」と見なさない（保留中は最終まとめの段階で待つ）', async () => {
    stubRecorder()
    let release: () => void = () => undefined
    const hold = new Promise<void>((resolve) => { release = resolve })
    const { calls } = mockApi({ holdNoteRun: hold, noteStatus: generating })
    const wrapper = await open()
    await startRecording(wrapper)

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    // 記録の終了は先に送られる（音の保存が先）
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(true)
    // 起動は保留中なので、まだ「終了しました」と言わない
    expect(wrapper.text()).not.toContain('録音を終了しました')

    release()
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/91/run')))
        .toBe(true)
    }, { timeout: 3000 })
  })

  it('起動が失敗しても録音の保存は残し、失敗を成功と言わない', async () => {
    stubRecorder()
    // 起動に失敗し、**記録にも行が無い**（＝タスクは本当に作られていない）
    const { calls } = mockApi({
      noteRunFails: true, notesForDetail: [], noteStatus: { status: 'PENDING', accepted: false, retryable: true }
    })
    const wrapper = await open()
    await startRecording(wrapper)
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/91/run')))
        .toBe(true)
    }, { timeout: 3000 })
    await flushPromises()

    // **録音の終了は成功している**（記録は終わっている・音は残っている）
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(true)
    // 失敗を隠さない（成功のトーストで済ませない）
    const text = wrapper.text()
    expect(text).toContain('最終まとめ')
    expect(text).toContain('開始できませんでした')
  })

  it('受理の応答を失っても、記録に最終まとめの行があれば二つ目のタスクを作らない', async () => {
    stubRecorder()
    // 起動の応答は 500（受理されたのに応答を失った状況に近い）
    const { calls } = mockApi({
      noteRunFails: true,
      notesForDetail: [finalNoteRow('GENERATING')],
      // 記録では**受理されている**（応答だけ失われた）
      noteStatus: generating
    })
    const wrapper = await open()
    await startRecording(wrapper)
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/91/run')))
        .toBe(true)
    }, { timeout: 3000 })
    await flushPromises()

    // 起動は 1 回だけ（記録に行があるので「もう一度起こす」とは判断しない）
    const noteRuns = calls.filter(
      (call) => call.url.includes('/api/admin/batch/classroom/notes/91/run'))
    expect(noteRuns).toHaveLength(1)
  })

  it('終了が PENDING を作っていても、起動が届いていなければ「始まった」と見なさない', async () => {
    stubRecorder()
    const { calls } = mockApi({
      // 起動の応答は 500（=届かなかった）
      noteRunFails: true,
      // 記録には**PENDING の FINAL 行がある**（user-api が先に作る）が、受理はされていない
      notesForDetail: [finalNoteRow('PENDING')],
      noteStatus: { status: 'PENDING', accepted: false, retryable: true }
    })
    const wrapper = await open()
    await startRecording(wrapper)
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/91/run')))
        .toBe(true)
    }, { timeout: 3000 })
    await flushPromises()

    // **「始めました」とは言わない**（PENDING は受理の証拠ではない）
    const text = wrapper.text()
    expect(text).toContain('まだ作成していません')
    expect(text).not.toContain('最終まとめの作成を始めました')
    // 録音の保存は残っている（音は失っていない）
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(true)
  })

  it('状態の問い合わせも失敗したら「確認できない」と出す（成功とも失敗とも決めない）', async () => {
    stubRecorder()
    const { calls } = mockApi({
      noteRunFails: true,
      notesForDetail: [finalNoteRow('PENDING')],
      // 状態の問い合わせも失敗する
      noteStatus: null
    })
    const wrapper = await open()
    await startRecording(wrapper)
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/91/run')))
        .toBe(true)
    }, { timeout: 3000 })
    await flushPromises()

    const text = wrapper.text()
    expect(text).toContain('確認できませんでした')
    expect(text).not.toContain('最終まとめの作成を始めました')
  })

  it('受理の応答を失っても、状態が FAILED なら失敗として出し、やり直せる', async () => {
    stubRecorder()
    const { calls } = mockApi({
      noteRunFails: true,
      notesForDetail: [finalNoteRow('FAILED')],
      noteStatus: { status: 'FAILED', accepted: false, retryable: true }
    })
    const wrapper = await open()
    await startRecording(wrapper)
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/api/admin/batch/classroom/notes/91/run')))
        .toBe(true)
    }, { timeout: 3000 })
    await flushPromises()

    // 失敗として出し、成功とは言わない（詳細画面にやり直しの入口がある）
    expect(wrapper.text()).not.toContain('最終まとめの作成を始めました')
    expect(wrapper.text()).toContain('最終まとめ')
  })
})

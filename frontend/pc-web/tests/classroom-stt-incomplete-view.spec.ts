import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ClassroomLiveView from '@/views/classroom/ClassroomLiveView.vue'

/**
 * **書き起こしの収尾の結果が、画面の終了判断と案内に正しく効くか**の検証（利用者の指摘 ①）。
 *
 * <p>見張るのは「`error` が無い＝成功」という誤読。後端は**やり直しても直らない不完整な終わり**
 * を `error=null` / `finalizeCompleted=false` / `retryable=false` で返す。これを成功と読むと、
 * 書き起こしが欠けたまま「録音と書き起こしを保存しました」と言ってしまう。</p>
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
  /*
   * 二音源（mic-pc）はマイク＋共有の音を取る。**共有に音声トラックがある**形にしないと
   * 画面は「共有の音が入っていません」として録音を始めない（実際の警告もそこ）。
   */
  const fakeTrack = { stop: () => undefined, kind: 'audio', readyState: 'live' }
  const fakeStream = () => ({
    getAudioTracks: () => [fakeTrack],
    getVideoTracks: () => [],
    getTracks: () => [fakeTrack]
  })
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: {
      getUserMedia: vi.fn(async () => fakeStream()),
      getDisplayMedia: vi.fn(async () => fakeStream())
    }
  })
}

/** 収尾の応答（`/stt/stream/finish`）を差し替えられる API のモック。 */
function mockApi(options: { finish?: Record<string, unknown>[] } = {}): {
  calls: Call[]
  finishCalls: () => number
} {
  const calls: Call[] = []
  let finishCalls = 0
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    const body = typeof init?.body === 'string'
      ? (JSON.parse(init.body) as Record<string, unknown>) : null
    calls.push({ url: String(url), method, body })
    const ok = (data: unknown): Response => new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })
    const target = String(url)
    if (target.includes('/stt/stream/finish')) {
      const scripted = options.finish?.[Math.min(finishCalls, (options.finish?.length ?? 1) - 1)]
      finishCalls += 1
      return ok(scripted ?? {
        interim: '', added: [], error: null,
        finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false,
        recovery: null, savedCount: 1, pendingCount: 0, notice: null
      })
    }
    if (target.includes('/options')) {
      return ok({
        enabled: true, chunkSeconds: 20, maxRecordingMinutes: 120, retentionDays: 30,
        dailyLimit: 0, usedToday: 0, notice: '', sttMode: 'SERVER', streamStt: true,
        sttLanguageCodes: { ja: 'ja-JP' }, noteEnabled: false
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
        recordId: 12, status: 'STOPPED', statusLabel: '停止', finalNoteId: null, runPath: null,
        notice: null, complete: true, missingSeqs: [], forced: false
      })
    }
    return ok({
      recordId: 12, recordNo: 'CR1', title: '数学', languageMode: 'ja', status: 'RECORDING',
      statusLabel: '録音中', startTime: null, segments: [], notes: [], summaryJson: null,
      hasAudio: false, audioMime: null, version: 2
    })
  }))
  return { calls, finishCalls: () => finishCalls }
}

/** 音源つきで画面を開く（`audioMode=mic-pc` で二音源）。 */
async function open(audioMode = 'mic-pc'): Promise<VueWrapper> {
  const router = classroomRouter()
  await router.push(`/student/classroom/12/live?name=数学&audioMode=${audioMode}&autostart=1`)
  await router.isReady()
  const wrapper = mount(ClassroomLiveView as Component, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

/** 停止 → 収尾が済むまで待つ（終了の入口が押せる状態にする）。 */
async function stopAndSettle(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('[data-cr-stop-recording]').trigger('click')
  await flushPromises()
  await vi.waitFor(() => {
    const button = wrapper.find('[data-cr-finish]')
    expect(button.exists()).toBe(true)
    expect((button.element as HTMLButtonElement).disabled).toBe(false)
  }, { timeout: 5000 })
}

beforeEach(() => {
  localStorage.clear()
  vi.unstubAllGlobals()
})

describe('授業録音：書き起こしが不完全なまま終わる回', () => {
  it('収尾が「やり直しても直らない不完整な終わり」なら、終える前に知らせて確認を取る（単音源）', async () => {
    stubRecorder()
    const { calls } = mockApi({
      finish: [
        // 収尾の応答は error=null だが**成功ではない**（やり直しても直らない終端）
        {
          interim: '', added: [], error: null,
          finalizeStatus: 'FAILED', finalizeCompleted: false, retryable: false,
          recovery: null, savedCount: 1, pendingCount: 0,
          notice: '認識の尾部を取り切れませんでした（やり直しても直りません）。'
        }
      ]
    })
    const wrapper = await open('mic')
    await stopAndSettle(wrapper)

    // 停止の収尾で既に不完全と分かるので、**止めて確認する**
    const notice = wrapper.get('[data-cr-stt-incomplete]')
    expect(notice.text()).toContain('やり直しても直りません')
    expect(notice.text()).toContain('続きをやり直す')
    // **失敗として無限に再試行させない**（やり直しても直らない）
    expect(wrapper.get('[data-cr-status-code]').attributes('data-cr-status-code')).not.toBe('saving')

    // 終了を押しても、確認するまでは記録を終えない
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(false)

    // 承知したうえで終える → 記録の終了が送られる
    await wrapper.get('[data-cr-stt-incomplete-ok]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect(calls.some((call) => call.url.includes('/classroom/12/end'))).toBe(true)
    }, { timeout: 5000 })
  })

  it('書き起こしが不完全でも、録音の欠落とは別の文言で出す（「音声は保存されています」と言い切らない・単音源）', async () => {
    stubRecorder()
    mockApi({
      finish: [{
        interim: '', added: [], error: null,
        finalizeStatus: 'FAILED', finalizeCompleted: false, retryable: false,
        savedCount: 1, pendingCount: 0, notice: '発話の尾部を取り切れませんでした。'
      }]
    })
    const wrapper = await open('mic')
    await stopAndSettle(wrapper)

    const text = wrapper.text()
    // 録音の保存はできている（音は失っていない）が、**書き起こしは不完全**と分けて出す
    expect(text).toContain('書き起こしの一部')
    expect(text).not.toContain('音声の一部が保存できていません')
    // 詳細情報に音源ごとの理由が入る
    expect(wrapper.get('[data-cr-stt-incomplete-notice]').text()).toContain('尾部')
  })

  it('収尾が「やり直せる失敗」なら、今までどおり【再試行】を出して終わらせない（単音源）', async () => {
    stubRecorder()
    const { calls } = mockApi({
      finish: [
        {
          interim: '', added: [], error: '保存できなかった文があります。',
          finalizeStatus: 'FAILED', finalizeCompleted: false, retryable: true,
          recovery: 'RESAVE_PENDING', savedCount: 1, pendingCount: 2, notice: null
        },
        // やり直しでは成功する
        {
          interim: '', added: [], error: null,
          finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false,
          savedCount: 3, pendingCount: 0, notice: null
        }
      ]
    })
    const wrapper = await open('mic')
    await wrapper.get('[data-cr-stop-recording]').trigger('click')
    await flushPromises()

    // 失敗として出し、やり直しの入口を出す
    await vi.waitFor(() => {
      expect(wrapper.find('[data-cr-retry-finalize]').exists()).toBe(true)
    }, { timeout: 5000 })
    expect(wrapper.get('[data-cr-status-code]').attributes('data-cr-status-code')).toBe('retry')
    // **不完全の確認は出さない**（やり直せば直るので、あきらめさせない）
    expect(wrapper.find('[data-cr-stt-incomplete]').exists()).toBe(false)

    // やり直すと成功し、終了の入口が押せる
    await wrapper.get('[data-cr-retry-finalize]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      const button = wrapper.find('[data-cr-finish]')
      expect(button.exists()).toBe(true)
      expect((button.element as HTMLButtonElement).disabled).toBe(false)
    }, { timeout: 5000 })
    expect(wrapper.find('[data-cr-stt-incomplete]').exists()).toBe(false)
    // 音声（分塊）は送り直していない＝収尾だけをやり直している
    const chunkPosts = calls.filter(
      (call) => call.url.includes('/chunks') && call.method === 'POST').length
    expect(chunkPosts).toBeLessThanOrEqual(1)
  })

  it('単音源の回は、使っていない音源を待たない（未使用の「音声なし」で失敗にしない）', async () => {
    stubRecorder()
    const { calls } = mockApi({
      finish: [
        {
          interim: '', added: [], error: null,
          finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false,
          savedCount: 2, pendingCount: 0, notice: null
        }
      ]
    })
    const wrapper = await open('mic')
    await stopAndSettle(wrapper)

    // マイクの収尾だけが送られる（共有の音は使っていない）
    const finishUrls = calls.filter((call) => call.url.includes('/stt/stream/finish'))
      .map((call) => call.url)
    expect(finishUrls.some((url) => url.includes('source=mic'))).toBe(true)
    expect(finishUrls.some((url) => url.includes('source=shared'))).toBe(false)

    // 不完全の案内は出ない（未使用の音源を失敗として数えない）
    expect(wrapper.find('[data-cr-stt-incomplete]').exists()).toBe(false)
    expect(wrapper.get('[data-cr-status-title]').text()).toContain('授業を終了できます')
  })
})

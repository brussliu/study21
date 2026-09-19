import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ClassroomLiveView from '@/views/classroom/ClassroomLiveView.vue'

/**
 * 授業録音の**保存の完了と終了の判断**（利用者の指摘③）の検証。
 *
 * <p>見張るのは:</p>
 * <ol>
 *   <li>停止したら、**最後の分塊まで送り切ってから**「送った分塊の一覧（連番の範囲）」を
 *       添えて終了を送る（最後の分塊が届いていないまま終えない）。</li>
 *   <li>サーバーが「欠けている連番」を返したら、**その連番を送り直せる**（終了しない）。</li>
 *   <li>音声が全部そろっていると確認できるまで「音声は保存されています」と言わない。</li>
 *   <li>明示の「不完全なまま終了」は、利用者が押したときだけ送る。</li>
 * </ol>
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

/** 録音に必要な代役（音は流さない）。 */
function stubRecorder(): void {
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
      // 最後の分塊は `stop` の**前**に届く（画面はこれを送り切ってから終了を送る）
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
  Object.defineProperty(navigator, 'mediaDevices', {
    configurable: true,
    value: { getUserMedia: vi.fn(async () => ({ getAudioTracks: () => [], getTracks: () => [] })) }
  })
}

interface MockOptions {
  /** `POST /end` の 1 回目の応答（欠落を返すなど）。 */
  endResponses?: Record<string, unknown>[]
  /**
   * 分塊の送信（`POST /chunks`）を**保留**する。
   * 「保存の途中」の見え方（状態・押せないこと）を確かめるのに使う。
   */
  holdChunks?: Promise<void>
  /** `GET /chunks` の応答。 */
  chunkList?: Record<string, unknown>
}

/** `/api/user/classroom` のモック（呼び出しを記録する）。 */
function mockApi(options: MockOptions = {}): { calls: Call[] } {
  const calls: Call[] = []
  let endCalls = 0
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    const body = typeof init?.body === 'string'
      ? (JSON.parse(init.body) as Record<string, unknown>) : null
    calls.push({ url: String(url), method, body })
    const ok = (data: unknown): Response => new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })
    const target = String(url)
    if (target.includes('/options')) {
      return ok({
        enabled: true, chunkSeconds: 20, maxRecordingMinutes: 120, retentionDays: 30,
        dailyLimit: 0, usedToday: 0, notice: '', sttMode: 'SERVER', streamStt: true,
        sttLanguageCodes: { ja: 'ja-JP' }, noteEnabled: false
      })
    }
    if (target.includes('/chunks') && method === 'GET') {
      return ok(options.chunkList ?? {
        items: [], chunkCount: 0, maxSeq: 0, nextSeq: 1, totalBytes: 0, recordedSeconds: null,
        finalizeCheck: { complete: false, missingSeqs: [], storedChunks: 0, expectedChunks: 0, reason: '' }
      })
    }
    if (target.includes('/chunks') && method === 'POST') {
      if (options.holdChunks !== undefined) await options.holdChunks
      return ok({
        recordId: 12, seq: 1, nextSeq: 1, nextChunkSeq: 2, appendedSegments: [],
        pendingNoteId: null, triggered: false, status: 'RECORDING', runPath: null
      })
    }
    if (target.includes('/stt/stream')) {
      return ok({ interim: '', added: [], error: null })
    }
    if (target.includes('/start')) {
      return ok({ recordId: 12, recordNo: 'CR1', status: 'RECORDING', statusLabel: '録音中', version: 2 })
    }
    if (target.includes('/end')) {
      endCalls += 1
      const scripted = options.endResponses?.[endCalls - 1]
      if (scripted !== undefined) {
        return new Response(JSON.stringify({
          success: false, code: 'CONFLICT',
          message: String(scripted.message ?? '欠けています'),
          // **構造化した一覧**（画面はこの値で判断する。文面では判断しない）
          data: scripted.data ?? null
        }), { status: Number(scripted.status ?? 409), headers: { 'Content-Type': 'application/json' } })
      }
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
  return { calls }
}

const LIVE = '/student/classroom/12/live?name=数学&audioMode=mic&autostart=1'

/** 画面を開く（API のモックは呼び側が先に入れる）。 */
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

describe('授業録音：停止 → 保存の完了 → 終了の判断', () => {
  it('停止したら、最後の分塊まで送り切ってから終了する（分塊の範囲を添える）', async () => {
    stubRecorder()
    // 保存が終わるまで待たせる（「保存の途中」の見え方を確かめる）
    let release: () => void = () => undefined
    const hold = new Promise<void>((resolve) => { release = resolve })
    const { calls } = mockApi({ holdChunks: hold })
    const wrapper = await open()
    // URL に autostart=1 があるので、開いた時点で録音が始まっている
    expect(wrapper.find('[data-cr-stop-recording]').exists()).toBe(true)

    await wrapper.get('[data-cr-stop-recording]').trigger('click')
    await flushPromises()

    // ① 停止した**その場**で「止めた」と分かる（赤い警告ではなく、いまの状態として出す）
    expect(wrapper.get('[data-cr-recording-status]').text()).toContain('停止中')
    // ② 保存が終わるまで、終了の入口は押せない（二度押しを防ぐ）＋ 待っている中身を出す
    expect((wrapper.get('[data-cr-finish]').element as HTMLButtonElement).disabled).toBe(true)
    expect(wrapper.get('[data-cr-status-title]').text()).toContain('音声を保存')
    expect(wrapper.get('[data-cr-finalize-phase]').text()).not.toBe('')

    // ③ 保存が終わったら「授業を終了できます」に変わり、終了の入口が押せる
    release()
    await vi.waitFor(() => {
      expect(wrapper.get('[data-cr-status-title]').text()).toContain('授業を終了できます')
    }, { timeout: 3000 })
    expect(wrapper.get('[data-cr-status]').attributes('data-cr-status-code')).toBe('ready')
    // 送れずに残っている音声は無い
    expect(wrapper.find('[data-cr-pending-uploads]').exists()).toBe(false)
    await vi.waitFor(() => {
      expect((wrapper.get('[data-cr-finish]').element as HTMLButtonElement).disabled).toBe(false)
    }, { timeout: 3000 })

    // 分塊は送り切っている（最後の分塊も含む）
    expect(calls.some((call) => call.url.includes('/chunks') && call.method === 'POST')).toBe(true)

    // 終了には「送った分塊の範囲」を添える
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()
    const endCall = calls.find((call) => call.url.includes('/end'))
    expect(endCall?.body).toBeTruthy()
    expect((endCall?.body as { manifest?: unknown }).manifest).toMatchObject({ lastSeq: 1, totalCount: 1 })
  })

  it('サーバーが欠けている連番を返したら、その分塊だけ送り直して終了しない', async () => {
    stubRecorder()
    mockApi({
      endResponses: [{ status: 409, message: '録音の音声（分塊）がそろっていません。欠けている連番: [2]' }]
    })
    const wrapper = await open()
    await wrapper.get('[data-cr-stop-recording]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect((wrapper.get('[data-cr-finish]').element as HTMLButtonElement).disabled).toBe(false)
    }, { timeout: 3000 })

    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()

    // 失敗として出す（詳細へ進まない）＋ 再試行の入口（状態欄に集約）
    expect(wrapper.get('[data-cr-status-code]').attributes('data-cr-status-code')).toBe('retry')
    expect(wrapper.get('[data-cr-retry-finalize]').text()).toContain('再試行')
    // **「音声は保存されています」とは言わない**（全部そろったことを確かめていない）
    expect(wrapper.html()).not.toContain('音声は保存されています')
  })

  it('明示の「不完全なまま終了」は利用者が押したときだけ送る', async () => {
    stubRecorder()
    const { calls } = mockApi({
      endResponses: [{
        status: 409,
        message: '録音の音声（分塊）がそろっていません。',
        // 文面ではなく**この構造**で判断する
        data: {
          complete: false, missingSeqs: [2], storedChunks: 1, expectedChunks: 2,
          reason: '分塊が 1 件足りません（連番 [2]）。'
        }
      }],
      chunkList: {
        items: [], chunkCount: 1, maxSeq: 1, nextSeq: 2, totalBytes: 100, recordedSeconds: 1,
        finalizeCheck: {
          complete: false, missingSeqs: [2], storedChunks: 1, expectedChunks: 2,
          reason: '分塊が 1 件足りません（連番 [2]）。'
        }
      }
    })
    const wrapper = await open()
    await wrapper.get('[data-cr-stop-recording]').trigger('click')
    await flushPromises()
    await vi.waitFor(() => {
      expect((wrapper.get('[data-cr-finish]').element as HTMLButtonElement).disabled).toBe(false)
    }, { timeout: 3000 })
    await wrapper.get('[data-cr-finish]').trigger('click')
    await flushPromises()

    // 影響を明示したうえで、別の入口として出す
    const incomplete = wrapper.get('[data-cr-finish-incomplete]')
    expect(incomplete.text()).toContain('不完全')
    await incomplete.trigger('click')
    await flushPromises()

    // **確認の 1 段**を挟む（押し間違いで音を失わない）＋ 失う範囲を出す
    const confirm = wrapper.get('[data-cr-incomplete-confirm]')
    expect(confirm.text()).toContain('2')
    // 確認するまでは送らない
    expect(calls.filter((call) => call.url.includes('/end'))).toHaveLength(1)

    await wrapper.get('[data-cr-incomplete-ok]').trigger('click')
    await flushPromises()

    const endCalls = calls.filter((call) => call.url.includes('/end'))
    expect(endCalls).toHaveLength(2)
    expect(endCalls[1]?.body).toMatchObject({ force: true })
  })

  it('未送信の分塊が残っているあいだは、画面を離れるときに確認する', async () => {
    stubRecorder()
    mockApi()
    await open()

    // 録音中は離脱を止める（未アップロードの音を持ったまま消えない）
    const event = new Event('beforeunload', { cancelable: true })
    window.dispatchEvent(event)
    expect(event.defaultPrevented).toBe(true)
  })
})

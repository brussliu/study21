import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, type Component } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import ClassroomLiveView from '@/views/classroom/ClassroomLiveView.vue'

/**
 * 授業録音の**「実際に録れた分塊」と「送れた分塊」**の検証（利用者の指摘①）。
 *
 * <p>見張るのは:</p>
 * <ol>
 *   <li>分塊を**作った時点**で「録れた」ことを数える（最後の分塊の送信が失敗しても
 *       サーバーが取りこぼしを見つけられる）。</li>
 *   <li>「送れた数」は別に数える（録れた数と混ぜない）。</li>
 *   <li>4xx（何度送っても通らない）分塊は**黙って捨てず**、失う音声として残す。</li>
 *   <li>開き直しても、録れた範囲が分かる。</li>
 * </ol>
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

type Call = { url: string; method: string; body: Record<string, unknown> | null }

function classroomRouter(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/', component: Dummy },
      { path: '/login', component: Dummy },
      { path: '/student/home', component: Dummy },
      { path: '/:area/classroom', component: Dummy },
      { path: '/:area/classroom/:id/live', component: Dummy },
      { path: '/:area/classroom/:id', component: Dummy },
      // 認証のガードが飛ばす先（この画面のテストでは使わないが、描画先を用意しておく）
      { path: '/:pathMatch(.*)*', component: Dummy }
    ]
  })
}

/** 録音の道具の代役。`stop()` で**最後の分塊**を 1 つ出す。 */
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
      this.ondataavailable?.({ data: new Blob([new Uint8Array([9, 9, 9])], { type: 'audio/webm' }) })
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
  /** 分塊のアップロード（`POST /chunks`）を**何回目から失敗**させるか（1 始まり）。 */
  chunkFailFrom?: number
  /** 失敗の種類: `'server'`（5xx＝送り直せる）/ `'client'`（4xx＝もう直らない）。 */
  chunkFailKind?: 'server' | 'client'
  /** `GET /chunks` の応答（開き直しの状態）。 */
  chunkList?: Record<string, unknown>
  /** `POST /end` の応答（1 回目だけ差し替える）。 */
  endResponses?: Record<string, unknown>[]
}

function mockApi(options: MockOptions = {}): { calls: Call[] } {
  const calls: Call[] = []
  let chunkPosts = 0
  let endCalls = 0
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    const body = typeof init?.body === 'string'
      ? (JSON.parse(init.body) as Record<string, unknown>) : null
    calls.push({ url: String(url), method, body })
    const ok = (data: unknown): Response => new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })
    const fail = (status: number, message: string, data: unknown = null): Response => new Response(
      JSON.stringify({ success: false, code: 'ERROR', message, data, timestamp: '' }),
      { status, headers: { 'Content-Type': 'application/json' } })
    /** 409（同じ連番に違う内容）は、後端と同じコードで返す（画面はコードで「直らない」を判断する）。 */
    const failConflict = (message: string): Response => new Response(
      JSON.stringify({ success: false, code: 'CONFLICT', message, data: null, timestamp: '' }),
      { status: 409, headers: { 'Content-Type': 'application/json' } })
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
        finalizeCheck: { complete: true, missingSeqs: [], storedChunks: 0, expectedChunks: 0, reason: null }
      })
    }
    if (target.includes('/chunks') && method === 'POST') {
      chunkPosts += 1
      if (options.chunkFailFrom !== undefined && chunkPosts >= options.chunkFailFrom) {
        return options.chunkFailKind === 'client'
          ? failConflict('同じ連番に違う内容の音声が届きました。')
          : fail(500, 'サーバーでエラーが発生しました。')
      }
      return ok({
        recordId: 12, seq: chunkPosts, nextSeq: 1, nextChunkSeq: chunkPosts + 1,
        appendedSegments: [], pendingNoteId: null, triggered: false, status: 'RECORDING', runPath: null
      })
    }
    if (target.includes('/stt/stream')) {
      // 後端は収尾の欄も返す（省くと画面は「確認できない」と見て成功と判定しない）
      return ok({
        interim: '', added: [], error: null,
        finalizeStatus: 'SAVED', finalizeCompleted: true, retryable: false,
        recovery: null, savedCount: 1, pendingCount: 0, notice: null
      })
    }
    if (target.includes('/start')) {
      return ok({ recordId: 12, recordNo: 'CR1', status: 'RECORDING', statusLabel: '録音中', version: 2 })
    }
    if (target.includes('/end')) {
      endCalls += 1
      const scripted = options.endResponses?.[endCalls - 1]
      if (scripted !== undefined) {
        return fail(Number(scripted.status ?? 409), String(scripted.message ?? 'そろっていません'),
          scripted.data ?? null)
      }
      return ok({
        recordId: 12, status: 'STOPPED', statusLabel: '停止', finalNoteId: null, runPath: null,
        notice: null, complete: true, missingSeqs: [], forced: false
      })
    }
    return ok({
      recordId: 12, recordNo: 'CR1', title: '数学', languageMode: 'ja', status: 'RECORDING',
      statusLabel: '録音中', startTime: null, segments: [], notes: [], summaryJson: null,
      hasAudio: false, audioMime: null, version: 2,
      assembly: { state: 'NONE', complete: false, storedChunks: 0, durationSeconds: null, missingSeqs: [], reason: null }
    })
  }))
  return { calls }
}

// 日本語は**エンコードして**渡す（ルーターがクエリを復号するときに壊れないように）
const LIVE = `/student/classroom/12/live?name=${encodeURIComponent('数学')}&audioMode=mic&autostart=1`

async function open(): Promise<{ wrapper: VueWrapper; router: Router }> {
  const router = classroomRouter()
  // 戻る先を履歴に積んでおく（ブラウザの戻るを確かめるため）
  await router.push('/student/home')
  await router.push(LIVE)
  await router.isReady()
  const wrapper = mount(ClassroomLiveView as Component, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

/**
 * 画面を開く（**ルーターつき**）。
 *
 * <p>站内の移動のガード（`onBeforeRouteLeave`）は、画面がルーターに載っていれば動く。
 * テストは `wrapper` から要素を探し、`router` で遷移を確かめる。</p>
 */
async function openViaRouter(): Promise<{ wrapper: VueWrapper; router: Router }> {
  // ログイン済みにする（本物のルーターは認証のガードを通る）
  window.sessionStorage.setItem('study21.auth.v2', JSON.stringify({ username: '確認用', role: 'STUDENT' }))
  return open()
}

/** 詳細情報を開いて、録れた数／送れた数（と失う連番）を読む。 */
async function manifestText(wrapper: VueWrapper): Promise<string> {
  await wrapper.get('[data-cr-details-toggle]').trigger('click')
  await flushPromises()
  const counts = wrapper.get('[data-cr-manifest-counts]').text()
  const missing = wrapper.find('[data-cr-details-missing]')
  return missing.exists() ? `${counts} / ${missing.text()}` : counts
}

beforeEach(() => {
  localStorage.clear()
  window.sessionStorage.clear()
  vi.unstubAllGlobals()
})

describe('授業録音：画面を離れるときの保護（站内の移動も止める）', () => {
  /** 未保存の音声が残っている状態を作る（1 回目のアップロードを失敗させる）。 */
  /**
   * 未保存の音声が残っている状態で画面を開く（一覧を残したまま開き直したのと同じ状態）。
   *
   * <p>ここで見るのは**ガードと遷移**だけなので、録音の道具は使わない
   * （送れていない音声があることは、記録に残した一覧で表せる）。</p>
   */
  async function withPendingChunk(): Promise<{ wrapper: VueWrapper; router: Router }> {
    mockApi()
    window.localStorage.setItem('study21.classroom.chunkManifest.12', JSON.stringify({
      expectedLastSeq: 2, expectedCount: 2, uploadedSeqs: [1], lastSeq: 1, totalCount: 1
    }))
    return openViaRouter()
  }

  it('送れていない音声があるあいだは、一覧へ戻るのを止めて確認する', async () => {
    const { wrapper, router } = await withPendingChunk()

    await wrapper.get('[data-cr-back-list]').trigger('click')
    await flushPromises()
    await new Promise((resolve) => window.setTimeout(resolve, 20))
    await flushPromises()

    // **移動しない**（確認の答えを待つ）
    expect(router.currentRoute.value.path).toBe('/student/classroom/12/live')
    expect(wrapper.find('[data-cr-live]').exists()).toBe(true)
    // 何が残っているかを書いた確認を出す
    const confirm = wrapper.get('[data-cr-leave-confirm]')
    expect(confirm.text()).toContain('送れていない音声')
  })

  it('ブラウザの戻る（站内のガード）でも同じ確認を出す', async () => {
    const { wrapper, router } = await withPendingChunk()
    // ブラウザの戻る = ルーターの移動（`beforeunload` では守れない経路）
    // ※ 戻る先は `open()` の中で履歴に積んである
    router.back()
    await flushPromises()
    await new Promise((resolve) => window.setTimeout(resolve, 20))
    await flushPromises()

    expect(wrapper.find('[data-cr-leave-confirm]').exists()).toBe(true)
    expect(router.currentRoute.value.path).toBe('/student/classroom/12/live')
  })

  it('【この画面に残る】を選んだら移動しない', async () => {
    const { wrapper, router } = await withPendingChunk()
    await wrapper.get('[data-cr-back-list]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-cr-leave-stay]').trigger('click')
    await flushPromises()
    await new Promise((resolve) => window.setTimeout(resolve, 20))
    await flushPromises()

    expect(wrapper.find('[data-cr-leave-confirm]').exists()).toBe(false)
    expect(router.currentRoute.value.path).toBe('/student/classroom/12/live')
    expect(wrapper.find('[data-cr-live]').exists()).toBe(true)
  })

  it('【保存せずに移動】を選んだときだけ、そのまま移動する', async () => {
    const { wrapper, router } = await withPendingChunk()
    await wrapper.get('[data-cr-back-list]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-cr-leave-now]').trigger('click')
    await flushPromises()
    await new Promise((resolve) => window.setTimeout(resolve, 20))
    await flushPromises()

    // 一覧へ移動している
    expect(router.currentRoute.value.path).toBe('/student/classroom')
  })
})

describe('授業録音：録れた分塊と送れた分塊（最終一覧）', () => {
  it('最後の分塊の送信が失敗しても、録れた数には残る（サーバーが取りこぼしを見つけられる）', async () => {
    stubRecorder()
    // 2 回目のアップロード（＝停止のときの最後の分塊）から失敗させる
    const { calls } = mockApi({ chunkFailFrom: 2 })
    const { wrapper } = await open()

    // 1 つ目の分塊は普通に届く（分塊の区切りを 1 回起こす）
    const recorder = (globalThis as unknown as {
      MediaRecorder: { instances: { ondataavailable: ((event: { data: Blob }) => void) | null }[] }
    }).MediaRecorder.instances[0]
    recorder?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    // 停止 → 最後の分塊（失敗する）
    await wrapper.get('[data-cr-stop-recording]').trigger('click')
    await flushPromises()

    // **録れた数は 2**（送れたのは 1）。送れた数で代用しない
    const counts = await manifestText(wrapper)
    expect(counts).toContain('2 件 / 1 件')
    // 送れなかった分塊は待ち行列に残る（黙って捨てない）
    expect(wrapper.find('[data-cr-pending-uploads]').exists()).toBe(true)
    expect(calls.filter((call) => call.url.includes('/chunks') && call.method === 'POST').length)
      .toBeGreaterThanOrEqual(2)
  })

  it('4xx（何度送っても通らない）分塊は、失う音声として残す（黙って捨てない）', async () => {
    stubRecorder()
    const { calls } = mockApi({ chunkFailFrom: 1, chunkFailKind: 'client' })
    const { wrapper } = await open()
    const recorder = (globalThis as unknown as {
      MediaRecorder: { instances: { ondataavailable: ((event: { data: Blob }) => void) | null }[] }
    }).MediaRecorder.instances[0]
    recorder?.ondataavailable?.({ data: new Blob([new Uint8Array([1, 2, 3])], { type: 'audio/webm' }) })
    await flushPromises()

    // 録れた数には残る（送れていない）
    await wrapper.get('[data-cr-details-toggle]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-cr-manifest-counts]').text()).toContain('1 件 / 0 件')
    // 待ち行列には入れない（何度送っても通らない）が、**理由を出す**（黙って捨てない）
    const shown = `${wrapper.get('[data-cr-status]').text()} `
      + `${wrapper.get('[data-cr-details]').text()}`
    expect(shown).toContain('同じ連番に違う内容')
    // 送り直しの対象にはしない（何度送っても通らないので、利用者を無駄に待たせない）
    expect(wrapper.find('[data-cr-retry-uploads]').exists()).toBe(false)
    expect(calls.filter((call) => call.url.includes('/chunks') && call.method === 'POST')).toHaveLength(1)
  })

  it('開き直しても、録れた範囲が分かる（送れた数と混ぜない）', async () => {
    stubRecorder()
    // サーバーには 1 つだけ保存されている（2 つ目は送れなかった）
    mockApi({
      chunkList: {
        items: [], chunkCount: 1, maxSeq: 1, nextSeq: 2, totalBytes: 100, recordedSeconds: 20,
        finalizeCheck: {
          complete: false, missingSeqs: [], storedChunks: 1, expectedChunks: 1, reason: null
        }
      }
    })
    const { wrapper } = await open()
    const counts = await manifestText(wrapper)
    // 画面が残した一覧が無いので、サーバーの保存済みから作る（1 件 / 1 件）
    expect(counts).toContain('1 件 / 1 件')
  })
})

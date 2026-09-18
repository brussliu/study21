import { beforeEach, describe, expect, it, vi } from 'vitest'
import { MissingRangeStore, SourceStream, type MissingRange } from '@/features/classroom/source-stream'

/** 送った内容を覚える偽の WebSocket（実際のソケットは使わない）。 */
class FakeSocket {
  static instances: FakeSocket[] = []
  binaryType = ''
  sent: (string | ArrayBuffer)[] = []
  onopen: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null
  closed = false

  constructor(public url: string) { FakeSocket.instances.push(this) }
  send(data: string | ArrayBuffer): void { this.sent.push(data) }
  close(): void { this.closed = true; this.onclose?.() }
  /* テストから呼ぶ操作 */
  open(): void { this.onopen?.() }
  deliver(payload: unknown): void { this.onmessage?.({ data: JSON.stringify(payload) }) }
  binaries(): ArrayBuffer[] { return this.sent.filter((item): item is ArrayBuffer => item instanceof ArrayBuffer) }
  texts(): string[] { return this.sent.filter((item): item is string => typeof item === 'string') }
}

/** 音声フレームの**見出し**（JSON のテキスト。バイナリの前に送る）だけを取り出す。 */
function frameHeaders(socket: FakeSocket | undefined): {
  type: string; no: number; startSample: number; sampleRate: number; source: string
}[] {
  if (socket === undefined) return []
  return socket.texts()
    .map((text) => { try { return JSON.parse(text) as { type?: string } } catch { return { type: '' } } })
    .filter((payload) => payload.type === 'frame')
    .map((payload) => payload as { type: string; no: number; startSample: number; sampleRate: number; source: string })
}

/**
 * 音源 1 つぶんの書き起こし送信（`SourceStream`）の検証。
 *
 * <p>ここで固定するのは「**音を失わない**」ことと「**音源ごとに独立**」なこと:</p>
 * <ul>
 *   <li>送れなかった音は捨てず、次の周期で同じ音を送り直す（HTTP の失敗＝未送信）</li>
 *   <li>貯めすぎたときだけ古い音を捨てるが、**欠落した区間を記録して画面へ伝える**</li>
 *   <li>停止は「残りを送り切る → `/finish`」の順（尾句を落とさない）</li>
 *   <li>片方の音源が失敗しても、もう片方は送り続ける</li>
 * </ul>
 */
interface Call {
  url: string
  /** 送った PCM のバイト数（Blob のサイズ。中身は見ない）。 */
  bodySize: number
}

/** `/stt/stream` に応える fetch の代役（呼び出しを記録し、失敗を差し込める）。 */
function mockStream(options: { failTimes?: number } = {}): { calls: Call[] } {
  const calls: Call[] = []
  let attempts = 0
  vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
    const ok = (data: unknown): Response => new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })
    const bodySize = init?.body instanceof Blob ? init.body.size : 0
    calls.push({ url: String(url), bodySize })
    if (String(url).includes('/stt/stream/finish')) {
      return ok({ interim: '', added: [], error: null })
    }
    attempts += 1
    if (attempts <= (options.failTimes ?? 0)) {
      return new Response(
        JSON.stringify({ success: false, code: 'INTERNAL_ERROR', message: 'サーバーでエラー', data: null }),
        { status: 500, headers: { 'Content-Type': 'application/json' } })
    }
    return ok({
      interim: 'ううう',
      added: [{
        segmentId: 1, seq: 1, startOffsetSeconds: 0.2, endOffsetSeconds: 1.4,
        speaker: '学生', text: 'ありがとう。', language: 'ja-JP', createdAt: null
      }],
      error: null
    })
  }))
  return { calls }
}

/** 1 秒ぶん（48kHz）のサンプル。 */
function samples(seconds: number, value = 0.2): Float32Array {
  return new Float32Array(Math.round(48_000 * seconds)).fill(value)
}

function newStream(overrides: Partial<ConstructorParameters<typeof SourceStream>[0]> = {}): {
  stream: SourceStream
  segments: unknown[]
  notices: string[]
  missing: { fromSeconds: number; toSeconds: number }[]
} {
  const segments: unknown[] = []
  const notices: string[] = []
  const missing: { fromSeconds: number; toSeconds: number }[] = []
  const stream = new SourceStream({
    source: 'mic',
    recordId: () => 12,
    // 既存のテストは HTTP 送信（常時接続が使えないときの退避経路）を確かめる
    useSocket: false,
    onSegments: (added) => segments.push(...added),
    onInterim: () => undefined,
    onNotice: (message) => notices.push(message),
    onMissingRange: (range) => missing.push(range),
    intervalMs: 250,
    ...overrides
  })
  return { stream, segments, notices, missing }
}

beforeEach(() => {
  vi.unstubAllGlobals()
})

describe('授業録音：音源ごとの書き起こし送信', () => {
  it('音源ごとの URL へ PCM を送り、確定した文を受け取る', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      const { stream, segments } = newStream({ source: 'shared' })
      stream.append(samples(1), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      expect(calls.some((call) => call.url.includes('source=shared'))).toBe(true)
      // PCM は 16kHz 16bit（1 秒 ＝ 32000 バイト。上限 2 秒までなので 1 回に収まる）
      const first = calls.find((call) => call.url.includes('/stt/stream?'))
      expect(first?.bodySize).toBe(32_000)
      expect(segments).toHaveLength(1)
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  it('送れなかった音は捨てず、次の周期で同じ音を送り直す', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream({ failTimes: 1 })
      const { stream, notices } = newStream()
      stream.append(samples(1), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)   // 1 回目は失敗
      expect(notices.some((message) => message.includes('送れませんでした'))).toBe(true)
      expect(stream.state().retrying).toBe(true)

      await vi.advanceTimersByTimeAsync(300)   // 2 回目は成功（同じ音）
      const pushes = calls.filter((call) => call.url.includes('/stt/stream?'))
      expect(pushes).toHaveLength(2)
      expect(pushes[0]?.bodySize).toBe(32_000)
      // 2 回目も**同じ音**（1 秒ぶん）を送っている＝捨てていない
      expect(pushes[1]?.bodySize).toBe(32_000)
      expect(stream.state().retrying).toBe(false)
      expect(stream.state().pendingSeconds).toBe(0)
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  it('貯めすぎたら古い音を捨て、欠落した区間を記録して知らせる（黙って消さない）', async () => {
    vi.useFakeTimers()
    try {
      mockStream()
      // 上限を 3 秒にして 10 秒ぶん貯める（送信はまだ動かさない）
      const { stream, missing, notices } = newStream({ maxBufferSeconds: 3, intervalMs: 10_000 })
      stream.append(samples(10), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(10)

      expect(missing.length).toBeGreaterThan(0)
      expect(missing[0]?.fromSeconds).toBe(0)
      expect(missing[0]?.toSeconds).toBeCloseTo(7, 1)
      expect(notices.some((message) => message.includes('飛ばしました'))).toBe(true)
      // 残っているのは直近 3 秒（捨てた分は記録済み）
      expect(stream.state().pendingSeconds).toBeLessThanOrEqual(3)
      expect(stream.state().missingRanges).toHaveLength(missing.length)
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  it('1 回に送る量は上限まで（貯まっていても小分けに送る）', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      const { stream } = newStream({ maxSendSeconds: 2, maxBufferSeconds: 60 })
      stream.append(samples(5), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      const first = calls.find((call) => call.url.includes('/stt/stream?'))
      // 2 秒ぶん（16kHz 16bit）だけ送る
      expect(first?.bodySize).toBe(64_000)
      expect(stream.state().pendingSeconds).toBeGreaterThan(2)
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  it('停止は「残りを送り切る → /finish」の順（尾句を落とさない）', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      const { stream, segments } = newStream({ maxSendSeconds: 2 })
      stream.append(samples(3), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      const finishPromise = stream.finish()
      await vi.advanceTimersByTimeAsync(1_000)
      await finishPromise

      const pushes = calls.filter((call) => call.url.includes('/stt/stream?'))
      const finish = calls.find((call) => call.url.includes('/stt/stream/finish'))
      expect(pushes.length).toBeGreaterThanOrEqual(2)
      expect(finish?.url).toContain('source=mic')
      // 送り切ってから終わりを伝えている
      expect(calls.indexOf(finish as Call)).toBe(calls.length - 1)
      expect(stream.state().pendingSeconds).toBe(0)
      expect(segments.length).toBeGreaterThanOrEqual(2)
    } finally {
      vi.useRealTimers()
    }
  })

  it('音源ごとに独立している（片方が失敗しても、もう片方は送り続ける）', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream({ failTimes: 100 })   // ずっと失敗する
      const mic = newStream({ source: 'mic' })
      const shared = newStream({ source: 'shared' })
      shared.stream.append(samples(1), 48_000)
      mic.stream.append(samples(1), 48_000)
      shared.stream.start()
      mic.stream.start()
      await vi.advanceTimersByTimeAsync(300)

      // 共有側は失敗し続けているが、マイク側も自分の音を送っている（止まらない）
      const sharedCalls = calls.filter((call) => call.url.includes('source=shared'))
      const micCalls = calls.filter((call) => call.url.includes('source=mic'))
      expect(sharedCalls.length).toBeGreaterThan(0)
      expect(micCalls.length).toBeGreaterThan(0)
      expect(mic.stream.state().retrying).toBe(true)
      mic.stream.stop()
      shared.stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })
})

/**
 * 常時接続（WebSocket）での送信。偽の WebSocket を差し込んで、実際のソケットは使わない。
 *
 * <p>見張るのは「100ms ずつ番号つきで送る」「受け取り確認で履歴を捨てる」
 * 「断線したら番号の続きから送り直す」「終了の合図と最後の確定文」。 </p>
 */
describe('授業録音：常時接続（WebSocket）での送信', () => {
  function socketStream(overrides: Record<string, unknown> = {}): {
    stream: SourceStream
    segments: unknown[]
    notices: string[]
  } {
    const segments: unknown[] = []
    const notices: string[] = []
    const stream = new SourceStream({
      source: 'mic',
      recordId: () => 12,
      onSegments: (added) => segments.push(...added),
      onInterim: () => undefined,
      onNotice: (message) => notices.push(message),
      intervalMs: 100,
      socketFrameSeconds: 0.1,
      socketFactory: (url) => new FakeSocket(url) as unknown as WebSocket,
      ...overrides
    })
    return { stream, segments, notices }
  }

  /** 48kHz の `seconds` 秒ぶんのサンプル。 */
  const feed = (stream: SourceStream, seconds: number): void =>
    stream.append(new Float32Array(Math.round(48_000 * seconds)).fill(0.2), 48_000)

  it('常時接続で 100ms ずつ番号つきで送り、受け取り確認で履歴を捨てる', async () => {
    vi.useFakeTimers()
    try {
      FakeSocket.instances = []
      const { stream, segments } = socketStream()
      feed(stream, 0.5)
      stream.start()
      const socket = FakeSocket.instances[0]
      expect(socket).toBeDefined()
      expect(socket?.url).toContain('/stt/socket?source=mic')
      socket?.open()
      await vi.advanceTimersByTimeAsync(350)

      // 100ms（3200 バイト）ずつ送る
      expect(socket?.binaries().length).toBeGreaterThanOrEqual(3)
      expect(socket?.binaries()[0]?.byteLength).toBe(3_200)

      // 受け取り確認が来たら、確認済みの分は履歴から消える（送り直さない）
      socket?.deliver({ type: 'result', interim: 'うう', added: [], error: null, processedFrames: 3 })
      await vi.advanceTimersByTimeAsync(50)
      expect(segments).toHaveLength(0)
      // 断線して張り直したときは、確認できていない番号（4 以降）だけを送り直す
      socket?.close()
      await vi.advanceTimersByTimeAsync(1_000)
      const second = FakeSocket.instances[1]
      second?.open()
      await vi.advanceTimersByTimeAsync(50)
      expect(second?.texts()[0]).toContain('"type":"resume"')
      expect(second?.texts()[0]).toContain('"from":4')
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })

  it('認識の結果（確定した文・エラー）はその場で画面へ渡す', async () => {
    vi.useFakeTimers()
    try {
      FakeSocket.instances = []
      const { stream, segments, notices } = socketStream()
      stream.start()
      const socket = FakeSocket.instances[0]
      socket?.open()
      socket?.deliver({
        type: 'result', interim: '', error: null, processedFrames: 1,
        added: [{ segmentId: 1, seq: 1, startOffsetSeconds: 0.2, endOffsetSeconds: 1.0,
          speaker: '学生', text: 'ありがとう。', language: 'ja-JP', createdAt: null }]
      })
      expect(segments).toHaveLength(1)

      socket?.deliver({ type: 'result', interim: '', added: [], error: '認識に失敗しました。', processedFrames: 2 })
      expect(notices.some((message) => message.includes('認識に失敗しました。'))).toBe(true)
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })

  it('終了は finish を送って、最後の確定文（finished）を待つ', async () => {
    vi.useFakeTimers()
    try {
      FakeSocket.instances = []
      const { stream, segments } = socketStream()
      feed(stream, 0.2)
      stream.start()
      const socket = FakeSocket.instances[0]
      socket?.open()
      await vi.advanceTimersByTimeAsync(250)

      const finishing = stream.finish()
      await vi.advanceTimersByTimeAsync(100)
      expect(socket?.texts().some((text) => text.includes('"type":"finish"'))).toBe(true)

      socket?.deliver({
        type: 'finished', error: null, processedFrames: 2,
        added: [{ segmentId: 9, seq: 9, startOffsetSeconds: 1.0, endOffsetSeconds: 2.0,
          speaker: '学生', text: '最後の文です。', language: 'ja-JP', createdAt: null }]
      })
      await vi.advanceTimersByTimeAsync(200)
      await finishing
      expect(segments.some((segment) => (segment as { text: string }).text === '最後の文です。')).toBe(true)
      expect(socket?.closed).toBe(true)
    } finally {
      vi.useRealTimers()
    }
  })

  it('常時接続を開けないときは HTTP へ退避する（録音と書き起こしを止めない）', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      const notices: string[] = []
      const stream = new SourceStream({
        source: 'mic',
        recordId: () => 12,
        onSegments: () => undefined,
        onInterim: () => undefined,
        onNotice: (message) => notices.push(message),
        intervalMs: 250,
        socketFactory: () => { throw new Error('WebSocket が使えない') }
      })
      stream.append(new Float32Array(48_000).fill(0.2), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      // HTTP の送信に切り替わっている（音は捨てない）
      expect(calls.some((call) => call.url.includes('/stt/stream?source=mic'))).toBe(true)
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  it('常時接続は周期ぶん送る（100ms フレームを 1 周期に 1 個だけ送らない）', async () => {
    vi.useFakeTimers()
    try {
      FakeSocket.instances = []
      // 本番と同じ周期（250ms）・フレーム（100ms）。
      // 実測では 20 秒の録音で 8.4 秒ぶんしか届かず（実時間の 4 割）、
      // 後半の発話が認識されないまま欠けていた。
      const { stream } = socketStream({ intervalMs: 250 })
      feed(stream, 1.5) // 1.5 秒ぶん＝100ms のフレーム 15 個
      stream.start()
      const socket = FakeSocket.instances[0]
      socket?.open()
      await vi.advanceTimersByTimeAsync(1_000) // 4 周期＝実時間 1 秒
      await vi.advanceTimersByTimeAsync(50) // 送信（FileReader）の解決を待つ

      // 実時間ぶん（1 秒＝10 フレーム）は送れている（1 周期 1 個なら 4 個しか出ない）
      expect(socket?.binaries().length).toBeGreaterThanOrEqual(10)
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })
})

/**
 * 授業録音：**録音の時間軸とフレームの識別**（アイテム 3 / 4）。
 *
 * <p>これまでは「そのセッションで何サンプル送ったか」から時刻を作っていたので、張り直し・
 * 後端の再起動・欠けた区間で 0 に戻ったり詰まったりした。いまは**どの音声フレームにも
 * 安定した番号と録音の先頭からの絶対位置（16kHz のサンプル）**を付けて送る
 * （常時接続はバイナリの前に見出しのテキスト 1 枚、HTTP は問い合わせの欄）。
 * 位置は**送った量の累計**から決まるので、張り直しても 0 に戻らない。</p>
 */
describe('授業録音：フレームの番号と絶対位置（録音の時間軸）', () => {
  const feed = (stream: SourceStream, seconds: number): void =>
    stream.append(new Float32Array(Math.round(48_000 * seconds)).fill(0.2), 48_000)

  function streamWithSocket(overrides: Record<string, unknown> = {}): SourceStream {
    return new SourceStream({
      source: 'mic',
      recordId: () => 12,
      onSegments: () => undefined,
      onInterim: () => undefined,
      onNotice: () => undefined,
      intervalMs: 250,
      socketFactory: (url) => new FakeSocket(url) as unknown as WebSocket,
      ...overrides
    })
  }

  it('A: 常時接続のフレームは「番号＋絶対位置」の見出しを先に送る（バイナリはそのまま）', async () => {
    vi.useFakeTimers()
    try {
      FakeSocket.instances = []
      const stream = streamWithSocket()
      feed(stream, 0.3) // 100ms のフレーム 3 個ぶん
      stream.start()
      const socket = FakeSocket.instances[0]
      socket?.open()
      await vi.advanceTimersByTimeAsync(300)
      await vi.advanceTimersByTimeAsync(50) // 送信（Blob → ArrayBuffer）の解決を待つ

      const headers = frameHeaders(socket)
      expect(headers).toHaveLength(3)
      // 番号は 1 から、位置は録音の先頭から 100ms ずつ
      expect(headers.map((header) => header.no)).toEqual([1, 2, 3])
      expect(headers.map((header) => header.startSample)).toEqual([0, 1_600, 3_200])
      expect(headers[0]).toMatchObject({ sampleRate: 16_000, source: 'mic' })
      // 見出しの直後にバイナリ（100ms ＝ 3200 バイト）が来る＝バイナリはバイナリのまま
      const firstHeaderAt = socket?.sent.findIndex((item) => item === JSON.stringify(headers[0])) ?? -1
      expect(firstHeaderAt).toBeGreaterThanOrEqual(0)
      expect(socket?.sent[firstHeaderAt + 1]).toBeInstanceOf(ArrayBuffer)
      expect(socket?.binaries()).toHaveLength(3)
      expect(socket?.binaries()[0]?.byteLength).toBe(3_200)
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })

  it('A: 張り直したあとの最初のフレームも絶対位置を続ける（0 に戻らない・番号も戻らない）', async () => {
    vi.useFakeTimers()
    try {
      // 張り直しを待っているあいだ HTTP へは行かない（戻ったときに同じ音を 2 回送らない）
      const { calls } = mockStream()
      FakeSocket.instances = []
      const stream = streamWithSocket()
      feed(stream, 0.5) // 100ms のフレーム 5 個ぶん
      stream.start()
      const first = FakeSocket.instances[0]
      first?.open()
      await vi.advanceTimersByTimeAsync(600)
      await vi.advanceTimersByTimeAsync(50)
      expect(frameHeaders(first).map((header) => header.no)).toEqual([1, 2, 3, 4, 5])
      expect(frameHeaders(first).map((header) => header.startSample)).toEqual([0, 1_600, 3_200, 4_800, 6_400])

      // 3 番まで確認できたところで切れる
      first?.deliver({ type: 'result', interim: '', added: [], error: null, processedFrames: 3 })
      first?.close()
      await vi.advanceTimersByTimeAsync(600) // 張り直しの待ち（500ms）
      expect(calls.filter((call) => call.url.includes('/stt/stream?'))).toHaveLength(0)

      const second = FakeSocket.instances[1]
      second?.open()
      await vi.advanceTimersByTimeAsync(50)
      // 後端を再起動しても位置が変わらないように、続きの番号で `resume` を送る
      expect(second?.texts()[0]).toBe('{"type":"resume","from":4}')
      // 確認できていない 4・5 を、番号も位置も続きから送り直す
      expect(frameHeaders(second).map((header) => header.no)).toEqual([4, 5])
      expect(frameHeaders(second).map((header) => header.startSample)).toEqual([4_800, 6_400])

      feed(stream, 0.2) // 続きの音
      await vi.advanceTimersByTimeAsync(600)
      await vi.advanceTimersByTimeAsync(50)
      const headers = frameHeaders(second)
      expect(headers.slice(-2).map((header) => header.no)).toEqual([6, 7])
      // 0.6 秒 ＝ 9600 サンプル（張り直しで 0 に戻っていない）
      expect(headers.at(-1)?.startSample).toBe(9_600)
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })

  it('A: 張り直した認識が 0 から数え直しても、文の時刻は録音の時間軸へ戻す', async () => {
    vi.useFakeTimers()
    try {
      FakeSocket.instances = []
      const segments: { startOffsetSeconds: number | null; endOffsetSeconds: number | null }[] = []
      const notices: string[] = []
      const stream = new SourceStream({
        source: 'shared',
        recordId: () => 12,
        onSegments: (added) => segments.push(...added),
        onInterim: () => undefined,
        onNotice: (message) => notices.push(message),
        intervalMs: 250,
        socketFactory: (url) => new FakeSocket(url) as unknown as WebSocket
      })
      feed(stream, 2) // 2 秒ぶん（フレーム 20 個）
      stream.start()
      const first = FakeSocket.instances[0]
      first?.open()
      await vi.advanceTimersByTimeAsync(2_200)
      await vi.advanceTimersByTimeAsync(50)
      expect(frameHeaders(first)).toHaveLength(20)
      first?.deliver({ type: 'result', interim: '', added: [], error: null, processedFrames: 20 })

      // 張り直して、2 秒目から続きを送る（確認できていない音は無い）
      first?.close()
      await vi.advanceTimersByTimeAsync(600)
      const second = FakeSocket.instances[1]
      second?.open()
      await vi.advanceTimersByTimeAsync(50)
      feed(stream, 0.2)
      await vi.advanceTimersByTimeAsync(600)
      await vi.advanceTimersByTimeAsync(50)
      expect(frameHeaders(second).map((header) => header.startSample)).toEqual([32_000, 33_600])

      // 後端が数え直して「セッションの中の 0.2 秒」を返しても、録音の 2.2 秒として出す
      second?.deliver({
        type: 'result', interim: '', error: null, processedFrames: 22,
        added: [{ segmentId: 7, seq: 7, startOffsetSeconds: 0.2, endOffsetSeconds: 1,
          speaker: '先生', text: '張り直しのあとの文です。', language: 'ja-JP', createdAt: null }]
      })
      expect(segments).toHaveLength(1)
      expect(segments[0]?.startOffsetSeconds).toBeCloseTo(2.2, 3)
      expect(segments[0]?.endOffsetSeconds).toBeCloseTo(3, 3)
      expect(notices.some((message) => message.includes('録音の時間軸'))).toBe(true)

      // 正しく録音の時間軸で返ってきた文はそのまま（二重に足さない）
      second?.deliver({
        type: 'result', interim: '', error: null, processedFrames: 22,
        added: [{ segmentId: 8, seq: 8, startOffsetSeconds: 5, endOffsetSeconds: 6,
          speaker: '先生', text: 'そのあとの文です。', language: 'ja-JP', createdAt: null }]
      })
      expect(segments[1]?.startOffsetSeconds).toBe(5)
      expect(segments[1]?.endOffsetSeconds).toBe(6)
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })

  it('A: HTTP の送信も同じ識別（番号＋絶対位置）を渡す', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      const { stream } = newStream({ useSocket: false, maxSendSeconds: 1 })
      stream.append(samples(2), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      const pushes = calls.filter((call) => call.url.includes('/stt/stream?'))
      expect(pushes).toHaveLength(1)
      expect(pushes[0]?.url).toContain('frameNo=1')
      expect(pushes[0]?.url).toContain('startSample=0')

      await vi.advanceTimersByTimeAsync(300)
      const second = calls.filter((call) => call.url.includes('/stt/stream?'))[1]
      // 2 回目は録音の 1 秒目から（セッションで数え直さない）
      expect(second?.url).toContain('frameNo=2')
      expect(second?.url).toContain('startSample=16000')
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })
})

/**
 * 授業録音：**通信の回復**（アイテム 4）。
 *
 * <p>見張るのは 3 つ:</p>
 * <ul>
 *   <li>**HTTP 200 でも業務エラーは成功にしない**（`success:false` / `data.error`）。
 *       送れていないのに「送れた」ことにすると、その音の書き起こしが永久に欠ける。</li>
 *   <li>**受け取り確認はサーバーが言った番号だけ**進める（`error` のときは進めない）。</li>
 *   <li>常時接続を失ったら**確認できていない音を全部 HTTP へ古い順に渡す**（欠けも重複も作らない）。</li>
 * </ul>
 */
describe('授業録音：失敗の見分けと、常時接続からの退避', () => {
  /** 200 で返す応答（`success` は true のまま、`data.error` に理由を入れる形も作れる）。 */
  function ok(data: unknown): Response {
    return new Response(
      JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }),
      { status: 200, headers: { 'Content-Type': 'application/json' } })
  }

  const segment = {
    segmentId: 1, seq: 1, startOffsetSeconds: 0.2, endOffsetSeconds: 1.4,
    speaker: '学生', text: 'ありがとう。', language: 'ja-JP', createdAt: null
  }

  it('B: HTTP 200 でも業務エラー（data.error）は成功にしない（同じ音を送り直す）', async () => {
    vi.useFakeTimers()
    try {
      const calls: Call[] = []
      let attempts = 0
      const notices: string[] = []
      const segments: unknown[] = []
      vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ url: String(url), bodySize: init?.body instanceof Blob ? init.body.size : 0 })
        if (String(url).includes('/finish')) return ok({ interim: '', added: [], error: null })
        attempts += 1
        // 1 回目: HTTP は 200 だが、認識セッションの保存に失敗している（業務エラー）
        if (attempts === 1) {
          return ok({ interim: '', added: [segment], error: '書き起こしを保存できませんでした。' })
        }
        return ok({ interim: '', added: [], error: null })
      }))
      const stream = new SourceStream({
        source: 'mic', recordId: () => 12, useSocket: false,
        onSegments: (added) => segments.push(...added),
        onInterim: () => undefined,
        onNotice: (message) => notices.push(message),
        intervalMs: 250
      })
      stream.append(samples(1), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      // 失敗として扱う（成功にしない）＝同じ音をもう一度送る
      expect(notices.some((message) => message.includes('送れませんでした'))).toBe(true)
      expect(stream.state().retrying).toBe(true)
      // その回に保存できた文は捨てない（画面には出す）
      expect(segments).toHaveLength(1)

      await vi.advanceTimersByTimeAsync(300)
      const pushes = calls.filter((call) => call.url.includes('/stt/stream?'))
      expect(pushes).toHaveLength(2)
      expect(pushes[1]?.bodySize).toBe(32_000) // 同じ 1 秒ぶん
      expect(stream.state().retrying).toBe(false)
      expect(stream.state().pendingSeconds).toBe(0)
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  it('B: HTTP 200 でも success:false は失敗（成功にしない）', async () => {
    vi.useFakeTimers()
    try {
      const calls: Call[] = []
      let attempts = 0
      const notices: string[] = []
      vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ url: String(url), bodySize: init?.body instanceof Blob ? init.body.size : 0 })
        if (String(url).includes('/finish')) return ok({ interim: '', added: [], error: null })
        attempts += 1
        if (attempts === 1) {
          // HTTP は 200 だが `success:false`（入口の確認で断られた等）
          return new Response(
            JSON.stringify({ success: false, code: 'CONFLICT', message: '録音中ではありません。', data: null }),
            { status: 200, headers: { 'Content-Type': 'application/json' } })
        }
        return ok({ interim: '', added: [], error: null })
      }))
      const stream = new SourceStream({
        source: 'mic', recordId: () => 12, useSocket: false,
        onSegments: () => undefined,
        onInterim: () => undefined,
        onNotice: (message) => notices.push(message),
        intervalMs: 250
      })
      stream.append(samples(1), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      expect(notices.some((message) => message.includes('録音中ではありません。'))).toBe(true)
      expect(stream.state().retrying).toBe(true)
      await vi.advanceTimersByTimeAsync(300)
      const pushes = calls.filter((call) => call.url.includes('/stt/stream?'))
      expect(pushes).toHaveLength(2)
      expect(pushes[1]?.bodySize).toBe(32_000)
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  /** URL から送信の識別（番号と絶対位置）を取り出す。 */
  function identityOf(url: string): { frameNo: number; startSample: number } {
    const query = new URLSearchParams(url.slice(url.indexOf('?')))
    return { frameNo: Number(query.get('frameNo')), startSample: Number(query.get('startSample')) }
  }

  it('B: 張り直せなくなったら、確認できていない音を全部 HTTP へ古い順に渡す（欠けも重複も作らない）', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      FakeSocket.instances = []
      const notices: string[] = []
      const stream = new SourceStream({
        source: 'mic', recordId: () => 12,
        onSegments: () => undefined, onInterim: () => undefined,
        onNotice: (message) => notices.push(message),
        intervalMs: 250,
        maxReconnects: 0,
        socketFactory: (url) => new FakeSocket(url) as unknown as WebSocket
      })
      stream.append(samples(0.5), 48_000) // 100ms のフレーム 5 個
      stream.start()
      const socket = FakeSocket.instances[0]
      socket?.open()
      await vi.advanceTimersByTimeAsync(600)
      await vi.advanceTimersByTimeAsync(50)
      expect(frameHeaders(socket).map((header) => header.no)).toEqual([1, 2, 3, 4, 5])
      // 2 番まで確認できた（3・4・5 は未確認）
      socket?.deliver({ type: 'result', interim: '', added: [], error: null, processedFrames: 2 })

      socket?.close() // 張り直さずに HTTP へ退避する
      expect(notices.some((message) => message.includes('全部 HTTP で送り直します'))).toBe(true)
      await vi.advanceTimersByTimeAsync(300)
      await vi.advanceTimersByTimeAsync(300)
      await vi.advanceTimersByTimeAsync(300)
      await vi.advanceTimersByTimeAsync(50)

      const pushes = calls.filter((call) => call.url.includes('/stt/stream?'))
      // 未確認の 3・4・5 を古い順に、同じ番号・同じ位置で送る（1・2 は送り直さない）
      expect(pushes.map((call) => identityOf(call.url))).toEqual([
        { frameNo: 3, startSample: 3_200 },
        { frameNo: 4, startSample: 4_800 },
        { frameNo: 5, startSample: 6_400 }
      ])
      expect(pushes.map((call) => call.bodySize)).toEqual([3_200, 3_200, 3_200])

      // 続きの音は番号も位置も続く（欠けも重複も無い）
      stream.append(samples(0.5), 48_000)
      await vi.advanceTimersByTimeAsync(300)
      await vi.advanceTimersByTimeAsync(50)
      const all = calls.filter((call) => call.url.includes('/stt/stream?'))
      expect(identityOf(all[3]?.url ?? '')).toEqual({ frameNo: 6, startSample: 8_000 })
      // 位置は「前の送信の終わり」から途切れず続く（重なりも穴も無い）
      for (let index = 1; index < all.length; index += 1) {
        const before = identityOf(all[index - 1]?.url ?? '')
        const after = identityOf(all[index]?.url ?? '')
        expect(after.startSample).toBe(before.startSample + (all[index - 1]?.bodySize ?? 0) / 2)
      }
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  it('B: 開いてすぐ切れる接続を繰り返しても、張り直しは上限までで HTTP へ退避する', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      FakeSocket.instances = []
      const notices: string[] = []
      const stream = new SourceStream({
        source: 'mic', recordId: () => 12,
        onSegments: () => undefined, onInterim: () => undefined,
        onNotice: (message) => notices.push(message),
        intervalMs: 250,
        maxReconnects: 2,
        socketFactory: (url) => new FakeSocket(url) as unknown as WebSocket
      })
      stream.start()
      // 開いても確認が取れないまま切れる、を繰り返す（`processedFrames` が来ない＝進んでいない）
      for (let round = 0; round < 6; round += 1) {
        const socket = FakeSocket.instances[round]
        if (socket === undefined) break
        socket.open()
        await vi.advanceTimersByTimeAsync(50)
        socket.close()
        // 張り直しの待ちは回数とともに延びる（500ms / 1000ms …）ので、それを超えて進める
        await vi.advanceTimersByTimeAsync(1_200)
      }
      // 最初の 1 本 ＋ 上限 2 回の張り直しで打ち切る（無限につなぎ直さない）
      expect(FakeSocket.instances).toHaveLength(3)
      expect(notices.some((message) => message.includes('全部 HTTP で送り直します'))).toBe(true)

      // 退避したあとは HTTP で送る（音は捨てない）
      stream.append(samples(0.3), 48_000)
      await vi.advanceTimersByTimeAsync(300)
      await vi.advanceTimersByTimeAsync(50)
      expect(calls.some((call) => call.url.includes('/stt/stream?source=mic'))).toBe(true)
      expect(FakeSocket.instances).toHaveLength(3)
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  it('B: 常時接続では error つきの結果で位置を進めない（進めるのは processedFrames だけ）', async () => {
    vi.useFakeTimers()
    try {
      FakeSocket.instances = []
      const stream = new SourceStream({
        source: 'mic', recordId: () => 12,
        onSegments: () => undefined, onInterim: () => undefined, onNotice: () => undefined,
        intervalMs: 250,
        socketFactory: (url) => new FakeSocket(url) as unknown as WebSocket
      })
      stream.append(samples(0.5), 48_000)
      stream.start()
      const first = FakeSocket.instances[0]
      first?.open()
      await vi.advanceTimersByTimeAsync(600) // 0.5 秒ぶん（フレーム 5 個）を送り切る
      await vi.advanceTimersByTimeAsync(50)
      expect(frameHeaders(first).map((header) => header.no)).toEqual([1, 2, 3, 4, 5])

      // エラーつき（保存できなかった）＝番号を進めない
      first?.deliver({ type: 'result', interim: '', added: [], error: '書き起こしを保存できませんでした。', processedFrames: 5 })
      first?.close()
      await vi.advanceTimersByTimeAsync(600)
      const second = FakeSocket.instances[1]
      second?.open()
      await vi.advanceTimersByTimeAsync(50)
      expect(second?.texts()[0]).toBe('{"type":"resume","from":1}')
      expect(frameHeaders(second).map((header) => header.no)).toEqual([1, 2, 3, 4, 5])

      // エラーなしの `processedFrames` だけが位置を進める
      second?.deliver({ type: 'result', interim: '', added: [], error: null, processedFrames: 3 })
      second?.close()
      await vi.advanceTimersByTimeAsync(600)
      const third = FakeSocket.instances[2]
      third?.open()
      await vi.advanceTimersByTimeAsync(50)
      expect(third?.texts()[0]).toBe('{"type":"resume","from":4}')
      expect(frameHeaders(third).map((header) => header.no)).toEqual([4, 5])
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })
})

/**
 * 授業録音：**欠けた区間を後から書き起こせるように残す**（アイテム 4 の音声の貯め方）。
 *
 * <p>貯められる量には上限が要る（無限に貯めると送信が遅れ続ける）。ただし上限を超えて
 * 捨てた区間を「画面の案内」だけで終わらせると、その音の書き起こしは永久に欠ける。
 * 録音そのものには全部入っているので、**音源と録音の時間軸の絶対位置**を残しておけば
 * あとから追い書きできる。</p>
 */
describe('授業録音：補書き起こしの手がかり（欠落区間の記録）', () => {
  function recoveryStream(recordId: number, storage: SourceStreamRecovery[] = []): SourceStream {
    return new SourceStream({
      source: 'mic',
      recordId: () => recordId,
      useSocket: false,
      intervalMs: 10_000, // 送信は動かさない（貯めすぎたときの記録だけを見る）
      maxBufferSeconds: 3,
      onSegments: () => undefined,
      onInterim: () => undefined,
      onNotice: () => undefined,
      onMissingRange: (range) => storage.push(range)
    })
  }

  type SourceStreamRecovery = MissingRange

  it('B: 飛ばした区間は音源と絶対位置つきで残る（秒だけでは追いかけられない）', () => {
    const recorded: MissingRange[] = []
    const stream = recoveryStream(91, recorded)
    stream.append(samples(10), 48_000) // 上限 3 秒なので 7 秒ぶんが抜ける

    expect(recorded).toHaveLength(1)
    expect(recorded[0]).toMatchObject({
      source: 'mic',
      fromSeconds: 0,
      toSeconds: 7,
      fromSample: 0,
      toSample: 112_000 // 7 秒 × 16kHz
    })
    expect(recorded[0]?.recordedAt).toBeGreaterThan(0)
    expect(recorded[0]?.frameNo).toBeGreaterThan(0)
  })

  it('B: 手がかりは保存され、同じ記録・音源で読み戻せる（画面を離れても残る）', () => {
    const recorded: MissingRange[] = []
    const stream = recoveryStream(92, recorded)
    stream.append(samples(10), 48_000)

    // 画面を作り直しても読める（＝保存されている）
    expect(recoveryStream(92).recoveryRanges()).toHaveLength(1)
    expect(recoveryStream(92).recoveryRanges()[0]?.toSample).toBe(112_000)
    // 別の記録・別の音源の分は混ざらない
    expect(recoveryStream(93).recoveryRanges()).toHaveLength(0)
  })

  it('B: 手がかりは件数で有界、保存期間（既定 30 日）を過ぎたら捨てる', () => {
    let now = 1_800_000_000_000
    const key = 'classroom-stt-recovery-test'
    localStorage.removeItem(key)
    const store = new MissingRangeStore({
      storageKey: key, maxRanges: 3, retentionDays: 30, now: () => now
    })
    const range = (fromSample: number, recordedAt = now): MissingRange => ({
      source: 'mic', fromSeconds: fromSample / 16_000, toSeconds: (fromSample + 16_000) / 16_000,
      fromSample, toSample: fromSample + 16_000, frameNo: 1, recordedAt
    })

    for (let index = 0; index < 5; index += 1) store.add(range(index * 16_000))
    // 上限 3 件。古い順に捨てる（新しい 3 件が残る）
    expect(store.list().map((item) => item.fromSample)).toEqual([32_000, 48_000, 64_000])
    // 同じ区間は二重に覚えない
    store.add(range(64_000))
    expect(store.list()).toHaveLength(3)

    // 保存期間（30 日）を過ぎた記録は捨てる＝録音の実体も消えているので追い書きできない
    now += 31 * 24 * 60 * 60 * 1000
    expect(store.list()).toHaveLength(0)
  })
})

/**
 * 授業録音：**止めても・欠けても時間軸が崩れない**（アイテム 3 の残り）。
 *
 * <p>一時停止（送信ループだけ止める）でも、欠けた区間を飛ばしたあとでも、番号と位置は
 * 「その音源で取り出した累計」から続く（セッションで数え直すと 0 に戻る・詰まる）。</p>
 */
describe('授業録音：一時停止と欠落のあとの時間軸', () => {
  it('A: 一時停止して再開しても、番号も位置も続く（0 に戻らない）', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      const { stream } = newStream({ useSocket: false, maxSendSeconds: 1 })
      stream.append(samples(1), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)
      stream.stop() // 一時停止（送信ループだけ止める。録音は続いている想定）

      stream.append(samples(1), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)
      const pushes = calls.filter((call) => call.url.includes('/stt/stream?'))
      expect(pushes).toHaveLength(2)
      expect(pushes[1]?.url).toContain('frameNo=2')
      // 再開した音は録音の 1 秒目（0 に戻って詰まらない）
      expect(pushes[1]?.url).toContain('startSample=16000')
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  it('A: 欠けた区間のあとのフレームは、欠けた分だけ先の位置から続く（詰めない）', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      const stream = new SourceStream({
        source: 'mic',
        recordId: () => 94,
        useSocket: false,
        onSegments: () => undefined,
        onInterim: () => undefined,
        onNotice: () => undefined,
        intervalMs: 250,
        maxSendSeconds: 2,
        maxBufferSeconds: 3
      })
      stream.append(samples(10), 48_000) // 上限 3 秒なので 7 秒ぶんが欠ける
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      const first = calls.find((call) => call.url.includes('/stt/stream?'))
      // 残っているのは録音の 7 秒目から（欠けた 7 秒を詰めない）
      expect(first?.url).toContain('startSample=112000')
      stream.stop()
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * 画面（録音中・終了）は `finish()` が**投げなかった**ことを成功の印にできない。
   *
   * <p>尾部を取り切れなかった・保存できなかったとき、後端は HTTP 200 で理由を返し、
   * `finish()` は**投げずに返る**。理由を取り出せる口が無いと、画面は「終わった」と言ってしまい、
   * 尾句が最終まとめに入らない（＝画面からは成功に見える静かな取りこぼし）。</p>
   */
  it('A: 収尾の失敗は理由として取り出せる（投げなくても成功に見せない）', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ url: String(url), bodySize: init?.body instanceof Blob ? init.body.size : 0 })
        // 後端が「収尾を完了できなかった」と返す（HTTP は 200）
        const data = String(url).includes('/finish')
          ? { interim: '', added: [], error: '尾部の文を取り切れませんでした。' }
          : { interim: '', added: [], error: null }
        return new Response(JSON.stringify({
          success: true, code: 'OK', message: 'OK', timestamp: '', data
        }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }))
      const { stream } = newStream({ useSocket: false })
      stream.append(samples(1), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      expect(stream.finishFailure()).toBeNull()
      const finishing = stream.finish()
      await vi.advanceTimersByTimeAsync(1_000)
      await finishing

      // 投げなくても、失敗は理由として取り出せる
      expect(stream.finishFailure()).toBe('尾部の文を取り切れませんでした。')
      expect(stream.state().finishFailure).toBe('尾部の文を取り切れませんでした。')
    } finally {
      vi.useRealTimers()
    }
  })

  /**
   * やり直し（もう一度 `finish()`）が通ったら、**前の失敗を引きずらない**。
   *
   * <p>理由が残ったままだと、画面は「まだ失敗している」と見て詳細へ進めない（実際は収尾済み）。</p>
   */
  it('A: 収尾をやり直して通ったら、前の失敗の理由は消える', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      let finishCalls = 0
      vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ url: String(url), bodySize: init?.body instanceof Blob ? init.body.size : 0 })
        let data: Record<string, unknown> = { interim: '', added: [], error: null }
        if (String(url).includes('/finish')) {
          finishCalls += 1
          if (finishCalls === 1) data = { interim: '', added: [], error: '尾部の文を取り切れませんでした。' }
        }
        return new Response(JSON.stringify({
          success: true, code: 'OK', message: 'OK', timestamp: '', data
        }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }))
      const { stream } = newStream({ useSocket: false })
      stream.append(samples(1), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)

      const first = stream.finish()
      await vi.advanceTimersByTimeAsync(1_000)
      await first
      expect(stream.finishFailure()).not.toBeNull()

      // 2 回目（やり直し）。同じ音源をもう一度締めないよう、収尾は 1 回ずつ走る
      const second = stream.finish()
      await vi.advanceTimersByTimeAsync(1_000)
      await second

      expect(finishCalls).toBeGreaterThanOrEqual(2)
      expect(stream.finishFailure()).toBeNull()
    } finally {
      vi.useRealTimers()
    }
  })

  it('B: 収尾（/finish）が 200 でも data.error なら成功と言わない', async () => {
    vi.useFakeTimers()
    try {
      const { calls } = mockStream()
      const notices: string[] = []
      vi.stubGlobal('fetch', vi.fn(async (url: string, init?: RequestInit) => {
        calls.push({ url: String(url), bodySize: init?.body instanceof Blob ? init.body.size : 0 })
        // 送信は成功、収尾だけ「尾部を取り切れなかった」と返す（HTTP は 200）
        const data = String(url).includes('/finish')
          ? { interim: '', added: [], error: '尾部の文を取り切れませんでした。' }
          : { interim: '', added: [], error: null }
        return new Response(JSON.stringify({
          success: true, code: 'OK', message: 'OK', timestamp: '', data
        }), { status: 200, headers: { 'Content-Type': 'application/json' } })
      }))
      const stream = new SourceStream({
        source: 'mic', recordId: () => 95, useSocket: false,
        onSegments: () => undefined, onInterim: () => undefined,
        onNotice: (message) => notices.push(message),
        intervalMs: 250, maxSendSeconds: 2
      })
      stream.append(samples(1), 48_000)
      stream.start()
      await vi.advanceTimersByTimeAsync(300)
      const finishing = stream.finish()
      await vi.advanceTimersByTimeAsync(1_000)
      await finishing

      expect(notices.some((message) => message.includes('尾部の文を取り切れませんでした。'))).toBe(true)
      expect(notices.some((message) => message.includes('問題がありました'))).toBe(true)
    } finally {
      vi.useRealTimers()
    }
  })
})

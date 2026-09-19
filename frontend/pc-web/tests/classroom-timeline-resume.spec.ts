import { beforeEach, describe, expect, it, vi } from 'vitest'
import { SourceStream } from '@/features/classroom/source-stream'

/**
 * 授業録音：**停止 → 続きの録音**と、**送り直し・張り直し**での時間の扱い。
 *
 * <p>見張るのは 3 つ（利用者の指示「統一の録音回放時間軸」）:</p>
 * <ol>
 *   <li>続きの録音は**保存済みの位置から**送る（0 へ戻ると後端が「すでに処理した位置より古い」
 *       としてその音を丸ごと捨てる＝書き起こしが消える）。</li>
 *   <li>フレーム番号も**続きから**（張り直しで 1 へ戻さない）。ただし後端が番号から
 *       「音声クロック」を計算する `resume` は**送らない**（送ると続きの音が全部「古い」と見られる）。</li>
 *   <li>報告の時計が飛んだら、その位置をそのまま使う（詰めて時刻をずらさない）。</li>
 * </ol>
 */
class FakeSocket {
  static instances: FakeSocket[] = []
  binaryType = ''
  sent: (string | ArrayBuffer)[] = []
  onopen: (() => void) | null = null
  onmessage: ((event: { data: string }) => void) | null = null
  onerror: (() => void) | null = null
  onclose: (() => void) | null = null

  constructor(public url: string) { FakeSocket.instances.push(this) }
  send(data: string | ArrayBuffer): void { this.sent.push(data) }
  close(): void { this.onclose?.() }
  open(): void { this.onopen?.() }
  deliver(payload: unknown): void { this.onmessage?.({ data: JSON.stringify(payload) }) }
  texts(): string[] { return this.sent.filter((item): item is string => typeof item === 'string') }
}

/** 音声フレームの見出し（番号と絶対位置）だけを取り出す。 */
function frameHeaders(socket: FakeSocket | undefined): { no: number; startSample: number }[] {
  if (socket === undefined) return []
  return socket.texts()
    .map((text) => { try { return JSON.parse(text) as { type?: string } } catch { return { type: '' } } })
    .filter((payload) => payload.type === 'frame')
    .map((payload) => payload as unknown as { no: number; startSample: number })
}

function samples(seconds: number): Float32Array {
  return new Float32Array(Math.round(48_000 * seconds)).fill(0.2)
}

function newStream(overrides: Partial<ConstructorParameters<typeof SourceStream>[0]> = {}): SourceStream {
  return new SourceStream({
    source: 'mic',
    recordId: () => 12,
    intervalMs: 100,
    socketFrameSeconds: 0.1,
    onSegments: () => undefined,
    onInterim: () => undefined,
    onNotice: () => undefined,
    socketFactory: (url) => new FakeSocket(url) as unknown as WebSocket,
    ...overrides
  })
}

beforeEach(() => {
  FakeSocket.instances = []
  vi.unstubAllGlobals()
})

describe('授業録音：続きの録音の位置と番号', () => {
  it('保存済みの位置から続くフレームを送る（0 へ戻さない・番号も続く）', async () => {
    vi.useFakeTimers()
    try {
      // 60 秒ぶん送ったあとに停止した（保存済みの位置は 960,000 サンプル＝60 秒）
      const stream = newStream({ startSample: 960_000, startFrameNo: 601 })
      stream.append(samples(0.3), 48_000, 1_488_000)
      stream.start()
      const socket = FakeSocket.instances[0]
      socket?.open()
      await vi.advanceTimersByTimeAsync(400)
      await vi.advanceTimersByTimeAsync(50)

      const headers = frameHeaders(socket)
      expect(headers.length).toBeGreaterThanOrEqual(3)
      // **続きの位置**から始まる（0 ではない＝後端に「古い音」として捨てられない）
      expect(headers[0]?.startSample).toBe(960_000)
      expect(headers[1]?.startSample).toBe(961_600)
      // 番号も続く（張り直しで 1 へ戻ると、後端の受け取り確認の窓と食い違う）
      expect(headers[0]?.no).toBe(601)
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })

  it('`resume` は送らない（送ると後端が番号から音の位置を計算し、続きの音を全部「古い」と見る）', async () => {
    vi.useFakeTimers()
    try {
      const stream = newStream({ startSample: 960_000, startFrameNo: 601 })
      stream.append(samples(0.2), 48_000, 1_488_000)
      stream.start()
      const socket = FakeSocket.instances[0]
      socket?.open()
      await vi.advanceTimersByTimeAsync(100)
      await vi.advanceTimersByTimeAsync(50)

      // 見出し（`frame`）だけで、`resume` は 1 つも送らない
      expect(socket?.texts().some((text) => text.includes('"type":"resume"'))).toBe(false)
      expect(socket?.texts()[0]).toContain('"type":"frame"')
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })

  it('張り直したあとも、番号も位置も続く（0 に戻らない）', async () => {
    vi.useFakeTimers()
    try {
      const stream = newStream({ startSample: 0, startFrameNo: 1 })
      stream.append(samples(0.5), 48_000, 0)
      stream.start()
      const first = FakeSocket.instances[0]
      first?.open()
      await vi.advanceTimersByTimeAsync(600)
      await vi.advanceTimersByTimeAsync(50)
      expect(frameHeaders(first).map((header) => header.no)).toEqual([1, 2, 3, 4, 5])

      // 3 番まで確認できたところで切れる
      first?.deliver({ type: 'result', interim: '', added: [], error: null, processedFrames: 3 })
      first?.close()
      await vi.advanceTimersByTimeAsync(1_000)
      const second = FakeSocket.instances[1]
      second?.open()
      await vi.advanceTimersByTimeAsync(50)
      // 確認できていない 4・5 を、番号も位置も続きから送り直す
      expect(frameHeaders(second).map((header) => header.no)).toEqual([4, 5])
      expect(frameHeaders(second).map((header) => header.startSample)).toEqual([4_800, 6_400])
      expect(second?.texts().some((text) => text.includes('"type":"resume"'))).toBe(false)
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })

  it('不足した区間は詰めずに先の位置から続く（時計の差をそのまま位置にする）', async () => {
    vi.useFakeTimers()
    try {
      const stream = newStream({ startSample: 0, startFrameNo: 1 })
      // 1 秒ぶん（位置 0）を送り切る
      stream.append(samples(1), 48_000, 0)
      stream.start()
      const socket = FakeSocket.instances[0]
      socket?.open()
      await vi.advanceTimersByTimeAsync(1_100)
      // 時計が 30 秒先の報告（あいだの 29 秒は録れていない＝欠落）
      stream.append(samples(0.2), 48_000, 48_000 * 30)
      await vi.advanceTimersByTimeAsync(400)
      await vi.advanceTimersByTimeAsync(50)
      const positions = frameHeaders(socket).map((header) => header.startSample)
      // 最初の 1 秒は 0〜14,400 を 100ms ずつ（詰めない）
      expect(positions[9]).toBe(14_400)
      // 欠落のあとは 30 秒の時計の位置（29.8 秒）から続く＝**詰めない・圧縮しない**
      const afterGap = positions.slice(10)
      expect(afterGap[0] as number).toBeGreaterThanOrEqual(476_800)
      expect(afterGap[1] as number).toBe((afterGap[0] as number) + 1_600)
      // 全体を通して**前へだけ**進む（戻ると後端が「古い」として捨てる）
      for (let index = 1; index < positions.length; index += 1) {
        expect(positions[index] as number).toBeGreaterThan(positions[index - 1] as number)
      }
      stream.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })
})

/**
 * 一時停止（録音の停止）で送信ループだけを止める。**時間軸は画面が持つ**ので、
 * 続きの録音では画面が「保存済みの位置」を渡し直す（このクラスは渡された位置を積むだけ）。
 */
describe('授業録音：停止 → 続きの録音（時間軸は画面が持つ）', () => {
  it('停止して続きを録ったら、渡された位置から続けて送る', async () => {
    vi.useFakeTimers()
    try {
      const first = newStream({ startSample: 0, startFrameNo: 1 })
      first.append(samples(1), 48_000, 0)
      first.start()
      const firstSocket = FakeSocket.instances[0]
      firstSocket?.open()
      await vi.advanceTimersByTimeAsync(300)
      await vi.advanceTimersByTimeAsync(50)
      expect(frameHeaders(firstSocket).map((header) => header.startSample))
        .toEqual([0, 1_600, 3_200])
      first.closeSocket()

      // 続きの録音（画面が保存済みの位置 960,000 と、続きの番号 601 を渡す）
      const second = newStream({ startSample: 960_000, startFrameNo: 601 })
      second.append(samples(0.2), 48_000, 48_000 * 60)
      second.start()
      const secondSocket = FakeSocket.instances[1]
      secondSocket?.open()
      await vi.advanceTimersByTimeAsync(200)
      await vi.advanceTimersByTimeAsync(50)
      const headers = frameHeaders(secondSocket)
      expect(headers[0]?.startSample).toBe(960_000)
      expect(headers[0]?.no).toBe(601)
      second.closeSocket()
    } finally {
      vi.useRealTimers()
    }
  })
})

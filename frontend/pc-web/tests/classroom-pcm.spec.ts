import { describe, expect, it } from 'vitest'
import {
  PCM_CHUNK_SAMPLE_RATE, PcmChunkBuffer, TIMELINE_SAMPLE_RATE, resampleTo16k,
  timelineSamplesOf, timelineSeconds
} from '@/features/classroom/pcm'

/**
 * 授業録音：サーバー側 STT へ渡す PCM（16kHz モノラル 16bit）。
 *
 * <p>`MediaRecorder` の `timeslice` で切った 2 つ目以降の分塊はコンテナのヘッダを持たず、
 * 認識エンジンがデコードできない（実測）。そのため分塊ごとに**そのまま認識できる PCM** を作り、
 * 音声と一緒に送る。ここはその変換だけを確かめる（ブラウザの音声取得は実機の E2E で見る）。</p>
 */
describe('授業録音：STT 用の PCM 変換', () => {
  it('48kHz の音を 16kHz へ落とす（長さが 1/3 になり、振幅は保たれる）', () => {
    // 48kHz・1 秒のサイン波（振幅 0.5）
    const input = new Float32Array(48000)
    for (let i = 0; i < input.length; i += 1) input[i] = 0.5 * Math.sin((2 * Math.PI * 440 * i) / 48000)

    const output = resampleTo16k(input, 48000)

    expect(output.length).toBe(16000)
    const peak = output.reduce((max, value) => Math.max(max, Math.abs(value)), 0)
    // 0.5 × 32767 ≒ 16384（間引きで少し小さくなるが桁は変わらない）
    expect(peak).toBeGreaterThan(12000)
    expect(peak).toBeLessThanOrEqual(16384)
  })

  it('44.1kHz でも落とせる（レート比が整数でなくても壊れない）', () => {
    const input = new Float32Array(44100)
    input.fill(0.25)

    const output = resampleTo16k(input, 44100)

    // 44100 / 16000 = 2.75625 → 約 16000 サンプル
    expect(output.length).toBeGreaterThan(15990)
    expect(output.length).toBeLessThanOrEqual(16000)
    // 一定の音は一定のまま（平均を取るので値は保たれる）
    expect(output[100]).toBe(8192)
  })

  it('もともと 16kHz なら素通しに近い（間引きしない）', () => {
    const input = new Float32Array(1600)
    input.fill(0.5)

    const output = resampleTo16k(input, 16000)

    expect(output.length).toBe(1600)
    expect(output[0]).toBe(16384)
  })

  it('無音は 0 のまま（ノイズを足さない）', () => {
    const output = resampleTo16k(new Float32Array(4800), 48000)

    expect(output.length).toBe(1600)
    expect(Array.from(output).every((value) => value === 0)).toBe(true)
  })

  it('16bit の範囲を超える音はクリップする（符号の反転を起こさない）', () => {
    const input = new Float32Array(480).fill(2.5)

    const output = resampleTo16k(input, 48000)

    expect(Array.from(output).every((value) => value === 32767)).toBe(true)
  })

  it('分塊ごとに貯めて、区切りで取り出すと空になる', () => {
    const buffer = new PcmChunkBuffer()
    buffer.append(new Float32Array(480).fill(0.5))
    buffer.append(new Float32Array(480).fill(0.5))

    const chunk = buffer.take(48000)

    // 960 / 3 = 320 サンプル
    expect(chunk.length).toBe(320)
    expect(buffer.sampleCount).toBe(0)
    // 2 回目の取り出しは空（前の分塊を二重に送らない）
    expect(buffer.take(48000).length).toBe(0)
  })

  it('取り出した PCM は 16bit のリトルエンディアンにできる（サーバーが解釈する形）', () => {
    const buffer = new PcmChunkBuffer()
    buffer.append(new Float32Array(480).fill(0.5))

    const chunk = buffer.take(48000)
    const bytes = new Uint8Array(chunk.buffer, chunk.byteOffset, chunk.byteLength)

    // 16384 = 0x4000 → リトルエンディアンで 00 40
    expect(bytes[0]).toBe(0x00)
    expect(bytes[1]).toBe(0x40)
    expect(PCM_CHUNK_SAMPLE_RATE).toBe(16000)
  })
})

/**
 * 送信が追いつかないと PCM が貯まる一方になる。**1 回に送る量を抑え、古い音は捨てる**
 * （実測: 貯まった音を 1 フレームで送ると認識側の上限を超えて接続が切られた）。
 */
describe('授業録音：書き起こし用 PCM の貯め方', () => {
  it('trim は新しい分だけ残し、捨てた数を返す', () => {
    const buffer = new PcmChunkBuffer()
    buffer.append(new Float32Array(1000).fill(0.1))
    buffer.append(new Float32Array(1000).fill(0.2))
    buffer.append(new Float32Array(1000).fill(0.3))

    // 直近 1500 サンプルだけ残す → 古い 1500 を捨てる
    expect(buffer.trim(1500)).toBe(1500)
    expect(buffer.sampleCount).toBe(1500)

    // 残っているのは新しい 2 つめ（0.2 の後半）と 3 つめ（0.3）
    const left = buffer.take(16000)
    expect(left.length).toBe(1500)
    expect(left[0]).toBeGreaterThan(0)
  })

  it('貯まっていなければ何も捨てない', () => {
    const buffer = new PcmChunkBuffer()
    buffer.append(new Float32Array(100))
    expect(buffer.trim(1000)).toBe(0)
    expect(buffer.sampleCount).toBe(100)
  })
})

/**
 * 授業録音：**録音の時間軸**（アイテム 3）。
 *
 * <p>マイクと共有の音は別の `AudioContext` から取り出すので、取り出しのレートが違うことがある
 * （48kHz / 44.1kHz）。送信のたびに「そのセッションで何サンプル送ったか」を数えると、
 * 張り直し・後端の再起動で 0 に戻ってしまう。**書き起こしへ送る PCM と同じ 16kHz の
 * サンプル位置**で表せば、音源が違っても・セッションが切れても 1 つの時間軸になる。</p>
 */
describe('授業録音：録音の時間軸（16kHz のサンプル位置）', () => {
  it('入力レートのサンプル数を 16kHz の位置へ直す（レートが違っても同じ長さになる）', () => {
    expect(TIMELINE_SAMPLE_RATE).toBe(16_000)
    // 100ms ぶんは、どのレートでも 1600 サンプル（16kHz の 0.1 秒）
    expect(timelineSamplesOf(4_800, 48_000)).toBe(1_600)
    expect(timelineSamplesOf(4_410, 44_100)).toBe(1_600)
    expect(timelineSamplesOf(1_600, 16_000)).toBe(1_600)
  })

  it('送った量が増えれば位置は単調に増える（戻らない）', () => {
    const positions = [0, 1, 9, 10, 11].map((frames) => timelineSamplesOf(frames * 4_800, 48_000))
    expect(positions).toEqual([0, 1_600, 14_400, 16_000, 17_600])
    // 10 フレームぶん進んだ差は、途中で測っても同じ 1600（＝区切りの取り方に依存しない）
    expect(timelineSamplesOf(4_800 * 10, 48_000) - timelineSamplesOf(4_800 * 9, 48_000)).toBe(1_600)
  })

  it('おかしなレートでも壊れない（16kHz として扱う）', () => {
    expect(timelineSamplesOf(1_600, 0)).toBe(1_600)
    expect(timelineSamplesOf(1_600, Number.NaN)).toBe(1_600)
    expect(timelineSamplesOf(-100, 48_000)).toBe(0)
  })

  it('サンプル数を秒へ直す（画面と後端のミリ秒の基準）', () => {
    expect(timelineSeconds(0)).toBe(0)
    expect(timelineSeconds(1_600)).toBe(0.1)
    expect(timelineSeconds(112_000)).toBe(7)
    expect(timelineSeconds(1)).toBe(0)
  })
})

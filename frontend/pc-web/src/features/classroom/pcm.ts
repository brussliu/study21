/**
 * 授業録音：**サーバー側 STT へ渡す PCM**（16kHz モノラル 16bit）を作る。
 *
 * <p>録音そのものは `MediaRecorder`（webm/opus）が行い、それは再生用として保存する。
 * ただし `MediaRecorder` の `timeslice` で切った**2 つ目以降の分塊はコンテナのヘッダを持たず**、
 * 音声認識エンジンがデコードできない（実測: 1 塊目だけ EBML ヘッダがあり、2 塊目以降は無い）。
 * そのため分塊ごとに「そのまま認識できる形」を作り、音声と一緒に送る（`POST /classroom/{id}/chunks`
 * の `stt` パート）。</p>
 *
 * <p>ここは変換だけを持つ純粋な処理（ブラウザの音声取得は `ClassroomLiveView` が行い、
 * 実機では E2E で確かめる）。</p>
 */

/** サーバー（Google / 阿里巴巴）へ渡すサンプリング周波数。 */
export const PCM_CHUNK_SAMPLE_RATE = 16000

/** MIME（サーバーは `audio/L16` を「ヘッダ無しの 16bit PCM」として解釈する）。 */
export const PCM_CHUNK_MIME = 'audio/L16'

/** 16bit PCM の振幅の上限（符号つき）。 */
const INT16_MAX = 32767
const INT16_MIN = -32768

/**
 * **録音の時間軸**のサンプリング周波数（書き起こしへ送る PCM と同じ 16kHz）。
 *
 * <p>マイクと共有の音は別の `AudioContext` から取り出すので、取り出しのレートが違うことがある
 * （48kHz / 44.1kHz）。「そのセッションで何サンプル送ったか」を数えると張り直し・後端の再起動で
 * 0 に戻ってしまうので、位置は**送る PCM と同じ 16kHz のサンプル数**で数える
 * （音源が違っても・セッションが切れても 1 つの時間軸になる。後端のミリ秒ともそのまま対応する）。</p>
 */
export const TIMELINE_SAMPLE_RATE = 16_000

/**
 * 入力レートのサンプル数を**録音の時間軸（16kHz）のサンプル位置**へ直す。
 *
 * <p>切り捨てで整数にする（丸めない）: 送った量が増えれば必ず増える単調な値が要るため
 * （丸めると前後で同じ値が出たり、わずかに戻ることがある）。</p>
 */
export function timelineSamplesOf(inputSamples: number, inputRate: number): number {
  const rate = Number.isFinite(inputRate) && inputRate > 0 ? inputRate : TIMELINE_SAMPLE_RATE
  const samples = Number.isFinite(inputSamples) && inputSamples > 0 ? inputSamples : 0
  return Math.floor(samples * TIMELINE_SAMPLE_RATE / rate)
}

/** 録音の時間軸のサンプル位置を秒へ直す（画面に出す値・後端のミリ秒と同じ基準）。 */
export function timelineSeconds(samples: number): number {
  const value = Number.isFinite(samples) && samples > 0 ? samples / TIMELINE_SAMPLE_RATE : 0
  return Math.round(value * 1000) / 1000
}

/**
 * Float32（AudioContext のレート。48kHz / 44.1kHz など）を 16kHz へ落とす。
 *
 * <p>間引くときに平均を取る（そのまま捨てるより高域の折り返しが減り、認識の邪魔をしにくい）。
 * レート比が整数でなくても動く。</p>
 */
export function resampleTo16k(input: Float32Array, inputRate: number): Int16Array {
  const rate = Number.isFinite(inputRate) && inputRate > 0 ? inputRate : PCM_CHUNK_SAMPLE_RATE
  const ratio = rate / PCM_CHUNK_SAMPLE_RATE
  // 16kHz 以下は間引かない（そのまま 16bit へ）
  const step = ratio <= 1 ? 1 : ratio
  const outLength = ratio <= 1 ? input.length : Math.floor(input.length / ratio)
  const output = new Int16Array(Math.max(0, outLength))
  for (let index = 0; index < output.length; index += 1) {
    const start = Math.floor(index * step)
    const end = Math.min(input.length, Math.max(start + 1, Math.floor((index + 1) * step)))
    let sum = 0
    for (let sample = start; sample < end; sample += 1) sum += input[sample]
    const average = sum / Math.max(1, end - start)
    output[index] = clampToInt16(Math.round(average * INT16_MAX))
  }
  return output
}

function clampToInt16(value: number): number {
  if (!Number.isFinite(value)) return 0
  if (value > INT16_MAX) return INT16_MAX
  if (value < INT16_MIN) return INT16_MIN
  return value
}

/**
 * 分塊の区切りまで PCM を貯めて、区切りで取り出す。
 *
 * <p>区切りは `MediaRecorder` の `ondataavailable` に合わせる（同じ間隔の音声を STT へ送るため）。</p>
 */
export class PcmChunkBuffer {
  private blocks: Float32Array[] = []
  private length = 0

  /** 音声の一部を足す（AudioWorklet から届いた分）。 */
  append(samples: Float32Array): void {
    if (samples.length === 0) return
    this.blocks.push(samples)
    this.length += samples.length
  }

  /** 貯めたサンプル数。 */
  get sampleCount(): number {
    return this.length
  }

  /** 貯めた分を 16kHz の 16bit PCM にして返す（返したら空になる）。 */
  take(inputRate: number): Int16Array {
    if (this.length === 0) return new Int16Array(0)
    const merged = new Float32Array(this.length)
    let offset = 0
    for (const block of this.blocks) {
      merged.set(block, offset)
      offset += block.length
    }
    this.blocks = []
    this.length = 0
    return resampleTo16k(merged, inputRate)
  }

  /**
   * 先頭から最大 `maxInputSamples` サンプルだけ取り出して 16kHz の 16bit PCM にする。
   *
   * <p>返した分だけバッファから消える（残りは次の呼び出しで取る）。**貯まった音を一度に
   * 全部送らない**ために使う（1 回の送信を小さく保ち、遅れを取り戻しやすくする）。</p>
   *
   * @returns `pcm`（16kHz 16bit）と、元のレートで取り出したサンプル数 `inputSamples`
   */
  takeBounded(inputRate: number, maxInputSamples: number): { pcm: Int16Array; inputSamples: number } {
    if (this.length === 0 || maxInputSamples <= 0) {
      return { pcm: new Int16Array(0), inputSamples: 0 }
    }
    const take = Math.min(maxInputSamples, this.length)
    const merged = new Float32Array(take)
    let offset = 0
    while (offset < take && this.blocks.length > 0) {
      const first = this.blocks[0]
      const need = take - offset
      if (first.length <= need) {
        merged.set(first, offset)
        offset += first.length
        this.blocks.shift()
      } else {
        merged.set(first.subarray(0, need), offset)
        this.blocks[0] = first.subarray(need)
        offset += need
      }
    }
    this.length -= take
    return { pcm: resampleTo16k(merged, inputRate), inputSamples: take }
  }

  /** 貯めた分を捨てる（録音を止めたとき・送らないとき）。 */
  clear(): void {
    this.blocks = []
    this.length = 0
  }

  /**
   * いちばん新しい `keep` サンプルだけ残して、古い分を捨てる。
   *
   * <p>送信が追いつかないと（サーバーの往復が遅い・分塊が大きい）、貯まる一方になって
   * 書き起こしが遅れ、1 回に送る量も膨らむ。**古い音を捨てて今の音を送る**ほうが
   * 授業の書き起こしとしては役に立つ（音声そのものは録音側に残っている）。</p>
   *
   * @return 捨てたサンプル数（0 なら何も捨てていない）
   */
  trim(keep: number): number {
    if (keep <= 0) {
      const dropped = this.length
      this.clear()
      return dropped
    }
    if (this.length <= keep) return 0
    const before = this.length
    let toDrop = this.length - keep
    while (toDrop > 0 && this.blocks.length > 0) {
      const first = this.blocks[0]
      if (first.length <= toDrop) {
        this.blocks.shift()
        this.length -= first.length
        toDrop -= first.length
      } else {
        this.blocks[0] = first.subarray(toDrop)
        this.length -= toDrop
        toDrop = 0
      }
    }
    return before - this.length
  }
}

/**
 * サーバー側 STT 用の音声を取るための AudioWorklet のソース。
 *
 * <p>マイクの音をそのまま渡すだけ（加工しない）。`AudioWorklet` を使うのは、
 * メインスレッドの負荷に左右されずにサンプルを取りこぼさないため。
 * 別ファイルにすると Vite の設定が要るので、Blob から読み込む。</p>
 */
export const PCM_CAPTURE_WORKLET_SOURCE = `
class Study21PcmCapture extends AudioWorkletProcessor {
  process (inputs) {
    const input = inputs[0]
    if (input && input[0]) {
      this.port.postMessage(input[0].slice(0))
    }
    return true
  }
}
registerProcessor('study21-pcm-capture', Study21PcmCapture)
`

/** AudioWorklet のモジュール名（この名前で registerProcessor している）。 */
export const PCM_CAPTURE_PROCESSOR = 'study21-pcm-capture'

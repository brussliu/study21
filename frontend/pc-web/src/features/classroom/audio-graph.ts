/**
 * 授業録音の**音源の混ぜ方**（マイク＋PC の音）。
 *
 * <p>現場の授業はマイクだけで足りるが、オンライン授業（腾讯会议・ブラウザの網課）では
 * **先生の声は PC が再生している音**なので、マイクだけでは録れない（ヘッドホンだと特に録れない）。
 * そこで「マイク」＋「共有した音（タブ／画面の音声）」を **AudioContext で本当に混ぜて**、
 * 録音（`MediaRecorder`）と書き起こし用 PCM の両方へ同じ混ざった音を渡す。</p>
 *
 * <p><b>しないこと</b>（事故を防ぐため）:</p>
 * <ul>
 *   <li>混ぜた音を `context.destination`（スピーカー）へ**つながない**。つなぐと PC の音が
 *       もう一度再生され、会議相手に戻ってハウリングする。</li>
 *   <li>映像は使わない・保存しない。共有では映像トラックが必要なので**保持はするが**、
 *       混ぜるのは音声トラックだけ。</li>
 * </ul>
 */
import { PCM_CHUNK_SAMPLE_RATE } from '@/features/classroom/pcm'

/**
 * 録音の音源モード。
 *
 * - `mic` … 対面の授業（マイクだけ）
 * - `mic-pc` … オンライン授業。**PC が再生している音（先生の声）**を共有でもらって混ぜる。
 *   タブの音と画面全体の音は**利用者から見て同じ操作**（共有の選択で選ぶ）なので 1 つに統合した
 *   （2026-09-18 利用者の指示）。
 */
export type ClassroomAudioMode = 'mic' | 'mic-pc'

/** 画面に出すモードの説明（日本語。画面と実機で同じ文言を使う）。 */
export const AUDIO_MODE_LABELS: Record<ClassroomAudioMode, string> = {
  mic: 'マイクのみ（対面の授業）',
  'mic-pc': 'マイク＋スピーカー（オンライン授業）'
}

/**
 * 統合前に選べた値（`mic-screen` / `mic-tab`）の読み替え。
 * 統合前に覚えた選択（`localStorage`）と、その頃の URL（`?audioMode=`）が残っていても
 * 「マイク＋スピーカー」として動かす（利用者に選び直させない）。
 */
const LEGACY_AUDIO_MODES: Record<string, ClassroomAudioMode> = {
  'mic-screen': 'mic-pc',
  'mic-tab': 'mic-pc'
}

/**
 * 選んだ音源を覚えておく鍵（**【新しい授業】で選び、録音中の画面が読む**）。
 * オンライン授業が続く週に、毎回「マイク＋スピーカー」を選び直さなくてよいようにする。
 */
export const AUDIO_MODE_STORAGE_KEY = 'study21.classroom.audioMode'

/**
 * 値（クエリ・`localStorage`）を音源モードにする。
 * 知らない値・壊れた値のときは null（呼び出し側が既定へ寄せる）。
 */
export function toClassroomAudioMode(value: unknown): ClassroomAudioMode | null {
  if (typeof value !== 'string') return null
  if (Object.prototype.hasOwnProperty.call(AUDIO_MODE_LABELS, value)) {
    return value as ClassroomAudioMode
  }
  return LEGACY_AUDIO_MODES[value] ?? null
}

/**
 * 前回選んだ音源を読む。
 * 覚えていない・知らない値・使えない環境（プライベートモード等）では今までどおりマイクだけ。
 */
export function loadAudioMode(): ClassroomAudioMode {
  try {
    const saved = toClassroomAudioMode(window.localStorage.getItem(AUDIO_MODE_STORAGE_KEY))
    if (saved !== null) return saved
  } catch {
    // 読めない環境では今までどおりマイクだけ
  }
  return 'mic'
}

/** 選んだ音源を覚える（覚えられなくても録音には影響しない）。 */
export function saveAudioMode(mode: ClassroomAudioMode): void {
  try {
    window.localStorage.setItem(AUDIO_MODE_STORAGE_KEY, mode)
  } catch {
    // 覚えられなくても録音には影響しない
  }
}

/**
 * **その記録の音源**を覚える鍵の前置き（`…audioMode.record.12`）。
 *
 * <p>端末の「前回の選択」（{@link AUDIO_MODE_STORAGE_KEY}）は**別の記録**にも使う値なので、
 * 記録ごとの設定は分けて持つ。詳細画面はこれを読んで話者を決める（利用者の指示 7 節:
 * 話者は**その記録の設定**で決める。開いている URL の query＝録音中の画面から渡された値では
 * 決めない＝一覧・履歴・ブックマークから開いても話者が変わらない）。</p>
 */
export const RECORD_AUDIO_MODE_STORAGE_PREFIX = 'study21.classroom.audioMode.record.'

/** その記録の音源を覚える（**録音を始めたときに、実際に録音した音源**を残す）。 */
export function saveRecordAudioMode(recordId: number, mode: ClassroomAudioMode): void {
  try {
    window.localStorage.setItem(`${RECORD_AUDIO_MODE_STORAGE_PREFIX}${recordId}`, mode)
  } catch {
    // 覚えられなくても録音・表示は続けられる（分からないときはサーバーの話者をそのまま出す）
  }
}

/**
 * その記録の音源を読む（覚えていない・知らない値のときは null＝**分からない**）。
 *
 * <p>null を「マイクのみ」に丸めない: 分からない記録の話者を勝手に決めると、二音源の録音が
 * **開き方によって「講義」に見える**（サーバーが記録した話者をそのまま出すのが正しい）。</p>
 */
export function loadRecordAudioMode(recordId: number): ClassroomAudioMode | null {
  try {
    return toClassroomAudioMode(
      window.localStorage.getItem(`${RECORD_AUDIO_MODE_STORAGE_PREFIX}${recordId}`))
  } catch {
    return null
  }
}

/** 二つの音を混ぜるときの初期音量（遠隔音を少し下げて、割れにくくする）。 */
const DEFAULT_MIC_GAIN = 1
const DEFAULT_REMOTE_GAIN = 0.8

/** 音が割れないように最後に軽く抑える（急な大音量だけを落とす）。 */
const LIMITER = { threshold: -6, knee: 0, ratio: 20, attack: 0.003, release: 0.25 }

/** 音量表示（0〜1）をならす強さ（小さいほど滑らか）。 */
const LEVEL_SMOOTHING = 0.4
/** RMS を 0〜1 の見た目に持ち上げる係数（人の声は小さいので少し増幅して見せる）。 */
const LEVEL_GAIN = 3

/** 混ぜた音の束（画面がこれを使って録音・採取・表示する）。 */
export interface ClassroomAudioGraph {
  /** 録音（`MediaRecorder`）へ渡す**混ぜた後**の流れ。 */
  readonly stream: MediaStream
  /** 書き起こし用 PCM の採取（AudioWorklet）をつなぐ先＝混ぜた後のノード。 */
  readonly mixed: AudioNode
  /** マイクと遠隔（共有音声）の音量（0〜1。表示用）。 */
  levels(): { mic: number; remote: number }
  /** マイクのトラック（機器変化・停止の検知に使う。取れないときは null）。 */
  readonly micTrack: MediaStreamTrack | null
  /** 共有音声のトラック（マイクのみのときは null）。 */
  readonly remoteTrack: MediaStreamTrack | null
  /** 共有音声を入れ替える（共有を選び直したとき）。古い方は切り離す。 */
  replaceRemote(stream: MediaStream | null): void
  /** すべて切り離して解放する（AudioContext は呼び出し側が閉じる）。 */
  close(): void
}

/** 作るときの指定。 */
export interface ClassroomAudioGraphOptions {
  /** 既存の AudioContext（PCM 採取と共有する）。 */
  context: AudioContext
  /** 混ぜる遠隔音（画面共有の音声）。無ければマイクのみ。 */
  remote?: MediaStream | null
  micGain?: number
  remoteGain?: number
}

/**
 * マイク（と任意で共有音声）を混ぜた束を作る。
 *
 * @param mic マイクの流れ（`getUserMedia` の結果）
 */
export function createClassroomAudioGraph(
  mic: MediaStream,
  options: ClassroomAudioGraphOptions
): ClassroomAudioGraph {
  const context = options.context
  const destination = context.createMediaStreamDestination()
  const limiter = context.createDynamicsCompressor()
  limiter.threshold.value = LIMITER.threshold
  limiter.knee.value = LIMITER.knee
  limiter.ratio.value = LIMITER.ratio
  limiter.attack.value = LIMITER.attack
  limiter.release.value = LIMITER.release
  // 混ぜた音は**録音と採取のためだけ**に使う（スピーカーへはつながない）
  limiter.connect(destination)

  const nodes: AudioNode[] = [limiter, destination]
  const micSource = context.createMediaStreamSource(mic)
  const micGainNode = context.createGain()
  micGainNode.gain.value = options.micGain ?? DEFAULT_MIC_GAIN
  micSource.connect(micGainNode)
  micGainNode.connect(limiter)
  nodes.push(micSource, micGainNode)

  const micAnalyser = context.createAnalyser()
  micAnalyser.fftSize = 1024
  micGainNode.connect(micAnalyser)
  nodes.push(micAnalyser)

  let remoteSource: MediaStreamAudioSourceNode | null = null
  let remoteGainNode: GainNode | null = null
  let remoteAnalyser: AnalyserNode | null = null
  let remoteTrack: MediaStreamTrack | null = null

  /** 共有音声を graph へつなぐ（映像トラックは使わない）。 */
  const attachRemote = (stream: MediaStream | null): void => {
    if (remoteSource !== null) {
      remoteSource.disconnect()
      remoteGainNode?.disconnect()
      remoteAnalyser?.disconnect()
    }
    remoteSource = null
    remoteGainNode = null
    remoteAnalyser = null
    remoteTrack = null
    if (stream === null) return
    // 音声トラックだけを混ぜる（MediaStreamAudioSourceNode は音声トラックだけを使う＝映像は混ざらない）
    remoteTrack = stream.getAudioTracks()[0] ?? null
    if (remoteTrack === null) return
    remoteSource = context.createMediaStreamSource(stream)
    remoteGainNode = context.createGain()
    remoteGainNode.gain.value = options.remoteGain ?? DEFAULT_REMOTE_GAIN
    remoteAnalyser = context.createAnalyser()
    remoteAnalyser.fftSize = 1024
    remoteSource.connect(remoteGainNode)
    remoteGainNode.connect(limiter)
    remoteGainNode.connect(remoteAnalyser)
    nodes.push(remoteSource, remoteGainNode, remoteAnalyser)
  }
  attachRemote(options.remote ?? null)

  const micBuffer = new Float32Array(micAnalyser.fftSize)
  const remoteBuffer = new Float32Array(1024)
  let micLevel = 0
  let remoteLevel = 0

  /** アナライザーから 0〜1 の音量を出す（RMS。声は小さいので少し持ち上げる）。 */
  const levelOf = (analyser: AnalyserNode, buffer: Float32Array): number => {
    analyser.getFloatTimeDomainData(buffer)
    let sum = 0
    for (let index = 0; index < buffer.length; index += 1) sum += buffer[index] * buffer[index]
    const rms = Math.sqrt(sum / buffer.length)
    return Math.min(1, rms * LEVEL_GAIN)
  }

  return {
    stream: destination.stream,
    mixed: limiter,
    micTrack: mic.getAudioTracks()[0] ?? null,
    get remoteTrack() { return remoteTrack },
    levels(): { mic: number; remote: number } {
      const nextMic = levelOf(micAnalyser, micBuffer)
      const nextRemote = remoteAnalyser === null ? 0 : levelOf(remoteAnalyser, remoteBuffer)
      // ならす（ぱたぱた動かないように）
      micLevel = micLevel + (nextMic - micLevel) * LEVEL_SMOOTHING
      remoteLevel = remoteLevel + (nextRemote - remoteLevel) * LEVEL_SMOOTHING
      return { mic: micLevel, remote: remoteLevel }
    },
    replaceRemote(stream: MediaStream | null): void {
      attachRemote(stream)
    },
    close(): void {
      for (const node of nodes) {
        try {
          node.disconnect()
        } catch {
          // すでに切れているときは何もしない
        }
      }
      // 音源そのものは呼び出し側（画面）が止める
    }
  }
}

/** 画面共有で音声をもらうときの指定（Chrome / Edge on Windows を想定）。 */
export function displayAudioConstraints(): { video: boolean; audio: { suppressLocalAudioPlayback: boolean } } {
  return {
    // 映像は「共有を続けるため」に必要（保存も送信もしない）
    video: true,
    audio: {
      /*
       * **共有した音はそのまま聞こえるようにする**。
       *
       * <p>`true` にすると、共有した瞬間から**そのタブ／画面の音が聞こえなくなる**
       * （実測: 利用者から「録音を始めたらブラウザの音が聞こえなくなった」と報告）。
       * 授業を受けながら録る使い方なので、聞こえないと成立しない。</p>
       *
       * <p>マイクへ音が回るのを防ぐのはこの指定の役目ではない（スピーカーで聞けば
       * マイクにも入る）。画面では**ヘッドホン**を勧める。</p>
       */
      suppressLocalAudioPlayback: false
    }
  }
}

/** 共有の音声が取れているか（取れていないときは画面で明確に知らせる）。 */
export function hasAudioTrack(stream: MediaStream | null | undefined): boolean {
  return stream !== null && stream !== undefined && stream.getAudioTracks().length > 0
}

/** 書き起こし用 PCM のサンプリング周波数（サーバーへ渡す値。画面と合わせる）。 */
export const STREAM_SAMPLE_RATE = PCM_CHUNK_SAMPLE_RATE

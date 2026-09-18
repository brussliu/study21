import { afterEach, describe, expect, it, vi } from 'vitest'
import {
  createClassroomAudioGraph,
  displayAudioConstraints,
  hasAudioTrack,
  loadAudioMode,
  saveAudioMode,
  toClassroomAudioMode,
  AUDIO_MODE_LABELS,
  AUDIO_MODE_STORAGE_KEY
} from '@/features/classroom/audio-graph'

/**
 * 授業録音：**マイク＋PC の音を混ぜる**ところ（オンライン授業で先生の声も録る）。
 *
 * <p>実機の `getDisplayMedia` はヘッドレスで確かめにくいので、ここでは「どう混ぜるか」を固定する:
 * マイクと共有音声の両方をリミッターへつなぐ / スピーカーへは**つながない**（ハウリング防止） /
 * 映像トラックは混ぜない / 共有を選び直したら入れ替える / 解放で全部切れる。</p>
 */
class FakeNode {
  readonly connected: FakeNode[] = []
  disconnected = 0
  connect(target: FakeNode): FakeNode { this.connected.push(target); return target }
  disconnect(): void { this.disconnected += 1 }
  gain = { value: 1 }
  threshold = { value: 0 }
  knee = { value: 0 }
  ratio = { value: 1 }
  attack = { value: 0 }
  release = { value: 0 }
}

class FakeAnalyser extends FakeNode {
  fftSize = 1024
  /** テストが入れる波形（既定は無音）。 */
  samples: number[] = []
  getFloatTimeDomainData(buffer: Float32Array): void {
    for (let index = 0; index < buffer.length; index += 1) buffer[index] = this.samples[index] ?? 0
  }
}

class FakeContext {
  analysers: FakeAnalyser[] = []
  limiter: FakeNode | null = null
  destinationNode: { stream: MediaStream } | null = null
  destination = {} as AudioDestinationNode
  createMediaStreamDestination(): { stream: MediaStream } {
    // jsdom には MediaStream が無いので、同一性だけを見られる代役にする
    this.destinationNode = { stream: { id: 'mixed' } as unknown as MediaStream }
    return this.destinationNode
  }
  createDynamicsCompressor(): FakeNode {
    this.limiter = new FakeNode()
    return this.limiter
  }
  createGain(): FakeNode { return new FakeNode() }
  createMediaStreamSource(): FakeNode { return new FakeNode() }
  createAnalyser(): FakeAnalyser {
    const analyser = new FakeAnalyser()
    this.analysers.push(analyser)
    return analyser
  }
}

function track(kind: 'audio' | 'video'): MediaStreamTrack {
  return { kind, stop: vi.fn() } as unknown as MediaStreamTrack
}

function streamOf(tracks: MediaStreamTrack[]): MediaStream {
  return {
    getAudioTracks: () => tracks.filter((item) => item.kind === 'audio'),
    getVideoTracks: () => tracks.filter((item) => item.kind === 'video'),
    getTracks: () => tracks
  } as unknown as MediaStream
}

afterEach(() => {
  // 画面が覚えている音源の選択を次のテストへ持ち越さない
  localStorage.clear()
})

describe('授業録音：音源のミックス（マイク＋PC の音）', () => {
  it('マイクと共有音声を同じリミッターへつなぎ、録音用の流れを作る', () => {
    const context = new FakeContext()
    const mic = streamOf([track('audio')])
    const remote = streamOf([track('audio'), track('video')])

    const graph = createClassroomAudioGraph(mic, {
      context: context as unknown as AudioContext,
      remote
    })

    // 混ぜた音は「録音と採取のため」の流れ（MediaStreamAudioDestinationNode）
    expect(graph.stream).toBe(context.destinationNode?.stream)
    expect(graph.mixed).toBe(context.limiter)
    // マイクと共有音声の両方がリミッターにつながっている
    const limiterInputs = context.analysers.length
    expect(limiterInputs).toBe(2)
    // 共有音声のトラックは音声だけを使う（映像は混ぜない）
    expect(graph.remoteTrack?.kind).toBe('audio')
  })

  it('スピーカーへはつながない（ハウリング防止）', () => {
    const context = new FakeContext()
    const graph = createClassroomAudioGraph(streamOf([track('audio')]), {
      context: context as unknown as AudioContext
    })

    // 出力（destination）へ connect していないこと
    const limiter = context.limiter as unknown as FakeNode
    expect(limiter.connected).not.toContain(context.destination)
    // 混ぜた音の行き先は「録音用の流れ」だけ
    expect(graph.stream).toBe(context.destinationNode?.stream)
  })

  it('音量は 0〜1 で返り、声が大きいほど大きくなる', () => {
    const context = new FakeContext()
    const graph = createClassroomAudioGraph(streamOf([track('audio')]), {
      context: context as unknown as AudioContext
    })

    expect(graph.levels().mic).toBe(0)
    // マイクの波形を大きくする（アナライザーは最初の 1 つ＝マイク）
    context.analysers[0].samples = new Array(1024).fill(0.5)
    const loud = graph.levels().mic
    expect(loud).toBeGreaterThan(0)
    expect(loud).toBeLessThanOrEqual(1)
  })

  it('共有を選び直すと、古い共有音声を切り離して入れ替える', () => {
    const context = new FakeContext()
    const graph = createClassroomAudioGraph(streamOf([track('audio')]), {
      context: context as unknown as AudioContext,
      remote: streamOf([track('audio')])
    })
    const before = context.analysers.length

    graph.replaceRemote(streamOf([track('audio')]))

    // 解析器が作り直される（＝古い接続は切られている）
    expect(context.analysers.length).toBeGreaterThan(before)
    expect(graph.remoteTrack).not.toBeNull()

    // 共有をやめたときは遠隔音なしに戻せる
    graph.replaceRemote(null)
    expect(graph.remoteTrack).toBeNull()
    expect(graph.levels().remote).toBe(0)
  })

  it('解放すると全部の節点を切り離す', () => {
    const context = new FakeContext()
    const graph = createClassroomAudioGraph(streamOf([track('audio')]), {
      context: context as unknown as AudioContext,
      remote: streamOf([track('audio')])
    })

    graph.close()

    expect(context.limiter?.disconnected).toBeGreaterThan(0)
    for (const analyser of context.analysers) expect(analyser.disconnected).toBeGreaterThan(0)
  })

  it('共有では映像が必要だが、音声が無い共有は「音が取れていない」と分かる', () => {
    // 映像だけの共有（タブの音声をチェックし忘れた場合）
    expect(hasAudioTrack(streamOf([track('video')]))).toBe(false)
    expect(hasAudioTrack(streamOf([track('audio'), track('video')]))).toBe(true)
    expect(hasAudioTrack(null)).toBe(false)
    const constraints = displayAudioConstraints()
    expect(constraints.video).toBe(true)
    /*
     * **共有した音はそのまま聞こえるようにする**（実測: `true` にすると録音を始めた瞬間に
     * ブラウザ／会議の音が聞こえなくなる＝利用者から報告があった）。マイクへ音を戻す心配は
     * この指定では無く、スピーカーで聞くかヘッドホンで聞くかの問題（画面で案内する）。
     */
    expect((constraints.audio as unknown as { suppressLocalAudioPlayback?: boolean })
      .suppressLocalAudioPlayback).toBe(false)
  })

  it('モードの文言は日本語で、2 つ（マイクのみ／マイク＋スピーカー）そろっている', () => {
    // タブの音と画面共有の音は**利用者から見て同じ操作**（共有の選択で選ぶ）なので 1 つに統合した
    expect(Object.keys(AUDIO_MODE_LABELS).sort()).toEqual(['mic', 'mic-pc'])
    expect(AUDIO_MODE_LABELS.mic).toContain('マイクのみ')
    expect(AUDIO_MODE_LABELS.mic).toContain('対面')
    expect(AUDIO_MODE_LABELS['mic-pc']).toContain('スピーカー')
    expect(AUDIO_MODE_LABELS['mic-pc']).toContain('オンライン授業')
  })
})

/**
 * 音源の選択は【新しい授業】で行い、**覚えておく**（オンライン授業が続く週に選び直させない）。
 * 画面（一覧の dialog と録音中）が同じ鍵を使うので、読み書きは 1 か所に置く。
 */
describe('授業録音：音源の選択を覚えておく', () => {
  it('選んだ音源を覚え、次に読んだときは同じ値が返る', () => {
    // 何も選んでいないときはマイクだけ（今までどおり）
    expect(loadAudioMode()).toBe('mic')

    saveAudioMode('mic-pc')
    expect(localStorage.getItem(AUDIO_MODE_STORAGE_KEY)).toBe('mic-pc')
    expect(loadAudioMode()).toBe('mic-pc')
  })

  it('統合前の値（mic-screen / mic-tab）も「マイク＋スピーカー」として読める', () => {
    // 統合前に選んだ人の記憶（localStorage）と、その頃の URL が残っていても壊さない
    localStorage.setItem(AUDIO_MODE_STORAGE_KEY, 'mic-tab')
    expect(loadAudioMode()).toBe('mic-pc')
    localStorage.setItem(AUDIO_MODE_STORAGE_KEY, 'mic-screen')
    expect(loadAudioMode()).toBe('mic-pc')
    expect(toClassroomAudioMode('mic-tab')).toBe('mic-pc')
    expect(toClassroomAudioMode('mic-screen')).toBe('mic-pc')
  })

  it('知らない値・壊れた値のときはマイクだけにする', () => {
    localStorage.setItem(AUDIO_MODE_STORAGE_KEY, 'unknown-value')
    expect(loadAudioMode()).toBe('mic')
  })

  it('値が音源モードかどうかを判定できる（クエリの値をそのまま使わない）', () => {
    expect(toClassroomAudioMode('mic')).toBe('mic')
    expect(toClassroomAudioMode('mic-pc')).toBe('mic-pc')
    expect(toClassroomAudioMode('mic-tab ')).toBeNull()
    expect(toClassroomAudioMode('unknown-value')).toBeNull()
    expect(toClassroomAudioMode(undefined)).toBeNull()
    expect(toClassroomAudioMode(null)).toBeNull()
    expect(toClassroomAudioMode(3)).toBeNull()
  })
})

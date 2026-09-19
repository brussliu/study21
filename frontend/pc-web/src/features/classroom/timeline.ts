/**
 * 授業録音 — **録音と回放の 1 つの時間軸**（マイクと共有の音で共有する）。
 *
 * <p><b>規則（`docs/DECISIONS.md` に理由を書く）</b></p>
 * <ol>
 *   <li><b>基準は累計のサンプル位置</b>（16kHz。{@link TIMELINE_SAMPLE_RATE}）。
 *       `Date.now()` も画面のタイマーも使わない（どちらも録音の実体とずれる）。</li>
 *   <li><b>一時停止のあいだは進めない</b>。録音していない時間は録音の音にも入っていないので、
 *       進めてしまうと**回放の位置と書き起こしの時刻が食い違う**。停止 → 再開では、
 *       再開した時点を 0 として**続きの位置から**積む（0 へ戻さない・隙間を詰めない）。</li>
 *   <li><b>進めるのは 1 つの報告につき 1 回</b>。採った（`observeCapture`）・送った
 *       （`observeSent`）・確認できた（`observeConfirmed`）は**それぞれ別の口**で、
 *       同じ位置を 2 度進めない（二重に進めると文が音より先へずれる）。</li>
 *   <li><b>位置は前へだけ進む</b>（`max` を採る）。送り直し・断線からの復帰・後端の再起動で
 *       古い位置の報告が来ても、時間は戻らない。</li>
 * </ol>
 *
 * <p><b>なぜ音源で 1 つか</b>: 画面は音源ごとに別の `SourceStream` を持つが、**時刻は 1 つ**で
 * なければならない（転写の並び・重なった発言・回放の位置が同じ基準で比べられない）。
 * 音源ごとの位置は {@link RecordingTimeline.sourceSample} でこの時間軸から写す。</p>
 */
import { TIMELINE_SAMPLE_RATE, timelineSamplesOf } from '@/features/classroom/pcm'

/** 音源の名前（`mic` / `shared`。API の値と同じ）。 */
export type TimelineSourceKey = string

/** 採った（いまの音）・送った・確認できたの**3 つの位置**（16kHz のサンプル数）。 */
export interface TimelinePositions {
  /** いまの音の位置（音源から採った累計）。 */
  capturedSamples: number
  /** 送った累計（重複を含む「投げた」位置）。 */
  sentSamples: number
  /** サーバーが確認した累計。**未確認の音を「回放できる」と言わない**ための位置。 */
  confirmedSamples: number
}

/**
 * 録音回放の時間軸。
 *
 * <p>使い方は「すべての節点を用意する → {@link begin} → 録音 → {@link pause} →
 * （保存済みの位置を {@link restore} して）{@link resume}」の順。`begin` を呼ぶ前に来た報告は
 * 位置を 0 のままにする（まだ録音が始まっていない）。</p>
 */
export class RecordingTimeline {
  /** いまの位置（16kHz の累計サンプル数）。 */
  private played = 0
  /** 走っているか（停止のあいだの報告で位置を進めない）。 */
  private active = false
  /**
   * 走っている回の**基準**（採った位置）。`resume` のたびに「そのときの採った位置」へ置き直す。
   *
   * <p>停止のあいだも `AudioContext` の時計は進むので、走っている回の中では
   * 「いまの報告 − 回の基準」だけを積む（空白を時間軸へ入れない）。</p>
   */
  private anchor: number | null = null
  private captured = 0
  private sent = 0
  private confirmed = 0
  /** 音源ごとの**次に使うフレーム番号**（張り直しでも 1 へ戻さない）。 */
  private readonly nextFrame: Record<TimelineSourceKey, number> = {}
  /**
   * 音源ごとの**採った位置**（16kHz のサンプル数）。
   *
   * <p>時間軸（{@link positionSamples}）は 1 つだが、**音源ごとの送信は自分の採った位置**から
   * 始める（片方が先に進んでいても、遅れて繋がった音源の先頭を先へ飛ばさない）。</p>
   */
  private readonly sourceCaptured: Record<TimelineSourceKey, number> = {}

  constructor(private readonly sampleRate = TIMELINE_SAMPLE_RATE) {}

  /** 録音を始める（いまの位置を基準にして走り出す）。 */
  begin(): void {
    this.active = true
    // 基準はいまの位置。**録音を始める前の時計は時間軸へ入れない**（用意のあいだの時計を数えない）
    this.anchor = this.played
  }

  /** 音源ごとの採った位置を、いまの時間軸の位置にそろえる（録音を始めるとき）。 */
  alignSourceCaptured(sources: TimelineSourceKey[]): void {
    for (const source of sources) {
      this.sourceCaptured[source] = Math.max(this.sourceCaptured[source] ?? 0, this.captured)
    }
  }

  /** 停止する（以降の報告では位置を進めない）。 */
  pause(): void {
    this.active = false
    this.anchor = null
  }

  /**
   * 再開する（**いまの位置から続く**。0 へ戻さない）。
   *
   * <p>基準を置き直すのは「止めていたあいだに進んだ時計を入れない」ため
   * （停止のあいだも `AudioContext` の時計は進む）。再開後の最初の報告は基準にするだけにする。</p>
   */
  resume(anchorInputSamples?: number, inputRate = TIMELINE_SAMPLE_RATE): void {
    this.active = true
    /*
     * 基準は「**再開した時点の音源の時計**」。止めていたあいだも `AudioContext` の時計は
     * 進んでいるので、いまの位置を基準にすると**止めていた時間が録音へ入る**
     * （録れていない時間なので、回放の位置と書き起こしの時刻が食い違う）。
     *
     * <p>「止めていたあいだに進んだ時計」を入れないため、呼ぶ側は**再開した時点の時計**を
     * 渡す（音源を取り直して時計が 0 から始まるときは渡さない＝次の報告が基準になる）。</p>
     */
    this.anchor = anchorInputSamples === undefined
      ? null : timelineSamplesOf(safeSamples(anchorInputSamples), inputRate)
  }

  /**
   * 保存しておいた位置へ戻す（画面の開き直し・後端の再起動のあと）。
   *
   * <p>**前へだけ**（すでに採った・確認できた位置より後ろへは戻さない）。後端が
   * 「保存済みの分塊の位置」を返し、画面が「保存していた位置」を持っているときは、
   * 先のほうを採る（短いほうを採ると、同じ位置の音をもう一度送って捨てられる）。</p>
   */
  restore(playedSamples: number): void {
    const safe = safeSamples(playedSamples)
    // **保存済みの位置は「後端が持っている音の位置」**なので、採った位置としても扱う
    // （短いほうを採ると、同じ位置の音をもう一度送って「古い」として捨てられる）
    this.captured = Math.max(this.captured, safe)
    this.played = this.captured
  }

  /**
   * **採った**音の位置を知らせる（{@link TIMELINE_SAMPLE_RATE} の累計サンプル数）。
   *
   * <p>呼ぶ側が「`AudioContext` の時計から 16kHz の位置へ直した値」を渡す
   * （{@link timelineSamplesOf}。音源ごとにレートが違っても同じ時間軸になる）。</p>
   *
   * <p>走っていないとき（停止のあいだ）は位置を進めない: 停止のあいだも時計は進むので、
   * そのまま積むと**録音に入っていない時間**が時間軸へ入る。
   * 走り出した最初の報告は**基準**にするだけ（その報告ぶんはまだ積まない）。</p>
   */
  observeCapture(inputSamples: number, inputRate = TIMELINE_SAMPLE_RATE,
    source?: TimelineSourceKey): void {
    if (!this.active) return
    const position = timelineSamplesOf(safeSamples(inputSamples), inputRate)
    /*
     * 基準（{@link begin} / {@link resume}）からの進みだけを積む。
     *
     * **基準が無いときは基準にするだけ**（録音の用意が済む前・音源を入れ替えた直後）:
     * ここで積むと、始める前の時計が録音の先頭へ入る。
     */
    if (this.anchor === null) {
      this.anchor = position
      return
    }
    const advanced = position - this.anchor
    if (advanced <= 0) return
    this.anchor = position
    this.advance(advanced)
    if (source !== undefined) {
      this.sourceCaptured[source] = Math.max(
        this.sourceCaptured[source] ?? 0, this.captured)
    }
  }

  /**
   * **その音源**が採った音の位置（16kHz の累計サンプル数）。その音源の送信はここから始める。
   *
   * <p>分からない（まだ 1 つも報告が来ていない）ときは全体の位置を使う
   * （時間軸そのものがその音源から始まっている）。</p>
   */
  sourceCapturedSamples(source: TimelineSourceKey): number {
    return this.sourceCaptured[source] ?? this.captured
  }

  /**
   * **採った音**の位置（16kHz の累計サンプル数）。
   *
   * <p>フレームの `startSample` と分塊の経過秒はこれを渡す（送っただけ・確認できただけの
   * 位置を「録れている音の位置」にしない＝まだ送れていない音を先の時刻で送らない）。</p>
   */
  capturedSamples(): number {
    return this.captured
  }

  /** いまの時間軸の位置（16kHz。採った・送った・確認できたいちばん先）。 */
  positionSamples(): number {
    return Math.max(this.captured, this.sent, this.confirmed)
  }

  /** 送った位置を知らせる（フレーム番号も一緒に覚える＝張り直しで 1 へ戻さない）。 */
  observeSent(absoluteSamples: number, source?: TimelineSourceKey, frameNo?: number): void {
    this.sent = Math.max(this.sent, safeSamples(absoluteSamples))
    if (source !== undefined && frameNo !== undefined && frameNo > 0) {
      this.nextFrame[source] = Math.max(this.nextFrame[source] ?? 1, frameNo + 1)
    }
  }

  /** 確認できた位置を知らせる（サーバーの受け取り確認）。 */
  observeConfirmed(absoluteSamples: number): void {
    this.confirmed = Math.max(this.confirmed, safeSamples(absoluteSamples))
  }

  /** いまの位置（秒）。画面に出す値・後端のミリ秒と同じ基準。 */
  positionSeconds(): number {
    return Math.round((this.positionSamples() / this.sampleRate) * 1000) / 1000
  }

  /** 3 つの位置（採った・送った・確認できた）。 */
  positions(): TimelinePositions {
    return {
      capturedSamples: this.captured,
      sentSamples: this.sent,
      confirmedSamples: this.confirmed
    }
  }

  /** 送った累計（16kHz のサンプル数）。 */
  sentSamples(): number {
    return this.sent
  }

  /** 確認できた累計（16kHz のサンプル数）。 */
  confirmedSamples(): number {
    return this.confirmed
  }

  /** 音源の**次に使うフレーム番号**（未使用なら 1）。 */
  nextFrameNo(source: TimelineSourceKey): number {
    return this.nextFrame[source] ?? 1
  }

  /** 保存用の形（そのまま `localStorage` へ置ける）。 */
  snapshot(): TimelineSnapshot {
    return { playedSamples: this.played, nextFrameNo: { ...this.nextFrame } }
  }

  /** 保存しておいた形を戻す（{@link snapshot} の対）。 */
  applySnapshot(snapshot: TimelineSnapshot): void {
    this.restore(snapshot.playedSamples)
    for (const [source, frameNo] of Object.entries(snapshot.nextFrameNo)) {
      if (Number.isFinite(frameNo) && frameNo > 0) {
        this.nextFrame[source] = Math.max(this.nextFrame[source] ?? 1, Math.floor(frameNo))
      }
    }
  }

  /**
   * 採った音を進める（**必ず前へだけ**）。
   *
   * <p>`played` は「採った音の累計」だけを保つ。送った・確認できた位置は別に持つ
   * （混ぜると、送っただけの音を「録れている音」として先の時刻で送ってしまう）。</p>
   */
  private advance(samples: number): void {
    if (samples <= 0) return
    this.captured += samples
    this.played = this.captured
  }
}

/** 保存する形（記録ごとに 1 つ）。 */
export interface TimelineSnapshot {
  /** 録音回放の時間軸での位置（16kHz のサンプル数）。 */
  playedSamples: number
  /** 音源ごとの次に使うフレーム番号（張り直し・開き直しで 1 へ戻さない）。 */
  nextFrameNo: Record<TimelineSourceKey, number>
}

/** 保存の名前（記録 ID ごとに 1 つ）。 */
export const TIMELINE_STORAGE_PREFIX = 'study21.classroom.timeline.'

/** `localStorage` の最小の口（テストで差し替えられるようにする）。 */
type TimelineStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

/**
 * 時間軸の位置を**画面を離れても残る**ように保存する。
 *
 * <p>**後端の分塊の状態（`GET /chunks`）だけに頼らない**: 分塊は「送れた音声」の位置しか
 * 持たないので、送れずに控えている音の位置・フレーム番号は画面が残す。両方あるときは
 * **先のほうを採る**（短いほうを採ると、同じ位置の音をもう一度送って捨てられる）。</p>
 */
export class TimelineStore {
  private readonly storage: TimelineStorage | null

  constructor(private readonly recordId: number, storage?: TimelineStorage | null) {
    this.storage = storage !== undefined ? storage : defaultStorage()
  }

  /** 保存する（保存できない環境でも録音は続ける）。 */
  save(snapshot: TimelineSnapshot): void {
    if (this.storage === null) return
    try {
      this.storage.setItem(this.key(), JSON.stringify({
        playedSamples: safeSamples(snapshot.playedSamples),
        nextFrameNo: snapshot.nextFrameNo ?? {}
      }))
    } catch {
      // 保存できない環境（容量・無効化）でも録音と送信は続ける
    }
  }

  /** 読み戻す（無い・壊れている・知らない形のときは null）。 */
  load(): TimelineSnapshot | null {
    if (this.storage === null) return null
    try {
      const raw = this.storage.getItem(this.key())
      if (raw === null) return null
      const parsed = JSON.parse(raw) as unknown
      if (parsed === null || typeof parsed !== 'object' || Array.isArray(parsed)) return null
      const played = (parsed as { playedSamples?: unknown }).playedSamples
      if (typeof played !== 'number' || !Number.isFinite(played) || played < 0) return null
      const frames = (parsed as { nextFrameNo?: unknown }).nextFrameNo
      const nextFrameNo: Record<string, number> = {}
      if (frames !== null && typeof frames === 'object' && !Array.isArray(frames)) {
        for (const [source, value] of Object.entries(frames as Record<string, unknown>)) {
          if (typeof value === 'number' && Number.isFinite(value) && value > 0) {
            nextFrameNo[source] = Math.floor(value)
          }
        }
      }
      return { playedSamples: Math.floor(played), nextFrameNo }
    } catch {
      // 壊れた保存内容は捨てる（読み続けても直らない）
      return null
    }
  }

  /** 記録ごと消したときに捨てる（録音の実体が無いのに位置だけ残さない）。 */
  clear(): void {
    if (this.storage === null) return
    try {
      this.storage.removeItem(this.key())
    } catch {
      // 消せない環境でも録音・表示は続けられる
    }
  }

  private key(): string {
    return `${TIMELINE_STORAGE_PREFIX}${this.recordId}`
  }
}

/** `localStorage` があれば使う（無い環境＝SSR・無効化では null）。 */
function defaultStorage(): TimelineStorage | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

/** 負・NaN を 0 に丸める（壊れた値で時間軸を戻さない）。 */
function safeSamples(value: number): number {
  if (!Number.isFinite(value) || value <= 0) return 0
  return Math.floor(value)
}

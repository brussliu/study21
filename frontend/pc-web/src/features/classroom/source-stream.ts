/**
 * 授業録音 — **音源 1 つぶんの書き起こし送信**（マイク用と共有の音用で 1 つずつ作る）。
 *
 * <p>設計の要点（利用者の指示 7 節の 3・4・5 に対応）:</p>
 * <ul>
 *   <li><b>音源ごとに独立</b>: 片方が切れても、もう片方は送り続ける（セッションも別）。</li>
 *   <li><b>送る量は小分け</b>: 1 回の送信は最大 `maxSendSeconds`（既定 2 秒）。遅れても
 *       「いまの音」を送る（書き起こしが遅れ続けない）。</li>
 *   <li><b>失敗したら保持して再送</b>: 送れなかった音は捨てず、次の周期で同じ音を送り直す
 *       （HTTP の失敗＝未送信であって、認識結果の失敗とは別）。</li>
 *   <li><b>黙って捨てない</b>: 貯められる量を超えたら古い分を捨てるが、**欠落した区間（秒）を
 *       記録して画面へ伝える**（録音そのものには全部入っているので、後から補書き起こしできる）。</li>
 *   <li><b>停止は順番どおり</b>: 送信ループを止める → 残りを送り切る → `/finish` を呼んで
 *       最後の確定文を受け取る（尾句を落とさない）。</li>
 * </ul>
 *
 * <p>AudioWorklet からのサンプル供給は画面（Vue）が行い、ここは**送信の状態機械だけ**を持つ
 * （テストしやすいようにブラウザの音声 API に触らない）。</p>
 */
import {
  finishClassroomSttStream,
  pushClassroomSttStream,
  type ClassroomSegment,
  type ClassroomSource,
  type ClassroomSttStreamPush
} from '@/api/classroom'
import { ApiError } from '@study21/web-shared'
import {
  normalizeSttFinalize,
  type SourceFinalizeResult,
  type SttFinalizePayload
} from '@/features/classroom/stt-finalize'
import {
  PCM_CHUNK_MIME, PcmChunkBuffer, TIMELINE_SAMPLE_RATE, timelineSamplesOf, timelineSeconds
} from '@/features/classroom/pcm'

/**
 * 送れずに抜けた音声の区間（**補書き起こしの手がかり**）。
 *
 * <p>画面に出す秒だけでなく、**音源と録音の時間軸の絶対位置**（16kHz のサンプル番号）を残す。
 * 録音そのものには全部入っているので、あとから「この音源のこの区間」を録音から聞き直して
 * 書き起こせる（秒だけでは、どの音源のどこかが分からず追いかけられない）。</p>
 */
export interface MissingRange {
  /** どの音源で抜けたか（`mic` / `shared`）。 */
  source: ClassroomSource
  fromSeconds: number
  toSeconds: number
  /** 録音の時間軸（{@link TIMELINE_SAMPLE_RATE}）での区間の先頭。 */
  fromSample: number
  /** 録音の時間軸での区間の終わり（この位置は含まない）。 */
  toSample: number
  /** 抜けたと分かったフレームの番号（送り直しの突き合わせ用）。 */
  frameNo: number
  /** 記録した時刻（ミリ秒）。保存期間を過ぎたものを捨てるのに使う。 */
  recordedAt: number
}

/**
 * 補書き起こしの手がかり（{@link MissingRange}）を、画面を離れても残るように保存する。
 *
 * <p>録音そのものには全部入っているので、欠けた区間は**あとから録音を聞き直して**
 * 書き起こせる。そのためには「どの音源の、録音のどの位置からどこまで」かが要る
 * （秒だけ・画面の案内だけでは、あとから追いかけられない）。</p>
 *
 * <p><b>捨てる時機</b>（無限に増やさない・要らなくなったら消す）:</p>
 * <ul>
 *   <li>**保存期間**（既定 {@link DEFAULT_RECOVERY_RETENTION_DAYS} 日。録音の保存期間と同じ考え方）を
 *       過ぎた記録は捨てる。録音の実体も消えているので、追い書きのしようが無い。</li>
 *   <li>**件数の上限**（既定 {@link DEFAULT_MAX_RECOVERY_RANGES} 件／記録・音源）を超えたら
 *       古い順に捨てる（送信が長く詰まっても際限なく増やさない）。</li>
 *   <li>同じ区間は二重に覚えない（案内と記録が二重に増えない）。</li>
 *   <li>記録ごと消したときは {@link MissingRangeStore.clear} で捨てる（呼ぶのは画面）。</li>
 * </ul>
 */
export class MissingRangeStore {
  private readonly storage: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> | null

  constructor(private readonly options: MissingRangeStoreOptions) {
    this.storage = options.storage !== undefined ? options.storage : defaultStorage()
  }

  /** 欠落した区間を覚える（同じ区間は二重に覚えない）。 */
  add(range: MissingRange): void {
    const ranges = this.list()
    if (ranges.some((item) => item.fromSample === range.fromSample && item.toSample === range.toSample)) {
      return
    }
    ranges.push(range)
    this.save(ranges)
  }

  /** 覚えている区間（保存期間を過ぎたもの・上限を超えた古いものはここで捨てる）。 */
  list(): MissingRange[] {
    const all = this.load()
    const now = this.now()
    const kept = all.filter((item) => now - item.recordedAt
      <= (this.options.retentionDays ?? DEFAULT_RECOVERY_RETENTION_DAYS) * 24 * 60 * 60 * 1000)
    const capped = kept.length > this.maxRanges() ? kept.slice(kept.length - this.maxRanges()) : kept
    if (capped.length !== all.length) this.save(capped)
    return capped
  }

  /** 全部捨てる（記録ごと消したとき）。 */
  clear(): void {
    this.save([])
  }

  private maxRanges(): number {
    return Math.max(1, this.options.maxRanges ?? DEFAULT_MAX_RECOVERY_RANGES)
  }

  private now(): number {
    return this.options.now !== undefined ? this.options.now() : Date.now()
  }

  private load(): MissingRange[] {
    const raw = this.read()
    if (raw === null) return []
    try {
      const parsed = JSON.parse(raw) as unknown
      if (!Array.isArray(parsed)) return []
      return parsed.filter((item): item is MissingRange => item !== null && typeof item === 'object'
        && typeof (item as MissingRange).fromSample === 'number'
        && typeof (item as MissingRange).toSample === 'number')
    } catch {
      // 壊れた保存内容は捨てる（読み続けても直らない）
      return []
    }
  }

  private save(ranges: MissingRange[]): void {
    if (this.storage === null) return
    try {
      this.storage.setItem(this.options.storageKey, JSON.stringify(ranges))
    } catch {
      // 保存できない環境（容量・無効化）でも録音と送信は続ける
    }
  }

  private read(): string | null {
    if (this.storage === null) return null
    try {
      return this.storage.getItem(this.options.storageKey)
    } catch {
      return null
    }
  }
}

/** {@link MissingRangeStore} の設定。 */
export interface MissingRangeStoreOptions {
  /** 保存の名前（記録 ID と音源ごとに 1 つ）。 */
  storageKey: string
  /** 残す期間（日）。既定 30 日。 */
  retentionDays?: number
  /** 覚えておく件数の上限。既定 200 件。 */
  maxRanges?: number
  /** いまの時刻（テスト用）。 */
  now?: () => number
  /** 保存先（既定は `localStorage`。無い環境では何も残さない）。 */
  storage?: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> | null
}

/** `localStorage` があれば使う（無い環境＝SSR・無効化では null）。 */
function defaultStorage(): Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> | null {
  try {
    return typeof localStorage === 'undefined' ? null : localStorage
  } catch {
    return null
  }
}

/**
 * 送信の単位（音声フレーム）。
 *
 * <p>`no`（番号）と `startSample`（**録音の先頭からの絶対位置**。16kHz のサンプル数）は
 * 常時接続と HTTP で**同じもの**を使う。番号も位置も「この音源で取り出した累計」から決まり、
 * 張り直し・退避・後端の再起動で 0 に戻らない（戻すと文の時刻が 0 へ飛ぶ）。</p>
 */
interface PendingFrame {
  /** フレーム番号（この音源で 1 から。取り出した順に増える） */
  no: number
  /** 録音の時間軸（{@link TIMELINE_SAMPLE_RATE}）での、このフレームの先頭の位置 */
  startSample: number
  blob: Blob
  /** 入力レートでのサンプル数（このフレームが持つ音の長さ） */
  samples: number
}

export interface SourceStreamOptions {
  source: ClassroomSource
  /** 授業記録 ID（まだ無ければ null＝送らない）。 */
  recordId: () => number | null
  /** 確定して保存された文（画面は書き起こしへ足す）。 */
  onSegments: (added: ClassroomSegment[]) => void
  /** まだ確定していない文（画面は 1 行だけ出す。保存はしない）。 */
  onInterim: (text: string) => void
  /** 画面に出す案内（失敗・欠落・復帰）。 */
  onNotice: (message: string) => void
  /**
   * 送れなかった区間（音源と、録音の時間軸での絶対位置つき）。
   * 後から録音を聞き直して補書き起こしするために残す。
   */
  onMissingRange?: (range: MissingRange) => void
  /**
   * 送った位置を知らせる（録音回放の時間軸。16kHz のサンプル数）。
   *
   * <p>画面は「送った位置」を別に持ち、**送り直し・張り直しで時間が戻らない**ようにする
   * （位置は前へだけ進める）。</p>
   */
  onSent?: (absoluteSamples: number, source: ClassroomSource, frameNo: number) => void
  /** 送信の周期（ミリ秒）。既定 250ms（音は 100ms ずつ送る前提で十分間に合う）。 */
  intervalMs?: number
  /** 1 回に送る最大の長さ（秒）。既定 2 秒。 */
  maxSendSeconds?: number
  /** 貯めておける最大の長さ（秒）。超えたら古い分を捨てて欠落として記録する。既定 60 秒。 */
  maxBufferSeconds?: number
  /** `/finish` を待つ上限（ミリ秒）。既定 20 秒。 */
  finishTimeoutMs?: number
  /**
   * 常時接続（WebSocket）を使うか。既定 true。
   *
   * <p>使えない環境（`WebSocket` が無い・接続できない・古いサーバー）では
   * **自動で HTTP の送信へ退避**する（録音も書き起こしも止めない）。</p>
   */
  useSocket?: boolean
  /** 常時接続で 1 回に送る長さ（秒）。既定 0.1（100ms）。 */
  socketFrameSeconds?: number
  /** 断線して張り直す回数の上限。既定 5（これを超えたら HTTP へ退避する）。 */
  maxReconnects?: number
  /** 接続が開くのを待つ上限（ミリ秒）。既定 1500（開かなければ HTTP へ退避する）。 */
  socketConnectTimeoutMs?: number
  /** テスト用: WebSocket の実装を差し替える。 */
  socketFactory?: (url: string) => WebSocket
  /** 補書き起こしの手がかりを残す期間（日）。既定 30（録音の保存期間と同じ考え方）。 */
  recoveryRetentionDays?: number
  /** テスト用: 補書き起こしの手がかりの保存先（既定は `localStorage`）。 */
  recoveryStorage?: Pick<Storage, 'getItem' | 'setItem' | 'removeItem'> | null
  /**
   * この音源の**送信を始める位置**（録音回放の時間軸。16kHz のサンプル数）。
   *
   * <p>停止 → 続きの録音・画面の開き直しでは**保存済みの位置**を渡す。0 から送ると後端が
   * 「すでに処理した位置より古い」として**その音を丸ごと捨てる**（書き起こしが消える）。
   * 画面が持つ時間軸（`RecordingTimeline`）が決める値なので、ここでは積むだけにする。</p>
   */
  startSample?: number
  /** この音源の**次に使うフレーム番号**（張り直し・開き直しでも 1 へ戻さない）。 */
  startFrameNo?: number
}

/** 画面に出す状態（音源ごと）。 */
export interface SourceStreamState {
  /** 送信ループが動いているか。 */
  running: boolean
  /** 確認できていない音（秒。まだ送っていない分と、送ったが確認が取れていない分）。 */
  pendingSeconds: number
  /** 送れずに残っている音があるか（再送中）。 */
  retrying: boolean
  /** 抜けた区間（このインスタンスが記録した分。保存されている分は `recoveryRanges()`）。 */
  missingRanges: MissingRange[]
  /** 送ったフレーム数（テスト・表示用）。 */
  sentFrames: number
  /**
   * 収尾の失敗の理由（**成功なら null**。{@link SourceStream.finishFailure} と同じ値）。
   *
   * <p>状態の 1 つとして持たせるのは、画面が `state()` だけを見て「終わった」と言ってよいかを
   * 決められるようにするため（成功の印を「投げなかったこと」に頼らない）。</p>
   */
  finishFailure: string | null
  /**
   * 収尾の結果そのもの（**まだ収尾していなければ null**）。
   *
   * <p>`finishFailure` は「やり直せば直る失敗」しか表せない。やり直しても直らない
   * **不完整な終わり**（`error=null` / `finalizeCompleted=false`）はここを見る。</p>
   */
  finalize: SourceFinalizeResult | null
}

const DEFAULT_INTERVAL_MS = 250
const DEFAULT_MAX_SEND_SECONDS = 2
const DEFAULT_MAX_BUFFER_SECONDS = 60
const DEFAULT_FINISH_TIMEOUT_MS = 20_000
/** 常時接続で 1 回に送る長さ（秒）。阿里雲の推奨に合わせて 100ms。 */
const DEFAULT_SOCKET_FRAME_SECONDS = 0.1
/** 1 周期で送るフレーム数の上限（長く止まったあとに一気に送りすぎない）。 */
const MAX_FRAMES_PER_TICK = 50
/** 断線して張り直す回数の上限。 */
const DEFAULT_MAX_RECONNECTS = 5
/** 接続が開くのを待つ上限（ミリ秒）。開かなければ HTTP へ退避する。 */
const DEFAULT_SOCKET_CONNECT_TIMEOUT_MS = 1_500
/**
 * HTTP 送信で 1 回に送る最小の長さ（秒）。
 *
 * <p>周期が短い設定でも、細かすぎる送信をしない（1 リクエストの往復のほうが大きい）。</p>
 */
const HARVEST_MIN_SECONDS = 0.1

/** 補書き起こしの手がかりを残しておく期間（日）。録音の保存期間と同じ考え方。 */
const DEFAULT_RECOVERY_RETENTION_DAYS = 30
/** 1 記録・1 音源で覚えておく欠落区間の上限（送信が長く詰まっても際限なく増やさない）。 */
const DEFAULT_MAX_RECOVERY_RANGES = 200

/**
 * 文の時刻を録音の時間軸へ写すかどうかの許容（秒）。
 *
 * <p>セッションの先頭ちょうどの文は正しい値でも「先頭 ＝ 0」になり得るので、丸めと
 * 1 フレームぶんの揺れを吸収する幅を置く（この幅より前に出た文だけを「数え直し」と見る）。</p>
 */
const TIMELINE_TOLERANCE_SECONDS = 0.05

export class SourceStream {
  private readonly options: Required<Pick<SourceStreamOptions, 'intervalMs' | 'maxSendSeconds'
    | 'maxBufferSeconds' | 'finishTimeoutMs' | 'useSocket' | 'socketFrameSeconds'
    | 'maxReconnects' | 'socketConnectTimeoutMs'>> & SourceStreamOptions
  private readonly buffer = new PcmChunkBuffer()
  /**
   * 取り出したが**まだ確認できていない**フレーム。番号の順に保つ。
   *
   * <p>常時接続でも HTTP でも同じ列を使う: 常時接続が切れて HTTP へ退避したとき、
   * ここに残っている音を**古い順にそのまま**送り直せる（欠けも重複も作らない）。</p>
   */
  private outbox: PendingFrame[] = []
  /** 取り出し済みのサンプル数（入力レート。**この回に取り出した累計**）。 */
  private consumedSamples = 0
  private sampleRate = 0
  private timer: number | null = null
  private sending = false
  private running = false
  /** 常時接続で終了の通知を受け取ったか。 */
  private finished = false
  private retrying = false
  private sentFrames = 0
  private readonly missing: MissingRange[] = []
  /** 常時接続（WebSocket）。使えないときは null のまま HTTP で送る。 */
  private socket: WebSocket | null = null
  private socketReady = false
  private socketFailed = false
  private reconnects = 0
  private nextFrameNo = 1
  /**
   * この回に採った音の**時間軸での先頭**（{@link SourceStreamOptions.startSample}）。
   *
   * <p>停止 → 続きの録音では 0 ではない（0 にすると後端に古い音として捨てられる）。</p>
   */
  private sampleOrigin = 0
  /**
   * **届いている音の先頭**が時間軸のどこか（16kHz のサンプル数）。
   *
   * <p>取り出すたびにその長さぶん進み、貯めすぎで捨てたときは捨てた長さぶん進む
   * （捨てた区間は位置に残る＝詰めない）。</p>
   */
  private arrivalStart = 0
  /**
   * **届いている音の終わり**が時間軸のどこか（16kHz のサンプル数）。
   *
   * <p>取り出しや欠落では動かない（音が届いたときにだけ進む）。</p>
   */
  private arrivedEnd = 0
  /**
   * この音源の**時計の起点**（最初の報告の値。null なら未確定）。
   *
   * <p>この音源の位置は「起点の位置 ＋ 時計の進みぶん」で出す。同じ `AudioContext` の時計なので、
   * マイクと共有で**同じ瞬間が同じ位置**になる。</p>
   */
  private clockOrigin: number | null = null
  /** 時計の起点での時間軸の位置（{@link sampleOrigin} の写し）。 */
  private clockBase = 0

  /** これまででいちばん新しい報告の時計（時計が飛んだぶんだけ位置を進めるのに使う）。 */
  private lastClock = 0
  /**
   * いま送っている位置（時間軸。16kHz のサンプル数）。送ったら画面へ知らせる
   * （{@link SourceStreamOptions.onSent}）。
   */
  private sentSamples = 0
  /** いままでに送った音の**終わりの位置**（次に送るフレームはここから続く）。 */
  private lastSentEnd = 0
  /** 直前に送った時刻（ミリ秒。**待った長さぶんだけ**送るのに使う。0 はまだ送っていない）。 */
  private lastSendAt = 0
  /** 送信ループが回った回数（周期ぶんのフレーム数を計算するのに使う）。 */
  private ticks = 0
  /**
   * 常時接続へ渡した**新しい**フレーム数（実時間ぶん送るペースの計算に使う）。
   * 送り直しは数えない（張り直しの直後に、送り直しのぶんで新しい音を止めないため）。
   */
  private framesSent = 0
  /** サーバーが処理し終えた番号（受け取り確認）。 */
  private processed = 0
  /**
   * 常時接続への書き込みの直列待ち行列。
   *
   * <p>`Blob` → `ArrayBuffer` の変換は非同期なので、まとめて投げると**番号の順に届かない**。
   * 順序が崩れると後端の「すでに処理した番号は捨てる」が効かず、同じ音が 2 度認識される。</p>
   */
  private socketQueue: Promise<void> = Promise.resolve()
  /** 張り直しを待っているあいだ（この間は送らない。音はバッファに貯めて、戻ったら送り直す）。 */
  private waitingReconnect = false
  /**
   * 収尾（{@link finish}）の結果（**成功なら null**）。
   *
   * <p>画面はこれを見て「終わった」と言ってよいかを決める。HTTP 200 でも `data.error` なら
   * 成功ではない（後端は尾部を取り切れなかった・保存できなかったときに理由を返す）。
   * 失敗したまま「終わった」と言うと、尾部の文が最終まとめに入らない。</p>
   */
  private finishError: string | null = null
  /**
   * **この音源の収尾の結果**（HTTP と常時接続で同じ形に揃えたもの）。
   *
   * <p>`error` だけでは足りない: 後端は「やり直しても直らない不完整な終わり」を
   * `error=null` で返す（画面が永久に再試行しないため）。ここに**結果そのもの**を持ち、
   * 画面は「終わってよいか」と「識別が完全か」を別々に読む。</p>
   */
  private finalizeResult: SourceFinalizeResult | null = null
  /**
   * 走っている収尾の約束（画面のやり直しで**二重に `/finish` を送らない**）。
   *
   * <p>画面は待ち時間に上限を置く（待ち切れなければ失敗として出し、あとから同じ音源だけを
   * やり直す）。そのとき前の収尾がまだ走っていることがあるので、やり直しは**同じ約束**を
   * 待つ（同じ音を 2 回締めない）。失敗したら捨てて、もう一度やり直せるようにする。</p>
   */
  private finishPromise: Promise<void> | null = null
  /**
   * いまの送信のまとまり（常時接続 1 本、または HTTP へ退避したあとの分）の**先頭フレーム**の
   * 録音の時間軸での位置。
   *
   * <p>認識の文の時刻は「その認識セッションの音声の先頭からのミリ秒」で返るので、授業全体の
   * 時間軸へ写すには**セッションの先頭が録音のどこか**が要る。後端は送ったサンプル数から
   * 自分で写すが、後端を再起動したときは基準が 0 に戻る（`resume` を送れない HTTP へ退避した
   * 場合は特に）。そのときは画面がここから写し直す。</p>
   */
  private sessionOriginSample: number | null = null
  /** 補書き起こしの手がかりの保存先（記録 ID が決まってから作る）。 */
  private recoveryStore: MissingRangeStore | null = null

  constructor(options: SourceStreamOptions) {
    this.options = {
      intervalMs: options.intervalMs ?? DEFAULT_INTERVAL_MS,
      maxSendSeconds: options.maxSendSeconds ?? DEFAULT_MAX_SEND_SECONDS,
      maxBufferSeconds: options.maxBufferSeconds ?? DEFAULT_MAX_BUFFER_SECONDS,
      finishTimeoutMs: options.finishTimeoutMs ?? DEFAULT_FINISH_TIMEOUT_MS,
      useSocket: options.useSocket ?? true,
      socketFrameSeconds: options.socketFrameSeconds ?? DEFAULT_SOCKET_FRAME_SECONDS,
      maxReconnects: options.maxReconnects ?? DEFAULT_MAX_RECONNECTS,
      socketConnectTimeoutMs: options.socketConnectTimeoutMs ?? DEFAULT_SOCKET_CONNECT_TIMEOUT_MS,
      ...options
    }
    this.sampleRate = 48_000
    // 続きの録音・開き直しでは**保存済みの位置と番号**から始める（0 から送ると後端に捨てられる）
    this.sampleOrigin = safeSamples(options.startSample)
    this.arrivalStart = this.sampleOrigin
    this.arrivedEnd = this.sampleOrigin
    this.clockBase = this.sampleOrigin
    this.sentSamples = this.sampleOrigin
    this.lastSentEnd = this.sampleOrigin
    this.nextFrameNo = Math.max(1, Math.floor(options.startFrameNo ?? 1))
  }

  get source(): ClassroomSource {
    return this.options.source
  }

  /**
   * AudioWorklet から届いたサンプルを足す（録音中はこれを呼び続ける）。
   *
   * @param contextSample この回の先頭の**コンテキストの時計**（サンプル数）。
   *   **音源をまたいで同じ値**（同じ `AudioContext` の時計）なので、これを位置の基準にする。
   */
  append(samples: Float32Array, sampleRate: number, contextSample?: number): void {
    if (samples.length === 0) return
    this.sampleRate = sampleRate > 0 ? sampleRate : this.sampleRate
    const length = timelineSamplesOf(samples.length, this.sampleRate)
    /*
     * この音源の位置は「**起点の位置 ＋ 時計の進みぶん**」で出す。
     *
     * <p>起点は最初の報告のときの（位置・時計）。時計は同じ `AudioContext` の値なので、
     * マイクと共有で**同じ瞬間が同じ位置**になり、後から繋がった音源も**その時点の位置**から
     * 始まる（0 から始めると録音の先頭へ入る）。**止めていた時間は入らない**
     * （停止 → 続きの録音では画面が `startSample` を渡し直す）。</p>
     *
     * <p>時計が分からない（古い実装・テスト）ときは、時計を使わず**届いた音の長さ**で進む
     * （従来どおり `startSample` から積む）。</p>
     */
    if (contextSample !== undefined && Number.isFinite(contextSample)) {
      // 時計の起点は**この音源の最初の報告**（そのときの位置が基準になる）
      if (this.clockOrigin === null) {
        this.clockOrigin = Math.max(0, contextSample)
        this.clockBase = this.sampleOrigin
      }
      /** 同じコンテキストの時計から出した**この音の終わりの位置**（音源をまたいで比べられる）。 */
      const fromClock = this.clockBase + timelineSamplesOf(
        Math.max(0, contextSample - this.clockOrigin), this.sampleRate)
      /*
       * **届いている音の終わり**を決める。
       *
       * <p>時計のほうが先なら、その差は**録れていない区間**（マイクを抜いた・端末が止まった・
       * 共有が切れた）。記録して画面へ知らせ、届いている音の先頭も**その長さぶん先へずらす**
       * （詰めて隠さない）。時計と音が同じ速さなら差は無い。</p>
       */
      const chainedEnd = this.arrivedEnd + length
      const skipped = Math.max(0, fromClock - chainedEnd)
      if (skipped > 0) {
        const range = this.missingRange(chainedEnd, chainedEnd + skipped, this.nextFrameNo)
        this.remember(range)
        this.options.onNotice(`音源の時計が ${round3(range.toSeconds - range.fromSeconds)} 秒進みました`
          + `（${range.fromSeconds}〜${range.toSeconds} 秒の音は録れていません）。`)
        this.arrivalStart += skipped
      }
      this.arrivedEnd = Math.max(chainedEnd, fromClock)
      this.lastClock = Math.max(this.lastClock, contextSample)
    } else {
      // 時計が分からないときは、届いた音をつないで進む（従来どおり）
      this.arrivedEnd += length
    }
    this.sampleOrigin = this.arrivedEnd
    this.buffer.append(samples)
    this.dropIfTooMuch()
  }

  /**
   * 貯めすぎたら**古い分を捨てて欠落として記録する**（黙って消さない）。
   *
   * <p>送信が追いつかないと（サーバーの往復が遅い）、貯まる一方になって書き起こしが遅れ、
   * 1 回に送る量も膨らむ。**古い音を捨てて今の音を送る**ほうが授業の書き起こしとしては
   * 役に立つ（音声そのものは録音側に残っている）。捨てた区間は**時間軸の位置つき**で記録して、
   * あとから録音を聞き直して補書き起こしできるようにする。</p>
   */
  private dropIfTooMuch(): void {
    const rate = this.rate()
    if (rate <= 0) return
    const keep = Math.round(rate * this.options.maxBufferSeconds)
    const dropped = this.buffer.trim(keep)
    if (dropped <= 0) return
    /*
     * 捨てたのは**まだ取り出していない**音なので、先頭が捨てたぶんだけ先へ進む（詰めない）。
     * 捨てた長さは {@link droppedTimelineSamples} に積む（先頭が先へずれる）。
     */
    const droppedTimeline = timelineSamplesOf(dropped, rate)
    const fromSample = this.arrivalStart
    // 捨てたぶんだけ先頭が先へ進む（位置は詰めない）
    this.arrivalStart += droppedTimeline
    const range = this.missingRange(fromSample, this.arrivalStart, this.nextFrameNo)
    this.remember(range)
    this.options.onNotice(`送信が追いつかず ${range.fromSeconds}〜${range.toSeconds} 秒の音を飛ばしました`
      + '（録音そのものには入っています。あとで補書き起こしできます）。')
  }

  /**
   * **いま取り出すフレーム**が始まる時間軸の位置（16kHz のサンプル数）。
   *
   * <p>位置は**採った音の先頭**（{@link arrivalStart}）。送り出しが遅れて「まだ送っていない音」が
   * 貯まっていても、**送ったところの続き**より前へは戻さない（同じ音を別の時刻で二度送ると、
   * 文の時刻が重なったり順序が入れ替わったりする）。</p>
   */
  private frameStart(): number {
    return Math.max(this.arrivalStart, this.lastSentEnd)
  }

  /** 送信ループを始める（録音を始めるときに 1 回）。 */
  start(): void {
    if (this.running) return
    this.running = true
    this.openSocketOrFallback()
    this.timer = window.setInterval(() => { this.pump() }, this.options.intervalMs)
  }

  /* ---------------- 常時接続（WebSocket） ---------------- */

  /**
   * HTTP へ退避する（常時接続が使えない・開かない・張り直せない）。
   *
   * <p>**セッションの先頭**も取り直す（送信のまとまりが変わるので、最初に送るフレームが
   * 後端にとっての新しい認識セッションの先頭になる）。</p>
   */
  private fallbackToHttp(): void {
    this.socketFailed = true
    this.sessionOriginSample = null
  }

  /** 常時接続を開く。使えない環境・開けなかったときは HTTP 送信へ退避する。 */
  private openSocketOrFallback(): void {
    if (this.options.useSocket === false || this.socketFailed || typeof WebSocket === 'undefined') {
      this.fallbackToHttp()
      return
    }
    const id = this.options.recordId()
    if (id === null) return
    const scheme = window.location.protocol === 'https:' ? 'wss' : 'ws'
    const url = `${scheme}://${window.location.host}/api/user/classroom/${id}/stt/socket`
      + `?source=${this.options.source}`
    let socket: WebSocket
    try {
      socket = this.options.socketFactory !== undefined
        ? this.options.socketFactory(url) : new WebSocket(url)
    } catch {
      this.fallbackToHttp()
      return
    }
    socket.binaryType = 'arraybuffer'
    this.socket = socket
    socket.onopen = () => {
      this.socketReady = true
      this.waitingReconnect = false
      // 送信のまとまりが変わった＝**セッションの先頭**を取り直す（最初に送るフレームで決まる）
      this.sessionOriginSample = null
      /*
       * **`resume` は送らない**。
       *
       * <p>後端は `resume` の番号から「その音源へ送ったサンプル数」を計算して
       * **時間軸の下端**にする（番号 × 100ms）。こちらは番号も位置も**録音全体で通し**なので、
       * 続きの録音（番号 600 番台から）で送ると下端が**実際の音より遥かに先**へ飛び、
       * 続きの音が全部「すでに処理した位置より古い」として捨てられる（書き起こしが消える）。
       * 位置はフレームの見出し（`startSample`）で毎回送るので、後端は番号から計算しなくてよい。</p>
       */
      this.resendUnacked()
    }
    socket.onmessage = (event: MessageEvent) => this.onSocketMessage(event)
    socket.onerror = () => {
      // 接続できない（古いサーバー・経路の問題）→ HTTP へ退避する
      if (!this.socketReady) this.fallbackToHttp()
    }
    // 接続が開かないまま時間が経ったら**HTTP へ退避**する（固まった接続で止まらない）
    window.setTimeout(() => {
      if (!this.socketReady && this.socket === socket && this.running) {
        this.fallbackToHttp()
        try {
          socket.close()
        } catch {
          // すでに閉じているときは何もしない
        }
        this.socket = null
        this.options.onNotice('書き起こしの接続が開きませんでした。HTTP で送ります（録音は続いています）。')
      }
    }, this.options.socketConnectTimeoutMs)
    socket.onclose = () => {
      this.socketReady = false
      this.socket = null
      if (!this.running) return
      // まだ送っていない音があるなら張り直す（無ければ HTTP で続ける）
      if (this.reconnects < this.options.maxReconnects) {
        this.reconnects += 1
        this.waitingReconnect = true
        this.options.onNotice(`書き起こしの接続が切れました（${this.reconnects}/${this.options.maxReconnects}）。`
          + 'つなぎ直して、確認できていない音を送り直します。')
        window.setTimeout(() => {
          if (!this.running) return
          this.waitingReconnect = false
          this.openSocketOrFallback()
        }, Math.min(4_000, 500 * this.reconnects))
      } else {
        this.waitingReconnect = false
        this.fallbackToHttp()
        this.options.onNotice('書き起こしの接続をつなぎ直せませんでした。'
          + '確認できていない音を全部 HTTP で送り直します（録音は続いています）。')
      }
    }
  }

  /** サーバーからの結果（確定文・途中の文・受け取り確認）。 */
  private onSocketMessage(event: MessageEvent): void {
    if (typeof event.data !== 'string') return
    let payload: {
      type?: string
      interim?: string
      added?: ClassroomSegment[]
      processedFrames?: number
      error?: string | null
    } & SttFinalizePayload
    try {
      payload = JSON.parse(event.data) as typeof payload
    } catch {
      return
    }
    if (payload.type === 'result' || payload.type === 'finished') {
      const added = this.onRecordingTimeline(payload.added ?? [])
      if (added.length > 0) this.options.onSegments(added)
      if (payload.interim !== undefined) this.options.onInterim(payload.interim)
      const failed = payload.error !== undefined && payload.error !== null && payload.error !== ''
      if (failed) {
        this.options.onNotice(`書き起こしの認識でエラーが出ました（${payload.error}）。`
          + '音は送り続けます。')
      }
      /*
       * **受け取り確認はサーバーが「処理し終えた」と言った番号だけ**を進める。
       *
       * エラーのときはサーバーが番号を進めない（保存できなかった文を含むフレームを
       * 「処理済み」と言うと、その音は二度と送られず書き起こしが消える）。画面が勝手に
       * 進めても同じ穴が開くので、`error` があるときは進めない。
       */
      if (!failed) this.acknowledge(payload.processedFrames ?? 0)
    }
    if (payload.type === 'finished') {
      this.finished = true
      /*
       * **収尾の結果を残す**（`finished` は「応答が届いた」だけ。識別が完全かは別）。
       *
       * <p>常時接続も HTTP と同じ欄（`finalizeCompleted` / `retryable` / `finalizeStatus` /
       * `notice`）を返すので、**同じ関数**で読み取る。`error` だけを見ると、やり直しても直らない
       * 終端（`error=null`）を成功と誤読し、欠けた書き起こしを「保存しました」と言ってしまう。</p>
       */
      this.applyFinalizePayload(payload)
    }
  }

  /**
   * 文の時刻を**録音の時間軸**へ写す（認識セッションの中の時刻で返ってきたとき）。
   *
   * <p>後端はふつう「その音源へ送ったサンプル数＋`begin_time`」で録音の先頭からの秒を返す。
   * ただし**後端を再起動したあと**（`resume` を送れない HTTP へ退避したときなど）は基準が 0 に
   * 戻るので、認識セッションの中の秒（0 付近）がそのまま届く。すると文が録音の先頭へ飛ぶ
   * （＝0 に戻る・詰まる）。ここでは「**そのセッションが始まった位置より前**を指す文は
   * セッションの中の時刻だ」と見て、セッションの先頭の絶対位置を足す。</p>
   *
   * <p>判定は物理的な整合で決める（当てずっぽうではない）: セッションの音声はセッションの
   * 先頭より前には無いので、正しい値なら必ず先頭以上になる。丸めのぶんだけ許容
   * （{@link TIMELINE_TOLERANCE_SECONDS}）を置く。</p>
   */
  private onRecordingTimeline(added: ClassroomSegment[]): ClassroomSegment[] {
    const origin = this.sessionOriginSample === null ? 0 : timelineSeconds(this.sessionOriginSample)
    if (origin <= TIMELINE_TOLERANCE_SECONDS) return added
    let shifted = false
    const mapped = added.map((segment) => {
      const start = segment.startOffsetSeconds
      if (start === null || start >= origin - TIMELINE_TOLERANCE_SECONDS) return segment
      shifted = true
      return {
        ...segment,
        startOffsetSeconds: round3(origin + start),
        endOffsetSeconds: segment.endOffsetSeconds === null
          ? null : round3(origin + segment.endOffsetSeconds)
      }
    })
    if (shifted) {
      this.options.onNotice('張り直した認識が時間軸を 0 から数え直していたので、'
        + `録音の時間軸（${origin} 秒）へ戻して出します。`)
    }
    return mapped
  }

  /** 受け取り確認: 確認できた番号までのフレームを未確認の列から捨てる。 */
  private acknowledge(processed: number): void {
    if (processed <= this.processed) return
    this.processed = processed
    this.outbox = this.outbox.filter((frame) => frame.no > processed)
    this.sentFrames = Math.max(this.sentFrames, processed)
    this.retrying = this.outbox.length > 0
    // **実際に進んだ**ときだけ張り直しの回数を数え直す（開いただけで数え直すと上限が効かない）
    this.reconnects = 0
  }

  /** 確認できていないフレームを、番号の順に送り直す（断線からの復帰）。 */
  private resendUnacked(): void {
    if (this.socket === null) return
    for (const frame of [...this.outbox].sort((left, right) => left.no - right.no)) {
      this.queueOverSocket(frame)
    }
  }

  /**
   * 常時接続へ **見出し（番号と絶対位置）→ バイナリ** の順で、番号の順に送る。
   *
   * <p>見出しはテキスト 1 枚（`{"type":"frame",...}`）。後端は今のところ `resume` / `finish`
   * だけを見て、知らない種類は黙って無視するので、**バイナリはバイナリのまま**で済む。</p>
   */
  private queueOverSocket(frame: PendingFrame): void {
    this.socketQueue = this.socketQueue
      .then(() => this.writeOverSocket(frame))
      .catch(() => undefined)
  }

  private async writeOverSocket(frame: PendingFrame): Promise<void> {
    const socket = this.socket
    if (socket === null || !this.socketReady) return
    // このセッションで最初に送るフレーム＝後端にとっての認識セッションの先頭
    if (this.sessionOriginSample === null) this.sessionOriginSample = frame.startSample
    socket.send(JSON.stringify({
      type: 'frame',
      no: frame.no,
      startSample: frame.startSample,
      sampleRate: TIMELINE_SAMPLE_RATE,
      source: this.options.source
    }))
    const buffer = await blobToArrayBuffer(frame.blob)
    // 変換の待ちのあいだに切れていたら送らない（閉じたソケットへ投げない）
    if (this.socket === socket && this.socketReady) socket.send(buffer)
  }

  /** 送信ループの 1 周期（常時接続ならフレームを送り、そうでなければ HTTP で送る）。 */
  private pump(): void {
    if (!this.running) return
    if (this.socketReady && this.socket !== null) {
      this.ticks += 1
      this.pumpSocket()
      return
    }
    // 張り直しを待っているあいだは送らない（音はバッファに貯める）。ここで HTTP へ流すと、
    // 戻ってきた常時接続の送り直しと**同じ音を 2 回**送ることになる
    if (this.waitingReconnect) return
    if (!this.socketFailed && this.socket !== null) return // 接続待ち（そのうち open/close が来る）
    void this.tick() // HTTP への退避
  }

  /**
   * 常時接続の 1 周期。**周期ぶんのフレームを送る**。
   *
   * 100ms のフレームを 1 周期に 1 個だけ送ると、周期（既定 250ms）ごとに 100ms しか
   * 届かず、**実時間の 4 割しか送れない**（実測: 20 秒の録音で 84 フレーム＝8.4 秒ぶん）。
   * 送れなかった音は貯まる一方で、やがて溢れて欠ける。ここでは
   * 「周期ぶん（経過した周期 × 周期長 ÷ フレーム長）」を送る。溜まっていても
   * 一度に送る上限（{@link MAX_FRAMES_PER_TICK}）は超えない。
   */
  private pumpSocket(): void {
    const due = Math.ceil(this.ticks * this.options.intervalMs
      / (this.options.socketFrameSeconds * 1000))
    const budget = Math.min(Math.max(due - this.framesSent, 0), MAX_FRAMES_PER_TICK)
    for (let index = 0; index < budget; index += 1) {
      if (!this.sendSocketFrame()) break
    }
  }

  /** 100ms のフレームを 1 つ取り出して送る（確認できるまで `outbox` に残す）。 */
  private sendSocketFrame(): boolean {
    const frame = this.takeFrame(this.options.socketFrameSeconds)
    if (frame === null) return false
    this.retrying = true
    // 実時間ぶん送れているかの計算に数えるのは**新しい**フレームだけ（送り直しは数えない）
    this.framesSent += 1
    this.noteSent(frame)
    this.queueOverSocket(frame)
    return true
  }

  /**
   * 送った位置を画面へ知らせる（**前へだけ**）。
   *
   * <p>画面はこれで「送った位置」を持ち、断線からの送り直しで**時間が戻らない**ようにする
   * （送り直しは古い位置なので、画面の控えは前のまま）。</p>
   */
  private noteSent(frame: PendingFrame): void {
    /*
     * 送った位置は**そのフレームの先頭**（送り直しで同じ位置をもう一度知らせても、
     * 画面側は前へだけ進めるので時間は戻らない）。番号も一緒に知らせて、
     * 画面が続きの番号を残せるようにする（張り直しで 1 へ戻さない）。
     */
    this.sentSamples = Math.max(this.sentSamples,
      frame.startSample + timelineSamplesOf(frame.samples, this.rate()))
    this.lastSentEnd = Math.max(this.lastSentEnd,
      frame.startSample + timelineSamplesOf(frame.samples, this.rate()))
    this.options.onSent?.(frame.startSample, this.options.source, frame.no)
  }

  /** 送信ループだけ止める（`finish` を呼ばない＝録音を一時停止したとき）。 */
  stop(): void {
    this.running = false
    if (this.timer !== null) {
      window.clearInterval(this.timer)
      this.timer = null
    }
  }

  /** 常時接続を閉じる（画面を離れるとき）。 */
  closeSocket(): void {
    try {
      this.socket?.close()
    } catch {
      // すでに閉じているときは何もしない
    }
    this.socket = null
    this.socketReady = false
  }

  /**
   * 送信ループを止めて、**残りを送り切ってから**終わりを伝える。
   *
   * <p>尾句を落とさないために「止める → 残りを送る → `/finish`」の順で行う。
   * 送り切れないまま時間切れになったら**その旨を残す**（黙って成功にしない＝
   * {@link finishFailure} が理由を返す）。</p>
   *
   * <p>2 回呼ばれても収尾は 1 回だけ走る（走っているあいだは同じ約束を返す）。**失敗したら
   * もう一度やり直せる**（画面が同じ音源だけをやり直すため）。</p>
   */
  async finish(): Promise<void> {
    if (this.finishPromise !== null) return this.finishPromise
    this.finishError = null
    const promise = this.runFinish()
    this.finishPromise = promise
    try {
      await promise
    } finally {
      // 失敗したときは約束を捨てる（やり直しを塞がない）。成功したら残す（二重に締めない）
      if (this.finishError !== null) this.finishPromise = null
    }
  }

  /** 収尾の本体（{@link finish} から 1 回だけ呼ばれる）。 */
  private async runFinish(): Promise<void> {
    const socket = this.socket
    const useSocket = this.socketReady && socket !== null
    this.stop()
    if (useSocket) {
      // 常時接続: 先に残りを送ってから終わりを伝え、最後の確定文を待つ
      try {
        this.socket = socket
        this.socketReady = true
        // 残りを送り切る（取り出せなくなるまで）
        while (this.buffer.sampleCount > 0 && this.sendSocketFrame()) {
          // 送り切るまで繰り返す
        }
        // **変換の待ちを先に片付ける**（`finish` が番号の順を追い越さないように）
        await this.socketQueue
        if (this.socket === socket && this.socketReady) socket.send(JSON.stringify({ type: 'finish' }))
        const deadline = Date.now() + this.options.finishTimeoutMs
        while (!this.finished && Date.now() < deadline) {
          await new Promise((resolve) => window.setTimeout(resolve, 100))
        }
        if (!this.finished) {
          this.options.onNotice('書き起こしの終了通知が時間内に届きませんでした。'
            + '音声はサーバーへ送信済みです（詳細画面で結果を確認してください）。')
          this.recordFinishFailure('書き起こしの終了通知が時間内に届きませんでした。')
        }
        if (this.pendingSeconds() > 0) {
          this.recordFinishFailure('送り切れなかった音声があります（尾句の文が残らないことがあります）。')
        }
      } finally {
        try {
          socket?.close()
        } catch {
          // すでに閉じているときは何もしない
        }
        this.socket = null
        this.socketReady = false
      }
      return
    }
    await this.finishViaHttp()
  }

  /**
   * 収尾の結果（**成功なら null**。失敗したら理由）。
   *
   * <p>後方互換の口（**やり直せば直る失敗**だけを返す）。「識別が完全か」まで見る呼び出しは
   * {@link finalizeOutcome} を使う。</p>
   */
  finishFailure(): string | null {
    return this.finishError
  }

  /**
   * **この音源の収尾の結果**（まだ収尾していなければ null）。
   *
   * <p>「終わってよいか（{@code canFinish}）」と「識別が完全か（{@code complete}）」を
   * 別々に持つ。やり直しても直らない不完整な終わりは**終わってよい**が、完全ではない。</p>
   */
  finalizeOutcome(): SourceFinalizeResult | null {
    return this.finalizeResult
  }

  /**
   * 後端の収尾の欄を読んで残す（HTTP と常時接続で**同じ関数**を通す）。
   *
   * <p>`error` があったら後方互換の `finishError` にも入れる（録音中の案内は今までどおり
   * 理由を出す）。やり直しても直らない終端は `error` が null なので、`finishError` には
   * 入れない（「やり直せる失敗」と混ざらないようにする）。</p>
   */
  private applyFinalizePayload(payload: SttFinalizePayload): void {
    const result = normalizeSttFinalize(payload, this.options.source)
    this.finalizeResult = result
    if (result.kind === 'RETRYABLE_FAILURE') {
      const reason = result.notice ?? '書き起こしの終了処理で問題がありました。'
      this.recordFinishFailure(reason)
      this.options.onNotice(`書き起こしの終了処理で問題がありました（${reason}）。`
        + '音声は録音に残っています。')
      return
    }
    if (result.kind === 'INCOMPLETE_UNRECOVERABLE' && result.notice !== null) {
      // **失敗として返さない**（利用者に永久に再試行させない）。知らせとしてだけ出す
      this.options.onNotice(result.notice)
    }
  }

  /** 収尾の失敗を覚える（最初の理由を残す＝いちばん根本の原因を見せる）。 */
  private recordFinishFailure(reason: string): void {
    if (this.finishError === null) this.finishError = reason
  }

  /** 常時接続が使えないときの収尾（HTTP）。 */
  private async finishViaHttp(): Promise<void> {
    this.stop()
    const deadline = Date.now() + this.options.finishTimeoutMs
    // 送り残しを送る（失敗しても期限まで粘る）
    while ((this.outbox.length > 0 || this.buffer.sampleCount > 0) && Date.now() < deadline) {
      const before = this.outbox.length
      await this.tick()
      if (this.outbox.length === before && before > 0) {
        await new Promise((resolve) => window.setTimeout(resolve, 200))
      }
    }
    if (this.outbox.length > 0 || this.buffer.sampleCount > 0) {
      this.options.onNotice('送り切れなかった音声があります（書き起こしが一部欠けます）。'
        + '電波の良い所で【再開】すると続きから送れます。')
      this.recordFinishFailure('送り切れなかった音声があります（尾句の文が残らないことがあります）。')
    }
    const id = this.options.recordId()
    if (id === null) return
    try {
      const response = await finishClassroomSttStream(id, this.options.source)
      if (response.data.added.length > 0) this.options.onSegments(response.data.added)
      this.options.onInterim('')
      /*
       * **収尾の結果を残す**（HTTP も常時接続と同じ欄を返す）。
       *
       * <p>`data.error` だけを見ると、やり直しても直らない終端（`error=null`）を成功と誤読する。
       * ここで共通の読み取り（{@link applyFinalizePayload}）を通し、
       * 「終わってよいか」と「識別が完全か」を分けて持つ。</p>
       */
      this.applyFinalizePayload(response.data)
    } catch (cause) {
      this.options.onNotice(`書き起こしの終了処理に失敗しました（${String(cause)}）。`)
      this.recordFinishFailure(cause instanceof Error ? cause.message : String(cause))
    }
  }

  /** いまの状態（画面の表示用）。 */
  state(): SourceStreamState {
    return {
      running: this.running,
      pendingSeconds: this.pendingSeconds(),
      retrying: this.retrying,
      missingRanges: [...this.missing],
      sentFrames: this.sentFrames,
      finishFailure: this.finishFailure(),
      finalize: this.finalizeResult
    }
  }

  /** 未確認の音（秒）。いま送っている分も含む。 */
  pendingSeconds(): number {
    const rate = this.rate()
    if (rate <= 0) return 0
    const waiting = this.buffer.sampleCount + this.outbox.reduce((sum, frame) => sum + frame.samples, 0)
    return waiting / rate
  }

  /* ---------------- 内部 ---------------- */

  private rate(): number {
    return this.sampleRate > 0 ? this.sampleRate : 0
  }

  /** 送ったが確認が取れなかったフレームを、欠落として記録する（音源と絶対位置つき）。 */
  private recordMissing(frame: PendingFrame): void {
    const range = this.missingRange(frame.startSample,
      frame.startSample + timelineSamplesOf(frame.samples, this.rate()), frame.no)
    this.remember(range)
    this.options.onNotice(`確認が取れないまま ${range.fromSeconds}〜${range.toSeconds} 秒の音を諦めました`
      + '（録音そのものには入っています。あとで補書き起こしできます）。')
  }

  /** 欠落の記録を作る（秒は画面用、サンプル位置は補書き起こし用）。 */
  private missingRange(fromSample: number, toSample: number, frameNo: number): MissingRange {
    return {
      source: this.options.source,
      fromSeconds: timelineSeconds(fromSample),
      toSeconds: timelineSeconds(toSample),
      fromSample,
      toSample,
      frameNo,
      recordedAt: Date.now()
    }
  }

  /** 欠落を覚えて画面へ伝える（同じ区間を二重に数えない）。 */
  private remember(range: MissingRange): void {
    if (this.missing.some((item) => item.fromSample === range.fromSample
      && item.toSample === range.toSample)) {
      return
    }
    this.missing.push(range)
    // 画面の案内だけで終わらせない（録音から後追いで書き起こせるように残す）
    this.recovery().add(range)
    this.options.onMissingRange?.(range)
  }

  /** 保存されている補書き起こしの手がかり（画面を離れても残る）。 */
  recoveryRanges(): MissingRange[] {
    return this.recovery().list()
  }

  /** 記録ごと消したときに、手がかりも捨てる（録音の実体が無いので追い書きできない）。 */
  clearRecovery(): void {
    this.recovery().clear()
  }

  private recovery(): MissingRangeStore {
    if (this.recoveryStore === null) {
      this.recoveryStore = new MissingRangeStore({
        storageKey: `classroom-stt-recovery:${this.options.recordId() ?? 0}:${this.options.source}`,
        retentionDays: this.options.recoveryRetentionDays ?? DEFAULT_RECOVERY_RETENTION_DAYS,
        storage: this.options.recoveryStorage
      })
    }
    return this.recoveryStore
  }

  /** 1 周期ぶん: 送る音を用意して送る（失敗したら保持して次に再送）。 */
  private async tick(): Promise<void> {
    const id = this.options.recordId()
    if (id === null || this.sending) return
    /*
     * 1 回に送る長さは「**前の送信から待った音の長さ**」まで（上限は `maxSendSeconds`）。
     *
     * <p>いつも上限まで送ると、送信の周期（250ms）ごとに 2 秒ぶんの音を投げることになり、
     * **送る位置が受信した音を追い越す**（位置が跳ぶ・同じ音を別の時刻で送る）。
     * 待った長さだけ送れば、速い回線では細かく・遅い回線ではまとめて送り、
     * 位置は「採った音の先頭」と同じ速さで進む。</p>
     */
    const waited = this.lastSendAt === 0 ? this.options.maxSendSeconds
      : Math.max(HARVEST_MIN_SECONDS, (Date.now() - this.lastSendAt) / 1000)
    const maxSeconds = Math.min(this.options.maxSendSeconds, waited)
    // **確認できていない音を先に送る**（常時接続から退避したときに、欠けも重複も作らないため、
    // 古い順＝番号の順に送る。新しい音はその後ろに続く）
    if (this.outbox.length === 0 && this.takeFrame(maxSeconds) === null) return
    const sendingFrame = this.outbox[0]
    if (sendingFrame === undefined) return
    this.sending = true
    // 次に送る長さは「ここから待った長さ」で決める
    this.lastSendAt = Date.now()
    // HTTP へ退避した分のまとまりも、最初に送るフレームが認識セッションの先頭になる
    if (this.sessionOriginSample === null) this.sessionOriginSample = sendingFrame.startSample
    try {
      const response = await pushClassroomSttStream(id, sendingFrame.blob, this.options.source,
        { frameNo: sendingFrame.no, startSample: sendingFrame.startSample })
      // 送れた: 次の音へ（番号で外す＝送っているあいだに確認が進んでいても取り違えない）
      this.noteSent(sendingFrame)
      this.dropFrame(sendingFrame.no)
      this.sentFrames += 1
      this.retrying = this.outbox.length > 0
      const added = this.onRecordingTimeline(response.data.added)
      if (added.length > 0) this.options.onSegments(added)
      this.options.onInterim(response.data.interim ?? '')
    } catch (cause) {
      // 送れなかった＝**捨てない**。次の周期で同じ音を送り直す
      this.retrying = true
      // 業務エラー（200 でも `data.error`）のときも、その回に保存できた文は捨てない
      const partial = cause instanceof ApiError ? cause.data as ClassroomSttStreamPush | null : null
      if (partial !== null && partial !== undefined && (partial.added?.length ?? 0) > 0) {
        this.options.onSegments(this.onRecordingTimeline(partial.added))
      }
      this.options.onNotice('書き起こしへ音声を送れませんでした（'
        + `${cause instanceof Error ? cause.message : String(cause)}）。`
        + '同じ音を送り直します（録音は続いています）。')
    } finally {
      this.sending = false
    }
  }

  /** 確認できた（送り終えた）フレームを未確認の列から外す。 */
  private dropFrame(no: number): void {
    this.outbox = this.outbox.filter((frame) => frame.no !== no)
  }

  /**
   * 送るぶんをバッファから取り出す（空なら null。上限を超えた分は次の周期へ回す）。
   *
   * <p>**番号と絶対位置はここで決まる**（取り出す前の累計＝そのフレームの先頭）。累計は
   * 取り出し・欠落のどちらでも増えるので、位置は張り直し・一時停止・後端の再起動で戻らない。</p>
   */
  private takeFrame(seconds = this.options.maxSendSeconds): PendingFrame | null {
    const rate = this.rate()
    if (rate <= 0 || this.buffer.sampleCount === 0) return null
    const maxInputSamples = Math.max(1, Math.round(rate * seconds))
    const { pcm, inputSamples } = this.buffer.takeBounded(rate, maxInputSamples)
    if (pcm.length === 0) return null
    /*
     * このフレームの位置 ＝「まだ取り出していない音の先頭 ＋ **取り出した合計**」。
     *
     * <p>累計から出すので、捨てた区間（欠落）のぶんだけ**先の位置**になる（詰めない）。
     * 続きの録音でも `sampleOrigin` から続くので 0 へ戻らない。</p>
     */
    const frame: PendingFrame = {
      no: this.nextFrameNo,
      startSample: this.frameStart(),
      blob: new Blob([pcm], { type: PCM_CHUNK_MIME }),
      samples: inputSamples
    }
    this.nextFrameNo += 1
    this.arrivalStart += timelineSamplesOf(inputSamples, rate)
    this.consumedSamples += inputSamples
    this.outbox.push(frame)
    this.trimOutbox()
    return frame
  }

  /**
   * 確認待ちのフレームを有界に保つ（溢れた古い分は**欠落として記録する**＝黙って捨てない）。
   *
   * <p>記録には**音源と絶対位置**を残す（後から録音を聞き直して補書き起こしできるように）。
   * ここで捨てる音は「送ったが確認が取れなかった」もので、認識されているかどうかは分からない
   * （同じ発話が残っていることもある。補書き起こしは発話キーで 1 行に寄るので二重にはならない）。</p>
   */
  private trimOutbox(): void {
    const rate = this.rate()
    if (rate <= 0) return
    const keepSamples = Math.round(rate * Math.max(this.options.socketFrameSeconds,
      this.options.maxBufferSeconds))
    let kept = this.outbox.reduce((sum, frame) => sum + frame.samples, 0)
    while (kept > keepSamples && this.outbox.length > 1) {
      const oldest = this.outbox.shift()
      if (oldest === undefined) break
      kept -= oldest.samples
      this.recordMissing(oldest)
    }
  }
}

/**
 * Blob を ArrayBuffer にする。
 *
 * <p>`Blob.arrayBuffer()` が無い環境（古い jsdom など）でも送れるように `FileReader` を使う。</p>
 */
function blobToArrayBuffer(blob: Blob): Promise<ArrayBuffer> {
  if (typeof blob.arrayBuffer === 'function') return blob.arrayBuffer()
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result as ArrayBuffer)
    reader.onerror = () => reject(reader.error ?? new Error('Blob を読めませんでした'))
    reader.readAsArrayBuffer(blob)
  })
}

function round3(value: number): number {
  return Math.round(value * 1000) / 1000
}

/** 0 未満・NaN を 0 に丸める（壊れた値で時間軸を戻さない）。 */
function safeSamples(value: number | undefined): number {
  if (value === undefined || !Number.isFinite(value) || value <= 0) return 0
  return Math.floor(value)
}

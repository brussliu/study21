<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  endClassroomRecord,
  fetchClassroomOptions,
  fetchClassroomRecord,
  fetchClassroomSegments,
  classroomSourcesOf,
  mergeClassroomSegments,
  orderClassroomSegments,
  runClassroomNote,
  segmentSpeakerOf,
  sendClassroomTranscript,
  startClassroomRecord,
  uploadClassroomChunk,
  type ClassroomEndResult,
  type ClassroomNote,
  type ClassroomOptions,
  type ClassroomRecordDetail,
  type ClassroomSegment
} from '@/api/classroom'
import {
  loadAudioMode,
  loadRecordAudioMode,
  toClassroomAudioMode,
  createClassroomAudioGraph,
  displayAudioConstraints,
  hasAudioTrack,
  saveRecordAudioMode,
  type ClassroomAudioGraph,
  type ClassroomAudioMode
} from '@/features/classroom/audio-graph'
import {
  PCM_CAPTURE_PROCESSOR,
  PCM_CAPTURE_WORKLET_SOURCE,
  PCM_CHUNK_MIME,
  PcmChunkBuffer
} from '@/features/classroom/pcm'
import {
  CLASSROOM_QUERY,
  formatElapsed,
  formatSegmentTime,
  languageModeLabel,
  noteSections,
  presetName,
  statusBadgeClass
} from '@/features/classroom/classroom'
import {
  SourceStream,
  type MissingRange
} from '@/features/classroom/source-stream'
import type { ClassroomSource } from '@/api/classroom'
import '@/features/classroom/classroom.css'

/**
 * 授業録音 / AI 授業記録 — 録音中の画面。
 *
 * 左にリアルタイムの書き起こし（STT 転写）、右に AI 授業ノート（テーマ／学習内容／
 * 先生の重点／宿題 を段階更新）を並べ、下に録音の開始・停止と授業の終了を置く。
 *
 * つながっている API（すべて user-api。AI ノートだけ admin-api の薄い入口）:
 *   1. `POST /classroom/{id}/start` … 録音の開始（開始時刻を確定）
 *   2. `POST /classroom/{id}/chunks?seq=N` … `MediaRecorder` の分塊を送る（長さは `options.chunkSeconds`）
 *      応答の `appendedSegments` を書き起こしへ足し、`triggered` のときは `runPath` を 1 回だけ呼ぶ
 *   3. `GET /classroom/{id}/segments?afterSeq=N` … 数秒間隔のポーリングで追記を取る
 *   4. `POST /classroom/{id}/end` → 返る `finalNoteId` の `runPath` を呼ぶ（最終まとめ = batC62）
 *
 * 録音そのものはブラウザの `MediaRecorder` が行う（音声はサーバーへ保存され、詳細画面で再生できる）。
 * `MediaRecorder` が無い環境では録音はせず、その旨を日本語で案内する（書き起こしの取得は続ける）。
 *
 * **書き起こしを誰が行うかは設定（STT プロバイダー）で決まる**（`options.sttMode`）:
 *   - `SERVER` … 分塊を送るとサーバーが STT を呼ぶ（API Key が必要。Google Cloud / OpenAI 互換）
 *   - `BROWSER` … **この画面が Web Speech API（Chrome / Edge。キー不要）で認識**し、
 *     final になった発話を `POST /classroom/{id}/transcripts` で送る（サーバーは音声の保存だけ）
 */
const route = useRoute()
const router = useRouter()
const toast = useToast()

const area = computed(() => route.path.split('/')[1] ?? 'student')

/** 授業記録の ID（数値のときだけ API につながる）。 */
const recordId = computed<number | null>(() => {
  const raw = route.params.id
  const value = Array.isArray(raw) ? raw[0] : raw
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed > 0 ? parsed : null
})

/** 新しい授業から渡された内容（API の値が取れたらそちらを優先する）。 */
const queryName = computed(() => {
  const value = route.query[CLASSROOM_QUERY.name]
  return typeof value === 'string' && value !== '' ? value : '（授業名未指定）'
})
const subject = computed(() => {
  const value = route.query[CLASSROOM_QUERY.subject]
  return typeof value === 'string' && value !== '' ? value : '（未指定）'
})
const queryLanguageMode = computed(() => {
  const raw = route.query[CLASSROOM_QUERY.languageMode]
  return languageModeLabel(typeof raw === 'string' ? raw : undefined)
})
const queryPreset = computed(() => {
  const raw = route.query[CLASSROOM_QUERY.preset]
  return presetName(typeof raw === 'string' ? raw : undefined)
})

/* ---------- 音源（マイク／マイク＋スピーカー） ---------- */

/**
 * 録音する音源。**【新しい授業】の dialog で選んだ値**が query で渡る（利用者の指示で選択を移した）。
 *
 * - `mic` … 対面の授業（今までどおり。マイクだけ）
 * - `mic-pc` … オンライン授業。**PC が再生している先生の声**も混ぜる（共有で選ぶ）
 *
 * <p>query が無い・知らない値のときは、前回選んだ音源（`localStorage`）に戻し、
 * それも無ければ今までどおりマイクだけにする。**この画面では変えられない**ので、
 * 値は開いたときに 1 回だけ決める（選択は【新しい授業】にしかない）。</p>
 */
const audioMode: ClassroomAudioMode = audioModeFromQuery()

/** 二つの音を混ぜるモードか（マイク＋スピーカー）。 */
const mixing = audioMode !== 'mic'

/**
 * **この記録の音源の設定**（書き起こしの**話者**はこれで決める）。
 *
 * <p>値は query（【新しい授業】の dialog が渡す）→ **その記録のために覚えた値**
 * （`saveRecordAudioMode`。録音中の画面が録音を始めたときに残す＝開き直しても分かる）
 * → null（分からない）の順で決める。録音を始めた時点で**実際に録音した音源**へ確定し、
 * 記録のために残す（利用者の指示 7 節: 詳細画面は query ではなく**その記録の設定**で
 * 話者を決める）。</p>
 *
 * <p>**端末の「前回の選択」（`localStorage`）は話者には使わない**（`mixing` と違う所）。
 * それは「この端末の前回の選択」であって**この記録の設定**ではないので、古い記録の話者を
 * 勝手に決めてしまう。分からないときはサーバーが返した話者をそのまま使う。</p>
 */
const configuredAudioMode = ref<ClassroomAudioMode | null>(configuredAudioModeOf())

/** query（無ければ**この記録のために覚えた値**）から、この記録の音源を求める。 */
function configuredAudioModeOf(): ClassroomAudioMode | null {
  const fromQuery = toClassroomAudioMode(
    Array.isArray(route.query[CLASSROOM_QUERY.audioMode])
      ? route.query[CLASSROOM_QUERY.audioMode]?.[0]
      : route.query[CLASSROOM_QUERY.audioMode]
  )
  if (fromQuery !== null) return fromQuery
  const id = recordId.value
  return id === null ? null : loadRecordAudioMode(id)
}

/** query の音源（知らない値・無い値は前回選んだ音源にする）。 */
function audioModeFromQuery(): ClassroomAudioMode {
  const raw = route.query[CLASSROOM_QUERY.audioMode]
  const value = Array.isArray(raw) ? raw[0] : raw
  return toClassroomAudioMode(value) ?? loadAudioMode()
}

/* ---------- サーバーから読む値 ---------- */

const detail = ref<ClassroomRecordDetail | null>(null)
const options = ref<ClassroomOptions | null>(null)
const segments = ref<ClassroomSegment[]>([])
const notes = ref<ClassroomNote[]>([])
/** 次に要求する afterSeq（サーバーが返す nextSeq をそのまま使う）。 */
const nextSeq = ref(0)
/** 書き起こし・ノートを取れなかったときの理由（録音は続ける）。 */
const syncError = ref('')
/**
 * 書き起こし（STT）そのものの状態の案内（つなぎ直し中・停止）。
 *
 * <p>`syncError` と分ける: ポーリングは成功のたびに `syncError` を消すので、そこへ書くと
 * せっかくの案内がすぐ消える（実測で「出たと思ったら消える」状態になった）。</p>
 */
const sttStreamNotice = ref('')
/** 操作が失敗したときの理由（日本語）。 */
const actionError = ref('')
/** このブラウザで録音できないときの案内。 */
const recorderNotice = ref('')
/** 終了時にサーバーが返した補足（書き起こしが無く最終まとめを作らなかったときなど）。 */
const finishNotice = ref('')
/** 録音の途中で画面を開き直したときの案内（この画面は録音していない）。 */
const resumeNotice = ref('')
/** ブラウザ音声認識の案内（非対応・接続できない・マイクが許可されない等）。 */
const browserSttNotice = ref('')
/** 認識中の途中経過（画面に出すだけ。保存は final だけ）。 */
const interimText = ref('')
/**
 * 書き起こし用の音声（PCM）を用意できなかったときの案内。
 * サーバー認識のときは**これが無いと書き起こせない**ので、黙って続けない。
 */
const pcmNotice = ref('')

/** ブラウザ音声認識のインスタンス（録音中だけ入る）。 */
let speechRecognition: SpeechRecognitionLike | null = null
/** 認識に使っている言語（画面に出す）。 */
const browserSttLang = ref('')
/** 認識結果の送信を直列にする（順序が入れ替わらないように）。 */
let transcriptChain: Promise<void> = Promise.resolve()
/** 認識が終わったあと、聞き直すまでの待ち（ミリ秒）。 */
const BROWSER_STT_RESTART_DELAY_MS = 300

/** 書き起こしをブラウザ（Web Speech API）で行うか。 */
const browserStt = computed(() => options.value?.sttMode === 'BROWSER')

/** AI 解析（フェーズノート・最終まとめ）が無効か。 */
const aiNotesOff = computed(() => options.value !== null && options.value.noteEnabled === false)

/**
 * 二音源なのに**ブラウザ認識**で書き起こしているか。
 *
 * <p>ブラウザの認識（Web Speech API）は**マイクからしか音を取れない**（共有した音を渡す口が無い）。
 * つまりこの組み合わせでは、スピーカーから出る先生の声は文字にならない。黙って半分の
 * 書き起こしを見せないよう、画面ではっきり知らせる（録音と再生用の音声には両方入っている）。</p>
 */
const micOnlyTranscript = computed(() => mixing && browserStt.value)

/** メタ行に出す「書き起こし」の説明（誰が認識するか。二音源＋ブラウザ認識は「マイクのみ」）。 */
const sttModeLabel = computed(() => {
  if (options.value === null) return '（未取得）'
  if (!browserStt.value) return 'サーバー認識（API Key を使用）'
  const detail = `Chrome / Edge・キー不要${browserSttLang.value === '' ? '' : '・' + browserSttLang.value}`
  return micOnlyTranscript.value
    ? `ブラウザ認識（マイクのみ・${detail}）`
    : `ブラウザ認識（${detail}）`
})

const lessonName = computed(() => detail.value?.title ?? queryName.value)
const languageMode = computed(() => (
  detail.value === null ? queryLanguageMode.value : languageModeLabel(detail.value.languageMode)
))
const preset = computed(() => {
  const name = detail.value?.presetName
  return name === null || name === undefined || name === '' ? queryPreset.value : name
})

/* ---------- 録音の状態 ---------- */

const recording = ref(false)
const elapsedSeconds = ref(0)
const starting = ref(false)
const finishing = ref(false)
let timer: number | null = null

const elapsedLabel = computed(() => formatElapsed(elapsedSeconds.value))
const maxRecordingMinutes = computed(() => options.value?.maxRecordingMinutes ?? 120)
/** 経過時間が上限に達したか（超えたら自動で止める）。 */
const limitReached = computed(() => elapsedSeconds.value >= maxRecordingMinutes.value * 60)

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/* ---------- ブラウザ音声認識（Web Speech API。キー不要） ---------- */

/**
 * ブラウザの認識 API（Chrome / Edge のみ。型は標準の lib に無いのでここで最小限を宣言する）。
 *
 * `webkitSpeechRecognition` は Chrome の実装名（`SpeechRecognition` も同じものを指す）。
 */
interface SpeechRecognitionAlternativeLike { transcript: string }
interface SpeechRecognitionResultLike {
  isFinal: boolean
  0: SpeechRecognitionAlternativeLike
  length: number
}
interface SpeechRecognitionEventLike {
  resultIndex: number
  results: { length: number; [index: number]: SpeechRecognitionResultLike }
}
interface SpeechRecognitionErrorLike { error: string; message?: string }
interface SpeechRecognitionLike {
  lang: string
  continuous: boolean
  interimResults: boolean
  maxAlternatives: number
  start: () => void
  stop: () => void
  abort: () => void
  onresult: ((event: SpeechRecognitionEventLike) => void) | null
  onerror: ((event: SpeechRecognitionErrorLike) => void) | null
  onend: (() => void) | null
}
type SpeechRecognitionConstructor = new () => SpeechRecognitionLike

/** このブラウザの認識 API（無ければ null）。 */
function speechRecognitionOf(): SpeechRecognitionConstructor | null {
  const scope = window as unknown as {
    SpeechRecognition?: SpeechRecognitionConstructor
    webkitSpeechRecognition?: SpeechRecognitionConstructor
  }
  return scope.SpeechRecognition ?? scope.webkitSpeechRecognition ?? null
}

/** 画面が認識した発話を送る（失敗は案内だけ出して録音は続ける）。 */
function sendTranscript(text: string, offsetSeconds: number): void {
  const id = recordId.value
  if (id === null || text.trim() === '') return
  transcriptChain = transcriptChain.then(async () => {
    try {
      const response = await sendClassroomTranscript(id, {
        text: text.trim(),
        offsetSeconds: Math.max(0, Math.round(offsetSeconds * 10) / 10)
      })
      syncError.value = ''
      appendSegments(response.data.appendedSegments)
      nextSeq.value = Math.max(nextSeq.value, response.data.nextSeq)
      const runPath = response.data.runPath
      if (response.data.triggered && runPath !== null && runPath !== '') {
        void runClassroomNote(runPath, 'classroom-live-view').catch((cause: unknown) => {
          syncError.value = messageOf(cause, 'AI ノートの生成を開始できませんでした。')
        })
      }
    } catch (caught) {
      syncError.value = messageOf(caught, '書き起こしを送信できませんでした。')
    }
  })
}

/** 認識を始める（使えないブラウザでは案内を出して false）。 */
function startBrowserRecognition(): boolean {
  const Constructor = speechRecognitionOf()
  if (Constructor === null) {
    browserSttNotice.value = 'このブラウザは音声認識に対応していません（Chrome / Edge をお使いください）。'
      + '音声は保存されますが、書き起こしは行われません。'
    return false
  }
  const mode = detail.value?.languageMode ?? route.query[CLASSROOM_QUERY.languageMode]
  const lang = options.value?.sttLanguageCodes?.[typeof mode === 'string' ? mode : 'ja'] ?? 'ja-JP'
  const recognition = new Constructor()
  recognition.lang = lang
  recognition.continuous = true
  recognition.interimResults = true
  recognition.maxAlternatives = 1
  recognition.onresult = (event) => {
    let interim = ''
    for (let index = event.resultIndex; index < event.results.length; index += 1) {
      const result = event.results[index]
      if (result.isFinal) {
        sendTranscript(result[0].transcript, elapsedSeconds.value)
      } else {
        interim += result[0].transcript
      }
    }
    interimText.value = interim
  }
  recognition.onerror = (event) => {
    // no-speech / aborted は日常的に起きるので黙って続ける
    if (event.error === 'no-speech' || event.error === 'aborted') return
    browserSttNotice.value = recognitionErrorMessage(event.error)
  }
  // continuous でも無音や一定時間で終わるので、録音中なら聞き直す（利用者には見せない）
  recognition.onend = () => {
    interimText.value = ''
    if (!recording.value || speechRecognition === null) return
    window.setTimeout(() => {
      if (!recording.value || speechRecognition === null) return
      try {
        speechRecognition.start()
      } catch {
        // すでに動いているときの例外は無視する
      }
    }, BROWSER_STT_RESTART_DELAY_MS)
  }
  speechRecognition = recognition
  browserSttLang.value = lang
  try {
    recognition.start()
    return true
  } catch {
    speechRecognition = null
    browserSttNotice.value = '音声認識を開始できませんでした。ページを再読み込みしてお試しください。'
    return false
  }
}

/** 認識のエラーを日本語の案内にする。 */
function recognitionErrorMessage(error: string): string {
  switch (error) {
    case 'not-allowed':
    case 'service-not-allowed':
      return 'マイクの使用が許可されていないため、音声認識を続けられません（ブラウザの設定を確認してください）。'
        + '音声は保存されます。'
    case 'network':
      return '音声認識サービスに接続できませんでした（ブラウザの音声認識は外部サービスを使います）。'
        + '音声は保存されます。'
    case 'audio-capture':
      return 'マイクが見つかりませんでした。音声認識を続けられません（音声の保存もできません）。'
    default:
      return '音声認識でエラーが発生しました（' + error + '）。'
  }
}

/** 認識を止める（録音の停止・画面を離れるとき）。 */
function stopBrowserRecognition(): void {
  const recognition = speechRecognition
  speechRecognition = null
  interimText.value = ''
  if (recognition === null) return
  try {
    recognition.onend = null
    recognition.stop()
  } catch {
    // 止められなくても録音は止まる
  }
}

/* ---------- MediaRecorder（分塊アップロード） ---------- */

/** 分塊の長さ（秒。設定 `CLASSROOM_AI_CHUNK_SECONDS`）。 */
const chunkSeconds = computed(() => options.value?.chunkSeconds ?? 20)
/** 送信済みの連番（次の分塊は +1）。再送しても同じ連番にしない。 */
let chunkSeq = 0
/** サーバーが持っている最後の連番（画面を開き直したときに続きから送る）。 */
const maxKnownSeq = computed(() => segments.value.reduce((max, segment) => Math.max(max, segment.seq), 0))
let recorder: MediaRecorder | null = null
let stream: MediaStream | null = null
/** 分塊の送信を直列にする（順序が入れ替わると STT の並びが崩れるため）。 */
let uploadChain: Promise<void> = Promise.resolve()
/**
 * 録音を始めた時刻と、直前の分塊が終わった秒。
 *
 * <p>書き起こしの時刻は**ここで測った実際の経過秒**を送る。以前はサーバーが
 * 「連番 × そのときの分塊の長さ設定」で計算していたため、録音中に設定を変えると
 * 時系列が壊れた（実測: 00:00 / 00:20 / 00:40 / 00:15 …）。</p>
 */
let recorderStartedAtMs = 0
let lastChunkEndSeconds = 0

/** いまの分塊の [始まり, 終わり] 秒（実際の経過時間から。単調に増える）。 */
function nextChunkOffsets(): { startSeconds: number; endSeconds: number } {
  const nowSeconds = recorderStartedAtMs === 0 ? 0 : (Date.now() - recorderStartedAtMs) / 1000
  const endSeconds = Math.max(lastChunkEndSeconds + 0.1, Math.round(nowSeconds * 100) / 100)
  const startSeconds = lastChunkEndSeconds
  lastChunkEndSeconds = endSeconds
  return { startSeconds, endSeconds }
}

/**
 * 1 つの分塊を送る（STT はサーバー側。応答の追記セグメントを画面へ足す）。
 *
 * `sttPcm` は**書き起こし用**の 16kHz PCM。`MediaRecorder` の分塊は 2 つ目以降が
 * コンテナのヘッダを持たず認識エンジンがデコードできないので、認識にはこちらを使う
 * （再生用の音声は今までどおり `blob` を保存する）。
 */
/** 送れなかった分塊（ネットワークの波で落ちても、黙って捨てない）。 */
interface PendingChunk {
  seq: number
  blob: Blob
  sttPcm: Blob | null
  offsets: { startSeconds: number; endSeconds: number }
  attempts: number
}

/** 送信待ちの分塊（古い順に送り直す）。 */
const pendingChunks = ref<PendingChunk[]>([])
/** 送り直しの予約（1 本だけ）。 */
let retryTimer: number | null = null
/** 送り直しの間隔（ミリ秒）と、あきらめるまでの回数。 */
const RETRY_DELAY_MS = 10_000
const RETRY_MAX_ATTEMPTS = 5

/** 送信待ちの件数を画面に出す（0 のときは何も出さない）。 */
const pendingNotice = computed(() => {
  const count = pendingChunks.value.length
  if (count === 0) return ''
  const giving = pendingChunks.value.some((chunk) => chunk.attempts >= RETRY_MAX_ATTEMPTS)
  return giving
    ? `送れなかった音声が ${count} 件あります（電波の良い所で【送り直す】を押してください）。`
    : `音声を送り直しています…（待ち ${count} 件）`
})

function sendChunk(blob: Blob, sttPcm: Blob | null = null): void {
  const id = recordId.value
  if (id === null || blob.size === 0) return
  const seq = ++chunkSeq
  // この分塊が録音のどこかを、実際の経過時間で測る（サーバーはこの値を使う）
  const offsets = nextChunkOffsets()
  uploadChain = uploadChain.then(() => uploadOne(id, { seq, blob, sttPcm, offsets, attempts: 0 }))
}

/** 1 つの分塊を送る（失敗したら待ち行列へ入れて、あとで送り直す）。 */
async function uploadOne(id: number, chunk: PendingChunk): Promise<void> {
  try {
    const response = await uploadClassroomChunk(id, chunk.seq, chunk.blob, chunk.sttPcm, chunk.offsets)
    syncError.value = ''
    appendSegments(response.data.appendedSegments)
    nextSeq.value = Math.max(nextSeq.value, response.data.nextSeq)
    // 送れたら待ち行列から外す
    pendingChunks.value = pendingChunks.value.filter((item) => item.seq !== chunk.seq)
    schedulePendingRetry()
    const runPath = response.data.runPath
    if (response.data.triggered && runPath !== null && runPath !== '') {
      // フェーズ分析（batC61）を 1 回だけ起動する（結果はポーリングで取る）
      void runClassroomNote(runPath, 'classroom-live-view').catch((cause: unknown) => {
        const message = messageOf(cause, 'AI ノートの生成を開始できませんでした。')
        syncError.value = message
      })
    }
  } catch (caught) {
    const reason = messageOf(caught, '音声を送信できませんでした。')
    // 4xx は内容の問題なので何度送っても通らない（待ち行列には入れず、理由を出すだけ）
    const status = caught instanceof ApiError ? caught.status : undefined
    if (status !== undefined && status >= 400 && status < 500) {
      syncError.value = reason
      return
    }
    const existing = pendingChunks.value.find((item) => item.seq === chunk.seq)
    if (existing === undefined) {
      chunk.attempts += 1
      pendingChunks.value = [...pendingChunks.value, chunk]
    } else {
      existing.attempts += 1
    }
    syncError.value = ''
    schedulePendingRetry()
  }
}

/** 送り直しを予約する（待ち行列が空なら何もしない）。 */
function schedulePendingRetry(): void {
  if (pendingChunks.value.length === 0) {
    if (retryTimer !== null) {
      window.clearTimeout(retryTimer)
      retryTimer = null
    }
    return
  }
  if (retryTimer !== null) return
  retryTimer = window.setTimeout(() => {
    retryTimer = null
    void flushPendingChunks()
  }, RETRY_DELAY_MS)
}

/** 待っている分塊を順に送り直す（【送り直す】からも呼ぶ）。 */
async function flushPendingChunks(): Promise<void> {
  const id = recordId.value
  if (id === null) return
  const waiting = [...pendingChunks.value]
  for (const chunk of waiting) {
    if (chunk.attempts > RETRY_MAX_ATTEMPTS) continue
    await uploadOne(id, chunk)
    if (pendingChunks.value.some((item) => item.seq === chunk.seq)) {
      // まだ送れないなら、この回はここまで（順番を保つ）
      break
    }
  }
  schedulePendingRetry()
}

/**
 * 追記セグメントを書き起こしへ足す（**同じ発話の新しい文が勝つ**）。
 *
 * <p>「同じ連番があるから」と捨てない。サーバーは発話キーで 1 行に寄せるので、同じ行が
 * **訂正・追記された文**で届く（連番は変わらない）。捨てると直した文が画面に出ない。</p>
 *
 * <p>`source` は**その文を出した音源**（音源ごとの送信から届いたときだけ分かる）。
 * 話者を音源の設定で決めるために行へ付けておく（サーバーは届いた音で二音源かを決めるので、
 * 届いていない回は「講義」と返す）。</p>
 */
function appendSegments(appended: ClassroomSegment[], source?: ClassroomSource): void {
  if (appended.length === 0) return
  const tagged = source === undefined ? appended : appended.map((segment) => ({ ...segment, source }))
  segments.value = mergeClassroomSegments(segments.value, tagged)
}

/**
 * 画面に出す順序（利用者の指示 3-4）: **実際の発言開始時刻**で並べ、遅れて届いた文も正しい位置へ入れる。
 *
 * <p>並べ替えの規則は詳細画面と共通（`orderClassroomSegments`）。同時刻は連番で安定させ、
 * 同時発言（時間の重なり）は**消さずそのまま**出す（片方を落とさない）。**話者は入れ替えない**。</p>
 */
const orderedSegments = computed(() => orderClassroomSegments(segments.value))

/** 転写 1 行の話者ラベル（**この記録の音源の設定**で決める。詳細画面と同じ規則）。 */
function speakerLabelOf(segment: ClassroomSegment): string {
  return segmentSpeakerOf(configuredAudioMode.value, segment)
}

/* ---------- 音源（マイク／マイク＋スピーカー）の用意と片付け ---------- */

/** 共有で得た流れ（音声だけ混ぜる。映像は保存しない） */
let displayStream: MediaStream | null = null
/** 混ぜた音の束（マイクのみのときは null＝今までどおり生のマイクを使う） */
let audioGraph: ClassroomAudioGraph | null = null
/**
 * 混ぜる仕組みの `AudioContext`。
 *
 * <p>**書き起こし用 PCM の取り出し（`AudioWorklet`）と同じコンテキストを使う**。
 * 別のコンテキストの節点へ `connect` すると `InvalidAccessError` になり、
 * 二音源のときだけ**黙って PCM が取れなくなる**（実測: サーバー認識でも
 * スピーカーの音が書き起こされなかった原因の 1 つ）。</p>
 */
let mixContext: AudioContext | null = null
/** マイクと遠隔の音量（表示用。0〜1） */
const micLevel = ref(0)
const remoteLevel = ref(0)
let levelTimer: number | null = null

/**
 * 録音に使う音源を用意する（マイク＋必要なら PC が再生している音）。
 *
 * <p>取れなければ `false`（＝黙ってマイクだけに落とさない）。前の録音で握ったままの
 * 音源（共有・混ぜる仕組み）は先に片付ける（同じ組み合わせを二重に掴まない）。</p>
 */
async function prepareAudioSources(): Promise<boolean> {
  releaseAudioSources()
  const mic = await navigator.mediaDevices.getUserMedia({
    audio: { echoCancellation: true, noiseSuppression: true, autoGainControl: true }
  })
  stream = mic
  if (!mixing) return true
  const shared = await requestDisplayAudio()
  if (shared === null) {
    for (const track of mic.getTracks()) track.stop()
    stream = null
    return false
  }
  displayStream = shared
  watchSharedAudio(shared)
  const AudioContextClass = window.AudioContext
    ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  const context = AudioContextClass === undefined ? null : new AudioContextClass()
  if (context === null) {
    // 混ぜられないのに始めると、マイクだけの音を「両方入っている」と誤解させる
    for (const track of shared.getTracks()) track.stop()
    for (const track of mic.getTracks()) track.stop()
    displayStream = null
    stream = null
    recorderNotice.value = 'このブラウザは音を混ぜられません（Chrome / Edge をお使いください）。'
    return false
  }
  mixContext = context
  audioGraph = createClassroomAudioGraph(mic, { context, remote: shared })
  startLevelMeter()
  return true
}

/** 本番の録音に使う流れ（混ぜているときは混ぜたあとの音）。 */
function recordStreamOf(): MediaStream | null {
  return audioGraph?.stream ?? stream
}

/** 共有の音が終わった（「共有を停止」・タブを閉じた）ことを知らせる。 */
function watchSharedAudio(shared: MediaStream): void {
  const remoteTrack = shared.getAudioTracks()[0]
  if (remoteTrack === undefined) return
  remoteTrack.addEventListener('ended', () => {
    recorderNotice.value = '共有の音が終わりました。【共有を選び直す】で再開できます。'
  })
}

/** 用意した音源（マイク・共有・混ぜる仕組み）を全部片付ける。 */
function releaseAudioSources(): void {
  stopLevelMeter()
  audioGraph?.close()
  audioGraph = null
  const context = mixContext
  mixContext = null
  if (context !== null && context.state !== 'closed') void context.close().catch(() => undefined)
  if (displayStream !== null) {
    displayStream.getTracks().forEach((track) => track.stop())
    displayStream = null
  }
  if (stream !== null) {
    stream.getTracks().forEach((track) => track.stop())
    stream = null
  }
}

/** 音量の表示を回す（録音中だけ）。 */
function startLevelMeter(): void {
  if (levelTimer !== null) return
  levelTimer = window.setInterval(() => {
    const levels = audioGraph?.levels()
    micLevel.value = levels?.mic ?? 0
    remoteLevel.value = levels?.remote ?? 0
  }, 200)
}

function stopLevelMeter(): void {
  if (levelTimer !== null) {
    window.clearInterval(levelTimer)
    levelTimer = null
  }
  micLevel.value = 0
  remoteLevel.value = 0
}

/**
 * 画面共有で**音**をもらう（映像は保存も送信もしない）。
 *
 * <p>タブの音声をチェックし忘れると音声トラックが来ない。そのときは**黙って続けない**
 * （「録れているつもり」を防ぐ）。</p>
 */
async function requestDisplayAudio(): Promise<MediaStream | null> {
  const media = navigator.mediaDevices as MediaDevices & {
    getDisplayMedia?: (
      constraints: { video: boolean; audio: { suppressLocalAudioPlayback: boolean } }
    ) => Promise<MediaStream>
  }
  if (media.getDisplayMedia === undefined) {
    recorderNotice.value = 'このブラウザは画面共有の音を取れません（Chrome / Edge をお使いください）。'
    return null
  }
  let shared: MediaStream | null = null
  try {
    shared = await media.getDisplayMedia(displayAudioConstraints())
  } catch {
    recorderNotice.value = '共有が許可されませんでした。共有を選び直してください（映像は使いません）。'
    return null
  }
  if (!hasAudioTrack(shared)) {
    // 映像だけの共有 → 共有を止めて、やり直してもらう
    for (const track of shared.getTracks()) track.stop()
    recorderNotice.value = '共有の音が入っていません。共有するときに「タブの音声を共有」'
      + '（画面全体なら「システム オーディオを共有」）にチェックを入れてください。'
    return null
  }
  return shared
}

/* ---------- 書き起こし用の PCM（サーバー側 STT のときだけ） ---------- */

/**
 * 分塊ごとの STT（ストリーミングが使えない設定）で使う、**混ぜた音**の PCM。
 *
 * <p>ストリーミング書き起こし（阿里巴巴）のときは音源ごとの PCM を別に取るので、ここは使わない。</p>
 */
const pcmBuffer = new PcmChunkBuffer()
let pcmContext: AudioContext | null = null
let pcmSource: MediaStreamAudioSourceNode | null = null
let pcmNode: AudioWorkletNode | null = null

/** ストリーミング書き起こしを使うか（阿里巴巴のリアルタイム認識のときだけ）。 */
const streamStt = computed(() => options.value?.streamStt === true && !browserStt.value)

/** 音源ごとの PCM 取り出し（AudioWorklet）。 */
interface PcmCapture {
  context: AudioContext
  node: AudioWorkletNode
  streamSource: MediaStreamAudioSourceNode
}
const captures: Partial<Record<ClassroomSource, PcmCapture>> = {}

/** 音源ごとの送信（マイク／共有の音で 1 つずつ。片方が切れてももう片方は続く）。 */
const sourceStreams: Partial<Record<ClassroomSource, SourceStream>> = {}

/** 音源ごとの「途中の文」（保存はしない。画面は 1 行だけ出す）。 */
const interimOf: Record<ClassroomSource, Ref<string>> = { mic: ref(''), shared: ref('') }
/** 音源ごとの案内（失敗・欠落・復帰）。 */
const noticeOf: Record<ClassroomSource, Ref<string>> = { mic: ref(''), shared: ref('') }
/** 音源ごとの「送れずに飛ばした区間」（秒）。 */
const missingOf: Record<ClassroomSource, Ref<MissingRange[]>> = {
  mic: ref([]), shared: ref([])
}

/** 音源の並び（この順に画面へ出す）。 */
const SOURCES: ClassroomSource[] = ['mic', 'shared']
/** 音源の日本語ラベル（画面表示と案内に使う）。 */
const SOURCE_LABELS: Record<ClassroomSource, string> = { mic: 'マイク', shared: '共有の音' }
/** 話者ラベル（サーバーが音源から決める）。色分けのクラスにも使う。 */
const SPEAKER_CLASSES: Record<string, string> = {
  先生: 'cr-transcript__line--teacher',
  学生: 'cr-transcript__line--student',
  講義: 'cr-transcript__line--lecture'
}

/**
 * 1 音源ぶんの PCM 取り出しを始める（**音源ごとに別のコンテキストと送信キュー**）。
 *
 * <p>混ぜた音からの取り出しはしない（混ぜる前に音源ごとに認識する＝利用者の指示）。
 * そのため `connect` のコンテキスト違いで黙って取れなくなる問題も起きない。</p>
 */
async function startSourceCapture(source: ClassroomSource, media: MediaStream): Promise<boolean> {
  const AudioContextClass = window.AudioContext
    ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  if (AudioContextClass === undefined) return false
  const context = new AudioContextClass()
  try {
    if (context.audioWorklet === undefined) {
      void context.close().catch(() => undefined)
      noticeOf[source].value = 'このブラウザは書き起こし用の音声を取り出せません'
        + '（Chrome / Edge をお使いください）。'
      return false
    }
    const moduleUrl = URL.createObjectURL(
      new Blob([PCM_CAPTURE_WORKLET_SOURCE], { type: 'application/javascript' }))
    try {
      await context.audioWorklet.addModule(moduleUrl)
    } finally {
      URL.revokeObjectURL(moduleUrl)
    }
    const streamSource = context.createMediaStreamSource(media)
    const node = new AudioWorkletNode(context, PCM_CAPTURE_PROCESSOR)
    node.port.onmessage = (event: MessageEvent<Float32Array>) => {
      // 送信キューへ渡す（送信は SourceStream が受け持つ＝送れなければ保持して再送する）
      sourceStreams[source]?.append(event.data, context.sampleRate)
    }
    // 音をそのまま出すとハウリングするので、0 のゲインを通してから出力へ繋ぐ
    const silent = context.createGain()
    silent.gain.value = 0
    streamSource.connect(node)
    node.connect(silent)
    silent.connect(context.destination)
    captures[source] = { context, node, streamSource }
    return true
  } catch {
    void context.close().catch(() => undefined)
    noticeOf[source].value = '書き起こし用の音声を準備できませんでした'
      + '（ページを再読み込みしてお試しください）。'
    return false
  }
}

/** 音源ごとの送信を始める（録音を始めるとき）。 */
function startSourceStreams(): void {
  for (const source of SOURCES) {
    sourceStreams[source]?.start()
  }
}

/** 音源ごとの送信を止める（`/finish` は呼ばない＝一時停止）。 */
function stopSourceStreams(): void {
  for (const source of SOURCES) {
    sourceStreams[source]?.stop()
    // 収尾（finish）が済んだあとに呼ばれるので、接続も閉じてよい
    sourceStreams[source]?.closeSocket()
  }
}

/**
 * 音源ごとの送信を**送り切ってから**終わりを伝える（尾句を落とさない）。
 *
 * <p>音源ごとに順番に待つ（片方が遅くても、もう片方の結果は先に画面へ出る）。</p>
 */
async function finishSourceStreams(): Promise<void> {
  for (const source of SOURCES) {
    const stream = sourceStreams[source]
    if (stream !== undefined) await stream.finish()
  }
}

/**
 * 収尾の段階（利用者の指示 7 節: 「録音停止」「音声の送信完了」「転写完了」「まとめ完了」は**別の状態**）。
 *
 * <p>割合（％）は出さない（実際の進み具合を測れないため）。いま何を待っているかを文章で出す。</p>
 */
type FinishPhase = 'idle' | 'stopping' | 'sending-tail' | 'transcribing' | 'note' | 'done' | 'failed'
const finishPhase = ref<FinishPhase>('idle')

/** 走っている収尾の種類（停止だけか、授業を終えるのか）。やり直しの宛先になる。 */
type FinalizeKind = 'stop' | 'finish'
const finalizeKind = ref<FinalizeKind>('stop')

/** 記録の終了（`POST /classroom/{id}/end`）の結果。**HTTP 200 でも `error` なら成功ではない**。 */
type EndOutcome =
  | { kind: 'ended'; result: ClassroomEndResult }
  /** すでに終わっていた（別のタブ・二度押し・再読み込みのあと）。失敗ではない */
  | { kind: 'already' }
  | { kind: 'failed' }

/**
 * 収尾（**「録音を停止」と「授業を終了」で同じ 1 つ**の処理）の進み具合。
 *
 * <p>「どこまで済んだか」を持ち、やり直しでは**まだ済んでいない段だけ**を実行する
 * （送り終えた音を送り直さない・記録を二重に終えない・最終まとめを二重に起動しない）。
 * 失敗してもこの記録は消さない（消すと、やり直しで最初から全部やり直すことになる）。</p>
 */
interface FinalizeProgress {
  /** 録音を止めて、最後の分塊（再生用の音声）まで送り切れたか。 */
  recorderFlushed: boolean
  /** 記録の終了（データベースへの保存）の結果。まだなら null。 */
  recordEnd: EndOutcome | null
  /** 最終まとめ（batC62）の起動が済んだか。 */
  noteStarted: boolean
}

let finalizeProgress: FinalizeProgress = { recorderFlushed: false, recordEnd: null, noteStarted: false }
/**
 * いま収尾が走っているか（**同期的に立てる**）。
 *
 * <p>二度押しをその場で止めるため、`await` より**前**に立てる。`finishing`（ref）は画面の
 * ボタンを止めるだけで、同じハンドラが 2 回呼ばれると `await` の先で両方とも進んでしまう。</p>
 */
let finalizeRunning = false
/** 収尾の失敗の理由（日本語。空なら失敗していない）。 */
const finalizeError = ref('')
/** 止まった段（**失敗したときに、どの段で止まったか**を出す。{@link stopFinalize} が入れる）。 */
const finalizeFailedStage = ref<FinishPhase>('idle')

/** 新しい録音のために収尾の進み具合を戻す（前の録音の「済んだ段」を引きずらない）。 */
function resetFinalize(): void {
  finalizeProgress = { recorderFlushed: false, recordEnd: null, noteStarted: false }
  /*
   * **書き起こしの収尾の「済んだ」も戻す**。残したままだと、続きの録音を締めるときに
   * 「もう収尾は済んでいる」と見て `/finish` を送らず、その録音の尾部の文が丸ごと残らない
   * （音源ごとの送信は作り直されるので、収尾も改めて必要になる）。
   */
  sttFinalized = false
  sttFinishing = null
  finalizeError.value = ''
  finalizeFailedStage.value = 'idle'
  finishPhase.value = 'idle'
}

/**
 * 段階の表示（**授業を終える**とき）。
 *
 * <p>「残りの音声を送っています」と「書き起こしの最終結果を待っています」は**別の段階**:
 * 前は分塊（再生用の音声）の送り残し、後ろは音源ごとの認識の尾部（最後の確定文）。
 * `transcribing` には**記録の終了（データベースへの保存）**も含める（保存が済むまで終わりではない）。</p>
 */
const FINISH_PHASE_LABELS: Record<FinishPhase, string> = {
  idle: '',
  stopping: '録音を停止しています…（最後の分塊を受け取っています）',
  'sending-tail': '残りの音声を送っています…（音声は保存されます）',
  transcribing: '書き起こしの最終結果を待って、記録を終えています…（遅れて届いた文も画面に出ます）',
  note: '最終まとめを作成しています…',
  done: '終了しました。詳細画面へ進みます…',
  failed: '終了処理が途中で止まりました。【続きをやり直す】を押すと、続きからやり直します。'
}

/** 段階の表示（**録音を停止するだけ**のとき。授業はまだ終えない）。 */
const STOP_PHASE_LABELS: Record<FinishPhase, string> = {
  idle: '',
  stopping: FINISH_PHASE_LABELS.stopping,
  'sending-tail': FINISH_PHASE_LABELS['sending-tail'],
  transcribing: FINISH_PHASE_LABELS.transcribing,
  note: '書き起こしの最終結果を保存しています…',
  done: '録音を停止しました（書き起こしの最後まで受け取りました）。',
  failed: '収尾（音声の送信・書き起こしの確定）が途中で止まりました。'
    + '【続きをやり直す】を押すと、続きからやり直せます。'
}

/** 段階の表示文（空なら出さない）。 */
const finishPhaseLabel = computed(() => (
  finalizeKind.value === 'stop' ? STOP_PHASE_LABELS : FINISH_PHASE_LABELS)[finishPhase.value]
)

/**
 * 止まった**段**の名前（日本語。空なら段を出さない）。
 *
 * <p>収尾は「録音の停止 → 音声の送信 → 書き起こしの確定 → 記録の終了 → 最終まとめ」と段が
 * 分かれている。理由（後端の日本語）だけを出しても**どの段をやり直せばよいか**が分からない
 * （音を送り直すのか、書き起こしを締め直すのかで、利用者のすることが違う）ので、
 * 止まった段を名前で出す。【続きをやり直す】はこの段から続ける。</p>
 *
 * <p>`transcribing` には**記録の終了（データベースへの保存）**も含める
 * （{@link FINISH_PHASE_LABELS} と同じ扱い）ので、名前も両方を書く。</p>
 */
const FINALIZE_STAGE_LABELS: Partial<Record<FinishPhase, string>> = {
  stopping: '録音の停止',
  'sending-tail': '音声の送信',
  transcribing: '書き起こしの確定と記録の終了',
  note: '最終まとめの作成'
}

/** 止まった段（日本語。空なら失敗していないか、段が分からない）。 */
const finalizeStageLabel = computed(() => FINALIZE_STAGE_LABELS[finalizeFailedStage.value] ?? '')

/** 【授業を終了】のボタンの文言（停止の収尾が走っているあいだを「終了中」と言わない）。 */
const finishButtonLabel = computed(() => {
  if (!finishing.value) return '授業を終了'
  return finalizeKind.value === 'stop' ? '停止中...' : '終了中...'
})

/** 走っている書き起こしの収尾（やり直しで**二重に締めない**）。 */
let sttFinishing: Promise<string | null> | null = null
/** **両方の音源**の書き起こしの最終結果を受け取れたか。 */
let sttFinalized = false

/**
 * 音源ごとの収尾（残りを送り切る → `/finish`）を 1 回だけ走らせる。
 *
 * <p>2 回呼ばれても走るのは 1 回（走っているあいだは同じ約束を返す）。**失敗したらやり直せる**
 * （約束を捨てるので、次の呼び出しでもう一度だけ走る）。戻り値は失敗の理由（**成功なら null**）。</p>
 */
function finishSttOnce(): Promise<string | null> {
  if (sttFinalized) return Promise.resolve(null)
  if (sttFinishing !== null) return sttFinishing
  sttFinishing = runSttFinish()
  return sttFinishing
}

/** 書き起こしの収尾の本体（{@link finishSttOnce} から 1 回だけ呼ばれる）。 */
async function runSttFinish(): Promise<string | null> {
  try {
    /*
     * **画面が認識した最後の発話を、サーバーへ入れ終えるまで待つ**（ブラウザ認識のとき）。
     *
     * <p>認識の結果は 1 件ずつ順に送っている（`transcriptChain`）。送信中の 1 件を待たずに
     * 記録を終えると、サーバーは録音中でないとして断り、**その発話が丸ごと残らない**
     * （尾部の文が最終まとめにも入らない）。送信そのものの失敗は案内（`syncError`）に出す
     * だけで、ここでは投げない（送れなかった文を送り直す手段が無いので、収尾を止めても
     * 直らない）。</p>
     */
    await transcriptChain.catch(() => undefined)
    await finishSourceStreams()
    /*
     * **`finish()` は失敗しても投げない**（後端は HTTP 200 で理由を返し、常時接続は `finished` に
     * 理由を載せて返す）。投げなかったことを成功の印にすると、尾部を取り切れていないのに
     * 「終わった」と言ってしまい、その文が最終まとめに入らない。**音源ごとに理由を見る**。
     */
    const failures = SOURCES.map((source) => {
      const reason = sourceStreams[source]?.finishFailure() ?? ''
      return reason === '' ? '' : `【${SOURCE_LABELS[source]}】${reason}`
    }).filter((message) => message !== '')
    if (failures.length > 0) {
      return `書き起こしの収尾（最後の確定文の取り込み）が終わりませんでした。${failures.join(' ')}`
    }
    sttFinalized = true
    // 済んだので、音源の取り出しと送信を片付ける（**失敗のときは残す**＝やり直せるように）
    releaseSourceCaptures()
    return null
  } catch (cause) {
    return '書き起こしの収尾に失敗しました（'
      + `${cause instanceof Error ? cause.message : String(cause)}）。`
  } finally {
    sttFinishing = null
  }
}

/** 音源ごとの PCM 取り出しと送信を片付ける（画面を離れるとき・録音を止めたとき）。 */
function releaseSourceCaptures(): void {
  for (const source of SOURCES) {
    const capture = captures[source]
    if (capture === undefined) continue
    capture.node.port.close()
    capture.node.disconnect()
    capture.streamSource.disconnect()
    void capture.context.close().catch(() => undefined)
    delete captures[source]
  }
  sourceStreams.mic = undefined
  sourceStreams.shared = undefined
}

/**
 * 混ぜた音から 16kHz PCM を並行して作る（**分塊ごとの STT** のときだけ）。
 *
 * <p>ストリーミング（阿里巴巴）では音源ごとの取り出しを使うので、ここは動かさない。</p>
 */
async function startMixedPcmCapture(source: MediaStream, tap: AudioNode | null = null): Promise<void> {
  if (browserStt.value || streamStt.value) return
  pcmNotice.value = ''
  const AudioContextClass = window.AudioContext
    ?? (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext
  // 混ぜた音から取るときは、**混ぜているのと同じコンテキスト**を使う
  // （別のコンテキストの節点へは connect できない＝黙って取れなくなる）
  const borrowed = tap !== null
  const context: AudioContext | null = borrowed
    ? ((tap.context as AudioContext | undefined) ?? null)
    : (AudioContextClass === undefined ? null : new AudioContextClass())
  if (context === null) {
    pcmNotice.value = 'このブラウザは書き起こし用の音声を取り出せません（Chrome / Edge をお使いください）。'
    return
  }
  try {
    if (context.audioWorklet === undefined) {
      if (!borrowed) void context.close().catch(() => undefined)
      pcmNotice.value = 'このブラウザは書き起こし用の音声を取り出せません（Chrome / Edge をお使いください）。'
      return
    }
    const moduleUrl = URL.createObjectURL(
      new Blob([PCM_CAPTURE_WORKLET_SOURCE], { type: 'application/javascript' }))
    try {
      await context.audioWorklet.addModule(moduleUrl)
    } finally {
      URL.revokeObjectURL(moduleUrl)
    }
    const sourceNode = tap === null ? context.createMediaStreamSource(source) : null
    const worklet = new AudioWorkletNode(context, PCM_CAPTURE_PROCESSOR)
    worklet.port.onmessage = (event: MessageEvent<Float32Array>) => {
      pcmBuffer.append(event.data)
    }
    const silent = context.createGain()
    silent.gain.value = 0
    if (sourceNode === null) {
      tap?.connect(worklet)
    } else {
      sourceNode.connect(worklet)
    }
    worklet.connect(silent)
    silent.connect(context.destination)
    pcmContext = context
    pcmSource = sourceNode
    pcmNode = worklet
    pcmBuffer.clear()
  } catch {
    if (!borrowed) void context.close().catch(() => undefined)
    pcmBuffer.clear()
    pcmNotice.value = '書き起こし用の音声を準備できませんでした。'
      + '書き起こしが一部だけになることがあります（ページを再読み込みしてお試しください）。'
  }
}

/** 書き起こし用の PCM を取り出す（分塊の区切りで呼ぶ。貯まっていなければ null）。 */
function takeSttPcm(): Blob | null {
  // ストリーミング書き起こし中は分塊ごとの PCM を付けない（サーバーも分塊 STT をしない）
  if (streamStt.value) return null
  const rate = pcmContext?.sampleRate ?? 0
  if (rate <= 0 || pcmBuffer.sampleCount === 0) return null
  const samples = pcmBuffer.take(rate)
  if (samples.length === 0) return null
  return new Blob([samples], { type: PCM_CHUNK_MIME })
}

/** 書き起こし用の PCM を止める（分塊ごとの STT のときだけ使う）。 */
function stopMixedPcmCapture(): void {
  pcmBuffer.clear()
  pcmNode?.port.close()
  pcmNode?.disconnect()
  pcmSource?.disconnect()
  const context = pcmContext
  pcmContext = null
  pcmSource = null
  pcmNode = null
  // 混ぜているコンテキストは**借りているだけ**（片付けは releaseAudioSources が行う）
  if (context !== null && context !== mixContext) void context.close().catch(() => undefined)
}

/** ブラウザの録音を始める（使えない環境では案内だけ出す）。 */
async function startRecorder(): Promise<boolean> {
  recorderNotice.value = ''
  if (typeof MediaRecorder === 'undefined' || navigator.mediaDevices?.getUserMedia === undefined) {
    recorderNotice.value = 'このブラウザでは音声を録音できません（書き起こしの取得だけを行います）。'
    // 音は残せないが、ブラウザ認識の書き起こしは続けられる（今までどおり）
    return true
  }
  try {
    // 【新しい授業】で選んだ音源を用意する（二音源なら**ここで共有の選択が出る**）
    const prepared = await prepareAudioSources()
    if (!prepared) return false
    const recordStream = recordStreamOf()
    if (recordStream === null) return false
    /*
     * 書き起こし用の音声（サーバー側 STT のときだけ）。
     * - ストリーミング（阿里巴巴）… **音源ごと**に取り出して別々に送る（混ぜる前に認識する）
     * - 分塊ごとの STT（google など）… 今までどおり混ぜた音から取る（分塊に付けて送る）
     */
    if (streamStt.value) {
      await startSourceStreamsForRecorder()
    } else {
      await startMixedPcmCapture(recordStream, audioGraph?.mixed ?? null)
    }
    const mime = MediaRecorder.isTypeSupported('audio/webm') ? 'audio/webm' : ''
    recorder = mime === ''
      ? new MediaRecorder(recordStream)
      : new MediaRecorder(recordStream, { mimeType: mime })
    recorder.ondataavailable = (event: BlobEvent) => {
      // 分塊の区切りで PCM も取り出す（同じ間隔の音声を認識へ送る）
      const sttPcm = takeSttPcm()
      if (event.data.size > 0) sendChunk(event.data, sttPcm)
    }
    // 分塊の長さごとに 1 つのファイルが届く（STT の 1 リクエスト＝1 分塊）
    recorderStartedAtMs = Date.now()
    lastChunkEndSeconds = 0
    recorder.start(Math.max(5, chunkSeconds.value) * 1000)
    // 話しながら文字を出す（阿里巴巴のリアルタイム認識のときだけ）
    startSourceStreams()
    return true
  } catch (cause) {
    recorderNotice.value = cause instanceof Error && cause.name === 'NotAllowedError'
      ? 'マイクの使用が許可されませんでした（書き起こしの取得だけを行います）。'
      : 'このブラウザでは音声を録音できません（書き起こしの取得だけを行います）。'
    // ここも今までどおり続ける（書き起こしはブラウザ側で取れる）
    return !mixing
  }
}

/**
 * 共有を選び直す（共有が終わった・音が入っていなかったとき）。
 *
 * <p>録音は止めずに、混ぜている遠隔音だけを入れ替える（マイクの声は続けて録れる）。</p>
 */
async function reshareDisplayAudio(): Promise<void> {
  recorderNotice.value = ''
  const shared = await requestDisplayAudio()
  if (shared === null) return
  if (displayStream !== null) {
    displayStream.getTracks().forEach((track) => track.stop())
  }
  displayStream = shared
  watchSharedAudio(shared)
  if (audioGraph === null) return
  audioGraph.replaceRemote(shared)
  recorderNotice.value = '共有の音を入れ替えました。'
}

/**
 * 録音に使う音源ごとに、PCM の取り出しと送信キューを用意する。
 *
 * <p>マイクは常に 1 本、共有の音は二音源のときだけ 1 本。**混ぜる前**に取るので、
 * 先生（共有）と学生（マイク）を別々に認識できる。</p>
 */
async function startSourceStreamsForRecorder(): Promise<void> {
  const id = recordId.value
  const build = (source: ClassroomSource): SourceStream => new SourceStream({
    source,
    recordId: () => recordId.value ?? id,
    onSegments: (added) => appendSegments(added, source),
    onInterim: (text) => { interimOf[source].value = text },
    onNotice: (message) => { noticeOf[source].value = message },
    onMissingRange: (range) => { missingOf[source].value = [...missingOf[source].value, range] },
    maxSendSeconds: 2,
    maxBufferSeconds: 60
  })
  interimOf.mic.value = ''
  interimOf.shared.value = ''
  noticeOf.mic.value = ''
  noticeOf.shared.value = ''
  missingOf.mic.value = []
  missingOf.shared.value = []
  /*
   * 送信キューを作る音源は**設定**（【新しい授業】で選んだ音源）で決める。
   * 「共有の音が届いたかどうか」で決めない（届かなかった回でも二音源の設定なら、
   * マイクの文は「学生」として扱う＝話者の決め方がぶれない）。
   */
  for (const source of classroomSourcesOf(audioMode)) {
    sourceStreams[source] = build(source)
  }
  if (stream !== null) await startSourceCapture('mic', stream)
  const shared = displayStream
  if (shared !== null && sourceStreams.shared !== undefined) {
    await startSourceCapture('shared', shared)
  }
}

/** ブラウザの録音を止める（最後の分塊は `ondataavailable` が送る。送信は待たない）。 */
function stopRecorder(): void {
  stopBrowserRecognition()
  stopSourceStreams()
  stopMixedPcmCapture()
  const current = recorder
  recorder = null
  if (current !== null && current.state !== 'inactive') {
    current.stop()
  }
  if (stream !== null) {
    stream.getTracks().forEach((track) => track.stop())
    stream = null
  }
}

/** `stop` イベントを待つ上限（ミリ秒）。来ない実装でも先へ進む。 */
const RECORDER_STOP_WAIT_MS = 3_000

/**
 * 録音を止め、**最後の分塊が届くまで待つ**（収尾の 1 段目）。
 *
 * <p>`MediaRecorder` の最後の分塊は `stop()` のあとに `ondataavailable` で届く。待たずに【終了】を送ると、
 * サーバーが先に録音を終わらせてしまい、あとから届いた分塊が **409（録音中ではありません）** で
 * 拒否されて**その回の音声が丸ごと残らない**（実測: 20 秒未満の録音は音声 0 バイト・
 * 24 秒の録音も最初の分塊だけだった）。</p>
 *
 * <p>音源（マイク・共有・混ぜる仕組み）はここで手放す＝**これ以上 `SourceStream` へ音が入らない**
 * （送るのは既に貯まっている分だけになる）。</p>
 */
async function stopRecorderOnce(): Promise<void> {
  stopBrowserRecognition()
  const current = recorder
  recorder = null
  if (current !== null && current.state !== 'inactive') {
    await new Promise<void>((resolve) => {
      // 「最後の分塊（dataavailable）→ stop」の順に来るので、stop を待てば送信は始まっている
      current.addEventListener('stop', () => resolve(), { once: true })
      current.stop()
      window.setTimeout(resolve, RECORDER_STOP_WAIT_MS)
    })
  }
  // 書き起こし用の PCM は、最後の分塊を取り出した**あと**に止める（最後の分塊にも付けるため）
  stopMixedPcmCapture()
  releaseAudioSources()
}

/**
 * 送信待ちの分塊（最後の分塊を含む）を**送り切る**（収尾の 2 段目）。
 *
 * <p>送り残しがあっても黙って終わらせない（呼び出し側が失敗として止める）。</p>
 */
async function flushUploadsOnce(): Promise<void> {
  // 送信中の分塊（最後の分塊を含む）が終わるまで待つ（失敗しても理由は下で見る）
  await uploadChain.catch(() => undefined)
  // 送り残しをもう一度試す（ネットワークが戻っていれば送れる）
  await flushPendingChunks()
  const left = pendingChunks.value.length
  if (left > 0) {
    recorderNotice.value = `送れなかった音声が ${left} 件あります。`
      + '電波の良い所で【送り直す】を押してください（押すまで消えません）。'
  }
}

/* ---------- ポーリング ---------- */

/** 追記セグメントを取る（ポーリングの 1 周期）。 */
async function pullSegments(): Promise<void> {
  const id = recordId.value
  if (id === null) return
  try {
    const response = await fetchClassroomSegments(id, nextSeq.value)
    syncError.value = ''
    appendSegments(response.data.items)
    nextSeq.value = Math.max(nextSeq.value, response.data.nextSeq)
  } catch (caught) {
    syncError.value = messageOf(caught, '書き起こしを取得できませんでした。')
  }
}

/** 詳細（ノートの生成状態・最終まとめ）を取る。 */
async function refreshDetail(): Promise<void> {
  const id = recordId.value
  if (id === null) return
  try {
    const response = await fetchClassroomRecord(id)
    detail.value = response.data
    notes.value = response.data.notes
    appendSegments(response.data.segments)
    nextSeq.value = response.data.segments.reduce((max, segment) => Math.max(max, segment.seq), nextSeq.value)
    if (!recording.value && response.data.status === 'COMPLETED') {
      stopPolling()
    }
  } catch (caught) {
    syncError.value = messageOf(caught, 'ノートの状態を確認できませんでした。')
  }
}

/** ノートがまだ生成中か（生成中なら詳細を取り続ける）。 */
const hasPendingNote = computed(() => notes.value.some(
  (note) => note.status === 'PENDING' || note.status === 'GENERATING'
))

let pollTimer: number | null = null
/** ポーリングの周期番号（詳細は数周期に 1 回だけ取る）。 */
let pollTicks = 0
/** ポーリングの間隔（AI 生図と同じ 2 秒）。 */
const POLL_INTERVAL_MS = 2000
/**
 * 詳細（ノートの生成状態）を取りに行く間隔（周期の数）。
 * 段階ノートは分塊の応答で作られるが、**別のタブ・再読込・バッチ（batC61 の拾い直し）**でも
 * 作られるため、録音中は定期的に詳細も見る（書き起こしは毎周期）。
 */
const DETAIL_POLL_TICKS = 5

function stopPolling(): void {
  if (pollTimer !== null) {
    window.clearInterval(pollTimer)
    pollTimer = null
  }
  pollTicks = 0
}

/** 2 秒間隔で書き起こしとノートを取りに行く。 */
function startPolling(): void {
  if (pollTimer !== null) return
  pollTimer = window.setInterval(() => {
    if (recording.value) void pullSegments()
    pollTicks += 1
    // 生成中のノートがあれば毎周期、無くても録音中は数周期ごとに詳細を見る
    if (hasPendingNote.value || (recording.value && pollTicks % DETAIL_POLL_TICKS === 0)) {
      void refreshDetail()
    }
  }, POLL_INTERVAL_MS)
}

/* ---------- 録音の開始・停止・終了 ---------- */

function startTimer(): void {
  if (timer !== null) return
  timer = window.setInterval(() => {
    elapsedSeconds.value += 1
    if (limitReached.value) stopRecording()
  }, 1000)
}

function stopTimer(): void {
  if (timer !== null) {
    window.clearInterval(timer)
    timer = null
  }
}

/** 録音を開始する（サーバーに開始を知らせてから、ブラウザの録音を始める）。 */
async function startRecording(): Promise<void> {
  // 収尾が走っているあいだは始めない（収尾の状態を作り直すと、費やした段が無駄になる）
  if (recording.value || starting.value || finalizeRunning) return
  actionError.value = ''
  const id = recordId.value
  if (id === null) {
    actionError.value = 'この画面は授業の記録と結び付いていません（一覧から開き直してください）。'
    toast.danger(actionError.value)
    return
  }
  if (options.value !== null && !options.value.enabled) {
    actionError.value = options.value.notice === ''
      ? '授業録音は現在ご利用いただけません。' : options.value.notice
    toast.danger(actionError.value)
    return
  }
  starting.value = true
  try {
    const response = await startClassroomRecord(id)
    if (detail.value !== null) {
      detail.value = { ...detail.value, status: response.data.status, statusLabel: response.data.statusLabel }
    }
    recording.value = true
    resumeNotice.value = ''
    elapsedSeconds.value = 0
    /*
     * **この記録の音源**を確定して残す（利用者の指示 7 節）。
     *
     * 話者は「この記録がどの音源で録られたか」で決まる（二音源＝マイク学生・共有先生／
     * 単一音源＝講義）。詳細画面は query ではなくこの値を読むので、録音を始めた時点＝
     * **実際に録音した音源**をここで残す（開き直し・別の端末・あとから一覧で開いたときも
     * 同じ話者になる）。
     */
    configuredAudioMode.value = audioMode
    saveRecordAudioMode(id, audioMode)
    // 新しい録音では収尾の進み具合も戻す（前の録音の「済んだ段」を引きずらない）
    resetFinalize()
    // すでに送った分塊がある（開き直し）ときは続きの番号から送る
    chunkSeq = maxKnownSeq.value
    startTimer()
    startPolling()
    // 書き起こしをブラウザで行う設定なら、録音と同時に認識を始める（音声は分塊で保存し続ける）
    if (browserStt.value) startBrowserRecognition()
    // 音源が用意できなければ**録音中にしない**（共有の音が無いのに「録音中」と見せない）
    const recorderStarted = await startRecorder()
    if (!recorderStarted) {
      recording.value = false
      stopTimer()
      stopPolling()
      stopBrowserRecognition()
      return
    }
  } catch (caught) {
    actionError.value = messageOf(caught, '録音を開始できませんでした。')
    toast.danger(actionError.value)
  } finally {
    starting.value = false
  }
}

/** 録音を停止する（授業はまだ終えない。収尾は【授業を終了】と**同じ 1 つ**の処理を通る）。 */
function stopRecording(): void {
  if (!recording.value) return
  requestFinalize('stop')
}

/** 授業を終了して詳細（最終整理結果）へ進む。 */
function finishLesson(): void {
  requestFinalize('finish')
}

/**
 * 収尾を頼む（**「録音を停止」と「授業を終了」の入口はここ 1 つ**）。
 *
 * <p>走っているあいだの 2 回目は**その場で捨てる**（二度押しで 2 つ走らせない）。
 * ただし【授業を終了】だけは格上げする: 停止だけの収尾が走っている最中に終了を押したら、
 * 押した操作を黙って捨てず、その収尾の続きとして記録の終了まで進める。</p>
 */
function requestFinalize(kind: FinalizeKind): void {
  if (finalizeRunning) {
    if (kind === 'finish') finalizeKind.value = 'finish'
    return
  }
  void runFinalize(kind)
}

/**
 * 収尾の本体（**「録音を停止」と「授業を終了」で同じ 1 つ**の処理。冪等）。
 *
 * <p>順番は固定し、前の段が済むまで次へ進まない:</p>
 * <ol>
 *   <li>録音を止めて、**最後の分塊（再生用の音声）を送り切る**（残っていれば失敗として止める）</li>
 *   <li>**両方の音源**の書き起こしの最終結果（尾部の確定文）を待つ</li>
 *   <li>授業を終えるときだけ、記録の終了（**データベースへの保存**）を待つ</li>
 *   <li>ここまで**全部済んでから**、最終まとめ（batC62）を起動して「終了しました」と言う</li>
 * </ol>
 *
 * <p>失敗したら**成功と言わない**（詳細へ進まない・最終まとめを起動しない・欠落の記録を消さない）。
 * 済んだ段は覚えているので、やり直し（{@link retryFinalize}）は**まだ済んでいない段だけ**を実行する。</p>
 */
async function runFinalize(kind: FinalizeKind): Promise<void> {
  // 二度押しはその場で止める（`await` より前に立てる）
  if (finalizeRunning) return
  finalizeRunning = true
  finishing.value = true
  finalizeKind.value = kind
  finalizeError.value = ''
  finishNotice.value = ''
  try {
    // 1) 録音を止める → 最後の分塊を送り切る
    if (!finalizeProgress.recorderFlushed) {
      finishPhase.value = 'stopping'
      recording.value = false
      stopTimer()
      await stopRecorderOnce()
      finishPhase.value = 'sending-tail'
      await flushUploadsOnce()
      const left = pendingChunks.value.length
      if (left > 0) {
        stopFinalize(`送れなかった音声が ${left} 件あります（尾句の文が残らないことがあります）。`)
        return
      }
      finalizeProgress.recorderFlushed = true
    }
    // 2) 両方の音源の書き起こしの最終結果（尾部の確定文）を待つ
    finishPhase.value = 'transcribing'
    const sttReason = await finishSttOnce()
    if (sttReason !== null) {
      stopFinalize(sttReason)
      return
    }
    if (finalizeKind.value === 'stop') {
      finishPhase.value = 'done'
      return
    }
    const id = recordId.value
    if (id === null) {
      // API の記録と結び付いていない（画面の流れだけ）ときは、そのまま詳細へ進む
      finishPhase.value = 'done'
      backToDetail()
      return
    }
    // 3) 記録の終了（データベースへの保存）。済んでいれば送り直さない
    let outcome = finalizeProgress.recordEnd
    if (outcome === null) {
      outcome = await endRecord(id)
      // 失敗は覚えない（やり直しでもう一度だけ送る）
      if (outcome.kind !== 'failed') finalizeProgress.recordEnd = outcome
    }
    if (outcome.kind === 'failed') return // 理由は endRecord（stopFinalize）が入れている
    if (outcome.kind === 'already') {
      finishPhase.value = 'done'
      backToDetail()
      return
    }
    // 4) **保存が済んでから**最終まとめを起動する（先に作ると尾部の文がまとめに入らない）
    const runPath = outcome.result.runPath
    if (runPath === null || runPath === '') {
      // 書き起こしが 1 件も無いとき（音声が送られなかった）は最終まとめを作らない。
      // サーバーの理由（日本語）をそのまま出す（admin-api の入口は呼ばない）
      finishNotice.value = outcome.result.notice ?? '書き起こしが無かったため、最終まとめは作成しませんでした。'
      finishPhase.value = 'done'
      toast.warning(finishNotice.value)
      backToDetail()
      return
    }
    if (!finalizeProgress.noteStarted) {
      finalizeProgress.noteStarted = true
      finishPhase.value = 'note'
      // 最終まとめ（batC62）を起動する。結果は詳細画面のポーリングで出す
      void runClassroomNote(runPath, 'classroom-live-view').catch((cause: unknown) => {
        syncError.value = messageOf(cause, '最終まとめの生成を開始できませんでした。')
      })
    }
    finishPhase.value = 'done'
    toast.success('録音を終了しました。')
    backToDetail()
  } catch (cause) {
    stopFinalize(messageOf(cause, '終了処理が途中で止まりました。'))
  } finally {
    finishing.value = false
    finalizeRunning = false
  }
}

/**
 * 収尾のやり直し（**まだ済んでいない段だけ**をもう一度走らせる）。何回失敗しても押せる。
 *
 * <p>押すたびに `requestFinalize` を通すので、二度押しで 2 つ走ることはない。</p>
 */
function retryFinalize(): void {
  requestFinalize(finalizeKind.value)
}

/** 収尾を失敗として止める（**成功と言わない**・最終まとめを起動しない・欠落の記録は消さない）。 */
function stopFinalize(reason: string): void {
  /*
   * **止まった段を残す**。理由（後端の日本語）だけでは、どの段をやり直せばよいか分からない
   * （音を送り直すのか、書き起こしを締め直すのか）。いまの段＝止まった段なので、ここで写す。
   */
  finalizeFailedStage.value = finishPhase.value
  finishPhase.value = 'failed'
  finalizeError.value = reason
  toast.danger(reason)
}

/**
 * 記録の終了（データベースへの保存）を送る（**HTTP 200 でも `data.error` なら成功ではない**）。
 *
 * <p>後端は収尾を取り切れなかった・保存できなかったときに理由を入れて返す。理由を無視して
 * 「終わった」と言うと、尾部の文が最終まとめに入らない（画面からは成功に見える静かな取りこぼし）。</p>
 */
async function endRecord(id: number): Promise<EndOutcome> {
  try {
    const response = await endClassroomRecord(id)
    const reason = response.data.error
    if (typeof reason === 'string' && reason !== '') {
      stopFinalize(`終了処理を完了できませんでした（${reason}）。`)
      return { kind: 'failed' }
    }
    return { kind: 'ended', result: response.data }
  } catch (caught) {
    // すでに終わっている（別のタブで終了した・二度押し・再読み込みのあと）ときは、
    // サーバーは 409（録音中ではありません）を返す。**エラーにせず詳細へ進む**
    if (await alreadyEnded(id)) {
      finishNotice.value = 'この授業はすでに終了しています。詳細を開きます。'
      toast.info(finishNotice.value)
      return { kind: 'already' }
    }
    stopFinalize(messageOf(caught, '授業を終了できませんでした。'))
    return { kind: 'failed' }
  }
}

/**
 * すでに終わっている授業か（別のタブで終了・二度押し・再読み込みのあと）。
 *
 * <p>`/end` は録音中でないと 409 を返すので、**状態を確かめてから**詳細へ進む
 * （実測: 終了は成功していたのに、画面の読み込みに失敗して詳細へ進めず、
 * もう一度押すと 409 の理由だけが出て操作できなくなった）。</p>
 */
async function alreadyEnded(id: number): Promise<boolean> {
  try {
    const response = await fetchClassroomRecord(id)
    return response.data.status !== 'RECORDING'
  } catch {
    // 状態が取れないときは今までどおりエラーとして出す
    return false
  }
}

function backToDetail(): void {
  void router.push({
    path: `/${area.value}/classroom/${String(route.params.id ?? 'local')}`,
    query: { ...route.query }
  })
}

function backToList(): void {
  stopRecording()
  void router.push({ path: `/${area.value}/classroom` })
}

/* ---------- 画面の表示 ---------- */

/** ノートを新しい順に（フェーズは上へ追記される）。 */
const orderedNotes = computed(() => [...notes.value].sort((left, right) => right.noteId - left.noteId))

/** 「新しい授業」の dialog から来た（開いたら自動で録音を始める）。 */
let autoStartRequested = false

onMounted(async () => {
  // 「新しい授業」の dialog から来たときは、開いたら**そのまま録音を始める**（利用者の指示）
  if (route.query[CLASSROOM_QUERY.autostart] === '1') {
    autoStartRequested = true
  }
  try {
    options.value = (await fetchClassroomOptions()).data
  } catch {
    // 設定が読めないときは既定値（分塊 20 秒・最大 120 分）で動かす
    options.value = null
  }
  const id = recordId.value
  if (id === null) return
  await refreshDetail()
  // **この画面は録音を始めない**（マイクは利用者が【録音を開始】を押したときだけ使う）。
  // 記録は作成時点で状態=RECORDING なので、サーバーの状態だけで「録音中」にすると
  // MediaRecorder が動かないまま録音したつもりになる（音声が 1 件も送られない）。
  // 途中（開始時刻あり）の記録を開き直したときは、続きから数えられるように経過時間だけ戻す。
  if (detail.value?.status === 'RECORDING' && detail.value.startTime !== null) {
    const startedAt = Date.parse(detail.value.startTime)
    elapsedSeconds.value = Number.isFinite(startedAt)
      ? Math.max(0, Math.floor((Date.now() - startedAt) / 1000)) : 0
    resumeNotice.value = '録音の途中です（この画面を開き直したため、いったん停止しています）。'
      + '続きを録音するには【録音を開始】を押してください。'
  }
  await pullSegments()
  // 音声機器が変わった（マイクを抜いた・差した）ときは知らせる（録音は止めない）
  if (navigator.mediaDevices !== undefined) {
    const media = navigator.mediaDevices as MediaDevices & { ondevicechange?: () => void }
    media.ondevicechange = () => {
      recorderNotice.value = '音声の機器が変わりました。音が入っているか、音量表示で確かめてください。'
        + '（必要なら録音を止めて選び直してください）'
    }
  }
  // 自動開始（dialog から来たとき）。マイクの許可はこの操作の中で求める
  if (autoStartRequested && !recording.value && !browserStt.value) {
    autoStartRequested = false
    await startRecording()
  } else if (autoStartRequested) {
    // ブラウザ認識でも同じ（画面が認識するのでサーバーは STT を呼ばない）
    autoStartRequested = false
    await startRecording()
  }
})

onBeforeUnmount(() => {
  stopTimer()
  stopPolling()
  stopSourceStreams()
  releaseSourceCaptures()
  stopMixedPcmCapture()
  // 常時接続も閉じる（音源ごと）
  for (const source of SOURCES) sourceStreams[source]?.closeSocket()
  // 用意した音源（マイク・共有・混ぜる仕組み）は掴んだままにしない
  releaseAudioSources()
  stopRecorder()
})
</script>

<template>
  <div class="cr-page" data-cr-live>
    <p class="cr-notice">
      <AppIcon name="info" size="sm" />
      <span>
        録音中の画面です。左にリアルタイムの書き起こし、右に AI 授業ノートが出ます。
        録音はブラウザで行い、分塊ごとにサーバーへ送って書き起こし（STT）を作ります
        （AI ノートはまとまりごとに段階的に更新します）。
      </span>
    </p>

    <section class="card">
      <div class="card__header cr-list-head">
        <h2 class="card__title"><AppIcon name="mic" size="sm" /> {{ lessonName }}</h2>
        <span class="badge badge--neutral">{{ subject }}</span>
        <div class="cr-list-head__spacer"></div>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--secondary" data-cr-back-list @click="backToList">
            <AppIcon name="chevron-left" size="sm" /> 一覧へ戻る
          </button>
        </div>
      </div>

      <!-- 録音中指示・経過時間・言語モード・前置詞 -->
      <div class="cr-live__top">
        <span class="cr-live__status" data-cr-recording-status>
          <span class="cr-recording-dot" :class="{ 'is-recording': recording }" aria-hidden="true"></span>
          <span class="badge" :class="recording ? 'badge--danger' : 'badge--neutral'">
            {{ recording ? '録音中' : '停止中' }}
          </span>
        </span>
        <span class="cr-timer" data-cr-timer>{{ elapsedLabel }}</span>
        <span class="cr-live__meta">
          <span class="cr-live__meta-label">言語モード</span>
          <span class="cr-live__meta-value" data-cr-live-language-mode>{{ languageMode }}</span>
        </span>
        <span class="cr-live__meta">
          <span class="cr-live__meta-label">前置詞</span>
          <span class="cr-live__meta-value" data-cr-live-preset>{{ preset }}</span>
        </span>
        <span class="cr-live__meta">
          <span class="cr-live__meta-label">分塊</span>
          <span class="cr-live__meta-value" data-cr-live-chunk>{{ chunkSeconds }} 秒</span>
        </span>
        <span class="cr-live__meta">
          <span class="cr-live__meta-label">書き起こし</span>
          <span class="cr-live__meta-value" data-cr-live-stt-mode>{{ sttModeLabel }}</span>
        </span>
      </div>

      <p v-if="actionError !== ''" class="alert alert--danger" data-cr-live-error>{{ actionError }}</p>
      <!--
        収尾（停止・終了）が途中で止まったとき: **止まった段**と本当の理由、そして**やり直し**を出す
        （成功と言わない）。やり直しは**まだ済んでいない段だけ**を走らせるので、何回失敗しても押せる。
      -->
      <p v-if="finalizeError !== ''" class="alert alert--danger" data-cr-finalize-error>
        <AppIcon name="alert" size="sm" />
        <span v-if="finalizeStageLabel !== ''" data-cr-finalize-stage>【{{ finalizeStageLabel }}】</span>
        <span>{{ finalizeError }}</span>
        <button
          type="button" class="btn btn--secondary btn--sm" :disabled="finishing"
          data-cr-retry-finalize @click="retryFinalize"
        >
          続きをやり直す
        </button>
      </p>
      <p v-if="finishNotice !== ''" class="alert alert--warning" data-cr-finish-notice>{{ finishNotice }}</p>
      <p v-if="resumeNotice !== ''" class="alert alert--info" data-cr-resume-notice>{{ resumeNotice }}</p>
      <p v-if="recorderNotice !== ''" class="alert alert--warning" data-cr-recorder-notice>{{ recorderNotice }}</p>
      <p v-if="browserSttNotice !== ''" class="alert alert--warning" data-cr-browser-stt-notice>{{ browserSttNotice }}</p>
      <p v-if="pcmNotice !== ''" class="alert alert--warning" data-cr-pcm-notice>{{ pcmNotice }}</p>
      <!-- 音源ごとの状態（片方が失敗しても、もう片方は続いていることが分かるように） -->
      <template v-for="source in SOURCES" :key="`notice-${source}`">
        <p v-if="noticeOf[source].value !== ''" class="alert alert--warning" :data-cr-source-notice="source">
          【{{ SOURCE_LABELS[source] }}】{{ noticeOf[source].value }}
        </p>
        <p
          v-if="missingOf[source].value.length > 0" class="alert alert--info"
          :data-cr-source-missing="source"
        >
          【{{ SOURCE_LABELS[source] }}】送れずに飛ばした区間：
          <span v-for="range in missingOf[source].value" :key="`${range.fromSeconds}-${range.toSeconds}`">
            {{ range.fromSeconds }}〜{{ range.toSeconds }} 秒
          </span>
          （録音した音声には全部入っているので、後から補書き起こしできます）
        </p>
      </template>
      <!-- 二音源＋ブラウザ認識: スピーカーの音は文字にならない（黙って半分を見せない） -->
      <p v-if="micOnlyTranscript" class="alert alert--warning" data-cr-mic-only-stt-notice>
        <AppIcon name="alert" size="sm" />
        <span>
          いまの設定はブラウザ音声認識なので、書き起こしは<strong>マイクの音だけ</strong>です
          （スピーカーから出る先生の声は文字になりません。録音した音声には両方入っています）。
          両方を文字にするには、システム設定「授業録音」の STT プロバイダーを
          <strong>サーバー認識</strong>（阿里巴巴 など）にしてください。
        </span>
      </p>
      <p v-if="aiNotesOff" class="alert alert--info" data-cr-ai-notes-off>
        AI 解析（フェーズノート・最終まとめ）は現在オフです。書き起こしだけを行います
        （システム設定「AI 授業記録（授業録音）」で戻せます）。
      </p>
      <p v-if="sttStreamNotice !== ''" class="alert alert--warning" data-cr-live-stt-notice>
        {{ sttStreamNotice }}
      </p>
      <p v-if="syncError !== ''" class="alert alert--warning" data-cr-live-sync-error>{{ syncError }}</p>

      <!-- 二段組：リアルタイム STT 転写 ＋ AI 授業ノート -->
      <div class="cr-live__columns">
        <section class="cr-column" data-cr-transcript>
          <div class="cr-column__head">
            <h3 class="cr-column__title"><AppIcon name="list" size="sm" /> リアルタイム STT 転写</h3>
            <span v-if="micOnlyTranscript" class="badge badge--warning" data-cr-transcript-mic-only>
              マイクのみ
            </span>
            <span class="cr-hint">{{ orderedSegments.length }} 行</span>
          </div>
          <div class="cr-column__body">
            <template v-if="segments.length > 0">
              <p
                v-for="segment in orderedSegments" :key="segment.seq" class="cr-transcript__line"
                :class="SPEAKER_CLASSES[speakerLabelOf(segment)]"
                :data-cr-transcript-line="segment.seq"
                :data-cr-transcript-speaker="speakerLabelOf(segment)"
              >
                <span class="cr-transcript__time">[{{ formatSegmentTime(segment.startOffsetSeconds) }}]</span>
                <span class="cr-transcript__speaker">{{ speakerLabelOf(segment) }}：</span>
                <span class="cr-transcript__text">{{ segment.text }}</span>
              </p>
            </template>
            <p v-else class="cr-column__placeholder">
              録音を開始すると、ここにリアルタイムの書き起こしが表示されます（1 文ずつ、時刻つき）。
              表示例：[10:15:02] 講義：それでは今日の授業を始めます。
              ※ MVP では話者を 1 つ（「講義」）だけにします。話者の欄は将来の「先生／生徒を分ける」
              設定（two_speaker）用に予約しています。
            </p>
            <!-- ブラウザ認識の途中経過（保存は final だけ。ここは見せるだけ） -->
            <p
              v-if="interimText !== ''" class="cr-transcript__line cr-transcript__line--interim"
              data-cr-transcript-interim
            >
              <span class="cr-transcript__time">[…]</span>
              <span class="cr-transcript__text">{{ interimText }}</span>
            </p>
            <!-- サーバー認識の途中経過（**音源ごと**に 1 行ずつ。保存はしない） -->
            <template v-for="source in SOURCES" :key="`interim-${source}`">
              <p
                v-if="interimOf[source].value !== ''"
                class="cr-transcript__line cr-transcript__line--interim"
                :data-cr-source-interim="source"
              >
                <span class="cr-transcript__time">[…]</span>
                <span class="cr-transcript__speaker">{{ SOURCE_LABELS[source] }}：</span>
                <span class="cr-transcript__text">{{ interimOf[source].value }}</span>
              </p>
            </template>
          </div>
        </section>

        <section class="cr-column" data-cr-note>
          <div class="cr-column__head">
            <h3 class="cr-column__title"><AppIcon name="wand" size="sm" /> AI 授業ノート</h3>
            <span class="cr-hint">{{ notes.length }} 件</span>
          </div>
          <div class="cr-column__body">
            <template v-if="orderedNotes.length > 0">
              <article
                v-for="note in orderedNotes" :key="note.noteId" class="cr-detail__block"
                :data-cr-note-item="note.noteId"
              >
                <span class="cr-detail__block-title">
                  <AppIcon name="wand" size="sm" />
                  {{ note.kind === 'FINAL' ? '最終まとめ' : `フェーズ ${note.phaseNo ?? ''}` }}
                  <span class="badge" :class="statusBadgeClass(note.status === 'FAILED' ? 'FAILED' : 'COMPLETED')">
                    {{ note.statusLabel }}
                  </span>
                </span>
                <template v-if="note.errorMessage !== null && note.errorMessage !== ''">
                  <span class="cr-detail__block-body">{{ note.errorMessage }}</span>
                </template>
                <template v-else-if="note.status === 'READY'">
                  <span
                    v-for="section in noteSections(note.noteJson)" :key="section.key"
                    class="cr-note__section" :data-cr-note-section="section.key"
                  >
                    <span class="cr-note__section-title">{{ section.title }}</span>
                    <span class="cr-note__section-body">{{ section.body === '' ? '（なし）' : section.body }}</span>
                  </span>
                </template>
                <span v-else class="cr-detail__block-body">
                  AI がノートを作っています。しばらくすると、ここに出ます。
                </span>
              </article>
            </template>
            <p v-else class="cr-column__placeholder">
              録音を開始すると、AI がノートを「毎文ではなく、まとまりごとに」段階的に更新します。
              以下の 4 つの段落に整理します：
              ・本時のテーマ
              ・学習内容
              ・先生の重点
              ・宿題
            </p>
          </div>
        </section>
      </div>

      <!--
        音源は【新しい授業】で選ぶ（この画面には選択を置かない。利用者の指示）。
        ここに残すのは**録音中の確認**だけ：二つの音が入っているかを目で確かめられるようにする。
      -->
      <!-- 話者の決め方（音源で決まる。モデルが人物を判定するのではない） -->
      <p v-if="mixing" class="cr-hint" data-cr-speaker-note>
        共有した音は<strong>「先生」</strong>、マイクは<strong>「学生」</strong>として書き起こします
        （共有に混じる他の人の声も「先生」扱いです）。イヤホンを使うと、スピーカーの音が
        マイクに入って二重に録れるのを防げます。
      </p>

      <section v-if="mixing || recording" class="cr-source" data-cr-audio-source>
        <div class="cr-levels" data-cr-audio-levels>
          <span class="cr-levels__item">
            マイク
            <span class="cr-levels__bar"><i :style="{ width: `${Math.round(micLevel * 100)}%` }"></i></span>
          </span>
          <span v-if="mixing" class="cr-levels__item">
            網課の音
            <span class="cr-levels__bar">
              <i class="is-remote" :style="{ width: `${Math.round(remoteLevel * 100)}%` }"></i>
            </span>
          </span>
          <span v-if="mixing" class="cr-hint">
            両方の音が入っているか、レベルで確かめてください（共有が切れたら【共有を選び直す】）。
          </span>
          <button
            v-if="mixing && recording" type="button" class="btn btn--secondary btn--sm"
            data-cr-reshare @click="reshareDisplayAudio"
          >
            共有を選び直す
          </button>
        </div>
      </section>

      <!-- 送れなかった音声（黙って消さない） -->
      <p v-if="pendingNotice !== ''" class="alert alert--warning" data-cr-pending-uploads>
        <AppIcon name="alert" size="sm" />
        <span>{{ pendingNotice }}</span>
        <button
          type="button" class="btn btn--secondary btn--sm" data-cr-retry-uploads
          @click="flushPendingChunks"
        >
          送り直す
        </button>
      </p>

      <!-- 録音開始・停止 ／ 授業を終了 -->
      <div class="cr-live__foot">
        <button
          v-if="!recording" type="button" class="btn btn--primary" :disabled="starting || finishing"
          data-cr-start-recording
          @click="startRecording"
        >
          <AppIcon name="mic" size="sm" /> {{ starting ? '開始中...' : '録音を開始' }}
        </button>
        <button
          v-else type="button" class="btn btn--secondary" data-cr-stop-recording @click="stopRecording"
        >
          <AppIcon name="stop" size="sm" /> 録音を停止
        </button>
        <button
          type="button" class="btn btn--danger" :disabled="finishing" data-cr-finish @click="finishLesson"
        >
          <AppIcon name="check" size="sm" /> {{ finishButtonLabel }}
        </button>
        <span v-if="finishPhaseLabel !== ''" class="cr-hint" data-cr-finish-phase>{{ finishPhaseLabel }}</span>
        <span v-else class="cr-hint">
          授業を終了すると、AI が最終まとめ（テーマ／学習内容／先生の重点／宿題）を作り、詳細画面へ進みます。
          録音は最大 {{ maxRecordingMinutes }} 分で自動的に止まります。
        </span>
      </div>
    </section>
  </div>
</template>

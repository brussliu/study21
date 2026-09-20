import { ApiError, HttpClient, type ApiResponse } from '@study21/web-shared'
import { segmentSpeaker } from '@/features/classroom/classroom'
import type { ClassroomAudioMode } from '@/features/classroom/audio-graph'

/**
 * 授業録音 / AI 授業記録 API（`/api/user/classroom`）。
 * user-api の `ClassroomController` と 1 対 1 で対応する。
 *
 * データは `CR_授業記録情報` / `CR_授業転写セグメント情報` / `CR_授業ノート情報` /
 * `CR_前置詞プリセット情報`。録音・分塊アップロード・STT・転写追記は user-api、
 * AI 授業ノートの生成は admin-api のバッチ（`batC61` / `batC62`）が行う。
 *
 * 画面の流れ（**送信 → ポーリング**。AI 生図と同じ契約）:
 *   1. `POST /classroom` で作成（RECORDING）→ `POST /classroom/{id}/start` で開始
 *   2. 分塊を `POST /classroom/{id}/chunks?seq=N` で送る（分塊長は `options.chunkSeconds`）
 *      → 応答の `appendedSegments` を書き起こしへ足し、`triggered` のときは
 *        `pendingNoteId` の `runPath`（admin-api の薄い入口）を **1 回だけ**呼ぶ
 *   3. `GET /classroom/{id}/segments?afterSeq=N` を数秒間隔でポーリングして追記を取る
 *   4. 終了は `POST /classroom/{id}/end`。返る `finalNoteId` の `runPath` を呼ぶ
 *      （最終まとめ = batC62）→ `GET /classroom/{id}` をポーリングして結果を出す
 *
 * 音声は `<audio>` が `classroomAudioUrl()` をそのまま読む（サーバーは Range/206 対応）。
 */

/**
 * 書き起こしの**音源**。
 * `mic`＝マイク（対面なら「講義」、二音源なら「学生」）／`shared`＝共有した音（「先生」）。
 */
export type ClassroomSource = 'mic' | 'shared'

/** 授業の言語モード（`features/classroom/classroom.ts` の `LanguageMode` と同じ値）。 */
export type ClassroomLanguageMode = 'zh' | 'ja' | 'en' | 'zh-en' | 'ja-en' | 'auto'

/** `CR_授業記録情報.状態`。 */
export type ClassroomStatus =
  | 'RECORDING' | 'STOPPED' | 'TRANSCRIBING' | 'ANALYZING' | 'COMPLETED' | 'FAILED' | 'CANCELLED'

/** ノートの種別（PHASE = 途中のフェーズ分析 / FINAL = 終了時の最終まとめ）。 */
export type ClassroomNoteKind = 'PHASE' | 'FINAL'

/** ノートの生成状態（PENDING の行を admin-api のバッチが拾う）。 */
export type ClassroomNoteStatus = 'PENDING' | 'GENERATING' | 'READY' | 'FAILED'

/** 画面が使う設定（**API Key は返らない**）。 */
export interface ClassroomOptions {
  enabled: boolean
  /** 分塊アップロードの長さ（秒。既定 20） */
  chunkSeconds: number
  /** 録音の最大時間（分） */
  maxRecordingMinutes: number
  /** 録音とノートの保存期間（日） */
  retentionDays: number
  /** 1 アカウント 1 日の録音回数（0 = 無制限） */
  dailyLimit: number
  /** 今日すでに作った件数 */
  usedToday: number
  /** 使えないときの理由（日本語。空なら使える） */
  notice: string
  /**
   * 書き起こしを誰が行うか。
   * `BROWSER` = 画面（Chrome / Edge の Web Speech API。サーバーは STT を呼ばない）／
   * `SERVER` = サーバー（STT プロバイダー。API Key が必要）。
   */
  sttMode: 'BROWSER' | 'SERVER'
  /** 言語モード（ja / zh / en / ja-en / zh-en）→ BCP-47（例 ja-JP）。 */
  sttLanguageCodes: Record<string, string>
  /**
   * 授業の AI 解析（フェーズノート batC61 / 最終まとめ batC62）を使うか。
   * false のときは書き起こしだけ（AI を呼ばない）。
   */
  noteEnabled: boolean
  /** ストリーミング書き起こし（話しながら文字が出る）が使えるか（阿里巴巴のときだけ true）。 */
  streamStt?: boolean
}

/** 前置詞（シナリオプリセット）。GLOBAL + 自分のスコープが返る。 */
export interface ClassroomPreset {
  presetId: number
  scope: 'GLOBAL' | 'FAMILY' | 'STUDENT'
  name: string
  text: string
  displayOrder: number
}

/** 作成・開始の結果。 */
export interface ClassroomRecordStatus {
  recordId: number
  recordNo: string
  status: ClassroomStatus
  statusLabel: string
  version: number
}

/** 転写の 1 行（タイムスタンプ・話者つき）。 */
export interface ClassroomSegment {
  segmentId: number
  seq: number
  startOffsetSeconds: number | null
  endOffsetSeconds: number | null
  /**
   * 話者ラベル（サーバーが音源から決める）。
   *
   * <p>サーバーは「共有の音が届いたか」で二音源かどうかを決めるため、二音源の設定でも
   * 共有が届かなかった回は「講義」と返る。画面は `segmentSpeakerOf` で**音源の設定**に
   * 合わせて出し直す。</p>
   */
  speaker: string | null
  /**
   * この文を出した音源（`mic` / `shared`）。
   *
   * <p>録音中の画面は、**どの音源の送信から届いた文か**をここに付ける（画面が自分の
   * 知っている事実を残す）。API が返さないときは undefined（＝音源が分からない）。</p>
   */
  source?: ClassroomSource | null
  text: string
  language: string | null
  createdAt: string | null
}

export interface ClassroomSegmentList {
  items: ClassroomSegment[]
  /** 次に要求する afterSeq（画面はこれをそのまま渡す） */
  nextSeq: number
}

/* ---------- 転写の表示（録音中の画面・詳細画面・最終まとめで**同じ規則**にする） ---------- */

/** 話者を分けないときのラベル（単一音源・旧データ）。 */
const SPEAKER_LECTURE = '講義'
/** 二音源（マイク＝学生／共有した音＝先生）のラベル。 */
const SPEAKER_STUDENT = '学生'
const SPEAKER_TEACHER = '先生'

/**
 * その録音で書き起こす音源を返す（**【新しい授業】で選んだ音源の設定**で決める）。
 *
 * <p><b>共有の音が実際に届いたかでは決めない</b>。二音源の設定なら、その回に共有の音が
 * 届かなくてもマイクの文は「学生」として扱う（サーバーは届いた音で二音源かを判断するので、
 * 画面が設定で決め直す）。</p>
 */
export function classroomSourcesOf(mode: ClassroomAudioMode | null): ClassroomSource[] {
  return mode === 'mic-pc' ? ['mic', 'shared'] : ['mic']
}

/** 音源ごとの話者ラベル（二音源＝マイク学生／共有先生、単一音源＝講義）。 */
export function speakerOfSource(mode: ClassroomAudioMode | null, source: ClassroomSource): string {
  if (mode !== 'mic-pc') return SPEAKER_LECTURE
  return source === 'shared' ? SPEAKER_TEACHER : SPEAKER_STUDENT
}

/**
 * 転写 1 行の話者ラベル（録音中の画面・詳細画面で同じものを使う）。
 *
 * <p>決めるのは**音源の設定**（【新しい授業】の dialog で選んだ値）:
 * 二音源＝マイク学生・共有先生／単一音源＝講義。</p>
 *
 * <p>設定が分からないとき（録音画面から来ていない・古い記録）はサーバーの値をそのまま使う
 * （分からないことを勝手に決めない）。行の音源も分からないときは、サーバーが「先生」と
 * 言った行だけを先生として残す（「講義」はサーバーが単一音源と見た印なので、二音源の
 * 設定では**学生の行**）。</p>
 */
export function segmentSpeakerOf(
  mode: ClassroomAudioMode | null, segment: ClassroomSegment
): string {
  if (mode === null) return segmentSpeaker(segment.speaker)
  if (mode === 'mic') return SPEAKER_LECTURE
  if (segment.source === 'shared') return SPEAKER_TEACHER
  if (segment.source === 'mic') return SPEAKER_STUDENT
  return segmentSpeaker(segment.speaker) === SPEAKER_TEACHER ? SPEAKER_TEACHER : SPEAKER_STUDENT
}

/**
 * 転写の並び（**実際の発言開始時刻**＝音声クロックの先頭オフセット）。
 *
 * <p>到着順・連番では並べない（遅れて届いた文も正しい位置へ入る）。同時刻は連番で
 * 安定させ、時刻が無い行は末尾へ置く（並びが揺れない）。**話者は入れ替えない**
 * （並べ替えで行の中身を混ぜない）。</p>
 */
export function orderClassroomSegments(segments: ClassroomSegment[]): ClassroomSegment[] {
  return [...segments].sort((left, right) => {
    const leftStart = left.startOffsetSeconds ?? Number.MAX_SAFE_INTEGER
    const rightStart = right.startOffsetSeconds ?? Number.MAX_SAFE_INTEGER
    if (leftStart !== rightStart) return leftStart - rightStart
    return left.seq - right.seq
  })
}

/**
 * 追記された文を今の一覧へ混ぜる（**同じ発話の新しい文が勝つ**）。
 *
 * <p>「同じ連番があるから」と捨てると、同じ発話の**訂正・追記**（サーバーは発話キーで
 * 1 行に寄せる＝同じ行が新しい文で更新される）が画面に出ない。行の同一性は
 * `segmentId`（無いときだけ連番）で見て、同じ行なら**新しい文で置き換える**。</p>
 *
 * <p>ただし**取得した行（音源が分からない行）で、画面へ直接届いた行を巻き戻さない**:
 * ポーリングや詳細の取得は**始めた時点の内容**を返すことがあり、同じ発話の古い文を
 * あとから持ってくることがある。取得の行には音源（`source`）が付かないので、上書きすると
 * 直した文が消えるだけでなく**話者もサーバーの値（「共有が届いていない」＝講義など）へ
 * 戻ってしまう**。サーバーが同じ行をさらに直したときは、直接届く文（音源つき）が
 * それを持ってくるので、ここで古い取得を採る必要はない。</p>
 */
export function mergeClassroomSegments(
  current: ClassroomSegment[], incoming: ClassroomSegment[]
): ClassroomSegment[] {
  if (incoming.length === 0) return current
  const merged = [...current]
  for (const segment of incoming) {
    const index = merged.findIndex((item) => sameSegment(item, segment))
    if (index < 0) {
      merged.push(segment)
      continue
    }
    const existing = merged[index] as ClassroomSegment
    if (hasLiveSource(existing) && !hasLiveSource(segment)) continue
    merged[index] = segment
  }
  return merged
}

/** その行が**画面へ直接届いた**ものか（送信の応答に付く音源が分かっている行）。 */
function hasLiveSource(segment: ClassroomSegment): boolean {
  return segment.source === 'mic' || segment.source === 'shared'
}

/** 同じ転写の行か（サーバーの行 ID を優先し、無いときだけ連番で見る）。 */
function sameSegment(left: ClassroomSegment, right: ClassroomSegment): boolean {
  if (left.segmentId > 0 && right.segmentId > 0) return left.segmentId === right.segmentId
  return left.seq === right.seq
}

/**
 * 結合（再生用の 1 本）の状態。**「音声は保存されている」と「再生できる」は別**。
 *
 * <p>`FAILED` / `INCOMPLETE` でも `storedChunks` の分だけ音は残っている（やり直せる）。</p>
 */
export interface ClassroomAssemblyView {
  /**
   * `NOT_STARTED`（まだ作っていない）/ `QUEUED`（受け付けた）/ `PROCESSING`（作成中）/
   * `READY`（できた）/ `INCOMPLETE`（欠落があり作らない）/ `FAILED`（作れなかった・やり直せる）。
   *
   * <p>「作成中」と言ってよいのは `QUEUED` / `PROCESSING` **だけ**。`NOT_STARTED` を
   * 「作成中」と読むと、永久に待ち続ける画面になる。</p>
   */
  state: 'NOT_STARTED' | 'QUEUED' | 'PROCESSING' | 'READY' | 'INCOMPLETE' | 'FAILED'
  /** いまある分塊の**全部**を含んだ 1 本があるか。 */
  complete: boolean
  /** 保存できている分塊の数（**音は残っている**ことの根拠）。 */
  storedChunks: number
  /** できた 1 本の長さ（秒。分からなければ null）。 */
  durationSeconds: number | null
  /** 欠けている連番（あれば）。 */
  missingSeqs: number[]
  /** 画面に出す理由（日本語。無ければ null）。 */
  reason: string | null
}

/** AI 授業ノート（PHASE / FINAL）。`noteJson` は AI が返した JSON 文字列。 */
export interface ClassroomNote {
  noteId: number
  kind: ClassroomNoteKind
  phaseNo: number | null
  startSeq: number | null
  endSeq: number | null
  status: ClassroomNoteStatus
  statusLabel: string
  noteJson: string | null
  errorCode: string | null
  errorMessage: string | null
  createdAt: string | null
  updatedAt: string | null
}

/** 詳細（状態・転写全文・ノート一覧・前置詞・最終まとめ）。 */
export interface ClassroomRecordDetail {
  recordId: number
  recordNo: string
  title: string | null
  /** 科目（数学・英語など。任意） */
  subject: string | null
  languageMode: ClassroomLanguageMode
  presetId: number | null
  presetName: string | null
  presetText: string | null
  status: ClassroomStatus
  statusLabel: string
  startTime: string | null
  endTime: string | null
  durationSeconds: number | null
  transcribedChars: number | null
  /** 最終まとめ（AI が返した JSON 文字列） */
  summaryJson: string | null
  hasAudio: boolean
  audioMime: string | null
  segments: ClassroomSegment[]
  notes: ClassroomNote[]
  version: number
  createdAt: string | null
  updatedAt: string | null
  /**
   * 結合（再生用の 1 本）の状態。
   *
   * <p>「音声は保存されている／再生用を作成中／作成に失敗」を分けて出すために使う
   * （失敗しても分塊は残っている＝やり直せる）。</p>
   */
  assembly?: ClassroomAssemblyView | null
  /**
   * **不完全なまま終えた回に失った連番**（音が残っていない区間）。
   *
   * <p>詳細画面が「どこが失われたか」を出し続けるために使う（空なら欠落していない）。</p>
   */
  lossSeqs?: number[]
  /** 書き起こし（認識）の収尾の結果（音声の欠落とは別の軸）。 */
  transcribe?: ClassroomTranscribeView | null
}

/** 一覧の 1 行。 */
export interface ClassroomRecordRow {
  recordId: number
  recordNo: string
  title: string | null
  /** 科目（数学・英語など。未設定は null） */
  subject: string | null
  languageMode: ClassroomLanguageMode
  status: ClassroomStatus
  statusLabel: string
  durationSeconds: number | null
  transcribedChars: number | null
  hasAudio: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface ClassroomRecordPage {
  items: ClassroomRecordRow[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

/**
 * 分塊アップロードの結果。
 * トリガーが成立すると `pendingNoteId` と `runPath`（admin-api の薄い入口）が入る。
 */
export interface ClassroomChunkResult {
  recordId: number
  seq: number
  /**
   * 次に取りに行く**書き起こし**（セグメント）の連番。
   *
   * **分塊の連番ではない**: 転写は文ごと、分塊は音の塊ごとで数が違う。
   * 次の分塊の連番は `nextChunkSeq`（分塊表）を使う。
   */
  nextSeq: number
  /**
   * 次に送る**分塊**の連番（分塊表の最大連番 + 1。0 件なら 1）。
   *
   * 開き直したときの続きの番号はこれを使う（転写の連番から作ると、
   * 文の数と分塊の数が違う回に番号がずれる）。
   */
  nextChunkSeq: number
  appendedSegments: ClassroomSegment[]
  pendingNoteId: number | null
  triggered: boolean
  status: ClassroomStatus
  runPath: string | null
}

/** 保存済みの分塊 1 つ（録音の位置と処理状態）。 */
export interface ClassroomRecordingChunk {
  seq: number
  byteSize: number
  startOffsetSeconds: number | null
  endOffsetSeconds: number | null
  mime: string | null
  /** この分塊が新しいコンテナ（`MediaRecorder` の作り直し）の先頭か。 */
  containerHead: boolean
  /** `STORED`（音声だけ）/ `TRANSCRIBED`（書き起こし済み）/ `SKIPPED`（書き起こしを省略）。 */
  processingStatus: string
  segmentCount: number
  createdAt: string | null
}

/**
 * 保存済みの分塊の一覧（`GET /classroom/{id}/chunks`）。
 *
 * 画面は**開き直したときの続きの連番**（`nextSeq`）と、録音の位置
 * （`recordedSeconds`。経過時間の続き）をここから取る。
 */
export interface ClassroomRecordingChunkList {
  items: ClassroomRecordingChunk[]
  chunkCount: number
  maxSeq: number
  /** 次に送る分塊の連番（1 から） */
  nextSeq: number
  totalBytes: number
  /** 保存済みの分塊が示す録音の位置（秒）。0 件なら null */
  recordedSeconds: number | null
  /**
   * 終了してよいかの下見（**サーバーが判断したもの**）。
   *
   * <p>画面を開き直しても同じ判断が取れるようにサーバーから返す（画面のメモリだけに頼らない）。</p>
   */
  finalizeCheck?: ClassroomFinalizeCheck
}

/**
 * 終了前の確認の結果（分塊が 1 から連続しているか）。
 *
 * <p>「少なくとも 1 つある」では足りない: 途中が欠けていても、最後の分塊が届いていなくても
 * 区別できない。欠けている連番を受け取って**その分塊だけ送り直す**。</p>
 */
export interface ClassroomFinalizeCheck {
  /** 分塊が 1 から連続していて全部そろっているか（＝終了できる）。 */
  complete: boolean
  /** 足りない連番。 */
  missingSeqs: number[]
  /** 保存できている分塊の数。 */
  storedChunks: number
  /** 画面が宣言した最後の連番。 */
  expectedChunks: number
  /** 画面に出す理由（日本語。終了できるときは null）。 */
  reason: string | null
}

/**
 * 停止のあとに画面が送る「送った分塊の一覧」。
 *
 * <p>サーバーはこれで**最後の分塊の取りこぼし**を見つける（行の最大連番だけでは、
 * まだ届いていない最後の分塊が分からない）。</p>
 */
export interface ClassroomChunkManifest {
  /**
   * **実際に録れた**最後の分塊の連番（分塊を作った時点で決まる）。
   *
   * <p>「送れた数」ではない。送信が失敗しても**録れた事実**は残す（そうしないと、
   * 最後の分塊の送信が失敗した回にサーバーが取りこぼしを見つけられない）。</p>
   */
  expectedLastSeq: number
  /** **実際に録れた**分塊の数（分塊を作った時点で数える）。 */
  expectedCount: number
  /**
   * **送信の応答を受け取れた**連番（**補助情報**）。
   *
   * <p>保存の事実ではない: 応答だけ失われた回はここから抜けるが、後端には保存されている。
   * だから後端はこの欄を**欠落の判断に使わない**（代わりに後端が保存済みの連番を返す）。</p>
   */
  uploadedSeqs: number[]
  /** 旧い画面との互換: 送信の応答を受け取れた最後の連番（新しい画面は使わない）。 */
  lastSeq: number
  /** 旧い画面との互換: 送信の応答を受け取れた件数（新しい画面は使わない）。 */
  totalCount: number
  /** 最後に**録れた**分塊が終わる録音回放の時間軸の位置（16kHz のサンプル数）。 */
  endSample?: number
  /**
   * **もう送り直しても直らない**分塊の連番（4xx＝内容の問題）。
   *
   * <p>黙って捨てない: 画面はこの連番を「失った音声」として出し、利用者が確認してから
   * 不完全なまま終われるようにする。</p>
   */
  unrecoverableSeqs?: number[]
  /**
   * その分塊を送れなかった**理由**（連番 → 後端が返した日本語のメッセージ）。
   *
   * <p>「保存できなかった連番: 3」だけでは、利用者は何が起きたか分からない。
   * 「同じ連番に違う内容の音声が届きました。」のように**理由**まで出すために残す。</p>
   */
  unrecoverableReasons?: Record<string, string>
}

/**
 * 終了の結果。
 * 書き起こし（転写セグメント）が 1 件も無いまま終了したときは `finalNoteId` と `runPath` が
 * null になり、`notice` に理由（日本語）が入る。**そのときは admin-api の runPath を呼ばない。**
 */
export interface ClassroomEndResult {
  recordId: number
  status: ClassroomStatus
  statusLabel: string
  finalNoteId: number | null
  runPath: string | null
  /** 画面に出す補足（日本語）。通常は null、最終まとめを作らなかったときだけ入る */
  notice: string | null
  /**
   * 終了を**完了できなかった**理由（日本語）。null なら完了。
   *
   * <p>HTTP 200 でもここに理由が入ることがある（収尾を取り切れず、尾部の文を保存できなかった等）。
   * 画面は `notice`（まとめを作らなかった案内）と混同せず、**失敗として**出してやり直させる
   * （成功と言わない・最終まとめを起動しない）。</p>
   */
  error?: string | null
  /** 音声が**全部そろっているか**（「音声は保存されています」と言ってよいのは true のときだけ）。 */
  complete?: boolean
  /** 足りない分塊の連番（あれば。画面はこの連番を送り直す）。 */
  missingSeqs?: number[]
  /** 明示の「不完全なまま終了」で終えたか。 */
  forced?: boolean
  /** 画面が申告した**実際に録れた**最後の連番（旧い画面は 0）。 */
  expectedLastSeq?: number
  /** 明示の不完全終了で**失った**連番（詳細画面に出し続ける）。 */
  lossSeqs?: number[]
  /** 失った範囲の理由の種類。 */
  lossReasonCode?: string | null
  /**
   * **書き起こし（認識）の収尾の結果**（音声の欠落とは**別の軸**）。
   *
   * <p>`complete=false` は「識別が不完全」または「確認できない」。画面は
   * 「録音と書き起こしを保存しました」と言い切らない。</p>
   */
  transcribe?: ClassroomTranscribeView | null
}

/**
 * **最終まとめ（1 つのノート）の生成タスクの状態**（起動の受理を確かめる入口）。
 *
 * <p>`PENDING` は「user-api が行を作った」だけで、**admin-api の実行が受理された証拠ではない**。
 * 受理されると `GENERATING` 以上になる。画面は `accepted` を見る。</p>
 */
export interface ClassroomNoteTaskStatus {
  noteId: number
  kind: string
  /** `PENDING`（未受理）/ `GENERATING`（受理済み・実行中）/ `READY`（完成）/ `FAILED`（失敗）。 */
  status: string
  statusLabel: string
  /** 実行が**受理されている**か（`GENERATING` 以上、または完成）。 */
  accepted: boolean
  /** もう一度起動を頼めるか（`PENDING`・`FAILED` は true）。 */
  retryable: boolean
  errorCode: string | null
  errorMessage: string | null
}

/**
 * **最終まとめ（1 つ）の状態**を取る（`GET /classroom/{id}/notes/{noteId}`）。
 *
 * <p>「FINAL の行があるか」ではなく、**その行の状態そのもの**を返す。起動の応答を失った回に
 * 「受理されたのか、まだ始まっていないのか」を正しく見分けるために使う。</p>
 */
export function fetchClassroomNoteTaskStatus(
  recordId: number, noteId: number
): Promise<ApiResponse<ClassroomNoteTaskStatus>> {
  return http.get<ClassroomNoteTaskStatus>(`${BASE}/${recordId}/notes/${noteId}`)
}

/** 書き起こし（認識）の収尾の状態（詳細・終了の応答が返す）。 */
export interface ClassroomTranscribeView {
  /** `COMPLETE` / `INCOMPLETE` / `RUNNING` / `NO_AUDIO` / `UNKNOWN`。 */
  status: string
  /** 識別が完全にそろっているか（**確認できないときは false**）。 */
  complete: boolean
  /** まだやり直せるか（`RUNNING` のとき true）。 */
  retryable: boolean
  /** 画面に出す短い理由（日本語。無ければ null）。 */
  reason: string | null
  /** 音源ごとの結果。 */
  sources: ClassroomTranscribeSourceView[]
}

/** 音源 1 つの収尾の結果。 */
export interface ClassroomTranscribeSourceView {
  source: string
  label: string
  status: string
  completed: boolean
  retryable: boolean
  savedCount: number
  pendingCount: number
  reason: string | null
}

export interface ClassroomDeleteResult {
  recordId: number
  /** 音声の実体も消せたか */
  deletedAudio: boolean
}

/** 状態の日本語ラベル（`ClassroomModels.STATUS_LABELS` と同じ）。 */
export const CLASSROOM_STATUS_LABELS: Record<ClassroomStatus, string> = {
  RECORDING: '録音中',
  STOPPED: '停止（まとめ作成待ち）',
  TRANSCRIBING: '書き起こし中',
  ANALYZING: 'AI が分析中',
  COMPLETED: '完了',
  FAILED: '失敗',
  CANCELLED: '取消'
}

/** ノートの生成状態の日本語ラベル（`ClassroomModels.NOTE_STATUS_LABELS` と同じ）。 */
export const CLASSROOM_NOTE_STATUS_LABELS: Record<ClassroomNoteStatus, string> = {
  PENDING: '分析待ち',
  GENERATING: 'AI が生成中',
  READY: 'できました',
  FAILED: '失敗'
}

/** 1 ページの既定件数（サーバーの `ClassroomModels.DEFAULT_SIZE` と同じ）。 */
export const CLASSROOM_DEFAULT_SIZE = 20
/** 1 ページの上限件数（サーバーの `ClassroomModels.MAX_SIZE` と同じ）。 */
export const CLASSROOM_MAX_SIZE = 100
/** 授業名の上限（サーバーの `ClassroomModels.TITLE_MAX` と同じ）。 */
export const CLASSROOM_TITLE_MAX = 200

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/user' })
/** 授業録音の API の前置き（`/api/user/classroom`）。 */
const BASE = '/classroom'
/** 画面が使う設定（有効／無効・分塊長・録音最大時間・保存期間・日次上限）。 */
export function fetchClassroomOptions(): Promise<ApiResponse<ClassroomOptions>> {
  return http.get<ClassroomOptions>(`${BASE}/options`)
}

/** ストリーミング書き起こしの 1 回ぶんの結果。 */
export interface ClassroomSttStreamPush {
  /** まだ確定していない文（画面は 1 行だけ上書きして出す）。 */
  interim: string
  /** この回で確定して保存されたセグメント。 */
  added: ClassroomSegment[]
  /**
   * やり直せば直る失敗の理由（日本語。null なら「やり直せる失敗ではない」）。
   *
   * <p>**`error` が null でも成功とは限らない**: やり直しても直らない終端は `error` ではなく
   * `notice` に載る（`finalizeCompleted=false` / `retryable=false`）。成功と誤読しないためには
   * {@link ClassroomSttStreamPush.finalizeCompleted} と
   * {@link ClassroomSttStreamPush.retryable} を必ず見る。</p>
   */
  error: string | null
  /** この音源の収尾の段階（`AUDIO_ACCEPTING` / `FINALIZING` / `SAVED` / `NO_AUDIO` / `NO_UTTERANCE` / `FAILED`）。 */
  finalizeStatus?: string
  /** 収尾が**完了したか**（必要な結果を全部保存できたか）。 */
  finalizeCompleted?: boolean
  /**
   * **まだやり直せるか**（`false` は終端）。
   *
   * <p>旧い後端はこの欄を返さない。そのときは「確認できない」として扱い、**成功とは見なさない**。</p>
   */
  retryable?: boolean
  /** 失敗したときの復帰の仕方（`AWAIT_RESULTS` / `RESAVE_PENDING` / `RETRANSCRIBE`。null なら無し）。 */
  recovery?: string | null
  /** この音源で保存できた文の数。 */
  savedCount?: number
  /** 保存待ちで残している文の数。 */
  pendingCount?: number
  /** **失敗ではない知らせ**（音声なし・発話なしの終端、やり直しても直らない終端など）。 */
  notice?: string | null
}

/** 前置詞（シナリオプリセット）の一覧（GLOBAL + 自分のスコープ）。 */
export function fetchClassroomPresets(): Promise<ApiResponse<ClassroomPreset[]>> {
  return http.get<ClassroomPreset[]>(`${BASE}/presets`)
}

/**
 * 授業記録を作成する（状態 = RECORDING）。
 * `presetId` は選んだ前置詞プリセット（任意）。サーバーは選択時のテキストを記録に残す。
 */
export function createClassroomRecord(body: {
  title: string
  /** 科目（数学・英語など。任意） */
  subject?: string | null
  languageMode: ClassroomLanguageMode
  presetId?: number | null
}): Promise<ApiResponse<ClassroomRecordStatus>> {
  return http.post<ClassroomRecordStatus>(BASE, { body })
}

/**
 * ストリーミング書き起こしに音声を 1 回ぶん送る（`POST /classroom/{id}/stt/stream?source=`）。
 *
 * 本文は 16kHz モノラル 16bit の PCM（ヘッダ無し）。返る `interim` は**まだ確定していない文**、
 * `added` は**この回で確定して保存された文**。
 *
 * <p>文の時刻は**サーバーが音声クロックから決める**（画面は時刻を送らない）。</p>
 *
 * <p><b>フレームの識別を常時接続と同じ欄で渡す</b>（`frameNo` ＝ その音源で 1 から数えた番号、
 * `startSample` ＝ 録音の先頭からの絶対位置（16kHz のサンプル数））。常時接続は
 * バイナリの前に見出しのテキストで同じ値を送る。**同じ音を送り直しても 2 度認識しない**
 * （後端は番号で識別する）ため、退避（常時接続 → HTTP）で経路が変わっても音の同一性が保てる。</p>
 */
export async function pushClassroomSttStream(
  recordId: number, pcm: Blob, source: ClassroomSource = 'mic',
  /** フレームの識別（番号と絶対位置）。渡さないときは後端が到着順に採番する（互換） */
  frame: { frameNo?: number; startSample?: number } = {}
): Promise<ApiResponse<ClassroomSttStreamPush>> {
  const query = new URLSearchParams({ source })
  if (frame.frameNo !== undefined) query.set('frameNo', String(frame.frameNo))
  if (frame.startSample !== undefined) query.set('startSample', String(frame.startSample))
  let response: Response
  try {
    // 音源ごとに別のセッション（話者ラベルと時間軸はサーバーが音源から決める）
    response = await fetch(`/api/user/classroom/${recordId}/stt/stream?${query.toString()}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/octet-stream' },
      body: pcm
    })
  } catch (cause) {
    throw new ApiError({ code: 'NETWORK_ERROR', message: 'ネットワークエラーが発生しました', cause })
  }
  const payload = (await response.json().catch(() => null)) as ApiResponse<ClassroomSttStreamPush> | null
  if (!response.ok || !payload?.success) {
    throw new ApiError({
      code: payload?.code ?? 'HTTP_ERROR',
      message: payload?.message ?? '書き起こしに失敗しました。',
      status: response.status
    })
  }
  /*
   * **HTTP 200 でも業務エラーは成功ではない**。
   *
   * 後端は「認識セッションが失敗した（保存できなかった文がある）」ときも 200 で返し、
   * 理由を `data.error` に入れる（`{success, code, message, data, traceId}` の形なので、
   * `success` だけを見ると**送れていないのに送れたことにする**）。呼び出し側が同じ音を
   * 送り直せるように失敗として投げるが、**その回に保存できた文は捨てない**（`data` に載せて渡す）。
   */
  const businessError = payload.data?.error
  if (typeof businessError === 'string' && businessError !== '') {
    throw new ApiError({
      code: 'STT_ERROR',
      message: businessError,
      status: response.status,
      traceId: payload.traceId,
      data: payload.data
    })
  }
  return payload
}

/** ストリーミング書き起こしの終わり（最後の確定文を取り出して閉じる）。 */
export async function finishClassroomSttStream(
  recordId: number, source: ClassroomSource = 'mic'
): Promise<ApiResponse<ClassroomSttStreamPush>> {
  return http.post<ClassroomSttStreamPush>(
    `${BASE}/${recordId}/stt/stream/finish?source=${source}`)
}

/**
 * 録音せずに取り込む（`POST /classroom/{id}/source`）。
 *
 * **音声ファイル（mp3・90 分まで）**か**貼り付けた文字起こし（50 万字まで）**のどちらかを送る。
 * 音声ならサーバーが書き起こして、文ごとに分けて保存する。そのあと `/end` を呼ぶと最終まとめの対象になる。
 */
export async function importClassroomSource(
  recordId: number,
  body: { file?: File | null; text?: string | null; durationSeconds?: number | null }
): Promise<ApiResponse<ClassroomChunkResult>> {
  const form = new FormData()
  if (body.file !== undefined && body.file !== null) form.append('file', body.file, body.file.name)
  if (body.text !== undefined && body.text !== null) form.append('text', body.text)
  if (body.durationSeconds !== undefined && body.durationSeconds !== null) {
    form.append('durationSeconds', String(Math.round(body.durationSeconds)))
  }
  let response: Response
  try {
    response = await fetch(`/api/user/classroom/${recordId}/source`, { method: 'POST', body: form })
  } catch (cause) {
    throw new ApiError({ code: 'NETWORK_ERROR', message: 'ネットワークエラーが発生しました', cause })
  }
  const payload = (await response.json().catch(() => null)) as ApiResponse<ClassroomChunkResult> | null
  if (!response.ok || !payload?.success) {
    throw new ApiError({
      code: payload?.code ?? 'HTTP_ERROR',
      message: payload?.message ?? '取り込みに失敗しました。',
      status: response.status
    })
  }
  return payload
}

/** 録音を開始する（開始時刻を確定する）。 */
export function startClassroomRecord(recordId: number): Promise<ApiResponse<ClassroomRecordStatus>> {
  return http.post<ClassroomRecordStatus>(`${BASE}/${recordId}/start`)
}

/**
 * 分塊をアップロードする（STT はサーバー側。応答に追記セグメントとトリガー結果が入る）。
 *
 * HttpClient は body を JSON にするため、multipart は fetch を直接使う
 * （Content-Type を自分で付けない＝ブラウザに boundary を付けさせる）。
 */
/**
 * 分塊を送る（`POST /api/user/classroom/{id}/chunks`）。
 *
 * `blob` は**再生用**の音声（`MediaRecorder` が作る webm）。`sttPcm` は**書き起こし用**の
 * 16kHz モノラル 16bit PCM（`audio/L16`）で、あればサーバーはこちらを音声認識へ送る
 * （`MediaRecorder` の分塊は 2 つ目以降がコンテナのヘッダを持たず認識できないため）。
 */
export async function uploadClassroomChunk(
  recordId: number, seq: number, blob: Blob, sttPcm: Blob | null = null,
  /** 画面が測った**実際の経過秒**（分塊の始まり・終わり）。無ければサーバーが連番から計算する */
  offsets: { startSeconds?: number; endSeconds?: number } = {},
  fileName = `chunk-${seq}.webm`
): Promise<ApiResponse<ClassroomChunkResult>> {
  const form = new FormData()
  form.append('seq', String(seq))
  form.append('file', blob, fileName)
  if (sttPcm !== null && sttPcm.size > 0) {
    form.append('stt', sttPcm, `chunk-${seq}.pcm`)
  }
  let response: Response
  try {
    const query = new URLSearchParams({ seq: String(seq) })
    if (offsets.startSeconds !== undefined) query.set('startSeconds', String(offsets.startSeconds))
    if (offsets.endSeconds !== undefined) query.set('endSeconds', String(offsets.endSeconds))
    response = await fetch(`/api/user/classroom/${recordId}/chunks?${query.toString()}`,
      { method: 'POST', body: form })
  } catch (cause) {
    throw new ApiError({ code: 'NETWORK_ERROR', message: 'ネットワークエラーが発生しました', cause })
  }
  const payload = (await response.json().catch(() => null)) as ApiResponse<ClassroomChunkResult> | null
  if (!response.ok || !payload?.success) {
    throw new ApiError({
      code: payload?.code ?? 'INTERNAL_ERROR',
      message: payload?.message ?? `HTTP ${response.status}`,
      status: response.status,
      traceId: payload?.traceId
    })
  }
  return payload
}

/**
 * ブラウザ（Web Speech API）の認識結果を 1 件送る（`sttMode = BROWSER` のとき）。
 *
 * 認識は画面が行い、サーバーはセグメントとして保存してトリガーだけ評価する
 * （応答の形は分塊アップロードと同じ＝追記セグメントとトリガー結果）。
 */
export function sendClassroomTranscript(
  recordId: number, body: { text: string; offsetSeconds?: number }
): Promise<ApiResponse<ClassroomChunkResult>> {
  return http.post<ClassroomChunkResult>(`${BASE}/${recordId}/transcripts`, { body })
}

/** 追記セグメントを取る（ポーリング用。`afterSeq` より後の行だけが返る）。 */
export function fetchClassroomSegments(
  recordId: number, afterSeq = 0
): Promise<ApiResponse<ClassroomSegmentList>> {
  return http.get<ClassroomSegmentList>(`${BASE}/${recordId}/segments`, { params: { afterSeq } })
}

/**
 * 保存済みの**分塊**（音声）を取る（`GET /classroom/{id}/chunks`）。
 *
 * 画面は「次に送る分塊の連番」（`nextSeq`）と、録音の位置（`recordedSeconds`）をここから取る。
 * **転写セグメントの連番からは作らない**（文の数と分塊の数は違う。以前はそれを混ぜていて、
 * 転写が分塊より多く出た回に分塊が「重複」として捨てられていた）。
 */
export function fetchClassroomChunks(
  recordId: number, afterSeq = 0
): Promise<ApiResponse<ClassroomRecordingChunkList>> {
  return http.get<ClassroomRecordingChunkList>(`${BASE}/${recordId}/chunks`, { params: { afterSeq } })
}

/** 1 件の詳細（状態・転写全文・ノート一覧・前置詞・最終まとめ）。 */
export function fetchClassroomRecord(recordId: number): Promise<ApiResponse<ClassroomRecordDetail>> {
  return http.get<ClassroomRecordDetail>(`${BASE}/${recordId}`)
}

/** 自分の記録の一覧（保護者は家族・管理者は全体。サーバーが範囲を決める）。 */
export function searchClassroomRecords(params: {
  status?: ClassroomStatus | ''
  page?: number
  size?: number
} = {}): Promise<ApiResponse<ClassroomRecordPage>> {
  return http.get<ClassroomRecordPage>(BASE, { params: { ...params, status: params.status || undefined } })
}

/**
 * 録音を終了する（最終まとめ = PENDING のノートを作る）。
 *
 * <p>停止のあとに分塊を送り切ったら、**送った分塊の一覧**を添えて呼ぶ。サーバーは
 * 分塊が 1 から連続しているかと収尾の状態を確かめ、欠けていれば **409 で欠けている連番**を
 * 返す（{@link ApiError.message} に日本語で入る）。画面はその分塊だけ送り直す。</p>
 *
 * <p>`force` は利用者が影響を確認して押した「不完全なまま終了」。既定は false
 * （＝黙って音を失わない）。</p>
 */
export function endClassroomRecord(
  recordId: number,
  options: { force?: boolean; manifest?: ClassroomChunkManifest } = {}
): Promise<ApiResponse<ClassroomEndResult>> {
  const body: { force: boolean; manifest: ClassroomChunkManifest | null } = {
    force: options.force === true,
    manifest: options.manifest ?? null
  }
  return http.post<ClassroomEndResult>(`${BASE}/${recordId}/end`, { body })
}

/**
 * 結合（再生用の 1 本）をやり直す（所有者のみ）。
 *
 * <p>**分塊は 1 つも消さない**（作り直しの材料）。作れなかったときの入口。</p>
 */
export function retryClassroomAssembly(recordId: number): Promise<ApiResponse<ClassroomAssemblyView>> {
  return http.post<ClassroomAssemblyView>(`${BASE}/${recordId}/assembly/retry`)
}

/** 授業記録を削除する（所有者のみ。音声の実体も消える）。 */
export function deleteClassroomRecord(recordId: number): Promise<ApiResponse<ClassroomDeleteResult>> {
  return http.delete<ClassroomDeleteResult>(`${BASE}/${recordId}`)
}

/**
 * 元音声の URL（`<audio src>` にそのまま使う）。
 * version を付けて、削除・差し替え後にブラウザが古い音声を使い回さないようにする。
 */
export function classroomAudioUrl(recordId: number, version = 0): string {
  return `/api/user/classroom/${recordId}/audio?v=${version}`
}

/**
 * AI 授業ノートの生成を起動する（admin-api の薄い入口。PHASE → batC61 / FINAL → batC62）。
 *
 * **AI には数十秒かかる**ので、タイムアウトを延ばして呼ぶ。画面はこの結果を待たずに
 * `GET /classroom/{id}` のポーリングを続ける（タイムアウトしてもポーリングが真の状態を返す）。
 */
export interface ClassroomNoteAcceptance {
  /** 対象のノート（最終まとめ）の ID。 */
  noteId: number
  /** この要求で**背景の実行を始めた**か（false は既に走っている・既に作成済み）。 */
  accepted: boolean
  /** 受理のあとの状態（`GENERATING` / `READY` / `FAILED`）。 */
  status: string
  /** 画面に出す短い説明（日本語）。 */
  message: string
}

/**
 * **1 つのまとめの状態を確かめ、失联していれば回復してもらう**（admin-api の入口）。
 *
 * <p>画面は「失联」と**断言しない**（前端では実行の生存を確かめられない）。後端が実行記録で
 * 確かめ、実行中なら何もしない（`recovered=false`）。失联していたときだけ**やり直せる失敗**に戻る。</p>
 */
export interface ClassroomNoteRecovery {
  noteId: number
  /** 確かめたあとの生成状態（`GENERATING` / `FAILED` / `READY` …）。 */
  status: string
  /** 実行の生存の判定（`RUNNING` / `BINDING` / `LOST` / `UNKNOWN`）。 */
  liveness: string
  /** 回復（やり直し）の入口を出してよいか。 */
  recoverable: boolean
  /** この呼び出しで回復したか。 */
  recovered: boolean
  /**
   * 画面に出す理由（日本語）。
   *
   * <p>**欄の名前は user-api の `message` で統一**する。admin-api は回復で `reason` を返すが、
   * user-api が受けて `message` に写す（層ごとに欄の名前が違うと、画面が `undefined` を読む）。</p>
   */
  message: string
}

export function recoverClassroomNote(
  recordId: number, noteId: number
): Promise<ApiResponse<ClassroomNoteRecovery>> {
  /*
   * **user-api の保護された入口**を呼ぶ。
   *
   * <p>画面から admin-api の内部入口（`/api/admin/batch/**`）を直接叩かない: あちらは
   * noteId しか見ないので、**他人のまとめを回復できてしまう**。user-api でログイン・授業の
   * 所有権・noteId の帰属を確かめてから、サービス間の合言葉つきで admin-api を呼ぶ。</p>
   */
  return http.post<ClassroomNoteRecovery>(
    `${BASE}/${recordId}/notes/${noteId}/recover`, { body: {} })
}

/**
 * AI 授業ノートの生成の**起動を受理してもらう**（admin-api の薄い入口）。
 *
 * <p>このエンドポイントは「AI を実行し終えた」ではなく「**タスクを受け付けた**」を返す
 * （AI は数十秒〜数分かかるので、画面は完了を待たない。結果は `GET /classroom/{id}` の
 * ポーリングで読む）。同じ要求を何度送っても**2 つ目のタスクは作らない**。</p>
 *
 * <p>受理の応答が返らないこともある（タイムアウト・通信断）。そのときは**状態を問い合わせて**
 * 確かめる（受理されたのに応答を失った回に、もう一度タスクを作らない）。</p>
 */
export function runClassroomNote(
  recordId: number, noteId: number | null = null, timeoutMs = 30_000
): Promise<ApiResponse<ClassroomNoteAcceptance>> {
  /*
   * **user-api の保護された入口**を呼ぶ（`runPath` を画面から叩かない）。
   * 理由は {@link recoverClassroomNote} と同じ: 内部入口は**利用者の権限を確かめない**。
   *
   * <p>`noteId` を省略できるのは、分塊・文字起こしのトリガー（画面が noteId を知らない回）。
   * そのときは user-api が**その授業の未生成のノート**を自分で選ぶ（他人のまとめは選べない）。</p>
   */
  const query = noteId === null ? '' : `?noteId=${noteId}`
  return http.post<ClassroomNoteAcceptance>(
    `${BASE}/${recordId}/notes/run${query}`, { body: {}, timeoutMs })
}

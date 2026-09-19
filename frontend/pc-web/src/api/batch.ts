import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * バッチ管理 API（/api/admin）。
 * 型とエンドポイントは admin-api の BatchController と対応する。
 *
 * 値はコードでやり取りし、日本語の表示文言は画面側（features/batch/batchLabels.ts）で作る。
 */

/** バッチ種別: S=システム起動時 / L=循環 / R=定時 / C=呼出。 */
export type BatchTypeCode = 'S' | 'L' | 'R' | 'C'
/** 実行の状態。 */
export type BatchStatusCode = 'QUEUED' | 'RUNNING' | 'SUCCESS' | 'FAILED' | 'SKIPPED'

export interface BatchTaskRow {
  taskCode: string
  taskType: BatchTypeCode
  description: string
  active: boolean
  activeVersion: number | null
  lastRunAt: string | null
  /** 有効／無効を切り替えられるか（S / L / R のみ true） */
  canToggleActive: boolean
  /**
   * 【再実行】ボタンをこの行に出すか。
   * 種別 C（呼出）は他の処理（AI 生図の流水線・授業ノートなど）が工程として呼ぶバッチで、
   * 画面からは起動できないため false（ボタンごと出さない）。
   */
  canManualRerun: boolean
  /**
   * この一覧の【再実行】ボタンを押せるか（業務処理のハンドラが実装済みか。種別 C は常に false）。
   * `false` は「一覧のボタンが押せない」の意味で、他の処理（AI 生図の流水線など）からの
   * 呼出まで禁じるものではない（呼出は `canManualRerun` とも別の入口）。
   */
  canRerun: boolean
  /** 起動時に実行されるバッチか（種別 S） */
  runsOnStartup: boolean
  loopEveryMinutes: number | null
  minuteOfHour: number | null
  pageCode: string | null
  requiredSettings: string[]
  settingsComplete: boolean
  missingSettings: string[]
  latestStatus: BatchStatusCode | null
  latestStartTime: string | null
  latestEndTime: string | null
  latestMessage: string | null
  running: boolean
}

export interface BatchTaskListResult {
  rows: BatchTaskRow[]
  totalCount: number
  startupTargets: string[]
}

export interface BatchExecutionRow {
  executionId: number
  batchCode: string
  batchType: BatchTypeCode
  triggerType: BatchTypeCode
  status: BatchStatusCode
  requestedByAccountId: number | null
  requestedByCode: string | null
  scheduleTime: string | null
  startTime: string | null
  endTime: string | null
  durationMs: number | null
  message: string | null
  errorDetail: string | null
  /**
   * 何を処理した行か（実行履歴の「対象」列）。
   * バッチコードは**再利用される**（例: batC52 は以前 AI 生図の AI 生成、いまは AI 画図助手）ので、
   * コードだけでは何の処理か分からない。`AI_FIGURE` / `AI_ASSIST` / `CLASSROOM_NOTE` が入る。
   * バックエンドがまだ返さないときは `requestPayload` から推定する（`batchTargetLabel`）。
   */
  targetKind?: string | null
  targetId?: number | null
  targetKey?: string | null
  /** 要求内容（生 JSON）。例: `{"aiRequestId":164}` / `{"assistId":12}` / `{"noteId":89}` */
  requestPayload?: string | null
}

export interface BatchExecutionPage {
  items: BatchExecutionRow[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

export interface BatchRerunResult {
  success: boolean
  executionId: number
  status: BatchStatusCode
  statusLabel: string
  message: string
  errorDetail: string | null
}

export interface BatchActiveResult {
  success: boolean
  batchCode: string
  active: boolean
  message: string
}

/* ---------------------------------------------------------------------------
 * 実行スケジュール（2.1 で 4 タスクを 1 つのスケジューラへ統一したもの）
 * ------------------------------------------------------------------------- */

/**
 * タスクごとの**設定の状態**（`TaskConfigStatus` と対応）。
 *
 * `MISSING` / `INVALID` のタスクは自動実行しない（コードに隠れた既定値で走らせない）。
 */
export type BatchScheduleConfigStatus = 'NOT_LOADED' | 'LOADED' | 'MISSING' | 'INVALID'

/** 実行スケジュールの対象 1 タスク（batR03 / batR04 / batL02 / batL03）。 */
export interface BatchScheduleTask {
  taskCode: string
  /** 設定の状態（未読込 / 有効 / 未設定 / 設定不正）。 */
  status: BatchScheduleConfigStatus
  /** 画面に出す日本語（未読込 / 有効 / 未設定 / 設定不正）。 */
  statusLabel: string
  /**
   * 有効か（`BAT_バッチコントロール情報` の値。**有効／無効の唯一の正**）。
   * 設定が無いときは null。画面から切り替えるのはバッチ一覧のスイッチだけ。
   */
  enabled: boolean | null
  /** 実行タイミングの説明（例: 「毎日 23:30」「毎時 01/06/…/56 分（5 分間隔・ずらし 1 分）」）。 */
  describe: string
  /** INTERVAL（循環）の実行間隔（分）。DAILY（定時）は null。 */
  intervalMinutes: number | null
  /** INTERVAL のずらし（分）。DAILY は null。 */
  offsetMinutes: number | null
  /** DAILY の実行時刻（HH:mm）。INTERVAL は null。 */
  dailyTime: string | null
  /** 1 日の実行時刻の例（画面表示用。INTERVAL は 24 時間分）。 */
  examplePoints: string[]
  /** 次の計画実行時刻（設定が無い・不正なときは null）。 */
  nextRunAt: string | null
  /** 次の計画実行時刻の表示（例「2026-09-20 06:30」。無いときは理由）。 */
  nextRunLabel: string
  /**
   * 実行設定の**適用時刻**（例「2026-09-19 22:00」）。利用者が時刻・間隔・ずらしを変えた時刻で、
   * **この時刻より前の計画実行点は実行しない**（設定を変えた直後に過去の点を実行しないため）。
   * 起動時の読み込みなど、まだ変更していないときは null。
   */
  configEffectiveFrom: string | null
  /**
   * 実行設定の**計画バージョン**（時刻・間隔・ずらし・有効／無効を変えるたびに 1 つ進む）。
   * 設定値の保存と**同じトランザクション**で進むので、適用時刻と必ず揃う。
   */
  planVersion: number
  /** 「設定が無い・不正」が続いた回数（托底で読み直しを試した回数。使える状態なら 0）。 */
  fallbackFailures: number
  /** 次に托底で設定を読み直す時刻（退避中でなければ null）。 */
  nextFallbackCheckAt: string | null
  /** 動かない理由と次にいつ確認するか（使える状態なら null）。 */
  fallbackMessage: string | null
  /** この画面が最後に計画実行点を確保した時刻（未実行は null）。 */
  lastPlannedAt: string | null
}

/** いまメモリで効いている実行スケジュール（DB は読まない）。 */
export interface BatchScheduleResult {
  /** スケジュールの時計（常に Asia/Tokyo）。 */
  zone: string
  /** いま効いている設定の版（0 = 未読込）。 */
  version: number
  /** その版を DB から読んだ実時刻。 */
  loadedAt: string | null
  /** DB は保存済みだが、メモリへの反映がまだ成功していないか。 */
  pendingRefresh: boolean
  /** 「保存済み・実行設定への反映待ち（理由）。自動で再試行します。」（無いときは null）。 */
  pendingMessage: string | null
  /** 最後に反映を試した時刻。 */
  lastRefreshAt: string | null
  /** 最後の反映失敗の理由（成功なら null）。 */
  lastRefreshError: string | null
  /** 次の自動再試行の予定時刻（待避中でなければ null）。 */
  nextRetryAt: string | null
  /** 設定が無い・不正で自動実行できないタスクがあるか。 */
  configMissing: boolean
  /** その案内（次にいつ確認するかを含む。無ければ null）。 */
  configMissingMessage: string | null
  /** スケジューラが設定を確認する間隔（秒）。 */
  checkIntervalSeconds: number
  /** 実行中の本数。 */
  runningWorkers: number
  /** 実行待ちの本数。 */
  queuedWorkers: number
  /** 対象 4 タスク。 */
  tasks: BatchScheduleTask[]
}

/** 【設定を再読み込み】（管理者）の結果。再読み込み後も応答の形は同じ。 */
export interface BatchScheduleReloadResult extends BatchScheduleResult {
  /** メモリへの反映に成功したか。 */
  refreshPublished: boolean
  /** 反映に失敗した理由（成功なら null）。 */
  refreshError: string | null
  /** 結果の日本語（失敗しても「前の設定のまま動きます」を返す）。 */
  message: string
}

const http = new HttpClient({ baseUrl: '/api/admin/batch' })

/** バッチ一覧（有効／無効・最新実行・設定充足状態）。 */
export function fetchBatchTasks(): Promise<ApiResponse<BatchTaskListResult>> {
  return http.get<BatchTaskListResult>('/tasks')
}

/** 【再実行】。実行履歴に 1 行追加し、その場で業務処理を実行する。 */
export function rerunBatchTask(batchCode: string, operator?: string): Promise<ApiResponse<BatchRerunResult>> {
  return http.post<BatchRerunResult>(`/tasks/${batchCode}/rerun`, { body: { operator } })
}

/** 有効／無効を切り替える。 */
export function updateBatchActive(
  batchCode: string,
  active: boolean,
  operator?: string
): Promise<ApiResponse<BatchActiveResult>> {
  return http.post<BatchActiveResult>(`/tasks/${batchCode}/active`, { body: { active, operator } })
}

/**
 * いま効いている実行スケジュール（時計・版・タスクごとの実行タイミングと次回実行時刻）。
 *
 * **メモリの設定を返す**（DB を読み直さない）。保存（設定画面）と有効／無効の切替は
 * 自動で反映されるので、DB を直接触ったときだけ【設定を再読み込み】を使う。
 */
export function fetchBatchSchedule(): Promise<ApiResponse<BatchScheduleResult>> {
  return http.get<BatchScheduleResult>('/schedule')
}

/**
 * 設定を DB から読み直してメモリに反映する（管理者の再読み込み）。
 *
 * 反映に失敗しても 200 で返り、`refreshPublished=false` と理由が入る
 * （保存は済んでいるので DB は巻き戻さない。前の有効な設定のまま動く）。
 */
export function reloadBatchSchedule(): Promise<ApiResponse<BatchScheduleReloadResult>> {
  return http.post<BatchScheduleReloadResult>('/schedule/reload', { body: {} })
}

/** 実行履歴（新しい順・ページング）。 */
export function searchBatchExecutions(params: {
  batchCode?: string
  status?: string
  keyword?: string
  page?: number
  size?: number
}): Promise<ApiResponse<BatchExecutionPage>> {
  return http.get<BatchExecutionPage>('/executions', { params })
}

/* ---------------------------------------------------------------------------
 * AI呼出履歴（2.0 の BAT_AI呼出履歴情報 を全件移行したもの）
 * ------------------------------------------------------------------------- */

/** AI 呼び出しの結果（2.0 の '成功' / '失敗'）。 */
export type AiCallResultCode = 'SUCCESS' | 'FAILURE'

export interface AiCallRow {
  callId: number
  executionId: number | null
  batchCode: string
  processKey: string | null
  language: string | null
  aiType: string | null
  modelName: string | null
  callUrl: string | null
  httpStatus: number | null
  startTime: string | null
  endTime: string | null
  durationMs: number | null
  result: AiCallResultCode
  /** 画面表示用のラベル（成功 / 失敗）。 */
  resultLabel: string
  errorCode: string | null
  errorMessage: string | null
  inputTokens: number | null
  outputTokens: number | null
  totalTokens: number | null
}

/** 詳細（一覧の項目 + 本文）。 */
export interface AiCallDetail extends AiCallRow {
  prompt: string | null
  response: string | null
  /** 本文が上限で切られているか（1 行が 200KB 超になることがある）。 */
  bodyTruncated: boolean
  bodyLimit: number
}

export interface AiCallPage {
  items: AiCallRow[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

/** 絞り込みに出す値（実データから作る）。 */
export interface AiCallFilterValues {
  aiTypes: string[]
  batchCodes: string[]
  models: string[]
  results: AiCallResultCode[]
}

/** AI 呼び出し履歴（新しい順・ページング）。一覧では本文を返さない。 */
export function searchAiCalls(params: {
  batchCode?: string
  aiType?: string
  result?: string
  keyword?: string
  startFrom?: string
  startTo?: string
  page?: number
  size?: number
}): Promise<ApiResponse<AiCallPage>> {
  return http.get<AiCallPage>('/ai-calls', { params })
}

/** AI 呼び出し履歴の 1 件（プロンプト・レスポンスの本文を含む）。 */
export function fetchAiCallDetail(callId: number): Promise<ApiResponse<AiCallDetail>> {
  return http.get<AiCallDetail>(`/ai-calls/${callId}`)
}

/** 絞り込みに出す値（AI 区分・バッチコード・モデル名）。 */
export function fetchAiCallFilters(): Promise<ApiResponse<AiCallFilterValues>> {
  return http.get<AiCallFilterValues>('/ai-calls/filters')
}

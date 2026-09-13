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
  /** 業務処理が実装済みで【再実行】できるか */
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

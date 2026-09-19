import { ApiError, HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * AI 生図・AI 画図助手 API（/api/user/geometry/ai）。
 * user-api の `GeometryAiController` と対応する。
 *
 * 契約は**送信 → ポーリング**:
 *   1. 画像を上げる（`POST /requests/images`）→ `imageToken`
 *   2. 要求を作る（`POST /requests`）→ `requestId` と `runPath`
 *   3. `runPath`（admin-api の薄い入口）を 1 回だけ呼ぶ（**AI に 60〜180 秒かかる**）
 *   4. `GET /requests/{id}` を 2 秒間隔でポーリングする（HttpClient の既定タイムアウトは 10 秒）
 *
 * AI の成果物は**GeoGebra のコマンド列**まで。XML とサムネイルは作図画面の applet が作る。
 */
export type GeometryAiKind = 'figure' | 'function' | 'mixed'
export type GeometryAiSubKind = 'triangle' | 'circle' | 'quad' | 'other'
export type GeometryAiFigureType = 'geometry' | 'function'
export type GeometryAiStatus =
  | 'QUEUED' | 'PREPROCESSING' | 'PREPROCESSED' | 'GENERATING' | 'GENERATED' | 'VALIDATING'
  | 'READY' | 'REGISTERED' | 'NEEDS_INPUT' | 'FAILED' | 'CANCELLED'
/** 失敗した工程（画面は工程別の案内を出す）。 */
export type GeometryAiStage = 'PREPROCESS' | 'GENERATE' | 'VALIDATE'

/** 切り抜き範囲（元画像に対する割合 0..1。`features/geometry/geometry-crop.ts` と同じ形）。 */
export interface GeometryAiCrop {
  x: number
  y: number
  w: number
  h: number
}

/** 画面が使う設定（**API Key は返らない**）。 */
export interface GeometryAiOptions {
  enabled: boolean
  assistEnabled: boolean
  maxImageMb: number
  maxImagePixels: number
  defaultCrop: 'manual' | 'center' | 'all'
  defaultKind: GeometryAiKind
  approval: 'manual' | 'auto'
  /** 1 アカウント 1 日の生図回数（0 = 無制限） */
  dailyLimit: number
  usedToday: number
  /** 使えないときの理由（日本語。空なら使える） */
  notice: string
}

export interface GeometryAiUpload {
  imageToken: string
  fileName: string
  mime: string
  size: number
  width: number
  height: number
}

export interface GeometryAiRequestStatus {
  requestId: number
  requestNo: string
  status: GeometryAiStatus
  statusLabel: string
  version: number
  /** admin-api の起動 API（画面が 1 回だけ呼ぶ） */
  runPath: string
}

export interface GeometryAiProposal {
  title: string | null
  tags: string[]
  memo: string | null
  recognized: string | null
}

export interface GeometryAiError {
  stage: GeometryAiStage | null
  stageLabel: string
  code: string | null
  message: string | null
}

export interface GeometryAiExecutionIds {
  preprocess: number | null
  ai: number | null
  validate: number | null
}

/** AI からの質問（追加入力待ちのとき）。 */
export interface GeometryAiQuestion {
  id: string | null
  question: string
  /** 選択肢（空なら自由記述） */
  options: string[]
  /** TEXT / NUMBER / CHOICE / CONFIRM */
  answerKind: string
}

export interface GeometryAiRequestDetail {
  requestId: number
  requestNo: string
  status: GeometryAiStatus
  statusLabel: string
  /** 画面の一覧で使う 8 種類のカード状態（待機中・読み取り中・生成中・検証中・追加入力待ち・確認待ち・失敗・保存完了） */
  cardStatus: string
  cardStatusLabel: string
  /** 作図方法（A〜D。null は歴史的な要求） */
  mode: GeometryAiMode | null
  modeLabel: string | null
  /** 利用者が指定した種類（AUTO を含む） */
  requestedOutputType: GeometryAiOutputType
  /** 実際に作る種類（決められないときは null） */
  resolvedOutputType: 'GEOMETRY' | 'GRAPH' | 'MIXED' | null
  /** 補充パラメータ（日本語の項目名 → 値。**当てはまらない項目は入らない**） */
  supplements: Record<string, string>
  /** AI の判定（GENERATABLE / NEEDS_INPUT / UNSUPPORTED）。実行・保存の結果ではない */
  outcome: string | null
  /** 追加入力待ちのときの質問 */
  questions: GeometryAiQuestion[]
  /** 読み取る範囲（送り直しのときに戻す） */
  crop: GeometryAiCrop | null
  userKind: GeometryAiKind | null
  userSubKind: GeometryAiSubKind | null
  aiKind: string | null
  figureType: GeometryAiFigureType
  note: string | null
  /** READY 以降（失敗した検証では参考として出る） */
  commands: string[]
  proposal: GeometryAiProposal | null
  error: GeometryAiError | null
  validationError: string | null
  executionIds: GeometryAiExecutionIds
  figureId: number | null
  retryCount: number
  version: number
  hasOriginalImage: boolean
  hasCroppedImage: boolean
  retryable: boolean
  cancellable: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface GeometryAiRequestRow {
  requestId: number
  requestNo: string
  status: GeometryAiStatus
  statusLabel: string
  userKind: GeometryAiKind | null
  userSubKind: GeometryAiSubKind | null
  figureType: GeometryAiFigureType
  note: string | null
  failedStage: GeometryAiStage | null
  errorCode: string | null
  errorMessage: string | null
  figureId: number | null
  retryCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface GeometryAiRequestPage {
  items: GeometryAiRequestRow[]
  totalElements: number
  page: number
  size: number
  totalPages: number
}

/** 図形として保存するときの中身（`GeometryFigureSave` と同じ形 + version）。 */
export interface GeometryAiConfirmBody {
  title: string
  figureType: GeometryAiFigureType
  memo?: string
  tags?: string[]
  construction: string
  thumbnail?: string
  version: number
}

export interface GeometryAiConfirmResult {
  request: GeometryAiRequestRow
  figure: {
    figureId: number
    figureNo: string
    title: string
  }
  message: string
}

export type GeometryAiAssistMode = 'append' | 'replace'

export interface GeometryAiAssistResult {
  assistId: number
  commands: string[]
  /** AI が返した日本語の説明 */
  description: string | null
  callId: number | null
}

/**
 * 助手の依頼（**AI はまだ呼ばない**）。
 *
 * 画面はこれを受け取ったら `runPath` を 1 回だけ実行し、
 * `GET /assist/{assistId}` をポーリングして結果を受け取る。
 */
export interface GeometryAiAssistRequest {
  assistId: number
  /** PENDING（依頼受付＝AI 実行待ち）から始まる */
  status: string
  statusLabel: string
  /** admin-api の実行 API（画面が 1 回だけ POST する） */
  runPath: string
}

/** 助手の状態（ポーリングで取る）。 */
export interface GeometryAiAssistStatus {
  assistId: number
  /** PENDING → READY / FAILED */
  status: string
  statusLabel: string
  /** READY のときだけ入る */
  commands: string[]
  description: string | null
  errorCode: string | null
  errorMessage: string | null
  commandCount: number
  /**
   * 指示を出す**前**の作図データ（XML。無ければ null）。
   * 【戻す】はこれへ戻す（端末をまたいだ履歴でも戻せるようにここで取る）。
   */
  beforeXml: string | null
}

/**
 * 助手の履歴の 1 件（会話ログ 1 ステップ）。
 *
 * **端末をまたいで**同じ履歴を見せるために DB（GEO_AI画図指示情報）から読む。
 * `beforeXml`（【戻す】の戻り先）は重いので含まれず、有無だけが来る。
 */
export interface GeometryAiAssistHistoryItem {
  assistId: number
  instruction: string
  /** APPEND / REPLACE */
  mode: string
  /** PENDING / GENERATING / READY / FAILED（AI の生成状態） */
  status: string
  statusLabel: string
  /** SUGGESTED / APPLIED / REJECTED / FAILED（作図に反映したか） */
  applyKind: string
  commands: string[]
  description: string | null
  errorCode: string | null
  errorMessage: string | null
  commandCount: number
  beforeXmlAvailable: boolean
  createdAt: string | null
  updatedAt: string | null
}

export interface GeometryAiAssistHistory {
  items: GeometryAiAssistHistoryItem[]
  count: number
  limit: number
}

/* ---------- 図形一覧に出す AI 生図のタスク ---------- */

/**
 * 一覧のカードの状態（画面のバッジに使う 8 種類）。
 *
 * 処理中の細かい段階は 4 つ（WAITING / READING / GENERATING / VALIDATING）にまとまる。
 * **段階そのものは `statusLabel`**（「画像を読み取り中」「検証中」など）を出す。
 *
 * 「生成できた（`READY`）」と「図形として保存できた（`SAVED`）」は**分ける**。
 * 利用者が一覧で「保存まで終わったのか」を見分けられなければならない。
 */
export type GeometryAiCardStatus =
  | 'WAITING' | 'READING' | 'GENERATING' | 'VALIDATING' | 'NEEDS_INPUT' | 'READY' | 'FAILED' | 'SAVED'

/**
 * 図形管理の一覧に出す AI 生図のタスク 1 件。
 *
 * **図形として保存済（`status` が REGISTERED）はこの API は返さない**。
 * 保存できたものは図形一覧のカードとして出るので、同じものを 2 つ並べない。
 */
export interface GeometryAiTaskRow {
  requestId: number
  requestNo: string
  /**
   * 内部の状態コード（QUEUED / PREPROCESSING / PREPROCESSED / GENERATING / GENERATED /
   * VALIDATING / READY / REGISTERED / NEEDS_INPUT / FAILED / CANCELLED）。
   */
  status: string
  /** 実際の処理段階の日本語（例「画像を読み取り中」「検証中」）。画面はこれをそのまま出す。 */
  statusLabel: string
  cardStatus: GeometryAiCardStatus
  /** カードの状態の日本語（例「待機中」「追加入力待ち」「保存完了」）。 */
  cardStatusLabel: string
  /** 作図モード（A〜D。なし = 歴史的な要求）。 */
  mode: 'A' | 'B' | 'C' | 'D' | null
  /** 作図モードの日本語（例「画像をもとに再現」）。分からなければ null。 */
  modeLabel: string | null
  /** 利用者が指定した、作成する図の種類（AUTO = おまかせ）。 */
  requestedOutputType: 'AUTO' | 'GEOMETRY' | 'GRAPH' | 'MIXED'
  /** 実際に作る種類（決まらないうちは null）。 */
  resolvedOutputType: 'GEOMETRY' | 'GRAPH' | 'MIXED' | null
  title: string | null
  description: string | null
  /** 追加入力待ちの質問の件数（カードに「質問 N 件」と出す）。 */
  questionCount: number
  /** 完了したタスクが作った図形（まだ無ければ null）。 */
  figureId: number | null
  /** 楽観的ロックの版数（【削除】などカードからの操作に使う）。 */
  version: number
  /** 失敗した工程（PREPROCESS / GENERATE / VALIDATE。成功なら null）。 */
  failedStage: string | null
  errorCode: string | null
  /** 失敗の理由（日本語。**隠さずそのまま画面に出す**）。 */
  errorMessage: string | null
  hasCroppedImage: boolean
  retryCount: number
  createdAt: string | null
  updatedAt: string | null
}

export interface GeometryAiTaskList {
  items: GeometryAiTaskRow[]
  totalElements: number
  limit: number
}

/** タスク一覧の既定の件数（サーバーの既定と同じ 20 件）。 */
export const DEFAULT_GEOMETRY_AI_TASK_LIMIT = 20

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため）
const http = new HttpClient({ baseUrl: '/api/user/geometry/ai' })
/**
 * バッチ起動（admin-api）用。
 * 起動 API は admin-api のパス（`/api/admin/batch/...`）なので、user-api 用の baseUrl では
 * 前置きが余計に付いてしまう。**baseUrl を空にした別のクライアント**で叩く。
 */
const adminHttp = new HttpClient({ baseUrl: '' })

/** 画面が使う設定。 */
export function fetchGeometryAiOptions(): Promise<ApiResponse<GeometryAiOptions>> {
  return http.get<GeometryAiOptions>('/options')
}

/**
 * 元画像をアップロードする。
 *
 * HttpClient は body を JSON にするため、FormData は fetch を直接使う
 * （Content-Type を自分で付けない＝ブラウザに boundary を付けさせる）。
 */
export async function uploadGeometryAiImage(file: File): Promise<ApiResponse<GeometryAiUpload>> {
  const form = new FormData()
  form.append('file', file, file.name)
  let response: Response
  try {
    response = await fetch('/api/user/geometry/ai/requests/images', { method: 'POST', body: form })
  } catch (cause) {
    throw new ApiError({ code: 'NETWORK_ERROR', message: 'ネットワークエラーが発生しました', cause })
  }
  const payload = (await response.json().catch(() => null)) as ApiResponse<GeometryAiUpload> | null
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
 * 補充パラメータ（画面がモードと種類に応じて出す任意の項目）。
 *
 * **当てはまらない項目は送らない**（`geometry-ai-options.ts` の `toSupplementPayload` が絞る）。
 * サーバー側でも同じ規則で絞るので、古い画面が余計な項目を送っても保存されない。
 */
export interface GeometryAiSupplements {
  reproduceFocus?: string | null
  whenInsufficient?: string | null
  knownValues?: string | null
  coordinateRange?: string | null
  formulaCorrection?: string | null
  parameters?: string | null
  domain?: string | null
  viewRange?: string | null
  showAuxiliary?: boolean | null
  goal?: string | null
  problemCorrection?: string | null
  keepObjects?: string | null
  changeObjects?: string | null
  textCorrection?: string | null
  keepLabels?: boolean | null
}

/** 作図方法（A〜D）。 */
export type GeometryAiMode = 'A' | 'B' | 'C' | 'D'
/** 作成する図の種類（AUTO が既定）。 */
export type GeometryAiOutputType = 'AUTO' | 'GEOMETRY' | 'GRAPH' | 'MIXED'

/**
 * 要求を作る（状態 = QUEUED）。
 *
 * `mode` が「入力をどう読むか」、`resultType` が「何を作るか」を決める。
 * **実行はバックエンドの働き手が行う**ので、画面は `runPath` を呼ばない。
 */
export function createGeometryAiRequest(body: {
  imageToken: string
  crop: GeometryAiCrop
  mode: GeometryAiMode
  resultType: GeometryAiOutputType
  supplements?: GeometryAiSupplements | null
  note?: string
}): Promise<ApiResponse<GeometryAiRequestStatus>> {
  return http.post<GeometryAiRequestStatus>('/requests', { body })
}

/**
 * 追加入力待ち・失敗した要求を**条件を直して送り直す**（同じ要求行を使い回す）。
 *
 * 画像はもうサーバーにあるので送り直さない。読み取る範囲・作図方法・種類・補充・補足要求を
 * 更新して `QUEUED` に戻す（実行はバックエンドの働き手）。
 */
export function resubmitGeometryAiRequest(requestId: number, body: {
  crop: GeometryAiCrop
  mode: GeometryAiMode
  resultType: GeometryAiOutputType
  supplements?: GeometryAiSupplements | null
  note?: string
  version: number
}): Promise<ApiResponse<GeometryAiRequestStatus>> {
  return http.post<GeometryAiRequestStatus>(`/requests/${requestId}/resubmit`, { body })
}

/** 自分の履歴（生成コマンドと画像は返らない）。 */
export function fetchGeometryAiRequests(params: {
  status?: string
  page?: number
  size?: number
} = {}): Promise<ApiResponse<GeometryAiRequestPage>> {
  return http.get<GeometryAiRequestPage>('/requests', { params })
}

/** 1 件（ポーリングの主対象）。 */
export function fetchGeometryAiRequest(requestId: number): Promise<ApiResponse<GeometryAiRequestDetail>> {
  return http.get<GeometryAiRequestDetail>(`/requests/${requestId}`)
}

/** 画像の URL（img src にそのまま使う）。 */
export function geometryAiImageUrl(requestId: number, kind: 'original' | 'cropped', version = 0): string {
  return `/api/user/geometry/ai/requests/${requestId}/image?kind=${kind}&v=${version}`
}

/** 【もう一度生成】（AI の成果物を消して前処理済みへ戻す。回数分だけ課金される）。 */
export function retryGeometryAiRequest(
  requestId: number, version: number
): Promise<ApiResponse<GeometryAiRequestStatus>> {
  return http.post<GeometryAiRequestStatus>(`/requests/${requestId}/retry`, { body: { version } })
}

/** 取消（QUEUED / PREPROCESSED のみ）。 */
export function cancelGeometryAiRequest(
  requestId: number, version: number
): Promise<ApiResponse<GeometryAiRequestStatus>> {
  return http.post<GeometryAiRequestStatus>(`/requests/${requestId}/cancel`, { body: { version } })
}

/**
 * タスクを**一覧から消す**（状態を取消にする。行は監査のためサーバーに残る）。
 *
 * 【取消】と違ってどの状態でも消せる（生成済み・失敗・追加入力待ちを片付けられる）。
 * 図形として保存済みのものは消せない（図形一覧から削除する）。
 */
export function discardGeometryAiRequest(
  requestId: number, version: number
): Promise<ApiResponse<GeometryAiRequestStatus>> {
  return http.post<GeometryAiRequestStatus>(`/requests/${requestId}/discard`, { body: { version } })
}

/** 図形として保存（作図画面で確認・調整したあと。登録元コード = 'AI'）。 */
export function confirmGeometryAiRequest(
  requestId: number, body: GeometryAiConfirmBody
): Promise<ApiResponse<GeometryAiConfirmResult>> {
  return http.post<GeometryAiConfirmResult>(`/requests/${requestId}/confirm`, { body })
}

/**
 * AI 生図のパイプラインを起動する（admin-api の薄い入口）。
 *
 * AI を呼ぶバッチは **作図モードごとの 4 つ（batC51-A〜D）だけ**
 * （前処理・検証はバックエンドの通常コード。実行履歴に出ない）。
 *
 * **AI に 60〜180 秒かかる**ので、タイムアウトを延ばして呼ぶ。画面はこの結果を待たずに
 * ポーリングを続ける（タイムアウトしてもポーリングが真の状態を返す）。
 */
export function runGeometryAiPipeline(runPath: string, timeoutMs = 600_000): Promise<ApiResponse<unknown>> {
  return adminHttp.post<unknown>(runPath, { body: { operator: 'geometry-ai-view' }, timeoutMs })
}

/**
 * 図形管理の一覧に出す AI 生図のタスク（自分の分だけ・新しい順）。
 *
 * 送信するとすぐ一覧へ戻り、この API のタスクカードとして**実際の処理段階**が出る
 * （進み具合の％は持たない・出さない）。処理中のカードがある間だけ 3 秒間隔で取り直す。
 */
export function fetchGeometryAiTasks(limit?: number): Promise<ApiResponse<GeometryAiTaskList>> {
  const params = { limit: limit ?? DEFAULT_GEOMETRY_AI_TASK_LIMIT }
  return http.get<GeometryAiTaskList>('/tasks', { params })
}

/* ---------- AI 画図助手（作図画面） ---------- */

/**
 * 助手の依頼を作る（**AI は呼ばない**。実行は `runPath`）。
 *
 * 画面は「① 依頼 → ② run を 1 回 → ③ 状態をポーリング」の順で使う
 * （AI が 60〜180 秒かかるため、同期 1 往復にしない）。
 */
export function createGeometryAiAssist(body: {
  figureId?: number | null
  construction: string
  instruction: string
  mode: GeometryAiAssistMode
  /**
   * いまの作図のオブジェクト一覧（「名前 = 定義（型）」の改行区切り）。
   *
   * XML にはコマンドで作った図形の定義が残らないことがある（実測: 円は種類と行列だけ）ので、
   * 画面が applet から取った定義を渡す（AI が中心・半径を推測しなくて済む）。
   */
  objects?: string | null
  /**
   * 前の案が実行できなかった内容（実行できなかった行と理由）。
   *
   * 画面は実行に失敗すると、同じ指示をこれつきで**もう一度だけ**送る（AI が自分で直す）。
   */
  failure?: string | null
}): Promise<ApiResponse<GeometryAiAssistRequest>> {
  return http.post<GeometryAiAssistRequest>('/assist', { body })
}

/** 助手の状態を取る（ポーリング。1.5〜2 秒間隔で呼ぶ）。 */
export function fetchGeometryAiAssist(assistId: number): Promise<ApiResponse<GeometryAiAssistStatus>> {
  return http.get<GeometryAiAssistStatus>(`/assist/${assistId}`)
}

/**
 * 助手の実行を起動する（admin-api の薄い入口。batC52 をその場で同期実行する）。
 *
 * 戻り値は使わない（**状態はポーリングで取る**のが契約）。`?async` は付けない。
 */
export function runGeometryAiAssist(runPath: string, timeoutMs = 600_000): Promise<ApiResponse<unknown>> {
  return adminHttp.post<unknown>(runPath, { body: { operator: 'geometry-draw-view' }, timeoutMs })
}

/** 【反映】の記録（作図への適用はブラウザの applet が行う）。 */
export function markGeometryAiAssistApplied(
  assistId: number, mode: GeometryAiAssistMode
): Promise<ApiResponse<GeometryAiAssistResult>> {
  return http.post<GeometryAiAssistResult>(`/assist/${assistId}/applied`, { body: { mode } })
}

/**
 * 図形ごとの指示履歴（古い順。自分が作った行だけ）。
 *
 * 会話ログを端末をまたいで見るための入口（`localStorage` だけだと他の端末で見えない）。
 */
export function loadGeometryAiAssistHistory(
  figureId: number, limit?: number
): Promise<ApiResponse<GeometryAiAssistHistory>> {
  return http.get<GeometryAiAssistHistory>('/assist', {
    params: limit === undefined ? { figureId } : { figureId, limit }
  })
}

/**
 * 変更案を**作図に反映しなかった**記録（利用者の【破棄】、または反映できなかったとき）。
 *
 * `reason`（どの行で失敗したか）を渡すと DB に残り、別の端末の履歴でも理由が見える。
 */
export function discardGeometryAiAssist(
  assistId: number, reason?: string
): Promise<ApiResponse<GeometryAiAssistResult>> {
  return http.post<GeometryAiAssistResult>(`/assist/${assistId}/discarded`,
    { body: reason === undefined ? {} : { reason } })
}

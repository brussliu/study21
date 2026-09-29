import { ApiError, HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * 英作文AI添削 API（user-api `/api/user/english-essays` と admin-api `/api/admin/batch/english-essay`）。
 *
 * <p>2026-09-27 まで画面は localStorage の見本データだけで動いていた（純前端）。ここは
 * その保管と AI を**本物の API** に繋ぐ層で、DB（`ENG_*`）と 2.0 の AI 添削に対応する。</p>
 *
 * <ul>
 *   <li>user-api: 作文と画像の保管、添削の**受付**、**同期 OCR**（要ログイン。**自分の作文だけ**）</li>
 *   <li>admin-api: 文字認識と添削の**実行**（働き手）。画面からは直接呼ばない
 *       （合言葉つきで user-api から呼ばれる内部入口）</li>
 * </ul>
 *
 * <p>multipart は {@link HttpClient} が扱えない（JSON 専用）ので、既存の `api/testinfo.ts` /
 * `api/tempfiles.ts` と同じく `fetch` + {@link ApiError} で書く。</p>
 */

/** 添削の状態（API の `statusCode`）。 */
export type EssayGradingStatusCode = 'QUEUED' | 'RUNNING' | 'SUCCEEDED' | 'FAILED' | 'CANCELED'

/** 画像の区分（利用者が 1 枚ずつ指定する）。 */
export type EssayImageCategory = 'question' | 'answer'

/** 一覧の 1 行に付く最新の添削（**中身は返らない**。状態と得点だけ）。 */
export interface EnglishEssayLatestGrading {
  gradingId: string
  round: number
  statusCode: string
  score: number | null
  maxScore: number | null
  createdAt: string
}

/** 一覧の 1 行（`GET /api/user/english-essays`）。 */
export interface EnglishEssayListItem {
  essayId: string
  level: string
  title: string
  titleZh: string | null
  questionText: string
  essayText: string
  wordCount: number
  imageCount: number
  questionImageCount: number
  answerImageCount: number
  createdAt: string
  updatedAt: string
  latestGrading: EnglishEssayLatestGrading | null
}

/** 一覧のページ（絞り込み・ページングは**サーバー側**）。 */
export interface EnglishEssayPage {
  items: EnglishEssayListItem[]
  total: number
  page: number
  size: number
  totalPages: number
}

/**
 * 画像の上限（`GET /api/user/english-essays/limits`）。
 *
 * <p>上限は**サーバーが決める**（画面は持たない。2 か所に書くとずれる）。画面は取れた値で
 * 先に知らせるだけにして、最終的な判定はサーバーに任せる。</p>
 */
export interface EnglishEssayLimits {
  /** 1 件の作文に付けられる画像の枚数。 */
  maxImages: number
  /** 画像 1 枚の大きさ（MB）。 */
  maxImageMb: number
}

/** 作文に付いた画像（詳細で返る形）。 */
export interface EnglishEssayImage {
  imageId: string
  order: number
  category: string
  originalFileName: string
  mimeType: string
  fileSize: number
  recognizedText: string | null
  confidence: number | null
}

/**
 * 添削 1 回ぶん（詳細で返る形）。
 *
 * <p>`report` は 2.0 と同形の JSON（レポート本文）。形はバックエンドの産出が規範なので、
 * ここでは `unknown` のまま持ち、`store` が画面の形（`EssayGrading`）へ組み立てる。</p>
 */
export interface EnglishEssayGrading {
  gradingId: string
  round: number
  statusCode: string
  level: string | null
  titleJa: string | null
  titleZh: string | null
  questionText: string | null
  essayText: string | null
  wordCount: number | null
  score: number | null
  maxScore: number | null
  report: unknown
  failureReason: string | null
  startedAt: string | null
  finishedAt: string | null
  createdAt: string
}

/** 作文 1 件の詳細（作成・更新・取得で同じ形）。 */
export interface EnglishEssayDetail {
  essayId: string
  level: string
  title: string
  titleZh: string | null
  questionText: string
  essayText: string
  wordCount: number
  /** `A` = 有効 / `X` = 削除（論理削除。2.0 と同じ）。 */
  stateCode: string
  createdAt: string
  updatedAt: string
  images: EnglishEssayImage[]
  gradings: EnglishEssayGrading[]
}

/** 作文の本文（作成・更新で送る）。 */
export interface EnglishEssaySave {
  level: string
  title: string
  titleZh: string
  questionText: string
  essayText: string
}

/** 残す画像 1 枚（並びと区分の更新。**消した画像は含めない**）。 */
export interface EnglishEssayImageOrder {
  imageId: string
  category: EssayImageCategory
  order: number
}

/** 作文の更新（本文 ＋ 残す画像の一覧）。 */
export interface EnglishEssayUpdate extends EnglishEssaySave {
  images: EnglishEssayImageOrder[]
}

/** 検索条件（2.0 の一覧と同じ 4 つ ＋ ページング）。 */
export interface EnglishEssayQuery {
  keyword?: string
  level?: string
  dateFrom?: string
  dateTo?: string
  page?: number
  size?: number
}

/** 文字認識に渡す画像（**ファイルの実体**。区分はファイルと同じ順で送る）。 */
export interface EssayOcrImage {
  file: File
  category: EssayImageCategory
}

/** 画像 1 枚ぶんの認識結果。 */
export interface EssayOcrPage {
  category: string
  text: string
  confidence: number
}

/** 文字認識の結果（**同期**で返る）。 */
export interface EssayOcrResult {
  questionText: string
  essayText: string
  pages: EssayOcrPage[]
  /** 設問画像だけの平均の信頼度（%）。 */
  questionConfidence: number
  /** 答案画像だけの平均の信頼度（%）。 */
  essayConfidence: number
}

/**
 * 添削の受付の結果（`POST /api/user/english-essays/{essayId}/gradings`）。実行は働き手が行う。
 */
export interface EssayGradingAccepted {
  gradingId: string
  round: number
  message: string
}

/** 画像 1 枚のアップロードの入力。 */
export interface EssayImageUpload {
  file: File
  category: EssayImageCategory
  order: number
}

// パスは他機能と同じ書き方に合わせる（末尾スラッシュ付きの URL は Spring 側で 404 になるため、
// baseUrl は `/api/user` までにして、パスは必ず `/english-essays` から始める）
const http = new HttpClient({ baseUrl: '/api/user' })

/**
 * 文字認識の待ち時間（ミリ秒）。
 *
 * <p>OCR は**同期**なので、画像 8 枚を渡すと数十秒かかる。既定の 10 秒では足りないので
 * 明示する（利用者の指示 2026-09-27。60 秒以上にすること）。**user-api 側の上限（既定 180 秒）
 * より長く**しておく: ブラウザが先に諦めると、AI を使ったのに画面には失敗だけが残る。</p>
 */
export const ESSAY_OCR_TIMEOUT_MS = 200_000

/** 画像アップロードの待ち時間（ミリ秒）。1 枚 10MB までなので既定の 10 秒では足りない。 */
export const ESSAY_IMAGE_UPLOAD_TIMEOUT_MS = 60_000

/** 一覧（絞り込み・ページングはサーバーが行う）。 */
export function searchEnglishEssays(params: EnglishEssayQuery = {}): Promise<ApiResponse<EnglishEssayPage>> {
  return http.get<EnglishEssayPage>('/english-essays', {
    params: {
      keyword: params.keyword,
      level: params.level,
      dateFrom: params.dateFrom,
      dateTo: params.dateTo,
      page: params.page,
      size: params.size
    }
  })
}

/** 1 件の詳細。 */
export function fetchEnglishEssay(essayId: string): Promise<ApiResponse<EnglishEssayDetail>> {
  return http.get<EnglishEssayDetail>(`/english-essays/${encodeURIComponent(essayId)}`)
}

/**
 * 画像の上限（枚数・1 枚の大きさ）を読む。
 *
 * <p>取れないときは呼ぶ側が「不明」として扱い、**操作は止めない**（サーバーが最終的に拒否して
 * 日本語の理由を返す）。</p>
 */
export function fetchEnglishEssayLimits(): Promise<ApiResponse<EnglishEssayLimits>> {
  return http.get<EnglishEssayLimits>('/english-essays/limits')
}

/** 新規作成（画像は付かない。画像は作成後に 1 枚ずつ上げる）。 */
export function createEnglishEssay(body: EnglishEssaySave): Promise<ApiResponse<EnglishEssayDetail>> {
  return http.post<EnglishEssayDetail>('/english-essays', { body })
}

/** 更新（本文と、**残す画像**の並び・区分）。 */
export function updateEnglishEssay(
  essayId: string,
  body: EnglishEssayUpdate
): Promise<ApiResponse<EnglishEssayDetail>> {
  return http.put<EnglishEssayDetail>(`/english-essays/${encodeURIComponent(essayId)}`, { body })
}

/** 削除（論理削除。204 が返る）。 */
export function deleteEnglishEssay(essayId: string): Promise<ApiResponse<void>> {
  return http.delete<void>(`/english-essays/${encodeURIComponent(essayId)}`)
}

/** 画面に出す画像の URL（`<img :src>` にそのまま使う）。 */
export function englishEssayImageUrl(essayId: string, imageId: string): string {
  return `/api/user/english-essays/${encodeURIComponent(essayId)}/images/${encodeURIComponent(imageId)}`
}

/** 画像を 1 枚上げる（multipart。`file` ＋ `category` ＋ `order`）。 */
export function uploadEnglishEssayImage(
  essayId: string,
  input: EssayImageUpload
): Promise<ApiResponse<EnglishEssayImage>> {
  const form = new FormData()
  form.append('file', input.file)
  form.append('category', input.category)
  form.append('order', String(input.order))
  return fetchMultipart<EnglishEssayImage>(
    `/api/user/english-essays/${encodeURIComponent(essayId)}/images`,
    form,
    ESSAY_IMAGE_UPLOAD_TIMEOUT_MS
  )
}

/**
 * 画像を文字にする（**user-api**。同期。1〜8 枚）。
 *
 * <p>入口は `POST /api/user/english-essays/ocr`（要ログイン）。**作文IDは要らない**（編集中の画像を
 * その場で送る）。user-api が合言葉つきで admin-api の内部入口へ転送する（admin-api を画面から
 * 直接叩かない: URL を知っているだけで誰でも AI を呼べてしまうため）。</p>
 *
 * <p>`categories` は**ファイルと同じ順**のカンマ区切りで送る（AI が設問と答案を分ける手がかり）。</p>
 */
export function recognizeEssayImages(
  level: string,
  images: readonly EssayOcrImage[]
): Promise<ApiResponse<EssayOcrResult>> {
  const form = new FormData()
  form.append('level', level)
  form.append('categories', images.map((image) => image.category).join(','))
  images.forEach((image) => form.append('files', image.file))
  return fetchMultipart<EssayOcrResult>('/api/user/english-essays/ocr', form, ESSAY_OCR_TIMEOUT_MS)
}

/**
 * 添削を**受付ける**（実行はバックエンドの働き手。すぐ返る）。
 *
 * <p>入口は **user-api**（`POST /api/user/english-essays/{essayId}/gradings`。要ログイン）。
 * `round` は**任意**で、渡さないときは**キーごと送らない**（次の回はサーバーが決める。画面で
 * 数えると、受付が重なったときにずれる）。他人・存在しない・削除済みの作文は 404 になる。</p>
 */
export function requestEssayGrading(
  essayId: string,
  round?: number
): Promise<ApiResponse<EssayGradingAccepted>> {
  return http.post<EssayGradingAccepted>(
    `/english-essays/${encodeURIComponent(essayId)}/gradings`,
    { body: round === undefined ? {} : { round } }
  )
}

/**
 * multipart の POST（`HttpClient` は JSON 専用なのでここだけ `fetch` を使う）。
 *
 * <p>タイムアウトは `AbortController` で自前にかける（同期の OCR は分単位かかる）。</p>
 */
async function fetchMultipart<T>(
  url: string,
  body: FormData,
  timeoutMs?: number
): Promise<ApiResponse<T>> {
  const controller = new AbortController()
  const timer = timeoutMs === undefined ? null : setTimeout(() => controller.abort(), timeoutMs)
  let response: Response
  try {
    response = await fetch(url, { method: 'POST', body, signal: controller.signal })
  } catch (cause) {
    if (controller.signal.aborted) {
      throw new ApiError({ code: 'TIMEOUT', message: 'リクエストがタイムアウトしました', cause })
    }
    throw new ApiError({ code: 'NETWORK_ERROR', message: 'ネットワークエラーが発生しました', cause })
  } finally {
    if (timer !== null) {
      clearTimeout(timer)
    }
  }
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null
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

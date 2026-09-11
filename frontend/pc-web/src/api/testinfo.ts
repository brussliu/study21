import { ApiError, HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * テスト情報管理 API（/api/user）。型とエンドポイントは user-api の TestInfoController と対応する。
 * 親が同じ契約でバックエンドを実装するため、ここは凍結された契約どおりに保つ。
 */

/** テストに添付された試験用紙ファイル。 */
export interface TestFile {
  fileId: number
  displayOrder: number
  originalFileName: string
  extension: string
  mimeType: string | null
  fileSize: number | null
  comment: string | null
  image: boolean
  contentUrl: string
  previewUrl: string | null
}

/** 一覧 1 行分のテスト情報。 */
export interface TestRow {
  testNo: string
  testName: string
  subject: string
  kind: string
  examDate: string | null
  score: number | null
  fullScore: number | null
  accuracyText: string
  memo: string | null
  version: number
  photoCount: number
  files: TestFile[]
  createdAt: string | null
  updatedAt: string | null
}

export interface TestInfoWorkspace {
  rows: TestRow[]
}

export interface TestInfoQuery {
  subject?: string
  kind?: string
  keyword?: string
}

/** 登録・更新で共通のテスト項目。未入力は null で送る（空文字は送らない）。 */
export interface SaveTestInfoRequest {
  testName: string
  subject: string
  kind: string
  examDate: string | null
  score: number | null
  fullScore: number | null
  memo: string | null
}

/** 更新時の既存ファイル 1 件分（表示順と備考）。 */
export interface TestFileOrderRequest {
  fileId: number
  displayOrder: number
  comment: string | null
}

export interface UpdateTestInfoRequest extends SaveTestInfoRequest {
  version: number
  files: TestFileOrderRequest[]
}

const http = new HttpClient({ baseUrl: '/api/user' })

export const getTestInfos = (query: TestInfoQuery = {}): Promise<ApiResponse<TestInfoWorkspace>> =>
  http.get('/test-infos', {
    params: {
      subject: query.subject?.trim() || undefined,
      kind: query.kind?.trim() || undefined,
      keyword: query.keyword?.trim() || undefined
    }
  })

/** テスト新規登録（metadata + ファイル 0..10）。 */
export async function createTestInfo(
  body: SaveTestInfoRequest,
  files: File[]
): Promise<ApiResponse<{ row: TestRow }>> {
  const form = new FormData()
  form.append('metadata', new Blob([JSON.stringify(body)], { type: 'application/json' }))
  files.forEach((file) => form.append('files', file))
  return fetchMultipart('/api/user/test-infos', form)
}

/** テスト更新（version 不一致は 409）。 */
export const updateTestInfo = (
  testNo: string,
  body: UpdateTestInfoRequest
): Promise<ApiResponse<{ row: TestRow }>> =>
  http.put(`/test-infos/${encodeURIComponent(testNo)}`, { body })

export const deleteTestInfo = (testNo: string): Promise<ApiResponse<{ deleted: boolean }>> =>
  http.delete(`/test-infos/${encodeURIComponent(testNo)}`)

/** 既存テストへファイルを追加（合計 10 件超はエラー）。 */
export async function uploadTestFiles(
  testNo: string,
  files: File[]
): Promise<ApiResponse<{ row: TestRow }>> {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))
  return fetchMultipart(`/api/user/test-infos/${encodeURIComponent(testNo)}/files`, form)
}

/** 臨時ファイルから取り込む。 */
export const addTestFileFromTempFile = (
  testNo: string,
  tempFileId: number
): Promise<ApiResponse<{ row: TestRow }>> =>
  http.post(`/test-infos/${encodeURIComponent(testNo)}/files/from-temp-file`, { body: { tempFileId } })

/** 他のテストのファイルから取り込む。 */
export const addTestFileFromTestFile = (
  testNo: string,
  sourceTestNo: string,
  sourceFileId: number
): Promise<ApiResponse<{ row: TestRow }>> =>
  http.post(`/test-infos/${encodeURIComponent(testNo)}/files/from-test-file`, {
    body: { sourceTestNo, sourceFileId }
  })

/** 回転・注釈を焼き込んだ画像で置き換える。 */
export async function saveTestFileImage(
  testNo: string,
  fileId: number,
  image: Blob
): Promise<ApiResponse<{ row: TestRow }>> {
  const form = new FormData()
  form.append('image', image, `test-file-${fileId}.png`)
  return fetchMultipart(`/api/user/test-infos/${encodeURIComponent(testNo)}/files/${fileId}/image`, form)
}

export const deleteTestFile = (testNo: string, fileId: number): Promise<ApiResponse<{ row: TestRow }>> =>
  http.delete(`/test-infos/${encodeURIComponent(testNo)}/files/${fileId}`)

/** ダウンロード用 URL（contentUrl に download=true を付ける）。 */
export function testFileDownloadUrl(file: TestFile): string {
  return `${file.contentUrl}${file.contentUrl.includes('?') ? '&' : '?'}download=true`
}

async function fetchMultipart<T>(url: string, body: FormData): Promise<ApiResponse<T>> {
  let response: Response
  try { response = await fetch(url, { method: 'POST', body }) }
  catch (cause) { throw new ApiError({ code: 'NETWORK_ERROR', message: 'ネットワークエラーが発生しました', cause }) }
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

import { ApiError, HttpClient, type ApiResponse } from '@study21/web-shared'

export interface TempFileSummary {
  tempFileId: number
  originalFileName: string
  extension: string | null
  mimeType: string | null
  fileSize: number
  comment: string | null
  thumbnail: string | null
  image: boolean
  contentUrl: string
  createdAt: string | null
  updatedAt: string | null
}

export interface UpdateTempFileRequest {
  originalFileName: string
  comment: string | null
}

export interface TempFileQuery {
  keyword?: string
  type?: '画像' | 'その他'
  period?: string
  limit?: number
}

const http = new HttpClient({ baseUrl: '/api/user' })

export const getTempFiles = (query: TempFileQuery = {}): Promise<ApiResponse<TempFileSummary[]>> =>
  http.get('/temp-files', {
    params: {
      keyword: query.keyword?.trim() || undefined,
      type: query.type ?? undefined,
      period: query.period && query.period !== 'すべて' ? query.period : undefined,
      limit: query.limit ?? 300
    }
  })

export const updateTempFile = (id: number, body: UpdateTempFileRequest): Promise<ApiResponse<TempFileSummary>> =>
  http.put(`/temp-files/${id}`, { body })

export const deleteTempFile = (id: number): Promise<ApiResponse<void>> => http.delete(`/temp-files/${id}`)

export const deleteTempFiles = (ids: number[]): Promise<ApiResponse<void>> =>
  http.post('/temp-files/batch-delete', { body: { ids } })

export async function uploadTempFiles(files: File[], comment?: string): Promise<ApiResponse<TempFileSummary[]>> {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))
  if (comment?.trim()) form.append('comment', comment.trim())
  return fetchMultipart('/api/user/temp-files', form)
}

export async function replaceTempFileImage(
  id: number,
  file: File,
  originalFileName?: string,
  comment?: string
): Promise<ApiResponse<TempFileSummary>> {
  const form = new FormData()
  form.append('file', file)
  if (originalFileName?.trim()) form.append('originalFileName', originalFileName.trim())
  if (comment != null) form.append('comment', comment)
  return fetchMultipart(`/api/user/temp-files/${id}/image`, form)
}

export async function saveTempFileImageAs(
  id: number,
  file: File,
  originalFileName?: string,
  comment?: string
): Promise<ApiResponse<TempFileSummary>> {
  const form = new FormData()
  form.append('file', file)
  if (originalFileName?.trim()) form.append('originalFileName', originalFileName.trim())
  if (comment != null) form.append('comment', comment)
  return fetchMultipart(`/api/user/temp-files/${id}/save-as`, form)
}

async function fetchMultipart<T>(url: string, body: FormData): Promise<ApiResponse<T>> {
  let response: Response
  try { response = await fetch(url, { method: 'POST', body }) }
  catch (cause) { throw new ApiError({ code: 'NETWORK_ERROR', message: 'ネットワークエラーが発生しました', cause }) }
  const payload = (await response.json().catch(() => null)) as ApiResponse<T> | null
  if (!response.ok || !payload?.success) {
    throw new ApiError({ code: payload?.code ?? 'INTERNAL_ERROR', message: payload?.message ?? `HTTP ${response.status}`, status: response.status, traceId: payload?.traceId })
  }
  return payload
}

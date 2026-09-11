import { ApiError, HttpClient, type ApiResponse } from '@study21/web-shared'

export interface DocumentFolder { folderId: number; parentFolderId: number | null; folderName: string; displayOrder: number; note: string | null }
export interface DocumentSummary {
  documentNo: string; folderId: number | null; status: '0' | '1'; expiryDate: string | null
  largeCategory: string | null; mediumCategory: string | null; smallCategory: string | null
  detailCategory: string | null; comment: string | null; fileCount: number; files: DocumentFileInfo[]
  createdAt: string | null; updatedAt: string | null
}
export interface DocumentFileInfo { branchNo: number; originalFileName: string; extension: string; comment: string | null; contentUrl: string; image: boolean }
export interface DocumentWorkspace { folders: DocumentFolder[]; documents: DocumentSummary[] }
export interface SaveDocumentRequest {
  folderId: number | null; status: '0' | '1'; expiryDate: string | null; comment: string | null
}
export interface SaveFolderRequest { parentFolderId: number | null; folderName: string; displayOrder: number; note: string | null }

const http = new HttpClient({ baseUrl: '/api/user' })

export const getDocumentWorkspace = (folderId?: number | null, keyword?: string): Promise<ApiResponse<DocumentWorkspace>> =>
  http.get('/documents', { params: { folderId: folderId ?? undefined, keyword: keyword?.trim() || undefined } })
export const updateDocument = (no: string, body: SaveDocumentRequest): Promise<ApiResponse<DocumentSummary>> =>
  http.put(`/documents/${encodeURIComponent(no)}`, { body })
export const deleteDocument = (no: string): Promise<ApiResponse<void>> => http.delete(`/documents/${encodeURIComponent(no)}`)
export const getDocumentFiles = (no: string): Promise<ApiResponse<DocumentFileInfo[]>> => http.get(`/documents/${encodeURIComponent(no)}/files`)
export const deleteDocumentFile = (no: string, branchNo: number): Promise<ApiResponse<void>> => http.delete(`/documents/${encodeURIComponent(no)}/files/${branchNo}`)
export const createFolder = (body: SaveFolderRequest): Promise<ApiResponse<DocumentFolder>> => http.post('/document-folders', { body })
export const updateFolder = (id: number, body: SaveFolderRequest): Promise<ApiResponse<DocumentFolder>> => http.put(`/document-folders/${id}`, { body })
export const deleteFolder = (id: number): Promise<ApiResponse<void>> => http.delete(`/document-folders/${id}`)

export async function createDocument(body: SaveDocumentRequest, files: File[]): Promise<ApiResponse<DocumentSummary>> {
  const form = new FormData()
  form.append('metadata', new Blob([JSON.stringify(body)], { type: 'application/json' }))
  files.forEach((file) => form.append('files', file))
  return fetchMultipart('/api/user/documents', form)
}

export async function uploadDocumentFiles(no: string, files: File[]): Promise<ApiResponse<DocumentFileInfo[]>> {
  const form = new FormData()
  files.forEach((file) => form.append('files', file))
  return fetchMultipart(`/api/user/documents/${encodeURIComponent(no)}/files`, form)
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

/**
 * 統一 API エラー。HTTP クライアント層が送出する。
 * `code` はバックエンドの ErrorCode またはクライアント側の ClientErrorCode。
 */
export interface ApiErrorInit {
  code: string
  message: string
  status?: number
  traceId?: string
  /** エラーレスポンスボディの data（例: 登録時の衝突フィールド { field: 'studentEmail' }） */
  data?: unknown
  cause?: unknown
}

export class ApiError extends Error {
  readonly code: string
  readonly status?: number
  readonly traceId?: string
  readonly data?: unknown

  constructor(init: ApiErrorInit) {
    super(init.message, init.cause === undefined ? undefined : { cause: init.cause })
    this.name = 'ApiError'
    this.code = init.code
    this.status = init.status
    this.traceId = init.traceId
    this.data = init.data
  }
}

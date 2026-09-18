import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * 保護者・生徒・管理者アカウントの登録 / ログイン API（/api/user）。
 * 型とエンドポイントは user-api の AccountController と対応する。
 *
 * 2026-09-14 の決定（Q8）: 管理者も user-api のログインを使う
 * （読書管理の【全体書籍】を管理するため。管理者の有効期限は無期限なので `expiryDate` は null）。
 */

export interface RegisterRequest {
  parentEmail: string
  parentPassword: string
  sei: string
  mei: string
  seiKana: string
  meiKana: string
  grade: string
  studentEmail: string
  studentPassword: string
  agreed: boolean
}

export interface RegisterResponse {
  guardianAccountId: number
  studentAccountId: number
  parentEmail: string
  studentEmail: string
  /** 有効期限（yyyy-MM-dd） */
  expiryDate: string
}

export interface LoginRequest {
  loginId: string
  password: string
}

export interface LoginResponse {
  accountId: number
  loginId: string
  displayName: string
  accountType: 'GUARDIAN' | 'STUDENT' | 'ADMIN'
  /** 有効期限（yyyy-MM-dd）。管理者は無期限なので null */
  expiryDate: string | null
}

const http = new HttpClient({ baseUrl: '/api/user' })

/** 保護者 + 初期生徒の一括登録。 */
export function registerAccount(payload: RegisterRequest): Promise<ApiResponse<RegisterResponse>> {
  return http.post<RegisterResponse>('/register', { body: payload })
}

/** メールアドレス + パスワードでログイン認証。 */
export function loginAccount(payload: LoginRequest): Promise<ApiResponse<LoginResponse>> {
  return http.post<LoginResponse>('/login', { body: payload })
}

import { HttpClient, type ApiResponse } from '@study21/web-shared'

/**
 * 自分の情報の修正 / パスワード変更 API（/api/user/profile）。
 * 型とエンドポイントは user-api の AccountProfileController と対応する。
 *
 * メールアドレス（＝ログインID）は変更できないため、更新リクエストには含めない。
 */

export interface UserProfile {
  accountId: number
  /** メールアドレス（ログインID 兼用。変更不可） */
  email: string
  sei: string
  mei: string
  seiKana: string | null
  meiKana: string | null
  /** 学年（生徒のみ。保護者は null） */
  grade: string | null
  phone: string | null
  mailNotify: boolean
  reminderNotify: boolean
  accountType: 'GUARDIAN' | 'STUDENT'
  displayName: string
}

export interface ProfileUpdateRequest {
  sei: string
  mei: string
  seiKana: string
  meiKana: string
  grade: string
  phone: string
  mailNotify: boolean
  reminderNotify: boolean
}

export interface PasswordChangeRequest {
  currentPassword: string
  newPassword: string
}

const http = new HttpClient({ baseUrl: '/api/user' })

/** 自分の情報を取得する。 */
export function fetchProfile(): Promise<ApiResponse<UserProfile>> {
  return http.get<UserProfile>('/profile')
}

/** 自分の情報を更新する（メールアドレス・権限は対象外）。 */
export function updateProfile(payload: ProfileUpdateRequest): Promise<ApiResponse<UserProfile>> {
  return http.put<UserProfile>('/profile', { body: payload })
}

/** パスワードを変更する（現在のパスワードで本人確認）。 */
export function changePassword(payload: PasswordChangeRequest): Promise<ApiResponse<null>> {
  return http.post<null>('/profile/password', { body: payload })
}

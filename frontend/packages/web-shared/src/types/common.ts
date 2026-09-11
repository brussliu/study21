/**
 * Study 2.1 — 共通型（ビジネス権限マッピングを含まない）
 */

/** 役割（バックエンド common-security の Role と対応。権限マッピングは未決定） */
export type Role = 'ADMIN' | 'STUDENT' | 'GUARDIAN'

/** テーマ */
export type ThemeMode = 'light' | 'dark'

/**
 * メニュー項目（Menu Registry 用）。
 * `roles` は「未決定」であり、本段階では常に未指定。
 */
export interface MenuItem {
  id: string
  label: string
  icon?: string
  path?: string
  roles?: Role[]
  children?: MenuItem[]
  enabled: boolean
}

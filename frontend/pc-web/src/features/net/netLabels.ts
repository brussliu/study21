import type {
  ApprovalStatus,
  JudgeMethodCode,
  SiteCategoryCode,
  SiteKindCode,
  StatusCode,
  TerminalModeCode
} from '@/api/net'

/**
 * サイト管理 / 端末コントロールの表示文言。
 *
 * DB にはコードだけを保存し、日本語の表示はここで作る（設計書 §2 の方針）。
 * 文言は 2.0 の画面（site.jsp / terminal_control.jsp）と同じにしている。
 */

/** 区分（端末モード別の許可区分）。2.0 は「0.勉強」のような番号つき表記だった。 */
export const KIND_LABELS: Record<SiteKindCode, string> = {
  STUDY: '0.勉強',
  NORMAL: '1.通常',
  BREAK: '2.休憩',
  GAME: '3.ゲーム'
}

/** URL の一致方法。 */
export const JUDGE_METHOD_LABELS: Record<JudgeMethodCode, string> = {
  PREFIX: '先頭一致',
  SUFFIX: '末尾一致',
  CONTAINS: '含める',
  EXACT: '完全一致'
}

/** 分類。自由入力（分類名称）がある場合はそちらを表示する。 */
export const CATEGORY_LABELS: Record<SiteCategoryCode, string> = {
  LEARNING: '学習',
  ENTERTAINMENT: '娯楽',
  SHOPPING: 'ショッピング',
  SNS: 'SNS',
  OTHER: 'その他'
}

/** 承認ステータス。 */
export const APPROVAL_LABELS: Record<ApprovalStatus, string> = {
  PENDING: '未承認',
  APPROVED: '承認済',
  REJECTED: '却下'
}

/** 有効／無効。 */
export const STATUS_LABELS: Record<StatusCode, string> = {
  '1': '有効',
  '0': '無効'
}

/** 端末モード。 */
export const MODE_LABELS: Record<TerminalModeCode, string> = {
  T: 'T.通常モード',
  K: 'K.休憩モード',
  G: 'G.ゲームモード',
  B: 'B.勉強モード',
  S: 'S.停止モード',
  J: 'J.自由モード'
}

/** 端末モードごとのバッジ色（2.0 の画面の色分けに合わせる）。 */
export const MODE_BADGE_CLASSES: Record<TerminalModeCode, string> = {
  T: 'badge--info',
  K: 'badge--warning',
  G: 'badge--neutral',
  B: 'badge--success',
  S: 'badge--danger',
  J: 'badge--primary'
}

/** 分類の表示（自由入力があればそれを優先する）。 */
export function categoryText(code: SiteCategoryCode, name: string | null | undefined): string {
  if (code === 'OTHER' && name != null && name.trim() !== '') {
    return name
  }
  return CATEGORY_LABELS[code] ?? code
}

/** 承認ステータスのバッジ色。 */
export function approvalBadgeClass(status: ApprovalStatus): string {
  if (status === 'APPROVED') return 'badge--success'
  if (status === 'REJECTED') return 'badge--danger'
  return 'badge--neutral'
}

/** 有効／無効のバッジ色。 */
export function statusBadgeClass(status: StatusCode): string {
  return status === '1' ? 'badge--success' : 'badge--danger'
}

/** セレクト用の選択肢（コードと表示文言）。 */
export function optionsOf<T extends string>(labels: Record<T, string>): { value: T; label: string }[] {
  return (Object.keys(labels) as T[]).map((value) => ({ value, label: labels[value] }))
}

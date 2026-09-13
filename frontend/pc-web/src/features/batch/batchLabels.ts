import type { BatchStatusCode, BatchTypeCode } from '@/api/batch'

/**
 * バッチ管理の表示文言。
 * 値はコードで保持し、画面に出す日本語はここで作る（2.0 の表記に合わせる）。
 */

export const TYPE_LABELS: Record<BatchTypeCode, string> = {
  S: 'S（起動時）',
  L: 'L（循環）',
  R: 'R（定時）',
  C: 'C（呼出）'
}

export const STATUS_LABELS: Record<BatchStatusCode, string> = {
  QUEUED: '待機中',
  RUNNING: '実行中',
  SUCCESS: '正常終了',
  FAILED: '異常終了',
  SKIPPED: 'スキップ'
}

export const STATUS_BADGE_CLASSES: Record<BatchStatusCode, string> = {
  QUEUED: 'badge--neutral',
  RUNNING: 'badge--info',
  SUCCESS: 'badge--success',
  FAILED: 'badge--danger',
  SKIPPED: 'badge--warning'
}

/**
 * 実行のきっかけ。種別 S は「admin-api の起動時と画面の再実行だけ」で動く
 * （2.0 の batL01 が持っていた 6 時間ごとの実行は 2.1 では行わない）。
 */
export function timingLabel(row: {
  taskType: BatchTypeCode
  loopEveryMinutes: number | null
  minuteOfHour: number | null
}): string {
  if (row.taskType === 'S') {
    return '起動時＋再実行'
  }
  if (row.taskType === 'L') {
    return row.loopEveryMinutes ? `${row.loopEveryMinutes}分ごと` : '循環'
  }
  if (row.taskType === 'R') {
    return '定時'
  }
  return '随時'
}

/** 実行時間の表示（未完了は「—」）。 */
export function durationLabel(durationMs: number | null): string {
  if (durationMs === null || durationMs === undefined) {
    return '—'
  }
  if (durationMs < 1000) {
    return `${durationMs} ms`
  }
  return `${(durationMs / 1000).toFixed(1)} 秒`
}

export function optionsOf(labels: Record<string, string>): Array<{ value: string; label: string }> {
  return Object.entries(labels).map(([value, label]) => ({ value, label }))
}

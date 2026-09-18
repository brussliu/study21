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

/**
 * 実行履歴の「対象」列のラベル（何を処理した行か）。
 *
 * バッチコードは**再利用される**（例: `batC52` は以前「AI 生図の AI 生成」、いまは「AI 画図助手」）ので、
 * コードだけでは区別できない。バックエンドが `targetKind` / `targetId` を返せばそれを使い、
 * まだ返さないときは **`要求内容`（生 JSON）から推定**する。
 *
 * ・`AI_FIGURE`      → 「AI生図 #164」
 * ・`AI_ASSIST`      → 「画図助手 #12」
 * ・`CLASSROOM_NOTE` → 「授業ノート #89」
 * ・判別できない／未知 → 「—」（列は空欄のまま。表は壊さない）
 */
export const TARGET_KIND_LABELS: Record<string, string> = {
  AI_FIGURE: 'AI生図',
  AI_ASSIST: '画図助手',
  CLASSROOM_NOTE: '授業ノート'
}

/** 要求内容（JSON 文字列）から対象の種別と ID を推定する（分からなければ null）。 */
export function targetFromPayload(payload: string | null | undefined): { kind: string; id: number | null } | null {
  if (payload === null || payload === undefined || payload.trim() === '') return null
  let parsed: unknown
  try {
    parsed = JSON.parse(payload)
  } catch {
    return null
  }
  if (parsed === null || typeof parsed !== 'object') return null
  const record = parsed as Record<string, unknown>
  const number = (value: unknown): number | null => (
    typeof value === 'number' && Number.isFinite(value) ? value : null
  )
  const aiRequestId = number(record.aiRequestId)
  if (aiRequestId !== null) return { kind: 'AI_FIGURE', id: aiRequestId }
  const assistId = number(record.assistId)
  if (assistId !== null) return { kind: 'AI_ASSIST', id: assistId }
  const noteId = number(record.noteId)
  if (noteId !== null) return { kind: 'CLASSROOM_NOTE', id: noteId }
  return null
}

/**
 * 実行履歴の「対象」の表示。
 * `targetKind` が分からない行（古い行・対象が無いバッチ）は「—」。
 */
export function batchTargetLabel(row: {
  targetKind?: string | null
  targetId?: number | null
  requestPayload?: string | null
}): string {
  const fromPayload = targetFromPayload(row.requestPayload)
  const kind = row.targetKind ?? fromPayload?.kind ?? null
  if (kind === null) return '—'
  const label = TARGET_KIND_LABELS[kind]
  if (label === undefined) return '—'
  const id = row.targetId ?? fromPayload?.id ?? null
  return id === null ? label : `${label} #${id}`
}

import type { BatchScheduleConfigStatus, BatchScheduleTask } from '@/api/batch'

/**
 * 実行スケジュール（統合スケジューラ）の表示文言と規則。
 *
 * 値はコードで保持し、画面に出す日本語はここで作る（`batchLabels.ts` と同じ方針）。
 * 実行間隔・ずらしの候補と規則はバックエンドの `ScheduleRuleCatalog` / `TaskSchedule` と
 * 同じでなければならない（ずれると保存が 400 になり、スケジュールも組めない）。
 *
 * **混同しないこと**（学習状況モニター）:
 *   * `monitorL02IntervalMinutes` … batL02（バッチ）を**いつ動かすか**（実行間隔）
 *   * `monitorVideoProcessing*Time` … **どの撮影時刻の動画を取り込むか**（動画処理時間帯）
 *   * `monitorSnapshotIntervalSeconds` … 動画から**何秒ごとに画像を切るか**（切図間隔）
 */

/** 実行間隔（分）の候補。`ScheduleRuleCatalog.INTERVAL_CHOICES` と同じ 6 択。 */
export const SCHEDULE_INTERVAL_CHOICES = [1, 5, 10, 15, 30, 60] as const

/** 実行間隔の既定（分）。設定が無い・読めないときに使う。 */
export const SCHEDULE_INTERVAL_DEFAULT = 5

/** 実行間隔の候補を `{value,label}` にしたもの（実行設定の欄はこの 6 択のラジオ）。 */
export function intervalChoices(): Array<{ value: string; label: string }> {
  return SCHEDULE_INTERVAL_CHOICES.map((minutes) => ({ value: String(minutes), label: `${minutes} 分` }))
}

/** 設定値（文字列）を実行間隔（分）として読む。候補に無い値は既定（5 分）にする。 */
export function parseIntervalMinutes(value: string | null | undefined): number {
  const parsed = Number.parseInt(String(value ?? '').trim(), 10)
  return (SCHEDULE_INTERVAL_CHOICES as readonly number[]).includes(parsed)
    ? parsed
    : SCHEDULE_INTERVAL_DEFAULT
}

/**
 * ずらし（分）の上限＝実行間隔 − 1（0〜間隔-1 の範囲しか選べない）。
 * 実行間隔は設定値のまま（文字列）渡してよい（候補外・未設定は既定の 5 分として扱う）。
 */
export function offsetMaxMinutes(intervalMinutes: number | string | null | undefined): number {
  return Math.max(0, parseIntervalMinutes(typeof intervalMinutes === 'number' ? String(intervalMinutes) : intervalMinutes) - 1)
}

/** ずらし（分）の候補（0〜実行間隔-1。例: 間隔 5 なら 0〜4 の 5 択）。 */
export function offsetChoices(intervalMinutes: number | string | null | undefined): Array<{ value: string; label: string }> {
  return Array.from({ length: offsetMaxMinutes(intervalMinutes) + 1 }, (_, minutes) => ({
    value: String(minutes),
    label: `${minutes} 分`
  }))
}

/** 実行設定の 1 か所（間隔のキーとずらしのキーの組）。 */
export interface ScheduleOffsetRule {
  /** タスク名（例: `batL02（動画取込・スナップショット切出）`）。 */
  task: string
  /** 実行間隔の設定キー（camelCase）。 */
  intervalKey: string
  /** ずらしの設定キー（camelCase）。 */
  offsetKey: string
}

/**
 * 画面が出す実行設定の組（2.1 で自動実行する 4 タスクのうち、間隔を設定で変えられる 2 つ）。
 * batR03 / batR04 は定時（時刻）なので対象外。
 */
export const SCHEDULE_OFFSET_RULES: ScheduleOffsetRule[] = [
  {
    task: 'batL02（動画取込・スナップショット切出）',
    intervalKey: 'monitorL02IntervalMinutes',
    offsetKey: 'monitorL02OffsetMinutes'
  },
  {
    task: 'batL03（スナップショット AI 分析）',
    intervalKey: 'monitorL03IntervalMinutes',
    offsetKey: 'monitorL03OffsetMinutes'
  }
]

/**
 * ずらしが実行間隔の範囲（0〜間隔-1）に入っているかを、**保存の前に**確かめる。
 *
 * 範囲外の値は DB に入ると（`COM_設定項目` は INT の 0..59 までしか見ない）
 * スケジューラが「設定不正」として自動実行を止めるため、送る前に画面で弾く。
 *
 * @returns 問題なければ null、あれば画面に出す日本語（理由にキー名を含める）
 */
export function scheduleOffsetProblem(
  values: Record<string, string>,
  rules: ScheduleOffsetRule[] = SCHEDULE_OFFSET_RULES
): string | null {
  for (const rule of rules) {
    const rawOffset = String(values[rule.offsetKey] ?? '').trim()
    if (rawOffset === '') continue
    const offset = Number.parseInt(rawOffset, 10)
    if (!Number.isFinite(offset)) {
      return `${rule.task} のずらし（${rule.offsetKey}）は分（数値）で指定してください。`
    }
    const max = offsetMaxMinutes(values[rule.intervalKey])
    if (offset < 0 || offset > max) {
      return `${rule.task} のずらしは 0〜実行間隔-1 分です（実行間隔 ${
        parseIntervalMinutes(values[rule.intervalKey])
      } 分なので 0〜${max} 分）。`
    }
  }
  return null
}

/** タスクコードで引ける形（バッチ一覧の突き合わせ用）。 */
export function scheduleByTaskCode(tasks: BatchScheduleTask[]): Map<string, BatchScheduleTask> {
  const map = new Map<string, BatchScheduleTask>()
  for (const task of tasks) {
    map.set(task.taskCode, task)
  }
  return map
}

/** 設定の状態のバッジ色（有効＝緑 / 未設定・設定不正＝赤 / 未読込＝灰）。 */
export const SCHEDULE_STATUS_BADGE_CLASSES: Record<BatchScheduleConfigStatus, string> = {
  NOT_LOADED: 'badge--neutral',
  LOADED: 'badge--success',
  MISSING: 'badge--danger',
  INVALID: 'badge--danger'
}

/** 設定の状態のバッジ色（未知の値は灰）。 */
export function scheduleStatusBadgeClass(status: string): string {
  return SCHEDULE_STATUS_BADGE_CLASSES[status as BatchScheduleConfigStatus] ?? 'badge--neutral'
}

/**
 * 保存済み・実行設定への反映待ちの案内（無ければ null）。
 *
 * バックエンドの `pendingMessage` をそのまま出す（理由込みの日本語が入っている）。
 * 古い応答などで空のときは画面側の定型文を作る。
 */
export function pendingRefreshMessage(pendingMessage: string | null | undefined): string | null {
  const message = String(pendingMessage ?? '').trim()
  if (message !== '') return message
  return null
}

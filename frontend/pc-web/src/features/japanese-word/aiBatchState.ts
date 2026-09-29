/**
 * AI 取得の 4 バッチ（batC41〜batC44）の**状態の見せ方**（純関数）。
 *
 * <p>利用者の指示（2026-09-27）: 検索条件のところに 4 つの状態を出して、どのバッチが動いているか
 * （有効か・最後は成功したか）が一目で分かるようにする。</p>
 *
 * <p>状態は**批次一覧と同じ入口**（`GET /api/admin/batch/tasks`）から取る。別の入口を作ると
 * バッチ一覧の表示と食い違うので、同じ行を使う。取得に失敗しても画面は壊さない（「未実行」で出す）。</p>
 */

import type { BatchTaskRow } from '@/api/batch'
import type { JpnAcquireAction } from './acquireActions'

/** バッジの色（設計システムの `.badge--*` に対応）。 */
export type JpnAiBatchTone = 'neutral' | 'info' | 'warning' | 'success' | 'danger'

/** 画面に出すバッチ 1 つの状態。 */
export interface JpnAiBatchBadge {
  /** バッチコード（`batC41` など。ツールチップと `data-` 属性に使う）。 */
  batchCode: string
  /** 区分の短い名前（A・B／C／D／E）。 */
  short: string
  /** バッジの文言（実行中・正常終了・異常終了・待機中・スキップ・未実行・無効）。 */
  label: string
  /** 色。 */
  tone: JpnAiBatchTone
  /** マウスを載せたときの説明（批次コード・最終実行・上限・設定の不足）。 */
  title: string
}

/**
 * 実行状態コード → 表示（日本語・色）。
 *
 * <p>サーバーの {@code BatchExecutionStatus} と同じ 5 つ。知らない値はそのまま出す
 * （画面が黙って「未実行」に見せると、状態が増えたときに気づけない）。</p>
 */
const STATUS_LABELS: Record<string, { label: string; tone: JpnAiBatchTone }> = {
  QUEUED: { label: '待機中', tone: 'info' },
  RUNNING: { label: '実行中', tone: 'warning' },
  SUCCESS: { label: '正常終了', tone: 'success' },
  FAILED: { label: '異常終了', tone: 'danger' },
  SKIPPED: { label: 'スキップ', tone: 'info' }
}

/** `2026-09-27T10:00:00` → `2026-09-27 10:00`（表示用。秒は落とす）。 */
function displayTime(value: string | null | undefined): string | null {
  if (!value) {
    return null
  }
  return value.replace('T', ' ').slice(0, 16)
}

/** 1 つのバッチの状態を、画面に出す形にする。 */
export function jpnAiBatchBadgeOf(
  action: JpnAcquireAction,
  row: BatchTaskRow | undefined,
  limit?: number | null
): JpnAiBatchBadge {
  const state = row?.latestStatus ? STATUS_LABELS[row.latestStatus] : undefined
  const statusLabel = row?.latestStatus ? (state?.label ?? row.latestStatus) : '未実行'
  // 無効のバッチは「動かない」ことが一番大事なので、実行状態より優先して見せる
  const label = row && !row.active ? '無効' : row?.running ? '実行中' : statusLabel
  const tone: JpnAiBatchTone = row && !row.active
    ? 'neutral'
    : row?.running
      ? 'warning'
      : (state?.tone ?? 'neutral')

  const lines = [`${action.label}（${row?.taskCode ?? action.batchCode}）`, `状態: ${label}`]
  if (label !== statusLabel) {
    lines.push(`最新の実行: ${statusLabel}`)
  }
  const runAt = displayTime(row?.latestEndTime ?? row?.latestStartTime ?? row?.lastRunAt)
  lines.push(runAt ? `最終実行: ${runAt}` : '最終実行: まだありません')
  if (typeof limit === 'number' && limit > 0) {
    lines.push(`1 回の最大 ${limit} 語`)
  }
  if (row && !row.settingsComplete) {
    lines.push(`設定が足りません: ${row.missingSettings.join(', ') || row.requiredSettings.join(', ')}`)
  }
  return {
    batchCode: row?.taskCode ?? action.batchCode,
    short: action.short,
    label,
    tone,
    title: lines.join('\n')
  }
}

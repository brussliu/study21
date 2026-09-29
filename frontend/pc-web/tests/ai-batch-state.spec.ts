import { describe, expect, it } from 'vitest'
import { jpnAiBatchBadgeOf } from '@/features/japanese-word/aiBatchState'
import { acquireActionOf } from '@/features/japanese-word/acquireActions'
import type { BatchTaskRow } from '@/api/batch'

/**
 * AI 取得の 4 バッチ（batC41〜batC44）の**状態の見せ方**（純関数）。
 *
 * <p>利用者の指示（2026-09-27）: 検索条件のところに 4 つの状態を出して、どのバッチが
 * 動いているか（有効か・最後は成功したか）が一目で分かるようにする。
 * データは批次一覧と同じ `GET /api/admin/batch/tasks` から取る（画面で見る値と食い違わない）。</p>
 */
function taskRow(overrides: Partial<BatchTaskRow> = {}): BatchTaskRow {
  return {
    taskCode: 'batC41',
    taskType: 'S',
    description: '日本語単語の AI 取得（詳細）',
    active: true,
    activeVersion: 1,
    lastRunAt: null,
    canToggleActive: true,
    canManualRerun: true,
    canRerun: true,
    runsOnStartup: false,
    loopEveryMinutes: null,
    minuteOfHour: null,
    pageCode: 'JAPANESE_WORD_AI',
    requiredSettings: ['BAT_C41_BATCH_MAX'],
    settingsComplete: true,
    missingSettings: [],
    latestStatus: 'SUCCESS',
    latestStartTime: '2026-09-27T10:00:00',
    latestEndTime: '2026-09-27T10:01:00',
    latestMessage: null,
    running: false,
    ...overrides
  }
}

describe('AI 取得バッチの状態', () => {
  it('最後が正常終了なら「正常終了」（緑）', () => {
    const badge = jpnAiBatchBadgeOf(acquireActionOf('AB'), taskRow())

    expect(badge.label).toBe('正常終了')
    expect(badge.tone).toBe('success')
    expect(badge.short).toBe('A・B')
  })

  it('いま動いていれば「実行中」（橙）', () => {
    const badge = jpnAiBatchBadgeOf(acquireActionOf('C'), taskRow({ running: true, latestStatus: 'RUNNING' }))

    expect(badge.label).toBe('実行中')
    expect(badge.tone).toBe('warning')
  })

  it('最後が異常終了なら「異常終了」（赤）', () => {
    const badge = jpnAiBatchBadgeOf(acquireActionOf('D'), taskRow({ latestStatus: 'FAILED' }))

    expect(badge.label).toBe('異常終了')
    expect(badge.tone).toBe('danger')
  })

  it('待機中・スキップも区別して出す', () => {
    expect(jpnAiBatchBadgeOf(acquireActionOf('E'), taskRow({ latestStatus: 'QUEUED' })).label).toBe('待機中')
    expect(jpnAiBatchBadgeOf(acquireActionOf('E'), taskRow({ latestStatus: 'SKIPPED' })).label).toBe('スキップ')
  })

  it('無効のバッチは「無効」（実行状態より優先して見せる）', () => {
    const badge = jpnAiBatchBadgeOf(acquireActionOf('AB'), taskRow({ active: false, running: true }))

    expect(badge.label).toBe('無効')
    expect(badge.tone).toBe('neutral')
  })

  it('一度も実行していなければ「未実行」', () => {
    const badge = jpnAiBatchBadgeOf(acquireActionOf('AB'), taskRow({ latestStatus: null }))

    expect(badge.label).toBe('未実行')
    expect(badge.tone).toBe('neutral')
  })

  it('批次一覧に行が無くても「未実行」で出す（読み込み失敗でも画面は壊さない）', () => {
    const badge = jpnAiBatchBadgeOf(acquireActionOf('AB'), undefined)

    expect(badge.label).toBe('未実行')
    expect(badge.batchCode).toBe('batC41')
  })

  it('マウスを載せた説明に、批次コード・最終実行・上限・設定の不足を出す', () => {
    const badge = jpnAiBatchBadgeOf(acquireActionOf('AB'), taskRow({
      settingsComplete: false,
      missingSettings: ['BAT_C41_BATCH_MAX']
    }), 20)

    expect(badge.title).toContain('batC41')
    expect(badge.title).toContain('詳細情報（A・B）')
    expect(badge.title).toContain('最終実行')
    expect(badge.title).toContain('1 回の最大 20 語')
    // 設定が足りないと実行できない（画面が出す理由）
    expect(badge.title).toContain('BAT_C41_BATCH_MAX')
  })

  it('未実行でも説明に批次コードと上限を出す', () => {
    const badge = jpnAiBatchBadgeOf(acquireActionOf('E'), undefined, 30)

    expect(badge.title).toContain('batC44')
    expect(badge.title).toContain('1 回の最大 30 語')
    expect(badge.title).toContain('未実行')
  })
})

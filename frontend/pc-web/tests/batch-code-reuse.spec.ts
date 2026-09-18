import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import BatchHistoryView from '@/views/batch/BatchHistoryView.vue'
import { buildBatchTabs, groupTitleOf } from '@/features/batch/batchGroups'
import { batchTargetLabel, targetFromPayload } from '@/features/batch/batchLabels'
import type { BatchExecutionRow, BatchTaskRow } from '@/api/batch'

/**
 * バッチコードの再利用（batC52）と、廃止した batC53 が混ざった実行履歴。
 *
 * 2026-09 の構成変更で AI 生図のパイプラインは
 *   ・**batC51-A〜D**＝AI 生成（**作図モードごとに 1 バッチ**）
 *   ・**batC52**＝AI 画図助手（空いた番号を**再利用**）
 *   ・画像の取込・前処理／コマンドの検証・確定は**バッチではない**（batC53 は廃止）
 * となった。モードが無い時代の**裸の batC51 は 2026-09-19 に削除**したが、
 * `BAT_バッチ実行履歴情報` には**古い履歴（裸の batC51＝当時の AI 生成、batC53＝検証）が残っており**、
 * 同じ `batC52` に「旧・AI 生図の履歴」と「新・AI 画図助手の履歴」が同居する
 * （見分けは `要求内容` の `aiRequestId`／`assistId`。過去の行は消さない）。
 *
 * ここでは、**その混在で画面が壊れないこと**（コードはそのまま出し、何をしたかはメッセージで分かる）
 * と、タブ分けが落ちないことを固定する。
 */
function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function executionRow(overrides: Partial<BatchExecutionRow> = {}): BatchExecutionRow {
  return {
    executionId: 9001,
    batchCode: 'batC51',
    batchType: 'C',
    triggerType: 'C',
    status: 'SUCCESS',
    requestedByAccountId: null,
    requestedByCode: 'APP',
    scheduleTime: null,
    startTime: '2026-09-15T23:50:27',
    endTime: '2026-09-15T23:50:28',
    durationMs: 900,
    message: 'AI がコマンドを 5 件生成しました。',
    errorDetail: null,
    ...overrides
  }
}

function taskRow(overrides: Partial<BatchTaskRow> = {}): BatchTaskRow {
  return {
    taskCode: 'batC51-A', taskType: 'C', description: 'AI 生図 画像をもとに再現', active: true, activeVersion: 1,
    lastRunAt: null, canToggleActive: false, canManualRerun: false, canRerun: true, runsOnStartup: false, loopEveryMinutes: null,
    minuteOfHour: null, pageCode: 'GEOMETRY_AI', requiredSettings: [], settingsComplete: true,
    missingSettings: [], latestStatus: null, latestStartTime: null, latestEndTime: null,
    latestMessage: null, running: false,
    ...overrides
  }
}

async function setup(options: { executions: BatchExecutionRow[]; tasks?: BatchTaskRow[] }): Promise<{
  wrapper: ReturnType<typeof mount>
}> {
  const pinia = createPinia()
  setActivePinia(pinia)
  const fetchMock = vi.fn(async (url: string) => {
    if (String(url).includes('/api/admin/batch/executions')) {
      return ok({
        items: options.executions,
        totalElements: options.executions.length,
        page: 1,
        size: 15,
        totalPages: 1
      })
    }
    return ok({ rows: options.tasks ?? [taskRow()], totalCount: 1, startupTargets: [] })
  })
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(BatchHistoryView, { global: { plugins: [pinia] } })
  await flushPromises()
  await flushPromises()
  return { wrapper }
}

beforeEach(() => {
  useToast().items.splice(0)
  window.scrollTo = vi.fn()
})

describe('バッチコード batC52 の再利用（AI 生図 → AI 画図助手）', () => {
  it('batC51-A〜D と batC52 は「図形管理」、廃止した batC53 は表に無い（設定ページから推定）', () => {
    for (const taskCode of ['batC51-A', 'batC51-B', 'batC51-C', 'batC51-D']) {
      expect(groupTitleOf(taskRow({ taskCode, pageCode: 'GEOMETRY_AI' }))).toBe('図形管理')
    }
    // batC52 は空いた番号を AI 画図助手に再利用（消さない）
    expect(groupTitleOf(taskRow({ taskCode: 'batC52', pageCode: 'GEOMETRY_AI' }))).toBe('図形管理')
    // batC53 はバッチではなくなった（表から外した）。設定ページが分かれば「図形管理」に落ちる
    expect(groupTitleOf(taskRow({ taskCode: 'batC53', pageCode: 'GEOMETRY_AI' }))).toBe('図形管理')
    expect(groupTitleOf(taskRow({ taskCode: 'batC53', pageCode: null }))).toBe('その他')
    // batC54 は使わない（表にも無く、設定ページからの推定だけ）
    expect(groupTitleOf(taskRow({ taskCode: 'batC54', pageCode: null }))).toBe('その他')
  })

  it('モード別の AI 生成と AI 画図助手が同じタブにまとまる', () => {
    const tabs = buildBatchTabs([
      taskRow({ taskCode: 'batC51-A', pageCode: 'GEOMETRY_AI' }),
      taskRow({ taskCode: 'batC52', pageCode: 'GEOMETRY_AI' })
    ])

    const group = tabs.find((tab) => tab.key === '図形管理')
    expect(group?.rows.map((row) => row.taskCode)).toEqual(['batC51-A', 'batC52'])
  })

  it('実行履歴の「対象」列: 種別ごとの表示と、分からない行は「—」', () => {
    // バックエンドが targetKind / targetId を返すとき
    expect(batchTargetLabel({ targetKind: 'AI_FIGURE', targetId: 164 })).toBe('AI生図 #164')
    expect(batchTargetLabel({ targetKind: 'AI_ASSIST', targetId: 12 })).toBe('画図助手 #12')
    expect(batchTargetLabel({ targetKind: 'CLASSROOM_NOTE', targetId: 89 })).toBe('授業ノート #89')
    // 未知の種別・対象が無い行・ID が無い行でも壊れない
    expect(batchTargetLabel({ targetKind: 'SOMETHING_NEW', targetId: 1 })).toBe('—')
    expect(batchTargetLabel({})).toBe('—')
    expect(batchTargetLabel({ targetKind: null, requestPayload: null })).toBe('—')
    expect(batchTargetLabel({ targetKind: 'AI_FIGURE', targetId: null })).toBe('AI生図')

    // バックエンドがまだ targetKind を返さないときは「要求内容」（生 JSON）から推定する
    expect(targetFromPayload('{"stage":"batC52","requestNo":"AIG2026","aiRequestId":164}'))
      .toEqual({ kind: 'AI_FIGURE', id: 164 })
    expect(targetFromPayload('{"assistId":12}')).toEqual({ kind: 'AI_ASSIST', id: 12 })
    expect(targetFromPayload('{"noteId":89}')).toEqual({ kind: 'CLASSROOM_NOTE', id: 89 })
    expect(targetFromPayload('{"stage":"batC51"}')).toBeNull()
    expect(targetFromPayload('壊れた JSON')).toBeNull()
    expect(targetFromPayload(null)).toBeNull()
    expect(batchTargetLabel({ requestPayload: '{"aiRequestId":164}' })).toBe('AI生図 #164')
    expect(batchTargetLabel({ requestPayload: '{"assistId":12}' })).toBe('画図助手 #12')
    expect(batchTargetLabel({ requestPayload: '{"noteId":89}' })).toBe('授業ノート #89')
    expect(batchTargetLabel({ requestPayload: '{}' })).toBe('—')
  })

  it('削除した裸の batC51 の履歴は今までどおり出る（定義が無くてもコードをそのまま表示する）', async () => {
    // 裸の batC51（モードが無い時代の AI 生成）は 2026-09-19 にバッチごと削除したが、
    // BAT_バッチ実行履歴情報 には当時の行が残っている（消さない）。履歴画面はタスク定義に
    // 依存せず**コードをそのまま**表示し、絞り込みの候補も履歴の実データから作る
    const { wrapper } = await setup({
      executions: [executionRow({ batchCode: 'batC51', message: 'AI がコマンドを 5 件生成しました。' })]
    })

    const rows = wrapper.findAll('[data-execution-id]')
    expect(rows).toHaveLength(1)
    expect(rows[0]?.findAll('td')[1]?.text()).toBe('batC51')
    expect(wrapper.text()).toContain('AI がコマンドを 5 件生成しました。')
    const options = wrapper.findAll('select[aria-label="バッチコード"] option')
      .map((option) => option.attributes('value'))
    expect(options).toContain('batC51')
  })

  it('同じ batC52 に旧形式と新形式の履歴が混ざっても実行履歴の一覧が壊れない', async () => {
    // 旧 batC52＝AI 生図の AI 生成（要求内容は {"aiRequestId": N}）。
    // 新 batC52＝AI 画図助手（要求内容は {"assistId": N}）※画面には要求内容は出ない
    const { wrapper } = await setup({
      executions: [
        executionRow({
          executionId: 9102, batchCode: 'batC52', requestedByCode: 'APP',
          message: '指示からコマンドを 3 件作りました。',
          requestPayload: '{"assistId":12}'
        }),
        executionRow({
          executionId: 9002, batchCode: 'batC52', requestedByCode: 'APP',
          message: 'AI がコマンドを 5 件生成しました。（AIG202609152350273118142）',
          requestPayload: '{"stage":"batC52","requestNo":"AIG202609152350273118142","aiRequestId":164}'
        }),
        executionRow({
          executionId: 9001, batchCode: 'batC53', status: 'SUCCESS',
          message: '検証に通りました。（AIG202609152350273118142 5 コマンド / geometry）',
          requestPayload: '{"stage":"batC53","requestNo":"AIG202609152350273118142","aiRequestId":164}'
        })
      ]
    })

    const rows = wrapper.findAll('[data-execution-id]')
    expect(rows).toHaveLength(3)
    // コードはそのまま出す（旧 batC53 の行も消さずに表示する）
    expect(rows.map((row) => row.findAll('td')[1]?.text()))
      .toEqual(['batC52', 'batC52', 'batC53'])
    // 何をした行なのかはメッセージで分かる（AI 生図の AI 生成 / AI 画図助手 / 検証）
    const text = wrapper.text()
    expect(text).toContain('指示からコマンドを 3 件作りました。')
    expect(text).toContain('AI がコマンドを 5 件生成しました。')
    expect(text).toContain('検証に通りました。')
    // 「対象」列で、同じ batC52 でも旧（AI 生図）と新（AI 画図助手）を見分けられる
    expect(rows.map((row) => row.get('[data-batch-target]').text()))
      .toEqual(['画図助手 #12', 'AI生図 #164', 'AI生図 #164'])
    // 見出しは「バッチコード」の次に「対象」を足しただけで、既存の列と順序はそのまま
    expect(wrapper.findAll('thead th').map((th) => th.text())).toEqual([
      '実行ID', 'バッチコード', '対象', '種別', '起動', '状態', '開始時刻', '終了時刻', '処理時間', 'メッセージ'
    ])

    // 絞り込みのバッチコード候補にも batC52（タスク表）と batC53（残っている履歴）が出る
    const options = wrapper.findAll('select[aria-label="バッチコード"] option')
      .map((option) => option.attributes('value'))
    expect(options).toContain('batC52')
    expect(options).toContain('batC53')
  })
})

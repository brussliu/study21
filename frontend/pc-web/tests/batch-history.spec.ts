import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import BatchHistoryView from '@/views/batch/BatchHistoryView.vue'
import type { BatchExecutionRow, BatchTaskRow } from '@/api/batch'

/**
 * バッチ実行履歴（メニュー「バッチ管理」＞「バッチ実行履歴」）。
 * 2.0 の履歴管理画面（history.jsp）の「バッチ実行履歴」タブに当たる。
 * 実行ID・バッチコード・種別・起動種別・状態・時刻・処理時間・メッセージを出す。
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function executionRow(overrides: Partial<BatchExecutionRow> = {}): BatchExecutionRow {
  return {
    executionId: 9001,
    batchCode: 'batS01',
    batchType: 'S',
    triggerType: 'S',
    status: 'SUCCESS',
    requestedByAccountId: null,
    requestedByCode: 'STARTUP',
    scheduleTime: '2026-09-12T00:00:00',
    startTime: '2026-09-12T00:00:00',
    endTime: '2026-09-12T00:00:01',
    durationMs: 820,
    message: 'Proxy started on port 7777',
    errorDetail: null,
    ...overrides
  }
}

function taskRow(overrides: Partial<BatchTaskRow> = {}): BatchTaskRow {
  return {
    taskCode: 'batS01', taskType: 'S', description: 'プロキシサービス', active: true, activeVersion: 1,
    lastRunAt: null, canToggleActive: true, canRerun: true, runsOnStartup: true, loopEveryMinutes: null,
    minuteOfHour: null, pageCode: 'SYSTEM', requiredSettings: [], settingsComplete: true, missingSettings: [],
    latestStatus: null, latestStartTime: null, latestEndTime: null, latestMessage: null, running: false,
    ...overrides
  }
}

type Call = { url: string; method: string }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return { url: String(call[0]), method: (init.method ?? 'GET').toUpperCase() }
  })
}

beforeEach(() => {
  useToast().items.splice(0)
  window.scrollTo = vi.fn()
})

async function setup(options: { executions?: BatchExecutionRow[]; total?: number } = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const executions = options.executions ?? [
    executionRow(),
    executionRow({ executionId: 9000, batchCode: 'batR02', batchType: 'R', triggerType: 'R', status: 'FAILED', message: '失敗しました', errorDetail: 'stack', durationMs: 1200 })
  ]
  const total = options.total ?? executions.length

  const fetchMock = vi.fn(async (url: string) => {
    if (String(url).includes('/api/admin/batch/executions')) {
      return ok({ items: executions, totalElements: total, page: 1, size: 15, totalPages: Math.max(1, Math.ceil(total / 15)) })
    }
    return ok({ rows: [taskRow()], totalCount: 1, startupTargets: ['batS01'] })
  })
  vi.stubGlobal('fetch', fetchMock)

  const wrapper = mount(BatchHistoryView, { global: { plugins: [pinia] } })
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock }
}

describe('バッチ実行履歴（バッチ管理＞バッチ実行履歴）', () => {
  it('実行履歴を表示する（実行ID・バッチコード・種別・起動・状態・処理時間）', async () => {
    const { wrapper } = await setup()

    const first = wrapper.get('tbody tr[data-execution-id="9001"]')
    expect(first.text()).toContain('batS01')
    expect(first.text()).toContain('S（起動時）')
    expect(first.text()).toContain('正常終了')
    expect(first.text()).toContain('820 ms')
    expect(first.text()).toContain('Proxy started on port 7777')

    const second = wrapper.get('tbody tr[data-execution-id="9000"]')
    expect(second.text()).toContain('異常終了')
    expect(second.text()).toContain('1.2 秒')
  })

  it('バッチコード・状態・キーワードで絞り込める', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('select[aria-label="状態"]').setValue('FAILED')
    await wrapper.get('input[aria-label="キーワード"]').setValue('proxy')
    await wrapper.findAll('.batch-page__filters .btn')[0].trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/executions'))
    expect(call?.url).toContain('status=FAILED')
    expect(call?.url).toContain('keyword=proxy')
  })

  it('件数の選択はページングの左隣にあり、変えるとその件数で取り直す', async () => {
    const executions = Array.from({ length: 20 }, (_, index) => executionRow({ executionId: 9001 - index }))
    const { wrapper, fetchMock } = await setup({ executions, total: 3500 })

    const pagination = wrapper.get('.pagination')
    // 件数情報 → 件数 → ページ番号 の順
    expect([...pagination.element.children].map((child) => child.className.split(' ')[0]))
      .toEqual(['pagination__info', 'pagination__size', 'pagination__pages'])
    expect(pagination.get('[data-page-size]').findAll('option').map((option) => option.text()))
      .toEqual(['20 件', '50 件', '100 件'])
    expect((pagination.get('[data-page-size]').element as HTMLSelectElement).value).toBe('20')

    // 既定は 20 件で取りに行く（絞り込み用のバッチコード収集は別途 size=100 で取る）
    expect(recorded(fetchMock).some(
      (entry) => entry.url.includes('/executions') && entry.url.includes('size=20'))).toBe(true)

    await pagination.get('[data-page-size]').setValue('50')
    await flushPromises()
    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/executions'))
    expect(call?.url).toContain('size=50')
    expect(call?.url).toContain('page=1')

    // 1 ページしか無くても件数は変えられる（行があるときは常に出す）
    const single = await setup({ executions: [executionRow()], total: 1 })
    expect(single.wrapper.find('.pagination [data-page-size]').exists()).toBe(true)
  })

  it('ページを移動できる', async () => {
    const executions = Array.from({ length: 15 }, (_, index) => executionRow({ executionId: 9001 - index }))
    const { wrapper, fetchMock } = await setup({ executions, total: 45 })

    expect(wrapper.text()).toContain('全 45 件')
    expect(wrapper.findAll('tbody tr[data-execution-id]').length).toBe(15)

    const pageTwo = wrapper.findAll('.pagination__pages .page-btn').find((button) => button.text() === '2')
    await pageTwo?.trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/executions'))
    expect(call?.url).toContain('page=2')
  })
})

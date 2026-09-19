import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import BatchListView from '@/views/batch/BatchListView.vue'
import type { BatchExecutionRow, BatchTaskRow } from '@/api/batch'

/**
 * バッチ管理画面（2.0 の batch.jsp 相当）。
 *
 * 2.1 の要点:
 *   * 上部の「バッチ検索条件」カードは置かない
 *   * 「並列」の列は持たない（2.1 は並列実行の仕組みを持たないため）
 *   * 有効にできるのは S / L / R。起動時に走るのは batS01 だけ
 *   * 【再実行】は有効／無効に関係なく押せる（業務処理が未実装のバッチは押せない）
 *   * 種別 C（呼出）は他の処理から呼ばれるバッチなので、【再実行】ボタン自体を出さない
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function taskRow(overrides: Partial<BatchTaskRow> = {}): BatchTaskRow {
  return {
    taskCode: 'batS01',
    taskType: 'S',
    description: 'プロキシサービス（admin-api 起動時と再実行）',
    active: true,
    activeVersion: 1,
    lastRunAt: '2026-09-12T00:00:00',
    canToggleActive: true,
    canManualRerun: true,
    canRerun: true,
    runsOnStartup: true,
    loopEveryMinutes: null,
    minuteOfHour: null,
    pageCode: 'SYSTEM',
    requiredSettings: [],
    settingsComplete: true,
    missingSettings: [],
    latestStatus: 'SUCCESS',
    latestStartTime: '2026-09-12T00:00:00',
    latestEndTime: '2026-09-12T00:00:01',
    latestMessage: 'Proxy already running on port 7777',
    running: false,
    ...overrides
  }
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

type Call = { url: string; method: string; body: Record<string, unknown> | null }

/** 実行スケジュール（GET /api/admin/batch/schedule）の既定＝対象タスクなし。 */
function emptySchedule(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    zone: 'Asia/Tokyo',
    version: 0,
    loadedAt: null,
    pendingRefresh: false,
    pendingMessage: null,
    lastRefreshAt: null,
    lastRefreshError: null,
    nextRetryAt: null,
    checkIntervalSeconds: 30,
    runningWorkers: 0,
    queuedWorkers: 0,
    tasks: [],
    ...overrides
  }
}

/** スケジュールの 1 タスク（設定に基づく実行タイミングと次回実行時刻）。 */
function scheduleTask(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    taskCode: 'batR03',
    status: 'LOADED',
    statusLabel: '有効',
    enabled: true,
    describe: '毎日 23:30',
    intervalMinutes: null,
    offsetMinutes: null,
    dailyTime: '23:30',
    examplePoints: ['23:30'],
    nextRunAt: '2026-09-19T23:30:00',
    nextRunLabel: '2026-09-19 23:30',
    lastPlannedAt: null,
    ...overrides
  }
}

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      body: init.body ? (JSON.parse(String(init.body)) as Record<string, unknown>) : null
    }
  })
}

function toastMessages(): string[] {
  return useToast().items.map((item) => item.message)
}

beforeEach(() => {
  useToast().items.splice(0)
  window.scrollTo = vi.fn()
})

async function setup(options: {
  tasks?: BatchTaskRow[]
  startupTargets?: string[]
  executions?: BatchExecutionRow[]
  total?: number
  rerunResult?: Record<string, unknown>
  schedule?: Record<string, unknown> | null
  handlers?: (url: string, method: string) => Response | null
} = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const tasks = options.tasks ?? [
    taskRow(),
    taskRow({
      taskCode: 'batR02',
      taskType: 'R',
      description: 'バッチ実行履歴・上網履歴クリーンアップ処理',
      active: false,
      canRerun: false,
      runsOnStartup: false,
      latestStatus: null,
      latestStartTime: null,
      latestEndTime: null,
      latestMessage: null
    }),
    taskRow({
      taskCode: 'batC04',
      taskType: 'C',
      description: '英訳中日問題生成',
      active: false,
      canToggleActive: false,
      // 種別 C でもハンドラは実装済みであり得る（実データの batC51 / batC52）。
      // 「ボタンを出さない」は canManualRerun だけで決まることを固定するため canRerun は true にする
      canManualRerun: false,
      canRerun: true,
      runsOnStartup: false
    })
  ]
  const executions = options.executions ?? [executionRow()]
  const total = options.total ?? executions.length

  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    const handled = options.handlers?.(String(url), method)
    if (handled) return handled
    if (String(url).includes('/api/admin/batch/tasks/') && String(url).endsWith('/rerun')) {
      return ok(options.rerunResult ?? {
        success: true,
        executionId: 9100,
        status: 'SUCCESS',
        statusLabel: '正常終了',
        message: 'Proxy started on port 7777',
        errorDetail: null
      })
    }
    if (String(url).includes('/api/admin/batch/tasks/') && String(url).endsWith('/active')) {
      return ok({ success: true, batchCode: 'batS01', active: true, message: 'バッチの有効設定を更新しました。' })
    }
    if (String(url).includes('/api/admin/batch/executions')) {
      return ok({ items: executions, totalElements: total, page: 1, size: 15, totalPages: Math.max(1, Math.ceil(total / 15)) })
    }
    /*
     * 実行スケジュール（GET /api/admin/batch/schedule）。既定は**空**にして、
     * 一覧が定義のタイミング（timingLabel）へ戻る動きを今までどおり固定する
     * （設定に基づくタイミングを見るテストは schedule を渡す）。
     */
    if (String(url).includes('/api/admin/batch/schedule')) {
      return ok(options.schedule ?? emptySchedule())
    }
    return ok({ rows: tasks, totalCount: tasks.length, startupTargets: options.startupTargets ?? ['batS01'] })
  })

  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(BatchListView, { global: { plugins: [pinia] } })
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock }
}

describe('バッチ一覧（バッチ管理＞バッチ一覧）', () => {
  it('バッチ一覧を表示する（種別・有効・実行タイミング）', async () => {
    const { wrapper } = await setup()

    // 一覧の見出しには一覧アイコンを付ける（他の一覧画面と揃える）
    expect(wrapper.get('.table-section__title').get('use').attributes('href')).toBe('#i-list')

    const firstRow = wrapper.get('tbody tr[data-batch-code="batS01"]')
    expect(firstRow.text()).toContain('batS01')
    expect(firstRow.text()).toContain('S（起動時）')
    expect(firstRow.text()).toContain('起動時＋再実行')
    expect(firstRow.text()).toContain('正常終了')
    expect(wrapper.text()).toContain('batS01')
    // 有効／無効の説明文は出さない（タブと有効スイッチで読み取れるため）
    expect(wrapper.find('.batch-page__note').exists()).toBe(false)
  })

  it('2.0 と同じタブでグループ分けし、タブで絞り込める', async () => {
    const { wrapper } = await setup()

    const tabLabels = wrapper.findAll('.tabs__tab').map((tab) => tab.text())
    // すべて（3 件）＋ 該当グループだけが出る（空のグループは出さない）
    expect(tabLabels[0]).toContain('すべて')
    expect(tabLabels[0]).toContain('3')
    expect(tabLabels).toContain('システム2')
    expect(tabLabels).toContain('英単語問題生成1')
    expect(wrapper.findAll('tbody tr[data-batch-code]').length).toBe(3)

    // タブを選ぶとそのグループだけになる
    const systemTab = wrapper.findAll('.tabs__tab').find((tab) => tab.text().includes('システム'))
    await systemTab?.trigger('click')
    expect(wrapper.findAll('tbody tr[data-batch-code]').length).toBe(2)
    expect(wrapper.find('tbody tr[data-batch-code="batC04"]').exists()).toBe(false)
    expect(wrapper.text()).toContain('バッチ一覧（システム）')

    // すべてに戻す
    await wrapper.findAll('.tabs__tab')[0].trigger('click')
    expect(wrapper.findAll('tbody tr[data-batch-code]').length).toBe(3)
  })

  it('「並列」の列と上部のバッチ検索条件カードを持たない', async () => {
    const { wrapper } = await setup()

    expect(wrapper.text()).not.toContain('並列')
    expect(wrapper.text()).not.toContain('バッチ検索条件')
    // 上部の検索カードは無く、絞り込みは実行履歴の見出し行だけ
    expect(wrapper.find('.search-panel').exists()).toBe(false)
  })

  it('有効スイッチで有効／無効 API へ送る', async () => {
    const { wrapper, fetchMock } = await setup()

    // batR02 は無効 → クリックで有効にする
    const switchInput = wrapper.get('tbody tr[data-batch-code="batR02"] input[type="checkbox"]')
    expect((switchInput.element as HTMLInputElement).checked).toBe(false)
    await switchInput.trigger('change')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.url.endsWith('/tasks/batR02/active'))
    expect(call?.method).toBe('POST')
    expect(call?.body).toEqual({ active: true, operator: undefined })
    expect(toastMessages().join(' ')).toContain('バッチの有効設定を更新しました。')
  })

  it('呼出（C）バッチの有効スイッチは押せない', async () => {
    const { wrapper } = await setup()

    const input = wrapper.get('tbody tr[data-batch-code="batC04"] input[type="checkbox"]')
    expect((input.element as HTMLInputElement).disabled).toBe(true)
  })

  it('【再実行】で rerun API を叩き、結果を表示して履歴を読み直す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('tbody tr[data-batch-code="batS01"] button').trigger('click')
    await flushPromises()
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.url.endsWith('/tasks/batS01/rerun'))
    expect(call?.method).toBe('POST')
    expect(wrapper.text()).toContain('再実行しました。')
    expect(wrapper.text()).toContain('Proxy started on port 7777')
    expect(toastMessages().join(' ')).toContain('batS01: Proxy started on port 7777')
    // 一覧を読み直す（実行の履歴は「バッチ実行履歴」画面で見る）
    expect(recorded(fetchMock).filter((entry) => entry.url.endsWith('/tasks')).length).toBeGreaterThan(1)
  })

  it('業務処理が未実装のバッチは再実行できない', async () => {
    const { wrapper } = await setup()

    const button = wrapper.get('tbody tr[data-batch-code="batR02"] button')
    expect((button.element as HTMLButtonElement).disabled).toBe(true)
    expect(button.attributes('title')).toContain('2.1 では未実装')
  })

  it('呼出（C）バッチには【再実行】ボタンを出さない', async () => {
    const { wrapper } = await setup()

    // 種別 C は他の処理から呼ばれるバッチで、画面からは起動しない → ボタンごと出さない。
    // ハンドラが実装済み（canRerun=true）でも同じで、出し分けは canManualRerun だけで決まる
    const callRow = wrapper.get('tbody tr[data-batch-code="batC04"]')
    expect(callRow.find('button').exists()).toBe(false)
    expect(callRow.findAll('td').at(-1)?.text()).toBe('—')
    // 押せない灰色のボタンも残さない（「2.1 では未実装です」の説明も出ない）
    expect(callRow.text()).not.toContain('再実行')

    // 他の種別は今までどおり（S は押せて、ハンドラ未実装の R は押せない見た目）
    expect((wrapper.get('tbody tr[data-batch-code="batS01"] button').element as HTMLButtonElement).disabled)
      .toBe(false)
    expect(wrapper.findAll('tbody button').length).toBe(2)
  })

  it('再実行が失敗したら失敗として表示する', async () => {
    const { wrapper } = await setup({
      rerunResult: {
        success: false,
        executionId: 9101,
        status: 'FAILED',
        statusLabel: '異常終了',
        message: 'プロキシサーバーの起動に失敗しました。',
        errorDetail: 'java.lang.IllegalStateException: プロキシサーバーの起動に失敗しました。'
      }
    })

    await wrapper.get('tbody tr[data-batch-code="batS01"] button').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('再実行に失敗しました。')
    expect(wrapper.text()).toContain('プロキシサーバーの起動に失敗しました。')
    expect(toastMessages().join(' ')).toContain('プロキシサーバーの起動に失敗しました。')
  })

})

/**
 * 実行タイミングと次回実行（2.1 の統合スケジューラ）。
 *
 * 2.0 は種別 R が「定時」としか出ず、何時に動くのか分からなかった。2.1 は実行時刻・実行間隔が
 * 設定で変わるので、一覧は `GET /api/admin/batch/schedule` を 1 回読んで
 * **設定に基づく実際のタイミング**（describe）と**次回実行時刻**（nextRunLabel）を出す。
 * スケジュールが取れないタスクは定義のタイミング（timingLabel）へ戻す。
 */
describe('バッチ一覧：実行タイミングと次回実行', () => {
  it('設定に基づく実行タイミングと次回実行時刻を出す', async () => {
    const { wrapper, fetchMock } = await setup({
      tasks: [
        taskRow({ taskCode: 'batR03', taskType: 'R', description: 'インターネット利用終了処理', pageCode: 'NET_CONTROL' }),
        taskRow({ taskCode: 'batL02', taskType: 'L', description: '学習モニター動画取込・スナップショット切出', pageCode: 'STUDY_MONITOR' })
      ],
      schedule: emptySchedule({
        version: 7,
        tasks: [
          scheduleTask(),
          scheduleTask({
            taskCode: 'batL02',
            describe: '毎時 01/06/…/56 分（5 分間隔・ずらし 1 分）',
            intervalMinutes: 5,
            offsetMinutes: 1,
            dailyTime: null,
            examplePoints: ['00:01', '00:06'],
            nextRunAt: '2026-09-19T12:36:00',
            nextRunLabel: '2026-09-19 12:36'
          })
        ]
      })
    })

    // スケジュールは 1 回だけ読む
    const calls = recorded(fetchMock).filter((entry) => entry.url.endsWith('/api/admin/batch/schedule'))
    expect(calls.length).toBe(1)

    const daily = wrapper.get('tbody tr[data-batch-code="batR03"]')
    // 種別は「R（定時）」だが、**実行タイミングの列には実際の時刻**が出る
    expect(daily.text()).toContain('R（定時）')
    expect(daily.text()).toContain('毎日 23:30')
    expect(daily.text()).toContain('2026-09-19 23:30')

    const interval = wrapper.get('tbody tr[data-batch-code="batL02"]')
    expect(interval.text()).toContain('毎時 01/06/…/56 分（5 分間隔・ずらし 1 分）')
    expect(interval.text()).toContain('ずらし 1 分')
    expect(interval.text()).toContain('2026-09-19 12:36')
  })

  it('スケジュールを取れないタスクは定義のタイミングのままにする', async () => {
    const { wrapper } = await setup({
      tasks: [taskRow({ taskCode: 'batS01' })],
      schedule: emptySchedule()
    })

    const row = wrapper.get('tbody tr[data-batch-code="batS01"]')
    expect(row.text()).toContain('起動時＋再実行')
    // 次回実行は分からないので「—」（バッチコード/種別/説明/有効/実行タイミング/次回実行/…）
    expect(row.findAll('td')[5].text()).toBe('—')
  })

  it('反映待ちのときは控えめな注意表示を出す', async () => {
    const { wrapper } = await setup({
      schedule: emptySchedule({
        pendingRefresh: true,
        pendingMessage: '保存済み・実行設定への反映待ち（ずらしが範囲外です）。自動で再試行します。'
      })
    })

    const notice = wrapper.get('[data-testid="batch-pending"]')
    expect(notice.text()).toContain('保存済み・実行設定への反映待ち（ずらしが範囲外です）。自動で再試行します。')
  })

  it('反映待ちが無ければ注意表示を出さない', async () => {
    const { wrapper } = await setup()

    expect(wrapper.find('[data-testid="batch-pending"]').exists()).toBe(false)
  })

  it('スケジュール API が落ちても一覧は出す（定義のタイミングへ戻す）', async () => {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const method = (init?.method ?? 'GET').toUpperCase()
      if (String(url).includes('/api/admin/batch/schedule')) {
        return new Response(JSON.stringify({ success: false, code: 'ERROR', message: '取得できませんでした。', data: null }), {
          status: 500,
          headers: { 'Content-Type': 'application/json' }
        })
      }
      if (String(url).includes('/api/admin/batch/executions')) {
        return ok({ items: [], totalElements: 0, page: 1, size: 15, totalPages: 1 })
      }
      void method
      return ok({ rows: [taskRow({ taskCode: 'batR03', taskType: 'R' })], totalCount: 1, startupTargets: [] })
    })
    vi.stubGlobal('fetch', fetchMock)
    const pinia = createPinia()
    setActivePinia(pinia)
    const wrapper = mount(BatchListView, { global: { plugins: [pinia] } })
    await flushPromises()
    await flushPromises()

    expect(wrapper.find('.alert--danger').exists()).toBe(false)
    expect(wrapper.get('tbody tr[data-batch-code="batR03"]').text()).toContain('定時')
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import BatchSchedulePanel from '@/features/batch/BatchSchedulePanel.vue'
import type { BatchScheduleTask } from '@/api/batch'

/**
 * 実行スケジュール（読み取り専用）— 設定画面の最上部に出すカード。
 *
 * 2.1 は 4 タスク（batR03 / batR04 / batL02 / batL03）を 1 つのスケジューラで動かし、
 * 実行タイミングは `COM_設定情報` から読む。ここでは
 *
 * ・時計（zone = Asia/Tokyo）・設定の版・読み込み時刻を出すこと
 * ・タスクごとに**実行タイミング**（describe）と**次回実行時刻**（nextRunLabel）を出すこと
 * ・**実行時間の例**（examplePoints）と実行中／実行待ちの本数を出すこと
 * ・**設定の状態**（有効 / 未設定 / 設定不正）を出し、未設定・設定不正は理由が分かること
 * ・**Cron を編集させない**（入力欄を出さないこと。有効／無効のスイッチも置かない）
 * ・**【設定を再読み込み】**が `POST /api/admin/batch/schedule/reload` を叩き、
 *   成功／失敗（＝反映待ち。前の設定のまま動く）を出すこと
 * ・`pendingRefresh` のときは**「保存済み・実行設定への反映待ち」と理由・次の再試行**を
 *   目立つ形（alert--warning）で出すこと
 *
 * を固定する。
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function task(overrides: Partial<BatchScheduleTask> = {}): BatchScheduleTask {
  return {
    taskCode: 'batL02',
    status: 'LOADED',
    statusLabel: '有効',
    enabled: true,
    describe: '毎時 01/06/…/56 分（5 分間隔・ずらし 1 分）',
    intervalMinutes: 5,
    offsetMinutes: 1,
    dailyTime: null,
    examplePoints: ['00:01', '00:06', '00:11'],
    nextRunAt: '2026-09-19T12:36:00',
    nextRunLabel: '2026-09-19 12:36',
    configEffectiveFrom: null,
    lastPlannedAt: null,
    ...overrides
  }
}

function payload(overrides: Record<string, unknown> = {}): Record<string, unknown> {
  return {
    zone: 'Asia/Tokyo',
    version: 7,
    loadedAt: '2026-09-19T03:30:00Z',
    pendingRefresh: false,
    pendingMessage: null,
    lastRefreshAt: '2026-09-19T03:30:00Z',
    lastRefreshError: null,
    nextRetryAt: null,
    checkIntervalSeconds: 30,
    runningWorkers: 1,
    queuedWorkers: 2,
    tasks: [
      task({ taskCode: 'batR03', describe: '毎日 23:30', intervalMinutes: null, offsetMinutes: null, dailyTime: '23:30', examplePoints: ['23:30'], nextRunLabel: '2026-09-19 23:30' }),
      task({ taskCode: 'batR04', describe: '毎日 06:30', intervalMinutes: null, offsetMinutes: null, dailyTime: '06:30', examplePoints: ['06:30'], nextRunLabel: '2026-09-20 06:30' }),
      task(),
      task({
        taskCode: 'batL03',
        status: 'INVALID',
        statusLabel: '設定不正',
        enabled: true,
        describe: '設定が不正なため実行しません',
        intervalMinutes: null,
        offsetMinutes: null,
        examplePoints: [],
        nextRunAt: null,
        nextRunLabel: '設定が不正なため次回実行はありません'
      })
    ],
    ...overrides
  }
}

type Call = { url: string; method: string; body: Record<string, unknown> | null }

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

async function setup(options: { schedule?: unknown; reload?: unknown } = {}) {
  const fetchMock = vi.fn(async (url: string) => {
    if (String(url).includes('/schedule/reload')) {
      return ok(options.reload ?? { ...payload(), refreshPublished: true, refreshError: null, message: '実行設定を読み直しました。' })
    }
    return ok(options.schedule ?? payload())
  })
  vi.stubGlobal('fetch', fetchMock)
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(BatchSchedulePanel, { global: { plugins: [pinia] } })
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock }
}

describe('実行スケジュール（読み取り専用）', () => {
  let wrapper: VueWrapper | null = null

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    vi.unstubAllGlobals()
  })

  it('時計・版・読み込み時刻・実行の本数を出す', async () => {
    const context = await setup()
    wrapper = context.wrapper

    const text = wrapper.text()
    expect(text).toContain('Asia/Tokyo')
    expect(text).toContain('設定の版 7')
    expect(text).toContain('読み込み')
    expect(text).toContain('実行中 1 本')
    expect(text).toContain('実行待ち 2 本')

    // Cron は編集させない（この画面に時刻・間隔の入力欄は 1 つも無い）
    expect(wrapper.findAll('input').length).toBe(0)
    expect(wrapper.find('select').exists()).toBe(false)
    // 有効／無効のスイッチも置かない（唯一の入口はバッチ一覧のスイッチ）
    expect(wrapper.find('input[type="checkbox"]').exists()).toBe(false)
    expect(text).toContain('バッチ一覧')
  })

  it('タスクごとの実行タイミング・次回実行時刻・実行時間の例を出す', async () => {
    const context = await setup()
    wrapper = context.wrapper

    const daily = wrapper.get('tbody tr[data-task-code="batR03"]')
    expect(daily.text()).toContain('毎日 23:30')
    expect(daily.text()).toContain('2026-09-19 23:30')
    expect(daily.text()).toContain('有効')

    const interval = wrapper.get('tbody tr[data-task-code="batL02"]')
    expect(interval.text()).toContain('毎時 01/06/…/56 分（5 分間隔・ずらし 1 分）')
    expect(interval.text()).toContain('2026-09-19 12:36')
    expect(interval.text()).toContain('00:01 / 00:06 / 00:11')

    // 種別 R が「定時」とだけ出て時刻が分からない状態にしない
    expect(wrapper.text()).not.toContain('定時')
  })

  it('実行設定の適用時刻があるときは「この時刻以降」を出す（変更直後に過去の点を実行しない）', async () => {
    const context = await setup({
      schedule: payload({
        tasks: [
          task({
            taskCode: 'batR03',
            describe: '毎日 21:00',
            intervalMinutes: null,
            offsetMinutes: null,
            dailyTime: '21:00',
            examplePoints: ['21:00'],
            nextRunLabel: '2026-09-20 21:00',
            configEffectiveFrom: '2026-09-19 22:00'
          }),
          task()
        ]
      })
    })
    wrapper = context.wrapper

    // 22:00 に 23:30 → 21:00 へ変更した場合、次回は翌日 21:00（今日の 21:00 は適用時刻より前）
    const changed = wrapper.get('tbody tr[data-task-code="batR03"]')
    expect(changed.text()).toContain('2026-09-20 21:00')
    expect(changed.get('[data-testid="task-effective-from"]').text())
      .toContain('設定の適用: 2026-09-19 22:00')
    // 実行設定を変えていないタスクには出さない（無関係な設定の保存で過去の補償を止めない）
    expect(wrapper.get('tbody tr[data-task-code="batL02"]')
      .find('[data-testid="task-effective-from"]').exists()).toBe(false)
  })

  it('設定が未設定・設定不正のタスクは状態と理由を出す', async () => {
    const context = await setup()
    wrapper = context.wrapper

    const invalid = wrapper.get('tbody tr[data-task-code="batL03"]')
    expect(invalid.text()).toContain('設定不正')
    expect(invalid.text()).toContain('設定が不正なため次回実行はありません')
    expect(invalid.get('.badge').classes()).toContain('badge--danger')
  })

  it('【設定を再読み込み】で reload API を叩き、結果を出す', async () => {
    const context = await setup()
    wrapper = context.wrapper

    await wrapper.get('[data-testid="schedule-reload"]').trigger('click')
    await flushPromises()

    const call = recorded(context.fetchMock).find((entry) => entry.url.endsWith('/api/admin/batch/schedule/reload'))
    expect(call?.method).toBe('POST')
    const result = wrapper.get('[data-testid="schedule-reload-result"]')
    expect(result.text()).toContain('実行設定を読み直しました。')
    expect(result.classes()).not.toContain('batch-schedule__result--failed')
  })

  it('再読み込みに失敗したら「前の設定のまま動きます」を出す（反映待ち）', async () => {
    const context = await setup({
      reload: {
        ...payload({
          pendingRefresh: true,
          pendingMessage: '保存済み・実行設定への反映待ち（時刻の解釈に失敗しました）。自動で再試行します。',
          lastRefreshError: '時刻の解釈に失敗しました',
          nextRetryAt: '2026-09-19T03:35:00Z'
        }),
        refreshPublished: false,
        refreshError: '時刻の解釈に失敗しました',
        message: '実行設定を読み直せませんでした（前の設定のまま動きます）。時刻の解釈に失敗しました'
      }
    })
    wrapper = context.wrapper

    await wrapper.get('[data-testid="schedule-reload"]').trigger('click')
    await flushPromises()

    const result = wrapper.get('[data-testid="schedule-reload-result"]')
    expect(result.text()).toContain('前の設定のまま動きます')
    expect(result.classes()).toContain('batch-schedule__result--failed')
  })

  it('反映待ちのときは理由と次の再試行を目立つ形で出す', async () => {
    const context = await setup({
      schedule: payload({
        pendingRefresh: true,
        pendingMessage: '保存済み・実行設定への反映待ち（ずらしが範囲外です）。自動で再試行します。',
        lastRefreshError: 'ずらしが範囲外です',
        nextRetryAt: '2026-09-19T03:35:00Z'
      })
    })
    wrapper = context.wrapper

    const pending = wrapper.get('[data-testid="schedule-pending"]')
    expect(pending.text()).toContain('保存済み・実行設定への反映待ち')
    expect(pending.text()).toContain('ずらしが範囲外です')
    expect(pending.text()).toContain('次の再試行')
    // 目立たせる（警告の見た目）
    expect(pending.classes()).toContain('alert--warning')
  })

  it('スケジュールを取得できないときはエラーを出して表を出さない', async () => {
    const fetchMock = vi.fn(async () =>
      new Response(JSON.stringify({ success: false, code: 'ERROR', message: '実行スケジュールを取得できませんでした。', data: null }), {
        status: 500,
        headers: { 'Content-Type': 'application/json' }
      }))
    vi.stubGlobal('fetch', fetchMock)
    const pinia = createPinia()
    setActivePinia(pinia)
    wrapper = mount(BatchSchedulePanel, { global: { plugins: [pinia] } })
    await flushPromises()
    await flushPromises()

    expect(wrapper.get('.alert--danger').text()).toContain('実行スケジュールを取得できませんでした。')
    expect(wrapper.find('tbody').exists()).toBe(false)
  })
})

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { nextTick } from 'vue'
import { createPinia, setActivePinia } from 'pinia'
import SystemSettingsView from '@/views/admin/system-settings/SystemSettingsView.vue'

/**
 * システム設定：**バッチの実行設定**と**実行スケジュール（読み取り専用）**（2026-09-19）。
 *
 * 2.1 は batR03 / batR04 / batL02 / batL03 を 1 つのスケジューラで動かし、実行タイミングは
 * `COM_設定情報` から読む。ここでは画面が
 *
 * ・「ネットワーク制御」の分頁にインターネット利用の開始／終了時刻（`netControlStartTime` /
 *   `netControlEndTime`）を持ち、**実行の有効／無効のスイッチは置かない**こと
 * ・「学習状況モニター」の batL02 / batL03 に**実行設定**（実行間隔＋ずらし）を足し、
 *   **動画処理時間帯**（どの撮影時刻の動画を取り込むか）と
 *   **スナップショット間隔**（何秒ごとに画像を切るか）とは**別の欄**として出すこと
 * ・ずらしの候補が 0〜実行間隔-1 分に追随し、範囲外は**保存の前に**弾くこと
 * ・実行スケジュール（時計・版・タスクごとの実行タイミング・次回実行・実行時間の例）を
 *   読み取り専用で出すこと（Cron の入力欄は出さない）
 * ・【設定を再読み込み】の結果（成功／失敗＝反映待ち）を出すこと
 *
 * を固定する。保存の経路はランタイムが持つので、**実際に DOM を操作して送信内容を見る**。
 */
function jsonResponse(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

/** 実行スケジュール（バックエンドの ScheduleConfigReport と同じ形）。 */
function schedulePayload(overrides: Record<string, unknown> = {}): Record<string, unknown> {
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
      {
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
        lastPlannedAt: null
      },
      {
        taskCode: 'batR04',
        status: 'LOADED',
        statusLabel: '有効',
        enabled: true,
        describe: '毎日 06:30',
        intervalMinutes: null,
        offsetMinutes: null,
        dailyTime: '06:30',
        examplePoints: ['06:30'],
        nextRunAt: '2026-09-20T06:30:00',
        nextRunLabel: '2026-09-20 06:30',
        lastPlannedAt: null
      },
      {
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
        lastPlannedAt: null
      },
      {
        taskCode: 'batL03',
        status: 'MISSING',
        statusLabel: '未設定',
        enabled: null,
        describe: '設定が無いため実行しません',
        intervalMinutes: null,
        offsetMinutes: null,
        dailyTime: null,
        examplePoints: [],
        nextRunAt: null,
        nextRunLabel: '設定が無いため次回実行はありません',
        lastPlannedAt: null
      }
    ],
    ...overrides
  }
}

/** 設定ページを実 DOM に載せる（ランタイムが document から要素を引くため attachTo が要る）。 */
function setup(options: { settings?: Record<string, string>; schedule?: unknown; reload?: Record<string, unknown> } = {}) {
  const saved: Array<Record<string, string>> = []
  const fetchMock = vi.fn((url: string, request?: RequestInit) => {
    const body = request?.body === undefined ? null : (JSON.parse(String(request.body)) as Record<string, unknown>)
    if (String(url).includes('/api/admin/batch/schedule/reload')) {
      // reload は同じ形＋反映の結果（refreshPublished / refreshError / message）を返す
      const data = (options.schedule ?? schedulePayload()) as Record<string, unknown>
      return Promise.resolve(jsonResponse({
        ...data,
        refreshPublished: true,
        refreshError: null,
        message: '実行設定を読み直しました。',
        ...(options.reload ?? {})
      }))
    }
    if (String(url).includes('/api/admin/batch/schedule')) {
      return Promise.resolve(jsonResponse(options.schedule ?? schedulePayload()))
    }
    if (String(url).includes('/saveSettings')) {
      const settings = (body?.settings ?? {}) as Record<string, string>
      saved.push(settings)
      return Promise.resolve(jsonResponse({ settings }, '設定を保存しました。'))
    }
    if (String(url).includes('/initSettings')) {
      return Promise.resolve(jsonResponse({ settings: options.settings ?? {} }))
    }
    return Promise.resolve(jsonResponse({}))
  })
  vi.stubGlobal('fetch', fetchMock)
  const pinia = createPinia()
  setActivePinia(pinia)
  const wrapper = mount(SystemSettingsView, { global: { plugins: [pinia] }, attachTo: document.body })
  return { wrapper, fetchMock, saved }
}

/** カテゴリのタブを開く（設定ランタイムはカテゴリごとにパネルを hidden で切り替える）。 */
async function openCategory(wrapper: VueWrapper, categoryId: string): Promise<void> {
  const button = wrapper.get(`.setting-category-btn[data-category="${categoryId}"]`)
  await button.trigger('click')
  await flushPromises()
}

/** 実行設定の欄（ラジオ）を選ぶ（実際の操作と同じく change を投げる）。 */
async function chooseRadio(key: string, value: string): Promise<void> {
  const input = document.querySelector<HTMLInputElement>(`input[name="setting_${key}"][value="${value}"]`)
  if (!input) throw new Error(`ラジオがありません: setting_${key} = ${value}`)
  input.checked = true
  input.dispatchEvent(new Event('change', { bubbles: true }))
  await flushPromises()
}

/** ずらしの欄に出ている選択肢（分）。 */
function offsetOptionValues(key: string): string[] {
  return Array.from(document.querySelectorAll<HTMLInputElement>(`input[name="setting_${key}"]`))
    .map((input) => input.value)
}

function saveRequestBody(fetchMock: ReturnType<typeof vi.fn>): Record<string, string> | null {
  const call = fetchMock.mock.calls.find((entry) => String(entry[0]).includes('/saveSettings'))
  if (!call) return null
  const body = JSON.parse(String((call[1] as RequestInit).body)) as { settings?: Record<string, string> }
  return body.settings ?? null
}

describe('システム設定：ネットワーク制御と学習状況モニターの実行設定', () => {
  let wrapper: VueWrapper | null = null
  beforeEach(() => {
    document.body.innerHTML = ''
    window.location.hash = ''
  })

  afterEach(() => {
    wrapper?.unmount()
    wrapper = null
    document.body.innerHTML = ''
    vi.unstubAllGlobals()
  })

  async function open(settings: Record<string, string> = {}): Promise<ReturnType<typeof setup>> {
    const context = setup({ settings })
    wrapper = context.wrapper
    // 実行スケジュールは Teleport 先（#settingScheduleSlot）へ入る子コンポーネントなので、
    // 設定の読み込み（initSettings）と合わせて 2 巡まわす
    await flushPromises()
    await flushPromises()
    await nextTick()
    await flushPromises()
    return context
  }

  it('「ネットワーク制御」にインターネット利用の開始／終了時刻を出す', async () => {
    await open({ netControlStartTime: '06:30', netControlEndTime: '23:30' })
    await openCategory(wrapper as VueWrapper, 'network_control')

    const start = document.querySelector<HTMLInputElement>('#setting_netControlStartTime')
    const end = document.querySelector<HTMLInputElement>('#setting_netControlEndTime')
    expect(start?.type).toBe('time')
    expect(start?.value).toBe('06:30')
    expect(end?.value).toBe('23:30')

    const panel = document.querySelector('[data-panel="network_control"]')
    expect(panel?.textContent).toContain('batR04')
    expect(panel?.textContent).toContain('batR03')
    // 有効／無効は**バッチ一覧のスイッチ**で切り替える（設定画面に 2 つ目のスイッチを作らない）
    expect(panel?.textContent).toContain('バッチ一覧')
    expect(panel?.querySelector('input[type="checkbox"]')).toBeNull()
  })

  it('保存すると実行設定の値も一緒に送る（ネットワーク制御と batL02 / batL03）', async () => {
    const context = await open({
      netControlStartTime: '06:30',
      netControlEndTime: '23:30',
      monitorL02IntervalMinutes: '5',
      monitorL02OffsetMinutes: '1',
      monitorL03IntervalMinutes: '15',
      monitorL03OffsetMinutes: '3'
    })

    await wrapper?.get('#settingSaveBtn').trigger('click')
    await flushPromises()

    const settings = saveRequestBody(context.fetchMock)
    expect(settings).not.toBeNull()
    expect(settings?.netControlStartTime).toBe('06:30')
    expect(settings?.netControlEndTime).toBe('23:30')
    expect(settings?.monitorL02IntervalMinutes).toBe('5')
    expect(settings?.monitorL02OffsetMinutes).toBe('1')
    expect(settings?.monitorL03IntervalMinutes).toBe('15')
    expect(settings?.monitorL03OffsetMinutes).toBe('3')
    // サーバーが知らないキーを混ぜない（未定義キーがあると 400 で何も保存されない）
    expect(Object.keys(settings ?? {}).filter((key) => key.startsWith('scheduleNotice'))).toEqual([])
  })

  it('実行間隔・動画処理時間帯・スナップショット間隔を別の欄として出す（混同させない）', async () => {
    await open({ monitorL02IntervalMinutes: '5', monitorL02OffsetMinutes: '1' })
    await openCategory(wrapper as VueWrapper, 'study_monitor')

    // 3 つは別の欄（違う id の入力）として存在する
    expect(document.querySelector('#setting_monitorL02IntervalMinutes')).not.toBeNull()
    expect(document.querySelector('#setting_monitorL02OffsetMinutes')).not.toBeNull()
    expect(document.querySelector('#setting_monitorVideoProcessingStartTime')).not.toBeNull()
    expect(document.querySelector('#setting_monitorVideoProcessingEndTime')).not.toBeNull()
    expect(document.querySelector('#setting_monitorSnapshotIntervalSeconds')).not.toBeNull()

    // 説明で区別する（実行間隔＝バッチをいつ動かすか / 時間帯＝どの撮影時刻の動画か / 間隔＝何秒ごとに切るか）
    const section = document.querySelector('[data-setting-subsection="bat-l02"]')
    const text = section?.textContent ?? ''
    expect(text).toContain('実行間隔')
    expect(text).toContain('バッチ')
    expect(text).toContain('動画処理時間帯')
    expect(text).toContain('撮影開始')
    expect(text).toContain('スナップショット間隔')
    expect(text).toContain('バッチの実行間隔ではありません')

    // batL03 にも実行設定がある（AI 分析の条件とは別）
    const l03 = document.querySelector('[data-setting-subsection="bat-l03"]')
    expect(l03?.querySelector('#setting_monitorL03IntervalMinutes')).not.toBeNull()
    expect(l03?.querySelector('#setting_monitorL03OffsetMinutes')).not.toBeNull()
  })

  it('ずらしの候補は 0〜実行間隔-1 分で、実行間隔を変えると追随する', async () => {
    await open({ monitorL02IntervalMinutes: '5', monitorL02OffsetMinutes: '1' })
    await openCategory(wrapper as VueWrapper, 'study_monitor')

    // 実行間隔 5 分 → ずらしは 0〜4 の 5 択
    expect(offsetOptionValues('monitorL02OffsetMinutes')).toEqual(['0', '1', '2', '3', '4'])
    expect(document.querySelector<HTMLInputElement>('input[name="setting_monitorL02OffsetMinutes"][value="1"]')?.checked)
      .toBe(true)

    // 実行間隔を 60 分に変えると 0〜59 の 60 択になる
    await chooseRadio('monitorL02IntervalMinutes', '60')
    expect(offsetOptionValues('monitorL02OffsetMinutes').length).toBe(60)
    expect(document.querySelector<HTMLInputElement>('input[name="setting_monitorL02OffsetMinutes"][value="1"]')?.checked)
      .toBe(true)

    // 実行間隔を 1 分に変えると候補は 0 だけ。いまの 1 分は範囲外なので選択が外れ、
    // 選び直しを案内する（範囲外のまま保存させない）
    await chooseRadio('monitorL02IntervalMinutes', '1')
    expect(offsetOptionValues('monitorL02OffsetMinutes')).toEqual(['0'])
    expect(document.querySelector('#settingToast')?.textContent).toContain('ずらしを 0〜0 分に選び直してください')
  })

  it('保存値のずらしが範囲外なら、その値を「範囲外」として見せて保存前に弾く', async () => {
    const context = await open({ monitorL03IntervalMinutes: '5', monitorL03OffsetMinutes: '9' })
    await openCategory(wrapper as VueWrapper, 'study_monitor')

    // 範囲外の値も消さずに出す（何が保存されているか分かるように）
    const options = offsetOptionValues('monitorL03OffsetMinutes')
    expect(options[0]).toBe('9')
    expect(document.querySelector<HTMLInputElement>('input[name="setting_monitorL03OffsetMinutes"][value="9"]')?.checked)
      .toBe(true)
    expect(document.querySelector('#setting_monitorL03OffsetMinutes')?.textContent).toContain('範囲外')

    await wrapper?.get('#settingSaveBtn').trigger('click')
    await flushPromises()

    // 保存は送られず、理由がトーストに出る（範囲外はスケジューラが「設定不正」として止める）
    expect(saveRequestBody(context.fetchMock)).toBeNull()
    const toast = document.querySelector('#settingToast')?.textContent ?? ''
    expect(toast).toContain('ずらし')
    expect(toast).toContain('batL03')
  })

  it('実行設定は「基本設定」タブに出す（AI レイアウトの名前一致に巻き込まれない）', async () => {
    await open({ monitorL03IntervalMinutes: '5', monitorL03OffsetMinutes: '1' })
    await openCategory(wrapper as VueWrapper, 'study_monitor')

    // `monitorL03IntervalMinutes` は AI の通信条件の名前に似ている（…Timeout / …Provider）ため、
    // TAB の判定に巻き込まれると「その他」へ入ってしまう
    const basic = document.querySelector('[data-setting-subsection="bat-l03"] [data-method-panel="基本設定"]')
    expect(basic?.querySelector('#setting_monitorL03IntervalMinutes')).not.toBeNull()
    expect(basic?.querySelector('#setting_monitorL03OffsetMinutes')).not.toBeNull()
    const other = document.querySelector('[data-setting-subsection="bat-l03"] [data-method-panel="その他"]')
    expect(other?.querySelector('#setting_monitorL03IntervalMinutes')).toBeNull()
    expect(other?.querySelector('#setting_monitorL03OffsetMinutes')).toBeNull()
  })

  it('【設定を再読み込み】で reload API を叩き、結果を出す', async () => {
    const context = await open()
    document.querySelector<HTMLButtonElement>('[data-testid="schedule-reload"]')?.click()
    await flushPromises()

    const call = context.fetchMock.mock.calls.find((entry) => String(entry[0]).includes('/api/admin/batch/schedule/reload'))
    expect(call).toBeTruthy()
    expect((call?.[1] as RequestInit | undefined)?.method).toBe('POST')
    // 結果は Vue が描き直すので 1 ティック待つ
    await flushPromises()
    await nextTick()
    expect(document.querySelector('[data-testid="schedule-reload-result"]')?.textContent)
      .toContain('実行設定を読み直しました。')
  })

  it('再読み込みに失敗したら「前の設定のまま動きます」を出す（反映待ち）', async () => {
    const context = await open()
    // reload だけ「反映できなかった」を返すように差し替える
    context.fetchMock.mockImplementation((url: string) => {
      if (String(url).includes('/schedule/reload')) {
        return Promise.resolve(jsonResponse({
          ...schedulePayload({
            pendingRefresh: true,
            pendingMessage: '保存済み・実行設定への反映待ち（時刻の解釈に失敗しました）。自動で再試行します。',
            lastRefreshError: '時刻の解釈に失敗しました',
            nextRetryAt: '2026-09-19T03:35:00Z'
          }),
          refreshPublished: false,
          refreshError: '時刻の解釈に失敗しました',
          message: '実行設定を読み直せませんでした（前の設定のまま動きます）。時刻の解釈に失敗しました'
        }))
      }
      return Promise.resolve(jsonResponse(schedulePayload()))
    })

    document.querySelector<HTMLButtonElement>('[data-testid="schedule-reload"]')?.click()
    await flushPromises()

    const reloadResult = document.querySelector('[data-testid="schedule-reload-result"]')
    expect(reloadResult?.textContent).toContain('前の設定のまま動きます')
    expect(reloadResult?.classList.contains('batch-schedule__result--failed')).toBe(true)
    // 反映待ちの案内も出る（理由と次の再試行）
    const pending = document.querySelector('[data-testid="schedule-pending"]')
    expect(pending?.textContent).toContain('保存済み・実行設定への反映待ち')
    expect(pending?.textContent).toContain('次の再試行')
  })

  it('実行スケジュールを読み取り専用で出す（Cron は編集させない）', async () => {
    await open()

    const panel = document.querySelector('[aria-label="実行スケジュール"]')
    expect(panel).not.toBeNull()
    const text = panel?.textContent ?? ''
    expect(text).toContain('Asia/Tokyo')
    expect(text).toContain('設定の版 7')
    expect(text).toContain('実行中 1 本')
    expect(text).toContain('実行待ち 2 本')
    expect(text).toContain('毎日 23:30')
    expect(text).toContain('2026-09-19 23:30')
    expect(text).toContain('毎時 01/06/…/56 分（5 分間隔・ずらし 1 分）')
    expect(text).toContain('2026-09-19 12:36')
    expect(text).toContain('00:01 / 00:06 / 00:11')
    // 未設定のタスクは理由が分かる（MISSING / INVALID は自動実行しない）
    expect(text).toContain('未設定')
    expect(text).toContain('設定が無いため次回実行はありません')
    // Cron は編集させない（入力欄を出さない）。有効／無効のスイッチも置かない
    expect(panel?.querySelectorAll('input, select, textarea').length).toBe(0)
    // 実行スケジュールは設定カテゴリの**上**に置く
    const workspace = document.querySelector('.setting-workspace')
    expect(workspace).not.toBeNull()
    const position = (panel as Element).compareDocumentPosition(workspace as Node)
    expect((position & Node.DOCUMENT_POSITION_FOLLOWING) !== 0).toBe(true)
  })

  it('反映待ちのときは理由と次の再試行を目立つ形で出す', async () => {
    const context = setup({
      schedule: schedulePayload({
        pendingRefresh: true,
        pendingMessage: '保存済み・実行設定への反映待ち（ずらしが範囲外です）。自動で再試行します。',
        lastRefreshError: 'ずらしが範囲外です',
        nextRetryAt: '2026-09-19T03:35:00Z'
      })
    })
    wrapper = context.wrapper
    await flushPromises()
    await flushPromises()

    const pending = document.querySelector('[data-testid="schedule-pending"]')
    expect(pending?.textContent).toContain('保存済み・実行設定への反映待ち')
    expect(pending?.textContent).toContain('ずらしが範囲外です')
    expect(pending?.textContent).toContain('次の再試行')
    expect(pending?.classList.contains('alert--warning')).toBe(true)
  })
})

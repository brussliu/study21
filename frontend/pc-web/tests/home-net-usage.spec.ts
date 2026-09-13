import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import NetUsageChart from '@/components/home/NetUsageChart.vue'

/**
 * ホームの「最近3日 上網状況（時間帯別）」（2.0 home.jsp の net-usage-card）。
 *
 * 実データ（プロキシの通信履歴）を 3 日 × 24 時間の棒グラフで出す。
 * 区分（サイトの区分）と端末で絞り込むと、その条件で取り直す。
 */
function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function summary(overrides: Partial<{
  days: string[]
  buckets: number[][]
  totalCount: number
}> = {}) {
  const buckets = overrides.buckets ?? [
    Array.from({ length: 24 }, (_, hour) => (hour === 9 ? 100 : hour === 20 ? 50 : 0)),
    Array.from({ length: 24 }, (_, hour) => (hour === 9 ? 40 : 0)),
    Array.from({ length: 24 }, (_, hour) => (hour === 21 ? 10 : 0))
  ]
  return {
    days: overrides.days ?? ['2026-09-12', '2026-09-11', '2026-09-10'],
    buckets,
    totalCount: overrides.totalCount ?? buckets.flat().reduce((sum, count) => sum + count, 0),
    terminalName: null,
    kind: null
  }
}

type Call = { url: string }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => ({ url: String(call[0]) }))
}

async function setup(data = summary()) {
  const fetchMock = vi.fn(async (url: string) => {
    if (String(url).includes('/net-access-logs/hourly-summary')) return ok(data)
    if (String(url).includes('/net-terminals')) {
      return ok({
        items: [
          { terminalId: 1, ipAddress: '192.168.0.92', terminalName: '勉強用PC', terminalMode: 'T', status: '1' },
          { terminalId: 2, ipAddress: '192.168.0.60', terminalName: 'ゲーム用PC', terminalMode: 'T', status: '1' }
        ],
        page: 1, size: 200, totalElements: 2, totalPages: 1
      })
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(NetUsageChart)
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock }
}

beforeEach(() => {
  vi.restoreAllMocks()
})

describe('ホーム：最近3日 上網状況（時間帯別）', () => {
  it('3 日 × 24 時間の棒を出し、凡例と件数を表示する', async () => {
    const { wrapper } = await setup()

    expect(wrapper.text()).toContain('最近3日 上網状況（時間帯別）')
    // 24 時間ぶんの列
    expect(wrapper.findAll('.home-net-usage__column').length).toBe(24)
    // 3 日 × 24 時間の棒
    expect(wrapper.findAll('[data-net-usage-bar]').length).toBe(72)
    // 今日 150 件 + 昨日 40 件 + 一昨日 10 件
    expect(wrapper.get('[data-net-usage-meta]').text()).toContain('200 件')
    const legend = wrapper.get('.home-net-usage__legend').text()
    for (const label of ['今日', '昨日', '一昨日']) {
      expect(legend).toContain(label)
    }
    expect(legend).toContain('150 件')
    // 一番多い時間帯（今日 9 時 = 100 件）が最大の高さ
    expect(wrapper.get('[data-net-usage-bar="0-9"]').attributes('style')).toContain('height: 100%')
    expect(wrapper.get('[data-net-usage-bar="1-9"]').attributes('style')).toContain('height: 40%')
    // 0 件は高さ 0
    expect(wrapper.get('[data-net-usage-bar="0-0"]').attributes('style')).toContain('height: 0%')
  })

  it('区分と端末で絞り込むと、その条件で取り直す', async () => {
    const { wrapper, fetchMock } = await setup()

    const kindSelect = wrapper.get('[data-net-usage-kind]')
    // 区分は「すべて」＋ 4 種類（0.勉強 / 1.通常 / 2.休憩 / 3.ゲーム）
    expect(kindSelect.findAll('option').map((option) => option.text()))
      .toEqual(['すべて', '0.勉強', '1.通常', '2.休憩', '3.ゲーム'])

    await kindSelect.setValue('BREAK')
    await flushPromises()
    expect(recorded(fetchMock).at(-1)?.url).toContain('kind=BREAK')

    // 端末は登録端末のドロップダウン
    const terminalSelect = wrapper.get('[data-net-usage-terminal]')
    expect(terminalSelect.findAll('option').map((option) => option.text()))
      .toEqual(['すべて', 'ゲーム用PC', '勉強用PC'])
    await terminalSelect.setValue('勉強用PC')
    await flushPromises()
    const last = recorded(fetchMock).at(-1)?.url ?? ''
    expect(last).toContain('kind=BREAK')
    expect(last).toContain(`terminalName=${encodeURIComponent('勉強用PC')}`)
  })

  it('0 件のときは案内を出す（棒は出さない）', async () => {
    const { wrapper } = await setup(summary({ buckets: [new Array(24).fill(0), new Array(24).fill(0), new Array(24).fill(0)], totalCount: 0 }))

    expect(wrapper.text()).toContain('この条件の上網状況はありません。')
    expect(wrapper.findAll('[data-net-usage-bar]').length).toBe(0)
  })
})

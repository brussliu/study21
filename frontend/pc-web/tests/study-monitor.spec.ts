import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import StudyMonitorView from '@/views/study-monitor/StudyMonitorView.vue'
import type {
  StudyMonitorSearchResult,
  StudyMonitorSnapshot,
  StudyMonitorVideo
} from '@/api/studyMonitor'

/**
 * 学習状況モニター（2.0 の study_monitor.jsp 相当。レイアウトも 2.0 のまま）。
 *
 * データは `MON_学習モニター*`（2.0 から移行済み）を API で読む。
 * 取り込み（batL02）と AI 分析（batL03）は 2.1 では未実装。
 */
function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function snapshot(
  id: number,
  time: string,
  state: StudyMonitorSnapshot['analysisState'],
  result: StudyMonitorSnapshot['resultCode'],
  videoId = 9
): StudyMonitorSnapshot {
  return {
    snapshotId: id,
    videoId,
    videoFileName: '00_20260726155346_20260726160140.mp4',
    capturedAt: `2026-07-26T${time}`,
    imagePath: `20260726/snapshot_000${id}.jpg`,
    width: 3840,
    height: 2160,
    analysisState: state,
    resultCode: result,
    confidence: 0.9,
    reason: result === 'AWAY' ? '椅子に誰も座っていない' : null,
    manual: false,
    version: 1
  }
}

function video(id: number, fileName: string, startedAt: string, snapshots: StudyMonitorSnapshot[]): StudyMonitorVideo {
  return {
    videoId: id,
    fileName,
    startedAt: `2026-07-26T${startedAt}`,
    endedAt: '2026-07-26T16:01:40',
    durationSeconds: 474,
    importState: 'IMPORTED',
    snapshotCount: snapshots.length,
    completedCount: snapshots.filter((item) => item.analysisState === 'COMPLETED').length
  }
}

function searchResult(overrides: Partial<StudyMonitorSearchResult> = {}): StudyMonitorSearchResult {
  const snapshots = overrides.snapshots ?? [
    snapshot(954, '15:53:46', 'COMPLETED', 'STUDY_NO_PC'),
    snapshot(955, '15:54:46', 'COMPLETED', 'STUDY_PC'),
    snapshot(956, '15:55:46', 'COMPLETED', 'AWAY'),
    snapshot(957, '15:56:46', 'WAITING', null)
  ]
  const videos = overrides.videos ?? [video(9, '00_20260726155346_20260726160140.mp4', '15:53:46', snapshots)]
  const resultCounts: Record<string, number> = {
    STUDY_NO_PC: 0, STUDY_PC: 0, AWAY: 0, PC_NON_STUDY: 0, OTHER: 0, UNKNOWN: 0
  }
  for (const item of snapshots) {
    if (item.resultCode !== null) resultCounts[item.resultCode] = (resultCounts[item.resultCode] ?? 0) + 1
  }
  return {
    date: '2026-07-26',
    timeFrom: '08:00:00',
    timeTo: '23:59:00',
    videos,
    snapshots,
    summary: overrides.summary ?? {
      videoCount: videos.length,
      snapshotCount: snapshots.length,
      completed: snapshots.filter((s) => s.analysisState === 'COMPLETED').length,
      waiting: snapshots.filter((s) => s.analysisState === 'WAITING').length,
      errors: snapshots.filter((s) => s.analysisState === 'ERROR').length,
      resultCounts
    }
  }
}

type Call = { url: string; method: string; body: unknown }

async function setup(result = searchResult()) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const fetchMock = vi.fn(async (_url: string, init?: RequestInit) => {
    const method = (init?.method ?? 'GET').toUpperCase()
    if (method === 'PATCH') {
      return ok({ message: '選択した画像の分析結果を更新しました。', updatedCount: 1 }, '選択した画像の分析結果を更新しました。')
    }
    return ok(result)
  })
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(StudyMonitorView, { global: { plugins: [pinia] } })
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock, calls: () => fetchMock.mock.calls.map((call) => ({
    url: String(call[0]),
    method: ((call[1] ?? {}) as RequestInit).method ?? 'GET',
    body: (call[1] as RequestInit | undefined)?.body
  })) as Call[] }
}

beforeEach(() => {
  useToast().items.splice(0)
})

describe('学習状況モニター', () => {
  it('対象日で API を呼び、動画とスナップショットを出す', async () => {
    const { wrapper, calls } = await setup()

    const first = calls()[0]
    expect(first?.url).toContain('/api/user/study-monitor/snapshots')
    expect(first?.url).toContain('date=')
    expect(first?.url).toContain('timeFrom=08%3A00')

    expect(wrapper.get('[data-video="9"]').text()).toContain('00_20260726155346_20260726160140.mp4')
    expect(wrapper.findAll('[data-snapshot]').length).toBe(4)
    expect(wrapper.get('[data-snapshot="954"]').text()).toContain('15:53:46')
    expect(wrapper.get('[data-snapshot="954"]').text()).toContain('学習中（PC不使用・読書または筆記）')
  })

  it('サマリと分析結果の割合を出す', async () => {
    const { wrapper } = await setup()

    expect(wrapper.get('[data-stat="videos"]').text()).toContain('1 本')
    expect(wrapper.get('[data-stat="total"]').text()).toContain('4 枚')
    expect(wrapper.get('[data-stat="done"]').text()).toContain('3 件')
    expect(wrapper.get('[data-stat="waiting"]').text()).toContain('1 件')
    expect(wrapper.findAll('.sm-bar__part').length).toBe(6)
    expect(wrapper.get('.snapshot-result-summary').text()).toContain('離席中')
  })

  it('時間帯と絞り込みを API のパラメータで送る', async () => {
    const { wrapper, calls } = await setup()

    await wrapper.get('input[aria-label="開始時刻"]').setValue('15:00')
    await wrapper.get('input[aria-label="終了時刻"]').setValue('16:00')
    await flushPromises()
    let last = calls().at(-1)?.url ?? ''
    expect(last).toContain('timeFrom=15%3A00')
    expect(last).toContain('timeTo=16%3A00')

    await wrapper.get('select[aria-label="AI分析"]').setValue('error')
    await flushPromises()
    last = calls().at(-1)?.url ?? ''
    expect(last).toContain('analysisState=error')

    // 分析結果の絞り込みはスナップショット検索モードのときだけ出す
    expect(wrapper.find('select[aria-label="分析結果"]').exists()).toBe(false)
    await wrapper.get('[data-view-mode="snapshot"]').trigger('click')
    await flushPromises()
    await wrapper.get('select[aria-label="分析結果"]').setValue('AWAY')
    await flushPromises()
    expect(calls().at(-1)?.url).toContain('result=AWAY')
  })

  it('画像は API の URL を使い、読み込めないときは代替を出す', async () => {
    const { wrapper } = await setup()

    const img = wrapper.get('[data-snapshot="954"] img.snapshot-image')
    expect(img.attributes('src')).toBe('/api/user/study-monitor/snapshots/954/image')

    await img.trigger('error')
    expect(wrapper.get('[data-snapshot="954"]').text()).toContain('画像なし')
    expect(wrapper.get('[data-snapshot="954"]').text()).toContain('20260726/snapshot_000954.jpg')
  })

  it('表示中を選択して分析結果を一括変更できる（理由は必須）', async () => {
    const { wrapper, calls } = await setup()

    expect(wrapper.get('[data-action="bulk-open"]').attributes('disabled')).toBeDefined()

    // 未分析（957）は選択できない
    expect(wrapper.get('[data-snapshot="957"]').find('input[type="checkbox"]').exists()).toBe(false)

    await wrapper.get('[data-action="select-visible"]').setValue(true)
    await flushPromises()
    expect(wrapper.get('[data-action="bulk-open"]').attributes('disabled')).toBeUndefined()

    await wrapper.get('[data-action="bulk-open"]').trigger('click')
    await wrapper.get('select[aria-label="分析結果"]').setValue('AWAY')
    await wrapper.get('[data-action="bulk-apply"]').trigger('click')
    await flushPromises()
    // 理由が空だと送らない
    expect(useToast().items.map((item) => item.message).join(' ')).toContain('修正理由を入力してください')
    expect(calls().some((call) => call.method === 'PATCH')).toBe(false)

    await wrapper.get('input[placeholder*="修正理由"]').setValue('E2E: 時間帯を確認して修正')
    await wrapper.get('[data-action="bulk-apply"]').trigger('click')
    await flushPromises()

    const patch = calls().find((call) => call.method === 'PATCH')
    expect(patch?.url).toContain('/api/user/study-monitor/snapshots')
    expect(JSON.parse(String(patch?.body))).toEqual({
      updates: [
        { snapshotId: 954, version: 1 }, { snapshotId: 955, version: 1 }, { snapshotId: 956, version: 1 }
      ],
      result: 'AWAY',
      reason: 'E2E: 時間帯を確認して修正'
    })
  })

  it('2.0 のページ構成（タブ / 集計 / 2 カラム / 時間軸）を持つ', async () => {
    const { wrapper } = await setup()

    // 見出しの行は置かず、上部はタブ（TODO と同じ見せ方）
    expect(wrapper.find('.monitor-heading').exists()).toBe(false)
    expect(wrapper.findAll('.tabs__tab').map((tab) => tab.text()))
      .toEqual(['動画から確認', 'スナップショットから確認'])
    expect(wrapper.findAll('.summary-count-card span').map((el) => el.text()))
      .toEqual(['動画', 'スナップショット', '分析済み', '未分析'])
    expect(wrapper.find('.monitor-main-column').exists()).toBe(true)
    expect(wrapper.findAll('.segment-row').length).toBe(1)

    // 一覧の見出しは「〜一覧」＋一覧アイコン（他の一覧画面と揃える）。
    // スナップショットは「動画から確認」「スナップショットから確認」の両方で使う 1 つの見出し。
    const videoTitle = wrapper.get('.segment-section h2')
    expect(videoTitle.text()).toBe('動画一覧')
    expect(videoTitle.get('use').attributes('href')).toBe('#i-list')
    const snapshotTitle = wrapper.get('.snapshot-section h2')
    expect(snapshotTitle.text()).toBe('スナップショット一覧')
    expect(snapshotTitle.get('use').attributes('href')).toBe('#i-list')

    await wrapper.get('[data-action="timeline-open"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('#smTimelineTitle').text()).toBe('時間軸で確認')
    expect(wrapper.findAll('[data-timeline-segment]').length).toBe(4)
  })

  it('【時間軸で確認】は選んだ動画ではなく時間帯全体のスナップショットを出す', async () => {
    const first = [
      snapshot(954, '15:53:46', 'COMPLETED', 'STUDY_NO_PC', 9),
      snapshot(955, '15:54:46', 'COMPLETED', 'STUDY_PC', 9)
    ]
    const second = [
      snapshot(960, '16:10:00', 'COMPLETED', 'AWAY', 10),
      snapshot(961, '16:11:00', 'WAITING', null, 10)
    ]
    const { wrapper } = await setup(searchResult({
      videos: [
        video(9, '00_20260726155346_20260726160140.mp4', '15:53:46', first),
        video(10, '00_20260726161000_20260726170000.mp4', '16:10:00', second)
      ],
      snapshots: [...first, ...second]
    }))

    // 画面の一覧は選んだ動画（先頭の動画＝9）の 2 枚だけ
    expect(wrapper.findAll('[data-snapshot]').length).toBe(2)

    await wrapper.get('[data-action="timeline-open"]').trigger('click')
    await flushPromises()

    // 時間軸は時間帯全体（2 本ぶんの 4 枚）
    expect(wrapper.findAll('[data-timeline-segment]').length).toBe(4)
    expect(wrapper.get('#smTimelineTitle').text()).toBe('時間軸で確認')
    expect(wrapper.get('.dialog .home-note').text()).toContain('4 枚')
    expect(wrapper.get('.activity-timeline-list').text()).toContain('16:10:00')
  })

  it('詳細は画像の右下の編集アイコンからだけ開く（画像そのものは開かない）', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-snapshot="956"] .snapshot-thumb').trigger('click')
    await flushPromises()
    expect(wrapper.find('#smDetailTitle').exists()).toBe(false)

    const edit = wrapper.get('[data-snapshot="956"] [data-snapshot-open="956"]')
    expect(edit.classes()).toContain('snapshot-edit-button')
    expect(edit.attributes('aria-label')).toContain('15:55:46')

    await edit.trigger('click')
    await flushPromises()
    expect(wrapper.get('#smDetailTitle').text()).toBe('15:55:46')
  })

  it('詳細モーダルは修正理由なしでも直せる（理由は任意・ユーザーの指定）', async () => {
    const { wrapper, calls } = await setup()

    await wrapper.get('[data-snapshot="956"] [data-snapshot-open="956"]').trigger('click')
    await flushPromises()

    // 分析結果の初期値は今の判定（離席中）
    expect((wrapper.get('select[aria-label="詳細の分析結果"]').element as HTMLSelectElement).value).toBe('AWAY')
    // 入力欄に「必須」を出さない
    expect(wrapper.get('.sm-detail-form').text()).toContain('修正理由（任意）')

    await wrapper.get('select[aria-label="詳細の分析結果"]').setValue('STUDY_PC')
    await wrapper.get('[data-action="detail-apply"]').trigger('click')
    await flushPromises()

    // 理由が空でも送る（理由は任意）
    const patch = calls().find((call) => call.method === 'PATCH')
    expect(patch?.url).toContain('/api/user/study-monitor/snapshots')
    expect(JSON.parse(String(patch?.body))).toEqual({
      updates: [{ snapshotId: 956, version: 1 }],
      result: 'STUDY_PC',
      reason: ''
    })
    // 保存したら閉じて、一覧を取り直す
    expect(wrapper.find('#smDetailTitle').exists()).toBe(false)
  })

  it('詳細モーダルに修正理由を書けば一緒に送る', async () => {
    const { wrapper, calls } = await setup()

    await wrapper.get('[data-snapshot="956"] [data-snapshot-open="956"]').trigger('click')
    await flushPromises()
    await wrapper.get('textarea[aria-label="詳細の修正理由"]').setValue('E2E: 詳細から修正')
    await wrapper.get('[data-action="detail-apply"]').trigger('click')
    await flushPromises()

    const patch = calls().find((call) => call.method === 'PATCH')
    expect(JSON.parse(String(patch?.body))).toEqual({
      updates: [{ snapshotId: 956, version: 1 }],
      result: 'AWAY',
      reason: 'E2E: 詳細から修正'
    })
  })

  it('未分析の画像は詳細で修正できない（その旨を出す）', async () => {
    const { wrapper, calls } = await setup()

    await wrapper.get('[data-snapshot="957"] [data-snapshot-open="957"]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-action="detail-apply"]').exists()).toBe(false)
    expect(wrapper.get('.sm-detail-note').text()).toContain('分析済みの画像だけ')
    expect(calls().some((call) => call.method === 'PATCH')).toBe(false)
  })

  it('1 枚の詳細モーダルが開く（判定理由つき）', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-snapshot-open="956"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('#smDetailTitle').text()).toBe('15:55:46')
    expect(wrapper.text()).toContain('離席中')
    expect(wrapper.text()).toContain('椅子に誰も座っていない')
  })
})

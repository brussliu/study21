import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { useToast } from '@study21/web-shared'
import DailyReportView from '@/views/daily-report/DailyReportView.vue'
import type { DayDetail, StatusResult, WeekResult } from '@/api/dailyReports'

/**
 * 学習日報（作り直した画面）。
 *
 * 1. 月カレンダー: 提出済（緑）／記載あり（黄）／未提出（グレー）が見分けられ、
 *    未展開でも時限数・掌握度・教科の色チップが出る
 * 2. 日のマスを押すと **その行が開いて各マスが縦に伸び、授業の詳細**（時限・教科・
 *    授業内容・掌握度・ノート）とその日のまとめが並ぶ。記録が無い日は推定で埋まる
 * 3. 授業のマスを押すと **その授業の日報入力ダイアログ**が開き、保存できる
 * 4. カレンダーの下の【記録を追加】【提出】で、選択中の日に記録を足し、提出できる
 */
function ok(data: unknown): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message: 'OK', data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function statusResult(from: string, to: string): StatusResult {
  const days = ['2026-09-10', '2026-09-11'].filter((date) => date >= from && date <= to).map((date, index) => ({
    date,
    weekday: index === 0 ? 4 : 5,
    hasReport: true,
    lessonCount: 6,
    averageMastery: 3.8,
    noteCount: 2,
    // 1 日目（9/10）は提出済、2 日目（9/11）は記載あり（未提出）
    submitStatus: index === 0 ? 'SUBMITTED' as const : 'DRAFT' as const,
    submitted: index === 0,
    submittedAt: index === 0 ? '2026-09-10T21:00:00' : null,
    subjects: ['数学', '国語', '英語', '理科', '体育'],
    detailedLessonCount: 5,
    holidayType: 'NORMAL' as const,
    // 未展開のマスに出す振り返りと今夜の勉強内容
    review: index === 0 ? '今日は数学の二次関数が分かってきた。' : null,
    homework: index === 0 ? '英語のワーク p.20-22' : null
  }))
  let schoolDays = 0
  for (let d = new Date(`${from}T00:00:00`); d <= new Date(`${to}T00:00:00`); d.setDate(d.getDate() + 1)) {
    const dow = d.getDay()
    if (dow !== 0 && dow !== 6) schoolDays += 1
  }
  const restDays = days.filter((day) => day.holidayType !== 'NORMAL').length
  const submittedDays = days.filter((day) => day.submitted).length
  return {
    from, to, days, schoolDays, reportDays: days.length,
    submittedDays, reopenedDays: days.filter((day) => !day.submitted && day.submittedAt !== null).length,
    restDays
  }
}

/** 記録が無い日の既定値（推定の授業だけ）。 */
function emptyDay(date: string, weekday: number) {
  return {
    date,
    weekday,
    hasReport: false,
    submitStatus: null,
    submitted: false,
    submittedAt: null,
    review: null,
    concentration: null,
    understanding: null,
    studyVolume: null,
    attitude: null,
    homework: null,
    subjects: [],
    holidayType: 'NORMAL' as const,
    lessons: [] as WeekResult['days'][number]['lessons']
  }
}

/** 週の 1 限の既定値（推定）。 */
function inferredLesson(period: number, subject: string) {
  return {
    period, subject, content: null, mastery: null, noteRecorded: false,
    noteSkippedReason: null, concentration: null, studyVolume: null, attitude: null,
    recorded: false
  }
}

/** 週の 1 限の既定値（記録済み）。 */
function recordedLesson(period: number, subject: string, content: string, mastery: number, noteRecorded: boolean) {
  return {
    period, subject, content, mastery, noteRecorded,
    noteSkippedReason: noteRecorded ? null : '時間が無かった',
    concentration: 4, studyVolume: 3, attitude: 4,
    recorded: true
  }
}

/** 2026-09-07(月)〜09-11(金) の週。木は記録あり（提出済）、金は記載あり。他は推定。 */
const week: WeekResult = {
  from: '2026-09-07',
  to: '2026-09-13',
  days: [
    {
      ...emptyDay('2026-09-07', 1),
      lessons: [inferredLesson(1, '数学')]
    },
    {
      ...emptyDay('2026-09-08', 2),
      lessons: [inferredLesson(1, '英語')]
    },
    emptyDay('2026-09-09', 3),
    {
      ...emptyDay('2026-09-10', 4),
      hasReport: true,
      submitStatus: 'SUBMITTED',
      submitted: true,
      submittedAt: '2026-09-10T21:00:00',
      review: '今日は数学の二次関数が分かってきた。',
      concentration: 4,
      understanding: 3,
      studyVolume: 4,
      attitude: 3,
      homework: '英語のワーク p.20-22',
      subjects: ['数学'],
      lessons: [recordedLesson(1, '数学', '二次関数の最大値', 4, true)]
    },
    {
      ...emptyDay('2026-09-11', 5),
      hasReport: true,
      submitStatus: 'DRAFT',
      subjects: ['国語'],
      lessons: [recordedLesson(1, '国語', '小説の読解', 3, false)]
    }
  ]
}

const detail: DayDetail = {
  date: '2026-09-10',
  hasReport: true,
  submitStatus: 'SUBMITTED',
  submitted: true,
  submittedAt: '2026-09-10T21:00:00',
  holidayType: 'NORMAL',
  review: '今日は数学の二次関数が分かってきた。',
  concentration: 4,
  understanding: 3,
  studyVolume: 4,
  attitude: 3,
  homework: '英語のワーク p.20-22',
  note: null,
  lessons: [{
    period: 1, subject: '数学', content: '二次関数の最大値', mastery: 4, noteRecorded: true,
    noteSkippedReason: null, concentration: 4, studyVolume: 3, attitude: 4
  }],
  subjects: []
}

type Call = { url: string; method: string; body: Record<string, unknown> | null }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return {
      url: String(call[0]),
      method: (init.method ?? 'GET').toUpperCase(),
      body: init.body ? JSON.parse(String(init.body)) as Record<string, unknown> : null
    }
  })
}

beforeEach(() => {
  useToast().items.splice(0)
  window.scrollTo = vi.fn()
})

async function setup(options: {
  days?: StatusResult['days']
  week?: WeekResult
  dayDetail?: DayDetail
} = {}) {
  const pinia = createPinia()
  setActivePinia(pinia)
  const fetchMock = vi.fn(async (url: string) => {
    const target = String(url)
    if (target.includes('/daily-reports/week')) return ok(options.week ?? week)
    if (target.includes('/daily-reports/day')) return ok(options.dayDetail ?? detail)
    if (target.includes('/daily-reports/lesson')) {
      return ok({ message: '1限を保存しました。', date: '2026-09-10', period: 1, created: false, reopened: false })
    }
    if (target.includes('/daily-reports/submit')) {
      return ok({
        message: '2026-09-11 の日報を提出しました（1 限）。',
        date: '2026-09-11', lessonCount: 1, submittedAt: '2026-09-11T22:00:00'
      })
    }
    if (target.includes('/daily-reports/status')) {
      const params = new URL(target, 'http://localhost').searchParams
      if (options.days) {
        return ok({
          from: params.get('from') ?? '', to: params.get('to') ?? '', days: options.days,
          schoolDays: 22, reportDays: options.days.length,
          submittedDays: options.days.filter((day) => day.submitted).length, reopenedDays: 0, restDays: 0
        })
      }
      return ok(statusResult(params.get('from') ?? '', params.get('to') ?? ''))
    }
    if (target.includes('/daily-reports/timetable')) {
      return ok({ weekdays: [1, 2, 3, 4, 5], periods: [1], cells: [{ weekday: 1, period: 1, subject: '数学', appearances: 3, days: 3 }] })
    }
    if (target.includes('/daily-reports/day') && !target.includes('/daily-reports/day?')) {
      return ok({ message: '祝日として保存しました。', date: '2026-09-10', holidayType: 'HOLIDAY', clearedLessons: 1 })
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(DailyReportView, { global: { plugins: [pinia] } })
  await flushPromises()
  await flushPromises()
  return { wrapper, fetchMock }
}

describe('学習日報（カレンダー → 週の課表 → 授業の入力）', () => {
  it('月カレンダーに提出済／記載あり／未提出が出て、提出率は提出済だけで数える', async () => {
    const { wrapper } = await setup()

    expect(wrapper.findAll('[data-has-report="true"]').length).toBeGreaterThan(0)
    expect(wrapper.get('[data-date="2026-09-08"]').attributes('data-has-report')).toBe('false')
    expect(wrapper.get('[data-date="2026-09-08"]').text()).toContain('未提出')
    expect(wrapper.text()).toContain('提出 ')
    expect(wrapper.text()).toContain('%')
    // 9/10 は提出済、9/11 は保存しただけ（記載あり）→ 提出率の分子は 1 日だけ
    const summaryText = wrapper.get('.dr-summary').text()
    expect(summaryText).toContain('提出 1 / 平日')
    expect(summaryText).toContain('記載あり 2 日')
    expect(wrapper.get('[data-date="2026-09-10"]').text()).toContain('提出済')
    expect(wrapper.get('[data-date="2026-09-11"]').text()).toContain('記載あり')
    // まだどの行も開いていない
    expect(wrapper.find('[data-lesson]').exists()).toBe(false)
  })

  it('日のマスを押すと、その行のマス自体が開いて中に授業が出る', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()

    // 行の下に別の領域を足すのではなく、同じ行のマスが展開する（高さが伸びる）
    const cell = wrapper.get('[data-date="2026-09-10"]')
    expect(cell.classes()).toContain('dr-day--expanded')
    expect(cell.find('.dr-day__lessons').exists()).toBe(true)
    expect(wrapper.find('[data-week-panel]').exists()).toBe(false)
    // 同じ行の他の日も開く（週の 1 行がまとめて展開）
    expect(wrapper.get('[data-date="2026-09-07"]').find('.dr-day__lessons').exists()).toBe(true)
    // 別の週は開かない
    expect(wrapper.get('[data-date="2026-09-14"]').find('.dr-day__lessons').exists()).toBe(false)
  })

  it('記録が無い日は推定の教科で埋まり、記録済みと区別できる', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()

    // 記録済みの授業は、時限・教科・授業内容・掌握度・ノートまで出す
    const recordedCell = wrapper.get('[data-lesson="2026-09-10-1"]')
    expect(recordedCell.text()).toContain('数学')
    expect(recordedCell.text()).toContain('二次関数の最大値')
    expect(recordedCell.text()).toContain('★★★★☆')   // 掌握度は星で出す（3 → ★★★）
    expect(recordedCell.text()).toContain('ノート記録あり')
    expect(recordedCell.classes()).not.toContain('dr-lesson-card--inferred')
    // 教科の色が付く（tokens.css の教科色）
    expect(recordedCell.classes()).toContain('dr-subject--math')

    // 推定の授業は破線＋「推定」で区別し、推定であることも書く
    const inferredCell = wrapper.get('[data-lesson="2026-09-07-1"]')
    expect(inferredCell.text()).toContain('数学')
    expect(inferredCell.text()).toContain('推定')
    // 案内は 1 行（カードの高さを記録済みと揃えるため短くしてある）
    expect(inferredCell.text()).toContain('推定：押すと記録できます')
    expect(inferredCell.classes()).toContain('dr-lesson-card--inferred')
  })

  it('未展開のマスにも提出状態・時限数・掌握度・教科チップ・ノート数が出る', async () => {
    const { wrapper } = await setup()

    const submitted = wrapper.get('[data-date="2026-09-10"]')
    expect(submitted.attributes('data-submitted')).toBe('true')
    expect(submitted.attributes('data-cell-state')).toBe('submitted')
    expect(submitted.text()).toContain('提出済')
    expect(submitted.text()).toContain('6限')
    expect(submitted.text()).toContain('掌握 3.8')
    // 教科は記録したぶんを全部出す（+n で省略しない）
    const chips = submitted.findAll('[data-subject]')
    expect(chips.map((chip) => chip.text())).toEqual(['数学', '国語', '英語', '理科', '体育'])
    expect(submitted.text()).not.toContain('+1')
    // 主科は色、副科は灰色の破線
    expect(chips[0].classes()).toContain('dr-subject--math')
    expect(chips[1].classes()).toContain('dr-subject--japanese')
    expect(chips[2].classes()).toContain('dr-subject--english')
    expect(chips[3].classes()).toContain('dr-subject--science')
    expect(chips.map((chip) => chip.attributes('data-subject-tone'))).toEqual(['main', 'main', 'main', 'main', 'minor'])
    // 副科（体育）は教科の色クラスを持ちつつ、破線の印（dr-subject--minor）が付く
    // （表示は CSS で灰色の破線にする）
    expect(chips[4].classes()).toContain('dr-subject--minor')
    expect(chips[4].classes()).toContain('dr-subject--pe')
    // 未展開のマスには振り返りと今夜の勉強内容を出す（「内容 n 件 / ノートあり」は出さない）
    expect(submitted.text()).toContain('今日は数学の二次関数が分かってきた。')
    expect(submitted.text()).toContain('今夜の勉強')
    expect(submitted.text()).toContain('英語のワーク p.20-22')
    expect(submitted.text()).not.toContain('内容 5 件')
    expect(submitted.text()).not.toContain('ノートあり')

    // 項目名は文字ではなくアイコン（振り返り＝ペン / 今夜の勉強＝月）。
    // 文字は読み上げ用（.visually-hidden）にだけ残す。
    const lines = submitted.findAll('.dr-day__review')
    expect(lines.length).toBe(2)
    expect(lines.map((line) => line.find('svg.icon use').attributes('href')))
      .toEqual(['#i-edit', '#i-moon'])
    for (const line of lines) {
      const hidden = line.find('.visually-hidden')
      expect(hidden.exists()).toBe(true)
      // 目の見える文字は「本文」と読み上げ用のラベルだけ（本文は .dr-day__review-text 側）
      expect(line.find('.dr-day__review-text').exists()).toBe(true)
      expect(hidden.text()).toMatch(/^(振り返り|今夜の勉強)$/)
    }

    // 記載ありの日は黄（draft）で、提出済とは区別する
    const draft = wrapper.get('[data-date="2026-09-11"]')
    expect(draft.attributes('data-cell-state')).toBe('draft')
    expect(draft.text()).toContain('記載あり')

    // 記録が無い平日は未提出（欠席ではない）
    expect(wrapper.get('[data-date="2026-09-07"]').attributes('data-cell-state')).toBe('missing')
  })

  it('展開すると、その日のまとめ（振り返り・今夜の勉強）も出る', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()

    const cell = wrapper.get('[data-date="2026-09-10"]')
    // 展開中はチップを畳んで、授業のカードとまとめを出す
    expect(cell.findAll('[data-subject]').length).toBe(0)
    const summary = cell.get('.dr-day__summary')
    expect(summary.text()).toContain('振り返り')
    expect(summary.text()).toContain('二次関数が分かってきた')
    expect(summary.text()).toContain('今夜の勉強')
    expect(summary.text()).toContain('英語のワーク')
    // 未展開のマスと同じく、項目名はアイコン（文字は読み上げ用だけ）
    const labels = summary.findAll('.dr-day__summary-label')
    expect(labels.map((label) => label.find('svg.icon use').attributes('href')))
      .toEqual(['#i-edit', '#i-moon'])
    expect(labels.map((label) => label.find('.visually-hidden').text()))
      .toEqual(['振り返り', '今夜の勉強'])
    // 4 観点（集中・理解・量・態度）は授業ごとの持ち物になったので、まとめには出さない
    expect(summary.text()).not.toContain('集中')
    expect(summary.text()).not.toContain('理解')
  })

  it('展開したマスの中に【授業追加】【当日まとめ】【提出】が並ぶ', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()

    const cell = wrapper.get('[data-date="2026-09-10"]')
    const actions = cell.get('[data-day-actions="2026-09-10"]')
    const labels = actions.findAll('.btn').map((button) => button.text().trim())
    // 1 日ぶんに 2 つの追加機能と提出（ご指示のとおり）
    expect(labels).toEqual(['授業追加', '当日まとめ', '提出済み'])

    // 提出済の日は提出ボタンが押せない
    expect(actions.get('[data-submit-day="2026-09-10"]').attributes('disabled')).toBeDefined()
    // 記録が無い日は提出できない
    const empty = wrapper.get('[data-day-actions="2026-09-07"]')
    expect(empty.get('[data-submit-day="2026-09-07"]').attributes('disabled')).toBeDefined()
  })

  it('【授業追加】は、その日の空いている時限で授業ダイアログを開く', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-add-lesson="2026-09-10"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('#drDialogTitle').text()).toContain('授業を追加')
    // 1 限は記録済みなので、空いている 2 限が初期値になる
    expect((wrapper.get('[data-testid="dr-period"]').element as HTMLSelectElement).value).toBe('2')
    expect(wrapper.text()).toContain('保存しただけでは未提出のままです')
  })

  it('【提出】で確認ダイアログを出し、確定すると提出 API を呼ぶ', async () => {
    const { wrapper, fetchMock } = await setup()

    // 記載ありの日（9/11）は提出できる
    await wrapper.get('[data-date="2026-09-11"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-submit-day="2026-09-11"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="dr-submit-title"]').text()).toContain('2026-09-11')
    expect(wrapper.get('[data-testid="dr-submit-summary"]').text()).toContain('1 限の記録を提出します')

    await wrapper.get('[data-testid="dr-submit-confirm"]').trigger('click')
    await flushPromises()

    const submitCall = recorded(fetchMock).find((call) => call.url.includes('/daily-reports/submit'))
    expect(submitCall?.method).toBe('POST')
    expect(submitCall?.body).toEqual({ date: '2026-09-11' })
    // 提出後はダイアログが閉じ、その週を読み直す
    expect(wrapper.find('[data-testid="dr-submit-title"]').exists()).toBe(false)
    expect(recorded(fetchMock).filter((call) => call.url.includes('/daily-reports/week')).length).toBeGreaterThan(1)
  })

  it('提出済の日は【提出済み】で押せない', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()

    const button = wrapper.get('[data-submit-day="2026-09-10"]')
    expect(button.text()).toContain('提出済み')
    expect(button.attributes('disabled')).toBeDefined()
  })

  it('もう一度同じ日を押すと閉じる', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-lesson]').exists()).toBe(true)

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    expect(wrapper.find('[data-lesson]').exists()).toBe(false)
  })

  it('授業のマスを押すと入力ダイアログが開き、記録済みの内容が入っている', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-lesson="2026-09-10-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('#drDialogTitle').text()).toContain('2026-09-10（木）')
    expect((wrapper.get('[data-testid="dr-period"]').element as HTMLSelectElement).value).toBe('1')
    expect((wrapper.get('#drSubject').element as HTMLInputElement).value).toBe('数学')
    expect((wrapper.get('#drContent').element as HTMLTextAreaElement).value).toBe('二次関数の最大値')
    // 授業の属性は 9 項目（まとめは別ダイアログに移した）
    expect(wrapper.text()).toContain('学習集中度')
    expect(wrapper.text()).toContain('学習量')
    expect(wrapper.text()).toContain('学習態度')
    expect(wrapper.text()).toContain('ノート記録')
    // 記録済みの自己評価・星も読み込む（入力も星になった）
    expect(wrapper.get('[data-testid="dr-mastery"]').attributes('data-value')).toBe('4')
    expect(wrapper.get('[data-testid="dr-mastery"]').text()).toContain('★★★★☆')
    expect(wrapper.get('[data-testid="dr-concentration"]').attributes('data-value')).toBe('4')
    // まとめ（振り返り・今夜の勉強）は授業ダイアログには出さない
    expect(wrapper.text()).not.toContain('今日の振り返り')
    expect(wrapper.text()).not.toContain('今夜の勉強内容')
  })

  it('副科はノート記録を選べない（ラジオを出さない）', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-add-lesson="2026-09-10"]').trigger('click')
    await flushPromises()

    // 主科（数学）ではラジオが出る
    await wrapper.get('#drSubject').setValue('数学')
    expect(wrapper.find('[data-testid="dr-note-recorded"]').exists()).toBe(true)

    // 副科（音楽）にするとラジオも理由欄も消える
    await wrapper.get('#drSubject').setValue('音楽')
    expect(wrapper.find('[data-testid="dr-note-recorded"]').exists()).toBe(false)
    expect(wrapper.find('[data-testid="dr-note-reason"]').exists()).toBe(false)
    expect(wrapper.get('[data-testid="dr-note-major-only"]').text()).toContain('副科はノート記録の対象外')
  })

  it('主科で「記録しない」を選ぶと理由欄が出て、候補から入れられる', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-add-lesson="2026-09-10"]').trigger('click')
    await flushPromises()

    await wrapper.get('#drSubject').setValue('数学')
    // 「記録した」のうちは理由欄を出さない
    expect(wrapper.find('[data-testid="dr-note-reason"]').exists()).toBe(false)

    await wrapper.get('[data-testid="dr-note-skipped"]').setValue()
    expect(wrapper.find('[data-testid="dr-note-reason"]').exists()).toBe(true)

    // 候補ボタンで理由を入れる
    await wrapper.get('[data-note-reason="時間が無かった"]').trigger('click')
    expect((wrapper.get('[data-testid="dr-note-reason"]').element as HTMLInputElement).value).toBe('時間が無かった')

    await wrapper.get('#drContent').setValue('二次関数')
    await wrapper.get('.dialog__foot .btn--primary').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.url === '/api/user/daily-reports/lesson')
    expect(call?.body).toMatchObject({
      subject: '数学',
      noteRecorded: false,
      noteSkippedReason: '時間が無かった'
    })
  })

  it('【当日まとめ】で祝日・休日区分とまとめを保存し、消える授業を案内する', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-day-summary="2026-09-10"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-testid="dr-summary-title"]').text()).toContain('2026-09-10')
    expect((wrapper.get('[data-testid="dr-summary-review"]').element as HTMLTextAreaElement).value)
      .toContain('二次関数が分かってきた')

    // 通常のうちは警告を出さない
    expect(wrapper.find('[data-testid="dr-summary-warning"]').exists()).toBe(false)
    // 祝日にすると、消える授業の件数を出す
    await wrapper.get('[data-testid="dr-holiday-type"]').setValue('HOLIDAY')
    expect(wrapper.get('[data-testid="dr-summary-warning"]').text()).toContain('1 件')

    await wrapper.get('[data-testid="dr-summary-homework"]').setValue('ワーク p.30')
    await wrapper.get('[data-testid="dr-summary-save"]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.url === '/api/user/daily-reports/day')
    expect(call?.method).toBe('POST')
    expect(call?.body).toEqual({
      date: '2026-09-10',
      holidayType: 'HOLIDAY',
      review: '今日は数学の二次関数が分かってきた。',
      homework: 'ワーク p.30'
    })
  })

  it('土日も平日と同じく「未提出」で、授業追加・当日まとめが押せる', async () => {
    const { wrapper } = await setup()

    // 9/12（土）・9/13（日）に記録は無い
    for (const date of ['2026-09-12', '2026-09-13']) {
      const cell = wrapper.get(`[data-date="${date}"]`)
      expect(cell.attributes('data-cell-state')).toBe('missing')
      expect(cell.text()).toContain('未提出')
    }
    await wrapper.get('[data-date="2026-09-12"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-add-lesson="2026-09-12"]').attributes('disabled')).toBeUndefined()
    expect(wrapper.get('[data-day-summary="2026-09-12"]').attributes('disabled')).toBeUndefined()
  })

  it('記録が無い日も、週を開くと推定の件数行が出てカードの並びが提出済みと揃う', async () => {
    const { wrapper } = await setup()

    // 未展開のうちは、記録が無い日に件数行は出さない
    expect(wrapper.find('[data-date="2026-09-07"] .dr-day__meta').exists()).toBe(false)

    // 週を開くと、記録が無い日も推定の「n限 推定」を出す（行の有無でカードが上下にずれないように）
    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()

    const inferred = wrapper.find('[data-date="2026-09-07"] .dr-day__meta')
    expect(inferred.text()).toContain('1限')
    expect(inferred.text()).toContain('推定')
    // 掌握度は推定では出さない
    expect(inferred.text()).not.toContain('掌握')

    // 記録済みの日はこれまでどおり件数と掌握度
    const recorded = wrapper.find('[data-date="2026-09-10"] .dr-day__meta')
    expect(recorded.text()).toContain('6限')
    expect(recorded.text()).toContain('掌握')
    expect(recorded.text()).not.toContain('推定')

    // 推定の授業が無い日は行を出さない（余白を作らない）
    expect(wrapper.find('[data-date="2026-09-09"] .dr-day__meta').exists()).toBe(false)
  })

  it('祝日・休日の日は「休み」として未提出と区別し、0限と出して提出できる', async () => {
    const { wrapper, fetchMock } = await setup({
      days: [
        { date: '2026-09-10', weekday: 4, hasReport: true, lessonCount: 0, averageMastery: null, noteCount: 0,
          submitStatus: 'DRAFT' as const, submitted: false, submittedAt: null, subjects: [],
          detailedLessonCount: 0, holidayType: 'HOLIDAY' as const, review: null, homework: null }
      ],
      // 祝日は授業が無い（0 限）
      dayDetail: {
        ...detail, holidayType: 'HOLIDAY', review: null, homework: null, lessons: []
      }
    })

    const cell = wrapper.get('[data-date="2026-09-10"]')
    expect(cell.attributes('data-cell-state')).toBe('rest')
    expect(cell.text()).toContain('祝日')
    expect(cell.text()).not.toContain('未提出')
    // 授業が無いことを件数で示す（0限。ユーザーの指定で出せるようにした）
    expect(cell.text()).toContain('0限')

    // 祝日・休日も提出できる（ユーザーの指定）
    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-submit-day="2026-09-10"]').attributes('disabled')).toBeUndefined()
    await wrapper.get('[data-submit-day="2026-09-10"]').trigger('click')
    await flushPromises()
    // 確認ダイアログは「祝日として提出」と案内する
    expect(wrapper.get('[data-testid="dr-submit-summary"]').text()).toContain('祝日として日報を提出します')
    await wrapper.get('[data-testid="dr-submit-confirm"]').trigger('click')
    await flushPromises()
    const call = recorded(fetchMock).find((entry) => entry.url === '/api/user/daily-reports/submit')
    expect(call?.method).toBe('POST')
    expect(call?.body).toMatchObject({ date: '2026-09-10' })
  })

  it('凡例に「授業なし」は出さず、祝日・休日を出す', async () => {
    const { wrapper } = await setup()

    const legend = wrapper.get('.dr-legend').text()
    expect(legend).not.toContain('授業なし')
    expect(legend).toContain('祝日・休日')
    // 淡い赤の見本（badge--danger と同じ色）
    expect(wrapper.find('.dr-legend__swatch--rest').exists()).toBe(true)
    expect(wrapper.find('.dr-legend__swatch--none').exists()).toBe(false)
  })

  it('提出前は、記録した時限のほかも推定の枠を出したままにする', async () => {
    // 1 限だけ記録した状態（提出前）。残りは推定の枠が残る
    const draft: WeekResult = {
      from: '2026-09-07',
      to: '2026-09-13',
      days: [{
        ...emptyDay('2026-09-10', 4),
        hasReport: true,
        submitStatus: 'DRAFT',
        subjects: ['数学'],
        lessons: [
          recordedLesson(1, '数学', '二次関数の最大値', 4, true),
          inferredLesson(2, '国語'),
          inferredLesson(3, '英語'),
          inferredLesson(4, '理科'),
          inferredLesson(5, '社会'),
          inferredLesson(6, '体育')
        ]
      }]
    }
    const { wrapper } = await setup({ week: draft })

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()

    const cards = wrapper.findAll('.dr-day__lessons [data-lesson]')
    expect(cards.map((card) => card.attributes('data-lesson'))).toEqual([
      '2026-09-10-1', '2026-09-10-2', '2026-09-10-3', '2026-09-10-4', '2026-09-10-5', '2026-09-10-6'
    ])
    // 記録した 1 限は記録済み、2〜6 限は推定（枠は消えない）
    expect(cards[0].classes()).not.toContain('dr-lesson-card--inferred')
    expect(cards.slice(1).every((card) => card.classes().includes('dr-lesson-card--inferred'))).toBe(true)
    expect(cards[1].text()).toContain('推定')
    // 空きの枠は出さない（推定で埋まるため）
    expect(wrapper.findAll('[data-lesson-blank]').length).toBe(0)
  })

  it('授業を消した時限は「空き」の枠になり、下のカードは上に詰まらない', async () => {
    // 3 限を消したあとの状態（1・2・4 限だけ記録が残っている）
    const afterDelete: WeekResult = {
      from: '2026-09-07',
      to: '2026-09-13',
      days: [{
        ...emptyDay('2026-09-10', 4),
        hasReport: true,
        submitStatus: 'DRAFT',
        subjects: ['数学', '国語', '理科'],
        lessons: [
          recordedLesson(1, '数学', '二次関数の最大値', 4, true),
          recordedLesson(2, '国語', '小説の読解', 3, false),
          recordedLesson(4, '理科', '化学変化', 3, false)
        ]
      }]
    }
    const { wrapper } = await setup({ week: afterDelete })

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()

    // 1・2・空き・4 限の 4 枠。消した 3 限は空きのまま残る
    const slots = wrapper.findAll('.dr-day__lessons > li')
    expect(slots.length).toBe(4)
    expect(wrapper.find('[data-lesson-blank="2026-09-10-3"]').exists()).toBe(true)
    expect(wrapper.get('[data-lesson-blank="2026-09-10-3"]').text()).toContain('3限')
    expect(wrapper.get('[data-lesson-blank="2026-09-10-3"]').text()).toContain('空き')

    // 時限の順番はそのまま（4 限が 3 番目に繰り上がらない）
    const keys = await Promise.all(slots.map(async (slot) => {
      const card = slot.find('[data-lesson]')
      const blank = slot.find('[data-lesson-blank]')
      return card.exists() ? card.attributes('data-lesson') : blank.attributes('data-lesson-blank')
    }))
    expect(keys).toEqual(['2026-09-10-1', '2026-09-10-2', '2026-09-10-3', '2026-09-10-4'])

    // 空きの枠からは削除も入力もできない（消えているので）
    expect(wrapper.find('[data-lesson-blank="2026-09-10-3"] [data-lesson-delete]').exists()).toBe(false)
  })

  it('授業カードの削除アイコンで 1 限だけ消せる', async () => {
    const { wrapper, fetchMock } = await setup()
    window.confirm = vi.fn(() => true)

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    // 記録済みのカードにだけ削除アイコンが出る
    expect(wrapper.find('[data-lesson-delete="2026-09-10-1"]').exists()).toBe(true)
    expect(wrapper.find('[data-lesson-delete="2026-09-07-1"]').exists()).toBe(false)

    await wrapper.get('[data-lesson-delete="2026-09-10-1"]').trigger('click')
    await flushPromises()
    const call = recorded(fetchMock).find((entry) => entry.method === 'DELETE')
    expect(call?.method).toBe('DELETE')
    expect(call?.url).toContain('date=2026-09-10')
    expect(call?.url).toContain('period=1')
  })

  it('日曜の授業がある月は 7 日とも同じ幅にする', async () => {
    const { wrapper } = await setup()
    // 日曜（9/13）に授業がある月
    expect(wrapper.get('.card__body').classes()).not.toContain('dr-calendar--all-equal')

    const { wrapper: withSunday } = await setup({
      days: [
        { date: '2026-09-13', weekday: 7, hasReport: true, lessonCount: 2, averageMastery: 4, noteCount: 1,
          submitStatus: 'SUBMITTED' as const, submitted: true, submittedAt: '2026-09-13T18:00:00',
          subjects: ['数学'], detailedLessonCount: 2, holidayType: 'NORMAL' as const, review: null, homework: null }
      ]
    })
    expect(withSunday.get('.card__body').classes()).toContain('dr-calendar--all-equal')
  })

  it('推定で開いたマスは「推定」と案内する', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-lesson="2026-09-07-1"]').trigger('click')
    await flushPromises()

    expect(wrapper.text()).toContain('過去の日報から推定した教科です。')
    expect((wrapper.get('#drSubject').element as HTMLInputElement).value).toBe('数学')
  })

  it('時限の選択肢はその日の日報に合わせて変わる（最大 10 限）', async () => {
    // 1〜6 限が記録済みの日
    const fullDay: WeekResult = {
      from: '2026-09-07',
      to: '2026-09-13',
      days: [{
        ...emptyDay('2026-09-10', 4),
        hasReport: true,
        submitStatus: 'DRAFT',
        subjects: ['数学'],
        lessons: [
          recordedLesson(1, '数学', '二次関数', 4, true),
          recordedLesson(2, '国語', '小説', 3, false),
          recordedLesson(3, '英語', '不定詞', 4, true),
          recordedLesson(4, '理科', '化学変化', 3, false),
          recordedLesson(5, '社会', '憲法', 3, false),
          recordedLesson(6, '体育', 'バスケットボール', 4, false)
        ]
      }]
    }
    const { wrapper } = await setup({ week: fullDay })

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-lesson="2026-09-10-1"]').trigger('click')
    await flushPromises()

    const select = wrapper.get('[data-testid="dr-period"]')
    // 記録済みの 2〜6 限は出さず、いまの 1 限と空いている 7〜10 限を出す
    expect(select.findAll('option').map((option) => option.text())).toEqual(['1限', '7限', '8限', '9限', '10限'])
    // 既定はいま開いている時限
    expect((select.element as HTMLSelectElement).value).toBe('1')
  })

  it('【授業追加】では、まだ使っていない時限が選択肢になる', async () => {
    const partialDay: WeekResult = {
      from: '2026-09-07',
      to: '2026-09-13',
      days: [{
        ...emptyDay('2026-09-10', 4),
        hasReport: true,
        submitStatus: 'DRAFT',
        subjects: ['数学'],
        lessons: [
          recordedLesson(1, '数学', '二次関数', 4, true),
          recordedLesson(2, '国語', '小説', 3, false)
        ]
      }]
    }
    const { wrapper } = await setup({ week: partialDay })

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-add-lesson="2026-09-10"]').trigger('click')
    await flushPromises()

    const select = wrapper.get('[data-testid="dr-period"]')
    // 空いている 3〜10 限（1・2 限は記録済みなので出さない）
    expect(select.findAll('option').map((option) => option.text()))
      .toEqual(['3限', '4限', '5限', '6限', '7限', '8限', '9限', '10限'])
    // 既定は最初の空き時限
    expect((select.element as HTMLSelectElement).value).toBe('3')
  })

  it('星を押して評価を選べる（同じ星をもう一度押すと未入力に戻る）', async () => {
    const { wrapper } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-lesson="2026-09-10-1"]').trigger('click')
    await flushPromises()

    const rating = wrapper.get('[data-testid="dr-mastery"]')
    expect(rating.attributes('data-value')).toBe('4')
    expect(rating.findAll('.star-rating__star').map((star) => star.text())).toEqual(['★', '★', '★', '★', '☆'])

    // 2 つ目の星を押すと 2 になる
    await wrapper.get('[data-testid="dr-mastery"] [data-star="2"]').trigger('click')
    expect(wrapper.get('[data-testid="dr-mastery"]').attributes('data-value')).toBe('2')
    expect(wrapper.get('[data-testid="dr-mastery"]').text()).toContain('2/5')

    // 同じ星をもう一度押すと未入力に戻る
    await wrapper.get('[data-testid="dr-mastery"] [data-star="2"]').trigger('click')
    expect(wrapper.get('[data-testid="dr-mastery"]').attributes('data-value')).toBe('')
    expect(wrapper.get('[data-testid="dr-mastery"]').text()).toContain('未入力')

    // 自己評価 3 つも同じ入力（押した値が入る）
    await wrapper.get('[data-testid="dr-concentration"] [data-star="5"]').trigger('click')
    await wrapper.get('[data-testid="dr-study-volume"] [data-star="1"]').trigger('click')
    await wrapper.get('[data-testid="dr-attitude"] [data-star="3"]').trigger('click')
    expect(wrapper.get('[data-testid="dr-concentration"]').attributes('data-value')).toBe('5')
    expect(wrapper.get('[data-testid="dr-study-volume"]').attributes('data-value')).toBe('1')
    expect(wrapper.get('[data-testid="dr-attitude"]').attributes('data-value')).toBe('3')
    // 星はボタンなのでキーボードでも押せる
    expect(wrapper.find('[data-testid="dr-attitude"] .star-rating__star').element.tagName).toBe('BUTTON')
  })

  it('保存で授業の 9 項目を送る（まとめは送らない）', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-lesson="2026-09-10-1"]').trigger('click')
    await flushPromises()

    await wrapper.get('#drContent').setValue('最大値と最小値')
    // 星を押して選ぶ（学習量 5）
    await wrapper.get('[data-testid="dr-study-volume"] [data-star="5"]').trigger('click')
    await wrapper.get('.dialog__foot .btn--primary').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).find((entry) => entry.url === '/api/user/daily-reports/lesson')
    expect(call?.method).toBe('POST')
    expect(call?.body).toEqual({
      date: '2026-09-10',
      period: 1,
      subject: '数学',
      content: '最大値と最小値',
      mastery: 4,
      noteRecorded: true,
      noteSkippedReason: null,
      concentration: 4,
      studyVolume: 5,
      attitude: 4
    })
    // 保存後は週と月を取り直す
    expect(recorded(fetchMock).filter((entry) => entry.url.includes('/daily-reports/week')).length).toBeGreaterThan(1)
  })

  it('教科が空なら保存しない', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-date="2026-09-10"]').trigger('click')
    await flushPromises()
    await wrapper.get('[data-lesson="2026-09-10-1"]').trigger('click')
    await flushPromises()

    await wrapper.get('#drSubject').setValue('')
    await wrapper.get('.dialog__foot .btn--primary').trigger('click')
    await flushPromises()

    expect(recorded(fetchMock).some((entry) => entry.url === '/api/user/daily-reports/lesson')).toBe(false)
  })

  it('前月・翌月へ移動すると期間を変えて取り直す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[aria-label="前の月"]').trigger('click')
    await flushPromises()

    const call = recorded(fetchMock).reverse().find((entry) => entry.url.includes('/daily-reports/status'))
    expect(call?.url).toContain('from=2026-08-01')
    expect(call?.url).toContain('to=2026-08-31')
  })
})

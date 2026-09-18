import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type DOMWrapper, type VueWrapper } from '@vue/test-utils'
import { formatIsoDateTime } from '@study21/web-shared'
import JapaneseWordStatusView from '@/views/japanese/JapaneseWordStatusView.vue'
import type { JpnDailyRow, JpnSkillRow, JpnStatusRow, JpnStatusSummary } from '@/api/japanese'

/**
 * 日本語勉強【単語勉強状況】（2.0 の japanese_word_status.jsp 相当）。
 * API はモックし、サマリ・日次の学習量・語別の学習状況・技能別の習得と、
 * 各ボタンが送るリクエストを固定する。
 * 実データ（学習状況 213 語・技能習得 906 件・日次 13 日）が入っている前提の画面。
 */

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function failure(message: string, status = 500): Response {
  return new Response(
    JSON.stringify({ success: false, code: 'INTERNAL_ERROR', message, data: null, timestamp: '' }),
    { status, headers: { 'Content-Type': 'application/json' } }
  )
}

/** 分をミリ秒に直す（テストの見やすさのため）。 */
function minutes(value: number): number {
  return value * 60_000
}

const SUMMARY: JpnStatusSummary = {
  studiedWordCount: 213,
  learnedCount: 84,
  favoriteCount: 12,
  averageMastery: 63.4,
  answeredCount: 5120,
  correctCount: 4423,
  accuracyPercent: 86.4,
  activeMs: minutes(205),
  todayActiveMs: minutes(45),
  lastStudiedAt: '2026-09-12T23:02:00'
}

function statusRow(overrides: Partial<JpnStatusRow> = {}): JpnStatusRow {
  return {
    wordId: 101,
    word: '勉強',
    reading: 'べんきょう',
    jlptLevel: 'N5',
    partOfSpeech: '名詞',
    book: '日本語総まとめ N5',
    category: '第1章',
    learnState: 'LEARNING',
    mastery: 62,
    learned: false,
    favorite: true,
    answeredCount: 25,
    correctCount: 20,
    wrongCount: 5,
    streak: 3,
    bestStreak: 7,
    activeMs: minutes(205),
    lastTestType: 'A',
    lastJudgment: 'CORRECT',
    firstStudiedAt: '2026-08-01T10:00:00',
    lastStudiedAt: '2026-09-12T23:02:00',
    nextReviewAt: '2026-09-20T09:00:00',
    reviewIntervalDays: 7,
    ...overrides
  }
}

/** 学習をまだ始めていない語（読み・JLPT・収録・復習日が未設定）。 */
function untouchedRow(overrides: Partial<JpnStatusRow> = {}): JpnStatusRow {
  return statusRow({
    wordId: 102,
    word: '図書館',
    reading: null,
    jlptLevel: null,
    partOfSpeech: null,
    book: null,
    category: null,
    learnState: 'NOT_STARTED',
    mastery: 0,
    favorite: false,
    answeredCount: 0,
    correctCount: 0,
    wrongCount: 0,
    streak: 0,
    bestStreak: 0,
    activeMs: 0,
    lastTestType: null,
    lastJudgment: null,
    firstStudiedAt: null,
    lastStudiedAt: null,
    nextReviewAt: null,
    reviewIntervalDays: 0,
    ...overrides
  })
}

function dailyRow(studyDate: string, overrides: Partial<JpnDailyRow> = {}): JpnDailyRow {
  return {
    studyDate,
    activeMs: 0,
    typeAMs: 0,
    typeBMs: 0,
    typeCMs: 0,
    typeDMs: 0,
    typeEMs: 0,
    wordCount: 0,
    testCount: 0,
    doneCount: 0,
    correctCount: 0,
    wrongCount: 0,
    ...overrides
  }
}

/** 実データに合わせて 13 日分。API は新しい順で返す。 */
function dailyRows(): JpnDailyRow[] {
  return [
    dailyRow('2026-09-13', { typeAMs: minutes(30), typeBMs: minutes(60), activeMs: minutes(90), wordCount: 12 }),
    dailyRow('2026-09-12', { typeCMs: minutes(45), activeMs: minutes(45), wordCount: 8 }),
    dailyRow('2026-09-11'),
    dailyRow('2026-09-10', { typeDMs: minutes(10), activeMs: minutes(10) }),
    dailyRow('2026-09-09', { typeEMs: minutes(20), activeMs: minutes(20) }),
    dailyRow('2026-09-08', { typeAMs: minutes(5), typeEMs: minutes(5), activeMs: minutes(10) }),
    dailyRow('2026-09-07', { typeBMs: minutes(15), activeMs: minutes(15) }),
    dailyRow('2026-09-06', { typeCMs: minutes(8), activeMs: minutes(8) }),
    dailyRow('2026-09-05'),
    dailyRow('2026-09-04', { typeAMs: minutes(3), activeMs: minutes(3) }),
    dailyRow('2026-09-03', { typeBMs: minutes(12), activeMs: minutes(12) }),
    dailyRow('2026-09-02', { typeDMs: minutes(6), activeMs: minutes(6) }),
    dailyRow('2026-09-01', { typeEMs: minutes(9), activeMs: minutes(9) })
  ]
}

function statusPage(items: JpnStatusRow[], overrides: {
  summary?: JpnStatusSummary
  daily?: JpnDailyRow[]
  totalElements?: number
  page?: number
  size?: number
  totalPages?: number
} = {}): unknown {
  return {
    summary: overrides.summary ?? SUMMARY,
    daily: overrides.daily ?? dailyRows(),
    items,
    totalElements: overrides.totalElements ?? items.length,
    page: overrides.page ?? 1,
    size: overrides.size ?? 20,
    totalPages: overrides.totalPages ?? 1
  }
}

function skillRow(overrides: Partial<JpnSkillRow> = {}): JpnSkillRow {
  return {
    wordId: 101,
    word: '勉強',
    reading: 'べんきょう',
    testType: 'B',
    skillCode: 'MEANING',
    learnState: 'REVIEW',
    mastery: 55,
    answeredCount: 8,
    correctCount: 6,
    wrongCount: 2,
    streak: 2,
    bestStreak: 4,
    lastJudgment: 'CORRECT',
    lastStudiedAt: '2026-09-12T23:02:00',
    nextReviewAt: '2026-09-22T09:00:00',
    ...overrides
  }
}

function skillPage(items: JpnSkillRow[], overrides: {
  totalElements?: number
  page?: number
  size?: number
  totalPages?: number
} = {}): unknown {
  return {
    items,
    totalElements: overrides.totalElements ?? items.length,
    page: overrides.page ?? 1,
    size: overrides.size ?? 20,
    totalPages: overrides.totalPages ?? 1
  }
}

type Call = { url: string; method: string }

function recorded(fetchMock: ReturnType<typeof vi.fn>): Call[] {
  return fetchMock.mock.calls.map((call) => {
    const init = (call[1] ?? {}) as RequestInit
    return { url: String(call[0]), method: (init.method ?? 'GET').toUpperCase() }
  })
}

function callsTo(fetchMock: ReturnType<typeof vi.fn>, prefix: string): Call[] {
  return recorded(fetchMock).filter((call) => call.url.startsWith(prefix))
}

const STATUS_PATH = '/api/user/japanese/status'
const SKILLS_PATH = '/api/user/japanese/status/skills'

/** 応答をテスト側から決められる Promise（読み込み中の表示を確かめる）。 */
function createDeferred(): { promise: Promise<Response>; resolve: (response: Response) => void } {
  let resolve: (response: Response) => void = () => {}
  const promise = new Promise<Response>((res) => {
    resolve = res
  })
  return { promise, resolve }
}

/** `get()` や `findAll().find()` が返す要素（存在は保証済みなので exists() を持たない）。 */
type FoundElement = Omit<DOMWrapper<Element>, 'exists'>

function summaryItem(wrapper: VueWrapper, label: string): FoundElement {
  const item = wrapper.findAll('.jp-summary__item')
    .find((node) => node.get('.jp-summary__label').text() === label)
  if (item === undefined) {
    throw new Error(`サマリの項目「${label}」がありません。`)
  }
  return item
}

function summaryValue(wrapper: VueWrapper, label: string): string {
  return summaryItem(wrapper, label).get('.jp-summary__value').text()
}

/** 積み上げ棒の高さ（px）を合計する。 */
function barHeight(item: FoundElement): number {
  return item.findAll('.jp-daily__segment').reduce((total, segment) => {
    const match = /height:\s*(\d+(?:\.\d+)?)px/.exec(segment.attributes('style') ?? '')
    return total + (match === null ? 0 : Number(match[1]))
  }, 0)
}

function segmentHeights(item: FoundElement): number[] {
  return item.findAll('.jp-daily__segment').map((segment) => {
    const match = /height:\s*(\d+(?:\.\d+)?)px/.exec(segment.attributes('style') ?? '')
    return match === null ? 0 : Number(match[1])
  })
}

describe('日本語勉強【単語勉強状況】', () => {
  async function setup(options: {
    status?: unknown
    skills?: unknown
    handlers?: (url: string, method: string) => Response | null
  } = {}) {
    const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
      const target = String(url)
      const method = (init?.method ?? 'GET').toUpperCase()
      const handled = options.handlers?.(target, method)
      if (handled) return handled
      if (method === 'GET' && target.startsWith(`${SKILLS_PATH}?`)) {
        return ok(options.skills ?? skillPage([skillRow()]))
      }
      if (method === 'GET' && target.startsWith(`${STATUS_PATH}?`)) {
        return ok(options.status ?? statusPage([statusRow(), untouchedRow()]))
      }
      return ok({})
    })

    vi.stubGlobal('fetch', fetchMock)
    const wrapper = mount(JapaneseWordStatusView)
    await flushPromises()
    return { wrapper, fetchMock }
  }

  beforeEach(() => {
    vi.restoreAllMocks()
  })

  it('サマリに学習した単語・習得済・平均習得度・正答率・学習時間を出す', async () => {
    const { wrapper } = await setup()

    const summary = wrapper.get('[data-jp-summary]')
    expect(summary.findAll('.jp-summary__item')).toHaveLength(9)

    expect(summaryValue(wrapper, '学習した単語')).toContain('213')
    expect(summaryItem(wrapper, '学習した単語').get('.jp-summary__unit').text()).toBe('語')
    expect(summaryValue(wrapper, '習得済')).toContain('84')
    expect(summaryItem(wrapper, '習得済').get('.jp-summary__unit').text()).toBe('語')
    expect(summaryValue(wrapper, 'お気に入り')).toContain('12')
    expect(summaryItem(wrapper, 'お気に入り').get('.jp-summary__unit').text()).toBe('語')
    expect(summaryValue(wrapper, '平均習得度')).toContain('63')
    expect(summaryItem(wrapper, '平均習得度').get('.jp-summary__unit').text()).toBe('%')
    expect(summaryValue(wrapper, '回答数')).toContain('5120')
    expect(summaryItem(wrapper, '回答数').get('.jp-summary__unit').text()).toBe('回')
    expect(summaryValue(wrapper, '正答率')).toContain('86')
    expect(summaryItem(wrapper, '正答率').get('.jp-summary__unit').text()).toBe('%')
    expect(summaryValue(wrapper, '総学習時間')).toBe('3 時間 25 分')
    expect(summaryValue(wrapper, '今日の学習')).toBe('45 分')
    expect(summaryValue(wrapper, '最終学習')).toBe(formatIsoDateTime('2026-09-12T23:02:00'))
  })

  it('学習時間は 60 分未満を分だけ、0 を 0 分で表す', async () => {
    const { wrapper } = await setup({
      status: statusPage([statusRow({ activeMs: minutes(45) }), untouchedRow()], {
        summary: { ...SUMMARY, activeMs: 0, todayActiveMs: minutes(90) }
      })
    })

    expect(summaryValue(wrapper, '総学習時間')).toBe('0 分')
    expect(summaryValue(wrapper, '今日の学習')).toBe('1 時間 30 分')
    expect(wrapper.get('[data-jp-status-row="101"]').text()).toContain('45 分')
    expect(wrapper.get('[data-jp-status-row="102"]').text()).toContain('0 分')
  })

  it('日次の学習量を古い順の棒グラフにし、一番長い日を 90px にする', async () => {
    const { wrapper } = await setup()

    // 13 日分。API は新しい順で返すが、グラフは古い順に並べる
    const items = wrapper.findAll('[data-jp-daily-bar]')
    expect(items).toHaveLength(13)
    expect(items[0]?.attributes('data-jp-daily-bar')).toBe('2026-09-01')
    expect(items[12]?.attributes('data-jp-daily-bar')).toBe('2026-09-13')
    expect(items[0]?.get('.jp-daily__date').text()).toBe('9/1')

    // 一番長い日（A 30 分 + B 60 分）は積み上げで 90px
    const longest = wrapper.get('[data-jp-daily-bar="2026-09-13"]')
    expect(segmentHeights(longest)).toEqual([30, 60, 0, 0, 0])
    expect(barHeight(longest)).toBe(90)
    expect(longest.get('.jp-daily__value').text()).toBe('90 分')

    // 種別ごとの色（A は基準クラス、B〜E は修飾クラス）
    const segments = longest.findAll('.jp-daily__segment')
    expect(segments[0]?.classes()).not.toContain('jp-daily__segment--b')
    expect(segments[1]?.classes()).toContain('jp-daily__segment--b')

    // 中くらいの日は比例して低くなる
    const middle = wrapper.get('[data-jp-daily-bar="2026-09-12"]')
    expect(segmentHeights(middle)).toEqual([0, 0, 45, 0, 0])
    expect(middle.get('.jp-daily__value').text()).toBe('45 分')

    // 学習が無い日は高さ 0
    const blank = wrapper.get('[data-jp-daily-bar="2026-09-11"]')
    expect(barHeight(blank)).toBe(0)
    expect(blank.get('.jp-daily__value').text()).toBe('0 分')

    // 凡例は種別 A〜E と色の対応
    const legend = wrapper.get('.jp-legend')
    expect(legend.text()).toContain('A 読み確認')
    expect(legend.text()).toContain('E 漢字の使い方')
    expect(legend.findAll('.jp-legend__item')).toHaveLength(5)
  })

  it('日次の学習記録がなければ案内を出す', async () => {
    const { wrapper } = await setup({ status: statusPage([statusRow()], { daily: [] }) })

    expect(wrapper.get('[data-jp-daily-empty]').text()).toContain('日次の学習記録がまだありません。')
    expect(wrapper.findAll('[data-jp-daily-bar]')).toHaveLength(0)
  })

  it('語別の学習状況を学習状態・習得度・次回復習つきで描画する', async () => {
    const { wrapper } = await setup()

    // 一覧の見出しには一覧アイコンを付ける（他の一覧画面と揃える）
    const wordsTitle = wrapper.findAll('.card__title').find((el) => el.text().includes('語別の学習状況'))
    expect(wordsTitle?.get('use').attributes('href')).toBe('#i-list')

    const table = wrapper.get('[data-jp-status-words]')
    expect(table.text()).toContain('勉強')
    expect(table.text()).toContain('べんきょう')

    const row = wrapper.get('[data-jp-status-row="101"]')
    expect(row.get('.jp-word').text()).toContain('勉強')
    expect(row.get('.jp-word__reading').text()).toBe('べんきょう')
    expect(row.get('.badge').text()).toBe('学習中')
    expect(row.text()).toContain('N5')
    expect(row.text()).toContain('名詞')
    expect(row.text()).toContain('日本語総まとめ N5')
    expect(row.text()).toContain('第1章')
    expect(row.get('.jp-mastery__fill').attributes('style')).toContain('width: 62%')
    expect(row.get('.jp-mastery__value').text()).toBe('62%')
    expect(row.text()).toContain('25 回 / 20 正解 / 5 不正解')
    expect(row.text()).toContain('3 回 / 最大 7 回')
    expect(row.text()).toContain('3 時間 25 分')
    expect(row.text()).toContain(formatIsoDateTime('2026-09-12T23:02:00'))
    expect(row.text()).toContain(formatIsoDateTime('2026-09-20T09:00:00'))

    // 未学習の語は未設定の項目を「—」で出す
    const untouched = wrapper.get('[data-jp-status-row="102"]')
    expect(untouched.get('.badge').text()).toBe('未学習')
    expect(untouched.get('.jp-mastery__fill').attributes('style')).toContain('width: 0%')
    expect(untouched.text()).toContain('—')
    expect(wrapper.get('[data-jp-status-count]').text()).toContain('全 2 件')
  })

  it('学習状態と JLPT の絞り込みをクエリで送る', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-status-filter="learnState"]').setValue('MASTERED')
    await wrapper.get('[data-jp-status-filter="jlpt"]').setValue('N5')
    await wrapper.get('[data-jp-status-search]').trigger('click')
    await flushPromises()

    const call = callsTo(fetchMock, STATUS_PATH).at(-1)
    expect(call?.method).toBe('GET')
    expect(call?.url).toBe(`${STATUS_PATH}?learnState=MASTERED&jlpt=N5&page=1&size=20`)
  })

  it('リセットで絞り込みを消して 1 ページ目から読み直す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-status-filter="learnState"]').setValue('REVIEW')
    await wrapper.get('[data-jp-status-filter="jlpt"]').setValue('N3')
    await wrapper.get('[data-jp-status-search]').trigger('click')
    await flushPromises()
    expect(callsTo(fetchMock, STATUS_PATH).at(-1)?.url).toContain('learnState=REVIEW')

    await wrapper.get('[data-jp-status-reset]').trigger('click')
    await flushPromises()

    expect(callsTo(fetchMock, STATUS_PATH).at(-1)?.url).toBe(`${STATUS_PATH}?page=1&size=20`)
    expect((wrapper.get('[data-jp-status-filter="learnState"]').element as HTMLSelectElement).value).toBe('')
    expect((wrapper.get('[data-jp-status-filter="jlpt"]').element as HTMLSelectElement).value).toBe('')
  })

  it('該当する学習状況がなければ案内を出す', async () => {
    const { wrapper } = await setup({ status: statusPage([], { totalElements: 0, totalPages: 0 }) })

    expect(wrapper.get('[data-jp-status-words-empty]').text()).toContain('該当する学習状況がありません。')
    expect(wrapper.find('[data-jp-status-words]').exists()).toBe(false)
  })

  it('ページングでページ番号と件数を送る', async () => {
    const { wrapper, fetchMock } = await setup({
      status: statusPage([statusRow(), untouchedRow()], { totalElements: 213, totalPages: 11 })
    })

    expect(wrapper.get('[data-jp-status-count]').text()).toContain('全 213 件')
    expect(wrapper.get('.pagination__info').text()).toContain('1 / 11 ページ')

    await wrapper.get('[data-jp-page="2"]').trigger('click')
    await flushPromises()
    expect(callsTo(fetchMock, STATUS_PATH).at(-1)?.url).toBe(`${STATUS_PATH}?page=2&size=20`)

    await wrapper.get('[data-jp-page-size]').setValue('50')
    await flushPromises()
    expect(callsTo(fetchMock, STATUS_PATH).at(-1)?.url).toBe(`${STATUS_PATH}?page=1&size=50`)
  })

  it('再読み込みで学習状況を取り直す', async () => {
    const { wrapper, fetchMock } = await setup()
    expect(callsTo(fetchMock, STATUS_PATH)).toHaveLength(1)

    await wrapper.get('[data-jp-refresh]').trigger('click')
    await flushPromises()

    expect(callsTo(fetchMock, STATUS_PATH)).toHaveLength(2)
  })

  it('技能タブを押すまで技能の API を呼ばない', async () => {
    const { wrapper, fetchMock } = await setup()

    expect(callsTo(fetchMock, SKILLS_PATH)).toHaveLength(0)
    expect(wrapper.find('[data-jp-skills]').exists()).toBe(false)

    await wrapper.get('[data-jp-tab="skills"]').trigger('click')
    await flushPromises()

    expect(callsTo(fetchMock, SKILLS_PATH)).toHaveLength(1)
    expect(callsTo(fetchMock, SKILLS_PATH)[0]?.url).toBe(`${SKILLS_PATH}?page=1&size=20`)
    expect(wrapper.get('[data-jp-skills]').findAll('tbody tr')).toHaveLength(1)
  })

  it('技能別の習得を描画する', async () => {
    const { wrapper } = await setup({
      skills: skillPage([
        skillRow(),
        skillRow({
          wordId: 102,
          word: '図書館',
          reading: null,
          testType: 'D',
          skillCode: 'CONTEXT',
          learnState: 'MASTERED',
          mastery: 90,
          streak: 0,
          bestStreak: 2,
          lastJudgment: null,
          lastStudiedAt: null,
          nextReviewAt: null
        })
      ])
    })

    await wrapper.get('[data-jp-tab="skills"]').trigger('click')
    await flushPromises()

    const table = wrapper.get('[data-jp-skills]')
    // 一覧の見出しには一覧アイコンを付ける（他の一覧画面と揃える）
    const skillsTitle = wrapper.findAll('.card__title').find((el) => el.text().includes('技能別の習得'))
    expect(skillsTitle?.get('use').attributes('href')).toBe('#i-list')
    expect(table.text()).toContain('B：表記・読み')
    expect(table.text()).toContain('D：文脈の意味')
    expect(table.text()).toContain('MEANING')
    expect(table.text()).toContain('CONTEXT')

    const row = wrapper.get('[data-jp-skill-row="101-B-MEANING"]')
    expect(row.get('.jp-word').text()).toContain('勉強')
    expect(row.get('.jp-word__reading').text()).toBe('べんきょう')
    expect(row.get('.badge').text()).toBe('復習')
    expect(row.get('.jp-mastery__fill').attributes('style')).toContain('width: 55%')
    expect(row.text()).toContain('8 回 / 6 正解 / 2 不正解')
    expect(row.text()).toContain('2 回 / 最大 4 回')
    expect(row.text()).toContain('CORRECT')

    expect(wrapper.get('[data-jp-skill-row="102-D-CONTEXT"]').text()).toContain('—')
    expect(wrapper.get('[data-jp-skill-count]').text()).toContain('全 2 件')
  })

  it('技能の絞り込みをクエリで送り、リセットで消す', async () => {
    const { wrapper, fetchMock } = await setup()

    await wrapper.get('[data-jp-tab="skills"]').trigger('click')
    await flushPromises()

    await wrapper.get('[data-jp-skill-filter="testType"]').setValue('D')
    await wrapper.get('[data-jp-skill-filter="skill"]').setValue('CONTEXT')
    await wrapper.get('[data-jp-skill-search]').trigger('click')
    await flushPromises()
    expect(callsTo(fetchMock, SKILLS_PATH).at(-1)?.url)
      .toBe(`${SKILLS_PATH}?testType=D&skill=CONTEXT&page=1&size=20`)

    await wrapper.get('[data-jp-skill-reset]').trigger('click')
    await flushPromises()
    expect(callsTo(fetchMock, SKILLS_PATH).at(-1)?.url).toBe(`${SKILLS_PATH}?page=1&size=20`)
    expect((wrapper.get('[data-jp-skill-filter="testType"]').element as HTMLSelectElement).value).toBe('')
  })

  it('技能のページングでページ番号と件数を送る', async () => {
    const { wrapper, fetchMock } = await setup({
      skills: skillPage([skillRow()], { totalElements: 906, totalPages: 46 })
    })

    await wrapper.get('[data-jp-tab="skills"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-jp-skill-count]').text()).toContain('全 906 件')

    await wrapper.get('[data-jp-skill-page="2"]').trigger('click')
    await flushPromises()
    expect(callsTo(fetchMock, SKILLS_PATH).at(-1)?.url).toBe(`${SKILLS_PATH}?page=2&size=20`)

    await wrapper.get('[data-jp-skill-page-size]').setValue('100')
    await flushPromises()
    expect(callsTo(fetchMock, SKILLS_PATH).at(-1)?.url).toBe(`${SKILLS_PATH}?page=1&size=100`)
  })

  it('該当する技能の習得状況がなければ案内を出す', async () => {
    const { wrapper } = await setup({ skills: skillPage([], { totalElements: 0, totalPages: 0 }) })

    await wrapper.get('[data-jp-tab="skills"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('[data-jp-skills-empty]').text()).toContain('該当する技能の習得状況がありません。')
    expect(wrapper.find('[data-jp-skills]').exists()).toBe(false)
  })

  it('読み込み中は案内を出し、終わると消える', async () => {
    const deferred = createDeferred()
    const fetchMock = vi.fn(() => deferred.promise)
    vi.stubGlobal('fetch', fetchMock)

    const wrapper = mount(JapaneseWordStatusView)
    await flushPromises()
    expect(wrapper.text()).toContain('読み込んでいます...')

    deferred.resolve(ok(statusPage([statusRow()])))
    await flushPromises()

    expect(wrapper.text()).not.toContain('読み込んでいます...')
    expect(wrapper.find('[data-jp-status-words]').exists()).toBe(true)
  })

  it('読み込みに失敗したら ApiError のメッセージを alert に出す', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        method === 'GET' && url.startsWith(`${STATUS_PATH}?`)
          ? failure('サーバーでエラーが発生しました。')
          : null
    })

    expect(wrapper.get('.alert.alert--danger').text()).toContain('サーバーでエラーが発生しました。')
    expect(wrapper.find('[data-jp-summary]').exists()).toBe(false)
  })

  it('技能の読み込みに失敗したら alert に出す', async () => {
    const { wrapper } = await setup({
      handlers: (url, method) =>
        method === 'GET' && url.startsWith(`${SKILLS_PATH}?`)
          ? failure('技能の習得状況を取得できませんでした。')
          : null
    })

    await wrapper.get('[data-jp-tab="skills"]').trigger('click')
    await flushPromises()

    expect(wrapper.get('.alert.alert--danger').text()).toContain('技能の習得状況を取得できませんでした。')
  })
})

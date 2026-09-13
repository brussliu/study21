import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { useToast } from '@study21/web-shared'
import GomokuView from '@/views/game/GomokuView.vue'
import type { GameMatchDetail, GameMatchSummary } from '@/api/games'

/**
 * ゲーム対戦（五子棋・黑白棋。学生 vs 家長が同時に対戦）。
 *
 * ・DB が唯一の正。SSE は「変わった」通知だけに使い、受けたら必ず GET で読み直す。
 * ・着手は版（version）を送る。409 は「他の操作で先に更新されました」を出して読み直す。
 * ・持ち時間の制限は無し。
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function conflict(message = '他の操作で先に更新されました。再読み込みしてください。'): Response {
  return new Response(JSON.stringify({ success: false, code: 'CONFLICT', message, data: null, timestamp: '' }), {
    status: 409,
    headers: { 'Content-Type': 'application/json' }
  })
}

function matchSummary(overrides: Partial<GameMatchSummary> = {}): GameMatchSummary {
  return {
    matchId: 1,
    gameType: 'GOMOKU',
    status: 'WAITING',
    myTurn: false,
    iAmChallenger: false,
    opponentName: '劉 競澤',
    myStone: 'WHITE',
    moveCount: 0,
    resultCode: null,
    winnerAccountId: null,
    updatedAt: '2026-09-13T10:00:00',
    version: 1,
    ...overrides
  }
}

/** 15×15 の空盤（中心だけ黒 → 白が打つ想定）。 */
function emptyBoard(): (null | 'BLACK' | 'WHITE')[][] {
  return Array.from({ length: 15 }, () => Array.from({ length: 15 }, () => null as null | 'BLACK' | 'WHITE'))
}

function matchDetail(overrides: Partial<GameMatchDetail> = {}): GameMatchDetail {
  // 1 手目（黒が 8 行 8 列）が盤面にも入っている状態
  const board = emptyBoard()
  board[7]![7] = 'BLACK'
  return {
    matchId: 1,
    gameType: 'GOMOKU',
    status: 'PLAYING',
    board,
    myStone: 'BLACK',
    challengerStone: 'BLACK',
    turnAccountId: 2,
    myTurn: true,
    moveCount: 1,
    winnerAccountId: null,
    resultCode: null,
    version: 3,
    challengerAccountId: 2,
    opponentAccountId: 1,
    challengerName: '劉 競澤',
    opponentName: 'Liu Bruss',
    opponentOnline: true,
    finishedAt: null,
    moves: [{ moveNumber: 1, accountId: 2, stone: 'BLACK', row: 7, col: 7, pass: false, flipped: [], line: [] }],
    ...overrides
  }
}

type Call = { url: string; method: string; body: string | null }

/** EventSource のスタブ（イベントを手で発火できる）。 */
class FakeEventSource {
  static instances: FakeEventSource[] = []
  url: string
  closed = false
  private listeners = new Map<string, ((event: Event) => void)[]>()
  constructor(url: string) {
    this.url = url
    FakeEventSource.instances.push(this)
  }
  addEventListener(type: string, handler: (event: Event) => void): void {
    this.listeners.set(type, [...(this.listeners.get(type) ?? []), handler])
  }
  removeEventListener(): void {}
  close(): void {
    this.closed = true
  }
  emit(type: string, data?: unknown): void {
    const event = { data: data === undefined ? '' : JSON.stringify(data) } as MessageEvent<string>
    for (const handler of this.listeners.get(type) ?? []) handler(event as unknown as Event)
  }
  lastEvent(): string | null {
    void this.listeners
    return null
  }
}

function router(): Router {
  return createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/:area/game', component: Dummy },
      { path: '/:area/game/:slug', component: Dummy }
    ]
  })
}

async function mountGomoku(handlers: {
  matches?: GameMatchSummary[]
  detail?: GameMatchDetail
  moveResponse?: () => Response
} = {}) {
  const r = router()
  await r.push('/student/game/gomoku')
  await r.isReady()
  const matches = handlers.matches ?? [matchSummary()]
  const detail = handlers.detail ?? matchDetail()
  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target = String(url)
    const method = (init?.method ?? 'GET').toUpperCase()
    if (target.includes('/games/opponents')) {
      return ok({ items: [{ accountId: 1, displayName: 'Liu Bruss', role: 'GUARDIAN' }] })
    }
    if (target.includes('/moves') && method === 'POST') {
      return handlers.moveResponse ? handlers.moveResponse() : ok(detail, '指しました。')
    }
    if (/\/games\/matches\/\d+/.test(target) && method === 'GET') {
      return ok(detail)
    }
    if (target.includes('/games/matches') && method === 'POST') {
      return ok(detail, '対戦を申し込みました。')
    }
    if (target.includes('/games/matches')) {
      return ok({ items: matches })
    }
    return ok({})
  })
  vi.stubGlobal('fetch', fetchMock)
  const wrapper = mount(GomokuView, { global: { plugins: [r] } })
  await flushPromises()
  return {
    wrapper,
    calls: () => fetchMock.mock.calls.map((call) => ({
      url: String(call[0]),
      method: (((call[1] ?? {}) as RequestInit).method ?? 'GET').toUpperCase(),
      body: ((call[1] ?? {}) as RequestInit).body === undefined ? null : String(((call[1] ?? {}) as RequestInit).body)
    })) as Call[]
  }
}

/** 一覧の取り直しと再描画を待つ（画面を開いた時点で一覧・相手・SSE は揃っている）。 */
async function settle(): Promise<void> {
  await flushPromises()
  await nextTick()
  await flushPromises()
}

beforeEach(() => {
  useToast().items.splice(0)
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
})

afterEach(() => {
  vi.unstubAllGlobals()
})

describe('ゲーム対戦（五子棋）', () => {
  it('対局中はステータスに相手の状態と【投了】を出し、相手が不在なら赤で示す', async () => {
    vi.useFakeTimers()
    const detail = matchDetail({ opponentOnline: true })
    const { wrapper, calls } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING', iAmChallenger: true })],
      detail
    })
    await settle()

    // 相手の状態をステータスに出す（オンラインのときは赤くしない）
    const online = wrapper.get('[data-opponent-status]')
    expect(online.text()).toContain('オンライン')
    expect(online.classes()).not.toContain('is-offline')
    // 対局中は【投了】がある（【終了】は出さない）
    expect(wrapper.find('[data-match-resign]').exists()).toBe(true)
    expect(wrapper.find('[data-match-timeout]').exists()).toBe(false)

    // 相手がゲーム画面を閉じた（次の確認でオフラインになる）
    detail.opponentOnline = false
    vi.advanceTimersByTime(5000)
    await flushPromises()
    await nextTick()

    const offline = wrapper.get('[data-opponent-status]')
    expect(offline.text()).toContain('オフライン')
    expect(offline.classes()).toContain('is-offline')
    expect(wrapper.find('[data-match-timeout]').exists()).toBe(true)
    // 5 秒ごとに確認している（相手の接続が切れたことを取りに行く）
    expect(calls().filter((call) => call.url.endsWith('/games/matches/1')).length).toBeGreaterThan(1)
  })

  it('【終了】は自分の勝ちで対局を終わらせる（相手が不在のとき）', async () => {
    const { wrapper, calls } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING', iAmChallenger: true })],
      detail: matchDetail({ opponentOnline: false })
    })
    await settle()

    await wrapper.get('[data-match-timeout]').trigger('click')
    await flushPromises()

    expect(calls().some((call) => call.method === 'POST' && call.url.endsWith('/games/matches/1/timeout'))).toBe(true)
  })

  it('【投了】は自分の負けで終わる', async () => {
    const { wrapper, calls } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING', iAmChallenger: true })],
      detail: matchDetail({ opponentOnline: true })
    })
    await settle()

    await wrapper.get('[data-match-resign]').trigger('click')
    await flushPromises()

    expect(calls().some((call) => call.method === 'POST' && call.url.endsWith('/games/matches/1/resign'))).toBe(true)
  })

  it('終わった対局は【盤面を閉じる】で一人用に戻る', async () => {
    const detail = matchDetail({ myStone: 'BLACK', status: 'PLAYING' })
    const { wrapper } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING', iAmChallenger: true })],
      detail
    })
    await settle()

    // 対局中なので【盤面を閉じる】は出さない（投了・終了だけ）
    expect(wrapper.find('[data-match-close]').exists()).toBe(false)
    expect(wrapper.find('[data-match-current]').exists()).toBe(true)

    // 決着（勝ち）→ 【盤面を閉じる】が出る
    detail.status = 'FINISHED'
    detail.resultCode = 'WIN'
    detail.winnerAccountId = 2
    await wrapper.get('[data-match-resign]').trigger('click')
    await flushPromises()
    await nextTick()

    expect(wrapper.find('[data-match-close]').exists()).toBe(true)
    await wrapper.get('[data-match-close]').trigger('click')
    await flushPromises()
    await nextTick()

    // 盤面を閉じると一人用の操作（モード・新しい対局）が出る
    expect(wrapper.find('[data-match-current]').exists()).toBe(false)
    expect(wrapper.find('[data-new-game]').exists()).toBe(true)
  })

  it('画面を開くと相手と一覧を取りに行き、通知（SSE）を張る', async () => {
    const { wrapper, calls } = await mountGomoku({ matches: [matchSummary()] })
    await settle()

    expect(calls().some((call) => call.url.includes('/games/opponents'))).toBe(true)
    expect(calls().some((call) => call.url.includes('/games/matches'))).toBe(true)
    // SSE を張っている（通知で読み直すため）
    expect(FakeEventSource.instances.length).toBe(1)
    expect(FakeEventSource.instances[0]?.url).toBe('/api/user/games/stream')
    // 対戦のカード（一覧）は出さない。相手の状態はステータスに出す
    expect(wrapper.find('[data-game-match]').exists()).toBe(false)
  })

  // 申し込みの流れ（2人対戦 →【新しい対局】→ 小窓）は tests/games-challenge.spec.ts で固定する。

  // 招待の受ける／断るは応戦ダイアログに移した（tests/games-challenge.spec.ts で固定する）。

  it('進行中の対戦を開くと盤面が出て、自分の手番でだけ着手できる', async () => {
    const { wrapper, calls } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING', iAmChallenger: true, iAmChallengerName: undefined } as never)],
      detail: matchDetail({ myStone: 'BLACK', myTurn: true, version: 3 })
    })
    await settle()
    await settle()
    await flushPromises()

    expect(wrapper.get('[data-match-current]').text()).toContain('あなたの手番')
    // 盤面は API の盤面（8 行 8 列＝index 112 に黒）
    const cells = wrapper.findAll('.gm-gomoku-cell')
    expect(cells[112]?.find('.gm-stone--black').exists()).toBe(true)

    // 着手は POST /moves に版を入れて送る
    await wrapper.get('.gm-gomoku-cell:nth-child(1)').trigger('click')
    await flushPromises()
    const call = calls().find((entry) => entry.url.endsWith('/games/matches/1/moves'))
    expect(call?.body).toBe(JSON.stringify({ row: 0, col: 0, version: 3 }))
  })

  it('相手の手番では着手できない（待ち表示）', async () => {
    const { wrapper, calls } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING', myTurn: false })],
      detail: matchDetail({ myStone: 'WHITE', myTurn: false, version: 4 })
    })
    await settle()
    await settle()
    await flushPromises()

    expect(wrapper.get('[data-match-current]').text()).toContain('相手の手番')
    await wrapper.get('.gm-gomoku-cell:nth-child(1)').trigger('click')
    await flushPromises()

    expect(calls().some((entry) => entry.url.endsWith('/moves'))).toBe(false)
    expect(useToast().items.map((item) => item.message).join(' ')).toContain('相手の手番です')
  })

  it('409（版違い）はメッセージを出して読み直す', async () => {
    const { wrapper, calls } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING' })],
      detail: matchDetail({ myTurn: true, version: 3 }),
      moveResponse: () => conflict()
    })
    await settle()
    await settle()
    await flushPromises()
    const before = calls().filter((entry) => /\/games\/matches\/1$/.test(entry.url)).length

    await wrapper.get('.gm-gomoku-cell:nth-child(1)').trigger('click')
    await flushPromises()

    expect(useToast().items.map((item) => item.message).join(' '))
      .toContain('他の操作で先に更新されました。再読み込みしてください。')
    // GET /matches/1 で読み直している
    expect(calls().filter((entry) => /\/games\/matches\/1$/.test(entry.url)).length).toBeGreaterThan(before)
  })

  it('SSE の move / finished 通知で読み直す（DB が正）', async () => {
    const { wrapper, calls } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING' })],
      detail: matchDetail({ myTurn: false, version: 5 })
    })
    await settle()
    await settle()
    await flushPromises()

    const stream = FakeEventSource.instances[0]
    const before = calls().filter((entry) => /\/games\/matches\/1$/.test(entry.url)).length

    stream?.emit('move', { matchId: 1, version: 6, accountId: 2 })
    await flushPromises()
    expect(calls().filter((entry) => /\/games\/matches\/1$/.test(entry.url)).length).toBeGreaterThan(before)

    stream?.emit('finished', { matchId: 1, winnerAccountId: 2, resultCode: 'WIN' })
    await flushPromises()
    expect(calls().filter((entry) => /\/games\/matches\/1$/.test(entry.url)).length).toBeGreaterThan(before)

    // 画面を閉じるときに SSE を閉じる
    wrapper.unmount()
    expect(stream?.closed).toBe(true)
  })

  it('終了した対局は勝敗を出す', async () => {
    const detail = matchDetail({ myStone: 'BLACK', status: 'PLAYING' })
    const { wrapper } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING', iAmChallenger: true })],
      detail
    })
    await settle()

    // 相手の投了で決着（通知 → 読み直し）
    detail.status = 'FINISHED'
    detail.resultCode = 'WIN'
    detail.winnerAccountId = 2
    detail.finishedAt = '2026-09-13T10:30:00'
    FakeEventSource.instances[0]?.emit('finished', { matchId: 1, winnerAccountId: 2, resultCode: 'WIN' })
    await settle()

    expect(wrapper.get('[data-match-result]').text()).toContain('あなたの勝ちです！')
  })

  it('投了の URL を叩く', async () => {
    const { wrapper, calls } = await mountGomoku({
      matches: [matchSummary({ matchId: 1, status: 'PLAYING' })],
      detail: matchDetail({ myTurn: true })
    })
    await settle()
    await settle()
    await flushPromises()

    await wrapper.get('[data-match-resign]').trigger('click')
    await flushPromises()
    expect(calls().some((call) => call.method === 'POST' && call.url.endsWith('/games/matches/1/resign'))).toBe(true)
  })
})

import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount, type VueWrapper } from '@vue/test-utils'
import { defineComponent, nextTick } from 'vue'
import { createMemoryHistory, createRouter, type Router } from 'vue-router'
import { useToast } from '@study21/web-shared'
import GomokuView from '@/views/game/GomokuView.vue'
import type { GameMatchDetail, GameMatchSummary, GameOpponent } from '@/api/games'

/**
 * 対戦の申し込み（2 人対戦）の流れ。
 *
 * ① モードで「2人対戦」を選んで【新しい対局】
 * ② 小さいウィンドウで 相手・先手/後手 を選んで【対戦を申し込む】（ウィンドウは閉じない）
 * ③ 相手がオフラインならその旨を出す
 * ④ 相手がオンラインなら相手の画面に応戦ダイアログが出る
 * ⑤ 応戦しないときは申請側に「応戦しませんでした」を出す
 * ⑥ 応戦したときは申請側のウィンドウが閉じて対戦が始まる
 */
const Dummy = defineComponent({ name: 'Dummy', render: () => null })

function ok(data: unknown, message = 'OK'): Response {
  return new Response(JSON.stringify({ success: true, code: 'OK', message, data, timestamp: '' }), {
    status: 200,
    headers: { 'Content-Type': 'application/json' }
  })
}

function validationError(message: string): Response {
  return new Response(JSON.stringify({ success: false, code: 'VALIDATION_ERROR', message, data: null, timestamp: '' }), {
    status: 400,
    headers: { 'Content-Type': 'application/json' }
  })
}

function opponent(overrides: Partial<GameOpponent> = {}): GameOpponent {
  return { accountId: 1, displayName: 'Liu Bruss', role: 'GUARDIAN', online: true, ...overrides }
}

function emptyBoard(): (null | 'BLACK' | 'WHITE')[][] {
  return Array.from({ length: 15 }, () => Array.from({ length: 15 }, () => null as null | 'BLACK' | 'WHITE'))
}

function matchDetail(overrides: Partial<GameMatchDetail> = {}): GameMatchDetail {
  return {
    matchId: 7,
    gameType: 'GOMOKU',
    status: 'PLAYING',
    board: emptyBoard(),
    myStone: 'BLACK',
    challengerStone: 'BLACK',
    turnAccountId: 2,
    myTurn: true,
    moveCount: 0,
    winnerAccountId: null,
    resultCode: null,
    version: 2,
    challengerAccountId: 2,
    opponentAccountId: 1,
    challengerName: '劉 競澤',
    opponentName: 'Liu Bruss',
    opponentOnline: true,
    finishedAt: null,
    moves: [],
    ...overrides
  }
}

function matchSummary(overrides: Partial<GameMatchSummary> = {}): GameMatchSummary {
  return {
    matchId: 7,
    gameType: 'GOMOKU',
    status: 'WAITING',
    myTurn: true,
    iAmChallenger: true,
    opponentName: 'Liu Bruss',
    myStone: 'BLACK',
    moveCount: 0,
    resultCode: null,
    winnerAccountId: null,
    updatedAt: '2026-09-13T10:00:00',
    version: 1,
    ...overrides
  }
}

type Call = { url: string; method: string; body: string | null }

/** EventSource のスタブ（相手側の通知を手で発火できる）。 */
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

async function mountGame(options: {
  opponents?: GameOpponent[]
  matches?: GameMatchSummary[]
  inviteResponse?: () => Response
  detail?: GameMatchDetail
} = {}) {
  const r = router()
  await r.push('/student/game/gomoku')
  await r.isReady()
  const opponents = options.opponents ?? [opponent()]
  let matches = options.matches ?? []
  const detail = options.detail ?? matchDetail()

  const fetchMock = vi.fn(async (url: string, init?: RequestInit) => {
    const target = String(url)
    const method = (init?.method ?? 'GET').toUpperCase()
    if (target.includes('/games/opponents')) {
      return ok({ items: opponents })
    }
    if (target.includes('/games/matches') && method === 'POST' && /\/accept$/.test(target)) {
      matches = [matchSummary({ status: 'PLAYING' })]
      return ok(detail, '対戦を始めます。')
    }
    if (target.includes('/games/matches') && method === 'POST' && /\/decline$/.test(target)) {
      return ok(detail, '対戦を断りました。')
    }
    if (target.includes('/games/matches') && method === 'POST' && /\/cancel$/.test(target)) {
      matches = []
      return ok(detail, '対戦を取り消しました。')
    }
    if (target.includes('/games/matches') && method === 'POST') {
      if (options.inviteResponse) return options.inviteResponse()
      matches = [matchSummary()]
      return ok(matchDetail({ status: 'WAITING' }), '対戦を申し込みました。')
    }
    if (/\/games\/matches\/\d+/.test(target) && method === 'GET') {
      return ok(detail)
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

/** 【新しい対局】を押す（2 人対戦を選んでから）。 */
async function startMatchFlow(wrapper: VueWrapper): Promise<void> {
  await wrapper.get('[data-solo-mode]').setValue('match')
  await wrapper.get('[data-new-game]').trigger('click')
  await flushPromises()
  await nextTick()
}

beforeEach(() => {
  useToast().items.splice(0)
  FakeEventSource.instances = []
  vi.stubGlobal('EventSource', FakeEventSource)
})

describe('対戦の申し込み（五子棋）', () => {
  it('モードは CPU が先頭で、既定は CPU', async () => {
    const { wrapper } = await mountGame()

    const mode = wrapper.get('[data-solo-mode]')
    const options = mode.findAll('option').map((option) => ({ value: option.attributes('value'), label: option.text() }))
    expect(options[0]?.value).toBe('cpu')
    // CPU が先頭・既定。2人対戦は ネット対戦（match）と 同じ画面（local）
    expect(options.map((option) => option.value)).toEqual(['cpu', 'match', 'local'])
    expect((mode.element as HTMLSelectElement).value).toBe('cpu')
  })

  it('CPU のときに【新しい対局】を押すと申し込みダイアログは出ない（その場で対局）', async () => {
    const { wrapper } = await mountGame()

    await wrapper.get('[data-new-game]').trigger('click')
    await flushPromises()

    expect(wrapper.find('[data-challenge-dialog]').exists()).toBe(false)
  })

  it('2 人対戦で【新しい対局】を押すと申し込みダイアログが開く（対局は始めない）', async () => {
    const { wrapper, calls } = await mountGame()
    await startMatchFlow(wrapper)

    expect(wrapper.find('[data-challenge-dialog]').exists()).toBe(true)
    // 盤面はまだ始まっていない（申し込む前）
    expect(calls().some((call) => call.method === 'POST')).toBe(false)
  })

  it('相手・先手後手を選んで申し込む（body と、ウィンドウが閉じないこと）', async () => {
    const { wrapper, calls } = await mountGame()
    await startMatchFlow(wrapper)

    await wrapper.get('[data-challenge-opponent]').setValue('1')
    await wrapper.get('[data-challenge-stone]').setValue('WHITE')
    await wrapper.get('[data-challenge-submit]').trigger('click')
    await flushPromises()

    const post = calls().find((call) => call.method === 'POST' && call.url.endsWith('/games/matches'))
    expect(post?.body).toBe(JSON.stringify({ gameType: 'GOMOKU', opponentAccountId: 1, challengerStone: 'WHITE' }))
    // ② 申し込んでもウィンドウは閉じない（返事待ちを出す）
    expect(wrapper.find('[data-challenge-dialog]').exists()).toBe(true)
    expect(wrapper.get('[data-challenge-dialog]').text()).toContain('相手の返事を待っています')
  })

  it('相手がオフラインならその旨を出して申し込まない（ウィンドウは開いたまま）', async () => {
    const { wrapper, calls } = await mountGame({
      opponents: [opponent({ online: false })],
      inviteResponse: () => validationError('相手がオンラインではありません。相手がゲーム画面を開いてから申し込んでください。')
    })
    await startMatchFlow(wrapper)

    await wrapper.get('[data-challenge-submit]').trigger('click')
    await flushPromises()

    const dialog = wrapper.get('[data-challenge-dialog]')
    expect(wrapper.find('[data-challenge-dialog]').exists()).toBe(true)
    expect(dialog.text()).toContain('相手がオンラインではありません')
    // 申し込みは 1 回だけで、対戦は始まっていない
    expect(calls().filter((call) => call.method === 'POST').length).toBe(1)
    expect(wrapper.get('[data-challenge-dialog]').text()).not.toContain('相手の返事を待っています')
  })

  it('相手が応戦したら申請側のウィンドウが閉じて対戦が始まる', async () => {
    const { wrapper } = await mountGame()
    await startMatchFlow(wrapper)
    await wrapper.get('[data-challenge-submit]').trigger('click')
    await flushPromises()
    expect(wrapper.get('[data-challenge-dialog]').text()).toContain('相手の返事を待っています')

    // ④⑥ 相手が応戦した（サーバーからの通知）→ 盤面が開く
    FakeEventSource.instances[0]?.emit('accepted', { matchId: 7, version: 2 })
    await flushPromises()
    await nextTick()
    await flushPromises()

    expect(wrapper.find('[data-challenge-dialog]').exists()).toBe(false)
    expect(wrapper.find('[data-match-board-status]').exists()).toBe(true)
    expect(wrapper.get('[data-match-board-status]').text()).toContain('あなたの手番')
  })

  it('相手が応戦しなかったら申請側にその旨を出す（ウィンドウは開いたまま）', async () => {
    const { wrapper } = await mountGame()
    await startMatchFlow(wrapper)
    await wrapper.get('[data-challenge-submit]').trigger('click')
    await flushPromises()

    FakeEventSource.instances[0]?.emit('declined', { matchId: 7 })
    await flushPromises()
    await nextTick()
    await flushPromises()

    const dialog = wrapper.get('[data-challenge-dialog]')
    expect(dialog.text()).toContain('相手が応戦しませんでした')
    expect(dialog.text()).not.toContain('相手の返事を待っています')
  })

  it('相手側は応戦ダイアログから受けられる（accept を叩く）', async () => {
    const { wrapper, calls } = await mountGame({
      matches: [matchSummary({ iAmChallenger: false, opponentName: '劉 競澤', myStone: 'WHITE' })]
    })
    await flushPromises()
    await nextTick()

    const dialog = wrapper.get('[data-invitation-dialog]')
    expect(dialog.text()).toContain('劉 競澤')

    await wrapper.get('[data-invitation-accept]').trigger('click')
    await flushPromises()

    expect(calls().some((call) => call.method === 'POST' && call.url.endsWith('/games/matches/7/accept'))).toBe(true)
    expect(wrapper.find('[data-invitation-dialog]').exists()).toBe(false)
  })

  it('相手側が応戦しないときは断りを送る', async () => {
    const { wrapper, calls } = await mountGame({
      matches: [matchSummary({ iAmChallenger: false, opponentName: '劉 競澤', myStone: 'WHITE' })]
    })
    await flushPromises()
    await nextTick()

    await wrapper.get('[data-invitation-decline]').trigger('click')
    await flushPromises()

    expect(calls().some((call) => call.method === 'POST' && call.url.endsWith('/games/matches/7/decline'))).toBe(true)
    expect(wrapper.find('[data-invitation-dialog]').exists()).toBe(false)
  })

  it('ページ下部の対戦カード（一覧・申し込み欄・切替）を出さない', async () => {
    const { wrapper } = await mountGame()

    // カードそのものを外した（一覧・申し込み欄・一人で遊ぶ/対戦の切替も無い）
    expect(wrapper.find('[data-game-match]').exists()).toBe(false)
    expect(wrapper.find('[data-match-mode="solo"]').exists()).toBe(false)
    expect(wrapper.find('[data-match-mode="match"]').exists()).toBe(false)
    expect(wrapper.find('[data-match-invite]').exists()).toBe(false)
    expect(wrapper.find('[data-match-opponent]').exists()).toBe(false)
    expect(wrapper.find('[data-match-row]').exists()).toBe(false)
    // 申し込みと応戦の小窓は残る（必要になったときだけ出る）
    expect(wrapper.find('[data-challenge-dialog]').exists()).toBe(false)
    expect(wrapper.find('[data-invitation-dialog]').exists()).toBe(false)
  })
})

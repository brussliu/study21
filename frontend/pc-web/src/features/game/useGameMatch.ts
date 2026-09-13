import { computed, onBeforeUnmount, ref } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import {
  acceptGameMatch,
  cancelGameMatch,
  createGameMatch,
  declineGameMatch,
  fetchGameMatch,
  fetchGameMatches,
  fetchGameOpponents,
  gameStreamUrl,
  playGameMove,
  resignGameMatch,
  timeoutGameMatch,
  type ApiStone,
  type GameMatchDetail,
  type GameMatchSummary,
  type GameOpponent,
  type GameTypeCode
} from '@/api/games'

/**
 * ゲーム対戦（五子棋・黑白棋）の状態と操作。
 *
 * ・**DB が唯一の正**。SSE は「変わった」ことの通知だけに使い、受け取ったら必ず GET で読み直す。
 * ・着手は版（version）を送る。409 のときは「他の操作で先に更新されました。再読み込みしてください。」
 *   を出して読み直す。
 * ・持ち時間の制限は無し（ユーザーの指定）。
 * ・SSE が使えない環境（EventSource が無い・繋がらない）でも壊れないよう、
 *   対局中だけ 5 秒のポーリングに切り替える。
 *
 * 申し込みの流れ（ユーザーの指定）:
 *   ① 2人対戦で【新しい対局】→ ② 小窓で 相手・先手/後手 を選んで申し込む（小窓は閉じない）
 *   ③ 相手がオフラインならその旨を小窓に出す ④ 相手の画面に応戦ダイアログ
 *   ⑤ 応戦しないときは申請側にその旨を出す ⑥ 応戦したら小窓が閉じて対戦が始まる
 */
const POLL_INTERVAL_MS = 5000
/** 対局中に相手の接続を確かめる間隔（相手が突然落ちたことに気づくため）。 */
const PRESENCE_INTERVAL_MS = 5000

/** `useGameMatch` の戻り値（対戦パネルへ渡す型）。 */
export type GameMatchState = ReturnType<typeof useGameMatch>

export function useGameMatch(gameType: GameTypeCode) {
  const toast = useToast()

  const opponents = ref<GameOpponent[]>([])
  const matches = ref<GameMatchSummary[]>([])
  /** 盤面を開いている対局（招待だけの一覧とは別）。 */
  const match = ref<GameMatchDetail | null>(null)
  const loading = ref(false)
  const busy = ref(false)
  const error = ref('')
  /** SSE が繋がっているか（画面に「同期中」を出すため）。 */
  const streaming = ref(false)

  /** 申し込みの小窓を開いているか（【新しい対局】で開く）。 */
  const challengeOpen = ref(false)
  /** 申し込んで返事を待っている最中か（小窓に「返事待ち」を出す）。 */
  const challengeWaiting = ref(false)
  /** 小窓に出すメッセージ（オフライン・応戦しなかった等）。 */
  const challengeMessage = ref('')
  /** 応戦ダイアログを閉じた対戦（閉じても一覧には残るので、画面だけ抑制する）。 */
  const dismissedInvitations = ref<number[]>([])

  const hasOpenMatch = computed(() => match.value !== null)
  const canPlay = computed(() =>
    match.value !== null && match.value.status === 'PLAYING' && match.value.myTurn)
  /** 招待中（自分が相手のもの＝受ける・断るが出る）。 */
  const invitations = computed(() =>
    matches.value.filter((row) => row.status === 'WAITING' && !row.iAmChallenger))
  /** 自分が申し込んで返事待ちのもの。 */
  const waitingMine = computed(() =>
    matches.value.filter((row) => row.status === 'WAITING' && row.iAmChallenger))
  const playing = computed(() => matches.value.filter((row) => row.status === 'PLAYING'))
  const finished = computed(() => matches.value.filter((row) => row.status === 'FINISHED'))

  /** いま自分が申し込んで返事を待っている対戦（小窓の「返事待ち」表示に使う）。 */
  const pendingChallenge = computed<GameMatchSummary | null>(() => waitingMine.value[0] ?? null)
  /** いま届いている招待（無ければ null。応戦ダイアログを出す）。 */
  const incomingInvitation = computed<GameMatchSummary | null>(() =>
    invitations.value.find((row) => !dismissedInvitations.value.includes(row.matchId)) ?? null)

  let stream: EventSource | null = null
  let pollHandle: ReturnType<typeof setInterval> | null = null
  let presenceHandle: ReturnType<typeof setInterval> | null = null

  function failureText(caught: unknown, fallback: string): string {
    return caught instanceof ApiError ? caught.message : fallback
  }

  /** 自分が関わっている対戦の一覧を取り直す。 */
  async function loadMatches(): Promise<void> {
    try {
      const response = await fetchGameMatches()
      matches.value = response.data.items
    } catch (caught) {
      error.value = failureText(caught, '対戦一覧を取得できませんでした。')
    }
  }

  /** 対戦できる相手（子ども・親）。 */
  async function loadOpponents(): Promise<void> {
    try {
      const response = await fetchGameOpponents()
      opponents.value = response.data.items
    } catch (caught) {
      error.value = failureText(caught, '対戦できる相手を取得できませんでした。')
    }
  }

  /** 開いている対局を読み直す（SSE の通知・409 のあとに必ず呼ぶ）。 */
  async function refreshOpenMatch(): Promise<void> {
    if (match.value === null) return
    try {
      const response = await fetchGameMatch(match.value.matchId)
      match.value = response.data
    } catch (caught) {
      error.value = failureText(caught, '対局の状態を取得できませんでした。')
    }
  }

  /** 一覧と開いている対局の両方を読み直す（再接続時の再同期）。 */
  async function refreshAll(): Promise<void> {
    await loadMatches()
    await refreshOpenMatch()
  }

  /**
   * 相手の状態（オンライン／オフライン）を確かめるタイマー。
   * 相手がゲーム画面を閉じてもサーバーから通知は来ないため、対局中だけ 5 秒ごとに
   * GET で読み直す（DB が唯一の正）。
   */
  function startPresenceWatch(): void {
    if (presenceHandle !== null) return
    presenceHandle = setInterval(() => {
      if (match.value !== null && match.value.status === 'PLAYING') {
        void refreshOpenMatch()
      }
    }, PRESENCE_INTERVAL_MS)
  }

  function stopPresenceWatch(): void {
    if (presenceHandle !== null) {
      clearInterval(presenceHandle)
      presenceHandle = null
    }
  }

  /**
   * 進行中の対戦があれば自動で盤面を開く。
   * 対戦の一覧は画面から外したので、読み込み直したときに続きから打てるようにする。
   */
  async function resumePlayingMatch(): Promise<void> {
    if (match.value !== null) return
    const playing = matches.value.find((row) => row.status === 'PLAYING')
    if (playing === undefined) return
    await openMatch(playing.matchId)
  }

  /** 一覧から対局を開く（盤面を表示する）。 */
  async function openMatch(matchId: number): Promise<void> {
    busy.value = true
    error.value = ''
    try {
      const response = await fetchGameMatch(matchId)
      match.value = response.data
    } catch (caught) {
      error.value = failureText(caught, '対局を開けませんでした。')
    } finally {
      busy.value = false
    }
  }

  /** 盤面を閉じる（一覧へ戻る）。 */
  function closeMatch(): void {
    match.value = null
  }

  /** 申し込みの小窓を開く（2人対戦で【新しい対局】を押したとき）。 */
  function openChallenge(): void {
    challengeMessage.value = ''
    // すでに申し込んで返事待ちの対戦があれば、その状態から再開する
    challengeWaiting.value = pendingChallenge.value !== null
    challengeOpen.value = true
  }

  /** 申し込みの小窓を閉じる（返事待ちなら取り消す）。 */
  async function closeChallenge(): Promise<void> {
    const pending = pendingChallenge.value
    const wasWaiting = challengeWaiting.value
    challengeOpen.value = false
    challengeWaiting.value = false
    challengeMessage.value = ''
    if (wasWaiting && pending !== null) {
      await act('cancel', pending.matchId)
    }
  }

  /**
   * 対戦を申し込む。
   *
   * ・成功したら小窓は**開いたまま**にして「相手の返事を待っています」を出す（ユーザーの指定 ②）。
   * ・相手がオフライン（400）のときはそのメッセージを小窓に出す（③）。
   */
  async function invite(opponentAccountId: number, challengerStone: ApiStone): Promise<boolean> {
    if (opponentAccountId <= 0) {
      challengeMessage.value = '対戦する相手を選んでください。'
      return false
    }
    busy.value = true
    error.value = ''
    challengeMessage.value = ''
    try {
      // 盤面はまだ開かない（相手が応戦したら開く）
      await createGameMatch(gameType, opponentAccountId, challengerStone)
      challengeWaiting.value = true
      await loadMatches()
      return true
    } catch (caught) {
      // 小窓の中に出す（閉じない。相手を変えてやり直せる）
      challengeMessage.value = failureText(caught, '対戦を申し込めませんでした。')
      challengeWaiting.value = false
      return false
    } finally {
      busy.value = false
    }
  }

  /** 応戦ダイアログを閉じる（あとで一覧から受けられる）。 */
  function dismissInvitation(matchId: number): void {
    if (!dismissedInvitations.value.includes(matchId)) {
      dismissedInvitations.value = [...dismissedInvitations.value, matchId]
    }
  }

  /** 招待の返事・取消・投了（結果の盤面をそのまま受け取る）。 */
  async function act(action: 'accept' | 'decline' | 'cancel' | 'resign', matchId: number): Promise<void> {
    busy.value = true
    error.value = ''
    try {
      const request = action === 'accept' ? acceptGameMatch
        : action === 'decline' ? declineGameMatch
          : action === 'cancel' ? cancelGameMatch
            : resignGameMatch
      const response = await request(matchId)
      if (action === 'accept' || action === 'decline') {
        // 応戦ダイアログは返事をした時点で閉じる（一覧の取り直しを待たない）
        dismissInvitation(matchId)
      }
      if (action === 'accept' || action === 'resign') {
        // 応戦したらそのまま盤面を開く（投了は結果の盤面を出す）
        match.value = response.data
      } else {
        match.value = match.value?.matchId === matchId ? null : match.value
      }
      if (action === 'cancel') {
        challengeWaiting.value = false
        challengeMessage.value = ''
      }
      const message = action === 'accept' ? '対戦を始めます。'
        : action === 'decline' ? '対戦を断りました。'
          : action === 'cancel' ? '対戦を取り消しました。'
            : '投了しました。'
      toast.success(message)
      await loadMatches()
    } catch (caught) {
      const message = failureText(caught, '操作できませんでした。')
      error.value = message
      toast.danger(message)
    } finally {
      busy.value = false
    }
  }

  /**
   * 相手が不在のときに対局を終了する（残った側の勝ち）。
   * 相手がまだオンラインならサーバーが断るので、そのメッセージを出して読み直す。
   */
  async function timeout(matchId: number): Promise<void> {
    busy.value = true
    error.value = ''
    try {
      const response = await timeoutGameMatch(matchId)
      match.value = response.data
      toast.success('相手が不在のため対局を終了しました。')
      await loadMatches()
    } catch (caught) {
      const message = failureText(caught, '対局を終了できませんでした。')
      error.value = message
      toast.danger(message)
      await refreshOpenMatch()
    } finally {
      busy.value = false
    }
  }

  /** 着手する（自分の手番のときだけ）。版が古ければ読み直す。 */
  async function playMove(row: number, col: number): Promise<void> {
    const current = match.value
    if (current === null || current.status !== 'PLAYING') return
    if (!current.myTurn) {
      toast.warning('相手の手番です。')
      return
    }
    if (busy.value) return
    busy.value = true
    try {
      const response = await playGameMove(current.matchId, row, col, current.version)
      match.value = response.data
      await loadMatches()
    } catch (caught) {
      if (caught instanceof ApiError && caught.status === 409) {
        // 版が古い＝他の操作で先に更新されている。DB が正なので読み直す。
        toast.warning('他の操作で先に更新されました。再読み込みしてください。')
        await refreshOpenMatch()
        return
      }
      const message = failureText(caught, 'その手は指せませんでした。')
      toast.danger(message)
      // 不正手（400）は盤面が変わっている可能性があるので読み直す
      await refreshOpenMatch()
    } finally {
      busy.value = false
    }
  }

  /** 5 秒ポーリング（SSE が使えないときの代替）。 */
  function startPolling(): void {
    if (pollHandle !== null) return
    pollHandle = setInterval(() => {
      void loadMatches()
      void refreshOpenMatch()
    }, POLL_INTERVAL_MS)
  }

  function stopPolling(): void {
    if (pollHandle !== null) {
      clearInterval(pollHandle)
      pollHandle = null
    }
  }

  /** SSE を張る（通知を受けたら GET で読み直す）。 */
  function startStream(): void {
    if (stream !== null) return
    if (typeof EventSource === 'undefined') {
      // EventSource が無い環境ではポーリングで代替する
      startPolling()
      return
    }
    try {
      stream = new EventSource(gameStreamUrl())
    } catch {
      startPolling()
      return
    }
    stream.addEventListener('open', () => {
      streaming.value = true
      stopPolling()
      // 再接続後は必ず読み直して同期する
      void refreshAll()
    })
    stream.addEventListener('invited', (event) => {
      const data = parseEvent(event)
      // ④ 相手の画面に応戦ダイアログを出す（一覧を取り直せば incomingInvitation に出る）
      toast.info('対戦の申し込みが届きました。応戦するか選んでください。')
      void loadMatches()
      if (data?.matchId !== undefined && match.value?.matchId === data.matchId) void refreshOpenMatch()
    })
    stream.addEventListener('accepted', (event) => {
      const data = parseEvent(event)
      const matchId = data?.matchId === undefined ? null : Number(data.matchId)
      void (async () => {
        await loadMatches()
        if (matchId === null) return
        const mine = matches.value.find((row) => row.matchId === matchId)
        if (mine === undefined) return
        // ⑥ 自分の申し込みが応戦された → 小窓を閉じて対戦を始める（盤面を開く）
        challengeOpen.value = false
        challengeWaiting.value = false
        challengeMessage.value = ''
        await openMatch(matchId)
        toast.success('相手が応戦しました。対戦を始めます。')
      })()
    })
    stream.addEventListener('move', (event) => {
      const data = parseEvent(event)
      if (data?.matchId !== undefined && match.value?.matchId !== data.matchId) {
        // 別の対戦の手でも一覧の手番表示は変わる
        void loadMatches()
        return
      }
      void refreshOpenMatch()
      void loadMatches()
    })
    stream.addEventListener('finished', (event) => {
      const data = parseEvent(event)
      if (data?.matchId !== undefined && match.value?.matchId === data.matchId) {
        void refreshOpenMatch()
        const won = data.winnerAccountId !== null && data.winnerAccountId !== undefined
        toast.info(won ? '対局が終わりました。' : '対局が終わりました（引き分け）。')
      }
      void loadMatches()
    })
    stream.addEventListener('declined', (event) => {
      const data = parseEvent(event)
      const matchId = data?.matchId === undefined ? null : Number(data.matchId)
      void (async () => {
        await loadMatches()
        if (matchId === null) {
          toast.info('対戦が断られました。')
          return
        }
        // ⑤ 応戦しなかった → 申請側の小窓にその旨を出す（小窓は開いたまま）
        const mine = waitingMine.value.some((row) => row.matchId === matchId)
          || matches.value.some((row) => row.matchId === matchId && row.iAmChallenger)
        if (challengeOpen.value && challengeWaiting.value && mine) {
          challengeWaiting.value = false
          challengeMessage.value = '相手が応戦しませんでした。'
          return
        }
        toast.info('対戦が断られました。')
      })()
    })
    stream.addEventListener('cancelled', (event) => {
      const data = parseEvent(event)
      if (data?.matchId !== undefined && match.value?.matchId === data.matchId) match.value = null
      challengeWaiting.value = false
      void loadMatches()
    })
    stream.addEventListener('error', () => {
      // EventSource は自動で再接続する。繋がらない環境ではポーリングに切り替える。
      streaming.value = false
      startPolling()
    })
  }

  function parseEvent(event: Event): Record<string, unknown> | null {
    const raw = (event as MessageEvent<string>).data
    if (typeof raw !== 'string' || raw.trim() === '') return null
    try {
      return JSON.parse(raw) as Record<string, unknown>
    } catch {
      return null
    }
  }

  /** SSE を閉じる（画面を離れるとき）。 */
  function stopStream(): void {
    if (stream !== null) {
      stream.close()
      stream = null
    }
    streaming.value = false
    stopPolling()
    stopPresenceWatch()
  }

  /** 最初の読み込み（一覧＋相手＋SSE）。進行中の対戦があれば盤面を開く。 */
  async function initialize(): Promise<void> {
    loading.value = true
    await Promise.all([loadMatches(), loadOpponents()])
    loading.value = false
    startStream()
    startPresenceWatch()
    await resumePlayingMatch()
  }

  onBeforeUnmount(stopStream)

  return {
    opponents,
    matches,
    invitations,
    incomingInvitation,
    waitingMine,
    pendingChallenge,
    playing,
    finished,
    match,
    hasOpenMatch,
    canPlay,
    loading,
    busy,
    error,
    streaming,
    challengeOpen,
    challengeWaiting,
    challengeMessage,
    initialize,
    loadMatches,
    loadOpponents,
    refreshAll,
    refreshOpenMatch,
    openMatch,
    closeMatch,
    openChallenge,
    closeChallenge,
    invite,
    dismissInvitation,
    act,
    timeout,
    playMove,
    startStream,
    stopStream
  }
}

/**
 * 学習・休憩・ゲームタイマー（2.0 `home.jsp` / `js/home.js` の移植）。
 *
 * 2.0 は残り秒数と実行状態をサーバー（`COM_タスク時間管理情報`）に保存し、
 * `api/home/getStudyClock` などで複数ウィンドウを同期していた。
 * 2.1 は当分サーバーを持たず、**ブラウザ内（localStorage）＋タブ間同期**で同じ動きにする
 * （ユーザーの指定）。実行中に再読み込みしても、保存した時刻との差から残り時間を計算し直す。
 *
 * ここは DOM に触らない純粋な部分だけを置く（テストしやすくするため）。
 * Vue 側の接続は `useTimers`（`useTimers.ts`）が持つ。
 */

export type TimerKind = 'study' | 'break' | 'game'

/** タイマー 1 つの状態。 */
export interface TimerSnapshot {
  /** 設定した時間（秒） */
  total: number
  /** 残り時間（秒） */
  remaining: number
  /** 動いているか */
  running: boolean
}

/** 3 つのタイマーをまとめた保存単位。 */
export interface TimersState {
  version: 1
  /** 最後に更新した時刻（ミリ秒。実行中の残り時間の計算に使う） */
  updatedAt: number
  timers: Record<TimerKind, TimerSnapshot>
}

/** 2.0 と同じ既定時間（学習 30 分・休憩 15 分・ゲーム 30 分）。 */
export const TIMER_DEFAULTS: Record<TimerKind, number> = {
  study: 30 * 60,
  break: 15 * 60,
  game: 30 * 60
}

export const TIMER_KINDS: TimerKind[] = ['study', 'break', 'game']

/** 学習タイマーの時間設定（2.0 のプリセットと同じ）。 */
export const STUDY_DURATION_OPTIONS = [300, 600, 900, 1200, 1800, 2400, 3600]

/** カスタム設定で受け付ける範囲（秒）。 */
export const TIMER_MIN_SECONDS = 60
export const TIMER_MAX_SECONDS = 12 * 60 * 60

export const STORAGE_KEY = 'study21.home.timers.v1'

/** 秒数を `HH:MM:SS` にする（2.0 の表示と同じ）。 */
export function formatRemaining(seconds: number): string {
  const safe = Math.max(0, Math.floor(seconds))
  const hours = Math.floor(safe / 3600)
  const minutes = Math.floor((safe % 3600) / 60)
  const rest = safe % 60
  return [hours, minutes, rest].map((value) => String(value).padStart(2, '0')).join(':')
}

/** 秒数の範囲を丸める（1 分〜12 時間）。 */
export function normalizeSeconds(seconds: number, fallback: number): number {
  if (!Number.isFinite(seconds) || seconds <= 0) return fallback
  return Math.min(TIMER_MAX_SECONDS, Math.max(TIMER_MIN_SECONDS, Math.floor(seconds)))
}

/** まっさらな状態（既定の時間・停止）。 */
export function createTimersState(now: number = Date.now()): TimersState {
  return {
    version: 1,
    updatedAt: now,
    timers: {
      study: { total: TIMER_DEFAULTS.study, remaining: TIMER_DEFAULTS.study, running: false },
      break: { total: TIMER_DEFAULTS.break, remaining: TIMER_DEFAULTS.break, running: false },
      game: { total: TIMER_DEFAULTS.game, remaining: TIMER_DEFAULTS.game, running: false }
    }
  }
}

/** 実行中に経過した秒数を引く（保存済みの状態と今の時刻から計算し直す）。 */
export function tickTimers(state: TimersState, now: number = Date.now()): TimersState {
  const elapsed = Math.max(0, Math.floor((now - state.updatedAt) / 1000))
  if (elapsed === 0) return state
  const timers = { ...state.timers }
  let changed = false
  for (const kind of TIMER_KINDS) {
    const timer = timers[kind]
    if (!timer.running) continue
    const remaining = Math.max(0, timer.remaining - elapsed)
    timers[kind] = { ...timer, remaining, running: remaining > 0 }
    changed = true
  }
  return changed ? { ...state, timers, updatedAt: now } : { ...state, updatedAt: now }
}

/** 開始する（残りが 0 なら何もしない。2.0 と同じく 0 では開始できない）。 */
export function startTimer(state: TimersState, kind: TimerKind, now: number = Date.now()): TimersState {
  const timer = state.timers[kind]
  if (timer.remaining <= 0) return state
  return {
    ...state,
    updatedAt: now,
    timers: { ...state.timers, [kind]: { ...timer, running: true } }
  }
}

/** 停止する（残り時間はそのまま。もう一度 開始 すると続きから動く）。 */
export function stopTimer(state: TimersState, kind: TimerKind, now: number = Date.now()): TimersState {
  const current = tickTimers(state, now)
  const timer = current.timers[kind]
  return {
    ...current,
    updatedAt: now,
    timers: { ...current.timers, [kind]: { ...timer, running: false } }
  }
}

/** 時間を設定する（残り時間もその時間に戻す。実行中なら止める）。 */
export function setDuration(
  state: TimersState,
  kind: TimerKind,
  seconds: number,
  now: number = Date.now()
): TimersState {
  const timer = state.timers[kind]
  const total = normalizeSeconds(seconds, timer.total)
  return {
    ...state,
    updatedAt: now,
    timers: { ...state.timers, [kind]: { total, remaining: total, running: false } }
  }
}

/** 保存された値を読み込む（壊れていたら既定に戻す）。 */
export function parseTimersState(raw: string | null, now: number = Date.now()): TimersState {
  const fallback = createTimersState(now)
  if (raw === null || raw === '') return fallback
  let parsed: unknown
  try {
    parsed = JSON.parse(raw)
  } catch {
    return fallback
  }
  if (parsed === null || typeof parsed !== 'object') return fallback
  const candidate = parsed as Partial<TimersState>
  if (candidate.version !== 1 || typeof candidate.updatedAt !== 'number' || candidate.timers == null) {
    return fallback
  }
  const timers = {} as Record<TimerKind, TimerSnapshot>
  for (const kind of TIMER_KINDS) {
    const source = (candidate.timers as Record<string, unknown>)[kind]
    if (source === null || typeof source !== 'object') return fallback
    const timer = source as Partial<TimerSnapshot>
    if (
      typeof timer.total !== 'number' || !Number.isFinite(timer.total) || timer.total <= 0 ||
      typeof timer.remaining !== 'number' || !Number.isFinite(timer.remaining) || timer.remaining < 0 ||
      typeof timer.running !== 'boolean'
    ) {
      return fallback
    }
    timers[kind] = {
      total: normalizeSeconds(timer.total, TIMER_DEFAULTS[kind]),
      remaining: Math.min(Math.floor(timer.remaining), TIMER_MAX_SECONDS),
      running: timer.running
    }
  }
  return tickTimers({ version: 1, updatedAt: candidate.updatedAt, timers }, now)
}

/** 保存する文字列にする。 */
export function serializeTimersState(state: TimersState): string {
  return JSON.stringify(state)
}

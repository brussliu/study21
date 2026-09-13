/**
 * 学習・休憩・ゲームタイマーの状態を画面につなぐ。
 *
 * - 状態は localStorage に保存する（実行中は「保存時刻との差」で残り時間を計算し直すので、
 *   再読み込みしても続きから動く）
 * - 別タブの変更は `storage` イベントで受け取る（2.0 の複数ウィンドウ同期と同じ考え方）
 * - 1 秒ごとに動かすのは「動いているタイマーがあるとき」だけ
 */
import { onBeforeUnmount, onMounted, ref, type Ref } from 'vue'
import {
  STORAGE_KEY,
  createTimersState,
  formatRemaining,
  parseTimersState,
  serializeTimersState,
  setDuration,
  startTimer,
  stopTimer,
  tickTimers,
  type TimerKind,
  type TimersState
} from './homeTimers'

export interface UseTimersResult {
  state: Ref<TimersState>
  remainingText: (kind: TimerKind) => string
  start: (kind: TimerKind) => void
  stop: (kind: TimerKind) => void
  setDurationSeconds: (kind: TimerKind, seconds: number) => void
}

export function useTimers(): UseTimersResult {
  const state = ref<TimersState>(load())
  let intervalId: number | null = null

  function storage(): Storage | null {
    try {
      return window.localStorage
    } catch {
      return null
    }
  }

  function load(): TimersState {
    const store = storage()
    if (store === null) return createTimersState()
    return parseTimersState(store.getItem(STORAGE_KEY))
  }

  function persist(): void {
    const store = storage()
    if (store === null) return
    try {
      store.setItem(STORAGE_KEY, serializeTimersState(state.value))
    } catch {
      // 保存できない環境（プライベートモード等）でも画面は動かす
    }
  }

  /** 動いているタイマーがあるときだけ 1 秒間隔で進める。 */
  function syncInterval(): void {
    const running = Object.values(state.value.timers).some((timer) => timer.running)
    if (running && intervalId === null) {
      intervalId = window.setInterval(() => {
        state.value = tickTimers(state.value)
        persist()
        syncInterval()
      }, 1000)
    } else if (!running && intervalId !== null) {
      window.clearInterval(intervalId)
      intervalId = null
    }
  }

  /** 他タブの変更を取り込む（自分の変更では `storage` は発火しない）。 */
  function onStorage(event: StorageEvent): void {
    if (event.key !== null && event.key !== STORAGE_KEY) return
    state.value = parseTimersState(event.newValue)
    syncInterval()
  }

  function onVisible(): void {
    if (document.visibilityState !== 'visible') return
    state.value = tickTimers(state.value)
    syncInterval()
  }

  onMounted(() => {
    state.value = tickTimers(state.value)
    persist()
    syncInterval()
    window.addEventListener('storage', onStorage)
    document.addEventListener('visibilitychange', onVisible)
  })

  onBeforeUnmount(() => {
    if (intervalId !== null) {
      window.clearInterval(intervalId)
      intervalId = null
    }
    window.removeEventListener('storage', onStorage)
    document.removeEventListener('visibilitychange', onVisible)
  })

  function update(next: TimersState): void {
    state.value = next
    persist()
    syncInterval()
  }

  return {
    state,
    remainingText: (kind) => formatRemaining(state.value.timers[kind].remaining),
    start: (kind) => update(startTimer(state.value, kind)),
    stop: (kind) => update(stopTimer(state.value, kind)),
    setDurationSeconds: (kind, seconds) => update(setDuration(state.value, kind, seconds))
  }
}

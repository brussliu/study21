import { onBeforeUnmount, reactive } from 'vue'

/**
 * ゲーム共通の経過時間タイマー（秒）。DB には保存しない。
 * reactive なオブジェクトなので、テンプレートでもスクリプトでも `timer.clock` の形で読める。
 */
export interface GameTimer {
  /** 経過秒数。 */
  seconds: number
  /** 動作中か。 */
  running: boolean
  /** 表示用の mm:ss（60 分以上は h:mm:ss）。 */
  clock: string
  /** 0 から計測開始。 */
  start: () => void
  /** 計測を止める（時間は保持）。 */
  stop: () => void
  /** 0 に戻して停止。 */
  reset: () => void
}

/** 秒を mm:ss（1 時間以上は h:mm:ss）に整形する。 */
export function formatClock(totalSeconds: number): string {
  const safe = Math.max(0, Math.floor(totalSeconds))
  const hours = Math.floor(safe / 3600)
  const minutes = Math.floor((safe % 3600) / 60)
  const seconds = safe % 60
  const pad = (value: number): string => String(value).padStart(2, '0')
  return hours > 0 ? `${hours}:${pad(minutes)}:${pad(seconds)}` : `${pad(minutes)}:${pad(seconds)}`
}

/**
 * 1 秒ごとに進むタイマー。
 * 画面が消えるときに自動で止める（onBeforeUnmount）。
 *
 * 使い方:
 *   const timer = useGameTimer()
 *   timer.start()  // 計測開始
 *   timer.stop()   // 一時停止（時間は保持）
 *   timer.reset()  // 0 に戻して停止
 */
export function useGameTimer(): GameTimer {
  let handle: ReturnType<typeof setInterval> | null = null

  function clear(): void {
    if (handle !== null) {
      clearInterval(handle)
      handle = null
    }
  }

  function start(): void {
    clear()
    state.running = true
    handle = setInterval(() => {
      state.seconds += 1
      state.clock = formatClock(state.seconds)
    }, 1000)
  }

  function stop(): void {
    state.running = false
    clear()
  }

  function reset(): void {
    stop()
    state.seconds = 0
    state.clock = formatClock(0)
  }

  const state = reactive<GameTimer>({
    seconds: 0,
    running: false,
    clock: formatClock(0),
    start,
    stop,
    reset
  })

  onBeforeUnmount(clear)

  return state
}

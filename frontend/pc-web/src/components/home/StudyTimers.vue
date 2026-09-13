<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useTimers } from '@/features/home/useTimers'
import {
  STUDY_DURATION_OPTIONS,
  TIMER_MIN_SECONDS,
  formatRemaining,
  normalizeSeconds,
  type TimerKind
} from '@/features/home/homeTimers'

/**
 * 学習・休憩・ゲームタイマー（2.0 `home.jsp` のタイマーカード）。
 *
 * 2.0 は残り時間と実行状態をサーバー（`COM_タスク時間管理情報`）に保存していたが、
 * 2.1 はブラウザ内（localStorage）＋タブ間同期で持つ（ユーザーの指定）。
 * 学習タイマーだけ時間の設定（5〜60 分＋カスタム）ができる。
 *
 * 2.0 にあった「タスク完了で休憩・ゲームの時間が増える」仕組みは、
 * 学習ステータス（タスク）機能が未移行のため入れていない。
 */
const timers = useTimers()

interface TimerCard {
  kind: TimerKind
  label: string
  icon: string
  tone: string
  text: string
  running: boolean
  total: number
  remaining: number
}

const CARDS: { kind: TimerKind; label: string; icon: string; tone: string }[] = [
  { kind: 'study', label: '学習タイマー', icon: 'clock', tone: 'study' },
  { kind: 'break', label: '休憩タイマー', icon: 'sun', tone: 'break' },
  { kind: 'game', label: 'ゲームタイマー', icon: 'gamepad', tone: 'game' }
]

const cards = computed<TimerCard[]>(() =>
  CARDS.map((card) => {
    const timer = timers.state.value.timers[card.kind]
    return {
      ...card,
      text: formatRemaining(timer.remaining),
      running: timer.running,
      total: timer.total,
      remaining: timer.remaining
    }
  })
)

/** 学習タイマーの時間設定（ドロップダウンで選ぶ）。 */
const studyTotal = computed(() => timers.state.value.timers.study.total)

/** よく使う時間。今の設定が一覧に無ければ「カスタム」を選ぶ。 */
const durationOptions = computed(() => [
  ...STUDY_DURATION_OPTIONS.map((seconds) => ({ value: String(seconds), label: `${seconds / 60}分` })),
  { value: 'custom', label: 'カスタム...' }
])

const customSelected = computed(() => !STUDY_DURATION_OPTIONS.includes(studyTotal.value))

function onDurationChange(event: Event): void {
  const value = (event.target as HTMLSelectElement).value
  if (value === 'custom') return
  timers.setDurationSeconds('study', Number(value))
}

/** カスタムの分（1〜720 分）を確定する。 */
function onCustomMinutes(event: Event): void {
  const minutes = Number((event.target as HTMLInputElement).value)
  if (!Number.isFinite(minutes) || minutes <= 0) return
  timers.setDurationSeconds('study', normalizeSeconds(minutes * 60, studyTotal.value))
}

/** 残りの割合（進捗バー用）。 */
function progress(card: TimerCard): number {
  if (card.total <= 0) return 0
  return Math.max(0, Math.min(100, Math.round((card.remaining / card.total) * 100)))
}
</script>

<template>
  <div class="home-timers">
    <section
      v-for="card in cards" :key="card.kind"
      class="card home-timer" :class="[`home-timer--${card.tone}`, { 'is-running': card.running }]"
      :data-timer="card.kind"
    >
      <div class="card__body">
        <div class="home-timer__head">
          <span class="home-timer__icon"><AppIcon :name="card.icon" size="sm" /></span>
          <span class="home-timer__label">{{ card.label }}</span>
          <span v-if="card.running" class="badge badge--success">実行中</span>
        </div>

        <p class="home-timer__clock" :data-timer-clock="card.kind">{{ card.text }}</p>
        <div class="home-timer__bar" role="presentation">
          <span class="home-timer__bar-fill" :style="{ width: `${progress(card)}%` }" />
        </div>

        <div class="home-timer__actions">
          <button
            type="button" class="btn btn--primary btn--sm" :data-timer-start="card.kind"
            :disabled="card.running || card.remaining <= 0" @click="timers.start(card.kind)"
          >
            <AppIcon name="play" size="sm" /> 開始
          </button>
          <button
            type="button" class="btn btn--secondary btn--sm" :data-timer-stop="card.kind"
            :disabled="!card.running" @click="timers.stop(card.kind)"
          >
            <AppIcon name="minus" size="sm" /> 停止
          </button>
        </div>

        <!-- 時間の設定は学習タイマーだけ（2.0 と同じ） -->
        <div v-if="card.kind === 'study'" class="home-timer__setting">
          <label class="home-timer__setting-label" for="homeStudyDuration">時間設定</label>
          <select
            id="homeStudyDuration" class="select" data-study-duration
            :value="customSelected ? 'custom' : String(studyTotal)"
            @change="onDurationChange"
          >
            <option v-for="option in durationOptions" :key="option.value" :value="option.value">
              {{ option.label }}
            </option>
          </select>
          <span v-if="customSelected" class="home-timer__custom">
            <input
              class="input" type="number" :min="TIMER_MIN_SECONDS / 60" max="720" step="1"
              :value="Math.round(studyTotal / 60)" data-study-duration-custom aria-label="学習タイマーの分数"
              @change="onCustomMinutes"
            />
            <span class="cell-muted">分</span>
          </span>
        </div>
      </div>
    </section>
  </div>
</template>

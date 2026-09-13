<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import { WORD_TEST_MINIMUM_MINUTES, buildWordTestDays, type WordTestDay } from '@/features/home/homeMockData'
import '@/features/home/home.css'

/**
 * 直近単語テスト情報統計（2.0 `english.jsp` の「直近単語テスト情報統計」）。
 *
 * 2.0 は Chart.js の複合グラフ（棒＝単語数 / 破線＝テスト時間(分) / バブル＝中断回数 /
 * 赤い破線＝最低時間 25 分。平日と週末で棒の色を変える）で 30 日ぶんを描いていた。
 * 2.1 は Chart.js を使わず SVG で描く。単語テストのテーブルはまだ無いため、
 * いまは `homeMockData.ts` の仮データ（同じ形）を表示する。
 */
const days = buildWordTestDays()

/** グラフの内側の余白（左は単語数の目盛り、右は時間(分)の目盛り）。 */
const CHART_WIDTH = 900
const CHART_HEIGHT = 240
const PADDING = { top: 12, right: 44, bottom: 26, left: 44 }

const plotWidth = CHART_WIDTH - PADDING.left - PADDING.right
const plotHeight = CHART_HEIGHT - PADDING.top - PADDING.bottom

/** 単語数（左軸）の上限。10 語刻みで切り上げる。 */
const wordMax = computed(() => {
  const max = Math.max(...days.map((day) => day.wordCount), 0)
  return Math.max(100, Math.ceil(max / 100) * 100)
})

/** 時間（右軸）の上限。最低時間ラインより必ず大きくする。 */
const minuteMax = computed(() => {
  const max = Math.max(...days.map((day) => day.minutes), WORD_TEST_MINIMUM_MINUTES)
  return Math.ceil((max + 5) / 10) * 10
})

function x(index: number): number {
  const step = plotWidth / Math.max(1, days.length)
  return PADDING.left + step * index + step / 2
}

function barWidth(): number {
  return Math.max(2, (plotWidth / Math.max(1, days.length)) * 0.62)
}

function barY(day: WordTestDay): number {
  return PADDING.top + plotHeight - (day.wordCount / wordMax.value) * plotHeight
}

function barHeight(day: WordTestDay): number {
  return Math.max(0, PADDING.top + plotHeight - barY(day))
}

function minuteY(minutes: number): number {
  return PADDING.top + plotHeight - (minutes / minuteMax.value) * plotHeight
}

/** 時間(分)の折れ線（点をつないだパス）。 */
const minutePath = computed(() =>
  days
    .map((day, index) => `${index === 0 ? 'M' : 'L'}${x(index).toFixed(1)},${minuteY(day.minutes).toFixed(1)}`)
    .join(' ')
)

/** バブルの半径（中断回数）。0 の日は出さない。 */
function bubbleRadius(day: WordTestDay): number {
  return day.interruptions <= 0 ? 0 : 2.2 + day.interruptions * 2.2
}

/** 左軸（単語数）の目盛り。 */
const wordTicks = computed(() => [0, 0.5, 1].map((ratio) => ({
  value: Math.round(wordMax.value * ratio),
  y: PADDING.top + plotHeight - plotHeight * ratio
})))

/** 右軸（時間(分)）の目盛り。 */
const minuteTicks = computed(() => [0, 0.5, 1].map((ratio) => ({
  value: Math.round(minuteMax.value * ratio),
  y: PADDING.top + plotHeight - plotHeight * ratio
})))

/** X 軸のラベル（1 日おきに出して詰まらないようにする）。 */
const xTicks = computed(() => days.filter((_, index) => index % 2 === 0))

const totals = computed(() => ({
  words: days.reduce((sum, day) => sum + day.wordCount, 0),
  minutes: days.reduce((sum, day) => sum + day.minutes, 0),
  interruptions: days.reduce((sum, day) => sum + day.interruptions, 0),
  testedDays: days.filter((day) => day.wordCount > 0).length
}))

/** 最低時間（25 分）を超えた日／超えなかった日。 */
const aboveMinimum = computed(() => days.filter((day) => day.minutes >= WORD_TEST_MINIMUM_MINUTES).length)
</script>

<template>
  <section class="card home-word-test" data-word-test>
    <div class="card__body">
      <div class="table-section__head">
        <h3 class="table-section__title"><AppIcon name="clipboard" size="sm" /> 直近単語テスト情報統計</h3>
        <span class="table-section__meta">過去 1 ヶ月</span>
      </div>

      <div class="home-word-test__legend">
        <span class="home-word-test__legend-item"><span class="home-word-test__swatch home-word-test__swatch--weekday" />単語数（平日）</span>
        <span class="home-word-test__legend-item"><span class="home-word-test__swatch home-word-test__swatch--weekend" />単語数（週末）</span>
        <span class="home-word-test__legend-item"><span class="home-word-test__swatch home-word-test__swatch--line" />時間（分）</span>
        <span class="home-word-test__legend-item"><span class="home-word-test__swatch home-word-test__swatch--bubble" />中断回数</span>
        <span class="home-word-test__legend-item"><span class="home-word-test__swatch home-word-test__swatch--minimum" />最低時間 {{ WORD_TEST_MINIMUM_MINUTES }} 分</span>
      </div>

      <div class="home-word-test__chart">
        <svg
          :viewBox="`0 0 ${CHART_WIDTH} ${CHART_HEIGHT}`" role="img"
          aria-label="直近1ヶ月の単語テスト（単語数・時間・中断回数）" data-word-test-chart
        >
          <!-- 目盛り線 -->
          <g class="home-word-test__grid">
            <line
              v-for="tick in wordTicks" :key="`grid-${tick.value}`"
              :x1="PADDING.left" :x2="CHART_WIDTH - PADDING.right" :y1="tick.y" :y2="tick.y"
            />
          </g>

          <!-- 最低時間（赤い破線） -->
          <line
            class="home-word-test__minimum"
            :x1="PADDING.left" :x2="CHART_WIDTH - PADDING.right"
            :y1="minuteY(WORD_TEST_MINIMUM_MINUTES)" :y2="minuteY(WORD_TEST_MINIMUM_MINUTES)"
            data-word-test-minimum
          />

          <!-- 単語数（棒。平日と週末で色を変える） -->
          <rect
            v-for="(day, index) in days" :key="`bar-${day.date}`"
            class="home-word-test__bar" :class="{ 'is-weekend': day.weekend }"
            :x="x(index) - barWidth() / 2" :y="barY(day)" :width="barWidth()" :height="barHeight(day)"
            :data-word-test-bar="day.date"
          >
            <title>{{ day.label }}（{{ day.weekday }}）単語数 {{ day.wordCount }} 語／時間 {{ day.minutes }} 分／中断 {{ day.interruptions }} 回</title>
          </rect>

          <!-- 時間（分）の折れ線 -->
          <path class="home-word-test__line" :d="minutePath" data-word-test-line />
          <circle
            v-for="(day, index) in days" :key="`point-${day.date}`"
            class="home-word-test__point" :cx="x(index)" :cy="minuteY(day.minutes)" r="2"
          />

          <!-- 中断回数（バブル） -->
          <circle
            v-for="(day, index) in days" v-show="bubbleRadius(day) > 0"
            :key="`bubble-${day.date}`"
            class="home-word-test__bubble" :cx="x(index)" :cy="minuteY(day.minutes)" :r="bubbleRadius(day)"
            :data-word-test-bubble="day.date"
          />

          <!-- 軸の目盛り -->
          <g class="home-word-test__axis">
            <text v-for="tick in wordTicks" :key="`wl-${tick.value}`" :x="PADDING.left - 6" :y="tick.y + 3" text-anchor="end">{{ tick.value }}</text>
            <text v-for="tick in minuteTicks" :key="`ml-${tick.value}`" :x="CHART_WIDTH - PADDING.right + 6" :y="tick.y + 3" text-anchor="start">{{ tick.value }}</text>
            <text
              v-for="day in xTicks" :key="`xl-${day.date}`"
              :x="x(days.indexOf(day))" :y="CHART_HEIGHT - 8" text-anchor="middle"
              :class="{ 'is-weekend': day.weekend }"
            >{{ day.label }}</text>
          </g>
        </svg>
      </div>

      <div class="home-word-test__summary">
        <span>計測 <strong>{{ totals.testedDays }}</strong> 日</span>
        <span>単語 <strong>{{ totals.words.toLocaleString() }}</strong> 語</span>
        <span>時間 <strong>{{ totals.minutes.toLocaleString() }}</strong> 分</span>
        <span>中断 <strong>{{ totals.interruptions }}</strong> 回</span>
        <span>最低時間（{{ WORD_TEST_MINIMUM_MINUTES }} 分）以上 <strong>{{ aboveMinimum }}</strong> 日</span>
      </div>

      <p class="home-note">
        いまはサンプルデータを表示しています（単語テストのデータはこれから移行します）。
      </p>
    </div>
  </section>
</template>

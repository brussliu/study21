<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  ANALYSIS_RESULTS,
  buildMockDay,
  shortTime,
  timeToSeconds,
  type AnalysisResult,
  type MonitorSnapshot
} from '@/features/study-monitor/studyMonitorMock'
import '@/features/home/home.css'

/**
 * 行動タイムライン（2.0 `home.jsp` の `home-monitor-card`）。
 *
 * 2.0 は `api/study-monitor/search`（viewMode=snapshot）が返す
 * **スナップショットの AI 分析結果**を時間軸に並べていた
 * （`js/home_monitor_timeline.js` の buildSegments / renderTimeline と同じ考え方）。
 *
 * 2.1 はその API がまだ無い（録画取込 batL02・AI 分析 batL03 が未実装）ため、
 * 学習状況モニター画面と同じ**サンプルデータ**で 2.0 と同じ見た目を再現する。
 * API ができたら `studyMonitorMock` の呼び出しを API に差し替える。
 *
 * ・対象日と時間帯で絞り込む（2.0 の既定は 08:00〜23:59）
 * ・同じ分析結果が続く区間はまとめる（2.0 と同じく 90 秒以内の間隔）
 * ・下部に「n 枚 / n 区間｜学習時間 h:mm（xx.x%）」を出す
 */

const todayIso = (): string => {
  const now = new Date()
  return `${now.getFullYear()}-${`${now.getMonth() + 1}`.padStart(2, '0')}-${`${now.getDate()}`.padStart(2, '0')}`
}

const date = ref(todayIso())
const timeFrom = ref('08:00')
const timeTo = ref('23:59')

/** 分析結果 → タイムラインの色（2.0 の slug に合わせる）。 */
const RESULT_SLUGS: Record<AnalysisResult, string> = {
  学習中: 'study',
  休憩中: 'break',
  ゲーム中: 'game',
  離席: 'away',
  判定不能: 'unknown'
}

const RESULT_LABELS: Record<string, string> = {
  study: '学習中',
  break: '休憩中',
  game: 'ゲーム中',
  away: '離席',
  unknown: '判定不能',
  unanalysed: '未分析'
}

/** その日のスナップショット（学習状況モニターの仮データから全動画ぶんを集める）。 */
const snapshots = computed<MonitorSnapshot[]>(() =>
  buildMockDay(date.value).videos.flatMap((video) => video.snapshots)
)

interface Segment {
  start: number
  end: number
  slug: string
  label: string
}

/** 1 枚のスナップショットの状態（2.0 の resultOf と同じ）。 */
function resultOf(snapshot: MonitorSnapshot): { slug: string; label: string } {
  if (snapshot.analysisState !== '分析済み' || snapshot.analysisResult === null) {
    return { slug: 'unanalysed', label: RESULT_LABELS.unanalysed }
  }
  const slug = RESULT_SLUGS[snapshot.analysisResult] ?? 'unknown'
  return { slug, label: RESULT_LABELS[slug] ?? snapshot.analysisResult }
}

const range = computed(() => ({
  start: timeToSeconds(`${timeFrom.value}:00`),
  end: timeToSeconds(`${timeTo.value}:00`)
}))

const rangeInvalid = computed(() => range.value.end <= range.value.start)

/** 分析結果ごとの区間（同じ結果が続くところはまとめる）。 */
const segments = computed<Segment[]>(() => {
  if (rangeInvalid.value) return []
  const { start: rangeStart, end: rangeEnd } = range.value
  const items = [...snapshots.value].sort(
    (left, right) => timeToSeconds(left.capturedTime) - timeToSeconds(right.capturedTime)
  )
  const built: Segment[] = []
  items.forEach((snapshot, index) => {
    const start = Math.min(rangeEnd, Math.max(rangeStart, timeToSeconds(snapshot.capturedTime)))
    const nextTime = index + 1 < items.length ? timeToSeconds(items[index + 1].capturedTime) : start + 60
    // 1 枚が担当する長さは最大 90 秒（2.0 と同じ）
    const end = Math.min(rangeEnd, Math.min(nextTime, start + 90))
    if (end <= start) return
    const result = resultOf(snapshot)
    const previous = built[built.length - 1]
    if (previous !== undefined && previous.slug === result.slug && start - previous.end <= 90) {
      previous.end = end
      return
    }
    built.push({ start, end, slug: result.slug, label: result.label })
  })
  return built
})

/** 使われた分析結果（凡例に出す）。 */
const usedResults = computed(() => {
  const used: { slug: string; label: string }[] = []
  for (const segment of segments.value) {
    if (!used.some((item) => item.slug === segment.slug)) {
      used.push({ slug: segment.slug, label: segment.label })
    }
  }
  return used
})

const studySeconds = computed(() =>
  segments.value.reduce((total, segment) => total + (segment.slug === 'study' ? segment.end - segment.start : 0), 0)
)

const durationSeconds = computed(() => Math.max(0, range.value.end - range.value.start))

const studyPercent = computed(() =>
  durationSeconds.value > 0 ? (studySeconds.value * 100) / durationSeconds.value : 0
)

/** 「学習時間 1時間20分」の形（2.0 の h:mm 表示に合わせる）。 */
const studyTimeText = computed(() => {
  const minutes = Math.floor(studySeconds.value / 60)
  const hours = Math.floor(minutes / 60)
  const rest = minutes % 60
  return `${String(hours).padStart(2, '0')}:${String(rest).padStart(2, '0')}`
})

function segmentStyle(segment: Segment): Record<string, string> {
  const duration = durationSeconds.value
  if (duration <= 0) return { left: '0%', width: '0%' }
  const left = ((segment.start - range.value.start) * 100) / duration
  const width = ((segment.end - segment.start) * 100) / duration
  return { left: `${left.toFixed(3)}%`, width: `${Math.max(width, 0.35).toFixed(3)}%` }
}

function segmentTitle(segment: Segment): string {
  const seconds = segment.end - segment.start
  const minutes = Math.floor(seconds / 60)
  const rest = Math.round(seconds % 60)
  const length = minutes > 0 ? `${minutes}分${rest > 0 ? `${rest}秒` : ''}` : `${Math.max(1, rest)}秒`
  return `${segment.label}｜${shortTime(segment.start)} - ${shortTime(segment.end)}｜${length}`
}

/** 目盛り（時間帯の広さに応じて 30 分 / 1 時間 / 2 時間）。 */
const ticks = computed(() => {
  const { start, end } = range.value
  const duration = end - start
  if (duration <= 0) return []
  const hours = duration / 3600
  const interval = hours > 10 ? 2 * 3600 : hours > 5 ? 3600 : 30 * 60
  const result: { seconds: number; left: number; edge: string }[] = []
  const first = Math.ceil(start / interval) * interval
  for (let seconds = first; seconds <= end; seconds += interval) {
    const left = ((seconds - start) * 100) / duration
    result.push({ seconds, left, edge: left < 0.001 ? 'start' : left > 99.999 ? 'end' : '' })
  }
  return result
})

/** 表示する分析結果の種類（凡例の説明に使う）。 */
const resultLegend = ANALYSIS_RESULTS.map((result) => ({
  slug: RESULT_SLUGS[result],
  label: result
}))
</script>

<template>
  <section class="card home-timeline" data-action-timeline>
    <div class="card__body">
      <div class="table-section__head home-timeline__head">
        <div>
          <h3 class="table-section__title"><AppIcon name="list" size="sm" /> 行動タイムライン</h3>
          <p class="home-timeline__lead">学習状況モニターの分析結果を時間軸で表示します。</p>
        </div>
        <div class="home-timeline__filters">
          <label class="filter-item">
            <span class="filter-item__label">対象日</span>
            <input v-model="date" class="input" type="date" data-timeline-date />
          </label>
          <span class="filter-item home-timeline__time">
            <span class="filter-item__label">時間帯</span>
            <input v-model="timeFrom" class="input" type="time" aria-label="開始時刻" data-timeline-from />
            <em>～</em>
            <input v-model="timeTo" class="input" type="time" aria-label="終了時刻" data-timeline-to />
          </span>
        </div>
      </div>

      <p v-if="rangeInvalid" class="batch-page__empty">終了時刻は開始時刻より後に設定してください。</p>

      <template v-else>
        <div class="home-timeline__track" data-timeline-track>
          <span
            v-for="(segment, index) in segments" :key="`${segment.slug}-${index}`"
            class="home-timeline__segment" :class="`home-timeline__segment--${segment.slug}`"
            :style="segmentStyle(segment)" :title="segmentTitle(segment)" :aria-label="segmentTitle(segment)"
            :data-timeline-segment="segment.slug"
          />
          <p v-if="segments.length === 0" class="home-timeline__empty">該当する分析結果はありません。</p>
        </div>

        <div class="home-timeline__scale" data-timeline-scale>
          <span
            v-for="tick in ticks" :key="tick.seconds"
            class="home-timeline__tick" :class="{ 'is-edge-start': tick.edge === 'start', 'is-edge-end': tick.edge === 'end' }"
            :style="{ left: `${tick.left.toFixed(3)}%` }"
          >{{ shortTime(tick.seconds) }}</span>
        </div>

        <div class="home-timeline__footer">
          <div class="home-timeline__legend" data-timeline-legend>
            <span v-for="item in usedResults" :key="item.slug" class="home-timeline__legend-item">
              <span class="home-timeline__dot" :class="`home-timeline__segment--${item.slug}`" />{{ item.label }}
            </span>
          </div>
          <span class="home-timeline__status" data-timeline-status>
            {{ snapshots.length }} 枚 / {{ segments.length }} 区間｜学習時間 {{ studyTimeText }}（{{ studyPercent.toFixed(1) }}%）
          </span>
        </div>
      </template>

      <p class="home-timeline__note">
        動画の取込（batL02）と AI 分析（batL03）は 2.1 では未実装のため、いまはサンプルデータです。
        <span class="home-timeline__note-legend">
          <template v-for="item in resultLegend" :key="item.slug">
            <span class="home-timeline__dot" :class="`home-timeline__segment--${item.slug}`" />{{ item.label }}
          </template>
        </span>
      </p>
    </div>
  </section>
</template>

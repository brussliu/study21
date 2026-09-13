<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiError } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { fetchHourlyUsageSummary, type HourlyUsageSummary } from '@/api/netAccessLogs'
import { searchTerminals } from '@/api/net'
import { KIND_LABELS, optionsOf } from '@/features/net/netLabels'
import '@/features/home/home.css'

/**
 * 最近3日 上網状況（時間帯別）＝ 2.0 `home.jsp` の `net-usage-card`。
 *
 * 2.0 は `api/proxy/getRecentUsageSummary`（`NET_プロキシ通信履歴情報` を時間帯別に集計）を
 * canvas で描いていた。2.1 は同じ集計を
 * `GET /api/user/net-access-logs/hourly-summary` で取り、SVG で描く（Chart.js は使わない）。
 *
 * ・区分（サイトの 0.勉強 / 1.通常 / 2.休憩 / 3.ゲーム）と端末で絞り込める
 * ・今日 / 昨日 / 一昨日 の 3 日 × 24 時間を、時間帯ごとにまとめて並べる
 */
const loading = ref(false)
const error = ref('')
const summary = ref<HourlyUsageSummary | null>(null)

const kind = ref('')
const terminalName = ref('')
const terminalOptions = ref<string[]>([])

const kindOptions = optionsOf(KIND_LABELS)

/** 3 日ぶんのラベル（今日・昨日・一昨日）と色。 */
const DAY_LABELS = ['今日', '昨日', '一昨日']

const hours = Array.from({ length: 24 }, (_, hour) => hour)

/** 目盛りの上限（0 のときは 1 にして 0 除算を避ける）。 */
const maxCount = computed(() => {
  const buckets = summary.value?.buckets ?? []
  let max = 0
  for (const day of buckets) {
    for (const count of day) {
      if (count > max) max = count
    }
  }
  return max === 0 ? 1 : max
})

const dayTotals = computed(() =>
  (summary.value?.buckets ?? []).map((day) => day.reduce((sum, count) => sum + count, 0))
)

/** 1 本の棒の高さ（%）。 */
function barHeight(count: number): number {
  if (count <= 0) return 0
  // 最小 2% は出して、1 件でも「あった」ことが分かるようにする
  return Math.max(2, Math.round((count / maxCount.value) * 100))
}

function barTitle(hour: number, dayIndex: number, count: number): string {
  const day = summary.value?.days[dayIndex] ?? ''
  return `${day} ${String(hour).padStart(2, '0')}:00〜${String(hour).padStart(2, '0')}:59 ／ ${count.toLocaleString()} 件`
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await fetchHourlyUsageSummary({
      terminalName: terminalName.value,
      kind: kind.value
    })
    summary.value = response.data
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '上網状況を取得できませんでした。'
  } finally {
    loading.value = false
  }
}

/** 端末の選択肢は登録済みの端末から作る（2.0 の listTerminalNames と同じ考え方）。 */
async function loadTerminalNames(): Promise<void> {
  try {
    const response = await searchTerminals({ size: 200 })
    const names = response.data.items.map((row) => row.terminalName.trim()).filter((name) => name !== '')
    terminalOptions.value = [...new Set(names)].sort((left, right) => left.localeCompare(right, 'ja'))
  } catch {
    terminalOptions.value = []
  }
}

watch([kind, terminalName], () => {
  void load()
})

onMounted(() => {
  void load()
  void loadTerminalNames()
})
</script>

<template>
  <section class="card home-net-usage">
    <div class="card__body">
      <div class="table-section__head home-net-usage__head">
        <h3 class="table-section__title">
          <AppIcon name="monitor" size="sm" /> 最近3日 上網状況（時間帯別）
        </h3>
        <div class="home-net-usage__filters">
          <label class="filter-item">
            <span class="filter-item__label">区分</span>
            <select v-model="kind" class="select" data-net-usage-kind>
              <option value="">すべて</option>
              <option v-for="option in kindOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </label>
          <label class="filter-item">
            <span class="filter-item__label">端末</span>
            <select v-model="terminalName" class="select" data-net-usage-terminal>
              <option value="">すべて</option>
              <option v-for="name in terminalOptions" :key="name" :value="name">{{ name }}</option>
            </select>
          </label>
          <span class="table-section__meta" data-net-usage-meta>
            全 {{ (summary?.totalCount ?? 0).toLocaleString() }} 件
          </span>
        </div>
      </div>

      <div class="home-net-usage__legend">
        <span
          v-for="(label, index) in DAY_LABELS" :key="label"
          class="home-net-usage__legend-item"
        >
          <span class="home-net-usage__dot" :class="`home-net-usage__dot--${index}`" />
          {{ label }}<span v-if="dayTotals[index] !== undefined" class="cell-muted">
            （{{ (dayTotals[index] ?? 0).toLocaleString() }} 件）</span>
        </span>
      </div>

      <p v-if="error" class="alert alert--danger">{{ error }}</p>
      <p v-else-if="loading" class="net-page__loading">読み込んでいます...</p>
      <p v-else-if="(summary?.totalCount ?? 0) === 0" class="batch-page__empty">
        この条件の上網状況はありません。
      </p>

      <div v-else class="home-net-usage__chart" data-net-usage-chart>
        <div v-for="hour in hours" :key="hour" class="home-net-usage__column">
          <div class="home-net-usage__bars">
            <span
              v-for="(label, dayIndex) in DAY_LABELS" :key="label"
              class="home-net-usage__bar" :class="`home-net-usage__bar--${dayIndex}`"
              :style="{ height: `${barHeight(summary?.buckets[dayIndex]?.[hour] ?? 0)}%` }"
              :title="barTitle(hour, dayIndex, summary?.buckets[dayIndex]?.[hour] ?? 0)"
              :data-net-usage-bar="`${dayIndex}-${hour}`"
            />
          </div>
          <span class="home-net-usage__hour">{{ hour }}</span>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import { computed } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  BEGINNER_RADAR,
  HOME_KPIS,
  HOME_LISTS,
  INTERMEDIATE_RADAR,
  type RadarAxis
} from '@/features/home/homeMockData'
import '@/features/home/home.css'

/**
 * ホームの学習サマリ（2.0 の english.jsp が出していた内容）。
 *
 *   ・KPI（総学習時間 / 学習レベル / 今日の学習時間 / 今日の読書時間 / 連続学習日数）
 *   ・レーダーチャート（初級・中級。現在値と目標値）
 *   ・単語帳・熟語帳・テスト結果・単語の学習状況の一覧
 *
 * 生徒のホーム（`views/home/HomeView.vue`）と保護者のホーム
 * （`views/parent/ParentHomeView.vue`）の両方で使う（見た目を揃えるため 1 か所に置く）。
 * いまは実データの API が無いため `features/home/homeMockData.ts` の仮データを表示する。
 */
const props = withDefaults(defineProps<{
  /** 誰の学習状況かを示す名前（保護者ホームで「お子さま」を明示するため）。 */
  learner?: string
}>(), { learner: '' })

/** 「〇〇 さんの学習状況です。」の前置き（名前が無ければ付けない）。 */
const noteLead = computed(() => (props.learner ? `${props.learner} さんの学習状況です。` : ''))

/** レーダーチャートを SVG で描く（Chart.js は使わない）。 */
const RADAR_SIZE = 200
const RADAR_CENTER = RADAR_SIZE / 2
const RADAR_RADIUS = 72

function pointAt(index: number, total: number, ratio: number): { x: number; y: number } {
  const angle = (Math.PI * 2 * index) / total - Math.PI / 2
  return {
    x: RADAR_CENTER + Math.cos(angle) * RADAR_RADIUS * ratio,
    y: RADAR_CENTER + Math.sin(angle) * RADAR_RADIUS * ratio
  }
}

function polygon(axes: RadarAxis[], pick: (axis: RadarAxis) => number): string {
  return axes
    .map((axis, index) => {
      const { x, y } = pointAt(index, axes.length, pick(axis) / 100)
      return `${x.toFixed(1)},${y.toFixed(1)}`
    })
    .join(' ')
}

function axisPoint(index: number, axes: RadarAxis[]): { x: number; y: number } {
  return pointAt(index, axes.length, 1)
}

function ringPolygon(axes: RadarAxis[], ratio: number): string {
  return axes.map((_, index) => {
    const { x, y } = pointAt(index, axes.length, ratio)
    return `${x.toFixed(1)},${y.toFixed(1)}`
  }).join(' ')
}

function labelPoint(index: number, axes: RadarAxis[]): { x: number; y: number } {
  const { x, y } = pointAt(index, axes.length, 1.16)
  return { x, y }
}
</script>

<template>
  <div class="home-summary">
    <!-- KPI（2.0 の english.jsp の上部カード） -->
    <div class="home-kpis">
      <div v-for="kpi in HOME_KPIS" :key="kpi.id" class="home-kpi" :class="`home-kpi--${kpi.tone}`" :data-kpi="kpi.id">
        <span class="home-kpi__label">
          <AppIcon :name="kpi.icon" size="sm" /> {{ kpi.label }}
        </span>
        <span class="home-kpi__value">{{ kpi.value }}</span>
        <span class="home-kpi__sub">{{ kpi.sub }}</span>
      </div>
    </div>

    <div class="home-grid">
      <!-- レーダーチャート（初級・中級） -->
      <section class="card">
        <div class="card__body">
          <div class="table-section__head">
            <h3 class="table-section__title">英語の学習バランス</h3>
            <span class="home-legend">
              <span><span class="home-legend__swatch home-legend__swatch--value" />現在</span>
              <span><span class="home-legend__swatch home-legend__swatch--target" />目標</span>
            </span>
          </div>

          <div class="home-radars">
            <div v-for="radar in [
              { key: 'beginner', title: '初級編', axes: BEGINNER_RADAR },
              { key: 'intermediate', title: '中級編', axes: INTERMEDIATE_RADAR }
            ]" :key="radar.key" class="home-radar" :data-radar="radar.key">
              <svg :width="RADAR_SIZE" :height="RADAR_SIZE" :viewBox="`0 0 ${RADAR_SIZE} ${RADAR_SIZE}`" role="img" :aria-label="`${radar.title}の学習バランス`">
                <!-- 目盛りの枠 -->
                <polygon
                  v-for="ratio in [0.25, 0.5, 0.75, 1]"
                  :key="ratio"
                  :points="ringPolygon(radar.axes, ratio)"
                  fill="none"
                  stroke="var(--color-border)"
                  stroke-width="1"
                />
                <!-- 軸 -->
                <line
                  v-for="(axis, index) in radar.axes"
                  :key="axis.axis"
                  :x1="RADAR_CENTER"
                  :y1="RADAR_CENTER"
                  :x2="axisPoint(index, radar.axes).x"
                  :y2="axisPoint(index, radar.axes).y"
                  stroke="var(--color-border)"
                  stroke-width="1"
                />
                <!-- 目標値 -->
                <polygon
                  :points="polygon(radar.axes, (axis) => axis.target)"
                  fill="none"
                  stroke="var(--color-text-subtle)"
                  stroke-width="1.5"
                  stroke-dasharray="4 3"
                />
                <!-- 現在値 -->
                <polygon
                  :points="polygon(radar.axes, (axis) => axis.value)"
                  fill="var(--color-primary)"
                  fill-opacity="0.25"
                  stroke="var(--color-primary)"
                  stroke-width="2"
                />
                <!-- 軸ラベル -->
                <text
                  v-for="(axis, index) in radar.axes"
                  :key="`label-${axis.axis}`"
                  :x="labelPoint(index, radar.axes).x"
                  :y="labelPoint(index, radar.axes).y"
                  text-anchor="middle"
                  dominant-baseline="middle"
                  font-size="10"
                  fill="var(--color-text-muted)"
                >{{ axis.axis }}</text>
              </svg>
              <span class="home-radar__caption">{{ radar.title }}</span>
            </div>
          </div>
        </div>
      </section>

      <!-- 学習状況の一覧（2.0 の english.jsp の下部リスト） -->
      <section class="card">
        <div class="card__body">
          <div class="table-section__head">
            <h3 class="table-section__title">学習の状況</h3>
            <span class="table-section__meta">単語・熟語・テスト</span>
          </div>

          <div class="home-list">
            <div v-for="item in HOME_LISTS" :key="item.id" class="home-item" :data-home-item="item.id">
              <span class="home-item__head">
                <AppIcon :name="item.icon" size="sm" /> {{ item.title }}
              </span>
              <span class="home-item__main">{{ item.main }}</span>
              <span class="home-item__sub">{{ item.sub }}</span>
              <span v-if="item.progress !== null" class="home-bar">
                <span class="home-bar__fill" :style="{ width: `${item.progress}%` }" />
              </span>
            </div>
          </div>

          <p class="home-note">
            {{ noteLead }}いまはサンプルデータを表示しています（実データの API はこれから接続します）。
          </p>
        </div>
      </section>
    </div>
  </div>
</template>

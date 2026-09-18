<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, formatIsoDateTime } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  LEARN_STATE_BADGES,
  LEARN_STATE_LABELS,
  LEARN_STATE_OPTIONS,
  TEST_TYPE_LABELS,
  TEST_TYPE_OPTIONS,
  fetchJpnStatus,
  searchJpnSkills,
  type JpnDailyRow,
  type JpnSkillRow,
  type JpnStatusRow,
  type JpnStatusSummary,
  type LearnState,
  type TestType
} from '@/api/japanese'
import { paginationItems } from '@/features/pagination/pagination'
import '@/features/japanese/japanese.css'

/**
 * 日本語勉強【単語勉強状況】。
 *
 * 2.0 の `japanese_word_status.jsp`（＋`js/japanese_word_status.js`）を
 * 2.1 のデザインで作り直した画面。データは `JPN_*` テーブル（学習状況はアカウントごと）を
 * `fetchJpnStatus` / `searchJpnSkills` で読む。
 *
 * ・サマリ（学習した単語・習得済・お気に入り・平均習得度・回答数・正答率・学習時間）
 * ・日次の学習量（テスト種別 A〜E の積み上げ棒グラフ。古い順に並べる）
 * ・語別の学習状況（学習状態・JLPT で絞り込み＋ページング）
 * ・技能別の習得（テスト種別・技能区分で絞り込み。タブを開いてから読む）
 */

/** 一覧の 1 ページの件数（画面共通の選択肢）。 */
const SIZE_OPTIONS = [20, 50, 100]
/** 一番長い日の積み上げをこの高さ（px）に合わせて比例配分する。 */
const DAILY_BAR_MAX_HEIGHT = 90
/** JLPT のレベル（`jlptLevel` に入る値）。 */
const JLPT_OPTIONS = ['N1', 'N2', 'N3', 'N4', 'N5']

/** まだ読み込めていないときのサマリ（0 件として描画する）。 */
const EMPTY_SUMMARY: JpnStatusSummary = {
  studiedWordCount: 0,
  learnedCount: 0,
  favoriteCount: 0,
  averageMastery: 0,
  answeredCount: 0,
  correctCount: 0,
  accuracyPercent: 0,
  activeMs: 0,
  todayActiveMs: 0,
  lastStudiedAt: null
}

/** API は種別を文字列で返すので、未知の値はそのまま表示する。 */
const TEST_TYPE_LABEL_BY_CODE: Readonly<Record<string, string | undefined>> = TEST_TYPE_LABELS

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/** 学習時間を「N 時間 M 分」で表す（60 分未満は「M 分」、0 は「0 分」）。 */
function formatDuration(ms: number): string {
  const minutes = toMinutes(ms)
  if (minutes === 0) return '0 分'
  const hours = Math.floor(minutes / 60)
  return hours > 0 ? `${hours} 時間 ${minutes % 60} 分` : `${minutes} 分`
}

/** ミリ秒を分に直す。 */
function toMinutes(ms: number): number {
  return Math.max(0, Math.round(ms / 60000))
}

function dateTimeLabel(iso: string | null): string {
  return iso === null || iso === '' ? '—' : formatIsoDateTime(iso)
}

/** 回答・正解・不正解の 1 セル分。 */
function answeredLabel(row: JpnStatusRow | JpnSkillRow): string {
  return `${row.answeredCount} 回 / ${row.correctCount} 正解 / ${row.wrongCount} 不正解`
}

/** 連続正解（現在／最大）の 1 セル分。 */
function streakLabel(row: JpnStatusRow | JpnSkillRow): string {
  return `${row.streak} 回 / 最大 ${row.bestStreak} 回`
}

function testTypeLabel(code: string): string {
  return TEST_TYPE_LABEL_BY_CODE[code] ?? code
}

/** 凡例は「A 読み確認」の形（`TEST_TYPE_LABELS` の「A：読み確認」から説明だけ使う）。 */
function legendLabel(type: TestType): string {
  const parts = TEST_TYPE_LABELS[type].split('：')
  return `${type} ${parts.length > 1 ? parts[1] : TEST_TYPE_LABELS[type]}`
}

/** A は基準クラス、B〜E は色の修飾クラスを足す。 */
function legendDotClass(type: TestType): string {
  return type === 'A' ? '' : `jp-legend__dot--${type.toLowerCase()}`
}

function segmentClass(type: TestType): string {
  return type === 'A' ? '' : `jp-daily__segment--${type.toLowerCase()}`
}

interface DailySegment {
  type: TestType
  /** 色の修飾クラス（A は空）。 */
  modifier: string
  /** 積み上げの高さ（px）。 */
  height: number
  minutes: number
  label: string
}

interface DailyBar {
  studyDate: string
  dateLabel: string
  minutes: number
  segments: DailySegment[]
}

/**
 * その日の学習時間（テスト種別 A〜E の積み上げの合計）。
 * 棒の高さと分の表示を必ず一致させたいので、日次の合計は内訳から求める
 * （A〜E 以外の学習が無い前提。`activeMs` は内訳の合計と同じ値になる）。
 */
function dailyTotalMs(row: JpnDailyRow): number {
  return row.typeAMs + row.typeBMs + row.typeCMs + row.typeDMs + row.typeEMs
}

/**
 * 種別ごとの積み上げの高さ（px）。
 * 一番長い日が DAILY_BAR_MAX_HEIGHT になるように比例配分し、端数は上の段で吸収する。
 */
function segmentHeights(parts: number[], totalMs: number, maxMs: number): number[] {
  if (totalMs <= 0 || maxMs <= 0) return parts.map(() => 0)
  const barHeight = Math.round((totalMs / maxMs) * DAILY_BAR_MAX_HEIGHT)
  let cumulative = 0
  let previous = 0
  return parts.map((ms) => {
    cumulative += ms
    const height = Math.round((cumulative / totalMs) * barHeight)
    const segment = Math.max(0, height - previous)
    previous = height
    return segment
  })
}

/** `2026-09-13` を `9/13` にする。 */
function dailyDateLabel(studyDate: string): string {
  const parts = studyDate.split('-')
  if (parts.length < 3) return studyDate
  return `${Number(parts[1])}/${Number(parts[2])}`
}

/* ---------- 学習状況（サマリ・日次・語別） ---------- */
const loading = ref(false)
const error = ref('')
const loadedSummary = ref<JpnStatusSummary | null>(null)
const daily = ref<JpnDailyRow[]>([])
const rows = ref<JpnStatusRow[]>([])
const page = ref(1)
const size = ref(SIZE_OPTIONS[0])
const totalElements = ref(0)
const totalPages = ref(0)

const summary = computed<JpnStatusSummary>(() => loadedSummary.value ?? EMPTY_SUMMARY)
const lastPage = computed(() => Math.max(1, totalPages.value))
const pageItems = computed(() => paginationItems(page.value, lastPage.value))

const filters = reactive<{ learnState: '' | LearnState; jlpt: string }>({
  learnState: '',
  jlpt: ''
})

/** 日次は API が新しい順で返すので、グラフは古い順に並べ替える。 */
const dailyBars = computed<DailyBar[]>(() => {
  const sorted = [...daily.value].sort((left, right) => left.studyDate.localeCompare(right.studyDate))
  const maxMs = sorted.reduce((max, row) => Math.max(max, dailyTotalMs(row)), 0)
  return sorted.map((row) => {
    const parts = [row.typeAMs, row.typeBMs, row.typeCMs, row.typeDMs, row.typeEMs]
    const totalMs = dailyTotalMs(row)
    const heights = segmentHeights(parts, totalMs, maxMs)
    return {
      studyDate: row.studyDate,
      dateLabel: dailyDateLabel(row.studyDate),
      minutes: toMinutes(totalMs),
      segments: TEST_TYPE_OPTIONS.map((type, index) => ({
        type,
        modifier: segmentClass(type),
        height: heights[index],
        minutes: toMinutes(parts[index]),
        label: TEST_TYPE_LABELS[type]
      }))
    }
  })
})

async function loadStatus(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await fetchJpnStatus({
      learnState: filters.learnState === '' ? undefined : filters.learnState,
      jlpt: filters.jlpt === '' ? undefined : filters.jlpt,
      page: page.value,
      size: size.value
    })
    loadedSummary.value = response.data.summary
    daily.value = response.data.daily
    rows.value = response.data.items
    totalElements.value = response.data.totalElements
    totalPages.value = response.data.totalPages
  } catch (caught) {
    error.value = messageOf(caught, '学習状況を取得できませんでした。')
    loadedSummary.value = null
    daily.value = []
    rows.value = []
    totalElements.value = 0
    totalPages.value = 0
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void loadStatus()
}

function reset(): void {
  filters.learnState = ''
  filters.jlpt = ''
  page.value = 1
  void loadStatus()
}

function goto(target: number): void {
  if (target < 1 || target > lastPage.value || target === page.value) return
  page.value = target
  void loadStatus()
}

/** 件数を変えたら 1 ページ目から読み直す。 */
function changeSize(): void {
  page.value = 1
  void loadStatus()
}

/* ---------- 技能別の習得（タブを開いてから読む） ---------- */
const activeTab = ref<'words' | 'skills'>('words')
const skillLoading = ref(false)
const skillError = ref('')
const skillRows = ref<JpnSkillRow[]>([])
const skillPage = ref(1)
const skillSize = ref(SIZE_OPTIONS[0])
const skillTotalElements = ref(0)
const skillTotalPages = ref(0)

const skillFilters = reactive<{ testType: '' | TestType; skill: string }>({
  testType: '',
  skill: ''
})

const skillLastPage = computed(() => Math.max(1, skillTotalPages.value))
const skillPageItems = computed(() => paginationItems(skillPage.value, skillLastPage.value))

async function loadSkills(): Promise<void> {
  skillLoading.value = true
  skillError.value = ''
  try {
    const response = await searchJpnSkills({
      testType: skillFilters.testType === '' ? undefined : skillFilters.testType,
      skill: skillFilters.skill.trim() === '' ? undefined : skillFilters.skill.trim(),
      page: skillPage.value,
      size: skillSize.value
    })
    skillRows.value = response.data.items
    skillTotalElements.value = response.data.totalElements
    skillTotalPages.value = response.data.totalPages
  } catch (caught) {
    skillError.value = messageOf(caught, '技能の習得状況を取得できませんでした。')
    skillRows.value = []
    skillTotalElements.value = 0
    skillTotalPages.value = 0
  } finally {
    skillLoading.value = false
  }
}

/** タブを切り替える（技能タブを開いたときに技能の API を呼ぶ）。 */
function selectTab(tab: 'words' | 'skills'): void {
  activeTab.value = tab
  if (tab === 'skills') {
    void loadSkills()
  }
}

function searchSkills(): void {
  skillPage.value = 1
  void loadSkills()
}

function resetSkills(): void {
  skillFilters.testType = ''
  skillFilters.skill = ''
  skillPage.value = 1
  void loadSkills()
}

function gotoSkillPage(target: number): void {
  if (target < 1 || target > skillLastPage.value || target === skillPage.value) return
  skillPage.value = target
  void loadSkills()
}

function changeSkillSize(): void {
  skillPage.value = 1
  void loadSkills()
}

onMounted(() => {
  void loadStatus()
})
</script>

<template>
  <div class="jp-page">
    <p v-if="error" class="alert alert--danger" data-jp-status-error>{{ error }}</p>

    <!-- サマリ（学習状況全体の数値） -->
    <section class="card" data-jp-summary-card>
      <div class="card__header">
        <h2 class="card__title"><AppIcon name="sigma" size="sm" /> 学習サマリ</h2>
      </div>
      <p v-if="loading" class="jp-page__loading" data-jp-loading>読み込んでいます...</p>
      <div v-else-if="!error" class="jp-summary" data-jp-summary>
        <span class="jp-summary__item">
          <span class="jp-summary__label">学習した単語</span>
          <span class="jp-summary__value">{{ summary.studiedWordCount }}<span class="jp-summary__unit">語</span></span>
        </span>
        <span class="jp-summary__item">
          <span class="jp-summary__label">習得済</span>
          <span class="jp-summary__value">{{ summary.learnedCount }}<span class="jp-summary__unit">語</span></span>
        </span>
        <span class="jp-summary__item">
          <span class="jp-summary__label">お気に入り</span>
          <span class="jp-summary__value">{{ summary.favoriteCount }}<span class="jp-summary__unit">語</span></span>
        </span>
        <span class="jp-summary__item">
          <span class="jp-summary__label">平均習得度</span>
          <span class="jp-summary__value">
            {{ Math.round(summary.averageMastery) }}<span class="jp-summary__unit">%</span>
          </span>
        </span>
        <span class="jp-summary__item">
          <span class="jp-summary__label">回答数</span>
          <span class="jp-summary__value">{{ summary.answeredCount }}<span class="jp-summary__unit">回</span></span>
        </span>
        <span class="jp-summary__item">
          <span class="jp-summary__label">正答率</span>
          <span class="jp-summary__value">
            {{ Math.round(summary.accuracyPercent) }}<span class="jp-summary__unit">%</span>
          </span>
        </span>
        <span class="jp-summary__item">
          <span class="jp-summary__label">総学習時間</span>
          <span class="jp-summary__value">{{ formatDuration(summary.activeMs) }}</span>
        </span>
        <span class="jp-summary__item">
          <span class="jp-summary__label">今日の学習</span>
          <span class="jp-summary__value">{{ formatDuration(summary.todayActiveMs) }}</span>
        </span>
        <span class="jp-summary__item">
          <span class="jp-summary__label">最終学習</span>
          <span class="jp-summary__value">{{ dateTimeLabel(summary.lastStudiedAt) }}</span>
        </span>
      </div>
    </section>

    <!-- 日次の学習量（テスト種別 A〜E の積み上げ・古い順） -->
    <section class="card" data-jp-daily>
      <div class="card__header">
        <h2 class="card__title"><AppIcon name="clock" size="sm" /> 日次の学習量</h2>
        <div class="jp-legend" data-jp-legend>
          <span v-for="option in TEST_TYPE_OPTIONS" :key="option" class="jp-legend__item">
            <span class="jp-legend__dot" :class="legendDotClass(option)" />
            {{ legendLabel(option) }}
          </span>
        </div>
      </div>
      <p v-if="loading" class="jp-page__loading">読み込んでいます...</p>
      <p v-else-if="dailyBars.length === 0 && !error" class="jp-page__empty" data-jp-daily-empty>
        日次の学習記録がまだありません。
      </p>
      <div v-else-if="dailyBars.length > 0" class="jp-daily" data-jp-daily-chart>
        <div
          v-for="bar in dailyBars" :key="bar.studyDate" class="jp-daily__item"
          :data-jp-daily-bar="bar.studyDate"
        >
          <div class="jp-daily__bar">
            <span
              v-for="segment in bar.segments" :key="segment.type" class="jp-daily__segment"
              :class="segment.modifier" :style="{ height: `${segment.height}px` }"
              :title="`${segment.label} ${segment.minutes} 分`"
            />
          </div>
          <span class="jp-daily__date">{{ bar.dateLabel }}</span>
          <span class="jp-daily__value">{{ bar.minutes }} 分</span>
        </div>
      </div>
    </section>

    <!-- 語別の学習状況 ／ 技能別の習得 -->
    <div class="tabs" role="tablist">
      <button
        type="button" class="tabs__tab" :class="{ 'is-active': activeTab === 'words' }"
        role="tab" :aria-selected="activeTab === 'words'" data-jp-tab="words" @click="selectTab('words')"
      >
        語別の学習状況
      </button>
      <button
        type="button" class="tabs__tab" :class="{ 'is-active': activeTab === 'skills' }"
        role="tab" :aria-selected="activeTab === 'skills'" data-jp-tab="skills" @click="selectTab('skills')"
      >
        技能別の習得
      </button>
    </div>

    <section
      class="tabs__panel" :class="{ 'is-active': activeTab === 'words' }"
      role="tabpanel" aria-label="語別の学習状況"
    >
      <div v-if="activeTab === 'words'" class="jp-tab-body">
        <!-- 絞り込み -->
        <div class="search-panel" data-jp-status-filters>
          <div class="search-panel__head">
            <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
            <div class="search-panel__actions">
              <button
                type="button" class="btn btn--primary" data-jp-status-search
                :disabled="loading" @click="search"
              >
                <AppIcon name="search" size="sm" /> 検索
              </button>
              <button
                type="button" class="btn btn--secondary" data-jp-status-reset
                :disabled="loading" @click="reset"
              >
                <AppIcon name="rotate" size="sm" /> リセット
              </button>
            </div>
          </div>
          <div class="filters">
            <div class="filters__row">
              <span class="filter-item">
                <span class="filter-item__label">学習状態：</span>
                <select v-model="filters.learnState" class="select" data-jp-status-filter="learnState">
                  <option value="">すべて</option>
                  <option v-for="option in LEARN_STATE_OPTIONS" :key="option" :value="option">
                    {{ LEARN_STATE_LABELS[option] }}
                  </option>
                </select>
              </span>
              <span class="filter-item">
                <span class="filter-item__label">JLPT：</span>
                <select v-model="filters.jlpt" class="select" data-jp-status-filter="jlpt">
                  <option value="">すべて</option>
                  <option v-for="option in JLPT_OPTIONS" :key="option" :value="option">{{ option }}</option>
                </select>
              </span>
            </div>
          </div>
        </div>

        <!-- 語別の一覧 -->
        <section class="card">
          <div class="card__header">
            <h2 class="card__title"><AppIcon name="list" size="sm" /> 語別の学習状況</h2>
            <span class="cell-muted" data-jp-status-count>全 {{ totalElements }} 件</span>
            <div class="search-panel__actions">
              <button
                type="button" class="btn btn--secondary btn--sm" data-jp-refresh
                :disabled="loading" @click="loadStatus"
              >
                <AppIcon name="rotate" size="sm" /> 再読み込み
              </button>
            </div>
          </div>

          <p v-if="loading" class="jp-page__loading">読み込んでいます...</p>
          <p v-else-if="rows.length === 0 && !error" class="jp-page__empty" data-jp-status-words-empty>
            該当する学習状況がありません。
          </p>
          <div v-else-if="rows.length > 0" class="table-wrap">
            <table class="data-table" data-jp-status-words>
              <thead>
                <tr>
                  <th>見出し語</th>
                  <th>JLPT</th>
                  <th>品詞</th>
                  <th>収録</th>
                  <th>学習状態</th>
                  <th>習得度</th>
                  <th>回答</th>
                  <th class="align-center">連続正解</th>
                  <th>学習時間</th>
                  <th>最終学習</th>
                  <th>次回復習</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.wordId" :data-jp-status-row="row.wordId">
                  <td>
                    <span class="jp-word">
                      {{ row.word }}
                      <span class="jp-word__reading">{{ row.reading ?? '—' }}</span>
                    </span>
                  </td>
                  <td>{{ row.jlptLevel ?? '—' }}</td>
                  <td>{{ row.partOfSpeech ?? '—' }}</td>
                  <td>
                    {{ row.book ?? '—' }}
                    <span class="jp-cell-sub">{{ row.category ?? '—' }}</span>
                  </td>
                  <td>
                    <span class="badge" :class="LEARN_STATE_BADGES[row.learnState]">
                      {{ LEARN_STATE_LABELS[row.learnState] }}
                    </span>
                  </td>
                  <td>
                    <span class="jp-mastery">
                      <span class="jp-mastery__bar">
                        <span class="jp-mastery__fill" :style="{ width: `${row.mastery}%` }" />
                      </span>
                      <span class="jp-mastery__value">{{ Math.round(row.mastery) }}%</span>
                    </span>
                  </td>
                  <td>{{ answeredLabel(row) }}</td>
                  <td class="align-center">{{ streakLabel(row) }}</td>
                  <td>{{ formatDuration(row.activeMs) }}</td>
                  <td>{{ dateTimeLabel(row.lastStudiedAt) }}</td>
                  <td>{{ dateTimeLabel(row.nextReviewAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="!loading && rows.length > 0" class="pagination" data-jp-status-pagination>
            <span class="pagination__info">
              全 {{ totalElements }} 件（{{ page }} / {{ lastPage }} ページ）
            </span>
            <label class="pagination__size">
              <span>件数</span>
              <select
                v-model.number="size" class="select" aria-label="1ページの件数"
                data-jp-page-size @change="changeSize"
              >
                <option v-for="option in SIZE_OPTIONS" :key="option" :value="option">{{ option }} 件</option>
              </select>
            </label>
            <div class="pagination__pages">
              <button
                type="button" class="page-btn" :disabled="page <= 1"
                data-jp-page-prev @click="goto(page - 1)"
              >
                ‹
              </button>
              <template v-for="(item, index) in pageItems" :key="index">
                <span v-if="item === 'gap'" class="page-gap">…</span>
                <button
                  v-else type="button" class="page-btn" :class="{ 'is-active': item === page }"
                  :data-jp-page="item" @click="goto(item)"
                >
                  {{ item }}
                </button>
              </template>
              <button
                type="button" class="page-btn" :disabled="page >= lastPage"
                data-jp-page-next @click="goto(page + 1)"
              >
                ›
              </button>
            </div>
          </div>
        </section>
      </div>
    </section>

    <section
      class="tabs__panel" :class="{ 'is-active': activeTab === 'skills' }"
      role="tabpanel" aria-label="技能別の習得"
    >
      <div v-if="activeTab === 'skills'" class="jp-tab-body">
        <p v-if="skillError" class="alert alert--danger" data-jp-skill-error>{{ skillError }}</p>

        <!-- 絞り込み -->
        <div class="search-panel" data-jp-skill-filters>
          <div class="search-panel__head">
            <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
            <div class="search-panel__actions">
              <button
                type="button" class="btn btn--primary" data-jp-skill-search
                :disabled="skillLoading" @click="searchSkills"
              >
                <AppIcon name="search" size="sm" /> 検索
              </button>
              <button
                type="button" class="btn btn--secondary" data-jp-skill-reset
                :disabled="skillLoading" @click="resetSkills"
              >
                <AppIcon name="rotate" size="sm" /> リセット
              </button>
            </div>
          </div>
          <div class="filters">
            <div class="filters__row">
              <span class="filter-item">
                <span class="filter-item__label">テスト種別：</span>
                <select v-model="skillFilters.testType" class="select" data-jp-skill-filter="testType">
                  <option value="">すべて</option>
                  <option v-for="option in TEST_TYPE_OPTIONS" :key="option" :value="option">
                    {{ TEST_TYPE_LABELS[option] }}
                  </option>
                </select>
              </span>
              <span class="filter-item filter-item--grow">
                <span class="filter-item__label">技能区分：</span>
                <input
                  v-model="skillFilters.skill" class="input" type="search" data-jp-skill-filter="skill"
                  placeholder="例: MEANING" @keyup.enter="searchSkills"
                >
              </span>
            </div>
          </div>
        </div>

        <!-- 技能別の一覧 -->
        <section class="card">
          <div class="card__header">
            <h2 class="card__title"><AppIcon name="list" size="sm" /> 技能別の習得</h2>
            <span class="cell-muted" data-jp-skill-count>全 {{ skillTotalElements }} 件</span>
            <div class="search-panel__actions">
              <button
                type="button" class="btn btn--secondary btn--sm" data-jp-skill-refresh
                :disabled="skillLoading" @click="loadSkills"
              >
                <AppIcon name="rotate" size="sm" /> 再読み込み
              </button>
            </div>
          </div>

          <p v-if="skillLoading" class="jp-page__loading">読み込んでいます...</p>
          <p v-else-if="skillRows.length === 0 && !skillError" class="jp-page__empty" data-jp-skills-empty>
            該当する技能の習得状況がありません。
          </p>
          <div v-else-if="skillRows.length > 0" class="table-wrap">
            <table class="data-table" data-jp-skills>
              <thead>
                <tr>
                  <th>見出し語</th>
                  <th>テスト種別</th>
                  <th>技能区分</th>
                  <th>学習状態</th>
                  <th>習得度</th>
                  <th>回答</th>
                  <th class="align-center">連続正解</th>
                  <th>最終判定</th>
                  <th>最終学習</th>
                  <th>次回復習</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="row in skillRows" :key="`${row.wordId}-${row.testType}-${row.skillCode}`"
                  :data-jp-skill-row="`${row.wordId}-${row.testType}-${row.skillCode}`"
                >
                  <td>
                    <span class="jp-word">
                      {{ row.word }}
                      <span class="jp-word__reading">{{ row.reading ?? '—' }}</span>
                    </span>
                  </td>
                  <td>{{ testTypeLabel(row.testType) }}</td>
                  <td>{{ row.skillCode }}</td>
                  <td>
                    <span class="badge" :class="LEARN_STATE_BADGES[row.learnState]">
                      {{ LEARN_STATE_LABELS[row.learnState] }}
                    </span>
                  </td>
                  <td>
                    <span class="jp-mastery">
                      <span class="jp-mastery__bar">
                        <span class="jp-mastery__fill" :style="{ width: `${row.mastery}%` }" />
                      </span>
                      <span class="jp-mastery__value">{{ Math.round(row.mastery) }}%</span>
                    </span>
                  </td>
                  <td>{{ answeredLabel(row) }}</td>
                  <td class="align-center">{{ streakLabel(row) }}</td>
                  <td>{{ row.lastJudgment ?? '—' }}</td>
                  <td>{{ dateTimeLabel(row.lastStudiedAt) }}</td>
                  <td>{{ dateTimeLabel(row.nextReviewAt) }}</td>
                </tr>
              </tbody>
            </table>
          </div>

          <div v-if="!skillLoading && skillRows.length > 0" class="pagination" data-jp-skill-pagination>
            <span class="pagination__info">
              全 {{ skillTotalElements }} 件（{{ skillPage }} / {{ skillLastPage }} ページ）
            </span>
            <label class="pagination__size">
              <span>件数</span>
              <select
                v-model.number="skillSize" class="select" aria-label="1ページの件数"
                data-jp-skill-page-size @change="changeSkillSize"
              >
                <option v-for="option in SIZE_OPTIONS" :key="option" :value="option">{{ option }} 件</option>
              </select>
            </label>
            <div class="pagination__pages">
              <button
                type="button" class="page-btn" :disabled="skillPage <= 1"
                data-jp-skill-page-prev @click="gotoSkillPage(skillPage - 1)"
              >
                ‹
              </button>
              <template v-for="(item, index) in skillPageItems" :key="index">
                <span v-if="item === 'gap'" class="page-gap">…</span>
                <button
                  v-else type="button" class="page-btn" :class="{ 'is-active': item === skillPage }"
                  :data-jp-skill-page="item" @click="gotoSkillPage(item)"
                >
                  {{ item }}
                </button>
              </template>
              <button
                type="button" class="page-btn" :disabled="skillPage >= skillLastPage"
                data-jp-skill-page-next @click="gotoSkillPage(skillPage + 1)"
              >
                ›
              </button>
            </div>
          </div>
        </section>
      </div>
    </section>
  </div>
</template>

<style scoped>
/* タブの中身（検索条件と一覧）を縦に並べる */
.jp-tab-body {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
}

/* 日次は種別ごとの積み上げ棒にする（横並びの区切り線は詰める） */
.jp-daily__bar {
  flex-direction: column-reverse;
  justify-content: flex-start;
  align-items: center;
  gap: 0;
}

/* 積み上げの一番上だけ角を丸める（column-reverse なので最後の子＝E） */
.jp-daily__segment {
  border-radius: 0;
}

.jp-daily__segment:last-child {
  border-radius: var(--radius-sm) var(--radius-sm) 0 0;
}
</style>

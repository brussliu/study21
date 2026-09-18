<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  correctStudyMonitorSnapshots,
  searchStudyMonitorSnapshots,
  studyMonitorImageUrl,
  type AnalysisResultCode,
  type AnalysisState,
  type StudyMonitorSearchResult,
  type StudyMonitorSnapshot,
  type StudyMonitorVideo
} from '@/api/studyMonitor'
import '@/features/study-monitor/study-monitor.css'

/**
 * 学習状況モニター（2.0 の study_monitor.jsp 相当。レイアウトも 2.0 のまま）。
 *
 * データは `MON_学習モニター*`（2.0 から移行済み）を API で読む。
 * 取り込み（batL02）と AI 分析（batL03）は 2.1 では未実装なので、
 * 既に取り込み・分析済みのデータを表示し、判定結果の手動修正だけを行う。
 */
const toast = useToast()

/** 分析結果のコード → 表示名（DB はコード、画面は日本語。2.0 と同じ表記）。 */
const RESULT_LABELS: Record<AnalysisResultCode, string> = {
  STUDY_NO_PC: '学習中（PC不使用・読書または筆記）',
  STUDY_PC: '学習中（PC使用）',
  AWAY: '離席中',
  PC_NON_STUDY: 'PC使用中（非学習）',
  OTHER: 'その他',
  UNKNOWN: '判断不可'
}
/** 分析結果を表す色（tokens の変数）。凡例・バー・時間軸で共通に使う。 */
const RESULT_COLORS: Record<AnalysisResultCode, string> = {
  STUDY_NO_PC: 'var(--color-success)',
  STUDY_PC: 'var(--color-info)',
  AWAY: 'var(--color-text-subtle)',
  PC_NON_STUDY: 'var(--color-danger)',
  OTHER: 'var(--color-warning)',
  UNKNOWN: 'var(--color-primary)'
}
/** 時間軸（動画の中）で使う「未分析」の色。 */
const UNANALYSED_COLOR = 'var(--color-border-strong)'

const RESULT_CODES = Object.keys(RESULT_LABELS) as AnalysisResultCode[]
const ANALYSIS_STATE_LABELS: Record<AnalysisState, string> = {
  WAITING: '未分析',
  RUNNING: '分析中',
  COMPLETED: '分析済み',
  ERROR: 'エラー'
}

const today = new Date()
const todayYmd = `${today.getFullYear()}-${`${today.getMonth() + 1}`.padStart(2, '0')}-${`${today.getDate()}`.padStart(2, '0')}`

const filters = reactive({ date: todayYmd, timeFrom: '08:00', timeTo: '23:59' })
const loading = ref(false)
const error = ref('')
const result = ref<StudyMonitorSearchResult | null>(null)

/** 表示方法（2.0 の「動画から確認」/「スナップショットから確認」）。 */
const searchMode = ref<'video' | 'snapshot'>('video')
const selectedVideoId = ref<number | null>(null)
const viewMode = ref<'thumb' | 'list'>('thumb')
const selectedIds = ref<number[]>([])
/** AI分析の絞り込み（すべて / 分析済み / 未分析 / エラー）。 */
const analysisState = ref<'all' | 'done' | 'waiting' | 'error'>('all')
/** 分析結果の絞り込み（スナップショット検索モードのときだけ出す）。 */
const analysisResult = ref<'all' | AnalysisResultCode>('all')
/** 画像が無い（コピー前・欠落）スナップショット。代替表示に切り替える。 */
const brokenImages = ref<number[]>([])
/** 時間帯を自動で広げたか（1 回だけ。0 時台の記録しかない日に「データが無い」と見えるのを防ぐ）。 */
const widened = ref(false)

/** 一括変更モーダル */
const bulkOpen = ref(false)
const bulkResult = ref<AnalysisResultCode>('STUDY_NO_PC')
const bulkReason = ref('')
/** 1 枚の詳細モーダル */
const detailOpen = ref(false)
const detailSnapshotId = ref<number | null>(null)
/** 詳細モーダルの修正フォーム（2.0 と同じく 1 枚ずつ直せる）。 */
const detailResult = ref<AnalysisResultCode>('STUDY_NO_PC')
const detailReason = ref('')
/** 時間軸モーダル */
const timelineOpen = ref(false)

const videos = computed<StudyMonitorVideo[]>(() => result.value?.videos ?? [])
const visibleVideos = computed<StudyMonitorVideo[]>(() => videos.value)
const visibleSnapshots = computed<StudyMonitorSnapshot[]>(() => {
  const all = result.value?.snapshots ?? []
  if (searchMode.value !== 'video') return all
  const videoId = selectedVideoId.value
  return videoId === null ? all : all.filter((snapshot) => snapshot.videoId === videoId)
})
const selectedVideo = computed<StudyMonitorVideo | null>(() =>
  videos.value.find((video) => video.videoId === selectedVideoId.value) ?? null)
const summary = computed(() => result.value?.summary ?? {
  videoCount: 0, snapshotCount: 0, completed: 0, waiting: 0, errors: 0, resultCounts: {} as Record<string, number>
})

const detailSnapshot = computed<StudyMonitorSnapshot | null>(() =>
  visibleSnapshots.value.find((snapshot) => snapshot.snapshotId === detailSnapshotId.value) ?? null)

function resultLabel(code: string | null): string {
  if (code === null) return '未分析'
  return RESULT_LABELS[code as AnalysisResultCode] ?? code
}

function resultColor(code: string | null): string {
  if (code === null) return UNANALYSED_COLOR
  return RESULT_COLORS[code as AnalysisResultCode] ?? UNANALYSED_COLOR
}

/** 時刻（HH:mm:ss）だけを出す。 */
function timeOf(iso: string | null): string {
  return iso === null ? '—' : iso.slice(11, 19)
}

function resultBadgeClass(code: string | null): string {
  switch (code) {
    case 'STUDY_NO_PC':
    case 'STUDY_PC':
      return 'badge--success'
    case 'AWAY':
      return 'badge--neutral'
    case 'PC_NON_STUDY':
      return 'badge--danger'
    case 'OTHER':
      return 'badge--warning'
    case 'UNKNOWN':
      return 'badge--info'
    default:
      return 'badge--neutral'
  }
}

/** 動画の状態（2.0 のセグメントの状態バッジ）。 */
function videoStateLabel(video: StudyMonitorVideo): string {
  if (video.importState === 'ERROR') return '取込エラー'
  if (video.snapshotCount > 0 && video.completedCount === video.snapshotCount) return '分析済み'
  return '未分析あり'
}

function videoStateClass(video: StudyMonitorVideo): string {
  const label = videoStateLabel(video)
  if (label === '取込エラー') return 'is-error'
  if (label === '分析済み') return 'is-completed'
  return 'is-waiting'
}

/** 動画の終了時刻（開始 ＋ 長さ）。 */
function endTimeOf(video: StudyMonitorVideo): string {
  const [hours, minutes] = video.startedAt.slice(11, 16).split(':').map(Number)
  const total = (hours ?? 0) * 60 + (minutes ?? 0) + Math.round(video.durationSeconds / 60)
  return `${String(Math.floor(total / 60) % 24).padStart(2, '0')}:${String(total % 60).padStart(2, '0')}`
}

function imageUrl(snapshotId: number): string {
  return studyMonitorImageUrl(snapshotId)
}

/**
 * 画像の縦横比（長方形で出す）。
 * DB の 幅/高さ（例: 3840×2160）を使い、無いデータは 16:9 とみなす。
 * 読み込み前でも場所が確保でき、画像が届いた瞬間にガタつかない。
 */
function imageAspectRatio(snapshot: StudyMonitorSnapshot): string {
  if (snapshot.width !== null && snapshot.height !== null && snapshot.width > 0 && snapshot.height > 0) {
    return `${snapshot.width} / ${snapshot.height}`
  }
  return '16 / 9'
}

function onImageError(snapshotId: number): void {
  if (!brokenImages.value.includes(snapshotId)) {
    brokenImages.value = [...brokenImages.value, snapshotId]
  }
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await searchStudyMonitorSnapshots({
      date: filters.date,
      timeFrom: filters.timeFrom,
      timeTo: filters.timeTo,
      // 動画の絞り込みは画面側で行う（videoId を送ると「選んでいた動画が別の日」のときに
      // 動画一覧も空になり、選択をやり直せなくなる）
      videoId: null,
      analysisState: analysisState.value,
      result: analysisResult.value === 'all' ? undefined : analysisResult.value
    })
    result.value = response.data
    // 既定の時間帯（08:00〜23:59）に記録が無ければ、いったん 1 日全体で探し直す
    // （移行したデータは 0 時台だけの日がある。ユーザーが手で指定した後は広げない）
    if (response.data.snapshots.length === 0 && !widened.value
        && (filters.timeFrom !== '00:00' || filters.timeTo !== '23:59')) {
      widened.value = true
      filters.timeFrom = '00:00'
      filters.timeTo = '23:59'
      toast.info('この日の記録は 0 時台にあります。時間帯を 00:00〜23:59 に広げました。')
      await load()
      return
    }
    // 動画タブ: 選択が無い／この日に無い動画を選んでいたら、先頭の動画に合わせる
    if (searchMode.value === 'video' && response.data.videos.length > 0
        && !response.data.videos.some((video) => video.videoId === selectedVideoId.value)) {
      selectedVideoId.value = response.data.videos[0]?.videoId ?? null
    }
    const ids = new Set(response.data.snapshots.map((snapshot) => snapshot.snapshotId))
    selectedIds.value = selectedIds.value.filter((id) => ids.has(id))
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : '学習状況モニターの情報を取得できませんでした。'
  } finally {
    loading.value = false
  }
}

/* 画面の【更新】ボタンは無し（日付・時間帯・タブ・絞り込みを変えると自動で取り直す）。 */

function selectVideo(video: StudyMonitorVideo): void {
  selectedVideoId.value = video.videoId
  selectedIds.value = []
  void load()
}

function toggleSelect(snapshot: StudyMonitorSnapshot): void {
  if (snapshot.analysisState !== 'COMPLETED') {
    toast.warning('分析済みの画像だけ一括変更できます。')
    return
  }
  selectedIds.value = selectedIds.value.includes(snapshot.snapshotId)
    ? selectedIds.value.filter((id) => id !== snapshot.snapshotId)
    : [...selectedIds.value, snapshot.snapshotId]
}

/** 「表示中の分析済み画像をすべて選択」（2.0 と同じチェック）。 */
function selectVisible(event: Event): void {
  const checked = (event.target as HTMLInputElement).checked
  const targets = visibleSnapshots.value.filter((snapshot) => snapshot.analysisState === 'COMPLETED')
  selectedIds.value = checked ? targets.map((snapshot) => snapshot.snapshotId) : []
  if (checked && targets.length > 0) {
    toast.success(`表示中の分析済み ${targets.length} 件を選択しました。`)
  }
}

function openBulk(): void {
  if (selectedIds.value.length === 0) {
    toast.warning('分析結果を変更する画像を選択してください。')
    return
  }
  bulkResult.value = 'STUDY_NO_PC'
  bulkReason.value = ''
  bulkOpen.value = true
}

/**
 * 分析結果の修正を API に送る（一括変更と 1 枚の詳細で共通）。
 * 修正理由は任意（詳細画面。ユーザーの指定）。必須にするかは呼び出し側が決める。
 * @returns 保存できたら true（呼び出し側がモーダルを閉じる）
 */
async function submitCorrection(
  targets: StudyMonitorSnapshot[],
  result: AnalysisResultCode,
  reason: string
): Promise<boolean> {
  try {
    const response = await correctStudyMonitorSnapshots(
      targets.map((snapshot) => ({ snapshotId: snapshot.snapshotId, version: snapshot.version })),
      result,
      reason.trim()
    )
    toast.success(response.data.message)
    await load()
    return true
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '分析結果を更新できませんでした。')
    return false
  }
}

/** 一括変更を適用する（2.0 と同じく修正理由は必須。詳細画面は任意）。 */
async function applyBulk(): Promise<void> {
  if (bulkReason.value.trim() === '') {
    toast.warning('修正理由を入力してください。')
    return
  }
  const targets = visibleSnapshots.value.filter((snapshot) => selectedIds.value.includes(snapshot.snapshotId))
  if (!await submitCorrection(targets, bulkResult.value, bulkReason.value)) return
  bulkOpen.value = false
  selectedIds.value = []
}

function openDetail(snapshot: StudyMonitorSnapshot): void {
  detailSnapshotId.value = snapshot.snapshotId
  // 2.0 の詳細と同じく、今の判定結果を選んだ状態から直せるようにする
  detailResult.value = (snapshot.resultCode as AnalysisResultCode | null) ?? 'STUDY_NO_PC'
  detailReason.value = ''
  detailOpen.value = true
}

/** 詳細モーダルで 1 枚だけ分析結果を修正する（2.0 の updateManualAnalysis 相当）。 */
async function applyDetail(): Promise<void> {
  const snapshot = detailSnapshot.value
  if (snapshot === null || snapshot.analysisState !== 'COMPLETED') return
  if (!await submitCorrection([snapshot], detailResult.value, detailReason.value)) return
  detailOpen.value = false
}

function openTimeline(): void {
  timelineOpen.value = true
}

/**
 * 時間軸で使うスナップショット。
 * 【時間軸で確認】は「その時間帯に何をしていたか」を見る画面なので、
 * 動画の絞り込み（＝画面の一覧）ではなく検索結果の全部を対象にする。
 */
const timelineSnapshots = computed<StudyMonitorSnapshot[]>(() =>
  [...(result.value?.snapshots ?? [])].sort((left, right) => left.capturedAt.localeCompare(right.capturedAt)))

/** 時間軸（2.0 の activity-timeline）。時間帯全体のスナップショットを時刻順に並べる。 */
const timelineSegments = computed(() => {
  const items = timelineSnapshots.value
  return items.map((snapshot, index) => {
    const start = secondsOf(snapshot.capturedAt)
    const next = index + 1 < items.length ? secondsOf(items[index + 1]!.capturedAt) : start + 60
    return {
      id: snapshot.snapshotId,
      time: timeOf(snapshot.capturedAt),
      label: resultLabel(snapshot.resultCode),
      color: resultColor(snapshot.resultCode),
      start,
      seconds: Math.max(30, Math.min(90, next - start))
    }
  })
})

function secondsOf(iso: string): number {
  const [hours, minutes, seconds] = iso.slice(11, 19).split(':').map(Number)
  return (hours ?? 0) * 3600 + (minutes ?? 0) * 60 + (seconds ?? 0)
}

const timelineRange = computed(() => {
  const from = secondsOf(`2000-01-01T${filters.timeFrom}:00`)
  const to = secondsOf(`2000-01-01T${filters.timeTo}:00`)
  return { from, duration: Math.max(1, to - from) }
})

function timelineStyle(segment: { start: number; seconds: number }): Record<string, string> {
  const { from, duration } = timelineRange.value
  return {
    left: `${(((segment.start - from) / duration) * 100).toFixed(3)}%`,
    width: `${Math.max(0.4, (segment.seconds / duration) * 100).toFixed(3)}%`
  }
}

/** 時間軸の目盛り（30 分〜2 時間。範囲の広さで変える）。 */
const timelineTicks = computed(() => {
  const { from, duration } = timelineRange.value
  const hours = duration / 3600
  const interval = hours > 10 ? 2 * 3600 : hours > 5 ? 3600 : 30 * 60
  const ticks: { seconds: number; left: number; edge: string; label: string }[] = []
  for (let seconds = Math.ceil(from / interval) * interval; seconds <= from + duration; seconds += interval) {
    const left = ((seconds - from) / duration) * 100
    ticks.push({
      seconds,
      left,
      edge: left < 0.001 ? 'start' : left > 99.999 ? 'end' : '',
      label: `${String(Math.floor(seconds / 3600)).padStart(2, '0')}:${String(Math.floor((seconds % 3600) / 60)).padStart(2, '0')}`
    })
  }
  return ticks
})

// 条件が変わったら取り直す（2.0 の【更新】と同じ。動画の選択は selectVideo で行う）
watch([() => filters.date, () => filters.timeFrom, () => filters.timeTo, analysisState, analysisResult, searchMode],
  () => { void load() })

onMounted(() => {
  void load()
})
</script>

<template>
  <div class="monitor-page" :class="{ 'is-snapshot-search': searchMode === 'snapshot' }">
    <!-- 上部: タブ（動画から確認 / スナップショットから確認）＋その右に検索条件 -->
    <div class="monitor-head">
      <div class="tabs" role="tablist">
        <button
          type="button" class="tabs__tab" :class="{ 'is-active': searchMode === 'video' }"
          role="tab" :aria-selected="searchMode === 'video'" data-view-mode="video"
          @click="searchMode = 'video'"
        >
          動画から確認
        </button>
        <button
          type="button" class="tabs__tab" :class="{ 'is-active': searchMode === 'snapshot' }"
          role="tab" :aria-selected="searchMode === 'snapshot'" data-view-mode="snapshot"
          @click="searchMode = 'snapshot'"
        >
          スナップショットから確認
        </button>
      </div>

      <!-- 検索条件（2.0 の monitor-toolbar。タブの右に置く） -->
      <div class="monitor-toolbar" aria-label="検索条件">
        <div class="monitor-filter">
          <label for="monitorDate">対象日</label>
          <input id="monitorDate" v-model="filters.date" class="input" type="date" aria-label="対象日">
        </div>

        <div class="monitor-filter monitor-time-range">
          <label>時間帯</label>
          <div>
            <input v-model="filters.timeFrom" class="input" type="time" aria-label="開始時刻">
            <span>～</span>
            <input v-model="filters.timeTo" class="input" type="time" aria-label="終了時刻">
          </div>
        </div>

        <div class="monitor-filter">
          <label for="monitorState">AI分析</label>
          <select id="monitorState" v-model="analysisState" class="select" aria-label="AI分析">
            <option value="all">すべて</option>
            <option value="done">分析済み</option>
            <option value="waiting">未分析</option>
            <option value="error">エラー</option>
          </select>
        </div>

        <div v-if="searchMode === 'snapshot'" class="monitor-filter">
          <label for="monitorResult">分析結果</label>
          <select id="monitorResult" v-model="analysisResult" class="select" aria-label="分析結果">
            <option value="all">すべて</option>
            <option v-for="code in RESULT_CODES" :key="code" :value="code">{{ resultLabel(code) }}</option>
          </select>
        </div>
      </div>
    </div>

    <!-- 検索結果の分析集計（2.0 の snapshot-summary） -->
    <section class="snapshot-summary" aria-live="polite">
      <div class="snapshot-summary-body">
        <div class="snapshot-summary-left">
          <div class="snapshot-summary-heading">
            <h2>検索結果の分析集計</h2>
            <p>現在の条件に一致した動画とスナップショットを集計します。</p>
          </div>
          <div class="snapshot-summary-overview">
            <div class="summary-count-card" data-stat="videos">
              <span>動画</span>
              <strong>{{ summary.videoCount }} 本</strong>
            </div>
            <div class="summary-count-card" data-stat="total">
              <span>スナップショット</span>
              <strong>{{ summary.snapshotCount }} 枚</strong>
            </div>
            <div class="summary-count-card is-completed" data-stat="done">
              <span>分析済み</span>
              <strong>{{ summary.completed }} 件</strong>
            </div>
            <div class="summary-count-card is-waiting" data-stat="waiting">
              <span>未分析</span>
              <strong>{{ summary.waiting }} 件</strong>
              <small v-if="summary.errors > 0" data-stat="errors">エラー {{ summary.errors }} 件</small>
            </div>
          </div>
        </div>

        <div class="snapshot-result-chart">
          <div class="snapshot-result-chart-heading">
            <span class="snapshot-result-chart-title">分析結果の割合</span>
            <button type="button" class="btn btn--secondary btn--sm" data-action="timeline-open" @click="openTimeline">
              <AppIcon name="clock" size="sm" /> 時間軸で確認
            </button>
          </div>
          <div class="sm-bar" role="img" aria-label="分析結果の割合">
            <span v-if="summary.snapshotCount === 0" class="sm-bar__empty">まだ分析結果がありません</span>
            <span
              v-for="code in RESULT_CODES"
              v-else
              :key="code"
              class="sm-bar__part"
              :style="{
                width: `${((summary.resultCounts[code] ?? 0) / summary.snapshotCount) * 100}%`,
                background: RESULT_COLORS[code]
              }"
              :title="`${resultLabel(code)} ${summary.resultCounts[code] ?? 0} 枚`"
            />
          </div>
          <div class="snapshot-result-summary">
            <span v-for="code in RESULT_CODES" :key="code" class="summary-result-item">
              <span class="summary-result-item__swatch" :style="{ background: RESULT_COLORS[code] }" />
              {{ resultLabel(code) }} <strong>{{ summary.resultCounts[code] ?? 0 }}</strong>
            </span>
          </div>
        </div>
      </div>
    </section>

    <!-- 本体（左: 動画 / 右: スナップショット） -->
    <div class="monitor-layout">
      <div class="monitor-main-column">
        <!-- 動画セグメント -->
        <section class="monitor-section segment-section">
          <div class="section-heading">
            <div>
              <h2><AppIcon name="list" size="sm" /> 動画一覧</h2>
              <p>フォルダー内のファイル名から開始・終了時刻を表示</p>
            </div>
            <span class="section-count">{{ visibleVideos.length }} 本</span>
          </div>
          <div class="segment-list">
            <button
              v-for="video in visibleVideos"
              :key="video.videoId"
              type="button"
              class="segment-row"
              :class="{ 'is-selected': video.videoId === selectedVideoId }"
              :data-video="video.videoId"
              @click="selectVideo(video)"
            >
              <span>
                <span class="segment-name">{{ video.fileName }}</span>
                <span class="segment-time">
                  {{ timeOf(video.startedAt).slice(0, 5) }} 〜 {{ endTimeOf(video) }}
                </span>
              </span>
              <span class="segment-state" :class="videoStateClass(video)">
                {{ videoStateLabel(video) }}
              </span>
              <span class="segment-duration">{{ video.snapshotCount }} 枚切出</span>
            </button>
            <p v-if="visibleVideos.length === 0" class="snapshot-empty">この日の動画はありません。</p>
          </div>
        </section>

        <!-- スナップショット（動画から確認・スナップショットから確認の両方で使う） -->
        <section class="monitor-section snapshot-section">
          <div class="section-heading">
            <div>
              <h2><AppIcon name="list" size="sm" /> スナップショット一覧</h2>
              <p>{{ selectedVideo ? selectedVideo.fileName : '動画を選ぶと切り出した画像を表示します' }}</p>
            </div>
            <div class="snapshot-tools">
              <span class="section-count" data-snapshot-count>{{ visibleSnapshots.length }} 枚</span>
              <div class="snapshot-display-mode" role="group" aria-label="画像表示形式">
                <button
                  type="button" class="btn btn--icon btn--sm" :class="{ 'is-active': viewMode === 'thumb' }"
                  title="縮略図表示" aria-label="縮略図表示" data-action="view-thumb" @click="viewMode = 'thumb'"
                >
                  <AppIcon name="grid" size="sm" />
                </button>
                <button
                  type="button" class="btn btn--icon btn--sm" :class="{ 'is-active': viewMode === 'list' }"
                  title="一覧表示" aria-label="一覧表示" data-action="view-list" @click="viewMode = 'list'"
                >
                  <AppIcon name="menu" size="sm" />
                </button>
              </div>
            </div>
          </div>

          <div class="snapshot-bulk-toolbar">
            <label class="snapshot-select-all">
              <input type="checkbox" data-action="select-visible" @change="selectVisible">
              表示中の分析済み画像をすべて選択
            </label>
            <button
              type="button" class="btn btn--primary btn--sm" data-action="bulk-open"
              :disabled="selectedIds.length === 0" @click="openBulk"
            >
              <AppIcon name="edit" size="sm" /> 分析結果を一括変更
            </button>
            <span class="snapshot-bulk-count">{{ selectedIds.length }} 件選択</span>
          </div>

          <p v-if="selectedVideo === null && visibleSnapshots.length === 0" class="snapshot-empty">
            左の動画を選んでください。
          </p>
          <p v-else-if="visibleSnapshots.length === 0" class="snapshot-empty">
            この条件にスナップショットがありません。
          </p>

          <div
            v-else
            class="snapshot-grid sm-snapshots"
            :class="{ 'list-view': viewMode === 'list' }"
          >
            <figure
              v-for="snapshot in visibleSnapshots"
              :key="snapshot.snapshotId"
              class="snapshot-card"
              :class="{ 'is-bulk-selected': selectedIds.includes(snapshot.snapshotId) }"
              :data-snapshot="snapshot.snapshotId"
            >
              <label v-if="snapshot.analysisState === 'COMPLETED'" class="snapshot-select-control">
                <input
                  type="checkbox" :checked="selectedIds.includes(snapshot.snapshotId)"
                  :aria-label="`${timeOf(snapshot.capturedAt)} を選択`" @change="toggleSelect(snapshot)"
                >
              </label>
              <div class="snapshot-thumb">
                <img
                  v-if="!brokenImages.includes(snapshot.snapshotId)"
                  class="snapshot-image" :src="imageUrl(snapshot.snapshotId)"
                  :alt="`${timeOf(snapshot.capturedAt)} の画像`" loading="lazy"
                  :style="{ aspectRatio: imageAspectRatio(snapshot) }"
                  @error="onImageError(snapshot.snapshotId)"
                >
                <span v-else class="snapshot-thumb__missing">
                  画像なし<br><small>{{ snapshot.imagePath }}</small>
                </span>
                <!-- 詳細（分析結果の修正）は画像の右下の編集アイコンから開く -->
                <button
                  type="button" class="btn btn--icon btn--sm snapshot-edit-button"
                  :data-snapshot-open="snapshot.snapshotId"
                  :title="`${timeOf(snapshot.capturedAt)} の詳細`"
                  :aria-label="`${timeOf(snapshot.capturedAt)} の詳細を開く`"
                  @click="openDetail(snapshot)"
                >
                  <AppIcon name="edit" size="sm" class="icon--edit" />
                </button>
              </div>
              <figcaption>
                <span class="sm-snapshot__time">{{ timeOf(snapshot.capturedAt) }}</span>
                <span class="badge" :class="resultBadgeClass(snapshot.resultCode)">
                  {{ resultLabel(snapshot.resultCode) }}
                </span>
              </figcaption>
            </figure>
          </div>
        </section>
      </div>
    </div>

    <!-- 分析結果の一括変更 -->
    <div v-if="bulkOpen" class="overlay">
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="smBulkTitle">
        <header class="dialog__head">
          <h2 id="smBulkTitle" class="dialog__title">分析結果を一括変更</h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="bulkOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </header>
        <div class="dialog__body">
          <p class="home-note">{{ selectedIds.length }} 件の画像を変更します。</p>
          <div class="filters">
            <div class="filters__row">
              <span class="filter-item">
                <span class="filter-item__label">分析結果：</span>
                <select v-model="bulkResult" class="select" aria-label="分析結果">
                  <option v-for="code in RESULT_CODES" :key="code" :value="code">{{ resultLabel(code) }}</option>
                </select>
              </span>
            </div>
            <div class="filters__row">
              <span class="filter-item filter-item--grow">
                <span class="filter-item__label">修正理由<span class="net-required">必須</span>：</span>
                <input v-model="bulkReason" class="input" type="text" maxlength="2000" placeholder="選択したすべての画像に保存する修正理由">
              </span>
            </div>
          </div>
        </div>
        <footer class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="bulkOpen = false">キャンセル</button>
          <button type="button" class="btn btn--primary" data-action="bulk-apply" @click="applyBulk">
            <AppIcon name="check" size="sm" /> 保存
          </button>
        </footer>
      </section>
    </div>

    <!-- スナップショットの詳細（2.0 と同じく、ここで 1 枚ずつ分析結果を直せる） -->
    <div v-if="detailOpen" class="overlay">
      <section class="dialog sm-detail-dialog" role="dialog" aria-modal="true" aria-labelledby="smDetailTitle">
        <header class="dialog__head">
          <h2 id="smDetailTitle" class="dialog__title">
            {{ timeOf(detailSnapshot?.capturedAt ?? null) }}
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="detailOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </header>
        <div class="dialog__body sm-detail-body">
          <!-- 画像（無いときはパスを出す。一覧と同じ扱い）。余白を作らず最大まで広げる -->
          <div class="snapshot-detail-image">
            <img
              v-if="detailSnapshot && !brokenImages.includes(detailSnapshot.snapshotId)"
              class="snapshot-image" :src="imageUrl(detailSnapshot.snapshotId)"
              :alt="`${timeOf(detailSnapshot.capturedAt)} の画像`"
              :style="{ aspectRatio: imageAspectRatio(detailSnapshot) }"
              @error="onImageError(detailSnapshot.snapshotId)"
            >
            <span v-else class="snapshot-thumb__missing">
              画像なし<br><small>{{ detailSnapshot?.imagePath ?? '' }}</small>
            </span>
          </div>

          <div class="sm-detail-info">
            <div class="sm-detail-meta">
              <span class="badge" :class="resultBadgeClass(detailSnapshot?.resultCode ?? null)">
                {{ resultLabel(detailSnapshot?.resultCode ?? null) }}
              </span>
              <span class="sm-detail-meta__state">
                状態 {{ ANALYSIS_STATE_LABELS[detailSnapshot?.analysisState ?? 'WAITING'] }}
                （{{ detailSnapshot?.manual ? '手動修正' : 'AI判定' }}）
                <template v-if="detailSnapshot?.confidence !== null && detailSnapshot?.confidence !== undefined">
                  ・確信度 {{ Math.round((detailSnapshot.confidence ?? 0) * 100) }}%
                </template>
              </span>
              <span class="sm-detail-meta__reason">
                <span class="home-item__head">理由・備考</span>{{ detailSnapshot?.reason ?? '—' }}
              </span>
              <span class="sm-detail-meta__file">{{ detailSnapshot?.videoFileName ?? '' }}</span>
            </div>

            <!-- 分析結果の修正（2.0 と同じく、分析済みの画像だけ直せる） -->
            <div v-if="detailSnapshot?.analysisState === 'COMPLETED'" class="filters sm-detail-form">
              <div class="filters__row">
                <span class="filter-item">
                  <span class="filter-item__label">分析結果：</span>
                  <select v-model="detailResult" class="select" aria-label="詳細の分析結果">
                    <option v-for="code in RESULT_CODES" :key="code" :value="code">{{ resultLabel(code) }}</option>
                  </select>
                </span>
              </div>
              <div class="filters__row">
                <span class="filter-item filter-item--grow">
                  <span class="filter-item__label">修正理由（任意）：</span>
                  <textarea
                    v-model="detailReason" class="input" rows="2" maxlength="2000"
                    aria-label="詳細の修正理由" placeholder="この画像に保存する修正理由（空でも保存できます）"
                  />
                </span>
              </div>
            </div>
            <p v-else class="home-note sm-detail-note">
              この画像は{{ ANALYSIS_STATE_LABELS[detailSnapshot?.analysisState ?? 'WAITING'] }}のため修正できません。
              分析済みの画像だけ修正できます。
            </p>
          </div>
        </div>
        <footer class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="detailOpen = false">閉じる</button>
          <button
            v-if="detailSnapshot?.analysisState === 'COMPLETED'"
            type="button" class="btn btn--primary" data-action="detail-apply" @click="applyDetail"
          >
            <AppIcon name="check" size="sm" /> 保存
          </button>
        </footer>
      </section>
    </div>

    <!-- 時間軸で確認（2.0 の activity-timeline） -->
    <div v-if="timelineOpen" class="overlay">
      <section class="dialog dialog--lg" role="dialog" aria-modal="true" aria-labelledby="smTimelineTitle">
        <header class="dialog__head">
          <h2 id="smTimelineTitle" class="dialog__title">時間軸で確認</h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="timelineOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </header>
        <div class="dialog__body">
          <p class="home-note">
            {{ filters.date }} {{ filters.timeFrom }}〜{{ filters.timeTo }} の {{ timelineSnapshots.length }} 枚を
            時刻の順に並べています（動画の絞り込みは掛けません）。
          </p>

          <div class="activity-timeline-wrap">
            <div class="activity-timeline" role="img" aria-label="分析結果の時間軸">
              <span v-if="timelineSegments.length === 0" class="activity-timeline-empty">
                該当する画像がありません。
              </span>
              <span
                v-for="segment in timelineSegments"
                v-else
                :key="segment.id"
                class="activity-segment"
                :style="{ ...timelineStyle(segment), background: segment.color }"
                :title="`${segment.time} ${segment.label}`"
                :data-timeline-segment="segment.id"
              />
            </div>
            <div class="activity-time-scale">
              <span
                v-for="tick in timelineTicks" :key="tick.seconds"
                class="activity-time-tick"
                :class="{ 'is-edge-start': tick.edge === 'start', 'is-edge-end': tick.edge === 'end' }"
                :style="{ left: `${tick.left.toFixed(3)}%` }"
              >{{ tick.label }}</span>
            </div>
            <div class="activity-timeline-legend">
              <span v-for="code in RESULT_CODES" :key="code">
                <i :style="{ background: RESULT_COLORS[code] }" />{{ resultLabel(code) }}
                {{ summary.resultCounts[code] ?? 0 }} 枚
              </span>
            </div>
            <div class="activity-timeline-list">
              <div v-for="segment in timelineSegments" :key="`row-${segment.id}`">
                <time>{{ segment.time }}</time>
                <i class="summary-result-item__swatch" :style="{ background: segment.color }" />
                <strong>{{ segment.label }}</strong>
              </div>
            </div>
          </div>
        </div>
        <footer class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="timelineOpen = false">閉じる</button>
        </footer>
      </section>
    </div>

    <!-- 注記（2.1 では取り込み・分析のバッチが未実装） -->
    <p class="toolbar-note">
      動画の取込（batL02）と AI 分析（batL03）は 2.1 では未実装のため、取り込み済みのデータを表示しています。
    </p>
  </div>
</template>

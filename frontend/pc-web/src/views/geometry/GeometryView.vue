<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, formatIsoDateTime, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import GeometryCreateDialog from '@/views/geometry/GeometryCreateDialog.vue'
import GeometryPrintDialog from '@/views/geometry/GeometryPrintDialog.vue'
import {
  FIGURE_TYPE_LABELS,
  FIGURE_TYPE_OPTIONS,
  deleteGeometryFigure,
  fetchGeometryTags,
  geometryThumbnailUrl,
  searchGeometryFigures,
  type GeometryFigure,
  type GeometryFigureType,
  type GeometrySort,
  type GeometryTag,
  type GeometryTotals
} from '@/api/geometry'
import {
  DEFAULT_GEOMETRY_AI_TASK_LIMIT,
  discardGeometryAiRequest,
  fetchGeometryAiOptions,
  fetchGeometryAiTasks,
  type GeometryAiCardStatus,
  type GeometryAiTaskRow
} from '@/api/geometry-ai'
import { paginationItems } from '@/features/pagination/pagination'
import '@/features/geometry/geometry.css'
import '@/features/geometry/geometry-ai.css'

/**
 * 図形管理【一覧画面】（メニュー「数学勉強」＞「図形管理」）。
 *
 * 2.0 の `geometry.jsp`（一覧・検索・削除・コピー・編集）を 2.1 のデザインで作り直した画面。
 * データは `GEO_図形情報`（2.0 の `TRN_図形作成情報` から移行済み）を API で読む。
 * 図形は GeoGebra の作図データとサムネイルを持ち、家族で共有する教材として扱う。
 *
 * ・検索条件（キーワード・タイプ）とタグ候補
 * ・AI 生図のタスク（送信するとすぐこの一覧へ戻り、**実際の処理段階**のカードとして出る）
 * ・図形カードの一覧（サムネイル・種別・登録区分・メモ・タグ・更新日）
 * ・カードごとの編集（作図画面へ）／コピー（コピーして開く）／印刷（小窓でプレビュー）／削除
 * ・【新規】は**作成方法の選択ダイアログ**を開く（手動で作図する／AI で作図する。利用者の指定）
 *
 * 削除済み・並び替えは画面に置かない（削除済みはサーバの既定＝ACTIVE のみ、
 * 並びは常に更新日の新しい順）。削除済みの件数だけはサマリに出す。
 *
 * 作図（GeoGebra エディタ）そのものは作図画面（`/{area}/geometry-draw`）が担当するため、
 * この画面は「新規作成」「編集」「コピーして開く」から画面遷移させるだけにする。
 * AI 生図（画像から作図）も同じで、送信済みのタスクはここから AI 生図の画面
 * （直して送り直す）か作図画面（作図を確認する）へ移すだけにする。
 */
const route = useRoute()
const router = useRouter()
const toast = useToast()

/** 画面のエリア（/student, /parent, /admin）。 */
const area = computed(() => route.path.split('/')[1] ?? 'student')

/** 1 ページに出す件数（図形管理は 24 / 48 / 96 件。利用者の指定）。 */
const PAGE_SIZES = [24, 48, 96]

/** 一覧の並び（画面に選ばせず、常に更新日の新しい順で読む。利用者の指定）。 */
const DEFAULT_SORT: GeometrySort = 'updatedDesc'

/** タグが 1 つも無いときの案内。 */
const NO_TAGS_MESSAGE = 'タグはまだありません。'

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/** 空の集計（初回読み込み前でもサマリを出せるようにする）。 */
function emptyTotals(): GeometryTotals {
  return { figureCount: 0, geometryCount: 0, functionCount: 0, deletedCount: 0 }
}

/* ---------- 検索条件 ---------- */
const filters = reactive({
  keyword: '',
  figureType: '' as '' | GeometryFigureType,
  /** タグ候補のクリックで選んだタグ（空なら絞り込みなし）。 */
  tag: ''
})

/* ---------- 図形一覧 ---------- */
const loading = ref(false)
const error = ref('')
const figures = ref<GeometryFigure[]>([])
const tags = ref<GeometryTag[]>([])
const totals = ref<GeometryTotals>(emptyTotals())
const page = ref(1)
const size = ref(PAGE_SIZES[0])
const totalElements = ref(0)
const totalPages = ref(0)
/** 更新系の処理中（二重送信を防ぐ）。 */
const busy = ref(false)
/** 印刷プレビューの小窓に出す図形（null なら閉じている）。 */
const printTarget = ref<GeometryFigure | null>(null)
/** 作成方法の選択ダイアログを開いているか（【新規】で開く）。 */
const createDialogOpen = ref(false)
/** AI 生図が使えるか（設定。null = まだ読めていない）。無効なら AI の導線を出さない。 */
const geometryAiEnabled = ref<boolean | null>(null)

const pageItems = computed(() => paginationItems(page.value, Math.max(1, totalPages.value)))

async function load(target = 1): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await searchGeometryFigures({
      keyword: filters.keyword.trim() === '' ? undefined : filters.keyword.trim(),
      figureType: filters.figureType === '' ? undefined : filters.figureType,
      tag: filters.tag === '' ? undefined : filters.tag,
      // 並び替えは画面に置かないので、常に既定（更新日の新しい順）で読む
      sort: DEFAULT_SORT,
      page: target,
      size: size.value
    })
    figures.value = response.data.items
    page.value = response.data.page
    totalPages.value = response.data.totalPages
    totalElements.value = response.data.totalElements
    totals.value = response.data.totals
  } catch (caught) {
    error.value = messageOf(caught, '図形の一覧を取得できませんでした。')
    figures.value = []
    totalElements.value = 0
    totalPages.value = 0
    totals.value = emptyTotals()
  } finally {
    loading.value = false
  }
}

/** タグ候補を取り直す（失敗しても一覧の表示は妨げない）。 */
async function loadTags(): Promise<void> {
  try {
    const response = await fetchGeometryTags()
    tags.value = response.data.items
  } catch {
    tags.value = []
  }
}

/** 一覧とタグ候補をまとめて取り直す。 */
async function reload(target = page.value): Promise<void> {
  await Promise.all([load(target), loadTags()])
}

function search(): void {
  void load(1)
}

function reset(): void {
  filters.keyword = ''
  filters.figureType = ''
  filters.tag = ''
  void load(1)
}

/** 1 ページの件数を変えたら 1 ページ目から読み直す。 */
function changeSize(): void {
  void load(1)
}

function goto(next: number): void {
  if (next < 1 || next > totalPages.value) return
  void load(next)
}

/** タグ候補のクリックで、そのタグの絞り込みを切り替える。 */
function toggleTag(tag: string): void {
  filters.tag = filters.tag === tag ? '' : tag
  void load(1)
}

/* ---------- カードの表示 ---------- */

/** 種別バッジの色（幾何図形=info / 関数グラフ=warning）。 */
function figureTypeBadgeClass(figure: GeometryFigure): string {
  return figure.figureType === 'function' ? 'badge badge--warning' : 'badge badge--info'
}

/** 登録区分のラベル（demo=初期データ / saved=利用者が作ったもの）。 */
function kindLabel(figure: GeometryFigure): string {
  return figure.kind === 'demo' ? '初期データ' : '自作'
}

function memoLabel(figure: GeometryFigure): string {
  const memo = figure.memo === null ? '' : figure.memo.trim()
  return memo === '' ? 'メモはありません。' : memo
}

function updatedLabel(figure: GeometryFigure): string {
  return figure.updatedAt === null ? '—' : formatIsoDateTime(figure.updatedAt)
}

/* ---------- 画面遷移（作図画面・AI 生図） ---------- */

/**
 * 新規作成。まず**作成方法の選択ダイアログ**を開く（手動で作図する／AI で作図する）。
 * ダイアログを挟むのは、【新規】の押し間違いで作図画面へ飛ばないようにするため。
 *
 * **AI 生図が無効（設定）のときは選択肢が 1 つだけになるので、ダイアログを出さずに
 * 作図画面を開く**（選ばせる意味が無いため）。
 */
function openCreateDialog(): void {
  if (geometryAiEnabled.value === false) {
    createFigure()
    return
  }
  createDialogOpen.value = true
}

/** 手動で作図する（現在の方法）。作図画面を空の状態で開く。 */
function createFigure(): void {
  createDialogOpen.value = false
  void router.push({ path: `/${area.value}/geometry-draw` })
}

/** AI で作図する（画像から）。AI 生図の画面を開く。 */
function createWithAi(): void {
  createDialogOpen.value = false
  void router.push({ path: `/${area.value}/geometry-ai` })
}

/** 編集。作図画面を指定した図形つきで開く。 */
function openFigure(figure: GeometryFigure): void {
  void router.push({
    path: `/${area.value}/geometry-draw`,
    query: { geometryId: String(figure.figureId) }
  })
}

/* ---------- コピー・印刷・削除 ---------- */

/**
 * コピー。ここでは複製せず、作図画面をコピー元つきで開く
 * （利用者が中身を確かめて直してから【保存】で新しい図形になる）。
 */
function copyFigure(figure: GeometryFigure): void {
  void router.push({
    path: `/${area.value}/geometry-draw`,
    query: { copyFrom: String(figure.figureId) }
  })
}

/** 印刷。画面を移らず、その図形の印刷プレビューを小窓で開く（利用者の指定）。 */
function openPrint(figure: GeometryFigure): void {
  printTarget.value = figure
}

async function removeFigure(figure: GeometryFigure): Promise<void> {
  if (busy.value) return
  if (!window.confirm(`「${figure.title}」を削除します。よろしいですか？`)) return
  busy.value = true
  try {
    const response = await deleteGeometryFigure(figure.figureId)
    toast.success(response.data.message)
    await reload()
  } catch (caught) {
    toast.danger(messageOf(caught, '図形を削除できませんでした。'))
  } finally {
    busy.value = false
  }
}

/**
 * 印刷プレビューから戻ったときに絞り込みを戻す（クエリに検索条件を持たせている）。
 * 空のクエリなら今までどおり全件。
 */
function applyQueryFilters(): void {
  const query = route.query
  filters.keyword = typeof query.keyword === 'string' ? query.keyword : ''
  filters.figureType = typeof query.figureType === 'string'
    ? (query.figureType as '' | GeometryFigureType)
    : ''
  filters.tag = typeof query.tag === 'string' ? query.tag : ''
}

/* ---------- AI 生図のタスク（送信済みの要求をこの一覧に出す） ---------- */

/**
 * カードに出す AI 生図のタスク（新しい順。0 件ならセクションごと出さない）。
 *
 * 図形として保存済（REGISTERED）は API が返さない（図形一覧のカードとして出るので重複しない）。
 */
const aiTasks = ref<GeometryAiTaskRow[]>([])
/** タスクの削除中か（二重に押させない。図形の操作とは別に持つ）。 */
const aiTaskBusy = ref(false)
/** タスクの取得に失敗したか（**図形一覧は壊さない**。小さな案内だけ出す）。 */
const aiTasksFailed = ref(false)
/** タスク一覧に出す上限（サーバーの既定と同じ 20 件）。 */
const AI_TASK_LIMIT = DEFAULT_GEOMETRY_AI_TASK_LIMIT
/** 一覧のタスクを取り直す間隔（処理中のカードがある間だけ。％は無いので段階で見せる）。 */
const AI_TASK_POLL_INTERVAL_MS = 3_000
/**
 * 処理中のカードの状態（この状態が 1 つでもある間だけポーリングする）。
 * NEEDS_INPUT（追加入力待ち）／FAILED／READY（確認待ち）／SAVED（保存完了）は
 * 利用者の操作待ちなので、こちらからは進まない。
 */
const AI_TASK_PROCESSING: GeometryAiCardStatus[] =
  ['WAITING', 'READING', 'GENERATING', 'VALIDATING']
/** ポーリングのタイマー（null = 止まっている）。多重に開始しないための持ち物。 */
let aiTasksTimer: number | null = null

/** 処理中のカードが 1 つでもあるか。 */
const hasProcessingTask = computed(() => aiTasks.value.some(isProcessingTask))

function isProcessingTask(task: GeometryAiTaskRow): boolean {
  return AI_TASK_PROCESSING.includes(task.cardStatus)
}

/** カードが【送り直す】【作図を確認する】を出せる状態か（それ以外は処理中）。 */
function canResumeTask(task: GeometryAiTaskRow): boolean {
  return task.cardStatus === 'NEEDS_INPUT' || task.cardStatus === 'FAILED'
}

/** カードの状態バッジの色（状態ごとに既存の badge クラスから選ぶ）。 */
function taskBadgeClass(task: GeometryAiTaskRow): string {
  const tone: Record<GeometryAiCardStatus, string> = {
    WAITING: 'badge--neutral',
    READING: 'badge--info',
    GENERATING: 'badge--info',
    VALIDATING: 'badge--warning',
    NEEDS_INPUT: 'badge--warning',
    READY: 'badge--info',
    SAVED: 'badge--success',
    FAILED: 'badge--danger'
  }
  return `badge ${tone[task.cardStatus] ?? 'badge--neutral'}`
}

/** 指定した、作成する図の種類の日本語（AUTO はおまかせ）。 */
const OUTPUT_TYPE_LABELS: Record<string, string> = {
  AUTO: 'おまかせ（AI が判別）',
  GEOMETRY: '幾何図形',
  GRAPH: '関数・方程式のグラフ',
  MIXED: '図形とグラフの組み合わせ'
}

function requestedTypeLabel(task: GeometryAiTaskRow): string {
  return OUTPUT_TYPE_LABELS[task.requestedOutputType] ?? task.requestedOutputType
}

/** 実際に作る種類の日本語（決まっていなければ null = 併記しない）。 */
function resolvedTypeLabel(task: GeometryAiTaskRow): string | null {
  const resolved = task.resolvedOutputType
  if (resolved === null) return null
  return OUTPUT_TYPE_LABELS[resolved] ?? resolved
}

/** 作図方法（古い要求はモードが無いので、画像から作図とみなす）。 */
function taskModeLabel(task: GeometryAiTaskRow): string {
  const label = task.modeLabel === null ? '' : task.modeLabel.trim()
  return label === '' ? '（旧）画像から作図' : label
}

function descriptionOf(task: GeometryAiTaskRow): string {
  return task.description === null ? '' : task.description.trim()
}

/** 失敗の理由（日本語のまま出す。無ければ空 = 出さない）。 */
function errorOf(task: GeometryAiTaskRow): string {
  return task.errorMessage === null ? '' : task.errorMessage.trim()
}

/** 更新日時（他のカードと同じ書き方）。 */
function taskUpdatedLabel(task: GeometryAiTaskRow): string {
  return task.updatedAt === null ? '—' : formatIsoDateTime(task.updatedAt)
}

/** ポーリングを止める（画面離脱・処理中が 0 件になったとき）。 */
function stopAiTaskPolling(): void {
  if (aiTasksTimer !== null) {
    window.clearInterval(aiTasksTimer)
    aiTasksTimer = null
  }
}

/**
 * 処理中のカードが 1 つでもあるときだけ 3 秒間隔のポーリングを始める。
 * **すでに動いているときは何もしない**（多重起動しない）。
 */
function startAiTaskPolling(): void {
  if (aiTasksTimer !== null) return
  aiTasksTimer = window.setInterval(() => {
    if (!hasProcessingTask.value) {
      stopAiTaskPolling()
      return
    }
    void loadAiTasks()
  }, AI_TASK_POLL_INTERVAL_MS)
}

/**
 * タスクを取り直す。
 *
 * 失敗しても**図形一覧は壊さない**（ポーリング中の失敗は握りつぶして、次の周期で取り直す）。
 * 処理中が 0 件になったらポーリングを止める。
 */
async function loadAiTasks(): Promise<void> {
  try {
    const response = await fetchGeometryAiTasks(AI_TASK_LIMIT)
    const items = response.data.items
    aiTasks.value = Array.isArray(items) ? items : []
    aiTasksFailed.value = false
  } catch {
    // 初回の失敗だけ小さく案内する（すでに出ていたカードは消さない）
    if (aiTasks.value.length === 0) aiTasksFailed.value = true
    stopAiTaskPolling()
    return
  }
  if (hasProcessingTask.value) {
    startAiTaskPolling()
  } else {
    stopAiTaskPolling()
  }
}

/**
 * 【内容を直して送り直す】。AI 生図の画面を要求 ID つきで開く
 * （画像はもう一度選び直さずに、質問への回答や条件の修正だけを行う）。
 */
function resumeTask(task: GeometryAiTaskRow): void {
  void router.push({
    path: `/${area.value}/geometry-ai`,
    query: { requestId: String(task.requestId) }
  })
}

/** 【作図を確認する】。作図画面を要求 ID つきで開く（確認・手直ししてから保存する）。 */
function openTaskDrawing(task: GeometryAiTaskRow): void {
  void router.push({
    path: `/${area.value}/geometry-draw`,
    query: { geometryAiRequestId: String(task.requestId) }
  })
}

/**
 * 【削除】。タスクカードを**一覧から消す**（状態を取消にする。記録はサーバーに残る）。
 *
 * <p>図形の削除と同じく**確認してから**消す（生成済みの作図は、消すと作り直しになるため）。
 * 図形として保存済みのものは消せない（図形一覧から削除する。サーバーも断る）。</p>
 */
async function discardTask(task: GeometryAiTaskRow): Promise<void> {
  if (aiTaskBusy.value) return
  if (!window.confirm(`AI 生図のタスク「${task.requestNo}」を削除します。よろしいですか？`
    + '（一覧から消えます。作図がまだ保存されていない場合は作り直しになります）')) {
    return
  }
  aiTaskBusy.value = true
  try {
    const response = await discardGeometryAiRequest(task.requestId, task.version)
    toast.success(response.message)
    await loadAiTasks()
  } catch (caught) {
    toast.danger(messageOf(caught, 'AI 生図のタスクを削除できませんでした。'))
    // 他の端末で先に消えたときは、一覧を取り直して状態を合わせる
    await loadAiTasks()
  } finally {
    aiTaskBusy.value = false
  }
}

onMounted(() => {
  applyQueryFilters()
  void reload(1)
  // AI 生図が使えるかを設定から読む（読めなくても一覧は使える。AI の導線だけを控えめにする）
  void fetchGeometryAiOptions()
    .then((response) => { geometryAiEnabled.value = response.data.enabled })
    .catch(() => { geometryAiEnabled.value = null })
  // 送信済みの AI 生図のタスクを、図形の一覧と同じタイミングで読む（失敗しても一覧は使える）
  void loadAiTasks()
})

onBeforeUnmount(() => {
  // 画面を離れたらポーリングを必ず止める（裏で叩き続けない）
  stopAiTaskPolling()
})
</script>

<template>
  <div class="gm-page">
    <!-- 検索条件（2.0 の絞り込み＋タグ候補） -->
    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--primary" data-gm-search :disabled="loading" @click="search">
            <AppIcon name="search" size="sm" /> 検索
          </button>
          <button type="button" class="btn btn--primary" data-gm-create @click="openCreateDialog">
            <AppIcon name="plus" size="sm" /> 新規
          </button>
          <button type="button" class="btn btn--secondary" data-gm-reset :disabled="loading" @click="reset">
            <AppIcon name="rotate" size="sm" /> リセット
          </button>
        </div>
      </div>
      <div class="filters">
        <div class="filters__row">
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="filters.keyword" class="input" type="search" data-gm-filter="keyword"
              placeholder="図形名、メモ、タグ" @keyup.enter="search"
            >
          </span>
          <span class="filter-item">
            <span class="filter-item__label">タイプ：</span>
            <select v-model="filters.figureType" class="select" data-gm-filter="figureType">
              <option value="">すべて</option>
              <option v-for="option in FIGURE_TYPE_OPTIONS" :key="option" :value="option">
                {{ FIGURE_TYPE_LABELS[option] }}
              </option>
            </select>
          </span>
        </div>
        <!-- タグ候補（もう一度押すと解除） -->
        <div class="gm-tags" data-gm-tag-list>
          <span class="filter-item__label">タグ：</span>
          <template v-if="tags.length > 0">
            <button
              v-for="tag in tags" :key="tag.tag" type="button" class="gm-tag-chip"
              :class="{ 'is-active': filters.tag === tag.tag }" :data-gm-tag-chip="tag.tag"
              @click="toggleTag(tag.tag)"
            >
              {{ tag.tag }}<span class="gm-tag-chip__count">{{ tag.count }}</span>
            </button>
          </template>
          <span v-else class="gm-hint" data-gm-tag-empty>{{ NO_TAGS_MESSAGE }}</span>
        </div>
      </div>
    </div>

    <!-- タスクの取得に失敗しても、図形一覧は今までどおり使える（小さな案内だけ出す） -->
    <section v-if="aiTasksFailed && aiTasks.length === 0" class="card">
      <div class="card__header gm-list-head">
        <h2 class="card__title"><AppIcon name="wand" size="sm" /> AI 生図のタスク</h2>
      </div>
      <p class="gm-ai-tasks__note" data-gm-ai-tasks-error>
        AI 生図のタスクを取得できませんでした。図形一覧はそのままご利用いただけます。
      </p>
    </section>

    <!-- AI 生図のタスク（送信するとすぐこの一覧に戻り、実際の処理段階のカードとして出る） -->
    <section v-else-if="aiTasks.length > 0" class="card" data-gm-ai-tasks>
      <div class="card__header gm-list-head">
        <h2 class="card__title"><AppIcon name="wand" size="sm" /> AI 生図のタスク</h2>
        <span class="gm-card__meta" data-gm-ai-tasks-summary>全 {{ aiTasks.length }} 件</span>
      </div>

      <div class="gm-ai-tasks">
        <article
          v-for="task in aiTasks" :key="task.requestId" class="gm-ai-task"
          :data-gm-ai-task="task.requestId" :data-gm-ai-task-status="task.cardStatus"
        >
          <div class="gm-ai-task__head">
            <span :class="taskBadgeClass(task)" data-gm-ai-task-status-label>{{ task.cardStatusLabel }}</span>
            <span class="gm-card__no" data-gm-ai-task-no>{{ task.requestNo }}</span>
          </div>

          <h3 v-if="task.title !== null && task.title.trim() !== ''" class="gm-ai-task__title" data-gm-ai-task-title>
            {{ task.title }}
          </h3>
          <p v-if="descriptionOf(task) !== ''" class="gm-ai-task__text" data-gm-ai-task-description>
            {{ descriptionOf(task) }}
          </p>

          <dl class="gm-ai-task__rows">
            <div class="gm-ai-task__row">
              <dt>作図方法</dt>
              <dd data-gm-ai-task-mode>{{ taskModeLabel(task) }}</dd>
            </div>
            <div class="gm-ai-task__row">
              <dt>作成する図の種類</dt>
              <dd data-gm-ai-task-output>{{ requestedTypeLabel(task) }}</dd>
            </div>
            <div class="gm-ai-task__row">
              <dt>実際の処理段階</dt>
              <!-- パーセントは出さない（実際の段階だけを出す） -->
              <dd data-gm-ai-task-stage>{{ task.statusLabel }}</dd>
            </div>
          </dl>

          <p v-if="resolvedTypeLabel(task) !== null" class="gm-ai-task__resolved" data-gm-ai-task-resolved>
            作る種類：{{ resolvedTypeLabel(task) }}
          </p>
          <p v-if="task.cardStatus === 'NEEDS_INPUT'" class="gm-ai-task__questions" data-gm-ai-task-questions>
            AI からの質問 {{ task.questionCount }} 件
          </p>
          <p v-if="errorOf(task) !== ''" class="gm-ai-task__error" data-gm-ai-task-error>
            {{ errorOf(task) }}
          </p>

          <!--
            操作は**アイコンだけ**（カードが狭いので文字は入らない。意味は title と aria-label で伝える。
            図形カードの操作と同じ規則）。
          -->
          <div class="gm-ai-task__foot">
            <span class="gm-ai-task__updated" data-gm-ai-task-updated>更新 {{ taskUpdatedLabel(task) }}</span>
            <div class="gm-ai-task__actions">
              <button
                v-if="canResumeTask(task)" type="button" class="btn btn--icon btn--sm"
                title="内容を直して送り直す" aria-label="内容を直して送り直す"
                data-gm-ai-task-resume @click="resumeTask(task)"
              >
                <AppIcon name="rotate" size="sm" />
              </button>
              <button
                v-else-if="task.cardStatus === 'READY'" type="button" class="btn btn--icon btn--sm"
                title="作図を確認する" aria-label="作図を確認する"
                data-gm-ai-task-open @click="openTaskDrawing(task)"
              >
                <AppIcon name="eye" size="sm" />
              </button>
              <!-- 保存まで終わったもの（サーバーはいま一覧に返さないが、返しても文言が崩れないように） -->
              <button
                v-else-if="task.cardStatus === 'SAVED'" type="button" class="btn btn--icon btn--sm"
                title="保存した図形を開く" aria-label="保存した図形を開く"
                data-gm-ai-task-open @click="openTaskDrawing(task)"
              >
                <AppIcon name="check-square" size="sm" />
              </button>
              <!-- 処理中は何も出さない（押しても進めないため） -->
              <span v-else class="gm-ai-task__wait">処理が終わるとここから確認できます。</span>

              <!-- 一覧から消す（状態を取消にする。行はサーバーに残る） -->
              <button
                type="button" class="btn btn--icon btn--sm is-danger" :disabled="aiTaskBusy"
                :title="`AI 生図のタスク「${task.requestNo}」を削除`"
                :aria-label="`AI 生図のタスク「${task.requestNo}」を削除`"
                data-gm-ai-task-delete @click="discardTask(task)"
              >
                <AppIcon name="trash" size="sm" />
              </button>
            </div>
          </div>
        </article>
      </div>
    </section>

    <!-- 図形一覧 -->
    <section class="card">
      <div class="card__header gm-list-head">
        <h2 class="card__title"><AppIcon name="list" size="sm" /> 図形一覧</h2>
        <span class="gm-card__meta" data-gm-summary>全 {{ totals.figureCount }} 件 ・ 幾何 {{ totals.geometryCount }} ・ 関数 {{ totals.functionCount }} ・ 削除済 {{ totals.deletedCount }}</span>
      </div>

      <p v-if="error" class="alert alert--danger">{{ error }}</p>
      <p v-else-if="loading" class="gm-page__loading">読み込んでいます...</p>
      <p v-else-if="figures.length === 0" class="gm-page__empty" data-gm-empty>
        該当する図形がありません。【新規】から作図できます。
      </p>

      <div v-else class="gm-grid" data-gm-grid>
        <article
          v-for="figure in figures" :key="figure.figureId" class="gm-card"
          :data-gm-card="figure.figureId"
        >
          <div class="gm-card__thumb">
            <img
              v-if="figure.hasThumbnail"
              :src="geometryThumbnailUrl(figure.figureId, figure.version)" :alt="figure.title"
              data-gm-thumb loading="lazy"
            >
            <div v-else class="gm-card__thumb-empty" data-gm-thumb-empty>
              <AppIcon name="image" size="lg" />
              <span>サムネイルなし</span>
            </div>
          </div>

          <div class="gm-card__body">
            <div class="gm-card__head">
              <h3 class="gm-card__title" data-gm-title>{{ figure.title }}</h3>
              <div class="gm-card__badges">
                <span :class="figureTypeBadgeClass(figure)" data-gm-type-badge>
                  {{ FIGURE_TYPE_LABELS[figure.figureType] }}
                </span>
                <span class="badge badge--neutral" data-gm-kind-badge>{{ kindLabel(figure) }}</span>
              </div>
            </div>

            <div class="gm-card__no" data-gm-no>{{ figure.figureNo }}</div>
            <p class="gm-card__memo" data-gm-memo>{{ memoLabel(figure) }}</p>

            <div class="gm-card__tags" data-gm-tags>
              <template v-if="figure.tags.length > 0">
                <span v-for="tag in figure.tags" :key="tag" class="gm-tag-chip">{{ tag }}</span>
              </template>
              <span v-else class="gm-tag-chip">タグなし</span>
            </div>

            <div class="gm-card__meta" data-gm-meta>更新 {{ updatedLabel(figure) }}</div>
          </div>

          <!-- 操作はアイコンだけ（カードが狭いので文字は入らない。意味は title と aria-label で伝える） -->
          <div class="gm-card__actions">
            <button
              type="button" class="btn btn--icon btn--sm"
              :title="`「${figure.title}」を編集`" :aria-label="`「${figure.title}」を編集`"
              data-gm-edit @click="openFigure(figure)"
            >
              <AppIcon name="edit" size="sm" />
            </button>
            <button
              type="button" class="btn btn--icon btn--sm"
              :title="`「${figure.title}」をコピー`" :aria-label="`「${figure.title}」をコピー`"
              data-gm-copy @click="copyFigure(figure)"
            >
              <AppIcon name="copy" size="sm" />
            </button>
            <!-- 印刷は画面を移らず、この図形のプレビューを小窓で開く -->
            <button
              type="button" class="btn btn--icon btn--sm"
              :title="`「${figure.title}」を印刷`" :aria-label="`「${figure.title}」を印刷`"
              :data-gm-print="figure.figureId" @click="openPrint(figure)"
            >
              <AppIcon name="print" size="sm" />
            </button>
            <button
              type="button" class="btn btn--icon btn--sm is-danger" :disabled="busy"
              :title="`「${figure.title}」を削除`" :aria-label="`「${figure.title}」を削除`"
              data-gm-delete @click="removeFigure(figure)"
            >
              <AppIcon name="trash" size="sm" />
            </button>
          </div>
        </article>
      </div>

      <div v-if="!loading && totalElements > 0" class="pagination">
        <span class="pagination__info">全 {{ totalElements }} 件（{{ page }} / {{ totalPages }} ページ）</span>
        <!-- 件数はページングの左隣に置く（画面共通のルール） -->
        <label class="pagination__size">
          <span>件数</span>
          <select v-model.number="size" class="select" aria-label="1ページの件数" data-gm-page-size @change="changeSize">
            <option v-for="option in PAGE_SIZES" :key="option" :value="option">{{ option }} 件</option>
          </select>
        </label>
        <div class="pagination__pages">
          <button type="button" class="page-btn" :disabled="page <= 1" @click="goto(page - 1)">‹</button>
          <template v-for="(item, key) in pageItems" :key="key">
            <span v-if="item === 'gap'" class="page-gap">…</span>
            <button
              v-else type="button" class="page-btn" :class="{ 'is-active': item === page }"
              :data-gm-page="item" @click="goto(item)"
            >
              {{ item }}
            </button>
          </template>
          <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goto(page + 1)">›</button>
        </div>
      </div>
    </section>

    <!-- 作成方法の選択（【新規】で開く。手動で作図する／AI で作図する） -->
    <GeometryCreateDialog
      v-if="createDialogOpen" :ai-enabled="geometryAiEnabled" @close="createDialogOpen = false"
      @manual="createFigure" @ai="createWithAi"
    />

    <!-- 印刷プレビューの小窓（カードの【印刷】で開く。画面を移らない） -->
    <GeometryPrintDialog
      v-if="printTarget !== null" :figure="printTarget"
      @close="printTarget = null"
    />
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  clearEssayList,
  ESSAY_PAGE_SIZE,
  ESSAY_PAGE_SIZES,
  essayList,
  essayTotal,
  essayTotalPages,
  latestGrading,
  listStateOf,
  loadEssays,
  removeEssay,
  type EssayFilter,
  type EssayListItem,
  type EssayListState
} from '@/features/english-essay/store'
import { paginationItems } from '@/features/pagination/pagination'
import { ESSAY_LEVELS, ESSAY_LEVEL_LABELS, type EssayLevel } from '@/features/english-essay/types'
import '@/features/english-essay/essay-list.css'

/**
 * 英作文AI添削の一覧（2.0 の `english_essay.jsp` の検索条件と一覧表）。
 *
 * <p>検索条件はキーワード・英検級・登録日（開始／終了）の 3 つで、2.0 と同じ並び。一覧の
 * 1 行は 画像枚数／題／英検級／語数／得点／状態／登録日。操作は 目（詳細）・編集・削除。</p>
 *
 * <p>データは user-api（`GET /api/user/english-essays`）。**絞り込みもページングもサーバー側**なので、
 * 画面は条件とページ番号を送って、返ってきた 1 ページをそのまま出す。得点と状態は
 * `latestGrading`（`SUCCEEDED` なら得点、`QUEUED`／`RUNNING` なら「添削中」、`FAILED` なら「失敗」、
 * 無ければ「未添削」）で決める。</p>
 */

const route = useRoute()
const router = useRouter()
const toast = useToast()

/** 画面が今見ている絞り込み（【検索】を押したときだけ反映する）。 */
const filters = reactive<{ keyword: string; level: EssayLevel | ''; dateFrom: string; dateTo: string }>({
  keyword: '',
  level: '',
  dateFrom: '',
  dateTo: ''
})

/** 適用済みの条件（検索ボタンで `filters` から写す）。 */
const applied = ref<EssayFilter>({})

/** いまのページ（1 から）。 */
const page = ref(1)

/** 1 ページの件数（20 / 50 / 100。既定は 20。`docs/FRONTEND_GUIDE.md` §3.10）。 */
const size = ref<number>(ESSAY_PAGE_SIZE)

/** 一覧の中身（store のキャッシュをそのまま出す。読み直すと入れ替わる）。 */
const essays = computed<EssayListItem[]>(() => essayList.value)

/** 絞り込みに一致する総件数（サーバーが数える）。 */
const count = computed<number>(() => essayTotal.value)

const totalPages = computed<number>(() => essayTotalPages.value)

/** ページ番号（多いときは「1 … 8 9 10 … 20」に省略する。画面共通の作り）。 */
const pageItems = computed(() => paginationItems(page.value, Math.max(1, totalPages.value)))

/** 遷移先の系統（/admin・/student・/parent）。 */
const area = computed(() => route.path.split('/')[1] ?? 'student')

/**
 * この系統で**提出・編集**ができるか。
 *
 * <p>親は「子 結果の閲覧」だけ（`docs/PERMISSION_MATRIX.md`）。新規・編集・削除は出さない
 * （見るだけの画面）。</p>
 */
const canSubmit = computed(() => area.value !== 'parent')

/** 一覧の状態の表示（未添削／添削中／添削済み／失敗）。 */
const STATE_LABELS: Record<EssayListState, string> = {
  NONE: '未添削',
  PENDING: '添削中',
  SUCCEEDED: '添削済み',
  FAILED: '失敗'
}

const STATE_BADGES: Record<EssayListState, string> = {
  NONE: 'badge--neutral',
  PENDING: 'badge--info',
  SUCCEEDED: 'badge--success',
  FAILED: 'badge--danger'
}

function messageOf(cause: unknown, fallback: string): string {
  return cause instanceof ApiError ? cause.message : fallback
}

/** いまの条件・ページ・件数で読み直す（削除でページが空になったら 1 つ前に戻す）。 */
async function reload(): Promise<void> {
  try {
    await loadEssays(applied.value, page.value, size.value)
    if (essays.value.length === 0 && page.value > totalPages.value) {
      page.value = totalPages.value
      await loadEssays(applied.value, page.value, size.value)
    }
  } catch (caught) {
    clearEssayList()
    toast.danger(messageOf(caught, '英作文の一覧を取得できませんでした。'))
  }
}

/** ページを変えて読み直す（同じページなら何もしない）。 */
function goPage(next: number): void {
  const target = Math.min(Math.max(1, next), totalPages.value)
  if (target === page.value) {
    return
  }
  page.value = target
  void reload()
}

/** 1 ページの件数を変えたら 1 ページ目から読み直す。 */
function changeSize(): void {
  page.value = 1
  void reload()
}

function search(): void {
  applied.value = {
    keyword: filters.keyword,
    level: filters.level,
    dateFrom: filters.dateFrom,
    dateTo: filters.dateTo
  }
  page.value = 1
  void reload()
}

function reset(): void {
  filters.keyword = ''
  filters.level = ''
  filters.dateFrom = ''
  filters.dateTo = ''
  search()
}

/** 登録日（2.0 の一覧と同じ `YYYY/MM/DD`）。 */
function formatDate(iso: string): string {
  return iso.slice(0, 10).replaceAll('-', '/')
}

/**
 * 得点（成功した添削だけ。未添削・添削中・失敗は「—」）。
 *
 * <p>満点が分かるときだけ `26 / 32`。分からない回（2.0 の古い形式の移行データ）は点数だけを
 * 出す（`0` や `—` を満点の代わりに出さない）。</p>
 */
function scoreOf(essay: EssayListItem): string {
  const grading = latestGrading(essay)
  if (grading === null || grading.statusCode !== 'SUCCEEDED' || grading.score === null) {
    return '—'
  }
  return grading.maxScore === null ? String(grading.score) : `${grading.score} / ${grading.maxScore}`
}

function stateLabel(essay: EssayListItem): string {
  return STATE_LABELS[listStateOf(essay)]
}

function stateBadge(essay: EssayListItem): string {
  return STATE_BADGES[listStateOf(essay)]
}

function categoryLabel(essay: EssayListItem): string {
  return `設問 ${essay.questionImageCount} / 答案 ${essay.answerImageCount}`
}

function openNew(): void {
  void router.push(`/${area.value}/english-essay/new`)
}

function openEdit(essay: EssayListItem): void {
  void router.push(`/${area.value}/english-essay/${essay.id}/edit`)
}

function detailPath(id: string): string {
  return `/${area.value}/english-essay/${id}`
}

function openDetail(essay: EssayListItem): void {
  void router.push(detailPath(essay.id))
}

async function remove(essay: EssayListItem): Promise<void> {
  if (!window.confirm(`「${essay.title}」を一覧から削除しますか？`)) {
    return
  }
  try {
    await removeEssay(essay.id)
    await reload()
    toast.success('英作文を削除しました。')
  } catch (caught) {
    toast.danger(messageOf(caught, '英作文を削除できませんでした。'))
  }
}

onMounted(() => {
  void reload()
})
</script>

<template>
  <div class="ee-page">
    <!-- 検索条件（キーワード・英検級・登録日） -->
    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
        <div class="search-panel__actions">
          <!-- 親は閲覧のみ（提出しない）ので【英作文新規】を出さない -->
          <button v-if="canSubmit" type="button" class="btn btn--primary" data-ee-new @click="openNew">
            <AppIcon name="plus" size="sm" /> 英作文新規
          </button>
          <button type="button" class="btn btn--primary" data-ee-search @click="search">
            <AppIcon name="search" size="sm" /> 検索
          </button>
          <button type="button" class="btn btn--secondary" data-ee-reset @click="reset">
            <AppIcon name="rotate" size="sm" /> リセット
          </button>
        </div>
      </div>
      <div class="filters">
        <div class="filters__row">
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="filters.keyword" class="input" type="search" style="flex: 1"
              placeholder="タイトル・設問・作文本文" data-ee-filter="keyword" @keyup.enter="search"
            >
          </span>
          <span class="filter-item">
            <span class="filter-item__label">英検級：</span>
            <select v-model="filters.level" class="select" data-ee-filter="level" aria-label="英検級">
              <option value="">すべて</option>
              <option v-for="level in ESSAY_LEVELS" :key="level" :value="level">
                {{ ESSAY_LEVEL_LABELS[level] }}
              </option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">登録日：</span>
            <span class="range-input">
              <input v-model="filters.dateFrom" class="input" type="date" data-ee-filter="dateFrom" aria-label="登録日（開始）">
              <span class="range-input__sep" aria-hidden="true">～</span>
              <input v-model="filters.dateTo" class="input" type="date" data-ee-filter="dateTo" aria-label="登録日（終了）">
            </span>
          </span>
        </div>
      </div>
    </div>

    <!-- 英作文一覧 -->
    <section class="card table-section">
      <div class="table-section__head card__header">
        <h2 class="table-section__title card__title"><AppIcon name="list" size="sm" /> 英作文一覧</h2>
        <span class="table-section__meta cell-muted" data-ee-count>全 {{ count }} 件</span>
      </div>

      <p v-if="count === 0" class="ee-empty" data-ee-empty>条件に一致する英作文はありません。</p>

      <template v-else>
        <div class="table-wrap">
          <table class="data-table ee-table">
            <thead>
              <tr>
                <th class="col-actions">操作</th>
                <th>No.</th>
                <th>登録日</th>
                <th>英検級</th>
                <th>題 / 設問</th>
                <th>画像枚数</th>
                <th>語数</th>
                <th>得点</th>
                <th>状態</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(essay, index) in essays" :key="essay.id" :data-ee-row="essay.id">
                <td class="row-actions ee-row-actions">
                  <span class="ee-row-actions__grid">
                    <button
                      type="button" class="btn btn--icon btn--sm" title="詳細" aria-label="詳細"
                      data-ee-view @click="openDetail(essay)"
                    >
                      <AppIcon name="eye" size="sm" class="icon--view" />
                    </button>
                    <!-- 編集・削除は提出する系統（管理者・生徒）だけ。親は閲覧のみ -->
                    <button
                      v-if="canSubmit"
                      type="button" class="btn btn--icon btn--sm" title="編集" aria-label="編集"
                      data-ee-edit @click="openEdit(essay)"
                    >
                      <AppIcon name="edit" size="sm" class="icon--edit" />
                    </button>
                    <button
                      v-if="canSubmit"
                      type="button" class="btn btn--icon btn--sm is-danger" title="削除" aria-label="削除"
                      data-ee-delete @click="remove(essay)"
                    >
                      <AppIcon name="trash" size="sm" class="icon--danger" />
                    </button>
                  </span>
                </td>
                <td class="cell-muted">{{ (page - 1) * size + index + 1 }}</td>
                <td class="cell-muted">{{ formatDate(essay.createdAt) }}</td>
                <td><span class="badge badge--primary">{{ ESSAY_LEVEL_LABELS[essay.level] }}</span></td>
                <td>
                  <span class="ee-row-title cell-strong" :title="essay.title">{{ essay.title }}</span>
                  <span class="ee-row-question cell-muted" :title="essay.questionText">{{ essay.questionText }}</span>
                </td>
                <td class="ee-cell-center">
                  <AppIcon name="image" size="sm" /> {{ essay.imageCount }}
                  <span class="ee-cell-sub cell-muted">{{ categoryLabel(essay) }}</span>
                </td>
                <td class="ee-cell-center">{{ essay.wordCount }}</td>
                <td class="ee-cell-center ee-score">{{ scoreOf(essay) }}</td>
                <td class="ee-cell-center">
                  <span class="badge" :class="stateBadge(essay)">
                    {{ stateLabel(essay) }}
                  </span>
                </td>
              </tr>
            </tbody>
          </table>
        </div>

        <!-- 行が 1 件以上あれば**いつも**出す（1 ページしか無くても件数は変えられる） -->
        <div v-if="essays.length > 0" class="pagination">
          <span class="pagination__info">全 {{ count }} 件（{{ page }} / {{ totalPages }} ページ）</span>
          <!-- 件数はページングの左隣に置く（画面共通のルール） -->
          <label class="pagination__size">
            <span>件数</span>
            <select
              v-model.number="size" class="select" aria-label="1ページの件数"
              data-ee-page-size @change="changeSize"
            >
              <option v-for="option in ESSAY_PAGE_SIZES" :key="option" :value="option">{{ option }} 件</option>
            </select>
          </label>
          <div class="pagination__pages">
            <button type="button" class="page-btn" :disabled="page <= 1" aria-label="前のページ" @click="goPage(page - 1)">
              ‹
            </button>
            <template v-for="(item, key) in pageItems" :key="key">
              <span v-if="item === 'gap'" class="page-gap">…</span>
              <button
                v-else type="button" class="page-btn" :class="{ 'is-active': item === page }"
                @click="goPage(item)"
              >
                {{ item }}
              </button>
            </template>
            <button
              type="button" class="page-btn" :disabled="page >= totalPages" aria-label="次のページ"
              @click="goPage(page + 1)"
            >
              ›
            </button>
          </div>
        </div>
      </template>
    </section>
  </div>
</template>

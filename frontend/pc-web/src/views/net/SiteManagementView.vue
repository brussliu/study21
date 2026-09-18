<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { ApiError, formatIsoDate, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import {
  approveSite,
  createSite,
  deleteSite,
  rejectSite,
  searchSites,
  updateSite,
  type SiteCategoryCode,
  type SiteKindCode,
  type SiteRow,
  type JudgeMethodCode
} from '@/api/net'
import { paginationItems } from '@/features/pagination/pagination'
import {
  APPROVAL_LABELS,
  CATEGORY_LABELS,
  JUDGE_METHOD_LABELS,
  KIND_LABELS,
  STATUS_LABELS,
  approvalBadgeClass,
  categoryText,
  optionsOf,
  statusBadgeClass
} from '@/features/net/netLabels'
import '@/features/net/net.css'

/**
 * サイト管理（2.0 の site.jsp 相当）。
 * 端末モードごとに許可するサイトを管理する。新規登録は未承認で入り、
 * 編集すると未承認に戻る（再承認が必要／2.0 と同じ）。
 *
 * 2.0 は操作後に batL01（プロキシ再起動）を起動していたが、2.1 の batL01 は未実装のため
 * 画面側からは何も起動しない（DB 更新のみ）。
 */
const toast = useToast()

const loading = ref(false)
const busy = ref(false)
const error = ref('')
const rows = ref<SiteRow[]>([])
const page = ref(1)
const totalPages = ref(1)
const totalElements = ref(0)
const sortBy = ref('createdAt')
const sortDir = ref<'asc' | 'desc'>('desc')

const PAGE_SIZE = 15

const filters = reactive({
  category: '',
  kind: '',
  judgeMethod: '',
  approvalStatus: '',
  status: '',
  keyword: ''
})

const kindOptions = optionsOf(KIND_LABELS)
const judgeMethodOptions = optionsOf(JUDGE_METHOD_LABELS)
const categoryOptions = optionsOf(CATEGORY_LABELS)
const approvalOptions = optionsOf(APPROVAL_LABELS)
const statusOptions = optionsOf(STATUS_LABELS)

const pageItems = computed(() => paginationItems(page.value, totalPages.value))
/** 並び替え（同じ列を押したら昇順・降順を切り替える）。 */
function toggleSort(column: string): void {
  if (sortBy.value === column) {
    sortDir.value = sortDir.value === 'asc' ? 'desc' : 'asc'
  } else {
    sortBy.value = column
    sortDir.value = 'asc'
  }
  void load()
}

function sortMark(column: string): string {
  if (sortBy.value !== column) return ''
  return sortDir.value === 'asc' ? '▲' : '▼'
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await searchSites({
      category: filters.category,
      kind: filters.kind,
      judgeMethod: filters.judgeMethod,
      approvalStatus: filters.approvalStatus,
      status: filters.status,
      keyword: filters.keyword,
      sortBy: sortBy.value,
      sortDir: sortDir.value,
      page: page.value,
      size: PAGE_SIZE
    })
    rows.value = response.data.items
    page.value = response.data.page
    totalPages.value = response.data.totalPages
    totalElements.value = response.data.totalElements
  } catch (caught) {
    error.value = caught instanceof ApiError ? caught.message : 'サイト一覧を取得できませんでした。'
  } finally {
    loading.value = false
  }
}

function search(): void {
  page.value = 1
  void load()
}

function reset(): void {
  filters.category = ''
  filters.kind = ''
  filters.judgeMethod = ''
  filters.approvalStatus = ''
  filters.status = ''
  filters.keyword = ''
  sortBy.value = 'createdAt'
  sortDir.value = 'desc'
  search()
}

function goto(nextPage: number): void {
  if (nextPage < 1 || nextPage > totalPages.value) return
  page.value = nextPage
  void load()
}

/* ---------- 新規登録 / 編集ダイアログ ---------- */
const dialogOpen = ref(false)
const editingId = ref<number | null>(null)
/** 編集中の行が持っていたバージョン（楽観的ロック用）。 */
const editingVersion = ref<number | null>(null)
const submitted = ref(false)
const form = reactive({
  siteName: '',
  siteUrl: '',
  kindCode: 'STUDY' as SiteKindCode,
  judgeMethodCode: 'SUFFIX' as JudgeMethodCode,
  categoryCode: 'OTHER' as SiteCategoryCode,
  categoryName: '',
  note: ''
})

const formErrors = computed<Record<string, string>>(() => {
  const result: Record<string, string> = {}
  if (form.siteName.trim() === '') result.siteName = 'サイト名称を入力してください。'
  else if (form.siteName.trim().length > 200) result.siteName = 'サイト名称は200文字以内で入力してください。'
  if (form.siteUrl.trim() === '') result.siteUrl = 'サイトURLを入力してください。'
  if (form.note.trim().length > 200) result.note = '備考は200文字以内で入力してください。'
  if (form.categoryCode === 'OTHER' && form.categoryName.trim().length > 50) {
    result.categoryName = '分類名称は50文字以内で入力してください。'
  }
  return result
})

function fieldError(field: string): string {
  return submitted.value ? (formErrors.value[field] ?? '') : ''
}

function openCreate(): void {
  editingId.value = null
  editingVersion.value = null
  form.siteName = ''
  form.siteUrl = ''
  form.kindCode = 'STUDY'
  form.judgeMethodCode = 'SUFFIX'
  form.categoryCode = 'OTHER'
  form.categoryName = ''
  form.note = ''
  submitted.value = false
  dialogOpen.value = true
}

function openEdit(row: SiteRow): void {
  editingId.value = row.siteId
  editingVersion.value = row.version
  form.siteName = row.siteName
  form.siteUrl = row.siteUrl
  form.kindCode = row.kindCode
  form.judgeMethodCode = row.judgeMethodCode
  form.categoryCode = row.categoryCode
  form.categoryName = row.categoryName ?? ''
  form.note = row.note ?? ''
  submitted.value = false
  dialogOpen.value = true
}

async function save(): Promise<void> {
  if (busy.value) return
  submitted.value = true
  if (Object.keys(formErrors.value).length > 0) {
    toast.warning('入力内容を確認してください。')
    return
  }
  busy.value = true
  try {
    const body = {
      siteName: form.siteName.trim(),
      siteUrl: form.siteUrl.trim(),
      kindCode: form.kindCode,
      judgeMethodCode: form.judgeMethodCode,
      categoryCode: form.categoryCode,
      categoryName: form.categoryCode === 'OTHER' ? form.categoryName.trim() : null,
      note: form.note.trim(),
      version: editingVersion.value ?? undefined
    }
    const response = editingId.value === null
      ? await createSite(body)
      : await updateSite(editingId.value, body)
    dialogOpen.value = false
    toast.success(response.data.message)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : 'サイトを保存できませんでした。')
  } finally {
    busy.value = false
  }
}

async function approve(row: SiteRow): Promise<void> {
  if (busy.value) return
  if (!window.confirm(`サイト「${row.siteName}」を承認しますか？`)) return
  busy.value = true
  try {
    const response = await approveSite(row.siteId)
    toast.success(response.data.message)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '承認できませんでした。')
  } finally {
    busy.value = false
  }
}

/** 却下（承認の取り消しにも使う）。承認日時・承認者は残さない。 */
async function reject(row: SiteRow): Promise<void> {
  if (busy.value) return
  if (!window.confirm(`サイト「${row.siteName}」を却下しますか？`)) return
  busy.value = true
  try {
    const response = await rejectSite(row.siteId)
    toast.success(response.data.message)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '却下できませんでした。')
  } finally {
    busy.value = false
  }
}

async function remove(row: SiteRow): Promise<void> {
  if (busy.value) return
  if (!window.confirm(`「${row.siteName}」を削除します。よろしいですか？`)) return
  busy.value = true
  try {
    const response = await deleteSite(row.siteId)
    toast.success(response.data.message)
    await load()
  } catch (caught) {
    toast.danger(caught instanceof ApiError ? caught.message : '削除できませんでした。')
  } finally {
    busy.value = false
  }
}

onMounted(load)
</script>

<template>
  <div class="net-page">
    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title">
          <AppIcon name="search" size="sm" /> 検索条件
        </h3>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--primary" :disabled="loading" @click="search">
            <AppIcon name="search" size="sm" /> 検索
          </button>
          <button type="button" class="btn btn--secondary" :disabled="loading" @click="reset">
            <AppIcon name="rotate" size="sm" /> リセット
          </button>
          <button type="button" class="btn btn--primary" @click="openCreate">
            <AppIcon name="plus" size="sm" /> 新規
          </button>
        </div>
      </div>
      <div class="filters">
        <div class="filters__row">
          <span class="filter-item">
            <span class="filter-item__label">分類：</span>
            <select v-model="filters.category" class="select" style="width: 140px">
              <option value="">すべて</option>
              <option v-for="option in categoryOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">区分：</span>
            <select v-model="filters.kind" class="select" style="width: 140px">
              <option value="">すべて</option>
              <option v-for="option in kindOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">判定方法：</span>
            <select v-model="filters.judgeMethod" class="select" style="width: 140px">
              <option value="">すべて</option>
              <option v-for="option in judgeMethodOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">承認：</span>
            <select v-model="filters.approvalStatus" class="select" style="width: 120px">
              <option value="">すべて</option>
              <option v-for="option in approvalOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">ステータス：</span>
            <select v-model="filters.status" class="select" style="width: 110px">
              <option value="">すべて</option>
              <option v-for="option in statusOptions" :key="option.value" :value="option.value">
                {{ option.label }}
              </option>
            </select>
          </span>
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="filters.keyword" class="input" style="flex: 1"
              placeholder="サイト名称 / サイトURL / 備考" @keyup.enter="search"
            />
          </span>
        </div>
      </div>
    </div>

    <div class="table-section">
      <div class="table-section__head">
        <h3 class="table-section__title"><AppIcon name="list" size="sm" /> サイト一覧</h3>
        <span class="table-section__meta">全 {{ totalElements }} 件</span>
      </div>

      <p v-if="error" class="alert alert--danger">{{ error }}</p>
      <p v-else-if="loading" class="net-page__loading">読み込んでいます...</p>

      <div v-else class="table-wrap">
        <table class="data-table">
          <thead>
            <tr>
              <th class="col-actions">操作</th>
              <th><button type="button" class="net-sort" @click="toggleSort('siteName')">サイト名称{{ sortMark('siteName') }}</button></th>
              <th><button type="button" class="net-sort" @click="toggleSort('siteUrl')">サイトURL{{ sortMark('siteUrl') }}</button></th>
              <th><button type="button" class="net-sort" @click="toggleSort('kind')">区分{{ sortMark('kind') }}</button></th>
              <th><button type="button" class="net-sort" @click="toggleSort('judgeMethod')">判定方法{{ sortMark('judgeMethod') }}</button></th>
              <th><button type="button" class="net-sort" @click="toggleSort('category')">分類{{ sortMark('category') }}</button></th>
              <th><button type="button" class="net-sort" @click="toggleSort('approvalStatus')">承認ステータス{{ sortMark('approvalStatus') }}</button></th>
              <th><button type="button" class="net-sort" @click="toggleSort('status')">ステータス{{ sortMark('status') }}</button></th>
              <th>備考</th>
              <th><button type="button" class="net-sort" @click="toggleSort('createdAt')">登録日時{{ sortMark('createdAt') }}</button></th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="rows.length === 0">
              <td colspan="10" class="net-page__empty">該当するサイトがありません。</td>
            </tr>
            <tr v-for="row in rows" :key="row.siteId" :data-site-id="row.siteId">
              <td class="row-actions">
                <button
                  v-if="row.approvalStatus === 'APPROVED'" type="button" class="btn btn--icon btn--sm"
                  title="却下" aria-label="却下" :disabled="busy" @click="reject(row)"
                >
                  <AppIcon name="rotate" size="sm" class="icon--reject" />
                </button>
                <button
                  v-else type="button" class="btn btn--icon btn--sm"
                  title="承認" aria-label="承認" :disabled="busy" @click="approve(row)"
                >
                  <AppIcon name="check-circle" size="sm" />
                </button>
                <button
                  type="button" class="btn btn--icon btn--sm" title="編集" aria-label="編集"
                  @click="openEdit(row)"
                >
                  <AppIcon name="edit" size="sm" class="icon--edit" />
                </button>
                <button
                  type="button" class="btn btn--icon btn--sm" title="削除" aria-label="削除"
                  :disabled="busy" @click="remove(row)"
                >
                  <AppIcon name="trash" size="sm" class="icon--danger" />
                </button>
              </td>
              <td class="cell-strong">{{ row.siteName }}</td>
              <td class="cell-muted">{{ row.siteUrl }}</td>
              <td>{{ KIND_LABELS[row.kindCode] }}</td>
              <td>{{ JUDGE_METHOD_LABELS[row.judgeMethodCode] }}</td>
              <td>{{ categoryText(row.categoryCode, row.categoryName) }}</td>
              <td><span class="badge" :class="approvalBadgeClass(row.approvalStatus)">{{ APPROVAL_LABELS[row.approvalStatus] }}</span></td>
              <td><span class="badge" :class="statusBadgeClass(row.status)">{{ STATUS_LABELS[row.status] }}</span></td>
              <td class="cell-muted">{{ row.note ?? '' }}</td>
              <td class="cell-muted">{{ formatIsoDate(row.createdAt ?? '') }}</td>
            </tr>
          </tbody>
        </table>
      </div>

      <div v-if="!loading && totalPages > 1" class="pagination">
        <span class="pagination__info">全 {{ totalElements }} 件（{{ page }} / {{ totalPages }} ページ）</span>
        <div class="pagination__pages">
          <button type="button" class="page-btn" :disabled="page <= 1" @click="goto(page - 1)">‹</button>
          <template v-for="(item, key) in pageItems" :key="key">
            <span v-if="item === 'gap'" class="page-gap">…</span>
            <button
              v-else type="button" class="page-btn" :class="{ 'is-active': item === page }"
              @click="goto(item)"
            >
              {{ item }}
            </button>
          </template>
          <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goto(page + 1)">›</button>
        </div>
      </div>
    </div>

    <!-- 新規登録 / 編集ダイアログ -->
    <div v-if="dialogOpen" class="overlay">
      <section class="dialog dialog--md" role="dialog" aria-modal="true" aria-labelledby="siteDialogTitle">
        <div class="dialog__head">
          <h2 id="siteDialogTitle" class="dialog__title">
            <AppIcon name="globe" size="sm" /> {{ editingId === null ? 'サイト新規登録' : 'サイト編集' }}
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="dialogOpen = false">
            <AppIcon name="x" size="sm" />
          </button>
        </div>
        <div class="dialog__body">
          <div class="form-grid">
            <div class="field">
              <label class="field__label" for="netSiteName">サイト名称<span class="net-required">必須</span></label>
              <input
                id="netSiteName" v-model="form.siteName" class="input" type="text" maxlength="200"
                placeholder="例：youtube.com" :class="{ 'is-invalid': fieldError('siteName') !== '' }"
              />
              <p v-if="fieldError('siteName')" class="field__error">{{ fieldError('siteName') }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="netSiteUrl">サイトURL<span class="net-required">必須</span></label>
              <input
                id="netSiteUrl" v-model="form.siteUrl" class="input" type="text"
                placeholder="https://example.com" :class="{ 'is-invalid': fieldError('siteUrl') !== '' }"
              />
              <p v-if="fieldError('siteUrl')" class="field__error">{{ fieldError('siteUrl') }}</p>
            </div>
            <div class="field">
              <label class="field__label" for="netSiteKind">区分</label>
              <select id="netSiteKind" v-model="form.kindCode" class="select">
                <option v-for="option in kindOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="netSiteJudge">判定方法</label>
              <select id="netSiteJudge" v-model="form.judgeMethodCode" class="select">
                <option v-for="option in judgeMethodOptions" :key="option.value" :value="option.value">
                  {{ option.label }}
                </option>
              </select>
            </div>
            <div class="field">
              <label class="field__label" for="netSiteCategory">分類</label>
              <select id="netSiteCategory" v-model="form.categoryCode" class="select">
                <option v-for="option in categoryOptions" :key="option.value" :value="option.value">{{ option.label }}</option>
              </select>
            </div>
            <div v-if="form.categoryCode === 'OTHER'" class="field">
              <label class="field__label" for="netSiteCategoryName">分類名称（その他の場合）</label>
              <input
                id="netSiteCategoryName" v-model="form.categoryName" class="input" type="text" maxlength="50"
                placeholder="例：英会話" :class="{ 'is-invalid': fieldError('categoryName') !== '' }"
              />
              <p v-if="fieldError('categoryName')" class="field__error">{{ fieldError('categoryName') }}</p>
            </div>
            <div class="field field--wide">
              <label class="field__label" for="netSiteNote">備考</label>
              <textarea
                id="netSiteNote" v-model="form.note" class="textarea" rows="2" maxlength="200"
                :class="{ 'is-invalid': fieldError('note') !== '' }"
              ></textarea>
              <p v-if="fieldError('note')" class="field__error">{{ fieldError('note') }}</p>
            </div>
          </div>
          <p v-if="editingId !== null" class="field__hint">
            編集すると承認は「未承認」に戻ります（再承認が必要です）。
          </p>
        </div>
        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="dialogOpen = false">キャンセル</button>
          <button type="button" class="btn btn--primary" :disabled="busy" @click="save">
            <AppIcon name="check" size="sm" /> {{ busy ? '保存中...' : '保存' }}
          </button>
        </div>
      </section>
    </div>
  </div>
</template>

<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useAuthStore } from '@/stores/auth'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import DocumentFileChips from '@/views/document/DocumentFileChips.vue'
import TestFilesDialog from '@/views/testinfo/TestFilesDialog.vue'
import TestInfoFormDialog from '@/views/testinfo/TestInfoFormDialog.vue'
import { deleteTestInfo, getTestInfos, type TestRow } from '@/api/testinfo'
import { formatExamDate, messageOf, toDocumentFileInfo } from '@/features/testinfo/testinfo-file'
import { paginationItems } from '@/features/pagination/pagination'

/**
 * テスト情報管理（2.0 の testinfo.jsp 相当）。
 * 検索条件で絞り込み、一覧から編集・削除・ファイル閲覧・注釈を行う。
 */
const auth = useAuthStore()
const isFamilyUser = computed(() => auth.role === 'STUDENT' || auth.role === 'GUARDIAN')

/** 一覧のページサイズ（クライアント側ページング）。 */
const PAGE_SIZE = 15

const SUBJECT_OPTIONS = ['すべて', '英語', '数学', '国語', '物理', '化学', '生物', '歴史', '地理']
const KIND_OPTIONS = ['すべて', '通常', '模擬', '復習', '練習']

interface TestQuery { subject: string; kind: string; keyword: string }

function emptyQuery(): TestQuery {
  return { subject: 'すべて', kind: 'すべて', keyword: '' }
}

const toast = useToast()
const rows = ref<TestRow[]>([])
const loading = ref(false)
const error = ref('')

// 検索条件（入力中）と適用済み条件を分離し、「検索」で反映する。
const form = ref<TestQuery>(emptyQuery())
const applied = ref<TestQuery>(emptyQuery())
const page = ref(1)

const formOpen = ref(false)
const editing = ref<TestRow | null>(null)
const filesOpen = ref(false)
const filesRow = ref<TestRow | null>(null)

const totalPages = computed(() => Math.max(1, Math.ceil(rows.value.length / PAGE_SIZE)))
const paged = computed(() => rows.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))
// 表示は共通ロジック（現在ページの前後＋先頭・末尾、離れた部分は省略記号）。
const pageItems = computed(() => paginationItems(page.value, totalPages.value))

watch(rows, () => { if (page.value > totalPages.value) page.value = 1 })

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const query = applied.value
    rows.value = (await getTestInfos({
      subject: query.subject === 'すべて' ? undefined : query.subject,
      kind: query.kind === 'すべて' ? undefined : query.kind,
      keyword: query.keyword
    })).data.rows
  } catch (cause) {
    error.value = messageOf(cause)
    rows.value = []
  } finally { loading.value = false }
}

function search(): void {
  applied.value = { ...form.value }
  page.value = 1
  void load()
}

function reset(): void {
  form.value = emptyQuery()
  applied.value = emptyQuery()
  page.value = 1
  void load()
}

function goto(target: number): void {
  page.value = Math.min(Math.max(1, target), totalPages.value)
}

function newTest(): void {
  editing.value = null
  formOpen.value = true
}

function editTest(row: TestRow): void {
  editing.value = row
  formOpen.value = true
}

function openFiles(row: TestRow): void {
  filesRow.value = row
  filesOpen.value = true
}

function onSaved(): void { void load() }

/** ファイル操作後は一覧とファイルダイアログの元データを両方更新する。 */
function onFilesChanged(): void {
  const current = filesRow.value
  void load().then(() => {
    if (current) filesRow.value = rows.value.find((row) => row.testNo === current.testNo) ?? current
  })
}

async function removeTest(row: TestRow): Promise<void> {
  const suffix = row.files.length > 0 ? `（ファイル ${row.files.length} 件も削除されます）` : ''
  if (!window.confirm(`${row.testNo}「${row.testName}」を削除しますか。${suffix}`)) return
  try {
    await deleteTestInfo(row.testNo)
    toast.success(`${row.testNo} を削除しました。`)
    await load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

function accuracy(row: TestRow): string {
  return row.accuracyText && row.accuracyText.trim() !== '' ? row.accuracyText : '--'
}

function numberText(value: number | null): string {
  return value === null || value === undefined ? '—' : String(value)
}

onMounted(() => { if (isFamilyUser.value) void load() })
</script>

<template>
  <div v-if="isFamilyUser" class="ti-page">
    <p v-if="error" class="alert alert--danger">{{ error }}</p>

    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title"><AppIcon name="search" /> 検索条件</h3>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--primary" @click="search"><AppIcon name="search" size="sm" /> 検索</button>
          <button type="button" class="btn btn--secondary" @click="newTest"><AppIcon name="plus" size="sm" /> 新規</button>
          <button type="button" class="btn btn--secondary" @click="reset"><AppIcon name="rotate" size="sm" /> リセット</button>
        </div>
      </div>
      <div class="filters ti-filters">
        <div class="filters__row">
          <span class="filter-item">
            <span class="filter-item__label">教科：</span>
            <select v-model="form.subject" class="select">
              <option v-for="option in SUBJECT_OPTIONS" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">区分：</span>
            <select v-model="form.kind" class="select">
              <option v-for="option in KIND_OPTIONS" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="form.keyword" class="input" placeholder="テスト名・番号" style="flex: 1"
              @keydown.enter.prevent="search"
            />
          </span>
        </div>
      </div>
    </div>

    <div class="table-section">
      <div class="table-section__head">
        <h3 class="table-section__title">テスト一覧</h3>
        <span class="table-section__meta">全 {{ rows.length }} 件</span>
      </div>
      <div class="table-wrap">
        <table class="data-table ti-table">
          <thead>
            <tr>
              <th class="col-actions">操作</th>
              <th class="ti-col-no">テスト番号</th>
              <th class="ti-col-name">テスト名</th>
              <th class="ti-col-subject">教科</th>
              <th class="ti-col-kind">区分</th>
              <th class="ti-col-date">試験日</th>
              <th class="ti-col-score">得点数</th>
              <th class="ti-col-score">満点数</th>
              <th class="ti-col-accuracy">正答率</th>
              <th class="ti-col-memo">詳細メモ</th>
              <th class="ti-col-files">試験用紙ファイル</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="11" class="ti-empty">読込中...</td>
            </tr>
            <tr v-else-if="paged.length === 0">
              <td colspan="11" class="ti-empty">該当するテストがありません</td>
            </tr>
            <tr v-for="row in paged" v-else :key="row.testNo">
              <td class="row-actions">
                <button type="button" class="btn btn--icon btn--sm" title="編集" @click="editTest(row)">
                  <AppIcon name="edit" size="sm" class="icon--edit" />
                </button>
                <button type="button" class="btn btn--icon btn--sm is-danger" title="削除" @click="removeTest(row)">
                  <AppIcon name="trash" size="sm" />
                </button>
                <button type="button" class="btn btn--icon btn--sm" title="試験用紙ファイル" aria-label="試験用紙ファイル" @click="openFiles(row)">
                  <AppIcon name="eye" size="sm" class="icon--view" />
                </button>
              </td>
              <td class="cell-strong ti-col-no">{{ row.testNo }}</td>
              <td class="ti-col-name" :title="row.testName">{{ row.testName }}</td>
              <td class="ti-col-subject">{{ row.subject }}</td>
              <td class="ti-col-kind">{{ row.kind }}</td>
              <td class="cell-muted ti-col-date">{{ formatExamDate(row.examDate) }}</td>
              <td class="cell-muted ti-col-score">{{ numberText(row.score) }}</td>
              <td class="cell-muted ti-col-score">{{ numberText(row.fullScore) }}</td>
              <td class="cell-muted ti-col-accuracy">{{ accuracy(row) }}</td>
              <td class="cell-muted ti-col-memo" :title="row.memo ?? ''">{{ row.memo || '—' }}</td>
              <td class="ti-col-files">
                <DocumentFileChips :files="row.files.map(toDocumentFileInfo)" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span class="pagination__info">全 {{ rows.length }} 件（{{ page }} / {{ totalPages }} ページ）</span>
        <div class="pagination__pages">
          <button type="button" class="page-btn" :disabled="page <= 1" @click="goto(page - 1)">‹</button>
          <template v-for="(item, key) in pageItems" :key="key">
            <span v-if="item === 'gap'" class="page-gap">…</span>
            <button
              v-else type="button" class="page-btn"
              :class="{ 'is-active': item === page }" @click="goto(item)"
            >
              {{ item }}
            </button>
          </template>
          <button type="button" class="page-btn" :disabled="page >= totalPages" @click="goto(page + 1)">›</button>
        </div>
      </div>
    </div>

    <TestInfoFormDialog :open="formOpen" :row="editing" @close="formOpen = false" @saved="onSaved" />

    <TestFilesDialog
      :open="filesOpen" :test-no="filesRow?.testNo ?? ''" :test-name="filesRow?.testName ?? ''"
      :files="filesRow?.files ?? []" @close="filesOpen = false" @changed="onFilesChanged"
    />
  </div>
  <section v-else class="card page-body">
    <h2>テスト情報管理</h2>
    <p>テスト情報は、生徒または保護者としてログインした場合のみ表示できます。</p>
  </section>
</template>

<style scoped>
.ti-page {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-height: 0;
}

.ti-filters .filter-item {
  flex: 0 1 auto;
  min-width: 0;
}

.ti-filters .filter-item .select {
  width: 128px;
  min-width: 128px;
}

.ti-filters .filter-item--grow {
  flex: 1 1 200px;
}

.ti-filters .filter-item--grow .input {
  width: auto;
  min-width: 0;
  flex: 1;
}

/* 列幅は固定レイアウトで決める。狭い画面では横スクロールし、
   余りは テスト名・詳細メモ・試験用紙ファイル（%）で分け合う。 */
/* 列幅は固定レイアウトで決める。指定した列はその幅を保ち、
   幅を指定しない列（＝試験用紙ファイル）が余りを全部受け取る。
   こうしないと余りが全列へ比例配分され、操作列が資料管理より広くなってしまう。 */
.ti-table {
  table-layout: fixed;
  min-width: 1180px;
}

/* 資料管理の操作列と同じ幅（アイコン3つぶん）を保つ。 */
.ti-table .col-actions { width: 124px; }
/* テスト番号は TST-yyyyMMdd-HHmmss（衝突時は -01 付き）が収まる幅を確保する。 */
.ti-table .ti-col-no { width: 184px; white-space: nowrap; }
.ti-table .ti-col-name { width: 15%; white-space: normal; overflow-wrap: anywhere; }
.ti-table .ti-col-subject { width: 64px; }
.ti-table .ti-col-kind { width: 64px; }
.ti-table .ti-col-date { width: 96px; }
.ti-table .ti-col-score { width: 64px; }
.ti-table .ti-col-accuracy { width: 72px; }
.ti-table .ti-col-memo { width: 12%; white-space: normal; overflow-wrap: anywhere; }
/* 幅を指定しない → 余った幅をすべて受け取る（チップが1行に収まりやすくなる）。 */
.ti-table .ti-col-files { white-space: normal; }

.ti-empty {
  padding: var(--sp-6);
  text-align: center;
  color: var(--color-text-muted);
  font-size: var(--fs-sm);
}
</style>

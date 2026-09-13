<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import DocumentFilesDialog from '@/views/document/DocumentFilesDialog.vue'
import DocumentFileChips from '@/views/document/DocumentFileChips.vue'
import { paginationItems } from '@/features/pagination/pagination'
import { CATEGORY_LABELS, folderCategories, folderOptions } from '@/features/document-folder/folderPath'
import {
  createDocument, deleteDocument, getDocumentWorkspace, updateDocument,
  type DocumentFolder, type DocumentSummary, type SaveDocumentRequest
} from '@/api/documents'

const toast = useToast()

/** 一覧のページサイズ。資料数が増えても表が縦に伸びすぎないようにする。 */
const PAGE_SIZE = 20

interface DocQuery {
  large: string
  medium: string
  small: string
  detail: string
  status: string
  expiry: string
  keyword: string
}

function emptyQuery(): DocQuery {
  return { large: '', medium: '', small: '', detail: '', status: '', expiry: '', keyword: '' }
}

const documents = ref<DocumentSummary[]>([])
const folders = ref<DocumentFolder[]>([])
const loading = ref(false)
const error = ref('')

// 検索条件（入力中）と適用済み条件を分離し、「検索」で反映する。
const form = ref<DocQuery>(emptyQuery())
const applied = ref<DocQuery>(emptyQuery())
const page = ref(1)

const dialogOpen = ref(false)
const selected = ref<DocumentSummary | null>(null)
const draft = ref<SaveDocumentRequest>(emptyDraft())
const uploadFiles = ref<File[]>([])
const dragging = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)

const filesOpen = ref(false)
const filesDocNo = ref('')

function emptyDraft(): SaveDocumentRequest {
  return { folderId: null, status: '1', expiryDate: null, comment: null }
}

function messageOf(cause: unknown): string {
  return cause instanceof ApiError || cause instanceof Error ? cause.message : '処理に失敗しました。'
}

function distinct(values: Array<string | null>): string[] {
  const set = new Set<string>()
  for (const value of values) {
    const trimmed = (value ?? '').trim()
    if (trimmed !== '') set.add(trimmed)
  }
  return [...set].sort((a, b) => a.localeCompare(b, 'ja'))
}

function distinctBy(key: (d: DocumentSummary) => string | null): string[] {
  return distinct(documents.value.map(key))
}

// ---- 分類ドロップダウン（上位を選ぶまで下位は空。上位の選択に応じて絞り込む） ----
const largeOptions = computed(() => distinctBy((d) => d.largeCategory))
const mediumOptions = computed(() => {
  const large = form.value.large
  if (!large) return []
  return distinct(documents.value.filter((d) => d.largeCategory === large).map((d) => d.mediumCategory))
})
const smallOptions = computed(() => {
  const { large, medium } = form.value
  if (!large || !medium) return []
  return distinct(documents.value
    .filter((d) => d.largeCategory === large && d.mediumCategory === medium)
    .map((d) => d.smallCategory))
})
const detailOptions = computed(() => {
  const { large, medium, small } = form.value
  if (!large || !medium || !small) return []
  return distinct(documents.value
    .filter((d) => d.largeCategory === large && d.mediumCategory === medium && d.smallCategory === small)
    .map((d) => d.detailCategory))
})

// 上位を変更した時点で下位の選択は無効になるため、下位の選択値をクリアする。
watch(() => form.value.large, () => { form.value.medium = ''; form.value.small = ''; form.value.detail = '' })
watch(() => form.value.medium, () => { form.value.small = ''; form.value.detail = '' })
watch(() => form.value.small, () => { form.value.detail = '' })

/**
 * キーワード検索の対象文字列。一覧に表示している全項目
 * （資料番号・ステータス・有効期限・大/中/小/細分類・コメント・内容=ファイル数）を連結する。
 */
function searchText(document: DocumentSummary): string {
  return [
    document.documentNo,
    document.status === '1' ? '有効' : '無効',
    document.status,
    document.expiryDate ?? '',
    document.largeCategory ?? '',
    document.mediumCategory ?? '',
    document.smallCategory ?? '',
    document.detailCategory ?? '',
    document.comment ?? '',
    String(document.fileCount)
  ].join(' ').toLowerCase()
}

// ---- 絞り込み（適用済み条件で評価。資料数が少ないためクライアント側で処理する） ----
const filtered = computed<DocumentSummary[]>(() => {
  const q = applied.value
  const keyword = q.keyword.trim().toLowerCase()
  // 有効期限は日付の完全一致（2.0 の検索条件と同じ: replace(有効期限,'/','-') = 指定日）
  const expiry = q.expiry.trim().replace(/-/g, '/')
  return documents.value.filter((d) => {
    if (q.large && (d.largeCategory ?? '') !== q.large) return false
    if (q.medium && (d.mediumCategory ?? '') !== q.medium) return false
    if (q.small && (d.smallCategory ?? '') !== q.small) return false
    if (q.detail && (d.detailCategory ?? '') !== q.detail) return false
    if (q.status && d.status !== q.status) return false
    if (expiry && (d.expiryDate ?? '').trim().replace(/-/g, '/') !== expiry) return false
    if (keyword && !searchText(d).includes(keyword)) return false
    return true
  })
})

const totalPages = computed(() => Math.max(1, Math.ceil(filtered.value.length / PAGE_SIZE)))
const paged = computed(() => filtered.value.slice((page.value - 1) * PAGE_SIZE, page.value * PAGE_SIZE))
// 表示は共通ロジック（現在ページの前後＋先頭・末尾、離れた部分は省略記号）。
const pageItems = computed(() => paginationItems(page.value, totalPages.value))

watch(filtered, () => { if (page.value > totalPages.value) page.value = 1 })

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await getDocumentWorkspace(null)
    documents.value = response.data.documents
    folders.value = response.data.folders
  } catch (cause) {
    error.value = messageOf(cause)
    documents.value = []
    folders.value = []
  } finally {
    loading.value = false
  }
}

function search(): void {
  applied.value = { ...form.value }
  page.value = 1
}

function reset(): void {
  form.value = emptyQuery()
  applied.value = emptyQuery()
  page.value = 1
}

function goto(target: number): void {
  page.value = Math.min(Math.max(1, target), totalPages.value)
}

function formatExpiry(document: DocumentSummary): string {
  return document.expiryDate && document.expiryDate.trim() !== '' ? document.expiryDate : '—'
}

function category(value: string | null | undefined): string {
  return value && value.trim() !== '' ? value : '—'
}

function statusLabel(status: string): string {
  return status === '1' ? '有効' : '無効'
}

// ---- 新規 / 編集 ----
/** ダイアログの分類表示（選択中フォルダの階層から導出。入力は受け付けない）。 */
const draftCategories = computed(() => folderCategories(folders.value, draft.value.folderId))
/** ダイアログのフォルダ選択肢（同名フォルダを区別できるよう階層パスで表示）。 */
const dialogFolderOptions = computed(() => folderOptions(folders.value))

function newDocument(): void {
  selected.value = null
  draft.value = emptyDraft()
  uploadFiles.value = []
  dialogOpen.value = true
}

function editDocument(document: DocumentSummary): void {
  selected.value = document
  draft.value = {
    folderId: document.folderId, status: document.status,
    expiryDate: document.expiryDate, comment: document.comment
  }
  uploadFiles.value = []
  dialogOpen.value = true
}

function closeDialog(): void {
  dialogOpen.value = false
  selected.value = null
  uploadFiles.value = []
  dragging.value = false
}

function pickFiles(): void {
  fileInput.value?.click()
}

function chooseFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  uploadFiles.value = [...uploadFiles.value, ...Array.from(input.files ?? [])]
  input.value = ''
}

function onDrop(event: DragEvent): void {
  dragging.value = false
  const dropped = Array.from(event.dataTransfer?.files ?? [])
  if (dropped.length > 0) uploadFiles.value = [...uploadFiles.value, ...dropped]
}

function onDragOver(): void { dragging.value = true }
function onDragLeave(): void { dragging.value = false }

function removePickedFile(index: number): void {
  uploadFiles.value = uploadFiles.value.filter((_, i) => i !== index)
}

async function save(): Promise<void> {
  try {
    if (selected.value) {
      await updateDocument(selected.value.documentNo, draft.value)
      toast.success(`${selected.value.documentNo} を更新しました。`)
    } else {
      const result = await createDocument(draft.value, uploadFiles.value)
      toast.success(`${result.data.documentNo} を登録しました。`)
    }
    closeDialog()
    await load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

async function removeDocument(document: DocumentSummary): Promise<void> {
  const count = document.fileCount
  const suffix = count > 0 ? `（ファイル ${count} 件も削除されます）` : ''
  if (!window.confirm(`${document.documentNo} を削除しますか。${suffix}`)) return
  try {
    await deleteDocument(document.documentNo)
    toast.success(`${document.documentNo} を削除しました。`)
    await load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

function openFiles(documentNo: string): void {
  filesDocNo.value = documentNo
  filesOpen.value = true
}

/** 資料を編集した後に一覧を開いていたファイルダイアログの対象が消えていないか確認する用途も兼ねる。 */
function onFilesChanged(): void { void load() }

onMounted(load)
</script>

<template>
  <div class="doc-list-page">
    <p v-if="error" class="alert alert--danger">{{ error }}</p>

    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title"><AppIcon name="search" size="sm" /> 検索条件</h3>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--primary" @click="search"><AppIcon name="search" size="sm" /> 検索</button>
          <button type="button" class="btn btn--primary" @click="newDocument"><AppIcon name="plus" size="sm" /> 新規</button>
          <button type="button" class="btn btn--secondary" @click="reset"><AppIcon name="rotate" size="sm" /> リセット</button>
        </div>
      </div>
      <div class="filters doc-list-filters">
        <div class="filters__row doc-list-filters__categories">
          <span class="filter-item">
            <span class="filter-item__label">大分類：</span>
            <select v-model="form.large" class="select">
              <option value="">すべて</option>
              <option v-for="option in largeOptions" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">中分類：</span>
            <select v-model="form.medium" class="select">
              <option value="">すべて</option>
              <option v-for="option in mediumOptions" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">小分類：</span>
            <select v-model="form.small" class="select">
              <option value="">すべて</option>
              <option v-for="option in smallOptions" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">細分類：</span>
            <select v-model="form.detail" class="select">
              <option value="">すべて</option>
              <option v-for="option in detailOptions" :key="option" :value="option">{{ option }}</option>
            </select>
          </span>
        </div>
        <div class="filters__row">
          <span class="filter-item">
            <span class="filter-item__label">ステータス：</span>
            <select v-model="form.status" class="select">
              <option value="">すべて</option>
              <option value="1">有効</option>
              <option value="0">無効</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">有効期限：</span>
            <input v-model="form.expiry" class="input" type="date" title="指定した日付が有効期限の資料を表示します" />
          </span>
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input
              v-model="form.keyword"
              class="input"
              placeholder="資料番号・分類・コメント・期限など"
              title="一覧の全項目（資料番号・ステータス・有効期限・分類・コメント・内容）を対象に検索します"
              style="flex: 1"
              @keydown.enter.prevent="search"
            />
          </span>
        </div>
      </div>
    </div>

    <div class="table-section">
      <div class="table-section__head">
        <h3 class="table-section__title">資料一覧</h3>
        <span class="table-section__meta">全 {{ filtered.length }} 件<template v-if="filtered.length !== documents.length">（{{ documents.length }} 件中）</template></span>
      </div>
      <div class="table-wrap">
        <table class="data-table doc-list-table">
          <thead>
            <tr>
              <th class="col-actions">操作</th>
              <th class="doc-col-no">資料番号</th>
              <th class="doc-col-status">ステータス</th>
              <th class="doc-col-expiry">有効期限</th>
              <th>大分類</th>
              <th>中分類</th>
              <th>小分類</th>
              <th>細分類</th>
              <th class="doc-col-comment">コメント</th>
              <th class="doc-col-content">内容</th>
            </tr>
          </thead>
          <tbody>
            <tr v-if="loading">
              <td colspan="10" class="doc-list-empty">読込中...</td>
            </tr>
            <tr v-else-if="paged.length === 0">
              <td colspan="10" class="doc-list-empty">該当する資料がありません</td>
            </tr>
            <tr v-for="document in paged" v-else :key="document.documentNo">
              <td class="row-actions">
                <button type="button" class="btn btn--icon btn--sm" title="編集" @click="editDocument(document)">
                  <AppIcon name="edit" size="sm" class="icon--edit" />
                </button>
                <button type="button" class="btn btn--icon btn--sm is-danger" title="削除" @click="removeDocument(document)">
                  <AppIcon name="trash" size="sm" />
                </button>
                <button type="button" class="btn btn--icon btn--sm" title="ファイル管理" aria-label="ファイル管理" @click="openFiles(document.documentNo)">
                  <AppIcon name="eye" size="sm" class="icon--view" />
                </button>
              </td>
              <td class="cell-strong doc-col-no">{{ document.documentNo }}</td>
              <td class="doc-col-status">
                <span class="badge" :class="document.status === '1' ? 'badge--success' : 'badge--danger'">
                  {{ statusLabel(document.status) }}
                </span>
              </td>
              <td class="cell-muted doc-col-expiry">{{ formatExpiry(document) }}</td>
              <td>{{ category(document.largeCategory) }}</td>
              <td>{{ category(document.mediumCategory) }}</td>
              <td>{{ category(document.smallCategory) }}</td>
              <td>{{ category(document.detailCategory) }}</td>
              <td class="cell-muted doc-col-comment" :title="document.comment ?? ''">{{ document.comment || '—' }}</td>
              <td class="doc-col-content">
                <DocumentFileChips :files="document.files" />
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="pagination">
        <span class="pagination__info">全 {{ filtered.length }} 件（{{ page }} / {{ totalPages }} ページ）</span>
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

    <div v-if="dialogOpen" class="overlay">
      <div class="dialog dialog--lg" role="dialog" aria-modal="true" aria-labelledby="docDetailTitle">
        <div class="dialog__head">
          <h2 class="dialog__title" id="docDetailTitle">
            <AppIcon name="folder" size="sm" /> 資料詳細{{ selected ? ` ${selected.documentNo}` : '（新規）' }}
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click="closeDialog">
            <AppIcon name="x" size="sm" />
          </button>
        </div>
        <div class="dialog__body">
          <div class="form-grid form-grid--3">
            <div class="field">
              <label class="field__label">フォルダ</label>
              <select v-model="draft.folderId" class="select">
                <option :value="null">未分類</option>
                <option v-for="option in dialogFolderOptions" :key="option.folderId" :value="option.folderId">{{ option.label }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label">ステータス</label>
              <select v-model="draft.status" class="select">
                <option value="1">有効</option>
                <option value="0">無効</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label">有効期限</label>
              <input v-model="draft.expiryDate" class="input" placeholder="YYYY/MM/DD" />
            </div>
          </div>

          <p class="doc-list-categories__hint">分類（大分類〜細分類）は、選択したフォルダの階層から自動的に決まります。</p>
          <div class="form-grid form-grid--4 doc-list-categories">
            <div v-for="(label, index) in CATEGORY_LABELS" :key="label" class="field">
              <label class="field__label">{{ label }}</label>
              <p class="doc-list-category">{{ category(draftCategories[index]) }}</p>
            </div>
          </div>

          <template v-if="selected">
            <div class="doc-list-files">
              <span class="doc-list-files__label">ファイル</span>
              <span class="doc-list-files__count">{{ selected.fileCount }} 件</span>
              <button type="button" class="btn btn--secondary btn--sm" @click="openFiles(selected.documentNo)">
                <AppIcon name="folder" size="sm" /> ファイル管理
              </button>
            </div>
          </template>
          <template v-else>
            <div
              class="dropzone doc-list-dropzone" :class="{ 'is-dragging': dragging }"
              @click="pickFiles" @dragover.prevent="onDragOver" @dragleave="onDragLeave" @drop.prevent="onDrop"
            >
              <AppIcon name="upload" />
              ここにファイルをドロップ、またはクリックして選択（各20MB以下・複数可）
            </div>
            <ul v-if="uploadFiles.length > 0" class="doc-list-picked">
              <li v-for="(file, index) in uploadFiles" :key="`${file.name}-${index}`">
                <span>{{ file.name }}</span>
                <button type="button" class="btn btn--icon btn--sm" title="外す" @click="removePickedFile(index)">
                  <AppIcon name="x" size="sm" />
                </button>
              </li>
            </ul>
          </template>

          <div class="field doc-list-comment">
            <label class="field__label">コメント</label>
            <textarea v-model="draft.comment" class="input" rows="3" placeholder="コメントを入力してください" />
          </div>
        </div>
        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click="closeDialog">キャンセル</button>
          <button type="button" class="btn btn--primary" @click="save"><AppIcon name="check" size="sm" /> 保存</button>
        </div>
      </div>
    </div>

    <input ref="fileInput" type="file" multiple style="display: none" @change="chooseFiles" />

    <DocumentFilesDialog :doc-no="filesDocNo" :open="filesOpen" @close="filesOpen = false" @changed="onFilesChanged" />
  </div>
</template>

<style scoped>
.doc-list-page {
  display: flex;
  flex-direction: column;
  gap: var(--sp-4);
  min-height: 0;
}

/* 検索条件は 2 行構成。
   1 行目: 4 分類（大/中/小/細）は等幅にして行全体を占める。
   2 行目: ステータス・有効期限は既定幅、キーワードは残り幅いっぱい。 */
.doc-list-filters .filter-item {
  flex: 0 1 auto;
  min-width: 0;
}
.doc-list-filters .doc-list-filters__categories .filter-item {
  flex: 1 1 0;
}
.doc-list-filters .filter-item .select,
.doc-list-filters .filter-item .input {
  width: 112px;
  min-width: 112px;
}
/* 1 行目のセレクトは自分のセル幅いっぱいに広げる */
.doc-list-filters .doc-list-filters__categories .filter-item .select {
  width: auto;
  min-width: 0;
  flex: 1;
}
/* 日付はネイティブの date 入力なので、YYYY/MM/DD + カレンダーアイコン分の幅を確保する */
.doc-list-filters .filter-item input[type='date'] {
  width: 148px;
  min-width: 148px;
}
.doc-list-filters .filter-item--grow {
  flex: 1 1 200px;
}
.doc-list-filters .filter-item--grow .input {
  width: auto;
  min-width: 0;
  flex: 1;
}

.doc-list-empty {
  padding: var(--sp-6);
  text-align: center;
  color: var(--color-text-muted);
  font-size: var(--fs-sm);
}

/* 列幅は固定レイアウトで決める（auto レイアウトでは内容列とコメント列の
   配分が内容量に左右され、同じ width 指定でも揃わないため）。
   残り幅は分類4列で均等に分ける。狭い画面では横スクロールする。 */
.doc-list-table {
  table-layout: fixed;
  min-width: 1180px;
}

.doc-list-table .col-actions { width: 124px; }
.doc-list-table .doc-col-no { width: 150px; white-space: nowrap; }
.doc-list-table .doc-col-status { width: 100px; }
.doc-list-table .doc-col-expiry { width: 92px; }

/* コメントは短いことが多いので狭くし、その分をファイルチップの内容列へ回す。 */
.doc-list-table .doc-col-comment { width: 14%; }
.doc-list-table .doc-col-content { width: 26%; }

.doc-list-table .doc-col-comment {
  white-space: normal;
  overflow-wrap: anywhere;
}

.doc-list-categories {
  margin-top: var(--sp-3);
}

.doc-list-categories__hint {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-xs);
  color: var(--color-text-subtle);
}

.doc-list-category {
  margin: 0;
  min-height: var(--control-md);
  display: flex;
  align-items: center;
  padding: 0 var(--sp-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
  background: var(--color-surface-alt);
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
  overflow-wrap: anywhere;
}

.doc-list-dropzone {
  margin-top: var(--sp-3);
  padding: var(--sp-5) var(--sp-4);
}

.doc-list-picked {
  margin: var(--sp-2) 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.doc-list-picked li {
  display: flex;
  align-items: center;
  gap: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

.doc-list-picked li span {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-list-files {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  margin-top: var(--sp-3);
}

.doc-list-files__label {
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium, 600);
  color: var(--color-text-muted);
}

.doc-list-files__count {
  flex: 1;
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
}

.doc-list-comment {
  margin-top: var(--sp-3);
}
</style>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import { useAuthStore } from '@/stores/auth'
import {
  deleteTempFile, deleteTempFiles, getTempFiles, uploadTempFiles,
  type TempFileSummary
} from '@/api/tempfiles'
import TempFileImageEditor from '@/views/tempfile/TempFileImageEditor.vue'
import DocumentImageViewer from '@/views/document/DocumentImageViewer.vue'
import type { DocumentFileInfo } from '@/api/documents'
import '@/features/temp-file/temp-file.css'

const auth = useAuthStore()
const isFamilyUser = computed(() => auth.role === 'STUDENT' || auth.role === 'GUARDIAN')

const toast = useToast()
const items = ref<TempFileSummary[]>([])
const loading = ref(false)
const keyword = ref('')
const typeFilter = ref<'すべて' | '画像' | 'その他'>('すべて')
const period = ref('すべて')
const selected = ref<Set<number>>(new Set())
const uploadInput = ref<HTMLInputElement | null>(null)
const editorOpen = ref(false)
const editorFile = ref<TempFileSummary | null>(null)
/** 画像表示ページ（資料管理・テスト情報管理と同じビューア）で開いているファイル。 */
const viewing = ref<TempFileSummary | null>(null)

const periodOptions = ['すべて', '直近1日間', '直近1週間', '直近1ヵ月', '直近3ヵ月', '3ヵ月以前']

/** ビューアは資料管理と共通のため、同じ形（DocumentFileInfo）に詰め替える。 */
function toPreviewFile(file: TempFileSummary): DocumentFileInfo {
  return {
    branchNo: file.tempFileId,
    originalFileName: file.originalFileName,
    extension: file.extension ?? '',
    comment: file.comment,
    contentUrl: file.contentUrl,
    image: file.image
  }
}
const previewImages = computed<DocumentFileInfo[]>(() => items.value.filter((file) => file.image).map(toPreviewFile))
const viewingAsDocument = computed<DocumentFileInfo | null>(() => viewing.value ? toPreviewFile(viewing.value) : null)

/** 表示中のファイルが絞り込みで消えた場合に備え、一覧の現在の並びから探し直す。 */
function syncViewerFile(file: DocumentFileInfo): void {
  viewing.value = items.value.find((item) => item.tempFileId === file.branchNo) ?? null
}

function messageOf(cause: unknown): string {
  return cause instanceof ApiError || cause instanceof Error ? cause.message : '処理に失敗しました。'
}

async function load(): Promise<void> {
  loading.value = true
  try {
    items.value = (await getTempFiles({
      keyword: keyword.value,
      type: typeFilter.value === 'すべて' ? undefined : typeFilter.value,
      period: period.value
    })).data
  } catch (cause) { toast.danger(messageOf(cause)); items.value = [] }
  finally { loading.value = false }
}

function applySearch(): void { void load() }

function toggleSelect(id: number): void {
  const next = new Set(selected.value)
  if (next.has(id)) next.delete(id)
  else next.add(id)
  selected.value = next
}

function openUpload(): void { uploadInput.value?.click() }

function onUploadChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files ?? [])
  if (files.length === 0) return
  void uploadTempFiles(files).then(() => {
    toast.success(`${files.length} 件を追加しました。`)
    input.value = ''
    void load()
  }).catch((cause) => toast.danger(messageOf(cause)))
}

function thumbUrl(file: TempFileSummary): string | null {
  // 旧 2.0 移行データの縮略ファイルは既に data: URL（接頭辞付き）で保存されている。
  // 一方、2.1 で新規アップロードした画像は裸の base64 で保存するため、ここで両者を吸収する。
  if (!file.image || !file.thumbnail) return null
  return file.thumbnail.startsWith('data:') ? file.thumbnail : `data:image/png;base64,${file.thumbnail}`
}

function formatBytes(bytes: number): string {
  if (bytes <= 0) return '0 B'
  if (bytes < 1024) return `${bytes} B`
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`
  return `${(bytes / (1024 * 1024)).toFixed(2)} MB`
}

function formatDate(value: string | null): string {
  if (!value) return '-'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  const pad = (n: number): string => String(n).padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

function kindLabel(file: TempFileSummary): string { return file.image ? '画像' : 'その他' }

function download(file: TempFileSummary): void {
  const anchor = document.createElement('a')
  anchor.href = `${file.contentUrl}?download=true`
  anchor.download = file.originalFileName
  anchor.click()
}

function downloadSelected(): void {
  const targets = items.value.filter((f) => selected.value.has(f.tempFileId))
  if (targets.length === 0) { toast.warning('ダウンロードするファイルを選択してください。'); return }
  targets.forEach(download)
  toast.info(`${targets.length} 件のダウンロードを開始しました。`)
}

async function remove(file: TempFileSummary): Promise<void> {
  if (!window.confirm(`「${file.originalFileName}」を削除しますか？`)) return
  try {
    await deleteTempFile(file.tempFileId)
    const next = new Set(selected.value)
    next.delete(file.tempFileId)
    selected.value = next
    toast.success(`${file.originalFileName} を削除しました。`)
    void load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

async function removeSelected(): Promise<void> {
  const targets = items.value.filter((f) => selected.value.has(f.tempFileId))
  if (targets.length === 0) { toast.warning('削除するファイルを選択してください。'); return }
  if (!window.confirm(`選択した ${targets.length} 件を削除しますか？`)) return
  try {
    await deleteTempFiles(targets.map((f) => f.tempFileId))
    selected.value = new Set()
    toast.success(`${targets.length} 件を削除しました。`)
    void load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

function openEditor(file: TempFileSummary): void {
  if (!file.image) { toast.info('画像以外は編集できません。'); return }
  editorFile.value = file
  editorOpen.value = true
}

function onEditorChanged(): void { void load() }

onMounted(() => { if (isFamilyUser.value) void load() })
</script>

<template>
  <div v-if="isFamilyUser" class="tf-page">
    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title"><AppIcon name="search" /> 検索条件</h3>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--primary" @click="applySearch"><AppIcon name="search" size="sm" /> 検索</button>
          <button type="button" class="btn btn--secondary" @click="openUpload"><AppIcon name="plus" size="sm" /> 新規</button>
          <button type="button" class="btn btn--secondary" @click="downloadSelected"><AppIcon name="download" size="sm" /> 原本画像DL</button>
          <button type="button" class="btn btn--danger-outline" @click="removeSelected"><AppIcon name="trash" size="sm" /> 選択削除</button>
        </div>
      </div>
      <div class="filters">
        <div class="filters__row">
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input v-model="keyword" class="input" placeholder="ファイル名・コメント" style="flex: 1" @keydown.enter.prevent="applySearch" />
          </span>
          <span class="filter-item">
            <span class="filter-item__label">タイプ：</span>
            <select v-model="typeFilter" class="select">
              <option>すべて</option><option>画像</option><option>その他</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">期間：</span>
            <select v-model="period" class="select">
              <option v-for="p in periodOptions" :key="p">{{ p }}</option>
            </select>
          </span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card__header">
        <h2 class="card__title">ファイル一覧</h2>
        <span class="card__sub">全 {{ items.length }} 件<template v-if="selected.size > 0">／選択 {{ selected.size }} 件</template></span>
      </div>
      <div class="card__body">
        <p v-if="loading" class="tf-muted">読込中...</p>
        <p v-else-if="items.length === 0" class="tf-muted">ファイルがありません。条件を変更するか、追加してください。</p>
        <div v-else class="tf-grid">
          <article v-for="file in items" :key="file.tempFileId" class="tf-card" :class="{ 'is-selected': selected.has(file.tempFileId) }">
            <label class="tf-card__check" title="選択" @click.stop>
              <input type="checkbox" :checked="selected.has(file.tempFileId)" @change="toggleSelect(file.tempFileId)" />
              <span class="tf-card__checkbox"><AppIcon name="check" size="sm" /></span>
            </label>

            <div v-if="file.image" class="tf-card__thumb">
              <img v-if="thumbUrl(file)" :src="thumbUrl(file)!" :alt="file.originalFileName" loading="lazy" />
              <AppIcon v-else name="image" />
            </div>
            <div v-else class="tf-card__thumb"><AppIcon name="file" /></div>

            <div class="tf-card__name" :title="file.originalFileName">{{ file.originalFileName }}</div>
            <div class="tf-card__meta">{{ kindLabel(file) }} ・ {{ formatBytes(file.fileSize) }} ・ {{ formatDate(file.createdAt) }}</div>
            <div class="tf-card__comment" :title="file.comment ?? ''">{{ file.comment ?? 'コメントなし' }}</div>
            <div class="tf-card__actions">
              <button v-if="file.image" type="button" class="btn btn--icon btn--sm" title="画像表示" aria-label="画像表示" @click="viewing = file">
                <AppIcon name="eye" size="sm" class="icon--view" />
              </button>
              <button v-if="file.image" type="button" class="btn btn--icon btn--sm" title="画像編集" @click="openEditor(file)">
                <AppIcon name="edit" size="sm" class="icon--edit" />
              </button>
              <button type="button" class="btn btn--icon btn--sm" title="ダウンロード" @click="download(file)">
                <AppIcon name="download" size="sm" />
              </button>
              <button type="button" class="btn btn--icon btn--sm is-danger" title="削除" @click="remove(file)">
                <AppIcon name="trash" size="sm" />
              </button>
            </div>
          </article>
        </div>
      </div>
    </div>

    <input ref="uploadInput" type="file" multiple style="display: none" @change="onUploadChange" />

    <TempFileImageEditor
      :file="editorFile"
      :open="editorOpen"
      @close="editorOpen = false"
      @changed="onEditorChanged"
    />

    <!-- 画像表示ページ（前へ/次へ・ズーム・左右90°回転）。資料管理と同じ共通ビューア -->
    <DocumentImageViewer
      v-if="viewing && viewingAsDocument"
      :images="previewImages"
      :current="viewingAsDocument"
      @update:current="syncViewerFile"
      @close="viewing = null"
    />
  </div>
  <section v-else class="card page-body">
    <h2>臨時ファイル管理</h2>
    <p>家族共有の臨時ファイルは、生徒または保護者としてログインした場合のみ表示できます。</p>
  </section>
</template>

<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import DocumentFilesDialog from '@/views/document/DocumentFilesDialog.vue'
import DocumentFileChips from '@/views/document/DocumentFileChips.vue'
import { CATEGORY_LABELS, folderCategories, folderOptions, folderPathNames } from '@/features/document-folder/folderPath'
import {
  createDocument, createFolder, deleteDocument, deleteFolder, getDocumentWorkspace, updateDocument, updateFolder,
  type DocumentFolder, type DocumentSummary, type SaveDocumentRequest
} from '@/api/documents'
import '@/features/document-folder/document-folder.css'

const toast = useToast()
const folders = ref<DocumentFolder[]>([])
const documents = ref<DocumentSummary[]>([])
const selectedFolderId = ref<number | null>(null)
const keyword = ref('')
const loading = ref(false)
const error = ref('')
const drawerOpen = ref(false)
const selected = ref<DocumentSummary | null>(null)
const uploadFiles = ref<File[]>([])
const filesOpen = ref(false)
const filesDocNo = ref('')

const draft = ref<SaveDocumentRequest>(emptyDraft())
const selectedFolder = computed(() => folders.value.find((f) => f.folderId === selectedFolderId.value))

interface FolderRow {
  folder: DocumentFolder
  depth: number
  expandable: boolean
  expanded: boolean
}

/** 折りたたみ状態（既定はすべて展開）。キーは 'root' または フォルダID の文字列。 */
const collapsedKeys = ref<Set<string>>(new Set())
const rootExpanded = computed(() => !collapsedKeys.value.has('root'))

function toggleExpand(key: string): void {
  const next = new Set(collapsedKeys.value)
  if (next.has(key)) next.delete(key)
  else next.add(key)
  collapsedKeys.value = next
}

/** ルート「すべての資料」の開閉（フォルダが無いときは何もしない）。 */
function toggleRoot(): void {
  if (folders.value.length > 0) toggleExpand('root')
}

/** 子フォルダを持つ行だけ開閉できる。 */
function toggleFolder(row: FolderRow): void {
  if (row.expandable) toggleExpand(String(row.folder.folderId))
}

const folderRows = computed<FolderRow[]>(() => {
  const children = new Map<number | null, DocumentFolder[]>()
  for (const folder of folders.value) {
    const key = folder.parentFolderId
    children.set(key, [...(children.get(key) ?? []), folder])
  }
  for (const group of children.values()) {
    // 同一階層は名称の昇順。フォルダ名の先頭に "01." 等の番号が入っている前提で、
    // 数字は数値として比較する（表示順カラムは手動作成フォルダで 0 のままのことがあり、
    // それを優先すると昇順にならないため使わない）。
    group.sort((a, b) => a.folderName.localeCompare(b.folderName, 'ja', { numeric: true }))
  }

  // ルートから到達できるかを先に確定する（折りたたみ状態とは無関係。孤立データの判定用）。
  const reachable = new Set<number>()
  const markReachable = (parentId: number | null): void => {
    for (const folder of children.get(parentId) ?? []) {
      if (reachable.has(folder.folderId)) continue
      reachable.add(folder.folderId)
      markReachable(folder.folderId)
    }
  }
  markReachable(null)

  const rows: FolderRow[] = []
  const visited = new Set<number>()
  const walk = (parentId: number | null, depth: number): void => {
    for (const folder of children.get(parentId) ?? []) {
      if (visited.has(folder.folderId)) continue
      visited.add(folder.folderId)
      const expandable = (children.get(folder.folderId)?.length ?? 0) > 0
      const expanded = !collapsedKeys.value.has(String(folder.folderId))
      rows.push({ folder, depth, expandable, expanded })
      if (expandable && expanded) walk(folder.folderId, depth + 1)
    }
  }
  if (rootExpanded.value) walk(null, 0)

  // 壊れた旧データ（ルートから到達できない行）があっても画面から消さず、末尾に表示する。
  for (const folder of folders.value) {
    if (!reachable.has(folder.folderId)) {
      rows.push({
        folder,
        depth: 0,
        expandable: (children.get(folder.folderId)?.length ?? 0) > 0,
        expanded: !collapsedKeys.value.has(String(folder.folderId))
      })
    }
  }
  return rows
})

function emptyDraft(): SaveDocumentRequest {
  return { folderId: selectedFolderId.value, status: '1', expiryDate: null, comment: null }
}

function messageOf(cause: unknown): string {
  return cause instanceof ApiError || cause instanceof Error ? cause.message : '処理に失敗しました。'
}

async function load(): Promise<void> {
  loading.value = true
  error.value = ''
  try {
    const response = await getDocumentWorkspace(selectedFolderId.value, keyword.value)
    folders.value = response.data.folders
    documents.value = response.data.documents
  } catch (cause) { error.value = messageOf(cause) }
  finally { loading.value = false }
}

/** ドロワーの分類表示（選択中フォルダの階層から導出。入力は受け付けない）。 */
const draftCategories = computed(() => folderCategories(folders.value, draft.value.folderId))
/** ドロワーのフォルダ選択肢（ツリーの折りたたみ状態に関係なく全フォルダを出す）。 */
const drawerFolderOptions = computed(() => folderOptions(folders.value))

/** 分類（大分類〜細分類）に対応する階層の上限。 */
const MAX_FOLDER_DEPTH = 4
const DEPTH_EXCEEDED = 'フォルダは大分類〜細分類の4階層までです。'
/** 選択中フォルダの深さ（未選択 = ルート直下に追加なので 0）。 */
const selectedDepth = computed(() => folderPathNames(folders.value, selectedFolderId.value).length)
/** 4 階層目（細分類）の下には追加できない。 */
const canAddFolder = computed(() => selectedDepth.value < MAX_FOLDER_DEPTH)

async function selectFolder(id: number | null): Promise<void> {
  selectedFolderId.value = id
  closeDrawer()
  await load()
}

function newDocument(): void {
  selected.value = null
  draft.value = emptyDraft()
  uploadFiles.value = []
  drawerOpen.value = true
}

function editDocument(document: DocumentSummary): void {
  selected.value = document
  draft.value = {
    folderId: document.folderId, status: document.status,
    expiryDate: document.expiryDate, comment: document.comment
  }
  uploadFiles.value = []
  drawerOpen.value = true
}

function chooseFiles(event: Event): void {
  uploadFiles.value = Array.from((event.target as HTMLInputElement).files ?? [])
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
    closeDrawer()
    await load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

async function removeDocument(): Promise<void> {
  if (!selected.value || !window.confirm(`${selected.value.documentNo} を削除しますか。`)) return
  try {
    await deleteDocument(selected.value.documentNo)
    toast.success('資料を削除しました。')
    closeDrawer()
    await load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

async function addFolder(): Promise<void> {
  if (!canAddFolder.value) { toast.danger(DEPTH_EXCEEDED); return }
  const name = window.prompt('新しいフォルダ名を入力してください。')?.trim()
  if (!name) return
  try {
    await createFolder({ parentFolderId: selectedFolderId.value, folderName: name, displayOrder: 0, note: null })
    toast.success('フォルダを作成しました。')
    await load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

async function removeFolder(): Promise<void> {
  if (selectedFolderId.value === null || !window.confirm('選択中のフォルダを削除しますか。')) return
  try {
    await deleteFolder(selectedFolderId.value)
    selectedFolderId.value = null
    toast.success('フォルダを削除しました。')
    await load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

async function renameFolder(): Promise<void> {
  const folder = selectedFolder.value
  if (!folder) return
  const name = window.prompt('フォルダ名を入力してください。', folder.folderName)?.trim()
  if (!name || name === folder.folderName) return
  try {
    await updateFolder(folder.folderId, { parentFolderId: folder.parentFolderId, folderName: name, displayOrder: folder.displayOrder, note: folder.note })
    toast.success('フォルダ名を変更しました。')
    await load()
  } catch (cause) { toast.danger(messageOf(cause)) }
}

function openFiles(no: string): void { filesDocNo.value = no; filesOpen.value = true }
function closeDrawer(): void { drawerOpen.value = false; selected.value = null; uploadFiles.value = [] }

onMounted(load)
</script>

<template>
  <div class="doc-folder-page">
    <p v-if="error" class="alert alert--danger">{{ error }}</p>
    <section class="doc-folder-workspace card">
      <aside class="doc-folder-tree" aria-label="資料フォルダ">
        <div class="doc-folder-tree__head">家族共有フォルダ</div>
        <div class="doc-folder-tree__body">
          <button class="doc-tree__row" :class="{ 'is-selected': selectedFolderId === null }" @click="selectFolder(null)">
            <span
              class="doc-tree__chev"
              :class="{ 'doc-tree__chev--blank': folders.length === 0 }"
              @click.stop="toggleRoot"
            >
              <AppIcon v-if="folders.length > 0" :name="rootExpanded ? 'chevron-down' : 'chevron-right'" size="sm" />
            </span>
            <AppIcon name="folder" size="sm" class="doc-tree__icon" />
            <span class="doc-tree__label">すべての資料</span>
          </button>
          <button
            v-for="row in folderRows" :key="row.folder.folderId" class="doc-tree__row"
            :class="{ 'is-selected': selectedFolderId === row.folder.folderId }"
            :style="{ paddingLeft: `${8 + row.depth * 16}px` }" @click="selectFolder(row.folder.folderId)"
          >
            <span
              class="doc-tree__chev"
              :class="{ 'doc-tree__chev--blank': !row.expandable }"
              @click.stop="toggleFolder(row)"
            >
              <AppIcon v-if="row.expandable" :name="row.expanded ? 'chevron-down' : 'chevron-right'" size="sm" />
            </span>
            <AppIcon name="folder" size="sm" class="doc-tree__icon" />
            <span class="doc-tree__label">{{ row.folder.folderName }}</span>
          </button>
        </div>
        <div class="doc-folder-tree__actions">
          <button
            type="button" class="btn btn--icon btn--sm"
            :title="canAddFolder ? 'フォルダ追加' : DEPTH_EXCEEDED" aria-label="フォルダ追加"
            :disabled="!canAddFolder" @click="addFolder"
          >
            <AppIcon name="plus" size="sm" />
          </button>
          <button
            type="button" class="btn btn--icon btn--sm" title="名称変更" aria-label="名称変更"
            :disabled="selectedFolderId === null" @click="renameFolder"
          >
            <AppIcon name="edit" size="sm" class="icon--edit" />
          </button>
          <button
            type="button" class="btn btn--icon btn--sm is-danger" title="削除" aria-label="削除"
            :disabled="selectedFolderId === null" @click="removeFolder"
          >
            <AppIcon name="trash" size="sm" />
          </button>
        </div>
      </aside>

      <main class="doc-folder-main">
        <div class="doc-folder-main__head">
          <span class="doc-folder-main__crumb">{{ selectedFolder?.folderName ?? 'すべての資料' }}</span>
          <span class="doc-folder-main__meta">{{ documents.length }} 件</span>
          <input v-model="keyword" class="input doc-folder-main__search" type="search" placeholder="資料番号・コメント" @keyup.enter="load" />
          <button class="btn btn--primary" type="button" @click="load"><AppIcon name="search" size="sm" />検索</button>
          <button class="btn btn--primary" type="button" @click="newDocument"><AppIcon name="plus" size="sm" /> 新規</button>
        </div>
        <div class="doc-folder-list">
          <div v-if="loading" class="doc-folder-list__empty">読込中...</div>
          <div v-else-if="documents.length === 0" class="doc-folder-list__empty">資料がありません</div>
          <div v-for="document in documents" v-else :key="document.documentNo" class="doc-card">
            <span class="doc-card__info">
              <span class="doc-card__head">
                <span class="doc-card__no">{{ document.documentNo }}</span>
                <DocumentFileChips :files="document.files" />
              </span>
              <span class="doc-card__sub">{{ document.comment || 'コメントなし' }} ／ {{ document.fileCount }} ファイル</span>
            </span>
            <span class="badge" :class="document.status === '1' ? 'badge--success' : 'badge--danger'">{{ document.status === '1' ? '有効' : '無効' }}</span>
            <span class="doc-card__expiry">{{ document.expiryDate || '期限なし' }}</span>
            <button class="doc-card__action" type="button" title="編集" aria-label="編集" @click.stop="editDocument(document)"><AppIcon name="edit" size="sm" class="icon--edit" /></button>
            <button class="doc-card__action" type="button" title="ファイル一覧" aria-label="ファイル一覧" @click.stop="openFiles(document.documentNo)"><AppIcon name="eye" size="sm" class="icon--view" /></button>
          </div>
        </div>
      </main>

      <aside class="doc-folder-drawer" :class="{ 'is-open': drawerOpen }" aria-label="資料詳細">
        <div class="doc-folder-drawer__head">
          <strong>{{ selected?.documentNo ?? '資料新規登録' }}</strong>
          <button class="btn btn--ghost btn--sm doc-folder-drawer__close" type="button" @click="closeDrawer">閉じる</button>
        </div>
        <div class="doc-folder-drawer__body">
          <label class="doc-folder-drawer__field">フォルダ<select v-model="draft.folderId" class="select"><option :value="null">未分類</option><option v-for="option in drawerFolderOptions" :key="option.folderId" :value="option.folderId">{{ option.label }}</option></select></label>
          <label class="doc-folder-drawer__field">ステータス<select v-model="draft.status" class="select"><option value="1">有効</option><option value="0">無効</option></select></label>
          <label class="doc-folder-drawer__field">有効期限<input v-model="draft.expiryDate" class="input" placeholder="YYYY/MM/DD" /></label>
          <p class="doc-folder-drawer__path">分類はフォルダの階層から自動的に決まります。</p>
          <div class="doc-folder-drawer__categories">
            <div v-for="(label, index) in CATEGORY_LABELS" :key="label" class="doc-folder-drawer__category">
              <span class="doc-folder-drawer__category-label">{{ label }}</span>
              <span class="doc-folder-drawer__category-value">{{ draftCategories[index] || '—' }}</span>
            </div>
          </div>
          <label class="doc-folder-drawer__field doc-folder-drawer__field--wide">コメント<textarea v-model="draft.comment" class="input" rows="3" /></label>
          <label v-if="!selected" class="doc-folder-drawer__field doc-folder-drawer__field--wide">ファイル（各20MB以下）<input type="file" multiple @change="chooseFiles" /></label>
          <p v-if="!selected && uploadFiles.length">{{ uploadFiles.length }} 件を選択済み</p>
          <button v-if="selected" class="btn btn--secondary" type="button" @click="openFiles(selected.documentNo)">ファイル管理</button>
        </div>
        <div class="doc-folder-drawer__foot"><button v-if="selected" class="btn btn--danger-outline" type="button" @click="removeDocument">削除</button><span style="flex:1"></span><button class="btn btn--primary" type="button" @click="save">保存</button></div>
      </aside>
    </section>
    <DocumentFilesDialog :doc-no="filesDocNo" :open="filesOpen" @close="filesOpen = false" @changed="load" />
  </div>
</template>

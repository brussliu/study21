<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useToast } from '@study21/web-shared'

/**
 * 臨時ファイル管理（デモ画面）
 * temp_file.jsp の機能を 2.1 のデザインで再現したモック。
 * 検索（キーワード／タイプ／期間）・選択・追加・原本画像DL・
 * 画像編集（切抜き範囲／回転／鮮明化／圧縮／別名保存）を UI 上で確認できる。
 * バックエンド処理は行わない（すべて Mock）。
 */
interface TempFile {
  id: number
  name: string
  kind: 'image' | 'other'
  comment: string
  sizeBytes: number
  registeredAt: number
}

const HOUR = 60 * 60 * 1000
const DAY = 24 * HOUR

function nowAgo(msAgo: number): number {
  return Date.now() - msAgo
}

const typeOptions = ['すべて', '画像', 'その他']
const periodOptions = ['すべて', '直近1日間', '直近1週間', '直近1ヵ月', '直近3ヵ月', '3ヵ月以前']

const initialFiles: TempFile[] = [
  { id: 1, name: 'img_001.png', kind: 'image', comment: '英単語ノートの写真', sizeBytes: 2417152, registeredAt: nowAgo(5 * HOUR) },
  { id: 2, name: 'img_002.png', kind: 'image', comment: '例文プリント', sizeBytes: 3145728, registeredAt: nowAgo(26 * HOUR) },
  { id: 3, name: 'note_003.jpg', kind: 'image', comment: '授業ノート', sizeBytes: 1876034, registeredAt: nowAgo(3 * DAY) },
  { id: 4, name: 'scan_004.png', kind: 'image', comment: 'スキャン取込', sizeBytes: 4096000, registeredAt: nowAgo(20 * DAY) },
  { id: 5, name: 'photo_005.jpg', kind: 'image', comment: 'カメラで撮影（12MB超）', sizeBytes: 12582912, registeredAt: nowAgo(60 * DAY) },
  { id: 6, name: 'memo_006.png', kind: 'image', comment: '', sizeBytes: 908412, registeredAt: nowAgo(120 * DAY) },
  { id: 7, name: 'handout_007.pdf', kind: 'other', comment: '補助教材（PDF）', sizeBytes: 1250000, registeredAt: nowAgo(200 * DAY) }
]

const toast = useToast()

// ---- 検索条件（入力状態と適用済み状態を分離。検索ボタンで反映） ----
const qKeyword = ref('')
const qType = ref('すべて')
const qPeriod = ref('すべて')
const kwApplied = ref('')
const typeApplied = ref('すべて')
const periodApplied = ref('すべて')

const files = ref<TempFile[]>(initialFiles.map((f) => ({ ...f })))
const selectedIds = ref<Set<number>>(new Set())

// ---- 画像編集ダイアログ ----
const editOpen = ref(false)
const editingId = ref<number | null>(null)
const editName = ref('')
const editComment = ref('')
const enhance = ref(0)
const rotation = ref(0)
const compress = ref<{ before: number; after: number | null }>({ before: 0, after: null })
const editNameInput = ref<HTMLInputElement | null>(null)

const uploadInput = ref<HTMLInputElement | null>(null)

const editingFile = computed<TempFile | null>(() => files.value.find((f) => f.id === editingId.value) ?? null)

const periodLimitMs: Record<string, number> = {
  '直近1日間': DAY,
  '直近1週間': 7 * DAY,
  '直近1ヵ月': 30 * DAY,
  '直近3ヵ月': 90 * DAY
}

function inPeriod(file: TempFile, period: string): boolean {
  if (period === 'すべて') return true
  const age = Date.now() - file.registeredAt
  if (period === '3ヵ月以前') return age > 90 * DAY
  return age <= (periodLimitMs[period] ?? Infinity)
}

const filteredFiles = computed<TempFile[]>(() => {
  const kw = kwApplied.value.trim().toLowerCase()
  return files.value
    .filter((f) => {
      if (kw !== '' && !`${f.name} ${f.comment}`.toLowerCase().includes(kw)) return false
      if (typeApplied.value === '画像' && f.kind !== 'image') return false
      if (typeApplied.value === 'その他' && f.kind !== 'other') return false
      return inPeriod(f, periodApplied.value)
    })
    .sort((a, b) => b.registeredAt - a.registeredAt)
})

function applySearch(): void {
  kwApplied.value = qKeyword.value
  typeApplied.value = qType.value
  periodApplied.value = qPeriod.value
}

// ---- 選択／削除 ----
function toggleSelect(file: TempFile): void {
  const next = new Set(selectedIds.value)
  if (next.has(file.id)) next.delete(file.id)
  else next.add(file.id)
  selectedIds.value = next
}

function removeFile(file: TempFile): void {
  if (!window.confirm(`「${file.name}」を削除しますか？`)) return
  files.value = files.value.filter((f) => f.id !== file.id)
  const next = new Set(selectedIds.value)
  next.delete(file.id)
  selectedIds.value = next
  toast.success(`${file.name} を削除しました（Mock）`)
}

function deleteSelected(): void {
  const targets = files.value.filter((f) => selectedIds.value.has(f.id))
  if (targets.length === 0) {
    toast.warning('削除するファイルを選択してください。')
    return
  }
  if (!window.confirm(`選択した ${targets.length} 件を削除しますか？`)) return
  const ids = new Set(targets.map((f) => f.id))
  files.value = files.value.filter((f) => !ids.has(f.id))
  selectedIds.value = new Set()
  toast.success(`${targets.length} 件を削除しました（Mock）`)
}

function downloadSelected(): void {
  const targets = files.value.filter((f) => selectedIds.value.has(f.id) && f.kind === 'image')
  if (targets.length === 0) {
    toast.warning('ダウンロードする画像（原本画像DL）を選択してください。')
    return
  }
  toast.info(`${targets.length} 件の原本画像のダウンロードを開始しました（Mock・ブラウザのDL確認が必要です）`)
}

// ---- 追加（ファイル選択のみ・アップロードは Mock） ----
function openUploadPicker(): void {
  uploadInput.value?.click()
}

function onUploadChange(event: Event): void {
  const input = event.target as HTMLInputElement
  const chosen = Array.from(input.files ?? [])
  if (chosen.length === 0) return
  const added: TempFile[] = chosen.map((file) => ({
    id: nextId(),
    name: file.name,
    kind: file.type.startsWith('image/') ? 'image' : 'other',
    comment: '',
    sizeBytes: file.size || 1024,
    registeredAt: Date.now()
  }))
  files.value = [...files.value, ...added]
  toast.success(`${added.length} 件を追加しました（Mock・サーバーには保存していません）`)
  input.value = ''
}

let idSeq = 1000
function nextId(): number {
  idSeq += 1
  return idSeq
}

// ---- 画像編集 ----
async function openEditor(file: TempFile): Promise<void> {
  editingId.value = file.id
  editName.value = file.name
  editComment.value = file.comment
  enhance.value = 0
  rotation.value = 0
  compress.value = { before: file.sizeBytes, after: null }
  editOpen.value = true
  await nextTick()
  editNameInput.value?.focus()
}

function closeEditor(): void {
  editOpen.value = false
  editingId.value = null
}

function rotateLeft(): void {
  rotation.value = (rotation.value - 90 + 360) % 360
}

function rotateRight(): void {
  rotation.value = (rotation.value + 90) % 360
}

function resetImage(): void {
  rotation.value = 0
  enhance.value = 0
  const file = editingFile.value
  compress.value = file ? { before: file.sizeBytes, after: null } : { before: 0, after: null }
}

function compressImage(): void {
  const file = editingFile.value
  if (!file) return
  const after = Math.min(file.sizeBytes, 8 * 1024 * 1024)
  if (after >= file.sizeBytes) {
    toast.info('すでに 8MB 以下なので圧縮は不要です（Mock）')
    return
  }
  compress.value = { before: file.sizeBytes, after }
  toast.success(`圧縮しました（Mock）: ${formatBytes(file.sizeBytes)} → ${formatBytes(after)}`)
}

function saveEditor(): void {
  const id = editingId.value
  if (id == null) return
  const after = compress.value.after
  files.value = files.value.map((f) =>
    f.id === id
      ? {
          ...f,
          name: editName.value.trim() !== '' ? editName.value.trim() : f.name,
          comment: editComment.value.trim(),
          sizeBytes: after != null ? after : f.sizeBytes
        }
      : f
  )
  toast.success('画像の編集内容を保存しました（Mock）')
  closeEditor()
}

function uniqueCopyName(base: string): string {
  const dot = base.lastIndexOf('.')
  const stem = dot > 0 ? base.slice(0, dot) : base
  const ext = dot > 0 ? base.slice(dot) : ''
  const taken = new Set(files.value.map((f) => f.name))
  let name = `${stem}（コピー）${ext}`
  let i = 2
  while (taken.has(name)) {
    name = `${stem}（コピー${i}）${ext}`
    i += 1
  }
  return name
}

function saveEditorAs(): void {
  const file = editingFile.value
  if (!file) return
  const name = editName.value.trim() !== '' ? editName.value.trim() : file.name
  const copy: TempFile = {
    id: nextId(),
    name: uniqueCopyName(name),
    kind: 'image',
    comment: editComment.value.trim(),
    sizeBytes: compress.value.after ?? file.sizeBytes,
    registeredAt: Date.now()
  }
  files.value = [copy, ...files.value]
  toast.success(`別名保存しました（Mock）: ${copy.name}`)
  closeEditor()
}

// ---- 表示ヘルパー ----
function formatBytes(sizeBytes: number): string {
  const size = Number(sizeBytes || 0)
  if (size <= 0) return '0 B'
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  if (size < 1024 * 1024 * 1024) return `${(size / (1024 * 1024)).toFixed(2)} MB`
  return `${(size / (1024 * 1024 * 1024)).toFixed(2)} GB`
}

function formatDate(epoch: number): string {
  const date = new Date(epoch)
  const pad = (v: number): string => String(v).padStart(2, '0')
  return `${date.getFullYear()}/${pad(date.getMonth() + 1)}/${pad(date.getDate())}`
}

function kindLabel(kind: TempFile['kind']): string {
  return kind === 'image' ? '画像' : 'その他'
}

function onKeydown(event: KeyboardEvent): void {
  if (event.key === 'Escape' && editOpen.value) closeEditor()
}

onMounted(() => {
  document.addEventListener('keydown', onKeydown)
})

onBeforeUnmount(() => {
  document.removeEventListener('keydown', onKeydown)
})
</script>

<template>
  <div class="prototype-screen prototype-screen--temp-file">
    <div class="search-panel">
      <div class="search-panel__head">
        <h3 class="search-panel__title"><svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索条件</h3>
        <div class="search-panel__actions">
          <button type="button" class="btn btn--primary" @click.stop="applySearch">
            <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-search"></use></svg>検索
          </button>
          <button type="button" class="btn btn--secondary" @click.stop="openUploadPicker">
            <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-plus"></use></svg>追加
          </button>
          <button type="button" class="btn btn--secondary" @click.stop="downloadSelected">
            <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-download"></use></svg>原本画像DL
          </button>
          <button type="button" class="btn btn--danger-outline" @click.stop="deleteSelected">
            <svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg>選択削除
          </button>
        </div>
      </div>
      <div class="filters">
        <div class="filters__row">
          <span class="filter-item filter-item--grow">
            <span class="filter-item__label">キーワード：</span>
            <input v-model="qKeyword" class="input" placeholder="ファイル名・コメント" style="flex: 1" @keydown.enter.prevent="applySearch">
          </span>
          <span class="filter-item">
            <span class="filter-item__label">タイプ：</span>
            <select v-model="qType" class="select">
              <option v-for="t in typeOptions" :key="t">{{ t }}</option>
            </select>
          </span>
          <span class="filter-item">
            <span class="filter-item__label">期間：</span>
            <select v-model="qPeriod" class="select">
              <option v-for="p in periodOptions" :key="p">{{ p }}</option>
            </select>
          </span>
        </div>
      </div>
    </div>

    <div class="card">
      <div class="card__header">
        <h2 class="card__title">ファイル一覧</h2>
        <span class="card__sub">
          全 {{ filteredFiles.length }} 件<template v-if="selectedIds.size > 0">／選択 {{ selectedIds.size }} 件</template>
        </span>
      </div>
      <div class="card__body">
        <div v-if="filteredFiles.length === 0" class="tf-empty">
          <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-file"></use></svg>
          該当するファイルがありません。検索条件を変更してください。
        </div>
        <div v-else class="file-grid">
          <article
            v-for="f in filteredFiles"
            :key="f.id"
            class="tf-file-card"
            :class="{ 'is-selected': selectedIds.has(f.id) }"
          >
            <label class="tf-file-card__check" title="選択" @click.stop>
              <input type="checkbox" :checked="selectedIds.has(f.id)" @change="toggleSelect(f)">
              <span class="tf-file-card__checkbox">
                <svg class="icon icon--sm"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>
              </span>
            </label>

            <button
              v-if="f.kind === 'image'"
              type="button"
              class="tf-file-card__thumb"
              title="画像編集を開く"
              @click.stop="openEditor(f)"
            >
              <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg>
            </button>
            <div v-else class="tf-file-card__thumb">
              <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-file"></use></svg>
            </div>

            <div class="tf-file-card__name" :title="f.name">{{ f.name }}</div>
            <div class="tf-file-card__meta">{{ kindLabel(f.kind) }} ・ {{ formatBytes(f.sizeBytes) }} ・ 登録 {{ formatDate(f.registeredAt) }}</div>
            <div class="tf-file-card__comment" :title="f.comment">{{ f.comment !== '' ? f.comment : 'コメントなし' }}</div>
            <div class="tf-file-card__actions">
              <button
                v-if="f.kind === 'image'"
                type="button"
                class="btn btn--icon btn--sm"
                title="画像編集"
                @click.stop="openEditor(f)"
              >
                <svg class="icon icon--edit"><use href="/prototype-assets/icons/icons.svg#i-edit"></use></svg>
              </button>
              <button type="button" class="btn btn--icon btn--sm is-danger" title="削除" @click.stop="removeFile(f)">
                <svg class="icon icon--danger"><use href="/prototype-assets/icons/icons.svg#i-trash"></use></svg>
              </button>
            </div>
          </article>
        </div>
      </div>
    </div>

    <input ref="uploadInput" type="file" multiple style="display: none" @change="onUploadChange">

    <!-- 画像編集ダイアログ（切抜き範囲・回転・鮮明化・圧縮・別名保存） -->
    <div v-if="editOpen" class="overlay" @click.self="closeEditor">
      <div class="dialog dialog--lg" role="dialog" aria-modal="true" aria-labelledby="tfEditorTitle">
        <div class="dialog__head">
          <h2 class="dialog__title" id="tfEditorTitle">
            <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg>
            画像編集
            <span class="dialog__subtitle">{{ editingFile?.name ?? '' }} を編集します（Mock）</span>
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" @click.stop="closeEditor">
            <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-x"></use></svg>
          </button>
        </div>
        <div class="dialog__body">
          <div class="row" style="gap: 16px; align-items: flex-start; flex-wrap: nowrap">
            <div class="grow">
              <div class="tf-editor-stage">
                <div class="tf-editor-img" :style="{ transform: `rotate(${rotation}deg)` }">
                  <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-image"></use></svg>
                  <span>プレビュー（Mock）</span>
                </div>
                <div class="tf-editor-crop" title="切り抜き範囲（Mock）">
                  <i class="tf-editor-handle tf-editor-handle--nw"></i>
                  <i class="tf-editor-handle tf-editor-handle--ne"></i>
                  <i class="tf-editor-handle tf-editor-handle--sw"></i>
                  <i class="tf-editor-handle tf-editor-handle--se"></i>
                </div>
              </div>
              <div class="tf-editor-meta">
                <div>現在ファイルサイズ: {{ editingFile ? formatBytes(editingFile.sizeBytes) : '-' }}</div>
                <div>圧縮後サイズ: {{ compress.after != null ? formatBytes(compress.after) : '-' }}</div>
                <div>サイズ: 1280px × 960px</div>
                <div>位置: (0, 0)</div>
              </div>
            </div>
            <div class="stack" style="gap: 10px; width: 260px; flex: none">
              <div class="field">
                <label class="field__label" for="tfEditorName">ファイル名</label>
                <input id="tfEditorName" ref="editNameInput" v-model="editName" class="input" type="text" maxlength="255">
              </div>
              <div class="field">
                <label class="field__label" for="tfEditorComment">コメント</label>
                <textarea id="tfEditorComment" v-model="editComment" class="input" rows="4" placeholder="コメントを入力してください"></textarea>
              </div>
              <div class="field">
                <label class="field__label" for="tfEditorEnhance">文書鮮明化 <span class="num">{{ enhance }}%</span></label>
                <input id="tfEditorEnhance" v-model.number="enhance" type="range" min="0" max="100" step="5" style="width: 100%">
              </div>
            </div>
          </div>
        </div>
        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" @click.stop="rotateLeft">左回転</button>
          <button type="button" class="btn btn--secondary" @click.stop="rotateRight">右回転</button>
          <button type="button" class="btn btn--secondary" disabled title="現在利用できません">AIで手書き除去</button>
          <button type="button" class="btn btn--secondary" @click.stop="compressImage">8MB以下に圧縮</button>
          <button type="button" class="btn btn--secondary" @click.stop="resetImage">リセット</button>
          <span style="flex: 1"></span>
          <button type="button" class="btn btn--secondary" @click.stop="closeEditor">キャンセル</button>
          <button type="button" class="btn btn--secondary" @click.stop="saveEditorAs">
            <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-copy"></use></svg>別名保存
          </button>
          <button type="button" class="btn btn--primary" @click.stop="saveEditor">
            <svg class="icon"><use href="/prototype-assets/icons/icons.svg#i-check"></use></svg>保存
          </button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.file-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
}

.tf-empty {
  padding: 32px 16px;
  text-align: center;
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
}
.tf-empty .icon {
  width: 28px;
  height: 28px;
  color: var(--color-text-subtle);
}

.tf-file-card {
  position: relative;
  display: flex;
  flex-direction: column;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: var(--color-surface);
  transition: border-color var(--duration-fast) var(--ease-standard), box-shadow var(--duration-fast) var(--ease-standard);
}
.tf-file-card:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
}
.tf-file-card.is-selected {
  border-color: var(--color-primary);
  box-shadow: 0 0 0 3px var(--color-primary-soft);
}

.tf-file-card__check {
  position: absolute;
  top: 8px;
  right: 8px;
  z-index: 2;
  cursor: pointer;
}
.tf-file-card__check input {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  opacity: 0;
  cursor: pointer;
  margin: 0;
}
.tf-file-card__checkbox {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 20px;
  height: 20px;
  border-radius: 50%;
  border: 1.5px solid var(--color-border-strong);
  background: rgba(255, 255, 255, 0.92);
  color: transparent;
}
.tf-file-card__check input:checked + .tf-file-card__checkbox {
  background: var(--color-primary);
  border-color: var(--color-primary);
  color: #fff;
}
.tf-file-card__check input:focus-visible + .tf-file-card__checkbox {
  outline: 2px solid var(--color-primary-strong);
  outline-offset: 2px;
}

.tf-file-card__thumb {
  width: 100%;
  aspect-ratio: 4 / 3;
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  background: var(--color-surface-alt);
  color: var(--color-text-subtle);
  cursor: default;
}
button.tf-file-card__thumb {
  cursor: pointer;
}
button.tf-file-card__thumb:hover {
  color: var(--color-primary-strong);
  background: var(--color-primary-soft);
}
.tf-file-card__thumb .icon {
  width: 28px;
  height: 28px;
}

.tf-file-card__name {
  padding: var(--sp-2) var(--sp-3) 0;
  font-size: var(--fs-sm);
  font-weight: var(--fw-medium, 600);
  color: var(--color-text-strong);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tf-file-card__meta {
  padding: 2px var(--sp-3) 0;
  font-size: var(--fs-2xs);
  color: var(--color-text-subtle);
}
.tf-file-card__comment {
  padding: 4px var(--sp-3) 0;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tf-file-card__actions {
  display: flex;
  gap: 2px;
  padding: var(--sp-2) var(--sp-2) var(--sp-2) var(--sp-3);
  margin-top: auto;
}

/* ---- 画像編集プレビュー ---- */
.tf-editor-stage {
  position: relative;
  height: 300px;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface-alt);
}
.tf-editor-img {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--sp-2);
  color: var(--color-text-subtle);
  font-size: var(--fs-sm);
  transition: transform 0.2s var(--ease-standard);
}
.tf-editor-img .icon {
  width: 56px;
  height: 56px;
}
.tf-editor-crop {
  position: absolute;
  top: 22%;
  left: 18%;
  width: 64%;
  height: 56%;
  border: 1.5px dashed var(--color-primary-strong);
  background: rgba(255, 255, 255, 0.12);
  pointer-events: none;
}
.tf-editor-handle {
  position: absolute;
  width: 10px;
  height: 10px;
  border: 2px solid #fff;
  border-radius: 2px;
  background: var(--color-primary-strong);
  box-shadow: 0 0 0 1px var(--color-primary-strong);
}
.tf-editor-handle--nw { top: -6px; left: -6px; }
.tf-editor-handle--ne { top: -6px; right: -6px; }
.tf-editor-handle--sw { bottom: -6px; left: -6px; }
.tf-editor-handle--se { bottom: -6px; right: -6px; }

.tf-editor-meta {
  display: flex;
  flex-wrap: wrap;
  gap: var(--sp-2) var(--sp-4);
  margin-top: var(--sp-2);
  font-size: var(--fs-xs);
  color: var(--color-text-subtle);
}

@media (max-width: 960px) {
  .tf-editor-stage {
    height: 200px;
  }
}
</style>

<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import DocumentImageViewer from '@/views/document/DocumentImageViewer.vue'
import TestInfoAnnotator from '@/views/testinfo/TestInfoAnnotator.vue'
import TestInfoTempFilePicker from '@/views/testinfo/TestInfoTempFilePicker.vue'
import TestInfoFilePicker from '@/views/testinfo/TestInfoFilePicker.vue'
import {
  addTestFileFromTempFile, addTestFileFromTestFile, createTestInfo, deleteTestFile,
  saveTestFileImage, updateTestInfo, uploadTestFiles,
  type SaveTestInfoRequest, type TestFile, type TestRow
} from '@/api/testinfo'
import { fileIcon, fileIconClass } from '@/features/document-files/document-file'
import {
  formatBytes, isConflict, messageOf, rotateDataUrl, rotateImageFile, toDocumentFileInfo
} from '@/features/testinfo/testinfo-file'
import '@/features/document-files/document-files.css'

/**
 * テスト情報の新規/編集ダイアログ（2.0 の testinfo_inputdialog1.jsp 相当）。
 * 基本項目と試験用紙ファイル（最大 10 件）をまとめて編集する。
 */
const props = defineProps<{ open: boolean; row: TestRow | null }>()
const emit = defineEmits<{ close: []; saved: [] }>()

const MAX_FILES = 10
const SUBJECT_OPTIONS = ['英語', '数学', '国語', '物理', '化学', '生物', '歴史', '地理']
const KIND_OPTIONS = ['通常', '模擬', '復習', '練習']

const toast = useToast()

interface ExistingItem { kind: 'existing'; file: TestFile; comment: string }
interface NewItem { kind: 'new'; file: File; comment: string; previewUrl: string }
type FileItem = ExistingItem | NewItem

const draft = ref({
  testName: '', subject: SUBJECT_OPTIONS[0], kind: KIND_OPTIONS[0],
  // <input type="number"> の v-model は数値を入れるため、得点・満点は string | number を受ける。
  examDate: '', score: '' as string | number, fullScore: '' as string | number, memo: ''
})
const items = ref<FileItem[]>([])
const version = ref(1)
const testNo = ref('')
const busy = ref(false)
const dragging = ref(false)
const fileInput = ref<HTMLInputElement | null>(null)
const viewer = ref<{ url: string; name: string } | null>(null)
const annotating = ref<FileItem | null>(null)
const tempPickerOpen = ref(false)
const testPickerOpen = ref(false)

const isNew = computed(() => testNo.value === '')
const remaining = computed(() => Math.max(0, MAX_FILES - items.value.length))
const existingItems = computed(() => items.value.filter((item): item is ExistingItem => item.kind === 'existing'))
const newItems = computed(() => items.value.filter((item): item is NewItem => item.kind === 'new'))
const canUsePicker = computed(() => !isNew.value && remaining.value > 0 && !busy.value)

/** 正答率: 満点が 0 以下なら「--」、それ以外は 得点/満点*100 を 0〜100 にクランプし小数 1 桁。 */
const accuracyText = computed(() => {
  const full = numberOrNull(draft.value.fullScore)
  const score = numberOrNull(draft.value.score)
  if (full === null || full <= 0) return '--'
  const rate = Math.min(100, Math.max(0, ((score ?? 0) / full) * 100))
  return `${rate.toFixed(1)}%`
})

/**
 * 入力値を数値へ。number 型 input の v-model は数値を入れるため、
 * 文字列・数値・未入力のいずれも受け取れるようにする（以前は .trim() で例外になっていた）。
 */
function numberOrNull(value: string | number | null | undefined): number | null {
  if (value === null || value === undefined) return null
  const text = typeof value === 'number' ? String(value) : value.trim()
  if (text === '') return null
  const parsed = Number(text)
  return Number.isFinite(parsed) ? parsed : null
}

function itemKey(item: FileItem): string {
  return item.kind === 'existing' ? `e-${item.file.fileId}` : `n-${item.file.name}-${item.file.lastModified}`
}

function itemName(item: FileItem): string {
  return item.kind === 'existing' ? item.file.originalFileName : item.file.name
}

function itemPreviewUrl(item: FileItem): string | null {
  return item.kind === 'existing' ? (item.file.previewUrl ?? (item.file.image ? item.file.contentUrl : null)) : item.previewUrl
}

function itemDocument(item: FileItem): ReturnType<typeof toDocumentFileInfo> {
  if (item.kind === 'existing') return toDocumentFileInfo(item.file)
  return {
    branchNo: 0,
    originalFileName: item.file.name,
    extension: (item.file.name.split('.').pop() ?? '').toLowerCase(),
    comment: item.comment.trim() === '' ? null : item.comment,
    contentUrl: item.previewUrl,
    image: item.file.type.startsWith('image/')
  }
}

function isImageItem(item: FileItem): boolean {
  return item.kind === 'existing' ? item.file.image : item.file.type.startsWith('image/')
}

// ---- 初期化 ----
watch(() => [props.open, props.row?.testNo] as const, () => {
  if (!props.open) return
  const row = props.row
  draft.value = {
    testName: row?.testName ?? '',
    subject: row?.subject ?? SUBJECT_OPTIONS[0],
    kind: row?.kind ?? KIND_OPTIONS[0],
    examDate: row?.examDate ? row.examDate.slice(0, 10) : '',
    score: row?.score === null || row?.score === undefined ? '' : String(row.score),
    fullScore: row?.fullScore === null || row?.fullScore === undefined ? '' : String(row.fullScore),
    memo: row?.memo ?? ''
  }
  items.value = (row?.files ?? []).map((file) => ({ kind: 'existing', file, comment: file.comment ?? '' }))
  version.value = row?.version ?? 1
  testNo.value = row?.testNo ?? ''
  viewer.value = null
  annotating.value = null
  busy.value = false
}, { immediate: true })

/** API 応答の行を反映する（既存ファイルの並び・備考は編集中の値を保持）。 */
function applyRow(row: TestRow): void {
  const comments = new Map(existingItems.value.map((item) => [item.file.fileId, item.comment]))
  items.value = [
    ...row.files.map((file): ExistingItem => ({ kind: 'existing', file, comment: comments.get(file.fileId) ?? file.comment ?? '' })),
    ...newItems.value
  ]
  version.value = row.version
  testNo.value = row.testNo
}

function close(): void {
  if (busy.value) return
  items.value.filter((item): item is NewItem => item.kind === 'new').forEach((item) => URL.revokeObjectURL(item.previewUrl))
  emit('close')
}

// ---- ファイル追加 ----
function addFiles(files: File[]): void {
  if (files.length === 0) return
  const accepted = files.slice(0, remaining.value)
  if (accepted.length < files.length) toast.warning(`試験用紙ファイルは最大 ${MAX_FILES} 件までです。`)
  items.value = [
    ...items.value,
    ...accepted.map((file): NewItem => ({
      kind: 'new', file, comment: '', previewUrl: URL.createObjectURL(file)
    }))
  ]
}

function pickFiles(): void { fileInput.value?.click() }

function chooseFiles(event: Event): void {
  const input = event.target as HTMLInputElement
  addFiles(Array.from(input.files ?? []))
  input.value = ''
}

function onDrop(event: DragEvent): void {
  dragging.value = false
  addFiles(Array.from(event.dataTransfer?.files ?? []))
}

/** Ctrl+V 貼り付け（クリップボードの画像）に対応する。 */
function onPaste(event: ClipboardEvent): void {
  if (!props.open || busy.value) return
  const files = Array.from(event.clipboardData?.files ?? []).filter((file) => file.type.startsWith('image/'))
  if (files.length === 0) return
  event.preventDefault()
  addFiles(files)
}

function removeItem(index: number): void {
  const target = items.value[index]
  if (!target) return
  if (target.kind === 'existing' && !isNew.value) {
    void removeExisting(target)
    return
  }
  if (target.kind === 'new') URL.revokeObjectURL(target.previewUrl)
  items.value = items.value.filter((_, i) => i !== index)
}

async function removeExisting(item: ExistingItem): Promise<void> {
  if (!window.confirm(`「${item.file.originalFileName}」を削除しますか。`)) return
  try {
    const response = await deleteTestFile(testNo.value, item.file.fileId)
    applyRow(response.data.row)
    toast.success('ファイルを削除しました。')
  } catch (cause) { toast.danger(messageOf(cause)) }
}

function move(index: number, delta: number): void {
  const target = index + delta
  if (target < 0 || target >= items.value.length) return
  const next = [...items.value]
  const [moved] = next.splice(index, 1)
  if (!moved) return
  next.splice(target, 0, moved)
  items.value = next
}

// ---- 回転・注釈・プレビュー ----
async function rotateItem(index: number, degrees: number): Promise<void> {
  const item = items.value[index]
  if (!item || busy.value) return
  if (item.kind === 'new') {
    try {
      const rotated = await rotateImageFile(item.file, degrees)
      URL.revokeObjectURL(item.previewUrl)
      items.value = items.value.map((current, i) => i === index
        ? { kind: 'new', file: rotated, comment: current.comment, previewUrl: URL.createObjectURL(rotated) }
        : current)
    } catch (cause) { toast.danger(messageOf(cause)) }
    return
  }
  try {
    busy.value = true
    const rotated = await rotateDataUrl(item.file.contentUrl, degrees)
    const blob = await (await fetch(rotated)).blob()
    const response = await saveTestFileImage(testNo.value, item.file.fileId, blob)
    applyRow(response.data.row)
    toast.success('画像を回転して保存しました。')
  } catch (cause) { toast.danger(messageOf(cause)) }
  finally { busy.value = false }
}

async function saveAnnotation(blob: Blob): Promise<void> {
  const item = annotating.value
  if (!item) return
  if (item.kind === 'new') {
    const rewritten = new File([blob], item.file.name, { type: blob.type, lastModified: item.file.lastModified })
    URL.revokeObjectURL(item.previewUrl)
    items.value = items.value.map((current) => current === item
      ? { kind: 'new', file: rewritten, comment: current.comment, previewUrl: URL.createObjectURL(rewritten) }
      : current)
    return
  }
  const response = await saveTestFileImage(testNo.value, item.file.fileId, blob)
  applyRow(response.data.row)
}

function previewItem(item: FileItem): void {
  const url = isImageItem(item) ? (item.kind === 'existing' ? item.file.contentUrl : item.previewUrl) : null
  if (url) { viewer.value = { url, name: itemName(item) }; return }
  if (item.kind === 'existing') {
    window.open(item.file.contentUrl, '_blank', 'noopener')
  }
}

// ---- ピッカー ----
async function pickFromTempFiles(tempFileIds: number[]): Promise<void> {
  tempPickerOpen.value = false
  if (isNew.value) return
  busy.value = true
  try {
    let last: TestRow | null = null
    for (const id of tempFileIds) last = (await addTestFileFromTempFile(testNo.value, id)).data.row
    if (last) applyRow(last)
    toast.success(`${tempFileIds.length} 件を取り込みました。`)
  } catch (cause) { toast.danger(messageOf(cause)) }
  finally { busy.value = false }
}

async function pickFromTestFiles(picks: Array<{ sourceTestNo: string; sourceFileId: number }>): Promise<void> {
  testPickerOpen.value = false
  if (isNew.value) return
  busy.value = true
  try {
    let last: TestRow | null = null
    for (const pick of picks) last = (await addTestFileFromTestFile(testNo.value, pick.sourceTestNo, pick.sourceFileId)).data.row
    if (last) applyRow(last)
    toast.success(`${picks.length} 件を取り込みました。`)
  } catch (cause) { toast.danger(messageOf(cause)) }
  finally { busy.value = false }
}

// ---- 保存 ----
function validate(): string | null {
  if (draft.value.testName.trim() === '') return 'テスト名を入力してください。'
  const full = numberOrNull(draft.value.fullScore)
  const score = numberOrNull(draft.value.score)
  if (full !== null && full > 0 && score !== null && score > full) return '得点数は満点数以下で入力してください。'
  return null
}

function buildPayload(): SaveTestInfoRequest {
  return {
    testName: draft.value.testName.trim(),
    subject: draft.value.subject,
    kind: draft.value.kind,
    examDate: draft.value.examDate.trim() === '' ? null : draft.value.examDate,
    score: numberOrNull(draft.value.score),
    fullScore: numberOrNull(draft.value.fullScore),
    memo: draft.value.memo.trim() === '' ? null : draft.value.memo.trim()
  }
}

/** 編集中の並び順どおりに既存ファイルの表示順・備考を作る。 */
function fileOrders(): Array<{ fileId: number; displayOrder: number; comment: string | null }> {
  return items.value
    .filter((item): item is ExistingItem => item.kind === 'existing')
    .map((item, index) => ({
      fileId: item.file.fileId,
      displayOrder: index,
      comment: item.comment.trim() === '' ? null : item.comment.trim()
    }))
}

/** 追加直後のファイルに fileId を割り当てる（同名 → 未使用の先頭、の順に照合）。 */
function assignNewFileIds(row: TestRow, knownIds: Set<number>): number[] {
  const candidates = row.files.filter((file) => !knownIds.has(file.fileId))
  const used = new Set<number>()
  return newItems.value.map((item, index) => {
    const byName = candidates.find((file) => !used.has(file.fileId) && file.originalFileName === item.file.name)
    const chosen = byName ?? candidates.find((file) => !used.has(file.fileId)) ?? candidates[index]
    if (chosen) used.add(chosen.fileId)
    return chosen?.fileId ?? 0
  })
}

async function save(): Promise<void> {
  const invalid = validate()
  if (invalid) { toast.danger(invalid); return }
  busy.value = true
  try {
    const payload = buildPayload()
    const newFiles = newItems.value.map((item) => item.file)
    let row: TestRow

    if (isNew.value) {
      row = (await createTestInfo(payload, newFiles)).data.row
      if (newItems.value.some((item) => item.comment.trim() !== '')) {
        row = (await updateTestInfo(row.testNo, { ...payload, version: row.version, files: [] })).data.row
      }
    } else {
      row = (await updateTestInfo(testNo.value, { ...payload, version: version.value, files: fileOrders() })).data.row
      if (newFiles.length > 0) {
        const knownIds = new Set(row.files.map((file) => file.fileId))
        row = (await uploadTestFiles(row.testNo, newFiles)).data.row
        // 追加分の表示順・備考を確定させる（アップロード API は順序と備考を持たないため）。
        const addedIds = assignNewFileIds(row, knownIds)
        const existingOrders = fileOrders()
        let cursor = 0
        const merged = items.value.map((item, index) => {
          if (item.kind === 'existing') {
            return existingOrders.find((order) => order.fileId === item.file.fileId)
              ?? { fileId: item.file.fileId, displayOrder: index, comment: null }
          }
          const fileId = addedIds[cursor++] ?? 0
          return { fileId, displayOrder: index, comment: item.comment.trim() === '' ? null : item.comment.trim() }
        }).filter((order) => order.fileId > 0)
        row = (await updateTestInfo(row.testNo, { ...payload, version: row.version, files: merged })).data.row
      }
    }

    toast.success(isNew.value ? `${row.testNo} を登録しました。` : `${row.testNo} を更新しました。`)
    emit('saved')
    emit('close')
  } catch (cause) {
    if (isConflict(cause)) toast.danger('他の端末で更新されています。再読込してください。')
    else toast.danger(messageOf(cause))
  } finally { busy.value = false }
}

onBeforeUnmount(() => {
  items.value.filter((item): item is NewItem => item.kind === 'new').forEach((item) => URL.revokeObjectURL(item.previewUrl))
})
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="overlay" @click.self="close" @paste="onPaste">
      <div class="dialog dialog--lg" role="dialog" aria-modal="true" aria-labelledby="testInfoDialogTitle">
        <div class="dialog__head">
          <h2 id="testInfoDialogTitle" class="dialog__title">
            <AppIcon name="clipboard" size="sm" /> テスト情報{{ isNew ? '（新規）' : ` ${testNo}` }}
          </h2>
          <button type="button" class="dialog__close" aria-label="閉じる" :disabled="busy" @click="close">
            <AppIcon name="x" size="sm" />
          </button>
        </div>

        <div class="dialog__body">
          <div class="form-grid form-grid--3">
            <div class="field">
              <label class="field__label">テスト番号</label>
              <input class="input" :value="isNew ? '自動採番' : testNo" disabled />
            </div>
            <div class="field">
              <label class="field__label">テスト名 <span class="ti-required">*</span></label>
              <input v-model="draft.testName" class="input" maxlength="120" placeholder="テスト名を入力" />
            </div>
            <div class="field">
              <label class="field__label">教科</label>
              <select v-model="draft.subject" class="select">
                <option v-for="option in SUBJECT_OPTIONS" :key="option" :value="option">{{ option }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label">区分</label>
              <select v-model="draft.kind" class="select">
                <option v-for="option in KIND_OPTIONS" :key="option" :value="option">{{ option }}</option>
              </select>
            </div>
            <div class="field">
              <label class="field__label">試験日</label>
              <input v-model="draft.examDate" class="input" type="date" />
            </div>
            <div class="field">
              <label class="field__label">正答率</label>
              <input class="input" :value="accuracyText" readonly />
            </div>
            <div class="field">
              <label class="field__label">得点数</label>
              <input v-model="draft.score" class="input" type="number" min="0" max="9999" />
            </div>
            <div class="field">
              <label class="field__label">満点数</label>
              <input v-model="draft.fullScore" class="input" type="number" min="0" max="9999" />
            </div>
          </div>

          <div class="field ti-memo">
            <label class="field__label">詳細メモ</label>
            <textarea v-model="draft.memo" class="input" rows="4" placeholder="詳細メモを入力してください" />
          </div>

          <section class="ti-files">
            <div class="ti-files__head">
              <span class="ti-files__label">試験用紙ファイル</span>
              <span class="ti-files__count">{{ items.length }} / {{ MAX_FILES }} 枚</span>
              <button type="button" class="btn btn--secondary btn--sm" :disabled="busy || remaining === 0" @click="pickFiles">
                <AppIcon name="upload" size="sm" /> ファイルを選択
              </button>
              <button
                type="button" class="btn btn--secondary btn--sm"
                :disabled="!canUsePicker" :title="isNew ? 'テストを保存すると選択できます' : '臨時ファイルから取り込む'"
                @click="tempPickerOpen = true"
              >
                <AppIcon name="folder" size="sm" /> 臨時ファイルから選択
              </button>
              <button
                type="button" class="btn btn--secondary btn--sm"
                :disabled="!canUsePicker" :title="isNew ? 'テストを保存すると選択できます' : '他のテストのファイルから取り込む'"
                @click="testPickerOpen = true"
              >
                <AppIcon name="clipboard" size="sm" /> テスト情報から選択
              </button>
            </div>

            <div
              class="dropzone ti-dropzone" :class="{ 'is-dragging': dragging }"
              @click="pickFiles" @dragover.prevent="dragging = true" @dragleave="dragging = false" @drop.prevent="onDrop"
            >
              <AppIcon name="upload" />
              ここにファイルをドロップ、またはクリックして選択（Ctrl+V 貼り付けも可・最大 {{ MAX_FILES }} 枚）
            </div>

            <p v-if="items.length === 0" class="ti-files__empty">試験用紙ファイルはまだありません。</p>
            <ul v-else class="ti-file-list">
              <li v-for="(item, index) in items" :key="itemKey(item)" class="ti-file-row">
                <span class="ti-file-row__thumb">
                  <img v-if="isImageItem(item) && itemPreviewUrl(item)" :src="itemPreviewUrl(item)!" :alt="itemName(item)" loading="lazy" />
                  <AppIcon v-else :name="fileIcon(itemDocument(item))" size="lg" :class="fileIconClass(itemDocument(item))" />
                </span>
                <span class="ti-file-row__main">
                  <span class="ti-file-row__name" :title="itemName(item)">{{ itemName(item) }}</span>
                  <span class="ti-file-row__meta">
                    {{ item.kind === 'existing' ? formatBytes(item.file.fileSize) : formatBytes(item.file.size) }}
                    <template v-if="item.kind === 'new'">・保存時にアップロード</template>
                  </span>
                  <input v-model="item.comment" class="input ti-file-row__comment" placeholder="備考" maxlength="200" />
                </span>
                <span class="ti-file-row__actions">
                  <button type="button" class="btn btn--icon btn--sm" title="前へ" :disabled="index === 0 || busy" @click="move(index, -1)">
                    <AppIcon name="chevron-left" size="sm" />
                  </button>
                  <button type="button" class="btn btn--icon btn--sm" title="次へ" :disabled="index === items.length - 1 || busy" @click="move(index, 1)">
                    <AppIcon name="chevron-right" size="sm" />
                  </button>
                  <template v-if="isImageItem(item)">
                    <button type="button" class="btn btn--icon btn--sm" title="左90°回転" :disabled="busy" @click="rotateItem(index, -90)">
                      <AppIcon name="rotate" size="sm" />
                    </button>
                    <button type="button" class="btn btn--icon btn--sm" title="右90°回転" :disabled="busy" @click="rotateItem(index, 90)">
                      <AppIcon name="rotate" size="sm" class="ti-icon-flip" />
                    </button>
                    <button type="button" class="btn btn--icon btn--sm" title="プレビュー" @click="previewItem(item)">
                      <AppIcon name="zoom-in" size="sm" />
                    </button>
                    <button type="button" class="btn btn--icon btn--sm" title="注釈" :disabled="busy" @click="annotating = item">
                      <AppIcon name="edit" size="sm" class="icon--edit" />
                    </button>
                  </template>
                  <button type="button" class="btn btn--icon btn--sm is-danger" title="削除" :disabled="busy" @click="removeItem(index)">
                    <AppIcon name="trash" size="sm" />
                  </button>
                </span>
              </li>
            </ul>
          </section>
        </div>

        <div class="dialog__foot">
          <button type="button" class="btn btn--secondary" :disabled="busy" @click="close">キャンセル</button>
          <button type="button" class="btn btn--primary" :disabled="busy" @click="save">
            <AppIcon name="check" size="sm" /> 保存
          </button>
        </div>
      </div>
    </div>
  </Teleport>

  <input ref="fileInput" type="file" multiple style="display: none" @change="chooseFiles" />

  <DocumentImageViewer
    v-if="viewer"
    :images="[{ branchNo: 0, originalFileName: viewer.name, extension: '', comment: null, contentUrl: viewer.url, image: true }]"
    :current="{ branchNo: 0, originalFileName: viewer.name, extension: '', comment: null, contentUrl: viewer.url, image: true }"
    @close="viewer = null"
  />

  <TestInfoAnnotator
    v-if="annotating"
    :open="true"
    :src="annotating.kind === 'existing' ? annotating.file.contentUrl : annotating.previewUrl"
    :file-name="itemName(annotating)"
    :save-handler="saveAnnotation"
    @close="annotating = null"
  />

  <TestInfoTempFilePicker
    :open="tempPickerOpen" :max="remaining" @close="tempPickerOpen = false" @picked="pickFromTempFiles"
  />
  <TestInfoFilePicker
    :open="testPickerOpen" :max="remaining" :exclude-test-no="testNo" @close="testPickerOpen = false" @picked="pickFromTestFiles"
  />
</template>

<style scoped>
.ti-required {
  color: var(--color-danger);
}

.ti-memo {
  margin-top: var(--sp-3);
}

.ti-files {
  margin-top: var(--sp-4);
  border-top: 1px solid var(--color-border);
  padding-top: var(--sp-3);
}

.ti-files__head {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: var(--sp-2);
}

.ti-files__label {
  font-size: var(--fs-sm);
  font-weight: var(--fw-semibold);
}

.ti-files__count {
  flex: 1;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
}

.ti-dropzone {
  margin-top: var(--sp-3);
  padding: var(--sp-5) var(--sp-4);
}

.ti-files__empty {
  margin: var(--sp-3) 0 0;
  font-size: var(--fs-sm);
  color: var(--color-text-muted);
}

.ti-file-list {
  margin: var(--sp-3) 0 0;
  padding: 0;
  list-style: none;
  display: flex;
  flex-direction: column;
  gap: var(--sp-2);
}

.ti-file-row {
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  padding: var(--sp-2) var(--sp-3);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-md);
}

.ti-file-row__thumb {
  width: 56px;
  height: 46px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--color-surface-alt);
  border-radius: var(--radius-sm);
  overflow: hidden;
}

.ti-file-row__thumb img {
  max-width: 100%;
  max-height: 100%;
  object-fit: contain;
}

.ti-file-row__main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.ti-file-row__name {
  font-size: var(--fs-sm);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.ti-file-row__meta {
  font-size: var(--fs-2xs);
  color: var(--color-text-subtle);
}

.ti-file-row__comment {
  margin-top: 2px;
  height: var(--control-sm);
}

.ti-file-row__actions {
  flex: none;
  display: inline-flex;
  align-items: center;
  gap: 2px;
}

.ti-icon-flip {
  transform: scaleX(-1);
}
</style>

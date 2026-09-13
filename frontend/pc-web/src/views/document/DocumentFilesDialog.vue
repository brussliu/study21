<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { ApiError, useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import DocumentImageViewer from '@/views/document/DocumentImageViewer.vue'
import { deleteDocumentFile, getDocumentFiles, uploadDocumentFiles, type DocumentFileInfo } from '@/api/documents'
import {
  downloadFile, fileActionHint, fileIcon, fileIconClass, openFile
} from '@/features/document-files/document-file'
import '@/features/document-files/document-files.css'

const props = defineProps<{ docNo: string; open: boolean }>()
const emit = defineEmits<{ close: []; changed: [] }>()
const toast = useToast()
const files = ref<DocumentFileInfo[]>([])
const pending = ref<File[]>([])
const loading = ref(false)
const viewer = ref<DocumentFileInfo | null>(null)
const fileInput = ref<HTMLInputElement | null>(null)
const images = computed(() => files.value.filter((file) => file.image))

function messageOf(cause: unknown): string { return cause instanceof ApiError || cause instanceof Error ? cause.message : '処理に失敗しました。' }

async function load(): Promise<void> {
  if (!props.open || !props.docNo) return
  loading.value = true
  try { files.value = (await getDocumentFiles(props.docNo)).data }
  catch (cause) { toast.danger(messageOf(cause)); files.value = [] }
  finally { loading.value = false }
}

watch(() => [props.open, props.docNo] as const, () => { viewer.value = null; pending.value = []; void load() }, { immediate: true })

function pickFiles(): void { fileInput.value?.click() }
function choose(event: Event): void { pending.value = Array.from((event.target as HTMLInputElement).files ?? []) }
async function upload(): Promise<void> {
  if (!pending.value.length) return
  try { files.value = (await uploadDocumentFiles(props.docNo, pending.value)).data; pending.value = []; toast.success('ファイルを追加しました。'); emit('changed') }
  catch (cause) { toast.danger(messageOf(cause)) }
}
async function remove(file: DocumentFileInfo): Promise<void> {
  if (!window.confirm(`${file.originalFileName} を削除しますか。`)) return
  try { await deleteDocumentFile(props.docNo, file.branchNo); await load(); toast.success('ファイルを削除しました。'); emit('changed') }
  catch (cause) { toast.danger(messageOf(cause)) }
}

/** ファイル名クリック: 画像は拡大表示、PDF は別タブで表示、その他はダウンロード。 */
function openDocumentFile(file: DocumentFileInfo): void {
  openFile(file, (image) => { viewer.value = image })
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open && !viewer" class="doc-files-overlay">
      <section class="doc-files-dialog" role="dialog" aria-modal="true" :aria-label="`資料 ${docNo} のファイル一覧`">
        <header class="doc-files-head">
          <strong>{{ docNo }} のファイル（{{ files.length }} 件）</strong>
          <button class="btn btn--ghost" @click="emit('close')">閉じる</button>
        </header>
        <div class="doc-files-upload">
          <input ref="fileInput" class="doc-files-upload__input" type="file" multiple @change="choose" />
          <button type="button" class="btn btn--secondary btn--sm" @click="pickFiles">
            <AppIcon name="upload" size="sm" /> ファイル選択
          </button>
          <span class="doc-files-upload__picked" :class="{ 'is-empty': pending.length === 0 }">
            {{ pending.length > 0 ? `${pending.length} 件選択中` : '選択されていません' }}
          </span>
          <button type="button" class="btn btn--primary btn--sm" :disabled="pending.length === 0" @click="upload">追加</button>
        </div>
        <div class="doc-files-body">
          <p v-if="loading">読込中...</p><p v-else-if="files.length === 0">この資料にファイルはありません。</p>
          <div v-for="file in files" :key="file.branchNo" class="doc-files-item">
            <button class="doc-files-open" :title="fileActionHint(file)" @click="openDocumentFile(file)">
              <AppIcon :name="fileIcon(file)" size="lg" :class="fileIconClass(file)" />
              <span>{{ file.originalFileName }}</span>
              <small>{{ file.extension.toUpperCase() }}</small>
            </button>
            <button class="btn btn--secondary btn--sm" @click="downloadFile(file)">取得</button>
            <button class="btn btn--danger-outline btn--sm" @click="remove(file)">削除</button>
          </div>
        </div>
      </section>
    </div>
  </Teleport>
  <DocumentImageViewer
    v-if="viewer" :images="images" :current="viewer"
    @update:current="viewer = $event" @close="viewer = null"
  />
</template>

<style scoped>
.doc-files-overlay{position:fixed;inset:0;z-index:var(--z-overlay);background:rgba(8,13,19,.75);display:flex;align-items:center;justify-content:center;padding:24px}
.doc-files-dialog{width:min(680px,100%);max-height:80vh;overflow:auto;background:var(--color-surface);border-radius:var(--radius-lg)}
.doc-files-head,.doc-files-upload,.doc-files-item{display:flex;align-items:center;gap:12px;padding:12px 16px;border-bottom:1px solid var(--color-border)}
.doc-files-head strong,.doc-files-open{flex:1}
.doc-files-body{padding:12px}
.doc-files-item{border:1px solid var(--color-border);margin-bottom:8px;border-radius:var(--radius-md)}
.doc-files-open{display:flex;align-items:center;gap:10px;border:0;background:transparent;text-align:left;color:inherit;cursor:pointer}
.doc-files-open span{flex:1}

/* ファイル選択: ネイティブ input は隠し、デザインシステムのボタンで操作する */
.doc-files-upload__input{display:none}
.doc-files-upload__picked{flex:1;min-width:0;font-size:var(--fs-xs);color:var(--color-text-muted);overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.doc-files-upload__picked.is-empty{color:var(--color-text-subtle)}
</style>

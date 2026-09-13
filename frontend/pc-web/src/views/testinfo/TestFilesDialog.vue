<script setup lang="ts">
import { computed, ref } from 'vue'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import DocumentImageViewer from '@/views/document/DocumentImageViewer.vue'
import TestInfoAnnotator from '@/views/testinfo/TestInfoAnnotator.vue'
import {
  deleteTestFile, saveTestFileImage, testFileDownloadUrl, type TestFile
} from '@/api/testinfo'
import { fileActionHint, fileIcon, fileIconClass, openFile } from '@/features/document-files/document-file'
import { messageOf, rotateDataUrl, toDocumentFileInfo } from '@/features/testinfo/testinfo-file'
import '@/features/document-files/document-files.css'

/**
 * テストの試験用紙ファイル一覧ダイアログ。
 * 資料管理のファイルダイアログと同じ操作に加え、回転（サーバ保存）と
 * 画像の注釈（2.0 の testinfo_showpic.jsp 相当）を行う。
 */
const props = defineProps<{ open: boolean; testNo: string; testName: string; files: TestFile[] }>()
const emit = defineEmits<{ close: []; changed: [] }>()

const toast = useToast()
const viewer = ref<TestFile | null>(null)
const annotating = ref<TestFile | null>(null)
const busyFileId = ref<number | null>(null)

const imageFiles = computed(() => props.files.filter((file) => file.image))
const viewerAsDocument = computed(() => viewer.value ? toDocumentFileInfo(viewer.value) : null)
const viewerImages = computed(() => imageFiles.value.map(toDocumentFileInfo))

function preview(file: TestFile): void {
  openFile(toDocumentFileInfo(file), () => { viewer.value = file })
}

async function rotate(file: TestFile, degrees: number): Promise<void> {
  if (busyFileId.value !== null) return
  busyFileId.value = file.fileId
  try {
    const rotated = await rotateDataUrl(file.contentUrl, degrees)
    const blob = await (await fetch(rotated)).blob()
    await saveTestFileImage(props.testNo, file.fileId, blob)
    toast.success('画像を回転して保存しました。')
    emit('changed')
  } catch (cause) { toast.danger(messageOf(cause)) }
  finally { busyFileId.value = null }
}

/** 注釈の保存: 焼き込んだ PNG をサーバへ送る。 */
async function saveAnnotation(blob: Blob): Promise<void> {
  const target = annotating.value
  if (!target) return
  await saveTestFileImage(props.testNo, target.fileId, blob)
  emit('changed')
}

async function remove(file: TestFile): Promise<void> {
  if (!window.confirm(`「${file.originalFileName}」を削除しますか。`)) return
  try {
    await deleteTestFile(props.testNo, file.fileId)
    toast.success(`${file.originalFileName} を削除しました。`)
    emit('changed')
  } catch (cause) { toast.danger(messageOf(cause)) }
}

function download(file: TestFile): void {
  const anchor = document.createElement('a')
  anchor.href = testFileDownloadUrl(file)
  anchor.download = file.originalFileName
  anchor.click()
}
</script>

<template>
  <Teleport to="body">
    <div v-if="open && !viewer && !annotating" class="doc-files-overlay">
      <section class="doc-files-dialog ti-files" role="dialog" aria-modal="true" :aria-label="`${testNo} の試験用紙ファイル`">
        <header class="doc-files-head">
          <strong>{{ testNo }} のファイル（{{ files.length }} 件）</strong>
          <span class="ti-files__name" :title="testName">{{ testName }}</span>
          <button class="btn btn--ghost btn--sm" @click="emit('close')">閉じる</button>
        </header>
        <div class="doc-files-body">
          <p v-if="files.length === 0">このテストにファイルはありません。</p>
          <div v-for="file in files" :key="file.fileId" class="doc-files-item">
            <button class="doc-files-open" :title="fileActionHint(toDocumentFileInfo(file))" @click="preview(file)">
              <AppIcon :name="fileIcon(toDocumentFileInfo(file))" size="lg" :class="fileIconClass(toDocumentFileInfo(file))" />
              <span>{{ file.originalFileName }}</span>
              <small>{{ file.extension.toUpperCase() }}</small>
            </button>
            <template v-if="file.image">
              <button
                type="button" class="btn btn--secondary btn--sm" :disabled="busyFileId !== null"
                title="左90°回転して保存" @click="rotate(file, -90)"
              >
                左90°
              </button>
              <button
                type="button" class="btn btn--secondary btn--sm" :disabled="busyFileId !== null"
                title="右90°回転して保存" @click="rotate(file, 90)"
              >
                右90°
              </button>
              <button type="button" class="btn btn--secondary btn--sm" title="注釈（ペン描画）" @click="annotating = file">
                <AppIcon name="edit" size="sm" class="icon--edit" /> 注釈
              </button>
            </template>
            <button type="button" class="btn btn--secondary btn--sm" @click="download(file)">取得</button>
            <button type="button" class="btn btn--danger-outline btn--sm" @click="remove(file)">削除</button>
          </div>
        </div>
      </section>
    </div>
  </Teleport>

  <DocumentImageViewer
    v-if="viewer && viewerAsDocument" :images="viewerImages" :current="viewerAsDocument"
    @update:current="viewer = files.find((file) => file.fileId === $event.branchNo) ?? null"
    @close="viewer = null"
  />

  <TestInfoAnnotator
    v-if="annotating"
    :open="true"
    :src="annotating.contentUrl"
    :file-name="annotating.originalFileName"
    :save-handler="saveAnnotation"
    @saved="emit('changed')"
    @close="annotating = null"
  />
</template>

<style scoped>
.ti-files__name {
  flex: 1;
  min-width: 0;
  font-size: var(--fs-xs);
  color: var(--color-text-muted);
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.doc-files-overlay{position:fixed;inset:0;z-index:var(--z-overlay);background:rgba(8,13,19,.75);display:flex;align-items:center;justify-content:center;padding:24px}
.doc-files-dialog{width:min(760px,100%);max-height:80vh;overflow:auto;background:var(--color-surface);border-radius:var(--radius-lg)}
.doc-files-head,.doc-files-item{display:flex;align-items:center;gap:12px;padding:12px 16px;border-bottom:1px solid var(--color-border)}
.doc-files-head strong{flex:none}
.doc-files-body{padding:12px}
.doc-files-item{border:1px solid var(--color-border);margin-bottom:8px;border-radius:var(--radius-md);flex-wrap:wrap}
.doc-files-open{flex:1;min-width:180px;display:flex;align-items:center;gap:10px;border:0;background:transparent;text-align:left;color:inherit;cursor:pointer}
.doc-files-open span{flex:1;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
</style>

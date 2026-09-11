<script setup lang="ts">
import { computed, ref } from 'vue'
import AppIcon from '@/components/ui/AppIcon.vue'
import DocumentImageViewer from '@/views/document/DocumentImageViewer.vue'
import {
  fileActionHint, fileChipClass, fileIcon, fileLabel, openFile
} from '@/features/document-files/document-file'
import type { DocumentFileInfo } from '@/api/documents'
import '@/features/document-files/document-files.css'

/**
 * 資料に含まれるファイルを、2.0 の内容列と同じ「色分けチップ」で並べる。
 * 多くても MAX_CHIPS 件まで表示し、残りは "+ N" にまとめる。
 * クリック時の動作はファイル一覧ダイアログと同一
 * （画像=拡大表示 / PDF=別タブで表示 / その他=ダウンロード）。
 */
const MAX_CHIPS = 6

const props = withDefaults(defineProps<{ files?: DocumentFileInfo[]; max?: number }>(), {
  files: () => [],
  max: MAX_CHIPS
})

const visible = computed(() => props.files.slice(0, props.max))
const hiddenCount = computed(() => Math.max(0, props.files.length - visible.value.length))

const viewing = ref<DocumentFileInfo | null>(null)
const images = computed(() => props.files.filter((file) => file.image))

function activate(file: DocumentFileInfo): void {
  openFile(file, (image) => { viewing.value = image })
}

function titleOf(file: DocumentFileInfo): string {
  return `${file.originalFileName}（${fileActionHint(file)}）`
}
</script>

<template>
  <span class="doc-file-chips">
    <span v-if="files.length === 0" class="doc-file-chips__empty">—</span>
    <button
      v-for="file in visible" :key="file.branchNo" type="button"
      :class="fileChipClass(file)" :title="titleOf(file)" :aria-label="titleOf(file)"
      @click.stop="activate(file)"
    >
      <AppIcon :name="fileIcon(file)" size="sm" class="doc-file-chip__icon" />
      <span>{{ fileLabel(file) }}</span>
    </button>
    <span v-if="hiddenCount > 0" class="doc-file-chip doc-file-chip--more" :title="`他 ${hiddenCount} 件`">+ {{ hiddenCount }}</span>
  </span>
  <DocumentImageViewer
    v-if="viewing" :images="images" :current="viewing"
    @update:current="viewing = $event" @close="viewing = null"
  />
</template>

<style scoped>
.doc-file-chips {
  display: inline-flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 4px;
}

.doc-file-chips__empty {
  color: var(--color-text-subtle);
  font-size: var(--fs-sm);
}
</style>

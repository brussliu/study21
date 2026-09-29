<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { useToast } from '@study21/web-shared'
import AppIcon from '@/components/ui/AppIcon.vue'
import DemoWordEditView from './demo/DemoWordEditView.vue'
import DemoWordStudyView from './demo/DemoWordStudyView.vue'
import JpnDetailVersionPanel from '@/features/japanese-word/JpnDetailVersionPanel.vue'
import { useJpnEditorStore } from '@/features/japanese-word/editorStore'

const props = defineProps<{ wordId: number }>()
const emit = defineEmits<{ close: []; saved: [] }>()
const store = useJpnEditorStore()
const toast = useToast()
const editor = ref<InstanceType<typeof DemoWordEditView> | null>(null)
const preview = ref(false)

/** 詳細の版の履歴を開いているか（既定は開いておく。読み込みはパネル側で 1 回）。 */
const versionsOpen = ref(true)

function close(): void {
  if (store.saveState === 'SAVING') return
  if (editor.value) editor.value.close()
  else { store.closeEditor(); emit('close') }
}
function beforeUnload(event: BeforeUnloadEvent): void {
  if (store.draftDirty || store.saveState === 'SAVING') {
    event.preventDefault()
    event.returnValue = ''
  }
}
onBeforeRouteLeave(() => {
  if (store.saveState === 'SAVING') return false
  return !store.draftDirty || window.confirm('未保存の変更を破棄して移動しますか？')
})
onMounted(() => {
  void store.openEditor(String(props.wordId))
  window.addEventListener('beforeunload', beforeUnload)
})
onBeforeUnmount(() => {
  window.removeEventListener('beforeunload', beforeUnload)
  store.closeEditor()
})
</script>

<template>
  <div class="overlay jp-editor-overlay" data-jp-word-dialog>
    <section class="dialog jp-word-editor-dialog" role="dialog" aria-modal="true" aria-labelledby="jpWordEditorTitle">
      <div class="dialog__head">
        <h2 id="jpWordEditorTitle" class="dialog__title">単語の修正</h2>
        <button type="button" class="dialog__close" aria-label="閉じる" :disabled="store.saveState === 'SAVING'" @click="close">
          <AppIcon name="x" size="sm" />
        </button>
      </div>
      <div class="dialog__body">
        <p v-if="store.loading" class="jp-hint" data-jp-editor-loading>詳細情報を読み込んでいます…</p>
        <p v-if="store.error" class="alert alert--danger">
          {{ store.error }}
          <button type="button" class="btn btn--secondary btn--sm" @click="store.openEditor(String(wordId))">再読み込み</button>
        </p>
        <DemoWordEditView
          v-if="store.draft && !store.loading" ref="editor" :store="store"
          @close="emit('close')" @saved="emit('saved')" @preview="preview = true"
        >
          <template #basic-extra>
            <div class="jp-demo-fieldgrid">
              <label class="filter-item">
                <span class="filter-item__label">状態</span>
                <select v-model="store.stateCode" class="select" data-jp-edit-state @change="store.touchDraft()">
                  <option value="ACTIVE">有効</option><option value="INACTIVE">無効</option>
                </select>
              </label>
              <label class="filter-item">
                <span class="filter-item__label">備考</span>
                <textarea v-model="store.note" class="textarea" rows="2" data-jp-edit-note @input="store.touchDraft()"></textarea>
              </label>
            </div>
          </template>
        </DemoWordEditView>

        <!-- 版の履歴（AI の取得も保存も版を積み上げる。古い版に戻せる） -->
        <details
          v-if="store.draft && !store.loading" class="jp-version-fold" data-jp-versions-fold open
          @toggle="versionsOpen = ($event.target as HTMLDetailsElement).open"
        >
          <summary class="jp-version-fold__summary">版の履歴（古い版に戻す）</summary>
          <JpnDetailVersionPanel
            v-if="versionsOpen" :store="store"
            @activated="(message) => toast.success(message)"
          />
        </details>
      </div>
    </section>
    <div v-if="preview && store.draft" class="overlay jp-study-overlay" data-jp-editor-preview>
      <section class="dialog dialog--wide jp-study-dialog" role="dialog" aria-modal="true" aria-labelledby="jpPreviewTitle">
        <div class="dialog__head">
          <h2 id="jpPreviewTitle" class="dialog__title">学習画面で確認（未保存）</h2>
          <button type="button" class="dialog__close" aria-label="プレビューを閉じる" @click="preview = false"><AppIcon name="x" size="sm" /></button>
        </div>
        <div class="dialog__body jp-study-dialog__body"><DemoWordStudyView :word="store.draft" draft :partial="false" /></div>
      </section>
    </div>
  </div>
</template>

<style scoped>
.jp-word-editor-dialog { width: min(1320px, calc(100vw - 32px)); max-width: none; height: calc(100dvh - 32px); max-height: none; }
.jp-word-editor-dialog > .dialog__body { min-height: 0; overflow: auto; }
.jp-word-editor-dialog :deep(.jp-demo-editor__head) { position: sticky; top: 0; z-index: 2; background: var(--color-surface); padding: var(--sp-3); border-bottom: 1px solid var(--color-border); }
.jp-word-editor-dialog :deep(.jp-demo-fieldgrid) { margin-bottom: var(--sp-4); }
</style>

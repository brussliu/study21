<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import DemoWordEditView from './DemoWordEditView.vue'
import DemoWordStudyView from './DemoWordStudyView.vue'

/**
 * 詳細編集のページ。
 *
 * 編集と「学習画面で確認」（下書きプレビュー）を行き来する。
 * プレビューは同じ下書きを見るので、閉じれば入力はそのまま残る（§7-4）。
 */

const store = useJapaneseDemoStore()
const router = useRouter()
const route = useRoute()

const wordId = String(route.params.wordId ?? '')
/** 下書きプレビューを出しているか。 */
const previewing = ref(false)

// ページを開いた時点で下書きを作る（すでに同じ単語を編集中なら作り直さない）
if (store.draft === null || store.draft.id !== wordId) {
  store.openEditor(wordId)
}

const previewWord = computed(() => store.draft)

function close(): void {
  void router.push({ name: 'student-japanese-demo' })
}

function startPreview(): void {
  previewing.value = true
  window.scrollTo({ top: 0 })
}

function endPreview(): void {
  previewing.value = false
}

/** プレビューから編集へ戻す（保存済みの内容ではなく、いまの下書きに戻る）。 */
function backToEdit(): void {
  previewing.value = false
}

void router
</script>

<template>
  <div>
    <!-- 下書きのプレビュー（未保存であることを帯で示す） -->
    <DemoWordStudyView
      v-if="previewing && previewWord"
      :word="previewWord"
      draft
      @close="endPreview"
      @edit="backToEdit"
    />
    <DemoWordEditView
      v-else
      @close="close"
      @preview="startPreview"
    />
  </div>
</template>

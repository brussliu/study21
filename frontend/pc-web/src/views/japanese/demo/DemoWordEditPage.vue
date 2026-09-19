<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import { openStudyPopup } from '@/features/japanese-demo/popup'
import DemoWordEditView from './DemoWordEditView.vue'

/**
 * 詳細編集のページ（**別ウィンドウ**）。
 *
 * 一覧の「詳細編集」から `window.open` で開く。2.0 と同じく、学習画面（A. 勉強）は
 * ここから**さらに別ウィンドウ**で開く（`js/japanese_word.js` の `openStudyDetail` と同じ形）。
 *
 * 保存せずに見たいとき（「学習画面で確認」）は、いま編集中の下書きを渡す。
 * 下書きは控え（`localStorage`）に置いてあるので、別ウィンドウから読める。
 */

const store = useJapaneseDemoStore()
const route = useRoute()

const wordId = String(route.query.wordId ?? '')
const opened = ref(false)

function closeWindow(): void {
  // 別ウィンドウなので、閉じて一覧へ戻る（開いた元が生きていればそちらへ戻す）
  try {
    if (window.opener !== null && !window.opener.closed) {
      window.opener.focus()
    }
  } catch {
    // 参照できないときは何もしない
  }
  window.close()
}

/** 学習画面（A. 勉強）を別ウィンドウで開く。いまの下書きのまま見せる。 */
function openStudy(): void {
  if (wordId === '') {
    return
  }
  openStudyPopup(wordId, { draft: true })
}

onMounted(() => {
  // 動作確認で状態を固定したいときだけ（画面には出さない）。例: `?save=SAVE_FAILED`
  store.applyDisplayFromQuery(window.location.search)
  if (wordId === '') {
    return
  }
  if (store.draft !== null && store.draft.id === wordId) {
    opened.value = true
    return
  }
  // 控えがあれば続きから、無ければ仮データから作り直す
  if (store.loadStoredDraft() && store.draft?.id === wordId) {
    opened.value = true
    return
  }
  opened.value = store.openEditor(wordId)
})
</script>

<template>
  <DemoWordEditView @close="closeWindow" @preview="openStudy" />
</template>

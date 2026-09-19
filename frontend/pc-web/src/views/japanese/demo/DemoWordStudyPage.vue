<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute } from 'vue-router'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import DemoWordStudyView from './DemoWordStudyView.vue'

/**
 * 学習画面（A. 勉強）のページ（**別ウィンドウ**）。
 *
 * 2.0 の `japanese_word.js` は
 * `window.open('japanese_test_a.jsp?wordId=…&view=detail', 'jpWordA_…', 'popup=yes,…')`
 * でこの画面を別ウィンドウに開いていた。2.1 も同じ形にしている。
 *
 * ・`wordId` … 表示する単語
 * ・`draft=1` … 未保存の下書き（詳細編集から「学習画面で確認」したとき）
 */

const store = useJapaneseDemoStore()
const route = useRoute()

const wordId = computed(() => String(route.query.wordId ?? ''))
const isDraft = computed(() => route.query.draft === '1')

/** 下書きの表示のときは、詳細編集が置いた下書きを読む。 */
const draftReady = computed(() => !isDraft.value || (store.draft !== null && store.draft.id === wordId.value))

const word = computed(() => {
  if (isDraft.value) {
    return store.draft?.id === wordId.value ? store.draft : null
  }
  return store.findWord(wordId.value)
})

onMounted(() => {
  window.scrollTo({ top: 0 })
  // 動作確認で状態を固定したいときだけ（画面には出さない）。例: `?detail=PARTIAL`
  store.applyDisplayFromQuery(window.location.search)
  if (isDraft.value) {
    store.loadStoredDraft()
  }
})

function closeWindow(): void {
  try {
    if (window.opener !== null && !window.opener.closed) {
      window.opener.focus()
    }
  } catch {
    // 参照できないときは何もしない
  }
  window.close()
}

/** 詳細編集へ移る（下書きのまま編集したいとき）。 */
function edit(): void {
  const query = isDraft.value ? '&draft=1' : ''
  window.location.href = `/student/japanese-demo/edit?wordId=${encodeURIComponent(wordId.value)}${query}`
}
</script>

<template>
  <div>
    <DemoWordStudyView
      v-if="word"
      :word="word"
      :draft="isDraft"
      @close="closeWindow"
      @edit="edit"
    />
    <div v-else-if="draftReady" class="jp-demo">
      <p class="alert alert--danger" data-demo-study-missing-word>
        単語が見つかりませんでした。
        <button type="button" class="jp-demo-linkbtn" @click="closeWindow">閉じる</button>
      </p>
    </div>
  </div>
</template>

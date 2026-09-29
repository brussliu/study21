<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { storeToRefs } from 'pinia'
import { useRoute } from 'vue-router'
import DemoWordStudyView from '@/views/japanese/demo/DemoWordStudyView.vue'
import { useJpnStudyStore } from '@/features/japanese-word/studyStore'
import '@/features/japanese-demo/japanese-demo.css'

/**
 * 学習画面（A. 勉強）のページ（**別ウィンドウ**）。
 *
 * <p>単語情報管理の【詳細】（目のアイコン）から `window.open` で開く（**自動最大化**）。
 * 2.0 の `japanese_word.js` が `japanese_test_a.jsp?wordId=…&view=detail` を別ウィンドウで
 * 開いていたのと同じ形。**データはこの窓が API から取り直す**（クッキーと画面セッションは
 * 同じオリジンなのでそのまま使える）。</p>
 *
 * <p>**画面の帯（見出し・修正・閉じる）は置かない**（利用者の指示。2.0 の学習画面も
 * 中身だけを出していた）。窓はブラウザの × で閉じ、修正は一覧の【修正】（鉛筆）から行う。</p>
 */

const store = useJpnStudyStore()
const { word, loading, error } = storeToRefs(store)
const route = useRoute()

const wordId = computed(() => Number(route.query.wordId ?? 0))

onMounted(() => {
  window.scrollTo({ top: 0 })
  if (wordId.value > 0) {
    void store.load(wordId.value)
  }
})
</script>

<template>
  <div class="jp-study-window" data-jp-study-page>
    <div class="jp-study-window__body">
      <p v-if="wordId <= 0" class="alert alert--danger" data-jp-study-page-error>
        表示する単語が指定されていません。
      </p>

      <p v-else-if="loading" class="jp-hint" data-jp-study-page-loading>単語の詳細を読み込んでいます…</p>

      <p v-else-if="error !== ''" class="alert alert--danger" data-jp-study-page-error>{{ error }}</p>

      <DemoWordStudyView
        v-else-if="word !== null"
        :key="word.id"
        :word="word"
      />

      <p v-else class="alert alert--danger" data-jp-study-page-missing>単語が見つかりませんでした。</p>
    </div>
  </div>
</template>

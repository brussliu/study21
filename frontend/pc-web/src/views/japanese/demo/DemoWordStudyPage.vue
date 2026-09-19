<script setup lang="ts">
import { computed, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useJapaneseDemoStore } from '@/features/japanese-demo/store/japaneseDemo'
import DemoWordStudyView from './DemoWordStudyView.vue'

/**
 * 学習画面の確認（A. 勉強）のページ。
 *
 * 保存済みのデモデータを表示する（下書きは混ぜない）。§7-4。
 * 単語が見つからないときは、その旨を出して一覧へ戻す。
 */

const store = useJapaneseDemoStore()
const router = useRouter()
const route = useRoute()

const wordId = String(route.params.wordId ?? '')
const word = computed(() => store.findWord(wordId))

onMounted(() => {
  window.scrollTo({ top: 0 })
})

function close(): void {
  void router.push({ name: 'student-japanese-demo' })
}

function edit(): void {
  void router.push({ name: 'student-japanese-demo-edit', params: { wordId } })
}
</script>

<template>
  <div>
    <DemoWordStudyView
      v-if="word"
      :word="word"
      @close="close"
      @edit="edit"
    />
    <div v-else class="jp-demo">
      <p class="alert alert--danger" data-demo-study-missing-word>
        単語が見つかりませんでした（デモデータをリセットした可能性があります）。
        <button type="button" class="jp-demo-linkbtn" @click="close">一覧へ戻る</button>
      </p>
    </div>
  </div>
</template>

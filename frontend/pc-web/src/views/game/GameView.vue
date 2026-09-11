<script setup lang="ts">
import { computed, defineAsyncComponent, ref, watch, type Component } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { GAMES } from '@/features/game/games'
import '@/features/game/game.css'

/**
 * ゲームは「資料管理」と同じく 1 ページのタブで切り替える。
 * 選んだタブは URL クエリ ?game=<slug> と同期する（直リンク・再読込でも保たれる）。
 */
const COMPONENTS: Record<string, Component> = {
  minesweeper: defineAsyncComponent(() => import('@/views/game/MinesweeperView.vue')),
  sudoku: defineAsyncComponent(() => import('@/views/game/SudokuView.vue')),
  '2048': defineAsyncComponent(() => import('@/views/game/Game2048View.vue')),
  gomoku: defineAsyncComponent(() => import('@/views/game/GomokuView.vue')),
  reversi: defineAsyncComponent(() => import('@/views/game/ReversiView.vue')),
  nonogram: defineAsyncComponent(() => import('@/views/game/NonogramView.vue'))
}

const route = useRoute()
const router = useRouter()

/** ?game=xxx が不明なときは先頭（マインスイーパー）にフォールバックする。 */
function tabFromQuery(value: unknown): string {
  const slug = typeof value === 'string' ? value : ''
  return GAMES.some((game) => game.slug === slug) ? slug : GAMES[0].slug
}

const activeTab = ref(tabFromQuery(route.query.game))
watch(() => route.query.game, (value) => { activeTab.value = tabFromQuery(value) })

const activeGame = computed(() => GAMES.find((game) => game.slug === activeTab.value) ?? GAMES[0])
const activeComponent = computed(() => COMPONENTS[activeTab.value])

function selectTab(slug: string): void {
  if (activeTab.value === slug) return
  activeTab.value = slug
  void router.replace({ path: route.path, query: { ...route.query, game: slug } })
}
</script>

<template>
  <div class="gm-tabs-page">
    <div class="tabs" role="tablist" aria-label="ゲーム">
      <button
        v-for="game in GAMES" :key="game.slug"
        type="button" class="tabs__tab" role="tab"
        :class="{ 'is-active': activeTab === game.slug }"
        :aria-selected="activeTab === game.slug"
        @click="selectTab(game.slug)"
      >
        {{ game.label }}
      </button>
    </div>

    <p class="gm-tabs__desc">{{ activeGame.desc }}（{{ activeGame.meta }}）</p>

    <section class="tabs__panel is-active" role="tabpanel" :aria-label="activeGame.label">
      <component :is="activeComponent" :key="activeTab" embedded />
    </section>
  </div>
</template>

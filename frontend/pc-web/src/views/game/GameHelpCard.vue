<script setup lang="ts">
import { computed } from 'vue'
import { GAME_HELP } from '@/features/game/gameHelp'
import { useHelpLanguage } from '@/features/game/useHelpLanguage'

/**
 * ゲーム共通の「遊び方」カード。
 * 右上の切り替え（日本語 / 中文）で、このカードの文言だけを切り替える。
 * 選んだ言語はアプリ内で共有される（features/game/useHelpLanguage.ts）。
 */
const props = defineProps<{
  /** ゲームの slug（features/game/games.ts と同じ値）。 */
  slug: string
}>()

const help = useHelpLanguage()
const text = computed(() => GAME_HELP[props.slug][help.language])
</script>

<template>
  <div class="card">
    <div class="card__header">
      <h3 class="card__title">{{ text.title }}</h3>
      <div class="gm-lang" role="group" aria-label="遊び方の言語 / 玩法的语言">
        <button
          type="button" class="gm-lang__btn" :class="{ 'is-active': help.isJa }"
          :aria-pressed="help.isJa" lang="ja" @click="help.setLanguage('ja')"
        >
          日本語
        </button>
        <button
          type="button" class="gm-lang__btn" :class="{ 'is-active': help.isZh }"
          :aria-pressed="help.isZh" lang="zh-CN" @click="help.setLanguage('zh')"
        >
          中文
        </button>
      </div>
    </div>
    <div class="card__body gm-help">
      <ul>
        <li v-for="item in text.items" :key="item">{{ item }}</li>
      </ul>
      <p class="gm-note">{{ text.note }}</p>
    </div>
  </div>
</template>

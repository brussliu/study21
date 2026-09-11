<script setup lang="ts">
import { useThemeStore } from '@/stores/theme'

defineEmits<{
  'open-drawer': []
}>()

const theme = useThemeStore()
</script>

<template>
  <header class="mobile-topbar">
    <button
      type="button"
      class="mobile-topbar__menu"
      aria-label="メニューを開く"
      @click="$emit('open-drawer')"
    >
      ☰
    </button>
    <div class="mobile-topbar__title">Study 2.1</div>
    <button
      type="button"
      class="mobile-topbar__theme"
      :aria-label="theme.theme === 'dark' ? 'ライトテーマへ切替' : 'ダークテーマへ切替'"
      @click="theme.toggle()"
    >
      {{ theme.theme === 'dark' ? '☀' : '☾' }}
    </button>
  </header>
</template>

<style scoped>
.mobile-topbar {
  position: sticky;
  top: 0;
  z-index: var(--z-sticky);
  display: flex;
  align-items: center;
  gap: var(--sp-3);
  height: 52px;
  padding: 0 var(--sp-3);
  padding-top: env(safe-area-inset-top);
  background: var(--color-surface);
  border-bottom: 1px solid var(--color-border);
}
.mobile-topbar__menu,
.mobile-topbar__theme {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: var(--fs-lg);
  color: var(--color-text);
  min-height: 40px;
}
.mobile-topbar__title {
  flex: 1;
  text-align: center;
  font-weight: var(--fw-semibold);
  font-size: var(--fs-lg);
}
</style>

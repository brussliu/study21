<script setup lang="ts">
defineProps<{
  homePath: string
}>()

defineEmits<{
  'open-drawer': []
}>()
</script>

<template>
  <nav class="mobile-bottom-nav" aria-label="ボトムナビゲーション">
    <RouterLink :to="homePath" class="mobile-bottom-nav__item">ホーム</RouterLink>
    <button type="button" class="mobile-bottom-nav__item" @click="$emit('open-drawer')">
      メニュー
    </button>
  </nav>
</template>

<style scoped>
.mobile-bottom-nav {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  z-index: var(--z-sticky);
  display: flex;
  border-top: 1px solid var(--color-border);
  background: var(--color-surface);
  padding-bottom: env(safe-area-inset-bottom);
}
.mobile-bottom-nav__item {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 52px;
  text-decoration: none;
  color: var(--color-text-muted);
  font-size: var(--fs-sm);
  border: 0;
  background: transparent;
  cursor: pointer;
}
.mobile-bottom-nav__item.router-link-active {
  color: var(--color-primary);
  font-weight: var(--fw-semibold);
}
</style>

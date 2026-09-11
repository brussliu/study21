<script setup lang="ts">
import { computed } from 'vue'
import type { MobileArea } from '@/router'
import { frameworkMenu } from '@/config/menuRegistry'

const props = defineProps<{
  open: boolean
  area: MobileArea
  homePath: string
}>()

defineEmits<{
  close: []
}>()

const menu = computed(() => frameworkMenu(props.area))
</script>

<template>
  <Teleport to="body">
    <div v-if="open" class="mobile-drawer-overlay" @click.self="$emit('close')">
      <aside class="mobile-drawer" aria-label="メニュー">
        <div class="mobile-drawer__header">
          <span class="mobile-drawer__heading">メニュー</span>
          <button type="button" class="mobile-drawer__close" aria-label="閉じる" @click="$emit('close')">
            ×
          </button>
        </div>
        <nav class="mobile-drawer__nav">
          <RouterLink
            v-for="item in menu"
            :key="item.id"
            :to="item.path ?? homePath"
            class="mobile-drawer__item"
            @click="$emit('close')"
          >
            {{ item.label }}
          </RouterLink>
          <div class="mobile-drawer__note">業務メニューは未実装</div>
        </nav>
      </aside>
    </div>
  </Teleport>
</template>

<style scoped>
.mobile-drawer-overlay {
  position: fixed;
  inset: 0;
  z-index: var(--z-overlay);
  background: rgba(10, 18, 28, 0.5);
}
.mobile-drawer {
  position: fixed;
  top: 0;
  left: 0;
  bottom: 0;
  width: min(80vw, 320px);
  background: var(--color-surface);
  z-index: var(--z-dialog);
  display: flex;
  flex-direction: column;
  box-shadow: var(--shadow-lg);
}
.mobile-drawer__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: var(--sp-3) var(--sp-4);
  border-bottom: 1px solid var(--color-border);
}
.mobile-drawer__heading {
  font-weight: var(--fw-semibold);
}
.mobile-drawer__close {
  border: 0;
  background: transparent;
  cursor: pointer;
  font-size: var(--fs-xl);
  color: var(--color-text-muted);
  min-height: 40px;
}
.mobile-drawer__nav {
  display: flex;
  flex-direction: column;
  padding: var(--sp-3);
  gap: var(--sp-1);
}
.mobile-drawer__item {
  padding: var(--sp-3);
  border-radius: var(--radius-md);
  color: var(--color-text);
  text-decoration: none;
}
.mobile-drawer__item:hover {
  background: var(--color-surface-hover);
}
.mobile-drawer__note {
  margin-top: var(--sp-4);
  padding: var(--sp-3);
  color: var(--color-text-subtle);
  font-size: var(--fs-xs);
}
</style>
